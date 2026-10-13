<!--
  买家 3D 定制页 / 方案查看页的画布（2026-09-29）。
  设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md §7。

  ## 它只管画和点

  面板状态（有哪些 logo、各自多大、开机画面换没换）在页面里；这里收一份完整的标注
  （`marks`），交给 station-shared/model3d/marks.js 画——和商品页展示台、管理台编辑器
  同一份画法，买家这里看到的位置就是商家后台看到的位置。

  - `placing` 非空时进入放置模式：鼠标下跟一片半透明预览，单击（位移 < 5px，拖动是转视角）
    落点，`place` 事件带回 gltf.scene 局部坐标的 { p, n }。触屏没有悬停，点一下直接落。
  - 屏幕和开机键不可选中：它们是商家标的，买家这里只能点开机键开关机。
  - 画的是 `marks` 本身，不经 parseMarks——新选的 logo 是 blob 地址，parseMarks 只认 https。
  - `states`（2026-10-08）：多个模型状态切换（如腿套没气/半充/充满），logo 由 states.js 投影过去。
    **只在主状态（第一个）能放置 logo、点开机键**——在别的状态上点出来的坐标不属于主模型。

  ## 坐标

  和 marks.js 的规矩一样：取景只挪外层 wrapper，**绝不挪 gltf.scene**，否则存下来的坐标
  和商品页、管理台对不上。

  ## 为什么是 .client.vue

  three.js 在服务端跑不了，而且包很重；同 Product3DViewer 的理由。
-->
<template>
  <div class="cz-canvas" :class="{ 'is-placing': !!placing, 'is-ready': ready }">
    <canvas ref="canvasEl"></canvas>
    <div v-if="!ready && !failed" class="cz-canvas-status" role="status">
      <span class="cz-spinner" aria-hidden="true"></span>{{ loadingText }}
    </div>
    <p v-else-if="failed" class="cz-canvas-status cz-canvas-status--error">{{ unavailableText }}</p>
    <p v-if="ready && placing && tip" class="cz-canvas-tip">{{ tip }}</p>
  </div>
</template>

<script setup lang="ts">
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import {
  corsUrl, createMarks, hitToLocal, localRadius, orientation,
  type Marks, type MarksHandle, type Vec3
} from '@station-shared/model3d/marks.js'
import {
  createStateSwitcher, type ModelState, type StateStatus, type StateSwitcher
} from '@station-shared/model3d/states.js'

const props = withDefaults(defineProps<{
  src: string
  marks: Marks | null
  /** 放置模式：预览片的大小（模型单位）。null = 不在放置 */
  placing?: { size: number } | null
  /** 不在模拟开机时屏幕固定显示哪一张。编辑时给 'boot'，买家换了开机 logo 马上看得到 */
  staticScreen?: 'boot' | 'run' | null
  loadingText?: string
  unavailableText?: string
  tip?: string
  /** parseStates 之后的模型状态；null = 只有主模型 */
  states?: ModelState[] | null
}>(), {
  placing: null,
  staticScreen: null,
  loadingText: 'Loading 3D model…',
  unavailableText: '3D view unavailable on this device.',
  tip: '',
  states: null
})

const emit = defineEmits<{
  place: [pos: { p: Vec3; n: Vec3 }]
  ready: [info: { radius: number }]
  failed: []
  'state-status': [id: string, status: StateStatus]
}>()

/** 模拟开机。页面的按钮和画布上点开机键都改它 */
const powerOn = defineModel<boolean>('powerOn', { default: false })
/** 当前显示的模型状态 id；加载失败会被改回原来那个 */
const stateId = defineModel<string | null>('stateId', { default: null })

const canvasEl = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)
const failed = ref(false)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let pmrem: THREE.PMREMGenerator | null = null
let wrapper: THREE.Group | null = null
let root: THREE.Object3D | null = null
let handle: MarksHandle | null = null
let hover: THREE.Mesh | null = null
let radius = 1
let frame = 0
let lastTick = 0
let resizeObserver: ResizeObserver | null = null
let disposed = false
let dirty = false
let lastFlush = 0
let switcher: StateSwitcher | null = null
let idleTimer = 0

