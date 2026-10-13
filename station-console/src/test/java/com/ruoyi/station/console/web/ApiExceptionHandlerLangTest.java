package com.ruoyi.station.console.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.station.console.service.BuyerText;
import com.ruoyi.station.console.service.ServiceException;

/**
 * 带 BuyerText 键的异常，在管理台和买家两侧各回什么语言。
 *
 * <p>2026-09-24 之前，管理台请求（没有 SiteLang）也去 render，语言是 null 就落到英文兜底：
 * 管理台聊天的「说点什么再发」在中文界面上显示成 "Type a message first."。</p>
 */
class ApiExceptionHandlerLangTest
{
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @AfterEach
    void clear()
    {
        SiteLang.clear();
    }

    private String msgOf(ServiceException e)
    {
        Map<String, Object> body = handler.handleService(e).getBody();
        return (String) body.get("msg");
    }

    @Test
    @DisplayName("管理台请求（没有站点语言）：回中文原话，交给管理台前端按界面语言翻")
    void consoleGetsChinese()
    {
        assertEquals("说点什么再发", msgOf(BuyerText.error("chat.empty", 400)));
        assertEquals("会话不存在", msgOf(BuyerText.error("chat.notFound", 404)));
    }

    @Test
    @DisplayName("买家请求：照旧按站点语言渲染")
    void buyerGetsSiteLanguage()
    {
        SiteLang.set("ja");
        assertEquals("メッセージを入力してから送信してください。", msgOf(BuyerText.error("chat.empty", 400)));
        SiteLang.set("en");
        assertEquals("Type a message first.", msgOf(BuyerText.error("chat.empty", 400)));
        // fr 不在 put() 那八个参数里，是后面单独 fr(...) 补的——锁住它，别让法语站悄悄退回英文
        SiteLang.set("fr");
        assertEquals("Écrivez d'abord un message.", msgOf(BuyerText.error("chat.empty", 400)));
        assertEquals("Le mot de passe doit contenir au moins 8 caractères.",
                msgOf(BuyerText.error("auth.passwordShort", 400, 8)));
    }

    @Test
    @DisplayName("没有文案键的（管理台自己的提示）：不管有没有语言都原样出")
    void plainMessageUntouched()
    {
        assertEquals("商品编码不能为空", msgOf(new ServiceException("商品编码不能为空")));
        SiteLang.set("en");
        assertEquals("商品编码不能为空", msgOf(new ServiceException("商品编码不能为空")));
    }
}
