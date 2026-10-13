package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventoryCostAdjustService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.utils.CostAdjustLayer;
import com.ruoyi.system.utils.InventoryCostMath;

/**
 * 成本调整Service业务层处理
 *
 * <p>建单时按当时的库存把每行校验一遍、把「调整会摊到多少件」记进明细数量；
 * 真正改成本在审核过账时（InventoryDocumentPostingService#executeCostAdjust），
 * 那里锁住库存行后按审核那一刻的数据再校验一遍。</p>
 *
 * <p>只调良品·在库的货：成本调整的场景（运费后到、入库价录错）针对的都是正常库存。</p>
 */
@Service
public class InventoryCostAdjustServiceImpl implements IInventoryCostAdjustService
{
    private static final String DOC_TYPE = InventoryDocumentTypes.COST_ADJUST;

    @Autowired
    private IDocumentService documentService;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private IInventorySerialService serialService;

    @Autowired
    private InventoryCostResolver costResolver;

    @Autowired
    private InventoryDocumentCodeGenerator codeGenerator;

    @Override
    public List<DocumentMain> selectCostAdjustList(DocumentMain query)
    {
        query.setDocType(DOC_TYPE);
        return documentService.selectDocumentList(query);
    }

    @Override
    public DocumentMain selectCostAdjustDetail(String shopCode, String docCode, String tableSuffix)
    {
        return requireCostAdjust(shopCode, docCode, tableSuffix);
    }

