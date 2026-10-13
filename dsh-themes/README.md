# 自定义 DSH 主题

两种做法，按你想要的控制力度选。

## 路线 A：主题包（需要 dsh-dream-skin 插件，有 GUI）

`my-aurora.dsh-theme.json` 就是一份合法的主题包，可直接导入。

1. 重新装回插件：`dsh plugin --profile web add dsh-dream-skin`（或走插件市场）
2. 重启 DSH
3. **设置 → 外观（Theme）→ 主题包 → 导入主题包**，选这个 `.dsh-theme.json`
4. 导入后会自动注册并出现在皮肤库里，点「应用」即生效

改配色只要编辑这个 JSON 后重新导入。校验失败通常是缺必填 token 或颜色非法。

### 字段说明

| 字段 | 说明 |
|---|---|
| `format` | 固定 `dsh-dream-skin/pack` |
| `version` | 格式版本，当前为 `1` |
| `manifest.id` | 主题唯一 id，只能小写字母/数字/`-`；导入时会自动加 `dream-pack:` 前缀 |
| `manifest.nameZh` | 可选中文名 |
| `manifest.colorScheme` | `light` 或 `dark`（决定放在哪个库里） |
| `manifest.accent` | 可选，默认强调色 |
| `manifest.tokens` | token 名 → 颜色值 |

### 必填 token（缺了会渲染不完整）

| 组 | Token |
|---|---|
| 背景 | `--dsw-alias-bg-base`、`--dsw-alias-bg-layer-1` |
| 文字 | `--dsw-alias-label-primary`、`--dsw-alias-label-secondary` |
| 品牌 | `--dsw-alias-brand-primary` |
| 边框 | `--dsw-alias-border-l1`、`--dsw-alias-border-l2` |

### 推荐补全的 token

| 组 | Token |
|---|---|
| 层 | `--dsw-alias-bg-layer-2`、`--dsw-alias-bg-layer-3`、`--dsw-alias-bg-overlay` |
| 文字 | `--dsw-alias-label-tertiary` |
| 状态 | `--dsw-alias-state-{business,success,warn,error}-primary` |
| 交互 | `--dsw-alias-interactive-bg-{hover,active}`、`--dsw-alias-button-primary-{fill,hover}` |
| 代码 | `--dsw-alias-markdown-{code-block,inline-code,tag}` |
| 滚动条 | `--dsw-alias-scrollbar-bg-l1`、`--dsw-alias-scrollbar-hover-l1` |
| 侧栏 | `--dsw-specific-sidebar-fill`、`--dsw-specific-sidebar-nav-item-active` |

颜色值支持 `#rgb`、`#rrggbb`、`rgb()`、`rgba()`、`hsl()`、`hsla()`。
`--dsw-alias-*` / `--dsw-specific-*` 是推荐的语义层；`--dsw-static-*` 是底层静态色板，直接改容易产生连锁影响。

## 路线 B：自己写客户端插件（不装任何第三方插件）

原生 API 在 `@deepseek-ai/dsh-client-ui-theme` 的 `ThemeRuntime` 上：

- `ctx.theme.register({ id, colorScheme, tokens })` —— 注册全新主题，并出现在「设置 → 通用设置 → 外观」列表中
- `ctx.theme.overrideTokens(source, tokens)` —— 在**当前**主题上叠一层覆盖，`tokens` 形如 `{ '--dsw-alias-bg-base': { light: '#fff', dark: '#111' } }`

注意：`overrideTokens` 的每个 token 必须显式写 `{ light, dark }` 两个值（传裸字符串会抛错）；`register` 的 `tokens` 是扁平的。

插件形状（client-only，可动态安装）：

```json
{
  "name": "my-dsh-theme",
  "private": true,
  "dsh": {
    "client": { "platform": "web" },
    "bundle": { "patch": "./cordis.patch.yml" }
  }
}
```

界面上的「设置 → 通用设置 → 外观」里有一个 token 检查器，会列出每个 `--dsw-alias-*` 的用途，可以照着它调。
