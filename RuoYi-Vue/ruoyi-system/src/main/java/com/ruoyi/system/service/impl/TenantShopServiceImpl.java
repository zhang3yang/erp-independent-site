package com.ruoyi.system.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.TenantShop;
import com.ruoyi.system.domain.TenantShopAdmin;
import com.ruoyi.system.domain.model.TenantAdminAccountBody;
import java.util.regex.Pattern;
import com.ruoyi.system.domain.model.TenantShopBody;
import com.ruoyi.system.mapper.TenantShopMapper;
import com.ruoyi.system.mapper.TenantEmployeeMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.ITenantShopService;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.common.sms.SmsVerificationService;

/**
 * 登录账号与商家关系服务实现。
 */
@Service
public class TenantShopServiceImpl implements ITenantShopService
{
    @Autowired
    private TenantShopMapper tenantShopMapper;

    @Autowired
    private TenantEmployeeMapper tenantEmployeeMapper;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private SmsVerificationService smsVerificationService;

    @Autowired
    private RoleCodeGenerator roleCodeGenerator;

    @Override
    public List<TenantShop> selectShopsByAccount(String userCode)
    {
        if (userCode == null)
        {
            return Collections.emptyList();
        }
        return tenantShopMapper.selectShopsByAccount(userCode);
    }

    @Override
    public TenantShop selectShopByAccountAndCode(String userCode, String shopCode)
    {
        if (userCode == null || StringUtils.isEmpty(shopCode))
        {
            return null;
        }
        return tenantShopMapper.selectShopByAccountAndCode(userCode, shopCode);
    }

    @Override
    public List<SysRole> selectRolesByMember(String shopCode, String userCode)
    {
        return tenantShopMapper.selectRolesByMember(shopCode, userCode);
    }

    @Override
    public boolean isShopAdmin(String shopCode, String userCode)
    {
        return StringUtils.isNotEmpty(shopCode) && userCode != null
                && tenantShopMapper.countShopAdminMembership(shopCode, userCode) > 0;
    }

    @Override
    public Set<String> selectRoleCodesByMember(String shopCode, String userCode)
    {
        return new HashSet<>(tenantShopMapper.selectRoleCodesByMember(shopCode, userCode));
    }

    @Override
    public Set<String> selectPermissionsByMember(String shopCode, String userCode)
    {
        return new HashSet<>(tenantShopMapper.selectPermissionsByMember(shopCode, userCode));
    }

    @Override
    public Set<String> selectPermissionsByShop(String shopCode)
    {
        return new HashSet<>(tenantShopMapper.selectPermissionsByShop(shopCode));
    }

    @Override
    public Set<String> selectFunCodesByMember(String shopCode, String userCode)
    {
        return new HashSet<>(tenantShopMapper.selectFunCodesByMember(shopCode, userCode));
    }

    @Override
    public Set<String> selectFunCodesByShop(String shopCode)
    {
        return new HashSet<>(tenantShopMapper.selectFunCodesByShop(shopCode));
    }

    @Override
    public List<SysMenu> selectMenuTreeByMember(String shopCode, String userCode)
    {
        return tenantShopMapper.selectMenuTreeByMember(shopCode, userCode);
    }

    @Override
    public List<SysMenu> selectMenuTreeByShop(String shopCode)
    {
        return tenantShopMapper.selectMenuTreeByShop(shopCode);
    }

    @Override
    public List<TenantShopAdmin> selectShopAdminRoles()
    {
        List<TenantShopAdmin> shops = tenantShopMapper.selectShopAdminRoles();
        for (TenantShopAdmin shop : shops)
        {
            shop.setFunCodes(tenantShopMapper.selectShopAdminFunCodes(shop.getRoleCode()).toArray(new String[0]));
        }
        return shops;
    }

    @Override
    public TenantShopAdmin selectShopAdminRole(String roleCode)
    {
        TenantShopAdmin shop = tenantShopMapper.selectShopAdminRoleByCode(roleCode);
        if (shop == null)
        {
            throw new ServiceException("Built-in shop administrator role does not exist");
        }
        shop.setFunCodes(tenantShopMapper.selectShopAdminFunCodes(roleCode).toArray(new String[0]));
        return shop;
    }

    @Override
    public List<SysMenu> selectTenantAssignableMenus()
    {
        return tenantShopMapper.selectTenantAssignableMenus();
    }

