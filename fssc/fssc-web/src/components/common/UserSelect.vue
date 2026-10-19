<template>
  <el-select
    :model-value="modelValue"
    filterable
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
 * UserSelect 人员选择组件（P2-3 统一人员下拉）
 *
 * 统一人员展示规则：nickName || userName（可选附加部门后缀），
 * 值绑定用户 id。数据源：userApi.page（租户用户分页）。
 *
 * 用法：
 *   <UserSelect v-model="bill.applyUserId" @change="(id, u) => onSel(bill, id, u)" />
 *
 * props:
 *   value            选中用户 id（v-model）
 *   pageSize         拉取数量（默认 200，覆盖全部在职用户）
 *   showDept         是否在标签后附加（部门名）
 *   clearable/disabled/placeholder
 *
 * emits:
 *   update:modelValue 选中 id
 *   change(id, user)  选中变化（user 为完整用户对象，用于回填姓名）
 */
import { ref, computed, onMounted } from 'vue'
import { userApi } from '@/api'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  pageSize: { type: Number, default: 200 },
  showDept: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  /** 外部已加载的用户列表（避免重复请求）；不传则组件内部通过 userApi.page 拉取 */
  users: { type: Array, default: null },
})

const emit = defineEmits(['update:modelValue', 'change'])

const innerOptions = ref([])
const loaded = ref(false)

const options = computed(() => (props.users && props.users.length ? props.users : innerOptions.value))

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

async function load() {
  if (props.users && props.users.length) return
  if (loaded.value) return
  loaded.value = true
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: props.pageSize })).list || []
    innerOptions.value = list
  } catch (e) {
    innerOptions.value = []
  }
}

onMounted(load)
</script>