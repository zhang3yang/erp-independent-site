package com.ruoyi.web.controller.inventory;

import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.enums.OnhandFlag;
import com.ruoyi.system.enums.QualityFlag;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryStock;
import com.ruoyi.system.service.IInventoryStockService;

/**
 * 库存Controller
 */
@RestController
@RequestMapping("/inventory/stock")
public class InventoryStockController extends BaseController
{
    private static final String DIRECT_WRITE_DISABLED =
            "禁止直接修改库存数据。库存只能通过入库、出库、调拨、借出、归还、损坏、维修等业务单据变更；账实不符请走「库存盘点」，审核后自动生成盘盈入库/盘亏出库单。";

    @Autowired
    private IInventoryStockService stockService;

    /**
     * 查询库存列表
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventoryStock stock)
    {
        startPage();
        stock.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventoryStock> list = stockService.selectStockList(stock);
        return getDataTable(list);
    }

    /**
     * 查询不在库的库存，供归还已借出商品选择使用。
     *
     * <p>二维模型下「不在库」这一格里借出在外的货、领到车间的在制品、送外维修中的货
     * 是混在一起的，库存表分不出来。这里只能把不在库的量全列出来，由操作员按实际
     * 归还的东西挑；要精确到「谁借了什么」得去翻借出单。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/borrowed")
    public AjaxResult borrowedList()
    {
        return success(selectPositiveOffhandStock(null));
    }

    /**
     * 查询不在库的不良品，供归还已送修商品选择使用。
     *
     * <p>送修的必然是不良品，所以这里能比借出清单多筛掉一层良品；但仍然和
     * 「借出在外的坏货」混在同一格。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/repairing")
    public AjaxResult repairingList()
    {
        return success(selectPositiveOffhandStock(QualityFlag.DEFECTIVE.getCode()));
    }

    /**
     * 查询在库的不良品，供「损坏去送修」挑选使用。
     *
     * <p>损坏登记会把货从良品翻到不良品，翻完还在库里，就是这一格。送修单从这里出货。</p>
     *
     * <p>注意：厂内维修（货没离库）期间，货同样记在这一格里，库存表分不出「待送修」
     * 和「厂内维修中」。所以这里列出来的量可能包含正在厂内维修的货，挑的时候要看送修单。</p>
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/damaged")
    public AjaxResult damagedList()
    {
        InventoryStock query = new InventoryStock();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setOnhandFlag(OnhandFlag.IN.getCode());
        query.setQualityFlag(QualityFlag.DEFECTIVE.getCode());
        List<InventoryStock> list = stockService.selectStockList(query);
        list.removeIf(stock -> stock.getPhysicalQty() == null
                || stock.getPhysicalQty().compareTo(BigDecimal.ZERO) <= 0);
        return success(list);
    }

    /** 查有量的不在库库存；qualityFlag 传 null 表示良品不良品都要 */
    private List<InventoryStock> selectPositiveOffhandStock(Integer qualityFlag)
    {
        InventoryStock query = new InventoryStock();
        query.setShopCode(SecurityUtils.getBusinessShopCode());
        query.setOnhandFlag(OnhandFlag.OUT.getCode());
        query.setQualityFlag(qualityFlag);
        List<InventoryStock> list = stockService.selectStockList(query);
        list.removeIf(stock -> stock.getPhysicalQty() == null
                || stock.getPhysicalQty().compareTo(BigDecimal.ZERO) <= 0);
        return list;
    }

    /**
     * 获取库存详细信息
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/detail")
    public AjaxResult getInfo(@RequestParam("goodsCode") String goodsCode,
            @RequestParam("wareCode") String wareCode,
            @RequestParam("qualityFlag") Integer qualityFlag,
            @RequestParam("onhandFlag") Integer onhandFlag)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventoryStock stock = stockService.selectStockByKey(shopCode, goodsCode, wareCode,
                StockState.of(qualityFlag, onhandFlag));
        if (stock == null)
        {
            return error("库存记录不存在");
        }
        return success(stock);
    }

    /**
     * 新增库存记录
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventoryStock stock)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 修改库存记录
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventoryStock stock)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 调整库存数量
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存管理", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public AjaxResult adjust(@RequestParam("goodsCode") String goodsCode,
            @RequestParam("wareCode") String wareCode,
            @RequestParam("qualityFlag") Integer qualityFlag,
            @RequestParam("onhandFlag") Integer onhandFlag,
            @RequestParam("adjustQty") BigDecimal adjustQty,
            @RequestParam(value = "costAmount", defaultValue = "0") BigDecimal costAmount)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 删除库存记录
     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "库存管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{goodsCodes}")
    public AjaxResult remove(@PathVariable String[] goodsCodes)
    {
        return error(DIRECT_WRITE_DISABLED);
    }
}

