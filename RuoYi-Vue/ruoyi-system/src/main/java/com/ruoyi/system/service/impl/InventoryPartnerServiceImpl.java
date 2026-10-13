package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MobileUtils;
import com.ruoyi.system.domain.GoodsPartner;
import com.ruoyi.system.domain.InventoryPartner;
import com.ruoyi.system.domain.PurchaseOrder;
import com.ruoyi.system.mapper.GoodsPartnerMapper;
import com.ruoyi.system.mapper.InventoryPartnerMapper;
import com.ruoyi.system.mapper.PurchaseOrderMapper;
import com.ruoyi.system.service.IInventoryPartnerService;

/**
 * 供应商/客户Service业务层处理
 */
@Service
public class InventoryPartnerServiceImpl implements IInventoryPartnerService
{
    @Autowired
    private InventoryPartnerMapper partnerMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private GoodsPartnerMapper goodsPartnerMapper;

    @Autowired
    private InventoryMasterDataCodeGenerator codeGenerator;

    /**
     * 查询供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 供应商/客户
     */
    @Override
    public InventoryPartner selectPartnerByCode(String shopCode, String partnerCode)
    {
        return partnerMapper.selectPartnerByCode(shopCode, partnerCode);
    }

    /**
     * 查询供应商/客户列表
     *
     * @param partner 供应商/客户查询条件
     * @return 供应商/客户集合
     */
    @Override
    public List<InventoryPartner> selectPartnerList(InventoryPartner partner)
    {
        return partnerMapper.selectPartnerList(partner);
    }

    /**
     * 按商品查历史供应商，给供应商选择做推荐。
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码，逗号分隔；空则返回空列表
     * @return 供货关系集合，按「最近一次入库日期倒序」排好
     */
    @Override
    public List<GoodsPartner> selectGoodsPartners(String shopCode, String goodsCodes)
    {
        if (goodsCodes == null || goodsCodes.trim().isEmpty())
        {
            return new ArrayList<>();
        }
        // 单据里的商品行数就是这个集合的上限，去重后直接进 in 条件。
        // 超长请求不该由这里兜——真有几百行明细的单，问题在建单那边。
        Set<String> codes = new LinkedHashSet<>();
        for (String code : goodsCodes.split(","))
        {
            String trimmed = code.trim();
            if (!trimmed.isEmpty())
            {
                codes.add(trimmed);
            }
        }
        if (codes.isEmpty())
        {
            return new ArrayList<>();
        }
        return goodsPartnerMapper.selectByGoodsCodes(shopCode, codes);
    }

    /**
     * 新增供应商/客户
     *
     * @param partner 供应商/客户信息
     * @return 结果
     */
    @Override
    public int insertPartner(InventoryPartner partner)
    {
        validatePhones(partner);

        // 新增往来单位的编码统一由服务端按单位类型生成，忽略客户端传入值。
        partner.setPartnerCode(codeGenerator.nextPartnerCode(partner.getPartnerType()));

        // 校验单位代码是否唯一
        if (!checkPartnerCodeUnique(partner.getShopCode(), partner.getPartnerCode()))
        {
            throw new ServiceException("单位代码'" + partner.getPartnerCode() + "'已存在");
        }

        // 设置默认值
        if (partner.getActiveFlag() == null)
        {
            partner.setActiveFlag(1);
        }

        return partnerMapper.insertPartner(partner);
    }

    /**
     * 修改供应商/客户
     *
     * @param partner 供应商/客户信息
     * @return 结果
     */
    @Override
    public int updatePartner(InventoryPartner partner)
    {
        validatePhones(partner);

        // 校验供应商/客户是否存在
        InventoryPartner existing = partnerMapper.selectPartnerByCode(partner.getShopCode(), partner.getPartnerCode());
        if (existing == null)
        {
            throw new ServiceException("供应商/客户不存在");
        }

        return partnerMapper.updatePartner(partner);
    }

    /**
     * 手机号与电话都按 11 位手机号收，口径见 {@link MobileUtils}。
     *
     * <p>两个字段都是选填，不填放行；填了就得是能打通的号码——往来单位的电话是采购员
     * 催货、库管员对账时唯一的联系方式，存一串位数不对的数字等于没存。</p>
     */
    private void validatePhones(InventoryPartner partner)
    {
        MobileUtils.validateOptional(partner.getMobile(), "手机号");
        MobileUtils.validateOptional(partner.getTelephone(), "电话");
    }

    /**
     * 批量删除供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCodes 单位代码数组
     * @return 结果
     */
    @Override
    public int deletePartnerByCodes(String shopCode, String[] partnerCodes)
    {
        // 校验供应商/客户是否被采购订单引用
        for (String partnerCode : partnerCodes)
        {
            PurchaseOrder orderQuery = new PurchaseOrder();
            orderQuery.setShopCode(shopCode);
            orderQuery.setPartnerCode(partnerCode);
            List<PurchaseOrder> orderList = purchaseOrderMapper.selectPurchaseOrderList(orderQuery);
            if (orderList != null && !orderList.isEmpty())
            {
                throw new ServiceException("单位'" + partnerCode + "'已被采购订单引用，不能删除");
            }
        }
        return partnerMapper.deletePartnerByCodes(shopCode, partnerCodes);
    }

    /**
     * 校验单位代码是否唯一
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 结果
     */
    @Override
    public boolean checkPartnerCodeUnique(String shopCode, String partnerCode)
    {
        int count = partnerMapper.checkPartnerCodeUnique(shopCode, partnerCode);
        return count == 0;
    }
}
