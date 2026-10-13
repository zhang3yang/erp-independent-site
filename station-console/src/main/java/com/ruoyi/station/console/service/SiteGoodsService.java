package com.ruoyi.station.console.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteGoodsLang;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.domain.SiteGoodsComboLang;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;

/**
 * 站点商品（后台管理用）。
 *
 * <h3>⚠ 和 ERP 那版最大的区别：从「挑」变成「录」</h3>
 * <p>ERP 那版的 {@code addGoods(String[] goodsCodes)} 是「把 ERP 商品档案里的这几个
 * 加进本站」：先 {@code InventoryGoodsMapper.selectGoodsByCode} 查档案，
 * 带出标准售价和单位，再落一行草稿。</p>
 *
 * <p>那条路整个没有了（docs/独立站与ERP的边界.md §5.4）——它读的是 ERP 的库，
 * 是一条互通。现在是 {@link #createGoods}：<b>站点自己录一个完整的商品</b>，
 * 名称、规格、图片、价格全在本站填。</p>
 *
 * <p>代价是同一个商品在 ERP 和各个站里各录一遍。这是要点 1 的直接成本，一期认了。</p>
 *
 * <h3>和对外的 {@link SiteOpenService} 分成两个 Service</h3>
 * <p>这里返回草稿和已下架的行，那边永远只返回已上架的。分开写是为了让
 * 「哪些数据能出公网」这件事一眼看得出来。</p>
 */
@Service
public class SiteGoodsService
{
    private static final Set<String> STATUSES = new LinkedHashSet<>(Arrays.asList(
            SiteGoods.STATUS_DRAFT, SiteGoods.STATUS_PUBLISHED, SiteGoods.STATUS_OFFLINE));

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    /** 后台列表，含草稿和已下架 */
    public List<SiteGoods> selectList(String keyword, String publishStatus, String category)
    {
        SiteInfo site = siteInfoService.require();
        return goodsMapper.selectAdminList(site.defaultLang(),
                trimToNull(keyword), trimToNull(publishStatus), trimToNull(category));
    }

