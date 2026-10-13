package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.domain.ShipBatchItem;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.ShipBatchItemMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.runner.MonthlyTableInitializer.TableGroup;
import com.ruoyi.system.service.IOutboundService;
import com.ruoyi.system.service.ISalesOrderService;

/**
 * 发货批次的可发量校验、付款额度校验与状态机测试。
 *
 * <p>状态机：待确认 → 已提交仓库 → 已打印备货单 → 备货完成 → 已录入快递单号
 * → 已打印快递单号 → 已完成。</p>
 *
 * <p>核心约定：<b>库存只在最后一步「完成」时扣</b>，那一刻＝承运商真把货取走。
 * 中间那几格货一直在仓库里——「已打印快递单号」也只表示贴了面单在等人上门；
 * 出库单、扣减、回写已发量全部推迟到完成那一刻，三步同事务。</p>
 *
 * <p>由此派生出「驳回」：完成之前的五格都能整个退回「待确认」，见文件末尾那组用例。</p>
 */
@ExtendWith(MockitoExtension.class)
class ShipBatchServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String ORDER_SUFFIX = "202608";
    private static final String BATCH_SUFFIX = "202609";
    private static final String ORDER = "SO20260819001";
    private static final String DETAIL = "SOMX20260819001";
    /** 发货批次明细自己的代码。与订单明细代码 {@link #DETAIL} 不是一回事，录单改数量按它定位 */
    private static final String BATCH_DETAIL = "XSFHMX20260819001";
    private static final String SHIP = "XSFH20260819001";
    private static final String WARE = "WH1";
    private static final String USER = "tester";

    @Mock private ShipBatchMapper batchMapper;
    @Mock private ShipBatchItemMapper batchItemMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private InventoryWarehouseMapper warehouseMapper;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;
    /** 建出库单前的整批加锁；同包不用 import */
    @Mock private OrderStockLockCoordinator stockLockCoordinator;
    @Mock private ISalesOrderService orderService;
    @Mock private IOutboundService outboundService;
    // 「已排进未发货批次的量」抽成了独立组件，订单服务算可发量也用它——
    // 两边共用是为了避开循环依赖，见 ShipBatchPendingQtyResolver 的类注释。
    @Mock private ShipBatchPendingQtyResolver pendingQtyResolver;
    // 计划发货日期可以指向未来月份，建单前要按需补建目标月表
    @Mock private MonthlyTableInitializer monthlyTables;

    @InjectMocks private ShipBatchServiceImpl service;

    private SalesOrder order;

    @BeforeEach
    void setUp()
    {
        order = new SalesOrder();
        order.setShopCode(SHOP);
        order.setOrderCode(ORDER);
        order.setOrderState(SalesOrder.STATE_READY);
        order.setTableSuffix(ORDER_SUFFIX);
        order.setCurrency("CNY");
        order.setTotalAmount(new BigDecimal("125.00"));
        // 默认全款已收，免得每个用例都被付款额度拦下来。卡额度的那几条自己改小
        order.setPaidAmount(new BigDecimal("125.00"));
    }

    private SalesOrderItem orderItem(String qty, String shipped)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setShopCode(SHOP);
        item.setOrderCode(ORDER);
        item.setDetailCode(DETAIL);
        item.setGoodsCode("G001");
        item.setGoodsName("测试商品");
        item.setSpec("STD");
        item.setUnit("个");
        item.setQty(new BigDecimal(qty));
        item.setShippedQty(new BigDecimal(shipped));
        item.setPrice(new BigDecimal("12.5"));
        return item;
    }

    private ShipBatchItem batchItem(String qty)
    {
        ShipBatchItem item = new ShipBatchItem();
        item.setDetailCode(BATCH_DETAIL);
        item.setOrderDetailCode(DETAIL);
        item.setGoodsName("测试商品");
        item.setQty(new BigDecimal(qty));
        return item;
    }

    private ShipBatch pendingBatch(String status)
    {
        ShipBatch batch = new ShipBatch();
        batch.setShopCode(SHOP);
        batch.setShipCode(SHIP);
        batch.setOrderCode(ORDER);
        batch.setWarehouseCode(WARE);
        batch.setWarehouseName("主仓");
        batch.setStatus(status);
        return batch;
    }

    private ShipBatch createCommand(String qty)
    {
        ShipBatch command = new ShipBatch();
        command.setOrderCode(ORDER);
        command.setWarehouseCode(WARE);
        command.setShipDate(new Date());
        command.setItems(new ArrayList<>(Collections.singletonList(batchItem(qty))));
        return command;
    }

    /** 草稿订单不能建发货批次。 */
    @Test
    void createRejectsDraftOrder()
    {
        order.setOrderState(SalesOrder.STATE_DRAFT);
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createBatch(SHOP, createCommand("5"), USER));
        assertTrue(error.getMessage().contains("尚未提交审核"));
    }

    /**
     * 建批次的订单状态门禁是<b>白名单</b>：只有已确认/备货中/发货中放行。
     *
     * <p>待审核和已驳回还没过审、也没预占，替它们发货等于绕过审核；已关闭是人工终止，
     * 剩余部分不再发。这三个状态以前是漏的——那时只挡草稿、已取消、已完成。</p>
     */
    @Test
    void createRejectsOrdersOutsideShippableStates()
    {
        assertCreateRejected(SalesOrder.STATE_PENDING, "待审核");
        assertCreateRejected(SalesOrder.STATE_REJECTED, "已驳回");
        assertCreateRejected(SalesOrder.STATE_CLOSED, "已关闭");
        assertCreateRejected(SalesOrder.STATE_CANCELLED, "已取消");
        assertCreateRejected(SalesOrder.STATE_FINISHED, "已完成");
    }

    private void assertCreateRejected(String orderState, String expectedInMessage)
    {
        order.setOrderState(orderState);
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createBatch(SHOP, createCommand("5"), USER));
        assertTrue(error.getMessage().contains(expectedInMessage),
                "状态「" + orderState + "」的报错文案应说明原因，实际：" + error.getMessage());
    }

    /**
     * 批次建好之后订单还会变，扣库存前必须按<b>当前</b>状态再判一次。
     *
     * <p>只在建批次时挡是不够的：订单确认后建了批次，随后被取消或关闭，那张批次仍能一路
     * 点到「完成」，货就真出库了，而 deriveState 对已取消/已关闭是原样返回，订单状态还挂着
     * 「已取消」，已发数量却涨了。</p>
     */
    @Test
    void completeRejectsWhenOrderLeftShippableStatesAfterBatchCreated()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LABEL_PRINTED));
        when(batchItemMapper.selectByShipCode(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(Collections.singletonList(batchItem("6")));
        order.setOrderState(SalesOrder.STATE_CANCELLED);
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER));
        assertTrue(error.getMessage().contains("已取消"), error.getMessage());
        // 拦在建出库单之前，库存一分不能动
        verify(outboundService, never()).createOutbound(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), anyList(), anyString(), anyString());
    }

    /** 提交仓库同样要看订单状态：订单关闭后剩下的批次不该还能往前推。 */
    @Test
    void confirmRejectsWhenOrderClosed()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PENDING));
        order.setOrderState(SalesOrder.STATE_CLOSED);
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
        assertTrue(error.getMessage().contains("已关闭"), error.getMessage());
        verify(batchMapper, never()).updateStatus(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    /**
     * 取消批次<b>不</b>看订单状态。
     *
     * <p>这是有意的：订单取消/关闭前必须先把在途批次清掉（SalesOrderServiceImpl
     * 的 assertNoPendingBatches），如果取消批次也按订单状态拦，历史遗留的
     * 「已取消订单 + 在途批次」组合就会两边都动不了，死锁在那儿。</p>
     */
    @Test
    void cancelIgnoresOrderState()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PENDING));
        when(batchMapper.updateStatus(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(ShipBatch.STATUS_CANCELLED),
                anyString(), eq(USER), any())).thenReturn(1);

        assertEquals(1, service.cancelBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
        // 压根不该去查订单
        verify(orderService, never()).findOrderAcrossMonths(anyString(), anyString());
    }

    /**
     * 可发量要扣掉「其他未发货批次已排的量」，光看已发量会让同一批货被排两次。
     *
     * <p>订单 10 个，已发 0，另一张待确认批次已排 7 → 只能再排 3，这里要 5 就该被拒。</p>
     */
    @Test
    void createRejectsWhenOtherPendingBatchesAlreadyTookTheQuantity()
    {
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(warehouseMapper.selectWarehouseByCode(SHOP, WARE)).thenReturn(warehouse());
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null))
                .thenReturn(Collections.singletonMap(DETAIL, new BigDecimal("7")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createBatch(SHOP, createCommand("5"), USER));
        assertTrue(error.getMessage().contains("可发数量只有 3"), "实际提示：" + error.getMessage());
    }

    /** 可发量够就建得出来，批次按计划发货日期落表，与订单月份无关。 */
    @Test
    void createSucceedsAndUsesShipDateSuffix()
    {
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(warehouseMapper.selectWarehouseByCode(SHOP, WARE)).thenReturn(warehouse());
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null)).thenReturn(Collections.<String, BigDecimal>emptyMap());
        when(codeGenerator.nextShipBatchCode()).thenReturn(SHIP);
        when(codeGenerator.nextShipBatchDetailCode()).thenReturn("XSFHMX001");

        ShipBatch created = service.createBatch(SHOP, createCommand("5"), USER);

        assertEquals(SHIP, created.getShipCode());
        assertEquals(ShipBatch.STATUS_PENDING, created.getStatus());
        // 商品快照一律取订单明细，保证两边一致
        assertEquals("测试商品", created.getItems().get(0).getGoodsName());
        assertEquals("G001", created.getItems().get(0).getGoodsCode());
    }

    /**
     * 计划发货日期排到下个月：写库之前必须先确认那个月的月表在。
     *
     * <p><b>是「确认」不是「补建」。</b>原来这里调的是 {@code ensureTablesFor}，
     * 真跑起来会撞 {@code 1412 Table definition has changed}——建表 DDL 立即提交，
     * 而本方法的事务早就有了自己的表定义快照，紧接着读这张新表就报错，
     * 表建出来了这一笔仍然失败。月表改由启动器和每天的定时任务提前建当月+下月，
     * 请求链路上只查不建。</p>
     */
    @Test
    void createRequiresMonthlyTablesForFutureShipDate()
    {
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(warehouseMapper.selectWarehouseByCode(SHOP, WARE)).thenReturn(warehouse());
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", "0")));
        when(pendingQtyResolver.resolve(SHOP, ORDER, null)).thenReturn(Collections.<String, BigDecimal>emptyMap());
        when(codeGenerator.nextShipBatchCode()).thenReturn(SHIP);
        when(codeGenerator.nextShipBatchDetailCode()).thenReturn("XSFHMX001");

        ShipBatch command = createCommand("5");
        command.setShipDate(dateOf(2026, 9, 15));

        ShipBatch created = service.createBatch(SHOP, command, USER);

        assertEquals(BATCH_SUFFIX, created.getTableSuffix());
        // 顺序不能反：检查必须发生在写库之前，否则等于没查。
        // 组别也得对：传 INVENTORY 会去查 u_f_docmain 那三张，发货批次自己的两张一张没查，
        // 结果就是「检查通过、insert 才撞表不存在」——这正是这个参数存在的原因。
        InOrder steps = inOrder(monthlyTables, batchMapper, batchItemMapper);
        steps.verify(monthlyTables).requireTablesFor(BATCH_SUFFIX, TableGroup.SHIP_BATCH);
        steps.verify(batchMapper).insert(any(ShipBatch.class), eq(BATCH_SUFFIX));
        steps.verify(batchItemMapper).batchInsert(anyList(), eq(BATCH_SUFFIX));
    }

    private Date dateOf(int year, int month, int day)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    private InventoryWarehouse warehouse()
    {
        InventoryWarehouse w = new InventoryWarehouse();
        w.setWareCode(WARE);
        w.setWareName("主仓");
        return w;
    }

    /**
     * 只 stub 到「取到订单」为止，不 stub 状态迁移。
     *
     * <p>给<b>预期会抛</b>的用例用：strict stubs 下多 stub 一个用不到的迁移语句，
     * 测试会以 UnnecessaryStubbingException 失败，跟被测逻辑没关系。</p>
     *
     * @return 被 stub 出去的那张批次，调用方可以继续改它（比如预置已传的照片）
     */
    private ShipBatch stubPickingUpToOrder()
    {
        ShipBatch batch = pendingBatch(ShipBatch.STATUS_PICK_PRINTED);
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX)).thenReturn(batch);
        when(batchItemMapper.selectByShipCode(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(Collections.singletonList(batchItem("6")));
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        return batch;
    }

    /** 一路 stub 到备货完成能成功。@return 同 {@link #stubPickingUpToOrder()} */
    private ShipBatch stubPickingReady()
    {
        ShipBatch batch = stubPickingUpToOrder();
        when(batchMapper.completePicking(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), anyString(), eq(USER), any()))
                .thenReturn(1);
        return batch;
    }

    /**
     * 只 stub 到「取到订单明细」为止，不 stub 状态迁移。给<b>预期会抛</b>的用例用，
     * 理由同 {@link #stubPickingUpToOrder()}。
     *
     * @return 被 stub 出去的那张批次，调用方可以继续改它（比如预置承运商与快递单号）
     */
    private ShipBatch stubLogisticsUpToItems()
    {
        ShipBatch batch = pendingBatch(ShipBatch.STATUS_PICKED);
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX)).thenReturn(batch);
        when(batchItemMapper.selectByShipCode(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(Collections.singletonList(batchItem("6")));
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        return batch;
    }

    /**
     * 一路 stub 到录入快递单号能成功。订单明细 10 个、已发 0、单价 12.5，
     * 本批次排了 6 个 → 本次货值 75，默认 setUp 里已收 125，额度够。
     *
     * @return 同 {@link #stubLogisticsUpToItems()}
     */
    private ShipBatch stubLogisticsReady()
    {
        ShipBatch batch = stubLogisticsUpToItems();
        stubLogisticsAmounts("0");
        when(batchMapper.enterLogistics(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), any(), any(), eq(USER), any()))
                .thenReturn(1);
        return batch;
    }

    /** 付款额度那一段要的两次查询：订单明细（含单价与已发量）＋其它在途批次占的量 */
    private void stubLogisticsAmounts(String shippedQty)
    {
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", shippedQty)));
        when(pendingQtyResolver.resolveInStatuses(eq(SHOP), eq(ORDER), anyList(), eq(SHIP)))
                .thenReturn(Collections.<String, BigDecimal>emptyMap());
    }

    /** 已打印快递单号的批次可以完成，这一步才建出库单、扣库存、回写已发量。 */
    private void stubCompleteReady(String batchQty)
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LABEL_PRINTED));
        when(batchItemMapper.selectByShipCode(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(Collections.singletonList(batchItem(batchQty)));
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", "0")));

        DocumentMain document = new DocumentMain();
        document.setDocCode("CK20260819001");
        document.setTableSuffix(BATCH_SUFFIX);
        when(outboundService.createOutbound(eq(SHOP), eq(InventoryDocumentTypes.SALES_OUTBOUND), eq(WARE),
                any(StockState.class), any(), any(), eq(SHIP), eq(InventoryDocumentTypes.SHIP_BATCH_SOURCE),
                anyList(), anyString(), eq(USER))).thenReturn(document);
        when(orderItemMapper.increaseShippedQty(eq(SHOP), eq(DETAIL), eq(ORDER_SUFFIX), any())).thenReturn(1);
        when(batchMapper.updateStatus(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(ShipBatch.STATUS_COMPLETED),
                eq(ShipBatch.STATUS_LABEL_PRINTED), eq(USER), any())).thenReturn(1);
    }

    // ================= 备货完成 =================

    /**
     * 备货完成这一步<b>不能</b>碰库存。
     *
     * <p>这是整条链路的核心约定：货拣好装箱只是「备好了」，扣减、开出库单、回写已发量
     * 全部推迟到仓库点「完成」。这里一旦开了出库单，改单与取消的窗口就没了。</p>
     */
    @Test
    void pickingDoesNotTouchStock()
    {
        stubPickingReady();

        assertEquals(1, service.completePicking(SHOP, SHIP, BATCH_SUFFIX, "/profile/upload/a.jpg", USER));

        verify(outboundService, never()).createOutbound(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), anyList(), anyString(), anyString());
        verify(orderItemMapper, never()).increaseShippedQty(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(stockLockCoordinator);
    }

    /**
     * 照片和状态同一条语句落。
     *
     * <p>拆成两条的话，照片写进去而状态迁移被 CAS 挡掉时，批次上会留着一组没人认领的照片。</p>
     */
    @Test
    void pickingWritesPhotosTogetherWithStatus()
    {
        stubPickingReady();
        service.completePicking(SHOP, SHIP, BATCH_SUFFIX, "/a.jpg,/b.jpg", USER);
        verify(batchMapper).completePicking(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq("/a.jpg,/b.jpg"),
                eq(USER), any());
        // 不走通用的 updateStatus，那条语句落不了照片
        verify(batchMapper, never()).updateStatus(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    /**
     * 一张照片都没有就不许点备货完成。
     *
     * <p>业务员在录快递单号之前，能看到的唯一凭证就是这组照片；一张都不传的话这一步
     * 就成了空点一下按钮，这个环节也就白设了。</p>
     */
    @Test
    void pickingRequiresAtLeastOnePhoto()
    {
        stubPickingUpToOrder();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completePicking(SHOP, SHIP, BATCH_SUFFIX, "   ", USER));
        assertTrue(error.getMessage().contains("至少上传一张"), error.getMessage());
        verify(batchMapper, never()).completePicking(anyString(), anyString(), anyString(), anyString(),
                anyString(), any());
    }

    /**
     * 空项要被剔掉，不能靠它骗过「至少一张」。
     *
     * <p>前端 ImageUpload 删掉中间一张时会留下 {@code "a.jpg,,b.jpg"} 这种串；
     * 极端情况下整串只剩逗号，按字符串非空判就放行了。</p>
     */
    @Test
    void pickingRejectsPhotoListThatIsOnlySeparators()
    {
        stubPickingUpToOrder();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completePicking(SHOP, SHIP, BATCH_SUFFIX, " , , ", USER));
        assertTrue(error.getMessage().contains("至少上传一张"), error.getMessage());
    }

    /** 中间的空项剔掉之后照样能过，落库的是规整过的串。 */
    @Test
    void pickingNormalizesPhotoList()
    {
        stubPickingReady();
        service.completePicking(SHOP, SHIP, BATCH_SUFFIX, "/a.jpg, ,/b.jpg,", USER);
        verify(batchMapper).completePicking(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq("/a.jpg,/b.jpg"),
                eq(USER), any());
    }

    /** 没打备货单不能点备货完成：仓库拿到批次的第一步是打单去拣货。 */
    @Test
    void pickingRequiresPrintedPickList()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_SUBMITTED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completePicking(SHOP, SHIP, BATCH_SUFFIX, "/a.jpg", USER));
        assertTrue(error.getMessage().contains("还没打印备货单"), error.getMessage());
        verify(batchMapper, never()).completePicking(anyString(), anyString(), anyString(), anyString(),
                anyString(), any());
    }

    /** 录完快递单号之后照片就是凭证，不能再改。 */
    @Test
    void savePhotosRefusedAfterLogisticsEntered()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LOGISTICS_ENTERED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.savePickPhotos(SHOP, SHIP, BATCH_SUFFIX, "/a.jpg", USER));
        assertTrue(error.getMessage().contains("不能改备货照片"), error.getMessage());
        verify(batchMapper, never()).updatePickPhotos(anyString(), anyString(), anyString(), anyString());
    }

    /** 已经备货完成的批次不许把照片全删光——那等于把业务员录单的依据抽走。 */
    @Test
    void savePhotosRefusesEmptyListOnPickedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PICKED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.savePickPhotos(SHOP, SHIP, BATCH_SUFFIX, " , ", USER));
        assertTrue(error.getMessage().contains("不能全删"), error.getMessage());
    }

    // ================= 录入快递单号 =================

    /** 录快递单号这一步同样不碰库存，货还在仓库里等仓库打面单。 */
    @Test
    void enterLogisticsDoesNotTouchStock()
    {
        stubLogisticsReady();

        assertEquals(1, service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));

        verify(outboundService, never()).createOutbound(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), anyList(), anyString(), anyString());
        verify(orderItemMapper, never()).increaseShippedQty(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(stockLockCoordinator);
    }

    /** 快递单号和状态同一条语句落，走的是专用的 enterLogistics 而不是通用迁移。 */
    @Test
    void enterLogisticsWritesCarrierAndNoTogetherWithStatus()
    {
        stubLogisticsReady();
        service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER);
        verify(batchMapper).enterLogistics(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq("SF123"),
                eq("顺丰速运"), eq(USER), any());
        verify(batchMapper, never()).updateStatus(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    /**
     * 请求没带物流信息时不能把批次上原有的值冲成空。
     *
     * <p>承运商与快递单号现在是录单的必填项，所以「两边都空」这条路已经走不到底了
     * （见 {@link #enterLogisticsRequiresCarrierAndNo()}）；这里守的是另一半——
     * 批次上已经有值、请求里没带，回写的必须是原值。</p>
     */
    @Test
    void enterLogisticsKeepsStoredValuesWhenRequestOmitsThem()
    {
        ShipBatch stored = stubLogisticsReady();
        stored.setCarrier("德邦");
        stored.setLogisticsNo("DB0001");

        service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, null, null, null, USER);
        verify(batchMapper).enterLogistics(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq("DB0001"),
                eq("德邦"), eq(USER), any());
    }

    /** 仓库还没点备货完成，业务员录不了单：得先看到备货照片才知道该发多少。 */
    @Test
    void enterLogisticsRequiresPickedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PICK_PRINTED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF1", "顺丰", null, USER));
        assertTrue(error.getMessage().contains("还没点备货完成"), error.getMessage());
        verify(batchMapper, never()).enterLogistics(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }

    @Test
    void enterLogisticsRejectsAlreadyEnteredBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LOGISTICS_ENTERED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF1", "顺丰", null, USER));
        assertTrue(error.getMessage().contains("已录入快递单号"), error.getMessage());
    }

    // ---- 本次发货数量 ----

    /** 按实际备好的货往下调，改的是批次明细的数量，明细代码不变。 */
    @Test
    void enterLogisticsWritesAdjustedQty()
    {
        stubLogisticsReady();

        service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运",
                Collections.singletonMap(BATCH_DETAIL, new BigDecimal("4")), USER);

        verify(batchItemMapper).updateQty(SHOP, BATCH_DETAIL, BATCH_SUFFIX, new BigDecimal("4"));
    }

    /** 数量没变就不该白发一条 update。 */
    @Test
    void enterLogisticsSkipsUpdateWhenQtyUnchanged()
    {
        stubLogisticsReady();

        service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运",
                Collections.singletonMap(BATCH_DETAIL, new BigDecimal("6")), USER);

        verify(batchItemMapper, never()).updateQty(anyString(), anyString(), anyString(), any());
    }

    /** 某行填 0 ＝这次不发它，整行删掉，它占的订单量随之放回去。 */
    @Test
    void enterLogisticsDeletesZeroQtyLine()
    {
        ShipBatch batch = pendingBatch(ShipBatch.STATUS_PICKED);
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX)).thenReturn(batch);
        // 两行：一行照发，一行填 0
        ShipBatchItem second = batchItem("2");
        second.setDetailCode("XSFHMX20260819002");
        when(batchItemMapper.selectByShipCode(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(Arrays.asList(batchItem("6"), second));
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        stubLogisticsAmounts("0");
        when(batchMapper.enterLogistics(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), any(), any(), eq(USER), any()))
                .thenReturn(1);

        java.util.Map<String, BigDecimal> qtys = new java.util.HashMap<>();
        qtys.put(BATCH_DETAIL, new BigDecimal("6"));
        qtys.put("XSFHMX20260819002", BigDecimal.ZERO);
        service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", qtys, USER);

        verify(batchItemMapper).deleteByDetailCode(SHOP, "XSFHMX20260819002", BATCH_SUFFIX);
        verify(batchItemMapper, never()).deleteByDetailCode(SHOP, BATCH_DETAIL, BATCH_SUFFIX);
    }

    /**
     * 只能往下调。
     *
     * <p>往上加等于绕过建批次时那道可发量校验（订单数量 − 已发 − 其它批次已排），
     * 而这时候货已经按计划量备好了，多出来的那部分根本没有货。</p>
     */
    @Test
    void enterLogisticsRejectsQtyAbovePlanned()
    {
        stubLogisticsUpToItems();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运",
                        Collections.singletonMap(BATCH_DETAIL, new BigDecimal("7")), USER));
        assertTrue(error.getMessage().contains("本批次只排了 6"), error.getMessage());
        verify(batchItemMapper, never()).updateQty(anyString(), anyString(), anyString(), any());
    }

    /** 每一行都填 0 等于什么都不发，那该走「取消」而不是录单清空。 */
    @Test
    void enterLogisticsRejectsAllZeroQty()
    {
        stubLogisticsUpToItems();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运",
                        Collections.singletonMap(BATCH_DETAIL, BigDecimal.ZERO), USER));
        assertTrue(error.getMessage().contains("什么都不发"), error.getMessage());
    }

    // ---- 付款额度 ----

    /**
     * 发货货值不能超过客户已付金额。
     *
     * <p>订单 10 个 × 12.5 ＝ 125，客户只付了 50，本批次要发 6 个 ＝ 75，超 25，直接拒。</p>
     */
    @Test
    void enterLogisticsRejectsWhenShipValueExceedsPaidAmount()
    {
        stubLogisticsUpToItems();
        order.setPaidAmount(new BigDecimal("50"));
        stubLogisticsAmounts("0");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
        assertTrue(error.getMessage().contains("超出客户已付金额"), error.getMessage());
        // 报错里要把账摊开，不能只说一句「超了」
        assertTrue(error.getMessage().contains("50.00") && error.getMessage().contains("75.00")
                && error.getMessage().contains("25.00"), "报错应说清已收/本次/超出，实际：" + error.getMessage());
        verify(batchMapper, never()).enterLogistics(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }

    /** 调减数量把货值压到额度以内就放行：50 元额度下最多发 4 个（＝50）。 */
    @Test
    void enterLogisticsPassesWhenQtyReducedToFitAllowance()
    {
        stubLogisticsReady();
        order.setPaidAmount(new BigDecimal("50"));

        assertEquals(1, service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运",
                Collections.singletonMap(BATCH_DETAIL, new BigDecimal("4")), USER));
    }

    /**
     * 额度按<b>整单累计</b>算，不是只看本批次。
     *
     * <p>订单已发 4 个（＝50 已经进了 shippedqty），客户付了 100，本批次再发 6 个（＝75），
     * 合计 125 > 100，该拒。只看本批次的话 75 < 100 就放行了，两张批次各自通过、
     * 合起来超发——这正是整单累计要挡的。</p>
     */
    @Test
    void enterLogisticsCountsAlreadyShippedValue()
    {
        stubLogisticsUpToItems();
        order.setPaidAmount(new BigDecimal("100"));
        stubLogisticsAmounts("4");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
        assertTrue(error.getMessage().contains("此前已发/在途占用"), error.getMessage());
        assertTrue(error.getMessage().contains("50.00"), "已发 4 个应算作 50，实际：" + error.getMessage());
    }

    /**
     * 其它<b>已录入/已打印快递单号</b>的兄弟批次也占额度。
     *
     * <p>不数它们的话，同一张订单的两张批次会各自通过、合起来超发。
     * 而还没录单的兄弟批次不算——额度是谁先录谁先占，见 LOGISTICS_COMMITTED_STATES。</p>
     */
    @Test
    void enterLogisticsCountsSiblingBatchesAlreadyHoldingAllowance()
    {
        stubLogisticsUpToItems();
        order.setPaidAmount(new BigDecimal("100"));
        when(orderItemMapper.selectByOrderCodeForUpdate(SHOP, ORDER, ORDER_SUFFIX))
                .thenReturn(Collections.singletonList(orderItem("10", "0")));
        // 另一张批次已经录了单，占掉 4 个 ＝ 50
        when(pendingQtyResolver.resolveInStatuses(eq(SHOP), eq(ORDER), anyList(), eq(SHIP)))
                .thenReturn(Collections.singletonMap(DETAIL, new BigDecimal("4")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
        assertTrue(error.getMessage().contains("超出客户已付金额"), error.getMessage());
        // 本批次自己必须被排除在外，否则 6 个会被重复算一遍
        verify(pendingQtyResolver).resolveInStatuses(eq(SHOP), eq(ORDER), anyList(), eq(SHIP));
    }

    /** 一分钱没收就一分货都发不出去——规则就是「发出去的货值不能超过收到的钱」。 */
    @Test
    void enterLogisticsRejectsWhenNothingPaid()
    {
        stubLogisticsUpToItems();
        order.setPaidAmount(BigDecimal.ZERO);
        stubLogisticsAmounts("0");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
        assertTrue(error.getMessage().contains("超出客户已付金额"), error.getMessage());
    }

    /** 货值正好等于已收金额时放行：判据是「不能大于」，不是「必须小于」。 */
    @Test
    void enterLogisticsAllowsExactlyPaidAmount()
    {
        stubLogisticsReady();
        order.setPaidAmount(new BigDecimal("75.00"));

        assertEquals(1, service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
    }

    /** 付款额度要在订单状态之后才校验：订单都取消了还提示「货值超了」是在指错方向。 */
    @Test
    void enterLogisticsReportsOrderStateBeforeAllowance()
    {
        stubLogisticsUpToItems();
        order.setOrderState(SalesOrder.STATE_CANCELLED);
        order.setPaidAmount(BigDecimal.ZERO);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "顺丰速运", null, USER));
        assertTrue(error.getMessage().contains("订单已取消"), error.getMessage());
    }

    // ================= 打印快递单号 =================

    /** 状态机不允许跳步：备货完成不能直接跳到已打印快递单号，中间少了业务员录单那一步。 */
    @Test
    void printLabelRequiresLogisticsEntered()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PICKED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.printExpressLabel(SHOP, SHIP, BATCH_SUFFIX, USER));
        assertTrue(error.getMessage().contains("不能转为"), error.getMessage());
    }

    /** 打面单仍然不碰库存，货还在仓库里等承运商。 */
    @Test
    void printLabelDoesNotTouchStock()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LOGISTICS_ENTERED));
        when(orderService.findOrderAcrossMonths(SHOP, ORDER)).thenReturn(order);
        when(batchMapper.updateStatus(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(ShipBatch.STATUS_LABEL_PRINTED),
                eq(ShipBatch.STATUS_LOGISTICS_ENTERED), eq(USER), any())).thenReturn(1);

        assertEquals(1, service.printExpressLabel(SHOP, SHIP, BATCH_SUFFIX, USER));
        verifyNoInteractions(outboundService, stockLockCoordinator);
    }

    /**
     * 完成三步的顺序：建出库单 → 审核过账 → 回写已发量。
     *
     * <p>原先这三步挂在「发货」上，且第一步是核销预占。订单不再预占库存，那一步整个去掉了；
     * 剩下三步整体后移到「完成」，因为货发出去到结单之间还有变数，而扣库存不可逆。</p>
     */
    @Test
    void completeExecutesThreeStepsInOrder()
    {
        stubCompleteReady("6");

        String docCode = service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER);

        assertEquals("CK20260819001", docCode);
        InOrder steps = inOrder(outboundService, orderItemMapper);
        steps.verify(outboundService).createOutbound(eq(SHOP), eq(InventoryDocumentTypes.SALES_OUTBOUND), eq(WARE),
                any(StockState.class), any(), any(), eq(SHIP), eq(InventoryDocumentTypes.SHIP_BATCH_SOURCE),
                anyList(), anyString(), eq(USER));
        steps.verify(outboundService).confirmOutbound(SHOP, "CK20260819001", BATCH_SUFFIX, true, USER);
        steps.verify(orderItemMapper).increaseShippedQty(SHOP, DETAIL, ORDER_SUFFIX, new BigDecimal("6"));
    }

    /**
     * 整批加锁必须排在建出库单之前。
     *
     * <p>{@code lockForTransaction} 的排序只在<b>单次调用内</b>生效，而锁攒到事务结束才放，
     * 所以一个事务只能有一个加锁点；分两次锁，事务之间照样能形成顺序反转。
     * 见 OrderStockLockCoordinator。</p>
     */
    @Test
    void completeLocksEveryStockRowBeforePosting()
    {
        stubCompleteReady("6");

        service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER);

        InOrder steps = inOrder(stockLockCoordinator, outboundService);
        steps.verify(stockLockCoordinator).lockForShip(eq(SHOP), eq(WARE), anyList());
        steps.verify(outboundService).createOutbound(eq(SHOP), eq(InventoryDocumentTypes.SALES_OUTBOUND), eq(WARE),
                any(StockState.class), any(), any(), eq(SHIP), eq(InventoryDocumentTypes.SHIP_BATCH_SOURCE),
                anyList(), anyString(), eq(USER));
    }

    /** 完成后要重算订单进度，否则订单状态永远停在已备货。 */
    @Test
    void completeRefreshesOrderProgress()
    {
        stubCompleteReady("6");
        service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER);
        verify(orderService).refreshShipProgress(SHOP, ORDER, ORDER_SUFFIX);
    }

    /**
     * 出库单要带上订单明细代码：审核挑批次时靠它认出自己的定制批次，
     * 别人的一律跳过。不带的话定制件的专属绑定就白做了。
     */
    @Test
    void completePassesOrderDetailCodeToOutbound()
    {
        stubCompleteReady("6");

        service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IOutboundService.OutboundItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(outboundService).createOutbound(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), captor.capture(), anyString(), anyString());
        assertEquals(DETAIL, captor.getValue().get(0).getOrderDetailCode());
    }

    /** 面单还没打就不能完成：库存只能在货真被拉走之后才扣。 */
    @Test
    void completeRequiresLabelPrintedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PICK_PRINTED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeBatch(SHOP, SHIP, BATCH_SUFFIX, null, null, USER));
        assertTrue(error.getMessage().contains("还没打印快递单号"), error.getMessage());
        verify(outboundService, never()).createOutbound(anyString(), anyString(), anyString(), any(), any(), any(),
                any(), any(), anyList(), anyString(), anyString());
    }

    /**
     * 录完快递单号之后订单侧不能直接取消，报错要指向「驳回」。
     *
     * <p>不是因为撤不回来——那时候货其实还在仓库里。是因为单子上带的是业务员自己填的
     * 面单信息、仓库正按它打单贴箱，该由仓库先驳回退成待确认（顺带写清为什么），
     * 业务员再决定是改还是废。订单侧一键作废的话仓库只会看见行凭空消失。</p>
     */
    @Test
    void cancelRejectsBatchAfterLogisticsEntered()
    {
        for (String status : Arrays.asList(ShipBatch.STATUS_LOGISTICS_ENTERED, ShipBatch.STATUS_LABEL_PRINTED))
        {
            when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX)).thenReturn(pendingBatch(status));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.cancelBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
            assertTrue(error.getMessage().contains("驳回"), status + " 的报错应指向驳回，实际：" + error.getMessage());
        }
    }

    /**
     * 「备货完成」还取消得掉。
     *
     * <p>这个口子是必需的：订单要关闭/取消/撤审，后端要求先把未完成的批次清掉
     * （assertNoPendingBatches）。只留「待确认」会让「批次已提交、仓库还没发货、
     * 客户临时要取消」变成批次和订单互相卡死的死局。</p>
     */
    @Test
    void cancelAllowsPickedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PICKED));
        when(batchMapper.updateStatus(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(ShipBatch.STATUS_CANCELLED),
                eq(ShipBatch.STATUS_PICKED), eq(USER), any())).thenReturn(1);

        assertEquals(1, service.cancelBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
        verifyNoInteractions(outboundService, stockLockCoordinator);
    }

    /** 已完成的批次不能取消：货被承运商拉走了、库存也扣了，只能走退货流程。 */
    @Test
    void cancelRejectsCompletedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_COMPLETED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
        assertTrue(error.getMessage().contains("退货流程"), error.getMessage());
    }

    /**
     * 提交给仓库、甚至打完单的批次仍然取消得掉，取消也<b>不</b>退任何库存占用：
     * 到那一步为止批次一分库存都没碰过，扣减只发生在「完成」那一刻的出库单上。
     */
    @Test
    void cancelSubmittedBatchDoesNotTouchStock()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_SUBMITTED));
        when(batchMapper.updateStatus(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(ShipBatch.STATUS_CANCELLED),
                eq(ShipBatch.STATUS_SUBMITTED), eq(USER), any())).thenReturn(1);

        assertEquals(1, service.cancelBatch(SHOP, SHIP, BATCH_SUFFIX, USER));
        verifyNoInteractions(outboundService, stockLockCoordinator);
    }

    /** 状态机不允许跳步：待确认不能直接跳到已打印备货单，业务员还没提交给仓库。 */
    @Test
    void printPickListRequiresSubmittedStatus()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_PENDING));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.printPickList(SHOP, SHIP, BATCH_SUFFIX, USER));
        assertTrue(error.getMessage().contains("不能转为"));
    }

    @Test
    void rejectsBadTableSuffix()
    {
        assertThrows(ServiceException.class, () -> service.selectBatchDetail(SHOP, SHIP, "20260"));
    }

    // ================= 录单时的物流信息必填 =================

    /**
     * 承运商和快递单号都没有就不许录单。
     *
     * <p>这一步的语义就是「这批货交给谁、单号多少」，那时候这两样一定知道；留空过去了，
     * 货出门之后查不到是谁拉走的，客户催货只能靠人回忆。</p>
     */
    @Test
    void enterLogisticsRequiresCarrierAndNo()
    {
        stubLogisticsUpToItems();
        order.setReceiveType(SalesOrder.RECEIVE_TYPE_FORWARDER);

        ServiceException noCarrier = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, "SF123", "  ", null, USER));
        assertTrue(noCarrier.getMessage().contains("承运商"), noCarrier.getMessage());

        ServiceException noLogistics = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, null, "顺丰", null, USER));
        assertTrue(noLogistics.getMessage().contains("快递单号"), noLogistics.getMessage());

        // 缺了就没推动状态，批次还停在备货完成
        verify(batchMapper, never()).enterLogistics(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }

    /**
     * 收货方式没填的历史订单按「要填」算。
     *
     * <p>判据写成 {@code RECEIVE_TYPE_PICKUP.equals(order.getReceiveType())} 而不是反过来，
     * 就是为了让 null 落进必填那一支——拿不准的时候按要填算，比放空过去安全。</p>
     */
    @Test
    void enterLogisticsRequiresInfoWhenReceiveTypeUnknown()
    {
        stubLogisticsUpToItems();
        order.setReceiveType(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, null, null, null, USER));
        assertTrue(error.getMessage().contains("承运商"), error.getMessage());
    }

    /**
     * 「上门提货」不要求承运商和快递单号：客户自己开车来仓库拉，本来就没有这两样。
     *
     * <p>硬要求只会逼人往框里敲「无」「客户自提」，而假数据比空值更糟——填过之后
     * 就分不清「本来就没有」和「忘了填」。口径与订单侧一致：那边的上门提货也不要求
     * 收件地址（见 SalesOrder.RECEIVE_TYPE_PICKUP 的注释）。</p>
     */
    @Test
    void enterLogisticsSkipsRequirementForSelfPickup()
    {
        stubLogisticsReady();
        order.setReceiveType(SalesOrder.RECEIVE_TYPE_PICKUP);

        assertEquals(1, service.enterLogistics(SHOP, SHIP, BATCH_SUFFIX, null, null, null, USER));
        verify(batchMapper).enterLogistics(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(null), eq(null),
                eq(USER), any());
    }

    // ================= 驳回 =================

    /**
     * 驳回是这套状态机唯一往回走的一步：货还没被承运商取走的五格都能退回「待确认」。
     *
     * <p>「已打印快递单号」也能驳是整件事的重点——那一格只表示贴了面单在等人上门，
     * 货还在仓库里。装错货、贴错单都发生在这段时间，不该逼人先点「完成」把错的货扣掉
     * 再走一遍销售退货（那会在流水里留下一对根本没发生过的出入库）。</p>
     */
    @Test
    void rejectAcceptsEveryStateBeforeGoodsLeave()
    {
        for (String from : Arrays.asList(ShipBatch.STATUS_SUBMITTED, ShipBatch.STATUS_PICK_PRINTED,
                ShipBatch.STATUS_PICKED, ShipBatch.STATUS_LOGISTICS_ENTERED,
                ShipBatch.STATUS_LABEL_PRINTED))
        {
            when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX)).thenReturn(pendingBatch(from));
            when(batchMapper.rejectToPending(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX), eq(from), eq(USER),
                    any(), anyString())).thenReturn(1);

            assertEquals(1, service.rejectBatch(SHOP, SHIP, BATCH_SUFFIX, "装错货了", USER),
                    from + " 应该可以驳回");
        }
    }

    /** 驳回一分库存都不碰：到「已打印快递单号」为止这条链本来就没开过出库单。 */
    @Test
    void rejectDoesNotTouchStockOrOrder()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LABEL_PRINTED));
        when(batchMapper.rejectToPending(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX),
                eq(ShipBatch.STATUS_LABEL_PRINTED), eq(USER), any(), anyString())).thenReturn(1);

        service.rejectBatch(SHOP, SHIP, BATCH_SUFFIX, "3 号箱装成了另一款", USER);

        verifyNoInteractions(outboundService, stockLockCoordinator);
        // 订单已发量只在「完成」那一步才加，驳回自然也不用回退
        verify(orderItemMapper, never()).increaseShippedQty(anyString(), anyString(), anyString(), any());
    }

    /**
     * 驳回不看订单状态，理由同 {@link #cancelIgnoresOrderState()}：这是往回撤的动作，
     * 撤不动才是麻烦——订单万一已经离开可发货区间，这张批次更得能退回去让人处理掉。
     */
    @Test
    void rejectIgnoresOrderState()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_LABEL_PRINTED));
        when(batchMapper.rejectToPending(eq(SHOP), eq(SHIP), eq(BATCH_SUFFIX),
                eq(ShipBatch.STATUS_LABEL_PRINTED), eq(USER), any(), anyString())).thenReturn(1);

        assertEquals(1, service.rejectBatch(SHOP, SHIP, BATCH_SUFFIX, "客户改主意了", USER));
        verify(orderService, never()).findOrderAcrossMonths(anyString(), anyString());
    }

    /** 已完成的货已经被承运商取走、库存也扣了，驳不回来，只能走退货。 */
    @Test
    void rejectRefusesCompletedBatch()
    {
        when(batchMapper.selectByCodeForUpdate(SHOP, SHIP, BATCH_SUFFIX))
                .thenReturn(pendingBatch(ShipBatch.STATUS_COMPLETED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rejectBatch(SHOP, SHIP, BATCH_SUFFIX, "装错了", USER));
        assertTrue(error.getMessage().contains("退货"));
        verify(batchMapper, never()).rejectToPending(anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyString());
    }

    /** 原因必填，而且要在查批次之前就拦下——空原因的驳回对业务员毫无用处。 */
    @Test
    void rejectRequiresReason()
    {
        assertThrows(ServiceException.class,
                () -> service.rejectBatch(SHOP, SHIP, BATCH_SUFFIX, "   ", USER));
        verify(batchMapper, never()).selectByCodeForUpdate(anyString(), anyString(), anyString());
    }
}
