package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * 排产选单查询的过滤条件检查。
 *
 * <p>这条 SQL 决定「生产模块能看到哪些订单明细」。少一个条件就会把整月的订单全堆进
 * 选单界面，多一个条件则会让该排产的活儿凭空消失，两种都不会报错，只能靠断言钉住。</p>
 */
class SalesOrderCandidateSqlTest
{
    private static final String STATEMENT =
            "com.ruoyi.system.mapper.SalesOrderItemMapper.selectProductionCandidates";

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception
    {
        configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource()));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        String resource = "mapper/system/SalesOrderItemMapper.xml";
        try (InputStream in = SalesOrderCandidateSqlTest.class.getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static String candidateSql(String goodsCode)
    {
        Map<String, Object> params = new HashMap<>();
        params.put("shopCode", "S001");
        params.put("tableSuffix", "202608");
        params.put("goodsCode", goodsCode);
        MappedStatement statement = configuration.getMappedStatement(STATEMENT);
        BoundSql boundSql = statement.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    /**
     * 不按备货来源筛。
     *
     * <p>订单页已经不展示、也不再能登记备货来源，这个字段现在只由建单时的「专门生产」
     * 推出来。拿它当门槛就等于只有定制件排得了产，普通标准件想按订单做没有入口。</p>
     */
    @Test
    void doesNotFilterByStockSource()
    {
        assertEquals(-1, candidateSql(null).indexOf("stock_source ="));
    }

    /** 已过审且还能补货的三个状态，草稿 / 待审核 / 已取消 / 已关闭 / 已完成都不在内。 */
    @Test
    void onlyListsOrdersPastAudit()
    {
        String sql = candidateSql(null);
        assertTrue(sql.contains("o.orderstate in ('已备货', '备货中', '发货中')"));
    }

    /** 排产上限是未发量，发完的行不再列出。 */
    @Test
    void skipsFullyShippedLines()
    {
        assertTrue(candidateSql(null).contains("d.qty - coalesce(d.shippedqty, 0) > 0"));
    }

    /**
     * 不拿库存当门槛：订单不占库存，现货随时可能被别的单发走，
     * 用它筛只会把该排产的活儿漏掉。
     */
    @Test
    void doesNotFilterByStockOnHand()
    {
        String sql = candidateSql(null);
        assertTrue(sql.indexOf("u_m_stock") < 0);
        assertTrue(sql.indexOf("physicalqty") < 0);
    }

    /** 商品过滤是可选项：给了才拼，不给不能凭空多出条件。 */
    @Test
    void appliesGoodsFilterOnlyWhenGiven()
    {
        assertTrue(candidateSql("G001").contains("d.goodscode ="));
        assertEquals(-1, candidateSql(null).indexOf("d.goodscode ="));
    }
}
