package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.PurchaseReceiveItem;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.mapper.PurchaseReceiveItemMapper;
import com.ruoyi.system.mapper.PurchaseReceiveMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.service.IPurchaseReceiveService;

/**
 * 采购发货的登记、行级状态派生、在途量与短装口径测试。
 *
 * <p>入库侧（实际到货数量、超发放行、驳回回滚）在 PurchaseInboundServiceImpl，不在本类。</p>
 *
 * <p>（原先这里还引了一份 2026-08-17 的采购发货/入库拆分设计稿，那份已经不在仓库里了，
 * 不再留死链接。）</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseReceiveServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String SUFFIX = "202608";
    private static final String RECEIVE = "DH20260817001";
    private static final String PURCHASE = "CG20260817001";
    private static final String DETAIL = "CGD20260817001";
    private static final String USER = "tester";

    @Mock private PurchaseReceiveMapper receiveMapper;
    @Mock private PurchaseReceiveItemMapper itemMapper;
    @Mock private PurchaseOrderMapper purchaseMapper;
    @Mock private PurchaseOrderItemMapper purchaseItemMapper;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;
    @Mock private InventoryWarehouseMapper warehouseMapper;
    // 发货日期可以指向未来月份，登记前要按需补建目标月表
    @Mock private MonthlyTableInitializer monthlyTables;

    @InjectMocks private PurchaseReceiveServiceImpl service;

    private PurchaseOrder order;

    @BeforeEach
    void setUp()
    {
        order = new PurchaseOrder();
        order.setShopCode(SHOP);
        order.setPurchaseCode(PURCHASE);
        order.setStatus("approved");
        order.setFreightType("collect");
    }

    private PurchaseOrderItem sourceItem(BigDecimal qty, BigDecimal received)
    {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setDetailCode(DETAIL);
        item.setGoodsCode("G001");
        item.setGoodsName("测试商品");
        item.setUnit("个");
        item.setQty(qty);
        item.setReceivedQty(received);
        item.setUnitPrice(new BigDecimal("10.00"));
        return item;
    }

    private PurchaseReceiveItem inputItem(BigDecimal qty)
    {
        PurchaseReceiveItem item = new PurchaseReceiveItem();
        item.setPurchaseDetailCode(DETAIL);
        item.setQty(qty);
        return item;
    }

    /** 构造一条已落库的发货明细，actualQty / inventoryDetailCode 决定行状态。 */
    private PurchaseReceiveItem storedItem(BigDecimal qty, BigDecimal actualQty, String inventoryDetailCode)
    {
        PurchaseReceiveItem item = new PurchaseReceiveItem();
        item.setShopCode(SHOP);
        item.setDetailCode("DHD001");
        item.setReceiveCode(RECEIVE);
        item.setPurchaseDetailCode(DETAIL);
        item.setGoodsCode("G001");
        item.setGoodsName("测试商品");
        item.setQty(qty);
        item.setActualQty(actualQty);
        item.setInventoryDetailCode(inventoryDetailCode);
        return item;
    }

    private PurchaseReceive command(BigDecimal qty)
    {
        PurchaseReceive receive = new PurchaseReceive();
        receive.setPurchaseCode(PURCHASE);
        receive.setSendDate(sendDate());
        receive.setItems(new ArrayList<>(Collections.singletonList(inputItem(qty))));
        return receive;
    }

    /** 固定在 202608，与 SUFFIX 对上，避免测试随当前月份漂移。 */
    private Date sendDate()
    {
        try
        {
            return new SimpleDateFormat("yyyy-MM-dd").parse("2026-08-17");
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // 登记发货
    // ------------------------------------------------------------------

    @Test
    void createReceive_按发货日期决定月表并落待入库状态()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        PurchaseReceive created = service.createReceive(SHOP, command(new BigDecimal("60")), USER);

        assertEquals(SUFFIX, created.getTableSuffix());
        assertEquals(IPurchaseReceiveService.STATUS_PENDING, created.getStatus());
        assertEquals(new BigDecimal("60"), created.getTotalQty());
        verify(receiveMapper).insert(created, SUFFIX);
        verify(itemMapper).batchInsert(created.getItems(), SUFFIX);
    }

    @Test
    void createReceive_不接受前端传来的签收人与签收时间()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setReceiveUser("偷偷传的签收人");
        input.setReceiveTime(new Date());

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertNull(created.getReceiveUser());
        assertNull(created.getReceiveTime());
    }

    @Test
    void createReceive_明细不落实际到货数量与批次序列号()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        PurchaseReceive input = command(new BigDecimal("10"));
        input.getItems().get(0).setBatchCode("P20260817");
        input.getItems().get(0).setSerialNumbers("SN001");

        PurchaseReceive created = service.createReceive(SHOP, input, USER);
        PurchaseReceiveItem saved = created.getItems().get(0);

        // actualQty 必须保持 null：行状态判定靠它区分「未入库」和「收到 0 件」
        assertNull(saved.getActualQty());
        assertNull(saved.getBatchCode());
        assertNull(saved.getSerialNumbers());
    }

    @Test
    void createReceive_预计到货数量超过采购剩余可发量时拒绝()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), new BigDecimal("80"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createReceive(SHOP, command(new BigDecimal("30")), USER));

        assertTrue(error.getMessage().contains("预计到货数量超过采购剩余可发数量"));
    }

    @Test
    void createReceive_加上在途量超过采购剩余可发量时拒绝()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        // 另一张发货单已占用 70 的在途量
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(new BigDecimal("70"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createReceive(SHOP, command(new BigDecimal("40")), USER));

        assertTrue(error.getMessage().contains("本次发货加在途数量超过采购剩余可发数量"));
    }

    @Test
    void createReceive_采购订单未审核时拒绝()
    {
        order.setStatus("draft");
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createReceive(SHOP, command(new BigDecimal("10")), USER));

        assertTrue(error.getMessage().contains("采购订单状态不允许登记发货"));
    }

    @Test
    void createReceive_非到付时预估运费归零()
    {
        order.setFreightType("free");
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setEstFreightAmount(new BigDecimal("88.00"));

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertEquals(0, created.getEstFreightAmount().compareTo(BigDecimal.ZERO));
    }

    /** 到付的实付归入库员，采购员就算硬塞一个金额也不能落到 freightamount 上 */
    @Test
    void createReceive_到付时实付运费不由采购员写()
    {
        stubCreate();

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setEstFreightAmount(new BigDecimal("88.00"));
        input.setFreightAmount(new BigDecimal("70.00"));

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertEquals(0, created.getEstFreightAmount().compareTo(new BigDecimal("88.00")));
        assertEquals(0, created.getFreightAmount().compareTo(BigDecimal.ZERO));
        verify(purchaseMapper, never()).increaseActualFreight(anyString(), anyString(), any());
    }

    /** 寄付的运费供应商发货时已付清，采购员填的就是实付，同时滚进采购订单的实际运费 */
    @Test
    void createReceive_寄付时实付运费由采购员填并滚进采购订单()
    {
        order.setFreightType("prepaid");
        stubCreate();

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setFreightAmount(new BigDecimal("66.00"));

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertEquals(0, created.getFreightAmount().compareTo(new BigDecimal("66.00")));
        // 预估对寄付没有意义
        assertEquals(0, created.getEstFreightAmount().compareTo(BigDecimal.ZERO));
        verify(purchaseMapper).increaseActualFreight(SHOP, PURCHASE, new BigDecimal("66.00"));
    }

    @Test
    void createReceive_运费为负时拒绝()
    {
        order.setFreightType("prepaid");
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setFreightAmount(new BigDecimal("-1"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.createReceive(SHOP, input, USER));
        assertTrue(error.getMessage().contains("运费不能为负数"));
    }

    /** 寄付的实付是采购员记在本单上的账，整单取消要从采购订单撤回 */
    @Test
    void cancelReceive_寄付时撤回已记的实付运费()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setPurchaseCode(PURCHASE);
        stored.setStatus(IPurchaseReceiveService.STATUS_PENDING);
        stored.setFreightType("prepaid");
        stored.setFreightAmount(new BigDecimal("66.00"));
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), null, null)));
        when(receiveMapper.deleteDraft(SHOP, RECEIVE, SUFFIX)).thenReturn(1);

        assertEquals(1, service.cancelReceive(SHOP, RECEIVE, SUFFIX, USER));
        verify(purchaseMapper).increaseActualFreight(SHOP, PURCHASE, new BigDecimal("-66.00"));
    }

    /** 到付的实付是入库员真付出去的钱，沿用「驳回也不扣回」的口径，取消发货单也不撤 */
    @Test
    void cancelReceive_到付时不撤回入库员已付的运费()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setPurchaseCode(PURCHASE);
        stored.setStatus(IPurchaseReceiveService.STATUS_PENDING);
        stored.setFreightType("collect");
        stored.setFreightAmount(new BigDecimal("66.00"));
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), null, null)));
        when(receiveMapper.deleteDraft(SHOP, RECEIVE, SUFFIX)).thenReturn(1);

        assertEquals(1, service.cancelReceive(SHOP, RECEIVE, SUFFIX, USER));
        verify(purchaseMapper, never()).increaseActualFreight(anyString(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 在途量批量汇总：发货页「已处理」列与下拉过滤都依赖它
    // ------------------------------------------------------------------

    @Test
    void selectPendingQtyByDetails_跨月表累加()
    {
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Arrays.asList("202607", "202608"));
        when(itemMapper.selectPendingQtyByDetails(SHOP, Arrays.asList(DETAIL, "CGD002"), "202607"))
                .thenReturn(Collections.singletonList(pendingRow(DETAIL, new BigDecimal("3"))));
        when(itemMapper.selectPendingQtyByDetails(SHOP, Arrays.asList(DETAIL, "CGD002"), "202608"))
                .thenReturn(Arrays.asList(pendingRow(DETAIL, new BigDecimal("2")),
                        pendingRow("CGD002", new BigDecimal("7"))));

        Map<String, BigDecimal> result = service.selectPendingQtyByDetails(SHOP, Arrays.asList(DETAIL, "CGD002"));

        assertEquals(0, result.get(DETAIL).compareTo(new BigDecimal("5")));
        assertEquals(0, result.get("CGD002").compareTo(new BigDecimal("7")));
    }

    /** 没有在途量的明细不出现在结果里，调用方按 0 处理 */
    @Test
    void selectPendingQtyByDetails_无在途时不返回该明细()
    {
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQtyByDetails(SHOP, Collections.singletonList(DETAIL), SUFFIX))
                .thenReturn(Collections.emptyList());

        assertTrue(service.selectPendingQtyByDetails(SHOP, Collections.singletonList(DETAIL)).isEmpty());
    }

    @Test
    void selectPendingQtyByDetails_明细为空时不查库()
    {
        assertTrue(service.selectPendingQtyByDetails(SHOP, Collections.emptyList()).isEmpty());
        verify(itemMapper, never()).selectReceiveTableSuffixes();
    }

    private PurchaseReceiveItem pendingRow(String purchaseDetailCode, BigDecimal qty)
    {
        PurchaseReceiveItem row = new PurchaseReceiveItem();
        row.setPurchaseDetailCode(purchaseDetailCode);
        row.setQty(qty);
        return row;
    }

    /** createReceive 走通一次所需的全部桩 */
    private void stubCreate()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");
    }

    // ------------------------------------------------------------------
    // 建议入库仓库：单头字段，一张发货单只入一个仓（决议 B6-1）
    // ------------------------------------------------------------------

    @Test
    void createReceive_建议仓库不存在时拒绝()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(warehouseMapper.selectWarehouseByCode(SHOP, "WH-NOPE")).thenReturn(null);

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setWarehouseCode("WH-NOPE");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createReceive(SHOP, input, USER));

        assertTrue(error.getMessage().contains("建议入库仓库"));
    }

    @Test
    void createReceive_建议仓库名称以主数据为准不信任前端()
    {
        InventoryWarehouse warehouse = new InventoryWarehouse();
        warehouse.setWareCode("WH01");
        warehouse.setWareName("主仓库");
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(warehouseMapper.selectWarehouseByCode(SHOP, "WH01")).thenReturn(warehouse);
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setWarehouseCode("WH01");
        input.setWarehouseName("前端瞎填的名字");

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertEquals("主仓库", created.getWarehouseName());
        assertEquals("主仓库", created.getItems().get(0).getWarehouseName());
    }

    @Test
    void createReceive_单头仓库铺到全部明细行()
    {
        InventoryWarehouse warehouse = new InventoryWarehouse();
        warehouse.setWareCode("WH01");
        warehouse.setWareName("主仓库");

        PurchaseOrderItem second = sourceItem(new BigDecimal("50"), BigDecimal.ZERO);
        second.setId(2L);
        second.setDetailCode("CGD20260817002");
        second.setGoodsCode("G002");
        second.setGoodsName("第二个商品");

        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Arrays.asList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO), second));
        when(warehouseMapper.selectWarehouseByCode(SHOP, "WH01")).thenReturn(warehouse);
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(anyString(), anyString(), eq(null), eq(SUFFIX)))
                .thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001", "DHD002");

        PurchaseReceive input = command(new BigDecimal("10"));
        PurchaseReceiveItem line2 = new PurchaseReceiveItem();
        line2.setPurchaseDetailCode("CGD20260817002");
        line2.setQty(new BigDecimal("5"));
        input.getItems().add(line2);
        input.setWarehouseCode("WH01");

        PurchaseReceive created = service.createReceive(SHOP, input, USER);

        assertEquals(2, created.getItems().size());
        for (PurchaseReceiveItem item : created.getItems())
        {
            assertEquals("WH01", item.getWarehouseCode());
            assertEquals("主仓库", item.getWarehouseName());
        }
    }

    @Test
    void createReceive_单头仓库留空时全部明细都不落仓库()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        // 采购员没选仓库，留给入库员在采购入库时定
        PurchaseReceive created = service.createReceive(SHOP, command(new BigDecimal("10")), USER);

        assertNull(created.getWarehouseCode());
        assertNull(created.getItems().get(0).getWarehouseCode());
        // 没填仓库就不该去查仓库主数据
        verify(warehouseMapper, never()).selectWarehouseByCode(anyString(), anyString());
    }

    @Test
    void selectReceiveDetail_从明细行反推单头仓库()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        PurchaseReceiveItem line = storedItem(new BigDecimal("10"), null, null);
        line.setWarehouseCode("WH01");
        line.setWarehouseName("主仓库");
        when(receiveMapper.selectByCode(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX))
                .thenReturn(Collections.singletonList(line));

        PurchaseReceive detail = service.selectReceiveDetail(SHOP, RECEIVE, SUFFIX);

        assertEquals("WH01", detail.getWarehouseCode());
        assertEquals("主仓库", detail.getWarehouseName());
    }

    // ------------------------------------------------------------------
    // 行级状态派生
    // ------------------------------------------------------------------

    @Test
    void selectReceiveDetail_按实际数量与入库明细号派生行状态()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        when(receiveMapper.selectByCode(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Arrays.asList(
                storedItem(new BigDecimal("10"), null, null),                        // 未处理
                storedItem(new BigDecimal("10"), new BigDecimal("10"), null),        // 入库中
                storedItem(new BigDecimal("10"), new BigDecimal("9"), "RKD001")));   // 已入库

        PurchaseReceive detail = service.selectReceiveDetail(SHOP, RECEIVE, SUFFIX);

        assertEquals(PurchaseReceiveServiceImpl.LINE_PENDING, detail.getItems().get(0).getLineStatus());
        assertEquals(PurchaseReceiveServiceImpl.LINE_INBOUNDING, detail.getItems().get(1).getLineStatus());
        assertEquals(PurchaseReceiveServiceImpl.LINE_STOCKED, detail.getItems().get(2).getLineStatus());
    }

    @Test
    void refreshStatus_全部未处理为待入库()
    {
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Arrays.asList(
                storedItem(new BigDecimal("10"), null, null),
                storedItem(new BigDecimal("10"), null, null)));

        assertEquals(IPurchaseReceiveService.STATUS_PENDING,
                service.refreshStatus(SHOP, RECEIVE, SUFFIX, USER));
    }

    @Test
    void refreshStatus_全部已过账为已入库()
    {
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Arrays.asList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), "RKD001"),
                storedItem(new BigDecimal("10"), new BigDecimal("8"), "RKD002")));

        assertEquals(IPurchaseReceiveService.STATUS_STOCKED,
                service.refreshStatus(SHOP, RECEIVE, SUFFIX, USER));
    }

    /** 只有全部行过账才算已入库，剩下一律待入库——发货单是整单入库的，没有中间态 */
    @Test
    void refreshStatus_有行没过账就还是待入库()
    {
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Arrays.asList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), "RKD001"),
                storedItem(new BigDecimal("10"), null, null)));

        assertEquals(IPurchaseReceiveService.STATUS_PENDING,
                service.refreshStatus(SHOP, RECEIVE, SUFFIX, USER));
    }

    /** 入库单建了还没审核，货没进仓库，仍是待入库 */
    @Test
    void refreshStatus_全部行在待审核仍是待入库()
    {
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Arrays.asList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), null),
                storedItem(new BigDecimal("10"), new BigDecimal("10"), null)));

        assertEquals(IPurchaseReceiveService.STATUS_PENDING,
                service.refreshStatus(SHOP, RECEIVE, SUFFIX, USER));
    }

    @Test
    void refreshStatus_把派生结果落库()
    {
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), "RKD001")));

        service.refreshStatus(SHOP, RECEIVE, SUFFIX, USER);

        verify(receiveMapper).updateStatusUnconditional(SHOP, RECEIVE, SUFFIX,
                IPurchaseReceiveService.STATUS_STOCKED, USER);
    }

    // ------------------------------------------------------------------
    // 修改与取消：只要有行被入库员碰过就锁住
    // ------------------------------------------------------------------

    @Test
    void updateDraft_非待入库状态拒绝修改()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setStatus(IPurchaseReceiveService.STATUS_STOCKED);
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setReceiveCode(RECEIVE);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, input, SUFFIX, USER));

        assertTrue(error.getMessage().contains("发货单已开始入库，不能修改"));
    }

    @Test
    void updateDraft_状态是待入库但有行已被占用时仍拒绝()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setStatus(IPurchaseReceiveService.STATUS_PENDING);
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), null)));

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setReceiveCode(RECEIVE);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, input, SUFFIX, USER));

        assertTrue(error.getMessage().contains("已开始入库"));
    }

    @Test
    void cancelReceive_全部未处理时放行()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setStatus(IPurchaseReceiveService.STATUS_PENDING);
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), null, null)));
        when(receiveMapper.deleteDraft(SHOP, RECEIVE, SUFFIX)).thenReturn(1);

        assertEquals(1, service.cancelReceive(SHOP, RECEIVE, SUFFIX, USER));
        verify(itemMapper).deleteByReceiveCode(SHOP, RECEIVE, SUFFIX);
    }

    /**
     * 单头状态只有两个，入库单建了还没审核时单头仍是「待入库」，
     * 拦截只能靠行状态。这条保证那道行级校验没被漏掉。
     */
    @Test
    void updateDraft_单头待入库但有行在入库中时拒绝修改()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setStatus(IPurchaseReceiveService.STATUS_PENDING);
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(Collections.singletonList(
                storedItem(new BigDecimal("10"), new BigDecimal("10"), null)));

        PurchaseReceive input = command(new BigDecimal("10"));
        input.setReceiveCode(RECEIVE);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(SHOP, input, SUFFIX, USER));
        assertTrue(error.getMessage().contains("已开始入库"));
    }

    @Test
    void cancelReceive_已开始入库时拒绝且不删明细()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        stored.setStatus(IPurchaseReceiveService.STATUS_STOCKED);
        when(receiveMapper.selectByCodeForUpdate(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);

        assertThrows(ServiceException.class, () -> service.cancelReceive(SHOP, RECEIVE, SUFFIX, USER));
        verify(itemMapper, never()).deleteByReceiveCode(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // 跨月表查询
    // ------------------------------------------------------------------

    @Test
    void findReceiveAcrossMonths_命中后带出月表后缀()
    {
        PurchaseReceive stored = new PurchaseReceive();
        stored.setReceiveCode(RECEIVE);
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Arrays.asList("202607", SUFFIX));
        when(receiveMapper.selectByCode(SHOP, RECEIVE, "202607")).thenReturn(null);
        when(receiveMapper.selectByCode(SHOP, RECEIVE, SUFFIX)).thenReturn(stored);
        when(itemMapper.selectByReceiveCode(SHOP, RECEIVE, SUFFIX)).thenReturn(new ArrayList<>());

        PurchaseReceive found = service.findReceiveAcrossMonths(SHOP, RECEIVE);

        assertEquals(SUFFIX, found.getTableSuffix());
    }

    @Test
    void findReceiveAcrossMonths_全部月表都没有时返回null()
    {
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(receiveMapper.selectByCode(SHOP, RECEIVE, SUFFIX)).thenReturn(null);

        assertNull(service.findReceiveAcrossMonths(SHOP, RECEIVE));
    }

    @Test
    void findReceiveAcrossMonths_跳过非法月表后缀()
    {
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Arrays.asList("bad", "202613", SUFFIX));
        when(receiveMapper.selectByCode(SHOP, RECEIVE, SUFFIX)).thenReturn(null);

        assertNull(service.findReceiveAcrossMonths(SHOP, RECEIVE));
        // 非法后缀不能拼进 SQL
        verify(receiveMapper, never()).selectByCode(SHOP, RECEIVE, "bad");
        verify(receiveMapper, never()).selectByCode(SHOP, RECEIVE, "202613");
    }

    @Test
    void selectPendingShipments_汇总全部月表并带上后缀()
    {
        PurchaseReceive row = new PurchaseReceive();
        row.setReceiveCode(RECEIVE);
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Arrays.asList("202607", SUFFIX));
        when(receiveMapper.selectPendingList(any(PurchaseReceive.class), eq("202607")))
                .thenReturn(new ArrayList<>());
        when(receiveMapper.selectPendingList(any(PurchaseReceive.class), eq(SUFFIX)))
                .thenReturn(new ArrayList<>(Collections.singletonList(row)));

        List<PurchaseReceive> result = service.selectPendingShipments(SHOP);

        assertEquals(1, result.size());
        assertEquals(SUFFIX, result.get(0).getTableSuffix());
    }

    @Test
    void selectShortageItems_采购单号为空时拒绝()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.selectShortageItems(SHOP, "  "));

        assertTrue(error.getMessage().contains("采购单号不能为空"));
    }

    // ------------------------------------------------------------------
    // 月表后缀防注入
    // ------------------------------------------------------------------

    @Test
    void selectReceiveList_非法月表后缀拒绝()
    {
        PurchaseReceive query = new PurchaseReceive();
        assertThrows(ServiceException.class, () -> service.selectReceiveList(query, "2026-08"));
        assertThrows(ServiceException.class, () -> service.selectReceiveList(query, "202613"));
        assertThrows(ServiceException.class, () -> service.selectReceiveList(query, "'; drop table x; --"));
    }

    @Test
    void createReceive_发货日期决定的月份写进月表后缀()
    {
        when(purchaseMapper.selectPurchaseOrderByCodeForUpdate(SHOP, PURCHASE)).thenReturn(order);
        when(purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(SHOP, PURCHASE))
                .thenReturn(Collections.singletonList(sourceItem(new BigDecimal("100"), BigDecimal.ZERO)));
        when(itemMapper.selectReceiveTableSuffixes()).thenReturn(Collections.singletonList(SUFFIX));
        when(itemMapper.selectPendingQty(SHOP, DETAIL, null, SUFFIX)).thenReturn(BigDecimal.ZERO);
        when(codeGenerator.nextPurchaseReceiveCode()).thenReturn(RECEIVE);
        when(codeGenerator.nextPurchaseReceiveDetailCode()).thenReturn("DHD001");

        service.createReceive(SHOP, command(new BigDecimal("10")), USER);

        ArgumentCaptor<String> suffixCaptor = ArgumentCaptor.forClass(String.class);
        verify(receiveMapper).insert(any(PurchaseReceive.class), suffixCaptor.capture());
        assertEquals(SUFFIX, suffixCaptor.getValue());
    }
}
