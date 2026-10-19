/**
 * 字典读取层（第 1 批收口基础设施，2026-10-15；第 3 批扩展：免权限通道 + 全局预热）
 *
 * 目标：让前端枚举可切到数据库字典，同时**保证接口不可用时行为与改造前逐字一致**。
 *
 * 为什么必须有这一层（这是切字典的核心风险）：
 *   本地常量（enums.js/billStatus.js）是**编译期常量**，同步可得、永不失败；
 *   字典是**运行时远程数据**，异步、可能超时/401/网络不通/后端未部署。
 *   若直接改成 `await dict` 渲染，接口一抖状态列就整列空白 —— 比不收口更糟。
 *   故口径固定为：
 *     · 同步返回，永不阻塞渲染（首帧用本地常量，字典回来后原位刷新）；
 *     · 加载中/失败/未配置 ⇒ **一律回落到本地常量**，界面零变化；
 *     · 字典与本地常量冲突时**以字典为准**（字典是运营可维护的权威源），
 *       但缺键时回落本地常量，不把英文码放上台面。
 *
 * 用法（Vue3 setup）：
 *   const dict = useDict('wf_node_multi_type', MULTI_TYPE)   // 传入本地常量作兜底
 *   dict.label(v)          // 取标签（同步，永不返回 undefined）
 *   dict.load()            // 挂载时调一次（或用 onMounted(dict.load)）
 *   dict.options           // 下拉用 [{value,label,cssClass}]，字典未到时来自本地常量
 *
 * 【2026-10-15 前置A 双通道设计】
 *   system:dict:list 只授予 CONFIG_ADMIN/IT_ADMIN，业务角色读不到 ⇒ 新增免权限端点
 *   `/dict/builtin/data/list`（仅暴露 is_builtin=1）。本层读取顺序：
 *     ① 免权限内置通道（人人可读）
 *     ② 失败/为空 ⇒ 回退带权限通道（管理员可用，兼容"端点尚未部署"的过渡期）
 *   这样后端未重启时 404 ⇒ 自动走 ②，行为与改造前完全一致；重启后自动切到 ①。
 *
 *  本层是**纯增量**：不修改 enums.js / billStatus.js 的任何既有导出，
 *    消费方按需引入即可，不引用的地方行为完全不变。
 */
import { ref, computed } from 'vue'
import { dictApi } from '@/api.js'

/** dict_type → 本地兜底常量的全局缓存（同一 dict_type 只请求一次） */
const store = ref({})
const inflight = {}
const loaded = {}
const failed = {}

/**
 * 把字典数据行转成 {value,label,cssClass} 形态。
 *
 *  **字段名必须兼容驼峰与下划线两种形态**（2026-10-15 实测踩过）：
 *   后端 `/dict/data/list` 返回的是 **Jackson 默认驼峰**（dictValue/dictLabel/cssClass/delFlag），
 *   而库里列名是下划线（dict_value/dict_label/css_class/del_flag）。
 *   只按下划线取值 ⇒ 全部读成 undefined ⇒ 字典项全空 ⇒ 界面比不收口更糟。
 */
function toOptions(rows) {
  return (rows || [])
    .filter((r) => r && r.del_flag !== 1 && r.delFlag !== 1)
    .map((r) => {
      const value = r.dict_value ?? r.dictValue
      const label = r.dict_label ?? r.dictLabel
      const css = r.css_class ?? r.cssClass ?? ''
      return { value, label, cssClass: css }
    })
    .filter((o) => o.value != null && o.value !== '')
}

/** 把本地常量对象转成同形态，供字典未到时使用 */
function constToOptions(map) {
  return Object.keys(map || {}).map((k) => ({ value: k, label: map[k], cssClass: '' }))
}

/** 解包后端 Result：可能是 {code,data:[...]}，也可能拦截器已剥成裸数组 */
function unwrap(res) {
  if (!res) return []
  if (Array.isArray(res)) return res
  const d = res.data ?? res
  return Array.isArray(d) ? d : []
}

