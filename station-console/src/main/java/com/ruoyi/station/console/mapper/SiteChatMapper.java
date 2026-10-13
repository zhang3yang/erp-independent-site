package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteChat;

/**
 * 在线咨询的会话线程。对应表 {@code u_s_chat}。
 *
 * <h3>⚠ {@code visitortoken} 不在 {@code columns} 里</h3>
 * <p>它是匿名会话的唯一凭据。管理台的会话列表走的是那份列清单，物理上取不出来——
 * 这是第一道防线，{@code SiteChat} 上的 {@code @JsonIgnore} 是第二道。
 * 照的是 {@code SiteBuyerMapper} 对 {@code passwordhash} 那一套。</p>
 *
 * <p>只有 {@link #selectByToken} 拿 token 当<b>查询条件</b>，它也不返回那一列。</p>
 *
 * <h3>不按 sitecode 过滤</h3>
 * <p>一个部署一个站，和 {@code SiteBuyerMapper} 一样：列写进去是为了将来，
 * 查询里不带它。带上只会给人一种「这里支持多站」的错觉，
 * 而真要多站，要改的远不止一个 where 条件。</p>
 */
public interface SiteChatMapper
{
    /** 按访客令牌找线程。⚠ token 只从 Cookie 来，绝不从 URL 来 */
    SiteChat selectByToken(@Param("visitorToken") String visitorToken);

    SiteChat selectByCode(@Param("chatCode") String chatCode);

    /**
     * 按客户编码找线程。
     *
     * <p>已登录的买家换了设备（新浏览器 = 没有 CHAT_VISITOR Cookie）时靠它把
     * 历史对话找回来。一个客户可能有多条线程（先匿名聊过、后来才登录），
     * 取最近活跃的那条。</p>
     */
    SiteChat selectLatestByBuyer(@Param("buyerCode") String buyerCode);

    int insert(SiteChat chat);

    /** 发完一条之后更新摘要和时间。列表靠这两列排序和显示 */
    int touchLast(@Param("chatId") Long chatId,
                  @Param("lastMessage") String lastMessage);

    /**
     * 推进已读游标。
     *
     * <p>⚠ SQL 里套了 {@code greatest}：游标<b>只增不减</b>。
     * 两个标签页同时开着、后开的那个先标已读，不套 greatest 就会把游标退回去，
     * 表现是「明明看过的消息又变回未读」。</p>
     *
     * @param side {@code console} 或 {@code buyer}
     */
    int advanceRead(@Param("chatId") Long chatId,
                    @Param("side") String side,
                    @Param("readId") Long readId);

    /** 访客留了邮箱之后回填身份。⚠ 全部走 coalesce，这一次没填的不覆盖上一次填过的 */
    int bindBuyer(@Param("chatId") Long chatId,
                  @Param("buyerCode") String buyerCode,
                  @Param("email") String email,
                  @Param("name") String name);

    /**
     * 管理台的会话列表。每一行带一个<b>现算</b>的 {@code unread}。
     *
     * <p>排序是「未读置顶，然后按最后消息倒排」——运营打开面板第一眼要看到
     * 还没回的人，而不是最近回过的人。</p>
     */
    List<SiteChat> selectList(@Param("query") SiteChat query,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    int countList(@Param("query") SiteChat query);

    /** 悬浮球红点用：全站运营未读的消息总数 */
    int totalUnreadForConsole();
}
