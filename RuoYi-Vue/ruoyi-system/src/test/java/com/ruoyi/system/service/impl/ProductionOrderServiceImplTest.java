package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.ProductionExecutionRequest;
import com.ruoyi.system.domain.ProductionLog;
import com.ruoyi.system.domain.ProductionMaterial;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.ProductionOrderAllocation;
import com.ruoyi.system.domain.ProductionOrderDetail;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.ProductionLogMapper;
import com.ruoyi.system.mapper.ProductionMaterialMapper;
import com.ruoyi.system.mapper.ProductionOrderDetailMapper;
import com.ruoyi.system.mapper.ProductionOrderMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.ISalesOrderService;

/**
 * 生产工单三段式物料流转（领料—消耗—退料）的状态机与成本口径测试。
 *
 * <p>对照设计说明书 4.4 流程1、流程2。二维库存模型下车间在制品 = 良品·不在库。</p>
 *
 * <p>（原先这里还引了一份 2026-08-14 的三段式物料流转设计稿，那份已经不在仓库里了，
 * 不再留死链接。）</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductionOrderServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String CODE = "SC20260818001";
    private static final String MATERIAL = "SCWL001";
    private static final String OUTPUT = "SCMX001";
    private static final String WARE = "CK001";
    private static final String PRODUCT_WARE = "CK002";
    private static final String USER = "tester";
    /** 订单驱动用的销售订单：单号、所在月表、明细代码 */
    private static final String ORDER_CODE = "XS20260820001";
    private static final String ORDER_MONTH = "202608";
    private static final String ORDER_DETAIL = "XSMX20260820001";

    /** 车间在制品：料领出去就不在仓库里了 */
    private static final StockState IN_PRODUCTION = StockState.of(QualityFlag.GOOD, OnhandFlag.OUT);

    @Mock private ProductionOrderMapper productionOrderMapper;
    @Mock private ProductionOrderDetailMapper detailMapper;
    @Mock private ProductionMaterialMapper materialMapper;
    @Mock private ProductionLogMapper logMapper;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;
    @Mock private InventoryDocumentTableRouter tableRouter;
    @Mock private IDocumentService documentService;
    @Mock private ISalesOrderService salesOrderService;
    @Mock private InventoryGoodsMapper goodsMapper;
    @Mock private IInventoryBatchService batchService;

    @InjectMocks private ProductionOrderServiceImpl service;

    private ProductionOrder order;

    @BeforeEach
    void setUp()
    {
        order = new ProductionOrder();
        order.setShopCode(SHOP);
        order.setProductionCode(CODE);
        order.setProductCode("P001");
        order.setProductName("成品A");
        order.setPlanYQty(new BigDecimal("10"));
        // 成品默认不做批次追踪；要批次或序列号的用例各自覆盖这条桩
        lenient().when(goodsMapper.selectGoodsByCode(eq(SHOP), anyString())).thenReturn(goods("none"));
        // 完工回写带状态条件，更新 0 行会被当成「已经完工过」拒掉，默认给 1 行
        lenient().when(productionOrderMapper.updateExecution(eq(SHOP), eq(CODE), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any(), anyString())).thenReturn(1);
    }

    /** 一个批次策略为 strategy 的成品档案 */
    private InventoryGoods goods(String strategy)
    {
        InventoryGoods item = new InventoryGoods();
        item.setShopCode(SHOP);
        item.setGoodsCode("P001");
        item.setGoodsName("成品A");
        item.setSpec("标准");
        item.setUnit("个");
        item.setBatchStrategy(strategy);
        return item;
    }

    /** 让 requireOrder 能查到这张工单及其明细 */
    private void givenOrder(String status, List<ProductionMaterial> materials, List<ProductionOrderDetail> outputs)
    {
        order.setStatus(status);
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, CODE)).thenReturn(order);
        when(detailMapper.selectByProductionCode(SHOP, CODE)).thenReturn(outputs);
        when(materialMapper.selectByProductionCode(SHOP, CODE)).thenReturn(materials);
    }

    private ProductionMaterial material(String totalQty, String pickedQty, String actualQty, String returnedQty)
    {
        ProductionMaterial item = new ProductionMaterial();
        item.setShopCode(SHOP);
        item.setProductionCode(CODE);
        item.setMaterialCode(MATERIAL);
        // 配方按产出分开，物料行必须指明服务的是哪个产出
        item.setDetailCode(OUTPUT);
        item.setGoodsCode("M001");
        item.setGoodsName("原料A");
        item.setUnit("个");
        item.setQtyPerUnit(BigDecimal.ONE);
        item.setTotalQty(new BigDecimal(totalQty));
        item.setPickedQty(new BigDecimal(pickedQty));
        item.setActualQty(new BigDecimal(actualQty));
        item.setReturnedQty(new BigDecimal(returnedQty));
        return item;
    }

    private ProductionOrderDetail output(String planQty)
    {
        ProductionOrderDetail item = new ProductionOrderDetail();
        item.setShopCode(SHOP);
        item.setProductionCode(CODE);
        item.setDetailCode(OUTPUT);
        item.setProductCode("P001");
        item.setProductName("成品A");
        item.setPlanYQty(new BigDecimal(planQty));
        return item;
    }

    private ProductionExecutionRequest request()
    {
        ProductionExecutionRequest request = new ProductionExecutionRequest();
        request.setMaterialWareCode(WARE);
        return request;
    }

    /** 按行给出本次领料量 / 实际消耗量的请求项 */
    private ProductionMaterial input(String pickedQty, String actualQty)
    {
        ProductionMaterial item = new ProductionMaterial();
        item.setMaterialCode(MATERIAL);
        if (pickedQty != null) item.setPickedQty(new BigDecimal(pickedQty));
        if (actualQty != null) item.setActualQty(new BigDecimal(actualQty));
        return item;
    }

    /** 带批次的领料请求行 */
    private ProductionMaterial batchLine(String qty, String batchCode)
    {
        ProductionMaterial item = new ProductionMaterial();
        item.setMaterialCode(MATERIAL);
        item.setPickedQty(new BigDecimal(qty));
        item.setBatchCode(batchCode);
        return item;
    }

    /** 一张已完工的工单：物料成本已由消耗单结算，产出明细已按当时的单位成本回写 */
    private void givenCompletedOrder(String materialCost, String processingCost, String totalCost,
            String unitCost, String actualQty)
    {
        ProductionOrderDetail item = output(actualQty);
        item.setActualYQty(new BigDecimal(actualQty));
        // 完工时落库的本行物料成本，费用调整按它做分摊基数
        item.setMaterialCost(new BigDecimal(materialCost));
        item.setUnitCost(new BigDecimal(unitCost));
        item.setTotalCost(new BigDecimal(totalCost));
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(item));
        order.setActualYQty(new BigDecimal(actualQty));
        order.setMaterialCost(new BigDecimal(materialCost));
        order.setProcessingCost(new BigDecimal(processingCost));
        order.setFreightCost(BigDecimal.ZERO);
        order.setOtherCost(BigDecimal.ZERO);
        order.setTotalCost(new BigDecimal(totalCost));
        order.setUnitCost(new BigDecimal(unitCost));
    }

    private List<DocumentMain> capturedDocuments(int times)
    {
        ArgumentCaptor<DocumentMain> captor = ArgumentCaptor.forClass(DocumentMain.class);
        verify(documentService, org.mockito.Mockito.times(times)).insertDocument(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------ 领料

    /**
     * 领料日志的成本金额记 0。
     *
     * <p>SCLL 是同仓库内的状态流转，单据明细上那个金额是成本从「在库」搬到「不在库」的
     * 搬运额，不是发生额。照抄进日志，详情页就成了「领料产生了成本」，和界面上
     * 「领料不产生成本」的提示自相矛盾。</p>
     */
    @Test
    void pickLogRecordsNoCost()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0007");
        when(codeGenerator.nextProductionLogCode()).thenReturn("SCSJ0007");
        when(materialMapper.increasePickedQty(SHOP, MATERIAL, new BigDecimal("10"))).thenReturn(1);
        // 过账后回读的明细带着搬运金额，日志不该照抄
        DocumentMain posted = new DocumentMain();
        DocumentDetail line = new DocumentDetail();
        line.setGoodsCode("M001");
        line.setQty(new BigDecimal("10"));
        line.setCostAmount(new BigDecimal("123.45"));
        posted.setDetails(Collections.singletonList(line));
        when(documentService.selectDocumentByCode(eq(SHOP), anyString(), any())).thenReturn(posted);

        service.pickProductionOrder(SHOP, CODE, request(), USER);

        ArgumentCaptor<ProductionLog> captor = ArgumentCaptor.forClass(ProductionLog.class);
        verify(logMapper).insertLog(captor.capture(), any());
        assertEquals("领料", captor.getValue().getOperation());
        assertEquals(0, captor.getValue().getCostAmount().compareTo(BigDecimal.ZERO));
    }

    /**
     * 分批领料的第一批：只领了一部分，工单停在「待领料」，开出的是 SCLL 状态流转单
     * （良品·在库 → 良品·不在库），单号走 SCLL 自己的号段。
     */
    @Test
    void pickPartiallyKeepsOrderWaitingForMaterial()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0001");
        when(materialMapper.increasePickedQty(SHOP, MATERIAL, new BigDecimal("4"))).thenReturn(1);

        ProductionExecutionRequest request = request();
        request.setMaterials(Collections.singletonList(input("4", null)));
        service.pickProductionOrder(SHOP, CODE, request, USER);

        DocumentMain document = capturedDocuments(1).get(0);
        assertEquals(InventoryDocumentTypes.PRODUCTION_PICK, document.getDocType());
        assertEquals("SCLL0001", document.getDocCode());
        assertEquals(StockState.available(), document.getFromState());
        assertEquals(IN_PRODUCTION, document.getToState());
        // 同仓库内的状态流转，来源和目标必须是同一个仓库
        assertEquals(WARE, document.getFromWareCode());
        assertEquals(WARE, document.getToWareCode());
        assertEquals(0, document.getDetails().get(0).getQty().compareTo(new BigDecimal("4")));
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "待领料", USER);
    }

    /** 补齐最后一批后所有物料都领足，工单才转「生产中」 */
    @Test
    void pickRemainingMovesOrderIntoProduction()
    {
        givenOrder("待领料", new ArrayList<>(Collections.singletonList(material("10", "4", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0002");
        when(materialMapper.increasePickedQty(SHOP, MATERIAL, new BigDecimal("6"))).thenReturn(1);

        // 不给本次领料量时按「计划量 - 已领量」一次领足
        service.pickProductionOrder(SHOP, CODE, request(), USER);

        assertEquals(0, capturedDocuments(1).get(0).getDetails().get(0).getQty().compareTo(new BigDecimal("6")));
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "生产中", USER);
    }

    /** 本次领料量不能超过未领量，超了直接拒绝，库存单据一张都不开 */
    @Test
    void pickRejectsQtyOverPlan()
    {
        givenOrder("待领料", new ArrayList<>(Collections.singletonList(material("10", "4", "0", "0"))),
                Collections.singletonList(output("10")));

        ProductionExecutionRequest request = request();
        request.setMaterials(Collections.singletonList(input("7", null)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.pickProductionOrder(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("超过未领量"));
        verify(documentService, never()).insertDocument(any());
    }

    /** 已完工的工单不能再领料 */
    @Test
    void pickRejectsCompletedOrder()
    {
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(output("10")));

        assertThrows(ServiceException.class, () -> service.pickProductionOrder(SHOP, CODE, request(), USER));
        verify(documentService, never()).insertDocument(any());
    }

    // ------------------------------------------------------ 批次 / 序列号追踪

    /**
     * 按批次管理的物料：一次领料横跨两个批次，请求里就发两行，
     * 单据也拆成两行明细各带各的批次——库存模块靠这个定位搬哪一批的成本。
     * 已领量按合计一次累加，不是每行累加一次。
     */
    @Test
    void pickSplitsLinesAcrossBatches()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0005");
        when(materialMapper.increasePickedQty(SHOP, MATERIAL, new BigDecimal("10"))).thenReturn(1);

        ProductionExecutionRequest request = request();
        request.setMaterials(Arrays.asList(batchLine("6", "B001"), batchLine("4", "B002")));
        service.pickProductionOrder(SHOP, CODE, request, USER);

        DocumentMain document = capturedDocuments(1).get(0);
        List<DocumentDetail> lines = document.getDetails();
        assertEquals(2, lines.size());
        assertEquals("B001", lines.get(0).getBatchCode());
        assertEquals(0, lines.get(0).getQty().compareTo(new BigDecimal("6")));
        assertEquals("B002", lines.get(1).getBatchCode());
        assertEquals(0, lines.get(1).getQty().compareTo(new BigDecimal("4")));
        assertEquals(0, document.getTotalQty().compareTo(new BigDecimal("10")));
        // 两行加起来正好领足
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "生产中", USER);
    }

    /** 多行领料的合计也要受未领量约束，不能靠拆行绕过 */
    @Test
    void pickRejectsBatchLinesOverPlan()
    {
        givenOrder("待领料", new ArrayList<>(Collections.singletonList(material("10", "4", "0", "0"))),
                Collections.singletonList(output("10")));

        ProductionExecutionRequest request = request();
        request.setMaterials(Arrays.asList(batchLine("4", "B001"), batchLine("4", "B002")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.pickProductionOrder(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("超过未领量"));
        verify(documentService, never()).insertDocument(any());
    }

    /** 序列号管理的物料，序列号原样透传给库存模块 */
    @Test
    void pickCarriesSerialNumbers()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("2", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0006");
        when(materialMapper.increasePickedQty(SHOP, MATERIAL, new BigDecimal("2"))).thenReturn(1);

        ProductionMaterial line = new ProductionMaterial();
        line.setMaterialCode(MATERIAL);
        line.setPickedQty(new BigDecimal("2"));
        line.setSerialNumbers(Arrays.asList("SN-1", "SN-2"));
        ProductionExecutionRequest request = request();
        request.setMaterials(Collections.singletonList(line));
        service.pickProductionOrder(SHOP, CODE, request, USER);

        assertEquals(Arrays.asList("SN-1", "SN-2"), capturedDocuments(1).get(0).getDetails().get(0).getSerialNumbers());
    }

    /** 消耗同样按批次拆行，实际消耗量按合计落库 */
    @Test
    void consumeSplitsLinesAcrossBatches()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                new ArrayList<>(Collections.singletonList(output("10"))));
        when(codeGenerator.nextProductionConsumeCode()).thenReturn("SCXH0002");
        when(codeGenerator.nextProductInboundCode()).thenReturn("SCCPRK0002");

        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        ProductionMaterial first = new ProductionMaterial();
        first.setMaterialCode(MATERIAL);
        first.setActualQty(new BigDecimal("6"));
        first.setBatchCode("B001");
        ProductionMaterial second = new ProductionMaterial();
        second.setMaterialCode(MATERIAL);
        second.setActualQty(new BigDecimal("3"));
        second.setBatchCode("B002");
        request.setMaterials(Arrays.asList(first, second));
        service.completeProductionOrder(SHOP, CODE, request, USER);

        DocumentMain consume = capturedDocuments(2).get(0);
        assertEquals(2, consume.getDetails().size());
        assertEquals("B001", consume.getDetails().get(0).getBatchCode());
        assertEquals("B002", consume.getDetails().get(1).getBatchCode());
        verify(materialMapper).updateActualQty(SHOP, MATERIAL, new BigDecimal("9"));
    }

    /** 退料支持按行指定数量和批次，只退一部分 */
    @Test
    void returnAcceptsRequestedQtyAndBatch()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionReturnCode()).thenReturn("SCTL0002");
        when(materialMapper.increaseReturnedQty(SHOP, MATERIAL, new BigDecimal("3"))).thenReturn(1);

        ProductionMaterial line = new ProductionMaterial();
        line.setMaterialCode(MATERIAL);
        line.setReturnedQty(new BigDecimal("3"));
        line.setBatchCode("B001");
        ProductionExecutionRequest request = request();
        request.setMaterials(Collections.singletonList(line));
        service.returnProductionMaterial(SHOP, CODE, request, USER);

        DocumentDetail detail = capturedDocuments(1).get(0).getDetails().get(0);
        assertEquals(0, detail.getQty().compareTo(new BigDecimal("3")));
        assertEquals("B001", detail.getBatchCode());
    }

    /** 退料量不能超过可退数量 */
    @Test
    void returnRejectsQtyOverReturnable()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "8", "0"))),
                Collections.singletonList(output("10")));

        ProductionMaterial line = new ProductionMaterial();
        line.setMaterialCode(MATERIAL);
        line.setReturnedQty(new BigDecimal("3"));
        ProductionExecutionRequest request = request();
        request.setMaterials(Collections.singletonList(line));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.returnProductionMaterial(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("超过可退数量"));
        verify(documentService, never()).insertDocument(any());
    }

    // ------------------------------------------------------------------ 完工

    /**
     * 完工先开 SCXH 消耗出库（从车间在制品永久出库），再开 SCCPRK 成品入库。
     *
     * <p>物料成本直接取消耗单过账后回写的表头金额，对上说明书 4.4 流程2 的
     * {@code materialcost = Σ(消耗数量 × 出库成本价)}；单位成本 = 总成本 ÷ 实际产出。</p>
     */
    @Test
    void completeConsumesFromWorkshopThenPutsProductIn()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                new ArrayList<>(Collections.singletonList(output("10"))));
        when(codeGenerator.nextProductionConsumeCode()).thenReturn("SCXH0001");
        when(codeGenerator.nextProductInboundCode()).thenReturn("SCCPRK0001");
        // 过账后库存模块按实际批次算出成本并回写表头金额和明细
        DocumentDetail postedLine = new DocumentDetail();
        postedLine.setGoodsCode("M001");
        postedLine.setQty(new BigDecimal("8"));
        postedLine.setCostAmount(new BigDecimal("500.00"));
        DocumentMain posted = new DocumentMain();
        posted.setTotalAmount(new BigDecimal("500.00"));
        posted.setDetails(Collections.singletonList(postedLine));
        // 成品入库单没有回读桩，走 null 分支保留建单时的明细
        when(documentService.selectDocumentByCode(eq(SHOP), anyString(), any()))
                .thenAnswer(invocation -> "SCXH0001".equals(invocation.getArgument(1)) ? posted : null);

        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        request.setMaterials(Collections.singletonList(input(null, "8")));
        request.setProcessingCost(new BigDecimal("100"));
        service.completeProductionOrder(SHOP, CODE, request, USER);

        List<DocumentMain> documents = capturedDocuments(2);
        DocumentMain consume = documents.get(0);
        assertEquals(InventoryDocumentTypes.PRODUCTION_CONSUME, consume.getDocType());
        assertEquals("SCXH0001", consume.getDocCode());
        // 消耗扣的是车间在制品，来源必须是不在库，否则会去扣仓库里的可用库存
        assertEquals(IN_PRODUCTION, consume.getFromState());
        assertEquals(0, consume.getDetails().get(0).getQty().compareTo(new BigDecimal("8")));

        DocumentMain productIn = documents.get(1);
        assertEquals(InventoryDocumentTypes.PRODUCT_INBOUND, productIn.getDocType());
        assertEquals(PRODUCT_WARE, productIn.getToWareCode());
        DocumentDetail productLine = productIn.getDetails().get(0);
        assertEquals(0, productLine.getQty().compareTo(new BigDecimal("10")));
        // (500 物料 + 100 加工费) / 10 = 60
        assertEquals(0, productLine.getCostPrice().compareTo(new BigDecimal("60")));
        assertEquals(0, productLine.getCostAmount().compareTo(new BigDecimal("600")));

        verify(materialMapper).updateActualQty(SHOP, MATERIAL, new BigDecimal("8"));

        ArgumentCaptor<BigDecimal> materialCost = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> totalCost = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> unitCost = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productionOrderMapper).updateExecution(eq(SHOP), eq(CODE), eq("生产中"), eq("已完工"), any(), any(),
                materialCost.capture(), totalCost.capture(), unitCost.capture(), any(), any(), any(), eq(USER));
        assertEquals(0, materialCost.getValue().compareTo(new BigDecimal("500")));
        assertEquals(0, totalCost.getValue().compareTo(new BigDecimal("600")));
        assertEquals(0, unitCost.getValue().compareTo(new BigDecimal("60")));
    }

    /**
     * 配方按产出商品分开后，成本也按产出算：每个产出先认领自己配方消耗掉的物料成本，
     * 加工费/运费/其他费用再按各产出的物料成本占比分摊，各产出的单位成本因此不同。
     */
    @Test
    void completeAllocatesCostByEachOutputsOwnBom()
    {
        ProductionMaterial expensive = material("6", "6", "0", "0");
        ProductionMaterial cheap = material("2", "2", "0", "0");
        cheap.setMaterialCode("SCWL002");
        cheap.setDetailCode("SCMX002");
        cheap.setGoodsCode("M002");
        cheap.setGoodsName("原料B");
        ProductionOrderDetail firstOutput = output("2");
        ProductionOrderDetail secondOutput = output("2");
        secondOutput.setDetailCode("SCMX002");
        secondOutput.setProductCode("P002");
        secondOutput.setProductName("成品B");
        givenOrder("生产中", new ArrayList<>(Arrays.asList(expensive, cheap)),
                new ArrayList<>(Arrays.asList(firstOutput, secondOutput)));
        when(codeGenerator.nextProductionConsumeCode()).thenReturn("SCXH0001");
        when(codeGenerator.nextProductInboundCode()).thenReturn("SCCPRK0001");

        // 消耗单过账后：贵料 300 出自成品A的配方，便宜料 100 出自成品B的配方
        DocumentDetail expensiveLine = new DocumentDetail();
        expensiveLine.setGoodsCode("M001");
        expensiveLine.setQty(new BigDecimal("6"));
        expensiveLine.setCostAmount(new BigDecimal("300.00"));
        DocumentDetail cheapLine = new DocumentDetail();
        cheapLine.setGoodsCode("M002");
        cheapLine.setQty(new BigDecimal("2"));
        cheapLine.setCostAmount(new BigDecimal("100.00"));
        DocumentMain posted = new DocumentMain();
        posted.setTotalAmount(new BigDecimal("400.00"));
        posted.setDetails(Arrays.asList(expensiveLine, cheapLine));
        when(documentService.selectDocumentByCode(eq(SHOP), anyString(), any()))
                .thenAnswer(invocation -> "SCXH0001".equals(invocation.getArgument(1)) ? posted : null);

        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        request.setProcessingCost(new BigDecimal("100"));
        service.completeProductionOrder(SHOP, CODE, request, USER);

        DocumentMain productIn = capturedDocuments(2).get(1);
        // 加工费 100 按物料成本 300:100 摊成 75:25 → 375 与 125，而不是各 250
        assertEquals(0, productIn.getDetails().get(0).getCostAmount().compareTo(new BigDecimal("375.00")));
        assertEquals(0, productIn.getDetails().get(1).getCostAmount().compareTo(new BigDecimal("125.00")));
        // 单位成本各算各的：贵料产出 187.5，便宜料产出 62.5，不再被拉平成 125
        assertEquals(0, productIn.getDetails().get(0).getCostPrice().compareTo(new BigDecimal("187.5")));
        assertEquals(0, productIn.getDetails().get(1).getCostPrice().compareTo(new BigDecimal("62.5")));
        // 本行物料成本单独落库，供事后调整费用时按同一比例重摊
        verify(detailMapper).updateExecution(eq(SHOP), eq(OUTPUT), eq(new BigDecimal("2")),
                eq(new BigDecimal("300.00")), any(), eq(new BigDecimal("375.00")));
        verify(detailMapper).updateExecution(eq(SHOP), eq("SCMX002"), eq(new BigDecimal("2")),
                eq(new BigDecimal("100.00")), any(), eq(new BigDecimal("125.00")));
    }

    /** 实际消耗不能超过「已领 - 已退」：没领到车间的料消耗不掉 */
    @Test
    void completeRejectsConsumeOverPicked()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "6", "0", "1"))),
                new ArrayList<>(Collections.singletonList(output("10"))));

        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        request.setMaterials(Collections.singletonList(input(null, "6")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeProductionOrder(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("不能超过可消耗量"));
        verify(documentService, never()).insertDocument(any());
    }

    /** 只有生产中的工单能完工，待领料的工单料还没领齐 */
    @Test
    void completeRejectsOrderNotInProduction()
    {
        givenOrder("待领料", new ArrayList<>(Collections.singletonList(material("10", "4", "0", "0"))),
                new ArrayList<>(Collections.singletonList(output("10"))));

        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        assertThrows(ServiceException.class, () -> service.completeProductionOrder(SHOP, CODE, request, USER));
        verify(documentService, never()).insertDocument(any());
    }

    // ------------------------------------------------------------------ 退料

    /**
     * 退料把剩下的在制品退回仓库：可退 = 已领 - 已耗 - 已退，
     * 单据是 SCTL 状态回流（良品·不在库 → 良品·在库），不定价、不产生成本。
     */
    @Test
    void returnSendsLeftoverBackToStock()
    {
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "8", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionReturnCode()).thenReturn("SCTL0001");
        when(materialMapper.increaseReturnedQty(SHOP, MATERIAL, new BigDecimal("2"))).thenReturn(1);

        service.returnProductionMaterial(SHOP, CODE, request(), USER);

        DocumentMain document = capturedDocuments(1).get(0);
        assertEquals(InventoryDocumentTypes.PRODUCTION_RETURN, document.getDocType());
        assertEquals("SCTL0001", document.getDocCode());
        assertEquals(IN_PRODUCTION, document.getFromState());
        assertEquals(StockState.available(), document.getToState());
        assertEquals(WARE, document.getFromWareCode());
        assertEquals(WARE, document.getToWareCode());
        DocumentDetail line = document.getDetails().get(0);
        assertEquals(0, line.getQty().compareTo(new BigDecimal("2")));
        // 状态回流不动成本，明细上不该带价
        assertTrue(line.getCostPrice() == null || line.getCostPrice().signum() == 0);
    }

    /** 全领全耗后没有可退的料 */
    @Test
    void returnRejectsWhenNothingLeft()
    {
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(output("10")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.returnProductionMaterial(SHOP, CODE, request(), USER));
        assertTrue(error.getMessage().contains("没有可退回"));
        verify(documentService, never()).insertDocument(any());
    }

    /** 待生产工单一根料都没领，退无可退 */
    @Test
    void returnRejectsPlannedOrder()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));

        assertThrows(ServiceException.class, () -> service.returnProductionMaterial(SHOP, CODE, request(), USER));
        verify(documentService, never()).insertDocument(any());
    }

    // ------------------------------------------------------------ 执行日志

    /**
     * 跨月工单的日志要跨月表查：上月领料、这月完工，两段日志在两张月表里，
     * 合并后按操作时间整体倒序，不能只看当月那几条。
     */
    @Test
    void logsSpanAllMonthlyTables()
    {
        when(logMapper.selectLogTableSuffixes()).thenReturn(Arrays.asList("202607", "202608"));
        when(logMapper.selectLogList(SHOP, CODE, null, "202607"))
                .thenReturn(new ArrayList<>(Collections.singletonList(log("领料", "2026-07-30 10:00:00"))));
        when(logMapper.selectLogList(SHOP, CODE, null, "202608"))
                .thenReturn(new ArrayList<>(Arrays.asList(
                        log("退料", "2026-08-02 09:00:00"), log("消耗", "2026-08-01 09:00:00"))));

        List<ProductionLog> logs = service.selectProductionLogList(SHOP, CODE, null, null);

        assertEquals(3, logs.size());
        assertEquals("退料", logs.get(0).getOperation());
        assertEquals("消耗", logs.get(1).getOperation());
        assertEquals("领料", logs.get(2).getOperation());
    }

    /** 指定了月份就只查那一张月表，留给排查用 */
    @Test
    void logsHonourExplicitSuffix()
    {
        when(tableRouter.validate("202608")).thenReturn("202608");
        when(logMapper.selectLogList(SHOP, CODE, "领料", "202608"))
                .thenReturn(new ArrayList<>(Collections.singletonList(log("领料", "2026-08-01 09:00:00"))));

        assertEquals(1, service.selectProductionLogList(SHOP, CODE, "领料", "202608").size());
        verify(logMapper, never()).selectLogTableSuffixes();
    }

    private ProductionLog log(String operation, String time)
    {
        ProductionLog item = new ProductionLog();
        item.setShopCode(SHOP);
        item.setProductionCode(CODE);
        item.setOperation(operation);
        try
        {
            item.setOperationTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(time));
        }
        catch (java.text.ParseException e)
        {
            throw new IllegalStateException(e);
        }
        return item;
    }

    // ------------------------------------------------------------ 三段守恒

    /**
     * 库存守恒：领料只是把量从「良品·在库」挪到「良品·不在库」，两格合计不变；
     * 真正让总量减少的只有消耗那一段。这里用单据方向表达这个口径。
     */
    @Test
    void pickAndReturnAreStateTransfersOnly()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0003");
        when(materialMapper.increasePickedQty(eq(SHOP), eq(MATERIAL), any())).thenReturn(1);

        service.pickProductionOrder(SHOP, CODE, request(), USER);

        DocumentMain document = capturedDocuments(1).get(0);
        // 状态流转单：两端都有状态、两端都有仓库，且是同一个仓库
        assertEquals(document.getFromWareCode(), document.getToWareCode());
        assertTrue(document.getFromState().isAvailable());
        assertTrue(!document.getToState().isOnhand() && document.getToState().isGood());
        // 领料不产生成本
        assertEquals(0, document.getTotalAmount().compareTo(BigDecimal.ZERO));
    }

    /** 领料单的明细数量合计要落到表头，否则 u_f_docmain 的 totalqty 一直是 0 */
    @Test
    void documentHeaderCarriesTotalQty()
    {
        givenOrder("待生产", new ArrayList<>(Arrays.asList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionPickCode()).thenReturn("SCLL0004");
        when(materialMapper.increasePickedQty(eq(SHOP), eq(MATERIAL), any())).thenReturn(1);

        service.pickProductionOrder(SHOP, CODE, request(), USER);

        assertEquals(0, capturedDocuments(1).get(0).getTotalQty().compareTo(new BigDecimal("10")));
    }

    /** 领料仓库是必填的，缺了会去扣不到库存 */
    @Test
    void pickRequiresWarehouse()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));

        ProductionExecutionRequest request = new ProductionExecutionRequest();
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.pickProductionOrder(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("领料仓库"));
        verify(documentService, never()).insertDocument(any());
    }

    /** 工单不存在时不能凭空开库存单据 */
    @Test
    void pickRejectsUnknownOrder()
    {
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, CODE)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.pickProductionOrder(SHOP, CODE, request(), USER));
        verify(documentService, never()).insertDocument(any());
    }

    // ------------------------------------------------------------------ 关闭

    /**
     * 车间还有没退回的料时不许关闭：关闭后退料入口就没了（只放行待领料/生产中/已完工），
     * 剩下的量会永远挂在「良品·不在库」，谁也取不回来。
     */
    @Test
    void closeRejectsOrderWithMaterialStillInWorkshop()
    {
        // 领 10、耗 6、退 1，车间还剩 3
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "6", "1"))),
                Collections.singletonList(output("10")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeProductionOrder(SHOP, CODE, null, USER));
        assertTrue(error.getMessage().contains("未退回的在制物料"));
        assertTrue(error.getMessage().contains("原料A 3"));
        verify(productionOrderMapper, never()).updateStatus(anyString(), anyString(), eq("已关闭"), anyString());
    }

    /** 领的料全部消耗或退回后，车间是空的，可以关闭 */
    @Test
    void closeAcceptsOrderWithWorkshopCleared()
    {
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "7", "3"))),
                Collections.singletonList(output("10")));

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /**
     * 关闭时松开还有缺口的来源订单明细：备货来源仍是「生产」，只是不再挂这张工单。
     * 不松开的话，选缺口界面上会一直显示「已挂 SCxxx」，看着像被占死了。
     */
    @Test
    void closeReleasesOrderDetailStillShort()
    {
        givenClosableOrderDrivenOrder();
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem(CODE, "4"));

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(salesOrderService).updateItemProductionOrder(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL, null);
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /** 缺口已经补齐的不动：那条工单号是追溯线索，而且没有缺口就不会挡任何人的路 */
    @Test
    void closeKeepsSourceOnFulfilledOrderDetail()
    {
        givenClosableOrderDrivenOrder();
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem(CODE, "0"));

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(salesOrderService, never()).updateItemProductionOrder(anyString(), anyString(),
                anyString(), anyString(), any());
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /** 明细已经被别的工单接手，不能把人家的占用抹掉 */
    @Test
    void closeLeavesOrderDetailTakenByAnotherOrder()
    {
        givenClosableOrderDrivenOrder();
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("SC20260901009", "4"));

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(salesOrderService, never()).updateItemProductionOrder(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    /** 订单明细没了（订单取消、明细删掉）不该让工单关不掉——车间已清空才是关闭的门槛 */
    @Test
    void closeSucceedsWhenOrderDetailIsGone()
    {
        givenClosableOrderDrivenOrder();
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL)).thenReturn(null);

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(salesOrderService, never()).updateItemProductionOrder(anyString(), anyString(),
                anyString(), anyString(), any());
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /** 一张车间已清空、可以关闭的订单驱动工单，分配挂在唯一那行产出上 */
    private void givenClosableOrderDrivenOrder()
    {
        ProductionOrderDetail out = output("10");
        out.setLineNo(1);
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                new ArrayList<>(Collections.singletonList(out)));
        order.setSourceType("order");
        ProductionOrderAllocation allocation = allocation(ORDER_CODE, ORDER_DETAIL, "6");
        allocation.setOutputLineNo(1);
        order.setOrderAllocation(JSON.toJSONString(Collections.singletonList(allocation)));
    }

    /** 一条订单明细：sourceCode 是当前挂着的工单，pendingQty 是还没发出去多少（排产上限） */
    private SalesOrderItem orderItem(String sourceCode, String pendingQty)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setOrderCode(ORDER_CODE);
        item.setDetailCode(ORDER_DETAIL);
        item.setGoodsCode("P001");
        item.setGoodsName("成品A");
        item.setSourceCode(sourceCode);
        // 排产上限是未发量
        item.setPendingQty(new BigDecimal(pendingQty));
        return item;
    }

    // ------------------------------------------------------------------ 完工后调整费用

    /** 完工后的工单，补录加工费应重算总成本和单位成本，物料成本不动 */
    @Test
    void adjustCostRecalculatesTotalAndUnitCost()
    {
        givenCompletedOrder("225.00", "100.00", "325.00", "32.500000", "10");
        ProductionExecutionRequest request = new ProductionExecutionRequest();
        request.setProcessingCost(new BigDecimal("150.00"));

        service.adjustProductionCost(SHOP, CODE, request, USER);

        // 物料 225 + 加工 150 = 375；单位成本 375 ÷ 10 = 37.5
        verify(productionOrderMapper).updateCost(SHOP, CODE, new BigDecimal("375.00"),
                new BigDecimal("37.500000"), new BigDecimal("150.00"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), USER);
        // 产出明细按新单位成本回写，物料成本原样留着（下次调整还要按它分摊）
        verify(detailMapper).updateExecution(eq(SHOP), eq(OUTPUT), eq(new BigDecimal("10")),
                eq(new BigDecimal("225.00")), eq(new BigDecimal("37.500000")), eq(new BigDecimal("375.00")));
    }

    /** 调整要在生产日志里留痕，costamount 记的是本行成本的差额 */
    @Test
    void adjustCostWritesAuditLog()
    {
        givenCompletedOrder("225.00", "100.00", "325.00", "32.500000", "10");
        when(codeGenerator.nextProductionLogCode()).thenReturn("SCSJ0001");
        when(tableRouter.forBusinessDate(any())).thenReturn("202608");
        ProductionExecutionRequest request = new ProductionExecutionRequest();
        request.setProcessingCost(new BigDecimal("150.00"));

        service.adjustProductionCost(SHOP, CODE, request, USER);

        ArgumentCaptor<ProductionLog> captor = ArgumentCaptor.forClass(ProductionLog.class);
        // 日志按业务日期落到对应的月表
        verify(logMapper).insertLog(captor.capture(), eq("202608"));
        ProductionLog log = captor.getValue();
        assertEquals("费用调整", log.getOperation());
        // 375 - 325 = 50，成本增加了 50
        assertEquals(0, log.getCostAmount().compareTo(new BigDecimal("50.00")));
        assertTrue(log.getRemark().contains("加工费"));
        assertTrue(log.getRemark().contains("库存成本未同步"));
        // 不开库存单据
        verify(documentService, never()).insertDocument(any());
    }

    /** 只有已完工的工单能调；已关闭代表成本定案 */
    @Test
    void adjustCostRejectsClosedOrder()
    {
        givenOrder("已关闭", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(output("10")));
        ProductionExecutionRequest request = new ProductionExecutionRequest();
        request.setProcessingCost(new BigDecimal("150.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.adjustProductionCost(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("已完工"));
        verify(productionOrderMapper, never()).updateCost(anyString(), anyString(), any(), any(), any(), any(), any(), anyString());
    }

    /** 三项费用一个没变时直接拒绝，不写无意义的调整日志 */
    @Test
    void adjustCostRejectsNoOpChange()
    {
        givenCompletedOrder("225.00", "100.00", "325.00", "32.500000", "10");
        ProductionExecutionRequest request = new ProductionExecutionRequest();
        request.setProcessingCost(new BigDecimal("100.00"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.adjustProductionCost(SHOP, CODE, request, USER));
        assertTrue(error.getMessage().contains("没有变化"));
        verify(logMapper, never()).insertLog(any(), anyString());
    }

    // ------------------------------------------------------------------ 建单：按产出分开的配方

    /**
     * 每个产出商品各有各的配方：物料的计划用量 = 单位用量 × <b>本行产出</b>的计划数量，
     * 分母不是整单产出合计。主表计划数量仍是各产出之和。
     */
    @Test
    @SuppressWarnings("unchecked")
    void insertScalesMaterialPlanQtyByItsOwnOutput()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT, "SCMX002");
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL, "SCWL002");

        ProductionOrderDetail first = newOutput("P001", "成品A", "2");
        first.setMaterials(new ArrayList<>(Collections.singletonList(bom("M001", "2"))));
        ProductionOrderDetail second = newOutput("P002", "成品B", "3");
        second.setMaterials(new ArrayList<>(Collections.singletonList(bom("M002", "1"))));
        ProductionOrder input = newOrder(Arrays.asList(first, second));

        service.insertProductionOrder(input);

        ArgumentCaptor<List<ProductionMaterial>> captor = ArgumentCaptor.forClass(List.class);
        verify(materialMapper).batchInsert(captor.capture());
        List<ProductionMaterial> saved = captor.getValue();
        // 成品A：单耗 2 × 本行产出 2 = 4（按老口径会是 2 × 5 = 10）
        assertEquals(OUTPUT, saved.get(0).getDetailCode());
        assertEquals(0, saved.get(0).getTotalQty().compareTo(new BigDecimal("4")));
        // 成品B：单耗 1 × 本行产出 3 = 3
        assertEquals("SCMX002", saved.get(1).getDetailCode());
        assertEquals(0, saved.get(1).getTotalQty().compareTo(new BigDecimal("3")));
        // 主表计划数量仍是产出合计
        assertEquals(0, input.getPlanYQty().compareTo(new BigDecimal("5")));
    }

    /**
     * 物料不在商品档案里就拒。不拦的话工单存得下、界面上也看不出问题，
     * 一直要到领料过账才报「商品不存在」，那时候工单已经排进计划了。
     */
    @Test
    void insertRejectsMaterialMissingFromGoodsArchive()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(goodsMapper.selectGoodsByCode(SHOP, "M404")).thenReturn(null);
        ProductionOrderDetail first = newOutput("P001", "成品A", "2");
        first.setMaterials(new ArrayList<>(Collections.singletonList(bom("M404", "2"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertProductionOrder(newOrder(Collections.singletonList(first))));

        assertTrue(error.getMessage().contains("不在商品档案里"), error.getMessage());
        verify(productionOrderMapper, never()).insertProductionOrder(any());
    }

    /** 产出商品不能出现在自己的配方里：完工时同一张单既消耗又入库同一个商品，成本算不出来 */
    @Test
    void insertRejectsOutputUsedAsItsOwnMaterial()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        ProductionOrderDetail first = newOutput("P001", "成品A", "2");
        first.setMaterials(new ArrayList<>(Collections.singletonList(bom("P001", "1"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertProductionOrder(newOrder(Collections.singletonList(first))));

        assertTrue(error.getMessage().contains("不能有它自己"), error.getMessage());
        verify(productionOrderMapper, never()).insertProductionOrder(any());
    }

    /** 老结构把物料平铺在工单下，没有归属就算不出计划用量，直接拒绝而不是默默丢掉 */
    @Test
    void insertRejectsMaterialsNotAttachedToAnyOutput()
    {
        ProductionOrderDetail first = newOutput("P001", "成品A", "2");
        ProductionOrder input = newOrder(Collections.singletonList(first));
        input.setMaterials(new ArrayList<>(Collections.singletonList(bom("M001", "2"))));

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("挂在产出明细下"));
        verify(materialMapper, never()).batchInsert(any());
    }

    private ProductionOrder newOrder(List<ProductionOrderDetail> details)
    {
        ProductionOrder input = new ProductionOrder();
        input.setShopCode(SHOP);
        input.setSourceType("stock");
        input.setProductCode("P001");
        input.setProductName("成品A");
        input.setDetails(new ArrayList<>(details));
        return input;
    }

    private ProductionOrderDetail newOutput(String productCode, String productName, String planQty)
    {
        ProductionOrderDetail item = new ProductionOrderDetail();
        item.setProductCode(productCode);
        item.setProductName(productName);
        item.setPlanYQty(new BigDecimal(planQty));
        return item;
    }

    /** 录入态的物料行：只有商品和单位用量，计划用量由服务端算 */
    private ProductionMaterial bom(String goodsCode, String qtyPerUnit)
    {
        ProductionMaterial item = new ProductionMaterial();
        item.setGoodsCode(goodsCode);
        item.setGoodsName(goodsCode);
        item.setQtyPerUnit(new BigDecimal(qtyPerUnit));
        return item;
    }

    // ------------------------------------------------------------------ 建单：订单驱动

    /**
     * 订单驱动工单落库时把分配序列化进主表的 orderallocation，并回写订单明细的备货来源。
     *
     * <p>回写是整条链路的关键：工单完工要靠订单明细上的 sourcecode 反查该发哪张订单。</p>
     */
    @Test
    void insertOrderDrivenPersistsAllocationAndClaimsOrderItem()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "10", null));

        ProductionOrder input = orderDrivenOrder("6", "6");
        service.insertProductionOrder(input);

        // 主表两列都由分配推导，不接受前端传值
        assertEquals(ORDER_CODE, input.getSourceOrderCodes());
        assertTrue(input.getOrderAllocation().contains(ORDER_DETAIL));
        assertTrue(input.getOrderAllocation().contains("\"outputLineNo\":1"));
        verify(salesOrderService).updateItemProductionOrder(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL, CODE);
    }

    /** 分给订单的量不能超过它的未发量，多出来的部分没有订单接得住 */
    @Test
    void insertRejectsAllocationOverPendingQty()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "4", null));

        ProductionOrder input = orderDrivenOrder("6", "6");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("还没发的只有 4"), error.getMessage());
        verify(productionOrderMapper, never()).insertProductionOrder(any());
    }

    /**
     * <b>库存里有多少现货不影响能不能排产。</b>上限只看未发量。
     *
     * <p>订单已经不再预占库存，所以「缺口」这个概念没有了：判据就是订单量 − 已发量。
     * 定制件更是只认为它专门做出来的那批货，仓库里同款的通用库存顶不上用。</p>
     */
    @Test
    void insertAllowsOrderProductionRegardlessOfStock()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "10", null));

        service.insertProductionOrder(orderDrivenOrder("6", "6"));

        verify(productionOrderMapper).insertProductionOrder(any());
    }

    /** 分出去的比计划做的还多，完工时凑不齐货 */
    @Test
    void insertRejectsAllocationOverPlannedOutput()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "10", null));

        // 计划产出 5，却分给订单 8
        ProductionOrder input = orderDrivenOrder("5", "8");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("超过计划产出"));
        verify(productionOrderMapper, never()).insertProductionOrder(any());
    }

    /** 订单要的是甲，这行产出的是乙——做出来也发不了这张订单 */
    @Test
    void insertRejectsAllocationOfDifferentGoods()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P999", "10", null));

        ProductionOrder input = orderDrivenOrder("6", "6");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("订单要的是 P999"));
    }

    /**
     * 一条订单明细同时只能挂一张在产工单：sourcecode 是单值列，
     * 两张工单抢同一条明细时后一张会把前一张的工单号覆盖掉，追溯就断了。
     */
    @Test
    void insertRejectsOrderItemAlreadyHeldByLiveOrder()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "10", "SC20260801001"));
        ProductionOrder holder = new ProductionOrder();
        holder.setStatus("生产中");
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, "SC20260801001")).thenReturn(holder);

        ProductionOrder input = orderDrivenOrder("6", "6");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("已挂在工单 SC20260801001"));
    }

    /** 占着这条明细的工单已经关闭，剩下的缺口该让新工单接手 */
    @Test
    void insertAllowsOrderItemHeldByClosedOrder()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);
        when(salesOrderService.lockOrderItem(SHOP, ORDER_MONTH, ORDER_DETAIL))
                .thenReturn(orderItem("P001", "10", "SC20260801001"));
        ProductionOrder holder = new ProductionOrder();
        holder.setStatus("已关闭");
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, "SC20260801001")).thenReturn(holder);

        service.insertProductionOrder(orderDrivenOrder("6", "6"));

        verify(salesOrderService).updateItemProductionOrder(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL, CODE);
    }

    /** 订单生产却一条订单都没挂，等于备货生产还占着订单的名头 */
    @Test
    void insertRejectsOrderDrivenWithoutAllocation()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);

        ProductionOrder input = orderDrivenOrder("6", "6");
        input.getDetails().get(0).setAllocations(new ArrayList<>());

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("必须选来源订单"));
    }

    /** 备货生产挂订单是矛盾的请求，与其默默丢掉不如让调用方改对 */
    @Test
    void insertRejectsStockOrderCarryingAllocation()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);

        ProductionOrder input = orderDrivenOrder("6", "6");
        input.setSourceType("stock");

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("备货生产的工单不能挂销售订单"));
    }

    /** 销售订单按月分表，没有月份定位不到明细，宁可拒也不猜当月 */
    @Test
    void insertRejectsAllocationWithoutTableSuffix()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);

        ProductionOrder input = orderDrivenOrder("6", "6");
        input.getDetails().get(0).getAllocations().get(0).setTableSuffix(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertProductionOrder(input));
        assertTrue(error.getMessage().contains("月份"));
    }

    /** 备货工单不该去动任何订单明细 */
    @Test
    void insertStockOrderLeavesSalesOrdersAlone()
    {
        when(codeGenerator.nextProductionOrderCode()).thenReturn(CODE);
        when(codeGenerator.nextProductionDetailCode()).thenReturn(OUTPUT);
        when(codeGenerator.nextProductionMaterialCode()).thenReturn(MATERIAL);

        ProductionOrderDetail first = newOutput("P001", "成品A", "2");
        first.setMaterials(new ArrayList<>(Collections.singletonList(bom("M001", "2"))));
        ProductionOrder input = newOrder(Collections.singletonList(first));

        service.insertProductionOrder(input);

        assertEquals(null, input.getOrderAllocation());
        assertEquals(null, input.getSourceOrderCodes());
        verify(salesOrderService, never()).updateItemProductionOrder(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /** 删工单要把它占住的订单明细放回去，否则那条明细的来源单号会指向一张不存在的工单 */
    @Test
    void deleteReleasesClaimedOrderItems()
    {
        ProductionOrder existing = new ProductionOrder();
        existing.setShopCode(SHOP);
        existing.setProductionCode(CODE);
        existing.setStatus("待生产");
        existing.setOrderAllocation("[{\"outputLineNo\":1,\"orderCode\":\"" + ORDER_CODE + "\",\"tableSuffix\":\""
                + ORDER_MONTH + "\",\"detailCode\":\"" + ORDER_DETAIL + "\",\"goodsCode\":\"P001\",\"qty\":6}]");
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, CODE)).thenReturn(existing);

        service.deleteProductionOrder(SHOP, CODE);

        // 备货来源仍是「生产」——业务意图没变，只是暂时没有工单在做
        verify(salesOrderService).updateItemProductionOrder(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL, null);
    }

    /** orderallocation 是历史遗留的透传列，老数据可能是随手写的文本，不该让整张工单查不出来 */
    @Test
    void detailToleratesUnparsableAllocation()
    {
        order.setStatus("待生产");
        order.setOrderAllocation("按老规矩分给张三");
        when(productionOrderMapper.selectProductionOrderByCode(SHOP, CODE)).thenReturn(order);
        when(detailMapper.selectByProductionCode(SHOP, CODE)).thenReturn(Collections.singletonList(output("10")));
        when(materialMapper.selectByProductionCode(SHOP, CODE)).thenReturn(new ArrayList<>());

        ProductionOrder loaded = service.selectProductionOrderByCode(SHOP, CODE);

        assertTrue(loaded.getDetails().get(0).getAllocations().isEmpty());
    }

    // ------------------------------------------------------------------ 完工：通知订单模块

    /**
     * 完工入库后按分配把成品预占给来源订单（说明书 6.3）。
     *
     * <p>这是「通知订单」在本系统里的落地形态：不发消息，直接把货占上、把状态推回去。</p>
     */
    @Test
    void completeClaimsSourceOrders()
    {
        givenCompletableOrder(allocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(salesOrderService.fulfillFromProduction(eq(SHOP), eq(ORDER_CODE), eq(ORDER_MONTH),
                eq(ORDER_DETAIL), any(), eq(USER))).thenReturn(new BigDecimal("6"));

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        // 认领量以实际产出为上限，仓库必须是成品入库的那个仓
        verify(salesOrderService).fulfillFromProduction(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL,
                new BigDecimal("6"), USER);
        ArgumentCaptor<ProductionLog> captor = ArgumentCaptor.forClass(ProductionLog.class);
        // 月表后缀在这里是 tableRouter 桩返回的 null，anyString() 不匹配 null，只能用 any()
        verify(logMapper, atLeastOnce()).insertLog(captor.capture(), any());
        ProductionLog claim = captor.getAllValues().stream()
                .filter(log -> "订单认领".equals(log.getOperation())).findFirst().orElse(null);
        assertTrue(claim != null, "应该写一条订单认领日志");
        assertEquals(0, claim.getQty().compareTo(new BigDecimal("6")));
        // 认领不产生成本，成品成本在入库时就结算完了
        assertEquals(0, claim.getCostAmount().compareTo(BigDecimal.ZERO));
        assertTrue(claim.getRemark().contains(ORDER_CODE));
    }

    /**
     * 少做了按分配顺序先到先得，不按比例摊。
     *
     * <p>计划做 10 分给两张订单各 6 和 4，实际只做出 6：第一张拿满，第二张一个都拿不到。
     * 按比例摊会变成 3.6 / 2.4，两张订单谁都发不了货。</p>
     */
    @Test
    void completeClaimsInAllocationOrderWhenOutputFallsShort()
    {
        givenCompletableOrder(allocation(ORDER_CODE, ORDER_DETAIL, "6"),
                allocation("XS20260820002", "XSMX20260820002", "4"));
        when(salesOrderService.fulfillFromProduction(eq(SHOP), eq(ORDER_CODE), anyString(), anyString(),
                any(), anyString())).thenReturn(new BigDecimal("6"));

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        verify(salesOrderService).fulfillFromProduction(SHOP, ORDER_CODE, ORDER_MONTH, ORDER_DETAIL,
                new BigDecimal("6"), USER);
        // 第一张就把 6 个吃光了，第二张这次一点都认领不到
        verify(salesOrderService, never()).fulfillFromProduction(anyString(), eq("XS20260820002"),
                anyString(), anyString(), any(), anyString());
    }

    /** 订单侧没占到（订单被取消、缺口已被别的方式补上）时不写日志，也不该让完工失败 */
    @Test
    void completeSurvivesOrderThatCannotBeClaimed()
    {
        givenCompletableOrder(allocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(salesOrderService.fulfillFromProduction(anyString(), anyString(), anyString(), anyString(),
                any(), anyString())).thenReturn(BigDecimal.ZERO);

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        ArgumentCaptor<ProductionLog> captor = ArgumentCaptor.forClass(ProductionLog.class);
        // 月表后缀在这里是 tableRouter 桩返回的 null，anyString() 不匹配 null，只能用 any()
        verify(logMapper, atLeastOnce()).insertLog(captor.capture(), any());
        assertTrue(captor.getAllValues().stream().noneMatch(log -> "订单认领".equals(log.getOperation())),
                "没占到就不该写认领日志");
        // 工单该完工还是完工了
        verify(productionOrderMapper).updateExecution(eq(SHOP), eq(CODE), eq("生产中"), eq("已完工"), any(), any(),
                any(), any(), any(), any(), any(), any(), eq(USER));
    }

    /** 备货生产的工单没有来源订单，完工时不该去碰订单模块 */
    @Test
    void completeOfStockOrderDoesNotTouchSalesOrders()
    {
        givenCompletableOrder();
        order.setSourceType("stock");

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        verify(salesOrderService, never()).fulfillFromProduction(anyString(), anyString(), anyString(),
                anyString(), any(), anyString());
    }

    // -------------------------------------------------- 成品的批次 / 序列号

    /**
     * 批次管理的成品：完工入库明细必须带批次号，没填就自动生成。
     *
     * <p>缺了批次号，过账时不会建批次记录（{@code executeInbound} 只在明细带批次时建），
     * 这批成品在库存里就只有数量、没有批次，卖的时候 FIFO 会报「批次库存不足」。</p>
     */
    @Test
    void completeGeneratesBatchForBatchTrackedProduct()
    {
        givenCompletableOrder();
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("batch"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0001");

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        DocumentDetail line = productInboundLine();
        assertEquals("PC0001", line.getBatchCode());
        // 规格和单位取商品档案：批次记录要落这两个字段，产出明细上没有
        assertEquals("标准", line.getSpec());
        assertEquals("个", line.getUnit());
    }

    /** 用户自己填了批次号就用他填的，不再生成 */
    @Test
    void completeKeepsBatchCodeFilledByUser()
    {
        givenCompletableOrder();
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("batch"));
        ProductionExecutionRequest request = completeRequest("6");
        request.getOutputs().get(0).setBatchCode(" B20260822 ");

        service.completeProductionOrder(SHOP, CODE, request, USER);

        assertEquals("B20260822", productInboundLine().getBatchCode());
    }

    /** 序列号管理的成品：序列号个数必须等于实际产出，对不上直接拒 */
    @Test
    void completeRejectsSerialCountMismatch()
    {
        // 一张单据都开不出来，所以不用 givenCompletableOrder（它会桩单号生成器）
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                new ArrayList<>(Collections.singletonList(output("10"))));
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("serial"));
        ProductionExecutionRequest request = completeRequest("6");
        request.getOutputs().get(0).setSerialNumbers(Arrays.asList("SN1", "SN2"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeProductionOrder(SHOP, CODE, request, USER));

        assertTrue(error.getMessage().contains("必须填写 6 个"), error.getMessage());
        verify(documentService, never()).insertDocument(any());
    }

    /** 序列号齐了就原样带给库存模块，批次号照样自动生成——序列号挂在批次下 */
    @Test
    void completeCarriesSerialNumbersOfProduct()
    {
        givenCompletableOrder();
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("serial"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0002");
        ProductionExecutionRequest request = completeRequest("3");
        request.getOutputs().get(0).setSerialNumbers(Arrays.asList("SN1", "SN2", "SN3"));

        service.completeProductionOrder(SHOP, CODE, request, USER);

        DocumentDetail line = productInboundLine();
        assertEquals("PC0002", line.getBatchCode());
        assertEquals(Arrays.asList("SN1", "SN2", "SN3"), line.getSerialNumbers());
    }

    /** 不做追踪的成品填了批次号是录入错误，与其默默丢掉不如说清楚 */
    @Test
    void completeRejectsBatchCodeOnUntrackedProduct()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                new ArrayList<>(Collections.singletonList(output("10"))));
        ProductionExecutionRequest request = completeRequest("6");
        request.getOutputs().get(0).setBatchCode("B001");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeProductionOrder(SHOP, CODE, request, USER));

        assertTrue(error.getMessage().contains("不做批次追踪"), error.getMessage());
    }

    // -------------------------------------------------- 定制件的专属批次

    /**
     * 定制的分配：完工入库时把这批成品绑给来源订单明细。
     *
     * <p>这是「定制货不能被别的订单发走」的起点——绑定之后，别的订单出库挑批次时
     * 会跳过它（见 {@code InventoryBatchOwnerTest}）。</p>
     */
    @Test
    void completeBindsBatchToCustomOrderDetail()
    {
        givenCompletableOrder(customAllocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("batch"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0001");
        when(salesOrderService.fulfillFromProduction(eq(SHOP), eq(ORDER_CODE), eq(ORDER_MONTH),
                eq(ORDER_DETAIL), any(), eq(USER))).thenReturn(new BigDecimal("6"));

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        verify(batchService).bindOwner(SHOP, "PC0001", "P001", PRODUCT_WARE, StockState.available(),
                ORDER_CODE, ORDER_DETAIL);
    }

    /**
     * 普通件的分配不绑。
     *
     * <p>普通标准件做出来是通用库存，谁先发货谁拿到；绑上反而会让仓库里躺着一批
     * 别的订单用不了的货。挂订单只是为了能从订单追溯到工单。</p>
     */
    @Test
    void completeDoesNotBindBatchForNonCustomAllocation()
    {
        givenCompletableOrder(allocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(goodsMapper.selectGoodsByCode(SHOP, "P001")).thenReturn(goods("batch"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0001");
        when(salesOrderService.fulfillFromProduction(eq(SHOP), eq(ORDER_CODE), eq(ORDER_MONTH),
                eq(ORDER_DETAIL), any(), eq(USER))).thenReturn(new BigDecimal("6"));

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        verify(batchService, never()).bindOwner(anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    /**
     * 定制件即使商品档案说「不做批次追踪」，也要补一个批次号。
     *
     * <p>专属关系挂在批次上，没有批次行就没有落脚点，别的订单照样能把货发走。
     * 定制件本来也该说得出「客户拿到的是哪一批」。</p>
     */
    @Test
    void completeGeneratesBatchForCustomEvenWhenProductIsUntracked()
    {
        givenCompletableOrder(customAllocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0009");
        when(salesOrderService.fulfillFromProduction(eq(SHOP), eq(ORDER_CODE), eq(ORDER_MONTH),
                eq(ORDER_DETAIL), any(), eq(USER))).thenReturn(new BigDecimal("6"));

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        assertEquals("PC0009", productInboundLine().getBatchCode());
        verify(batchService).bindOwner(SHOP, "PC0009", "P001", PRODUCT_WARE, StockState.available(),
                ORDER_CODE, ORDER_DETAIL);
    }

    /** 订单侧认领不到（订单被取消、明细被删）时不绑：那批货没有归属，退回通用库存 */
    @Test
    void completeSkipsBindingWhenOrderCannotBeClaimed()
    {
        givenCompletableOrder(customAllocation(ORDER_CODE, ORDER_DETAIL, "6"));
        when(batchService.generateBatchCode(SHOP)).thenReturn("PC0009");
        when(salesOrderService.fulfillFromProduction(anyString(), anyString(), anyString(), anyString(),
                any(), anyString())).thenReturn(BigDecimal.ZERO);

        service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER);

        verify(batchService, never()).bindOwner(anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString());
    }

    // -------------------------------------------------- 重复完工

    /**
     * 重复提交完工：工单先锁行，锁内状态已经变了时回写更新 0 行，整笔回滚。
     *
     * <p>不拦的话第二次会再开一套消耗单和成品入库单，而消耗扣的是「良品·不在库」这一格，
     * 扣掉的是别的工单还在车间的料。</p>
     */
    @Test
    void completeRejectsDuplicateSubmission()
    {
        givenCompletableOrder();
        when(productionOrderMapper.updateExecution(eq(SHOP), eq(CODE), eq("生产中"), eq("已完工"),
                any(), any(), any(), any(), any(), any(), any(), any(), eq(USER))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.completeProductionOrder(SHOP, CODE, completeRequest("6"), USER));

        assertTrue(error.getMessage().contains("已经完工"), error.getMessage());
        verify(productionOrderMapper, atLeastOnce()).lockProductionOrder(SHOP, CODE);
    }

    /** 完工入库单（SCCPRK）的第一行明细 */
    private DocumentDetail productInboundLine()
    {
        for (DocumentMain document : capturedDocuments(2))
        {
            if (InventoryDocumentTypes.PRODUCT_INBOUND.equals(document.getDocType()))
            {
                return document.getDetails().get(0);
            }
        }
        throw new AssertionError("没有开出成品入库单");
    }

    /**
     * 一张「生产中」、可以直接完工的订单驱动工单：一行产出计划 10、一行物料已领 10。
     * 传进来的分配挂在那行产出上。
     */
    private void givenCompletableOrder(ProductionOrderAllocation... allocations)
    {
        ProductionOrderDetail out = output("10");
        out.setLineNo(1);
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "0"))),
                new ArrayList<>(Collections.singletonList(out)));
        order.setSourceType("order");
        // 分配必须走主表的 JSON：loadChildren 每次都按 orderallocation 重建产出行上的
        // allocations，直接往 detail 对象上塞会被它覆盖成空。这么写也顺带覆盖了序列化往返
        List<ProductionOrderAllocation> flat = new ArrayList<>();
        for (ProductionOrderAllocation allocation : allocations)
        {
            allocation.setOutputLineNo(1);
            flat.add(allocation);
        }
        order.setOrderAllocation(flat.isEmpty() ? null : JSON.toJSONString(flat));
        when(codeGenerator.nextProductionConsumeCode()).thenReturn("SCXH9001");
        when(codeGenerator.nextProductInboundCode()).thenReturn("SCCPRK9001");
    }

    /** 完工请求：录入实际产出，物料按默认全消耗 */
    private ProductionExecutionRequest completeRequest(String actualOutput)
    {
        ProductionExecutionRequest request = request();
        request.setProductWareCode(PRODUCT_WARE);
        ProductionOrderDetail input = new ProductionOrderDetail();
        input.setDetailCode(OUTPUT);
        input.setActualYQty(new BigDecimal(actualOutput));
        request.setOutputs(Collections.singletonList(input));
        return request;
    }

    private ProductionOrderAllocation allocation(String orderCode, String detailCode, String qty)
    {
        ProductionOrderAllocation allocation = new ProductionOrderAllocation();
        allocation.setOrderCode(orderCode);
        allocation.setTableSuffix(ORDER_MONTH);
        allocation.setDetailCode(detailCode);
        allocation.setQty(new BigDecimal(qty));
        return allocation;
    }

    /** 定制的分配行：完工入库时批次要绑给它。 */
    private ProductionOrderAllocation customAllocation(String orderCode, String detailCode, String qty)
    {
        ProductionOrderAllocation allocation = allocation(orderCode, detailCode, qty);
        allocation.setCustomMade(Boolean.TRUE);
        return allocation;
    }

    /** 订单驱动的建单请求：一个产出、一条分配、一行物料 */
    private ProductionOrder orderDrivenOrder(String planQty, String allocatedQty)
    {
        ProductionOrderDetail output = newOutput("P001", "成品A", planQty);
        output.setMaterials(new ArrayList<>(Collections.singletonList(bom("M001", "1"))));
        ProductionOrderAllocation allocation = new ProductionOrderAllocation();
        allocation.setOrderCode(ORDER_CODE);
        allocation.setTableSuffix(ORDER_MONTH);
        allocation.setDetailCode(ORDER_DETAIL);
        allocation.setQty(new BigDecimal(allocatedQty));
        output.setAllocations(new ArrayList<>(Collections.singletonList(allocation)));
        ProductionOrder input = newOrder(Collections.singletonList(output));
        input.setSourceType("order");
        return input;
    }

    /** 被分配的销售订单明细。排产上限只看 pendingQty。 */
    private SalesOrderItem orderItem(String goodsCode, String pendingQty, String sourceCode)
    {
        SalesOrderItem item = new SalesOrderItem();
        item.setDetailCode(ORDER_DETAIL);
        item.setOrderCode(ORDER_CODE);
        item.setGoodsCode(goodsCode);
        item.setGoodsName("成品A");
        item.setPendingQty(new BigDecimal(pendingQty));
        item.setSourceCode(sourceCode);
        return item;
    }

    /** 没完工就关闭是「这批不做了」的终止，必须说明原因 */
    @Test
    void closeRequiresReasonWhenOrderNotCompleted()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeProductionOrder(SHOP, CODE, "   ", USER));
        assertTrue(error.getMessage().contains("终止要说明原因"), error.getMessage());
        verify(productionOrderMapper, never()).updateStatus(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * 一次都没领过料的工单（待生产）可以直接终止：车间本来就是空的。
     * 以前这种工单只能删，删了就什么痕迹都不留。
     */
    @Test
    void closeAbortsPlannedOrderWithReason()
    {
        givenOrder("待生产", new ArrayList<>(Collections.singletonList(material("10", "0", "0", "0"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionLogCode()).thenReturn("SCSJ0100");

        service.closeProductionOrder(SHOP, CODE, "客户砍单", USER);

        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /**
     * 生产中的工单，料退干净了就能终止——这条路以前是不存在的：
     * 关闭只收已完工、删除只收待生产，领过料又不想做的工单永远清不掉。
     */
    @Test
    void closeAbortsInProductionOrderAndLogsReason()
    {
        // 领 10、耗 0、退 10，车间已经清空
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "10"))),
                Collections.singletonList(output("10")));
        when(codeGenerator.nextProductionLogCode()).thenReturn("SCSJ0101");
        when(tableRouter.forBusinessDate(any())).thenReturn("202608");

        service.closeProductionOrder(SHOP, CODE, " 原料涨价，这批停掉 ", USER);

        ArgumentCaptor<ProductionLog> captor = ArgumentCaptor.forClass(ProductionLog.class);
        verify(logMapper).insertLog(captor.capture(), eq("202608"));
        ProductionLog log = captor.getValue();
        assertEquals("终止", log.getOperation());
        // 终止不开库存单据，doccode 记工单号本身
        assertEquals(CODE, log.getDocCode());
        assertEquals(0, log.getQty().compareTo(BigDecimal.ZERO));
        assertEquals(0, log.getCostAmount().compareTo(BigDecimal.ZERO));
        assertTrue(log.getRemark().contains("从「生产中」终止"), log.getRemark());
        assertTrue(log.getRemark().contains("原料涨价"), log.getRemark());
        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
    }

    /** 车间还有料的工单不能终止：关闭之后没有任何入口能把料取回来 */
    @Test
    void closeAbortRejectsOrderWithMaterialStillInWorkshop()
    {
        givenOrder("生产中", new ArrayList<>(Collections.singletonList(material("10", "10", "0", "4"))),
                Collections.singletonList(output("10")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeProductionOrder(SHOP, CODE, "不做了", USER));
        assertTrue(error.getMessage().contains("未退回的在制物料"));
        assertTrue(error.getMessage().contains("原料A 6"));
        verify(logMapper, never()).insertLog(any(), any());
    }

    /** 已关闭的工单不能再关一次 */
    @Test
    void closeRejectsAlreadyClosedOrder()
    {
        givenOrder("已关闭", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(output("10")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.closeProductionOrder(SHOP, CODE, "再关一次", USER));
        assertTrue(error.getMessage().contains("已经关闭"), error.getMessage());
        verify(productionOrderMapper, never()).updateStatus(anyString(), anyString(), anyString(), anyString());
    }

    /** 正常完工后的关闭不需要原因，也不写终止日志 */
    @Test
    void closeCompletedOrderNeedsNoReasonAndWritesNoAbortLog()
    {
        givenOrder("已完工", new ArrayList<>(Collections.singletonList(material("10", "10", "10", "0"))),
                Collections.singletonList(output("10")));

        service.closeProductionOrder(SHOP, CODE, null, USER);

        verify(productionOrderMapper).updateStatus(SHOP, CODE, "已关闭", USER);
        verify(logMapper, never()).insertLog(any(), any());
    }
}
