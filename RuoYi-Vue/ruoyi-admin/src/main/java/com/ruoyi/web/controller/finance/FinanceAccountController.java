package com.ruoyi.web.controller.finance;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.FinanceAccount;
import com.ruoyi.system.service.IFinanceAccountService;

/**
 * 账户信息 Controller（说明书 5.5 接口8）
 */
@RestController
@RequestMapping("/finance/account")
public class FinanceAccountController extends BaseController
{
    @Autowired
    private IFinanceAccountService accountService;

    /**
     * 查询公司账户列表
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:account:list')")
    @GetMapping("/list")
    public TableDataInfo list(FinanceAccount account)
    {
        startPage();
        account.setShopCode(SecurityUtils.getBusinessShopCode());
        return getDataTable(accountService.selectAccountList(account));
    }

    /**
     * 启用中的账户，供资金流水选择付款/收款账户。不分页。
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/options")
    public AjaxResult options()
    {
        FinanceAccount query = new FinanceAccount();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setIsActive(1);
        return success(accountService.selectAccountList(query));
    }

    /**
     * 获取账户详情
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:account:query')")
    @GetMapping("/{accountCode}")
    public AjaxResult getInfo(@PathVariable("accountCode") String accountCode)
    {
        FinanceAccount account = accountService.selectAccountByCode(
                SecurityUtils.getBusinessShopCode(), accountCode);
        if (account == null)
        {
            return error("账户不存在");
        }
        return success(account);
    }

    /**
     * 新增账户
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:account:add')")
    @Log(title = "财务账户", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody FinanceAccount account)
    {
        account.setShopCode(SecurityUtils.getBusinessShopCode());
        account.setCreateUser(SecurityUtils.getUsername());
        return toAjax(accountService.insertAccount(account));
    }

    /**
     * 修改账户。余额不接收，只由资金流水驱动。
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:account:edit')")
    @Log(title = "财务账户", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody FinanceAccount account)
    {
        account.setShopCode(SecurityUtils.getBusinessShopCode());
        account.setUpdateUser(SecurityUtils.getUsername());
        return toAjax(accountService.updateAccount(account));
    }

    /**
     * 删除账户
     */
    @PreAuthorize("@ss.hasBusinessShop() and @ss.hasPermi('finance:account:remove')")
    @Log(title = "财务账户", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(accountService.deleteAccountByIds(SecurityUtils.getBusinessShopCode(), ids));
    }
}
