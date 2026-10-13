package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DocTypeConfig;

/**
 * 单据类型配置Mapper接口
 */
public interface DocTypeConfigMapper
{
    /**
     * 查询单据类型
     *
     * @param docType 单据类型
     * @return 单据类型
     */
    public DocTypeConfig selectDocTypeByCode(@Param("docType") String docType);

    /**
     * 查询单据类型列表
     *
     * @param config 查询条件
     * @return 单据类型集合
     */
    public List<DocTypeConfig> selectDocTypeList(DocTypeConfig config);

    /**
     * 新增单据类型
     *
     * @param config 单据类型信息
     * @return 结果
     */
    public int insertDocType(DocTypeConfig config);

    /**
     * 修改单据类型
     *
     * @param config 单据类型信息
     * @return 结果
     */
    public int updateDocType(DocTypeConfig config);

    /**
     * 删除单据类型
     *
     * @param docType 单据类型
     * @return 结果
     */
    public int deleteDocTypeByCode(@Param("docType") String docType);

    /**
     * 校验单据类型是否唯一
     *
     * @param docType 单据类型
     * @return 结果
     */
    public int checkDocTypeUnique(@Param("docType") String docType);
}
