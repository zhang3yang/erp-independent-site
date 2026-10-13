package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.FinanceTransaction;
import com.ruoyi.system.mapper.FinanceTransactionMapper;

/**
 * 退款时效窗口：一张单最后一笔收付款完成之后，只有 3 天内能退。
 *
 * <p>采购付完货款、销售收到回款之后，退款的窗口期是 3 个自然日；过期后该单据自动从
 * 退款下拉里消失，接口层也一并拒绝。目的是把退款约束在「钱刚动完、双方还对得上账」
 * 的时间段内，避免几个月前的单据被翻出来退款。</p>
 *
 * <h3>窗口从哪一笔算起</h3>
 *
 * <p>取<b>最后一笔正向收付款</b>的交易时间——采购看支出（付给供应商的钱），
 * 销售看收入（客户打进来的钱）。退款流水本身不参与计算，否则退一笔就能把窗口
 * 无限续期。再付一笔款会重新开始计时，这是有意的：又付了钱，自然又有了退的理由。</p>
 *
 * <p>只认「已完成」的流水：还没走完流程的钱没有真正动过，不该开启退款窗口。</p>
 *
 * <h3>为什么单独抽一个组件</h3>
 *
 * <p>销售订单服务和采购订单服务都要用它筛下拉，资金流水服务要用它做接口层校验。
 * 直接让订单服务依赖资金流水服务会构成循环依赖（资金流水服务本来就依赖订单服务），
 * 所以这里只依赖 Mapper，不依赖任何 Service。</p>
 */
@Component
public class RefundWindowResolver
{
    /** 退款窗口天数。改这个值同时影响下拉筛选和接口校验，两边取的是同一个常量。 */
    public static final int REFUND_WINDOW_DAYS = 3;

    @Autowired
    private FinanceTransactionMapper transactionMapper;

    /**
     * 某类业务下每张单据最后一笔正向收付款的时间。
     *
     * <p>跨月合并：一张单的付款可以分散在多个月表里，取全部月份的最大值。</p>
     *
     * @param bizType 采购 或 销售
     * @return 业务单号 → 最后一笔收付款时间；没有收付款过的单据不在结果里
     */
    public Map<String, Date> lastSettledTimes(String shopCode, String bizType)
    {
        String transType = FinanceTransaction.BIZ_PURCHASE.equals(bizType)
                ? FinanceTransaction.TYPE_EXPENSE : FinanceTransaction.TYPE_INCOME;
        Map<String, Date> result = new HashMap<String, Date>();
        for (String suffix : FinanceTableSuffixes.valid(transactionMapper.selectTransactionTableSuffixes()))
        {
            List<Map<String, Object>> rows = transactionMapper.selectLastSettledTimeByBizType(
                    shopCode, bizType, transType, suffix);
            for (Map<String, Object> row : rows)
            {
                // key 按 SQL 里的小写别名取，见 mapper 里那段注释
                Object code = value(row, "bizcode");
                Date settled = toDate(value(row, "lasttime"));
                if (code == null || settled == null)
                {
                    continue;
                }
                Date known = result.get(code.toString());
                if (known == null || settled.after(known))
                {
                    result.put(code.toString(), settled);
                }
            }
        }
        return result;
    }

    /**
     * 把 JDBC 返回的时间值统一成 {@link Date}。
     *
     * <p>resultType=map 走的是 {@code ResultSet.getObject()}，MySQL Connector/J 8 对
     * DATETIME 列<b>默认返回 {@link LocalDateTime} 而不是 Date</b>。只判 {@code instanceof Date}
     * 会把每一行都静默跳过——SQL 查出来 4 行，一行都用不上，还不报任何错。</p>
     */
    private Date toDate(Object value)
    {
        if (value instanceof Date)
        {
            // java.sql.Timestamp 也走这里，它是 java.util.Date 的子类
            return (Date) value;
        }
        if (value instanceof LocalDateTime)
        {
            return Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    /**
     * 按列名取值，忽略大小写。
     *
     * <p>resultType=map 的 key 是 JDBC 返回的列标签，会被驱动或 MyBatis 的驼峰设置
     * 改掉大小写，写死一种拼法会取到 null 而且不报错——查出来 4 行、一行都用不上。</p>
     */
    private Object value(Map<String, Object> row, String column)
    {
        Object direct = row.get(column);
        if (direct != null)
        {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 单张单据的最后一笔正向收付款时间，没有则返回 null。 */
    public Date lastSettledTime(String shopCode, String bizType, String bizCode)
    {
        return bizCode == null ? null : lastSettledTimes(shopCode, bizType).get(bizCode);
    }

    /**
     * 窗口是否还开着。
     *
     * <p>从没收付过款的单据（settled 为 null）一律返回 false——没有钱进出，
     * 谈不上退款，也就没有窗口可言。</p>
     */
    public boolean withinWindow(Date settled)
    {
        if (settled == null)
        {
            return false;
        }
        Date now = new Date();
        // 交易时间可以是补录的历史时间，也可以被填成未来时间；
        // 未来时间视为窗口开着，不做额外处理，免得把正常单据误挡。
        return !now.after(deadline(settled));
    }

    /** 窗口截止时刻 = 最后一笔收付款时间 + 3 天。 */
    public Date deadline(Date settled)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(settled);
        calendar.add(Calendar.DAY_OF_MONTH, REFUND_WINDOW_DAYS);
        return calendar.getTime();
    }
}
