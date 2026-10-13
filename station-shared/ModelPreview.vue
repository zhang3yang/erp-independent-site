<!--
  .glb 模型预览。文件资源页的卡片、选择器、商品编辑页三处共用。

  ## 这是管理台用的最小版，不是前台那个展示台

  前台 station-site/app/components/Product3DViewer.client.vue 有取景动画、多视角、离屏停渲染、
  海报兜底——那是给买家看的。这里只回答一个问题：**这个文件能不能画出来、画出来是不是这个货**。
  所以：包围盒居中、一个固定的四分之三视角、OrbitControls 自转，加载失败一行字。

  不 import 前台那份：两个工程互不依赖（station-shared/README.md），而且前台那份是 .client.vue，
  依赖 Nuxt 的 `~/` 别名。

  ## 显存

  一个 WebGL 上下文占的显存不小，浏览器对同页上下文数量有上限（Chrome 16 个，超了踢最早的）。
  所以宿主必须**只在弹窗里挂它**、关闭即销毁（el-dialog destroy-on-close），
  卡片列表里绝不能一张一个。组件自己在 onBeforeUnmount 里把几何、材质、贴图、renderer 全释放。

  ## 依赖

  ## 模型状态预览（2026-10-08）

  传了 `states`（parseStates 之后的）和 `marks` 时，画布下方出状态按钮，商家 logo 按前台同一套
  （model3d/states.js）投影到各状态上——上传完一眼看出对齐、投影准不准。主模型只画 logo，
  屏幕/开机键不画（预览的是外形和 logo）。

  裸导入 `three`。共用目录在两个项目根目录之外，靠宿主 vite.config.js 的别名指到宿主的
  node_modules（station-console-ui 已加）。新宿主要用它，先加那条别名。
-->
<template>
  <div class="model-preview" :style="{ height: height + 'px' }">
    <canvas ref="canvasEl" class="model-preview-canvas"></canvas>
    <div v-if="status" class="model-preview-status" :class="{ 'is-error': failed }">{{ status }}</div>
    <div v-if="states && ready" class="model-preview-states">
      <button v-for="s in states" :key="s.id" type="button" class="model-preview-chip"
              :class="{ on: s.id === stateId, failed: failedIds.includes(s.id) }" @click="pick(s.id)">
        <span v-if="loadingId === s.id" class="model-preview-spin"></span>{{ s.name }}
      </button>
    </div>
  </div>
</template>

<script setup name="SiteModelPreview">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { useStationT } from './useStationT'
import { corsUrl, createMarks, parseMarks } from './model3d/marks.js'
import { createStateSwitcher } from './model3d/states.js'

const st = useStationT()

const props = defineProps({
  /** .glb 的完整地址 */
  src: { type: String, required: true },
  height: { type: Number, default: 320 },
  /** 可选：parseStates 之后的模型状态列表（第一个是 src 本身） */
  states: { type: Array, default: null },
  /** 可选：主模型上的标注（JSON 字符串或对象），只取 logo 画并投影 */
  marks: { type: [String, Object], default: null }
})

const ready = ref(false)
const stateId = ref(null)
const loadingId = ref(null)
const failedIds = ref([])
let switcher = null
let logoHandle = null
let lastTick = 0

const canvasEl = ref(null)
const status = ref('')
const failed = ref(false)

let renderer = null
let scene = null
let camera = null
let controls = null
let pmrem = null
let model = null
let frame = 0
let resizeObserver = null
let disposed = false

function supportsWebGL(canvas) {
  try {
    return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'))
  } catch {
    return false
  }
}

