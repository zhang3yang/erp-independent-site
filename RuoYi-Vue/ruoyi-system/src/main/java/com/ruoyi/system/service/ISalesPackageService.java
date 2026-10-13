package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.SalesPackage;

/**
 * 销售套餐主数据。套餐是一组商品的组合标签，不是商品，见 {@link com.ruoyi.system.domain.SalesPackage}。
 */
public interface ISalesPackageService
{
    /** 列表，不带明细。列表页只显示商品种类数，展开才查明细。 */
    List<SalesPackage> selectPackageList(String shopCode, SalesPackage query);

    /** 详情，含明细行。 */
    SalesPackage selectPackageDetail(String shopCode, String packageCode);

    /**
     * 下单时可选的套餐，<b>含明细</b>，只列启用中的。
     *
     * <p>明细一次性批量查出来按套餐分组，不逐个套餐查——选择框里通常十几个套餐，
     * 逐个查就是十几趟往返。</p>
     */
    List<SalesPackage> selectActivePackages(String shopCode);

    /** 新建。套餐代码同商家内唯一。 */
    SalesPackage createPackage(String shopCode, SalesPackage command, String username);

    /**
     * 修改。套餐代码不可改，明细整体重建。
     *
     * <p>改套餐<b>不影响已经下过的订单</b>：订单存的是商品行快照，不是对套餐的引用。</p>
     */
    int updatePackage(String shopCode, SalesPackage command, String username);

    /** 删除，连明细一起删。 */
    int deletePackage(String shopCode, String packageCode);
}
