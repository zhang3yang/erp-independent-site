package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ProductionLog;
import com.ruoyi.system.domain.ProductionMaterial;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.ProductionOrderAllocation;
import com.ruoyi.system.domain.ProductionOrderDetail;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.ProductionExecutionRequest;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.ProductionLogMapper;
import com.ruoyi.system.mapper.ProductionMaterialMapper;
import com.ruoyi.system.mapper.ProductionOrderDetailMapper;
import com.ruoyi.system.mapper.ProductionOrderMapper;
import com.ruoyi.system.service.IProductionOrderService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.SerialNumberRemarkCodec;
import com.alibaba.fastjson2.JSON;

/** 生产工单基础服务实现，库存操作统一通过库存单据服务过账。 */
@Service
public class ProductionOrderServiceImpl implements IProductionOrderService
{
    private static final String STATUS_PLANNED = "待生产";
    /** 已领部分物料、尚未领足；分批领料时的中间状态 */
    private static final String STATUS_WAIT_PICK = "待领料";
    private static final String STATUS_IN_PRODUCTION = "生产中";
    private static final String STATUS_COMPLETED = "已完工";
    private static final String STATUS_CLOSED = "已关闭";

    /** 工单来源：订单驱动，产出要分配到具体的销售订单明细 */
    private static final String SOURCE_ORDER = "order";
    /** 工单来源：备货生产，产出直接进库存，不挂订单 */
    private static final String SOURCE_STOCK = "stock";

    @Autowired private ProductionOrderMapper productionOrderMapper;
    @Autowired private ProductionOrderDetailMapper detailMapper;
    @Autowired private ProductionMaterialMapper materialMapper;
    @Autowired private ProductionLogMapper logMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    @Autowired private InventoryDocumentTableRouter tableRouter;
    @Autowired private IDocumentService documentService;
    /** 完工入库要按成品档案的批次策略决定批次号和序列号，与采购入库同一套口径 */
    @Autowired private InventoryGoodsMapper goodsMapper;
    @Autowired private IInventoryBatchService batchService;
    /**
     * 订单驱动工单要读销售订单的未发量，并把工单号回写到订单明细上。单向依赖：
     * 订单模块不反过来引用生产，不存在循环。走服务不走 Mapper，是为了让明细归属校验
     * （明细存在、且确实属于这张订单）留在订单模块自己手里。
     */
    @Autowired private ISalesOrderService salesOrderService;

    @Override
    public ProductionOrder selectProductionOrderById(String shopCode, Long id)
    {
        ProductionOrder order = productionOrderMapper.selectProductionOrderById(shopCode, id);
        return loadChildren(order);
    }

    @Override
    public ProductionOrder selectProductionOrderByCode(String shopCode, String productionCode)
    {
        ProductionOrder order = productionOrderMapper.selectProductionOrderByCode(shopCode, productionCode);
        return loadChildren(order);
    }

    /**
     * 装载子表。
     *
     * <p>物料清单在库里是一张平表，但业务上每一行都属于某个产出商品（配方按产出分开），
     * 所以读出来后按 detailcode 归回各产出行。{@code order.materials} 仍保留完整的平表，
     * 领料/消耗/退料按 materialCode 逐行处理，不关心归属。</p>
     *
     * <p>订单分配同理：主表存的是一个工单级的 JSON 数组，读出来按产出行号归回各产出行。</p>
     */
    private ProductionOrder loadChildren(ProductionOrder order)
    {
        if (order != null)
        {
            List<ProductionOrderDetail> details = detailMapper.selectByProductionCode(order.getShopCode(), order.getProductionCode());
            List<ProductionMaterial> materials = materialMapper.selectByProductionCode(order.getShopCode(), order.getProductionCode());
            Map<String, List<ProductionMaterial>> byDetail = new LinkedHashMap<>();
            for (ProductionMaterial material : materials)
            {
                byDetail.computeIfAbsent(material.getDetailCode(), key -> new ArrayList<>()).add(material);
            }
            Map<Integer, List<ProductionOrderAllocation>> byLine = new LinkedHashMap<>();
            for (ProductionOrderAllocation allocation : parseAllocations(order.getOrderAllocation()))
            {
                byLine.computeIfAbsent(allocation.getOutputLineNo(), key -> new ArrayList<>()).add(allocation);
            }
            for (ProductionOrderDetail detail : details)
            {
                List<ProductionMaterial> bom = byDetail.get(detail.getDetailCode());
                detail.setMaterials(bom == null ? new ArrayList<>() : bom);
                List<ProductionOrderAllocation> allocations = byLine.get(detail.getLineNo());
                detail.setAllocations(allocations == null ? new ArrayList<>() : allocations);
            }
            order.setDetails(details);
            order.setMaterials(materials);
        }
        return order;
    }

    /**
     * 反序列化主表的 orderallocation。
     *
     * <p>解析失败只当没有分配，不抛：这一列是历史遗留的「模型透传字段」，
     * 老数据里可能是随手写的文本，让整张工单查不出来不值当。</p>
     */
    private List<ProductionOrderAllocation> parseAllocations(String json)
    {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try
        {
            List<ProductionOrderAllocation> parsed = JSON.parseArray(json, ProductionOrderAllocation.class);
            return parsed == null ? new ArrayList<>() : parsed;
        }
        catch (RuntimeException error)
        {
            return new ArrayList<>();
        }
    }

    @Override
    public List<ProductionOrder> selectProductionOrderList(ProductionOrder order)
    {
        return productionOrderMapper.selectProductionOrderList(order);
    }

    @Override
    @Transactional
    public int insertProductionOrder(ProductionOrder order)
    {
        validateOrder(order);
        order.setProductionCode(codeGenerator.nextProductionOrderCode());
        order.setStatus(STATUS_PLANNED);
        order.setActualYQty(BigDecimal.ZERO);
        order.setMaterialCost(BigDecimal.ZERO);
        order.setProcessingCost(BigDecimal.ZERO);
        order.setFreightCost(BigDecimal.ZERO);
        order.setOtherCost(BigDecimal.ZERO);
        order.setTotalCost(BigDecimal.ZERO);
        order.setUnitCost(BigDecimal.ZERO);
        // 分配要落进主表的 orderallocation，所以必须赶在 insert 之前算好；
        // 产出行号在 prepareChildren 里定，分配靠行号归属，顺序不能倒过来
        prepareChildren(order);
        applyAllocations(order);
        productionOrderMapper.insertProductionOrder(order);
        if (!order.getDetails().isEmpty()) detailMapper.batchInsert(order.getDetails());
        if (!order.getMaterials().isEmpty()) materialMapper.batchInsert(order.getMaterials());
        writeBackOrderSource(order, order.getProductionCode());
        return 1;
    }

    @Override
    @Transactional
    public int updateProductionOrder(ProductionOrder order)
    {
        validateOrder(order);
        ProductionOrder existing = productionOrderMapper.selectProductionOrderById(order.getShopCode(), order.getId());
        if (existing == null) throw new ServiceException("生产工单不存在");
        // 锁行后按锁内的状态再判一次：下面要先删子表再更新主表，删是不可回退的，
        // 不能在「读的时候还是待生产、删的时候已经领过料」这种缝隙里动手
        productionOrderMapper.lockProductionOrder(order.getShopCode(), existing.getProductionCode());
        existing = productionOrderMapper.selectProductionOrderByCode(order.getShopCode(), existing.getProductionCode());
        if (existing == null) throw new ServiceException("生产工单不存在");
        if (!STATUS_PLANNED.equals(existing.getStatus())) throw new ServiceException("只有待生产状态的工单才能修改");
        order.setProductionCode(existing.getProductionCode());
        // 先松开旧分配占的订单明细，否则新分配还想要同一条明细时会撞上「已挂在本工单」的占用检查，
        // 改单只是换个数量也过不去。松开后再校验，缺口按松开后的状态现算
        releaseOrderSource(existing);
        detailMapper.deleteByProductionCode(order.getShopCode(), order.getProductionCode());
        materialMapper.deleteByProductionCode(order.getShopCode(), order.getProductionCode());
        prepareChildren(order);
        applyAllocations(order);
        if (!order.getDetails().isEmpty()) detailMapper.batchInsert(order.getDetails());
        if (!order.getMaterials().isEmpty()) materialMapper.batchInsert(order.getMaterials());
        int updated = productionOrderMapper.updateProductionOrder(order);
        writeBackOrderSource(order, order.getProductionCode());
        return updated;
    }

    /**
     * 删除工单。
     *
     * <p>只允许删待生产的工单：一旦领过料就已经开过库存单据、写过生产日志，删掉工单会让
     * 那些单据的来源单号指向一个不存在的工单，追溯断链。已经开工的工单只能走关闭。</p>
     */
    @Override
    @Transactional
    public int deleteProductionOrder(String shopCode, String productionCode)
    {
        // 先锁行：不锁的话「删除」和「领料」并发时，删除读到的还是待生产，
        // 料已经领出去了工单却没了
        productionOrderMapper.lockProductionOrder(shopCode, productionCode);
        ProductionOrder existing = productionOrderMapper.selectProductionOrderByCode(shopCode, productionCode);
        if (existing == null) throw new ServiceException("生产工单不存在");
        if (!STATUS_PLANNED.equals(existing.getStatus()))
        {
            throw new ServiceException("只有待生产状态的工单才能删除，当前状态：" + existing.getStatus()
                    + "；已开工的工单请使用关闭");
        }
        // 工单没了，被它占住的订单明细要放回去，否则那些明细的来源单号会指向一张不存在的工单，
        // 既建不了新工单（占用检查过不去）也看不出到底谁在做
        releaseOrderSource(existing);
        detailMapper.deleteByProductionCode(shopCode, productionCode);
        materialMapper.deleteByProductionCode(shopCode, productionCode);
        return productionOrderMapper.deleteProductionOrder(shopCode, productionCode);
    }

