// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useFormMeta - 表单元数据驱动 composable
 * 对齐差距①「单据/界面不是元数据驱动，新增一种单据＝改代码」。
 *
 * 原先：每个单据页面把表单项写死在 .vue 里 —— 加一个字段要改代码、并发版。
 * 改造后：字段的控件类型/布局/必录/锁定/默认值全部由后端元数据下发，
 *         前端按元数据渲染；新增字段 = 后台插一行数据。
 *
 * 后端接口
 *   GET /api/tenant/masterdata/form-meta/{formCode}/fields?roles=R1,R2
 *   GET /api/tenant/masterdata/form-meta/{formCode}/rules?scope=SAVE
 *   GET /api/tenant/masterdata/form-meta/{formCode}/bundle?roles=..&scope=..
 *   POST /api/tenant/masterdata/form-meta/{formCode}/validate?scope=SAVE
 *   POST /api/tenant/masterdata/form-meta/{formCode}/defaults
 *
 * 必录/锁定的权威规则在服务端（FormMetaService.validate），本 composable
 * 生成的 el-form rules 只是<b>体验层提示</b>，不替代服务端校验。
 */

import { ref, computed } from 'vue'
import request from '@/utils/request'

/** 控件类型 → Element Plus 组件映射 */
const WIDGET_MAP = {
  INPUT: 'el-input',
  INPUT_NUMBER: 'el-input-number',
  TEXTAREA: 'el-input',
  SELECT: 'el-select',
  DATE: 'el-date-picker',
  DATETIME: 'el-date-picker',
  MONEY: 'el-input-number',
  PERCENT: 'el-input-number',
  SWITCH: 'el-switch',
  UPLOAD: 'el-upload',
  AMOUNT_LIST: undefined,      // 行项目表：业务页面自行渲染
  USER_SELECT: 'el-select',
  DEPT_SELECT: 'el-tree-select',
  REF: 'el-select',
}

/** 宽度 → 栅格 span（24 栅格制） */
const WIDTH_SPAN = {
  FULL: 24,
  HALF: 12,
  THIRD: 8,
  QUARTER: 6,
}

