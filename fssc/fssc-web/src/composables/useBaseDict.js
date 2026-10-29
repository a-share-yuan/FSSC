// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 基础数据下拉的「字典 + i18n」合并层
 *
 * ## 为什么需要这一层（不要直接用 useDict 的 options 当 label）
 *
 * 系统里绝大多数下拉原本是裸写死的：
 *   <el-option :label="$t('expense.preapply.optPlane')" value="AIR" />
 * 这里的 label 走 vue-i18n，中英双语都有（zh-CN.js / en-US.js 各一份）。
 *
 * 如果改造时直接换成 `dict.options`：
 *   <el-option v-for="o in dict.options" :label="o.label" ... />
 * 那么 label 就变成数据库里的中文 —— 切到英文界面时「交通方式」下拉里
 * 会显示"飞机/高铁"而不是 "Plane/High-speed rail"，是用户可感知的功能倒退。
 *
 * 所以定下这条规则（本次改造全部按它执行）：
 *   字典定「值域」(value) + i18n 定「文案」(label)
 *   - value 以字典为准：后台可增删选项，这是办结的目的；
 *   - label 优先走 i18n：保住双语；
 *   - i18n 缺 key（后台新增了选项但没补语言包）⇒ 回落字典 label（中文），
 *     宁可显示中文也不要显示一串裸码值 AIR/HIGH_SPEED_RAIL。
 *
 * ## 与 useDict 的关系
 *
 * 本层建立在 useDict 之上，复用它的全部 fail-soft 语义：
 *   字典未到 / 401 / 超时 / 后端未部署 ⇒ options 原样等于本地兜底常量，界面零变化。
 * 本层只多做一件事：把 label 换算成 i18n 结果。
 *
 * ## 用法
 *
 *   import { useBaseDict } from '@/composables/useBaseDict'
 *   const { options: trafficOptions, load } = useBaseDict('traffic_mode', TRAFFIC_LOCAL, TRAFFIC_I18N)
 *   onMounted(load)
 *
 *   <el-option v-for="o in trafficOptions" :key="o.value" :label="o.label" :value="o.value" />
 *
 * @param {string} dictType sys_dict_type.dict_type
 * @param {object} localFallback 本地兜底常量 { VALUE: '兜底中文', ... }（字典不可用时用它）
 * @param {object} i18nKeys 每个 value 对应的 i18n key { VALUE: 'expense.preapply.optPlane' }
 */
import { computed, unref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDict } from './useDict'

export function useBaseDict(dictType, localFallback = {}, i18nKeys = {}) {
  const { t, te } = useI18n()
  const dict = useDict(dictType, localFallback)

  /**
   * 合成后的下拉选项：
   *   1. 取值域 —— 字典优先，未到则本地兜底（由 useDict.options 保证同步有值）
   *   2. 取文案 —— i18n key 存在且能翻译 ⇒ 用译文；否则用字典 label；再否则用本地兜底中文
   */
  const options = computed(() => {
    const raw = unref(dict.options) || []
    return raw.map((o) => {
      const key = i18nKeys[o.value]
      let label = o.label
      if (key) {
        // te() 判断该 key 在当前语言包是否存在，避免 i18n 回退成 "expense.xxx.optPlane" 这种裸路径
        if (te(key)) label = t(key)
      }
      return { value: o.value, label, cssClass: o.cssClass || '' }
    })
  })

  /**
   * 反查文案（列表/详情页回显用）。找不到时返回兜底文本，绝不返回 undefined。
   */
  function labelOf(value, fallbackText = '') {
    const hit = options.value.find((o) => o.value === value)
    return hit && hit.label ? hit.label : fallbackText
  }

  return { options, labelOf, load: dict.load, loaded: dict.loaded, failed: dict.failed }
}

