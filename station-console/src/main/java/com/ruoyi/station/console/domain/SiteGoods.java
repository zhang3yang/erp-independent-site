package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 站点商品。对应表 {@code u_s_sitegoods}。
 *
 * <h3>⚠ 它的含义变了：从「上架关系」变成「商品档案」</h3>
 * <p>以前这张表只存「站点特有的那部分」——售价、起订量、分类、上架状态；商品名、规格、
 * 单位、图片都在 ERP 的 {@code sdsshop.u_m_waregoods} 里，取数时 join 回去。</p>
 *
 * <p><b>那条 join 没有了</b>（docs/独立站与ERP的边界.md §5.4）。它是一条互通，
 * 而且是跨库的——库搬到站点服务器之后，跨库限定名连语法上都不成立。所以
 * {@link #goodsName} / {@link #spec} / {@link #imgUrl} / {@link #mainImgs}
 * 从「查询时 join 出来的临时字段」变成了<b>本表真正的列</b>，由站点自己录。</p>
 *
 * <p>代价是同一个商品在 ERP 和各个站里各录一遍。这是「两边后端没有任何互通」的直接成本，
 * 一期认了。后期真要省这个人工，走的是「导出 / 导入一个商品包」这种<b>离线</b>方式，
 * 不是重新开一条实时接口。</p>
 *
 * <p>标题、卖点、详情这些要分语言，放在 {@code u_s_sitegoods_lang} 里，
 * 查询时按「当前语言 → 站点默认语言 → 本表的 goodsName」的顺序回退。</p>
 */
public class SiteGoods implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 草稿：录进来了但不对外 */
    public static final String STATUS_DRAFT = "草稿";
    /** 已上架：对外接口只返回这个状态的行 */
    public static final String STATUS_PUBLISHED = "已上架";
    /** 已下架：曾经上过，现在不对外 */
    public static final String STATUS_OFFLINE = "已下架";

    private String shopCode;
    private String siteCode;
    private String goodsCode;
    /** 站点分类（英文），列表页的筛选按钮由它去重生成 */
    private String siteCategory;
    /** 站点售价。0 表示不标价，页面显示成 Inquiry */
    private BigDecimal price;
    private BigDecimal moq;
    /** 起订量单位。⚠ 不再回退商品档案（没有档案可回退了），留空就是留空 */
    private String unit;
    private Integer featured;
    private String publishStatus;
    private Integer sortNum;
    /** 规格键值对 JSON，详情页渲染成表格。这一份是默认语言（主表），也是「有几行、什么顺序」的源头 */
    private String specsJson;
    /**
     * 组合的规格维度（最多两维，如尺码 / 款式），JSON。空 = 组合是平铺的一排。
     * 形状和取舍见 sql/station/migration/2026-09-25-goods-options.sql；
     * 写入前由 SiteGoodsService.normalizeOptions 校验整理。
     */
    private String optionsJson;
    /**
     * 请求语言那一份规格译文（来自 u_s_sitegoods_lang.specsjson，按默认键当身份）。
     * <b>瞬态字段</b>：不是主表的列，只在 selectPublicOne 里 join 出来，供 SiteOpenService 逐键回退合并。
     */
    private String langSpecsJson;
    /**
     * 站点专用图片，逗号分隔的完整 URL，第一张为主图。
     *
     * <p>留空则回退 {@link #imgUrl} / {@link #mainImgs}。是「覆盖」不是「追加」——
     * 两组混在一起的话，运营挑完图还是会看到那张内部用的图，等于没挑。</p>
     *
     * <p>图片来自本站的「文件资源」库，存在这台机器的磁盘上，由 {@code file.<主域名>} 直接对外。</p>
     */
    private String siteImgs;
    /**
     * 站点商品视频，逗号分隔的完整 URL，同样来自「文件资源」库。
     *
     * <p>和 siteImgs 不同的是这一列没有回退来源：留空就是没有视频，详情页不渲染视频区块。</p>
     */
    private String siteVideos;
    /**
     * 3D 模型（.glb）完整地址，来自「文件资源」库的模型标签。一个商品只挂一个。
     *
     * <p>空 = 没有模型，详情页不渲染展示台。只认 {@code https://} 和 {@code /} 开头的值，
     * 其余在保存时置空（{@code SiteGoodsService#normalizeMediaUrl}）：http 模型在 https
     * 页面上会被浏览器当混合内容拦掉，与其静默失败不如当成没配。</p>
     */
    private String model3d;
    /**
     * 3D 标注 JSON：模型上的 logo 贴花、屏幕、开机键（商家在管理台上点出来的）。
     *
     * <p>空 = 没有标注。坐标是 glb 场景自身的坐标。保存时经 {@code Model3dMarks#normalize}
     * 按白名单重组——这一列会原样出公网。</p>
     */
    private String model3dMarks;
    /**
     * 3D 模型状态 JSON：多个 glb 切换（如腿套「没气 / 半充 / 充满」），第一个就是主模型 model3d。
     *
     * <p>空 = 没有状态。保存时经 {@code Model3dStates#normalize} 按白名单重组——这一列会出公网。</p>
     */
    private String model3dStates;
    /**
     * 主图位置的视频（一个商品只挂一个），同样来自「文件资源」库。
     *
     * <p>和 {@link #siteVideos} 是两回事：siteVideos 是详情底部那一排「视频」，
     * 这一列是图库轮播的<b>第一格</b>——买家一进详情页就看到它。两边可以都配，
     * 也可以只配一边。</p>
     *
     * <p>空 = 没有主图视频，图库第一格仍是图。封面不入库：前台直接拿跟在后面的
     * 第一张图当 poster，省得商家换了图之后封面还指着旧那张。</p>
     *
     * <p>和 model3d 一样只认 {@code https://} 和 {@code /} 开头的值
     * （{@code SiteGoodsService#normalizeMediaUrl}）：http 视频在 https 页面上会被
     * 浏览器当混合内容拦掉，与其静默失败不如当成没配。</p>
     */
    private String mainVideo;
    private String remark;
    private String createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private String updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // ---- 商品档案。搬迁前这四个是 join sdsshop.u_m_waregoods 出来的，现在是本表的列 ----

    /** 商品名称（内部叫法）。多语言标题都没填时，列表和详情兜底显示它 */
    private String goodsName;
    /** 规格型号 */
    private String spec;
    /** 主图 URL */
    private String imgUrl;
    /** 其他附加主图，逗号分隔 */
    private String mainImgs;

    // ---- 以下不是本表的列，是按语言从 u_s_sitegoods_lang 回退取出来的 ----

    /** 站点标题：当前语言 → 站点默认语言 → goodsName */
    private String title;
    private String subtitle;
    /** 详情富文本。只有详情接口查，列表不查——这个字段可以很大 */
    private String description;
    private String keywords;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getSiteCategory() { return siteCategory; }
    public void setSiteCategory(String siteCategory) { this.siteCategory = siteCategory; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getMoq() { return moq; }
    public void setMoq(BigDecimal moq) { this.moq = moq; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getFeatured() { return featured; }
    public void setFeatured(Integer featured) { this.featured = featured; }

    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public String getSpecsJson() { return specsJson; }
    public void setSpecsJson(String specsJson) { this.specsJson = specsJson; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getLangSpecsJson() { return langSpecsJson; }
    public void setLangSpecsJson(String langSpecsJson) { this.langSpecsJson = langSpecsJson; }
    public String getSiteImgs() { return siteImgs; }
    public void setSiteImgs(String siteImgs) { this.siteImgs = siteImgs; }
    public String getSiteVideos() { return siteVideos; }
    public void setSiteVideos(String siteVideos) { this.siteVideos = siteVideos; }
    public String getModel3d() { return model3d; }
    public void setModel3d(String model3d) { this.model3d = model3d; }
    public String getModel3dMarks() { return model3dMarks; }
    public void setModel3dMarks(String model3dMarks) { this.model3dMarks = model3dMarks; }
    public String getModel3dStates() { return model3dStates; }
    public void setModel3dStates(String model3dStates) { this.model3dStates = model3dStates; }
    public String getMainVideo() { return mainVideo; }
    public void setMainVideo(String mainVideo) { this.mainVideo = mainVideo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }

    public String getMainImgs() { return mainImgs; }
    public void setMainImgs(String mainImgs) { this.mainImgs = mainImgs; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
}
