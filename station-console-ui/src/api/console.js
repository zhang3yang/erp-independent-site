import request from '@/utils/request'

/**
 * 管理台的全部接口。
 *
 * 这些路径打到站点后端的 /api/**，由它**查本机的库**。
 *
 * ⚠ 以前这一层是转发：管理台后端把请求加上身份请求头，转给 ERP 的 /openapi/console/**。
 * 那条路整个删了（docs/独立站与ERP的边界.md §10.1、§10.3.6）——
 * 但**对外的 URL 路径一个都没变**，所以这个文件基本没动，改的都是注释和两处语义。
 *
 * **注意没有一个方法接受 siteCode。**一个部署一个站，站点由服务端从本库读，
 * 前端既传不了也不需要传。
 */

// ===================== 站点 =====================

/** 站点档案，含首页装修内容 */
export function getSite() {
  return request({ url: '/site', method: 'get' })
}

/**
 * 改站点档案（域名、语言、币种、前台开关、询盘通知邮箱）。**新增的**。
 *
 * ERP 那版没有这个：站点档案在 ERP 那边改，管理台只读。现在档案一分为二——
 * 只有站点编码和跳转密钥还要和 ERP 对齐（改了「进入管理」就进不来），其余都搬到本站（§7.2），
 * 搬过来的当然要能在本站改。
 *
 * ⚠ **域名也在这一份里，不在 ERP。** 它一度被列进「留在 ERP 的登记信息」，
 * 但两边库之间从来没有同步：在 ERP 改域名，本站的 u_s_siteinfo.domain 纹丝不动。
 *
 * ⚠ 后端对 domain 和 remark 用的是 coalesce，**不带这两个字段 = 保持原值**。
 * 别为了「只改一个开关」而送一个精简过的 body 并指望其余字段不动——
 * langs / currencies / notifyemail 是直赋值，不送就是清空。要么整份送，要么就送这两个。
 */
export function saveSite(data) {
  return request({ url: '/site', method: 'put', data })
}

/**
 * 改结算币种之前看一眼汇率。**不改任何数据。**
 *
 * 返回 `{ from, to, same, rate, error }`：
 *   · `rate`  —— 1 个 from 换多少个 to，确认框把它摆给运营看；
 *   · `error` —— 汇率取不到时的原因，此时 rate 为 null。
 *
 * ⚠ 汇率取不到**不是**错误响应（不会走 request.js 的报错分支）。因为那条路仍然有解：
 *   运营可以选「只改币种代码、数字不动」，而那条路压根不需要汇率。
 *   弹个红框把流程断掉，等于把唯一可行的那条路也堵了。
 */
export function previewCurrency(to) {
  return request({ url: '/site/currency-preview', method: 'get', params: { to } })
}

/**
 * 汇率状态：最后一次成功拉取的时间、当前生效汇率、失败原因、手工覆盖值。
 *
 * ⚠ 这一屏存在的理由：免费汇率 API 静悄悄挂掉是这个功能最可能的故障，
 *   而它在前台没有任何症状——价格照常显示，只是停在几周前的汇率上。
 */
export function getRate() {
  return request({ url: '/rate', method: 'get' })
}

/**
 * 存手工覆盖的汇率。传 `{ overrideJson: '{"EUR":0.90}' }`，传空表示不覆盖。
 *
 * ⚠ 覆盖值相对的是汇率源的基准（USD），**不是站点本币**。
 */
export function saveRate(overrideJson) {
  return request({ url: '/rate', method: 'put', data: { overrideJson } })
}

/** 立即拉一次汇率。让运营当场看到结果，而不是等第二天或去翻服务器日志 */
export function refreshRate() {
  return request({ url: '/rate/refresh', method: 'post' })
}

/**
 * 保存首页装修**草稿**。传完整的装修 JSON 字符串；传空串表示「草稿是空的」。
 *
 * ⚠ 2026-09-13 起这个接口不再直接改网站上的内容，要再调一次
 * {@link publishSiteContent} 才见客。路径没变（后端刻意保持的，见那边的注释）。
 */
export function saveSiteContent(contentJson) {
  return request({
    url: '/site/content',
    method: 'put',
    data: contentJson,
    headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
  })
}

/** 发布：把库里那份草稿推成访客看到的内容。不带请求体，发的就是库里那份 */
export function publishSiteContent() {
  return request({ url: '/site/content/publish', method: 'post' })
}

/** 放弃草稿，回到上一次发布的那份 */
export function discardSiteDraft() {
  return request({ url: '/site/content/draft', method: 'delete' })
}

