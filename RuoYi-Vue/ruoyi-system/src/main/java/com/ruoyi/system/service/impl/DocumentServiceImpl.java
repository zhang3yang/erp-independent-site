package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.runner.MonthlyTableInitializer.TableGroup;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.utils.GoodsSummaryBuilder;
import com.ruoyi.system.utils.InventoryCostMath;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.SerialNumberRemarkCodec;

/**
 * 单据Service业务层处理
 */
@Service
public class DocumentServiceImpl implements IDocumentService
{
    @Autowired
    private DocumentMainMapper documentMainMapper;

    @Autowired
    private DocumentDetailMapper documentDetailMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private DocTypeConfigMapper docTypeConfigMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryCostResolver costResolver;

    @Autowired
    private InventoryDocumentLockCoordinator documentLockCoordinator;

    @Autowired
    private InventoryDocumentPostingService postingService;

    @Autowired
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    @Autowired
    private InventoryDocumentTableRouter tableRouter;

    @Autowired
    private InventoryDocumentIdempotencyService idempotencyService;

    @Autowired
    private MonthlyTableInitializer monthlyTables;

    /**
     * 查询单据（含明细）
     */
    @Override
    public DocumentMain selectDocumentByCode(String shopCode, String docCode, String tableSuffix)
    {
        tableSuffix = validateTableSuffix(tableSuffix);
        DocumentMain document = documentMainMapper.selectDocumentByCode(shopCode, docCode, tableSuffix);
        if (document != null)
        {
            List<DocumentDetail> details = documentDetailMapper.selectDetailsByDocCode(shopCode, docCode, tableSuffix);
            for (DocumentDetail detail : details)
            {
                detail.setSerialNumbers(SerialNumberRemarkCodec.decode(detail.getRemark()));
            }
            document.setDetails(details);
            document.setTableSuffix(tableSuffix);
        }
        return document;
    }

    /**
     * 查询单据列表。
     *
     * <p>单据按 docdate 分月表，所以「查哪个月」这件事必须由调用方说清楚，三种方式：</p>
     * <ol>
     *   <li>指定 {@code tableSuffix} 或 {@code docDate} —— 只查这一张月表；</li>
     *   <li>指定 {@code docDateBegin}/{@code docDateEnd} —— 查区间覆盖到的月表；</li>
     *   <li>什么都不给 —— 查全部现存月表。</li>
     * </ol>
     *
     * <p>第三种以前是「只查当月」，结果一跨月，上个月的出库单、调拨单在页面上就凭空
     * 消失了，用户只能改 URL 参数才捞得回来。列表长一点远好过查不到。</p>
     */
    @Override
    public List<DocumentMain> selectDocumentList(DocumentMain document)
    {
        String explicitSuffix = resolveExplicitListSuffix(document);
        if (explicitSuffix != null)
        {
            document.setTableSuffix(explicitSuffix);
            return documentMainMapper.selectDocumentList(document);
        }

        // 月表清单必须在分页之外查：startPage() 之后的第一条 SQL 会把分页参数吃掉，
        // 让这条元数据查询吃走的话，下面真正的列表查询就变成不分页了。
        List<String> existing = PageUtils.withoutPaging(
                () -> documentMainMapper.selectDocumentMainTableSuffixes());
        List<String> suffixes = MonthTableSuffix.queryRange(existing,
                document.getDocDateBegin(), document.getDocDateEnd());
        if (suffixes.isEmpty())
        {
            // 一条 SQL 都不发就直接返回时，必须把 startPage() armed 的分页参数清掉。
            // 它是线程变量，留在那儿会被这个线程处理的<b>下一个请求</b>的第一条 SQL 吃掉——
            // 那条 SQL 可能自带 limit，拼出 `limit 1 LIMIT ?` 直接语法报错。
            PageUtils.clearPage();
            return new ArrayList<>();
        }
        return documentMainMapper.selectDocumentListAcrossMonths(document, suffixes);
    }

    /**
     * 列表查询是否被限定在单个月表上。
     *
     * @return 该月后缀；调用方没限定月份时返回 null，表示要走跨月查询
     */
    private String resolveExplicitListSuffix(DocumentMain document)
    {
        if (document.getTableSuffix() != null && !document.getTableSuffix().isEmpty())
        {
            return validateTableSuffix(document.getTableSuffix());
        }
        if (document.getDocDate() != null)
        {
            return getTableSuffixByBusinessDate(document.getDocDate());
        }
        return null;
    }

