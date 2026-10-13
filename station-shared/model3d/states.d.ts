// states.js 的类型声明，给 station-site（TS）用。实现和注释见 states.js。
import type { Object3D } from 'three'
import type { MarkLogo, Marks, MarksHandle } from './marks.js'
import type { ModelState } from './stateList.js'

export type { ModelState }
export { parseStates } from './stateList.js'


export type StateStatus = 'loading' | 'ready' | 'failed'

/** 切换器收的状态项：模型状态，或者规格（id = c:<编码>，可带自己的标注） */
export type SwitchState = ModelState & { marks?: Marks | null; projectFrom?: string }

export interface StateSwitcher {
  setStates(states: SwitchState[] | null): void
  setLogos(logos: MarkLogo[] | null): void
  show(id: string): Promise<void>
  preload(ids?: string[]): Promise<void>
  readonly current: string | null
  isMain(): boolean
  /** 当前显示的非主模型上的标注句柄；主模型时 null */
  currentMarks(): MarksHandle | null
  tick(dtMs: number): void
  dispose(): void
}

export function alignTo(main: Object3D, wrapper: Object3D): void
export function projectLogos(logos: MarkLogo[], mainScene: Object3D, stateScene: Object3D): MarkLogo[]
export function createStateSwitcher(opts: {
  mainScene: Object3D
  marksOpts?: { accent?: string; reducedMotion?: boolean; showPowerRing?: boolean }
  onStatus?: (id: string, status: StateStatus) => void
  onShown?: (id: string) => void
  load?: (url: string) => Promise<Object3D>
}): StateSwitcher
