package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteUser;

/**
 * 管理台操作用户。对应表 {@code u_s_user}。
 *
 * <p>本站自己的用户体系，和 ERP 的 {@code sys_user} 没有任何关系
 * （docs/独立站与ERP的边界.md 要点 5）。</p>
 */
public interface SiteUserMapper
{
    /** 按登录名精确查。只用来查重名：免密进入时给新用户起名要避开已有的行 */
    SiteUser selectByUsername(@Param("username") String username);

    /**
     * 免密进入用。按 ERP 令牌里的 {@code payload.user} 找人。
     *
     * <p>查不到不是错误——第一次从 ERP 跳进来的人本来就没有本站账号，
     * 由 Service 落一个 {@code source=erpsso} 的新用户（§6b）。</p>
     */
    SiteUser selectByErpUser(@Param("erpUser") String erpUser);

    SiteUser selectById(@Param("id") Long id);

    /** 用户管理列表 */
    List<SiteUser> selectList(@Param("keyword") String keyword, @Param("status") String status);

    int insert(SiteUser user);

    /** 改昵称、角色、状态。登录名不允许改，理由见 SiteUserMapper.xml */
    int update(SiteUser user);

    // ⚠ updatePassword 删了：本站不存密码，password 那一列没有任何代码读写（见 SiteUser 类注释）

    int deleteById(@Param("id") Long id);

    /** 本站还剩几个可用的站长。删人/停用/降级之前要看它，别把最后一个站长弄没了 */
    int countActiveAdmins(@Param("roleCode") String roleCode, @Param("excludeId") Long excludeId);

    /** 免密进入成功后打点 */
    int touchLogin(@Param("id") Long id, @Param("loginIp") String loginIp);
}
