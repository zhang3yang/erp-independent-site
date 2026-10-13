package com.ruoyi.station.console.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.service.SiteInfoService;

/**
 * 把请求头 {@code X-Site-Lang}（前台当前语言）读进 {@link SiteLang}。挂在 {@code /openapi/site/**} 上。
 *
 * <p>头是空的、或者不是站点开了的语言，就按站点默认语言——老版前台不带这个头，
 * 报错时看到的是默认语言那句，不是英文。站点信息取不到（站点停用）时不设，
 * 那种情况下接口本身也会先报「站点不可用」。</p>
 */
public class SiteLangInterceptor implements HandlerInterceptor
{
    public static final String HEADER = "X-Site-Lang";

    private final SiteInfoService siteInfoService;

    public SiteLangInterceptor(SiteInfoService siteInfoService)
    {
        this.siteInfoService = siteInfoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        String want = request.getHeader(HEADER);
        try
        {
            SiteInfo site = siteInfoService.require();
            SiteLang.set(site.resolveLang(want));
        }
        catch (RuntimeException e)
        {
            SiteLang.set(want == null || want.isBlank() ? null : want.trim().toLowerCase());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex)
    {
        SiteLang.clear();
    }
}
