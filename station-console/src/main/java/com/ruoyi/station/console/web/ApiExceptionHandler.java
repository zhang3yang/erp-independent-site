package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.ServiceException;

/**
 * 接口的异常出口。
 *
 * <p>响应外壳是 {@code {code, msg}}，和 ERP 的 {@code AjaxResult} 一致——
 * 前端的 request.js 认的就是这个形状。</p>
 *
 * <h3>⚠ {@code assignableTypes} 那一句是必需的，不能去掉</h3>
 * <p>不限定范围的话，下面那个兜底的 {@code @ExceptionHandler(Exception.class)} 会把
 * Spring MVC 自己抛的 {@code NoResourceFoundException} 一起接管——于是任何一个不存在的地址
 * 都会返回 {@code 200 {code:500,"系统异常"}} 而不是 404，顺带给每一次扫描器的探测
 * 都打一条带栈的 ERROR 日志。限定到这几个 Controller 之后，没匹配上的路径回到 Spring 的默认 404。</p>
 *
 * <h3>为什么把 OpenApiController 也框进来了</h3>
 * <p>它是<b>对公网开放</b>的那一组。没有统一出口的话，一个 {@code ?page=abc}
 * 就会让访客看到一段 Spring 的默认错误页（带类名和路径）。
 * 这一层把它们压成一句人话。</p>
 *
 * <p>原来那个「连不上 ERP」的异常分支删掉了：那整套 S2S 客户端已经不存在了
 * （docs/独立站与ERP的边界.md §10.1）。以前那里还有一段解释「ERP 的 401/403
 * 为什么不会走到这里」——现在没有 ERP 这一跳，那段话连同它描述的情况一起消失了。</p>
 *
 * <h3>⚠ 新加的 Controller 必须登进下面那张表</h3>
 * <p>{@code assignableTypes} 是白名单，漏登一个的表现很难看出来：那个 Controller 抛的
 * {@link ServiceException} 没人接，落到 Spring 的默认错误处理，前端拿到的是
 * {@code 500 {"timestamp":..,"status":500,"error":"Internal Server Error","path":..}}——
 * <b>里面没有 msg</b>。于是那些写好的一句话（「密码至少 8 位」「邮箱还没验证」
 * 「本站还没登记主域名」）一句都到不了买家眼前，界面上只剩一个笼统的「请求失败」，
 * 而真正的原因只在服务端日志里。{@link BuyerAccountController} 就是这么漏了一阵子的。</p>
 */
@RestControllerAdvice(assignableTypes = {
        ConsoleApiController.class, OpenApiController.class, SessionController.class,
        BuyerAccountController.class,
        // 在线咨询的两组。SiteChatController 和 OpenApiController 一样是**对公网开放**的，
        // 框进来的理由就是上面「为什么把 OpenApiController 也框进来了」那一条；
        // ConsoleChatController 框进来是为了让它的异常和 /api/** 其余接口回同一个形状
        SiteChatController.class, ConsoleChatController.class,
        // 买家 3D 定制方案（2026-09-29），对公网开放，理由同 OpenApiController
        CustomPlanController.class })
public class ApiExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 业务异常：这句话是说给用户看的，原样返回。
     *
     * <p>{@code code} 用异常上带的那个（401 登录失效、403 无权限、500 一般业务失败），
     * 前端按它决定是弹提示还是跳登录页。</p>
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, Object>> handleService(ServiceException e)
    {
        // 不打日志：这是用户操作的正常结果（填错了、选错了），不是故障。
        // 打成 ERROR 的话，运营点几次就在日志里刷出一片带栈的报错
        // 带文案键的（买家看得到的那些）按请求语言重新渲染；没键的原样出（管理台那一侧）
        //
        // ⚠ 没有请求语言 = 这是管理台的请求（SiteLang 只在 /openapi/site/** 上设，买家那一侧一定有值）。
        //   这时回中文原话（异常的 message 就是 zh 那一份），和管理台其余提示一个口径，
        //   由管理台前端的 i18n/server.js 按界面语言翻。以前这里照样去 render，
        //   语言是 null 就落到英文兜底——管理台聊天那几句（「说点什么再发」）在中文界面上是英文，
        //   而且因为不是中文，前端那层也认不出来，法语界面上还是英文。
        String lang = SiteLang.current();
        String msg = e.getKey() == null || lang == null
                ? e.getMessage()
                : BuyerText.render(e.getKey(), lang, e.getArgs());
        return body(e.getCode(), msg);
    }

    /**
     * 兜底。
     *
     * <p>页面上只给一句通用提示，细节进日志：异常消息里可能带着内网地址、SQL 片段、
     * 类名——那些对排查有用，对访客只有帮助攻击的价值。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e)
    {
        log.error("接口异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                SiteLang.current() == null ? "系统异常，请稍后重试" : BuyerText.render("common.error", SiteLang.current()));
    }

    private ResponseEntity<Map<String, Object>> body(int code, String msg)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("msg", msg);
        // HTTP 一律 200，业务码放 body——和 ERP 同一套约定，前端只按 body 里的 code 判。
        // 两套判据混用是很多「有时候能拿到错误提示、有时候拿到的是空」的根源。
        //
        // ⚠ AuthInterceptor 是个例外：它回真实的 401/403 状态码。那一层在 Controller
        //   之外，nginx 日志和监控要能看出「有人在被拒」，而这一层是业务失败，
        //   对监控来说是正常流量。
        return ResponseEntity.ok(payload);
    }
}
