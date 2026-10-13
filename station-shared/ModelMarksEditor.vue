<!--
  商品 3D 标注编辑器：在模型上点出 logo 贴花、屏幕、开机键。

  设计：docs/superpowers/specs/2026-09-25-product-3d-marks-design.md §5

  ## 画法和前台是同一份

  叠加效果全部交给 ./model3d/marks.js，前台展示台用的也是它——这里看到的位置就是买家看到的位置。
  这个组件只管三件事：场景、鼠标落点、右侧面板。

  ## 显存

  和 ModelPreview 一样，宿主必须**只在弹窗里挂它**（el-dialog destroy-on-close），关闭即销毁。

  ## 坐标

  落点用 marks.js 的 hitToLocal 换算到 gltf.scene 的局部坐标。这里自己的取景（包围盒居中）
  挪的是外面那层 wrapper，不碰 gltf.scene，所以存下来的坐标和前台一致。
-->
<template>
  <div class="marks-editor">
    <el-alert v-if="modelChanged" type="warning" :closable="false" show-icon class="marks-alert"
      :title="st('marks.modelChanged', '模型已更换，请检查标注位置')" />

    <div class="marks-body">
      <div class="marks-stage">
        <canvas ref="canvasEl" class="marks-canvas" :class="{ 'is-placing': !!placing }"></canvas>
        <div v-if="status" class="marks-status" :class="{ 'is-error': failed }">{{ status }}</div>
        <div v-if="placing" class="marks-tip">
          {{ placingTip }}
          <el-button link type="primary" size="small" @click="placing = null">{{ st('marks.cancelPlace', '取消') }}</el-button>
        </div>
      </div>

      <div class="marks-panel">
        <!-- ============ logo ============ -->
        <div class="marks-section">
          <div class="marks-head">
            <span>{{ st('marks.logos', 'logo') }}（{{ logos.length }} / {{ LIMITS.maxLogos }}）</span>
            <el-button size="small" type="primary" plain :disabled="!ready || logos.length >= LIMITS.maxLogos"
              @click="openPicker('logo')">{{ st('marks.addLogo', '添加 logo') }}</el-button>
          </div>
          <div v-if="!logos.length" class="marks-empty">{{ st('marks.logoEmpty', '还没有 logo。透明底 PNG 效果最好') }}</div>
          <div v-for="(logo, i) in logos" :key="logo.id" class="marks-item">
            <div class="marks-item-head">
              <img :src="logo.src" class="marks-thumb" alt="" />
              <span class="marks-item-name">logo {{ i + 1 }}</span>
              <el-button link type="primary" size="small" @click="startPlace('logo', logo.id)">{{ st('marks.replace', '重新放置') }}</el-button>
              <el-button link type="danger" size="small" @click="removeLogo(logo.id)">{{ st('marks.remove', '删除') }}</el-button>
            </div>
            <template v-if="logo.p">
              <div class="marks-row"><span>{{ st('marks.size', '大小') }}</span>
                <el-slider v-model="logo.size" :min="R * 0.02" :max="R" :step="R / 500" :show-tooltip="false" /></div>
              <div class="marks-row"><span>{{ st('marks.rotate', '旋转') }}</span>
                <el-slider v-model="logo.rot" :min="-180" :max="180" :step="1" /></div>
              <div class="marks-row"><span>{{ st('marks.opacity', '透明度') }}</span>
                <el-slider v-model="logo.opacity" :min="0.1" :max="1" :step="0.05" /></div>
            </template>
            <div v-else class="marks-empty">{{ st('marks.notPlaced', '还没放到模型上') }}</div>
          </div>
        </div>

        <!-- ============ 屏幕 ============ -->
        <div class="marks-section">
          <div class="marks-head">
            <span>{{ st('marks.screen', '屏幕') }}</span>
            <el-button size="small" type="primary" plain :disabled="!ready" @click="startPlace('screen')">
              {{ screen ? st('marks.replace', '重新放置') : st('marks.setScreen', '设置屏幕') }}</el-button>
          </div>
          <template v-if="screen">
            <div class="marks-row"><span>{{ st('marks.width', '宽') }}</span>
              <el-slider v-model="screen.w" :min="R * 0.02" :max="R * 1.5" :step="R / 500" :show-tooltip="false" /></div>
            <div class="marks-row"><span>{{ st('marks.height', '高') }}</span>
              <el-slider v-model="screen.h" :min="R * 0.02" :max="R * 1.5" :step="R / 500" :show-tooltip="false" /></div>
            <div class="marks-row"><span>{{ st('marks.rotate', '旋转') }}</span>
              <el-slider v-model="screen.rot" :min="-180" :max="180" :step="1" /></div>

            <div class="marks-pick">
              <span>{{ st('marks.bootLogo', '开机 logo') }}</span>
              <img v-if="screen.bootSrc" :src="screen.bootSrc" class="marks-thumb" alt="" />
              <el-button link type="primary" size="small" @click="openPicker('boot')">{{ st('marks.choose', '选择') }}</el-button>
              <el-button v-if="screen.bootSrc" link type="danger" size="small" @click="screen.bootSrc = ''">{{ st('marks.remove', '删除') }}</el-button>
            </div>
            <div class="marks-pick">
              <span>{{ st('marks.bootBg', '背景色') }}</span>
              <el-color-picker v-model="screen.bootBg" size="small" :predefine="['#000000', '#ffffff', '#0b1d3a']" />
            </div>
            <div class="marks-row"><span>{{ st('marks.bootSeconds', 'logo 停留（秒）') }}</span>
              <el-slider v-model="bootSeconds" :min="0" :max="10" :step="0.1" /></div>
            <div class="marks-pick">
              <span>{{ st('marks.runImage', '工作界面') }}</span>
              <img v-if="screen.runSrc" :src="screen.runSrc" class="marks-thumb" alt="" />
              <el-button link type="primary" size="small" @click="openPicker('run')">{{ st('marks.choose', '选择') }}</el-button>
              <el-button v-if="screen.runSrc" link type="danger" size="small" @click="screen.runSrc = ''">{{ st('marks.remove', '删除') }}</el-button>
            </div>
            <el-button link type="danger" size="small" @click="removeScreen">{{ st('marks.removeScreen', '删除屏幕') }}</el-button>
          </template>
          <div v-else class="marks-empty">{{ st('marks.screenEmpty', '在模型的屏幕中心点一下，再调宽高对齐边框') }}</div>
        </div>

        <!-- ============ 开机键 ============ -->
        <div class="marks-section">
          <div class="marks-head">
            <span>{{ st('marks.power', '开机键') }}</span>
            <el-button size="small" type="primary" plain :disabled="!ready" @click="startPlace('power')">
              {{ power ? st('marks.replace', '重新放置') : st('marks.setPower', '设置开机键') }}</el-button>
          </div>
          <template v-if="power">
            <div class="marks-row"><span>{{ st('marks.ringSize', '光圈大小') }}</span>
              <el-slider v-model="power.r" :min="R * 0.005" :max="R * 0.2" :step="R / 1000" :show-tooltip="false" /></div>
            <el-button link type="danger" size="small" @click="power = null">{{ st('marks.remove', '删除') }}</el-button>
          </template>
          <div v-else class="marks-empty">{{ st('marks.powerEmpty', '在模型的开机键上点一下。要和屏幕一起配才会在前台出现') }}</div>
        </div>
      </div>
    </div>

    <div class="marks-footer">
      <el-button :disabled="!canSimulate" @click="toggleSimulate">
        {{ simulating ? st('marks.simOff', '模拟关机') : st('marks.simOn', '模拟开机') }}</el-button>
      <span class="marks-footer-gap"></span>
      <el-button @click="emit('cancel')">{{ st('marks.cancel', '取消') }}</el-button>
      <el-button type="primary" :disabled="!ready" @click="confirm">{{ st('marks.confirm', '确定') }}</el-button>
    </div>

    <media-picker v-model="pickerOpen" type="image" :limit="1" @confirm="onPicked" />
  </div>
