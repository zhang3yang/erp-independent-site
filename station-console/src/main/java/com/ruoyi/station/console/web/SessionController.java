package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.station.console.security.RequirePerm;
import com.ruoyi.station.console.session.ConsoleIdentity;
import com.ruoyi.station.console.session.ConsoleSession;

/**
 * 会话的出口。<b>只有退出，没有登录。</b>
 *
 * <h3>⚠ 这个类以前叫 LoginController，账号密码登录整个删掉了</h3>
 * <p>它原来有三个接口：{@code GET /auth/state}（登录页要显示的信息）、
 * {@code POST /auth/login}（账号密码登录）、{@code POST /api/auth/password}（改密码）。
 * 依据是 docs/独立站与ERP的边界.md §6a「独立站要有自己的登录页」。</p>
 *
 * <p><b>§6a 作废</b>，回到 docs/独立站与ERP的边界.md §6d 那条：</p>
 *
 * <blockquote>
 * <b>这一条比前面所有加起来都重要。</b>入口做得再严，只要管理台还留着 {@code /login}
 * 和账号密码，别人就直接走那扇门。
 * </blockquote>
 *
 * <p>所以现在<b>唯一的入场动作是 {@code POST /sso}</b>（{@link SsoController}）——
 * 从 ERP 的「独立站管理」点「进入管理」免密进来。直接打开 {@code /login}
 * 看到的是一个空页面，上面没有任何可以输入的东西。</p>
 *
 * <h3>这么改丢掉了什么，要知道</h3>
 * <p>§6a 当初的理由是真的：<b>ERP 挂了或者网断了，就没有人能新进这个后台</b>
 * （已经建立的会话不受影响，Redis 里的 TTL 是 8 小时）。
 * 换来的是「本站没有第二套可以被爆破、被撞库、被弱口令攻破的凭据」。
 * 这是一个取舍，不是一个疏漏——要应急入口就单独做，加 IP 白名单 + 二次验证，
 * 和这条正常链路完全分开（sso 文档 §4.3 末段），<b>不要把登录页加回来</b>。</p>
 *
 * <h3>为什么退出在 {@code /api/} 下</h3>
 * <p>{@code AuthInterceptor} 拦的是整个 {@code /api/**}，规则是「一律要会话 + 要权限」。
 * 退出本来就需要会话——没会话的人没什么可退的——所以它待在规则里面，
 * 这一层<b>一个例外都没有</b>。例外列表是会被忘记维护的东西，下一个人加接口时不会去看它。</p>
 */
@RestController
public class SessionController
{
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    /**
     * 退出。
     *
     * <p>退出之后前端去 {@code /login?reason=logged-out}，那是一个空页面——
     * 要再进来只能回 ERP 再点一次「进入管理」。</p>
     */
    @PostMapping("/api/auth/logout")
    @RequirePerm
    public Map<String, Object> logout(HttpServletRequest request)
    {
        ConsoleIdentity identity = ConsoleSession.current(request);
        if (identity != null)
        {
            log.info("管理台退出：{}（{}）", identity.displayName(), identity.getUserName());
        }
        ConsoleSession.end(request);
        return ok(null);
    }

    /** 响应外壳和管理台其余接口一致：{@code {code, msg, data}} */
    private Map<String, Object> ok(Object data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        if (data != null)
        {
            result.put("data", data);
        }
        return result;
    }
}
