/*
  商品 3D 标注：模型上的 logo 贴花、屏幕、开机键。

  设计：docs/superpowers/specs/2026-09-25-product-3d-marks-design.md

  ## 为什么只有这一份

  管理台编辑器（station-shared/ModelMarksEditor.vue）和前台展示台
  （station-site/app/components/Product3DViewer.client.vue）都用这里。标注是商家在管理台上
  「看着摆」的，两边画法差一点，前台就贴歪一点——所以不照 ModelPreview 那样各抄一份。
  前台通过 nuxt.config.ts 的 @station-shared 别名引用它（只是构建期引用，部署产物互不依赖）。

  ## 坐标

  标注里的点、法线、尺寸都在 glb 场景自身（gltf.scene）的局部坐标里。管理台预览和前台
  取景的挪法不一样，标注挂在 gltf.scene 下面就跟着模型走，两边一致。

  ## 贴图与 CORS

  所有图片用 crossOrigin = 'anonymous' 拉——WebGL 贴图和画到 canvas 再上传都要求跨域干净。
  桶没配 CORS 时这一项加载失败、被跳过，模型照常显示（见设计 §7）。

  ## 范围

  parseMarks 的范围和后端 Model3dMarks.java 是同一套，改一边要改另一边。后端是严格的
  （不合规拒绝保存），这里是宽松的（坏的那一项丢掉，其余照常）——前台不能因为一项坏了整块不显示。
*/
import * as THREE from 'three'
import { DecalGeometry } from 'three/examples/jsm/geometries/DecalGeometry.js'

export const LIMITS = {
  version: 1,
  maxLogos: 10,
  maxCoord: 10000,
  maxBootMs: 10000,
  defaultBootMs: 1500,
  defaultBootBg: '#000000'
}

const FADE_IN_MS = 400
const FADE_OFF_MS = 300
const COLOR_RE = /^#[0-9a-fA-F]{6}$/
const ID_RE = /^[A-Za-z0-9_-]{1,32}$/

/* ---------------- 解析（与后端同一套范围，宽松版） ---------------- */

function okUrl(v) {
  if (typeof v !== 'string') return null
  const s = v.trim()
  if (!s || s.length > 500) return null
  return s.startsWith('https://') || s.startsWith('/') ? s : null
}

function okNum(v, min, max) {
  return typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max ? v : null
}

function okVec(v) {
  if (!Array.isArray(v) || v.length !== 3) return null
  const out = v.map(x => okNum(x, -LIMITS.maxCoord, LIMITS.maxCoord))
  return out.some(x => x === null) ? null : out
}

function okNormal(v) {
  const n = okVec(v)
  if (!n) return null
  const len = Math.hypot(n[0], n[1], n[2])
  return len < 1e-9 ? null : n.map(x => x / len)
}

function okPos(v) {
  return typeof v === 'number' && Number.isFinite(v) && v > 0 && v <= LIMITS.maxCoord ? v : null
}

function withDefault(v, min, max, fallback) {
  if (v === undefined || v === null) return fallback
  return okNum(v, min, max)
}

function parseLogo(raw, i) {
  if (!raw || typeof raw !== 'object') return null
  const src = okUrl(raw.src)
  const p = okVec(raw.p)
  const n = okNormal(raw.n)
  const size = okPos(raw.size)
  const rot = withDefault(raw.rot, -180, 180, 0)
  const opacity = withDefault(raw.opacity, 0.1, 1, 1)
  if (!src || !p || !n || size === null || rot === null || opacity === null) return null
  const id = typeof raw.id === 'string' && ID_RE.test(raw.id) ? raw.id : 'l' + i
  return { id, src, p, n, size, rot, opacity }
}

function parseScreen(raw) {
  if (!raw || typeof raw !== 'object') return null
  const p = okVec(raw.p)
  const n = okNormal(raw.n)
  const w = okPos(raw.w)
  const h = okPos(raw.h)
  const rot = withDefault(raw.rot, -180, 180, 0)
  const bootSrc = okUrl(raw.bootSrc)
  const runSrc = okUrl(raw.runSrc)
  const bootMs = withDefault(raw.bootMs, 0, LIMITS.maxBootMs, LIMITS.defaultBootMs)
  const bootBg = raw.bootBg == null ? LIMITS.defaultBootBg : COLOR_RE.test(raw.bootBg) ? raw.bootBg.toLowerCase() : null
  if (!p || !n || w === null || h === null || rot === null || bootMs === null || !bootBg) return null
  if (!bootSrc && !runSrc) return null
  const s = { p, n, w, h, rot, bootBg, bootMs: Math.round(bootMs) }
  if (bootSrc) s.bootSrc = bootSrc
  if (runSrc) s.runSrc = runSrc
  return s
}