    /**
     * 新增单据（含明细）
     */
    @Override
    @Transactional
    public int insertDocument(DocumentMain document)
    {
        String idempotencyKey = idempotencyService.resolveKey(document);
        if (idempotencyKey != null)
        {
            document.setIdempotencyKey(idempotencyKey);
            String replayCode = idempotencyService.replay(document);
            if (replayCode != null)
            {
                return 1;
            }
            idempotencyService.claim(document);
            idempotencyService.registerRollbackCleanup(document);
        }
        try
        {
            int result = insertDocumentInternal(document);
            if (idempotencyKey != null)
            {
                idempotencyService.registerCompletion(document);
            }
            return result;
        }
        catch (RuntimeException e)
        {
            if (idempotencyKey != null && !org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            {
                idempotencyService.cleanupAfterFailure(document);
            }
            throw e;
        }
    }

    private int insertDocumentInternal(DocumentMain document)
    {
        // 盘盈入库、盘亏出库是盘点单审核的产物，手工开一张等于绕过盘点直接改库存
        if ((InventoryDocumentTypes.STOCKTAKE_GAIN.equals(document.getDocType())
                || InventoryDocumentTypes.STOCKTAKE_LOSS.equals(document.getDocType()))
                && !InventoryDocumentTypes.STOCKTAKE_SOURCE.equals(document.getSourceDocType()))
        {
            throw new ServiceException("盘盈入库、盘亏出库只能由库存盘点审核生成，请到「库存盘点」建盘点单");
        }
        if (document.getDocDate() == null)
        {
            document.setDocDate(new Date());
        }
        // 物理月表严格按业务单据日期路由，不使用当前月份或客户端随意传入的后缀。
        document.setTableSuffix(getTableSuffixByBusinessDate(document.getDocDate()));

        // 单据日期是业务员自己选的，可以指到当月和下月之外去（填错年份、补录历史月份）。
        // 那些月份的月表不存在，直接往下走的话 insert 撞「Table doesn't exist」，
        // 页面上只看到一句「系统异常」。这里提前拦一道，给出能照着做的提示。
        //
        // ⚠ 只查不建。在这个事务里建表会撞 1412 Table definition has changed——
        //   DDL 立即提交，而本事务的表定义快照停在 DDL 之前，读新表直接报错，
        //   表建出来了这一笔还是失败。月表由启动器和每天的定时任务提前建当月+下月，
        //   详见 MonthlyTableInitializer.requireTablesFor 的注释。
        monthlyTables.requireTablesFor(document.getTableSuffix(), TableGroup.INVENTORY);

        // 资产状态单据始终由服务端生成编号，忽略客户端传入值。
        // 必须在重复校验、明细 docCode 回填和 detailCode 生成之前完成编号生成。
        String generatedDocCode = generateAssetDocumentCode(document.getDocType());
        if (generatedDocCode != null)
        {
            document.setDocCode(generatedDocCode);
        }
        if (isBlank(document.getDocCode()))
        {
            throw new ServiceException("单据编号不能为空");
        }

        // 校验单据代码是否已存在
        DocumentMain existing = documentMainMapper.selectDocumentByCode(
                document.getShopCode(), document.getDocCode(), document.getTableSuffix());
        if (existing != null)
        {
            throw new ServiceException("单据代码'" + document.getDocCode() + "'已存在");
        }

        // 设置默认审核状态
        if (document.getConfirmState() == null)
        {
            document.setConfirmState("待审核");
        }
        applyDocumentDefaults(document);
        fillDocumentReferenceNames(document);

        DocTypeConfig docTypeConfig = docTypeConfigMapper.selectDocTypeByCode(document.getDocType());
        String direction = docTypeConfig == null ? null : docTypeConfig.getDirection();

        // 商品内容摘要先按客户端提交的明细算一遍。下面的循环会用商品主档补齐缺失的商品名，
        // 补出来的值不一样时再回写一次（见循环末尾），绝大多数单据这一次就是最终值。
        String goodsSummary = buildGoodsSummary(document);
        document.setGoodsSummary(goodsSummary);

        // 先写主表，使幂等键唯一约束在明细和库存锁定之前生效；事务失败时主表和明细整体回滚。
        int mainRows = documentMainMapper.insertDocument(document);

        // 插入明细
        if (document.getDetails() != null && !document.getDetails().isEmpty())
        {
            int lineNo = 1;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalSaleAmount = BigDecimal.ZERO;
            for (DocumentDetail detail : document.getDetails())
            {
                detail.setShopCode(document.getShopCode());
                detail.setDocCode(document.getDocCode());
                detail.setTableSuffix(document.getTableSuffix());
                fillGoodsInfo(detail, document.getShopCode());
                fillDetailReferenceNames(detail, document, direction);
                if ("N".equals(direction))
                {
                    resolveTransferDetailCost(document, detail);
                }
                else if (isSalesOutbound(document))
                {
                    // 销售单价要以入库成本为基准带出，成本必须由服务端按来源库存解析，
                    // 不能沿用客户端传入的价格。其余出库类型仍在审核过账时才结算成本。
                    resolveOutboundDetailCost(document, detail);
                }
                resolveSalePrice(document, detail);
                if (detail.getSerialNumbers() != null)
                {
                    detail.setRemark(SerialNumberRemarkCodec.encode(detail.getSerialNumbers()));
                }
                // 新增明细时忽略客户端传入编号，统一由服务端生成。
                detail.setDetailCode(documentCodeGenerator.nextDetailCode(document.getDocType()));
                if (detail.getLineNo() == null)
                {
                    detail.setLineNo(lineNo);
                }
                lineNo++;
                if (detail.getQty() != null)
                {
                    totalQty = totalQty.add(detail.getQty());
                }
                if (detail.getSaleAmount() != null)
                {
                    totalSaleAmount = totalSaleAmount.add(detail.getSaleAmount());
                }
            }
            documentDetailMapper.batchInsertDetail(document.getDetails(), document.getTableSuffix());
            markGoodsUsed(document);

            // 客户端只传了商品编码时，商品名是上面 fillGoodsInfo 从主档补出来的，
            // 插主表那一刻还不知道。变了才回写，没变就省掉这条 update。
            String finalSummary = buildGoodsSummary(document);
            if (!Objects.equals(goodsSummary, finalSummary))
            {
                document.setGoodsSummary(finalSummary);
                documentMainMapper.updateDocumentGoodsSummary(document.getShopCode(),
                        document.getDocCode(), finalSummary, document.getTableSuffix());
            }

            // 销售出库表头展示销售额；成本额在审核过账时按实际出库成本回写。
            if (isSalesOutbound(document))
            {
                document.setTotalQty(totalQty);
                document.setTotalAmount(totalSaleAmount);
                documentMainMapper.updateDocumentTotals(document.getShopCode(), document.getDocCode(),
                        totalQty, totalSaleAmount, document.getTableSuffix());
            }
        }

        documentLockCoordinator.lockPendingOutboundOrTransfer(document);

        return mainRows;
    }

    /**
     * 修改单据（含明细）
     */
    @Override
    @Transactional
    public int updateDocument(DocumentMain document)
    {
        if (document.getTableSuffix() == null || document.getTableSuffix().isEmpty())
        {
            document.setTableSuffix(document.getDocDate() == null
                    ? getCurrentTableSuffix() : getTableSuffixByBusinessDate(document.getDocDate()));
        }
        else
        {
            document.setTableSuffix(validateTableSuffix(document.getTableSuffix()));
        }

        // 只有待审核状态才能修改
        DocumentMain existing = documentMainMapper.selectDocumentByCode(
                document.getShopCode(), document.getDocCode(), document.getTableSuffix());
        if (existing == null)
        {
            throw new ServiceException("单据不存在");
        }
        if (!"待审核".equals(existing.getConfirmState()))
        {
            throw new ServiceException("只有待审核状态的单据才能修改");
        }
        // 成本调整的金额可以是负数，下面的通用重算会把它当成非法单价拦掉或改坏
        if (InventoryDocumentTypes.COST_ADJUST.equals(existing.getDocType()))
        {
            throw new ServiceException("成本调整单不能修改，请删除后重新开单");
        }

        if (document.getDocType() == null) document.setDocType(existing.getDocType());
        if (document.getDocTypeName() == null) document.setDocTypeName(existing.getDocTypeName());
        if (document.getDocDate() == null) document.setDocDate(existing.getDocDate());
        String originalSuffix = getTableSuffixByBusinessDate(existing.getDocDate());
        String requestedSuffix = getTableSuffixByBusinessDate(document.getDocDate());
        if (!originalSuffix.equals(document.getTableSuffix()) || !originalSuffix.equals(requestedSuffix))
        {
            throw new ServiceException("已保存单据不能跨月修改，请重新创建单据");
        }
        if (isBlank(document.getFromWareCode())) document.setFromWareCode(existing.getFromWareCode());
        if (isBlank(document.getFromWareName())) document.setFromWareName(existing.getFromWareName());
        if (isBlank(document.getToWareCode())) document.setToWareCode(existing.getToWareCode());
        if (isBlank(document.getToWareName())) document.setToWareName(existing.getToWareName());
        if (document.getFromState() == null) document.setFromState(existing.getFromState());
        if (document.getToState() == null) document.setToState(existing.getToState());
        if (isBlank(document.getPartnerCode())) document.setPartnerCode(existing.getPartnerCode());
        if (isBlank(document.getPartnerName())) document.setPartnerName(existing.getPartnerName());
        if (isBlank(document.getHandler())) document.setHandler(existing.getHandler());
        if (document.getHandlerTime() == null) document.setHandlerTime(existing.getHandlerTime());
        if (document.getSourceDocCode() == null) document.setSourceDocCode(existing.getSourceDocCode());
        if (document.getSourceDocType() == null) document.setSourceDocType(existing.getSourceDocType());
        if (document.getSourceDocTypeName() == null) document.setSourceDocTypeName(existing.getSourceDocTypeName());
        if (document.getTotalQty() == null) document.setTotalQty(existing.getTotalQty());
        if (document.getTotalAmount() == null) document.setTotalAmount(existing.getTotalAmount());
        fillDocumentReferenceNames(document);

        List<DocumentDetail> originalDetails = documentDetailMapper.selectDetailsByDocCode(
                document.getShopCode(), document.getDocCode(), document.getTableSuffix());
        documentLockCoordinator.unlockPendingOutboundOrTransfer(existing, originalDetails);
        Map<String, DocumentDetail> originalDetailMap = new HashMap<>();
        for (DocumentDetail originalDetail : originalDetails)
        {
            originalDetailMap.put(originalDetail.getDetailCode(), originalDetail);
        }

        DocTypeConfig docTypeConfig = docTypeConfigMapper.selectDocTypeByCode(document.getDocType());
        String direction = docTypeConfig == null ? null : docTypeConfig.getDirection();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 删除原有明细并重新插入
        documentDetailMapper.deleteDetailsByDocCode(
                document.getShopCode(), document.getDocCode(), document.getTableSuffix());

        if (document.getDetails() != null && !document.getDetails().isEmpty())
        {
            int lineNo = 1;
            for (DocumentDetail detail : document.getDetails())
            {
                detail.setShopCode(document.getShopCode());
                detail.setDocCode(document.getDocCode());
                detail.setTableSuffix(document.getTableSuffix());
                fillGoodsInfo(detail, document.getShopCode());
                fillDetailReferenceNames(detail, document, direction);

                DocumentDetail originalDetail = detail.getDetailCode() == null
                        ? null : originalDetailMap.get(detail.getDetailCode());
                if (originalDetail == null && lineNo <= originalDetails.size())
                {
                    DocumentDetail sameLineDetail = originalDetails.get(lineNo - 1);
                    if (detail.getGoodsCode().equals(sameLineDetail.getGoodsCode()))
                    {
                        originalDetail = sameLineDetail;
                    }
                }
                boolean sameGoods = originalDetail != null
                        && detail.getGoodsCode().equals(originalDetail.getGoodsCode());

                if (detail.getSerialNumbers() == null && originalDetail != null)
                {
                    detail.setSerialNumbers(SerialNumberRemarkCodec.decode(originalDetail.getRemark()));
                }
                if (detail.getCostPrice() == null && sameGoods)
                {
                    detail.setCostPrice(originalDetail.getCostPrice());
                }
                // 改过价的行沿用业务员的定价；未改价的行留空，由下面按当前成本重新带出，
                // 避免编辑时换了仓库、批次后售价还停在旧成本上。
                if (detail.getSalePrice() == null && sameGoods
                        && InventoryCostMath.isSalePriceOverridden(
                                originalDetail.getSalePrice(), originalDetail.getCostPrice()))
                {
                    detail.setSalePrice(originalDetail.getSalePrice());
                }
                if ("N".equals(direction))
                {
                    resolveTransferDetailCost(document, detail);
                }
                else if ("O".equals(direction))
                {
                    resolveOutboundDetailCost(document, detail);
                }
                // 新增行和未改价的行按当前入库成本带出售价；改过价的行沿用上面回填的快照。
                resolveSalePrice(document, detail);
                recalculateDetailAmounts(detail);

                // 已有明细保留原编号；新增明细统一由服务端生成。
                detail.setDetailCode(originalDetail == null
                        ? documentCodeGenerator.nextDetailCode(document.getDocType())
                        : originalDetail.getDetailCode());
                if (detail.getSerialNumbers() != null)
                {
                    detail.setRemark(SerialNumberRemarkCodec.encode(detail.getSerialNumbers()));
                }
                detail.setLineNo(lineNo++);
                totalQty = totalQty.add(detail.getQty());
                // 销售出库表头展示销售额，其余单据仍按成本额汇总。
                totalAmount = totalAmount.add(isSalesOutbound(document)
                        ? detail.getSaleAmount() : detail.getCostAmount());
            }
            documentDetailMapper.batchInsertDetail(document.getDetails(), document.getTableSuffix());
            markGoodsUsed(document);
        }

        documentLockCoordinator.lockPendingOutboundOrTransfer(document);

        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount);
        // 改单是「删掉全部明细再整体重建」，摘要跟着整体重算即可；
        // 明细被删光时算出 null，列表上那句「可乐等3种」也会跟着消失。
        document.setGoodsSummary(buildGoodsSummary(document));

        return documentMainMapper.updateDocument(document);
    }

