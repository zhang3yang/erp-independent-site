package com.ruoyi.system.runner;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 月表自动建表器
 * <p>
 * 项目启动时自动检查当月的业务月表是否存在，不存在则自动创建；
 * 启动时和每天凌晨各确认一次<b>当月和下月</b>的月表，提前一个月建好——
 * 请求链路上不建表（在业务事务里 DDL 会撞 1412），只做存在性检查。
 * 启动时还会把<b>全部历史月表</b>扫一遍补上升级后新增的列，
 * 见 {@link #upgradeExistingMonthlyTables()}。
 * 库存月表：u_f_docmain_{yyyyMM}、u_f_docdetail_{yyyyMM}、u_f_ledger_{yyyyMM}
 * 采购到货月表：u_p_receive_{yyyyMM}、u_p_receivedetail_{yyyyMM}
 * 生产执行日志月表：u_f_productionlog_{yyyyMM}
 * 资金流水月表：u_f_transaction_{yyyyMM}
 * 订单月表：u_o_order_{yyyyMM}、u_o_orderdetail_{yyyyMM}
 * 发货批次月表：u_f_shipbatch_{yyyyMM}、u_f_shipbatchdetail_{yyyyMM}
 * </p>
 */
@Component
@Order(10)
public class MonthlyTableInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(MonthlyTableInitializer.class);

    private static final String SCHEMA = "sdsorder";
    private static final String SCHEDULE_ZONE = "Asia/Shanghai";
    private static final ZoneId MONTH_ZONE = ZoneId.of(SCHEDULE_ZONE);
    private static final DateTimeFormatter SUFFIX_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private DataSource dataSource;

    /**
     * 一个业务模块落一笔单要用到的月表。
     *
     * <p>{@link #requireTablesFor} 必须按模块查，不能所有调用方都查同一组：各模块的月表是
     * 分批加进来的，历史月份常常只有其中一部分（比如 202607 只有库存那三张，
     * 发货批次和订单的月表是后来才有的）。统一查全部会把「那个月本来就能开的库存单」
     * 也一起拦掉；统一只查库存三张，则是现在这个 bug——发货批次查过了，
     * insert 的时候才发现 u_f_shipbatch_YYYYMM 根本不存在。</p>
     */
    public enum TableGroup
    {
        /** 库存单据：主表、明细、流水 */
        INVENTORY("u_f_docmain", "u_f_docdetail", "u_f_ledger"),
        /** 发货批次：主表、明细 */
        SHIP_BATCH("u_f_shipbatch", "u_f_shipbatchdetail"),
        /** 采购到货：主表、明细 */
        PURCHASE_RECEIVE("u_p_receive", "u_p_receivedetail");

        private final List<String> prefixes;

        TableGroup(String... prefixes)
        {
            this.prefixes = Collections.unmodifiableList(Arrays.asList(prefixes));
        }

        public List<String> prefixes()
        {
            return prefixes;
        }
    }

    /**
     * 本进程内已经<b>整月建齐</b>的月份后缀，避免每次建单都去查一遍 information_schema。
     *
     * <p>只由建表路径写入（{@link #ensureTablesFor}、{@link #ensureCurrentMonthTables}），
     * 所以「在这个集合里」等价于「{@link #ensureMonthlyTables} 里那一整套表都在」。
     * {@link #requireTablesFor} <b>不往里写</b>——它一次只确认一个模块的两三张表，
     * 写进来会让别的模块误以为自己那几张也查过了。它自己用 {@link #verifiedTables}。</p>
     */
    private final Set<String> ensuredSuffixes = ConcurrentHashMap.newKeySet();

    /** {@link #requireTablesFor} 确认过存在的单张月表全名，逐表缓存，理由见上。 */
    private final Set<String> verifiedTables = ConcurrentHashMap.newKeySet();

    @Override
    public void run(ApplicationArguments args)
    {
        // 这里不再吞异常：月表建不出来，之后所有落到这个月的单据都会在运行时炸，
        // 与其让服务假装启动成功，不如启动阶段就失败。
        ensureCurrentMonthTables();
        // 当月和下月之外的历史月表也要补列，见方法注释
        upgradeExistingMonthlyTables();
    }

    /**
     * 每天凌晨跑一次，确保<b>当月和下月</b>的月表都在。
     *
     * <p>原先是「每月 1 号只建当月」。两个问题：跨月那一刻建表和第一笔单据是抢的，
     * 而且业务员在月底开一张日期填下个月的单（提前开单、排下月的发货批次）时，
     * 下月表根本还不存在。提前一个月建出来，这两种情况就都不用在请求链路上补表了——
     * <b>而补表这件事在业务事务里是做不成的，见 {@link #requireTablesFor}。</b></p>
     *
     * <p>每天跑而不是每月跑：漏了一天第二天就补上，代价是每天两次
     * {@code CREATE TABLE IF NOT EXISTS}，可以忽略。</p>
     */
    @Scheduled(cron = "0 10 0 * * *", zone = SCHEDULE_ZONE)
    public void ensureCurrentAndNextMonthTablesDaily()
    {
        try
        {
            ensureCurrentMonthTables();
        }
        catch (RuntimeException e)
        {
            // 定时任务里不能往上抛，抛了这一轮就没人记录。打 ERROR 让告警能抓到。
            log.error("月表定时建表失败，请立即检查数据库: {}", e.getMessage(), e);
        }
    }

    /**
     * 建好指定月份的月表。
     *
     * <p><b>⚠ 只能在业务事务之外调用</b>（启动器、定时任务、运维脚本）。
     * 建表是 DDL，MySQL 会立即提交；如果调用方正开着事务，事务的表定义快照就落在
     * DDL 之前，接下来读这张表会直接撞
     * {@code 1412 Table definition has changed, please retry transaction}——
     * 表建出来了，这一笔仍然失败，下一笔才成功。请求链路上要判月表在不在，
     * 用 {@link #requireTablesFor}。</p>
     *
     * @param suffix yyyyMM 月表后缀，非法直接抛（后缀是要拼进 DDL 的）
     */
    public void ensureTablesFor(String suffix)
    {
        String checked = MonthTableSuffix.require(suffix);
        if (!ensuredSuffixes.add(checked))
        {
            return;
        }
        try
        {
            ensureMonthlyTables(checked);
        }
        catch (RuntimeException e)
        {
            // 建失败就把标记撤回去，否则这个进程再也不会重试这个月份
            ensuredSuffixes.remove(checked);
            throw e;
        }
    }

    /**
     * 落单前确认目标月份的月表已经建好，没建好就报一句人能看懂的话。
     *
     * <p><b>这里只查不建，是有意的。</b>见 {@link #ensureTablesFor} 上的说明：
     * 在业务事务里建表会撞 1412，把「表不存在」换成一个更难懂的错，
     * 还会留下一批建了没用上的表。月表只在事务之外提前建（启动时 + 每天的定时任务，
     * 各建当月和下月），这里发现没建只能拦下来。</p>
     *
     * <p>正常业务落不到这里：当月和下月一直是建好的。会走到这里的是
     * 「单据日期填到两个月以后」和「补录上线之前的历史月份」，
     * 两者都该由管理员先把那几个月的表建出来，而不是由一次业务请求顺手创建。</p>
     *
     * <p><b>必须按模块传 {@code group}</b>，原因见 {@link TableGroup}：写死查库存那三张，
     * 发货批次和采购到货就会「检查通过、insert 撞表不存在」，报出来是原始 SQL 错误。</p>
     *
     * @param group 本次落单要用到的月表组，按调用方所属模块传
     */
    public void requireTablesFor(String suffix, TableGroup group)
    {
        String checked = MonthTableSuffix.require(suffix);
        // 整月建齐过就不用逐表查了，见 ensuredSuffixes 的注释
        if (ensuredSuffixes.contains(checked))
        {
            return;
        }
        List<String> missing = new ArrayList<>();
        try (Connection conn = dataSource.getConnection())
        {
            for (String tablePrefix : group.prefixes())
            {
                String tableName = tablePrefix + "_" + checked;
                if (verifiedTables.contains(tableName)) continue;
                if (tableExists(conn, SCHEMA, tableName)) verifiedTables.add(tableName);
                else missing.add(tableName);
            }
        }
        catch (Exception e)
        {
            log.error("检查月表 {} 是否存在时出错: {}", checked, e.getMessage(), e);
            throw new ServiceException("无法确认 " + checked + " 月份的数据表状态，请联系管理员");
        }
        if (!missing.isEmpty())
        {
            // 把缺的表名带上：管理员照着建就行，不用再去翻代码猜这个模块要哪几张
            throw new ServiceException("单据日期所在月份（" + checked.substring(0, 4) + "年"
                    + checked.substring(4) + "月）的数据表还没创建，本单无法保存。"
                    + "请把日期改到本月或下月，或联系管理员先建好该月份的月表（缺："
                    + String.join("、", missing) + "）");
        }
    }

    /** 当月 + 下月。提前一个月建好，跨月和「提前开下月单」就都不用在请求里补表 */
    private void ensureCurrentMonthTables()
    {
        YearMonth current = YearMonth.now(MONTH_ZONE);
        for (YearMonth month : new YearMonth[] { current, current.plusMonths(1) })
        {
            String suffix = month.format(SUFFIX_FMT);
            ensureMonthlyTables(suffix);
            ensuredSuffixes.add(suffix);
        }
    }


    /**
     * 给<b>所有已存在的</b>月表补上升级后新增的列，不只是当月和下月。
     *
     * <p>{@link #ensureCurrentMonthTables()} 只走当月和下月两个后缀，而补列这件事是搭在
     * {@link #ensureTable} 里的，所以历史月表一张都碰不到。后果不是少显示一个字段：
     * 月表的列清单是写死在 mapper 里的，缺一列，那个月的每一次查询都直接
     * {@code Unknown column}，整块功能打不开——而且报错的是<b>跨月扫全部月表</b>的那类查询
     * （按订单查全部发货批次就是），一张老表缺列能把当月的页面也一起带挂。</p>
     *
     * <p>加过列之后第一次启动就会把历史表刷齐，不必等人想起来去跑 sql/migration 下的脚本。
     * 脚本仍然留着：线上可能先跑脚本再发版，两边做的是同一件事，都写成可重复执行。</p>
     *
     * <p>只在启动时跑一次，不进每天的定时任务：历史月表的集合只会每月多一张，
     * 而新的那一张当月就被 {@link #ensureCurrentMonthTables()} 按最新 DDL 建出来了，
     * 天天扫一遍 information_schema 没有意义。</p>
     *
     * <p>失败只告警不抛，与 {@link #ensureAddedColumns} 同一口径：可能是权限不足，
     * 也可能列已经被脚本加过。真缺列的话第一次查询就会报得很清楚。</p>
     */
    private void upgradeExistingMonthlyTables()
    {
        try (Connection conn = dataSource.getConnection())
        {
            for (String tablePrefix : ADDED_COLUMNS.keySet())
            {
                for (String tableName : monthlyTableNamesOf(conn, tablePrefix))
                {
                    // 当月和下月刚在上一步补过，重复调只多两次 columnExists，不值得单独排除
                    ensureAddedColumns(conn, tableName, tablePrefix);
                }
            }
        }
        catch (Exception e)
        {
            log.warn("补历史月表的新增列失败，原因: {}。请手动执行 sql/migration 下对应的脚本",
                    e.getMessage());
        }
    }

    /** 库里现有的 {@code <prefix>_yyyyMM} 表名。正则限定 6 位数字，避免误伤同前缀的非月表。 */
    private List<String> monthlyTableNamesOf(Connection conn, String tablePrefix) throws Exception
    {
        List<String> names = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = ? AND table_name REGEXP ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql))
        {
            stmt.setString(1, SCHEMA);
            stmt.setString(2, "^" + tablePrefix + "_[0-9]{6}$");
            try (ResultSet rs = stmt.executeQuery())
            {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    /**
     * 确保指定月份的三张月表都存在
     */
    private void ensureMonthlyTables(String suffix)
    {
        ensureTable(suffix, "u_f_docmain", buildDocMainDDL(suffix));
        ensureTable(suffix, "u_f_docdetail", buildDocDetailDDL(suffix));
        ensureTable(suffix, "u_f_ledger", buildLedgerDDL(suffix));
        ensureTable(suffix, "u_p_receive", buildPurchaseReceiveDDL(suffix));
        ensureTable(suffix, "u_p_receivedetail", buildPurchaseReceiveDetailDDL(suffix));
        ensureTable(suffix, "u_f_productionlog", buildProductionLogDDL(suffix));
        ensureTable(suffix, "u_f_transaction", buildFinanceTransactionDDL(suffix));
        ensureTable(suffix, "u_o_order", buildOrderDDL(suffix));
        ensureTable(suffix, "u_o_orderdetail", buildOrderDetailDDL(suffix));
        ensureTable(suffix, "u_f_shipbatch", buildShipBatchDDL(suffix));
        ensureTable(suffix, "u_f_shipbatchdetail", buildShipBatchDetailDDL(suffix));
    }

    /**
     * 检查表是否存在，不存在则执行建表 DDL
     */
    private void ensureTable(String suffix, String tablePrefix, String ddl)
    {
        String tableName = tablePrefix + "_" + suffix;
        try (Connection conn = dataSource.getConnection())
        {
            if (tableExists(conn, SCHEMA, tableName))
            {
                ensureCostPrecision(conn, tableName, tablePrefix);
                ensureAddedColumns(conn, tableName, tablePrefix);
                log.debug("月表已存在，跳过: {}.{}", SCHEMA, tableName);
                return;
            }
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute(ddl);
                log.info("已创建月表: {}.{}", SCHEMA, tableName);
            }
        }
        catch (Exception e)
        {
            // 不能吞：吞掉只是把错误推迟到第一笔单据落库时，那时候报出来的是原始 SQL 错误，
            // 排查成本高得多。启动路径上抛出去会直接让应用启动失败。
            log.error("创建月表失败: {}.{}，原因: {}", SCHEMA, tableName, e.getMessage(), e);
            throw new ServiceException("月表 " + SCHEMA + "." + tableName + " 创建失败：" + e.getMessage());
        }
    }

    /** 已存在月表也校准成本精度，避免不同月份使用不同口径。 */
    private void ensureCostPrecision(Connection conn, String tableName, String tablePrefix)
    {
        String sql;
        String priceColumn;
        String amountColumn;
        if ("u_f_docdetail".equals(tablePrefix))
        {
            priceColumn = "costprice";
            amountColumn = "costamount";
            sql = "ALTER TABLE `" + SCHEMA + "`.`" + tableName
                    + "` MODIFY COLUMN `costprice` decimal(18,6) DEFAULT 0.000000,"
                    + " MODIFY COLUMN `costamount` decimal(18,2) DEFAULT 0.00";
        }
        else if ("u_f_ledger".equals(tablePrefix))
        {
            priceColumn = "cost_price";
            amountColumn = "cost_amount";
            sql = "ALTER TABLE `" + SCHEMA + "`.`" + tableName
                    + "` MODIFY COLUMN `cost_price` decimal(18,6) DEFAULT 0.000000,"
                    + " MODIFY COLUMN `cost_amount` decimal(18,2) DEFAULT 0.00";
        }
        else
        {
            return;
        }
        try
        {
            if (columnHasPrecision(conn, tableName, priceColumn, 18, 6)
                    && columnHasPrecision(conn, tableName, amountColumn, 18, 2))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("检查月表成本精度失败: {}.{}，原因: {}", SCHEMA, tableName, e.getMessage());
            return;
        }
        try (Statement stmt = conn.createStatement())
        {
            stmt.execute(sql);
        }
        catch (Exception e)
        {
            log.warn("校准月表成本精度失败: {}.{}，原因: {}", SCHEMA, tableName, e.getMessage());
        }
    }

    /**
     * 升级后新增的月表列，按表前缀分组：列名 → ADD COLUMN 子句（不含 ALTER TABLE 前缀）。
     *
     * <p>DDL 必须与对应的 build*DDL 方法保持一致，改动两边同步。</p>
     */
    private static final Map<String, Map<String, String>> ADDED_COLUMNS = buildAddedColumns();

    private static Map<String, Map<String, String>> buildAddedColumns()
    {
        Map<String, Map<String, String>> byTable = new LinkedHashMap<>();

        Map<String, String> orderDetail = new LinkedHashMap<>();
        orderDetail.put("custom_made", "ADD COLUMN `custom_made` tinyint(1) NOT NULL DEFAULT 0"
                + " COMMENT '专门生产（定制品）：1=库存里的通用货顶不上，必须为这一行单独做一批；下单时人工标记'"
                + " AFTER `remainingqty`");
        byTable.put("u_o_orderdetail", orderDetail);

        Map<String, String> docDetail = new LinkedHashMap<>();
        docDetail.put("orderdetailcode", "ADD COLUMN `orderdetailcode` varchar(50) DEFAULT NULL"
                + " COMMENT '来源销售订单明细代码（销售出库专用，用于定制件专属批次匹配）'"
                + " AFTER `batchcode`,"
                + " ADD INDEX `idx_docdetail_orderdetail` (`shop_code`,`orderdetailcode`)");
        byTable.put("u_f_docdetail", docDetail);

        // 商品内容摘要。三张主表（u_f_docmain / u_o_order / 非月表的 u_p_purchase）都加，
        // 采购主表不是月表，由 sql/migration/20260903_goods_summary.sql 负责。
        String goodsSummaryDdl = "ADD COLUMN `goodssummary` varchar(255) DEFAULT NULL"
                + " COMMENT '商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算'";

        Map<String, String> docMain = new LinkedHashMap<>();
        docMain.put("goodssummary", goodsSummaryDdl + " AFTER `partnername`");
        byTable.put("u_f_docmain", docMain);

        Map<String, String> order = new LinkedHashMap<>();
        order.put("goodssummary", goodsSummaryDdl + " AFTER `goodsinfo`");
        // 外贸字段，与 sql/migration/20260910_sales_order_export_fields.sql 做的是同一件事，
        // 列定义必须与上面 buildOrderDDL 里对应的几行逐字一致
        order.put("buyercountry", "ADD COLUMN `buyercountry` varchar(10) DEFAULT NULL"
                + " COMMENT '客户国家（ISO 3166-1 alpha-2，取值见字典 order_country）'"
                + " AFTER `buyer2image`");
        order.put("buyeraddress", "ADD COLUMN `buyeraddress` varchar(500) DEFAULT NULL"
                + " COMMENT '客户地址（国外，不结构化，整段自由文本）'"
                + " AFTER `buyercountry`");
        order.put("receivetype", "ADD COLUMN `receivetype` varchar(20) NOT NULL DEFAULT '货代地址'"
                + " COMMENT '国内收货方式：货代地址/客户地址/上门提货'"
                + " AFTER `receivezipcode`");
        order.put("markneeded", "ADD COLUMN `markneeded` tinyint(1) NOT NULL DEFAULT 0"
                + " COMMENT '是否需要唛头：1=需要。只有 receivetype=货代地址 时才有意义，其余方式恒为 0'"
                + " AFTER `receivetype`");
        order.put("markfile", "ADD COLUMN `markfile` varchar(1000) DEFAULT NULL"
                + " COMMENT '唛头文件。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）；markneeded=1 时才有值'"
                + " AFTER `markneeded`");
        order.put("descitems", "ADD COLUMN `descitems` text"
                + " COMMENT '商品描述清单（JSON 数组：名称/数量/单价），只作记录，不参与货款合计'"
                + " AFTER `remark`");
        order.put("orderfiles", "ADD COLUMN `orderfiles` text"
                + " COMMENT '订单资料（与客户来回确认的文档）。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）'"
                + " AFTER `descitems`");
        byTable.put("u_o_order", order);

        // 仓库驳回，与 sql/migration/20260911_shipbatch_reject.sql 做的是同一件事，
        // 列定义必须与上面 buildShipBatchDDL 里对应的三行逐字一致
        Map<String, String> shipBatch = new LinkedHashMap<>();
        // 备货 / 录单五列，与 sql/migration/20260920_shipbatch_picking_flow.sql 做的是同一件事。
        // ⚠ 必须排在驳回三列<b>前面</b>：这几条都是 AFTER `shiptime`，后 add 的会插在前 add 的
        //   前面，按驳回列先跑的话新表和老表的列序会对不上（不影响正确性，但 desc 出来两样）。
        shipBatch.put("pickphotos", "ADD COLUMN `pickphotos` varchar(2000) DEFAULT NULL"
                + " COMMENT '备货照片，逗号分隔的 URL。备货完成时至少一张（备货照片与产品照片不分类别，一列存完）'"
                + " AFTER `shiptime`");
        shipBatch.put("pickuser", "ADD COLUMN `pickuser` varchar(50) DEFAULT NULL"
                + " COMMENT '备货完成人（库管员）'"
                + " AFTER `pickphotos`");
        shipBatch.put("picktime", "ADD COLUMN `picktime` datetime DEFAULT NULL"
                + " COMMENT '备货完成时间'"
                + " AFTER `pickuser`");
        shipBatch.put("logisticsuser", "ADD COLUMN `logisticsuser` varchar(50) DEFAULT NULL"
                + " COMMENT '录入快递单号的人（业务员）'"
                + " AFTER `picktime`");
        shipBatch.put("logisticstime", "ADD COLUMN `logisticstime` datetime DEFAULT NULL"
                + " COMMENT '录入快递单号时间'"
                + " AFTER `logisticsuser`");
        shipBatch.put("rejectuser", "ADD COLUMN `rejectuser` varchar(50) DEFAULT NULL"
                + " COMMENT '驳回人（仓库）'"
                + " AFTER `logisticstime`");
        shipBatch.put("rejecttime", "ADD COLUMN `rejecttime` datetime DEFAULT NULL"
                + " COMMENT '驳回时间'"
                + " AFTER `rejectuser`");
        shipBatch.put("rejectreason", "ADD COLUMN `rejectreason` varchar(500) DEFAULT NULL"
                + " COMMENT '驳回原因（最近一次）。仓库把批次退回待确认时必填'"
                + " AFTER `rejecttime`");
        byTable.put("u_f_shipbatch", shipBatch);

        return byTable;
    }

    /**
     * 给升级前建好的月表补上后来新增的列。
     *
     * <p>与 sql/ 下对应的迁移脚本做的是同一件事，两边都留着：脚本负责一次性把所有历史月表
     * 刷齐（包括本进程不会碰到的旧月份），这里兜住「脚本忘了跑」——缺列的话那张表的
     * 每一次查询和插入都会直接报 Unknown column。</p>
     *
     * <p>失败只告警不抛：列可能已经被脚本加过，也可能是权限不足；真缺列的话第一笔单据
     * 落库时会报得很清楚，不值得为它挡住整个应用启动。</p>
     */
    private void ensureAddedColumns(Connection conn, String tableName, String tablePrefix)
    {
        Map<String, String> columns = ADDED_COLUMNS.get(tablePrefix);
        if (columns == null) return;
        for (Map.Entry<String, String> entry : columns.entrySet())
        {
            String columnName = entry.getKey();
            try
            {
                if (columnExists(conn, tableName, columnName)) continue;
            }
            catch (Exception e)
            {
                log.warn("检查月表 {}.{} 的 {} 列失败，原因: {}", SCHEMA, tableName, columnName, e.getMessage());
                continue;
            }
            String sql = "ALTER TABLE `" + SCHEMA + "`.`" + tableName + "` " + entry.getValue();
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute(sql);
                log.info("已为月表补充 {} 列: {}.{}", columnName, SCHEMA, tableName);
            }
            catch (Exception e)
            {
                log.warn("为月表 {}.{} 补充 {} 列失败，原因: {}", SCHEMA, tableName, columnName, e.getMessage());
            }
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws Exception
    {
        String sql = "SELECT 1 FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql))
        {
            stmt.setString(1, SCHEMA);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);
            try (ResultSet rs = stmt.executeQuery())
            {
                return rs.next();
            }
        }
    }

    private boolean columnHasPrecision(Connection conn, String tableName, String columnName,
            int expectedPrecision, int expectedScale) throws Exception
    {
        String sql = "SELECT numeric_precision, numeric_scale FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql))
        {
            stmt.setString(1, SCHEMA);
            stmt.setString(2, tableName);
            stmt.setString(3, columnName);
            try (ResultSet rs = stmt.executeQuery())
            {
                return rs.next()
                        && rs.getInt("numeric_precision") == expectedPrecision
                        && rs.getInt("numeric_scale") == expectedScale;
            }
        }
    }

    /**
     * 判断表是否存在
     */
    private boolean tableExists(Connection conn, String schema, String tableName) throws Exception
    {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(schema, null, tableName, new String[]{"TABLE"}))
        {
            return rs.next();
        }
    }

    // ==================== DDL 生成 ====================

    private String buildDocMainDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_docmain_" + suffix + "` (\n"
            + "  `shop_code` varchar(50) NOT NULL DEFAULT '' COMMENT '商家编码',\n"
            + "  `doccode` varchar(50) NOT NULL COMMENT '单据代码',\n"
            + "  `doctype` varchar(30) NOT NULL COMMENT '单据类型',\n"
            + "  `doctypename` varchar(50) NOT NULL COMMENT '单据类型名称',\n"
            + "  `docdate` datetime NOT NULL COMMENT '单据日期',\n"
            + "  `fromwarecode` varchar(50) NOT NULL COMMENT '来源仓库代码',\n"
            + "  `fromwarename` varchar(100) NOT NULL COMMENT '来源仓库名称',\n"
            + "  `towarecode` varchar(50) NOT NULL COMMENT '目标仓库代码',\n"
            + "  `towarename` varchar(100) NOT NULL COMMENT '目标仓库名称',\n"
            + "  `fromqualityflag` tinyint DEFAULT NULL COMMENT '来源良品状态：1-良品，0-不良品',\n"
            + "  `fromonhandflag` tinyint DEFAULT NULL COMMENT '来源在库状态：1-在库，0-不在库',\n"
            + "  `toqualityflag` tinyint DEFAULT NULL COMMENT '目标良品状态：1-良品，0-不良品',\n"
            + "  `toonhandflag` tinyint DEFAULT NULL COMMENT '目标在库状态：1-在库，0-不在库',\n"
            + "  `partnercode` varchar(50) DEFAULT NULL COMMENT '往来单位代码',\n"
            + "  `partnername` varchar(100) DEFAULT NULL COMMENT '往来单位名称',\n"
            + "  `goodssummary` varchar(255) DEFAULT NULL COMMENT '商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算',\n"
            + "  `createuser` varchar(50) NOT NULL COMMENT '创建人',\n"
            + "  `handler` varchar(50) DEFAULT NULL COMMENT '经手人',\n"
            + "  `handlertime` datetime DEFAULT NULL COMMENT '业务发生时间',\n"
            + "  `confirmstate` varchar(20) DEFAULT '待审核' COMMENT '待审核/已审核/已驳回/已取消',\n"
            + "  `confirmuser` varchar(50) DEFAULT NULL COMMENT '审核人',\n"
            + "  `confirmtime` datetime DEFAULT NULL COMMENT '审核时间',\n"
            + "  `sourcedoccode` varchar(50) DEFAULT NULL COMMENT '来源单据代码',\n"
            + "  `sourcedoctype` varchar(30) NOT NULL COMMENT '来源单据类型',\n"
            + "  `sourcedoctypename` varchar(50) NOT NULL COMMENT '来源单据类型名称',\n"
            + "  `totalqty` decimal(18,4) DEFAULT 0.0000 COMMENT '总数量',\n"
            + "  `totalamount` decimal(18,2) DEFAULT 0.00 COMMENT '总金额',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            + "  `idempotency_key` varchar(100) DEFAULT NULL COMMENT '客户端幂等键',\n"
            + "  PRIMARY KEY (`doccode`, `sourcedoctype`),\n"
            + "  UNIQUE KEY `uk_docmain_shop_idempotency` (`shop_code`, `idempotency_key`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据主表（" + suffix + "）'";
    }

    private String buildDocDetailDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_docdetail_" + suffix + "` (\n"
            + "  `shop_code` varchar(50) NOT NULL DEFAULT '' COMMENT '商家编码',\n"
            + "  `detailcode` varchar(50) NOT NULL COMMENT '明细代码',\n"
            + "  `doccode` varchar(50) NOT NULL COMMENT '单据代码',\n"
            + "  `lineno` int DEFAULT 0 COMMENT '行号',\n"
            + "  `goodscode` varchar(50) NOT NULL COMMENT '商品代码',\n"
            + "  `goodsname` varchar(100) NOT NULL COMMENT '商品名称',\n"
            + "  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号',\n"
            + "  `unit` varchar(20) DEFAULT NULL COMMENT '单位',\n"
            + "  `warecode` varchar(50) DEFAULT NULL COMMENT '本行仓库代码',\n"
            + "  `warename` varchar(100) DEFAULT NULL COMMENT '本行仓库名称',\n"
            + "  `qualityflag` tinyint DEFAULT NULL COMMENT '本行良品状态：1-良品，0-不良品',\n"
            + "  `onhandflag` tinyint DEFAULT NULL COMMENT '本行在库状态：1-在库，0-不在库',\n"
            + "  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '数量',\n"
            + "  `saleprice` decimal(18,4) DEFAULT 0.0000 COMMENT '销售单价',\n"
            + "  `saleamount` decimal(18,2) DEFAULT 0.00 COMMENT '销售金额',\n"
            + "  `costprice` decimal(18,6) DEFAULT 0.000000 COMMENT '成本单价',\n"
            + "  `costamount` decimal(18,2) DEFAULT 0.00 COMMENT '成本金额',\n"
            + "  `batchcode` varchar(50) DEFAULT NULL COMMENT '批次代码',\n"
            // 销售出库才有值。审核出库时要靠它挑批次：定制件的专属批次只发给自己那条明细，
            // 别人的一律跳过。落库而不是只在内存里传，是因为建单和审核是两次调用，
            // 审核时明细是从这张表读回来的。
            + "  `orderdetailcode` varchar(50) DEFAULT NULL COMMENT '来源销售订单明细代码（销售出库专用，用于定制件专属批次匹配）',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '行备注',\n"
            + "  PRIMARY KEY (`detailcode`),\n"
            + "  KEY `idx_docdetail_orderdetail` (`shop_code`,`orderdetailcode`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据明细表（" + suffix + "）'";
    }

    private String buildLedgerDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_ledger_" + suffix + "` (\n"
            + "  `shop_code` varchar(50) NOT NULL DEFAULT '' COMMENT '商家编码',\n"
            + "  `ledger_code` varchar(50) NOT NULL COMMENT '流水代码',\n"
            + "  `doc_code` varchar(50) NOT NULL COMMENT '单据代码',\n"
            + "  `doc_type` varchar(30) NOT NULL COMMENT '单据类型',\n"
            + "  `doc_type_name` varchar(50) NOT NULL COMMENT '单据类型名称',\n"
            + "  `detail_code` varchar(50) NOT NULL COMMENT '单据明细代码',\n"
            + "  `goods_code` varchar(50) NOT NULL COMMENT '商品代码',\n"
            + "  `goods_name` varchar(100) NOT NULL COMMENT '商品名称',\n"
            + "  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号',\n"
            + "  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',\n"
            + "  `ware_code` varchar(50) NOT NULL COMMENT '仓库代码',\n"
            + "  `ware_name` varchar(100) NOT NULL COMMENT '仓库名称',\n"
            + "  `quality_flag` tinyint DEFAULT NULL COMMENT '良品状态：1-良品，0-不良品',\n"
            + "  `onhand_flag` tinyint DEFAULT NULL COMMENT '在库状态：1-在库，0-不在库',\n"
            + "  `before_qty` decimal(18,4) DEFAULT 0.0000 COMMENT '变动前数量',\n"
            + "  `change_qty` decimal(18,4) DEFAULT 0.0000 COMMENT '变动数量',\n"
            + "  `after_qty` decimal(18,4) NOT NULL COMMENT '变动后结存数量',\n"
            + "  `cost_price` decimal(18,6) DEFAULT 0.000000 COMMENT '成本单价',\n"
            + "  `cost_amount` decimal(18,2) DEFAULT 0.00 COMMENT '成本金额',\n"
            + "  `batch_code` varchar(50) DEFAULT NULL COMMENT '批次代码',\n"
            + "  `serial_no` varchar(50) DEFAULT NULL COMMENT '序列号',\n"
            + "  `source_doc_code` varchar(50) DEFAULT NULL COMMENT '来源单据代码',\n"
            + "  `source_doc_type` varchar(30) DEFAULT NULL COMMENT '来源单据类型',\n"
            + "  `operator` varchar(50) DEFAULT NULL COMMENT '操作人',\n"
            + "  `operate_time` datetime DEFAULT NULL COMMENT '操作时间',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            + "  PRIMARY KEY (`ledger_code`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水表（" + suffix + "）'";
    }

    private String buildPurchaseReceiveDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_p_receive_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL,\n"
            + "  `receivecode` varchar(50) NOT NULL,\n"
            + "  `purchasecode` varchar(50) DEFAULT NULL,\n"
            + "  `senddate` date DEFAULT NULL COMMENT '发货日期，决定本单落哪张月表',\n"
            + "  `logisticsno` varchar(100) DEFAULT NULL,\n"
            + "  `carrier` varchar(100) DEFAULT NULL,\n"
            + "  `freighttype` varchar(20) DEFAULT NULL,\n"
            + "  `estfreightamount` decimal(18,2) DEFAULT 0.00 COMMENT '预估运费，采购员登记发货时填',\n"
            + "  `freightamount` decimal(18,2) DEFAULT 0.00 COMMENT '实付运费，入库员签收时填，多次入库累加',\n"
            + "  `freightpaid` decimal(18,2) DEFAULT 0.00,\n"
            + "  `freightpaystatus` varchar(20) DEFAULT NULL,\n"
            + "  `payuser` varchar(50) DEFAULT NULL,\n"
            + "  `paytime` datetime DEFAULT NULL,\n"
            + "  `totalqty` decimal(18,4) DEFAULT 0.0000,\n"
            + "  `receivetime` datetime DEFAULT NULL COMMENT '签收时间，入库员填',\n"
            + "  `receiveuser` varchar(50) DEFAULT NULL COMMENT '签收人，入库员填',\n"
            + "  `status` varchar(20) DEFAULT '待入库' COMMENT '待入库/已入库，由明细行派生；发货单整单入库，没有中间态',\n"
            + "  `remark` varchar(500) DEFAULT NULL,\n"
            + "  `createuser` varchar(50) DEFAULT NULL,\n"
            + "  `createtime` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `updateuser` varchar(50) DEFAULT NULL,\n"
            + "  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "  PRIMARY KEY (`id`), UNIQUE KEY `uk_receive_shop_code` (`shop_code`,`receivecode`),\n"
            + "  KEY `idx_receive_purchase` (`shop_code`,`purchasecode`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
    }

    private String buildProductionLogDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_productionlog_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `logcode` varchar(50) NOT NULL COMMENT '日志代码',\n"
            + "  `productioncode` varchar(50) NOT NULL COMMENT '工单号',\n"
            + "  `operation` varchar(30) NOT NULL COMMENT '操作类型：领料/消耗/入库/退料/费用调整/订单预占/终止',\n"
            + "  `doccode` varchar(50) NOT NULL COMMENT '关联库存单据代码',\n"
            + "  `doctype` varchar(30) NOT NULL COMMENT '关联库存单据类型',\n"
            + "  `docdetailcode` varchar(50) DEFAULT NULL COMMENT '关联库存单据明细代码',\n"
            + "  `goodscode` varchar(50) NOT NULL COMMENT '商品代码',\n"
            + "  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '数量',\n"
            + "  `costamount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本金额',\n"
            + "  `operationtime` datetime NOT NULL COMMENT '操作时间',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_productionlog_shop_code` (`shop_code`,`logcode`),\n"
            + "  KEY `idx_productionlog_order` (`shop_code`,`productioncode`,`operationtime`),\n"
            + "  KEY `idx_productionlog_doc` (`shop_code`,`doccode`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产执行日志表（" + suffix + "）'";
    }

    /**
     * 资金流水月表。DDL 与 sql/finance_module_schema_20260814.sql 第 3 节保持一致，
     * 改动必须两边同步。amount 的符号由 CHECK 约束兜底：收入为正、支出为负，
     * 账户余额一律 currentbalance + amount，不按 transtype 分支。
     */
    private String buildFinanceTransactionDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_transaction_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `transactioncode` varchar(50) NOT NULL COMMENT '流水代码',\n"
            + "  `accountcode` varchar(50) DEFAULT NULL COMMENT '我方账户代码',\n"
            + "  `accountname` varchar(50) DEFAULT NULL COMMENT '我方账户简称（冗余）',\n"
            + "  `transtype` varchar(10) NOT NULL COMMENT '收入/支出',\n"
            + "  `categorycode` varchar(30) NOT NULL COMMENT '收支分类代码',\n"
            + "  `categoryname` varchar(50) DEFAULT NULL COMMENT '收支分类名称（冗余快照）',\n"
            + "  `amount` decimal(18,2) NOT NULL COMMENT '变动金额（收入为正，支出为负）',\n"
            + "  `balance` decimal(18,2) DEFAULT NULL COMMENT '执行完成后账户余额',\n"
            + "  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT 'ISO 4217 币种快照',\n"
            + "  `partyname` varchar(100) NOT NULL COMMENT '对方名称',\n"
            + "  `partytext` varchar(500) DEFAULT NULL COMMENT '对方文本信息',\n"
            + "  `partyimage` varchar(500) DEFAULT NULL COMMENT '对方图片信息',\n"
            + "  `tradetime` datetime DEFAULT NULL COMMENT '资金实际交易时间（排序对账依据）',\n"
            + "  `biztype` varchar(30) NOT NULL COMMENT '采购/销售/其他（费用、工资仅保留历史值）',\n"
            + "  `bizcode` varchar(50) DEFAULT NULL COMMENT '业务单据号',\n"
            + "  `bizsummary` varchar(200) NOT NULL COMMENT '业务摘要',\n"
            + "  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '草稿/待审核/已审核/已完成/已驳回',\n"
            + "  `rejectreason` varchar(500) DEFAULT NULL COMMENT '驳回原因',\n"
            + "  `createuser` varchar(50) NOT NULL COMMENT '创建人',\n"
            + "  `createtime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "  `audituser` varchar(50) DEFAULT NULL COMMENT '审核人',\n"
            + "  `audittime` datetime DEFAULT NULL COMMENT '审核时间',\n"
            + "  `completeuser` varchar(50) DEFAULT NULL COMMENT '完成人',\n"
            + "  `completetime` datetime DEFAULT NULL COMMENT '完成时间',\n"
            + "  `attachment` varchar(1000) DEFAULT NULL COMMENT '凭证附件路径',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            + "  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_transaction_shop_code` (`shop_code`,`transactioncode`),\n"
            + "  KEY `idx_transaction_shop_status` (`shop_code`,`status`),\n"
            + "  KEY `idx_transaction_shop_tradetime` (`shop_code`,`tradetime`),\n"
            + "  KEY `idx_transaction_shop_biz` (`shop_code`,`biztype`,`bizcode`),\n"
            + "  KEY `idx_transaction_shop_account` (`shop_code`,`accountcode`),\n"
            + "  CONSTRAINT `ck_transaction_amount_sign_" + suffix + "` CHECK (\n"
            + "      (`transtype` = '收入' AND `amount` > 0) OR (`transtype` = '支出' AND `amount` < 0))\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金流水表（" + suffix + "）'";
    }

    /**
     * 订单主表月表。DDL 与 sql/order_module_schema_20260819.sql 第 1 节
     * 叠加 sql/order_module_schema_fix_20260819.sql 第 1 节的结果保持一致，
     * 改动必须两边同步。落表月份取 createtime 所在月。
     */
    private String buildOrderDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_o_order_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `ordercode` varchar(50) NOT NULL COMMENT '订单代码',\n"
            + "  `platcode` varchar(30) DEFAULT NULL COMMENT '平台代码',\n"
            + "  `platname` varchar(50) DEFAULT NULL COMMENT '平台名称（冗余）',\n"
            + "  `shopcode` varchar(50) NOT NULL COMMENT '商家店铺代码',\n"
            + "  `shopname` varchar(100) NOT NULL COMMENT '商家店铺名称（冗余）',\n"
            + "  `staffcode` varchar(50) DEFAULT NULL COMMENT '商家员工代码',\n"
            + "  `staffname` varchar(50) DEFAULT NULL COMMENT '商家员工名称（冗余）',\n"
            // ⚠ 默认值是空串不是 NULL，别改回去。
            //
            // docs/独立站与ERP的边界.md 要点 6（原文）：「ERP 系统中订单和客户中的
            // buyeruser 和 buyershop 暂时保留不删，内容默认空串即可。」
            //
            // 为什么留：这两列本来是「订单是哪个买家在哪个站下的」。两边订单不关联之后
            // （要点 4），它没有来源了。但删列是不可逆的，而且后期真做订单拉取时
            // 它正好是落 ID 的地方。留着、空着，成本是零。
            //
            // 只改这里不够：历史月表要跑一条
            //     UPDATE u_o_order_YYYYMM SET buyeruser = '' WHERE buyeruser IS NULL
            // 补齐，见 sql/migration/20260903_station_erp_split.sql。只改 builder 不改
            // 存量表，会出现「这个月好好的、上个月不对」的诡异 bug——
            // 20260828_receive_status_default.sql 修的就是同一类问题。
            + "  `buyershop` varchar(50) NOT NULL DEFAULT '' COMMENT '买家商家代码。⚠ 默认空串，见要点 6',\n"
            + "  `buyeruser` varchar(50) NOT NULL DEFAULT '' COMMENT '个人买家代码或商家买家员工代码。⚠ 默认空串，见要点 6',\n"
            // 客户快照：建单时从 sdsshop.u_m_buyer 拷入，之后不跟客户档案联动。
            // 与上面 buyershop/buyeruser 的分工：那两列留给没建档的散客手填，
            // 这几列只在业务员选了客户档案时才有值。
            // buyer2* 是二级客户快照，与 buyer* 四列同样是建单当时拷入、之后不联动。
            // 本段与 sql/buyer_module_schema_20260824.sql 第 3 节、
            // sql/order_module_schema_20260819.sql 第 1 节必须同步改。
            + "  `buyercode` varchar(50) DEFAULT NULL COMMENT '客户编码（快照，来自 u_m_buyer）',\n"
            + "  `buyername` varchar(200) DEFAULT NULL COMMENT '客户名称（快照，建单当时的简称/全称）',\n"
            + "  `buyerimage` varchar(500) DEFAULT NULL COMMENT '客户头像（快照）',\n"
            + "  `shopimage` varchar(500) DEFAULT NULL COMMENT '客户店铺图（快照）',\n"
            + "  `buyer2code` varchar(50) DEFAULT NULL COMMENT '二级客户编码（快照）',\n"
            + "  `buyer2name` varchar(100) DEFAULT NULL COMMENT '二级客户名称（快照）',\n"
            + "  `buyer2user` varchar(50) DEFAULT NULL COMMENT '二级客户联系人（快照）',\n"
            + "  `buyer2image` varchar(500) DEFAULT NULL COMMENT '二级客户头像（快照）',\n"
            // 出口单的客户在国外，下面那套国内结构化收件地址装不下：国家单独一列（取字典
            // order_country 的键），国外地址整段自由文本存着就行——各国的行政区划层级
            // 对不上，硬拆成省市区只会拆错。国内收件信息仍在下面的 receive* 几列。
            + "  `buyercountry` varchar(10) DEFAULT NULL COMMENT '客户国家（ISO 3166-1 alpha-2，取值见字典 order_country）',\n"
            + "  `buyeraddress` varchar(500) DEFAULT NULL COMMENT '客户地址（国外，不结构化，整段自由文本）',\n"
            + "  `platorderid` varchar(100) DEFAULT NULL COMMENT '平台内订单编号',\n"
            + "  `platbuyerid` varchar(100) DEFAULT NULL COMMENT '平台内买家名称',\n"
            + "  `platorderurl` varchar(500) DEFAULT NULL COMMENT '平台内订单链接',\n"
            + "  `platordertime` datetime DEFAULT NULL COMMENT '平台订单时间',\n"
            + "  `platorderleave` varchar(500) DEFAULT NULL COMMENT '平台留言',\n"
            + "  `goodsamount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '货款金额',\n"
            + "  `freightamount` decimal(18,2) DEFAULT 0.00 COMMENT '运费金额',\n"
            + "  `totalamount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额（货款+运费）',\n"
            // 方向陷阱：采购 u_p_purchase.paidamount 是「付出去」，这里是「收进来」。
            + "  `paidamount` decimal(18,2) DEFAULT 0.00 COMMENT '已收到金额（方向：收进来。注意采购 u_p_purchase.paidamount 是付出去，同名反向）',\n"
            // 与 finance_order_currency_20260821.sql 收紧后的定义保持一致：可空会让
            // CurrencySupport 读到 null，两边不一致时新月表还会退回旧定义。
            + "  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT 'ISO 4217 币种代码，一张订单只对应一种币种',\n"
            // 履约与支付拆成两列：一张订单可以同时是「已支付」和「发货中」，一列存不下。
            // 与采购 u_p_purchase 的 status + paystatus 对齐。
            + "  `orderstate` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '履约状态：草稿/待审核/已驳回/已备货/备货中/发货中/已完成/已取消/已关闭（支付状态见 paystatus）',\n"
            + "  `paystatus` varchar(20) NOT NULL DEFAULT '未支付' COMMENT '支付状态：未支付/部分支付/已支付/超额支付（与采购 paystatus 对齐）',\n"
            + "  `receivestatus` varchar(20) NOT NULL DEFAULT '未收款' COMMENT '回款状态：未收款/部分收款/已收款/超额收款（财务模块回款完成后回写）',\n"
            + "  `sendpercent` decimal(5,2) DEFAULT 0.00 COMMENT '发货比例（%）＝Σ已发货数量/Σ订单数量×100',\n"
            + "  `receiveperson` varchar(50) NOT NULL COMMENT '收件人',\n"
            + "  `receiveaddress` varchar(200) NOT NULL COMMENT '收件地址',\n"
            // 不是 11 位手机号，别照手机号的宽度给：平台下单的虚拟号位数不固定，
            // 客户也常常一次留两三个号码用逗号分隔，50 位装不下。口径见 ContactPhoneUtils
            + "  `receivetelephone` varchar(100) NOT NULL COMMENT '收件电话（可以是虚拟号、固话，或逗号/分号分隔的多个号码）',\n"
            + "  `receivezipcode` varchar(20) DEFAULT NULL COMMENT '收件邮编',\n"
            // 国内这一段货发到哪：发货代、发客户自己的国内地址、或者客户上门来提。
            // 上门提货时上面的 receiveaddress 允许是空串（见 SalesOrderServiceImpl.validateHeader），
            // 列本身仍然 NOT NULL，别改成可空——历史行不出现 null 全靠这个约束。
            + "  `receivetype` varchar(20) NOT NULL DEFAULT '货代地址' COMMENT '国内收货方式：货代地址/客户地址/上门提货',\n"
            // 唛头只有走货代才谈得上，换成别的收货方式时服务层会把这两列清回 0 / null
            + "  `markneeded` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要唛头：1=需要。只有 receivetype=货代地址 时才有意义，其余方式恒为 0',\n"
            // 附件存的是一段 JSON：每项带 name（源文件名）和 url（相对路径）。
            // 只存 url 的话，页面上只能显示服务端改写过的存储名（原名后面缀了序列号，走 OSS
            // 时更是整段换掉），而用户要看的是自己上传时的那个文件名，所以名字得跟着一起存。
            + "  `markfile` varchar(1000) DEFAULT NULL COMMENT '唛头文件。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）；markneeded=1 时才有值',\n"
            + "  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（决定本单落哪张月表）',\n"
            + "  `paytime` datetime DEFAULT NULL COMMENT '支付时间（首付款）',\n"
            + "  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
            + "  `goodsinfo` text COMMENT '商品详情',\n"
            // 与上面的 goodsinfo 不是一回事：那个是自由文本，这个是由明细整体重算的一句摘要，
            // 只给列表页看。改名太贵（历史数据和接口都在用 goodsinfo），只能靠注释区分。
            + "  `goodssummary` varchar(255) DEFAULT NULL COMMENT '商品内容摘要（如“可乐等3种”），由明细整体重算的展示用冗余列，不参与计算',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            // 销售自己写给客户和生产看的一份口径说明：一行只有名称、数量、单价，不挂
            // 商品编码、不占库存、不参与发货，也不进货款合计（货款只按货品明细算）。
            // 它不跟任何东西关联，没必要为它再开一张明细月表，JSON 文本足够；
            // 同库里 u_p_production.orderallocation 也是这么存的。
            // 服务层写入前会整体解析校验再序列化回去，脏数据进不来。
            + "  `descitems` text COMMENT '商品描述清单（JSON 数组：名称/数量/单价），只作记录，不参与货款合计',\n"
            // 十个附件、文件名又常常是一长串中文，varchar 装不下，直接给 text
            + "  `orderfiles` text COMMENT '订单资料（与客户来回确认的文档）。JSON 数组，每项含 name（上传时的源文件名）和 url（相对路径）',\n"
            // 两个原因列各自独立：驳回改单重提后 rejectreason 保留，用来对照这轮改了什么
            + "  `rejectreason` varchar(500) DEFAULT NULL COMMENT '驳回原因（审核不通过时必填）',\n"
            + "  `closereason` varchar(500) DEFAULT NULL COMMENT '关闭原因（强制关闭时必填）',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_order_shop_code` (`shop_code`,`ordercode`),\n"
            + "  KEY `idx_order_shop_state` (`shop_code`,`orderstate`),\n"
            + "  KEY `idx_order_shop_createtime` (`shop_code`,`createtime`),\n"
            + "  KEY `idx_order_shop_shop` (`shop_code`,`shopcode`),\n"
            + "  KEY `idx_order_shop_staff` (`shop_code`,`staffcode`),\n"
            + "  KEY `idx_order_shop_platorder` (`shop_code`,`platcode`,`platorderid`),\n"
            // 复合到 createtime：按客户查订单一定是「这个客户最近的单」，
            // 带上时间列才能让排序也走索引，不用回表再 filesort
            + "  KEY `idx_order_shop_buyer` (`shop_code`,`buyercode`,`createtime`),\n"
            + "  CONSTRAINT `ck_order_sendpercent_" + suffix + "` CHECK (`sendpercent` >= 0 AND `sendpercent` <= 100)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表（" + suffix + "）'";
    }

    /**
     * 订单明细月表。DDL 与 sql/order_module_schema_20260819.sql 第 2 节保持一致，
     * 改动必须两边同步。明细跟随所属订单的月份，不按自身时间落表。
     */
    private String buildOrderDetailDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_o_orderdetail_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `detailcode` varchar(50) NOT NULL COMMENT '明细代码',\n"
            + "  `ordercode` varchar(50) NOT NULL COMMENT '订单代码',\n"
            + "  `lineno` int DEFAULT 0 COMMENT '行号',\n"
            + "  `goodscode` varchar(50) NOT NULL COMMENT '商品代码',\n"
            + "  `goodsname` varchar(100) NOT NULL COMMENT '商品名称（冗余）',\n"
            + "  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号（冗余）',\n"
            + "  `unit` varchar(20) DEFAULT NULL COMMENT '单位（冗余）',\n"
            + "  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '数量',\n"
            + "  `shippedqty` decimal(18,4) DEFAULT 0.0000 COMMENT '已发货数量（累计）',\n"
            + "  `reservedqty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '【已废弃】订单预占数量。订单预占机制已取消，恒为 0，无代码读写；列保留只为不动历史行',\n"
            + "  `remainingqty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '未发货数量=qty-shippedqty（与采购 u_p_purchasedetail.remainingqty 对齐）',\n"
            // 定制标记与备货来源是两件事，不能合并：stock_source 是「当前靠什么补货」，
            // 建工单时会被回写成'生产'，普通标准件排产一次就永远显示成生产了；
            // custom_made 是「这一行本来就得专门做」，下单时人工定，之后不随备货过程变。
            + "  `custom_made` tinyint(1) NOT NULL DEFAULT 0 COMMENT '专门生产（定制品）：1=库存里的通用货顶不上，必须为这一行单独做一批；下单时人工标记',\n"
            // 备货来源在订单明细（我在等哪张单），实发来源在批次明细（这批货实际从哪来），两层都要有：
            // 工单完工要「触发发货」时，发货批次往往还没建，只能从订单明细反查。
            + "  `stock_source` varchar(20) NOT NULL DEFAULT '现货' COMMENT '【已废弃】备货来源。该概念已取消，恒为默认值 现货，无代码读写；列保留只为不动历史行',\n"
            + "  `sourcecode` varchar(50) DEFAULT NULL COMMENT '正在负责这条明细的生产工单号：建/改工单时回写，关闭或移出分配时清空',\n"
            + "  `price` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '销售单价',\n"
            + "  `amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '销售金额',\n"
            + "  `costprice` decimal(18,4) DEFAULT 0.0000 COMMENT '成本单价',\n"
            + "  `costamount` decimal(18,2) DEFAULT 0.00 COMMENT '成本金额',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '行备注',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_orderdetail_shop_code` (`shop_code`,`detailcode`),\n"
            + "  UNIQUE KEY `uk_orderdetail_shop_line` (`shop_code`,`ordercode`,`lineno`),\n"
            + "  KEY `idx_orderdetail_shop_order` (`shop_code`,`ordercode`),\n"
            + "  KEY `idx_orderdetail_shop_goods` (`shop_code`,`goodscode`),\n"
            + "  KEY `idx_orderdetail_shop_source` (`shop_code`,`sourcecode`),\n"
            + "  CONSTRAINT `ck_orderdetail_qty_" + suffix + "` CHECK (`qty` > 0),\n"
            + "  CONSTRAINT `ck_orderdetail_shippedqty_" + suffix + "` CHECK (`shippedqty` >= 0),\n"
            + "  CONSTRAINT `ck_orderdetail_reservedqty_" + suffix + "` CHECK (`reservedqty` >= 0),\n"
            + "  CONSTRAINT `ck_orderdetail_source_" + suffix + "` CHECK (`stock_source` IN ('现货','采购','生产'))\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表（" + suffix + "）'";
    }

    /**
     * 发货批次主表月表。<b>本方法就是这张表的唯一权威 DDL</b>——早期那份
     * {@code sql/order_module_schema_20260819.sql} 已经不在仓库里了，别再去找它对。
     * 加列时要同步的是下面 {@link #ADDED_COLUMNS} 里的 {@code u_f_shipbatch} 条目
     * 和 sql/migration/ 下对应的迁移脚本（历史月表靠脚本刷齐）。
     *
     * <p>落表月份取 shipdate（计划发货日期）所在月，一张订单的多个批次可能跨月，
     * 查询批次要按 shipdate 定表，不能按订单的 createtime。</p>
     */
    private String buildShipBatchDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_shipbatch_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `shipcode` varchar(50) NOT NULL COMMENT '发货批次代码',\n"
            + "  `ordercode` varchar(50) NOT NULL COMMENT '关联订单代码',\n"
            + "  `shipdate` datetime NOT NULL COMMENT '计划发货日期（决定本批次落哪张月表）',\n"
            + "  `actualshiptime` datetime DEFAULT NULL COMMENT '实际发货时间',\n"
            + "  `warehousecode` varchar(50) NOT NULL COMMENT '发货仓库代码',\n"
            + "  `warehousename` varchar(100) NOT NULL COMMENT '发货仓库名称（冗余）',\n"
            + "  `carrier` varchar(50) DEFAULT NULL COMMENT '物流承运商',\n"
            + "  `logisticsno` varchar(50) DEFAULT NULL COMMENT '物流单号',\n"
            // 方向陷阱：这里是「发货实付的运费」，订单主表 freightamount 是「向客户收的运费」。
            + "  `freightamount` decimal(18,2) DEFAULT 0.00 COMMENT '本批次实付运费（方向：付出去。订单主表 freightamount 是向客户收的，反向）',\n"
            + "  `freightpaid` decimal(18,2) DEFAULT 0.00 COMMENT '本批次已支付运费',\n"
            + "  `freightpaystatus` varchar(20) DEFAULT '未支付' COMMENT '运费支付状态：未支付/已支付/超额支付',\n"
            + "  `payuser` varchar(50) DEFAULT NULL COMMENT '运费支付人',\n"
            + "  `paytime` datetime DEFAULT NULL COMMENT '运费支付时间',\n"
            + "  `status` varchar(20) NOT NULL DEFAULT '待确认' COMMENT '待确认/已提交仓库/已打印备货单/备货完成/已录入快递单号/已打印快递单号/已完成/已取消。库存只在「已完成」时扣',\n"
            + "  `createuser` varchar(50) NOT NULL COMMENT '创建人（业务员）',\n"
            + "  `createtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "  `confirmuser` varchar(50) DEFAULT NULL COMMENT '确认人（仓库）',\n"
            + "  `confirmtime` datetime DEFAULT NULL COMMENT '确认时间',\n"
            + "  `shipuser` varchar(50) DEFAULT NULL COMMENT '发货人（在「完成」那一步落，那一刻货才真的离开仓库）',\n"
            + "  `shiptime` datetime DEFAULT NULL COMMENT '发货时间',\n"
            // 备货／录单五列与 ADDED_COLUMNS 里的 u_f_shipbatch 条目逐字一致，改动两边同步
            + "  `pickphotos` varchar(2000) DEFAULT NULL COMMENT '备货照片，逗号分隔的 URL。备货完成时至少一张（备货照片与产品照片不分类别，一列存完）',\n"
            + "  `pickuser` varchar(50) DEFAULT NULL COMMENT '备货完成人（库管员）',\n"
            + "  `picktime` datetime DEFAULT NULL COMMENT '备货完成时间',\n"
            + "  `logisticsuser` varchar(50) DEFAULT NULL COMMENT '录入快递单号的人（业务员）',\n"
            + "  `logisticstime` datetime DEFAULT NULL COMMENT '录入快递单号时间',\n"
            // 驳回三列同上，与 ADDED_COLUMNS 逐字一致
            + "  `rejectuser` varchar(50) DEFAULT NULL COMMENT '驳回人（仓库）',\n"
            + "  `rejecttime` datetime DEFAULT NULL COMMENT '驳回时间',\n"
            + "  `rejectreason` varchar(500) DEFAULT NULL COMMENT '驳回原因（最近一次）。仓库把批次退回待确认时必填',\n"
            + "  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_shipbatch_shop_code` (`shop_code`,`shipcode`),\n"
            + "  KEY `idx_shipbatch_shop_order` (`shop_code`,`ordercode`),\n"
            + "  KEY `idx_shipbatch_shop_status` (`shop_code`,`status`),\n"
            + "  KEY `idx_shipbatch_shop_shipdate` (`shop_code`,`shipdate`),\n"
            + "  KEY `idx_shipbatch_shop_warehouse` (`shop_code`,`warehousecode`),\n"
            + "  KEY `idx_shipbatch_shop_logistics` (`shop_code`,`logisticsno`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货批次主表（" + suffix + "）'";
    }

    /**
     * 发货批次明细月表。DDL 与 sql/order_module_schema_20260819.sql 第 4 节保持一致，
     * 改动必须两边同步。明细跟随所属批次的月份。
     */
    private String buildShipBatchDetailDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_f_shipbatchdetail_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
            + "  `shop_code` varchar(50) NOT NULL COMMENT '商家编码',\n"
            + "  `detailcode` varchar(50) NOT NULL COMMENT '明细代码',\n"
            + "  `shipcode` varchar(50) NOT NULL COMMENT '发货批次代码',\n"
            + "  `orderdetailcode` varchar(50) NOT NULL COMMENT '关联订单明细代码',\n"
            + "  `goodscode` varchar(50) NOT NULL COMMENT '商品代码',\n"
            + "  `goodsname` varchar(100) NOT NULL COMMENT '商品名称（冗余）',\n"
            + "  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号（冗余）',\n"
            + "  `unit` varchar(20) DEFAULT NULL COMMENT '单位（冗余）',\n"
            + "  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '本批次发货数量',\n"
            + "  `stock_source` varchar(20) NOT NULL DEFAULT '现货' COMMENT '【已废弃】实发来源。备货来源概念取消后一并废弃，恒为默认值 现货，无代码读写',\n"
            + "  `sourcecode` varchar(50) DEFAULT NULL COMMENT '来源单号（采购单号/生产工单号）',\n"
            + "  `remark` varchar(500) DEFAULT NULL COMMENT '行备注',\n"
            + "  PRIMARY KEY (`id`),\n"
            + "  UNIQUE KEY `uk_shipbatchdetail_shop_code` (`shop_code`,`detailcode`),\n"
            + "  KEY `idx_shipbatchdetail_shop_ship` (`shop_code`,`shipcode`),\n"
            + "  KEY `idx_shipbatchdetail_shop_orderdetail` (`shop_code`,`orderdetailcode`),\n"
            + "  KEY `idx_shipbatchdetail_shop_goods` (`shop_code`,`goodscode`),\n"
            + "  KEY `idx_shipbatchdetail_shop_source` (`shop_code`,`sourcecode`),\n"
            + "  CONSTRAINT `ck_shipbatchdetail_qty_" + suffix + "` CHECK (`qty` > 0),\n"
            + "  CONSTRAINT `ck_shipbatchdetail_source_" + suffix + "` CHECK (`stock_source` IN ('现货','采购','生产'))\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货批次明细表（" + suffix + "）'";
    }

    private String buildPurchaseReceiveDetailDDL(String suffix)
    {
        return "CREATE TABLE IF NOT EXISTS `" + SCHEMA + "`.`u_p_receivedetail_" + suffix + "` (\n"
            + "  `id` bigint NOT NULL AUTO_INCREMENT, `shop_code` varchar(50) NOT NULL,\n"
            + "  `detailcode` varchar(50) NOT NULL, `receivecode` varchar(50) NOT NULL,\n"
            + "  `purchasedetailcode` varchar(50) DEFAULT NULL, `goodscode` varchar(50) NOT NULL,\n"
            + "  `goodsname` varchar(100) DEFAULT NULL, `spec` varchar(100) DEFAULT NULL, `unit` varchar(20) DEFAULT NULL,\n"
            + "  `qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '预计到货数量，采购员填',\n"
            // NULL 表示该行还没入库，是行状态判定的依据，不能改成 DEFAULT 0
            + "  `actualqty` decimal(18,4) DEFAULT NULL COMMENT '实际到货数量，入库员填；NULL=该行未入库',\n"
            + "  `goodqty` decimal(18,4) DEFAULT 0.0000 COMMENT '入库审核通过时回填=实际到货数量',\n"
            + "  `badqty` decimal(18,4) DEFAULT 0.0000 COMMENT '短装数量=max(预计-实际,0)',\n"
            + "  `qualitystatus` varchar(20) DEFAULT NULL COMMENT '齐套/短装/超发',\n"
            + "  `price` decimal(18,4) DEFAULT 0.0000, `amount` decimal(18,2) DEFAULT 0.00,\n"
            + "  `batchcode` varchar(50) DEFAULT NULL, `serialnumbers` varchar(500) DEFAULT NULL,\n"
            + "  `inventorydoccode` varchar(50) DEFAULT NULL, `inventorydetailcode` varchar(50) DEFAULT NULL,\n"
            + "  `warehousecode` varchar(50) DEFAULT NULL COMMENT '入库仓库代码',\n"
            + "  `warehousename` varchar(100) DEFAULT NULL COMMENT '入库仓库名称',\n"
            + "  `remark` varchar(500) DEFAULT NULL, `createtime` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `updatetime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "  PRIMARY KEY (`id`), UNIQUE KEY `uk_receivedetail_shop_code` (`shop_code`,`detailcode`),\n"
            + "  KEY `idx_receivedetail_receive` (`shop_code`,`receivecode`), KEY `idx_receivedetail_goods` (`shop_code`,`goodscode`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
    }
}