function parsePower(raw) {
  if (!raw || typeof raw !== 'object') return null
  const p = okVec(raw.p)
  const n = okNormal(raw.n)
  const r = okPos(raw.r)
  return p && n && r !== null ? { p, n, r } : null
}

/**
 * 字符串或对象 → 合法的 v1 标注；不认识 / 什么都没有时返回 null。
 * 单项坏了只丢那一项。
 */
export function parseMarks(value) {
  let raw = value
  if (typeof raw === 'string') {
    try {
      raw = JSON.parse(raw)
    } catch {
      return null
    }
  }
  if (!raw || typeof raw !== 'object' || raw.v !== LIMITS.version) return null
  const logos = Array.isArray(raw.logos)
    ? raw.logos.slice(0, LIMITS.maxLogos).map(parseLogo).filter(Boolean)
    : []
  const screen = parseScreen(raw.screen)
  const power = parsePower(raw.power)
  if (!logos.length && !screen && !power) return null
  const out = { v: LIMITS.version }
  const model = okUrl(raw.model)
  if (model) out.model = model
  if (logos.length) out.logos = logos
  if (screen) out.screen = screen
  if (power) out.power = power
  return out
}

/* ---------------- 几何工具 ---------------- */

const Z = new THREE.Vector3(0, 0, 1)

/**
 * 以法线为 +Z 的朝向，再绕法线转 rot 度。
 *
 * X 取 up × n，所以竖直面上的图是正的；法线接近竖直（贴在顶面/底面）时 up 换成 -Z，
 * 让图的上方朝模型背面，从正前方俯看是正的。logo 贴花、屏幕、开机键三者共用，
 * 同一个 rot 在三者上的含义一致。
 */
export function orientation(n, rotDeg = 0) {
  const nz = new THREE.Vector3(n[0], n[1], n[2]).normalize()
  const up = Math.abs(nz.y) > 0.98 ? new THREE.Vector3(0, 0, -1) : new THREE.Vector3(0, 1, 0)
  const x = new THREE.Vector3().crossVectors(up, nz).normalize()
  const y = new THREE.Vector3().crossVectors(nz, x)
  const q = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, nz))
  if (rotDeg) q.multiply(new THREE.Quaternion().setFromAxisAngle(Z, THREE.MathUtils.degToRad(rotDeg)))
  return q
}

/** root 局部坐标下的包围球半径。编辑器滑块范围、表面偏移量都按它换算 */
export function localRadius(root) {
  root.updateWorldMatrix(true, true)
  const box = new THREE.Box3().setFromObject(root, true)
  if (box.isEmpty()) return 1
  const s = new THREE.Vector3()
  root.getWorldScale(s)
  const r = box.getBoundingSphere(new THREE.Sphere()).radius / Math.max(Math.abs(s.x), 1e-9)
  return Math.max(r, 1e-6)
}

/**
 * 射线命中点（Raycaster 的 intersection）→ root 局部坐标的 { p, n }。编辑器落点用。
 * 面法线在命中物体的局部空间里，先转世界再转 root 局部。
 */
export function hitToLocal(root, hit) {
  root.updateWorldMatrix(true, false)
  const p = root.worldToLocal(hit.point.clone())
  let n = new THREE.Vector3(0, 0, 1)
  if (hit.face) {
    n = hit.face.normal.clone().transformDirection(hit.object.matrixWorld)
    const inv = new THREE.Quaternion()
    root.getWorldQuaternion(inv)
    n.applyQuaternion(inv.invert()).normalize()
  }
  const round = v => Math.round(v * 1e6) / 1e6
  return { p: [round(p.x), round(p.y), round(p.z)], n: [round(n.x), round(n.y), round(n.z)] }
}

/* ---------------- 图片缓存 ---------------- */

// 只缓存 <img>（不占显存），贴图每个实例自己建、自己释放——共享贴图会被另一个实例 dispose 掉
const images = new Map()

