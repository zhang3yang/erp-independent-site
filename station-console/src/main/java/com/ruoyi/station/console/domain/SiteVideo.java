package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 独立站视频资源。一条记录一个视频。
 *
 * <p>比图片多一个 {@link #duration}（时长），少一个缩略图地址——列表里用
 * {@code <video preload="metadata">} 直接显示首帧，不额外生成封面文件，所以没有封面地址要存。</p>
 *
 * <p>{@link #width}/{@link #height} 存的是视频画面尺寸，前端靠它把视频卡片和图片卡片
 * 按同一宽度对齐，视频高度按原始宽高比算，不会出现两种卡片一宽一窄的情况。</p>
 */
public class SiteVideo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String videoId;
    private String shopCode;
    private String siteCode;
    private String videoName;
    private String videoUrl;
    /** 相对 {@code station.upload.dir} 的路径，删除文件时要用 */
    private String objectName;
    private String provider;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    /** 时长（秒），前端量出来后一并提交；量不到存 null */
    private Integer duration;
    private String remark;

    /**
     * 所在文件夹，指向 {@code u_s_sitefolder.folderid}。
     *
     * <p>{@code null} = 未分类，落在树的根上。删文件夹时这一列被置回 null，
     * 文件本身不动——图片很可能正被商品页或博客引用着。</p>
     */
    private String folderId;
    private Date createtime;
    private Date updatetime;
    private String createBy;
    private String updateBy;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getVideoName() { return videoName; }
    public void setVideoName(String videoName) { this.videoName = videoName; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreatetime() { return createtime; }
    public void setCreatetime(Date createtime) { this.createtime = createtime; }
    public Date getUpdatetime() { return updatetime; }
    public void setUpdatetime(Date updatetime) { this.updatetime = updatetime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public String getFolderId()
    {
        return folderId;
    }

    public void setFolderId(String folderId)
    {
        this.folderId = folderId;
    }
}
