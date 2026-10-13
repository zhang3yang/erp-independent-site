package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.SysUserPost;

/**
 * 用户与岗位关联表 数据层
 * 
 * @author ruoyi
 */
public interface SysUserPostMapper
{
    /**
     * 通过用户编码删除用户和岗位关联
     * 
     * @param userCode 用户编码
     * @return 结果
     */
    public int deleteUserPostByUserCode(String userCode);

    /**
     * 通过岗位ID查询岗位使用数量
     * 
     * @param postId 岗位ID
     * @return 结果
     */
    public int countUserPostById(Long postId);

    /**
     * 批量删除用户和岗位关联
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteUserPost(String[] ids);

    /**
     * 批量新增用户岗位信息
     * 
     * @param userPostList 用户岗位列表
     * @return 结果
     */
    public int batchUserPost(List<SysUserPost> userPostList);
}
