package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 站点图片资源。一条记录一张图。
 *
 * <p>一台机器一个站，<b>没有隔离维度</b>：这个库里的图片就是这个站的全部图片。
 * {@link #shopCode} / {@link #siteCode} 是搬迁留下的遗留列，值恒定，查询不按它们过滤。</p>
 *
 * <p>文件落在这台机器的磁盘上（{@code station.upload.dir}），由 {@code file.<主域名>}
 * 直接对外——不再是「商家自己的对象存储」那一套。</p>
 */
public class SiteImage implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String imageId;
    private String shopCode;
    private String siteCode;
    private String imageName;
    private String imageUrl;
    /**
     * 缩略图地址。
     *
     * <p>对象存储那套是靠厂商的图片处理参数现生成的（不占额外存储）。落本地磁盘之后
     * <b>没有这个能力</b>，一期恒为 null，前端回退显示原图。列留着，将来接上图片处理再填。</p>
     */
    private String thumbUrl;
    /** 桶内对象路径，删除文件时要用；完整 URL 带自定义域名时反推不回来 */
    private String objectName;
    /** 存到了哪里。一期恒为 {@code local}（本机磁盘）；换成对象存储时这一列才有多个值 */
    private String provider;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
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

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }
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
