package com.ruoyi.system.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Platform request for creating or editing a shop.
 */
public class TenantShopBody
{
    @Size(max = 50, message = "Shop code cannot exceed 50 characters")
    private String shopCode;

    @NotBlank(message = "Shop name is required")
    @Size(max = 50, message = "Shop name cannot exceed 50 characters")
    private String shopName;

    @Size(max = 50) private String province;
    @Size(max = 50) private String city;
    @Size(max = 50) private String district;
    @Size(max = 100) private String address;
    @Size(max = 50) private String telephone;
    @Size(max = 200) private String description;
    @Size(max = 100) private String imageUrl;
    /** 这个商家专用的独立站管理台地址。只允许 http/https，见 TenantPlatformServiceImpl 的写入校验 */
    @Size(max = 500, message = "Console URL cannot exceed 500 characters") private String consoleUrl;
    private Integer status;

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getConsoleUrl() { return consoleUrl; }
    public void setConsoleUrl(String consoleUrl) { this.consoleUrl = consoleUrl; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
