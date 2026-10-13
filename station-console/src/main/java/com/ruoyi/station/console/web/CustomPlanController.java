package com.ruoyi.station.console.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.CustomPlanRateLimiter;
import com.ruoyi.station.console.service.CustomPlanService;

/**
 * 买家 3D 定制方案的前台接口，2026-09-29。和 {@link OpenApiController} 一样<b>对公网开放、匿名可调</b>。
 * 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md。
 *
 * <p>单独一个类是因为它收 multipart，而且带着自己的限流器；路径仍在 {@code /openapi/site/} 下
 * （nginx 那条同源转发、语言拦截器都是现成的）。⚠ 它必须登记在 {@link ApiExceptionHandler}
 * 的 {@code assignableTypes} 里，否则抛出来的业务错误会变成 Spring 的默认 500 页。</p>
 *
 * <p>⚠ 生产的 nginx 默认只收 1MB 的请求体，保存方案一次最多约 12MB——
 * 主域 {@code location /openapi/site/} 要配 {@code client_max_body_size 16m}，否则前台拿到 413。</p>
 */
@RestController
@RequestMapping("/openapi/site/customize")
public class CustomPlanController
{
    private static final Logger log = LoggerFactory.getLogger(CustomPlanController.class);

    /** 蜜罐命中时回的假方案号：格式对得上，库里不存在 */
    private static final String FAKE_PLAN_ID = "0000000000000000";

    @Autowired
    private CustomPlanService planService;

    /**
     * 保存方案的限流。⚠ 独立的一个，别复用询盘那个——那个 Redis 挂了放行，这个要拒绝，
     * 见 {@link CustomPlanRateLimiter} 类注释。
     */
    @Autowired
    private CustomPlanRateLimiter rateLimiter;

    /** 定制页数据：商品名、主图、模型、商家的屏幕和开机键。商品不能定制时 404 */
    @GetMapping("/{goodsCode}")
    public Map<String, Object> editor(@PathVariable("goodsCode") String goodsCode,
            @RequestParam(value = "lang", required = false) String lang)
    {
        Map<String, Object> view = planService.editorView(goodsCode, lang);
        if (view == null)
        {
            throw BuyerText.error("custom.unavailable", 404);
        }
        return ok(view);
    }

    /**
     * 保存方案（multipart：{@code data} JSON 串 + 0–6 个 {@code files}）。
     *
     * <p>顺序：限流 → 蜜罐 → 校验（全部在 service 里，通过之前不写 COS）→ 存图 → 落库。
     * 限流和蜜罐的反馈刻意不同，理由同 {@link OpenApiController#submitInquiry}：
     * 蜜罐命中装作成功（回假方案号），限流命中如实 429。</p>
     */
    @PostMapping("/plan")
    public Map<String, Object> save(@RequestParam(value = "data", required = false) String data,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request)
    {
        String clientIp = SsoController.clientIp(request);

        // ⚠ 必须在写 COS 之前
        if (!rateLimiter.tryAcquire(clientIp))
        {
            Map<String, Object> limited = new LinkedHashMap<>();
            limited.put("code", 429);
            limited.put("msg", BuyerText.render("custom.tooFrequent", SiteLang.current()));
            return limited;
        }

        if (data != null && CustomPlanService.isHoneypot(data))
        {
            log.info("拦下一条机器人提交的定制方案，IP {}", clientIp);
            return ok(Map.of("planId", FAKE_PLAN_ID));
        }

        String planId = planService.save(data, files, clientIp);
        return ok(Map.of("planId", planId));
    }

    /** 读方案（公开视图，不含邮箱和备注）。不存在、id 格式不对回同一句 404 */
    @GetMapping("/plan/{planId}")
    public Map<String, Object> plan(@PathVariable("planId") String planId,
            @RequestParam(value = "lang", required = false) String lang)
    {
        Map<String, Object> view = planService.publicView(planId, lang);
        if (view == null)
        {
            throw BuyerText.error("custom.notFound", 404);
        }
        return ok(view);
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
