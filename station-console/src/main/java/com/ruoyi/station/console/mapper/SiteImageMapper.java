package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteImage;

/**
 * 站点图片资源。对应表 {@code u_s_siteimage}。
 *
 * <p>ERP 那版每条语句都带 {@code shop_code}——那是商家之间的隔离边界。
 * 这里没有这个维度：一台机器一个站，库里的图片就是这个站的全部图片。
 * {@code shop_code} 降级成遗留列（§5.3）。</p>
 */
public interface SiteImageMapper
{
    List<SiteImage> selectList(@Param("query") SiteImage query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteImage query);

    /**
     * 某一家存储里有多少张图。换存储时用来告诉人「有多少张不再显示」。
     *
     * <p>{@code provider} 为 {@code local} 时也算上那些这一列是 NULL 的历史行——
     * 早期代码把 provider 硬编码成 local，但列是可空的，不认 NULL 会漏掉老数据。</p>
     */
    int countByProvider(@Param("provider") String provider);

    SiteImage selectByCode(@Param("imageId") String imageId);

    /** 这个地址是不是本站文件库里的图（混元生 3D 提交前校验） */
    int countByUrl(@Param("url") String url);

    int insertImage(SiteImage image);

    /** 只改显示名称和备注：地址、尺寸这些是上传时定死的，不该从界面改 */
    int updateImageInfo(SiteImage image);

    int deleteByCode(@Param("imageId") String imageId);

    /** 删文件夹时把里面的文件回落到根。⚠ 只清归属，不删文件——它们可能正被页面引用 */
    int clearFolder(@Param("folderId") String folderId);

    /** 批量挪到某个文件夹。folderId 传 null 表示挪回根 */
    int updateFolder(@Param("ids") List<String> ids, @Param("folderId") String folderId);
}
