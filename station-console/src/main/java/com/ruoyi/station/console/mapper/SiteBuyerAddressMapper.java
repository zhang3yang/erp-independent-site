package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteBuyerAddress;

/**
 * 买家收货地址簿。对应表 {@code u_s_buyeraddr}。
 *
 * <h3>⚠ 这个接口里每一个方法都带 {@code buyerCode}，一个都不能少</h3>
 * <p>这张表对外用自增 id（不发业务码，理由见建表脚本），id 是连号可猜的。
 * 所以「按 id 取一条」这种签名<b>不存在</b>——凡是按 id 定位的方法，
 * id 和 buyerCode 都要一起进 where。</p>
 *
 * <p>少一处的后果不是「数据错了」，是「把 URL 里的数字改一下就能读到、
 * 甚至改掉别人的收货地址和电话」。这一条比这张表上任何别的考虑都重要。</p>
 *
 * <p>{@code buyerCode} 的唯一来源是买家会话（{@code BuyerAccountController.requireBuyer}），
 * <b>绝不从请求参数取</b>。</p>
 */
public interface SiteBuyerAddressMapper
{
    /** 某个买家的地址簿。默认的排最前，其余按新建时间倒排 */
    List<SiteBuyerAddress> selectByBuyer(@Param("buyerCode") String buyerCode);

    /** 按 id 取一条。⚠ buyerCode 一起进 where：不是他的就当不存在 */
    SiteBuyerAddress selectOne(@Param("id") Long id, @Param("buyerCode") String buyerCode);

    /** 他现在有几条。服务层用它挡住「一个账号存几千条地址」 */
    int countByBuyer(@Param("buyerCode") String buyerCode);

    int insert(SiteBuyerAddress address);

    /**
     * 改一条。⚠ where 带 buyerCode，改不是自己的那条时影响 0 行（服务层据此报 404）。
     *
     * <p>不动 {@code isdefault}：那一列只由 {@link #clearDefault} + {@link #markDefault}
     * 这一对改。混在一起的话，一次普通的编辑就可能把默认标记搬到另一条上。</p>
     */
    int update(SiteBuyerAddress address);

    int delete(@Param("id") Long id, @Param("buyerCode") String buyerCode);

    /** 把这个买家所有地址的默认标记清零。⚠ 必须和 {@link #markDefault} 在同一个事务里 */
    int clearDefault(@Param("buyerCode") String buyerCode);

    /** 置一条为默认。where 带 buyerCode，同上 */
    int markDefault(@Param("id") Long id, @Param("buyerCode") String buyerCode);

    /**
     * 他还剩下的最新一条的 id，没有就返回 null。
     *
     * <p>删掉默认地址之后用它顶上：不补的话，结账页会变成「有地址簿但选不出默认」，
     * 而买家看不出为什么。</p>
     */
    Long selectLatestId(@Param("buyerCode") String buyerCode);
}