/* ------------------------------------------------------------------ *
 * 共享的「值 → 中文兜底 / 值 → i18n key」映射。
 *
 * 为什么集中放这里而不是每个页面各写一份：
 *   同一维度在 8 个页面被引用（交通方式 3 处文件、付款方式 5 处文件），
 *   各写一份必然漂移 —— 加一个选项要改 8 次，漏一处就出现"两个页面选项不一致"。
 *  办结到基础数据字典的目的正是消灭这种漂移。
 *
 * 值必须与 sys_dict_data.dict_value 逐字一致；
 *    字典缺失时兜底到这里的常量，行为与原先逐字相同（零风险）。

/** 交通方式（traffic_mode） */
export const TRAFFIC_LOCAL = {
  AIR: '飞机', RAIL: '高铁', TRAIN: '火车', CAR: '汽车', SHIP: '轮船'
}
export const TRAFFIC_I18N = {
  AIR: 'expense.preapply.optPlane',
  RAIL: 'expense.preapply.optHighSpeedRail',
  TRAIN: 'expense.preapply.optTrain',
  CAR: 'expense.preapply.optCar',
  SHIP: 'expense.preapply.optShip'
}

/** 舱位席别（seat_class） */
export const SEAT_LOCAL = {
  ECONOMY: '经济舱', BUSINESS: '商务舱', FIRST: '头等舱', SECOND: '二等座', FIRST_SEAT: '一等座'
}
export const SEAT_I18N = {
  ECONOMY: 'expense.preapply.optEconomyClass',
  BUSINESS: 'expense.preapply.optBusinessClass',
  FIRST: 'expense.preapply.optFirstClass',
  SECOND: 'expense.preapply.optSecondClassSeat',
  FIRST_SEAT: 'expense.preapply.optFirstClassSeat'
}

/** 培训类型（training_type） */
export const TRAINING_LOCAL = {
  INTERNAL: '内训', EXTERNAL: '外训', PUBLIC: '公开课'
}
export const TRAINING_I18N = {
  INTERNAL: 'expense.preapply.optInternalTraining',
  EXTERNAL: 'expense.preapply.optExternalTraining',
  PUBLIC: 'expense.preapply.optPublicCourse'
}

/**
 * 付款方式（pay_method）。
 * 各页面 i18n 前缀不同（expense.claim.* / pay.corp.*），故映射按页面区分，见下方各常量。
 */
export const PAY_METHOD_LOCAL = {
  BANK: '银行转账', CASH: '现金', CHECK: '支票', TELEGRAPHIC: '电汇', DRAFT: '承兑汇票'
}
export const PAY_METHOD_I18N_CLAIM = {
  BANK: 'expense.claim.optBankTransfer',
  CASH: 'expense.claim.optCash',
  CHECK: 'expense.claim.optCheque'
}
export const PAY_METHOD_I18N_CORP = {
  BANK: 'pay.corp.optBankTransfer',
  CASH: 'pay.corp.optCash',
  CHECK: 'expense.claim.optCheque',
  TELEGRAPHIC: 'pay.corp.optTelegraphic',
  DRAFT: 'pay.corp.optAcceptanceDraft'
}

/**
 * 收款方类型（enum_payee_type）—— 库内字典早已存在且值域与前端写死逐字一致
 * （EMPLOYEE/SUPPLIER/CUSTOMER），故无需新建字典，直接办结到既有字典即可。
 *
 * i18n key 选 `field.*` 而非各页面的 `colEmployee`/`optEmployee`：
 *   field.supplier / field.customer 是全局共用 key，而 colEmployee 在
 *   expense.claim 与 pay.corp 下重复定义（两份都译成"员工"），选 field.*
 *   可让五个页面共用同一份映射，不受页面 i18n 命名空间差异影响。
 */
export const PAYEE_TYPE_LOCAL = {
  EMPLOYEE: '员工', SUPPLIER: '供应商', CUSTOMER: '客户'
}
export const PAYEE_TYPE_I18N = {
  EMPLOYEE: 'expense.claim.colEmployee',
  SUPPLIER: 'field.supplier',
  CUSTOMER: 'field.customer'
}
