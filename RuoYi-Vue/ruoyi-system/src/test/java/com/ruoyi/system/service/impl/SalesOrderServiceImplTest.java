package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.fastjson2.JSON;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderDescItem;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.OrderPlatformMapper;
import com.ruoyi.system.mapper.ProductionOrderMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.service.IInventoryBatchService;

/**
 * 销售订单的建单校验、确认预占、状态派生与回款口径测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202608";
    private static final String ORDER = "SO20260819001";
    private static final String DETAIL = "SOMX20260819001";
    private static final String USER = "tester";
    private static final String PLAT = "PLAT01";
    private static final String STORE = "S01";

    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper itemMapper;
    @Mock private InventoryGoodsMapper goodsMapper;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;
    /** 多明细预占前的整批加锁；同包不用 import */
    /** 退款时效窗口；同包不用 import */
    @Mock private RefundWindowResolver refundWindowResolver;
    /** 撤审要问它有没有在途批次；同包不用 import */
    @Mock private ShipBatchPendingQtyResolver pendingQtyResolver;
    /** 建单/改单要按平台与店铺主数据校验并回填快照 */
    @Mock private OrderPlatformMapper platformMapper;
    /** 撤审要问它有没有按本订单开的在制工单，默认 0 张 */
    @Mock private ProductionOrderMapper productionOrderMapper;
    /** 定制件的专属批次：算「备货中」要读，订单终止要松开 */
    @Mock private IInventoryBatchService batchService;
    /** 审核判「走现货的明细库存够不够」要读它。不打桩就是查不到库存行 = 可用量 0。 */
    @Mock private InventoryStockMapper stockMapper;
    /** staffcode 存的是登录账号，列表/详情要显示姓名，经它换 */
    @Mock private StaffNameResolver staffNameResolver;

    @InjectMocks private SalesOrderServiceImpl service;

    private SalesOrder command;

    @BeforeEach
    void setUp()
    {
        command = new SalesOrder();
        command.setShopStoreCode(STORE);
        command.setShopStoreName("测试店铺");
        command.setPlatCode(PLAT);
        command.setReceivePerson("张三");
        command.setReceiveAddress("测试地址");
        command.setReceiveTelephone("13800000000");
        command.setItems(new ArrayList<>(Collections.singletonList(item("G001", "10", "12.5"))));
    }

    /**
     * 让平台与店铺主数据查得到。
     *
     * <p>建单和改单在 validateHeader 之后紧接着走 applyPlatformStore，查不到就抛
     * 「所选平台不存在或已停用」，所以凡是要走过表头校验的用例都得先调一次。
     * 不放进 {@code @BeforeEach}：只做表头拒绝的用例根本走不到这一步，
     * 提前打桩会被严格模式判成 UnnecessaryStubbing。</p>
     */
    private void stubPlatformStore()
    {
        stubPlatformStore(1, 1);
    }

    /** @param storeActive 传 0 表示店铺已停用，用来验「停用只挡新选中，不挡保持原样」 */
    private void stubPlatformStore(int platformActive, int storeActive)
    {
        OrderPlatform platform = new OrderPlatform();
        platform.setPlatformCode(PLAT);
        platform.setPlatformName("测试平台");
        platform.setIsActive(platformActive);

        OrderPlatformStore store = new OrderPlatformStore();
        store.setPlatformCode(PLAT);
        store.setStoreCode(STORE);
        store.setStoreName("测试店铺");
        store.setIsActive(storeActive);

        when(platformMapper.selectPlatformByCode(SHOP, PLAT)).thenReturn(platform);
        when(platformMapper.selectStoreByCode(SHOP, PLAT, STORE)).thenReturn(store);
    }

    private SalesOrderItem item(String goodsCode, String qty, String price)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setGoodsCode(goodsCode);
        item.setQty(new BigDecimal(qty));
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private InventoryGoods goods(String code, String name)
    {
        InventoryGoods g = new InventoryGoods();
        g.setGoodsCode(code);
        g.setGoodsName(name);
        g.setSpec("STD");
        g.setUnit("个");
        return g;
    }

    /** 建单：金额按明细汇总重算，不接受前端给的合计；初始状态是草稿。 */
    @Test
    void createOrderRecalculatesAmountsFromItems()
    {
        stubPlatformStore();
        command.setFreightAmount(new BigDecimal("15"));
        command.setTotalAmount(new BigDecimal("99999"));   // 前端瞎给的合计，应被覆盖
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals(SalesOrder.STATE_DRAFT, created.getOrderState());
        assertEquals(0, created.getGoodsAmount().compareTo(new BigDecimal("125.00")), "10 × 12.5");
        assertEquals(0, created.getTotalAmount().compareTo(new BigDecimal("140.00")), "货款 125 + 运费 15");
        assertEquals(0, created.getItems().get(0).getAmount().compareTo(new BigDecimal("125.00")));
        assertEquals("CNY", created.getCurrency());
    }

    /**
     * 归属业务员一律取<b>当前登录账号</b>，前端塞进来的值不算。
     *
     * <p>原来是「前端没给才兜底成登录名」，等于谁都能把单挂到别人名下。这个字段以后要
     * 拿来算提成，是笔钱，不能由请求体决定。真要代下单或转单，得另开带权限、留痕的动作。</p>
     */
    @Test
    void createForcesStaffCodeToLoginUser()
    {
        stubPlatformStore();
        command.setStaffCode("someone-else");
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals(USER, created.getStaffCode());
    }

    /** 「提交审核」按钮：建单直接落待审核，校验与建单一样，不经过草稿、不多一条状态 UPDATE。 */
    @Test
    void createAndSubmitLandsInPendingState()
    {
        stubPlatformStore();
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);

        SalesOrder created = service.createAndSubmit(SHOP, command, USER);

        assertEquals(SalesOrder.STATE_PENDING, created.getOrderState());
        assertEquals(USER, created.getStaffCode());
        // 落表月份是建单时刻的当月，不是测试里那个固定的 SUFFIX，所以这里不比它
        verify(orderMapper).insert(argThat(o -> SalesOrder.STATE_PENDING.equals(o.getOrderState())), anyString());
        verify(orderMapper, never()).updateStateWithReason(anyString(), anyString(), anyString(), anyString(),
                anyList(), any(), any());
    }

    /** 空明细的单连草稿都不该落，直接提交更不能：整单不落库，而不是留一张空草稿。 */
    @Test
    void createAndSubmitRejectsEmptyItemsWithoutInserting()
    {
        stubPlatformStore();
        command.setItems(new ArrayList<>());

        assertThrows(ServiceException.class, () -> service.createAndSubmit(SHOP, command, USER));

        verify(orderMapper, never()).insert(any(), anyString());
    }

    /** 改单并提交只认草稿 / 已驳回：已备货的单在改单那一半就被挡，提交不会发生。 */
    @Test
    void updateAndSubmitRejectsConfirmedOrder()
    {
        command.setOrderCode(ORDER);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateAndSubmit(SHOP, command, SUFFIX, USER));

        assertTrue(error.getMessage().contains("撤审"));
        verify(orderMapper, never()).updateStateWithReason(anyString(), anyString(), anyString(), anyString(),
                anyList(), any(), any());
    }

    /** 同一商品重复开行会让预占与发货核销失去唯一定位，建单就要挡掉。 */
    @Test
    void createOrderRejectsDuplicateGoods()
    {
        stubPlatformStore();
        command.getItems().add(item("G001", "5", "12.5"));
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().contains("重复"));
    }

    @Test
    void createOrderRejectsNonPositiveQty()
    {
        stubPlatformStore();
        command.getItems().get(0).setQty(BigDecimal.ZERO);
        ServiceException error = assertThrows(ServiceException.class, () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().contains("数量必须大于"));
    }

    private SalesOrder existing(String state)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(ORDER);
        order.setOrderState(state);
        order.setTotalAmount(new BigDecimal("100"));
        order.setPaidAmount(BigDecimal.ZERO);
        return order;
    }

    /**
     * 列表把 staffcode（建单时的登录账号）换成员工姓名。
     *
     * <p>库里 staffname 那一列不参与——它历来没写过值，写了也会在账号改名后跟 sys_user 漂移。
     * 这里连带守着「一次 in 查完」：解析器只该被调一次，不能每行查一次。</p>
     */
    @Test
    void listResolvesStaffNameFromLoginAccount()
    {
        SalesOrder first = existing(SalesOrder.STATE_READY);
        first.setStaffCode("zhangsan");
        SalesOrder second = existing(SalesOrder.STATE_READY);
        second.setStaffCode("lisi");
        Map<String, String> nameByLogin = new HashMap<>();
        nameByLogin.put("zhangsan", "张三");
        nameByLogin.put("lisi", "李四");
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(Arrays.asList(first, second));
        when(staffNameResolver.nickNamesByLogin(anyList())).thenReturn(nameByLogin);

        List<SalesOrder> list = service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals("张三", list.get(0).getStaffName());
        assertEquals("李四", list.get(1).getStaffName());
        verify(staffNameResolver).nickNamesByLogin(anyList());
    }

    /** 账号查不到（人已离职、账号删了）就退回显示账号本身，不能留空——留空看着像没人下单。 */
    @Test
    void listFallsBackToLoginWhenStaffNameMissing()
    {
        SalesOrder row = existing(SalesOrder.STATE_READY);
        row.setStaffCode("ghost");
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(Collections.singletonList(row));
        when(staffNameResolver.nickNamesByLogin(anyList())).thenReturn(Collections.emptyMap());

        List<SalesOrder> list = service.selectOrderList(new SalesOrder(), SUFFIX);

        assertEquals("ghost", list.get(0).getStaffName());
    }

    /**
     * 按「业务员名称」搜：姓名先反查成一批登录账号，再拿账号去月表里筛。
     *
     * <p>姓名不落订单表，只能这么两步走。一个姓氏可能对上好几个人，所以是 in 而不是等号。</p>
     */
    @Test
    void listResolvesStaffNameIntoAccountFilter()
    {
        when(staffNameResolver.loginsByNickNameLike("李")).thenReturn(Arrays.asList("lixiao", "lisi"));
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(new ArrayList<>());
        SalesOrder query = new SalesOrder();
        query.setStaffName("李");

        service.selectOrderList(query, SUFFIX);

        ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).selectList(captor.capture(), eq(SUFFIX));
        assertEquals(Arrays.asList("lixiao", "lisi"), captor.getValue().getStaffCodes());
    }

    /**
     * 名字填了但一个员工都没匹配上：留<b>空集合</b>，不是 null。
     *
     * <p>mapper 靠这个空集合落成 {@code 1 = 0}。清成 null 就等于没有条件，会把全月的订单
     * 都列出来——搜一个不存在的名字却搜出全部，是最误导人的一种「没报错」。</p>
     */
    @Test
    void listKeepsEmptyAccountFilterWhenNobodyMatchesName()
    {
        when(staffNameResolver.loginsByNickNameLike("查无此人")).thenReturn(Collections.emptyList());
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(new ArrayList<>());
        SalesOrder query = new SalesOrder();
        query.setStaffName("查无此人");

        service.selectOrderList(query, SUFFIX);

        ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).selectList(captor.capture(), eq(SUFFIX));
        assertTrue(captor.getValue().getStaffCodes().isEmpty());
    }

    /**
     * staffCodes 是内部查询载体，前端塞进来的一律作废。
     *
     * <p>控制器的 query 是从请求参数绑出来的，这个字段也绑得进去。不清掉就会出现
     * 「界面上没填任何业务员条件，结果被 URL 参数筛掉了一半单子」。</p>
     */
    @Test
    void listIgnoresClientSuppliedAccountFilter()
    {
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(new ArrayList<>());
        SalesOrder query = new SalesOrder();
        query.setStaffCodes(Collections.singletonList("someone-else"));

        service.selectOrderList(query, SUFFIX);

        ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).selectList(captor.capture(), eq(SUFFIX));
        assertNull(captor.getValue().getStaffCodes());
        verifyNoInteractions(staffNameResolver);
    }

    /** 空列表不去查解析器：拼出空的 in() 白跑一次 SQL。 */
    @Test
    void listSkipsStaffLookupWhenEmpty()
    {
        when(orderMapper.selectList(any(), eq(SUFFIX))).thenReturn(new ArrayList<>());

        service.selectOrderList(new SalesOrder(), SUFFIX);

        verifyNoInteractions(staffNameResolver);
    }

    private SalesOrderItem storedItem(String qty, String shipped)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setShopCode(SHOP);
        item.setOrderCode(ORDER);
        item.setDetailCode(DETAIL);
        item.setGoodsCode("G001");
        item.setGoodsName("测试商品");
        item.setQty(new BigDecimal(qty));
        item.setShippedQty(new BigDecimal(shipped));
        return item;
    }

    /** 一条绑给 DETAIL 的专属批次，剩余量 remain。 */
    private InventoryBatch ownedBatch(String remain)
    {
        InventoryBatch batch = new InventoryBatch();
        batch.setShopCode(SHOP);
        batch.setBatchCode("PC202608001");
        batch.setGoodsCode("G001");
        batch.setRemainQty(new BigDecimal(remain));
        batch.setOwnerOrderCode(ORDER);
        batch.setOwnerDetailCode(DETAIL);
        return batch;
    }

    /**
     * 让 G001 在库里有 physical 这么多可用量（无锁定、无预占）。
     *
     * <p>不调这个方法就等于查不到库存行，可用量 0——走现货的明细一律落备货中。</p>
     */
    private void stubAvailableStock(String physical)
    {
        InventoryStock stock = new InventoryStock();
        stock.setShopCode(SHOP);
        stock.setGoodsCode("G001");
        stock.setWareCode("WH1");
        stock.setPhysicalQty(new BigDecimal(physical));
        stock.setLockedQty(BigDecimal.ZERO);
        when(stockMapper.selectStockList(any())).thenReturn(Collections.singletonList(stock));
    }

    /** 审核通过一律不占库存——这是取消订单预占后最要紧的一条。 */
    @Test
    void confirmNeverReservesStock()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        stubAvailableStock("50");
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

    }

    /** 走现货、库存够本行未发量 → 已确认，业务员可以去建发货批次。 */
    @Test
    void confirmGoesToConfirmedWhenStockCoversPendingQty()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        stubAvailableStock("10");
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING);
    }

    /** 走现货但库存差一个 → 备货中。差一点也是不够，不做四舍五入。 */
    @Test
    void confirmGoesToPreparingWhenStockShortByOne()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        stubAvailableStock("9");
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING);
    }

    /** 走现货但一行库存都查不到 → 备货中。 */
    @Test
    void confirmGoesToPreparingWhenNoStockRowAtAll()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(stockMapper.selectStockList(any())).thenReturn(Collections.emptyList());
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING);
    }

    /**
     * 定制件不看通用库存，<b>根本不去查</b>。
     *
     * <p>货是专门为这张单做的，仓库里同款现货有多少都不影响判断——真去看了反而会因为
     * 有别人的货就说成「已备货」。</p>
     */
    @Test
    void confirmDoesNotCheckGeneralStockForCustomMadeLine()
    {
        SalesOrderItem item = storedItem("10", "0");
        item.setCustomMade(Boolean.TRUE);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(Collections.singletonList(item));
        when(batchService.selectBatchesByOwner(SHOP, DETAIL)).thenReturn(Collections.emptyList());
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING);
        verifyNoInteractions(stockMapper);
    }

    /** 一条明细没着落就拖累整单：普通件现货够，定制件的专属批次没到，整单还是备货中。 */
    @Test
    void confirmGoesToPreparingWhenAnySingleLineIsAwaiting()
    {
        SalesOrderItem ready = storedItem("10", "0");
        SalesOrderItem awaiting = storedItem("5", "0");
        awaiting.setDetailCode("DETAIL2");
        awaiting.setCustomMade(Boolean.TRUE);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Arrays.asList(ready, awaiting));
        stubAvailableStock("100");
        when(batchService.selectBatchesByOwner(SHOP, "DETAIL2")).thenReturn(Collections.emptyList());
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING);
    }

    /** 定制件还没做出来（专属批次一个都没有）→ 备货中。它不看通用库存，只认为它做的那批。 */
    @Test
    void confirmGoesToPreparingWhenCustomBatchNotReady()
    {
        SalesOrderItem item = storedItem("10", "0");
        item.setCustomMade(Boolean.TRUE);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(Collections.singletonList(item));
        when(batchService.selectBatchesByOwner(SHOP, DETAIL)).thenReturn(Collections.emptyList());
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_PREPARING, SalesOrder.STATE_PENDING);
    }

    /**
     * 确认订单一行库存都不动：既不预占，也不需要加锁。
     *
     * <p>这条替换了原来的 {@code confirmLocksWholeOrderBeforeReservingAnyLine}。那条断言的是
     * 「整单一次锁完再逐条预占」——{@link OrderStockLockCoordinator} 正是为那个流程写的：
     * 逐条 reserve 各自加锁时，两张明细顺序相反的订单会交叉持锁互等。</p>
     *
     * <p>取消订单预占之后这个死锁场景在确认这一步<b>不存在</b>了：没有 reserve，就没有要
     * 排序的加锁点。协调器本身仍然有用，发货那一步还在调 {@code lockForShip}——
     * 出库单要动的库存行照旧得一次锁完。</p>
     */
    @Test
    void confirmTouchesNeitherLocksNorReservations()
    {
        List<SalesOrderItem> items = Arrays.asList(storedItem("10", "0"), storedItem("5", "0"));
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(items);
        stubAvailableStock("100");
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

    }

    /** 定制件的专属批次已经够了 → 已确认。仓库里同款的通用库存有多少都不影响这个判断。 */
    @Test
    void confirmGoesToConfirmedWhenCustomBatchReady()
    {
        SalesOrderItem item = storedItem("10", "0");
        item.setCustomMade(Boolean.TRUE);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(Collections.singletonList(item));
        when(batchService.selectBatchesByOwner(SHOP, DETAIL)).thenReturn(Collections.singletonList(ownedBatch("10")));
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING))
                .thenReturn(1);

        service.confirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_READY, SalesOrder.STATE_PENDING);
    }

    @Test
    void confirmRejectsNonPendingOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_SHIPPING));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("只有待审核订单可以审核通过"));
    }

    /** 草稿直接点审核是最容易撞上的一种，错误里要把「先提交」说出来。 */
    @Test
    void confirmOnDraftTellsUserToSubmitFirst()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_DRAFT));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("请先提交审核"));
    }

    /** 发出去的货收不回来：取消要拦，并指路到退货与关闭。 */
    @Test
    void cancelRejectsOrderWithShippedGoods()
    {
        // 发货中在状态白名单外就被挡，明细都不用读
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_SHIPPING));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("销售退货"));
        assertTrue(error.getMessage().contains("关闭订单"));
    }

    /**
     * 发货中是 deriveState 算出来的；万一批次刚完成、状态还没重算，已备货的单也可能带着已发量。
     * 明细上的兜底照样拦，并指路到退货与关闭。
     */
    @Test
    void cancelRejectsReadyOrderWhoseItemsWereAlreadyShipped()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "3")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(SHOP, ORDER, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已发出部分货物"));
        verify(orderMapper, never()).updateOrderState(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /** 已关闭是终态，别再让它被取消翻回去。 */
    @Test
    void cancelRejectsClosedOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_CLOSED));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("已关闭"));
    }

    /** 取消只认已备货 / 备货中：已驳回的单该删除，待审核的该撤回，别再留一张已取消的废单。 */
    @Test
    void cancelRejectsRejectedOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_REJECTED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(SHOP, ORDER, SUFFIX, USER));

        assertTrue(error.getMessage().contains("删除"));
        verify(orderMapper, never()).updateOrderState(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /** 取消订单要连预占一起释放，否则那部分可用量永远被吃着。 */
    @Test
    void cancelReleasesReservations()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX, SalesOrder.STATE_CANCELLED, SalesOrder.STATE_READY))
                .thenReturn(1);

        service.cancelOrder(SHOP, ORDER, SUFFIX, USER);

    }

    /**
     * 备货中的两个出口<b>放行</b>：撤审、取消一个都不挡。
     *
     * <p>整条链一件货都不占，「货还没凑齐」只是审核那一刻的快照，不构成终止订单的门槛——
     * 销售把单提给仓库就算交差了。真正要拦的是已经派出去的活儿，那由在途批次
     * （{@code assertNoPendingBatches}）和在制工单（{@code assertNoOpenProductions}）
     * 两道检查负责，另有测试守着。纯粹现货不够的单本来就没什么要收拾的，
     * 从前跟着一起锁死，销售只能干等库存补上，这两个测试就是防止那道门禁被加回来。</p>
     *
     * <p>关闭是另一回事：它只给发过货的单，一件没发的备货中单该走取消，
     * 见 {@link #closeRejectsOrderWithoutShipment}。每个状态只留一个终止动作。</p>
     */
    @Test
    void preparingOrderAllowsCancel()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_PREPARING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(orderMapper.updateOrderState(SHOP, ORDER, SUFFIX,
                SalesOrder.STATE_CANCELLED, SalesOrder.STATE_PREPARING)).thenReturn(1);

        service.cancelOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateOrderState(SHOP, ORDER, SUFFIX,
                SalesOrder.STATE_CANCELLED, SalesOrder.STATE_PREPARING);
    }

    /**
     * 关闭只给发过货的单。备货中一件都没发，该走「取消」——每个状态只留一个终止动作，
     * 接口和界面同一口径（index.vue 的 canClose 只认发货中）。
     */
    @Test
    void closeRejectsOrderWithoutShipment()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_PREPARING));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeOrder(SHOP, ORDER, SUFFIX, "客户弃单", USER));

        assertTrue(error.getMessage().contains("取消"));
        verify(orderMapper, never()).updateStateWithReason(anyString(), anyString(), anyString(), anyString(),
                anyList(), any(), any());
    }

    /** 发过货的单才能关闭：已发部分是既成事实，关闭终止剩余部分。 */
    @Test
    void closeAllowsOrderWithShippedGoods()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_SHIPPING));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "3")));
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_CLOSED),
                anyList(), isNull(), eq("客户弃单"))).thenReturn(1);

        service.closeOrder(SHOP, ORDER, SUFFIX, "客户弃单", USER);

        verify(orderMapper).updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_CLOSED),
                argThat(states -> states.contains(SalesOrder.STATE_SHIPPING)), isNull(), eq("客户弃单"));
    }

    @Test
    void preparingOrderAllowsUnconfirm()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_PREPARING));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null)).thenReturn(Collections.emptyMap());
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_DRAFT),
                anyList(), isNull(), isNull())).thenReturn(1);

        service.unconfirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_DRAFT),
                argThat(states -> states.contains(SalesOrder.STATE_PREPARING)), isNull(), isNull());
        verify(orderMapper).updateShipProgress(SHOP, ORDER, SUFFIX, BigDecimal.ZERO, SalesOrder.STATE_DRAFT);
    }

    /**
     * 车间还在为这张单做货，就不许关闭/取消——做完了没人认领，成本白花。
     * 撤审那条路原本就挡，这里补的是关闭与取消两个新加的挡板。
     */
    @Test
    void closeRejectsOrderWithOpenProductions()
    {
        // 在途的活儿先于「有没有发过货」被拦，明细都不用读
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(productionOrderMapper.countOpenAllocatedToOrder(SHOP, ORDER)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeOrder(SHOP, ORDER, SUFFIX, "客户弃单", USER));
        assertTrue(error.getMessage().contains("生产工单"));
    }

    @Test
    void cancelRejectsOrderWithOpenProductions()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(productionOrderMapper.countOpenAllocatedToOrder(SHOP, ORDER)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("生产工单"));
    }

    /** 撤回：待审核 → 草稿。没占过库存，所以一次释放都不该发生。 */
    @Test
    void withdrawReturnsPendingOrderToDraft()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(orderMapper.updateStateWithReason(SHOP, ORDER, SUFFIX, SalesOrder.STATE_DRAFT,
                Collections.singletonList(SalesOrder.STATE_PENDING), null, null)).thenReturn(1);

        service.withdrawOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateStateWithReason(SHOP, ORDER, SUFFIX, SalesOrder.STATE_DRAFT,
                Collections.singletonList(SalesOrder.STATE_PENDING), null, null);
    }

    /** 撤回只认待审核：已备货的单要退回去得走撤审。 */
    @Test
    void withdrawRejectsNonPendingOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.withdrawOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("只有待审核"));
        verify(orderMapper, never()).updateStateWithReason(anyString(), anyString(), anyString(),
                anyString(), anyList(), any(), any());
    }

    /** 发货比例按数量算，部分发货 → 发货中。 */
    @Test
    void refreshShipProgressComputesPercentAndShippingState()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Arrays.asList(storedItem("10", "6"), storedItem("10", "0")));

        String state = service.refreshShipProgress(SHOP, ORDER, SUFFIX);

        assertEquals(SalesOrder.STATE_SHIPPING, state);
        ArgumentCaptor<BigDecimal> percent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(orderMapper).updateShipProgress(eq(SHOP), eq(ORDER), eq(SUFFIX), percent.capture(),
                eq(SalesOrder.STATE_SHIPPING));
        assertEquals(0, percent.getValue().compareTo(new BigDecimal("30.00")), "6 / 20 = 30%");
    }

    /** 全部发完 → 已完成，比例 100。 */
    @Test
    void refreshShipProgressFinishesWhenFullyShipped()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_SHIPPING));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "10")));

        String state = service.refreshShipProgress(SHOP, ORDER, SUFFIX);

        assertEquals(SalesOrder.STATE_FINISHED, state);
        verify(orderMapper).updateShipProgress(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(new BigDecimal("100.00")),
                eq(SalesOrder.STATE_FINISHED));
    }

    /** 已取消的订单不能被发货进度带回活跃状态。 */
    @Test
    void refreshShipProgressKeepsCancelledState()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_CANCELLED));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));

        assertEquals(SalesOrder.STATE_CANCELLED, service.refreshShipProgress(SHOP, ORDER, SUFFIX));
    }

    /** 回款：支付状态与回款状态同源派生，一起落库，不会漂。 */
    @Test
    void applyPaymentDerivesBothStatusesFromSameAmount()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));

        service.applyPayment(SHOP, ORDER, SUFFIX, new BigDecimal("40"), null);

        verify(orderMapper).applyPayment(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(new BigDecimal("40")),
                eq("部分支付"), eq("部分收款"), any());
    }

    @Test
    void applyPaymentMarksFullyPaid()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));

        service.applyPayment(SHOP, ORDER, SUFFIX, new BigDecimal("100"), null);

        verify(orderMapper).applyPayment(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(new BigDecimal("100")),
                eq("已支付"), eq("已收款"), any());
    }

    @Test
    void applyPaymentMarksOverpaid()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));

        service.applyPayment(SHOP, ORDER, SUFFIX, new BigDecimal("120"), null);

        verify(orderMapper).applyPayment(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(new BigDecimal("120")),
                eq("超额支付"), eq("超额收款"), any());
    }

    @Test
    void applyPaymentRejectsCancelledOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_CANCELLED));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.applyPayment(SHOP, ORDER, SUFFIX, new BigDecimal("10"), null));
        assertTrue(error.getMessage().contains("已取消"));
    }

    /** 工单号回写要认明细：明细不存在，或者不属于这张订单，一律挡。 */
    @Test
    void updateItemProductionOrderRejectsUnknownDetail()
    {
        when(itemMapper.selectByDetailCodeForUpdate(SHOP, DETAIL, SUFFIX)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateItemProductionOrder(SHOP, ORDER, SUFFIX, DETAIL, "SC001"));

        assertTrue(error.getMessage().contains("订单明细不存在"));
        verify(itemMapper, never()).updateSourceCode(anyString(), anyString(), anyString(), any());
    }

    /** 明细属于别的订单也要挡：否则生产模块能把工单号写到不相干的单上。 */
    @Test
    void updateItemProductionOrderRejectsDetailOfAnotherOrder()
    {
        SalesOrderItem item = storedItem("10", "0");
        item.setOrderCode("SO-OTHER");
        when(itemMapper.selectByDetailCodeForUpdate(SHOP, DETAIL, SUFFIX)).thenReturn(item);

        assertThrows(ServiceException.class,
                () -> service.updateItemProductionOrder(SHOP, ORDER, SUFFIX, DETAIL, "SC001"));

        verify(itemMapper, never()).updateSourceCode(anyString(), anyString(), anyString(), any());
    }

    /** 建工单：把工单号记到明细上。 */
    @Test
    void updateItemProductionOrderWritesTheWorkOrderCode()
    {
        when(itemMapper.selectByDetailCodeForUpdate(SHOP, DETAIL, SUFFIX)).thenReturn(storedItem("10", "0"));
        when(itemMapper.updateSourceCode(SHOP, DETAIL, SUFFIX, "SC001")).thenReturn(1);

        service.updateItemProductionOrder(SHOP, ORDER, SUFFIX, DETAIL, "SC001");

        verify(itemMapper).updateSourceCode(SHOP, DETAIL, SUFFIX, "SC001");
    }

    /** 关工单/移出分配：传 null 松开占用，这条明细又能被别的工单接手。 */
    @Test
    void updateItemProductionOrderClearsTheLinkWhenGivenNull()
    {
        when(itemMapper.selectByDetailCodeForUpdate(SHOP, DETAIL, SUFFIX)).thenReturn(storedItem("10", "0"));
        when(itemMapper.updateSourceCode(SHOP, DETAIL, SUFFIX, null)).thenReturn(1);

        service.updateItemProductionOrder(SHOP, ORDER, SUFFIX, DETAIL, null);

        verify(itemMapper).updateSourceCode(SHOP, DETAIL, SUFFIX, null);
    }

    /** 月表后缀非法要挡在查库之前。 */
    @Test
    void rejectsBadTableSuffix()
    {
        assertThrows(ServiceException.class, () -> service.selectOrderDetail(SHOP, ORDER, "abc"));
    }

    /**
     * 回款下拉的过滤口径：草稿、已取消、已收满的都不出现。
     *
     * <p>这条最容易在现场表现成「下拉是空的、系统坏了」，实际是订单被填成了超额收款。</p>
     *
     * <p>「已收 &lt; 应收」和状态排除都已经下推到 SQL（见 SalesOrderMapper.selectReceivableOrders），
     * mock 测不到 where 里的判断，这里守住服务层还管得着的那半边：排除清单传对、
     * 并且不再退回「拉全月订单再逐条丢掉」的老路子。</p>
     */
    @Test
    void receivableOrdersExcludeDraftCancelledAndFullyPaid()
    {
        SalesOrder partial = receivable("SO-PART", SalesOrder.STATE_READY, "100", "30");
        when(orderMapper.selectReceivableOrders(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(partial)));

        List<SalesOrder> result = service.selectReceivableOrders(SHOP, SUFFIX);

        assertEquals(1, result.size());
        assertEquals("SO-PART", result.get(0).getOrderCode());
        assertEquals(SUFFIX, result.get(0).getTableSuffix());

        ArgumentCaptor<List<String>> excluded = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).selectReceivableOrders(eq(SHOP), eq(SUFFIX), excluded.capture());
        assertTrue(excluded.getValue().contains(SalesOrder.STATE_DRAFT));
        assertTrue(excluded.getValue().contains(SalesOrder.STATE_CANCELLED));
        verify(orderMapper, never()).selectList(any(), anyString());
    }

    /** 不给月份就扫全部月表：8 月下的单 9 月才到账是常态，按当月过滤会让业务员找不到单。 */
    @Test
    void receivableOrdersWithoutSuffixScanAllMonths()
    {
        when(orderMapper.selectOrderTableSuffixes()).thenReturn(Arrays.asList("202607", "202608"));
        when(orderMapper.selectReceivableOrders(eq(SHOP), eq("202607"), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(
                        receivable("SO-07", SalesOrder.STATE_READY, "100", "0"))));
        when(orderMapper.selectReceivableOrders(eq(SHOP), eq("202608"), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(
                        receivable("SO-08", SalesOrder.STATE_READY, "100", "0"))));

        List<SalesOrder> result = service.selectReceivableOrders(SHOP, null);

        assertEquals(2, result.size());
        // 月份倒序，最近的单排在前面
        assertEquals("SO-08", result.get(0).getOrderCode());
        assertEquals("202608", result.get(0).getTableSuffix());
        assertEquals("202607", result.get(1).getTableSuffix());
    }

    private SalesOrder receivable(String orderCode, String state, String total, String paid)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(orderCode);
        order.setOrderState(state);
        order.setTotalAmount(new BigDecimal(total));
        order.setPaidAmount(new BigDecimal(paid));
        return order;
    }

    // ================= 币种 =================

    /** 建单可以选币种，服务端按白名单归一（小写也认）。 */
    @Test
    void createOrderNormalizesCurrency()
    {
        stubPlatformStore();
        command.setCurrency("usd");
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);

        assertEquals("USD", service.createOrder(SHOP, command, USER).getCurrency());
    }

    /** 白名单外的币种不落库：后端读它的第一刻就会抛，整页打不开。 */
    @Test
    void createOrderRejectsUnknownCurrency()
    {
        stubPlatformStore();
        command.setCurrency("XXX");
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().contains("币种"));
    }

    /**
     * 店铺停用后，挂在它名下的草稿还能改。
     *
     * <p>停用的语义是「以后别再往这个店铺开新单」，不是「把已经开出来的单冻住」。
     * 原先建单改单共用一套严格校验，店铺一停用那些草稿就再也改不动了——连「换成一个
     * 启用的店铺」这个唯一的自救动作都做不了，而提交那条路又不查店铺，
     * 于是出现「改不了却提交得了」。</p>
     */
    @Test
    void updateDraftKeepsDisabledStoreWhenUnchanged()
    {
        stubPlatformStore(1, 0);
        command.setOrderCode(ORDER);
        SalesOrder old = existing(SalesOrder.STATE_DRAFT);
        old.setPlatCode(PLAT);
        old.setShopStoreCode(STORE);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);
        when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);

        service.updateDraft(SHOP, command, SUFFIX, USER);

        verify(orderMapper).updateDraft(argThat(o -> STORE.equals(o.getShopStoreCode())), eq(SUFFIX));
    }

    /** 但不能借着改单把订单挪到一个已停用的店铺上——那等于绕开停用开新单。 */
    @Test
    void updateDraftRejectsSwitchingToDisabledStore()
    {
        stubPlatformStore(1, 0);
        command.setOrderCode(ORDER);
        SalesOrder old = existing(SalesOrder.STATE_DRAFT);
        old.setPlatCode(PLAT);
        old.setShopStoreCode("STORE-OTHER");
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, command, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已停用"), error.getMessage());
        verify(orderMapper, never()).updateDraft(any(), anyString());
    }

    /** 建单没有「保持原样」一说：停用的店铺一律不许选。 */
    @Test
    void createOrderRejectsDisabledStore()
    {
        stubPlatformStore(1, 0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));

        assertTrue(error.getMessage().contains("已停用"), error.getMessage());
    }

    /**
     * 平台停用同理，报错要指名道姓是平台不是店铺。
     *
     * <p>只打平台的桩：平台一停用就直接抛了，根本走不到查店铺那一步，
     * 顺手把店铺也 stub 上会被严格模式判成 UnnecessaryStubbing。</p>
     */
    @Test
    void createOrderRejectsDisabledPlatform()
    {
        OrderPlatform platform = new OrderPlatform();
        platform.setPlatformCode(PLAT);
        platform.setPlatformName("测试平台");
        platform.setIsActive(0);
        when(platformMapper.selectPlatformByCode(SHOP, PLAT)).thenReturn(platform);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));

        assertTrue(error.getMessage().contains("平台") && error.getMessage().contains("已停用"),
                error.getMessage());
    }

    /**
     * 改单时没换商品的行要沿用原来的明细代码。
     *
     * <p>改单是「删掉全部明细再整体重建」，每行都发新代码的话，外部按 orderdetailcode
     * 记着这张单的历史记录（已取消的发货批次、关掉的预占行）会在改一次单之后
     * 全部指向不存在的行。</p>
     */
    @Test
    void updateDraftKeepsDetailCodeForUnchangedGoods()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_DRAFT));
        SalesOrderItem stored = storedItem("5", "0");
        stored.setGoodsCode("G001");
        stored.setDetailCode("SOMX-OLD-001");
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX)).thenReturn(List.of(stored));
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);

        service.updateDraft(SHOP, command, SUFFIX, USER);

        verify(itemMapper).batchInsert(
                argThat(list -> "SOMX-OLD-001".equals(list.get(0).getDetailCode())), eq(SUFFIX));
        // 沿用了就不该再去要新号
        verify(codeGenerator, never()).nextSalesOrderDetailCode();
    }

    /** 换成别的商品就是另一行了，该发新的明细代码。 */
    @Test
    void updateDraftIssuesNewDetailCodeWhenGoodsChanged()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_DRAFT));
        SalesOrderItem stored = storedItem("5", "0");
        stored.setGoodsCode("G-OTHER");
        stored.setDetailCode("SOMX-OLD-001");
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX)).thenReturn(List.of(stored));
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);
        when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);

        service.updateDraft(SHOP, command, SUFFIX, USER);

        verify(itemMapper).batchInsert(argThat(list -> DETAIL.equals(list.get(0).getDetailCode())), eq(SUFFIX));
    }

    /** 已驳回的订单可以直接删掉：没过审、没预占、没发过货，留着只是垃圾。 */
    @Test
    void deleteDraftAllowsRejectedOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_REJECTED));
        when(orderMapper.deleteDraft(eq(SHOP), eq(ORDER), eq(SUFFIX), anyList())).thenReturn(1);

        assertEquals(1, service.deleteDraft(SHOP, ORDER, SUFFIX));

        verify(itemMapper).deleteByOrderCode(SHOP, ORDER, SUFFIX);
    }

    /** 过了审的订单不能删——那时已经预占了库存，得走撤审或关闭。 */
    @Test
    void deleteDraftRejectsConfirmedOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(existing(SalesOrder.STATE_READY));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.deleteDraft(SHOP, ORDER, SUFFIX));

        assertTrue(error.getMessage().contains("草稿和已驳回"), error.getMessage());
        verify(orderMapper, never()).deleteDraft(anyString(), anyString(), anyString(), anyList());
    }

    /** 还没提交过、也没收过钱的草稿，币种随便改。 */
    @Test
    void updateDraftAllowsCurrencyChangeOnUntouchedDraft()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        command.setCurrency("USD");
        SalesOrder old = existing(SalesOrder.STATE_DRAFT);
        old.setCurrency("CNY");
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);
        when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);

        service.updateDraft(SHOP, command, SUFFIX, USER);

        verify(orderMapper).updateDraft(argThat(o -> "USD".equals(o.getCurrency())), eq(SUFFIX));
    }

    /**
     * 已驳回的订单是提交过的——退回来改的是内容不是币种。
     * 客户看到的报价、财务按币种筛出来的可回款订单都以它为准，改了两边就对不上。
     */
    @Test
    void updateDraftRejectsCurrencyChangeAfterSubmit()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        command.setCurrency("USD");
        SalesOrder old = existing(SalesOrder.STATE_REJECTED);
        old.setCurrency("CNY");
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, command, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已提交审核"));
        verify(orderMapper, never()).updateDraft(any(), anyString());
    }

    /** 收过钱之后更不能改：已收金额是按原币种记的。 */
    @Test
    void updateDraftRejectsCurrencyChangeAfterPayment()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        command.setCurrency("USD");
        SalesOrder old = existing(SalesOrder.STATE_DRAFT);
        old.setCurrency("CNY");
        old.setPaidAmount(new BigDecimal("100"));
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, command, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已有收款记录"));
        verify(orderMapper, never()).updateDraft(any(), anyString());
    }

    /** 币种没变的话，已提交、已收款的单照样能改内容——锁的是币种，不是整张单。 */
    @Test
    void updateDraftAllowsOtherEditsWhenCurrencyUnchanged()
    {
        stubPlatformStore();
        command.setOrderCode(ORDER);
        command.setCurrency("CNY");
        SalesOrder old = existing(SalesOrder.STATE_REJECTED);
        old.setCurrency("CNY");
        old.setPaidAmount(new BigDecimal("100"));
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(old);
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);
        when(orderMapper.updateDraft(any(), eq(SUFFIX))).thenReturn(1);

        assertEquals(1, service.updateDraft(SHOP, command, SUFFIX, USER));
    }

    /** 已备货的订单不能再改，要改先撤审。 */
    @Test
    void updateDraftRejectsConfirmedOrder()
    {
        command.setOrderCode(ORDER);
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, command, SUFFIX, USER));
        assertTrue(error.getMessage().contains("撤审"));
    }

    // ================= 提交 / 驳回 / 撤审 / 关闭 =================

    /** 提交只推状态，一件库存都不能占——待审核的单可能被驳回。 */
    @Test
    void submitMovesDraftToPendingWithoutReserving()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_DRAFT));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_PENDING),
                anyList(), isNull(), isNull())).thenReturn(1);

        service.submitOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_PENDING),
                anyList(), isNull(), isNull());
    }

    /** 空单不该走到审核台上才被发现。 */
    @Test
    void submitRejectsOrderWithoutItems()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_DRAFT));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX)).thenReturn(Collections.emptyList());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("没有明细"));
    }

    @Test
    void submitRejectsAlreadyPendingOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("只有草稿和已驳回"));
    }

    /** 驳回把原因写进 rejectreason；待审核没占过库存，不需要释放。 */
    @Test
    void rejectStoresReasonAndTouchesNoReservation()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_PENDING));
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_REJECTED),
                anyList(), eq("单价低于底价"), isNull())).thenReturn(1);

        service.rejectOrder(SHOP, ORDER, SUFFIX, "  单价低于底价  ", USER);

        verify(orderMapper).updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_REJECTED),
                anyList(), eq("单价低于底价"), isNull());
    }

    @Test
    void rejectRequiresReason()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rejectOrder(SHOP, ORDER, SUFFIX, "   ", USER));
        assertTrue(error.getMessage().contains("驳回原因"));
        verify(orderMapper, never()).updateStateWithReason(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyString(), anyString());
    }

    /** 撤审要连预占一起放掉，否则那部分可用量永远被吃着。 */
    @Test
    void unconfirmReleasesReservationsAndResetsProgress()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null)).thenReturn(Collections.emptyMap());
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_DRAFT),
                anyList(), isNull(), isNull())).thenReturn(1);

        service.unconfirmOrder(SHOP, ORDER, SUFFIX, USER);

        verify(orderMapper).updateShipProgress(SHOP, ORDER, SUFFIX, BigDecimal.ZERO, SalesOrder.STATE_DRAFT);
    }

    /** 发过货的不能撤审——撤审后明细会被整体重建，已发量就成了对不上的账。 */
    @Test
    void unconfirmRejectsOrderWithShippedGoods()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "3")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unconfirmOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("关闭订单"));
    }

    /** 在途批次记着订单明细代码，撤审重建明细会让它指空，所以要先拦下。 */
    @Test
    void unconfirmRejectsOrderWithOpenBatches()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_READY));
        when(itemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null))
                .thenReturn(Collections.singletonMap("SOMX1", new BigDecimal("2")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.unconfirmOrder(SHOP, ORDER, SUFFIX, USER));
        assertTrue(error.getMessage().contains("批次"));
    }

    /** 关闭：写原因、放掉剩余预占，已发数量一个字都不动。 */
    @Test
    void closeStoresReasonAndReleasesRemainingReservations()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_SHIPPING));
        when(itemMapper.selectByOrderCode(SHOP, ORDER, SUFFIX))
                .thenReturn(Collections.singletonList(storedItem("10", "3")));
        when(orderMapper.updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_CLOSED),
                anyList(), isNull(), eq("客户弃单"))).thenReturn(1);

        service.closeOrder(SHOP, ORDER, SUFFIX, "客户弃单", USER);

        verify(orderMapper).updateStateWithReason(eq(SHOP), eq(ORDER), eq(SUFFIX), eq(SalesOrder.STATE_CLOSED),
                anyList(), isNull(), eq("客户弃单"));
        verify(itemMapper, never()).increaseShippedQty(anyString(), anyString(), anyString(), any());
    }

    @Test
    void closeRequiresReason()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeOrder(SHOP, ORDER, SUFFIX, null, USER));
        assertTrue(error.getMessage().contains("关闭原因"));
    }

    /** 草稿谈不上关闭，那是「删除」的活。 */
    @Test
    void closeRejectsDraftOrder()
    {
        when(orderMapper.selectByCodeForUpdate(SHOP, ORDER, SUFFIX)).thenReturn(existing(SalesOrder.STATE_DRAFT));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeOrder(SHOP, ORDER, SUFFIX, "不发了", USER));
        assertTrue(error.getMessage().contains("可以关闭"));
    }

    // ------------------------------------------------------------------
    // 可发货订单下拉：查询次数必须与订单张数无关
    // ------------------------------------------------------------------

    /**
     * 可发量 = 数量 - 已发 - 已排进未发货批次的量，且明细要按订单挂回各自的单上。
     *
     * <p>这里同时盯住查询次数：三张单只允许一次单头查询、一次明细查询、一次占用量查询。
     * 曾经的实现按状态各查一次、再逐单捞明细并逐单算占用量，订单一多就是几百次往返。</p>
     */
    @Test
    void shippableOrdersLoadItemsAndPendingQtyInOneRoundTrip()
    {
        SalesOrder confirmed = shippableOrder("SO001", SalesOrder.STATE_READY);
        SalesOrder shipping = shippableOrder("SO002", SalesOrder.STATE_SHIPPING);
        when(orderMapper.selectByStates(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<>(Arrays.asList(confirmed, shipping)));
        when(itemMapper.selectByOrderCodes(eq(SHOP), anyList(), eq(SUFFIX)))
                .thenReturn(Arrays.asList(
                        lineOf("SO001", "D1", "10", "2"),
                        lineOf("SO002", "D2", "5", "0")));
        // D1 上还有 3 个已经排进了别的未发货批次，可发量只剩 10 - 2 - 3 = 5
        when(pendingQtyResolver.resolveByOrders(eq(SHOP), anyList()))
                .thenReturn(Collections.singletonMap("D1", new BigDecimal("3")));

        List<SalesOrder> result = service.selectShippableOrders(SHOP, SUFFIX);

        assertEquals(2, result.size());
        assertEquals("SO001", result.get(0).getOrderCode());
        assertEquals(0, result.get(0).getItems().get(0).getRemainingQty().compareTo(new BigDecimal("5")));
        assertEquals(0, result.get(1).getItems().get(0).getRemainingQty().compareTo(new BigDecimal("5")));
        assertEquals(SUFFIX, result.get(0).getTableSuffix());

        verify(orderMapper).selectByStates(eq(SHOP), eq(SUFFIX), anyList());
        verify(itemMapper).selectByOrderCodes(eq(SHOP), anyList(), eq(SUFFIX));
        verify(pendingQtyResolver).resolveByOrders(eq(SHOP), anyList());
        // 逐单查明细的老路子必须彻底走掉
        verify(itemMapper, never()).selectByOrderCode(anyString(), anyString(), anyString());
        verify(pendingQtyResolver, never()).resolve(anyString(), anyString(), any());
    }

    /** 全部排满的订单不进下拉：可发量为 0 的单选出来也发不了。 */
    @Test
    void shippableOrdersDropFullyCommittedOnes()
    {
        SalesOrder full = shippableOrder("SO003", SalesOrder.STATE_PREPARING);
        when(orderMapper.selectByStates(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(full)));
        when(itemMapper.selectByOrderCodes(eq(SHOP), anyList(), eq(SUFFIX)))
                .thenReturn(Collections.singletonList(lineOf("SO003", "D3", "8", "8")));
        when(pendingQtyResolver.resolveByOrders(eq(SHOP), anyList())).thenReturn(Collections.emptyMap());

        assertTrue(service.selectShippableOrders(SHOP, SUFFIX).isEmpty());
    }

    /** 一张可发货的单都没有时，后面两次查询根本不该发出去。 */
    @Test
    void shippableOrdersSkipDetailQueriesWhenNoOrders()
    {
        when(orderMapper.selectByStates(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<SalesOrder>());

        assertTrue(service.selectShippableOrders(SHOP, SUFFIX).isEmpty());

        verify(itemMapper, never()).selectByOrderCodes(anyString(), anyList(), anyString());
        verify(pendingQtyResolver, never()).resolveByOrders(anyString(), anyList());
    }

    private SalesOrder shippableOrder(String orderCode, String state)
    {
        SalesOrder order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(orderCode);
        order.setOrderState(state);
        return order;
    }

    private SalesOrderItem lineOf(String orderCode, String detailCode, String qty, String shipped)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setShopCode(SHOP);
        item.setOrderCode(orderCode);
        item.setDetailCode(detailCode);
        item.setGoodsCode("G001");
        item.setQty(new BigDecimal(qty));
        item.setShippedQty(new BigDecimal(shipped));
        return item;
    }

    // ------------------------------------------------------------------
    // 可回款 / 可退款下拉：筛选条件下推到 SQL，排除清单两边不一样
    // ------------------------------------------------------------------

    /**
     * 可退款的排除清单里<b>只有</b>草稿。
     *
     * <p>已取消的单必须留着——取消订单后把客户已付的钱退回去正是退款最常见的场景。
     * 照抄可回款那份清单就会让这批单从下拉里消失。</p>
     */
    @Test
    void refundableOrdersKeepCancelledOnes()
    {
        SalesOrder cancelled = existing(SalesOrder.STATE_CANCELLED);
        cancelled.setPaidAmount(new BigDecimal("100"));
        when(refundWindowResolver.lastSettledTimes(eq(SHOP), anyString()))
                .thenReturn(Collections.singletonMap(ORDER, new Date()));
        when(orderMapper.selectRefundableOrders(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(cancelled)));
        when(refundWindowResolver.withinWindow(any())).thenReturn(true);

        List<SalesOrder> result = service.selectRefundableOrders(SHOP, SUFFIX);

        assertEquals(1, result.size());

        ArgumentCaptor<List<String>> excluded = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).selectRefundableOrders(eq(SHOP), eq(SUFFIX), excluded.capture());
        assertEquals(Collections.singletonList(SalesOrder.STATE_DRAFT), excluded.getValue());
        verify(orderMapper, never()).selectList(any(), anyString());
    }

    /** 退款时效过期的单要在服务层被筛掉——这个条件依赖资金流水，下推不了。 */
    @Test
    void refundableOrdersStillFilterExpiredWindowInJava()
    {
        SalesOrder paid = existing(SalesOrder.STATE_FINISHED);
        paid.setPaidAmount(new BigDecimal("100"));
        when(refundWindowResolver.lastSettledTimes(eq(SHOP), anyString()))
                .thenReturn(Collections.emptyMap());
        when(orderMapper.selectRefundableOrders(eq(SHOP), eq(SUFFIX), anyList()))
                .thenReturn(new ArrayList<>(Collections.singletonList(paid)));
        when(refundWindowResolver.withinWindow(any())).thenReturn(false);

        assertTrue(service.selectRefundableOrders(SHOP, SUFFIX).isEmpty());
    }

    // ================= 出口字段：客户国家/地址、国内收货方式、唛头、商品描述清单 =================

    /** 建单时能顺利落库的一套桩，出口字段的用例都要走完整条建单路径 */
    private void stubCreatePath()
    {
        stubPlatformStore();
        when(goodsMapper.selectGoodsByCode(SHOP, "G001")).thenReturn(goods("G001", "测试商品"));
        when(codeGenerator.nextSalesOrderCode()).thenReturn(ORDER);
        when(codeGenerator.nextSalesOrderDetailCode()).thenReturn(DETAIL);
    }

    private SalesOrderDescItem desc(String name, String qty, String price)
    {
        SalesOrderDescItem row = new SalesOrderDescItem();
        row.setName(name);
        if (qty != null) row.setQty(new BigDecimal(qty));
        if (price != null) row.setPrice(new BigDecimal(price));
        return row;
    }

    /** 前端没传收货方式时兜成货代地址：列是 NOT NULL，落 null 会直接插不进去。 */
    @Test
    void createOrderDefaultsReceiveTypeToForwarder()
    {
        stubCreatePath();

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals(SalesOrder.RECEIVE_TYPE_FORWARDER, created.getReceiveType());
        assertEquals(Boolean.FALSE, created.getMarkNeeded());
    }

    /** 认不出的收货方式直接拒：脏值落进去，界面上三个单选框会一个都选不中。 */
    @Test
    void createOrderRejectsUnknownReceiveType()
    {
        command.setReceiveType("随便发发");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().contains("国内收货方式"));
    }

    /**
     * 上门提货货不出仓、不发快递、不打面单，收件人、电话、地址三样都不强求。
     *
     * <p>三列在库里都是 NOT NULL，所以要兜成空串而不是留 null，否则直接插不进去。</p>
     */
    @Test
    void pickupOrderAllowsBlankReceiveContact()
    {
        stubCreatePath();
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_PICKUP);
        command.setReceivePerson(null);
        command.setReceiveTelephone(null);
        command.setReceiveAddress(null);

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals("", created.getReceivePerson());
        assertEquals("", created.getReceiveTelephone());
        assertEquals("", created.getReceiveAddress());
    }

    /** 上门提货可以不填电话，但填了得像一串电话——写进字母的一律拦。 */
    @Test
    void pickupOrderStillValidatesPhoneFormatWhenFilled()
    {
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_PICKUP);
        command.setReceiveTelephone("找老王");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().startsWith("收件电话"));
    }

    /**
     * 收件电话<b>不</b>按 11 位手机号收。
     *
     * <p>平台下单给的是虚拟号，位数不固定；客户留的可能是带区号的固话；也常常一次留
     * 好几个号码怕打不通。这三类以前全被 11 位的规则挡在门外，是实打实录不进单。</p>
     */
    @Test
    void receiveTelephoneAcceptsVirtualLandlineAndMultipleNumbers()
    {
        stubCreatePath();
        command.setReceiveTelephone("400-800-1234;0755-88886666, 17012345678");

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals("400-800-1234;0755-88886666, 17012345678", created.getReceiveTelephone());
    }

    /** 另外两种收货方式要把货发出去，收件人一样不能少。 */
    @Test
    void nonPickupOrderStillRequiresReceivePerson()
    {
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_BUYER);
        command.setReceivePerson(" ");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertEquals("收件人不能为空", error.getMessage());
    }

    /** 另外两种收货方式要把货发出去，没地址发不了，照旧必填。 */
    @Test
    void nonPickupOrderStillRequiresReceiveAddress()
    {
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_BUYER);
        command.setReceiveAddress("  ");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertEquals("收件地址不能为空", error.getMessage());
    }

    /**
     * 唛头是货代认箱用的。不走货代却带着唛头文件，是前端切换收货方式时没清干净——
     * 服务端再清一遍，免得库里留下「上门提货却要唛头」这种解释不了的单。
     */
    @Test
    void nonForwarderOrderClearsMarkFields()
    {
        stubCreatePath();
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_BUYER);
        command.setMarkNeeded(Boolean.TRUE);
        command.setMarkFile("/profile/upload/mark.pdf");

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals(Boolean.FALSE, created.getMarkNeeded());
        assertNull(created.getMarkFile());
    }

    /** 走货代且勾了要唛头，文件才留得住。 */
    @Test
    void forwarderOrderKeepsMarkFile()
    {
        stubCreatePath();
        command.setReceiveType(SalesOrder.RECEIVE_TYPE_FORWARDER);
        command.setMarkNeeded(Boolean.TRUE);
        command.setMarkFile("/profile/upload/mark.pdf");

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals(Boolean.TRUE, created.getMarkNeeded());
        assertEquals("/profile/upload/mark.pdf", created.getMarkFile());
    }

    /**
     * 商品描述清单：金额由服务端按数量×单价重算，不收前端给的值；
     * 并且它不参与货款——货款仍旧只按货品明细算。
     */
    @Test
    void descItemsRecalculateAmountAndStayOutOfGoodsAmount()
    {
        stubCreatePath();
        SalesOrderDescItem row = desc("定制礼盒 A 款", "3", "20");
        row.setAmount(new BigDecimal("99999"));   // 前端瞎给的金额，应被覆盖
        command.setDescItems(new ArrayList<>(Collections.singletonList(row)));

        SalesOrder created = service.createOrder(SHOP, command, USER);

        List<SalesOrderDescItem> saved =
                JSON.parseArray(created.getDescItemsJson(), SalesOrderDescItem.class);
        assertEquals(1, saved.size());
        assertEquals(0, saved.get(0).getAmount().compareTo(new BigDecimal("60.00")), "3 × 20");
        // 货款还是 10 × 12.5，描述清单一分钱都没进来
        assertEquals(0, created.getGoodsAmount().compareTo(new BigDecimal("125.00")));
    }

    /** 名称是描述行唯一的内容，空的整行丢掉；全丢光就存 null，不存 "[]"。 */
    @Test
    void descItemsDropNamelessRowsAndStoreNullWhenEmpty()
    {
        stubCreatePath();
        command.setDescItems(new ArrayList<>(Arrays.asList(
                desc("  ", "3", "20"), desc(null, "1", "1"))));

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertNull(created.getDescItemsJson());
    }

    /** 这份清单是要拿给客户看的，负数的数量或单价直接拒。 */
    @Test
    void descItemsRejectNegativeNumbers()
    {
        stubPlatformStore();
        command.setDescItems(new ArrayList<>(Collections.singletonList(
                desc("定制礼盒 A 款", "-1", "20"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createOrder(SHOP, command, USER));
        assertTrue(error.getMessage().contains("不能是负数"));
    }

    /** 客户国家与客户地址原样落库，服务端不改写——它们是销售填给客户看的。 */
    @Test
    void createOrderKeepsBuyerCountryAndAddress()
    {
        stubCreatePath();
        command.setBuyerCountry("US");
        command.setBuyerAddress("123 Main St, Springfield, IL 62704, USA");

        SalesOrder created = service.createOrder(SHOP, command, USER);

        assertEquals("US", created.getBuyerCountry());
        assertEquals("123 Main St, Springfield, IL 62704, USA", created.getBuyerAddress());
    }
}