    /**
     * 关闭工单 —— 工单的唯一结案动作，此后它不再产出任何东西。
     *
     * <p>两种用法共用这一条路径：</p>
     * <ul>
     *   <li><b>已完工关闭</b>：正常收尾，成本就此定案，之后不能再调费用；</li>
     *   <li><b>未完工终止</b>（待生产 / 待领料 / 生产中）：这批不做了。必须给原因，
     *       原因落进生产日志。以前这两种状态既关不掉也删不掉（删只收待生产），
     *       领了料又不想做的工单就成了永远清不掉的死单，还一直占着来源订单——
     *       销售订单撤审时会被 {@code assertNoOpenProductions} 拦住，那张订单跟着一起锁死。</li>
     * </ul>
     *
     * <p>不论从哪个状态进来，<b>车间必须清空</b>：退料只放行「待领料 / 生产中 / 已完工」，
     * 一旦关闭就再没有任何入口能把料取回来，剩下的量会永远挂在「良品·不在库」——
     * 既不算可用库存，也不在任何一张单据的可操作范围里，账上凭空少一块。待生产工单
     * 一颗料都没领，这条校验自然通过。</p>
     *
     * <p>关闭同时松开还没做完的来源订单明细，见 {@link #releaseUnfinishedOrderSources}。
     * 终止的工单没有产出，缺口都还在，等于全部松开。</p>
     *
     * <p>状态统一落「已关闭」，不为终止单开一个状态：是不是半路停的，看实际产出和完工
     * 日期就知道，多一个状态所有筛选、报表、按钮判断都要跟着改一遍。</p>
     */
    @Override
    @Transactional
    public int closeProductionOrder(String shopCode, String productionCode, String reason, String updateUser)
    {
        ProductionOrder existing = requireOrder(shopCode, productionCode);
        if (STATUS_CLOSED.equals(existing.getStatus())) throw new ServiceException("工单已经关闭");
        boolean aborted = !STATUS_COMPLETED.equals(existing.getStatus());
        if (aborted && isBlank(reason))
        {
            throw new ServiceException("工单还没完工，终止要说明原因（这批为什么不做了）");
        }
        List<String> outstanding = new ArrayList<>();
        for (ProductionMaterial material : existing.getMaterials())
        {
            // 与退料口径一致：可退 = 已领 - 已耗 - 已退
            BigDecimal remaining = zeroIfNull(material.getPickedQty())
                    .subtract(zeroIfNull(material.getActualQty()))
                    .subtract(zeroIfNull(material.getReturnedQty()));
            if (remaining.signum() > 0)
            {
                outstanding.add(material.getGoodsName() + " " + remaining.stripTrailingZeros().toPlainString());
            }
        }
        if (!outstanding.isEmpty())
        {
            throw new ServiceException("工单还有未退回的在制物料，关闭后无法再退料，请先退料："
                    + String.join("、", outstanding));
        }
        releaseUnfinishedOrderSources(existing);
        if (aborted) insertAbortLog(existing, reason);
        return productionOrderMapper.updateStatus(shopCode, productionCode, STATUS_CLOSED, updateUser);
    }

    /**
     * 终止日志：从哪个状态停的、为什么。
     *
     * <p>终止不开库存单据，所以和费用调整一样，{@code doccode} 记工单号本身、{@code doctype}
     * 记来源类型 SC（三个列都是 NOT NULL）。{@code qty} 和 {@code costamount} 都是 0——
     * 终止本身不动数量也不产生成本，料是之前退料退回去的，各有各的日志。</p>
     */
    private void insertAbortLog(ProductionOrder order, String reason)
    {
        ProductionLog log = new ProductionLog();
        log.setShopCode(order.getShopCode());
        log.setLogCode(codeGenerator.nextProductionLogCode());
        log.setProductionCode(order.getProductionCode());
        log.setOperation("终止");
        log.setDocCode(order.getProductionCode());
        log.setDocType("SC");
        log.setGoodsCode(order.getProductCode());
        log.setQty(BigDecimal.ZERO);
        log.setCostAmount(BigDecimal.ZERO);
        Date operationTime = new Date();
        log.setOperationTime(operationTime);
        String note = "从「" + order.getStatus() + "」终止：" + reason.trim();
        log.setRemark(note.length() > 500 ? note.substring(0, 500) : note);
        logMapper.insertLog(log, tableRouter.forBusinessDate(operationTime));
    }

    /**
     * 关闭工单时松开<b>还没做完</b>的来源订单明细。
     *
     * <p>关闭是工单的结案动作，此后它不会再产出任何东西。缺口还在的那些明细如果继续挂着
     * 这张工单的单号，选缺口界面上就一直显示「已挂 SCxxx」——计划员看着像被占死了，
     * 其实 {@link #requireDetailFree} 早就放行已关闭的工单。把单号清掉，界面和实际才一致。
     * 备货来源仍是「生产」：业务意图没变，只是暂时没有工单在做。</p>
     *
     * <p><b>已经发完的不动。</b>那条 {@code sourcecode} 是追溯线索——这批货是哪张工单
     * 做出来的，清掉就断了，而它也不会挡任何人的路：发完的明细不会再出现在选单界面上。</p>
     *
     * <p>判据是<b>未发量</b>不是缺口。原先按缺口判，理由是「没有缺口就不会出现在选单界面上」——
     * 那个前提已经不成立：现在没缺口的明细照样可以排产（定制品），留着指向已关闭工单的
     * sourcecode 只会让选单界面显示一个误导人的「已挂 SCxxx」。</p>
     *
     * <p>订单侧的任何变故都不该让工单关不掉——明细被删、订单取消、已经被别的工单接手，
     * 一律跳过。关闭的前提（车间已清空）在上面校验过了，那才是关闭真正的门槛。</p>
     */
    private void releaseUnfinishedOrderSources(ProductionOrder order)
    {
        if (!SOURCE_ORDER.equals(order.getSourceType())) return;
        for (ProductionOrderAllocation allocation : parseAllocations(order.getOrderAllocation()))
        {
            if (allocation == null || isBlank(allocation.getDetailCode())) continue;
            if (!MonthTableSuffix.isValid(allocation.getTableSuffix())) continue;
            SalesOrderItem item = salesOrderService.lockOrderItem(order.getShopCode(),
                    allocation.getTableSuffix(), allocation.getDetailCode());
            if (item == null) continue;
            // 挂的已经不是本工单：别人接手过，不要把人家的占用抹掉
            if (!order.getProductionCode().equals(item.getSourceCode())) continue;
            if (zeroIfNull(item.getPendingQty()).signum() <= 0) continue;
            salesOrderService.updateItemProductionOrder(order.getShopCode(), item.getOrderCode(),
                    allocation.getTableSuffix(), allocation.getDetailCode(), null);
        }
    }

    /**
     * 生产领料，对应说明书的 borrow_out。
     *
     * <p>开 SCLL 单据（direction=N），把物料从「良品·在库」转到「良品·不在库」。
     * 二维状态下车间不算仓库，料领出去就离库了——落回在库的话在制品会被当成可用
     * 库存再卖一次。这一步<b>不产生成本</b>——成本要等完工时的生产消耗（SCXH）才结算。</p>
     *
     * <p>支持分批领料：请求里按物料行给出本次领料量，未给出的行按「计划量 - 已领量」
     * 一次领足。全部物料领足后工单转「生产中」，否则停在「待领料」。</p>
     */
    @Override
    @Transactional
    public int pickProductionOrder(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser)
    {
        ProductionOrder order = requireOrder(shopCode, productionCode);
        requireRequest(request);
        if (!STATUS_PLANNED.equals(order.getStatus()) && !STATUS_WAIT_PICK.equals(order.getStatus()))
        {
            throw new ServiceException("只有待生产或待领料工单才能领料，当前状态：" + order.getStatus());
        }
        String ware = required(request.getMaterialWareCode(), "领料仓库不能为空");
        if (order.getMaterials().isEmpty()) throw new ServiceException("生产工单物料明细不能为空");

        Map<String, List<ProductionMaterial>> inputs = indexMaterialInputs(request);
        List<DocumentDetail> details = new ArrayList<>();
        List<ProductionMaterial> picking = new ArrayList<>();
        Map<String, BigDecimal> pickedTotals = new LinkedHashMap<>();
        for (ProductionMaterial material : order.getMaterials())
        {
            BigDecimal remaining = zeroIfNull(material.getTotalQty()).subtract(zeroIfNull(material.getPickedQty()));
            BigDecimal total = BigDecimal.ZERO;
            for (ProductionMaterial line : linesOf(inputs, material, remaining, ProductionMaterial::setPickedQty))
            {
                BigDecimal qty = line.getPickedQty();
                if (qty == null || qty.signum() < 0) throw new ServiceException("物料'" + material.getGoodsName() + "'的领料量不能为负");
                if (qty.signum() == 0) continue;
                total = total.add(qty);
                details.add(buildDetail(material, qty, ware, line));
                picking.add(material);
            }
            if (total.compareTo(remaining) > 0)
            {
                throw new ServiceException("物料'" + material.getGoodsName() + "'本次领料量超过未领量 " + remaining);
            }
            if (total.signum() > 0) pickedTotals.put(material.getMaterialCode(), total);
        }
        if (details.isEmpty()) throw new ServiceException("本次没有需要领料的物料");

        // 同仓库内状态流转：良品·在库 -> 良品·不在库（料到了车间就不在仓库里了）
        DocumentMain document = buildDocument(shopCode, InventoryDocumentTypes.PRODUCTION_PICK, "生产领料", request,
                ware, ware, details, productionCode, order.getProductName(), updateUser);
        document.setFromState(StockState.available());
        document.setToState(StockState.of(QualityFlag.GOOD, OnhandFlag.OUT));
        insertAndConfirm(document, updateUser);

        List<DocumentDetail> posted = postedDetails(document, details);
        for (DocumentDetail line : posted) insertLog(order, "领料", document, line, request);
        // 已领量按物料合计一次累加：一个物料可能拆成多个批次多行领
        for (ProductionMaterial material : order.getMaterials())
        {
            BigDecimal total = pickedTotals.get(material.getMaterialCode());
            if (total == null) continue;
            if (materialMapper.increasePickedQty(shopCode, material.getMaterialCode(), total) == 0)
            {
                throw new ServiceException("物料'" + material.getGoodsName() + "'的领料量超过计划用量，请刷新后重试");
            }
            material.setPickedQty(zeroIfNull(material.getPickedQty()).add(total));
        }

        // 全部物料领足才算进入生产，否则停在待领料等后续批次
        boolean allPicked = true;
        for (ProductionMaterial material : order.getMaterials())
        {
            if (zeroIfNull(material.getPickedQty()).compareTo(zeroIfNull(material.getTotalQty())) < 0) allPicked = false;
        }
        return productionOrderMapper.updateStatus(shopCode, productionCode,
                allPicked ? STATUS_IN_PRODUCTION : STATUS_WAIT_PICK, updateUser);
    }

