package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.PurchaseOrder;

/**
 * 首页看板那几条聚合查询的口径检查。
 *
 * <p>这些 SQL 决定首页上「今天该干什么」那几个数。状态列表漏一个或多一个都不会报错，
 * 只会让数字悄悄偏掉——待收货漏掉「部分到货」，收了一半还在等的单就从看板上消失了，
 * 而那些恰恰是最该盯的。所以把口径钉在断言里。</p>
 *
 * <p>顺带钉住两个「合成状态」：{@code pending_receive} 和 {@code 在产} 在库里都不存在，
 * 是看板卡片点进去时用的筛选值，由列表 SQL 翻成 in 条件。OGNL 的
 * {@code <choose>} 分支走错了不会抛，只会静默退化成「不筛」或「按字面值筛」，
 * 两种都是列表里一条都不剩或者全都在。</p>
 */
class DashboardSqlTest
{
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception
    {
        configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource()));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        for (String resource : Arrays.asList(
                "mapper/inventory/DashboardMapper.xml",
                "mapper/system/PurchaseOrderMapper.xml",
                "mapper/system/ProductionOrderMapper.xml"))
        {
            try (InputStream in = DashboardSqlTest.class.getClassLoader().getResourceAsStream(resource))
            {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static String sql(String statement, Object params)
    {
        MappedStatement mappedStatement = configuration.getMappedStatement(statement);
        BoundSql boundSql = mappedStatement.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> shop()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("shopCode", "S001");
        return params;
    }

    /* ==========================================================================
     * 待我处理：库存单据、销售订单、采购订单三条队列
     * ========================================================================== */

    /**
     * 库存单据里只有销售退货入库按创建人收窄，别的类型一概不收。
     *
     * <p>{@code u_f_docmain} 是共享表，采购入库、销售出库、调拨、生产、借出归还都在里面，
     * 那些是仓库的活儿。要是把归属条件加到整条 where 上，「采购入库 5 单」会当场变成 0——
     * 入库单本来就不是业务员建的。所以判据必须是「不是这个类型，或者是我建的」。</p>
     */
    @Test
    void pendingDocumentsScopeOnlyTheOwnedDocType()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609"));
        params.put("ownedDocType", "XSTHRK");
        params.put("owner", "zhangsan");
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingDocumentsByType", params);
        assertTrue(sql.contains("(doctype != ? or createuser = ?)"), sql);
        // 光有 createuser = ? 而没有前半截，就是把整表都收窄了
        assertFalse(sql.contains("and createuser = ?"), sql);
    }

    /** 能看全部的人不加这个条件。 */
    @Test
    void pendingDocumentsWithoutOwnerHaveNoCreatorFilter()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609"));
        params.put("ownedDocType", "XSTHRK");
        params.put("owner", null);
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingDocumentsByType", params);
        assertFalse(sql.contains("createuser"), sql);
        assertFalse(sql.contains("doctype !="), sql);
    }

    /** 一张月表一个 union 分支，各自带着自己的月份值——前端要靠这个月份定位列表。 */
    @Test
    void pendingSalesOrdersUnionsOneBranchPerMonth()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609", "202608"));
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingSalesOrdersByMonth", params);
        assertTrue(sql.contains("from sdsorder.u_o_order_202609"), sql);
        assertTrue(sql.contains("from sdsorder.u_o_order_202608"), sql);
        assertTrue(sql.contains("'202609' as docMonth"), sql);
        assertTrue(sql.contains("'202608' as docMonth"), sql);
        assertTrue(sql.contains("union all"), sql);
    }

    /** 只数待审核的，别的状态一概不算——这个数是「等着我点审核」的张数。 */
    @Test
    void pendingSalesOrdersOnlyCountsPendingState()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609"));
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingSalesOrdersByMonth", params);
        assertTrue(sql.contains("orderstate = '待审核'"), sql);
    }

    /**
     * 没有 order:sales:all 的业务员，只数他自己名下的单。
     *
     * <p>看板和订单列表是同一块业务的两个出口，行级权限必须同时生效。少了这一句，
     * 首页说「销售订单 6 单」，点进列表只有他自己的 1 张——用户会以为列表坏了。
     * 见 {@code docs/订单只看自己的单（业务员权限）.md}。</p>
     */
    @Test
    void pendingSalesOrdersHonourOwnScope()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609"));
        params.put("owner", "zhangsan");
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingSalesOrdersByMonth", params);
        assertTrue(sql.contains("staffcode = ?"), sql);
    }

    /** 能看全部的人不加这个条件——owner 为 null 就是「不筛」。 */
    @Test
    void pendingSalesOrdersWithoutOwnerHaveNoStaffFilter()
    {
        Map<String, Object> params = shop();
        params.put("suffixes", Arrays.asList("202609"));
        params.put("owner", null);
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingSalesOrdersByMonth", params);
        assertFalse(sql.contains("staffcode"), sql);
    }

    /**
     * 采购订单的待审核是英文 {@code submitted}，不是中文。
     *
     * <p>两个模块的状态词汇表不是一套：销售订单存中文，采购订单存
     * draft/submitted/approved/…。照着另一边改会静默筛出 0 张。</p>
     */
    @Test
    void pendingPurchaseOrdersUseEnglishStatus()
    {
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPendingPurchaseOrderCount", shop());
        assertTrue(sql.contains("status = 'submitted'"), sql);
        assertTrue(sql.contains("from sdsshop.u_p_purchase"), sql);
    }

    /* ==========================================================================
     * 本月销售
     * ========================================================================== */

    /**
     * 只算审核通过且没作废的单。
     *
     * <p>草稿、待审核、已驳回还没成立，已取消、已关闭已经作废，任何一种混进来
     * 都是把「本月卖了多少」往上虚增。</p>
     */
    @Test
    void salesAmountOnlyCountsLiveOrders()
    {
        Map<String, Object> params = shop();
        params.put("suffix", "202609");
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectSalesAmountByCurrency", params);
        assertTrue(sql.contains("orderstate in ('已备货', '备货中', '发货中', '已完成')"), sql);
        assertFalse(sql.contains("'草稿'"), sql);
        assertFalse(sql.contains("'已取消'"), sql);
    }

    /**
     * 按币种分组，而且空币种归到 CNY。
     *
     * <p>系统里没有汇率，跨币种相加是造假；老数据里 currency 为空的单不兜底的话
     * 会单独成一组，在浮层里显示成一个没有币种的金额。</p>
     */
    @Test
    void salesAmountGroupsByCurrencyWithCnyFallback()
    {
        Map<String, Object> params = shop();
        params.put("suffix", "202609");
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectSalesAmountByCurrency", params);
        assertTrue(sql.contains("group by coalesce(nullif(trim(currency), ''), 'CNY')"), sql);
    }

    /** 销售额同样跟着行级权限收窄：业务员看到的是自己的业绩，不是全店的。 */
    @Test
    void salesAmountHonoursOwnScope()
    {
        Map<String, Object> params = shop();
        params.put("suffix", "202609");
        params.put("owner", "zhangsan");
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectSalesAmountByCurrency", params);
        assertTrue(sql.contains("staffcode = ?"), sql);
    }

    @Test
    void salesAmountWithoutOwnerHasNoStaffFilter()
    {
        Map<String, Object> params = shop();
        params.put("suffix", "202609");
        params.put("owner", null);
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectSalesAmountByCurrency", params);
        assertFalse(sql.contains("staffcode"), sql);
    }


    /* ==========================================================================
     * 采购待收货 / 在产工单
     * ========================================================================== */

    /**
     * 待收货是两个状态，且超期的判据是传进来的业务日期，不是数据库的 curdate()。
     *
     * <p>curdate() 取的是数据库所在时区的今天，和服务层按 Asia/Shanghai 算的其余日期
     * 会在跨日那几个小时里打架。</p>
     */
    @Test
    void purchaseFollowUpCoversBothWaitingStates()
    {
        Map<String, Object> params = shop();
        params.put("today", new Date());
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectPurchaseFollowUp", params);
        assertTrue(sql.contains("status in ('approved', 'partial_received')"), sql);
        assertTrue(sql.contains("expectdate < ?"), sql);
        assertFalse(sql.contains("curdate()"), sql);
    }

    /** 在产 = 还没完工也没关闭的三种。漏掉「待领料」会把领了一半的工单当成不存在。 */
    @Test
    void productionFollowUpCoversAllUnfinishedStates()
    {
        Map<String, Object> params = shop();
        params.put("today", new Date());
        String sql = sql("com.ruoyi.system.mapper.DashboardMapper.selectProductionFollowUp", params);
        assertTrue(sql.contains("status in ('待生产', '待领料', '生产中')"), sql);
        assertTrue(sql.contains("plandate < ?"), sql);
    }

    /* ==========================================================================
     * 两个合成状态：看板点进列表页时用的筛选值
     * ========================================================================== */

    /** pending_receive 翻成两个真实状态的 in，不能退化成按字面值等值筛。 */
    @Test
    void purchaseListExpandsPendingReceive()
    {
        PurchaseOrder query = new PurchaseOrder();
        query.setShopCode("S001");
        query.setStatus("pending_receive");
        String sql = sql("com.ruoyi.system.mapper.PurchaseOrderMapper.selectPurchaseOrderList", query);
        assertTrue(sql.contains("p.status in ('approved', 'partial_received')"), sql);
        assertFalse(sql.contains("p.status = ?"), sql);
    }

    /** 真实状态照旧走等值，合成状态的分支不能把它们一起吃掉。 */
    @Test
    void purchaseListKeepsRealStatusAsEquals()
    {
        PurchaseOrder query = new PurchaseOrder();
        query.setShopCode("S001");
        query.setStatus("submitted");
        String sql = sql("com.ruoyi.system.mapper.PurchaseOrderMapper.selectPurchaseOrderList", query);
        assertTrue(sql.contains("p.status = ?"), sql);
        assertFalse(sql.contains("partial_received"), sql);
    }

    /** 不填状态就不加这个条件，别因为多了 choose 就变成永远筛。 */
    @Test
    void purchaseListWithoutStatusHasNoStatusFilter()
    {
        PurchaseOrder query = new PurchaseOrder();
        query.setShopCode("S001");
        String sql = sql("com.ruoyi.system.mapper.PurchaseOrderMapper.selectPurchaseOrderList", query);
        assertFalse(sql.contains("p.status ="), sql);
        assertFalse(sql.contains("p.status in"), sql);
    }

    /** 「在产」翻成三个真实状态的 in，口径与看板那条 SQL 必须一致。 */
    @Test
    void productionListExpandsInProduction()
    {
        ProductionOrder query = new ProductionOrder();
        query.setShopCode("S001");
        query.setStatus("在产");
        String sql = sql("com.ruoyi.system.mapper.ProductionOrderMapper.selectProductionOrderList", query);
        assertTrue(sql.contains("status in ('待生产', '待领料', '生产中')"), sql);
    }

    /** 真实状态照旧走等值。 */
    @Test
    void productionListKeepsRealStatusAsEquals()
    {
        ProductionOrder query = new ProductionOrder();
        query.setShopCode("S001");
        query.setStatus("已完工");
        String sql = sql("com.ruoyi.system.mapper.ProductionOrderMapper.selectProductionOrderList", query);
        assertTrue(sql.contains("status = ?"), sql);
        assertFalse(sql.contains("待领料"), sql);
    }
}
