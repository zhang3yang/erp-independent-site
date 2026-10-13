package com.ruoyi.station.console.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 本库发号器。对应表 {@code u_s_sequence}。
 *
 * <h3>为什么不继续用 Redis 发号</h3>
 * <p>ERP 那边询盘单号和图片主键走的是它自己的 Redis 发号器。那个发号器在 ERP 侧，
 * 搬过来之后不在了——而重新在这边搭一套 Redis 发号，会引入一个「Redis 被清了、
 * 单号从头开始、撞上唯一索引」的故障模式。</p>
 *
 * <p>一个站的量级下，一条 {@code update ... set nextval = nextval + 1} 完全够用，
 * 而且它和业务数据在同一个库、同一个事务里：询盘插入失败，号也跟着回滚，不留空洞。</p>
 */
public interface SequenceMapper
{
    /**
     * 占一个号。
     *
     * <p>⚠ 必须先 update 再 select，且两句在同一个事务里：{@code update} 会给这一行加行锁，
     * 并发的第二个请求会等在这里，拿到的是加过之后的值。反过来「先 select 再 update」
     * 两个请求会读到同一个值，发出两个一样的单号。</p>
     */
    int increase(@Param("name") String name);

    long current(@Param("name") String name);
}
