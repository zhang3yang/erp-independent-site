package com.ruoyi.common.utils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import com.ruoyi.common.config.RuoYiConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.PatternMatchUtils;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

/**
 * 安全服务工具类
 * 
 * @author ruoyi
 */
public class SecurityUtils
{
    public static final String BUSINESS_SHOP_HEADER = "X-Shop-Code";

    /**
     * 用户编码
     **/
    public static String getUserCode()
    {
        try
        {
            return getLoginUser().getUserCode();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取用户编码异常", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 获取部门ID
     **/
    public static Long getDeptId()
    {
        try
        {
            return getLoginUser().getDeptId();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取部门ID异常", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 获取用户账户
     **/
    public static String getUsername()
    {
        try
        {
            return getLoginUser().getUsername();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取用户账户异常", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 获取当前选择的商家代码。
     */
    public static String getShopCode()
    {
        try
        {
            return getLoginUser().getShopCode();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取当前商家异常", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Resolve the shop used by tenant business APIs. Platform super administrators
     * provide an explicitly selected shop in the request header.
     */
    public static String getBusinessShopCode()
    {
        LoginUser loginUser = getLoginUser();
        if (StringUtils.isNotEmpty(loginUser.getShopCode()))
        {
            return loginUser.getShopCode();
        }
        if (isAdmin(loginUser.getUserCode()))
        {
            String shopCode = StringUtils.trim(ServletUtils.getRequest().getHeader(BUSINESS_SHOP_HEADER));
            if (StringUtils.isNotEmpty(shopCode) && shopCode.matches("[A-Za-z0-9_-]{1,50}"))
            {
                return shopCode.toUpperCase();
            }
            throw new ServiceException("请先选择要管理的商家");
        }
        throw new ServiceException("当前账号没有可用的商家上下文", HttpStatus.FORBIDDEN);
    }

    /**
     * 获取用户
     **/
    public static LoginUser getLoginUser()
    {
        try
        {
            return (LoginUser) getAuthentication().getPrincipal();
        }
        catch (Exception e)
        {
            throw new ServiceException("获取用户信息异常", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 获取Authentication
     */
    public static Authentication getAuthentication()
    {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 生成BCryptPasswordEncoder密码
     *
     * @param password 密码
     * @return 加密字符串
     */
    public static String encryptPassword(String password)
    {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(password);
    }

    /**
     * 判断密码是否相同
     *
     * @param rawPassword 真实密码
     * @param encodedPassword 加密后字符
     * @return 结果
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword)
    {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 是否为管理员
     * 
     * @return 结果
     */
    public static boolean isAdmin()
    {
        return isAdmin(getUserCode());
    }

    /**
     * 是否为管理员
     * 
     * @param userCode 用户编码
     * @return 结果
     */
    public static boolean isAdmin(String userCode)
    {
        return RuoYiConfig.isSuperAdmin(userCode);
    }

    /**
     * 验证用户是否具备某权限
     * 
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public static boolean hasPermi(String permission)
    {
        return hasPermi(getLoginUser().getPermissions(), permission);
    }

    /**
     * 判断是否包含权限
     * 
     * @param authorities 权限列表
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public static boolean hasPermi(Collection<String> authorities, String permission)
    {
        return authorities.stream().filter(StringUtils::hasText)
                .anyMatch(x -> Constants.ALL_PERMISSION.equals(x) || PatternMatchUtils.simpleMatch(x, permission));
    }

    /**
     * 验证用户是否拥有某个角色
     * 
     * @param role 角色标识
     * @return 用户是否具备某角色
     */
    public static boolean hasRole(String role)
    {
        List<SysRole> roleList = getLoginUser().getUser().getRoles();
        Collection<String> roles = roleList.stream().map(SysRole::getRoleKey).collect(Collectors.toSet());
        return hasRole(roles, role);
    }

    /**
     * 判断是否包含角色
     * 
     * @param roles 角色列表
     * @param role 角色
     * @return 用户是否具备某角色权限
     */
    public static boolean hasRole(Collection<String> roles, String role)
    {
        return roles.stream().filter(StringUtils::hasText)
                .anyMatch(x -> Constants.SUPER_ADMIN.equals(x) || PatternMatchUtils.simpleMatch(x, role));
    }

}
