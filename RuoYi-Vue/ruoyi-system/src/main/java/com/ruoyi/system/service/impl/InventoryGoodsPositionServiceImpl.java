package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.InventoryGoodsPosition;
import com.ruoyi.system.domain.InventoryWarehouse;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.InventoryGoodsPositionMapper;
import com.ruoyi.system.mapper.InventoryWarehouseMapper;
import com.ruoyi.system.service.IInventoryGoodsPositionService;

/**
 * 商品位置Service业务层处理
 */
@Service
public class InventoryGoodsPositionServiceImpl implements IInventoryGoodsPositionService
{
    @Autowired
    private InventoryGoodsPositionMapper positionMapper;

    @Autowired
    private InventoryGoodsMapper goodsMapper;

    @Autowired
    private InventoryWarehouseMapper warehouseMapper;

    @Autowired
    private StaffNameResolver staffNameResolver;

    @Override
    public List<InventoryGoodsPosition> selectPositionList(InventoryGoodsPosition position)
    {
        List<InventoryGoodsPosition> list = positionMapper.selectPositionList(position);
        if (list != null && !list.isEmpty())
        {
            java.util.List<String> logins = new java.util.ArrayList<>();
            for (InventoryGoodsPosition item : list)
            {
                logins.add(item.getCreateUser());
            }
            java.util.Map<String, String> names = staffNameResolver.nickNamesByLogin(logins);
            for (InventoryGoodsPosition item : list)
            {
                String login = item.getCreateUser();
                // 查不到就回退显示原登录名，不留空
                item.setCreateUserName(login == null ? null : names.getOrDefault(login, login));
            }
        }
        return list;
    }

    @Override
    public InventoryGoodsPosition selectPositionById(String shopCode, Long positionId)
    {
        return positionMapper.selectPositionById(shopCode, positionId);
    }

    @Override
    public int insertPosition(InventoryGoodsPosition position)
    {
        fillSnapshot(position);
        checkUnique(position, null);
        try
        {
            return positionMapper.insertPosition(position);
        }
        catch (DuplicateKeyException e)
        {
            // 两个人同时给同一个商品登记，双方都过了上面的检查，唯一键在这里兜底。
            // 不转的话页面上会看到一串 Duplicate entry 的原始报错。
            throw duplicateGoodsException(position);
        }
    }

    @Override
    public int updatePosition(InventoryGoodsPosition position)
    {
        InventoryGoodsPosition existing = positionMapper.selectPositionById(position.getShopCode(),
                position.getPositionId());
        if (existing == null)
        {
            throw new ServiceException("商品位置不存在");
        }
        fillSnapshot(position);
        checkUnique(position, position.getPositionId());
        try
        {
            return positionMapper.updatePosition(position);
        }
        catch (DuplicateKeyException e)
        {
            throw duplicateGoodsException(position);
        }
    }

    @Override
    public int deletePositionByIds(String shopCode, Long[] positionIds)
    {
        return positionMapper.deletePositionByIds(shopCode, positionIds);
    }

    /**
     * 商品名称、规格、单位、仓库名称一律按编码从主档重新取，
     * 不信任前端传上来的值，免得页面上改了显示就把错的名字存进去。
     */
    private void fillSnapshot(InventoryGoodsPosition position)
    {
        InventoryGoods goods = goodsMapper.selectGoodsByCode(position.getShopCode(), position.getGoodsCode());
        if (goods == null)
        {
            throw new ServiceException("商品'" + position.getGoodsCode() + "'不存在");
        }
        InventoryWarehouse warehouse = warehouseMapper.selectWarehouseByCode(position.getShopCode(),
                position.getWareCode());
        if (warehouse == null)
        {
            throw new ServiceException("仓库'" + position.getWareCode() + "'不存在");
        }
        position.setGoodsName(goods.getGoodsName());
        position.setSpec(goods.getSpec());
        position.setUnit(goods.getUnit());
        position.setWareName(warehouse.getWareName());
    }

    /**
     * 一种商品只登记一个位置，跨仓库也算重复：同一个商品不能既在 A 仓又在 B 仓各记一条。
     * 撞上了就把已有那条在哪个仓库说清楚，库管员照着去改，别在这边再建一条。
     */
    private void checkUnique(InventoryGoodsPosition position, Long excludePositionId)
    {
        InventoryGoodsPosition existing = positionMapper.selectPositionByGoodsCode(
                position.getShopCode(), position.getGoodsCode(), excludePositionId);
        if (existing != null)
        {
            // 位置是大文本，不往错误提示里塞，只说清在哪个仓库，页面上一搜就能找到那条
            throw new ServiceException("商品'" + position.getGoodsName() + "'已在仓库'"
                    + existing.getWareName() + "'登记过存放位置，一种商品只能登记一个位置，请修改原记录");
        }
    }

    private ServiceException duplicateGoodsException(InventoryGoodsPosition position)
    {
        return new ServiceException("商品'" + position.getGoodsName()
                + "'已登记过存放位置，一种商品只能登记一个位置，请刷新后修改原记录");
    }
}
