<!--
  WebGL 商品展示台。

  从 kangwanjia-website-EN 的 components/Product3DViewer.client.vue 搬来。
  除了两句状态文案改成 props（这个站是多语的，不能写死英文），其余照原样——
  相机取景、飞行动画、离屏停渲染、显存回收这些和站点数据无关。

  ## 为什么是 .client.vue

  three.js 在服务端跑不了（没有 WebGL、没有 canvas），而且它是这个站**最重的一个包**
  （core + OrbitControls + GLTFLoader + RoomEnvironment 大约 160KB gzip）。
  `.client` 后缀让 Nuxt 把它编译成异步组件：服务端只输出一个占位 div，
  浏览器侧也只有真正渲染到它的时候才去下那个 chunk。

  ⚠ 所以调用方必须**等它进视口再渲染**（`v-if`），不能一进商品页就挂上——
  否则每个只想看图片的买家都白下 160KB。见 app/pages/products/[code].vue 里的
  IntersectionObserver。
-->
<template>
  <div ref="rootEl" class="viewer" :class="{ 'is-ready': ready }">
    <canvas v-if="supported" ref="canvasEl" class="viewer-canvas"></canvas>

    <!-- Poster doubles as the loading state and as the no-WebGL fallback. -->
    <img v-if="poster" class="viewer-poster" :src="poster" alt="" />

    <div v-if="supported && !ready && !failed" class="viewer-status" role="status">
      <span class="viewer-spinner" aria-hidden="true"></span>
      {{ loadingText }}
    </div>
    <p v-else-if="!supported || failed" class="viewer-status viewer-status--error">
      {{ unavailableText }}
    </p>
  </div>
</template>

<script setup lang="ts">
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { ACCENT, buildPlaceholderModel } from '~/lib/product3d/placeholderModel'
import { orbitFor, shortestAngle, type ViewerView } from '~/lib/product3d/framing'
// 商品 3D 标注（logo 贴花 / 屏幕 / 开机键）。和管理台编辑器是同一份，见 nuxt.config.ts 的别名注释
import { corsUrl, createMarks, parseMarks, type Marks, type MarksHandle } from '@station-shared/model3d/marks.js'
// 模型状态（多个 glb 切换，如腿套没气/半充/充满）：对齐、logo 投影、淡入淡出都在这一份里
import { createStateSwitcher, parseStates, type StateStatus, type StateSwitcher } from '@station-shared/model3d/states.js'

export type { ViewerView }

const props = withDefaults(
  defineProps<{
    /** Optional .glb / .gltf under public/. Without it a procedural stand-in model is built. */
    src?: string
    views: ViewerView[]
    /** Shown while the scene boots and whenever WebGL is unavailable. */
    poster?: string
    /** 加载中那行字。多语，由调用方按站点语言给 */
    loadingText?: string
    /** 不支持 WebGL / 模型加载失败时那行字 */
    unavailableText?: string
    /** 详情接口的 model3dMarks。只对真实 glb 生效，占位模型不挂 */
    marks?: unknown
    /** 详情接口的 model3dStates。第一个是主模型（src），其余各一个 glb；不足 2 个不切换 */
    states?: unknown
    /** 配了独立模型的规格：[{ code, name, model, marks }]，marks 空 = logo 跟随主模型（2026-10-09） */
    variants?: { code: string; name?: string; model: string; marks?: unknown; states?: unknown }[] | null
    /** 当前选中的规格编码；它在 variants 里时展示台切到它的模型 */
    variantCode?: string | null
  }>(),
  {
    src: '',
    poster: '',
    loadingText: 'Loading 3D model…',
    unavailableText: '3D view unavailable on this device — showing product photography instead.',
    marks: null,
    states: null,
    variants: null,
    variantCode: null
  }
)