    /**
     * 把请求里的物料行按 materialCode 分组。
     *
     * <p>同一个物料允许发多行——按批次或序列号管理的物料，一次要动的量可能横跨几个批次，
     * 每行带一个批次（或一组序列号），库存模块才知道搬哪一批的成本。</p>
     */
    private Map<String, List<ProductionMaterial>> indexMaterialInputs(ProductionExecutionRequest request)
    {
        Map<String, List<ProductionMaterial>> values = new LinkedHashMap<>();
        if (request.getMaterials() == null) return values;
        for (ProductionMaterial input : request.getMaterials())
        {
            if (input == null || input.getMaterialCode() == null) continue;
            values.computeIfAbsent(input.getMaterialCode(), key -> new ArrayList<>()).add(input);
        }
        return values;
    }

    /**
     * 取某个物料本次要处理的请求行；调用方没给这个物料时，按兜底数量生成一行。
     *
     * <p>兜底行不带批次和序列号，只有不做批次追踪的物料走得通；需要追踪的物料没指定批次时，
     * 会在过账校验里被拦下并提示「必须填写调拨批次」，比在这里再判一次商品档案更省一次查询。</p>
     */
    private List<ProductionMaterial> linesOf(Map<String, List<ProductionMaterial>> inputs,
            ProductionMaterial material, BigDecimal fallbackQty, BiConsumer<ProductionMaterial, BigDecimal> setter)
    {
        List<ProductionMaterial> lines = inputs.get(material.getMaterialCode());
        if (lines != null && !lines.isEmpty()) return lines;
        ProductionMaterial fallback = new ProductionMaterial();
        fallback.setMaterialCode(material.getMaterialCode());
        setter.accept(fallback, fallbackQty);
        return Collections.singletonList(fallback);
    }

    /** 按物料行和请求行拼一条库存单据明细，批次和序列号原样带给库存模块。 */
    private DocumentDetail buildDetail(ProductionMaterial material, BigDecimal qty, String wareCode,
            ProductionMaterial line)
    {
        DocumentDetail detail = new DocumentDetail();
        detail.setGoodsCode(material.getGoodsCode());
        detail.setGoodsName(material.getGoodsName());
        detail.setSpec(material.getSpec());
        detail.setUnit(material.getUnit());
        detail.setQty(qty);
        detail.setWareCode(wareCode);
        if (line != null)
        {
            String batchCode = line.getBatchCode();
            detail.setBatchCode(batchCode == null || batchCode.trim().isEmpty() ? null : batchCode.trim());
            detail.setSerialNumbers(line.getSerialNumbers());
        }
        return detail;
    }

    @Override
    @Transactional
    public int completeProductionOrder(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser)
    {
        ProductionOrder order = requireOrder(shopCode, productionCode);
        requireRequest(request);
        if (!STATUS_IN_PRODUCTION.equals(order.getStatus())) throw new ServiceException("只有生产中工单才能完工");
        String ware = required(request.getProductWareCode(), "成品入库仓库不能为空");

        // 实际产出数量以录入值为准，某行未录入时退回计划数量。产出为 0 的行不入库。
        Map<String, ProductionOrderDetail> outputInputs = indexOutputs(request);
        BigDecimal outputQty = BigDecimal.ZERO;
        List<DocumentDetail> details = new ArrayList<>();
        List<ProductionOrderDetail> postedOutputs = new ArrayList<>();
        for (ProductionOrderDetail item : order.getDetails())
        {
            ProductionOrderDetail input = outputInputs.get(item.getDetailCode());
            BigDecimal actual = input != null && input.getActualYQty() != null
                    ? input.getActualYQty() : item.getPlanYQty();
            if (actual == null || actual.signum() < 0) throw new ServiceException("实际产出数量不能为负");
            item.setActualYQty(actual);
            outputQty = outputQty.add(actual);
            if (actual.signum() == 0) continue;
            DocumentDetail detail = new DocumentDetail(); detail.setGoodsCode(item.getProductCode());
            detail.setGoodsName(item.getProductName()); detail.setQty(actual);
            detail.setWareCode(ware); details.add(detail);
            // 成品的批次 / 序列号：口径与采购入库一致，批次留空由服务端生成，
            // 序列号必须逐个录（生产环节贴上去的号，系统造不出来）
            applyOutputTracking(order.getShopCode(), detail, input, hasCustomAllocation(item));
            // 批次号回填到产出行上：完工后 fulfillSourceOrders 要拿它把定制货绑给订单明细，
            // 那时手上只有 postedOutputs，摸不到这里的 DocumentDetail
            item.setBatchCode(detail.getBatchCode());
            postedOutputs.add(item);
        }
        if (details.isEmpty() || outputQty.signum() <= 0) throw new ServiceException("实际产出数量必须大于0");

        // 先做生产消耗出库（说明书的 consume_out）：把领用中的物料永久出库，
        // 工单的物料成本就等于这张出库单过账后算出的实际成本。
        MaterialConsumption consumption = consumeMaterials(order, request, updateUser);
        BigDecimal materialCost = consumption.total();

        BigDecimal processingCost = feeOrExisting(request.getProcessingCost(), order.getProcessingCost(), "加工费");
        BigDecimal freightCost = feeOrExisting(request.getFreightCost(), order.getFreightCost(), "运费");
        BigDecimal otherCost = feeOrExisting(request.getOtherCost(), order.getOtherCost(), "其他费用");

        // 总成本 = 物料成本 + 加工费 + 运费 + 其他费用（设计说明书 4.4 流程2）
        BigDecimal totalCost = materialCost.add(processingCost).add(freightCost).add(otherCost);
        // 工单口径的平均单位成本，只用于主表汇总；各产出行的单位成本各算各的
        BigDecimal unitCost = totalCost.divide(outputQty, 6, java.math.RoundingMode.HALF_UP);

        // 配方按产出分开，成本也按产出算：每行先认领自己配方消耗掉的物料成本，
        // 剩下的（加工费、运费、其他费用，以及产出为 0 那些行耗掉的料）按物料成本占比分摊。
        // 各产出用料贵贱不同，摊完的单位成本才不会被拉平。
        List<BigDecimal> baseCosts = new ArrayList<>();
        BigDecimal baseTotal = BigDecimal.ZERO;
        for (ProductionOrderDetail item : postedOutputs)
        {
            BigDecimal base = consumption.of(item.getDetailCode());
            baseCosts.add(base);
            baseTotal = baseTotal.add(base);
        }
        BigDecimal pool = totalCost.subtract(baseTotal);

        // 最后一行吸收分币尾差，保证各行金额之和等于总成本
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < details.size(); i++)
        {
            DocumentDetail detail = details.get(i);
            BigDecimal amount;
            if (i == details.size() - 1)
            {
                amount = totalCost.subtract(allocated);
            }
            else
            {
                // 一点料都没消耗时（比如全部退回、成本只有加工费）没有比例可依，退回按产出数量摊
                BigDecimal share = baseTotal.signum() > 0
                        ? pool.multiply(baseCosts.get(i)).divide(baseTotal, 2, java.math.RoundingMode.HALF_UP)
                        : pool.multiply(detail.getQty()).divide(outputQty, 2, java.math.RoundingMode.HALF_UP);
                amount = baseCosts.get(i).add(share).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            allocated = allocated.add(amount);
            detail.setCostPrice(amount.divide(detail.getQty(), 6, java.math.RoundingMode.HALF_UP));
            detail.setCostAmount(amount);
        }

        DocumentMain document = buildDocument(shopCode, InventoryDocumentTypes.PRODUCT_INBOUND, "生产成品入库", request,
                null, ware, details, productionCode, order.getProductName(), updateUser);
        insertAndConfirm(document, updateUser);

        for (int i = 0; i < postedOutputs.size(); i++)
        {
            ProductionOrderDetail item = postedOutputs.get(i);
            DocumentDetail line = details.get(i);
            detailMapper.updateExecution(shopCode, item.getDetailCode(), item.getActualYQty(),
                    baseCosts.get(i), line.getCostPrice(), line.getCostAmount());
        }
        // 产出为 0 的行也要落库，否则明细上留着旧的实际数量与实际情况不符。
        // 这种行的料已经耗掉了，物料成本照记（成本已经并进公摊池由其它行承担），
        // 单位成本和小计成本记 0——没有产出，摊到它头上的成本没有载体。
        for (ProductionOrderDetail item : order.getDetails())
        {
            if (item.getActualYQty() != null && item.getActualYQty().signum() == 0)
            {
                detailMapper.updateExecution(shopCode, item.getDetailCode(), BigDecimal.ZERO,
                        consumption.of(item.getDetailCode()), BigDecimal.ZERO, BigDecimal.ZERO);
            }
        }
        // 用过账后回读的明细写日志，才能拿到服务端生成的明细号和实际成本金额。
        for (DocumentDetail posted : postedDetails(document, details)) insertLog(order, "入库", document, posted, request);
        fulfillSourceOrders(order, postedOutputs, document, ware, request, updateUser);
        // 带上 expectStatus 兜底：行锁挡住了并发，这里再挡一次「锁等到了、但前一个请求已经完工」——
        // 更新 0 行说明状态已经变了，整个事务回滚，不会留下两套消耗单和成品入库单
        int updated = productionOrderMapper.updateExecution(shopCode, productionCode, STATUS_IN_PRODUCTION,
                STATUS_COMPLETED, outputQty,
                request.getOperationTime() == null ? new Date() : request.getOperationTime(),
                materialCost, totalCost, unitCost, processingCost, freightCost, otherCost, updateUser);
        if (updated == 0) throw new ServiceException("工单状态已变化（可能已经完工），请刷新后重试");
        return updated;
    }

