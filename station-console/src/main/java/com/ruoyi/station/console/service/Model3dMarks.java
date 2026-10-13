package com.ruoyi.station.console.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品 3D 标注（{@code u_s_sitegoods.model3d_marks}）的校验与下发。
 *
 * <p>标注是商家在管理台上用鼠标点在模型上的 logo 贴花、屏幕、开机键，存成一段 JSON，
 * 前台加载模型后按它现场叠加。设计：docs/superpowers/specs/2026-09-25-product-3d-marks-design.md。</p>
 *
 * <h3>为什么按白名单重新序列化，而不是校验过就原样存</h3>
 * <p>这一列会原样出公网（{@link SiteOpenService} 的详情接口）。原样存等于允许借道塞任意字段给前台；
 * 重新组装一遍，库里就只可能有这里认识的键。</p>
 *
 * <p>范围和前台 {@code station-shared/model3d/marks.js} 的 {@code parseMarks} 是同一套——
 * 改一边要改另一边。编辑器产出的数据不会触发这里的拒绝，拒绝只防篡改和 bug。</p>
 */
public final class Model3dMarks
{
    static final int VERSION = 1;
    static final int MAX_BYTES = 20 * 1024;
    static final int MAX_LOGOS = 10;
    static final int MAX_URL = 500;
    static final double MAX_COORD = 10000;
    static final int MAX_BOOT_MS = 10000;
    static final int DEFAULT_BOOT_MS = 1500;
    static final String DEFAULT_BOOT_BG = "#000000";

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private Model3dMarks()
    {
    }