/** 标注里既有屏幕又有开机键时为 true，调用方据此显示「开机 / 关机」按钮 */
const emit = defineEmits<{
  'power-available': [available: boolean]
  /** 某个状态的模型开始加载 / 加载好 / 加载失败。调用方据此转圈、提示 */
  'state-status': [id: string, status: StateStatus]
}>()

const activeView = defineModel<number>('activeView', { default: 0 })
const autoRotate = defineModel<boolean>('autoRotate', { default: true })
/** 屏幕开没开。调用方的按钮和画布上点开机键都改它 */
const powerOn = defineModel<boolean>('powerOn', { default: false })
/** 当前显示的模型状态 id。调用方的状态按钮改它；加载失败会被改回原来那个 */
const stateId = defineModel<string | null>('stateId', { default: null })

const rootEl = ref<HTMLElement | null>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)
const failed = ref(false)
const supported = ref(true)

// Three.js objects are deliberately kept out of refs: they are deep, mutable
// graphs that Vue must not proxy, and nothing renders off them directly.
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let envTexture: THREE.Texture | null = null
let modelRoot: THREE.Group | null = null
let frame = 0
let resizeObserver: ResizeObserver | null = null
let viewObserver: IntersectionObserver | null = null
let onVisibility: (() => void) | null = null
let visible = true
let marks: MarksHandle | null = null
let parsedMarks: Marks | null = null
let marksRoot: THREE.Object3D | null = null
let lastTick = 0
let switcher: StateSwitcher | null = null
let idleHandle = 0

/** World-space bounds of the framed model; every camera distance is fitted to it. */
let modelBox = new THREE.Box3()
let modelRadius = 1

// Camera fly-to state. While `tween` is set the camera is driven here instead
// of by the user's drag.
let tween: {
  from: THREE.Spherical
  to: THREE.Spherical
  fromTarget: THREE.Vector3
  toTarget: THREE.Vector3
  t: number
} | null = null
const TWEEN_MS = 720
let syncCountdown = 0

const DEG = Math.PI / 180

/** Soft ellipse under the model so it does not float on the gradient. */
function buildContactShadow(radius: number) {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = size
  const ctx = canvas.getContext('2d')!
  const grad = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  grad.addColorStop(0, 'rgba(8, 27, 40, 0.55)')
  grad.addColorStop(0.45, 'rgba(8, 27, 40, 0.28)')
  grad.addColorStop(1, 'rgba(8, 27, 40, 0)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(radius * 3.2, radius * 2.1),
    new THREE.MeshBasicMaterial({ map: texture, transparent: true, depthWrite: false })
  )
  mesh.rotation.x = -Math.PI / 2
  mesh.renderOrder = -1
  return mesh
}

/* ---------------- 3D 标注 ---------------- */

function brandColor() {
  const v = getComputedStyle(document.documentElement).getPropertyValue('--brand-blue').trim()
  return v || '#0196FA'
}

function mountMarks() {
  parsedMarks = marksRoot ? parseMarks(props.marks) : null
  if (!marksRoot || !parsedMarks) {
    emit('power-available', false)
    return
  }
  // 标注只是锦上添花：这里出任何错都不能让整个模型进「不支持 3D」的兜底
  // （mountMarks 在 loadModel 的 try 里，抛出去就会把 failed 置 true）
  try {
    marks = createMarks(marksRoot, parsedMarks, {
      accent: brandColor(),
      // 用户 2026-09-26 要求：前台不显示开机键光圈。点模型上的开机键和下面的「开机」按钮照样能开关
      showPowerRing: false,
      reducedMotion: !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    })
    emit('power-available', !!(parsedMarks.screen && parsedMarks.power))
  } catch (err) {
    console.error('[Product3DViewer] 3D 标注挂载失败，只显示模型', err)
    marks?.dispose()
    marks = null
    parsedMarks = null
    emit('power-available', false)
  }
}

