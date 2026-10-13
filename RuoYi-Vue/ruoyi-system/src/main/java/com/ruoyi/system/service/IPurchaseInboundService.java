package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 采购入库Service接口
 */
public interface IPurchaseInboundService
{
    /**
     * 直接创建采购入库单。
     *
     * @param shopCode 商家编码
     * @param inbound 入库单及明细
     * @param createUser 创建人
     * @return 入库单
     */
    public DocumentMain createDirectInbound(String shopCode, DocumentMain inbound, String createUser);

    /**
     * 从采购订单生成入库单
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param inboundItems 入库明细（商品代码、数量、仓库代码）
     * @return 入库单
     */
    public DocumentMain createInboundFromPurchase(String shopCode, String purchaseCode,
            List<InboundItem> inboundItems, String createUser);

    /**
     * 由采购发货单创建采购入库单。入库员在库存模块发起。
     *
     * <p>只处理 {@code command.items} 里列出的行，没列出的行保持「未处理」，以后再入
     * （决议 A2-2 支持一张发货单分多次入库）。</p>
     *
     * <p>实际到货数量不设上限（决议 A1b）：既不受发货单预计数量约束，
     * 也不受采购合同数量约束，入库员点到多少就入多少。</p>
     *
     * @return 生成的采购入库单，状态为待审核
     */
    public DocumentMain createInboundFromShipment(String shopCode, ShipmentInboundCommand command, String createUser);

    /**
     * 由发货单入库的指令。
     */
    public static class ShipmentInboundCommand
    {
        /** 发货单号 */
        private String receiveCode;
        /** 发货单所在月表；不传则由服务端跨月表查找 */
        private String tableSuffix;
        /** 签收人 */
        private String receiveUser;
        /** 签收时间 */
        private java.util.Date receiveTime;
        /** 入库仓库 */
        private String toWareCode;
        /** 本次实付运费 */
        private BigDecimal freightAmount;
        private String remark;
        /** 本次要入库的行 */
        private List<ShipmentInboundItem> items = new ArrayList<>();

        public String getReceiveCode() { return receiveCode; }
        public void setReceiveCode(String receiveCode) { this.receiveCode = receiveCode; }
        public String getTableSuffix() { return tableSuffix; }
        public void setTableSuffix(String tableSuffix) { this.tableSuffix = tableSuffix; }
        public String getReceiveUser() { return receiveUser; }
        public void setReceiveUser(String receiveUser) { this.receiveUser = receiveUser; }
        public java.util.Date getReceiveTime() { return receiveTime; }
        public void setReceiveTime(java.util.Date receiveTime) { this.receiveTime = receiveTime; }
        public String getToWareCode() { return toWareCode; }
        public void setToWareCode(String toWareCode) { this.toWareCode = toWareCode; }
        public BigDecimal getFreightAmount() { return freightAmount; }
        public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<ShipmentInboundItem> getItems() { return items; }
        public void setItems(List<ShipmentInboundItem> items) { this.items = items; }
    }

    /**
     * 由发货单入库的明细行。数量以入库员填的实际到货数量为准。
     */
    public static class ShipmentInboundItem
    {
        /** 发货明细号 */
        private String receiveDetailCode;
        /** 实际到货数量 */
        private BigDecimal actualQty;
        private String batchCode;
        private List<String> serialNumbers = new ArrayList<>();

        public String getReceiveDetailCode() { return receiveDetailCode; }
        public void setReceiveDetailCode(String receiveDetailCode) { this.receiveDetailCode = receiveDetailCode; }
        public BigDecimal getActualQty() { return actualQty; }
        public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }
        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
    }

    /**
     * 采购入库单审核
     *
     * @param shopCode 商家编码
     * @param docCode 入库单号
     * @param tableSuffix 月表后缀
     * @param approved 是否通过
     * @param confirmUser 审核人
     * @return 结果
     */
    public int confirmPurchaseInbound(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser);

    /**
     * 入库明细项
     */
    public static class InboundItem
    {
        /** 商品代码 */
        private String goodsCode;
        private String purchaseDetailCode;
        /** 入库数量 */
        private BigDecimal qty;
        /** 入库仓库代码 */
        private String wareCode;
        /** 批次号（可选） */
        private String batchCode;
        /** 兼容字段；采购订单入库时后端始终使用采购订单单价 */
        private BigDecimal costPrice;
        /** 序列号列表（可选，用于序列号管理） */
        private List<String> serialNumbers = new ArrayList<>();

        public String getGoodsCode() { return goodsCode; }
        public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
        public String getPurchaseDetailCode() { return purchaseDetailCode; }
        public void setPurchaseDetailCode(String purchaseDetailCode) { this.purchaseDetailCode = purchaseDetailCode; }

        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }

        public String getWareCode() { return wareCode; }
        public void setWareCode(String wareCode) { this.wareCode = wareCode; }

        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
    }
}
