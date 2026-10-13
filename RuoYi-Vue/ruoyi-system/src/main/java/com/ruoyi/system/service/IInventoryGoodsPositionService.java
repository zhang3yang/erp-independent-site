package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.InventoryGoodsPosition;

/**
 * 商品位置Service接口
 */
public interface IInventoryGoodsPositionService
{
    /**
     * 查询商品位置列表
     */
    public List<InventoryGoodsPosition> selectPositionList(InventoryGoodsPosition position);

    /**
     * 查询商品位置详情
     */
    public InventoryGoodsPosition selectPositionById(String shopCode, Long positionId);

    /**
     * 新增商品位置
     */
    public int insertPosition(InventoryGoodsPosition position);

    /**
     * 修改商品位置
     */
    public int updatePosition(InventoryGoodsPosition position);

    /**
     * 批量删除商品位置
     */
    public int deletePositionByIds(String shopCode, Long[] positionIds);
}
