package com.ruoyi.station.console.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteGoods;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.domain.SiteOrderItem;
import com.ruoyi.station.console.mapper.SiteGoodsMapper;
import com.ruoyi.station.console.domain.SiteGoodsCombo;
import com.ruoyi.station.console.mapper.SiteGoodsComboMapper;
import com.ruoyi.station.console.mapper.SiteOrderItemMapper;
import com.ruoyi.station.console.mapper.SiteOrderMapper;

/**
 * 站点订单。
 *
 * <p>docs/独立站与ERP的边界.md §3.2 把「订单管理」列在「留位置」里。这个类是那一格
 * 真正长出东西的地方，依据是：前台的询价流程（{@code station-site/app/lib/shop.ts}）
 * 已经把模型定完了，而那些单子在这之前只存在买家自己的浏览器里。</p>
 *
 * <h3>⚠ 和 ERP 没有任何互通</h3>
 * <p>要点 4：站点订单和 ERP 订单没有隶属关系。这个类里没有一次调用出这台机器，
 * 也没有「同步到 ERP」——一期的同步方式是人工在 ERP 里录一张（§8：不做，也不预埋接口）。</p>
 *
 * <h3>⚠ 价格由服务端重新定，不信前端传的</h3>
 * <p>{@link #submit} <b>只接受商品编码和数量</b>。名称、单价、图片、单位全部由服务端
 * 从 {@code u_s_sitegoods} 现查，小计也在服务端算。</p>
 *
 * <p>这不是洁癖：前台的清单在 localStorage 里，改一个数字就能把 12.50 变成 0.01，
 * 然后提交。信前端传的价格等于把报价单交给买家自己填。</p>
 *
 * <p>顺带的好处是「下架的商品下不了单」自动成立——{@code selectPublicOne} 只返回
 * 已上架的商品，查不到就是下架了或者编码是编的。</p>
 */
