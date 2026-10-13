package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 商家自己的对象存储配置。一个商家一套，名下所有站点共用。
 *
 * <h3>密钥怎么走</h3>
 * <p>{@link #accessKeySecret} 在库里是 AES-GCM 密文，不是明文。三个方向：</p>
 * <ul>
 *   <li><b>入</b>：前端提交明文 → Service 加密 → 落库；</li>
 *   <li><b>用</b>：Service 从库里读出来解密 → 转成 {@code OssProperties.ProviderConfig} 交给上传；</li>
 *   <li><b>出</b>：返回给前端时整个字段置空，只用 {@link #secretConfigured} 告诉界面「已经配过了」。
 *       密钥一旦存进去就不再出现在任何下发到浏览器的地方——界面上改密钥是覆盖写，不是回显后编辑。</li>
 * </ul>
 */
public class ShopStorage implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_ENABLED = "0";
    public static final String STATUS_DISABLED = "1";

    private String shopCode;
    /** aliyun / huawei / tencent，取值与 OssProperties 的常量一致 */
    private String provider;
    private String endpoint;
    /** 腾讯云必填，如 ap-beijing；阿里云、华为云用 endpoint */
    private String region;
    private String accessKeyId;
    /** 库里是密文；下发给前端时一律置空，见类注释 */
    private String accessKeySecret;
    private String bucket;
    private String publicUrl;
    private String status;
    /**
     * 是否当前生效。一个商家可以存着三家的配置，但同一时刻只有一套在用。
     *
     * <p>由 {@code ShopStorageServiceImpl.save} 保证同商家只有一行为 '1'。</p>
     */
    private String active;
    private Date createtime;
    private Date updatetime;
    private String createBy;
    private String updateBy;

    /**
     * 「密钥已配置」标记，不落库。
     *
     * <p>界面需要区分「没配过」和「配过但不给看」这两种状态：前者要提示先去配置，
     * 后者输入框留空即可（留空 = 不改）。光看 accessKeySecret 是否为空分不出来，
     * 因为它下发前已经被抹掉了。</p>
     */
    private boolean secretConfigured;

    public boolean isEnabled()
    {
        return !STATUS_DISABLED.equals(status);
    }

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
    public Date getCreatetime() { return createtime; }
    public void setCreatetime(Date createtime) { this.createtime = createtime; }
    public Date getUpdatetime() { return updatetime; }
    public void setUpdatetime(Date updatetime) { this.updatetime = updatetime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public boolean isSecretConfigured() { return secretConfigured; }
    public void setSecretConfigured(boolean secretConfigured) { this.secretConfigured = secretConfigured; }
}
