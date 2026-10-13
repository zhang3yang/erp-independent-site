package com.ruoyi.system.enums;

import java.util.Objects;

/**
 * 库存二维状态的组合值：良品状态 + 在库状态。
 *
 * <p>库存、批次、序列号按这两个维度分格存放，单据的来源/目标状态也用它表达。
 * 四种组合对应的业务含义：</p>
 *
 * <ul>
 *   <li>良品·在库 —— 可用库存，能销售、调拨、借出、领料</li>
 *   <li>不良品·在库 —— 待处理的坏品，也包括厂内维修中的</li>
 *   <li>良品·不在库 —— 借出在外的好货，以及领到车间的在制品</li>
 *   <li>不良品·不在库 —— 送外维修中的，以及借出在外的坏货</li>
 * </ul>
 *
 * <p>后两格里「为什么不在库」不再由库存表回答——借出、生产领用、送外维修混在一起，
 * 要区分只能翻单据。这是二维模型的既定取舍。</p>
 */
public final class StockState
{
    private final Integer qualityFlag;

    private final Integer onhandFlag;

    private StockState(Integer qualityFlag, Integer onhandFlag)
    {
        this.qualityFlag = qualityFlag;
        this.onhandFlag = onhandFlag;
    }

    public static StockState of(Integer qualityFlag, Integer onhandFlag)
    {
        return new StockState(qualityFlag, onhandFlag);
    }

    public static StockState of(QualityFlag quality, OnhandFlag onhand)
    {
        return new StockState(quality == null ? null : quality.getCode(),
                onhand == null ? null : onhand.getCode());
    }

    /** 可自由支配的正常库存：良品、在库 */
    public static StockState available()
    {
        return of(QualityFlag.GOOD, OnhandFlag.IN);
    }

    public Integer getQualityFlag()
    {
        return qualityFlag;
    }

    public Integer getOnhandFlag()
    {
        return onhandFlag;
    }

    /**
     * 两个维度是否都是合法枚举值。
     *
     * @return 合法为 true
     */
    public boolean isValid()
    {
        return QualityFlag.isValid(qualityFlag) && OnhandFlag.isValid(onhandFlag);
    }

    /**
     * 是否为可用库存。可用库存才能销售出库、调拨、借出、领料。
     *
     * @return 可用为 true
     */
    public boolean isAvailable()
    {
        return isGood() && isOnhand();
    }

    public boolean isGood()
    {
        return Objects.equals(QualityFlag.GOOD.getCode(), qualityFlag);
    }

    public boolean isOnhand()
    {
        return Objects.equals(OnhandFlag.IN.getCode(), onhandFlag);
    }

    /**
     * 供报错和单据备注使用的可读名称，形如「良品·在库」。
     * 有维度非法时该段显示原始值，方便定位脏数据。
     *
     * @return 二维状态名称
     */
    public String getLabel()
    {
        String quality = QualityFlag.getLabel(qualityFlag);
        String onhand = OnhandFlag.getLabel(onhandFlag);
        return (quality == null ? String.valueOf(qualityFlag) : quality) + "·"
                + (onhand == null ? String.valueOf(onhandFlag) : onhand);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof StockState))
        {
            return false;
        }
        StockState other = (StockState) obj;
        return Objects.equals(qualityFlag, other.qualityFlag)
                && Objects.equals(onhandFlag, other.onhandFlag);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(qualityFlag, onhandFlag);
    }

    @Override
    public String toString()
    {
        return getLabel();
    }
}
