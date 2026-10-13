package com.ruoyi.station.console.web;

/**
 * 当前请求的前台语言（2026-09-22 起）。{@link SiteLangInterceptor} 在 {@code /openapi/site/**}
 * 上从请求头 {@code X-Site-Lang} 读出来放这里，{@code ApiExceptionHandler} 出错时按它渲染
 * {@code BuyerText} 里的句子；请求结束清掉。
 *
 * <p>为什么是请求头而不是每个接口加参数：买家侧有二十来个接口（登录、地址簿、头像、
 * 查单……），语言对它们都只是「报错用什么话说」，不是业务参数。前台在一个地方
 * （useBuyer / useOrders / useChat 的 fetch 封装）带上头，后端在一个地方读，两边都不用改接口。</p>
 *
 * <p>⚠ ThreadLocal。Servlet 线程池复用线程，所以拦截器的 afterCompletion 必须 {@link #clear()}，
 * 否则上一个日语请求的语言会漏给下一个没带头的请求。</p>
 */
public final class SiteLang
{
    private SiteLang()
    {
    }

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    /** 当前请求的语言码；没经过拦截器、或者没带头时为 null（那时按站点默认语言处理） */
    public static String current()
    {
        return CURRENT.get();
    }

    static void set(String lang)
    {
        if (lang == null || lang.isBlank())
        {
            CURRENT.remove();
        }
        else
        {
            CURRENT.set(lang);
        }
    }

    static void clear()
    {
        CURRENT.remove();
    }
}