/**
 * 机器翻译一批文案。返回 `{ data: { texts } }`，和入参**等长、按下标对应**。
 *
 * 只翻字符串，不认识装修 JSON 的结构——哪些格子要翻、翻回来填到哪儿，
 * 由装修表单决定（它本来就按共享层/语言层分好了）。
 */
export function translateTexts(from, to, texts) {
  return request({ url: '/site/translate', method: 'post', data: { from, to, texts } })
}

/* ---------- 分类译名 ----------
   分类本身没有表：身份是 u_s_sitegoods.sitecategory 那一列自由文本，同时兼着筛选键
   （前台 ?cat=）和两级层次的载体。下面这组接口只管「各语言显示成什么字」。 */

/**
 * 分类译名表：`{ keys, langs, defaultLang, names }`。
 *
 * `keys` 是全站分类按路径逐级拆出来的 catkey，已排好序（父级在子级前），
 * 前端按 `/` 的个数决定缩进即可。`names` 是 `catkey → lang → 译名` 两层字典，
 * 空格子不出现在里面。
 */
export function listCategoryLangs() {
  return request({ url: '/categories/langs', method: 'get' })
}

/**
 * 保存手工改过的格子，`rows` 形如 `[{ catKey, lang, name }]`。
 *
 * ⚠ **只写提交上来的那几格**，没提交的一个字不动——译名是站点级共享的，
 * 按「提交的就是全部」去删会把别的分类的译名一起删掉。
 */
export function saveCategoryLangs(rows) {
  return request({ url: '/categories/langs', method: 'post', data: { rows } })
}

/**
 * 一键补齐：把全站分类缺的译名翻出来，返回 `{ filled, failed }`。
 *
 * **已有的永远不覆盖**，所以重复点是安全的；全都有译名时后端一次翻译接口都不调。
 * `failed` 是翻失败的语种，某个语种挂了不影响其他语种。
 */
export function translateCategoryLangs() {
  return request({ url: '/categories/translate', method: 'post' })
}

/**
 * 装修预览：把一份还没保存的装修 JSON 算成前台会看到的那份数据。
 *
 * 不写库。返回的形状和前台 `/openapi/site/info` 完全一样，
 * 管理台原样 postMessage 给预览窗口就行——合并规则只有后端一份，前端不重算。
 */
export function previewSiteContent(contentJson, lang) {
  return request({
    url: '/site/content/preview',
    method: 'post',
    params: lang ? { lang } : undefined,
    data: contentJson,
    headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
  })
}

// ===================== 商品 =====================

export function listSiteGoods(query) {
  return request({ url: '/goods', method: 'get', params: query })
}

export function getSiteGoods(goodsCode) {
  return request({ url: '/goods/' + encodeURIComponent(goodsCode), method: 'get' })
}

/**
 * 新建一个商品，落成草稿。
 *
 * ⚠ 路径没变，但**请求体变了**：以前传的是 `['code1','code2']`——
 * 「从 ERP 商品档案里挑哪几个加进本站」；现在传的是一个完整的商品对象，
 * 名称、规格、价格、图片全在本站录。
 *
 * 依据 §5.4：取商品名和图片那条 `join sdsshop.u_m_waregoods` 是一条跨库互通，
 * 库搬到站点服务器之后连语法上都不成立，所以商品从「挑」变成「录」。
 * 代价是同一个商品在 ERP 和各个站里各录一遍，这是要点 1 的直接成本。
 */
export function addSiteGoods(goods) {
  return request({ url: '/goods', method: 'post', data: goods })
}

/** 保存商品档案和各语言文案。data 形如 { goods: {...}, langs: [...] } */
export function updateSiteGoods(data) {
  return request({ url: '/goods', method: 'put', data })
}

export function changeSiteGoodsStatus(goodsCodes, publishStatus) {
  return request({ url: '/goods/status', method: 'post', data: { goodsCodes, publishStatus } })
}

/**
 * 删除商品。
 *
 * ⚠ 语义变了：以前删的是「这个站上架了它」这层关系，ERP 的商品档案还在。
 * 现在**商品档案就在本站**，删了就是真删了，连多语言文案一起。
 */
export function deleteSiteGoods(goodsCodes) {
  const codes = Array.isArray(goodsCodes) ? goodsCodes.join(',') : goodsCodes
  return request({ url: '/goods/' + encodeURIComponent(codes), method: 'delete' })
}

