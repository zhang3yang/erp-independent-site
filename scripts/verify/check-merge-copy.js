// MergeCheck.java 里那两段（overlay、compose 那一组）是从 SiteOpenService.java 拷来的。
// 拷贝会过期，所以每次跑真值表之前先比一遍。
//
// 比的是**去掉注释之后的代码**：注释照抄一遍只会让真值表那个文件里堆着两百行
// javadoc，而且以后改一个字就要两边一起改，不改也没人发现——逻辑一致才是这里要守的。
const fs = require('fs')

// ⚠ 统一成 LF 再比。下面 overlay 那个起始标记里带着一个 \n（真源码那边没有
// ======== 标记可用，只能从方法签名那一行比起），而 Windows 上检出的工作副本是 CRLF，
// 于是那个标记永远找不到——表现是「overlay：没找到这一段」，而两份其实一模一样。
// 这里比的是逻辑，行尾本来就不该参与。
const real = fs.readFileSync(process.argv[2], 'utf8').replace(/\r\n/g, '\n')
const copy = fs.readFileSync(process.argv[3], 'utf8').replace(/\r\n/g, '\n')

/** 取 startMark 那一行之后、endMark 之前的那一段 */
function grab(text, startMark, endMark) {
  const a = text.indexOf(startMark)
  if (a < 0) return null
  const from = text.indexOf('\n', a)
  const b = text.indexOf(endMark, from < 0 ? a : from)
  return b < 0 ? null : text.slice(from, b)
}

const norm = s => s
  .replace(/\/\*[\s\S]*?\*\//g, '')   // 块注释和 javadoc
  .replace(/\/\/.*$/gm, '')           // 行注释（这两段里没有含「//」的字符串字面量）
  .replace(/\bstatic\s+/g, '')        // 拷贝那份是 static 的，真源码是实例方法
  .split('\n').map(l => l.trim()).filter(Boolean).join('\n')

/**
 * 要比的两段。
 *
 * overlay 在真源码里没有起始标记，从方法签名比到下一段 javadoc；
 * compose 那一组有好几个方法，两边都用 ======== 标记圈出来。
 */
const BLOCKS = [
  {
    name: 'overlay',
    real: () => grab(real, 'private void overlay(Map<String, Object> merged, Object layer)\n', '\n    /**'),
    copy: () => grab(copy, 'private static void overlay', '// ======== 拷贝结束')
  },
  {
    name: 'compose / joinRows / joinNested / joinScalar',
    real: () => grab(real, '======== 以下到「拼接结束」为止', '// ======== 拼接结束'),
    copy: () => grab(copy, '======== 以下到「拼接结束」为止', '// ======== 拼接结束')
  }
]

let failed = false
for (const block of BLOCKS) {
  const a = block.real()
  const b = block.copy()
  if (!a || !b) {
    console.log(`⚠ ${block.name}：没找到这一段，检查标记`)
    failed = true
    continue
  }
  // 两边都是从方法签名那一行之后截的，比的是方法体
  const left = norm(a)
  const right = norm(b)
  if (left === right) {
    console.log(`${block.name} 拷贝与真源码一致`)
  } else {
    console.log(`⚠ ${block.name} 的拷贝已经和真源码不一样了，真值表测的不是线上那份逻辑`)
    console.log('--- 真源码 ---\n' + left)
    console.log('--- 拷贝 ---\n' + right)
    failed = true
  }
}

if (failed) process.exit(1)
