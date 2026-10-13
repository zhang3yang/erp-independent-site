package com.ruoyi.station.console.util;

import java.time.Duration;

/**
 * 腾讯混元生 3D（ai3d）的最小客户端：只钉死 host / service / version，签名和发送走 {@link TencentApiClient}。
 *
 * <p>用到两个接口（专业版，2026-09-26 查的官方文档）：</p>
 * <ul>
 *   <li>{@code SubmitHunyuanTo3DProJob}：提交，返回 {@code JobId}（24 小时有效）；</li>
 *   <li>{@code QueryHunyuanTo3DProJob}：按 JobId 查，{@code Status} 为 WAIT / RUN / DONE / FAIL，
 *       DONE 时结果在 {@code ResultFile3Ds[]}（Type / Url / PreviewImageUrl）。</li>
 * </ul>
 *
 * <p>拼请求体、解析响应、翻译错误码都在 {@code ModelGenService}，这里不碰 JSON。</p>
 *
 * <p>密钥复用「文件存储」里腾讯云那一对（和机器翻译一样，账号级密钥一对通吃）。
 * 那把密钥是子账号的话，要在访问管理里给它挂 {@code QcloudAI3DFullAccess}。</p>
 */
public final class Ai3dClient
{
    public static final String ACTION_SUBMIT = "SubmitHunyuanTo3DProJob";
    public static final String ACTION_QUERY = "QueryHunyuanTo3DProJob";

    private static final String HOST = "ai3d.tencentcloudapi.com";
    private static final String SERVICE = "ai3d";
    private static final String VERSION = "2025-05-13";

    /** 提交和查询都是秒回的小请求（生成本身是异步的），给 20 秒够了 */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private Ai3dClient()
    {
    }

    /** 调一次 ai3d 接口，返回响应体原文 */
    public static String call(String action, String region, String payload,
            String secretId, String secretKey) throws Exception
    {
        return TencentApiClient.call(HOST, SERVICE, VERSION, action, region, payload, secretId, secretKey, TIMEOUT);
    }
}
