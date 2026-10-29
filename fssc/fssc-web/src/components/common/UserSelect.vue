<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-select
    :model-value="modelValue"
    filterable
    :remote="lazy"
    :remote-method="handleRemoteSearch"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder || '请选择人员'"
    style="width: 100%"
    @update:model-value="onUpdate"
    @change="onChange"
  >
    <el-option
      v-for="u in options"
      :key="u.id"
      :label="labelOf(u)"
      :value="u.id"
    />
  </el-select>
</template>

<script setup>
/**
 * UserSelect 人员选择组件（统一人员下拉 + P0 权限治理）
 *
 * 统一人员展示规则：nickName || userName（可选附加部门后缀），
 * 值绑定用户 id。数据源：userApi.page（租户用户分页）。
 *
 * **P0 修复**：默认只加载同公司人员，解决全量加载的性能与安全问题。
 *
 * 用法：
 *   <UserSelect v-model="bill.applyUserId" @change="(id, u) => onSel(bill, id, u)" />
 *   <UserSelect v-model="delegate.toUserId" scope="same-company" show-dept />
 *   <UserSelect v-model="form.userId" scope="all" lazy /> <!-- HR 角色用 -->
 *
 * props:
 *   modelValue 选中用户 id（v-model）
 *   pageSize 拉查询量（默认 200）
 *   showDept 是否在标签后附加（部门名）
 *   scope 可见范围：'same-company'（同公司，缺省）/ 'same-dept'（同部门）/ 'all'（全量，需权限）
 *   companyId 手动指定公司 ID（覆盖自动推断）
 *   lazy 是否启用懒加载（输入关键词才查询，默认 false）
 *   clearable/disabled/placeholder
 *   users 外部已加载的用户列表（优先使用）
 *
 * emits:
 *   update:modelValue 选中 id
 *   change(id, user) 选中变化（user 为完整用户对象，用于回填姓名）
 */
import { ref, computed, onMounted } from 'vue'
import { userApi, store } from '@/api'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  pageSize: { type: Number, default: 200 },
  showDept: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  /** 外部已加载的用户列表（避免重复请求）；不传则组件内部通过 userApi.page 拉取 */
  users: { type: Array, default: null },
  /** 可见范围：'same-company'（同公司，缺省）/ 'same-dept'（同部门）/ 'all'（全量） */
  scope: { type: String, default: 'same-company', validator: (v) => ['same-company', 'same-dept', 'all'].includes(v) },
  /** 手动指定公司 ID（覆盖自动推断） */
  companyId: { type: Number, default: null },
  /** 是否启用懒加载（输入关键词才查询） */
  lazy: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change'])

const innerOptions = ref([])
const loaded = ref(false)
const searchKeyword = ref('')

const options = computed(() => (props.users && props.users.length ? props.users : innerOptions.value))

/** 根据 scope 和公司/部门自动过滤 */
function buildQueryParams() {
  const params = { pageNum: 1, pageSize: props.pageSize }

  // 关键词搜索（懒加载模式）
  if (props.lazy && searchKeyword.value) {
    params.keyword = searchKeyword.value
  }

  // 公司维度过滤
  if (props.companyId != null) {
    // 手动指定的 companyId 优先
    params.companyId = props.companyId
  } else if (props.scope === 'same-company') {
    // 默认：同公司
    params.companyId = store.user?.companyId || undefined
  }
  // scope='all' 时不传 companyId，后端返回全量

  // 部门维度过滤（未来可扩展）
  if (props.scope === 'same-dept' && store.user?.deptId) {
    params.deptIds = String(store.user.deptId)
  }

  return params
}

async function load() {
  if (props.users && props.users.length) return
  if (loaded.value && !props.lazy) return
  if (!props.lazy) loaded.value = true

  try {
    const params = buildQueryParams()
    const list = (await userApi.page(params)).list || []
    innerOptions.value = list
  } catch (e) {
    innerOptions.value = []
  }
}

/** 远程搜索（lazy 模式） */
async function handleRemoteSearch(keyword) {
  if (!props.lazy) return
  searchKeyword.value = keyword
  await load()
}

function labelOf(u) {
  const name = u.nickName || u.userName || ''
  if (props.showDept && (u.deptName || u.departmentName)) {
    return `${name}（${u.deptName || u.departmentName}）`
  }
  return name
}

function onUpdate(v) {
  emit('update:modelValue', v)
}

function onChange(id) {
  const u = options.value.find((x) => x.id === id)
  emit('change', id, u)
}

onMounted(load)
</script>