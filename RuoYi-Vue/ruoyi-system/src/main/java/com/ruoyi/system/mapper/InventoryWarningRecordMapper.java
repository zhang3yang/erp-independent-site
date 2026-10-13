package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryWarningCandidate;
import com.ruoyi.system.domain.InventoryWarningRecord;

public interface InventoryWarningRecordMapper
{
    public List<InventoryWarningRecord> selectRecordList(InventoryWarningRecord record);

    public InventoryWarningRecord selectRecordById(@Param("shopCode") String shopCode,
            @Param("recordId") Long recordId);

    public List<InventoryWarningCandidate> selectWarningCandidates(@Param("shopCode") String shopCode);

    public List<String> selectEnabledShopCodes();

    public int insertRecord(InventoryWarningRecord record);

    public int updateActiveRecord(@Param("activeKey") String activeKey,
            @Param("availableQty") java.math.BigDecimal availableQty,
            @Param("thresholdQty") java.math.BigDecimal thresholdQty);

    public int recoverNormalRecords(@Param("shopCode") String shopCode);

    public int acknowledgeRecord(@Param("shopCode") String shopCode,
            @Param("recordId") Long recordId, @Param("operator") String operator,
            @Param("remark") String remark);

    public int closeRecord(@Param("shopCode") String shopCode,
            @Param("recordId") Long recordId, @Param("operator") String operator,
            @Param("remark") String remark);

    public int countActiveRecords(@Param("shopCode") String shopCode);
}