function resize() {
  if (!renderer || !camera || !canvasEl.value) return
  const host = canvasEl.value.parentElement
  const w = Math.max(host.clientWidth, 1)
  const h = Math.max(host.clientHeight, 1)
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

/** 包围盒居中、底面落到 y=0，相机放在固定的四分之三视角上，距离按包围球算 */
function frameModel(object) {
  const box = new THREE.Box3().setFromObject(object)
  if (box.isEmpty()) return
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  object.position.sub(center)
  object.position.y += size.y / 2

  const radius = Math.max(size.length() / 2, 0.001)
  const fov = camera.fov * Math.PI / 180
  const dist = radius / Math.sin(fov / 2) * 1.15
  const target = new THREE.Vector3(0, size.y / 2, 0)
  const sph = new THREE.Spherical(dist, THREE.MathUtils.degToRad(65), THREE.MathUtils.degToRad(35))
  camera.position.setFromSpherical(sph).add(target)
  camera.near = dist / 100
  camera.far = dist * 100
  camera.updateProjectionMatrix()
  controls.target.copy(target)
  controls.minDistance = radius * 0.3
  controls.maxDistance = dist * 4
  controls.update()
}

function disposeModel() {
  switcher?.dispose()
  switcher = null
  logoHandle?.dispose()
  logoHandle = null
  ready.value = false
  if (!model) return
  scene.remove(model)
  model.traverse(node => {
    if (node.geometry) node.geometry.dispose()
    const mats = Array.isArray(node.material) ? node.material : node.material ? [node.material] : []
    for (const m of mats) {
      for (const key of Object.keys(m)) {
        const v = m[key]
        if (v && v.isTexture) v.dispose()
      }
      m.dispose()
    }
  })
  model = null
}

function load(url) {
  if (!renderer || !url) return
  disposeModel()
  failed.value = false
  status.value = st('model.loading', '正在加载模型…')
  const token = url
  // corsUrl：按域名分开浏览器缓存，见 model3d/marks.js（否则前台会拿到这里缓存的跨域头被拦）
  new GLTFLoader().load(
    corsUrl(url),
    gltf => {
      if (disposed || token !== props.src) return
      // 取景挪外层包，不挪 gltf.scene：标注坐标在它的局部坐标里
      model = new THREE.Group()
      model.add(gltf.scene)
      scene.add(model)
      frameModel(model)
      mountStates(gltf.scene)
      status.value = ''
      ready.value = true
    },
    undefined,
    err => {
      if (disposed || token !== props.src) return
      console.error('[ModelPreview] 模型加载失败', url, err)
      failed.value = true
      status.value = st('model.failed', '模型加载失败：文件可能不是有效的 .glb，或者地址取不到')
    }
  )
}

function mountStates(mainScene) {
  const parsed = parseMarks(props.marks)
  const logos = parsed?.logos || []
  try {
    if (logos.length) logoHandle = createMarks(mainScene, { v: 1, logos }, {})
    if (!props.states || props.states.length < 2) return
    switcher = createStateSwitcher({
      mainScene,
      onStatus: (id, st2) => {
        loadingId.value = st2 === 'loading' ? id : loadingId.value === id ? null : loadingId.value
        if (st2 === 'failed') {
          if (!failedIds.value.includes(id)) failedIds.value = [...failedIds.value, id]
          stateId.value = switcher?.current ?? null
        }
      }
    })
    switcher.setStates(props.states)
    switcher.setLogos(logos)
    stateId.value = switcher.current
  } catch (err) {
    console.error('[ModelPreview] 状态/标注挂载失败，只显示模型', err)
  }
}

function pick(id) {
  if (!switcher) return
  failedIds.value = failedIds.value.filter(x => x !== id)
  stateId.value = id
  switcher.show(id)
}

function tick() {
  if (disposed) return
  frame = requestAnimationFrame(tick)
  const now = performance.now()
  const dt = Math.min(now - (lastTick || now), 100)
  lastTick = now
  switcher?.tick(dt)
  controls.update()
  renderer.render(scene, camera)
}

onMounted(() => {
  const canvas = canvasEl.value
  if (!supportsWebGL(canvas)) {
    failed.value = true
    status.value = st('model.noWebgl', '这台设备不支持 WebGL，无法预览')
    return
  }
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping

  scene = new THREE.Scene()
  pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture

  camera = new THREE.PerspectiveCamera(40, 1, 0.01, 100)
  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.autoRotate = true
  controls.autoRotateSpeed = 1.5

  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas.parentElement)

  load(props.src)
  tick()
})

watch(() => props.src, url => load(url))

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  disposeModel()
  controls?.dispose()
  pmrem?.dispose()
  if (scene?.environment) scene.environment.dispose()
  renderer?.dispose()
  // 主动丢上下文：dispose() 只释放对象，上下文本身要等 GC；连开几次弹窗会先撞上限
  renderer?.forceContextLoss?.()
  renderer = null
  scene = null
  camera = null
  controls = null
})
</script>

<style scoped>
.model-preview {
  position: relative;
  width: 100%;
  background: radial-gradient(circle at 50% 30%, #f7f8fa, #e4e7ed);
  border-radius: 8px;
  overflow: hidden;
}
.model-preview-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.model-preview-status {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 12px;
  text-align: center;
  font-size: 13px;
  color: #606266;
  pointer-events: none;
}
.model-preview-status.is-error { color: #f56c6c; }
.model-preview-states {
  position: absolute;
  left: 0;
  right: 0;
  top: 10px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.model-preview-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 999px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
}
.model-preview-chip.on { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.model-preview-chip.failed { border-color: #f56c6c; color: #f56c6c; }
.model-preview-spin {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: model-preview-spin .8s linear infinite;
}
@keyframes model-preview-spin { to { transform: rotate(360deg); } }
</style>
