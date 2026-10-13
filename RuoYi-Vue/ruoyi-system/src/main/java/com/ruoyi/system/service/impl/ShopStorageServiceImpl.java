package com.ruoyi.system.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.OssProperties;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.OssStorageService;
import com.ruoyi.common.utils.sign.AesUtils;
import com.ruoyi.system.domain.ShopStorage;
import com.ruoyi.system.mapper.ShopStorageMapper;
import com.ruoyi.system.service.IShopStorageService;

/**
 * 商家对象存储配置实现。
 *
 * <p>这个类是密钥的唯一出入口：加密只在 {@link #save} 里发生，解密只在
 * {@link #requireUploadTarget} 里发生。别处要用商家的存储配置，走这两个方法，
 * 不要自己去 mapper 取——那样迟早会有人把密文当明文传给 SDK。</p>
 */
@Service
public class ShopStorageServiceImpl implements IShopStorageService
{
    private static final Logger log = LoggerFactory.getLogger(ShopStorageServiceImpl.class);

    private static final List<String> PROVIDERS = Arrays.asList(
            OssProperties.ALIYUN, OssProperties.HUAWEI, OssProperties.TENCENT);

    /**
     * application.yml 里那个开发用的默认主密钥。
     *
     * <p>放在这里只为了启动时能认出「生产还在用默认值」并告警。它本来就在仓库里，
     * 再抄一份不降低任何安全性——真正的安全边界是生产有没有用环境变量覆盖它。</p>
     */
    private static final String DEV_DEFAULT_SECRET = "b4LDUWf/u+H6hVBTc+qHCdRfMdK/nMvtJux0i0A5ipClTqf9";

    @Autowired
    private ShopStorageMapper storageMapper;

    @Autowired
    private OssProperties ossProperties;

    /** 只用它的 verifyWritable：保存配置前探一次桶到底能不能写 */
    @Autowired
    private OssStorageService ossStorageService;

    @Override
    public ShopStorage selectForDisplay(String shopCode)
    {
        return forDisplay(storageMapper.selectActive(requireShop(shopCode)));
    }

