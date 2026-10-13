package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.system.domain.InventoryStocktake;

/**
 * 库存盘点Service接口
 */
public interface IInventoryStocktakeService
{
    public List<InventoryStocktake> selectStocktakeList(InventoryStocktake query);

    /** 单头 + 明细（明细带当前库存和被占用量） */
    public InventoryStocktake selectStocktakeDetail(String shopCode, String stocktakeCode);

    /** 新建盘点单并快照账面数量，单上的商品从这一刻起冻结 */
    public InventoryStocktake createStocktake(String shopCode, CreateCommand command, String username);

    /** 录入实盘数，只在「盘点中」可以，可以反复保存 */
    public void saveCounts(String shopCode, String stocktakeCode, List<CountLine> lines);

    /** 往盘点单上加商品，账面数量按当前库存快照 */
    public void addItems(String shopCode, String stocktakeCode, List<String> goodsCodes, Integer qualityFlag);

    public void removeItem(String shopCode, String stocktakeCode, Long itemId);

    public void submit(String shopCode, String stocktakeCode, String username);

    public void reject(String shopCode, String stocktakeCode, String reason, String username);

    public void cancel(String shopCode, String stocktakeCode, String username);

    /** 审核通过：差异生成盘盈入库 / 盘亏出库并当场过账 */
    public InventoryStocktake approve(String shopCode, String stocktakeCode, String username);

    class CreateCommand
    {
        private String wareCode;

        /** all 全仓 / goods 指定商品 */
        private String scope;

        /** scope=goods 时要盘的商品 */
        private List<String> goodsCodes;

        /** 是否把在库的不良品也一起盘 */
        private Boolean includeDefective;

        private String remark;

        public String getWareCode()
        {
            return wareCode;
        }

        public void setWareCode(String wareCode)
        {
            this.wareCode = wareCode;
        }

        public String getScope()
        {
            return scope;
        }

        public void setScope(String scope)
        {
            this.scope = scope;
        }

        public List<String> getGoodsCodes()
        {
            return goodsCodes;
        }

        public void setGoodsCodes(List<String> goodsCodes)
        {
            this.goodsCodes = goodsCodes;
        }

        public Boolean getIncludeDefective()
        {
            return includeDefective;
        }

        public void setIncludeDefective(Boolean includeDefective)
        {
            this.includeDefective = includeDefective;
        }

        public String getRemark()
        {
            return remark;
        }

        public void setRemark(String remark)
        {
            this.remark = remark;
        }
    }

    class CountLine
    {
        private Long id;

        private BigDecimal countedQty;

        private BigDecimal costPrice;

        private String batchCode;

        private String serialNos;

        private String remark;

        public Long getId()
        {
            return id;
        }

        public void setId(Long id)
        {
            this.id = id;
        }

        public BigDecimal getCountedQty()
        {
            return countedQty;
        }

        public void setCountedQty(BigDecimal countedQty)
        {
            this.countedQty = countedQty;
        }

        public BigDecimal getCostPrice()
        {
            return costPrice;
        }

        public void setCostPrice(BigDecimal costPrice)
        {
            this.costPrice = costPrice;
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

        public String getRemark()
        {
            return remark;
        }

        public void setRemark(String remark)
        {
            this.remark = remark;
        }
    }
}
