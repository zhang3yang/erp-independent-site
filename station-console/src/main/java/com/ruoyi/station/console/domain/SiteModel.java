package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 独立站 3D 模型资源。一条记录一个 .glb 文件。
 *
 * <p>和 {@link SiteVideo} 同构，少了宽高时长——模型没有这三个量。也没有缩略图：
 * 列表里用一个立方体图标 + 「预览」按钮，预览时才起 three.js 把模型真的画出来。</p>
 *
 * <p>商品挂模型存的是 {@link #modelUrl}（见 {@code u_s_sitegoods.model3d}），不是主键，
 * 和图片视频的做法一致：前台只认 URL，文件库删了记录商品也不会跟着裂。</p>
 */
public class SiteModel implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String modelId;
    private String shopCode;
    private String siteCode;
    private String modelName;
    private String modelUrl;
    /** 桶内对象路径 {@code model/YYYY/MM/DD/<modelid>.glb}，删除文件时要用 */
    private String objectName;
    private String provider;
    private Long fileSize;
    private String mimeType;
    private String remark;

    /** 所在文件夹，规则同 {@link SiteVideo#getFolderId()}：{@code null} = 未分类 */
    private String folderId;
    private Date createtime;
    private Date updatetime;
    private String createBy;
    private String updateBy;

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelUrl() { return modelUrl; }
    public void setModelUrl(String modelUrl) { this.modelUrl = modelUrl; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public Date getCreatetime() { return createtime; }
    public void setCreatetime(Date createtime) { this.createtime = createtime; }
    public Date getUpdatetime() { return updatetime; }
    public void setUpdatetime(Date updatetime) { this.updatetime = updatetime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
}
