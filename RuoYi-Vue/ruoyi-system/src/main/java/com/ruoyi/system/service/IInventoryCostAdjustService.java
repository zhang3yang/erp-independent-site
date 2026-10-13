package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.enums.PriceScope;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 成本调整Service接口
 *
 * <p>成本调整单就是一张 CBTZ 类型的库存单据，建单、审核走通用单据服务，
 * 这里只负责按成本层校验和组装。</p>
 */
public interface IInventoryCostAdjustService
{
    public List<DocumentMain> selectCostAdjustList(DocumentMain query);

    public DocumentMain selectCostAdjustDetail(String shopCode, String docCode, String tableSuffix);

    /** 看一眼某个商品（批次 / 序列号）现在的数量和成本，前端据此把「调整后单价」换算成调整金额 */
    public Preview preview(String shopCode, String wareCode, String goodsCode, String batchCode, List<String> serialNos);

    public DocumentMain createCostAdjust(String shopCode, CreateCommand command, String username);

    public void approve(String shopCode, String docCode, String tableSuffix, String username);

    public void reject(String shopCode, String docCode, String tableSuffix, String username);

    public void delete(String shopCode, String docCode, String tableSuffix);

    class Preview
    {
        /** STOCK / BATCH / SERIAL，见 CostAdjustLayer */
        private String layer;

        private String goodsCode;

        private String goodsName;

        private String unit;

        /** 调整会摊到的数量：移动平均是仓库在库数，批次是批次剩余数，序列号是个数 */
        private BigDecimal qty;

        /** 这部分货现在的单位成本 */
        @PriceField(PriceScope.COST)
        private BigDecimal unitCost;

        /** 这部分货现在的成本合计 */
        @PriceField(PriceScope.COST)
        private BigDecimal totalCost;

        public String getLayer()
        {
            return layer;
        }

        public void setLayer(String layer)
        {
            this.layer = layer;
        }

        public String getGoodsCode()
        {
            return goodsCode;
        }

        public void setGoodsCode(String goodsCode)
        {
            this.goodsCode = goodsCode;
        }

        public String getGoodsName()
        {
            return goodsName;
        }

        public void setGoodsName(String goodsName)
        {
            this.goodsName = goodsName;
        }

        public String getUnit()
        {
            return unit;
        }

        public void setUnit(String unit)
        {
            this.unit = unit;
        }

        public BigDecimal getQty()
        {
            return qty;
        }

        public void setQty(BigDecimal qty)
        {
            this.qty = qty;
        }

        public BigDecimal getUnitCost()
        {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost)
        {
            this.unitCost = unitCost;
        }

        public BigDecimal getTotalCost()
        {
            return totalCost;
        }

        public void setTotalCost(BigDecimal totalCost)
        {
            this.totalCost = totalCost;
        }
    }

    class CreateCommand
    {
        private String wareCode;

        /** 调整原因，必填：这张单改的是账面价值，事后要能说清楚为什么 */
        private String remark;

        private List<Line> items;

        public String getWareCode()
        {
            return wareCode;
        }

        public void setWareCode(String wareCode)
        {
            this.wareCode = wareCode;
        }

        public String getRemark()
        {
            return remark;
        }

        public void setRemark(String remark)
        {
            this.remark = remark;
        }

        public List<Line> getItems()
        {
            return items;
        }

        public void setItems(List<Line> items)
        {
            this.items = items;
        }
    }

    class Line
    {
        private String goodsCode;

        private String batchCode;

        /** 序列号，逗号或换行分隔 */
        private String serialNos;

        /** 调整金额，正数调增、负数调减 */
        private BigDecimal adjustAmount;

        public String getGoodsCode()
        {
            return goodsCode;
        }

        public void setGoodsCode(String goodsCode)
        {
            this.goodsCode = goodsCode;
        }

        public String getBatchCode()
        {
            return batchCode;
        }

        public void setBatchCode(String batchCode)
        {
            this.batchCode = batchCode;
        }

        public String getSerialNos()
        {
            return serialNos;
        }

        public void setSerialNos(String serialNos)
        {
            this.serialNos = serialNos;
        }

        public BigDecimal getAdjustAmount()
        {
            return adjustAmount;
        }

        public void setAdjustAmount(BigDecimal adjustAmount)
        {
            this.adjustAmount = adjustAmount;
        }
    }
}
