package com.ruoyi.station.console.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
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
 * 引导式下单（下单向导）。设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md
 *
 * <p>三件事：</p>
 * <ol>
 *   <li><b>管理台存向导</b>（{@link #save}）：{@link #validateSchema} 是 schema 格式的唯一把关处。
 *       前台和下面的 {@link #resolve} 都假定过了这一关的 JSON 结构是对的。</li>
 *   <li><b>前台取向导</b>（{@link #publicView}）：文字按访客语言挑好、对访客隐藏的选项剔掉、
 *       挂的商品没上架的选项剔掉，前台拿到就能直接渲染。</li>
 *   <li><b>下单时复核</b>（{@link #resolve}）：前台送来的答案<b>一项都不信</b>，按同一份 schema
 *       重新走一遍显隐、必填、选项、联动、起订量，产出订单行和选配快照。</li>
 * </ol>
 *
 * <h3>⚠ 为什么 resolve 要把显隐重算一遍</h3>
 * <p>前台是公网上谁都能改的 JS。只校验「选项存不存在」的话，品牌现货的买家可以直接往请求里
 * 塞一个 {@code logo: "embroidery"}——那个选项确实存在，只是在他的条件下是灰的。
 * 所以条件（{@code showIf} / {@code needs}）在后端也要成立，看不见的字段答案一律丢掉。</p>
 *
 * <h3>条件只能引用排在前面的字段</h3>
 * <p>{@link #validateSchema} 强制这一条。这样前后端都能「从上往下一遍」求出显隐，
 * 不会出现 A 看 B、B 又看 A 的死循环，也和页面上「先选上面、再选下面」的顺序一致。</p>
 */
@Service
public class OrderWizardService
{
    private static final Logger log = LoggerFactory.getLogger(OrderWizardService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 预置模板。管理台「载入预置模板」按本站的站点编码挑对应的那份（2026-09-28）。
     *
     * <p>三个站是同一套代码，但卖的东西不一样：法国站是按摩仪整机（主机 + 恢复靴），统领是零部件和加压套件，
     * 没有主机成品。只有一份模板时，统领点「载入预置模板」会被换成法国站那套、选项全挂着统领没有的商品。</p>
     *
     * <p>{@code siteCodes} 是这份模板<b>给哪些站用</b>；一个站都没对上时不自动挑，
     * 管理台只给下拉让人自己选——默认载入别家的模板比不载入更糟。新站要有自己的模板时加一行即可。</p>
     */
    record Preset(String code, String file, Set<String> siteCodes)
    {
    }

    static final List<Preset> PRESETS = List.of(
            // 按摩仪整机：主机 + 恢复靴 / 臂套 / 裤 / 腰带（Esva boots，法国站）
            new Preset("massager", "order-wizard/massager-template.json", Set.of("DLZ2049739702")),
            // 零部件与加压套件：4/6/8 腔套件、气泵气阀、电池、适配器、硅胶管（统领）
            new Preset("parts", "order-wizard/tongling-template.json", Set.of("DLZ2049684105")),
            // 硅胶管按图定制：4 腔 / 8 腔两款管按米下单，定制时再填等级、硬度、颜色、尺寸（康辰，2026-09-29）
            new Preset("tubing", "order-wizard/kangchen-template.json", Set.of("DLZ2049683802")));

    static final Set<String> OPTION_TYPES = Set.of("cards", "options", "swatch");
    static final Set<String> TYPES = Set.of("cards", "options", "swatch", "number", "text", "date", "goods");

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern GOODS_CODE = Pattern.compile("^[A-Za-z0-9._-]{1,50}$");
    private static final Pattern LANG = Pattern.compile("^[a-z]{2}(-[a-z]{2})?$");
    /**
     * 色块的颜色。它会被前台原样放进 CSS 的 background 里，所以只放行颜色和渐变用得到的字符——
     * 引号、分号、冒号、斜杠一律不行，写不出 url(...) 也跳不出这一条声明。
     */
    private static final Pattern COLOR = Pattern.compile("^[#A-Za-z0-9(),.%\\s-]{1,200}$");

    private static final int MAX_SCHEMA_CHARS = 300_000;
    private static final int MAX_GROUPS = 20;
    private static final int MAX_FIELDS = 40;
    private static final int MAX_OPTIONS = 80;
    private static final int MAX_LABEL = 2000;
    /** 买家填的文本最长多少字。和订单备注（800）不是一回事：那是订单上的一列，这个进快照 */
    static final int MAX_TEXT = 1000;
    /** 数量上限，和 SiteOrderService.MAX_QTY 同一个数 */
    static final long MAX_QTY = 9_999_999L;

    @Autowired
    private OrderWizardMapper mapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    /**
     * 进程内缓存：库里那一行 + 解析好的模型。
     *
     * <p>{@code /openapi/site/info} 每次 SSR 都要问一句「向导开没开」，不缓存就是每个页面
     * 多一次查库。一个部署一个进程，改动只从 {@link #save} 进来，存完清掉即可。</p>
     */
    private volatile Loaded cache;

    private record Loaded(OrderWizard row, Model model)
    {
    }

    // ==================== 模型 ====================

    record Cond(String field, Set<String> in)
    {
    }

    static final class Opt
    {
        String code;
        JsonNode node;
        String goods;
        long moq;
        List<Cond> needs = List.of();
        boolean hidden;
    }

    static final class Field
    {
        String code;
        String type;
        boolean multi;
        boolean required;
        JsonNode node;
        List<Cond> showIf = List.of();
        List<Opt> options = List.of();
        long min;
        long max;
        String goods;
        String source;

        boolean isOptions()
        {
            return OPTION_TYPES.contains(type);
        }

        Opt option(String code)
        {
            for (Opt opt : options)
            {
                if (opt.code.equals(code))
                {
                    return opt;
                }
            }
            return null;
        }
    }

    static final class Group
    {
        String code;
        JsonNode node;
        List<Cond> showIf = List.of();
        List<Field> fields = new ArrayList<>();
    }

    static final class Model
    {
        JsonNode root;
        List<Group> groups = new ArrayList<>();

        /** schema 里挂到的全部商品编码（选项上的 + 数字字段上的），按出现顺序 */
        Set<String> goodsCodes()
        {
            Set<String> codes = new LinkedHashSet<>();
            for (Group group : groups)
            {
                for (Field field : group.fields)
                {
                    if (field.goods != null)
                    {
                        codes.add(field.goods);
                    }
                    for (Opt opt : field.options)
                    {
                        if (opt.goods != null && !opt.hidden)
                        {
                            codes.add(opt.goods);
                        }
                    }
                }
            }
            return codes;
        }
    }

    // ==================== 校验（管理台保存时） ====================

    /**
     * 校验并解析一份 schema。<b>格式的唯一把关处</b>，报错是给管理台看的中文。
     *
     * @return 解析好的模型；不合格抛 {@link ServiceException}
     */
    static Model validateSchema(String json)
    {
        if (json == null || json.isBlank())
        {
            throw new ServiceException("向导内容是空的");
        }
        if (json.length() > MAX_SCHEMA_CHARS)
        {
            throw new ServiceException("向导内容太大了（超过 " + MAX_SCHEMA_CHARS / 1000 + "K 字符），请精简选项或说明文字");
        }
        JsonNode root;
        try
        {
            root = JSON.readTree(json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("向导内容不是合法的 JSON：" + e.getOriginalMessage());
        }
        if (root == null || !root.isObject())
        {
            throw new ServiceException("向导内容必须是一个 JSON 对象");
        }
        checkText(root.path("title"), "向导标题");
        checkText(root.path("lead"), "向导导语");

        JsonNode groups = root.path("groups");
        if (!groups.isArray() || groups.isEmpty())
        {
            throw new ServiceException("向导里至少要有一个分组");
        }
        if (groups.size() > MAX_GROUPS)
        {
            throw new ServiceException("分组最多 " + MAX_GROUPS + " 个");
        }

        Model model = new Model();
        model.root = root;
        Set<String> groupCodes = new HashSet<>();
        // 按顺序登记已经出现过的字段：条件只能引用排在前面的，见类注释
        Map<String, Field> seen = new LinkedHashMap<>();

        for (JsonNode g : groups.values())
        {
            Group group = new Group();
            group.node = g;
            group.code = code(g, "分组");
            if (!groupCodes.add(group.code))
            {
                throw new ServiceException("分组编码重复：" + group.code);
            }
            String where = "分组「" + group.code + "」";
            checkText(g.path("title"), where + "的标题");
            checkText(g.path("sub"), where + "的副标题");
            group.showIf = conds(g.path("showIf"), seen, where + "的显示条件");

            JsonNode fields = g.path("fields");
            if (!fields.isArray() || fields.isEmpty())
            {
                throw new ServiceException(where + "里至少要有一个字段");
            }
            if (fields.size() > MAX_FIELDS)
            {
                throw new ServiceException(where + "的字段最多 " + MAX_FIELDS + " 个");
            }
            for (JsonNode f : fields.values())
            {
                Field field = parseField(f, seen);
                seen.put(field.code, field);
                group.fields.add(field);
            }
            model.groups.add(group);
        }
        return model;
    }

    private static Field parseField(JsonNode f, Map<String, Field> seen)
    {
        Field field = new Field();
        field.node = f;
        field.code = code(f, "字段");
        String where = "字段「" + field.code + "」";
        if (seen.containsKey(field.code))
        {
            throw new ServiceException("字段编码重复：" + field.code + "（整份向导里不能重名，条件是按编码找字段的）");
        }
        field.type = f.path("type").asString("");
        if (!TYPES.contains(field.type))
        {
            throw new ServiceException(where + "的类型「" + field.type + "」不认识，只能是 " + String.join(" / ", TYPES));
        }
        field.multi = f.path("multi").asBoolean(false);
        field.required = f.path("required").asBoolean(false);
        checkText(f.path("title"), where + "的名称");
        checkText(f.path("help"), where + "的说明");
        checkText(f.path("hint"), where + "的提示");
        field.showIf = conds(f.path("showIf"), seen, where + "的显示条件");

        if (field.isOptions())
        {
            JsonNode options = f.path("options");
            if (!options.isArray() || options.isEmpty())
            {
                throw new ServiceException(where + "至少要有一个选项");
            }
            if (options.size() > MAX_OPTIONS)
            {
                throw new ServiceException(where + "的选项最多 " + MAX_OPTIONS + " 个");
            }
            List<Opt> list = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (JsonNode o : options.values())
            {
                Opt opt = new Opt();
                opt.node = o;
                opt.code = code(o, where + "的选项");
                if (!codes.add(opt.code))
                {
                    throw new ServiceException(where + "的选项编码重复：" + opt.code);
                }
                String ow = where + "的选项「" + opt.code + "」";
                checkText(o.path("title"), ow + "的名称");
                checkText(o.path("desc"), ow + "的描述");
                checkText(o.path("badge"), ow + "的角标");
                opt.hidden = o.path("hidden").asBoolean(false);
                opt.needs = conds(o.path("needs"), seen, ow + "的可选条件");
                opt.moq = nonNegative(o.path("moq"), ow + "的起订量");
                if (!o.path("price").isMissingNode() && !o.path("price").isNull()
                        && (!o.path("price").isNumber() || o.path("price").decimalValue().signum() < 0))
                {
                    throw new ServiceException(ow + "的加价必须是不小于 0 的数");
                }
                opt.goods = goods(o.path("goods"), ow);
                String icon = o.path("icon").asString("");
                if (icon.length() > 8)
                {
                    throw new ServiceException(ow + "的图标太长（放一个表情符号即可，要图片请填图片地址）");
                }
                String image = o.path("image").asString("");
                if (!image.isEmpty() && !(image.startsWith("https://") || image.startsWith("http://") || image.startsWith("/")))
                {
                    throw new ServiceException(ow + "的图片地址必须以 https:// 或 / 开头");
                }
                String color = o.path("color").asString("");
                if (!color.isEmpty() && !COLOR.matcher(color).matches())
                {
                    throw new ServiceException(ow + "的颜色只能写色值或渐变（如 #15181C、linear-gradient(...)）");
                }
                JsonNode sets = o.path("sets");
                if (!sets.isMissingNode() && !sets.isNull() && !sets.isObject())
                {
                    throw new ServiceException(ow + "的联动设置必须是对象");
                }
                list.add(opt);
            }
            field.options = list;
        }

        if ("number".equals(field.type))
        {
            field.min = nonNegative(f.path("min"), where + "的最小值");
            field.max = f.path("max").isMissingNode() || f.path("max").isNull() ? MAX_QTY : nonNegative(f.path("max"), where + "的最大值");
            if (field.max > MAX_QTY || field.min > field.max)
            {
                throw new ServiceException(where + "的最小值 / 最大值不对（0 ≤ 最小 ≤ 最大 ≤ " + MAX_QTY + "）");
            }
            field.goods = goods(f.path("goods"), where);
        }

        if ("goods".equals(field.type))
        {
            field.source = f.path("source").asString("");
            Field source = seen.get(field.source);
            if (source == null || !source.isOptions())
            {
                throw new ServiceException(where + "的「商品来自」必须是排在它前面的一个选项类字段");
            }
            boolean anyGoods = source.options.stream().anyMatch(o -> o.goods != null);
            if (!anyGoods)
            {
                throw new ServiceException(where + "引用的字段「" + source.code + "」没有一个选项挂了商品");
            }
        }
        return field;
    }

    private static String code(JsonNode node, String what)
    {
        if (node == null || !node.isObject())
        {
            throw new ServiceException(what + "必须是对象");
        }
        String code = node.path("code").asString("");
        if (!CODE.matcher(code).matches())
        {
            throw new ServiceException(what + "的编码「" + code + "」不合规：1~40 位字母、数字、下划线或短横线");
        }
        return code;
    }

    private static String goods(JsonNode node, String where)
    {
        if (node.isMissingNode() || node.isNull() || node.asString("").isEmpty())
        {
            return null;
        }
        String code = node.asString("");
        if (!GOODS_CODE.matcher(code).matches())
        {
            throw new ServiceException(where + "挂的商品编码「" + code + "」不合规");
        }
        return code;
    }

    private static long nonNegative(JsonNode node, String what)
    {
        if (node.isMissingNode() || node.isNull())
        {
            return 0;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0)
        {
            throw new ServiceException(what + "必须是不小于 0 的整数");
        }
        return node.longValue();
    }

    /** 多语言文字：{"fr":"...","en":"..."}。可以没有，有就得是这个形状 */
    private static void checkText(JsonNode node, String what)
    {
        if (node.isMissingNode() || node.isNull())
        {
            return;
        }
        if (!node.isObject())
        {
            throw new ServiceException(what + "必须按语言写成 {\"fr\":\"…\",\"en\":\"…\"}");
        }
        for (Map.Entry<String, JsonNode> entry : node.properties())
        {
            if (!LANG.matcher(entry.getKey()).matches())
            {
                throw new ServiceException(what + "里的语言代码「" + entry.getKey() + "」不认识");
            }
            if (!entry.getValue().isString() || entry.getValue().asString().length() > MAX_LABEL)
            {
                throw new ServiceException(what + "（" + entry.getKey() + "）必须是不超过 " + MAX_LABEL + " 字的文字");
            }
        }
    }

    private static List<Cond> conds(JsonNode node, Map<String, Field> seen, String what)
    {
        if (node.isMissingNode() || node.isNull())
        {
            return List.of();
        }
        if (!node.isArray())
        {
            throw new ServiceException(what + "必须是数组：[{\"field\":\"…\",\"in\":[\"…\"]}]");
        }
        List<Cond> list = new ArrayList<>();
        for (JsonNode c : node.values())
        {
            String field = c.path("field").asString("");
            Field target = seen.get(field);
            if (target == null)
            {
                throw new ServiceException(what + "引用的字段「" + field + "」不存在，或者排在后面（条件只能看前面的字段）");
            }
            if (!target.isOptions())
            {
                throw new ServiceException(what + "引用的字段「" + field + "」不是选项类，没法按选了什么来判断");
            }
            JsonNode in = c.path("in");
            if (!in.isArray() || in.isEmpty())
            {
                throw new ServiceException(what + "要列出至少一个取值");
            }
            Set<String> values = new LinkedHashSet<>();
            for (JsonNode v : in.values())
            {
                String value = v.asString("");
                if (target.option(value) == null)
                {
                    throw new ServiceException(what + "里的取值「" + value + "」不是字段「" + field + "」的选项");
                }
                values.add(value);
            }
            list.add(new Cond(field, values));
        }
        return list;
    }

    // ==================== 读 ====================

    private Loaded loaded()
    {
        Loaded current = cache;
        if (current != null)
        {
            return current;
        }
        OrderWizard row = mapper.selectOne();
        Model model = null;
        if (row != null && row.getSchemaJson() != null && !row.getSchemaJson().isBlank())
        {
            try
            {
                model = validateSchema(row.getSchemaJson());
            }
            catch (ServiceException e)
            {
                // 库里的内容过不了校验：只可能是有人直接改库。当成没开，别让前台拿到一份坏的
                log.error("u_s_orderwizard.schemajson 过不了校验，向导按关闭处理：{}", e.getMessage());
            }
        }
        current = new Loaded(row, model);
        cache = current;
        return current;
    }

    /** 开着且内容有效，才算开 */
    private Model openModel()
    {
        Loaded current = loaded();
        return current.row() != null && current.row().isOn() ? current.model() : null;
    }

    /**
     * 给 {@code /openapi/site/info} 的那一小块：前台据此决定顶栏和商品页要不要放入口。
     */
    public Map<String, Object> summary(String lang)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Model model = openModel();
        result.put("enabled", model != null);
        if (model != null)
        {
            SiteInfo site = siteInfoService.require();
            String language = site.resolveLang(lang);
            result.put("title", pick(model.root.path("title"), language, site.defaultLang()));
            result.put("goods", new ArrayList<>(model.goodsCodes()));
        }
        return result;
    }

    /**
     * 前台渲染用的向导。没开返回 null。
     *
     * <p>和库里那份的区别：文字按访客语言挑成字符串；{@code hidden} 的选项、挂的商品没上架的选项、
     * 挂的商品没上架的数字字段都剔掉；<b>选项上的</b> {@code price}（加价，预留字段）不下发、也不计价。
     * 另附 {@code goods}：向导挂到的商品 + 单价 + 各自对外可选的型号（带型号价），goods 字段按它出尺码表，
     * 前台按它算预估总价；{@code currency} 是这些单价的币种。</p>
     */
    public Map<String, Object> publicView(String lang)
    {
        Model model = openModel();
        if (model == null)
        {
            return null;
        }
        SiteInfo site = siteInfoService.require();
        String language = site.resolveLang(lang);
        String defaultLang = site.defaultLang();
        Goods goods = new Goods(language, defaultLang);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", pick(model.root.path("title"), language, defaultLang));
        result.put("lead", pick(model.root.path("lead"), language, defaultLang));

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Group group : model.groups)
        {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("code", group.code);
            g.put("title", pick(group.node.path("title"), language, defaultLang));
            g.put("sub", pick(group.node.path("sub"), language, defaultLang));
            g.put("showIf", condsView(group.showIf));
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Field field : group.fields)
            {
                if (field.goods != null && goods.one(field.goods) == null)
                {
                    continue;
                }
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("code", field.code);
                f.put("type", field.type);
                f.put("multi", field.multi);
                f.put("required", field.required);
                f.put("title", pick(field.node.path("title"), language, defaultLang));
                f.put("help", pick(field.node.path("help"), language, defaultLang));
                f.put("hint", pick(field.node.path("hint"), language, defaultLang));
                f.put("showIf", condsView(field.showIf));
                if ("number".equals(field.type))
                {
                    f.put("min", field.min);
                    f.put("max", field.max);
                    f.put("goods", field.goods);
                }
                if ("goods".equals(field.type))
                {
                    f.put("source", field.source);
                }
                if (field.isOptions())
                {
                    List<Map<String, Object>> options = new ArrayList<>();
                    for (Opt opt : field.options)
                    {
                        if (opt.hidden || (opt.goods != null && goods.one(opt.goods) == null))
                        {
                            continue;
                        }
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("code", opt.code);
                        o.put("title", pick(opt.node.path("title"), language, defaultLang));
                        o.put("desc", pick(opt.node.path("desc"), language, defaultLang));
                        o.put("badge", pick(opt.node.path("badge"), language, defaultLang));
                        o.put("icon", opt.node.path("icon").asString(""));
                        o.put("image", opt.node.path("image").asString(""));
                        o.put("color", opt.node.path("color").asString(""));
                        o.put("needs", condsView(opt.needs));
                        o.put("moq", opt.moq);
                        o.put("goods", opt.goods);
                        JsonNode sets = opt.node.path("sets");
                        o.put("sets", sets.isObject() ? JSON.convertValue(sets, Map.class) : Map.of());
                        options.add(o);
                    }
                    // 一个选项都不剩（全隐藏了 / 挂的商品全下架了）的选项字段不下发：留个空框只会让人卡住
                    if (options.isEmpty())
                    {
                        continue;
                    }
                    f.put("options", options);
                }
                fields.add(f);
            }
            if (!fields.isEmpty())
            {
                g.put("fields", fields);
                groups.add(g);
            }
        }
        result.put("groups", groups);

        Map<String, Object> goodsView = new LinkedHashMap<>();
        for (String code : model.goodsCodes())
        {
            SiteGoods one = goods.one(code);
            if (one == null)
            {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("title", firstNonBlank(one.getTitle(), one.getGoodsName()));
            item.put("unit", one.getUnit());
            item.put("image", firstImage(one));
            // 单价（站点本币）。前台据此算「预估总价」，和商品页、购物车同一个价——那两处本来就公开显示。
            // ⚠ 只是展示：下单时照旧由 SiteOrderService.repriceLines 从库里现查，不信前端传的
            item.put("price", one.getPrice());
            List<Map<String, Object>> combos = new ArrayList<>();
            for (SiteGoodsCombo combo : goods.combos(code))
            {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("code", combo.getComboCode());
                c.put("title", firstNonBlank(combo.getTitle(), combo.getComboCode()));
                c.put("image", combo.getImage());
                // 选了型号就按型号价，和 repriceLines 同一个口径
                c.put("price", combo.getPrice());
                combos.add(c);
            }
            item.put("combos", combos);
            goodsView.put(code, item);
        }
        result.put("goods", goodsView);
        // 上面那些单价的币种（站点本币）。前台换算成访客选的显示币种，和购物车一样
        result.put("currency", site.getCurrency());
        return result;
    }

    private static List<Map<String, Object>> condsView(List<Cond> conds)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Cond cond : conds)
        {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("field", cond.field());
            c.put("in", new ArrayList<>(cond.in()));
            list.add(c);
        }
        return list;
    }

    // ==================== 下单复核 ====================

    /** 复核的结果：订单行（只有编码和数量，价格照旧由 SiteOrderService 现查）+ 快照 JSON */
    public record Resolved(List<SiteOrderItem> lines, String snapshotJson)
    {
    }

    /**
     * 按已启用的 schema 复核买家的答案。见类注释「为什么要把显隐重算一遍」。
     *
     * @param answers {字段编码: 值}，值的形状见设计文档「答案格式」
     * @param lang    买家当时浏览的语言。报错文字和快照里的那份文字按它
     */
    public Resolved resolve(JsonNode answers, String lang)
    {
        Model model = openModel();
        if (model == null)
        {
            throw BuyerText.error("wizard.closed", 400);
        }
        SiteInfo site = siteInfoService.require();
        String language = site.resolveLang(lang);
        String defaultLang = site.defaultLang();
        // 能不能买按默认语言查就够了；显示用的文字另外按两种语言各取一次
        Goods goods = new Goods(defaultLang, defaultLang);
        JsonNode input = answers != null && answers.isObject() ? answers : JSON.createObjectNode();

        Map<String, Object> accepted = new LinkedHashMap<>();
        List<Field> order = new ArrayList<>();
        Map<String, Group> groupOf = new HashMap<>();
        List<SiteOrderItem> lines = new ArrayList<>();
        long goodsTotal = 0;

        for (Group group : model.groups)
        {
            if (!met(group.showIf, accepted))
            {
                continue;
            }
            for (Field field : group.fields)
            {
                if (!met(field.showIf, accepted))
                {
                    continue;
                }
                if (field.goods != null && goods.one(field.goods) == null)
                {
                    continue;
                }
                String label = pick(field.node.path("title"), language, defaultLang);
                JsonNode a = input.path(field.code);
                Object value;
                if (field.isOptions())
                {
                    value = readOptions(field, a, accepted, goods, label, language, defaultLang);
                }
                else if ("number".equals(field.type))
                {
                    Long n = readLong(a, label, field.min, field.max);
                    value = n;
                    if (n != null && n > 0 && field.goods != null)
                    {
                        lines.add(line(field.goods, null, n));
                    }
                }
                else if ("text".equals(field.type))
                {
                    String text = a.isString() ? a.asString().trim() : "";
                    if (text.length() > MAX_TEXT)
                    {
                        throw BuyerText.error("wizard.tooLong", 400, label, MAX_TEXT);
                    }
                    value = text.isEmpty() ? null : text;
                }
                else if ("date".equals(field.type))
                {
                    String text = a.isString() ? a.asString().trim() : "";
                    if (!text.isEmpty())
                    {
                        try
                        {
                            LocalDate.parse(text);
                        }
                        catch (DateTimeParseException e)
                        {
                            throw BuyerText.error("wizard.badDate", 400, label);
                        }
                    }
                    value = text.isEmpty() ? null : text;
                }
                else
                {
                    Map<String, Map<String, Long>> qty = readGoods(field, a, accepted, model, goods, label);
                    for (Map.Entry<String, Map<String, Long>> g : qty.entrySet())
                    {
                        for (Map.Entry<String, Long> c : g.getValue().entrySet())
                        {
                            lines.add(line(g.getKey(), c.getKey().isEmpty() ? null : c.getKey(), c.getValue()));
                            goodsTotal += c.getValue();
                        }
                    }
                    value = qty.isEmpty() ? null : qty;
                }

                if (value == null)
                {
                    if (field.required)
                    {
                        throw BuyerText.error("wizard.required", 400, label);
                    }
                    continue;
                }
                accepted.put(field.code, value);
                order.add(field);
                groupOf.put(field.code, group);
            }
        }

        if (lines.isEmpty())
        {
            throw BuyerText.error("wizard.noLines", 400);
        }

        // 起订量：选中的选项里带 moq 的，goods 字段数量合计不得低于它
        for (Field field : order)
        {
            if (!field.isOptions())
            {
                continue;
            }
            for (String code : selected(accepted.get(field.code)))
            {
                Opt opt = field.option(code);
                if (opt != null && opt.moq > 0 && goodsTotal < opt.moq)
                {
                    throw BuyerText.error("wizard.moq", 400,
                            pick(opt.node.path("title"), language, defaultLang), opt.moq, goodsTotal);
                }
            }
        }

        String snapshot = snapshot(order, groupOf, accepted, language, defaultLang);
        return new Resolved(lines, snapshot);
    }

    private Object readOptions(Field field, JsonNode a, Map<String, Object> accepted, Goods goods,
            String label, String language, String defaultLang)
    {
        List<String> picked = new ArrayList<>();
        if (a.isArray())
        {
            for (JsonNode v : a.values())
            {
                picked.add(v.asString(""));
            }
        }
        else if (a.isString() && !a.asString().isEmpty())
        {
            picked.add(a.asString());
        }
        if (!field.multi && picked.size() > 1)
        {
            throw BuyerText.error("wizard.badOption", 400, label);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String code : picked)
        {
            Opt opt = field.option(code);
            if (opt == null || opt.hidden || (opt.goods != null && goods.one(opt.goods) == null))
            {
                throw BuyerText.error("wizard.badOption", 400, label);
            }
            if (!met(opt.needs, accepted))
            {
                throw BuyerText.error("wizard.locked", 400, label, pick(opt.node.path("title"), language, defaultLang));
            }
            unique.add(code);
        }
        if (unique.isEmpty())
        {
            return null;
        }
        return field.multi ? new ArrayList<>(unique) : unique.iterator().next();
    }

    private static Long readLong(JsonNode a, String label, long min, long max)
    {
        if (a.isMissingNode() || a.isNull() || (a.isString() && a.asString().isBlank()))
        {
            return null;
        }
        long n;
        if (a.isIntegralNumber() && a.canConvertToLong())
        {
            n = a.longValue();
        }
        else if (a.isString() && a.asString().trim().matches("\\d{1,9}"))
        {
            n = Long.parseLong(a.asString().trim());
        }
        else
        {
            throw BuyerText.error("wizard.badNumber", 400, label, min, max);
        }
        if (n < min || n > max)
        {
            throw BuyerText.error("wizard.badNumber", 400, label, min, max);
        }
        return n;
    }

    /**
     * goods 字段：{商品编码: {型号编码: 数量}}。只收「来源字段里选中了的、上架的商品」，
     * 型号只收对外可选的；没有型号的商品用空串键。数量 0 的格子忽略。
     */
    private Map<String, Map<String, Long>> readGoods(Field field, JsonNode a, Map<String, Object> accepted,
            Model model, Goods goods, String label)
    {
        Field source = null;
        for (Group group : model.groups)
        {
            for (Field f : group.fields)
            {
                if (f.code.equals(field.source))
                {
                    source = f;
                }
            }
        }
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        if (source == null || !a.isObject())
        {
            return result;
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String code : selected(accepted.get(source.code)))
        {
            Opt opt = source.option(code);
            if (opt != null && opt.goods != null)
            {
                allowed.add(opt.goods);
            }
        }
        for (String goodsCode : allowed)
        {
            JsonNode row = a.path(goodsCode);
            if (!row.isObject())
            {
                continue;
            }
            if (goods.one(goodsCode) == null)
            {
                throw BuyerText.error("wizard.badGoods", 400, label);
            }
            Set<String> comboCodes = new LinkedHashSet<>();
            for (SiteGoodsCombo combo : goods.combos(goodsCode))
            {
                comboCodes.add(combo.getComboCode());
            }
            Map<String, Long> qty = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> cell : row.properties())
            {
                String comboCode = cell.getKey();
                boolean ok = comboCodes.isEmpty() ? comboCode.isEmpty() : comboCodes.contains(comboCode);
                if (!ok)
                {
                    throw BuyerText.error("wizard.badGoods", 400, label);
                }
                Long n = readLong(cell.getValue(), label, 0, MAX_QTY);
                if (n != null && n > 0)
                {
                    qty.put(comboCode, n);
                }
            }
            if (!qty.isEmpty())
            {
                result.put(goodsCode, qty);
            }
        }
        return result;
    }

    private static SiteOrderItem line(String goodsCode, String comboCode, long qty)
    {
        SiteOrderItem item = new SiteOrderItem();
        item.setGoodsCode(goodsCode);
        item.setComboCode(comboCode);
        item.setQty(BigDecimal.valueOf(qty));
        return item;
    }

    static boolean met(List<Cond> conds, Map<String, Object> accepted)
    {
        for (Cond cond : conds)
        {
            boolean hit = false;
            for (String value : selected(accepted.get(cond.field())))
            {
                if (cond.in().contains(value))
                {
                    hit = true;
                    break;
                }
            }
            if (!hit)
            {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<String> selected(Object value)
    {
        if (value instanceof String s)
        {
            return List.of(s);
        }
        if (value instanceof List<?> list)
        {
            return (List<String>) list;
        }
        return List.of();
    }

    // ==================== 快照 ====================

    /**
     * 选配快照：每个答了的字段一条，文字按<b>买家语言</b>和<b>中文</b>各存一份。
     *
     * <p>买家那份给回执页 / 买家中心 / 回执邮件；中文那份给管理台和商家通知信
     * （管理台主要是我们自己的业务员在看）。schema 里没写中文的退回英文、再退回默认语言。</p>
     */
    private String snapshot(List<Field> order, Map<String, Group> groupOf, Map<String, Object> accepted,
            String language, String defaultLang)
    {
        Goods buyerGoods = new Goods(language, defaultLang);
        Goods zhGoods = "zh".equals(language) ? buyerGoods : new Goods("zh", defaultLang);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Field field : order)
        {
            Group group = groupOf.get(field.code);
            Object value = accepted.get(field.code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", field.code);
            row.put("raw", value);
            row.put("group", pick(group.node.path("title"), language, defaultLang));
            row.put("field", pick(field.node.path("title"), language, defaultLang));
            row.put("value", display(field, value, language, defaultLang, buyerGoods));
            row.put("groupZh", pick(group.node.path("title"), "zh", defaultLang));
            row.put("fieldZh", pick(field.node.path("title"), "zh", defaultLang));
            row.put("valueZh", display(field, value, "zh", defaultLang, zhGoods));
            rows.add(row);
        }
        return JSON.writeValueAsString(rows);
    }

    @SuppressWarnings("unchecked")
    private static String display(Field field, Object value, String lang, String defaultLang, Goods goods)
    {
        String sep = "zh".equals(lang) || "ja".equals(lang) ? "、" : ", ";
        if (field.isOptions())
        {
            List<String> names = new ArrayList<>();
            for (String code : selected(value))
            {
                Opt opt = field.option(code);
                names.add(opt == null ? code : pick(opt.node.path("title"), lang, defaultLang));
            }
            return String.join(sep, names);
        }
        if (value instanceof Map<?, ?> map)
        {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Map<String, Long>> g : ((Map<String, Map<String, Long>>) map).entrySet())
            {
                SiteGoods one = goods.one(g.getKey());
                String name = one == null ? g.getKey() : firstNonBlank(one.getTitle(), one.getGoodsName());
                Map<String, String> comboNames = new HashMap<>();
                for (SiteGoodsCombo combo : goods.combos(g.getKey()))
                {
                    comboNames.put(combo.getComboCode(), firstNonBlank(combo.getTitle(), combo.getComboCode()));
                }
                List<String> cells = new ArrayList<>();
                for (Map.Entry<String, Long> c : g.getValue().entrySet())
                {
                    cells.add(c.getKey().isEmpty() ? "×" + c.getValue()
                            : comboNames.getOrDefault(c.getKey(), c.getKey()) + " ×" + c.getValue());
                }
                parts.add(name + ": " + String.join(sep, cells));
            }
            return String.join("; ", parts);
        }
        return String.valueOf(value);
    }

    /**
     * 把快照拆成可显示的行。给管理台订单详情、商家通知信、买家那一侧共用。
     *
     * @param zh true 取中文那份（管理台、商家），false 取买家语言那份
     * @return 每行 {group, field, value}；普通购物车的单、或 JSON 坏了，返回空列表
     */
    public static List<Map<String, String>> snapshotRows(String json, boolean zh)
    {
        List<Map<String, String>> rows = new ArrayList<>();
        if (json == null || json.isBlank())
        {
            return rows;
        }
        try
        {
            JsonNode root = JSON.readTree(json);
            if (!root.isArray())
            {
                return rows;
            }
            for (JsonNode node : root.values())
            {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("group", node.path(zh ? "groupZh" : "group").asString(""));
                row.put("field", node.path(zh ? "fieldZh" : "field").asString(""));
                row.put("value", node.path(zh ? "valueZh" : "value").asString(""));
                rows.add(row);
            }
        }
        catch (JacksonException e)
        {
            log.warn("订单选配快照解析失败，按没有处理：{}", e.getOriginalMessage());
        }
        return rows;
    }

    // ==================== 管理台 ====================

    /** 管理台编辑页要的：开关 + 原样的 schema 文本 + 最后改动 */
    public Map<String, Object> adminView()
    {
        OrderWizard row = mapper.selectOne();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", row != null && row.isOn());
        result.put("schemaJson", row == null || row.getSchemaJson() == null ? "" : row.getSchemaJson());
        result.put("updateUser", row == null ? null : row.getUpdateUser());
        result.put("updateTime", row == null ? null : row.getUpdateTime());
        return result;
    }

    /**
     * 存。保存即生效（设计文档「存储」一节：不做草稿两态）。
     *
     * @param enabled    前台开关
     * @param schemaJson 整份向导；开着的时候不能为空
     */
    public void save(Boolean enabled, String schemaJson, String user)
    {
        boolean on = Boolean.TRUE.equals(enabled);
        String json = schemaJson == null ? "" : schemaJson.trim();
        if (json.isEmpty())
        {
            if (on)
            {
                throw new ServiceException("向导内容是空的，不能开启。先点「载入预置模板」或自己搭一份");
            }
        }
        else
        {
            Model model = validateSchema(json);
            // 统一存紧凑格式：管理台显示时自己排版
            json = JSON.writeValueAsString(model.root);
        }
        OrderWizard wizard = new OrderWizard();
        wizard.setEnabled(on ? 1 : 0);
        wizard.setSchemaJson(json.isEmpty() ? null : json);
        wizard.setUpdateUser(user);
        mapper.upsert(wizard);
        cache = null;
        log.info("引导式下单已保存：开关={}，内容 {} 字符，操作人 {}", on, json.length(), user);
    }

    /**
     * 有哪些预置模板，以及哪一份是本站的。管理台的「载入预置模板」按它渲染：
     * 有本站的那份就一键载入它，其余放下拉里；一份都对不上就只给下拉。
     *
     * @return [{code, forThisSite}]，本站那份排第一。名称由管理台按 code 翻译（wizard.preset.*）
     */
    public List<Map<String, Object>> templates()
    {
        String siteCode = siteInfoService.require().getSiteCode();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Preset preset : PRESETS)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", preset.code());
            item.put("forThisSite", preset.siteCodes().contains(siteCode));
            list.add(item);
        }
        list.sort((a, b) -> Boolean.compare((Boolean) b.get("forThisSite"), (Boolean) a.get("forThisSite")));
        return list;
    }

    /**
     * 取一份预置模板。原样返回，管理台载入后照常走一遍保存校验。
     *
     * @param code 模板编码；为空时取本站对应的那份，本站没有对应的就报错（不替人挑别家的）
     */
    public String template(String code)
    {
        Preset preset;
        if (code == null || code.isBlank())
        {
            String siteCode = siteInfoService.require().getSiteCode();
            preset = PRESETS.stream().filter(p -> p.siteCodes().contains(siteCode)).findFirst()
                    .orElseThrow(() -> new ServiceException("本站还没有对应的预置模板，请在下拉里选一份"));
        }
        else
        {
            preset = PRESETS.stream().filter(p -> p.code().equals(code)).findFirst()
                    .orElseThrow(() -> new ServiceException("没有这份预置模板：" + code));
        }
        return readResource(preset.file());
    }

    static String readResource(String file)
    {
        try (InputStream in = OrderWizardService.class.getClassLoader().getResourceAsStream(file))
        {
            if (in == null)
            {
                throw new ServiceException("jar 里没有预置模板（" + file + "）");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new ServiceException("读取预置模板失败：" + e.getMessage());
        }
    }

    // ==================== 工具 ====================

    /** 多语言文字挑一种：当前语言 → 英文 → 站点默认语言 → 随便哪个非空的 */
    static String pick(JsonNode text, String lang, String defaultLang)
    {
        if (text == null || !text.isObject())
        {
            return "";
        }
        for (String key : new String[] { lang, "en", defaultLang })
        {
            String value = text.path(key == null ? "" : key).asString("");
            if (!value.isBlank())
            {
                return value;
            }
        }
        for (JsonNode value : text.values())
        {
            if (value.isString() && !value.asString().isBlank())
            {
                return value.asString();
            }
        }
        return "";
    }

    private static String firstNonBlank(String a, String b)
    {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static String firstImage(SiteGoods goods)
    {
        String siteImgs = goods.getSiteImgs();
        if (siteImgs != null && !siteImgs.isBlank())
        {
            String first = siteImgs.split(",")[0].trim();
            if (!first.isEmpty())
            {
                return first;
            }
        }
        return goods.getImgUrl();
    }

    /**
     * 一次请求内的商品 / 型号查询缓存。只查<b>对外</b>那几条语句（已上架、型号可选）：
     * 向导里挂着一个已经下架的商品，它在前台和复核里都当不存在。
     */
    private final class Goods
    {
        private final String lang;
        private final String defaultLang;
        private final Map<String, SiteGoods> goods = new HashMap<>();
        private final Map<String, List<SiteGoodsCombo>> combos = new HashMap<>();

        Goods(String lang, String defaultLang)
        {
            this.lang = lang;
            this.defaultLang = defaultLang;
        }

        SiteGoods one(String code)
        {
            return goods.computeIfAbsent(code, c -> goodsMapper.selectPublicOne(c, lang, defaultLang));
        }

        List<SiteGoodsCombo> combos(String code)
        {
            return combos.computeIfAbsent(code, c -> {
                List<SiteGoodsCombo> list = comboMapper.selectPublicByGoods(c, lang, defaultLang);
                return list == null ? List.of() : list;
            });
        }
    }
}
