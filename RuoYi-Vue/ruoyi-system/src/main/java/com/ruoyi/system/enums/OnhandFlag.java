package com.ruoyi.system.enums;

/**
 * 在库状态。库存三维状态之一，描述货物在不在自己仓库里。
 *
 * <p>「不在库」这一格里混着借出在外的货、领到车间的在制品、送外维修中的货——
 * 二维模型下库存表不回答「为什么不在库」，要区分只能翻单据。</p>
 */
public enum OnhandFlag
{
    /** 在库，货物在自己仓库或厂区内 */
    IN(1, "在库"),

    /** 不在库，货物已离开仓库，如借出给客户、送外维修 */
    OUT(0, "不在库");

    private final Integer code;

    private final String label;

    OnhandFlag(Integer code, String label)
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
     * @return 在库状态，无匹配时为 null
     */
    public static OnhandFlag fromCode(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (OnhandFlag value : values())
        {
            if (value.code.equals(code))
            {
                return value;
            }
        }
        return null;
    }

    /**
     * 按编码取名称，取不到返回 null。
     *
     * @param code 编码
     * @return 名称，无匹配时为 null
     */
    public static String getLabel(Integer code)
    {
        OnhandFlag value = fromCode(code);
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