// ⚠ 这里原来有两个方法：listCategoryTree / listGoodsByCategory——
//   「从 ERP 商品档案里挑商品」那个选择器用的。
//
//   两个都删了。ERP 的商品档案够不着了（要点 1），商品改成站点自己录（§5.4）。
//   对应的 GoodsSelectDialog.vue 组件也删了，Goods.vue 里那个「加商品」弹窗
//   从一个 ERP 商品选择器变成了一张表单。

// ===================== 询盘 =====================

export function listInquiry(query) {
  return request({ url: '/inquiry', method: 'get', params: query })
}

export function getInquiry(id) {
  return request({ url: '/inquiry/' + encodeURIComponent(id), method: 'get' })
}

/** 改处理状态和内部备注。客户填的内容一个字都不许改——那是原始凭据 */
export function updateInquiry(data) {
  return request({ url: '/inquiry', method: 'put', data })
}

// ⚠ 这里原来有一个 convertInquiry（询盘转 ERP 客户），删了。
//   它往 ERP 的 sdsshop.u_m_buyer 里建客户档案——是一条互通（§3.2）。
//   站点自己的客户管理一期不做（§11），所以现在没有任何「转客户」的去处。
//   u_s_inquiry.buyercode 那一列留着、空着，将来做站点客户管理时正好是落 ID 的地方。

// ===================== 文件资源 =====================
//
// 图片和视频是两张表、两组接口。
//
// ⚠ 存储介质变了：以前是「商家自己的对象存储」（阿里云 / 华为云 / 腾讯云三选一，
//   商家在存储设置里自己填密钥）；现在文件落在**这台机器的磁盘上**，
//   由 file.<主域名> 直接对外（§10.3.8）。
//   接口路径和请求体都没变，所以下面这一段一个字都不用改。

export function listSiteImage(query) {
  return request({ url: '/file/image', method: 'get', params: query })
}

/**
 * 上传图片。传 FormData。
 *
 * width/height 由调用方先在浏览器里量好再传：浏览器选文件时本来就要加载一次，
 * 服务端再解码一遍纯属浪费。量不到就别传，后端存 null。
 *
 * 超时单独放大：默认 30 秒对视频不够用。
 */
export function uploadSiteImage(formData, onUploadProgress) {
  return request({
    url: '/file/image',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 10 * 60 * 1000,
    onUploadProgress
  })
}

/** 只能改名称和备注。地址、尺寸是上传时定死的，后端不接受修改 */
export function updateSiteImage(data) {
  return request({ url: '/file/image', method: 'put', data })
}

export function delSiteImage(imageId) {
  return request({ url: '/file/image/' + encodeURIComponent(imageId), method: 'delete' })
}

export function listSiteVideo(query) {
  return request({ url: '/file/video', method: 'get', params: query })
}

export function uploadSiteVideo(formData, onUploadProgress) {
  return request({
    url: '/file/video',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 10 * 60 * 1000,
    onUploadProgress
  })
}

export function updateSiteVideo(data) {
  return request({ url: '/file/video', method: 'put', data })
}

export function delSiteVideo(videoId) {
  return request({ url: '/file/video/' + encodeURIComponent(videoId), method: 'delete' })
}

// ---- 3D 模型（.glb）。2026-09-20 加，四个接口和视频一一对应，只是没有宽高时长 ----

export function listSiteModel(query) {
  return request({ url: '/file/model', method: 'get', params: query })
}

export function uploadSiteModel(formData, onUploadProgress) {
  return request({
    url: '/file/model',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 10 * 60 * 1000,
    onUploadProgress
  })
}

export function updateSiteModel(data) {
  return request({ url: '/file/model', method: 'put', data })
}

export function delSiteModel(modelId) {
  return request({ url: '/file/model/' + encodeURIComponent(modelId), method: 'delete' })
}

// ---- 用照片生成 3D（腾讯混元生 3D，2026-09-26）----
// 提交：{ front, left?, right?, back?, name?, quality: 'standard'|'fine', pbr, folderId? }，图片都是文件库里的地址
export function submitModelGen(data) {
  return request({ url: '/file/model/gen', method: 'post', data })
}

// 任务列表：{ total, rows, active, enabled, dailyLimit, usedToday }
export function listModelGen(query) {
  return request({ url: '/file/model/gen', method: 'get', params: query })
}

// 只删任务记录，已经生成进模型库的模型不受影响
export function delModelGen(jobId) {
  return request({ url: '/file/model/gen/' + encodeURIComponent(jobId), method: 'delete' })
}