    @Override
    @Transactional
    public int returnProductionMaterial(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser)
    {
        ProductionOrder order = requireOrder(shopCode, productionCode);
        requireRequest(request);
        // 待生产工单还没领料，已关闭工单不允许再动库存。
        if (!STATUS_WAIT_PICK.equals(order.getStatus()) && !STATUS_IN_PRODUCTION.equals(order.getStatus())
                && !STATUS_COMPLETED.equals(order.getStatus()))
        {
            throw new ServiceException("只有待领料、生产中或已完工的工单才能退料，当前状态：" + order.getStatus());
        }
        String ware = required(request.getMaterialWareCode(), "退料仓库不能为空");
        // 退料统一退回「良品·在库·自有」。报废的料退回后再单独登记损坏，
        // 这样良品状态的变化只由损坏登记这一条路径产生。

        Map<String, List<ProductionMaterial>> inputs = indexMaterialInputs(request);
        List<DocumentDetail> details = new ArrayList<>();
        Map<String, BigDecimal> returnedTotals = new LinkedHashMap<>();
        for (ProductionMaterial material : order.getMaterials())
        {
            // 可退 = 已领 - 实际消耗 - 已退。完工前实际消耗为 0，等于已领的都能退回。
            BigDecimal returnable = zeroIfNull(material.getPickedQty())
                    .subtract(zeroIfNull(material.getActualQty()))
                    .subtract(zeroIfNull(material.getReturnedQty()));
            BigDecimal total = BigDecimal.ZERO;
            for (ProductionMaterial line : linesOf(inputs, material, returnable, ProductionMaterial::setReturnedQty))
            {
                BigDecimal qty = line.getReturnedQty();
                if (qty == null || qty.signum() < 0) throw new ServiceException("物料'" + material.getGoodsName() + "'的退料量不能为负");
                if (qty.signum() == 0) continue;
                total = total.add(qty);
                details.add(buildDetail(material, qty, ware, line));
            }
            if (total.compareTo(returnable) > 0)
            {
                throw new ServiceException("物料'" + material.getGoodsName() + "'的退料量超过可退数量 " + returnable);
            }
            if (total.signum() > 0) returnedTotals.put(material.getMaterialCode(), total);
        }
        if (details.isEmpty()) throw new ServiceException("没有可退回的生产物料");

        // 同仓库内状态回流：良品·不在库 -> 良品·在库。料从头到尾都在库存表里，
        // 只是换了一格，所以不需要给明细定价——改造前那套「按领料单价原价退回」的
        // 补偿逻辑不再需要。退回来的料坏了要另开损坏登记。
        DocumentMain document = buildDocument(shopCode, InventoryDocumentTypes.PRODUCTION_RETURN, "生产退料", request,
                ware, ware, details, productionCode, order.getProductName(), updateUser);
        document.setFromState(StockState.of(QualityFlag.GOOD, OnhandFlag.OUT));
        document.setToState(StockState.available());
        insertAndConfirm(document, updateUser);
        for (DocumentDetail line : postedDetails(document, details)) insertLog(order, "退料", document, line, request);
        // 累加已退数量而不是清零实际消耗，否则同一批物料会被反复退回库存。
        // 按物料合计一次累加：一个物料可能拆成多个批次多行退。
        for (ProductionMaterial material : order.getMaterials())
        {
            BigDecimal total = returnedTotals.get(material.getMaterialCode());
            if (total == null) continue;
            if (materialMapper.increaseReturnedQty(shopCode, material.getMaterialCode(), total) == 0)
            {
                throw new ServiceException("物料'" + material.getGoodsName() + "'的退料数量超过可退数量，请刷新后重试");
            }
        }
        return 1;
    }

    /**
     * 完工后补录 / 修改三项费用，重算总成本和单位成本。
     *
     * <p>只放行「已完工」——待生产、生产中的工单还没结算成本，改费用没有意义；已关闭的工单
     * 成本已定案，不再接受调整。这样「关闭」就是成本的截止动作，和它校验车间余料是一个意思：
     * 关闭之前把该收的尾巴收完。</p>
     *
     * <p>物料成本不参与重算。它只能由生产消耗（SCXH）单据产生，事后补的是加工费、运费这类
     * 人工录入的费用，动不了已经消耗掉的料。</p>
     *
     * <p><b>库存成本不同步。</b>成品在完工时已按当时的单位成本入库，那批库存的成本不动——
     * 本系统的库存模块没有「只改金额不改数量」的成本调整单据，硬做要改过账引擎，而且成品
     * 一旦部分售出，已售部分的差异没有损益科目可落。因此这里的定位是：<b>工单口径的成本
     * 修正</b>，工单与库存的成本会出现差额，差额逐行写进生产日志，看得见、可追。</p>
     */
    @Override
    @Transactional
    public int adjustProductionCost(String shopCode, String productionCode, ProductionExecutionRequest request, String updateUser)
    {
        ProductionOrder order = requireOrder(shopCode, productionCode);
        requireRequest(request);
        if (!STATUS_COMPLETED.equals(order.getStatus()))
        {
            throw new ServiceException("只有已完工且未关闭的工单才能调整费用，当前状态：" + order.getStatus());
        }
        BigDecimal outputQty = zeroIfNull(order.getActualYQty());
        if (outputQty.signum() <= 0) throw new ServiceException("工单实际产出为0，无法分摊成本");

        BigDecimal materialCost = zeroIfNull(order.getMaterialCost());
        BigDecimal processingCost = feeOrExisting(request.getProcessingCost(), order.getProcessingCost(), "加工费");
        BigDecimal freightCost = feeOrExisting(request.getFreightCost(), order.getFreightCost(), "运费");
        BigDecimal otherCost = feeOrExisting(request.getOtherCost(), order.getOtherCost(), "其他费用");
        BigDecimal totalCost = materialCost.add(processingCost).add(freightCost).add(otherCost);
        if (totalCost.compareTo(zeroIfNull(order.getTotalCost())) == 0)
        {
            throw new ServiceException("三项费用没有变化，无需调整");
        }
        BigDecimal unitCost = totalCost.divide(outputQty, 6, java.math.RoundingMode.HALF_UP);

        // 产出为 0 的行不参与分摊，与完工时的口径一致
        List<ProductionOrderDetail> outputs = new ArrayList<>();
        for (ProductionOrderDetail item : order.getDetails())
        {
            if (zeroIfNull(item.getActualYQty()).signum() > 0) outputs.add(item);
        }
        if (outputs.isEmpty()) throw new ServiceException("工单没有实际产出的明细行，无法分摊成本");

        String note = costAdjustNote(order, processingCost, freightCost, otherCost, unitCost);
        Date operationTime = request.getOperationTime() == null ? new Date() : request.getOperationTime();
        // 分摊口径与完工时相同：各行认领自己的物料成本，费用按物料成本占比摊，末行吸收分币尾差。
        // 各行的物料成本是完工时落库的 materialcost——只有它留着，才拆得回当初的分摊基数。
        BigDecimal baseTotal = BigDecimal.ZERO;
        for (ProductionOrderDetail item : outputs) baseTotal = baseTotal.add(zeroIfNull(item.getMaterialCost()));
        BigDecimal pool = totalCost.subtract(baseTotal);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < outputs.size(); i++)
        {
            ProductionOrderDetail item = outputs.get(i);
            BigDecimal base = zeroIfNull(item.getMaterialCost());
            BigDecimal amount;
            if (i == outputs.size() - 1)
            {
                amount = totalCost.subtract(allocated);
            }
            else
            {
                BigDecimal share = baseTotal.signum() > 0
                        ? pool.multiply(base).divide(baseTotal, 2, java.math.RoundingMode.HALF_UP)
                        : pool.multiply(item.getActualYQty()).divide(outputQty, 2, java.math.RoundingMode.HALF_UP);
                amount = base.add(share).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            allocated = allocated.add(amount);
            BigDecimal lineUnitCost = amount.divide(item.getActualYQty(), 6, java.math.RoundingMode.HALF_UP);
            BigDecimal delta = amount.subtract(zeroIfNull(item.getTotalCost()));
            detailMapper.updateExecution(shopCode, item.getDetailCode(), item.getActualYQty(), base, lineUnitCost, amount);
            insertCostAdjustLog(order, item, delta, note, operationTime);
        }
        return productionOrderMapper.updateCost(shopCode, productionCode, totalCost, unitCost,
                processingCost, freightCost, otherCost, updateUser);
    }

    /** 调整说明，落在生产日志的备注里：改了什么、单位成本从多少变成多少。 */
    private String costAdjustNote(ProductionOrder order, BigDecimal processingCost, BigDecimal freightCost,
            BigDecimal otherCost, BigDecimal unitCost)
    {
        StringBuilder note = new StringBuilder("费用调整：");
        appendFeeChange(note, "加工费", order.getProcessingCost(), processingCost);
        appendFeeChange(note, "运费", order.getFreightCost(), freightCost);
        appendFeeChange(note, "其他费用", order.getOtherCost(), otherCost);
        note.append("单位成本 ").append(plain(order.getUnitCost())).append(" → ").append(plain(unitCost));
        note.append("；库存成本未同步");
        return note.length() > 500 ? note.substring(0, 500) : note.toString();
    }

    private void appendFeeChange(StringBuilder note, String name, BigDecimal before, BigDecimal after)
    {
        if (zeroIfNull(before).compareTo(zeroIfNull(after)) == 0) return;
        note.append(name).append(' ').append(plain(before)).append(" → ").append(plain(after)).append('；');
    }

    private String plain(BigDecimal value)
    {
        return zeroIfNull(value).stripTrailingZeros().toPlainString();
    }

