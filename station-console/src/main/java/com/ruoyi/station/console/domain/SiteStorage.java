package com.ruoyi.station.console.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 本站的存储配置。对应表 {@code u_s_storage}。
 *
 * <p>替代 ERP 的 {@code sdssys.s_m_shop_storage}（docs/独立站与ERP的边界.md §5.2）。</p>
 *
 * <h3>⚠ 那张表为什么不能跟着搬</h3>
 * <p>{@code s_m_shop_storage} <b>不在 {@code sdsstation}，在 {@code sdssys}</b>，
 * 而且 ERP 自己的文件上传也在用它（{@code ruoyi-common} 的 {@code OssStorageService} /
 * {@code OssProperties}）。它留在 ERP 原地不动，站点自己新建这一份。
 * 它之所以看着像独立站的表，只是因为 mapper 文件被放进了 {@code mapper/station/} 目录。</p>
 *
 * <h3>一期只有 local</h3>
 * <p>文件落本机磁盘，由 {@code file.<主域名>} 直接对外（§10.3.8）。ERP 那套三厂商 SDK
 * 是为「一个平台服务 N 个商家、每家自填密钥」设计的；这里一台机器一个站，
 * 图片就在本机磁盘上，nginx 直接发。</p>
 *
 * <p>结构照抄 ERP 那张表是为了将来真要接对象存储时不用改表——补一个实现即可。
 * {@link #accessKeySecret} 仍然是 AES-GCM 密文，用 {@code AesUtils}，
 * <b>不要再发明第二种加密方式</b>。</p>
 */
public class SiteStorage implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 本机磁盘。不配存储时的默认值，也是最省事的那个 */
    public static final String PROVIDER_LOCAL = "local";

    /**
     * 腾讯云 COS。
     *
     * <p>{@code provider} 列的注释里还写着 {@code aliyun} 和 {@code huawei}——
     * 那是照抄 ERP {@code s_m_shop_storage} 的结构留下的，<b>没有实现</b>。
     * 选了它们会在上传时被明确拒绝，而不是静默走本机磁盘：
     * 静默降级的后果是文件写在本机、URL 却指向没配好的桶，每张图都是死链。</p>
     */
    public static final String PROVIDER_TENCENT = "tencent";

    private String provider;
    private String endpoint;
    private String region;
    private String accessKeyId;
    /**
     * AES-GCM 密文。
     *
     * <p><b>只进不出</b>：前端填的新密钥要能反序列化进来，但这个字段永远不出现在任何响应里。
     * ERP 那版是靠 Service 层每次手工置 null 来防下发的（{@code selectForDisplay}），
     * 只要有人新加一条查询路径就会漏；放在字段上是一道更难绕过的闸。
     * 前端要判「配没配过」，看 {@link #secretConfigured}。</p>
     *
     * <h3>⚠ 这里不能用 {@code @JsonIgnore}</h3>
     * <p>字段上的 {@code @JsonIgnore} 会把整个属性（字段 + getter + setter）一起屏蔽，
     * <b>序列化和反序列化都不走</b>。它曾经就是那么写的，于是前端填的 SecretKey 在
     * {@code @RequestBody} 那一步就被丢掉，{@code SiteStorageService.save} 拿到 null，
     * 当成「本次不改密钥」，mapper 里那个 coalesce 保留了库里原来的空值——
     * 表现是「保存成功了，但密钥一直没存进去」：{@code secretConfigured} 恒为 false，
     * 页面上那条「还没有配置对象存储」的提示怎么保存都消不掉。</p>
     *
     * <p>{@code Access.WRITE_ONLY} 才是这里要的语义：写得进来，读不出去。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String accessKeySecret;
    private String bucket;
    private String publicUrl;
    private String status;
    /**
     * 是否当前生效：1 是 0 否。只有一行为 1。
     *
     * <h3>⚠ 不要给它加一个 {@code isActive()} 便利方法</h3>
     * <p>这里曾经有一个 {@code public boolean isActive() { return "1".equals(active); }}。
     * 它和 {@code getActive()} 在 JavaBeans 眼里是<b>同一个属性 active 的两个 getter</b>，
     * 返回类型却一个 String 一个 boolean。MyBatis 的 {@code Reflector} 解析到这里直接抛
     * {@code Illegal overloaded getter method with ambiguous type for property 'active'}，
     * 于是 {@code SiteStorageMapper.upsert} 里那个 {@code #{active}} 一取值就炸——
     * <b>整张表一行都写不进去</b>，而表现只是管理台「存储设置」保存时一句
     * 「系统异常，请稍后重试」，堆栈里也完全看不出和这个方法有关。</p>
     *
     * <p>要判「生效没有」，在调用处写 {@code "1".equals(storage.getActive())}。</p>
     */
    private String active;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private String createBy;
    private String updateBy;

    /** 给前端看的：密钥配过没有。密钥本身永远不下发 */
    public boolean isSecretConfigured()
    {
        return accessKeySecret != null && !accessKeySecret.isBlank();
    }

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

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
}
