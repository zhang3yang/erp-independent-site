package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.InventoryPartner;

/**
 * 供应商/客户Mapper接口
 */
public interface InventoryPartnerMapper
{
    /**
     * 查询供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 供应商/客户
     */
    public InventoryPartner selectPartnerByCode(@Param("shopCode") String shopCode,
            @Param("partnerCode") String partnerCode);

    /**
     * 查询供应商/客户列表
     *
     * @param partner 供应商/客户查询条件
     * @return 供应商/客户集合
     */
    public List<InventoryPartner> selectPartnerList(InventoryPartner partner);

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
     * 删除供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 结果
     */
    public int deletePartnerByCode(@Param("shopCode") String shopCode,
            @Param("partnerCode") String partnerCode);

    /**
     * 批量删除供应商/客户
     *
     * @param shopCode 商家编码
     * @param partnerCodes 单位代码数组
     * @return 结果
     */
    public int deletePartnerByCodes(@Param("shopCode") String shopCode,
            @Param("partnerCodes") String[] partnerCodes);

    /**
     * 校验单位代码是否唯一
     *
     * @param shopCode 商家编码
     * @param partnerCode 单位代码
     * @return 结果
     */
    public int checkPartnerCodeUnique(@Param("shopCode") String shopCode,
            @Param("partnerCode") String partnerCode);
}
