package com.ruoyi.station.console.web;

import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import com.ruoyi.station.console.security.RequirePerm;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * {@code /api/**} 的门。判两件事：<b>有没有会话</b>、<b>有没有这个权限</b>。
 *
 * <h3>⚠ 权限判定为什么搬到这里来了</h3>
 * <p>以前这里只判会话，类注释里明写着「不判权限——权限由 ERP 在每个数据接口上现查，
 * 在这里再缓存一份只会让管理台持有第二份会过期的真相」。</p>
 *
 * <p><b>那个前提没有了。</b>docs/独立站与ERP的边界.md 要点 1：两边后端没有任何互通。
 * ERP 不再参与任何一次取数，所以也就没有「那边会再判一遍」这回事了——
 * <b>这一层就是唯一的授权点</b>。漏判一个接口，那个接口就是无权限的。</p>
 *
 * <p>权限串挂在每个方法的 {@link RequirePerm} 上，值来自会话里的 {@link ConsoleIdentity#getPerms()}，
 * 而那份是登录/进入时从本站的 {@code u_s_role} 读的。</p>
 *
 * <h3>没挂注解的接口一律拒绝</h3>
 * <p>这一条是刻意的：新加一个接口忘了挂注解，表现是「这个接口 403」——
 * 开发五分钟内就会发现。反过来（没注解就放行）表现是「这个接口谁都能调」，
 * 可能几个月都没人发现。<b>默认拒绝，不是默认放行。</b></p>
 *
 * <h3>会话有效期</h3>
 * <p>由 Redis 上的 session TTL 管（8 小时不活跃即失效）。
 * 权限变更有会话期的滞后，理由和取舍见 {@link ConsoleIdentity} 的类注释。</p>
 */
public class AuthInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /**
     * ⚠ 这句话前端会原样弹出来（{@code request.js} 的 handleExpired），所以它要说清楚
     * <b>怎么回来</b>：本站没有登录页，唯一入口是从 ERP 点「进入管理」。
     * 写「请重新登录」会让人在这个域名上找一个不存在的登录框。
     */
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"msg\":\"登录状态已失效。回到众联 ERP 的独立站管理页面，重新点【进入管理】\"}";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception
    {
        ConsoleIdentity identity = ConsoleSession.current(request);
        if (identity == null)
        {
            // /api/** 是给前端 XHR 用的，回 JSON 而不是 302——重定向到登录页的话，
            // 前端拿到的是一段 HTML，控制台里报的是 JSON 解析错误，看不出真正的原因是掉线了
            return write(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED_BODY);
        }

        // 非 Controller 方法（静态资源、错误页）不判权限，交给后面的处理
        if (!(handler instanceof HandlerMethod method))
        {
            return true;
        }

        RequirePerm required = method.getMethodAnnotation(RequirePerm.class);
        if (required == null)
        {
            // 见类注释：默认拒绝。这条日志是给开发看的，不是给运维看的
            log.error("接口 {}.{} 没有挂 @RequirePerm，按无权限拒绝。"
                    + "给它加一个注解——需要的权限串，或者 @RequirePerm() 表示只要登录",
                    method.getBeanType().getSimpleName(), method.getMethod().getName());
            return write(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"code\":403,\"msg\":\"这个接口没有配置权限，请联系开发\"}");
        }

        String perm = required.value();
        if (perm.isEmpty() || identity.hasPerm(perm))
        {
            return true;
        }

        log.warn("用户 {}（角色 {}）缺少权限 {}，拒绝 {} {}",
                identity.getUserName(), identity.getRoleCode(), perm,
                request.getMethod(), request.getRequestURI());
        return write(response, HttpServletResponse.SC_FORBIDDEN,
                "{\"code\":403,\"msg\":\"没有该操作的权限，需要联系站长授权\"}");
    }

    /**
     * 写响应体。
     *
     * <p>HTTP 状态码给真实的（401/403），body 里的 code 也给同一个值——
     * 前端的 request.js 认的是 body 里的 code，而 nginx 的 access log
     * 和监控认的是 HTTP 状态码。两边一致才不会出现「日志里全是 200，
     * 但用户说进不去」的情况。</p>
     */
    private boolean write(HttpServletResponse response, int status, String body) throws Exception
    {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
        return false;
    }
}
