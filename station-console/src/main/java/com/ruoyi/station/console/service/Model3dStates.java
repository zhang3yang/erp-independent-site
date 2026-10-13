package com.ruoyi.station.console.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品 3D 模型状态（{@code u_s_sitegoods.model3d_states}）的校验与下发。
 *
 * <p>一个商品挂多个 glb，前台出一排按钮切换（首个用例：腿套「没气 / 半充 / 充满」）。
 * 设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md。</p>
 *
 * <h3>第一个状态永远是主模型</h3>
 * <p>{@code states[0]} 不存模型地址——它的模型就是商品的 {@code model3d}，标注坐标也只属于它。
 * 其余状态的 logo 由前台从主模型投影过去（{@code station-shared/model3d/states.js}），这里不管。</p>
 *
 * <p>和 {@link Model3dMarks} 同一个道理：这一列会出公网，按白名单重组后再存。</p>
 */
public final class Model3dStates
{
    static final int VERSION = 1;
    static final int MAX_BYTES = 20 * 1024;
    static final int MAX_STATES = 5;
    static final int MAX_NAME = 30;
    static final int MAX_URL = 500;

    private static final Pattern ID = Pattern.compile("^s[0-9]{1,6}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private Model3dStates()
    {
    }

    /**
     * 保存前清洗。不合规直接拒绝，提示说清是第几个状态的哪一项。
     *
     * @param mainModel   商品的主模型地址（已规范化）；空 = 没有主模型，状态没有依附，存 null
     * @param langs       站点开着的语言；名字里别的语言静默丢掉（站点关掉一种语言后，老数据仍能保存）
     * @param defaultLang 站点默认语言，每个状态这个语言的名字必填
     * @return 规范化后的 JSON；没有状态时 null
     */
    public static String normalize(String json, String mainModel, List<String> langs, String defaultLang)
    {
        String text = json == null ? null : json.trim();
        if (text == null || text.isEmpty() || "null".equals(text))
        {
            return null;
        }
        if (mainModel == null || mainModel.isEmpty())
        {
            return null;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
        {
            throw new ServiceException("模型状态内容过大（上限 20KB）");
        }
        Map<String, Object> raw;
        try
        {
            raw = JSON.readValue(text, Map.class);
        }
        catch (Exception e)
        {
            throw new ServiceException("模型状态不是合法的 JSON");
        }
        if (raw == null)
        {
            return null;
        }
        Object v = raw.get("v");
        if (!(v instanceof Number) || ((Number) v).doubleValue() != VERSION)
        {
            throw new ServiceException("模型状态版本不支持");
        }
        Object list = raw.get("states");
        if (list == null)
        {
            return null;
        }
        if (!(list instanceof List))
        {
            throw new ServiceException("模型状态列表格式不对");
        }
        List<?> items = (List<?>) list;
        if (items.isEmpty())
        {
            return null;
        }
        if (items.size() > MAX_STATES)
        {
            throw new ServiceException("模型状态最多 " + MAX_STATES + " 个");
        }

        List<Map<String, Object>> states = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < items.size(); i++)
        {
            String where = "第 " + (i + 1) + " 个模型状态";
            if (!(items.get(i) instanceof Map))
            {
                throw new ServiceException(where + "格式不对");
            }
            Map<?, ?> m = (Map<?, ?>) items.get(i);
            Object id = m.get("id");
            if (!(id instanceof String) || !ID.matcher((String) id).matches())
            {
                throw new ServiceException(where + "的编号格式不对");
            }
            if (!ids.add((String) id))
            {
                throw new ServiceException(where + "的编号重复");
            }
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("id", id);
            state.put("name", names(m.get("name"), langs, defaultLang, where));
            // 第一个是主模型：带了模型地址也丢掉，模型就是商品的 model3d
            if (i > 0)
            {
                String model = glbUrl(m.get("model"), where);
                if (model != null)
                {
                    state.put("model", model);
                }
            }
            states.add(state);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", VERSION);
        out.put("states", states);
        try
        {
            return JSON.writeValueAsString(out);
        }
        catch (Exception e)
        {
            throw new ServiceException("模型状态保存失败");
        }
    }

    /**
     * 下发用：只留第一个 + 有模型的状态，名字换成当前语言（缺译回退默认语言，再退任一非空）。
     * 少于 2 个返回 null——只有一个状态时不出按钮。库里坏了也返回 null，不让接口 500。
     */
    public static List<Map<String, Object>> toPublic(String json, String lang, String defaultLang)
    {
        List<Map<String, Object>> states = usable(json);
        if (states == null)
        {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : states)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.get("id"));
            item.put("name", pickName(s.get("name"), lang, defaultLang, String.valueOf(s.get("id"))));
            if (s.get("model") != null)
            {
                item.put("model", s.get("model"));
            }
            out.add(item);
        }
        return out;
    }

