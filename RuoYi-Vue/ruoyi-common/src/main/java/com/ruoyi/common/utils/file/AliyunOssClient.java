package com.ruoyi.common.utils.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.ObjectMetadata;
import com.ruoyi.common.config.OssProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** 阿里云 OSS 适配实现。 */
@Component
public class AliyunOssClient implements ObjectStorageClient
{
    @Override
    public String provider()
    {
        return OssProperties.ALIYUN;
    }

    @Override
    public void put(OssProperties.ProviderConfig config, String objectName, byte[] bytes, String contentType)
    {
        OSS client = new OSSClient(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
        try
        {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            if (contentType != null && !contentType.isBlank())
            {
                metadata.setContentType(contentType);
            }
            client.putObject(config.getBucket(), objectName, new ByteArrayInputStream(bytes), metadata);
        }
        finally
        {
            client.shutdown();
        }
    }

    @Override
    public void delete(OssProperties.ProviderConfig config, String objectName)
    {
        OSS client = new OSSClient(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
        try
        {
            client.deleteObject(config.getBucket(), objectName);
        }
        finally
        {
            client.shutdown();
        }
    }

    @Override
    public String thumbnailQuery(int width, int height)
    {
        return "?x-oss-process=image/resize,m_lfit,w_" + width + ",h_" + height;
    }
}
