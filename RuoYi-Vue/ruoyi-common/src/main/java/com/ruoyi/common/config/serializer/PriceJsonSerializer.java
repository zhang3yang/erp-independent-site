package com.ruoyi.common.config.serializer;

import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import com.ruoyi.common.annotation.PriceField;
import com.ruoyi.common.core.domain.PriceScopeAware;
import com.ruoyi.common.enums.PriceScope;
import com.ruoyi.common.utils.PriceMaskUtils;

/**
 * 金额按岗位可见的序列化过滤：无权查看就写 {@code null}。
 *
 * <p>结构照抄同目录的 {@code SensitiveJsonSerializer}，只是把判据从「是不是管理员」
 * 换成「有没有这个权限」。</p>
 *
 * <p>写 {@code null} 而不是 {@code 0}：{@code 0} 会被前端当成真实金额参与合计，
 * 算出一堆假账；{@code null} 的语义就是「这个值不给你」。</p>
 *
 * @author wczl
 */
public class PriceJsonSerializer extends StdSerializer<Object>
{
    /** 字段归属侧；{@code null} 表示这个实例还没被 contextual 化。 */
    private final PriceScope scope;

    /** 字段名，{@link PriceScope#AUTO} 回头问所在对象时要用。 */
    private final String fieldName;

    /** 有权查看时真正干活的序列化器（BigDecimal 等）。 */
    private final ValueSerializer<Object> delegate;

    public PriceJsonSerializer()
    {
        super(Object.class);
        this.scope = null;
        this.fieldName = null;
        this.delegate = null;
    }

    @SuppressWarnings("unchecked")
    public PriceJsonSerializer(PriceScope scope, String fieldName, ValueSerializer<?> delegate)
    {
        super(Object.class);
        this.scope = scope;
        this.fieldName = fieldName;
        this.delegate = (ValueSerializer<Object>) delegate;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException
    {
        if (scope != null && !PriceMaskUtils.isVisible(effectiveScope(gen)))
        {
            gen.writeNull();
            return;
        }
        if (delegate != null)
        {
            delegate.serialize(value, gen, ctxt);
        }
        else
        {
            // 没被 contextual 化（理论上不会走到）：按原值输出，别把数据吞了
            ctxt.writeValue(gen, value);
        }
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) throws DatabindException
    {
        if (Objects.isNull(property))
        {
            return this;
        }
        PriceField annotation = property.getAnnotation(PriceField.class);
        ValueSerializer<?> valueSerializer = ctxt.findValueSerializer(property.getType());
        if (Objects.isNull(annotation))
        {
            return valueSerializer;
        }
        return new PriceJsonSerializer(annotation.value(), property.getName(), valueSerializer);
    }

    /**
     * {@link PriceScope#AUTO} 的字段问所在对象这一次算哪一侧。
     *
     * <p>靠 {@code JsonGenerator.currentValue()} 拿到正在序列化的那个 bean —— Jackson
     * 在写对象的第一个字段之前就把它挂上去了。</p>
     *
     * <p>拿不到、或者对象不肯说（查询条件对象、{@code docType} 为空的草稿），就返回
     * {@code AUTO}，由 {@link PriceMaskUtils#isVisible} 按最严的走：两个权限都有才给看。</p>
     */
    private PriceScope effectiveScope(JsonGenerator gen)
    {
        if (scope != PriceScope.AUTO)
        {
            return scope;
        }
        try
        {
            Object owner = gen.currentValue();
            if (owner instanceof PriceScopeAware)
            {
                PriceScope resolved = ((PriceScopeAware) owner).resolvePriceScope(fieldName);
                if (resolved != null && resolved != PriceScope.AUTO)
                {
                    return resolved;
                }
            }
        }
        catch (Exception e)
        {
            // 判不出来就按最严的走，不让异常冒到接口上
        }
        return PriceScope.AUTO;
    }
}
