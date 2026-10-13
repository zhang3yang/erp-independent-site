<!--
  买家中心。左栏五格 + 右侧面板：我的资料（含头像）/ 收货地址 / 我的订单（**来自服务端**）
  / 安全（改密码）/ 绑定的登录方式。

  ## 版式照参考站 kangwanjia-website-EN 的 pages/profile

  左栏那张卡 + 右侧面板、头像上传 + 六个色块，都照它。三处刻意不一样：

    1. **头像存服务端**，不是 localStorage。那边页面上写着「nothing is uploaded
       anywhere」，照搬会和这个站的登录自相矛盾——买家账号的意义就是「换台电脑还在」。
       裁剪缩放仍然在浏览器里做（useAvatar.ts）。
    2. **邮箱只读**。那边是 stub 改了没后果；这边邮箱是账号身份，换邮箱要走验证流程。
    3. **「绑定的登录方式」是如实的空**。那边是个微信绑定 stub（填手机号存本地，
       没有扫码也没有后端），照搬过来就是一个「点了像绑定了其实什么都没发生」的按钮。

  比它多一格「订单」：那边订单是独立一页，而这个站的 /orders 仍然是给没登录的人用的
  本地台账，服务端那份订单只有这一页看得到。

  ## 收货地址簿（2026-09-08 加的）

  在此之前收货地址只在结账页填一次，存在**买家自己浏览器**的 localStorage
  `station-buyer` 里（checkout/index.vue 的 loadBuyer/saveBuyer）——换台电脑、
  清一次缓存就没了，而登录这件事的全部意义就是「换台电脑还在」。
  现在存 u_s_buyeraddr，按会话里的客户编码查。

  ⚠ 结账页那份 localStorage **没有删**：下单不要求登录，没登录的人仍然靠它免于重填。
    登录的人两份都有，地址簿优先——见 checkout/index.vue 里那段。

  ⚠ 地址簿改动**不影响历史订单**。订单上的收货地是下单当时的快照，
    货已经按旧地址发出去了。后端那边也没有任何一处回写订单。

  ## 这一页是买家登录唯一真正的用处

  在此之前「我的订单」（/orders）是一份 localStorage 台账，那一页上专门有一句提示
  告诉买家「这份列表只存在这台浏览器里」——换台电脑就全没了。
  这一页的列表来自 u_s_order，按会话里的客户编码查，换设备、换浏览器都在。

  ⚠ /orders 那一页**没动**：它仍然是本地台账，给没登录的人用（下单不要求登录）。
    把两份合并（登录了就显示服务端的、否则显示本地的）是下一步，
    useOrders.ts 的头注释里已经写了要怎么改。分开做是为了不和正在改订单模块的人撞车。

  ## ⚠ 订单归属在后端判，不在这里

  列表和详情都不接受「查谁的」参数，客户编码只从会话取
  （BuyerAccountController.requireBuyer）。详情还额外确认那张单属于当前买家——
  单号是有规律的流水号，少了那一层，一个登录用户挨个试就能看光全站的订单。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap acct-page">
      <!-- 还没问出「我是谁」：先摆骨架，别闪一下「请登录」再跳成买家名字 -->
      <div v-if="buyer === undefined" aria-hidden="true">
        <div class="skeleton-block sk-bar"></div>
        <div class="skeleton-block sk-body"></div>
      </div>

      <!-- 确认没登录 -->
      <div v-else-if="!buyer" class="acct-guest">
        <h1>{{ t.acctMyAccount }}</h1>
        <p class="muted">{{ t.acctGuestLead }}</p>
        <NuxtLink :to="shopUrl(localePath('/login'))" class="btn">{{ t.acctSignInCta }}</NuxtLink>
        <NuxtLink :to="shopUrl(localePath('/register'))" class="btn btn-ghost">{{ t.acctRegisterCta }}</NuxtLink>
      </div>

      <template v-else>
        <header class="acct-head">
          <div>
            <h1>{{ t.acctMyAccount }}</h1>
            <p class="muted">{{ buyer.displayName }} · {{ buyer.email }}</p>
          </div>
          <button type="button" class="btn btn-ghost" @click="doLogout">{{ t.acctSignOut }}</button>
        </header>

        <div class="pf-layout">
          <!-- ---------------- 左栏 ---------------- -->
          <aside class="rail">
            <div class="rail-me">
              <UserAvatar :src="buyer.avatar" :initials="initials" :size="52" />
              <span class="rail-id">
                <strong>{{ buyer.displayName }}</strong>
                <small>{{ buyer.email }}</small>
              </span>
            </div>

            <nav class="rail-nav" :aria-label="t.acctSections">
              <button v-for="s in SECTIONS" :key="s.id" type="button" class="rail-link"
                :class="{ on: section === s.id }"
                :aria-current="section === s.id ? 'page' : undefined"
                @click="section = s.id">
                <span class="rail-ico" aria-hidden="true">{{ s.icon }}</span>
                {{ t[s.key] }}
              </button>
            </nav>
          </aside>

          <!-- ---------------- 右侧面板 ---------------- -->
          <div class="pane">

        <!-- ===================== 我的资料 ===================== -->
        <section v-if="section === 'profile'" class="acct-block">
          <h2>{{ t.acctProfile }}</h2>
          <p class="muted block-sub">{{ t.acctProfileLead }}</p>

          <p v-if="detailsError" class="acct-msg is-error">{{ detailsError }}</p>
          <p v-else-if="detailsDone" class="acct-msg is-ok">{{ t.acctDetailsSaved }}</p>

          <!-- 头像：传图 或 挑色块。两个都是点完立刻生效，不跟着下面那张表单走 -->
          <div class="av-block">
            <UserAvatar :src="buyer.avatar" :initials="initials" :size="92" />
            <div class="av-side">
              <div class="av-acts">
                <button type="button" class="btn btn-ghost" :disabled="avatarBusy" @click="pickFile">
                  {{ avatarBusy ? t.acctWorking : t.acctAvatarUpload }}
                </button>
                <button v-if="buyer.avatar" type="button" class="mini mini-danger"
                  :disabled="avatarBusy" @click="setAvatar(null)">
                  {{ t.acctAvatarRemove }}
                </button>
              </div>
              <!-- ⚠ 这句和参考站不一样：那边存 localStorage，这边存账号，见 useAvatar.ts -->
              <p class="muted av-hint">{{ fill(t.acctAvatarHint, { size: AVATAR_SIZE }) }}</p>
              <p v-if="avatarError" class="err">{{ avatarError }}</p>
              <input ref="fileEl" type="file" accept="image/*" class="sr-only" @change="onFile">
            </div>
          </div>

          <fieldset class="presets">
            <legend>{{ t.acctAvatarPick }}</legend>
            <div class="preset-row">
              <button v-for="p in AVATAR_PRESETS" :key="p.id" type="button" class="preset"
                :class="{ on: buyer.avatar === `preset:${p.id}` }"
                :style="{ background: `linear-gradient(140deg, ${p.from}, ${p.to})` }"
                :aria-label="fill(t.acctAvatarUsePreset, { name: p.id })"
                :aria-pressed="buyer.avatar === `preset:${p.id}`"
                :disabled="avatarBusy"
                @click="setAvatar(`preset:${p.id}`)">
                {{ initials }}
              </button>
            </div>
          </fieldset>

          <form class="details-form" @submit.prevent="submitDetails">
            <label class="field">
              <span>{{ t.acctDisplayName }}</span>
              <input v-model.trim="details.name" maxlength="100" autocomplete="name"
                :disabled="detailsBusy">
            </label>

            <!--
              ⚠ 邮箱只读，和参考站不一样。那边是个 stub，改了没有后果；
                这边邮箱是账号的身份（唯一键、登录名、找回密码的去处），
                换邮箱要走一遍验证流程，后端也不收这个字段。
                摆在这里但禁用，比干脆不显示强——买家要能看到自己用哪个邮箱登录。
            -->
            <label class="field">
              <span>{{ t.acctEmail }}</span>
              <input :value="buyer.email" type="email" readonly disabled>
              <small class="muted">{{ t.acctEmailFixed }}</small>
            </label>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.acctCompany }}</span>
                <input v-model.trim="details.company" maxlength="200"
                  autocomplete="organization" :disabled="detailsBusy">
              </label>
              <label class="field">
                <span>{{ t.acctPhone }}</span>
                <input v-model.trim="details.phone" maxlength="50" autocomplete="tel"
                  :disabled="detailsBusy">
              </label>
            </div>

            <label class="field">
              <span>{{ t.acctCountry }}</span>
              <input v-model.trim="details.country" maxlength="80" autocomplete="country-name"
                :disabled="detailsBusy">
            </label>

            <button type="submit" class="btn" :disabled="detailsBusy">
              {{ detailsBusy ? t.acctWorking : t.acctSaveChanges }}
            </button>
          </form>
        </section>

        <!-- ===================== 我的订单 ===================== -->
        <section v-else-if="section === 'orders'" class="acct-block">
          <h2>{{ t.acctMyOrders }}</h2>

          <p v-if="ordersError" class="acct-msg is-error">{{ ordersError }}</p>

          <div v-else-if="loadingOrders" aria-hidden="true">
            <div class="skeleton-block sk-row"></div>
            <div class="skeleton-block sk-row"></div>
          </div>

          <p v-else-if="!orders.length" class="muted">{{ t.acctOrdersEmpty }}</p>

          <ul v-else class="ord-list">
            <li v-for="o in orders" :key="o.orderCode" class="ord-row">
              <div class="ord-main">
                <span class="ord-code">{{ o.orderCode }}</span>
                <OrderStatusPill :status="o.status" :lang="site.lang" />
              </div>
              <div class="ord-meta muted">
                <span>{{ formatDateShort(o.createTime || '', site.lang) }}</span>
                <!-- 面议单不显示金额：显示 0.00 会被当成已经报过价了 -->
                <span v-if="o.inquireOnly" class="ord-amount">{{ t.inquire }}</span>
                <span v-else class="ord-amount">{{ money(o.subtotal) }}</span>
              </div>
            </li>
          </ul>

          <p v-if="total > orders.length" class="muted more-note">
            {{ fill(t.acctOrdersMore, { shown: orders.length, total }) }}
          </p>
        </section>

        <!-- ===================== 收货地址簿 ===================== -->
        <section v-else-if="section === 'addresses'" class="acct-block">
          <div class="block-head">
            <div>
              <h2>{{ t.acctAddrTitle }}</h2>
              <p class="muted block-sub">{{ t.acctAddrLead }}</p>
            </div>
            <button v-if="!addrForm" type="button" class="btn btn-ghost" @click="newAddr">
              {{ t.acctAddrAdd }}
            </button>
          </div>

          <p v-if="addrError" class="acct-msg is-error">{{ addrError }}</p>
          <p v-else-if="addrDone" class="acct-msg is-ok">{{ addrDone }}</p>

          <!-- 新增和修改是同一张表单：后端也是同一个接口，带 id 就是改 -->
          <form v-if="addrForm" class="addr-form" @submit.prevent="saveAddr">
            <h3>{{ addrForm.id ? t.acctAddrEdit : t.acctAddrNew }}</h3>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.acctAddrLabel }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="addrForm.label" maxlength="50"
                  :placeholder="t.acctAddrLabelHint" :disabled="addrBusy">
              </label>
              <label class="field">
                <span>{{ t.acctAddrRecipient }} *</span>
                <input v-model.trim="addrForm.contactName" maxlength="100" autocomplete="name"
                  :class="{ bad: addrErrors.contactName }" :disabled="addrBusy">
                <em v-if="addrErrors.contactName" class="err">{{ addrErrors.contactName }}</em>
              </label>
            </div>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.coCountry }} *</span>
                <input v-model.trim="addrForm.country" maxlength="80" autocomplete="country-name"
                  :class="{ bad: addrErrors.country }" :disabled="addrBusy">
                <em v-if="addrErrors.country" class="err">{{ addrErrors.country }}</em>
              </label>
              <label class="field">
                <span>{{ t.coCity }}</span>
                <input v-model.trim="addrForm.city" maxlength="80" autocomplete="address-level2"
                  :disabled="addrBusy">
              </label>
            </div>

            <label class="field">
              <span>{{ t.coLine1 }} <em class="opt">{{ t.coOptional }}</em></span>
              <input v-model.trim="addrForm.addrLine1" maxlength="200" autocomplete="address-line1"
                :disabled="addrBusy">
            </label>

            <div class="grid-3">
              <label class="field">
                <span>{{ t.coLine2 }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="addrForm.addrLine2" maxlength="200"
                  autocomplete="address-line2" :disabled="addrBusy">
              </label>
              <label class="field">
                <span>{{ t.coRegion }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="addrForm.region" maxlength="80" autocomplete="address-level1"
                  :disabled="addrBusy">
              </label>
              <label class="field">
                <span>{{ t.coPostal }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="addrForm.postal" maxlength="30" autocomplete="postal-code"
                  :disabled="addrBusy">
              </label>
            </div>

            <label class="field">
              <span>{{ t.coPhone }} <em class="opt">{{ t.coOptional }}</em></span>
              <input v-model.trim="addrForm.phone" maxlength="50" autocomplete="tel"
                :disabled="addrBusy">
            </label>

            <label class="check">
              <input v-model="addrMakeDefault" type="checkbox" :disabled="addrBusy">
              <span>{{ t.acctAddrMakeDefault }}</span>
            </label>

            <div class="form-foot">
              <button type="submit" class="btn" :disabled="addrBusy">
                {{ addrBusy ? t.acctWorking : t.acctSaveCta }}
              </button>
              <button type="button" class="btn btn-ghost" :disabled="addrBusy"
                @click="addrForm = null">
                {{ t.cancel }}
              </button>
            </div>
          </form>

          <div v-if="loadingAddrs" aria-hidden="true">
            <div class="skeleton-block sk-row"></div>
          </div>

          <p v-else-if="!addresses.length && !addrForm" class="muted">{{ t.acctAddrEmpty }}</p>

          <ul v-else-if="addresses.length" class="addr-list">
            <li v-for="a in addresses" :key="a.id" class="addr-card"
              :class="{ 'is-default': a.isDefault }">
              <div class="addr-top">
                <!-- 没起备注名的显示「国家 · 城市」，比显示一个空标签强 -->
                <span class="addr-label">{{ a.label || [a.country, a.city].filter(Boolean).join(' · ') }}</span>
                <span v-if="a.isDefault" class="addr-flag">{{ t.acctAddrDefault }}</span>
              </div>
              <address class="addr-body">
                <strong>{{ a.contactName }}</strong>
                <span v-if="addrStreet(a)">{{ addrStreet(a) }}</span>
                <span v-if="addrCityLine(a)">{{ addrCityLine(a) }}</span>
                <span>{{ a.country }}</span>
                <span v-if="a.phone" class="muted">{{ a.phone }}</span>
              </address>
              <div class="addr-acts">
                <button v-if="!a.isDefault" type="button" class="mini" :disabled="addrBusy"
                  @click="makeDefault(a.id)">
                  {{ t.acctAddrMakeDefault }}
                </button>
                <button type="button" class="mini" :disabled="addrBusy" @click="editAddr(a)">
                  {{ t.acctAddrEdit }}
                </button>
                <button type="button" class="mini mini-danger" :disabled="addrBusy"
                  @click="dropAddr(a)">
                  {{ t.acctAddrDelete }}
                </button>
              </div>
            </li>
          </ul>
        </section>

        <!-- ===================== 安全（改密码） ===================== -->
        <section v-else-if="section === 'security'" class="acct-block">
          <h2>{{ t.acctChangePassword }}</h2>
          <p class="muted block-sub">{{ t.acctSecurityLead }}</p>

          <p v-if="pwdError" class="acct-msg is-error">{{ pwdError }}</p>
          <p v-else-if="pwdDone" class="acct-msg is-ok">{{ t.acctPwdChanged }}</p>

          <form class="pwd-form" @submit.prevent="submitPassword">
            <label class="field">
              <span>{{ t.acctOldPassword }}</span>
              <input v-model="pwd.old" type="password" autocomplete="current-password"
                required :disabled="pwdBusy">
            </label>
            <label class="field">
              <span>{{ t.acctNewPassword }}</span>
              <input v-model="pwd.next" type="password" autocomplete="new-password"
                required :minlength="MIN" :disabled="pwdBusy">
              <small class="muted">{{ fill(t.acctPwdMin, { n: MIN }) }}</small>
            </label>
            <button type="submit" class="btn" :disabled="pwdBusy">
              {{ pwdBusy ? t.acctWorking : t.acctSaveCta }}
            </button>
          </form>
        </section>

        <!-- ===================== 绑定的登录方式 ===================== -->
        <!--
          ⚠ 这一块现在是空的，而且是**如实的空**，不是没做完。

          参考站（kangwanjia-website-EN）那一栏是「微信绑定」，但那是个纯前端 stub：
          填一个手机号存进 localStorage，没有扫码、没有微信开放平台、没有后端。
          照搬到这个真站上会变成一个「点了像是绑定了、其实什么都没发生」的按钮——
          那比没有这一栏糟。

          所以这里只说实话：这个站现在只有邮箱 + 密码一种登录方式。
          将来真接了微信/Google 登录，绑定入口放这里。
        -->
        <section v-else class="acct-block">
          <h2>{{ t.acctLinked }}</h2>
          <p class="muted block-sub">{{ t.acctLinkedLead }}</p>

          <div class="link-row">
            <span class="link-mark" aria-hidden="true">✉</span>
            <div class="link-text">
              <strong>{{ t.acctLinkedEmail }}</strong>
              <span class="muted">{{ buyer.email }}</span>
            </div>
            <span class="link-on">{{ t.acctLinkedInUse }}</span>
          </div>

          <p class="muted link-none">{{ t.acctLinkedNone }}</p>
        </section>

          </div>
        </div>
      </template>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { formatDateShort, formatMoney } from '~/lib/shop'
import type { BuyerAddress, BuyerAddressForm, BuyerOrder } from '~/composables/useBuyer'
import { AVATAR_PRESETS, AVATAR_SIZE, initialsOf, readAvatarFile } from '~/composables/useAvatar'

const site = await useSiteInfo()
const localePath = useLocalePath()
// 买家中心在 buyer. 子域上，指回商城的链接必须是主域绝对地址——
// 客户端路由不经过 domain-split 中间件，相对路径会把商城页面渲染在买家域下
const { shopUrl } = useSiteHosts()
const t = useShopCopy(() => site.lang)

const {
  buyer, refresh, logout, myOrders, changePassword,
  updateProfile, updateAvatar, myAddresses, saveAddress, setDefaultAddress, removeAddress
} = useBuyer()

/** 和后端 station.buyer.password-min-length 的默认值对齐 */
const MIN = 8

/**
 * 和后端 SiteBuyerAddressService.MAX_ADDRESSES 对齐。
 *
 * ⚠ 前端这一份是为了**说人话**：后端那句拒绝是中文的，而这个站的买家是海外的。
 *   真正的闸在后端（前端这份改一改就没了），这里只是让常见的那次拒绝有译文。
 *   两个数字要一起改。
 */
const MAX_ADDRESSES = 20

/* ---------------- 左栏的五格 ---------------- */

type SectionId = 'profile' | 'addresses' | 'orders' | 'security' | 'linked'

/**
 * 版式照参考站（kangwanjia-website-EN 的 pages/profile）。
 *
 * ⚠ 比它多一格「订单」：那边的订单是独立的一页（/orders），而这个站的
 *   /orders 仍然是给**没登录的人**用的 localStorage 台账，服务端那份订单
 *   只有这一页看得到。少这一格就是把一个已经在用的功能藏起来。
 */
const SECTIONS: { id: SectionId; key: string; icon: string }[] = [
  { id: 'profile', key: 'acctProfile', icon: '👤' },
  { id: 'addresses', key: 'acctAddrTitle', icon: '📍' },
  { id: 'orders', key: 'acctMyOrders', icon: '📦' },
  { id: 'security', key: 'acctSecurity', icon: '🔑' },
  { id: 'linked', key: 'acctLinked', icon: '🔗' }
]
/**
 * 当前分区，跟着地址栏的 hash 走（`/account#addresses`）。
 *
 * 不是为了好看：不挂 hash 的话，买家在「收货地址」里按一下刷新就被扔回「我的资料」，
 * 而地址那一栏正是他最可能反复进出的地方（填一条、看一眼、再填一条）。
 * 顺带也能把某一栏的链接直接发给人。
 *
 * ⚠ 用 `replaceState` 不用 `router.push`：分区切换不该在浏览器历史里堆一堆条目，
 *   点五下左栏再按后退键要退五次才离开这一页。
 */
const route = useRoute()
const section = ref<SectionId>(
  SECTIONS.some(s => s.id === route.hash.slice(1))
    ? (route.hash.slice(1) as SectionId)
    : 'profile'
)
watch(section, id => {
  if (import.meta.client) history.replaceState(history.state, '', `#${id}`)
})

/* ---------------- 头像 ---------------- */

/** 首字母字块用联系人名，没有就退到 displayName（公司名 → 邮箱） */
const initials = computed(() => initialsOf(buyer.value?.name || buyer.value?.displayName))

const fileEl = ref<HTMLInputElement | null>(null)
const avatarBusy = ref(false)
const avatarError = ref('')

function pickFile() {
  avatarError.value = ''
  fileEl.value?.click()
}

/**
 * 选了一张图：在浏览器里裁成正方形、缩到 256px，再存到账号上。
 *
 * ⚠ 裁在前端、存在后端。参考站是「裁在前端、存 localStorage」，那句
 *   「nothing is uploaded anywhere」在这个站上不成立——换台电脑还在才是
 *   买家账号的意义。理由见 useAvatar.ts 的头注释。
 */
async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 选完就把 input 清掉：不清的话，删了头像想再传同一张文件不会触发 change
  input.value = ''
  if (!file) return
  avatarError.value = ''
  avatarBusy.value = true
  try {
    await setAvatar(await readAvatarFile(file), true)
  } catch (err: unknown) {
    const reason = (err as Error)?.message
    avatarError.value = reason === 'not-an-image' ? t.value.acctAvatarNotImage! : t.value.acctAvatarUnreadable!
  } finally {
    avatarBusy.value = false
  }
}

/** 存头像。`keepBusy` 是给上面那条用的——它自己管 busy，别在这里提前关掉 */
async function setAvatar(value: string | null, keepBusy = false) {
  avatarError.value = ''
  if (!keepBusy) avatarBusy.value = true
  try {
    // 后端回的是改完的整份档案，updateAvatar 内部已经写回 buyer，
    // 所以顶栏那个头像也跟着变，不用自己同步
    await updateAvatar(value)
  } catch (err: unknown) {
    avatarError.value = (err as Error)?.message || t.value.acctFailed
  } finally {
    if (!keepBusy) avatarBusy.value = false
  }
}

const orders = ref<BuyerOrder[]>([])
const total = ref(0)
const loadingOrders = ref(false)
const ordersError = ref('')

const pwd = reactive({ old: '', next: '' })
const pwdBusy = ref(false)
const pwdError = ref('')
const pwdDone = ref(false)

/* ---------------- 我的资料 ---------------- */
const details = reactive({ name: '', company: '', phone: '', country: '' })
const detailsBusy = ref(false)
const detailsError = ref('')
const detailsDone = ref(false)

/* ---------------- 收货地址簿 ---------------- */
const addresses = ref<BuyerAddress[]>([])
const loadingAddrs = ref(false)
/** null = 没在编辑。有值时那张表单显示出来，带 id 是改、不带是新增 */
const addrForm = ref<BuyerAddressForm | null>(null)
const addrMakeDefault = ref(false)
const addrErrors = reactive<Record<string, string>>({})
const addrBusy = ref(false)
const addrError = ref('')
const addrDone = ref('')

const money = (n: number) => formatMoney(n, site.currency, site.lang)

/**
 * 会话是 HttpOnly Cookie，服务端渲染这一页时拿不到，所以身份和订单都在挂载后取
 * （见 useBuyer 的头注释）。
 */
onMounted(async () => {
  await refresh()
  if (!buyer.value) return

  // 资料表单的初值就是刚问回来的那份档案
  details.name = buyer.value.name || ''
  details.company = buyer.value.company || ''
  details.phone = buyer.value.phone || ''
  details.country = buyer.value.country || ''

  loadingOrders.value = true
  loadingAddrs.value = true
  // 订单和地址簿两个接口互不依赖，并发发出去。串起来的话这一页要等两个来回
  await Promise.all([
    myOrders(1, 20)
      .then((res) => {
        orders.value = res.rows || []
        total.value = res.total || 0
      })
      .catch((e: unknown) => {
        ordersError.value = (e as Error)?.message || t.value.acctFailed
      })
      .finally(() => { loadingOrders.value = false }),
    myAddresses()
      .then((rows) => { addresses.value = rows || [] })
      .catch((e: unknown) => {
        addrError.value = (e as Error)?.message || t.value.acctFailed
      })
      .finally(() => { loadingAddrs.value = false })
  ])
})

/* ---------------- 我的资料 ---------------- */

async function submitDetails() {
  if (detailsBusy.value) return
  detailsBusy.value = true
  detailsError.value = ''
  detailsDone.value = false
  try {
    // 后端回的是改完的整份档案，updateProfile 内部已经把它写回 buyer 了，
    // 所以顶栏显示的 displayName（公司名 → 联系人 → 邮箱）会跟着变
    await updateProfile({ ...details })
    detailsDone.value = true
  } catch (e: unknown) {
    detailsError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    detailsBusy.value = false
  }
}

/* ---------------- 收货地址簿 ---------------- */

/** 地址的街道那一行：两行地址拼起来，都空就不显示这一行 */
function addrStreet(a: BuyerAddress) {
  return [a.addrLine1, a.addrLine2].filter(Boolean).join(', ')
}

/** 「城市, 州省 邮编」。三个都可能是空的，所以逐段拼，不写死逗号 */
function addrCityLine(a: BuyerAddress) {
  const left = [a.city, a.region].filter(Boolean).join(', ')
  return [left, a.postal].filter(Boolean).join(' ')
}

function blankAddr(): BuyerAddressForm {
  return {
    label: '', contactName: '', phone: '',
    addrLine1: '', addrLine2: '', city: '', region: '', postal: '',
    // 国家预填成他资料里那个：多数人只有一个收货国，省一次输入
    country: details.country || ''
  }
}

function newAddr() {
  if (addresses.value.length >= MAX_ADDRESSES) {
    addrError.value = fill(t.value.acctAddrFull!, { n: MAX_ADDRESSES })
    return
  }
  clearAddrMsg()
  addrForm.value = blankAddr()
  // 第一条地址后端一定会设成默认，这里把勾也打上，免得界面和结果对不上
  addrMakeDefault.value = addresses.value.length === 0
}

function editAddr(a: BuyerAddress) {
  clearAddrMsg()
  addrForm.value = {
    id: a.id,
    label: a.label || '',
    contactName: a.contactName || '',
    phone: a.phone || '',
    addrLine1: a.addrLine1 || '',
    addrLine2: a.addrLine2 || '',
    city: a.city || '',
    region: a.region || '',
    postal: a.postal || '',
    country: a.country || ''
  }
  addrMakeDefault.value = a.isDefault
}

function clearAddrMsg() {
  addrError.value = ''
  addrDone.value = ''
  for (const k of Object.keys(addrErrors)) delete addrErrors[k]
}

/**
 * 收件人和国家必填，和结账页、和后端是同一条规则。
 *
 * ⚠ 前端先判一遍不是为了省一次请求，是为了**语言**：后端那几句拒绝是中文的
 *   （"请填写收件人"），而这个站的买家是海外的。常见的那两次拒绝在这里出译文，
 *   真正的闸仍然在后端。
 */
function validateAddr() {
  for (const k of Object.keys(addrErrors)) delete addrErrors[k]
  const f = addrForm.value
  if (!f) return false
  if (!f.contactName) addrErrors.contactName = t.value.acctAddrBadRecipient!
  if (!f.country) addrErrors.country = t.value.acctAddrBadCountry!
  return !Object.keys(addrErrors).length
}

async function saveAddr() {
  if (addrBusy.value || !addrForm.value) return
  if (!validateAddr()) return
  addrBusy.value = true
  addrError.value = ''
  addrDone.value = ''
  try {
    await saveAddress(addrForm.value, addrMakeDefault.value)
    // 保存接口只回那一条。默认标记可能挪了位置，所以整份重取，不在本地拼
    addresses.value = await myAddresses()
    addrForm.value = null
    addrDone.value = t.value.acctAddrSaved!
  } catch (e: unknown) {
    addrError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    addrBusy.value = false
  }
}

async function makeDefault(id: number) {
  if (addrBusy.value) return
  addrBusy.value = true
  clearAddrMsg()
  try {
    // 这两个接口回的就是设/删完之后的整份地址簿，不用再取一次
    addresses.value = await setDefaultAddress(id)
  } catch (e: unknown) {
    addrError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    addrBusy.value = false
  }
}

async function dropAddr(a: BuyerAddress) {
  if (addrBusy.value) return
  // 删地址没有撤销，问一句。confirm 够用：这一页没有别的模态框，为它引一套不值得
  if (!window.confirm(t.value.acctAddrDeleteConfirm!)) return
  addrBusy.value = true
  clearAddrMsg()
  try {
    addresses.value = await removeAddress(a.id)
    // 正在编辑的就是被删的那条：表单留着会「保存一条不存在的地址」，收起来
    if (addrForm.value?.id === a.id) addrForm.value = null
    addrDone.value = t.value.acctAddrRemoved!
  } catch (e: unknown) {
    addrError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    addrBusy.value = false
  }
}

async function doLogout() {
  await logout()
  // 退出后回商城首页。⚠ 用 navigateTo + 绝对地址，不是 router.replace：
  //   这一页在 buyer. 域上，router.replace 会把首页渲染在买家域下（客户端路由
  //   不经过 domain-split 中间件）。整页跳转顺带把这个域清干净
  await navigateTo(shopUrl(localePath('/')), { external: shopUrl('/') !== '/' })
}

async function submitPassword() {
  if (pwdBusy.value) return
  pwdBusy.value = true
  pwdError.value = ''
  pwdDone.value = false
  try {
    await changePassword(pwd.old, pwd.next)
    pwdDone.value = true
    pwd.old = ''
    pwd.next = ''
    // 后端改完密码就把会话作废了（怀疑泄露时留着旧会话等于让攻击者继续待着），
    // 所以这里要送他去重新登录。停两秒让他看到那句「已修改」
    // ⚠ 用 navigateTo + 绝对地址，不是 router.replace：登录页在**主域**上，
    //   router.replace 会把它渲染在买家域下（客户端路由不经过 domain-split 中间件），
    //   在那儿输密码只会在买家域上单独种一份会话，主域那边还是未登录
    setTimeout(() => {
      const href = shopUrl(localePath('/login'))
      navigateTo(href, { external: href !== localePath('/login'), replace: true })
    }, 1800)
  } catch (e: unknown) {
    pwdError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    pwdBusy.value = false
  }
}

useHead({
  title: `${t.value.acctMyAccount} | ${site.companyName}`,
  // 每个访客各自的页面，不给收录
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.acct-page {
  padding: 40px 0 72px;
}
.sk-bar { height: 40px; margin-bottom: 16px; }
.sk-body { height: 220px; }
.sk-row { height: 56px; margin-bottom: 10px; }

.acct-guest {
  max-width: 460px;
  margin: 40px auto;
  text-align: center;
}
.acct-guest h1 { margin: 0 0 10px; }
.acct-guest p { margin: 0 0 20px; }
.acct-guest .btn { margin: 0 6px; }

.acct-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 28px;
}
.acct-head h1 { margin: 0 0 6px; }
.acct-head p { margin: 0; }

.acct-block {
  margin-bottom: 32px;
  padding: 24px;
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
}
.acct-block h2 {
  margin: 0 0 16px;
  font-size: 1.15rem;
}
.acct-msg {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: var(--radius, 10px);
  font-size: 13px;
  line-height: 20px;
}
.is-error { background: #fdecec; color: #a12020; }
.is-ok { background: #eaf7ee; color: #1c6b34; }

.ord-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ord-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 0;
  border-bottom: 1px solid var(--border, #e6e6ef);
}
.ord-row:last-child { border-bottom: 0; }
.ord-main {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ord-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 14px;
}
.ord-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
}
.ord-amount { font-variant-numeric: tabular-nums; }
.more-note { margin: 14px 0 0; font-size: 13px; }

.pwd-form { max-width: 360px; }
.pwd-form .field small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}

/* ---------------- 左栏 + 面板（版式照参考站的 pages/profile） ---------------- */
.pf-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
.rail {
  padding: 18px;
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
  /* 面板很长时左栏跟着走，不用滚回去换栏 */
  position: sticky;
  top: 16px;
}
.rail-me {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border, #e6e6ef);
}
.rail-id {
  display: grid;
  /* 长邮箱不许把左栏撑宽——minmax(0,…) + 下面的省略号 */
  min-width: 0;
}
.rail-id strong { font-size: 14px; }
.rail-id small,
.rail-id strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rail-id small { font-size: 12px; color: var(--text-muted); }

.rail-nav { display: grid; gap: 2px; }
.rail-link {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--radius-sm, 8px);
  background: none;
  font: inherit;
  font-size: 14px;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}
.rail-link:hover { background: var(--surface-muted, #f4f6f9); }
.rail-link.on {
  background: rgba(11, 114, 171, .1);
  color: var(--primary, #0b72ab);
  font-weight: 600;
}
.rail-ico { font-size: 15px; line-height: 1; }

.pane { min-width: 0; }
.pane .acct-block { margin-bottom: 0; }

/* ---------------- 头像 ---------------- */
.av-block {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 22px;
}
.av-side { min-width: 0; }
.av-acts { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.av-hint { margin: 10px 0 0; font-size: 12px; line-height: 18px; max-width: 42em; }

.presets {
  margin: 0 0 24px;
  padding: 0;
  border: 0;
}
.presets legend {
  padding: 0;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--text-muted);
}
.preset-row { display: flex; gap: 10px; flex-wrap: wrap; }
.preset {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 2px solid transparent;
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  /* 选中的那个描一圈：底色本身就是彩色的，加深或加亮都不够明显 */
  box-shadow: 0 0 0 0 rgba(11, 114, 171, 0);
}
.preset.on {
  border-color: var(--surface, #fff);
  box-shadow: 0 0 0 2px var(--primary, #0b72ab);
}
.preset[disabled] { opacity: .6; cursor: default; }

/* 只给读屏用的隐藏 input：display:none 的 input 点不开文件选择框 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* ---------------- 绑定的登录方式 ---------------- */
.link-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
}
.link-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 50%;
  background: var(--surface-muted, #f4f6f9);
  font-size: 17px;
}
.link-text { display: grid; min-width: 0; }
.link-text strong { font-size: 14px; }
.link-text span { font-size: 13px; overflow: hidden; text-overflow: ellipsis; }
.link-on {
  margin-left: auto;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #eaf7ee;
  color: #1c6b34;
  white-space: nowrap;
}
.link-none { margin: 14px 0 0; font-size: 13px; }

/* ---------------- 区块标题右侧带一个按钮 ---------------- */
.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.block-head h2 { margin-bottom: 6px; }
.block-sub { margin: 0 0 16px; font-size: 13px; }

/* ---------------- 表单（和结账页那套对齐） ---------------- */
/* ⚠ 这几条和 checkout/index.vue 的同名规则是一样的。没有抽成公共类：
   两页的表单会各自长出自己的东西（这里有 .check、那边有蜜罐），
   抽出去之后每次改都要确认另一页没被带歪，代价比重复这几行大 */
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.acct-block .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0 18px; }
.field .opt { font-style: normal; color: var(--text-soft, var(--text-muted)); font-size: 0.92em; }
.field input.bad { border-color: #d92d20; }
.err { display: block; margin-top: 5px; font-style: normal; font-size: 0.78rem; color: #d92d20; }
.field input[disabled] { background: var(--surface-muted, #f4f6f9); color: var(--text-muted); }

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 18px;
  font-size: 13px;
}
.check input { width: auto; }

.form-foot { display: flex; gap: 10px; flex-wrap: wrap; }

.details-form { max-width: 620px; }

.addr-form {
  max-width: 620px;
  margin-bottom: 24px;
  padding: 18px 18px 6px;
  background: var(--surface-muted, #f9fbfc);
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
}
.addr-form h3 { margin: 0 0 14px; font-size: 1rem; }

/* ---------------- 地址卡片 ---------------- */
.addr-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.addr-card {
  padding: 16px;
  border: 1px solid var(--border, #e6e6ef);
  border-radius: var(--radius, 10px);
}
/* 默认地址描一道主色边：结账页默认选的就是它，一眼看得出是哪张 */
.addr-card.is-default { border-color: var(--primary-light, #7fa8c4); }
.addr-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.addr-label { font-size: 13px; font-weight: 600; }
.addr-flag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(11, 114, 171, .12);
  color: var(--primary, #0b72ab);
}
.addr-body {
  font-style: normal;
  font-size: 13px;
  line-height: 20px;
}
.addr-body span, .addr-body strong { display: block; }
.addr-acts {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.mini {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--primary, #0b72ab);
  cursor: pointer;
  text-decoration: underline;
}
.mini[disabled] { opacity: .5; cursor: default; }
.mini-danger { color: #a12020; }

@media (max-width: 900px) {
  /* 左栏放到上面横过来：手机上 250px 的侧栏会把面板挤成一条 */
  .pf-layout { grid-template-columns: 1fr; }
  .rail { position: static; }
  .rail-nav { grid-auto-flow: column; grid-auto-columns: 1fr; gap: 4px; overflow-x: auto; }
  .rail-link { justify-content: center; padding: 10px 8px; font-size: 13px; }
  .rail-link span:not(.rail-ico) { display: none; }
}

@media (max-width: 640px) {
  .grid-2, .acct-block .grid-3 { grid-template-columns: 1fr; }
  .av-block { gap: 14px; }
}
</style>
