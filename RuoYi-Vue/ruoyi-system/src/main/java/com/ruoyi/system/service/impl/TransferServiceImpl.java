package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.service.ITransferService;

/**
 * 调拨Service业务层处理
 */
@Service
public class TransferServiceImpl implements ITransferService
{
    @Autowired
    private IDocumentService documentService;

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
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    @Autowired
    private InventoryDocumentPostingService postingService;

    /**
     * 创建调拨单
     */
    @Override
    @Transactional
    public DocumentMain createTransfer(String shopCode, String fromWareCode, StockState fromState,
            String toWareCode, StockState toState, List<TransferItem> transferItems, String remark, String createUser)
    {
        // 1. 校验来源仓库和目标仓库不能相同
        if (fromWareCode.equals(toWareCode))
        {
            throw new ServiceException("来源仓库和目标仓库不能相同");
        }

        // 1.1 校验库存状态。和出库单同理，放在建单这一步，别等审核才拒——
        //     那时待审核锁定量已经占上了，要人工取消单据才放得出来
        DocumentMain stateProbe = new DocumentMain();
        stateProbe.setDocType(InventoryDocumentTypes.WAREHOUSE_TRANSFER);
        stateProbe.setFromState(fromState);
        stateProbe.setToState(toState);
        postingService.validateDocument(stateProbe, "N");

        // 2. 校验仓库
        InventoryWarehouse fromWarehouse = warehouseMapper.selectWarehouseByCode(shopCode, fromWareCode);
        if (fromWarehouse == null)
        {
            throw new ServiceException("来源仓库'" + fromWareCode + "'不存在");
        }

        InventoryWarehouse toWarehouse = warehouseMapper.selectWarehouseByCode(shopCode, toWareCode);
        if (toWarehouse == null)
        {
            throw new ServiceException("目标仓库'" + toWareCode + "'不存在");
        }

        // 3. 校验调拨明细
        if (transferItems == null || transferItems.isEmpty())
        {
            throw new ServiceException("调拨明细不能为空");
        }

        // 4. 构建调拨单
        DocumentMain transfer = new DocumentMain();
        transfer.setShopCode(shopCode);
        transfer.setDocType(InventoryDocumentTypes.WAREHOUSE_TRANSFER);
        transfer.setDocTypeName("仓库调拨");
        transfer.setDocDate(new java.util.Date());
        transfer.setFromWareCode(fromWareCode);
        transfer.setFromWareName(fromWarehouse.getWareName());
        transfer.setFromState(fromState);
        transfer.setToWareCode(toWareCode);
        transfer.setToWareName(toWarehouse.getWareName());
        transfer.setToState(toState);
        transfer.setRemark(remark);
        transfer.setCreateUser(createUser);

        List<DocumentDetail> details = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, BigDecimal> itemQtyMap = new HashMap<>();

        for (TransferItem transferItem : transferItems)
        {
            // 查询商品信息
            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, transferItem.getGoodsCode());
            if (goods == null)
            {
                throw new ServiceException("商品'" + transferItem.getGoodsCode() + "'不存在");
            }

            // 校验来源仓库库存是否充足
            InventoryStock fromStock = stockMapper.selectStockByKey(shopCode, transferItem.getGoodsCode(), fromWareCode, fromState);
            if (fromStock == null)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'在来源仓库中无库存记录");
            }

