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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PriceMaskUtils;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.domain.PurchaseOrderItem;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.utils.GoodsSummaryBuilder;
import com.ruoyi.system.utils.SerialNumberRemarkCodec;
import com.ruoyi.system.service.IPurchaseOrderService;

/**
 * 采购订单Service业务层处理
 */
@Service
public class PurchaseOrderServiceImpl implements IPurchaseOrderService
{
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private InventoryGoodsMapper inventoryGoodsMapper;

    @Autowired
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    /** 退款时效窗口：最后一笔付款完成后 3 天内才让退，过期的采购单不进下拉 */
    @Autowired
    private RefundWindowResolver refundWindowResolver;

    /** 税率百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 查询采购订单
     *
     * @param id 采购订单ID
     * @return 采购订单
     */
    @Override
    public PurchaseOrder selectPurchaseOrderById(String shopCode, Long id)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderById(shopCode, id);
        if (order != null)
        {
            List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByPurchaseCode(
                    order.getShopCode(), order.getPurchaseCode());
            decodeSerialNumbers(items);
            order.setItems(items);
        }
        return order;
    }

    /**
     * 查询采购订单（根据商家编码和采购单号）
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @return 采购订单
     */
    @Override
    public PurchaseOrder selectPurchaseOrderByCode(String shopCode, String purchaseCode)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(shopCode, purchaseCode);
        if (order != null)
        {
            List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByPurchaseCode(shopCode, purchaseCode);
            decodeSerialNumbers(items);
            order.setItems(items);
        }
        return order;
    }

    /**
     * 查询采购订单列表
     *
     * @param purchaseOrder 采购订单
     * @return 采购订单集合
     */
    @Override
    public List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder)
    {
        return purchaseOrderMapper.selectPurchaseOrderList(purchaseOrder);
    }

    @Override
    public List<PurchaseOrder> selectPayableOrders(String shopCode, String includeCode)
    {
        return purchaseOrderMapper.selectPayableOrders(shopCode, trimToNull(includeCode));
    }

    /**
     * 可退款采购单。付过钱只是前提，还要在退款时效窗口内——
     * 最后一笔付款完成 3 天后这张单会自动从下拉里消失。
     *
     * <p>includeCode 指定的那一张<b>不受窗口限制</b>：改一笔已存的退款草稿时，
     * 它绑的单可能在草稿存下之后就过期了，把它剔掉等于逼人重选，而这笔退款本来就是
     * 窗口内发起的。窗口只管「新发起」，不管「已经在途」。</p>
     */
    @Override
    public List<PurchaseOrder> selectRefundableOrders(String shopCode, String includeCode)
    {
        String keep = trimToNull(includeCode);
        List<PurchaseOrder> candidates = purchaseOrderMapper.selectRefundableOrders(shopCode, keep);
        Map<String, Date> settled = refundWindowResolver.lastSettledTimes(
                shopCode, FinanceTransaction.BIZ_PURCHASE);
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrder order : candidates)
        {
            Date last = settled.get(order.getPurchaseCode());
            if (order.getPurchaseCode().equals(keep))
            {
                if (last != null) order.setRefundDeadline(refundWindowResolver.deadline(last));
                result.add(order);
                continue;
            }
            if (!refundWindowResolver.withinWindow(last))
            {
                continue;
            }
            order.setRefundDeadline(refundWindowResolver.deadline(last));
            result.add(order);
        }
        return result;
    }

    private void decodeSerialNumbers(List<PurchaseOrderItem> items)
    {
        if (items == null) return;
        for (PurchaseOrderItem item : items)
        {
            item.setSerialNumbers(SerialNumberRemarkCodec.decode(item.getSerialNumbersText()));
        }
    }

    /**
     * 新增采购订单
     *
     * @param purchaseOrder 采购订单
     * @return 结果
     */
    @Override
    @Transactional
    public int insertPurchaseOrder(PurchaseOrder purchaseOrder)
    {
        // 采购订单号始终由服务端生成，忽略客户端传入值。
        purchaseOrder.setPurchaseCode(documentCodeGenerator.nextPurchaseOrderCode());

        // 设置初始状态
        purchaseOrder.setStatus("draft");
        normalizeEstimatedFreight(purchaseOrder);

        // 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty())
        {
            validatePurchaseItems(purchaseOrder.getShopCode(), purchaseOrder.getItems());
            int lineNo = 1;
            for (PurchaseOrderItem item : purchaseOrder.getItems())
            {
                // 设置商家编码、采购单号和行号
                item.setShopCode(purchaseOrder.getShopCode());
                item.setPurchaseCode(purchaseOrder.getPurchaseCode());
                item.setDetailCode(documentCodeGenerator.nextPurchaseOrderDetailCode());
                item.setLineNo(lineNo++);
                item.setReceivedQty(BigDecimal.ZERO);
                item.setSerialNumbersText(SerialNumberRemarkCodec.encode(item.getSerialNumbers()));
            }
            totalAmount =
                    applyLineAmounts(purchaseOrder.getItems());

            // 插入明细
            purchaseOrderItemMapper.batchInsertPurchaseOrderItem(purchaseOrder.getItems());
        }
        // 总金额一律由明细算出，没有明细就是 0。原先这一行写在 if 里面，
        // 明细为空时前端传什么就存什么——一张 0 行明细、金额随便填的采购单，
        // 财务照样能选中它付款。
        purchaseOrder.setTotalAmount(totalAmount);
        // 商品内容摘要跟总金额一样，一律由明细算出：明细删光了摘要也要跟着清空，
        // 否则列表上会挂着一句「可乐等3种」而单子里一行都没有。
        purchaseOrder.setGoodsSummary(GoodsSummaryBuilder.build(purchaseOrder.getItems(),
                PurchaseOrderItem::getGoodsCode, PurchaseOrderItem::getGoodsName));

        // 插入主表
        return purchaseOrderMapper.insertPurchaseOrder(purchaseOrder);
    }

    /**
     * 修改采购订单
     *
     * @param purchaseOrder 采购订单
     * @return 结果
     */
    @Override
    @Transactional
    public int updatePurchaseOrder(PurchaseOrder purchaseOrder)
    {
        // 只有草稿状态才能修改
        PurchaseOrder existing = purchaseOrderMapper.selectPurchaseOrderById(
                purchaseOrder.getShopCode(), purchaseOrder.getId());
        if (existing == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if (!"draft".equals(existing.getStatus()))
        {
            throw new ServiceException("只有草稿状态的订单才能修改");
        }
        purchaseOrder.setPurchaseCode(existing.getPurchaseCode());

        List<PurchaseOrderItem> originalItems = purchaseOrderItemMapper.selectItemsByPurchaseCode(
                purchaseOrder.getShopCode(), existing.getPurchaseCode());
        Map<Long, PurchaseOrderItem> originalItemMap = new HashMap<>();
        for (PurchaseOrderItem originalItem : originalItems)
        {
            originalItemMap.put(originalItem.getId(), originalItem);
        }
        // 金额守卫必须在归一化和校验之前：归一化会把 null 运费写成 0，
        // 校验会因为 null 单价直接抛错，两者都发生在前端回传的 null 被换回原值之前。
        keepMaskedCostPrices(purchaseOrder, existing, originalItemMap);

        // 状态、审核人、审核时间、驳回原因都不在 updatePurchaseOrder 那条 SQL 的更新列里，
        // 所以这里不用（也不能）从前端的表单对象里取——它们只由状态流转动作维护。
        normalizeEstimatedFreight(purchaseOrder);

        if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty())
        {
            validatePurchaseItems(purchaseOrder.getShopCode(), purchaseOrder.getItems());
        }

        // 删除原有明细
        purchaseOrderItemMapper.deleteItemsByPurchaseCode(purchaseOrder.getShopCode(), purchaseOrder.getPurchaseCode());

        // 重新计算总金额并插入新明细
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty())
        {
            int lineNo = 1;
            for (PurchaseOrderItem item : purchaseOrder.getItems())
            {
                item.setShopCode(purchaseOrder.getShopCode());
                item.setPurchaseCode(purchaseOrder.getPurchaseCode());
                PurchaseOrderItem originalItem = item.getId() == null ? null : originalItemMap.get(item.getId());
                item.setDetailCode(originalItem != null && originalItem.getDetailCode() != null
                        && !originalItem.getDetailCode().trim().isEmpty()
                                ? originalItem.getDetailCode()
                                : documentCodeGenerator.nextPurchaseOrderDetailCode());
                item.setLineNo(lineNo++);
                item.setReceivedQty(BigDecimal.ZERO);
                item.setSerialNumbersText(SerialNumberRemarkCodec.encode(item.getSerialNumbers()));
            }
            totalAmount = applyLineAmounts(purchaseOrder.getItems());
            purchaseOrderItemMapper.batchInsertPurchaseOrderItem(purchaseOrder.getItems());
        }
        // 同 insertPurchaseOrder：总金额一律由明细算出。原先这一行在 if 里面，
        // 把明细全删光再保存时，旧的总金额会原封不动留在单子上——0 行明细却挂着几万块。
        purchaseOrder.setTotalAmount(totalAmount);
        // 商品内容摘要跟总金额一样，一律由明细算出：明细删光了摘要也要跟着清空，
        // 否则列表上会挂着一句「可乐等3种」而单子里一行都没有。
        purchaseOrder.setGoodsSummary(GoodsSummaryBuilder.build(purchaseOrder.getItems(),
                PurchaseOrderItem::getGoodsCode, PurchaseOrderItem::getGoodsName));

        if (purchaseOrderMapper.updatePurchaseOrder(purchaseOrder) != 1)
        {
            // SQL 里卡了 status = 'draft'：读到草稿之后、写回之前被人提交了会走到这里
            throw new ServiceException("采购订单状态已变化，请刷新后重试");
        }
        return 1;
    }

    /**
     * 改单守卫：当前用户看不到采购金额时，一律沿用库里的原值，不认前端回传的 {@code null}。
     *
     * <p>没有这道守卫，一个没有 {@code price:cost} 的人改一下数量就会：预估运费被
     * {@code updatePurchaseOrder} 里的 {@code coalesce(#{estimatedFreight}, 0)} 清零，
     * 而明细单价会先一步在 {@code validateQtyAndPrice} 撞上「采购单价不能为空」——
     * 连数量都改不了。见 {@code docs/金额按岗位可见.md} 第 6.1 节。</p>
     *
     * <p>新增的明细行（库里没有原值）不兜底：看不到价格的人本来也填不出价格，
     * 让它照常在 {@code validateQtyAndPrice} 报「采购单价不能为空」，比静默存一个 0 强。</p>
     *
     * <p>只在有登录上下文且确实无权时才动手（{@code isCostMaskedOnWrite}）——
     * 内部调用和单元测试没有要防的人，照收传入的值。</p>
     */
    private void keepMaskedCostPrices(PurchaseOrder order, PurchaseOrder existing,
            Map<Long, PurchaseOrderItem> originalItemMap)
    {
        if (!PriceMaskUtils.isCostMaskedOnWrite())
        {
            return;
        }
        order.setEstimatedFreight(existing.getEstimatedFreight());
        if (order.getItems() == null)
        {
            return;
        }
        for (PurchaseOrderItem item : order.getItems())
        {
            PurchaseOrderItem original = item == null || item.getId() == null
                    ? null : originalItemMap.get(item.getId());
            if (original != null)
            {
                item.setUnitPrice(original.getUnitPrice());
            }
        }
    }

    /**
     * 归一化预估运费。
     *
     * <p>说明书 3.4 流程2 把 estimatedfreight 定义在「到付」分支下：包邮不产生运费，
     * 寄付由供应商先垫付、金额到财务付款时才知道，只有到付才需要采购员下单时预估。
     * 因此非到付一律归零，避免出现「包邮却挂着预估运费」这类自相矛盾的数据。</p>
     */
    private void normalizeEstimatedFreight(PurchaseOrder order)
    {
        BigDecimal estimated = order.getEstimatedFreight();
        if (!"collect".equals(order.getFreightType()))
        {
            order.setEstimatedFreight(BigDecimal.ZERO);
            return;
        }
        if (estimated == null)
        {
            order.setEstimatedFreight(BigDecimal.ZERO);
            return;
        }
        if (estimated.signum() < 0)
        {
            throw new ServiceException("预估运费不能为负数");
        }
        order.setEstimatedFreight(estimated.setScale(2, RoundingMode.HALF_UP));
    }

    private void validatePurchaseItems(String shopCode, List<PurchaseOrderItem> items)
    {
        Set<String> goodsCodes = new HashSet<>();
        for (PurchaseOrderItem item : items)
        {
            String goodsCode = item == null || item.getGoodsCode() == null
                    ? "" : item.getGoodsCode().trim();
            if (goodsCode.isEmpty())
            {
                throw new ServiceException("采购商品编码不能为空");
            }
            if (!goodsCodes.add(goodsCode))
            {
                throw new ServiceException("同一采购订单中商品'" + goodsCode + "'不能重复添加");
            }
            item.setGoodsCode(goodsCode);

            InventoryGoods goods = inventoryGoodsMapper.selectGoodsByCode(shopCode, goodsCode);
            if (goods == null)
            {
                throw new ServiceException("采购商品'" + goodsCode + "'不存在");
            }
            validateQtyAndPrice(goods, item);
            validateTrackingData(goods, item);
        }
    }

    /**
     * 采购数量与单价的合法性。销售订单的 prepareItems 一直有这两条，采购这边原先是漏的。
     *
     * <p>三种坏数据各有各的下游后果，都不是在这一层报错，所以必须在这里就拦住：</p>
     * <ul>
     *   <li><b>数量 ≤ 0</b>：这一行永远到不了货——采购发货登记要求本次数量大于 0，
     *       而累计到货又不能超过采购数量，两条一夹就再也发不出去，单子卡死在「已审核」；</li>
     *   <li><b>单价为空</b>：{@code applyLineAmounts} 把它当 0 算，这一行的金额悄悄从订单
     *       总额里漏掉；等到登记发货时 {@code PurchaseReceiveServiceImpl.validateItems} 里的
     *       {@code source.getUnitPrice().multiply(...)} 会直接 NPE，接口 500，
     *       而那时候单子已经审过、货也发了；</li>
     *   <li><b>单价为负</b>：订单总额算成负数，而财务的 {@code requireWithinPayable} 对
     *       总额 ≤ 0 的单是<b>直接放行</b>的（本意是「明细还没填，无从判断」），
     *       于是这张单可以付任意金额，「付款不能超过待付货款」那道校验等于没有。</li>
     * </ul>
     *
     * <p>商品名取商品档案而不是明细行上的快照：后者是前端传的，报错文案不该跟着它走。</p>
     */
    private void validateQtyAndPrice(InventoryGoods goods, PurchaseOrderItem item)
    {
        if (item.getQty() == null || item.getQty().signum() <= 0)
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的采购数量必须大于0");
        }
        if (item.getUnitPrice() == null)
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的采购单价不能为空");
        }
        if (item.getUnitPrice().signum() < 0)
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的采购单价不能为负数");
        }
    }

    /**
     * 计算明细的不含税金额与税额，返回价税合计。
     *
     * <pre>
     * amount    = qty × price
     * taxAmount = amount × taxRate / 100
     * 总金额     = Σ(amount + taxAmount)
     * </pre>
     */
    private BigDecimal applyLineAmounts(List<PurchaseOrderItem> items)
    {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items)
        {
            BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal taxRate = item.getTaxRate() == null ? BigDecimal.ZERO : item.getTaxRate();
            if (taxRate.signum() < 0 || taxRate.compareTo(HUNDRED) > 0)
            {
                throw new ServiceException("商品'" + item.getGoodsName() + "'的税率必须在0到100之间");
            }
            BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = amount.multiply(taxRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            item.setTaxRate(taxRate);
            item.setTaxAmount(taxAmount);
            total = total.add(amount).add(taxAmount);
        }
        return total;
    }

    /**
     * 采购下单阶段不再采集批次号和序列号。
     *
     * <p>说明书 3.3.2 的采购明细不含这两个字段：下单时货未发出，批次尚未产生，
     * 序列号更无从得知。批次由入库时生成，序列号在到货质检环节采集。</p>
     *
     * <p>本方法只校验商品的追踪策略本身是否合法，并清空请求体中可能携带的
     * 批次号与序列号，避免客户端绕过界面写入无事实依据的数据。存量订单已存的
     * 值不受影响——修改草稿会整单重写明细，其余状态不允许修改。</p>
     */
    private void validateTrackingData(InventoryGoods goods, PurchaseOrderItem item)
    {
        String batchStrategy = goods.getBatchStrategy() == null || goods.getBatchStrategy().trim().isEmpty()
                ? "none" : goods.getBatchStrategy().trim().toLowerCase();
        if (!"none".equals(batchStrategy) && !"batch".equals(batchStrategy) && !"serial".equals(batchStrategy))
        {
            throw new ServiceException("商品'" + goods.getGoodsName() + "'的批次策略无效：" + batchStrategy);
        }

        item.setBatchCode(null);
        item.setSerialNumbers(new ArrayList<>());
    }

    /**
     * 批量删除采购订单
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deletePurchaseOrderByIds(String shopCode, Long[] ids)
    {
        for (Long id : ids)
        {
            PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderById(shopCode, id);
            if (order == null)
            {
                throw new ServiceException("采购订单不存在");
            }
            // 已驳回的订单同样放行：它不能改也不能重新提交，若连删除都不允许就成了死单，
            // 只能永久堆在列表里。驳回单必然没通过审核，不会有到货、退货等下游单据引用。
            if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus()))
            {
                throw new ServiceException("只能删除草稿或已驳回状态的订单");
            }

            // 删除明细
            purchaseOrderItemMapper.deleteItemsByPurchaseCode(order.getShopCode(), order.getPurchaseCode());
        }

        return purchaseOrderMapper.deletePurchaseOrderByIds(shopCode, ids);
    }

    /**
     * 提交采购订单审核
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param updateUser 更新人
     * @return 结果
     */
    @Override
    public int submitPurchaseOrder(String shopCode, String purchaseCode, String updateUser)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(shopCode, purchaseCode);
        if (order == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if (!"draft".equals(order.getStatus()))
        {
            throw new ServiceException("只有草稿状态的订单才能提交审核");
        }

        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByPurchaseCode(shopCode, purchaseCode);
        decodeSerialNumbers(items);
        // 空单挡在这里：没有明细的采购单总额是 0，而财务对总额 ≤ 0 的单是放行付款的，
        // 让它走到审核台上等于开了一张可以付任意金额的空白单
        if (items.isEmpty())
        {
            throw new ServiceException("采购订单没有明细，不能提交审核");
        }
        validatePurchaseItems(shopCode, items);

        // 驳回原因传库里的原值：重新提交不清空上一轮的驳回说明，
        // 正好对照这轮改了什么，与销售订单 withdrawOrder 的口径一致
        return requireTransition(purchaseOrderMapper.updatePurchaseOrderStatus(shopCode, purchaseCode,
                "submitted", Collections.singletonList("draft"),
                order.getRejectReason(), null, null, updateUser));
    }

    /** 状态流转语句带了期望状态，影响行数为 0 说明有人抢先改了状态 */
    private int requireTransition(int affected)
    {
        if (affected != 1)
        {
            throw new ServiceException("采购订单状态已变化，请刷新后重试");
        }
        return affected;
    }

    /**
     * 审核采购订单
     *
     * @param shopCode 商家编码
     * @param purchaseCode 采购单号
     * @param approved true-审核通过，false-驳回
     * @param rejectReason 驳回原因，驳回时必填，通过时忽略
     * @param updateUser 更新人
     * @return 结果
     */
    @Override
    public int approvePurchaseOrder(String shopCode, String purchaseCode, boolean approved,
            String rejectReason, String updateUser)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(shopCode, purchaseCode);
        if (order == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if (!"submitted".equals(order.getStatus()))
        {
            throw new ServiceException("只有待审核状态的订单才能审核");
        }

        // 驳回必须说明原因：采购员只有知道为什么被驳回才能改对，事后追溯也要有依据。
        String reason = trimToNull(rejectReason);
        if (!approved && reason == null)
        {
            throw new ServiceException("驳回采购订单必须填写驳回原因");
        }
        if (reason != null && reason.length() > 500)
        {
            throw new ServiceException("驳回原因不能超过500个字符");
        }

        // 审核人与审核时间用于审计追溯，通过与驳回都记录，且不被后续修改覆盖
        // （改单那条 SQL 已经不含这两列，覆盖不了）。
        // 通过时把驳回原因写成 null，清掉上一轮已失效的驳回说明。
        return requireTransition(purchaseOrderMapper.updatePurchaseOrderStatus(shopCode, purchaseCode,
                approved ? "approved" : "rejected", Collections.singletonList("submitted"),
                approved ? null : reason, updateUser, new Date(), updateUser));
    }

    /**
     * 录入物流信息。
     *
     * <p>说明书流程1：采购订单审核后，供应商发货，采购员录入物流单号，
     * 库管员据此创建到货单。因此物流单号属于采购订单主表，不是到货单独有。</p>
     */
    @Override
    public int updateLogistics(String shopCode, String purchaseCode, String logisticsNo,
            String carrier, String updateUser)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(shopCode, purchaseCode);
        if (order == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if (!"approved".equals(order.getStatus()) && !"partial_received".equals(order.getStatus()))
        {
            throw new ServiceException("只有已审核或部分到货的订单可以录入物流信息");
        }
        int rows = purchaseOrderMapper.updateLogistics(shopCode, purchaseCode,
                trimToNull(logisticsNo), trimToNull(carrier), updateUser);
        if (rows != 1)
        {
            throw new ServiceException("采购订单状态已变化，请刷新后重试");
        }
        return rows;
    }

    private String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 取消采购订单
     */
    @Override
    public int cancelPurchaseOrder(String shopCode, String purchaseCode, String updateUser)
    {
        PurchaseOrder order = purchaseOrderMapper.selectPurchaseOrderByCode(shopCode, purchaseCode);
        if (order == null)
        {
            throw new ServiceException("采购订单不存在");
        }
        if ("approved".equals(order.getStatus()) || "partial_received".equals(order.getStatus())
                || "completed".equals(order.getStatus()))
        {
            throw new ServiceException("已审核的订单不能取消");
        }
        if ("cancelled".equals(order.getStatus()))
        {
            throw new ServiceException("订单已经是取消状态");
        }

        // 上面两个判断只是为了给出说得清的提示；真正的并发守卫是 SQL 里的期望状态白名单。
        // 白名单与上面的排除项是同一个口径：已审核之后的三个状态不能取消，已取消的不重复取消。
        // 驳回原因传原值，取消不该动它。
        return requireTransition(purchaseOrderMapper.updatePurchaseOrderStatus(shopCode, purchaseCode,
                "cancelled", Arrays.asList("draft", "submitted", "rejected"),
                order.getRejectReason(), null, null, updateUser));
    }
}
