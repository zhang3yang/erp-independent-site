package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.OrderWizard;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteOrderItem;
import com.ruoyi.station.console.mapper.OrderWizardMapper;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;

/**
 * 引导式下单：schema 校验 + 下单复核。
 *
 * <p>复核是一个公网写接口的闸，这里每条规则一个用例：看不见的字段答案丢掉、灰掉的选项塞不进来、
 * 隐藏的选项塞不进来、型号只认可选的、必填、起订量。</p>
 */
class OrderWizardServiceTest
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private OrderWizardService service;
    private OrderWizardMapper mapper;
    private SiteInfoService siteInfoService;
    private SiteInfo site;
    private String template;

    @BeforeEach
    void setUp()
    {
        service = new OrderWizardService();
        template = OrderWizardService.readResource("order-wizard/massager-template.json");

        mapper = mock(OrderWizardMapper.class);
        siteInfoService = mock(SiteInfoService.class);
        site = new SiteInfo();
        site.setSiteCode("DLZ2049739702");
        site.setLangs("fr,ru,es,ja,zh,en");
        site.setCurrency("GBP");
        when(siteInfoService.require()).thenReturn(site);

        SiteGoodsMapper goodsMapper = mock(SiteGoodsMapper.class);
        for (String code : List.of("EB-UNIT-01", "EB-BOOT-01", "EB-ARM-01", "EB-TROUS-01", "EB-WAIST-01", "EB-BAG-01"))
        {
            SiteGoods goods = new SiteGoods();
            goods.setGoodsCode(code);
            goods.setTitle(code + " title");
            goods.setUnit("pcs");
            when(goodsMapper.selectPublicOne(eq(code), anyString(), anyString())).thenReturn(goods);
        }
        SiteGoodsComboMapper comboMapper = mock(SiteGoodsComboMapper.class);
        List<SiteGoodsCombo> bootCombos = new ArrayList<>();
        for (String code : List.of("M-TOUTENSEMBLE", "L-TOUTENSEMBLE"))
        {
            SiteGoodsCombo combo = new SiteGoodsCombo();
            combo.setComboCode(code);
            combo.setTitle(code.charAt(0) + " · 全套");
            bootCombos.add(combo);
        }
        when(comboMapper.selectPublicByGoods(eq("EB-BOOT-01"), anyString(), anyString())).thenReturn(bootCombos);

        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "siteInfoService", siteInfoService);
        ReflectionTestUtils.setField(service, "goodsMapper", goodsMapper);
        ReflectionTestUtils.setField(service, "comboMapper", comboMapper);
        open(template);
    }

    private void open(String schema)
    {
        OrderWizard row = new OrderWizard();
        row.setEnabled(1);
        row.setSchemaJson(schema);
        when(mapper.selectOne()).thenReturn(row);
        ReflectionTestUtils.setField(service, "cache", null);
    }

    private static JsonNode answers(String json)
    {
        return JSON.readTree(json.replace('\'', '"'));
    }

    /** 一份合格的企业 OEM 答案，各用例在它上面改一两处 */
    private static final String OEM = "{'audience':'business','mode':'oem','country':'FR',"
            + "'unitNeed':'yes','unitQty':5,'plug':'EU','screenLogo':'mine',"
            + "'garments':['boot'],'garmentQty':{'EB-BOOT-01':{'M-TOUTENSEMBLE':3,'L-TOUTENSEMBLE':2}},"
            + "'logo':'print','manual':['fr','en'],'certs':['CE'],'sample':'sample'}";

    // ==================== schema 校验 ====================

    @Test
    @DisplayName("预置模板本身过得了校验")
    void templateIsValid()
    {
        assertDoesNotThrow(() -> OrderWizardService.validateSchema(template));
    }

    @Test
    @DisplayName("统领那份模板（零部件 + 套件）也过得了校验")
    void tonglingTemplateIsValid() throws Exception
    {
        try (var in = OrderWizardService.class.getClassLoader().getResourceAsStream("order-wizard/tongling-template.json"))
        {
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            OrderWizardService.Model model = OrderWizardService.validateSchema(json);
            assertEquals(18, model.goodsCodes().size(), "统领上架的 18 个商品都要挂上（G001 测试数据除外）");
        }
    }

    @Test
    @DisplayName("康辰那份模板（硅胶管按图定制）过得了校验，两款管都挂上")
    void kangchenTemplateIsValid()
    {
        OrderWizardService.Model model = OrderWizardService.validateSchema(
                OrderWizardService.readResource("order-wizard/kangchen-template.json"));
        assertEquals(List.of("12010304", "12010305"), new ArrayList<>(model.goodsCodes()));
    }

    @Test
    @DisplayName("康辰：目录规格不用填定制参数；直接做大货不满 500 米拒，先打样不限")
    void kangchenResolve()
    {
        site.setSiteCode("DLZ2049683802");
        site.setLangs("zh,en,es,pt,it,ko,ja");
        SiteGoodsMapper goodsMapper = mock(SiteGoodsMapper.class);
        for (String code : List.of("12010304", "12010305"))
        {
            SiteGoods goods = new SiteGoods();
            goods.setGoodsCode(code);
            goods.setUnit("m");
            when(goodsMapper.selectPublicOne(eq(code), anyString(), anyString())).thenReturn(goods);
        }
        ReflectionTestUtils.setField(service, "goodsMapper", goodsMapper);
        ReflectionTestUtils.setField(service, "comboMapper", mock(SiteGoodsComboMapper.class));
        open(OrderWizardService.readResource("order-wizard/kangchen-template.json"));

        String stock = "{'mode':'stock','application':'industrial','country':'EU','tubes':['tube8'],"
                + "'tubeQty':{'12010305':{'':600}},'sample':'bulk'}";
        OrderWizardService.Resolved r = service.resolve(answers(stock), "en");
        assertEquals(1, r.lines().size());
        assertEquals(600, r.lines().get(0).getQty().intValue());

        String few = stock.replace("600", "50");
        assertEquals("wizard.moq", assertThrows(ServiceException.class,
                () -> service.resolve(answers(few), "en")).getKey());
        assertDoesNotThrow(() -> service.resolve(answers(few.replace("'bulk'", "'sample'")), "en"));

        // 按图定制：尺寸说明必填
        String custom = few.replace("'stock'", "'custom'").replace("'bulk'", "'sample'")
                .replace("'sample':", "'grade':'food','drawing':'haveDrawing','sample':");
        assertEquals("wizard.required", assertThrows(ServiceException.class,
                () -> service.resolve(answers(custom), "zh")).getKey());
        assertDoesNotThrow(() -> service.resolve(answers(custom.replace("'grade'", "'dims':'8 x 2.0 mm','grade'")), "zh"));
    }

    @Test
    @DisplayName("载入预置模板按站点挑：法国站 → 按摩仪，统领 → 零部件，康辰 → 硅胶管，对不上的站不替人挑")
    void presetBySite()
    {
        // 法国站（setUp 里的站点）
        assertTrue(service.template(null).contains("EB-UNIT-01"));
        assertEquals("massager", service.templates().get(0).get("code"));
        assertEquals(true, service.templates().get(0).get("forThisSite"));

        site.setSiteCode("DLZ2049684105");   // 统领
        assertTrue(service.template(null).contains("12023907"));
        assertEquals("parts", service.templates().get(0).get("code"));

        site.setSiteCode("DLZ2049683802");   // 康辰
        assertTrue(service.template(null).contains("12010305"));
        assertEquals("tubing", service.templates().get(0).get("code"));

        site.setSiteCode("DLZ0000000000");   // 没登记的站
        assertThrows(ServiceException.class, () -> service.template(null));
        assertTrue(service.templates().stream().noneMatch(p -> Boolean.TRUE.equals(p.get("forThisSite"))));
        // 明确指定编码时照给
        assertTrue(service.template("parts").contains("12023907"));
        assertThrows(ServiceException.class, () -> service.template("nope"));
    }

    @Test
    @DisplayName("条件只能引用排在前面的字段")
    void conditionMustLookBackwards()
    {
        String bad = "{'groups':[{'code':'g','fields':["
                + "{'code':'a','type':'options','showIf':[{'field':'b','in':['x']}],'options':[{'code':'x'}]},"
                + "{'code':'b','type':'options','options':[{'code':'x'}]}]}]}";
        ServiceException e = assertThrows(ServiceException.class,
                () -> OrderWizardService.validateSchema(bad.replace('\'', '"')));
        assertTrue(e.getMessage().contains("排在后面"), e.getMessage());
    }

    @Test
    @DisplayName("字段编码整份不能重名、类型要认识")
    void duplicateCodeAndUnknownType()
    {
        String dup = "{'groups':[{'code':'g','fields':[{'code':'a','type':'text'},{'code':'a','type':'text'}]}]}";
        assertThrows(ServiceException.class, () -> OrderWizardService.validateSchema(dup.replace('\'', '"')));
        String type = "{'groups':[{'code':'g','fields':[{'code':'a','type':'slider'}]}]}";
        assertThrows(ServiceException.class, () -> OrderWizardService.validateSchema(type.replace('\'', '"')));
    }

    @Test
    @DisplayName("色块颜色写不出 url(...)：它会进前台的 CSS")
    void colorCannotCarryUrl()
    {
        String bad = "{'groups':[{'code':'g','fields':[{'code':'c','type':'swatch','options':"
                + "[{'code':'x','color':'url(https://evil.example/x.png)'}]}]}]}";
        assertThrows(ServiceException.class, () -> OrderWizardService.validateSchema(bad.replace('\'', '"')));
    }

    // ==================== 下单复核 ====================

    @Test
    @DisplayName("企业 OEM：订单行 = 主机台数 + 各尺码数量，快照有中文")
    void resolvesLinesAndSnapshot()
    {
        OrderWizardService.Resolved r = service.resolve(answers(OEM), "fr");
        List<SiteOrderItem> lines = r.lines();
        assertEquals(3, lines.size());
        assertEquals("EB-UNIT-01", lines.get(0).getGoodsCode());
        assertNull(lines.get(0).getComboCode());
        assertEquals(5, lines.get(0).getQty().intValue());
        assertEquals("M-TOUTENSEMBLE", lines.get(1).getComboCode());
        assertEquals(3, lines.get(1).getQty().intValue());

        List<Map<String, String>> zh = OrderWizardService.snapshotRows(r.snapshotJson(), true);
        assertTrue(zh.stream().anyMatch(row -> "订购方式".equals(row.get("field")) && "贴牌 OEM".equals(row.get("value"))));
        List<Map<String, String>> fr = OrderWizardService.snapshotRows(r.snapshotJson(), false);
        assertTrue(fr.stream().anyMatch(row -> "Type de commande".equals(row.get("field"))));
    }

    @Test
    @DisplayName("品牌现货塞一个只有 OEM 能选的 logo 工艺：拒")
    void lockedOptionRejected()
    {
        String stock = OEM.replace("'mode':'oem'", "'mode':'stock'").replace("'screenLogo':'mine',", "");
        ServiceException e = assertThrows(ServiceException.class, () -> service.resolve(answers(stock), "zh"));
        assertEquals("wizard.locked", e.getKey());
    }

    @Test
    @DisplayName("个人买家：企业才有的字段（订购方式、logo、认证）被丢掉，不报错也不进快照")
    void hiddenFieldsDropped()
    {
        String personal = OEM.replace("'audience':'business'", "'audience':'personal'").replace("'screenLogo':'mine',", "");
        OrderWizardService.Resolved r = service.resolve(answers(personal), "en");
        String snapshot = r.snapshotJson();
        assertFalse(snapshot.contains("\"code\":\"mode\""), snapshot);
        assertFalse(snapshot.contains("\"code\":\"logo\""), snapshot);
        assertFalse(snapshot.contains("\"code\":\"certs\""), snapshot);
    }

    @Test
    @DisplayName("对访客隐藏的选项（英规插头）塞不进来")
    void hiddenOptionRejected()
    {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.resolve(answers(OEM.replace("'plug':'EU'", "'plug':'UK'")), "fr"));
        assertEquals("wizard.badOption", e.getKey());
    }

    @Test
    @DisplayName("型号只认可选的；一个数都没填算没填")
    void comboAndRequired()
    {
        String badCombo = OEM.replace("'L-TOUTENSEMBLE':2", "'XXXL':2");
        assertEquals("wizard.badGoods", assertThrows(ServiceException.class,
                () -> service.resolve(answers(badCombo), "fr")).getKey());

        String zero = OEM.replace("'M-TOUTENSEMBLE':3,'L-TOUTENSEMBLE':2", "'M-TOUTENSEMBLE':0");
        assertEquals("wizard.required", assertThrows(ServiceException.class,
                () -> service.resolve(answers(zero), "fr")).getKey());
    }

    @Test
    @DisplayName("起订量：定制彩盒放出来之后，总数不够 100 拒")
    void moqEnforced()
    {
        open(template.replace("\"code\": \"customBox\", \"hidden\": true,", "\"code\": \"customBox\","));
        String boxed = OEM.replace("'logo':'print'", "'logo':'print','pack':'customBox'");
        ServiceException e = assertThrows(ServiceException.class, () -> service.resolve(answers(boxed), "fr"));
        assertEquals("wizard.moq", e.getKey());
    }

    @Test
    @DisplayName("开关关着：拒，前台也拿不到向导")
    void closedWizard()
    {
        OrderWizard row = new OrderWizard();
        row.setEnabled(0);
        row.setSchemaJson(template);
        when(mapper.selectOne()).thenReturn(row);
        ReflectionTestUtils.setField(service, "cache", null);
        assertEquals("wizard.closed", assertThrows(ServiceException.class,
                () -> service.resolve(answers(OEM), "fr")).getKey());
        assertNull(service.publicView("fr"));
        assertEquals(false, service.summary("fr").get("enabled"));
    }

    @Test
    @DisplayName("前台视图：隐藏选项不下发，文字按语言挑好")
    @SuppressWarnings("unchecked")
    void publicViewStripsHidden()
    {
        Map<String, Object> view = service.publicView("zh");
        assertEquals("在线定制下单", view.get("title"));
        String dump = JSON.writeValueAsString(view);
        assertFalse(dump.contains("\"UK\""), "英规是 hidden，不该下发");
        assertFalse(dump.contains("battery"), "电池版是 hidden，不该下发");
        // 选项上的加价是预留字段，不下发；商品和型号的单价要下发（前台算预估总价）
        List<Map<String, Object>> groups = (List<Map<String, Object>>) view.get("groups");
        for (Map<String, Object> g : groups)
        {
            for (Map<String, Object> f : (List<Map<String, Object>>) g.get("fields"))
            {
                for (Map<String, Object> o : (List<Map<String, Object>>) f.getOrDefault("options", List.of()))
                {
                    assertFalse(o.containsKey("price"), "选项加价不该下发：" + o.get("code"));
                }
            }
        }
        Map<String, Object> goods = (Map<String, Object>) view.get("goods");
        assertTrue(goods.containsKey("EB-BOOT-01"));
        assertTrue(((Map<String, Object>) goods.get("EB-BOOT-01")).containsKey("price"));
        assertEquals("GBP", view.get("currency"));
    }
}
