package com.ruoyi.web.controller.inventory;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.DocTypeConfig;
import com.ruoyi.system.service.IDocTypeConfigService;

/**
 * 单据类型配置Controller
 */
@RestController
@RequestMapping("/inventory/doctypes")
public class DocTypeConfigController extends BaseController
{
    @Autowired
    private IDocTypeConfigService docTypeConfigService;

    /**
     * 查询单据类型列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(DocTypeConfig config)
    {
        startPage();
        List<DocTypeConfig> list = docTypeConfigService.selectDocTypeList(config);
        return getDataTable(list);
    }

    /**
     * 获取单据类型详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{docType}")
    public AjaxResult getInfo(@PathVariable("docType") String docType)
    {
        DocTypeConfig config = docTypeConfigService.selectDocTypeByCode(docType);
        if (config == null)
        {
            return error("单据类型不存在");
        }
        return success(config);
    }

    /**
     * 新增单据类型
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "单据类型配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DocTypeConfig config)
    {
        return toAjax(docTypeConfigService.insertDocType(config));
    }

    /**
     * 修改单据类型
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "单据类型配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DocTypeConfig config)
    {
        return toAjax(docTypeConfigService.updateDocType(config));
    }

    /**
     * 删除单据类型
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "单据类型配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{docType}")
    public AjaxResult remove(@PathVariable("docType") String docType)
    {
        return toAjax(docTypeConfigService.deleteDocTypeByCode(docType));
    }
}

