package com.ruoyi.station.console.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteModel;
import com.ruoyi.station.console.domain.SiteModelJob;
import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.mapper.SiteImageMapper;
import com.ruoyi.station.console.mapper.SiteModelJobMapper;
import com.ruoyi.station.console.util.Ai3dClient;

/**
 * 管理台「用照片生成」3D 模型：腾讯混元生 3D（专业版）。
 *
 * <p>设计：docs/superpowers/specs/2026-09-26-hunyuan-3d-generate-design.md</p>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>{@link #submit}：校验图片都是本站文件库里的、当天没超上限 → 落一行 SUBMITTING →
 *       调 {@code SubmitHunyuanTo3DProJob} → WAIT（或 FAIL）；</li>
 *   <li>{@link #poll}（每 15 秒）：查还没结束的任务 → DONE 时下载 glb、校验、
 *       经 {@link SiteFileService#saveGeneratedModel} 转存进模型库 → DONE。</li>
 * </ol>
 *
 * <p>进度放后端轮询而不是前端：关掉浏览器也会完成；前端只读任务表。</p>
 *
 * <h3>密钥</h3>
 * <p>复用「文件存储」里腾讯云那一对（和 {@link SiteTranslateService} 一样的取法）。
 * 那把密钥是子账号的话，要在访问管理里给它挂 {@code QcloudAI3DFullAccess}。
 * ⚠ 密钥明文只活在调用栈上，不进字段、不进日志、不进任何响应。</p>
 *
 * <h3>⚠ 依赖 {@code @EnableScheduling}</h3>
 * <p>见 {@code PageViewMaintenance} 的类注释。那个注解没了，任务会永远停在「排队中」而不报任何错。</p>
 */
@Service
public class ModelGenService
{
    private static final Logger log = LoggerFactory.getLogger(ModelGenService.class);

    /** 精细度两档。混元默认 50 万面，glb 常见二三十 MB，前台展示台要下完才显示 */
    static final int FACE_STANDARD = 150_000;
    static final int FACE_FINE = 400_000;

    /** 生成的 glb 上限。超过就是参数不对或者混元那边出了问题，不往桶里塞 */
    static final long MAX_GLB_BYTES = 100L * 1024 * 1024;
    static final int MAX_SAVE_RETRIES = 5;
    /** 超过这么久还没完成就判失败。混元的 JobId 24 小时有效，2 小时已经很宽 */
    static final long JOB_TIMEOUT_MS = 2L * 60 * 60 * 1000;
    /** SUBMITTING 超过这么久还没拿到混元任务号，说明提交那一刻服务重启了 */
    static final long SUBMIT_STUCK_MS = 5L * 60 * 1000;
    private static final int POLL_BATCH = 10;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 下结果文件用。混元的结果地址是预签名的 COS 链接，可能有跳转 */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${station.ai3d.enabled:true}")
    private boolean enabled;

    @Value("${station.ai3d.region:ap-guangzhou}")
    private String region;

    /** 混元模型版本："3.0" / "3.1" */
    @Value("${station.ai3d.model:3.0}")
    private String modelVersion;

    @Value("${station.ai3d.daily-limit:20}")
    private int dailyLimit;

    @Autowired
    private SiteModelJobMapper jobMapper;

    @Autowired
    private SiteImageMapper imageMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CodeGenerator codeGenerator;

    @Autowired
    private SiteFileService fileService;

    @Autowired
    private SiteStorageService storageService;

    /**
     * 提交参数。
     *
     * @param front   正面图（必填），本站文件库里的图片地址
     * @param quality {@code standard}（默认，15 万面）或 {@code fine}（40 万面）
     */
    public record Request(String front, String left, String right, String back,
            String name, String quality, Boolean pbr, String folderId)
    {
    }

    // ==================== 提交 ====================

