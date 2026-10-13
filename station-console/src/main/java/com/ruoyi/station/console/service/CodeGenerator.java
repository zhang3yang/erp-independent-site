package com.ruoyi.station.console.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.mapper.SequenceMapper;

/**
 * 单号生成。询盘单号、图片/视频主键都从这里出。
 *
 * <h3>为什么不是 Redis</h3>
 * <p>ERP 那边走的是它自己的 Redis 发号器（{@code InventoryDocumentCodeGenerator}）。
 * 那个发号器在 ERP 侧，搬不过来——搬过来就要在这边再搭一套，而那套东西有个
 * 讨厌的故障模式：Redis 被清了，号从头开始，撞上唯一索引，
 * 而且是在用户点了提交之后才炸。</p>
 *
 * <p>一个站的量级下（一天几十条询盘、几百张图），一条
 * {@code update ... set nextval = nextval + 1} 完全够用。它还有一个 Redis 给不了的好处：
 * <b>号和业务数据在同一个库</b>，不需要跨两个存储去对账。</p>
 *
 * <h3>前缀沿用 ERP 那一套</h3>
 * <p>{@code XP} / {@code SIMG} / {@code SVID}，和搬过来的历史数据接得上。
 * 改前缀会让历史单号和新单号看起来像两个系统的东西。</p>
 */
@Service
public class CodeGenerator
{
    private static final String SEQ_INQUIRY = "inquiry";
    private static final String SEQ_IMAGE = "image";
    private static final String SEQ_VIDEO = "video";
    private static final String SEQ_MODEL = "model";
    private static final String SEQ_MODEL_JOB = "modeljob";
    private static final String SEQ_ORDER = "order";
    private static final String SEQ_BUYER = "buyer";
    private static final String SEQ_CHAT = "chat";
    private static final String SEQ_POST = "post";
    private static final String SEQ_FOLDER = "folder";

    private static final String PREFIX_INQUIRY = "XP";
    private static final String PREFIX_IMAGE = "SIMG";
    private static final String PREFIX_VIDEO = "SVID";
    /** 3D 模型（.glb）。2026-09-20 加，号段从 1 起，没有历史数据要接 */
    private static final String PREFIX_MODEL = "SMDL";
    /** 混元生 3D 任务。2026-09-26 加，号段从 1 起 */
    private static final String PREFIX_MODEL_JOB = "SMJB";

    /**
     * 订单号和客户编码的前缀。
     *
     * <p>{ KH} 和 ERP 的客户前缀是同一个字母组合，但那是<b>另一个库里另一张表</b>的号——
     * 两边各自计数、互不相干（boundary §6c）。看到两个 KH 开头的号不要以为是同一个客户。</p>
     */
    private static final String PREFIX_ORDER = "DD";
    private static final String PREFIX_BUYER = "KH";

    /**
     * 在线咨询会话的前缀。
     *
     * <p>这个号<b>不是给客户看的</b>——它不出现在前台任何地方，只在管理台的会话列表上
     * 给一条匿名线程一个能指认的名字（访客一句称呼都没留时，运营至少能说
     * 「LT0000000007 那个人」）。</p>
     */
    private static final String PREFIX_CHAT = "LT";

    /**
     * 博客文章的内部编码前缀。
     *
     * <p>这个号<b>不出现在前台任何地方</b>：文章的公开地址用的是
     * {@code u_s_post.slug}（{@code /blog/how-to-choose-silicone-tubing}）。
     * 编码只是语言表的外键和管理台里指认一篇文章的名字。</p>
     *
     * <p>⚠ 所以别把它拼进 URL——博客整个存在的理由就是自然搜索，
     * {@code /blog/BLOG0000000001} 这种地址等于把 URL 里的关键词这个优势扔掉。</p>
     */
    private static final String PREFIX_POST = "BLOG";
    private static final String PREFIX_FOLDER = "SFD";

    /** 流水位数。10 位够用到 99 亿条，前面补零让单号等宽、排序即时间序 */
    private static final int WIDTH = 10;

    @Autowired
    private SequenceMapper sequenceMapper;

    /**
     * ⚠ 下面每个入口方法各自挂 {@code @Transactional}，<b>不是</b>挂在下面那个 {@code next} 上。
     *
     * <p>Spring 的事务是靠代理实现的，代理只拦得住从<b>外面</b>进来的调用。
     * 把注解挂在 {@code next} 上、再由那些入口方法内部调用它，就是一次自调用——
     * 走的是 {@code this.next(...)}，根本不经过代理，注解静默失效。
     * 这类错误编译不报、启动不报、平时也看不出来，只有在并发下撞了号才暴露。</p>
     *
     * <p><b>{@code REQUIRES_NEW} 也是有意的。</b>发号必须独立提交：挂在调用方的事务里的话，
     * 调用方回滚会把号一起还回去，而那个号可能已经被另一个并发请求看到并跳过了——
     * 结果是两个请求拿到同一个号。号断几个不要紧（本来就不保证连续），撞号要紧。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextInquiryCode()
    {
        return next(SEQ_INQUIRY, PREFIX_INQUIRY);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextImageId()
    {
        return next(SEQ_IMAGE, PREFIX_IMAGE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextVideoId()
    {
        return next(SEQ_VIDEO, PREFIX_VIDEO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextModelId()
    {
        return next(SEQ_MODEL, PREFIX_MODEL);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextModelJobId()
    {
        return next(SEQ_MODEL_JOB, PREFIX_MODEL_JOB);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextOrderCode()
    {
        return next(SEQ_ORDER, PREFIX_ORDER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextBuyerCode()
    {
        return next(SEQ_BUYER, PREFIX_BUYER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextChatCode()
    {
        return next(SEQ_CHAT, PREFIX_CHAT);
    }

    /**
     * 博客文章的内部编码。
     *
     * <p>⚠ 老库要跑 {@code sql/station/migration/2026-09-08-blog.sql} 才有
     * {@code post} 这一行序列。没跑的话这里会抛「序列 post 不存在」——
     * 那是 {@link #next} 里那句显式检查，比一个静默的 0 行更新好查。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextPostCode()
    {
        return next(SEQ_POST, PREFIX_POST);
    }

    /**
     * 媒体文件夹的编号。
     *
     * <p>⚠ 这个方法在合并提交 {@code 0f231ed1} 里<b>被丢掉过一次</b>：远端的
     * {@code 0e73988c} 给这个文件加了它（+10 行），而本地同一时间也在改这个文件，
     * 解冲突时把远端那一半漏了。结果是 {@code SiteFolderService} 引用一个不存在的
     * 方法，<b>整个 station-console 编译不过</b>。</p>
     *
     * <p>教训：这个文件是「谁加功能都要动一行」的公共文件，合并冲突时
     * 两边的方法都要留下，不是二选一。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextFolderId()
    {
        return next(SEQ_FOLDER, PREFIX_FOLDER);
    }

    /**
     * 占一个号。事务由上面那些入口开，这里只管取。
     *
     * <p>顺序不能反：先 {@code update}（给这一行加行锁），再 {@code select}。
     * 反过来两个并发请求会读到同一个值，发出两个一样的号。</p>
     */
    private String next(String name, String prefix)
    {
        int rows = sequenceMapper.increase(name);
        if (rows == 0)
        {
            // 建库脚本里那几行 INSERT IGNORE 没跑到。这是部署问题，不是用户操作问题
            throw new ServiceException("单号生成失败：发号器里没有「" + name + "」这一行，检查建库脚本");
        }
        long value = sequenceMapper.current(name);
        return prefix + String.format("%0" + WIDTH + "d", value);
    }
}
