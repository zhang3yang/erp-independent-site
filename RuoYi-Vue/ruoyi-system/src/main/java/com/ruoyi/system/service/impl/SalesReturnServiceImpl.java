package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.domain.vo.SalesReturnBatchOption;
import com.ruoyi.system.domain.vo.SalesReturnableItem;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.DocumentDetailMapper;
import com.ruoyi.system.mapper.DocumentMainMapper;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryLedgerMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.service.IDocumentService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.service.ISalesReturnService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 销售退货实现。可退量核算在这里，库存增减一律交给单据引擎。
 */
@Service
public class SalesReturnServiceImpl implements ISalesReturnService
{
    /** 建单后、提交入库前的状态。单据引擎用的就是这个字面量，页面上叫「待提交」 */
    private static final String PENDING_STATE = "待审核";

    @Autowired private ISalesOrderService orderService;
    @Autowired private SalesOrderItemMapper orderItemMapper;
    @Autowired private ShipBatchMapper batchMapper;
    @Autowired private DocumentMainMapper documentMainMapper;
    @Autowired private DocumentDetailMapper documentDetailMapper;
    @Autowired private InventoryGoodsMapper goodsMapper;
    @Autowired private InventoryLedgerMapper ledgerMapper;
    @Autowired private IDocumentService documentService;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;

    /**
     * 横向权限点：查看全部销售退货单。不勾的人只看得到自己建的退货单
     * （{@code u_f_docmain.createuser}）。
     *
     * <p>⚠ 只在销售退货这条服务路径上限归属。退货单是共享的 DocumentMain，
     * 库存的入库/出库/调拨/生产走同一条 mapper 语句，绝不能在那边设 ownerScope。</p>
     *
     * <p>提交（审核即过账）不认归属：那是仓库/入库员做的。</p>
     */
    public static final String PERMI_VIEW_ALL = "order:salesreturn:all";

    /** 横向权限：不是自己建的退货单，看不到也删不了。 */
    private void requireOwnReturn(DocumentMain document)
    {
        if (document == null) return;
        OwnershipScope.requireOwn(document.getCreateUser(), "退货单", document.getDocCode(), PERMI_VIEW_ALL);
    }

    @Override
    public List<DocumentMain> selectReturnList(String shopCode, String docCode, String orderCode,
            String confirmState, String tableSuffix)
    {
        String suffix = MonthTableSuffix.require(tableSuffix);
        DocumentMain query = new DocumentMain();
        query.setShopCode(shopCode);
        query.setDocType(RETURN_DOC_TYPE);
        query.setTableSuffix(suffix);
        query.setDocCode(isBlank(docCode) ? null : docCode.trim());
        query.setSourceDocCode(isBlank(orderCode) ? null : orderCode.trim());
        query.setConfirmState(isBlank(confirmState) ? null : confirmState.trim());
        query.setOwnerScope(OwnershipScope.ownerFilter(PERMI_VIEW_ALL));
        return documentMainMapper.selectDocumentList(query);
    }

    @Override
    public List<DocumentMain> selectPendingReturns(String shopCode)
    {
        List<DocumentMain> result = new ArrayList<>();
        for (String suffix : MonthTableSuffix.validDescending(documentDetailMapper.selectDocumentTableSuffixes()))
        {
            DocumentMain query = new DocumentMain();
            query.setShopCode(shopCode);
            query.setDocType(RETURN_DOC_TYPE);
            query.setConfirmState(PENDING_STATE);
            query.setTableSuffix(suffix);
            query.setOwnerScope(OwnershipScope.ownerFilter(PERMI_VIEW_ALL));
            result.addAll(documentMainMapper.selectDocumentList(query));
        }
        return result;
    }

