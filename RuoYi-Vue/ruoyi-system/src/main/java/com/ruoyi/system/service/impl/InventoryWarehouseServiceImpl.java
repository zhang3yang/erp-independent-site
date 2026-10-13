package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MobileUtils;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.mapper.InventoryStockMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IInventoryWarehouseService;

/**
 * 仓库Service业务层处理
 */
@Service
public class InventoryWarehouseServiceImpl implements IInventoryWarehouseService
{
    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private InventoryStockMapper stockMapper;

    @Autowired
    private InventoryMasterDataCodeGenerator codeGenerator;

    /**
     * 查询仓库
     *
     * @param shopCode 商家编码
     * @param wareCode 仓库代码
     * @return 仓库
     */
    @Override
    public InventoryWarehouse selectWarehouseByCode(String shopCode, String wareCode)
    {
        return warehouseMapper.selectWarehouseByCode(shopCode, wareCode);
    }

    /**
     * 查询仓库列表
     *
     * @param warehouse 仓库查询条件
     * @return 仓库集合
     */
    @Override
    public List<InventoryWarehouse> selectWarehouseList(InventoryWarehouse warehouse)
    {
        return warehouseMapper.selectWarehouseList(warehouse);
    }

    /**
     * 新增仓库
     *
     * @param warehouse 仓库信息
     * @return 结果
     */
    @Override
    public int insertWarehouse(InventoryWarehouse warehouse)
    {
        // 联系电话按 11 位手机号收，口径见 MobileUtils。选填，不填放行
        MobileUtils.validateOptional(warehouse.getContactPhone(), "联系电话");

        // 新增仓库的编码统一由服务端生成，忽略客户端传入值。
        warehouse.setWareCode(codeGenerator.nextWarehouseCode());

        // 校验仓库代码是否唯一
        if (!checkWareCodeUnique(warehouse.getShopCode(), warehouse.getWareCode()))
        {
            throw new ServiceException("仓库代码'" + warehouse.getWareCode() + "'已存在");
        }

        // 设置默认值
        if (warehouse.getActiveFlag() == null)
        {
            warehouse.setActiveFlag(1);
        }
        if (warehouse.getWareType() == null)
        {
            warehouse.setWareType("实体仓");
        }

        return warehouseMapper.insertWarehouse(warehouse);
    }

    /**
     * 修改仓库
     *
     * @param warehouse 仓库信息
     * @return 结果
     */
    @Override
    public int updateWarehouse(InventoryWarehouse warehouse)
    {
        MobileUtils.validateOptional(warehouse.getContactPhone(), "联系电话");

        // 校验仓库是否存在
        InventoryWarehouse existing = warehouseMapper.selectWarehouseByCode(warehouse.getShopCode(), warehouse.getWareCode());
        if (existing == null)
        {
            throw new ServiceException("仓库不存在");
        }

        return warehouseMapper.updateWarehouse(warehouse);
    }

    /**
     * 批量删除仓库
     *
     * @param shopCode 商家编码
     * @param wareCodes 仓库代码数组
     * @return 结果
     */
    @Override
    public int deleteWarehouseByCodes(String shopCode, String[] wareCodes)
    {
        // 校验仓库是否被库存引用
        for (String wareCode : wareCodes)
        {
            InventoryStock stockQuery = new InventoryStock();
            stockQuery.setShopCode(shopCode);
            stockQuery.setWareCode(wareCode);
            List<InventoryStock> stockList = stockMapper.selectStockList(stockQuery);
            if (stockList != null && !stockList.isEmpty())
            {
                throw new ServiceException("仓库'" + wareCode + "'已被库存引用，不能删除");
            }
        }
        return warehouseMapper.deleteWarehouseByCodes(shopCode, wareCodes);
    }

    /**
     * 校验仓库代码是否唯一
     *
     * @param shopCode 商家编码
     * @param wareCode 仓库代码
     * @return 结果
     */
    @Override
    public boolean checkWareCodeUnique(String shopCode, String wareCode)
    {
        int count = warehouseMapper.checkWareCodeUnique(shopCode, wareCode);
        return count == 0;
    }
}
