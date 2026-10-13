package com.ruoyi.station.console.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteBuyer;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.domain.SiteInquiry;
import com.ruoyi.station.console.domain.SiteOrder;
import com.ruoyi.station.console.mapper.SiteBuyerMapper;
import com.ruoyi.station.console.mapper.SiteInquiryMapper;
import com.ruoyi.station.console.mapper.SiteOrderMapper;

/**
 * 站点客户档案。
 *
 * <h3>⚠ 和 ERP 的销售客户没有任何关系</h3>
 * <p>docs/独立站与ERP的边界.md §6c：买家属于站点。这个类里没有一次调用出这台机器，
 * 也没有「转成 ERP 客户」那个动作——那个动作往 {@code sdsshop.u_m_buyer} 写数据，
 * 是一条互通，§10.1 里已经删掉了。</p>
 *
 * <h3>档案是长出来的</h3>
 * <p>没有 {@code create}。谁发询盘、谁下单，就按邮箱为他落一行（{@link #resolveByEmail}）；
 * 管理台只能改标签和备注。和 {@code SiteUserService} 那边「谁从 ERP 跳进来就落一行」
 * 是同一个思路：没有「新建」按钮，就不会有一个对不上任何人的档案。</p>
 */
@Service
public class SiteBuyerService
{
    private static final Logger log = LoggerFactory.getLogger(SiteBuyerService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SiteBuyerMapper buyerMapper;

    @Autowired
    private SiteOrderMapper orderMapper;

    @Autowired
    private SiteInquiryMapper inquiryMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CodeGenerator codeGenerator;

    // ==================== 建档 / 更新 ====================

    /**
     * 按邮箱找客户，找不到就建一个。
     *
     * <p>这是<b>唯一</b>会往 {@code u_s_buyer} 插行的方法。询盘和下单都走它。</p>
     *
     * <h3>⚠ 为什么要接 DuplicateKeyException</h3>
     * <p>「先查、没有就插」在并发下会撞：同一个买家同时提交询盘和订单（或者点了两次提交），
     * 两个请求都查到 null，都去插，第二个撞上 {@code uk_buyer_email}。</p>
     *
     * <p>用「撞了就再查一次」而不是先加锁：这条路径在买家点提交那一下的请求链上，
     * 为一个几乎不发生的并发去锁一张表不值得，而撞了之后重查一次的代价是一次查询。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyer resolveByEmail(String email, String name, String company,
            String phone, String country)
    {
        String key = trimToNull(email);
        if (key == null)
        {
            // 没有邮箱就没有身份。不建档，也不报错——询盘/订单本身已经存下来了，
            // 后台照样看得到，只是没挂到客户档案上
            return null;
        }
        key = key.toLowerCase();

        SiteBuyer existing = buyerMapper.selectByEmail(key);
        if (existing != null)
        {
            touch(existing, name, company, phone, country);
            return existing;
        }

        SiteInfo site = siteInfoService.require();
        SiteBuyer buyer = new SiteBuyer();
        buyer.setShopCode(site.getShopCode());
        buyer.setSiteCode(site.getSiteCode());
        buyer.setBuyerCode(codeGenerator.nextBuyerCode());
        buyer.setEmail(key);
        buyer.setName(cut(name, 100));
        buyer.setCompany(cut(company, 200));
        buyer.setPhone(cut(phone, 50));
        buyer.setCountry(cut(country, 80));
        try
        {
            buyerMapper.insert(buyer);
        }
        catch (DuplicateKeyException e)
        {
            // 并发下另一个请求刚插进去了。重查一次拿它那一行——
            // 这里**不能**把异常吞掉就返回 null：调用方会以为这个客户没建档
            SiteBuyer raced = buyerMapper.selectByEmail(key);
            if (raced == null)
            {
                throw e;
            }
            log.info("客户 {} 的档案由并发请求先建好了，用它那一行", key);
            touch(raced, name, company, phone, country);
            return raced;
        }

        // 建档之前这个邮箱可能已经有历史订单了（订单先于客户管理上线）。挂上去
        orderMapper.bindBuyerByEmail(key, buyer.getBuyerCode());
        refreshCounts(buyer.getBuyerCode(), key);

        log.info("新客户建档：{}（{}），来自 {}", buyer.getBuyerCode(), key,
                cut(company, 200) == null ? "无公司名" : company);
        return buyer;
    }

    private void touch(SiteBuyer buyer, String name, String company, String phone, String country)
    {
        SiteBuyer patch = new SiteBuyer();
        patch.setEmail(buyer.getEmail());
        // 全部走 coalesce（在 SQL 里），这一次没填的不覆盖上一次填过的
        patch.setName(cut(name, 100));
        patch.setCompany(cut(company, 200));
        patch.setPhone(cut(phone, 50));
        patch.setCountry(cut(country, 80));
        buyerMapper.touch(patch);
    }

    /** 重算询盘数和下单数。见 mapper 里那段：计数现算，不靠累加维护 */
    public void refreshCounts(String buyerCode, String email)
    {
        if (trimToNull(buyerCode) == null || trimToNull(email) == null)
        {
            return;
        }
        buyerMapper.refreshCounts(buyerCode, email.toLowerCase());
    }

    // ==================== 询盘转客户 ====================

    /**
     * 把一条询盘挂到客户档案上（没有档案就建一个），并回填
     * {@code u_s_inquiry.buyercode}。
     *
     * <p>这就是建库脚本里 {@code buyercode} 那一列注释说的「将来转的是这里」。
     * <b>转的是本站的档案，不是 ERP 的客户</b>——那个动作已经删掉了（§10.1）。</p>
     *
     * <p>可以重复点：已经挂过的会走到 {@code resolveByEmail} 的「找到了」那一支，
     * 只更新联系方式，不会重复建档。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyer linkInquiry(Long inquiryId)
    {
        if (inquiryId == null)
        {
            throw new ServiceException("缺少询盘 ID");
        }
        SiteInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null)
        {
            throw new ServiceException("询盘不存在");
        }
        SiteBuyer buyer = resolveByEmail(inquiry.getEmail(), inquiry.getName(),
                inquiry.getCompany(), inquiry.getPhone(), null);
        if (buyer == null)
        {
            throw new ServiceException("这条询盘没有邮箱，建不了客户档案");
        }
        inquiryMapper.bindBuyer(inquiryId, buyer.getBuyerCode());
        refreshCounts(buyer.getBuyerCode(), buyer.getEmail());
        log.info("询盘 {} 已挂到客户 {}（{}）", inquiry.getInquiryCode(),
                buyer.getBuyerCode(), buyer.getEmail());
        // ⚠ 重查一次再返回。上面那个 buyer 是 refreshCounts **之前**的快照，
        //   里面的 inquirycount / ordercount 还是旧值（新建的话是 null）。
        //   直接返回它，前端弹的提示框里会显示「询盘 null 条」
        return buyerMapper.selectByCode(buyer.getBuyerCode());
    }

    // ==================== 管理台 ====================

    public Map<String, Object> selectList(SiteBuyer query, Integer pageNum, Integer pageSize)
    {
        SiteBuyer condition = query == null ? new SiteBuyer() : query;
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;

        int total = buyerMapper.countList(condition);
        List<SiteBuyer> rows = total == 0
                ? List.of()
                : buyerMapper.selectList(condition, (current - 1) * size, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    /**
     * 客户详情：档案 + 他的订单 + 他的询盘。
     *
     * <p>三样一起返回，不是三个接口：运营点开一个客户就是想看「这个人跟我们来往过什么」，
     * 拆成三次请求只是让前端多写两个 loading。</p>
     */
    /**
     * 按客户编码取档案。<b>不含密码哈希</b>（走的是 {@code selectByCode}，
     * 那条查询的列清单里没有 passwordhash）。
     *
     * <p>给前台买家会话用：{@code BuyerAccountController.requireBuyer} 每个请求调它一次，
     * 拿会话里的编码换出「这个人是谁、停用了没有」。</p>
     *
     * <p>⚠ 认证相关的读取走 {@code BuyerAuthService}，不要在这里加密码比对——
     * 那会让密码哈希流进一条不需要它的调用链。</p>
     */
    public SiteBuyer findByCode(String buyerCode)
    {
        String code = trimToNull(buyerCode);
        return code == null ? null : buyerMapper.selectByCode(code);
    }

    public Map<String, Object> detail(String buyerCode)
    {
        String code = trimToNull(buyerCode);
        if (code == null)
        {
            throw new ServiceException("缺少客户编码");
        }
        SiteBuyer buyer = buyerMapper.selectByCode(code);
        if (buyer == null)
        {
            throw new ServiceException("客户不存在");
        }

        SiteOrder orderQuery = new SiteOrder();
        orderQuery.setBuyerCode(code);
        List<SiteOrder> orders = orderMapper.selectList(orderQuery, 0, MAX_PAGE_SIZE);

        SiteInquiry inquiryQuery = new SiteInquiry();
        // 询盘按邮箱找：历史询盘的 buyercode 可能还是空的（客户管理后于询盘上线）
        inquiryQuery.setEmail(buyer.getEmail());
        List<SiteInquiry> inquiries = inquiryMapper.selectByEmail(buyer.getEmail(), MAX_PAGE_SIZE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buyer", buyer);
        result.put("orders", orders);
        result.put("inquiries", inquiries);
        return result;
    }

    // ==================== 买家自己改（前台账户页） ====================

    /**
     * 买家在前台账户页改自己的联系方式。
     *
     * <p>⚠ 和 {@link #updateNote} 是两拨人的两件事，别合并：那个是<b>运营</b>改
     * 标签和备注（买家看不到的内部字段），这个是<b>买家自己</b>改联系人、公司、
     * 电话、国家（运营看到的对外字段）。两边能改的列没有交集，这是刻意的。</p>
     *
     * <p>⚠ 改不了邮箱：那是他的身份（唯一键、登录名、找回密码的去处）。
     * 换邮箱要走一遍验证流程，不是这个接口的事。</p>
     *
     * <p>⚠ 也没有 coalesce：他清空公司名就是要清掉它，见 mapper 里那段注释。</p>
     *
     * @return 改完之后的档案。前台顶栏和账户页直接用它刷新显示，不用再问一次
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyer updateProfile(String buyerCode, String name, String company,
            String phone, String country)
    {
        String code = trimToNull(buyerCode);
        if (code == null || buyerMapper.selectByCode(code) == null)
        {
            throw BuyerText.error("auth.signInRequired", 401);
        }
        SiteBuyer patch = new SiteBuyer();
        patch.setBuyerCode(code);
        patch.setName(cut(name, 100));
        patch.setCompany(cut(company, 200));
        patch.setPhone(cut(phone, 50));
        patch.setCountry(cut(country, 80));
        buyerMapper.updateProfile(patch);
        // 重查一次再返回：上面那个 patch 只有四个字段，displayName / 计数都不在里面
        return buyerMapper.selectByCode(code);
    }

    /**
     * 头像的六个内置色块。和前台 {@code useAvatar.ts} 的 {@code AVATAR_PRESETS} 一一对应。
     *
     * <p>⚠ 白名单在这里，不在前台。前台那份是用来画色块的，改不改都不影响这里；
     * 少了这道判断，这个接口就变成「买家能往库里写任意字符串」——那串东西会被
     * 原样塞进 {@code <span style="background: linear-gradient(...)">} 里。</p>
     */
    private static final List<String> AVATAR_PRESETS =
            List.of("lilac", "rose", "teal", "amber", "slate", "moss");

    /**
     * 头像 data URL 的长度上限（字符数，不是字节）。
     *
     * <p>前台裁成正方形、缩到 256px、按 JPEG 0.82 编码，落地十几到几十 KB，
     * base64 之后再涨三分之一。30 万字符（约 220KB）留了十倍余量，
     * 同时挡住「把一张 5MB 的原图直接 base64 塞过来」。</p>
     */
    private static final int AVATAR_MAX_CHARS = 300_000;

    /**
     * 设头像。三种合法取值：{@code null}（清掉，退回首字母字块）、
     * {@code preset:<六个色块之一>}、浏览器生成的 JPEG/PNG data URL。
     *
     * <p>⚠ <b>别放宽这个校验去接受任意字符串。</b>这一列的值会被前台直接放进
     * {@code <img src>} 或 CSS 的 {@code background} 里。允许任意 URL 的话，
     * 买家的头像就成了一个「向第三方发请求」的开关（追踪像素、外链失效、混合内容告警），
     * 而它会显示在管理台的客户列表上——也就是说那是买家能塞给运营看的东西。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(String buyerCode, String avatar)
    {
        String code = trimToNull(buyerCode);
        if (code == null || buyerMapper.selectByCode(code) == null)
        {
            throw BuyerText.error("auth.signInRequired", 401);
        }
        String value = trimToNull(avatar);
        if (value != null)
        {
            if (value.startsWith("preset:"))
            {
                if (!AVATAR_PRESETS.contains(value.substring("preset:".length())))
                {
                    throw BuyerText.error("avatar.badPreset", 400);
                }
            }
            else if (value.startsWith("data:image/jpeg;base64,") || value.startsWith("data:image/png;base64,"))
            {
                if (value.length() > AVATAR_MAX_CHARS)
                {
                    throw BuyerText.error("avatar.tooBig", 400);
                }
            }
            else
            {
                // 外链、svg、javascript: 之类一律挡在这里，理由见方法注释
                throw BuyerText.error("avatar.badFormat", 400);
            }
        }
        buyerMapper.updateAvatar(code, value);
    }

    /** 取头像。没设过就是 null，前台画首字母字块 */
    public String avatarOf(String buyerCode)
    {
        String code = trimToNull(buyerCode);
        return code == null ? null : buyerMapper.selectAvatar(code);
    }

    // ==================== 管理台的写 ====================

    /** 管理台唯一的写操作：标签和备注。联系方式不给改——那是客户自己填的 */
    @Transactional(rollbackFor = Exception.class)
    public int updateNote(String buyerCode, String tag, String remark)
    {
        String code = trimToNull(buyerCode);
        if (code == null || buyerMapper.selectByCode(code) == null)
        {
            throw new ServiceException("客户不存在");
        }
        SiteBuyer patch = new SiteBuyer();
        patch.setBuyerCode(code);
        patch.setTag(cut(tag, 50));
        patch.setRemark(cut(remark, 500));
        return buyerMapper.updateNote(patch);
    }

    // ==================== 小工具 ====================

    private static String trimToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cut(String value, int max)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
