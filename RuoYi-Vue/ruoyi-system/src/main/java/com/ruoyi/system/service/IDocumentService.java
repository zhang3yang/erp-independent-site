package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.DocumentMain;

/**
 * 单据Service接口
 */
public interface IDocumentService
{
    /**
     * 查询单据
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @return 单据（含明细）
     */
    public DocumentMain selectDocumentByCode(String shopCode, String docCode, String tableSuffix);

    /**
     * 查询单据列表
     *
     * @param document 查询条件
     * @return 单据集合
     */
    public List<DocumentMain> selectDocumentList(DocumentMain document);

    /**
     * 新增单据（含明细）
     *
     * @param document 单据信息
     * @return 结果
     */
    public int insertDocument(DocumentMain document);

    /**
     * 修改单据（含明细）
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
    public int deleteDocument(String shopCode, String docCode, String tableSuffix);

    /**
     * 审核单据
     *
     * @param shopCode 商家编码
     * @param docCode 单据代码
     * @param tableSuffix 月表后缀
     * @param approved 是否通过
     * @param confirmUser 审核人
     * @return 结果
     */
    public int confirmDocument(String shopCode, String docCode, String tableSuffix, boolean approved, String confirmUser);

    /**
     * 获取当前月表后缀（如 202607）
     *
     * @return 月表后缀
     */
    public String getCurrentTableSuffix();

    /** 根据业务日期获取月表后缀。 */
    public String getTableSuffixByBusinessDate(java.util.Date businessDate);
}
