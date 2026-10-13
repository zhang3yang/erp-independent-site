/*
 * 没有真实模型时用的程序化替身模型（气压按摩仪：控制盒 + 两条腿套 + 气管）。
 *
 * 从 kangwanjia-website-EN 的 lib/product3d/placeholderModel.ts 原样搬来。
 *
 * ⚠ 这个替身长得像一台按摩仪，**只适合当演示**（/3d-demo）。商品详情页上不会用它：
 *   给一个阀门商品配一台按摩仪的三维模型，比不显示 3D 更糟。详情页只在商品真的
 *   有 .glb 模型时才渲染展示台，见 app/pages/products/[code].vue。
 */
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'

/* ---------------- procedural stand-in model ----------------
   Used until a real .glb is dropped in. It is a stylised build of the
   product: control unit with screen, dial and zone keys, two tapered leg
   sleeves with chamber rings, and the hoses linking them. */

const SHELL = 0xfbfaff
const SHELL_DEEP = 0x2b2438
const FABRIC = 0x574a7a
/** Brand purple, shared with the viewer's rim light. */
export const ACCENT = 0x9e68f8

export function buildPlaceholderModel(): THREE.Group {
  const g = new THREE.Group()

  const shell = new THREE.MeshPhysicalMaterial({
    color: SHELL,
    roughness: 0.42,
    metalness: 0.04,
    clearcoat: 0.7,
    clearcoatRoughness: 0.28
  })
  const deep = new THREE.MeshPhysicalMaterial({ color: SHELL_DEEP, roughness: 0.5, metalness: 0.15 })
  const metal = new THREE.MeshStandardMaterial({ color: 0xb9b3c6, roughness: 0.28, metalness: 0.9 })
  const glass = new THREE.MeshStandardMaterial({
    color: 0x14101d,
    roughness: 0.15,
    metalness: 0.2,
    emissive: new THREE.Color(ACCENT).multiplyScalar(0.22)
  })
  const fabric = new THREE.MeshPhysicalMaterial({
    color: FABRIC,
    roughness: 0.92,
    metalness: 0,
    sheen: 0.6,
    sheenColor: new THREE.Color(0xb9a8e0),
    sheenRoughness: 0.7,
    // Hold the fabric back from the bright room probe so it reads as a deep
    // matte weave next to the glossy white shell.
    envMapIntensity: 0.4
  })
  const rubber = new THREE.MeshStandardMaterial({ color: 0x241d33, roughness: 0.75 })

  // --- control unit -------------------------------------------------
  const unit = new THREE.Group()
  unit.position.set(0, 0.56, 0)

  const body = new THREE.Mesh(new RoundedBoxGeometry(1.9, 1.12, 0.5, 5, 0.14), shell)
  unit.add(body)

  // Recessed dark deck the controls sit on.
  const deck = new THREE.Mesh(new RoundedBoxGeometry(1.72, 0.94, 0.06, 4, 0.06), deep)
  deck.position.z = 0.24
  unit.add(deck)

  const screen = new THREE.Mesh(new RoundedBoxGeometry(0.86, 0.5, 0.04, 4, 0.03), glass)
  screen.position.set(-0.36, 0.14, 0.29)
  unit.add(screen)

  const dial = new THREE.Mesh(new THREE.CylinderGeometry(0.21, 0.21, 0.11, 48), metal)
  dial.rotation.x = Math.PI / 2
  dial.position.set(0.5, 0.14, 0.3)
  unit.add(dial)

  const dialCap = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.13, 24), glass)
  dialCap.rotation.x = Math.PI / 2
  dialCap.position.set(0.5, 0.14, 0.31)
  unit.add(dialCap)

  const keyGeo = new RoundedBoxGeometry(0.26, 0.14, 0.05, 3, 0.04)
  for (let i = 0; i < 4; i++) {
    const key = new THREE.Mesh(keyGeo, i === 1 ? new THREE.MeshStandardMaterial({
      color: ACCENT, roughness: 0.4, emissive: new THREE.Color(ACCENT).multiplyScalar(0.35)
    }) : rubber)
    key.position.set(-0.63 + i * 0.32, -0.29, 0.29)
    unit.add(key)
  }

  // Air ports + charging inlet on the back shell.
  const portGeo = new THREE.CylinderGeometry(0.075, 0.075, 0.12, 20)
  for (let i = 0; i < 4; i++) {
    const port = new THREE.Mesh(portGeo, metal)
    port.rotation.x = Math.PI / 2
    port.position.set(-0.51 + i * 0.34, -0.02, -0.27)
    unit.add(port)
  }
  const inlet = new THREE.Mesh(new RoundedBoxGeometry(0.22, 0.1, 0.08, 2, 0.03), deep)
  inlet.position.set(0.66, -0.36, -0.26)
  unit.add(inlet)

  // Recessed carry handle: a flattened torus half-sunk into the back.
  const handle = new THREE.Mesh(new THREE.TorusGeometry(0.3, 0.05, 12, 40, Math.PI), deep)
  handle.rotation.set(Math.PI / 2, 0, 0)
  handle.position.set(0, 0.5, -0.12)
  unit.add(handle)

  g.add(unit)

  // --- leg sleeves --------------------------------------------------
  // One lathe profile carries the whole sleeve: a closed foot, the ankle-to-
  // thigh taper, and a closed top. Starting and ending on the axis is what
  // caps it — an open profile lathes into a hollow bucket.
  const SLEEVE_H = 2.4
  /** Radius of the sleeve at height fraction t, shared with the seam rings. */
  const sleeveR = (t: number) => 0.22 + 0.16 * t + 0.018 * Math.sin(t * Math.PI * 3)

  const profile: THREE.Vector2[] = [new THREE.Vector2(0, 0), new THREE.Vector2(sleeveR(0) - 0.05, 0)]
  for (let i = 0; i <= 14; i++) {
    const t = i / 14
    profile.push(new THREE.Vector2(sleeveR(t), 0.04 + t * (SLEEVE_H - 0.04)))
  }
  profile.push(new THREE.Vector2(sleeveR(1) - 0.06, SLEEVE_H + 0.05))
  profile.push(new THREE.Vector2(0, SLEEVE_H + 0.07))
  const sleeveGeo = new THREE.LatheGeometry(profile, 56)

  for (const side of [-1, 1]) {
    const sleeve = new THREE.Group()
    sleeve.position.set(side * 1.24, 0, -0.15)
    sleeve.rotation.y = side * 0.18

    const shellMesh = new THREE.Mesh(sleeveGeo, fabric)
    sleeve.add(shellMesh)

    // Four welded chamber seams, one per compression zone.
    for (let i = 0; i < 5; i++) {
      const t = 0.12 + i * 0.19
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(sleeveR(t) + 0.008, 0.026, 10, 48),
        rubber
      )
      ring.rotation.x = Math.PI / 2
      ring.position.y = 0.04 + t * (SLEEVE_H - 0.04)
      sleeve.add(ring)
    }

    // The zip runs the length of the sleeve on the outside face.
    const zip = new THREE.Mesh(new THREE.BoxGeometry(0.035, SLEEVE_H * 0.92, 0.035), rubber)
    zip.position.set(side * (sleeveR(0.5) - 0.01), SLEEVE_H * 0.5, 0.06)
    sleeve.add(zip)

    const cuff = new THREE.Mesh(
      new THREE.CylinderGeometry(sleeveR(1) + 0.012, sleeveR(1) + 0.012, 0.09, 48),
      rubber
    )
    cuff.position.y = SLEEVE_H - 0.06
    sleeve.add(cuff)

    const coupler = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 0.16, 20), metal)
    coupler.rotation.z = Math.PI / 2
    coupler.position.set(side * -(sleeveR(0.78) + 0.05), SLEEVE_H * 0.78, 0)
    sleeve.add(coupler)

    g.add(sleeve)

    // --- hose from the unit's port to that coupler ---
    const couplerWorld = new THREE.Vector3(
      side * 1.24 - side * (sleeveR(0.78) + 0.05),
      SLEEVE_H * 0.78,
      -0.15
    )
    const hose = new THREE.CatmullRomCurve3([
      new THREE.Vector3(side * 0.45, 0.54, -0.28),
      new THREE.Vector3(side * 0.78, 0.22, -0.5),
      new THREE.Vector3(side * 1.05, 0.95, -0.35),
      couplerWorld
    ])
    g.add(new THREE.Mesh(new THREE.TubeGeometry(hose, 60, 0.055, 12, false), rubber))
  }

  return g
}
