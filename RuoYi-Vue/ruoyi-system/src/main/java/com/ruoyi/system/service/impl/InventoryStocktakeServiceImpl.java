package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.ruoyi.system.domain.InventoryStocktake;
import com.ruoyi.system.domain.InventoryStocktakeItem;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryStocktakeMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.IInventorySerialService;
import com.ruoyi.system.service.IInventoryStocktakeService;

/**
 * 库存盘点Service业务层处理
 *
 * <p>盘点单本身不动库存，它只负责「账面多少、实盘多少、谁盘的、谁批的」这条证据链。
 * 审核通过时把差异翻译成两种普通库存单据——盘盈入库 PYRK、盘亏出库 PKCK——
 * 交给和其他单据同一套过账逻辑，成本、批次、序列号、流水全部照常处理，
 * 这里不另写一套改库存的代码。</p>
 *
 * <p>差异以<b>审核那一刻</b>的库存为账面，不以建单快照为准：盘点期间单上的商品是冻结的
 * （见 StocktakeFreezeGuard），两者正常相等；建单那一瞬间恰好有单据过账这种边角情况，
 * 用审核时的库存算才不会把那一笔再算一遍。</p>
 */
@Service
public class InventoryStocktakeServiceImpl implements IInventoryStocktakeService
{
    private static final int INSERT_CHUNK = 500;

    @Autowired
    private InventoryStocktakeMapper stocktakeMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private IInventoryBatchService batchService;

    @Autowired
    private IInventorySerialService serialService;

    @Autowired
    private IDocumentService documentService;

    @Autowired
    private DocumentDetailMapper documentDetailMapper;

    @Autowired
    private InventoryDocumentCodeGenerator codeGenerator;

    @Autowired
    private StaffNameResolver staffNameResolver;

    @Override
    public List<InventoryStocktake> selectStocktakeList(InventoryStocktake query)
    {
        List<InventoryStocktake> list = stocktakeMapper.selectStocktakeList(query);
        if (list != null && !list.isEmpty())
        {
            List<String> logins = new ArrayList<>();
            for (InventoryStocktake item : list)
            {
                logins.add(item.getCreateUser());
                logins.add(item.getConfirmUser());
            }
            Map<String, String> names = staffNameResolver.nickNamesByLogin(logins);
            for (InventoryStocktake item : list)
            {
                item.setCreateUserName(displayName(names, item.getCreateUser()));
                item.setConfirmUserName(displayName(names, item.getConfirmUser()));
            }
        }
        return list;
    }