    /** 按当前明细算出列表页显示的「商品内容」，如「可乐等3种」。没有明细返回 null。 */
    private String buildGoodsSummary(DocumentMain document)
    {
        return GoodsSummaryBuilder.build(document.getDetails(),
                DocumentDetail::getGoodsCode, DocumentDetail::getGoodsName);
    }

    /**
     * 补齐明细中的商品主数据，避免客户端只提交商品编码时写入空商品名称。
     *
     * @return 商品主档，供调用方复用，避免重复查询。
     */
    private InventoryGoods fillGoodsInfo(DocumentDetail detail, String shopCode)
    {
        if (detail.getGoodsCode() == null || detail.getGoodsCode().trim().isEmpty())
        {
            throw new ServiceException("商品编码不能为空");
        }
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, detail.getGoodsCode());
        if (goods == null)
        {
            throw new ServiceException("商品'" + detail.getGoodsCode() + "'不存在");
        }
        if (detail.getGoodsName() == null || detail.getGoodsName().trim().isEmpty())
        {
            detail.setGoodsName(goods.getGoodsName());
        }
        if (detail.getSpec() == null)
        {
            detail.setSpec(goods.getSpec());
        }
        if (detail.getUnit() == null)
        {
            detail.setUnit(goods.getUnit());
        }
        if (detail.getGoodsName() == null || detail.getGoodsName().trim().isEmpty())
        {
            throw new ServiceException("商品'" + detail.getGoodsCode() + "'名称不能为空");
        }
        return goods;
    }

    /**
     * 销售出库的销售单价以本行的入库成本为基准。
     *
     * <p>客户端未指定时带出服务端按来源库存解析出的成本单价，指定时按快照存入单据。
     * 商品主档的参考价只作展示，不参与这里的任何计算，也不再是销售出库的前置条件。
     * 销售单价只决定对外金额，不参与库存成本核算。</p>
     *
     * <p>带出的售价按 saleprice 列精度规约，使存库前后数值一致；审核过账时正是靠
     * “售价是否仍等于开单成本”判断业务员有没有改过价。</p>
     */
    private void resolveSalePrice(DocumentMain document, DocumentDetail detail)
    {
        if (!isSalesOutbound(document))
        {
            return;
        }
        if (detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'的数量必须大于0");
        }
        if (detail.getSalePrice() == null)
        {
            detail.setSalePrice(InventoryCostMath.salePrice(detail.getCostPrice()));
        }
        else if (detail.getSalePrice().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'的销售单价不能小于0");
        }
        else
        {
            detail.setSalePrice(InventoryCostMath.salePrice(detail.getSalePrice()));
        }
        detail.setSaleAmount(InventoryCostMath.saleAmount(detail.getSalePrice(), detail.getQty()));
    }

    /** 销售出库的表头金额按销售额汇总；其余单据仍以成本额汇总。 */
    private boolean isSalesOutbound(DocumentMain document)
    {
        return InventoryDocumentTypes.SALES_OUTBOUND.equals(document.getDocType());
    }

    /**
     * 单据一旦引用到商品，就把商品标记为「已使用」，之后名称、规格型号、计量单位、商品类型、
     * 批次策略、成本计算方法不再允许修改（见 InventoryGoodsServiceImpl.updateGoods）。
     *
     * <p>这里在保存明细时置位，不等审核——待审核单据同样已经把这些字段抄进了明细行。
     * 所有模块（采购入库、出库、调拨、资产、生产）的单据都经由本类保存明细，所以只需在这里挂一次。</p>
     */
    private void markGoodsUsed(DocumentMain document)
    {
        if (document.getDetails() == null || document.getDetails().isEmpty())
        {
            return;
        }
        Set<String> goodsCodes = new LinkedHashSet<String>();
        for (DocumentDetail detail : document.getDetails())
        {
            if (!isBlank(detail.getGoodsCode()))
            {
                goodsCodes.add(detail.getGoodsCode().trim());
            }
        }
        if (!goodsCodes.isEmpty())
        {
            goodsMapper.markGoodsUsed(document.getShopCode(), goodsCodes);
        }
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private void fillDocumentReferenceNames(DocumentMain document)
    {
        if (!isBlank(document.getToWareCode()))
        {
            InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(document.getShopCode(), document.getToWareCode());
            if (warehouse == null) throw new ServiceException("仓库'" + document.getToWareCode() + "'不存在");
            document.setToWareName(warehouse.getWareName());
        }
        if (!isBlank(document.getFromWareCode()))
        {
            InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(document.getShopCode(), document.getFromWareCode());
            if (warehouse == null) throw new ServiceException("仓库'" + document.getFromWareCode() + "'不存在");
            document.setFromWareName(warehouse.getWareName());
        }
        // 三维状态的合法性由枚举保证，显示名称也由枚举派生，不再查表补名
        requireValidState(document.getToState(), "目标库存状态");
        requireValidState(document.getFromState(), "来源库存状态");
    }

    /**
     * 校验三维状态。null 表示该单据不涉及这一侧的状态（如入库单没有来源状态），直接放过；
     * 填了就必须三个维度都是合法枚举值。
     *
     * @param state 三维状态
     * @param label 报错时的字段名称
     */
    private void requireValidState(StockState state, String label)
    {
        if (state != null && !state.isValid())
        {
            throw new ServiceException(label + "不合法：" + state.getLabel()
                    + "，良品状态只能是 1/0，在库状态只能是 1/0，占用类型只能是 OWN/LOAN/REPAIR/PRODUCTION");
        }
    }

    /**
     * 根据单据方向补齐明细仓库、库存状态及其名称，避免代码和冗余名称不一致。
     */
    private void fillDetailReferenceNames(DocumentDetail detail, DocumentMain document, String direction)
    {
        if (isBlank(detail.getWareCode()))
        {
            detail.setWareCode("I".equals(direction) ? document.getToWareCode() : document.getFromWareCode());
        }
        if (!isBlank(detail.getWareCode()))
        {
            InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(
                    document.getShopCode(), detail.getWareCode());
            if (warehouse == null)
            {
                throw new ServiceException("仓库'" + detail.getWareCode() + "'不存在");
            }
            detail.setWareName(warehouse.getWareName());
        }

        StockState detailState = detail.getStockState();
        if (detailState == null || !detailState.isValid())
        {
            detailState = "I".equals(direction) ? document.getToState() : document.getFromState();
            detail.setStockState(detailState);
        }
        requireValidState(detailState, "明细行库存状态");
    }

    /**
     * 明细数量发生变化时，以单价重新计算金额，保证单据、库存成本和流水一致。
     */
    private void recalculateDetailAmounts(DocumentDetail detail)
    {
        if (detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'的数量必须大于0");
        }
        if (detail.getCostPrice() == null)
        {
            detail.setCostPrice(BigDecimal.ZERO);
        }
        if (detail.getCostPrice().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'的成本单价不能小于0");
        }
        detail.setCostAmount(detail.getCostPrice().multiply(detail.getQty()).setScale(2, RoundingMode.HALF_UP));

        if (detail.getSalePrice() != null)
        {
            if (detail.getSalePrice().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("商品'" + detail.getGoodsName() + "'的销售单价不能小于0");
            }
            detail.setSaleAmount(InventoryCostMath.saleAmount(detail.getSalePrice(), detail.getQty()));
        }
    }

    /**
     * 调拨单创建、编辑时根据来源库存/批次重新计算成本，忽略客户端成本字段。
     */
    private void resolveTransferDetailCost(DocumentMain document, DocumentDetail detail)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(document.getShopCode(), detail.getGoodsCode());
        if (goods == null)
        {
            throw new ServiceException("商品'" + detail.getGoodsCode() + "'不存在");
        }
        String fromWareCode = document.getFromWareCode();
        StockState fromState = document.getFromState();
        InventoryStock sourceStock = stockMapper.selectStockByKey(document.getShopCode(),
                detail.getGoodsCode(), fromWareCode, fromState);
        if (sourceStock == null)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'在来源仓库中无库存记录");
        }
        BigDecimal costPrice = costResolver.resolveTransferCostPrice(document.getShopCode(), goods,
                fromWareCode, fromState, detail.getBatchCode(), detail.getSerialNumbers(),
                detail.getQty(), sourceStock);
        detail.setCostPrice(costPrice);
        detail.setCostAmount(costPrice.multiply(detail.getQty()).setScale(2, RoundingMode.HALF_UP));
    }

    private void resolveOutboundDetailCost(DocumentMain document, DocumentDetail detail)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(document.getShopCode(), detail.getGoodsCode());
        if (goods == null)
        {
            throw new ServiceException("商品'" + detail.getGoodsCode() + "'不存在");
        }
        InventoryStock sourceStock = stockMapper.selectStockByKey(document.getShopCode(), detail.getGoodsCode(),
                document.getFromWareCode(), document.getFromState());
        if (sourceStock == null)
        {
            throw new ServiceException("商品'" + detail.getGoodsName() + "'在来源仓库中无库存记录");
        }
        BigDecimal costPrice = costResolver.resolveOutboundCostPrice(document.getShopCode(), goods,
                document.getFromWareCode(), document.getFromState(), detail.getBatchCode(),
                detail.getSerialNumbers(), detail.getQty(), sourceStock);
        detail.setCostPrice(costPrice);
        detail.setCostAmount(costPrice.multiply(detail.getQty()).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 删除单据
     */
    @Override
    @Transactional
    public int deleteDocument(String shopCode, String docCode, String tableSuffix)
    {
        tableSuffix = validateTableSuffix(tableSuffix);
        DocumentMain existing = documentMainMapper.selectDocumentByCodeForUpdate(shopCode, docCode, tableSuffix);
        if (existing == null)
        {
            throw new ServiceException("单据不存在");
        }
        if (!"待审核".equals(existing.getConfirmState()))
        {
            throw new ServiceException("只有待审核状态的单据才能删除");
        }

        documentLockCoordinator.unlockPendingOutboundOrTransfer(existing, documentDetailMapper.selectDetailsByDocCode(shopCode, docCode, tableSuffix));

        // 删除明细
        documentDetailMapper.deleteDetailsByDocCode(shopCode, docCode, tableSuffix);
        // 删除主表
        return documentMainMapper.deleteDocumentByCode(shopCode, docCode, tableSuffix);
    }

    /**
     * 审核单据
     */
    @Override
    @Transactional
    public int confirmDocument(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser)
    {
        tableSuffix = validateTableSuffix(tableSuffix);
        DocumentMain existing = documentMainMapper.selectDocumentByCodeForUpdate(shopCode, docCode, tableSuffix);
        if (existing == null)
        {
            throw new ServiceException("单据不存在");
        }
        if (!"待审核".equals(existing.getConfirmState()))
        {
            throw new ServiceException("只有待审核状态的单据才能审核");
        }

        String confirmState = approved ? "已审核" : "已驳回";

        // 审核通过时执行库存联动
        if (approved)
        {
            existing.setDetails(documentDetailMapper.selectDetailsByDocCode(shopCode, docCode, tableSuffix));
            documentLockCoordinator.acquirePendingOutboundOrTransferLocks(existing);
            postingService.execute(existing, tableSuffix, confirmUser);
        }
        else
        {
            documentLockCoordinator.unlockPendingOutboundOrTransfer(existing,
                    documentDetailMapper.selectDetailsByDocCode(shopCode, docCode, tableSuffix));
        }

        return documentMainMapper.confirmDocument(shopCode, docCode, tableSuffix, confirmState, confirmUser);
    }

    /**
     * 执行库存操作
     */
    /**
     * 获取当前月表后缀
     */
    @Override
    public String getCurrentTableSuffix()
    {
        return tableRouter.currentSuffix();
    }

    /** 根据业务日期计算物理月表后缀。 */
    public String getTableSuffixByBusinessDate(Date businessDate)
    {
        return tableRouter.forBusinessDate(businessDate);
    }

    /**
     * 校验月表后缀格式（仅允许yyyyMM格式的数字字符串）
     *
     * @param tableSuffix 月表后缀
     * @return 校验后的月表后缀，如果为空则返回当前月表后缀
     * @throws ServiceException 如果格式不正确
     */
    public String validateTableSuffix(String tableSuffix)
    {
        return tableRouter.validate(tableSuffix);
    }

    private String generateAssetDocumentCode(String docType)
    {
        if (InventoryDocumentTypes.LOAN.equals(docType))
        {
            return documentCodeGenerator.nextLoanCode();
        }
        if (InventoryDocumentTypes.RETURN.equals(docType))
        {
            return documentCodeGenerator.nextReturnCode();
        }
        if (InventoryDocumentTypes.DAMAGE.equals(docType))
        {
            return documentCodeGenerator.nextDamageCode();
        }
        if (InventoryDocumentTypes.DAMAGE_OUTBOUND.equals(docType))
        {
            return documentCodeGenerator.nextDamageOutboundCode();
        }
        if (InventoryDocumentTypes.REPAIR_OUT.equals(docType))
        {
            return documentCodeGenerator.nextRepairOutCode();
        }
        if (InventoryDocumentTypes.REPAIR_IN.equals(docType))
        {
            return documentCodeGenerator.nextRepairInCode();
        }
        return null;
    }

    private String valueOrDefault(String value, String defaultValue)
    {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private void applyDocumentDefaults(DocumentMain document)
    {
        if (document.getDocDate() == null)
        {
            document.setDocDate(new java.util.Date());
        }
        if (document.getCreateUser() == null || document.getCreateUser().isEmpty())
        {
            document.setCreateUser("system");
        }
        document.setFromWareCode(valueOrDefault(document.getFromWareCode(), ""));
        document.setFromWareName(valueOrDefault(document.getFromWareName(), ""));
        document.setToWareCode(valueOrDefault(document.getToWareCode(), ""));
        document.setToWareName(valueOrDefault(document.getToWareName(), ""));
        // 状态没填时按「良品·在库·自有」兜底；只填了一侧时另一侧跟随
        if (document.getFromState() == null && document.getToState() == null)
        {
            document.setFromState(StockState.available());
            document.setToState(StockState.available());
        }
        else if (document.getFromState() == null)
        {
            document.setFromState(document.getToState());
        }
        else if (document.getToState() == null)
        {
            document.setToState(document.getFromState());
        }
        document.setSourceDocType(valueOrDefault(document.getSourceDocType(), document.getDocType()));
        document.setSourceDocTypeName(valueOrDefault(document.getSourceDocTypeName(), document.getDocTypeName()));
        if (document.getTotalQty() == null)
        {
            document.setTotalQty(BigDecimal.ZERO);
        }
        if (document.getTotalAmount() == null)
        {
            document.setTotalAmount(BigDecimal.ZERO);
        }
    }
}
