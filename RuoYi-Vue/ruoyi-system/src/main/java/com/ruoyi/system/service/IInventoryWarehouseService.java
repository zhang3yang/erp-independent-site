package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.InventoryWarehouse;

/**
 * 仓库Service接口
 */
public interface IInventoryWarehouseService
{
    /**
     * 查询仓库
     *
     * @param shopCode 商家编码
     * @param wareCode 仓库代码
     * @return 仓库
     */
    public InventoryWarehouse selectWarehouseByCode(String shopCode, String wareCode);

    /**
     * 查询仓库列表
     *
     * @param warehouse 仓库查询条件
     * @return 仓库集合
     */
    public List<InventoryWarehouse> selectWarehouseList(InventoryWarehouse warehouse);

    /**
     * 新增仓库
     *
     * @param warehouse 仓库信息
     * @return 结果
     */
    public int insertWarehouse(InventoryWarehouse warehouse);

    /**
     * 修改仓库
     *
     * @param warehouse 仓库信息
     * @return 结果
     */
    public int updateWarehouse(InventoryWarehouse warehouse);

    /**
     * 批量删除仓库
     *
     * @param shopCode 商家编码
     * @param wareCodes 仓库代码数组
     * @return 结果
     */
    public int deleteWarehouseByCodes(String shopCode, String[] wareCodes);

    /**
     * 校验仓库代码是否唯一
     *
     * @param shopCode 商家编码
     * @param wareCode 仓库代码
     * @return 结果
     */
    public boolean checkWareCodeUnique(String shopCode, String wareCode);
}