    /**
     * 存定制方案快照用：同 {@link #toPublic} 的筛选，但名字保留全语言（方案页按看的人的语言换名）。
     * 少于 2 个返回 null。
     */
    public static String toSnapshot(String json)
    {
        List<Map<String, Object>> states = usable(json);
        if (states == null)
        {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", VERSION);
        out.put("states", states);
        try
        {
            return JSON.writeValueAsString(out);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Variant state lists for the inflation workflow must contain exactly three usable models. */
    public static void requireThree(String json, String where)
    {
        try
        {
            Map<String, Object> raw = JSON.readValue(json, Map.class);
            Object list = raw == null ? null : raw.get("states");
            if (!(list instanceof List) || ((List<?>) list).size() != 3
                    || !hasModel(((List<?>) list).get(1)) || !hasModel(((List<?>) list).get(2)))
            {
                throw new ServiceException(where + "必须配置完整的三个模型状态（没气、半气、满气）");
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException(where + "必须配置完整的三个模型状态（没气、半气、满气）");
        }
    }

    private static boolean hasModel(Object state)
    {
        if (!(state instanceof Map)) return false;
        Object model = ((Map<?, ?>) state).get("model");
        return model instanceof String && !((String) model).trim().isEmpty();
    }

    /** 解析 + 只留第一个和有模型的；不足 2 个或坏了返回 null */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> usable(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return null;
        }
        try
        {
            Map<String, Object> raw = JSON.readValue(json, Map.class);
            Object list = raw == null ? null : raw.get("states");
            if (!(list instanceof List))
            {
                return null;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            int i = 0;
            for (Object o : (List<?>) list)
            {
                if (!(o instanceof Map))
                {
                    return null;
                }
                Map<String, Object> s = (Map<String, Object>) o;
                if (!(s.get("id") instanceof String))
                {
                    return null;
                }
                if (i == 0)
                {
                    Map<String, Object> first = new LinkedHashMap<>(s);
                    first.remove("model");
                    out.add(first);
                }
                else if (s.get("model") instanceof String)
                {
                    out.add(s);
                }
                i++;
            }
            return out.size() < 2 ? null : out;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String pickName(Object names, String lang, String defaultLang, String fallback)
    {
        if (!(names instanceof Map))
        {
            return fallback;
        }
        Map<?, ?> m = (Map<?, ?>) names;
        for (String key : new String[] { lang, defaultLang })
        {
            Object v = key == null ? null : m.get(key);
            if (v instanceof String && !((String) v).trim().isEmpty())
            {
                return ((String) v).trim();
            }
        }
        for (Object v : m.values())
        {
            if (v instanceof String && !((String) v).trim().isEmpty())
            {
                return ((String) v).trim();
            }
        }
        return fallback;
    }

    private static Map<String, String> names(Object value, List<String> langs, String defaultLang, String where)
    {
        if (!(value instanceof Map))
        {
            throw new ServiceException(where + "的名字格式不对");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet())
        {
            String lang = e.getKey() == null ? null : String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            if (lang == null || !langs.contains(lang))
            {
                continue;
            }
            if (e.getValue() != null && !(e.getValue() instanceof String))
            {
                throw new ServiceException(where + "的名字格式不对");
            }
            String name = e.getValue() == null ? "" : ((String) e.getValue()).trim();
            if (name.isEmpty())
            {
                continue;
            }
            if (name.codePointCount(0, name.length()) > MAX_NAME)
            {
                throw new ServiceException(where + "的名字（" + lang + "）超过 " + MAX_NAME + " 个字");
            }
            out.put(lang, name);
        }
        if (!out.containsKey(defaultLang))
        {
            throw new ServiceException(where + "缺少默认语言（" + defaultLang + "）的名字");
        }
        return out;
    }

    /**
     * .glb 地址校验：同主模型的规则（https:// 或 / 开头）且扩展名是 .glb（忽略查询串）。
     * 组合（规格）各自的模型也走这里（{@code SiteGoodsService#saveCombos}）。
     *
     * @param where 报错时说是哪一个，如「第 2 个模型状态」「组合「C01」」
     * @return 规范化后的地址；空 → null
     */
    static String glbUrl(Object value, String where)
    {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty()))
        {
            return null;
        }
        String text = value instanceof String ? SiteGoodsService.normalizeMediaUrl((String) value) : null;
        if (text == null || text.length() > MAX_URL)
        {
            throw new ServiceException(where + "的模型地址只能是 https:// 或 / 开头的地址");
        }
        int q = text.indexOf('?');
        String path = (q < 0 ? text : text.substring(0, q)).toLowerCase(Locale.ROOT);
        if (!path.endsWith(".glb"))
        {
            throw new ServiceException(where + "的模型只支持 .glb 文件");
        }
        return text;
    }
}
