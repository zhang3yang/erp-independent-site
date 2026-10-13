package com.ruoyi.framework.sms;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.sms.SmsVerificationService;
import com.ruoyi.common.utils.StringUtils;

/** SMS verification for employee creation. */
@Service
public class SmsVerificationServiceImpl implements SmsVerificationService
{
    private static final int CODE_EXPIRE_SECONDS = 300;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_FAILURES = 5;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SmsSender smsSender;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void sendEmployeeAddCode(String phonenumber, String platcode)
    {
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        String cooldownKey = CacheConstants.SMS_EMPLOYEE_ADD_COOLDOWN_KEY + phone;
        if (!redisCache.setCacheObjectIfAbsent(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS))
        {
            throw new ServiceException("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeKey = CacheConstants.SMS_EMPLOYEE_ADD_CODE_KEY + phone;
        redisCache.deleteObject(CacheConstants.SMS_EMPLOYEE_ADD_FAIL_KEY + phone);
        redisCache.setCacheObject(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        try
        {
            smsSender.sendLoginCode(phone, code, platcode);
        }
        catch (RuntimeException ex)
        {
            redisCache.deleteObject(codeKey);
            redisCache.deleteObject(cooldownKey);
            throw new ServiceException("验证码发送失败，请稍后再试");
        }
    }

    @Override
    public void verifyEmployeeAddCode(String phonenumber, String code)
    {
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        if (code == null || !Pattern.matches("^\\d{6}$", code))
        {
            throw new ServiceException("短信验证码格式不正确");
        }
        String codeKey = CacheConstants.SMS_EMPLOYEE_ADD_CODE_KEY + phone;
        String failKey = CacheConstants.SMS_EMPLOYEE_ADD_FAIL_KEY + phone;
        long result = redisCache.verifyAndConsumeSmsCode(
                codeKey, failKey, code, MAX_FAILURES, CODE_EXPIRE_SECONDS);
        if (result == 1L)
        {
            return;
        }
        if (result == -2L)
        {
            throw new ServiceException("验证码错误次数过多，请重新获取");
        }
        if (result == 0L)
        {
            throw new ServiceException("验证码已过期，请重新获取");
        }
        throw new ServiceException("手机号或验证码错误");
    }

    @Override
    public void sendTenantAdminAccountCode(String phonenumber, String platcode)
    {
        sendCode(phonenumber, platcode, CacheConstants.SMS_TENANT_ADMIN_ACCOUNT_CODE_KEY,
                CacheConstants.SMS_TENANT_ADMIN_ACCOUNT_COOLDOWN_KEY,
                CacheConstants.SMS_TENANT_ADMIN_ACCOUNT_FAIL_KEY);
    }

    @Override
    public void verifyTenantAdminAccountCode(String phonenumber, String code)
    {
        verifyCode(phonenumber, code, CacheConstants.SMS_TENANT_ADMIN_ACCOUNT_CODE_KEY,
                CacheConstants.SMS_TENANT_ADMIN_ACCOUNT_FAIL_KEY);
    }

    private void sendCode(String phonenumber, String platcode, String codePrefix, String cooldownPrefix,
            String failPrefix)
    {
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        String cooldownKey = cooldownPrefix + phone;
        if (!redisCache.setCacheObjectIfAbsent(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS))
        {
            throw new ServiceException("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeKey = codePrefix + phone;
        redisCache.deleteObject(failPrefix + phone);
        redisCache.setCacheObject(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        try
        {
            smsSender.sendLoginCode(phone, code, platcode);
        }
        catch (RuntimeException ex)
        {
            redisCache.deleteObject(codeKey);
            redisCache.deleteObject(cooldownKey);
            throw new ServiceException("验证码发送失败，请稍后再试");
        }
    }

    private void verifyCode(String phonenumber, String code, String codePrefix, String failPrefix)
    {
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        if (code == null || !Pattern.matches("^\\d{6}$", code))
        {
            throw new ServiceException("短信验证码格式不正确");
        }
        long result = redisCache.verifyAndConsumeSmsCode(codePrefix + phone, failPrefix + phone,
                code, MAX_FAILURES, CODE_EXPIRE_SECONDS);
        if (result == 1L)
        {
            return;
        }
        if (result == -2L)
        {
            throw new ServiceException("验证码错误次数过多，请重新获取");
        }
        if (result == 0L)
        {
            throw new ServiceException("验证码已过期，请重新获取");
        }
        throw new ServiceException("手机号或验证码错误");
    }

    private String normalizePhone(String phone)
    {
        return StringUtils.trim(phone);
    }

    private void validatePhone(String phone)
    {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches())
        {
            throw new ServiceException("Mobile number must be an 11-digit mainland China mobile number");
        }
    }
}
