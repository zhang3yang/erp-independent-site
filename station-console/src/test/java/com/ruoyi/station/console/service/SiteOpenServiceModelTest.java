package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
 * 站点接口的商品详情要下发 model3d，列表卡片不带。
 */
@ExtendWith(MockitoExtension.class)
class SiteOpenServiceModelTest
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
        goods.setTitle("Valve");
        when(goodsMapper.selectPublicOne(eq("G001"), eq("en"), eq("en"))).thenReturn(goods);
        // lenient：规格模型那条用例会改成自己的组合列表
        lenient().when(comboMapper.selectPublicByGoods(eq("G001"), eq("en"), eq("en"))).thenReturn(List.of());
    }

    @Test
    void detailCarriesModel3d()
    {
        goods.setModel3d("https://file.example.com/model/2026/09/20/SMDL0000000001.glb");

        Map<String, Object> detail = service.productDetail("en", "G001");

        assertNotNull(detail);
        assertEquals("https://file.example.com/model/2026/09/20/SMDL0000000001.glb", detail.get("model3d"));
    }

    @Test
    void detailKeepsNullWhenNoModel()
    {
        Map<String, Object> detail = service.productDetail("en", "G001");

        // 键要在（前台按 `p.model3d` 读，undefined 和 null 都当没配），值是 null 不是空串
        assertTrue(detail.containsKey("model3d"));
        assertNull(detail.get("model3d"));
    }

    @Test
    void detailCarriesMarksAsObject()
    {
        goods.setModel3dMarks("{\"v\":1,\"power\":{\"p\":[0,0,0],\"n\":[0,0,1],\"r\":0.01}}");

        Map<String, Object> detail = service.productDetail("en", "G001");

        // 下发对象不是字符串：前台直接用，不再 JSON.parse
        assertTrue(detail.get("model3dMarks") instanceof Map, String.valueOf(detail.get("model3dMarks")));
        assertTrue(((Map<?, ?>) detail.get("model3dMarks")).containsKey("power"));
    }

    @Test
    void detailMarksNullWhenBrokenOrMissing()
    {
        Map<String, Object> detail = service.productDetail("en", "G001");
        assertTrue(detail.containsKey("model3dMarks"));
        assertNull(detail.get("model3dMarks"));

        goods.setModel3dMarks("{broken");
        assertNull(service.productDetail("en", "G001").get("model3dMarks"));
    }

    @Test
    void listCardDoesNotCarryModel3d()
    {
        goods.setModel3d("https://file.example.com/a.glb");

        Map<String, Object> detail = service.productDetail("en", "G001");

        // 详情 = 卡片 + 详情专属字段。卡片那一段的键集合里不该有 model3d：
        // 用 videos（卡片字段）在、model3d 在 videos 之后加进去这一点来间接验证 toCard 没动
        List<String> keys = List.copyOf(detail.keySet());
        assertTrue(keys.indexOf("videos") < keys.indexOf("specs"), keys.toString());
        assertTrue(keys.indexOf("specs") < keys.indexOf("model3d"), keys.toString());
        assertFalse(keys.indexOf("model3d") < keys.indexOf("description"), keys.toString());
    }

    // ---------- 规格各自的模型（2026-10-09）----------

    private static com.ruoyi.station.console.domain.SiteGoodsCombo combo(String code, String model, String marks)
    {
        com.ruoyi.station.console.domain.SiteGoodsCombo c = new com.ruoyi.station.console.domain.SiteGoodsCombo();
        c.setComboCode(code);
        c.setTitle(code);
        c.setModel3d(model);
        c.setModel3dMarks(marks);
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void comboCardsCarryOwnModelOnlyWhenSet()
    {
        when(comboMapper.selectPublicByGoods(eq("G001"), eq("en"), eq("en"))).thenReturn(List.of(
                combo("PLAIN", null, null),
                combo("FOLLOW", "https://x/leg-l.glb", null),
                combo("OWN", "https://x/arm.glb", "{\"v\":1,\"power\":{\"p\":[0,0,0],\"n\":[0,0,1],\"r\":0.01}}")));

        List<Map<String, Object>> combos = (List<Map<String, Object>>) service.productDetail("en", "G001").get("combos");

        assertFalse(combos.get(0).containsKey("model3d"), "没配模型的规格不带字段");
        assertEquals("https://x/leg-l.glb", combos.get(1).get("model3d"));
        assertTrue(combos.get(1).containsKey("model3dMarks"));
        assertNull(combos.get(1).get("model3dMarks"), "跟随主模型：标注是 null");
        assertNotNull(((Map<String, Object>) combos.get(2).get("model3dMarks")).get("power"));
    }
}
