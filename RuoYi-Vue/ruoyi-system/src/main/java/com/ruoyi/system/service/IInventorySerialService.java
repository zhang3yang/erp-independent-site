package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventorySerial;

/**
 * 序列号Service接口
 */
public interface IInventorySerialService
{
    /**
     * 查询序列号
     */
    public InventorySerial selectSerialByKey(String shopCode, String serialNo);

    public InventorySerial selectSerialByContext(String shopCode, String serialNo, String goodsCode,
            String wareCode, StockState state);

    /**
     * 查询序列号列表
     */
    public List<InventorySerial> selectSerialList(InventorySerial serial);

    /**
     * 新增序列号
     */
    public int insertSerial(InventorySerial serial);

    /**
     * 修改序列号
     */
    public int updateSerial(InventorySerial serial);

    /**
     * 批量新增序列号
     */
    public int batchInsertSerial(List<InventorySerial> serials);

    /**
     * 序列号出库
     */
    public int outboundSerial(String shopCode, String serialNo, String goodsCode, String wareCode,
            StockState state, String outboundDoc);

    public int transferSerial(String shopCode, String serialNo, String goodsCode,
            String fromWareCode, StockState fromState, String toWareCode, String toWareName,
            StockState toState);

    /**
     * 检查序列号是否存在
     */
    public boolean checkSerialExists(String shopCode, String serialNo);

    /**
     * 检查序列号是否在库
     */
    public boolean checkSerialInStock(String shopCode, String serialNo);

    /**
     * 查询在库序列号列表
     */
    public List<InventorySerial> selectInStockSerialList(String shopCode, String goodsCode, String wareCode);

    /** 查询指定商品、仓库、库存状态下的在库序列号 */
    public List<InventorySerial> selectInStockSerialList(String shopCode, String goodsCode, String wareCode,
            StockState state);
}
