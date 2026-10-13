package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品组合的规格维度（{@code u_s_sitegoods.optionsjson}）。
 *
 * <p>一个商品最多两个维度（比如「尺码」「款式」），每个维度若干取值；
 * 一条组合（SKU）用 {@code opt1 / opt2} 记它是哪两个取值的搭配。
 * 为什么是 JSON、为什么还是那张组合表，写在
 * {@code sql/station/migration/2026-09-25-goods-options.sql} 文件头。</p>
 *
 * <p>JSON 形状：</p>
 * <pre>
 * {"dims":[
 *   {"code":"size","names":{"zh":"尺码","en":"Size"},
 *    "values":[{"code":"SM","names":{"zh":"S/M"}}, ...]},
 *   {"code":"style","names":{"zh":"款式"},
 *    "values":[{"code":"BLK","names":{"zh":"黑色"},"image":"https://..."}]}
 * ]}
 * </pre>
 *
 * <p>这个类只做三件事：读进来（{@link #parse}）、按站点规则校验整理（{@link #normalize}）、
 * 按语言取名字（{@link #dimName} / {@link #valueName}）。不碰数据库。</p>
 */
public final class GoodsOptions
{
    public static final int MAX_DIMS = 2;
    public static final int MAX_VALUES = 40;

    /**
     * 编码只许字母数字和 - _，最长 20。
     *
     * <p>二维模式下组合编码是「取值编码-取值编码」拼出来的（管理台那边拼），
     * 组合编码列是 varchar(50)：20 + 1 + 20 装得下。
     * 不许空格和斜杠：组合编码会进深链 {@code ?combo=}，也会出现在订单明细里给人读。</p>
     */
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");

    /** 名称拼接符。组合名 = 第一维名 + 这个 + 第二维名，如「S/M · 黑色」 */
    public static final String JOIN = " · ";

    public static final class Value
    {
        public String code;
        public Map<String, String> names = new LinkedHashMap<>();
        public String image;
    }

    public static final class Dim
    {
        public String code;
        public Map<String, String> names = new LinkedHashMap<>();
        public List<Value> values = new ArrayList<>();
    }

    private final List<Dim> dims;

    private GoodsOptions(List<Dim> dims)
    {
        this.dims = dims;
    }

    public List<Dim> dims()
    {
        return dims;
    }

    public int size()
    {
        return dims.size();
    }

    // ==================== 读 ====================

    /**
     * 把库里 / 前端提交的 JSON 读成对象。空串、null、{@code {"dims":[]}} 都返回 null
     * （= 平铺模式）。
     *
     * <p>这一步<b>不校验</b>业务规则，只管结构读得出来；读不出来抛
     * {@link IllegalArgumentException}，调用方决定是报错（保存时）还是降级（对外展示时）。</p>
     */
    public static GoodsOptions parse(String json, ObjectMapper mapper)
    {
        String text = json == null ? null : json.trim();
        if (text == null || text.isEmpty())
        {
            return null;
        }
        Map<String, Object> root;
        try
        {
            root = mapper.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {});
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("规格维度不是合法的 JSON");
        }
        Object rawDims = root == null ? null : root.get("dims");
        if (!(rawDims instanceof List) || ((List<?>) rawDims).isEmpty())
        {
            return null;
        }
        List<Dim> dims = new ArrayList<>();
        for (Object rawDim : (List<?>) rawDims)
        {
            if (!(rawDim instanceof Map))
            {
                continue;
            }
            Map<?, ?> d = (Map<?, ?>) rawDim;
            Dim dim = new Dim();
            dim.code = trim(d.get("code"));
            dim.names = names(d.get("names"));
            Object rawValues = d.get("values");
            if (rawValues instanceof List)
            {
                for (Object rawValue : (List<?>) rawValues)
                {
                    if (!(rawValue instanceof Map))
                    {
                        continue;
                    }
                    Map<?, ?> v = (Map<?, ?>) rawValue;
                    Value value = new Value();
                    value.code = trim(v.get("code"));
                    value.names = names(v.get("names"));
                    value.image = trim(v.get("image"));
                    dim.values.add(value);
                }
            }
            dims.add(dim);
        }
        return dims.isEmpty() ? null : new GoodsOptions(dims);
    }

    // ==================== 校验整理（保存时） ====================

    /**
     * 按站点规则校验并整理，不合规直接抛 {@link ServiceException}（带给商家看的中文原因）。
     *
     * <ul>
     *   <li>最多两个维度，每个维度至少一个取值；</li>
     *   <li>维度编码、同一维度里的取值编码不许重复，只许字母数字 - _；</li>
     *   <li><b>默认语言的名称必须有</b>——维度名和每个取值名都是。没有的话详情页那一格是空的，
     *       而组合名是拿它们拼的，拼出来也是空的，前台会把整条组合丢掉；</li>
     *   <li>站点没开的语言的名称丢掉，理由同商品文案：存了也没人读得到；</li>
     *   <li>图片只认 https:// 和 / 开头，理由同 model3d（http 在 https 页面上会被拦）。</li>
     * </ul>
     */
    public GoodsOptions normalize(Collection<String> siteLangs, String defaultLang)
    {
        if (dims.size() > MAX_DIMS)
        {
            throw new ServiceException("组合最多两个维度（比如「尺码」和「款式」）");
        }
        Set<String> allowed = new LinkedHashSet<>(siteLangs);
        Set<String> dimCodes = new LinkedHashSet<>();
        int di = 0;
        for (Dim dim : dims)
        {
            di++;
            String label = "第 " + di + " 个维度";
            if (dim.code == null || !CODE.matcher(dim.code).matches())
            {
                throw new ServiceException(label + "的编码只能是 1~20 位字母、数字、- 或 _");
            }
            if (!dimCodes.add(dim.code))
            {
                throw new ServiceException("维度编码重复：" + dim.code);
            }
            dim.names = keepLangs(dim.names, allowed);
            if (dim.names.get(defaultLang) == null)
            {
                throw new ServiceException(label + "还没有起名（" + defaultLang + "），比如「尺码」「款式」");
            }
            label = "「" + dim.names.get(defaultLang) + "」";
            if (dim.values.isEmpty())
            {
                throw new ServiceException(label + "至少要有一个取值");
            }
            if (dim.values.size() > MAX_VALUES)
            {
                throw new ServiceException(label + "的取值太多了（最多 " + MAX_VALUES + " 个）");
            }
            Set<String> valueCodes = new LinkedHashSet<>();
            for (Value value : dim.values)
            {
                if (value.code == null || !CODE.matcher(value.code).matches())
                {
                    throw new ServiceException(label + "里有取值的编码不对：只能是 1~20 位字母、数字、- 或 _");
                }
                if (!valueCodes.add(value.code))
                {
                    throw new ServiceException(label + "里的取值编码重复：" + value.code);
                }
                value.names = keepLangs(value.names, allowed);
                if (value.names.get(defaultLang) == null)
                {
                    throw new ServiceException(label + "里的取值「" + value.code + "」还没有起名（" + defaultLang + "）");
                }
                if (value.image != null && !(value.image.startsWith("https://") || value.image.startsWith("/")))
                {
                    value.image = null;
                }
            }
        }
        return this;
    }

    public String toJson(ObjectMapper mapper)
    {
        List<Map<String, Object>> outDims = new ArrayList<>();
        for (Dim dim : dims)
        {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("code", dim.code);
            d.put("names", dim.names);
            List<Map<String, Object>> values = new ArrayList<>();
            for (Value value : dim.values)
            {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("code", value.code);
                v.put("names", value.names);
                if (value.image != null)
                {
                    v.put("image", value.image);
                }
                values.add(v);
            }
            d.put("values", values);
            outDims.add(d);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("dims", outDims);
        try
        {
            return mapper.writeValueAsString(root);
        }
        catch (Exception e)
        {
            throw new ServiceException("规格维度保存失败：" + e.getMessage());
        }
    }

    // ==================== 查 ====================

    /** 第 index 个维度（0 起）里有没有这个取值 */
    public boolean hasValue(int index, String code)
    {
        return find(index, code) != null;
    }

    public Value find(int index, String code)
    {
        if (code == null || index < 0 || index >= dims.size())
        {
            return null;
        }
        for (Value value : dims.get(index).values)
        {
            if (code.equals(value.code))
            {
                return value;
            }
        }
        return null;
    }

    /** 维度名：请求语言 → 站点默认语言 → 编码 */
    public String dimName(int index, String lang, String defaultLang)
    {
        Dim dim = dims.get(index);
        return pick(dim.names, lang, defaultLang, dim.code);
    }

    /** 取值名：请求语言 → 站点默认语言 → 编码。取值不存在返回 null */
    public String valueName(int index, String code, String lang, String defaultLang)
    {
        Value value = find(index, code);
        return value == null ? null : pick(value.names, lang, defaultLang, value.code);
    }

    /**
     * 组合名：各维度取值名用「 · 」连起来，如「S/M · 黑色」。
     * 有一个取值找不到就返回 null（那条组合不该显示）。
     */
    public String comboTitle(String opt1, String opt2, String lang, String defaultLang)
    {
        String first = valueName(0, opt1, lang, defaultLang);
        if (first == null)
        {
            return null;
        }
        if (dims.size() < 2)
        {
            return first;
        }
        String second = valueName(1, opt2, lang, defaultLang);
        return second == null ? null : first + JOIN + second;
    }

    // ==================== 内部 ====================

    private static String pick(Map<String, String> names, String lang, String defaultLang, String fallback)
    {
        String hit = names.get(lang);
        if (hit == null)
        {
            hit = names.get(defaultLang);
        }
        return hit == null ? fallback : hit;
    }

    private static Map<String, String> keepLangs(Map<String, String> names, Set<String> allowed)
    {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet())
        {
            if (allowed.contains(entry.getKey()) && entry.getValue() != null)
            {
                out.put(entry.getKey(), cut(entry.getValue(), 60));
            }
        }
        return out;
    }

    private static Map<String, String> names(Object raw)
    {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
            {
                String lang = trim(entry.getKey());
                String name = trim(entry.getValue());
                if (lang != null && name != null)
                {
                    out.put(lang, name);
                }
            }
        }
        return out;
    }

    private static String trim(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String cut(String value, int max)
    {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
