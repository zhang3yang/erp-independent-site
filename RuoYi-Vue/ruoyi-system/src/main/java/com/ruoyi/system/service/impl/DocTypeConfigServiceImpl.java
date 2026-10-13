package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.mapper.DocTypeConfigMapper;
import com.ruoyi.system.service.IDocTypeConfigService;

/**
 * 单据类型配置Service业务层处理
 */
@Service
public class DocTypeConfigServiceImpl implements IDocTypeConfigService
{
    @Autowired
    private DocTypeConfigMapper docTypeConfigMapper;

    @Override
    public DocTypeConfig selectDocTypeByCode(String docType)
    {
        return docTypeConfigMapper.selectDocTypeByCode(docType);
    }

    @Override
    public List<DocTypeConfig> selectDocTypeList(DocTypeConfig config)
    {
        return docTypeConfigMapper.selectDocTypeList(config);
    }

    @Override
    public int insertDocType(DocTypeConfig config)
    {
        if (!checkDocTypeUnique(config.getDocType()))
        {
            throw new ServiceException("单据类型'" + config.getDocType() + "'已存在");
        }

        // 设置默认值
        if (config.getNeedConfirmFlag() == null)
        {
            config.setNeedConfirmFlag(1);
        }
        if (config.getAllowNegativeFlag() == null)
        {
            config.setAllowNegativeFlag(0);
        }

        return docTypeConfigMapper.insertDocType(config);
    }

    @Override
    public int updateDocType(DocTypeConfig config)
    {
        DocTypeConfig existing = docTypeConfigMapper.selectDocTypeByCode(config.getDocType());
        if (existing == null)
        {
            throw new ServiceException("单据类型不存在");
        }

        return docTypeConfigMapper.updateDocType(config);
    }

    @Override
    public int deleteDocTypeByCode(String docType)
    {
        // TODO: 校验单据类型是否被单据引用
        return docTypeConfigMapper.deleteDocTypeByCode(docType);
    }

    @Override
    public boolean checkDocTypeUnique(String docType)
    {
        int count = docTypeConfigMapper.checkDocTypeUnique(docType);
        return count == 0;
    }
}
