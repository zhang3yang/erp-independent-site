-- ============================================================================
-- 独立站装修内容 contentjson：v1（单语言）→ v2（分语言）
--
-- 背景：站点决定走「一站多语」。原来 u_s_site.contentjson 整份只有一种语言的内容，
--      而 /openapi/site/info 是带 lang 参数的——商品名会跟着语言变，导航/标语/公司
--      介绍/SEO 却永远是同一份。这个脚本把老数据包一层语言。
--
-- v2 结构（详见 StationOpenServiceImpl#siteContent）：
--   {
--     "v": 2,
--     "logo": "...",              ← i18n 之外的顶层键 = 共享层，各语言通用
--     "i18n": { "en": { ... }, "ja": { ... } }
--   }
--
-- 迁移方向：整份老内容 → i18n.<站点默认语言>，默认语言取 langs 列的第一项，空则 en。
--          共享层这次留空，由商家之后在装修界面里往外提（图片类）。
--
-- 可重复执行：迁完的行 $.v = 2，WHERE 里排掉了，再跑一次不会二次包裹。
-- 需要 MySQL 5.7+（JSON_VALID / JSON_OBJECT / JSON_TYPE）。
--
-- ⚠ 先备份：mysqldump -u root -p sdsstation u_s_site > u_s_site.bak-20260831.sql
-- ============================================================================

-- ---- 1. 迁移前先看一眼有几行要动，以及各自会落到哪个语言 ----
SELECT sitecode,
       langs,
       COALESCE(NULLIF(LOWER(TRIM(SUBSTRING_INDEX(langs, ',', 1))), ''), 'en') AS target_lang,
       CASE
           WHEN contentjson IS NULL OR TRIM(contentjson) = ''      THEN '空，跳过'
           WHEN NOT JSON_VALID(contentjson)                        THEN '⚠ 不是合法 JSON，跳过，需人工处理'
           WHEN JSON_EXTRACT(contentjson, '$.v') IS NOT NULL       THEN '已是 v2，跳过'
           ELSE '本次迁移'
       END AS action
FROM sdsstation.u_s_site
ORDER BY sitecode;

-- ---- 2. 迁移 ----
UPDATE sdsstation.u_s_site
SET contentjson = JSON_OBJECT(
        'v', 2,
        'i18n', JSON_OBJECT(
            COALESCE(NULLIF(LOWER(TRIM(SUBSTRING_INDEX(langs, ',', 1))), ''), 'en'),
            CAST(contentjson AS JSON)
        )
    )
WHERE contentjson IS NOT NULL
  AND TRIM(contentjson) <> ''
  AND JSON_VALID(contentjson)
  -- 只包对象。有人手工塞进一个数组或裸字符串的话，包了也没法在装修界面里编辑，留着人工看
  AND JSON_TYPE(CAST(contentjson AS JSON)) = 'OBJECT'
  -- 已经是 v2 的不再动，这条让脚本可以重复执行
  AND JSON_EXTRACT(contentjson, '$.v') IS NULL;

-- ---- 3. 验证：应该全是 v2 或空 ----
SELECT sitecode,
       JSON_EXTRACT(contentjson, '$.v')                     AS version,
       JSON_KEYS(JSON_EXTRACT(contentjson, '$.i18n'))       AS langs_in_content
FROM sdsstation.u_s_site
WHERE contentjson IS NOT NULL AND TRIM(contentjson) <> ''
ORDER BY sitecode;

-- ============================================================================
-- 回滚（只对本脚本迁过的行有效——它们的 i18n 底下只有一种语言）：
--
-- UPDATE sdsstation.u_s_site
-- SET contentjson = JSON_UNQUOTE(JSON_EXTRACT(contentjson,
--         CONCAT('$.i18n.', COALESCE(NULLIF(LOWER(TRIM(SUBSTRING_INDEX(langs, ',', 1))), ''), 'en'))))
-- WHERE JSON_VALID(contentjson)
--   AND JSON_EXTRACT(contentjson, '$.v') = 2
--   AND JSON_LENGTH(JSON_EXTRACT(contentjson, '$.i18n')) = 1;
--
-- ⚠ 商家已经在装修界面里配过第二种语言之后，这个回滚会丢掉那些内容，别再用。
-- ============================================================================
