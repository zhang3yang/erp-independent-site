package com.ruoyi.system.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.InventoryLedger;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.system.constant.InventoryDocumentTypes;
import com.ruoyi.system.mapper.InventoryLedgerMapper;
import com.ruoyi.system.service.IInventoryLedgerService;
import com.ruoyi.system.utils.InventoryLedgerRemarkResolver;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 库存流水Service业务层处理
 */
@Service
public class InventoryLedgerServiceImpl implements IInventoryLedgerService
{
    private static final DateTimeFormatter SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private InventoryLedgerMapper ledgerMapper;

    @Autowired
    private InventoryDocumentCodeGenerator documentCodeGenerator;

    @Autowired
    private StaffNameResolver staffNameResolver;

    /**
     * 查询库存流水
     */
    @Override
    public InventoryLedger selectLedgerByCode(String shopCode, String ledgerCode, String tableSuffix)
    {
        tableSuffix = validateTableSuffix(tableSuffix);
        return ledgerMapper.selectLedgerByCode(shopCode, ledgerCode, tableSuffix);
    }

    /**
     * 查询库存流水列表。
     *
     * <p>流水按 operate_time 分月表。指定了 {@code tableSuffix} 或 {@code operateTime}
     * 就只查那一张月表；给了时间区间就查区间覆盖到的月表；都不给则查全部现存月表。</p>
     *
     * <p>最后这条以前是「只查当月」——库存流水是用来追溯「库存为什么变了」的，跨月之后
     * 追不到上个月，这个模块就等于废了。</p>
     */
    @Override
    public List<InventoryLedger> selectLedgerList(InventoryLedger ledger)
    {
        List<InventoryLedger> ledgers = queryLedgers(ledger);
        // 兼容历史资产流水中沿用“调拨入库/调拨出库”的技术备注，查询时展示为业务含义。
        for (InventoryLedger item : ledgers)
        {
            // 兼容修复前已落库的补发流水：其物理类型仍是 CK，但来源类型 XSBF 足以
            // 唯一识别业务含义，查询时统一展示为“销售补发出库”。
            if (InventoryDocumentTypes.SALES_RESHIP_SOURCE.equals(item.getSourceDocType()))
            {
                item.setDocTypeName("销售补发出库");
            }
            String businessRemark = InventoryLedgerRemarkResolver.resolve(item.getDocType(),
                    item.getStockState(), item.getChangeQty());
            if (businessRemark != null)
            {
                item.setRemark(businessRemark);
            }
        }
        fillOperatorNames(ledgers);
        return ledgers;
    }

    /**
     * 把每行的操作人登录名解析成员工姓名（昵称）填进 operatorName，供列表展示。
     *
     * <p>operator 存的是 {@code SecurityUtils.getUsername()}（登录名），一次批量查 sys_user
     * 把用到的登录名都换成昵称，避免逐行查库。查不到的（如已删除的账号）回退显示原登录名，
     * 不留空。</p>
     */
    private void fillOperatorNames(List<InventoryLedger> ledgers)
    {
        if (ledgers == null || ledgers.isEmpty())
        {
            return;
        }
        Set<String> operators = new LinkedHashSet<>();
        for (InventoryLedger item : ledgers)
        {
            if (item.getOperator() != null && !item.getOperator().isEmpty())
            {
                operators.add(item.getOperator());
            }
        }
        if (operators.isEmpty())
        {
            return;
        }
        Map<String, String> nameByLogin = staffNameResolver.nickNamesByLogin(operators);
        for (InventoryLedger item : ledgers)
        {
            String op = item.getOperator();
            // 查不到就回退显示原登录名，宁可看到 code 也不要空白
            item.setOperatorName(op == null ? null : nameByLogin.getOrDefault(op, op));
        }
    }

    /** 按调用方给的月份线索决定查一张月表还是跨月查。 */
    private List<InventoryLedger> queryLedgers(InventoryLedger ledger)
    {
        if (ledger.getTableSuffix() != null && !ledger.getTableSuffix().isEmpty())
        {
            ledger.setTableSuffix(validateTableSuffix(ledger.getTableSuffix()));
            return ledgerMapper.selectLedgerList(ledger);
        }
        if (ledger.getOperateTime() != null)
        {
            ledger.setTableSuffix(getTableSuffixByBusinessDate(ledger.getOperateTime()));
            return ledgerMapper.selectLedgerList(ledger);
        }

        // 月表清单必须在分页之外查：startPage() 之后的第一条 SQL 会把分页参数吃掉，
        // 让这条元数据查询吃走的话，下面真正的列表查询就变成不分页了。
        List<String> existing = PageUtils.withoutPaging(() -> ledgerMapper.selectLedgerTableSuffixes());
        List<String> suffixes = MonthTableSuffix.queryRange(existing,
                ledger.getOperateTimeBegin(), ledger.getOperateTimeEnd());
        if (suffixes.isEmpty())
        {
            // 一条 SQL 都不发就直接返回时，必须把 startPage() armed 的分页参数清掉。
            // 它是线程变量，留在那儿会被这个线程处理的下一个请求的第一条 SQL 吃掉。
            PageUtils.clearPage();
            return new ArrayList<>();
        }
        return ledgerMapper.selectLedgerListAcrossMonths(ledger, suffixes);
    }

    /**
     * 新增库存流水
     */
    @Override
    public int insertLedger(InventoryLedger ledger)
    {
        if (ledger.getTableSuffix() == null || ledger.getTableSuffix().isEmpty())
        {
            ledger.setTableSuffix(getCurrentTableSuffix());
        }
        // 流水号始终由服务端生成，忽略客户端传入值。
        ledger.setLedgerCode(generateLedgerCode());
        return ledgerMapper.insertLedger(ledger);
    }

    /**
     * 批量新增库存流水
     */
    @Override
    public int batchInsertLedger(List<InventoryLedger> ledgers)
    {
        if (ledgers == null || ledgers.isEmpty())
        {
            return 0;
        }
        // 设置默认月表后缀和流水号
        for (InventoryLedger ledger : ledgers)
        {
            if (ledger.getTableSuffix() == null || ledger.getTableSuffix().isEmpty())
            {
                ledger.setTableSuffix(getCurrentTableSuffix());
            }
            // 流水号始终由服务端生成，忽略客户端传入值。
            ledger.setLedgerCode(generateLedgerCode());
        }
        return ledgerMapper.batchInsertLedger(ledgers, ledgers.get(0).getTableSuffix());
    }

    /** 生成库存流水号：KCLSH + 小时级 Base10 数字编码。 */
    @Override
    public String generateLedgerCode()
    {
        return documentCodeGenerator.nextLedgerCode();
    }

    /**
     * 获取当前月表后缀
     */
    private String getCurrentTableSuffix()
    {
        return LocalDate.now().format(SUFFIX_FORMAT);
    }

    private String getTableSuffixByBusinessDate(Date businessDate)
    {
        return businessDate.toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().format(SUFFIX_FORMAT);
    }

    private String validateTableSuffix(String tableSuffix)
    {
        if (tableSuffix == null || tableSuffix.isEmpty()) return getCurrentTableSuffix();
        if (!tableSuffix.matches("^\\d{6}$"))
        {
            throw new ServiceException("月表后缀格式不正确，必须是yyyyMM格式");
        }
        try
        {
            LocalDate.parse(tableSuffix + "01", DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        catch (Exception e)
        {
            throw new ServiceException("月表后缀不是有效的年月");
        }
        return tableSuffix;
    }
}
