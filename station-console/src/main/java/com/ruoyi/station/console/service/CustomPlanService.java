package com.ruoyi.station.console.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.CustomPlan;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.CustomPlanMapper;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;

/**
 * 买家 3D 定制方案：前台 {@code /customize/{商品号}} 上贴 logo、换开机画面，保存成一个方案链接。
 * 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md。
 *
 * <h3>买家能决定的只有两样</h3>
 * <p>logo（图、位置、大小、角度、透明度，最多 {@value #MAX_LOGOS} 个）和开机画面（logo 图、底色）。
 * 屏幕和开机键的几何、工作界面图、开机 logo 停留时长——<b>一律从商品当前的标注里取</b>，
 * 请求里带什么都不认。商家自己在商品上贴的 logo 不带进方案：买家定制的就是「换成我的 logo」。</p>
 *
 * <h3>⚠ 图片引用只能是 {@code upload:N}</h3>
 * <p>请求里所有图片位置只能写本次一起上传的第 N 个文件，每个上传的文件也必须被用到。
 * 任何网址（包括本站的）一律拒绝。于是方案里<b>不可能出现外链图片</b>——
 * 方案会在商家管理台里渲染，外链图等于让任何人往商家后台塞追踪像素。</p>
 *
 * <h3>⚠ 全部校验通过之前不写 COS</h3>
 * <p>先用占位地址把整份标注过一遍 {@link Model3dMarks#normalize}，过了才存图、换成真地址、
 * 再过一遍、落库。存了图之后插库失败，已存的对象删不掉（{@code CosClient} 故意没有删除能力），
 * 只能把对象地址记进日志，由运维和其他孤儿对象一起清。</p>
 *
 * <h3>方案号不走发号器</h3>
 * <p>16 位随机 base62。方案里有买家的 logo，查看链接谁拿到都能看，流水号的话挨个数就能翻出别人的。</p>
 */
@Service
public class CustomPlanService
{
    private static final Logger log = LoggerFactory.getLogger(CustomPlanService.class);

    static final int MAX_LOGOS = 5;
    /** logo 5 张 + 开机画面 1 张 */
    static final int MAX_FILES = MAX_LOGOS + 1;
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    static final int MAX_EDGE = 4096;
    static final int MAX_NOTE = 500;
    static final int MAX_EMAIL = 200;
    static final int ID_LENGTH = 16;

