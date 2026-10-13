package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryBatchAllocation;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryLedger;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.mapper.InventoryBatchMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventorySerialMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventoryLedgerService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.utils.CostAdjustLayer;
import com.ruoyi.system.utils.InventoryCostMath;
import com.ruoyi.system.utils.InventoryLedgerRemarkResolver;
import com.ruoyi.system.utils.SerialNumberRemarkCodec;

/** 审核后的库存过账服务，统一维护库存、成本、批次、序列号和流水。 */
@Component
public class InventoryDocumentPostingService
{
    @Autowired
    private DocumentMainMapper documentMainMapper;

    @Autowired
    private DocumentDetailMapper documentDetailMapper;

    @Autowired
    private DocTypeConfigMapper docTypeConfigMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private IInventoryLedgerService ledgerService;

    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private IInventorySerialService serialService;

    @Autowired
    private InventoryCostResolver costResolver;

    @Autowired
    private InventoryDocumentLockCoordinator documentLockCoordinator;

    @Autowired
    private StocktakeFreezeGuard stocktakeFreezeGuard;

    @Autowired
    private InventoryBatchMapper batchMapper;

    @Autowired
    private InventorySerialMapper serialMapper;

    public void execute(DocumentMain document, String tableSuffix, String operator)
    {
        // 获取单据类型配置
        DocTypeConfig docTypeConfig = docTypeConfigMapper.selectDocTypeByCode(document.getDocType());
        if (docTypeConfig == null)
        {
            throw new ServiceException("单据类型'" + document.getDocType() + "'未配置");
        }

        String direction = docTypeConfig.getDirection();
        validateDocument(document, direction);
        List<DocumentDetail> details = documentDetailMapper.selectDetailsByDocCode(
                document.getShopCode(), document.getDocCode(), tableSuffix);

        if (details == null || details.isEmpty())
        {
            throw new ServiceException("单据明细为空");
        }

        // 盘点中的商品不许过账，否则清点期间的进出会被盘点再算一遍
        stocktakeFreezeGuard.requireNotFrozen(document, direction, details);

        documentLockCoordinator.lockStockOperationResources(document, direction, details);
        List<InventoryLedger> ledgers = new ArrayList<>();

        for (DocumentDetail detail : details)
        {
            detail.setSerialNumbers(SerialNumberRemarkCodec.decode(detail.getRemark()));
            InventoryLedger ledger = new InventoryLedger();
            ledger.setShopCode(document.getShopCode());
            ledger.setDocCode(document.getDocCode());
            ledger.setDocType(document.getDocType());
            ledger.setDocTypeName(document.getDocTypeName());
            ledger.setDetailCode(detail.getDetailCode());
            ledger.setGoodsCode(detail.getGoodsCode());
            ledger.setGoodsName(detail.getGoodsName());
            ledger.setSpec(detail.getSpec());
            ledger.setUnit(detail.getUnit());
            ledger.setCostPrice(detail.getCostPrice());
            ledger.setCostAmount(detail.getCostAmount());
            ledger.setBatchCode(detail.getBatchCode());
            ledger.setSourceDocCode(document.getSourceDocCode());
            ledger.setSourceDocType(document.getSourceDocType());
            ledger.setOperator(operator);
            ledger.setOperateTime(new java.util.Date());
            ledger.setTableSuffix(tableSuffix);

            if ("I".equals(direction))
            {
                // 入库操作：增加目标仓库库存
                executeInbound(document, detail, ledger);
                ledgers.add(ledger);
            }
            else if ("O".equals(direction))
            {
                // 出库操作：减少来源仓库库存
                ledgers.addAll(executeOutbound(document, detail, ledger));
            }
            else if ("N".equals(direction))
            {
                // 调拨操作：来源减，目标加
                executeTransfer(document, detail, ledger, tableSuffix, operator);
            }
            else if ("C".equals(direction))
            {
                // 成本调整：数量不动，只改成本
                ledgers.add(executeCostAdjust(document, detail, ledger));
            }
        }

        if ("O".equals(direction) || "N".equals(direction))
        {
            // 销售出库表头展示销售额，其中未改价的行已在 syncSalePriceWithActualCost
            // 里对齐到实际成本；其余出库、调拨回写审核时按实际批次/序列号重算出的成本额。
            boolean salesOutbound = InventoryDocumentTypes.SALES_OUTBOUND.equals(document.getDocType());
            BigDecimal actualTotalAmount = BigDecimal.ZERO;
            for (DocumentDetail detail : details)
            {
                BigDecimal amount = salesOutbound ? detail.getSaleAmount() : detail.getCostAmount();
                if (amount != null)
                {
                    actualTotalAmount = actualTotalAmount.add(amount);
                }
            }
            documentMainMapper.updateDocumentTotalAmount(document.getShopCode(), document.getDocCode(),
                    actualTotalAmount, tableSuffix);
        }

        // 批量写入流水
        if (!ledgers.isEmpty())
        {
            ledgerService.batchInsertLedger(ledgers);
        }
    }

    /**
     * 校验单据的库存状态流转。
     *
     * <p>二维状态下每种单据只改自己该改的那一维：借出、归还、生产领料、生产退料
     * 只改在库状态，损坏登记只改良品状态，另一维必须原样带过来。</p>
     *
     * <p>出库（方向 O）和调拨（方向 N，但换的是仓库不是状态）也要校验：出库只能从
     * 在库的货里扣（生产消耗例外，它扣的就是车间里的在制品），调拨只搬仓库，
     * 一格都不许改——否则调拨单能把不良品调成良品、把借出在外的货调回可用库存，
     * 绕开损坏登记和归还流程。</p>
     *
     * <p>注意「良品·不在库」这一格里借出和在制品是混在一起的，「不良品·不在库」
     * 里送外维修和借出的坏货也是混的。这里只能校验状态合法，管不住「归还的数量
     * 其实是车间的料」这种越界——想区分只能去翻单据。</p>
     */
    public void validateDocument(DocumentMain document, String direction)
    {
        if (document.getDocType() == null) return;
        if ("O".equals(direction))
        {
            validateOutbound(document);
            return;
        }
        if (!"N".equals(direction)) return;
        if (InventoryDocumentTypes.WAREHOUSE_TRANSFER.equals(document.getDocType()))
        {
            validateTransfer(document);
            return;
        }
        String type = document.getDocType();
        boolean sameWarehouseBusiness = InventoryDocumentTypes.LOAN.equals(type)
                || InventoryDocumentTypes.RETURN.equals(type)
                || InventoryDocumentTypes.REPAIR_OUT.equals(type)
                || InventoryDocumentTypes.REPAIR_IN.equals(type)
                || InventoryDocumentTypes.DAMAGE.equals(type)
                || InventoryDocumentTypes.PRODUCTION_PICK.equals(type)
                || InventoryDocumentTypes.PRODUCTION_RETURN.equals(type);
        if (!sameWarehouseBusiness) return;

        StockState from = document.getFromState();
        StockState to = document.getToState();
        if (from == null || !from.isValid() || to == null || !to.isValid())
        {
            throw new ServiceException("单据的来源、目标库存状态必须完整填写良品状态和在库状态");
        }

        if (InventoryDocumentTypes.PRODUCTION_PICK.equals(type))
        {
            // 生产领料：可用库存领到车间。车间不在仓库里，所以领出去就算不在库，
            // 否则在制品会落回「良品·在库」被当成可用库存再卖一次
            requireTransition(type, from, to, StockState.available(),
                    StockState.of(QualityFlag.GOOD, OnhandFlag.OUT));
        }
        else if (InventoryDocumentTypes.PRODUCTION_RETURN.equals(type))
        {
            // 生产退料：料从车间退回仓库。报废的料先退回来，再单独登记损坏
            requireTransition(type, from, to,
                    StockState.of(QualityFlag.GOOD, OnhandFlag.OUT),
                    StockState.available());
        }
        else if (InventoryDocumentTypes.LOAN.equals(type))
        {
            validateLoan(from, to);
        }
        else if (InventoryDocumentTypes.RETURN.equals(type))
        {
            validateReturn(from, to);
        }
        else if (InventoryDocumentTypes.REPAIR_OUT.equals(type))
        {
            validateRepairOut(from, to);
        }
        else if (InventoryDocumentTypes.REPAIR_IN.equals(type))
        {
            validateRepairIn(from, to);
        }
        else if (InventoryDocumentTypes.DAMAGE.equals(type))
        {
            validateDamage(from, to);
        }

        if (document.getFromWareCode() == null || !document.getFromWareCode().equals(document.getToWareCode()))
            throw new ServiceException("借出、归还、维修、损坏必须在同一仓库内办理");
    }

