package com.ruoyi.station.console.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「用户管理」按部署留在菜单里（console.users-menu，2026-09-27）。
 *
 * <p>法国站（默认 false）：只有「站点用户」按钮进来的会话能管用户，站长 + * 也不行——
 * 和 {@link UserAdminGateTest} 锁的是同一件事。统领（true）：从「进入管理」进来、
 * 角色有 site:user:list 的也能，运营角色照样不能。</p>
 */
class UsersMenuGrantTest
{
    @Test
    @DisplayName("「站点用户」按钮进来的：不看开关、不看角色，一律能")
    void usersButtonAlwaysGrants()
    {
        assertTrue(SsoController.grantsUserAdmin(true, false, List.of()));
        assertTrue(SsoController.grantsUserAdmin(true, true, List.of()));
    }

    @Test
    @DisplayName("开关关着（法国站）：站长 + * 从「进入管理」进来也不能")
    void menuOffDeniesNormalEntry()
    {
        assertFalse(SsoController.grantsUserAdmin(false, false, List.of("*")));
        assertFalse(SsoController.grantsUserAdmin(false, false, List.of("site:user:list")));
    }

    @Test
    @DisplayName("开关开着（统领）：有 site:user:list 或 * 的能，没有的不能")
    void menuOnFollowsRolePerm()
    {
        assertTrue(SsoController.grantsUserAdmin(false, true, List.of("*")));
        assertTrue(SsoController.grantsUserAdmin(false, true, List.of("site:goods:list", "site:user:list")));
        assertFalse(SsoController.grantsUserAdmin(false, true, List.of("site:goods:list")));
        assertFalse(SsoController.grantsUserAdmin(false, true, null));
    }
}