    @Override
    public ShopStorage selectForDisplay(String shopCode, String provider)
    {
        String value = trimToNull(provider);
        if (value == null)
        {
            return selectForDisplay(shopCode);
        }
        return forDisplay(storageMapper.selectByProvider(requireShop(shopCode),
                value.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<String> configuredProviders(String shopCode)
    {
        return storageMapper.selectConfiguredProviders(requireShop(shopCode));
    }

    /** 下发前统一处理：密钥不出库，只留一个「配过没有」的标记 */
    private ShopStorage forDisplay(ShopStorage storage)
    {
        if (storage == null)
        {
            return null;
        }
        storage.setSecretConfigured(notBlank(storage.getAccessKeySecret()));
        storage.setAccessKeySecret(null);
        return storage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int save(String shopCode, ShopStorage storage, String operator)
    {
        requireShop(shopCode);
        if (storage == null)
        {
            throw new ServiceException("配置不能为空");
        }
        normalize(storage);
        validate(storage);

        storage.setShopCode(shopCode);
        storage.setUpdateBy(operator);
        storage.setActive("1");

        // 按 (商家, 厂商) 定位，不是按商家：换厂商是新增/更新另一行，
        // 原来那家的配置留在库里，切回去还能用，不用重填一遍
        ShopStorage current = storageMapper.selectByProvider(shopCode, storage.getProvider());
        String plainSecret = storage.getAccessKeySecret();
        if (current == null && !notBlank(plainSecret))
        {
            throw new ServiceException("请填写 AccessKeySecret");
        }
        // 密钥留空 = 沿用这个厂商已存的那份。探测要用明文，先解出来
        String effectiveSecret = notBlank(plainSecret)
                ? plainSecret : decrypt(current.getAccessKeySecret(), shopCode);

        // 存之前先真写一次。不验的话，桶名或密钥填错要等到用户传第一个文件才发现，
        // 那时报的还是一句笼统的「上传失败」，跟哪一栏填错完全对不上号。
        verifyWritable(storage, effectiveSecret);

        int rows;
        if (current == null)
        {
            storage.setAccessKeySecret(encrypt(plainSecret));
            storage.setCreateBy(operator);
            rows = storageMapper.insertStorage(storage);
        }
        else if (!notBlank(plainSecret))
        {
            // 留空 = 不改密钥。走不碰那一列的更新语句
            rows = storageMapper.updateStorageKeepSecret(storage);
        }
        else
        {
            storage.setAccessKeySecret(encrypt(plainSecret));
            rows = storageMapper.updateStorage(storage);
        }

        // 保存哪套就用哪套。两条语句在同一个事务里，中间不会出现「一个 active 都没有」
        storageMapper.clearActive(shopCode);
        storageMapper.markActive(shopCode, storage.getProvider());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int remove(String shopCode)
    {
        return storageMapper.deleteByShop(requireShop(shopCode));
    }

    @Override
    public OssProperties.ProviderConfig requireUploadTarget(String shopCode)
    {
        ShopStorage storage = storageMapper.selectActive(requireShop(shopCode));
        if (storage == null)
        {
            throw new ServiceException("请先在「文件资源 → 存储设置」里配置对象存储，再上传文件");
        }
        if (!storage.isEnabled())
        {
            throw new ServiceException("对象存储配置已停用，请先启用后再上传");
        }

        OssProperties.ProviderConfig config = new OssProperties.ProviderConfig();
        config.setEndpoint(resolveEndpoint(storage));
        config.setRegion(storage.getRegion());
        config.setAccessKeyId(storage.getAccessKeyId());
        config.setAccessKeySecret(decrypt(storage.getAccessKeySecret(), shopCode));
        config.setBucket(storage.getBucket());
        config.setPublicUrl(storage.getPublicUrl());

        if (!config.isConfigured())
        {
            throw new ServiceException("对象存储配置不完整，请检查后重新保存");
        }
        return config;
    }

    @Override
    public String requireProvider(String shopCode)
    {
        ShopStorage storage = storageMapper.selectActive(requireShop(shopCode));
        if (storage == null)
        {
            throw new ServiceException("请先在「文件资源 → 存储设置」里配置对象存储，再上传文件");
        }
        return storage.getProvider();
    }

    @Override
    public String currentProvider(String shopCode)
    {
        ShopStorage storage = storageMapper.selectActive(requireShop(shopCode));
        return storage == null ? null : storage.getProvider();
    }

    /**
     * 用待保存的这套配置往桶里真写一次再删掉，失败就不让保存。
     *
     * <p>停用状态的配置不探测：商家可能就是因为密钥过期了才来把它停用的，
     * 这时候还拦着不让保存就成了死结。</p>
     */
    private void verifyWritable(ShopStorage storage, String plainSecret)
    {
        if (!storage.isEnabled())
        {
            return;
        }
        OssProperties.ProviderConfig config = new OssProperties.ProviderConfig();
        config.setEndpoint(resolveEndpoint(storage));
        config.setRegion(storage.getRegion());
        config.setAccessKeyId(storage.getAccessKeyId());
        config.setAccessKeySecret(plainSecret);
        config.setBucket(storage.getBucket());
        config.setPublicUrl(storage.getPublicUrl());
        ossStorageService.verifyWritable(storage.getProvider(), config);
    }

    /** 腾讯云只要 region 就够，访问域名由它推导；另外两家必须自己填 endpoint */
    private String resolveEndpoint(ShopStorage storage)
    {
        if (notBlank(storage.getEndpoint()))
        {
            return storage.getEndpoint().trim();
        }
        if (OssProperties.TENCENT.equalsIgnoreCase(storage.getProvider()) && notBlank(storage.getRegion()))
        {
            return "cos." + storage.getRegion().trim() + ".myqcloud.com";
        }
        return null;
    }

    private void normalize(ShopStorage storage)
    {
        storage.setProvider(trimToNull(storage.getProvider()));
        if (storage.getProvider() != null)
        {
            storage.setProvider(storage.getProvider().toLowerCase(Locale.ROOT));
        }
        storage.setEndpoint(trimToNull(storage.getEndpoint()));
        storage.setRegion(trimToNull(storage.getRegion()));
        storage.setAccessKeyId(trimToNull(storage.getAccessKeyId()));
        storage.setAccessKeySecret(trimToNull(storage.getAccessKeySecret()));
        storage.setBucket(trimToNull(storage.getBucket()));
        storage.setPublicUrl(trimToNull(storage.getPublicUrl()));
        storage.setStatus(ShopStorage.STATUS_DISABLED.equals(storage.getStatus())
                ? ShopStorage.STATUS_DISABLED : ShopStorage.STATUS_ENABLED);
    }

    private void validate(ShopStorage storage)
    {
        if (!PROVIDERS.contains(storage.getProvider()))
        {
            throw new ServiceException("对象存储厂商只能是：阿里云(aliyun)、华为云(huawei)、腾讯云(tencent)");
        }
        if (!notBlank(storage.getAccessKeyId()))
        {
            throw new ServiceException("请填写 AccessKeyId");
        }
        if (!notBlank(storage.getBucket()))
        {
            throw new ServiceException("请填写存储桶名称");
        }
        if (OssProperties.TENCENT.equals(storage.getProvider()))
        {
            if (!notBlank(storage.getRegion()))
            {
                throw new ServiceException("腾讯云 COS 必须填写地域 Region，如 ap-beijing");
            }
            // 腾讯云控制台上的桶名自带 APPID。只填前半截的话 SDK 会直接报 NoSuchBucket，
            // 到那时错误信息在日志里，界面上只看到「上传失败」，很难查。在这里拦住。
            if (!storage.getBucket().matches(".+-\\d{5,}"))
            {
                throw new ServiceException("腾讯云的桶名必须带 APPID，形如 name-1300000000，请从控制台完整复制");
            }
        }
        else if (!notBlank(storage.getEndpoint()))
        {
            throw new ServiceException("请填写 Endpoint 访问域名");
        }
        if (notBlank(storage.getPublicUrl()) && !OssProperties.isHttpUrl(storage.getPublicUrl()))
        {
            throw new ServiceException("自定义域名必须以 http:// 或 https:// 开头");
        }
    }

    private String encrypt(String plain)
    {
        return AesUtils.encrypt(plain, requireConfigSecret());
    }

    private String decrypt(String cipher, String shopCode)
    {
        try
        {
            return AesUtils.decrypt(cipher, requireConfigSecret());
        }
        catch (RuntimeException e)
        {
            // 主密钥换过、或者有人直接往库里塞了明文。不把底层异常抛给用户看
            log.error("商家 {} 的对象存储密钥解密失败，需重新填写密钥", shopCode, e);
            throw new ServiceException("对象存储密钥无法解密，请重新填写后保存");
        }
    }

    /**
     * 启动时检查主密钥。
     *
     * <p>用仓库里那个默认值跑生产，等于所有商家的密钥都用一把公开的锁锁着。
     * 这种问题不会有任何运行时表现——功能一切正常，直到有人翻到这个仓库为止，
     * 所以只能在启动日志里喊一嗓子。</p>
     */
    @PostConstruct
    public void reportConfiguration()
    {
        String secret = ossProperties.getConfigSecret();
        if (!notBlank(secret))
        {
            log.error("对象存储密钥加密：未配置 ruoyi.oss.config-secret，商家将无法保存自己的存储配置");
            return;
        }
        if (DEV_DEFAULT_SECRET.equals(secret))
        {
            log.warn("对象存储密钥加密：正在使用仓库里的默认主密钥，仅供本地开发。"
                    + "生产环境请用环境变量 OSS_CONFIG_SECRET 覆盖，"
                    + "并且要在第一个商家配置存储之前设好——改了之后已存的密钥全部需要重填");
        }
    }

    /**
     * 加密商家密钥用的主密钥。
     *
     * <p>没配（连默认值都被清空了）就直接拒绝而不是用空串加密：
     * 空口令派生出来的密钥是固定的，那比公开的锁还糟。</p>
     */
    private String requireConfigSecret()
    {
        String secret = ossProperties.getConfigSecret();
        if (!notBlank(secret))
        {
            throw new ServiceException("平台未配置密钥加密主密钥（ruoyi.oss.config-secret），请联系管理员");
        }
        return secret;
    }

    private String requireShop(String shopCode)
    {
        String value = trimToNull(shopCode);
        if (value == null)
        {
            throw new ServiceException("缺少商家上下文");
        }
        return value;
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
