package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.mapper.DocTypeConfigMapper;

/**
 * 哪些单据类型在待审核阶段预占库存。
 *
 * <p>这个判定决定了过账时用「扣减并释放锁定」还是「仅扣减」。判错会让
 * lockedqty 减成负数，被 SQL 的非负条件挡下，抛出与实际原因无关的
 * 「库存数据已被其他人修改」。采购退货就踩过这个坑。</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryDocumentLockCoordinatorPendingQtyTest
{
    @Mock private DocTypeConfigMapper docTypeConfigMapper;

    @InjectMocks private InventoryDocumentLockCoordinator coordinator;

    /**
     * 单据类型在配置表里存在。
     *
     * <p>不放进 {@code @BeforeEach}：null 单据类型的用例会提前返回、根本不查表，
     * 统一打桩会被 Mockito 严格模式判为多余桩。</p>
     */
    private void givenDocTypeConfigured()
    {
        when(docTypeConfigMapper.selectDocTypeByCode(anyString())).thenReturn(new DocTypeConfig());
    }

    @Test
    void 销售出库与仓库调拨预占库存()
    {
        givenDocTypeConfigured();
        assertTrue(coordinator.locksPendingQty(InventoryDocumentTypes.SALES_OUTBOUND));
        assertTrue(coordinator.locksPendingQty(InventoryDocumentTypes.WAREHOUSE_TRANSFER));
    }

    @Test
    void 采购退货与生产出库不预占库存()
    {
        givenDocTypeConfigured();
        // 一律引常量：这几条原先写死成 RO / MO / OO，单据类型改成拼音编码后
        // 断言的都是根本不存在的类型，恒返回 false，等于没在测东西。
        // 误判为预占会让 lockedqty 减成负数，过账直接更新 0 行。
        assertFalse(coordinator.locksPendingQty(InventoryDocumentTypes.PURCHASE_RETURN_OUTBOUND));
        assertFalse(coordinator.locksPendingQty(InventoryDocumentTypes.PRODUCTION_PICK));
        assertFalse(coordinator.locksPendingQty(InventoryDocumentTypes.PRODUCTION_CONSUME));
    }

    @Test
    void 状态流转类单据不预占库存()
    {
        givenDocTypeConfigured();
        assertFalse(coordinator.locksPendingQty("LOAN"));
        assertFalse(coordinator.locksPendingQty("RETURN"));
        assertFalse(coordinator.locksPendingQty("REPAIR_OUT"));
        assertFalse(coordinator.locksPendingQty("REPAIR_IN"));
        assertFalse(coordinator.locksPendingQty("DAMAGE"));
    }

    @Test
    void 入库不预占库存()
    {
        givenDocTypeConfigured();
        assertFalse(coordinator.locksPendingQty("RK"));
    }

    @Test
    void 空单据类型按不预占处理()
    {
        assertFalse(coordinator.locksPendingQty(null));
    }
}
