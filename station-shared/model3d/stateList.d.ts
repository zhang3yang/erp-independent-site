// stateList.js 的类型声明。实现和注释见 stateList.js。

/** 接口下发的一项：第一个是主模型（没有 model），其余各一个 glb */
export interface ModelState {
  id: string
  name: string
  model?: string
}

export function parseStates(value: unknown): ModelState[] | null
