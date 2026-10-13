/*
  商品 3D 模型状态：一个商品挂多个 glb，按钮切换（首个用例：腿套「没气 / 半充 / 充满」）。

  设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md

  ## 为什么只有这一份

  商品页展示台、买家定制页画布、管理台预览三处都切状态。对齐和投影只要差一点，
  同一个 logo 在三处就落在三个地方——所以和 marks.js 一样只写一份。

  ## 第一个状态是主模型

  主模型（商品的 model3d）由宿主自己加载、自己挂标注，这里只接管它的显隐。
  其余状态各自一个外层包（wrapper）挂在主模型的父节点下：

  - **对齐**：只设 wrapper 的缩放和位置，让它的包围盒最长边和主模型相等、中心重合。
    **绝不动 gltf.scene 本身**（同 marks.js 的坐标规矩）。
  - **投影**：标注坐标只存一份（主模型 gltf.scene 局部坐标）。切到别的状态时，
    从 logo 在主模型上的位置沿 ±法线各发一条射线打新模型，取最近的命中点当新位置、
    命中面的法线当新朝向；两边都打空就在这个状态里不显示这个 logo（不悬空）。
    屏幕、开机键不投影——它们只在主状态出现。

  ## 规格各自的模型（2026-10-09）

  规格模型也当成主模型的一种「状态」走这里（id 约定 `c:<组合编码>`，模型状态是 `s…`）。
  状态项可以带 `marks`（规格自己单独标的标注）：带了就**直接用它画**（含屏幕 / 开机键），不投影；
  没带就照旧投影主模型的 logo（= 跟随主模型）。设计 docs/superpowers/specs/2026-10-09-combo-model3d-design.md

  ⚠ 投影的前提是各状态模型朝向一致（运营拍照要同一个角度）。朝向不一致时 logo 会打空或贴错面，
  管理台「预览全部状态」能一眼看出来。
*/
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { corsUrl, createMarks } from './marks.js'
import { parseStates } from './stateList.js'

const FADE_MS = 300
/** refreshMarks 用：这一项画的是自带标注，不随主模型 logo 重投 */
const OWN = Symbol('own-marks')
/** 射线最远打多远：新模型包围盒最长边的这个比例 */
const PROBE_RATIO = 0.15

/* ---------------- 解析：在 stateList.js（不引 three，页面可以直接用） ---------------- */

export { parseStates }

/* ---------------- 对齐 ---------------- */

function worldBox(obj) {
  obj.updateWorldMatrix(true, true)
  const box = new THREE.Box3()
  // 标注组不算：logo 贴在表面上，不影响外形，但屏幕面片会让盒子差一点
  obj.traverse(o => {
    if (!o.isMesh) return
    for (let p = o; p && p !== obj; p = p.parent) if (p.name === '__marks') return
    box.expandByObject(o, true)
  })
  return box
}

/**
 * 让 wrapper（里面装着状态模型）的包围盒最长边等于 main 的、中心重合。
 * 假定 wrapper 的父节点没有旋转（三个宿主都只平移外层）。
 */
export function alignTo(main, wrapper) {
  wrapper.position.set(0, 0, 0)
  wrapper.scale.setScalar(1)
  const target = worldBox(main)
  const own = worldBox(wrapper)
  if (target.isEmpty() || own.isEmpty()) return
  const tSize = target.getSize(new THREE.Vector3())
  const oSize = own.getSize(new THREE.Vector3())
  const k = Math.max(tSize.x, tSize.y, tSize.z) / Math.max(oSize.x, oSize.y, oSize.z, 1e-9)

  const parent = wrapper.parent
  const ps = new THREE.Vector3(1, 1, 1)
  parent?.getWorldScale(ps)
  const origin = new THREE.Vector3()
  wrapper.getWorldPosition(origin)
  // 以 wrapper 原点为中心缩放 k 倍之后，状态模型的中心在哪
  const scaled = own.getCenter(new THREE.Vector3()).sub(origin).multiplyScalar(k).add(origin)
  const delta = target.getCenter(new THREE.Vector3()).sub(scaled)
  wrapper.scale.setScalar(k)
  wrapper.position.set(delta.x / (ps.x || 1), delta.y / (ps.y || 1), delta.z / (ps.z || 1))
  wrapper.updateWorldMatrix(true, true)
}