    public SiteModelJob submit(Request req, String operator)
    {
        if (!enabled)
        {
            throw new ServiceException("「用照片生成 3D」没有启用（station.ai3d.enabled）");
        }
        if (req == null || trimToNull(req.front()) == null)
        {
            throw new ServiceException("请选择一张正面图");
        }

        // 只许用本站文件库里的图：混元要能从公网拉到，也防止把任意外部地址塞进来
        Map<String, String> images = new LinkedHashMap<>();
        images.put("front", requireOwnImage(req.front(), "正面图"));
        putView(images, "left", req.left(), "左侧图");
        putView(images, "right", req.right(), "右侧图");
        putView(images, "back", req.back(), "背面图");

        int faceCount = "fine".equalsIgnoreCase(trimToNull(req.quality())) ? FACE_FINE : FACE_STANDARD;
        boolean pbr = Boolean.TRUE.equals(req.pbr());

        int today = jobMapper.countSince(startOfToday());
        if (today >= dailyLimit)
        {
            throw new ServiceException("今天已经生成 " + today + " 次，达到上限 " + dailyLimit
                    + " 次。明天再试，或调高 station.ai3d.daily-limit");
        }

        Credentials credentials = credentials();
        SiteInfo site = siteInfoService.require();

        SiteModelJob job = new SiteModelJob();
        job.setJobId(codeGenerator.nextModelJobId());
        job.setSiteCode(site.getSiteCode());
        job.setStatus(SiteModelJob.SUBMITTING);
        job.setJobName(jobName(req.name(), images.get("front")));
        job.setImagesJson(toJson(images));
        job.setFaceCount(faceCount);
        job.setEnablePbr(pbr ? 1 : 0);
        job.setFolderId(trimToNull(req.folderId()));
        job.setCreateBy(operator);
        jobMapper.insertJob(job);

        String payload = toJson(submitPayload(images, faceCount, pbr, modelVersion));
        try
        {
            String body = Ai3dClient.call(Ai3dClient.ACTION_SUBMIT, region, payload,
                    credentials.secretId(), credentials.secretKey());
            Map<String, Object> response = response(body);
            Map<String, Object> error = map(response.get("Error"));
            if (error != null)
            {
                fail(job, str(error.get("Code")), str(error.get("Message")));
            }
            else
            {
                String tcJobId = trimToNull(str(response.get("JobId")));
                if (tcJobId == null)
                {
                    fail(job, "NoJobId", "腾讯云没有返回任务号");
                }
                else
                {
                    job.setTcJobId(tcJobId);
                    job.setStatus(SiteModelJob.WAIT);
                    jobMapper.updateJob(job);
                    log.info("混元生 3D 已提交：job={} tcJob={} faces={} pbr={}", job.getJobId(), tcJobId, faceCount, pbr);
                }
            }
        }
        catch (Exception e)
        {
            log.error("混元生 3D 提交失败：job={}", job.getJobId(), e);
            job.setStatus(SiteModelJob.FAIL);
            job.setErrorCode("Network");
            job.setErrorMsg("连不上腾讯云，请稍后重试");
            job.setFinishtime(new Date());
            jobMapper.updateJob(job);
        }
        return job;
    }

