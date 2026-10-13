package com.ruoyi.system.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryStocktake;
import com.ruoyi.system.domain.InventoryStocktakeItem;

/**
 * 库存盘点Mapper接口
 */
public interface InventoryStocktakeMapper
{
    public List<InventoryStocktake> selectStocktakeList(InventoryStocktake query);

    public InventoryStocktake selectStocktakeByCode(@Param("shopCode") String shopCode,
            @Param("stocktakeCode") String stocktakeCode);

    public InventoryStocktake selectStocktakeByCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("stocktakeCode") String stocktakeCode);

    public int insertStocktake(InventoryStocktake stocktake);

    /** 按条件改状态，from 不匹配时更新 0 行，调用方据此判断「状态已变化」 */
    public int updateStatus(@Param("shopCode") String shopCode, @Param("stocktakeCode") String stocktakeCode,
            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
            @Param("username") String username, @Param("rejectReason") String rejectReason);

    public int completeStocktake(InventoryStocktake stocktake);

    public int refreshLineCount(@Param("shopCode") String shopCode, @Param("stocktakeCode") String stocktakeCode);

    /** 明细，带商品档案的批次策略/成本方法和当前库存 */
    public List<InventoryStocktakeItem> selectItems(@Param("shopCode") String shopCode,
            @Param("stocktakeCode") String stocktakeCode);

    public int batchInsertItems(@Param("items") List<InventoryStocktakeItem> items);

    public int updateItemCount(InventoryStocktakeItem item);

    public int updateItemResult(InventoryStocktakeItem item);

    public int deleteItem(@Param("shopCode") String shopCode, @Param("stocktakeCode") String stocktakeCode,
            @Param("id") Long id);

    /**
     * 查这个仓库里哪些商品正压在未结束的盘点单上。
     *
     * @param excludeStocktakeCode 排除的盘点单，往单子上加商品时排除自己；不排除传 null
     * @return 命中的明细行，stocktakeCode / goodsCode / goodsName 有值
     */
    public List<InventoryStocktakeItem> selectFrozenGoods(@Param("shopCode") String shopCode,
            @Param("wareCode") String wareCode, @Param("goodsCodes") Collection<String> goodsCodes,
            @Param("excludeStocktakeCode") String excludeStocktakeCode);
}
