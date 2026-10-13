package com.ruoyi.web.controller.inventory;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.service.IInventoryBatchService;

/**
 * 批次信息Controller
 */
@RestController
@RequestMapping("/inventory/batch")
public class InventoryBatchController extends BaseController
{
    private static final String DIRECT_WRITE_DISABLED =
            "禁止直接新增、修改或删除批次。批次只能随入库、出库、调拨及资产状态业务单据变更。";

    @Autowired
    private IInventoryBatchService batchService;

    /**
     * 查询批次列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryBatch batch)
    {
        startPage();
        batch.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryBatch> list = batchService.selectBatchList(batch);
        return getDataTable(list);
    }

    /**
     * 查询可用批次列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/available")
    public AjaxResult availableList(InventoryBatch batch)
    {
        batch.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryBatch> list = batchService.selectAvailableBatchList(batch);
        return success(list);
    }

    /**
     * 获取批次详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{batchCode}/{goodsCode}/{wareCode}/{qualityFlag}/{onhandFlag}")
    public AjaxResult getInfo(@PathVariable("batchCode") String batchCode,
            @PathVariable("goodsCode") String goodsCode, @PathVariable("wareCode") String wareCode,
            @PathVariable("qualityFlag") Integer qualityFlag,
            @PathVariable("onhandFlag") Integer onhandFlag)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryBatch batch = batchService.selectBatchByKey(
                shopCode, batchCode, goodsCode, wareCode,
                StockState.of(qualityFlag, onhandFlag));
        if (batch == null)
        {
            return error("批次不存在");
        }
        return success(batch);
    }

    /**
     * 新增批次
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "批次管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryBatch batch)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 修改批次
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "批次管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryBatch batch)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 改批次的存放货位。
     *
     * <p>这是 {@link #DIRECT_WRITE_DISABLED} 的<b>唯一例外</b>，而且只改货位这一个字段：
     * 数量、成本、状态是过账算出来的账，手工改会让批次和库存流水对不上；
     * 货位是纯管理信息，不参与任何计算，库管员挪了货本来就该能改。</p>
     *
     * <p>接口特意不复用 {@code PUT /inventory/batch}——那条走的是整个实体，
     * 前端多传一个字段就能改到不该改的列。</p>
     *
     * @param params position 新货位，传空表示清掉
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "批次货位", businessType = BusinessType.UPDATE)
    @PutMapping("/{batchCode}/{goodsCode}/{wareCode}/{qualityFlag}/{onhandFlag}/position")
    public AjaxResult updatePosition(@PathVariable("batchCode") String batchCode,
            @PathVariable("goodsCode") String goodsCode, @PathVariable("wareCode") String wareCode,
            @PathVariable("qualityFlag") Integer qualityFlag,
            @PathVariable("onhandFlag") Integer onhandFlag,
            @RequestBody Map<String, Object> params)
    {
        Object position = params == null ? null : params.get("position");
        return toAjax(batchService.updateBatchPosition(SecurityUtils.getBusinessShopCode(),
                batchCode, goodsCode, wareCode, StockState.of(qualityFlag, onhandFlag),
                position == null ? null : String.valueOf(position)));
    }

    /**
     * 删除批次
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "批次管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{batchCode}/{goodsCode}/{wareCode}/{qualityFlag}/{onhandFlag}")
    public AjaxResult remove(@PathVariable("batchCode") String batchCode,
            @PathVariable("goodsCode") String goodsCode, @PathVariable("wareCode") String wareCode,
            @PathVariable("qualityFlag") Integer qualityFlag,
            @PathVariable("onhandFlag") Integer onhandFlag)
    {
        return error(DIRECT_WRITE_DISABLED);
    }
}

