package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.PurchaseReceiveItem;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.mapper.InventoryGoodsCategoryMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.OrderPlatformMapper;
import com.ruoyi.system.mapper.ProductionBomItemMapper;
import com.ruoyi.system.mapper.ProductionBomMapper;
import com.ruoyi.system.mapper.ProductionMaterialMapper;
import com.ruoyi.system.mapper.ProductionOrderDetailMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.mapper.PurchaseReceiveItemMapper;
import com.ruoyi.system.mapper.PurchaseReceiveMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.service.IBuyerService;

/**
 * 「看不见」不能变成「改没了」—— 四个改单入口的金额守卫。
 *
 * <p>这是整套金额可见性里唯一会<b>动数据</b>的一环，也是唯一一处出错不会一眼看出来的地方：
 * 价格被清零之后单子还在、界面还正常，只有对账时才发现钱没了。
 * 见 {@code docs/金额按岗位可见.md} 第 6.1 节。</p>
 *
 * <p>每个入口两条用例：无权的人改单不能改动金额；有权的人照常改得动。
 * 再加一条无登录上下文的 —— 读侧遮蔽、写侧照收，两边默认值相反（第 6.2 节）。</p>
 */
@ExtendWith(MockitoExtension.class)
class PriceWriteGuardTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202609";

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    /** 登录成一个只有这些权限的人；不传权限就是仓库那种两样都看不到的岗位。 */
    private static void login(String userCode, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserName(userCode);
        user.setUserCode(userCode);
        LoginUser loginUser = new LoginUser(user, Set.of(permissions));
        loginUser.setUserCode(userCode);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    // ==================================================================
    // 采购订单：传 null 会当场报「采购单价不能为空」，运费会被 coalesce 成 0
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 采购订单改单
    {
        @Mock private PurchaseOrderMapper orderMapper;
        @Mock private PurchaseOrderItemMapper itemMapper;
        @Mock private InventoryGoodsMapper goodsMapper;
        @Mock private InventoryDocumentCodeGenerator codeGenerator;
        @Mock private RefundWindowResolver refundWindowResolver;

        @InjectMocks private PurchaseOrderServiceImpl service;

        @Test
        void 无权看成本的人改数量_单价与预估运费沿用原值()
        {
            login("us102");
            stubExisting();

            service.updatePurchaseOrder(command(null, null));

            assertEquals(new BigDecimal("5.00"), captureItems().get(0).getUnitPrice(),
                    "单价被前端回传的 null 冲掉了");
            assertEquals(new BigDecimal("88.00"), captureOrder().getEstimatedFreight(),
                    "预估运费被 coalesce 成 0 了");
        }

        @Test
        void 有权看成本的人改得动单价与运费()
        {
            login("us101", "price:cost");
            stubExisting();

            service.updatePurchaseOrder(command(new BigDecimal("9.00"), new BigDecimal("120")));

            assertEquals(new BigDecimal("9.00"), captureItems().get(0).getUnitPrice());
            assertEquals(new BigDecimal("120.00"), captureOrder().getEstimatedFreight());
        }

        /** 没有登录上下文（内部调用、定时任务）：写侧照收，不能静默丢掉 */
        @Test
        void 无登录上下文照收传入的值()
        {
            SecurityContextHolder.clearContext();
            stubExisting();

            service.updatePurchaseOrder(command(new BigDecimal("9.00"), new BigDecimal("120")));

            assertEquals(new BigDecimal("9.00"), captureItems().get(0).getUnitPrice());
        }

        private void stubExisting()
        {
            PurchaseOrder existing = new PurchaseOrder();
            existing.setId(1L);
            existing.setShopCode(SHOP);
            existing.setPurchaseCode("CG001");
            existing.setStatus("draft");
            existing.setFreightType("collect");
            existing.setEstimatedFreight(new BigDecimal("88.00"));
            when(orderMapper.selectPurchaseOrderById(SHOP, 1L)).thenReturn(existing);

            PurchaseOrderItem original = new PurchaseOrderItem();
            original.setId(10L);
            original.setDetailCode("CGD001");
            original.setGoodsCode("G1");
            original.setQty(new BigDecimal("2"));
            original.setUnitPrice(new BigDecimal("5.00"));
            when(itemMapper.selectItemsByPurchaseCode(SHOP, "CG001"))
                    .thenReturn(new java.util.ArrayList<>(Arrays.asList(original)));

            InventoryGoods goods = new InventoryGoods();
            goods.setGoodsCode("G1");
            goods.setGoodsName("可乐");
            goods.setBatchStrategy("none");
            lenient().when(goodsMapper.selectGoodsByCode(SHOP, "G1")).thenReturn(goods);

            when(orderMapper.updatePurchaseOrder(any())).thenReturn(1);
        }

        /** 前端回传的表单：无权用户拿到的价格是 null，原样回来 */
        private PurchaseOrder command(BigDecimal unitPrice, BigDecimal estimatedFreight)
        {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setId(10L);
            item.setGoodsCode("G1");
            // 改的是数量，价格只是被表单带回来
            item.setQty(new BigDecimal("3"));
            item.setUnitPrice(unitPrice);

            PurchaseOrder command = new PurchaseOrder();
            command.setId(1L);
            command.setShopCode(SHOP);
            command.setFreightType("collect");
            command.setEstimatedFreight(estimatedFreight);
            command.setItems(new java.util.ArrayList<>(Arrays.asList(item)));
            return command;
        }

        @SuppressWarnings("unchecked")
        private List<PurchaseOrderItem> captureItems()
        {
            ArgumentCaptor<List<PurchaseOrderItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(itemMapper).batchInsertPurchaseOrderItem(captor.capture());
            return captor.getValue();
        }

        private PurchaseOrder captureOrder()
        {
            ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
            verify(orderMapper).updatePurchaseOrder(captor.capture());
            return captor.getValue();
        }
    }

    // ==================================================================
    // 采购到货单：运费传 null 会被 nvl 成 0，还会按差额把采购订单上的实付冲掉
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 采购到货单改单
    {
        @Mock private PurchaseReceiveMapper receiveMapper;
        @Mock private PurchaseReceiveItemMapper itemMapper;
        @Mock private PurchaseOrderMapper purchaseMapper;
        @Mock private PurchaseOrderItemMapper purchaseItemMapper;
        @Mock private InventoryDocumentCodeGenerator codeGenerator;
        @Mock private InventoryWarehouseMapper warehouseMapper;
        @Mock private MonthlyTableInitializer monthlyTables;

        @InjectMocks private PurchaseReceiveServiceImpl service;

        @Test
        void 无权看成本的人改数量_运费沿用原值()
        {
            login("us102");
            stubExisting();

            PurchaseReceive command = command(null);
            service.updateDraft(SHOP, command, SUFFIX, "tester");

            assertEquals(new BigDecimal("50.00"), command.getFreightAmount(), "实付运费被清零了");
            // 运费没变，就不该去动采购订单上的实付运费
            verify(purchaseMapper, never()).increaseActualFreight(anyString(), anyString(), any());
        }

        @Test
        void 有权看成本的人改得动运费()
        {
            login("us101", "price:cost");
            stubExisting();

            PurchaseReceive command = command(new BigDecimal("70"));
            service.updateDraft(SHOP, command, SUFFIX, "tester");

            assertEquals(new BigDecimal("70.00"), command.getFreightAmount());
        }


        private void stubExisting()
        {
            PurchaseReceive old = new PurchaseReceive();
            old.setShopCode(SHOP);
            old.setReceiveCode("FH001");
            old.setPurchaseCode("CG001");
            old.setStatus("待入库");
            old.setFreightType("prepaid");
            old.setFreightAmount(new BigDecimal("50.00"));
            old.setEstFreightAmount(BigDecimal.ZERO);
            when(receiveMapper.selectByCodeForUpdate(SHOP, "FH001", SUFFIX)).thenReturn(old);
            // requireAllLinesPending：没有已入库的行
            when(itemMapper.selectByReceiveCode(SHOP, "FH001", SUFFIX)).thenReturn(Collections.emptyList());

            PurchaseOrder order = new PurchaseOrder();
            order.setShopCode(SHOP);
            order.setPurchaseCode("CG001");
            order.setFreightType("prepaid");
            when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, "CG001")).thenReturn(order);

            PurchaseOrderItem source = new PurchaseOrderItem();
            source.setDetailCode("CGD001");
            source.setGoodsCode("G1");
            source.setQty(new BigDecimal("10"));
            source.setUnitPrice(new BigDecimal("5.00"));
            source.setReceivedQty(BigDecimal.ZERO);
            when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, "CG001"))
                    .thenReturn(Arrays.asList(source));

            when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Arrays.asList(SUFFIX));
            when(receiveMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);
        }

        private PurchaseReceive command(BigDecimal freightAmount)
        {
            PurchaseReceiveItem item = new PurchaseReceiveItem();
            item.setPurchaseDetailCode("CGD001");
            item.setGoodsCode("G1");
            item.setQty(new BigDecimal("3"));

            PurchaseReceive command = new PurchaseReceive();
            command.setReceiveCode("FH001");
            command.setFreightType("prepaid");
            command.setFreightAmount(freightAmount);
            command.setItems(new java.util.ArrayList<>(Arrays.asList(item)));
            return command;
        }
    }

    // ==================================================================
    // 销售订单草稿：单价传 null 被 nvl 成 0，整单货款归零
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 销售订单改草稿
    {
        @Mock private SalesOrderMapper orderMapper;
        @Mock private SalesOrderItemMapper itemMapper;
        @Mock private InventoryGoodsMapper goodsMapper;
        @Mock private OrderPlatformMapper platformMapper;
        @Mock private InventoryDocumentCodeGenerator codeGenerator;
        @Mock private IBuyerService buyerService;
        @Mock private InventoryStockMapper stockMapper;

        @InjectMocks private SalesOrderServiceImpl service;

        @Test
        void 无权看销售价的人改收件信息_单价与运费沿用原值()
        {
            login("us101", "price:cost", "order:sales:all");
            stubExisting();

            SalesOrder command = command(null, null);
            service.updateDraft(SHOP, command, SUFFIX, "tester");

            assertEquals(new BigDecimal("8.00"), captureItems().get(0).getPrice(), "单价被清零了");
            assertEquals(new BigDecimal("30.00"), command.getFreightAmount(), "运费被清零了");
            assertEquals(new BigDecimal("54.00"), command.getTotalAmount(), "3 × 8.00 + 30.00");
        }

        @Test
        void 有权看销售价的人改得动单价与运费()
        {
            login("us100", "price:sale", "order:sales:all");
            stubExisting();

            SalesOrder command = command(new BigDecimal("10.00"), new BigDecimal("40.00"));
            service.updateDraft(SHOP, command, SUFFIX, "tester");

            assertEquals(new BigDecimal("10.00"), captureItems().get(0).getPrice());
            assertEquals(new BigDecimal("70.00"), command.getTotalAmount(), "3 × 10.00 + 40.00");
        }

        private void stubExisting()
        {
            SalesOrder old = new SalesOrder();
            old.setShopCode(SHOP);
            old.setOrderCode("SO001");
            old.setOrderState(SalesOrder.STATE_DRAFT);
            old.setCurrency("CNY");
            old.setFreightAmount(new BigDecimal("30.00"));
            old.setPaidAmount(BigDecimal.ZERO);
            old.setPlatCode("P1");
            old.setShopStoreCode("S1");
            when(orderMapper.selectByCodeForUpdate(SHOP, "SO001", SUFFIX)).thenReturn(old);

            OrderPlatform platform = new OrderPlatform();
            platform.setPlatformCode("P1");
            platform.setPlatformName("天猫");
            platform.setIsActive(1);
            when(platformMapper.selectPlatformByCode(SHOP, "P1")).thenReturn(platform);

            OrderPlatformStore store = new OrderPlatformStore();
            store.setStoreCode("S1");
            store.setStoreName("旗舰店");
            store.setIsActive(1);
            when(platformMapper.selectStoreByCode(SHOP, "P1", "S1")).thenReturn(store);

            SalesOrderItem original = new SalesOrderItem();
            original.setDetailCode("SOD001");
            original.setGoodsCode("G1");
            original.setQty(new BigDecimal("2"));
            original.setPrice(new BigDecimal("8.00"));
            original.setAmount(new BigDecimal("16.00"));
            when(itemMapper.selectByOrderCode(SHOP, "SO001", SUFFIX)).thenReturn(Arrays.asList(original));

            InventoryGoods goods = new InventoryGoods();
            goods.setGoodsCode("G1");
            goods.setGoodsName("可乐");
            goods.setSpec("330ml");
            goods.setUnit("瓶");
            lenient().when(goodsMapper.selectGoodsByCode(SHOP, "G1")).thenReturn(goods);

            when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);
        }

        private SalesOrder command(BigDecimal price, BigDecimal freightAmount)
        {
            SalesOrderItem item = new SalesOrderItem();
            item.setGoodsCode("G1");
            item.setQty(new BigDecimal("3"));
            item.setPrice(price);

            SalesOrder command = new SalesOrder();
            command.setOrderCode("SO001");
            command.setPlatCode("P1");
            command.setShopStoreCode("S1");
            command.setCurrency("CNY");
            // 上门提货：收件人、电话、地址都不强求，专心验金额
            command.setReceiveType(SalesOrder.RECEIVE_TYPE_PICKUP);
            command.setFreightAmount(freightAmount);
            command.setItems(new java.util.ArrayList<>(Arrays.asList(item)));
            return command;
        }

        @SuppressWarnings("unchecked")
        private List<SalesOrderItem> captureItems()
        {
            ArgumentCaptor<List<SalesOrderItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(itemMapper).batchInsert(captor.capture(), eq(SUFFIX));
            return captor.getValue();
        }
    }

    // ==================================================================
    // 商品档案：updateGoods 无条件写 standardprice，校验又放过 null
    // ==================================================================

    @Nested
    @ExtendWith(MockitoExtension.class)
    class 商品参考价
    {
        @Mock private InventoryGoodsMapper goodsMapper;
        @Mock private InventoryStockMapper stockMapper;
        @Mock private InventoryGoodsCategoryMapper categoryMapper;
        @Mock private ProductionBomMapper bomMapper;
        @Mock private ProductionBomItemMapper bomItemMapper;
        @Mock private ProductionOrderDetailMapper productionDetailMapper;
        @Mock private ProductionMaterialMapper productionMaterialMapper;
        @Mock private StaffNameResolver staffNameResolver;

        @InjectMocks private InventoryGoodsServiceImpl service;

        @Test
        void 无权看销售价的人改库存预警量_参考价沿用原值()
        {
            login("us101", "price:cost");
            stubExisting();

            service.updateGoods(command(null));

            assertEquals(new BigDecimal("12.00"), capture().getStandardPrice(), "参考价被悄悄抹掉了");
        }

        @Test
        void 有权看销售价的人改得动参考价()
        {
            login("us100", "price:sale");
            stubExisting();

            service.updateGoods(command(new BigDecimal("15.00")));

            assertEquals(new BigDecimal("15.00"), capture().getStandardPrice());
        }

        private void stubExisting()
        {
            InventoryGoods existing = new InventoryGoods();
            existing.setShopCode(SHOP);
            existing.setGoodsCode("G1");
            existing.setGoodsName("可乐");
            existing.setStandardPrice(new BigDecimal("12.00"));
            existing.setUsedFlag(0);
            existing.setCategory("");
            when(goodsMapper.selectGoodsByCode(SHOP, "G1")).thenReturn(existing);
            // isGoodsUsed 的第二道判据：没有库存
            when(stockMapper.selectStockList(any(InventoryStock.class))).thenReturn(Collections.emptyList());
            when(goodsMapper.updateGoods(any())).thenReturn(1);
        }

        private InventoryGoods command(BigDecimal standardPrice)
        {
            InventoryGoods goods = new InventoryGoods();
            goods.setShopCode(SHOP);
            goods.setGoodsCode("G1");
            goods.setGoodsName("可乐");
            goods.setBatchStrategy("none");
            goods.setCategory("");
            // 改的是库存预警量，参考价只是被表单带回来
            goods.setMinStockQty(new BigDecimal("10"));
            goods.setStandardPrice(standardPrice);
            return goods;
        }

        private InventoryGoods capture()
        {
            ArgumentCaptor<InventoryGoods> captor = ArgumentCaptor.forClass(InventoryGoods.class);
            verify(goodsMapper).updateGoods(captor.capture());
            return captor.getValue();
        }
    }
}
