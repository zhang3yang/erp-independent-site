package com.ruoyi.system.service.impl;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.system.constant.InventoryDocumentTypes;

/**
 * 库存业务单据编号生成器。
 * 编号规则：业务前缀 + Redis 小时级 Base10 数字编码。
 */
@Component
public class InventoryDocumentCodeGenerator
{
    private static final String INBOUND_PREFIX = "RK";
    private static final String OUTBOUND_PREFIX = "CK";
    private static final String TRANSFER_PREFIX = "DB";
    private static final String PURCHASE_ORDER_PREFIX = "CG";
    private static final String PURCHASE_RECEIVE_PREFIX = "DH";
    /** 三段式改造前的领料出库、生产退料入库前缀，只用于识别历史单据 */
    private static final String MATERIAL_OUTBOUND_PREFIX = "LLCK";
    private static final String MATERIAL_RETURN_PREFIX = "SCTLRK";
    private static final String PRODUCT_INBOUND_PREFIX = "SCCPRK";
    private static final String PRODUCTION_PICK_PREFIX = "SCLL";
    private static final String PRODUCTION_CONSUME_PREFIX = "SCXH";
    private static final String PRODUCTION_RETURN_PREFIX = "SCTL";
    private static final String PRODUCTION_ORDER_PREFIX = "SC";
    private static final String PRODUCTION_DETAIL_PREFIX = "SCMX";
    private static final String PRODUCTION_MATERIAL_PREFIX = "SCWL";
    /** 产品配方（BOM）主数据，不是单据，但同样需要全局唯一的业务主键 */
    private static final String BOM_PREFIX = "SCPF";
    private static final String BOM_DETAIL_PREFIX = "SCPFMX";
    private static final String LOAN_PREFIX = "JC";
    private static final String RETURN_PREFIX = "GH";
    private static final String DAMAGE_PREFIX = "SHDJ";
    private static final String DAMAGE_OUTBOUND_PREFIX = "SHCK";
    private static final String REPAIR_OUT_PREFIX = "SX";
    private static final String REPAIR_IN_PREFIX = "WXGH";
    private static final String LEDGER_PREFIX = "KCLSH";
    private static final String STOCKTAKE_PREFIX = "PD";
    private static final String STOCKTAKE_GAIN_PREFIX = "PYRK";
    private static final String STOCKTAKE_LOSS_PREFIX = "PKCK";
    private static final String COST_ADJUST_PREFIX = "CBTZ";
    /**
     * 销售订单号：<b>无前缀，纯 Base10 数字</b>。
     *
     * <p>数字部分本身就是全系统唯一的——{@code geHourKey} 里的序列号是全局共享的
     * （一个 Redis key，所有单据类型共用一个计数器），前缀只是最后拼上去的标签，
     * 不参与生成。所以去掉前缀不会跟别的单据撞号。</p>
     *
     * <p>V2.0 §5.4 流程2 里把回款的 {@code bizcode} 写成「SOxxx」，那只是举例。
     * 财务绑定订单走的是查表校验（{@code findOrderAcrossMonths}），不看前缀，
     * 而且流水上另有「业务类型」列区分采购单和销售订单，不会混。</p>
     *
     * <p>订单<b>明细</b>号仍保留 SOMX 前缀：明细号只在系统内部关联用，
     * 带前缀便于排查时一眼认出是哪种记录。</p>
     */
    private static final String SALES_ORDER_PREFIX = "";
    private static final String SALES_ORDER_DETAIL_PREFIX = "SOMX";
    /**
     * 销售发货批次。前缀是 XSFH 不是 FH——「FH」已经被
     * {@link com.ruoyi.system.constant.InventoryDocumentTypes#PURCHASE_SHIPMENT_SOURCE}
     * 占作采购发货单的来源类型，复用会让采购入库的审核分支认错单据。
     */
    private static final String SHIP_BATCH_PREFIX = "XSFH";
    private static final String SHIP_BATCH_DETAIL_PREFIX = "XSFHMX";
    private static final String SALES_RESHIP_PREFIX = "XSBF";
    private static final String SALES_RESHIP_DETAIL_PREFIX = "XSBFMX";
    private static final String RESERVATION_PREFIX = "YZ";
    /** 销售退货入库。与采购退货的 CGTHCK 对称，各走各的号段，单号上一眼分得出方向 */
    private static final String SALES_RETURN_PREFIX = "XSTHRK";
    /**
     * 销售客户。不是单据，但同样需要全局唯一的字符串业务主键，和 BOM 的 SCPF 同一个道理。
     *
     * <p>KH 与已有前缀（CG/DH/SC/JC/GH/SX/XSFH/SHDJ…）都不冲突。</p>
     */
    private static final String BUYER_PREFIX = "KH";
    /** 二级客户。照 SOMX / XSFHMX / SCPFMX 的主从命名，主前缀 + MX */
    private static final String BUYER2_PREFIX = "KHMX";
    /**
     * 独立站站点。同样不是单据，只是要一个全局唯一的字符串业务主键。
     *
     * <p>DLZ 取「独立站」三个字的首字母，与已有前缀（DB 调拨、DH 到货、DW 往来单位）都不撞。</p>
     */
    private static final String STATION_SITE_PREFIX = "DLZ";
    /** 独立站客户询盘。XP 与已有前缀都不冲突 */
    private static final String STATION_INQUIRY_PREFIX = "XP";
    /** 独立站图片资源 */
    private static final String STATION_IMAGE_PREFIX = "SIMG";
    /** 独立站视频资源 */
    private static final String STATION_VIDEO_PREFIX = "SVID";

