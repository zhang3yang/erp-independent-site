package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PriceMaskUtils;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.domain.PurchaseReceive;
import com.ruoyi.system.domain.PurchaseReceiveItem;
import com.ruoyi.system.domain.vo.PurchaseShortageItem;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.mapper.PurchaseReceiveItemMapper;
import com.ruoyi.system.mapper.PurchaseReceiveMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.runner.MonthlyTableInitializer.TableGroup;
import com.ruoyi.system.service.IPurchaseReceiveService;

/**
 * 采购发货实现。见 {@link IPurchaseReceiveService} 的状态机说明。
 */
@Service
public class PurchaseReceiveServiceImpl implements IPurchaseReceiveService
{
    @Autowired private PurchaseReceiveMapper receiveMapper;
    @Autowired private PurchaseReceiveItemMapper itemMapper;
    @Autowired private PurchaseOrderMapper purchaseMapper;
    @Autowired private PurchaseOrderItemMapper purchaseItemMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    @Autowired private InventoryWarehouseMapper warehouseMapper;
    @Autowired private MonthlyTableInitializer monthlyTables;

    /** 行状态：还没入库，计入在途量 */
    public static final String LINE_PENDING = "未处理";
    /** 行状态：已建入库单但还在待审核，在途量已转移到入库单 */
    public static final String LINE_INBOUNDING = "入库中";
    /** 行状态：入库单已审核通过，数量已进采购订单 receivedqty */
    public static final String LINE_STOCKED = "已入库";

    @Override
    public List<PurchaseReceive> selectReceiveList(PurchaseReceive query, String tableSuffix)
    {
        validateSuffix(tableSuffix);
        return receiveMapper.selectList(query, tableSuffix);
    }

    @Override
    public PurchaseReceive selectReceiveDetail(String shopCode, String receiveCode, String tableSuffix)
    {
        validateSuffix(tableSuffix);
        PurchaseReceive result = receiveMapper.selectByCode(shopCode, receiveCode, tableSuffix);
        if (result != null)
        {
            result.setTableSuffix(tableSuffix);
            result.setItems(loadItems(shopCode, receiveCode, tableSuffix));
            resolveHeaderWarehouse(result);
        }
        return result;
    }

    /** 读明细并派生行状态。 */
    private List<PurchaseReceiveItem> loadItems(String shopCode, String receiveCode, String tableSuffix)
    {
        List<PurchaseReceiveItem> items = itemMapper.selectByReceiveCode(shopCode, receiveCode, tableSuffix);
        for (PurchaseReceiveItem item : items)
        {
            item.setLineStatus(resolveLineStatus(item));
        }
        return items;
    }

