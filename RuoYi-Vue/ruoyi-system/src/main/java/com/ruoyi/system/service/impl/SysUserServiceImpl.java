package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.bean.BeanValidators;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.system.domain.SysPost;
import com.ruoyi.system.domain.SysUserPost;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysPostMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserPostMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 用户 业务层处理
 * 
 * @author ruoyi
 */
@Service
public class SysUserServiceImpl implements ISysUserService
{
    private static final int USER_CODE_GENERATION_MAX_ATTEMPTS = 1000;

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private RedisService redisService;

    @Autowired
    protected Validator validator;

    /**
     * 根据条件分页查询用户列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUserList(SysUser user)
    {
        return userMapper.selectUserList(user);
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectAllocatedList(SysUser user)
    {
        return userMapper.selectAllocatedList(user);
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUnallocatedList(SysUser user)
    {
        return userMapper.selectUnallocatedList(user);
    }

    /**
     * 通过用户名查询用户
     * 
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserByUserName(String userName)
    {
        return userMapper.selectUserByUserName(userName);
    }

    @Override
    public SysUser selectUserByPhonenumber(String phonenumber)
    {
        return userMapper.selectUserByPhonenumber(phonenumber);
    }

    @Override
    public SysUser selectEmployeeAccountByPhonenumber(String phonenumber)
    {
        return userMapper.selectEmployeeAccountByPhonenumber(phonenumber);
    }

    @Override
    public String generateUserCode()
    {
        SysUser user = new SysUser();
        ensureUserCode(user);
        return user.getUserCode();
    }

    /**
     * 通过用户编码查询用户
     * 
     * @param userCode 用户编码
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserByCode(String userCode)
    {
        return userMapper.selectUserByCode(userCode);
    }

    /**
     * 查询用户所属角色组
     * 
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(String userName)
    {
        List<SysRole> list = roleMapper.selectRolesByUserName(userName);
        if (CollectionUtils.isEmpty(list))
        {
            return StringUtils.EMPTY;
        }
        return list.stream().map(SysRole::getRoleName).collect(Collectors.joining(","));
    }

    /**
     * 查询用户所属岗位组
     * 
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(String userName)
    {
        List<SysPost> list = postMapper.selectPostsByUserName(userName);
        if (CollectionUtils.isEmpty(list))
        {
            return StringUtils.EMPTY;
        }
        return list.stream().map(SysPost::getPostName).collect(Collectors.joining(","));
    }

    /**
     * 校验用户名称是否唯一
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(SysUser user)
    {
        String userCode = StringUtils.isNull(user.getUserCode()) ? "" : user.getUserCode();
        SysUser info = userMapper.checkUserNameUnique(user.getUserName());
        if (StringUtils.isNotNull(info) && !StringUtils.equals(info.getUserCode(), userCode))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public boolean checkPhoneUnique(SysUser user)
    {
        String userCode = StringUtils.isNull(user.getUserCode()) ? "" : user.getUserCode();
        SysUser info = userMapper.checkPhoneUnique(user.getPhonenumber());
        if (StringUtils.isNotNull(info) && !StringUtils.equals(info.getUserCode(), userCode))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public boolean checkEmailUnique(SysUser user)
    {
        String userCode = StringUtils.isNull(user.getUserCode()) ? "" : user.getUserCode();
        SysUser info = userMapper.checkEmailUnique(user.getEmail());
        if (StringUtils.isNotNull(info) && !StringUtils.equals(info.getUserCode(), userCode))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验用户是否允许操作
     * 
     * @param user 用户信息
     */
    @Override
    public void checkUserAllowed(SysUser user)
    {
        if (StringUtils.isNotNull(user.getUserCode()) && user.isAdmin())
        {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 校验用户是否有数据权限
     * 
     * @param userCode 用户编码
     */
    @Override
    public void checkUserDataScope(String userCode)
    {
        if (!SecurityUtils.isAdmin())
        {
            SysUser user = new SysUser();
            user.setUserCode(userCode);
            List<SysUser> users = SpringUtils.getAopProxy(this).selectUserList(user);
            if (StringUtils.isEmpty(users))
            {
                throw new ServiceException("没有权限访问用户数据！");
            }
        }
    }

    /**
     * 新增保存用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertUser(SysUser user)
    {
        ensureUserCode(user);
        // 新增用户信息
        int rows = userMapper.insertUser(user);
        // 新增用户岗位关联
        insertUserPost(user);
        // 新增用户与角色管理
        insertUserRole(user);
        return rows;
    }

    /**
     * 注册用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean registerUser(SysUser user)
    {
        ensureUserCode(user);
        return userMapper.insertUser(user) > 0;
    }

    /**
     * 修改保存用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateUser(SysUser user)
    {
        String userCode = user.getUserCode();
        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleByUserCode(userCode);
        // 新增用户与角色管理
        insertUserRole(user);
        // 删除用户与岗位关联
        userPostMapper.deleteUserPostByUserCode(userCode);
        // 新增用户与岗位管理
        insertUserPost(user);
        return userMapper.updateUser(user);
    }

    /**
     * 用户授权角色
     * 
     * @param userCode 用户编码
     * @param roleCodes 角色组
     */
    @Override
    @Transactional
    public void insertUserAuth(String userCode, String[] roleCodes)
    {
        userRoleMapper.deleteUserRoleByUserCode(userCode);
        insertUserRole(userCode, roleCodes);
    }

    /**
     * 修改用户状态
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int updateUserStatus(SysUser user)
    {
        return userMapper.updateUserStatus(user.getUserCode(), user.getStatus());
    }

    /**
     * 修改用户基本信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int updateUserProfile(SysUser user)
    {
        return userMapper.updateUser(user);
    }

    /**
     * 修改用户头像
     * 
     * @param userCode 用户编码
     * @param avatar 头像地址
     * @return 结果
     */
    @Override
    public boolean updateUserAvatar(String userCode, String avatar)
    {
        return userMapper.updateUserAvatar(userCode, avatar) > 0;
    }

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param userCode 用户编码
     * @param loginIp 登录IP地址
     * @param loginDate 登录时间
     * @return 结果
     */
    public void updateLoginInfo(String userCode, String loginIp, Date loginDate)
    {
        userMapper.updateLoginInfo(userCode, loginIp, loginDate);
    }

    /**
     * 重置用户密码
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int resetPwd(SysUser user)
    {
        return userMapper.resetUserPwd(user.getUserCode(), user.getPassword());
    }

    /**
     * 重置用户密码
     * 
     * @param userCode 用户编码
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetUserPwd(String userCode, String password)
    {
        return userMapper.resetUserPwd(userCode, password);
    }

    /**
     * 新增用户角色信息
     * 
     * @param user 用户对象
     */
    public void insertUserRole(SysUser user)
    {
        this.insertUserRole(user.getUserCode(), user.getRoleCodes());
    }

    /**
     * 新增用户岗位信息
     * 
     * @param user 用户对象
     */
    public void insertUserPost(SysUser user)
    {
        Long[] posts = user.getPostIds();
        if (StringUtils.isNotEmpty(posts))
        {
            // 新增用户与岗位管理
            List<SysUserPost> list = new ArrayList<SysUserPost>(posts.length);
            for (Long postId : posts)
            {
                SysUserPost up = new SysUserPost();
                up.setUserCode(user.getUserCode());
                up.setPostId(postId);
                list.add(up);
            }
            userPostMapper.batchUserPost(list);
        }
    }

    /**
     * 新增用户角色信息
     * 
     * @param userCode 用户编码
     * @param roleCodes 角色组
     */
    public void insertUserRole(String userCode, String[] roleCodes)
    {
        if (StringUtils.isNotEmpty(roleCodes))
        {
            long distinctRoleCount = java.util.Arrays.stream(roleCodes).filter(java.util.Objects::nonNull).distinct().count();
            if (distinctRoleCount != roleCodes.length || roleMapper.countPlatformRoles(roleCodes) != roleCodes.length)
            {
                throw new ServiceException("平台用户只能分配平台角色");
            }
            // 新增用户与角色管理
            List<SysUserRole> list = new ArrayList<SysUserRole>(roleCodes.length);
            for (String roleCode : roleCodes)
            {
                SysUserRole ur = new SysUserRole();
                ur.setUserCode(userCode);
                ur.setRoleCode(roleCode);
                list.add(ur);
            }
            userRoleMapper.batchUserRole(list);
        }
    }

    /**
     * 通过用户编码删除用户
     * 
     * @param userCode 用户编码
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteUserByCode(String userCode)
    {
        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleByUserCode(userCode);
        // 删除用户与岗位表
        userPostMapper.deleteUserPostByUserCode(userCode);
        return userMapper.deleteUserByCode(userCode);
    }

    /**
     * 批量删除用户信息
     * 
     * @param userCodes 需要删除的用户编码
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteUserByCodes(String[] userCodes)
    {
        for (String userCode : userCodes)
        {
            checkUserAllowed(new SysUser(userCode));
            checkUserDataScope(userCode);
        }
        // 删除用户与角色关联
        userRoleMapper.deleteUserRole(userCodes);
        // 删除用户与岗位关联
        userPostMapper.deleteUserPost(userCodes);
        return userMapper.deleteUserByCodes(userCodes);
    }

    /**
     * 导入用户数据
     * 
     * @param userList 用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    @Override
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(userList) || userList.size() == 0)
        {
            throw new ServiceException("导入用户数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (SysUser user : userList)
        {
            try
            {
                // 验证是否存在这个用户
                SysUser u = userMapper.selectUserByUserName(user.getUserName());
                if (StringUtils.isNull(u))
                {
                    BeanValidators.validateWithException(validator, user);
                    deptService.checkDeptDataScope(user.getDeptId());
                    String password = configService.selectConfigByKey("sys.user.initPassword");
                    user.setPassword(SecurityUtils.encryptPassword(password));
                    user.setCreateBy(operName);
                    ensureUserCode(user);
                    userMapper.insertUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 导入成功");
                }
                else if (isUpdateSupport)
                {
                    BeanValidators.validateWithException(validator, user);
                    checkUserAllowed(u);
                    checkUserDataScope(u.getUserCode());
                    deptService.checkDeptDataScope(user.getDeptId());
                    user.setUserCode(u.getUserCode());
                    user.setDeptId(u.getDeptId());
                    user.setUpdateBy(operName);
                    userMapper.updateUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、账号 " + user.getUserName() + " 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String msg = "<br/>" + failureNum + "、账号 " + user.getUserName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        else
        {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    private void ensureUserCode(SysUser user)
    {
        if (StringUtils.isNotEmpty(user.getUserCode()))
        {
            if (!user.getUserCode().matches("^us[0-9A-Z]+$"))
            {
                throw new ServiceException("用户编码格式不正确");
            }
            return;
        }
        for (int attempt = 0; attempt < USER_CODE_GENERATION_MAX_ATTEMPTS; attempt++)
        {
            java.util.Map result = redisService.geDayKey(KeyType.Base36Upper, "us");
            if (!Boolean.TRUE.equals(result.get("result")) || result.get("primaryKey") == null)
            {
                throw new ServiceException("生成用户编码失败：" + StringUtils.defaultIfEmpty(
                        String.valueOf(result.get("errorinfo")), String.valueOf(result.get("msg"))));
            }
            String userCode = String.valueOf(result.get("primaryKey"));
            if (userMapper.selectUserByCode(userCode) == null)
            {
                user.setUserCode(userCode);
                return;
            }
        }
        throw new ServiceException("生成用户编码失败：连续生成的用户编码均已存在");
    }
}