    /**
     * 费用调整的生产日志，按产出行各写一条。
     *
     * <p>这条日志没有对应的库存单据——费用调整不开单据、不动库存，所以 doccode 记工单号本身、
     * doctype 记来源类型 SC（三个列都是 NOT NULL）。{@code costamount} 在这里是<b>本行成本的
     * 变化差额</b>（可能为负），不是其它日志行那样的「本次操作金额」，备注里写了口径。</p>
     */
    private void insertCostAdjustLog(ProductionOrder order, ProductionOrderDetail item, BigDecimal delta,
            String note, Date operationTime)
    {
        ProductionLog log = new ProductionLog();
        log.setShopCode(order.getShopCode());
        log.setLogCode(codeGenerator.nextProductionLogCode());
        log.setProductionCode(order.getProductionCode());
        log.setOperation("费用调整");
        log.setDocCode(order.getProductionCode());
        log.setDocType("SC");
        log.setGoodsCode(item.getProductCode());
        log.setQty(zeroIfNull(item.getActualYQty()));
        log.setCostAmount(delta);
        log.setOperationTime(operationTime);
        log.setRemark(note);
        logMapper.insertLog(log, tableRouter.forBusinessDate(operationTime));
    }

    /**
     * 取工单并锁住它，锁持有到事务结束。
     *
     * <p>执行类操作（领料 / 完工 / 退料 / 关闭 / 调成本）都走这里。状态判断是在内存里做的，
     * 不先锁的话两个并发请求会同时读到同一个状态各干一遍——完工尤其致命：会开出两套
     * 消耗单和成品入库单，第二套消耗的是「良品·不在库」这一格里别的工单的在制品。</p>
     */
    private ProductionOrder requireOrder(String shopCode, String productionCode)
    {
        productionOrderMapper.lockProductionOrder(shopCode, productionCode);
        ProductionOrder order = selectProductionOrderByCode(shopCode, productionCode);
        if (order == null) throw new ServiceException("生产工单不存在");
        return order;
    }

    private void requireRequest(ProductionExecutionRequest request)
    {
        if (request == null) throw new ServiceException("生产执行参数不能为空");
    }

    private String required(String value, String message)
    {
        if (value == null || value.trim().isEmpty()) throw new ServiceException(message);
        return value.trim();
    }

