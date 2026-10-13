package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ContactPhoneUtils;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.PriceMaskUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.Buyer;
import com.ruoyi.system.domain.Buyer2;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderDescItem;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.SalesOrderProductionCandidate;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.OrderPlatformMapper;
import com.ruoyi.system.mapper.ProductionOrderMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.service.IBuyerService;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.utils.GoodsSummaryBuilder;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 销售订单实现。状态机见 {@link ISalesOrderService}。
 */
@Service
public class SalesOrderServiceImpl implements ISalesOrderService
{
    @Autowired private SalesOrderMapper orderMapper;
    @Autowired private SalesOrderItemMapper itemMapper;
    @Autowired private InventoryGoodsMapper goodsMapper;
    @Autowired private OrderPlatformMapper platformMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    /** 定制件的专属关系落在批次上：算「备货中」要读，订单终止要松开 */
    @Autowired private IInventoryBatchService batchService;
    /** 审核时判「走现货的明细库存够不够」，够才落已备货。只读，不占也不锁。 */
    @Autowired private InventoryStockMapper stockMapper;
    /** 建单/改草稿时按 buyercode 查客户，把快照拷进订单 */
    @Autowired private IBuyerService buyerService;
    @Autowired private ShipBatchPendingQtyResolver pendingQtyResolver;
    /** 退款时效窗口：最后一笔回款完成后 3 天内才让退，过期的订单不进下拉 */
    @Autowired private RefundWindowResolver refundWindowResolver;
    /**
     * 撤审前要查有没有按本订单开的在制工单。注入 Mapper 而不是 IProductionOrderService：
     * 生产服务已经注入了本服务（完工时回来认领订单），反向注入服务会成环。
     * 与 {@link ShipBatchPendingQtyResolver} 绕开循环依赖是同一个理由。
     */
    @Autowired private ProductionOrderMapper productionOrderMapper;

    /** staffcode 存的是登录账号，列表/详情要显示姓名，经它换 */
    @Autowired private StaffNameResolver staffNameResolver;

    /**
     * 还能继续发货的履约状态，定义在 {@link SalesOrder#isShippableState}。
     * 发货批次那边判断能不能建单、能不能发货用的是同一张表，别在这里另起一份。
     */
    private static final String[] SHIPPABLE_STATES = {
        SalesOrder.STATE_READY, SalesOrder.STATE_PREPARING, SalesOrder.STATE_SHIPPING
    };

    /**
     * 还没过审、可以随便改动的两个状态。
     *
     * <p>改单、提交、删除都认这一个集合：这两个状态都没预占、没发货量、没在途批次，
     * 对下游没有任何牵连。三处用同一份，免得又出现「改不了却提交得了」那种口径分裂。</p>
     */
    private static final List<String> EDITABLE_STATES =
            Arrays.asList(SalesOrder.STATE_DRAFT, SalesOrder.STATE_REJECTED);

    /**
     * 横向权限点：查看全部销售订单。
     *
     * <p>它是「能力」不是「限制」——勾上＝看得到全店的单，不勾＝只看得到归属于自己的。
     * 写成「只看自己」那种限制型权限的话，配错方向的后果是限制没生效，而这种错没人会发现；
     * 写成能力型，配错的后果是「怎么少了一半单子」，一眼就能看出来。</p>
     *
     * <p>归属认的是 {@code u_o_order.staffcode}，建单时固定取登录账号、之后任何改单都动不了它
     * （见 {@code SalesOrderStaffAttributionSqlTest}），所以它是可靠的归属依据。</p>
     */
    public static final String PERMI_VIEW_ALL = "order:sales:all";

    @Override
    public List<SalesOrder> selectOrderList(SalesOrder query, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        resolveStaffNameFilter(query);
        List<SalesOrder> list = orderMapper.selectList(query, tableSuffix);
        for (SalesOrder row : list) row.setTableSuffix(tableSuffix);
        fillStaffNames(list);
        return list;
    }

    /**
     * 把「业务员名称」这个查询条件换成一批登录账号，填进 {@code query.staffCodes}。
     *
     * <p>姓名在 sys_user，不在订单表，所以按姓名筛只能先换成账号再去月表里筛。</p>
     *
     * <p>没填名字时<b>把 staffCodes 清成 null</b>：这个字段是内部查询载体，
     * 而控制器的 {@code SalesOrder query} 是从请求参数绑出来的，前端塞得进来。
     * 一律由这里说了算，免得出现「界面上没填条件，结果被外部参数筛掉了」。</p>
     *
     * <p>名字填了但一个员工都没匹配上时留一个<b>空集合</b>，由 mapper 落成 {@code 1 = 0}。
     * 这里不提前 return 空列表：控制器用了 PageHelper 的 {@code startPage()}，
     * SQL 不执行那个 ThreadLocal 就不会被消费掉。</p>
     */
    private void resolveStaffNameFilter(SalesOrder query)
    {
        query.setStaffCodes(null);
        if (!isBlank(query.getStaffName()))
        {
            query.setStaffCodes(staffNameResolver.loginsByNickNameLike(query.getStaffName()));
        }
        applyOwnOrderScope(query);
    }

    /**
     * 横向权限：没有 {@link #PERMI_VIEW_ALL} 的人，列表里只出现归属于自己的单。
     *
     * <p>业务员只关心自己的单：混着全店的单一是难找，二是容易点错别人的单，
     * 三是同事之间的业绩本来也不该互相看。</p>
     *
     * <p><b>与「按业务员名称搜」是交集，不是覆盖。</b>业务员搜同事的名字，正确结果是
     * 「一条都搜不到」——覆盖成自己的单会让他以为同事名下挂着这些单，比搜不到更糟。
     * 交集为空时留空集合，由 mapper 落成 {@code 1 = 0}，与按姓名搜不中是同一条路径。</p>
     *
     * <p>「业务员账号」那个精确条件不用在这儿管：它在 mapper 里是
     * {@code and staffcode = ?}，与本方法写下的 {@code and staffcode in (?)} 是 AND 关系，
     * 填了别人的账号自然一条都查不出来。</p>
     */
    private void applyOwnOrderScope(SalesOrder query)
    {
        String me = OwnershipScope.ownerFilter(PERMI_VIEW_ALL);
        if (me == null) return;
        List<String> byName = query.getStaffCodes();
        query.setStaffCodes(byName == null || byName.contains(me)
                ? Collections.singletonList(me)
                : Collections.emptyList());
    }

    /**
     * 横向权限：不归自己的单，看不到也动不了。
     *
     * <p>只挡业务员自己那几条路（详情、改单、提交、撤回、关闭、取消、删草稿）。
     * <b>审核类动作和跨模块的调用一律不挡</b>：审核本来就不是下单人自己做的；
     * 而仓库发货、财务回款、生产完工、销售退货、销售补发都要按订单号取业务员的单，
     * 那些人手里没有、也不该有 {@link #PERMI_VIEW_ALL}，挡了就是直接把发货和回款搞停。
     * 那条路走的是 {@code findOrderAcrossMonths}，本方法一概不碰。</p>
     *
     * <p>报错说清楚是归属问题，不含糊成「订单不存在」：同事之间不是防窃取关系，
     * 说不存在只会让人以为单子丢了，跑去问一圈。</p>
     */
    private void requireOwnOrder(SalesOrder order)
    {
        if (order == null) return;
        OwnershipScope.requireOwn(order.getStaffCode(), "订单", order.getOrderCode(), PERMI_VIEW_ALL);
    }

    /**
     * 把 staffcode（建单时的登录账号）换成员工姓名，填进 staffName 供展示。
     *
     * <p><b>不读库里那一列。</b>{@code staffname} 是建表时留的冗余列，历来没被写过值；
     * 就算补上写入，账号改名后也会跟 {@code sys_user} 漂移。姓名一律现查
     * {@code nick_name}，查不到（账号已删）回退显示登录账号，不留空——与库存批次、
     * 货位、台账那几处的口径一致，都走 {@link StaffNameResolver}。</p>
     *
     * <p>一次 in 查完，与订单张数无关。</p>
     */
    private void fillStaffNames(List<SalesOrder> orders)
    {
        if (orders.isEmpty()) return;
        List<String> logins = new ArrayList<>(orders.size());
        for (SalesOrder order : orders) logins.add(order.getStaffCode());
        Map<String, String> nameByLogin = staffNameResolver.nickNamesByLogin(logins);
        for (SalesOrder order : orders)
        {
            String login = order.getStaffCode();
            if (isBlank(login)) continue;
            String name = nameByLogin.get(login);
            order.setStaffName(name == null ? login : name);
        }
    }

