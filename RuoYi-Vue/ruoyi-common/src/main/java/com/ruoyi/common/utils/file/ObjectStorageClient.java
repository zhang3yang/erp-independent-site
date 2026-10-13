package com.ruoyi.common.utils.file;

import com.ruoyi.common.config.OssProperties;

/** 对象存储厂商适配接口。新增厂商只需实现本接口并注册为 Spring Bean。 */
public interface ObjectStorageClient
{
    /** 厂商标识，与 ruoyi.oss.provider 取值一致。 */
    String provider();

    /** 上传对象到桶内指定路径。 */
    void put(OssProperties.ProviderConfig config, String objectName, byte[] bytes, String contentType);

    /**
     * 删除桶内对象。
     *
     * <p>目前只用在保存存储配置时的连通性探测上（传一个探测对象再删掉）。</p>
     */
    void delete(OssProperties.ProviderConfig config, String objectName);

    /** 服务端等比缩略图的 URL 查询串，各厂商图片处理参数不同。 */
    String thumbnailQuery(int width, int height);
}
