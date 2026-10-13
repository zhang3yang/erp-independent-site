package com.ruoyi.system.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 登录名 → 员工姓名（昵称）解析。单据/流水/主数据里存的操作人、审核人、创建人都是登录名
 * （{@code SecurityUtils.getUsername()}），列表展示时统一按这张表换成 sys_user.nick_name。
 */
public interface StaffNameMapper
{
    /**
     * 批量查登录名对应的昵称。返回每行 {@code login=登录名, name=昵称}；
     * 库里没有的登录名不返回，由调用方回退显示原值。
     */
    public List<Map<String, Object>> selectNickNames(@Param("logins") Collection<String> logins);

    /**
     * 反向查：按昵称模糊匹配，返回登录名。供「按员工姓名筛业务单据」用——姓名不落业务表，
     * 只能先换成账号再去业务表里筛。
     *
     * @param keyword 姓名关键字，调用方保证非空且已去空白
     * @return 匹配到的登录名，可能为空列表
     */
    public List<String> selectLoginsByNickNameLike(@Param("keyword") String keyword);
}
