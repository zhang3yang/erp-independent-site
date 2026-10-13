package com.ruoyi.station.console.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.station.console.domain.CurrencyConvertLog;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.CurrencyConvertLogMapper;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;
import com.ruoyi.station.console.mapper.SiteOrderItemMapper;
import com.ruoyi.station.console.mapper.SiteOrderMapper;

/**
 * 改结算币种时，把库里的金额按汇率一起换算。
 *
 * <h3>在这之前是什么样</h3>
 * <p>管理台「站点概览 → 结算币种」改的只是 {@code u_s_siteinfo.currency} 这一个
 * 三字母码。商品价、型号价、订单金额的<b>数字一个不动</b>，只有渲染时拼的符号变了。
 * 也就是说把 GBP 改成 USD，一个标价 £100 的商品当场变成 $100——页面上没有任何提示，
 * 而它是一次实打实的调价。那一版的提示语写着「改它<u>不会</u>换算已有商品的价格数字」，
 * 描述是准确的，但那个行为本身是错的。</p>
 *
 * <h3>动哪些数据</h3>
 * <pre>
 *   u_s_sitegoods.price        商品价
 *   u_s_sitegoods_combo.price  型号价    ← 漏了它，买家选型号下单时会按旧币种数字成交
 *   u_s_orderitem.price        订单行单价（连 currency 列一起改）
 *   u_s_orderitem.linetotal    行小计，按新单价重算
 *   u_s_order.subtotal         订单小计，重算为 sum(linetotal)
 *   u_s_order.currency         订单币种
 * </pre>
 *
 * <h3>⚠ 为什么连已经下的单也改</h3>
 * <p>「订单是下单当时的快照」这条规矩仍然成立，它管的是<b>业务内容</b>：商品名、型号、
 * 数量、单价都不该被事后的商品改动影响。换币种改的不是内容而是<b>计价单位</b>——
 * 同一笔钱换一种写法，买家买的东西和付的钱都没变。</p>
 *
 * <p>代价是真的：已经发给买家的报价邮件里是旧币种旧数字，库里换完就和邮件对不上了。
 * 这件事在管理台的确认框里写明，由运营自己决定。</p>
 *
 * <h3>⚠ 不可逆</h3>
 * <p>换算是「乘一个汇率、四舍五入到 4 位」，原值不留副本。所以每次换算往
 * {@code u_s_currency_convert_log} 落一行记死当时的汇率——还原是除以<b>那个</b>汇率，
 * 不是今天的。而且只能近似还原，四舍五入丢掉的位追不回来。要精确回滚只有库备份。</p>
 *
 * @see CurrencyConvertLog
 */
@Service
public class CurrencyConvertService
{
    private static final Logger log = LoggerFactory.getLogger(CurrencyConvertService.class);

    @Autowired
    private RateService rateService;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    @Autowired
    private SiteOrderItemMapper itemMapper;

    @Autowired
    private SiteOrderMapper orderMapper;

    @Autowired
    private CurrencyConvertLogMapper logMapper;

    /**
     * 查这次换算会用什么汇率。管理台的确认框拿它给运营看，<b>不改任何数据</b>。
     *
     * <p>拿不到汇率就在这里就报出来，而不是等运营点了确认、保存到一半才失败——
     * 那时候他已经在心里认定「币种改好了」。</p>
     *
     * @return 1 个 {@code fromCode} 换多少个 {@code toCode}
     * @throws ServiceException 任一币种没有汇率
     */
    public BigDecimal previewRate(String fromCode, String toCode)
    {
        return requireRate(normalize(fromCode), normalize(toCode));
    }

    /**
     * 最近几次换算。管理台「汇率」那张卡片底下列出来。
     *
     * <p><b>⚠ 没有这个出口的话，{@code u_s_currency_convert_log} 就是一张只写不读的表</b>——
     * 而它存在的全部理由是「事后能查到那次用的什么汇率」。运营要查得登进数据库，
     * 那等于没有。</p>
     */
    public List<CurrencyConvertLog> recentLogs(int limit)
    {
        return logMapper.selectRecent(limit <= 0 ? 5 : limit);
    }