    @Autowired
    private RedisService redisService;

    public String nextInboundCode()
    {
        return nextCode(INBOUND_PREFIX, "采购入库");
    }

    public String nextOutboundCode()
    {
        return nextCode(OUTBOUND_PREFIX, "销售出库");
    }

    public String nextTransferCode()
    {
        return nextCode(TRANSFER_PREFIX, "仓库调拨");
    }

    public String nextPurchaseOrderCode()
    {
        return nextCode(PURCHASE_ORDER_PREFIX, "采购订单");
    }

    public String nextPurchaseOrderDetailCode()
    {
        return nextCode(PURCHASE_ORDER_PREFIX, "采购订单明细");
    }

    public String nextPurchaseReceiveCode()
    {
        return nextCode(PURCHASE_RECEIVE_PREFIX, "采购到货");
    }

    public String nextPurchaseReceiveDetailCode()
    {
        return nextCode(PURCHASE_RECEIVE_PREFIX, "采购到货明细");
    }

    public String nextMaterialOutboundCode()
    {
        return nextCode(MATERIAL_OUTBOUND_PREFIX, "领料出库");
    }

    public String nextProductInboundCode()
    {
        return nextCode(PRODUCT_INBOUND_PREFIX, "生产成品入库");
    }

    public String nextMaterialReturnCode()
    {
        return nextCode(MATERIAL_RETURN_PREFIX, "生产退料入库");
    }

    public String nextProductionPickCode()
    {
        return nextCode(PRODUCTION_PICK_PREFIX, "生产领料");
    }

    public String nextProductionConsumeCode()
    {
        return nextCode(PRODUCTION_CONSUME_PREFIX, "生产消耗");
    }

    public String nextProductionReturnCode()
    {
        return nextCode(PRODUCTION_RETURN_PREFIX, "生产退料");
    }

    public String nextProductionOrderCode()
    {
        return nextCode(PRODUCTION_ORDER_PREFIX, "生产工单");
    }

    public String nextProductionDetailCode()
    {
        return nextCode(PRODUCTION_DETAIL_PREFIX, "生产产出明细");
    }

    public String nextProductionMaterialCode()
    {
        return nextCode(PRODUCTION_MATERIAL_PREFIX, "生产物料明细");
    }

    public String nextBomCode()
    {
        return nextCode(BOM_PREFIX, "产品配方");
    }

    public String nextBomDetailCode()
    {
        return nextCode(BOM_DETAIL_PREFIX, "产品配方明细");
    }

