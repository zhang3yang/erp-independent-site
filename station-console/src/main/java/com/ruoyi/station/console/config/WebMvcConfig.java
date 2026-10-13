package com.ruoyi.station.console.config;

import java.nio.file.Paths;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.station.console.web.AuthInterceptor;
import com.ruoyi.station.console.web.SiteLangInterceptor;
import com.ruoyi.station.console.service.SiteInfoService;

/**
 * 拦截器和静态资源。
 *
 * <h3>拦截器只拦 {@code /api/**}</h3>
 * <p>放行的是：</p>
 * <ul>
 *   <li>{@code /openapi/site/**} —— 前台开放接口，<b>本来就是给匿名访客看的</b>；</li>
 *   <li>{@code /sso} —— 唯一的入口，这时候当然还没有会话；</li>
 *   <li>{@code /files/**} —— 上传的图片和视频，见下；</li>
 *   <li>前端静态资源和 SPA 的路由——它们不含任何数据，光有壳子看不到东西。
 *       真正的数据全在 {@code /api/**} 后面，那才是需要守的门。</li>
 * </ul>
 *
 * <h3>没有能登录的 {@code /login}</h3>
 * <p>这不是漏了：<b>管理台不做本地登录</b>，加一个登录表单就等于在 SSO 这道闸旁边
 * 开了一扇后门，前面那一整套就白做了（docs/独立站与ERP的边界.md §6d）。</p>
 *
 * <p>⚠ 这里的历史值得知道，免得有人第三次改回来：
 * boundary §6a 曾经加过账号密码登录，理由是「ERP 挂了或者网断了，整个站的后台就进不去了，
 * 而独立站本来该是能独立跑的」。<b>那一版撤掉了</b>——那个代价是明知故犯的，
 * 换来的是本站没有第二套可以被撞库、被弱口令攻破的凭据。完整说明见
 * {@code SessionController} 的类注释。</p>
 *
 * <p>{@code /login} 这个前端路由还在，但它是一个<b>空页面</b>：没有表单、没有站点信息，
 * 只在从 ERP 跳转被拒时显示一句原因。它由下面的 SPA 静态资源发出去，
 * 后端不需要为它留任何接口——{@code /auth/**} 那一组（{@code state} / {@code login} /
 * {@code password}）整个删了，公开路径少一组就少一组要防的东西。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    @Autowired
    private StationProperties properties;
    @Autowired
    private SiteInfoService siteInfoService;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/api/**");
        // 前台当前语言进 SiteLang，报错按它翻（BuyerText）。只挂前台那一片
        registry.addInterceptor(new SiteLangInterceptor(siteInfoService)).addPathPatterns("/openapi/site/**");
    }

    /**
     * 跨域。<b>默认一条都不注册</b>，只有配了 {@code station.cors.allowed-origins} 才开。
     *
     * <h3>为什么默认不开</h3>
     * <p>生产上前台和这个后端在同一个域名下——nginx 里一条
     * {@code location /openapi/site/} 转到本机 8081，浏览器发的是<b>同源</b>请求。
     * 不需要 CORS，也就不会因为白名单漏配而白屏，还省掉一次跨域预检
     * （海外访客的往返延迟本来就高）。这是选「同源反代」而不是「另开 api 子域」的好处。</p>
     *
     * <h3>那为什么还要有这一段</h3>
     * <p>前台的 {@code NUXT_PUBLIC_API_BASE} 是可以填一个跨根域地址的，
     * nuxt.config.ts 里把它当成一个受支持的选项写着（「跨根域，走 CORS，不带 Cookie」）。
     * 但这个服务原来<b>一行 CORS 配置都没有</b>，ERP 那版给 {@code /openapi/site/**}
     * 注册的「任意来源」CORS 也在瘦身时删了——于是那个选项事实上不可用，
     * 而且失败发生在浏览器里，<b>后端日志上什么都看不到</b>，
     * 表现是「填了这个变量之后询盘就发不出去了，但服务器上一切正常」。</p>
     *
     * <h3>三条边界</h3>
     * <ol>
     *   <li><b>只给 {@code /openapi/site/**}。</b>{@code /api/**} 是管理台的接口、
     *       靠 Cookie 会话，给它开跨域等于把 CSRF 的门打开；</li>
     *   <li><b>不允许 {@code *}。</b>这组接口的读部分确实是公开的，但
     *       {@code POST /inquiry} 是写操作——通配来源等于允许任何一个网页
     *       替访客发询盘。要填就填确切的域名；</li>
     *   <li><b>不带凭证</b>（不调 {@code allowCredentials}）。前台取这组数据不需要
     *       任何身份，带上 Cookie 只会让上面那条更危险。</li>
     * </ol>
     *
     * <h3>⚠ 第三条和「买家登录」的关系：跨根域时买家登录用不了</h3>
     * <p>{@code /openapi/site/account/**}（买家注册/登录/我的订单）靠的是
     * {@code BUYER_SESSION} 这个 Cookie。而这里 {@code allowCredentials(false)} 意味着
     * <b>跨域请求不带 Cookie</b>——所以把前台的 {@code NUXT_PUBLIC_API_BASE} 填成
     * 一个跨根域地址时，买家会「登录返回 200，然后立刻又是未登录」。</p>
     *
     * <p><b>不要为此改成 {@code allowCredentials(true)}</b>：那会让白名单里的任何来源
     * 都能带着买家的会话发请求，等于把 CSRF 的门打开（买家会话现在靠
     * {@code SameSite=Lax} 挡 CSRF，见 {@code BuyerSession} 类注释）。
     * 要跨根域就得先上 CSRF token，那是另一件事。</p>
     *
     * <p>正确的做法还是<b>同源</b>：{@code NUXT_PUBLIC_API_BASE} 留空，
     * 让 nginx 上那条 {@code location /openapi/site/} 把请求转到本机 8081。
     * 这也是 .env.example 里推荐的配法。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry)
    {
        List<String> origins = properties.getCors().allowedOriginList();
        if (origins.isEmpty())
        {
            return;
        }
        registry.addMapping("/openapi/site/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", SiteLangInterceptor.HEADER)
                .allowCredentials(false)
                // 预检结果缓存半小时，省掉大部分 OPTIONS 往返
                .maxAge(1800);
    }

    /**
     * 把上传目录挂到 {@code /files/**} 上。
     *
     * <h3>为什么要有这一段</h3>
     * <p>文件落在本机磁盘上（{@code station.upload.dir}），生产环境由 nginx 上
     * {@code file.<主域名>} 那个 server 块直接发（docs/独立站与ERP的边界.md §10.3.8）。</p>
     *
     * <p>但那样一来，<b>没有 nginx 就没有任何东西能把文件发出去</b>——本地开发时
     * 图片传上去了、库里有记录、界面上全是裂图。这一段就是补那个洞。</p>
     *
     * <h3>生产环境不会走到这里</h3>
     * <p>nginx 在前面按路径拦截，静态文件根本到不了 Java。留着它不冲突，
     * 而且多一层兜底：nginx 那个 server 块配错时，至少还能从
     * {@code data.<主域名>/files/...} 访问到，便于排查。</p>
     *
     * <h3>⚠ 路径穿越</h3>
     * <p>{@code ResourceHttpRequestHandler} 自己会拦 {@code ..} 和编码过的斜杠
     * （{@code isInvalidPath} / {@code isResourceUnderLocation}），这里不用再写一遍。
     * 但<b>别把 location 改成一个上层目录</b>——那样能读到的就不止上传文件了。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        String dir = properties.getUpload().getDir();
        if (dir == null || dir.trim().isEmpty())
        {
            return;
        }
        // 必须是绝对路径 + 结尾斜杠，file: 协议才认。Windows 上 toUri() 会给出
        // file:///C:/... 这种形式，直接拼字符串很容易少一个斜杠
        String location = Paths.get(dir.trim()).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/"))
        {
            location = location + "/";
        }
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
