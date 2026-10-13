package com.ruoyi.common.config.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.core.domain.PriceScopeAware;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.PriceScope;
import com.ruoyi.common.utils.PriceMaskUtils;

/**
 * 金额按岗位可见：走真序列化，不是对着判权方法打桩。
 *
 * <p>这套机制唯一的出口就是 Jackson，用 mock 验判权逻辑等于没验到真正会出问题的地方
 * （注解没生效、contextual 化没走到、AUTO 拿不到所在对象）。</p>
 *
 * <p>见 {@code docs/金额按岗位可见.md}。</p>
 */
class PriceJsonSerializerTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 平台超管的账号，默认取值见 RuoYiConfig.superAdminCodes */
    private static final String SUPER_ADMIN = "us1";

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 四种岗位
    // ------------------------------------------------------------------

    /** 销售：看得到销售价，看不到采购与成本 */
    @Test
    void salesSeesSalePriceOnly()
    {
        login("us100", "price:sale");
        String json = MAPPER.writeValueAsString(sample());
        assertEquals(null, read(json, "costPrice"));
        assertEquals("8.00", read(json, "salePrice"));
    }

    /** 采购：看得到采购与成本，看不到销售价 */
    @Test
    void purchaserSeesCostPriceOnly()
    {
        login("us101", "price:cost");
        String json = MAPPER.writeValueAsString(sample());
        assertEquals("5.00", read(json, "costPrice"));
        assertEquals(null, read(json, "salePrice"));
    }

    /** 仓库：两样都看不到，但数量照常 —— 他的活儿是管货不是管钱 */
    @Test
    void warehouseSeesNeitherButKeepsQty()
    {
        login("us102");
        String json = MAPPER.writeValueAsString(sample());
        assertEquals(null, read(json, "costPrice"));
        assertEquals(null, read(json, "salePrice"));
        assertEquals("3", read(json, "qty"), "数量不是金额，不该被遮");
    }

    /** 财务：两样都看得到 */
    @Test
    void financeSeesBoth()
    {
        login("us103", "price:cost", "price:sale");
        String json = MAPPER.writeValueAsString(sample());
        assertEquals("5.00", read(json, "costPrice"));
        assertEquals("8.00", read(json, "salePrice"));
    }

    // ------------------------------------------------------------------
    // 超管与无登录
    // ------------------------------------------------------------------

    /**
     * 平台超管不受这两个权限约束。
     *
     * <p>这里故意不给他任何 price 权限：超管进入某个商家之后，权限集会被换成该商家的
     * 业务权限，{@code *:*:*} 并不在里面，判权必须认账号本身。</p>
     */
    @Test
    void superAdminSeesBothWithoutPricePermissions()
    {
        login(SUPER_ADMIN);
        String json = MAPPER.writeValueAsString(sample());
        assertEquals("5.00", read(json, "costPrice"));
        assertEquals("8.00", read(json, "salePrice"));
    }

    /** 拿到 *:*:* 的（未进商家的超管、平台角色）同样全看得到 */
    @Test
    void wildcardPermissionSeesBoth()
    {
        login("us104", "*:*:*");
        String json = MAPPER.writeValueAsString(sample());
        assertEquals("5.00", read(json, "costPrice"));
        assertEquals("8.00", read(json, "salePrice"));
    }

    /** 没有登录上下文（定时任务、内部调用）：读一律遮蔽，宁可少显示一次 */
    @Test
    void noLoginContextMasksEverything()
    {
        SecurityContextHolder.clearContext();
        String json = MAPPER.writeValueAsString(sample());
        assertEquals(null, read(json, "costPrice"));
        assertEquals(null, read(json, "salePrice"));
    }

    // ------------------------------------------------------------------
    // 一列两用（PriceScope.AUTO）
    // ------------------------------------------------------------------

    /** 销售出库单的总金额是销售额，有 price:sale 就该看得到 */
    @Test
    void autoResolvedToSaleIsVisibleToSales()
    {
        login("us100", "price:sale");
        assertEquals("100.00", read(MAPPER.writeValueAsString(auto(PriceScope.SALE)), "totalAmount"));
    }

    /** 同一个字段，其余单据存的是成本额，销售就看不到了 */
    @Test
    void autoResolvedToCostIsHiddenFromSales()
    {
        login("us100", "price:sale");
        assertEquals(null, read(MAPPER.writeValueAsString(auto(PriceScope.COST)), "totalAmount"));
    }

    /**
     * 判不出口径（查询条件对象、docType 为空的草稿）就按最严的走：两个权限都有才给看。
     */
    @Test
    void autoUnresolvedNeedsBothPermissions()
    {
        login("us100", "price:sale");
        assertEquals(null, read(MAPPER.writeValueAsString(auto(null)), "totalAmount"),
                "只有一半权限，判不出口径时不能给看");

        SecurityContextHolder.clearContext();
        login("us103", "price:cost", "price:sale");
        assertEquals("100.00", read(MAPPER.writeValueAsString(auto(null)), "totalAmount"));
    }

    /** 所在对象压根没实现 PriceScopeAware 时也走最严的那条，不能抛异常 */
    @Test
    void autoOnPlainBeanFallsBackToStrict()
    {
        login("us100", "price:sale");
        assertEquals(null, read(MAPPER.writeValueAsString(new PlainAuto()), "totalAmount"));

        SecurityContextHolder.clearContext();
        login("us103", "price:cost", "price:sale");
        assertEquals("100.00", read(MAPPER.writeValueAsString(new PlainAuto()), "totalAmount"));
    }

    // ------------------------------------------------------------------
    // 只遮出，不遮进
    // ------------------------------------------------------------------

    /**
     * 读和写对「没有登录上下文」的默认值必须相反。
     *
     * <p>两边共用一套判断的后果实测过：采购发货单的运费在单元测试里被当场清零。</p>
     */
    @Test
    void writeSideAcceptsValuesWhenThereIsNoLoginContext()
    {
        SecurityContextHolder.clearContext();
        assertFalse(PriceMaskUtils.isCostMaskedOnWrite(), "没有要防的人，写入照收");
        assertFalse(PriceMaskUtils.isSaleMaskedOnWrite());
        assertFalse(PriceMaskUtils.isCostVisible(), "同一时刻，读侧必须是遮蔽的");
        assertFalse(PriceMaskUtils.isSaleVisible());
    }

    /** 登录了但确实无权，写侧才拦；有权就放行 */
    @Test
    void writeSideGuardsOnlyWhenLoggedInAndUnauthorized()
    {
        login("us102");
        assertTrue(PriceMaskUtils.isCostMaskedOnWrite());
        assertTrue(PriceMaskUtils.isSaleMaskedOnWrite());

        SecurityContextHolder.clearContext();
        login("us103", "price:cost", "price:sale");
        assertFalse(PriceMaskUtils.isCostMaskedOnWrite());
        assertFalse(PriceMaskUtils.isSaleMaskedOnWrite());
    }

    /** keepIfMasked：看不到就沿用库里的原值，看得到就认前端传的值 */
    @Test
    void keepIfMaskedFallsBackToExistingValue()
    {
        login("us102");
        assertEquals(new BigDecimal("5.00"),
                PriceMaskUtils.keepIfMasked(PriceScope.COST, null, new BigDecimal("5.00")));

        SecurityContextHolder.clearContext();
        login("us101", "price:cost");
        assertEquals(new BigDecimal("9.00"),
                PriceMaskUtils.keepIfMasked(PriceScope.COST, new BigDecimal("9.00"), new BigDecimal("5.00")));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void login(String userCode, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserCode(userCode);
        LoginUser loginUser = new LoginUser(user, Set.of(permissions));
        loginUser.setUserCode(userCode);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private Sample sample()
    {
        Sample sample = new Sample();
        sample.costPrice = new BigDecimal("5.00");
        sample.salePrice = new BigDecimal("8.00");
        sample.qty = new BigDecimal("3");
        return sample;
    }

    private AutoSample auto(PriceScope resolved)
    {
        AutoSample sample = new AutoSample();
        sample.totalAmount = new BigDecimal("100.00");
        sample.resolved = resolved;
        return sample;
    }

    /**
     * 从报文里取一个字段的<b>原始字面量</b>，字段写成 null 时返回 {@code null}。
     *
     * <p>刻意不走 {@code readTree}：Jackson 默认把小数读成 double，{@code 5.00} 读回来
     * 是 {@code 5.0}，断言就验不到真正发出去的那串字符了。这里要对的就是报文本身。</p>
     */
    private String read(String json, String field)
    {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(null|[^,}]+)").matcher(json);
        assertTrue(matcher.find(), "报文里没有字段 " + field + "：" + json);
        String raw = matcher.group(1).trim();
        return "null".equals(raw) ? null : raw;
    }

    public static class Sample
    {
        @PriceField(PriceScope.COST)
        public BigDecimal costPrice;

        @PriceField(PriceScope.SALE)
        public BigDecimal salePrice;

        /** 数量不是金额，用来确认遮蔽只作用在打了注解的字段上 */
        public BigDecimal qty;

        public BigDecimal getCostPrice() { return costPrice; }
        public BigDecimal getSalePrice() { return salePrice; }
        public BigDecimal getQty() { return qty; }
    }

    /** 一列两用：自己按业务类型说这一次算哪一侧，对应 DocumentMain.totalAmount */
    public static class AutoSample implements PriceScopeAware
    {
        @PriceField(PriceScope.AUTO)
        public BigDecimal totalAmount;

        public PriceScope resolved;

        public BigDecimal getTotalAmount() { return totalAmount; }

        @Override
        public PriceScope resolvePriceScope(String fieldName)
        {
            return resolved;
        }
    }

    /** 标了 AUTO 却没实现 PriceScopeAware —— 后人漏实现时不能崩，只能更严 */
    public static class PlainAuto
    {
        @PriceField(PriceScope.AUTO)
        public BigDecimal totalAmount = new BigDecimal("100.00");

        public BigDecimal getTotalAmount() { return totalAmount; }
    }
}
