package com.ruoyi.system.domain.vo;

import java.io.Serializable;

/**
 * 「还没了结的单子有几张、其中拖过期限的有几张」——采购待收货和在产工单共用这一个形状。
 *
 * <p>两块的口径不同（一个看预计到货日，一个看计划生产日），但呈现完全一样：
 * 卡片上一个总数，底下一行红字写逾期几张。共用一个 VO 是为了让这件事在类型上就摆明，
 * 省得两边各写一个只差字段名的类。</p>
 *
 * <p>逾期数是总数的子集，不是并列的另一堆——前端不要把两个数加起来。</p>
 *
 * @author wczl
 */
public class DashboardFollowUp implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 未了结的张数 */
    private int activeCount;

    /** 其中已过期限的张数，是 {@link #activeCount} 的子集 */
    private int overdueCount;

    public int getActiveCount() { return activeCount; }
    public void setActiveCount(int activeCount) { this.activeCount = activeCount; }

    public int getOverdueCount() { return overdueCount; }
    public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }
}
