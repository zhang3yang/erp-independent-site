package com.ruoyi.system.domain;

import java.io.Serializable;

/**
 * 单据类型配置实体类
 * 对应表：sdsshop.u_m_doctypeconfig
 */
public class DocTypeConfig implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 单据类型 */
    private String docType;

    /** 单据类型名称 */
    private String docTypeName;

    /** 方向：I-入库，O-出库，N-调拨 */
    private String direction;

    /** 是否需要审核 */
    private Integer needConfirmFlag;

    /** 是否允许负库存 */
    private Integer allowNegativeFlag;

    /** 备注 */
    private String remark;

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDocTypeName() { return docTypeName; }
    public void setDocTypeName(String docTypeName) { this.docTypeName = docTypeName; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getNeedConfirmFlag() { return needConfirmFlag; }
    public void setNeedConfirmFlag(Integer needConfirmFlag) { this.needConfirmFlag = needConfirmFlag; }

    public Integer getAllowNegativeFlag() { return allowNegativeFlag; }
    public void setAllowNegativeFlag(Integer allowNegativeFlag) { this.allowNegativeFlag = allowNegativeFlag; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