            BigDecimal availableQty = fromStock.getAvailableQty() == null ? BigDecimal.ZERO : fromStock.getAvailableQty();
            String itemKey = transferItem.getGoodsCode() + "|" + fromWareCode + "|" + fromState;
            BigDecimal alreadyAllocated = itemQtyMap.getOrDefault(itemKey, BigDecimal.ZERO);
            BigDecimal remainingAvailable = availableQty.subtract(alreadyAllocated);
            if (remainingAvailable.compareTo(transferItem.getQty()) < 0)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'可用库存不足，当前可用库存：" + remainingAvailable + "，需要调拨：" + transferItem.getQty());
            }
            itemQtyMap.put(itemKey, alreadyAllocated.add(transferItem.getQty()));

            validateTrackingData(shopCode, goods, transferItem, fromWareCode, fromState);

            // 调拨成本必须来自来源库存/批次，不能使用客户端传入的任意价格。
            BigDecimal costPrice = costResolver.resolveTransferCostPrice(shopCode, goods,
                    fromWareCode, fromState, transferItem.getBatchCode(),
                    transferItem.getSerialNumbers(), transferItem.getQty(), fromStock);
            BigDecimal costAmount = costPrice.multiply(transferItem.getQty()).setScale(2, RoundingMode.HALF_UP);

            // 构建明细
            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(shopCode);
            detail.setGoodsCode(transferItem.getGoodsCode());
            detail.setGoodsName(goods.getGoodsName());
            detail.setSpec(goods.getSpec());
            detail.setUnit(goods.getUnit());
            detail.setQty(transferItem.getQty());
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costAmount);
            detail.setBatchCode(transferItem.getBatchCode());
            detail.setSerialNumbers(transferItem.getSerialNumbers());
            detail.setRemark("");

            details.add(detail);
            totalQty = totalQty.add(transferItem.getQty());
            totalAmount = totalAmount.add(costAmount);
        }

        transfer.setDetails(details);
        transfer.setTotalQty(totalQty);
        transfer.setTotalAmount(totalAmount);

        // 5. 生成调拨单号
        String docCode = generateTransferDocCode();
        transfer.setDocCode(docCode);

        // 6. 保存调拨单
        documentService.insertDocument(transfer);

        return transfer;
    }

    private void validateTrackingData(String shopCode, InventoryGoods goods, TransferItem item,
            String wareCode, StockState state)
    {
        String strategy = goods.getBatchStrategy() == null ? "none" : goods.getBatchStrategy().trim();
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
            if (item.getBatchCode() == null || item.getBatchCode().trim().isEmpty())
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'必须填写调拨批次");
            }
            InventoryBatch batch = batchService.selectBatchByKey(
                    shopCode, item.getBatchCode().trim(), goods.getGoodsCode(), wareCode, state);
            if (batch == null || !state.equals(batch.getStockState()) || !"active".equals(batch.getStatus()))
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'填写的批次不可用");
            }
            BigDecimal remainQty = batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            if (remainQty.compareTo(item.getQty()) < 0)
            {
                throw new ServiceException("批次'" + batch.getBatchCode() + "'剩余数量不足，当前剩余：" + remainQty);
            }
            item.setBatchCode(item.getBatchCode().trim());
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
                throw new ServiceException("序列号管理商品'" + goods.getGoodsName() + "'的调拨数量必须是整数");
            }
            if (serialNumbers.size() != expectedCount)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'调拨数量为" + expectedCount
                        + "，必须选择相同数量的序列号");
            }
            Set<String> batchCodes = new HashSet<>();
            for (String serialNo : serialNumbers)
            {
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo,
                        goods.getGoodsCode(), wareCode, state);
                if (serial == null || !"instock".equals(serial.getStatus()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'不属于当前商品、仓库或库存状态，或已出库");
                }
                if (serial.getBatchCode() == null || serial.getBatchCode().isEmpty())
                {
                    throw new ServiceException("序列号'" + serialNo + "'未关联批次，不能调拨");
                }
                batchCodes.add(serial.getBatchCode());
            }
            item.setSerialNumbers(serialNumbers);
            item.setBatchCode(batchCodes.size() == 1 ? batchCodes.iterator().next() : null);
        }
    }

    private List<String> normalizeSerialNumbers(List<String> serialNumbers)
    {
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        if (serialNumbers == null) return result;
        for (String serialNo : serialNumbers)
        {
            if (serialNo == null || serialNo.trim().isEmpty()) continue;
            String normalized = serialNo.trim();
            if (!unique.add(normalized)) throw new ServiceException("序列号'" + normalized + "'重复");
            result.add(normalized);
        }
        return result;
    }

    /**
     * 调拨单审核
     */
    @Override
    @Transactional
    public int confirmTransfer(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser)
    {
        // 审核调拨单（会触发库存联动：来源减，目标加）
        return documentService.confirmDocument(shopCode, docCode, tableSuffix, approved, confirmUser);
    }

    /**
     * 生成调拨单号
     */
    private String generateTransferDocCode()
    {
        return documentCodeGenerator.nextTransferCode();
    }
}
