package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.mapper.DocumentMainMapper;

/** 单据创建幂等协调器，隔离 HTTP 请求头、Redis 状态和数据库兜底查询。 */
@Component
public class InventoryDocumentIdempotencyService
{
    private static final String REDIS_KEY_PREFIX = "inventory:document:idempotency:";

    @Autowired
    private DocumentMainMapper documentMainMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private InventoryDocumentTableRouter tableRouter;

    public String resolveKey(DocumentMain document)
    {
        String key = document == null ? null : document.getIdempotencyKey();
        if (isBlank(key))
        {
            try
            {
                key = ServletUtils.getRequest().getHeader("Idempotency-Key");
            }
            catch (Exception ignored)
            {
                // 非 HTTP 内部调用没有请求头，按普通创建处理。
            }
        }
        if (isBlank(key))
        {
            return null;
        }
        key = key.trim();
        if (key.length() > 100 || !key.matches("^[A-Za-z0-9._:-]{8,100}$"))
        {
            throw new ServiceException("Idempotency-Key 只能包含字母、数字及 . _ : -，长度为8-100个字符");
        }
        return key;
    }

    /** 返回已创建单据编码；没有可重放结果时返回 null。 */
    public String replay(DocumentMain document)
    {
        String redisKey = redisKey(document.getShopCode(), document.getIdempotencyKey());
        Map valueResult = redisService.getValue(redisKey);
        String value = valueResult == null ? null : String.valueOf(valueResult.get("value"));
        if (value == null || "null".equals(value))
        {
            return null;
        }
        if (!value.startsWith("DONE|"))
        {
            String suffix = suffixFor(document);
            DocumentMain existing = documentMainMapper.selectDocumentByIdempotencyKey(document.getShopCode(),
                    document.getIdempotencyKey(), suffix);
            if (existing != null)
            {
                redisService.setValueCover(redisKey, "DONE|" + suffix + "|" + existing.getDocCode());
                document.setDocCode(existing.getDocCode());
                document.setTableSuffix(suffix);
                return existing.getDocCode();
            }
            throw processingRequestException();
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length == 3)
        {
            DocumentMain existing = documentMainMapper.selectDocumentByCode(document.getShopCode(),
                    parts[2], tableRouter.validate(parts[1]));
            if (existing != null)
            {
                document.setDocCode(existing.getDocCode());
                document.setTableSuffix(parts[1]);
                return existing.getDocCode();
            }
        }
        redisService.delValue(redisKey);
        String suffix = suffixFor(document);
        DocumentMain existing = documentMainMapper.selectDocumentByIdempotencyKey(document.getShopCode(),
                document.getIdempotencyKey(), suffix);
        if (existing != null)
        {
            document.setDocCode(existing.getDocCode());
            document.setTableSuffix(suffix);
            return existing.getDocCode();
        }
        return null;
    }

    public void claim(DocumentMain document)
    {
        String redisKey = redisKey(document.getShopCode(), document.getIdempotencyKey());
        Map result = redisService.setValue(redisKey, "PROCESSING");
        if (!"success".equalsIgnoreCase(String.valueOf(result.get("result"))))
        {
            throw processingRequestException();
        }
    }

    public void registerCompletion(DocumentMain document)
    {
        final String redisKey = redisKey(document.getShopCode(), document.getIdempotencyKey());
        final String doneValue = "DONE|" + document.getTableSuffix() + "|" + document.getDocCode();
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            redisService.setValueCover(redisKey, doneValue);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                redisService.setValueCover(redisKey, doneValue);
            }

            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    redisService.delValue(redisKey);
                }
            }
        });
    }

    public void registerRollbackCleanup(DocumentMain document)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        final String redisKey = redisKey(document.getShopCode(), document.getIdempotencyKey());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    redisService.delValue(redisKey);
                }
            }
        });
    }

    public void cleanupAfterFailure(DocumentMain document)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            redisService.delValue(redisKey(document.getShopCode(), document.getIdempotencyKey()));
        }
    }

    private String suffixFor(DocumentMain document)
    {
        Date businessDate = document.getDocDate() == null ? new Date() : document.getDocDate();
        return tableRouter.forBusinessDate(businessDate);
    }

    private String redisKey(String shopCode, String key)
    {
        return REDIS_KEY_PREFIX + shopCode + ":" + key;
    }

    private ServiceException processingRequestException()
    {
        return new ServiceException("相同 Idempotency-Key 的单据请求正在处理中，请勿重复提交");
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
