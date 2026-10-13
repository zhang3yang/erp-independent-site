package com.ruoyi.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.service.IInventoryWarningService;

@Component("inventoryWarningTask")
public class InventoryWarningTask
{
    private static final Logger log = LoggerFactory.getLogger(InventoryWarningTask.class);

    @Autowired
    private IInventoryWarningService warningService;

    public void checkWarnings()
    {
        log.info("开始执行库存低库存预警检查");
        log.info("库存低库存预警检查完成：{}", warningService.checkAllWarnings());
    }
}