    /**
     * 可发货订单及其可发量。整个方法固定 3 类查询：单头一次、明细一次、
     * 各发货批次月表各一次——与订单张数无关。
     */
    @Override
    public List<SalesOrder> selectShippableOrders(String shopCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        List<SalesOrder> orders = orderMapper.selectByStates(shopCode, tableSuffix,
                Arrays.asList(SHIPPABLE_STATES));
        // 横向权限：这是「新建发货批次」那个下拉的数据源，只有 views/order/shipbatch 在用。
        // 不筛的话，业务员看不到同事的订单、却能在这里把同事的订单挑出来发货。
        // 内存里筛就够了：本来就要全量取回来算可发量。
        String owner = OwnershipScope.ownerFilter(PERMI_VIEW_ALL);
        if (owner != null)
        {
            orders.removeIf(order -> !owner.equals(order.getStaffCode()));
        }
        if (orders.isEmpty()) return new ArrayList<>();

        List<String> orderCodes = new ArrayList<>(orders.size());
        for (SalesOrder order : orders)
        {
            order.setTableSuffix(tableSuffix);
            orderCodes.add(order.getOrderCode());
        }

        Map<String, List<SalesOrderItem>> itemsByOrder = new HashMap<>();
        for (SalesOrderItem item : itemMapper.selectByOrderCodes(shopCode, orderCodes, tableSuffix))
        {
            itemsByOrder.computeIfAbsent(item.getOrderCode(), key -> new ArrayList<>()).add(item);
        }
        // 已排进未完成批次的量也算占掉了，只看已发量会把发完的订单留在下拉里。
        // 明细代码全局唯一，一批订单的占用量并在同一个 map 里按明细取就行
        Map<String, BigDecimal> pending = pendingQtyResolver.resolveByOrders(shopCode, orderCodes);

        List<SalesOrder> result = new ArrayList<>();
        for (SalesOrder order : orders)
        {
            List<SalesOrderItem> items = itemsByOrder.get(order.getOrderCode());
            if (items == null) continue;
            boolean anyRemaining = false;
            for (SalesOrderItem item : items)
            {
                BigDecimal remaining = nvl(item.getQty())
                        .subtract(nvl(item.getShippedQty()))
                        .subtract(nvl(pending.get(item.getDetailCode())));
                item.setRemainingQty(remaining);
                if (remaining.signum() > 0) anyRemaining = true;
            }
            if (anyRemaining)
            {
                order.setItems(items);
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public List<SalesOrder> selectReceivableOrders(String shopCode, String tableSuffix)
    {
        List<SalesOrder> result = new ArrayList<>();
        // 传了月份就只看那个月；没传表示全部月份——订单与回款流水常常不在同一个月，
        // 8 月下的单 9 月才到账是常态，只给当月会让业务员在下拉里找不到欠款单
        List<String> suffixes = MonthTableSuffix.isValid(tableSuffix)
                ? Collections.singletonList(tableSuffix)
                : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes());
        for (String suffix : suffixes)
        {
            collectReceivable(shopCode, suffix, result);
        }
        return result;
    }

    @Override
    public List<SalesOrder> selectRefundableOrders(String shopCode, String tableSuffix)
    {
        List<SalesOrder> result = new ArrayList<>();
        List<String> suffixes = MonthTableSuffix.isValid(tableSuffix)
                ? Collections.singletonList(tableSuffix)
                : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes());
        // 一次把全部订单的最后回款时间取出来，避免逐单去扫月表
        Map<String, Date> settled = refundWindowResolver.lastSettledTimes(
                shopCode, FinanceTransaction.BIZ_SALE);
        for (String suffix : suffixes)
        {
            collectRefundable(shopCode, suffix, settled, result);
        }
        return result;
    }

    private void collectRefundable(String shopCode, String tableSuffix,
            Map<String, Date> settled, List<SalesOrder> result)
    {
        // 草稿还没成立，谈不上收款也就谈不上退款；没收过钱的也没什么可退。
        // 已取消的<b>要</b>列出来——取消订单后把客户已付的钱退回去，正是退款最常见的场景。
        // 这两个条件由 SQL 判，只有退款时效要查资金流水，留在这里
        for (SalesOrder order : orderMapper.selectRefundableOrders(shopCode, tableSuffix,
                Collections.singletonList(SalesOrder.STATE_DRAFT)))
        {
            // 退款时效：最后一笔回款完成后 3 天内才能退，过期自动从下拉里消失
            Date last = settled.get(order.getOrderCode());
            if (!refundWindowResolver.withinWindow(last))
            {
                continue;
            }
            order.setTableSuffix(tableSuffix);
            // 让前端能显示「还剩几天」，不用自己再算一遍
            order.setRefundDeadline(refundWindowResolver.deadline(last));
            result.add(order);
        }
    }

    private void collectReceivable(String shopCode, String tableSuffix, List<SalesOrder> result)
    {
        // 草稿还没成立、已取消的作废，都不该出现在回款下拉里；
        // 已收满的也排除掉，再绑一笔只会把订单记成超额收款。三个条件都由 SQL 判：
        // 不传月份时这个查询要落到每一张订单月表上，拉回来再筛的行数随历史数据一起涨
        for (SalesOrder order : orderMapper.selectReceivableOrders(shopCode, tableSuffix,
                Arrays.asList(SalesOrder.STATE_DRAFT, SalesOrder.STATE_CANCELLED)))
        {
            order.setTableSuffix(tableSuffix);
            result.add(order);
        }
    }

    @Override
    public SalesOrder findOrderAcrossMonths(String shopCode, String orderCode)
    {
        if (isBlank(orderCode)) return null;
        for (String suffix : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes()))
        {
            SalesOrder hit = orderMapper.selectByCode(shopCode, orderCode.trim(), suffix);
            if (hit != null)
            {
                hit.setTableSuffix(suffix);
                hit.setItems(itemMapper.selectByOrderCode(shopCode, hit.getOrderCode(), suffix));
                return hit;
            }
        }
        return null;
    }

