package com.ruoyi.station.console.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteImage;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.domain.SiteVideo;
import com.ruoyi.station.console.domain.SiteModel;
import com.ruoyi.station.console.mapper.SiteImageMapper;
import com.ruoyi.station.console.mapper.SiteVideoMapper;
import com.ruoyi.station.console.mapper.SiteModelMapper;
import com.ruoyi.station.console.util.CosClient;

/**
 * 站点文件资源（图片 / 视频）。
 *
 * <h3>⚠ 存储方式变了：对象存储 → 本机磁盘</h3>
 * <p>docs/独立站与ERP的边界.md §10.3.8：<b>文件存储落本地磁盘，
 * 由 {@code file.<主域名>} 直接对外。</b></p>
 *
 * <p>ERP 那版是「一个平台服务 N 个商家，每家在 {@code s_m_shop_storage} 里自填密钥，
 * 上传时按商家取配置、交给三个厂商 SDK 之一」。那套东西搬不过来也不该搬：</p>
 * <ul>
 *   <li>{@code s_m_shop_storage} 在 {@code sdssys}，不在 {@code sdsstation}，
 *       而且 ERP 自己的上传也在用它——它留在 ERP 原地不动（§5.2 的那个警告）；</li>
 *   <li>一台机器一个站，没有「N 个商家各自的桶」这个维度；</li>
 *   <li>三个厂商 SDK 是三份依赖、一套密钥管理、一个主密钥轮换流程，
 *       为一个站的几百张图付这个代价不划算。</li>
 * </ul>
 *
 * <p>{@code u_s_storage} 那张表仍然建着（结构照抄），将来真要接对象存储时补一个实现即可，
 * 不用改表。</p>
 *
 * <h3>2026-09-05：接上了腾讯云 COS</h3>
 * <p>上面那段仍然是这个类的出发点，但它不再是唯一形态了。现在按
 * {@code u_s_storage} 里当前生效那一行的 {@code provider} 分流：</p>
 * <ul>
 *   <li>{@code local}（默认，也是库里一行都没有时的回落）—— 写本机磁盘，
 *       地址用 {@code station.upload.base-url} 拼；</li>
 *   <li>{@code tencent} —— 交给 {@link CosClient}（手写签名，零依赖），
 *       地址用 {@code u_s_storage.public_url}（自定义域名）拼；</li>
 *   <li>{@code aliyun} / {@code huawei} —— 表结构和前端下拉里有，<b>没有实现</b>，
 *       选了会被明确拒绝。不静默降级到本机磁盘：那样文件在本机、URL 指向没配好的桶，
 *       每张图都是死链，还要等到有人打开页面才发现。</li>
 * </ul>
 *
 * <p>{@code u_s_image.provider} / {@code u_s_video.provider} 记的是<b>这个文件当初存到哪去了</b>，
 * 不是「当前用哪家」——换存储之后老文件还在老地方，靠这一列才知道该去哪取。</p>
 *
 * <h3>宽高由前端量</h3>
 * <p>服务端不解码文件：解图片要 ImageIO、解视频要 ffmpeg，为了两个数字不值得，
 * 而浏览器在选文件时本来就要加载一次。量不到就存 null，前端按自然尺寸渲染。</p>
 */
@Service
public class SiteFileService
{
    private static final Logger log = LoggerFactory.getLogger(SiteFileService.class);

    /** 磁盘上的一级目录，把图片和视频分开，运维清理和备份时好下手 */
    private static final String IMAGE_FOLDER = "image";
    private static final String VIDEO_FOLDER = "video";
    private static final String MODEL_FOLDER = "model";
    /** 买家定制方案上传的 logo（2026-09-29）。单独一个目录、不进素材库，见 {@link #storeCustomImage} */
    private static final String CUSTOM_FOLDER = "custom";

    private static final int NAME_MAX_LENGTH = 200;

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Autowired
    private SiteImageMapper imageMapper;

    @Autowired
    private SiteVideoMapper videoMapper;

    @Autowired
    private SiteModelMapper modelMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CodeGenerator codeGenerator;

    @Autowired
    private SiteFolderService folderService;

    @Autowired
    private StationProperties properties;

    @Autowired
    private SiteStorageService storageService;


    // ==================== 列表 ====================

