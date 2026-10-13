package com.ruoyi.system.service.impl;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.SalesPackage;
import com.ruoyi.system.domain.SalesPackageItem;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.SalesPackageMapper;
import com.ruoyi.system.service.ISalesPackageService;

/**
 * 销售套餐实现。校验集中在 {@link #prepareItems}，建单和改单共用同一套。
 */
@Service
public class SalesPackageServiceImpl implements ISalesPackageService
{
    @Autowired private SalesPackageMapper packageMapper;
    @Autowired private InventoryGoodsMapper goodsMapper;

    @Override
    public List<SalesPackage> selectPackageList(String shopCode, SalesPackage query)
    {
        SalesPackage condition = query == null ? new SalesPackage() : query;
        condition.setShopCode(shopCode);
        return packageMapper.selectPackageList(condition);
    }

    @Override
    public SalesPackage selectPackageDetail(String shopCode, String packageCode)
    {
        SalesPackage found = requirePackage(shopCode, packageCode);
        found.setItems(packageMapper.selectItemsByPackage(shopCode, found.getPackageCode()));
        return found;
    }

    @Override
    public List<SalesPackage> selectActivePackages(String shopCode)
    {
        SalesPackage query = new SalesPackage();
        query.setShopCode(shopCode);
        query.setIsActive(1);
        List<SalesPackage> packages = packageMapper.selectPackageList(query);
        if (packages.isEmpty()) return packages;

        List<String> codes = new ArrayList<>();
        Map<String, SalesPackage> byCode = new LinkedHashMap<>();
        for (SalesPackage row : packages)
        {
            codes.add(row.getPackageCode());
            row.setItems(new ArrayList<>());
            byCode.put(row.getPackageCode(), row);
        }
        // 一趟查出全部明细再分组，别在循环里逐个套餐查
        Map<String, InventoryGoods> goodsCache = new LinkedHashMap<>();
        for (SalesPackageItem item : packageMapper.selectItemsByPackages(shopCode, codes))
        {
            SalesPackage owner = byCode.get(item.getPackageCode());
            if (owner == null) continue;
            // 名称规格单位用商品档案的当前值刷一遍，标准价也从这里取——套餐存的是建套餐
            // 那一刻的快照，商品改了名或调了价，下单时该按最新的来。
            // 同一商品可能出现在多个套餐里，查过的缓存住。
            InventoryGoods goods = goodsCache.computeIfAbsent(item.getGoodsCode(),
                    code -> goodsMapper.selectGoodsByCode(shopCode, code));
            if (goods != null)
            {
                item.setGoodsName(goods.getGoodsName());
                item.setSpec(goods.getSpec());
                item.setUnit(goods.getUnit());
                item.setStandardPrice(goods.getStandardPrice());
            }
            owner.getItems().add(item);
        }
        // 没有明细的套餐给不了任何商品，别摆在选择框里让人白点
        List<SalesPackage> usable = new ArrayList<>();
        for (SalesPackage row : packages)
        {
            if (!row.getItems().isEmpty()) usable.add(row);
        }
        return usable;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesPackage createPackage(String shopCode, SalesPackage command, String username)
    {
        validateHeader(command);
        String code = command.getPackageCode().trim();
        if (packageMapper.selectPackageByCode(shopCode, code) != null)
        {
            throw new ServiceException("套餐代码'" + code + "'已存在");
        }
        List<SalesPackageItem> items = prepareItems(shopCode, code, command.getItems());

        command.setShopCode(shopCode);
        command.setPackageCode(code);
        command.setPackageName(command.getPackageName().trim());
        command.setCreateUser(username);
        packageMapper.insertPackage(command);
        packageMapper.batchInsertItems(items);
        command.setItems(items);
        return command;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePackage(String shopCode, SalesPackage command, String username)
    {
        validateHeader(command);
        SalesPackage stored = requirePackage(shopCode, command.getPackageCode());
        List<SalesPackageItem> items = prepareItems(shopCode, stored.getPackageCode(), command.getItems());

        command.setShopCode(shopCode);
        command.setPackageCode(stored.getPackageCode());
        command.setPackageName(command.getPackageName().trim());
        command.setUpdateUser(username);
        if (packageMapper.updatePackage(command) != 1)
        {
            throw new ServiceException("套餐已被其他人修改，请刷新后重试");
        }
        // 明细整体重建：套餐是配置不是单据，没有需要保留的执行痕迹
        packageMapper.deleteItemsByPackage(shopCode, stored.getPackageCode());
        packageMapper.batchInsertItems(items);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePackage(String shopCode, String packageCode)
    {
        SalesPackage stored = requirePackage(shopCode, packageCode);
        // 删套餐不影响已下的订单——订单存的是商品行快照，不引用套餐
        packageMapper.deleteItemsByPackage(shopCode, stored.getPackageCode());
        return packageMapper.deletePackage(shopCode, stored.getPackageCode());
    }

    private void validateHeader(SalesPackage command)
    {
        if (command == null) throw new ServiceException("套餐内容不能为空");
        if (isBlank(command.getPackageCode())) throw new ServiceException("套餐代码不能为空");
        if (isBlank(command.getPackageName())) throw new ServiceException("套餐名称不能为空");
    }

    /**
     * 校验明细并回填商品档案快照。
     *
     * <p>三条规则：至少一行、同一商品不能开两行、数量必须为正。第二条与订单明细同口径——
     * 套餐里开两行同商品，铺进订单时也只会合成一行，不如在这里就说清楚。</p>
     */
    private List<SalesPackageItem> prepareItems(String shopCode, String packageCode, List<SalesPackageItem> source)
    {
        if (source == null || source.isEmpty()) throw new ServiceException("套餐至少要包含一个商品");

        List<SalesPackageItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SalesPackageItem row : source)
        {
            if (row == null || isBlank(row.getGoodsCode())) throw new ServiceException("套餐商品不能为空");
            String goodsCode = row.getGoodsCode().trim();
            if (!seen.add(goodsCode))
            {
                throw new ServiceException("商品'" + goodsCode + "'在套餐里重复，请合并数量");
            }
            if (row.getQty() == null || row.getQty().signum() <= 0)
            {
                throw new ServiceException("商品'" + goodsCode + "'的数量必须大于 0");
            }

            InventoryGoods goods = goodsMapper.selectGoodsByCode(shopCode, goodsCode);
            if (goods == null) throw new ServiceException("商品'" + goodsCode + "'不存在");

            SalesPackageItem item = new SalesPackageItem();
            item.setShopCode(shopCode);
            item.setPackageCode(packageCode);
            item.setGoodsCode(goodsCode);
            // 名称规格单位存快照，与订单明细同一套做法；下单时会按 goodsCode 重新取当前档案
            item.setGoodsName(goods.getGoodsName());
            item.setSpec(goods.getSpec());
            item.setUnit(goods.getUnit());
            // 数量按 4 位存，与订单明细、库存的口径一致（半打、0.5 米这类套餐是合法的）
            item.setQty(row.getQty().setScale(4, RoundingMode.HALF_UP));
            item.setRemark(row.getRemark());
            items.add(item);
        }
        return items;
    }

    private SalesPackage requirePackage(String shopCode, String packageCode)
    {
        if (isBlank(packageCode)) throw new ServiceException("套餐代码不能为空");
        SalesPackage found = packageMapper.selectPackageByCode(shopCode, packageCode.trim());
        if (found == null) throw new ServiceException("套餐不存在");
        return found;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