/* ---------------- 投影 ---------------- */

function meshesOf(root) {
  const out = []
  root.traverse(o => {
    if (!o.isMesh) return
    for (let p = o; p; p = p.parent) if (p.name === '__marks') return
    out.push(o)
  })
  return out
}

/**
 * 把主模型局部坐标的 logo 投到状态模型上，返回状态模型局部坐标的 logo。打空的丢掉。
 *
 * @param logos      主模型 gltf.scene 局部坐标的 logo 列表
 * @param mainScene  主模型 gltf.scene
 * @param stateScene 状态模型 gltf.scene（已经对齐过）
 */
export function projectLogos(logos, mainScene, stateScene) {
  if (!logos?.length) return []
  mainScene.updateWorldMatrix(true, true)
  stateScene.updateWorldMatrix(true, true)
  const meshes = meshesOf(stateScene)
  const box = worldBox(stateScene)
  const size = box.getSize(new THREE.Vector3())
  const far = Math.max(size.x, size.y, size.z) * PROBE_RATIO
  const mq = new THREE.Quaternion()
  mainScene.getWorldQuaternion(mq)
  const sq = new THREE.Quaternion()
  stateScene.getWorldQuaternion(sq)
  const sqInv = sq.clone().invert()
  const ms = new THREE.Vector3()
  mainScene.getWorldScale(ms)
  const ss = new THREE.Vector3()
  stateScene.getWorldScale(ss)
  const sizeK = (Math.abs(ms.x) || 1) / (Math.abs(ss.x) || 1)

  // ⚠ 射线要双面打：「充满」比「没气」大一圈，logo 原来的位置在新模型**里面**，
  //   往外打碰到的是网格背面——three 默认按材质的 side 剔掉背面，那样鼓起来的状态上 logo 全丢
  //   （2026-10-08 Node 几何测试抓到）。投完还原。
  const sides = new Map()
  for (const m of meshes) {
    for (const mat of Array.isArray(m.material) ? m.material : [m.material]) {
      if (mat && !sides.has(mat)) {
        sides.set(mat, mat.side)
        mat.side = THREE.DoubleSide
      }
    }
  }
  try {
    return castAll(logos, meshes, far, mainScene, stateScene, mq, sqInv, sizeK)
  } finally {
    for (const [mat, side] of sides) mat.side = side
  }
}

function castAll(logos, meshes, far, mainScene, stateScene, mq, sqInv, sizeK) {
  const ray = new THREE.Raycaster()
  const out = []
  for (const logo of logos) {
    const wp = mainScene.localToWorld(new THREE.Vector3(...logo.p))
    const wn = new THREE.Vector3(...logo.n).applyQuaternion(mq).normalize()
    let best = null
    for (const dir of [wn, wn.clone().negate()]) {
      ray.set(wp, dir)
      ray.far = far
      const hit = ray.intersectObjects(meshes, false)[0]
      if (hit && (!best || hit.distance < best.distance)) best = hit
    }
    if (!best) continue

    let hn = wn.clone()
    if (best.face) {
      const nm = new THREE.Matrix3().getNormalMatrix(best.object.matrixWorld)
      hn = best.face.normal.clone().applyMatrix3(nm).normalize()
      // 双面/法线朝里的网格：翻到和原法线同一侧
      if (hn.dot(wn) < 0) hn.negate()
    }
    const p = stateScene.worldToLocal(best.point.clone())
    const n = hn.applyQuaternion(sqInv).normalize()
    out.push({
      ...logo,
      p: [p.x, p.y, p.z],
      n: [n.x, n.y, n.z],
      size: logo.size * sizeK
    })
  }
  return out
}

/* ---------------- 淡入淡出 ---------------- */

/** 整棵树的材质乘一个不透明度系数；k = 1 时还原成原样 */
function setFade(root, k) {
  root.traverse(o => {
    const mats = Array.isArray(o.material) ? o.material : o.material ? [o.material] : []
    for (const m of mats) {
      if (!m.userData.__fade) {
        if (k === 1) continue
        m.userData.__fade = { transparent: m.transparent, opacity: m.opacity, depthWrite: m.depthWrite }
      }
      const orig = m.userData.__fade
      if (k === 1) {
        m.transparent = orig.transparent
        m.opacity = orig.opacity
        m.depthWrite = orig.depthWrite
        delete m.userData.__fade
      } else {
        m.transparent = true
        m.opacity = orig.opacity * k
        // 两个模型交叠淡入淡出时互相挡深度会出黑块
        m.depthWrite = false
      }
      m.needsUpdate = true
    }
  })
}