    private DocumentMain buildDocument(String shopCode, String type, String typeName, ProductionExecutionRequest request,
            String fromWare, String toWare, List<DocumentDetail> details, String sourceCode, String sourceName, String user)
    {
        DocumentMain document = new DocumentMain(); document.setShopCode(shopCode); document.setDocCode(nextDocumentCode(type));
        document.setDocType(type); document.setDocTypeName(typeName); document.setDocDate(request.getOperationTime() == null ? new Date() : request.getOperationTime());
        document.setFromWareCode(fromWare); document.setToWareCode(toWare); document.setFromState(StockState.available());
        document.setToState(StockState.available()); document.setSourceDocCode(sourceCode); document.setSourceDocType("SC");
        document.setSourceDocTypeName("生产工单"); document.setCreateUser(user); document.setHandler(user); document.setDetails(details); document.setRemark(request.getRemark());
        // 表头数量金额只有销售出库会由 DocumentServiceImpl 自动汇总，其余类型必须自行填写，
        // 否则 u_f_docmain 的 totalqty / totalamount 会一直是 0。出库单的成本要等过账时按
        // 实际批次算出来，这里汇总到的金额为 0，过账后由 InventoryDocumentPostingService 回写。
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (DocumentDetail detail : details)
        {
            if (detail.getQty() != null) totalQty = totalQty.add(detail.getQty());
            if (detail.getCostAmount() != null) totalAmount = totalAmount.add(detail.getCostAmount());
        }
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount);
        return document;
    }

    /**
     * 单号前缀与单据类型保持一致，生产单据不再复用采购入库、销售出库号段。
     *
     * <p>三段式的 SCLL / SCXH / SCTL 必须各走各的号段，否则领料、消耗、退料单都会拿到
     * 销售出库的 CK 号，单号上分不出是哪一段。</p>
     */
    private String nextDocumentCode(String type)
    {
        if (InventoryDocumentTypes.PRODUCTION_PICK.equals(type)) return codeGenerator.nextProductionPickCode();
        if (InventoryDocumentTypes.PRODUCTION_CONSUME.equals(type)) return codeGenerator.nextProductionConsumeCode();
        if (InventoryDocumentTypes.PRODUCTION_RETURN.equals(type)) return codeGenerator.nextProductionReturnCode();
        if (InventoryDocumentTypes.MATERIAL_OUTBOUND.equals(type)) return codeGenerator.nextMaterialOutboundCode();
        if (InventoryDocumentTypes.PRODUCT_INBOUND.equals(type)) return codeGenerator.nextProductInboundCode();
        if (InventoryDocumentTypes.MATERIAL_RETURN.equals(type)) return codeGenerator.nextMaterialReturnCode();
        if (InventoryDocumentTypes.PURCHASE_INBOUND.equals(type)) return codeGenerator.nextInboundCode();
        return codeGenerator.nextOutboundCode();
    }

    private BigDecimal zeroIfNull(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 完工录入的产出行（实际数量、批次号、序列号），按产出明细代码索引。 */
    private Map<String, ProductionOrderDetail> indexOutputs(ProductionExecutionRequest request)
    {
        Map<String, ProductionOrderDetail> values = new HashMap<>();
        if (request.getOutputs() == null) return values;
        for (ProductionOrderDetail input : request.getOutputs())
        {
            if (input == null || input.getDetailCode() == null) continue;
            values.put(input.getDetailCode(), input);
        }
        return values;
    }

    /**
     * 按成品档案的批次策略给成品入库明细补上批次号和序列号。
     *
     * <p>口径与采购入库的 {@code validateInboundTracking} 一致，缺了这一步的后果是：
     * 批次管理的成品入库后没有批次记录（{@code InventoryDocumentPostingService.executeInbound}
     * 只在明细带了批次号时才建批次），卖的时候 FIFO 会报「批次库存不足」；序列号管理的成品
     * 更是连一个序列号都没有，根本发不出去。</p>
     *
     * <p>成品的批次代表「这一次生产出来的这一批」，所以留空时由服务端生成，不要求用户填；
     * 序列号只能人工给——号是贴在实物上的。</p>
     */
    private void applyOutputTracking(String shopCode, DocumentDetail detail, ProductionOrderDetail input,
            boolean custom)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, detail.getGoodsCode());
        if (goods == null) throw new ServiceException("产出商品'" + detail.getGoodsCode() + "'不存在");
        // 规格和单位取商品档案：批次和序列号记录都要落这两个字段，产出明细上没有
        detail.setGoodsName(isBlank(detail.getGoodsName()) ? goods.getGoodsName() : detail.getGoodsName());
        detail.setSpec(goods.getSpec());
        detail.setUnit(goods.getUnit());

        String strategy = isBlank(goods.getBatchStrategy()) ? "none" : goods.getBatchStrategy().trim().toLowerCase();
        String batchCode = input == null || input.getBatchCode() == null ? "" : input.getBatchCode().trim();
        List<String> serialNumbers = SerialNumberRemarkCodec.normalize(
                input == null ? null : input.getSerialNumbers());

        if ("none".equals(strategy))
        {
            if (!serialNumbers.isEmpty())
            {
                throw new ServiceException("成品'" + goods.getGoodsName() + "'不做批次追踪，无需填写序列号");
            }
            // 定制件例外：专属关系挂在批次上，没有批次行就没有落脚点，别的订单照样能把它发走。
            // 所以哪怕商品档案说不追踪，也补一个批次号让它进批次表——定制件本来也该说得出
            // 「客户拿到的是哪一批」。代价是这一批的出库成本按批次走（见
            // InventoryDocumentPostingService 里强制走批次扣减那段），与该商品的常规口径不同。
            if (custom)
            {
                detail.setBatchCode(batchCode.isEmpty() ? batchService.generateBatchCode(shopCode) : batchCode);
                detail.setSerialNumbers(new ArrayList<>());
                return;
            }
            if (!batchCode.isEmpty())
            {
                throw new ServiceException("成品'" + goods.getGoodsName() + "'不做批次追踪，无需填写批次号");
            }
            detail.setBatchCode(null);
            detail.setSerialNumbers(new ArrayList<>());
            return;
        }
        if ("batch".equals(strategy))
        {
            if (!serialNumbers.isEmpty()) throw new ServiceException("批次管理的成品'" + goods.getGoodsName() + "'无需填写序列号");
            detail.setBatchCode(batchCode.isEmpty() ? batchService.generateBatchCode(shopCode) : batchCode);
            detail.setSerialNumbers(new ArrayList<>());
            return;
        }
        if ("serial".equals(strategy))
        {
            int expected;
            try
            {
                expected = detail.getQty().intValueExact();
            }
            catch (ArithmeticException error)
            {
                throw new ServiceException("序列号管理的成品'" + goods.getGoodsName() + "'的实际产出必须是整数");
            }
            if (serialNumbers.size() != expected)
            {
                throw new ServiceException("成品'" + goods.getGoodsName() + "'实际产出 " + expected
                        + "，必须填写 " + expected + " 个不重复的序列号，当前 " + serialNumbers.size() + " 个");
            }
            detail.setBatchCode(batchCode.isEmpty() ? batchService.generateBatchCode(shopCode) : batchCode);
            detail.setSerialNumbers(serialNumbers);
            return;
        }
        throw new ServiceException("成品'" + goods.getGoodsName() + "'的批次策略无效：" + strategy);
    }

    /** 费用以录入值为准，未录入时沿用工单上已有的值；负数直接拒绝。 */
    private BigDecimal feeOrExisting(BigDecimal input, BigDecimal existing, String name)
    {
        BigDecimal value = input != null ? input : zeroIfNull(existing);
        if (value.signum() < 0) throw new ServiceException(name + "不能为负");
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 生产消耗出库（说明书的 consume_out），返回工单的物料成本。
     *
     * <p>开一张 SCXH 出库单，把「生产领用中」状态的物料永久出库。消耗量以完工录入值为准，
     * 某行未录入时按「已领 - 已退」全额消耗。</p>
     *
     * <p>物料成本直接取这张出库单过账后的表头金额——库存模块会按实际批次算出成本并回写，
     * 这正是说明书 4.4 流程2 要求的 {@code materialcost = Σ(消耗数量 × 出库成本价)}。
     * 改造前需要「从领料日志反推单价」的那套近似逻辑（跨月还会退化成平均单价）不再需要。</p>
     *
     * <p>配方按产出商品分开，所以成本也按产出归集：每条单据明细都记得自己出自哪一行物料、
     * 那行物料又属于哪个产出，过账后按 detailCode 汇总回去，供完工时逐个产出算单位成本。</p>
     */
    private MaterialConsumption consumeMaterials(ProductionOrder order, ProductionExecutionRequest request, String updateUser)
    {
        Map<String, List<ProductionMaterial>> inputs = indexMaterialInputs(request);
        String ware = required(request.getMaterialWareCode(), "物料消耗仓库不能为空");
        List<DocumentDetail> details = new ArrayList<>();
        // 与 details 一一对应：第 i 条单据明细耗的是哪个产出的料
        List<String> owners = new ArrayList<>();
        for (ProductionMaterial material : order.getMaterials())
        {
            // 可消耗 = 已领 - 已退 - 已耗。正常单次完工时已耗为 0，减它是为了万一
            // 有第二次完工走到这里，不至于把同一批料再消耗一遍（那会扣到别的工单的在制品）
            BigDecimal consumable = zeroIfNull(material.getPickedQty())
                    .subtract(zeroIfNull(material.getReturnedQty()))
                    .subtract(zeroIfNull(material.getActualQty()));
            BigDecimal consumed = BigDecimal.ZERO;
            List<DocumentDetail> lines = new ArrayList<>();
            for (ProductionMaterial line : linesOf(inputs, material, consumable, ProductionMaterial::setActualQty))
            {
                BigDecimal qty = line.getActualQty();
                if (qty == null || qty.signum() < 0) throw new ServiceException("物料'" + material.getGoodsName() + "'的实际消耗量不能为负");
                if (qty.signum() == 0) continue;
                consumed = consumed.add(qty);
                lines.add(buildDetail(material, qty, ware, line));
            }
            if (consumed.compareTo(consumable) > 0)
            {
                throw new ServiceException("物料'" + material.getGoodsName() + "'的实际消耗量不能超过可消耗量 " + consumable);
            }
            material.setActualQty(consumed);
            materialMapper.updateActualQty(order.getShopCode(), material.getMaterialCode(), consumed);
            details.addAll(lines);
            for (int i = 0; i < lines.size(); i++) owners.add(material.getDetailCode());
        }
        // 一点物料都不消耗也是合法的（比如全部退回），此时物料成本为 0
        if (details.isEmpty()) return new MaterialConsumption(BigDecimal.ZERO, new LinkedHashMap<>());

        // 从车间的在制品里出库，来源必须是「良品·不在库」，否则会去扣可用库存——
        // 那样领出去的料永远消耗不掉，仓库里的好货反倒被扣光
        DocumentMain document = buildDocument(order.getShopCode(), InventoryDocumentTypes.PRODUCTION_CONSUME,
                "生产消耗", request, ware, null, details, order.getProductionCode(), order.getProductName(), updateUser);
        StockState inProduction = StockState.of(QualityFlag.GOOD, OnhandFlag.OUT);
        document.setFromState(inProduction);
        document.setToState(inProduction);
        insertAndConfirm(document, updateUser);

        // 回读的明细与建单时同序（postedDetails 已保证行数一致），据此把成本摊回各产出
        List<DocumentDetail> posted = postedDetails(document, details);
        Map<String, BigDecimal> byDetail = new LinkedHashMap<>();
        for (int i = 0; i < posted.size(); i++)
        {
            DocumentDetail line = posted.get(i);
            insertLog(order, "消耗", document, line, request);
            byDetail.merge(owners.get(i), zeroIfNull(line.getCostAmount()), BigDecimal::add);
        }
        // 过账后表头金额已被回写成按实际批次算出的成本，直接作为工单物料成本
        return new MaterialConsumption(zeroIfNull(document.getTotalAmount()), byDetail);
    }

    /**
     * 一次生产消耗的结果：工单口径的物料总成本，以及按产出明细归集的物料成本。
     *
     * <p>{@code total} 取过账后的单据表头金额，{@code byDetail} 是逐行累加出来的，两者
     * 理论上相等；万一库存模块的表头汇总有分币差，以 {@code total} 为准，差额在完工分摊时
     * 由末行吸收。</p>
     */
    private static final class MaterialConsumption
    {
        private final BigDecimal total;
        private final Map<String, BigDecimal> byDetail;

        private MaterialConsumption(BigDecimal total, Map<String, BigDecimal> byDetail)
        {
            this.total = total;
            this.byDetail = byDetail;
        }

        private BigDecimal total() { return total; }

        private BigDecimal of(String detailCode)
        {
            BigDecimal value = byDetail.get(detailCode);
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    /**
     * 取过账后回读的单据明细；行数对不上时退回建单时的明细，保证日志行数与业务行数一致。
     * 回读的明细带服务端生成的明细号和实际成本，建单时构造的那份两者都没有。
     */
    private List<DocumentDetail> postedDetails(DocumentMain document, List<DocumentDetail> fallback)
    {
        List<DocumentDetail> saved = document.getDetails();
        return saved != null && saved.size() == fallback.size() ? saved : fallback;
    }

    private void insertAndConfirm(DocumentMain document, String user)
    {
        documentService.insertDocument(document);
        documentService.confirmDocument(document.getShopCode(), document.getDocCode(), document.getTableSuffix(), true, user);
        DocumentMain saved = documentService.selectDocumentByCode(document.getShopCode(), document.getDocCode(), document.getTableSuffix());
        if (saved != null)
        {
            document.setTotalAmount(saved.getTotalAmount());
            document.setDetails(saved.getDetails());
        }
    }

    /**
     * 写一条执行日志。
     *
     * <p>{@code costamount} 记的是<b>本次操作发生的成本</b>：只有消耗（SCXH）和成品入库（SCCPRK）
     * 有成本，领料（SCLL）和退料（SCTL）是同仓库内的状态流转，单据上那个金额是成本从「在库」
     * 搬到「不在库」的搬运额，不是发生额——照抄下来，界面上就成了「领料产生了成本」，
     * 和这两步不产生成本的口径自相矛盾，所以一律记 0。</p>
     */
    private void insertLog(ProductionOrder order, String operation, DocumentMain document, DocumentDetail detail, ProductionExecutionRequest request)
    {
        ProductionLog log = new ProductionLog(); log.setShopCode(order.getShopCode()); log.setLogCode(codeGenerator.nextProductionLogCode());
        log.setProductionCode(order.getProductionCode()); log.setOperation(operation); log.setDocCode(document.getDocCode()); log.setDocType(document.getDocType());
        log.setDocDetailCode(detail.getDetailCode()); log.setGoodsCode(detail.getGoodsCode()); log.setQty(detail.getQty());
        log.setCostAmount(isStateTransfer(document.getDocType()) || detail.getCostAmount() == null
                ? BigDecimal.ZERO : detail.getCostAmount());
        log.setOperationTime(document.getDocDate()); log.setRemark(request.getRemark());
        logMapper.insertLog(log, tableRouter.forBusinessDate(log.getOperationTime()));
    }

    /** 领料和退料是状态流转，不产生成本 */
    private boolean isStateTransfer(String docType)
    {
        return InventoryDocumentTypes.PRODUCTION_PICK.equals(docType)
                || InventoryDocumentTypes.PRODUCTION_RETURN.equals(docType);
    }

    /**
     * 查工单的执行日志。
     *
     * <p>不指定月份时跨所有已建的日志月表查——工单跨月是常态（上月领料、这月完工），
     * 只查当月的话详情页会缺掉前面几段。一张工单的日志就几条，全查回来再排序即可，
     * 不值得为它做分页。指定了月份就只查那一张，留给排查用。</p>
     */
    @Override
    public List<ProductionLog> selectProductionLogList(String shopCode, String productionCode,
            String operation, String tableSuffix)
    {
        if (tableSuffix != null && !tableSuffix.trim().isEmpty())
        {
            return logMapper.selectLogList(shopCode, productionCode, operation, tableRouter.validate(tableSuffix));
        }
        List<ProductionLog> logs = new ArrayList<>();
        for (String suffix : logMapper.selectLogTableSuffixes())
        {
            if (suffix == null || !suffix.matches("^\\d{6}$")) continue;
            logs.addAll(logMapper.selectLogList(shopCode, productionCode, operation, suffix));
        }
        // 各月表内部已按时间倒序，合并后要整体重排一次
        logs.sort((left, right) -> {
            Date leftTime = left.getOperationTime();
            Date rightTime = right.getOperationTime();
            if (leftTime == null && rightTime == null) return 0;
            if (leftTime == null) return 1;
            if (rightTime == null) return -1;
            return rightTime.compareTo(leftTime);
        });
        return logs;
    }

    private void validateOrder(ProductionOrder order)
    {
        if (order == null) throw new ServiceException("生产工单不能为空");
        if (order.getShopCode() == null || order.getShopCode().trim().isEmpty()) throw new ServiceException("商家编码不能为空");
        if (order.getSourceType() == null || !(SOURCE_ORDER.equals(order.getSourceType()) || SOURCE_STOCK.equals(order.getSourceType()))) throw new ServiceException("工单来源必须是order或stock");
        if (order.getProductCode() == null || order.getProductCode().trim().isEmpty()) throw new ServiceException("产出商品编码不能为空");
        if (order.getProductName() == null || order.getProductName().trim().isEmpty()) throw new ServiceException("产出商品名称不能为空");
        if (order.getPlanDate() == null) order.setPlanDate(new Date());
        if (order.getDetails() == null) order.setDetails(Collections.emptyList());
        if (order.getMaterials() == null) order.setMaterials(Collections.emptyList());
        order.setPlanYQty(sumDetailPlanQty(order.getDetails()));
    }

    /**
     * 计划产出数量以产出明细为唯一数据源，主表字段只是冗余展示，一律由明细合计覆盖。
     * 完工入库数量和单位成本都按明细逐行计算，若两处允许不一致，会出现"按主表数量领料、
     * 按明细数量入库"的错账，因此这里不接受调用方传入的主表数量。
     */
    private BigDecimal sumDetailPlanQty(List<ProductionOrderDetail> details)
    {
        if (details.isEmpty()) throw new ServiceException("产出明细不能为空");
        BigDecimal total = BigDecimal.ZERO;
        for (ProductionOrderDetail item : details)
        {
            if (item == null || item.getPlanYQty() == null || item.getPlanYQty().signum() <= 0)
            {
                throw new ServiceException("产出明细计划数量必须大于0");
            }
            total = total.add(item.getPlanYQty());
        }
        return total;
    }

    /**
     * 生成子表的代码、行号和计划用量。
     *
     * <p>物料清单挂在产出明细行下（{@code details[].materials}），一个产出商品一套配方：
     * <b>计划用量 = 单位用量 × 该产出行的计划数量</b>，分母是本行产出，不是整单产出合计。
     * 同一个物料被两个产出用到时会生成两行，各带自己的 detailCode，之后的领料、消耗、
     * 退料和成本归集都按行独立走。</p>
     *
     * <p>展开后的平表写回 {@code order.materials}，供落库和执行环节使用。</p>
     */
    private void prepareChildren(ProductionOrder order)
    {
        List<ProductionMaterial> flat = new ArrayList<>();
        int line = 1;
        int materialLine = 1;
        // 一张工单里同一个商品只查一次档案
        Map<String, InventoryGoods> archive = new HashMap<>();
        for (ProductionOrderDetail item : order.getDetails())
        {
            if (item == null || item.getProductCode() == null || item.getProductCode().trim().isEmpty()) throw new ServiceException("产出明细商品编码不能为空");
            if (item.getPlanYQty() == null || item.getPlanYQty().signum() <= 0) throw new ServiceException("产出明细计划数量必须大于0");
            requireGoods(order.getShopCode(), archive, item.getProductCode(), "产出商品");
            item.setShopCode(order.getShopCode()); item.setProductionCode(order.getProductionCode());
            item.setDetailCode(codeGenerator.nextProductionDetailCode()); item.setLineNo(line++);
            if (item.getMaterials() == null) item.setMaterials(new ArrayList<>());
            for (ProductionMaterial material : item.getMaterials())
            {
                if (material == null || material.getGoodsCode() == null || material.getGoodsCode().trim().isEmpty()) throw new ServiceException("物料编码不能为空");
                if (material.getQtyPerUnit() == null || material.getQtyPerUnit().signum() <= 0) throw new ServiceException("物料单位用量必须大于0");
                requireGoods(order.getShopCode(), archive, material.getGoodsCode(), "物料");
                // 自己耗自己算不出成本：完工时同一张单既消耗又入库同一个商品，
                // 消耗按当前库存均价扣、入库按算出来的单位成本加，成本会越滚越偏
                if (material.getGoodsCode().trim().equals(item.getProductCode().trim()))
                {
                    throw new ServiceException("产出「" + item.getProductName() + "」的物料里不能有它自己，"
                            + "请把这一行删掉");
                }
                material.setShopCode(order.getShopCode()); material.setProductionCode(order.getProductionCode());
                material.setDetailCode(item.getDetailCode());
                material.setMaterialCode(codeGenerator.nextProductionMaterialCode()); material.setLineNo(materialLine++);
                material.setTotalQty(material.getQtyPerUnit().multiply(item.getPlanYQty()));
                flat.add(material);
            }
        }
        // 老结构把物料平铺在工单下，那样的请求没有归属信息，计划用量算不出来，直接拒绝比默默丢掉好
        if (flat.isEmpty() && !order.getMaterials().isEmpty())
        {
            throw new ServiceException("物料清单要挂在产出明细下：请把每个产出商品用到的物料放进对应产出行的 materials");
        }
        order.setMaterials(flat);
    }

    /**
     * 商品必须在商品档案里，建单时就拦。
     *
     * <p>不拦的话工单存得下、界面上也看不出问题，一直要到领料过账（物料）或完工入库（产出）
     * 才报「商品不存在」，那时候工单已经排进计划了。{@code archive} 是本次调用内的缓存，
     * 同一个商品被几个产出用到时只查一次。</p>
     */
    private InventoryGoods requireGoods(String shopCode, Map<String, InventoryGoods> archive,
            String goodsCode, String role)
    {
        String code = goodsCode.trim();
        InventoryGoods goods = archive.get(code);
        if (goods != null) return goods;
        goods = goodsMapper.selectGoodsByCode(shopCode, code);
        if (goods == null) throw new ServiceException(role + "'" + code + "'不在商品档案里，请先建档或重新选择");
        archive.put(code, goods);
        return goods;
    }

    /**
     * 完工后把产出认领给来源订单（说明书 6.3「工单状态通知订单模块，触发发货」）。
     *
     * <p>刚入库的成品按建单时的分配预占给销售订单明细。缺口补齐后订单从「备货中」回到
     * 「已确认」，业务员这才能去建发货批次——这就是「通知订单」在本系统里的落地形态：
     * 不发消息，直接把货占上、把状态推回去。备货生产的工单没有分配，这一步什么都不做。</p>
     *
     * <p><b>按实际产出发，不按计划发。</b> 每个产出行能认领的上限是它的<b>实际</b>产出，
     * 计划做 10 实际做 6 就只能认领 6。</p>
     *
     * <p><b>一行产出分给多张订单时按分配顺序先到先得</b>，不按比例摊。少做了就先满足排在
     * 前面的那张，后面的继续挂着缺口——按比例摊会把每张订单都变成半截，结果是谁都发不了货。
     * 分配顺序就是建单时在界面上勾选的顺序。</p>
     *
     * <p>只认领本次入库的量，仓库里原有的同款成品不动：那些不是这张工单做出来的。</p>
     */
    private void fulfillSourceOrders(ProductionOrder order, List<ProductionOrderDetail> postedOutputs,
            DocumentMain document, String wareCode, ProductionExecutionRequest request, String updateUser)
    {
        if (!SOURCE_ORDER.equals(order.getSourceType())) return;
        for (ProductionOrderDetail item : postedOutputs)
        {
            List<ProductionOrderAllocation> allocations = item.getAllocations();
            if (allocations == null || allocations.isEmpty()) continue;
            BigDecimal remaining = zeroIfNull(item.getActualYQty());
            for (ProductionOrderAllocation allocation : allocations)
            {
                if (remaining.signum() <= 0) break;
                BigDecimal want = zeroIfNull(allocation.getQty()).min(remaining);
                if (want.signum() <= 0) continue;
                BigDecimal got = salesOrderService.fulfillFromProduction(order.getShopCode(),
                        allocation.getOrderCode(), allocation.getTableSuffix(), allocation.getDetailCode(),
                        want, updateUser);
                if (got.signum() <= 0) continue;
                bindCustomBatch(order, item, allocation, wareCode);
                remaining = remaining.subtract(got);
                insertFulfillLog(order, allocation, document, item.getProductCode(), got, request);
            }
        }
    }

    /** 这个产出行有没有分配给定制的订单明细。有一条是定制的，这一批产出就得进批次表。 */
    private boolean hasCustomAllocation(ProductionOrderDetail item)
    {
        if (item.getAllocations() == null) return false;
        for (ProductionOrderAllocation allocation : item.getAllocations())
        {
            if (allocation != null && Boolean.TRUE.equals(allocation.getCustomMade())) return true;
        }
        return false;
    }

    /**
     * 定制件：把刚入库的这批成品绑给来源订单明细，别的订单出库时就挑不到它了。
     *
     * <p>只对<b>定制</b>的分配行做。普通标准件是通用库存，谁先发货谁拿到，绑上反而会让
     * 仓库里躺着一批别人用不了的货。</p>
     *
     * <p>没有批次号的定制件在 {@link #applyTrackingData} 里已经被强制补了一个——专属关系
     * 挂在批次上，没有批次就没有落脚点。</p>
     *
     * <p>批次已经被别的订单绑走时 {@code bindOwner} 会抛异常，让整笔完工回滚：那说明两张
     * 工单的产出撞进了同一个批次号，静默跳过会让这张单的货被前一张发走，客户拿不到自己的定制品。</p>
     */
    private void bindCustomBatch(ProductionOrder order, ProductionOrderDetail item,
            ProductionOrderAllocation allocation, String wareCode)
    {
        if (!Boolean.TRUE.equals(allocation.getCustomMade())) return;
        if (isBlank(item.getBatchCode()))
        {
            throw new ServiceException("定制产出「" + item.getProductName() + "」没有批次号，无法绑定给订单 "
                    + allocation.getOrderCode() + "：定制件的专属关系挂在批次上，请检查成品的批次策略");
        }
        batchService.bindOwner(order.getShopCode(), item.getBatchCode(), item.getProductCode(),
                wareCode, StockState.available(), allocation.getOrderCode(), allocation.getDetailCode());
    }

    /**
     * 订单认领的生产日志。
     *
     * <p>挂在成品入库单（SCCPRK）下：认领的就是这张单入库的货，日志里点开能顺着单号查到批次。
     * {@code costamount} 记 0——认领不产生成本，成品的成本在入库时就结算完了。</p>
     */
    private void insertFulfillLog(ProductionOrder order, ProductionOrderAllocation allocation,
            DocumentMain document, String goodsCode, BigDecimal qty, ProductionExecutionRequest request)
    {
        ProductionLog log = new ProductionLog();
        log.setShopCode(order.getShopCode());
        log.setLogCode(codeGenerator.nextProductionLogCode());
        log.setProductionCode(order.getProductionCode());
        log.setOperation("订单认领");
        log.setDocCode(document.getDocCode());
        log.setDocType(document.getDocType());
        log.setGoodsCode(goodsCode);
        log.setQty(qty);
        log.setCostAmount(BigDecimal.ZERO);
        log.setOperationTime(document.getDocDate());
        String note = "认领销售订单 " + allocation.getOrderCode() + " 明细 " + allocation.getDetailCode()
                + "，数量 " + plain(qty)
                + (Boolean.TRUE.equals(allocation.getCustomMade()) ? "（定制，批次已绑定）" : "");
        log.setRemark(note.length() > 500 ? note.substring(0, 500) : note);
        logMapper.insertLog(log, tableRouter.forBusinessDate(log.getOperationTime()));
    }

    /**
     * 校验产出分配并落成主表的 {@code orderallocation} / {@code sourceordercodes}。
     *
     * <p>说明书 4.4 的工单来源 order：这张工单的产出是为哪几张销售订单生产的。备货生产
     * （stock）不许带分配——挂了订单就不是备货了，与其默默丢掉不如让调用方改对。</p>
     *
     * <p>校验四件事，缺一不可：</p>
     * <ol>
     *   <li>产出商品要对得上——分配的订单明细商品必须就是这一行产出的商品，
     *       否则完工入库的货压根不是订单要的东西；</li>
     *   <li>不许超订单——单条分配量不能超过该明细现算的缺口（已锁行，并发下也准）；</li>
     *   <li>不许超产出——一个产出行分出去的总量不能超过它的计划产出，
     *       多出来的部分没有货可发；</li>
     *   <li>一条订单明细同时只能挂一张在产工单——{@code sourcecode} 是单值列，
     *       两张工单抢同一条明细时后一张的单号会把前一张覆盖掉，追溯就断了。
     *       真要拆两张工单做，先把前一张关闭。</li>
     * </ol>
     *
     * <p>本方法必须在 {@link #prepareChildren} 之后调用：分配按产出行号归属，行号在那里定。</p>
     */
    private void applyAllocations(ProductionOrder order)
    {
        List<ProductionOrderAllocation> flat = new ArrayList<>();
        boolean orderDriven = SOURCE_ORDER.equals(order.getSourceType());
        // 同一条订单明细在一张工单里只能出现一次：分两行写的话，各自校验都能过、合起来超缺口
        Map<String, String> claimedDetails = new HashMap<>();
        for (ProductionOrderDetail output : order.getDetails())
        {
            List<ProductionOrderAllocation> allocations = output.getAllocations();
            if (allocations == null || allocations.isEmpty()) continue;
            if (!orderDriven)
            {
                throw new ServiceException("备货生产的工单不能挂销售订单，产出「" + output.getProductName()
                        + "」上有订单分配；要按订单做请把来源改成订单生产");
            }
            BigDecimal allocated = BigDecimal.ZERO;
            for (ProductionOrderAllocation allocation : allocations)
            {
                validateAllocation(order, output, allocation, claimedDetails);
                allocated = allocated.add(allocation.getQty());
                allocation.setOutputLineNo(output.getLineNo());
                flat.add(allocation);
            }
            if (allocated.compareTo(output.getPlanYQty()) > 0)
            {
                throw new ServiceException("产出「" + output.getProductName() + "」分配给订单的总量 "
                        + plain(allocated) + " 超过计划产出 " + plain(output.getPlanYQty()));
            }
        }
        if (orderDriven && flat.isEmpty())
        {
            throw new ServiceException("订单生产的工单必须选来源订单：请在产出行上分配销售订单明细，"
                    + "不挂订单的请把来源改成备货生产");
        }
        order.setOrderAllocation(flat.isEmpty() ? null : JSON.toJSONString(flat));
        order.setSourceOrderCodes(joinOrderCodes(flat));
    }

    /** 单条分配的校验。订单明细在这里锁行，锁一直持有到本事务结束。 */
    private void validateAllocation(ProductionOrder order, ProductionOrderDetail output,
            ProductionOrderAllocation allocation, Map<String, String> claimedDetails)
    {
        if (allocation == null) throw new ServiceException("订单分配行不能为空");
        if (isBlank(allocation.getOrderCode())) throw new ServiceException("订单分配的销售订单号不能为空");
        if (isBlank(allocation.getDetailCode())) throw new ServiceException("订单分配的订单明细代码不能为空");
        if (!MonthTableSuffix.isValid(allocation.getTableSuffix()))
        {
            throw new ServiceException("订单分配缺少订单所在月份（tableSuffix），销售订单按月分表，没有它定位不到明细");
        }
        if (allocation.getQty() == null || allocation.getQty().signum() <= 0)
        {
            throw new ServiceException("订单「" + allocation.getOrderCode() + "」的分配数量必须大于0");
        }
        String claimedBy = claimedDetails.put(allocation.getDetailCode(), output.getProductName());
        if (claimedBy != null)
        {
            throw new ServiceException("订单明细「" + allocation.getDetailCode() + "」在本工单里被分配了两次（"
                    + claimedBy + "、" + output.getProductName() + "），请合并成一行");
        }

        SalesOrderItem item = salesOrderService.lockOrderItem(
                order.getShopCode(), allocation.getTableSuffix(), allocation.getDetailCode());
        if (item == null)
        {
            throw new ServiceException("销售订单明细不存在：" + allocation.getOrderCode()
                    + " / " + allocation.getDetailCode());
        }
        // 用产出行的商品编码起头比：它在 prepareChildren 里已经校验过非空
        if (!output.getProductCode().equals(item.getGoodsCode()))
        {
            throw new ServiceException("产出「" + output.getProductName() + "」不能分给订单明细「"
                    + item.getGoodsName() + "」：订单要的是 " + item.getGoodsCode()
                    + "，这一行产出的是 " + output.getProductCode());
        }
        // 上限是<b>未发量</b>：只要还没发出去就允许排产。订单不再预占库存，
        // 「库存里有多少现货」不影响能不能为这条明细排产——普通件排出来的货进通用库存，
        // 定制件排出来的货绑给这条明细。
        BigDecimal pending = zeroIfNull(item.getPendingQty());
        if (allocation.getQty().compareTo(pending) > 0)
        {
            throw new ServiceException("订单「" + allocation.getOrderCode() + "」的「" + item.getGoodsName()
                    + "」还没发的只有 " + plain(pending) + "，分配了 " + plain(allocation.getQty()));
        }
        requireDetailFree(order, item, allocation);

        // 冗余字段一律以订单为准，前端传什么都覆盖掉——这两个值只用于工单上的展示，
        // 让它跟着请求走的话，改个名字就能让工单显示成另一张订单
        allocation.setGoodsCode(item.getGoodsCode());
        allocation.setGoodsName(item.getGoodsName());
        // 建单时刻的快照，只作回显：真正的校验一律现查订单明细
        allocation.setPendingQty(pending);
        // 定制标记同样以订单为准：前端传的只是它上次看到的值
        allocation.setCustomMade(Boolean.TRUE.equals(item.getCustomMade()));
    }

    /**
     * 这条订单明细有没有被别的工单占着。
     *
     * <p>占用状态就存在订单明细的 {@code sourcecode} 上，不需要反扫工单的分配 JSON。
     * 已关闭的工单不算占用：它已经结案，剩下的缺口该让新工单接手。</p>
     */
    private void requireDetailFree(ProductionOrder order, SalesOrderItem item, ProductionOrderAllocation allocation)
    {
        String holder = item.getSourceCode();
        if (isBlank(holder)) return;
        if (holder.equals(order.getProductionCode())) return;
        ProductionOrder occupying = productionOrderMapper.selectProductionOrderByCode(order.getShopCode(), holder);
        if (occupying == null || STATUS_CLOSED.equals(occupying.getStatus())) return;
        throw new ServiceException("订单「" + allocation.getOrderCode() + "」的「" + item.getGoodsName()
                + "」已挂在工单 " + holder + "（" + occupying.getStatus() + "）上，"
                + "一条订单明细同时只能由一张工单负责；要改由本工单做，请先关闭那张工单");
    }

    /**
     * 把工单号回写到被分配的订单明细上，供工单完工时反查该发哪张订单。
     *
     * <p>{@code productionCode} 传 null 表示松开占用：明细的备货来源仍是「生产」——业务意图没变，
     * 只是暂时没有工单在做，界面上仍会提示它要走生产。</p>
     */
    private void writeBackOrderSource(ProductionOrder order, String productionCode)
    {
        for (ProductionOrderAllocation allocation : parseAllocations(order.getOrderAllocation()))
        {
            salesOrderService.updateItemProductionOrder(order.getShopCode(), allocation.getOrderCode(),
                    allocation.getTableSuffix(), allocation.getDetailCode(), productionCode);
        }
    }

    /** 松开这张工单占住的订单明细。改单、删单时调。 */
    private void releaseOrderSource(ProductionOrder existing)
    {
        writeBackOrderSource(existing, null);
    }

    /**
     * 主表的 sourceordercodes：去重后的订单号，逗号分隔。
     *
     * <p>列宽 varchar(500)，放得下约 38 个订单号，一张工单挂这么多订单已经该拆单了。
     * 真超了就少放几个，<b>按整个订单号丢</b>不做字符截断——半截单号比没有还糟，
     * 看着像个单号却查不到。完整的分配始终在 {@code orderallocation}（TEXT）里，
     * 这一列只是给列表页扫一眼用的。</p>
     */
    private String joinOrderCodes(List<ProductionOrderAllocation> allocations)
    {
        if (allocations.isEmpty()) return null;
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ProductionOrderAllocation allocation : allocations) codes.add(allocation.getOrderCode());
        StringBuilder joined = new StringBuilder();
        for (String code : codes)
        {
            int added = joined.length() == 0 ? code.length() : code.length() + 1;
            if (joined.length() + added > 500) break;
            if (joined.length() > 0) joined.append(',');
            joined.append(code);
        }
        return joined.length() == 0 ? null : joined.toString();
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

}