    @Override
    @Transactional
    public int updateShopAdminPermissions(String roleCode, String[] funCodes)
    {
        TenantShopAdmin shop = selectShopAdminRole(roleCode);
        List<SysMenu> assignableMenus = tenantShopMapper.selectTenantAssignableMenus();
        Map<String, SysMenu> menuMap = assignableMenus.stream()
                .collect(Collectors.toMap(SysMenu::getFunCode, Function.identity()));
        Set<String> allowedFunCodes = menuMap.keySet();
        Set<String> submittedFunCodes = funCodes == null ? java.util.Collections.emptySet()
                : Arrays.stream(funCodes)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        if (!allowedFunCodes.containsAll(submittedFunCodes))
        {
            throw new ServiceException(
                    "Shop administrator permissions can only use tenant, purchase, inventory, or production menus");
        }
        String[] requestedFunCodes = includeFunCodeParents(funCodes, menuMap);
        tenantShopMapper.deleteShopAdminRoleMenus(shop.getRoleCode());
        int rows = requestedFunCodes.length == 0 ? 1
                : tenantShopMapper.batchInsertShopAdminRoleMenus(shop.getRoleCode(), requestedFunCodes);
        Set<String> businessFunCodes = collectShopBusinessFunCodes(assignableMenus, menuMap);
        String[] selectedBusinessFunCodes = Arrays.stream(requestedFunCodes)
                .filter(businessFunCodes::contains)
                .toArray(String[]::new);
        tenantShopMapper.deleteShopBusinessMenus(shop.getShopCode());
        if (selectedBusinessFunCodes.length > 0)
        {
            tenantShopMapper.insertShopBusinessMenus(shop.getShopCode(), selectedBusinessFunCodes,
                    SecurityUtils.getUsername());
        }
        return rows;
    }

    private String[] includeFunCodeParents(String[] funCodes, Map<String, SysMenu> menuMap)
    {
        Set<String> normalized = new LinkedHashSet<>();
        if (funCodes != null)
        {
            Arrays.stream(funCodes).filter(java.util.Objects::nonNull).forEach(funCode -> {
                String currentId = funCode;
                while (currentId != null && menuMap.containsKey(currentId) && normalized.add(currentId))
                {
                    String parentId = menuMap.get(currentId).getParentId();
                    currentId = "0".equals(parentId) ? null : parentId;
                }
            });
        }
        return normalized.toArray(new String[0]);
    }

    private boolean isShopBusinessMenu(SysMenu menu)
    {
        return StringUtils.startsWithAny(menu.getPerms(),
                com.ruoyi.common.constant.TenantPermissionPrefixes.BUSINESS_ONLY);
    }

    private Set<String> collectShopBusinessFunCodes(List<SysMenu> menus, Map<String, SysMenu> menuMap)
    {
        Set<String> businessFunCodes = new LinkedHashSet<>();
        menus.stream().filter(this::isShopBusinessMenu).forEach(menu -> {
            String currentId = menu.getFunCode();
            while (currentId != null && menuMap.containsKey(currentId) && businessFunCodes.add(currentId))
            {
                String parentId = menuMap.get(currentId).getParentId();
                currentId = "0".equals(parentId) ? null : parentId;
            }
        });
        return businessFunCodes;
    }

