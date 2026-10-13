package com.ruoyi.common.utils.file;

/** 对象存储上传结果，包含原文件地址、等比缩略图地址和桶内对象路径。 */
public class OssUploadResult
{
    private final String url;
    private final String thumbUrl;
    /**
     * 桶内对象路径，如 {@code site/20260826/imgABC.jpg}。
     *
     * <p>删除文件时要用它——完整 URL 里可能带自定义域名，反推不回对象路径。
     * 历史调用方不关心这个，走两参数构造即可。</p>
     */
    private final String objectName;

    public OssUploadResult(String url, String thumbUrl)
    {
        this(url, thumbUrl, null);
    }

    public OssUploadResult(String url, String thumbUrl, String objectName)
    {
        this.url = url;
        this.thumbUrl = thumbUrl;
        this.objectName = objectName;
    }

    public String getUrl() { return url; }
    public String getThumbUrl() { return thumbUrl; }
    public String getObjectName() { return objectName; }
}
