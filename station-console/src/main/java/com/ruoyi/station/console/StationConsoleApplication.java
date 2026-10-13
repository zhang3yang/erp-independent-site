package com.ruoyi.station.console;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 独立站后端。<b>一台机器 = 一个站</b>，这个服务装着这个站的全部。
 *
 * <h3>它做什么</h3>
 * <ol>
 *   <li><b>前台取数</b>——{@code /openapi/site/**}，Nuxt 前台（{@code station-site}）用，对公网开放；</li>
 *   <li><b>管理台取数</b>——{@code /api/**}，{@code station-console-ui} 用，要会话 + 权限；</li>
 *   <li><b>一个入口</b>——{@code POST /sso}，从 ERP 的「独立站管理」点「进入管理」免密进来。
 *       <b>没有登录页</b>，{@code /login} 是一个空页面。</li>
 * </ol>
 *
 * <h3>⚠ 这个类的注释以前写的是反的，值得读一遍</h3>
 * <p>它原来是这么写的：</p>
 * <blockquote>
 * <b>它没有登录页，也没有用户表。</b>身份的唯一来源是 ERP 发的一次性票。<br>
 * <b>它也没有数据库。</b>商品、询盘、站点档案都在 ERP 的库里，这里一条 SQL 都不写。
 * </blockquote>
 *
 * <p>那两句话现在都不成立了。依据是 docs/独立站与ERP的边界.md 要点 1：</p>
 * <blockquote>
 * ERP 和独立站完全分开部署，独立站与 ERP 的后端没有任何互通。
 * </blockquote>
 *
 * <p>于是：</p>
 * <ul>
 *   <li><b>有自己的库</b>（本机 {@code sdsstation}）——原来那套「读写全走 S2S」
 *       要求管理台能连到 ERP，那就是最硬的一条互通；</li>
 *   <li><b>有自己的用户表</b>（{@code u_s_user} / {@code u_s_role}，§6）——
 *       谁从 ERP 跳进来就为他落一行，角色由站长在本站调，和 ERP 的角色无关。</li>
 * </ul>
 *
 * <p>但<b>「没有登录页」那半句仍然成立</b>：§6a 一度给它补过账号密码登录，
 * 那一版撤掉了，唯一入口还是 ERP 发的令牌。理由见 {@code SessionController}。</p>
 *
 * <h3>现在的闸在哪</h3>
 * <p>不在「有没有 MyBatis」上了（它现在必须有），而在<b>「有没有指向 ERP 的 HTTP 客户端」</b>上。
 * 完整说明和验收方式见 {@code pom.xml} 顶部那段注释。想加一条「顺便去 ERP 查一下」之前，
 * 先去读它。</p>
 *
 * <h3>能独立跑到什么程度</h3>
 * <p>§13 第 1 条给了判据：<b>把 ERP 整个停掉</b>，前台照常卖货、管理台里已经登进来的人
 * 照常改商品和看询盘（会话在 Redis 里，8 小时不活跃才失效）。</p>
 *
 * <p><b>用不了的是「新进来」这一个动作</b>：入场令牌由 ERP 签发，ERP 不在就签不出来。
 * 这是取消本地登录页换来的代价，明知故犯——详见 {@code SessionController}。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
/*
 * ⚠ 这一行不能少，少了启动直接失败：
 *
 *     Field siteInfoMapper in ...SiteInfoService required a bean of type
 *     '...mapper.SiteInfoMapper' that could not be found.
 *
 * mapper 是一组**接口**，没有实现类，Spring 的组件扫描不会理它们；要靠 MyBatis 在这里
 * 按包扫一遍、给每个接口生成一个代理 bean。
 *
 * 从 ERP 移植时最容易漏掉这一条：那边这行不在启动类上，而在
 * ruoyi-framework 的 ApplicationConfig 里（{@code @MapperScan("com.ruoyi.**.mapper")}），
 * 而 ruoyi-framework 这个包这边**不依赖**（pom 顶部那道闸），于是跟着一起没了。
 *
 * 用 @MapperScan 而不是给九个接口逐个加 @Mapper：加接口的人不必记得加注解，
 * 少一个「忘了就启动不起来」的点。
 */
@MapperScan("com.ruoyi.station.console.mapper")
/*
 * 定时任务。目前只有一个用途：RateFetcher 每天拉一次汇率。
 *
 * ⚠ 少了这一行不会报错，只会「什么都不发生」——@Scheduled 被当成普通注解忽略掉，
 *   汇率永远停在建库时那个空值，前台不显示币种切换器。而这个症状和「API 拉不通」
 *   长得一模一样，会往网络那边查很久。加任何 @Scheduled 之前先确认这一行还在。
 *
 * 默认单线程调度。现在只有一个任务，够用；将来加到两个以上、且其中一个跑得久时，
 * 要配 TaskScheduler 的线程池，否则它们会互相排队。
 */
@EnableScheduling
public class StationConsoleApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(StationConsoleApplication.class, args);
    }
}
