package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryGoodsPosition;

/**
 * 商品位置Mapper接口
 */
public interface InventoryGoodsPositionMapper
{
    public List<InventoryGoodsPosition> selectPositionList(InventoryGoodsPosition position);

    public InventoryGoodsPosition selectPositionById(@Param("shopCode") String shopCode,
            @Param("positionId") Long positionId);

    /**
     * 按商品编码查已登记的位置。一种商品只登记一条，所以最多返回一条；
     * 改的时候把自己排掉，否则改自己也会被判成重复。
     */
    public InventoryGoodsPosition selectPositionByGoodsCode(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("excludePositionId") Long excludePositionId);

    public int insertPosition(InventoryGoodsPosition position);

    public int updatePosition(InventoryGoodsPosition position);

    public int deletePositionByIds(@Param("shopCode") String shopCode,
            @Param("positionIds") Long[] positionIds);
}