/**
 * 双通道拉取：免权限内置通道优先，失败/为空回退带权限通道。
 * 任一通道成功即返回；两通道都失败返回空数组（调用方回落本地常量）。
 */
async function fetchDict(dictType) {
  try {
    const pub = unwrap(await dictApi.builtinDataList(dictType))
    if (pub.length) {
      failed[dictType] = null
      return pub
    }
    // 免权限通道返回空：可能是"端点未部署(404)"或"该类型非内置" ⇒ 再试带权限通道。
    // 业务角色在这里会 403，属预期（fail-soft），不向上抛。
    const authed = unwrap(await dictApi.dataList(dictType))
    if (authed.length) {
      failed[dictType] = null
      return authed
    }
    failed[dictType] = 'empty'
    return []
  } catch (e) {
    // 第一通道抛错（未部署/网络）⇒ 静默试第二通道；两通道皆败才记失败
    try {
      const authed = unwrap(await dictApi.dataList(dictType))
      if (authed.length) {
        failed[dictType] = null
        return authed
      }
    } catch (_) { /* 忽略：两通道都不可用，回落本地常量 */ }
    failed[dictType] = e && e.message ? e.message : 'error'
    if (import.meta.env.DEV) {
      console.warn(`[useDict] 字典「${dictType}」两条通道都读取失败，已回落到本地常量：`, e)
    }
    return []
  }
}

/**
 * @param {string} dictType 字典类型，如 'wf_node_multi_type'
 * @param {object} fallback 本地兜底常量（必填 —— 它是"零风险"的保障）
 */
export function useDict(dictType, fallback) {
  const fbOptions = constToOptions(fallback)

  // 字典已到 ⇒ 用字典；未到/失败 ⇒ 用本地常量。
  // 注意 computed 依赖 store.value 整体，字典回来会自动触发重算。
  const options = computed(() => {
    const rows = store.value[dictType]
    if (rows && rows.length) return toOptions(rows)
    return fbOptions
  })

  function label(value, fallbackText = '') {
    if (value == null || value === '') return fallbackText
    const hit = options.value.find((o) => o.value === value)
    if (hit && hit.label) return hit.label
    // 字典与本地常量都没命中：原样返回（不吞值），与 enumLabel 行为一致
    return String(value)
  }

  function cssClass(value) {
    const hit = options.value.find((o) => o.value === value)
    return hit ? hit.cssClass : ''
  }

  async function load(force = false) {
    if (loaded[dictType] && !force) return store.value[dictType] || []
    if (inflight[dictType]) return inflight[dictType]
    inflight[dictType] = fetchDict(dictType)
      .then((rows) => {
        // 空数组视为"该字典未配置" ⇒ 保持本地兜底，不让下拉变空
        if (rows.length) {
          store.value = { ...store.value, [dictType]: rows }
          loaded[dictType] = true
        }
        return rows
      })
      .finally(() => {
        delete inflight[dictType]
      })
    return inflight[dictType]
  }

  return { options, label, cssClass, load, loaded, failed }
}

/**
 * 组件内一次性加载多个字典（省重复请求）。
 * 用法：const { ready } = useDicts({ wf_node_multi_type: MULTI_TYPE, ... }); onMounted(ready)
 */
export function useDicts(map) {
  const entries = Object.entries(map || {})
  const handles = entries.map(([t, fb]) => useDict(t, fb))
  const ready = () => Promise.all(handles.map((h) => h.load()))
  return { ready, ...Object.fromEntries(entries.map(([t], i) => [t, handles[i]])) }
}

/** 供非组件场景（工具函数内）同步取值：未加载时返回 null 表示"走本地常量" */
export function dictLoaded(dictType) {
  return !!loaded[dictType]
}

/**
 * 【2026-10-15】全局预热：应用启动/登录后一次性把高频内置字典拉到内存。
 *
 * 为什么需要它：`billStatus.js` 的 `statusLabel()` 是**纯同步函数**，
 * 被 55 个业务文件直接调用，没有组件生命周期可挂 `onMounted`。
 * 逐文件改造既不现实也无必要 —— 统一在入口预热一次即可让这些同步函数共享缓存。
 *
 * @param {string[]} types 要预热的 dict_type 列表
 * @returns {Promise<number>} 成功加载的字典个数（失败的不计入，但不抛错）
 */
