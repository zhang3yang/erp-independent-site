package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryPartner;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryPartnerMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.service.IOutboundService;

/**
 * 出库Service业务层处理
 */
@Service
public class OutboundServiceImpl implements IOutboundService
{
    @Autowired
    private IDocumentService documentService;

    @Autowired
    private DocTypeConfigMapper docTypeConfigMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private IInventorySerialService serialService;

    @Autowired
    private InventoryCostResolver costResolver;

    @Autowired
    private InventoryPartnerMapper partnerMapper;

    @Autowired
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    @Autowired
    private InventoryDocumentPostingService postingService;

    /**
     * 创建出库单
     */
    @Override
    @Transactional
    public DocumentMain createOutbound(String shopCode, String docType, String fromWareCode, StockState fromState,
            String partnerCode, String partnerName, String sourceDocCode, String sourceDocType,
            List<OutboundItem> outboundItems, String remark, String createUser)
    {
        // 1. 校验单据类型
        DocTypeConfig docTypeConfig = docTypeConfigMapper.selectDocTypeByCode(docType);
        if (docTypeConfig == null)
        {
            throw new ServiceException("单据类型'" + docType + "'不存在");
        }
        if (!"O".equals(docTypeConfig.getDirection()))
        {
            throw new ServiceException("单据类型'" + docType + "'不是出库类型");
        }

        // 1.1 校验来源库存状态。放在建单这一步而不是等审核，否则一张扣「不在库」的
        //     出库单会先把待审核锁定量占掉，审核时才被拒，锁定的量要等人工取消才放出来
        DocumentMain stateProbe = new DocumentMain();
        stateProbe.setDocType(docType);
        stateProbe.setFromState(fromState);
        postingService.validateDocument(stateProbe, "O");

        // 2. 校验仓库
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, fromWareCode);
        if (warehouse == null)
        {
            throw new ServiceException("仓库'" + fromWareCode + "'不存在");
        }

        // 3. 校验往来单位（如果提供）
        if (partnerCode != null && !partnerCode.isEmpty())
        {
            InventoryPartner partner = partnerMapper.selectPartnerByCode(shopCode, partnerCode);
            if (partner == null)
            {
                throw new ServiceException("往来单位'" + partnerCode + "'不存在");
            }
        }

        // 4. 校验出库明细
        if (outboundItems == null || outboundItems.isEmpty())
        {
            throw new ServiceException("出库明细不能为空");
        }

        // 5. 构建出库单
        DocumentMain outbound = new DocumentMain();
        outbound.setShopCode(shopCode);
        outbound.setDocType(docType);
        // 补发复用 CK 的库存扣减规则，但业务上不是普通销售发货。名称必须在单据和流水
        // 落库前就区分开，否则库存流水里所有记录都只显示“销售出库”，无法审计补发成本。
        boolean salesReship = InventoryDocumentTypes.SALES_OUTBOUND.equals(docType)
                && InventoryDocumentTypes.SALES_RESHIP_SOURCE.equals(sourceDocType);
        outbound.setDocTypeName(salesReship ? "销售补发出库" : docTypeConfig.getDocTypeName());
        outbound.setDocDate(new java.util.Date());
        outbound.setFromWareCode(fromWareCode);
        outbound.setFromWareName(warehouse.getWareName());
        outbound.setFromState(fromState);
        outbound.setPartnerCode(partnerCode);
        outbound.setPartnerName(partnerName);
        outbound.setSourceDocCode(sourceDocCode);
        outbound.setSourceDocType(sourceDocType);
        if (salesReship)
        {
            outbound.setSourceDocTypeName("销售补发单");
        }
        outbound.setRemark(remark);
        outbound.setCreateUser(createUser);

        List<DocumentDetail> details = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 记录本次出库单中每个商品的累计出库数量，用于同一出库单内同一商品多行时的可用库存校验
        java.util.Map<String, BigDecimal> itemQtyMap = new java.util.HashMap<>();

        for (OutboundItem outboundItem : outboundItems)
        {
            // 查询商品信息
            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, outboundItem.getGoodsCode());
            if (goods == null)
            {
                throw new ServiceException("商品'" + outboundItem.getGoodsCode() + "'不存在");
            }
            String goodsName = valueOrDefault(outboundItem.getGoodsName(), goods.getGoodsName());
            String spec = valueOrDefault(outboundItem.getSpec(), goods.getSpec());
            String unit = valueOrDefault(outboundItem.getUnit(), goods.getUnit());

            // 校验库存是否充足（使用可用库存 = 物理库存 - 锁定数量）
            InventoryStock stock = stockMapper.selectStockByKey(shopCode, outboundItem.getGoodsCode(), fromWareCode, fromState);
            if (stock == null)
            {
                throw new ServiceException("商品'" + goodsName + "'在仓库中无库存记录");
            }

            if (outboundItem.getQty() == null || outboundItem.getQty().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品'" + goodsName + "'的出库数量必须大于0");
            }

            // 计算可用库存：物理库存 - 锁定数量 - 本单中该商品已累计的出库数量
            BigDecimal availableQty = stock.getAvailableQty();
            String itemKey = outboundItem.getGoodsCode() + "|" + fromWareCode + "|" + fromState;
            BigDecimal alreadyAllocated = itemQtyMap.getOrDefault(itemKey, BigDecimal.ZERO);
            BigDecimal remainingAvailable = availableQty.subtract(alreadyAllocated);

            if (remainingAvailable.compareTo(outboundItem.getQty()) < 0)
            {
                throw new ServiceException("商品'" + goodsName + "'可用库存不足，可用库存：" + remainingAvailable + "，需要出库：" + outboundItem.getQty());
            }
            itemQtyMap.put(itemKey, alreadyAllocated.add(outboundItem.getQty()));

