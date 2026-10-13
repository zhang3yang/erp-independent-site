package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ContactPhoneUtils;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.SalesReship;
import com.ruoyi.system.domain.SalesReshipItem;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.SalesOrderItemMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.mapper.SalesReshipItemMapper;
import com.ruoyi.system.mapper.SalesReshipMapper;
import com.ruoyi.system.service.IOutboundService;
import com.ruoyi.system.service.ISalesOrderService;
import com.ruoyi.system.service.ISalesReshipService;
import com.ruoyi.system.utils.MonthTableSuffix;
import com.ruoyi.system.utils.OwnershipScope;

@Service
public class SalesReshipServiceImpl implements ISalesReshipService
{
    @Autowired private SalesReshipMapper reshipMapper;
    @Autowired private SalesReshipItemMapper itemMapper;
    @Autowired private SalesOrderMapper orderMapper;
    @Autowired private SalesOrderItemMapper orderItemMapper;
    @Autowired private InventoryWarehouseMapper warehouseMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    @Autowired private ISalesOrderService orderService;
    @Autowired private IOutboundService outboundService;

    /**
     * 横向权限点：查看全部销售补发单。不勾的人只看得到自己建的补发单。
     *
     * <p>⚠ 仓库要在这个页面点「发货」，所以仓库角色也得勾上，否则它列表里一条都没有。
     * 发货动作本身不认归属——仓库处理的本来就是别人建的单。</p>
     */
    public static final String PERMI_VIEW_ALL = "order:salesreship:all";

    /** 横向权限：不是自己建的补发单，看不到也删不了。 */
    private void requireOwnReship(SalesReship reship)
    {
        if (reship == null) return;
        OwnershipScope.requireOwn(reship.getCreateUser(), "销售补发单", reship.getReshipCode(), PERMI_VIEW_ALL);
    }

    @Override
    public List<SalesReship> selectList(SalesReship query)
    {
        // 一律由服务层说了算：ownerScope 是内部载体，前端塞得进来，不能信
        query.setOwnerScope(OwnershipScope.ownerFilter(PERMI_VIEW_ALL));
        return reshipMapper.selectList(query);
    }

    @Override
    public SalesReship selectDetail(String shopCode, String reshipCode)
    {
        SalesReship reship = reshipMapper.selectByCode(shopCode, reshipCode);
        requireOwnReship(reship);
        if (reship != null) reship.setItems(itemMapper.selectByReshipCode(shopCode, reshipCode));
        return reship;
    }