</template>

<script setup name="SiteModelMarksEditor">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import MediaPicker from './MediaPicker.vue'
import { useStationT } from './useStationT'
import { LIMITS, parseMarks, createMarks, orientation, localRadius, hitToLocal, corsUrl } from './model3d/marks.js'

const st = useStationT()

const props = defineProps({
  /** 商品当前挂的 .glb 地址 */
  src: { type: String, required: true },
  /** 商品上已有的标注（JSON 字符串或对象），没有就是空 */
  marks: { type: [String, Object], default: null }
})
/** confirm：规范化后的 JSON 字符串，什么都没标时是 null */
const emit = defineEmits(['confirm', 'cancel'])

const initial = parseMarks(props.marks)
const modelChanged = computed(() => !!initial?.model && initial.model !== props.src)

// 面板状态。坐标没落下之前 p 是 null，不交给画法
const logos = ref((initial?.logos || []).map(l => ({ ...l })))
const screen = ref(initial?.screen ? { bootSrc: '', runSrc: '', ...initial.screen } : null)
const power = ref(initial?.power ? { ...initial.power } : null)
const bootSeconds = computed({
  get: () => (screen.value ? screen.value.bootMs / 1000 : 1.5),
  set: v => { if (screen.value) screen.value.bootMs = Math.round(v * 1000) }
})

