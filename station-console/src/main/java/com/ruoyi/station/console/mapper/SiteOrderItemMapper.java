package com.ruoyi.station.console.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteOrderItem;

/**
 * 订单行。对应表 {@code u_s_orderitem}。
 *
 * <p>行只在下单时写一次，之后不改：它是下单当时的商品快照
 * （见 {@link com.ruoyi.station.console.domain.SiteOrderItem} 的类注释）。
 * 所以这里没有 update，也没有单行删除。</p>
 */
public interface SiteOrderItemMapper
{
    /** 批量插一张单的所有行。一次 insert 多值，不是循环单条 */
    int insertBatch(@Param("items") List<SiteOrderItem> items);

    List<SiteOrderItem> selectByOrder(@Param("orderCode") String orderCode);

    // ==================== 改结算币种时的批量换算 ====================

    /*
      ⚠ 上面那句「行只在下单时写一次，之后不改」仍然成立。

      它说的是业务内容：商品名、型号、单价、数量都是下单当时的快照。换币种改的不是
      内容而是**计价单位**——同一笔钱换一种写法，买家买的东西和付的钱都没变。

      拆成 convertPrice + syncLineTotal 两步，是为了不把
      「linetotal = price × qty」的正确性押在「MySQL 的多列赋值从左到右」
      这个方言细节上。完整理由在 SiteOrderItemMapper.xml 里，写反了不报错、
      只是行小计少乘一次汇率。
    */

    /**
     * 本站所有订单行的单价乘上汇率，币种码一并改成新的。
     *
     * <p><b>⚠ 不跳过面议行</b>（{@code price = 0}）：金额确实不变，但它的
     * {@code currency} 列同样要改。漏了就是同一张单里标价行写 USD、
     * 面议行写 GBP。</p>
     */
    int convertPrice(@Param("rate") BigDecimal rate, @Param("toCode") String toCode);

    /**
     * 按<b>已经换算过的</b>单价重算行小计。<b>必须紧跟在 {@link #convertPrice} 之后调</b>，
     * 单独调一次是拿旧单价重算，等于什么都没做。
     */
    int syncLineTotal();
}
