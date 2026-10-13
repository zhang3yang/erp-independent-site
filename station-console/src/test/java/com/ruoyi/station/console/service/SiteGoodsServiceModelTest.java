package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 商品上 3D 模型地址的清洗规则。和前台 {@code pages/products/[code].vue} 里 {@code modelUrl}
 * 的判定必须一字不差：只认 https:// 和 / 开头，其余当没配。
 */
class SiteGoodsServiceModelTest
{
    @Test
    void keepsHttpsAndRootRelative()
    {
        assertEquals("https://file.example.com/model/2026/09/20/SMDL0000000001.glb",
                SiteGoodsService.normalizeMediaUrl("  https://file.example.com/model/2026/09/20/SMDL0000000001.glb  "));
        assertEquals("/files/model/a.glb", SiteGoodsService.normalizeMediaUrl("/files/model/a.glb"));
    }

    @Test
    void dropsHttpAndGarbage()
    {
        // http 模型在 https 页面上是混合内容，浏览器直接拦，前台也不认——存了等于没存
        assertNull(SiteGoodsService.normalizeMediaUrl("http://file.example.com/model/a.glb"));
        assertNull(SiteGoodsService.normalizeMediaUrl("ftp://x/a.glb"));
        assertNull(SiteGoodsService.normalizeMediaUrl("model/a.glb"));
        assertNull(SiteGoodsService.normalizeMediaUrl("javascript:alert(1)"));
    }

    @Test
    void blankBecomesNull()
    {
        assertNull(SiteGoodsService.normalizeMediaUrl(null));
        assertNull(SiteGoodsService.normalizeMediaUrl(""));
        assertNull(SiteGoodsService.normalizeMediaUrl("   "));
    }
}
