package com.ruoyi.system.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.mapper.StaffNameMapper;

/**
 * 把登录名解析成员工姓名（sys_user.nick_name），供列表/详情展示。
 *
 * <p>单据、流水、主数据里的操作人 / 审核人 / 创建人存的都是登录名
 * （{@code SecurityUtils.getUsername()}）。展示 code 可读性差，统一经这里换成姓名。
 * 查不到的（如已删除的账号）回退显示原登录名，不留空。</p>
 */
@Component
public class StaffNameResolver
{
    @Autowired
    private StaffNameMapper staffNameMapper;

    /**
     * 批量解析。返回 {@code Map<登录名, 姓名>}，只含库里查得到的；调用方对未命中的自行回退。
     */
    public Map<String, String> nickNamesByLogin(Collection<String> logins)
    {
        Map<String, String> result = new HashMap<>();
        if (logins == null || logins.isEmpty())
        {
            return result;
        }
        // 去重且剔空，避免拼出空的 in()
        Set<String> distinct = new LinkedHashSet<>();
        for (String login : logins)
        {
            if (login != null && !login.isEmpty())
            {
                distinct.add(login);
            }
        }
        if (distinct.isEmpty())
        {
            return result;
        }
        for (Map<String, Object> row : staffNameMapper.selectNickNames(distinct))
        {
            Object login = row.get("login");
            Object name = row.get("name");
            if (login != null && name != null && !name.toString().isEmpty())
            {
                result.put(login.toString(), name.toString());
            }
        }
        return result;
    }

    /**
     * 单个解析。查不到就回退返回原登录名（登录名本身为空时原样返回）。
     */
    public String nickNameOrLogin(String login)
    {
        if (login == null || login.isEmpty())
        {
            return login;
        }
        return nickNamesByLogin(Collections.singletonList(login)).getOrDefault(login, login);
    }

    /**
     * 反向解析：按姓名<b>模糊</b>查登录名。给「按员工姓名筛业务单据」用。
     *
     * <p>姓名不落业务表（业务表存的是登录名），所以按姓名筛只能先换成账号，
     * 再拿账号去业务表里筛。</p>
     *
     * @return 匹配到的登录名。<b>一个都没匹配上返回空列表</b>——调用方必须把这当成
     *         「筛不出任何单据」，不能当成「没有筛选条件」，否则会把全部数据都列出来。
     */
    public List<String> loginsByNickNameLike(String keyword)
    {
        if (keyword == null || keyword.trim().isEmpty())
        {
            return Collections.emptyList();
        }
        return staffNameMapper.selectLoginsByNickNameLike(keyword.trim());
    }
}
