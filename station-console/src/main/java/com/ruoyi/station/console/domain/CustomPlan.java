package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;

/**
 * 买家 3D 定制方案。对应表 {@code u_s_custom_plan}。
 *
 * <p>买家在前台 {@code /customize/{商品号}} 贴 logo、换开机画面，保存后得到一个方案链接。
 * <b>保存后不可改</b>：每次保存都是新的一行，{@link #parentId} 记它从哪个方案改来。
 * 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md。</p>
 *
 * <p>{@link #marks} 是合并后的完整 v1 标注快照（商家的屏幕/开机键 + 买家的 logo/开机画面），
 * 展示方案时只读它，不回读商品表——商家之后挪了屏幕、换了模型，已发出去的链接不变。</p>
 *
 * <p>⚠ 这个对象只给管理台下发（带 email / note）。公开接口另拼一个 Map，见
 * {@code CustomPlanService.publicView}，别直接把它序列化给前台。</p>
 */
public class CustomPlan implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String planId;
    @JsonIgnore
    private String shopCode;
    @JsonIgnore
    private String siteCode;
    private String goodsCode;
    /** 定制的是哪个规格；空 = 主模型（2026-10-09） */
    private String comboCode;
    private String model3d;
    private String marks;
    /** 保存时商品模型状态的快照（只含有模型的状态，名字全语言）；空 = 没有状态 */
    private String model3dStates;
    private String email;
    private String note;
    private String parentId;
    @JsonIgnore
    private String ipHash;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createtime;

    /** 列表里顺带带出的商品标题（默认语言），不是表里的列 */
    private String goodsTitle;

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
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
    public String getMarks() { return marks; }
    public void setMarks(String marks) { this.marks = marks; }
    public String getModel3dStates() { return model3dStates; }
    public void setModel3dStates(String model3dStates) { this.model3dStates = model3dStates; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }
    public Date getCreatetime() { return createtime; }
    public void setCreatetime(Date createtime) { this.createtime = createtime; }
    public String getGoodsTitle() { return goodsTitle; }
    public void setGoodsTitle(String goodsTitle) { this.goodsTitle = goodsTitle; }
}
