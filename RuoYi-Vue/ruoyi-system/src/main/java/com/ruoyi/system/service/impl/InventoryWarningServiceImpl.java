package com.ruoyi.system.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.domain.InventoryWarningCandidate;
import com.ruoyi.system.domain.InventoryWarningRecord;
import com.ruoyi.system.domain.InventoryWarningRule;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.mapper.InventoryWarningRecordMapper;
import com.ruoyi.system.mapper.InventoryWarningRuleMapper;
import com.ruoyi.system.service.IInventoryWarningService;

@Service
public class InventoryWarningServiceImpl implements IInventoryWarningService
{
    private static final Logger log = LoggerFactory.getLogger(InventoryWarningServiceImpl.class);
    private static final String WARNING_TYPE = "LOW_STOCK";

    @Autowired
    private InventoryWarningRuleMapper ruleMapper;

    @Autowired
    private InventoryWarningRecordMapper recordMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    private final TransactionTemplate transactionTemplate;

    public InventoryWarningServiceImpl(PlatformTransactionManager transactionManager)
    {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<InventoryWarningRule> selectRuleList(InventoryWarningRule rule)
    {
        return ruleMapper.selectRuleList(rule);
    }

    @Override
    public InventoryWarningRule selectRuleById(String shopCode, Long ruleId)
    {
        return ruleMapper.selectRuleById(shopCode, ruleId);
    }

    @Override
    public int insertRule(InventoryWarningRule rule)
    {
        normalizeAndValidateRule(rule, null);
        if (rule.getEnabled() == null)
        {
            rule.setEnabled(1);
        }
        return ruleMapper.insertRule(rule);
    }

    @Override
    public int updateRule(InventoryWarningRule rule)
    {
        if (rule.getRuleId() == null || ruleMapper.selectRuleById(rule.getShopCode(), rule.getRuleId()) == null)
        {
            throw new ServiceException("预警规则不存在");
        }
        normalizeAndValidateRule(rule, rule.getRuleId());
        if (rule.getEnabled() == null)
        {
            rule.setEnabled(1);
        }
        return ruleMapper.updateRule(rule);
    }

    private void normalizeAndValidateRule(InventoryWarningRule rule, Long excludeRuleId)
    {
        rule.setGoodsCode(StringUtils.trim(rule.getGoodsCode()));
        rule.setWareCode(StringUtils.trim(rule.getWareCode()));
        if (rule.getThresholdQty() == null || rule.getThresholdQty().signum() < 0)
        {
            throw new ServiceException("预警阈值不能小于0");
        }
        if (rule.getEnabled() != null && rule.getEnabled() != 0 && rule.getEnabled() != 1)
        {
            throw new ServiceException("启用状态不正确");
        }
        InventoryGoods goods = goodsMapper.selectGoodsByCode(rule.getShopCode(), rule.getGoodsCode());
        if (goods == null || !Integer.valueOf(1).equals(goods.getActiveFlag()))
        {
            throw new ServiceException("商品不存在或已停用");
        }
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(rule.getShopCode(), rule.getWareCode());
        if (warehouse == null || !Integer.valueOf(1).equals(warehouse.getActiveFlag()))
        {
            throw new ServiceException("仓库不存在或已停用");
        }
        if (ruleMapper.countRuleByDimension(rule.getShopCode(), rule.getGoodsCode(), rule.getWareCode(), excludeRuleId) > 0)
        {
            throw new ServiceException("该商品和仓库已经配置过预警规则");
        }
    }

    @Override
    public int deleteRuleByIds(String shopCode, Long[] ruleIds)
    {
        if (ruleIds == null || ruleIds.length == 0)
        {
            return 0;
        }
        return ruleMapper.deleteRuleByIds(shopCode, ruleIds);
    }

    @Override
    public List<InventoryWarningRecord> selectRecordList(InventoryWarningRecord record)
    {
        return recordMapper.selectRecordList(record);
    }

    @Override
    public InventoryWarningRecord selectRecordById(String shopCode, Long recordId)
    {
        return recordMapper.selectRecordById(shopCode, recordId);
    }

    @Override
    public int acknowledgeRecord(String shopCode, Long recordId, String operator, String remark)
    {
        validateRemark(remark);
        int rows = recordMapper.acknowledgeRecord(shopCode, recordId, operator, StringUtils.trim(remark));
        if (rows == 0)
        {
            throw new ServiceException("预警不存在或当前状态不能确认");
        }
        return rows;
    }

    @Override
    public int closeRecord(String shopCode, Long recordId, String operator, String remark)
    {
        validateRemark(remark);
        int rows = recordMapper.closeRecord(shopCode, recordId, operator, StringUtils.trim(remark));
        if (rows == 0)
        {
            throw new ServiceException("预警不存在或当前状态不能关闭");
        }
        return rows;
    }

    private void validateRemark(String remark)
    {
        if (remark != null && remark.length() > 500)
        {
            throw new ServiceException("处理备注不能超过500个字符");
        }
    }

    @Override
    public int countActiveRecords(String shopCode)
    {
        return recordMapper.countActiveRecords(shopCode);
    }

    @Override
    public Map<String, Integer> checkShopWarnings(String shopCode)
    {
        if (StringUtils.isEmpty(shopCode))
        {
            throw new ServiceException("商家编码不能为空");
        }
        return transactionTemplate.execute(status -> doCheckShopWarnings(shopCode));
    }

    private Map<String, Integer> doCheckShopWarnings(String shopCode)
    {
        int created = 0;
        int updated = 0;
        List<InventoryWarningCandidate> candidates = recordMapper.selectWarningCandidates(shopCode);
        for (InventoryWarningCandidate candidate : candidates)
        {
            if (candidate.getAvailableQty().compareTo(candidate.getThresholdQty()) > 0)
            {
                continue;
            }
            String activeKey = buildActiveKey(candidate);
            int rows = recordMapper.updateActiveRecord(activeKey, candidate.getAvailableQty(), candidate.getThresholdQty());
            if (rows > 0)
            {
                updated += rows;
                continue;
            }
            InventoryWarningRecord record = toRecord(candidate, activeKey);
            int inserted = recordMapper.insertRecord(record);
            if (inserted > 0)
            {
                created += inserted;
            }
            else
            {
                updated += recordMapper.updateActiveRecord(activeKey, candidate.getAvailableQty(), candidate.getThresholdQty());
            }
        }
        int recovered = recordMapper.recoverNormalRecords(shopCode);
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("recovered", recovered);
        result.put("active", recordMapper.countActiveRecords(shopCode));
        return result;
    }

    private String buildActiveKey(InventoryWarningCandidate candidate)
    {
        return candidate.getShopCode() + ":" + candidate.getGoodsCode() + ":"
                + candidate.getWareCode() + ":" + WARNING_TYPE;
    }

    private InventoryWarningRecord toRecord(InventoryWarningCandidate candidate, String activeKey)
    {
        InventoryWarningRecord record = new InventoryWarningRecord();
        record.setShopCode(candidate.getShopCode());
        record.setGoodsCode(candidate.getGoodsCode());
        record.setGoodsName(candidate.getGoodsName());
        record.setSpec(candidate.getSpec());
        record.setUnit(candidate.getUnit());
        record.setWareCode(candidate.getWareCode());
        record.setWareName(candidate.getWareName());
        record.setAvailableQty(candidate.getAvailableQty());
        record.setThresholdQty(candidate.getThresholdQty());
        record.setActiveKey(activeKey);
        return record;
    }

    @Override
    public Map<String, Integer> checkAllWarnings()
    {
        Map<String, Integer> total = new LinkedHashMap<>();
        total.put("shops", 0);
        total.put("failedShops", 0);
        total.put("created", 0);
        total.put("updated", 0);
        total.put("recovered", 0);
        for (String shopCode : recordMapper.selectEnabledShopCodes())
        {
            try
            {
                Map<String, Integer> shopResult = checkShopWarnings(shopCode);
                total.compute("shops", (key, value) -> value + 1);
                total.compute("created", (key, value) -> value + shopResult.get("created"));
                total.compute("updated", (key, value) -> value + shopResult.get("updated"));
                total.compute("recovered", (key, value) -> value + shopResult.get("recovered"));
            }
            catch (Exception e)
            {
                total.compute("failedShops", (key, value) -> value + 1);
                log.error("库存低库存预警检查失败，shopCode={}", shopCode, e);
            }
        }
        return total;
    }
}