const canvasEl = ref(null)
const status = ref('')
const failed = ref(false)
const ready = ref(false)
/** 包围球半径（gltf.scene 局部坐标），滑块范围按它换算 */
const R = ref(1)

let renderer = null
let scene = null
let camera = null
let controls = null
let pmrem = null
let wrapper = null
let root = null
let handle = null
let hover = null
let frame = 0
let lastTick = 0
let resizeObserver = null
let disposed = false

/* ---------------- 场景 ---------------- */

function resize() {
  if (!renderer || !camera || !canvasEl.value) return
  const host = canvasEl.value.parentElement
  const w = Math.max(host.clientWidth, 1)
  const h = Math.max(host.clientHeight, 1)
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

/** 挪外层 wrapper 让模型居中，不碰 gltf.scene（坐标要和前台一致） */
function frame3d() {
  const box = new THREE.Box3().setFromObject(wrapper)
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  wrapper.position.sub(center)
  const radius = Math.max(size.length() / 2, 0.001)
  const dist = radius / Math.sin((camera.fov * Math.PI) / 360) * 1.1
  // 从正前方稍高一点看：标注最常在正面
  camera.position.set(0, radius * 0.25, dist)
  camera.near = dist / 100
  camera.far = dist * 100
  camera.updateProjectionMatrix()
  controls.target.set(0, 0, 0)
  controls.minDistance = radius * 0.2
  controls.maxDistance = dist * 4
  controls.update()
}

function tick() {
  if (disposed) return
  frame = requestAnimationFrame(tick)
  const now = performance.now()
  const dt = Math.min(now - (lastTick || now), 100)
  lastTick = now
  flushMarks()
  handle?.tick(dt)
  controls.update()
  renderer.render(scene, camera)
}

onMounted(() => {
  const canvas = canvasEl.value
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  } catch {
    failed.value = true
    status.value = st('model.noWebgl', '这台设备不支持 WebGL，无法预览')
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping

  scene = new THREE.Scene()
  pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  const key = new THREE.DirectionalLight(0xffffff, 2)
  key.position.set(3, 5, 4)
  scene.add(key)

  camera = new THREE.PerspectiveCamera(40, 1, 0.01, 100)
  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true

  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas.parentElement)

  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  canvas.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('pointerleave', hideHover)

  status.value = st('model.loading', '正在加载模型…')
  new GLTFLoader().load(
    corsUrl(props.src),
    gltf => {
      if (disposed) return
      root = gltf.scene
      wrapper = new THREE.Group()
      wrapper.add(root)
      scene.add(wrapper)
      frame3d()
      R.value = localRadius(root)
      handle = createMarks(root, null, { accent: '#3b82f6' })
      dirty = true
      status.value = ''
      ready.value = true
    },
    undefined,
    err => {
      if (disposed) return
      console.error('[ModelMarksEditor] 模型加载失败', props.src, err)
      failed.value = true
      status.value = st('model.failed', '模型加载失败：文件可能不是有效的 .glb，或者地址取不到')
    }
  )
  tick()
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
  handle?.dispose()
  disposeHover()
  wrapper?.traverse(node => {
    node.geometry?.dispose?.()
    const mats = Array.isArray(node.material) ? node.material : node.material ? [node.material] : []
    for (const m of mats) {
      for (const k of Object.keys(m)) if (m[k]?.isTexture) m[k].dispose()
      m.dispose()
    }
  })
  controls?.dispose()
  pmrem?.dispose()
  scene?.environment?.dispose()
  renderer?.dispose()
  renderer?.forceContextLoss?.()
  renderer = scene = camera = controls = handle = root = wrapper = null
})

/* ---------------- 面板 → 画法 ---------------- */

// 滑块拖动时每一下都会触发。贴花要在整张网格上重新投影，大模型一次上百毫秒，
// 所以攒到下一帧、并且最多每 80ms 刷一次
let dirty = false
let lastFlush = 0