/** 开机时把镜头转到正对屏幕：屏幕在侧面或背面时，买家点了开机也看得到它亮 */
function faceScreen() {
  const s = parsedMarks?.screen
  if (!s || !marksRoot || !camera || !controls) return
  marksRoot.updateWorldMatrix(true, false)
  const target = marksRoot.localToWorld(new THREE.Vector3(...s.p))
  const q = new THREE.Quaternion()
  marksRoot.getWorldQuaternion(q)
  const dir = new THREE.Vector3(...s.n).applyQuaternion(q).normalize()
  const radius = orbits[0]?.spherical.radius ?? modelRadius * 3
  const to = new THREE.Spherical().setFromVector3(dir.multiplyScalar(radius))
  // 别让镜头钻到地板下面或者正顶上（和 OrbitControls 的极角限制一致）
  to.phi = THREE.MathUtils.clamp(to.phi, controls.minPolarAngle, controls.maxPolarAngle)
  const from = new THREE.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
  to.theta = shortestAngle(from.theta, to.theta)
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    controls.target.copy(target)
    camera.position.setFromSpherical(to).add(target)
    controls.update()
    return
  }
  tween = { from, to, fromTarget: controls.target.clone(), toTarget: target, t: 0 }
}

// 单击（不是拖动）画布：打中开机键就切换
let downAt: { x: number; y: number } | null = null
const raycaster = new THREE.Raycaster()

function onPointerDown(e: PointerEvent) {
  downAt = { x: e.clientX, y: e.clientY }
}

/** 当前显示的那个模型上的标注：主模型用自己那份；状态/规格模型问切换器（跟随主模型的规格只有 logo） */
function activeHandle(): MarksHandle | null {
  if (!switcher || switcher.isMain()) return marks
  return switcher.currentMarks()
}

function onPointerUp(e: PointerEvent) {
  const start = downAt
  downAt = null
  const handle = activeHandle()
  if (!start || !handle || !camera || !canvasEl.value) return
  if (Math.hypot(e.clientX - start.x, e.clientY - start.y) > 5) return
  const rect = canvasEl.value.getBoundingClientRect()
  const ndc = new THREE.Vector2(
    ((e.clientX - rect.left) / rect.width) * 2 - 1,
    -((e.clientY - rect.top) / rect.height) * 2 + 1
  )
  raycaster.setFromCamera(ndc, camera)
  if (handle.hitPower(raycaster)) powerOn.value = !powerOn.value
}

watch(powerOn, (on) => {
  const handle = activeHandle()
  if (!handle) return
  handle.setPower(on)
  if (on) {
    autoRotate.value = false
    // 正对屏幕只认主模型的屏幕坐标；规格模型上的屏幕开了就开了，不转镜头
    if (!switcher || switcher.isMain()) faceScreen()
  }
})

/* ---------------- 模型状态 + 规格各自的模型 ----------------
   两样都交给 states.js 的切换器：模型状态 id 是 s…，规格是 c:<组合编码>（可带自己的标注）。
   规格有独立模型时它优先；没有时显示当前模型状态（或主模型）。 */

const MAIN_ID = '__main'
let variantStates: ({ id: string; name: string; model: string; marks: Marks | null; projectFrom?: string })[] = []
let mainStateId = MAIN_ID
/** 每个状态/规格开机键可不可用（screen + power 都有），切过去时告诉调用方 */
const powerById = new Map<string, boolean>()