export async function preloadBuiltinDicts(types) {
  // 已加载的类型直接跳过（本函数会被多处调用：挂载、路由变化、后端恢复），
  // 没有这个短路就会在每次路由跳转时重发一遍请求。
  const list = (types || []).filter((t) => t && !loaded[t])
  if (!list.length) return 0

  // ① 批量通道（1 次往返取回全部）—— 第 4 批后有 67 个展示字典，逐个请求会打 67 次往返。
  //    任何异常（端点未部署 404 / 网络 / 反序列化）都只是"批量没成"，静默回退 ②，
  //    结果与使用改造前的逐个拉取完全一致，界面零变化。
  try {
    const raw = await dictApi.builtinDataBatch(list)
    const map = (raw && typeof raw === 'object' && !Array.isArray(raw) && (raw.data ?? raw)) || {}
    let ok = 0
    for (const [t, rows] of Object.entries(map)) {
      if (Array.isArray(rows) && rows.length) {
        store.value = { ...store.value, [t]: rows }
        loaded[t] = true
        ok++
      }
    }
    // 批量没覆盖到的（该类型被闸门剔除 / 后端版本较旧）⇒ 逐个补拉
    const rest = list.filter((t) => !loaded[t])
    if (!rest.length) return ok
    return ok + await loadOneByOne(rest)
  } catch (e) {
    if (import.meta.env.DEV) {
      console.warn('[useDict] 批量内置字典通道不可用，回退逐个拉取：', e)
    }
    return await loadOneByOne(list)
  }
}

/**
 * 逐个拉取（批量通道不可用时的兜底，也是改造前的原有行为）。
 *
 *  必须**限并发**：第 4 批后待预热的有 67 个字典，Promise.all 一把梭就是 67 个并发请求，
 *    登录首屏直接把浏览器连接池和后端打满（表现为其它接口排队、首屏明显变慢）。
 *    分成每批 6 个串行推进，总耗时仍远小于一次页面加载，但不会挤占业务请求。
 */
const CONCURRENCY = 6
async function loadOneByOne(list) {
  let ok = 0
  for (let i = 0; i < list.length; i += CONCURRENCY) {
    const batch = list.slice(i, i + CONCURRENCY)
    const res = await Promise.all(
      batch.map((t) =>
        fetchDict(t).then((rows) => {
          if (rows.length) {
            store.value = { ...store.value, [t]: rows }
            loaded[t] = true
            return 1
          }
          return 0
        })
      )
    )
    ok += res.reduce((a, b) => a + b, 0)
  }
  return ok
}

/** 需要全局预热的内置字典（第 3 批：状态展示口径） */
export const PRELOAD_DICT_TYPES = ['bill_status_display']

/**
 * 同步取某个字典里某值的标签（供 billStatus.js 这类纯函数使用）。
 * 未加载/未命中 ⇒ 返回 null，由调用方回落到本地常量。
 *
 * @param {string} dictType
 * @param {string} value
 * @returns {string|null}
 */
export function dictLabelOf(dictType, value) {
  if (value == null || value === '') return null
  const rows = store.value[dictType]
  if (!rows || !rows.length) return null
  const hit = toOptions(rows).find((o) => o.value === value)
  return hit && hit.label ? hit.label : null
}

/**
 * 同步取某个字典里某值的 css_class（供 statusType 这类"取颜色"的纯函数使用）。
 * 未加载/未命中 ⇒ 返回 null，由调用方回落到本地常量。
 *
 * 字典的 css_class 存的是 el-tag 的 type（info/success/warning/danger/primary）。
 *
 * @param {string} dictType
 * @param {string} value
 * @returns {string|null}
 */
export function dictCssClassOf(dictType, value) {
  if (value == null || value === '') return null
  const rows = store.value[dictType]
  if (!rows || !rows.length) return null
  const hit = toOptions(rows).find((o) => o.value === value)
  return hit && hit.cssClass ? hit.cssClass : null
}