    /**
     * 可补发的原订单：本月里真正发出过货、且还没建过补发单的。
     *
     * <p>固定 3 次查询——已补发过的订单号一次、全月已发货明细一次、单头一次，
     * 与当月订单张数无关。「已发出过」这个条件下推到了 SQL，不必把整月订单
     * 拉回来再逐张回表查明细。</p>
     */
    @Override
    public List<SalesOrder> selectEligibleOrders(String shopCode, String tableSuffix)
    {
        MonthTableSuffix.require(tableSuffix);
        Set<String> used = new HashSet<>(reshipMapper.selectOriginalOrderCodes(shopCode));

        // 先按明细定候选：发出过货的行才有补发的意义，一张单一行都没发过就不用查单头了
        Map<String, List<SalesOrderItem>> itemsByOrder = new LinkedHashMap<>();
        for (SalesOrderItem item : orderItemMapper.selectShippedItemsByMonth(shopCode, tableSuffix))
        {
            if (used.contains(item.getOrderCode())) continue;
            itemsByOrder.computeIfAbsent(item.getOrderCode(), key -> new ArrayList<>()).add(item);
        }
        if (itemsByOrder.isEmpty()) return new ArrayList<>();

        // 横向权限：这是「新建补发」那个下拉的数据源，按**订单**的归属筛，不是按补发单的——
        // 业务员不该在这里把同事的订单挑出来补发。
        String orderOwner = OwnershipScope.ownerFilter(SalesOrderServiceImpl.PERMI_VIEW_ALL);
        List<SalesOrder> result = new ArrayList<>();
        for (SalesOrder order : orderMapper.selectByCodes(shopCode, tableSuffix,
                new ArrayList<>(itemsByOrder.keySet())))
        {
            if (orderOwner != null && !orderOwner.equals(order.getStaffCode())) continue;
            order.setTableSuffix(tableSuffix);
            order.setItems(itemsByOrder.get(order.getOrderCode()));
            result.add(order);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReship create(String shopCode, SalesReship command, String username)
    {
        if (command == null || isBlank(command.getOriginalOrderCode()))
            throw new ServiceException("必须选择原销售订单");
        if (command.getItems() == null || command.getItems().isEmpty())
            throw new ServiceException("补发明细不能为空");
        if (reshipMapper.selectByOriginalOrder(shopCode, command.getOriginalOrderCode().trim()) != null)
            throw new ServiceException("该原订单已经创建过补发单，每张原订单只能补发一次");

        SalesOrder order = orderService.findOrderAcrossMonths(shopCode, command.getOriginalOrderCode());
        if (order == null) throw new ServiceException("原销售订单不存在");

        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(shopCode, command.getWarehouseCode());
        if (warehouse == null) throw new ServiceException("补发仓库不存在");

        List<SalesOrderItem> originalItems = orderItemMapper.selectByOrderCodeForUpdate(
                shopCode, order.getOrderCode(), order.getTableSuffix());
        Map<String, SalesOrderItem> originalByCode = new HashMap<>();
        for (SalesOrderItem item : originalItems) originalByCode.put(item.getDetailCode(), item);

        Set<String> picked = new HashSet<>();
        List<SalesReshipItem> items = new ArrayList<>();
        int lineNo = 1;
        for (SalesReshipItem input : command.getItems())
        {
            String detailCode = input == null ? null : input.getOriginalOrderDetailCode();
            if (isBlank(detailCode)) throw new ServiceException("补发明细必须关联原订单明细");
            if (!picked.add(detailCode)) throw new ServiceException("同一原订单明细不能重复选择");
            SalesOrderItem original = originalByCode.get(detailCode);
            if (original == null) throw new ServiceException("原订单明细不存在：" + detailCode);
            BigDecimal qty = nvl(input.getQty());
            if (qty.signum() <= 0) throw new ServiceException("商品“" + original.getGoodsName() + "”补发数量必须大于0");
            // 补发的是已经实际发出去、但需要重新寄送的货，不能拿尚未履约的数量绕过正常发货。
            if (qty.compareTo(nvl(original.getShippedQty())) > 0)
                throw new ServiceException("商品“" + original.getGoodsName() + "”最多可补发 " + nvl(original.getShippedQty()));

            SalesReshipItem item = new SalesReshipItem();
            item.setShopCode(shopCode);
            item.setDetailCode(codeGenerator.nextSalesReshipDetailCode());
            item.setOriginalOrderDetailCode(original.getDetailCode());
            item.setLineNo(lineNo++);
            item.setGoodsCode(original.getGoodsCode());
            item.setGoodsName(original.getGoodsName());
            item.setSpec(original.getSpec());
            item.setUnit(original.getUnit());
            item.setQty(qty);
            item.setRemark(input.getRemark());
            items.add(item);
        }

        command.setShopCode(shopCode);
        command.setReshipCode(codeGenerator.nextSalesReshipCode());
        command.setOriginalOrderCode(order.getOrderCode());
        command.setOriginalOrderSuffix(order.getTableSuffix());
        command.setPlatOrderId(order.getPlatOrderId());
        command.setPlatCode(order.getPlatCode());
        command.setPlatName(order.getPlatName());
        command.setShopStoreCode(order.getShopStoreCode());
        command.setShopStoreName(order.getShopStoreName());
        command.setReceivePerson(valueOrDefault(command.getReceivePerson(), order.getReceivePerson()));
        command.setReceiveAddress(valueOrDefault(command.getReceiveAddress(), order.getReceiveAddress()));
        command.setReceiveTelephone(valueOrDefault(command.getReceiveTelephone(), order.getReceiveTelephone()));
        // 校验放在沿用原单之后：不填是允许的（沿用原单的号码），落库的那个值必须像一串电话。
        // 口径见 ContactPhoneUtils，与销售订单的收件电话同一套——这里按 11 位手机号收的话，
        // 原单上存的虚拟号、固话、多号码在补发这一步会全部卡住，而它们是从订单原样带过来的
        ContactPhoneUtils.validateRequired(command.getReceiveTelephone(), "联系电话");
        command.setReceiveZipCode(valueOrDefault(command.getReceiveZipCode(), order.getReceiveZipCode()));
        command.setWarehouseName(warehouse.getWareName());
        command.setStatus(SalesReship.STATUS_PENDING);
        command.setCreateUser(username);
        for (SalesReshipItem item : items) item.setReshipCode(command.getReshipCode());

        try
        {
            reshipMapper.insert(command);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("该原订单已经创建过补发单，每张原订单只能补发一次");
        }
        itemMapper.batchInsert(items);
        command.setItems(items);
        return command;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ship(String shopCode, String reshipCode, String carrier, String logisticsNo, String username)
    {
        SalesReship reship = reshipMapper.selectByCodeForUpdate(shopCode, reshipCode);
        if (reship == null) throw new ServiceException("销售补发单不存在");
        if (SalesReship.STATUS_SHIPPED.equals(reship.getStatus()))
            throw new ServiceException("该补发单已经整单发货，不能再次补发");
        if (!SalesReship.STATUS_PENDING.equals(reship.getStatus()))
            throw new ServiceException("补发单当前状态不能发货：" + reship.getStatus());

        List<SalesReshipItem> items = itemMapper.selectByReshipCode(shopCode, reshipCode);
        if (items.isEmpty()) throw new ServiceException("补发单没有明细");

        List<IOutboundService.OutboundItem> outboundItems = new ArrayList<>();
        for (SalesReshipItem item : items)
        {
            IOutboundService.OutboundItem out = new IOutboundService.OutboundItem();
            out.setGoodsCode(item.getGoodsCode());
            out.setGoodsName(item.getGoodsName());
            out.setSpec(item.getSpec());
            out.setUnit(item.getUnit());
            out.setQty(item.getQty());
            // 补发不是二次销售，销售金额必须为 0；库存成本仍按实际出库成本结转。
            out.setSalePrice(BigDecimal.ZERO);
            outboundItems.add(out);
        }

        String remark = "销售补发，补发单" + reshipCode + "，原订单" + reship.getOriginalOrderCode();
        DocumentMain document = outboundService.createOutbound(shopCode, InventoryDocumentTypes.SALES_OUTBOUND,
                reship.getWarehouseCode(), StockState.available(), null, reship.getShopStoreName(),
                reshipCode, InventoryDocumentTypes.SALES_RESHIP_SOURCE, outboundItems, remark, username);
        outboundService.confirmOutbound(shopCode, document.getDocCode(), document.getTableSuffix(), true, username);

        if (reshipMapper.updateShipped(shopCode, reshipCode, trimToNull(carrier), trimToNull(logisticsNo),
                document.getDocCode(), username) != 1)
            throw new ServiceException("补发单状态已变化，请刷新后重试");
        return document.getDocCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String shopCode, String reshipCode)
    {
        if (isBlank(reshipCode)) throw new ServiceException("补发单号不能为空");
        SalesReship reship = reshipMapper.selectByCodeForUpdate(shopCode, reshipCode.trim());
        if (reship == null) throw new ServiceException("销售补发单不存在");
        requireOwnReship(reship);
        if (!SalesReship.STATUS_PENDING.equals(reship.getStatus()))
        {
            throw new ServiceException("补发单已" + reship.getStatus() + "，不能删除。已发出的货请走销售退货");
        }

        itemMapper.deleteByReshipCode(shopCode, reship.getReshipCode());
        // 状态条件也写在 delete 语句里：拿到行锁到这一步之间若被人发了货，这里会删 0 行
        if (reshipMapper.deletePending(shopCode, reship.getReshipCode()) != 1)
        {
            throw new ServiceException("补发单状态已变化，请刷新后重试");
        }
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private String trimToNull(String value) { return isBlank(value) ? null : value.trim(); }
    private String valueOrDefault(String value, String fallback) { return isBlank(value) ? fallback : value.trim(); }
}
