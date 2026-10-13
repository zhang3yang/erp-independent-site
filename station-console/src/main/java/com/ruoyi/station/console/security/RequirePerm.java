package com.ruoyi.station.console.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 这个接口需要哪个权限。由 {@code AuthInterceptor} 在请求进 Controller 之前判。
 *
 * <p>权限串沿用前端 {@code v-hasPermi} 用的那一套（{@code site:goods:list} 之类），
 * 和 {@code u_s_role.perms} 里存的是同一批值——三处用同一套串，
 * 改一处忘了另两处的后果是「菜单里有、按钮点了 403」，很容易发现。</p>
 *
 * <h3>它替代了什么</h3>
 * <p>ERP 那版是 {@code @ConsolePermi}，由 {@code ConsoleApiAspect} 拿请求头里的 userCode
 * <b>去 ERP 的库里现查</b>权限。这里查的是本站的 {@code u_s_role}
 * （docs/独立站与ERP的边界.md §4.3：权限归独立站自己那套）。</p>
 *
 * <h3>为什么是注解而不是路径规则</h3>
 * <p>路径规则（{@code /api/goods/** → site:goods:*}）写在一个地方，看起来更集中，
 * 但新增一个接口时很容易忘了去那张表里加一行——而<b>忘了的后果是这个接口没有权限限制</b>。
 * 注解写在方法上，漏了是显式可见的：一眼扫过去哪个方法没挂注解。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm
{
    /** 需要的权限串。留空表示只要登录了就行（比如 /api/me） */
    String value() default "";
}
