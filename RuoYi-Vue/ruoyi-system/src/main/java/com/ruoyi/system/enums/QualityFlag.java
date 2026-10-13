package com.ruoyi.system.enums;

/**
 * 良品状态。库存三维状态之一，描述货物本身好不好。
 *
 * <p>与 {@link OnhandFlag} 组合起来取代改造前的单一库存状态码。
 * 值域固定，业务流转规则直接依赖它，不做成可配置字典。</p>
 */
public enum QualityFlag
{
    /** 良品，货物完好 */
    GOOD(1, "良品"),

    /** 不良品，货物损坏或有瑕疵。只有不良品才能送修。 */
    DEFECTIVE(0, "不良品");

    private final Integer code;

    private final String label;

    QualityFlag(Integer code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public Integer getCode()
    {
        return code;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * 按编码取枚举，取不到返回 null。
     *
     * @param code 编码
     * @return 良品状态，无匹配时为 null
     */
    public static QualityFlag fromCode(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (QualityFlag value : values())
        {
            if (value.code.equals(code))
            {
                return value;
            }
        }
        return null;
    }

    /**
     * 按编码取名称，取不到返回 null。单据、库存、流水上冗余存的名称都由这里生成，
     * 保证同一编码在各处显示一致。
     *
     * @param code 编码
     * @return 名称，无匹配时为 null
     */
    public static String getLabel(Integer code)
    {
        QualityFlag value = fromCode(code);
        return value == null ? null : value.getLabel();
    }

    /**
     * 是否为合法编码。
     *
     * @param code 编码
     * @return 合法为 true
     */
    public static boolean isValid(Integer code)
    {
        return fromCode(code) != null;
    }
}
