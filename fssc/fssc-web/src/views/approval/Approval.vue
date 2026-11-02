<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card class="cm-target-table" :header="$t('approval.main.My')">
    <div style="margin-bottom: 8px">
      <el-button type="info" size="small" @click="exportCsv">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      <!-- 批量审批：勾选 ≥1 行启用「批量审批」；未勾选时禁用并给出 tooltip 提示 -->
      <el-tooltip :content="$t('approval.main.Select')" placement="top" :disabled="selRows.length > 0">
        <span style="margin-left: 12px; display: inline-block">
          <el-button type="success" size="small" :loading="batchApproving" :disabled="!selRows.length" @click="batchApprove">
            批量审批({{ selRows.length }})
          </el-button>
        </span>
      </el-tooltip>
    </div>
    <el-table ref="taskTableRef" class="cm-target-table" :data="tasks" stripe v-loading="loading" @selection-change="handleSelectionChange" @expand-change="onExpand" row-key="taskId" @row-dblclick="openTrace">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-steps v-if="getHistory(row).length" :active="getHistory(row).length" align-center finish-status="success" simple>
                <el-step v-for="(h, i) in getHistory(row)" :key="h.id || i" :title="h.nodeName || '—'" :description="`${h.operUserName || ''} ${h.operTime ? h.operTime.substring(5, 16) : ''}`" />
              </el-steps>
              <!-- 无记录时不再画一枚假的「提交」环节：展开区必须如实说明查询状态，
                   否则「一个圆圈＋提交」会被读成"本单刚提交、后面还没人审"。 -->
              <div v-else class="hist-placeholder">
                <span v-if="histStateOf(row) === 'loading'">{{ $t('approval.main.Loading') }}</span>
                <span v-else-if="histStateOf(row) === 'noperm'">{{ $t('approval.main.Current') }}</span>
                <span v-else-if="histStateOf(row) === 'nobill'">{{ $t('approval.main.This') }}</span>
                <template v-else-if="histStateOf(row) === 'fail'">
                  <span>审批轨迹加载失败：{{ histMsgOf(row) }}</span>
                  <el-button link type="primary" size="small" @click="reloadHistory(row)">{{ $t('common.retry') }}</el-button>
                </template>
                <template v-else-if="histStateOf(row) === 'empty'">
                  <span>{{ histEmptyTip(row) }}</span>
                  <el-button link type="primary" size="small" @click="reloadHistory(row)">重取</el-button>
                </template>
                <span v-else>正在加载审批轨迹…</span>
              </div>
              <div v-if="getHistory(row).length" class="history-detail">
                <!-- 耗时摘要 -->
                <div class="duration-summary">
                  <span class="dur-badge">总耗时: {{ getTotalDuration(row) }}</span>
                  <span class="dur-badge">平均: {{ getAvgDuration(row) }}</span>
                  <span v-if="getMaxDurationNode(row)" class="dur-badge warn">
                    最慢: {{ getMaxDurationNode(row).name }} ({{ getMaxDurationNode(row).dur }})
                  </span>
                </div>
                <el-timeline>
                  <el-timeline-item v-for="(h, i) in getHistory(row)" :key="h.id || i" :timestamp="h.operTime" :type="wfActionType(h.action)" placement="top">
                    <span style="font-weight:600">{{ h.nodeName || '—' }}</span>
                    <el-tag :type="wfActionType(h.action)" size="small" style="margin:0 6px">{{ wfActionLabel(h.action) }}</el-tag>
                    <span style="color: var(--app-text-secondary, #606266)">{{ h.operUserName || '—' }}</span>
                    <!-- 节点耗时 -->
                    <el-tag v-if="getNodeDuration(row, i)" size="small" :type="getNodeDurationType(row, i)" effect="plain" style="margin-left: 8px">
                      <el-icon style="margin-right:2px"><Timer /></el-icon>{{ getNodeDuration(row, i) }}
                    </el-tag>
                    <div v-if="h.comment" style="color:#909399;font-size:12px;margin-top:2px">{{ h.comment }}</div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="selection" width="45" />
      <el-table-column label="流程标题" min-width="220" show-overflow-tooltip sortable prop="title">
        <template #default="{ row }">
          <span>{{ row.title || '—' }}</span>
          <!-- 业务单据已被清理 / 无法解析时明确标注，避免审批人对着一行空白盲审 -->
          <el-tag v-if="row.bizResolved === false" size="small" type="warning" effect="plain" style="margin-left:6px">无关联单据</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="单据编号" width="160" sortable prop="billNo">
        <template #default="{ row }">
          <span v-if="row.billNo">{{ row.billNo }}</span>
          <span v-else style="color:#c0c4cc">—</span>
        </template>
      </el-table-column>
      <!-- 待办列表要有「单据类型」列：报销/借款/付款/合同混排，缺这列只能逐行点开才知是什么单 -->
      <el-table-column label="单据类型" width="110" sortable>
        <template #default="{ row }">
          <span>{{ docTypeLabel(row.billType || row.bizType) || row.billType || row.bizType || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="taskName" label="当前节点" width="140"  sortable />
      <el-table-column label="当前在办人" width="180">
        <template #default="{ row }">
          <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
          <!-- 领取成功但列表回读还没追上的窗口期，不能再挂着「待领取」骗人；
               本地动作覆盖层生效期间显示本人＋「列表同步中」，读数追上后自动交回服务端规则。 -->
          <el-tooltip v-else-if="localOpOf(row)?.kind === 'claim'" content="本任务已由你领取成功，列表仍在刷新；可直接点「通过/驳回」办理" placement="top">
            <el-tag size="small" type="success" effect="plain">{{ myName() }}（本人 · 列表刷新中）</el-tag>
          </el-tooltip>
          <el-tag v-else size="small" type="warning" effect="plain">待领取</el-tag>
          <!-- 委托归属（避免"两边都以为是对方在办"）：这行其实是委托人的单子，我作为受托人代他办。
               字段仅在本人有生效委托时下发（缺省不带＝按普通待办渲染）。 -->
          <el-tooltip v-if="row.delegatedFromName" :content="'该单办理人为 ' + row.delegatedFromName + '，现处委托代办期，由你代为办理'" placement="top">
            <el-tag size="small" type="primary" effect="plain" style="margin-left:6px">代 {{ row.delegatedFromName }} 办理</el-tag>
          </el-tooltip>
          <!-- 我名下的单已交人代管：委托人侧看得见，避免误以为"没人办"而重复催办 -->
          <el-tooltip v-if="row.delegatedToNames && row.delegatedToNames.length"
                      :content="'本单已委托给 ' + row.delegatedToNames.join('、') + ' 代管，委托期内由他办理'" placement="top">
            <el-tag size="small" type="success" effect="plain" style="margin-left:6px">已委托</el-tag>
          </el-tooltip>
          <!-- POOL 软锁（共享作业"任务被他人打开处理时其他用户看到锁定状态"）：
               行仍然可见（不藏单），但同池他人不能再点通过/驳回——服务端 canOperate 同一规则硬拦。 -->
          <el-tooltip v-if="row.lockedByOther" content="共享作业任务同时只允许一人办理；如需接办请联系其释放，或由其转办" placement="top">
            <el-tag size="small" type="danger" effect="plain" style="margin-left:6px">{{ row.lockOwnerName || '他人' }} 正在办理</el-tag>
          </el-tooltip>
          <!-- 加签待办标识：这一行是别人临时插的一道审，不是本来的环节。
没有角标时加签人与普通办理人视觉同一，会把自己的"签完回跳"当成"本节点已办结"来理解。
               字段仅在 wf_add_sign 有绑定本任务的进行中记录时下发（缺省不带＝按普通待办行渲染）。 -->
          <el-tooltip v-if="row.addSignTodo" placement="top" :content="addSignTodoTip(row)">
            <el-tag size="small" type="warning" effect="plain" style="margin-left:6px">{{ row.addSignPosition === 'AFTER' ? '后加签' : '前加签' }}</el-tag>
          </el-tooltip>
          <!-- 名单由 wf_node_config 的 ROLE 取人按单据公司范围解析得出（只读展示）。
               运行态实际是"取角色首个持有人"直接指派，不亮出名单就等于让审批人猜单子落在谁手上。 -->
          <el-tooltip v-if="row.candidateCount" :content="'本节点可办理：' + (row.candidateNames || []).join('、')" placement="top">
            <el-tag size="small" type="info" effect="plain" style="margin-left:6px">{{ row.candidateCount }} 人可办</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="前序审批人" width="160">
        <!-- 四种状态如实分列，取不到数不再冒充「首节点」 -->
        <!-- / 本格与审批弹窗的「前序审批」段同源同一规则（prevTrackOf，按时间正序，
             提单/同意/自动通过/驳回四类记录），并补 empty 档：接口零记录 ≠ 本单处于首节点。
             原来「首节点」这五个字既出现在"查询成功"之后，也出现在空页/规则不匹配之后，
             于是三个在办节点一起报「首节点」，而弹窗链上明明列得出前序已办。 -->
        <template #default="{ row }">
          <span v-if="getPrevApprovers(row).length" style="font-size:13px">{{ getPrevApprovers(row).join(' → ') }}</span>
          <el-tooltip v-else-if="histStateOf(row) === 'done'" placement="top"
                      :content="`共 ${historyRawCount[row.billId] || 0} 条审批记录，本节点之前没有人工办理记录（多为系统自动处理）`">
            <span style="color:#c0c4cc;font-size:12px">本节点前无人工办理</span>
          </el-tooltip>
          <el-tooltip v-else-if="histStateOf(row) === 'empty'" placement="top" :content="histEmptyTip(row)">
            <el-button link size="small" type="warning" @click="loadHistory(row, true)">无记录·重新查询</el-button>
          </el-tooltip>
          <span v-else-if="histStateOf(row) === 'loading'" style="color:#909399;font-size:12px">加载中…</span>
          <el-tooltip v-else-if="histStateOf(row) === 'noperm'" placement="top"
                      content="当前账号无审批历史查看权限，暂不能展示轨迹">
            <span style="color:#e6a23c;font-size:12px">无权限查看</span>
          </el-tooltip>
          <el-tooltip v-else-if="histStateOf(row) === 'fail'" placement="top"
                      :content="`审批历史加载失败：${histMsgOf(row) || '系统未返回具体原因'}，点击重试`">
            <el-button link size="small" type="warning" @click="loadHistory(row, true)">加载失败·重试</el-button>
          </el-tooltip>
          <el-tooltip v-else-if="histStateOf(row) === 'nobill'" placement="top"
                      content="该待办未关联到业务单据，无审批历史可取">
            <span style="color:#c0c4cc;font-size:12px">—</span>
          </el-tooltip>
          <el-button v-else link size="small" type="primary" @click="loadHistory(row)">取审批历史</el-button>
        </template>
      </el-table-column>
      <el-table-column label="申请金额" width="120" align="right">
        <template #default="{ row }">{{ row.totalAmount != null ? fmt(row.totalAmount) : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="500">
        <template #default="{ row }">
          <!-- 任务池：无人认领的先领取（锁定到自己名下），已领取的可释放退回任务池。
               一旦被同池他人领取，本行的办理动作全部灰显（显示"XX 正在办理"）——
               共享作业任务同时只允许一人办理；服务端 canOperate 同一规则硬拦，这里只是不让点了才报错。
               「去处理/追溯」不锁：看单据、看轨迹是只读动作。AUTO/PICK 审批节点不加锁（同角色通道是
               自审拦截的既有绕行出口）。 -->
          <el-button v-if="showClaim(row)" type="primary" size="small" v-prevent-double-click @click="claimTask(row)">领取</el-button>
          <!-- 「释放」只在有池证据的节点上出现。后端的 canUnclaim 不区分取人方式
               （AUTO/PICK 指派到我也照样 true），而释放会把节点打回"无人领取"态；
               非池节点要换人请走旁边既有的「转办」/管理员「改派」。 -->
          <el-button v-if="showUnclaim(row)" type="primary" plain size="small" v-prevent-double-click @click="unclaimTask(row)">释放</el-button>
          <!-- 落点被权限校验住的行，按钮直接显示「查看单据」（点开即全链路追溯，不再"处理"个跳不动的页） -->
          <el-button type="info" plain size="small" :disabled="row.bizResolved === false" @click="goBiz(row)">{{ bizLandingBlocked(row) ? '查看单据' : '去处理' }}</el-button>
          <el-button type="success" v-prevent-double-click size="small" :disabled="row.lockedByOther" @click="approve(row, true)">通过</el-button>
          <el-button type="danger" v-prevent-double-click size="small" :disabled="row.lockedByOther" @click="approve(row, false)">驳回</el-button>
          <el-button type="warning" size="small" :disabled="!row.billId || row.lockedByOther" @click="openDeduct(row)">核减</el-button>
          <!-- 转办：改派给其他用户办理（被他人锁定时也禁用——转办权归领取人） -->
          <el-button type="info" size="small" :disabled="row.lockedByOther" @click="openTransfer(row)">转办</el-button>
          <!-- 加签：临时插一道 —— 前加签=加签人先审，后加签=我审完后加签人再审；
               两种都自动回跳、流程走向不变。与转办的区别写在弹窗说明里，避免两者被混用。
               addSignAllowed=false 来自节点配置 allow_addsign=0（接真，需先打开灰度总开关才下发该字段）：
               服务端同一份规则也会拒绝，这里隐藏按钮是为了不让人点了才知道不行。 -->
          <el-button v-if="row.addSignAllowed !== false" type="warning" plain size="small"
                     :disabled="row.lockedByOther" @click="openAddSign(row)">加签</el-button>
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
    </el-table>

    <!-- 服务端分页：数据量增长后一次渲染上百条会卡死 -->
    <el-pagination
      v-if="total > 0"
      class="approval-pager"
      background
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="onPage"
      @size-change="onSize"
    />

    <!-- 核定金额三层：审批核减弹层 -->
    <el-dialog v-model="deductVisible" title="审批核减（核定三层）" width="520px">
      <el-form ref="deductFormRef" :model="deduct" :rules="deductRules" label-width="110px" v-if="deductVisible">
        <el-form-item label="单据编号">
          <span>{{ cur.billNo || '-' }}</span>
        </el-form-item>
        <el-form-item label="申请金额">
          <span style="color:#f59a23;font-weight:700">{{ fmt(cur.totalAmount) }} 元</span>
        </el-form-item>
<el-form-item label="核定报销" prop="approvedAmount">

          <el-input-number v-model="deduct.approvedAmount" :min="0" :precision="2" :max="Number(cur.totalAmount || 0)" style="width:100%" @change="autoRefund" />
          <div style="font-size:12px;color:#909399">不得超出申请金额（审批只能核减，不能核增）</div>
        </el-form-item>
<el-form-item label="核定付款" prop="approvedPayAmount">

          <el-input-number v-model="deduct.approvedPayAmount" :min="0" :precision="2" style="width:100%" @change="autoRefund" />
          <div style="font-size:12px;color:#909399">不得超出核定报销金额</div>
        </el-form-item>
<el-form-item label="核定退款" prop="approvedRefundAmount">

          <el-input-number v-model="deduct.approvedRefundAmount" :min="0" :precision="2" style="width:100%" />
          <div style="font-size:12px;color:#909399">= 核定报销 − 核定付款（改金额时自动重算）</div>
        </el-form-item>
<el-form-item label="核减说明" prop="remark">

          <el-input v-model="deduct.remark" type="textarea" :rows="2" placeholder="如：超标部分不予报销" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deductVisible = false">取消</el-button>
        <el-button type="primary" v-prevent-double-click @click="submitDeduct">确定核减</el-button>
      </template>
    </el-dialog>

    <!-- 转办弹窗：被转办人只能从本节点可办理名单中选（与后端 /transfer 目标校验同一规则，20260922 拆层乙） -->
    <el-dialog v-model="transferVisible" title="转办（改派办理人）" width="460px">
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferFormRules" label-width="100px">
        <el-form-item label="待办任务">
          <span style="color: var(--app-text-secondary, #606266)">{{ transferRow.title || transferRow.billNo || ('任务 ' + (transferRow.taskId || '')) }}</span>
        </el-form-item>
        <el-form-item label="转办给" prop="target">
          <el-select v-model="transferForm.target" filterable :loading="transferCandLoading"
            placeholder="从本节点可办理名单中选择" style="width:100%" @change="onTransferPick">
            <el-option v-for="c in transferCandidates" :key="c.userId" :value="c.userId"
              :label="c.name + (c.deptName ? ' · ' + c.deptName : '') + (c.pendingCount != null ? ' · 在办' + c.pendingCount + '条' : '')" />
          </el-select>
          <div v-if="!transferCandLoading && transferCandEmpty" style="font-size:12px;color:#e6a23c">
            本节点没有其他人可办理，请联系流程管理员在「审批流程看板」使用改派处理。
          </div>
        </el-form-item>
        <el-form-item label="转办说明">
          <el-input v-model="transferComment" type="textarea" :rows="2" placeholder="转办原因（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" v-prevent-double-click @click="submitTransfer">确认转办</el-button>
      </template>
    </el-dialog>

    <!-- 加签弹窗：前加签=加签人先审，后加签=我审完后加签人再审；两种都自动回跳 -->
    <el-dialog v-model="addSignVisible" title="加签（临时增加审批人）" width="540px">
      <el-form ref="addSignFormRef" :model="addSignForm" :rules="addSignFormRules" label-width="100px">
        <el-form-item label="待办任务">
          <span style="color: var(--app-text-secondary, #606266)">{{ addSignRow.title || addSignRow.billNo || ('任务 ' + (addSignRow.taskId || '')) }}</span>
        </el-form-item>
        <el-form-item label="加签位置" prop="position">
          <el-radio-group v-model="addSignForm.position">
            <el-radio label="BEFORE">前加签（加签人先审）</el-radio>
            <el-radio label="AFTER">后加签（我审完后加签人再审）</el-radio>
          </el-radio-group>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:4px">
            加签人审完<b>自动回跳</b>——前加签回到我、后加签交给下一节点本应办理的人，<b>流程走向不变</b>；
            加签人的「同意」只代表这一道审完，不会把单据往前推。可多选，按选择顺序<b>依次</b>加签。
            同一任务同时只允许一组加签，已有进行中的加签需先等它走完或取消。
          </div>
        </el-form-item>
        <el-form-item label="加签给" prop="signUsers">
          <el-select v-model="addSignForm.signUsers" multiple filterable :loading="addSignCandLoading"
            placeholder="从本节点可办理名单中选择（可多选）" style="width:100%">
            <el-option v-for="c in addSignCandidates" :key="c.userId" :value="c.userId"
              :label="(c.name || c.userName || c.userId) + (c.deptName ? ' · ' + c.deptName : '')" />
          </el-select>
          <div v-if="!addSignCandLoading && addSignCandEmpty" style="font-size:12px;color:#e6a23c">
            本节点没有其他可办理人，无法加签。
          </div>
        </el-form-item>
        <el-form-item label="加签原因">
          <el-input v-model="addSignForm.reason" type="textarea" :rows="2" placeholder="加签原因（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSignVisible = false">取消</el-button>
        <el-button type="primary" v-prevent-double-click @click="submitAddSign">确认加签</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <!-- 去处理直达单据详情抽屉（附件预览 / OCR 结果 / 审批历史），行内操作与抽屉内操作共用同一套审批逻辑 -->
  <ApprovalDetailDrawer
    v-model="detailVisible"
    :row="cur"
    @approve="(approved) => approve(cur, approved)"
    @deduct="openDeductFromDrawer"
  />

  <CoachMark ref="approvalCm" :selector="'.cm-target-table'" title="审批列表" text="审批列表展示所有待审批单据，点击行可查看详情并执行审批。" />
  <ApprovalDialog ref="approvalDlg" @detail="openDetailFromDialog" />
</template>

<script setup>

import { onMounted, ref, reactive, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import { wfApi, billApi, flowApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import ApprovalDetailDrawer from '../../components/approval/ApprovalDetailDrawer.vue'
import ApprovalDialog from '../../components/approval/ApprovalDialog.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { wfActionLabel as _wfActionLabel, wfActionType, docTypeLabel } from '@/constants/enums.js'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useRouter } from 'vue-router'
import { goBizPage, normalizeBillType, resolveBillRoute } from '../../utils/bizEntry'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => load(),
})
const approvalCm = ref(null)
/** 当前操作行：去处理（详情抽屉）/ 核减（弹层）共用 */
const cur = ref({})
const router = useRouter()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.billId || row.bill_id || row.id
  traceBizType.value = row.billType || null
  traceVisible.value = true
}

// 去处理直达单据详情抽屉
const detailVisible = ref(false)
/** 共享审批弹窗（单据摘要 + 必填意见），当前审批行供「查看单据详情」回查 */
const approvalDlg = ref(null)
const approvalRow = ref({})

/** 报销类单据走详情抽屉，其余单据类型跳各自业务模块 */
function billRouteOf(row) {
  return resolveBillRoute(normalizeBillType(row?.billType))
}
function isExpenseBill(row) {
  const route = billRouteOf(row)
  return route === '/expense/my' || route === '/expense/apply'
}

/**
 * 审批落点校验。发票池（#/invoice/query）的数据面按 inv:list 门控，
 * 无该权限的审批岗点「去处理」会被丢进整页「暂无数据」＋一串「无该功能/按钮权限」报错——
 * 财务审核岗（FIN_AUDIT）与 CFO 这两个角色都不持 inv:list，"审核岗看不到发票"只能回列表用行内快捷办理。
 * 无权限时不再跳被校验住的业务页，改落全链路追溯（只读，exp:trace:list 审批岗在持）；
 * 待 sys_role_menu 把 inv:list 授给这两个角色并重新登录（权限烤进 JWT）后，
 * 本校验自动放行、按钮文案回到「去处理」。
 */
const canInvPool = hasPerm('inv:list')
function bizLandingBlocked(row) {
  return billRouteOf(row) === '/invoice' && !canInvPool
}

function openDetailFromDialog() {
  cur.value = approvalRow.value || {}
  detailVisible.value = true
  detailVisible2.value = false
}

/**
 * 「去处理」：按单据类型分发。
 *  - 报销类单据（CLAIM/APPLY/LOAN/REPAY/PAY，落 /expense/my 或 /expense/apply）：
 *    直接打开单据详情抽屉（单据信息/明细/附件与识别/审批历史），而非把审批人丢到业务模块的列表页。
 *  - 其他单据类型（发票减值 INV_*、固定资产、合同、资产、应收等）：按 bizType 分发到对应业务模块页，
 *    避免用报销单详情去查非报销单（历史缺陷：把发票减值单当报销单打开 → 误报「单据不存在」）。
 *  - 无法解析 / 业务行已被清理（bizResolved=false）：退化为打开全链路联查并明确告知。
 */
function goBiz(row) {
  if (row?.bizResolved === false || row?.billId == null) {
    ElMessage.warning(t('approval.main.This2'))
    openTrace(row)
    return
  }
  const isExpense = isExpenseBill(row)
  if (isExpense) {
    cur.value = row
    detailVisible.value = true
    return
  }
  // 落点是发票池而本账号无 inv:list ⇒ 不跳校验页，落全链路追溯
  if (bizLandingBlocked(row)) {
    ElMessage.warning(t('approval.main.Current2'))
    openTrace(row)
    return
  }
  if (billRouteOf(row) && goBizPage(router, row)) {
    return
  }
  ElMessage.warning(t('approval.main.Current3'))
  openTrace(row)
}

const selRows = ref([])
const tasks = ref([])
const loading = ref(false)
// 服务端分页状态（不一次性拉取全部待办）
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const deductVisible = ref(false)
const deduct = reactive({ approvedAmount: 0, approvedPayAmount: 0, approvedRefundAmount: 0, remark: '' })

const deductRules = reactive({
  approvedAmount: [{ required: true, message: t('approval.main.Please') }],
  approvedPayAmount: [{ required: true, message: t('approval.main.Please2') }],
  approvedRefundAmount: [{ required: true, message: t('approval.main.Please3') }],
})

// 审批历史缓存：{ [billId]: WfTaskHistory[] }
// 缓存键只到 billId、未带 bizType ⇒ 跨单据域的同值主键会串到同一份历史，键本身不安全。
// 键规则暂不动，改为用 historyOwner 记住"这份值是替哪种 bizType 取的"，
// 域不一致时按"本行未查询"处理（宁可让人多点一次「取审批历史」，也不拿别的单据域的空结果冒充「首节点」）。
const historyMap = reactive({})
const historyOwner = reactive({})
/**
 * 查询状态与缓存分开记。否则"没取到"和"没有记录"在界面上同形（都落进「首节点」），
 * 而 /wf/query/history/page 对业务岗回 HTTP 200 + 4030，请求又被 silent 吞掉，
 * 于是"前序审批人恒显首节点""审批轨迹恒为空"。状态如实区分：
 *   loading 查询中 / done 取到了（可为空数组）/ noperm 本账号无 wf:history:list / fail 请求失败
 * 另加一档 empty —— 接口正常返回但一条记录都没有。在途单不可能没有记录
 * 提单就是一条，空返回只可能是查询范围/权限问题，绝不能包装成「本单处于首节点」这条业务结论。
 */
const historyState = reactive({})
const historyMsg = reactive({})
/** 原始返回行数（未按动作码筛选）：用来把"0 条记录"与"有记录但都不是人工办理"分清楚 */
const historyRawCount = reactive({})
const canViewHistory = hasPerm('wf:history:list')

// 审批动作 → 中文/类型（名称表办结到 constants/enums，与单据详情面板、H5 审批历史共用）
function wfActionLabel(a) { return a ? _wfActionLabel(a) : '—' }

/**
 * 加签待办的说明文案：把"这一道是什么、签完去哪"一次说清。
 * 语义与后端加签回跳钩子一致——加签人的「通过」只代表加签这一道审完，
 * 任务交还原办理人/下一位加签人，流程不往前推（区别于转办＝受理人永久变更）。
 */
function addSignTodoTip(row) {
  const from = row.addSignFromName ? `由 ${row.addSignFromName} 发起` : '由前序办理人发起'
  const way = row.addSignPosition === 'AFTER'
    ? '后加签：本环节原定办理人已签过，你是补加的一道审，签完流程继续往下走'
    : '前加签：任务被临时改派给你先审，签完交还原办理人，流程不推进'
  const reason = row.addSignReason ? `；加签原因：${row.addSignReason}` : ''
  return `${from}的加签待办。${way}${reason}`
}

/** 当前登录人显示名（领取成功后就地回填，不等列表刷新） */
const myName = () => store.user?.realName || store.user?.nickName
  || store.user?.username || store.user?.userName || '我'

/** 本行缓存是否可用（同 billId 不同单据域 ⇒ 视为未查询） */
function histOwned(row) {
  const k = row?.billId
  if (k == null || historyMap[k] === undefined) return false
  return (historyOwner[k] || '') === (row.billType || '')
}

/**
 * 后端 /wf/query/history/page 返回的是「最新在前」（WfQueryService.historyPage 的
 * orderByDesc(id) ＋ causalOrder 把实例定论行前置），而审批链、进度面板、展开行的耗时
 * 差分都是按"事情发生的先后"读的。这里统一翻正一次，三处同源，不再各排各的。
 * （顺带治好 节点耗时：以前 list[i+1] 取到的是更早一条，差值恒为负，
 *  fmtDuration 见负即返回空串 ⇒ 耗时徽标与总耗时从来不显示。）
 */
function historyAsc(list) {
  const t = (h) => { const d = parseTime(h?.operTime); return d ? d.getTime() : Number.MAX_SAFE_INTEGER }
  return [...(Array.isArray(list) ? list : [])]
    .sort((a, b) => (t(a) - t(b)) || (Number(a?.id || 0) - Number(b?.id || 0)))
}

// 获取某行的审批历史（时间正序；本行未取到数时为空数组）
function getHistory(row) {
  if (!histOwned(row)) return []
  return historyAsc(historyMap[row.billId])
}

/**
 * 「前序审批人」列与审批弹窗的「前序审批」段必须同源同一规则。
 * 三份 filter 各写各的就会各给一个答案 —— 列取 APPROVE|SUBMIT|AUTO_PASS（看不见驳回）、弹窗取
 * APPROVE|REJECT|AUTO_PASS（看不见提单）、展开行取全量且是最新在前；同一张单三处三种答案。
 * 故只留这一份：节点办理记录四类动作，按时间正序，列与弹窗都读它。
 * 不含 APPROVED/REJECTED —— 那是实例级定论行，记的是整单结论不是某一节。
 */
const NODE_HANDLE_CODES = ['SUBMIT', 'APPROVE', 'AUTO_PASS', 'REJECT']
function prevTrackOf(row) {
  return getHistory(row)
    .filter((h) => NODE_HANDLE_CODES.includes(h.action))
    .map((h) => ({
      node: h.nodeName,
      assignee: h.operUserName,
      approved: h.action !== 'REJECT',
      comment: h.comment,
      action: h.action,
    }))
}

function getPrevApprovers(row) {
  return prevTrackOf(row).map((t) => t.assignee).filter(Boolean)
}

/**
 * 「前序审批人」这一格到底处于哪种状态，界面必须能自证。
 *   nobill 该行没关联到业务单据 / noperm 账号无 wf:history:list / loading 查询中
 *   done 取到了（有记录、只是没有人工办理行） / empty 接口空返回（规则或权限问题，不是首节点）
 *   fail 查询失败（原因见 histMsgOf） / '' 尚未查询
 * 关键是把 done+空 与"没取到"分开：后者从前一律显示「首节点」，等于把权限缺陷与网络缺陷
 * 包装成一条假的业务结论。
 */
function histStateOf(row) {
  const k = row?.billId
  if (k == null) return 'nobill'
  if (!histOwned(row)) {
    if (historyState[k] === 'loading') return 'loading'
    if (!canViewHistory) return 'noperm'
    return ''
  }
  if (historyState[k]) return historyState[k]
  if (!canViewHistory) return 'noperm'
  return ''
}
function histMsgOf(row) {
  return row?.billId != null ? (historyMsg[row.billId] || '') : ''
}
/** 空返回时的说明：只报业务单号，不暴露内部查询规则 */
function histEmptyTip(row) {
  return `按单号「${row.billNo || '—'}」查询审批历史时没有返回记录。`
    + '在途单据至少应有一条提交记录，未返回通常是查询范围或权限问题，'
    + '不代表本单还在第一个节点。点击可重新查询。'
}

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 审批节点耗时分析
function parseTime(s) {
  if (!s) return null
  const d = new Date(s.replace(/-/g, '/'))
  return isNaN(d.getTime()) ? null : d
}
function fmtDuration(ms) {
  if (!ms || ms < 0) return ''
  const m = Math.floor(ms / 60000)
  if (m < 60) return m + '分钟'
  const h = Math.floor(m / 60)
  if (h < 24) return h + '小时' + (m % 60) + '分'
  const d = Math.floor(h / 24)
  return d + '天' + (h % 24) + '小时'
}
function getNodeDuration(row, index) {
  const list = getHistory(row)
  if (index >= list.length - 1) return '' // 最后一项无耗时
  const cur = parseTime(list[index].operTime)
  const next = parseTime(list[index + 1]?.operTime)
  if (!cur || !next) return ''
  return fmtDuration(next - cur)
}
function getNodeDurationMs(row, index) {
  const list = getHistory(row)
  if (index >= list.length - 1) return 0
  const cur = parseTime(list[index].operTime)
  const next = parseTime(list[index + 1]?.operTime)
  if (!cur || !next) return 0
  return next - cur
}
function getNodeDurationType(row, index) {
  const ms = getNodeDurationMs(row, index)
  if (ms > 8 * 3600000) return 'danger' // > 8小时
  if (ms > 2 * 3600000) return 'warning' // > 2小时
  return 'success'
}
function getTotalDuration(row) {
  const list = getHistory(row)
  if (list.length < 2) return '-'
  const first = parseTime(list[0].operTime)
  const last = parseTime(list[list.length - 1].operTime)
  if (!first || !last) return '-'
  return fmtDuration(last - first)
}
function getAvgDuration(row) {
  const list = getHistory(row)
  if (list.length < 2) return '-'
  const first = parseTime(list[0].operTime)
  const last = parseTime(list[list.length - 1].operTime)
  if (!first || !last) return '-'
  const avg = (last - first) / (list.length - 1)
  return fmtDuration(avg)
}
function getMaxDurationNode(row) {
  const list = getHistory(row)
  if (list.length < 2) return null
  let maxMs = 0, maxIdx = -1
  for (let i = 0; i < list.length - 1; i++) {
    const ms = getNodeDurationMs(row, i)
    if (ms > maxMs) { maxMs = ms; maxIdx = i }
  }
  if (maxIdx < 0) return null
  return { name: list[maxIdx].nodeName || '节点' + (maxIdx + 1), dur: fmtDuration(maxMs) }
}

/**
 * 任务池领取/释放的本地状态。
 *   poolArrived taskId→true：该行进列表时即无人态（=任务池节点，唯一可自证的池证据）
 *   localOp taskId→{kind:'claim'|'unclaim'}：本人刚发过且已成功的动作，服务端回读滞后时用来兜底
 * 领取接口已返回成功、提示语也弹了，同一行仍显示「待领取／N 人可办」，要再手动刷新一次
 * 才见「本人／释放」⇒ 界面把一次成功的操作说成没发生。这里既 await 刷新，
 * 也在刷新结果还没追上时如实标「列表同步中」。
 */
const poolArrived = reactive({})
const localOp = reactive({})
function localOpOf(row) { return row?.taskId != null ? localOp[String(row.taskId)] : null }

/**
 * 本行是否任务池节点（决定「释放」是否出现）。
 * 后端 enrichHandlers 的 canUnclaim 只看「办理人是不是我」（WfTaskController），
 * 而 WfEngineFacade.unclaim 也只校验"是不是自己领的"、不校验池属性 ⇒ AUTO/PICK 节点指派到
 * 我名下后照样回 canUnclaim=true，界面上就多出一颗「释放」；真点下去会把任务退回候选态，
 * 凭空造出一个"无人领取"的节点（后端 unclaim 里还有一处释放后清空 currentAssignee 的补丁）。
 * 后端补 poolTask 字段之前，先在前端办结：无池证据就不给这颗按钮。
 */
function isPoolRow(row) {
  if (!row) return false
  if (row.poolTask === true) return true // 后端补字段后即采信，前端不用再猜
  const t = row.taskId != null ? String(row.taskId) : null
  if (t && (poolArrived[t] || localOp[t]?.kind === 'claim')) return true
  if (row.lockedByOther) return true    // 该标记由 poolLockedByOthers 下发，只可能是池节点
  return !row.assigneeId
}
function showClaim(row) {
  const op = localOpOf(row)
  if (op?.kind === 'claim') return false
  if (op?.kind === 'unclaim') return true
  return !!row.canClaim
}
function showUnclaim(row) {
  const op = localOpOf(row)
  if (op?.kind === 'unclaim') return false
  if (op?.kind === 'claim') return isPoolRow(row)
  return !!row.canUnclaim && isPoolRow(row)
}

async function load() {
  loading.value = true
  try {
    const res = await wfApi.todoPage({ pageNum: pageNum.value, pageSize: pageSize.value })
    const rows = res?.list || []
    // 待办行里没有 handlingMode/assignType 可用，唯一能自证"这是任务池节点"的
    // 服务端事实是「进列表时没有办理人」——Flowable 侧只有候选组，指派发生在领取那一刻
    // （WfTaskController 注释同一规则）。先记下来，领取之后仍据此决定「释放」是否出现。
    rows.forEach((r) => { if (r.taskId != null && !r.assigneeId) poolArrived[r.taskId] = true })
    // 服务端读数已经追上本人的领取/释放动作 ⇒ 撤掉本地覆盖层，徽标交回服务端说了算
    Object.keys(localOp).forEach((t) => {
      const r = rows.find((x) => String(x.taskId) === String(t))
      if (!r) { delete localOp[t]; return }
      if (localOp[t].kind === 'claim' && r.assigneeId) delete localOp[t]
      if (localOp[t].kind === 'unclaim' && !r.assigneeId) delete localOp[t]
    })
    tasks.value = rows
    total.value = Number(res?.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 翻页 */
function onPage(p) {
  pageNum.value = p
  load()
}
/** 每页条数变化 */
function onSize(s) {
  pageSize.value = s
  pageNum.value = 1
  load()
}

/**
 * 展开行时才加载该单的审批历史（懒加载 + 缓存）。
 * 原来在 load() 里对每条待办都发一次请求 = N+1，数据量一大直接打爆浏览器。
 */
function onExpand(row, expandedRows) {
  const expanded = Array.isArray(expandedRows)
    ? expandedRows.some((r) => r.taskId === row.taskId)
    : true
  if (!expanded) return
  // 判断"该行是否已有可用历史"要连同单据域一起看（histOwned），
  // 同 billId 的另一域单据先取过数时，本行仍需自己取一次，不能沿用别人的空结果。
  if (row.billId != null && !histOwned(row)) {
    loadHistory(row)
  }
}

/** 展开区「重试/重取」：失败与空返回都允许按当前规则强制重拉 */
function reloadHistory(row) {
  loadHistory(row, true)
}

async function loadHistory(row, force) {
  const k = row.billId
  if (k == null) return
  // 无权限不再发请求（请求了也只会被服务端 4030 拒），但界面要如实说"无权限"，
  // 而不是把这格降级成「首节点」骗人。
  if (!canViewHistory) {
    historyOwner[k] = row.billType || ''
    historyState[k] = 'noperm'; historyMsg[k] = '当前账号没有查看审批历史的权限'; return
  }
  if (!force && histOwned(row)) return
  historyMap[k] = [] // 占位，避免重复请求
  historyOwner[k] = row.billType || ''
  historyState[k] = 'loading'
  historyMsg[k] = ''
  historyRawCount[k] = 0
  try {
    // 真因办结：从前这里把 row.billType（取自流程变量 bizType）原样当过滤条件，
    // 而 wf_task_history.biz_type 的运行期真值可能带 EXP_ 前缀一类的域前缀，服务端按
    // (bizType,bizId) 硬筛就筛成空页（HTTP 200 + 空 list，WfQueryController:89-90 自己写明了这个坑）。
    // 前端接着把"空页"读成"没有前序环节" ⇒ 三个在办节点齐刷刷显示「首节点」，而弹窗的审批链
    // 走的是流程实例维度、照常列得出前序已办，两处于是互相打脸。
    // 改走 bizNo 维度：控制器按单号解析实例后成对 (biz_type,biz_id) 查询，与前端传的域字符串脱钩。
    const params = { pageNum: 1, pageSize: 100, bizId: k }
    if (row.billNo) params.bizNo = row.billNo
    else params.bizType = row.billType
    // silent：审批历史属辅助信息，不弹全局错误；但失败原因必须回写状态，界面如实显示
    const res = await flowApi.historyPage(params, { silent: true })
    const list = res?.list || res?.records || []
    historyMap[k] = list
    historyOwner[k] = row.billType || ''
    historyRawCount[k] = Number(res?.total ?? list.length)
    historyState[k] = list.length ? 'done' : 'empty'
  } catch (e) {
    historyMap[k] = []
    historyOwner[k] = row.billType || ''
    historyState[k] = 'fail'
    historyMsg[k] = e?.message || '系统未返回具体原因'
  }
}

/**
 * / 弹窗的「前序审批」段与列表那一格同源同一规则（都读 prevTrackOf），
 * 且开弹窗前先把数取到手 —— 从前只靠"该行恰好被展开过"，没展开就整段不显示，
 * 审批人对着空弹窗只能靠链上的节点名猜前面谁办过。silent 查询，取不到就维持不显示。
 */
function historyForDialog(row) {
  return prevTrackOf(row)
}

async function ensureHistoryFor(row) {
  if (row?.billId == null || !canViewHistory) return
  if (histOwned(row) && historyState[row.billId] !== 'fail') return
  try { await loadHistory(row, historyState[row?.billId] === 'fail') } catch { /* silent 已吞 */ }
}

async function approve(row, approved) {
  if (!row?.taskId) { ElMessage.warning(t('approval.main.This3')); return }
  if (!approvalDlg.value) { ElMessage.error(t('useApprovalDialog.main.Approval')); return }
  // 审批意见必经输入框且必填（合规整改：不留默认「同意/不同意」空意见，取消则不执行）。
  // 弹窗在此之上补齐单据摘要与前序审批，审批人无需回列表展开行翻详情。
  approvalRow.value = row
  detailVisible.value = false // 弹窗与抽屉互斥，避免双审批入口同屏
  // 开弹窗前把该单的审批历史取到手，弹窗的「前序审批」与列表那一格才可能同源；
  // silent 查询失败不拦审批（拿不到就不显示该段）。
  await ensureHistoryFor(row)
  const res = await approvalDlg.value.open({
    row,
    taskId: row.taskId,
    decision: approved ? 'approve' : 'reject',
    history: historyForDialog(row),
    showDetail: isExpenseBill(row) && row.bizResolved !== false && row.billId != null,
    // 传阅：只有「通过」才可能投递抄送（驳回会终止实例），故仅在通过时显示抄送区
    allowCc: approved,
    // 审批请求改由弹窗执行 —— 成功才关窗；被服务端业务异常拒绝
    //（「任务已被处理」「禁止审批自己提交的单据」「会计期间已结账」等）时弹窗保持打开、
    // 意见与下一环节点名/审批项选择全部保留，失败原因就地显示并可原地重试。
    // 若先关窗再发请求，失败只剩一条飘过顶部的红条，审批人既丢了意见也不知卡在哪。
    submit: async (payload) => {
      try {
        // 写动作统一包 try/catch。silent 只为压掉拦截器把后端原文/权限码直出成 toast，
        // 失败规则统一由下面 catch 的业务语义提示承担（单号＋节点＋下一步）。
        await wfApi.approve({ taskId: row.taskId, approved: payload.approved, comment: payload.comment,
          nextAssigneeUserId: payload.nextAssigneeUserId, ccUserIds: payload.ccUserIds,
          rejectToNodeKey: payload.rejectToNodeKey, approvalItemCode: payload.approvalItemCode }, { silent: true })
      } catch (e) {
        // 写侧零反馈：通过/退回被服务端拒绝（如作业池任务未领取 code 400「须先领取再办理」）时，
        // 必须显式落业务语义 toast（含单号/节点与可执行的下一步），再把异常原样抛回弹窗 ——
        // 弹窗保持打开、意见/点名保留，可原地重试；不再出现"点了没反应、零提示、易重复点"。
        // 本通道 silent=true ⇒ 拦截器不再回放后端原文，这里必须逐字补回（replayServerMsg=true），
        // 否则强制校验类拒绝（如三单匹配冻结）只剩通用「处置」文案，原因被丢。
        notifyRefused(payload.approved ? '通过' : '驳回', row, e, true)
        throw e
      }
      ElMessage.success(t('purchase.poOrder.Rejected'))
      detailVisible.value = false
      load()
    },
  })
  if (!res) return // 用户取消，或提交失败后原地重试前放弃
}

// 批量审批：勾选行统一意见一次性提交后端批量接口
// （wfApi.batchApprove → 主路径 POST /api/tenant/workflow/task/batch-approve，请求体 { taskIds, comment }；
//   /api/tenant/wf/batch-approve 只是 404 时的防御性回退，后端并无该路径）。
// 完成后按返回的 fail 数组逐条展示失败明细并刷新列表；未勾选时按钮禁用并提示。
const batchApproving = ref(false)
const taskTableRef = ref(null)

// 转办
const transferVisible = ref(false)
const transferRow = ref({})
const transferFormRef = ref(null)
const transferForm = reactive({ target: null })
// 与后端 /wf/task/transfer 的目标校验同一规则：必须落到一个具体的人
const transferFormRules = {
  target: commonRules.select('请选择被转办人'),
}
const transferTargetName = ref('')
const transferComment = ref('')

const transferCandidates = ref([])
const transferCandLoading = ref(false)
const transferCandEmpty = ref(false)

function onTransferPick(id) {
  const c = transferCandidates.value.find((x) => x.userId === id)
  transferTargetName.value = c ? c.name : String(id)
}

async function openTransfer(row) {
  if (!row?.taskId) { ElMessage.warning(t('approval.main.This4')); return }
  transferRow.value = row
  transferForm.target = null
  transferTargetName.value = ''
  transferComment.value = ''
  transferCandidates.value = []
  transferCandEmpty.value = false
  transferVisible.value = true
  transferCandLoading.value = true
  try {
    const r = await wfApi.taskCandidates({ taskId: row.taskId })
    transferCandidates.value = r?.candidates || []
    transferCandEmpty.value = !!r?.empty || transferCandidates.value.length === 0
  } catch (e) {
    ElMessage.warning('可办理名单获取失败：' + e.message)
  } finally { transferCandLoading.value = false }
}

async function submitTransfer() {
  if (!(await validateForms([transferFormRef]))) return
  try {
    await wfApi.transfer({
      taskId: transferRow.value.taskId,
      targetUserId: transferForm.target,
      comment: transferComment.value || '',
    })
    ElMessage.success(`已转办给 ${transferTargetName.value || transferForm.target}`)
    transferVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 加签：前加签 / 后加签
// 与转办的本质区别：转办是「受理人永久变更」，加签是「临时插一道」——
// 加签人审完自动回跳（前→原办理人；后→下一节点应得的人），流程走向不变。
// 加签人在待办里看到的是同一个任务，但他的「同意」只代表加签审完，不会推进流程。
const addSignVisible = ref(false)
const addSignRow = ref({})
const addSignForm = reactive({ position: 'BEFORE', signUsers: [], reason: '' })
const addSignFormRef = ref(null)
// 与后端 /wf/task/addsign 同一规则：位置二选一、加签人至少一位
const addSignFormRules = {
  position: commonRules.select('请选择加签位置'),
  signUsers: commonRules.multiSelect('请至少选择一位加签人'),
}
const addSignCandidates = ref([])
const addSignCandLoading = ref(false)
const addSignCandEmpty = ref(false)

async function openAddSign(row) {
  if (!row?.taskId) { ElMessage.warning(t('approval.main.This5')); return }
  addSignRow.value = row
  addSignForm.position = 'BEFORE'
  addSignForm.signUsers = []
  addSignForm.reason = ''
  addSignCandidates.value = []
  addSignCandEmpty.value = false
  addSignVisible.value = true
  addSignCandLoading.value = true
  try {
    // 与转办同源：候选人名单来自 /task-candidates（后端已做 canOperate 校验），
    // 因此弹窗里选得到的人提交时一定通过——避免"能选但提交报错"。
    const r = await wfApi.taskCandidates({ taskId: row.taskId })
    addSignCandidates.value = r?.candidates || []
    addSignCandEmpty.value = !!r?.empty || addSignCandidates.value.length === 0
  } catch (e) {
    ElMessage.warning('可办理名单获取失败：' + e.message)
  } finally { addSignCandLoading.value = false }
}

async function submitAddSign() {
  if (!(await validateForms([addSignFormRef]))) return
  try {
    const r = await wfApi.addSign({
      taskId: addSignRow.value.taskId,
      position: addSignForm.position,
      // 后端契约为逗号分隔的用户ID串（多人=链式依次签），此处保持选择顺序
      signUsers: addSignForm.signUsers.join(','),
      reason: addSignForm.reason || '',
    })
    ElMessage.success(addSignForm.position === 'BEFORE'
      ? '已前加签：加签人先审，审完自动回到你'
      : '已后加签：你审完后由加签人再审')
    addSignVisible.value = false
    load()
    return r
  } catch (e) { ElMessage.error(e.message) }
}

// 任务池：领取 / 释放（节点取人方式=POOL 时该任务只有候选组、没有办理人）
// 领取＝把任务锁定到自己名下（并发抢单由后端 Flowable claim 保证只有一人成功）；
// 释放＝退回任务池给同池其他人。两者都只刷新列表，不弹窗——没有需要征询的输入。
// 负向操作零反馈：这两条链路上的拒绝全是 HTTP 200 + {code,msg}
// （GlobalExceptionHandler 把 BizException 归一成 Result.fail，不是 4xx 状态码），
// 拦截器已把后端原文 toastBizError 出来，且 claim/unclaim 未传 silent ⇒
// 原文不会丢。真正缺的是两件事：一是提示里不带单号/节点，屏上十来行时根本不知道说的是哪一条；
// 二是服务端拒绝语只说"不许"，不说"那该怎么办"（自审拦截校验没有任何"改派/绕行"出口提示，
// 审批人只能反复点）。下面按后端逐条原文给出处置。
const billLabel = (row) => `【${row?.billNo || row?.title || '未关联单据'}｜${row?.taskName || '当前节点'}】`
/** 后端拒绝原文 → 可执行的下一步（判定顺序自严到宽，取第一条命中） */
function refuseAdvice(msg) {
  const m = String(msg || '')
  // 「自审/连办」三类若归一成「本单是你自己提交的」，
  // 连办校验命中时（同一人已办过本实例其他节点）文案就指错方向，排查被带偏。
  // 故按后端原文分流：连办优先（更具体），自审次之。
  if (/同一人在同一审批流程|连办/.test(m)) {
    return '你已在同一审批流程中办理过其他节点（连办/自审拦截）。本弹窗内没有「转办」入口：请先点「取消」回到待办列表，在该行行内点「转办」把本节点交给同角色其他持有人，或联系流程管理员「改派」；反复点击不会放行。'
  }
  if (/自审|自己提交的|禁止审批自己/.test(m)) {
    return '本单是你自己提交的，内控规则不允许自审自批。本弹窗内没有「转办」入口：请先点「取消」回到待办列表，在该行行内点「转办」把本节点交给同角色其他人，或联系流程管理员「改派」；反复点击不会放行。'
  }
  // 审批提交侧校验（下一环节须点名到人）——漏进兜底文案就让人摸不到出口
  if (/指定.*办理人|未点名|下一环节/.test(m)) {
    return '本节点按配置需先指定下一环节办理人：请回到弹窗在上方「下一环节」区块点名到人后再提交。'
  }
  if (/他人领取|已由他人/.test(m)) {
    return '同池同事已抢先领取。本行稍后会显示其姓名或「XX 正在办理」，需要接办请由其释放，或走「转办」。'
  }
  if (/候选办理人|无权领取/.test(m)) {
    return '你不在本节点的候选名单内（角色与公司范围不匹配）。请到「系统管理 · 角色授权」核岗，或改由名单内同事办理。'
  }
  if (/已在你名下|无需重复领取/.test(m)) {
    return '该任务已在你名下，直接点「通过 / 驳回」办理即可，无需再领取。'
  }
  // 作业池任务未领取即点通过/驳回的主路径——后端拒「作业池任务须先领取再办理」，
  // 原样直出等于让用户猜；给出「先领取再办理」的明确出口，并说明本次未生效无须重复提交。
  if (/须先.*领取|先领取再办理|请先.*领取/.test(m)) {
    return '任务池（共享作业）节点要先「领取」再办理：请先回列表点该行「领取」把任务锁定到本人名下，再点「通过 / 驳回」；本次未生效，无须重复提交。'
  }
  // 审批提交侧的期间校验（会计期间已结账）
  if (/会计期间|结账/.test(m)) {
    return '单据所属会计期间已结账，本期间不能再办理。请联系总账会计按流程处理期间后再回来办理；重复提交不会放行。'
  }
  if (/只能释放自己领取/.test(m)) {
    return '释放只认领取人本人。需要换人请由当前办理人释放，或使用「转办」/联系管理员「改派」。'
  }
  if (/无人可办|无其他办理人/.test(m)) {
    return '服务端判定释放后本节点无人可办，故未释放；确需换人请用「转办」或「改派」。'
  }
  if (/不存在或已处理|已被处理/.test(m)) {
    return '该任务已被办理或已流转，列表稍后同步；请到「单据追溯」确认最新落点。'
  }
  // 提交结果未知（超时）不能报成功也不能诱导重复点——先回读再用诊断规则办结
  // longWrite 端点超时由 api.js 报成「已提交，处理中」，同一条处置规则要接住它
  if (/超时|仍在处理|已提交，处理中/.test(m)) {
    return '请求超时：本次提交结果未知（服务端可能已受理）。请勿重复提交——先回列表刷新核对该任务是否已被办理，再决定是否重试。'
  }
  if (/未登录/.test(m)) {
    return '登录态已失效，请重新登录后再操作。'
  }
  if (/权限|无权/.test(m)) {
    return '当前角色缺少该操作的权限点，请联系系统管理员按角色授权。'
  }
  return '请核对本任务当前状态与可走的合法出口（先领取 / 转办 / 改派），或联系流程管理员核对节点配置；不确定时不要反复重试同一动作。'
}
/**
 * 拒绝原因＋单号/节点＋处置：一次说全，可手动关闭，停留 8 秒。
 *
 * 审批提交链统一带 `{ silent: true }`（见 approve 的注释），拦截器因此不再把后端原文 toast 出来；
 * 若这里只渲染 refuseAdvice 推出的「处置」，服务端红字（如三单匹配强制校验的拒付原因）就永远
 * 到不了屏上——与「执行付款拦停弹窗原文回放」两条通道规则不一致。故对有服务端原文的通道
 * （replayServerMsg=true）先逐字回放服务端原文，再接「处置」建议；领取/释放等非 silent 通道
 * 原文已由拦截器给出，不重复回放。
 */
function notifyRefused(action, row, e, replayServerMsg = false) {
  const raw = replayServerMsg ? String(e?.message || '').trim() : ''
  // axios 无业务响应体时的通用文案（"Request failed with status code 400" 等）不是原因，
  // 回放它反而把真原因挤没；只有拿到服务端 msg 才逐字显示。
  const replayable = raw && !/^Request failed with status code|^Network Error|^timeout of|^网络错误/i.test(raw)
  // 服务端 37s 才回、前端 20s 断连那一类，库内其实已办结；
  // 前缀写死「未生效」与事实相反，正是诱导审批人重复点击的那句话 ⇒ 超时通道改报「结果待确认」。
  const head = `${billLabel(row)} ${e?.accepted ? `${action}提交结果待确认` : `${action}未生效`}`
  ElMessage({
    type: 'error',
    duration: 8000,
    showClose: true,
    grouping: true,
    message: `${head}${replayable ? `：${raw}` : ''} —— 处置：${refuseAdvice(raw || e?.message)}`,
  })
}

async function claimTask(row) {
  try {
    await wfApi.claim(row.taskId)
  } catch (e) {
    // 领取失败：先如实回显拒绝原因（拦截器已弹原文，这里补单号/节点与处置），再按服务端读数刷新
    notifyRefused('领取', row, e)
    await load()
    return
  }
  // 成功即就地落账（不等列表回来），并 await 刷新 —— 若先弹成功提示、
  // 再发一个不 await 的 load()，同一行的徽标在响应回来前一直是「待领取／N 人可办」：
  // 提示说"已领取"，界面说"没领取"。
  const t = row.taskId != null ? String(row.taskId) : null
  if (t) localOp[t] = { kind: 'claim' }
  row.assigneeName = myName()
  row.canClaim = false
  row.canUnclaim = true
  row.lockedByOther = false
  ElMessage.success(`${billLabel(row)} 已领取，本节点现在由你办理${isPoolRow(row) ? '；如需交还请点「释放」' : ''}`)
  await load()
}

async function unclaimTask(row) {
  try {
    await wfApi.unclaim(row.taskId)
  } catch (e) {
    notifyRefused('释放', row, e)
    await load()
    return
  }
  const t = row.taskId != null ? String(row.taskId) : null
  if (t) localOp[t] = { kind: 'unclaim' }
  row.assigneeName = ''
  row.canClaim = true
  row.canUnclaim = false
  row.lockedByOther = false
  ElMessage.success(`${billLabel(row)} 已释放，任务退回共享池，同池同事可先行领取`)
  await load()
}
/**
 * 批量摘要不再报「金额合计」。
 * 从前这里用 `Number(r.totalAmount || 0)` 累加，NULL 与 0 同形 ⇒ 6 单全取不到金额时照样打出
 * 「¥0.00」，一个语法正确、业务上虚假的合计把审批人哄过了。合计改由弹窗按明细真算
 * 只累加取到值的行，未取到的如实标注，一处真值不留两份。
 * 这里只保留"选了多少条、卡在哪些节点"，以及两条会在提交时逐条失败的风险提示。
 */
function batchSummary(rows) {
  const uniq = (vals) => [...new Set(vals.filter(Boolean))]
  const nodes = uniq(rows.map((r) => r.taskName))
  const unresolved = rows.filter((r) => r.bizResolved === false).length
  const locked = rows.filter((r) => r.lockedByOther).length
  const out = [
    { label: '待办条数', value: rows.length + ' 条' },
    { label: '当前节点', value: nodes.length <= 2 ? nodes.join('、') : `${nodes.length} 个节点` },
  ]
  if (unresolved) out.push({ label: '无关联单据', value: `${unresolved} 条业务行已清理，明细已标注，提交时多半逐条失败`, full: true })
  if (locked) out.push({ label: '他人办理中', value: `${locked} 条已被同池他人领取（服务端 canOperate 会拒），建议先取消勾选`, full: true })
  return out
}

/**
 * 176：批量逐单明细。
 * 查询＝批量前已勾选的那几行（todo/page 的同一份数据），不另发请求、不新增查询范围，
 * 因此明细与审批人在列表里看到的单号/金额/节点逐字一致，不会出现"弹窗另一套数"。
 * amount 严格保留 null（后端该域无金额列或业务行取不到时 totalAmount 就是 NULL），
 * 由弹窗区分"未取到"与"0 元"。
 */
function batchItems(rows) {
  return rows.map((r, i) => ({
    key: String(r.taskId ?? i),
    taskId: r.taskId,
    billNo: r.billNo || '',
    title: r.title || '',
    billTypeText: docTypeLabel(r.billType || r.bizType) || '',
    amount: r.totalAmount == null || r.totalAmount === '' ? null : Number(r.totalAmount),
    node: r.taskName || '',
    unresolved: r.bizResolved === false,
    locked: !!r.lockedByOther,
    lockOwnerName: r.lockOwnerName || '',
  }))
}

/** 后端 WfTaskBatchController 的单次条数硬上限（超限必被 400 拒，这里先给可读提示） */
const BATCH_MAX = 100

/**
 * 批量提交的两条通道，返回同一种 { success, fail:[{taskId, billNo?, reason}] } 形状。
 * ① 未选抄送 ⇒ 后端批量接口一次请求（逐条独立事务），无需逐单办理；
 * ② 选了抄送 ⇒ 批量接口的入参只有 {taskIds, comment, approved}，<b>没有</b> ccUserIds，
 *    抄送仅在单条 /wf/approve 生效（与节点配置取并集后写 wf_cc）。与其把选择悄悄丢掉，
 *    这里改走逐单通道：同一批单、同一条意见，逐条办理并各自投递抄送。
 *    两条通道在后端 converge 到同一个 WfEngineFacade.complete(...)（批量走的 4 参重载即以
 *    nextAssigneeUserId/ccUserIds/rejectToNodeKey/approvalItemCode = null 委托到 9 参实现），
 *    越权守卫、自审拦截、PICK 点名硬约束同一规则，换路不绕校验。
 * @returns Promise<{success:number, fail:Array}>；整体不可用（接口未部署/未登录等）时抛错，
 *          由弹窗就地回显并保持打开（手感），单条业务失败不算整体失败。
 */
async function submitBatch(rows, payload) {
  const taskIds = rows.map((r) => String(r.taskId))
  if (!payload.ccUserIds) {
    let res
    try {
      res = await wfApi.batchApprove({ taskIds, comment: payload.comment })
    } catch (e) {
      // 两个候选路径都 404 → 批量审批接口尚未部署，给出可行动的提示而不是英文原文。
      // 后端已实现于 /api/tenant/workflow/task/batch-approve（WfTaskBatchController）；
      // 旧文案写的 /api/tenant/wf/batch-approve 在 WfTaskController 下并不存在，会把排障方向带偏。
      if (e?.status === 404 || /404/.test(String(e?.message))) {
        throw new Error('批量审批暂不可用，请改为逐单审批，或联系管理员核查')
      }
      throw e
    }
    // 宽容解析：data = { success?: <成功条数>, fail?: [{ taskId, reason }] }
    const failList = Array.isArray(res?.fail) ? res.fail : Array.isArray(res?.failed) ? res.failed : []
    // 后端 WfTaskBatchController 实际字段是 success；successCount/okCount 是历史假设名，
    // 保留在后仅为兼容其它部署形态（只读后两者会恒 undefined，只能靠 fallback 兜住，
    // 结果虽正确但属"靠兜底"而非"直读"，任一条失败明细解析变动就会显示错数）。
    const declared = Number(res?.success ?? res?.successCount ?? res?.okCount)
    return { success: Number.isFinite(declared) ? declared : Math.max(0, rows.length - failList.length), fail: failList }
  }
  // 逐单通道（带抄送）：silent 关掉全局 toast，失败逐条收进明细统一回显
  const fail = []
  let success = 0
  for (const r of rows) {
    try {
      await wfApi.approve({ taskId: String(r.taskId), approved: payload.approved !== false, comment: payload.comment, ccUserIds: payload.ccUserIds }, { silent: true })
      success++
    } catch (e) {
      fail.push({ taskId: r.taskId, billNo: r.billNo || '', reason: e?.message || '审批失败' })
    }
  }
  return { success, fail }
}

async function batchApprove() {
  const all = selRows.value || []
  if (!all.length) { ElMessage.warning(t('approval.main.Select')); return }
  const rows = all.filter((r) => r?.taskId)
  if (!rows.length) { ElMessage.warning(t('approval.main.Selected')); return }
  if (!approvalDlg.value) { ElMessage.error(t('useApprovalDialog.main.Approval')); return }
  if (rows.length > BATCH_MAX) { ElMessage.warning(`单次批量审批不能超过 ${BATCH_MAX} 条，请减少勾选后再点`); return }
  // 同款手感（从前批量是"先关窗再发请求"）：提交动作交给弹窗执行 ——
  // 接口整体不可用（未部署 404 / 登录态失效 / 后端不可达）时弹窗保持打开、425 字意见不丢，
  // 就地看原因并可原地重试；单条业务失败（任务已被处理、禁止自审）属批量接口的正常返回，
  // 照常关窗后逐条回显失败明细。
  let outcome = null
  batchApproving.value = true
  let res
  try {
    res = await approvalDlg.value.open({
      batch: rows.length,
      allowReject: false,
      summary: batchSummary(rows),
      items: batchItems(rows), // 逐单明细
      allowCc: true, // 批量补抄送入口（选了人即改走逐单通道，见 submitBatch）
      submit: async (payload) => { outcome = await submitBatch(rows, payload) },
    })
  } catch (_) {
    return // 弹窗侧已就地显示原因，这里不重复弹
  } finally {
    batchApproving.value = false
  }
  if (!res || !outcome) return // 用户取消，或提交失败后放弃

  const failList = Array.isArray(outcome.fail) ? outcome.fail : []
  const okCount = Number.isFinite(Number(outcome.success)) ? Number(outcome.success) : Math.max(0, rows.length - failList.length)
  if (!failList.length) {
    ElMessage.success(`批量审批完成：成功 ${okCount}/${rows.length} ${t('approval.main.Records')}`)
  } else {
    ElMessage.warning(`批量审批完成：成功 ${okCount} 条，失败 ${failList.length} 条，明细见弹窗`)
    // 失败明细逐条展示（VNode 渲染，避免把后端文案当 HTML 注入）
    const lines = failList.map((f, i) => {
      const r = rows.find((x) => String(x.taskId) === String(f?.taskId))
      const name = f?.billNo || r?.billNo || r?.title || '任务 ' + (f?.taskId ?? r?.taskId ?? i + 1)
      // 后端字段是 reason，不是 msg/message：只读后两者 ⇒ 后端精心构造的逐条失败原因
      //（「任务已被处理，请刷新待办」「禁止审批自己提交的单据」等）全部丢失，界面永远显示
      // 兜底文案「审批失败」，等于把最有用的排障信息吞掉了。reason 必须放最前。
      return `${i + 1}. ${name}：${f?.reason || f?.msg || f?.message || '审批失败'}`
    })
    ElMessageBox.alert(
      h('div', null, lines.map((l) => h('div', { style: 'margin:2px 0;line-height:1.6' }, l))),
      `批量审批失败明细（${failList.length} 条）`,
      { type: 'error', confirmButtonText: '知道了' },
    ).catch(() => {})
  }
  taskTableRef.value?.clearSelection()
  selRows.value = []
  load()
}

/** 打开核减弹层：以申请金额作为核定报销初值，付款默认等于核定报销（退款 0） */
function openDeduct(row) {
  cur.value = row
  const total = Number(row.totalAmount || 0)
  const approved = row.approvedAmount != null ? Number(row.approvedAmount) : total
  const pay = row.approvedPayAmount != null ? Number(row.approvedPayAmount) : approved
  deduct.approvedAmount = round2(approved)
  deduct.approvedPayAmount = round2(pay)
  deduct.approvedRefundAmount = round2(Math.max(0, approved - pay))
  deduct.remark = ''
  deductVisible.value = true
}

function round2(v) { return Math.round(Number(v || 0) * 100) / 100 }

/** 详情抽屉内点「核减」：先收起抽屉再打开核减弹层，避免两层浮层叠加 */
function openDeductFromDrawer() {
  detailVisible.value = false
  openDeduct(cur.value)
}

/** 核定报销/付款变化时自动重算退款，保证三层勾稽 */
function autoRefund() {
  const a = Number(deduct.approvedAmount || 0)
  const p = Number(deduct.approvedPayAmount || 0)
  deduct.approvedRefundAmount = round2(Math.max(0, a - p))
}

async function submitDeduct() {
  if (!(await validateForms([deductFormRef]))) return
  const total = Number(cur.value.totalAmount || 0)
  const a = Number(deduct.approvedAmount || 0)
  const p = Number(deduct.approvedPayAmount || 0)
  const r = Number(deduct.approvedRefundAmount || 0)
  // 前端三层预校验（后端 assertApproveTrilogy 强校验兜底）
  if (a > total + 0.01) { ElMessage.warning(`核定报销(${a})超出申请金额(${total})，审批只能核减不能核增`); return }
  if (p > a + 0.01) { ElMessage.warning(`核定付款(${p})超出核定报销(${a})`); return }
  if (Math.abs(r - (a - p)) > 0.01) { ElMessage.warning(`核定退款须等于 核定报销(${a}) − 核定付款(${p}) = ${round2(a - p)}`); return }
  try {
    await billApi.approveDeduct(cur.value.billId, {
      approvedAmount: a,
      approvedPayAmount: p,
      approvedRefundAmount: r,
      remark: deduct.remark,
    })
    ElMessage.success(`核减成功：核定报销 ${fmt(a)}，核定付款 ${fmt(p)}，核定退款 ${fmt(r)}`)
    deductVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function exportCsv() {
  if (!tasks.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'title', label: t('approval.main.Workflow') }, { prop: 'billNo', label: t('bill.billDetailPanel.Document2') }, { prop: 'taskName', label: t('approval.main.Node') }, { prop: 'assigneeName', label: t('approval.main.Current4') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = tasks.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Approval_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('utils.csv.Select')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('utils.csv.Cannot')); return }
  const header = keys.join(',')
  const lines = selRows.value.map((row) => keys.map((k) => {
    const v = row[k]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Approval_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}


// CoachMark 仅通过帮助按钮手动触发，不自动弹出避免遮挡页面
</script>

<style scoped>
.approval-pager { margin-top: 12px; display: flex; justify-content: flex-end; flex-wrap: wrap; }
.expand-content { padding: 12px 20px; background: var(--app-hover, #fafafa); }
.hist-placeholder { display: flex; align-items: center; gap: 8px; font-size: 13px; line-height: 20px; color: #909399; }
.history-detail { margin-top: 12px; max-height: 280px; overflow-y: auto; }
.duration-summary { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.dur-badge {
  font-size: 12px; padding: 2px 8px; border-radius: 10px;
  background: rgba(64, 158, 255, 0.12); color: #409eff; border: 1px solid #d9ecff;
}
.dur-badge.warn { background: rgba(245, 108, 108, 0.13); color: #f56c6c; border-color: #fbc4c4; }
</style>