    @Override
    @Transactional
    public int saveShopAdminAccount(String roleCode, TenantAdminAccountBody body, String operator)
    {
        TenantShopAdmin shop = selectShopAdminRole(roleCode);
        String nickName = StringUtils.trim(body.getNickName());
        String phonenumber = StringUtils.trim(body.getPhonenumber());
        if (!Pattern.matches("^1[3-9]\\d{9}$", phonenumber))
        {
            throw new ServiceException("Mobile number must be an 11-digit mainland China mobile number");
        }
        // 页面不再维护账号状态，请求里没带就沿用账号原有状态
        String status = StringUtils.isEmpty(body.getStatus()) ? null
                : (StringUtils.equals(UserConstants.USER_DISABLE, body.getStatus())
                        ? UserConstants.USER_DISABLE : UserConstants.NORMAL);

        String currentUserCode = shop.getAdminUserCode();
        SysUser currentAccount = currentUserCode == null ? null : userService.selectUserByCode(currentUserCode);
        if (currentUserCode != null && (currentAccount == null || currentAccount.isAdmin()
                || tenantEmployeeMapper.countPlatformBindingsByUserCode(currentUserCode) > 0))
        {
            throw new ServiceException("Shop administrator account is invalid");
        }

        SysUser account = currentAccount != null
                && StringUtils.equals(currentAccount.getPhonenumber(), phonenumber)
                ? currentAccount : userService.selectEmployeeAccountByPhonenumber(phonenumber);
        boolean updateCurrentAccount = account != null && StringUtils.equals(account.getUserCode(), currentUserCode);
        boolean createAccount = false;

        if (account != null)
        {
            if (account.isAdmin() || tenantEmployeeMapper.countPlatformBindingsByUserCode(account.getUserCode()) > 0)
            {
                throw new ServiceException("Platform administrator account cannot be reused as a shop administrator");
            }
            if (!updateCurrentAccount && !UserConstants.NORMAL.equals(account.getStatus()))
            {
                throw new ServiceException("The account associated with this mobile number is disabled");
            }
            if (!updateCurrentAccount && tenantShopMapper.countShopAdminMembership(shop.getShopCode(), account.getUserCode()) > 0)
            {
                throw new ServiceException("This account is already the administrator of the current shop");
            }
        }
        else
        {
            // 页面不再填写密码，新账号统一使用系统配置的初始密码
            String initialPassword = StringUtils.defaultIfEmpty(body.getPassword(),
                    configService.selectConfigByKey("sys.user.initPassword"));
            if (StringUtils.isEmpty(initialPassword))
            {
                throw new ServiceException("System initial password is not configured");
            }
            account = new SysUser();
            account.setUserCode(userService.generateUserCode());
            account.setUserName(account.getUserCode());
            account.setNickName(nickName);
            account.setPhonenumber(phonenumber);
            account.setStatus(status == null ? UserConstants.NORMAL : status);
            account.setCreateBy(operator);
            account.setRemark("Shop administrator: " + shop.getShopCode());
            account.setPassword(SecurityUtils.encryptPassword(initialPassword));
            createAccount = true;
        }

        smsVerificationService.verifyTenantAdminAccountCode(phonenumber, body.getSmsCode());
        if (createAccount)
        {
            userService.insertUser(account);
        }
        else if (updateCurrentAccount)
        {
            account.setNickName(nickName);
            account.setPhonenumber(phonenumber);
            account.setStatus(status == null ? account.getStatus() : status);
            account.setUpdateBy(operator);
            // 这里不再重置密码，置空避免 updateShopAdminUser 重写密码和最后修改时间
            account.setPassword(null);
            tenantShopMapper.updateShopAdminUser(account);
        }

        Long deptId = tenantShopMapper.selectShopRootDeptId(shop.getShopCode());
        tenantShopMapper.upsertShopAdminMembership(shop.getShopCode(), account.getUserCode(), deptId, operator);
        tenantShopMapper.demoteOtherShopOwners(shop.getShopCode(), account.getUserCode(), operator);
        tenantShopMapper.deleteOtherShopAdminRoleMembers(shop.getShopCode(), roleCode, account.getUserCode());
        tenantShopMapper.insertShopAdminRoleMember(shop.getShopCode(), roleCode, account.getUserCode(), operator);
        return 1;
    }

    @Override
    public TenantShop selectShopByCode(String shopCode)
    {
        if (StringUtils.isEmpty(shopCode))
        {
            return null;
        }
        return tenantShopMapper.selectShopByCode(shopCode);
    }

    @Override
    public TenantShop selectShopDetailByCode(String shopCode)
    {
        if (StringUtils.isEmpty(shopCode))
        {
            return null;
        }
        return tenantShopMapper.selectShopDetailByCode(shopCode);
    }

    @Override
    @Transactional
    public int createShop(TenantShopBody body, String operatorUserCode, String operator)
    {
        String shopCode = generateShopCode();
        if (tenantShopMapper.selectShopByCode(shopCode) != null)
        {
            throw new ServiceException("Shop code already exists");
        }
        body.setShopCode(shopCode);
        normalizeShop(body);
        tenantShopMapper.insertShop(body);
        tenantShopMapper.insertShopRootDept(shopCode, rootDeptName(body.getShopName()), operator);
        tenantShopMapper.provisionAllTenantMenus(shopCode, operator);
        String roleCode = roleCodeGenerator.generate();
        tenantShopMapper.insertBuiltInAdminRole(roleCode, shopCode, builtInRoleKey(shopCode), operator);
        Long deptId = tenantShopMapper.selectShopRootDeptId(shopCode);
        if (roleCode == null || deptId == null)
        {
            throw new ServiceException("Shop initialization failed");
        }
        tenantShopMapper.insertDefaultAdminRoleMenus(roleCode);
        tenantShopMapper.insertSuperAdminMembership(shopCode, operatorUserCode, deptId, operator);
        tenantShopMapper.insertSuperAdminRole(shopCode, operatorUserCode, roleCode, operator);
        return 1;
    }

