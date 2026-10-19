/**
 * 币种下拉的数据源收口（2026-10-18，B 级）
 *
 * ## 背景
 *
 * 币种此前在 12 个页面里各写一份 <el-option value="CNY">人民币</el-option>，
 * 有的还只写 CNY/USD 两项（实际 md_currency 有 35 种）。
 * 后果：① 加币种要改 12 处，漏一处就出现"这个页面选不到那个币种"；
 *       ② 后端 md_currency 明明是主数据，界面却读不到，属双源脱节。
 *
 * ## 权限现实（改造过程中的关键转折，务必知道）
 *
 * 最初直接调 `masterApi.currency.page()` 实测失败：该端点带权限码 `md:currency:list`，
 * 只授予 CONFIG_ADMIN/IT_ADMIN，而 COMMON_EMP 154 人、SCAN_STAFF 5 人全部 4030。
 * 币种下拉散落在 budget/contract/expense/invoice/pay/purchase/receivable/treasury
 * 共 12 个页面 40 处，是**业务录入必填项**——要求普通员工先授"币种管理权"才能填单，
 * 权限模型不成立。
 *
 * 故后端于 2026-10-18 新增**免权限只读通道** `/tenant/masterdata/currency/options`
 * （三道闸门同 builtin 字典通道：仍需登录 / 仅 status=NORMAL 且未删 / 只读且字段最小化），
 * 本层改调该端点 ⇒ 普通员工也能读到全部启用币种，币种真正成为全员可维护的基础数据。
 *
 * 仍保留本地兜底：通道未部署/网络异常时回落内置清单，绝不让下拉变空。
 *
 * ## 用法
 *
 *   import { useCurrency } from '@/composables/useCurrency'
 *   // 纯主数据口径（label 用 currencyName）
 *   const { options: currencyOptions, load, formatLabel } = useCurrency()
 *   // 保留双语口径（原 label 走 i18n 的页面用这个）
 *   const { options: c, load, formatLabel: cur } = useCurrency({
 *     i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }
 *   })
 *   onMounted(load)
 *
 *   <el-option v-for="o in currencyOptions" :key="o.value" :label="cur(o.value)" :value="o.value" />
 *
 * ## label 格式
 *
 * i18n 命中的币种用语言包原文；未命中的（后台新增币种）用 `currencyName`
 * 按 format 拼接，共三种：'name'=人民币元 / 'code-name'=CNY 人民币 / 'name-code'=人民币 CNY，
 * 以保证各页面原有展示格式不变。
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { masterApi } from '@/api.js'

/**
 * 内置币种清单（兜底用）。
 * 取自 md_currency 启用项中最常用的核心币种，label 与库内 currencyName 逐字一致，
 * 保证"通道不可用时界面与正常态相同"。已剔除 2026-10-18 勘误停用的
 * FRF（法国法郎，已被 EUR 取代）/ RM（与 MYR 重复）等。
 */
const BUILTIN = {
  CNY: '人民币元',
  USD: '美元',
  EUR: '欧元',
  JPY: '日元',
  HKD: '港币',
  SGD: '新加坡元',
  GBP: '英镑'
}

/** 全局共享一份，多个组件同时用也只发一次请求 */
const store = ref([])
const inflight = ref(null)
const loaded = ref(false)
const lastError = ref(null)

function toOptions(rows) {
  return (rows || [])
    .filter((r) => r && (r.del_flag ?? r.delFlag) !== 1)
    .map((r) => ({
      value: r.currency_code ?? r.currencyCode,
      label: r.currency_name ?? r.currencyName
    }))
    .filter((o) => o.value && o.label)
}

/** 同步、永不返回空：未加载时用内置清单 */
const builtinOptions = Object.keys(BUILTIN).map((k) => ({ value: k, label: BUILTIN[k] }))

async function fetchAll() {
  try {
    // 免权限只读通道：仅需登录，返回启用且未删的币种展示字段
    const res = await masterApi.currency.options()
    const data = res?.data ?? res
    const list = Array.isArray(data) ? data : (data?.list ?? [])
    return toOptions(list)
  } catch (e) {
    lastError.value = e?.message || 'error'
    return []
  }
}