    private static final Pattern PLAN_ID = Pattern.compile("^[A-Za-z0-9]{16}$");
    private static final Pattern UPLOAD_REF = Pattern.compile("^upload:(\\d{1,2})$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /** 和下单那条一致（SiteOrderService），三处不一致会出现「前台过了、后端拒了」 */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private CustomPlanMapper planMapper;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteFileService fileService;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    @Autowired
    private SiteOpenService openService;

    @Autowired
    private StationProperties properties;

    // ==================== 前台：定制页数据 ====================

    /**
     * 定制页要的：商品名、主图、模型、商家标注里的屏幕和开机键。
     *
     * @return 商品不存在 / 没上架 / 没模型时 null（控制器回 404）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> editorView(String goodsCode, String lang)
    {
        SiteInfo site = siteInfoService.requirePublic();
        SiteGoods goods = findCustomizable(site, goodsCode, lang);
        if (goods == null)
        {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("goodsCode", goods.getGoodsCode());
        view.put("title", goods.getTitle());
        view.put("image", firstImage(goods));
        view.put("model3d", goods.getModel3d());
        Map<String, Object> marks = Model3dMarks.toObject(goods.getModel3dMarks());
        view.put("screen", marks == null ? null : marks.get("screen"));
        view.put("power", marks == null ? null : marks.get("power"));
        // 模型状态（如腿套没气/半充/充满）：买家的 logo 由前台投影过去，见 states.js
        view.put("states", Model3dStates.toPublic(goods.getModel3dStates(), site.resolveLang(lang), site.defaultLang()));
        // 规格各自的模型（2026-10-09）：只列配了模型的；屏幕/开机键只在单独标注时从规格自己的标注取
        List<Map<String, Object>> combos = new ArrayList<>();
        for (Map<String, Object> card : openService.publicComboCards(goods, lang, site))
        {
            if (card.get("model3d") == null)
            {
                continue;
            }
            Map<String, Object> own = (Map<String, Object>) card.get("model3dMarks");
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", card.get("code"));
            c.put("title", card.get("title"));
            c.put("image", card.get("image"));
            c.put("model3d", card.get("model3d"));
            c.put("model3dStates", card.get("model3dStates"));
            c.put("own", own != null);
            c.put("screen", own == null ? null : own.get("screen"));
            c.put("power", own == null ? null : own.get("power"));
            combos.add(c);
        }
        view.put("combos", combos);
        return view;
    }

    // ==================== 前台：保存方案 ====================

    /**
     * 保存一份方案。调用方已经过了限流和蜜罐。
     *
     * @param data  前台的 JSON 串（见设计文档 §5.2）
     * @param files 本次上传的图，按 {@code upload:N} 的 N 排序
     * @return 方案号
     */
    public String save(String data, List<MultipartFile> files, String clientIp)
    {
        Map<String, Object> req = parse(data);
        List<MultipartFile> uploads = files == null ? List.of() : files;

        if (!Boolean.TRUE.equals(req.get("agree")))
        {
            throw BuyerText.error("custom.agreeRequired", 400);
        }

        SiteInfo site = siteInfoService.requirePublic();
        SiteGoods goods = findCustomizable(site, str(req.get("goodsCode")), null);
        if (goods == null)
        {
            throw BuyerText.error("custom.unavailable", 404);
        }

        // ---- 图片：个数、大小、魔数、能解码、边长 ----
        if (uploads.size() > MAX_FILES)
        {
            throw BuyerText.error("custom.badData", 400);
        }
        List<Image> images = new ArrayList<>(uploads.size());
        for (MultipartFile file : uploads)
        {
            images.add(readImage(file));
        }

        // ---- 其余字段 ----
        String email = optionalEmail(req.get("email"));
        String note = optionalNote(req.get("note"));
        String parentId = str(req.get("from"));
        if (parentId != null && !PLAN_ID.matcher(parentId).matches())
        {
            throw BuyerText.error("custom.badData", 400);
        }

        // ---- 规格（2026-10-09）：带了就必须是这个商品下可选、配了模型的组合；模型和屏幕/开机键全从它取 ----
        SiteGoodsCombo combo = null;
        String comboCode = str(req.get("combo"));
        if (comboCode != null)
        {
            combo = comboCode.length() > 50 ? null
                    : comboMapper.selectPublicOne(goods.getGoodsCode(), comboCode, site.defaultLang(), site.defaultLang());
            if (combo == null || usableModel(combo.getModel3d()) == null)
            {
                throw BuyerText.error("custom.badData", 400);
            }
        }
        String model = combo != null ? combo.getModel3d() : goods.getModel3d();

        // ---- 拼标注：先用占位地址校验一遍，全部通过之前不写 COS ----
        BitSet used = new BitSet();
        // 跟随主模型的规格没有自己的标注 → 没有屏幕，带了开机画面会在 merge 里被拒
        Map<String, Object> merchant = Model3dMarks.toObject(combo != null ? combo.getModel3dMarks() : goods.getModel3dMarks());
        Map<String, Object> merged = merge(model, merchant, req, images.size(), used);
        if (used.cardinality() != images.size())
        {
            // 多传了没用上的文件：不收，省得有人借这个口子往桶里塞东西
            throw BuyerText.error("custom.badData", 400);
        }
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < images.size(); i++)
        {
            placeholders.add("/custom-upload/" + i + "." + images.get(i).ext);
        }
        normalizeOrReject(fill(merged, placeholders));

        // ---- 通过了：存图、换真地址、再校验一遍、落库 ----
        String planId = newPlanId();
        List<String> urls = new ArrayList<>();
        try
        {
            for (int i = 0; i < images.size(); i++)
            {
                Image img = images.get(i);
                urls.add(fileService.storeCustomImage(img.bytes, planId + "-" + i, img.ext, img.contentType));
            }
            String marksJson = normalizeOrReject(fill(merged, urls));

            CustomPlan plan = new CustomPlan();
            plan.setPlanId(planId);
            plan.setShopCode(site.getShopCode());
            plan.setSiteCode(site.getSiteCode());
            plan.setGoodsCode(goods.getGoodsCode());
            plan.setComboCode(combo == null ? null : combo.getComboCode());
            plan.setModel3d(model);
            plan.setMarks(marksJson);
            // 状态只从服务端商品/规格配置取（前端提交的不收），存快照：之后商家换模型，旧方案仍可回显。
            plan.setModel3dStates(combo == null ? Model3dStates.toSnapshot(goods.getModel3dStates())
                    : Model3dStates.toSnapshot(combo.getModel3dStates()));
            plan.setEmail(email);
            plan.setNote(note);
            plan.setParentId(parentId);
            plan.setIpHash(hashIp(clientIp));
            planMapper.insertPlan(plan);
        }
        catch (RuntimeException e)
        {
            if (!urls.isEmpty())
            {
                log.error("定制方案 {} 保存失败，已上传的对象成了孤儿，需人工清理：{}", planId, urls);
            }
            if (e instanceof ServiceException && ((ServiceException) e).getKey() != null)
            {
                throw e;
            }
            log.error("定制方案 {} 保存失败", planId, e);
            throw BuyerText.error("common.error", 500);
        }
        log.info("定制方案 {} 已保存：商品 {}，{} 张图", planId, goods.getGoodsCode(), images.size());
        return planId;
    }

