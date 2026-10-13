package com.ruoyi.system.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/**
 * Resolves and validates the physical monthly table suffix used by inventory documents.
 */
@Component
public class InventoryDocumentTableRouter
{
    private static final DateTimeFormatter SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter VALIDATION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public String currentSuffix()
    {
        return LocalDate.now(BUSINESS_ZONE).format(SUFFIX_FORMAT);
    }

    public String forBusinessDate(Date businessDate)
    {
        Date value = businessDate == null ? new Date() : businessDate;
        return value.toInstant().atZone(BUSINESS_ZONE).toLocalDate().format(SUFFIX_FORMAT);
    }

    /**
     * Validates a caller-provided suffix. Empty values resolve to the current business month.
     */
    public String validate(String tableSuffix)
    {
        if (tableSuffix == null || tableSuffix.isEmpty())
        {
            return currentSuffix();
        }
        if (!tableSuffix.matches("^\\d{6}$"))
        {
            throw new ServiceException("月表后缀格式不正确，必须是yyyyMM格式");
        }
        try
        {
            LocalDate.parse(tableSuffix + "01", VALIDATION_FORMAT);
        }
        catch (Exception e)
        {
            throw new ServiceException("月表后缀不是有效的年月");
        }
        return tableSuffix;
    }
}