    /**
     * 出库：默认只能从在库的货里扣。
     *
     * <p>借出在外、领到车间的货都在「不在库」那一格，不挡住的话一张销售出库单就能
     * 把它们卖掉，库存看着对、货其实不在仓库里。生产消耗是唯一的例外——它扣的本来
     * 就是车间里的在制品。</p>
     *
     * <p>良品状态不限制：报废品、不良品降价出售都是正常业务，出库单上自己选。</p>
     */
    private void validateOutbound(DocumentMain document)
    {
        StockState from = document.getFromState();
        if (from == null || !from.isValid())
        {
            throw new ServiceException("出库单的来源库存状态必须完整填写良品状态和在库状态");
        }
        if (InventoryDocumentTypes.PRODUCTION_CONSUME.equals(document.getDocType()))
        {
            if (from.isOnhand())
            {
                throw new ServiceException("生产消耗扣的是已领到车间的在制品，来源必须是不在库，当前为" + from.getLabel());
            }
            return;
        }
        if (!from.isOnhand())
        {
            throw new ServiceException("出库的货必须在库，借出在外或已领到车间的货要先归还、退料，当前为" + from.getLabel());
        }
    }

    /**
     * 调拨：只搬仓库，一格状态都不许改。
     *
     * <p>不挡的话调拨单就成了万能洗白工具：不良品调一次变良品，绕开维修流程；
     * 借出在外的货调一次变可用库存，绕开归还。</p>
     */
    private void validateTransfer(DocumentMain document)
    {
        StockState from = document.getFromState();
        StockState to = document.getToState();
        if (from == null || !from.isValid() || to == null || !to.isValid())
        {
            throw new ServiceException("调拨单的来源、目标库存状态必须完整填写良品状态和在库状态");
        }
        if (!from.equals(to))
        {
            throw new ServiceException("调拨只换仓库不改库存状态，良品状态由损坏登记和维修决定、"
                    + "在库状态由借出归还决定：" + from.getLabel() + " -> " + to.getLabel());
        }
        if (!from.isOnhand())
        {
            throw new ServiceException("调拨的货必须在库，借出在外或已领到车间的货要先收回来，当前为" + from.getLabel());
        }
    }

    /**
     * 借出：货离开仓库，良品和不良品都可以借（比如坏机器借给供应商拆解），
     * 但良品状态本身不因借出而改变。
     */
    private void validateLoan(StockState from, StockState to)
    {
        if (!from.isOnhand())
        {
            throw new ServiceException("借出的货必须在库，当前为" + from.getLabel());
        }
        if (to.isOnhand())
        {
            throw new ServiceException("借出的目标状态必须是不在库，当前为" + to.getLabel());
        }
        requireSameQuality("借出", from, to);
    }

    /**
     * 归还：货回到仓库。借出期间的好坏变化不在这里体现——
     * 归还后维持原良品状态，坏了的再单独登记损坏。
     */
    private void validateReturn(StockState from, StockState to)
    {
        if (from.isOnhand())
        {
            throw new ServiceException("归还的来源必须是不在库的货，当前为" + from.getLabel());
        }
        if (!to.isOnhand())
        {
            throw new ServiceException("归还的目标必须是在库，当前为" + to.getLabel());
        }
        requireSameQuality("归还", from, to);
    }

    /** 借出、归还、生产领料退料只搬动在库状态，良品状态必须原样保留 */
    private void requireSameQuality(String action, StockState from, StockState to)
    {
        if (!java.util.Objects.equals(from.getQualityFlag(), to.getQualityFlag()))
        {
            throw new ServiceException(action + "不改变良品状态，货是好是坏要由损坏登记决定："
                    + from.getLabel() + " -> " + to.getLabel());
        }
    }

    /**
     * 送修：只有不良品能送修，良品要先登记损坏。
     *
     * <p>送外维修货离开仓库转到「不良品·不在库」；厂内维修货还在仓库，
     * 二维下状态不变，单据只作记录不产生库存变动。</p>
     */
    private void validateRepairOut(StockState from, StockState to)
    {
        if (from.isGood())
        {
            throw new ServiceException("只有不良品才能送修，良品请先登记损坏");
        }
        if (!from.isOnhand())
        {
            throw new ServiceException("送修的货必须在库，当前为" + from.getLabel());
        }
        if (to.isGood())
        {
            throw new ServiceException("送修的目标状态必须仍是不良品，修好与否由维修归还决定，当前为" + to.getLabel());
        }
    }

    /**
     * 维修归还：货回到仓库，修好了是良品，没修好仍是不良品。
     * 厂内维修的来源本来就在库，所以这里不限制来源的在库状态。
     */
    private void validateRepairIn(StockState from, StockState to)
    {
        if (from.isGood())
        {
            throw new ServiceException("维修归还的来源必须是不良品，当前为" + from.getLabel());
        }
        if (!to.isOnhand())
        {
            throw new ServiceException("维修归还的目标必须是在库，当前为" + to.getLabel());
        }
    }

    /**
     * 损坏登记：只把良品改成不良品，在库状态原样保留。
     * 借出在外的货登记损坏后仍是「不良品·不在库」，等归还时才回到仓库。
     */
    private void validateDamage(StockState from, StockState to)
    {
        if (!from.isGood())
        {
            throw new ServiceException("损坏登记的来源必须是良品，当前为" + from.getLabel());
        }
        if (to.isGood())
        {
            throw new ServiceException("损坏登记的目标必须是不良品，当前为" + to.getLabel());
        }
        if (!java.util.Objects.equals(from.getOnhandFlag(), to.getOnhandFlag()))
        {
            throw new ServiceException("损坏登记只改良品状态，在库状态必须保持不变："
                    + from.getLabel() + " -> " + to.getLabel());
        }
    }

    private void requireTransition(String type, StockState from, StockState to,
            StockState expectedFrom, StockState expectedTo)
    {
        if (!expectedFrom.equals(from) || !expectedTo.equals(to))
        {
            throw new ServiceException(type + "状态流转不合法，应为 " + expectedFrom.getLabel()
                    + " -> " + expectedTo.getLabel() + "，当前为 " + from.getLabel() + " -> " + to.getLabel());
        }
    }