function mountStates() {
  if (!marksRoot) return
  const states = parseStates(props.states)
  variantStates = (props.variants || [])
    .filter(v => v && v.code && /^(\/|https:\/\/)/.test(String(v.model || '')))
    .flatMap(v => {
      const ownStates = parseStates(v.states)
      if (!ownStates) return [{ id: 'c:' + v.code, name: v.name || v.code, model: v.model, marks: parseMarks(v.marks) }]
      const baseId = 'c:' + v.code + ':' + ownStates[0]!.id
      return ownStates.map((s, i) => ({
        id: 'c:' + v.code + ':' + s.id,
        name: `${v.name || v.code} · ${s.name}`,
        model: i === 0 ? v.model : s.model!,
        marks: i === 0 ? parseMarks(v.marks) : null,
        ...(i > 0 ? { projectFrom: baseId } : {})
      }))
    })
  const base = states ?? [{ id: MAIN_ID, name: '' }]
  mainStateId = base[0]!.id
  powerById.set(mainStateId, !!(parsedMarks?.screen && parsedMarks?.power))
  variantStates.forEach(v => powerById.set(v.id, !!(v.marks?.screen && v.marks?.power)))
  if (base.length + variantStates.length < 2) return
  // 同标注：只是锦上添花，出错只记日志，主模型照常
  try {
    switcher = createStateSwitcher({
      mainScene: marksRoot,
      marksOpts: {
        accent: brandColor(),
        showPowerRing: false,
        reducedMotion: !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      },
      onStatus: (id, status) => {
        emit('state-status', id, status)
        // 模型状态失败：按钮退回当前真正显示的那个（规格失败不动规格选择，价格询盘照常）
        const publicStateId = id.startsWith('c:') ? id.split(':').pop() : id
        if (status === 'failed' && stateId.value === publicStateId) {
          const current = switcher?.current ?? null
          stateId.value = current?.startsWith('c:') ? current.split(':').pop() ?? null : current
        }
      },
      onShown: id => emit('power-available', powerById.get(id) ?? false)
    })
    switcher.setStates([...base, ...variantStates])
    switcher.setLogos(parsedMarks?.logos ?? [])
    const selectedComboStates = parseStates(props.variants?.find(x => x.code === props.variantCode)?.states)
    if (selectedComboStates) stateId.value = selectedComboStates[0]!.id
    else if (states) stateId.value = switcher.current
    applyTarget()
  } catch (err) {
    console.error('[Product3DViewer] 模型状态/规格模型挂载失败，只显示主模型', err)
    switcher?.dispose()
    switcher = null
  }
}

/** 该显示哪个：选中的规格有独立模型 → 它；否则当前模型状态；再否则主模型 */
function targetId() {
  const v = props.variantCode ? 'c:' + props.variantCode : null
  if (v && variantStates.some(x => x.id.startsWith(v + ':'))) {
    const selectedState = parseStates(props.variants?.find(x => x.code === props.variantCode)?.states)?.find(s => s.id === stateId.value)
    return selectedState ? `${v}:${selectedState.id}` : variantStates.find(x => x.id.startsWith(v + ':'))!.id
  }
  if (v && variantStates.some(x => x.id === v)) return v
  return stateId.value || mainStateId
}

function applyTarget() {
  if (!switcher) return
  const id = targetId()
  if (id === switcher.current) return
  if (powerOn.value) {
    // 换模型先关屏：屏幕跟着原来那个模型一起隐藏，别留着开机状态
    activeHandle()?.setPower(false, { instant: true })
    powerOn.value = false
  }
  switcher.show(id)
}

watch(stateId, applyTarget)
watch(() => props.variantCode, () => {
  const ownStates = parseStates(props.variants?.find(x => x.code === props.variantCode)?.states)
  if (ownStates) stateId.value = ownStates[0]!.id
  applyTarget()
})

/** 首屏起来以后，浏览器空闲时把其余模型拉下来：模型状态全拉，规格最多 3 个（一个商品可能十几个规格） */
function schedulePreload() {
  if (!switcher) return
  const states = parseStates(props.states)?.slice(1).map(s => s.id) ?? []
  const variants = variantStates.map(v => v.id).filter(id => id !== switcher?.current).slice(0, 3)
  const run = () => switcher?.preload([...states, ...variants])
  const ric = (window as unknown as { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number })
    .requestIdleCallback
  if (ric) idleHandle = ric(run, { timeout: 4000 })
  else idleHandle = window.setTimeout(run, 1500)
}

/* ---------------- scene plumbing ---------------- */