/** 在主状态上（或者根本没有状态）——只有这时能放 logo、点开机键 */
const onMain = () => !switcher || switcher.isMain()

const reducedMotion = () => !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

function brandColor() {
  const v = getComputedStyle(document.documentElement).getPropertyValue('--brand-blue').trim()
  return v || '#0196FA'
}

/* ---------------- 场景 ---------------- */

function resize() {
  if (!renderer || !camera || !canvasEl.value) return
  const host = canvasEl.value.parentElement!
  const w = Math.max(host.clientWidth, 1)
  const h = Math.max(host.clientHeight, 1)
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

/** 挪外层 wrapper 让模型居中，不碰 gltf.scene */
function frameModel() {
  if (!wrapper || !camera || !controls) return
  const box = new THREE.Box3().setFromObject(wrapper)
  const size = box.getSize(new THREE.Vector3())
  wrapper.position.sub(box.getCenter(new THREE.Vector3()))
  const r = Math.max(size.length() / 2, 0.001)
  const dist = (r / Math.sin((camera.fov * Math.PI) / 360)) * 1.1
  // 从正前方稍高一点看：logo 和屏幕最常在正面
  camera.position.set(0, r * 0.25, dist)
  camera.near = dist / 100
  camera.far = dist * 100
  camera.updateProjectionMatrix()
  controls.target.set(0, 0, 0)
  controls.minDistance = r * 0.3
  controls.maxDistance = dist * 3
  controls.update()
}

function tick() {
  if (disposed) return
  frame = requestAnimationFrame(tick)
  const now = performance.now()
  const dt = Math.min(now - (lastTick || now), 100)
  lastTick = now
  if (!renderer || !scene || !camera || !controls) return
  flushMarks()
  handle?.tick(dt)
  switcher?.tick(dt)
  controls.update()
  renderer.render(scene, camera)
}

/* ---------------- 标注 ---------------- */

// 滑块拖动每一下都会改 marks；贴花要在整张网格上重投影，大模型一次上百毫秒，
// 所以攒到下一帧、最多每 80ms 刷一次（同管理台编辑器）
function flushMarks() {
  if (!dirty || !handle) return
  const now = performance.now()
  if (now - lastFlush < 80) return
  lastFlush = now
  dirty = false
  handle.setMarks(props.marks)
  switcher?.setLogos(props.marks?.logos ?? [])
  if (!powerOn.value) handle.setStatic(props.staticScreen)
}

/* ---------------- 模型状态 ---------------- */

function mountStates() {
  if (!root || !props.states) return
  try {
    switcher = createStateSwitcher({
      mainScene: root,
      marksOpts: { accent: brandColor(), showPowerRing: false, reducedMotion: reducedMotion() },
      onStatus: (id, status) => {
        emit('state-status', id, status)
        if (status === 'failed' && stateId.value === id) stateId.value = switcher?.current ?? null
      }
    })
    switcher.setStates(props.states)
    switcher.setLogos(props.marks?.logos ?? [])
    stateId.value = switcher.current
    // 空闲时把其余状态拉下来，第一次点也快
    idleTimer = window.setTimeout(() => switcher?.preload(), 1500)
  } catch (err) {
    console.error('[CustomizeCanvas] 模型状态挂载失败，只显示主模型', err)
    switcher?.dispose()
    switcher = null
  }
}

watch(stateId, (id) => {
  if (!switcher || !id || id === switcher.current) return
  if (id !== props.states?.[0]?.id) {
    hideHover()
    // 屏幕跟着主模型一起隐藏，先关掉
    if (powerOn.value) powerOn.value = false
  }
  switcher.show(id)
})

watch(() => props.marks, () => { dirty = true }, { deep: true })
watch(() => props.staticScreen, (mode) => {
  if (handle && !powerOn.value) handle.setStatic(mode)
})

watch(powerOn, (on) => {
  if (!handle) return
  if (on) {
    handle.setStatic(null)
    handle.setPower(true, { instant: reducedMotion() })
  } else {
    handle.setPower(false, { instant: true })
    handle.setStatic(props.staticScreen)
  }
})

/* ---------------- 点选 ---------------- */

let downAt: { x: number; y: number } | null = null
const raycaster = new THREE.Raycaster()

function ray(e: PointerEvent) {
  if (!camera || !canvasEl.value) return false
  const rect = canvasEl.value.getBoundingClientRect()
  raycaster.setFromCamera(new THREE.Vector2(
    ((e.clientX - rect.left) / rect.width) * 2 - 1,
    -((e.clientY - rect.top) / rect.height) * 2 + 1
  ), camera)
  return true
}

function pickSurface(e: PointerEvent) {
  if (!handle || !ray(e)) return null
  return raycaster.intersectObjects(handle.meshes, false)[0] || null
}

function onPointerDown(e: PointerEvent) {
  downAt = { x: e.clientX, y: e.clientY }
}

function onPointerUp(e: PointerEvent) {
  const start = downAt
  downAt = null
  if (!start || !handle || !root) return
  // 拖动是在转视角，不是点选
  if (Math.hypot(e.clientX - start.x, e.clientY - start.y) > 5) return
  // 别的状态上点出来的坐标不属于主模型；开机键也只在主状态
  if (!onMain()) return
  if (props.placing) {
    const hit = pickSurface(e)
    if (!hit) return
    hideHover()
    emit('place', hitToLocal(root, hit))
    return
  }
  if (ray(e) && handle.hitPower(raycaster)) powerOn.value = !powerOn.value
}

/* ---- 放置时跟着鼠标走的半透明预览 ---- */

let hoverPending: PointerEvent | null = null

function onPointerMove(e: PointerEvent) {
  // 触屏没有悬停；按着的时候是在转视角
  if (!props.placing || downAt || e.pointerType !== 'mouse' || !onMain()) return
  if (!hoverPending) requestAnimationFrame(() => {
    const ev = hoverPending
    hoverPending = null
    if (ev && props.placing) updateHover(ev)
  })
  hoverPending = e
}

function updateHover(e: PointerEvent) {
  const hit = pickSurface(e)
  if (!hit || !root || !props.placing) return hideHover()
  const { p, n } = hitToLocal(root, hit)
  const s = props.placing.size
  if (!hover) {
    hover = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({ color: brandColor(), transparent: true, opacity: 0.45, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    )
    hover.renderOrder = 10
    root.add(hover)
  }
  hover.visible = true
  hover.scale.set(s, s, 1)
  hover.position.set(...p).addScaledVector(new THREE.Vector3(...n), radius * 0.004)
  hover.quaternion.copy(orientation(n, 0))
}

function hideHover() {
  if (hover) hover.visible = false
}

watch(() => props.placing, (p) => { if (!p) hideHover() })

/* ---------------- 生命周期 ---------------- */

function init() {
  const canvas = canvasEl.value
  if (!canvas) return
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  } catch {
    failed.value = true
    emit('failed')
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05

  scene = new THREE.Scene()
  pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  const key = new THREE.DirectionalLight(0xffffff, 2.2)
  key.position.set(3, 5, 4)
  scene.add(key)
  const fill = new THREE.DirectionalLight(0xdfe6ff, 0.8)
  fill.position.set(-2, 1.4, 5)
  scene.add(fill)

  camera = new THREE.PerspectiveCamera(38, 1, 0.01, 100)
  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.enablePan = false

  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas.parentElement!)

  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  canvas.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('pointerleave', hideHover)

  new GLTFLoader().load(
    corsUrl(props.src),
    (gltf) => {
      if (disposed) return
      root = gltf.scene
      wrapper = new THREE.Group()
      wrapper.add(root)
      scene!.add(wrapper)
      frameModel()
      radius = localRadius(root)
      try {
        handle = createMarks(root, null, {
          accent: brandColor(),
          // 同商品页：不画开机键光圈（用户 2026-09-26 定），点击区照样在
          showPowerRing: false,
          reducedMotion: reducedMotion()
        })
      } catch (err) {
        console.error('[CustomizeCanvas] 标注挂载失败，只显示模型', err)
      }
      mountStates()
      dirty = true
      ready.value = true
      emit('ready', { radius })
    },
    undefined,
    (err) => {
      if (disposed) return
      console.error('[CustomizeCanvas] 模型加载失败', props.src, err)
      failed.value = true
      emit('failed')
    }
  )
  tick()
}

let booted = false

/**
 * canvas 一出现就起场景，只起一次。
 *
 * ⚠ 不能只在 onMounted 里起：整页加载时 Nuxt 先给 .client 组件渲染一个占位 div，
 *   随后在**同一个实例**上换成真模板，onMounted 不会再触发——那时 canvas 还不存在，
 *   直接 return 就永远停在「加载中」（2026-09-30 本机 e2e 踩到：从商品页新标签打开定制页
 *   正好就是整页加载）。客户端路由切过来时模板已经在了。两种都要接住，同 Product3DViewer。
 */
function boot() {
  if (booted || !canvasEl.value) return
  booted = true
  init()
}

onMounted(() => {
  boot()
  watch(canvasEl, boot, { flush: 'post' })
})

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  const canvas = canvasEl.value
  canvas?.removeEventListener('pointerdown', onPointerDown)
  canvas?.removeEventListener('pointermove', onPointerMove)
  canvas?.removeEventListener('pointerup', onPointerUp)
  canvas?.removeEventListener('pointerleave', hideHover)
  clearTimeout(idleTimer)
  switcher?.dispose()
  switcher = null
  handle?.dispose()
  if (hover) {
    hover.geometry.dispose()
    ;(hover.material as THREE.Material).dispose()
    hover.removeFromParent()
  }
  // 显存不归 GC 管，手动释放
  wrapper?.traverse((node) => {
    const mesh = node as THREE.Mesh
    mesh.geometry?.dispose?.()
    const mats = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : []
    for (const m of mats) {
      for (const k of Object.keys(m)) {
        const v = (m as unknown as Record<string, unknown>)[k] as { isTexture?: boolean; dispose?: () => void }
        if (v?.isTexture) v.dispose?.()
      }
      m.dispose()
    }
  })
  controls?.dispose()
  pmrem?.dispose()
  scene?.environment?.dispose()
  renderer?.dispose()
  renderer?.forceContextLoss?.()
  renderer = null
  scene = null
  camera = null
  controls = null
  handle = null
  root = null
  wrapper = null
})
</script>

<style scoped>
.cz-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  border-radius: var(--radius-lg, 14px);
  background:
    radial-gradient(ellipse at 50% 35%, rgba(255, 255, 255, 0.10), transparent 60%),
    linear-gradient(180deg, #1b222b 0%, #0d1116 100%);
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  touch-action: none;
  cursor: grab;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.cz-canvas.is-ready canvas { opacity: 1; }
canvas:active { cursor: grabbing; }
.cz-canvas.is-placing canvas { cursor: crosshair; }

.cz-canvas-status {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  max-width: 86%;
  text-align: center;
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.85);
  pointer-events: none;
}

.cz-canvas-tip {
  position: absolute;
  left: 50%;
  top: 14px;
  transform: translateX(-50%);
  margin: 0;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 0.82rem;
  white-space: nowrap;
  pointer-events: none;
}

.cz-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.25);
  border-top-color: rgba(255, 255, 255, 0.85);
  animation: cz-spin 0.8s linear infinite;
}

@keyframes cz-spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .cz-spinner { animation-duration: 2.4s; }
}
</style>