    @Override
    public InventoryStocktake selectStocktakeDetail(String shopCode, String stocktakeCode)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, false);
        stocktake.setItems(stocktakeMapper.selectItems(shopCode, stocktake.getStocktakeCode()));
        List<String> logins = new ArrayList<>();
        logins.add(stocktake.getCreateUser());
        logins.add(stocktake.getConfirmUser());
        Map<String, String> names = staffNameResolver.nickNamesByLogin(logins);
        stocktake.setCreateUserName(displayName(names, stocktake.getCreateUser()));
        stocktake.setConfirmUserName(displayName(names, stocktake.getConfirmUser()));
        return stocktake;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStocktake createStocktake(String shopCode, CreateCommand command, String username)
    {
        if (command == null || isBlank(command.getWareCode()))
        {
            throw new ServiceException("请选择盘点仓库");
        }
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, command.getWareCode().trim());
        if (warehouse == null)
        {
            throw new ServiceException("仓库'" + command.getWareCode() + "'不存在");
        }
        String scope = isBlank(command.getScope()) ? InventoryStocktake.SCOPE_ALL : command.getScope().trim();
        boolean includeDefective = Boolean.TRUE.equals(command.getIncludeDefective());

        InventoryStocktake stocktake = new InventoryStocktake();
        stocktake.setShopCode(shopCode);
        stocktake.setStocktakeCode(codeGenerator.nextStocktakeCode());
        stocktake.setWareCode(warehouse.getWareCode());
        stocktake.setWareName(warehouse.getWareName());
        stocktake.setScope(scope);
        stocktake.setStatus(InventoryStocktake.STATUS_COUNTING);
        stocktake.setRemark(trimToNull(command.getRemark()));
        stocktake.setCreateUser(username);

        List<InventoryStocktakeItem> items;
        if (InventoryStocktake.SCOPE_ALL.equals(scope))
        {
            items = snapshotWholeWarehouse(stocktake, includeDefective);
            if (items.isEmpty())
            {
                throw new ServiceException("仓库'" + warehouse.getWareName() + "'没有在库库存，全仓盘点没有可盘的商品；"
                        + "要把账上没有的货盘进来，请选「指定商品」");
            }
        }
        else if (InventoryStocktake.SCOPE_GOODS.equals(scope))
        {
            Set<String> goodsCodes = normalizeCodes(command.getGoodsCodes());
            if (goodsCodes.isEmpty())
            {
                throw new ServiceException("指定商品盘点至少要选一个商品");
            }
            items = new ArrayList<>();
            for (String goodsCode : goodsCodes)
            {
                items.add(snapshotItem(stocktake, requireGoods(shopCode, goodsCode), QualityFlag.GOOD.getCode()));
                if (includeDefective)
                {
                    InventoryStock defective = stockMapper.selectStockByKey(shopCode, goodsCode,
                            stocktake.getWareCode(), StockState.of(QualityFlag.DEFECTIVE, OnhandFlag.IN));
                    if (defective != null && positive(defective.getPhysicalQty()))
                    {
                        items.add(snapshotItem(stocktake, requireGoods(shopCode, goodsCode),
                                QualityFlag.DEFECTIVE.getCode()));
                    }
                }
            }
        }
        else
        {
            throw new ServiceException("盘点范围只能是全仓（all）或指定商品（goods）");
        }

        requireNoOtherStocktake(shopCode, stocktake.getWareCode(), goodsCodesOf(items), null);

        stocktake.setLineCount(items.size());
        stocktakeMapper.insertStocktake(stocktake);
        insertItems(items);
        return stocktake;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCounts(String shopCode, String stocktakeCode, List<CountLine> lines)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_COUNTING, "录入实盘数");
        if (lines == null || lines.isEmpty())
        {
            return;
        }
        Map<Long, InventoryStocktakeItem> itemsById = new HashMap<>();
        for (InventoryStocktakeItem item : stocktakeMapper.selectItems(shopCode, stocktake.getStocktakeCode()))
        {
            itemsById.put(item.getId(), item);
        }
        for (CountLine line : lines)
        {
            InventoryStocktakeItem item = line.getId() == null ? null : itemsById.get(line.getId());
            if (item == null)
            {
                throw new ServiceException("盘点明细不存在或不属于本单，请刷新后重试");
            }
            String label = itemLabel(item);
            BigDecimal counted = line.getCountedQty();
            if (counted != null)
            {
                if (counted.signum() < 0)
                {
                    throw new ServiceException(label + "的实盘数量不能小于 0");
                }
                if ("serial".equalsIgnoreCase(item.getBatchStrategy()) && !isInteger(counted))
                {
                    throw new ServiceException(label + "按序列号管理，实盘数量必须是整数");
                }
            }
            if (line.getCostPrice() != null && line.getCostPrice().signum() < 0)
            {
                throw new ServiceException(label + "的盘盈成本单价不能小于 0");
            }
            item.setCountedQty(counted);
            // 没有 price:cost 的库管员拿到的成本价是 null（序列化时抹掉的），原样传回来
            // 不能把建单时带出的均价冲掉，所以 null 一律视为「不改」
            if (line.getCostPrice() != null)
            {
                item.setCostPrice(line.getCostPrice());
            }
            item.setBatchCode(trimToNull(line.getBatchCode()));
            item.setSerialNos(trimToNull(line.getSerialNos()));
            item.setRemark(trimToNull(line.getRemark()));
            stocktakeMapper.updateItemCount(item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItems(String shopCode, String stocktakeCode, List<String> goodsCodes, Integer qualityFlag)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_COUNTING, "添加商品");
        Integer quality = qualityFlag == null ? QualityFlag.GOOD.getCode() : qualityFlag;
        if (QualityFlag.fromCode(quality) == null)
        {
            throw new ServiceException("良品状态只能是 1（良品）或 0（不良品）");
        }
        Set<String> codes = normalizeCodes(goodsCodes);
        if (codes.isEmpty())
        {
            throw new ServiceException("请选择要加入盘点的商品");
        }
        Set<String> existing = new LinkedHashSet<>();
        for (InventoryStocktakeItem item : stocktakeMapper.selectItems(shopCode, stocktake.getStocktakeCode()))
        {
            existing.add(item.getGoodsCode() + "|" + item.getQualityFlag());
        }
        List<InventoryStocktakeItem> items = new ArrayList<>();
        for (String goodsCode : codes)
        {
            InventoryGoods goods = requireGoods(shopCode, goodsCode);
            if (existing.contains(goodsCode + "|" + quality))
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'（" + QualityFlag.getLabel(quality)
                        + "）已经在盘点单上");
            }
            items.add(snapshotItem(stocktake, goods, quality));
        }
        requireNoOtherStocktake(shopCode, stocktake.getWareCode(), codes, stocktake.getStocktakeCode());
        insertItems(items);
        stocktakeMapper.refreshLineCount(shopCode, stocktake.getStocktakeCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(String shopCode, String stocktakeCode, Long itemId)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_COUNTING, "删除明细");
        if (itemId == null || stocktakeMapper.deleteItem(shopCode, stocktake.getStocktakeCode(), itemId) != 1)
        {
            throw new ServiceException("盘点明细不存在或不属于本单，请刷新后重试");
        }
        stocktakeMapper.refreshLineCount(shopCode, stocktake.getStocktakeCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String shopCode, String stocktakeCode, String username)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_COUNTING, "提交");
        List<InventoryStocktakeItem> items = stocktakeMapper.selectItems(shopCode, stocktake.getStocktakeCode());
        if (items.isEmpty())
        {
            throw new ServiceException("盘点单上没有商品，不能提交");
        }
        int uncounted = 0;
        String firstUncounted = null;
        for (InventoryStocktakeItem item : items)
        {
            if (item.getCountedQty() == null)
            {
                uncounted++;
                if (firstUncounted == null) firstUncounted = itemLabel(item);
            }
        }
        if (uncounted > 0)
        {
            throw new ServiceException("还有 " + uncounted + " 行没录实盘数（如" + firstUncounted
                    + "）。没清点的商品请删掉这一行，清点过、数量和账面一致的填账面数");
        }
        // 提交时就把审核会挡的问题先挡掉，别让审核人去催盘点人补序列号
        for (InventoryStocktakeItem item : items)
        {
            validateDifference(stocktake, item, item.getCountedQty().subtract(currentQty(item)));
        }
        if (stocktakeMapper.updateStatus(shopCode, stocktake.getStocktakeCode(), InventoryStocktake.STATUS_COUNTING,
                InventoryStocktake.STATUS_SUBMITTED, username, null) != 1)
        {
            throw new ServiceException("盘点单状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String shopCode, String stocktakeCode, String reason, String username)
    {
        if (isBlank(reason))
        {
            throw new ServiceException("请填写驳回原因，盘点人要照着去复盘");
        }
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_SUBMITTED, "驳回");
        if (stocktakeMapper.updateStatus(shopCode, stocktake.getStocktakeCode(), InventoryStocktake.STATUS_SUBMITTED,
                InventoryStocktake.STATUS_COUNTING, username, reason.trim()) != 1)
        {
            throw new ServiceException("盘点单状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String shopCode, String stocktakeCode, String username)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        String status = stocktake.getStatus();
        if (!InventoryStocktake.STATUS_COUNTING.equals(status) && !InventoryStocktake.STATUS_SUBMITTED.equals(status))
        {
            throw new ServiceException("盘点单" + stocktake.getStocktakeCode() + "当前是「" + status + "」，不能作废");
        }
        if (stocktakeMapper.updateStatus(shopCode, stocktake.getStocktakeCode(), status,
                InventoryStocktake.STATUS_CANCELLED, username, null) != 1)
        {
            throw new ServiceException("盘点单状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStocktake approve(String shopCode, String stocktakeCode, String username)
    {
        InventoryStocktake stocktake = requireStocktake(shopCode, stocktakeCode, true);
        requireStatus(stocktake, InventoryStocktake.STATUS_SUBMITTED, "审核");
        List<InventoryStocktakeItem> items = stocktakeMapper.selectItems(shopCode, stocktake.getStocktakeCode());
        requireNoOtherStocktake(shopCode, stocktake.getWareCode(), goodsCodesOf(items), stocktake.getStocktakeCode());

        // 1. 以当前库存为账面算差异，并逐行校验
        Map<Integer, List<InventoryStocktakeItem>> gainsByQuality = new LinkedHashMap<>();
        Map<Integer, List<InventoryStocktakeItem>> lossesByQuality = new LinkedHashMap<>();
        for (InventoryStocktakeItem item : items)
        {
            if (item.getCountedQty() == null)
            {
                throw new ServiceException(itemLabel(item) + "没有实盘数量，请驳回让盘点人补录");
            }
            BigDecimal book = currentQty(item);
            BigDecimal diff = item.getCountedQty().subtract(book);
            item.setBookQty(book);
            item.setDiffQty(diff);
            item.setDiffAmount(BigDecimal.ZERO.setScale(2));
            validateDifference(stocktake, item, diff);
            if (diff.signum() > 0)
            {
                gainsByQuality.computeIfAbsent(item.getQualityFlag(), k -> new ArrayList<>()).add(item);
            }
            else if (diff.signum() < 0)
            {
                lossesByQuality.computeIfAbsent(item.getQualityFlag(), k -> new ArrayList<>()).add(item);
            }
        }

        // 2. 差异翻译成调整单，建单后同事务审核过账。按良品状态分单：单据表头只有一个库存状态
        List<String> docCodes = new ArrayList<>();
        String tableSuffix = null;
        BigDecimal gainQty = BigDecimal.ZERO;
        BigDecimal gainAmount = BigDecimal.ZERO;
        BigDecimal lossQty = BigDecimal.ZERO;
        BigDecimal lossAmount = BigDecimal.ZERO;
        for (Map.Entry<Integer, List<InventoryStocktakeItem>> entry : gainsByQuality.entrySet())
        {
            DocumentMain document = buildAdjustment(stocktake, entry.getKey(), entry.getValue(), true, username);
            tableSuffix = post(document, username);
            docCodes.add(document.getDocCode());
            Map<String, BigDecimal> postedCost = postedCostByGoods(shopCode, document);
            for (InventoryStocktakeItem item : entry.getValue())
            {
                BigDecimal amount = postedCost.getOrDefault(item.getGoodsCode(), BigDecimal.ZERO);
                item.setDiffAmount(amount);
                gainQty = gainQty.add(item.getDiffQty());
                gainAmount = gainAmount.add(amount);
            }
        }
        for (Map.Entry<Integer, List<InventoryStocktakeItem>> entry : lossesByQuality.entrySet())
        {
            DocumentMain document = buildAdjustment(stocktake, entry.getKey(), entry.getValue(), false, username);
            tableSuffix = post(document, username);
            docCodes.add(document.getDocCode());
            // 盘亏的金额以过账后按实际批次/均价结算出来的成本为准，不是建单时的估算
            Map<String, BigDecimal> postedCost = postedCostByGoods(shopCode, document);
            for (InventoryStocktakeItem item : entry.getValue())
            {
                BigDecimal amount = postedCost.getOrDefault(item.getGoodsCode(), BigDecimal.ZERO);
                item.setDiffAmount(amount.negate());
                lossQty = lossQty.add(item.getDiffQty().negate());
                lossAmount = lossAmount.add(amount);
            }
        }

        // 3. 回写明细和单头
        int diffCount = 0;
        for (InventoryStocktakeItem item : items)
        {
            if (item.getDiffQty().signum() != 0) diffCount++;
            stocktakeMapper.updateItemResult(item);
        }
        stocktake.setDiffCount(diffCount);
        stocktake.setGainQty(gainQty);
        stocktake.setGainAmount(gainAmount);
        stocktake.setLossQty(lossQty);
        stocktake.setLossAmount(lossAmount);
        stocktake.setAdjustDocCodes(docCodes.isEmpty() ? null : String.join(",", docCodes));
        stocktake.setAdjustTableSuffix(tableSuffix);
        stocktake.setConfirmUser(username);
        if (stocktakeMapper.completeStocktake(stocktake) != 1)
        {
            throw new ServiceException("盘点单状态已变化，请刷新后重试");
        }
        return selectStocktakeDetail(shopCode, stocktake.getStocktakeCode());
    }

    // ------------------------------------------------------------------ 快照

    private List<InventoryStocktakeItem> snapshotWholeWarehouse(InventoryStocktake stocktake, boolean includeDefective)
    {
        InventoryStock query = new InventoryStock();
        query.setShopCode(stocktake.getShopCode());
        query.setWareCode(stocktake.getWareCode());
        query.setOnhandFlag(OnhandFlag.IN.getCode());
        List<InventoryStock> stocks = stockMapper.selectStockList(query);
        List<InventoryStocktakeItem> items = new ArrayList<>();
        for (InventoryStock stock : stocks)
        {
            if (!positive(stock.getPhysicalQty()))
            {
                continue;
            }
            if (!includeDefective && !QualityFlag.GOOD.getCode().equals(stock.getQualityFlag()))
            {
                continue;
            }
            InventoryStocktakeItem item = baseItem(stocktake, stock.getGoodsCode(), stock.getGoodsName(),
                    stock.getSpec(), stock.getUnit(), stock.getQualityFlag());
            item.setBookQty(stock.getPhysicalQty());
            item.setCostPrice(stockCostPrice(stock));
            items.add(item);
        }
        // 账面均价为 0 的（比如一直零成本入库）补一个全店均价，免得盘盈按 0 入库
        for (InventoryStocktakeItem item : items)
        {
            if (item.getCostPrice() == null || item.getCostPrice().signum() == 0)
            {
                item.setCostPrice(shopAverageCost(stocktake.getShopCode(), item.getGoodsCode()));
            }
        }
        items.sort((a, b) -> {
            int byGoods = a.getGoodsCode().compareTo(b.getGoodsCode());
            return byGoods != 0 ? byGoods : Integer.compare(b.getQualityFlag(), a.getQualityFlag());
        });
        return items;
    }

    private InventoryStocktakeItem snapshotItem(InventoryStocktake stocktake, InventoryGoods goods, Integer quality)
    {
        InventoryStock stock = stockMapper.selectStockByKey(stocktake.getShopCode(), goods.getGoodsCode(),
                stocktake.getWareCode(), StockState.of(quality, OnhandFlag.IN.getCode()));
        InventoryStocktakeItem item = baseItem(stocktake, goods.getGoodsCode(), goods.getGoodsName(),
                goods.getSpec(), goods.getUnit(), quality);
        item.setBookQty(stock == null || stock.getPhysicalQty() == null ? BigDecimal.ZERO : stock.getPhysicalQty());
        BigDecimal cost = stock == null ? null : stockCostPrice(stock);
        item.setCostPrice(cost == null || cost.signum() == 0
                ? shopAverageCost(stocktake.getShopCode(), goods.getGoodsCode()) : cost);
        return item;
    }

    private InventoryStocktakeItem baseItem(InventoryStocktake stocktake, String goodsCode, String goodsName,
            String spec, String unit, Integer quality)
    {
        InventoryStocktakeItem item = new InventoryStocktakeItem();
        item.setShopCode(stocktake.getShopCode());
        item.setStocktakeCode(stocktake.getStocktakeCode());
        item.setGoodsCode(goodsCode);
        item.setGoodsName(goodsName);
        item.setSpec(spec);
        item.setUnit(unit);
        item.setQualityFlag(quality);
        return item;
    }

    private BigDecimal stockCostPrice(InventoryStock stock)
    {
        if (stock.getAvgCostPrice() != null && stock.getAvgCostPrice().signum() > 0)
        {
            return stock.getAvgCostPrice();
        }
        if (positive(stock.getPhysicalQty()) && stock.getTotalCostAmount() != null)
        {
            return stock.getTotalCostAmount().divide(stock.getPhysicalQty(), 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** 这个商品在全店所有仓库、所有状态下的加权均价；一件都没有时为 0 */
    private BigDecimal shopAverageCost(String shopCode, String goodsCode)
    {
        InventoryStock query = new InventoryStock();
        query.setShopCode(shopCode);
        query.setGoodsCode(goodsCode);
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (InventoryStock stock : stockMapper.selectStockList(query))
        {
            if (positive(stock.getPhysicalQty()) && stock.getTotalCostAmount() != null)
            {
                qty = qty.add(stock.getPhysicalQty());
                amount = amount.add(stock.getTotalCostAmount());
            }
        }
        return qty.signum() > 0 ? amount.divide(qty, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private void insertItems(List<InventoryStocktakeItem> items)
    {
        for (int from = 0; from < items.size(); from += INSERT_CHUNK)
        {
            stocktakeMapper.batchInsertItems(items.subList(from, Math.min(items.size(), from + INSERT_CHUNK)));
        }
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 一行差异能不能落成调整单。提交和审核各跑一遍：提交时先挡住，审核时库存可能又变了。
     *
     * <p>要求照着过账逻辑来：序列号商品进出都得点名是哪几个；个别计价的批次商品
     * 出库得指定批次；盘亏不能吃掉已经被待审核出库单占住的货。</p>
     */
    private void validateDifference(InventoryStocktake stocktake, InventoryStocktakeItem item, BigDecimal diff)
    {
        if (diff.signum() == 0)
        {
            return;
        }
        String label = itemLabel(item);
        String strategy = valueOrDefault(item.getBatchStrategy(), "none").toLowerCase();
        String costing = valueOrDefault(item.getCostCalculation(), "moving_avg").toLowerCase();
        StockState state = StockState.of(item.getQualityFlag(), OnhandFlag.IN.getCode());
        BigDecimal qty = diff.abs();

        if (diff.signum() < 0)
        {
            BigDecimal locked = item.getLockedQty() == null ? BigDecimal.ZERO : item.getLockedQty();
            BigDecimal available = currentQty(item).subtract(locked);
            if (available.compareTo(qty) < 0)
            {
                throw new ServiceException(label + "盘亏 " + strip(qty) + "，但有 " + strip(locked)
                        + " 已被待审核的出库/调拨单占用，盘亏后剩下的货不够那些单发。"
                        + "请先把那些单据驳回或删除，再审核盘点");
            }
        }
        else if (item.getCostPrice() == null)
        {
            throw new ServiceException(label + "盘盈 " + strip(qty) + "，请填写盘盈入库的成本单价");
        }

        if ("serial".equals(strategy))
        {
            if (!isInteger(qty))
            {
                throw new ServiceException(label + "按序列号管理，差异数量必须是整数");
            }
            List<String> serials = parseSerials(item.getSerialNos());
            if (serials.size() != qty.intValue())
            {
                throw new ServiceException(label + (diff.signum() < 0 ? "盘亏 " : "盘盈 ") + strip(qty)
                        + " 个，要填写" + (diff.signum() < 0 ? "找不到的" : "多出来的") + " " + strip(qty)
                        + " 个序列号，当前填了 " + serials.size() + " 个");
            }
            for (String serialNo : serials)
            {
                if (diff.signum() < 0)
                {
                    InventorySerial serial = serialService.selectSerialByContext(stocktake.getShopCode(), serialNo,
                            item.getGoodsCode(), stocktake.getWareCode(), state);
                    if (serial == null || !"instock".equals(serial.getStatus()))
                    {
                        throw new ServiceException("序列号'" + serialNo + "'不在本仓库的" + label + "账上，不能记盘亏");
                    }
                }
                else if (serialService.selectSerialByKey(stocktake.getShopCode(), serialNo) != null)
                {
                    throw new ServiceException("序列号'" + serialNo + "'系统里已经有记录，不能作为盘盈新增；"
                            + "如果它是在别的仓库账上，请用调拨");
                }
            }
            return;
        }
        if (!isBlank(item.getSerialNos()))
        {
            throw new ServiceException(label + "没有启用序列号管理，不用填序列号");
        }
        if (diff.signum() < 0 && "batch".equals(strategy) && "individual".equals(costing))
        {
            if (isBlank(item.getBatchCode()))
            {
                throw new ServiceException(label + "按批次个别计价，盘亏要指定是哪个批次少了");
            }
            InventoryBatch batch = batchService.selectBatchByKey(stocktake.getShopCode(), item.getBatchCode().trim(),
                    item.getGoodsCode(), stocktake.getWareCode(), state);
            if (batch == null || !"active".equals(batch.getStatus()))
            {
                throw new ServiceException(label + "的批次'" + item.getBatchCode() + "'在本仓库不存在或已关闭");
            }
            BigDecimal remain = batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            if (remain.compareTo(qty) < 0)
            {
                throw new ServiceException(label + "的批次'" + item.getBatchCode() + "'只剩 " + strip(remain)
                        + "，不够盘亏 " + strip(qty) + "。跨批次的盘亏请拆成多张盘点单");
            }
        }
    }

    private void requireNoOtherStocktake(String shopCode, String wareCode, Set<String> goodsCodes,
            String excludeStocktakeCode)
    {
        if (goodsCodes.isEmpty())
        {
            return;
        }
        List<InventoryStocktakeItem> hits = stocktakeMapper.selectFrozenGoods(shopCode, wareCode, goodsCodes,
                excludeStocktakeCode);
        if (hits != null && !hits.isEmpty())
        {
            InventoryStocktakeItem hit = hits.get(0);
            throw new ServiceException("商品'" + valueOrDefault(hit.getGoodsName(), hit.getGoodsCode())
                    + "'已经在盘点单 " + hit.getStocktakeCode() + " 上（共 " + hits.size()
                    + " 行冲突），同一个商品同时只能在一张盘点单上");
        }
    }

    // ------------------------------------------------------------------ 调整单

    private DocumentMain buildAdjustment(InventoryStocktake stocktake, Integer quality,
            List<InventoryStocktakeItem> items, boolean gain, String username)
    {
        StockState state = StockState.of(quality, OnhandFlag.IN.getCode());
        DocumentMain document = new DocumentMain();
        document.setShopCode(stocktake.getShopCode());
        document.setDocType(gain ? InventoryDocumentTypes.STOCKTAKE_GAIN : InventoryDocumentTypes.STOCKTAKE_LOSS);
        document.setDocTypeName(gain ? "盘盈入库" : "盘亏出库");
        document.setDocCode(gain ? codeGenerator.nextStocktakeGainCode() : codeGenerator.nextStocktakeLossCode());
        document.setDocDate(new Date());
        if (gain)
        {
            document.setToWareCode(stocktake.getWareCode());
            document.setToState(state);
        }
        else
        {
            document.setFromWareCode(stocktake.getWareCode());
            document.setFromState(state);
        }
        document.setSourceDocCode(stocktake.getStocktakeCode());
        document.setSourceDocType(InventoryDocumentTypes.STOCKTAKE_SOURCE);
        document.setSourceDocTypeName("库存盘点");
        // 制单记盘点人，审核记批准盘点的人，流水上两个人都看得到
        document.setCreateUser(stocktake.getCreateUser());
        document.setHandler(username);
        document.setRemark("盘点单 " + stocktake.getStocktakeCode() + (gain ? " 盘盈" : " 盘亏"));

        List<DocumentDetail> details = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InventoryStocktakeItem item : items)
        {
            String strategy = valueOrDefault(item.getBatchStrategy(), "none").toLowerCase();
            String costing = valueOrDefault(item.getCostCalculation(), "moving_avg").toLowerCase();
            BigDecimal qty = item.getDiffQty().abs();
            BigDecimal costPrice = item.getCostPrice() == null ? BigDecimal.ZERO : item.getCostPrice();

            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(stocktake.getShopCode());
            detail.setGoodsCode(item.getGoodsCode());
            detail.setGoodsName(item.getGoodsName());
            detail.setSpec(item.getSpec());
            detail.setUnit(item.getUnit());
            detail.setWareCode(stocktake.getWareCode());
            detail.setStockState(state);
            detail.setQty(qty);
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP));
            if ("serial".equals(strategy))
            {
                detail.setSerialNumbers(parseSerials(item.getSerialNos()));
            }
            if (gain)
            {
                // 追踪型商品入库必须落批次，否则 FIFO 扣批次、序列号出库找批次都会对不上。
                // 批次号直接用盘点单号，日后在批次列表里一眼看得出这批货是盘出来的
                if ("batch".equals(strategy) || "serial".equals(strategy))
                {
                    detail.setBatchCode(stocktake.getStocktakeCode());
                }
            }
            else if ("batch".equals(strategy) && "individual".equals(costing))
            {
                detail.setBatchCode(item.getBatchCode().trim());
            }
            detail.setRemark("");
            details.add(detail);
            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(detail.getCostAmount());
        }
        document.setDetails(details);
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount);
        return document;
    }

    /** 建单 + 同事务审核过账，返回单据所在月表后缀 */
    private String post(DocumentMain document, String username)
    {
        documentService.insertDocument(document);
        documentService.confirmDocument(document.getShopCode(), document.getDocCode(),
                document.getTableSuffix(), true, username);
        return document.getTableSuffix();
    }

    /** 过账后回读明细：盘亏的成本是审核时按实际批次/均价结算后写回明细的 */
    private Map<String, BigDecimal> postedCostByGoods(String shopCode, DocumentMain document)
    {
        Map<String, BigDecimal> result = new HashMap<>();
        for (DocumentDetail detail : documentDetailMapper.selectDetailsByDocCode(shopCode,
                document.getDocCode(), document.getTableSuffix()))
        {
            BigDecimal amount = detail.getCostAmount() == null ? BigDecimal.ZERO : detail.getCostAmount();
            result.merge(detail.getGoodsCode(), amount, BigDecimal::add);
        }
        return result;
    }

    // ------------------------------------------------------------------ 工具

    private InventoryStocktake requireStocktake(String shopCode, String stocktakeCode, boolean forUpdate)
    {
        if (isBlank(stocktakeCode))
        {
            throw new ServiceException("盘点单号不能为空");
        }
        InventoryStocktake stocktake = forUpdate
                ? stocktakeMapper.selectStocktakeByCodeForUpdate(shopCode, stocktakeCode.trim())
                : stocktakeMapper.selectStocktakeByCode(shopCode, stocktakeCode.trim());
        if (stocktake == null)
        {
            throw new ServiceException("盘点单'" + stocktakeCode + "'不存在");
        }
        return stocktake;
    }

    private void requireStatus(InventoryStocktake stocktake, String expected, String action)
    {
        if (!expected.equals(stocktake.getStatus()))
        {
            throw new ServiceException("盘点单" + stocktake.getStocktakeCode() + "当前是「" + stocktake.getStatus()
                    + "」，只有「" + expected + "」才能" + action);
        }
    }

    private InventoryGoods requireGoods(String shopCode, String goodsCode)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
        if (goods == null)
        {
            throw new ServiceException("商品'" + goodsCode + "'不存在");
        }
        return goods;
    }

    private BigDecimal currentQty(InventoryStocktakeItem item)
    {
        return item.getCurrentQty() == null ? BigDecimal.ZERO : item.getCurrentQty();
    }

    private Set<String> goodsCodesOf(List<InventoryStocktakeItem> items)
    {
        Set<String> codes = new LinkedHashSet<>();
        for (InventoryStocktakeItem item : items)
        {
            codes.add(item.getGoodsCode());
        }
        return codes;
    }

    private Set<String> normalizeCodes(List<String> codes)
    {
        Set<String> result = new LinkedHashSet<>();
        if (codes == null)
        {
            return result;
        }
        for (String code : codes)
        {
            if (!isBlank(code))
            {
                result.add(code.trim());
            }
        }
        return result;
    }

    /** 序列号可以用逗号（中英文）、空格或换行分隔，扫码枪一行一个也能直接贴进来 */
    private List<String> parseSerials(String raw)
    {
        List<String> result = new ArrayList<>();
        if (isBlank(raw))
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

    private String itemLabel(InventoryStocktakeItem item)
    {
        String name = valueOrDefault(item.getGoodsName(), item.getGoodsCode());
        return "商品'" + name + "'" + (QualityFlag.DEFECTIVE.getCode().equals(item.getQualityFlag()) ? "（不良品）" : "");
    }

    private String displayName(Map<String, String> names, String login)
    {
        return login == null ? null : names.getOrDefault(login, login);
    }

    private boolean positive(BigDecimal value)
    {
        return value != null && value.signum() > 0;
    }

    private boolean isInteger(BigDecimal value)
    {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private String strip(BigDecimal value)
    {
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }

    private String valueOrDefault(String value, String defaultValue)
    {
        return isBlank(value) ? defaultValue : value;
    }
}
