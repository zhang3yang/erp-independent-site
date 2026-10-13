package com.ruoyi.station.console.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import com.ruoyi.station.console.domain.SiteRate;
import com.ruoyi.station.console.mapper.SiteRateMapper;

/**
 * 汇率取值与换算。
 *
 * <h3>两层来源，覆盖优先</h3>
 * <p>{@code ratesjson} 是 {@link RateFetcher} 每天自动拉的，{@code overridejson}
 * 是运营手工填的。<b>覆盖逐币种生效</b>——覆盖了 EUR 不影响 CNY。</p>
 *
 * <h3>⚠ 两个基准，别看混</h3>
 * <ul>
 *   <li>{@code u_s_rate.base_code} —— 汇率源返回值的基准，跟着 API 走，现在是 USD；</li>
 *   <li>{@code u_s_siteinfo.currency} —— 站点本币，商品价格用它存。</li>
 * </ul>
 * <p>两者经常不是一个值。{@link #ratesFor} 负责把前者换算成后者再交出去，
 * 所以<b>前台拿到的汇率一律以站点本币为基准，直接乘就行</b>，不需要知道 USD 中转这回事。
 * 这一步放在后端而不是前台，是因为算反了的结果仍然是一个「看起来像价格」的数字，
 * 页面上不报错，只会贵 40 倍或便宜 40 倍——这种 bug 在前端很难被发现。</p>
 *
 * <h3>缓存</h3>
 * <p>进程内 {@code volatile} 字段，和 {@link SiteInfoService} 同构。
 * <b>直接改库不生效</b>，要重启或经由管理台保存（会 {@link #evict()}）。
 * 这是本项目既有的约定，不为这一张表破例。</p>
 */
