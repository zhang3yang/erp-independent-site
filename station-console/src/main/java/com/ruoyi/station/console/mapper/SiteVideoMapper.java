package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteVideo;

/**
 * 站点视频资源。对应表 {@code u_s_sitevideo}。规则同 {@link SiteImageMapper}。
 */
public interface SiteVideoMapper
{
    List<SiteVideo> selectList(@Param("query") SiteVideo query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteVideo query);

    /** 某一家存储里有多少个视频。规则同 {@link SiteImageMapper#countByProvider} */
    int countByProvider(@Param("provider") String provider);

    SiteVideo selectByCode(@Param("videoId") String videoId);

    int insertVideo(SiteVideo video);

    int updateVideoInfo(SiteVideo video);

    int deleteByCode(@Param("videoId") String videoId);

    /** 删文件夹时把里面的文件回落到根。⚠ 只清归属，不删文件——它们可能正被页面引用 */
    int clearFolder(@Param("folderId") String folderId);

    /** 批量挪到某个文件夹。folderId 传 null 表示挪回根 */
    int updateFolder(@Param("ids") List<String> ids, @Param("folderId") String folderId);
}
