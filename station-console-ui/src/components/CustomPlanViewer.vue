<!--
  买家 3D 定制方案的只读预览（管理台「定制方案」详情弹窗里用，2026-09-29）。

  画法用 station-shared/model3d/marks.js，和前台方案页、商品页展示台同一份——
  这里看到的就是买家在前台看到的。只读：能转、能模拟开机，不能改。

  ⚠ 只能挂在 destroy-on-close 的弹窗里（同 ModelPreview 的显存规矩），列表里不能一行一个。
-->
<template>
  <div class="cpv">
    <canvas ref="canvasEl"></canvas>
    <div v-if="status" class="cpv-status">{{ status }}</div>
    <div v-if="hasPower && ready" class="cpv-bar">
      <el-button size="small" :type="on ? 'primary' : 'default'" @click="toggle">
        {{ on ? $t('customPlan.powerOff') : $t('customPlan.powerOn') }}
      </el-button>
    </div>
  </div>
</template>

<script setup name="CustomPlanViewer">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { parseMarks, createMarks, corsUrl } from '@station-shared/model3d/marks.js'
import { t } from '@/i18n'

const props = defineProps({
  /** 方案快照里的模型地址（不是商品现在的） */
  src: { type: String, required: true },
  /** 方案快照里的标注（后端已解析成对象） */
  marks: { type: Object, default: null }
})

const canvasEl = ref(null)
const status = ref('')
const ready = ref(false)
const on = ref(false)
const parsed = parseMarks(props.marks)
const hasPower = computed(() => !!(parsed?.screen && parsed?.power))

let renderer = null
let scene = null
let camera = null
let controls = null
let pmrem = null
let wrapper = null
let handle = null
let frame = 0
let lastTick = 0
let resizeObserver = null
let disposed = false

function resize() {
  if (!renderer || !camera || !canvasEl.value) return
  const host = canvasEl.value.parentElement
  const w = Math.max(host.clientWidth, 1)
  const h = Math.max(host.clientHeight, 1)
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

/** 挪外层 wrapper 居中，不碰 gltf.scene（标注坐标在它的局部空间里） */
function frameModel() {
  const box = new THREE.Box3().setFromObject(wrapper)
  const size = box.getSize(new THREE.Vector3())
  wrapper.position.sub(box.getCenter(new THREE.Vector3()))
  const r = Math.max(size.length() / 2, 0.001)
  const dist = (r / Math.sin((camera.fov * Math.PI) / 360)) * 1.1
  camera.position.set(0, r * 0.25, dist)
  camera.near = dist / 100
  camera.far = dist * 100
  camera.updateProjectionMatrix()
  controls.target.set(0, 0, 0)
  controls.update()
}

function tick() {
  if (disposed) return
  frame = requestAnimationFrame(tick)
  const now = performance.now()
  const dt = Math.min(now - (lastTick || now), 100)
  lastTick = now
  handle?.tick(dt)
  controls.update()
  renderer.render(scene, camera)
}

/** 屏幕常亮哪一张：有开机图亮开机图，没有亮工作界面（只配了工作界面的商品，屏幕不能是黑的） */
function staticMode() {
  return parsed?.screen?.bootSrc ? 'boot' : 'run'
}

function toggle() {
  on.value = !on.value
  if (!handle) return
  if (on.value) {
    handle.setStatic(null)
    handle.setPower(true)
  } else {
    handle.setPower(false, { instant: true })
    handle.setStatic(staticMode())
  }
}

onMounted(() => {
  const canvas = canvasEl.value
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  } catch {
    status.value = t('customPlan.noWebgl')
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

  status.value = t('common.loading')
  new GLTFLoader().load(
    corsUrl(props.src),
    gltf => {
      if (disposed) return
      wrapper = new THREE.Group()
      wrapper.add(gltf.scene)
      scene.add(wrapper)
      frameModel()
      handle = createMarks(gltf.scene, parsed, { accent: '#3b82f6', showPowerRing: false })
      // 默认屏幕亮着开机画面：商家点开就能看到买家换的开机 logo
      if (hasPower.value) handle.setStatic(staticMode())
      status.value = ''
      ready.value = true
    },
    undefined,
    err => {
      if (disposed) return
      console.error('[CustomPlanViewer] 模型加载失败', props.src, err)
      status.value = t('customPlan.modelFailed')
    }
  )
  tick()
})

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  handle?.dispose()
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
  renderer = scene = camera = controls = handle = wrapper = null
})
</script>

<style scoped>
.cpv {
  position: relative;
  width: 100%;
  height: 460px;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(180deg, #1b222b 0%, #0d1116 100%);
}
.cpv canvas { display: block; width: 100%; height: 100%; }
.cpv-status {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, .85);
  font-size: 13px;
  pointer-events: none;
}
.cpv-bar { position: absolute; left: 12px; bottom: 12px; }
</style>