// ===================== 文件夹 =====================
//
// 图片和视频是**两棵独立的树**，靠 mediaType（image / video）分开——每个调用都要带上它。
// 共用一棵的话，切到视频页签会看到一堆只装着图片的空文件夹。
//
// ⚠ 列表接口的 folderId 有三个状态，别混：
//     不传        → 全部（含未分类）
//     'ROOT'      → 只看未分类
//     具体 id     → 只看那个文件夹

/** 整棵树。顶层在最外面，children 已经拼好，每个节点带 fileCount */
export function listSiteFolder(mediaType) {
  return request({ url: '/file/folder', method: 'get', params: { mediaType } })
}

/** 建文件夹。parentId 留空就建在顶层 */
export function addSiteFolder(data) {
  return request({ url: '/file/folder', method: 'post', data })
}

/** 改名（传 folderName）或挪位置（传 parentId，空串表示挪到顶层）。可以一次都做 */
export function updateSiteFolder(data) {
  return request({ url: '/file/folder', method: 'put', data })
}

/**
 * 删文件夹。
 *
 * ⚠ 里面的文件会回落到「未分类」，子文件夹提升一级，**都不会被删掉**。
 *   图片可能正被商品页或博客引用着。
 */
export function delSiteFolder(folderId) {
  return request({ url: '/file/folder/' + encodeURIComponent(folderId), method: 'delete' })
}

/** 把一批文件挪进某个文件夹。folderId 传 null / 空串 = 挪回未分类 */
export function moveSiteFiles(mediaType, fileIds, folderId) {
  return request({ url: '/file/folder/move', method: 'post', data: { mediaType, fileIds, folderId } })
}
// ===================== 存储设置 =====================
//
// ⚙ 这两个接口的调用方 2026-09-07 换了：原来是 views/Files.vue 右上角那个
//   「对象存储设置」弹窗，现在是 views/Overview.vue 里的「文件存储」卡。
//   Files.vue 还留着一处 getShopStorage（checkStorage），只为了在上传前
//   探一下「现在能不能传」——那一页不再写存储配置。
//   接口本身和请求体都没变。
//
// ⚠ 一期这两个接口基本没内容可回：文件落本机磁盘，provider 恒为 local，没有密钥要管。
//   保留是因为 §10.3.6 要求 URL 不变，前端那个页面还在。
//   将来真接对象存储时，表（u_s_storage，结构照抄 s_m_shop_storage）和接口都不用改。

// 返回体里没有 accessKeySecret（后端 domain 上那个字段挂着忽略序列化的注解），
// 只有 secretConfigured 说明配过没有。密钥存进去之后不再下发到浏览器，
// 界面上改密钥是覆盖写，不是回显后编辑。
export function getShopStorage(provider) {
  return request({ url: '/file/storage', method: 'get', params: provider ? { provider } : undefined })
}

// accessKeySecret 留空 = 不改密钥。但换了厂商必须填新的——
// 原来那把是上一家的凭据，后端会拒绝。
export function saveShopStorage(data) {
  return request({ url: '/file/storage', method: 'post', data })
}

// ===================== 用户管理（新增） =====================
//
// 本站自己的用户体系（§6）。ERP 那边没有对应物——那时候管理台没有用户表，
// 身份全来自 ERP 发的票。
//
// 要点 5：每套独立站自己成立用户体系，站与站之间、站与 ERP 之间都不关联。

export function listSiteUser(query) {
  return request({ url: '/user', method: 'get', params: query })
}

/** 有哪些角色。出厂两条：admin（站长，权限 *）和 operator（运营） */
export function listSiteRole() {
  return request({ url: '/user/roles', method: 'get' })
}

// ⚠ addSiteUser 删了：本站建不出账号。
//
// 用户是「长出来的」——谁第一次从 ERP 点「进入管理」跳进来，后端就为他落一行
// （SiteUserService.createFromErp），默认角色是运营。手工建的账号谁都用不了：
// 本站没有登录页，而免密进入是按 ERP 账号名找人的，匹配不上手工建的那一行。

/** 改昵称、角色、状态。登录名不允许改——它是这个人的身份，历史操作记录里存的都是旧名字 */
export function updateSiteUser(user) {
  return request({ url: '/user', method: 'put', data: user })
}

// ⚠ resetSiteUserPassword 也删了。它当时的作用是「给一个从 ERP 进来的人开本地登录」，
// 也就是在唯一入口旁边开一扇门——正是 docs/独立站与ERP的边界.md §6d 要挡的事。

/** 删用户。最后一个可用的站长删不掉——删了就没人能再授权了 */
export function delSiteUser(id) {
  return request({ url: `/user/${id}`, method: 'delete' })
}

