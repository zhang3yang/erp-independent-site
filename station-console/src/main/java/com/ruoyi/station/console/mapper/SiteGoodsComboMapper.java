package com.ruoyi.station.console.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.domain.SiteGoodsComboLang;

/**
 * 商品组合。对应表 {@code u_s_sitegoods_combo} 和 {@code u_s_sitegoods_combo_lang}。
 *
 * <h3>对外和后台的语句照例分开写</h3>
 * <p>对外那条（{@code selectPublicByGoods}）永远带 {@code status = '可选'}，
 * 后台那条永远不带。这和 {@link SiteGoodsMapper} 里 publishstatus 的处理是同一条规矩：
 * 混在一起用参数控制，早晚有人传错值把停用的组合捅到公网上。</p>
 *
 * <h3>方法签名里为什么没有 shopCode / siteCode</h3>
 * <p>一个部署一个站，这两列不是筛选维度。写入时由 {@code SiteContext} 填，
 * 见 {@link SiteInfoMapper} 的类注释。</p>
 */
public interface SiteGoodsComboMapper
{
    // ==================== 对外（前台） ====================

    /**
     * 某个商品对外可选的组合，名称按语言回退。
     *
     * @param lang        当前语言
     * @param defaultLang 站点默认语言，当前语言没填名称时用它
     */
    List<SiteGoodsCombo> selectPublicByGoods(@Param("goodsCode") String goodsCode,
            @Param("lang") String lang, @Param("defaultLang") String defaultLang);

    /**
     * 按编码取一条对外可选的组合。<b>提交询盘和下单时用它做服务端校验</b>——
     * 价格必须以库里的为准，不能信前端传来的那个数。
     */
    SiteGoodsCombo selectPublicOne(@Param("goodsCode") String goodsCode,
            @Param("comboCode") String comboCode,
            @Param("lang") String lang, @Param("defaultLang") String defaultLang);

    // ==================== 后台：含停用的 ====================

    /** 后台列表：某个商品的全部组合，名称取站点默认语言那一条给列表显示 */
    List<SiteGoodsCombo> selectAdminByGoods(@Param("goodsCode") String goodsCode,
            @Param("lang") String lang);

    /** 一个商品下全部组合的全部语言名称，编辑弹窗按语言分页签展示 */
    List<SiteGoodsComboLang> selectLangsByGoods(@Param("goodsCode") String goodsCode);

    /** 存一条组合。有则更新无则插入 */
    int upsert(SiteGoodsCombo combo);

    /** 存一条组合在某种语言下的名称。有则更新无则插入 */
    int upsertLang(SiteGoodsComboLang lang);

    /**
     * 删掉一个商品下**不在这批编码里**的组合（连同它们的语言行）。
     *
     * <p>保存是「整份替换」：前端提交的那批就是全部，库里多出来的是被删掉的那些。
     * {@code comboCodes} 为空表示这个商品一条组合都不要了。</p>
     */
    int deleteMissing(@Param("goodsCode") String goodsCode, @Param("comboCodes") List<String> comboCodes);

    int deleteLangsMissing(@Param("goodsCode") String goodsCode, @Param("comboCodes") List<String> comboCodes);

    /** 商品被删时连带清掉，两张表各一条 */
    int deleteByGoods(@Param("goodsCodes") String[] goodsCodes);

    int deleteLangsByGoods(@Param("goodsCodes") String[] goodsCodes);

    // ==================== 改结算币种时的批量换算 ====================

    /**
     * 把本站所有型号的价格乘上汇率。语义和 {@link SiteGoodsMapper#convertPrice} 完全一致，
     * 理由不重复写，看那一份。
     *
     * <p><b>⚠ 漏了这张表的后果比漏商品表更隐蔽</b>：商品价是列表里看得见的一列，
     * 改完币种一眼就能发现没换；型号价藏在编辑弹窗的第二个页签里，
     * 没换的话要等买家选了型号下单、拿到一个按旧币种数字计价的订单才暴露。
     * {@code SiteOrderService#repriceLines} 里「选了型号就按型号价」那一行就是它的出口。</p>
     *
     * <p>这条语句<b>带 sitecode 条件</b>，而本文件里其他语句都只按 goodscode 过滤。
     * 不是不一致：其他语句都有 goodscode 兜着，最多波及邻站同编码的那一个商品；
     * 这条没有 goodscode，少了 sitecode 就是把同库里别的站的型号价<b>全部</b>乘一遍。</p>
     */
    int convertPrice(@Param("rate") BigDecimal rate, @Param("updateUser") String updateUser);
}
