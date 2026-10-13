package com.ruoyi.station.console.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.config.ConsoleProperties;
import com.ruoyi.station.console.domain.SiteRole;
import com.ruoyi.station.console.domain.SiteUser;
import com.ruoyi.station.console.mapper.SiteRoleMapper;
import com.ruoyi.station.console.mapper.SiteUserMapper;

/**
 * 本站的用户体系。
 *
 * <p>docs/独立站与ERP的边界.md 要点 5：每套独立站自己成立用户体系，
 * 站与站之间、站与 ERP 之间都不关联。这个类是那句话的全部实现。</p>
 *
 * <h3>只有一个入口</h3>
 * <p>{@link #resolveFromErp} —— 从 ERP 点「进入管理」免密进来，见 {@code SsoController}。</p>
 *
 * <p>⚠ 这里以前还有一个 {@code login(username, password)}：§6a 给管理台加过账号密码登录。
 * <b>那一版撤掉了</b>，跟着走的还有「重置密码」「改自己的密码」「新建本地账号」——
 * 一个进不来的账号没有意义，一个没人能用的密码只是一份可以被爆破的负担。
 * 理由见 {@code SessionController} 的类注释。</p>
 *
 * <p>于是<b>用户是「长出来的」而不是「建出来的」</b>：谁第一次从 ERP 跳进来，
 * 本站就为他落一行，默认角色 {@code operator}，之后由站长在「用户管理」里调角色、
 * 停用或删除。</p>
 *
 * <h3>ERP 免密进来的人，权限由本站决定</h3>
 * <p>跳转令牌里<b>没有 perms</b>，这是有意的（§4.3）：ERP 只说「这是谁」，
 * 不说「他能干什么」。第一次跳进来的人落一个 {@code operator}，
 * 之后由站长在本站调。一个在 ERP 里是超管的人，在这个站也只是运营。</p>
 */
@Service
public class SiteUserService
{
    private static final Logger log = LoggerFactory.getLogger(SiteUserService.class);

    /** ERP 首次跳进来的人给这个角色。要更高权限由站长在本站授 */
    private static final String DEFAULT_ROLE = SiteRole.ROLE_OPERATOR;

    @Autowired
    private SiteUserMapper userMapper;

    @Autowired
    private SiteRoleMapper roleMapper;

    /** 只用来读 grant-all-perms 那一项，见 permsOf */
    @Autowired
    private ConsoleProperties consoleProperties;

    // ==================== 入场 ====================

    /**
     * 从 ERP 免密进入：按令牌里的 {@code payload.user} 找人，找不到就落一个。
     *
     * <p><b>这是本站唯一的入场路径</b>，也是唯一会往 {@code u_s_user} 里插行的地方。</p>
     *
     * @param erpUser  ERP 的登录账号，只作为身份标识
     * @param nickname ERP 上的显示名，只在首次落用户时用一下
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteUser resolveFromErp(String erpUser, String nickname, String clientIp)
    {
        String key = trimToNull(erpUser);
        if (key == null)
        {
            throw new ServiceException("跳转令牌里没有用户标识", 401);
        }

        SiteUser user = userMapper.selectByErpUser(key);
        if (user == null)
        {
            user = createFromErp(key, nickname);
            log.info("管理台首次免密进入，已为 ERP 用户 {} 建本站账号（角色 {}）。"
                    + "要给他更高权限，由站长在「用户管理」里改", key, DEFAULT_ROLE);
        }
        if (user.isDisabled())
        {
            // 站长在本站停用了这个人。ERP 那边还能点「进入管理」，但进不来——这是对的，
            // 本站的账号状态由本站说了算
            throw new ServiceException("你在本站的账号已被停用，请联系站长", 403);
        }

        userMapper.touchLogin(user.getId(), clientIp);
        return user;
    }

    /**
     * 首次从 ERP 进来时落一个用户。
     *
     * <p>登录名直接用 ERP 的账号名。撞了已有账号的话，加后缀避开——不能直接复用那一行：
     * 同名不等于同一个人，复用等于让任何一个 ERP 用户只要改个登录名就能顶掉本站某个账号。
     * 现在本站已经建不出新账号了，这一支只会碰到历史遗留的 {@code source=local} 行，
     * <b>但它必须留着</b>：那个「顶掉别人」的口子和账号怎么来的没关系。</p>
     */
    private SiteUser createFromErp(String erpUser, String nickname)
    {
        String username = erpUser;
        if (userMapper.selectByUsername(username) != null)
        {
            username = erpUser + "@erp";
            if (userMapper.selectByUsername(username) != null)
            {
                username = erpUser + "@erp" + System.currentTimeMillis() % 100000;
            }
        }
        SiteUser user = new SiteUser();
        user.setUsername(username);
        // ⚠ 没有 setPassword：u_s_user.password 那一列已经没有任何代码读写了（列还在，见建库脚本）。
        // 本站不存密码，也就没有一份可以被离线爆破的东西
        user.setNickname(trimToNull(nickname));
        user.setRoleCode(DEFAULT_ROLE);
        user.setSource(SiteUser.SOURCE_ERP_SSO);
        user.setErpUser(erpUser);
        user.setStatus(SiteUser.STATUS_NORMAL);
        user.setCreateUser("erp-sso");
        userMapper.insert(user);
        return user;
    }

    // ==================== 权限 ====================

