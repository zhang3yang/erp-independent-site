package com.ruoyi.system.service.impl;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.OssProperties;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.sign.AesUtils;
import com.ruoyi.system.domain.StationSite;
import com.ruoyi.system.mapper.StationSiteMapper;
import com.ruoyi.system.service.IStationSiteService;

/**
 * 独立站登记 Service 实现。
 *
 * <p>这张表现在只登记「有哪些站、在哪、怎么进」——站里的业务数据都在站点服务器上
 * （docs/独立站与ERP的边界.md §7）。</p>
 */
@Service
public class StationSiteServiceImpl implements IStationSiteService
{
    /** 站点状态白名单，与前端的启用/停用开关、建表脚本的注释保持一致 */
    private static final Set<String> STATUSES = new LinkedHashSet<>(Arrays.asList(
            StationSite.STATUS_ENABLED, StationSite.STATUS_DISABLED));

    /**
     * 跳转密钥的字节数。
     *
     * <p>32 字节 = 256 位，和 HMAC-SHA256 的输出等长。再长不会更安全
     * （HMAC 会先把超长的密钥哈希一遍），再短就在削弱签名。</p>
     */
    private static final int SECRET_BYTES = 32;

    /**
     * 生成密钥用的随机源。
     *
     * <p>用 {@link SecureRandom} 而不是 {@code Math.random} / {@code UUID}：
     * 这个值是跳转协议的全部安全性所在——拿到它就能以任意身份进那个站的后台。
     * 随机性弱一分，整套设计就少一分。</p>
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StationSiteMapper siteMapper;

    @Autowired
    private InventoryDocumentCodeGenerator codeGenerator;

    /** 只用来取加密主密钥（{@code ruoyi.oss.config-secret}），和对象存储本身无关 */
    @Autowired
    private OssProperties ossProperties;

    @Override
    public List<StationSite> selectSiteList(StationSite query)
    {
        requireShop(query == null ? null : query.getShopCode());
        return siteMapper.selectList(query);
    }

