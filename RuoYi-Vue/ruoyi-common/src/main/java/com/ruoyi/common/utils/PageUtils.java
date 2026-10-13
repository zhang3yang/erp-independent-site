package com.ruoyi.common.utils;

import java.util.function.Supplier;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.utils.sql.SqlUtil;

/**
 * 分页工具类
 * 
 * @author ruoyi
 */
public class PageUtils extends PageHelper
{
    /**
     * 设置请求分页数据
     */
    public static void startPage()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage()
    {
        PageHelper.clearPage();
    }

    /**
     * 在分页参数之外执行一次查询，执行完把分页参数原样还回去。
     *
     * <p>{@link #startPage()} 设的是线程变量，<b>紧接着的第一条 SQL</b> 会把它吃掉并清空。
     * 所以列表查询前如果还要先查一次元数据（典型的是按月分表时先查有哪些月表），
     * 那条元数据查询会把分页吃走，真正的列表查询反而变成不分页——返回全表、
     * 每页数量失效、翻页每页都一样。这类问题不会报错，只会悄悄返回错的数据。</p>
     *
     * <pre>
     * List&lt;String&gt; suffixes = PageUtils.withoutPaging(() -&gt; mapper.selectTableSuffixes());
     * return mapper.selectListAcrossMonths(query, suffixes);   // 分页落在这条上
     * </pre>
     *
     * @param query 要在分页之外跑的查询
     * @return 该查询的返回值
     */
    public static <T> T withoutPaging(Supplier<T> query)
    {
        Page<?> pending = PageHelper.getLocalPage();
        if (pending == null)
        {
            return query.get();
        }
        PageHelper.clearPage();
        try
        {
            return query.get();
        }
        finally
        {
            // 还回去而不是重新 startPage：分页参数里还有 orderBy、reasonable 等，
            // 重建容易漏，原样放回最省事也最不会错。
            PageHelper.setLocalPage(pending);
        }
    }
}
