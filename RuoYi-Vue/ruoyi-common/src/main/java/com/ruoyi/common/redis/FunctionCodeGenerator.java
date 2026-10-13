package com.ruoyi.common.redis;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/** Generates function codes through the shared Redis second-key service. */
@Component
public class FunctionCodeGenerator
{
    private static final String FUNCTION_CODE_PREFIX = "fn";

    @Autowired
    private RedisService redisService;

    public String generate()
    {
        Map<?, ?> result = redisService.getSecondKey(KeyType.Base10, FUNCTION_CODE_PREFIX);
        if (!Boolean.TRUE.equals(result.get("result")) || result.get("primaryKey") == null)
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException("Function code generation failed" + (error == null ? "" : ": " + error));
        }
        String funCode = String.valueOf(result.get("primaryKey"));
        if (!funCode.matches("^fn[0-9]+$"))
        {
            throw new ServiceException("Generated function code has an invalid format");
        }
        return funCode;
    }
}
