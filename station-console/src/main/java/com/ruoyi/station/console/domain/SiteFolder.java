package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文件资源的文件夹。一条记录一个文件夹。
 *
 * <p>自引用的邻接表：{@link #parentId} 指向父行，{@code null} 表示顶层。
 * 没做物化路径也没做闭包表——一个站撑死几十个文件夹，整棵读出来在内存里拼
 * （{@code SiteFolderService.tree}）比维护冗余路径省事，改名和移动也不用批量重写子孙。</p>
 *
 * <p>⚠ <b>图片和视频是两棵独立的树</b>，靠 {@link #mediaType} 分开。页面上本来就是
 * 「图片 / 视频」两个页签，共用一棵的话切到视频页签会看到一堆只装着图片的空文件夹。</p>
 *
 * <p>{@link #shopCode} / {@link #siteCode} 是搬迁留下的遗留列，一台机器一个站，
 * 值恒定，查询不按它们过滤。</p>
 */
public class SiteFolder implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String folderId;
    private String shopCode;
    private String siteCode;
    /** 这棵树装什么：{@code image} / {@code video}。⚠ 每条查询都要带上它 */
    private String mediaType;
    /** 父文件夹。{@code null} = 顶层 */
    private String parentId;
    private String folderName;
    /** 同级排序，小的在前；相同时按名字 */
    private Integer sort;
    private Date createtime;
    private Date updatetime;
    private String createBy;
    private String updateBy;

    /**
     * 子文件夹。只在 {@code tree()} 拼树时填，不来自数据库。
     *
     * <p>初始化成空列表而不是 null：前端 el-tree 拿到 {@code children: null} 会把这个节点
     * 当成「还没加载」而画出展开箭头，点下去又什么都没有。</p>
     */
    private List<SiteFolder> children = new ArrayList<>();

    /**
     * 这个文件夹里直接装了多少个文件（不含子文件夹里的）。
     *
     * <p>只在列表接口填，用来在树上显示计数、以及删除前判断空不空。</p>
     */
    private Integer fileCount;

    public String getFolderId()
    {
        return folderId;
    }

    public void setFolderId(String folderId)
    {
        this.folderId = folderId;
    }

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getSiteCode()
    {
        return siteCode;
    }

    public void setSiteCode(String siteCode)
    {
        this.siteCode = siteCode;
    }

    public String getMediaType()
    {
        return mediaType;
    }

    public void setMediaType(String mediaType)
    {
        this.mediaType = mediaType;
    }

    public String getParentId()
    {
        return parentId;
    }

    public void setParentId(String parentId)
    {
        this.parentId = parentId;
    }

    public String getFolderName()
    {
        return folderName;
    }

    public void setFolderName(String folderName)
    {
        this.folderName = folderName;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    public Date getCreatetime()
    {
        return createtime;
    }

    public void setCreatetime(Date createtime)
    {
        this.createtime = createtime;
    }

    public Date getUpdatetime()
    {
        return updatetime;
    }

    public void setUpdatetime(Date updatetime)
    {
        this.updatetime = updatetime;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public List<SiteFolder> getChildren()
    {
        return children;
    }

    public void setChildren(List<SiteFolder> children)
    {
        this.children = children;
    }

    public Integer getFileCount()
    {
        return fileCount;
    }

    public void setFileCount(Integer fileCount)
    {
        this.fileCount = fileCount;
    }
}