export function useFormMeta(formCode) {
  /** 字段元数据 */
  const fields = ref([])
  /** 校验规则 */
  const rules = ref([])
  /** 加载中 */
  const loading = ref(false)
  /** 元数据缓存：formCode+roles → fields */
  const cache = {}

  /**
   * 取元数据。
   * @param {string} code 表单编码（缺省用 useFormMeta(formCode) 传入的）
   * @param {object} opts {roles, scope, force}
   */
  async function fetchMeta(code = formCode, opts = {}) {
    const { roles = '', scope = 'SAVE', force = false } = opts
    const ck = code + '|' + roles + '|' + scope
    if (!force && cache[ck]) {
      fields.value = cache[ck].fields
      rules.value = cache[ck].rules
      return { fields: fields.value, rules: rules.value }
    }
    loading.value = true
    try {
      const res = await request.get('/masterdata/form-meta/' + code + '/bundle', {
        params: { roles, scope },
      })
      const data = res?.data || {}
      cache[ck] = { fields: data.fields || [], rules: data.rules || [] }
      fields.value = cache[ck].fields
      rules.value = cache[ck].rules
      return { fields: fields.value, rules: rules.value }
    } finally {
      loading.value = false
    }
  }

  /** 页面加载时预热（避免首次提交才去后端取） */
  function preload(opts) {
    return fetchMeta(formCode, opts)
  }

  /** 控件组件名（取不到映射时兜底为 el-input） */
  function widgetOf(widgetType) {
    return WIDGET_MAP[widgetType] || 'el-input'
  }

  /** 是否为行项目表字段（交业务页面自行渲染） */
  function isDetailList(field) {
    return field.widgetType === 'AMOUNT_LIST'
  }

  /** 是否 textarea（决定是否设 :rows） */
  function isTextarea(field) {
    return field.widgetType === 'TEXTAREA'
  }

  /** 是否需要日期格式（value-format） */
  function isDateField(field) {
    return field.widgetType === 'DATE' || field.widgetType === 'DATETIME'
  }

  /** 字段栅格 span */
  function spanOf(field) {
    return field.span || WIDTH_SPAN[field.controlWidth] || 24
  }

  /**
   * 由字段元数据生成 el-form rules（体验层提示，非权威校验）。
   * 权威校验在服务端 FormMetaService.validate —— 前端绕过也拦得住。
   */
  function rulesFromMeta(metaFields = fields.value) {
    const obj = {}
    for (const f of metaFields) {
      if (isDetailList(f)) continue
      const required = f.required === 1
      const r = []
      if (required) {
        r.push({ required: true, message: (f.fieldLabel || f.fieldKey) + '不能为空', trigger: 'blur' })
      }
      // 叠加后端规则表里的长度/数值约束，给出即时提示
      const fieldRules = (rules.value || []).filter((x) => x.fieldKey === f.fieldKey)
      for (const fr of fieldRules) {
        if (fr.ruleType === 'MAX_LENGTH' && fr.ruleValue) {
          r.push({
            max: Number(fr.ruleValue),
            message: fr.errorMessage || (f.fieldLabel + '长度不能超过' + fr.ruleValue),
            trigger: 'blur',
          })
        } else if (fr.ruleType === 'MIN_VALUE' && fr.ruleValue) {
          r.push({
            type: 'number',
            min: Number(fr.ruleValue),
            message: fr.errorMessage || (f.fieldLabel + '不能小于' + fr.ruleValue),
            trigger: 'blur',
          })
        } else if (fr.ruleType === 'EMAIL') {
          r.push({ type: 'email', message: fr.errorMessage || '邮箱格式不正确', trigger: 'blur' })
        } else if (fr.ruleType === 'PHONE') {
          r.push({
            pattern: /^1[3-9]\d{9}$/,
            message: fr.errorMessage || '手机号格式不正确',
            trigger: 'blur',
          })
        } else if (fr.ruleType === 'PATTERN' && fr.ruleValue) {
          r.push({ pattern: new RegExp(fr.ruleValue), message: fr.errorMessage || '格式不正确', trigger: 'blur' })
        }
      }
      if (r.length) {
        obj[f.fieldKey] = r
      }
    }
    return obj
  }

  /**
   * 生成初始表单值（按元数据填默认值）。
   */
  async function initForm(initial = {}) {
    const res = await request.post('/masterdata/form-meta/' + formCode + '/defaults', initial)
    return res?.data || initial
  }

  /**
   * 调服务端权威校验。返回 {passed, errors, warnings}。
   * 保存/提交前必调 —— 这是「元数据驱动」真正生效的地方。
   */
  async function validateOnServer(formData, scope = 'SAVE') {
    const res = await request.post(
      `/masterdata/form-meta/${formCode}/validate?scope=${scope}`,
      formData
    )
    return res?.data || { passed: true, errors: [], warnings: [] }
  }

  /** 只读字段集合（当前用户不可编辑） */
  const readonlyFields = computed(() =>
    fields.value.filter((f) => f.locked === 1).map((f) => f.fieldKey)
  )

  /** 可见且可编辑的字段 */
  const editableFields = computed(() =>
    fields.value.filter((f) => f.locked !== 1 && !isDetailList(f))
  )

  /** 清缓存（后台改了字段元数据后调用） */
  function clearCache() {
    Object.keys(cache).forEach((k) => delete cache[k])
  }

  return {
    fields,
    rules,
    loading,
    readonlyFields,
    editableFields,
    fetchMeta,
    preload,
    widgetOf,
    isDetailList,
    isTextarea,
    isDateField,
    spanOf,
    rulesFromMeta,
    initForm,
    validateOnServer,
    clearCache,
    WIDGET_MAP,
    WIDTH_SPAN,
  }
}
