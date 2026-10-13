package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 出库Service接口
 */
public interface IOutboundService
{
    /**
     * 创建出库单
     *
     * @param shopCode 商家编码
     * @param docType 出库类型（CK-销售出库/MO-领料出库/RO-采购退货出库/OO-其他出库）
     * @param fromWareCode 来源仓库代码
     * @param fromStatusCode 来源库存状态代码
     * @param partnerCode 往来单位代码（可选）
     * @param partnerName 往来单位名称（可选）
     * @param sourceDocCode 来源单据代码（可选）
     * @param sourceDocType 来源单据类型（可选）
     * @param outboundItems 出库明细
     * @param remark 备注
     * @param createUser 创建人
     * @return 出库单
     */
    public DocumentMain createOutbound(String shopCode, String docType, String fromWareCode, StockState fromState,
            String partnerCode, String partnerName, String sourceDocCode, String sourceDocType,
            List<OutboundItem> outboundItems, String remark, String createUser);

    /**
     * 出库单审核
     *
     * @param shopCode 商家编码
     * @param docCode 出库单号
     * @param tableSuffix 月表后缀
     * @param approved 是否通过
     * @param confirmUser 审核人
     * @return 结果
     */
    public int confirmOutbound(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser);

    /**
     * 出库明细项
     */
    public static class OutboundItem
    {
        /** 商品代码 */
        private String goodsCode;
        /** 单据行商品名称（允许覆盖商品主档快照） */
        private String goodsName;
        /** 单据行规格（允许覆盖商品主档快照） */
        private String spec;
        /** 单据行单位（允许覆盖商品主档快照） */
        private String unit;
        /** 出库数量 */
        private BigDecimal qty;
        /** 批次号（可选） */
        private String batchCode;
        /** 序列号列表（可选，用于序列号管理） */
        private List<String> serialNumbers = new ArrayList<>();
        /** 成本单价（可选，系统自动计算） */
        private BigDecimal costPrice;
        /** 销售单价（可选，销售出库留空时带出本行的入库成本） */
        private BigDecimal salePrice;
        /**
         * 来源销售订单明细代码（可选，销售发货时必填）。
         *
         * <p>审核时挑批次要靠它认出哪些定制批次是自己的：为别的订单专门生产的批次
         * 一律跳过，自己的排在最前先发掉。非订单出库（调拨、盘亏、领料）留空，
         * 那种情况下任何人的定制批次都不能动。</p>
         */
        private String orderDetailCode;

        public String getGoodsCode() { return goodsCode; }
        public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getSpec() { return spec; }
        public void setSpec(String spec) { this.spec = spec; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }

        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }

        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

        public BigDecimal getSalePrice() { return salePrice; }
        public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

        public String getOrderDetailCode() { return orderDetailCode; }
        public void setOrderDetailCode(String orderDetailCode) { this.orderDetailCode = orderDetailCode; }
    }
}
