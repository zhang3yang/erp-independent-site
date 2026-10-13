package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.vo.SalesReturnableItem;

/**
 * 销售退货。客户把已发出的货退回来，走 XSTHRK 入库单把货和成本还回库存池。
 *
 * <p>客户退回来（I 入库）与采购侧退给供应商（O 出库）方向相反。不新建业务表，
 * 退货事实完全落在库存单据上，按来源单号关联回订单。</p>
 *
 * <p><b>入库成本取原出库的实际成本，不是订单售价。</b>出库时 FIFO/加权算出来的
 * costamount 除以数量就是这批货当初出去的单位成本，原样还回来库存总成本才守恒；
 * 用售价入库会把毛利凭空计进库存。</p>
 *
 * <p><b>退货不回冲订单的已发数量。</b>与采购退货保持一致：shippedqty 记录的是
 * 「实际发出去过多少」，退货是发货之后的独立事实，体现在退货单与库存流水上。
 * 因此订单状态不会因退货从「已完成」退回「发货中」，也不会因此重新变得可以取消。</p>
 *
 * <p><b>建单与入库分两步。</b>与销售补发「先建单、再整单发货」对称：{@link #createReturn}
 * 只落一张待提交（待审核）的退货单，货还没回库；{@link #submitReturn} 才审核过账，
 * 真正加库存、处理批次序列号并写流水。可退量从建单那一刻就把待提交单算进已退，
 * 所以草稿期间不会被别的单重复退掉；不想退了用 {@link #deleteReturn} 删草稿把量放回去。</p>
 */
public interface ISalesReturnService
{
    /** 销售退货入库单类型 */
    String RETURN_DOC_TYPE = com.ruoyi.system.constant.InventoryDocumentTypes.SALES_RETURN_INBOUND;

    /**
     * 查询某销售订单的可退商品，按「商品 + 发货仓库」聚合。
     *
     * <p>可退数量 = 已发数量 − 已退数量（含待审核退货单）。为 0 的行不返回。
     * 已发数量只算已审核的销售出库单——待审核的出库单还没扣库存，退它没有意义。</p>
     */
    List<SalesReturnableItem> selectReturnableItems(String shopCode, String orderCode);

    /**
     * 新建销售退货单，只建单不入库，落成待提交（待审核）状态。
     *
     * <p>可退量、仓库唯一性、批次序列号都在这一步校验完，成本也按原出库实际成本
     * 定死在明细上；库存要等 {@link #submitReturn} 才动。</p>
     *
     * @param command 退货命令，含订单号、备注和明细
     * @param username 操作人
     * @return 生成的退货入库单（待提交）
     */
    DocumentMain createReturn(String shopCode, ReturnCommand command, String username);

    /**
     * 提交退货：审核退货单，货真正入库。
     *
     * <p>由库存过账服务增加库存、处理批次与序列号并写入流水，只能提交一次。</p>
     *
     * @param docCode 退货单号
     * @param tableSuffix 月表后缀 yyyyMM
     * @param username 操作人
     * @return 提交后的退货入库单
     */
    DocumentMain submitReturn(String shopCode, String docCode, String tableSuffix, String username);

    /**
     * 删除还没提交的退货单，把占住的可退数量放回去。已提交入库的单不能删。
     *
     * @param docCode 退货单号
     * @param tableSuffix 月表后缀 yyyyMM
     */
    void deleteReturn(String shopCode, String docCode, String tableSuffix);

    /**
     * 查询退货记录。
     *
     * @param tableSuffix 月表后缀 yyyyMM
     * @param docCode 退货单号，支持模糊，为空表示不限
     * @param orderCode 销售订单号，为空表示不限
     * @param confirmState 审核状态（待审核/已审核），为空表示不限
     */
    List<DocumentMain> selectReturnList(String shopCode, String docCode, String orderCode,
            String confirmState, String tableSuffix);

    /**
     * 全部月份里还没提交的退货单，按月表倒序。
     *
     * <p>退货记录是按月表查的，月份一翻页，上个月留下的待提交单就从视野里消失了，
     * 而它一直占着可退数量。这个查询不分月，专门用来把这些单捞出来提醒人处理。
     * 待提交单数量本来就少，不分页。</p>
     */
    List<DocumentMain> selectPendingReturns(String shopCode);

    /** 查询退货单详情，含明细行 */
    DocumentMain selectReturnDetail(String shopCode, String docCode, String tableSuffix);

    /** 退货命令 */
    class ReturnCommand
    {
        private String orderCode;
        private String remark;
        private List<ReturnItem> items = new ArrayList<>();

        public String getOrderCode() { return orderCode; }
        public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<ReturnItem> getItems() { return items; }
        public void setItems(List<ReturnItem> items) { this.items = items; }
    }

    /** 退货明细 */
    class ReturnItem
    {
        private String goodsCode;
        private String warehouseCode;
        private BigDecimal qty;
        private String batchCode;
        private List<String> serialNumbers = new ArrayList<>();
        private String remark;

        public String getGoodsCode() { return goodsCode; }
        public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }
        public String getWarehouseCode() { return warehouseCode; }
        public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
        public BigDecimal getQty() { return qty; }
        public void setQty(BigDecimal qty) { this.qty = qty; }
        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