    /**
     * 行状态判定。不落库，完全由两个已有字段推出来：
     *
     * <pre>
     * actualqty 为空                → 未处理（入库员还没碰过这行）
     * actualqty 有值、明细号为空     → 入库中（入库单已建，还在待审核）
     * inventorydetailcode 非空      → 已入库（入库单审核通过，已过账）
     * </pre>
     */
    private String resolveLineStatus(PurchaseReceiveItem item)
    {
        if (!isBlank(item.getInventoryDetailCode())) return LINE_STOCKED;
        return item.getActualQty() == null ? LINE_PENDING : LINE_INBOUNDING;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceive createReceive(String shopCode, PurchaseReceive command, String username)
    {
        if (command == null || isBlank(command.getPurchaseCode())) throw new ServiceException("来源采购单号不能为空");
        PurchaseOrder order = purchaseMapper.selectPurchaseOrderByCodeForUpdate(shopCode, command.getPurchaseCode().trim());
        if (order == null) throw new ServiceException("采购订单不存在");
        if (!"approved".equals(order.getStatus()) && !"partial_received".equals(order.getStatus()))
        {
            throw new ServiceException("采购订单状态不允许登记发货");
        }

        // 月表按发货日期定。签收时间在发货阶段为空，不能再拿它定表。
        Date sendDate = command.getSendDate() == null ? new Date() : command.getSendDate();
        String suffix = new SimpleDateFormat("yyyyMM").format(sendDate);
        validateSuffix(suffix);
        // 发货日期同样可以填到未来月份，先确认目标月表在，理由同发货批次。
        // 只查不建（事务里做 DDL 会撞 1412），传 PURCHASE_RECEIVE——要的是 u_p_receive 那两张
        monthlyTables.requireTablesFor(suffix, TableGroup.PURCHASE_RECEIVE);

        List<PurchaseOrderItem> sourceItems = purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(shopCode, order.getPurchaseCode());
        validateItems(shopCode, command.getItems(), sourceItems);
        applyHeaderWarehouse(shopCode, command);
        validatePendingQuantities(shopCode, command.getItems(), sourceItems, null);

        BigDecimal total = command.getItems().stream().map(PurchaseReceiveItem::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        command.setShopCode(shopCode);
        command.setPurchaseCode(order.getPurchaseCode());
        command.setReceiveCode(codeGenerator.nextPurchaseReceiveCode());
        command.setStatus(STATUS_PENDING);
        command.setCreateUser(username);
        command.setSendDate(sendDate);
        // 签收人与签收时间由入库员在采购入库时填，这里一律置空，不接受前端传值
        command.setReceiveUser(null);
        command.setReceiveTime(null);
        normalizeFreight(command, order.getFreightType(), null);
        command.setTotalQty(total);
        command.setTableSuffix(suffix);
        receiveMapper.insert(command, suffix);
        prepareItems(command.getItems(), shopCode, command.getReceiveCode());
        itemMapper.batchInsert(command.getItems(), suffix);
        // 寄付的实付运费在这一步就确定了，同步滚进采购订单的实际运费——
        // 到付走入库员那条路径累加，这里不会进来。
        if (command.getFreightAmount().signum() > 0)
        {
            purchaseMapper.increaseActualFreight(shopCode, command.getPurchaseCode(), command.getFreightAmount());
        }
        return command;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDraft(String shopCode, PurchaseReceive command, String tableSuffix, String username)
    {
        validateSuffix(tableSuffix);
        PurchaseReceive old = receiveMapper.selectByCodeForUpdate(shopCode, command.getReceiveCode(), tableSuffix);
        if (old == null) throw new ServiceException("发货单不存在");
        requireAllLinesPending(shopCode, old, tableSuffix, "修改");

        PurchaseOrder order = purchaseMapper.selectPurchaseOrderByCodeForUpdate(shopCode, old.getPurchaseCode());
        if (order == null) throw new ServiceException("来源采购订单不存在");
        List<PurchaseOrderItem> sourceItems = purchaseItemMapper.selectItemsByPurchaseCodeForUpdate(shopCode, order.getPurchaseCode());
        validateItems(shopCode, command.getItems(), sourceItems);
        applyHeaderWarehouse(shopCode, command);
        validatePendingQuantities(shopCode, command.getItems(), sourceItems, old.getReceiveCode());

        itemMapper.deleteByReceiveCode(shopCode, old.getReceiveCode(), tableSuffix);
        command.setShopCode(shopCode);
        command.setPurchaseCode(old.getPurchaseCode());
        command.setReceiveCode(old.getReceiveCode());
        // 发货日期改了也不搬月表：搬表要跨表迁移主子记录，风险远大于收益。
        // 要落到别的月份就取消重建，界面上会提示。
        command.setSendDate(command.getSendDate() == null ? old.getSendDate() : command.getSendDate());
        BigDecimal oldActual = nvl(old.getFreightAmount());
        normalizeFreight(command, order.getFreightType(), old);
        BigDecimal freightDelta = command.getFreightAmount().subtract(oldActual);
        command.setTotalQty(command.getItems().stream().map(PurchaseReceiveItem::getQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        command.setUpdateUser(username);
        prepareItems(command.getItems(), shopCode, old.getReceiveCode());
        if (receiveMapper.updateDraft(command, tableSuffix) != 1) throw new ServiceException("发货单状态已变化，请刷新后重试");
        itemMapper.batchInsert(command.getItems(), tableSuffix);
        // 实付运费按差额改：updateDraft 的更新列里没有 freightamount，改金额或改运费类型
        // 都从这里走，全额覆盖会把入库员累计的钱抹掉。
        if (freightDelta.signum() != 0)
        {
            receiveMapper.applyReceiveInfo(shopCode, old.getReceiveCode(), tableSuffix, null, null, freightDelta, username);
            purchaseMapper.increaseActualFreight(shopCode, old.getPurchaseCode(), freightDelta);
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelReceive(String shopCode, String receiveCode, String tableSuffix, String username)
    {
        validateSuffix(tableSuffix);
        PurchaseReceive receive = receiveMapper.selectByCodeForUpdate(shopCode, receiveCode, tableSuffix);
        if (receive == null) throw new ServiceException("发货单不存在");
        requireAllLinesPending(shopCode, receive, tableSuffix, "取消");
        // 寄付的实付是采购员记在本单上的账，单子取消了这笔账也要从采购订单上撤回。
        // 到付的实付是入库员签收时真付出去的钱，沿用「驳回也不扣回」的口径，不撤。
        BigDecimal recorded = nvl(receive.getFreightAmount());
        if ("prepaid".equals(receive.getFreightType()) && recorded.signum() > 0)
        {
            purchaseMapper.increaseActualFreight(shopCode, receive.getPurchaseCode(), recorded.negate());
        }
        itemMapper.deleteByReceiveCode(shopCode, receiveCode, tableSuffix);
        return receiveMapper.deleteDraft(shopCode, receiveCode, tableSuffix);
    }

    /**
     * 只要有任何一行被入库员碰过，整单就不许改也不许取消。
     *
     * <p>入库员已经按发货单的内容点过货了，这时采购员再改数量或删行，
     * 会让已经生成的入库单对不上来源。要改就取消重开。</p>
     */
    private void requireAllLinesPending(String shopCode, PurchaseReceive receive, String tableSuffix, String action)
    {
        if (!STATUS_PENDING.equals(receive.getStatus()))
        {
            throw new ServiceException("发货单已开始入库，不能" + action + "。如需变更请联系入库员驳回入库单");
        }
        for (PurchaseReceiveItem item : itemMapper.selectByReceiveCode(shopCode, receive.getReceiveCode(), tableSuffix))
        {
            if (!LINE_PENDING.equals(resolveLineStatus(item)))
            {
                throw new ServiceException("商品'" + item.getGoodsName() + "'已开始入库，不能" + action + "整张发货单");
            }
        }
    }

    @Override
    public List<PurchaseReceive> selectPendingShipments(String shopCode)
    {
        List<PurchaseReceive> result = new ArrayList<>();
        for (String suffix : validSuffixes(itemMapper.selectReceiveTableSuffixes()))
        {
            PurchaseReceive query = new PurchaseReceive();
            query.setShopCode(shopCode);
            for (PurchaseReceive row : receiveMapper.selectPendingList(query, suffix))
            {
                row.setTableSuffix(suffix);
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public PurchaseReceive findReceiveAcrossMonths(String shopCode, String receiveCode)
    {
        if (isBlank(receiveCode)) return null;
        for (String suffix : validSuffixes(itemMapper.selectReceiveTableSuffixes()))
        {
            PurchaseReceive hit = receiveMapper.selectByCode(shopCode, receiveCode.trim(), suffix);
            if (hit != null)
            {
                hit.setTableSuffix(suffix);
                hit.setItems(loadItems(shopCode, hit.getReceiveCode(), suffix));
                resolveHeaderWarehouse(hit);
                return hit;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshStatus(String shopCode, String receiveCode, String tableSuffix, String username)
    {
        validateSuffix(tableSuffix);
        List<PurchaseReceiveItem> items = itemMapper.selectByReceiveCode(shopCode, receiveCode, tableSuffix);
        String status = deriveStatus(items);
        receiveMapper.updateStatusUnconditional(shopCode, receiveCode, tableSuffix, status, username);
        return status;
    }

    /**
     * 由明细行派生单头状态。发货单是整单入库的，所以只有两个状态：
     *
     * <pre>
     * 全部行已入库           → 已入库
     * 其余                   → 待入库
     * </pre>
     *
     * <p>「入库单已建、还在待审核」也算待入库：货没过账就还没进仓库，中途驳回会整单退回。
     * 这时整单不能改也不能取消，由 {@link #requireAllLinesPending} 按行状态拦住，
     * 界面上则看单头的 {@code processingLines} 是否大于 0。</p>
     */
    private String deriveStatus(List<PurchaseReceiveItem> items)
    {
        if (items == null || items.isEmpty()) return STATUS_PENDING;
        for (PurchaseReceiveItem item : items)
        {
            if (!LINE_STOCKED.equals(resolveLineStatus(item))) return STATUS_PENDING;
        }
        return STATUS_STOCKED;
    }

    @Override
    public List<PurchaseShortageItem> selectShortageItems(String shopCode, String purchaseCode)
    {
        if (isBlank(purchaseCode)) throw new ServiceException("采购单号不能为空");
        List<PurchaseShortageItem> result = new ArrayList<>();
        for (String suffix : validSuffixes(itemMapper.selectReceiveTableSuffixes()))
        {
            result.addAll(itemMapper.selectShortageByPurchase(shopCode, purchaseCode.trim(), suffix));
        }
        return result;
    }

    /**
     * 归一化运费。运费类型由采购员在采购订单上定、发货单继承，三种类型的金额归属不同：
     *
     * <ul>
     *   <li>包邮：不产生运费，预估与实付都归零，两个角色都不用填。</li>
     *   <li>寄付：供应商发货时运费就付清了，金额当场确定，由采购员直接填实付；
     *       预估对寄付没有意义，归零。入库员那边不再录，否则同一笔钱记两遍。</li>
     *   <li>到付：要等入库员签收付款才知道实付多少，采购员只能填预估，
     *       实付由入库员在采购入库时逐次累加，本方法不碰。</li>
     * </ul>
     *
     * @param old 改单前的发货单，新建时传 null
     */
    private void normalizeFreight(PurchaseReceive command, String orderFreightType, PurchaseReceive old)
    {
        if (isBlank(command.getFreightType())) command.setFreightType(orderFreightType);
        String type = command.getFreightType();
        boolean prepaid = "prepaid".equals(type);
        boolean collect = "collect".equals(type);

        keepMaskedFreight(command, old);
        BigDecimal entered = nvl(prepaid ? command.getFreightAmount() : command.getEstFreightAmount());
        if (entered.signum() < 0) throw new ServiceException("运费不能为负数");
        entered = entered.setScale(2, RoundingMode.HALF_UP);

        command.setEstFreightAmount(collect ? entered : BigDecimal.ZERO);
        command.setFreightAmount(resolveActualFreight(command, old, prepaid, entered));
    }

    /**
     * 本单归一化后应有的实付运费。只有寄付才由采购员定，其余情况一律沿用库里已有的值——
     * 到付的实付是入库员累加出来的（驳回后重建时还留着上次已付的钱），改单不能覆盖。
     */
    private BigDecimal resolveActualFreight(PurchaseReceive command, PurchaseReceive old, boolean prepaid, BigDecimal entered)
    {
        if (prepaid) return entered;
        // 从寄付改成到付或包邮，采购员之前填的那笔要撤掉
        if (old != null && "prepaid".equals(old.getFreightType())) return BigDecimal.ZERO;
        return old == null ? BigDecimal.ZERO : nvl(old.getFreightAmount());
    }

    /**
     * 改单守卫：当前用户看不到成本金额时，运费一律沿用库里的原值。
     *
     * <p>运费在 {@link #normalizeFreight} 里要过一道 {@code nvl}，没有这道守卫，
     * 一个没有 {@code price:cost} 的人改一下数量，回传的 {@code null} 就会被当成 0
     * 落库，把运费清零，还会顺带在 {@code updateDraft} 里按差额冲掉采购订单上的实付运费。
     * 见 {@code docs/金额按岗位可见.md} 第 6.1 节。</p>
     *
     * <p>新建发货单（{@code old == null}）不兜底：库里本来就没有原值，
     * 归零是这张单真实的起点。</p>
     */
    private void keepMaskedFreight(PurchaseReceive command, PurchaseReceive old)
    {
        if (old == null || !PriceMaskUtils.isCostMaskedOnWrite())
        {
            return;
        }
        command.setFreightAmount(old.getFreightAmount());
        command.setEstFreightAmount(old.getEstFreightAmount());
    }

    private static BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 解析单头的建议入库仓库并铺到全部明细行。
     *
     * <p>一张发货单只入一个仓（决议 B6-1），所以仓库是单头字段，界面上也只有一个下拉。
     * 这里把它铺到每一行的 warehousecode——库存那边是按明细行读仓库的，落库结构不变。</p>
     *
     * <p>留空是允许的：采购员未必知道入哪个仓，入库员在采购入库时选即可。</p>
     */
    private void applyHeaderWarehouse(String shopCode, PurchaseReceive command)
    {
        String wareCode = command.getWarehouseCode();
        if (isBlank(wareCode))
        {
            command.setWarehouseCode(null);
            command.setWarehouseName(null);
            for (PurchaseReceiveItem item : command.getItems())
            {
                item.setWarehouseCode(null);
                item.setWarehouseName(null);
            }
            return;
        }
        // 仓库名称一律以商家仓库主数据为准，不信任前端传入的名称
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, wareCode.trim());
        if (warehouse == null) throw new ServiceException("建议入库仓库'" + wareCode.trim() + "'不存在");
        command.setWarehouseCode(warehouse.getWareCode());
        command.setWarehouseName(warehouse.getWareName());
        for (PurchaseReceiveItem item : command.getItems())
        {
            item.setWarehouseCode(warehouse.getWareCode());
            item.setWarehouseName(warehouse.getWareName());
        }
    }

    /** 从明细行反推单头仓库，供查询回显。明细为空或未填仓库时留空。 */
    private void resolveHeaderWarehouse(PurchaseReceive receive)
    {
        if (receive.getItems() == null) return;
        for (PurchaseReceiveItem item : receive.getItems())
        {
            if (!isBlank(item.getWarehouseCode()))
            {
                receive.setWarehouseCode(item.getWarehouseCode());
                receive.setWarehouseName(item.getWarehouseName());
                return;
            }
        }
    }

    private void validateItems(String shopCode, List<PurchaseReceiveItem> items, List<PurchaseOrderItem> sources)
    {
        if (items == null || items.isEmpty()) throw new ServiceException("发货明细不能为空");
        Set<String> codes = new HashSet<>();
        for (PurchaseReceiveItem item : items)
        {
            if (item == null || isBlank(item.getPurchaseDetailCode()) || !codes.add(item.getPurchaseDetailCode()))
            {
                throw new ServiceException("发货明细必须唯一关联采购明细");
            }
            if (item.getQty() == null || item.getQty().signum() <= 0) throw new ServiceException("预计到货数量必须大于0");
            PurchaseOrderItem source = sources.stream()
                    .filter(row -> item.getPurchaseDetailCode().equals(row.getDetailCode())).findFirst().orElse(null);
            if (source == null) throw new ServiceException("采购明细不存在");
            // 发货登记阶段仍按采购合同量拦：采购员照合同报数，报超了是录入错误。
            // 入库阶段则不拦——入库员点的是实物，见 IPurchaseInboundService#createInboundFromShipment。
            if (value(source.getReceivedQty()).add(item.getQty()).compareTo(source.getQty()) > 0)
            {
                throw new ServiceException("商品'" + source.getGoodsName() + "'的预计到货数量超过采购剩余可发数量");
            }
            item.setGoodsCode(source.getGoodsCode());
            item.setGoodsName(source.getGoodsName());
            item.setSpec(source.getSpec());
            item.setUnit(source.getUnit());
            item.setPrice(source.getUnitPrice());
            item.setAmount(source.getUnitPrice().multiply(item.getQty()));
        }
    }

    private void prepareItems(List<PurchaseReceiveItem> items, String shopCode, String receiveCode)
    {
        for (PurchaseReceiveItem item : items)
        {
            item.setShopCode(shopCode);
            item.setReceiveCode(receiveCode);
            item.setDetailCode(codeGenerator.nextPurchaseReceiveDetailCode());
            // 实际到货数量、批次号、序列号都是入库环节的事，发货登记一律不落值。
            // actualqty 必须保持 NULL——行状态判定靠它区分「未入库」和「收到0件」。
            item.setActualQty(null);
            item.setBatchCode(null);
            item.setSerialNumbers(null);
        }
    }

    /**
     * 超收校验。在途量口径见 {@link IPurchaseReceiveService} 的状态机说明：
     * 只有「未处理」的行（actualqty IS NULL）才计在途，
     * 已经被入库单占用的行由入库单的待审核量承担，不在这里重复计算。
     */
    private void validatePendingQuantities(String shopCode, List<PurchaseReceiveItem> items,
            List<PurchaseOrderItem> sources, String excludeReceiveCode)
    {
        Map<String, PurchaseOrderItem> sourceMap = new LinkedHashMap<>();
        for (PurchaseOrderItem source : sources) sourceMap.put(source.getDetailCode(), source);

        // 月表清单只查一次。原来写在 per-item 循环里，明细多时是 N×M 次查询。
        List<String> suffixes = validSuffixes(itemMapper.selectReceiveTableSuffixes());

        for (PurchaseReceiveItem item : items)
        {
            PurchaseOrderItem source = sourceMap.get(item.getPurchaseDetailCode());
            BigDecimal pending = BigDecimal.ZERO;
            for (String suffix : suffixes)
            {
                pending = pending.add(value(itemMapper.selectPendingQty(shopCode,
                        item.getPurchaseDetailCode(), excludeReceiveCode, suffix)));
            }
            if (value(source.getReceivedQty()).add(pending).add(item.getQty()).compareTo(source.getQty()) > 0)
            {
                throw new ServiceException("商品'" + source.getGoodsName() + "'本次发货加在途数量超过采购剩余可发数量");
            }
        }
    }

    private void validateSuffix(String suffix)
    {
        if (suffix == null || !suffix.matches("\\d{6}")) throw new ServiceException("月表后缀必须是yyyyMM");
        int month = Integer.parseInt(suffix.substring(4));
        if (month < 1 || month > 12) throw new ServiceException("月表后缀月份无效");
    }

    /** 过滤出合法的 yyyyMM 月表后缀，避免把未经校验的表名拼进 SQL。 */
    @Override
    public Map<String, BigDecimal> selectPendingQtyByDetails(String shopCode, List<String> purchaseDetailCodes)
    {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (purchaseDetailCodes == null || purchaseDetailCodes.isEmpty()) return result;
        for (String suffix : validSuffixes(itemMapper.selectReceiveTableSuffixes()))
        {
            for (PurchaseReceiveItem row : itemMapper.selectPendingQtyByDetails(shopCode, purchaseDetailCodes, suffix))
            {
                result.merge(row.getPurchaseDetailCode(), value(row.getQty()), BigDecimal::add);
            }
        }
        return result;
    }

    private List<String> validSuffixes(List<String> suffixes)
    {
        List<String> valid = new ArrayList<>();
        if (suffixes == null) return valid;
        for (String suffix : suffixes)
        {
            if (suffix != null && suffix.matches("\\d{6}"))
            {
                int month = Integer.parseInt(suffix.substring(4));
                if (month >= 1 && month <= 12) valid.add(suffix);
            }
        }
        return valid;
    }

    private BigDecimal value(BigDecimal number) { return number == null ? BigDecimal.ZERO : number; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
