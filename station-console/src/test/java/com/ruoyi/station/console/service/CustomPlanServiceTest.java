package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.CustomPlan;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.CustomPlanMapper;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;
import com.ruoyi.station.console.domain.SiteGoodsCombo;

/**
 * 买家 3D 定制方案：合并规则、图片引用、验图、公开视图不漏邮箱。
 * 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md §10。
 */
@ExtendWith(MockitoExtension.class)
class CustomPlanServiceTest
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = "https://file.test/model/a.glb";
    private static final String MERCHANT_MARKS = "{\"v\":1,\"model\":\"" + MODEL + "\","
            + "\"logos\":[{\"id\":\"m1\",\"src\":\"https://file.test/image/merchant.png\",\"p\":[0,0,0],\"n\":[0,0,1],\"size\":0.1}],"
            + "\"screen\":{\"p\":[0,0.1,0.03],\"n\":[0,0,1],\"w\":0.05,\"h\":0.06,\"rot\":0,"
            + "\"bootSrc\":\"https://file.test/image/boot.png\",\"bootBg\":\"#000000\",\"bootMs\":1500,"
            + "\"runSrc\":\"https://file.test/image/run.png\"},"
            + "\"power\":{\"p\":[0,0.02,0.03],\"n\":[0,0,1],\"r\":0.006}}";

    @Mock CustomPlanMapper planMapper;
    @Mock SiteGoodsMapper goodsMapper;
    @Mock SiteInfoService siteInfoService;
    @Mock SiteFileService fileService;
    @Mock SiteGoodsComboMapper comboMapper;
    @Mock SiteOpenService openService;
    @Spy StationProperties properties = new StationProperties();

    @InjectMocks CustomPlanService service;

    private SiteGoods goods;

    @BeforeEach
    void setUp()
    {
        SiteInfo site = new SiteInfo();
        site.setShopCode("SHOP");
        site.setSiteCode("SITE");
        lenient().when(siteInfoService.requirePublic()).thenReturn(site);

        goods = new SiteGoods();
        goods.setGoodsCode("G1");
        goods.setTitle("Air pump");
        goods.setModel3d(MODEL);
        goods.setModel3dMarks(MERCHANT_MARKS);
        lenient().when(goodsMapper.selectPublicOne(eq("G1"), any(), any())).thenReturn(goods);
        lenient().when(fileService.storeCustomImage(any(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "https://file.test/custom/" + inv.getArgument(1) + "." + inv.getArgument(2));
    }

    // ---------- 工具 ----------

    private static byte[] png(int w, int h)
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
            return out.toByteArray();
        }
        catch (java.io.IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] jpg(int w, int h)
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", out);
            return out.toByteArray();
        }
        catch (java.io.IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static MultipartFile file(byte[] bytes)
    {
        return new MockMultipartFile("files", "a.png", "image/png", bytes);
    }

    private static String logo(String src)
    {
        return "{\"id\":\"a1\",\"src\":\"" + src + "\",\"p\":[0.01,0.1,0.03],\"n\":[0,0,2],\"size\":0.03,\"rot\":10,\"opacity\":1}";
    }

    private static String data(String logos, String extra)
    {
        return "{\"goodsCode\":\"G1\",\"agree\":true,\"logos\":[" + logos + "]" + (extra == null ? "" : "," + extra) + "}";
    }

    @SuppressWarnings("unchecked")
    private CustomPlan savedPlan()
    {
        ArgumentCaptor<CustomPlan> captor = ArgumentCaptor.forClass(CustomPlan.class);
        verify(planMapper).insertPlan(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> marksOf(CustomPlan plan)
    {
        return JSON.readValue(plan.getMarks(), Map.class);
    }

    private static void assertKey(String key, Runnable action)
    {
        ServiceException e = assertThrows(ServiceException.class, action::run);
        assertEquals(key, e.getKey());
    }

    // ---------- 合法提交 ----------

    @Test
    @SuppressWarnings("unchecked")
    void savesMergedSnapshotWithUploadedUrls() throws Exception
    {
        String id = service.save(data(logo("upload:0"), "\"boot\":{\"src\":\"upload:1\",\"bg\":\"#FF0000\"},"
                + "\"email\":\" Buyer@X.com \",\"note\":\"hi\\u0007 there\""),
                List.of(file(png(64, 32)), file(jpg(40, 40))), "1.2.3.4");

        assertTrue(id.matches("^[A-Za-z0-9]{16}$"));
        CustomPlan plan = savedPlan();
        assertEquals(id, plan.getPlanId());
        assertEquals("G1", plan.getGoodsCode());
        assertEquals(MODEL, plan.getModel3d());
        assertEquals("buyer@x.com", plan.getEmail());
        assertEquals("hi there", plan.getNote());
        assertEquals(64, plan.getIpHash().length());
        assertFalse(plan.getIpHash().contains("1.2.3.4"));

        Map<String, Object> marks = marksOf(plan);
        List<Map<String, Object>> logos = (List<Map<String, Object>>) marks.get("logos");
        assertEquals(1, logos.size(), "商家自己的 logo 不带进方案");
        assertEquals("https://file.test/custom/" + id + "-0.png", logos.get(0).get("src"));
        Map<String, Object> screen = (Map<String, Object>) marks.get("screen");
        assertEquals("https://file.test/custom/" + id + "-1.jpg", screen.get("bootSrc"));
        assertEquals("#ff0000", screen.get("bootBg"));
        assertEquals("https://file.test/image/run.png", screen.get("runSrc"));
        assertNotNull(marks.get("power"));
        verify(fileService).storeCustomImage(any(), eq(id + "-0"), eq("png"), eq("image/png"));
        verify(fileService).storeCustomImage(any(), eq(id + "-1"), eq("jpg"), eq("image/jpeg"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientScreenAndPowerAreIgnored() throws Exception
    {
        // 客户端塞了自己的屏幕坐标、工作界面、开机键——一律按商家的
        service.save(data(logo("upload:0"), "\"screen\":{\"p\":[9,9,9],\"runSrc\":\"https://evil.test/x.png\"},"
                + "\"power\":{\"p\":[9,9,9],\"n\":[0,0,1],\"r\":1}"), List.of(file(png(8, 8))), "ip");

        Map<String, Object> marks = marksOf(savedPlan());
        Map<String, Object> screen = (Map<String, Object>) marks.get("screen");
        assertEquals(List.of(0.0, 0.1, 0.03), screen.get("p"));
        assertEquals("https://file.test/image/run.png", screen.get("runSrc"));
        assertEquals("https://file.test/image/boot.png", screen.get("bootSrc"), "没换开机图就用商家的");
        assertEquals(0.006, ((Map<String, Object>) marks.get("power")).get("r"));
    }

    @Test
    void bootColorOnlyIsEnough() throws Exception
    {
        service.save("{\"goodsCode\":\"G1\",\"agree\":true,\"boot\":{\"bg\":\"#123456\"}}", List.of(), "ip");
        verify(planMapper).insertPlan(any());
        verify(fileService, never()).storeCustomImage(any(), anyString(), anyString(), anyString());
    }

    // ---------- 图片引用 ----------

    @Test
    void rejectsUnusedUpload() throws Exception
    {
        assertKey("custom.badData", () -> service.save(data(logo("upload:0"), null),
                List.of(file(png(8, 8)), file(png(8, 8))), "ip"));
        verifyNoInteractions(fileService);
    }

    @Test
    void rejectsOutOfRangeUpload() throws Exception
    {
        assertKey("custom.badData", () -> service.save(data(logo("upload:1"), null), List.of(file(png(8, 8))), "ip"));
        verifyNoInteractions(fileService);
    }

    @Test
    void rejectsExternalAndSiteUrls()
    {
        assertKey("custom.badData", () -> service.save(data(logo("https://evil.test/a.png"), null), List.of(), "ip"));
        assertKey("custom.badData", () -> service.save(data(logo("/image/a.png"), null), List.of(), "ip"));
        assertKey("custom.badData", () -> service.save(
                "{\"goodsCode\":\"G1\",\"agree\":true,\"boot\":{\"src\":\"https://evil.test/a.png\"}}", List.of(), "ip"));
        verifyNoInteractions(fileService);
    }

    @Test
    void rejectsTooManyLogos() throws Exception
    {
        StringBuilder logos = new StringBuilder();
        List<MultipartFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++)
        {
            logos.append(i == 0 ? "" : ",").append(logo("upload:" + i));
            files.add(file(png(8, 8)));
        }
        assertKey("custom.badData", () -> service.save(data(logos.toString(), null), files, "ip"));
    }

    @Test
    void rejectsBadGeometryBeforeWritingStorage() throws Exception
    {
        String bad = "{\"id\":\"a1\",\"src\":\"upload:0\",\"p\":[0,0,0],\"n\":[0,0,0],\"size\":0.03}";
        assertKey("custom.badData", () -> service.save(data(bad, null), List.of(file(png(8, 8))), "ip"));
        verifyNoInteractions(fileService);
        verify(planMapper, never()).insertPlan(any());
    }

    // ---------- 验图 ----------

    @Test
    void rejectsSvgRenamedAsPng()
    {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        assertKey("custom.badFile", () -> CustomPlanService.readImage(file(svg)));
    }

    @Test
    void rejectsGif() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "gif", out);
        assertKey("custom.badFile", () -> CustomPlanService.readImage(file(out.toByteArray())));
    }

    @Test
    void rejectsOversizeFileAndEdge() throws Exception
    {
        byte[] big = new byte[(int) CustomPlanService.MAX_FILE_BYTES + 1];
        big[0] = (byte) 0x89;
        big[1] = 'P';
        big[2] = 'N';
        big[3] = 'G';
        assertKey("custom.badFile", () -> CustomPlanService.readImage(file(big)));
        assertKey("custom.badFile", () -> CustomPlanService.readImage(file(png(4097, 1))));
    }

    @Test
    void rejectsTruncatedPng() throws Exception
    {
        byte[] whole = png(16, 16);
        byte[] cut = java.util.Arrays.copyOf(whole, 20);
        assertKey("custom.badFile", () -> CustomPlanService.readImage(file(cut)));
    }

    @Test
    void acceptsPngAndJpgByMagicNotExtension() throws Exception
    {
        MockMultipartFile lying = new MockMultipartFile("files", "logo.svg", "image/svg+xml", jpg(10, 10));
        assertEquals("jpg", CustomPlanService.readImage(lying).ext);
        assertEquals("png", CustomPlanService.readImage(file(png(10, 10))).ext);
    }

    // ---------- 其他字段 ----------

    @Test
    void requiresAgreement()
    {
        assertKey("custom.agreeRequired",
                () -> service.save("{\"goodsCode\":\"G1\",\"logos\":[]}", List.of(), "ip"));
    }

    @Test
    void rejectsEmptyPlan()
    {
        assertKey("custom.empty", () -> service.save("{\"goodsCode\":\"G1\",\"agree\":true}", List.of(), "ip"));
    }

    @Test
    void rejectsBadEmailAndFrom() throws Exception
    {
        assertKey("order.badEmail", () -> service.save(data(logo("upload:0"), "\"email\":\"nope\""),
                List.of(file(png(8, 8))), "ip"));
        assertKey("custom.badData", () -> service.save(data(logo("upload:0"), "\"from\":\"../../x\""),
                List.of(file(png(8, 8))), "ip"));
    }

    @Test
    void rejectsLongNote() throws Exception
    {
        String note = "x".repeat(CustomPlanService.MAX_NOTE + 1);
        assertKey("custom.noteTooLong", () -> service.save(data(logo("upload:0"), "\"note\":\"" + note + "\""),
                List.of(file(png(8, 8))), "ip"));
    }

    @Test
    void goodsWithoutModelIsUnavailable()
    {
        goods.setModel3d(null);
        assertKey("custom.unavailable", () -> service.save(data(logo("upload:0"), null), List.of(), "ip"));
        assertNull(service.editorView("G1", null));
    }

    @Test
    void bootWithoutMerchantScreenIsRejected() throws Exception
    {
        goods.setModel3dMarks(null);
        assertKey("custom.badData", () -> service.save(
                "{\"goodsCode\":\"G1\",\"agree\":true,\"boot\":{\"bg\":\"#123456\"}}", List.of(), "ip"));
        // 只贴 logo 仍然可以
        service.save(data(logo("upload:0"), null), List.of(file(png(8, 8))), "ip");
        verify(planMapper, times(1)).insertPlan(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootNeedsOnlyMerchantScreen() throws Exception
    {
        // 商家只标了屏幕、没标开机键：照样能换开机画面
        goods.setModel3dMarks(MERCHANT_MARKS.replaceFirst(",\"power\":\\{[^}]*\\}", ""));
        service.save("{\"goodsCode\":\"G1\",\"agree\":true,\"boot\":{\"bg\":\"#123456\"}}", List.of(), "ip");
        Map<String, Object> marks = marksOf(savedPlan());
        assertEquals("#123456", ((Map<String, Object>) marks.get("screen")).get("bootBg"));
        assertNull(marks.get("power"));
    }

    @Test
    void storageFailureBecomesGenericError() throws Exception
    {
        doThrow(new ServiceException("上传到腾讯云失败（AccessDenied）"))
                .when(fileService).storeCustomImage(any(), anyString(), anyString(), anyString());
        assertKey("common.error", () -> service.save(data(logo("upload:0"), null), List.of(file(png(8, 8))), "ip"));
        verify(planMapper, never()).insertPlan(any());
    }

    @Test
    void honeypotDetection()
    {
        assertTrue(CustomPlanService.isHoneypot("{\"website\":\"http://spam\"}"));
        assertFalse(CustomPlanService.isHoneypot("{\"website\":\"\"}"));
        assertFalse(CustomPlanService.isHoneypot("not json"));
    }

    // ---------- 读 ----------

    @Test
    void publicViewHidesEmailAndNote()
    {
        CustomPlan plan = new CustomPlan();
        plan.setPlanId("AbCdEfGh12345678");
        plan.setGoodsCode("G1");
        plan.setModel3d(MODEL);
        plan.setMarks(MERCHANT_MARKS);
        plan.setEmail("secret@x.com");
        plan.setNote("target price 1.2");
        when(planMapper.selectById("AbCdEfGh12345678")).thenReturn(plan);

        Map<String, Object> view = service.publicView("AbCdEfGh12345678", null);
        assertNotNull(view);
        assertFalse(view.containsKey("email"));
        assertFalse(view.containsKey("note"));
        assertFalse(JSON.writeValueAsString(view).contains("secret@x.com"));
        assertEquals("Air pump", view.get("title"));
        assertNotNull(view.get("marks"));
    }

    @Test
    void publicViewSkipsDbForMalformedId()
    {
        assertNull(service.publicView("short", null));
        assertNull(service.publicView("AbCdEfGh1234567!", null));
        assertNull(service.publicView(null, null));
        verifyNoInteractions(planMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void editorViewCarriesOnlyScreenAndPower()
    {
        Map<String, Object> view = service.editorView("G1", null);
        assertNotNull(view.get("screen"));
        assertNotNull(view.get("power"));
        assertFalse(view.containsKey("logos"));
        assertEquals(MODEL, view.get("model3d"));
    }

    // ---------- 模型状态（docs/superpowers/specs/2026-10-08-product-3d-states-design.md）----------

    private static final String STATES = "{\"v\":1,\"states\":["
            + "{\"id\":\"s1\",\"name\":{\"en\":\"Deflated\",\"zh\":\"没气\"}},"
            + "{\"id\":\"s2\",\"name\":{\"en\":\"Half\"},\"model\":\"https://file.test/model/half.glb\"},"
            + "{\"id\":\"s3\",\"name\":{\"en\":\"Full\"}}]}";

    @Test
    void editorViewCarriesLocalizedStates()
    {
        goods.setModel3dStates(STATES);
        List<Map<String, Object>> states = (List<Map<String, Object>>) service.editorView("G1", null).get("states");
        // 没模型的 s3 不下发
        assertEquals(2, states.size());
        assertEquals("Deflated", states.get(0).get("name"));
        assertEquals("https://file.test/model/half.glb", states.get(1).get("model"));
    }

    @Test
    void saveSnapshotsStatesAndIgnoresClientStates() throws Exception
    {
        goods.setModel3dStates(STATES);
        service.save(data(logo("upload:0"),
                "\"states\":[{\"id\":\"s9\",\"model\":\"https://evil.test/x.glb\"}]"), List.of(file(png(8, 8))), "ip");
        String snap = savedPlan().getModel3dStates();
        assertNotNull(snap);
        assertTrue(snap.contains("half.glb"));
        assertTrue(snap.contains("没气"), "快照保留全语言名字");
        assertFalse(snap.contains("evil"));
        assertFalse(snap.contains("Full"), "没模型的状态不进快照");
    }

    @Test
    void saveWithoutStatesStoresNull()
    {
        service.save(data(logo("upload:0"), null), List.of(file(png(8, 8))), "ip");
        assertNull(savedPlan().getModel3dStates());
    }

    @Test
    void publicViewReadsStatesFromSnapshotNotGoods()
    {
        CustomPlan plan = new CustomPlan();
        plan.setPlanId("AbCdEfGh12345678");
        plan.setGoodsCode("G1");
        plan.setModel3d(MODEL);
        plan.setMarks(MERCHANT_MARKS);
        plan.setModel3dStates(Model3dStates.toSnapshot(STATES));
        when(planMapper.selectById("AbCdEfGh12345678")).thenReturn(plan);
        // 商家之后把状态删了：方案照旧
        goods.setModel3dStates(null);

        List<Map<String, Object>> states = (List<Map<String, Object>>) service.publicView("AbCdEfGh12345678", null).get("states");
        assertEquals(2, states.size());
        assertEquals("Half", states.get(1).get("name"));
    }

    // ---------- 规格各自的模型（docs/superpowers/specs/2026-10-09-combo-model3d-design.md）----------

    private static final String ARM = "https://file.test/model/arm.glb";
    private static final String ARM_MARKS = "{\"v\":1,\"model\":\"" + ARM + "\","
            + "\"screen\":{\"p\":[0,0.2,0.03],\"n\":[0,0,1],\"w\":0.04,\"h\":0.05,\"rot\":0,"
            + "\"runSrc\":\"https://file.test/image/arm-run.png\"}}";

    private static SiteGoodsCombo combo(String code, String model, String marks)
    {
        SiteGoodsCombo c = new SiteGoodsCombo();
        c.setComboCode(code);
        c.setTitle(code + " title");
        c.setModel3d(model);
        c.setModel3dMarks(marks);
        return c;
    }

    private static Map<String, Object> card(String code, String model, Map<String, Object> marks)
    {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", code);
        m.put("title", code + " title");
        m.put("image", null);
        if (model != null)
        {
            m.put("model3d", model);
            m.put("model3dMarks", marks);
        }
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void editorViewListsOnlyCombosWithModel()
    {
        when(openService.publicComboCards(any(), any(), any())).thenReturn(List.of(
                card("PLAIN", null, null),
                card("FOLLOW", "https://file.test/model/leg-l.glb", null),
                card("ARM", ARM, Model3dMarks.toObject(ARM_MARKS))));

        List<Map<String, Object>> combos = (List<Map<String, Object>>) service.editorView("G1", null).get("combos");

        assertEquals(2, combos.size());
        assertEquals(false, combos.get(0).get("own"));
        assertNull(combos.get(0).get("screen"), "跟随主模型的规格没有屏幕");
        assertEquals(true, combos.get(1).get("own"));
        assertNotNull(combos.get(1).get("screen"));
    }

    @Test
    void saveWithOwnComboUsesItsModelAndScreen() throws Exception
    {
        goods.setModel3dStates(STATES);
        when(comboMapper.selectPublicOne(eq("G1"), eq("ARM"), any(), any())).thenReturn(combo("ARM", ARM, ARM_MARKS));

        service.save("{\"goodsCode\":\"G1\",\"agree\":true,\"combo\":\"ARM\",\"boot\":{\"bg\":\"#123456\"}}", List.of(), "ip");

        CustomPlan plan = savedPlan();
        assertEquals("ARM", plan.getComboCode());
        assertEquals(ARM, plan.getModel3d());
        assertTrue(plan.getMarks().contains("arm-run.png"), "屏幕取规格自己的标注");
        assertFalse(plan.getMarks().contains("image/run.png"), "不混入主模型的屏幕");
        assertNull(plan.getModel3dStates(), "状态只属于主模型，规格方案不存");
    }

    @Test
    void saveWithFollowComboAllowsLogoButRejectsBoot()
    {
        when(comboMapper.selectPublicOne(eq("G1"), eq("FOLLOW"), any(), any()))
                .thenReturn(combo("FOLLOW", "https://file.test/model/leg-l.glb", null));

        assertKey("custom.badData", () -> service.save(
                "{\"goodsCode\":\"G1\",\"agree\":true,\"combo\":\"FOLLOW\",\"boot\":{\"bg\":\"#123456\"}}", List.of(), "ip"));
        service.save("{\"goodsCode\":\"G1\",\"agree\":true,\"combo\":\"FOLLOW\",\"logos\":[" + logo("upload:0") + "]}",
                List.of(file(png(8, 8))), "ip");
        CustomPlan plan = savedPlan();
        assertEquals("https://file.test/model/leg-l.glb", plan.getModel3d());
        assertFalse(plan.getMarks().contains("screen"));
    }

    @Test
    void saveRejectsUnknownOrModellessCombo()
    {
        // NOPE 不打桩 = 查不到；lenient：严格模式下用别的参数调到这个方法会被当成打桩写错
        lenient().when(comboMapper.selectPublicOne(eq("G1"), eq("PLAIN"), any(), any())).thenReturn(combo("PLAIN", null, null));
        assertKey("custom.badData", () -> service.save(
                "{\"goodsCode\":\"G1\",\"agree\":true,\"combo\":\"NOPE\",\"logos\":[" + logo("upload:0") + "]}",
                List.of(file(png(8, 8))), "ip"));
        assertKey("custom.badData", () -> service.save(
                "{\"goodsCode\":\"G1\",\"agree\":true,\"combo\":\"PLAIN\",\"logos\":[" + logo("upload:0") + "]}",
                List.of(file(png(8, 8))), "ip"));
        verify(planMapper, never()).insertPlan(any());
    }

    @Test
    void saveWithoutComboStillMain()
    {
        service.save(data(logo("upload:0"), null), List.of(file(png(8, 8))), "ip");
        CustomPlan plan = savedPlan();
        assertNull(plan.getComboCode());
        assertEquals(MODEL, plan.getModel3d());
        verifyNoInteractions(comboMapper);
    }

    @Test
    void publicViewCarriesComboTitle()
    {
        CustomPlan plan = new CustomPlan();
        plan.setPlanId("AbCdEfGh12345678");
        plan.setGoodsCode("G1");
        plan.setComboCode("ARM");
        plan.setModel3d(ARM);
        plan.setMarks(ARM_MARKS);
        when(planMapper.selectById("AbCdEfGh12345678")).thenReturn(plan);
        when(comboMapper.selectPublicOne(eq("G1"), eq("ARM"), any(), any())).thenReturn(combo("ARM", ARM, ARM_MARKS));

        Map<String, Object> view = service.publicView("AbCdEfGh12345678", null);
        assertEquals("ARM", view.get("comboCode"));
        assertEquals("ARM title", view.get("comboTitle"));
        assertEquals(true, view.get("comboAvailable"));
    }

    @Test
    void publicViewSurvivesDeletedCombo()
    {
        CustomPlan plan = new CustomPlan();
        plan.setPlanId("AbCdEfGh12345678");
        plan.setGoodsCode("G1");
        plan.setComboCode("GONE");
        plan.setModel3d(ARM);
        plan.setMarks(ARM_MARKS);
        when(planMapper.selectById("AbCdEfGh12345678")).thenReturn(plan);

        Map<String, Object> view = service.publicView("AbCdEfGh12345678", null);
        assertEquals("GONE", view.get("comboCode"));
        assertNull(view.get("comboTitle"));
        assertEquals(false, view.get("comboAvailable"));
        assertEquals(ARM, view.get("model3d"), "方案照常用快照模型");
    }
}
