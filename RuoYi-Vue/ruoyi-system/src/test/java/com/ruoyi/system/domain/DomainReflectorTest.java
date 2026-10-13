package com.ruoyi.system.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.util.regex.Pattern;

/**
 * 所有 domain 类都要能被 MyBatis 反射解析。
 *
 * <p>MyBatis 在第一次把某个类当作参数或结果类型时构建 {@code Reflector}，构建失败会抛
 * {@code ReflectionException}，而那一刻已经是运行期执行 SQL 了——编译、启动全都是绿的，
 * 只有真正插一条数据才炸。最容易踩的是<b>同一个属性写了两个返回类型不同的 getter</b>：</p>
 *
 * <pre>
 *   public Boolean getCustomMade()  // 包装类型，允许为 null
 *   public boolean isCustomMade()   // 便捷重载，顺手加的
 * </pre>
 *
 * <p>两个都是 JavaBeans 规范认可的 {@code customMade} getter，返回类型却不一致，Reflector
 * 判定歧义直接拒绝，那张表从此读写全废。这条测试把全部 domain 类扫一遍，把这类问题
 * 挡在提交之前。</p>
 */
class DomainReflectorTest
{
    private static final String DOMAIN_PACKAGE = "com.ruoyi.system.domain";

    @Test
    void everyDomainClassIsReflectableByMyBatis()
    {
        List<Class<?>> classes = scanDomainClasses();
        // 扫不到就说明包名改了或者扫描配置失效，静默通过等于这条测试白写
        assertTrue(classes.size() > 20, "domain 类扫描结果异常，只找到 " + classes.size() + " 个");

        ReflectorFactory factory = new DefaultReflectorFactory();
        List<String> failures = new ArrayList<>();
        for (Class<?> type : classes)
        {
            try
            {
                factory.findForClass(type);
            }
            catch (Exception e)
            {
                failures.add(type.getName() + " -> " + e.getMessage());
            }
        }
        if (!failures.isEmpty())
        {
            fail("以下 domain 类 MyBatis 反射不了，读写这些表的 SQL 会在运行期抛异常：\n  "
                    + String.join("\n  ", failures));
        }
    }

    private List<Class<?>> scanDomainClasses()
    {
        // 只按类名过滤，不看注解：domain 是纯 POJO，没有 @Component 之类的标记
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        List<Class<?>> classes = new ArrayList<>();
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(DOMAIN_PACKAGE);
        for (BeanDefinition definition : candidates)
        {
            String name = definition.getBeanClassName();
            if (name == null) continue;
            try
            {
                classes.add(Class.forName(name));
            }
            catch (Throwable ignored)
            {
                // 加载不了的类（缺可选依赖之类）不是本测试要管的事
            }
        }
        return classes;
    }
}
