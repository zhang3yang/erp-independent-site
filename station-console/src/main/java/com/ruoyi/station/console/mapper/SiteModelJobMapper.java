package com.ruoyi.station.console.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteModelJob;

/**
 * 混元生 3D 任务（{@code u_s_model_job}）。
 */
public interface SiteModelJobMapper
{
    int insertJob(SiteModelJob job);

    /** 更新状态类字段（status / tcjobid / modelid / previewurl / 错误 / 计数 / finishtime），不动提交参数 */
    int updateJob(SiteModelJob job);

    SiteModelJob selectByCode(@Param("jobId") String jobId);

    /** 轮询要处理的：SUBMITTING（只为兜底判失败）/ WAIT / RUN / SAVING，按创建时间先来先查 */
    List<SiteModelJob> selectPending(@Param("limit") int limit);

    List<SiteModelJob> selectList(@Param("offset") int offset, @Param("limit") int limit);

    int countList();

    /** 某个时刻之后提交过几次（每日上限用；FAIL 的也算——提交失败多半没扣积分，但防的是乱点） */
    int countSince(@Param("since") Date since);

    int deleteByCode(@Param("jobId") String jobId);
}
