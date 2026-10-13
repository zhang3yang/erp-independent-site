package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品位置实体类
 * 对应表：sdsshop.u_m_goods_position
 *
 * <p>记录一个商品在某个仓库里的存放位置，由库管员自己用文字描述（几号库区、几排几架几层……），
 * 系统不解析这段文字，只原样保存和展示。</p>
 *
 * <p>商品名称、规格型号、计量单位、仓库名称都是新增时按编码取的快照，
 * 只为列表和导出好看，业务上认的是 goodsCode + wareCode。</p>
 */
public class InventoryGoodsPosition implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 位置ID */
    private Long positionId;

    /** 商家编码 */
    private String shopCode;

    /** 商品代码 */
    @NotBlank(message = "商品代码不能为空")
    @Size(max = 50, message = "商品代码不能超过50个字符")
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 商品规格 */
    private String spec;

    /** 商品单位 */
    private String unit;

    /** 仓库代码 */
    @NotBlank(message = "仓库代码不能为空")
    @Size(max = 50, message = "仓库代码不能超过50个字符")
    private String wareCode;

    /** 仓库名称 */
    private String wareName;

    /** 存放位置，库管员填写的大文本 */
    @NotBlank(message = "存放位置不能为空")
    @Size(max = 2000, message = "存放位置不能超过2000个字符")
    private String position;

    /** 创建人 */
    private String createUser;

    /** 创建时间 */
    private Date createTime;

    /** 修改时间，列表展示的就是它 */
    private Date updateTime;

    public Long getPositionId()
    {
        return positionId;
    }

    public void setPositionId(Long positionId)
    {
        this.positionId = positionId;
    }

    public String getShopCode()
    {
        return shopCode;
    }

    public void setShopCode(String shopCode)
    {
        this.shopCode = shopCode;
    }

    public String getGoodsCode()
    {
        return goodsCode;
    }

    public void setGoodsCode(String goodsCode)
    {
        this.goodsCode = goodsCode;
    }

    public String getGoodsName()
    {
        return goodsName;
    }

    public void setGoodsName(String goodsName)
    {
        this.goodsName = goodsName;
    }

    public String getSpec()
    {
        return spec;
    }

    public void setSpec(String spec)
    {
        this.spec = spec;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public String getWareCode()
    {
        return wareCode;
    }

    public void setWareCode(String wareCode)
    {
        this.wareCode = wareCode;
    }

    public String getWareName()
    {
        return wareName;
    }

    public void setWareName(String wareName)
    {
        this.wareName = wareName;
    }

    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    public String getCreateUser()
    {
        return createUser;
    }

    public void setCreateUser(String createUser)
    {
        this.createUser = createUser;
    }

    /** 创建人姓名（非数据库字段，查询时按登录名关联 sys_user.nick_name 带出，供前端展示） */
    private String createUserName;

    public String getCreateUserName() { return createUserName; }
    public void setCreateUserName(String createUserName) { this.createUserName = createUserName; }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
