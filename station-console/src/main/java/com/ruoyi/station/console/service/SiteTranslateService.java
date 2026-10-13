package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.util.TmtClient;

/**
 * 机器翻译：把默认语言那份文案批量翻成另一种语言，填进管理台的表单里。
 *
 * <h3>它解决的是哪个麻烦</h3>
 * <p>站点开 7 种语言，装修表单里每个文案格子就要填 7 遍。v4 那次改造已经把
 * 「结构」从这件事里摘出去了（导航条数、图片、版式只填一次），剩下的<b>纯文字</b>
 * 仍然是 7 遍。这个服务把「第一遍」自动填上，商家在机翻结果上改，比从空白开始快得多。</p>
 *
 * <p>⚠ 定位是<b>初稿</b>，不是成品。管理台那边填完会明说「机器翻译的，请自己过一遍」——
 * 外贸站的文案直接拿机翻发出去是要出事的。</p>
 *
 * <h3>密钥复用 COS 那一对</h3>
 * <p>腾讯云的 SecretId/SecretKey 是账号级的，配过对象存储就能直接用于翻译，
 * 不必在管理台里再填一遍密钥——多一处存密钥就多一处泄漏面。
 * 代价是<b>翻译能不能用，取决于对象存储配没配</b>，所以下面那几句报错要说清楚这件事，
 * 不然商家会对着「翻译」这个按钮找一个并不存在的翻译配置页。</p>
 *
 * <h3>为什么不做缓存</h3>
 * <p>看着很值（同一句 slogan 可能翻很多次），但商家改一个字就是一条新记录，
 * 命中率不会高；而一旦缓存，商家改完原文再点翻译还拿到旧译文，那种「怎么改都不变」
 * 的表现比多花几分钱糟糕得多。腾讯云每月前 500 万字符免费，这点量根本用不着省。</p>
 */
@Service
public class SiteTranslateService
{
    private static final Logger log = LoggerFactory.getLogger(SiteTranslateService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * TMT 认得的语种。站点语言列表里出现别的值就直接挡在这里，
     * 而不是发出去换一个看不懂的 {@code UnsupportedOperation} 回来。
     */
    private static final Set<String> SUPPORTED = Set.of(
            "zh", "zh-TW", "en", "ja", "ko", "fr", "es", "it", "de",
            "tr", "ru", "pt", "vi", "id", "th", "ms", "ar", "hi");

    /**
     * 一次请求最多翻几段、总共多长。
     *
     * <p>腾讯云那边的限制是单次 6000 字符，这里取一半：管理台是按「一屏格子」凑批的，
     * 留出余量比卡着上限跑稳妥。超了就分几批发，对调用方是透明的。</p>
     */
    private static final int BATCH_CHARS = 3000;
    private static final int BATCH_ITEMS = 50;

    /** 整次调用的上限。防的是「一次点击把几十万字符发出去」，不是防攻击——这个接口要登录 */
    private static final int MAX_ITEMS = 300;
    private static final int MAX_CHARS = 30000;

    /**
     * 地域。翻译服务各地域价格和配额一样，选近的就行。
     *
     * <p>默认跟服务器走（上海）。要改在 application.yml 里加 {@code station.tmt.region}。</p>
     */
    @Value("${station.tmt.region:ap-shanghai}")
    private String region;

    @Autowired
    private SiteStorageService storageService;

    /**
     * 批量翻译。返回的列表和入参<b>等长、按下标对应</b>。
     *
     * <p>空字符串原样返回，不发给腾讯云——那既浪费字符配额，
     * 又会让「哪一格是空的」这个信息在往返中丢掉。</p>
     *
     * @param from  源语言（站点默认语言）
     * @param to    目标语言
     * @param texts 要翻的文案，允许有空串
     * @return 译文，和 {@code texts} 一一对应
     */
    public List<String> translate(String from, String to, List<String> texts)
    {
        String source = normalize(from);
        String target = normalize(to);
        if (source == null || target == null)
        {
            throw new ServiceException("源语言或目标语言为空");
        }
        if (source.equals(target))
        {
            throw new ServiceException("源语言和目标语言是同一种，不用翻译");
        }
        if (!SUPPORTED.contains(source) || !SUPPORTED.contains(target))
        {
            throw new ServiceException("腾讯云机器翻译不支持这个语种（" + source + " → " + target + "）");
        }
        if (texts == null || texts.isEmpty())
        {
            return List.of();
        }
        if (texts.size() > MAX_ITEMS)
        {
            throw new ServiceException("一次最多翻 " + MAX_ITEMS + " 段，请分几次");
        }

        // 结果先按原样铺满，翻译回来再按下标覆盖。这样空串那些「没送去翻」的位置
        // 天然保持原值，调用方不用再对一遍下标
        List<String> out = new ArrayList<>(texts);

        // 挑出真正要翻的那些，记住它们在原列表里的下标
        List<Integer> indexes = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < texts.size(); i++)
        {
            String text = texts.get(i);
            if (text == null || text.trim().isEmpty())
            {
                continue;
            }
            total += text.length();
            if (total > MAX_CHARS)
            {
                throw new ServiceException("一次最多翻 " + MAX_CHARS + " 个字符，请分几次");
            }
            indexes.add(i);
        }
        if (indexes.isEmpty())
        {
            return out;
        }

        Credentials credentials = credentials();

        // 按「条数」和「字符数」两个口径切批，哪个先到算哪个
        List<Integer> batch = new ArrayList<>();
        int chars = 0;
        for (Integer index : indexes)
        {
            int len = texts.get(index).length();
            if (!batch.isEmpty() && (batch.size() >= BATCH_ITEMS || chars + len > BATCH_CHARS))
            {
                translateBatch(source, target, texts, batch, out, credentials);
                batch = new ArrayList<>();
                chars = 0;
            }
            batch.add(index);
            chars += len;
        }
        if (!batch.isEmpty())
        {
            translateBatch(source, target, texts, batch, out, credentials);
        }
        return out;
    }

