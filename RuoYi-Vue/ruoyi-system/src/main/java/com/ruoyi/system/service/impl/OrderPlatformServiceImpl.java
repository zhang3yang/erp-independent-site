package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.OrderPlatform;
import com.ruoyi.system.domain.OrderPlatformStore;
import com.ruoyi.system.mapper.OrderPlatformMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.service.IOrderPlatformService;
import com.ruoyi.system.utils.MonthTableSuffix;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPlatformServiceImpl implements IOrderPlatformService
{
    @Autowired private OrderPlatformMapper mapper;
    @Autowired private SalesOrderMapper salesOrderMapper;

    @Override
    public List<OrderPlatform> selectPlatformList(OrderPlatform query)
    {
        return mapper.selectPlatformList(query);
    }

    @Override
    public List<OrderPlatformStore> selectStoreList(OrderPlatformStore query)
    {
        return mapper.selectStoreList(query);
    }

    @Override
    public int insertPlatform(OrderPlatform platform)
    {
        normalizePlatform(platform);
        if (mapper.selectPlatformByCode(platform.getShopCode(), platform.getPlatformCode()) != null)
        {
            throw new ServiceException("平台代码 " + platform.getPlatformCode() + " 已存在");
        }
        return mapper.insertPlatform(platform);
    }

    @Override
    public int updatePlatform(OrderPlatform platform)
    {
        normalizePlatform(platform);
        OrderPlatform existing = mapper.selectPlatformByCode(platform.getShopCode(), platform.getPlatformCode());
        if (existing == null || !existing.getId().equals(platform.getId()))
        {
            throw new ServiceException("平台不存在；平台代码不能修改，如需调整请停用后新建");
        }
        return mapper.updatePlatform(platform);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePlatform(String shopCode, Long id)
    {
        OrderPlatform existing = findPlatformById(shopCode, id);
        if (existing == null) throw new ServiceException("平台不存在");
        if (mapper.countStores(shopCode, existing.getPlatformCode()) > 0)
        {
            throw new ServiceException("平台「" + existing.getPlatformName() + "」下还有店铺，不能删除；可改为停用");
        }
        return mapper.deletePlatform(shopCode, id);
    }

    @Override
    public int insertStore(OrderPlatformStore store)
    {
        normalizeStore(store);
        requirePlatform(store.getShopCode(), store.getPlatformCode());
        requireStoreUnique(store, null);
        return mapper.insertStore(store);
    }

    @Override
    public int updateStore(OrderPlatformStore store)
    {
        normalizeStore(store);
        OrderPlatformStore existing = mapper.selectStoreByCode(
                store.getShopCode(), store.getPlatformCode(), store.getStoreCode());
        if (existing == null || !existing.getId().equals(store.getId()))
        {
            throw new ServiceException("店铺不存在；平台和店铺代码不能修改，如需调整请停用后新建");
        }
        store.setPlatformCode(existing.getPlatformCode());
        requireStoreUnique(store, existing.getId());
        return mapper.updateStore(store);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteStore(String shopCode, Long id)
    {
        OrderPlatformStore existing = findStoreById(shopCode, id);
        if (existing == null) throw new ServiceException("店铺不存在");
        for (String suffix : MonthTableSuffix.validDescending(salesOrderMapper.selectOrderTableSuffixes()))
        {
            if (mapper.countStoreOrders(shopCode, existing.getPlatformCode(), existing.getStoreCode(), suffix) > 0)
            {
                throw new ServiceException("店铺「" + existing.getStoreName() + "」已被销售订单引用，不能删除；可改为停用");
            }
        }
        return mapper.deleteStore(shopCode, id);
    }

    private OrderPlatform findPlatformById(String shopCode, Long id)
    {
        OrderPlatform query = new OrderPlatform();
        query.setShopCode(shopCode);
        return mapper.selectPlatformList(query).stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
    }

    private OrderPlatformStore findStoreById(String shopCode, Long id)
    {
        OrderPlatformStore query = new OrderPlatformStore();
        query.setShopCode(shopCode);
        return mapper.selectStoreList(query).stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * 店铺代码和店铺名称只在同一平台内唯一。
     *
     * <p>同一个商家在不同平台上开的店，代码和名称撞车是常态——「自营店」几乎每个平台都有一个，
     * 平台自己给的店铺代码也各按各的规则来。原先按 (shop_code, store_code) 全局判重，
     * 等于逼着用户给每个平台的同名店手工编一个不重复的代码。</p>
     *
     * @param selfId 改单时传自己的 id，避免把自己判成重复；新增传 null
     */
    private void requireStoreUnique(OrderPlatformStore store, Long selfId)
    {
        OrderPlatformStore sameCode = mapper.selectStoreByCode(
                store.getShopCode(), store.getPlatformCode(), store.getStoreCode());
        if (sameCode != null && !sameCode.getId().equals(selfId))
        {
            throw new ServiceException("平台「" + sameCode.getPlatformName() + "」下店铺代码 "
                    + store.getStoreCode() + " 已存在");
        }
        OrderPlatformStore sameName = mapper.selectStoreByName(
                store.getShopCode(), store.getPlatformCode(), store.getStoreName());
        if (sameName != null && !sameName.getId().equals(selfId))
        {
            throw new ServiceException("平台「" + sameName.getPlatformName() + "」下店铺名称「"
                    + store.getStoreName() + "」已存在");
        }
    }

    private void requirePlatform(String shopCode, String platformCode)
    {
        if (mapper.selectPlatformByCode(shopCode, platformCode) == null)
        {
            throw new ServiceException("所选平台不存在");
        }
    }

    private void normalizePlatform(OrderPlatform platform)
    {
        if (platform == null) throw new ServiceException("平台内容不能为空");
        platform.setPlatformCode(require(platform.getPlatformCode(), "平台代码", 30));
        platform.setPlatformName(require(platform.getPlatformName(), "平台名称", 50));
        platform.setRemark(optional(platform.getRemark(), "备注", 200));
        if (platform.getIsActive() == null) platform.setIsActive(1);
    }

    private void normalizeStore(OrderPlatformStore store)
    {
        if (store == null) throw new ServiceException("店铺内容不能为空");
        store.setPlatformCode(require(store.getPlatformCode(), "所属平台", 30));
        store.setStoreCode(require(store.getStoreCode(), "店铺代码", 50));
        store.setStoreName(require(store.getStoreName(), "店铺名称", 100));
        store.setRemark(optional(store.getRemark(), "备注", 200));
        if (store.getIsActive() == null) store.setIsActive(1);
    }

    private String require(String value, String label, int max)
    {
        String result = StringUtils.trimToNull(value);
        if (result == null) throw new ServiceException(label + "不能为空");
        if (result.length() > max) throw new ServiceException(label + "不能超过 " + max + " 个字符");
        return result;
    }

    private String optional(String value, String label, int max)
    {
        String result = StringUtils.trimToNull(value);
        if (result != null && result.length() > max) throw new ServiceException(label + "不能超过 " + max + " 个字符");
        return result;
    }
}
