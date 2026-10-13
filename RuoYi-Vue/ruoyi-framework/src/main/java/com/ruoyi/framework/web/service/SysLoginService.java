package com.ruoyi.framework.web.service;

import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.exception.user.BlackListException;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.CaptchaExpireException;
import com.ruoyi.common.exception.user.UserNotExistsException;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.common.enums.UserStatus;
import com.ruoyi.framework.sms.SmsSender;

/**
 * 登录校验方法
 * 
 * @author ruoyi
 */
@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private SmsSender smsSender;

    private final SecureRandom secureRandom = new SecureRandom();
    private static final int SMS_CODE_EXPIRE_SECONDS = 300;
    private static final int SMS_COOLDOWN_SECONDS = 60;
    private static final int SMS_MAX_VERIFY_FAILURES = 5;

    /**
     * 登录验证
     * 
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid)
    {
        // 验证码校验
        validateCaptcha(username, code, uuid);
        LoginUser loginUser = authenticate(username, password);
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 只校验账号密码，<b>不校验证码、不发 token</b>。
     *
     * <p>给「独立站专用登录页」用（{@code POST /station/login}）：那条路认证成功后不建 ERP 会话，
     * 而是直接签站点跳转令牌。前置校验（长度、IP 黑名单）、密码错 N 次锁定（在
     * {@code SysPasswordService} 里）、登录日志，都和 {@link #login} 一样走一遍。</p>
     *
     * @return 认证通过的登录用户（还没有 token）
     */
    public LoginUser authenticate(String username, String password)
    {
        // 登录前置校验
        loginPreCheck(username, password);
        // 用户验证
        Authentication authentication = null;
        try
        {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            AuthenticationContextHolder.setContext(authenticationToken);
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (Exception e)
        {
            if (e instanceof BadCredentialsException)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                throw new UserPasswordNotMatchException();
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                throw new ServiceException(e.getMessage());
            }
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        recordLoginInfo(loginUser.getUserCode());
        return loginUser;
    }

    public void sendSmsLoginCode(String phonenumber)
    {
        sendSmsLoginCode(phonenumber, null);
    }

    public void sendSmsLoginCode(String phonenumber, String platcode)
    {
        checkLoginIp(phonenumber);
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        SysUser user = userService.selectUserByPhonenumber(phone);
        if (user == null || !UserStatus.OK.getCode().equals(user.getStatus())
                || UserStatus.DELETED.getCode().equals(user.getDelFlag()))
        {
            throw new ServiceException("手机号或账号状态不正确");
        }
        String cooldownKey = CacheConstants.SMS_LOGIN_COOLDOWN_KEY + phone;
        if (!redisCache.setCacheObjectIfAbsent(cooldownKey, "1", SMS_COOLDOWN_SECONDS, TimeUnit.SECONDS))
        {
            throw new ServiceException("验证码发送过于频繁，请稍后再试");
        }
        String smsCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeKey = CacheConstants.SMS_LOGIN_CODE_KEY + phone;
        String failKey = CacheConstants.SMS_LOGIN_FAIL_KEY + phone;
        redisCache.deleteObject(failKey);
        redisCache.setCacheObject(codeKey, smsCode, SMS_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        try
        {
            smsSender.sendLoginCode(phone, smsCode, platcode);
        }
        catch (RuntimeException ex)
        {
            redisCache.deleteObject(codeKey);
            redisCache.deleteObject(cooldownKey);
            throw new ServiceException("验证码发送失败，请稍后再试");
        }
    }

    public String loginBySms(String phonenumber, String code)
    {
        checkLoginIp(phonenumber);
        String phone = normalizePhone(phonenumber);
        validatePhone(phone);
        if (code == null || !Pattern.matches("^\\d{6}$", code))
        {
            throw new ServiceException("验证码格式不正确");
        }
        String failKey = CacheConstants.SMS_LOGIN_FAIL_KEY + phone;
        Integer failures = redisCache.getCacheObject(failKey);
        if (failures != null && failures >= SMS_MAX_VERIFY_FAILURES)
        {
            throw new ServiceException("验证码错误次数过多，请稍后再试");
        }
        String codeKey = CacheConstants.SMS_LOGIN_CODE_KEY + phone;
        String expected = redisCache.getCacheObject(codeKey);
        if (expected == null)
        {
            throw new ServiceException("验证码已过期，请重新获取");
        }
        if (!java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                code.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        {
            int nextFailures = failures == null ? 1 : failures + 1;
            redisCache.setCacheObject(failKey, nextFailures, SMS_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            if (nextFailures >= SMS_MAX_VERIFY_FAILURES)
            {
                redisCache.deleteObject(codeKey);
            }
            throw new ServiceException("手机号或验证码错误");
        }
        redisCache.deleteObject(codeKey);
        redisCache.deleteObject(failKey);
        SysUser user = userService.selectUserByPhonenumber(phone);
        if (user == null || !UserStatus.OK.getCode().equals(user.getStatus())
                || UserStatus.DELETED.getCode().equals(user.getDelFlag()))
        {
            throw new ServiceException("手机号或账号状态不正确");
        }
        LoginUser loginUser = new LoginUser(user.getUserCode(), user.getDeptId(), user,
                permissionService.getMenuPermission(user));
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(phone, Constants.LOGIN_SUCCESS,
                MessageUtils.message("user.login.success")));
        recordLoginInfo(loginUser.getUserCode());
        return tokenService.createToken(loginUser);
    }

    private String normalizePhone(String phonenumber)
    {
        return phonenumber == null ? "" : phonenumber.trim();
    }

    private void validatePhone(String phonenumber)
    {
        if (!Pattern.matches("^1[3-9]\\d{9}$", phonenumber))
        {
            throw new ServiceException("请输入正确的11位手机号码");
        }
    }

    private void checkLoginIp(String loginName)
    {
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(loginName, Constants.LOGIN_FAIL,
                    MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
            String captcha = redisCache.getCacheObject(verifyKey);
            if (captcha == null)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
                throw new CaptchaExpireException();
            }
            redisCache.deleteObject(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
                throw new CaptchaException();
            }
        }
    }

    /**
     * 登录前置校验
     * @param username 用户名
     * @param password 用户密码
     */
    public void loginPreCheck(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        // 密码如果不在指定范围内 错误
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // IP黑名单校验
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param userCode 用户编码
     */
    public void recordLoginInfo(String userCode)
    {
        userService.updateLoginInfo(userCode, IpUtils.getIpAddr(), DateUtils.getNowDate());
    }
}
