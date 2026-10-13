package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 调拨Service接口
 */
public interface ITransferService
{
    /**
     * 创建调拨单
     *
     * @param shopCode 商家编码
     * @param fromWareCode 来源仓库代码
     * @param fromStatusCode 来源库存状态代码
     * @param toWareCode 目标仓库代码
     * @param toState 目标三维库存状态
     * @param transferItems 调拨明细
     * @param remark 备注
     * @param createUser 创建人
     * @return 调拨单
     */
    public DocumentMain createTransfer(String shopCode, String fromWareCode, StockState fromState,
            String toWareCode, StockState toState, List<TransferItem> transferItems, String remark, String createUser);

    /**
     * 调拨单审核
     *
     * @param shopCode 商家编码
     * @param docCode 调拨单号
     * @param tableSuffix 月表后缀
     * @param approved 是否通过
     * @param confirmUser 审核人
     * @return 结果
     */
    public int confirmTransfer(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser);

    /**
     * 调拨明细项
     */
    public static class TransferItem
    {
        /** 商品代码 */
        private String goodsCode;
        /** 调拨数量 */
        private BigDecimal qty;
        /** 批次号（可选） */
        private String batchCode;
        /** 序列号列表（可选，用于序列号管理） */
        private List<String> serialNumbers = new ArrayList<>();
        /** 成本单价（可选，使用移动平均成本） */
        private BigDecimal costPrice;

        public String getGoodsCode() { return goodsCode; }
        public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }

        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }

        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    }
}
