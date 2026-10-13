package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 规格按语言合并 {@code SiteOpenService.buildSpecs}：默认定行清单、逐键回退、键值都翻、保序。
 * 用反射直接调私有方法，不起 Spring 上下文。
 */
class SiteOpenServiceSpecI18nTest
{
    @SuppressWarnings("unchecked")
    private Map<String, Object> build(String def, String lang) throws Exception
    {
        SiteOpenService svc = new SiteOpenService();
        var m = SiteOpenService.class.getDeclaredMethod("buildSpecs", String.class, String.class, String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(svc, def, lang, "test");
    }

    @Test
    void translatesKeyAndValue() throws Exception
    {
        String def = "{\"Material\":\"Platinum cured silicone\",\"Structure\":\"4 cavities, single row\"}";
        String pt = "{\"Material\":{\"k\":\"Material\",\"v\":\"silicone curado com platina\"},"
                + "\"Structure\":{\"k\":\"Estrutura\",\"v\":\"4 cavidades, fila única\"}}";
        Map<String, Object> out = build(def, pt);
        assertEquals("silicone curado com platina", out.get("Material"));
        assertEquals("4 cavidades, fila única", out.get("Estrutura"));
        // 键被翻成 Estrutura 后，原键 Structure 不应再出现
        assertTrue(!out.containsKey("Structure"), out.toString());
    }

    @Test
    void fallsBackPerKeyWhenTranslationMissing() throws Exception
    {
        String def = "{\"Material\":\"Silicone\",\"Hardness\":\"40-70 Shore A\"}";
        // 只译了 Material，Hardness 没译
        String pt = "{\"Material\":{\"k\":\"Material\",\"v\":\"silicone\"}}";
        Map<String, Object> out = build(def, pt);
        assertEquals("silicone", out.get("Material"));
        assertEquals("40-70 Shore A", out.get("Hardness")); // 回退默认键+默认值
    }

    @Test
    void partialOverrideKeyOnlyOrValueOnly() throws Exception
    {
        String def = "{\"Hardness\":\"40-70 Shore A\"}";
        // 只译键、值留空 -> 键用译文，值回退默认（Shore A 该留原文）
        String pt = "{\"Hardness\":{\"k\":\"Dureza\",\"v\":\"\"}}";
        Map<String, Object> out = build(def, pt);
        assertEquals("40-70 Shore A", out.get("Dureza"));
    }

    @Test
    void noLangReturnsDefaultAndKeepsOrder() throws Exception
    {
        String def = "{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\"}";
        Map<String, Object> out = build(def, null);
        assertEquals(List.of("A", "B", "C"), new ArrayList<>(out.keySet()));
        assertEquals("2", out.get("B"));
    }

    @Test
    void badLangJsonFallsBackToDefault() throws Exception
    {
        String def = "{\"A\":\"1\"}";
        Map<String, Object> out = build(def, "{not json");
        assertEquals("1", out.get("A"));
    }
}
