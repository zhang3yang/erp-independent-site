package com.ruoyi.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <b>ERP 自己</b>的对象存储配置（头像、商品图、商家 Logo、通用上传），当前固定用阿里云。
 *
 * <p>独立站维护平台的图片/视频不读这个类，读的是每个商家自填的 {@code s_m_shop_storage}
 * 表——那条路见 {@code IShopStorageService}。两边共用 {@link ProviderConfig} 这个结构体
 * 和三个 {@code ObjectStorageClient} 实现（怎么把字节传到某个桶），但配置来源完全分开。</p>
 *
 * <p>{@code provider} 决定 ERP 侧用哪家。改它必须同时补上对应的子节点，
 * 否则 {@link #isEnabled()} 变 false，上传会回退到本地磁盘。</p>
 *
 * <p>密钥建议通过环境变量注入，不要提交到代码仓库。</p>
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.oss")
public class OssProperties
{
    public static final String ALIYUN = "aliyun";
    public static final String HUAWEI = "huawei";
    public static final String TENCENT = "tencent";

    private boolean enabled;

    /** 生效的对象存储厂商：aliyun | huawei */
    private String provider = ALIYUN;

    // 以下顶层字段为历史配置格式，等价于 aliyun 节点。aliyun 子节点未配置时继续生效。
    private String endpoint = "http://oss-cn-beijing.aliyuncs.com";
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket = "wczlerp";
    private String publicUrl;

    /**
     * 加密商家自填的对象存储密钥用的主密钥（见 ShopStorageServiceImpl）。
     *
     * <p>和上面那几组配置无关：那些是平台自己的存储，这一个是「锁商家密钥的钥匙」。
     * 生产环境必须用环境变量注入，没配时商家保存存储配置会直接失败——
     * 这是有意的，不给硬编码兜底，见 ShopStorageServiceImpl.requireConfigSecret。</p>
     *
     * <p><b>换了这个值，所有商家已存的密钥都解不开，必须重新填一次。</b></p>
     */
    private String configSecret;

    private ProviderConfig aliyun = new ProviderConfig();
    private ProviderConfig huawei = new ProviderConfig();
    private ProviderConfig tencent = new ProviderConfig();

    /** 判断一个地址能不能当公开访问域名用。只认 http/https */
    public static boolean isHttpUrl(String url)
    {
        if (url == null)
        {
            return false;
        }
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** 腾讯云 COS 只需配 region，访问域名可由 region 推导。 */
    @PostConstruct
    public void normalize()
    {
        if (!ProviderConfig.notBlank(tencent.getEndpoint()) && ProviderConfig.notBlank(tencent.getRegion()))
        {
            tencent.setEndpoint("cos." + tencent.getRegion().trim() + ".myqcloud.com");
        }
    }

    /** 当前 provider 的有效配置；aliyun 子节点缺省时回退到顶层旧格式配置。 */
    public ProviderConfig active()
    {
        if (HUAWEI.equalsIgnoreCase(provider))
        {
            return huawei;
        }
        if (TENCENT.equalsIgnoreCase(provider))
        {
            return tencent;
        }
        return aliyun.isConfigured() ? aliyun : legacy();
    }

    private ProviderConfig legacy()
    {
        ProviderConfig config = new ProviderConfig();
        config.setEndpoint(endpoint);
        config.setAccessKeyId(accessKeyId);
        config.setAccessKeySecret(accessKeySecret);
        config.setBucket(bucket);
        config.setPublicUrl(publicUrl);
        return config;
    }

    public boolean isEnabled() { return enabled && active().isConfigured(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public ProviderConfig getAliyun() { return aliyun; }
    public void setAliyun(ProviderConfig aliyun) { this.aliyun = aliyun; }
    public ProviderConfig getHuawei() { return huawei; }
    public void setHuawei(ProviderConfig huawei) { this.huawei = huawei; }
    public ProviderConfig getTencent() { return tencent; }
    public void setTencent(ProviderConfig tencent) { this.tencent = tencent; }
    public String getConfigSecret() { return configSecret; }
    public void setConfigSecret(String configSecret) { this.configSecret = configSecret; }

    /** 单个厂商的连接配置。 */
    public static class ProviderConfig
    {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        /** 地域代号，腾讯云 COS 必填（如 ap-beijing）；阿里云、华为云用 endpoint 即可。 */
        private String region;
        /** 使用厂商默认公网域名时留空；使用 CDN/自定义域名时填写完整地址。 */
        private String publicUrl;

        public boolean isConfigured()
        {
            return notBlank(endpoint) && notBlank(accessKeyId) && notBlank(accessKeySecret) && notBlank(bucket);
        }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getPublicUrl() { return publicUrl; }
        public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }

        static boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
    }
}
