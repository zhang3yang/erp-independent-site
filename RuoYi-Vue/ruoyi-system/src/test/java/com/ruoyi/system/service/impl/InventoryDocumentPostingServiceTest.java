package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;

class InventoryDocumentPostingServiceTest
{
    private final InventoryDocumentPostingService service = new InventoryDocumentPostingService();

    /** 良品·在库，可自由支配的正常库存 */
    private static final StockState AVAILABLE = StockState.available();

    /** 不良品·在库：登记损坏后还没送修的货，也包括厂内维修中的 */
    private static final StockState DEFECTIVE_ONHAND =
            StockState.of(QualityFlag.DEFECTIVE, OnhandFlag.IN);

    /** 良品·不在库：借出在外的好货，或领到车间的在制品 */
    private static final StockState GOOD_OFFHAND =
            StockState.of(QualityFlag.GOOD, OnhandFlag.OUT);

    /** 不良品·不在库：送外维修中的，或借出在外的坏货 */
    private static final StockState DEFECTIVE_OFFHAND =
            StockState.of(QualityFlag.DEFECTIVE, OnhandFlag.OUT);

    @Test
    void acceptsSupportedAssetStatusTransitionsWithinOneWarehouse()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.LOAN, AVAILABLE, GOOD_OFFHAND, "W1", "W1"), "N"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.RETURN, GOOD_OFFHAND, AVAILABLE, "W1", "W1"), "N"));
        // 送外维修：不良品离库
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.REPAIR_OUT, DEFECTIVE_ONHAND, DEFECTIVE_OFFHAND, "W1", "W1"), "N"));
        // 厂内维修：货没离开仓库，二维下状态不变，单据只作记录
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.REPAIR_OUT, DEFECTIVE_ONHAND, DEFECTIVE_ONHAND, "W1", "W1"), "N"));
        // 维修归还：修好了变良品，没修好仍是不良品
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.REPAIR_IN, DEFECTIVE_OFFHAND, AVAILABLE, "W1", "W1"), "N"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.REPAIR_IN, DEFECTIVE_OFFHAND, DEFECTIVE_ONHAND, "W1", "W1"), "N"));
    }

    /** 车间不在仓库里，所以领料就是离库、退料就是回库 */
    @Test
    void acceptsProductionPickAndReturn()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.PRODUCTION_PICK, AVAILABLE, GOOD_OFFHAND, "W1", "W1"), "N"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.PRODUCTION_RETURN, GOOD_OFFHAND, AVAILABLE, "W1", "W1"), "N"));
    }

    /** 领料只改在库状态，不能把良品领成不良品 */
    @Test
    void rejectsProductionPickThatChangesQuality()
    {
        DocumentMain document = document(InventoryDocumentTypes.PRODUCTION_PICK,
                AVAILABLE, DEFECTIVE_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    /** 不良品也能借出（比如坏机器借给供应商拆解），借出不改变货的好坏 */
    @Test
    void acceptsLoanAndReturnOfDefectiveGoods()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.LOAN, DEFECTIVE_ONHAND, DEFECTIVE_OFFHAND, "W1", "W1"), "N"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.RETURN, DEFECTIVE_OFFHAND, DEFECTIVE_ONHAND, "W1", "W1"), "N"));
    }

    /** 借出、归还只搬在库状态，顺手把不良品变良品是不允许的 */
    @Test
    void rejectsLoanThatChangesQuality()
    {
        DocumentMain document = document(InventoryDocumentTypes.LOAN, DEFECTIVE_ONHAND, GOOD_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void rejectsReturnThatChangesQuality()
    {
        DocumentMain document = document(InventoryDocumentTypes.RETURN, GOOD_OFFHAND, DEFECTIVE_ONHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    /** 借出的来源必须在库：已经不在库的货（借出在外、在车间）不能再借一次 */
    @Test
    void rejectsLoanFromOffhandStock()
    {
        DocumentMain document = document(InventoryDocumentTypes.LOAN, GOOD_OFFHAND, GOOD_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void acceptsDamageThatOnlyFlipsQuality()
    {
        // 在库的良品坏了
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.DAMAGE, AVAILABLE, DEFECTIVE_ONHAND, "W1", "W1"), "N"));
        // 不在库的货坏了：仍然不在库，只有良品状态变了
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.DAMAGE, GOOD_OFFHAND, DEFECTIVE_OFFHAND, "W1", "W1"), "N"));
    }

    @Test
    void rejectsDamageThatAlsoChangesOnhand()
    {
        // 不在库的货登记损坏时不能顺手把货「搬回」仓库，那样账实不符
        DocumentMain document = document(InventoryDocumentTypes.DAMAGE, GOOD_OFFHAND, DEFECTIVE_ONHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void rejectsRepairOutFromGoodQuality()
    {
        // 只有不良品能送修，良品要先登记损坏
        DocumentMain document = document(InventoryDocumentTypes.REPAIR_OUT, AVAILABLE, DEFECTIVE_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    /** 送修的来源必须在库：已经不在库的货（借出在外、已送修）不能再送修一次 */
    @Test
    void rejectsRepairOutFromOffhandStock()
    {
        DocumentMain document = document(InventoryDocumentTypes.REPAIR_OUT,
                DEFECTIVE_OFFHAND, DEFECTIVE_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    /** 修好与否由维修归还决定，送修单不能把不良品直接标成良品 */
    @Test
    void rejectsRepairOutThatFixesQuality()
    {
        DocumentMain document = document(InventoryDocumentTypes.REPAIR_OUT,
                DEFECTIVE_ONHAND, GOOD_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void rejectsInvalidAssetStatusTransition()
    {
        DocumentMain document = document(InventoryDocumentTypes.LOAN, AVAILABLE, DEFECTIVE_ONHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void rejectsAssetTransitionAcrossWarehouses()
    {
        DocumentMain document = document(InventoryDocumentTypes.RETURN, GOOD_OFFHAND, AVAILABLE, "W1", "W2");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    @Test
    void rejectsIncompleteState()
    {
        DocumentMain document = document(InventoryDocumentTypes.LOAN,
                StockState.of(QualityFlag.GOOD.getCode(), null), GOOD_OFFHAND, "W1", "W1");

        assertThrows(ServiceException.class, () -> service.validateDocument(document, "N"));
    }

    /** 出库只能从在库的货里扣，良品不良品都行 */
    @Test
    void acceptsOutboundFromOnhandStock()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.SALES_OUTBOUND, AVAILABLE, AVAILABLE, "W1", "W1"), "O"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.SALES_OUTBOUND, DEFECTIVE_ONHAND, DEFECTIVE_ONHAND, "W1", "W1"), "O"));
    }

    /** 借出在外、领到车间的货不能被一张出库单直接卖掉 */
    @Test
    void rejectsOutboundFromOffhandStock()
    {
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.SALES_OUTBOUND, GOOD_OFFHAND, GOOD_OFFHAND, "W1", "W1"), "O"));
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.PURCHASE_RETURN_OUTBOUND, DEFECTIVE_OFFHAND, DEFECTIVE_OFFHAND, "W1", "W1"), "O"));
    }

    /** 生产消耗是唯一从「不在库」出库的单据——它扣的就是车间里的在制品 */
    @Test
    void productionConsumeMustDrawFromWorkshop()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.PRODUCTION_CONSUME, GOOD_OFFHAND, GOOD_OFFHAND, "W1", "W1"), "O"));
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.PRODUCTION_CONSUME, AVAILABLE, AVAILABLE, "W1", "W1"), "O"));
    }

    /** 调拨只换仓库，两个维度都不许动 */
    @Test
    void acceptsTransferThatKeepsState()
    {
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.WAREHOUSE_TRANSFER, AVAILABLE, AVAILABLE, "W1", "W2"), "N"));
        assertDoesNotThrow(() -> service.validateDocument(
                document(InventoryDocumentTypes.WAREHOUSE_TRANSFER, DEFECTIVE_ONHAND, DEFECTIVE_ONHAND, "W1", "W2"), "N"));
    }

    /** 调拨不能当洗白工具：不良品调一次变良品、借出的货调一次变可用库存 */
    @Test
    void rejectsTransferThatLaundersState()
    {
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.WAREHOUSE_TRANSFER, DEFECTIVE_ONHAND, AVAILABLE, "W1", "W2"), "N"));
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.WAREHOUSE_TRANSFER, GOOD_OFFHAND, AVAILABLE, "W1", "W2"), "N"));
        // 状态没变但货本来就不在库，也调不了
        assertThrows(ServiceException.class, () -> service.validateDocument(
                document(InventoryDocumentTypes.WAREHOUSE_TRANSFER, GOOD_OFFHAND, GOOD_OFFHAND, "W1", "W2"), "N"));
    }

    private DocumentMain document(String type, StockState fromState, StockState toState,
            String fromWarehouse, String toWarehouse)
    {
        DocumentMain document = new DocumentMain();
        document.setDocType(type);
        document.setFromState(fromState);
        document.setToState(toState);
        document.setFromWareCode(fromWarehouse);
        document.setToWareCode(toWarehouse);
        return document;
    }
}