function current(forSave = false) {
  const out = { v: LIMITS.version }
  if (forSave) out.model = props.src
  const placed = logos.value.filter(l => l.p)
  if (placed.length) out.logos = placed.map(l => ({ ...l }))
  if (screen.value?.p) {
    const s = { ...screen.value }
    if (!s.bootSrc) delete s.bootSrc
    if (!s.runSrc) delete s.runSrc
    out.screen = s
  }
  if (power.value?.p) out.power = { ...power.value }
  return out
}

function flushMarks() {
  if (!dirty || !handle) return
  const now = performance.now()
  if (now - lastFlush < 80) return
  lastFlush = now
  dirty = false
  handle.setMarks(current())
  if (!simulating.value) handle.setStatic(screen.value?.runSrc ? 'run' : 'boot')
}

watch([logos, screen, power], () => { dirty = true }, { deep: true })

/* ---------------- 落点 ---------------- */

/** { kind: 'logo', id } | { kind: 'screen' } | { kind: 'power' } | null */
const placing = ref(null)
const placingTip = computed(() => {
  const k = placing.value?.kind
  if (k === 'logo') return st('marks.tipLogo', '在模型上点一下放 logo')
  if (k === 'screen') return st('marks.tipScreen', '在屏幕中心点一下')
  if (k === 'power') return st('marks.tipPower', '在开机键上点一下')
  return ''
})

function startPlace(kind, id) {
  placing.value = { kind, id }
}

let downAt = null
const raycaster = new THREE.Raycaster()

function pick(e) {
  if (!handle || !camera) return null
  const rect = canvasEl.value.getBoundingClientRect()
  const ndc = new THREE.Vector2(
    ((e.clientX - rect.left) / rect.width) * 2 - 1,
    -((e.clientY - rect.top) / rect.height) * 2 + 1
  )
  raycaster.setFromCamera(ndc, camera)
  return raycaster.intersectObjects(handle.meshes, false)[0] || null
}

function onPointerDown(e) {
  downAt = { x: e.clientX, y: e.clientY }
}

function onPointerUp(e) {
  const start = downAt
  downAt = null
  if (!placing.value || !start) return
  // 拖动是在转视角，不是落点
  if (Math.hypot(e.clientX - start.x, e.clientY - start.y) > 5) return
  const hit = pick(e)
  if (!hit) return
  place(hitToLocal(root, hit))
}

function place({ p, n }) {
  const r = R.value
  const { kind, id } = placing.value
  if (kind === 'logo') {
    const logo = logos.value.find(l => l.id === id)
    if (logo) Object.assign(logo, { p, n })
  } else if (kind === 'screen') {
    if (screen.value) Object.assign(screen.value, { p, n })
    else screen.value = { p, n, w: round(r * 0.3), h: round(r * 0.4), rot: 0, bootSrc: '', runSrc: '',
      bootBg: LIMITS.defaultBootBg, bootMs: LIMITS.defaultBootMs }
  } else if (kind === 'power') {
    if (power.value) Object.assign(power.value, { p, n })
    else power.value = { p, n, r: round(r * 0.03) }
  }
  placing.value = null
  hideHover()
}

const round = v => Math.round(v * 1e6) / 1e6

/* ---- 悬停预览：跟着鼠标走的半透明一片 ---- */

let hoverPending = null

function onPointerMove(e) {
  if (!placing.value || downAt) return
  // 大模型射线一次几毫秒，攒到下一帧只算一次
  if (!hoverPending) requestAnimationFrame(() => {
    const ev = hoverPending
    hoverPending = null
    if (ev && placing.value) updateHover(ev)
  })
  hoverPending = e
}

function hoverSize() {
  const r = R.value
  const { kind, id } = placing.value
  if (kind === 'logo') {
    const s = logos.value.find(l => l.id === id)?.size || r * 0.15
    return [s, s]
  }
  if (kind === 'screen') return screen.value ? [screen.value.w, screen.value.h] : [r * 0.3, r * 0.4]
  const pr = power.value?.r || r * 0.03
  return [pr * 2, pr * 2]
}

