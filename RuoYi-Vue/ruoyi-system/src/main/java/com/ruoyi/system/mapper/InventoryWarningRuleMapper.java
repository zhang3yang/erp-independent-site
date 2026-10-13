package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryWarningRule;

public interface InventoryWarningRuleMapper
{
    public List<InventoryWarningRule> selectRuleList(InventoryWarningRule rule);

    public InventoryWarningRule selectRuleById(@Param("shopCode") String shopCode,
            @Param("ruleId") Long ruleId);

    public int countRuleByDimension(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("excludeRuleId") Long excludeRuleId);

    public int insertRule(InventoryWarningRule rule);

    public int updateRule(InventoryWarningRule rule);

    public int deleteRuleByIds(@Param("shopCode") String shopCode,
            @Param("ruleIds") Long[] ruleIds);
}
