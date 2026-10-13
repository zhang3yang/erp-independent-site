package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.PurchaseReceive;

public interface PurchaseReceiveMapper
{
    List<PurchaseReceive> selectList(@Param("query") PurchaseReceive query, @Param("tableSuffix") String tableSuffix);

    /** 可供入库的发货单（全部行都未被占用的待入库单），供库存模块的采购入库页下拉选择。 */
    List<PurchaseReceive> selectPendingList(@Param("query") PurchaseReceive query, @Param("tableSuffix") String tableSuffix);

    PurchaseReceive selectByCode(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix);

    PurchaseReceive selectByCodeForUpdate(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix);

    int insert(@Param("receive") PurchaseReceive receive, @Param("tableSuffix") String tableSuffix);

    int updateDraft(@Param("receive") PurchaseReceive receive, @Param("tableSuffix") String tableSuffix);

    /**
     * 单头状态由明细派生，不做 CAS——派生结果就是唯一正确答案，
     * 用期望状态卡反而会在并发入库时把正确的重算挡掉。
     * 明细行的并发安全由 {@code fillActualQty} 的 {@code actualqty is null} 保证。
     */
    int updateStatusUnconditional(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix, @Param("status") String status,
            @Param("username") String username);

    /** 入库员签收时回写签收信息，并把本次实付运费累加到发货单。 */
    int applyReceiveInfo(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix, @Param("receiveUser") String receiveUser,
            @Param("receiveTime") Date receiveTime, @Param("freightDelta") BigDecimal freightDelta,
            @Param("username") String username);

    int deleteDraft(@Param("shopCode") String shopCode, @Param("receiveCode") String receiveCode,
            @Param("tableSuffix") String tableSuffix);
}
