package com.ruoyi.system.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;

public final class SerialNumberRemarkCodec
{
    private static final String PREFIX = "@SN:";
    private static final int MAX_REMARK_LENGTH = 500;

    private SerialNumberRemarkCodec()
    {
    }

    public static String encode(List<String> serialNumbers)
    {
        List<String> normalized = normalize(serialNumbers);
        if (normalized.isEmpty())
        {
            return "";
        }
        String encoded = PREFIX + String.join("\n", normalized);
        if (encoded.length() > MAX_REMARK_LENGTH)
        {
            throw new ServiceException("序列号内容过长，无法保存到入库明细");
        }
        return encoded;
    }

    public static List<String> decode(String remark)
    {
        if (remark == null || !remark.startsWith(PREFIX))
        {
            return new ArrayList<>();
        }
        String content = remark.substring(PREFIX.length());
        if (content.isEmpty())
        {
            return new ArrayList<>();
        }
        List<String> serialNumbers = new ArrayList<>();
        for (String value : content.split("\\r?\\n"))
        {
            if (value != null && !value.trim().isEmpty())
            {
                serialNumbers.add(value.trim());
            }
        }
        return serialNumbers;
    }

    public static List<String> normalize(List<String> serialNumbers)
    {
        Set<String> unique = new LinkedHashSet<>();
        if (serialNumbers != null)
        {
            for (String serialNumber : serialNumbers)
            {
                if (serialNumber != null && !serialNumber.trim().isEmpty())
                {
                    unique.add(serialNumber.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }
}
