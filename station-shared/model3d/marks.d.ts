// marks.js 的类型声明，给 station-site（TS）用。实现和注释见 marks.js。
import type { Group, Mesh, Object3D, Quaternion, Raycaster, Intersection } from 'three'

export type Vec3 = [number, number, number]

export interface MarkLogo {
  id: string
  src: string
  p: Vec3
  n: Vec3
  size: number
  rot: number
  opacity: number
}

export interface MarkScreen {
  p: Vec3
  n: Vec3
  w: number
  h: number
  rot: number
  bootSrc?: string
  bootBg: string
  bootMs: number
  runSrc?: string
}

export interface MarkPower {
  p: Vec3
  n: Vec3
  r: number
}

export interface Marks {
  v: 1
  model?: string
  logos?: MarkLogo[]
  screen?: MarkScreen
  power?: MarkPower
}

export interface MarksHandle {
  group: Group
  meshes: Mesh[]
  radius: number
  setMarks(marks: Marks | null): void
  setPower(on: boolean, opts?: { instant?: boolean }): void
  isOn(): boolean
  hitPower(raycaster: Raycaster): boolean
  setStatic(mode: 'boot' | 'run' | null): void
  tick(dtMs: number): void
  dispose(): void
}

export const LIMITS: {
  version: 1
  maxLogos: number
  maxCoord: number
  maxBootMs: number
  defaultBootMs: number
  defaultBootBg: string
}

export function parseMarks(value: unknown): Marks | null
/** 贴图 / .glb 用的地址：按当前域名分开浏览器缓存（COS 不回 Vary: Origin），见 marks.js */
export function corsUrl(url: string): string
export function orientation(n: Vec3 | number[], rotDeg?: number): Quaternion
export function localRadius(root: Object3D): number
export function hitToLocal(root: Object3D, hit: Intersection): { p: Vec3; n: Vec3 }
export function createMarks(
  root: Object3D,
  marks: Marks | null,
  opts?: { accent?: string; reducedMotion?: boolean; showPowerRing?: boolean }
): MarksHandle
