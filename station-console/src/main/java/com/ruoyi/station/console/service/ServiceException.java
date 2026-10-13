package com.ruoyi.station.console.service;

/**
 * 业务异常。message 是给人看的一句话，code 是 HTTP 状态码语义（400 / 401 / 404 / 429 / 500）。
 *
 * <p>2026-09-22 起多了一对 {@code key} / {@code args}：<b>买家</b>看得到的那些句子用
 * {@link BuyerText#error} 抛，带着文案键；{@code ApiExceptionHandler} 出响应时按请求语言
 * 重新渲染。message 仍然是中文——日志和管理台看到的还是原来那句。
 * 管理台那一侧的异常继续用前两个构造器，key 为 null，原样输出。</p>
 */
public class ServiceException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;
    /** 文案键（{@link BuyerText}），没有 = 不翻，原样输出 message */
    private final String key;
    private final Object[] args;

    public ServiceException(String message)
    {
        this(message, 500);
    }

    public ServiceException(String message, int code)
    {
        this(message, code, null);
    }

    public ServiceException(String message, int code, String key, Object... args)
    {
        super(message);
        this.code = code;
        this.key = key;
        this.args = args == null ? new Object[0] : args;
    }

    public int getCode()
    {
        return code;
    }

    public String getKey()
    {
        return key;
    }

    public Object[] getArgs()
    {
        return args;
    }
}