    @Override
    public StationSite selectSiteByCode(String shopCode, String siteCode)
    {
        requireShop(shopCode);
        return siteMapper.selectByCode(shopCode, trimToNull(siteCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StationSite insertSite(StationSite site)
    {
        requireShop(site == null ? null : site.getShopCode());
        normalize(site);
        validate(site);

        // 编码一律服务端生成：让前端传编码等于把主键交给前端，既能撞号，
        // 也能被拿来覆盖别人的站点。与客户、仓库这些主数据同一个口径。
        site.setSiteCode(codeGenerator.nextStationSiteCode());

        // 建站就发一份跳转密钥，不留「以后再补」这个状态——
        // 没有密钥的站，「进入管理」是坏的，而那个坏法（点了没反应）很难查。
        String plain = randomSecret();
        site.setConsoleSecret(encrypt(plain));
        try
        {
            siteMapper.insert(site);
        }
        catch (DuplicateKeyException e)
        {
            // 唯一键只有域名一个，撞了必然是它。先查后插挡不住并发，只能在这里兜底。
            throw new ServiceException(domainConflictMessage(site.getShopCode(), site.getDomain()));
        }
        // ⚠ 把**明文**放回对象上返回给调用方。这是它唯一一次离开服务端——
        //   装站点服务器时要把这个值填进那边的 u_s_siteinfo.consolesecret。
        //   库里存的是密文，之后再也读不出明文来（能解密，但没有接口会那么做）。
        site.setConsoleSecret(plain);
        site.setSecretConfigured(Boolean.TRUE);
        return site;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSite(StationSite site)
    {
        requireShop(site == null ? null : site.getShopCode());
        normalize(site);
        validate(site);
        if (isBlank(site.getSiteCode()))
        {
            throw new ServiceException("站点编码不能为空");
        }
        if (siteMapper.selectByCode(site.getShopCode(), site.getSiteCode()) == null)
        {
            throw new ServiceException("站点不存在：" + site.getSiteCode());
        }
        try
        {
            return siteMapper.update(site);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException(domainConflictMessage(site.getShopCode(), site.getDomain()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotateSecret(String shopCode, String siteCode, String updateUser)
    {
        requireShop(shopCode);
        String code = trimToNull(siteCode);
        StationSite site = siteMapper.selectByCode(shopCode, code);
        if (site == null)
        {
            throw new ServiceException("站点不存在");
        }
        String plain = randomSecret();
        siteMapper.rotateSecret(shopCode, code, encrypt(plain), updateUser);
        return plain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSiteByCodes(String shopCode, String[] siteCodes)
    {
        requireShop(shopCode);
        if (siteCodes == null || siteCodes.length == 0)
        {
            throw new ServiceException("请选择要删除的站点");
        }
        // 登记表没有下游单据挂着，直接删。不再合作的站建议改「停用」留档，
        // 但这是使用建议，不是系统限制。
        //
        // ⚠ 删这条登记不会动站点服务器上的任何东西——那台机器上的库、商品、询盘都还在。
        //   要真正下线一个站，是去关那台机器，不是删这一行。
        return siteMapper.deleteByCodes(shopCode, siteCodes);
    }

    // ==================== 密钥 ====================

    /** URL-safe 的随机串。不带 padding，省得 = 号在配置文件里被人手删掉 */
    private String randomSecret()
    {
        byte[] raw = new byte[SECRET_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 加密跳转密钥。
     *
     * <p>照抄 {@code ShopStorageServiceImpl} 那一套：{@code AesUtils}（AES/GCM）+
     * {@code ruoyi.oss.config-secret} 主密钥。<b>不要再发明第二种加密方式</b>——
     * 两套加密就有两个主密钥要轮换，而第二套一定是那个没人记得怎么轮换的（§7.1）。</p>
     *
     * <p>主密钥没配时直接失败，不给硬编码兜底：兜底意味着密钥实际上是明文存的，
     * 而库被拖走的时候没人会记得这一点。</p>
     */
    private String encrypt(String plain)
    {
        String masterKey = ossProperties.getConfigSecret();
        if (isBlank(masterKey))
        {
            throw new ServiceException("还没配置加密主密钥（ruoyi.oss.config-secret），"
                    + "跳转密钥不能明文入库。用环境变量 OSS_CONFIG_SECRET 注入一个高熵随机串");
        }
        return AesUtils.encrypt(plain, masterKey);
    }

    // ==================== 内部 ====================

    /**
     * 归一化。域名空值必须落成 null 而不是空串：唯一索引允许多个 null，
     * 空串只能有一条，存空串会让「第二个没填域名的站」直接撞唯一键。
     */
    private void normalize(StationSite site)
    {
        site.setSiteName(trimToNull(site.getSiteName()));
        site.setDomain(normalizeDomain(site.getDomain()));
        // 四个子域也归一化：它们存的是**域名不是 URL**，带协议头存进来的话，
        // StationSite.subDomain 虽然会剥掉，但库里留着一个不一致的值总是麻烦
        site.setDataDomain(normalizeDomain(site.getDataDomain()));
        site.setFileDomain(normalizeDomain(site.getFileDomain()));
        site.setConsoleDomain(normalizeDomain(site.getConsoleDomain()));
        site.setBuyerDomain(normalizeDomain(site.getBuyerDomain()));
        site.setServerIp(trimToNull(site.getServerIp()));
        site.setOwnerName(trimToNull(site.getOwnerName()));
        site.setRemark(trimToNull(site.getRemark()));
        // 折进两态：空值算启用，三态时期的「建设中/运行中/已停用」也在这里被折掉，
        // 免得老调用方传上来的旧值绕过下面的白名单校验
        site.setStatus(StationSite.normalizeStatus(site.getStatus()));
        if (site.getSortNum() == null)
        {
            site.setSortNum(0);
        }
        // 最后访问时间只由发令牌那一步打点，前端传什么都不认
        site.setLastAccessTime(null);
        // ⚠ 跳转密钥只由 insertSite / rotateSecret 写，普通改单一律置 null。
        //   mapper 的 update 对它是 coalesce，置 null 就是「不改」——
        //   不置的话，前端每保存一次站点档案就会把密钥写成 null（它从来不下发，前端传的就是 null），
        //   而表现是「从此以后点进入管理都进不去」，没人会联想到刚才只是改了个站点名。
        site.setConsoleSecret(null);
    }

    private void validate(StationSite site)
    {
        if (isBlank(site.getSiteName()))
        {
            throw new ServiceException("站点名称不能为空");
        }
        if (!STATUSES.contains(site.getStatus()))
        {
            throw new ServiceException("站点状态只能是：" + String.join("、", STATUSES));
        }
        if (site.getSortNum() < 0)
        {
            throw new ServiceException("排序不能是负数");
        }
        // 五个域名列都过一遍主机名白名单。归一化已经把路径截掉了，这里挡的是
        // 「填了一句话」「粘了一段 JSON」这类根本不是域名的值
        requireHostLike(site.getDomain(), "主域名");
        requireHostLike(site.getDataDomain(), "后端服务域名");
        requireHostLike(site.getFileDomain(), "文件域名");
        requireHostLike(site.getConsoleDomain(), "管理后台域名");
        requireHostLike(site.getBuyerDomain(), "买家后台域名");
        // 主域名和管理后台域名至少要有一个：跳转地址由它们拼，两个都没有的话
        // 「进入管理」按钮是灰的（§4.5：没登记域名的站，按钮就该是灰的）。
        // 这里不强制要求填——建站登记时可能确实还没定域名，那个状态是合法的。
    }

    /**
     * 域名归一化：转小写，剥掉协议头、凭据前缀、路径和查询串，只留主机（可带端口）。
     *
     * <p>不归一化的话「www.a.com」「https://www.a.com」「www.A.com/」会被当成三个不同的站，
     * 唯一索引形同虚设。</p>
     *
     * <h3>⚠ 为什么要截路径，不只是剥协议头</h3>
     * <p>这几列的历史来源是老的 {@code consoleurl}（「管理台地址」），人往里填过各种东西。
     * 搬迁时真实数据里就有一条完整的阿里巴巴商品链接。而「进入管理」是
     * <b>把跳转令牌 POST 到 {@code https://<consoledomain>/sso}</b>——
     * 一条填错的值会让令牌被发到第三方服务器上，而令牌 60 秒内可以直接用（§4.4）。</p>
     *
     * <p>{@link StationSite#consoleBaseUrl()} 那一侧也截一次。两侧都做是有意的：
     * 库里那批历史脏值不会经过这个方法。</p>
     */
    private String normalizeDomain(String domain)
    {
        String value = trimToNull(domain);
        if (value == null)
        {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("https://"))
        {
            value = value.substring(8);
        }
        else if (value.startsWith("http://"))
        {
            value = value.substring(7);
        }
        // user:pass@host —— 取最后一个 @ 之后的部分。
        // evil.com@real.com 这种串在浏览器眼里主机是 real.com，肉眼读起来却像 evil.com
        int at = value.lastIndexOf('@');
        if (at >= 0)
        {
            value = value.substring(at + 1);
        }
        for (char sep : new char[] { '/', '?', '#', '\\' })
        {
            int cut = value.indexOf(sep);
            if (cut >= 0)
            {
                value = value.substring(0, cut);
            }
        }
        return trimToNull(value);
    }

    /**
     * 主机名字符白名单。
     *
     * <p>归一化之后剩下的应当只有「字母数字 . - _ : 」——最后那个冒号是端口。
     * 出现别的字符说明这个值根本不是域名（填了句子、填了带空格的地址、粘了一段 JSON），
     * 与其存进去等着拼成一个奇怪的 URL，不如当场拒绝。</p>
     *
     * <p>不做更严格的域名语法校验（比如每一段的长度、TLD 白名单）：本地开发会填
     * {@code 127.0.0.1:5174}，客户可能带自己的内网域名，管太严只会逼人绕过。</p>
     */
    private void requireHostLike(String value, String label)
    {
        if (value == null)
        {
            return;
        }
        if (!value.matches("^[A-Za-z0-9._:-]+$"))
        {
            throw new ServiceException(label + "只能填域名或 IP（可带端口），不要带路径、空格或参数。"
                    + "当前值：" + value);
        }
    }

    private String domainConflictMessage(String shopCode, String domain)
    {
        StationSite occupied = siteMapper.selectByDomain(shopCode, domain);
        String name = occupied == null ? null : occupied.getSiteName();
        return "域名「" + domain + "」已经被站点"
                + (name == null ? "占用" : "「" + name + "」占用") + "，同一个域名只能建一条";
    }

    /** 直达判定里「只有独立站管理」的权限前缀；「进入管理」按钮本身要的是 list */
    private static final String STATION_PERM_PREFIX = "site:station:";
    private static final String STATION_LIST_PERM = "site:station:list";

    @Override
    public StationSite resolveDirectConsoleSite(String shopCode, Set<String> tenantPermissions, boolean platformAdmin)
    {
        if (platformAdmin || isBlank(shopCode) || tenantPermissions == null || tenantPermissions.isEmpty())
        {
            return null;
        }
        if (!tenantPermissions.contains(STATION_LIST_PERM)
                || !tenantPermissions.stream().allMatch(p -> p != null && p.startsWith(STATION_PERM_PREFIX)))
        {
            return null;
        }
        StationSite query = new StationSite();
        query.setShopCode(shopCode);
        // selectList 只带 secretconfigured 布尔列，密文不进内存
        List<StationSite> jumpable = siteMapper.selectList(query).stream()
                .filter(s -> !StationSite.isDisabled(s.getStatus()))
                .filter(s -> s.consoleBaseUrl() != null)
                .filter(s -> Boolean.TRUE.equals(s.getSecretConfigured()))
                .collect(java.util.stream.Collectors.toList());
        // 零个没得进，多个不知道进哪个——都交回工作台让人自己选
        return jumpable.size() == 1 ? jumpable.get(0) : null;
    }

    private void requireShop(String shopCode)
    {
        if (isBlank(shopCode))
        {
            throw new ServiceException("当前账号没有商家上下文，无法操作独立站");
        }
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }
}
