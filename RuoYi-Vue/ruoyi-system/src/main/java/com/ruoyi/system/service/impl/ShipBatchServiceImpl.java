package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.domain.ShipBatchItem;
import com.ruoyi.system.domain.vo.ShipBatchLogisticsInfo;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.ShipBatchItemMapper;
import com.ruoyi.system.mapper.ShipBatchMapper;
import com.ruoyi.system.runner.MonthlyTableInitializer;
import com.ruoyi.system.runner.MonthlyTableInitializer.TableGroup;
import com.ruoyi.system.service.IOutboundService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.service.IShipBatchService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

/**
 * 发货批次实现。状态机（待确认→已提交仓库→已打印备货单→备货完成→已录入快递单号→
 * 已打印快递单号→已完成）与「完成」那一步的三件事见 {@link IShipBatchService}。
 */
@Service
public class ShipBatchServiceImpl implements IShipBatchService
{
    /**
     * 能被仓库驳回的状态：货还在仓库里的那几格。
     *
     * <p>「已完成」不在里面，因为那一刻货已经被承运商取走、库存也扣了，撤不回来；
     * 「待确认」不在里面，因为它本来就在业务员手里，没什么可退的；
     * 「已取消」是终态。</p>
     */
    private static final List<String> REJECTABLE_STATES = Arrays.asList(
            ShipBatch.STATUS_SUBMITTED, ShipBatch.STATUS_PICK_PRINTED, ShipBatch.STATUS_PICKED,
            ShipBatch.STATUS_LOGISTICS_ENTERED, ShipBatch.STATUS_LABEL_PRINTED);

    /**
     * 订单侧还能作废整张批次的状态。
     *
     * <p>口子留到「备货完成」为止：客户临时取消订单是常有的事，而订单要关闭/取消/撤审，
     * {@code SalesOrderServiceImpl.assertNoPendingBatches} 要求先把未完成的批次清掉。
     * 只留「待确认」的话，「批次已提交、仓库还没发货、客户要取消」就成了死局——
     * 批次取消不掉，订单也关不掉。</p>
     *
     * <p>「已录入快递单号」往后不给取消，但不是因为撤不回来——那时候货其实还在仓库里。
     * 是因为那之后单子上带的是业务员自己填的面单信息、仓库正按它打单贴箱，
     * 该由仓库先点「驳回」把它退回来并写清原因。退回「待确认」之后照样能取消。</p>
     */
    private static final List<String> CANCELLABLE_STATES = Arrays.asList(
            ShipBatch.STATUS_PENDING, ShipBatch.STATUS_SUBMITTED,
            ShipBatch.STATUS_PICK_PRINTED, ShipBatch.STATUS_PICKED);

    /**
     * 已经把订单付款额度占掉的批次状态。
     *
     * <p>额度是<b>谁先录快递单号谁先占</b>。还躺在「已提交仓库」「备货完成」的兄弟批次
     * 不算数——把它们也算进来，第一个录单的人就会被自己排的其它批次挤爆，
     * 而那些批次最后发不发、发多少都还没定。</p>
     *
     * <p>「已完成」也不在里面：那部分量已经回写进订单明细的 shippedqty，
     * 货值从那里算，再数一遍就重复了。</p>
     */
    private static final List<String> LOGISTICS_COMMITTED_STATES = Arrays.asList(
            ShipBatch.STATUS_LOGISTICS_ENTERED, ShipBatch.STATUS_LABEL_PRINTED);

    @Autowired private ShipBatchMapper batchMapper;
    @Autowired private ShipBatchItemMapper batchItemMapper;
    @Autowired private SalesOrderItemMapper orderItemMapper;
    @Autowired private InventoryWarehouseMapper warehouseMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    /** 建出库单要动的库存行，发货前一次锁完。见该类注释。 */
    @Autowired private OrderStockLockCoordinator stockLockCoordinator;
    @Autowired private ISalesOrderService orderService;
    @Autowired private IOutboundService outboundService;
    @Autowired private ShipBatchPendingQtyResolver pendingQtyResolver;
    @Autowired private MonthlyTableInitializer monthlyTables;

    /**
     * 横向权限点：查看全部发货批次。给「订单管理 → 发货批次」那个页面用
     * （业务主管想看全店排产时勾它）。不勾的人只看得到自己建的批次
     * （{@code u_f_shipbatch.createuser}，建批次时写的登录账号）。
     */
    public static final String PERMI_VIEW_ALL = "order:shipbatch:all";

    /**
     * 「库存管理 → 仓库发货管理」这个页面本身的权限（菜单 {@code fn5103}）。
     *
     * <p><b>持有它就等于能看全部批次</b>，不需要再单独勾 {@link #PERMI_VIEW_ALL}。</p>
     *
     * <p>这不是图省事，是这两件事本来就分不开：仓库发货页的全部工作内容就是处理
     * <b>别人建的</b>批次。「能进这个页面」和「只看得到自己建的」放在一起没有意义——
     * 库管员自己不建批次，那样他的工作台永远是空的。</p>
     *
     * <p>原先两个页面共用 {@code order:shipbatch:list}，系统分不出「这人是仓库还是销售」，
     * 只能另开一个开关，而那个开关挂在订单管理树下——为了让库管员看全部批次，
     * 就得给他一个他不该有的订单管理菜单，看着像权限越界。拆出这个独立权限串之后，
     * 库管员的配置完全留在库存管理这一棵树里。</p>
     */
    public static final String PERMI_WAREHOUSE = "order:shipbatch:warehouse";