    /**
     * 按汇率换算全站金额，并落一条流水。
     *
     * <p><b>⚠ 这个方法不开事务，也不能自己开。</b>它必须跑在调用方
     * （{@link SiteInfoService#update}）那个事务里：换算和「改 currency 这一列」
     * 是一件事的两半，分开提交的话，中间失败会留下
     * 「金额换了、币种没改」或者反过来——两种都是无声的错账，
     * 而且都没有任何现象提示它发生过。</p>
     *
     * @param site     本站档案，取 sitecode 记流水用
     * @param fromCode 换算前的币种
     * @param toCode   换算后的币种
     * @param operator 操作人
     * @return 这次换算的流水，含各表影响行数；管理台原样展示给运营
     * @throws ServiceException 汇率取不到，或两个币种相同（调用方应该先判）
     */
    public CurrencyConvertLog convert(SiteInfo site, String fromCode, String toCode, String operator)
    {
        String from = normalize(fromCode);
        String to = normalize(toCode);
        if (from == null || to == null)
        {
            throw new ServiceException("币种代码不能为空");
        }
        if (from.equals(to))
        {
            // 调用方判过一次了，走到这里说明判漏了。抛出来而不是静默返回：
            // 静默返回会往流水里记一条 rate=1 的空换算，以后查还原依据的人得先想明白
            // 这一行是什么意思
            throw new ServiceException("换算前后是同一个币种（" + from + "），不需要换算");
        }

        BigDecimal rate = requireRate(from, to);

        /*
          ⚠ 顺序不能动，四步之间有依赖：

            1. 先数「本来就对不上」的单 —— 必须在任何换算之前。订单行换完而小计
               还没换的那一刻，所有单都对不上，这时候数出来的是个纯噪音。
            2. 商品价、型号价 —— 和订单无关，放哪都行，放这里是因为它俩最便宜。
            3. 订单行单价 → 行小计 —— syncLineTotal 读的是换算完的单价，反了就是
               行小计少乘一次汇率。
            4. 订单小计 —— 读的是换算完的行小计，同理。
        */
        int mismatch = orderMapper.countSubtotalMismatch();

        int goodsRows = goodsMapper.convertPrice(rate, operator);
        int comboRows = comboMapper.convertPrice(rate, operator);

        int itemRows = itemMapper.convertPrice(rate, to);
        itemMapper.syncLineTotal();

        int orderRows = orderMapper.convertSubtotal(to);

        CurrencyConvertLog record = new CurrencyConvertLog();
        record.setSiteCode(site == null ? null : site.getSiteCode());
        record.setFromCode(from);
        record.setToCode(to);
        record.setRate(rate);
        record.setGoodsRows(goodsRows);
        record.setComboRows(comboRows);
        record.setItemRows(itemRows);
        record.setOrderRows(orderRows);
        record.setMismatch(mismatch);
        record.setOperator(operator);
        logMapper.insert(record);

        // ⚠ 这条日志是 INFO 不是 DEBUG，而且把汇率原样打出来。
        //   库里那张流水表是主要依据，但运维排查时手边往往只有日志文件——
        //   「那天到底乘了多少」这个数必须在两个地方都找得到
        log.info("结算币种 {} → {}，汇率 {}，换算：商品 {} 行、型号 {} 行、"
                + "订单行 {} 行、订单 {} 行；操作人 {}",
                from, to, rate, goodsRows, comboRows, itemRows, orderRows, operator);

        if (mismatch > 0)
        {
            log.warn("换算前有 {} 张订单的 subtotal 和它的行小计之和对不上，已按行重算改成一致。"
                    + "这是历史数据本来就不自洽（手工改过、或早期版本算法不同），"
                    + "不是这次换算造成的", mismatch);
        }
        return record;
    }

    /**
     * 取汇率，取不到就把话说清楚。
     *
     * <p><b>⚠ 绝不能降级成「取不到就不换算」。</b>那样的结果是币种改了、数字没动，
     * 也就是回到了这个类要修的那个 bug——而且这一次是<b>静默</b>发生的，
     * 运营刚看完一个写着「将按汇率换算」的确认框。</p>
     */
    private BigDecimal requireRate(String from, String to)
    {
        BigDecimal rate = rateService.rate(from, to);
        if (rate == null || rate.signum() <= 0)
        {
            // 说清楚缺的是哪一边，以及去哪儿补。只说「汇率不可用」的话，
            // 运营既不知道是哪个币种的问题，也不知道下一步该做什么
            throw new ServiceException(String.format(Locale.ROOT,
                    "拿不到 %s → %s 的汇率，币种没有改。"
                    + "可能是汇率表里没有这两个币种之一（自动拉取的汇率源不覆盖它），"
                    + "也可能是汇率从来没拉成功过。"
                    + "去「站点概览 → 汇率」看一眼拉取状态，或在那里手工填一个覆盖值再试",
                    from, to));
        }
        return rate;
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
