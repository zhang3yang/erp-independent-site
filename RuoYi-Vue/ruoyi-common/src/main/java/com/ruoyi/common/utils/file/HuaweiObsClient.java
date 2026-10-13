package com.ruoyi.common.utils.file;

import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.PutObjectRequest;
import com.ruoyi.common.config.OssProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * 华为云 OBS 适配实现。
 * ObsClient 内部持有连接池，官方建议复用，这里按「endpoint + AK」缓存单实例，配置变更时重建。
 */
@Component
public class HuaweiObsClient implements ObjectStorageClient
{
    private static final Logger log = LoggerFactory.getLogger(HuaweiObsClient.class);

    private volatile ObsClient cached;
    private volatile String cachedKey;

    @Override
    public String provider()
    {
        return OssProperties.HUAWEI;
    }

    @Override
    public void put(OssProperties.ProviderConfig config, String objectName, byte[] bytes, String contentType)
    {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength((long) bytes.length);
        if (contentType != null && !contentType.isBlank())
        {
            metadata.setContentType(contentType);
        }
        PutObjectRequest request = new PutObjectRequest(config.getBucket(), objectName,
                new ByteArrayInputStream(bytes));
        request.setMetadata(metadata);
        client(config).putObject(request);
    }

    @Override
    public void delete(OssProperties.ProviderConfig config, String objectName)
    {
        client(config).deleteObject(config.getBucket(), objectName);
    }

    /** OBS 图片处理参数与 OSS 不同，缩放模式需写作 m_lfit。 */
    @Override
    public String thumbnailQuery(int width, int height)
    {
        return "?x-image-process=image/resize,m_lfit,w_" + width + ",h_" + height;
    }

    private ObsClient client(OssProperties.ProviderConfig config)
    {
        String key = config.getEndpoint() + "|" + config.getAccessKeyId();
        ObsClient current = cached;
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
            closeQuietly(cached);
            cached = new ObsClient(config.getAccessKeyId(), config.getAccessKeySecret(), config.getEndpoint());
            cachedKey = key;
            return cached;
        }
    }

    @PreDestroy
    public void destroy()
    {
        closeQuietly(cached);
        cached = null;
        cachedKey = null;
    }

    private void closeQuietly(ObsClient client)
    {
        if (client == null)
        {
            return;
        }
        try
        {
            client.close();
        }
        catch (Exception e)
        {
            log.warn("关闭 OBS 客户端失败", e);
        }
    }
}
