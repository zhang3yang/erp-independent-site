package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteModel;

/**
 * 站点 3D 模型资源。对应表 {@code u_s_sitemodel}。规则同 {@link SiteVideoMapper}。
 */
public interface SiteModelMapper
{
    List<SiteModel> selectList(@Param("query") SiteModel query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteModel query);

    /** 某一家存储里有多少个模型。规则同 {@link SiteImageMapper#countByProvider} */
    int countByProvider(@Param("provider") String provider);

    SiteModel selectByCode(@Param("modelId") String modelId);

    int insertModel(SiteModel model);

    int updateModelInfo(SiteModel model);

    int deleteByCode(@Param("modelId") String modelId);

    /** 删文件夹时把里面的文件回落到根。⚠ 只清归属，不删文件——它们可能正被商品引用 */
    int clearFolder(@Param("folderId") String folderId);

    /** 批量挪到某个文件夹。folderId 传 null 表示挪回根 */
    int updateFolder(@Param("ids") List<String> ids, @Param("folderId") String folderId);
}
