import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'

const files = process.argv.slice(2)
let bad = 0
for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const name = f.split(/[\\/]/).pop()
  const { descriptor, errors } = parse(src, { filename: f })
  if (errors && errors.length) {
    console.log('[PARSE-FAIL] ' + name)
    errors.forEach((e) => console.log('   ', e.message))
    bad++
    continue
  }
  try {
    compileScript(descriptor, { id: 'c' })
    console.log('[OK] ' + name)
  } catch (e) {
    console.log('[SCRIPT-FAIL] ' + name + ': ' + e.message)
    bad++
  }
}
console.log(bad === 0 ? 'ALL SFC OK' : 'FAILED ' + bad)
process.exit(bad === 0 ? 0 : 1)
