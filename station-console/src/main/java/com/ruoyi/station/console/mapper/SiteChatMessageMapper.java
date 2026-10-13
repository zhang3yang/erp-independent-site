package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteChatMessage;

/**
 * 在线咨询的消息。对应表 {@code u_s_chatmessage}。
 *
 * <p>两侧的轮询走的都是 {@link #selectSince}：带上手里最大的那个 id，
 * 只取比它新的。全量拉一遍在对话长了以后会越来越慢，而且每 3 秒一次。</p>
 */
public interface SiteChatMessageMapper
{
    /**
     * 增量拉取。
     *
     * @param sinceId 手里最大的 id，第一次进来传 0
     * @param limit   一次最多取多少。防的是「离开一天回来，一次拉几千条把页面卡死」
     */
    List<SiteChatMessage> selectSince(@Param("chatId") Long chatId,
                                      @Param("sinceId") Long sinceId,
                                      @Param("limit") int limit);

    /**
     * 最近的 N 条，按时间正序返回。
     *
     * <p>打开窗口时用它，不用 {@link #selectSince}(0)：一条聊了半年的线程
     * 不该在点开的那一刻把全部历史推给浏览器。</p>
     */
    List<SiteChatMessage> selectRecent(@Param("chatId") Long chatId,
                                       @Param("limit") int limit);

    int insert(SiteChatMessage message);

    /**
     * 某条线程里，某一侧还没读的条数。
     *
     * <p>⚠ {@code sender} 传的是<b>对方</b>：运营的未读数只数买家发的，
     * 自己刚发出去的那条不该让自己的红点亮起来。</p>
     */
    int countUnread(@Param("chatId") Long chatId,
                    @Param("readId") Long readId,
                    @Param("sender") String sender);

    /** 这条线程当前最大的消息 id。标已读时把游标推到这里 */
    Long maxId(@Param("chatId") Long chatId);
}
