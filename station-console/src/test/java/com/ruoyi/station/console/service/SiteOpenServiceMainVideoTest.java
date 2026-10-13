package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;

/**
 * 主图视频（图库第一格）和详情底部那一排视频是两个字段，详情接口要分别下发。
 *
 * <p>最要紧的一条是最后那个用例：两边<b>互不影响</b>。它们在管理台是两个选择器、
 * 在库里是两列、在页面上是两个位置——任何一处把它们并成一份，买家看到的就是
 * 「主图视频也在底下重播一遍」或者「底下的视频被顶到了主图位置」。</p>
 */
@ExtendWith(MockitoExtension.class)
class SiteOpenServiceMainVideoTest
{
    @Mock SiteInfoService siteInfoService;
    @Mock SiteGoodsMapper goodsMapper;
    @Mock SiteGoodsComboMapper comboMapper;
    @Mock SiteInfo site;

    @InjectMocks SiteOpenService service;

    private SiteGoods goods;

    @BeforeEach
    void setUp()
    {
        when(siteInfoService.requirePublic()).thenReturn(site);
        when(site.resolveLang(any())).thenReturn("en");
        when(site.defaultLang()).thenReturn("en");

        goods = new SiteGoods();
        goods.setGoodsCode("G001");
        goods.setTitle("Recovery boots");
        when(goodsMapper.selectPublicOne(eq("G001"), eq("en"), eq("en"))).thenReturn(goods);
        when(comboMapper.selectPublicByGoods(eq("G001"), eq("en"), eq("en"))).thenReturn(List.of());
    }

    @Test
    void detailCarriesMainVideo()
    {
        goods.setMainVideo("https://file.example.com/video/2026/09/22/SVID0000000001.mp4");

        Map<String, Object> detail = service.productDetail("en", "G001");

        assertNotNull(detail);
        assertEquals("https://file.example.com/video/2026/09/22/SVID0000000001.mp4",
                detail.get("mainVideo"));
    }

    @Test
    void detailKeepsNullWhenNoMainVideo()
    {
        Map<String, Object> detail = service.productDetail("en", "G001");

        // 键要在（前台按 `p.mainVideo` 读，undefined 和 null 都当没配），值是 null 不是空串
        assertTrue(detail.containsKey("mainVideo"));
        assertNull(detail.get("mainVideo"));
    }

    @Test
    void mainVideoAndDetailVideosAreIndependent()
    {
        goods.setMainVideo("https://file.example.com/video/main.mp4");
        goods.setSiteVideos("https://file.example.com/video/a.mp4,https://file.example.com/video/b.mp4");

        Map<String, Object> detail = service.productDetail("en", "G001");

        // 主图那一格只认 mainvideo 这一列
        assertEquals("https://file.example.com/video/main.mp4", detail.get("mainVideo"));
        // 底部那一排只认 sitevideos，主图视频不混进去
        assertEquals(List.of("https://file.example.com/video/a.mp4",
                "https://file.example.com/video/b.mp4"), detail.get("videos"));
    }

    @Test
    void detailVideosSurviveWithoutMainVideo()
    {
        goods.setSiteVideos("https://file.example.com/video/a.mp4");

        Map<String, Object> detail = service.productDetail("en", "G001");

        // 反过来也一样：没配主图视频不该把底部那一排也一起吞掉
        assertNull(detail.get("mainVideo"));
        assertEquals(List.of("https://file.example.com/video/a.mp4"), detail.get("videos"));
    }
}