/** Centre the model over the origin, sit it on the floor, and record its bounds. */
function frameModel(root: THREE.Group) {
  const box = new THREE.Box3().setFromObject(root)
  const centre = box.getCenter(new THREE.Vector3())
  root.position.sub(new THREE.Vector3(centre.x, box.min.y, centre.z))

  modelBox = new THREE.Box3().setFromObject(root)
  modelRadius = Math.max(modelBox.getBoundingSphere(new THREE.Sphere()).radius, 0.001)
}

/**
 * Where each preset parks, for the model and viewport shape in play. Fitting a
 * view walks the bounding box, so it is solved once here rather than per frame.
 */
let orbits: { target: THREE.Vector3; spherical: THREE.Spherical }[] = []

function recomputeOrbits() {
  if (!camera || !controls) return
  const fov = camera.fov
  const aspect = camera.aspect
  orbits = props.views.map((v) => orbitFor(v, modelBox, fov, aspect))

  // The manual zoom range has to bracket what the presets ask for: a close-up
  // sits nearer than any bounding-sphere rule of thumb would allow, and
  // OrbitControls would otherwise clamp it straight back out.
  const fits = orbits.map((o) => o.spherical.radius)
  controls.minDistance = Math.max(modelRadius * 0.3, Math.min(...fits) * 0.7)
  controls.maxDistance = Math.max(...fits) * 1.8
}

function applyView(index: number, immediate = false) {
  if (!camera || !controls) return
  const orbit = orbits[index]
  if (!orbit) return

  const target = orbit.target.clone()
  const spherical = orbit.spherical.clone()
  if (immediate) {
    controls.target.copy(target)
    camera.position.setFromSpherical(spherical).add(target)
    controls.update()
    tween = null
    return
  }
  const from = new THREE.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
  // Take the short way round instead of unwinding through a full turn.
  spherical.theta = shortestAngle(from.theta, spherical.theta)
  tween = { from, to: spherical, fromTarget: controls.target.clone(), toTarget: target, t: 0 }
}

// True while the caption is being pulled from the camera, so the watcher below
// does not turn that into a fly-to and yank the view out of the user's hands.
let syncing = false

/** Point the caption at whichever preset the camera currently sits nearest. */
function syncActiveViewFromCamera() {
  if (!camera || !controls) return
  const s = new THREE.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
  let best = activeView.value
  let bestScore = Infinity
  orbits.forEach((orbit, i) => {
    const want = orbit.spherical
    const dTheta = Math.abs(Math.atan2(Math.sin(s.theta - want.theta), Math.cos(s.theta - want.theta)))
    const dPhi = Math.abs(s.phi - want.phi)
    // Distance counts too, or a close-up reads as the wide shot behind it.
    const dZoom = Math.abs(Math.log(s.radius / want.radius))
    const score = dTheta + dPhi * 1.4 + dZoom * 1.6
    if (score < bestScore) {
      bestScore = score
      best = i
    }
  })
  if (best !== activeView.value) {
    syncing = true
    activeView.value = best
    // Watchers flush before nextTick, so the flag is always cleared after use.
    nextTick(() => {
      syncing = false
    })
  }
}

function resize() {
  if (!renderer || !camera || !controls || !rootEl.value) return
  const { clientWidth: w, clientHeight: h } = rootEl.value
  if (!w || !h) return
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  recomputeOrbits()

  // A narrower canvas needs more distance to hold the same model. Pull the
  // camera to the new fit but keep whatever angle the visitor dragged to.
  const fitted = orbits[activeView.value]
  if (!ready.value || tween || !fitted) return
  const current = new THREE.Spherical().setFromVector3(camera.position.clone().sub(controls.target))
  current.radius = fitted.spherical.radius
  camera.position.setFromSpherical(current).add(controls.target)
}