    /** 发一批出去，把译文按下标写回 {@code out} */
    private void translateBatch(String source, String target, List<String> texts,
            List<Integer> indexes, List<String> out, Credentials credentials)
    {
        List<String> payloadTexts = new ArrayList<>(indexes.size());
        for (Integer index : indexes)
        {
            payloadTexts.add(texts.get(index));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Source", source);
        payload.put("Target", target);
        payload.put("ProjectId", 0);
        payload.put("SourceTextList", payloadTexts);

        String response;
        try
        {
            response = TmtClient.call("TextTranslateBatch", region, JSON.writeValueAsString(payload),
                    credentials.secretId(), credentials.secretKey());
        }
        catch (Exception e)
        {
            // 网络层失败。异常消息里可能有内网地址，不往上抛原文
            log.warn("调用腾讯云机器翻译失败", e);
            throw new ServiceException("连不上腾讯云翻译服务，请稍后再试");
        }

        JsonNode root = JSON.readTree(response).path("Response");
        JsonNode error = root.path("Error");
        if (!error.isMissingNode() && !error.isNull())
        {
            throw new ServiceException(explain(
                    error.path("Code").asString(""), error.path("Message").asString("")));
        }

        JsonNode list = root.path("TargetTextList");
        if (!list.isArray() || list.size() != indexes.size())
        {
            // 条数对不上就整批作废。按短的那个对下标的话，会把译文错位填进别的格子里——
            // 那种错在界面上看是「翻译了，但每一句都不对应」，比报错难查得多
            log.warn("腾讯云翻译返回的条数和请求不一致：请求 {} 条，返回 {}", indexes.size(), list.size());
            throw new ServiceException("翻译服务返回的结果条数不对，请重试");
        }
        for (int i = 0; i < indexes.size(); i++)
        {
            out.set(indexes.get(i), list.get(i).asString(""));
        }
    }

    /**
     * 把腾讯云的错误码翻译成「该去改哪里」。
     *
     * <p>直接把 {@code AuthFailure.SecretIdNotFound} 摆给商家看是没有用的——
     * 他要知道的是「去哪儿填什么」。</p>
     */
    private String explain(String code, String message)
    {
        if (code.startsWith("AuthFailure"))
        {
            return "腾讯云密钥不对，或者这个账号还没开通「机器翻译」服务。"
                    + "密钥用的是「文件存储」里那一对（腾讯云 COS），先去那页确认一下；"
                    + "开通在腾讯云控制台的「机器翻译 TMT」里，开通后即可使用";
        }
        /*
          密钥是对的、但这个账号没开通机器翻译。2026-09-25 esvafit 就是这个：
          那对密钥是为 COS 建的，账号上只开了对象存储。
          ⚠ 和上面 AuthFailure 分开写：那一条要商家先怀疑密钥，这一条密钥没问题，
            让他去核对密钥只会白忙一趟。
        */
        if (code.contains("UserNotRegistered"))
        {
            return "这个腾讯云账号还没开通「机器翻译」服务（密钥本身没问题）。"
                    + "到腾讯云控制台搜「机器翻译 TMT」开通即可，每月有免费额度；开通后再点翻译";
        }
        if (code.contains("ServiceIsolate"))
        {
            return "腾讯云账号欠费，机器翻译服务已被停用，请到腾讯云控制台充值后再试";
        }
        if (code.contains("NoFreeAmount") || code.contains("Arrears") || code.contains("Balance"))
        {
            return "腾讯云机器翻译的免费额度用完了或账户欠费，请到腾讯云控制台查看";
        }
        if (code.startsWith("RequestLimitExceeded"))
        {
            return "翻译请求太频繁，等几秒再点一次";
        }
        if (code.contains("UnsupportedLanguage"))
        {
            return "腾讯云不支持这两种语言之间直译。"
                    + "它的语种对以中文和英文为中心，站点默认语言是中文或英文时最稳妥";
        }
        log.warn("腾讯云机器翻译返回错误：{} {}", code, message);
        return "翻译失败（" + code + "）";
    }

    /** 取腾讯云密钥。⚠ 返回值不许出现在任何响应、任何日志里 */
    private Credentials credentials()
    {
        SiteStorage storage = storageService.selectForDisplay("tencent");
        String secretId = storage == null ? null : trimToNull(storage.getAccessKeyId());
        String secretKey = storageService.storedSecret("tencent");
        if (secretId == null || secretKey == null || secretKey.isBlank())
        {
            throw new ServiceException("还没配腾讯云密钥。翻译和文件存储共用同一对密钥，"
                    + "请先在「文件存储」里把腾讯云 COS 配好");
        }
        return new Credentials(secretId, secretKey);
    }

    /**
     * 站点语言列表里的写法未必等于 TMT 的写法，统一一下。
     *
     * <p>⚠ {@code zh-TW} 是<b>唯一</b>不能剥掉后缀的：剥成 {@code zh} 就变简体了，
     * 而繁体站点要的正是那个后缀。</p>
     */
    private static String normalize(String lang)
    {
        String value = trimToNull(lang);
        if (value == null)
        {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.equals("zh-tw") || value.equals("zh-hk") || value.equals("zh-hant"))
        {
            return "zh-TW";
        }
        // en-us → en，zh-cn → zh
        int dash = value.indexOf('-');
        return dash > 0 ? value.substring(0, dash) : value;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 密钥对。单独一个类型只为了让「这东西不许打日志」这件事在签名上看得见 */
    private record Credentials(String secretId, String secretKey)
    {
    }
}
