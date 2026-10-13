package com.ruoyi.system.domain.model;

import jakarta.validation.constraints.Size;

/** 预警人工处理请求。 */
public class InventoryWarningHandleBody
{
    @Size(max = 500, message = "处理备注不能超过500个字符")
    private String remark;

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
