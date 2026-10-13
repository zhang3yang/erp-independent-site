package com.ruoyi.station.console.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteFollowup;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.mapper.SiteFollowupMapper;
import com.ruoyi.station.console.mapper.SiteInquiryMapper;

/**
 * 询盘跟进。
 *
 * <p>「营销管理」这一页做的就是这件事：回答<b>「今天该联系谁」</b>，
 * 以及<b>「哪些线索已经躺太久了」</b>。</p>
 *
 * <h3>为什么这是这个站最值钱的一块营销功能</h3>
 * <p>这是个 B2B 询盘站：前台不收款、价格 0 表示面议、成交靠人跟出来的报价单。
 * 优惠券对「面议」没有意义，群发邮件的买家基数还小、合规代价却不小。
 * 而漏掉一条询盘 = 漏掉一单，并且<b>漏掉是静悄悄的</b>——没有任何现象提示。</p>
 */
@Service
public class SiteFollowupService
{
    private static final Logger log = LoggerFactory.getLogger(SiteFollowupService.class);

    /**
     * 多少天没人碰算「躺太久」。
     *
     * <p>3 天：外贸询盘的回复窗口很短，买家同时问了好几家，隔一个周末基本就凉了。
     * 定成常量而不是配置项——它是个<b>判断口径</b>，做成可配只会让不同的人
     * 看到不同的「逾期数」，然后开始争论哪个对。真要改就改这里，一处。</p>
     */
    private static final int STALE_DAYS = 3;

    /** 看板一次最多回多少条，和 mapper 里那个 limit 一致 */
    private static final int BOARD_LIMIT = 300;

    @Autowired
    private SiteFollowupMapper followupMapper;

    @Autowired
    private SiteInquiryMapper inquiryMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    // ==================== 跟进记录 ====================

    /**
     * 一条询盘的跟进历史，最新的在最前面。
     *
     * <p>⚠ 入参是<b>数据库 id</b>不是询盘编码，和现有的
     * {@code GET /api/inquiry/{id}} 保持一致——管理台手上有的就是 id。
     * 记录本身按 {@code inquirycode} 存（那个稳定、人也认得），
     * 所以这里先按 id 把询盘捞出来换成编码。</p>
     */
    public List<SiteFollowup> listByInquiry(Long inquiryId)
    {
        return followupMapper.selectByTarget(SiteFollowup.TARGET_INQUIRY,
                requireInquiry(inquiryId).getInquiryCode());
    }

    /**
     * 记一条跟进。
     *
     * <p>⚠ <b>顺带把询盘的状态往前推一格</b>：一条「待处理」的询盘，只要有人写了
     * 第一条跟进，它就不再是「待处理」了——那正是这个动作的含义。
     * 不这么做的话，看板上会永远挂着一堆「已经联系过、但状态还是待处理」的线索，
     * 而人会开始忽略这个状态字段。</p>
     *
     * <p>只推「待处理 → 已联系」这一格。已成交 / 无效 / 已联系都不动——
     * 那些是人明确设过的结论，不该被一条跟进记录改写。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteFollowup addForInquiry(Long inquiryId, String action, String content,
            String operator)
    {
        SiteInfo site = siteInfoService.require();

        SiteInquiry inquiry = requireInquiry(inquiryId);
        String code = inquiry.getInquiryCode();

        String text = trimToNull(content);
        if (text == null) throw new ServiceException("跟进内容不能为空");
        if (text.length() > SiteFollowup.CONTENT_MAX)
        {
            throw new ServiceException("跟进内容太长了（最多 " + SiteFollowup.CONTENT_MAX + " 字）");
        }

        String act = trimToNull(action);
        if (!SiteFollowup.validAction(act))
        {
            throw new ServiceException("跟进方式只能是：" + String.join("、", SiteFollowup.ACTIONS));
        }

        SiteFollowup row = new SiteFollowup();
        row.setSiteCode(site.getSiteCode());
        row.setTargetType(SiteFollowup.TARGET_INQUIRY);
        row.setTargetCode(code);
        row.setAction(act == null ? "记录" : act);
        row.setContent(text);
        row.setCreateUser(operator);
        followupMapper.insert(row);

        /*
         * 见方法注释：只推「待处理 → 已联系」这一格。
         *
         * ⚠ 用 bumpStatus，**绝不能用 updateHandle** —— 那一条里 remark / owner /
         *   nextfollow 都是直赋值，传一个只填了 id 和 status 的对象进去，
         *   会把内部备注、负责人、下次跟进日期一起清空。而那三样正是这个模块
         *   攒出来的东西，被自己清掉就太荒唐了。
         */
        if (SiteInquiry.STATUS_NEW.equals(inquiry.getStatus()))
        {
            inquiryMapper.bumpStatus(inquiry.getId(), SiteInquiry.STATUS_CONTACTED, operator);
        }

        log.info("询盘 {} 新增跟进（{}，操作人 {}）", code, row.getAction(), operator);
        return row;
    }

    /** 按 id 取询盘，取不到就抛一句人话 */
    private SiteInquiry requireInquiry(Long inquiryId)
    {
        if (inquiryId == null) throw new ServiceException("缺少询盘 ID");
        SiteInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new ServiceException("询盘不存在");
        return inquiry;
    }

    /** 跟着询盘一起删。⚠ 不删就是孤儿行，而所有读都从询盘那边起手，永远不会被发现 */
    public int deleteByInquiries(String[] inquiryCodes)
    {
        if (inquiryCodes == null || inquiryCodes.length == 0) return 0;
        return followupMapper.deleteByTargets(SiteFollowup.TARGET_INQUIRY, inquiryCodes);
    }

    // ==================== 跟进看板 ====================

    /**
     * 「营销管理」那一页的全部数据：四个数字 + 一份清单。
     *
     * <p>⚠ 数字和清单走的是<b>同一套口径</b>（mapper 里两条语句共用同一段
     * {@code boardBase}）。不一致的话会出现「顶上说 3 条逾期、点进去列表是 5 条」，
     * 而那种不一致一旦出现，人就不再信这一页——一个不被信的待办列表等于没有。</p>
     *
     * @param bucket overdue / today / unowned / all
     * @param owner  只看某个负责人的；为空看全部
     */
    public Map<String, Object> board(String bucket, String owner)
    {
        String b = trimToNull(bucket);
        if (b == null) b = "overdue";
        if (!List.of("overdue", "today", "unowned", "all").contains(b))
        {
            throw new ServiceException("不认识的分组：" + b);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bucket", b);
        result.put("staleDays", STALE_DAYS);
        result.put("limit", BOARD_LIMIT);
        result.put("stat", followupMapper.selectBoardStat(STALE_DAYS));
        result.put("rows", followupMapper.selectBoard(b, STALE_DAYS, trimToNull(owner)));
        return result;
    }

    private static String trimToNull(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
