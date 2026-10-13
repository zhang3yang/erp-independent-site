package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;

/**
 * 销售订单的横向权限：没有 {@code order:sales:all} 的业务员只看得到归属于自己的单。
 *
 * <p>归属认的是 {@code staffcode}（建单时的登录账号，改单动不了它）。</p>
 *
 * <p>这套测试里最要紧的不是「挡住了」，而是<b>「没挡错人」</b>——仓库发货、财务回款、
 * 生产完工都要按订单号取业务员的单，那些岗位手里没有也不该有这个权限，
 * 挡了就是把发货和回款直接搞停。所以最后一组用例专门钉住「不该挡的路没被挡」。</p>
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderOwnScopeTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202609";
    private static final String ME = "lixiao";
    private static final String OTHER = "wangwu";
    private static final String ALL = "order:sales:all";

    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper itemMapper;
    @Mock private StaffNameResolver staffNameResolver;

    @InjectMocks private SalesOrderServiceImpl service;

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 列表
    // ------------------------------------------------------------------

    /** 业务员（没有 order:sales:all）：列表被钉死在自己名下 */
    @Test
    void 业务员只看得到自己的单()
    {
        login(ME, "us100");
        stubEmptyList();

        service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals(Collections.singletonList(ME), captureQuery().getStaffCodes());
    }

    /** 有 order:sales:all：不加归属条件，看全店 */
    @Test
    void 有查看全部权限就不加归属条件()
    {
        login(ME, "us100", ALL);
        stubEmptyList();

        service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals(null, captureQuery().getStaffCodes(), "不该塞归属条件");
    }

    /** 平台超管豁免：他不做业务，只观察和维护 */
    @Test
    void 平台超管看得到全部()
    {
        login("wczladmin", "us1");
        stubEmptyList();

        service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals(null, captureQuery().getStaffCodes());
    }

    /**
     * 前端能往查询参数里塞 staffCodes，但它是服务层的内部载体，
     * 一律由服务层说了算——塞别人的账号进来照样只查得到自己的。
     */
    @Test
    void 前端塞进来的归属条件不算数()
    {
        login(ME, "us100");
        stubEmptyList();

        SalesOrder query = new SalesOrder();
        query.setStaffCodes(Arrays.asList(OTHER));
        service.selectOrderList(query, SUFFIX);

        assertEquals(Collections.singletonList(ME), captureQuery().getStaffCodes());
    }

    // ------------------------------------------------------------------
    // 与「按业务员名称搜」的关系：交集，不是覆盖
    // ------------------------------------------------------------------

    /** 搜自己的名字：还是自己的单 */
    @Test
    void 搜自己的名字取交集后仍是自己()
    {
        login(ME, "us100");
        stubEmptyList();
        when(staffNameResolver.loginsByNickNameLike("李晓")).thenReturn(Arrays.asList(ME));

        SalesOrder query = new SalesOrder();
        query.setStaffName("李晓");
        service.selectOrderList(query, SUFFIX);

        assertEquals(Collections.singletonList(ME), captureQuery().getStaffCodes());
    }

    /**
     * 搜同事的名字：正确结果是<b>一条都搜不到</b>。
     *
     * <p>空集合由 mapper 落成 {@code 1 = 0}。这里绝不能退化成「显示自己的单」——
     * 那会让业务员以为同事名下挂着这些单，比搜不到更误导。</p>
     */
    @Test
    void 搜同事的名字得到空结果而不是自己的单()
    {
        login(ME, "us100");
        stubEmptyList();
        when(staffNameResolver.loginsByNickNameLike("王五")).thenReturn(Arrays.asList(OTHER));

        SalesOrder query = new SalesOrder();
        query.setStaffName("王五");
        service.selectOrderList(query, SUFFIX);

        List<String> staffCodes = captureQuery().getStaffCodes();
        assertNotNull(staffCodes, "必须是空集合，不能是 null——null 等于没有条件，会把全月的单列出来");
        assertTrue(staffCodes.isEmpty());
    }

    // ------------------------------------------------------------------
    // 详情与本人动作
    // ------------------------------------------------------------------

    /** 详情：别人的单打不开。只拦列表不拦详情等于没拦，前端「详情」按钮打的就是这个接口 */
    @Test
    void 详情打不开别人的单()
    {
        login(ME, "us100");
        when(orderMapper.selectByCode(SHOP, "SO002", SUFFIX)).thenReturn(orderOf("SO002", OTHER));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.selectOrderDetail(SHOP, "SO002", SUFFIX));
        assertTrue(ex.getMessage().contains("不归属于你"), ex.getMessage());
    }

    /** 自己的单照常打得开 */
    @Test
    void 详情打得开自己的单()
    {
        login(ME, "us100");
        when(orderMapper.selectByCode(SHOP, "SO001", SUFFIX)).thenReturn(orderOf("SO001", ME));
        when(itemMapper.selectByOrderCode(SHOP, "SO001", SUFFIX)).thenReturn(Collections.emptyList());

        assertNotNull(service.selectOrderDetail(SHOP, "SO001", SUFFIX));
    }

    /** 关闭：动作路径同样认归属，不能绕过列表直接拿单号操作别人的单 */
    @Test
    void 关不掉别人的单()
    {
        login(ME, "us100");
        when(orderMapper.selectByCodeForUpdate(SHOP, "SO002", SUFFIX)).thenReturn(orderOf("SO002", OTHER));

        assertThrows(ServiceException.class,
                () -> service.closeOrder(SHOP, "SO002", SUFFIX, "不做了", ME));
    }

    // ------------------------------------------------------------------
    // ⚠ 不该挡的路：挡了就是把发货和回款搞停
    // ------------------------------------------------------------------

    /**
     * 仓库、财务、生产、退货、补发全都经 {@code findOrderAcrossMonths} 按订单号取单。
     * 他们手里没有 order:sales:all，这条路要是也认归属，发货和回款当场停摆。
     */
    @Test
    void 跨模块按单号取单不认归属()
    {
        login("cangku", "us200");   // 仓库账号，既没有 all，也不是这张单的业务员
        when(orderMapper.selectOrderTableSuffixes()).thenReturn(Arrays.asList(SUFFIX));
        when(itemMapper.selectByOrderCode(SHOP, "SO002", SUFFIX)).thenReturn(Collections.emptyList());
        when(orderMapper.selectByCode(SHOP, "SO002", SUFFIX)).thenReturn(orderOf("SO002", OTHER));

        SalesOrder found = service.findOrderAcrossMonths(SHOP, "SO002");

        assertNotNull(found, "仓库按单号取业务员的单被挡住了——发货链路会直接断掉");
        assertEquals(OTHER, found.getStaffCode());
    }

    /** 没有登录上下文（内部调用、定时任务、单元测试）一律放行，不能静默筛成空 */
    @Test
    void 无登录上下文不加归属条件()
    {
        SecurityContextHolder.clearContext();
        stubEmptyList();

        service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals(null, captureQuery().getStaffCodes());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void login(String userName, String userCode, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setUserCode(userCode);
        LoginUser loginUser = new LoginUser(user, Set.of(permissions));
        loginUser.setUserCode(userCode);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private SalesOrder orderOf(String orderCode, String staffCode)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(orderCode);
        order.setStaffCode(staffCode);
        order.setOrderState(SalesOrder.STATE_DRAFT);
        order.setTableSuffix(SUFFIX);
        return order;
    }

    private void stubEmptyList()
    {
        lenient().when(orderMapper.selectList(any(SalesOrder.class), eq(SUFFIX)))
                .thenReturn(Collections.emptyList());
    }

    private SalesOrder captureQuery()
    {
        ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).selectList(captor.capture(), anyString());
        return captor.getValue();
    }
}