    /**
     * Generate the immutable shop identifier using the shared daily Base36 key
     * sequence. The generated format is {@code sp + geDayKey(Base36)}.
     */
    private String generateShopCode()
    {
        Map result = redisService.geDayKey(KeyType.Base36Upper, "sp");
        if (!Boolean.TRUE.equals(result.get("result")) || result.get("primaryKey") == null)
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException("Shop code generation failed"
                    + (error == null ? "" : ": " + error));
        }
        String shopCode = String.valueOf(result.get("primaryKey"));
        if (!shopCode.matches("^sp[0-9A-Z]+$"))
        {
            throw new ServiceException("Generated shop code has an invalid format");
        }
        return shopCode;
    }

    @Override
    @Transactional
    public int updateShop(String shopCode, TenantShopBody body, String operator)
    {
        TenantShop current = selectShopByCode(shopCode);
        if (current == null)
        {
            throw new ServiceException("Shop does not exist");
        }
        if (!StringUtils.equalsIgnoreCase(shopCode, StringUtils.trim(body.getShopCode())))
        {
            throw new ServiceException("Shop code cannot be changed after creation");
        }
        normalizeShop(body);
        return tenantShopMapper.updateShop(shopCode, body);
    }

    @Override
    @Transactional
    public int deleteShop(String shopCode, String operator)
    {
        if (selectShopByCode(shopCode) == null)
        {
            throw new ServiceException("Shop does not exist");
        }
        tenantShopMapper.deleteShopUserPosts(shopCode);
        tenantShopMapper.deleteShopUserRoles(shopCode);
        tenantShopMapper.deleteShopUsers(shopCode);
        tenantShopMapper.deleteShopMenus(shopCode);
        tenantShopMapper.deleteShopRoleMenus(shopCode);
        tenantShopMapper.deleteShopRoleDepts(shopCode);
        tenantShopMapper.deleteShopRoles(shopCode);
        tenantShopMapper.deleteShopPosts(shopCode);
        tenantShopMapper.deleteShopDepts(shopCode);
        tenantShopMapper.deleteLegacyShopRoleFunctions(shopCode);
        tenantShopMapper.deleteLegacyShopStaffRoles(shopCode);
        tenantShopMapper.deleteLegacyShopStaff(shopCode);
        tenantShopMapper.deleteLegacyShopRoles(shopCode);
        tenantShopMapper.deleteLegacyShopOrganizations(shopCode);
        tenantShopMapper.deleteShopMigrationIssues(shopCode);
        return tenantShopMapper.markShopDeleted(shopCode);
    }

    private void normalizeShop(TenantShopBody shop)
    {
        shop.setShopName(StringUtils.trim(shop.getShopName()));
        shop.setProvince(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getProvince()), ""));
        shop.setCity(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getCity()), ""));
        shop.setDistrict(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getDistrict()), ""));
        shop.setAddress(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getAddress()), ""));
        shop.setTelephone(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getTelephone()), ""));
        shop.setDescription(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getDescription()), ""));
        shop.setImageUrl(StringUtils.defaultIfEmpty(StringUtils.trim(shop.getImageUrl()), ""));
        // ⚠ console_url 是【遗留列】，现在**没有任何读取方**。
        //
        // 它原来是「三级管理台地址」里的商家那一级：站点档案 → 商家档案 → 全局配置。
        // docs/独立站与ERP的边界.md §4.5 把这条链整个删了：
        //
        //     地址现在一站一份，从站点档案的域名派生（§7），
        //     全局兜底反而会让 A 站的人跳到 B 站。
        //
        // 列和字段留着不删（删列不可逆，历史值还有考古价值），但商家编辑表单上那一项
        // 已经去掉了。校验仍然保留：万一哪天有人从接口写进来一个 javascript: 开头的串，
        // 它不该被存下来等着以后被谁拼进页面。
        //
        // 空值落成 null 而不是空串：null 和数据库列的 DEFAULT NULL 一致，
        // 翻库时能一眼分清「没填」和「填了个空」。
        String consoleUrl = StringUtils.trimToNull(shop.getConsoleUrl());
        if (consoleUrl != null && !isHttpUrl(consoleUrl))
        {
            throw new ServiceException("管理台地址必须以 http:// 或 https:// 开头");
        }
        shop.setConsoleUrl(consoleUrl);
        shop.setStatus(Integer.valueOf(0).equals(shop.getStatus()) ? 0 : 1);
    }

    private String rootDeptName(String shopName)
    {
        String suffix = "总部";
        int maxNameLength = 30 - suffix.length();
        String value = shopName.length() > maxNameLength ? shopName.substring(0, maxNameLength) : shopName;
        return value + suffix;
    }

    private String builtInRoleKey(String shopCode)
    {
        String normalized = shopCode.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return "shop_admin_" + normalized;
    }
    /**
     * 只放行 http / https。
     *
     * <p>原来用的是 {@code StationConsoleProperties.isHttpUrl}；那个类瘦身之后没有这个方法了
     * （它连 baseUrl 一起删了，见 docs/独立站与ERP的边界.md §4.5）。
     * 这里只剩一个调用点，就地放一个私有方法，不值得为它再建一个工具类。</p>
     */
    private static boolean isHttpUrl(String url)
    {
        if (url == null)
        {
            return false;
        }
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
