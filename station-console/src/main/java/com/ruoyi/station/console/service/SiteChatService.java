package com.ruoyi.station.console.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteChat;
import com.ruoyi.station.console.domain.SiteChatMessage;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteChatMapper;
import com.ruoyi.station.console.mapper.SiteChatMessageMapper;

/**
 * 在线咨询。买家在前台问，运营在管理台回。
 *
 * <h3>一条线程的身份怎么认</h3>
 * <p>两条路，<b>登录的那条优先</b>：</p>
 * <ol>
 *   <li>有买家会话 → 按 {@code buyercode} 找。这样换台电脑登录也接得上原来的对话；</li>
 *   <li>没有 → 按 {@code CHAT_VISITOR} Cookie 里那个令牌找。</li>
 * </ol>
 *
 * <p>顺序不能反。反过来（先认 Cookie）的话，一个买家在公用电脑上登录，
 * 接上的会是<b>上一个人</b>留在那台机器上的匿名线程。</p>
 *
 * <h3>⚠ 点开窗口不建行，发出第一条才建</h3>
 * <p>{@link #open} 只查不写。呼应 {@code SiteBuyerService} 那条「没有『新建』按钮，
 * 就不会有一个对不上任何人的档案」——只是好奇点了一下小圆圈的人，
 * 不该在运营的会话列表里占一行。</p>
 *
 * <h3>未读数现算</h3>
 * <p>库里只有两个已读游标，没有计数列。理由见 {@code SiteChatMapper.xml} 文件头。</p>
 *
 * <p>设计文档：{@code docs/设计稿/2026-09-04-在线咨询.md}</p>
 */
