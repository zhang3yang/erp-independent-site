package com.ruoyi.web.controller.system;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.model.TenantAdminPermissionBody;
import com.ruoyi.system.domain.model.TenantAdminAccountBody;
import com.ruoyi.system.domain.model.TenantShopBody;
import com.ruoyi.system.domain.TenantDepartment;
import com.ruoyi.system.domain.TenantEmployee;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ITenantEmployeeService;
import com.ruoyi.system.service.ITenantShopService;
import com.ruoyi.common.utils.file.OssStorageService;
import com.ruoyi.common.utils.file.OssUploadResult;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.core.domain.model.SmsCodeBody;
import com.ruoyi.common.sms.SmsVerificationService;

/**
 * Platform-only assignment of each shop administrator's immutable permission set.
 */
@RestController
@RequestMapping("/system/tenant-admin")
@PreAuthorize("@ss.isPlatformAdmin()")
public class TenantPlatformController extends BaseController
{
    @Autowired
    private OssStorageService ossStorageService;
    @Autowired
    private ITenantShopService tenantShopService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private ITenantEmployeeService tenantEmployeeService;

    @Autowired
    private SmsVerificationService smsVerificationService;

    @GetMapping("/list")
    public AjaxResult list()
    {
        return success(tenantShopService.selectShopAdminRoles());
    }

    @GetMapping("/departments")
    public AjaxResult departments(TenantDepartment query)
    {
        return success(tenantEmployeeService.selectPlatformDeptList(query));
    }

    @GetMapping("/employees")
    public com.ruoyi.common.core.page.TableDataInfo employees(TenantEmployee employee)
    {
        startPage();
        return getDataTable(tenantEmployeeService.selectPlatformEmployeeList(employee));
    }

    @GetMapping("/shop/{shopCode}")
    public AjaxResult getShop(@PathVariable String shopCode)
    {
        return success(tenantShopService.selectShopDetailByCode(shopCode));
    }

    @Log(title = "Shop management", businessType = BusinessType.INSERT)
    @PostMapping("/shop")
    public AjaxResult createShop(@Validated @RequestBody TenantShopBody body)
    {
        AjaxResult result = toAjax(tenantShopService.createShop(body, getUserCode(), getUsername()));
        result.put("shopCode", body.getShopCode());
        return result;
    }

    @PostMapping("/shop/image")
    public AjaxResult uploadShopImage(@RequestParam("file") MultipartFile file) throws Exception
    {
        OssUploadResult result = ossStorageService.uploadImage(file, "shop");
        if (result == null)
        {
            String fileName = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, MimeTypeUtils.IMAGE_EXTENSION, true);
            AjaxResult local = success();
            local.put("url", fileName);
            local.put("fileName", fileName);
            return local;
        }
        AjaxResult ajax = success();
        ajax.put("url", result.getUrl());
        ajax.put("fileName", result.getUrl());
        ajax.put("thumbUrl", result.getThumbUrl());
        return ajax;
    }

    @Log(title = "Shop management", businessType = BusinessType.UPDATE)
    @PutMapping("/shop/{shopCode}")
    public AjaxResult updateShop(@PathVariable String shopCode,
            @Validated @RequestBody TenantShopBody body)
    {
        return toAjax(tenantShopService.updateShop(shopCode, body, getUsername()));
    }

    @Log(title = "Shop management", businessType = BusinessType.DELETE)
    @DeleteMapping("/shop/{shopCode}")
    public AjaxResult deleteShop(@PathVariable String shopCode)
    {
        return toAjax(tenantShopService.deleteShop(shopCode, getUsername()));
    }

    @GetMapping({ "/permission-tree", "/permission-tree/{roleCode}" })
    public AjaxResult permissionTree(@PathVariable(required = false) String roleCode)
    {
        List<TreeSelect> menus = menuService.buildMenuTreeSelect(tenantShopService.selectTenantAssignableMenus());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("menus", menus);
        ajax.put("checkedKeys", roleCode == null ? List.of()
                : Arrays.asList(tenantShopService.selectShopAdminRole(roleCode).getFunCodes()));
        return ajax;
    }

    @Log(title = "商家管理员权限", businessType = BusinessType.GRANT)
    @PutMapping("/{roleCode}/permissions")
    public AjaxResult updatePermissions(@PathVariable String roleCode,
            @RequestBody TenantAdminPermissionBody body)
    {
        return toAjax(tenantShopService.updateShopAdminPermissions(roleCode, body.getFunCodes()));
    }

    @Log(title = "Shop administrator account", businessType = BusinessType.UPDATE, excludeParamNames = { "smsCode" })
    @PutMapping("/{roleCode}/account")
    public AjaxResult saveAccount(@PathVariable String roleCode,
            @Validated @RequestBody TenantAdminAccountBody body)
    {
        return toAjax(tenantShopService.saveShopAdminAccount(roleCode, body, getUsername()));
    }

    @PreAuthorize("@ss.isPlatformAdmin()")
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/{roleCode}/account/sms/code")
    public AjaxResult sendAccountSmsCode(@PathVariable String roleCode, @RequestBody SmsCodeBody body)
    {
        tenantShopService.selectShopAdminRole(roleCode);
        smsVerificationService.sendTenantAdminAccountCode(body == null ? null : body.getPhonenumber(),
                body == null ? null : body.getPlatcode());
        AjaxResult ajax = AjaxResult.success("验证码已发送");
        ajax.put("expireSeconds", 300);
        ajax.put("retryAfterSeconds", 60);
        return ajax;
    }
}
