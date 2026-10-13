package com.ruoyi.station.console.mapper;

import com.ruoyi.station.console.domain.OrderWizard;

/**
 * 引导式下单的向导定义。对应表 {@code u_s_orderwizard}，一个站最多一行。
 *
 * <p>和 {@link SiteInfoMapper} 同一条规矩：方法不带 siteCode，
 * 站点编码在 SQL 里取 {@code u_s_siteinfo} 那唯一一行。</p>
 */
public interface OrderWizardMapper
{
    /** 取本站的向导。没配过返回 null */
    OrderWizard selectOne();

    /** 存（没有就插、有就改）。sitecode 取本站档案那一行 */
    int upsert(OrderWizard wizard);
}
