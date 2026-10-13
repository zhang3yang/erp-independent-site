package com.ruoyi.station.console.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本站自己的配置。对应 {@code station.*}。
 *
 * <p>⚠ 这里<b>一个 ERP 地址都没有</b>，将来也不该有。
 * docs/独立站与ERP的边界.md 要点 1：独立站与 ERP 的后端没有任何互通。
 * 验收方式见 §13 第 2 条——{@code grep -rn "erp\.base-url" station-console/src} 必须零结果。</p>
 */
@ConfigurationProperties(prefix = "station")
public class StationProperties
{
    private Upload upload = new Upload();
    private Jump jump = new Jump();
    private Inquiry inquiry = new Inquiry();
    private Cors cors = new Cors();
    private Buyer buyer = new Buyer();
    private Chat chat = new Chat();
    private PageView pageview = new PageView();
    private OrderLookup orderLookup = new OrderLookup();
    private CustomPlan customPlan = new CustomPlan();

    /**
     * 前台那两个 Cookie（{@code BUYER_SESSION}、{@code CHAT_VISITOR}）的 Domain 作用域。
     * <b>留空 = 不设 Domain，也就是 host-only</b>（默认）。
     *
     * <h3>为什么会需要它</h3>
     * <p>前台劈成了两个主机名（{@code deploy/nginx/tongling.conf}）：</p>
     * <pre>
     * tongling.vclinker.com        公开页：首页、商品、购物车、我的询价
     * buyer.tongling.vclinker.com  账号页：登录、注册、找回密码、重置密码、邮箱验证、账户
     * </pre>
     * <p>登录发生在 {@code buyer.} 上，而<b>用到那个会话的页面在主域上</b>——
     * 顶栏要判断显示「Sign in」还是买家名字（{@code GET /account/me}），
     * 「我的询价」要按身份拉服务端那份订单，下单要把单挂到买家名下。
     * host-only 的 Cookie 带不到主域去，表现是<b>登录成功了，回到主域还是未登录</b>，
     * 而且不报任何错。</p>
     *
     * <p>所以这里填两个 host 的公共父域：{@code tongling.vclinker.com}。
     * 按 RFC 6265，Domain 写成 {@code tongling.vclinker.com} 同时覆盖它自己和它的子域，
     * <b>不要写成前面带点的 {@code .tongling.vclinker.com}</b>——那是 RFC 2109 的老写法，
     * 现代浏览器会把前导点忽略掉，但 Servlet 容器和一些反代不一定，多一个不必要的差异面。</p>
     *
     * <h3>⚠ 代价：作用域里的每一个子域都会收到这个 Cookie</h3>
     * <p>包括 {@code console.}（管理台）和 {@code data.}（开放接口）。这是放宽作用域
     * 换来的，接受它的理由：</p>
     * <ul>
     *   <li>两个 Cookie 都是 {@code HttpOnly}，那些子域上的 JS 读不到；</li>
     *   <li>管理台和买家是两套会话、两个 Cookie 名，收到一个不认识的 Cookie 会被忽略；</li>
     *   <li>另一条路是「让 buyer. 和主域各自登录一次」，那不是安全性更好，
     *       是把同一个账号拆成两个会话——买家会看到自己在一个域名上登着、
     *       在另一个上没登。</li>
     * </ul>
     * <p><b>但不要把作用域再往上放到根域 {@code vclinker.com}。</b>那上面还挂着 ERP
     * （{@code console.erp.vclinker.com}）和别的客户站，等于把买家会话发给一堆无关系统。</p>
     *
     * <h3>⚠ 必须和前台的 {@code NUXT_PUBLIC_SITE_URL} 对得上</h3>
     * <p>前台那边同名的东西是币种 Cookie 的作用域，它是<b>从 site-url 的主机名推出来的</b>
     * （{@code station-site/app/composables/useSiteHosts.ts} 的 {@code cookieDomain}）。
     * 两边算出不同的值不会报错，只会让「币种记住了但登录状态没记住」这种半截失效出现。</p>
     */
    private String cookieDomain = "";