            validateTrackingData(shopCode, goods, outboundItem, fromWareCode, fromState);

            // 出库成本来自库存/批次；FIFO 创建时先显示移动平均估算，审核时按实际批次重算。
            boolean fifoBatch = "batch".equals(goods.getBatchStrategy())
                    && "fifo".equals(goods.getCostCalculation());
            BigDecimal costPrice = costResolver.resolveOutboundCostPrice(shopCode, goods,
                    fromWareCode, fromState, outboundItem.getBatchCode(),
                    outboundItem.getSerialNumbers(), outboundItem.getQty(), stock);
            BigDecimal costAmount = costPrice.multiply(outboundItem.getQty()).setScale(2, RoundingMode.HALF_UP);

            // 构建明细
            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(shopCode);
            detail.setGoodsCode(outboundItem.getGoodsCode());
            detail.setGoodsName(goodsName);
            detail.setSpec(spec);
            detail.setUnit(unit);
            detail.setQty(outboundItem.getQty());
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costAmount);
            // 销售单价留空时由单据服务带出本行的入库成本，这里只透传业务员的改价。
            detail.setSalePrice(outboundItem.getSalePrice());
            detail.setBatchCode(outboundItem.getBatchCode());
            // 审核挑批次时要拿它认出自己的定制批次，必须跟着明细落库
            detail.setOrderDetailCode(outboundItem.getOrderDetailCode());
            detail.setSerialNumbers(outboundItem.getSerialNumbers());
            detail.setRemark("");

            details.add(detail);
            totalQty = totalQty.add(outboundItem.getQty());
            totalAmount = totalAmount.add(costAmount);
        }

        outbound.setDetails(details);
        outbound.setTotalQty(totalQty);
        outbound.setTotalAmount(totalAmount);

        // 6. 生成出库单号
        String docCode = generateOutboundDocCode();
        outbound.setDocCode(docCode);

        // 7. 保存出库单
        documentService.insertDocument(outbound);

        return outbound;
    }

    /**
     * 出库单审核
     */
    @Override
    @Transactional
    public int confirmOutbound(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser)
    {
        // 审核出库单（会触发库存联动）
        return documentService.confirmDocument(shopCode, docCode, tableSuffix, approved, confirmUser);
    }

    /**
     * 生成出库单号
     */
    private String generateOutboundDocCode()
    {
        return documentCodeGenerator.nextOutboundCode();
    }

    private String valueOrDefault(String value, String defaultValue)
    {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private void validateTrackingData(String shopCode, InventoryGoods goods, OutboundItem item,
            String wareCode, StockState state)
    {
        String strategy = valueOrDefault(goods.getBatchStrategy(), "none");
        List<String> serialNumbers = normalizeSerialNumbers(item.getSerialNumbers());

        if ("none".equals(strategy))
        {
            item.setBatchCode(null);
            if (!serialNumbers.isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'未启用序列号管理，不能填写序列号");
            }
            return;
        }

        if ("batch".equals(strategy))
        {
            if (!"individual".equals(goods.getCostCalculation()))
            {
                // 只有个别计价的成本来自具体批次，才需要业务员指定出库批次。
                // FIFO 在审核时按入库时间自动分配；加权平均直接扣汇总库存、不消耗批次。
                // 两者都忽略客户端传入值。
                item.setBatchCode(null);
                return;
            }
            if (item.getBatchCode() == null || item.getBatchCode().trim().isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'必须选择出库批次");
            }
            InventoryBatch batch = batchService.selectBatchByKey(
                    shopCode, item.getBatchCode().trim(), goods.getGoodsCode(), wareCode, state);
            if (batch == null || !state.equals(batch.getStockState()) || !"active".equals(batch.getStatus()))
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'选择的批次不可用");
            }
            BigDecimal remainQty = batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            if (remainQty.compareTo(item.getQty()) < 0)
            {
                throw new ServiceException("批次'" + batch.getBatchCode() + "'剩余数量不足，当前剩余：" + remainQty);
            }
            return;
        }

        if ("serial".equals(strategy))
        {
            int expectedCount;
            try
            {
                expectedCount = item.getQty().intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new ServiceException("序列号管理商品'" + goods.getGoodsName() + "'的出库数量必须是整数");
            }
            if (serialNumbers.size() != expectedCount)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'出库数量为" + expectedCount
                        + "，必须选择相同数量的序列号");
            }
            Set<String> batchCodes = new HashSet<>();
            for (String serialNo : serialNumbers)
            {
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo,
                        goods.getGoodsCode(), wareCode, state);
                if (serial == null || !"instock".equals(serial.getStatus())
                        || !goods.getGoodsCode().equals(serial.getGoodsCode())
                        || !wareCode.equals(serial.getWareCode())
                        || !state.equals(serial.getStockState()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'不属于当前商品、仓库或库存状态，或已出库");
                }
                if (serial.getBatchCode() != null && !serial.getBatchCode().isEmpty())
                {
                    batchCodes.add(serial.getBatchCode());
                }
            }
            item.setSerialNumbers(serialNumbers);
            item.setBatchCode(batchCodes.size() == 1 ? batchCodes.iterator().next() : null);
        }
    }

    private List<String> normalizeSerialNumbers(List<String> serialNumbers)
    {
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        if (serialNumbers == null)
        {
            return result;
        }
        for (String serialNo : serialNumbers)
        {
            if (serialNo == null || serialNo.trim().isEmpty())
            {
                continue;
            }
            String normalized = serialNo.trim();
            if (!unique.add(normalized))
            {
                throw new ServiceException("序列号'" + normalized + "'重复");
            }
            result.add(normalized);
        }
        return result;
    }
}
