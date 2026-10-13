package com.ruoyi.web.controller.inventory;

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
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventorySerial;
import com.ruoyi.system.service.IInventorySerialService;

/**
 * 序列号Controller
 */
@RestController
@RequestMapping("/inventory/serial")
public class InventorySerialController extends BaseController
{
    private static final String DIRECT_WRITE_DISABLED =
            "禁止直接新增或修改序列号。序列号只能随入库、出库、调拨及资产状态业务单据变更。";

    @Autowired
    private IInventorySerialService serialService;

    /**
     * 查询序列号列表     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/list")
    public TableDataInfo list(InventorySerial serial)
    {
        startPage();
        serial.setShopCode(SecurityUtils.getBusinessShopCode());
        List<InventorySerial> list = serialService.selectSerialList(serial);
        return getDataTable(list);
    }

    /**
     * 查询在库序列号列表     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/instock")
    public AjaxResult inStockList(@RequestParam(value = "goodsCode", required = false) String goodsCode,
            @RequestParam(value = "wareCode", required = false) String wareCode,
            @RequestParam(value = "qualityFlag", required = false) Integer qualityFlag,
            @RequestParam(value = "onhandFlag", required = false) Integer onhandFlag)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        // 不传状态时默认只列可用库存里的序列号
        StockState state = qualityFlag == null && onhandFlag == null
                ? StockState.available() : StockState.of(qualityFlag, onhandFlag);
        List<InventorySerial> list = serialService.selectInStockSerialList(shopCode, goodsCode, wareCode, state);
        return success(list);
    }

    /**
     * 获取序列号详细信息     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @GetMapping("/{serialNo}")
    public AjaxResult getInfo(@PathVariable("serialNo") String serialNo)
    {
        String shopCode = SecurityUtils.getBusinessShopCode();
        InventorySerial serial = serialService.selectSerialByKey(shopCode, serialNo);
        if (serial == null)
        {
            return error("序列号不存在");
        }
        return success(serial);
    }

    /**
     * 新增序列号     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "序列号管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InventorySerial serial)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 批量新增序列号     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "序列号管理", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult batchAdd(@Validated @RequestBody List<InventorySerial> serials)
    {
        return error(DIRECT_WRITE_DISABLED);
    }

    /**
     * 修改序列号     */
    @PreAuthorize("@ss.hasBusinessShop()")
    @Log(title = "序列号管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody InventorySerial serial)
    {
        return error(DIRECT_WRITE_DISABLED);
    }
}