    /** 提交请求体。包级可见给单测 */
    static Map<String, Object> submitPayload(Map<String, String> images, int faceCount, boolean pbr, String model)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ImageUrl", images.get("front"));
        List<Map<String, String>> views = new ArrayList<>();
        for (String view : List.of("left", "right", "back"))
        {
            String url = images.get(view);
            if (url != null)
            {
                Map<String, String> v = new LinkedHashMap<>();
                v.put("ViewType", view);
                v.put("ViewImageUrl", url);
                views.add(v);
            }
        }
        if (!views.isEmpty())
        {
            payload.put("MultiViewImages", views);
        }
        payload.put("FaceCount", faceCount);
        payload.put("EnablePBR", pbr);
        if (trimToNull(model) != null)
        {
            payload.put("Model", model.trim());
        }
        return payload;
    }

    // ==================== 轮询 ====================

    @Scheduled(initialDelay = 30_000, fixedDelay = 15_000)
    public void poll()
    {
        if (!enabled)
        {
            return;
        }
        List<SiteModelJob> pending = jobMapper.selectPending(POLL_BATCH);
        if (pending.isEmpty())
        {
            return;
        }
        Credentials credentials;
        try
        {
            credentials = credentials();
        }
        catch (ServiceException e)
        {
            // 密钥被删了：任务留着，配好之后接着查。不在这里批量判失败
            log.warn("混元生 3D 轮询跳过：{}", e.getMessage());
            return;
        }
        for (SiteModelJob job : pending)
        {
            try
            {
                pollOne(job, credentials);
            }
            catch (Exception e)
            {
                // 一条出错只影响它自己
                log.error("混元生 3D 轮询出错：job={}", job.getJobId(), e);
            }
        }
    }

    void pollOne(SiteModelJob job, Credentials credentials) throws Exception
    {
        long age = job.getCreatetime() == null ? 0 : System.currentTimeMillis() - job.getCreatetime().getTime();
        if (job.getTcJobId() == null)
        {
            // 落了库、还没拿到混元任务号：正常情况下 submit 几秒内就会把它改成 WAIT 或 FAIL。
            // 一直停在这里只可能是提交那一刻服务重启了——没有任务号就没法查，只能判失败
            if (age > SUBMIT_STUCK_MS)
            {
                fail(job, "SubmitInterrupted", "提交过程中服务重启了，这次没有生成，请重新提交");
            }
            return;
        }
        if (age > JOB_TIMEOUT_MS)
        {
            fail(job, "Timeout", "生成超时（超过 2 小时没有完成），请重新提交");
            return;
        }

        String body = Ai3dClient.call(Ai3dClient.ACTION_QUERY, region, toJson(Map.of("JobId", job.getTcJobId())),
                credentials.secretId(), credentials.secretKey());
        Map<String, Object> response = response(body);
        job.setPolls((job.getPolls() == null ? 0 : job.getPolls()) + 1);

        Map<String, Object> error = map(response.get("Error"));
        if (error != null)
        {
            fail(job, str(error.get("Code")), str(error.get("Message")));
            return;
        }

        String status = str(response.get("Status"));
        if ("FAIL".equalsIgnoreCase(status))
        {
            fail(job, str(response.get("ErrorCode")), str(response.get("ErrorMessage")));
            return;
        }
        if (!"DONE".equalsIgnoreCase(status))
        {
            // WAIT / RUN：只更新状态和计数
            job.setStatus("RUN".equalsIgnoreCase(status) ? SiteModelJob.RUN : SiteModelJob.WAIT);
            jobMapper.updateJob(job);
            return;
        }

        List<Object> files = list(response.get("ResultFile3Ds"));
        job.setPreviewUrl(previewUrl(files));
        String glbUrl = pickGlb(files);
        if (glbUrl == null)
        {
            fail(job, "NoGlb", "混元没有返回 glb 格式的模型");
            return;
        }

        job.setStatus(SiteModelJob.SAVING);
        jobMapper.updateJob(job);
        try
        {
            byte[] glb = download(glbUrl);
            SiteModel model = fileService.saveGeneratedModel(glb, job.getJobName(), job.getFolderId(), job.getCreateBy());
            job.setModelId(model.getModelId());
            job.setStatus(SiteModelJob.DONE);
            job.setErrorCode(null);
            job.setErrorMsg(null);
            job.setFinishtime(new Date());
            jobMapper.updateJob(job);
            log.info("混元生 3D 完成：job={} model={} bytes={}", job.getJobId(), model.getModelId(), glb.length);
        }
        catch (Exception e)
        {
            int retries = (job.getSaveRetries() == null ? 0 : job.getSaveRetries()) + 1;
            job.setSaveRetries(retries);
            log.warn("混元生 3D 下载转存失败（第 {} 次）：job={}", retries, job.getJobId(), e);
            if (retries >= MAX_SAVE_RETRIES)
            {
                String why = e instanceof ServiceException ? e.getMessage() : "网络或存储出错";
                fail(job, "SaveFailed", "模型已生成，但下载转存失败：" + why);
            }
            else
            {
                // 保持 SAVING，下次轮询重新查一遍拿新地址再下
                jobMapper.updateJob(job);
            }
        }
    }

    /** 在结果文件里挑 glb：Type 为 GLB（不分大小写），没有的话认地址路径以 .glb 结尾的 */
    static String pickGlb(List<Object> files)
    {
        String byPath = null;
        for (Object item : files)
        {
            Map<String, Object> file = map(item);
            if (file == null)
            {
                continue;
            }
            String url = trimToNull(str(file.get("Url")));
            if (url == null || !url.startsWith("https://"))
            {
                continue;
            }
            if ("GLB".equalsIgnoreCase(trimToNull(str(file.get("Type")))))
            {
                return url;
            }
            String path = url.split("\\?", 2)[0].toLowerCase(Locale.ROOT);
            if (byPath == null && path.endsWith(".glb"))
            {
                byPath = url;
            }
        }
        return byPath;
    }

    private static String previewUrl(List<Object> files)
    {
        for (Object item : files)
        {
            Map<String, Object> file = map(item);
            String url = file == null ? null : trimToNull(str(file.get("PreviewImageUrl")));
            if (url != null && url.startsWith("https://") && url.length() <= 1000)
            {
                return url;
            }
        }
        return null;
    }

    /** 下载结果文件，截断在上限，校验 glb 魔数 */
    private byte[] download(String url) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<InputStream> response = DOWNLOADER.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body())
        {
            if (response.statusCode() / 100 != 2)
            {
                throw new IOException("下载模型返回 HTTP " + response.statusCode());
            }
            byte[] data = in.readNBytes((int) MAX_GLB_BYTES + 1);
            if (data.length > MAX_GLB_BYTES)
            {
                throw new ServiceException("生成的模型超过 100MB，换「标准」精细度再试");
            }
            if (!isGlb(data))
            {
                throw new ServiceException("混元返回的文件不是有效的 glb");
            }
            return data;
        }
    }

    static boolean isGlb(byte[] data)
    {
        return data != null && data.length >= 12
                && data[0] == 'g' && data[1] == 'l' && data[2] == 'T' && data[3] == 'F';
    }

    // ==================== 列表 / 删除 ====================

    public Map<String, Object> list(Integer pageNum, Integer pageSize)
    {
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int page = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        int total = jobMapper.countList();
        List<SiteModelJob> rows = total == 0 ? List.of() : jobMapper.selectList((page - 1) * size, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        // 前端据此决定要不要继续每 10 秒刷新
        result.put("active", rows.stream().anyMatch(SiteModelJob::isActive));
        result.put("enabled", enabled);
        result.put("dailyLimit", dailyLimit);
        result.put("usedToday", jobMapper.countSince(startOfToday()));
        return result;
    }

    /** 只删任务记录，不删已经生成进模型库的模型 */
    public void remove(String jobId)
    {
        if (jobMapper.selectByCode(jobId) == null)
        {
            throw new ServiceException("这个任务不存在");
        }
        jobMapper.deleteByCode(jobId);
    }

    // ==================== 错误翻译 ====================

    /**
     * 腾讯云错误码 → 给商家看的话。具体错误码以首次接入时实际返回为准，单测里钉住。
     */
    static String translate(String code, String message)
    {
        String c = code == null ? "" : code;
        String m = message == null ? "" : message;
        if (c.startsWith("AuthFailure.UnauthorizedOperation"))
        {
            return "这把腾讯云密钥没有混元生 3D 权限：在腾讯云访问管理里给它挂上 QcloudAI3DFullAccess";
        }
        if (c.startsWith("AuthFailure"))
        {
            return "腾讯云密钥不对，去「站点概览 → 文件存储」重新填一次";
        }
        String lc = (c + " " + m).toLowerCase(Locale.ROOT);
        if (lc.contains("billing") || lc.contains("resourceinsufficient") || lc.contains("arrear")
                || lc.contains("balance") || m.contains("欠费") || m.contains("余额") || m.contains("积分"))
        {
            return "混元积分不足或没开按量付费：去腾讯云「混元生3D」控制台充值积分，或在「设置」里开启按量付费";
        }
        if (c.startsWith("ResourceUnavailable") || lc.contains("notopen") || m.contains("未开通"))
        {
            return "还没开通腾讯混元生3D：去腾讯云控制台开通并领取免费积分";
        }
        if (c.startsWith("LimitExceeded") || c.startsWith("RequestLimitExceeded"))
        {
            return "混元那边任务太多（同时最多 3 个），等前面的生成完再提交";
        }
        if (c.startsWith("InvalidParameter"))
        {
            return "图片不符合要求（8MB 以内、边长 128–5000 像素、jpg/png/webp）：" + m;
        }
        return "生成失败：" + (m.isEmpty() ? "未知原因" : m) + (c.isEmpty() ? "" : "（" + c + "）");
    }

    private void fail(SiteModelJob job, String code, String message)
    {
        job.setStatus(SiteModelJob.FAIL);
        job.setErrorCode(truncate(code, 100));
        job.setErrorMsg(truncate(translate(code, message), 1000));
        job.setFinishtime(new Date());
        jobMapper.updateJob(job);
        log.warn("混元生 3D 失败：job={} code={} message={}", job.getJobId(), code, message);
    }

    // ==================== 小工具 ====================

    private String requireOwnImage(String url, String label)
    {
        String value = trimToNull(url);
        if (value == null || !value.startsWith("https://") || imageMapper.countByUrl(value) == 0)
        {
            throw new ServiceException(label + "要从文件库里选（而且要是 https 地址），混元要能从公网拉到它");
        }
        return value;
    }

    private void putView(Map<String, String> images, String view, String url, String label)
    {
        if (trimToNull(url) != null)
        {
            images.put(view, requireOwnImage(url, label));
        }
    }

    /** 模型显示名：填了用填的，没填取正面图文件名（去扩展名） */
    static String jobName(String name, String frontUrl)
    {
        String value = trimToNull(name);
        if (value == null)
        {
            String file = frontUrl.split("\\?", 2)[0];
            file = file.substring(file.lastIndexOf('/') + 1);
            int dot = file.lastIndexOf('.');
            value = (dot > 0 ? file.substring(0, dot) : file) + "（照片生成）";
        }
        return truncate(value, 150);
    }

    private static Date startOfToday()
    {
        return Date.from(LocalDate.now(SHANGHAI).atStartOfDay(SHANGHAI).toInstant());
    }

    /** 取腾讯云密钥（同 SiteTranslateService）。⚠ 返回值不许出现在任何响应、任何日志里 */
    private Credentials credentials()
    {
        SiteStorage storage = storageService.selectForDisplay("tencent");
        String secretId = storage == null ? null : trimToNull(storage.getAccessKeyId());
        String secretKey = storageService.storedSecret("tencent");
        if (secretId == null || secretKey == null || secretKey.isBlank())
        {
            throw new ServiceException("还没配腾讯云密钥。混元生 3D 和文件存储共用同一对密钥，"
                    + "请先在「站点概览 → 文件存储」里把腾讯云 COS 配好");
        }
        return new Credentials(secretId, secretKey);
    }

    /** 取 {@code {"Response":{...}}} 里那一层 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> response(String body)
    {
        Map<String, Object> root = JSON.readValue(body, Map.class);
        Map<String, Object> response = map(root.get("Response"));
        if (response == null)
        {
            throw new ServiceException("腾讯云返回的格式不对");
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value)
    {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    private static String toJson(Object value)
    {
        return new String(JSON.writeValueAsBytes(value), StandardCharsets.UTF_8);
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String truncate(String value, int max)
    {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 密钥对。单独一个类型只为了让「这东西不许打日志」这件事在签名上看得见 */
    record Credentials(String secretId, String secretKey)
    {
    }
}
