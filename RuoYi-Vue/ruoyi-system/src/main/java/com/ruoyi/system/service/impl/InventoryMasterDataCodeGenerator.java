package com.ruoyi.system.service.impl;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;

/**
 * 库存基础资料编码生成器。
 * 编码规则：中文名称大写首字母前缀 + Redis 秒级 Base36 大写编码。
 */
@Component
public class InventoryMasterDataCodeGenerator
{
    private static final String WAREHOUSE_PREFIX = "CK";
    private static final String SUPPLIER_PREFIX = "GYS";
    private static final String CUSTOMER_PREFIX = "KH";
    private static final String DEPARTMENT_PREFIX = "BM";
    private static final String PARTNER_PREFIX = "DW";
    private static final String BATCH_PREFIX = "PC";

    @Autowired
    private RedisService redisService;

    public String nextWarehouseCode()
    {
        return nextCode(WAREHOUSE_PREFIX, "仓库");
    }

    public String nextPartnerCode(String partnerType)
    {
        String prefix;
        if ("supplier".equalsIgnoreCase(partnerType))
        {
            prefix = SUPPLIER_PREFIX;
        }
        else if ("customer".equalsIgnoreCase(partnerType))
        {
            prefix = CUSTOMER_PREFIX;
        }
        else if ("department".equalsIgnoreCase(partnerType))
        {
            prefix = DEPARTMENT_PREFIX;
        }
        else
        {
            prefix = PARTNER_PREFIX;
        }
        return nextCode(prefix, "往来单位");
    }

    public String nextBatchCode()
    {
        return nextCode(BATCH_PREFIX, "批次");
    }

    private String nextCode(String prefix, String businessName)
    {
        Map result = redisService.getSecondKey(KeyType.Base36Upper, prefix);
        if (!Boolean.TRUE.equals(result.get("result")))
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException(businessName + "编码生成失败"
                    + (error == null ? "" : "：" + error));
        }

        Object primaryKey = result.get("primaryKey");
        String code = primaryKey == null ? "" : primaryKey.toString();
        if (!code.matches("^" + prefix + "[0-9A-Z]+$"))
        {
            throw new ServiceException(businessName + "编码格式异常");
        }
        return code;
    }
}