    @Override
    public DocumentMain selectReturnDetail(String shopCode, String docCode, String tableSuffix)
    {
        String suffix = MonthTableSuffix.require(tableSuffix);
        DocumentMain document = requireReturnDocument(shopCode, docCode, suffix);
        requireOwnReturn(document);
        document.setDetails(documentDetailMapper.selectDetailsByDocCode(shopCode, document.getDocCode(), suffix));
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentMain submitReturn(String shopCode, String docCode, String tableSuffix, String username)
    {
        String suffix = MonthTableSuffix.require(tableSuffix);
        DocumentMain document = requireReturnDocument(shopCode, docCode, suffix);
        if (!PENDING_STATE.equals(document.getConfirmState()))
        {
            throw new ServiceException("退货单" + document.getDocCode() + "已" + document.getConfirmState() + "，不能重复提交");
        }
        // 审核即过账：加库存、处理批次与序列号、写流水，全在这一步的事务里
        documentService.confirmDocument(shopCode, document.getDocCode(), suffix, true, username);
        // 结尾取详情不走 selectReturnDetail：那条路认归属，而审核是仓库/入库员做的，
        // 他们本来就不是这张退货单的创建人，走过去会在最后一步被自己挡掉。
        DocumentMain saved = requireReturnDocument(shopCode, document.getDocCode(), suffix);
        saved.setDetails(documentDetailMapper.selectDetailsByDocCode(shopCode, saved.getDocCode(), suffix));
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReturn(String shopCode, String docCode, String tableSuffix)
    {
        String suffix = MonthTableSuffix.require(tableSuffix);
        DocumentMain document = requireReturnDocument(shopCode, docCode, suffix);
        requireOwnReturn(document);
        if (!PENDING_STATE.equals(document.getConfirmState()))
        {
            throw new ServiceException("退货单" + document.getDocCode() + "已入库，不能删除");
        }
        documentService.deleteDocument(shopCode, document.getDocCode(), suffix);
    }

    /** 取一张退货单并挡住非 XSTHRK 的单据，别让这里变成绕过库存权限查任意单据的口子 */
    private DocumentMain requireReturnDocument(String shopCode, String docCode, String suffix)
    {
        if (isBlank(docCode)) throw new ServiceException("退货单号不能为空");
        DocumentMain document = documentMainMapper.selectDocumentByCode(shopCode, docCode.trim(), suffix);
        if (document == null) throw new ServiceException("退货单不存在");
        if (!RETURN_DOC_TYPE.equals(document.getDocType())) throw new ServiceException("单据不是销售退货单");
        document.setTableSuffix(suffix);
        return document;
    }

    @Override
    public List<SalesReturnableItem> selectReturnableItems(String shopCode, String orderCode)
    {
        SalesOrder order = requireOrder(shopCode, orderCode);
        List<String> shipCodes = shipCodesOf(shopCode, order.getOrderCode());
        Map<String, SalesReturnableItem> returnable = loadReturnable(shopCode, order, shipCodes);
        Map<String, List<SalesReturnBatchOption>> batches = shippedBatches(shopCode, shipCodes);

        List<SalesReturnableItem> items = new ArrayList<>();
        for (SalesReturnableItem item : returnable.values())
        {
            if (item.getReturnableQty().signum() <= 0) continue;
            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, item.getGoodsCode());
            item.setBatchStrategy(goods == null || isBlank(goods.getBatchStrategy())
                    ? "none" : goods.getBatchStrategy().trim().toLowerCase());
            List<SalesReturnBatchOption> options = batches.get(item.key());
            if (options != null) item.setBatchOptions(options);
            // 退款参考 = 退货数量 × 订单售价，是订单币种的钱，把币种一起带给页面配符号
            item.setCurrency(CurrencySupport.require(order.getCurrency()));
            items.add(item);
        }
        return items;
    }

    @Override
    /*
     * ⚠ 隔离级别是刻意降到 READ COMMITTED 的，不是随手写的，改回默认会让下面的行锁失效。
     *
     * 这个方法是「读可退量 → 校验 → 插退货单」的先读后写。默认的 REPEATABLE READ 下，
     * 事务的一致性读视图在**第一次普通读**（requireOrder）时就定死了，之后 loadReturnable
     * 读到的永远是那一刻的快照——即使在中间加了行锁、即使等到了对方提交，也照样看不见
     * 对方刚插进去的退货单，锁等于白拿。
     *
     * READ COMMITTED 下每条普通读各取一次新快照，所以「先拿锁、再读可退量」才真的成立。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DocumentMain createReturn(String shopCode, ReturnCommand command, String username)
    {
        if (command == null || isBlank(command.getOrderCode())) throw new ServiceException("来源销售订单号不能为空");
        if (command.getItems() == null || command.getItems().isEmpty()) throw new ServiceException("退货明细不能为空");

        SalesOrder order = requireOrder(shopCode, command.getOrderCode());

        /*
         * 锁住本订单的明细行，再去算可退量。
         *
         * 可退量 = 已发 − 已退，而「已退」是把退货单据（含待审核）聚合出来的。聚合读不加锁，
         * 插单据插的又是新行，两个并发请求在数据库层面完全不冲突——不锁的话双方都读到
         * 「可退 10」，各插一张 10 件的退货单，审核后库存凭空多 10 件、退款也多算一份。
         *
         * 锁的是订单明细而不是单据表：单据是按月分表的新行，锁不住「还没插进来的那一张」；
         * 订单明细是这条业务链上唯一稳定的那把手。发货 shipBatch 锁的也是这几行，
         * 顺带把「一边发货一边退货」也串起来了——已发量正是可退量的分子。
         */
        orderItemMapper.selectByOrderCodeForUpdate(shopCode, order.getOrderCode(), order.getTableSuffix());

        Map<String, SalesReturnableItem> returnable = loadReturnable(shopCode, order,
                shipCodesOf(shopCode, order.getOrderCode()));

        // 退货单按仓库拆分：入库单的目标仓库是单头字段，一单只能对应一个仓库
        Map<String, List<ReturnItem>> byWarehouse = new LinkedHashMap<>();
        Set<String> lineKeys = new HashSet<>();
        for (ReturnItem item : command.getItems())
        {
            if (item == null || isBlank(item.getGoodsCode())) throw new ServiceException("退货商品不能为空");
            if (isBlank(item.getWarehouseCode())) throw new ServiceException("商品'" + item.getGoodsCode() + "'必须选择退回仓库");
            if (item.getQty() == null || item.getQty().signum() <= 0)
            {
                throw new ServiceException("商品'" + item.getGoodsCode() + "'的退货数量必须大于0");
            }

            String key = item.getGoodsCode().trim() + "@" + item.getWarehouseCode().trim();
            if (!lineKeys.add(key)) throw new ServiceException("商品'" + item.getGoodsCode() + "'在同一仓库不能重复添加");

            SalesReturnableItem source = returnable.get(key);
            if (source == null || source.getReturnableQty().signum() <= 0)
            {
                throw new ServiceException("商品'" + item.getGoodsCode() + "'在该仓库没有可退数量");
            }
            if (item.getQty().compareTo(source.getReturnableQty()) > 0)
            {
                throw new ServiceException("商品'" + source.getGoodsName() + "'退货数量超过可退数量，当前可退："
                        + source.getReturnableQty().stripTrailingZeros().toPlainString());
            }
            byWarehouse.computeIfAbsent(item.getWarehouseCode().trim(), k -> new ArrayList<>()).add(item);
        }

        if (byWarehouse.size() > 1)
        {
            throw new ServiceException("一张退货单只能退回同一仓库，请按仓库分开提交");
        }

        Map.Entry<String, List<ReturnItem>> group = byWarehouse.entrySet().iterator().next();
        List<DocumentDetail> details = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReturnItem item : group.getValue())
        {
            SalesReturnableItem source = returnable.get(item.getGoodsCode().trim() + "@" + group.getKey());
            DocumentDetail detail = new DocumentDetail();
            detail.setShopCode(shopCode);
            detail.setGoodsCode(source.getGoodsCode());
            detail.setGoodsName(source.getGoodsName());
            detail.setSpec(source.getSpec());
            detail.setUnit(source.getUnit());
            detail.setWareCode(group.getKey());
            detail.setQty(item.getQty());
            // 成本原样还回来：单价用原出库的加权实际成本，金额随本次退货数量算
            BigDecimal costPrice = source.getCostPrice() == null ? BigDecimal.ZERO : source.getCostPrice();
            detail.setCostPrice(costPrice);
            detail.setCostAmount(costPrice.multiply(item.getQty()).setScale(2, RoundingMode.HALF_UP));
            detail.setBatchCode(item.getBatchCode());
            detail.setSerialNumbers(item.getSerialNumbers() == null ? new ArrayList<>() : item.getSerialNumbers());
            detail.setRemark(item.getRemark());
            details.add(detail);
            totalQty = totalQty.add(item.getQty());
            totalAmount = totalAmount.add(detail.getCostAmount());
        }

