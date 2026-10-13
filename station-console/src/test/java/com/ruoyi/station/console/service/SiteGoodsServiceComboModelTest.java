package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.station.console.domain.SiteGoodsCombo;

/**
 * 规格（组合）各自的 3D 模型 + 标注的保存规则。设计：docs/superpowers/specs/2026-10-09-combo-model3d-design.md
 */
class SiteGoodsServiceComboModelTest
{
    private static final String MARKS = "{\"v\":1,\"power\":{\"p\":[0,0,0],\"n\":[0,0,1],\"r\":0.01}}";

    private static SiteGoodsCombo apply(Object model, Object marks)
    {
        SiteGoodsCombo combo = new SiteGoodsCombo();
        combo.setComboCode("C01");
        Map<String, Object> item = new HashMap<>();
        item.put("model3d", model);
        item.put("model3dMarks", marks);
        SiteGoodsService.applyComboModel(combo, item);
        return combo;
    }

    @Test
    void keepsModelAndOwnMarks()
    {
        SiteGoodsCombo c = apply("https://file.example.com/model/arm.glb", MARKS);
        assertEquals("https://file.example.com/model/arm.glb", c.getModel3d());
        assertNotNull(c.getModel3dMarks());
    }

    @Test
    void emptyMarksMeansFollowMain()
    {
        SiteGoodsCombo c = apply("/files/model/arm.glb", null);
        assertEquals("/files/model/arm.glb", c.getModel3d());
        assertNull(c.getModel3dMarks());
    }

    @Test
    void noModelClearsMarks()
    {
        SiteGoodsCombo c = apply("  ", MARKS);
        assertNull(c.getModel3d());
        assertNull(c.getModel3dMarks(), "标注离开模型没有意义");
    }

    @Test
    void rejectsBadModelWithComboCode()
    {
        ServiceException e = assertThrows(ServiceException.class, () -> apply("http://x/a.glb", null));
        assertTrue(e.getMessage().contains("组合「C01」"), e.getMessage());
        e = assertThrows(ServiceException.class, () -> apply("https://x/a.gltf", null));
        assertTrue(e.getMessage().contains("只支持 .glb"), e.getMessage());
    }

    @Test
    void rejectsBadMarksWithComboCode()
    {
        ServiceException e = assertThrows(ServiceException.class, () -> apply("https://x/a.glb", "{oops"));
        assertTrue(e.getMessage().startsWith("组合「C01」："), e.getMessage());
    }
}
