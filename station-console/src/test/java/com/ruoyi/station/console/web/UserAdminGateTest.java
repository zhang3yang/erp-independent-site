package com.ruoyi.station.console.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.station.console.domain.SiteRole;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteUserService;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * 用户接口只认「站点用户」入口进来的会话（2026-09-24）。
 *
 * <p>用户管理从管理台菜单里拿掉了，唯一入口是 ERP「独立站管理」的「站点用户」按钮
 * （令牌带 scope=users → 会话 userAdmin=true）。这里锁住两件事：普通进入的会话哪怕是站长、
 * 哪怕权限是 *，也调不动用户接口；带标记的会话能调。</p>
 */
class UserAdminGateTest
{
    private MockHttpServletRequest requestWith(boolean userAdmin)
    {
        ConsoleIdentity identity = new ConsoleIdentity();
        identity.setUserName("esvaboots");
        identity.setRoleCode("admin");
        identity.setPerms(List.of("*"));
        identity.setUserAdmin(userAdmin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ConsoleSession.start(request, identity);
        return request;
    }

    private ConsoleApiController controllerWith(SiteUserService userService)
    {
        ConsoleApiController controller = new ConsoleApiController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        return controller;
    }

    @Test
    @DisplayName("普通进入的会话：站长 + 全部权限也是 403，服务层一次都不碰")
    void normalSessionRejected()
    {
        SiteUserService userService = mock(SiteUserService.class);
        ConsoleApiController controller = controllerWith(userService);
        MockHttpServletRequest request = requestWith(false);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.roles(request));
        assertEquals(403, e.getCode());
        assertThrows(ServiceException.class, () -> controller.userList(null, null, request));
        assertThrows(ServiceException.class, () -> controller.userRemove(1L, request));
        verify(userService, never()).roles();
        verify(userService, never()).delete(1L);
    }

    @Test
    @DisplayName("「站点用户」入口进来的会话：放行")
    void userAdminSessionAllowed()
    {
        SiteUserService userService = mock(SiteUserService.class);
        when(userService.roles()).thenReturn(List.of(new SiteRole()));
        ConsoleApiController controller = controllerWith(userService);

        controller.roles(requestWith(true));
        verify(userService).roles();
    }
}