@Service
public class SiteOrderService
{
    private static final Logger log = LoggerFactory.getLogger(SiteOrderService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 一张单最多多少行。防的是有人拿脚本往这个公开接口上灌 */
    private static final int MAX_ITEMS = 100;

    /** 单行最大数量。外贸按米/千克下单，所以给得大，但不能没有上限 */
    private static final BigDecimal MAX_QTY = new BigDecimal("9999999");

    @Autowired
    private SiteOrderMapper orderMapper;

    @Autowired
    private SiteOrderItemMapper itemMapper;

    @Autowired
    private SiteGoodsComboMapper comboMapper;

    @Autowired
    private SiteGoodsMapper goodsMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteBuyerService buyerService;

    @Autowired
    private InquiryNotifier notifier;

    /** 发给<b>买家本人</b>的下单确认信。⚠ 和 {@link InquiryNotifier} 是两个收件人，见那两个类的注释 */
    @Autowired
    private BuyerMailer buyerMailer;

    @Autowired
    private CodeGenerator codeGenerator;

    // ==================== 前台提交 ====================

    /**
     * 前台买家提交询价清单，落成一张订单。
     *
     * @param order 联系方式、目的地、运输/付款倾向、备注。
     *              ⚠ 里面的 {@code subtotal} / {@code currency} / {@code orderCode} /
     *              {@code status} 一律忽略，由服务端定
     * @param lines 只用 {@code goodsCode}、{@code comboCode} 和 {@code qty} 三个字段，
     *              其余忽略（见类注释）。型号的价格同样由服务端现查，不看前端传的
     * @return 落好的订单（含行）
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteOrder submit(SiteOrder order, List<SiteOrderItem> lines, String clientIp)
    {
        SiteInfo site = siteInfoService.require();
        if (order == null)
        {
            throw BuyerText.error("order.empty", 400);
        }
        if (lines == null || lines.isEmpty())
        {
            throw BuyerText.error("order.noLines", 400);
        }
        if (lines.size() > MAX_ITEMS)
        {
            throw BuyerText.error("order.tooManyLines", 400, MAX_ITEMS);
        }

        order.setContactName(require(order.getContactName(), "order.contactRequired", 100));
        // ⚠ 邮箱统一小写。客户档案那边（SiteBuyerService）也小写，两边必须一致——
        //   不一致的话 bindBuyerByEmail 那条按邮箱回填的 update 就挂不上历史订单。
        //   现在的库是 utf8mb4_0900_ai_ci（大小写不敏感）所以侥幸能匹配上，
        //   但那是排序规则给的运气，不是代码保证的
        order.setEmail(require(order.getEmail(), "order.emailRequired", 150).toLowerCase());
        // 和前台那条正则、以及询盘那边保持一致——三处不一致的话，
        // 会出现「前台过了、后端拒了」这种只有一部分人能提交的状态
        if (!order.getEmail().matches("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$"))
        {
            throw BuyerText.error("order.badEmail", 400);
        }
        order.setCountry(require(order.getCountry(), "order.countryRequired", 80));
        order.setCompany(cut(order.getCompany(), 200));
        order.setPhone(cut(order.getPhone(), 50));
        order.setAddrLine1(cut(order.getAddrLine1(), 200));
        order.setAddrLine2(cut(order.getAddrLine2(), 200));
        order.setCity(cut(order.getCity(), 80));
        order.setRegion(cut(order.getRegion(), 80));
        order.setPostal(cut(order.getPostal(), 30));
        order.setShippingPref(cut(order.getShippingPref(), 20));
        order.setPaymentPref(cut(order.getPaymentPref(), 20));
        order.setBuyerNote(cut(order.getBuyerNote(), 800));
        // 买家下单时浏览的语言：回执邮件按它写，买家中心读单时没传语言也退回它。
        // 不认识的退回站点默认语言，所以这一列永远是站点开了的某一种
        order.setLang(site.resolveLang(order.getLang()));

        String orderCode = codeGenerator.nextOrderCode();

        // ---- 重新定价。见类注释 ----
        List<SiteOrderItem> items = repriceLines(site, lines, orderCode);

        BigDecimal subtotal = BigDecimal.ZERO;
        int inquireOnly = 0;
        for (SiteOrderItem item : items)
        {
            if (item.isInquireOnly())
            {
                inquireOnly++;
            }
            else
            {
                subtotal = subtotal.add(item.getLineTotal());
            }
        }

        // ---- 客户档案。先建档再插单，这样订单落库时 buyercode 就是对的 ----
        SiteBuyer buyer = buyerService.resolveByEmail(order.getEmail(), order.getContactName(),
                order.getCompany(), order.getPhone(), order.getCountry());

        order.setShopCode(site.getShopCode());
        order.setSiteCode(site.getSiteCode());
        order.setOrderCode(orderCode);
        order.setStatus(SiteOrder.STATUS_SUBMITTED);
        order.setSource(SiteOrder.SOURCE_SITE);
        order.setCurrency(site.getCurrency());
        order.setSubtotal(subtotal);
        order.setInquireOnly(inquireOnly);
        order.setBuyerCode(buyer == null ? null : buyer.getBuyerCode());
        order.setClientIp(cut(clientIp, 64));
        orderMapper.insert(order);

        itemMapper.insertBatch(items);
        order.setItems(items);

        // 建档之后重算一次计数：这张单刚插进去，上面 resolveByEmail 那一步还看不到它
        if (buyer != null)
        {
            buyerService.refreshCounts(buyer.getBuyerCode(), buyer.getEmail());
        }

        // ⚠ 这一行不能少，也不能挪到事务外面自己发。
        //
        //   不能少：结账页原来是把清单当成一条**询盘**提交的，商家一直靠询盘通知邮件
        //   知道有新单。改成真实订单之后不发信，表现是「买家提交成功、单子在管理台里躺着、
        //   商家完全不知道」——比改造之前更糟。
        //
        //   不能自己发：notifyAfterCommit 把发信挂在事务提交之后、再扔进线程池，
        //   两层都是必要的（SMTP 超时会回滚整张单 / 会拖慢买家那个请求）。
        //   理由全文见 InquiryNotifier 的类注释。
        notifier.notifyAfterCommit(site, order);

        // 给买家本人回一封确认信。
        //
        // ⚠ 这封不是「顺手也发一下」：下单不要求登录，游客那份回执只存在他当时
        //   那台浏览器的 localStorage 里——换台电脑、清一次浏览器数据，那张单就没了。
        //   这封信里的单号 + selectForGuest 那个接口，才是他唯一的跨设备退路。
        //
        // ⚠ 同样挂在事务提交之后、同样扔线程池、而且**异常全吞**。
        //   它是通知不是流程：为一封发不出去的信把订单回滚掉是本末倒置。
        //   顺序放在商家那封之后：两封都发不出去时，日志里先出现的是更要紧的那一条
        // 回执里的商品名按买家的语言给；库里的快照仍是默认语言（管理台要看稳定的名字）
        buyerMailer.sendOrderReceiptAfterCommit(site, order, localizedItems(site, items, order.getLang()));

        log.info("站点订单 {} 已入库：{} 项（{} 项面议），小计 {} {}，来自 {} / {}",
                orderCode, items.size(), inquireOnly, subtotal, site.getCurrency(),
                order.getEmail(), order.getCountry());
        return order;
    }

    /**
     * 按商品编码现查商品，拼出订单行。
     *
     * <p>同一个编码在清单里出现两次时<b>合并数量</b>而不是留两行：前台已经保证一个编码一行，
     * 但这个接口是公开的，重复编码进来只会让同一张单里出现两行一样的东西。</p>
     */
    private List<SiteOrderItem> repriceLines(SiteInfo site, List<SiteOrderItem> lines, String orderCode)
    {
        /*
          用 LinkedHashMap 保住买家清单里的顺序。

          ⚠ 键是「商品编码 + 型号编码」，不只是商品编码。同一个商品选了两个不同型号
          （machine 和 machine&boots）是**两行**，不是一行数量翻倍——它们价格不同。
          分隔符用 NUL（源码里写作 \0）：型号编码里可能出现 &、-、: 这些字符，唯独不会有它。
        */
        Map<String, BigDecimal> wanted = new LinkedHashMap<>();
        for (SiteOrderItem line : lines)
        {
            String code = trimToNull(line == null ? null : line.getGoodsCode());
            if (code == null)
            {
                throw BuyerText.error("order.lineNoCode", 400);
            }
            String comboCode = trimToNull(line.getComboCode());
            BigDecimal qty = line.getQty();
            if (qty == null || qty.signum() <= 0)
            {
                throw BuyerText.error("order.badQty", 400, code);
            }
            if (qty.compareTo(MAX_QTY) > 0)
            {
                throw BuyerText.error("order.qtyTooBig", 400, code);
            }
            wanted.merge(code + '\0' + (comboCode == null ? "" : comboCode), qty, BigDecimal::add);
        }

        String defaultLang = site.defaultLang();
        List<SiteOrderItem> items = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        int sort = 0;
        for (Map.Entry<String, BigDecimal> entry : wanted.entrySet())
        {
            String[] parts = entry.getKey().split("\0", -1);
            String code = parts[0];
            String comboCode = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            // ⚠ 用 selectPublicOne 而不是 selectAdminOne：只有**已上架**的商品能下单。
            //   下架的商品下单成功，等于卖一个已经不卖的东西
            SiteGoods goods = goodsMapper.selectPublicOne(code, defaultLang, defaultLang);
            if (goods == null)
            {
                missing.add(code);
                continue;
            }

            /*
              型号。同样只认库里的：**价格以型号为准**，所以这里绝不能信前端传来的
              price——那是买家浏览器里的数字，改一下就是自己定价。整个 repriceLines
              存在的理由就是这一件事，型号只是把它的粒度从商品细到了型号。

              型号被停用或删掉时按「不可下单」处理，和商品下架同一个口径：
              它的价格已经没有依据了，退回让买家重新选，比按商品价成交安全。
            */
            SiteGoodsCombo combo = null;
            if (comboCode != null)
            {
                combo = comboMapper.selectPublicOne(code, comboCode, defaultLang, defaultLang);
                if (combo == null)
                {
                    missing.add(code + "（型号 " + comboCode + "）");
                    continue;
                }
            }

            SiteOrderItem item = new SiteOrderItem();
            item.setShopCode(site.getShopCode());
            item.setOrderCode(orderCode);
            item.setGoodsCode(code);
            // 商品名优先用语言层的 title（买家看到的那个），没有再退回档案名
            item.setGoodsName(cut(firstNonBlank(goods.getTitle(), goods.getGoodsName()), 200));
            item.setComboCode(combo == null ? null : cut(combo.getComboCode(), 50));
            item.setComboName(combo == null ? null : cut(combo.getTitle(), 200));
            item.setSubtitle(cut(firstNonBlank(goods.getSubtitle(), goods.getSiteCategory()), 200));
            item.setImgUrl(cut(firstImage(goods), 500));
            // 选了型号就按型号价，没选才按商品价
            BigDecimal price = combo != null ? combo.getPrice() : goods.getPrice();
            price = price == null ? BigDecimal.ZERO : price;
            item.setPrice(price);
            item.setCurrency(site.getCurrency());
            item.setQty(entry.getValue());
            item.setUnit(cut(goods.getUnit(), 20));
            // 面议行（price <= 0）的行小计记 0，不是「0 元买到了」——
            // 是「这一行的金额还没定」。inquireOnly 那个计数才是它的表达方式
            item.setLineTotal(price.signum() <= 0 ? BigDecimal.ZERO
                    : price.multiply(entry.getValue()));
            item.setSortNum(sort++);
            items.add(item);
        }

        if (!missing.isEmpty())
        {
            // 明确说是哪几个：买家的清单在 localStorage 里放了几天，中间商品下架是常事。
            // 只说「有商品不可用」的话他不知道该删哪一行
            throw BuyerText.error("order.unavailable", 400, String.join(", ", missing));
        }
        return items;
    }

    /**
     * 把订单行的商品名 / 型号名 / 副标题换成 {@code lang} 那种语言的（2026-09-22 起）。
     *
     * <p>库里的快照按站点默认语言存，一个字不动——管理台只有中文界面，运营看到的名字必须稳定。
     * 买家那一侧（买家中心、凭单号查单、回执邮件）读的时候按他<b>当前</b>浏览的语言换一遍，
     * 切了语言商品名就跟着切。价格、数量、图片都是快照，不碰：那些是「下单当时」的事实。</p>
     *
     * <p>换不到就保留快照：商品下架了、型号删了、那种语言没译名，都退回默认语言那份——
     * 退回的正是买家下单时看到的那个名字，不会出空行。</p>
     *
     * <p>返回的是<b>新列表、新对象</b>，不改传进来的那份：submit 里传进来的 items
     * 刚插进库，还要原样留给 notifier 和日志。</p>
     */
    private List<SiteOrderItem> localizedItems(SiteInfo site, List<SiteOrderItem> items, String lang)
    {
        if (items == null || items.isEmpty())
        {
            return items == null ? List.of() : items;
        }
        String target = site.resolveLang(lang);
        String defaultLang = site.defaultLang();
        if (target.equalsIgnoreCase(defaultLang))
        {
            return items;
        }
        List<SiteOrderItem> out = new ArrayList<>(items.size());
        for (SiteOrderItem item : items)
        {
            SiteOrderItem copy = new SiteOrderItem();
            copy.setId(item.getId());
            copy.setShopCode(item.getShopCode());
            copy.setOrderCode(item.getOrderCode());
            copy.setGoodsCode(item.getGoodsCode());
            copy.setGoodsName(item.getGoodsName());
            copy.setComboCode(item.getComboCode());
            copy.setComboName(item.getComboName());
            copy.setSubtitle(item.getSubtitle());
            copy.setImgUrl(item.getImgUrl());
            copy.setPrice(item.getPrice());
            copy.setCurrency(item.getCurrency());
            copy.setQty(item.getQty());
            copy.setUnit(item.getUnit());
            copy.setLineTotal(item.getLineTotal());
            copy.setSortNum(item.getSortNum());
            String code = trimToNull(item.getGoodsCode());
            SiteGoods goods = code == null ? null : goodsMapper.selectPublicOne(code, target, defaultLang);
            if (goods != null)
            {
                String title = trimToNull(goods.getTitle());
                if (title != null)
                {
                    copy.setGoodsName(cut(title, 200));
                }
                String subtitle = trimToNull(goods.getSubtitle());
                if (subtitle != null)
                {
                    copy.setSubtitle(cut(subtitle, 200));
                }
                String comboCode = trimToNull(item.getComboCode());
                if (comboCode != null)
                {
                    SiteGoodsCombo combo = comboMapper.selectPublicOne(code, comboCode, target, defaultLang);
                    if (combo != null && trimToNull(combo.getTitle()) != null)
                    {
                        copy.setComboName(cut(combo.getTitle(), 200));
                    }
                }
            }
            out.add(copy);
        }
        return out;
    }

    /** 站点专用图第一张优先，其次商品档案主图 */
    private String firstImage(SiteGoods goods)
    {
        String siteImgs = trimToNull(goods.getSiteImgs());
        if (siteImgs != null)
        {
            String first = siteImgs.split(",")[0];
            if (!first.isBlank())
            {
                return first.trim();
            }
        }
        return trimToNull(goods.getImgUrl());
    }

    // ==================== 管理台 ====================

    public Map<String, Object> selectList(SiteOrder query, Integer pageNum, Integer pageSize)
    {
        SiteOrder condition = query == null ? new SiteOrder() : query;
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;

        int total = orderMapper.countList(condition);
        List<SiteOrder> rows = total == 0
                ? List.of()
                : orderMapper.selectList(condition, (current - 1) * size, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    /** 订单详情：单 + 行 */
    public SiteOrder detail(String orderCode)
    {
        String code = trimToNull(orderCode);
        if (code == null)
        {
            throw new ServiceException("缺少订单号");
        }
        SiteOrder order = orderMapper.selectByCode(code);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        order.setItems(itemMapper.selectByOrder(code));
        return order;
    }

    /**
     * 推进状态。
     *
     * <h3>为什么要校验流转，而不是让运营随便改</h3>
     * <p>时间轴上那三个时间戳是「只写一次」的（见 mapper 里的 coalesce）。
     * 允许从「已完成」跳回「待确认」的话，时间戳还留着，于是出现
     * 「状态是待确认，但发货时间是上周二」这种自相矛盾的单子。</p>
     *
     * <p>所以只允许往前走，外加「任何状态都能取消」。要往后退只能取消重下——
     * 这在报价阶段是正常操作，在已发货之后本来也不该退。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int advance(String orderCode, String status, String handleUser)
    {
        SiteOrder current = detail(orderCode);
        String target = trimToNull(status);
        if (target == null)
        {
            throw new ServiceException("缺少目标状态");
        }
        if (!ORDER_FLOW.contains(target) && !SiteOrder.STATUS_CANCELLED.equals(target))
        {
            throw new ServiceException("未知的订单状态：" + target);
        }
        if (current.isCancelled())
        {
            throw new ServiceException("这张单已取消，不能再改状态");
        }
        if (SiteOrder.STATUS_CANCELLED.equals(target))
        {
            // 取消不看当前在哪一步：报价阶段客户反悔、发货后拒收，都要能记下来
            return writeStatus(orderCode, target, handleUser);
        }
        int from = ORDER_FLOW.indexOf(current.getStatus());
        int to = ORDER_FLOW.indexOf(target);
        if (from < 0)
        {
            // 库里是一个不在词表里的值（历史数据或者手工改过），放行但记一条
            log.warn("订单 {} 的当前状态「{}」不在词表里，直接置为「{}」",
                    orderCode, current.getStatus(), target);
        }
        else if (to <= from)
        {
            throw new ServiceException("订单已经是「" + current.getStatus() + "」，不能退回「" + target + "」。"
                    + "如果这张单不做了，用「取消」");
        }
        return writeStatus(orderCode, target, handleUser);
    }

    /** 正向流转顺序。取消不在里面——它是任何一步都能走的旁路 */
    private static final List<String> ORDER_FLOW = List.of(
            SiteOrder.STATUS_SUBMITTED, SiteOrder.STATUS_CONFIRMED,
            SiteOrder.STATUS_SHIPPED, SiteOrder.STATUS_FINISHED);

    private int writeStatus(String orderCode, String status, String handleUser)
    {
        SiteOrder update = new SiteOrder();
        update.setOrderCode(orderCode);
        update.setStatus(status);
        update.setHandleUser(cut(handleUser, 50));
        int rows = orderMapper.updateStatus(update);
        log.info("订单 {} 状态改为 {}（操作人 {}）", orderCode, status, handleUser);
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public int updateRemark(String orderCode, String remark, String handleUser)
    {
        detail(orderCode);
        SiteOrder update = new SiteOrder();
        update.setOrderCode(orderCode);
        update.setRemark(cut(remark, 500));
        update.setHandleUser(cut(handleUser, 50));
        return orderMapper.updateRemark(update);
    }

    // ==================== 小工具 ====================

    private static String trimToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cut(String value, int max)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /** @param key 缺了时抛的文案键（BuyerText），买家看得到，按语言翻 */
    private static String require(String value, String key, int max)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null)
        {
            throw BuyerText.error(key, 400);
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String firstNonBlank(String a, String b)
    {
        String first = trimToNull(a);
        return first != null ? first : trimToNull(b);
    }

    // ==================== 前台买家自助查询 ====================
    //
    // 买家登录之后看「我的订单」走这两个方法（BuyerAccountController）。
    //
    // ⚠ 和上面那组管理台方法的区别不只是过滤条件，**返回的东西也不一样**：
    //   这里手工拼一个买家能看的形状，不把 SiteOrder 整个序列化出去。
    //   那个对象上有 clientip（他自己的 IP，存着是为了排查刷单）、handleuser、
    //   remark（运营的内部备注，写着「压价狠，先报高 10%」这类话）。
    //   把整张单发给买家是一场事故，而 @JsonIgnore 只挡得住一个字段——
    //   下一个人往 SiteOrder 上加一列内部字段时不会想起这里。

    /**
     * 我的订单列表。
     *
     * <p>⚠ {@code buyerCode} 必须来自会话，不能来自请求参数——
     * 参数来的话，换一个客户编码就能看别人的订单。调用方那边有一行注释说这件事。</p>
     */
    public Map<String, Object> selectByBuyer(String buyerCode, Integer pageNum, Integer pageSize)
    {
        String code = trimToNull(buyerCode);
        if (code == null)
        {
            // 走到这里说明调用方没做鉴权。返回空列表而不是全部——
            // 「条件为空就查全部」是这类接口最常见的越权方式
            throw new ServiceException("缺少客户编码");
        }
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;

        SiteOrder condition = new SiteOrder();
        condition.setBuyerCode(code);

        int total = orderMapper.countList(condition);
        List<SiteOrder> rows = total == 0
                ? List.of()
                : orderMapper.selectList(condition, (current - 1) * size, size);

        List<Map<String, Object>> safe = new java.util.ArrayList<>();
        for (SiteOrder order : rows)
        {
            safe.add(buyerView(order, false));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", safe);
        return result;
    }

    /**
     * 我的某一张订单，含订单行。
     *
     * @return 买家能看的那部分；订单不存在<b>或者不属于这个买家</b>时返回 null
     *         （调用方对两种情况回同一句「订单不存在」——区分开等于告诉他哪些单号是真的）
     */
    public Map<String, Object> selectForBuyer(String orderCode, String buyerCode, String lang)
    {
        String code = trimToNull(orderCode);
        String buyer = trimToNull(buyerCode);
        if (code == null || buyer == null)
        {
            return null;
        }
        SiteOrder order = orderMapper.selectByCode(code);
        // ⚠ 归属判断不能省，而且要在取订单行之前：单号是有规律的流水号，
        //   一个登录用户挨个试就能试出别人的单
        if (order == null || !buyer.equals(trimToNull(order.getBuyerCode())))
        {
            return null;
        }
        order.setItems(localizedItems(siteInfoService.require(), itemMapper.selectByOrder(code),
                lang == null || lang.isBlank() ? order.getLang() : lang));
        return buyerView(order, true);
    }

    /**
     * 匿名查单：凭<b>单号 + 下单邮箱</b>取回一张单，含订单行。
     *
     * <p>给没有账号的买家用。下单不要求登录，他手上的回执只在当时那台浏览器里，
     * 换台设备就只剩确认信里那个单号了——这个方法是那条路的另一半。</p>
     *
     * <h3>⚠ 邮箱这一关就是全部的鉴权</h3>
     * <p>单号是 {@code DD} + 流水，<b>可枚举</b>：从自己的单号往前后数就能猜到别人的。
     * 所以邮箱必须<b>完全对上</b>（去空格、忽略大小写之后逐字相等），不能做前缀匹配、
     * 不能只比域名、更不能为了「用户体验」在对不上的时候提示「邮箱不对」——
     * 那等于告诉他这个单号是真的，只要换个邮箱接着试。</p>
     *
     * <p>撞号的成本由 {@code OrderLookupRateLimiter} 兜着（调用方在查库之前先过它）。
     * 那个限流是这个接口<b>访问控制的一部分</b>，不是防灌水，别把它当可选项去掉。</p>
     *
     * @return 买家能看的那部分；<b>单号不存在、邮箱对不上、任一参数为空，一律返回 null</b>
     *         （调用方对这几种回同一句话）
     */
    public Map<String, Object> selectForGuest(String orderCode, String email, String lang)
    {
        String code = trimToNull(orderCode);
        String mail = trimToNull(email);
        if (code == null || mail == null)
        {
            return null;
        }
        SiteOrder order = orderMapper.selectByCode(code);
        // ⚠ 归属判断要在取订单行之前：对不上就什么都别查，更别把行数漏出去
        if (order == null || !mail.equalsIgnoreCase(trimToNull(order.getEmail())))
        {
            return null;
        }
        order.setItems(localizedItems(siteInfoService.require(), itemMapper.selectByOrder(code),
                lang == null || lang.isBlank() ? order.getLang() : lang));
        return buyerView(order, true);
    }

    /**
     * 一张单里买家能看到的部分。
     *
     * <p>四个时间戳都下发：前台那条时间轴（询价已发送 → 已确认 → 已发货 → 已完成）
     * 就是靠它们点亮的，见 {@code station-site/app/pages/orders/[id].vue}。</p>
     */
    private Map<String, Object> buyerView(SiteOrder order, boolean withItems)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", order.getOrderCode());
        data.put("status", order.getStatus());
        data.put("lang", order.getLang());
        data.put("currency", order.getCurrency());
        data.put("subtotal", order.getSubtotal());
        data.put("inquireOnly", order.getInquireOnly());
        data.put("createTime", order.getCreateTime());
        data.put("confirmTime", order.getConfirmTime());
        data.put("shipTime", order.getShipTime());
        data.put("finishTime", order.getFinishTime());
        // 他自己填的那些，回显给他看：换台电脑打开也知道当初填了什么地址
        data.put("contactName", order.getContactName());
        data.put("company", order.getCompany());
        data.put("phone", order.getPhone());
        data.put("city", order.getCity());
        data.put("region", order.getRegion());
        data.put("country", order.getCountry());
        data.put("shippingPref", order.getShippingPref());
        data.put("paymentPref", order.getPaymentPref());
        data.put("buyerNote", order.getBuyerNote());

        if (withItems)
        {
            // 从引导式下单来的单：他当初选了什么，按下单语言那份文字给（列表不查这一列，所以只在详情里）
            data.put("wizard", OrderWizardService.snapshotRows(order.getWizardJson(), false));
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (SiteOrderItem item : order.getItems())
            {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("goodsCode", item.getGoodsCode());
                line.put("goodsName", item.getGoodsName());
                // 型号快照。买家的订单详情页要显示他当初选的是哪一款——
                // 同一个商品的两个型号在这里是两行，不写明就是两行一模一样的东西
                line.put("comboCode", item.getComboCode());
                line.put("comboName", item.getComboName());
                line.put("subtitle", item.getSubtitle());
                line.put("imgUrl", item.getImgUrl());
                line.put("qty", item.getQty());
                line.put("unit", item.getUnit());
                line.put("price", item.getPrice());
                line.put("lineTotal", item.getLineTotal());
                items.add(line);
            }
            data.put("items", items);
            data.put("itemCount", items.size());
        }
        else
        {
            // 列表页不查订单行（N+1 查询），但卡片上要显示「共 N 项」。
            // 这个数在列表查询里拿不到，前台那边显示小计就够了
            data.put("items", List.of());
        }
        return data;
    }
}