    public String nextLoanCode()
    {
        return nextCode(LOAN_PREFIX, "借出");
    }

    public String nextReturnCode()
    {
        return nextCode(RETURN_PREFIX, "归还");
    }

    public String nextDamageCode()
    {
        return nextCode(DAMAGE_PREFIX, "损坏登记");
    }

    public String nextDamageOutboundCode()
    {
        return nextCode(DAMAGE_OUTBOUND_PREFIX, "损坏出库");
    }

    public String nextRepairOutCode()
    {
        return nextCode(REPAIR_OUT_PREFIX, "送修");
    }

    public String nextRepairInCode()
    {
        return nextCode(REPAIR_IN_PREFIX, "维修归还");
    }

    public String nextStocktakeCode()
    {
        return nextCode(STOCKTAKE_PREFIX, "库存盘点");
    }

    public String nextStocktakeGainCode()
    {
        return nextCode(STOCKTAKE_GAIN_PREFIX, "盘盈入库");
    }

    public String nextStocktakeLossCode()
    {
        return nextCode(STOCKTAKE_LOSS_PREFIX, "盘亏出库");
    }

    public String nextCostAdjustCode()
    {
        return nextCode(COST_ADJUST_PREFIX, "成本调整");
    }

    public String nextLedgerCode()
    {
        return nextCode(LEDGER_PREFIX, "库存流水");
    }

    public String nextProductionLogCode()
    {
        return nextCode("SCSJ", "生产执行日志");
    }

    /** 资金流水号，前缀 ZJ */
    public String nextTransactionCode()
    {
        return nextCode("ZJ", "资金流水");
    }

    public String nextSalesOrderCode()
    {
        return nextCode(SALES_ORDER_PREFIX, "销售订单");
    }

    public String nextSalesOrderDetailCode()
    {
        return nextCode(SALES_ORDER_DETAIL_PREFIX, "销售订单明细");
    }

    public String nextBuyerCode()
    {
        return nextCode(BUYER_PREFIX, "客户");
    }

    public String nextBuyer2Code()
    {
        return nextCode(BUYER2_PREFIX, "二级客户");
    }

    public String nextStationSiteCode()
    {
        return nextCode(STATION_SITE_PREFIX, "独立站站点");
    }

    public String nextInquiryCode()
    {
        return nextCode(STATION_INQUIRY_PREFIX, "客户询盘");
    }

    public String nextSiteImageCode()
    {
        return nextCode(STATION_IMAGE_PREFIX, "独立站图片");
    }

    public String nextSiteVideoCode()
    {
        return nextCode(STATION_VIDEO_PREFIX, "独立站视频");
    }

    public String nextShipBatchCode()
    {
        return nextCode(SHIP_BATCH_PREFIX, "发货批次");
    }

    public String nextShipBatchDetailCode()
    {
        return nextCode(SHIP_BATCH_DETAIL_PREFIX, "发货批次明细");
    }

    public String nextSalesReshipCode()
    {
        return nextCode(SALES_RESHIP_PREFIX, "销售补发");
    }

    public String nextSalesReshipDetailCode()
    {
        return nextCode(SALES_RESHIP_DETAIL_PREFIX, "销售补发明细");
    }

    public String nextReservationCode()
    {
        return nextCode(RESERVATION_PREFIX, "订单预占");
    }

    public String nextSalesReturnCode()
    {
        return nextCode(SALES_RETURN_PREFIX, "销售退货入库");
    }

