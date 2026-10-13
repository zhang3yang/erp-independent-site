package com.ruoyi.framework.config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.framework.interceptor.RepeatSubmitInterceptor;

/**
 * 通用配置
 * 
 * @author ruoyi
 */
@Configuration
public class ResourcesConfig implements WebMvcConfigurer
{
    private static final Logger log = LoggerFactory.getLogger(ResourcesConfig.class);

    @Autowired
    private RepeatSubmitInterceptor repeatSubmitInterceptor;

    /**
     * ERP 自己的接口允许哪些来源跨域调。逗号分隔，支持 {@code *} 通配（如
     * {@code https://*.vclinker.com}）。
     *
     * <p>默认值带上了本地开发的几个端口，否则改完这里所有人的本地联调都会撞 CORS。</p>
     */
    @Value("${ruoyi.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        /** 本地文件上传路径 */
        registry.addResourceHandler(Constants.RESOURCE_PREFIX + "/**")
                .addResourceLocations("file:" + RuoYiConfig.getProfile() + "/");

        /** swagger配置 */
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                .setCacheControl(CacheControl.maxAge(5, TimeUnit.HOURS).cachePublic());
    }

    /**
     * 自定义拦截规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/**");
    }

    /**
     * 本地开发用的来源。生产上配了 ruoyi.cors.allowed-origins 也不会去掉它们——
     * 它们指向开发机自己的 localhost，对生产没有任何意义，留着不构成风险。
     *
     * <p>⚠ <b>带端口和不带端口必须各写一条。</b>Spring 把 origin pattern 编译成正则时，
     * {@code *} 变成 {@code .*}，但前面那个冒号是<b>字面量</b>——所以
     * {@code http://localhost:*} 匹配不上 {@code http://localhost}（80 端口的 origin
     * 里根本没有冒号）。只写带 {@code :*} 的那条，用 nginx 在 80 端口跑前端的人就会撞 CORS，
     * 而且报的是「No 'Access-Control-Allow-Origin' header」，看不出是端口的问题。</p>
     */
    private static final List<String> DEV_ORIGINS = Arrays.asList(
            "http://localhost", "http://localhost:*",
            "http://127.0.0.1", "http://127.0.0.1:*",
            "https://localhost", "https://localhost:*");

    /**
     * 跨域配置。<b>只有一套：ERP 自己的接口，白名单。</b>
     *
     * <h3>⚠ 这里原来有两套，第二套删了</h3>
     *
     * <p>原来还给 {@code /openapi/site/**} 单独注册了一份「任意来源」的 CORS——
     * 那是独立站前台的匿名取数通道，前台跑在谁的域名下事先不可能知道，
     * 塞不进静态白名单。</p>
     *
     * <p>那组接口现在不在 ERP 上了。前台取数搬到站点自己的后端
     * （{@code station-console} 的 {@code OpenApiController}，对外
     * {@code data.<主域名>}），依据 docs/独立站与ERP的边界.md §10.1：
     * <b>ERP 和独立站的后端没有任何互通</b>。ERP 这边既然一个匿名跨域接口都没有了，
     * 留着那条注册就是给一个不存在的路径放行——它不造成安全问题，但会让下一个人
     * 以为 ERP 上还有公开接口。</p>
     *
     * <p>{@code /openapi/console/**}（旧的管理台 S2S）也一起删了，见 §4.5。</p>
     *
     * <h3>没开 allowCredentials</h3>
     *
     * <p>不开。ERP 用的是 Bearer token（走 Authorization 头，不是 Cookie），
     * 开了它反而会让 {@code *} 通配失效并抛异常。</p>
     */
    @Bean
    public CorsFilter corsFilter()
    {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", erpCors());
        return new CorsFilter(source);
    }

    /** ERP 自己的接口：白名单 */
    private CorsConfiguration erpCors()
    {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new java.util.ArrayList<>(DEV_ORIGINS);
        for (String item : allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
        {
            String origin = item == null ? null : item.trim();
            if (origin != null && !origin.isEmpty() && !origins.contains(origin))
            {
                origins.add(origin);
            }
        }
        origins.forEach(config::addAllowedOriginPattern);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(1800L);

        if (allowedOrigins == null || allowedOrigins.trim().isEmpty())
        {
            log.warn("CORS：没有配置 ruoyi.cors.allowed-origins，ERP 接口目前只允许本地来源"
                    + "（{}）跨域。生产环境请把 ERP 前端的域名配上，否则前端会撞 CORS", DEV_ORIGINS);
        }
        else
        {
            log.info("CORS：ERP 接口允许的来源 {}", origins);
        }
        return config;
    }
}