    @Override
    public List<SalesOrderProductionCandidate> selectProductionCandidates(String shopCode, String tableSuffix, String goodsCode)
    {
        List<SalesOrderProductionCandidate> result = new ArrayList<>();
        // 与可回款订单同口径：给了月份只查那个月，没给就查全部月表。上月下单这月排产是常态
        List<String> suffixes = MonthTableSuffix.isValid(tableSuffix)
                ? Collections.singletonList(tableSuffix)
                : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes());
        for (String suffix : suffixes)
        {
            for (SalesOrderProductionCandidate row : itemMapper.selectProductionCandidates(shopCode, suffix, goodsCode))
            {
                // 后续按明细定位、回写来源都要靠它，SQL 查不出来（表名本身就是月份），在这里补
                row.setTableSuffix(suffix);
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public List<ProductionOrder> selectProductionsByOrder(String shopCode, String orderCode)
    {
        if (isBlank(orderCode)) return new ArrayList<>();
        // 走已经注入的 Mapper，不注入 IProductionOrderService——生产服务反过来注入了本服务，
        // 那样会成环。理由同 assertNoOpenProductions。
        return productionOrderMapper.selectByOrderCode(shopCode, orderCode.trim());
    }

    @Override
    public SalesOrderItem lockOrderItem(String shopCode, String tableSuffix, String detailCode)
    {
        MonthTableSuffix.require(tableSuffix);
        return itemMapper.selectByDetailCodeForUpdate(shopCode, detailCode, tableSuffix);
    }

    @Override
    @Transactional
    public BigDecimal fulfillFromProduction(String shopCode, String orderCode, String tableSuffix,
            String detailCode, BigDecimal wantQty, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        if (nvl(wantQty).signum() <= 0) return BigDecimal.ZERO;

        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        // 订单没了、或者不在「能发货」的状态里：货照样进了库存，只是没人认领。见接口注释，一律不抛。
        // 用白名单而不是排除草稿/已取消：待审核和已驳回的单还没过审，替它们认领正是
        // submitOrder 明令不做的事；已关闭的单剩余部分已经终止，认了也发不出去
        if (order == null || !SalesOrder.isShippableState(order.getOrderState())) return BigDecimal.ZERO;

        SalesOrderItem item = itemMapper.selectByDetailCodeForUpdate(shopCode, detailCode, tableSuffix);
        if (item == null) return BigDecimal.ZERO;

        // 上限是未发量：已经发走的部分不再认领一遍，工单重复通知同一条明细也不会认超
        BigDecimal pending = nvl(item.getQty()).subtract(nvl(item.getShippedQty()));
        if (pending.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal got = pending.compareTo(wantQty) < 0 ? pending : wantQty;

        // 这里不动任何库存，所以不需要 OrderStockLockCoordinator：定制件的专属绑定已经由
        // 生产模块在完工入库时落到批次上了，本方法只认领数量、刷状态。
        // 专属批次备齐的订单会从「备货中」回到「已确认」，由 deriveState 统一判定——
        // 这里不自己拼状态，发货比例和状态是一起落库的，分两处写迟早漂
        refreshShipProgress(shopCode, orderCode, tableSuffix);
        return got;
    }

    @Override
    public SalesOrder selectOrderDetail(String shopCode, String orderCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCode(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order != null)
        {
            order.setTableSuffix(tableSuffix);
            order.setItems(itemMapper.selectByOrderCode(shopCode, orderCode, tableSuffix));
            order.setDescItems(parseDescItems(order.getDescItemsJson()));
            // 同 fillStaffNames：姓名现查，不信库里那一列
            order.setStaffName(staffNameResolver.nickNameOrLogin(order.getStaffCode()));
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createOrder(String shopCode, SalesOrder command, String username)
    {
        return createOrderInState(shopCode, command, username, SalesOrder.STATE_DRAFT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createAndSubmit(String shopCode, SalesOrder command, String username)
    {
        // 直接落待审核，不先插草稿再改状态。submitOrder 比建单多查的两件事——单必须有明细、
        // 只有草稿/已驳回能提交——前者 requireItems 已经查过，后者对一张新单不成立。
        return createOrderInState(shopCode, command, username, SalesOrder.STATE_PENDING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateAndSubmit(String shopCode, SalesOrder command, String tableSuffix, String username)
    {
        // 同一个事务里先改再提：改单失败提交不会发生，提交失败改单跟着回滚。
        // 两个都是本类的 public 方法，自调用不经过代理，但外层事务已经开着，
        // 它们各自的 @Transactional 本来也只是加入而已，没有独立语义要丢。
        updateDraft(shopCode, command, tableSuffix, username);
        return submitOrder(shopCode, command.getOrderCode(), tableSuffix, username);
    }

    /**
     * 建单的公共部分。初始状态由调用方定：「保存为草稿」落草稿，「提交审核」直接落待审核。
     * 两条路的校验一字不差，所以不管走哪条，能落库的单都是审核台上认得的单。
     */
    private SalesOrder createOrderInState(String shopCode, SalesOrder command, String username, String state)
    {
        validateHeader(command);
        // 建单没有「原样保留」一说，停用的平台或店铺一律不许选
        applyPlatformStore(shopCode, command, null);
        applyBuyerSnapshot(shopCode, command);
        List<SalesOrderItem> items = requireItems(command.getItems());
        command.setCurrency(CurrencySupport.require(command.getCurrency()));

        // 订单按 createtime 落表。建单时刻就是当前月，不接受前端指定月份。
        String suffix = MonthTableSuffix.current();

        command.setShopCode(shopCode);
        command.setOrderCode(codeGenerator.nextSalesOrderCode());
        command.setOrderState(state);
        applyExportFields(command);
        // 归属业务员固定取当前登录账号，<b>不接受前端传值</b>。原来是「前端没给才兜底成 username」，
        // 等于谁都能把单挂到别人名下。这个字段以后要用来算提成，是笔钱，不能由请求体决定。
        // 真需要代下单或转单，得另开一个带权限、留痕的动作，不能是建单的副作用。
        command.setStaffCode(username);
        command.setTableSuffix(suffix);
        prepareItems(shopCode, command, items);
        applyAmounts(command, items);
        applyGoodsSummary(command, items);

        orderMapper.insert(command, suffix);
        itemMapper.batchInsert(items, suffix);
        command.setItems(items);
        return command;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDraft(String shopCode, SalesOrder command, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder old = orderMapper.selectByCodeForUpdate(shopCode, command.getOrderCode(), tableSuffix);
        requireOwnOrder(old);
        if (old == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_DRAFT.equals(old.getOrderState())
                && !SalesOrder.STATE_REJECTED.equals(old.getOrderState()))
        {
            throw new ServiceException("只有草稿和已驳回的订单可以修改，当前状态："
                    + old.getOrderState() + "。已审核的订单请先撤审");
        }
        validateHeader(command);
        // 带上原单：平台/店铺没换的话，就算它在建单之后被停用了也照样让改
        applyPlatformStore(shopCode, command, old);
        applyBuyerSnapshot(shopCode, command);
        List<SalesOrderItem> items = requireItems(command.getItems());
        // 币种只在「从没提交过、也没收过钱」的草稿上可改。
        // 一旦提交，客户看到的报价、财务按币种筛出来的可回款订单都以它为准；
        // 一旦收过钱，已收金额是按原币种记的，改币种等于把两种钱混在一个字段里。
        // 已驳回的订单也算提交过，退回来改的是内容不是币种，要换币种只能重开一单。
        command.setCurrency(CurrencySupport.require(command.getCurrency()));
        if (!command.getCurrency().equals(CurrencySupport.require(old.getCurrency())))
        {
            if (nvl(old.getPaidAmount()).signum() != 0)
            {
                throw new ServiceException("订单已有收款记录，币种不能修改；请改用同币种或另开新单");
            }
            if (!SalesOrder.STATE_DRAFT.equals(old.getOrderState()))
            {
                throw new ServiceException("订单已提交审核（当前状态：" + old.getOrderState()
                        + "），币种不能修改；如需换币种请另开新单");
            }
        }

        command.setShopCode(shopCode);
        command.setOrderCode(old.getOrderCode());
        command.setTableSuffix(tableSuffix);
        applyExportFields(command);
        List<SalesOrderItem> oldItems = itemMapper.selectByOrderCode(shopCode, old.getOrderCode(), tableSuffix);
        // 金额守卫要在 prepareItems 之前：那里的 nvl 会把无权用户回传的 null 当成 0，
        // 再经 applyAmounts 汇总，整单货款归零。
        keepMaskedAmounts(command, old, items, oldItems);
        // 没换商品的行沿用原明细代码，别让外部记着这些代码的历史记录（已取消的批次、
        // 关掉的预占行、绑在批次上的定制归属）在改一次单之后全部指向不存在的行
        prepareItems(shopCode, command, items, detailCodesByGoods(oldItems));
        applyAmounts(command, items);
        applyGoodsSummary(command, items);

        if (orderMapper.updateDraft(command, tableSuffix) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        // 草稿没有预占也没有发货量，明细可以整体重建
        itemMapper.deleteByOrderCode(shopCode, old.getOrderCode(), tableSuffix);
        itemMapper.batchInsert(items, tableSuffix);
        releaseOwnedBatchesOfDroppedItems(shopCode, oldItems, items);
        return 1;
    }

    /**
     * 改单时被丢掉的明细，松开它们名下的定制货。
     *
     * <p>改单是「删掉全部明细再整体重建」，换过商品的行会拿到新的明细代码，旧代码从此
     * 不指向任何行。定制批次是绑在明细代码上的，不放开就成了死货——仓库里躺着一批
     * 谁都发不出去、也查不到归属的成品。</p>
     *
     * <p>能走到这里说明订单是草稿（改单只允许草稿/已驳回），照理不该有定制货。但撤审会把
     * 确认过的单退回草稿，那时定制件可能已经做出来入库了，所以这一步不能省。</p>
     */
    private void releaseOwnedBatchesOfDroppedItems(String shopCode, List<SalesOrderItem> oldItems,
            List<SalesOrderItem> newItems)
    {
        Set<String> kept = new HashSet<>();
        for (SalesOrderItem item : newItems) kept.add(item.getDetailCode());
        for (SalesOrderItem item : oldItems)
        {
            if (!kept.contains(item.getDetailCode()))
            {
                batchService.releaseOwner(shopCode, item.getDetailCode());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder confirmOrder(String shopCode, String orderCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (order == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_PENDING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有待审核订单可以审核通过，当前状态：" + order.getOrderState()
                    + (SalesOrder.STATE_DRAFT.equals(order.getOrderState()) ? "。请先提交审核" : ""));
        }

        List<SalesOrderItem> items = itemMapper.selectByOrderCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (items.isEmpty()) throw new ServiceException("订单没有明细，不能确认");

        // 确认不再碰库存，所以也不需要 OrderStockLockCoordinator.lockForConfirm——
        // 那道锁是为「逐条 reserve」准备的，现在这一步一行库存都不动，没有可锁的东西。
        // deriveState 只在「已备货及之后」的状态里改，从待审核推第一步得在这里自己判：
        // 有明细还没着落就直接落到备货中，业务员一眼看出这单还得等。
        boolean awaiting = false;
        for (SalesOrderItem item : items)
        {
            if (isAwaitingSupply(shopCode, item)) { awaiting = true; break; }
        }

        String state = awaiting ? SalesOrder.STATE_PREPARING : SalesOrder.STATE_READY;
        if (orderMapper.updateOrderState(shopCode, orderCode, tableSuffix, state, SalesOrder.STATE_PENDING) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return selectOrderDetail(shopCode, orderCode, tableSuffix);
    }

    /**
     * 这条明细的货还没着落——「备货中」的判据，分两路：
     *
     * <ul>
     *   <li><b>定制件</b>：只认为它专门做出来的<b>专属批次</b>，够本行未发量才算有着落。
     *       通用库存里的同款货顶不上客户要的规格，有多少都不算数；</li>
     *   <li><b>普通件</b>：查仓库可用库存，够本行未发量就算有着落。</li>
     * </ul>
     *
     * <p>普通件查的是<b>全部仓库</b>的可用量合计，不挑仓——订单上没有仓库字段，
     * 发哪个仓是建发货批次时才定的。可用量口径与库存页一致：物理 − 锁定。</p>
     *
     * <p>这只是审核那一刻的快照。订单不占库存，查到够也不代表留得住，那批货随时可能
     * 被别的单先发走；反过来备货中的单中途来了货也能发。真正的判定在出库单锁定那一步。</p>
     */
    private boolean isAwaitingSupply(String shopCode, SalesOrderItem item)
    {
        BigDecimal pending = nvl(item.getQty()).subtract(nvl(item.getShippedQty()));
        if (pending.signum() <= 0) return false;

        // 定制件只认为它专门做出来的那批货，通用库存有多少都不算数
        if (Boolean.TRUE.equals(item.getCustomMade()))
        {
            return ownedBatchQty(shopCode, item.getDetailCode()).compareTo(pending) < 0;
        }
        return availableStockQty(shopCode, item.getGoodsCode()).compareTo(pending) < 0;
    }

    /** 这个商品在所有仓库的可用库存合计（良品·在库，物理 − 锁定）。 */
    private BigDecimal availableStockQty(String shopCode, String goodsCode)
    {
        if (isBlank(goodsCode)) return BigDecimal.ZERO;
        InventoryStock query = new InventoryStock();
        query.setShopCode(shopCode);
        query.setGoodsCode(goodsCode);
        query.setStockState(StockState.available());
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryStock stock : stockMapper.selectStockList(query))
        {
            BigDecimal available = stock.getAvailableQty();
            // 单个仓库可用量为负（锁定量大于物理量）不往下拖别的仓的可用量
            if (available.signum() > 0) total = total.add(available);
        }
        return total;
    }

    /**
     * 松开这张订单名下全部定制货的专属绑定，退回通用库存。
     *
     * <p>订单关闭或取消时调。那批货确实是为这张单做的，但订单终止了，不放开的话它会
     * 一直挂在仓库里没人能发——留一批永远发不掉的货比让别的单用掉更糟。</p>
     */
    private void releaseOwnedBatches(String shopCode, String orderCode, String tableSuffix)
    {
        for (SalesOrderItem item : itemMapper.selectByOrderCode(shopCode, orderCode, tableSuffix))
        {
            batchService.releaseOwner(shopCode, item.getDetailCode());
        }
    }

    /** 这条明细名下专属批次的剩余总量，即「专门为它做出来、还没发走」的货有多少。 */
    private BigDecimal ownedBatchQty(String shopCode, String detailCode)
    {
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryBatch batch : batchService.selectBatchesByOwner(shopCode, detailCode))
        {
            total = total.add(nvl(batch.getRemainQty()));
        }
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitOrder(String shopCode, String orderCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_DRAFT.equals(order.getOrderState())
                && !SalesOrder.STATE_REJECTED.equals(order.getOrderState()))
        {
            throw new ServiceException("只有草稿和已驳回的订单可以提交，当前状态：" + order.getOrderState());
        }
        // 提交前挡住空单：不然一张没有明细的单能一路走到审核台上才被发现
        if (itemMapper.selectByOrderCode(shopCode, orderCode, tableSuffix).isEmpty())
        {
            throw new ServiceException("订单没有明细，不能提交");
        }
        // 提交不占库存，预占留到审核通过那一刻，见 SalesOrder.STATE_PENDING 的注释
        if (orderMapper.updateStateWithReason(shopCode, orderCode, tableSuffix, SalesOrder.STATE_PENDING,
                Arrays.asList(SalesOrder.STATE_DRAFT, SalesOrder.STATE_REJECTED), null, null) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectOrder(String shopCode, String orderCode, String tableSuffix, String reason, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        if (isBlank(reason)) throw new ServiceException("驳回原因必须填写");
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (order == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_PENDING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有待审核订单可以驳回，当前状态：" + order.getOrderState());
        }
        // 待审核没占过库存，驳回不需要释放任何东西
        if (orderMapper.updateStateWithReason(shopCode, orderCode, tableSuffix, SalesOrder.STATE_REJECTED,
                Collections.singletonList(SalesOrder.STATE_PENDING), reason.trim(), null) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdrawOrder(String shopCode, String orderCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_PENDING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有待审核订单可以撤回，当前状态：" + order.getOrderState());
        }
        // 待审核没占过库存（见 submitOrder），也不可能有在途批次或在制工单——
        // 那些都要过审之后才建得出来。所以撤回只改状态，没有要释放的东西。
        // 两个原因字段都传 null：mapper 里是 <if test="... != null"> ，null 表示不动这一列。
        // 与「重新提交不清空驳回原因」同口径——上一轮被驳回的理由留着，正好对照这轮改了什么。
        if (orderMapper.updateStateWithReason(shopCode, orderCode, tableSuffix, SalesOrder.STATE_DRAFT,
                Collections.singletonList(SalesOrder.STATE_PENDING), null, null) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unconfirmOrder(String shopCode, String orderCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (order == null) throw new ServiceException("订单不存在");
        // 备货中一样能撤审。审核不占库存，「货还没凑齐」只是审核那一刻的快照，
        // 不该变成终止订单的门槛——销售把单提给仓库就算交差了。真正拦得住的是
        // 已经派出去的活儿：在途批次和在制工单，由下面两道挡板管。
        if (!SalesOrder.STATE_READY.equals(order.getOrderState())
                && !SalesOrder.STATE_PREPARING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有已备货、备货中的订单可以撤审，当前状态：" + order.getOrderState());
        }
        // 发出去的货收不回来，撤审后订单会退回草稿允许改数量，那时已发量就成了对不上的账
        for (SalesOrderItem item : itemMapper.selectByOrderCodeForUpdate(shopCode, orderCode, tableSuffix))
        {
            if (nvl(item.getShippedQty()).signum() > 0)
            {
                throw new ServiceException("订单已发出部分货物，不能撤审。如需终止请用「关闭订单」");
            }
        }
        // 批次只是排产计划、不占库存，但撤审后订单退回草稿、明细会被整体重建，
        // 批次上记的 orderdetailcode 就会指向不存在的行。所以要求先把在途批次清掉。
        assertNoPendingBatches(shopCode, orderCode, "撤审");
        assertNoOpenProductions(shopCode, orderCode, "撤审");
        if (orderMapper.updateStateWithReason(shopCode, orderCode, tableSuffix, SalesOrder.STATE_DRAFT,
                Arrays.asList(SalesOrder.STATE_READY, SalesOrder.STATE_PREPARING), null, null) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        // 发货进度归零：撤审后这张单回到没确认过的样子
        orderMapper.updateShipProgress(shopCode, orderCode, tableSuffix, BigDecimal.ZERO, SalesOrder.STATE_DRAFT);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeOrder(String shopCode, String orderCode, String tableSuffix, String reason, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        if (isBlank(reason)) throw new ServiceException("关闭原因必须填写");
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order == null) throw new ServiceException("订单不存在");
        if (!SalesOrder.STATE_READY.equals(order.getOrderState())
                && !SalesOrder.STATE_PREPARING.equals(order.getOrderState())
                && !SalesOrder.STATE_SHIPPING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有已备货、备货中、发货中的订单可以关闭，当前状态："
                    + order.getOrderState());
        }
        // 在途批次必须先清掉。关闭后订单不再是可发货状态，那些批次也发不出去了，
        // 留着只会变成永远确认不了的僵尸行；更要紧的是别让它们绕过状态门禁把货发走。
        // 这两道先于下面「有没有发过货」：在途的活儿不管走关闭还是取消都得先收拾，先说这个。
        assertNoPendingBatches(shopCode, orderCode, "关闭");
        assertNoOpenProductions(shopCode, orderCode, "关闭");
        // 关闭只给发过货的单：已发部分是既成事实，关闭终止的是剩余部分。一件没发的单
        // 该走「取消」——每个状态只留一个终止动作，界面也只显示那一个（index.vue 的 canClose）。
        // 已备货 / 备货中仍在上面的允许列表里，是给「批次刚完成、进度还没重算」那一瞬用的，
        // 真正的门是这道有没有发过货。
        boolean shipped = false;
        for (SalesOrderItem item : itemMapper.selectByOrderCode(shopCode, orderCode, tableSuffix))
        {
            if (nvl(item.getShippedQty()).signum() > 0)
            {
                shipped = true;
                break;
            }
        }
        if (!shipped)
        {
            throw new ServiceException("订单还没发过货，不用关闭，请用「取消」终止");
        }
        // 已发的部分是既成事实，要退走销售退货；关闭只终止剩余部分
        // 为这张单做的定制货退回通用库存：订单终止了，不放开的话那批货永远没人能发
        releaseOwnedBatches(shopCode, orderCode, tableSuffix);
        if (orderMapper.updateStateWithReason(shopCode, orderCode, tableSuffix, SalesOrder.STATE_CLOSED,
                Arrays.asList(SalesOrder.STATE_READY, SalesOrder.STATE_PREPARING, SalesOrder.STATE_SHIPPING),
                null, reason.trim()) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(String shopCode, String orderCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order == null) throw new ServiceException("订单不存在");
        if (SalesOrder.STATE_CANCELLED.equals(order.getOrderState())) throw new ServiceException("订单已取消");
        if (SalesOrder.STATE_FINISHED.equals(order.getOrderState())) throw new ServiceException("订单已完成，不能取消");
        if (SalesOrder.STATE_CLOSED.equals(order.getOrderState())) throw new ServiceException("订单已关闭，不能取消");
        // 白名单只有已备货和备货中：每个状态只留一个终止动作。草稿、已驳回用「删除」，
        // 待审核用「撤回」，发过货的用「关闭」，界面按同一张表显示按钮（index.vue 的 canCancel）。
        // 备货中照样放行：货没凑齐只是审核那一刻的快照，不是终止订单的门槛。
        if (!SalesOrder.STATE_READY.equals(order.getOrderState())
                && !SalesOrder.STATE_PREPARING.equals(order.getOrderState()))
        {
            throw new ServiceException("只有已备货、备货中的订单可以取消，当前状态：" + order.getOrderState()
                    + "。草稿和已驳回的单请直接删除，待审核的用「撤回」；"
                    + "发过货的单已发部分走销售退货，剩余部分用「关闭订单」终止");
        }

        // 发出去的货收不回来，取消只能走退货流程，不能直接把订单抹掉。
        // 发过货的单按 deriveState 本该已经是发货中，这里是兜底，顺带锁住明细行。
        for (SalesOrderItem item : itemMapper.selectByOrderCodeForUpdate(shopCode, orderCode, tableSuffix))
        {
            if (nvl(item.getShippedQty()).signum() > 0)
            {
                throw new ServiceException("订单已发出部分货物，不能取消。已发的部分走销售退货，"
                        + "剩余部分用「关闭订单」终止");
            }
        }
        // 同「关闭」：取消后订单不再可发货，在途批次留着既发不出去也没人清
        assertNoPendingBatches(shopCode, orderCode, "取消");
        assertNoOpenProductions(shopCode, orderCode, "取消");
        releaseOwnedBatches(shopCode, orderCode, tableSuffix);
        if (orderMapper.updateOrderState(shopCode, orderCode, tableSuffix,
                SalesOrder.STATE_CANCELLED, order.getOrderState()) != 1)
        {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDraft(String shopCode, String orderCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        requireOwnOrder(order);
        if (order == null) throw new ServiceException("订单不存在");
        // 与「可修改」同一个集合：草稿和已驳回都还没过审、没预占、没发过货，删掉不牵连任何下游。
        // 已驳回原先删不掉，只能改完重提或者走取消——取消会留下一张「已取消」的废单占着列表，
        // 而这张单本来就没成立过，没有留痕的价值。
        if (!EDITABLE_STATES.contains(order.getOrderState()))
        {
            throw new ServiceException("只有草稿和已驳回的订单可以删除，当前状态：" + order.getOrderState()
                    + "。已过审的订单请用「撤审」或「关闭订单」");
        }
        // 明细一删，绑在它上面的定制货就再也查不到归属了，先放开。
        // 草稿照理不该有定制货，但撤审会把确认过的单退回草稿，那时可能已经做出来入库了
        releaseOwnedBatches(shopCode, orderCode, tableSuffix);
        itemMapper.deleteByOrderCode(shopCode, orderCode, tableSuffix);
        return orderMapper.deleteDraft(shopCode, orderCode, tableSuffix, EDITABLE_STATES);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateItemProductionOrder(String shopCode, String orderCode, String tableSuffix,
            String detailCode, String productionCode)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrderItem item = itemMapper.selectByDetailCodeForUpdate(shopCode, detailCode, tableSuffix);
        if (item == null || !orderCode.equals(item.getOrderCode()))
        {
            throw new ServiceException("订单明细不存在");
        }
        return itemMapper.updateSourceCode(shopCode, detailCode, tableSuffix, productionCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyPayment(String shopCode, String orderCode, String tableSuffix, BigDecimal paidDelta,
            Date tradeTime)
    {
        MonthTableSuffix.require(tableSuffix);
        if (paidDelta == null || paidDelta.signum() == 0) throw new ServiceException("回款金额不能为零");
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (order == null) throw new ServiceException("订单不存在");
        // 已取消的订单不能再收钱，但要能退钱：取消订单后把客户已付的款退回去是退款最常见的场景
        if (SalesOrder.STATE_CANCELLED.equals(order.getOrderState()) && paidDelta.signum() > 0)
        {
            throw new ServiceException("订单已取消，不能登记回款");
        }

        BigDecimal after = nvl(order.getPaidAmount()).add(paidDelta);
        if (after.signum() < 0) after = BigDecimal.ZERO;
        String payStatus = derivePayStatus(after, nvl(order.getTotalAmount()));
        return orderMapper.applyPayment(shopCode, orderCode, tableSuffix, paidDelta,
                payStatus, toReceiveStatus(payStatus), tradeTime == null ? new Date() : tradeTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshShipProgress(String shopCode, String orderCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        SalesOrder order = orderMapper.selectByCodeForUpdate(shopCode, orderCode, tableSuffix);
        if (order == null) throw new ServiceException("订单不存在");

        List<SalesOrderItem> items = itemMapper.selectByOrderCode(shopCode, orderCode, tableSuffix);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal shippedQty = BigDecimal.ZERO;
        boolean awaiting = false;
        for (SalesOrderItem item : items)
        {
            totalQty = totalQty.add(nvl(item.getQty()));
            shippedQty = shippedQty.add(nvl(item.getShippedQty()));
            if (!awaiting && isAwaitingSupply(shopCode, item)) awaiting = true;
        }

        BigDecimal percent = totalQty.signum() <= 0 ? BigDecimal.ZERO
                : shippedQty.multiply(HUNDRED).divide(totalQty, 2, RoundingMode.HALF_UP);
        String state = deriveState(order.getOrderState(), shippedQty, totalQty, awaiting);
        orderMapper.updateShipProgress(shopCode, orderCode, tableSuffix, percent, state);
        return state;
    }

    /**
     * 由发货进度推履约状态。
     *
     * <p>只在「已备货 / 备货中 / 发货中」之间以及走向「已完成」时改。审核链上的
     * 草稿 / 待审核 / 已驳回，以及两个终态已取消 / 已关闭，都是另一条路径上的状态，
     * 不该被发货进度带偏——尤其是<b>已关闭</b>：关闭时往往已经发过一部分，
     * 让它参与派生会立刻被算回「发货中」，人工终止就白做了。</p>
     *
     * @param awaitingSupply 还有明细的货没着落，见 {@link #isAwaitingSupply}。
     *        已确认 ⇄ 备货中 靠它来回走：现货来了、定制批次备齐了就回已确认。
     */
    private String deriveState(String current, BigDecimal shippedQty, BigDecimal totalQty,
            boolean awaitingSupply)
    {
        if (SalesOrder.STATE_DRAFT.equals(current) || SalesOrder.STATE_CANCELLED.equals(current)
                || SalesOrder.STATE_PENDING.equals(current) || SalesOrder.STATE_REJECTED.equals(current)
                || SalesOrder.STATE_CLOSED.equals(current))
        {
            return current;
        }
        if (totalQty.signum() > 0 && shippedQty.compareTo(totalQty) >= 0) return SalesOrder.STATE_FINISHED;
        if (shippedQty.signum() > 0) return SalesOrder.STATE_SHIPPING;
        return awaitingSupply ? SalesOrder.STATE_PREPARING : SalesOrder.STATE_READY;
    }

    /** 支付状态由已收金额与订单总额派生，取值与采购 u_p_purchase.paystatus 一致。 */
    private String derivePayStatus(BigDecimal paid, BigDecimal total)
    {
        if (paid.signum() <= 0) return "未支付";
        if (total.signum() <= 0) return "已支付";
        int cmp = paid.compareTo(total);
        if (cmp < 0) return "部分支付";
        return cmp > 0 ? "超额支付" : "已支付";
    }

    /** 回款状态与支付状态同源，只是措辞换成财务视角。 */
    private String toReceiveStatus(String payStatus)
    {
        if ("部分支付".equals(payStatus)) return "部分收款";
        if ("已支付".equals(payStatus)) return "已收款";
        if ("超额支付".equals(payStatus)) return "超额收款";
        return "未收款";
    }

    /** 明细补齐：单号、行号、商品快照、行金额。 */
    private void prepareItems(String shopCode, SalesOrder order, List<SalesOrderItem> items)
    {
        prepareItems(shopCode, order, items, Collections.emptyMap());
    }

    /**
     * 明细补齐：单号、行号、商品快照、行金额。
     *
     * @param reusableDetailCodes 商品代码 → 原有明细代码。改单时传进来，让没换过商品的行
     *        <b>沿用原来的明细代码</b>；建单传空 map。
     *
     * <p>为什么要沿用：改单是「删掉全部明细再整体重建」，每行都发新代码的话，外部按
     * {@code orderdetailcode} 记住这张单的东西就全指到不存在的行上了。撤审 → 改单
     * 这条路走一遍，已取消的发货批次、历史预占行都会变成悬空引用。真正被换掉的商品行
     * 本来就该失效，但没动过的行不该跟着遭殃。</p>
     *
     * <p>在途批次和在制工单另有 assertNoPendingBatches / assertNoOpenProductions 挡在
     * 撤审那一步，这里管的是那些拦不住、也不该拦的历史记录。</p>
     */
    private void prepareItems(String shopCode, SalesOrder order, List<SalesOrderItem> items,
            Map<String, String> reusableDetailCodes)
    {
        Set<String> lineKeys = new HashSet<>();
        int lineNo = 0;
        for (SalesOrderItem item : items)
        {
            lineNo++;
            if (isBlank(item.getGoodsCode())) throw new ServiceException("第" + lineNo + "行商品代码不能为空");
            if (nvl(item.getQty()).signum() <= 0) throw new ServiceException("第" + lineNo + "行数量必须大于 0");
            if (nvl(item.getPrice()).signum() < 0) throw new ServiceException("第" + lineNo + "行单价不能为负");

            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, item.getGoodsCode());
            if (goods == null) throw new ServiceException("商品'" + item.getGoodsCode() + "'不存在");
            // 同一商品重复开行会让预占与发货的核销逻辑失去唯一定位，这里挡掉
            if (!lineKeys.add(item.getGoodsCode()))
            {
                throw new ServiceException("商品'" + goods.getGoodsName() + "'重复，请合并到一行");
            }

            item.setShopCode(shopCode);
            item.setOrderCode(order.getOrderCode());
            // 商品没换就沿用原来的明细代码，换了或新加的才发新号
            String reused = reusableDetailCodes.get(item.getGoodsCode());
            item.setDetailCode(reused != null ? reused : codeGenerator.nextSalesOrderDetailCode());
            item.setLineNo(lineNo);
            item.setGoodsName(goods.getGoodsName());
            item.setSpec(goods.getSpec());
            item.setUnit(goods.getUnit());
            item.setAmount(nvl(item.getPrice()).multiply(item.getQty()).setScale(2, RoundingMode.HALF_UP));
            // 定制标记只在建单/改单这一步收，之后不再变——它是订单的固有属性。
            // 没传当普通品：升级前的调用方不知道有这一项，默认成定制会让所有单都要专门排产。
            if (item.getCustomMade() == null) item.setCustomMade(Boolean.FALSE);
        }
    }

    /**
     * 原有明细的「商品代码 → 明细代码」映射，供改单时沿用代码。
     *
     * <p>同一商品在一张订单里只允许开一行（{@code prepareItems} 里的 lineKeys 挡着），
     * 所以按商品代码索引不会撞。</p>
     */
    private Map<String, String> detailCodesByGoods(List<SalesOrderItem> items)
    {
        Map<String, String> codes = new HashMap<>();
        for (SalesOrderItem item : items)
        {
            if (!isBlank(item.getGoodsCode()) && !isBlank(item.getDetailCode()))
            {
                codes.put(item.getGoodsCode(), item.getDetailCode());
            }
        }
        return codes;
    }

    /**
     * 改单守卫：看不到的金额一律沿用库里的原值，不认前端回传的 {@code null}。
     *
     * <p>没有这道守卫，一个没有 {@code price:sale} 的人改一下收货地址就会把整单货款清零——
     * {@code prepareItems} 里的 {@code nvl(item.getPrice())} 把 {@code null} 当 0，
     * {@code applyAmounts} 再把这些 0 汇总成订单总额。
     * 见 {@code docs/金额按岗位可见.md} 第 6.1 节。</p>
     *
     * <p>按商品代码对齐新旧明细，与 {@link #detailCodesByGoods} 同一套口径
     * （一张订单里同一商品只允许开一行，不会撞）。</p>
     *
     * <p>无权的人新增商品行直接报错，不静默存 0：一行价格为 0 的订单看不出是无权还是真免费，
     * 而它会一路算进总额、收款状态和可回款金额。</p>
     */
    private void keepMaskedAmounts(SalesOrder command, SalesOrder old, List<SalesOrderItem> items,
            List<SalesOrderItem> oldItems)
    {
        boolean saleMasked = PriceMaskUtils.isSaleMaskedOnWrite();
        boolean costMasked = PriceMaskUtils.isCostMaskedOnWrite();
        if (!saleMasked && !costMasked)
        {
            return;
        }
        if (saleMasked)
        {
            command.setFreightAmount(old.getFreightAmount());
        }
        Map<String, SalesOrderItem> originalByGoods = new HashMap<>();
        for (SalesOrderItem oldItem : oldItems)
        {
            if (!isBlank(oldItem.getGoodsCode()))
            {
                originalByGoods.put(oldItem.getGoodsCode(), oldItem);
            }
        }
        for (SalesOrderItem item : items)
        {
            SalesOrderItem original = isBlank(item.getGoodsCode())
                    ? null : originalByGoods.get(item.getGoodsCode());
            if (original == null)
            {
                if (saleMasked)
                {
                    throw new ServiceException("无权查看销售价，不能新增商品行；"
                            + "请由有价格权限的同事添加，或只修改已有行的数量");
                }
                continue;
            }
            if (saleMasked)
            {
                item.setPrice(original.getPrice());
            }
            if (costMasked)
            {
                item.setCostPrice(original.getCostPrice());
                item.setCostAmount(original.getCostAmount());
            }
        }
    }

    /** 货款按明细汇总，总额 = 货款 + 运费。不接受前端直接给的合计。 */
    private void applyAmounts(SalesOrder order, List<SalesOrderItem> items)
    {
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : items) goodsAmount = goodsAmount.add(nvl(item.getAmount()));
        order.setGoodsAmount(goodsAmount);
        order.setFreightAmount(nvl(order.getFreightAmount()));
        order.setTotalAmount(goodsAmount.add(order.getFreightAmount()));
        order.setCurrency(CurrencySupport.require(order.getCurrency()));
    }

    /**
     * 刷新列表页显示的「商品内容」。
     *
     * <p>和 {@link #applyAmounts} 一样，值一律由明细算出、每次建单改单整体重算——
     * 明细本来就是「全删再重建」，跟着一起重算最省心。</p>
     */
    private void applyGoodsSummary(SalesOrder order, List<SalesOrderItem> items)
    {
        order.setGoodsSummary(GoodsSummaryBuilder.build(items,
                SalesOrderItem::getGoodsCode, SalesOrderItem::getGoodsName));
    }

    /**
     * 出口字段归一化，建单和改草稿都走一遍。
     *
     * <p>做三件事，都是「不让库里出现自相矛盾的行」：</p>
     * <ol>
     *   <li>收货方式兜成三个合法值之一，收件地址兜成非 null（列是 NOT NULL）；</li>
     *   <li>不走货代就没有唛头——把唛头标记和唛头文件一起清掉，免得留下
     *       「上门提货却要唛头」这种谁也解释不了的单；</li>
     *   <li>商品描述清单整体解析校验后按标准格式序列化回去，金额一律由服务端按
     *       数量×单价重算，不收前端传来的值。</li>
     * </ol>
     */
    private void applyExportFields(SalesOrder command)
    {
        String receiveType = normalizeReceiveType(command.getReceiveType());
        command.setReceiveType(receiveType);
        // 上门提货可以三样都不填，而这三列都是 NOT NULL，留 null 会直接插不进去
        if (command.getReceivePerson() == null) command.setReceivePerson("");
        if (command.getReceiveTelephone() == null) command.setReceiveTelephone("");
        if (command.getReceiveAddress() == null) command.setReceiveAddress("");

        // 唛头是给货代认箱用的，别的收货方式留着这两列只会误导仓库
        boolean forwarder = SalesOrder.RECEIVE_TYPE_FORWARDER.equals(receiveType);
        boolean markNeeded = forwarder && Boolean.TRUE.equals(command.getMarkNeeded());
        command.setMarkNeeded(markNeeded);
        if (!markNeeded) command.setMarkFile(null);

        command.setDescItemsJson(buildDescItemsJson(command.getDescItems()));
    }

    /** 空值当默认的「货代地址」，认不出的值直接拒——脏值落进去，界面上的单选框就选不中任何一项 */
    private String normalizeReceiveType(String receiveType)
    {
        if (isBlank(receiveType)) return SalesOrder.RECEIVE_TYPE_FORWARDER;
        String trimmed = receiveType.trim();
        if (!SalesOrder.isValidReceiveType(trimmed))
        {
            throw new ServiceException("国内收货方式只能是货代地址、客户地址或上门提货，收到的是：" + trimmed);
        }
        return trimmed;
    }

    /**
     * 商品描述清单 → 落库的 JSON 文本。
     *
     * <p>没有有效行就存 null，不存 {@code "[]"}：两者在界面上是同一回事，
     * 存 null 让「这张单没写描述」在库里一眼能看出来。</p>
     *
     * <p>名称是这行唯一的内容，空的就整行丢掉——留一条只有数量的空描述，
     * 客户和生产都看不懂。数量和单价负数直接拒：这份清单是要给客户看的。</p>
     */
    private String buildDescItemsJson(List<SalesOrderDescItem> descItems)
    {
        if (descItems == null || descItems.isEmpty()) return null;
        List<SalesOrderDescItem> cleaned = new ArrayList<>();
        for (SalesOrderDescItem row : descItems)
        {
            if (row == null || isBlank(row.getName())) continue;
            BigDecimal qty = nvl(row.getQty());
            BigDecimal price = nvl(row.getPrice());
            if (qty.signum() < 0 || price.signum() < 0)
            {
                throw new ServiceException("商品描述「" + row.getName().trim() + "」的数量和单价不能是负数");
            }
            SalesOrderDescItem clean = new SalesOrderDescItem();
            clean.setName(row.getName().trim());
            clean.setQty(qty);
            clean.setPrice(price);
            // 金额只认服务端算出来的，前端传什么都不看
            clean.setAmount(qty.multiply(price).setScale(2, RoundingMode.HALF_UP));
            cleaned.add(clean);
        }
        return cleaned.isEmpty() ? null : JSON.toJSONString(cleaned);
    }

    /**
     * 落库的 JSON → 商品描述清单。
     *
     * <p>解析失败只当没有描述，不抛：这份清单不参与任何计算，
     * 让一行脏数据把整张订单的详情页顶掉不值当。口径与
     * {@code ProductionOrderServiceImpl.parseAllocations} 一致。</p>
     */
    private List<SalesOrderDescItem> parseDescItems(String json)
    {
        if (isBlank(json)) return new ArrayList<>();
        try
        {
            List<SalesOrderDescItem> parsed = JSON.parseArray(json, SalesOrderDescItem.class);
            return parsed == null ? new ArrayList<>() : parsed;
        }
        catch (RuntimeException error)
        {
            return new ArrayList<>();
        }
    }

    private void validateHeader(SalesOrder command)
    {
        if (command == null) throw new ServiceException("订单内容不能为空");
        if (isBlank(command.getShopStoreCode())) throw new ServiceException("商家店铺代码不能为空");
        if (isBlank(command.getPlatCode())) throw new ServiceException("平台代码不能为空");
        // 上门提货是客户自己来仓库拉：货不出仓、不发快递、不打面单，收件人、电话、地址
        // 三样都没有着落处，所以一概不强求。另外两种要把货发出去，一样都不能少。
        // 三列在库里都是 NOT NULL，applyExportFields 会把没填的兜成空串，不能留 null。
        if (SalesOrder.RECEIVE_TYPE_PICKUP.equals(normalizeReceiveType(command.getReceiveType())))
        {
            // 不填放行，填了得像一串电话
            ContactPhoneUtils.validateOptional(command.getReceiveTelephone(), "收件电话");
        }
        else
        {
            if (isBlank(command.getReceivePerson())) throw new ServiceException("收件人不能为空");
            if (isBlank(command.getReceiveAddress())) throw new ServiceException("收件地址不能为空");
            // 收件电话<b>不</b>按 11 位手机号收，口径见 ContactPhoneUtils：平台下单给的是虚拟号，
            // 位数不固定；客户留的可能是带区号的固话；也常常一次留好几个号码。
            // 客户档案上的手机号仍旧走 MobileUtils，两者不是一回事。
            ContactPhoneUtils.validateRequired(command.getReceiveTelephone(), "收件电话");
        }
    }

    /**
     * 平台与店铺由维护主数据决定，订单只保存当时的代码和名称快照。
     * 不信任前端传入的名称；店铺代码只在平台内唯一，所以按「平台+店铺代码」查。
     */
    /**
     * 平台与店铺由维护主数据决定，订单只保存当时的代码和名称快照。
     * 不信任前端传入的名称；店铺代码只在平台内唯一，所以按「平台+店铺代码」查。
     *
     * <p><b>停用只挡「新选中」，不挡「保持原样」。</b>停用的语义是「以后别再往这个店铺开新单」，
     * 不是「把已经开出来的单冻住」。原先建单和改单共用一套严格校验，结果是店铺一停用，
     * 那些还挂在它名下的草稿就再也改不动了——连「把店铺换成一个启用的」这个唯一的自救动作
     * 都做不了，而 {@link #submitOrder} 又不走这段，于是出现「改不了却提交得了」。</p>
     *
     * @param previous 改单时传库里的原单，建单传 null。只有与原单一致的平台/店铺才允许是停用状态
     */
    private void applyPlatformStore(String shopCode, SalesOrder command, SalesOrder previous)
    {
        String platCode = command.getPlatCode().trim();
        String storeCode = command.getShopStoreCode().trim();

        com.ruoyi.system.domain.OrderPlatform platform = platformMapper.selectPlatformByCode(shopCode, platCode);
        if (platform == null)
        {
            throw new ServiceException("所选平台不存在");
        }
        if (!Integer.valueOf(1).equals(platform.getIsActive())
                && !isUnchanged(platCode, previous == null ? null : previous.getPlatCode()))
        {
            throw new ServiceException("平台「" + platform.getPlatformName() + "」已停用，不能选它开新单");
        }

        com.ruoyi.system.domain.OrderPlatformStore store =
                platformMapper.selectStoreByCode(shopCode, platCode, storeCode);
        if (store == null)
        {
            throw new ServiceException("所选店铺不存在，或不属于该平台");
        }
        if (!Integer.valueOf(1).equals(store.getIsActive())
                && !isUnchanged(storeCode, previous == null ? null : previous.getShopStoreCode()))
        {
            throw new ServiceException("店铺「" + store.getStoreName() + "」已停用，不能选它开新单");
        }

        command.setPlatCode(platform.getPlatformCode());
        command.setPlatName(platform.getPlatformName());
        command.setShopStoreCode(store.getStoreCode());
        command.setShopStoreName(store.getStoreName());
    }

    /**
     * 把客户快照拷进订单。
     *
     * <p>只认前端传来的 {@code buyerCode}，名称、头像、店铺图一律按编码查 u_m_buyer 现填，
     * <b>前端传的快照值全部丢弃</b>：信前端就等于允许它把订单上的客户名写成任意值，
     * 跨商户的编码也能靠 {@code requireBuyer} 带 shopCode 查询挡在门外。</p>
     *
     * <p>客户选填。没建档的散客继续走 {@code buyerUser} 手填那条路，
     * 这时把八个快照列清空，免得改单时留下上一个客户的残值。</p>
     *
     * <p>停用的客户不让新挂单，但不影响已经挂着它的历史单——那些单上的是快照，
     * 跟档案是不是还启用没关系。</p>
     *
     * <p>二级客户同样只认编码，并且必须挂在所选主客户名下，校验在
     * {@code requireBuyer2} 里。没选主客户就带了二级客户属于非法组合，直接拒。</p>
     */
    private void applyBuyerSnapshot(String shopCode, SalesOrder command)
    {
        String buyerCode = command.getBuyerCode() == null ? null : command.getBuyerCode().trim();
        String buyer2Code = command.getBuyer2Code() == null ? null : command.getBuyer2Code().trim();
        if (buyerCode == null || buyerCode.isEmpty())
        {
            if (buyer2Code != null && !buyer2Code.isEmpty())
            {
                throw new ServiceException("选了二级客户就必须先选主客户");
            }
            clearBuyerSnapshot(command);
            return;
        }

        Buyer buyer = buyerService.requireBuyer(shopCode, buyerCode);
        if (!Integer.valueOf(1).equals(buyer.getActiveFlag()))
        {
            throw new ServiceException("客户「" + buyer.resolveDisplayName() + "」已停用，不能用于新订单");
        }

        command.setBuyerCode(buyer.getBuyerCode());
        command.setBuyerName(buyer.resolveDisplayName());
        command.setBuyerImage(buyer.getImageUrl());
        command.setShopImage(buyer.getShopImage());
        // 客户档案里维护了买家账号时顺手带上，业务员没手填就不覆盖他填的
        if (isBlank(command.getBuyerUser()) && !isBlank(buyer.getBuyerUser()))
        {
            command.setBuyerUser(buyer.getBuyerUser());
        }

        if (buyer2Code == null || buyer2Code.isEmpty())
        {
            // 换主客户后前端应该已经清掉二级客户，这里再清一遍兜底：
            // 少了它，改单时会留下上一个主客户的门店快照。
            command.setBuyer2Code(null);
            command.setBuyer2Name(null);
            command.setBuyer2User(null);
            command.setBuyer2Image(null);
            return;
        }

        Buyer2 buyer2 = buyerService.requireBuyer2(shopCode, buyer.getBuyerCode(), buyer2Code);
        if (!Integer.valueOf(1).equals(buyer2.getActiveFlag()))
        {
            throw new ServiceException("二级客户「" + buyer2.resolveDisplayName() + "」已停用，不能用于新订单");
        }
        command.setBuyer2Code(buyer2.getBuyer2Code());
        command.setBuyer2Name(buyer2.resolveDisplayName());
        command.setBuyer2User(buyer2.getBuyer2User());
        command.setBuyer2Image(buyer2.getImageUrl());
    }

    private void clearBuyerSnapshot(SalesOrder command)
    {
        command.setBuyerCode(null);
        command.setBuyerName(null);
        command.setBuyerImage(null);
        command.setShopImage(null);
        command.setBuyer2Code(null);
        command.setBuyer2Name(null);
        command.setBuyer2User(null);
        command.setBuyer2Image(null);
    }

    /** 这次提交的代码和原单上的是不是同一个（原单为空视为「变了」，即建单一律按新选处理） */
    private boolean isUnchanged(String submitted, String stored)
    {
        return stored != null && submitted.equals(stored.trim());
    }

    private List<SalesOrderItem> requireItems(List<SalesOrderItem> items)
    {
        if (items == null || items.isEmpty()) throw new ServiceException("订单明细不能为空");
        return new ArrayList<>(items);
    }

    /**
     * 挡住「订单要离开可发货区间，但手上还挂着没发的批次」这种局面。
     *
     * <p>撤审、关闭、取消三处都要挡：这三个动作之后订单就不再是可发货状态，
     * 剩下的批次既发不出去（{@code ShipBatchServiceImpl} 会按订单状态拒绝），
     * 也不会有人回头去清，只会变成一直显示「待确认」的僵尸行。要求先取消批次，
     * 比替用户默默作废单据更可交代。</p>
     *
     * @param action 用在报错文案里的动作名，例如「关闭」
     */
    private void assertNoPendingBatches(String shopCode, String orderCode, String action)
    {
        for (BigDecimal pending : pendingQtyResolver.resolve(shopCode, orderCode, null).values())
        {
            if (nvl(pending).signum() > 0)
            {
                throw new ServiceException("订单还有未完成的发货批次，请先取消这些批次再" + action);
            }
        }
    }

    /**
     * 挡住「订单要终止或退回草稿，但还有按它开的在制工单」这种局面。
     *
     * <p><b>撤审</b>是与在途批次同一类问题，只是引用藏得更深：工单的产出分配
     * （{@code orderallocation}）里存的是订单<b>明细代码</b>，而撤审后 {@link #updateDraft}
     * 会把明细整体删掉重建、换一批新的明细代码。那之后工单完工调
     * {@link #fulfillFromProduction} 会按旧明细代码查不到行，直接返回 0——为这张订单专门
     * 生产的成品进了库却没占给订单，而且那条路径是<b>有意静默</b>的（见方法注释），
     * 连认领日志都不会写，没人会发现。</p>
     *
     * <p><b>取消与关闭</b>原先不挡：明细没被删，分配指向的行还在，工单完工时
     * {@code fulfillFromProduction} 会按履约状态白名单正确地拒绝认领，账面上不会错。
     * 现在一并挡住，是因为「不会错账」不等于「说得过去」——车间还在为一张已经作废的
     * 订单做货，做完了没人认领，成本白花。让人先去生产管理把工单处理掉，
     * 是把这个决定摆到明面上，而不是让它在完工那一刻被静默丢弃。</p>
     *
     * @param action 用在报错文案里的动作名：撤审 / 关闭 / 取消
     */
    private void assertNoOpenProductions(String shopCode, String orderCode, String action)
    {
        if (productionOrderMapper.countOpenAllocatedToOrder(shopCode, orderCode) > 0)
        {
            throw new ServiceException("订单还有按它开的生产工单没完工，" + action
                    + "后这些工单做出来的货没人认领。请先在生产管理里终止或关闭这些工单，或等完工后再" + action);
        }
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