/**
 * 格式化 label。
 *
 * ## 为什么需要 i18n 版本（2026-10-18 实施时发现）
 *
 * 原写死下拉的 label **走 vue-i18n 双语**，而各页面中英格式并不统一：
 *   expense.common.curCny : zh='人民币 CNY'  en='CNY'
 *   pay.corp.optCny        : zh='人民币 CNY'  en='CNY (RMB)'
 *   pay.corp.optUsd        : zh='美元 USD'    en='USD (US Dollar)'
 * 如果直接用主数据的 currencyName（纯中文名），切英文时币种下拉会全变中文 —— 功能倒退。
 *
 * 故口径与 useBaseDict 一致：**主数据定值域 + i18n 定文案**。
 * i18n 映射只覆盖原来就有的那几个币种（后台新增的币种没有 key），
 * 无 key 时回落到主数据名称 + 目标格式，绝不显示裸 code。
 *
 * @param {object} opts
 *   opts.i18nKeys  { CNY: 'expense.common.curCny', ... } 该页面的 i18n 映射
 *   opts.format    无 i18n key 时使用的格式：'name' | 'code-name' | 'name-code'
 */
export function useCurrency(opts = {}) {
  const i18nKeys = opts.i18nKeys || {}
  const fallbackFormat = opts.format || 'name'
  let t = null
  let te = null
  try {
    const i18n = useI18n()
    t = i18n.t
    te = i18n.te
  } catch (e) {
    // 非 setup 环境（如纯 JS 模块）调用时降级为纯文本模式
  }

  const options = computed(() => (store.value.length ? store.value : builtinOptions))

  /** 主数据纯名称 */
  function nameOf(value) {
    const hit = options.value.find((o) => o.value === value)
    return hit && hit.label ? hit.label : ''
  }

  const FORMATS = ['name', 'code-name', 'name-code']

  /**
   * 主数据纯名称。
   *
   * ## 为什么要这样写（2026-10-18 收尾加固）
   *
   * 本文件历史上同时存在 `labelOf(value, fallbackText)` 与 `formatLabel(value, format)`，
   * **两者第二参数语义完全不同却同名**。改造中 12 个页面里有 6 个误写成
   * `labelOf: currencyLabel`，模板又照习惯传了 `currencyLabel(o.value, 'name-code')` ——
   * 于是格式被静默丢弃、所有 label 退化成纯名称。
   * 这类退化**能编译、测试也过、构建零告警**，只有肉眼比对 label 才发现（坑 4a）。
   *
   * 所以现在让 `labelOf` 自证：第二参数若是合法格式名，说明调用方想控制格式
   * ⇒ 直接转 `formatLabel`，并在 dev 环境告警。误用从"静默退化"变成"立刻出声"。
   */
  function labelOf(value, arg2 = '') {
    if (FORMATS.includes(arg2)) {
      if (import.meta.env && import.meta.env.DEV) {
        console.warn(
          '[useCurrency] labelOf 收到格式参数 ' + JSON.stringify(arg2) +
          '；该函数第二参数本应是兜底文本。已自动按 formatLabel 处理。' +
          '建议解构改为 { formatLabel: currencyLabel }，避免同名不同义。'
        )
      }
      return formatLabel(value, arg2)
    }
    return nameOf(value) || arg2
  }

  /**
   * 按页面原有展示格式输出 label：i18n 优先，无 key 时用主数据名 + 格式拼接。
   * @param {string} value  币种代码
   * @param {'name'|'code-name'|'name-code'} [formatOverride] 覆盖默认格式
   */
  function formatLabel(value, formatOverride) {
    const fmt = formatOverride || fallbackFormat
    if (!value) return ''
    const key = i18nKeys[value]
    // te() 判定避免把 i18n 未注册的 key 渲染成裸路径字符串
    if (key && t && te && te(key)) return t(key)
    const name = nameOf(value)
    if (!name) return value
    if (fmt === 'code-name') return `${value} ${name}`
    if (fmt === 'name-code') return `${name} ${value}`
    return name
  }

  async function load(force = false) {
    if (loaded.value && !force) return store.value
    if (inflight.value) return inflight.value
    inflight.value = fetchAll().then((rows) => {
      // 空结果视为"拿不到"（403 / 权限不足 / 后端未部署）⇒ 保持内置清单，绝不让下拉变空
      if (rows.length) {
        store.value = rows
        loaded.value = true
      }
      return rows
    }).finally(() => { inflight.value = null })
    return inflight.value
  }

  return { options, labelOf, formatLabel, load, loaded, lastError }
}