    /**
     * 单据明细号使用所属业务单据相同的前缀 + 小时级 Base10 数字编码。
     */
    public String nextDetailCode(String docType)
    {
        if (InventoryDocumentTypes.PURCHASE_INBOUND.equals(docType))
        {
            return nextCode(INBOUND_PREFIX, "采购入库明细");
        }
        // 采购退货出库、损坏出库和其他出库都复用销售出库的明细号段
        if (InventoryDocumentTypes.SALES_OUTBOUND.equals(docType)
                || InventoryDocumentTypes.PURCHASE_RETURN_OUTBOUND.equals(docType)
                || InventoryDocumentTypes.DAMAGE_OUTBOUND.equals(docType) || "OO".equals(docType))
        {
            return nextCode(OUTBOUND_PREFIX, "销售出库明细");
        }
        if (InventoryDocumentTypes.MATERIAL_OUTBOUND.equals(docType))
        {
            return nextCode(MATERIAL_OUTBOUND_PREFIX, "领料出库明细");
        }
        if (InventoryDocumentTypes.PRODUCT_INBOUND.equals(docType))
        {
            return nextCode(PRODUCT_INBOUND_PREFIX, "生产成品入库明细");
        }
        if (InventoryDocumentTypes.SALES_RETURN_INBOUND.equals(docType))
        {
            return nextCode(SALES_RETURN_PREFIX, "销售退货入库明细");
        }
        if (InventoryDocumentTypes.MATERIAL_RETURN.equals(docType))
        {
            return nextCode(MATERIAL_RETURN_PREFIX, "生产退料入库明细");
        }
        if (InventoryDocumentTypes.PRODUCTION_PICK.equals(docType))
        {
            return nextCode(PRODUCTION_PICK_PREFIX, "生产领料明细");
        }
        if (InventoryDocumentTypes.PRODUCTION_CONSUME.equals(docType))
        {
            return nextCode(PRODUCTION_CONSUME_PREFIX, "生产消耗明细");
        }
        if (InventoryDocumentTypes.PRODUCTION_RETURN.equals(docType))
        {
            return nextCode(PRODUCTION_RETURN_PREFIX, "生产退料明细");
        }
        if (InventoryDocumentTypes.WAREHOUSE_TRANSFER.equals(docType))
        {
            return nextCode(TRANSFER_PREFIX, "仓库调拨明细");
        }
        if (InventoryDocumentTypes.LOAN.equals(docType))
        {
            return nextCode(LOAN_PREFIX, "借出明细");
        }
        if (InventoryDocumentTypes.RETURN.equals(docType))
        {
            return nextCode(RETURN_PREFIX, "归还明细");
        }
        if (InventoryDocumentTypes.DAMAGE.equals(docType))
        {
            return nextCode(DAMAGE_PREFIX, "损坏登记明细");
        }
        if (InventoryDocumentTypes.REPAIR_OUT.equals(docType))
        {
            return nextCode(REPAIR_OUT_PREFIX, "送修明细");
        }
        if (InventoryDocumentTypes.REPAIR_IN.equals(docType))
        {
            return nextCode(REPAIR_IN_PREFIX, "维修归还明细");
        }
        if (InventoryDocumentTypes.STOCKTAKE_GAIN.equals(docType))
        {
            return nextCode(STOCKTAKE_GAIN_PREFIX, "盘盈入库明细");
        }
        if (InventoryDocumentTypes.STOCKTAKE_LOSS.equals(docType))
        {
            return nextCode(STOCKTAKE_LOSS_PREFIX, "盘亏出库明细");
        }
        if (InventoryDocumentTypes.COST_ADJUST.equals(docType))
        {
            return nextCode(COST_ADJUST_PREFIX, "成本调整明细");
        }
        throw new ServiceException("单据类型'" + docType + "'未配置明细号前缀");
    }

    private String nextCode(String prefix, String documentName)
    {
        Map result = redisService.geHourKey(KeyType.Base10, prefix);
        if (!Boolean.TRUE.equals(result.get("result")))
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException(documentName + "单号生成失败"
                    + (error == null ? "" : "：" + error));
        }

        Object primaryKey = result.get("primaryKey");
        String documentCode = primaryKey == null ? "" : primaryKey.toString();
        // prefix 直接拼进正则。现有前缀都是纯字母，没有正则元字符；
        // 销售订单的前缀是空串，这里退化成 ^[0-9]+$ 校验纯数字单号，是刻意的，别"修"。
        if (!documentCode.matches("^" + prefix + "[0-9]+$"))
        {
            throw new ServiceException(documentName + "单号格式异常");
        }
        return documentCode;
    }
}
