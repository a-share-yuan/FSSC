<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap">
      <el-input v-model="kw" placeholder="资产编号/名称" clearable style="width: 220px" @keyup.enter="loadAssets" />
      <el-button type="primary" @click="loadAssets">查询</el-button>
      <el-button type="success" @click="openAsset">资产建档</el-button>
      <el-button type="warning" @click="depreciateMonthBatch">月度折旧批处理</el-button>
      <el-button type="primary" plain @click="openCapital">资本性支出申请</el-button>
      <!-- 在建工程转固入口：拉取在途 CIP（IN_PROGRESS）弹窗选择后转固；接口未部署（404）时友好提示不阻断 -->
      <el-button type="success" plain @click="openCipFlow">转固</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="assets" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
      <el-table-column type="selection" width="45" />
      <!-- 无编号卡片（盘点导入等）首列空值文案，按卡片 ID 追溯 -->
      <el-table-column label="资产编号" width="140" sortable>
        <template #default="{ row }">
          <span v-if="row.assetNo">{{ row.assetNo }}</span>
          <span v-else class="muted-no">—（待审批通过后生成）</span>
        </template>
      </el-table-column>
      <el-table-column prop="assetName" label="资产名称" min-width="150" show-overflow-tooltip sortable />
      <el-table-column label="资产类别" width="110">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="使用部门" width="110">
        <template #default="{ row }">{{ deptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column label="原值" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.originalAmount) }}</template>
      </el-table-column>
      <el-table-column label="累计折旧" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.accumDepr) }}</template>
      </el-table-column>
      <el-table-column label="月折旧额" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.monthlyDepr) }}</template>
      </el-table-column>
      <el-table-column label="折旧方法" width="90" align="center">
        <template #default="{ row }">{{ deprMethodName(row.depreciationMethod) }}</template>
      </el-table-column>
      <el-table-column label="净值" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="assetStatusTagType(row.status)" size="small">{{ assetStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="470" fixed="right">
        <template #default="{ row }">          <InlineActions :visible="true" @view="openAsset(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="false" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <!-- 编辑入口——打开与新增相同的建档弹窗并回填卡片数据，保存走 assetApi.update -->
          <el-button link size="small" type="primary" @click="openAsset(row, true)">编辑</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="warning" @click="depreciate(row)">折旧</el-button>
          <el-button v-if="row.status === 'CIP'" link size="small" type="success" @click="openCipToAsset(row)">转固</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="danger" @click="scrap(row)">报废</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="primary" @click="openTransfer(row)">调拨</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="primary" @click="doDisposeSell(row)">外销</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="primary" @click="doCheckDiff(row)">盘点差异</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="primary" @click="openAdjustValue(row)">原值调整</el-button>
          <el-button v-if="row.status !== 'SCRAP' && row.status !== 'CIP'" link size="small" type="primary" @click="doImpair(row)">减值</el-button>
          <el-button link size="small" type="info" @click="showNet(row)">净值</el-button>
          <!-- X-06读取环节：变动台账此前只写不读，卡片页给一个消费者入口 -->
          <el-button link size="small" type="info" @click="openChangeLog(row)">变动台账</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; loadAssets() }" />
  </div>

  <!-- 资产建档/编辑弹窗 -->
  <el-dialog v-model="dlgAsset" :title="cardReadonly ? '资产卡片详情' : (aForm.id ? '编辑资产' : '资产建档')" width="620px" destroy-on-close>
    <el-form ref="assetFormRef" :model="aForm" :rules="assetRules" label-width="120px" size="small" :disabled="cardReadonly">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="资产编号">
            <el-input v-model="aForm.assetNo" placeholder="保存时自动生成" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="资产名称" prop="assetName"><el-input v-model="aForm.assetName" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="资产类别" prop="categoryId">
            <el-select v-model="aForm.categoryId" filterable clearable placeholder="请选择资产类别" style="width:100%" @change="onCategoryChange">
              <!-- 下拉项前置类别编码（CAT01~CAT05），便于与类别主数据页、凭证摘要规则对号 -->
              <el-option v-for="c in categories" :key="c.id" :label="(c.categoryCode ? c.categoryCode + ' ' : '') + c.categoryName + (c.depreciationMonths ? '（默认' + c.depreciationMonths + '月）' : '')" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="使用部门" prop="deptId">
            <el-select v-model="aForm.deptId" filterable clearable placeholder="请选择部门" style="width:100%">
              <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属公司" prop="companyId">
            <el-select v-model="aForm.companyId" filterable clearable placeholder="请选择公司" style="width:100%">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="购置日期" prop="purchaseDate"><el-date-picker v-model="aForm.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="原值" prop="originalAmount"><el-input-number v-model="aForm.originalAmount" :min="0" :max="AMOUNT_MAX" :precision="2" style="width:100%" @blur="clampAmount('originalAmount', '原值')" @change="recalcResidual" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="残值"><el-input-number v-model="aForm.residualValue" :min="0" :max="AMOUNT_MAX" :precision="2" style="width:100%" @blur="clampAmount('residualValue', '残值')" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="折旧方法" prop="depreciationMethod">
          <el-select v-model="aForm.depreciationMethod" style="width:100%">
            <el-option label="直线法" value="LINEAR" />
            <el-option label="加速折旧" value="ACCELERATED" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="折旧月数" prop="depreciationMonths"><el-input-number v-model="aForm.depreciationMonths" :min="1" :max="MONTHS_MAX" :precision="0" style="width:100%" @blur="clampMonths('depreciationMonths', '折旧月数')" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="税法折旧年限(月)">
            <el-input-number v-model="aForm.taxLifeMonths" :precision="0" :max="MONTHS_MAX" style="width:100%" placeholder="可空" @blur="clampMonths('taxLifeMonths', '税法折旧年限')" />
            <div style="font-size:12px; color:#909399; line-height:1.4">与会计年限分离，支撑税会差异 A105080</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商">
            <el-select v-model="aForm.supplierId" filterable clearable :placeholder="suppliersDenied ? '当前账号无供应商查看权限，可留空' : '请选择供应商'" style="width:100%">
              <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName || s.name" :value="s.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="发票号"><el-input v-model="aForm.invoiceNo" maxlength="64" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="dlgAsset = false">{{ cardReadonly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!cardReadonly" type="primary" @click="saveAsset">保存</el-button>
    </template>
  </el-dialog>

  <!-- 资本性支出申请弹窗 -->
  <el-dialog v-model="dlgCapital" title="资本性支出申请" width="560px" destroy-on-close>
    <el-form ref="capitalFormRef" :model="capForm" :rules="capitalRules" label-width="130px" size="small">
      <el-form-item label="支出金额" prop="amount"><el-input-number v-model="capForm.amount" :min="0" :max="AMOUNT_MAX" :precision="2" style="width:100%" @blur="clampIn(capForm, 'amount', AMOUNT_MAX, '支出金额')" /></el-form-item>
      <el-form-item label="预算科目">
        <!-- CER 预算集成：指定预算科目则发起即硬控、审批通过后占用；留空自动取综合预算默认处理 -->
        <el-select v-model="capForm.budgetItemId" filterable clearable placeholder="留空自动匹配综合预算" style="width:100%">
          <el-option v-for="bgt in budgetItems" :key="bgt.id" :label="budgetOptionLabel(bgt)" :value="bgt.id" />
        </el-select>
        <!-- 空集/查询失败要看得见，不能只留一个空的下拉让人猜 -->
        <div v-if="budgetItemsNotice" style="font-size:12px;color:#e6a23c;line-height:1.5">{{ budgetItemsNotice }}</div>
      </el-form-item>
      <el-form-item label="资产/项目名称" prop="assetName"><el-input v-model="capForm.assetName" placeholder="资本性支出对应资产或项目名称" /></el-form-item>
      <el-form-item label="使用部门" prop="deptId">
        <el-select v-model="capForm.deptId" filterable clearable placeholder="请选择部门" style="width:100%">
          <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属公司" prop="companyId">
        <el-select v-model="capForm.companyId" filterable clearable placeholder="请选择公司" style="width:100%">
          <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请说明" prop="remark"><el-input v-model="capForm.remark" type="textarea" :rows="2" placeholder="立项依据 / 采购计划说明" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgCapital = false">取消</el-button>
      <el-button type="primary" @click="saveCapital">提交审批</el-button>
    </template>
  </el-dialog>

  <!-- 资产调拨弹窗 -->
  <el-dialog v-model="dlgTransfer" title="资产调拨（提交审批）" width="480px" destroy-on-close>
    <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="120px" size="small">
      <el-form-item label="调拨资产">
        <el-input :model-value="transferRow.assetName" disabled />
      </el-form-item>
      <el-form-item label="目标部门" prop="targetDeptId">
        <el-select v-model="transferForm.targetDeptId" filterable clearable placeholder="请选择目标部门" style="width:100%">
          <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标公司">
        <el-select v-model="transferForm.targetCompanyId" filterable clearable placeholder="跨法人调拨时选择目标公司" style="width:100%">
          <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="调拨说明"><el-input v-model="transferForm.remark" type="textarea" :rows="2" placeholder="调拨原因/备注" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgTransfer = false">取消</el-button>
      <el-button type="primary" @click="confirmTransfer">提交审批</el-button>
    </template>
  </el-dialog>

  <!-- X-06变动台账：原值调整/盘点差异的原因、前后镜像与审批最终状态（此前只写不读） -->
  <el-dialog v-model="dlgChangeLog" :title="`变动台账 - ${changeLogAssetName}`" width="920px" destroy-on-close>
    <el-table :data="changeLogs" size="small" max-height="380" v-loading="changeLogLoading"
              empty-text="该资产暂无变动台账（原值调整/盘点差异审批发起后在此记录）">
      <el-table-column prop="changeType" label="变动类型" width="100">
        <template #default="{ row }">{{ CHANGE_TYPE_LABEL[row.changeType] || row.changeType || '—' }}</template>
      </el-table-column>
      <el-table-column prop="changeMethod" label="变动方式" width="100">
        <template #default="{ row }">{{ CHANGE_METHOD_LABEL[row.changeMethod] || row.changeMethod || '—' }}</template>
      </el-table-column>
      <el-table-column prop="changeReason" label="变动原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.changeReason || '—' }}</template>
      </el-table-column>
      <el-table-column label="原值 前→后" width="180" align="right">
        <template #default="{ row }">{{ row.beforeOriginalAmount == null ? '—' : fmt(row.beforeOriginalAmount) }} → {{ row.afterOriginalAmount == null ? '—' : fmt(row.afterOriginalAmount) }}</template>
      </el-table-column>
      <el-table-column label="幅度" width="110" align="right">
        <template #default="{ row }">{{ row.deltaAmount == null ? '—' : fmt(row.deltaAmount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="审批" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
            {{ { APPROVING: '在途', APPROVED: '已通过', REJECTED: '已驳回' }[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="登记时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
    </el-table>
    <template #footer><el-button @click="dlgChangeLog = false">关闭</el-button></template>
  </el-dialog>

  <!-- 资产原值调整弹窗（X-06/原因改人工输入并随变动台账记录，不再传硬编码默认处理句） -->
  <el-dialog v-model="dlgAdjust" title="资产原值调整（提交审批）" width="480px" destroy-on-close>
    <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="120px" size="small">
      <el-form-item label="调整资产">
        <el-input :model-value="adjustRow.assetName" disabled />
      </el-form-item>
      <el-form-item label="当前原值">
        <el-input :model-value="fmt(adjustRow.originalAmount)" disabled />
      </el-form-item>
      <el-form-item label="调整后原值" prop="newOriginalAmount">
        <el-input v-model="adjustForm.newOriginalAmount" placeholder="请输入调整后原值（须大于 0）" />
      </el-form-item>
      <el-form-item label="调整原因" prop="reason">
        <el-input v-model="adjustForm.reason" type="textarea" :rows="2"
                  placeholder="请填写调整原因（审批人与事后追溯看这一句）" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgAdjust = false">取消</el-button>
      <el-button type="primary" @click="confirmAdjustValue">提交审批</el-button>
    </template>
  </el-dialog>

  <!-- 在建工程转固弹窗 -->
  <el-dialog v-model="dlgCipToAsset" title="在建工程转固定资产（提交审批）" width="440px" destroy-on-close>
    <el-form ref="cipFormRef" :model="cipForm" :rules="cipRules" label-width="120px" size="small">
      <el-form-item label="转固资产">
        <el-input :model-value="cipRow.assetName" disabled />
      </el-form-item>
      <el-form-item label="转固日期" prop="transferDate">
        <el-date-picker v-model="cipForm.transferDate" type="date" value-format="YYYY-MM-DD" placeholder="选择转固日期" style="width:100%" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="cipForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgCipToAsset = false">取消</el-button>
      <el-button type="primary" @click="confirmCipToAsset">提交审批</el-button>
    </template>
  </el-dialog>

  <!-- 在建工程转固入口弹窗：选择在途 CIP 项目 → 补齐转固要素 → 走 /masterdata/config/cip/{id}/transferToAsset -->
  <el-dialog v-model="dlgCipFlow" title="在建工程转固" width="680px" destroy-on-close>
    <el-alert type="info" :closable="false" style="margin-bottom: 10px"
      title="以下为在途在建工程项目，选择一项并补齐转固要素后确认；按归集成本生成固定资产并发起转固审批。" />
    <el-table :data="cipFlowRows" size="small" border stripe max-height="320" v-loading="cipFlowLoading"
              highlight-current-row empty-text="暂无在途在建工程" @current-change="(r) => (cipFlowSelected = r)">
      <el-table-column prop="cipCode" label="编码" width="130" />
      <el-table-column prop="cipName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="companyName" label="所属公司" width="130" show-overflow-tooltip />
      <el-table-column label="累计归集" width="120" align="right">
        <template #default="{ row }">{{ fmtCip(row.accruedAmount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
    </el-table>
    <el-form v-if="cipFlowSelected" ref="cipFlowFormRef" :model="cipFlowForm" :rules="cipFlowRules" label-width="120px" size="small" style="margin-top:10px">
      <el-form-item label="转固项目"><el-input :model-value="cipFlowSelected.cipName" disabled /></el-form-item>
      <el-form-item label="固定资产类别" prop="categoryId">
        <el-select v-model="cipFlowForm.categoryId" filterable placeholder="请选择类别" style="width:100%">
          <el-option v-for="c in cipFlowCats" :key="c.id" :label="c.categoryName || c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="使用部门" prop="deptId">
        <el-tree-select v-model="cipFlowForm.deptId" :data="cipFlowDepts" check-strictly filterable placeholder="请选择部门" style="width:100%" />
      </el-form-item>
      <el-form-item label="转固日期" prop="transferDate">
        <el-date-picker v-model="cipFlowForm.transferDate" type="date" value-format="YYYY-MM-DD" placeholder="购置日期/折旧起算" style="width:100%" />
      </el-form-item>
      <el-form-item label="折旧方法">
        <el-select v-model="cipFlowForm.depreciationMethod" style="width:100%">
          <el-option label="直线法" value="LINEAR" />
          <el-option label="加速折旧" value="ACCELERATED" />
        </el-select>
      </el-form-item>
      <el-form-item label="折旧月数" prop="depreciationMonths">
        <el-input-number v-model="cipFlowForm.depreciationMonths" :min="1" :max="600" style="width:100%" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="cipFlowForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgCipFlow = false">取消</el-button>
      <el-button type="primary" :loading="cipFlowSubmitting" :disabled="!cipFlowSelected" @click="confirmCipFlow">确认转固</el-button>
    </template>
  </el-dialog>

  <!-- 审批轨迹抽屉 -->
  <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { statusLabel } from '@/constants/billStatus'
import { assetStatusLabel } from '@/constants/enums'
import { formatDateTime } from '@/utils/format'
import { ref, inject, onMounted } from 'vue'
import { useFormRules } from '@/composables/useFormRules'
import { validateForm } from '@/composables/useFormRules'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assetApi, cipFlowApi, orgApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import InlineActions from '@/components/InlineActions.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()

// 快捷键
useKeyboard({
  'ctrl+r': () => loadAssets(),
})

// 审批轨迹
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id
  traceVisible.value = true
}

const { categories, depts, companies, suppliers, suppliersDenied, fmt, categoryName, deptName, loading } = inject('assetShared')

const kw = ref('')
const assets = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const selRows = ref([])

function assetStatusTagType(s) {
  if (['SCRAP', 'DISPOSED'].includes(s)) return 'danger'
  if (['USING'].includes(s)) return 'primary'
  if (['CIP'].includes(s)) return 'warning'
  return 'info'
}
function deprMethodName(m) { return { LINEAR: '直线法', ACCELERATED: '加速折旧' }[m] || m }

const dlgAsset = ref(false)
const emptyAsset = () => ({ id: null, assetNo: '', assetName: '', categoryId: null, deptId: null, companyId: null, purchaseDate: '', originalAmount: 0, residualValue: 0, depreciationMethod: 'LINEAR', depreciationMonths: 60, taxLifeMonths: null, supplierId: null, invoiceNo: '', status: 'INIT' })
const aForm = ref(emptyAsset())
/** 「查看」与「编辑」共用同一弹窗：无编辑意图时整表只读、不出保存按钮 */
const cardReadonly = ref(false)

// 数字框防呆：金额类上限 99,999,999,999、月数上限 600，失焦回读超限自动修正并提示
// （自动化键入偶发串位 3000→300000，人工也可能误输多位；el-input-number 的 :max 在失焦时默认处理截断）
const AMOUNT_MAX = 99999999999
const MONTHS_MAX = 600
function clampIn(obj, key, max, label) {
  const v = Number(obj?.[key])
  if (!Number.isFinite(v) || v <= max) return false
  obj[key] = max
  ElMessage.warning(`${label}超出上限 ${max.toLocaleString('zh-CN')}，已自动修正为上限值`)
  return true
}
function clampAmount(key, label) { clampIn(aForm.value, key, AMOUNT_MAX, label) }
function clampMonths(key, label) { clampIn(aForm.value, key, MONTHS_MAX, label) }

// S4 修复：选中资产类别后，按类别主数据(md_asset_category)的默认折旧参数级联带出折旧月数/残值，
// 避免「类别主数据配了默认值却形同虚设、每张卡片都手填固定 60 个月」导致折旧规则不一致。
function onCategoryChange(id) {
  const c = (categories.value || []).find((x) => x.id === id)
  if (!c) return
  const filled = []
  if (c.depreciationMonths != null && Number(c.depreciationMonths) > 0) {
    aForm.value.depreciationMonths = Number(c.depreciationMonths)
    filled.push('折旧月数 ' + aForm.value.depreciationMonths)
  }
  if (c.residualRate != null) {
    const pct = (Number(c.residualRate) * 100).toFixed(2)
    const amt = Number(aForm.value.originalAmount) || 0
    if (amt > 0) {
      aForm.value.residualValue = Number((amt * Number(c.residualRate)).toFixed(2))
      filled.push('残值 ' + aForm.value.residualValue + '（残值率 ' + pct + '%）')
    } else {
      filled.push('残值率 ' + pct + '%（填原值后自动算残值）')
    }
  }
  if (filled.length) ElMessage.success('已按类别【' + c.categoryName + '】带出：' + filled.join('、'))
}

// S4 配套：先选类别、后填原值的情形，按类别残值率补算残值（用户已手工填残值则不覆盖）
function recalcResidual() {
  const c = (categories.value || []).find((x) => x.id === aForm.value.categoryId)
  if (!c || c.residualRate == null) return
  const amt = Number(aForm.value.originalAmount) || 0
  if (amt <= 0) return
  if (Number(aForm.value.residualValue) > 0) return
  aForm.value.residualValue = Number((amt * Number(c.residualRate)).toFixed(2))
}

const dlgCapital = ref(false)
const capForm = ref({ amount: 0, assetName: '', deptId: null, companyId: null, remark: '' })

const dlgTransfer = ref(false)
const transferRow = ref({})
const transferForm = ref({ targetDeptId: null, targetCompanyId: null, remark: '' })

// X-06：原值调整改弹窗——原因必须由人工填写（旧版传死值 '前端原值调整'，写入数据库后与真实成因无关）
const dlgAdjust = ref(false)
const adjustRow = ref({})
const adjustForm = ref({ newOriginalAmount: '', reason: '' })

const dlgCipToAsset = ref(false)
const cipRow = ref({})
const cipForm = ref({ transferDate: '', remark: '' })

// 表单校验规则：登记表（useFormRules）为唯一规则，页面不再本地手写规则对象
const assetFormRef = ref(null)
const capitalFormRef = ref(null)
const transferFormRef = ref(null)
const cipFormRef = ref(null)
const adjustFormRef = ref(null)
const { rules: assetRules } = useFormRules('ASSET_CARD')
const { rules: capitalRules } = useFormRules('ASSET_CAPITAL')
const { rules: transferRules } = useFormRules('ASSET_TRANSFER')
const { rules: cipRules } = useFormRules('ASSET_CIP_TRANSFER')
const { rules: adjustRules } = useFormRules('ASSET_ADJUST')
const { submit: guardAsset } = useSubmitGuard(assetFormRef)
const { submit: guardCapital } = useSubmitGuard(capitalFormRef)
const { submit: guardTransfer } = useSubmitGuard(transferFormRef)
const { submit: guardCipToAsset } = useSubmitGuard(cipFormRef)
const { submit: guardAdjust } = useSubmitGuard(adjustFormRef)

async function loadAssets() {
  loading.value = true
  try {
    const d = await assetApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: kw.value })
    assets.value = d.list || []
    total.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/**
 * 资产建档 / 查看 / 编辑共用的弹窗入口。
 * - 不传 row：新增（空表单）；
 * - 传 row：先 assetApi.detail 回填卡片数据再开弹窗（原「查看」打开空白表单的缺陷在此修复），
 *  edit=true 仅为语义区分（行内「编辑」按钮），保存时按 aForm.id 自动走 assetApi.update。
 */
async function openAsset(row, edit = false) {
  if (row && row.id) {
    try {
      const d = await assetApi.detail(row.id)
      aForm.value = { ...emptyAsset(), ...(d || {}) }
      cardReadonly.value = !edit
      dlgAsset.value = true
      if (edit) ElMessage.info(`编辑资产：${d?.assetNo || row.assetNo || ''} ${d?.assetName || row.assetName || ''}`)
    } catch (e) { ElMessage.error('载入资产卡片失败：' + e.message) }
    return
  }
  aForm.value = emptyAsset(); cardReadonly.value = false; dlgAsset.value = true
}
async function saveAsset() {
  await guardAsset(async () => {
    try {
      if (aForm.value.id) {
        // 编辑保存按勾稽式重算净值（净值 = 原值 - 累计折旧），
        // 避免表单里的旧净值被后端 #13 强勾稽拦截（改原值后净值得联动）。
        const f = { ...aForm.value }
        const orig = Number(f.originalAmount) || 0
        const accum = Number(f.accumDepr) || 0
        f.netAmount = Math.round((orig - accum) * 100) / 100
        await assetApi.update(f)
      } else { await assetApi.create(aForm.value) }
      ElMessage.success('已保存'); dlgAsset.value = false; loadAssets()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function depreciate(row) {
  try { await ElMessageBox.confirm('确认对资产「' + row.assetName + '」计提当月折旧？', '计提折旧', { type: 'warning' }) } catch { return }
  try { const a = await assetApi.depreciate(row.id); ElMessage.success('本月折旧已计提，净值 ' + fmt(a.netAmount)); loadAssets() } catch (e) { ElMessage.error(e.message) }
}
async function depreciateMonthBatch() {
  try { await ElMessageBox.confirm('将对全部在用资产按月生成折旧批单并提交审批？', '批量计提折旧', { type: 'warning' }) } catch { return }
  try { const n = await assetApi.depreciateMonth(); ElMessage.success('已生成折旧批单并提交审批，待计提 ' + n + ' 项'); loadAssets() } catch (e) { ElMessage.error(e.message) }
}
async function scrap(row) {
  try {
    await ElMessageBox.confirm('确认报废资产「' + row.assetName + '」？', '报废（提交审批）', { type: 'warning' })
    await assetApi.scrap(row.id); ElMessage.success('报废已提交审批'); loadAssets()
  } catch (e) {
    // 只放行取消/关闭，真实 API 错误必须可见（旧版整段吞掉＝「点了没反应」）
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e && e.message ? e.message : String(e))
  }
}
function openTransfer(row) {
  transferRow.value = row
  transferForm.value = { targetDeptId: null, targetCompanyId: null, remark: '' }
  dlgTransfer.value = true
}
async function confirmTransfer() {
  const row = transferRow.value
  const f = transferForm.value
  await guardTransfer(async () => {
    try {
      await assetApi.transfer(row.id, f.targetDeptId, f.targetCompanyId, f.remark || '手工调拨')
      ElMessage.success('调拨已提交审批'); dlgTransfer.value = false; loadAssets()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function doDisposeSell(row) {
  try {
    const { value: sellAmount } = await ElMessageBox.prompt('输入对外销售金额', '对外销售处置（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入金额' })
    await assetApi.disposeSell(row.id, Number(sellAmount))
    ElMessage.success('外销处置已提交审批'); loadAssets()
  } catch (e) {
    // 同上——外销金额非法/权限不足/状态机拦截都发生在 prompt 之后的同一个 try 里
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e && e.message ? e.message : String(e))
  }
}
async function doCheckDiff(row) {
  try {
    // 乙案：本审批只作差异确认——通过环节不产生结转凭证，账面在总账域结转前分文不动。
    // 原症状＝单据 APPROVED＋记录现有却呈现"差异已处理"，语义与账实脱节⇒把规则如实写进发起提示。
    const { value: diffAmount } = await ElMessageBox.prompt('输入盘点差异金额（正=盘盈/负=盘亏）。注意：本审批仅为差异确认，通过后不自动生成结转凭证，账面不变；差异结转须在总账域另起「盘点差异结转」做账，请在提交前知会总账岗。', '盘点差异处理（提交审批）', { confirmButtonText: '下一步', cancelButtonText: '取消', inputPattern: /^-?\d+(\.\d+)?$/, inputErrorMessage: '请输入金额' })
    // X-06：差异说明改人工输入。旧版直接传死值 '盘点差异'，而后端 checkDiff 又把该形参整个丢弃
    // ⇒ 差异成因从来没有载体；现说明随变动台账写入数据库，故必须先有内容才放行。
    const { value: remark } = await ElMessageBox.prompt('填写差异说明（盘盈/盘亏成因，随变动台账记录，事后追溯看这一句）', '盘点差异说明', { confirmButtonText: '提交审批', cancelButtonText: '取消', inputValidator: (v) => (v && v.trim() ? true : '请填写差异说明') })
    await assetApi.checkDiff(row.id, Number(diffAmount), remark.trim())
    ElMessage.success('盘点差异已提交审批；审批通过仅为差异确认，结转凭证需在总账域另行制单（盘点差异结转）'); loadAssets()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e && e.message ? e.message : String(e))
  }
}
function openAdjustValue(row) {
  adjustRow.value = row
  adjustForm.value = { newOriginalAmount: '', reason: '' }
  dlgAdjust.value = true
}
async function confirmAdjustValue() {
  const row = adjustRow.value
  const f = adjustForm.value
  await guardAdjust(async () => {
    try {
      await assetApi.adjustValue(row.id, Number(f.newOriginalAmount), f.reason.trim())
      ElMessage.success('原值调整已提交审批'); dlgAdjust.value = false; loadAssets()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function doImpair(row) {
  try {
    const { value: impairmentAmount } = await ElMessageBox.prompt('输入减值金额', '减值准备（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入金额' })
    await assetApi.impair(row.id, Number(impairmentAmount))
    ElMessage.success('减值已提交审批'); loadAssets()
  } catch (e) {
    // 同上——减值分支的服务端拒绝此前被静默丢弃，列表不刷新、也无任何提示
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e && e.message ? e.message : String(e))
  }
}
async function showNet(row) { try { const nv = await assetApi.netValue(row.id); ElMessage.info('资产净值：' + fmt(nv)) } catch (e) { ElMessage.error(e.message) } }

// X-06读取环节：变动台账此前"只写不读"，卡片页补一个消费者
const CHANGE_TYPE_LABEL = { VALUE_ADJUST: '原值调整', CHECK_DIFF: '盘点差异', IMPAIR: '减值', DISPOSE: '处置', TRANSFER: '调拨' }
const CHANGE_METHOD_LABEL = { WF: '审批流变动', DIRECT: '直接变动' }
const dlgChangeLog = ref(false)
const changeLogLoading = ref(false)
const changeLogs = ref([])
const changeLogAssetName = ref('')
async function openChangeLog(row) {
  changeLogAssetName.value = row.assetName || row.assetNo || `资产#${row.id}`
  changeLogs.value = []
  dlgChangeLog.value = true
  changeLogLoading.value = true
  try {
    changeLogs.value = await assetApi.changeLogs(row.id)
  } catch (e) {
    ElMessage.error(e && e.message ? e.message : String(e))
  } finally {
    changeLogLoading.value = false
  }
}

function openCapital() { capForm.value = { amount: 0, assetName: '', deptId: null, companyId: null, remark: '', budgetItemId: null }; loadBudgetItems(); dlgCapital.value = true }
async function saveCapital() {
  await guardCapital(async () => {
    try {
      await assetApi.capitalRequest(capForm.value.amount, capForm.value.assetName, capForm.value.deptId, capForm.value.companyId, capForm.value.remark, capForm.value.budgetItemId)
      ElMessage.success('资本性支出申请已提交审批')
      dlgCapital.value = false
    } catch (e) { ElMessage.error(e.message) }
  })
}

// CER 预算集成：预算科目下拉数据（bg_item 可用预算明细）
// 本下拉属于「单据发起页的引用选择」，不是预算管理列表。旧代码走 budgetApi.itemPage
// （/tenant/budget/item/page），该端点套个人 data_scope 五级过滤——DataScopeKit:61-72 对 scope=4(DEPT)
// 注入 dept_id = 登录人部门，而现库 bg_item 9 行有 8 行 dept_id 为 NULL（预算按公司下达、未按部门拆），
// 于是 DEPT 档员工（现库正证＝剧本岗 3872：company_id=1／dept_id=1019／多角色 min(data_scope)=4）恒命中 0 行，
// "显式指定预算科目"这条自助通道只剩非部门档的外岗可用（同一接口对 SELF 档降级本公司后回 9 行）⇒ 下拉内容随角色而变。
// 改走 已办结的引用选择规则 mdApi.budgets（/tenant/budget/item/pick＝按公司放行＋跨公司依旧隔离），
// 与 useBill.js:344/378、ExpenseCreateTab.vue:497 三处同源，避免另立第二处真值。
// 空集/查询失败不再静默置空：把成因就地写进字段下方提示（留空仍走系统默认处理，不编造科目行）。
const budgetItems = ref([])
const budgetItemsNotice = ref('')
// pick 接口回的是 bg_item 裸行，没有科目名：dimKey 实测形如「2025-COMPANY1-DEPTnull-ITEM331」
// 字面 null＋内部 id，不能上屏；用费项主数据把 itemId 换成名称（/expense-item/tree 无需授权，
// 与 BudgetExecution.vue:242-245 同一映射），补不到名时只显示年度与金额，仍不暴露主键。
const budgetItemNames = ref({})
async function loadBudgetItemNames() {
  if (Object.keys(budgetItemNames.value).length) return
  try {
    const { mdApi } = await import('../../api')
    const d = await mdApi.expenseItems()
    const arr = Array.isArray(d) ? d : d?.list || []
    budgetItemNames.value = Object.fromEntries(arr.map((x) => [String(x.id), [x.itemCode || x.code, x.itemName || x.name].filter(Boolean).join(' ')]))
  } catch { budgetItemNames.value = {} }
}
/** 预算科目选项文案：费项名＋年度/期间＋金额，不让内部主键上屏 */
function budgetOptionLabel(b) {
  const money = (v) => Number(v || 0).toLocaleString()
  const avail = Number(b.totalAmount || 0) + Number(b.adjustAmount || 0)
    - Number(b.occupyAmount || 0) - Number(b.usedAmount || 0)
  const name = budgetItemNames.value[String(b.itemId)] || '预算科目'
  const period = [b.budgetYear ? `${b.budgetYear}年` : '', b.budgetPeriod ? `${b.budgetPeriod}月` : ''].join('')
  return `${name}${period ? ' · ' + period : ''}（总额 ${money(b.totalAmount)} / 可用 ${money(avail)}）`
}
async function loadBudgetItems() {
  if (budgetItems.value.length) return
  budgetItemsNotice.value = ''
  loadBudgetItemNames()
  try {
    const { mdApi } = await import('../../api')
    budgetItems.value = (await mdApi.budgets({ pageNum: 1, pageSize: 200 })).list || []
    if (!budgetItems.value.length) {
      budgetItemsNotice.value = '当前公司没有可选的预算科目行（预算未按本公司下达）；留空将由系统默认选取预算线'
    }
  } catch (e) {
    budgetItems.value = []
    budgetItemsNotice.value = '预算科目加载失败：' + (e?.message || '未知原因') + '；留空将由系统默认选取预算线'
  }
}

function openCipToAsset(row) {
  cipRow.value = row
  cipForm.value = { transferDate: '', remark: '' }
  dlgCipToAsset.value = true
}
async function confirmCipToAsset() {
  await guardCipToAsset(async () => {
    try {
      await assetApi.cipToAsset(cipRow.value.id, cipForm.value.transferDate, cipForm.value.remark)
      ElMessage.success('转固已提交审批'); dlgCipToAsset.value = false; loadAssets()
    } catch (e) { ElMessage.error(e.message) }
  })
}

/* 在建工程转固入口
 * 点击后拉取在途 CIP（GET /api/tenant/cip/list?status=IN_PROGRESS）→ 弹窗选择 → POST /api/tenant/cip/to-asset {cipId}。
 * 后端并行实现中：接口 404 时提示「转固流程入口准备中」并不阻断页面其他操作。 */
const dlgCipFlow = ref(false)
const cipFlowRows = ref([])
const cipFlowSelected = ref(null)
const cipFlowLoading = ref(false)
const cipFlowSubmitting = ref(false)
// transferToAsset 真实契约要素
const cipFlowForm = ref({ categoryId: null, deptId: null, transferDate: '', depreciationMethod: 'LINEAR', depreciationMonths: 60, remark: '' })
// 第2批：转固弹窗原为装饰红星（el-form-item required 无 prop），改挂中央框架 CIP_TRANSFER 规则集
const cipFlowFormRef = ref(null)
const { rules: cipFlowRules } = useFormRules('CIP_TRANSFER')
const cipFlowCats = ref([])
const cipFlowDepts = ref([])

const fmtCip = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

async function openCipFlow() {
  dlgCipFlow.value = true
  cipFlowSelected.value = null
  cipFlowRows.value = []
  cipFlowForm.value = { categoryId: null, deptId: null, transferDate: '', depreciationMethod: 'LINEAR', depreciationMonths: 60, remark: '' }
  cipFlowLoading.value = true
  try {
    const d = await cipFlowApi.inProgress()
    const list = Array.isArray(d) ? d : (d?.list || d?.records || [])
    // 在途 = 尚未进入转固流程的 CIP（NORMAL）；TRANSFERRING/TRANSFERRED 已在途/已转固
    cipFlowRows.value = list.filter((r) => !r.status || r.status === 'NORMAL')
    if (!cipFlowRows.value.length) ElMessage.info('当前无在途在建工程项目')
  } catch (e) {
    // 404（契约未部署）→ 友好提示，不阻断
    if (e?.status === 404 || /404/.test(String(e?.message))) {
      ElMessage.info('转固流程入口准备中')
    } else {
      ElMessage.error(e?.message || '获取在建工程列表失败')
    }
  } finally { cipFlowLoading.value = false }
  // 选项惰性加载（类别 + 部门树），失败不阻断
  try {
    if (!cipFlowCats.value.length) {
      const c = await assetApi.categoryPage({ pageNum: 1, pageSize: 200 })
      cipFlowCats.value = (c && (c.records || c.list)) || []
    }
    if (!cipFlowDepts.value.length) {
      const t = await orgApi.deptTree()
      cipFlowDepts.value = (t && (t.records || t.list || t)) || []
    }
  } catch (_) { /* 选项加载失败不阻断转固入口 */ }
}

async function confirmCipFlow() {
  const row = cipFlowSelected.value
  if (!row?.id) { ElMessage.warning('请先选择在建工程项目'); return }
  try {
    await ElMessageBox.confirm(`确认将在建工程「${row.cipName || row.id}」转固为固定资产？`, '转固确认', { type: 'warning', closeOnClickModal: false })
  } catch (_) { return }
  // 第2批：装饰红星转真校验（原 4 个 toast 式 if 改为 EP 标准 validate，红字落字段）
  if (!(await validateForm(cipFlowFormRef.value))) return
  cipFlowSubmitting.value = true
  try {
    await cipFlowApi.toAsset(row.id, {
      categoryId: cipFlowForm.value.categoryId,
      deptId: cipFlowForm.value.deptId,
      transferDate: cipFlowForm.value.transferDate,
      depreciationMethod: cipFlowForm.value.depreciationMethod,
      depreciationMonths: cipFlowForm.value.depreciationMonths,
      remark: cipFlowForm.value.remark,
    })
    ElMessage.success('转固已提交（转固审批已发起）')
    dlgCipFlow.value = false
    loadAssets()
  } catch (e) {
    // 404（契约未部署）→ 友好提示；业务校验失败原样展示后端错误
    if (e?.status === 404 || /404/.test(String(e?.message))) {
      ElMessage.info('转固流程入口准备中')
    } else {
      ElMessage.error(e?.message || '转固失败，请稍后重试')
    }
  } finally { cipFlowSubmitting.value = false }
}

function handleSelectionChange(val) { selRows.value = val }

onMounted(() => { loadAssets() })

defineExpose({ loadAssets })
// InlineActions approve/reject handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: loadAssets })
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
.muted-no { color: #909399; }
</style>