    /**
     * 规整后的 Cookie 作用域：空串一律当成 {@code null}（= 不调 {@code setDomain}）。
     *
     * <p>顺手剥掉前导点：环境变量里被人写成 {@code .tongling.vclinker.com} 是很常见的，
     * 而带点那个写法见上面的说明。</p>
     *
     * <h3>⚠ 2026-09-09 起没有调用方了，别再接回去</h3>
     *
     * <p>{@link com.ruoyi.station.console.session.BuyerSession} 和
     * {@link com.ruoyi.station.console.session.ChatVisitor} 原来用它放宽 Cookie 作用域，
     * 现在两个都改回<b>永远 host-only</b>，理由见 {@code BuyerSession} 类注释
     * 「Domain 作用域」那一节。一句话：{@code file.<主域>} 是 CNAME 到腾讯云 COS 的，
     * 带 Domain 的 Cookie 会跟着每一张图片请求发到第三方。</p>
     *
     * <p>两个 host 共用登录状态靠的是
     * {@link com.ruoyi.station.console.session.BuyerHandoff}——一张 60 秒的一次性票
     * 把<b>同一个令牌</b>交给另一个域，两边各种一份 host-only Cookie。</p>
     *
     * <p>方法和配置项留着没删，是因为前台的币种偏好 Cookie 仍然按这个思路跨域共享
     * （那是个 {@code USD} 这样的字符串，不标识人）。<b>但服务端这三个
     * ——会话、访客、聊天线程——都不该再用它。</b></p>
     */
    public String normalizedCookieDomain()
    {
        String value = cookieDomain == null ? "" : cookieDomain.trim();
        while (value.startsWith("."))
        {
            value = value.substring(1);
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 加密 {@code u_s_storage.access_key_secret} 用的主密钥。
     *
     * <p>和 ERP 的 {@code OSS_CONFIG_SECRET} 用的是同一个工具类（{@code AesUtils}），
     * 但<b>值是本站自己的</b>——两边不共享任何密钥。</p>
     *
     * <p>一期文件落本地磁盘，用不到它。留着是为了将来接对象存储时不用改配置结构。</p>
     */
    private String configSecret;

    /** 上传落盘的配置 */
    public static class Upload
    {
        /** 文件根目录。nginx 里 {@code file.<主域名>} 的 root 指向这里 */
        private String dir = "/data/station/upload";

        /**
         * 拼进 {@code imageurl} / {@code videourl} 的前缀。
         *
         * <p>⚠ 必须和 nginx 里 file 子域的配置对得上，否则库里存的地址指向一个 404。
         * 而且<b>存的是完整 URL</b>——这一点和 ERP 那边一样，换域名要处理历史数据
         * （独立站部署手册（Windows）.md 附录 D.1 说的就是这件事）。上线前把它定下来。</p>
         */
        private String baseUrl = "";

        /**
         * 允许的图片扩展名，逗号分隔。白名单不是黑名单——黑名单永远漏。
         *
         * <h3>⚠ svg 被拿掉了，不要加回来</h3>
         * <p>SVG 不是一种图片格式，它是一份 XML 文档，可以内嵌 {@code <script>}、
         * {@code <foreignObject>} 和 {@code onload=}。而这些文件是由
         * {@code file.<主域名>} <b>以原始 Content-Type 直接发出去</b>的
         * （WebMvcConfig 的 {@code /files/**}，生产上是 nginx），
         * 浏览器直接打开一个 .svg 就会执行里面的脚本。</p>
         *
         * <p>它曾经在这份白名单里——那是这一列里<b>唯一一个能执行脚本的格式</b>。
         * 因为文件在独立子域上，影响面比同源小（拿不到管理台的会话 Cookie），
         * 但「一个我们自己域名下的、任何人可以让它执行任意脚本的地址」本身就是个把柄：
         * 拿它做钓鱼页、拿它当 XSS 的落地点都成立。</p>
         *
         * <p>真的要支持矢量 logo，正确做法是发的时候强制
         * {@code Content-Type: image/svg+xml} 外加 {@code Content-Disposition: attachment}
         * 和一条 {@code Content-Security-Policy: sandbox}，而不是把它放回白名单。</p>
         */
        private String imageExt = "jpg,jpeg,png,gif,webp,bmp";

        /**
         * 允许的视频扩展名，逗号分隔。
         *
         * <p>限制的不是「能不能传上去」，是「传上去之后浏览器能不能播」——
         * 磁盘不挑格式，但 {@code <video>} 标签只认这几种，
         * 放行别的等于让运营传一个永远打不开的文件。</p>
         */
        private String videoExt = "mp4,webm,mov,m4v";

        /**
         * 允许的 3D 模型扩展名，逗号分隔。默认只有 {@code glb}。
         *
         * <p>{@code gltf} 故意不放行：它是多文件格式，几何在外链的 {@code .bin}、贴图在外链
         * 图片里，单文件上传上去只有一份 JSON 骨架，前台 GLTFLoader 一定报 404。
         * {@code glb} 是把这些打成一个二进制包的版本，建模软件都能直接导出。</p>
         */
        private String modelExt = "glb";

        public List<String> imageExtList() { return split(imageExt); }

        public List<String> videoExtList() { return split(videoExt); }

        public List<String> modelExtList() { return split(modelExt); }

        /** 去掉末尾斜杠，拼 URL 时就不用担心出现双斜杠 */
        public String normalizedBaseUrl()
        {
            String value = baseUrl == null ? "" : baseUrl.trim();
            while (value.endsWith("/"))
            {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }

        private static List<String> split(String text)
        {
            List<String> result = new ArrayList<>();
            if (text == null)
            {
                return result;
            }
            for (String item : text.split(","))
            {
                String ext = item == null ? null : item.trim().toLowerCase(Locale.ROOT);
                if (ext != null && !ext.isEmpty() && !result.contains(ext))
                {
                    result.add(ext);
                }
            }
            return result;
        }

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getImageExt() { return imageExt; }
        public void setImageExt(String imageExt) { this.imageExt = imageExt; }
        public String getVideoExt() { return videoExt; }
        public void setVideoExt(String videoExt) { this.videoExt = videoExt; }
        public String getModelExt() { return modelExt; }
        public void setModelExt(String modelExt) { this.modelExt = modelExt; }
    }

    /** 从 ERP 跳过来的令牌配置 */
    public static class Jump
    {
        /**
         * 验签密钥的<b>兜底</b>值。
         *
         * <p>正常情况下不配在这里，配在库里（{@code u_s_siteinfo.consolesecret}）——
         * 它一站一份、必须和 ERP 登记的那份一致，属于站点档案的一部分。
         * 这个配置项只在库里那一列为空时才被用到，留空即可（那时 {@code /sso} 一律拒绝）。</p>
         */
        private String secret = "";

        /**
         * 令牌有效期上限（秒）。
         *
         * <p>ERP 签发时写的 {@code exp} 是 60 秒。这里再夹一道：
         * ERP 那边配错成 86400 的话，这一侧仍然只认 120 秒内签发的令牌。
         * <b>不要把它调大来「解决」时钟不同步</b>——那是在放大重放窗口，
         * 该做的是给两台机器装 NTP。</p>
         */
        private int maxAgeSeconds = 120;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public int getMaxAgeSeconds() { return maxAgeSeconds; }
        public void setMaxAgeSeconds(int maxAgeSeconds) { this.maxAgeSeconds = maxAgeSeconds; }
    }

    /**
     * 询盘接口的限流。
     *
     * <h3>为什么必须有</h3>
     * <p>{@code POST /openapi/site/inquiry} 是<b>整个服务唯一对公网开放的写接口</b>，
     * 匿名、无会话。在此之前它只有一个蜜罐字段挡着——蜜罐挡的是「把每个 input 都填一遍」
     * 的通用脚本，<b>不挡任何一个专门冲着这个站来的人</b>：一个 while 循环就能把
     * {@code u_s_inquiry} 灌满，顺带把通知邮箱轰爆（{@code InquiryNotifier} 是单线程串行发，
     * 队列会一直堆），而运营在真询盘里再也捞不出东西。</p>
     *
     * <p>ERP 那版接口上有一个 {@code @RateLimiter(count = 5, limitType = IP)}，
     * 迁到这个工程时<b>没有跟过来</b>（RuoYi 的那套注解在 ruoyi-framework 里，这个工程
     * 不依赖 ERP 的任何一个包）。nginx 和前台代理里那几段「必须转发真实 IP，否则每分钟
     * 5 条/IP 会退化成全站每分钟 5 条」的注释指的就是那个已经不存在的限流。
     * 这里把它补回来，实现见 {@code InquiryRateLimiter}。</p>
     */
    public static class Inquiry
    {
        /**
         * 每个 IP 每分钟最多几条。
         *
         * <p>5 条是照 ERP 那版的值。真人填一张询价单要几十秒，5 条/分钟对正常访客
         * 绰绰有余；而共用一个出口 IP 的办公室（NAT）超了也只是被要求过一会儿再试，
         * 页面上会同时给出邮箱作为退路。</p>
         */
        private int ratePerMinute = 5;

        /**
         * 每个 IP 每天最多几条。
         *
         * <p>光有分钟窗口挡不住<b>慢速灌水</b>：5 条/分钟不间断就是 7200 条/天。
         * 这一层专门治那个。50 条对一个真实买家来说已经高得离谱了。</p>
         */
        private int ratePerDay = 50;

        /** 设成 0 或负数就是关掉对应那一层的限流。两层都关 = 完全不限流，不要在生产上这么做 */
        public int getRatePerMinute() { return ratePerMinute; }
        public void setRatePerMinute(int ratePerMinute) { this.ratePerMinute = ratePerMinute; }
        public int getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(int ratePerDay) { this.ratePerDay = ratePerDay; }
    }

    /**
     * 跨域。
     *
     * <h3>默认一个来源都不放</h3>
     * <p>生产上前台和这个后端在<b>同一个域名</b>下（nginx 里一条
     * {@code location /openapi/site/} 转到本机 8081），浏览器发的是同源请求，
     * 根本不需要 CORS。这是选「同源反代」而不是「另开 api 子域」的好处之一。</p>
     *
     * <p>但前台的 {@code NUXT_PUBLIC_API_BASE} 是<b>可以</b>填一个跨根域地址的
     * （nuxt.config.ts 里明确写着这个选项）。填了之后浏览器侧的询盘提交就是跨域请求，
     * 而这个服务原来<b>没有任何 CORS 配置</b>——ERP 那版给 {@code /openapi/site/**}
     * 注册的「任意来源」CORS 也在瘦身时删了。结果是那个选项事实上不可用，
     * 而失败发生在浏览器里，后端日志上什么都看不到。</p>
     *
     * <p>所以给一个白名单开关：要跨域就把前台域名列进来，不填就是不放（安全的默认值）。
     * <b>不要用 {@code *}</b>——虽然这组接口是公开只读的，但 {@code POST /inquiry}
     * 通配来源等于允许任何网页替访客发询盘。</p>
     */
    public static class Cors
    {
        /**
         * 允许跨域访问 {@code /openapi/site/**} 的来源，逗号分隔，
         * 要带协议和端口：{@code https://kangchen.com,https://www.kangchen.com}。
         *
         * <p>留空 = 不注册任何 CORS 映射（默认）。</p>
         */
        private String allowedOrigins = "";

        public List<String> allowedOriginList()
        {
            List<String> result = new ArrayList<>();
            if (allowedOrigins == null || allowedOrigins.trim().isEmpty())
            {
                return result;
            }
            for (String item : allowedOrigins.split(","))
            {
                String origin = item == null ? null : item.trim();
                if (origin != null && !origin.isEmpty() && !result.contains(origin))
                {
                    result.add(origin);
                }
            }
            return result;
        }

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    /**
     * 前台买家登录（{@code station.buyer.*}）。
     *
     * <h3>⚠ 这一组是**买家**的，和管理台入场没有任何关系</h3>
     * <p>管理台没有登录页，唯一入口是从 ERP 免密进来（{@code SessionController}）。
     * 这里配的是<b>前台客户</b>自助注册、验证邮箱、密码登录那一套
     * （{@code u_s_buyer}，见 domain {@code SiteBuyer} 的类注释）。
     * 两拨人、两张表、两套会话，改这一组不会影响管理台。</p>
     */
    public static class Buyer
    {
        /**
         * 会话 Cookie 名。
         *
         * <p><b>刻意和管理台的 {@code CONSOLE_SESSION} 不同名。</b>两者本来也跑在不同的
         * 主机名上（{@code kangchen.com} 和 {@code console.kangchen.com}），Cookie 本身
         * 就串不过去；不同名是为了让「浏览器里那个 Cookie 是谁的」在排查时一眼可见，
         * 也让运维在 Redis 里能分清两批键。</p>
         */
        private String cookieName = "BUYER_SESSION";

        /**
         * Cookie 的 Secure 标记。
         *
         * <p><b>生产必须 true。</b>本地 http 调试时用环境变量关掉——
         * 别为了本地方便把默认值改成 false，那种改动最容易跟着上线。</p>
         *
         * <p>⚠ 本地不关的表现很迷惑：登录接口返回 200、响应里有 Set-Cookie，
         * 但浏览器<b>静默丢掉</b>它（http 页面不接受 Secure Cookie），
         * 于是「登录成功了但立刻又是未登录」。</p>
         */
        private boolean cookieSecure = true;

        /**
         * 会话多少天不活动就失效。滑动过期：每次带着有效 Cookie 来都会续期。
         *
         * <p>30 天是照买家的使用节奏定的：外贸买家可能一个月才回来看一次订单进度，
         * 按管理台那 8 小时来，他每次都要重新登录，那就等于没有账号。
         * 会话在 Redis 上，要强制踢人就删对应的键。</p>
         */
        private int sessionDays = 30;

        /** 密码最少几位。只管长度，不强制「大小写数字符号各一个」——那类规则的实际效果是让人写成 Passw0rd! 然后到处复用 */
        private int passwordMinLength = 8;

        /** 邮箱验证链接多少小时内有效 */
        private int verifyHours = 24;

        /** 重置密码链接多少小时内有效。⚠ 比验证链接短得多：它能直接改密码 */
        private int resetHours = 1;

        /**
         * 同一个 IP 每分钟最多试几次登录。
         *
         * <p>挡的是撞库。10 次对真人绰绰有余（记错密码试三四次是常事），
         * 而撞库脚本一分钟能试几千次。</p>
         */
        private int loginRatePerMinute = 10;

        /**
         * 同一个邮箱每天最多发几封信（验证 + 重置合并计数）。
         *
         * <p>不限的话，任何人都能拿别人的邮箱反复点「忘记密码」，把那个人的收件箱刷满——
         * 而且我们的发件账号会先被 SMTP 服务商限流，那时候连正常的验证信也发不出去。</p>
         */
        private int mailPerDay = 5;

        public String getCookieName() { return cookieName; }
        public void setCookieName(String cookieName) { this.cookieName = cookieName; }
        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
        public int getSessionDays() { return sessionDays; }
        public void setSessionDays(int sessionDays) { this.sessionDays = sessionDays; }
        public int getPasswordMinLength() { return passwordMinLength; }
        public void setPasswordMinLength(int passwordMinLength) { this.passwordMinLength = passwordMinLength; }
        public int getVerifyHours() { return verifyHours; }
        public void setVerifyHours(int verifyHours) { this.verifyHours = verifyHours; }
        public int getResetHours() { return resetHours; }
        public void setResetHours(int resetHours) { this.resetHours = resetHours; }
        public int getLoginRatePerMinute() { return loginRatePerMinute; }
        public void setLoginRatePerMinute(int loginRatePerMinute) { this.loginRatePerMinute = loginRatePerMinute; }
        public int getMailPerDay() { return mailPerDay; }
        public void setMailPerDay(int mailPerDay) { this.mailPerDay = mailPerDay; }
    }

    /**
     * 在线咨询（买家 ↔ 运营的对话）。
     *
     * <p>设计文档：{@code docs/设计稿/2026-09-04-在线咨询.md}</p>
     */
    public static class Chat
    {
        /**
         * 访客令牌的 Cookie 名。
         *
         * <p>⚠ 和 {@code BUYER_SESSION}、{@code CONSOLE_SESSION} 是<b>三个不同的东西</b>，
         * 名字别改成相近的。这个认的不是身份，是「哪条对话线程」。</p>
         */
        private String cookieName = "CHAT_VISITOR";

        /**
         * ⚠ 默认 true，本地开发在 {@code application-local.yml} 里关。
         *
         * <p>和买家会话那个 {@code cookieSecure} 是同一件事的同一个坑：本地走 http，
         * Secure 的 Cookie 浏览器根本不存，表现是「每刷新一次就是一个新访客」。
         * <b>不要为了本地方便改这里的默认值</b>——那种改动最容易跟着上线。</p>
         */
        private boolean cookieSecure = true;

        /**
         * 令牌在浏览器里留多久（天）。
         *
         * <p>30 天，和买家会话一致。外贸买家隔几周回来接着问是常事，
         * 短了就变成「上次聊的记录没了」。</p>
         */
        private int cookieDays = 30;

        /**
         * 每个 IP 每分钟最多发几条。
         *
         * <p>⚠ 比询盘那个 5 条/分钟<b>放宽很多</b>，这是有意的：聊天本来就是一句句发，
         * 套询盘的值会把正常对话卡死——一个着急的买家连发三句「在吗」「多少钱」「能发货吗」
         * 就被挡了。20 条对真人绰绰有余，对脚本仍然是个门槛。</p>
         */
        private int ratePerMinute = 20;

        /**
         * 每个 IP 每天最多发几条。
         *
         * <p>光有分钟窗口挡不住慢速灌水：20 条/分钟不间断是 28800 条/天。这一层治那个。</p>
         */
        private int ratePerDay = 500;

        /** 一条消息最多多少字符，超了截断。2000 和库里那一列的宽度一致 */
        private int maxLength = 2000;

        /** 打开窗口时回多少条历史。聊了半年的线程不该在点开那一刻把全部推给浏览器 */
        private int historySize = 50;

        public String getCookieName() { return cookieName; }
        public void setCookieName(String cookieName) { this.cookieName = cookieName; }
        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
        public int getCookieDays() { return cookieDays; }
        public void setCookieDays(int cookieDays) { this.cookieDays = cookieDays; }
        public int getRatePerMinute() { return ratePerMinute; }
        public void setRatePerMinute(int ratePerMinute) { this.ratePerMinute = ratePerMinute; }
        public int getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(int ratePerDay) { this.ratePerDay = ratePerDay; }
        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
        public int getHistorySize() { return historySize; }
        public void setHistorySize(int historySize) { this.historySize = historySize; }
    }

    /**
     * 前台浏览量统计。
     *
     * <p>表结构和全部设计决定在 {@code sql/station/migration/2026-09-08-pageview.sql}
     * 的文件头里，改这里的默认值之前先读它。</p>
     */
    public static class PageView
    {
        /**
         * 整套统计的总开关。
         *
         * <p>关掉之后 {@code POST /openapi/site/pv} <b>照样返回 204</b>，只是不落库。
         * 这是有意的：前台那个埋点脚本不该因为后端关了统计就在控制台里刷错误，
         * 而且开关一开一关不需要重新打包前端。</p>
         */
        private boolean enabled = true;

        /**
         * 访客标识的 Cookie 名。
         *
         * <p>⚠ 和 {@code BUYER_SESSION}、{@code CHAT_VISITOR}、{@code CONSOLE_SESSION}
         * 是<b>第四个不同的东西</b>。这个既不认身份也不认对话线程，它只回答
         * 「这两次浏览是不是同一个浏览器」，值本身没有任何权限。</p>
         */
        private String visitorCookieName = "SITE_VISITOR";

        /** 会话标识的 Cookie 名。浏览器关掉就没了（maxAge 见 {@link #sessionMinutes}） */
        private String sessionCookieName = "SITE_SESSION";

        /**
         * ⚠ 默认 true，本地开发在 {@code application-local.yml} 里关。
         *
         * <p>和 {@code CHAT_VISITOR} 那个 {@code cookieSecure} 是同一个坑：本地走 http，
         * Secure 的 Cookie 浏览器根本不存，表现是<b>每看一个页面都算一个新访客</b>——
         * PV 正常、UV 等于 PV，而这个现象很容易被当成「统计写得不对」。</p>
         */
        private boolean cookieSecure = true;

        /**
         * 访客标识在浏览器里留多久（天）。
         *
         * <p>180 天，比聊天令牌的 30 天长：那个是「上次聊到哪」，过期了重新开一条就行；
         * 这个是 UV 的分母，过期就意味着<b>老访客被重新算成新访客</b>，
         * 回访率那个数字会虚高。</p>
         *
         * <p>⚠ 但也不要设成好几年。它是 GDPR 意义上的个人数据标识符，
         * 保留期越长越需要一个说得过去的理由。180 天覆盖「一个采购季回来复看」，
         * 够了。</p>
         */
        private int visitorCookieDays = 180;

        /** 多少分钟不活动算一次新会话。30 分钟是这个行业的通行值，改了就没法和别人的数字比 */
        private int sessionMinutes = 30;

        /**
         * 明细保留多少天，超期由 {@code PageViewCleaner} 删掉。
         *
         * <p>90 天。往长了设没有意义：明细的用途是「最近发生了什么」，
         * 而超过 90 天的历史看的是趋势，那部分在 {@code u_s_pv_daily} 里，永不删除。</p>
         *
         * <p>配成 0 或负数 = 不清理。⚠ 别在生产上这么配——这张表是全库写入最频繁的，
         * 而这台机器<b>没有数据库备份</b>，让它无限长下去等于慢慢把磁盘吃掉。</p>
         */
        private int retainDays = 90;

        /**
         * 每个 IP 每分钟最多上报几次。
         *
         * <p>⚠ 比聊天那 20 条<b>还要放宽</b>，因为这是被动上报不是主动操作：
         * 一个认真看货的买家一分钟点开七八个商品很正常，再加上返回列表、
         * 切分类，一分钟三四十次一点都不夸张。60 对真人足够，对刷量脚本仍是门槛。</p>
         *
         * <p>NAT 出口共用 IP 会让一个办公室的人挤在同一个额度里——
         * 这里<b>接受这个误差</b>。丢几条浏览记录只是统计少了几个数，
         * 而询盘那边同样的情况是丢一个客户，所以那边的值定得比这里保守得多。</p>
         */
        private int ratePerMinute = 60;

        /** 每个 IP 每天最多上报几次。光有分钟窗口挡不住慢速灌水：60/分钟不间断是 86400/天 */
        private int ratePerDay = 3000;

        /**
         * 内存队列最多堆多少条待写。满了就<b>丢弃</b>新来的，不阻塞请求线程。
         *
         * <p>⚠ 这是这个功能最重要的一条取舍：<b>统计数据的价值远低于页面可用性</b>。
         * 库慢了、锁住了、连接池满了，队列会迅速堆满，此时正确的行为是丢掉浏览记录，
         * 而不是让每个访客的页面都卡在一次埋点上。丢弃时按批打 WARN（不是每条一行，
         * 那会把日志刷爆），日志里能看出丢了多少。</p>
         */
        private int queueCapacity = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getVisitorCookieName() { return visitorCookieName; }
        public void setVisitorCookieName(String visitorCookieName) { this.visitorCookieName = visitorCookieName; }
        public String getSessionCookieName() { return sessionCookieName; }
        public void setSessionCookieName(String sessionCookieName) { this.sessionCookieName = sessionCookieName; }
        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
        public int getVisitorCookieDays() { return visitorCookieDays; }
        public void setVisitorCookieDays(int visitorCookieDays) { this.visitorCookieDays = visitorCookieDays; }
        public int getSessionMinutes() { return sessionMinutes; }
        public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }
        public int getRetainDays() { return retainDays; }
        public void setRetainDays(int retainDays) { this.retainDays = retainDays; }
        public int getRatePerMinute() { return ratePerMinute; }
        public void setRatePerMinute(int ratePerMinute) { this.ratePerMinute = ratePerMinute; }
        public int getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(int ratePerDay) { this.ratePerDay = ratePerDay; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    /**
     * 匿名查单（{@code POST /openapi/site/order/lookup}）的限流。
     *
     * <h3>⚠ 这一组和 {@link Inquiry} 那一组的取舍是<b>相反</b>的</h3>
     * <p>询盘限流保的是「别让人灌爆收件箱」，所以 Redis 挂了要<b>放行</b>——
     * 丢一条真询盘等于丢一个客户。这个接口保的是「别让人读到别人的订单」：
     * 单号是 {@code DD} + 流水，可枚举，唯一挡着的就是必须同时报对下单邮箱。
     * 所以它的限流 Redis 挂了要<b>拒绝</b>，实现见 {@code OrderLookupRateLimiter}。</p>
     *
     * <p>额度也比询盘小一个量级：一个真买家来查单，一天点不了几次。</p>
     */
    public static class OrderLookup
    {
        /**
         * 每个 IP 每小时最多查几次。
         *
         * <p>真买家的用法是「收到确认信 → 点链接 → 填一次邮箱」，10 次/小时
         * 连手滑重填都够了。挡的是拿一个已知邮箱去撞单号的脚本。</p>
         */
        private int ipRatePerHour = 10;

        /** 每个 IP 每天最多几次。光有小时窗口挡不住慢速撞号：10/小时 = 240/天 */
        private int ipRatePerDay = 30;

        /**
         * 每个<b>邮箱</b>每天最多被查几次。
         *
         * <p>只按 IP 限的话，换一批 IP 就能接着撞同一个受害者的单号。
         * 这一层按被查的那个邮箱算，跟 IP 换不换没关系。</p>
         */
        private int emailRatePerDay = 20;

        /** 三项都是设成 0 或负数 = 关掉那一层。⚠ 全关等于把订单详情开给任何人撞单号 */
        public int getIpRatePerHour() { return ipRatePerHour; }
        public void setIpRatePerHour(int ipRatePerHour) { this.ipRatePerHour = ipRatePerHour; }
        public int getIpRatePerDay() { return ipRatePerDay; }
        public void setIpRatePerDay(int ipRatePerDay) { this.ipRatePerDay = ipRatePerDay; }
        public int getEmailRatePerDay() { return emailRatePerDay; }
        public void setEmailRatePerDay(int emailRatePerDay) { this.emailRatePerDay = emailRatePerDay; }
    }

    /**
     * 买家 3D 定制方案（{@code POST /openapi/site/customize/plan}）的限流，2026-09-29。
     *
     * <h3>⚠ Redis 挂了要<b>拒绝</b>，和 {@link Inquiry} 相反、和 {@link OrderLookup} 一样</h3>
     * <p>这是唯一一个能让匿名请求往对象存储写文件的口子（每次最多 6 张图、每张 2MB）。
     * 拿不到计数就放行，等于不限量写 COS。代价是 Redis 故障期间存不了方案，
     * 页面提示稍后再试，买家编辑的内容还在页面上。实现见 {@code CustomPlanRateLimiter}。</p>
     */
    public static class CustomPlan
    {
        /** 每个 IP 每小时最多存几次。真买家改几版就够了 */
        private int ipRatePerHour = 10;

        /** 每个 IP 每天最多存几次 */
        private int ipRatePerDay = 30;

        /** <b>整个站</b>每天最多存几次：换 IP 刷也有个封顶，存储费用心里有数 */
        private int siteRatePerDay = 500;

        /** 三项都是设成 0 或负数 = 关掉那一层 */
        public int getIpRatePerHour() { return ipRatePerHour; }
        public void setIpRatePerHour(int ipRatePerHour) { this.ipRatePerHour = ipRatePerHour; }
        public int getIpRatePerDay() { return ipRatePerDay; }
        public void setIpRatePerDay(int ipRatePerDay) { this.ipRatePerDay = ipRatePerDay; }
        public int getSiteRatePerDay() { return siteRatePerDay; }
        public void setSiteRatePerDay(int siteRatePerDay) { this.siteRatePerDay = siteRatePerDay; }
    }

    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }
    public Jump getJump() { return jump; }
    public void setJump(Jump jump) { this.jump = jump; }
    public Inquiry getInquiry() { return inquiry; }
    public void setInquiry(Inquiry inquiry) { this.inquiry = inquiry; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Buyer getBuyer() { return buyer; }
    public void setBuyer(Buyer buyer) { this.buyer = buyer; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public PageView getPageview() { return pageview; }
    public void setPageview(PageView pageview) { this.pageview = pageview; }
    public OrderLookup getOrderLookup() { return orderLookup; }
    public void setOrderLookup(OrderLookup orderLookup) { this.orderLookup = orderLookup; }
    public CustomPlan getCustomPlan() { return customPlan; }
    public void setCustomPlan(CustomPlan customPlan) { this.customPlan = customPlan; }
    public String getConfigSecret() { return configSecret; }
    public void setConfigSecret(String configSecret) { this.configSecret = configSecret; }
    public String getCookieDomain() { return cookieDomain; }
    public void setCookieDomain(String cookieDomain) { this.cookieDomain = cookieDomain; }
}
