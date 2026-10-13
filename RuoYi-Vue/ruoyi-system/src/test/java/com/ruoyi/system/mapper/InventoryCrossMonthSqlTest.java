package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryLedger;

/**
 * 跨月查询的动态 SQL 检查。
 *
 * <p>这两条语句里月表名是用 {@code ${}} 拼进去的，拼错了要等运行时才炸，而且报出来的是
 * 原始 SQL 错误。这里直接把 mapper XML 解析出来生成一遍 SQL，结构、OGNL 表达式、
 * union 分支数量都在编译期之后的第一时间就能验掉。</p>
 */
class InventoryCrossMonthSqlTest
{
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception
    {
        configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource()));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        build("mapper/inventory/DocumentMainMapper.xml");
        build("mapper/inventory/InventoryLedgerMapper.xml");
    }

    private static void build(String resource) throws Exception
    {
        try (InputStream in = InventoryCrossMonthSqlTest.class.getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static String sqlOf(String statementId, Object parameter)
    {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(parameter);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> params(String key, Object value, String key2, Object value2)
    {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        map.put(key2, value2);
        return map;
    }

    @Test
    void documentCrossMonthQueryUnionsOneBranchPerMonth()
    {
        DocumentMain query = new DocumentMain();
        query.setShopCode("S001");
        List<String> suffixes = Arrays.asList("202608", "202607", "202606");

        String sql = sqlOf("com.ruoyi.system.mapper.DocumentMainMapper.selectDocumentListAcrossMonths",
                params("query", query, "suffixes", suffixes));

        assertTrue(sql.contains("u_f_docmain_202608"));
        assertTrue(sql.contains("u_f_docmain_202607"));
        assertTrue(sql.contains("u_f_docmain_202606"));
        assertEquals(2, countOf(sql, "union all"));

        // sys_user 连在 union 外面：连几次只取决于要显示几个人名（创建人、审核人），
        // 不随月表数量增长。挪进 <foreach> 的话，查 3 个月就连 3 倍。
        //
        // 这里比的是「1 个月」和「3 个月」的连接数，不写死具体数字——原先断言的是
        // 「只连一次」，后来加了审核人那一连就成了 2，测试跟着挂掉，可 union 外面这件事
        // 从头到尾没变过。数字会跟着显示需求变，要守的不是它。
        String oneMonth = sqlOf("com.ruoyi.system.mapper.DocumentMainMapper.selectDocumentListAcrossMonths",
                params("query", query, "suffixes", Arrays.asList("202608")));
        assertEquals(countOf(oneMonth, "left join sys_user"), countOf(sql, "left join sys_user"),
                "sys_user 的连接数不该随月表数量变，否则就是被挪进 union 分支里了");
        assertTrue(sql.endsWith("order by docdate desc, doccode desc"));
    }

    @Test
    void documentDateRangeStillFiltersInsideEachMonthTable()
    {
        DocumentMain query = new DocumentMain();
        query.setShopCode("S001");
        query.setDocDateBegin(new Date());
        query.setDocDateEnd(new Date());

        String sql = sqlOf("com.ruoyi.system.mapper.DocumentMainMapper.selectDocumentListAcrossMonths",
                params("query", query, "suffixes", Arrays.asList("202608", "202607")));

        // 区间首尾两张月表里有不该出现的行，光靠挑月表收不干净
        assertEquals(2, countOf(sql, "m.docdate >="));
        assertEquals(2, countOf(sql, "m.docdate <="));
    }

    @Test
    void documentTypesFilterSplitsCommaSeparatedValue()
    {
        DocumentMain query = new DocumentMain();
        query.setShopCode("S001");
        query.setDocTypes("CK,LLCK,OO");

        String sql = sqlOf("com.ruoyi.system.mapper.DocumentMainMapper.selectDocumentListAcrossMonths",
                params("query", query, "suffixes", Arrays.asList("202608")));

        assertTrue(sql.contains("m.doctype in ( ? , ? , ? )"));
    }

    @Test
    void documentSingleMonthQueryStillTargetsExactlyOneTable()
    {
        DocumentMain query = new DocumentMain();
        query.setShopCode("S001");
        query.setTableSuffix("202607");

        Map<String, Object> parameter = new HashMap<>();
        parameter.put("query", query);
        String sql = sqlOf("com.ruoyi.system.mapper.DocumentMainMapper.selectDocumentList", parameter);

        assertTrue(sql.contains("u_f_docmain_202607"));
        assertFalse(sql.contains("union all"));
    }

    @Test
    void ledgerCrossMonthQueryUnionsOneBranchPerMonth()
    {
        InventoryLedger query = new InventoryLedger();
        query.setShopCode("S001");

        String sql = sqlOf("com.ruoyi.system.mapper.InventoryLedgerMapper.selectLedgerListAcrossMonths",
                params("query", query, "suffixes", Arrays.asList("202608", "202607")));

        assertTrue(sql.contains("u_f_ledger_202608"));
        assertTrue(sql.contains("u_f_ledger_202607"));
        assertEquals(1, countOf(sql, "union all"));
        // 排序只在外层做一次，分支内不排也不截断
        assertEquals(1, countOf(sql, "order by operate_time desc"));
    }

    @Test
    void ledgerTimeRangeFiltersInsideEachMonthTable()
    {
        InventoryLedger query = new InventoryLedger();
        query.setShopCode("S001");
        query.setOperateTimeBegin(new Date());
        query.setOperateTimeEnd(new Date());

        String sql = sqlOf("com.ruoyi.system.mapper.InventoryLedgerMapper.selectLedgerListAcrossMonths",
                params("query", query, "suffixes", Arrays.asList("202608", "202607")));

        assertEquals(2, countOf(sql, "operate_time >="));
        assertEquals(2, countOf(sql, "operate_time <="));
    }

    @Test
    void ledgerSingleMonthQueryStillTargetsExactlyOneTable()
    {
        InventoryLedger query = new InventoryLedger();
        query.setShopCode("S001");
        query.setTableSuffix("202607");

        Map<String, Object> parameter = new HashMap<>();
        parameter.put("query", query);
        String sql = sqlOf("com.ruoyi.system.mapper.InventoryLedgerMapper.selectLedgerList", parameter);

        assertTrue(sql.contains("u_f_ledger_202607"));
        assertFalse(sql.contains("union all"));
    }

    private static int countOf(String text, String token)
    {
        int count = 0;
        int from = text.indexOf(token);
        while (from >= 0)
        {
            count++;
            from = text.indexOf(token, from + token.length());
        }
        return count;
    }
}
