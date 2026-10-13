package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteFolder;

/**
 * 文件资源的文件夹。
 *
 * <p>⚠ 每条查询都带 {@code mediaType}：图片和视频是两棵独立的树，
 * 漏掉它就会把另一棵树的文件夹一起查出来。</p>
 */
public interface SiteFolderMapper
{
    /** 某一类的全部文件夹，按 (parentid, sort, foldername) 排。整棵读出来在内存里拼层级 */
    List<SiteFolder> selectByType(@Param("mediaType") String mediaType);

    SiteFolder selectById(@Param("folderId") String folderId);

    /** 同一个父下有没有重名的。改名时要排掉自己，所以带 excludeId */
    int countByName(@Param("mediaType") String mediaType,
                    @Param("parentId") String parentId,
                    @Param("folderName") String folderName,
                    @Param("excludeId") String excludeId);

    /** 直接子文件夹的个数。删除前判空用 */
    int countChildren(@Param("folderId") String folderId);

    int insert(SiteFolder folder);

    int update(SiteFolder folder);

    /**
     * 挪到顶层（parentid 置 null）。
     *
     * <p>⚠ 单独一条而不复用 update：那个动态 SQL 里 parentid 包在     int deleteById(@Param("folderId") String folderId);lt;if test="parentId != null"    int deleteById(@Param("folderId") String folderId);gt;
     * 里，传 null 会被整条跳过——表现是「点了没反应」，不报错。</p>
     */
    int moveToRoot(@Param("folderId") String folderId, @Param("updateBy") String updateBy);

    int deleteById(@Param("folderId") String folderId);

    /**
     * 每个文件夹里直接装了多少文件。
     *
     * <p>返回的 {@link SiteFolder} 只有 {@code folderId} 和 {@code fileCount} 两个字段有值，
     * 是个投影不是完整实体。一次查完再在内存里合并，比每个文件夹查一次少 N 次往返。</p>
     */
    List<SiteFolder> countFiles(@Param("mediaType") String mediaType);
}
