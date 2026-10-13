package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.station.console.service.ChatRateLimiter;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.ServiceException;
import com.ruoyi.station.console.service.SiteChatService;
import com.ruoyi.station.console.session.BuyerSession;
import com.ruoyi.station.console.session.ChatVisitor;

/**
 * 前台的在线咨询接口。挂在 {@code /openapi/site/chat/**} 下。
 *
 * <h3>⚠ 这一组和 {@code BuyerAccountController} 的门不一样</h3>
 * <p>那一组里「我的订单」要买家会话，这一组<b>一个都不要</b>——匿名访客就是主要用户。
 * 身份来自 {@code CHAT_VISITOR} 这个 Cookie，认的不是人，是<b>哪条对话线程</b>。</p>
 *
 * <p>已登录的买家会被优先按 {@code buyercode} 认（{@code SiteChatService.resolve}），
 * 所以换台电脑登录也接得上原来的对话。</p>
 *
 * <h3>⚠ 这个 Controller 必须在 ApiExceptionHandler 的 assignableTypes 里</h3>
 * <p>它是<b>对公网开放</b>的一组。不框进去的话，一个 {@code ?sinceId=abc}
 * 就会让访客看到一段 Spring 的默认错误页（带类名和路径）。
 * 理由和 {@code OpenApiController} 当初被框进去时一模一样。</p>
 *
 * <h3>令牌绝不进 URL</h3>
 * <p>下面没有任何一个方法收 {@code token} 参数——它只从 Cookie 读。
 * 放 query 里会让这个「拿到就能读走整条对话」的串原样进 nginx 的 access log。
 * 见 {@link ChatVisitor} 类注释。</p>
 */
@RestController
@RequestMapping("/openapi/site/chat")
public class SiteChatController
{
    @Autowired
    private SiteChatService chatService;

    @Autowired
    private ChatVisitor chatVisitor;

    @Autowired
    private BuyerSession buyerSession;

    @Autowired
    private ChatRateLimiter rateLimiter;

    /**
     * 打开窗口。回最近的历史 + 未读数，顺便把 Cookie 种上。
     *
     * <p>⚠ 这里<b>不建库行</b>：只是点开了小圆圈、一句话没说的人，不该在运营的
     * 会话列表里占一行。线程要等他真的发出第一条才建。</p>
     */
    @PostMapping("/open")
    public Map<String, Object> open(HttpServletRequest request, HttpServletResponse response)
    {
        String token = chatVisitor.ensure(request, response);
        return ok(chatService.open(token, buyerSession.current(request)));
    }

    /**
     * 增量拉取。前端把手里最大的 id 当 {@code sinceId} 传上来。
     *
     * <p>没有 Cookie 就是还没说过话，回空——不在这里发新令牌，
     * 免得爬虫每来一次就拿走一个。</p>
     */
    @GetMapping("/messages")
    public Map<String, Object> messages(
            @RequestParam(value = "sinceId", required = false) Long sinceId,
            HttpServletRequest request)
    {
        String token = chatVisitor.current(request);
        return ok(chatService.messagesForBuyer(token, buyerSession.current(request), sinceId));
    }

    /**
     * 发一条。请求体 {@code {content, email?, name?, pageUrl?, lang?}}。
     *
     * <p>⚠ 限流在落库<b>之前</b>判，而且拿的是真实 IP。反代必须转发
     * {@code X-Forwarded-For}，否则所有访客都是 127.0.0.1，限流会退化成全站限流
     * ——第 21 条消息之后谁都发不出话。</p>
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body,
            HttpServletRequest request, HttpServletResponse response)
    {
        Map<String, String> in = body == null ? Map.of() : body;
        String clientIp = SsoController.clientIp(request);
        if (!rateLimiter.tryAcquire(clientIp))
        {
            // 这句话要给出**退路**：限流是分钟级的，但对一个正在问价的人来说
            // 「稍后再试」等于把他赶走。告诉他还能发询盘
            throw BuyerText.error("chat.tooFast", 429);
        }

        String token = chatVisitor.ensure(request, response);
        return ok(chatService.sendFromBuyer(token, buyerSession.current(request),
                in.get("content"), in.get("email"), in.get("name"),
                in.get("pageUrl"), in.get("lang"),
                clientIp, request.getHeader("User-Agent")));
    }

    /** 标已读。窗口在前台、且页面可见时才调——收起状态下调它，红点就永远不会亮 */
    @PostMapping("/read")
    public Map<String, Object> read(HttpServletRequest request)
    {
        chatService.readByBuyer(chatVisitor.current(request), buyerSession.current(request));
        return ok(null);
    }

    private Map<String, Object> ok(Object data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        return result;
    }
}
