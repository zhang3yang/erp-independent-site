package com.ruoyi.common.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取项目相关配置
 * 
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi")
public class RuoYiConfig
{
    /** 项目名称 */
    private String name;

    /** 版本 */
    private String version;

    /** 版权年份 */
    private String copyrightYear;

    /** 上传路径 */
    private static String profile;

    /** 获取地址开关 */
    private static boolean addressEnabled;

    /** 验证码类型 */
    private static String captchaType;

    /**
     * 平台超级管理员用户 ID。始终保留默认的 1，并允许在配置文件中追加账号。
     * 使用用户 ID 而不是昵称判断，避免昵称修改后权限发生变化。
     */
    private static volatile Set<String> superAdminCodes = Collections.singleton("us1");

    private List<String> superAdminUserCodes;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getCopyrightYear()
    {
        return copyrightYear;
    }

    public void setCopyrightYear(String copyrightYear)
    {
        this.copyrightYear = copyrightYear;
    }

    public static String getProfile()
    {
        return profile;
    }

    public void setProfile(String profile)
    {
        RuoYiConfig.profile = profile;
    }

    public static boolean isAddressEnabled()
    {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled)
    {
        RuoYiConfig.addressEnabled = addressEnabled;
    }

    public static String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        RuoYiConfig.captchaType = captchaType;
    }

    public List<String> getSuperAdminUserCodes()
    {
        return superAdminUserCodes;
    }

    public void setSuperAdminUserCodes(List<String> superAdminUserCodes)
    {
        this.superAdminUserCodes = superAdminUserCodes;
        Set<String> configuredCodes = new HashSet<>();
        configuredCodes.add("us1");
        if (superAdminUserCodes != null)
        {
            superAdminUserCodes.stream().filter(java.util.Objects::nonNull).forEach(configuredCodes::add);
        }
        RuoYiConfig.superAdminCodes = Collections.unmodifiableSet(configuredCodes);
    }

    /**
     * 获取全部平台超级管理员用户编码。
     */
    public static Set<String> getSuperAdminCodes()
    {
        return superAdminCodes;
    }

    /**
     * 判断用户是否为平台超级管理员。
     */
    public static boolean isSuperAdmin(String userCode)
    {
        return userCode != null && superAdminCodes.contains(userCode);
    }

    /**
     * 获取导入上传路径
     */
    public static String getImportPath()
    {
        return getProfile() + "/import";
    }

    /**
     * 获取头像上传路径
     */
    public static String getAvatarPath()
    {
        return getProfile() + "/avatar";
    }

    /**
     * 获取下载路径
     */
    public static String getDownloadPath()
    {
        return getProfile() + "/download/";
    }

    /**
     * 获取上传路径
     */
    public static String getUploadPath()
    {
        return getProfile() + "/upload";
    }
}