// ===================== 订单 =====================
//
// boundary §11 把「订单管理」列在「留位置」里。这一组是那一格真正长出东西的地方，
// 依据是前台的询价流程已经把模型定完了（station-site/app/lib/shop.ts）。
//
// ⚠ 没有「新建订单」。订单只有一个来源：前台买家自助提交
//   （POST /openapi/site/order，那是公网接口，不在这个文件里）。
//   管理台这一组只读和推进状态。
//
// ⚠ 也没有任何「同步到 ERP」。要点 4：两边订单没有隶属关系；
//   §8：一期人工在 ERP 里录一张，不做也不预埋接口。

export function listSiteOrder(query) {
  return request({ url: '/order', method: 'get', params: query })
}

/** 订单详情，含订单行 */
export function getSiteOrder(orderCode) {
  return request({ url: `/order/${orderCode}`, method: 'get' })
}

/**
 * 推进订单状态。
 *
 * ⚠ 只能往前走，外加「任何状态都能取消」。后端会拒绝往后退——
 *   理由是时间轴上那三个时间戳只写一次，允许退回会造出
 *   「状态是待确认，但发货时间是上周二」这种自相矛盾的单子。
 */
export function changeSiteOrderStatus(orderCode, status) {
  return request({ url: `/order/${orderCode}/status`, method: 'post', data: { status } })
}

/** 改订单的内部备注。买家看不到这一段 */
export function saveSiteOrderRemark(orderCode, remark) {
  return request({ url: `/order/${orderCode}/remark`, method: 'put', data: { remark } })
}

// ===================== 客户 =====================
//
// ⚠ 和 ERP 的销售客户（sdsshop.u_m_buyer）没有任何关系（§6c）。
//
// ⚠ 没有「新建客户」。档案是询盘/下单时按邮箱长出来的，管理台只能改标签和备注。
//   没有「新建」按钮，就不会有一个对不上任何人的档案。

export function listSiteBuyer(query) {
  return request({ url: '/buyer', method: 'get', params: query })
}

/** 客户详情：档案 + 他的订单 + 他的询盘，一次返回 */
export function getSiteBuyer(buyerCode) {
  return request({ url: `/buyer/${buyerCode}`, method: 'get' })
}

/** 改标签和备注。联系方式不给改——那是客户自己填的，改了就对不上他的来信 */
export function saveSiteBuyerNote(buyerCode, tag, remark) {
  return request({ url: `/buyer/${buyerCode}/note`, method: 'put', data: { tag, remark } })
}

/**
 * 询盘转客户。
 *
 * ⚠ 这**不是** ERP 那个「转客户」——那一版往 sdsshop.u_m_buyer 建 ERP 客户档案，
 *   是一条互通，已经删掉了（§3.2）。这里挂的是本站自己的 u_s_buyer，
 *   也就是 u_s_inquiry.buyercode 那一列注释一直写着的「将来转的是这里」。
 *
 * 可以重复点：已经转过的只更新联系方式，不会重复建档。
 */
export function convertInquiryToBuyer(id) {
  return request({ url: `/inquiry/${id}/convert`, method: 'post' })
}

// ===================== 退出 =====================
//
// 这里只有退出，**没有登录**。
//
// 进这个后台只有一条路：ERP 的「独立站管理」点「进入管理」，浏览器 POST 一个
// 一次性令牌到站点后端的 /sso，那边验签、建会话、302 到首页。前端一行代码都不参与，
// 所以这个文件里没有对应的方法。
//
// ⚠ 曾经有过一组 /auth/state + /auth/login + /auth/password（§6a 那版登录页）。
//   整组删了，连后端那三个接口一起——公开路径少一组，就少一组要防的东西。
//   要加回来之前先读 docs/独立站与ERP的边界.md §6d。

/** 退出。在 /api/ 下，需要会话——没会话的人本来也没什么可退的 */
export function logout() {
  return request({ url: '/auth/logout', method: 'post' })
}

// ===================== 在线咨询 =====================
//
// 买家在前台的悬浮窗里问，运营在管理台的悬浮面板里回。后端 /api/chat/**
// （station-console 的 ConsoleChatController），表是 u_s_chat / u_s_chatmessage。
//
// ⚠ 走**轮询**，不是 WebSocket：面板开着 3 秒拉一次增量，收起时 15 秒只拉未读数。
//   选它是因为部署面为零——nginx 和服务的环境变量一个字都不用改。
//   代价是最差 3 秒延迟，这个量级的站没人感觉得到。
//
// ⚠ 拉消息一律带 sinceId。不带的话每 3 秒把整条对话重新拉一遍，
//   聊得越久越慢，而这个退化是渐进的、上线时看不出来。
//
// 设计文档：docs/设计稿/2026-09-04-在线咨询.md

