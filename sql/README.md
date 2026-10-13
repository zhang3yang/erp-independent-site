# 数据库脚本

这个目录只有两个脚本。拉下项目要做的就是按顺序跑一遍，然后启动后端。

```bash
mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < sql/01_schema_and_init.sql   # 必须
mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < sql/02_demo_data.sql         # 可选
```

跑完改 `RuoYi-Vue/ruoyi-admin/src/main/resources/application-druid.yml` 里的账号密码，启动
`ruoyi-admin`，用 `wczladmin / admin123` 登录。

| 文件 | 内容 |
|---|---|
| `01_schema_and_init.sql` | 四个库的全部表结构，加菜单、字典、参数、角色、账号、三个出厂商家、单据类型 |
| `02_demo_data.sql` | 商品分类、商品档案、仓库、往来单位。不装也能跑，只是界面上没数据 |
| `migration/` | 上一次发布之后新增的结构变更，见下文 |
| `tools/rebuild_baseline.sh` | 重新生成上面两个文件的脚本 |

## 四个库，库名不能改

| 库 | 装什么 |
|---|---|
| `sdssys` | 菜单、字典、角色、账号、租户、定时任务 |
| `sdsshop` | 商品、仓库、库存、批次、序列号、采购、生产、独立站站点档案 `u_s_site` |
| `sdsorder` | 出入库单、流水、订单、发货批次、资金流水 |
| `sdsstation` | 独立站上架商品、多语言文案、图片视频、询盘（整库归站点服务器，ERP 侧迟早删，见 `../docs/独立站与ERP的边界.md` §5.2） |

库名在几十个 mapper XML 里是写死的（`from sdsshop.u_f_account` 这种），不是配置项。想换库名
得连着 XML 一起改，别改。`application-druid.yml` 里只配 `sdssys` 一个，另外三个靠这些前缀跨库
访问，所以四个库必须在同一个 MySQL 实例上、同一个账号能访问。

## 月表不在脚本里

`sdsorder` 下有 11 类按月分表（`u_f_docmain_yyyyMM`、`u_o_order_yyyyMM`、`u_f_ledger_yyyyMM` ……），
由 `MonthlyTableInitializer` 在后端启动时自动建当月和下月，基线一张都不建。

**所以执行完 SQL 直接启动后端就行，不要手工建月表。** 反过来说，给月表加列改的不是这里，是：

1. `ruoyi-system/.../runner/MonthlyTableInitializer.java` 里对应的 `build*DDL` 方法 —— 管以后新建的月表；
2. `migration/` 下一个 `ALTER` 脚本 —— 管已经存在的月表。

两处漏一处，下个月 1 号新建的表就和这个月的对不上，查询直接报 `Unknown column`。

## 出厂数据

三个商家：天津康万家（`sp708xm02q`）、常德睿腾（`sp708xm02r`）、天津康晨（`sp708xm02u`）。

| 账号 | 是谁 |
|---|---|
| `wczladmin` | 平台超级管理员，三个商家都能看 |
| `us208XQ03` | 天津康万家 的商家管理员 |
| `us208XQ02` | 常德睿腾 / 天津康晨 的商家管理员 |

密码统一 `admin123`。这是公开的默认口令，部署到任何真实环境前先改掉。

`02_demo_data.sql` 只给主数据，不给库存、批次、流水和单据 —— 那些依赖月表，而月表是后端启动时
才建的，硬塞一份进来只会造出对不上账的库存。想要有货的库，启动后端后在界面上走一遍采购入库，
链路和数据都是真的。

## 加新的结构变更

**不要手改 `01_schema_and_init.sql`**，它是生成的，下次重新生成会被覆盖。

新的变更放 `migration/`，文件名 `YYYYMMDD_简短说明.sql`，写成可重复执行的（用
`IF NOT EXISTS` / `WHERE NOT EXISTS` 之类），因为同一个脚本在本地、远程、别人机器上都要跑。
脚本开头写清楚改了什么、为什么这么改。

发布之后合并回基线：

```bash
# 1. 把 migration/ 里的脚本在本地库跑完，确认本地库是最新状态
# 2. 重新生成基线
MYSQL_PWD=xxx sql/tools/rebuild_baseline.sh
# 3. 清空 migration/，提交
```

这样 `migration/` 永远只有「上次发布之后」的几个文件，不会再攒成一百多个。

## ⚠ 脚本自带 `USE`，会无视你当前选的库

`01_schema_and_init.sql` 里每一段前面都有 `USE sdssys` / `USE sdsshop` / `USE sdsorder` /
`USE sdsstation`。**在客户端里先建一个 `sdssys_test` 再执行本文件是没有用的** —— 脚本会跳回
上面那四个库。想灌到别的库名下验证，必须先替换文件里的库名：

```bash
# Git Bash
sed 's/`sdssys`/`t_sdssys`/g; s/`sdsshop`/`t_sdsshop`/g;
     s/`sdsorder`/`t_sdsorder`/g; s/`sdsstation`/`t_sdsstation`/g' \
    sql/01_schema_and_init.sql > /tmp/t01.sql
mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < /tmp/t01.sql
```

```powershell
# PowerShell
$s = Get-Content -Raw -Encoding UTF8 sql\01_schema_and_init.sql
$s = $s -replace '`sdssys`','`t_sdssys`' -replace '`sdsshop`','`t_sdsshop`' `
        -replace '`sdsorder`','`t_sdsorder`' -replace '`sdsstation`','`t_sdsstation`'
[System.IO.File]::WriteAllText("$env:TEMP\t01.sql", $s, (New-Object System.Text.UTF8Encoding($false)))
cmd /c "mysql -h 127.0.0.1 -uroot -p --default-character-set=utf8mb4 < ""$env:TEMP\t01.sql"""
```

换了库名之后应用是连不上的（库名写死在 mapper XML 里），那样只能验 SQL 本身。要让应用真跑起来，
库名必须还是那四个 —— 换台机器、或者本机再起一个 MySQL 实例（另一个端口），别在开发库上试。

## 已经在跑的环境不要执行基线

`01_schema_and_init.sql` 全文只有 `CREATE DATABASE IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` /
`INSERT IGNORE`，没有 `DROP`、没有 `TRUNCATE`、没有 `DELETE`、没有 `ALTER`。跑在有数据的库上不会
毁东西 —— 但它也不会把已有的表改成新结构，已存在的表会被整张跳过。它是给新拉取项目的人用的
出厂脚本，不是迁移工具。已有环境（包括远程库）用 `migration/` 下的增量脚本。

改这个文件或 `tools/rebuild_baseline.sh` 之后，**务必确认输出里一条破坏性语句都没有**：

```bash
grep -niE '^\s*(drop|truncate|delete)\b' sql/01_schema_and_init.sql sql/02_demo_data.sql
```

mysqldump 默认会在每个 `CREATE TABLE` 前面加 `DROP TABLE IF EXISTS`，生成器靠
`--skip-add-drop-table` 关掉它。这个参数掉了一次，代价是一个开发库。

## 历史脚本

2026-08-28 之前的 139 个增量脚本已经合并进基线并从版本库删除。要查某次改动当时是怎么写的：

```bash
git ls-tree sql-archive-20260828 RuoYi-Vue/sql/
git show sql-archive-20260828:RuoYi-Vue/sql/inventory_stock_state_two_dim_20260818.sql
```
