package com.ruoyi.system.service.impl;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;

/** Generates globally unique role codes in the format rl + Base10 second key. */
@Component
public class RoleCodeGenerator
{
    private static final String ROLE_CODE_PREFIX = "rl";

    @Autowired
    private RedisService redisService;

    public String generate()
    {
        Map result = redisService.getSecondKey(KeyType.Base10, ROLE_CODE_PREFIX);
        if (!Boolean.TRUE.equals(result.get("result")) || result.get("primaryKey") == null)
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException("Role code generation failed" + (error == null ? "" : ": " + error));
        }
        String roleCode = String.valueOf(result.get("primaryKey"));
        if (!roleCode.matches("^rl[0-9]+$"))
        {
            throw new ServiceException("Generated role code has an invalid format");
        }
        return roleCode;
    }
}
