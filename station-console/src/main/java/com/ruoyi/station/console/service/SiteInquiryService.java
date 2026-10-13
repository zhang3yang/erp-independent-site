package com.ruoyi.station.console.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.mapper.SiteInquiryMapper;

/**
 * 站点客户询盘（后台管理用）。
 *
 * <h3>⚠ 少了一个方法：{@code convertToBuyer}</h3>
 * <p>ERP 那版有「询盘转成 ERP 客户」——往 {@code sdsshop.u_m_buyer} 里建一条客户档案，
 * 归属业务员＝操作人。<b>那是一条互通，整个删掉</b>
 * （docs/独立站与ERP的边界.md §3.2：「询盘转 ERP 客户」这个动作删掉）。</p>
 *
 * <p>站点自己的客户管理一期不做（§11：留位置 = 菜单 + 路由 + 空页面，不建表），
 * 所以现在没有任何「转客户」的去处。{@code buyercode} 那一列留着、空着。</p>
 *
 * <h3>写操作前不需要再校验站点归属</h3>
 * <p>ERP 那版每个写操作前都要 {@code requireOwnSite}：底层只按 {@code (shopCode, id)} 定位，
 * 少了那一道，拿一个隔壁站的询盘 id 过来就能改它。这里一台机器一个站，
 * 库里的询盘就是本站的询盘，不存在「隔壁站的 id」。</p>
 */
@Service
public class SiteInquiryService
{
    /** 每页条数上限。管理台自己用，给得比对外接口宽 */
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Autowired
    private SiteInquiryMapper inquiryMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    /**
     * 询盘列表。
     *
     * @return {@code {total, rows}}，外壳和 ERP 的 TableDataInfo 一致——
     *         前端的表格组件认的是这两个键，换了名字要改前端
     */
    public Map<String, Object> selectList(SiteInquiry query, Integer pageNum, Integer pageSize)
    {
        SiteInquiry condition = query == null ? new SiteInquiry() : query;
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;

        int total = inquiryMapper.countList(condition);
        List<SiteInquiry> rows = total == 0
                ? List.of()
                : inquiryMapper.selectList(condition, (current - 1) * size, size);
        // 站点名称由这里填。ERP 那版是 left join u_s_site 带出来的，那张表不在这个库里
        String siteName = siteInfoService.require().getSiteName();
        for (SiteInquiry item : rows)
        {
            item.setSiteName(siteName);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    public SiteInquiry selectById(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("缺少询盘 ID");
        }
        SiteInquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry != null)
        {
            inquiry.setSiteName(siteInfoService.require().getSiteName());
        }
        return inquiry;
    }

    /**
     * 改处理状态和内部备注。
     *
     * <p><b>客户填的内容一个字都不许改</b>——那是原始凭据。所以这里只把
     * status / remark / handleUser 三样传下去，请求体里带的姓名、邮箱、留言一律不理。
     * 这一点靠 mapper 的 update 语句保证（它的 set 里只有那三列），
     * 不是靠这里逐个字段置 null。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateHandle(SiteInquiry inquiry, String operator)
    {
        if (inquiry == null || inquiry.getId() == null)
        {
            throw new ServiceException("缺少询盘 ID");
        }
        if (inquiryMapper.selectById(inquiry.getId()) == null)
        {
            throw new ServiceException("询盘不存在");
        }
        inquiry.setHandleUser(operator);
        return inquiryMapper.updateHandle(inquiry);
    }
}
