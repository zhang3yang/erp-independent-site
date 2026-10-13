package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.GoodsPartner;
import com.ruoyi.system.domain.InventoryPartner;

/**
 * 供应商/客户Service接口
 */
public interface IInventoryPartnerService
{
    /**
     * 查询供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 供应商/客户
     */
    public InventoryPartner selectPartnerByCode(String shopCode, String partnerCode);

    /**
     * 查询供应商/客户列表
     *
     * @param partner 供应商/客户查询条件
     * @return 供应商/客户集合
     */
    public List<InventoryPartner> selectPartnerList(InventoryPartner partner);

    /**
     * 按商品查历史供应商，给供应商选择做推荐。
     *
     * @param shopCode 商家编码
     * @param goodsCodes 商品代码，逗号分隔；空则返回空列表
     * @return 供货关系集合，按「最近一次入库日期倒序」排好
     */
    public List<GoodsPartner> selectGoodsPartners(String shopCode, String goodsCodes);

    /**
     * 新增供应商/客户
     *
     * @param partner 供应商/客户信息
     * @return 结果
     */
    public int insertPartner(InventoryPartner partner);

    /**
     * 修改供应商/客户
     *
     * @param partner 供应商/客户信息
     * @return 结果
     */
    public int updatePartner(InventoryPartner partner);

    /**
     * 批量删除供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCodes 单位代码数组
     * @return 结果
     */
    public int deletePartnerByCodes(String shopCode, String[] partnerCodes);

    /**
     * 校验单位代码是否唯一
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 结果
     */
    public boolean checkPartnerCodeUnique(String shopCode, String partnerCode);
}
