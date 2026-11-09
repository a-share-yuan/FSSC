// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

<template>
  <div class="df-wrap" v-loading="loading">
    <el-form
      ref="formRef"
      :model="model"
      :rules="formRules"
      :label-width="labelWidth"
      :disabled="readonly"
      label-position="right"
    >
      <div v-for="sec in sections" :key="sec.name" class="df-section">
        <div v-if="showSectionTitle" class="df-section__title">{{ sec.name || '基本信息' }}</div>
        <el-row :gutter="16">
          <el-col v-for="f in sec.fields" :key="f.fieldKey" :span="spanOf(f)">
            <el-form-item :label="f.fieldLabel" :prop="f.fieldKey">
              <!-- 单行文本 / 参照（参照未配置选项时退化为可输入下拉） -->
              <el-input
                v-if="f.widgetType === 'INPUT'"
                v-model="model[f.fieldKey]"
                :placeholder="f.placeholder || ''"
                :disabled="isLocked(f)"
                clearable
              />

              <el-input
                v-else-if="f.widgetType === 'TEXTAREA'"
                v-model="model[f.fieldKey]"
                type="textarea"
                :rows="3"
                :placeholder="f.placeholder || ''"
                :disabled="isLocked(f)"
              />

              <el-input
                v-else-if="f.widgetType === 'REF'"
                v-model="model[f.fieldKey]"
                :placeholder="f.placeholder || '请填写参照值'"
                :disabled="isLocked(f)"
                clearable
              />

              <el-input-number
                v-else-if="f.widgetType === 'INPUT_NUMBER' || f.widgetType === 'MONEY' || f.widgetType === 'PERCENT'"
                v-model="model[f.fieldKey]"
                :precision="f.widgetType === 'PERCENT' ? 2 : 2"
                :controls="false"
                :placeholder="f.placeholder || ''"
                :disabled="isLocked(f)"
                style="width: 100%"
              />

              <el-switch
                v-else-if="f.widgetType === 'SWITCH'"
                v-model="model[f.fieldKey]"
                :disabled="isLocked(f)"
              />

              <el-date-picker
                v-else-if="f.widgetType === 'DATE' || f.widgetType === 'DATETIME'"
                v-model="model[f.fieldKey]"
                :type="f.widgetType === 'DATE' ? 'date' : 'datetime'"
                :value-format="f.widgetType === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
                :placeholder="f.placeholder || '请选择'"
                :disabled="isLocked(f)"
                style="width: 100%"
              />

              <el-select
                v-else-if="f.widgetType === 'SELECT'"
                v-model="model[f.fieldKey]"
                :placeholder="f.placeholder || '请选择'"
                :disabled="isLocked(f)"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="o in optionsOf(f)"
                  :key="String(o.value)"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>

              <!-- 人员：优先用组织用户列表，取不到时允许手填，避免配置一处人员字段就卡死整张表单 -->
              <el-select
                v-else-if="f.widgetType === 'USER_SELECT'"
                v-model="model[f.fieldKey]"
                filterable
                allow-create
                default-first-option
                :placeholder="f.placeholder || '请选择或输入'"
                :disabled="isLocked(f)"
                style="width: 100%"
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.value"
                  :label="u.label"
                  :value="u.value"
                />
              </el-select>

              <el-tree-select
                v-else-if="f.widgetType === 'DEPT_SELECT'"
                v-model="model[f.fieldKey]"
                :data="deptTree"
                :props="{ label: 'label', value: 'value', children: 'children' }"
                check-strictly
                filterable
                :placeholder="f.placeholder || '请选择部门'"
                :disabled="isLocked(f)"
                style="width: 100%"
              />

              <!-- 附件与行项目表暂不内联渲染：附件走单据附件区，行项目需要专用表格组件 -->
              <el-input
                v-else-if="f.widgetType === 'UPLOAD'"
                v-model="model[f.fieldKey]"
                :placeholder="f.placeholder || '请填写附件编号或链接'"
                :disabled="isLocked(f)"
              />

              <div v-else-if="f.widgetType === 'AMOUNT_LIST'" class="df-hint">
                行项目字段需专用明细组件承载
              </div>

              <el-input
                v-else
                v-model="model[f.fieldKey]"
                :placeholder="f.placeholder || ''"
                :disabled="isLocked(f)"
              />

              <div v-if="f.helpText" class="df-help">{{ f.helpText }}</div>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <el-empty v-if="!loading && !fields.length" description="该表单尚未配置字段" :image-size="80" />
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { formApi, userApi, orgApi } from '../../api'

