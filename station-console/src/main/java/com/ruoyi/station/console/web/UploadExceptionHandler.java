package com.ruoyi.station.console.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 「文件太大」的出口。
 *
 * <h3>为什么不并进 {@link ApiExceptionHandler}</h3>
 * <p>因为它<b>接不到</b>。{@code MaxUploadSizeExceededException} 是 DispatcherServlet 在
 * {@code checkMultipart} 里抛的，那时候还没走到路由，handler 是 null；而 ApiExceptionHandler
 * 上挂着 {@code assignableTypes = ConsoleApiController.class}，handler 为 null 时这个条件
 * 直接不成立。结果就是超限的上传落到 Spring 的默认错误页——前端拿到一段 HTML，
 * 控制台里报的是 JSON 解析错误，看不出真正的原因是文件超了。</p>
 *
 * <p>那个 {@code assignableTypes} 是不能去掉的（去掉会把 404 一起吞成 500，
 * 见 ApiExceptionHandler 的注释），所以这里单开一个不限定范围的 advice。
 * 它只认这一个异常类型，不会顺手接管别的东西。</p>
 */
@RestControllerAdvice
public class UploadExceptionHandler
{
    /**
     * <p>不打日志：这是用户操作的正常结果（选了个太大的文件），不是故障。
     * 打成 ERROR 的话，商家挑几次文件就在日志里刷出一片带栈的报错。</p>
     *
     * <p>HTTP 用 200 + body 里的 code，和 ERP 的响应外壳一致——前端的 request.js
     * 认的是 body 里的 code，不是 HTTP 状态码。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e)
    {
        return ResponseEntity.ok(Map.of("code", 500,
                "msg", "文件超出大小限制（单个文件最大 200MB），再大的素材建议直接传到自己的对象存储控制台"));
    }
}
