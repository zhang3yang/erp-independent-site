package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryPartner;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.PurchaseReceiveItem;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.GoodsPartnerMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryPartnerMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.mapper.PurchaseReceiveItemMapper;
import com.ruoyi.system.mapper.PurchaseReceiveMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IPurchaseInboundService;
import com.ruoyi.system.service.IPurchaseReceiveService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.SerialNumberRemarkCodec;

/**
 * 采购入库Service业务层处理
 */
@Service
public class PurchaseInboundServiceImpl implements IPurchaseInboundService
{
    private static final Logger log = LoggerFactory.getLogger(PurchaseInboundServiceImpl.class);

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private IDocumentService documentService;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private InventoryPartnerMapper partnerMapper;

    /** 商品供货关系：审核通过时累积，给供应商选择做推荐 */
    @Autowired
    private GoodsPartnerMapper goodsPartnerMapper;

    @Autowired
    private DocumentDetailMapper documentDetailMapper;

    @Autowired
    private DocumentMainMapper documentMainMapper;

    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private PurchaseReceiveMapper receiveMapper;

    @Autowired
    private PurchaseReceiveItemMapper receiveItemMapper;

    /** 采购发货侧：跨月表定位发货单、重算单头派生状态。 */
    @Autowired
    private IPurchaseReceiveService receiveService;

    /** 采购订单直接生成入库单的旧通道开关；默认停用，入库统一走采购到货与质检。 */
    private static final String LEGACY_INBOUND_SWITCH = "purchase.inbound.legacy.enabled";

    /**
     * 直接创建采购入库单。该入口与采购订单入库使用相同的商品、批次和序列号规则。
     */
    @Override
    @Transactional
    public DocumentMain createDirectInbound(String shopCode, DocumentMain inbound, String createUser)
    {
        if (inbound == null)
        {
            throw new ServiceException("入库单不能为空");
        }
        if (inbound.getDetails() == null || inbound.getDetails().isEmpty())
        {
            throw new ServiceException("入库明细不能为空");
        }
        if (isBlank(inbound.getToWareCode()))
        {
            throw new ServiceException("入库仓库不能为空");
        }
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, inbound.getToWareCode());
        if (warehouse == null)
        {
            throw new ServiceException("仓库'" + inbound.getToWareCode() + "'不存在");
        }
        // 供应商必填：入库单要靠它记账、结款，也是「这个商品从谁那儿进的」唯一来源。
        // 原先这里是可选的（只有前端在拦），漏传就建出一张没有往来单位的入库单。
        if (isBlank(inbound.getPartnerCode()))
        {
            throw new ServiceException("供应商不能为空");
        }
        InventoryPartner partner = partnerMapper.selectPartnerByCode(shopCode, inbound.getPartnerCode());
        if (partner == null)
        {
            throw new ServiceException("供应商'" + inbound.getPartnerCode() + "'不存在");
        }
        // 往来单位表里客户、部门和供应商混在一起，传错类型会建出一张「向客户采购」的单。
        if (!"supplier".equals(partner.getPartnerType()))
        {
            throw new ServiceException("往来单位'" + partner.getPartnerName() + "'不是供应商，不能作为入库来源");
        }
        // 停用状态刻意不校验：采购发货入库借用本方法建单，供应商在下单之后被停用的话，
        // 已经在路上的货还是要收进来。界面上的供应商选择弹窗只列启用的，新建单进不来。
        inbound.setPartnerCode(partner.getPartnerCode());
        inbound.setPartnerName(partner.getPartnerName());

        inbound.setShopCode(shopCode);
        inbound.setDocType(InventoryDocumentTypes.PURCHASE_INBOUND);
        inbound.setDocTypeName("采购入库");
        inbound.setDocCode(generateInboundDocCode());
        if (isBlank(inbound.getSourceDocCode()))
        {
            inbound.setSourceDocCode("DIRECT-" + inbound.getDocCode());
        }
        inbound.setDocDate(inbound.getDocDate() == null ? new java.util.Date() : inbound.getDocDate());
        inbound.setCreateUser(createUser);
        inbound.setHandler(createUser);
        inbound.setHandlerTime(new java.util.Date());
        inbound.setFromWareCode("");
        inbound.setFromWareName("");
        inbound.setToWareCode(warehouse.getWareCode());
        inbound.setToWareName(warehouse.getWareName());
        // 采购入库一律入到「良品·在库·自有」；不良品要先入库再登记损坏
        inbound.setFromState(StockState.available());
        inbound.setToState(StockState.available());
        // 手工直接入库不占用系统采购订单数量；外部参考号仅用于追溯。
        // 调用方已指定来源类型时保持不动——采购发货入库借用本方法建单，
        // 靠 FH 来源类型在审核时走回写采购订单的分支。
        if (isBlank(inbound.getSourceDocType()))
        {
            inbound.setSourceDocType("DIRECT");
            inbound.setSourceDocTypeName("直接入库");
        }
        inbound.setTableSuffix(documentService.getTableSuffixByBusinessDate(inbound.getDocDate()));

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (DocumentDetail detail : inbound.getDetails())
        {
            if (detail == null || isBlank(detail.getGoodsCode()))
            {
                throw new ServiceException("入库商品不能为空");
            }
            if (detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品'" + detail.getGoodsCode() + "'的入库数量必须大于0");
            }

            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, detail.getGoodsCode());
            if (goods == null)
            {
                throw new ServiceException("商品'" + detail.getGoodsCode() + "'不存在");
            }