    /**
     * 一个角色有哪些权限。
     *
     * <h3>⚠ 这里是「登录即全权」的唯一开关点</h3>
     *
     * <p>{@code console.grant-all-perms}（默认开，理由见 {@link ConsoleProperties}）为真时，
     * 不看角色，直接返回 {@code *}。</p>
     *
     * <p><b>为什么改在这一个方法里，而不是去改 AuthInterceptor 或者前端</b>：
     * 入场（{@code SsoController}）走它，而它的返回值同时喂给两个地方——</p>
     * <ol>
     *   <li>会话里的 {@code ConsoleIdentity.perms}，后端 {@code AuthInterceptor} 拿它判 403；</li>
     *   <li>{@code /api/me} 下发给前端，前端的 {@code v-hasPermi} 拿它决定按钮显隐。</li>
     * </ol>
     * <p>改在这里，两边天然是同一份，不会出现「按钮显示了但接口 403」或者反过来
     * 「按钮藏了但接口其实放行」。要是分别去改那两处，早晚会对不上。</p>
     *
     * <p>关掉这个开关时，下面原来的逻辑照旧：角色查不到时返回空列表<b>而不是放行</b>——
     * 库里那一行被删了，该表现为「什么都干不了」，不该表现为「什么都能干」。</p>
     */
    public List<String> permsOf(String roleCode)
    {
        if (consoleProperties.isGrantAllPerms())
        {
            // 不打日志：这是常态，每次登录都会走到，打了只是刷屏。
            // 启动时由 reportPermissionMode() 说一次，那一条才是运维要看的
            return List.of(SiteRole.ALL);
        }
        SiteRole role = roleCode == null ? null : roleMapper.selectByCode(roleCode);
        if (role == null)
        {
            log.warn("角色 {} 在 u_s_role 里不存在，按无权限处理", roleCode);
            return List.of();
        }
        return role.permList();
    }

    /**
     * 启动时把当前的权限模式喊一嗓子。
     *
     * <p>「登录即全权」是一个**看不出来**的状态：界面上一切正常，只是谁都能改任何东西。
     * 不在启动日志里说一句的话，接手的人要读到 permsOf 才会知道。</p>
     */
    @PostConstruct
    public void reportPermissionMode()
    {
        if (consoleProperties.isGrantAllPerms())
        {
            log.warn("权限模式：**登录即全权**（console.grant-all-perms=true）。"
                    + "任何登录进来的人都拿到 * ，u_s_user.rolecode 和 u_s_role.perms 不参与判定。"
                    + "要按角色卡权限，把这一项配成 false");
        }
        else
        {
            log.info("权限模式：按角色（u_s_role.perms）。ERP 免密首次进入的人默认 {}", DEFAULT_ROLE);
        }
    }

    /** 有没有这个权限。角色的 perms 是 {@code *} 时全放行 */
    public boolean hasPerm(String roleCode, String perm)
    {
        List<String> perms = permsOf(roleCode);
        return perms.contains(SiteRole.ALL) || perms.contains(perm);
    }

    public List<SiteRole> roles()
    {
        return roleMapper.selectAll();
    }

    // ==================== 用户管理 ====================

    public List<SiteUser> selectList(String keyword, String status)
    {
        return userMapper.selectList(trimToNull(keyword), trimToNull(status));
    }

    // ⚠ 这里以前有一个 create(user, rawPassword, operator)：站长在本站手工建账号。
    //
    // 删掉的理由不是「多余」，是**它建出来的账号谁都用不了**：本站没有登录页，
    // 而免密进入是按 erpuser 找人的，手工建的那一行根本匹配不上。留着它只会让人
    // 建完账号之后发现那个人还是进不来，然后来问为什么。
    //
    // 现在用户只有一个来源：谁从 ERP 跳进来，就为他落一行（createFromErp）。

    @Transactional(rollbackFor = Exception.class)
    public int update(SiteUser user, String operator)
    {
        if (user == null || user.getId() == null)
        {
            throw new ServiceException("缺少用户 ID");
        }
        SiteUser current = userMapper.selectById(user.getId());
        if (current == null)
        {
            throw new ServiceException("用户不存在");
        }
        if (user.getRoleCode() != null)
        {
            requireRole(user.getRoleCode());
        }
        // 把最后一个站长降级或停用，等于把所有人锁在门外——那之后没人能再改回来，
        // 只能去改数据库。这道闸挡的就是这个
        boolean losingAdmin = SiteRole.ROLE_ADMIN.equals(current.getRoleCode())
                && (user.getRoleCode() != null && !SiteRole.ROLE_ADMIN.equals(user.getRoleCode())
                    || SiteUser.STATUS_DISABLED.equals(user.getStatus()));
        if (losingAdmin)
        {
            requireAnotherAdmin(current.getId());
        }
        return userMapper.update(user);
    }

    // ⚠ 这里以前还有 resetPassword（站长给别人设密码）和 changeOwnPassword（改自己的）。
    //
    // 两个都跟着登录页一起删了。resetPassword 那个尤其要说清楚：它当时的作用是
    // 「给一个从 ERP 进来的人开本地登录」——也就是**在唯一入口旁边开一扇门**，
    // 正是 docs/独立站与ERP的边界.md §6d 要挡的事。

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("缺少用户 ID");
        }
        SiteUser current = userMapper.selectById(id);
        if (current == null)
        {
            throw new ServiceException("用户不存在");
        }
        if (SiteRole.ROLE_ADMIN.equals(current.getRoleCode()))
        {
            requireAnotherAdmin(id);
        }
        return userMapper.deleteById(id);
    }

    // ---- 内部 ----

    private void requireAnotherAdmin(Long excludeId)
    {
        if (userMapper.countActiveAdmins(SiteRole.ROLE_ADMIN, excludeId) == 0)
        {
            throw new ServiceException("这是最后一个可用的站长，不能停用/降级/删除。"
                    + "先给别人授站长角色再回来操作");
        }
    }

    private void requireRole(String roleCode)
    {
        if (roleCode != null && roleMapper.selectByCode(roleCode) == null)
        {
            throw new ServiceException("角色「" + roleCode + "」不存在");
        }
    }

    private static String trimToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
