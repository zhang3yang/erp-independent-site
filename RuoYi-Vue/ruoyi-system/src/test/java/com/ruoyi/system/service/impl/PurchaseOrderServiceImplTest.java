package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.PurchaseOrderItemMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;

/**
 * 采购订单的审核驳回原因与预估运费归一化测试。
 *
 * <p>对应 2026-08-14-purchase-module-remaining-gaps.md 的 3.1 运费管理与第 4 节驳回原因。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String PURCHASE = "CG20260814001";
    private static final String USER = "tester";

    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Mock private InventoryGoodsMapper inventoryGoodsMapper;
    @Mock private InventoryDocumentCodeGenerator documentCodeGenerator;

    @InjectMocks private PurchaseOrderServiceImpl service;

    // ------------------------------------------------------------------
    // 审核驳回原因
    // ------------------------------------------------------------------

    @Test
    void approve_驳回未填原因时拒绝()
    {
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(submittedOrder());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.approvePurchaseOrder(SHOP, PURCHASE, false, "   ", USER));

        assertTrue(error.getMessage().contains("驳回原因"));
        verify(purchaseOrderMapper, never()).updatePurchaseOrder(any(PurchaseOrder.class));
    }

    /*
     * 审核走的是 updatePurchaseOrderStatus 而不是 updatePurchaseOrder：
     * 期望状态写在 SQL 的 where 里，影响行数为 0 就说明有人抢先改了状态。
     * 改单那条语句已经不含 status / confirmuser / confirmtime / rejectreason 几列——
     * 它的参数对象是前端提交的表单，把状态列放进去等于让「修改」权限自己批单子。
     */
    private void stubStatusTransition()
    {
        when(purchaseOrderMapper.updatePurchaseOrderStatus(eq(SHOP), eq(PURCHASE), anyString(),
                anyList(), any(), any(), any(), eq(USER))).thenReturn(1);
    }

    @Test
    void approve_驳回时记录原因与审核人()
    {
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(submittedOrder());
        stubStatusTransition();

        assertEquals(1, service.approvePurchaseOrder(SHOP, PURCHASE, false, "  单价高于合同价  ", USER));

        // 驳回：目标状态 rejected、只允许从 submitted 过来、原因去掉首尾空格、记审核人和时间
        verify(purchaseOrderMapper).updatePurchaseOrderStatus(eq(SHOP), eq(PURCHASE), eq("rejected"),
                argThat(expected -> expected.size() == 1 && expected.contains("submitted")),
                eq("单价高于合同价"), eq(USER), argThat(time -> time != null), eq(USER));
    }

    @Test
    void approve_通过时清空上一轮的驳回原因()
    {
        PurchaseOrder order = submittedOrder();
        order.setRejectReason("上一轮被驳回的原因");
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(order);
        stubStatusTransition();

        service.approvePurchaseOrder(SHOP, PURCHASE, true, null, USER);

        // 通过时驳回原因写 null：这一列是无条件写入的，传 null 才能清掉上一轮已失效的说明
        verify(purchaseOrderMapper).updatePurchaseOrderStatus(eq(SHOP), eq(PURCHASE), eq("approved"),
                anyList(), isNull(), eq(USER), argThat(time -> time != null), eq(USER));
    }

    /** 状态被人抢先改掉时，影响行数为 0，要报出来而不是当成功返回。 */
    @Test
    void approve_状态被并发改掉时拒绝()
    {
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(submittedOrder());
        when(purchaseOrderMapper.updatePurchaseOrderStatus(eq(SHOP), eq(PURCHASE), anyString(),
                anyList(), any(), any(), any(), eq(USER))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.approvePurchaseOrder(SHOP, PURCHASE, true, null, USER));

        assertTrue(error.getMessage().contains("状态已变化"), error.getMessage());
    }

    @Test
    void approve_驳回原因超长时拒绝()
    {
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(submittedOrder());

        String tooLong = new String(new char[501]).replace('\0', '甲');
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.approvePurchaseOrder(SHOP, PURCHASE, false, tooLong, USER));

        assertTrue(error.getMessage().contains("500"));
    }

    @Test
    void approve_非待审核状态拒绝()
    {
        PurchaseOrder order = submittedOrder();
        order.setStatus("approved");
        when(purchaseOrderMapper.selectPurchaseOrderByCode(SHOP, PURCHASE)).thenReturn(order);

        assertThrows(ServiceException.class,
                () -> service.approvePurchaseOrder(SHOP, PURCHASE, true, null, USER));
    }

    // ------------------------------------------------------------------
    // 预估运费归一化：说明书 3.4 流程2 只在「到付」下录入预估运费
    // ------------------------------------------------------------------

    @Test
    void insert_包邮时预估运费归零()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("free");
        order.setEstimatedFreight(new BigDecimal("50.00"));
        service.insertPurchaseOrder(order);

        verify(purchaseOrderMapper).insertPurchaseOrder(argThat(saved ->
                BigDecimal.ZERO.compareTo(saved.getEstimatedFreight()) == 0));
    }

    @Test
    void insert_寄付时预估运费同样归零()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("prepaid");
        order.setEstimatedFreight(new BigDecimal("50.00"));
        service.insertPurchaseOrder(order);

        verify(purchaseOrderMapper).insertPurchaseOrder(argThat(saved ->
                BigDecimal.ZERO.compareTo(saved.getEstimatedFreight()) == 0));
    }

    @Test
    void insert_到付时保留预估运费并规整到两位小数()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("collect");
        order.setEstimatedFreight(new BigDecimal("12.345"));
        service.insertPurchaseOrder(order);

        verify(purchaseOrderMapper).insertPurchaseOrder(argThat(saved ->
                new BigDecimal("12.35").compareTo(saved.getEstimatedFreight()) == 0));
    }

    @Test
    void insert_到付预估运费为负数时拒绝()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("collect");
        order.setEstimatedFreight(new BigDecimal("-1"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertPurchaseOrder(order));
        assertTrue(error.getMessage().contains("预估运费"));
    }

    @Test
    void insert_到付未填预估运费时按零落库()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("collect");
        order.setEstimatedFreight(null);
        service.insertPurchaseOrder(order);

        verify(purchaseOrderMapper).insertPurchaseOrder(argThat(saved ->
                BigDecimal.ZERO.compareTo(saved.getEstimatedFreight()) == 0));
    }

    @Test
    void insert_采购单号由服务端生成忽略客户端传入值()
    {
        stubCodeGenerator();

        PurchaseOrder order = draftOrder("free");
        order.setPurchaseCode("客户端伪造的单号");
        service.insertPurchaseOrder(order);

        verify(purchaseOrderMapper).insertPurchaseOrder(argThat(saved ->
                PURCHASE.equals(saved.getPurchaseCode()) && "draft".equals(saved.getStatus())));
    }

    // ------------------------------------------------------------------
    // 删除：草稿与已驳回都可删，其余状态不可
    // ------------------------------------------------------------------

    @Test
    void delete_已驳回订单可删除并连带删除明细()
    {
        PurchaseOrder order = submittedOrder();
        order.setStatus("rejected");
        order.setRejectReason("单价高于合同价");
        when(purchaseOrderMapper.selectPurchaseOrderById(SHOP, 1L)).thenReturn(order);
        when(purchaseOrderMapper.deletePurchaseOrderByIds(SHOP, new Long[] { 1L })).thenReturn(1);

        assertEquals(1, service.deletePurchaseOrderByIds(SHOP, new Long[] { 1L }));

        verify(purchaseOrderItemMapper).deleteItemsByPurchaseCode(SHOP, PURCHASE);
    }

    @Test
    void delete_草稿订单仍可删除()
    {
        PurchaseOrder order = submittedOrder();
        order.setStatus("draft");
        when(purchaseOrderMapper.selectPurchaseOrderById(SHOP, 1L)).thenReturn(order);
        when(purchaseOrderMapper.deletePurchaseOrderByIds(SHOP, new Long[] { 1L })).thenReturn(1);

        assertEquals(1, service.deletePurchaseOrderByIds(SHOP, new Long[] { 1L }));

        verify(purchaseOrderItemMapper).deleteItemsByPurchaseCode(SHOP, PURCHASE);
    }

    @Test
    void delete_已审核订单拒绝删除且不动明细()
    {
        PurchaseOrder order = submittedOrder();
        order.setStatus("approved");
        when(purchaseOrderMapper.selectPurchaseOrderById(SHOP, 1L)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.deletePurchaseOrderByIds(SHOP, new Long[] { 1L }));

        assertTrue(error.getMessage().contains("草稿或已驳回"));
        verify(purchaseOrderItemMapper, never()).deleteItemsByPurchaseCode(any(), any());
        verify(purchaseOrderMapper, never()).deletePurchaseOrderByIds(any(), any());
    }

    @Test
    void delete_待审核订单拒绝删除()
    {
        when(purchaseOrderMapper.selectPurchaseOrderById(SHOP, 1L)).thenReturn(submittedOrder());

        assertThrows(ServiceException.class,
                () -> service.deletePurchaseOrderByIds(SHOP, new Long[] { 1L }));

        verify(purchaseOrderMapper, never()).deletePurchaseOrderByIds(any(), any());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private void stubCodeGenerator()
    {
        when(documentCodeGenerator.nextPurchaseOrderCode()).thenReturn(PURCHASE);
    }

    /** 无明细的草稿：insertPurchaseOrder 会跳过商品校验，便于单独验证运费归一化 */
    private PurchaseOrder draftOrder(String freightType)
    {
        PurchaseOrder order = new PurchaseOrder();
        order.setShopCode(SHOP);
        order.setPartnerCode("P001");
        order.setPartnerName("测试供应商");
        order.setFreightType(freightType);
        order.setCreateUser(USER);
        return order;
    }

    private PurchaseOrder submittedOrder()
    {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setShopCode(SHOP);
        order.setPurchaseCode(PURCHASE);
        order.setStatus("submitted");
        return order;
    }
}
