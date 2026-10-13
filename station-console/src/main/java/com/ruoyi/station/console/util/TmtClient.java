package com.ruoyi.station.console.util;

import java.time.Duration;

/**
 * 腾讯云机器翻译（TMT）的最小客户端：TC3 签名 + 一个 POST。
 *
 * <h3>为什么不引官方 SDK</h3>
 * <p>和 {@link CosClient} 同一个理由，见那个类的类注释：这个工程跑在 Spring Boot 4 /
 * Jackson 3 上，官方 SDK 会带进一整串和它打架的传递依赖；而且<b>后端是在服务器上打包的</b>，
 * 多一个依赖就多一次「构建时要从外网拉包」的失败可能（那台机器的网络是受限的）。</p>
 *
 * <p>这里只用 JDK 自带的东西：{@code HttpClient} 发请求，{@code Mac} 算签名，
 * {@code MessageDigest} 算摘要。整个类没有任何第三方 import，连 JSON 都不解析——
 * 拼请求体和解析响应交给调用方，那边用的是工程里统一那个 Jackson 3 的 ObjectMapper。</p>
 *
 * <h3>密钥从哪来</h3>
 * <p><b>复用 COS 那一对</b>（{@code u_s_storage} 里 provider=tencent 那行）。
 * 腾讯云的 SecretId/SecretKey 是账号级的，一对通吃所有产品，不是每个产品一对。
 * 所以商家配过对象存储，翻译就能直接用，不用再填一遍——
 * 但<b>要在腾讯云控制台单独开通「机器翻译」这个服务</b>，没开通的表现是
 * 一个 {@code AuthFailure.UnauthorizedOperation} 错误码。</p>
 *
 * <h3>TC3-HMAC-SHA256 签名</h3>
 * <p>腾讯云 API 3.0 的签名方法，四步：拼规范请求串 → 拼待签字符串 → 用
 * 「日期/服务/tc3_request」三级派生出签名密钥 → HMAC 出签名。和 AWS SigV4 是一个路子。</p>
 *
 * <p>⚠ 几个只有签错了才会知道的细节，改这个类之前先看一遍：</p>
 * <ul>
 *   <li>规范请求串里的 <b>header 名要小写、值要 trim、并且按名字排序</b>，
 *       末尾还要多一个换行；</li>
 *   <li>{@code x-tc-action} 的<b>值</b>在规范请求串里也要<b>转小写</b>
 *       （{@code texttranslatebatch}），但真正发出去的那个 header 用原样大小写；</li>
 *   <li>时间戳是<b>秒</b>不是毫秒，日期要按 <b>UTC</b> 算——用服务器本地时区（东八区）
 *       算的话，每天早上 8 点之前那段时间签出来的日期是错的，
 *       表现是「白天好好的，一到半夜就全部签名失败」；</li>
 *   <li>Content-Type 必须和签名里那个一字不差，包括 {@code ; charset=utf-8} 这一段。</li>
 * </ul>
 */
public final class TmtClient
{
    private static final String HOST = "tmt.tencentcloudapi.com";
    private static final String SERVICE = "tmt";
    private static final String VERSION = "2018-03-21";

    /** 翻译是个同步的小请求，超时给短一点：商家在管理台点着按钮等结果 */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private TmtClient()
    {
    }

    /**
     * 调一次接口，返回响应体原文。签名和发送在 {@link TencentApiClient}（2026-09-26 抽出去的，
     * 混元生 3D 用同一套），这里只钉死机器翻译的 host / service / version。
     *
     * <p>不判业务错误码：腾讯云把业务错误也放在 HTTP 200 的响应体里
     * （{@code {"Response":{"Error":{"Code":…}}}}），那一层交给调用方，
     * 因为只有它知道该把哪个错误码翻译成哪句给商家看的话。</p>
     *
     * @param action    接口名，如 {@code TextTranslateBatch}
     * @param region    地域，如 {@code ap-shanghai}
     * @param payload   请求体 JSON
     * @param secretId  腾讯云 SecretId
     * @param secretKey 腾讯云 SecretKey
     * @return 响应体原文
     * @throws Exception 网络层失败，或者 HTTP 状态码不是 2xx
     */
    public static String call(String action, String region, String payload,
            String secretId, String secretKey) throws Exception
    {
        return TencentApiClient.call(HOST, SERVICE, VERSION, action, region, payload, secretId, secretKey, TIMEOUT);
    }
}
