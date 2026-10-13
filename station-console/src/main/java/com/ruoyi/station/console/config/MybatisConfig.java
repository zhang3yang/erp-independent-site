package com.ruoyi.station.console.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 的接线。
 *
 * <h3>⚠ 为什么要手写这一个类，而不是靠 starter 自动配</h3>
 *
 * <p>因为在 <b>Spring Boot 4</b> 上，{@code mybatis-spring-boot-starter} 的自动配置
 * <b>根本不会生效，而且不报错</b>。</p>
 *
 * <p>Boot 4 把 {@code spring-boot-autoconfigure} 按模块拆开了，自动配置类跟着换了包：</p>
 * <pre>
 *   Boot 3: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
 *   Boot 4: org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
 * </pre>
 *
 * <p>而 {@code MybatisAutoConfiguration}（最新版 3.0.4，2024-11 发布，为 Boot 3 编译）
 * 上面写着 {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)}，指的是<b>旧包</b>。
 * Spring Boot 遇到「注解里引用的类不存在」的自动配置，处理方式是<b>静默跳过</b>——
 * 不打日志、不报错。于是 {@code SqlSessionFactory} 这个 bean 压根没被创建，
 * 而 {@code @MapperScan} 生成的那些 mapper 代理又都需要它，报出来是这一句：</p>
 *
 * <pre>
 *   Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
 * </pre>
 *
 * <p>这句话看着像「配置漏了一项」，实际是「整套自动配置没跑」。查的时候很容易走偏，
 * 所以把结论写在这里。</p>
 *
 * <h3>为什么不是换个版本了事</h3>
 *
 * <p>查过 Maven Central：{@code mybatis-spring-boot-starter} 最新就是 <b>3.0.4</b>，
 * 没有适配 Boot 4 的版本。所以这不是「升个版本」能解决的，只能自己接。</p>
 *
 * <p>反过来降 Boot 到 3.x 也不行——这个工程选 Boot 4 是有前提的（pom 里那段
 * Jackson 3 的注释），降版本要连着改一批 import。</p>
 *
 * <h3>starter 依赖为什么还留着</h3>
 *
 * <p>它带进来的 {@code mybatis}、{@code mybatis-spring}、{@code spring-boot-starter-jdbc}
 * 三个都还要用，而且版本由它统一管。**只有它那个 autoconfigure 是废的**，
 * 上面几个类本身在 Spring 7 上工作正常（这个类用的就是它们）。</p>
 *
 * <p>将来 MyBatis 出了适配 Boot 4 的版本，这个类可以删掉——
 * 那时候即使先不删也不冲突：{@code MybatisAutoConfiguration} 上有
 * {@code @ConditionalOnMissingBean}，这里的 bean 在，它就不会再建一份。</p>
 *
 * <h3>配置项还是读 application.yml</h3>
 *
 * <p>{@code mybatis.*} 那三项没有搬进代码，仍然从 yml 读（键名一字不差）。
 * 理由是配置该待在配置文件里——把 {@code map-underscore-to-camel-case} 写进 Java,
 * 下一个人改 yml 里那一行会发现「改了没用」，那种问题比这个类本身更难查。</p>
 */
@org.springframework.context.annotation.Configuration
public class MybatisConfig
{
    /** mapper XML 的位置。多个用逗号隔开，和 yml 里 {@code mybatis.mapper-locations} 一致 */
    @Value("${mybatis.mapper-locations:}")
    private String mapperLocations;

    /** 实体所在包，让 XML 里的 resultType 能写短名 */
    @Value("${mybatis.type-aliases-package:}")
    private String typeAliasesPackage;

    /** 库里 snake_case、Java 里 camelCase */
    @Value("${mybatis.configuration.map-underscore-to-camel-case:true}")
    private boolean mapUnderscoreToCamelCase;

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception
    {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        if (StringUtils.hasText(typeAliasesPackage))
        {
            factory.setTypeAliasesPackage(typeAliasesPackage);
        }

        Resource[] resources = resolveMapperLocations();
        if (resources.length > 0)
        {
            factory.setMapperLocations(resources);
        }

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(mapUnderscoreToCamelCase);
        factory.setConfiguration(configuration);

        return factory.getObject();
    }

    /**
     * ⚠ 这个 bean 不是可选的装饰。
     *
     * <p>{@code SqlSessionTemplate} 是 mapper 代理实际用的那一层，它负责把
     * SqlSession 挂到当前事务上——没有它，{@code @Transactional} 里的多条语句会各自
     * 拿一个 session，回滚只回滚最后一条。
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory)
    {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 把逗号分隔的 classpath 通配符展开成具体资源。
     *
     * <p>用 {@code classpath*:}（带星）而不是 {@code classpath:}：前者会扫所有
     * classpath 根，打成 jar 之后 mapper XML 在 {@code BOOT-INF/classes} 下也找得到。
     * 这也是 yml 里那一行的写法，别去掉那个星。
     */
    private Resource[] resolveMapperLocations() throws IOException
    {
        if (!StringUtils.hasText(mapperLocations))
        {
            return new Resource[0];
        }
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> found = new ArrayList<>();
        for (String pattern : StringUtils.tokenizeToStringArray(mapperLocations, ","))
        {
            // 单个模式匹配不到就跳过，不抛异常：新增一个 mapper 目录时不至于
            // 因为暂时还没有 XML 就把服务启动堵住
            for (Resource resource : resolver.getResources(pattern))
            {
                found.add(resource);
            }
        }
        return found.toArray(new Resource[0]);
    }
}
