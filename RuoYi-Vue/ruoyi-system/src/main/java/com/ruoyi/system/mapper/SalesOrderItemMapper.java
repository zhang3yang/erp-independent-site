package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.SalesOrderProductionCandidate;

/**
 * 销售订单明细 Mapper。明细跟随所属订单的月份，{@code tableSuffix} 一律取订单的后缀。
 */
public interface SalesOrderItemMapper
{
    List<SalesOrderItem> selectByOrderCode(@Param("shopCode") String shopCode,
            @Param("orderCode") String orderCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 按订单号批量取明细。发货下拉、补发选单这类「一次要看几十上百张单」的场景走这里，
     * 不要在订单循环里调 {@link #selectByOrderCode}。
     *
     * @param orderCodes 由调用方保证非空
     */
    List<SalesOrderItem> selectByOrderCodes(@Param("shopCode") String shopCode,
            @Param("orderCodes") List<String> orderCodes, @Param("tableSuffix") String tableSuffix);

    /**
     * 一个月表里所有真正发出过货的明细（shippedqty &gt; 0），供销售补发选单。
     *
     * <p>补发的前提就是「这一行已经发出去过」，把这个条件下推到 SQL，
     * 调用方只需要一次查询就能拿到全月的候选明细，不必逐单回表。</p>
     */
    List<SalesOrderItem> selectShippedItemsByMonth(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix);

    List<SalesOrderItem> selectByOrderCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("orderCode") String orderCode, @Param("tableSuffix") String tableSuffix);

    SalesOrderItem selectByDetailCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("detailCode") String detailCode, @Param("tableSuffix") String tableSuffix);

    /**
     * 一个月表里所有<b>还没发完</b>的订单明细，连订单头一起查出来，供生产建工单时选单。
     *
     * <p>两道过滤，都只关「这活儿还值不值得做」，不关「该由谁做」：</p>
     * <ul>
     *   <li>订单状态是已备货 / 备货中 / 发货中——草稿和待审核还没过审，替它们排产等于
     *       绕过审核；已取消、已关闭、已完成没有再生产的意义；</li>
     *   <li>未发量 {@code qty - shippedqty > 0}——发完的行没什么可做的。</li>
     * </ul>
     *
     * <p><b>不按 stock_source 过滤。</b>订单页已经不展示、也不再能登记备货来源，那个字段
     * 现在只由建单时的「专门生产」推出来；拿它当门槛等于只有定制件排得了产，普通标准件
     * 想按订单做就没有入口了。挑哪些行做是计划员在选单界面上的决定。</p>
     *
     * <p><b>也不按库存过滤。</b>订单不占库存，现货随时可能被别的单发走，
     * 用它筛只会把该排产的活儿漏掉。</p>
     *
     * @param goodsCode 传了就只看这个商品，用于「按产出商品找还差多少」
     */
    List<SalesOrderProductionCandidate> selectProductionCandidates(@Param("shopCode") String shopCode,
            @Param("tableSuffix") String tableSuffix, @Param("goodsCode") String goodsCode);

    int batchInsert(@Param("items") List<SalesOrderItem> items, @Param("tableSuffix") String tableSuffix);

    /**
     * 累加已发货量并同步未发量。{@code shippedqty + delta <= qty} 兜住超发：
     * 并发发货时后到的那笔更新 0 行，服务层据此报错，不会把订单发超。
     */
    int increaseShippedQty(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix, @Param("delta") BigDecimal delta);

    /** 记下正在负责这条明细的生产工单号，传 null 表示松开占用。 */
    int updateSourceCode(@Param("shopCode") String shopCode, @Param("detailCode") String detailCode,
            @Param("tableSuffix") String tableSuffix, @Param("sourceCode") String sourceCode);

    int deleteByOrderCode(@Param("shopCode") String shopCode, @Param("orderCode") String orderCode,
            @Param("tableSuffix") String tableSuffix);
}
