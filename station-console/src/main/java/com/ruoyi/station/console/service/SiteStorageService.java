package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.config.StationProperties;
import com.ruoyi.station.console.domain.SiteStorage;
import com.ruoyi.station.console.mapper.SiteStorageMapper;
import com.ruoyi.station.console.util.AesUtils;

/**
 * 本站存储配置。
 *
 * <p>替代 ERP 的 {@code IShopStorageService}（对应表 {@code sdssys.s_m_shop_storage}）。
 * <b>那个 Service 和那张表都留在 ERP 原地不动</b>——它们不在 {@code sdsstation} 里，
 * 而且 ERP 自己的文件上传也在用（docs/独立站与ERP的边界.md §5.2 的那个警告）。</p>
 *
 * <h3>一期这个类几乎不干活</h3>
 * <p>文件落本机磁盘（§10.3.8），{@code provider} 恒为 {@code local}，没有密钥要管。
 * 它存在的理由是让 {@code /api/file/storage} 那两个接口还在——
 * §10.3.6 要求「对外的 URL 路径不变，station-console-ui 不用跟着改」。</p>
 *
 * <p>真接对象存储时，把 {@link #requireActive} 的返回值交给一个上传实现即可，
 * 表结构和这两个接口都不用动。</p>
 */
@Service
public class SiteStorageService
{
    @Autowired
    private SiteStorageMapper storageMapper;

    @Autowired
    private StationProperties properties;

    /**
     * 当前生效的那一套。
     *
     * <p>库里一行都没有时<b>造一个 local 返回</b>，不抛异常：新装的机器上
     * {@code u_s_storage} 可能是空的（建库脚本没插数据），而那时候上传应该照样能用——
     * 一期本来就只落本机磁盘，不需要任何配置。</p>
     */
    public SiteStorage requireActive()
    {
        SiteStorage active = storageMapper.selectActive();
        if (active != null)
        {
            return active;
        }
        SiteStorage local = new SiteStorage();
        local.setProvider(SiteStorage.PROVIDER_LOCAL);
        local.setStatus("0");
        local.setActive("1");
        local.setPublicUrl(properties.getUpload().normalizedBaseUrl());
        return local;
    }

    /**
     * 回显用的配置。
     *
     * <p>{@code accessKeySecret} 不会出现在返回值里——{@link SiteStorage} 上那个字段
     * 挂着 {@code @JsonIgnore}，前端要判「配没配过」看 {@code secretConfigured}。</p>
     *
     * <p>ERP 那版是靠 Service 层每次手工置 null 来防下发的，只要有人新加一条查询路径就会漏；
     * 放在字段上是一道更难绕过的闸。</p>
     */
    public SiteStorage selectForDisplay(String provider)
    {
        if (provider == null || provider.isBlank())
        {
            // 不指定厂商 = 「当前用的是哪套」，打开弹窗时问的就是这个
            return requireActive();
        }
        // ⚠ 指定了厂商就只答这一家，<b>没配过就返回 null</b>。
        //
        // 这里原来写的是 `stored == null ? requireActive() : stored`——退回「当前生效的那套」。
        // 那一行让管理台的厂商切换**完全用不了**，而且症状看着和后端毫无关系：
        //
        //   点「腾讯云 COS」→ 前端 GET ?provider=tencent → 腾讯云没配过 → 后端退回 local 那套
        //   → 前端按约定「有 data 就整体铺进表单」→ provider 被覆盖成 local
        //   → 三个 tab 一个都不选中，字段还是上一家的
        //
        // 前端那边写的是「data 为 null 表示这家没配过，除厂商外全部清空」——
        // 它一直在等一个 null，而这一侧永远不给。契约对不上，两边单看都像是对的。
        return storageMapper.selectByProvider(provider.trim());
    }

    /** 配过哪几家。前端用它决定「切换存储」下拉里哪些是灰的 */
    public List<String> configuredProviders()
    {
        List<String> result = new ArrayList<>();
        for (SiteStorage item : storageMapper.selectAll())
        {
            result.add(item.getProvider());
        }
        return result;
    }

    /** 当前生效的是哪一家 */
    public String currentProvider()
    {
        return requireActive().getProvider();
    }