        DocumentMain document = new DocumentMain();
        document.setShopCode(shopCode);
        document.setDocCode(codeGenerator.nextSalesReturnCode());
        document.setDocType(RETURN_DOC_TYPE);
        document.setDocTypeName("销售退货入库");
        document.setDocDate(new Date());
        document.setToWareCode(group.getKey());
        document.setToState(StockState.available());
        // 往来单位记订单上的店铺与买家。订单模块没有客户主档（u_m_partner 未接），
        // 这里先用订单自带的快照，接上客户档案后改成 partnercode 即可。
        document.setPartnerCode(order.getShopStoreCode());
        document.setPartnerName(isBlank(order.getShopStoreName()) ? order.getBuyerUser() : order.getShopStoreName());
        document.setSourceDocCode(order.getOrderCode());
        document.setSourceDocType(InventoryDocumentTypes.SALES_ORDER_SOURCE);
        document.setSourceDocTypeName("销售订单");
        document.setCreateUser(username);
        document.setHandler(username);
        document.setRemark(isBlank(command.getRemark())
                ? "销售退货，来源订单" + order.getOrderCode() : command.getRemark().trim());
        // 表头数量金额只有销售出库会自动汇总，其余类型必须自己填
        document.setTotalQty(totalQty);
        document.setTotalAmount(totalAmount);
        document.setDetails(details);