function tick() {
  frame = requestAnimationFrame(tick)
  const now = performance.now()
  // 切回标签页时 dt 会很大，封顶，免得开机动画一帧跳完
  const dt = Math.min(now - (lastTick || now), 100)
  lastTick = now
  if (!renderer || !scene || !camera || !controls || !visible) return
  marks?.tick(dt)
  switcher?.tick(dt)

  if (tween) {
    tween.t = Math.min(1, tween.t + 16.7 / TWEEN_MS)
    // easeInOutCubic — settles without the abrupt stop of a linear lerp.
    const e = tween.t < 0.5 ? 4 * tween.t ** 3 : 1 - (-2 * tween.t + 2) ** 3 / 2
    const s = new THREE.Spherical(
      THREE.MathUtils.lerp(tween.from.radius, tween.to.radius, e),
      THREE.MathUtils.lerp(tween.from.phi, tween.to.phi, e),
      THREE.MathUtils.lerp(tween.from.theta, tween.to.theta, e)
    )
    controls.target.lerpVectors(tween.fromTarget, tween.toTarget, e)
    camera.position.setFromSpherical(s).add(controls.target)
    if (tween.t >= 1) tween = null
  }

  // Auto-rotate is suspended while a fly-to is running so they cannot fight.
  controls.autoRotate = autoRotate.value && !tween
  controls.update()

  // Keep the caption honest as the model drifts past each reference angle.
  // Every few frames is plenty and keeps the per-frame cost off the loop.
  if (!tween && ++syncCountdown % 12 === 0) syncActiveViewFromCamera()
  renderer.render(scene, camera)
}

async function loadModel() {
  const group = new THREE.Group()

  // The model sits in an inner group that frameModel shifts onto the floor, so
  // the shadow — a child of the outer group — stays put at world y = 0.
  const inner = new THREE.Group()
  if (props.src) {
    // corsUrl：按域名分开浏览器缓存，见 marks.js 里那段注释（管理台预览过的模型前台会被拦）
    const gltf = await new GLTFLoader().loadAsync(corsUrl(props.src))
    inner.add(gltf.scene)
    marksRoot = gltf.scene
  } else {
    inner.add(buildPlaceholderModel())
  }
  frameModel(inner)
  mountMarks()
  mountStates()

  group.add(inner)
  group.add(buildContactShadow(modelRadius))
  return group
}

async function init() {
  const canvas = canvasEl.value
  const host = rootEl.value
  if (!canvas || !host) return

  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  } catch {
    supported.value = false
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.outputColorSpace = THREE.SRGBColorSpace

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(38, 1, 0.1, 100)

  // A room probe gives soft studio reflections without shipping an HDR file.
  const pmrem = new THREE.PMREMGenerator(renderer)
  envTexture = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environment = envTexture
  pmrem.dispose()

  const key = new THREE.DirectionalLight(0xffffff, 2.6)
  key.position.set(3, 5, 4)
  scene.add(key)
  const rim = new THREE.DirectionalLight(ACCENT, 1.1)
  rim.position.set(-4, 2, -3)
  scene.add(rim)
  const fill = new THREE.DirectionalLight(0xdfe6ff, 0.9)
  fill.position.set(-2, 1.4, 5)
  scene.add(fill)
  scene.add(new THREE.AmbientLight(0xffffff, 0.3))

  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.07
  controls.enablePan = false
  controls.autoRotateSpeed = 1.1
  controls.minPolarAngle = 12 * DEG
  controls.maxPolarAngle = 160 * DEG
  controls.addEventListener('start', () => {
    tween = null
  })
  controls.addEventListener('end', syncActiveViewFromCamera)
  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointerup', onPointerUp)

  try {
    modelRoot = await loadModel()
  } catch (err) {
    console.error('[Product3DViewer] model failed to load', err)
    failed.value = true
    return
  }
  scene.add(modelRoot)

  resize()
  // resize() bails out if the host has no box yet, so make sure the presets
  // are solved before the opening view is applied.
  recomputeOrbits()
  applyView(activeView.value, true)

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host)

  // Idle offscreen or in a background tab — a spinning product is not worth a GPU.
  viewObserver = new IntersectionObserver(
    ([entry]) => {
      visible = entry.isIntersecting && !document.hidden
    },
    { threshold: 0.05 }
  )
  viewObserver.observe(host)
  onVisibility = () => {
    visible = !document.hidden
  }
  document.addEventListener('visibilitychange', onVisibility)

  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    autoRotate.value = false
  }

  ready.value = true
  frame = requestAnimationFrame(tick)
  schedulePreload()
}

