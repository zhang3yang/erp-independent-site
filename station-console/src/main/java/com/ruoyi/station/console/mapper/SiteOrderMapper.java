package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteOrder;

/**
 * 站点订单。对应表 {@code u_s_order}。
 *
 * <p>⚠ 这里没有一个方法碰 ERP 的任何东西（boundary 要点 4）。SQL 里也一律不写库限定名——
 * 站点后端只连 {@code sdsstation} 这一个库，不写库名就物理上不可能指到别的库去。</p>
 */
public interface SiteOrderMapper
{
    /** 前台提交，或管理台手工录 */
    int insert(SiteOrder order);

    /**
     * 后台列表。字段为空即不过滤，<b>不带订单行</b>。
     *
     * <p>不带行是有意的：一页一百张单、每张再查一次明细就是 N+1。
     * 明细在详情里查（{@link #selectByCode}）。</p>
     *
     * <p>分页参数单独传、不塞进 query 对象——理由和询盘那边一样：
     * 那个对象也是公网提交接口的入参，往上加 offset/limit 等于开两个可以塞任意值的字段。</p>
     */
    List<SiteOrder> selectList(@Param("query") SiteOrder query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteOrder query);

    /** 按单号查一张单（不含行，行由 SiteOrderItemMapper 查） */
    SiteOrder selectByCode(@Param("orderCode") String orderCode);

    /**
     * 推进状态 + 打对应的时间戳。
     *
     * <p>时间戳<b>在 SQL 里用 coalesce 只写一次</b>：同一个状态被点两次（网络重试、
     * 两个人同时点），第二次不该把时间改成更晚的那一刻——那会让「什么时候发货的」
     * 变成「最后一次有人点发货是什么时候」。</p>
     */
    int updateStatus(SiteOrder order);

    /** 只改内部备注，不动状态和时间轴 */
    int updateRemark(SiteOrder order);

    /** 按邮箱把历史订单挂到客户档案上。客户档案是后于订单出现的，所以要回填 */
    int bindBuyerByEmail(@Param("email") String email, @Param("buyerCode") String buyerCode);

    /** 某个客户的订单数，客户列表里那一列用 */
    int countByBuyer(@Param("buyerCode") String buyerCode);

    // ==================== 改结算币种时的批量换算 ====================

    /**
     * 换算前先数：有多少张单的 {@code subtotal} 本来就和它的行对不上。
     *
     * <p>{@link #convertSubtotal} 把小计<b>重算</b>成 {@code sum(linetotal)} 而不是
     * 拿旧小计乘汇率——只有重算才能保证换完之后「行加起来 = 小计」。代价是库里
     * 原本就不自洽的单会被顺手改成一致，那是在修一个既有问题，但它发生在一次
     * 「我只是改了个币种」的操作里。所以先数出来记进换算流水，让运营看得见。</p>
     *
     * <p>必须在任何换算<b>之前</b>调：订单行换完而小计还没换的那一刻，所有单都对不上。</p>
     */
    int countSubtotalMismatch();

    /**
     * 本站所有订单的币种码改成新的，小计重算为该单所有行的 {@code linetotal} 之和。
     *
     * <p><b>⚠ 必须在 {@link SiteOrderItemMapper#syncLineTotal()} 之后调</b>：
     * 它读的是换算完的行小计。顺序反了的结果是订单币种是新的、金额还是旧的，
     * 而这两个数字单看都「像对的」。</p>
     */
    int convertSubtotal(@Param("toCode") String toCode);
}
