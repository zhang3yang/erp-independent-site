package com.ruoyi.station.console.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云 COS 的最小上传客户端：签名 + 一个 PUT。
 *
 * <h3>为什么不引官方 SDK</h3>
 * <p>{@code com.qcloud:cos_api} 会带进 httpclient 4、jackson-databind 2、commons-logging
 * 一整串传递依赖，而这个工程跑在 Spring Boot 4 上、用的是 Jackson 3
 * （见 pom.xml 顶部那段）。为了「上传一个对象」这一个动作付那个代价不划算，
 * 而且多一套依赖就多一处将来会和 Boot 升级打架的地方。</p>
 *
 * <p>这里只用 JDK 自带的东西：{@link HttpClient} 发请求，{@link Mac} 算签名。
 * 整个类没有任何第三方 import。</p>
 *
 * <h3>只有 PUT，没有 DELETE</h3>
 * <p>删文件时{@code SiteFileService.removeImage} 只删库记录、桶里的对象留着——
 * 那个文件可能已经被首页装修或商品详情引用了，删掉的后果是几天后有人发现页面裂图。
 * 孤儿对象由运维定期清理，那是一个可以先对一遍引用的动作。所以这里不需要删除能力，
 * <b>也不要顺手加一个</b>：加了就一定有人会去调它。</p>
 *
 * <h3>⚠ 上传走的是服务器的公网带宽</h3>
 * <p>链路是「浏览器 → 这台服务器 → COS」，同一份数据在服务器的网卡上进出各一次。
 * 这台机器的公网带宽是 3 Mbps，传一个 200MB 的视频光是第二段就要十几分钟。</p>
 *
 * <p><b>解法是把 Endpoint 配成内网域名</b>：CVM 和存储桶在同一个地域（都在上海）时，
 * {@code https://cos-internal.ap-shanghai.myqcloud.com} 走的是腾讯云内网——不限速、不计流量费。
 * 公网域名 {@code cos.ap-shanghai.myqcloud.com} 也能用，只是慢且花钱。</p>
 *
 * <p>⚠ 内网域名只在<b>同地域</b>有效。哪天服务器迁出上海，这个 Endpoint 会连不上，
 * 表现是上传超时——那时候改回公网域名即可。</p>
 */
public final class CosClient
{
    /**
     * 连接超时给 10 秒，整体超时给 10 分钟。
     *
     * <p>整体超时必须给得大：200MB 的视频经公网上传本来就要十几分钟。给小了的表现是
     * 「小图片能传、大视频总是失败」，而且失败在传输过程中，日志里只有一个 timeout。</p>
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 签名有效期。只是这一次请求用，给 10 分钟足够覆盖一次大文件上传 */
    private static final long SIGN_VALID_SECONDS = 600;

    /**
     * 网络抖动时重试几次。
     *
     * <p>只对<b>连接层面的失败</b>重试（超时、连接被重置）。COS 明确拒绝的请求
     * （签名不对、桶不存在、没权限）重试一万次也是同一个结果，只会把一次失败拖成三次，
     * 让用户多等两倍时间才看到那句本来就该立刻看到的错误提示。</p>
     */
    private static final int MAX_RETRY = 2;

    private CosClient()
    {
    }

    /**
     * COS 明确拒绝了这次请求——签名、权限、桶名、地域之类。
     *
     * <p>和网络错误分开是有意义的：这一类<b>重试没有用</b>，而且错误码本身就是处置方式
     * （{@code SignatureDoesNotMatch} 去查密钥、{@code NoSuchBucket} 去查桶名和地域、
     * {@code AccessDenied} 去查这把密钥有没有这个桶的权限）。调用方应该把
     * {@link #getErrorCode()} 显示给用户，而不是笼统地说一句「上传失败」。</p>
     */
    public static class CosServiceException extends IOException
    {
        private static final long serialVersionUID = 1L;

        private final String errorCode;

        CosServiceException(int status, String errorCode, String message)
        {
            super("COS 返回 " + status + " " + errorCode + "：" + message);
            this.errorCode = errorCode;
        }

        /** COS 错误体里的 {@code <Code>}，取不到时是 {@code HTTP <状态码>} */
        public String getErrorCode()
        {
            return errorCode;
        }
    }

    /**
     * 文件流的来源。
     *
     * <h3>⚠ 为什么是「来源」而不是一个 {@code InputStream}</h3>
     * <p>因为要重试。流只能读一次——第一次尝试把它读完之后，重试时再推同一个流，
     * 发出去的是 0 字节，而 COS 会开开心心地存下一个空文件并返回 200。
     * 那种失败比直接报错糟得多：库里有记录、页面上是裂图、桶里躺着一个 0 字节对象。</p>
     *
     * <p>{@code MultipartFile::getInputStream} 每次调用都会重新打开临时文件，
     * 正好符合这个约定。</p>
     */
    @FunctionalInterface
    public interface StreamSource
    {
        InputStream open() throws IOException;
    }

    /**
     * 上传一个对象。
     *
     * @param host        桶的访问域名，形如 {@code bucket-1250000000.cos.ap-shanghai.myqcloud.com}
     * @param key         对象键，不带前导斜杠，形如 {@code image/2026/09/05/IMG0001.jpg}
     * @param secretId    腾讯云 SecretId
     * @param secretKey   腾讯云 SecretKey（明文，调用方负责解密）
     * @param source      文件流的来源，<b>每次尝试都会重新 open 一个</b>，用完由本方法关闭
     * @param length      字节数。必须准确——它会作为 Content-Length 发出去
     * @param contentType MIME 类型，为空则交给 COS 按扩展名推断
     * @throws CosServiceException COS 明确拒绝（签名 / 权限 / 桶名 / 地域），不重试
     * @throws IOException         网络错误，且重试若干次后仍然失败
     */
    public static void put(String host, String key, String secretId, String secretKey,
            StreamSource source, long length, String contentType) throws IOException
    {
        String path = "/" + encodePath(key);
        IOException last = null;

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++)
        {
            // ⚠ 签名每次重算，不能复用上一次的。签名里带着时间戳，而重试之间可能隔了
            //   十几秒——虽然有效期给了 10 分钟、复用大概率也没事，但「大概率没事」
            //   正是那种在生产上偶发失败、在测试里永远复现不了的东西
            String authorization = authorization("put", path, host, secretId, secretKey);

            try (InputStream in = source.open())
            {
                // ofInputStream 默认用 chunked 编码（Content-Length 未知），而 COS 对
                // chunked 的 PUT 支持不稳。fromPublisher 带上准确长度，发出去的就是
                // 定长请求；同时它仍然是流式的，200MB 的文件不会进堆。
                HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.fromPublisher(
                        HttpRequest.BodyPublishers.ofInputStream(() -> in), length);

                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://" + host + path))
                        .header("Authorization", authorization)
                        .timeout(Duration.ofMinutes(10))
                        .PUT(publisher);
                if (contentType != null && !contentType.isBlank())
                {
                    builder.header("Content-Type", contentType);
                }

                HttpResponse<String> response = CLIENT.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (status >= 200 && status < 300)
                {
                    return;
                }
                // 非 2xx 一律当作「服务端明确拒绝」，直接抛，不重试。
                // 5xx 理论上值得重一次，但 COS 的 5xx 极少见，而多等两轮超时的代价是确定的
                throw new CosServiceException(status, errorCode(response.body(), status),
                        brief(response.body()));
            }
            catch (CosServiceException e)
            {
                throw e;
            }
            catch (IOException e)
            {
                // 连接超时、连接被重置、读流失败——这一类重试有意义
                last = e;
            }
            catch (InterruptedException e)
            {
                // 恢复中断标志再抛。吞掉它的话，上层线程池的关闭信号就丢了
                Thread.currentThread().interrupt();
                throw new IOException("上传被中断", e);
            }
        }
        throw new IOException("上传 COS 失败，已重试 " + MAX_RETRY + " 次", last);
    }

    /**
     * 从 COS 的错误体里抠出 {@code <Code>}。
     *
     * <p>只用正则不做 XML 解析：这里要的就是一个短字符串，引一个 XML 解析器、
     * 或者处理一份可能被截断的响应体，都不值得。取不到就用 HTTP 状态码顶上——
     * 调用方要把它显示给用户，不能是 null。</p>
     */
    private static String errorCode(String body, int status)
    {
        if (body != null)
        {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<Code>([^<]+)</Code>").matcher(body);
            if (m.find())
            {
                return m.group(1);
            }
        }
        return "HTTP " + status;
    }

    // ==================== 签名 ====================

    /**
     * 算 Authorization 头。腾讯云 COS 的签名（俗称 sign v5），和 AWS SigV4 不是一回事。
     *
     * <pre>
     *   KeyTime      = 开始时间戳;结束时间戳
     *   SignKey      = HMAC-SHA1(SecretKey, KeyTime)
     *   HttpString   = 方法小写 \n 路径 \n 查询串 \n 头部串 \n
     *   StringToSign = "sha1" \n KeyTime \n SHA1(HttpString) \n
     *   Signature    = HMAC-SHA1(SignKey, StringToSign)
     * </pre>
     *
     * <h3>只签 host 一个头</h3>
     * <p>COS 只校验 {@code q-header-list} 里列出的那些头，没列的头它不管。
     * 签得越多，出错面越大——{@code Content-Type} 被中间层改一下大小写、
     * {@code Content-Length} 被 HttpClient 自己算得不一样，都会变成
     * {@code SignatureDoesNotMatch}，而那个错看不出是哪个头对不上。</p>
     *
     * <p>只签 {@code host} 就够了：它决定了这个签名只对这一个桶有效。</p>
     *
     * <h3>⚠ 开始时间往前推 60 秒</h3>
     * <p>服务器时钟和 COS 的差几秒是常事。不往前推的话，时钟快一点点就会得到
     * 「签名尚未生效」，而这种错是间歇性的——最难查的那一类。</p>
     */
    private static String authorization(String method, String path, String host,
            String secretId, String secretKey)
    {
        long now = System.currentTimeMillis() / 1000L;
        String keyTime = (now - 60) + ";" + (now + SIGN_VALID_SECONDS);

        String signKey = hmacSha1Hex(keyTime, secretKey);

        // 头部串：键小写、按键排序、键和值都做 RFC3986 编码。这里只有 host 一个
        String headerList = "host";
        String headerString = "host=" + rfc3986(host);

        // 查询串：PUT 对象没有查询参数，两个都留空。留空不是省略——
        // HttpString 里那两个空行必须在，少一个换行整个签名就不对
        String paramList = "";
        String paramString = "";

        String httpString = method + "\n" + path + "\n" + paramString + "\n" + headerString + "\n";
        String stringToSign = "sha1\n" + keyTime + "\n" + sha1Hex(httpString) + "\n";
        String signature = hmacSha1Hex(stringToSign, signKey);

        return "q-sign-algorithm=sha1"
                + "&q-ak=" + secretId
                + "&q-sign-time=" + keyTime
                + "&q-key-time=" + keyTime
                + "&q-header-list=" + headerList
                + "&q-url-param-list=" + paramList
                + "&q-signature=" + signature;
    }

    private static String hmacSha1Hex(String data, String key)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            // HmacSHA1 是 JDK 必须实现的算法，取不到只可能是运行环境被裁剪过
            throw new IllegalStateException("算签名失败", e);
        }
    }

    private static String sha1Hex(String data)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return hex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("算摘要失败", e);
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 路径编码：逐段编码，{@code /} 保留。
     *
     * <p>对象键是本服务自己拼的（{@code image/年/月/日/发号器id.扩展名}），
     * 目前全是安全字符，这一步等于没做事。留着是因为哪天有人把原始文件名拼进 key，
     * 没有这一步就会同时坏两样：请求 URL 和签名——而且两者的坏法不一样。</p>
     */
    private static String encodePath(String key)
    {
        StringBuilder sb = new StringBuilder();
        for (String segment : key.split("/", -1))
        {
            if (sb.length() > 0)
            {
                sb.append('/');
            }
            sb.append(rfc3986(segment));
        }
        return sb.toString();
    }

    /**
     * RFC3986 编码。
     *
     * <p>{@link URLEncoder} 是 form 编码，和 RFC3986 差三处：空格变 {@code +}、
     * {@code *} 不编码、{@code ~} 被编码。COS 按 RFC3986 校验，这三处不修就是
     * {@code SignatureDoesNotMatch}。</p>
     */
    private static String rfc3986(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    /** 错误体可能很长（COS 会带 RequestId、TraceId），截断后进日志 */
    private static String brief(String body)
    {
        if (body == null)
        {
            return "(空响应)";
        }
        String trimmed = body.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }

    /**
     * 一次匿名 GET 的结果。{@code body} 已经截断过，只够用来判「取回来的是不是我刚放上去那个」。
     */
    public record PublicProbe(int status, String body)
    {
        public boolean ok()
        {
            return status >= 200 && status < 300;
        }
    }

    /**
     * 匿名 GET 一个<b>公开</b>地址。存储配置的连通性自检用，不签名。
     *
     * <h3>为什么必须是匿名的</h3>
     * <p>这个请求要模拟的是「访客的浏览器去取一张图」。带上签名就能取到私有桶里的对象，
     * 那样自检会在一个私有桶上通过，而真正的访客拿到的是 403——自检恰好放过了
     * 它唯一要拦的那种配置。</p>
     *
     * <h3>不跟随重定向</h3>
     * <p>{@link #CLIENT} 配的是 {@code Redirect.NEVER}，这里正好要这个行为：
     * 自定义域名回一个 3xx 说明它指向了别处（常见于域名还挂在建站工具、或者
     * CDN 配了强制跳转），那些地址拿来存进 {@code imageurl} 早晚要出事。
     * 让它以非 2xx 的形式失败，比悄悄跟过去看到一个 200 好。</p>
     *
     * <p>超时给 15 秒：探针只有几十字节，慢过这个数说明链路本身有问题。</p>
     *
     * @param url 完整的公开地址
     * @throws IOException DNS 解不出、连不上、超时、证书不对——都归到这里
     */
    public static PublicProbe getPublic(String url) throws IOException
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new PublicProbe(response.statusCode(), brief(response.body()));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("自检被中断", e);
        }
        catch (IllegalArgumentException e)
        {
            // URI.create 对不合法的地址抛的是 IllegalArgumentException（RuntimeException），
            // 不裹一层的话它会穿过调用方那圈 catch(IOException) 变成 500
            throw new IOException("地址不合法：" + url, e);
        }
    }

    /**
     * 从「Endpoint + 桶名」推出真正要请求的主机名。
     *
     * <p>管理台那个输入框里填的可能是三种形态，这里都认：</p>
     * <ul>
     *   <li>{@code https://cos.ap-shanghai.myqcloud.com} —— 地域级 Endpoint，最常见，
     *       前面拼上桶名；</li>
     *   <li>{@code https://mybucket-125.cos.ap-shanghai.myqcloud.com} —— 已经带了桶名，
     *       原样用（再拼一次会变成 {@code mybucket.mybucket.cos...}，
     *       报的是 NoSuchBucket，看着像桶名填错了）；</li>
     *   <li>{@code cos.ap-shanghai.myqcloud.com} —— 没带协议头，去掉协议这一步本来就要做。</li>
     * </ul>
     */
    public static String resolveHost(String endpoint, String bucket)
    {
        String host = endpoint == null ? "" : endpoint.trim();
        host = host.replaceFirst("(?i)^https?://", "");
        int slash = host.indexOf('/');
        if (slash >= 0)
        {
            host = host.substring(0, slash);
        }
        String name = bucket == null ? "" : bucket.trim();
        if (name.isEmpty() || host.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + "."))
        {
            return host;
        }
        return name + "." + host;
    }
}
