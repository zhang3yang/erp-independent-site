package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.common.config.OssProperties;
import com.ruoyi.system.domain.ShopStorage;

/**
 * 商家对象存储配置。
 *
 * <p>一个商家可以同时存着阿里云、华为云、腾讯云三套配置，其中一套是「当前生效」的。
 * 界面上切换厂商时回显对应那套（{@link #selectForDisplay(String, String)}），
 * 保存时那套就成为当前生效的。上传和列文件一律用当前生效的那套。</p>
 */
public interface IShopStorageService
{
    /**
     * 当前生效的那套配置，<b>密钥字段已抹掉</b>，可以直接下发给前端。
     *
     * @return 一套都没配过时返回 null
     */
    ShopStorage selectForDisplay(String shopCode);

    /**
     * 指定厂商的那套配置，密钥同样已抹掉。界面上切换厂商时用它回显。
     *
     * @return 这个厂商没配过时返回 null，前端据此清空表单
     */
    ShopStorage selectForDisplay(String shopCode, String provider);

    /** 商家配过哪几家，界面上给已配置的厂商打标记 */
    List<String> configuredProviders(String shopCode);

    /**
     * 保存并把这一套设为当前生效。
     *
     * <p>{@code accessKeySecret} 留空表示不改该厂商已存的密钥；该厂商从没配过时留空会报错。</p>
     */
    int save(String shopCode, ShopStorage storage, String operator);

    int remove(String shopCode);

    /**
     * 取商家当前生效的上传目标，密钥已解密。
     *
     * <p>一套都没配、当前那套被停用、或者密文解不开时一律抛 {@code ServiceException}——
     * 按产品决定，平台不替商家兜底存文件，宁可让上传失败也不把文件落到平台自己的桶里。</p>
     */
    OssProperties.ProviderConfig requireUploadTarget(String shopCode);

    /** 当前生效的厂商标识（aliyun/huawei/tencent），落库时记下文件存在哪家 */
    String requireProvider(String shopCode);

    /**
     * 当前生效的厂商标识，没配过返回 null。
     *
     * <p>和 {@link #requireProvider} 的区别是不抛异常：查列表时商家没配存储是正常情况，
     * 不该因此报错，返回 null 让调用方自己决定怎么处理。</p>
     */
    String currentProvider(String shopCode);
}