    /**
     * 执行入库操作
     */
    private void executeInbound(DocumentMain document, DocumentDetail detail, InventoryLedger ledger)
    {
        String shopCode = document.getShopCode();
        String goodsCode = detail.getGoodsCode();
        String wareCode = valueOrDefault(detail.getWareCode(), document.getToWareCode());
        String wareName = valueOrDefault(detail.getWareName(), document.getToWareName());
        StockState state = resolveState(detail, document.getToState());
        BigDecimal qty = detail.getQty();
        BigDecimal costAmount = detail.getCostAmount() != null ? detail.getCostAmount() : BigDecimal.ZERO;

        // 查询或创建库存记录
        InventoryStock stock = stockMapper.selectStockByKeyForUpdate(shopCode, goodsCode, wareCode, state);
        if (stock == null)
        {
            // 创建新的库存记录
            stock = new InventoryStock();
            stock.setShopCode(shopCode);
            stock.setGoodsCode(goodsCode);
            stock.setGoodsName(detail.getGoodsName());
            stock.setSpec(detail.getSpec());
            stock.setUnit(detail.getUnit());
            stock.setWareCode(wareCode);
            stock.setWareName(wareName);
            stock.setStockState(state);
            stock.setPhysicalQty(qty);
            stock.setLockedQty(BigDecimal.ZERO);
            stock.setTotalCostAmount(costAmount);
            if (qty.compareTo(BigDecimal.ZERO) > 0)
            {
                stock.setAvgCostPrice(costAmount.divide(qty, 6, BigDecimal.ROUND_HALF_UP));
            }
            else
            {
                stock.setAvgCostPrice(BigDecimal.ZERO);
            }
            stockMapper.insertStock(stock);

            ledger.setBeforeQty(BigDecimal.ZERO);
        }
        else
        {
            // 更新现有库存
            BigDecimal beforeQty = stock.getPhysicalQty();
            int rows = stockMapper.adjustStockQtyNoVersion(shopCode, goodsCode, wareCode, state,
                    qty, costAmount);
            if (rows == 0)
            {
                throw new ServiceException("库存数据已被其他人修改，请刷新后重试");
            }

            ledger.setBeforeQty(beforeQty);
        }

        ledger.setWareCode(wareCode);
        ledger.setWareName(wareName);
        ledger.setStockState(state);
        ledger.setChangeQty(qty);
        ledger.setAfterQty(ledger.getBeforeQty().add(qty));
        ledger.setRemark("入库");

        // 创建批次记录
        if (detail.getBatchCode() != null && !detail.getBatchCode().isEmpty())
        {
            InventoryBatch batch = new InventoryBatch();
            batch.setShopCode(shopCode);
            batch.setBatchCode(detail.getBatchCode());
            batch.setGoodsCode(goodsCode);
            batch.setGoodsName(detail.getGoodsName());
            batch.setSpec(detail.getSpec() != null ? detail.getSpec() : "");
            batch.setUnit(detail.getUnit() != null ? detail.getUnit() : "");
            batch.setWareCode(wareCode);
            batch.setWareName(wareName);
            batch.setStockState(state);
            batch.setInitQty(qty);
            batch.setRemainQty(qty);
            batch.setCostPrice(detail.getCostPrice() != null ? detail.getCostPrice() : BigDecimal.ZERO);
            batch.setPurchaseCode(document.getSourceDocCode());
            batch.setPurchaseDate(document.getDocDate());
            BigDecimal costPrice = detail.getCostPrice() != null ? detail.getCostPrice() : BigDecimal.ZERO;
            batch.setPurchaseAmount(costPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP));
            batch.setPartnerCode(document.getPartnerCode());
            batch.setPartnerName(document.getPartnerName());
            batch.setStatus("active");
            batch.setCreateUser(document.getCreateUser());
            try
            {
                batchService.insertBatch(batch);
            }
            catch (ServiceException e)
            {
                // 批次已存在时不抛出异常，允许同一批次多次入库
                if (!e.getMessage().contains("已存在"))
                {
                    throw e;
                }
            }
        }