            TrackingData tracking = validateInboundTracking(shopCode, goods, detail.getQty(),
                    detail.getBatchCode(), detail.getSerialNumbers());
            BigDecimal costPrice = detail.getCostPrice() == null ? BigDecimal.ZERO : detail.getCostPrice();
            if (costPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'的成本单价不能小于0");
            }
            BigDecimal costAmount = costPrice.multiply(detail.getQty()).setScale(2, RoundingMode.HALF_UP);

            detail.setShopCode(shopCode);
            detail.setGoodsName(isBlank(detail.getGoodsName()) ? goods.getGoodsName() : detail.getGoodsName());
            detail.setSpec(detail.getSpec() == null ? goods.getSpec() : detail.getSpec());
            detail.setUnit(detail.getUnit() == null ? goods.getUnit() : detail.getUnit());
            detail.setWareCode(warehouse.getWareCode());
            detail.setWareName(warehouse.getWareName());
            detail.setStockState(StockState.available());
            detail.setBatchCode(tracking.batchCode);
            detail.setSerialNumbers(tracking.serialNumbers);
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costAmount);

            totalQty = totalQty.add(detail.getQty());
            totalAmount = totalAmount.add(costAmount);
        }
        inbound.setTotalQty(totalQty);
        inbound.setTotalAmount(totalAmount);

        documentService.insertDocument(inbound);
        return inbound;
    }

    /**
     * 从采购订单生成入库单
     */
    @Override
    @Transactional
    public DocumentMain createInboundFromPurchase(String shopCode, String purchaseCode,
            List<InboundItem> inboundItems, String createUser)
    {
        // 0. 旧通道开关。该路径与采购到货的在途量互不感知，同时启用会造成超收，默认停用。
        if (!"true".equalsIgnoreCase(configService.selectConfigByKey(LEGACY_INBOUND_SWITCH)))
        {
            throw new ServiceException("采购订单直接入库已停用，请通过「采购管理 → 采购到货与质检」办理入库");
        }

        // 1. 校验采购订单
        PurchaseOrder purchaseOrder = purchaseOrderMapper.selectPurchaseOrderByCodeForUpdate(shopCode, purchaseCode);
        if (purchaseOrder == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if (!"approved".equals(purchaseOrder.getStatus()))
        {
            throw new ServiceException("只有已审核的采购订单才能生成入库单");
        }

        // 2. 查询采购订单明细
        List<PurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectItemsByPurchaseCodeForUpdate(
                shopCode, purchaseCode);
        if (orderItems == null || orderItems.isEmpty())
        {
            throw new ServiceException("采购订单明细为空");
        }
        for (PurchaseOrderItem orderItem : orderItems)
        {
            orderItem.setSerialNumbers(SerialNumberRemarkCodec.decode(orderItem.getSerialNumbersText()));
        }

        // 3. 校验入库明细
        if (inboundItems == null || inboundItems.isEmpty())
        {
            throw new ServiceException("入库明细不能为空");
        }

        // 采购入库一律入到「良品·在库·自有」；不良品要先入库再登记损坏
        StockState resolvedState = StockState.available();
        String tableSuffix = documentService.getTableSuffixByBusinessDate(new java.util.Date());

        // 4. 构建入库单
        DocumentMain inbound = new DocumentMain();
        inbound.setShopCode(shopCode);
        inbound.setDocType(InventoryDocumentTypes.PURCHASE_INBOUND);
        inbound.setDocTypeName("采购入库");
        inbound.setDocDate(new java.util.Date());
        inbound.setPartnerCode(purchaseOrder.getPartnerCode());
        inbound.setPartnerName(purchaseOrder.getPartnerName());
        inbound.setSourceDocCode(purchaseCode);
        inbound.setSourceDocType(InventoryDocumentTypes.PURCHASE_ORDER_SOURCE);
        inbound.setSourceDocTypeName("采购订单");
        inbound.setCreateUser(createUser);
        inbound.setHandler(createUser);
        inbound.setHandlerTime(new java.util.Date());
        inbound.setFromWareCode("");
        inbound.setFromWareName("");
        inbound.setFromState(resolvedState);
        inbound.setToState(resolvedState);
        inbound.setTableSuffix(tableSuffix);
        inbound.setRemark("由采购订单" + purchaseCode + "生成");

        List<DocumentDetail> details = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 待审核入库量要跨全部月表统计（理由见 DocumentDetailMapper.selectPendingInboundQty
        // 上面的注释）。后缀列表在这里取一次，不要放进下面的明细循环里反复查
        // information_schema。
        List<String> pendingSuffixes = MonthTableSuffix.validDescending(
                documentDetailMapper.selectDocumentTableSuffixes());

        for (InboundItem inboundItem : inboundItems)
        {
            if (inboundItem == null || inboundItem.getGoodsCode() == null || inboundItem.getGoodsCode().trim().isEmpty())
            {
                throw new ServiceException("入库商品不能为空");
            }
            if (inboundItem.getQty() == null || inboundItem.getQty().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品'" + inboundItem.getGoodsCode() + "'的入库数量必须大于0");
            }
            if (inboundItem.getWareCode() == null || inboundItem.getWareCode().trim().isEmpty())
            {
                throw new ServiceException("商品'" + inboundItem.getGoodsCode() + "'必须选择入库仓库");
            }
            // 查找对应的采购明细
            PurchaseOrderItem orderItem = findOrderItem(orderItems, inboundItem.getGoodsCode());
            if (orderItem == null)
            {
                throw new ServiceException("商品'" + inboundItem.getGoodsCode() + "'不在采购订单中");
            }
            long duplicateCount = inboundItems.stream()
                    .filter(item -> item != null && inboundItem.getGoodsCode().equals(item.getGoodsCode()))
                    .count();
            if (duplicateCount > 1)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'不能重复添加入库明细");
            }

            // 校验入库数量
            BigDecimal pendingQty = pendingSuffixes.isEmpty() ? BigDecimal.ZERO
                    : documentDetailMapper.selectPendingInboundQty(
                            shopCode, purchaseCode, inboundItem.getGoodsCode(), pendingSuffixes);
            if (pendingQty == null)
            {
                pendingQty = BigDecimal.ZERO;
            }
            BigDecimal remainQty = orderItem.getQty()
                    .subtract(orderItem.getReceivedQty() != null ? orderItem.getReceivedQty() : BigDecimal.ZERO)
                    .subtract(pendingQty);
            if (inboundItem.getQty().compareTo(remainQty) > 0)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'入库数量超过剩余未到货数量，剩余：" + remainQty);
            }

            // 查询商品信息
            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, inboundItem.getGoodsCode());
            if (goods == null)
            {
                throw new ServiceException("商品'" + inboundItem.getGoodsCode() + "'不存在");
            }

            String batchCode = inboundItem.getBatchCode() == null ? "" : inboundItem.getBatchCode().trim();
            if (batchCode.isEmpty() && orderItem.getBatchCode() != null)
            {
                batchCode = orderItem.getBatchCode().trim();
            }
            List<String> serialNumbers = SerialNumberRemarkCodec.normalize(inboundItem.getSerialNumbers());
            BigDecimal remainingOrderQty = orderItem.getQty()
                    .subtract(orderItem.getReceivedQty() == null ? BigDecimal.ZERO : orderItem.getReceivedQty());
            if (serialNumbers.isEmpty() && orderItem.getSerialNumbers() != null
                    && orderItem.getSerialNumbers().size() == inboundItem.getQty().intValue()
                    && inboundItem.getQty().compareTo(remainingOrderQty) == 0)
            {
                serialNumbers = SerialNumberRemarkCodec.normalize(orderItem.getSerialNumbers());
            }
            TrackingData tracking = validateInboundTracking(shopCode, goods, inboundItem.getQty(), batchCode, serialNumbers);
            batchCode = tracking.batchCode;
            serialNumbers = tracking.serialNumbers;

            // 查询仓库信息
            InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, inboundItem.getWareCode());
            if (warehouse == null)
            {
                throw new ServiceException("仓库'" + inboundItem.getWareCode() + "'不存在");
            }

            // 计算成本
            // 采购订单入库成本只能来自已审核采购订单，客户端字段仅保留兼容性，不能改写库存成本。
            BigDecimal costPrice = orderItem.getUnitPrice();
            if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'的成本单价无效");
            }
            BigDecimal costAmount = costPrice.multiply(inboundItem.getQty()).setScale(2, RoundingMode.HALF_UP);

            // 构建明细
            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(shopCode);
            detail.setGoodsCode(inboundItem.getGoodsCode());
            detail.setGoodsName(goods.getGoodsName());
            detail.setSpec(goods.getSpec());
            detail.setUnit(goods.getUnit());
            detail.setWareCode(warehouse.getWareCode());
            detail.setWareName(warehouse.getWareName());
            detail.setStockState(resolvedState);
            detail.setQty(inboundItem.getQty());
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costAmount);
            detail.setBatchCode(batchCode);
            detail.setSerialNumbers(serialNumbers);
            detail.setRemark(SerialNumberRemarkCodec.encode(detail.getSerialNumbers()));

            if (inbound.getToWareCode() == null)
            {
                inbound.setToWareCode(inboundItem.getWareCode());
                inbound.setToWareName(warehouse.getWareName());
            }

            details.add(detail);
            totalQty = totalQty.add(inboundItem.getQty());
            totalAmount = totalAmount.add(costAmount);
        }

        inbound.setDetails(details);
        inbound.setTotalQty(totalQty);
        inbound.setTotalAmount(totalAmount);

        // 5. 生成入库单号
        String docCode = generateInboundDocCode();
        inbound.setDocCode(docCode);

        // 6. 保存入库单
        documentService.insertDocument(inbound);

        return inbound;
    }

    /**
     * 由采购发货单创建采购入库单。入库员在库存模块「入库信息 → 采购入库」发起。
     *
     * <p>只处理本次提交的行，其余行保持「未处理」留待下次入库。
     * 实际到货数量不设上限（决议 A1b），既不卡发货单的预计数量，也不卡采购合同量。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentMain createInboundFromShipment(String shopCode, ShipmentInboundCommand command, String createUser)
    {
        if (command == null || isBlank(command.getReceiveCode())) throw new ServiceException("发货单号不能为空");
        if (command.getItems() == null || command.getItems().isEmpty()) throw new ServiceException("本次入库明细不能为空");
        if (isBlank(command.getReceiveUser())) throw new ServiceException("签收人不能为空");
        if (command.getReceiveTime() == null) throw new ServiceException("签收时间不能为空");
        if (isBlank(command.getToWareCode())) throw new ServiceException("入库仓库不能为空");

        String receiveCode = command.getReceiveCode().trim();
        String suffix = resolveShipmentSuffix(shopCode, receiveCode, command.getTableSuffix());

        PurchaseReceive receive = receiveMapper.selectByCodeForUpdate(shopCode, receiveCode, suffix);
        if (receive == null) throw new ServiceException("发货单不存在");
        if (IPurchaseReceiveService.STATUS_STOCKED.equals(receive.getStatus()))
        {
            throw new ServiceException("发货单已全部入库，无需重复处理");
        }
        if (receive.getSendDate() != null && command.getReceiveTime().before(receive.getSendDate()))
        {
            throw new ServiceException("签收时间不能早于发货日期");
        }

        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCodeForUpdate(shopCode, receive.getPurchaseCode());
        if (order == null) throw new ServiceException("来源采购订单不存在");

        Map<String, PurchaseReceiveItem> storedMap = new LinkedHashMap<>();
        for (PurchaseReceiveItem stored : receiveItemMapper.selectByReceiveCode(shopCode, receiveCode, suffix))
        {
            storedMap.put(stored.getDetailCode(), stored);
        }
        // 建单与回写用同一批 DocumentDetail 对象，createDirectInbound 会就地补上
        // 系统生成的批次号，回写发货明细时直接读回来，两边不会有出入。
        DocumentMain document = new DocumentMain();
        document.setToWareCode(command.getToWareCode().trim());
        // 采购入库一律入到「良品·在库·自有」；不良品要先入库再登记损坏
        document.setToState(StockState.available());
        document.setPartnerCode(order.getPartnerCode());
        document.setPartnerName(order.getPartnerName());
        document.setSourceDocCode(receiveCode);
        document.setSourceDocType(InventoryDocumentTypes.PURCHASE_SHIPMENT_SOURCE);
        document.setSourceDocTypeName("采购发货单");
        document.setDocDate(command.getReceiveTime());
        document.setRemark(isBlank(command.getRemark())
                ? "由采购发货单" + receiveCode + "入库，来源采购单" + receive.getPurchaseCode()
                : command.getRemark().trim());

        Map<String, DocumentDetail> detailByReceiveLine = new LinkedHashMap<>();
        List<DocumentDetail> details = new ArrayList<>();
        Set<String> goodsInDoc = new HashSet<>();
        for (ShipmentInboundItem item : command.getItems())
        {
            if (item == null || isBlank(item.getReceiveDetailCode())) throw new ServiceException("入库明细必须关联发货明细");
            PurchaseReceiveItem stored = storedMap.get(item.getReceiveDetailCode().trim());
            if (stored == null) throw new ServiceException("发货明细不存在");
            if (stored.getActualQty() != null)
            {
                throw new ServiceException("商品'" + stored.getGoodsName() + "'已被其它入库单占用，请刷新后重试");
            }
            // 允许填 0：整行没到货时按全额短装关闭，整单照样一次入完。
            // 0 的行不进库存单据（过账 0 没有意义），只在回写发货明细时标短装关闭。
            if (item.getActualQty() == null || item.getActualQty().signum() < 0)
            {
                throw new ServiceException("商品'" + stored.getGoodsName() + "'的实际到货数量不能为负数");
            }
            // 审核时按商品编码把入库明细配回发货明细，同一单内商品必须唯一。
            // 采购订单本身禁止同商品重复行，发货单由采购明细派生，正常不会撞。
            if (!goodsInDoc.add(stored.getGoodsCode()))
            {
                throw new ServiceException("商品'" + stored.getGoodsName() + "'在同一张入库单内重复");
            }

            DocumentDetail detail = new DocumentDetail();
            detail.setGoodsCode(stored.getGoodsCode());
            detail.setQty(item.getActualQty());
            detail.setBatchCode(item.getBatchCode());
            detail.setSerialNumbers(SerialNumberRemarkCodec.normalize(item.getSerialNumbers()));
            // 成本一律取采购订单单价，客户端传什么都不作数
            detail.setCostPrice(stored.getPrice());
            if (item.getActualQty().signum() > 0) details.add(detail);
            detailByReceiveLine.put(stored.getDetailCode(), detail);
        }

        // 发货单是整单入库的：一次必须覆盖全部未处理行，不允许只入一部分。
        // 分批到货请采购员拆成多张发货单，各自带自己的物流单号。
        for (PurchaseReceiveItem stored : storedMap.values())
        {
            if (stored.getActualQty() == null && !detailByReceiveLine.containsKey(stored.getDetailCode()))
            {
                throw new ServiceException("发货单必须整单入库，商品'" + stored.getGoodsName()
                        + "'没有填实际到货数量。整行没到货请填 0。");
            }
        }
        if (details.isEmpty())
        {
            throw new ServiceException("整单一件都没到货，请让采购员取消这张发货单，不要建入库单");
        }
        document.setDetails(details);

        createDirectInbound(shopCode, document, createUser);

        // 回写发货明细：未处理 -> 入库中。actualqty is null 的并发保护在 SQL 里，
        // 影响行数不为 1 说明这行刚被别的入库单抢走。
        for (Map.Entry<String, DocumentDetail> entry : detailByReceiveLine.entrySet())
        {
            DocumentDetail detail = entry.getValue();
            PurchaseReceiveItem write = new PurchaseReceiveItem();
            write.setShopCode(shopCode);
            write.setDetailCode(entry.getKey());
            write.setActualQty(detail.getQty());
            write.setBatchCode(detail.getBatchCode());
            write.setSerialNumbers(SerialNumberRemarkCodec.encode(detail.getSerialNumbers()));
            write.setWarehouseCode(document.getToWareCode());
            write.setWarehouseName(document.getToWareName());
            write.setInventoryDocCode(document.getDocCode());
            if (receiveItemMapper.fillActualQty(write, suffix) != 1)
            {
                throw new ServiceException("发货明细已被其它入库单占用，请刷新后重试");
            }
        }

        // 运费在签收时就付出去了，与入库单审核与否无关，因此建单即计入，驳回也不扣回。
        // 驳回后重建入库单时，界面会提示已累计金额，避免重复录入。
        // 只有到付才由入库员出钱：包邮没有运费，寄付的运费供应商发货时已付清、
        // 金额由采购员记在发货单上，这里再累加一次就成了同一笔钱记两遍。
        BigDecimal freight = "collect".equals(receive.getFreightType())
                ? normalizeFreight(command.getFreightAmount())
                : BigDecimal.ZERO;
        receiveMapper.applyReceiveInfo(shopCode, receiveCode, suffix, command.getReceiveUser().trim(),
                command.getReceiveTime(), freight, createUser);
        if (freight.signum() > 0)
        {
            purchaseOrderMapper.increaseActualFreight(shopCode, receive.getPurchaseCode(), freight);
        }

        receiveService.refreshStatus(shopCode, receiveCode, suffix, createUser);
        return document;
    }

    /** 前端传了月表后缀就用它，没传则跨月表找——入库员未必知道发货单落在哪个月。 */
    private String resolveShipmentSuffix(String shopCode, String receiveCode, String tableSuffix)
    {
        if (!isBlank(tableSuffix) && tableSuffix.trim().matches("\\d{6}"))
        {
            return tableSuffix.trim();
        }
        PurchaseReceive located = receiveService.findReceiveAcrossMonths(shopCode, receiveCode);
        if (located == null) throw new ServiceException("发货单不存在");
        return located.getTableSuffix();
    }

    /** 到付时入库员录入的本次实付运费。非到付的发货单不会走到这里。 */
    private BigDecimal normalizeFreight(BigDecimal freight)
    {
        if (freight == null) return BigDecimal.ZERO;
        if (freight.signum() < 0) throw new ServiceException("本次运费不能为负数");
        return freight.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 采购入库单审核
     */
    @Override
    @Transactional
    public int confirmPurchaseInbound(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser)
    {
        if (tableSuffix == null || tableSuffix.isEmpty())
        {
            tableSuffix = documentService.getCurrentTableSuffix();
        }
        // 先锁定主单，使本次读取的明细与随后库存审核使用同一版本。
        DocumentMain inbound = documentMainMapper.selectDocumentByCodeForUpdate(shopCode, docCode, tableSuffix);
        if (inbound == null)
        {
            throw new ServiceException("入库单不存在");
        }
        if (!InventoryDocumentTypes.PURCHASE_INBOUND.equals(inbound.getDocType()))
        {
            throw new ServiceException("该单据不是采购入库单");
        }
        if (!"待审核".equals(inbound.getConfirmState()))
        {
            throw new ServiceException("只有待审核状态的采购入库单才能审核");
        }
        List<DocumentDetail> confirmedDetails = documentDetailMapper.selectDetailsByDocCode(shopCode, docCode, tableSuffix);
        if (confirmedDetails != null)
        {
            for (DocumentDetail detail : confirmedDetails)
            {
                detail.setSerialNumbers(SerialNumberRemarkCodec.decode(detail.getRemark()));
            }
        }

        PurchaseOrder sourceOrder = null;
        List<PurchaseOrderItem> lockedOrderItems = null;
        if (approved && isPurchaseOrderInbound(inbound))
        {
            sourceOrder = purchaseOrderMapper.selectPurchaseOrderByCodeForUpdate(
                    shopCode, inbound.getSourceDocCode());
            if (sourceOrder == null)
            {
                throw new ServiceException("来源采购订单'" + inbound.getSourceDocCode() + "'不存在");
            }
            if (!"approved".equals(sourceOrder.getStatus()) && !"completed".equals(sourceOrder.getStatus()))
            {
                throw new ServiceException("来源采购订单不是已审核状态，不能入库");
            }
            lockedOrderItems = purchaseOrderItemMapper.selectItemsByPurchaseCodeForUpdate(
                    shopCode, inbound.getSourceDocCode());
            applyPurchaseOrderCosts(shopCode, inbound, confirmedDetails, lockedOrderItems, tableSuffix);
            increasePurchaseOrderReceivedQty(shopCode, inbound.getSourceDocCode(),
                    confirmedDetails, lockedOrderItems);
        }

        // 采购发货单来源：审核通过要回写采购订单与发货明细，驳回要把占用的行放回去。
        if (isShipmentInbound(inbound))
        {
            settleShipmentInbound(shopCode, inbound, confirmedDetails, approved, confirmUser);
        }

        // 采购数量占用成功后再执行库存联动；后续任一步失败都会由同一事务整体回滚。
        int result = documentService.confirmDocument(shopCode, docCode, tableSuffix, approved, confirmUser);

        if (approved && sourceOrder != null)
        {
            checkPurchaseOrderComplete(shopCode, inbound.getSourceDocCode());
        }

        if (approved)
        {
            accumulateGoodsPartner(shopCode, inbound, confirmedDetails);
        }

        return result;
    }

    /**
     * 累积「商品 ← 供应商」供货关系，供采购下单和手工入库推荐供应商用。
     *
     * <p>埋在审核通过这一步，是因为三条入库通道（手工直接入库、采购订单入库、
     * 采购发货入库）都要经过本方法，一处埋点覆盖全部；也因为只有审核通过才算
     * 真的进过这家的货，待审核和已驳回的单不该形成推荐。</p>
     *
     * <p>成本单价取审核后的最终值——采购订单来源的单会在 applyPurchaseOrderCosts
     * 里被改写成订单单价，那里改的是同一批明细对象，所以这里读到的是改写后的。</p>
     *
     * <p>这是一张纯派生的展示表，写失败不该把已经过账的入库单一起回滚，所以整段
     * 捕获异常只记日志。对不上就重跑 20260904_goods_partner_recommend.sql 刷一遍。</p>
     */
    private void accumulateGoodsPartner(String shopCode, DocumentMain inbound, List<DocumentDetail> details)
    {
        if (isBlank(inbound.getPartnerCode()) || details == null || details.isEmpty())
        {
            return;
        }
        String partnerCode = inbound.getPartnerCode().trim();
        String partnerName = inbound.getPartnerName() == null ? "" : inbound.getPartnerName().trim();
        java.util.Date docDate = inbound.getDocDate() == null ? new java.util.Date() : inbound.getDocDate();
        // 同一张入库单内商品不重复（建单时已校验），这里再去重一次只为兜住历史脏数据，
        // 否则一张单会让同一商品的次数加两遍。
        Set<String> counted = new HashSet<>();
        for (DocumentDetail detail : details)
        {
            if (detail == null || isBlank(detail.getGoodsCode()) || !counted.add(detail.getGoodsCode()))
            {
                continue;
            }
            try
            {
                goodsPartnerMapper.accumulate(shopCode, detail.getGoodsCode().trim(), partnerCode,
                        partnerName, inbound.getDocCode(), docDate, detail.getCostPrice());
            }
            catch (Exception e)
            {
                log.warn("累积商品供货关系失败，不影响入库单 {}：goods={}, partner={}",
                        inbound.getDocCode(), detail.getGoodsCode(), partnerCode, e);
            }
        }
    }

    private boolean isShipmentInbound(DocumentMain inbound)
    {
        return InventoryDocumentTypes.PURCHASE_SHIPMENT_SOURCE.equals(inbound.getSourceDocType())
                && !isBlank(inbound.getSourceDocCode());
    }

    /**
     * 采购发货入库单的审核结算。
     *
     * <p><b>通过</b>：改写成本为采购订单单价 → 按实际到货数量累加采购订单已到货数量
     * （不设上限，决议 A1b）→ 反推采购订单状态 → 发货明细转「已入库」→ 重算发货单状态。</p>
     *
     * <p><b>驳回</b>：把本单占用的发货明细放回「未处理」，入库员可以重做。
     * 运费不扣回——钱在签收时已经付给承运商了，驳回入库单并不能把钱要回来。</p>
     */
    private void settleShipmentInbound(String shopCode, DocumentMain inbound,
            List<DocumentDetail> details, boolean approved, String confirmUser)
    {
        String receiveCode = inbound.getSourceDocCode().trim();
        PurchaseReceive receive = receiveService.findReceiveAcrossMonths(shopCode, receiveCode);
        if (receive == null)
        {
            throw new ServiceException("来源采购发货单'" + receiveCode + "'不存在");
        }
        String suffix = receive.getTableSuffix();

        if (!approved)
        {
            receiveItemMapper.resetByInventoryDoc(shopCode, inbound.getDocCode(), suffix);
            receiveService.refreshStatus(shopCode, receiveCode, suffix, confirmUser);
            return;
        }

        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCodeForUpdate(
                shopCode, receive.getPurchaseCode());
        if (order == null)
        {
            throw new ServiceException("来源采购订单'" + receive.getPurchaseCode() + "'不存在");
        }
        List<PurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectItemsByPurchaseCodeForUpdate(
                shopCode, receive.getPurchaseCode());
        applyPurchaseOrderCosts(shopCode, inbound, details, orderItems, inbound.getTableSuffix());

        Map<String, PurchaseOrderItem> orderItemMap = new LinkedHashMap<>();
        for (PurchaseOrderItem item : orderItems)
        {
            orderItemMap.put(item.getGoodsCode(), item);
        }

        // 入库明细按商品编码配回发货明细。建单时已校验过同一入库单内商品不重复，
        // 且采购订单本身禁止同商品重复行，所以这个键是唯一的。
        Map<String, PurchaseReceiveItem> receiveItemByGoods = new LinkedHashMap<>();
        for (PurchaseReceiveItem item : receiveItemMapper.selectByReceiveCode(shopCode, receiveCode, suffix))
        {
            if (inbound.getDocCode().equals(item.getInventoryDocCode()) && isBlank(item.getInventoryDetailCode()))
            {
                receiveItemByGoods.put(item.getGoodsCode(), item);
            }
        }

        for (DocumentDetail detail : details)
        {
            PurchaseReceiveItem line = receiveItemByGoods.get(detail.getGoodsCode());
            if (line == null)
            {
                throw new ServiceException("入库明细'" + detail.getGoodsCode() + "'找不到对应的发货明细");
            }
            PurchaseOrderItem orderItem = orderItemMap.get(detail.getGoodsCode());
            if (orderItem == null)
            {
                throw new ServiceException("商品'" + detail.getGoodsCode() + "'不在来源采购订单中");
            }
            // 不带超收拦截：入库员点到多少就入多少，合同量也不卡（决议 A1b）。
            // 超收后 remainingqty 变负，前端显示成「超收 N」。
            if (purchaseOrderItemMapper.increaseReceivedQtyUnchecked(
                    shopCode, orderItem.getId(), detail.getQty()) != 1)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'的累计到货数量更新失败");
            }
            receiveItemMapper.markStocked(shopCode, line.getDetailCode(), suffix,
                    detail.getDetailCode(), resolveArrivalStatus(line.getQty(), detail.getQty()));
            receiveItemByGoods.remove(detail.getGoodsCode());
        }

        // 实际到货数量填 0 的行没有对应的库存明细（过账 0 没有意义），上面那轮碰不到它们。
        // 这里按全额短装关闭，否则它们会一直卡在「入库中」，整单永远到不了「已入库」。
        for (PurchaseReceiveItem line : receiveItemByGoods.values())
        {
            receiveItemMapper.markShortClosed(shopCode, line.getDetailCode(), suffix, inbound.getDocCode());
        }

        updatePurchaseOrderStatus(shopCode, receive.getPurchaseCode());
        receiveService.refreshStatus(shopCode, receiveCode, suffix, confirmUser);
    }

    /** 到货情况：实收等于预计为齐套，少于为短装，多于为超发。 */
    private String resolveArrivalStatus(BigDecimal expectQty, BigDecimal actualQty)
    {
        BigDecimal expect = expectQty == null ? BigDecimal.ZERO : expectQty;
        BigDecimal actual = actualQty == null ? BigDecimal.ZERO : actualQty;
        int compared = actual.compareTo(expect);
        return compared == 0 ? "齐套" : compared < 0 ? "短装" : "超发";
    }

    /**
     * 反推采购订单状态。
     *
     * <p>用「已到货 &gt;= 采购数量」判定完成，超收自然算完成，不用为决议 A1b 额外处理。</p>
     */
    private void updatePurchaseOrderStatus(String shopCode, String purchaseCode)
    {
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByPurchaseCode(shopCode, purchaseCode);
        if (items == null || items.isEmpty())
        {
            return;
        }
        boolean all = true;
        boolean any = false;
        for (PurchaseOrderItem item : items)
        {
            BigDecimal received = item.getReceivedQty() == null ? BigDecimal.ZERO : item.getReceivedQty();
            any |= received.signum() > 0;
            all &= received.compareTo(item.getQty()) >= 0;
        }
        purchaseOrderMapper.updateStatus(shopCode, purchaseCode,
                all ? "completed" : any ? "partial_received" : "approved");
    }

    private void applyPurchaseOrderCosts(String shopCode, DocumentMain inbound,
            List<DocumentDetail> details, List<PurchaseOrderItem> orderItems, String tableSuffix)
    {
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("采购入库单明细为空");
        }
        Map<String, PurchaseOrderItem> orderItemMap = new LinkedHashMap<>();
        for (PurchaseOrderItem item : orderItems)
        {
            if (orderItemMap.put(item.getGoodsCode(), item) != null)
            {
                throw new ServiceException("采购订单商品'" + item.getGoodsName() + "'存在重复行，无法确定入库成本");
            }
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (DocumentDetail detail : details)
        {
            PurchaseOrderItem orderItem = orderItemMap.get(detail.getGoodsCode());
            if (orderItem == null)
            {
                throw new ServiceException("商品'" + detail.getGoodsCode() + "'不在来源采购订单中");
            }
            BigDecimal costPrice = orderItem.getUnitPrice();
            if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'的采购订单单价无效");
            }
            BigDecimal costAmount = costPrice.multiply(detail.getQty()).setScale(2, RoundingMode.HALF_UP);
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costAmount);
            documentDetailMapper.updateDetailCost(shopCode, detail.getDetailCode(), costPrice,
                    costAmount, tableSuffix);
            totalAmount = totalAmount.add(costAmount);
        }
        inbound.setTotalAmount(totalAmount);
        documentMainMapper.updateDocumentTotalAmount(shopCode, inbound.getDocCode(), totalAmount, tableSuffix);
    }

    /**
     * 查找采购明细
     */
    private PurchaseOrderItem findOrderItem(List<PurchaseOrderItem> orderItems, String goodsCode)
    {
        for (PurchaseOrderItem item : orderItems)
        {
            if (goodsCode.equals(item.getGoodsCode()))
            {
                return item;
            }
        }
        return null;
    }

    /**
     * 更新采购订单已到货数量
     */
    private void increasePurchaseOrderReceivedQty(String shopCode, String purchaseCode,
            List<DocumentDetail> details, List<PurchaseOrderItem> orderItems)
    {
        if (details == null || details.isEmpty())
        {
            return;
        }

        Map<String, PurchaseOrderItem> orderItemMap = new LinkedHashMap<>();
        for (PurchaseOrderItem item : orderItems)
        {
            PurchaseOrderItem previous = orderItemMap.put(item.getGoodsCode(), item);
            if (previous != null)
            {
                throw new ServiceException("采购订单商品'" + item.getGoodsName()
                        + "'存在重复行，无法安全累计入库数量");
            }
        }
        Map<String, BigDecimal> inboundQtyMap = new LinkedHashMap<>();
        for (DocumentDetail detail : details)
        {
            if (detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品'" + detail.getGoodsName() + "'的入库数量必须大于0");
            }
            inboundQtyMap.merge(detail.getGoodsCode(), detail.getQty(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : inboundQtyMap.entrySet())
        {
            PurchaseOrderItem item = orderItemMap.get(entry.getKey());
            if (item == null)
            {
                throw new ServiceException("商品'" + entry.getKey() + "'不在来源采购订单中");
            }
            int rows = purchaseOrderItemMapper.increaseReceivedQty(
                    shopCode, item.getId(), entry.getValue());
            if (rows == 0)
            {
                BigDecimal receivedQty = item.getReceivedQty() == null
                        ? BigDecimal.ZERO : item.getReceivedQty();
                BigDecimal remainingQty = item.getQty().subtract(receivedQty).max(BigDecimal.ZERO);
                throw new ServiceException("商品'" + item.getGoodsName()
                        + "'入库数量超过采购订单剩余未到货数量，当前剩余：" + remainingQty);
            }
        }
    }

    private boolean isPurchaseOrderInbound(DocumentMain inbound)
    {
        return InventoryDocumentTypes.PURCHASE_ORDER_SOURCE.equals(inbound.getSourceDocType())
                && inbound.getSourceDocCode() != null
                && !inbound.getSourceDocCode().trim().isEmpty()
                && !inbound.getSourceDocCode().startsWith("DIRECT-");
    }

    /**
     * 检查采购订单是否全部到货
     */
    private void checkPurchaseOrderComplete(String shopCode, String purchaseCode)
    {
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByPurchaseCode(shopCode, purchaseCode);
        if (items == null || items.isEmpty())
        {
            return;
        }

        boolean allReceived = true;
        for (PurchaseOrderItem item : items)
        {
            BigDecimal qty = item.getQty() != null ? item.getQty() : BigDecimal.ZERO;
            BigDecimal receivedQty = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
            if (receivedQty.compareTo(qty) < 0)
            {
                allReceived = false;
                break;
            }
        }

        if (allReceived)
        {
            purchaseOrderMapper.updateStatus(shopCode, purchaseCode, "completed");
        }
    }

    private TrackingData validateInboundTracking(String shopCode, InventoryGoods goods, BigDecimal qty,
            String batchCode, List<String> serialNumbers)
    {
        String batchStrategy = isBlank(goods.getBatchStrategy())
                ? "none" : goods.getBatchStrategy().trim().toLowerCase();
        String normalizedBatchCode = batchCode == null ? "" : batchCode.trim();
        List<String> normalizedSerialNumbers = SerialNumberRemarkCodec.normalize(serialNumbers);

        if ("none".equals(batchStrategy))
        {
            if (!normalizedBatchCode.isEmpty() || !normalizedSerialNumbers.isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'无需填写批次号或序列号");
            }
            normalizedBatchCode = null;
        }
        else if ("batch".equals(batchStrategy))
        {
            if (normalizedBatchCode.isEmpty())
            {
                normalizedBatchCode = batchService.generateBatchCode(shopCode);
            }
            if (!normalizedSerialNumbers.isEmpty())
            {
                throw new ServiceException("批次管理商品无需填写序列号");
            }
        }
        else if ("serial".equals(batchStrategy))
        {
            if (normalizedBatchCode.isEmpty())
            {
                normalizedBatchCode = batchService.generateBatchCode(shopCode);
            }
            int expectedSerialCount;
            try
            {
                expectedSerialCount = qty.intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new ServiceException("序列号管理商品'" + goods.getGoodsName() + "'的入库数量必须是整数");
            }
            if (normalizedSerialNumbers.size() != expectedSerialCount)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'必须填写" + expectedSerialCount + "个序列号");
            }
        }
        else
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的批次策略无效：" + batchStrategy);
        }

        return new TrackingData(normalizedBatchCode, normalizedSerialNumbers);
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static class TrackingData
    {
        private final String batchCode;
        private final List<String> serialNumbers;

        private TrackingData(String batchCode, List<String> serialNumbers)
        {
            this.batchCode = batchCode;
            this.serialNumbers = serialNumbers;
        }
    }

    /**
     * 生成入库单号
     */
    private String generateInboundDocCode()
    {
        return documentCodeGenerator.nextInboundCode();
    }
}
