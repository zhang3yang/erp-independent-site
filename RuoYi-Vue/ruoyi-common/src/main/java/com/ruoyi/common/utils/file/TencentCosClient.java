package com.ruoyi.common.utils.file;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.ruoyi.common.config.OssProperties;
import com.ruoyi.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯云 COS 适配实现。
 * 注意桶名必须是控制台上带 APPID 的完整名称（形如 name-1300000000）。
 * COSClient 内部持有连接池，按「region + SecretId」缓存单实例，配置变更时重建。
 */
@Component
public class TencentCosClient implements ObjectStorageClient
{
    private static final Pattern ENDPOINT_REGION = Pattern.compile("cos\\.([a-z0-9-]+)\\.myqcloud\\.com");

    private volatile COSClient cached;
    private volatile String cachedKey;

    @Override
    public String provider()
    {
        return OssProperties.TENCENT;
    }

    @Override
    public void put(OssProperties.ProviderConfig config, String objectName, byte[] bytes, String contentType)
    {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (contentType != null && !contentType.isBlank())
        {
            metadata.setContentType(contentType);
        }
        PutObjectRequest request = new PutObjectRequest(config.getBucket(), objectName,
                new ByteArrayInputStream(bytes), metadata);
        client(config).putObject(request);
    }

    @Override
    public void delete(OssProperties.ProviderConfig config, String objectName)
    {
        client(config).deleteObject(config.getBucket(), objectName);
    }

    /** COS 图片处理由数据万象提供，thumbnail 为等比内切缩放，语义等同 OSS 的 m_lfit。 */
    @Override
    public String thumbnailQuery(int width, int height)
    {
        return "?imageMogr2/thumbnail/" + width + "x" + height;
    }

    private COSClient client(OssProperties.ProviderConfig config)
    {
        String region = region(config);
        String key = region + "|" + config.getAccessKeyId();
        COSClient current = cached;
        if (current != null && key.equals(cachedKey))
        {
            return current;
        }
        synchronized (this)
        {
            if (cached != null && key.equals(cachedKey))
            {
                return cached;
            }
            shutdownQuietly(cached);
            COSCredentials credentials = new BasicCOSCredentials(config.getAccessKeyId(),
                    config.getAccessKeySecret());
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            clientConfig.setHttpProtocol(HttpProtocol.https);
            cached = new COSClient(credentials, clientConfig);
            cachedKey = key;
            return cached;
        }
    }

    /** region 未显式配置时，从访问域名 cos.<region>.myqcloud.com 中解析。 */
    private String region(OssProperties.ProviderConfig config)
    {
        String region = config.getRegion();
        if (region != null && !region.isBlank())
        {
            return region.trim();
        }
        String endpoint = config.getEndpoint();
        if (endpoint != null)
        {
            Matcher matcher = ENDPOINT_REGION.matcher(endpoint);
            if (matcher.find())
            {
                return matcher.group(1);
            }
        }
        throw new ServiceException("腾讯云 COS 缺少 region 配置");
    }

    @PreDestroy
    public void destroy()
    {
        shutdownQuietly(cached);
        cached = null;
        cachedKey = null;
    }

    private void shutdownQuietly(COSClient client)
    {
        if (client != null)
        {
            client.shutdown();
        }
    }
}
