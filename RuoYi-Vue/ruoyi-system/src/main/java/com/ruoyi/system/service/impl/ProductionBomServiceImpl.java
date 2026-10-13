package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ProductionBom;
import com.ruoyi.system.domain.ProductionBomItem;
import com.ruoyi.system.mapper.InventoryGoodsMapper;
import com.ruoyi.system.mapper.ProductionBomItemMapper;
import com.ruoyi.system.mapper.ProductionBomMapper;
import com.ruoyi.system.service.IProductionBomService;

/**
 * 产品配方（BOM）主数据服务。
 *
 * <p>配方是「下次建工单时按这套来」的模板，本身不动库存、不产生成本，所以没有状态机，
 * 只有启用/停用。工单上的物料是建单那一刻的快照，改配方不会回头影响已建工单。</p>
 */
@Service
public class ProductionBomServiceImpl implements IProductionBomService
{
    private static final String STATUS_ENABLED = "启用";
    private static final String STATUS_DISABLED = "停用";

    @Autowired private ProductionBomMapper bomMapper;
    @Autowired private ProductionBomItemMapper itemMapper;
    @Autowired private InventoryDocumentCodeGenerator codeGenerator;
    /** 成品和物料都得在商品档案里，配方本身不动库存，不在这里拦就得等领料时才发现 */
    @Autowired private InventoryGoodsMapper goodsMapper;

    @Override
    public List<ProductionBom> selectBomList(ProductionBom query)
    {
        return bomMapper.selectBomList(query);
    }

    @Override
    public ProductionBom selectBomByCode(String shopCode, String bomCode)
    {
        ProductionBom bom = bomMapper.selectBomByCode(shopCode, bomCode);
        if (bom != null) bom.setItems(itemMapper.selectByBomCode(shopCode, bomCode));
        return bom;
    }

    @Override
    public ProductionBom selectEnabledBomByProduct(String shopCode, String productCode)
    {
        if (productCode == null || productCode.trim().isEmpty()) return null;
        ProductionBom bom = bomMapper.selectBomByProduct(shopCode, productCode.trim());
        if (bom == null || !STATUS_ENABLED.equals(bom.getStatus())) return null;
        bom.setItems(itemMapper.selectByBomCode(shopCode, bom.getBomCode()));
        return bom;
    }

    @Override
    public List<String> selectBomProductCodes(String shopCode)
    {
        return bomMapper.selectProductCodes(shopCode);
    }

    @Override
    @Transactional
    public int insertBom(ProductionBom bom)
    {
        validate(bom);
        // uk_shop_bom_product 会兜底，但先查一次能给出「这个成品已经有配方了」这种看得懂的提示
        if (bomMapper.selectBomByProduct(bom.getShopCode(), bom.getProductCode()) != null)
        {
            throw new ServiceException("商品「" + bom.getProductName() + "」已经有配方了，请直接修改那一套");
        }
        bom.setBomCode(codeGenerator.nextBomCode());
        bomMapper.insertBom(bom);
        saveItems(bom);
        return 1;
    }

    @Override
    @Transactional
    public int updateBom(ProductionBom bom)
    {
        validate(bom);
        ProductionBom existing = bomMapper.selectBomByCode(bom.getShopCode(), bom.getBomCode());
        if (existing == null) throw new ServiceException("配方不存在");
        // 成品是配方的身份，换成品等于换一套配方；Mapper 也不更新 productcode
        if (!existing.getProductCode().equals(bom.getProductCode()))
        {
            throw new ServiceException("配方对应的成品不能修改，请为新成品另建一套配方");
        }
        itemMapper.deleteByBomCode(bom.getShopCode(), bom.getBomCode());
        saveItems(bom);
        return bomMapper.updateBom(bom);
    }

    @Override
    @Transactional
    public int deleteBom(String shopCode, String bomCode)
    {
        ProductionBom existing = bomMapper.selectBomByCode(shopCode, bomCode);
        if (existing == null) throw new ServiceException("配方不存在");
        itemMapper.deleteByBomCode(shopCode, bomCode);
        return bomMapper.deleteBom(shopCode, bomCode);
    }

    private void validate(ProductionBom bom)
    {
        if (bom == null) throw new ServiceException("配方不能为空");
        if (isBlank(bom.getShopCode())) throw new ServiceException("商家编码不能为空");
        if (isBlank(bom.getProductCode())) throw new ServiceException("成品编码不能为空");
        if (isBlank(bom.getProductName())) throw new ServiceException("成品名称不能为空");
        if (bom.getStatus() == null || bom.getStatus().trim().isEmpty()) bom.setStatus(STATUS_ENABLED);
        if (!STATUS_ENABLED.equals(bom.getStatus()) && !STATUS_DISABLED.equals(bom.getStatus()))
        {
            throw new ServiceException("配方状态只能是启用或停用");
        }
        if (bom.getItems() == null) bom.setItems(new ArrayList<>());
        if (bom.getItems().isEmpty()) throw new ServiceException("配方至少要有一行物料");
    }

    /** 生成明细代码和行号后落库。同一物料不允许两行——那是录入错误，合并成一行改单耗即可。 */
    private void saveItems(ProductionBom bom)
    {
        Set<String> seen = new HashSet<>();
        int line = 1;
        requireGoods(bom.getShopCode(), bom.getProductCode(), "成品");
        for (ProductionBomItem item : bom.getItems())
        {
            if (item == null || isBlank(item.getGoodsCode())) throw new ServiceException("物料编码不能为空");
            if (item.getQtyPerUnit() == null || item.getQtyPerUnit().signum() <= 0)
            {
                throw new ServiceException("物料「" + item.getGoodsName() + "」的单位用量必须大于0");
            }
            if (!seen.add(item.getGoodsCode().trim()))
            {
                throw new ServiceException("物料「" + item.getGoodsName() + "」重复了，同一个物料合并成一行改单耗即可");
            }
            // 自己做自己不是配方：带进工单后完工时同一张单既消耗又入库同一个商品，成本算不出来
            if (item.getGoodsCode().trim().equals(bom.getProductCode().trim()))
            {
                throw new ServiceException("成品「" + bom.getProductName() + "」不能出现在自己的配方里");
            }
            requireGoods(bom.getShopCode(), item.getGoodsCode(), "物料");
            item.setShopCode(bom.getShopCode());
            item.setBomCode(bom.getBomCode());
            item.setBomDetailCode(codeGenerator.nextBomDetailCode());
            item.setLineNo(line++);
        }
        itemMapper.batchInsert(bom.getItems());
    }

    /**
     * 商品必须在商品档案里。
     *
     * <p>配方是模板，本身不动库存，编码错了当场看不出来——要等它被带进工单、领料过账时
     * 才报「商品不存在」，那时候已经拖了几道工序。</p>
     */
    private void requireGoods(String shopCode, String goodsCode, String role)
    {
        if (goodsMapper.selectGoodsByCode(shopCode, goodsCode.trim()) == null)
        {
            throw new ServiceException(role + "'" + goodsCode.trim() + "'不在商品档案里，请先建档或重新选择");
        }
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