@Service
public class SiteChatService
{
    private static final Logger log = LoggerFactory.getLogger(SiteChatService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 一次增量最多拉多少条。
     *
     * <p>防的是「离开一天回来，一次拉几千条把页面卡死」。取不完的下一次轮询接着取——
     * 轮询本来就带着 sinceId，天然是续传的。</p>
     */
    private static final int MAX_BATCH = 200;

    @Autowired
    private SiteChatMapper chatMapper;

    @Autowired
    private SiteChatMessageMapper messageMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private SiteBuyerService buyerService;

    @Autowired
    private CodeGenerator codeGenerator;

    @Autowired
    private StationProperties properties;

    // ==================== 前台（买家） ====================

    /**
     * 打开窗口：回最近的历史 + 未读数。<b>不写库</b>，见类注释。
     *
     * @param token     CHAT_VISITOR 里那个令牌，可能为 null
     * @param buyerCode 买家会话里的客户编码，没登录就是 null
     */
    public Map<String, Object> open(String token, String buyerCode)
    {
        SiteChat chat = resolve(token, buyerCode);
        Map<String, Object> result = new LinkedHashMap<>();
        if (chat == null)
        {
            // 还没说过话。回一个空壳，前端照样把窗口画出来
            result.put("chatCode", null);
            result.put("messages", List.of());
            result.put("unread", 0);
            result.put("email", null);
            return result;
        }
        List<SiteChatMessage> messages =
                messageMapper.selectRecent(chat.getId(), properties.getChat().getHistorySize());
        result.put("chatCode", chat.getChatCode());
        result.put("messages", messages);
        result.put("unread", unreadForBuyer(chat));
        // 前端拿它决定还要不要再问一次邮箱
        result.put("email", chat.getEmail());
        return result;
    }

    /** 增量拉取。{@code sinceId} 是浏览器手里最大的那个 id */
    public Map<String, Object> messagesForBuyer(String token, String buyerCode, Long sinceId)
    {
        SiteChat chat = resolve(token, buyerCode);
        Map<String, Object> result = new LinkedHashMap<>();
        if (chat == null)
        {
            result.put("messages", List.of());
            result.put("unread", 0);
            return result;
        }
        result.put("messages", messageMapper.selectSince(chat.getId(), since(sinceId), MAX_BATCH));
        result.put("unread", unreadForBuyer(chat));
        return result;
    }

    /**
     * 买家发一条。没有线程就在这里建。
     *
     * <p>⚠ 限流<b>不在这个方法里</b>，在 Controller 上——它要拿真实 IP，
     * 而且必须在落库之前判。放这里会让「限流」和「事务」缠在一起。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteChatMessage sendFromBuyer(String token, String buyerCode, String content,
            String email, String name, String pageUrl, String lang,
            String clientIp, String userAgent)
    {
        String text = cut(content, properties.getChat().getMaxLength());
        if (text == null)
        {
            throw BuyerText.error("chat.empty", 400);
        }

        SiteChat chat = resolve(token, buyerCode);
        if (chat == null)
        {
            chat = create(token, buyerCode, pageUrl, lang, clientIp, userAgent);
        }

        // 留了邮箱就认领/新建客户档案。⚠ 这是唯一会把匿名线程和真人对上的地方
        attachBuyerIfPossible(chat, email, name);

        SiteChatMessage message = new SiteChatMessage();
        message.setShopCode(chat.getShopCode());
        message.setChatId(chat.getId());
        message.setSender(SiteChatMessage.SENDER_BUYER);
        message.setContent(text);
        messageMapper.insert(message);

        chatMapper.touchLast(chat.getId(), cut(text, 500));
        // 自己发的自己当然读过了。不推的话，买家自己发的消息会算进他自己的未读
        chatMapper.advanceRead(chat.getId(), "buyer", message.getId());
        return message;
    }

    /** 买家把这条线程标已读：游标推到当前最大的 id */
    public void readByBuyer(String token, String buyerCode)
    {
        SiteChat chat = resolve(token, buyerCode);
        if (chat != null)
        {
            advanceToLatest(chat.getId(), "buyer");
        }
    }

    // ==================== 管理台（运营） ====================

    /** 会话列表。每行带一个现算的未读数，未读的排在前面 */
    public Map<String, Object> consoleList(SiteChat query, Integer pageNum, Integer pageSize)
    {
        SiteChat condition = query == null ? new SiteChat() : query;
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;

        int total = chatMapper.countList(condition);
        List<SiteChat> rows = total == 0
                ? List.of()
                : chatMapper.selectList(condition, (current - 1) * size, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    /** 悬浮球红点用：全站运营未读的消息总条数 */
    public int consoleUnread()
    {
        return chatMapper.totalUnreadForConsole();
    }

    /**
     * 某条线程的消息。{@code sinceId} 为 0 时回最近一批历史，否则回增量。
     *
     * <p>两种语义合在一个接口里，是因为前端那两次调用（点开一个会话、之后每 3 秒轮询）
     * 除了 sinceId 之外一模一样。分成两个接口只会让前端多写一份一样的错误处理。</p>
     */
    public Map<String, Object> consoleMessages(String chatCode, Long sinceId)
    {
        SiteChat chat = requireChat(chatCode);
        long from = since(sinceId);
        List<SiteChatMessage> messages = from == 0L
                ? messageMapper.selectRecent(chat.getId(), properties.getChat().getHistorySize())
                : messageMapper.selectSince(chat.getId(), from, MAX_BATCH);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chat", chat);
        result.put("messages", messages);
        return result;
    }

    /** 运营回一条。{@code userName} 记进 senderuser，将来要查「这句谁回的」 */
    @Transactional(rollbackFor = Exception.class)
    public SiteChatMessage consoleSend(String chatCode, String content, String userName)
    {
        String text = cut(content, properties.getChat().getMaxLength());
        if (text == null)
        {
            throw BuyerText.error("chat.empty", 400);
        }
        SiteChat chat = requireChat(chatCode);

        SiteChatMessage message = new SiteChatMessage();
        message.setShopCode(chat.getShopCode());
        message.setChatId(chat.getId());
        message.setSender(SiteChatMessage.SENDER_CONSOLE);
        message.setSenderUser(userName);
        message.setContent(text);
        messageMapper.insert(message);

        chatMapper.touchLast(chat.getId(), cut(text, 500));
        // 自己回的自己读过了，否则运营的红点会被自己点亮
        chatMapper.advanceRead(chat.getId(), "console", message.getId());
        return message;
    }

    /** 运营把这条线程标已读 */
    public void consoleRead(String chatCode)
    {
        SiteChat chat = requireChat(chatCode);
        advanceToLatest(chat.getId(), "console");
    }

    // ==================== 里面用的 ====================

    /**
     * 认线程。<b>登录的那条优先</b>，理由见类注释：反过来会让公用电脑上的买家
     * 接上前一个人的匿名对话。
     */
    private SiteChat resolve(String token, String buyerCode)
    {
        String code = trimToNull(buyerCode);
        if (code != null)
        {
            SiteChat mine = chatMapper.selectLatestByBuyer(code);
            if (mine != null)
            {
                return mine;
            }
        }
        String key = trimToNull(token);
        return key == null ? null : chatMapper.selectByToken(key);
    }

    private SiteChat create(String token, String buyerCode, String pageUrl, String lang,
            String clientIp, String userAgent)
    {
        String key = trimToNull(token);
        if (key == null)
        {
            // Controller 里 ChatVisitor.ensure 保证了一定有令牌。走到这里说明接线错了
            throw BuyerText.error("chat.initFailed", 500);
        }
        SiteInfo site = siteInfoService.require();
        SiteChat chat = new SiteChat();
        chat.setShopCode(site.getShopCode());
        chat.setSiteCode(site.getSiteCode());
        chat.setChatCode(codeGenerator.nextChatCode());
        chat.setVisitorToken(key);
        chat.setPageUrl(cut(pageUrl, 500));
        chat.setLang(cut(lang, 10));
        chat.setClientIp(cut(clientIp, 64));
        chat.setUserAgent(cut(userAgent, 500));

        // 已登录的话，档案上的邮箱和称呼直接带过来——不用再问他一遍
        String code = trimToNull(buyerCode);
        if (code != null)
        {
            SiteBuyer buyer = buyerService.findByCode(code);
            if (buyer != null)
            {
                chat.setBuyerCode(buyer.getBuyerCode());
                chat.setEmail(buyer.getEmail());
                chat.setName(buyer.getName());
            }
        }
        chatMapper.insert(chat);
        log.info("新会话 {}（{}），来自 {}", chat.getChatCode(),
                chat.getEmail() == null ? "匿名" : chat.getEmail(),
                chat.getPageUrl() == null ? "未知页面" : chat.getPageUrl());
        return chat;
    }

    /**
     * 访客留了邮箱就认领/新建客户档案，并把这条线程挂上去。
     *
     * <p>已经挂过的不再动：{@code resolveByEmail} 那边会更新联系方式，
     * 但这条线程的归属不该因为他换了个邮箱填就跳到另一个人身上。</p>
     */
    private void attachBuyerIfPossible(SiteChat chat, String email, String name)
    {
        String mail = trimToNull(email);
        String who = cut(name, 100);
        if (mail == null && who == null)
        {
            return;
        }
        if (chat.getBuyerCode() != null)
        {
            // 已经有归属了。见方法注释：**不改归属**，只补称呼——
            // 他这次在邮箱格里填了另一个地址，不该让这条对话跳到另一个人名下
            chatMapper.bindBuyer(chat.getId(), null, null, who);
            if (who != null) chat.setName(who);
            return;
        }

        SiteBuyer buyer = buyerService.resolveByEmail(mail, who, null, null, null);
        if (buyer == null)
        {
            // 邮箱是空的（只填了称呼），建不了档案。称呼还是要存下来
            chatMapper.bindBuyer(chat.getId(), null, null, who);
            chat.setName(who);
            return;
        }
        chatMapper.bindBuyer(chat.getId(), buyer.getBuyerCode(), buyer.getEmail(), who);
        chat.setBuyerCode(buyer.getBuyerCode());
        chat.setEmail(buyer.getEmail());
        log.info("会话 {} 挂到客户 {}（{}）", chat.getChatCode(), buyer.getBuyerCode(), buyer.getEmail());
    }

    /** 把某一侧的游标推到当前最大的消息 id */
    private void advanceToLatest(Long chatId, String side)
    {
        Long max = messageMapper.maxId(chatId);
        if (max != null)
        {
            chatMapper.advanceRead(chatId, side, max);
        }
    }

    /** 买家那一侧的未读：只数**运营**发的 */
    private int unreadForBuyer(SiteChat chat)
    {
        return messageMapper.countUnread(chat.getId(),
                chat.getBuyerReadId() == null ? 0L : chat.getBuyerReadId(),
                SiteChatMessage.SENDER_CONSOLE);
    }

    private SiteChat requireChat(String chatCode)
    {
        String code = trimToNull(chatCode);
        if (code == null)
        {
            throw BuyerText.error("chat.sessionMissing", 400);
        }
        SiteChat chat = chatMapper.selectByCode(code);
        if (chat == null)
        {
            throw BuyerText.error("chat.notFound", 404);
        }
        return chat;
    }

    private static long since(Long sinceId)
    {
        return sinceId == null || sinceId < 0 ? 0L : sinceId;
    }

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
}