    /**
     * 图片列表。<b>只列当前生效那家存储里的文件。</b>
     *
     * <h3>为什么要按 provider 过滤</h3>
     * <p>换存储 = 换了一个文件库。老文件还在老桶里、地址也还指着老桶，但新配置下
     * 那个桶可能已经不可访问了（密钥换了、桶删了、域名解绑了）。不过滤的话，
     * 列表里会混着两个桶的文件，其中一半点开是 404 裂图，而界面上看不出区别。</p>
     *
     * <p>ERP 那版就有这个条件（按 {@code s_m_shop_storage} 的 active 行过滤），
     * 迁过来时因为「一期恒为 local、不存在第二个 provider」而省掉了，
     * {@code SiteImageMapper.xml} 顶部留了一条「将来真接了对象存储要补回来」的警告。
     * 2026-09-07 接上腾讯云 COS，那个「将来」到了。</p>
     *
     * <h3>⚠ 这不是查询条件，是硬边界</h3>
     * <p>{@code query} 是从请求参数绑上来的，客户端能自己传 {@code ?provider=local}。
     * 所以这里<b>无条件覆盖</b>它，不能写成「前端没传才填」——那等于把边界交给调用方。</p>
     */
    public Map<String, Object> selectImageList(SiteImage query, Integer pageNum, Integer pageSize)
    {
        SiteImage condition = query == null ? new SiteImage() : query;
        condition.setProvider(storageService.currentProvider());
        int size = pageSize(pageSize);
        int offset = offset(pageNum, size);
        int total = imageMapper.countList(condition);
        List<SiteImage> rows = total == 0 ? List.of() : imageMapper.selectList(condition, offset, size);
        return page(total, rows);
    }

    /** 视频列表。规则同 {@link #selectImageList}，包括那个无条件覆盖 */
    public Map<String, Object> selectVideoList(SiteVideo query, Integer pageNum, Integer pageSize)
    {
        SiteVideo condition = query == null ? new SiteVideo() : query;
        condition.setProvider(storageService.currentProvider());
        int size = pageSize(pageSize);
        int offset = offset(pageNum, size);
        int total = videoMapper.countList(condition);
        List<SiteVideo> rows = total == 0 ? List.of() : videoMapper.selectList(condition, offset, size);
        return page(total, rows);
    }

    /**
     * 某一家存储里有多少个文件。
     *
     * <p>给「换存储」那个确认框用：切过去之后原来那家的文件不再出现在列表里
     * （{@link #selectImageList} 那个过滤），得先把数目告诉人，
     * 否则商家会以为图被删了。<b>必须在 save 之前调</b>——save 之后
     * 「当前生效的是哪家」就变了。</p>
     */
    public int countImagesOf(String provider)
    {
        return provider == null || provider.isBlank() ? 0 : imageMapper.countByProvider(provider);
    }

    /** 同 {@link #countImagesOf}，视频那张表 */
    public int countVideosOf(String provider)
    {
        return provider == null || provider.isBlank() ? 0 : videoMapper.countByProvider(provider);
    }

    /** 3D 模型列表。规则同 {@link #selectImageList}，包括那个无条件覆盖 */
    public Map<String, Object> selectModelList(SiteModel query, Integer pageNum, Integer pageSize)
    {
        SiteModel condition = query == null ? new SiteModel() : query;
        condition.setProvider(storageService.currentProvider());
        int size = pageSize(pageSize);
        int offset = offset(pageNum, size);
        int total = modelMapper.countList(condition);
        List<SiteModel> rows = total == 0 ? List.of() : modelMapper.selectList(condition, offset, size);
        return page(total, rows);
    }

    /** 同 {@link #countImagesOf}，模型那张表 */
    public int countModelsOf(String provider)
    {
        return provider == null || provider.isBlank() ? 0 : modelMapper.countByProvider(provider);
    }

    // ==================== 上传 ====================

