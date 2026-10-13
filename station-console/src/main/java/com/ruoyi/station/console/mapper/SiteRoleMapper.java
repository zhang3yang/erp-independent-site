package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteRole;

/**
 * 管理台角色。对应表 {@code u_s_role}。
 *
 * <p>出厂两条：{@code admin}（站长，权限 {@code *}）和 {@code operator}（运营）。
 * 站长可以在本站改 {@code perms}，与 ERP 无关（§4.3）。</p>
 */
public interface SiteRoleMapper
{
    SiteRole selectByCode(@Param("roleCode") String roleCode);

    List<SiteRole> selectAll();

    int updatePerms(@Param("roleCode") String roleCode, @Param("perms") String perms);
}