/**
 * 贴图 / .glb 用的地址：原地址后面加一个 `cors3d=<当前域名>`。
 *
 * ⚠ 2026-09-26 线上踩到：腾讯云 COS 只在请求带 Origin 时才回 Access-Control-Allow-Origin，
 * 而且响应**没有 `Vary: Origin`**。同一张图如果已经在商品页上当普通 <img> 显示过
 * （详情图、组合图、主图），浏览器缓存里那份是不带跨域头的；这里再带 crossOrigin 去取同一个
 * 地址，浏览器直接拿缓存那份 → 判跨域失败 → logo / 屏幕图不显示。服务端 curl 永远验不出来。
 * 换一个查询串就是另一个缓存键，走一次真的带 Origin 的请求。COS 对不认识的查询参数照常回对象。
 *
 * 值用**当前域名**而不是固定的 1：COS 回的 Allow-Origin 是请求方那一个域。管理台（console.）和
 * 前台（主域）同属一个站点，浏览器缓存是共用的——管理台预览过的模型，前台再取会拿到写着
 * console. 的那份，照样被拦（症状是前台「模型加载失败」）。按域名分开缓存就没这个问题。
 *
 * .glb 也走这里（GLTFLoader 用 fetch，同样吃缓存）：前台展示台、管理台预览、标注编辑器三处。
 *
 * blob: / data: 原样返回（2026-09-29，买家定制页用本地 blob 预览还没上传的 logo）：
 * 它们不走网络、没有缓存问题，而 blob 地址后面拼了查询串就再也取不到了。
 */
export function corsUrl(url) {
  if (!url) return url
  if (url.startsWith('blob:') || url.startsWith('data:')) return url
  const host = typeof location !== 'undefined' ? location.host : 'ssr'
  return url + (url.includes('?') ? '&' : '?') + 'cors3d=' + encodeURIComponent(host)
}

function loadImage(url) {
  let entry = images.get(url)
  if (!entry) {
    entry = { img: null, promise: null }
    entry.promise = new Promise((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.decoding = 'async'
      img.onload = () => {
        entry.img = img
        resolve(img)
      }
      img.onerror = () => {
        images.delete(url)
        reject(new Error('image failed: ' + url))
      }
      img.src = corsUrl(url)
    })
    images.set(url, entry)
  }
  return entry
}

/** 已加载就同步回调（编辑器拖滑块时不闪），否则等加载完 */
function withImage(url, cb, onFail) {
  const entry = loadImage(url)
  if (entry.img) {
    cb(entry.img)
    return
  }
  entry.promise.then(cb, err => {
    console.warn('[marks]', err.message)
    onFail?.()
  })
}

function textureOf(source) {
  const t = source instanceof HTMLCanvasElement ? new THREE.CanvasTexture(source) : new THREE.Texture(source)
  t.colorSpace = THREE.SRGBColorSpace
  t.anisotropy = 4
  t.needsUpdate = true
  return t
}

/** 屏幕画面用的 canvas，宽高比跟屏幕走，长边 1024 */
function screenCanvas(w, h) {
  const c = document.createElement('canvas')
  const long = 1024
  c.width = w >= h ? long : Math.max(16, Math.round((long * w) / h))
  c.height = h > w ? long : Math.max(16, Math.round((long * h) / w))
  return c
}

/** 开机画面：底色铺满，logo 按 contain 居中占 70% */
function drawBoot(c, bg, img) {
  const ctx = c.getContext('2d')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, c.width, c.height)
  if (!img) return
  const k = Math.min((c.width * 0.7) / img.naturalWidth, (c.height * 0.7) / img.naturalHeight)
  const dw = img.naturalWidth * k
  const dh = img.naturalHeight * k
  ctx.drawImage(img, (c.width - dw) / 2, (c.height - dh) / 2, dw, dh)
}

/** 工作界面：按 cover 裁切铺满 */
function drawCover(c, img) {
  const ctx = c.getContext('2d')
  const k = Math.max(c.width / img.naturalWidth, c.height / img.naturalHeight)
  const dw = img.naturalWidth * k
  const dh = img.naturalHeight * k
  ctx.drawImage(img, (c.width - dw) / 2, (c.height - dh) / 2, dw, dh)
}

function disposeObject(obj) {
  obj.traverse(node => {
    node.geometry?.dispose?.()
    const mats = Array.isArray(node.material) ? node.material : node.material ? [node.material] : []
    for (const m of mats) {
      m.map?.dispose?.()
      m.dispose()
    }
  })
  obj.removeFromParent()
}

/* ---------------- 主入口 ---------------- */

