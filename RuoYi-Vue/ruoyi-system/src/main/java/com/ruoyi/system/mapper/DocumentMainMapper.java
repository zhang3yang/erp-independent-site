package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 单据主表Mapper接口
 */
public interface DocumentMainMapper
{
    /**
     * 查询单据
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @return 单据
     */
    public DocumentMain selectDocumentByCode(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix);

    /** 审核时锁定单据主记录，防止同一单据被并发重复审核。 */
    public DocumentMain selectDocumentByCodeForUpdate(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix);

    /** 按商家和客户端幂等键查询已创建单据。 */
    public DocumentMain selectDocumentByIdempotencyKey(@Param("shopCode") String shopCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 查询单据列表（单月）
     *
     * @param document 查询条件（含 tableSuffix）
     * @return 单据集合
     */
    public List<DocumentMain> selectDocumentList(@Param("query") DocumentMain document);

    /**
     * 查询单据列表（跨月）。
     *
     * <p>把命中的月表 union all 起来一次查完，而不是在 Java 里逐月查再拼——列表页用
     * PageHelper 分页，它只作用在一条 SQL 上，逐月查会导致每个月都返回第一页。</p>
     *
     * @param document 查询条件，其中的 tableSuffix 不参与，由 suffixes 决定扫哪些表
     * @param suffixes 要扫的月表后缀，必须已经过 {@code MonthTableSuffix} 校验且确实存在
     * @return 单据集合
     */
    public List<DocumentMain> selectDocumentListAcrossMonths(@Param("query") DocumentMain document,
            @Param("suffixes") List<String> suffixes);

    /** 全部单据主表月表后缀。 */
    public List<String> selectDocumentMainTableSuffixes();

    /**
     * 新增单据
     *
     * @param document 单据信息
     * @return 结果
     */
    public int insertDocument(DocumentMain document);

    /**
     * 修改单据
     *
     * @param document 单据信息
     * @return 结果
     */
    public int updateDocument(DocumentMain document);

    /**
     * 删除单据
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @return 结果
     */
    public int deleteDocumentByCode(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 审核单据
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @param confirmState 审核状态
     * @param confirmUser 审核人
     * @return 结果
     */
    public int confirmDocument(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("tableSuffix") String tableSuffix,
            @Param("confirmState") String confirmState,
            @Param("confirmUser") String confirmUser);

    /** 更新审核后确定的单据总成本。 */
    public int updateDocumentTotalAmount(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("totalAmount") java.math.BigDecimal totalAmount,
            @Param("tableSuffix") String tableSuffix);

    /**
     * 明细写入后回写表头的商品内容摘要。
     *
     * <p>建单时主表先于明细插入（幂等键要先生效），而摘要要等明细补齐商品名之后才准，
     * 所以补一条只改这一列的 update；值没变化时服务层不会调用它。</p>
     */
    public int updateDocumentGoodsSummary(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("goodsSummary") String goodsSummary,
            @Param("tableSuffix") String tableSuffix);

    /** 明细写入后回写单据表头数量与金额合计。 */
    public int updateDocumentTotals(@Param("shopCode") String shopCode,
            @Param("docCode") String docCode,
            @Param("totalQty") java.math.BigDecimal totalQty,
            @Param("totalAmount") java.math.BigDecimal totalAmount,
            @Param("tableSuffix") String tableSuffix);
}