    /**
     * 保存一套配置。
     *
     * <p>密钥用 {@code AesUtils}（AES-GCM）加密后落库，主密钥来自
     * {@code station.config-secret}。<b>不要再发明第二种加密方式</b>——
     * 两套加密就有两个主密钥要轮换，而第二套一定是那个没人记得怎么轮换的
     * （§7.1 那条规矩）。</p>
     *
     * <p>没配主密钥时直接失败，<b>不给硬编码兜底</b>：兜底意味着密钥实际上是明文存的，
     * 而库被拖走的时候没人会记得这一点。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(SiteStorage storage, String operator)
    {
        if (storage == null || storage.getProvider() == null || storage.getProvider().isBlank())
        {
            throw new ServiceException("请选择存储厂商");
        }
        String provider = storage.getProvider().trim();

        // 明文密钥只有在这一次请求里出现过。传上来是空的表示「不改密钥」，
        // 这时把它置 null，mapper 的 upsert 里那个 coalesce 会保留库里已有的密文
        String plain = storage.getAccessKeySecret();
        if (plain != null && !plain.isBlank())
        {
            storage.setAccessKeySecret(AesUtils.encrypt(plain, requireConfigSecret()));
        }
        else
        {
            storage.setAccessKeySecret(null);
        }

        storage.setProvider(provider);
        storage.setActive("1");
        storage.setUpdateBy(operator);
        storage.setCreateBy(operator);
        storageMapper.upsert(storage);
        // 顺序：先写自己（active=1），再把别人置 0。反过来的话，中间那一瞬间
        // 一行 active 都没有，正好赶上一次上传就会走到「没配存储」的分支
        storageMapper.deactivateOthers(provider);
    }

    /**
     * 某一家<b>已经存在库里</b>的密钥明文。
     *
     * <p>最初存在的理由：保存配置时的连通性自检
     * （{@code SiteFileService.verifyStorage}）要真的连一次 COS，而「留空 = 不改密钥」
     * 这个约定意味着请求体里可能根本没有密钥——那时候只能用库里已经存着的那把。</p>
     *
     * <p>⚠ 和 {@link #decryptSecret} 同一条规矩：返回值不许出现在任何响应、任何日志里。
     * 调用点见那个方法的注释。</p>
     */
    public String storedSecret(String provider)
    {
        if (provider == null || provider.isBlank())
        {
            return null;
        }
        return decryptSecret(storageMapper.selectByProvider(provider.trim()));
    }

    /**
     * 解出明文密钥。<b>只有需要真的去调腾讯云的路径能调</b>，返回值不许出现在任何响应里。
     *
     * <p>调用点<b>就这三个</b>：</p>
     * <ul>
     *   <li>{@code SiteFileService.storeCos} —— 真上传；</li>
     *   <li>{@code SiteFileService.verifyStorage} —— 保存配置时放一个探针对象上去（经
     *       {@link #storedSecret}）；</li>
     *   <li>{@code SiteTranslateService.credentials} —— 机器翻译（2026-09-13 加，经
     *       {@link #storedSecret}）。腾讯云的 SecretId/SecretKey 是<b>账号级</b>的，
     *       一对通吃 COS 和 TMT，所以翻译不另存一份密钥——多存一份就多一处泄漏面，
     *       那正是这条规矩要防的东西。</li>
     * </ul>
     *
     * <p><b>加第四个之前先掂量</b>——每多一个调用点，就多一个明文密钥可能被日志打出去、
     * 被塞进响应体的地方。判据不是「在哪个类里」，是这两条：
     * ① 它是不是真的要拿密钥去调腾讯云（而不是只想展示给人看）；
     * ② 拿到的值有没有可能流进响应体或日志。第二条答不上来就别加。</p>
     */
    public String decryptSecret(SiteStorage storage)
    {
        if (storage == null || storage.getAccessKeySecret() == null
                || storage.getAccessKeySecret().isBlank())
        {
            return null;
        }
        return AesUtils.decrypt(storage.getAccessKeySecret(), requireConfigSecret());
    }

    private String requireConfigSecret()
    {
        String secret = properties.getConfigSecret();
        if (secret == null || secret.trim().isEmpty())
        {
            throw new ServiceException("还没配置加密主密钥（station.config-secret），"
                    + "存储密钥不能明文入库。用环境变量 STATION_CONFIG_SECRET 注入一个高熵随机串");
        }
        return secret;
    }
}