        // 创建序列号记录
        if (detail.getSerialNumbers() != null && !detail.getSerialNumbers().isEmpty())
        {
            List<InventorySerial> serials = new ArrayList<>();
            for (String serialNo : detail.getSerialNumbers())
            {
                if (serialNo == null || serialNo.trim().isEmpty())
                {
                    continue;
                }
                InventorySerial serial = new InventorySerial();
                serial.setShopCode(shopCode);
                serial.setSerialNo(serialNo.trim());
                serial.setGoodsCode(goodsCode);
                serial.setGoodsName(detail.getGoodsName());
                serial.setSpec(detail.getSpec());
                serial.setUnit(detail.getUnit());
                serial.setBatchCode(detail.getBatchCode());
                serial.setCostPrice(detail.getCostPrice());
                serial.setWareCode(wareCode);
                serial.setWareName(wareName);
                serial.setStockState(state);
                serial.setInboundDoc(document.getDocCode());
                serial.setInboundDetail(detail.getDetailCode());
                serial.setStatus("instock");
                serial.setInTime(new Date());
                serial.setPurchaseCode(document.getSourceDocCode());
                serial.setPartnerCode(document.getPartnerCode());
                serial.setCreateUser(document.getCreateUser());
                serials.add(serial);
            }
            if (!serials.isEmpty())
            {
                serialService.batchInsertSerial(serials);
            }
        }
    }

    /**
     * 执行出库操作
     */
    private List<InventoryLedger> executeOutbound(DocumentMain document, DocumentDetail detail,
            InventoryLedger ledger)
    {
        String shopCode = document.getShopCode();
        String goodsCode = detail.getGoodsCode();
        String wareCode = document.getFromWareCode();
        StockState state = document.getFromState();
        BigDecimal qty = detail.getQty().negate(); // 出库数量为负
        // 开单时估算的成本，用于判断销售单价有没有被业务员改过（下方按实际成本回写售价）。
        BigDecimal estimatedCostPrice = detail.getCostPrice();

        // 查询库存记录
        InventoryStock stock = stockMapper.selectStockByKeyForUpdate(shopCode, goodsCode, wareCode, state);
        if (stock == null)
        {
            throw new ServiceException("库存记录不存在");
        }

        // 检查库存是否充足
        BigDecimal physicalQty = stock.getPhysicalQty() != null ? stock.getPhysicalQty() : BigDecimal.ZERO;
        BigDecimal afterQty = physicalQty.add(qty);
        if (afterQty.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("库存不足，当前库存：" + physicalQty + "，需要出库：" + detail.getQty());
        }

        // 未预占的出库类型（领料、采购退货、其他出库）直接扣物理库存，若被其他待审核
        // 单据锁走的部分吃掉，扣减语句会静默失败。这里提前算一次可用库存给出明确原因，
        // 避免走到 adjustOutboundStock 之后只能报一句笼统的失败。
        if (!documentLockCoordinator.locksPendingQty(document.getDocType()))
        {
            // 口径统一走 getAvailableQty()：物理 - 待审核出库锁定 - 订单预占，
            // 与 InventoryStockMapper.xml 里的 SQL 强约束保持一致。
            BigDecimal availableQty = stock.getAvailableQty();
            if (availableQty.compareTo(detail.getQty()) < 0)
            {
                throw new ServiceException("可用库存不足，当前可用库存：" + availableQty
                        + "，需要出库：" + detail.getQty());
            }
        }

        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
        if (goods == null)
        {
            throw new ServiceException("商品'" + goodsCode + "'不存在");
        }
        boolean fifoBatch = goods != null
                && "batch".equalsIgnoreCase(valueOrDefault(goods.getBatchStrategy(), "none"))
                && "fifo".equalsIgnoreCase(valueOrDefault(goods.getCostCalculation(), ""));

        // 这一行是不是要发给某条销售订单明细。只有销售出库带得上，见 DocumentDetail.orderDetailCode。
        String orderDetailCode = isBlank(detail.getOrderDetailCode()) ? null : detail.getOrderDetailCode().trim();

        /*
         * 定制件必须走批次扣减，不看商品的成本策略。
         *
         * 专属归属挂在批次上，只有真的去挑批次、扣批次，「别人的定制货不能被这一单发走」
         * 才拦得住。而默认路径里加权平均出库压根不读也不写批次表（见下面 else 分支的注释），
         * 那条路上专属绑定形同虚设。
         *
         * 判据是「这个商品在本仓有专属批次」而不是「订单明细标了定制」：订单侧的标记
         * 这一层看不到，也不该看到——库存模块不该反向依赖订单模块。有专属批次就说明
         * 有人为某张单专门做过货，这一仓的这个商品就必须按批次发。
         *
         * 代价：同一个商品，涉及定制的出库按批次成本走（等于个别计价），
         * 普通出库仍按商品档案的口径。所以这条路上的流水备注要标出来，对账时看得见。
         */
        boolean ownedBatchPresent = !fifoBatch && batchService.hasOwnedBatch(shopCode, goodsCode, wareCode, state);
        boolean customBatch = ownedBatchPresent;
        if (customBatch) fifoBatch = true;

        ledger.setWareCode(wareCode);
        ledger.setWareName(document.getFromWareName());
        ledger.setStockState(state);

        List<InventoryLedger> outboundLedgers = new ArrayList<>();
        BigDecimal outboundCostAmount;
        if (fifoBatch)
        {
            List<InventoryBatchAllocation> allocations = batchService.deductFifoBatches(
                    shopCode, goodsCode, wareCode, state, detail.getQty(), orderDetailCode);
            BigDecimal actualCostAmount = BigDecimal.ZERO;
            for (InventoryBatchAllocation allocation : allocations)
            {
                BigDecimal batchCostPrice = allocation.getCostPrice() == null
                        ? BigDecimal.ZERO : allocation.getCostPrice();
                actualCostAmount = actualCostAmount.add(batchCostPrice.multiply(allocation.getQty()));
            }
            actualCostAmount = actualCostAmount.setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualCostPrice = actualCostAmount.divide(detail.getQty(), 6, RoundingMode.HALF_UP);
            detail.setCostPrice(actualCostPrice);
            detail.setCostAmount(actualCostAmount);
            documentDetailMapper.updateDetailCost(shopCode, detail.getDetailCode(), actualCostPrice,
                    actualCostAmount, ledger.getTableSuffix());
            ledger.setCostPrice(actualCostPrice);
            ledger.setCostAmount(actualCostAmount.negate());
            outboundCostAmount = actualCostAmount.negate();

            BigDecimal runningQty = physicalQty;
            BigDecimal remainingCostAmount = outboundCostAmount;

            for (int i = 0; i < allocations.size(); i++)
            {
                InventoryBatchAllocation allocation = allocations.get(i);
                BigDecimal allocationCostAmount;
                if (i == allocations.size() - 1)
                {
                    allocationCostAmount = remainingCostAmount;
                }
                else
                {
                    BigDecimal batchCostPrice = allocation.getCostPrice() == null
                            ? BigDecimal.ZERO : allocation.getCostPrice();
                    allocationCostAmount = batchCostPrice.multiply(allocation.getQty())
                            .setScale(2, RoundingMode.HALF_UP).negate();
                    remainingCostAmount = remainingCostAmount.subtract(allocationCostAmount);
                }

                InventoryLedger fifoLedger = copyLedgerBase(ledger);
                fifoLedger.setBatchCode(allocation.getBatchCode());
                fifoLedger.setCostPrice(allocation.getCostPrice());
                fifoLedger.setBeforeQty(runningQty);
                fifoLedger.setChangeQty(allocation.getQty().negate());
                runningQty = runningQty.subtract(allocation.getQty());
                fifoLedger.setAfterQty(runningQty);
                fifoLedger.setCostAmount(allocationCostAmount);
                // 定制件是被强制拉到这条路上来的，成本口径与商品档案不一致，备注里说清楚
                fifoLedger.setRemark(customBatch ? "出库（定制批次，按批次成本）" : "出库（FIFO自动分配）");
                outboundLedgers.add(fifoLedger);
            }
        }
        else
        {
            String trackingStrategy = valueOrDefault(goods.getBatchStrategy(), "none").toLowerCase();
            boolean serialTracked = "serial".equals(trackingStrategy);
            boolean individualCost = "individual".equalsIgnoreCase(
                    valueOrDefault(goods.getCostCalculation(), "moving_avg"));
            boolean individualSerial = serialTracked && individualCost;
            List<InventorySerial> selectedSerials = new ArrayList<>();
            List<BigDecimal> selectedSerialCosts = new ArrayList<>();
            Map<String, BigDecimal> serialBatchQuantities = new LinkedHashMap<>();
            if (serialTracked)
            {
                if (detail.getSerialNumbers() == null || detail.getSerialNumbers().isEmpty())
                {
                    throw new ServiceException("商品'" + detail.getGoodsName() + "'必须选择出库序列号");
                }
                int expectedSerialCount;
                try
                {
                    expectedSerialCount = detail.getQty().intValueExact();
                }
                catch (ArithmeticException e)
                {
                    throw new ServiceException("序列号商品'" + detail.getGoodsName() + "'的出库数量必须是整数");
                }
                if (detail.getSerialNumbers().size() != expectedSerialCount)
                {
                    throw new ServiceException("商品'" + detail.getGoodsName() + "'的出库数量与序列号数量不一致");
                }
                for (String serialNo : detail.getSerialNumbers())
                {
                    InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo,
                            goodsCode, wareCode, state);
                    if (serial == null || !"instock".equals(serial.getStatus())
                            || isBlank(serial.getBatchCode()))
                    {
                        throw new ServiceException("序列号'" + serialNo + "'未关联有效的在库批次");
                    }
                    // 序列号是人工挑的，挑到别人的定制货就得当场拦下——这条路不走 FIFO 分配，
                    // 上面那道「只挑得到自己的批次」的过滤在这里帮不上忙
                    InventoryBatch serialBatch = batchService.selectBatchByKey(shopCode,
                            serial.getBatchCode(), goodsCode, wareCode, state);
                    if (serialBatch != null && !serialBatch.isAvailableFor(orderDetailCode))
                    {
                        throw new ServiceException("序列号'" + serialNo + "'所在批次 " + serial.getBatchCode()
                                + " 是为销售订单 " + serialBatch.getOwnerOrderCode()
                                + " 专门生产的定制货，不能发给本单");
                    }
                    selectedSerials.add(serial);
                    serialBatchQuantities.merge(serial.getBatchCode(), BigDecimal.ONE, BigDecimal::add);
                    if (individualSerial)
                    {
                        selectedSerialCosts.add(costResolver.resolveSerialCostPrice(shopCode, goods,
                                wareCode, state, serial));
                    }
                }
            }

            BigDecimal actualCostPrice;
            BigDecimal actualCostAmount;
            if (individualSerial)
            {
                BigDecimal serialCostTotal = BigDecimal.ZERO;
                for (BigDecimal serialCost : selectedSerialCosts)
                {
                    serialCostTotal = serialCostTotal.add(serialCost);
                }
                actualCostPrice = serialCostTotal.divide(detail.getQty(), 6, RoundingMode.HALF_UP);
                actualCostAmount = serialCostTotal.setScale(2, RoundingMode.HALF_UP);
            }
            else
            {
                actualCostPrice = costResolver.resolveOutboundCostPrice(shopCode, goods,
                        wareCode, state, detail.getBatchCode(), detail.getSerialNumbers(),
                        detail.getQty(), stock);
                actualCostAmount = InventoryCostMath.amount(actualCostPrice, detail.getQty());
                if (serialTracked)
                {
                    for (int i = 0; i < selectedSerials.size(); i++)
                    {
                        selectedSerialCosts.add(actualCostPrice);
                    }
                }
            }
            detail.setCostPrice(actualCostPrice);
            detail.setCostAmount(actualCostAmount);
            documentDetailMapper.updateDetailCost(shopCode, detail.getDetailCode(), actualCostPrice,
                    actualCostAmount, ledger.getTableSuffix());
            ledger.setCostPrice(actualCostPrice);
            outboundCostAmount = actualCostAmount.negate();
            ledger.setBeforeQty(stock.getPhysicalQty());
            ledger.setChangeQty(qty);
            ledger.setAfterQty(afterQty);
            ledger.setCostAmount(outboundCostAmount);
            ledger.setRemark("出库");
            if (serialTracked)
            {
                for (Map.Entry<String, BigDecimal> entry : serialBatchQuantities.entrySet())
                {
                    batchService.deductBatchQty(shopCode, entry.getKey(), goodsCode,
                            wareCode, state, entry.getValue());
                }

                BigDecimal runningQty = physicalQty;
                BigDecimal writtenCostAmount = BigDecimal.ZERO;
                for (int i = 0; i < selectedSerials.size(); i++)
                {
                    InventorySerial serial = selectedSerials.get(i);
                    BigDecimal serialCostPrice = selectedSerialCosts.get(i);
                    BigDecimal serialCostAmount;
                    if (i == selectedSerials.size() - 1)
                    {
                        serialCostAmount = actualCostAmount.subtract(writtenCostAmount);
                    }
                    else
                    {
                        serialCostAmount = InventoryCostMath.amount(serialCostPrice, BigDecimal.ONE);
                        writtenCostAmount = writtenCostAmount.add(serialCostAmount);
                    }
                    InventoryLedger serialLedger = copyLedgerBase(ledger);
                    serialLedger.setSerialNo(serial.getSerialNo());
                    serialLedger.setBatchCode(serial.getBatchCode());
                    serialLedger.setCostPrice(serialCostPrice);
                    serialLedger.setCostAmount(serialCostAmount.negate());
                    serialLedger.setBeforeQty(runningQty);
                    serialLedger.setChangeQty(BigDecimal.ONE.negate());
                    runningQty = runningQty.subtract(BigDecimal.ONE);
                    serialLedger.setAfterQty(runningQty);
                    serialLedger.setRemark("出库（序列号）");
                    outboundLedgers.add(serialLedger);
                }
            }
            else if (individualCost && detail.getBatchCode() != null && !detail.getBatchCode().isEmpty())
            {
                // 只有个别计价按指定批次出库，才消耗该批次的剩余数量。加权平均直接扣减
                // 汇总库存，不读也不写批次；FIFO 走上面的自动分配分支。
                //
                // 批次是业务员在建单时手工指定的，可能指到别人的定制货上，这里必须拦。
                InventoryBatch picked = batchService.selectBatchByKey(shopCode, detail.getBatchCode(),
                        goodsCode, wareCode, state);
                if (picked != null && !picked.isAvailableFor(orderDetailCode))
                {
                    throw new ServiceException("批次 " + detail.getBatchCode() + " 是为销售订单 "
                            + picked.getOwnerOrderCode() + " 专门生产的定制货，不能发给本单");
                }
                batchService.deductBatchQty(shopCode, detail.getBatchCode(), goodsCode,
                        wareCode, state, detail.getQty());
            }
            if (!serialTracked)
            {
                outboundLedgers.add(ledger);
            }
        }

        // 最后一次出库吸收此前分次结转产生的分币尾差，保证数量归零时总成本同步归零。
        if (afterQty.compareTo(BigDecimal.ZERO) == 0)
        {
            BigDecimal remainingStockCost = stock.getTotalCostAmount() == null
                    ? BigDecimal.ZERO : stock.getTotalCostAmount();
            BigDecimal calculatedOutboundCost = outboundCostAmount.abs();
            if (remainingStockCost.compareTo(calculatedOutboundCost) != 0)
            {
                BigDecimal finalCostAmount = InventoryCostMath.amountWithClosingRemainder(
                        calculatedOutboundCost, afterQty, remainingStockCost);
                BigDecimal finalCostPrice = InventoryCostMath.average(finalCostAmount, detail.getQty());
                BigDecimal adjustment = finalCostAmount.subtract(calculatedOutboundCost);
                detail.setCostPrice(finalCostPrice);
                detail.setCostAmount(finalCostAmount);
                documentDetailMapper.updateDetailCost(shopCode, detail.getDetailCode(), finalCostPrice,
                        finalCostAmount, ledger.getTableSuffix());
                outboundCostAmount = finalCostAmount.negate();

                InventoryLedger lastLedger = outboundLedgers.get(outboundLedgers.size() - 1);
                lastLedger.setCostAmount(lastLedger.getCostAmount().subtract(adjustment));
                BigDecimal lastLedgerQty = lastLedger.getChangeQty() == null
                        ? BigDecimal.ZERO : lastLedger.getChangeQty().abs();
                if (lastLedgerQty.compareTo(BigDecimal.ZERO) > 0)
                {
                    lastLedger.setCostPrice(InventoryCostMath.average(
                            lastLedger.getCostAmount().abs(), lastLedgerQty));
                }
            }
        }

        // 批次校验及扣减成功后，再更新汇总库存；任一操作失败时整个事务回滚。
        // qty 在方法开头已取负，只有待审核阶段预占过库存的单据类型才释放锁定量，
        // 否则会把 lockedqty 减成负数。
        int rows = adjustOutboundStock(document, shopCode, goodsCode, wareCode, state,
                qty, outboundCostAmount);
        if (rows == 0)
        {
            throw new ServiceException(outboundFailureMessage(document, goodsCode));
        }

        // 序列号出库
        if (detail.getSerialNumbers() != null && !detail.getSerialNumbers().isEmpty())
        {
            for (String serialNo : detail.getSerialNumbers())
            {
                if (serialNo != null && !serialNo.trim().isEmpty())
                {
                    serialService.outboundSerial(shopCode, serialNo.trim(), goodsCode,
                            wareCode, state, document.getDocCode());
                }
            }
        }

        syncSalePriceWithActualCost(document, detail, estimatedCostPrice, ledger.getTableSuffix());

        return outboundLedgers;
    }

    /**
     * 销售出库审核后，把未改价的行的销售单价对齐到实际出库成本。
     *
     * <p>开单时售价按移动平均估算的成本带出，FIFO、个别计价要到这里才知道真实批次成本，
     * 单据金额必须跟着走，口径才和其余出库、调拨一致。业务员显式改过价的行保持原样，
     * 那是对外报价，不受成本重算影响。</p>
     */
    private void syncSalePriceWithActualCost(DocumentMain document, DocumentDetail detail,
            BigDecimal estimatedCostPrice, String tableSuffix)
    {
        if (!InventoryDocumentTypes.SALES_OUTBOUND.equals(document.getDocType())
                || detail.getSalePrice() == null)
        {
            return;
        }
        if (InventoryCostMath.isSalePriceOverridden(detail.getSalePrice(), estimatedCostPrice))
        {
            return;
        }
        BigDecimal actualSalePrice = InventoryCostMath.salePrice(detail.getCostPrice());
        if (detail.getSalePrice().compareTo(actualSalePrice) == 0)
        {
            return;
        }
        BigDecimal actualSaleAmount = InventoryCostMath.saleAmount(actualSalePrice, detail.getQty());
        detail.setSalePrice(actualSalePrice);
        detail.setSaleAmount(actualSaleAmount);
        documentDetailMapper.updateDetailSalePrice(document.getShopCode(), detail.getDetailCode(),
                actualSalePrice, actualSaleAmount, tableSuffix);
    }

    /**
     * 成本调整：数量不动，按 {@link CostAdjustLayer} 把差额落到出库时取成本的那一层，
     * 同时加减库存汇总的总成本。
     *
     * <p>明细的 costAmount 就是调整金额（正为调增、负为调减），建单时已按当时库存校验过一遍，
     * 这里锁住库存行后按审核那一刻的数据再校验一遍，因为中间可能出过库。</p>
     *
     * <p>在库数量为 0 的不许调：成本没有货可以承接。已经卖出去的那部分差额属于销售成本，
     * 要回溯到当初的出库单上，这张单据不管。</p>
     */
    private InventoryLedger executeCostAdjust(DocumentMain document, DocumentDetail detail, InventoryLedger ledger)
    {
        String shopCode = document.getShopCode();
        String goodsCode = detail.getGoodsCode();
        String wareCode = document.getFromWareCode();
        StockState state = document.getFromState();
        String label = "商品'" + valueOrDefault(detail.getGoodsName(), goodsCode) + "'";
        BigDecimal delta = detail.getCostAmount() == null ? BigDecimal.ZERO
                : detail.getCostAmount().setScale(2, RoundingMode.HALF_UP);
        if (delta.signum() == 0)
        {
            throw new ServiceException(label + "的调整金额不能为 0");
        }

        InventoryStock stock = stockMapper.selectStockByKeyForUpdate(shopCode, goodsCode, wareCode, state);
        BigDecimal qty = stock == null || stock.getPhysicalQty() == null ? BigDecimal.ZERO : stock.getPhysicalQty();
        if (qty.signum() <= 0)
        {
            throw new ServiceException(label + "在该仓库的在库数量为 0，成本没有货可以承接；"
                    + "已经卖出的货差额属于销售成本，不能用成本调整单处理");
        }
        BigDecimal stockTotal = stock.getTotalCostAmount() == null ? BigDecimal.ZERO : stock.getTotalCostAmount();
        if (stockTotal.add(delta).signum() < 0)
        {
            throw new ServiceException(label + "调减 " + delta.negate().toPlainString()
                    + " 超过了库存总成本 " + stockTotal.toPlainString());
        }
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
        if (goods == null)
        {
            throw new ServiceException("商品'" + goodsCode + "'不存在");
        }

        CostAdjustLayer layer = CostAdjustLayer.of(goods);
        BigDecimal unitAfter;
        String remark;
        if (layer == CostAdjustLayer.BATCH)
        {
            if (isBlank(detail.getBatchCode()))
            {
                throw new ServiceException(label + "按批次计价，成本调整要指定批次");
            }
            String batchCode = detail.getBatchCode().trim();
            InventoryBatch batch = batchService.selectBatchByKey(shopCode, batchCode, goodsCode, wareCode, state);
            BigDecimal remain = batch == null || batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            if (batch == null || !"active".equals(batch.getStatus()) || remain.signum() <= 0)
            {
                throw new ServiceException(label + "的批次'" + batchCode + "'不存在、已关闭或已经出完");
            }
            BigDecimal oldValue = InventoryCostMath.batchCostPrice(batch).multiply(remain).setScale(2, RoundingMode.HALF_UP);
            BigDecimal newValue = oldValue.add(delta);
            if (newValue.signum() <= 0)
            {
                throw new ServiceException(label + "批次'" + batchCode + "'剩余 " + remain.stripTrailingZeros().toPlainString()
                        + " 件的成本只有 " + oldValue.toPlainString() + "，调整后必须大于 0");
            }
            unitAfter = newValue.divide(remain, 6, RoundingMode.HALF_UP);
            // 没出过库的批次，单价是按「累计金额 / 入库数量」现算的（见 InventoryCostMath.batchCostPrice），
            // 所以累计金额必须改成调整后的值；出过库的按单价走，累计金额只作记录，加上差额即可
            BigDecimal initQty = batch.getInitQty() == null ? BigDecimal.ZERO : batch.getInitQty();
            BigDecimal purchaseAmount = remain.compareTo(initQty) == 0 ? newValue
                    : (batch.getPurchaseAmount() == null ? BigDecimal.ZERO : batch.getPurchaseAmount()).add(delta);
            if (batchMapper.adjustBatchCost(shopCode, batchCode, goodsCode, wareCode, state, unitAfter, purchaseAmount) != 1)
            {
                throw new ServiceException("批次'" + batchCode + "'已被其他人修改，请刷新后重试");
            }
            ledger.setBatchCode(batchCode);
            remark = "成本调整（批次 " + batchCode + "）";
        }
        else if (layer == CostAdjustLayer.SERIAL)
        {
            List<String> serials = detail.getSerialNumbers();
            if (serials == null || serials.isEmpty())
            {
                throw new ServiceException(label + "按序列号个别计价，成本调整要指定序列号");
            }
            BigDecimal count = BigDecimal.valueOf(serials.size());
            BigDecimal share = delta.divide(count, 6, RoundingMode.DOWN);
            BigDecimal written = BigDecimal.ZERO;
            for (int i = 0; i < serials.size(); i++)
            {
                String serialNo = serials.get(i);
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo, goodsCode, wareCode, state);
                if (serial == null || !"instock".equals(serial.getStatus()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'不在本仓库的" + label + "账上");
                }
                // 最后一个吸收除不尽的尾差，保证各序列号加起来正好等于调整金额
                BigDecimal part = i == serials.size() - 1 ? delta.subtract(written) : share;
                written = written.add(part);
                BigDecimal newCost = costResolver.resolveSerialCostPrice(shopCode, goods, wareCode, state, serial).add(part);
                if (newCost.signum() < 0)
                {
                    throw new ServiceException("序列号'" + serialNo + "'调整后的成本小于 0");
                }
                if (serialMapper.updateSerialCost(shopCode, serialNo, goodsCode, wareCode, state, newCost) != 1)
                {
                    throw new ServiceException("序列号'" + serialNo + "'已被其他人修改，请刷新后重试");
                }
            }
            unitAfter = delta.divide(count, 6, RoundingMode.HALF_UP);
            remark = "成本调整（序列号 " + serials.size() + " 个，每个约 " + unitAfter.stripTrailingZeros().toPlainString() + "）";
        }
        else
        {
            unitAfter = stockTotal.add(delta).divide(qty, 6, RoundingMode.HALF_UP);
            remark = "成本调整";
        }

        if (stockMapper.adjustStockQtyNoVersion(shopCode, goodsCode, wareCode, state, BigDecimal.ZERO, delta) != 1)
        {
            throw new ServiceException("库存数据已被其他人修改，请刷新后重试");
        }

        ledger.setWareCode(wareCode);
        ledger.setWareName(document.getFromWareName());
        ledger.setStockState(state);
        ledger.setBeforeQty(qty);
        ledger.setChangeQty(BigDecimal.ZERO);
        ledger.setAfterQty(qty);
        // 移动平均记调整后的均价；批次记调整后的批次单价；序列号记平均每件调了多少
        ledger.setCostPrice(unitAfter);
        ledger.setCostAmount(delta);
        ledger.setRemark(remark);
        return ledger;
    }

    /**
     * 复制流水公共字段，供 FIFO 跨批次时生成多条流水。
     */
    private InventoryLedger copyLedgerBase(InventoryLedger source)
    {
        InventoryLedger target = new InventoryLedger();
        target.setShopCode(source.getShopCode());
        target.setDocCode(source.getDocCode());
        target.setDocType(source.getDocType());
        target.setDocTypeName(source.getDocTypeName());
        target.setDetailCode(source.getDetailCode());
        target.setGoodsCode(source.getGoodsCode());
        target.setGoodsName(source.getGoodsName());
        target.setSpec(source.getSpec());
        target.setUnit(source.getUnit());
        target.setWareCode(source.getWareCode());
        target.setWareName(source.getWareName());
        target.setStockState(source.getStockState());
        target.setCostPrice(source.getCostPrice());
        target.setSourceDocCode(source.getSourceDocCode());
        target.setSourceDocType(source.getSourceDocType());
        target.setOperator(source.getOperator());
        target.setOperateTime(source.getOperateTime());
        target.setTableSuffix(source.getTableSuffix());
        return target;
    }

    /**
     * 扣减出库汇总库存。
     *
     * <p>销售出库和仓库调拨在创建待审核单据时已经预占了 lockedqty，审核时要
     * 「扣物理库存 + 释放本单锁定量」；采购退货 RO、生产领料 MO、其他出库 OO
     * 从未预占，只能扣物理库存。</p>
     *
     * <p>若对未预占的单据也走释放锁定的语句，lockedqty 会被减成负数，
     * 被 SQL 的非负条件挡下更新 0 行，最终抛出与实际原因无关的
     * 「库存数据已被其他人修改」。调拨路径已按此区分处理，这里保持一致。</p>
     */
    private int adjustOutboundStock(DocumentMain document, String shopCode, String goodsCode,
            String wareCode, StockState state, BigDecimal qty, BigDecimal costAmount)
    {
        if (documentLockCoordinator.locksPendingQty(document.getDocType()))
        {
            return stockMapper.adjustStockQtyWithUnlockNoVersion(shopCode, goodsCode, wareCode,
                    state, qty, qty, costAmount);
        }
        return stockMapper.adjustStockQtyNoVersion(shopCode, goodsCode, wareCode, state,
                qty, costAmount);
    }

    /** 出库扣减失败时给出与单据类型匹配的原因，避免一律报「已被其他人修改」。 */
    private String outboundFailureMessage(DocumentMain document, String goodsCode)
    {
        if (documentLockCoordinator.locksPendingQty(document.getDocType()))
        {
            return "库存数据已被其他人修改，请刷新后重试";
        }
        return "商品'" + goodsCode + "'可用库存不足，可能有部分库存被其他待审核单据锁定，请刷新后重试";
    }

    /**
     * 执行调拨操作
     */
    private void executeTransfer(DocumentMain document, DocumentDetail detail, InventoryLedger ledger,
            String tableSuffix, String operator)
    {
        String shopCode = document.getShopCode();
        String goodsCode = detail.getGoodsCode();
        BigDecimal qty = detail.getQty();
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
        if (goods == null)
        {
            throw new ServiceException("商品'" + goodsCode + "'不存在");
        }

        // 1. 减少来源仓库库存
        String fromWareCode = document.getFromWareCode();
        StockState fromState = document.getFromState();
        InventoryStock fromStock = stockMapper.selectStockByKeyForUpdate(
                shopCode, goodsCode, fromWareCode, fromState);
        if (fromStock == null)
        {
            throw new ServiceException("来源仓库库存记录不存在");
        }

        BigDecimal fromAfterQty = fromStock.getPhysicalQty().subtract(qty);
        if (fromAfterQty.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("来源仓库库存不足，当前库存：" + fromStock.getPhysicalQty());
        }

        List<String> transferSerials = detail.getSerialNumbers() == null
                ? new ArrayList<>() : detail.getSerialNumbers();
        String trackingStrategy = valueOrDefault(goods.getBatchStrategy(), "none").toLowerCase();
        boolean serialTracked = "serial".equals(trackingStrategy);
        boolean movingAverage = "moving_avg".equalsIgnoreCase(
                valueOrDefault(goods.getCostCalculation(), "moving_avg"));
        boolean individualSerial = serialTracked && "individual".equalsIgnoreCase(
                valueOrDefault(goods.getCostCalculation(), "moving_avg"));
        if (!serialTracked && !transferSerials.isEmpty())
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'未启用序列号管理，不能调拨序列号");
        }
        if (serialTracked && transferSerials.isEmpty())
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'必须选择调拨序列号");
        }

        Map<String, BigDecimal> batchQuantities = new LinkedHashMap<>();
        Map<String, BigDecimal> rawBatchCostAmounts = new LinkedHashMap<>();
        if (serialTracked)
        {
            int serialQty;
            try
            {
                serialQty = qty.intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new ServiceException("序列号商品'" + detail.getGoodsName() + "'的调拨数量必须是整数");
            }
            if (serialQty != transferSerials.size())
            {
                throw new ServiceException("商品'" + detail.getGoodsName() + "'的调拨数量与序列号数量不一致");
            }
            for (String serialNo : transferSerials)
            {
                InventorySerial serial = serialService.selectSerialByContext(shopCode, serialNo,
                        goodsCode, fromWareCode, fromState);
                if (serial == null || !"instock".equals(serial.getStatus()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'已不属于调出商品、仓库或库存状态");
                }
                if (isBlank(serial.getBatchCode()))
                {
                    throw new ServiceException("序列号'" + serialNo + "'未关联批次，不能调拨");
                }
                batchQuantities.merge(serial.getBatchCode(), BigDecimal.ONE, BigDecimal::add);
                if (individualSerial)
                {
                    BigDecimal serialCost = costResolver.resolveSerialCostPrice(shopCode, goods,
                            fromWareCode, fromState, serial);
                    rawBatchCostAmounts.merge(serial.getBatchCode(), serialCost, BigDecimal::add);
                }
            }
        }
        else if (!isBlank(detail.getBatchCode()))
        {
            batchQuantities.put(detail.getBatchCode(), qty);
        }

        // 审核时再次按锁定后的来源库存/批次/序列号确定真实成本。
        BigDecimal calculatedCostPrice;
        BigDecimal calculatedCostAmount;
        if (individualSerial)
        {
            BigDecimal serialCostTotal = BigDecimal.ZERO;
            for (BigDecimal batchCostAmount : rawBatchCostAmounts.values())
            {
                serialCostTotal = serialCostTotal.add(batchCostAmount);
            }
            calculatedCostPrice = serialCostTotal.divide(qty, 6, RoundingMode.HALF_UP);
            calculatedCostAmount = serialCostTotal.setScale(2, RoundingMode.HALF_UP);
        }
        else
        {
            calculatedCostPrice = costResolver.resolveTransferCostPrice(shopCode, goods,
                    fromWareCode, fromState, detail.getBatchCode(), transferSerials, qty, fromStock);
            calculatedCostAmount = InventoryCostMath.amount(calculatedCostPrice, qty);
        }
        BigDecimal actualCostAmount = InventoryCostMath.amountWithClosingRemainder(
                calculatedCostAmount, fromAfterQty, fromStock.getTotalCostAmount());
        BigDecimal actualCostPrice = InventoryCostMath.average(actualCostAmount, qty);
        detail.setCostPrice(actualCostPrice);
        detail.setCostAmount(actualCostAmount);
        documentDetailMapper.updateDetailCost(shopCode, detail.getDetailCode(), actualCostPrice,
                actualCostAmount, tableSuffix);

        BigDecimal costAmount = actualCostAmount.negate();

        // 只有普通仓库调拨在创建单据时锁定了来源库存，审核时需要释放本单锁定量。
        // 借出、归还、损坏、送修、维修归还等状态流转没有创建锁定记录，不能在这里
        // 扣减 lockedQty，否则会错误释放其他出库/调拨单据占用的库存。
        boolean releaseDocumentLock = documentLockCoordinator.locksPendingQty(document.getDocType());
        BigDecimal adjustLockedQty = releaseDocumentLock ? qty.negate() : BigDecimal.ZERO;
        if (!releaseDocumentLock)
        {
            // 同上，口径统一走 getAvailableQty()，含订单预占。
            BigDecimal availableQty = fromStock.getAvailableQty();
            if (availableQty.compareTo(qty) < 0)
            {
                throw new ServiceException("来源仓库可用库存不足，当前可用库存：" + availableQty);
            }
        }

        int rows = stockMapper.adjustStockQtyWithUnlockNoVersion(shopCode, goodsCode, fromWareCode, fromState,
                qty.negate(), adjustLockedQty, costAmount);
        if (rows == 0)
        {
            throw new ServiceException("库存数据已被其他人修改，请刷新后重试");
        }

        // 按批次分摊本次实际调拨成本，最后一个批次吸收分币尾差。
        Map<String, BigDecimal> batchCostPrices = new LinkedHashMap<>();
        Map<String, BigDecimal> batchCostAmounts = new LinkedHashMap<>();
        BigDecimal writtenBatchCost = BigDecimal.ZERO;
        int batchSequence = 0;
        for (Map.Entry<String, BigDecimal> entry : batchQuantities.entrySet())
        {
            String batchCode = entry.getKey();
            BigDecimal batchQty = entry.getValue();
            BigDecimal baseCostPrice;
            if (individualSerial)
            {
                baseCostPrice = rawBatchCostAmounts.get(batchCode).divide(
                        batchQty, 6, RoundingMode.HALF_UP);
            }
            else if (movingAverage)
            {
                baseCostPrice = actualCostPrice;
            }
            else
            {
                baseCostPrice = costResolver.resolveBatchCostPrice(shopCode, goods,
                        fromWareCode, fromState, batchCode);
            }
            BigDecimal batchCostAmount = ++batchSequence == batchQuantities.size()
                    ? actualCostAmount.subtract(writtenBatchCost)
                    : InventoryCostMath.amount(baseCostPrice, batchQty);
            writtenBatchCost = writtenBatchCost.add(batchCostAmount);
            batchCostAmounts.put(batchCode, batchCostAmount);
            batchCostPrices.put(batchCode, InventoryCostMath.average(batchCostAmount, batchQty));
        }
        writeTransferLedgers(document, detail, batchQuantities, batchCostPrices, batchCostAmounts,
                fromStock.getPhysicalQty(),
                fromWareCode, document.getFromWareName(), fromState,
                false, tableSuffix, operator);
        for (Map.Entry<String, BigDecimal> entry : batchQuantities.entrySet())
        {
            transferBatch(document, detail, entry.getKey(), entry.getValue(),
                    batchCostPrices.get(entry.getKey()), batchCostAmounts.get(entry.getKey()), operator);
        }
        for (String serialNo : transferSerials)
        {
            serialService.transferSerial(shopCode, serialNo, goodsCode,
                    fromWareCode, fromState, document.getToWareCode(), document.getToWareName(),
                    document.getToState());
        }

        // 2. 增加目标仓库库存
        String toWareCode = document.getToWareCode();
        StockState toState = document.getToState();
        BigDecimal toCostAmount = actualCostAmount;

        InventoryStock toStock = stockMapper.selectStockByKeyForUpdate(
                shopCode, goodsCode, toWareCode, toState);
        BigDecimal toBeforeQtyForLedger = toStock == null || toStock.getPhysicalQty() == null
                ? BigDecimal.ZERO : toStock.getPhysicalQty();
        if (toStock == null)
        {
            // 创建新的库存记录
            toStock = new InventoryStock();
            toStock.setShopCode(shopCode);
            toStock.setGoodsCode(goodsCode);
            toStock.setGoodsName(detail.getGoodsName());
            toStock.setSpec(detail.getSpec());
            toStock.setUnit(detail.getUnit());
            toStock.setWareCode(toWareCode);
            toStock.setWareName(document.getToWareName());
            toStock.setStockState(toState);
            toStock.setPhysicalQty(qty);
            toStock.setLockedQty(BigDecimal.ZERO);
            toStock.setTotalCostAmount(toCostAmount);
            if (qty.compareTo(BigDecimal.ZERO) > 0)
            {
                toStock.setAvgCostPrice(toCostAmount.divide(qty, 6, BigDecimal.ROUND_HALF_UP));
            }
            else
            {
                toStock.setAvgCostPrice(BigDecimal.ZERO);
            }
            stockMapper.insertStock(toStock);

        }
        else
        {
            // 更新现有库存
            rows = stockMapper.adjustStockQtyNoVersion(shopCode, goodsCode, toWareCode, toState,
                    qty, toCostAmount);
            if (rows == 0)
            {
                throw new ServiceException("库存数据已被其他人修改，请刷新后重试");
            }

        }
        writeTransferLedgers(document, detail, batchQuantities, batchCostPrices, batchCostAmounts,
                toBeforeQtyForLedger,
                toWareCode, document.getToWareName(), toState,
                true, tableSuffix, operator);
    }

    private void writeTransferLedgers(DocumentMain document, DocumentDetail detail,
            Map<String, BigDecimal> batchQuantities, Map<String, BigDecimal> batchCostPrices,
            Map<String, BigDecimal> batchCostAmounts,
            BigDecimal initialQty, String wareCode,
            String wareName, StockState state, boolean inbound,
            String tableSuffix, String operator)
    {
        Map<String, BigDecimal> allocations = batchQuantities.isEmpty()
                ? java.util.Collections.singletonMap(detail.getBatchCode(), detail.getQty()) : batchQuantities;
        BigDecimal runningQty = initialQty;
        for (Map.Entry<String, BigDecimal> entry : allocations.entrySet())
        {
            BigDecimal allocationQty = entry.getValue();
            BigDecimal changeQty = inbound ? allocationQty : allocationQty.negate();
            BigDecimal afterQty = runningQty.add(changeQty);
            BigDecimal allocationCostPrice = batchCostPrices.get(entry.getKey());
            if (allocationCostPrice == null)
            {
                allocationCostPrice = detail.getCostPrice() == null ? BigDecimal.ZERO : detail.getCostPrice();
            }
            BigDecimal allocationCost = batchCostAmounts.get(entry.getKey());
            if (allocationCost == null)
            {
                allocationCost = detail.getCostAmount() == null
                        ? InventoryCostMath.amount(allocationCostPrice, allocationQty)
                        : detail.getCostAmount();
            }
            if (!inbound) allocationCost = allocationCost.negate();

            InventoryLedger transferLedger = new InventoryLedger();
            transferLedger.setShopCode(document.getShopCode());
            transferLedger.setDocCode(document.getDocCode());
            transferLedger.setDocType(document.getDocType());
            transferLedger.setDocTypeName(document.getDocTypeName());
            transferLedger.setDetailCode(detail.getDetailCode());
            transferLedger.setGoodsCode(detail.getGoodsCode());
            transferLedger.setGoodsName(detail.getGoodsName());
            transferLedger.setSpec(detail.getSpec());
            transferLedger.setUnit(detail.getUnit());
            transferLedger.setWareCode(wareCode);
            transferLedger.setWareName(wareName);
            transferLedger.setStockState(state);
            transferLedger.setBeforeQty(runningQty);
            transferLedger.setChangeQty(changeQty);
            transferLedger.setAfterQty(afterQty);
            transferLedger.setCostPrice(allocationCostPrice);
            transferLedger.setCostAmount(allocationCost);
            transferLedger.setBatchCode(entry.getKey());
            transferLedger.setSourceDocCode(document.getSourceDocCode());
            transferLedger.setSourceDocType(document.getSourceDocType());
            transferLedger.setOperator(operator);
            transferLedger.setOperateTime(new java.util.Date());
            String ledgerRemark = InventoryLedgerRemarkResolver.resolve(document.getDocType(), state, changeQty);
            transferLedger.setRemark(ledgerRemark == null
                    ? (inbound ? "库存增加" : "库存扣减") : ledgerRemark);
            transferLedger.setTableSuffix(tableSuffix);
            ledgerService.insertLedger(transferLedger);
            runningQty = afterQty;
        }
    }

    private void transferBatch(DocumentMain document, DocumentDetail detail, String batchCode,
            BigDecimal batchQty, BigDecimal transferCostPrice, BigDecimal transferCostAmount,
            String operator)
    {
        String shopCode = document.getShopCode();
        String goodsCode = detail.getGoodsCode();
        StockState fromState = document.getFromState();
        StockState toState = document.getToState();
        InventoryBatch fromBatch = batchService.selectBatchByKey(
                shopCode, batchCode, goodsCode, document.getFromWareCode(), fromState);
        if (fromBatch == null || !fromState.equals(fromBatch.getStockState()))
        {
            throw new ServiceException("来源仓库批次'" + batchCode + "'不存在或库存状态不匹配");
        }
        batchService.deductBatchQty(shopCode, batchCode, goodsCode,
                document.getFromWareCode(), fromState, batchQty);

        InventoryBatch toBatch = batchService.selectBatchByKey(
                shopCode, batchCode, goodsCode, document.getToWareCode(), toState);
        if (toBatch != null)
        {
            if (!toState.equals(toBatch.getStockState()))
            {
                throw new ServiceException("目标仓库批次'" + batchCode + "'已属于库存状态'"
                        + toBatch.getStockState().getLabel() + "'，不能合并到'" + toState.getLabel() + "'");
            }
            toBatch.setInitQty(batchQty);
            toBatch.setRemainQty(batchQty);
            toBatch.setCostPrice(transferCostPrice);
            toBatch.setPurchaseAmount(transferCostAmount);
            toBatch.setStatus("active");
            batchService.insertBatch(toBatch);
            return;
        }

        InventoryBatch newBatch = new InventoryBatch();
        newBatch.setShopCode(shopCode);
        newBatch.setBatchCode(batchCode);
        newBatch.setGoodsCode(goodsCode);
        newBatch.setGoodsName(detail.getGoodsName());
        newBatch.setSpec(detail.getSpec() != null ? detail.getSpec() : "");
        newBatch.setUnit(detail.getUnit() != null ? detail.getUnit() : "");
        newBatch.setWareCode(document.getToWareCode());
        newBatch.setWareName(document.getToWareName());
        newBatch.setStockState(toState);
        newBatch.setInitQty(batchQty);
        newBatch.setRemainQty(batchQty);
        newBatch.setCostPrice(transferCostPrice);
        newBatch.setStatus("active");
        newBatch.setCreateUser(operator);
        newBatch.setProductDate(fromBatch.getProductDate());
        newBatch.setExpireDate(fromBatch.getExpireDate());
        newBatch.setPurchaseCode(fromBatch.getPurchaseCode());
        newBatch.setPurchaseDate(fromBatch.getPurchaseDate());
        newBatch.setPurchaseAmount(transferCostAmount);
        newBatch.setPartnerCode(fromBatch.getPartnerCode());
        newBatch.setPartnerName(fromBatch.getPartnerName());
        batchService.insertBatch(newBatch);
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private String valueOrDefault(String value, String defaultValue)
    {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    /**
     * 取明细行的三维状态，没填全时回退到单据表头的状态。
     *
     * @param detail 单据明细
     * @param fallback 表头状态
     * @return 三维状态
     */
    private StockState resolveState(DocumentDetail detail, StockState fallback)
    {
        StockState state = detail.getStockState();
        return state != null && state.isValid() ? state : fallback;
    }
}