        // 只建单不过账。货要等「提交退货」审核这张单才真正回库，见 submitReturn
        documentService.insertDocument(document);

        DocumentMain saved = documentService.selectDocumentByCode(shopCode, document.getDocCode(), document.getTableSuffix());
        if (saved != null)
        {
            saved.setTableSuffix(document.getTableSuffix());
            return saved;
        }
        // 退货不回冲订单的 shippedqty，理由见 ISalesReturnService 的类注释
        return document;
    }

    /**
     * 汇总某订单的可退数量，跨全部月表统计。
     *
     * @return key 为「商品编码@仓库编码」
     */
    private Map<String, SalesReturnableItem> loadReturnable(String shopCode, SalesOrder order, List<String> shipCodes)
    {
        Map<String, SalesReturnableItem> result = new LinkedHashMap<>();
        if (shipCodes.isEmpty()) return result;

        for (String suffix : MonthTableSuffix.validDescending(documentDetailMapper.selectDocumentTableSuffixes()))
        {
            for (SalesReturnableItem row : documentDetailMapper.selectShippedQtyByShipCodes(shopCode, shipCodes, suffix))
            {
                SalesReturnableItem item = result.computeIfAbsent(row.key(), key -> newItem(row));
                item.setShippedQty(value(item.getShippedQty()).add(value(row.getShippedQty())));
                item.setCostAmount(value(item.getCostAmount()).add(value(row.getCostAmount())));
            }
        }

        for (String suffix : MonthTableSuffix.validDescending(documentDetailMapper.selectDocumentTableSuffixes()))
        {
            for (SalesReturnableItem row : documentDetailMapper.selectReturnedQtyByOrder(shopCode, order.getOrderCode(), suffix))
            {
                SalesReturnableItem item = result.get(row.key());
                // 只统计还有发货记录的商品；对不上的退货行属于脏数据，忽略但不阻断
                if (item != null)
                {
                    item.setReturnedQty(value(item.getReturnedQty()).add(value(row.getReturnedQty())));
                }
            }
        }

        Map<String, BigDecimal> salePrices = salePrices(shopCode, order);
        for (SalesReturnableItem item : result.values())
        {
            item.setShippedQty(value(item.getShippedQty()));
            item.setReturnedQty(value(item.getReturnedQty()));
            item.setReturnableQty(item.getShippedQty().subtract(item.getReturnedQty()).max(BigDecimal.ZERO));
            // 加权实际成本。出库时可能跨了多个批次、多次发货，逐笔还原没有意义，按总额摊
            item.setCostPrice(item.getShippedQty().signum() > 0
                    ? value(item.getCostAmount()).divide(item.getShippedQty(), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            item.setSalePrice(salePrices.get(item.getGoodsCode()));
        }
        return result;
    }

    /**
     * 该订单已完成的批次号。出库单挂在批次上，不是直接挂订单，而出库单只在批次「完成」时才开。
     *
     * <p><b>销售补发发出去的货不在这里，是有意的。</b>补发的出库单 sourcedoccode 记的是
     * 补发单号、sourcedoctype 是补发，不是发货批次，所以不会被算进「已发」，也就退不了。
     * 补发本身就不改原订单的已发数量与履约状态，把它算进可退量会让「可退 ≤ 已发」这条
     * 恒等式对不上账。补发的货要退，走库存模块直接开入库单。</p>
     */
    private List<String> shipCodesOf(String shopCode, String orderCode)
    {
        List<String> shipCodes = new ArrayList<>();
        for (String suffix : MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes()))
        {
            ShipBatch query = new ShipBatch();
            query.setShopCode(shopCode);
            query.setOrderCode(orderCode);
            query.setStatus(ShipBatch.STATUS_COMPLETED);
            for (ShipBatch batch : batchMapper.selectList(query, suffix))
            {
                shipCodes.add(batch.getShipCode());
            }
        }
        return shipCodes;
    }

    /**
     * 出库时实际动用过的批次，按「商品@仓库」分组。
     *
     * <p>FIFO 出库的批次只在流水上，出库单明细的 batchcode 是空的。</p>
     */
    private Map<String, List<SalesReturnBatchOption>> shippedBatches(String shopCode, List<String> shipCodes)
    {
        Map<String, List<SalesReturnBatchOption>> result = new LinkedHashMap<>();
        if (shipCodes.isEmpty()) return result;
        for (String suffix : MonthTableSuffix.validDescending(ledgerMapper.selectLedgerTableSuffixes()))
        {
            for (SalesReturnBatchOption row : ledgerMapper.selectShippedBatches(shopCode, shipCodes, suffix))
            {
                result.computeIfAbsent(row.key(), key -> new ArrayList<>()).add(row);
            }
        }
        return result;
    }

    /** 订单行售价，只用于页面显示退款参考 */
    private Map<String, BigDecimal> salePrices(String shopCode, SalesOrder order)
    {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (SalesOrderItem item : orderItemMapper.selectByOrderCode(shopCode, order.getOrderCode(), order.getTableSuffix()))
        {
            prices.put(item.getGoodsCode(), item.getPrice());
        }
        return prices;
    }

    private SalesReturnableItem newItem(SalesReturnableItem row)
    {
        SalesReturnableItem item = new SalesReturnableItem();
        item.setGoodsCode(row.getGoodsCode());
        item.setGoodsName(row.getGoodsName());
        item.setSpec(row.getSpec());
        item.setUnit(row.getUnit());
        item.setWarehouseCode(row.getWarehouseCode());
        item.setWarehouseName(row.getWarehouseName());
        item.setShippedQty(BigDecimal.ZERO);
        item.setReturnedQty(BigDecimal.ZERO);
        item.setCostAmount(BigDecimal.ZERO);
        return item;
    }

    private SalesOrder requireOrder(String shopCode, String orderCode)
    {
        if (isBlank(orderCode)) throw new ServiceException("来源销售订单号不能为空");
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, orderCode.trim());
        if (order == null) throw new ServiceException("销售订单不存在");
        return order;
    }

    private BigDecimal value(BigDecimal number) { return number == null ? BigDecimal.ZERO : number; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