    @Override
    public Preview preview(String shopCode, String wareCode, String goodsCode, String batchCode, List<String> serialNos)
    {
        InventoryGoods goods = requireGoods(shopCode, goodsCode);
        StockState state = StockState.available();
        InventoryStock stock = stockMapper.selectStockByKey(shopCode, goods.getGoodsCode(), wareCode, state);
        BigDecimal stockQty = stock == null || stock.getPhysicalQty() == null ? BigDecimal.ZERO : stock.getPhysicalQty();

        CostAdjustLayer layer = CostAdjustLayer.of(goods);
        Preview preview = new Preview();
        preview.setLayer(layer.name());
        preview.setGoodsCode(goods.getGoodsCode());
        preview.setGoodsName(goods.getGoodsName());
        preview.setUnit(goods.getUnit());
        if (layer == CostAdjustLayer.BATCH)
        {
            if (isBlank(batchCode))
            {
                return preview;
            }
            InventoryBatch batch = batchService.selectBatchByKey(shopCode, batchCode.trim(), goods.getGoodsCode(),
                    wareCode, state);
            BigDecimal remain = batch == null || batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            if (batch == null || !"active".equals(batch.getStatus()) || remain.signum() <= 0)
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'的批次'" + batchCode
                        + "'在该仓库不存在、已关闭或已经出完");
            }
            BigDecimal unitCost = InventoryCostMath.batchCostPrice(batch);
            preview.setQty(remain);
            preview.setUnitCost(unitCost);
            preview.setTotalCost(unitCost.multiply(remain).setScale(2, RoundingMode.HALF_UP));
        }
        else if (layer == CostAdjustLayer.SERIAL)
        {
            if (serialNos == null || serialNos.isEmpty())
            {
                return preview;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (String serialNo : serialNos)
            {
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo, goods.getGoodsCode(),
                        wareCode, state);
                if (serial == null || !"instock".equals(serial.getStatus()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'不在该仓库的商品'" + goods.getGoodsName() + "'账上");
                }
                total = total.add(costResolver.resolveSerialCostPrice(shopCode, goods, wareCode, state, serial));
            }
            BigDecimal count = BigDecimal.valueOf(serialNos.size());
            preview.setQty(count);
            preview.setUnitCost(total.divide(count, 6, RoundingMode.HALF_UP));
            preview.setTotalCost(total.setScale(2, RoundingMode.HALF_UP));
        }
        else
        {
            BigDecimal total = stock == null || stock.getTotalCostAmount() == null
                    ? BigDecimal.ZERO : stock.getTotalCostAmount();
            preview.setQty(stockQty);
            preview.setUnitCost(stockQty.signum() > 0 ? total.divide(stockQty, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            preview.setTotalCost(total);
        }
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentMain createCostAdjust(String shopCode, CreateCommand command, String username)
    {
        if (command == null || isBlank(command.getWareCode()))
        {
            throw new ServiceException("请选择仓库");
        }
        if (isBlank(command.getRemark()))
        {
            throw new ServiceException("请填写调整原因，改的是账面价值，事后要说得清为什么");
        }
        if (command.getItems() == null || command.getItems().isEmpty())
        {
            throw new ServiceException("至少要有一行调整明细");
        }
        String wareCode = command.getWareCode().trim();
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, wareCode);
        if (warehouse == null)
        {
            throw new ServiceException("仓库'" + wareCode + "'不存在");
        }

        List<DocumentDetail> details = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Line line : command.getItems())
        {
            if (line == null || isBlank(line.getGoodsCode()))
            {
                throw new ServiceException("调整明细的商品不能为空");
            }
            BigDecimal delta = line.getAdjustAmount() == null ? BigDecimal.ZERO
                    : line.getAdjustAmount().setScale(2, RoundingMode.HALF_UP);
            List<String> serials = parseSerials(line.getSerialNos());
            Preview current = preview(shopCode, wareCode, line.getGoodsCode().trim(), line.getBatchCode(), serials);
            String label = "商品'" + current.getGoodsName() + "'";
            if (delta.signum() == 0)
            {
                throw new ServiceException(label + "的调整金额不能为 0");
            }
            CostAdjustLayer layer = CostAdjustLayer.valueOf(current.getLayer());
            if (layer == CostAdjustLayer.BATCH && isBlank(line.getBatchCode()))
            {
                throw new ServiceException(label + "按批次计价，要指定调整哪个批次");
            }
            if (layer == CostAdjustLayer.SERIAL && serials.isEmpty())
            {
                throw new ServiceException(label + "按序列号个别计价，要指定调整哪几个序列号");
            }
            if (current.getQty() == null || current.getQty().signum() <= 0)
            {
                throw new ServiceException(label + "在该仓库没有在库的货，成本没有货可以承接；"
                        + "已经卖出的货差额属于销售成本，不能用成本调整单处理");
            }
            BigDecimal after = current.getTotalCost().add(delta);
            if (after.signum() < 0 || (layer == CostAdjustLayer.BATCH && after.signum() == 0))
            {
                throw new ServiceException(label + "现有成本 " + current.getTotalCost().toPlainString() + "，调减 "
                        + delta.negate().toPlainString() + " 后" + (after.signum() == 0 ? "为 0" : "变成负数"));
            }
            String key = current.getGoodsCode() + "|" + (layer == CostAdjustLayer.BATCH ? line.getBatchCode().trim() : "")
                    + (layer == CostAdjustLayer.SERIAL ? "|" + String.join(",", serials) : "");
            if (layer != CostAdjustLayer.SERIAL && !seen.add(key))
            {
                throw new ServiceException(label + (layer == CostAdjustLayer.BATCH ? "的同一批次" : "")
                        + "出现了两行，请合并成一行");
            }

            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(shopCode);
            detail.setGoodsCode(current.getGoodsCode());
            detail.setGoodsName(current.getGoodsName());
            detail.setUnit(current.getUnit());
            detail.setWareCode(wareCode);
            detail.setStockState(StockState.available());
            // 数量记「这笔差额摊到了多少件货上」，单价记平均每件调了多少，金额就是调整金额
            detail.setQty(current.getQty());
            detail.setCostPrice(delta.divide(current.getQty(), 6, RoundingMode.HALF_UP));
            detail.setCostAmount(delta);
            detail.setBatchCode(layer == CostAdjustLayer.BATCH ? line.getBatchCode().trim() : null);
            if (layer == CostAdjustLayer.SERIAL)
            {
                detail.setSerialNumbers(serials);
            }
            detail.setRemark("");
            details.add(detail);
            totalQty = totalQty.add(current.getQty());
            totalAmount = totalAmount.add(delta);
        }

        DocumentMain document = new DocumentMain();
        document.setShopCode(shopCode);
        document.setDocType(DOC_TYPE);
        document.setDocTypeName("成本调整");
        document.setDocCode(codeGenerator.nextCostAdjustCode());
        document.setDocDate(new Date());
        document.setFromWareCode(wareCode);
        document.setToWareCode(wareCode);
        document.setFromState(StockState.available());
        document.setToState(StockState.available());
        document.setCreateUser(username);
        document.setHandler(username);
        document.setRemark(command.getRemark().trim());
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount);
        document.setDetails(details);
        documentService.insertDocument(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String shopCode, String docCode, String tableSuffix, String username)
    {
        DocumentMain document = requireCostAdjust(shopCode, docCode, tableSuffix);
        documentService.confirmDocument(shopCode, document.getDocCode(), document.getTableSuffix(), true, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String shopCode, String docCode, String tableSuffix, String username)
    {
        DocumentMain document = requireCostAdjust(shopCode, docCode, tableSuffix);
        documentService.confirmDocument(shopCode, document.getDocCode(), document.getTableSuffix(), false, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String shopCode, String docCode, String tableSuffix)
    {
        DocumentMain document = requireCostAdjust(shopCode, docCode, tableSuffix);
        documentService.deleteDocument(shopCode, document.getDocCode(), document.getTableSuffix());
    }

    private DocumentMain requireCostAdjust(String shopCode, String docCode, String tableSuffix)
    {
        DocumentMain document = isBlank(docCode) ? null
                : documentService.selectDocumentByCode(shopCode, docCode.trim(), tableSuffix);
        if (document == null || !DOC_TYPE.equals(document.getDocType()))
        {
            throw new ServiceException("成本调整单'" + docCode + "'不存在");
        }
        return document;
    }

    private InventoryGoods requireGoods(String shopCode, String goodsCode)
    {
        InventoryGoods goods = isBlank(goodsCode) ? null : goodsMapper.selectGoodsByCode(shopCode, goodsCode.trim());
        if (goods == null)
        {
            throw new ServiceException("商品'" + goodsCode + "'不存在");
        }
        return goods;
    }

    /** 序列号可以用逗号（中英文）、空格或换行分隔 */
    static List<String> parseSerials(String raw)
    {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty())
        {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split("[,，;；\\s]+"))
        {
            String serial = part.trim();
            if (serial.isEmpty())
            {
                continue;
            }
            if (!seen.add(serial))
            {
                throw new ServiceException("序列号'" + serial + "'重复填写");
            }
            result.add(serial);
        }
        return result;
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
