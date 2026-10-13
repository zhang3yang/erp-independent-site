# 装修用的占位图

`deco/` 下两张图是 `sql/station/03_init_decorate.sql` 引用的**占位图**，从
`kangwanjia-website-EN/public/img/` 搬过来的：

| 文件 | 来源 | 装修里的哪一格 |
| --- | --- | --- |
| `deco/home-hero.jpg` | `kwj-hero-1.jpg` | Banner → 背景图 |
| `deco/about-factory.jpg` | `kwj-lab.jpg` | 关于我们 → 配图 |

## 放到服务器上

装修 JSON 里写的是 `/files/deco/xxx.jpg`。`/files/` 在 nginx 里 alias 到
`/data/station/upload/`（见 [deploy/nginx/tongling.conf](../nginx/tongling.conf)），
所以放进去就能访问，**不用重新构建前台**：

```bash
scp deploy/site-assets/deco/*.jpg ubuntu@<IP>:/tmp/
```

```bash
install -o station -g station -m 644 -D /tmp/home-hero.jpg     /data/station/upload/deco/home-hero.jpg
install -o station -g station -m 644 -D /tmp/about-factory.jpg /data/station/upload/deco/about-factory.jpg
# /files/ 在主域和 buyer. 两个 host 上都配了（deploy/nginx/tongling.conf），
# 验主域这一个就够——装修内容里的图是给公开页用的
curl -sI https://<主域名>/files/deco/home-hero.jpg | head -1   # 期望 200
```

换成客户自己的照片时，覆盖这两个文件就行，装修内容不用动；或者传到对象存储，
在管理台「首页装修」里把那两格改成完整 URL（**注意那两格是分语言的，七种语言各一份**，
批量改用 SQL 的 `REPLACE(contentjson, 旧地址, 新地址)` 快得多）。

## ⚠ 别放回 `station-site/public/`

这两张图**曾经**放在那儿，路径写的 `/img/home-hero.jpg`。看着更自然，实际不行：

- Nitro 的静态资源清单是**构建时**生成的，运行时不扫盘。把文件塞进已部署的
  `.output/public/` 里，重启也还是 404（实测过）——必须重新构建、重新发布整个前台
  才能加一张图；
- 而装修图片是**运营内容**，商家随时会换。让它跟着代码走，等于每换一张图就要走一次
  发布流程。

`/files/` 那条路由本来就是给这类内容留的（`station.upload.base-url` 的落地点），
和商品图走同一套。
