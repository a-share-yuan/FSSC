<template>
  <el-drawer v-model="visible" title="收藏夹" direction="rtl" size="360px" :destroy-on-close="true">
    <div class="fav-content">
      <div class="fav-tabs">
        <el-radio-group v-model="activeType" size="small">
          <el-radio-button label="bill">单据</el-radio-button>
          <el-radio-button label="report">报表</el-radio-button>
          <el-radio-button label="menu">功能菜单</el-radio-button>
        </el-radio-group>
      </div>

      <div class="fav-list">
        <div v-for="item in filteredList" :key="item.id" class="fav-item" draggable="true"
          @dragstart="dragStart($event, item)" @dragover.prevent="dragOver($event, item)" @drop="drop($event, item)"
          @click="navigate(item)"
        >
          <el-icon class="fav-drag"><Rank /></el-icon>
          <el-icon class="fav-icon"><component :is="iconFor(item)" /></el-icon>
          <span class="fav-name">{{ item.name }}</span>
          <el-button link size="small" type="danger" @click.stop="removeFav(item)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-empty v-if="filteredList.length === 0" description="暂无收藏，点击星标添加" :image-size="48" />
      </div>

      <div class="fav-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>拖拽排序 · 点击星标添加收藏</span>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Rank, Delete, InfoFilled, Document, DataAnalysis, Menu, Star } from '@element-plus/icons-vue'
import { store } from '../api'
import { ElMessage } from 'element-plus'
import { pathDenyReason } from '../router/pathAccess'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const STORAGE_KEY = 'fssc_favorites'
const activeType = ref('bill')
const favorites = ref([])

const filteredList = computed(() => favorites.value.filter(f => f.type === activeType.value))

function loadFavorites() {
  try {
    favorites.value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch { favorites.value = [] }
}

function saveFavorites() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(favorites.value))
}

function removeFav(item) {
  favorites.value = favorites.value.filter(f => f.id !== item.id)
  saveFavorites()
}

function navigate(item) {
  // 收藏夹存的是路径：角色调整后原收藏可能已不可达，直推会被守卫弹回并提示无权限
  if (item.path && pathDenyReason(item.path)) {
    ElMessage.warning('该页面当前账号无法访问，请联系管理员')
    return
  }
  if (item.path) router.push(item.path)
  visible.value = false
}

function iconFor(item) {
  if (item.type === 'report') return DataAnalysis
  if (item.type === 'menu') return Menu
  return Document
}

// 拖拽排序
let dragItem = null
function dragStart(e, item) { dragItem = item }
function dragOver(e, item) { e.preventDefault() }
function drop(e, target) {
  if (!dragItem || dragItem.id === target.id) return
  const list = favorites.value
  const fromIdx = list.indexOf(dragItem)
  const toIdx = list.indexOf(target)
  list.splice(fromIdx, 1)
  list.splice(toIdx, 0, dragItem)
  saveFavorites()
}

// 全局方法：添加收藏
function addFavorite(item) {
  if (favorites.value.some(f => f.id === item.id)) return
  favorites.value.push(item)
  saveFavorites()
}
function isFavorited(id) {
  return favorites.value.some(f => f.id === id)
}

defineExpose({ addFavorite, isFavorited })

onMounted(() => loadFavorites())
</script>

<style scoped>
.fav-content { padding: 0 4px; display: flex; flex-direction: column; height: 100%; }
.fav-tabs { margin-bottom: 12px; }
.fav-list { flex: 1; overflow-y: auto; }
.fav-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px; border-radius: 6px; cursor: pointer;
  border: 1px solid #ebeef5; margin-bottom: 4px;
  transition: background 0.15s;
}
.fav-item:hover { background: #ecf5ff; }
.fav-drag { color: #c0c4cc; cursor: grab; font-size: 14px; }
.fav-icon { color: #409eff; font-size: 16px; }
.fav-name { flex: 1; font-size: 13px; }
.fav-hint {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: #909399; padding-top: 10px;
  border-top: 1px solid #ebeef5;
}
</style>
