package com.ruoyi.common.utils.file;

import com.ruoyi.common.config.OssProperties;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一对象存储服务。
 *
 * <h3>两条互不相干的上传链路</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>配置来源</th><th>谁在用</th></tr>
 *   <tr>
 *     <td>{@link #uploadImage}、{@link #uploadFile}</td>
 *     <td>配置文件 {@code ruoyi.oss}（平台的阿里云）</td>
 *     <td><b>ERP 自己</b>：头像、商品图、商家 Logo、通用上传</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #uploadTo}</td>
 *     <td>数据库 {@code s_m_shop_storage}（商家自填，三家任选）</td>
 *     <td><b>独立站维护平台</b>：文件资源里的图片和视频</td>
 *   </tr>
 * </table>
 *
 * <p><b>这两条路是隔离的，不要混用。</b>独立站的文件必须落到商家自己的桶里——
 * 那是商家自己付费的存储，也是他自己的数据；ERP 的文件落平台的桶。
 * 在 station 模块里调 uploadImage/uploadFile 就是把商家的素材传进了平台的桶，
 * 编译和运行都不会报错，只有翻账单的时候才发现。</p>
 *
 * <p>平台侧未启用（{@code enabled=false} 或配置不全）时前两个方法返回 null，
 * 由调用方回退到本地上传；{@link #uploadTo} 没有这种回退——商家没配存储就该失败，
 * 见 {@code ShopStorageServiceImpl.requireUploadTarget}。</p>
 */
@Service
public class OssStorageService
{
    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private static final int THUMBNAIL_WIDTH = 150;
    private static final int THUMBNAIL_HEIGHT = 150;

    private final OssProperties properties;
    private final RedisService redisService;
    private final Map<String, ObjectStorageClient> clients = new HashMap<>();

    public OssStorageService(OssProperties properties, RedisService redisService,
            List<ObjectStorageClient> storageClients)
    {
        this.properties = properties;
        this.redisService = redisService;
        for (ObjectStorageClient client : storageClients)
        {
            this.clients.put(client.provider().toLowerCase(Locale.ROOT), client);
        }
    }

    public boolean isEnabled()
    {
        return properties.isEnabled() && lookup() != null;
    }

    public OssUploadResult uploadImage(MultipartFile file, String folder) throws IOException
    {
        if (!isEnabled())
        {
            return null;
        }
        if (file == null || file.isEmpty())
        {
            throw new IOException("上传图片不能为空");
        }
        ObjectStorageClient client = client();
        String objectName = nextObjectName(file.getOriginalFilename());
        client.put(properties.active(), objectName, file.getBytes(), file.getContentType());
        String url = toUrl(objectName);
        return new OssUploadResult(url, url + client.thumbnailQuery(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException
    {
        if (!isEnabled())
        {
            return null;
        }
        String objectName = nextObjectName(file.getOriginalFilename());
        client().put(properties.active(), objectName, file.getBytes(), file.getContentType());
        return toUrl(objectName);
    }

    /**
     * 上传到指定的存储目标，而不是平台配置的那个。
     *
     * <p>给「商家自带对象存储」用：商家在管理台里填了自己的密钥，文件要落到他自己的桶里。
     * 上面那两个方法读 {@code properties.active()}（平台配置），这个方法的 provider 和 config
     * 都由调用方给。</p>
     *
     * @param provider 厂商标识，aliyun / huawei / tencent
     * @param config   目标桶的连接配置，密钥必须已经是明文
     * @param folder   桶内一级目录，用来把不同用途的文件分开，null 表示不分目录
     * @return 上传结果，含原图地址和缩略图地址；缩略图对视频没有意义，调用方自行忽略
     */
    public OssUploadResult uploadTo(String provider, OssProperties.ProviderConfig config,
            MultipartFile file, String folder) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new IOException("上传文件不能为空");
        }
        if (config == null || !config.isConfigured())
        {
            throw new ServiceException("对象存储配置不完整");
        }
        ObjectStorageClient client = require(provider);
        String objectName = nextObjectName(file.getOriginalFilename(), folder);
        client.put(config, objectName, file.getBytes(), file.getContentType());
        String url = toUrl(config, objectName);
        return new OssUploadResult(url, url + client.thumbnailQuery(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                objectName);
    }

    /**
     * 探一次这套配置到底能不能用：往桶里写一个几十字节的探测对象，再删掉。
     *
     * <p>不用「桶是否存在」之类的轻量检查——桶存在不等于这把密钥能往里写，
     * 而能不能写正是上传唯一关心的事。真写一次才问得出真答案。</p>
     *
     * <p>厂商的原始报错对使用者没有意义（"AccessDenied because of bucket acl" 这种），
     * 所以这里把常见错误码翻译成「哪一栏填错了」。特别是<b>桶名填成别人已注册的桶</b>时，
     * 三家返回的都是 AccessDenied 而不是 NoSuchBucket——桶名是全局唯一的，
     * 填了个通用词多半已经被人占了，这条提示要专门说清楚。</p>
     *
     * @throws ServiceException 配置不可用，消息里说明是哪一项的问题
     */
    public void verifyWritable(String provider, OssProperties.ProviderConfig config)
    {
        if (config == null || !config.isConfigured())
        {
            throw new ServiceException("配置不完整");
        }
        ObjectStorageClient client = require(provider);
        String objectName = "connectivity-check/" + System.currentTimeMillis() + ".txt";
        byte[] probe = "wczl-erp storage connectivity check".getBytes(StandardCharsets.UTF_8);
        try
        {
            client.put(config, objectName, probe, "text/plain");
        }
        catch (RuntimeException e)
        {
            throw new ServiceException(explain(e));
        }
        try
        {
            client.delete(config, objectName);
        }
        catch (RuntimeException e)
        {
            // 能写不能删：上传功能可以正常用，不该因此拦住保存。
            // 留个日志就好——真正的后果只是桶里多一个几十字节的探测文件。
            log.warn("存储配置探测：写入成功但删除失败，桶内残留 {}", objectName, e);
        }
    }

    /**
     * 把厂商的原始异常翻译成「该改哪一栏」。认不出来的截断后原样返回，总比吞掉强。
     *
     * <p>这里用 {@code toString()} 而不是 {@code getMessage()}：阿里云 OSSException 的
     * getMessage() 只有一句「返回结果无效，无法解析。」，错误码和厂商返回的 XML 在 toString()
     * 里才拼进去。华为、腾讯的异常同理。</p>
     */
    private String explain(RuntimeException e)
    {
        StringBuilder sb = new StringBuilder(e.toString());
        Throwable cause = e.getCause();
        while (cause != null && sb.length() < 4000)
        {
            sb.append(' ').append(cause);
            cause = cause.getCause();
        }
        String text = sb.toString();
        if (text.contains("NoSuchBucket"))
        {
            return "存储桶不存在，请检查桶名称是否填对";
        }
        if (text.contains("AccessDenied") || text.contains("bucket acl"))
        {
            return "这把密钥没有该存储桶的写入权限。两种常见原因："
                    + "① 桶名填成了别人的桶——桶名在厂商内是全局唯一的，通用词多半已被占用，"
                    + "请从控制台复制自己的桶名；② 密钥所属的子账号没有该桶的读写授权";
        }
        if (text.contains("InvalidAccessKeyId") || text.contains("InvalidAccessKeyID"))
        {
            return "AccessKeyId 无效，请检查是否填错或已被禁用";
        }
        if (text.contains("SignatureDoesNotMatch"))
        {
            return "AccessKeySecret 不正确，请重新复制一次";
        }
        if (text.contains("UnknownHost") || text.contains("ConnectException")
                || text.contains("timed out") || text.contains("Connection"))
        {
            return "连不上该地址，请检查 Endpoint / 地域是否填对，以及服务器能否访问外网";
        }
        // 认不出来的错误原样带出去，但要截断：厂商可能把整段 XML 塞在异常里，
        // 弹窗放不下，而且再长也帮不上使用者，真正排查还是得看服务端日志
        return "对象存储拒绝了这次请求：" + (text.length() > 200 ? text.substring(0, 200) + "…" : text);
    }

    private ObjectStorageClient client()
    {
        ObjectStorageClient client = lookup();
        if (client == null)
        {
            throw new ServiceException("未知的对象存储厂商: " + properties.getProvider());
        }
        return client;
    }

    private ObjectStorageClient require(String provider)
    {
        ObjectStorageClient client = provider == null ? null
                : clients.get(provider.trim().toLowerCase(Locale.ROOT));
        if (client == null)
        {
            throw new ServiceException("未知的对象存储厂商: " + provider);
        }
        return client;
    }

    private ObjectStorageClient lookup()
    {
        String provider = properties.getProvider();
        return provider == null ? null : clients.get(provider.trim().toLowerCase(Locale.ROOT));
    }

    private String nextObjectName(String originalFilename)
    {
        return nextObjectName(originalFilename, null);
    }

    /** folder 为空时不分目录，保持和历史上传一致的 {@code 日期/文件名} 结构 */
    private String nextObjectName(String originalFilename, String folder)
    {
        String base = DateUtils.datePath() + "/" + nextImageKey() + "." + extension(originalFilename);
        if (folder == null || folder.isBlank())
        {
            return base;
        }
        return folder.replaceAll("^/+", "").replaceAll("/+$", "") + "/" + base;
    }

    private String toUrl(String objectName)
    {
        return toUrl(properties.active(), objectName);
    }

    private String toUrl(OssProperties.ProviderConfig config, String objectName)
    {
        String publicUrl = config.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank())
        {
            return publicUrl.replaceAll("/$", "") + "/" + objectName;
        }
        String endpoint = config.getEndpoint().replaceFirst("^https?://", "").replaceAll("/$", "");
        return "https://" + config.getBucket() + "." + endpoint + "/" + objectName;
    }

    private String extension(String filename)
    {
        if (filename == null || filename.lastIndexOf('.') < 0)
        {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** 使用 RedisService 的秒级 Base36 主键生成器，统一生成 img 前缀文件名。 */
    private String nextImageKey()
    {
        Map result = redisService.getSecondKey(KeyType.Base36Upper, "img");
        if (!Boolean.TRUE.equals(result.get("result")) || result.get("primaryKey") == null)
        {
            Object error = result.get("errorinfo") != null ? result.get("errorinfo") : result.get("msg");
            throw new ServiceException("OSS 图片名称生成失败" + (error == null ? "" : ": " + error));
        }
        String key = result.get("primaryKey").toString();
        if (!key.matches("^img[0-9A-Z]+$"))
        {
            throw new ServiceException("OSS 图片名称格式异常");
        }
        return key;
    }
}
