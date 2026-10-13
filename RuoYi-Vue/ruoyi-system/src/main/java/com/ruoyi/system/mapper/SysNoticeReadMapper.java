package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.domain.SysNoticeRead;

/**
 * 公告已读记录 数据层
 *
 * @author ruoyi
 */
public interface SysNoticeReadMapper
{
    /**
     * 新增已读记录（忽略重复）
     *
     * @param noticeRead 已读记录
     * @return 结果
     */
    public int insertNoticeRead(SysNoticeRead noticeRead);

    /**
     * 查询某用户未读公告数量
     *
     * @param userCode 用户编码
     * @return 未读数量
     */
    public int selectUnreadCount(@Param("userCode") String userCode);

    /**
     * 查询某用户是否已读某公告
     *
     * @param noticeId 公告ID
     * @param userCode   用户编码
     * @return 已读记录数（0未读 1已读）
     */
    public int selectIsRead(@Param("noticeId") Long noticeId, @Param("userCode") String userCode);

    /**
     * 批量标记已读
     *
     * @param userCode    用户编码
     * @param noticeIds 公告ID数组
     * @return 结果
     */
    public int insertNoticeReadBatch(@Param("userCode") String userCode, @Param("noticeIds") Long[] noticeIds);

    /**
     * 查询带已读状态的公告列表（SQL层限制条数，一次查询完成）
     *
     * @param userCode 用户编码
     * @param limit  最多返回条数
     * @return 带 isRead 标记的公告列表
     */
    public List<SysNotice> selectNoticeListWithReadStatus(@Param("userCode") String userCode, @Param("limit") int limit);

    /**
     * 查询已阅读某公告的用户列表
     *
     * @param noticeId 公告ID
     * @param searchValue 搜索值
     * @return 已读用户列表
     */
    public List<Map<String, Object>> selectReadUsersByNoticeId(@Param("noticeId") Long noticeId, @Param("searchValue") String searchValue);

    /**
     * 公告删除时清理对应已读记录
     *
     * @param noticeIds 公告ID数组
     * @return 结果
     */
    public int deleteByNoticeIds(@Param("noticeIds") Long[] noticeIds);
}