    /** 这两个权限任意一个，都代表「看得到全部批次」。 */
    private static final String[] VIEW_ALL_PERMISSIONS = { PERMI_VIEW_ALL, PERMI_WAREHOUSE };

    @Override
    public List<ShipBatch> selectBatchList(ShipBatch query, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        // 一律由服务层说了算：ownerScope 是内部载体，前端塞得进来，不能信
        query.setOwnerScope(OwnershipScope.ownerFilter(VIEW_ALL_PERMISSIONS));
        List<ShipBatch> list = batchMapper.selectList(query, tableSuffix);
        for (ShipBatch row : list) row.setTableSuffix(tableSuffix);
        return list;
    }

    /** 横向权限：不是自己建的批次，看不到也动不了。 */
    private void requireOwnBatch(ShipBatch batch)
    {
        if (batch == null) return;
        OwnershipScope.requireOwn(batch.getCreateUser(), "发货批次", batch.getShipCode(), VIEW_ALL_PERMISSIONS);
    }

    @Override
    public List<ShipBatch> selectBatchesByOrder(String shopCode, String orderCode)
    {
        List<ShipBatch> result = new ArrayList<>();
        // 一张订单的批次按各自的 shipdate 落表，可能散在多个月份，必须全表扫一遍
        for (String suffix : MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes()))
        {
            ShipBatch query = new ShipBatch();
            query.setShopCode(shopCode);
            query.setOrderCode(orderCode);
            for (ShipBatch row : batchMapper.selectList(query, suffix))
            {
                row.setTableSuffix(suffix);
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public ShipBatch selectBatchDetail(String shopCode, String shipCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCode(shopCode, shipCode, tableSuffix);
        requireOwnBatch(batch);
        if (batch != null)
        {
            batch.setTableSuffix(tableSuffix);
            batch.setItems(batchItemMapper.selectByShipCode(shopCode, shipCode, tableSuffix));
            applyOrderInfo(shopCode, batch);
        }
        return batch;
    }

    /**
     * 带出来源订单的币种、所在月份和收货方式给页面用。批次表不存这几样：都是订单的属性，
     * 存一份快照就多一个会和订单对不上的地方，而单张批次的详情多查一次订单不贵。
     *
     * <p>月份是改单必需的，不只是显示：前端要按订单所在月份去查可发货订单，才能拿到
     * 每行还能发多少。批次自己的 tableSuffix 是按计划发货日期落的，与订单的月份经常
     * 不是同一个（月底下单、次月发货很常见），拿它去查会查空。</p>
     *
     * <p>只在读单张批次时调用；列表页一屏几十行，每行回查一次订单会打成 N+1。</p>
     */
    private void applyOrderInfo(String shopCode, ShipBatch batch)
    {
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        // 订单被删或跨月找不到时留空，页面按本位币显示，不该因为显示字段拿不到就让详情打不开
        if (order == null) return;
        batch.setOrderCurrency(order.getCurrency());
        batch.setOrderTableSuffix(order.getTableSuffix());
        // 录快递单号那一步要靠它决定承运商与单号标不标必填，见 ShipBatch.orderReceiveType
        batch.setOrderReceiveType(order.getReceiveType());
    }

    @Override
    public ShipBatch findBatchAcrossMonths(String shopCode, String shipCode)
    {
        if (isBlank(shipCode)) return null;
        for (String suffix : MonthTableSuffix.validDescending(batchMapper.selectShipBatchTableSuffixes()))
        {
            ShipBatch hit = batchMapper.selectByCode(shopCode, shipCode.trim(), suffix);
            if (hit != null)
            {
                // 详情不传月份时会退到这条路（见 ShipBatchController.getInfo），
                // 不挡就等于给了一个「省掉 tableSuffix 就能看别人批次」的后门。
                // 与 findOrderAcrossMonths 不同，本方法没有跨模块调用方，挡了不会伤到谁。
                requireOwnBatch(hit);
                hit.setTableSuffix(suffix);
                hit.setItems(batchItemMapper.selectByShipCode(shopCode, hit.getShipCode(), suffix));
                // 这条路径也是给详情用的，币种和订单月份要和 selectBatchDetail 带一样的
                applyOrderInfo(shopCode, hit);
                return hit;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShipBatch createBatch(String shopCode, ShipBatch command, String username)
    {
        if (command == null || isBlank(command.getOrderCode())) throw new ServiceException("来源订单号不能为空");
        if (command.getItems() == null || command.getItems().isEmpty()) throw new ServiceException("发货明细不能为空");

        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, command.getOrderCode());
        if (order == null) throw new ServiceException("订单不存在");
        assertOrderShippable(order, "建发货批次");

        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, command.getWarehouseCode());
        if (warehouse == null) throw new ServiceException("发货仓库'" + command.getWarehouseCode() + "'不存在");

        // 批次按计划发货日期落表，与订单的月份无关
        Date shipDate = command.getShipDate() == null ? new Date() : command.getShipDate();
        String suffix = MonthTableSuffix.of(shipDate);
        // 计划发货日期可以排到下个月甚至更远，而建表器只提前建当月和下月，
        // 再远的月份月表不存在，直接往下走会撞「表不存在」的原始 SQL 错误。
        //
        // ⚠ 只查不建。在这个事务里建表会撞 1412 Table definition has changed——
        //   DDL 立即提交，而本事务的表定义快照停在 DDL 之前，读新表直接报错，
        //   表建出来了这一笔还是失败。详见 MonthlyTableInitializer.requireTablesFor。
        //
        // 传 SHIP_BATCH 而不是用默认组：这里要的是 u_f_shipbatch 那两张，
        // 查库存那三张会「检查通过、insert 才炸」。
        monthlyTables.requireTablesFor(suffix, TableGroup.SHIP_BATCH);

        List<SalesOrderItem> orderItems = orderItemMapper.selectByOrderCodeForUpdate(
                shopCode, order.getOrderCode(), order.getTableSuffix());
        validateShippableQty(shopCode, order, orderItems, command.getItems(), null);

        command.setShopCode(shopCode);
        command.setOrderCode(order.getOrderCode());
        command.setShipCode(codeGenerator.nextShipBatchCode());
        command.setShipDate(shipDate);
        command.setWarehouseName(warehouse.getWareName());
        command.setStatus(ShipBatch.STATUS_PENDING);
        command.setCreateUser(username);
        command.setTableSuffix(suffix);

        prepareItems(command, orderItems);
        batchMapper.insert(command, suffix);
        batchItemMapper.batchInsert(command.getItems(), suffix);
        return command;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePending(String shopCode, ShipBatch command, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch old = batchMapper.selectByCodeForUpdate(shopCode, command.getShipCode(), tableSuffix);
        if (old == null) throw new ServiceException("发货批次不存在");
        requireOwnBatch(old);
        if (!ShipBatch.STATUS_PENDING.equals(old.getStatus()))
        {
            throw new ServiceException("批次已提交仓库，不能修改。如需变更请取消后重建");
        }

        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, old.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        assertOrderShippable(order, "修改发货批次");
        if (command.getItems() == null || command.getItems().isEmpty()) throw new ServiceException("发货明细不能为空");

        if (!isBlank(command.getWarehouseCode()))
        {
            InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, command.getWarehouseCode());
            if (warehouse == null) throw new ServiceException("发货仓库'" + command.getWarehouseCode() + "'不存在");
            command.setWarehouseName(warehouse.getWareName());
        }
        else
        {
            command.setWarehouseCode(old.getWarehouseCode());
            command.setWarehouseName(old.getWarehouseName());
        }

        List<SalesOrderItem> orderItems = orderItemMapper.selectByOrderCodeForUpdate(
                shopCode, order.getOrderCode(), order.getTableSuffix());
        // 校验时排除本批次自己已排的量，否则改单会把自己算成占用
        validateShippableQty(shopCode, order, orderItems, command.getItems(), old.getShipCode());

        command.setShopCode(shopCode);
        command.setShipCode(old.getShipCode());
        command.setOrderCode(old.getOrderCode());
        prepareItems(command, orderItems);

        if (batchMapper.updatePending(command, tableSuffix) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        batchItemMapper.deleteByShipCode(shopCode, old.getShipCode(), tableSuffix);
        batchItemMapper.batchInsert(command.getItems(), tableSuffix);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmBatch(String shopCode, String shipCode, String tableSuffix, String username)
    {
        return transit(shopCode, shipCode, tableSuffix, ShipBatch.STATUS_PENDING,
                ShipBatch.STATUS_SUBMITTED, "提交仓库", username, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int printPickList(String shopCode, String shipCode, String tableSuffix, String username)
    {
        return transit(shopCode, shipCode, tableSuffix, ShipBatch.STATUS_SUBMITTED,
                ShipBatch.STATUS_PICK_PRINTED, "打印备货单", username, false);
    }

    /**
     * 备货完成：已打印备货单 → 备货完成。<b>一分库存都不动</b>，只落备货照片。
     *
     * <p>没走 {@link #transit} 有两个原因：照片要和状态同一条语句落（见
     * {@code ShipBatchMapper.completePicking}）；还要把「还没打备货单」和「已经备完」
     * 分开报——这两种情况操作人要做的事完全不同，一句「不能转为备货完成」说不清楚。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completePicking(String shopCode, String shipCode, String tableSuffix, String photos,
            String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        if (ShipBatch.STATUS_PICKED.equals(batch.getStatus())) throw new ServiceException("批次已备货完成");
        if (ShipBatch.STATUS_COMPLETED.equals(batch.getStatus())) throw new ServiceException("批次已完成");
        if (!ShipBatch.STATUS_PICK_PRINTED.equals(batch.getStatus()))
        {
            throw new ServiceException("批次还没打印备货单，不能点备货完成。当前状态：" + batch.getStatus());
        }
        if (batchItemMapper.selectByShipCode(shopCode, shipCode, tableSuffix).isEmpty())
        {
            throw new ServiceException("批次没有明细，不能备货");
        }

        // 批次建好之后订单还可能被取消、关闭或撤审，往前推之前必须按当前状态再判一次
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        assertOrderShippable(order, "备货");

        // 照片校验排在订单状态之后：订单已经取消了还去提示「请上传照片」是在指错方向
        String finalPhotos = normalizePhotos(photos);
        if (isBlank(finalPhotos))
        {
            throw new ServiceException("请至少上传一张备货照片——业务员在录快递单号之前，只能靠这组照片确认货备成什么样");
        }

        if (batchMapper.completePicking(shopCode, shipCode, tableSuffix, finalPhotos, username, new Date()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int savePickPhotos(String shopCode, String shipCode, String tableSuffix, String photos,
            String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        String status = batch.getStatus();
        if (!ShipBatch.STATUS_PICK_PRINTED.equals(status) && !ShipBatch.STATUS_PICKED.equals(status))
        {
            throw new ServiceException("批次当前状态「" + status + "」不能改备货照片。"
                    + "只有「已打印备货单」和「备货完成」两格能传，再往后业务员已经按这组照片录过快递单号了");
        }
        // 「备货完成」上不许清空：那一格的照片是业务员录单的依据，清掉等于把凭证抽走。
        // 「已打印备货单」上还没点备货完成，清空只是重传前的一步，放行
        String finalPhotos = normalizePhotos(photos);
        if (isBlank(finalPhotos) && ShipBatch.STATUS_PICKED.equals(status))
        {
            throw new ServiceException("备货完成的批次至少要留一张照片，不能全删");
        }
        if (batchMapper.updatePickPhotos(shopCode, shipCode, tableSuffix, finalPhotos) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * 照片列表规整：去掉空串与首尾空白，按逗号重新拼。
     *
     * <p>前端 ImageUpload 删掉中间一张时可能留下 {@code "a.jpg,,b.jpg"} 这样的串，
     * 直接落库的话「至少一张」会被空项骗过去，详情页也会渲染出一个坏掉的图片框。</p>
     */
    private String normalizePhotos(String photos)
    {
        if (photos == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String part : photos.split(","))
        {
            String url = part.trim();
            if (url.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(url);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    @Override
    public ShipBatchLogisticsInfo loadLogisticsInfo(String shopCode, String shipCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCode(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        requireOwnBatch(batch);

        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        Map<String, SalesOrderItem> orderItems = indexByDetailCode(
                orderItemMapper.selectByOrderCode(shopCode, order.getOrderCode(), order.getTableSuffix()));

        ShipBatchLogisticsInfo info = new ShipBatchLogisticsInfo();
        info.setShipCode(batch.getShipCode());
        info.setOrderCode(order.getOrderCode());
        info.setCurrency(order.getCurrency());
        info.setReceiveType(order.getReceiveType());
        info.setTotalAmount(nvl(order.getTotalAmount()));
        info.setPaidAmount(nvl(order.getPaidAmount()));

        BigDecimal committed = committedShipAmount(shopCode, order, orderItems, shipCode);
        info.setCommittedAmount(committed);
        // 负数按 0 显示：已占额度超过已收的情况只可能来自历史数据或人工改库，
        // 摆一个负数在界面上只会让人以为算错了
        BigDecimal allowance = nvl(order.getPaidAmount()).subtract(committed);
        info.setAllowanceAmount(allowance.signum() < 0 ? BigDecimal.ZERO : allowance);

        List<ShipBatchLogisticsInfo.Line> lines = new ArrayList<>();
        for (ShipBatchItem item : batchItemMapper.selectByShipCode(shopCode, shipCode, tableSuffix))
        {
            ShipBatchLogisticsInfo.Line line = new ShipBatchLogisticsInfo.Line();
            line.setDetailCode(item.getDetailCode());
            line.setGoodsCode(item.getGoodsCode());
            line.setGoodsName(item.getGoodsName());
            line.setSpec(item.getSpec());
            line.setUnit(item.getUnit());
            line.setPlannedQty(nvl(item.getQty()));
            SalesOrderItem orderItem = orderItems.get(item.getOrderDetailCode());
            // 订单明细被删时留 0：弹窗还是要打得开，真提交时 requireOrderItem 会拦下来说清楚
            line.setPrice(orderItem == null ? BigDecimal.ZERO : nvl(orderItem.getPrice()));
            lines.add(line);
        }
        info.setItems(lines);
        return info;
    }

    /**
     * 这张订单上已经把付款额度占掉的货值，<b>不含 {@code excludeShipCode} 这张批次</b>。
     *
     * <p>两部分相加：</p>
     * <ol>
     *   <li>已完成批次——那部分量已经回写进订单明细的 shippedqty，从那里算</li>
     *   <li>其它已录入/已打印快递单号的批次——量还在批次明细上，从那里算</li>
     * </ol>
     *
     * <p>单价一律取<b>订单明细</b>的价，不取批次上的：批次表根本不存单价，而订单明细的价
     * 就是客户认的那个价，与客户已付金额同一个币种、同一个口径。</p>
     */
    private BigDecimal committedShipAmount(String shopCode, SalesOrder order,
            Map<String, SalesOrderItem> orderItems, String excludeShipCode)
    {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItem orderItem : orderItems.values())
        {
            total = total.add(nvl(orderItem.getShippedQty()).multiply(nvl(orderItem.getPrice())));
        }
        Map<String, BigDecimal> inFlight = pendingQtyResolver.resolveInStatuses(
                shopCode, order.getOrderCode(), LOGISTICS_COMMITTED_STATES, excludeShipCode);
        for (Map.Entry<String, BigDecimal> entry : inFlight.entrySet())
        {
            SalesOrderItem orderItem = orderItems.get(entry.getKey());
            // 订单明细找不到的行按 0 算而不是报错：这里是「已经占掉多少」的统计，
            // 拿不准的时候少算一点也好过让整个弹窗打不开
            if (orderItem == null) continue;
            total = total.add(nvl(entry.getValue()).multiply(nvl(orderItem.getPrice())));
        }
        return total;
    }

    /**
     * 录入快递单号：备货完成 → 已录入快递单号。<b>一分库存都不动，货也还在仓库里</b>
     * ——扣减在 {@link #completeBatch}，那一步才对应承运商把货取走。
     *
     * <p>这一步是业务员敲定「这次到底发多少」的地方，也是唯一卡客户付款额度的地方，
     * 口径见 {@link IShipBatchService#enterLogistics}。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enterLogistics(String shopCode, String shipCode, String tableSuffix, String logisticsNo,
            String carrier, Map<String, BigDecimal> qtyByDetailCode, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        requireOwnBatch(batch);
        if (ShipBatch.STATUS_LOGISTICS_ENTERED.equals(batch.getStatus()))
        {
            throw new ServiceException("批次已录入快递单号");
        }
        if (ShipBatch.STATUS_COMPLETED.equals(batch.getStatus())) throw new ServiceException("批次已完成");
        if (!ShipBatch.STATUS_PICKED.equals(batch.getStatus()))
        {
            throw new ServiceException("仓库还没点备货完成，不能录快递单号。当前状态：" + batch.getStatus());
        }

        List<ShipBatchItem> items = batchItemMapper.selectByShipCode(shopCode, shipCode, tableSuffix);
        if (items.isEmpty()) throw new ServiceException("批次没有明细，不能录快递单号");

        // 批次建好之后订单还可能被取消、关闭或撤审，往前推之前必须按当前状态再判一次
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        assertOrderShippable(order, "录入快递单号");

        // 物流信息的校验排在订单状态之后：订单已经取消了还去提示「承运商必填」是在指错方向。
        // 校验的是「这一步做完之后表里有没有值」，不是「请求里带没带」——建批次时就填好的，
        // 弹窗原样带出来照样算数，不该逼人重打一遍。
        String finalCarrier = isBlank(carrier) ? batch.getCarrier() : carrier.trim();
        String finalLogisticsNo = isBlank(logisticsNo) ? batch.getLogisticsNo() : logisticsNo.trim();
        if (!SalesOrder.RECEIVE_TYPE_PICKUP.equals(order.getReceiveType()))
        {
            if (isBlank(finalCarrier)) throw new ServiceException("承运商必须填写，货交给谁拉走要记下来");
            if (isBlank(finalLogisticsNo)) throw new ServiceException("快递单号必须填写，没有单号后面查不到这批货");
        }
        // 「上门提货」不要求：客户自己开车来仓库拉，既没有承运商也没有面单。硬要求只会逼人
        // 往框里敲「无」「客户自提」，而假数据比空值更糟——填过之后就分不清「本来就没有」
        // 和「忘了填」。口径与订单侧一致：那边的上门提货也不要求收件地址。
        // 判断用 equals(order.getReceiveType()) 而不是反过来，收货方式为 null 的历史单
        // 会落进「必填」这一支，是有意的：拿不准的时候按要填算。

        // 订单明细上锁：下面要按它的单价算货值，同一事务里的付款额度判断不能被并发改价掀翻
        Map<String, SalesOrderItem> orderItems = indexByDetailCode(
                orderItemMapper.selectByOrderCodeForUpdate(shopCode, order.getOrderCode(), order.getTableSuffix()));

        Map<String, BigDecimal> finalQty = resolveShipQty(items, qtyByDetailCode);
        assertWithinPaidAmount(shopCode, order, orderItems, items, finalQty, shipCode);

        // 校验全过了才动明细：填 0 的行整行删掉，它占的订单量随之放回去
        for (ShipBatchItem item : items)
        {
            BigDecimal qty = finalQty.get(item.getDetailCode());
            if (qty.signum() == 0)
            {
                batchItemMapper.deleteByDetailCode(shopCode, item.getDetailCode(), tableSuffix);
            }
            else if (qty.compareTo(nvl(item.getQty())) != 0)
            {
                batchItemMapper.updateQty(shopCode, item.getDetailCode(), tableSuffix, qty);
            }
        }

        if (batchMapper.enterLogistics(shopCode, shipCode, tableSuffix, finalLogisticsNo, finalCarrier,
                username, new Date()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * 把请求里的「本次发货数量」对到各行上，并挡住非法值。没传的行按原计划量发。
     *
     * <p><b>只能往下调</b>：上限是建批次时排的计划量。往上加等于绕过建单时那道可排量校验
     * （订单数量 − 已发 − 其它批次已排），而这时候批次早就提交给仓库、货也已经按计划量备好了，
     * 凭空多发的那部分根本没有货。真要多发，作废重排或者另开一张批次。</p>
     *
     * @return key 为批次明细代码，每一行都有值；0 表示这一行本次不发
     */
    private Map<String, BigDecimal> resolveShipQty(List<ShipBatchItem> items,
            Map<String, BigDecimal> qtyByDetailCode)
    {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        boolean anyPositive = false;
        for (ShipBatchItem item : items)
        {
            BigDecimal planned = nvl(item.getQty());
            BigDecimal wanted = qtyByDetailCode == null ? null : qtyByDetailCode.get(item.getDetailCode());
            if (wanted == null) wanted = planned;
            if (wanted.signum() < 0)
            {
                throw new ServiceException("商品'" + item.getGoodsName() + "'的本次发货数量不能是负数");
            }
            if (wanted.compareTo(planned) > 0)
            {
                throw new ServiceException("商品'" + item.getGoodsName() + "'本批次只排了 " + planned
                        + "，本次发货数量不能填 " + wanted + "。要多发请让仓库驳回后重排批次，或者另开一张");
            }
            if (wanted.signum() > 0) anyPositive = true;
            result.put(item.getDetailCode(), wanted);
        }
        if (!anyPositive)
        {
            throw new ServiceException("每一行都填了 0，这张批次等于什么都不发。"
                    + "整张批次不发了请直接取消，别用录单清空");
        }
        return result;
    }

    /**
     * 付款额度校验：整单累计的发货货值不能超过客户已付金额。
     *
     * <p>口径与报错文案都见 {@link IShipBatchService#enterLogistics}。客户已付金额为 0 的订单
     * 一分货都发不出去，这是有意的——规则就是「发出去的货值不能超过收到的钱」。</p>
     */
    private void assertWithinPaidAmount(String shopCode, SalesOrder order,
            Map<String, SalesOrderItem> orderItems, List<ShipBatchItem> items,
            Map<String, BigDecimal> finalQty, String shipCode)
    {
        BigDecimal thisBatch = BigDecimal.ZERO;
        for (ShipBatchItem item : items)
        {
            SalesOrderItem orderItem = requireOrderItem(orderItems, item);
            thisBatch = thisBatch.add(finalQty.get(item.getDetailCode()).multiply(nvl(orderItem.getPrice())));
        }
        BigDecimal committed = committedShipAmount(shopCode, order, orderItems, shipCode);
        BigDecimal paid = nvl(order.getPaidAmount());
        BigDecimal after = committed.add(thisBatch);
        if (after.compareTo(paid) <= 0) return;

        String currency = isBlank(order.getCurrency()) ? "" : order.getCurrency() + " ";
        throw new ServiceException("发货货值超出客户已付金额。订单总价 " + currency + plain(order.getTotalAmount())
                + "，客户已付 " + currency + plain(paid)
                + "，此前已发/在途占用 " + currency + plain(committed)
                + "，本次要发 " + currency + plain(thisBatch)
                + "，合计 " + currency + plain(after)
                + "，超出 " + currency + plain(after.subtract(paid))
                + "。请调减本次发货数量，或等财务收到款再发");
    }

    /** 金额按两位小数写进报错文案。用 toPlainString 免得大额出现科学计数法 */
    private static String plain(BigDecimal value)
    {
        return nvl(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int printExpressLabel(String shopCode, String shipCode, String tableSuffix, String username)
    {
        return transit(shopCode, shipCode, tableSuffix, ShipBatch.STATUS_LOGISTICS_ENTERED,
                ShipBatch.STATUS_LABEL_PRINTED, "打印快递单号", username, false);
    }

    /**
     * 驳回到「待确认」。货还没被承运商取走的五格都能驳。
     *
     * <p>不校验订单状态，理由同 {@link #cancelBatch}：驳回是往回撤，撤不动才是麻烦——
     * 订单万一已经离开可发货区间，这张批次更得能退回去让人处理掉。</p>
     *
     * <p>库存、订单已发量都不用管：到「已打印快递单号」为止这条链一次都没碰过库存，
     * 订单的 shippedqty 也只在完成那一步才加。批次占的已排量按状态算，
     * 「待确认」同样计入（见 ShipBatchItemMapper.selectPendingQtyByOrders），
     * 所以退回去之后这批量还在，不会被别的批次抢走。</p>
     *
     * <p><b>付款额度也自动让了出来</b>：额度只被「已录入/已打印快递单号」两格占着
     * （见 {@link #LOGISTICS_COMMITTED_STATES}），批次一退回「待确认」就不在那两格里了，
     * 同一张订单的其它批次立刻能用这部分额度。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectBatch(String shopCode, String shipCode, String tableSuffix, String reason, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        if (isBlank(reason)) throw new ServiceException("驳回原因必须填写");
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");

        String status = batch.getStatus();
        if (ShipBatch.STATUS_COMPLETED.equals(status))
        {
            throw new ServiceException("批次已完成，货已经被承运商取走，不能驳回。请走销售退货流程");
        }
        if (ShipBatch.STATUS_PENDING.equals(status))
        {
            throw new ServiceException("批次还在「待确认」，本来就在业务员手里，不用驳回");
        }
        if (!REJECTABLE_STATES.contains(status))
        {
            throw new ServiceException("批次当前状态「" + status + "」不能驳回");
        }

        if (batchMapper.rejectToPending(shopCode, shipCode, tableSuffix, status, username, new Date(),
                reason.trim()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * 完成：库存在这一步才真正扣，对应现实中承运商把货取走那一刻。
     * 开出库单 → 同事务过账 → 回写订单已发量，三步一个事务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String completeBatch(String shopCode, String shipCode, String tableSuffix,
            Map<String, List<String>> serials, Map<String, String> batchCodes, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        if (ShipBatch.STATUS_COMPLETED.equals(batch.getStatus())) throw new ServiceException("批次已完成");
        if (!ShipBatch.STATUS_LABEL_PRINTED.equals(batch.getStatus()))
        {
            throw new ServiceException("批次还没打印快递单号，不能完成。当前状态：" + batch.getStatus());
        }

        List<ShipBatchItem> items = batchItemMapper.selectByShipCode(shopCode, shipCode, tableSuffix);
        if (items.isEmpty()) throw new ServiceException("批次没有明细，不能完成");

        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        // 发货之后订单还可能被取消、关闭或撤审，扣库存之前必须按当前状态再判一次
        assertOrderShippable(order, "完成发货");
        String orderSuffix = order.getTableSuffix();

        Map<String, SalesOrderItem> orderItems = indexByDetailCode(orderItemMapper.selectByOrderCodeForUpdate(
                shopCode, order.getOrderCode(), orderSuffix));

        // 出库单要动的库存行，动手之前一次锁完。见 OrderStockLockCoordinator 类注释。
        stockLockCoordinator.lockForShip(shopCode, batch.getWarehouseCode(), items);

        // ---- 第 1 步：建销售出库单 ----
        // 订单不再预占库存，所以这里没有「核销预占」那一步：可用量够不够、能不能拿到货，
        // 全部交给下一步的出库单锁定扣减，不够就在那一刻报出来。
        List<IOutboundService.OutboundItem> outboundItems = new ArrayList<>();
        for (ShipBatchItem item : items)
        {
            SalesOrderItem orderItem = requireOrderItem(orderItems, item);
            IOutboundService.OutboundItem out = new IOutboundService.OutboundItem();
            out.setGoodsCode(item.getGoodsCode());
            out.setGoodsName(item.getGoodsName());
            out.setSpec(item.getSpec());
            out.setUnit(item.getUnit());
            out.setQty(item.getQty());
            // 销售单价带订单价过去，出库单的销售金额才对得上订单
            out.setSalePrice(orderItem.getPrice());
            // 挑批次要靠它：这一单自己的定制批次优先发掉，别人的一律跳过
            out.setOrderDetailCode(orderItem.getDetailCode());
            // 序列号与出库批次由完成这一步指定，批次单据不存。传空没关系，
            // 出库单会按商品档案的批次策略判断该不该有，缺了在那里报错
            applyTracking(out, item, serials, batchCodes);
            outboundItems.add(out);
        }
        String remark = "销售发货，来源批次" + shipCode + "，订单" + order.getOrderCode();
        DocumentMain document = outboundService.createOutbound(shopCode, InventoryDocumentTypes.SALES_OUTBOUND,
                batch.getWarehouseCode(), StockState.available(), null, null,
                shipCode, InventoryDocumentTypes.SHIP_BATCH_SOURCE, outboundItems, remark, username);

        // ---- 第 2 步：同事务审核，完成锁定、扣减与流水 ----
        outboundService.confirmOutbound(shopCode, document.getDocCode(), document.getTableSuffix(), true, username);

        // ---- 第 3 步：回写订单已发量，重算进度 ----
        for (ShipBatchItem item : items)
        {
            SalesOrderItem orderItem = requireOrderItem(orderItems, item);
            if (orderItemMapper.increaseShippedQty(shopCode, orderItem.getDetailCode(), orderSuffix, item.getQty()) != 1)
            {
                throw new ServiceException("商品'" + item.getGoodsName() + "'发货量超过订单数量");
            }
        }

        if (batchMapper.updateStatus(shopCode, shipCode, tableSuffix, ShipBatch.STATUS_COMPLETED,
                ShipBatch.STATUS_LABEL_PRINTED, username, new Date()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }

        orderService.refreshShipProgress(shopCode, order.getOrderCode(), orderSuffix);
        return document.getDocCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelBatch(String shopCode, String shipCode, String tableSuffix, String username)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        requireOwnBatch(batch);
        if (ShipBatch.STATUS_COMPLETED.equals(batch.getStatus()))
        {
            throw new ServiceException("批次已完成，货已经被承运商取走，不能取消。请走退货流程");
        }
        if (ShipBatch.STATUS_CANCELLED.equals(batch.getStatus())) throw new ServiceException("批次已取消");
        // 录完快递单号之后不直接放行取消，是因为这两件事的责任方不同：单子上带的是业务员
        // 自己填的面单信息，仓库正按它打单贴箱，该由仓库先点「驳回」把批次退回待确认
        // （顺带写清为什么退），业务员再决定是改还是废。让订单侧一键作废一张仓库已经在办的
        // 批次，仓库只会看见行突然消失。退回「待确认」之后照样能取消。
        if (!CANCELLABLE_STATES.contains(batch.getStatus()))
        {
            throw new ServiceException("批次当前状态「" + batch.getStatus()
                    + "」不能直接取消，请先让仓库在「仓库发货管理」点「驳回」退回待确认，再取消");
        }

        // 批次只是「发货计划」，到这一步为止没碰过库存：锁定与扣减都发生在完成那一步的出库单上，
        // 而已完成的批次根本走不到这里。所以取消不需要退还任何东西。
        if (batchMapper.updateStatus(shopCode, shipCode, tableSuffix, ShipBatch.STATUS_CANCELLED,
                batch.getStatus(), username, new Date()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * @param ownerOnly 要不要认归属。业务员的「提交仓库」要认；仓库的两个打印动作不认——
     *                  仓库处理的本来就是别人建的批次，认了等于仓库谁的单都动不了。
     */
    private int transit(String shopCode, String shipCode, String tableSuffix, String expected,
            String target, String action, String username, boolean ownerOnly)
    {
        MonthTableSuffix.require(tableSuffix);
        ShipBatch batch = batchMapper.selectByCodeForUpdate(shopCode, shipCode, tableSuffix);
        if (batch == null) throw new ServiceException("发货批次不存在");
        if (ownerOnly) requireOwnBatch(batch);
        if (!expected.equals(batch.getStatus()))
        {
            throw new ServiceException("批次当前状态为「" + batch.getStatus() + "」，不能转为「" + target + "」");
        }
        // 订单在批次建好之后可能已经离开可发货区间，往前推之前先按当前状态判一次。
        // 取消批次不走这里——那是订单取消/关闭前必须先做的一步，任何状态都得放行。
        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, batch.getOrderCode());
        if (order == null) throw new ServiceException("来源订单不存在");
        assertOrderShippable(order, action);
        if (batchMapper.updateStatus(shopCode, shipCode, tableSuffix, target, expected, username, new Date()) != 1)
        {
            throw new ServiceException("批次状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * 校验本批次要发的量排不排得下。
     *
     * <p>可排量 = 订单数量 - 已发量 - 其他未完成批次已排的量。第三项不能省：
     * 已发量只统计真正出过库的，光看它会让同一批货被排进两张批次。</p>
     */
    private void validateShippableQty(String shopCode, SalesOrder order, List<SalesOrderItem> orderItems,
            List<ShipBatchItem> items, String excludeShipCode)
    {
        Map<String, SalesOrderItem> indexed = indexByDetailCode(orderItems);
        Map<String, BigDecimal> pending = pendingQtyResolver.resolve(shopCode, order.getOrderCode(), excludeShipCode);

        Map<String, BigDecimal> wanted = new LinkedHashMap<>();
        for (ShipBatchItem item : items)
        {
            if (isBlank(item.getOrderDetailCode())) throw new ServiceException("发货明细必须关联订单明细");
            if (nvl(item.getQty()).signum() <= 0) throw new ServiceException("发货数量必须大于 0");
            wanted.merge(item.getOrderDetailCode(), item.getQty(), BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> entry : wanted.entrySet())
        {
            SalesOrderItem orderItem = indexed.get(entry.getKey());
            if (orderItem == null) throw new ServiceException("订单明细'" + entry.getKey() + "'不存在");
            BigDecimal shippable = nvl(orderItem.getQty())
                    .subtract(nvl(orderItem.getShippedQty()))
                    .subtract(nvl(pending.get(entry.getKey())));
            if (entry.getValue().compareTo(shippable) > 0)
            {
                throw new ServiceException("商品'" + orderItem.getGoodsName() + "'可发数量只有 " + shippable
                        + "，本次要发 " + entry.getValue());
            }
        }
    }

    /** 明细补齐：单号、商品快照。商品信息一律取订单明细的快照，保证两边一致。 */
    private void prepareItems(ShipBatch batch, List<SalesOrderItem> orderItems)
    {
        Map<String, SalesOrderItem> indexed = indexByDetailCode(orderItems);
        for (ShipBatchItem item : batch.getItems())
        {
            SalesOrderItem orderItem = indexed.get(item.getOrderDetailCode());
            if (orderItem == null) throw new ServiceException("订单明细'" + item.getOrderDetailCode() + "'不存在");
            item.setShopCode(batch.getShopCode());
            item.setShipCode(batch.getShipCode());
            item.setDetailCode(codeGenerator.nextShipBatchDetailCode());
            item.setGoodsCode(orderItem.getGoodsCode());
            item.setGoodsName(orderItem.getGoodsName());
            item.setSpec(orderItem.getSpec());
            item.setUnit(orderItem.getUnit());
            if (isBlank(item.getSourceCode())) item.setSourceCode(orderItem.getSourceCode());
        }
    }

    /**
     * 订单必须处在「已确认 / 备货中 / 发货中」才允许往下走。
     *
     * <p>白名单，不是黑名单。以前这里只挡草稿、已取消、已完成，剩下三个状态是漏的：
     * 待审核与已驳回还没过审也没预占，替它们发货等于绕过审核；已关闭是人工终止，
     * 剩余部分本就不该再发。</p>
     *
     * <p>建批次、确认、打备货单、备货完成、录快递单号、打面单、完成七处都要过这一关。
     * 只在建批次时挡不住——批次建好之后
     * 订单还可能被取消或关闭，那时批次仍躺在「待确认」，一路点下去货就真出库了，
     * 而 {@code deriveState} 对已取消/已关闭是原样返回，订单状态还挂着「已取消」，
     * 已发数量却涨了，账就此对不上。</p>
     *
     * @param action 用在报错文案里的动作名，例如「发货」
     */
    private void assertOrderShippable(SalesOrder order, String action)
    {
        String state = order.getOrderState();
        if (SalesOrder.isShippableState(state)) return;
        if (SalesOrder.STATE_DRAFT.equals(state)) throw new ServiceException("订单尚未提交审核，不能" + action);
        if (SalesOrder.STATE_PENDING.equals(state)) throw new ServiceException("订单还在待审核，未预占库存，不能" + action);
        if (SalesOrder.STATE_REJECTED.equals(state)) throw new ServiceException("订单已驳回，请改单后重新提交审核，不能" + action);
        if (SalesOrder.STATE_FINISHED.equals(state)) throw new ServiceException("订单已完成，不能再" + action);
        if (SalesOrder.STATE_CANCELLED.equals(state)) throw new ServiceException("订单已取消，不能" + action);
        if (SalesOrder.STATE_CLOSED.equals(state)) throw new ServiceException("订单已关闭，剩余部分不再发货，不能" + action);
        throw new ServiceException("订单当前状态「" + state + "」不能" + action);
    }

    /**
     * 把发货时录的序列号 / 出库批次贴到出库明细行上。
     *
     * <p>按发货批次明细代码取，不按商品编码：同一张批次里同一个商品只会有一行，但用明细代码
     * 定位才不依赖这个约定。传进来是空的就什么都不做——商品不需要跟踪信息时本来就该是空的，
     * 需要却没传的，销售出库单会在过账前拦下来并说清楚缺什么。</p>
     */
    private void applyTracking(IOutboundService.OutboundItem out, ShipBatchItem item,
            Map<String, List<String>> serials, Map<String, String> batchCodes)
    {
        List<String> serialNumbers = serials == null ? null : serials.get(item.getDetailCode());
        if (serialNumbers != null && !serialNumbers.isEmpty())
        {
            out.setSerialNumbers(new ArrayList<>(serialNumbers));
        }
        String batchCode = batchCodes == null ? null : batchCodes.get(item.getDetailCode());
        if (batchCode != null && !batchCode.trim().isEmpty())
        {
            out.setBatchCode(batchCode.trim());
        }
    }

    private SalesOrderItem requireOrderItem(Map<String, SalesOrderItem> indexed, ShipBatchItem item)
    {
        SalesOrderItem orderItem = indexed.get(item.getOrderDetailCode());
        if (orderItem == null) throw new ServiceException("订单明细'" + item.getOrderDetailCode() + "'不存在");
        return orderItem;
    }

    private Map<String, SalesOrderItem> indexByDetailCode(List<SalesOrderItem> items)
    {
        Map<String, SalesOrderItem> map = new LinkedHashMap<>();
        for (SalesOrderItem item : items) map.put(item.getDetailCode(), item);
        return map;
    }

    private static BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
