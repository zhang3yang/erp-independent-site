package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 管理台操作用户。对应表 {@code u_s_user}。
 *
 * <p><b>和 ERP 的 {@code sys_user} 没有任何关系</b>——没有外键，没有同步，没有共用主键。
 * docs/独立站与ERP的边界.md 要点 5：每套独立站自己成立用户体系，站与站之间、
 * 站与 ERP 之间都不关联。</p>
 *
 * <h3>只有一个来源</h3>
 * <p>{@link #SOURCE_ERP_SSO} —— 从 ERP 点「进入管理」第一次跳过来时自动落的。
 * 本站没有登录页，也建不出账号，所以这是唯一会产生新行的路径。</p>
 *
 * <p>⚠ {@link #SOURCE_LOCAL} 是<b>遗留值</b>：§6a 那一版有过账号密码登录，
 * 站长能在本站手工建账号。那一版撤掉了（见 {@code SessionController}），
 * 常量留着只为让库里已有的 {@code local} 行还能正常显示。</p>
 *
 * <h3>没有 password 字段</h3>
 * <p>{@code u_s_user.password} 那一列还在（建库脚本里标了「已废弃」），
 * 但<b>没有任何代码读它或写它</b>——本站不存密码，也就没有一份可以被离线爆破的东西。
 * 这个类上原来有一个 {@code @JsonIgnore private String password}，
 * 那道「别序列化出去」的防护现在不需要了，因为对象上根本没有它。</p>
 */
public class SiteUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 【遗留】站长在本站手工建的账号。已经建不出新的了，见类注释 */
    public static final String SOURCE_LOCAL = "local";
    /** 从 ERP 首次跳转自动落的账号 */
    public static final String SOURCE_ERP_SSO = "erpsso";

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_DISABLED = "停用";

    private Long id;
    private String username;
    private String nickname;
    /** 角色编码，见 {@link SiteRole}。ERP 首次跳进来的人给默认值，之后由站长在本站调 */
    private String roleCode;
    private String source;
    /**
     * 对应 ERP 跳转令牌里的 {@code payload.user}，只作为<b>身份标识</b>。
     *
     * <p>免密进入靠它找人。本地账号为 null。</p>
     *
     * <p>⚠ 它不是「这个人在 ERP 里的权限」——令牌里根本没有 perms，那是有意的：
     * 权限归本站这套，由 {@link #roleCode} 决定。把 ERP 的权限码塞进来，
     * 等于两边的权限模型又绑在一起了（§4.3）。</p>
     */
    private String erpUser;
    private String status;
    private Integer loginCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastLoginTime;
    private String lastLoginIp;
    private String createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @JsonIgnore
    public boolean isDisabled()
    {
        return STATUS_DISABLED.equals(status);
    }

    /** 显示用的人名：昵称优先，没有就用登录名 */
    public String displayName()
    {
        return nickname != null && !nickname.isBlank() ? nickname : username;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getErpUser() { return erpUser; }
    public void setErpUser(String erpUser) { this.erpUser = erpUser; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }

    public Date getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