/** 会话列表。未读的排在前面，然后按最后一条消息倒排 */
export function listSiteChat(query) {
  return request({ url: '/chat', method: 'get', params: query })
}

/**
 * 全站未读消息**条数**，给悬浮球上那个红点。
 *
 * ⚠ 这是这一组里唯一的高频接口（每 15 秒一次），后端那条 count 是带索引的。
 *   别改成「顺便把列表也带回来」——那就是每 15 秒一次的全表扫。
 */
export function getSiteChatUnread() {
  return request({ url: '/chat/unread', method: 'get' })
}

/** 某条会话的消息。sinceId 为空/0 回最近一批历史，否则回增量 */
export function listSiteChatMessages(chatCode, sinceId) {
  return request({ url: `/chat/${chatCode}/messages`, method: 'get', params: { sinceId } })
}

/** 回一条。需要 site:chat:reply——「能看」和「能替公司说话」是两件事 */
export function sendSiteChatMessage(chatCode, content) {
  return request({ url: `/chat/${chatCode}/send`, method: 'post', data: { content } })
}

/** 标已读。点开某条会话时调一次 */
export function readSiteChat(chatCode) {
  return request({ url: `/chat/${chatCode}/read`, method: 'post' })
}

// ===================== 统计 =====================

/**
 * 前台浏览量。一次把概览页要的全拿回来，不发五个请求。
 *
 * 回的 data：
 *   enabled       统计有没有开。⚠ false 时下面那些列表都是空的，
 *                 这和「加载失败」是两种不同的状态，界面上要分开显示
 *   today         { date, pv, pvHuman, uv }  ⚠ uv 是现算的
 *   yesterday     同上，uv 读的是凌晨回填的那一列
 *   trend         最近 14 天，一天一项，没数据的日子已经补零
 *   topGoods      商品排行。**这是这个接口的核心**——浏览量和询盘数、成单数、
 *                 标价在同一行里，回答「看了 200 次一个询盘都没有，是不是价格问题」
 *   typeBreakdown 页面类型分布
 *   referrers     来源分布（按主机名）。⚠ 含站内跳转，界面上要区分
 *   topDays       排行和分布的统计窗口天数
 *   retainDays    明细保留天数
 *
 * ⚠ 需要 site:stat:query。老库要跑 sql/station/migration/2026-09-08-pageview.sql
 *   才会给 operator 角色加上这个权限——不跑的话运营看到的是 403。
 */
export function getPageViewStat() {
  return request({ url: '/stat/pv', method: 'get' })
}

// ===================== 站长工具 =====================
//
// docs/独立站与ERP的边界.md §11 把它列在「一期不做、只留位置」里。2026-09 做出来了。
//
// ⚠ 三个接口，两个权限：
//     GET  /seo        site:seo:query   看配置
//     PUT  /seo        site:seo:edit    改配置（**只有 admin**）
//     GET  /seo/audit  site:seo:query   SEO 体检
//
//   体检给 operator 是因为它就是运营的日常活（哪个商品缺日语标题）；
//   改配置里有「整站禁收录」那种一开就静默毁掉自然流量的开关，归站长。

/**
 * 站长工具配置。
 *
 * ⚠ **永远有返回，不会 404。** 老库没跑 sql/station/migration/2026-09-08-seo-tools.sql
 *   时后端回的是一份默认值，而那份默认值等于这个模块存在之前前台的原有行为。
 *   所以界面上不需要区分「没配置过」和「没跑迁移」——那是有意设计的，别加这个分支。
 */
export function getSeo() {
  return request({ url: '/seo', method: 'get' })
}

/**
 * 存站长工具配置。
 *
 * ⚠ **整份送。** 后端一个字段都不走 coalesce（见 SiteSeoMapper.xml 里那段注释）：
 *   每一项都会原样回显到界面上，所以「传上来什么就是什么」是对的——
 *   清空 GA4 测量 ID 就该真的清掉。别为了「只改一个开关」而送一个精简过的 body，
 *   那会把其余项全部清空。
 *
 * ⚠ 统计那三项只填 **ID**（G-XXXXXXXXXX / GTM-XXXXXXX / 32 位十六进制），
 *   不是整段统计代码。后端和前台各校验一次正则，不合法的保存会被拒、
 *   而且就算用 SQL 塞进库里也注入不出来。
 *
 *   这不是懒——给一个「自由粘贴统计代码」的框等于开一个存储型 XSS 入口：
 *   任何能进管理台的人都能在每个公开页面上执行任意 JS，包括偷买家 Cookie。
 *
 * 返回的是**保存后**的那一份（后端可能把越界值夹回去了），界面上要用它回填。
 */
