<template>
  <div class="profile">
    <!-- ========== 用户信息卡 ========== -->
    <el-card class="user-card" shadow="never">
      <div class="user-header">
        <div class="avatar">
          {{ avatarText }}
        </div>
        <div class="user-meta">
          <div class="user-name-line">
            <span class="user-name">{{ profileUser?.nickName || user?.realName || user?.username || '-' }}</span>
            <el-tag :type="user?.subjectType === 'PLATFORM' ? 'danger' : 'primary'" size="small" effect="dark" round>
              {{ user?.subjectType === 'PLATFORM' ? '平台管理员' : '企业员工' }}
            </el-tag>
          </div>
          <div class="user-sub-line">
            <span class="meta-item"><el-icon><User /></el-icon>{{ user?.username }}</span>
            <span class="meta-item"><el-icon><Iphone /></el-icon>{{ profileUser?.phone || '未绑定' }}</span>
            <span class="meta-item"><el-icon><Message /></el-icon>{{ profileUser?.email || '未绑定' }}</span>
            <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatTime(user?.loginAt) }}</span>
          </div>
        </div>
        <div class="user-actions">
          <el-button size="small" plain @click="router.push('/')"><el-icon><Back /></el-icon>&nbsp;返回主页</el-button>
          <el-button type="primary" size="small" @click="openEditProfile">编辑资料</el-button>
          <el-button type="warning" plain size="small" @click="openChangePassword">修改密码</el-button>
          <el-dropdown trigger="click" @command="onMoreAction">
            <el-button size="small" plain>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="phone">更换手机</el-dropdown-item>
                <el-dropdown-item command="email">更换邮箱</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-card>

    <!-- ========== 概览统计 ========== -->
    <el-row :gutter="12" class="stat-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-role" @click="scrollTo('roles')">
          <div class="stat-icon"><el-icon><Avatar /></el-icon></div>
          <div class="stat-body">
            <div class="stat-num">{{ roleList.length }}</div>
            <div class="stat-label">我的角色</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-perm" @click="scrollTo('perms')">
          <div class="stat-icon"><el-icon><Key /></el-icon></div>
          <div class="stat-body">
            <div class="stat-num">{{ isAdminUser ? '全部' : permList.length }}</div>
            <div class="stat-label">我的权限</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-todo" @click="scrollTo('todos')">
          <div class="stat-icon"><el-icon><Bell /></el-icon></div>
          <div class="stat-body">
            <div class="stat-num">{{ todoList.length }}</div>
            <div class="stat-label">待办事项</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-done">
          <div class="stat-icon"><el-icon><CircleCheck /></el-icon></div>
          <div class="stat-body">
            <div class="stat-num">{{ doneCount }}</div>
            <div class="stat-label">本月已办</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ========== 主体区域：左右分栏 ========== -->
    <el-row :gutter="16">
      <!-- 左栏：角色 + 权限 -->
      <el-col :xs="24" :lg="14">
        <!-- 我的角色 -->
        <el-card ref="rolesRef" class="section-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Avatar /></el-icon> 我的角色</span>
              <el-tag size="small" type="info" round>{{ roleList.length }} 个</el-tag>
            </div>
          </template>
          <div v-if="roleList.length" class="role-grid">
            <div v-for="r in roleList" :key="r" class="role-item">
              <div class="role-icon"><el-icon><UserFilled /></el-icon></div>
              <div class="role-name">{{ r }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无角色" :image-size="60" />
        </el-card>

        <!-- 我的权限 -->
        <el-card ref="permsRef" class="section-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Key /></el-icon> 我的权限</span>
              <div class="perm-header-right">
                <el-input
                  v-model="permSearch"
                  placeholder="搜索权限"
                  size="small"
                  clearable
                  style="width: 160px"
                  :prefix-icon="Search"
                />
                <el-tag size="small" type="info" round>{{ permList.length }} 项</el-tag>
              </div>
            </div>
          </template>
          <div v-if="isAdminUser" class="perm-admin-notice">
            <el-alert type="success" :closable="false" show-icon>
              <template #title>管理员通配权限</template>
              <span style="font-size: 13px">您拥有 <el-tag size="small" type="success">*:*:*</el-tag> 通配权限，可访问系统全部模块和功能，无需单独授权。</span>
            </el-alert>
          </div>
          <div v-else-if="permTreeData.length" class="perm-tree-wrap">
            <div class="perm-toolbar">
              <el-button size="small" text @click="expandAllPerms">全部展开</el-button>
              <el-button size="small" text @click="collapseAllPerms">全部收起</el-button>
            </div>
            <el-tree
              ref="permTreeRef"
              :key="treeKey"
              :data="permTreeData"
              :props="{ label: 'label', children: 'children' }"
              :filter-node-method="permFilterNode"
              :default-expanded-keys="permDefaultExpanded"
              node-key="key"
              show-checkbox
              :check-strictly="true"
              class="perm-tree"
            >
              <template #default="{ node, data }">
                <span class="perm-tree-node">
                  <span class="perm-tree-label">{{ node.label }}</span>
                  <el-tag v-if="data.children" size="small" type="info" round>{{ data.children.length }}</el-tag>
                </span>
              </template>
            </el-tree>
          </div>
          <el-empty v-else description="暂无权限数据" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 右栏：待办 -->
      <el-col :xs="24" :lg="10">
        <el-card ref="todosRef" class="section-card todo-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Bell /></el-icon> 我的待办</span>
              <el-button v-if="todoList.length" type="primary" size="small" text @click="goApproval">
                查看全部<el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <SkeletonDetail v-if="todoLoading" :rows="6" />
          <div v-else>
            <div v-if="todoList.length" class="todo-list">
              <div v-for="(item, idx) in todoList" :key="idx" class="todo-item" @click="goApproval">
                <div class="todo-left">
                  <div :class="['todo-priority', todoPriority(item)]"></div>
                  <div class="todo-content">
                    <div class="todo-title">{{ item.title || item.billNo || '-' }}</div>
                    <div class="todo-sub">
                      <el-tag size="small" effect="plain" round>{{ item.taskName || '-' }}</el-tag>
                      <span class="todo-wait">{{ todoWaitTime(item.createTime) }}</span>
                    </div>
                  </div>
                </div>
                <el-button type="primary" size="small" plain round>去审批</el-button>
              </div>
            </div>
            <el-empty v-else-if="!todoLoading" description="暂无待办事项" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 编辑资料弹窗 ========== -->
    <el-dialog v-model="editVisible" title="编辑资料" width="480px" destroy-on-close>
      <el-form :model="editForm" :rules="editProfileRules" label-width="100px">
        <el-form-item label="姓名" prop="nickName" required>
          <el-input v-model="editForm.nickName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="请输入邮箱地址" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 修改密码弹窗 ========== -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="480px" destroy-on-close>
      <el-form :model="pwdForm" :rules="pwdRules" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- ========== 更换手机弹窗 ========== -->
    <el-dialog v-model="phoneVisible" title="更换手机号" width="480px" destroy-on-close>
      <el-form :model="phoneForm" :rules="phoneRules" label-width="100px">
        <el-form-item label="新手机号" prop="newPhone" required>
          <el-input v-model="phoneForm.newPhone" placeholder="请输入新手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码" prop="verifyCode" required>
          <el-input v-model="phoneForm.verifyCode" style="width: 200px" />
          <el-button style="margin-left: 8px" size="small" disabled>发送验证码</el-button>
          <span style="color: #999; font-size: 12px; margin-left: 4px;">（验证码由系统短信/邮件下发）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="phoneVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePhone">确认更换</el-button>
      </template>
    </el-dialog>

    <!-- ========== 更换邮箱弹窗 ========== -->
    <el-dialog v-model="emailVisible" title="更换邮箱" width="480px" destroy-on-close>
      <el-form :model="emailForm" :rules="emailRules" label-width="100px">
        <el-form-item label="新邮箱" prop="newEmail" required>
          <el-input v-model="emailForm.newEmail" placeholder="请输入新邮箱地址" maxlength="100" />
        </el-form-item>
        <el-form-item label="验证码" prop="verifyCode" required>
          <el-input v-model="emailForm.verifyCode" style="width: 200px" />
          <el-button style="margin-left: 8px" size="small" disabled>发送验证码</el-button>
          <span style="color: #999; font-size: 12px; margin-left: 4px;">（验证码由系统短信/邮件下发）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEmail">确认更换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { store, wfApi, profileApi } from '../../api'
import { ElMessage } from 'element-plus'
import { MODULE_MAP, RESOURCE_MAP, ACTION_MAP } from '../../utils/permMappings'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import {
  User, UserFilled, Avatar, Key, Bell, Clock, Iphone, Message,
  CircleCheck, ArrowDown, ArrowRight, Folder, Search, Back,
} from '@element-plus/icons-vue'
import { formatDate, formatDateTime } from '@/utils/format'

const router = useRouter()
const user = store.user
const profileUser = ref({})
const todoList = ref([])
const todoLoading = ref(false)
const roleList = ref([])
const saving = ref(false)
const doneCount = ref(0)
const permSearch = ref('')
const permTreeRef = ref()
const permDefaultExpanded = ref([])
const treeKey = ref(0)

// 编辑资料
const editVisible = ref(false)
const editForm = ref({ nickName: '', phone: '', email: '' })

// 修改密码
const pwdVisible = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

// 更换手机
const phoneVisible = ref(false)
const phoneForm = ref({ newPhone: '', verifyCode: '' })

// 更换邮箱
const emailVisible = ref(false)
const emailForm = ref({ newEmail: '', verifyCode: '' })

const editProfileRules = {
  nickName: [{ required: true, message: '请输入姓名' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码' }],
  newPassword: [{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码' }],
}

const phoneRules = {
  newPhone: [{ required: true, message: '请输入新手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }],
  verifyCode: [{ required: true, message: '请输入验证码' }],
}

const emailRules = {
  newEmail: [{ required: true, message: '请输入新邮箱' }, { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
  verifyCode: [{ required: true, message: '请输入验证码' }],
}

// 头像首字母
const avatarText = computed(() => {
  const name = profileUser.value?.nickName || user?.realName || user?.username || '?'
  return name.charAt(0).toUpperCase()
})

// 权限列表（优先用 profile API 返回的 perms，其次回退到 localStorage 登录数据）
const permList = ref([])
const isAdminUser = computed(() => permList.value.includes('*:*:*'))

// 构建权限树：模块 → 资源 → 操作（映射表已从 ../utils/permMappings 导入）
const permTreeData = computed(() => {
  // 1. 展开逗号打包的权限码
  const rawPerms = permList.value
  const perms = []
  for (const p of rawPerms) {
    if (!p) continue
    for (const part of String(p).split(',')) {
      const code = part.trim()
      if (code) perms.push(code)
    }
  }

  // 2. 按段数分类构建树（跳过 *:*:* 通配符，admin 由模板单独展示）
  const tree = {}
  for (const code of perms) {
    if (code === '*:*:*') continue
    const parts = code.split(':')
    const modKey = parts[0]
    if (!modKey) continue

    if (parts.length === 2) {
      // 两段式 module:action（如 workbench:view, dashboard:view）
      const actKey = parts[1]
      const modLabel = MODULE_MAP[modKey] || modKey
      const actLabel = ACTION_MAP[actKey] || actKey
      if (!tree[modKey]) tree[modKey] = { key: modKey, label: modLabel, children: {} }
      const resKey = '_default'
      if (!tree[modKey].children[resKey]) tree[modKey].children[resKey] = { key: `${modKey}:${resKey}`, label: '通用功能', children: {} }
      tree[modKey].children[resKey].children[actKey] = { key: code, label: actLabel }
    } else {
      // 三段式 module:resource:action
      const resKey = parts[1] || 'other'
      const actKey = parts.slice(2).join(':') || 'all'
      const modLabel = MODULE_MAP[modKey] || modKey
      const resLabel = RESOURCE_MAP[resKey] || resKey
      const actLabel = ACTION_MAP[actKey] || actKey
      if (!tree[modKey]) tree[modKey] = { key: modKey, label: modLabel, children: {} }
      if (!tree[modKey].children[resKey]) tree[modKey].children[resKey] = { key: `${modKey}:${resKey}`, label: resLabel, children: {} }
      tree[modKey].children[resKey].children[actKey] = { key: code, label: actLabel }
    }
  }

  return Object.values(tree)
    .sort((a, b) => Object.keys(b.children).length - Object.keys(a.children).length)
    .map(mod => ({
      ...mod,
      children: Object.values(mod.children)
        .sort((a, b) => Object.keys(b.children).length - Object.keys(a.children).length)
        .map(res => ({ ...res, children: Object.values(res.children) })),
    }))
})

// 搜索过滤
watch(permSearch, (val) => {
  permTreeRef.value?.filter(val)
})

function permFilterNode(value, data) {
  if (!value) return true
  const kw = value.toLowerCase()
  return (data.label?.toLowerCase().includes(kw)) || (data.permCode?.toLowerCase().includes(kw))
}

function expandAllPerms() {
  const allKeys = []
  for (const mod of permTreeData.value) {
    allKeys.push(mod.key)
    for (const res of mod.children) allKeys.push(res.key)
  }
  permDefaultExpanded.value = allKeys
  treeKey.value++
}

function collapseAllPerms() {
  permDefaultExpanded.value = []
  treeKey.value++
}

function formatTime(ts) {
  if (!ts) return '-'
  return formatDateTime(ts)
}

function scrollTo(refName) {
  const el = refName === 'roles' ? rolesRef.value?.$el : refName === 'perms' ? permsRef.value?.$el : todosRef.value?.$el
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function todoPriority(item) {
  if (!item.createTime) return 'normal'
  const hours = (Date.now() - new Date(item.createTime).getTime()) / 3600000
  if (hours > 48) return 'urgent'
  if (hours > 24) return 'warning'
  return 'normal'
}

function todoWaitTime(createTime) {
  if (!createTime) return ''
  const diff = Date.now() - new Date(createTime).getTime()
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(hours / 24)
  if (days > 0) return `等待 ${days}天${hours % 24}小时`
  if (hours > 0) return `等待 ${hours}小时`
  const mins = Math.floor(diff / 60000)
  return `等待 ${mins}分钟`
}

function goApproval() {
  router.push('/approval')
}

function onMoreAction(cmd) {
  if (cmd === 'phone') openChangePhone()
  else if (cmd === 'email') openChangeEmail()
}

function openEditProfile() {
  editForm.value = {
    nickName: profileUser.value?.nickName || user?.realName || '',
    phone: profileUser.value?.phone || '',
    email: profileUser.value?.email || '',
  }
  editVisible.value = true
}

async function saveProfile() {
  if (editForm.value.phone && !/^1[3-9]\d{9}$/.test(editForm.value.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (editForm.value.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editForm.value.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  saving.value = true
  try {
    await profileApi.update(editForm.value)
    ElMessage.success('更新成功')
    editVisible.value = false
    loadProfile()
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    saving.value = false
  }
}

function openChangePassword() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdVisible.value = true
}

async function savePassword() {
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (pwdForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  saving.value = true
  try {
    await profileApi.changePassword({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdVisible.value = false
    store.logout()
    location.hash = '#/login'
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  } finally {
    saving.value = false
  }
}

function openChangePhone() {
  phoneForm.value = { newPhone: '', verifyCode: '' }
  phoneVisible.value = true
}

async function savePhone() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.value.newPhone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  saving.value = true
  try {
    await profileApi.changePhone(phoneForm.value)
    ElMessage.success('手机号更换成功')
    phoneVisible.value = false
    loadProfile()
  } catch (e) {
    ElMessage.error(e.message || '更换失败')
  } finally {
    saving.value = false
  }
}

function openChangeEmail() {
  emailForm.value = { newEmail: '', verifyCode: '' }
  emailVisible.value = true
}

async function saveEmail() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.value.newEmail)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  saving.value = true
  try {
    await profileApi.changeEmail(emailForm.value)
    ElMessage.success('邮箱更换成功')
    emailVisible.value = false
    loadProfile()
  } catch (e) {
    ElMessage.error(e.message || '更换失败')
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  try {
    const data = await profileApi.get()
    profileUser.value = data.user || {}
    roleList.value = data.roles || []
    // 用 profile API 返回的 perms 作为权限树数据源（比 localStorage 更可靠）
    permList.value = data.perms || user?.perms || []
  } catch (e) {
    console.warn('个人资料加载失败', e.message)
    // 回退到 localStorage 登录数据
    permList.value = user?.perms || []
  }
}

onMounted(async () => {
  loadProfile()
  // 加载待办
  todoLoading.value = true
  try {
    todoList.value = await wfApi.todo()
  } catch (e) {
    console.warn('待办加载失败', e.message)
  } finally {
    todoLoading.value = false
  }
  // 默认展开前2个模块
  permDefaultExpanded.value = permTreeData.value.slice(0, 2).map(n => n.key)
})

const rolesRef = ref()
const permsRef = ref()
const todosRef = ref()
</script>

<style scoped>
.profile { max-width: 1100px; margin: 0 auto; padding: 16px; }

/* ===== 用户信息卡 ===== */
.user-card { margin-bottom: 16px; border: none; border-radius: 8px; }
.user-card :deep(.el-card__body) { padding: 20px 24px; }
.user-header { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.avatar {
  width: 64px; height: 64px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #409eff, #36cfc9);
  color: #fff; font-size: 28px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}
.user-meta { flex: 1; min-width: 200px; }
.user-name-line { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.user-name { font-size: 20px; font-weight: 700; color: #1f2d3d; }
.user-sub-line { display: flex; flex-wrap: wrap; gap: 16px; color: #909399; font-size: 13px; }
.meta-item { display: flex; align-items: center; gap: 4px; }
.meta-item .el-icon { font-size: 14px; }
.user-actions { display: flex; gap: 8px; flex-shrink: 0; }

/* ===== 概览统计 ===== */
.stat-row { margin-bottom: 16px; }
.stat-card {
  display: flex; align-items: center; gap: 12px; padding: 16px;
  border-radius: 8px; cursor: pointer; transition: all 0.2s;
  border: 1px solid #ebeef5; background: #fff;
}
.stat-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); transform: translateY(-2px); }
.stat-icon {
  width: 42px; height: 42px; border-radius: 8px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; color: #fff;
}
.stat-icon .el-icon { font-size: 22px; }
.stat-role .stat-icon { background: linear-gradient(135deg, #409eff, #79bbff); }
.stat-perm .stat-icon { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-todo .stat-icon { background: linear-gradient(135deg, #e6a23c, #f3d19e); }
.stat-done .stat-icon { background: linear-gradient(135deg, #909399, #b1b3b8); }
.stat-num { font-size: 24px; font-weight: 700; color: #1f2d3d; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; }

/* ===== 区域卡片通用 ===== */
.section-card { margin-bottom: 16px; border-radius: 8px; }
.section-card :deep(.el-card__header) { padding: 14px 20px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-title { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 15px; color: #1f2d3d; }
.card-title .el-icon { color: #409eff; font-size: 16px; }
.perm-header-right { display: flex; align-items: center; gap: 8px; }

/* ===== 角色网格 ===== */
.role-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.role-item {
  display: flex; align-items: center; gap: 10px; padding: 12px 14px;
  border-radius: 8px; background: #f5f7fa; border: 1px solid #ebeef5; transition: all 0.2s;
}
.role-item:hover { border-color: #409eff; background: #ecf5ff; }
.role-icon {
  width: 32px; height: 32px; border-radius: 6px; flex-shrink: 0;
  background: #e1f3d8; color: #67c23a;
  display: flex; align-items: center; justify-content: center; font-size: 16px;
}
.role-name { font-size: 14px; font-weight: 500; color: #303133; }

/* ===== 权限目录树 ===== */
.perm-tree-wrap { padding: 4px 0; }
.perm-admin-notice { padding: 8px 0; }
.perm-toolbar { display: flex; gap: 4px; margin-bottom: 8px; padding-left: 4px; }
.perm-tree { max-height: 480px; overflow-y: auto; }
.perm-tree :deep(.el-tree-node__content) { height: 34px; }
.perm-tree-node { display: flex; align-items: center; gap: 6px; flex: 1; }
.perm-tree-label { font-size: 14px; color: #303133; }
.perm-tree-code {
  font-size: 11px; color: #c0c4cc; font-family: 'Consolas', 'Monaco', monospace;
  background: #f5f7fa; padding: 1px 6px; border-radius: 3px;
}
.perm-tree :deep(.el-tree-node__content:hover) { background: #f5f7fa; }
.perm-tree :deep(.el-tree-node.is-current > .el-tree-node__content) { background: #ecf5ff; }

/* ===== 待办列表 ===== */
.todo-card :deep(.el-card__body) { padding: 0; }
.todo-list { max-height: 480px; overflow-y: auto; }
.todo-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; border-bottom: 1px solid #f0f0f0; cursor: pointer; transition: background 0.2s;
}
.todo-item:hover { background: #f5f7fa; }
.todo-item:last-child { border-bottom: none; }
.todo-left { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.todo-priority { width: 4px; height: 36px; border-radius: 2px; flex-shrink: 0; }
.todo-priority.urgent { background: #f56c6c; }
.todo-priority.warning { background: #e6a23c; }
.todo-priority.normal { background: #409eff; }
.todo-content { flex: 1; min-width: 0; }
.todo-title {
  font-size: 14px; font-weight: 500; color: #303133;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 4px;
}
.todo-sub { display: flex; align-items: center; gap: 8px; }
.todo-wait { font-size: 12px; color: #c0c4cc; }

/* ===== 响应式 ===== */
@media (max-width: 767px) {
  .profile { padding: 10px; }
  .user-header { gap: 12px; }
  .avatar { width: 48px; height: 48px; font-size: 22px; }
  .user-name { font-size: 17px; }
  .user-sub-line { gap: 10px; font-size: 12px; }
  .user-actions { width: 100%; justify-content: flex-start; }
  .stat-card { padding: 12px; }
  .stat-num { font-size: 20px; }
  .role-grid { grid-template-columns: 1fr; }
}
</style>