    /**
     * 合并出完整标注（图片位置先留 {@code upload:N} 原样，由 {@link #fill} 换）。
     * 商家的屏幕/开机键整块照搬，只换开机画面的图和底色；买家的 logo 逐项只取认识的字段。
     * 开机画面只要求商家标了屏幕；开机键可有可无（没有时买家靠「模拟开机」按钮看效果）。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> merge(String model, Map<String, Object> merchant, Map<String, Object> req,
            int fileCount, BitSet used)
    {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", 1);
        out.put("model", model);

        // logo
        Object rawLogos = req.get("logos");
        List<Map<String, Object>> logos = new ArrayList<>();
        if (rawLogos != null)
        {
            if (!(rawLogos instanceof List) || ((List<?>) rawLogos).size() > MAX_LOGOS)
            {
                throw BuyerText.error("custom.badData", 400);
            }
            for (Object item : (List<?>) rawLogos)
            {
                if (!(item instanceof Map))
                {
                    throw BuyerText.error("custom.badData", 400);
                }
                Map<String, Object> m = (Map<String, Object>) item;
                Map<String, Object> logo = new LinkedHashMap<>();
                logo.put("id", m.get("id"));
                logo.put("src", uploadRef(m.get("src"), fileCount, used, true));
                logo.put("p", m.get("p"));
                logo.put("n", m.get("n"));
                logo.put("size", m.get("size"));
                logo.put("rot", m.get("rot"));
                logo.put("opacity", m.get("opacity"));
                logos.add(logo);
            }
        }

        // 屏幕 / 开机键：只从商家那份来
        Map<String, Object> screen = merchant == null ? null : (Map<String, Object>) merchant.get("screen");
        Map<String, Object> power = merchant == null ? null : (Map<String, Object>) merchant.get("power");
        boolean bootChanged = false;
        Object rawBoot = req.get("boot");
        if (rawBoot != null)
        {
            if (!(rawBoot instanceof Map) || screen == null)
            {
                // 商品没标屏幕，前台根本不会出现「开机画面」那一块
                throw BuyerText.error("custom.badData", 400);
            }
            Map<String, Object> boot = (Map<String, Object>) rawBoot;
            screen = new LinkedHashMap<>(screen);
            if (boot.get("src") != null)
            {
                screen.put("bootSrc", uploadRef(boot.get("src"), fileCount, used, true));
                bootChanged = true;
            }
            Object bg = boot.get("bg");
            if (bg != null)
            {
                if (!(bg instanceof String) || !COLOR.matcher((String) bg).matches())
                {
                    throw BuyerText.error("custom.badData", 400);
                }
                screen.put("bootBg", ((String) bg).toLowerCase());
                bootChanged = true;
            }
        }

        if (logos.isEmpty() && !bootChanged)
        {
            throw BuyerText.error("custom.empty", 400);
        }
        if (!logos.isEmpty())
        {
            out.put("logos", logos);
        }
        if (screen != null)
        {
            out.put("screen", screen);
        }
        if (power != null)
        {
            out.put("power", power);
        }
        return out;
    }

    /** 图片位置只认 {@code upload:N}，N 在本次文件范围内；记下用过哪几个 */
    private static String uploadRef(Object value, int fileCount, BitSet used, boolean required)
    {
        if (value == null && !required)
        {
            return null;
        }
        Matcher m = value instanceof String ? UPLOAD_REF.matcher((String) value) : null;
        if (m == null || !m.matches())
        {
            throw BuyerText.error("custom.badData", 400);
        }
        int index = Integer.parseInt(m.group(1));
        if (index >= fileCount)
        {
            throw BuyerText.error("custom.badData", 400);
        }
        used.set(index);
        return (String) value;
    }