export function saveSeo(data) {
  return request({ url: '/seo', method: 'put', data })
}

/**
 * SEO 体检。现算，不落库。
 *
 * 回的 data：
 *   goodsCount   扫了多少个已上架商品（草稿和已下架不算——它们对外不可见）
 *   langs        站点开着的语言
 *   issues       问题清单，已按严重程度排好序（高 → 中 → 低）
 *   high/medium/low  各级别条数
 *   skipped      跳过的坏数据行数，正常是 0
 *   notChecked   **查不了的项目**，界面上必须显示
 *
 * ⚠ notChecked 那一项不是占位符。一份体检报告最危险的地方不是漏报，
 *   是让人以为「没报就是没问题」——canonical 指向的域名后端确实查不到
 *   （那是 station-site 那个 Node 进程的环境变量），所以要明说。
 */
export function getSeoAudit() {
  return request({ url: '/seo/audit', method: 'get' })
}

// ===================== 博客 =====================
//
// §11 原来把「博客管理」列在「一期不做、只留位置」里。2026-09 做出来了：
// 这个站的 SEO 价值原来全压在商品详情页上，而商品页能覆盖的关键词就是商品名——
// 覆盖不到「how to choose silicone tubing」这类真正带来询盘的长尾词。
//
// 五个权限，和商品那一套对齐，**全给 operator**：写文章就是运营的日常活。
//
// ⚠ 两个标识别混用：
//     postCode  内部编码（BLOG 前缀），下面这些接口用的都是它
//     slug      公开地址那一段（/blog/how-to-choose-silicone-tubing）
//   编码不出现在前台任何地方。改已发布文章的 slug = 换地址，老地址会 404、
//   排名和外链一起丢（这个站没有 301 重定向表）。

/** 文章列表。⚠ 显示的是**站点默认语言**那一条文案，没填就是空标题——那正是要让人看见的 */
export function listSitePosts(query) {
  return request({ url: '/post', method: 'get', params: query })
}

/** 一篇文章：档案 + 全部语言文案 + 站点开着的语言（编辑弹窗按它排语言页签） */
export function getSitePost(postCode) {
  return request({ url: `/post/${postCode}`, method: 'get' })
}

/**
 * 新建文章。只建骨架（slug + 分类 + 封面等），文案由随后的保存写入。
 *
 * ⚠ **一律创建成草稿**，后端不接受前端传状态：一篇还没有任何正文的文章
 *   不该有机会直接出现在公网上。
 *
 * ⚠ slug 必填，而且后端不替你生成。中文标题生成不出来（刻意不做音译——
 *   猜出来的拼音串既不可读也不含关键词），那时候必须人工填一个。
 */
export function addSitePost(data) {
  return request({ url: '/post', method: 'post', data })
}

/**
 * 保存文章档案和各语言文案。
 *
 * 传 `{ post: {...}, langs: [{ lang, title, summary, content, keywords }, ...] }`。
 *
 * ⚠ 正文在后端过 RichTextCleaner 白名单（script / iframe / style / 事件属性 /
 *   javascript: 全部剥掉）——前台是 v-html 渲染的，和商品详情同一条规矩。
 *   所以「保存后发现某个标签没了」不是 bug。
 *
 * ⚠ 转「已发布」之前至少要填**一种**语言的标题（不要求默认语言）。
 *   这个模块的规则是「哪种语言有标题，哪种语言才有这篇文章」——
 *   一种都没有的话，发布出去在每个语言下都是不存在的。
 */
export function saveSitePost(data) {
  return request({ url: '/post', method: 'put', data })
}

/**
 * 批量改状态：草稿 / 已发布 / 已下架。
 *
 * ⚠ 发布时间只在它原来是空的时候填：一篇下架后重新发布的文章，日期还是当初那个
 *   （外链和收录都指着那个日期，跳到今天等于告诉 Google「这是篇新文章」）。
 */
export function updateSitePostStatus(postCodes, publishStatus) {
  return request({ url: '/post/status', method: 'put', data: { postCodes, publishStatus } })
}

/** 删文章。档案和多语言文案一起删 */
export function delSitePosts(postCodes) {
  return request({ url: `/post/${postCodes}`, method: 'delete' })
}

