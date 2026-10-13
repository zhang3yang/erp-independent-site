#!/usr/bin/env bash
#
# 检查 MyBatis mapper XML 里有没有会让应用起不来的写法。
#
# ## 为什么需要这个脚本
#
# 2026-09-08 同一个错踩了两次，第二次还上了线：XML 规范**禁止注释内容里出现 `--`**，
# 而中文注释里用 `------` 当分隔线是很自然的写法。
#
# 后果不是「注释显示得怪」，而是**整个后端起不来**：
#
#     MyBatis 解析 mapper 失败
#       → sqlSessionFactory 建不出来
#       → 所有 mapper bean 失败
#       → Spring 上下文起不来，服务进 StartPending 然后挂掉
#
# 而报错里只有一句 `Failed to parse mapping resource`，**不告诉你是哪一行**，
# 也不告诉你原因是注释。第一次排查花了不少时间。
#
# 更坑的是它**编译期发现不了**：`mvn compile` 只编译 Java，XML 是运行时才解析的。
# 所以「编译通过」「打包成功」都不能说明 mapper 是好的——线上那次就是这么挂的。
#
# ## 另一条会让它起不来的路
#
# 2026-09-11 又挂了一次，同一句 `Failed to parse mapping resource`，但源码是干净的：
# 撤销邮件群发（442f02cb）删掉了 SiteCampaignMapper 的接口、domain 和 XML，
# 可之后只 `mvn compile` 没 `mvn clean`，`target/classes/mapper/` 里那份旧 XML 还在。
# MyBatis 按 `classpath*:mapper/*.xml` 扫描，照样读到它，
# 而 namespace 和 resultMap 指向的类已经不存在了，于是解析失败，后面那串连锁反应一模一样。
#
# 这个坑比上面那个更容易查错方向：报错路径在 `target/` 底下，
# 而 `git status` 是干净的、源码目录里也搜不到这个文件，很容易以为是 SQL 或配置写错了。
# 所以下面检查三专门比一遍「产物里有、源码里没有」。
#
# ## 怎么用
#
#     bash scripts/check-mapper-xml.sh
#
# 全部通过则 exit 0，有问题打出文件和行号并 exit 1。
# 改完任何 mapper XML 之后跑一遍，比等启动失败便宜得多。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIL=0

# 所有模块下的 mapper 目录
MAPPER_DIRS=$(find "$ROOT" -type d -name mapper -path '*/main/resources/*' 2>/dev/null)

if [ -z "$MAPPER_DIRS" ]; then
  echo "找不到任何 mapper 目录，检查脚本放对位置了吗"
  exit 1
fi

