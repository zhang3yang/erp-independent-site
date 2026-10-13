package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 3D 标注的保存清洗。范围和 station-shared/model3d/marks.js 的 parseMarks 是同一套。
 */
class Model3dMarksTest
{
    private static final String IMG = "https://file.example.com/image/2026/09/25/SIMG0000000001.png";

    private static String logo(String extra)
    {
        return "{\"id\":\"a1\",\"src\":\"" + IMG + "\",\"p\":[0,0.1,0.03],\"n\":[0,0,2],\"size\":0.03" + extra + "}";
    }

    private static String full()
    {
        return "{\"v\":1,\"model\":\"https://file.example.com/model/a.glb\","
                + "\"logos\":[" + logo(",\"rot\":15,\"opacity\":0.8") + "],"
                + "\"screen\":{\"p\":[0,0.1,0.035],\"n\":[0,0,1],\"w\":0.04,\"h\":0.06,\"rot\":0,"
                + "\"bootSrc\":\"" + IMG + "\",\"bootBg\":\"#1A2B3C\",\"bootMs\":1500,\"runSrc\":\"/files/ui.png\"},"
                + "\"power\":{\"p\":[0,0.02,0.035],\"n\":[0,0,1],\"r\":0.006}}";
    }

    @Test
    void keepsValidMarksAndNormalizes()
    {
        Map<String, Object> m = Model3dMarks.toObject(Model3dMarks.normalize(full()));
        assertNotNull(m);
        assertEquals(1, ((Number) m.get("v")).intValue());
        assertEquals("https://file.example.com/model/a.glb", m.get("model"));

        Map<?, ?> logo = (Map<?, ?>) ((List<?>) m.get("logos")).get(0);
        assertEquals(IMG, logo.get("src"));
        // 法线存单位化之后的值
        assertEquals(List.of(0.0, 0.0, 1.0), logo.get("n"));
        assertEquals(15.0, ((Number) logo.get("rot")).doubleValue());

        Map<?, ?> screen = (Map<?, ?>) m.get("screen");
        assertEquals("#1a2b3c", screen.get("bootBg"));
        assertEquals(1500, ((Number) screen.get("bootMs")).intValue());
        assertEquals("/files/ui.png", screen.get("runSrc"));
        assertNotNull(m.get("power"));
    }

    @Test
    void fillsDefaults()
    {
        String json = "{\"v\":1,\"screen\":{\"p\":[0,0,0],\"n\":[0,1,0],\"w\":1,\"h\":1,\"runSrc\":\"" + IMG + "\"}}";
        Map<?, ?> screen = (Map<?, ?>) Model3dMarks.toObject(json).get("screen");
        assertEquals("#000000", screen.get("bootBg"));
        assertEquals(1500, ((Number) screen.get("bootMs")).intValue());
        assertEquals(0.0, ((Number) screen.get("rot")).doubleValue());
    }

    @Test
    void dropsUnknownFields()
    {
        String json = "{\"v\":1,\"evil\":\"<script>\",\"logos\":[" + logo(",\"onload\":\"x\"") + "]}";
        String out = Model3dMarks.normalize(json);
        assertFalse(out.contains("evil"), out);
        assertFalse(out.contains("onload"), out);
    }

    @Test
    void blankOrEmptyBecomesNull()
    {
        assertNull(Model3dMarks.normalize(null));
        assertNull(Model3dMarks.normalize("   "));
        assertNull(Model3dMarks.normalize("null"));
        // 三样都没有 = 没有标注
        assertNull(Model3dMarks.normalize("{\"v\":1,\"model\":\"https://x/a.glb\",\"logos\":[]}"));
    }

    @Test
    void rejectsBadInput()
    {
        String[] bad = {
            "{not json",
            "{\"v\":2,\"logos\":[" + logo("") + "]}",
            "{\"logos\":[" + logo("") + "]}",
            // http 图片：https 页面上是混合内容
            "{\"v\":1,\"logos\":[" + logo("").replace("https://", "http://") + "]}",
            "{\"v\":1,\"logos\":[" + logo("").replace(IMG, "javascript:alert(1)") + "]}",
            "{\"v\":1,\"logos\":[" + logo(",\"opacity\":0") + "]}",
            "{\"v\":1,\"logos\":[" + logo(",\"rot\":181") + "]}",
            "{\"v\":1,\"logos\":[" + logo("").replace("\"size\":0.03", "\"size\":-1") + "]}",
            "{\"v\":1,\"logos\":[" + logo("").replace("[0,0,2]", "[0,0,0]") + "]}",
            "{\"v\":1,\"logos\":[" + logo("").replace("[0,0.1,0.03]", "[0,1e9,0]") + "]}",
            "{\"v\":1,\"logos\":[" + logo("").replace("[0,0.1,0.03]", "[0,\"1\",0]") + "]}",
            "{\"v\":1,\"screen\":{\"p\":[0,0,0],\"n\":[0,1,0],\"w\":1,\"h\":1}}",
            "{\"v\":1,\"screen\":{\"p\":[0,0,0],\"n\":[0,1,0],\"w\":1,\"h\":1,\"runSrc\":\"" + IMG + "\",\"bootBg\":\"red\"}}",
            "{\"v\":1,\"screen\":{\"p\":[0,0,0],\"n\":[0,1,0],\"w\":1,\"h\":1,\"runSrc\":\"" + IMG + "\",\"bootMs\":10001}}",
            "{\"v\":1,\"power\":{\"p\":[0,0,0],\"n\":[0,1,0]}}",
        };
        for (String json : bad)
        {
            assertThrows(ServiceException.class, () -> Model3dMarks.normalize(json), json);
        }
    }

    @Test
    void rejectsTooManyLogosAndTooLarge()
    {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"logos\":[");
        for (int i = 0; i < 11; i++)
        {
            sb.append(i == 0 ? "" : ",").append(logo(""));
        }
        String eleven = sb.append("]}").toString();
        assertThrows(ServiceException.class, () -> Model3dMarks.normalize(eleven));

        String huge = "{\"v\":1,\"pad\":\"" + "x".repeat(21 * 1024) + "\"}";
        assertThrows(ServiceException.class, () -> Model3dMarks.normalize(huge));
    }

    @Test
    void toObjectSwallowsBadData()
    {
        // 有人手改过库：详情接口不能因此 500
        assertNull(Model3dMarks.toObject("{broken"));
        assertNull(Model3dMarks.toObject(null));
        assertTrue(Model3dMarks.toObject(full()).containsKey("screen"));
    }
}
