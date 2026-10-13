package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.mapper.InventorySerialMapper;
import com.ruoyi.system.service.IInventorySerialService;

/**
 * 序列号Service业务层处理
 */
@Service
public class InventorySerialServiceImpl implements IInventorySerialService
{
    @Autowired
    private InventorySerialMapper serialMapper;

    @Autowired
    private StaffNameResolver staffNameResolver;

    /**
     * 查询序列号
     */
    @Override
    public InventorySerial selectSerialByKey(String shopCode, String serialNo)
    {
        InventorySerial serial = serialMapper.selectSerialByKey(shopCode, serialNo);
        if (serial != null)
        {
            serial.setCreateUserName(staffNameResolver.nickNameOrLogin(serial.getCreateUser()));
        }
        return serial;
    }

    @Override
    public InventorySerial selectSerialByContext(String shopCode, String serialNo, String goodsCode,
            String wareCode, StockState state)
    {
        return serialMapper.selectSerialByContext(shopCode, serialNo, goodsCode, wareCode, state);
    }

    /**
     * 查询序列号列表
     */
    @Override
    public List<InventorySerial> selectSerialList(InventorySerial serial)
    {
        return serialMapper.selectSerialList(serial);
    }

    /**
     * 新增序列号
     */
    @Override
    public int insertSerial(InventorySerial serial)
    {
        // 校验序列号是否已存在
        if (checkSerialExists(serial.getShopCode(), serial.getSerialNo()))
        {
            throw new ServiceException("序列号'" + serial.getSerialNo() + "'已存在");
        }

        // 设置默认值
        if (serial.getStatus() == null || serial.getStatus().isEmpty())
        {
            serial.setStatus("instock");
        }

        return serialMapper.insertSerial(serial);
    }

    /**
     * 修改序列号
     */
    @Override
    public int updateSerial(InventorySerial serial)
    {
        // 校验是否存在
        InventorySerial existing = serialMapper.selectSerialByKey(serial.getShopCode(), serial.getSerialNo());
        if (existing == null)
        {
            throw new ServiceException("序列号不存在");
        }

        return serialMapper.updateSerial(serial);
    }

    /**
     * 批量新增序列号
     */
    @Override
    public int batchInsertSerial(List<InventorySerial> serials)
    {
        if (serials == null || serials.isEmpty())
        {
            return 0;
        }

        // 校验序列号是否重复
        Set<String> requestSerials = new HashSet<>();
        for (InventorySerial serial : serials)
        {
            if (serial == null || serial.getSerialNo() == null || serial.getSerialNo().trim().isEmpty())
            {
                throw new ServiceException("序列号不能为空");
            }
            serial.setSerialNo(serial.getSerialNo().trim());
            if (!requestSerials.add(serial.getSerialNo()))
            {
                throw new ServiceException("序列号'" + serial.getSerialNo() + "'在本次入库中重复");
            }
            if (checkSerialExists(serial.getShopCode(), serial.getSerialNo()))
            {
                throw new ServiceException("序列号'" + serial.getSerialNo() + "'已存在");
            }
            if (serial.getStatus() == null || serial.getStatus().isEmpty())
            {
                serial.setStatus("instock");
            }
        }

        try
        {
            return serialMapper.batchInsertSerial(serials);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("序列号已存在，请检查后重试");
        }
    }

    /**
     * 序列号出库
     */
    @Override
    public int outboundSerial(String shopCode, String serialNo, String goodsCode, String wareCode,
            StockState state, String outboundDoc)
    {
        // 1. 按本次出库上下文精确查询，避免同号历史记录干扰合法出库
        InventorySerial serial = serialMapper.selectSerialByContext(
                shopCode, serialNo, goodsCode, wareCode, state);
        if (serial == null)
        {
            InventorySerial sameSerial = serialMapper.selectSerialByKey(shopCode, serialNo);
            if (sameSerial == null)
            {
                throw new ServiceException("序列号'" + serialNo + "'不存在");
            }
            if (!goodsCode.equals(sameSerial.getGoodsCode()))
            {
                throw new ServiceException("序列号'" + serialNo + "'不属于当前商品（当前归属商品："
                        + sameSerial.getGoodsCode() + "）");
            }
            if (!wareCode.equals(sameSerial.getWareCode()))
            {
                throw new ServiceException("序列号'" + serialNo + "'不属于当前仓库（当前归属仓库："
                        + sameSerial.getWareName() + "）");
            }
            throw new ServiceException("序列号'" + serialNo + "'库存状态不匹配（当前状态："
                    + sameSerial.getStockState().getLabel() + "）");
        }

        // 2. 校验序列号是否在库
        if (!"instock".equals(serial.getStatus()))
        {
            throw new ServiceException("序列号'" + serialNo + "'已出库（出库单：" + serial.getOutboundDoc() + "），不能重复出库");
        }

        // 3. 执行出库（SQL 层面通过完整上下文和 activeflag=1 保证并发安全）
        int rows = serialMapper.outboundSerial(shopCode, serialNo, goodsCode, wareCode, state,
                serial.getBatchCode(), outboundDoc);
        if (rows != 1)
        {
            throw new ServiceException("序列号'" + serialNo + "'状态已变更，请刷新后重试");
        }
        return rows;
    }

    @Override
    public int transferSerial(String shopCode, String serialNo, String goodsCode,
            String fromWareCode, StockState fromState, String toWareCode, String toWareName,
            StockState toState)
    {
        InventorySerial serial = serialMapper.selectSerialByContext(
                shopCode, serialNo, goodsCode, fromWareCode, fromState);
        if (serial == null || !"instock".equals(serial.getStatus()))
        {
            throw new ServiceException("序列号'" + serialNo + "'不属于调出商品、仓库或库存状态，或已不在库");
        }
        int rows = serialMapper.transferSerial(shopCode, serialNo, goodsCode,
                fromWareCode, fromState, serial.getBatchCode(), toWareCode, toWareName, toState);
        if (rows != 1)
        {
            throw new ServiceException("序列号'" + serialNo + "'已被其他单据处理，请刷新后重试");
        }
        return rows;
    }

    /**
     * 检查序列号是否存在
     */
    @Override
    public boolean checkSerialExists(String shopCode, String serialNo)
    {
        return serialMapper.checkSerialExists(shopCode, serialNo) > 0;
    }

    /**
     * 检查序列号是否在库
     */
    @Override
    public boolean checkSerialInStock(String shopCode, String serialNo)
    {
        return serialMapper.checkSerialInStock(shopCode, serialNo) > 0;
    }

    /**
     * 查询在库序列号列表
     */
    @Override
    public List<InventorySerial> selectInStockSerialList(String shopCode, String goodsCode, String wareCode)
    {
        return serialMapper.selectInStockSerialList(shopCode, goodsCode, wareCode, null);
    }

    @Override
    public List<InventorySerial> selectInStockSerialList(String shopCode, String goodsCode, String wareCode,
            StockState state)
    {
        return serialMapper.selectInStockSerialList(shopCode, goodsCode, wareCode, state);
    }
}
