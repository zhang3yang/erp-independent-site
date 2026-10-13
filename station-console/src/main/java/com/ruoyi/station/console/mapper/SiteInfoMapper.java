package com.ruoyi.station.console.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteInfo;

/**
 * 本站档案。对应表 {@code u_s_siteinfo}，只有一行。
 *
 * <h3>为什么这里的方法都不带 shopCode / siteCode</h3>
 * <p>一个部署一个站，这个库里只有这一个站的数据。租户列还在（{@code shop_code}）、
 * 站点列也还在（{@code sitecode}），但它们<b>不是筛选维度</b>——
 * docs/独立站与ERP的边界.md §5.3 明写：新代码不得读 {@code shop_code}、
 * 不得按它过滤。</p>
 *
 * <p>整个 mapper 层都遵守这一条。写入时那两列的值由 {@code SiteContext} 从这张表上取，
 * 保证和历史数据一致（主键里有它们，值飘了就会出现同一个商品两行）。</p>
 *
 * <h3>SQL 里为什么不写库限定名</h3>
 * <p>站点后端只连一个库（{@code sdsstation}）。写 {@code from u_s_siteinfo} 而不是
 * {@code from sdsstation.u_s_siteinfo}——不写库名就物理上不可能再指到别的库去，
 * 这比写一句注释管用。ERP 那边搬迁前踩的就是这个坑：跨库限定名一旦跨了机器，
 * 当场失效，不是变慢，是报错。</p>
 */
public interface SiteInfoMapper
{
    /** 取本站档案。表里只有一行，查不到说明建站脚本没跑完 */
    SiteInfo selectSite();

    /** 建站时插入唯一那一行 */
    int insert(SiteInfo site);

    /** 改档案（名称、语言、币种、状态、域名、备注）。装修内容走下面三个 */
    int update(SiteInfo site);

    /**
     * 存装修草稿。<b>不动已发布那份</b>。
     *
     * <p>和 {@link #update} 分开：装修 JSON 是整块存整块读的大字段，
     * 每次改个站点名都把它一起 update 一遍没有必要，而且并发下会互相覆盖。</p>
     */
    int updateDraft(@Param("draftJson") String draftJson, @Param("updateUser") String updateUser);

    /**
     * 发布：草稿整份拷进 contentjson，草稿清空。
     *
     * @return 影响行数。<b>0 表示当时没有草稿</b>，不是失败——SQL 里带着
     *         {@code and draftjson is not null}，调用方据此回一句「没有待发布的改动」
     */
    int publishDraft(@Param("updateUser") String updateUser);

    /** 放弃草稿。只清草稿三列 */
    int discardDraft();
}
