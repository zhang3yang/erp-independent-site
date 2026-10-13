package com.ruoyi.station.console.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.station.console.domain.SiteBuyer;

/**
 * 站点客户档案。对应表 {@code u_s_buyer}。
 *
 * <p>⚠ 和 ERP 的 {@code sdsshop.u_m_buyer} 无任何关系（boundary §6c）。</p>
 *
 * <p>没有 {@code insert(建档)} 之外的写入口给管理台用：档案是询盘/下单时长出来的，
 * 管理台只能改标签和备注（{@link #updateNote}）。理由见 domain 的类注释。</p>
 *
 * <h3>⚠ 凭据列只有下面三个方法碰</h3>
 * <p>{@code passwordhash} 不在给管理台用的列清单里。取它的只有
 * {@link #selectForAuth} 和 {@link #selectForAuthByCode}，写它的只有
 * {@link #updatePassword}。这是「少一处出现就少一处被顺手序列化出去的机会」——
 * domain 上的 {@code @JsonIgnore} 是第二道，这里是第一道。</p>
 */
public interface SiteBuyerMapper
{
    /** 首次出现时落一行 */
    int insert(SiteBuyer buyer);

    /** 按邮箱找人。邮箱就是客户的身份。<b>不含 passwordhash</b> */
    SiteBuyer selectByEmail(@Param("email") String email);

    /**
     * 认证专用：按邮箱取，<b>含 passwordhash</b>。
     *
     * <p>只给 {@code BuyerAuthService} 用。别在别处调它——那等于把密码哈希
     * 带进一个不需要它的调用链，而那条链上某一层迟早会把对象整个序列化出去。</p>
     */
    SiteBuyer selectForAuth(@Param("email") String email);

    /** 认证专用：按客户编码取，含 passwordhash。改密码时验旧密码用 */
    SiteBuyer selectForAuthByCode(@Param("buyerCode") String buyerCode);

    /**
     * 设密码。注册、重置、改密码三条路都走它。
     *
     * <p>顺手把邮箱验证状态一起写：注册时置 0（还没验），验证通过和重置成功时置 1
     * （能收到那封信就证明邮箱是他的，重置成功等于又验了一次）。</p>
     */
    int updatePassword(@Param("buyerCode") String buyerCode,
            @Param("passwordHash") String passwordHash,
            @Param("emailVerified") boolean emailVerified);

    /** 邮箱验证通过 */
    int markVerified(@Param("buyerCode") String buyerCode);

    /** 登录成功后打点。⚠ 失败不打——失败也打的话，logincount 就变成了「有人在试密码」的计数 */
    int touchLogin(@Param("buyerCode") String buyerCode, @Param("loginIp") String loginIp);

    SiteBuyer selectByCode(@Param("buyerCode") String buyerCode);

    List<SiteBuyer> selectList(@Param("query") SiteBuyer query,
            @Param("offset") int offset, @Param("limit") int limit);

    int countList(@Param("query") SiteBuyer query);

    /**
     * 同一个邮箱再来时，更新联系方式 + 活跃时间。
     *
     * <p>⚠ 用 coalesce：这一次没填公司名，不该把上一次填过的抹掉。
     * 客户第二次提交时经常只填邮箱和留言。</p>
     */
    int touch(SiteBuyer buyer);

    /** 管理台唯一的写操作：标签和备注 */
    int updateNote(SiteBuyer buyer);

    /**
     * 买家自己在账户页改联系方式（联系人 / 公司 / 电话 / 国家）。
     *
     * <p>⚠ 和 {@link #touch} 不是一回事，别合并：那一条是「客户又提交了一次，
     * 这次没填的别把上次填的抹掉」，所以走 coalesce；这一条是买家在编辑框里按了保存，
     * 他清空公司名就是要清掉它，所以是直接赋值。</p>
     *
     * <p>⚠ 改不了邮箱。邮箱是客户的身份（唯一键、登录名、找回密码的去处），
     * 换邮箱等于换一个账号，那要走验证流程，不是一个 update 的事。</p>
     */
    int updateProfile(SiteBuyer buyer);

    /**
     * 取头像。
     *
     * <p>⚠ 单独一条查询，而且 {@link SiteBuyer} 上<b>没有</b>对应字段——是刻意的。
     * 头像是一串几十 KB 的 base64，挂在那个对象上就会跟着
     * {@link #selectList}（管理台客户列表，一页 20 行）和 {@link #selectByCode}
     * （{@code requireBuyer}，每个要身份的请求跑一次）一起被读出来、序列化出去。</p>
     *
     * <p>没有字段就不可能被顺手带上，这比「记得别把它加进列清单」可靠。</p>
     */
    String selectAvatar(@Param("buyerCode") String buyerCode);

    /** 设头像。传 null 就是清掉，退回首字母字块 */
    int updateAvatar(@Param("buyerCode") String buyerCode, @Param("avatar") String avatar);

    /** 重算计数。询盘和订单都可能被删/改，计数用现算的，不靠累加维护 */
    int refreshCounts(@Param("buyerCode") String buyerCode, @Param("email") String email);
}
