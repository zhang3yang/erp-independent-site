package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品组合（详情页上那排带缩略图的「组合」选项卡）。对应表 {@code u_s_sitegoods_combo}。
 *
 * <h3>为什么组合名不在这个类的表里</h3>
 * <p>名称要分语言，所以单独一张 {@code u_s_sitegoods_combo_lang}
 * （见 {@link SiteGoodsComboLang}）。这个类上的 {@link #title} <b>不是本表的列</b>，
 * 是查询时按「当前语言 → 站点默认语言」回退 join 出来的，和 {@link SiteGoods#getTitle()}
 * 是同一个套路。</p>
 *
 * <h3>combocode 是稳定标识，别用名字代替它</h3>
 * <p>名称有七份、还会被商家改；单据快照、深链 {@code ?combo=}、按组合做统计
 * 全靠 {@code combocode}。而且名字里带 {@code &}（参考站那份组合就叫
 * {@code machine&boots}），拿它当 URL 参数天然是坑。</p>
 *
 * <h3>没有库存字段，这是故意的</h3>
 * <p>站不收款不发货，ERP 又不互通（boundary 要点 1），站内库存没有数据来源，
 * 只能手工维护，很快就和实际对不上。要「售罄置灰」用 {@link #status}。</p>
 */
public class SiteGoodsCombo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 对外可选 */
    public static final String STATUS_ON = "可选";
    /** 停用：不对外返回，但历史单据里的快照还在 */
    public static final String STATUS_OFF = "停用";

    private String shopCode;
    private String siteCode;
    private String goodsCode;
    /** 组合编码，商品内唯一。见类注释 */
    private String comboCode;
    /**
     * 二维模式下这一条是第一维 / 第二维的哪个取值（取值编码，见 u_s_sitegoods.optionsjson）。
     * 平铺模式下两个都是 null；只有一个维度时 opt2 是 null。
     */
    private String opt1;
    private String opt2;
    /** 该组合的价格。⚠ 0 = 面议，不是免费（和 {@link SiteGoods#getPrice()} 同一约定） */
    private BigDecimal price;
    /** 缩略图完整 URL。各语言通用——产品实拍不分市场 */
    private String image;
    /**
     * 这个规格自己的 3D 模型 .glb；空 = 用商品主模型。
     * 设计 docs/superpowers/specs/2026-10-09-combo-model3d-design.md
     */
    private String model3d;
    /** 这个规格自己的 3D 标注 JSON；空 = logo 跟随主模型（前台自动投影）。没有 model3d 时一定为空 */
    private String model3dMarks;
    /** This variant's state list; the first state uses model3d as its base model. */
    private String model3dStates;
    /** 越小越靠前。排第一的是详情页默认选中的那个 */
    private Integer sortNum;
    /** 可选 / 停用 */
    private String status;
    private String createUser;
    private String createTime;
    private String updateUser;
    private String updateTime;

    /**
     * 名称。<b>不是本表的列</b>，按语言回退 join 出来的，见类注释。
     *
     * <p>后台编辑弹窗里不用它——那边要的是「每种语言各填一格」，走
     * {@link SiteGoodsComboLang} 的列表。</p>
     */
    private String title;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public String getComboCode() { return comboCode; }
    public void setComboCode(String comboCode) { this.comboCode = comboCode; }

    public String getModel3d() { return model3d; }
    public void setModel3d(String model3d) { this.model3d = model3d; }

    public String getModel3dMarks() { return model3dMarks; }
    public void setModel3dMarks(String model3dMarks) { this.model3dMarks = model3dMarks; }
    public String getModel3dStates() { return model3dStates; }
    public void setModel3dStates(String model3dStates) { this.model3dStates = model3dStates; }
    public String getOpt1() { return opt1; }
    public void setOpt1(String opt1) { this.opt1 = opt1; }
    public String getOpt2() { return opt2; }
    public void setOpt2(String opt2) { this.opt2 = opt2; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
