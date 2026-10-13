package com.ruoyi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonSerialize;
import com.ruoyi.common.config.serializer.PriceJsonSerializer;
import com.ruoyi.common.enums.PriceScope;

/**
 * 金额字段按岗位可见。序列化成 JSON 时，当前登录人无权查看就写成 {@code null}。
 *
 * <p>用法：</p>
 *
 * <pre>
 * &#64;PriceField(PriceScope.COST)
 * private BigDecimal costPrice;
 * </pre>
 *
 * <p>它<b>只管出口，不管入口</b>：接口收到的请求体照常绑定，无权用户回传的 {@code null}
 * 会原样进到 Service。改单入口必须自己用 {@code PriceMaskUtils.keepIfMasked} 兜住，
 * 否则「看不见」会变成「改没了」。见 {@code docs/金额按岗位可见.md} 第 6.1 节。</p>
 *
 * <h3>⚠ 只对走 Jackson 的出口有效</h3>
 *
 * <p>Excel 导出走 POI 和 {@code @Excel} 注解，服务端渲染 PDF 走模板引擎，
 * <b>两者都不经过 Jackson，这个注解在那里形同虚设</b>。目前带金额的实体一个导出口子
 * 都没有，所以眼下没有缺口；<b>以后给单据加导出或打印功能，必须在那里另挡一道。</b></p>
 *
 * @author wczl
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@JacksonAnnotationsInside
@JsonSerialize(using = PriceJsonSerializer.class)
public @interface PriceField
{
    /**
     * 字段归属哪一侧。
     *
     * <p>{@link PriceScope#AUTO} 留给「同一列两种口径」的字段，所在类要实现
     * {@code PriceScopeAware} 自己说算哪一侧。</p>
     */
    PriceScope value();
}
