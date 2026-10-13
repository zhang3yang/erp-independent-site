#!/usr/bin/env bash
#
# 独立站装修内容那套逻辑的自动化校验。发版前跑一遍，几秒钟。
#
#   bash scripts/verify/run-all.sh
#
# 为什么这几项值得单独有个脚本：装修合并那段代码影响**每个站点的每个页面**，
# 而这个项目里 station-console 没有测试框架（没有 src/test）。这三项是在
# 「不引入测试框架」的前提下，能自动化的那部分。
#
# 需要：java（17+，跑单文件源码）、node。两样本机都有。
set -u

cd "$(dirname "$0")/../.." || exit 1
fail=0

run() {
  local name="$1"; shift
  echo "── $name"
  if "$@"; then
    echo "   ok"
  else
    echo "   ✗ 不通过"
    fail=1
  fi
  echo
}

# 1. 真值表跑之前先确认它测的是**真代码**。
#    MergeCheck.java 里那份 overlay 是从 SiteOpenService 拷来的，拷贝会过期。
run "合并逻辑：拷贝有没有和真源码漂移" \
  node scripts/verify/check-merge-copy.js \
    station-console/src/main/java/com/ruoyi/station/console/service/SiteOpenService.java \
    scripts/verify/MergeCheck.java

# 2. 三层合并 + 拼接的真值表。
#    v1/v2/v3/v4、半译按条回退、对象合一层、数组整份替换、不污染共享层、
#    文案袋子不外泄。
run "合并逻辑：真值表" \
  java scripts/verify/MergeCheck.java

# 2b. 管理台那张表单的「载入 → 保存」往返。
#     跑的是 DecorateForm.vue 里真的那段逻辑（把 <script setup> 抠出来在 Node 里跑），
#     验的是「结构只存一份、文案按语言各存一份」那条线：老数据升得上来、
#     切语言不动结构、存出去是新结构、再载一次不变形。
#     ⚠ 模板里的绑定它验不到，见那个文件头。
run "装修表单：载入 → 保存 往返" \
  node scripts/verify/decorate-roundtrip.mjs

# 2c. 首页区块的「四层接线」：前台模板 / 前台 script / 管理台表单 / 后端拼接
#     四处对不上时构建都不报错，症状全是安静的（某个语言显示 undefined、
#     管理台排的顺序和首页不一样、一排填了字却空白的卡片）。
run "首页区块：四层接线对不对得上" \
  node scripts/verify/check-block-wiring.js

# 2d. 主题配色的对比度。颜色是最容易被「顺手调一下」的东西，而调过头没有任何报错。
#     脚本从 site.css 真读 token，所以改了主题这里会跟着变。
run "主题配色：对比度达不达标" \
  node scripts/verify/check-contrast.js

# 3. 新站初始装修内容那条 UPDATE 里的 JSON 是不是合法的。

#    它是一整行几万字符的字面量，肉眼看不出坏没坏，而坏了要到页面上才发现。
run "新站初始装修内容：SQL 里那段 JSON" \
  node scripts/verify/check-init-json.js sql/station/03_init_decorate.sql

if [ "$fail" -eq 0 ]; then
  echo "全部通过"
else
  echo "有不通过的，别发版"
fi
exit "$fail"