@Service
public class RateService
{
    private static final Logger log = LoggerFactory.getLogger(RateService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 除法保留的小数位。
     *
     * <p>10 位是拍的，但有依据：JPY 这类「1 美元≈154」的币种，反过来算
     * （JPY→USD）是 0.006476…，留 10 位后再乘金额，误差落在小数点后第 7 位往后，
     * 而展示只到 2 位。留 4 位就不够了——那样 100 万日元的换算结果会差出几毛钱，
     * 买家把两个页面的数字一对就发现对不上。</p>
     */
    private static final int SCALE = 10;

    @Autowired
    private SiteRateMapper siteRateMapper;

    /** 见类注释。写它的是管理台线程和定时任务线程，读它的是前台请求线程 */
    private volatile SiteRate cached;

    /** 取汇率行。查不到返回 null——建库脚本没跑全时是这样，调用方按「还没有汇率」处理 */
    public SiteRate require()
    {
        SiteRate local = cached;
        if (local == null)
        {
            local = siteRateMapper.selectRate();
            cached = local;
        }
        return local;
    }

    /** 缓存失效。定时任务写完、运营保存完都要调 */
    public void evict()
    {
        cached = null;
    }

    /**
     * 生效汇率：自动拉取的表叠加手工覆盖，键一律大写。
     *
     * <p>全部相对 {@code base_code}，<b>不是</b>相对站点本币。要给前台用的话走
     * {@link #ratesFor}。</p>
     *
     * <p>解析失败返回空表而不是抛异常：汇率读不出来只该让币种切换器消失，
     * 不该让整个站点信息接口 500。</p>
     */
    public Map<String, BigDecimal> effectiveRates()
    {
        SiteRate rate = require();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (rate == null)
        {
            return result;
        }
        putAll(result, rate.getRatesJson(), "ratesjson");
        // 后放的压过先放的，这就是「覆盖优先」
        putAll(result, rate.getOverrideJson(), "overridejson");
        return result;
    }

    /**
     * 任意两个币种之间的汇率：{@code 1 个 from 换多少个 to}。
     *
     * <p>两边都相对 {@code base_code}，所以一次除法就够——站点本币是 CNY 也不用
     * 换汇率源。算不出来（缺任一边、或分母为 0）返回 null，调用方据此把这个币种
     * 从可选列表里剔掉。</p>
     */
    public BigDecimal rate(String from, String to)
    {
        Map<String, BigDecimal> rates = effectiveRates();
        BigDecimal a = lookup(rates, from);
        BigDecimal b = lookup(rates, to);
        if (a == null || b == null || a.signum() == 0)
        {
            return null;
        }
        return b.divide(a, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 给前台的汇率表：<b>以 {@code base}（站点本币）为基准</b>，只含 {@code codes} 里那几个。
     *
     * <p>{@code base} 自己恒为 1，且恒在返回值里——买家永远切得回本币。
     * 算不出汇率的币种<b>直接不返回</b>，前台据此不把它放进切换器：
     * 与其让买家切过去看到一个错的价格，不如让他看不到这个选项。</p>
     *
     * <p>只下发这几个而不是全部 166 个：一个只开 3 个币种的站，没有理由让每个访客
     * 都下载 166 条汇率。</p>
     */
    public Map<String, BigDecimal> ratesFor(String base, List<String> codes)
    {
        Map<String, BigDecimal> all = effectiveRates();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        String baseCode = normalize(base);
        if (baseCode == null)
        {
            return result;
        }
        result.put(baseCode, BigDecimal.ONE);

        BigDecimal baseRate = lookup(all, baseCode);
        if (baseRate == null || baseRate.signum() == 0)
        {
            // 本币自己都不在汇率表里（币种码填错、或 API 不覆盖这个币种）。
            // 只返回本币=1，前台看到只有一个币种就不渲染切换器，降级成没有这个功能。
            log.warn("站点本币 {} 不在汇率表里，币种切换不可用。检查 u_s_siteinfo.currency "
                    + "是不是合法 ISO 4217 码，以及 u_s_rate.ratesjson 是否已拉到", baseCode);
            return result;
        }

        if (codes != null)
        {
            for (String code : codes)
            {
                String target = normalize(code);
                if (target == null || result.containsKey(target))
                {
                    continue;
                }
                BigDecimal targetRate = lookup(all, target);
                if (targetRate == null)
                {
                    log.warn("币种 {} 没有汇率，已从前台可选列表里剔除", target);
                    continue;
                }
                result.put(target, targetRate.divide(baseRate, SCALE, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    /**
     * 存运营手工填的覆盖值。传 null 表示不覆盖、全用自动拉取的值。
     *
     * <p>存之前先解析一遍：非法 JSON 直接拒绝并说清楚哪里不对。这里<b>不能</b>沿用
     * {@link #putAll} 那种「解析不了就忽略」的宽容——那是读路径，面对的是已经在库里的
     * 历史数据，宽容是为了不让一个坏值搞垮整个接口。而这里是写路径，运营刚敲完回车，
     * 此刻告诉他填错了，成本是零；放进去等它在读路径被静默忽略，他会以为已经生效了。</p>
     */
    public int saveOverride(String overrideJson, String operator)
    {
        if (overrideJson != null && !overrideJson.trim().isEmpty())
        {
            Map<String, Object> raw;
            try
            {
                raw = JSON.readValue(overrideJson, Map.class);
            }
            catch (Exception e)
            {
                throw new ServiceException("汇率覆盖值不是合法 JSON，应形如 {\"EUR\":0.90}");
            }
            for (Map.Entry<String, Object> entry : raw.entrySet())
            {
                if (normalize(entry.getKey()) == null)
                {
                    throw new ServiceException("汇率覆盖值里有空的币种代码");
                }
                BigDecimal value;
                try
                {
                    value = new BigDecimal(String.valueOf(entry.getValue()));
                }
                catch (Exception e)
                {
                    throw new ServiceException(entry.getKey() + " 的汇率不是数字：" + entry.getValue());
                }
                if (value.signum() <= 0)
                {
                    throw new ServiceException(entry.getKey() + " 的汇率必须大于 0，填的是 " + value);
                }
            }
        }
        int rows = siteRateMapper.updateOverride(overrideJson, operator);
        evict();
        return rows;
    }

    /** 汇率有多久没成功更新过。没成功过返回 -1 */
    public long staleDays()
    {
        SiteRate rate = require();
        if (rate == null || rate.getFetchTime() == null)
        {
            return -1;
        }
        long ms = System.currentTimeMillis() - rate.getFetchTime().getTime();
        return ms / (24L * 60 * 60 * 1000);
    }

    // ==================== 内部 ====================

    /**
     * 把一段 JSON 里的数字塞进 map。
     *
     * <p>整段解析失败只记 WARN 并跳过；单条值非法（负数、零、不是数字）也只跳过这一条。
     * 运营手填的 {@code overridejson} 里出现一个错值，不该把另外几个正确的一起废掉。</p>
     */
    private void putAll(Map<String, BigDecimal> target, String json, String field)
    {
        if (json == null || json.trim().isEmpty())
        {
            return;
        }
        Map<String, Object> raw;
        try
        {
            raw = JSON.readValue(json, Map.class);
        }
        catch (Exception e)
        {
            log.warn("u_s_rate.{} 不是合法 JSON，已整段忽略：{}", field, e.getMessage());
            return;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet())
        {
            String code = normalize(entry.getKey());
            if (code == null || entry.getValue() == null)
            {
                continue;
            }
            try
            {
                BigDecimal value = new BigDecimal(String.valueOf(entry.getValue()));
                // 汇率必须为正。0 会让后面的除法炸，负数没有任何意义
                if (value.signum() <= 0)
                {
                    log.warn("u_s_rate.{} 里 {} 的值是 {}，不是正数，已忽略这一条", field, code, value);
                    continue;
                }
                target.put(code, value);
            }
            catch (NumberFormatException e)
            {
                log.warn("u_s_rate.{} 里 {} 的值 {} 不是数字，已忽略这一条",
                        field, code, entry.getValue());
            }
        }
    }

    private BigDecimal lookup(Map<String, BigDecimal> rates, String code)
    {
        String key = normalize(code);
        return key == null ? null : rates.get(key);
    }

    private String normalize(String code)
    {
        if (code == null)
        {
            return null;
        }
        String trimmed = code.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