    /**
     * 保存前清洗。空白 → null；不合规直接拒绝（{@link ServiceException}），不静默丢——
     * 静默丢的结果是「后台标了、前台没有」，最难查。
     *
     * @return 规范化后的 JSON；三样都没有时返回 null
     */
    public static String normalize(String json)
    {
        String text = json == null ? null : json.trim();
        if (text == null || text.isEmpty() || "null".equals(text))
        {
            return null;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
        {
            throw new ServiceException("3D 标注内容过大（上限 20KB）");
        }
        Map<String, Object> raw;
        try
        {
            raw = JSON.readValue(text, Map.class);
        }
        catch (Exception e)
        {
            throw new ServiceException("3D 标注不是合法的 JSON");
        }
        Map<String, Object> clean = rebuild(raw);
        if (clean == null)
        {
            return null;
        }
        try
        {
            return JSON.writeValueAsString(clean);
        }
        catch (Exception e)
        {
            throw new ServiceException("3D 标注保存失败");
        }
    }

    /**
     * 下发用：库里的字符串 → 对象。库里的值都经过 {@link #normalize}，这里坏了
     * （比如有人手改过库）就当没有标注，不让详情接口 500。
     */
    public static Map<String, Object> toObject(String json)
    {
        try
        {
            String clean = normalize(json);
            return clean == null ? null : JSON.readValue(clean, Map.class);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Map<String, Object> rebuild(Map<String, Object> raw)
    {
        if (raw == null)
        {
            return null;
        }
        Object v = raw.get("v");
        if (!(v instanceof Number) || ((Number) v).doubleValue() != VERSION)
        {
            throw new ServiceException("3D 标注版本不支持");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", VERSION);
        String model = url(raw.get("model"), "模型地址", false);
        if (model != null)
        {
            out.put("model", model);
        }

        List<Map<String, Object>> logos = logos(raw.get("logos"));
        Map<String, Object> screen = screen(raw.get("screen"));
        Map<String, Object> power = power(raw.get("power"));
        if (logos.isEmpty() && screen == null && power == null)
        {
            return null;
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

    private static List<Map<String, Object>> logos(Object value)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value == null)
        {
            return out;
        }
        if (!(value instanceof List))
        {
            throw new ServiceException("3D 标注的 logo 列表格式不对");
        }
        List<?> list = (List<?>) value;
        if (list.size() > MAX_LOGOS)
        {
            throw new ServiceException("logo 最多 " + MAX_LOGOS + " 个");
        }
        int i = 0;
        for (Object item : list)
        {
            Map<String, Object> m = map(item, "logo");
            Map<String, Object> logo = new LinkedHashMap<>();
            Object id = m.get("id");
            logo.put("id", id instanceof String && ID.matcher((String) id).matches() ? id : "l" + i);
            logo.put("src", url(m.get("src"), "logo 图片地址", true));
            logo.put("p", point(m.get("p")));
            logo.put("n", normal(m.get("n")));
            logo.put("size", positive(m.get("size"), "logo 大小"));
            logo.put("rot", range(m.get("rot"), -180, 180, 0, "logo 旋转角度"));
            logo.put("opacity", range(m.get("opacity"), 0.1, 1, 1, "logo 透明度"));
            out.add(logo);
            i++;
        }
        return out;
    }

    private static Map<String, Object> screen(Object value)
    {
        if (value == null)
        {
            return null;
        }
        Map<String, Object> m = map(value, "屏幕");
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("p", point(m.get("p")));
        s.put("n", normal(m.get("n")));
        s.put("w", positive(m.get("w"), "屏幕宽度"));
        s.put("h", positive(m.get("h"), "屏幕高度"));
        s.put("rot", range(m.get("rot"), -180, 180, 0, "屏幕旋转角度"));
        String boot = url(m.get("bootSrc"), "开机 logo 图片地址", false);
        String run = url(m.get("runSrc"), "工作界面图片地址", false);
        if (boot == null && run == null)
        {
            throw new ServiceException("屏幕至少要选开机 logo 或工作界面其中一张图");
        }
        if (boot != null)
        {
            s.put("bootSrc", boot);
        }
        Object bg = m.get("bootBg");
        if (bg != null && !(bg instanceof String && COLOR.matcher((String) bg).matches()))
        {
            throw new ServiceException("屏幕背景色格式不对");
        }
        s.put("bootBg", bg == null ? DEFAULT_BOOT_BG : ((String) bg).toLowerCase());
        s.put("bootMs", (int) Math.round(range(m.get("bootMs"), 0, MAX_BOOT_MS, DEFAULT_BOOT_MS, "开机 logo 停留时间")));
        if (run != null)
        {
            s.put("runSrc", run);
        }
        return s;
    }

    private static Map<String, Object> power(Object value)
    {
        if (value == null)
        {
            return null;
        }
        Map<String, Object> m = map(value, "开机键");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("p", point(m.get("p")));
        p.put("n", normal(m.get("n")));
        p.put("r", positive(m.get("r"), "开机键光圈大小"));
        return p;
    }

    /* ---------------- 基本类型 ---------------- */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String what)
    {
        if (!(value instanceof Map))
        {
            throw new ServiceException("3D 标注的" + what + "格式不对");
        }
        return (Map<String, Object>) value;
    }

    private static String url(Object value, String what, boolean required)
    {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty()))
        {
            if (required)
            {
                throw new ServiceException(what + "不能为空");
            }
            return null;
        }
        String text = value instanceof String ? SiteGoodsService.normalizeMediaUrl((String) value) : null;
        if (text == null || text.length() > MAX_URL)
        {
            throw new ServiceException(what + "只能是 https:// 或 / 开头的地址");
        }
        return text;
    }

    private static double num(Object value, String what)
    {
        if (!(value instanceof Number))
        {
            throw new ServiceException("3D 标注的" + what + "不是数字");
        }
        double d = ((Number) value).doubleValue();
        if (!Double.isFinite(d))
        {
            throw new ServiceException("3D 标注的" + what + "不是有效数字");
        }
        return d;
    }

    private static double positive(Object value, String what)
    {
        double d = num(value, what);
        if (d <= 0 || d > MAX_COORD)
        {
            throw new ServiceException(what + "超出范围");
        }
        return d;
    }

    private static double range(Object value, double min, double max, double fallback, String what)
    {
        if (value == null)
        {
            return fallback;
        }
        double d = num(value, what);
        if (d < min || d > max)
        {
            throw new ServiceException(what + "超出范围");
        }
        return d;
    }

    private static List<Double> vec3(Object value, String what)
    {
        if (!(value instanceof List) || ((List<?>) value).size() != 3)
        {
            throw new ServiceException("3D 标注的" + what + "格式不对");
        }
        List<Double> out = new ArrayList<>(3);
        for (Object o : (List<?>) value)
        {
            double d = num(o, what);
            if (Math.abs(d) > MAX_COORD)
            {
                throw new ServiceException("3D 标注的" + what + "超出范围");
            }
            out.add(d);
        }
        return out;
    }

    private static List<Double> point(Object value)
    {
        return vec3(value, "位置");
    }

    /** 法线：非零，存单位化之后的值——前台直接拿来当朝向 */
    private static List<Double> normal(Object value)
    {
        List<Double> n = vec3(value, "朝向");
        double len = Math.sqrt(n.get(0) * n.get(0) + n.get(1) * n.get(1) + n.get(2) * n.get(2));
        if (len < 1e-9)
        {
            throw new ServiceException("3D 标注的朝向不能是零向量");
        }
        return List.of(n.get(0) / len, n.get(1) / len, n.get(2) / len);
    }
}
