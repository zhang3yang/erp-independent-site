package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.enums.StockState;

/**
 * 序列号Mapper接口
 */
public interface InventorySerialMapper
{
    /**
     * 查询序列号
     */
    public InventorySerial selectSerialByKey(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo);

    public InventorySerial selectSerialByContext(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state);

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
    public int outboundSerial(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state,
            @Param("batchCode") String batchCode, @Param("outboundDoc") String outboundDoc);

    /** 成本调整：只改在库序列号的单件成本 */
    public int updateSerialCost(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo, @Param("goodsCode") String goodsCode,
            @Param("wareCode") String wareCode, @Param("state") StockState state,
            @Param("costPrice") java.math.BigDecimal costPrice);

    /** 序列号换仓或换状态：按来源仓库和来源三维状态定位，改成目标仓库和目标三维状态。 */
    public int transferSerial(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo, @Param("goodsCode") String goodsCode,
            @Param("fromWareCode") String fromWareCode, @Param("fromState") StockState fromState,
            @Param("batchCode") String batchCode, @Param("toWareCode") String toWareCode,
            @Param("toWareName") String toWareName, @Param("toState") StockState toState);

    /**
     * 检查序列号是否存在
     */
    public int checkSerialExists(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo);

    /**
     * 检查序列号是否在库
     */
    public int checkSerialInStock(@Param("shopCode") String shopCode,
            @Param("serialNo") String serialNo);

    /**
     * 查询在库序列号列表
     */
    public List<InventorySerial> selectInStockSerialList(@Param("shopCode") String shopCode,
            @Param("goodsCode") String goodsCode, @Param("wareCode") String wareCode,
            @Param("state") StockState state);
}