watch(activeView, (i) => {
  if (!syncing) applyView(i)
})

function reset() {
  // Already on view 0 after a drag: fly back explicitly, since assigning the
  // same index would not fire the watcher.
  if (activeView.value === 0) applyView(0)
  else activeView.value = 0
}

defineExpose({ reset })

let booted = false

/** Start the scene the moment the canvas exists, and only ever once. */
function boot() {
  if (booted || !canvasEl.value) return
  booted = true
  init()
}

onMounted(() => {
  if (!window.WebGLRenderingContext) {
    supported.value = false
    return
  }
  // The canvas may or may not exist by the time this hook runs. On a full page
  // load Nuxt renders a .client component's placeholder div first and swaps in
  // the real template afterwards — on the same instance, so the mount hook
  // never fires again; on a client-side route change the template is already
  // there. Cover both: try now, and watch the ref for the case where it is
  // still empty. (A watcher with `immediate` cannot do this on its own — the
  // callback would run inside the watch() call, before its own handle exists.)
  boot()
  watch(canvasEl, boot, { flush: 'post' })
})

onBeforeUnmount(() => {
  if (frame) cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  viewObserver?.disconnect()
  if (onVisibility) document.removeEventListener('visibilitychange', onVisibility)
  canvasEl.value?.removeEventListener('pointerdown', onPointerDown)
  canvasEl.value?.removeEventListener('pointerup', onPointerUp)
  const cic = (window as unknown as { cancelIdleCallback?: (h: number) => void }).cancelIdleCallback
  if (idleHandle) (cic ? cic(idleHandle) : clearTimeout(idleHandle))
  switcher?.dispose()
  switcher = null
  marks?.dispose()
  marks = null
  marksRoot = null
  controls?.dispose()
  // Geometries and materials are not GC-visible to the GPU driver; drop them by hand.
  modelRoot?.traverse((obj) => {
    const mesh = obj as THREE.Mesh
    mesh.geometry?.dispose?.()
    const mat = mesh.material
    if (Array.isArray(mat)) mat.forEach((m) => m.dispose())
    else mat?.dispose?.()
  })
  envTexture?.dispose()
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
  controls = null
  modelRoot = null
})
</script>

<style scoped>
.viewer {
  position: absolute;
  inset: 0;
}

.viewer-canvas {
  display: block;
  width: 100%;
  height: 100%;
  opacity: 0;
  transition: opacity 0.5s ease;
  touch-action: none;
  cursor: grab;
}

.viewer-canvas:active {
  cursor: grabbing;
}

.viewer.is-ready .viewer-canvas {
  opacity: 1;
}

.viewer.is-ready .viewer-poster {
  opacity: 0;
}

/* Covers the canvas until the scene is up, then fades out over it. */
.viewer-poster {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 0.32;
  transition: opacity 0.6s ease;
  filter: blur(2px);
  pointer-events: none;
}

.viewer-status {
  position: absolute;
  left: 50%;
  bottom: 20px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  max-width: 88%;
  text-align: center;
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.85);
  pointer-events: none;
}

.viewer-status--error {
  color: rgba(255, 255, 255, 0.78);
}

.viewer-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.25);
  border-top-color: rgba(255, 255, 255, 0.85);
  animation: viewer-spin 0.8s linear infinite;
}

@keyframes viewer-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .viewer-spinner {
    animation-duration: 2.4s;
  }
}
</style>