function updateHover(e) {
  const hit = pick(e)
  if (!hit) return hideHover()
  const { p, n } = hitToLocal(root, hit)
  const [w, h] = hoverSize()
  if (!hover) {
    hover = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({ color: '#3b82f6', transparent: true, opacity: 0.45, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    )
    hover.renderOrder = 10
    root.add(hover)
  }
  hover.visible = true
  hover.scale.set(w, h, 1)
  hover.position.set(...p).addScaledVector(new THREE.Vector3(...n), R.value * 0.004)
  hover.quaternion.copy(orientation(n, 0))
}

function hideHover() {
  if (hover) hover.visible = false
}

function disposeHover() {
  if (!hover) return
  hover.geometry.dispose()
  hover.material.dispose()
  hover.removeFromParent()
  hover = null
}

watch(placing, v => { if (!v) hideHover() })

/* ---------------- 选图 ---------------- */

const pickerOpen = ref(false)
let pickerTarget = null

function openPicker(target) {
  pickerTarget = target
  pickerOpen.value = true
}

function onPicked(urls) {
  const url = urls?.[0]
  if (!url) return
  if (pickerTarget === 'logo') {
    const id = 'l' + Date.now().toString(36)
    logos.value.push({ id, src: url, p: null, n: null, size: round(R.value * 0.15), rot: 0, opacity: 1 })
    startPlace('logo', id)
  } else if (pickerTarget === 'boot' && screen.value) {
    screen.value.bootSrc = url
  } else if (pickerTarget === 'run' && screen.value) {
    screen.value.runSrc = url
  }
}

function removeLogo(id) {
  logos.value = logos.value.filter(l => l.id !== id)
  if (placing.value?.id === id) placing.value = null
}

function removeScreen() {
  screen.value = null
  simulating.value = false
}

/* ---------------- 模拟开机 ---------------- */

const simulating = ref(false)
const canSimulate = computed(() => ready.value && !!screen.value?.p && !!(screen.value.bootSrc || screen.value.runSrc))

function toggleSimulate() {
  if (!handle) return
  flushNow()
  if (!simulating.value) {
    simulating.value = true
    handle.setStatic(null)
    handle.setPower(true)
  } else {
    simulating.value = false
    handle.setPower(false, { instant: true })
    handle.setStatic(screen.value?.runSrc ? 'run' : 'boot')
  }
}

function flushNow() {
  lastFlush = 0
  flushMarks()
}

/* ---------------- 确定 ---------------- */

function confirm() {
  if (screen.value?.p && !screen.value.bootSrc && !screen.value.runSrc) {
    ElMessage.warning(st('marks.needScreenImage', '屏幕至少要选开机 logo 或工作界面其中一张图'))
    return
  }
  if (logos.value.some(l => !l.p)) {
    ElMessage.warning(st('marks.needPlace', '还有 logo 没放到模型上'))
    return
  }
  const out = parseMarks(current(true))
  emit('confirm', out ? JSON.stringify(out) : null)
}
</script>

<style scoped>
.marks-editor { display: flex; flex-direction: column; gap: 12px; }
.marks-alert { margin: 0; }
.marks-body { display: flex; gap: 16px; height: 68vh; min-height: 420px; }
.marks-stage {
  position: relative;
  flex: 1;
  min-width: 0;
  background: radial-gradient(circle at 50% 30%, #f7f8fa, #e4e7ed);
  border-radius: 8px;
  overflow: hidden;
}
.marks-canvas { display: block; width: 100%; height: 100%; cursor: grab; }
.marks-canvas.is-placing { cursor: crosshair; }
.marks-status {
  position: absolute; left: 0; right: 0; bottom: 12px;
  text-align: center; font-size: 13px; color: #606266; pointer-events: none;
}
.marks-status.is-error { color: #f56c6c; }
.marks-tip {
  position: absolute; top: 12px; left: 50%; transform: translateX(-50%);
  padding: 6px 12px; border-radius: 16px; background: rgba(0, 0, 0, 0.72); color: #fff; font-size: 13px;
}
.marks-panel { width: 340px; flex-shrink: 0; overflow-y: auto; padding-right: 4px; }
.marks-section { padding: 12px 0; border-bottom: 1px solid #ebeef5; }
.marks-section:last-child { border-bottom: 0; }
.marks-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; margin-bottom: 8px; }
.marks-empty { font-size: 12px; color: #909399; line-height: 1.6; }
.marks-item { padding: 8px; margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 6px; }
.marks-item-head { display: flex; align-items: center; gap: 8px; }
.marks-item-name { flex: 1; font-size: 13px; }
.marks-thumb { width: 32px; height: 32px; object-fit: contain; background: #f2f3f5; border-radius: 4px; }
.marks-row { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #606266; }
.marks-row > span { width: 88px; flex-shrink: 0; }
.marks-row .el-slider { flex: 1; }
.marks-pick { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #606266; margin: 6px 0; }
.marks-pick > span { width: 88px; flex-shrink: 0; }
.marks-footer { display: flex; align-items: center; gap: 8px; }
.marks-footer-gap { flex: 1; }
</style>