const props = defineProps({
  formCode: { type: String, required: true },
  modelValue: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  labelWidth: { type: String, default: '120px' },
})

const emit = defineEmits(['update:modelValue', 'loaded'])

const formRef = ref(null)
const fields = ref([])
const loading = ref(false)
const userOptions = ref([])
const deptTree = ref([])

const WIDTH_SPAN = { FULL: 24, HALF: 12, THIRD: 8, QUARTER: 6 }

/** 双向绑定到父组件的表单对象；直接改字段而不是整体替换，避免输入过程中失焦 */
const model = computed(() => props.modelValue || {})

watch(
  () => model.value && Object.keys(model.value).length,
  () => formRef.value && formRef.value.clearValidate(),
)

const spanOf = (f) => f.span || WIDTH_SPAN[f.controlWidth] || 24
const isLocked = (f) => f.locked === 1 || f.locked === true

/** 按 section 分组，保持 sortOrder 次序；没有分节时归到一个默认组 */
const sections = computed(() => {
  const groups = new Map()
  for (const f of fields.value) {
    const name = f.section || '基本信息'
    if (!groups.has(name)) groups.set(name, [])
    groups.get(name).push(f)
  }
  return [...groups.entries()].map(([name, list]) => ({ name, fields: list }))
})

const showSectionTitle = computed(() => sections.value.length > 1)

/** 前端规则只做即时提示，必录/范围的权威判定在服务端（保存与提交时各校验一次） */
const formRules = computed(() => {
  const r = {}
  for (const f of fields.value) {
    if (!f.required) continue
    r[f.fieldKey] = [
      {
        required: true,
        message: (f.fieldLabel || f.fieldKey) + '不能为空',
        trigger: ['blur', 'change'],
      },
    ]
  }
  return r
})

function optionsOf(f) {
  if (!f.optionsJson) return []
  try {
    const parsed = JSON.parse(f.optionsJson)
    if (Array.isArray(parsed)) return parsed
    if (Array.isArray(parsed.options)) return parsed.options
    // 兼容 {字典码: 显示名} 这类简单映射
    return Object.entries(parsed).map(([value, label]) => ({ value, label: String(label) }))
  } catch (e) {
    return []
  }
}

async function loadMeta() {
  if (!props.formCode) return
  loading.value = true
  try {
    const res = await formApi.meta(props.formCode)
    // 响应拦截器已解包到 body.data，这里拿到的是表单元数据本身
    const data = res || {}
    fields.value = data.fields || []
    emit('loaded', data)
    await loadRefOptions()
  } finally {
    loading.value = false
  }
}

/** 只有配置了人员/部门字段才去拉选项，避免每张表单都多打两个接口 */
async function loadRefOptions() {
  const needUser = fields.value.some((f) => f.widgetType === 'USER_SELECT')
  const needDept = fields.value.some((f) => f.widgetType === 'DEPT_SELECT')
  if (needUser) {
    try {
      const res = await userApi.page({ pageNum: 1, pageSize: 500 })
      const list = res?.list || res?.records || []
      userOptions.value = list.map((u) => ({
        value: u.id,
        label: u.realName || u.nickname || u.username || String(u.id),
      }))
    } catch (e) {
      userOptions.value = []
    }
  }
  if (needDept) {
    try {
      const res = await orgApi.deptTree()
      deptTree.value = flattenTree(res || [])
    } catch (e) {
      deptTree.value = []
    }
  }
}

/** 部门树字段名各版本不一致，统一折成 label/value/children */
function flattenTree(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes.map((n) => ({
    label: n.deptName || n.name || n.label || String(n.id),
    value: n.id || n.value,
    children: flattenTree(n.children || []),
  }))
}

function validate() {
  return formRef.value ? formRef.value.validate() : Promise.resolve(false)
}

function clearValidate() {
  formRef.value && formRef.value.clearValidate()
}

onMounted(loadMeta)

watch(() => props.formCode, loadMeta)

defineExpose({ validate, clearValidate, reload: loadMeta })
</script>

<style scoped>
.df-section {
  margin-bottom: 8px;
}
.df-section__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin: 4px 0 12px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1.2;
}
.df-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 2px;
}
.df-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
