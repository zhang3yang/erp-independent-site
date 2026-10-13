/*
 * 3D 展示台的取景计算。
 *
 * 从 kangwanjia-website-EN 的 lib/product3d/framing.ts 原样搬来，一行没改——
 * 里面全是纯数学（球坐标、包围盒、二分法求取景距离），不依赖那个站的任何数据，
 * 所以没有改的必要。原文注释保留英文，方便和上游对照。
 */
import * as THREE from 'three'

/** One reference view: where the camera parks and what it looks at. */
export interface ViewerView {
  label: string
  /** Horizontal camera angle in degrees; 0 looks straight at the model's front. */
  azimuth: number
  /** Vertical camera angle in degrees above the floor. */
  elevation: number
  /**
   * Multiplier on the auto-fit distance. 1 frames the whole model; below 1
   * moves in for a close-up and deliberately crops.
   */
  zoom?: number
  /** What the camera aims at, as a fraction of the model's height. Default 0.5. */
  target?: number
}

const DEG = Math.PI / 180

/** How much of the frustum the model may fill at zoom 1 — the rest is margin. */
const FILL = 0.86

/** Below this the camera is inside the model; OrbitControls also loses its up axis at the poles. */
const MIN_PHI = 6 * DEG
const MAX_PHI = 174 * DEG

export function viewTarget(view: ViewerView, box: THREE.Box3) {
  const height = box.max.y - box.min.y
  return new THREE.Vector3(0, box.min.y + height * (view.target ?? 0.5), 0)
}

/** Unit-radius spherical for the view's angles, clamped off the poles. */
export function viewDirection(view: ViewerView) {
  return new THREE.Spherical(
    1,
    THREE.MathUtils.clamp((90 - view.elevation) * DEG, MIN_PHI, MAX_PHI),
    view.azimuth * DEG
  )
}

function boxCorners(box: THREE.Box3) {
  const out: THREE.Vector3[] = []
  for (const x of [box.min.x, box.max.x])
    for (const y of [box.min.y, box.max.y])
      for (const z of [box.min.z, box.max.z]) out.push(new THREE.Vector3(x, y, z))
  return out
}

/**
 * Smallest distance along `sph` at which every corner of `box` still sits
 * inside the padded frustum. Solved by bisection rather than a bounding-sphere
 * formula, which over-shoots badly for a wide, flat product on a wide canvas.
 */
export function fitDistance(
  box: THREE.Box3,
  target: THREE.Vector3,
  sph: THREE.Spherical,
  fovDeg: number,
  aspect: number
) {
  const tanV = Math.tan((fovDeg * DEG) / 2)
  const tanH = tanV * Math.max(aspect, 0.01)
  const dir = new THREE.Vector3().setFromSpherical(sph).normalize()
  const corners = boxCorners(box)

  const eye = new THREE.Vector3()
  const view = new THREE.Matrix4()
  const up = new THREE.Vector3(0, 1, 0)
  const p = new THREE.Vector3()

  const fits = (dist: number) => {
    eye.copy(dir).multiplyScalar(dist).add(target)
    view.lookAt(eye, target, up)
    view.setPosition(eye)
    view.invert()
    for (const c of corners) {
      p.copy(c).applyMatrix4(view)
      const depth = -p.z
      if (depth <= 0.001) return false
      if (Math.abs(p.x) > depth * tanH * FILL) return false
      if (Math.abs(p.y) > depth * tanV * FILL) return false
    }
    return true
  }

  const span = box.getSize(new THREE.Vector3()).length() || 1
  let lo = span * 0.05
  let hi = span * 8
  if (!fits(hi)) return hi
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2
    if (fits(mid)) hi = mid
    else lo = mid
  }
  return hi
}

/** Camera target and orbit position for a view at the current viewport aspect. */
export function orbitFor(view: ViewerView, box: THREE.Box3, fovDeg: number, aspect: number) {
  const target = viewTarget(view, box)
  const spherical = viewDirection(view)
  spherical.radius = fitDistance(box, target, spherical, fovDeg, aspect) * (view.zoom ?? 1)
  return { target, spherical }
}

/** Shortest signed angle from a to b, so a fly-to never unwinds the long way. */
export function shortestAngle(from: number, to: number) {
  return from + Math.atan2(Math.sin(to - from), Math.cos(to - from))
}