function disposeTree(obj) {
  obj.traverse(node => {
    node.geometry?.dispose?.()
    const mats = Array.isArray(node.material) ? node.material : node.material ? [node.material] : []
    for (const m of mats) {
      for (const key of Object.keys(m)) {
        const v = m[key]
        if (v && v.isTexture) v.dispose()
      }
      m.dispose()
    }
  })
  obj.removeFromParent()
}

/* ---------------- 切换控制 ---------------- */

/**
 * @param opts {
 *   mainScene:  主模型 gltf.scene（宿主自己加载、自己挂标注）
 *   marksOpts:  传给 createMarks 的选项（accent / reducedMotion）
 *   onStatus:   (id, 'loading' | 'ready' | 'failed') => void
 *   onShown:    (id) => void  真正切过去之后（淡入开始时）
 *   load:       可选，(url) => Promise<Object3D>，测试用；默认 GLTFLoader
 * }
 */
export function createStateSwitcher(opts) {
  const mainScene = opts.mainScene
  const parent = mainScene.parent
  const reducedMotion = !!opts.marksOpts?.reducedMotion
  const load = opts.load || (url => new GLTFLoader().loadAsync(corsUrl(url)).then(g => g.scene))

  let states = []
  let logos = []
  let currentId = null
  let wantId = null
  let disposed = false
  /** id → { wrapper, scene, marks, projectedFor, projectedLogos, promise, failed } */
  const entries = new Map()
  /** 正在进行的淡入淡出 */
  let fade = null

  function mainId() {
    return states[0]?.id ?? null
  }

  function holderOf(id) {
    if (id === mainId()) return mainScene
    return entries.get(id)?.wrapper ?? null
  }

  function ensure(id) {
    const state = states.find(s => s.id === id)
    if (!state || id === mainId()) return Promise.resolve(null)
    let entry = entries.get(id)
    if (entry?.promise && !entry.failed) return entry.promise
    entry = { wrapper: null, scene: null, marks: null, projectedFor: null, projectedLogos: [], promise: null, failed: false }
    entries.set(id, entry)
    entry.promise = load(state.model).then(scene => {
      if (disposed) {
        disposeTree(scene)
        return null
      }
      const wrapper = new THREE.Group()
      wrapper.name = '__state_' + id
      wrapper.visible = false
      wrapper.add(scene)
      parent.add(wrapper)
      alignTo(mainScene, wrapper)
      entry.wrapper = wrapper
      entry.scene = scene
      return entry
    }).catch(err => {
      entry.failed = true
      console.error('[states] 状态模型加载失败', state.model, err)
      throw err
    })
    return entry.promise
  }

  /** 状态模型上的 logo：跟着当前 logos 重算（只在要显示时算） */
  function refreshMarks(entry, state) {
    if (!entry?.scene) return
    // 自带标注（规格单独标注）：画一次就够，和主模型的 logo 无关
    if (state?.marks) {
      if (entry.projectedFor === OWN) return
      entry.marks?.dispose()
      entry.marks = null
      entry.projectedFor = OWN
      entry.projectedLogos = state.marks.logos || []
      try {
        entry.marks = createMarks(entry.scene, state.marks, opts.marksOpts || {})
      } catch (err) {
        console.error('[states] 规格模型的标注挂载失败', err)
      }
      return
    }
    if (entry.projectedFor === logos) return
    entry.marks?.dispose()
    entry.marks = null
    const sourceId = state?.projectFrom
    const sourceEntry = sourceId ? entries.get(sourceId) : null
    const sourceScene = sourceEntry?.scene || mainScene
    const sourceLogos = sourceEntry?.projectedLogos || logos
    const projected = projectLogos(sourceLogos, sourceScene, entry.scene)
    entry.projectedLogos = projected
    entry.projectedFor = sourceId ? sourceEntry : logos
    if (projected.length) {
      try {
        entry.marks = createMarks(entry.scene, { v: 1, logos: projected }, opts.marksOpts || {})
      } catch (err) {
        console.error('[states] 状态模型上的 logo 挂载失败', err)
      }
    }
  }

  function startFade(fromId, toId) {
    const from = holderOf(fromId)
    const to = holderOf(toId)
    if (fade) finishFade()
    if (!to) return
    to.visible = true
    if (!from || from === to || reducedMotion) {
      if (from && from !== to) from.visible = false
      return
    }
    setFade(to, 0)
    fade = { from, to, t: 0 }
  }

  function finishFade() {
    if (!fade) return
    setFade(fade.from, 1)
    setFade(fade.to, 1)
    fade.from.visible = false
    fade = null
  }

  async function show(id) {
    if (!states.some(s => s.id === id)) return
    wantId = id
    if (id === currentId) return
    if (id !== mainId()) {
      opts.onStatus?.(id, 'loading')
      let entry
      try {
        entry = await ensure(id)
      } catch {
        opts.onStatus?.(id, 'failed')
        if (wantId === id) wantId = currentId
        return
      }
      if (disposed || !entry) return
      opts.onStatus?.(id, 'ready')
      // 加载期间又点了别的：只进缓存，不切
      if (wantId !== id) return
      const state = states.find(s => s.id === id)
      if (state?.projectFrom) {
        const sourceState = states.find(s => s.id === state.projectFrom)
        if (sourceState && state.projectFrom !== mainId()) {
          try {
            const sourceEntry = await ensure(state.projectFrom)
            if (sourceEntry) refreshMarks(sourceEntry, sourceState)
          } catch {
            opts.onStatus?.(id, 'failed')
            if (wantId === id) wantId = currentId
            return
          }
        }
      }
      if (disposed || wantId !== id) return
      refreshMarks(entry, state)
    }
    const from = currentId
    currentId = id
    startFade(from, id)
    opts.onShown?.(id)
  }

  return {
    /** 换状态列表（parseStates 之后的）。当前状态不在新列表里时回到主状态 */
    setStates(next) {
      states = Array.isArray(next) ? next : []
      for (const [id, entry] of entries) {
        const s = states.find(x => x.id === id)
        if (!s || id === mainId()) {
          entry.marks?.dispose()
          if (entry.wrapper) disposeTree(entry.wrapper)
          entries.delete(id)
        }
      }
      if (currentId == null || !states.some(s => s.id === currentId)) {
        if (fade) finishFade()
        mainScene.visible = true
        currentId = mainId()
        wantId = currentId
      }
    },
    /** 换 logo（主模型局部坐标）。当前显示的状态立刻重投，其余等切过去时再算 */
    setLogos(next) {
      logos = Array.isArray(next) ? next : []
      const entry = entries.get(currentId)
      if (entry) refreshMarks(entry, states.find(s => s.id === currentId))
    },
    show,
    /** 空闲时预加载其余状态。失败不提示——真点到它时再报 */
    /** ids 给了就只预加载这几个（规格可能很多，宿主自己挑），不给就是全部 */
    async preload(ids) {
      const list = ids ? states.filter(s => ids.includes(s.id)) : states.slice(1)
      for (const s of list) {
        if (disposed) return
        try {
          await ensure(s.id)
        } catch {
          // 点到时 ensure 会重试一次
        }
      }
    },
    get current() {
      return currentId
    },
    isMain() {
      return currentId === mainId()
    },
    /** 当前显示的那个非主模型上的标注句柄（主模型时 null，宿主用自己那份）。开机、点开机键用 */
    currentMarks() {
      return currentId === mainId() ? null : (entries.get(currentId)?.marks ?? null)
    },
    tick(dt) {
      if (fade) {
        fade.t = Math.min(1, fade.t + dt / FADE_MS)
        setFade(fade.to, fade.t)
        setFade(fade.from, 1 - fade.t)
        if (fade.t >= 1) finishFade()
      }
      for (const [id, entry] of entries) {
        if (id === currentId || (fade && entry.wrapper === fade.from)) entry.marks?.tick(dt)
      }
    },
    dispose() {
      disposed = true
      if (fade) finishFade()
      for (const entry of entries.values()) {
        entry.marks?.dispose()
        if (entry.wrapper) disposeTree(entry.wrapper)
      }
      entries.clear()
      mainScene.visible = true
    }
  }
}
