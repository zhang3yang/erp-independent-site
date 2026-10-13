package com.ruoyi.station.console.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteGoodsLang;

/**
 * 站点商品。对应表 {@code u_s_sitegoods} 和 {@code u_s_sitegoods_lang}。
 *
 * <h3>⚠ 和 ERP 那版最大的区别：没有 join 了</h3>
 * <p>ERP 那版每条查询都 {@code left join sdsshop.u_m_waregoods} 去取商品名、规格、档案图
 * （XML 里三处）。那是一条跨库互通，库搬到本机之后连语法都不成立
 * （docs/独立站与ERP的边界.md §5.4）。那四个字段现在是 {@code u_s_sitegoods} 自己的列。</p>
 *
 * <h3>对外和后台的语句刻意分开写</h3>
 * <p>对外那几条（{@code selectPublic*}）永远带 {@code publishstatus = '已上架'}，
 * 后台那几条（{@code selectAdmin*}）永远不带。混在一起用参数控制，
 * 早晚会有人传错值把草稿捅到公网上。</p>
 *
 * <h3>方法签名里为什么没有 shopCode / siteCode</h3>
 * <p>一个部署一个站，这个库里只有这一个站的商品，那两列不是筛选维度（§5.3）。
 * 写入时它们由 {@code SiteContext} 填，见 {@link SiteInfoMapper} 的类注释。</p>
 */
public interface SiteGoodsMapper
{
    // ==================== 对外（前台）：只返回已上架 ====================

    /**
     * 对外的商品列表。按语言回退取文案，不返回详情富文本（那个字段可以很大）。
     *
     * @param lang        当前语言
     * @param defaultLang 站点默认语言，当前语言没有文案时用它
     * @param keyword     关键字，匹配标题、卖点、商品编码；为空不过滤
     * @param category    站点分类；为空不过滤
     */
    List<SiteGoods> selectPublicList(@Param("lang") String lang, @Param("defaultLang") String defaultLang,
            @Param("keyword") String keyword, @Param("category") String category,
            @Param("offset") int offset, @Param("limit") int limit);

    /** 对外商品列表的总数，条件与 {@link #selectPublicList} 完全一致 */
    int countPublicList(@Param("lang") String lang, @Param("defaultLang") String defaultLang,
            @Param("keyword") String keyword, @Param("category") String category);

    /** 对外的商品详情，比列表多一个详情富文本 */
    SiteGoods selectPublicOne(@Param("goodsCode") String goodsCode, @Param("lang") String lang,
            @Param("defaultLang") String defaultLang);

    /** 已上架商品用到的站点分类，列表页的筛选按钮由它生成 */
    List<String> selectPublicCategories();

    // ==================== 后台：含草稿和已下架，绝不能被对外接口调用 ====================

    /** 后台列表：文案取站点默认语言那一条给列表显示 */
    List<SiteGoods> selectAdminList(@Param("lang") String lang, @Param("keyword") String keyword,
            @Param("publishStatus") String publishStatus, @Param("category") String category);

    /** 后台按商品编码查一条，不限上架状态 */
    SiteGoods selectAdminOne(@Param("goodsCode") String goodsCode);

    /**
     * 这个商品编码在本站存在吗。不限上架状态，只回 0 或 1。
     *
     * <p>给浏览量埋点用（{@code PageViewService}）：那个接口在公网上匿名开放，
     * 前端说「我在看商品 X」时必须验一下 X 真的存在，否则任何人都能往
     * {@code u_s_pv_daily} 里塞出无穷多个不存在的商品编码——那张表的主键里有
     * {@code goodscode}，塞多少就长多少行，而且商品排行里会混进一堆
     * <b>join 不到商品名</b>的幽灵行。</p>
     *
     * <p>⚠ 为什么不用 {@link #selectAdminOne} 判 null：那条要把整行（含 specsjson、
     * siteimgs 这些能到 2000 字符的列）读出来再扔掉，而这里每看一个商品详情页
     * 就要判一次。只回一个 count 便宜得多。</p>
     *
     * <p>⚠ 不限上架状态是有意的：商品下架之后，它<b>下架前</b>那些浏览记录
     * 仍然是真实发生过的数据，不该因为状态变了就记不上。</p>
     */
    int countByCode(@Param("goodsCode") String goodsCode);

    int insert(SiteGoods goods);

    int update(SiteGoods goods);

    /** 批量改上架状态，上架 / 下架按钮走它 */
    int updateStatus(@Param("goodsCodes") String[] goodsCodes,
            @Param("publishStatus") String publishStatus, @Param("updateUser") String updateUser);

    int deleteByCodes(@Param("goodsCodes") String[] goodsCodes);

    // ==================== 多语言文案 ====================

    /** 一个商品的全部语言文案，编辑弹窗按语言分页签展示 */
    List<SiteGoodsLang> selectLangs(@Param("goodsCode") String goodsCode);

    /** 存一种语言的文案。有则更新无则插入，省得前端还要判断是新增还是修改 */
    int upsertLang(SiteGoodsLang lang);

    int deleteLangsByGoods(@Param("goodsCodes") String[] goodsCodes);

    // ==================== 改结算币种时的批量换算 ====================

    /**
     * 把本站所有商品的售价乘上汇率。
     *
     * <p>只在 {@code u_s_siteinfo.currency} 被改掉时由
     * {@link com.ruoyi.station.console.service.CurrencyConvertService} 调一次。
     * 在此之前改币种只换符号不动数字，等于把 £100 的商品静默变成 $100。</p>
     *
     * <p><b>⚠ 只动 {@code price > 0} 的行。</b>{@code price = 0} 的含义是「面议」
     * 而不是「零元」（见建表注释），乘汇率之后还是 0、什么也没变，
     * 却会把 {@code updatetime} 和 {@code updateuser} 刷掉一整批——
     * 于是「这个商品上次是谁什么时候改的」变成了一句废话。</p>
     *
     * <p>乘法在 SQL 里做而不是查出来逐行算：一个站几千个商品，
     * 拉回 Java 再逐条 update 是几千次往返，而且中间任何一条失败都要靠事务回滚
     * 几千行——一条 UPDATE 没有这个中间态。</p>
     *
     * @param rate       1 个旧币种换多少个新币种，来自 {@code RateService#rate}
     * @param updateUser 操作人
     * @return 改了几行
     */
    int convertPrice(@Param("rate") BigDecimal rate, @Param("updateUser") String updateUser);
}
