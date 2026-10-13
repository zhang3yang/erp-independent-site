package com.ruoyi.system.service.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.domain.DocumentDetail;
import com.ruoyi.system.domain.DocumentMain;
import com.ruoyi.system.domain.InventoryStocktakeItem;
import com.ruoyi.system.mapper.InventoryStocktakeMapper;

/**
 * 盘点期间冻结：盘点单没结束，单上的商品在这个仓库里不许过账。
 *
 * <p>不冻结会重复扣：清点到一半发走一批货，实盘数里已经少了这批，出库又扣一次，
 * 审核时差异算成盘亏，同一批货被扣两遍。反过来入库会被算成盘盈。</p>
 *
 * <p>只拦<b>过账</b>（审核），不拦建单。发货批次走到「完成」、采购入库审核、调拨审核
 * 都会在这里停下，提示是哪张盘点单，等盘点审核或作废后再点一次即可。
 * 冻结粒度是「仓库 + 商品」，不在单上的商品照常进出。</p>
 *
 * <p>盘点单自己生成的盘盈入库、盘亏出库按来源类型放行——它们就是在盘点单
 * 还是「待审核」的时候过账的。</p>
 */
@Component
public class StocktakeFreezeGuard
{
    @Autowired
    private InventoryStocktakeMapper stocktakeMapper;

    public void requireNotFrozen(DocumentMain document, String direction, List<DocumentDetail> details)
    {
        if (InventoryDocumentTypes.STOCKTAKE_SOURCE.equals(document.getSourceDocType()))
        {
            return;
        }
        Map<String, Set<String>> goodsByWare = new LinkedHashMap<>();
        for (DocumentDetail detail : details)
        {
            if ("I".equals(direction))
            {
                add(goodsByWare, valueOrDefault(detail.getWareCode(), document.getToWareCode()), detail.getGoodsCode());
            }
            else if ("O".equals(direction))
            {
                add(goodsByWare, document.getFromWareCode(), detail.getGoodsCode());
            }
            else
            {
                // 调拨两头都算：调出去会让盘点仓少货，调进来会多货
                add(goodsByWare, document.getFromWareCode(), detail.getGoodsCode());
                add(goodsByWare, document.getToWareCode(), detail.getGoodsCode());
            }
        }
        for (Map.Entry<String, Set<String>> entry : goodsByWare.entrySet())
        {
            List<InventoryStocktakeItem> hits = stocktakeMapper.selectFrozenGoods(
                    document.getShopCode(), entry.getKey(), entry.getValue(), null);
            if (hits != null && !hits.isEmpty())
            {
                InventoryStocktakeItem hit = hits.get(0);
                throw new ServiceException("商品'" + valueOrDefault(hit.getGoodsName(), hit.getGoodsCode())
                        + "'正在盘点（盘点单 " + hit.getStocktakeCode() + "），盘点审核或作废之前，"
                        + "它在这个仓库的单据都不能审核（出入库、调拨、成本调整都算）。"
                        + "单据已保存，到「库存盘点」把这张盘点单审完或作废后再来审核即可");
            }
        }
    }

    private void add(Map<String, Set<String>> goodsByWare, String wareCode, String goodsCode)
    {
        if (isBlank(wareCode) || isBlank(goodsCode))
        {
            return;
        }
        goodsByWare.computeIfAbsent(wareCode, k -> new LinkedHashSet<>()).add(goodsCode);
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private String valueOrDefault(String value, String defaultValue)
    {
        return isBlank(value) ? defaultValue : value;
    }
}
