package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * 用户 业务层
 * 
 * @author ruoyi
 */
public interface ISysUserService
{
    /**
     * 根据条件分页查询用户列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUserList(SysUser user);

    /**
     * 根据条件分页查询已分配用户角色列表
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

    /** Generate a new platform user code. */
    public String generateUserCode();

    /**
     * 根据用户名查询用户所属角色组
     * 
     * @param userName 用户名
     * @return 结果
     */
    public String selectUserRoleGroup(String userName);

    /**
     * 根据用户名查询用户所属岗位组
     * 
     * @param userName 用户名
     * @return 结果
     */
    public String selectUserPostGroup(String userName);

    /**
     * 校验用户名称是否唯一
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkUserNameUnique(SysUser user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkPhoneUnique(SysUser user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkEmailUnique(SysUser user);

    /**
     * 校验用户是否允许操作
     * 
     * @param user 用户信息
     */
    public void checkUserAllowed(SysUser user);

    /**
     * 校验用户是否有数据权限
     * 
     * @param userCode 用户编码
     */
    public void checkUserDataScope(String userCode);

    /**
     * 新增用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int insertUser(SysUser user);

    /**
     * 注册用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean registerUser(SysUser user);

    /**
     * 修改用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUser(SysUser user);

    /**
     * 用户授权角色
     * 
     * @param userCode 用户编码
     * @param roleCodes 角色组
     */
    public void insertUserAuth(String userCode, String[] roleCodes);

    /**
     * 修改用户状态
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUserStatus(SysUser user);

    /**
     * 修改用户基本信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUserProfile(SysUser user);

    /**
     * 修改用户头像
     * 
     * @param userCode 用户编码
     * @param avatar 头像地址
     * @return 结果
     */
    public boolean updateUserAvatar(String userCode, String avatar);

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param userCode 用户编码
     * @param loginIp 登录IP地址
     * @param loginDate 登录时间
     * @return 结果
     */
    public void updateLoginInfo(String userCode, String loginIp, Date loginDate);

    /**
     * 重置用户密码
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int resetPwd(SysUser user);

    /**
     * 重置用户密码
     * 
     * @param userCode 用户编码
     * @param password 密码
     * @return 结果
     */
    public int resetUserPwd(String userCode, String password);

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
     * 导入用户数据
     * 
     * @param userList 用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName);
}
