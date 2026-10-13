package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.station.console.domain.SiteModelJob;
import com.ruoyi.station.console.mapper.SiteImageMapper;
import com.ruoyi.station.console.mapper.SiteModelJobMapper;

/**
 * 混元生 3D：提交前的校验、请求体、结果挑 glb、错误码翻译、超时判定。
 * 真正调腾讯云的那一段靠联调验（要积分），这里不打网络。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelGenServiceTest
{
    private static final String FRONT = "https://file.example.com/image/2026/09/26/SIMG0000000070.png";
    private static final String LEFT = "https://file.example.com/image/2026/09/26/SIMG0000000071.png";

    @Mock SiteModelJobMapper jobMapper;
    @Mock SiteImageMapper imageMapper;
    @Mock SiteInfoService siteInfoService;
    @Mock CodeGenerator codeGenerator;
    @Mock SiteFileService fileService;
    @Mock SiteStorageService storageService;

    @InjectMocks ModelGenService service;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "region", "ap-guangzhou");
        ReflectionTestUtils.setField(service, "modelVersion", "3.0");
        ReflectionTestUtils.setField(service, "dailyLimit", 20);
        when(imageMapper.countByUrl(anyString())).thenReturn(1);
        when(jobMapper.countSince(any())).thenReturn(0);
    }

    private static ModelGenService.Request req(String front)
    {
        return new ModelGenService.Request(front, null, null, null, null, null, null, null);
    }

    // ---------------- 提交前校验：都在调腾讯云之前拦下，不落库、不花积分 ----------------

    @Test
    void rejectsImageNotInOwnLibrary()
    {
        when(imageMapper.countByUrl(FRONT)).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.submit(req(FRONT), "u"));
        verify(jobMapper, never()).insertJob(any());
    }

    @Test
    void rejectsHttpAndBlank()
    {
        assertThrows(ServiceException.class, () -> service.submit(req(FRONT.replace("https://", "http://")), "u"));
        assertThrows(ServiceException.class, () -> service.submit(req("  "), "u"));
        assertThrows(ServiceException.class, () -> service.submit(null, "u"));
        verify(jobMapper, never()).insertJob(any());
    }

    @Test
    void rejectsOverDailyLimit()
    {
        when(jobMapper.countSince(any())).thenReturn(20);
        ServiceException e = assertThrows(ServiceException.class, () -> service.submit(req(FRONT), "u"));
        assertTrue(e.getMessage().contains("上限"), e.getMessage());
        verify(jobMapper, never()).insertJob(any());
    }

    @Test
    void rejectsWhenDisabled()
    {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThrows(ServiceException.class, () -> service.submit(req(FRONT), "u"));
    }

    @Test
    void rejectsWhenNoTencentKey()
    {
        when(storageService.selectForDisplay("tencent")).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.submit(req(FRONT), "u"));
        assertTrue(e.getMessage().contains("文件存储"), e.getMessage());
        verify(jobMapper, never()).insertJob(any());
    }

    // ---------------- 请求体 ----------------

    @Test
    void payloadFrontOnly()
    {
        Map<String, String> images = new LinkedHashMap<>();
        images.put("front", FRONT);
        Map<String, Object> p = ModelGenService.submitPayload(images, ModelGenService.FACE_STANDARD, false, "3.0");
        assertEquals(FRONT, p.get("ImageUrl"));
        assertFalse(p.containsKey("MultiViewImages"));
        assertEquals(150_000, p.get("FaceCount"));
        assertEquals(false, p.get("EnablePBR"));
        assertEquals("3.0", p.get("Model"));
        assertFalse(p.containsKey("Prompt"), "Prompt 和图片互斥，不能带");
    }

    @Test
    void payloadMultiView()
    {
        Map<String, String> images = new LinkedHashMap<>();
        images.put("front", FRONT);
        images.put("back", "https://x/back.png");
        images.put("left", LEFT);
        Map<String, Object> p = ModelGenService.submitPayload(images, ModelGenService.FACE_FINE, true, "3.1");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> views = (List<Map<String, String>>) p.get("MultiViewImages");
        // 顺序固定 left / right / back，字段名是 ViewType / ViewImageUrl
        assertEquals(2, views.size());
        assertEquals("left", views.get(0).get("ViewType"));
        assertEquals(LEFT, views.get(0).get("ViewImageUrl"));
        assertEquals("back", views.get(1).get("ViewType"));
        assertEquals(400_000, p.get("FaceCount"));
        assertEquals(true, p.get("EnablePBR"));
    }

    // ---------------- 结果挑 glb ----------------

    @Test
    void pickGlbByTypeCaseInsensitive()
    {
        List<Object> files = List.of(
                Map.of("Type", "OBJ", "Url", "https://r/a.zip"),
                Map.of("Type", "glb", "Url", "https://r/a.glb?sign=1"));
        assertEquals("https://r/a.glb?sign=1", ModelGenService.pickGlb(files));
    }

    @Test
    void pickGlbByPathWhenTypeMissing()
    {
        List<Object> files = List.of(Map.of("Url", "https://r/model.GLB?x=1"));
        assertEquals("https://r/model.GLB?x=1", ModelGenService.pickGlb(files));
    }

    @Test
    void pickGlbIgnoresHttpAndNonGlb()
    {
        assertNull(ModelGenService.pickGlb(List.of(Map.of("Type", "GLB", "Url", "http://r/a.glb"))));
        assertNull(ModelGenService.pickGlb(List.of(Map.of("Type", "OBJ", "Url", "https://r/a.obj"))));
        assertNull(ModelGenService.pickGlb(List.of()));
    }

    @Test
    void glbMagic()
    {
        assertTrue(ModelGenService.isGlb("glTF\u0002\u0000\u0000\u0000xxxx".getBytes()));
        assertFalse(ModelGenService.isGlb("<html>not a model".getBytes()));
        assertFalse(ModelGenService.isGlb(new byte[3]));
        assertFalse(ModelGenService.isGlb(null));
    }

    // ---------------- 错误码翻译 ----------------

    @Test
    void translateErrors()
    {
        assertTrue(ModelGenService.translate("AuthFailure.UnauthorizedOperation", "x").contains("QcloudAI3DFullAccess"));
        assertTrue(ModelGenService.translate("AuthFailure.SignatureFailure", "x").contains("文件存储"));
        assertTrue(ModelGenService.translate("FailedOperation.BillingError", "x").contains("积分"));
        assertTrue(ModelGenService.translate("ResourceInsufficient.Charge", "x").contains("积分"));
        assertTrue(ModelGenService.translate("FailedOperation", "账户已欠费").contains("积分"));
        assertTrue(ModelGenService.translate("ResourceUnavailable.NotExist", "x").contains("开通"));
        assertTrue(ModelGenService.translate("LimitExceeded", "x").contains("3 个"));
        assertTrue(ModelGenService.translate("InvalidParameterValue.ImageSize", "too big").contains("128"));
        assertEquals("生成失败：boom（FailedOperation.X）", ModelGenService.translate("FailedOperation.X", "boom"));
        assertEquals("生成失败：未知原因", ModelGenService.translate(null, null));
    }

    // ---------------- 任务名、超时 ----------------

    @Test
    void jobNameDefaultsToFrontFileName()
    {
        assertEquals("SIMG0000000070（照片生成）", ModelGenService.jobName(null, FRONT + "?x=1"));
        assertEquals("空气泵", ModelGenService.jobName("  空气泵 ", FRONT));
    }

    @Test
    void pollTimesOutOldJobWithoutCallingTencent() throws Exception
    {
        SiteModelJob job = new SiteModelJob();
        job.setJobId("SMJB0000000001");
        job.setTcJobId("tc-1");
        job.setStatus(SiteModelJob.RUN);
        job.setCreatetime(new Date(System.currentTimeMillis() - ModelGenService.JOB_TIMEOUT_MS - 60_000));

        service.pollOne(job, new ModelGenService.Credentials("id", "key"));

        ArgumentCaptor<SiteModelJob> saved = ArgumentCaptor.forClass(SiteModelJob.class);
        verify(jobMapper).updateJob(saved.capture());
        assertEquals(SiteModelJob.FAIL, saved.getValue().getStatus());
        assertTrue(saved.getValue().getErrorMsg().contains("超时"));
        assertTrue(saved.getValue().getFinishtime() != null);
    }

    @Test
    void stuckSubmittingFailsAfterFiveMinutesButFreshOneIsLeftAlone() throws Exception
    {
        SiteModelJob fresh = new SiteModelJob();
        fresh.setJobId("SMJB0000000002");
        fresh.setStatus(SiteModelJob.SUBMITTING);
        fresh.setCreatetime(new Date(System.currentTimeMillis() - 30_000));
        // 刚落库、submit 还在调腾讯云：轮询不能把它判死
        service.pollOne(fresh, new ModelGenService.Credentials("id", "key"));
        verify(jobMapper, never()).updateJob(any());

        SiteModelJob stuck = new SiteModelJob();
        stuck.setJobId("SMJB0000000003");
        stuck.setStatus(SiteModelJob.SUBMITTING);
        stuck.setCreatetime(new Date(System.currentTimeMillis() - ModelGenService.SUBMIT_STUCK_MS - 1_000));
        service.pollOne(stuck, new ModelGenService.Credentials("id", "key"));
        ArgumentCaptor<SiteModelJob> saved = ArgumentCaptor.forClass(SiteModelJob.class);
        verify(jobMapper).updateJob(saved.capture());
        assertEquals(SiteModelJob.FAIL, saved.getValue().getStatus());
        assertEquals("SubmitInterrupted", saved.getValue().getErrorCode());
    }

    @Test
    void responseRequiresEnvelope()
    {
        assertThrows(ServiceException.class, () -> ModelGenService.response("{\"foo\":1}"));
        assertEquals("abc", ModelGenService.response("{\"Response\":{\"JobId\":\"abc\"}}").get("JobId"));
    }
}
