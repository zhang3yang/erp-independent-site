package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteStorage;

/**
 * 本站存储配置。对应表 {@code u_s_storage}。
 *
 * <p>一期文件落本机磁盘（{@code provider=local}），这张表只有一行、基本不动。
 * 结构和方法照抄 ERP 那套是为了将来接对象存储时不用改表也不用改接口。</p>
 */
public interface SiteStorageMapper
{
    /** 当前生效的那一套（{@code active='1'}）。一期恒为 local */
    SiteStorage selectActive();

    SiteStorage selectByProvider(@Param("provider") String provider);

    List<SiteStorage> selectAll();

    int upsert(SiteStorage storage);

    /** 把除了指定 provider 之外的全部置为不生效。换厂商时先跑它，保证只有一行 active */
    int deactivateOthers(@Param("provider") String provider);
}
