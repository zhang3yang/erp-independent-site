package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.InventoryWarningRecord;
import com.ruoyi.system.domain.InventoryWarningRule;

public interface IInventoryWarningService
{
    public List<InventoryWarningRule> selectRuleList(InventoryWarningRule rule);

    public InventoryWarningRule selectRuleById(String shopCode, Long ruleId);

    public int insertRule(InventoryWarningRule rule);

    public int updateRule(InventoryWarningRule rule);

    public int deleteRuleByIds(String shopCode, Long[] ruleIds);

    public List<InventoryWarningRecord> selectRecordList(InventoryWarningRecord record);

    public InventoryWarningRecord selectRecordById(String shopCode, Long recordId);

    public int acknowledgeRecord(String shopCode, Long recordId, String operator, String remark);

    public int closeRecord(String shopCode, Long recordId, String operator, String remark);

    public int countActiveRecords(String shopCode);

    public Map<String, Integer> checkShopWarnings(String shopCode);

    public Map<String, Integer> checkAllWarnings();
}
