package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 规格维度（尺码 × 款式）的读、校验和组合名拼接。
 */
class GoodsOptionsTest
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> LANGS = List.of("zh", "en", "fr");

    private static final String TWO_DIMS = "{\"dims\":["
            + "{\"code\":\"size\",\"names\":{\"zh\":\"尺码\",\"en\":\"Size\",\"de\":\"Größe\"},"
            + " \"values\":[{\"code\":\"SM\",\"names\":{\"zh\":\"S/M\"}},{\"code\":\"L\",\"names\":{\"zh\":\"L\",\"en\":\"L\"}}]},"
            + "{\"code\":\"style\",\"names\":{\"zh\":\"款式\",\"en\":\"Style\"},"
            + " \"values\":[{\"code\":\"BLK\",\"names\":{\"zh\":\"黑色\",\"en\":\"Black\"},\"image\":\"https://f/b.png\"},"
            + "            {\"code\":\"GRY\",\"names\":{\"zh\":\"灰色\"},\"image\":\"http://insecure/g.png\"}]}"
            + "]}";

    private GoodsOptions twoDims()
    {
        return GoodsOptions.parse(TWO_DIMS, JSON).normalize(LANGS, "zh");
    }

    @Test
    void emptyMeansFlatMode()
    {
        assertNull(GoodsOptions.parse(null, JSON));
        assertNull(GoodsOptions.parse("  ", JSON));
        assertNull(GoodsOptions.parse("{\"dims\":[]}", JSON));
    }

    @Test
    void brokenJsonIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> GoodsOptions.parse("{dims:", JSON));
    }

    @Test
    void composesComboTitleWithLanguageFallback()
    {
        GoodsOptions options = twoDims();
        assertEquals("S/M · 黑色", options.comboTitle("SM", "BLK", "zh", "zh"));
        // en 有「Black」没有「S/M」的英文名 → S/M 回退默认语言
        assertEquals("S/M · Black", options.comboTitle("SM", "BLK", "en", "zh"));
        assertEquals("L · 灰色", options.comboTitle("L", "GRY", "fr", "zh"));
        // 对不上取值 → null（前台不显示这条）
        assertNull(options.comboTitle("XXL", "BLK", "zh", "zh"));
        assertNull(options.comboTitle("SM", null, "zh", "zh"));
    }

    @Test
    void normalizeDropsUnopenedLangsAndInsecureImages()
    {
        GoodsOptions options = twoDims();
        assertFalse(options.dims().get(0).names.containsKey("de"));
        assertEquals("https://f/b.png", options.dims().get(1).values.get(0).image);
        assertNull(options.dims().get(1).values.get(1).image);

        // 整理后再存再读，内容不变
        GoodsOptions again = GoodsOptions.parse(options.toJson(JSON), JSON).normalize(LANGS, "zh");
        assertEquals("S/M · 黑色", again.comboTitle("SM", "BLK", "zh", "zh"));
        assertTrue(again.hasValue(1, "GRY"));
    }

    @Test
    void rejectsMissingDefaultName()
    {
        String json = "{\"dims\":[{\"code\":\"size\",\"names\":{\"en\":\"Size\"},"
                + "\"values\":[{\"code\":\"SM\",\"names\":{\"zh\":\"S/M\"}}]}]}";
        ServiceException e = assertThrows(ServiceException.class,
                () -> GoodsOptions.parse(json, JSON).normalize(LANGS, "zh"));
        assertTrue(e.getMessage().contains("还没有起名"), e.getMessage());
    }

    @Test
    void rejectsDuplicateValueCodesAndBadCodes()
    {
        String dup = "{\"dims\":[{\"code\":\"size\",\"names\":{\"zh\":\"尺码\"},"
                + "\"values\":[{\"code\":\"L\",\"names\":{\"zh\":\"L\"}},{\"code\":\"L\",\"names\":{\"zh\":\"大\"}}]}]}";
        assertThrows(ServiceException.class, () -> GoodsOptions.parse(dup, JSON).normalize(LANGS, "zh"));

        String bad = "{\"dims\":[{\"code\":\"size\",\"names\":{\"zh\":\"尺码\"},"
                + "\"values\":[{\"code\":\"S/M\",\"names\":{\"zh\":\"S/M\"}}]}]}";
        assertThrows(ServiceException.class, () -> GoodsOptions.parse(bad, JSON).normalize(LANGS, "zh"));
    }

    @Test
    void rejectsThreeDims()
    {
        String dim = "{\"code\":\"d%d\",\"names\":{\"zh\":\"维%d\"},\"values\":[{\"code\":\"A\",\"names\":{\"zh\":\"A\"}}]}";
        String json = "{\"dims\":[" + String.format(dim, 1, 1) + "," + String.format(dim, 2, 2) + ","
                + String.format(dim, 3, 3) + "]}";
        assertThrows(ServiceException.class, () -> GoodsOptions.parse(json, JSON).normalize(LANGS, "zh"));
    }

    @Test
    void oneDimComboTitleIsJustTheValue()
    {
        String json = "{\"dims\":[{\"code\":\"size\",\"names\":{\"zh\":\"尺码\"},"
                + "\"values\":[{\"code\":\"XL\",\"names\":{\"zh\":\"XL\"}}]}]}";
        GoodsOptions options = GoodsOptions.parse(json, JSON).normalize(LANGS, "zh");
        assertEquals("XL", options.comboTitle("XL", null, "en", "zh"));
    }
}
