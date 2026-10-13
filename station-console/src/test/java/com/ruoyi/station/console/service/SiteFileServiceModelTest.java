package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteModel;
import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.mapper.SiteImageMapper;
import com.ruoyi.station.console.mapper.SiteModelMapper;
import com.ruoyi.station.console.mapper.SiteVideoMapper;

/**
 * 3D 模型上传：白名单、落盘路径、provider 记录。
 *
 * <p>provider 用 local 跑在临时目录里，不碰 COS。tencent 那条路径的签名和上传
 * 已由 {@code CosClient} 那边覆盖，这里只验模型这一层的分流。</p>
 */
@ExtendWith(MockitoExtension.class)
class SiteFileServiceModelTest
{
    @TempDir
    Path tempDir;

    @Mock SiteImageMapper imageMapper;
    @Mock SiteVideoMapper videoMapper;
    @Mock SiteModelMapper modelMapper;
    @Mock SiteInfoService siteInfoService;
    @Mock CodeGenerator codeGenerator;
    @Mock SiteFolderService folderService;
    @Mock SiteStorageService storageService;

    @Spy StationProperties properties = new StationProperties();

    @InjectMocks SiteFileService service;

    @BeforeEach
    void setUp()
    {
        properties.getUpload().setDir(tempDir.toString());
        properties.getUpload().setBaseUrl("https://files.test");
    }

    private void stubHappyPath()
    {
        SiteInfo site = new SiteInfo();
        site.setShopCode("SHOP");
        site.setSiteCode("SITE");
        when(siteInfoService.require()).thenReturn(site);

        SiteStorage storage = new SiteStorage();
        storage.setProvider(SiteStorage.PROVIDER_LOCAL);
        when(storageService.requireActive()).thenReturn(storage);

        when(codeGenerator.nextModelId()).thenReturn("SMDL0000000007");
        when(folderService.resolveUploadFolder(eq(SiteFolderService.TYPE_MODEL), any())).thenReturn(null);
    }

    @Test
    void acceptsGlbAndStoresUnderModelFolder() throws Exception
    {
        stubHappyPath();
        MockMultipartFile file = new MockMultipartFile("file", "Valve-A.GLB",
                "model/gltf-binary", "glTF".getBytes());

        SiteModel saved = service.uploadModel(file, null, "tester");

        assertEquals("SMDL0000000007", saved.getModelId());
        assertEquals("Valve-A.GLB", saved.getModelName());
        assertEquals(SiteStorage.PROVIDER_LOCAL, saved.getProvider());
        assertEquals(4L, saved.getFileSize());
        assertTrue(saved.getObjectName().startsWith("model/"), saved.getObjectName());
        // 扩展名统一小写落盘，和图片视频一致
        assertTrue(saved.getObjectName().endsWith("/SMDL0000000007.glb"), saved.getObjectName());
        assertEquals("https://files.test/" + saved.getObjectName(), saved.getModelUrl());
        assertTrue(Files.exists(tempDir.resolve(saved.getObjectName())));
        verify(modelMapper).insertModel(saved);
    }

    @Test
    void rejectsGltfAndObj()
    {
        // 白名单校验在拿 SiteInfo 之后、拿存储之前；这里只让前者能过
        SiteInfo site = new SiteInfo();
        when(siteInfoService.require()).thenReturn(site);

        for (String name : new String[] { "a.gltf", "a.obj", "a.fbx", "a", "a.glb.exe" })
        {
            MockMultipartFile file = new MockMultipartFile("file", name, null, "x".getBytes());
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.uploadModel(file, null, "tester"), name);
            assertTrue(ex.getMessage().contains("glb"), ex.getMessage());
        }
        verify(modelMapper, never()).insertModel(any());
        verify(codeGenerator, never()).nextModelId();
    }

    @Test
    void rejectsEmptyFile()
    {
        when(siteInfoService.require()).thenReturn(new SiteInfo());
        MockMultipartFile empty = new MockMultipartFile("file", "a.glb", null, new byte[0]);
        assertThrows(ServiceException.class, () -> service.uploadModel(empty, null, "tester"));
        verify(modelMapper, never()).insertModel(any());
    }

    @Test
    void listAlwaysFiltersByCurrentProvider()
    {
        when(storageService.currentProvider()).thenReturn("tencent");
        SiteModel query = new SiteModel();
        query.setProvider("local"); // 客户端自己传的，必须被覆盖
        when(modelMapper.countList(any())).thenReturn(0);

        service.selectModelList(query, 1, 20);

        assertEquals("tencent", query.getProvider());
        verify(modelMapper, never()).selectList(any(), eq(0), eq(20));
    }

    @Test
    void countModelsOfBlankProviderIsZero()
    {
        assertEquals(0, service.countModelsOf(null));
        assertEquals(0, service.countModelsOf(" "));
        verify(modelMapper, never()).countByProvider(anyString());
    }
}
