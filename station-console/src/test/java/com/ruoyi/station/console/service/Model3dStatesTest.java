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
import tools.jackson.databind.ObjectMapper;

/**
 * 3D 模型状态的保存清洗与下发。设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md
 */
class Model3dStatesTest
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MAIN = "https://file.example.com/model/main.glb";
    private static final String HALF = "https://file.example.com/model/half.glb";
    private static final List<String> LANGS = List.of("zh", "en", "ja");

    private static String state(String id, String names, String model)
    {
        return "{\"id\":\"" + id + "\",\"name\":" + names + (model == null ? "" : ",\"model\":\"" + model + "\"") + "}";
    }

    private static String wrap(String... states)
    {
        return "{\"v\":1,\"states\":[" + String.join(",", states) + "]}";
    }

    private static String three()
    {
        return wrap(state("s1", "{\"zh\":\"没气\",\"en\":\"Deflated\"}", null),
                state("s2", "{\"zh\":\"半充\",\"en\":\"Half\"}", HALF),
                state("s3", "{\"zh\":\"充满\"}", null));
    }

    private static String normalize(String json)
    {
        return Model3dStates.normalize(json, MAIN, LANGS, "zh");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> states(String json) throws Exception
    {
        return (List<Map<String, Object>>) JSON.readValue(json, Map.class).get("states");
    }

    private static void rejects(String json, String hint)
    {
        ServiceException e = assertThrows(ServiceException.class, () -> normalize(json));
        assertTrue(e.getMessage().contains(hint), e.getMessage());
    }

    // ---------- normalize ----------

    @Test
    void keepsValidStatesAndEmptyModelSlots() throws Exception
    {
        List<Map<String, Object>> s = states(normalize(three()));
        assertEquals(3, s.size());
        assertEquals("s1", s.get(0).get("id"));
        assertEquals(HALF, s.get(1).get("model"));
        // 第三个还没模型：照存，以后补
        assertFalse(s.get(2).containsKey("model"));
    }

    @Test
    void firstStateModelIsDropped() throws Exception
    {
        String json = wrap(state("s1", "{\"zh\":\"没气\"}", "https://evil.example.com/x.glb"),
                state("s2", "{\"zh\":\"半充\"}", HALF));
        assertFalse(states(normalize(json)).get(0).containsKey("model"));
    }

    @Test
    void unknownFieldsAndClosedLanguagesAreDropped() throws Exception
    {
        String json = "{\"v\":1,\"x\":1,\"states\":[{\"id\":\"s1\",\"evil\":\"<script>\",\"name\":{\"zh\":\"没气\",\"fr\":\"Dégonflé\"}}]}";
        String out = normalize(json);
        assertFalse(out.contains("evil"));
        assertFalse(out.contains("fr"));
        assertFalse(out.contains("\"x\""));
    }

    @Test
    void emptyOrNoMainModelBecomesNull()
    {
        assertNull(normalize(null));
        assertNull(normalize("  "));
        assertNull(normalize("{\"v\":1,\"states\":[]}"));
        // 没主模型：状态无所依附
        assertNull(Model3dStates.normalize(three(), null, LANGS, "zh"));
    }

    @Test
    void rejectsTooManyStates()
    {
        String[] six = new String[6];
        for (int i = 0; i < 6; i++)
        {
            six[i] = state("s" + (i + 1), "{\"zh\":\"状态" + i + "\"}", null);
        }
        rejects(wrap(six), "最多 5 个");
    }

    @Test
    void rejectsBadOrDuplicateId()
    {
        rejects(wrap(state("x1", "{\"zh\":\"a\"}", null)), "编号格式");
        rejects(wrap(state("s1", "{\"zh\":\"a\"}", null), state("s1", "{\"zh\":\"b\"}", HALF)), "编号重复");
    }

    @Test
    void rejectsMissingDefaultNameAndLongName()
    {
        rejects(wrap(state("s1", "{\"en\":\"Deflated\"}", null)), "缺少默认语言（zh）");
        rejects(wrap(state("s1", "{\"zh\":\"   \"}", null)), "缺少默认语言");
        rejects(wrap(state("s1", "{\"zh\":\"" + "长".repeat(31) + "\"}", null)), "超过 30 个字");
    }

    @Test
    void rejectsBadModelUrl()
    {
        rejects(wrap(state("s1", "{\"zh\":\"a\"}", null), state("s2", "{\"zh\":\"b\"}", "http://x.com/a.glb")), "第 2 个模型状态的模型地址");
        rejects(wrap(state("s1", "{\"zh\":\"a\"}", null), state("s2", "{\"zh\":\"b\"}", "https://x.com/a.gltf")), "只支持 .glb");
        // 查询串不影响扩展名判断
        assertNotNull(normalize(wrap(state("s1", "{\"zh\":\"a\"}", null), state("s2", "{\"zh\":\"b\"}", "https://x.com/a.GLB?v=2"))));
    }

    @Test
    void rejectsBadJsonAndVersion()
    {
        rejects("{oops", "不是合法的 JSON");
        rejects("{\"v\":2,\"states\":[]}", "版本不支持");
        rejects("{\"v\":1,\"states\":{}}", "列表格式不对");
    }

    // ---------- toPublic / toSnapshot ----------

    @Test
    void publicKeepsOnlyStatesWithModelAndLocalizes()
    {
        List<Map<String, Object>> out = Model3dStates.toPublic(normalize(three()), "en", "zh");
        assertEquals(2, out.size());
        assertEquals("Deflated", out.get(0).get("name"));
        assertNull(out.get(0).get("model"));
        assertEquals("Half", out.get(1).get("name"));
        assertEquals(HALF, out.get(1).get("model"));
    }

    @Test
    void publicFallsBackToDefaultLanguage()
    {
        List<Map<String, Object>> out = Model3dStates.toPublic(normalize(three()), "ja", "zh");
        assertEquals("没气", out.get(0).get("name"));
    }

    @Test
    void publicIsNullWhenFewerThanTwoOrBroken()
    {
        String onlyMain = normalize(wrap(state("s1", "{\"zh\":\"没气\"}", null), state("s2", "{\"zh\":\"半充\"}", null)));
        assertNull(Model3dStates.toPublic(onlyMain, "zh", "zh"));
        assertNull(Model3dStates.toPublic("{broken", "zh", "zh"));
        assertNull(Model3dStates.toPublic(null, "zh", "zh"));
    }

    @Test
    void snapshotKeepsAllLanguageNames() throws Exception
    {
        String snap = Model3dStates.toSnapshot(normalize(three()));
        List<Map<String, Object>> s = states(snap);
        assertEquals(2, s.size());
        assertEquals(Map.of("zh", "没气", "en", "Deflated"), s.get(0).get("name"));
        // 快照可以再走 toPublic（方案页按语言换名）
        assertEquals("Half", Model3dStates.toPublic(snap, "en", "zh").get(1).get("name"));
        assertNull(Model3dStates.toSnapshot(null));
    }
}
