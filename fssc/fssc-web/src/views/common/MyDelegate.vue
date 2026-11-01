<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="my-delegate-page">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <div>
            <span class="panel-title">我的委托代理</span>
            <span class="panel-hint">出差/休假期间，可把待办审批临时委托给他人代为处理</span>
          </div>
          <div class="panel-actions">
            <!-- E-12/服务端已按身份过滤，这里只是切换"我委托的 / 由我代办"两个视角。
                 「全部」是无公司维度的集团级账号才有的一档。 -->
            <el-radio-group v-model="scope" size="small" @change="onScopeChange">
              <el-radio-button value="mine">我委托的</el-radio-button>
              <el-radio-button value="agent">由我代办</el-radio-button>
              <el-radio-button v-if="isGroupLevel" value="all">全部</el-radio-button>
            </el-radio-group>
            <el-button v-if="canCreate" type="primary" size="small" @click="openDialog">新增委托</el-button>
          </div>
        </div>
      </template>

      <!-- 无查询权限：明确指引，而不是发请求后弹「缺少权限」 -->
      <el-empty
        v-if="!canList"
        description="当前账号没有委托代理查询权限，请联系管理员开通"
        :image-size="80"
      />
      <template v-else>
        <SkeletonList v-if="loading" :rows="6" :cols="6" />
        <template v-else>
          <el-table :data="delegates" size="small" stripe @row-dblclick="showRowDetail">
            <el-table-column prop="fromUserName" label="委托人" width="130" />
            <el-table-column prop="toUserName" label="代理人" width="130" />
            <el-table-column prop="bizTypes" label="适用单据" min-width="170">
              <template #default="{ row }">
                <span>{{ bizTypesText(row.bizTypes) }}</span>
              </template>
            </el-table-column>
            <!-- E-12/后端给的是 LocalDateTime 原样串（2026-12-25T09:00:00），
                 直接铺在列上不可读，统一走 formatDateTime（YYYY-MM-DD HH:mm） -->
            <el-table-column prop="beginTime" label="开始时间" width="150"
                             :formatter="(row, col) => formatDateTime(row[col.property])" />
            <el-table-column prop="endTime" label="结束时间" width="150"
                             :formatter="(row, col) => formatDateTime(row[col.property])" />
            <el-table-column prop="status" label="状态" width="96">
              <template #default="{ row }">
                <el-tag size="small" :type="effectiveStatus(row).type">
                  {{ effectiveStatus(row).text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canCancel(row)" link type="danger" size="small" @click="cancelDelegate(row)">取消</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无委托记录" :image-size="70" />
            </template>
          </el-table>
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pager"
            @size-change="onSizeChange"
            @current-change="onCurrentChange"
          />
        </template>
      </template>
    </el-card>

    <!-- 新增委托 -->
    <el-dialog v-model="dialog.visible" title="新增委托代理" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="delegateRules" label-width="120px" size="default">
        <el-form-item label="委托人">
          <!-- 后端硬校验「委托人必须是当前登录用户」（WfDelegateService.create，越权修复），
               故这里是系统带出的只读值，不是人工填项 ⇒ 按 Q1 规则不加红星 -->
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>
        <el-form-item label="代理人" prop="toUserId">
          <!-- 默认只加载同公司人员，解决全量加载的性能与安全问题 -->
          <UserSelect v-model="dialog.form.toUserId" scope="same-company" show-dept />
        </el-form-item>
        <el-form-item label="适用单据类型">
          <el-input v-model="dialog.form.bizTypes" placeholder="逗号分隔，空=全部。如 EXP_CLAIM,EXP_LOAN" />
        </el-form-item>
        <el-form-item label="开始时间" prop="beginTime">
          <!-- 后端 Jackson 只认 ISO 'T' 分隔，空格格式 500「系统异常」 -->
          <el-date-picker v-model="dialog.form.beginTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="dialog.form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="委托原因">
          <el-input v-model="dialog.form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label=" ">
          <div style="font-size:12px;color:#909399;line-height:18px">
            只能委托<b>同公司</b>同事：跨公司委托默认被拒（代办会让外公司的人出现在本公司单据的审批记录上），
            确需放行须由管理员在参数配置中设置
            <code>delegate.cross_company.whitelist</code>。委托生效期内代理人可代为办理你的待办
            （两边列表都能看到，代理人侧标注「代 X 办理」、你侧标注「已委托」），建立后代理人会收到站内通知，到期自动失效。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 我的委托代理（权限治理新增）
 *
 * 背景：委托代理（wf:delegate:*）本是员工自助功能，但原先只作为
 * 「流程管理」(/expense/flow, 菜单 104) 的一个页签存在，而菜单 104
 * 位于「系统管理 → 系统配置」域下且被误授给 COMMON_EMP（普通员工）。
 * 权限审计将该菜单从全员基础角色收回（流程建模属高危管理功能），
 * 为不使普通员工丢失委托代理自助能力，特拆出本独立页面。
 *
 * 权限：wf:delegate:list 查询 / wf:delegate:create 新增 / wf:delegate:update 取消
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flowApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { billTypeLabel } from '@/constants/enums.js'
import { hasPerm } from '@/utils/perm'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'

const canList = hasPerm('wf:delegate:list')
const canCreate = hasPerm('wf:delegate:create')
const canUpdate = hasPerm('wf:delegate:update')

/** 适用单据是多选拼接串（如 EXP_CLAIM,EXP_LOAN），逐段换中文；空=全部单据 */
function bizTypesText(v) {
  const codes = String(v || '').split(',').map(s => s.trim()).filter(Boolean)
  return codes.length ? codes.map(c => billTypeLabel(c)).join('、') : '全部单据'
}

/**
 * 委托的「真实状态」——不能只看 status 列。
 *
 * 后端生效判定看的是时间区间（`begin_time <= now <= end_time`，见 WfDelegateService.activeDelegateTo）；
 * `status` 列只记录「人工取消 DISABLED / 到期回收 EXPIRED」，而到期回收由 WfDelegateExpireJob
 * 每 10 分钟刷一次 ⇒ 存在最长 10 分钟的窗口：行仍是 NORMAL 却早已失效。
 * 若直接按 status 显示「启用」，用户会误以为还能继续代办。这里按时间就地算出真实态，与后端同一规则。
 */
function effectiveStatus(row) {
  const s = String(row?.status || '').toUpperCase()
  if (s === 'DISABLED') return { text: '已停用', type: 'danger' }
  if (s === 'EXPIRED') return { text: '已过期', type: 'info' }
  const parse = (v) => (v ? new Date(String(v).replace(' ', 'T')).getTime() : null)
  const begin = parse(row?.beginTime)
  const end = parse(row?.endTime)
  const now = Date.now()
  if (begin && now < begin) return { text: '待生效', type: 'warning' }
  if (end && now > end) return { text: '已过期', type: 'info' }
  return { text: '生效中', type: 'success' }
}

/** 仅 NORMAL 的委托可取消（已过期/已停用再点取消只会拿到 4xx）。
 *  且只有委托人能取消——服务端同一规则（WfDelegateService.cancel 校验 from_user_id），
 *  「由我代办」视角下把别人的委托列出取消按钮，点了只会撞 403。 */
function canCancel(row) {
  return canUpdate && String(row?.status || '').toUpperCase() === 'NORMAL'
    && String(row?.fromUserId) === String(currentUserId.value)
}

const delegates = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
/** 列表视角：mine=我委托的（缺省）/ agent=由我代办 / all=全量（仅集团级账号，服务端拒绝其余人） */
const scope = ref('mine')
const isGroupLevel = computed(() => store.user?.companyId == null)

// 委托契约是用户 ID（WfDelegate.fromUserId/toUserId）：弹窗送 fromUserName/toUserName 的话，
// Jackson 丢弃未知字段 ⇒ userId 恒空，UI 新增委托 100% 被 create 的必填断言拒（见治理文档 §11.6-4）
const currentUserId = computed(() => store.user?.userId ?? store.user?.id ?? null)
const currentUserName = computed(() => store.user?.realName || store.user?.nickName
  || store.user?.username || store.user?.userName || '当前用户')

function blankForm() {
  return { fromUserId: currentUserId.value, toUserId: null, bizTypes: '', beginTime: '', endTime: '', reason: '', status: 'NORMAL' }
}

const dialog = reactive({
  visible: false, saving: false,
  form: blankForm(),
})

const formRef = ref(null)

// 与后端 WfDelegateService.create 同一规则：受托人必填（委托人由系统带出），且生效判定只看时间区间 ⇒ 区间留空必被拒
const delegateRules = {
  toUserId: commonRules.required('请选择代理人'),
  beginTime: commonRules.date('请选择开始时间'),
  endTime: commonRules.date('请选择结束时间'),
}

async function load() {
  if (!canList) return
  loading.value = true
  try {
    const r = await flowApi.delegatePage({ pageNum: pageNum.value, pageSize: pageSize.value, scope: scope.value })
    delegates.value = r?.list || r?.records || []
    total.value = r?.total ?? r?.totalCount ?? delegates.value.length
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onScopeChange() { pageNum.value = 1; load() }

function onSizeChange(v) { pageSize.value = v; pageNum.value = 1; load() }
function onCurrentChange(v) { pageNum.value = v; load() }

function openDialog() {
  Object.assign(dialog, { visible: true, saving: false, form: blankForm() })
}

async function save() {
  if (!(await validateForms([formRef]))) return
  dialog.saving = true
  try {
    await flowApi.delegateCreate({ ...dialog.form })
    ElMessage.success('保存成功')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    dialog.saving = false
  }
}

async function cancelDelegate(row) {
  try {
    await ElMessageBox.confirm('确认取消该委托？', '确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await flowApi.delegateCancel(row.id)
    ElMessage.success('已取消')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.my-delegate-page { min-height: 100%; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-actions { display: flex; align-items: center; gap: 10px; }
.panel-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); }
.panel-hint { margin-left: 10px; font-size: 12px; color: #909399; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
