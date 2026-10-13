package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.InventoryGoods;
import com.ruoyi.system.domain.ProductionBom;
import com.ruoyi.system.domain.ProductionBomItem;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.ProductionBomItemMapper;
import com.ruoyi.system.mapper.ProductionBomMapper;

/**
 * 产品配方主数据的录入规则测试。
 *
 * <p>配方不动库存、不产生成本，风险都在录入口径上：一个成品一套、同料不重复、
 * 单耗必须为正、成品不可改。停用的配方不参与建单时的自动带出。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductionBomServiceImplTest
{
    private static final String SHOP = "sp001";
    private static final String BOM = "SCPF001";
    private static final String PRODUCT = "P001";
    private static final String USER = "tester";

    @Mock private ProductionBomMapper bomMapper;
    @Mock private ProductionBomItemMapper itemMapper;
    @Mock private InventoryDocumentCodeGenerator codeGenerator;
    @Mock private InventoryGoodsMapper goodsMapper;

    @InjectMocks private ProductionBomServiceImpl service;

    @BeforeEach
    void setUp()
    {
        // 成品和物料默认都在商品档案里；要验「档案里没有」的用例自己覆盖
        lenient().when(goodsMapper.selectGoodsByCode(eq(SHOP), anyString())).thenReturn(new InventoryGoods());
    }

    private ProductionBom bom(String status, List<ProductionBomItem> items)
    {
        ProductionBom bom = new ProductionBom();
        bom.setShopCode(SHOP);
        bom.setProductCode(PRODUCT);
        bom.setProductName("成品A");
        bom.setStatus(status);
        bom.setCreateUser(USER);
        bom.setItems(new ArrayList<>(items));
        return bom;
    }

    private ProductionBomItem item(String goodsCode, String qtyPerUnit)
    {
        ProductionBomItem item = new ProductionBomItem();
        item.setGoodsCode(goodsCode);
        item.setGoodsName(goodsCode);
        item.setQtyPerUnit(new BigDecimal(qtyPerUnit));
        return item;
    }

    /** 建配方：生成配方号和明细号，行号从 1 连续排 */
    @Test
    @SuppressWarnings("unchecked")
    void insertGeneratesCodesAndLineNumbers()
    {
        when(codeGenerator.nextBomCode()).thenReturn(BOM);
        when(codeGenerator.nextBomDetailCode()).thenReturn("SCPFMX001", "SCPFMX002");
        ProductionBom input = bom("启用", Arrays.asList(item("M001", "2"), item("M002", "0.5")));

        service.insertBom(input);

        assertEquals(BOM, input.getBomCode());
        ArgumentCaptor<List<ProductionBomItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(captor.capture());
        List<ProductionBomItem> saved = captor.getValue();
        assertEquals("SCPFMX001", saved.get(0).getBomDetailCode());
        assertEquals(1, saved.get(0).getLineNo());
        assertEquals(BOM, saved.get(0).getBomCode());
        assertEquals(SHOP, saved.get(0).getShopCode());
        assertEquals(2, saved.get(1).getLineNo());
    }

    /** 一个成品一套配方：再建一套直接拒绝，给出能看懂的提示而不是等唯一索引报错 */
    @Test
    void insertRejectsSecondBomForSameProduct()
    {
        when(bomMapper.selectBomByProduct(SHOP, PRODUCT)).thenReturn(bom("启用", Collections.emptyList()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Collections.singletonList(item("M001", "2")))));
        assertTrue(error.getMessage().contains("已经有配方"));
        verify(bomMapper, never()).insertBom(any());
    }

    /**
     * 成品不能出现在自己的配方里。前端拦过一道，服务端也得拦——走接口能绕过前端，
     * 而这种配方一旦带进工单，完工时同一张单既消耗又入库同一个商品，成本算不出来。
     */
    @Test
    void insertRejectsSelfReferencingMaterial()
    {
        when(codeGenerator.nextBomCode()).thenReturn(BOM);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Arrays.asList(item("M001", "2"), item(PRODUCT, "1")))));
        assertTrue(error.getMessage().contains("不能出现在自己的配方里"), error.getMessage());
        verify(itemMapper, never()).batchInsert(any());
    }

    /** 物料编码不在商品档案里就拒：配方不动库存，不在这里拦要等领料过账才发现 */
    @Test
    void insertRejectsMaterialMissingFromGoodsArchive()
    {
        when(codeGenerator.nextBomCode()).thenReturn(BOM);
        when(goodsMapper.selectGoodsByCode(SHOP, "M404")).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Collections.singletonList(item("M404", "2")))));
        assertTrue(error.getMessage().contains("不在商品档案里"), error.getMessage());
        verify(itemMapper, never()).batchInsert(any());
    }

    /** 同一个物料两行是录入错误，合并成一行改单耗即可 */
    @Test
    void insertRejectsDuplicateMaterial()
    {
        when(codeGenerator.nextBomCode()).thenReturn(BOM);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Arrays.asList(item("M001", "2"), item("M001", "3")))));
        assertTrue(error.getMessage().contains("重复"));
        verify(itemMapper, never()).batchInsert(any());
    }

    /** 配方一行物料都没有就不成其为配方 */
    @Test
    void insertRejectsEmptyBom()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Collections.emptyList())));
        assertTrue(error.getMessage().contains("至少要有一行物料"));
    }

    @Test
    void insertRejectsNonPositiveQtyPerUnit()
    {
        when(codeGenerator.nextBomCode()).thenReturn(BOM);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.insertBom(bom("启用", Collections.singletonList(item("M001", "0")))));
        assertTrue(error.getMessage().contains("单位用量必须大于0"));
    }

    /** 改配方先清后插，成品本身不能换——换成品等于换一套配方 */
    @Test
    void updateReplacesItemsAndKeepsProduct()
    {
        ProductionBom existing = bom("启用", Collections.emptyList());
        existing.setBomCode(BOM);
        when(bomMapper.selectBomByCode(SHOP, BOM)).thenReturn(existing);
        when(codeGenerator.nextBomDetailCode()).thenReturn("SCPFMX003");
        ProductionBom input = bom("停用", Collections.singletonList(item("M003", "1")));
        input.setBomCode(BOM);

        service.updateBom(input);

        verify(itemMapper).deleteByBomCode(SHOP, BOM);
        verify(itemMapper).batchInsert(any());
        verify(bomMapper).updateBom(input);
    }

    @Test
    void updateRejectsProductChange()
    {
        ProductionBom existing = bom("启用", Collections.emptyList());
        existing.setBomCode(BOM);
        when(bomMapper.selectBomByCode(SHOP, BOM)).thenReturn(existing);
        ProductionBom input = bom("启用", Collections.singletonList(item("M001", "1")));
        input.setBomCode(BOM);
        input.setProductCode("P999");

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateBom(input));
        assertTrue(error.getMessage().contains("成品不能修改"));
        verify(itemMapper, never()).deleteByBomCode(anyString(), anyString());
    }

    /**
     * 新建配方选成品时要锁掉所有已有配方的成品，停用的也算——它占着那个成品。
     * 这份清单不能用列表页那一页，翻页之后就漏了。
     */
    @Test
    void selectBomProductCodesReturnsEveryProductWithBom()
    {
        when(bomMapper.selectProductCodes(SHOP)).thenReturn(Arrays.asList("P001", "P002"));

        assertEquals(Arrays.asList("P001", "P002"), service.selectBomProductCodes(SHOP));
    }

    /** 建工单自动带出：启用的带出来，停用的当作没有 */
    @Test
    void selectEnabledBomByProductReturnsOnlyEnabled()
    {
        ProductionBom enabled = bom("启用", Collections.emptyList());
        enabled.setBomCode(BOM);
        when(bomMapper.selectBomByProduct(SHOP, PRODUCT)).thenReturn(enabled);
        when(itemMapper.selectByBomCode(SHOP, BOM)).thenReturn(
                Collections.singletonList(item("M001", "2")));

        ProductionBom found = service.selectEnabledBomByProduct(SHOP, PRODUCT);
        assertNotNull(found);
        assertEquals(1, found.getItems().size());
    }

    @Test
    void selectEnabledBomByProductSkipsDisabled()
    {
        ProductionBom disabled = bom("停用", Collections.emptyList());
        disabled.setBomCode(BOM);
        when(bomMapper.selectBomByProduct(SHOP, PRODUCT)).thenReturn(disabled);

        assertNull(service.selectEnabledBomByProduct(SHOP, PRODUCT));
        verify(itemMapper, never()).selectByBomCode(anyString(), anyString());
    }

    /** 商品没有配方时返回 null，不报错——工单本来就可以不用配方 */
    @Test
    void selectEnabledBomByProductReturnsNullWhenMissing()
    {
        when(bomMapper.selectBomByProduct(SHOP, PRODUCT)).thenReturn(null);
        assertNull(service.selectEnabledBomByProduct(SHOP, PRODUCT));
    }
}
