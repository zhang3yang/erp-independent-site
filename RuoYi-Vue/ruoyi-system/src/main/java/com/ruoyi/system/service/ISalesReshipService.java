package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesReship;

/**
 * 销售补发：一张原订单最多一张补发单，整单只发一次。
 *
 * <p>「最多一张」是数据库唯一索引 {@code uk_sales_reship_original_order} 保证的，
 * 所以建错单必须能删干净，否则那张原订单就永远补发不了了——见 {@link #delete}。</p>
 */
public interface ISalesReshipService
{
    List<SalesReship> selectList(SalesReship query);

    SalesReship selectDetail(String shopCode, String reshipCode);

    /** 指定月份内已发过货、且尚未创建补发单的原销售订单。 */
    List<SalesOrder> selectEligibleOrders(String shopCode, String tableSuffix);

    SalesReship create(String shopCode, SalesReship command, String username);

    /** 整张补发单一次性全部发出，不接受行级或分批数量。 */
    String ship(String shopCode, String reshipCode, String carrier, String logisticsNo, String username);

    /**
     * 删除还没发货的补发单，把原订单的补发名额还回去。
     *
     * <p>选错仓库、填错数量、选错原订单都靠它补救：补发单不能改（改单会让「一张原订单
     * 只补发一次」的口径变得含糊），所以补救方式是删掉重建。</p>
     *
     * <p>物理删除而不是置作废状态：唯一索引管的是 {@code originalordercode} 这一列，
     * 留着作废行照样占着名额，那等于没补救。待发货的补发单没出过库、没写过流水，
     * 删掉不会断任何账——与销售订单删草稿、销售退货删待提交单是同一口径。
     * 已发货的删不掉，那背后有出库单和库存流水。</p>
     */
    void delete(String shopCode, String reshipCode);
}
