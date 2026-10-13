package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
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
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.SalesReship;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.SalesReshipItemMapper;
import com.ruoyi.system.mapper.SalesReshipMapper;
import com.ruoyi.system.mapper.ShipBatchItemMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.ISalesOrderService;

/**
 * 发货批次 / 销售退货 / 销售补发 三个页面的横向权限。
 *
 * <p>销售订单那条在 {@link SalesOrderOwnScopeTest}，共用的判权规则在
 * {@code OwnershipScope}。</p>
 *
 * <p>每个模块都钉两件事：<b>挡住了</b>，以及更要紧的<b>没挡错人</b>——
 * 仓库要打单发货、入库员要审退货单，他们本来就不是这些单的创建人，
 * 挡了就是把发货和退货入库直接搞停。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderModulesOwnScopeTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202609";
    private static final String ME = "lixiao";
    private static final String OTHER = "wangwu";

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    private static void login(String userName, String userCode, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setUserCode(userCode);
        LoginUser loginUser = new LoginUser(user, Set.of(permissions));
        loginUser.setUserCode(userCode);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    // ==================================================================
    // 发货批次
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 发货批次
    {
        @Mock private ShipBatchMapper batchMapper;
        @Mock private ShipBatchItemMapper batchItemMapper;
        @Mock private ISalesOrderService orderService;

        @InjectMocks private ShipBatchServiceImpl service;

        @Test
        void 业务员只看得到自己建的批次()
        {
            login(ME, "us100");
            lenient().when(batchMapper.selectList(any(ShipBatch.class), eq(SUFFIX)))
                    .thenReturn(Collections.emptyList());

            service.selectBatchList(new ShipBatch(), SUFFIX);

            assertEquals(ME, captureQuery().getOwnerScope());
        }

        @Test
        void 有查看全部权限就不加归属条件()
        {
            login(ME, "us100", ShipBatchServiceImpl.PERMI_VIEW_ALL);
            lenient().when(batchMapper.selectList(any(ShipBatch.class), eq(SUFFIX)))
                    .thenReturn(Collections.emptyList());

            service.selectBatchList(new ShipBatch(), SUFFIX);

            assertNull(captureQuery().getOwnerScope(), "不该塞归属条件");
        }

        /**
         * ⚠ 库管员：只有「仓库发货管理」页的权限，**没有** order:shipbatch:all，
         * 也一条批次都不是他建的 —— 照样要看得到全部。
         *
         * <p>「能进仓库发货页」和「只看得到自己建的」本来就分不开：那个页面的全部工作
         * 就是处理别人建的批次。这一条要是挂了，库管员的工作台是空的，货发不出去。</p>
         *
         * <p>它同时钉住「库管员不必再跨树去订单管理下勾一个权限」——那样会让他的侧边栏
         * 多出一个用不上的订单管理菜单，看着像权限越界。</p>
         */
        @Test
        void 库管员不用额外权限就看得到全部批次()
        {
            login("cangku", "us200", ShipBatchServiceImpl.PERMI_WAREHOUSE);
            lenient().when(batchMapper.selectList(any(ShipBatch.class), eq(SUFFIX)))
                    .thenReturn(Collections.emptyList());

            service.selectBatchList(new ShipBatch(), SUFFIX);

            assertNull(captureQuery().getOwnerScope(), "库管员被按归属筛了，工作台会是空的");
        }

        /** 库管员也打得开别人建的批次详情，否则点「详情」就报错 */
        @Test
        void 库管员打得开别人建的批次()
        {
            login("cangku", "us200", ShipBatchServiceImpl.PERMI_WAREHOUSE);
            when(batchMapper.selectByCode(SHOP, "FH002", SUFFIX)).thenReturn(batchOf("FH002", OTHER));
            when(batchItemMapper.selectByShipCode(SHOP, "FH002", SUFFIX)).thenReturn(Collections.emptyList());
            when(orderService.findOrderAcrossMonths(SHOP, "SO001")).thenReturn(shippableOrder());

            assertNotNull(service.selectBatchDetail(SHOP, "FH002", SUFFIX));
        }

        /** 前端能往查询参数里塞 ownerScope，服务层一律覆盖 */
        @Test
        void 前端塞进来的归属条件不算数()
        {
            login(ME, "us100");
            lenient().when(batchMapper.selectList(any(ShipBatch.class), eq(SUFFIX)))
                    .thenReturn(Collections.emptyList());

            ShipBatch query = new ShipBatch();
            query.setOwnerScope(OTHER);
            service.selectBatchList(query, SUFFIX);

            assertEquals(ME, captureQuery().getOwnerScope());
        }

        @Test
        void 详情打不开别人的批次()
        {
            login(ME, "us100");
            when(batchMapper.selectByCode(SHOP, "FH002", SUFFIX)).thenReturn(batchOf("FH002", OTHER));

            assertThrows(ServiceException.class,
                    () -> service.selectBatchDetail(SHOP, "FH002", SUFFIX));
        }

        /**
         * 不传月份时详情会退到跨月查找那条路。不挡的话，省掉 tableSuffix 就是一个后门。
         */
        @Test
        void 省掉月份也绕不过去()
        {
            login(ME, "us100");
            when(batchMapper.selectShipBatchTableSuffixes()).thenReturn(Arrays.asList(SUFFIX));
            when(batchMapper.selectByCode(SHOP, "FH002", SUFFIX)).thenReturn(batchOf("FH002", OTHER));

            assertThrows(ServiceException.class,
                    () -> service.findBatchAcrossMonths(SHOP, "FH002"));
        }

        /**
         * ⚠ 仓库打备货单处理的本来就是别人建的批次，这条路不认归属。
         *
         * <p>认了的话仓库谁的单都动不了，货全压在仓库里。</p>
         */
        @Test
        void 仓库打备货单不认归属()
        {
            login("cangku", "us200");   // 仓库账号：没有 all，也不是创建人
            ShipBatch batch = batchOf("FH002", OTHER);
            batch.setStatus(ShipBatch.STATUS_SUBMITTED);
            when(batchMapper.selectByCodeForUpdate(SHOP, "FH002", SUFFIX)).thenReturn(batch);
            when(orderService.findOrderAcrossMonths(SHOP, "SO001")).thenReturn(shippableOrder());
            when(batchMapper.updateStatus(eq(SHOP), eq("FH002"), eq(SUFFIX), anyString(),
                    anyString(), anyString(), any())).thenReturn(1);

            assertDoesNotThrow(() -> service.printPickList(SHOP, "FH002", SUFFIX, "cangku"));
        }

        /**
         * ⚠ 仓库的「备货完成」同样不认归属，理由同上。
         *
         * <p>这一格是新加的，单独守一条：它走的是专用的 completePicking 语句而不是
         * 通用的 transit，很容易在加的时候忘了把 ownerOnly 关掉。</p>
         */
        @Test
        void 仓库备货完成不认归属()
        {
            login("cangku", "us200");
            ShipBatch batch = batchOf("FH002", OTHER);
            batch.setStatus(ShipBatch.STATUS_PICK_PRINTED);
            when(batchMapper.selectByCodeForUpdate(SHOP, "FH002", SUFFIX)).thenReturn(batch);
            when(batchItemMapper.selectByShipCode(SHOP, "FH002", SUFFIX))
                    .thenReturn(Arrays.asList(new com.ruoyi.system.domain.ShipBatchItem()));
            when(orderService.findOrderAcrossMonths(SHOP, "SO001")).thenReturn(shippableOrder());
            when(batchMapper.completePicking(eq(SHOP), eq("FH002"), eq(SUFFIX), anyString(),
                    anyString(), any())).thenReturn(1);

            assertDoesNotThrow(() -> service.completePicking(SHOP, "FH002", SUFFIX, "/a.jpg", "cangku"));
        }

        private com.ruoyi.system.domain.SalesOrder shippableOrder()
        {
            com.ruoyi.system.domain.SalesOrder order = new com.ruoyi.system.domain.SalesOrder();
            order.setShopCode(SHOP);
            order.setOrderCode("SO001");
            order.setOrderState(com.ruoyi.system.domain.SalesOrder.STATE_READY);
            return order;
        }

        private ShipBatch batchOf(String shipCode, String createUser)
        {
            ShipBatch batch = new ShipBatch();
            batch.setShopCode(SHOP);
            batch.setShipCode(shipCode);
            batch.setOrderCode("SO001");
            batch.setCreateUser(createUser);
            batch.setStatus(ShipBatch.STATUS_PENDING);
            return batch;
        }

        private ShipBatch captureQuery()
        {
            ArgumentCaptor<ShipBatch> captor = ArgumentCaptor.forClass(ShipBatch.class);
            verify(batchMapper).selectList(captor.capture(), anyString());
            return captor.getValue();
        }
    }

    // ==================================================================
    // 销售退货（共享的 DocumentMain）
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 销售退货
    {
        @Mock private DocumentMainMapper documentMainMapper;
        @Mock private DocumentDetailMapper documentDetailMapper;
        @Mock private IDocumentService documentService;

        @InjectMocks private SalesReturnServiceImpl service;

        @Test
        void 业务员只看得到自己建的退货单()
        {
            login(ME, "us100");
            lenient().when(documentMainMapper.selectDocumentList(any(DocumentMain.class)))
                    .thenReturn(Collections.emptyList());

            service.selectReturnList(SHOP, null, null, null, SUFFIX);

            assertEquals(ME, captureQuery().getOwnerScope());
        }

        @Test
        void 有查看全部权限就不加归属条件()
        {
            login(ME, "us100", SalesReturnServiceImpl.PERMI_VIEW_ALL);
            lenient().when(documentMainMapper.selectDocumentList(any(DocumentMain.class)))
                    .thenReturn(Collections.emptyList());

            service.selectReturnList(SHOP, null, null, null, SUFFIX);

            assertNull(captureQuery().getOwnerScope());
        }

        @Test
        void 详情打不开别人的退货单()
        {
            login(ME, "us100");
            when(documentMainMapper.selectDocumentByCode(SHOP, "TH002", SUFFIX))
                    .thenReturn(returnOf("TH002", OTHER));

            assertThrows(ServiceException.class,
                    () -> service.selectReturnDetail(SHOP, "TH002", SUFFIX));
        }

        /**
         * ⚠ 提交＝审核即过账，是仓库/入库员做的，不认归属。
         *
         * <p>认了的话业务员建的退货单没人审得了，货退回来入不了库。</p>
         */
        @Test
        void 入库员提交审核不认归属()
        {
            login("rukuyuan", "us300");   // 入库员：没有 all，也不是创建人
            DocumentMain doc = returnOf("TH002", OTHER);
            when(documentMainMapper.selectDocumentByCode(SHOP, "TH002", SUFFIX)).thenReturn(doc);
            when(documentDetailMapper.selectDetailsByDocCode(SHOP, "TH002", SUFFIX))
                    .thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> service.submitReturn(SHOP, "TH002", SUFFIX, "rukuyuan"));
            verify(documentService).confirmDocument(SHOP, "TH002", SUFFIX, true, "rukuyuan");
        }

        private DocumentMain returnOf(String docCode, String createUser)
        {
            DocumentMain doc = new DocumentMain();
            doc.setShopCode(SHOP);
            doc.setDocCode(docCode);
            doc.setDocType(InventoryDocumentTypes.SALES_RETURN_INBOUND);
            doc.setCreateUser(createUser);
            doc.setConfirmState("待审核");
            return doc;
        }

        private DocumentMain captureQuery()
        {
            ArgumentCaptor<DocumentMain> captor = ArgumentCaptor.forClass(DocumentMain.class);
            verify(documentMainMapper).selectDocumentList(captor.capture());
            return captor.getValue();
        }
    }

    // ==================================================================
    // 销售补发
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 销售补发
    {
        @Mock private SalesReshipMapper reshipMapper;
        @Mock private SalesReshipItemMapper itemMapper;

        @InjectMocks private SalesReshipServiceImpl service;

        @Test
        void 业务员只看得到自己建的补发单()
        {
            login(ME, "us100");
            lenient().when(reshipMapper.selectList(any(SalesReship.class)))
                    .thenReturn(Collections.emptyList());

            service.selectList(new SalesReship());

            assertEquals(ME, captureQuery().getOwnerScope());
        }

        @Test
        void 有查看全部权限就不加归属条件()
        {
            login(ME, "us100", SalesReshipServiceImpl.PERMI_VIEW_ALL);
            lenient().when(reshipMapper.selectList(any(SalesReship.class)))
                    .thenReturn(Collections.emptyList());

            service.selectList(new SalesReship());

            assertNull(captureQuery().getOwnerScope());
        }

        @Test
        void 详情打不开别人的补发单()
        {
            login(ME, "us100");
            when(reshipMapper.selectByCode(SHOP, "BF002")).thenReturn(reshipOf("BF002", OTHER));

            assertThrows(ServiceException.class, () -> service.selectDetail(SHOP, "BF002"));
        }

        @Test
        void 详情打得开自己的补发单()
        {
            login(ME, "us100");
            when(reshipMapper.selectByCode(SHOP, "BF001")).thenReturn(reshipOf("BF001", ME));
            when(itemMapper.selectByReshipCode(SHOP, "BF001")).thenReturn(Collections.emptyList());

            assertNotNull(service.selectDetail(SHOP, "BF001"));
        }

        private SalesReship reshipOf(String reshipCode, String createUser)
        {
            SalesReship reship = new SalesReship();
            reship.setShopCode(SHOP);
            reship.setReshipCode(reshipCode);
            reship.setCreateUser(createUser);
            reship.setStatus(SalesReship.STATUS_PENDING);
            return reship;
        }

        private SalesReship captureQuery()
        {
            ArgumentCaptor<SalesReship> captor = ArgumentCaptor.forClass(SalesReship.class);
            verify(reshipMapper).selectList(captor.capture());
            return captor.getValue();
        }
    }
}
