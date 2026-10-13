package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ShipBatch;

/**
 * 发货批次主表 Mapper。月表按 {@code shipdate} 路由，一张订单的批次可能散在多张月表里。
 */
public interface ShipBatchMapper
{
    List<String> selectShipBatchTableSuffixes();

    List<ShipBatch> selectList(@Param("query") ShipBatch query, @Param("tableSuffix") String tableSuffix);

    ShipBatch selectByCode(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix);

    ShipBatch selectByCodeForUpdate(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix);

    int insert(@Param("batch") ShipBatch batch, @Param("tableSuffix") String tableSuffix);

    /** 只允许改待确认的批次。 */
    int updatePending(@Param("batch") ShipBatch batch, @Param("tableSuffix") String tableSuffix);

    /**
     * 状态迁移，带期望值 CAS。提交仓库、打印备货单、打印快递单号、完成、取消都走这里，
     * 各自的经手人与时间由对应的字段参数带入，传 null 的列保持原值。
     *
     * <p>备货完成与录入快递单号<b>不</b>走这里：那两步除了状态还要落照片 / 物流信息，
     * 见 {@link #completePicking} 与 {@link #enterLogistics}。</p>
     */
    int updateStatus(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix, @Param("status") String status,
            @Param("expectedStatus") String expectedStatus, @Param("operator") String operator,
            @Param("operateTime") Date operateTime);

    /**
     * 备货完成：已打印备货单 → 备货完成，同时落备货照片与经手人，带期望值 CAS。
     *
     * <p>照片和状态同一条语句落：拆成两条的话，照片写进去而状态迁移被 CAS 挡掉时，
     * 批次上会留着一组没人认领的照片。</p>
     *
     * @param photos 逗号分隔的照片 URL，服务层已保证至少一张
     */
    int completePicking(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix, @Param("photos") String photos,
            @Param("operator") String operator, @Param("operateTime") Date operateTime);

    /**
     * 补传 / 替换备货照片，不改状态。只认「已打印备货单 / 备货完成」两格——
     * 再往后业务员已经按这组照片录过快递单号，照片就是凭证，不该还能动。
     */
    int updatePickPhotos(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix, @Param("photos") String photos);

    /**
     * 录入快递单号：备货完成 → 已录入快递单号，同时回填承运商、快递单号与经手人，
     * 带期望值 CAS。传 null 的物流列保持原值（上门提货的单两项都可能没有）。
     */
    int enterLogistics(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix, @Param("logisticsNo") String logisticsNo,
            @Param("carrier") String carrier, @Param("operator") String operator,
            @Param("operateTime") Date operateTime);

    /**
     * 驳回到「待确认」，带期望值 CAS。
     *
     * <p>不走 {@link #updateStatus}：那条语句只往前推，按目标状态回填经手人；驳回要做的
     * 是反过来——把确认人、备货人、录单人、发货人连同备货照片<b>抹掉</b>，再记下驳回人
     * 与原因。硬塞进同一条 SQL 会让那个 {@code choose} 分支越滚越乱。</p>
     */
    int rejectToPending(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix, @Param("expectedStatus") String expectedStatus,
            @Param("operator") String operator, @Param("operateTime") Date operateTime,
            @Param("reason") String reason);

    int deletePending(@Param("shopCode") String shopCode, @Param("shipCode") String shipCode,
            @Param("tableSuffix") String tableSuffix);
}
