package com.ruoyi.station.console.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云 API 3.0 的通用调用：TC3-HMAC-SHA256 签名 + 一个 POST。
 *
 * <p>2026-09-26 从 {@link TmtClient} 里抽出来：那边把 host / service / version 写死成机器翻译的，
 * 而混元生 3D（{@link Ai3dClient}）要的是同一套签名、不同的三个值。签名逻辑只该有一份——
 * 这类代码抄两份，哪天修了一边的 bug，另一边会在半夜 UTC 日期翻页时才炸。</p>
 *
 * <p>为什么不引官方 SDK、以及签名那几个「只有签错了才知道」的细节，见 {@link TmtClient} 的类注释。
 * 这里只用 JDK 自带的东西，不解析 JSON：拼请求体、解析响应、判业务错误码都交给调用方。</p>
 */
public final class TencentApiClient
{
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    static final String CONTENT_TYPE = "application/json; charset=utf-8";

    /** 日期按 UTC 算：用东八区的话，每天早上 8 点之前签出来的日期是错的 */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private TencentApiClient()
    {
    }

    /**
     * 调一次接口，返回响应体原文。
     *
     * <p>不判业务错误码：腾讯云把业务错误也放在 HTTP 200 的响应体里
     * （{@code {"Response":{"Error":{"Code":…}}}}），交给调用方翻译成商家看得懂的话。</p>
     *
     * @throws Exception 网络层失败，或者 HTTP 状态码不是 2xx
     */
    public static String call(String host, String service, String version, String action, String region,
            String payload, String secretId, String secretKey, Duration timeout) throws Exception
    {
        long timestamp = Instant.now().getEpochSecond();
        String authorization = authorization(host, service, action, payload, secretId, secretKey, timestamp);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host))
                .timeout(timeout)
                .header("Authorization", authorization)
                .header("Content-Type", CONTENT_TYPE)
                .header("X-TC-Action", action)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .header("X-TC-Version", version);
        if (region != null && !region.isBlank())
        {
            builder.header("X-TC-Region", region);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2)
        {
            throw new IllegalStateException("腾讯云接口 " + service + "." + action + " 返回 HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * 算 Authorization 头。纯函数（时间戳由调用方给），单测按固定输入钉住结果。
     */
    static String authorization(String host, String service, String action, String payload,
            String secretId, String secretKey, long timestamp) throws Exception
    {
        String date = DAY.format(Instant.ofEpochSecond(timestamp));

        // ---- 一、规范请求串 ----
        // 只签三个 header。签得越少越不容易错，而这三个是腾讯云要求必签的
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase(Locale.ROOT) + "\n";
        String canonicalRequest = "POST\n"
                + "/\n"
                + "\n"                       // 查询串：POST 走请求体，这里恒为空
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + sha256Hex(payload);

        // ---- 二、待签字符串 ----
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        // ---- 三、派生签名密钥（日期 → 服务 → tc3_request，逐级 HMAC）----
        byte[] secretDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, service);
        byte[] secretSigning = hmac(secretService, "tc3_request");

        // ---- 四、签名 ----
        String signature = hex(hmac(secretSigning, stringToSign));
        return ALGORITHM
                + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static byte[] hmac(byte[] key, String data) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) throws Exception
    {
        return hex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    /** 小写十六进制。⚠ 必须小写，腾讯云那边是按字符串比的 */
    private static String hex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
