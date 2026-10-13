package com.ruoyi.common.constant;

/**
 * 缓存的key 常量
 * 
 * @author ruoyi
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /** 手机验证码登录缓存前缀 */
    public static final String SMS_LOGIN_CODE_KEY = "sms:login:code:";

    /** 手机验证码发送冷却缓存前缀 */
    public static final String SMS_LOGIN_COOLDOWN_KEY = "sms:login:cooldown:";

    /** 手机验证码失败次数缓存前缀 */
    public static final String SMS_LOGIN_FAIL_KEY = "sms:login:fail:";

    /** 新增员工手机号验证码缓存前缀 */
    public static final String SMS_EMPLOYEE_ADD_CODE_KEY = "sms:employee:add:code:";

    /** 新增员工手机号验证码发送冷却缓存前缀 */
    public static final String SMS_EMPLOYEE_ADD_COOLDOWN_KEY = "sms:employee:add:cooldown:";

    /** 新增员工手机号验证码失败次数缓存前缀 */
    public static final String SMS_EMPLOYEE_ADD_FAIL_KEY = "sms:employee:add:fail:";

    /** 商家管理员账号手机号验证码缓存前缀 */
    public static final String SMS_TENANT_ADMIN_ACCOUNT_CODE_KEY = "sms:tenant-admin:account:code:";

    /** 商家管理员账号手机号验证码发送冷却缓存前缀 */
    public static final String SMS_TENANT_ADMIN_ACCOUNT_COOLDOWN_KEY = "sms:tenant-admin:account:cooldown:";

    /** 商家管理员账号手机号验证码失败次数缓存前缀 */
    public static final String SMS_TENANT_ADMIN_ACCOUNT_FAIL_KEY = "sms:tenant-admin:account:fail:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    // ⚠ 这里原来有 STATION_CONSOLE_TICKET_KEY——独立站管理台的一次性入场票。
    //
    // 旧协议是「ERP 发一张随机票存 Redis → 管理台后端拿票到 /openapi/console/redeem
    // 换回身份」。那条兑换回调本身就是一条两边后端的互通，整条删了
    // （docs/独立站与ERP的边界.md §4.5、§10.1）。
    //
    // 新协议的跳转令牌是自包含签名令牌，ERP 侧不留任何服务端状态，所以不再需要这个 key。
}
