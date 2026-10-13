package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.station.console.domain.SiteChat;
import com.ruoyi.station.console.security.RequirePerm;
import com.ruoyi.station.console.service.SiteChatService;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * 管理台的在线咨询接口。挂在 {@code /api/chat/**} 下，由 {@code AuthInterceptor} 守着。
 *
 * <h3>为什么单开一个 Controller，不塞进 ConsoleApiController</h3>
 * <p>那个文件已经 660 多行、装着八个模块的接口。再往里加一组，
 * 下一个来改询盘的人得先翻过聊天的代码。这一组和别的模块没有任何共用，
 * 单开一个文件是它该有的边界。</p>
 *
 * <h3>⚠ 两件必须做对的事</h3>
 * <ol>
 *   <li><b>每个方法都要挂 {@link RequirePerm}。</b>{@code AuthInterceptor} 是
 *       <b>默认拒绝</b>——漏一个，那个接口就是 403，而且只有点到它的人才会发现；</li>
 *   <li><b>这个类要加进 {@code ApiExceptionHandler} 的 {@code assignableTypes}。</b>
 *       不加的话这一组的异常不走统一出口，前端的 request.js 认不出那个形状，
 *       表现是「点了没反应，控制台里是 JSON 解析错误」。</li>
 * </ol>
 *
 * <p>权限串 {@code site:chat:list} / {@code site:chat:reply} 由
 * {@code sql/station/migration/2026-09-04-chat.sql} 写进 {@code u_s_role}。
 * 站长是 {@code *}，运营靠那条 UPDATE 追加。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ConsoleChatController
{
    @Autowired
    private SiteChatService chatService;

    /**
     * 会话列表。未读的排在前面，然后按最后一条消息倒排。
     *
     * <p>{@code query.name} 一个输入框查四个字段：称呼、邮箱、会话编码、最后一条的内容。
     * 运营记得住的通常是其中之一——尤其是「那个问不锈钢的」，所以内容也要能搜。</p>
     */
    @GetMapping
    @RequirePerm("site:chat:list")
    public Map<String, Object> list(SiteChat query,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize)
    {
        return okTable(chatService.consoleList(query, pageNum, pageSize));
    }

    /**
     * 悬浮球红点用：全站未读的消息总条数。
     *
     * <p>⚠ 这个接口被<b>每 15 秒轮询一次</b>，是这一组里唯一的高频接口。
     * 它只有一条带索引的 count，别往里加东西——加一个 join 就是每 15 秒一次的全表扫。</p>
     */
    @GetMapping("/unread")
    @RequirePerm("site:chat:list")
    public Map<String, Object> unread()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unread", chatService.consoleUnread());
        return ok(data);
    }

    /** 某条会话的消息。{@code sinceId} 为空或 0 回最近一批历史，否则回增量 */
    @GetMapping("/{chatCode}/messages")
    @RequirePerm("site:chat:list")
    public Map<String, Object> messages(@PathVariable("chatCode") String chatCode,
            @RequestParam(value = "sinceId", required = false) Long sinceId)
    {
        return ok(chatService.consoleMessages(chatCode, sinceId));
    }

    /**
     * 回一条。
     *
     * <p>用的是 {@code site:chat:reply}，不是 {@code site:chat:list}——
     * 「能看」和「能替公司说话」是两件事，运营角色可以只给前者。</p>
     */
    @PostMapping("/{chatCode}/send")
    @RequirePerm("site:chat:reply")
    public Map<String, Object> send(@PathVariable("chatCode") String chatCode,
            @RequestBody Map<String, String> body, HttpServletRequest request)
    {
        ConsoleIdentity identity = ConsoleSession.require(request);
        String content = body == null ? null : body.get("content");
        return ok(chatService.consoleSend(chatCode, content, identity.getUserName()));
    }

    /** 标已读。运营点开某条会话时调一次 */
    @PostMapping("/{chatCode}/read")
    @RequirePerm("site:chat:list")
    public Map<String, Object> read(@PathVariable("chatCode") String chatCode)
    {
        chatService.consoleRead(chatCode);
        return ok(null);
    }

    // ⚠ 这两个外壳和 ConsoleApiController 里那两个是**同一套约定**（{code,msg,data} /
    //   {code,msg,total,rows}）。前端的 request.js 认的就是这个形状，别在这里发明新的。
    private Map<String, Object> ok(Object data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        return result;
    }

    private Map<String, Object> okTable(Map<String, Object> page)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "查询成功");
        result.put("total", page.get("total"));
        result.put("rows", page.get("rows"));
        return result;
    }
}