    /** 把 {@code upload:N} 换成第 N 个地址。返回新对象，不动入参（占位和真地址各换一次） */
    @SuppressWarnings("unchecked")
    static Map<String, Object> fill(Map<String, Object> merged, List<String> urls)
    {
        Map<String, Object> out = new LinkedHashMap<>(merged);
        Object logos = merged.get("logos");
        if (logos instanceof List)
        {
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Object o : (List<?>) logos)
            {
                Map<String, Object> logo = new LinkedHashMap<>((Map<String, Object>) o);
                logo.put("src", resolve(logo.get("src"), urls));
                copy.add(logo);
            }
            out.put("logos", copy);
        }
        Object screen = merged.get("screen");
        if (screen instanceof Map)
        {
            Map<String, Object> s = new LinkedHashMap<>((Map<String, Object>) screen);
            s.put("bootSrc", resolve(s.get("bootSrc"), urls));
            if (s.get("bootSrc") == null)
            {
                s.remove("bootSrc");
            }
            out.put("screen", s);
        }
        return out;
    }

    private static Object resolve(Object src, List<String> urls)
    {
        if (src instanceof String)
        {
            Matcher m = UPLOAD_REF.matcher((String) src);
            if (m.matches())
            {
                return urls.get(Integer.parseInt(m.group(1)));
            }
        }
        // 商家原来的开机图（买家没换）：原样
        return src;
    }

    /**
     * 过一遍商品标注那套校验（范围、白名单重组）。它抛的是给商家看的中文，
     * 买家看不懂也不该看——记日志，对外换成一句通用的。编辑器产出的数据不会走到这里。
     */
    private static String normalizeOrReject(Map<String, Object> marks)
    {
        try
        {
            String json = Model3dMarks.normalize(JSON.writeValueAsString(marks));
            if (json == null)
            {
                throw BuyerText.error("custom.empty", 400);
            }
            return json;
        }
        catch (ServiceException e)
        {
            if (e.getKey() != null)
            {
                throw e;
            }
            log.info("定制方案标注校验不过：{}", e.getMessage());
            throw BuyerText.error("custom.badData", 400);
        }
        catch (RuntimeException e)
        {
            log.info("定制方案标注序列化失败：{}", e.getMessage());
            throw BuyerText.error("custom.badData", 400);
        }
    }

    // ==================== 前台：读方案 ====================

    /**
     * 公开的方案视图。<b>不含 email / note</b>：链接谁拿到都能看。
     *
     * @return 不存在或 id 格式不对时 null（格式不对不查库）
     */
    public Map<String, Object> publicView(String planId, String lang)
    {
        if (planId == null || !PLAN_ID.matcher(planId).matches())
        {
            return null;
        }
        CustomPlan plan = planMapper.selectById(planId);
        if (plan == null)
        {
            return null;
        }
        SiteInfo site = siteInfoService.requirePublic();
        // 商品名按当前语言；商品下架了就用档案名兜底，方案照样能看
        SiteGoods goods = goodsMapper.selectPublicOne(plan.getGoodsCode(), site.resolveLang(lang), site.defaultLang());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("planId", plan.getPlanId());
        view.put("goodsCode", plan.getGoodsCode());
        view.put("title", goods != null ? goods.getTitle() : plan.getGoodsTitle());
        view.put("available", goods != null && usableModel(goods.getModel3d()) != null);
        view.put("currentModel3d", goods == null ? null : goods.getModel3d());
        view.put("model3d", plan.getModel3d());
        view.put("marks", Model3dMarks.toObject(plan.getMarks()));
        // 规格：编码照给；名称按当前语言，组合删了/停用了就只有编码
        view.put("comboCode", plan.getComboCode());
        SiteGoodsCombo planCombo = plan.getComboCode() == null ? null
                : comboMapper.selectPublicOne(plan.getGoodsCode(), plan.getComboCode(), site.resolveLang(lang), site.defaultLang());
        view.put("comboTitle", planCombo == null ? null : planCombo.getTitle());
        view.put("comboAvailable", planCombo != null && usableModel(planCombo.getModel3d()) != null);
        view.put("states", Model3dStates.toPublic(plan.getModel3dStates(), site.resolveLang(lang), site.defaultLang()));
        view.put("createTime", plan.getCreatetime());
        view.put("parentId", plan.getParentId());
        return view;
    }

    // ==================== 管理台 ====================

    /** 管理台列表。{@code from}/{@code to} 是 yyyy-MM-dd，含两端 */
    public Map<String, Object> selectList(String goodsCode, String from, String to, Integer pageNum, Integer pageSize)
    {
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        String code = trimToNull(goodsCode);
        Date start = day(from, 0);
        Date end = day(to, 1);

        int total = planMapper.countList(code, start, end);
        List<CustomPlan> rows = total == 0 ? List.of() : planMapper.selectList(code, start, end, (current - 1) * size, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    /** 管理台详情：整行（带 email / note）+ 解析后的标注 */
    public Map<String, Object> selectDetail(String planId)
    {
        if (planId == null || !PLAN_ID.matcher(planId).matches())
        {
            throw new ServiceException("方案不存在");
        }
        CustomPlan plan = planMapper.selectById(planId);
        if (plan == null)
        {
            throw new ServiceException("方案不存在");
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("plan", plan);
        view.put("marks", Model3dMarks.toObject(plan.getMarks()));
        return view;
    }

    // ==================== 小工具 ====================

    private SiteGoods findCustomizable(SiteInfo site, String goodsCode, String lang)
    {
        String code = trimToNull(goodsCode);
        if (code == null || code.length() > 50)
        {
            return null;
        }
        SiteGoods goods = goodsMapper.selectPublicOne(code, site.resolveLang(lang), site.defaultLang());
        if (goods == null || usableModel(goods.getModel3d()) == null)
        {
            return null;
        }
        return goods;
    }

    /** 和前台 modelUrl 判定一致：https:// 或 / 开头才算有模型 */
    private static String usableModel(String url)
    {
        String u = trimToNull(url);
        return u != null && (u.startsWith("https://") || u.startsWith("/")) ? u : null;
    }

    private static String firstImage(SiteGoods goods)
    {
        for (String group : new String[] { goods.getSiteImgs(), goods.getImgUrl(), goods.getMainImgs() })
        {
            String g = trimToNull(group);
            if (g != null)
            {
                String first = trimToNull(g.split(",")[0]);
                if (first != null)
                {
                    return first;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String data)
    {
        if (data == null || data.isBlank() || data.length() > 64 * 1024)
        {
            throw BuyerText.error("custom.badData", 400);
        }
        try
        {
            Object value = JSON.readValue(data, Object.class);
            if (value instanceof Map)
            {
                return (Map<String, Object>) value;
            }
        }
        catch (RuntimeException e)
        {
            // 落到下面
        }
        throw BuyerText.error("custom.badData", 400);
    }

    /** 蜜罐：前台的隐藏字段 website 有值 = 机器人。给控制器判用，这里不处理 */
    public static boolean isHoneypot(String data)
    {
        try
        {
            Object value = JSON.readValue(data, Object.class);
            if (value instanceof Map)
            {
                Object w = ((Map<?, ?>) value).get("website");
                return w instanceof String && !((String) w).isBlank();
            }
        }
        catch (RuntimeException e)
        {
            // 解析不了就不是蜜罐的事，交给 save 去拒
        }
        return false;
    }

    private static String optionalEmail(Object value)
    {
        String email = value instanceof String ? trimToNull((String) value) : null;
        if (email == null)
        {
            return null;
        }
        email = email.toLowerCase();
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches())
        {
            throw BuyerText.error("order.badEmail", 400);
        }
        return email;
    }

    /** 备注：去掉控制字符（保留换行），限长 */
    private static String optionalNote(Object value)
    {
        String note = value instanceof String ? (String) value : null;
        if (note == null)
        {
            return null;
        }
        note = note.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (note.isEmpty())
        {
            return null;
        }
        if (note.codePointCount(0, note.length()) > MAX_NOTE)
        {
            throw BuyerText.error("custom.noteTooLong", 400, MAX_NOTE);
        }
        return note;
    }

    /** 一张验过的图 */
    static final class Image
    {
        final byte[] bytes;
        final String ext;
        final String contentType;

        Image(byte[] bytes, String ext, String contentType)
        {
            this.bytes = bytes;
            this.ext = ext;
            this.contentType = contentType;
        }
    }

    /**
     * 验图：≤ 2MB；按<b>文件头</b>认 PNG / JPEG（扩展名和 Content-Type 都是客户端说了算，不作数）；
     * 先只读头拿宽高（不解码，防解压炸弹），≤ 4096 才真解码一次，解不开也拒。
     * SVG（能夹脚本）、GIF、webp 在魔数那一步就被挡掉。
     */
    static Image readImage(MultipartFile file)
    {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES)
        {
            throw BuyerText.error("custom.badFile", 400);
        }
        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (IOException e)
        {
            throw BuyerText.error("custom.badFile", 400);
        }
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES)
        {
            throw BuyerText.error("custom.badFile", 400);
        }
        String ext;
        String type;
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
        {
            ext = "png";
            type = "image/png";
        }
        else if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF)
        {
            ext = "jpg";
            type = "image/jpeg";
        }
        else
        {
            throw BuyerText.error("custom.badFile", 400);
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
        {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext())
            {
                throw BuyerText.error("custom.badFile", 400);
            }
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_EDGE || h > MAX_EDGE)
                {
                    throw BuyerText.error("custom.badFile", 400);
                }
                if (reader.read(0) == null)
                {
                    throw BuyerText.error("custom.badFile", 400);
                }
            }
            finally
            {
                reader.dispose();
            }
        }
        catch (IOException | RuntimeException e)
        {
            if (e instanceof ServiceException)
            {
                throw (ServiceException) e;
            }
            throw BuyerText.error("custom.badFile", 400);
        }
        return new Image(bytes, ext, type);
    }

    static String newPlanId()
    {
        char[] out = new char[ID_LENGTH];
        for (int i = 0; i < ID_LENGTH; i++)
        {
            out[i] = BASE62[RANDOM.nextInt(BASE62.length)];
        }
        return new String(out);
    }

    /** IP 的 HMAC-SHA256，排查限流用；不存明文。密钥用主配置密钥，没配就用固定串（仍比明文强） */
    private String hashIp(String clientIp)
    {
        String ip = clientIp == null ? "" : clientIp.trim();
        String secret = trimToNull(properties.getConfigSecret());
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((secret == null ? "station-customplan" : secret)
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Date day(String text, int plusDays)
    {
        String t = trimToNull(text);
        if (t == null)
        {
            return null;
        }
        try
        {
            return Date.from(LocalDate.parse(t).plusDays(plusDays).atStartOfDay(ZONE).toInstant());
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("日期格式不对，应为 yyyy-MM-dd");
        }
    }

    private static String str(Object value)
    {
        return value instanceof String ? trimToNull((String) value) : null;
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