COUNT=0
for dir in $MAPPER_DIRS; do
  # ⚠ 必须递归。ruoyi-system 的 60 个 mapper 全在 mapper/inventory、mapper/finance
  #   这类子目录里，直接子级一个都没有；只扫 "$dir"/*.xml 的话这 60 个查不到，
  #   而 mapperLocations 是 classpath*:mapper/**/*Mapper.xml，MyBatis 照样加载它们。
  #   2026-09-11 加看板 mapper 时发现的：脚本报「22 个全部通过」，
  #   可仓库里有 85 个 XML。
  for f in $(find "$dir" -name '*.xml' -type f | sort); do
    [ -e "$f" ] || continue
    COUNT=$((COUNT + 1))
    rel="${f#$ROOT/}"

    # ---- 检查一：注释里的 "--" ----
    #
    # 用 awk 逐行走一个「在不在注释里」的状态机。为什么不用一条正则：
    # 注释可以跨很多行，而 grep 是逐行的——单行正则看不出「这一行在注释内部」。
    #
    # ⚠ 只报注释**内部**的 --，不报 <!-- 和 --> 本身，也不报 SQL 里的
    #   （SQL 注释用 -- 是合法的，只是这些 mapper 里没这么写）。
    bad=$(awk '
      {
        line = $0
        # 先把本行的 <!-- 和 --> 记下位置，再判断正文
        if (in_comment) {
          # 注释内部：找 --> 之前有没有 --
          p = index(line, "-->")
          body = (p > 0) ? substr(line, 1, p - 1) : line
          if (body ~ /--/) print NR ": " $0
          if (p > 0) in_comment = 0
        } else {
          p = index(line, "<!--")
          if (p > 0) {
            rest = substr(line, p + 4)
            q = index(rest, "-->")
            body = (q > 0) ? substr(rest, 1, q - 1) : rest
            if (body ~ /--/) print NR ": " $0
            if (q == 0) in_comment = 1
          }
        }
      }
    ' "$f")

    if [ -n "$bad" ]; then
      echo "✗ $rel —— 注释里有 \"--\"，XML 规范不允许，MyBatis 会解析失败："
      echo "$bad" | sed 's/^/    /'
      FAIL=1
    fi

    # ---- 检查二：XML 本身合法吗 ----
    #
    # 用 node 做一个轻量校验。⚠ 不引入任何依赖：这台开发机上 node 一定有
    # （前端要用），而 xmllint 在 Windows 上不一定有。
    #
    # 这里只查注释规则和标签配对这两类致命问题，不做 DTD 校验——
    # DTD 在 mybatis.org 上，而服务器和这里都不一定连得上外网。
    if command -v node >/dev/null 2>&1; then
      node -e '
        const fs = require("fs");
        const s = fs.readFileSync(process.argv[1], "utf8");
        // 标签配对的粗查：数一下开合是否平衡（忽略自闭合和注释、CDATA）
        const stripped = s
          .replace(/<!--[\s\S]*?-->/g, "")
          .replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, "")
          .replace(/<\?[\s\S]*?\?>/g, "")
          .replace(/<!DOCTYPE[^>]*>/g, "");
        const opens = [];
        const re = /<(\/?)([A-Za-z_][\w.-]*)([^>]*?)(\/?)>/g;
        let m;
        while ((m = re.exec(stripped))) {
          const [, close, name, attrs, self] = m;
          if (self === "/") continue;
          if (close === "/") {
            const top = opens.pop();
            if (top !== name) {
              console.error(`标签不配对：遇到 </${name}>，但最近打开的是 <${top}>`);
              process.exit(1);
            }
          } else {
            opens.push(name);
          }
        }
        if (opens.length) {
          console.error(`有未闭合的标签：${opens.join(", ")}`);
          process.exit(1);
        }
      ' "$f" 2>/tmp/xmlcheck.$$ || {
        echo "✗ $rel —— XML 结构有问题："
        sed 's/^/    /' /tmp/xmlcheck.$$
        FAIL=1
      }
      rm -f /tmp/xmlcheck.$$
    fi
  done
done

# ---- 检查三：target/classes 里的孤儿 XML ----
#
# 上面两项只看源码。但真正喂给 MyBatis 的是 target/classes，
# 而 mvn 不会把「源码里已经删掉的资源」从 target/classes 里清走。
# 所以这里反过来查：产物里有、源码里没有的，就是回滚后没 clean 的残留。
#
# ⚠ 判据是相对 mapper/ 的路径在不在源码目录里，不是「git 里有没有」——
#   新写还没提交的 mapper 不该被当成孤儿。
ORPHAN_FOUND=0

for dir in $MAPPER_DIRS; do
  # .../<模块>/src/main/resources/mapper  →  .../<模块>
  module="${dir%/main/resources/mapper}"
  module="${module%/src}"
  tgt="$module/target/classes/mapper"

  # 没编译过（干净 clone、刚 clean 完、CI 上）就没什么可比的
  [ -d "$tgt" ] || continue

  for f in $(find "$tgt" -name '*.xml' -type f | sort); do
    [ -e "$f" ] || continue
    # 比的是相对 mapper/ 的路径而不是文件名：不同子目录下允许同名 XML，
    # 只按文件名比会把 inventory/XMapper.xml 认成 finance/XMapper.xml 的孤儿。
    rel="${f#$tgt/}"
    [ -e "$dir/$rel" ] && continue

    if [ "$ORPHAN_FOUND" -eq 0 ]; then
      echo "✗ target/classes 里有源码里已经没有的 mapper XML："
      ORPHAN_FOUND=1
    fi
    echo "    ${f#$ROOT/}"
    FAIL=1
  done
done

if [ "$ORPHAN_FOUND" -eq 1 ]; then
  echo "  这是删文件后只 compile 没 clean 留下的残留。MyBatis 照样会扫到它，"
  echo "  而它引用的类已经不存在了，启动会报 Failed to parse mapping resource。"
  echo "  删掉上面这些文件，或者跑一次 mvn clean。"
fi

if [ "$FAIL" -eq 0 ]; then
  echo "✓ $COUNT 个 mapper XML 全部通过，target/classes 里没有残留"
else
  echo ""
  echo "⚠ 上面这些问题**编译期发现不了**（mvn compile 不解析 XML），"
  echo "  但会让 station-console 起不来。改完再跑一遍这个脚本。"
fi
exit "$FAIL"