    @Transactional(rollbackFor = Exception.class)
    public SiteImage uploadImage(MultipartFile file, Integer width, Integer height,
            String folderId, String operator)
    {
        SiteInfo site = siteInfoService.require();
        requireFile(file);
        String ext = requireExtension(file, properties.getUpload().imageExtList(), "图片");

        SiteStorage storage = storageService.requireActive();
        String imageId = codeGenerator.nextImageId();
        String objectName = store(storage, file, IMAGE_FOLDER, imageId, ext);

        SiteImage image = new SiteImage();
        image.setImageId(imageId);
        image.setShopCode(site.getShopCode());
        image.setSiteCode(site.getSiteCode());
        image.setImageName(displayName(file.getOriginalFilename()));
        image.setImageUrl(publicUrl(storage, objectName));
        // 缩略图：对象存储那套是靠厂商的图片处理参数现生成的，本机磁盘没有这个能力。
        // 存 null，前端回退显示原图。列留着，将来接上图片处理再填
        image.setThumbUrl(null);
        image.setObjectName(objectName);
        // ⚠ 存实际用的那一家，不能再硬编码 local。这一列是「这个文件当初存到哪去了」的
        //   唯一记录——换存储之后老文件还在老地方，靠它才能知道该去哪取
        image.setProvider(storage.getProvider());
        image.setFileSize(file.getSize());
        image.setMimeType(file.getContentType());
        image.setWidth(positiveOrNull(width));
        image.setHeight(positiveOrNull(height));
        // ⚠ 过一遍 resolveUploadFolder：文件夹可能刚被别人删了，那时候落到根，
        //   而不是让整个上传失败——文件都传完了，为这个报错不值
        image.setFolderId(folderService.resolveUploadFolder(SiteFolderService.TYPE_IMAGE, folderId));
        image.setCreateBy(operator);
        image.setUpdateBy(operator);
        imageMapper.insertImage(image);
        return image;
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteVideo uploadVideo(MultipartFile file, Integer width, Integer height,
            Integer duration, String folderId, String operator)
    {
        SiteInfo site = siteInfoService.require();
        requireFile(file);
        String ext = requireExtension(file, properties.getUpload().videoExtList(), "视频");

        SiteStorage storage = storageService.requireActive();
        String videoId = codeGenerator.nextVideoId();
        String objectName = store(storage, file, VIDEO_FOLDER, videoId, ext);

        SiteVideo video = new SiteVideo();
        video.setVideoId(videoId);
        video.setShopCode(site.getShopCode());
        video.setSiteCode(site.getSiteCode());
        video.setVideoName(displayName(file.getOriginalFilename()));
        video.setVideoUrl(publicUrl(storage, objectName));
        video.setObjectName(objectName);
        video.setProvider(storage.getProvider());
        video.setFileSize(file.getSize());
        video.setMimeType(file.getContentType());
        video.setWidth(positiveOrNull(width));
        video.setHeight(positiveOrNull(height));
        video.setDuration(positiveOrNull(duration));
        video.setFolderId(folderService.resolveUploadFolder(SiteFolderService.TYPE_VIDEO, folderId));
        video.setCreateBy(operator);
        video.setUpdateBy(operator);
        videoMapper.insertVideo(video);
        return video;
    }

    /**
     * 上传 3D 模型。只收 {@code .glb}（白名单 {@code station.upload.model-ext}，
     * 为什么不收 gltf 写在 {@link StationProperties.Upload#getModelExt()} 上）。
     *
     * <p>没有宽高时长要量：服务端不解析 glb，前端也不解析——传错文件的话
     * 管理台「预览」那一步就能看出来，不用为此在这里读二进制。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteModel uploadModel(MultipartFile file, String folderId, String operator)
    {
        SiteInfo site = siteInfoService.require();
        requireFile(file);
        String ext = requireExtension(file, properties.getUpload().modelExtList(), "3D 模型");
        return saveModel(site, file, ext, folderId, operator);
    }

    /**
     * 混元生 3D 生成完、下载下来的 glb 入库（2026-09-26）。和手动上传走同一段存储 + 落库代码，
     * 所以生成的模型在文件库、商品挂载、换域名脚本里和上传的没有任何区别。
     *
     * <p>调用方（{@code ModelGenService}）已经校验过前 4 字节是 {@code glTF}，这里不再解析。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteModel saveGeneratedModel(byte[] glb, String name, String folderId, String operator)
    {
        if (glb == null || glb.length == 0)
        {
            throw new ServiceException("生成的模型文件是空的");
        }
        SiteInfo site = siteInfoService.require();
        String fileName = (name == null || name.isBlank() ? "混元生成" : name.trim()) + ".glb";
        return saveModel(site, new BytesFile(fileName, "model/gltf-binary", glb), "glb", folderId, operator);
    }

    private SiteModel saveModel(SiteInfo site, MultipartFile file, String ext, String folderId, String operator)
    {
        SiteStorage storage = storageService.requireActive();
        String modelId = codeGenerator.nextModelId();
        String objectName = store(storage, file, MODEL_FOLDER, modelId, ext);

        SiteModel model = new SiteModel();
        model.setModelId(modelId);
        model.setShopCode(site.getShopCode());
        model.setSiteCode(site.getSiteCode());
        model.setModelName(displayName(file.getOriginalFilename()));
        model.setModelUrl(publicUrl(storage, objectName));
        model.setObjectName(objectName);
        model.setProvider(storage.getProvider());
        model.setFileSize(file.getSize());
        model.setMimeType(file.getContentType());
        model.setFolderId(folderService.resolveUploadFolder(SiteFolderService.TYPE_MODEL, folderId));
        model.setCreateBy(operator);
        model.setUpdateBy(operator);
        modelMapper.insertModel(model);
        return model;
    }

    /**
     * 存一张买家定制方案的图（logo / 开机画面），返回对外地址。
     *
     * <p>和 {@link #uploadImage} 的区别：<b>不写 {@code u_s_siteimage}</b>——买家传的图不该出现在
     * 商家的素材库里；落在单独的 {@code custom/} 目录，文件名是方案号加序号。
     * 格式、大小、能不能解码都由调用方（{@code CustomPlanService}）验过，这里只管存。</p>
     *
     * <p>失败照 {@link #storeCos} 的约定抛出去。已经存上去的前几张不删（{@link CosClient} 没有删除能力），
     * 调用方负责把对象键记进日志。</p>
     *
     * @param id  文件名（不含扩展名），形如 {@code <planId>-0}；调用方保证只含 [A-Za-z0-9-]
     * @param ext {@code png} 或 {@code jpg}
     */
    public String storeCustomImage(byte[] data, String id, String ext, String contentType)
    {
        SiteStorage storage = storageService.requireActive();
        String objectName = store(storage, new BytesFile(id + "." + ext, contentType, data), CUSTOM_FOLDER, id, ext);
        return publicUrl(storage, objectName);
    }

    // ==================== 改名 / 删除 ====================

    @Transactional(rollbackFor = Exception.class)
    public int renameImage(String imageId, String name, String remark, String operator)
    {
        if (imageMapper.selectByCode(requireId(imageId)) == null)
        {
            throw new ServiceException("图片不存在");
        }
        SiteImage update = new SiteImage();
        update.setImageId(imageId);
        update.setImageName(requireName(name));
        update.setRemark(trimToNull(remark));
        update.setUpdateBy(operator);
        return imageMapper.updateImageInfo(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public int renameVideo(String videoId, String name, String remark, String operator)
    {
        if (videoMapper.selectByCode(requireId(videoId)) == null)
        {
            throw new ServiceException("视频不存在");
        }
        SiteVideo update = new SiteVideo();
        update.setVideoId(videoId);
        update.setVideoName(requireName(name));
        update.setRemark(trimToNull(remark));
        update.setUpdateBy(operator);
        return videoMapper.updateVideoInfo(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public int renameModel(String modelId, String name, String remark, String operator)
    {
        if (modelMapper.selectByCode(requireId(modelId)) == null)
        {
            throw new ServiceException("模型不存在");
        }
        SiteModel update = new SiteModel();
        update.setModelId(modelId);
        update.setModelName(requireName(name));
        update.setRemark(trimToNull(remark));
        update.setUpdateBy(operator);
        return modelMapper.updateModelInfo(update);
    }

    /**
     * 删图片。
     *
     * <p><b>只删数据库记录，磁盘上的文件留着</b>——文件可能已经被首页装修、商品详情引用了，
     * 删掉会让线上页面出现裂图，而且是删完好几天后才有人发现。孤儿文件由运维定期清理，
     * 那是一个可以慢慢来、能先对一遍引用的动作。</p>
     *
     * <p>这一条和 ERP 那版一致（那边是「桶里的文件留着」），换了存储介质不改变这个判断。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeImage(String imageId)
    {
        int rows = imageMapper.deleteByCode(requireId(imageId));
        if (rows == 0)
        {
            throw new ServiceException("图片不存在");
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public int removeVideo(String videoId)
    {
        int rows = videoMapper.deleteByCode(requireId(videoId));
        if (rows == 0)
        {
            throw new ServiceException("视频不存在");
        }
        return rows;
    }

    /** 删模型。只删记录不删文件，理由同 {@link #removeImage}——商品可能正挂着它 */
    @Transactional(rollbackFor = Exception.class)
    public int removeModel(String modelId)
    {
        int rows = modelMapper.deleteByCode(requireId(modelId));
        if (rows == 0)
        {
            throw new ServiceException("模型不存在");
        }
        return rows;
    }

    // ==================== 存储配置自检 ====================

    /** 探针对象的前缀。用一个不像业务目录的名字，扫桶的时候一眼能认出来是自检留下的 */
    private static final String PROBE_FOLDER = "__probe";

    /**
     * 保存存储配置前的连通性自检：真的传一个探针对象上去，再<b>按访客的方式</b>取回来。
     *
     * <h3>为什么必须在保存之前做，而且失败就不许保存</h3>
     * <p>这套设计里唯一真正致命的错误是「配置存下去了，但地址取不到东西」。它不会在
     * 保存那一刻报错，也不会在上传那一刻报错——上传会成功，库里会存下一条完整 URL，
     * 然后每张图都是死链，等到有人打开页面才发现。而那时候要改的是历史数据
     * （{@code imageurl} 存的是上传那一刻固化下来的完整地址）。</p>
     *
     * <p>所以这里宁可把「保存配置」这个动作做重、做慢（一次 PUT + 一次 GET，两三秒），
     * 也不让一份取不到东西的配置落库。这和 {@link #publicUrl} 里「不给相对路径兜底」
     * 是同一条思路：让错误在<b>能当场改</b>的那一刻发生。</p>
     *
     * <h3>两步各自能证明什么</h3>
     * <ol>
     *   <li><b>PUT</b>（带签名，打 COS 的桶域名）—— 证明 SecretId/SecretKey 对、
     *       桶名对、地域对、这把密钥有写权限。失败时 COS 的错误码本身就是处置方式。</li>
     *   <li><b>GET</b>（匿名，打{@code public_url} 那个自定义域名）—— 证明自定义域名
     *       解析对了、证书对、桶允许公开读、而且拼出来的地址真能取到刚放上去那个对象。
     *       <b>这一步是匿名的</b>，因为它要模拟的就是访客的浏览器；带签名的话，
     *       一个私有桶也能通过，而那正是它唯一要拦的东西。</li>
     * </ol>
     *
     * <h3>探针对象不删</h3>
     * <p>{@link CosClient} 故意没有 DELETE 能力（那个类的注释里写了原因）。探针对象
     * 留在桶里 {@code __probe/} 下，一次保存一个几十字节的文件，攒不出体量，
     * 由运维和孤儿对象一起定期清。用随机 UUID 做名字，不会被人枚举出来。</p>
     *
     * @param candidate 待保存的配置。密钥可能是明文（这次填了），也可能为空（沿用库里那把）
     */
    public void verifyStorage(SiteStorage candidate)
    {
        if (candidate == null || !SiteStorage.PROVIDER_TENCENT.equals(candidate.getProvider()))
        {
            // local 没有远端可连；aliyun / huawei 没有实现，会在 store() 那里被明确拒绝。
            // 这里不重复报错——那样人还没保存就先吃一个看不懂的自检失败
            return;
        }

        String bucket = trimToNull(candidate.getBucket());
        String secretId = trimToNull(candidate.getAccessKeyId());
        if (bucket == null || secretId == null)
        {
            throw new ServiceException("腾讯云 COS 的配置不完整（SecretId / 存储桶名称）");
        }

        // 密钥：这次填了就用这次的（此时还是明文，save() 之后才会加密），
        // 没填表示「不改密钥」，那就用库里已经存着的那把
        String secretKey = trimToNull(candidate.getAccessKeySecret());
        if (secretKey == null)
        {
            secretKey = storageService.storedSecret(candidate.getProvider());
        }
        if (secretKey == null || secretKey.isBlank())
        {
            throw new ServiceException("还没有可用的 SecretKey，请填写一次");
        }

        String key = PROBE_FOLDER + "/" + UUID.randomUUID() + ".txt";
        byte[] body = key.getBytes(StandardCharsets.UTF_8);
        String host = cosHost(candidate);

        // ---- 第一步：带签名 PUT ----
        try
        {
            CosClient.put(host, key, secretId, secretKey,
                    () -> new ByteArrayInputStream(body), body.length, "text/plain");
        }
        catch (CosClient.CosServiceException e)
        {
            log.warn("存储自检：COS 拒绝写入 code={} host={}", e.getErrorCode(), host);
            throw new ServiceException("连不上腾讯云 COS（" + e.getErrorCode() + "）。"
                    + explainCosError(e.getErrorCode()));
        }
        catch (IOException e)
        {
            log.warn("存储自检：写 COS 出错 host={}", host, e);
            throw new ServiceException("连接腾讯云 COS 超时或网络不通，稍后再试一次；"
                    + "一直不行就检查地域 Region 填得对不对");
        }

        // ---- 第二步：匿名 GET 自定义域名 ----
        String url = publicUrl(candidate, key);
        CosClient.PublicProbe probe;
        try
        {
            probe = CosClient.getPublic(url);
        }
        catch (IOException e)
        {
            log.warn("存储自检：取不到 {}", url, e);
            throw new ServiceException("文件已经能写进桶，但用「" + url + "」取不回来："
                    + "域名解析不了、连不上或者证书不对。"
                    + "确认自定义域名已经在腾讯云控制台绑到这个桶上并配好证书；"
                    + "暂时没有自有域名就把「自定义域名」清空，系统会用 COS 的默认域名");
        }

        if (!probe.ok())
        {
            log.warn("存储自检：{} 返回 {} body={}", url, probe.status(), probe.body());
            throw new ServiceException("文件已经能写进桶，但用「" + url + "」取回来是 "
                    + probe.status() + "：" + explainProbeStatus(probe));
        }

        // 状态码 200 还不够。域名指到别的服务器上时，那台服务器可能对任何路径都回一个
        // 200 的首页——地址「能打开」，但打开的不是这张图。比对内容才能证明取到的
        // 确实是刚放上去那个对象
        if (probe.body() == null || !probe.body().contains(key))
        {
            log.warn("存储自检：{} 回了 200 但内容对不上 body={}", url, probe.body());
            throw new ServiceException("「" + url + "」能打开，但取回来的不是刚上传的那个文件——"
                    + "这个域名多半指向了别的服务，不是这个 COS 桶。"
                    + "检查它在腾讯云控制台的绑定关系");
        }

        log.info("存储自检通过：host={} url={}", host, url);
    }

    /** 把 COS 的错误码翻译成「该去改哪一栏」。三个码对应三个完全不同的处置 */
    private static String explainCosError(String code)
    {
        if (code == null)
        {
            return "检查密钥、存储桶和地域";
        }
        return switch (code)
        {
            case "SignatureDoesNotMatch", "InvalidAccessKeyId" ->
                    "SecretId 或 SecretKey 不对，去腾讯云控制台重新复制一次";
            case "NoSuchBucket" ->
                    "桶不存在。检查桶名有没有带上 APPID（形如 name-1300000000），以及地域 Region 是不是这个桶所在的地域";
            case "AccessDenied" ->
                    "这把密钥没有这个桶的写权限。用子账号的话，去访问管理里给它授这个桶的读写";
            default -> "对着这个错误码查腾讯云文档，或者核对密钥、桶名、地域三项";
        };
    }

    /** 匿名 GET 的非 2xx，逐个说清是哪种配置问题 */
    private static String explainProbeStatus(CosClient.PublicProbe probe)
    {
        int status = probe.status();
        if (status == 403 || (probe.body() != null && probe.body().contains("AccessDenied")))
        {
            return "桶不允许公开读。去存储桶的权限设置里改成「公有读私有写」——"
                    + "不改的话文件能传上去，但访客看到的每张图都是 403";
        }
        if (status == 404)
        {
            return "域名通了但这个对象取不到。多半是自定义域名后面多带了一层路径"
                    + "（这一栏只填到域名为止，image/… 那一段由系统拼），"
                    + "或者域名绑到了别的桶上";
        }
        if (status >= 300 && status < 400)
        {
            return "这个域名回了一个跳转，说明它指向的不是 COS 桶。"
                    + "存进库的是固定地址，跟着跳转走早晚会出事，所以这里不放行";
        }
        return "检查自定义域名在腾讯云控制台的绑定关系";
    }

    // ==================== 内部 ====================

    /**
     * 把上传的文件写到磁盘，返回相对根目录的路径。
     *
     * <p>目录按 {@code 类型/年/月/日} 分：一个目录下堆几万个文件之后，
     * {@code ls} 会卡住，而备份和清理都要 {@code ls}。</p>
     *
     * <p><b>文件名用发号器给的 id，不用原始文件名。</b>原始文件名是用户可控的字符串——
     * 里面可能有 {@code ../}、有空格、有中文、有和别人重名的。id 是本库发的、
     * 一定唯一、一定安全。显示用的原名单独存在 {@code imagename} 列里。</p>
     */
    private String store(SiteStorage storage, MultipartFile file, String folder, String id, String ext)
    {
        String relative = folder + "/" + LocalDate.now().format(DAY) + "/" + id + "." + ext;
        String provider = storage.getProvider();

        if (SiteStorage.PROVIDER_TENCENT.equals(provider))
        {
            return storeCos(file, relative, storage);
        }

        if (!SiteStorage.PROVIDER_LOCAL.equals(provider))
        {
            // aliyun / huawei 在表结构和前端下拉里都存在，但没有实现。
            // ⚠ 明确拒绝，不要静默落本机磁盘：那样文件在本机、URL 却指向没配好的桶，
            //   每张图都是死链，而且要等到有人打开页面才发现
            throw new ServiceException("暂不支持的存储厂商：" + provider
                    + "。当前只实现了腾讯云 COS（tencent）和本机磁盘（local）");
        }

        return storeLocal(file, relative);
    }

    /**
     * 传到腾讯云 COS，返回对象键。
     *
     * <h3>为什么是手写的 {@link CosClient} 而不是官方 SDK</h3>
     * <p>{@code com.qcloud:cos_api} 会带进 11 个传递依赖——httpclient 4、joda-time、
     * okhttp 3.12（2019 年的版本）、gson、tencentcloud-sdk-kms……而这个工程是
     * Spring Boot 4 + Jackson 3。为「PUT 一个对象」这一个动作付那个代价不划算，
     * 而且 pom.xml 顶部那道闸明写着「OkHttp 一个都不引」。</p>
     *
     * <p>SDK 版真正比手写强的两点（把 COS 错误码透出到页面、失败自动重试）
     * 已经搬进 {@link CosClient} 了。SDK 版做不到而手写能做到的是<b>内网加速</b>：
     * 它只接受 Region、把域名拼死成公网的，而这台机器公网只有 3 Mbps。</p>
     *
     * <h3>⚠ 失败必须抛，不能吞</h3>
     * <p>上传方法上有 {@code @Transactional}：这里抛出去，那条还没提交的
     * {@code u_s_image} 记录会跟着回滚。吞掉的后果是库里多一条指向不存在对象的记录，
     * 列表页把它渲染成一个裂图，而谁也不知道它是哪来的。</p>
     */
    private String storeCos(MultipartFile file, String relative, SiteStorage storage)
    {
        String bucket = trimToNull(storage.getBucket());
        String secretId = trimToNull(storage.getAccessKeyId());
        if (bucket == null || secretId == null)
        {
            throw new ServiceException("腾讯云 COS 的配置不完整（SecretId / 存储桶名称），去「站点概览」页的「文件存储」里补齐");
        }
        // 密钥在这里才解密，解出来的明文只活在这一次调用的栈上，不进任何字段、不进日志
        String secretKey = storageService.decryptSecret(storage);
        if (secretKey == null || secretKey.isBlank())
        {
            // 解不出来有两种可能，处置是同一个：回「文件存储」重填一次
            //   ① 从来没填过；
            //   ② 填过，但 STATION_CONFIG_SECRET 这个主密钥被换过——换主密钥不会
            //      重新加密库里已有的密文，AES-GCM 的认证标签会校验失败
            throw new ServiceException("腾讯云 COS 的密钥读不出来，去「站点概览」页的「文件存储」里重新填一次 SecretKey");
        }

        String host = cosHost(storage);
        try
        {
            // ⚠ 传的是方法引用不是流：重试时要重新打开一个。
            //   MultipartFile.getInputStream() 每次调用都重新打开临时文件，正好符合约定
            CosClient.put(host, relative, secretId, secretKey, file::getInputStream,
                    file.getSize(), file.getContentType());
            log.info("COS 上传成功：host={} key={} size={}", host, relative, file.getSize());
            return relative;
        }
        catch (CosClient.CosServiceException e)
        {
            // 服务端明确拒绝：密钥不对、桶不存在、没权限、地域填错。
            // 这几种都是配置问题，把 COS 的错误码带给用户——否则只能靠猜，
            // 而 SignatureDoesNotMatch / NoSuchBucket / AccessDenied 是三个完全不同的处置
            log.error("COS 拒绝上传：code={} host={} key={}", e.getErrorCode(), host, relative, e);
            throw new ServiceException("上传到腾讯云失败（" + e.getErrorCode()
                    + "）。检查管理台「站点概览」页「文件存储」里的密钥、存储桶和地域是否正确");
        }
        catch (IOException e)
        {
            // 网络不通、超时、读流失败——重试可能就好了，不必让用户去改配置
            log.error("COS 上传出错：host={} key={}", host, relative, e);
            throw new ServiceException("上传失败，请稍后重试");
        }
    }

    /**
     * 算出要请求的 COS 主机名。
     *
     * <h3>为什么要从 region 推</h3>
     * <p>管理台的「文件存储」里，<b>腾讯云那一档显示的是「地域 Region」，不是 Endpoint</b>
     * （station-console-ui 的 Overview.vue：{@code v-if="storageProvider === 'tencent'"}
     * 那两个 form-item 是二选一的）。所以选腾讯云保存下来的行里 {@code endpoint} 是空的、
     * {@code region} 才有值——只认 endpoint 的话，第一次上传就是「配置不完整」。</p>
     *
     * <p>推导规则和 ERP 那边一致（{@code OssProperties}：
     * {@code "cos." + region + ".myqcloud.com"}），两处别各写各的。</p>
     *
     * <h3>endpoint 优先，这是内网加速的入口</h3>
     * <p>{@code endpoint} 填了就用它。管理台在腾讯云这一档不显示这个输入框，
     * 但库里那一列还在——这正好是走内网的口子：</p>
     *
     * <pre>update u_s_storage set endpoint='cos-internal.ap-shanghai.myqcloud.com' where provider='tencent';</pre>
     *
     * <p>CVM 和桶在同一地域时，内网域名不限速也不计流量费。这台机器公网只有 3 Mbps，
     * 传一个 200MB 的视频，公网要十几分钟、内网几十秒。
     * ⚠ 跨地域时内网域名连不上，表现是上传超时——那时候把这一列清空即可退回公网。</p>
     */
    private String cosHost(SiteStorage storage)
    {
        String endpoint = trimToNull(storage.getEndpoint());
        if (endpoint == null)
        {
            String region = trimToNull(storage.getRegion());
            if (region == null)
            {
                throw new ServiceException("腾讯云 COS 没填地域 Region，去「站点概览」页的「文件存储」里补上（如 ap-shanghai）");
            }
            endpoint = "cos." + region + ".myqcloud.com";
        }
        return CosClient.resolveHost(endpoint, storage.getBucket());
    }

    /**
     * 算出<b>对外</b>的 COS 主机名。没配自定义域名时，地址就用它拼。
     *
     * <h3>为什么不能直接用 {@link #cosHost}</h3>
     * <p>{@code cosHost} 算的是「这次上传要 PUT 到哪」，它优先用 {@code endpoint} 列——
     * 而那一列存在的意义正是填内网域名 {@code cos-internal.<region>.myqcloud.com} 做上传加速
     * （这台机器公网只有 3 Mbps，走内网快一个数量级，还不计流量费）。</p>
     *
     * <p>把它直接拿来拼对外地址，就会让「配了内网加速」+「没填自定义域名」这两个
     * <b>各自都完全正确</b>的选择组合出一个灾难：文件确实传上去了，
     * 库里存下的地址却是一个只有腾讯云内网能解析的域名——服务器上 curl 得通，
     * 访客的浏览器一个都打不开。而且保存配置时的连通性自检也查不出来，
     * 因为自检本身就是从这台服务器上发的。</p>
     *
     * <p>所以这里把内网域名换回公网域名。其余形态原样保留。</p>
     */
    private String publicCosHost(SiteStorage storage)
    {
        return cosHost(storage).replace(".cos-internal.", ".cos.");
    }

    /**
     * 落本机磁盘，返回相对根目录的路径。
     */
    private String storeLocal(MultipartFile file, String relative)
    {
        try
        {
            Path root = Paths.get(properties.getUpload().getDir()).toAbsolutePath().normalize();
            Path target = root.resolve(relative).normalize();
            // 兜底：relative 是本方法自己拼的，理论上跑不出 root。留着这一句是因为
            // 「理论上」在这类代码里靠不住——folder 和 ext 将来可能被改成可配置的
            if (!target.startsWith(root))
            {
                throw new ServiceException("文件路径不合法");
            }
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream())
            {
                // 流式写盘，不经过 byte[]。单文件上限 200MB，
                // 改成 file.getBytes() 的话几个人同时传就把堆吃光了
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relative;
        }
        catch (IOException e)
        {
            // 磁盘满、权限不对、目录不存在——都是运维问题，细节进日志不进页面
            log.error("写文件失败：{}", relative, e);
            throw new ServiceException("文件上传失败，请稍后重试");
        }
    }

    /**
     * 拼对外访问地址。
     *
     * <p>没配 {@code station.upload.base-url} 时抛异常，<b>不给一个相对路径兜底</b>：
     * 兜底的后果是库里存进一批 {@code /image/2026/09/03/xxx.jpg} 这样的地址，
     * 前台在自己域名下看着正常，等哪天换了域名或者被别的站引用就全裂了——
     * 而那时候要改的是几千行历史数据（独立站部署手册（Windows）.md 附录 D.1）。
     * 上线前把这个值配对，比事后清历史数据便宜得多。</p>
     */
    private String publicUrl(SiteStorage storage, String objectName)
    {
        if (SiteStorage.PROVIDER_TENCENT.equals(storage.getProvider()))
        {
            // 优先用 u_s_storage.public_url（自定义域名 / CDN）。留空才回落到 COS 默认域名——
            // 默认域名把桶名和 APPID 暴露在每一张图片的地址里，而且以后想换 CDN 就得刷历史数据
            String base = stripTrailingSlash(trimToNull(storage.getPublicUrl()));
            if (base == null)
            {
                base = "https://" + publicCosHost(storage);
            }
            return base + "/" + objectName;
        }

        String base = properties.getUpload().normalizedBaseUrl();
        if (base.isEmpty())
        {
            throw new ServiceException("还没配文件访问域名（station.upload.base-url），"
                    + "先配好再上传——存进库的是完整地址，配错了要改历史数据");
        }
        return base + "/" + objectName;
    }

    private Map<String, Object> page(int total, List<?> rows)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    private int pageSize(Integer pageSize)
    {
        return pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int offset(Integer pageNum, int size)
    {
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        return (current - 1) * size;
    }

    /** 显示名默认取原始文件名；超长的截断而不是报错——名字太长不是用户该关心的错误 */
    private String displayName(String originalFilename)
    {
        String name = trimToNull(originalFilename);
        if (name == null)
        {
            return "未命名";
        }
        return name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
    }

    private void requireFile(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要上传的文件");
        }
    }

    /** 校验扩展名并返回它（小写）。落盘时要用它拼文件名 */
    private String requireExtension(MultipartFile file, List<String> allowed, String label)
    {
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext))
        {
            throw new ServiceException(label + "格式不支持，支持的格式：" + String.join("、", allowed));
        }
        return ext;
    }

    private String requireName(String name)
    {
        String value = trimToNull(name);
        if (value == null)
        {
            throw new ServiceException("名称不能为空");
        }
        return value.length() > NAME_MAX_LENGTH ? value.substring(0, NAME_MAX_LENGTH) : value;
    }

    private String requireId(String id)
    {
        String value = trimToNull(id);
        if (value == null)
        {
            throw new ServiceException("缺少主键");
        }
        return value;
    }

    /** 前端量不到尺寸时会传 0 或负数上来，一律当没量到处理 */
    private Integer positiveOrNull(Integer value)
    {
        return value == null || value <= 0 ? null : value;
    }

    /** 末尾斜杠去掉，避免拼出 {@code https://x.com//image/...} 这种双斜杠地址 */
    private static String stripTrailingSlash(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value;
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 内存里的一段字节当成上传文件用，好让生成的模型走和上传完全一样的 {@link #store} 路径。
     * 模型上限 100MB（ModelGenService 下载时就截断了），放内存没问题。
     */
    private static final class BytesFile implements MultipartFile
    {
        private final String fileName;
        private final String contentType;
        private final byte[] data;

        BytesFile(String fileName, String contentType, byte[] data)
        {
            this.fileName = fileName;
            this.contentType = contentType;
            this.data = data;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(data); }

        @Override
        public void transferTo(java.io.File dest) throws IOException
        {
            Files.write(dest.toPath(), data);
        }
    }
}
