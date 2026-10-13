package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 管理台角色。对应表 {@code u_s_role}。
 *
 * <p>{@link #perms} 存的就是前端 {@code v-hasPermi} 用的那批权限串
 * （{@code site:goods:list} 之类），逗号分隔——沿用同一套串，
 * {@code station-console-ui} 的按钮显隐逻辑一个字都不用改。</p>
 *
 * <h3>为什么不做「用户-角色」多对多</h3>
 * <p>一个站的后台用户是个位数，一人一角色够用。多一张关联表就多一处 join，
 * 而这里没有任何需求要求它。真需要了再加，那时候数据也就几十行。</p>
 *
 * <h3>和 ERP 权限的关系：没有关系</h3>
 * <p>跳转令牌里<b>没有 perms</b>，这是有意的（§4.3）。ERP 只说「这是谁」，
 * 不说「他能干什么」——后者由这里的角色配置决定。一个在 ERP 里是超管的人，
 * 第一次跳进某个站，拿到的也只是这个站的默认角色。</p>
 */
public class SiteRole implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 站长：本站全部权限 */
    public static final String ROLE_ADMIN = "admin";
    /** 运营：日常维护，不含首页装修和存储设置 */
    public static final String ROLE_OPERATOR = "operator";

    /** 通配权限。{@code perms} 是这一个字符时表示全部放行 */
    public static final String ALL = "*";

    private String roleCode;
    private String roleName;
    private String perms;
    private Integer sortNum;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 权限串拆成列表。顺序无所谓，判定用 contains */
    @JsonIgnore
    public List<String> permList()
    {
        List<String> result = new ArrayList<>();
        if (perms == null || perms.trim().isEmpty())
        {
            return result;
        }
        for (String item : perms.split(","))
        {
            String perm = item == null ? null : item.trim();
            if (perm != null && !perm.isEmpty() && !result.contains(perm))
            {
                result.add(perm);
            }
        }
        return result;
    }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getPerms() { return perms; }
    public void setPerms(String perms) { this.perms = perms; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
