package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
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
import com.ruoyi.system.domain.SalesOrder;

/**
 * 订单归属业务员（{@code staffcode}）的落库口径检查。
 *
 * <p>这个字段以后要拿来算员工提成，是笔钱。它必须满足两条：<b>建单时写下来</b>、
 * <b>之后任何改单都动不了它</b>。两条都靠 SQL 的列清单保证，不是靠调用方自觉——
 * 改单表单漏带一次就会把归属冲成 NULL，而且不报错，等到发提成才发现整月的单没有归属。</p>
 *
 * <p>后半部分是<b>按归属筛选</b>的落法：姓名不在订单表里，服务层反查成一批账号再筛，
 * 空集合必须落成 {@code 1 = 0} 而不是「没有条件」。</p>
 *
 * <p>断言写在 SQL 文本上，因为这些都是「少一列 / 少一个条件」的问题，跑不出异常，只能钉住。</p>
 */
class SalesOrderStaffAttributionSqlTest
{
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception
    {
        configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource()));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        String resource = "mapper/system/SalesOrderMapper.xml";
        try (InputStream in = SalesOrderStaffAttributionSqlTest.class.getClassLoader()
                .getResourceAsStream(resource))
        {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static String sqlOf(String statementId)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode("S001");
        order.setOrderCode("SO20260910001");
        Map<String, Object> params = new HashMap<>();
        params.put("order", order);
        params.put("tableSuffix", "202609");
        MappedStatement statement = configuration.getMappedStatement(
                "com.ruoyi.system.mapper.SalesOrderMapper." + statementId);
        BoundSql boundSql = statement.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    /** 列表查询的 SQL，用来检查按归属筛选的条件怎么落。 */
    private static String listSql(java.util.List<String> staffCodes)
    {
        SalesOrder query = new SalesOrder();
        query.setShopCode("S001");
        query.setStaffCodes(staffCodes);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("tableSuffix", "202609");
        MappedStatement statement = configuration.getMappedStatement(
                "com.ruoyi.system.mapper.SalesOrderMapper.selectList");
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }

    /** 没有按名称搜（staffCodes 为 null）时不该多出任何归属条件。 */
    @Test
    void listWithoutStaffFilterAddsNoCondition()
    {
        String sql = listSql(null);
        assertFalse(sql.contains("staffcode in"));
        assertFalse(sql.contains("1 = 0"));
    }

    /** 按名称搜到了人 → 落成 staffcode in (...)，走 idx_order_shop_staff。 */
    @Test
    void listFiltersByResolvedAccounts()
    {
        assertTrue(listSql(java.util.Arrays.asList("zhangsan", "lisi")).contains("staffcode in"));
    }

    /**
     * 按名称搜但一个人都没匹配上 → 必须落成 {@code 1 = 0}。
     *
     * <p>这是最容易写错的一条：空集合如果被当成「没有条件」，结果就是把全月的订单
     * 都列出来，看着像搜索没生效，而正确结果是一条都搜不到。</p>
     */
    @Test
    void listMatchesNothingWhenNoAccountResolved()
    {
        String sql = listSql(new java.util.ArrayList<String>());
        assertTrue(sql.contains("1 = 0"));
        assertFalse(sql.contains("staffcode in"));
    }

    /** 建单要把归属写下来，否则这张单从一开始就没有主人。 */
    @Test
    void insertRecordsStaffCode()
    {
        assertTrue(sqlOf("insert").contains("staffcode"));
    }

    /**
     * 改单不许碰归属。
     *
     * <p>原来这条 update 里带着 {@code staffcode = #{order.staffCode}}，而前端改单表单
     * 是否回传这个值并没有任何东西保证。撤审 → 改单这条路走一遍就可能把归属清空。</p>
     */
    @Test
    void updateDraftLeavesStaffCodeAlone()
    {
        String sql = sqlOf("updateDraft");
        // 先确认语句本身解析出来了，免得下面两条断言在空字符串上白过
        assertTrue(sql.contains("buyercode ="));
        assertFalse(sql.contains("staffcode"));
        assertFalse(sql.contains("staffname"));
    }
}