    /** 单条详情：商品档案 + 全部语言文案 + 本站开了哪几种语言 */
    public Map<String, Object> selectDetail(String goodsCode)
    {
        SiteInfo site = siteInfoService.require();
        SiteGoods goods = goodsMapper.selectAdminOne(trimToNull(goodsCode));
        if (goods == null)
        {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goods", goods);
        result.put("langs", goodsMapper.selectLangs(goods.getGoodsCode()));
        // 组合。主表一份（价格/图/排序/状态），名称按 combocode + lang 单独一份，
        // 弹窗里前者是表格的行、后者是每行在各语言页签下的那一格
        result.put("combos", comboMapper.selectAdminByGoods(goods.getGoodsCode(), site.defaultLang()));
        result.put("comboLangs", comboMapper.selectLangsByGoods(goods.getGoodsCode()));
        // 站点开了哪几种语言，编辑弹窗按它生成页签
        result.put("siteLangs", site.langList());
        return result;
    }

    /**
     * 新建一个商品。
     *
     * <p>替代 ERP 那版的「批量把 ERP 商品加进站点」。入参从
     * {@code List<String> goodsCodes} 变成一个完整的 {@link SiteGoods}——
     * 这是 §5.4 那个改动在接口上的样子。</p>
     *
     * <p>一律先落草稿：录进来的东西没填英文标题就直接对外，
     * 等于把中文品名推给国外客户。文案在编辑弹窗里慢慢写，写好了再上架。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String createGoods(SiteGoods goods, String operator)
    {
        SiteInfo site = siteInfoService.require();
        if (goods == null)
        {
            throw new ServiceException("参数不能为空");
        }
        String goodsCode = trimToNull(goods.getGoodsCode());
        if (goodsCode == null)
        {
            throw new ServiceException("商品编码不能为空");
        }
        if (trimToNull(goods.getGoodsName()) == null)
        {
            throw new ServiceException("商品名称不能为空");
        }
        if (goodsMapper.selectAdminOne(goodsCode) != null)
        {
            throw new ServiceException("商品编码「" + goodsCode + "」已经存在了");
        }
        if (goods.getPrice() != null && goods.getPrice().signum() < 0)
        {
            throw new ServiceException("售价不能是负数");
        }

        goods.setGoodsCode(goodsCode);
        goods.setGoodsName(trimToNull(goods.getGoodsName()));
        goods.setSpec(trimToNull(goods.getSpec()));
        goods.setSiteCategory(trimToNull(goods.getSiteCategory()));
        goods.setUnit(trimToNull(goods.getUnit()));
        goods.setRemark(trimToNull(goods.getRemark()));
        goods.setSpecsJson(normalizeJson(goods.getSpecsJson()));
        // 新建时还没有组合，维度定义也就没有意义；在编辑弹窗里连同组合一起配
        goods.setOptionsJson(null);
        goods.setModel3d(normalizeMediaUrl(goods.getModel3d()));
        goods.setModel3dMarks(Model3dMarks.normalize(goods.getModel3dMarks()));
        goods.setModel3dStates(Model3dStates.normalize(goods.getModel3dStates(), goods.getModel3d(),
                site.langList(), site.defaultLang()));
        goods.setMainVideo(normalizeMediaUrl(goods.getMainVideo()));
        // 主键那两列的值由档案给，不由前端给：主键里有它们，值飘了就会出现同一个商品两行
        goods.setShopCode(site.getShopCode());
        goods.setSiteCode(site.getSiteCode());
        goods.setPublishStatus(SiteGoods.STATUS_DRAFT);
        goods.setCreateUser(operator);
        goodsMapper.insert(goods);
        return goodsCode;
    }

    /**
     * 保存商品档案和各语言文案。
     *
     * <p>档案和文案在一个事务里存：只存住一半的话，站点上会出现「有标题没价格」
     * 这种半截数据。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveGoods(SiteGoods goods, List<Map<String, Object>> langs,
            List<Map<String, Object>> combos, String operator)
    {
        SiteInfo site = siteInfoService.require();
        if (goods == null)
        {
            throw new ServiceException("参数不能为空");
        }
        String goodsCode = trimToNull(goods.getGoodsCode());
        if (goodsCode == null)
        {
            throw new ServiceException("商品编码不能为空");
        }
        if (goodsMapper.selectAdminOne(goodsCode) == null)
        {
            throw new ServiceException("这个商品不存在");
        }
        goods.setGoodsCode(goodsCode);
        goods.setGoodsName(trimToNull(goods.getGoodsName()));
        goods.setSpec(trimToNull(goods.getSpec()));
        goods.setSiteCategory(trimToNull(goods.getSiteCategory()));
        goods.setUnit(trimToNull(goods.getUnit()));
        goods.setRemark(trimToNull(goods.getRemark()));
        goods.setSpecsJson(normalizeJson(goods.getSpecsJson()));
        GoodsOptions options = normalizeOptions(site, goods.getOptionsJson());
        goods.setOptionsJson(options == null ? null : options.toJson(JSON));
        goods.setModel3d(normalizeMediaUrl(goods.getModel3d()));
        goods.setModel3dMarks(Model3dMarks.normalize(goods.getModel3dMarks()));
        goods.setModel3dStates(Model3dStates.normalize(goods.getModel3dStates(), goods.getModel3d(),
                site.langList(), site.defaultLang()));
        goods.setMainVideo(normalizeMediaUrl(goods.getMainVideo()));
        if (goods.getPublishStatus() != null && !STATUSES.contains(goods.getPublishStatus()))
        {
            throw new ServiceException("上架状态只能是：" + String.join("、", STATUSES));
        }
        if (goods.getPrice() != null && goods.getPrice().signum() < 0)
        {
            throw new ServiceException("售价不能是负数");
        }
        // 上架前必须有标题：没有默认语言标题的商品推到站上，客户看到的是一串内部品名
        if (SiteGoods.STATUS_PUBLISHED.equals(goods.getPublishStatus()))
        {
            requireTitle(site, langs);
        }
        goods.setUpdateUser(operator);
        int rows = goodsMapper.update(goods);

        Set<String> allowed = new LinkedHashSet<>(site.langList());
        for (Map<String, Object> item : langs == null ? new ArrayList<Map<String, Object>>() : langs)
        {
            String lang = trimToNull(str(item.get("lang")));
            if (lang == null)
            {
                continue;
            }
            // 站点没开的语言不许存：存了也没人读得到，只会变成一堆没人维护的僵尸文案
            if (!allowed.contains(lang))
            {
                continue;
            }
            SiteGoodsLang text = new SiteGoodsLang();
            text.setShopCode(site.getShopCode());
            text.setSiteCode(site.getSiteCode());
            text.setGoodsCode(goodsCode);
            text.setLang(lang);
            text.setTitle(trimToNull(str(item.get("title"))));
            text.setSubtitle(trimToNull(str(item.get("subtitle"))));
            // 详情富文本会被前台当 HTML 渲染，在这里过一道白名单。
            //
            // ⚠ 放服务层而不是靠 filter：这个方法是商品文案唯一的写入路径，
            // 而 Servlet 层的过滤器很容易漏配路径（ERP 那边的 XssFilter 就一条都没覆盖到
            // 商品文案的入口）。前台是 Nuxt、v-html 渲染，指望它转义是不成立的。
            text.setDescription(com.ruoyi.station.console.util.RichTextCleaner
                    .clean(trimToNull(str(item.get("description")))));
            text.setKeywords(trimToNull(str(item.get("keywords"))));
            // 规格译文 JSON（按默认键当身份）。非法就拒，和默认 specsjson 一致——
            // 存了坏 JSON，前台那一语言的规格表会空着，而后台看着有内容，最难查
            text.setSpecsJson(normalizeJson(str(item.get("specsJson"))));
            goodsMapper.upsertLang(text);
        }

        saveCombos(site, goodsCode, combos, options, operator);
        return rows;
    }

    /** 规格维度：读 + 按站点语言校验。空的（平铺模式）返回 null */
    private GoodsOptions normalizeOptions(SiteInfo site, String json)
    {
        GoodsOptions options;
        try
        {
            options = GoodsOptions.parse(json, JSON);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException(e.getMessage());
        }
        return options == null ? null : options.normalize(site.langList(), site.defaultLang());
    }

    /**
     * 存这个商品的组合。<b>整份替换</b>：前端提交的那批就是全部，库里多出来的删掉。
     *
     * <p>为什么是整份替换而不是增量：弹窗里那张表是「所见即所得」的，商家删掉一行、
     * 点保存，期望就是它没了。做成增量的话还要额外传一个「被删掉的编码」列表，
     * 那个列表一旦和界面不同步，表现就是「删了又回来」。</p>
     *
     * <p><b>{@code combos} 为 null 时什么都不做，空列表才是「全删」。</b>
     * 这两个必须分开：老版本的前端不传这个字段，如果把 null 也当成「全删」，
     * 商家用没更新的页面存一次商品档案，组合就静默消失了。</p>
     *
     * <h3>二维模式（{@code options} 不为 null，2026-09-25）</h3>
     * <ul>
     *   <li>每条组合必须带 {@code opt1}（第一维的取值编码），有第二维时还必须带 {@code opt2}，
     *       而且都得是维度里真有的取值——否则详情页上它对不上任何一个格子，等于一条看不见的货；</li>
     *   <li>同一对取值只能有一条组合：两条「XL · 黑色」价格不同，买家选中的是哪一条说不清；</li>
     *   <li><b>组合名由这里按各语言的取值名拼</b>（「S/M · 黑色」），前端传来的 titles 不看。
     *       订单、询盘、购物车存的都是组合名快照，只要这里拼对，下游一行都不用改；
     *       让前端拼的话，改了取值名却没重新生成组合名，快照就是旧名字。</li>
     * </ul>
     * <p>平铺模式下 opt1/opt2 一律清空：从二维切回平铺时不留垃圾。</p>
     */
    /**
     * 规格自己的 3D 模型、标注和完整三态模型。
     *
     * <p>模型同主模型的规则且必须是 .glb；标注走 {@link Model3dMarks#normalize}（严格）。
     * 标注空 = logo 跟随商品主模型（前台投影）；三态必填，状态模型的 logo 从本规格没气模型投影。
     * 报错带上组合编码，商家才知道是哪一行。</p>
     */
    static void applyComboModel(SiteGoodsCombo combo, Map<String, Object> item,
            List<String> langs, String defaultLang)
    {
        String where = "组合「" + combo.getComboCode() + "」";
        combo.setModel3d(Model3dStates.glbUrl(item.get("model3d"), where));
        combo.setModel3dMarks(null);
        combo.setModel3dStates(null);
        if (combo.getModel3d() == null)
        {
            return;
        }
        try
        {
            combo.setModel3dMarks(Model3dMarks.normalize(str(item.get("model3dMarks"))));
            combo.setModel3dStates(Model3dStates.normalize(str(item.get("model3dStates")),
                    combo.getModel3d(), langs, defaultLang));
            Model3dStates.requireThree(combo.getModel3dStates(), where);
        }
        catch (ServiceException e)
        {
            throw new ServiceException(where + "：" + e.getMessage());
        }
    }

    private void saveCombos(SiteInfo site, String goodsCode,
            List<Map<String, Object>> combos, GoodsOptions options, String operator)
    {
        if (combos == null)
        {
            return;
        }
        Set<String> allowedLangs = new LinkedHashSet<>(site.langList());
        List<String> keep = new ArrayList<>();
        Set<String> pairs = new LinkedHashSet<>();

        int index = 0;
        for (Map<String, Object> item : combos)
        {
            String comboCode = trimToNull(str(item.get("comboCode")));
            if (comboCode == null)
            {
                // 编码是这条组合的身份，没有它就没法对齐语言行、没法做单据快照。
                // 静默跳过而不是报错：界面上「添加一项」出来的空行还没填就点了保存，
                // 为这个把整次保存打回去不值得
                continue;
            }
            if (keep.contains(comboCode))
            {
                throw new ServiceException("组合编码重复：" + comboCode);
            }
            keep.add(comboCode);

            SiteGoodsCombo combo = new SiteGoodsCombo();
            combo.setShopCode(site.getShopCode());
            combo.setSiteCode(site.getSiteCode());
            combo.setGoodsCode(goodsCode);
            combo.setComboCode(comboCode);
            combo.setImage(trimToNull(str(item.get("image"))));
            applyComboModel(combo, item, site.langList(), site.defaultLang());
            combo.setPrice(decimal(item.get("price")));
            if (combo.getPrice() != null && combo.getPrice().signum() < 0)
            {
                throw new ServiceException("组合「" + comboCode + "」的价格不能是负数");
            }
            // 没填排序就按提交顺序排。让商家拖一遍表格就等于排好序，不用每行手填数字
            Integer sortNum = integer(item.get("sortNum"));
            combo.setSortNum(sortNum == null ? index : sortNum);
            String status = trimToNull(str(item.get("status")));
            combo.setStatus(SiteGoodsCombo.STATUS_OFF.equals(status)
                    ? SiteGoodsCombo.STATUS_OFF : SiteGoodsCombo.STATUS_ON);
            combo.setCreateUser(operator);

            Map<String, String> composed = null;
            if (options != null)
            {
                String opt1 = trimToNull(str(item.get("opt1")));
                String opt2 = options.size() > 1 ? trimToNull(str(item.get("opt2"))) : null;
                if (!options.hasValue(0, opt1) || (options.size() > 1 && !options.hasValue(1, opt2)))
                {
                    throw new ServiceException("组合「" + comboCode + "」没有对上规格里的取值，请在规格表里重新选一下");
                }
                if (!pairs.add(opt1 + "\u0000" + opt2))
                {
                    throw new ServiceException("「" + options.comboTitle(opt1, opt2, site.defaultLang(), site.defaultLang())
                            + "」这个搭配出现了两次");
                }
                combo.setOpt1(opt1);
                combo.setOpt2(opt2);
                composed = new LinkedHashMap<>();
                for (String lang : allowedLangs)
                {
                    composed.put(lang, options.comboTitle(opt1, opt2, lang, site.defaultLang()));
                }
            }
            comboMapper.upsert(combo);

            // 名称：{ en: '...', ja: '...' }。二维模式下用上面拼好的那份
            Object titles = composed != null ? composed : item.get("titles");
            if (titles instanceof Map)
            {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) titles).entrySet())
                {
                    String lang = trimToNull(str(entry.getKey()));
                    // 站点没开的语言不许存，理由同商品文案：存了也没人读得到
                    if (lang == null || !allowedLangs.contains(lang))
                    {
                        continue;
                    }
                    SiteGoodsComboLang text = new SiteGoodsComboLang();
                    text.setShopCode(site.getShopCode());
                    text.setSiteCode(site.getSiteCode());
                    text.setGoodsCode(goodsCode);
                    text.setComboCode(comboCode);
                    text.setLang(lang);
                    text.setTitle(trimToNull(str(entry.getValue())));
                    comboMapper.upsertLang(text);
                }
            }
            index++;
        }

        // 先摘语言行再删主体，与项目其它主子表一个顺序
        comboMapper.deleteLangsMissing(goodsCode, keep);
        comboMapper.deleteMissing(goodsCode, keep);
    }

    /** 批量改上架状态 */
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(String[] goodsCodes, String publishStatus, String operator)
    {
        SiteInfo site = siteInfoService.require();
        if (goodsCodes == null || goodsCodes.length == 0)
        {
            throw new ServiceException("请选择要操作的商品");
        }
        if (!STATUSES.contains(publishStatus))
        {
            throw new ServiceException("上架状态只能是：" + String.join("、", STATUSES));
        }
        // 上架这一步要挡：站点默认语言没标题的商品不许推上去
        if (SiteGoods.STATUS_PUBLISHED.equals(publishStatus))
        {
            String lang = site.defaultLang();
            for (String code : goodsCodes)
            {
                String goodsCode = trimToNull(code);
                if (goodsCode == null)
                {
                    continue;
                }
                boolean hasTitle = goodsMapper.selectLangs(goodsCode).stream()
                        .anyMatch(text -> lang.equals(text.getLang()) && trimToNull(text.getTitle()) != null);
                if (!hasTitle)
                {
                    throw new ServiceException("商品「" + goodsCode + "」还没填 " + lang
                            + " 标题，先去编辑里补上再上架");
                }
            }
        }
        return goodsMapper.updateStatus(goodsCodes, publishStatus, operator);
    }

    /** 删除，连同文案一起删 */
    @Transactional(rollbackFor = Exception.class)
    public int deleteGoods(String[] goodsCodes)
    {
        if (goodsCodes == null || goodsCodes.length == 0)
        {
            throw new ServiceException("请选择要删除的商品");
        }
        // 先摘文案再删主体，与项目其它主子表一个顺序。组合也是这个商品的子表，一起摘
        comboMapper.deleteLangsByGoods(goodsCodes);
        comboMapper.deleteByGoods(goodsCodes);
        goodsMapper.deleteLangsByGoods(goodsCodes);
        return goodsMapper.deleteByCodes(goodsCodes);
    }

    // ---- 内部 ----

    private void requireTitle(SiteInfo site, List<Map<String, Object>> langs)
    {
        String lang = site.defaultLang();
        if (langs != null)
        {
            for (Map<String, Object> item : langs)
            {
                if (lang.equals(trimToNull(str(item.get("lang"))))
                        && trimToNull(str(item.get("title"))) != null)
                {
                    return;
                }
            }
        }
        throw new ServiceException("上架前必须填写 " + lang + " 标题");
    }

    /**
     * 单值媒体地址（3D 模型、主图视频）存之前清洗一遍：trim，然后只认 {@code https://}
     * 和 {@code /} 开头的值，其余置空。
     *
     * <p>和前台 {@code pages/products/[code].vue} 里 {@code modelUrl} / {@code mainVideo}
     * 的判定一字不差。
     * http 地址的模型在 https 页面上会被浏览器当混合内容直接拦掉，前台那边是「当成没配」，
     * 这边也一样——与其存一个前台永远不认的值，不如在保存时就置空。</p>
     */
    static String normalizeMediaUrl(String url)
    {
        String text = trimToNull(url);
        if (text == null)
        {
            return null;
        }
        return text.startsWith("https://") || text.startsWith("/") ? text : null;
    }

    /**
     * 规格 JSON 存之前先解析一遍。
     *
     * <p>解析不了直接拒绝，不静默存进去：存了坏 JSON，前台详情页那张规格表就会空着，
     * 而人在后台看是有内容的，这种「后台有前台没有」最难查。</p>
     */
    private String normalizeJson(String json)
    {
        String text = trimToNull(json);
        if (text == null)
        {
            return null;
        }
        try
        {
            JSON.readTree(text);
            return text;
        }
        catch (Exception e)
        {
            throw new ServiceException("规格内容不是合法的 JSON，请检查后重试");
        }
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * JSON 里的数字转 BigDecimal。
     *
     * <p>走字符串而不是 {@code ((Number) value).doubleValue()}：Jackson 把
     * {@code 199.90} 解成 Double，再转 BigDecimal 得到的是 199.90000000000000568…，
     * 存进 decimal(18,4) 会四舍五入成 199.9000——这一次看不出来，但价格字段上
     * 「有时差一分」是最难解释的那类问题。字符串构造器没有这一步。</p>
     *
     * <p>填了非数字（比如「面议」）时返回 null 而不是抛异常：那一格前端是文本框，
     * 让它落回默认值 0（= 面议）比把整次保存打回去更合适。</p>
     */
    private static BigDecimal decimal(Object value)
    {
        String text = trimToNull(str(value));
        if (text == null)
        {
            return null;
        }
        try
        {
            return new BigDecimal(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** 同上，整数版。用于排序号 */
    private static Integer integer(Object value)
    {
        String text = trimToNull(str(value));
        if (text == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(new BigDecimal(text).intValue());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