/**
 * 在 root（= gltf.scene）上挂标注。
 *
 * @param root   模型根节点；标注挂在它下面、用它的局部坐标
 * @param marks  parseMarks 之后的对象（或 null）
 * @param opts   { accent: 开机键颜色, reducedMotion: 不做渐变,
 *                 showPowerRing: 画不画开机键光圈（默认画；前台 2026-09-26 起不画，点击区域照样在） }
 */
export function createMarks(root, marks, opts = {}) {
  const accent = new THREE.Color(opts.accent || '#3b82f6')
  const reducedMotion = !!opts.reducedMotion
  const showPowerRing = opts.showPowerRing !== false

  const group = new THREE.Group()
  group.name = '__marks'
  root.add(group)

  const radius = localRadius(root)
  const lift = radius * 0.002

  // 贴花要投到模型网格上；不能把自己的标注也算进去
  const meshes = []
  root.traverse(o => {
    if (o.isMesh && !isOwn(o)) meshes.push(o)
  })
  function isOwn(o) {
    for (let p = o; p; p = p.parent) if (p === group) return true
    return false
  }

  let current = { logos: [], screen: null, power: null }
  const logoItems = new Map() // id → { key, obj }
  let screenItem = null // { key, obj, planes }
  let powerItem = null // { key, obj, ring, hit }
  let disposed = false

  /* ---- logo ---- */

  function buildLogo(logo, done) {
    withImage(logo.src, img => {
      if (disposed) return
      const holder = new THREE.Group()
      const aspect = img.naturalHeight / Math.max(img.naturalWidth, 1)
      const w = logo.size
      const h = logo.size * aspect
      const q = orientation(logo.n, logo.rot)
      const p = new THREE.Vector3(...logo.p)
      const n = new THREE.Vector3(...logo.n)
      const mat = new THREE.MeshStandardMaterial({
        map: textureOf(img),
        transparent: true,
        opacity: logo.opacity,
        depthWrite: false,
        polygonOffset: true,
        polygonOffsetFactor: -4,
        roughness: 0.55,
        metalness: 0
      })

      const decal = decalOn(p, n, q, w, h)
      if (decal) {
        holder.add(new THREE.Mesh(decal, mat))
      } else {
        // 射线打空（点在边缘、或模型是空壳）：退化成贴在表面上的一片
        const plane = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat)
        plane.position.copy(p).addScaledVector(n, lift)
        plane.quaternion.copy(q)
        holder.add(plane)
      }
      group.add(holder)
      done(holder)
    })
  }

  /** 沿法线反向找到落点所在的网格，在它上面投贴花；几何转回 root 局部坐标 */
  function decalOn(p, n, q, w, h) {
    root.updateWorldMatrix(true, true)
    const toWorld = root.matrixWorld
    const wp = p.clone().applyMatrix4(toWorld)
    const wq = new THREE.Quaternion()
    root.getWorldQuaternion(wq)
    const wn = n.clone().applyQuaternion(wq).normalize()
    const ws = new THREE.Vector3()
    root.getWorldScale(ws)
    const k = Math.abs(ws.x) || 1

    const probe = radius * k * 0.05
    const ray = new THREE.Raycaster(wp.clone().addScaledVector(wn, probe), wn.clone().negate(), 0, probe * 2)
    const hit = ray.intersectObjects(meshes, false)[0]
    if (!hit) return null

    const size = new THREE.Vector3(w * k, h * k, Math.max(w, h) * k * 0.6)
    const euler = new THREE.Euler().setFromQuaternion(wq.clone().multiply(q))
    const geo = new DecalGeometry(hit.object, hit.point, euler, size)
    if (!geo.attributes.position || geo.attributes.position.count === 0) {
      geo.dispose()
      return null
    }
    geo.applyMatrix4(toWorld.clone().invert())
    return geo
  }

  /* ---- 屏幕 ---- */

  // 状态机：off → bootIn → bootHold → runIn → run；任何时候 setPower(false) → fadeOff → off
  let phase = 'off'
  let phaseT = 0
  let staticMode = null // 编辑器用：'boot' | 'run' | null

  function buildScreen(s) {
    const holder = new THREE.Group()
    holder.position.set(...s.p).addScaledVector(new THREE.Vector3(...s.n), lift)
    holder.quaternion.copy(orientation(s.n, s.rot))

    // 黑玻璃：受光照，有反光
    const glass = new THREE.Mesh(
      new THREE.PlaneGeometry(s.w, s.h),
      new THREE.MeshStandardMaterial({ color: 0x050608, roughness: 0.15, metalness: 0.3, polygonOffset: true, polygonOffsetFactor: -2 })
    )
    holder.add(glass)

    // 两层发光画面叠在玻璃上：不受光照（toneMapped false）= 看起来是自己在亮
    const layer = (order) => {
      const m = new THREE.Mesh(
        new THREE.PlaneGeometry(s.w, s.h),
        new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, toneMapped: false, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -3 - order })
      )
      m.position.z = lift * 0.5 * (order + 1)
      m.renderOrder = 2 + order
      m.visible = false
      holder.add(m)
      return m
    }
    const boot = s.bootSrc ? layer(0) : null
    const run = s.runSrc ? layer(1) : null

    if (boot) {
      const c = screenCanvas(s.w, s.h)
      drawBoot(c, s.bootBg, null)
      boot.material.map = textureOf(c)
      withImage(s.bootSrc, img => {
        if (disposed) return
        drawBoot(c, s.bootBg, img)
        boot.material.map.needsUpdate = true
      })
    }
    if (run) {
      withImage(s.runSrc, img => {
        if (disposed) return
        const c = screenCanvas(s.w, s.h)
        drawCover(c, img)
        run.material.map = textureOf(c)
        run.material.needsUpdate = true
      }, () => {
        // 工作界面图挂了：没有就当没配，开机停在 logo 画面
        if (screenItem?.planes.run === run) screenItem.planes.run = null
      })
    }
    group.add(holder)
    return { obj: holder, planes: { boot, run } }
  }

  function setLayer(m, opacity) {
    if (!m) return
    m.material.opacity = opacity
    m.visible = opacity > 0.001 && !!m.material.map
  }

  function renderScreen() {
    if (!screenItem) return
    const { boot, run } = screenItem.planes
    if (staticMode) {
      setLayer(boot, staticMode === 'boot' || !run ? 1 : 0)
      setLayer(run, staticMode === 'run' && run ? 1 : 0)
      return
    }
    const ease = t => t * t * (3 - 2 * t)
    switch (phase) {
      case 'off':
        setLayer(boot, 0)
        setLayer(run, 0)
        break
      case 'bootIn':
        setLayer(boot, ease(Math.min(1, phaseT / FADE_IN_MS)))
        setLayer(run, 0)
        break
      case 'bootHold':
        setLayer(boot, 1)
        setLayer(run, 0)
        break
      case 'runIn':
        setLayer(boot, boot ? 1 : 0)
        setLayer(run, ease(Math.min(1, phaseT / FADE_IN_MS)))
        break
      case 'run':
        setLayer(boot, 0)
        setLayer(run, 1)
        break
      case 'fadeOff': {
        const k = 1 - ease(Math.min(1, phaseT / FADE_OFF_MS))
        if (boot) setLayer(boot, Math.min(boot.material.opacity, k))
        if (run) setLayer(run, Math.min(run.material.opacity, k))
        break
      }
    }
  }

  function advance(dt) {
    if (phase === 'off' || phase === 'run') return
    phaseT += dt
    const hasRun = !!screenItem?.planes.run
    if (phase === 'bootIn' && phaseT >= FADE_IN_MS) go('bootHold')
    // 没有工作界面图：停在开机画面
    else if (phase === 'bootHold' && hasRun && phaseT >= (current.screen?.bootMs ?? 0)) go('runIn')
    else if (phase === 'runIn' && phaseT >= FADE_IN_MS) go('run')
    else if (phase === 'fadeOff' && phaseT >= FADE_OFF_MS) go('off')
  }

  function go(next) {
    phase = next
    phaseT = 0
  }

  /* ---- 开机键 ---- */

  function buildPower(pw) {
    const holder = new THREE.Group()
    holder.position.set(...pw.p).addScaledVector(new THREE.Vector3(...pw.n), lift * 2)
    holder.quaternion.copy(orientation(pw.n, 0))

    const ring = new THREE.Mesh(
      new THREE.RingGeometry(pw.r * 0.72, pw.r, 48),
      new THREE.MeshBasicMaterial({ color: accent, transparent: true, opacity: 0.9, toneMapped: false, depthWrite: false })
    )
    ring.renderOrder = 5
    // 不画光圈时只藏光圈本身：下面那片透明的点击区域还在，点模型上的开机键照样能开关
    ring.visible = showPowerRing
    holder.add(ring)

    // 点击判定区比光圈大一圈：手指点不准。不可见但参与射线（visible 为 false 的物体射线打不到，所以用 opacity 0）
    const hit = new THREE.Mesh(
      new THREE.CircleGeometry(pw.r * 1.6, 24),
      new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, depthWrite: false, colorWrite: false })
    )
    holder.add(hit)
    group.add(holder)
    return { obj: holder, ring, hit }
  }

  let pulseT = 0
  function renderPower(dt) {
    if (!powerItem) return
    pulseT += dt
    const on = isOn()
    const m = powerItem.ring.material
    if (on) {
      m.opacity = 1
      powerItem.ring.scale.setScalar(1)
      m.color.copy(accent).lerp(new THREE.Color('#ffffff'), 0.35)
    } else if (reducedMotion) {
      m.opacity = 0.9
      m.color.copy(accent)
    } else {
      const k = (Math.sin((pulseT / 1400) * Math.PI * 2) + 1) / 2
      m.opacity = 0.45 + 0.5 * k
      powerItem.ring.scale.setScalar(0.9 + 0.2 * k)
      m.color.copy(accent)
    }
  }

  /* ---- 对外 ---- */

  function isOn() {
    return phase !== 'off' && phase !== 'fadeOff'
  }

  function setPower(on, { instant = false } = {}) {
    if (!current.screen || !screenItem) return
    const quick = instant || reducedMotion
    if (on) {
      const hasBoot = !!screenItem.planes.boot
      const hasRun = !!screenItem.planes.run
      // 不做动效时跳过开机 logo，直接到最终画面
      if (quick) go(hasRun ? 'run' : 'bootHold')
      else go(hasBoot ? 'bootIn' : 'runIn')
    } else {
      go(quick ? 'off' : 'fadeOff')
    }
    renderScreen()
  }

  function setMarks(next) {
    const m = next || {}
    const logos = m.logos || []
    // logo：按 id 比对，只重建变了的
    const seen = new Set()
    for (const logo of logos) {
      seen.add(logo.id)
      const key = JSON.stringify(logo)
      const old = logoItems.get(logo.id)
      if (old && old.key === key) continue
      const entry = { key, obj: old?.obj || null }
      logoItems.set(logo.id, entry)
      buildLogo(logo, obj => {
        // 等图的时候又改了一次：旧的这次作废
        if (logoItems.get(logo.id) !== entry) {
          disposeObject(obj)
          return
        }
        if (entry.obj) disposeObject(entry.obj)
        entry.obj = obj
      })
    }
    for (const [id, entry] of logoItems) {
      if (!seen.has(id)) {
        if (entry.obj) disposeObject(entry.obj)
        logoItems.delete(id)
      }
    }

    const sKey = m.screen ? JSON.stringify(m.screen) : ''
    if ((screenItem?.key || '') !== sKey) {
      if (screenItem) disposeObject(screenItem.obj)
      screenItem = m.screen ? { key: sKey, ...buildScreen(m.screen) } : null
      if (!screenItem) phase = 'off'
    }
    const pKey = m.power ? JSON.stringify(m.power) : ''
    if ((powerItem?.key || '') !== pKey) {
      if (powerItem) disposeObject(powerItem.obj)
      powerItem = m.power ? { key: pKey, ...buildPower(m.power) } : null
    }
    current = { logos, screen: m.screen || null, power: m.power || null }
    renderScreen()
    renderPower(0)
  }

  /** 射线是否打中开机键（被机身挡住的不算） */
  function hitPower(raycaster) {
    if (!powerItem || !current.screen) return false
    const hits = raycaster.intersectObjects([powerItem.hit, ...meshes], false)
    return hits[0]?.object === powerItem.hit
  }

  function tick(dt) {
    if (disposed) return
    advance(dt)
    renderScreen()
    renderPower(dt)
  }

  function dispose() {
    disposed = true
    for (const entry of logoItems.values()) if (entry.obj) disposeObject(entry.obj)
    logoItems.clear()
    if (screenItem) disposeObject(screenItem.obj)
    if (powerItem) disposeObject(powerItem.obj)
    screenItem = null
    powerItem = null
    group.removeFromParent()
  }

  setMarks(marks)

  return {
    group,
    meshes,
    radius,
    setMarks,
    setPower,
    isOn,
    hitPower,
    /** 编辑器用：屏幕固定显示 'boot' / 'run'，null 回到开关机状态机 */
    setStatic(mode) {
      staticMode = mode
      renderScreen()
    },
    tick,
    dispose
  }
}
