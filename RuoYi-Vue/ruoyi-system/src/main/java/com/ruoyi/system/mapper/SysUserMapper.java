package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * 用户表 数据层
 * 
 * @author ruoyi
 */
public interface SysUserMapper
{
    /**
     * 根据条件分页查询用户列表
     * 
     * @param sysUser 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUserList(SysUser sysUser);

    /**
     * 根据条件分页查询已配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectAllocatedList(SysUser user);

    /**
     * 根据条件分页查询未分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUnallocatedList(SysUser user);

    /**
     * 通过用户名查询用户
     * 
     * @param userName 用户名
     * @return 用户对象信息
     */
    public SysUser selectUserByUserName(String userName);

    /** 根据手机号查询启用中的用户 */
    public SysUser selectUserByPhonenumber(String phonenumber);

    /** 根据手机号查询未删除的平台账号，供员工复用账号场景使用。 */
    public SysUser selectEmployeeAccountByPhonenumber(String phonenumber);

    /**
     * 通过用户编码查询用户
     * 
     * @param userCode 用户编码
     * @return 用户对象信息
     */
    public SysUser selectUserByCode(String userCode);

    /**
     * 新增用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int insertUser(SysUser user);

    /**
     * 修改用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUser(SysUser user);

    /**
     * 修改用户头像
     * 
     * @param userCode 用户编码
     * @param avatar 头像地址
     * @return 结果
     */
    public int updateUserAvatar(@Param("userCode") String userCode, @Param("avatar") String avatar);

    /**
     * 修改用户状态
     * 
     * @param userCode 用户编码
     * @param status 状态
     * @return 结果
     */
    public int updateUserStatus(@Param("userCode") String userCode, @Param("status") String status);

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param userCode 用户编码
     * @param loginIp 登录IP地址
     * @param loginDate 登录时间
     * @return 结果
     */
    public int updateLoginInfo(@Param("userCode") String userCode, @Param("loginIp") String loginIp, @Param("loginDate") Date loginDate);

    /**
     * 重置用户密码
     * 
     * @param userCode 用户编码
     * @param password 密码
     * @return 结果
     */
    public int resetUserPwd(@Param("userCode") String userCode, @Param("password") String password);

    /**
     * 通过用户编码删除用户
     * 
     * @param userCode 用户编码
     * @return 结果
     */
    public int deleteUserByCode(String userCode);

    /**
     * 批量删除用户信息
     * 
     * @param userCodes 需要删除的用户编码
     * @return 结果
     */
    public int deleteUserByCodes(String[] userCodes);

    /**
     * 校验用户名称是否唯一
     * 
     * @param userName 用户名称
     * @return 结果
     */
    public SysUser checkUserNameUnique(String userName);

    /**
     * 校验手机号码是否唯一
     *
     * @param phonenumber 手机号码
     * @return 结果
     */
    public SysUser checkPhoneUnique(String phonenumber);

    /**
     * 校验email是否唯一
     *
     * @param email 用户邮箱
     * @return 结果
     */
    public SysUser checkEmailUnique(String email);
}