// ===================== 询盘跟进 / 营销管理 =====================
//
// §11 里「营销管理」只有一个名字。落到这个站上，营销最值钱的一块不是优惠券
// 也不是群发邮件——**是「询盘来了没人回」**。这是个 B2B 询盘站：前台不收款、
// 价格 0 表示面议、成交靠人跟出来的报价单。漏一条询盘 = 漏一单，而且是静悄悄地漏。
//
// ⚠ 这一组**不用新权限**，复用 site:inquiry:list / site:inquiry:edit。
//   跟进记录就是询盘数据；单开一套会造出「能看看板、却点不开背后的询盘」
//   这种没用的中间状态。operator 本来就有这两个。
//
// ⚠ 库里已经有的三样这一组不碰、也别重复造：
//     u_s_inquiry.status  待处理/已联系/已成交/无效   —— 跟进状态
//     u_s_inquiry.remark  内部备注（单条，会被覆盖）
//     u_s_buyer.tag       客户标签 —— 客户分组在「客户管理」里已经能改

/**
 * 跟进看板：四个数字 + 一份清单。
 *
 * bucket：overdue（默认，已逾期）/ today（今天要跟）/ unowned（没人认领）/ all（全部未结案）
 *
 * ⚠ 数字和清单是**同一套口径**（后端两条 SQL 共用一段 where）。
 *   看到「顶上说 3 条、列表 5 条」就是有人改了一处没改另一处，那是 bug 不是特性——
 *   一个对不上的待办列表会让人不再信这一页，而不被信的待办等于没有。
 *
 * 回的 data：
 *   stat      { openCount, overdueCount, todayCount, unownedCount }
 *   rows      清单，每行带 idleDays（躺了多少天）和 lastTouch（最后被碰的时间）
 *   staleDays 多少天没动算「躺太久」（后端常量，现在是 3）
 *   limit     一次最多回多少条
 */
export function getFollowupBoard(params) {
  return request({ url: '/followup/board', method: 'get', params })
}

/** 一条询盘的跟进历史，最新的在最前面。⚠ 传的是数据库 id，不是询盘编码 */
export function listFollowup(inquiryId) {
  return request({ url: `/inquiry/${inquiryId}/followup`, method: 'get' })
}

/**
 * 记一条跟进。传 `{ action, content }`。
 *
 * action：记录 / 邮件 / 电话 / whatsapp / 会面。⚠ 它**不驱动任何逻辑**，
 * 纯粹是给人看的分类，所以加一种方式只用改后端那个白名单。
 *
 * ⚠ 这个动作会**顺带把「待处理」推成「已联系」**——有人写了第一条跟进，
 *   它就不再是待处理了。已成交 / 无效 / 已联系都不动，那些是人明确设过的结论。
 *
 * ⚠ 跟进记录**只增不改**：没有编辑、没有删除。它是一条线索的历史，
 *   改过的历史没有价值——写错了再写一条更正。
 */
export function addFollowup(inquiryId, data) {
  return request({ url: `/inquiry/${inquiryId}/followup`, method: 'post', data })
}

// ==================== 引导式下单（2026-09-28） ====================
// 设计：docs/superpowers/specs/2026-09-28-order-wizard-design.md。保存即生效，格式由后端校验

/** 向导：{ enabled, schemaJson, updateUser, updateTime } */
export function getWizard() {
  return request({ url: '/wizard', method: 'get' })
}

/** 存向导。schemaJson 是整份 JSON 字符串；格式不对后端回 400，报错说清是哪个分组 / 字段 / 选项 */
export function saveWizard(enabled, schemaJson) {
  return request({ url: '/wizard', method: 'put', data: { enabled, schemaJson } })
}

/** 有哪些预置模板、哪份是本站的：[{ code, forThisSite }]，本站那份排第一 */
export function getWizardTemplates() {
  return request({ url: '/wizard/templates', method: 'get' })
}

/** 取一份预置模板（只取出来，不落库）。不传 code = 本站对应的那份 */
export function getWizardTemplate(code) {
  return request({ url: '/wizard/template', method: 'get', params: code ? { code } : {} })
}

// ==================== 买家 3D 定制方案（2026-09-29，只读） ====================

/** 定制方案列表。params: { goodsCode, from, to（yyyy-MM-dd，含两端）, pageNum, pageSize } */
export function listCustomPlan(params) {
  return request({ url: '/customplan', method: 'get', params })
}

/** 定制方案详情：{ plan（带邮箱、备注）, marks（解析后的标注） } */
export function getCustomPlan(planId) {
  return request({ url: `/customplan/${planId}`, method: 'get' })
}
