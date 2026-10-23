<template>
  <div class="ops-console">
    <el-card shadow="never">
      <template #header>
        <span class="header-title">{{ $t('sharing.ops.pageTitle') }}</span>
        <span class="header-note">{{ $t('sharing.ops.hintHeader') }}</span>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- ---------------- 1. 分配策略配置 ---------------- -->
        <el-tab-pane :label="$t('sharing.ops.tabPolicy')" name="policy">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item :label="$t('sharing.common.colKeyword')">
              <el-input v-model="policyQ.keyword" :placeholder="$t('sharing.ops.phPolicyKeyword')" clearable style="width: 200px" @keyup.enter="policyReset" />
            </el-form-item>
            <el-form-item :label="$t('sharing.common.colStrategy')">
              <el-select v-model="policyQ.strategy" clearable :placeholder="$t('common.all')" style="width: 140px" @change="policyReset">
                <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="policyReset">{{ $t('common.search') }}</el-button>
              <el-button @click="openEffective">{{ $t('sharing.ops.btnEffectiveTrial') }}</el-button>
              <el-button v-permission="'wf:config:update'" type="success" @click="openPolicy(null)">{{ $t('sharing.ops.btnAddPolicy') }}</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="policyRows" v-loading="policyLoading" size="small" stripe border max-height="440" :empty-text="$t('sharing.ops.emptyPolicy')">
            <el-table-column prop="modelKey" :label="$t('sharing.ops.colProcess')" width="140" show-overflow-tooltip />
            <el-table-column prop="nodeKey" :label="$t('sharing.common.colNode')" width="130" show-overflow-tooltip />
            <el-table-column prop="policyName" :label="$t('sharing.ops.colPolicyName')" min-width="140" show-overflow-tooltip />
            <el-table-column :label="$t('sharing.ops.colAssignMode')" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="strategyTag(row.strategy)" effect="plain">{{ strategyLabel(row.strategy) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="normalHours" :label="$t('sharing.ops.colNormalHours')" width="105" align="right" />
            <el-table-column prop="urgentHours" :label="$t('sharing.ops.colUrgentHours')" width="105" align="right" />
            <el-table-column prop="balanceIntervalHours" :label="$t('sharing.ops.colBalanceInterval')" width="105" align="right" />
            <el-table-column prop="balanceBatchSize" :label="$t('sharing.ops.colBalanceBatch')" width="100" align="right" />
            <el-table-column :label="$t('sharing.common.colEnabled')" width="72" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? t('sharing.common.colEnabled') : t('sharing.common.colDisabled') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="$t('common.remark')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="$t('common.operation')" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'wf:config:update'" link type="primary" size="small" @click="openPolicy(row)">{{ $t('sharing.common.btnEdit') }}</el-button>
                <el-button v-permission="'wf:config:update'" link type="danger" size="small" @click="delPolicy(row)">{{ $t('sharing.common.btnDelete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="policyPager" @change="loadPolicy" />
        </el-tab-pane>

        <!-- ---------------- 2. 分配规则顺序解析试算 ---------------- -->
        <el-tab-pane :label="$t('sharing.ops.tabRule')" name="rule">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 10px"
            :title="$t('sharing.ops.hintRuleOrder')"
          />
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('sharing.common.colWorkItem')">
              <el-select v-model="ruleForm.taskType" clearable :placeholder="$t('sharing.common.colAny')" style="width: 130px">
                <el-option :label="$t('sharing.common.colAudit')" value="AUDIT" />
                <el-option :label="$t('sharing.common.colBookkeep')" value="BOOKKEEP" />
                <el-option :label="$t('sharing.common.colPay')" value="PAY" />
                <el-option :label="$t('sharing.common.colVoucher')" value="VOUCHER" />
                <el-option :label="$t('sharing.common.colArchive')" value="ARCHIVE" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('field.billType')"><el-input v-model="ruleForm.bizType" :placeholder="$t('sharing.ops.phBizTypeSample')" style="width: 150px" /></el-form-item>
            <el-form-item :label="$t('field.amount')"><el-input-number v-model="ruleForm.amount" :min="0" :precision="2" :controls="false" style="width: 130px" /></el-form-item>
            <el-form-item :label="$t('sharing.ops.labCompanyId')">
              <el-select v-model="ruleForm.companyId" filterable clearable :placeholder="$t('common.all')" style="width: 170px">
                <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('sharing.ops.labExpenseItemId')">
              <el-select v-model="ruleForm.expenseItemId" filterable clearable :placeholder="$t('common.all')" style="width: 170px">
                <el-option v-for="it in expenseItems" :key="it.id" :label="it.itemName" :value="it.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('sharing.ops.labCurrency')"><el-input v-model="ruleForm.currencyCode" placeholder="CNY" style="width: 90px" /></el-form-item>
            <el-form-item :label="$t('sharing.ops.labLegalEntityId')">
              <el-select v-model="ruleForm.legalEntityId" filterable clearable :placeholder="$t('common.all')" style="width: 170px">
                <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="ruleLoading" @click="doRuleResolve">{{ $t('sharing.common.btnDryRun') }}</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="ruleResult" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item :label="$t('sharing.ops.colMatchedRule')">{{ ruleResult.matchedRuleName || t('sharing.ops.fallbackNoRule') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.common.colRuleCode')">{{ ruleResult.matchedRuleCode || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colEvaluated')">{{ ruleResult.evaluatedCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('field.desc')">{{ ruleResult.note || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="ruleResult" :data="ruleResult.trace || []" size="small" stripe border max-height="380" :empty-text="$t('sharing.ops.emptyTrace')">
            <el-table-column prop="priority" :label="$t('sharing.common.colPriority')" width="80" align="center" />
            <el-table-column prop="ruleCode" :label="$t('sharing.common.colRuleCode')" width="150" show-overflow-tooltip />
            <el-table-column prop="ruleName" :label="$t('sharing.common.colRuleName')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="dimension" :label="$t('sharing.ops.colDimension')" width="130" />
            <el-table-column prop="condition" :label="$t('sharing.ops.colCondition')" min-width="150" show-overflow-tooltip />
            <el-table-column :label="$t('sharing.common.colHit')" width="72" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.matched ? 'success' : 'info'">{{ row.matched ? t('sharing.common.colHit') : t('sharing.ops.labelMiss') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="$t('sharing.ops.colJudgeNote')" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- ---------------- 3. 自动分配 + 调度日志 ---------------- -->
        <el-tab-pane :label="$t('sharing.ops.tabDispatch')" name="dispatch">
          <el-card shadow="never" class="sub-card">
            <template #header><b>{{ $t('sharing.ops.subAutoAssign') }}</b>{{ $t('sharing.ops.hintAutoAssignDryRun') }}</template>
            <el-form :inline="true" size="small">
              <el-form-item :label="$t('sharing.ops.labTaskRowId')"><el-input-number v-model="assignForm.taskRowId" :min="0" :controls="false" style="width: 140px" /></el-form-item>
              <el-form-item :label="$t('sharing.common.colPriority')">
                <el-select v-model="assignForm.priority" style="width: 110px">
                  <el-option :label="$t('sharing.common.colNormal')" value="NORMAL" />
                  <el-option :label="$t('sharing.common.colUrgent')" value="URGENT" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('sharing.common.btnDryRun')">
                <el-switch v-model="assignForm.dryRun" />
              </el-form-item>
              <el-form-item>
                <el-button v-permission="'wf:task:reassign'" type="primary" :loading="assignLoading" @click="doAssign">{{ $t('sharing.ops.btnExecute') }}</el-button>
              </el-form-item>
            </el-form>
            <el-descriptions v-if="assignResult" :column="4" border size="small">
              <el-descriptions-item :label="$t('sharing.common.colStrategy')">{{ strategyLabel(assignResult.strategy) }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.colMatchedRule')">{{ assignResult.matchedRuleCode || '—' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.common.btnDryRun')">{{ assignResult.dryRun ? t('common.yes') : t('common.no') }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.labelSaved')">{{ assignResult.applied ? t('common.yes') : t('common.no') }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.colChosenHandler')">{{ assignResult.decision?.chosenUserName || t('sharing.ops.fallbackUnassigned') }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.colDecisionNote')" :span="3">{{ assignResult.decision?.reason || '—' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.colCandidates')" :span="4">{{ assignResult.decision?.candidateSnapshot || '—' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.ops.colLimitHours')">{{ assignResult.limitHours ?? '—' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('sharing.common.colDueTime')" :span="3">{{ formatDateTime(assignResult.dueTime) || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="never" class="sub-card" style="margin-top: 12px">
            <template #header><b>{{ $t('sharing.ops.subDispatchLog') }}</b>{{ $t('sharing.ops.hintDispatchLog') }}</template>
            <el-form :inline="true" size="small" style="margin-bottom: 8px">
              <el-form-item :label="$t('sharing.ops.labTaskId')"><el-input v-model="logQ.taskId" clearable style="width: 150px" @keyup.enter="logReset" /></el-form-item>
              <el-form-item :label="$t('field.docNo')"><el-input v-model="logQ.bizNo" clearable style="width: 150px" @keyup.enter="logReset" /></el-form-item>
              <el-form-item :label="$t('sharing.common.colStrategy')">
                <el-select v-model="logQ.strategy" clearable :placeholder="$t('common.all')" style="width: 130px" @change="logReset">
                  <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
              </el-form-item>
              <el-form-item><el-button type="primary" @click="logReset">{{ $t('common.search') }}</el-button></el-form-item>
            </el-form>
            <el-table :data="logRows" v-loading="logLoading" size="small" stripe border max-height="380" :empty-text="$t('sharing.ops.emptyLog')">
              <el-table-column prop="bizNo" :label="$t('field.docNo')" width="140" show-overflow-tooltip />
              <el-table-column prop="nodeName" :label="$t('sharing.common.colNode')" width="130" show-overflow-tooltip />
              <el-table-column prop="dispatchMode" :label="$t('sharing.common.colMethod')" width="100" :formatter="(row, col, val) => dispatchModeLabel(val)" />
              <el-table-column prop="strategy" :label="$t('sharing.common.colStrategy')" width="110">
                <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
              </el-table-column>
              <el-table-column prop="matchedRuleCode" :label="$t('sharing.ops.colMatchedRule')" width="130" show-overflow-tooltip />
              <el-table-column prop="chosenUserName" :label="$t('sharing.ops.colChosenHandler')" width="110" />
              <el-table-column prop="decisionReason" :label="$t('sharing.ops.colDecisionNote')" min-width="160" show-overflow-tooltip />
              <el-table-column :label="$t('sharing.common.colSuccess')" width="72" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? t('common.yes') : t('common.no') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" :label="$t('field.time')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
              <el-table-column :label="$t('common.operation')" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" :disabled="!row.taskId" @click="openChain(row)">{{ $t('sharing.ops.btnChain') }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <TablePager :pager="logPager" @change="loadLog" />
          </el-card>
        </el-tab-pane>

        <!-- ---------------- 4. 双时限 ---------------- -->
        <el-tab-pane :label="$t('sharing.ops.tabTimeLimit')" name="timelimit">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            :title="$t('sharing.ops.hintDueRule')" />
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('sharing.ops.labScanLimit')"><el-input-number v-model="limitQ.limit" :min="1" :max="1000" style="width: 130px" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="dueLoading" @click="loadDueScan">{{ $t('sharing.ops.btnDueScan') }}</el-button>
              <el-button v-permission="'wf:config:update'" :loading="backfillLoading" @click="doBackfill">{{ $t('sharing.ops.btnBackfill') }}</el-button>
              <el-button @click="loadScanReport">{{ $t('sharing.ops.btnJobReport') }}</el-button>
              <el-button v-permission="'wf:config:update'" @click="markVisible = true">{{ $t('sharing.ops.btnMarkPriority') }}</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="dueScan" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item :label="$t('sharing.ops.colScanTime')">{{ formatDateTime(dueScan.scannedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colOpenTasks')">{{ dueScan.pendingTotal ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colOverdueTasks')">{{ dueScan.overdueTotal ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colNoDueTime')">{{ dueScan.noDueTimeCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="dueScan" :data="dueScan.overdue || []" size="small" stripe border max-height="320" :empty-text="$t('sharing.ops.emptyNoOverdue')">
            <el-table-column prop="bizNo" :label="$t('field.docNo')" width="140" show-overflow-tooltip />
            <el-table-column prop="nodeName" :label="$t('sharing.common.colNode')" width="140" show-overflow-tooltip />
            <el-table-column prop="taskId" :label="$t('sharing.ops.labTaskId')" width="150" show-overflow-tooltip />
            <el-table-column prop="assigneeId" :label="$t('sharing.ops.colAssigneeId')" width="110" />
            <el-table-column :label="$t('sharing.common.colPriority')" width="88" align="center">
              <template #default="{ row }">{{ row.priority === 'URGENT' ? t('sharing.common.colUrgent') : t('sharing.common.colNormal') }}</template>
            </el-table-column>
            <el-table-column :label="$t('sharing.common.colDueTime')" width="160" :formatter="(r, c, v) => formatDateTime(v)" prop="dueTime" />
            <el-table-column prop="overdueHours" :label="$t('sharing.ops.colOverdueHours')" width="100" align="right" />
          </el-table>

          <el-descriptions v-if="scanReport" :column="3" border size="small" style="margin-top: 12px">
            <el-descriptions-item :label="$t('sharing.ops.colScanTime')">{{ formatDateTime(scanReport.scannedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colOverdueTenants')">{{ scanReport.overdueTenantCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colOverdueTotal')">{{ scanReport.overdueTotal ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('field.desc')" :span="3">{{ scanReport.note || '—' }}</el-descriptions-item>
          </el-descriptions>

          <el-dialog v-model="markVisible" :title="$t('sharing.ops.titleMark')" width="420px">
            <el-form ref="markFormRef" :model="markForm" :rules="markRules" label-width="100px" size="small">
              <el-form-item :label="$t('sharing.ops.labTaskRowId')" prop="taskRowId"><el-input-number v-model="markForm.taskRowId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
              <el-form-item :label="$t('sharing.common.colPriority')" prop="priority">
                <el-select v-model="markForm.priority" style="width: 100%">
                  <el-option :label="$t('sharing.common.colNormal')" value="NORMAL" />
                  <el-option :label="$t('sharing.common.colUrgent')" value="URGENT" />
                </el-select>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="markVisible = false">{{ $t('common.cancel') }}</el-button>
              <el-button type="primary" :loading="markLoading" @click="doMark">{{ $t('common.confirm') }}</el-button>
            </template>
          </el-dialog>
        </el-tab-pane>

        <!-- ---------------- 5. 制证失败·执行结果视图（A12） ---------------- -->
        <el-tab-pane :label="$t('sharing.ops.tabVoucherFail')" name="vouchfail">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            :title="$t('sharing.ops.hintVoucherFail')" />
          <el-alert v-if="vfError" type="error" :closable="false" show-icon style="margin-bottom: 10px" :title="vfError" />
          <el-descriptions v-if="vfStats" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item :label="$t('sharing.ops.colFailEvents')">{{ vfStats.allEvents ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colNotRegenerated')">{{ vfStats.allPending ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colScanScope')">{{ $t('sharing.ops.txtRecentEvents', { v1: vfStats.scannedRows ?? 0 }) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colTruncated')">{{ vfStats.truncated ? t('sharing.ops.labelYesLatest') + vfStats.scanLimit + ' 条）' : t('common.no') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('sharing.ops.colBySource')" :span="4">
              <el-tag v-for="s in vfStats.bySource || []" :key="s.sourceType" size="small" style="margin-right: 8px">
                {{ $t('sharing.ops.textSourceCount', { v1: s.sourceTypeName, v2: s.pending, v3: s.events }) }}</el-tag>
              <span v-if="!(vfStats.bySource || []).length">—</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form :inline="true" size="small" style="margin-bottom: 8px">
            <el-form-item :label="$t('common.status')">
              <el-select v-model="vfQ.status" clearable :placeholder="$t('sharing.common.colAny')" style="width: 130px" @change="vfReset">
                <el-option :label="$t('sharing.ops.colNotRegenerated')" value="PENDING" />
                <el-option :label="$t('sharing.ops.colRegenerated')" value="RESOLVED" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('sharing.common.colSource')">
              <el-select v-model="vfQ.sourceType" clearable :placeholder="$t('sharing.common.colAny')" style="width: 140px" @change="vfReset">
                <el-option v-for="s in VF_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('field.docNo')">
              <el-input v-model="vfQ.billNo" clearable :placeholder="$t('sharing.ops.phBillNoContains')" style="width: 170px" @keyup.enter="vfReset" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="vfLoading" @click="vfReset">{{ $t('common.search') }}</el-button>
              <el-button @click="loadVfStats">{{ $t('sharing.ops.btnRefreshStats') }}</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="vfRows" v-loading="vfLoading" size="small" stripe border max-height="380" :empty-text="$t('sharing.ops.emptyVoucherFail')">
            <el-table-column prop="operTime" :label="$t('sharing.ops.colEventTime')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column prop="sourceTypeName" :label="$t('sharing.common.colSource')" width="100" />
            <el-table-column prop="billNo" :label="$t('sharing.common.colDocNo')" width="150" show-overflow-tooltip />
            <el-table-column prop="billTypeName" :label="$t('field.billType')" width="120">
              <template #default="{ row }">{{ row.billTypeName || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('sharing.common.colDocStatus')" width="100">
              <template #default="{ row }">{{ billStatusLabel(row.billStatus) }}</template>
            </el-table-column>
            <el-table-column :label="$t('field.period')" width="90">
              <template #default="{ row }">{{ periodLabel(row) }}</template>
            </el-table-column>
            <el-table-column :label="$t('field.result')" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 'RESOLVED' ? 'success' : 'danger'">{{ row.resultName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="$t('sharing.ops.colFailReason')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="$t('sharing.ops.colNewVoucher')" width="130">
              <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openVfDetail(row)">{{ $t('field.detail') }}</el-button>
                <el-tooltip v-permission="'sharing:voucher:generate'" :content="row.retryHint || ''" placement="top" :disabled="row.retriable">
                  <el-button link type="warning" size="small"
                    :disabled="!row.retriable" :loading="vfRetrying" @click="doVfRetry(row)">{{ $t('sharing.ops.btnRegenerate') }}</el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="vfPager" @change="loadVfPage" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 策略编辑 -->
    <el-dialog v-model="policyVisible" :title="policyForm.id ? t('sharing.console.dlgEditPolicy') : t('sharing.console.dlgCreatePolicy')" width="620px" destroy-on-close>
      <el-form ref="policyFormRef" :model="policyForm" :rules="policyRules" label-width="120px" size="small">
        <el-form-item :label="$t('sharing.ops.phProcessKey')" prop="modelKey"><el-input v-model="policyForm.modelKey" :placeholder="$t('sharing.ops.phStarDefault')" /></el-form-item>
        <el-form-item :label="$t('sharing.common.colNodeKey')" prop="nodeKey"><el-input v-model="policyForm.nodeKey" :placeholder="$t('sharing.ops.phStarDefault')" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colPolicyName')" prop="policyName"><el-input v-model="policyForm.policyName" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colAssignMode')" prop="strategy">
          <el-select v-model="policyForm.strategy" style="width: 100%">
            <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sharing.ops.colNormalHours')"><el-input-number v-model="policyForm.normalHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colUrgentHours')"><el-input-number v-model="policyForm.urgentHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colBalanceInterval')"><el-input-number v-model="policyForm.balanceIntervalHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colBalanceBatch')"><el-input-number v-model="policyForm.balanceBatchSize" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colWeightVolume')"><el-input-number v-model="policyForm.smartWeightVolume" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colWeightUrgency')"><el-input-number v-model="policyForm.smartWeightUrgency" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.ops.colWeightPriority')"><el-input-number v-model="policyForm.smartWeightPriority" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('sharing.common.colEnabled')"><el-switch v-model="policyForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="policyForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="policySaving" @click="savePolicy">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 生效策略试算 -->
    <el-dialog v-model="effectiveVisible" :title="$t('sharing.ops.titleEffective')" width="520px">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('sharing.ops.phProcessKey')"><el-input v-model="effectiveQ.modelKey" :placeholder="$t('sharing.ops.phOptional')" style="width: 150px" /></el-form-item>
        <el-form-item :label="$t('sharing.common.colNodeKey')"><el-input v-model="effectiveQ.nodeKey" :placeholder="$t('sharing.ops.phOptional')" style="width: 150px" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="effectiveLoading" @click="doEffective">{{ $t('common.search') }}</el-button></el-form-item>
      </el-form>
      <el-descriptions v-if="effectiveResult" :column="2" border size="small">
        <el-descriptions-item :label="$t('sharing.ops.colPolicyName')">{{ effectiveResult.policyName || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colAssignMode')">{{ strategyLabel(effectiveResult.strategy) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colNormalHours')">{{ effectiveResult.normalHours ?? '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colUrgentHours')">{{ effectiveResult.urgentHours ?? '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colProcess')">{{ effectiveResult.modelKey || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colNode')">{{ effectiveResult.nodeKey || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.desc')" :span="2">{{ $t('sharing.ops.hintTimeoutFallback') }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else :description="$t('sharing.ops.emptyEffective')" :image-size="60" />
    </el-dialog>

    <!-- 决策链 -->
    <el-dialog v-model="chainVisible" :title="$t('sharing.ops.titleChain')" width="640px">
      <el-table :data="chainRows" v-loading="chainLoading" size="small" stripe border max-height="400" :empty-text="$t('sharing.ops.emptyDecision')">
        <el-table-column prop="dispatchMode" :label="$t('sharing.common.colMethod')" width="90" :formatter="(row, col, val) => dispatchModeLabel(val)" />
        <el-table-column prop="strategy" :label="$t('sharing.common.colStrategy')" width="100">
          <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
        </el-table-column>
        <el-table-column prop="chosenUserName" :label="$t('sharing.ops.colChosenHandler')" width="110" />
        <el-table-column prop="decisionReason" :label="$t('sharing.ops.colDecisionNote')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" :label="$t('field.time')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
      </el-table>
    </el-dialog>
    <!-- 制证失败·行明细钻取 -->
    <el-dialog v-model="vfDetailVisible" :title="$t('sharing.ops.titleVoucherFailDetail')" width="640px">
      <el-descriptions v-if="vfDetail" :column="2" border size="small">
        <el-descriptions-item :label="$t('sharing.common.colDocNo')">{{ vfDetail.billNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.labBillId')">{{ vfDetail.billId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.billType')">{{ vfDetail.billTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colDocStatus')">{{ billStatusLabel(vfDetail.billStatus) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colSource')">{{ vfDetail.sourceTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.labCompanyPeriod')">{{ vfDetail.companyId ?? '—' }} / {{ periodLabel(vfDetail) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colEventTime')">{{ formatDateTime(vfDetail.operTime) || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.result')">{{ vfDetail.resultName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colNewVoucher')" :span="2">{{ vfDetail.voucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colFailReason')" :span="2">
          <div style="white-space: pre-wrap; line-height: 1.6">{{ vfDetail.reason || '—' }}</div>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.btnRegenerate')" :span="2">
          <el-tag size="small" :type="vfDetail.retriable ? 'success' : 'info'">{{ vfDetail.retriable ? t('sharing.ops.labelRetriable') : t('sharing.ops.labelNotRetriable') }}</el-tag>
          <span style="margin-left: 8px">{{ vfDetail.retryHint || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="vfDetail.inconsistency" :label="$t('sharing.ops.labelCaution')" :span="2">
          <span style="color: var(--el-color-danger)">{{ vfDetail.inconsistency }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.ops.colEventRow')" :span="2">{{ vfDetail.method || '—' }} #{{ vfDetail.logId }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="vfDetailVisible = false">{{ $t('common.close') }}</el-button>
        <el-button v-permission="'sharing:voucher:generate'" type="warning" :disabled="!vfDetail?.retriable"
          :loading="vfRetrying" @click="doVfRetry(vfDetail)">{{ $t('sharing.ops.btnRegenerate') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { dispatchModeLabel } from '@/constants/enums'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wfOpsApi, sharingApi, orgApi, mdApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime } from '@/utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * 共享作业控制台（G3）：全部数据源为 WfSharedOpsController（/api/tenant/wf/ops/**）。
 * 权限：读 wf:config:list（策略）/ wf:instance:list（日志、时限、质量）；写 wf:config:update / wf:task:reassign。
 */
const STRATEGIES = computed(() => ([
  { value: 'PICK', label: t('sharing.common.btnGrab') },
  { value: 'BALANCE_TIME', label: t('sharing.ops.optBalanceTime') },
  { value: 'BALANCE_VOLUME', label: t('sharing.ops.optBalanceVolume') },
  { value: 'SMART', label: t('sharing.ops.optSmart') },
]))
const STRATEGY_MAP = Object.fromEntries(STRATEGIES.value.map((s) => [s.value, s.label]))
function strategyLabel(s) { return STRATEGY_MAP[s] || s || '—' }
function strategyTag(s) {
  return s === 'PICK' ? 'info' : s === 'SMART' ? 'success' : 'warning'
}

const tab = ref('policy')
const loadedTabs = new Set()
function onTabChange(name) {
  if (loadedTabs.has(name)) return
  loadedTabs.add(name)
  if (name === 'policy') loadPolicy()
  if (name === 'dispatch') loadLog()
  if (name === 'timelimit') loadDueScan()
}

// ---------------- 1. 分配策略 ----------------
const policyQ = reactive({ keyword: '', strategy: '' })
const policyPager = useTablePager({ mode: 'server', storageKey: 'sharing_ops_policy' })
const { rows: policyRows, loading: policyLoading } = policyPager
const policyVisible = ref(false)
const policySaving = ref(false)
const policyFormRef = ref(null)
const policyForm = reactive({})
const policyRules = computed(() => (reactive({
  modelKey: commonRules.required(t('sharing.ops.errProcessKey')),
  nodeKey: commonRules.required(t('sharing.ops.errNodeKey')),
  policyName: commonRules.required(t('sharing.ops.errPolicyName')),
  strategy: commonRules.select(t('sharing.ops.errAssignMode')),
})))

function policyReset() { policyPager.resetPage(); loadPolicy() }

async function loadPolicy() {
  await policyPager.load(async (p) => {
    const r = await wfOpsApi.policyPage({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      keyword: policyQ.keyword || undefined,
      strategy: policyQ.strategy || undefined,
    })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}

function openPolicy(row) {
  Object.keys(policyForm).forEach((k) => delete policyForm[k])
  Object.assign(policyForm, row
    ? { ...row }
    : { modelKey: '*', nodeKey: '*', strategy: 'PICK', normalHours: 24, urgentHours: 4, enabled: 1 })
  policyVisible.value = true
}

async function savePolicy() {
  if (!(await validateForms([policyFormRef]))) return
  policySaving.value = true
  try {
    if (policyForm.id) await wfOpsApi.policyUpdate(policyForm)
    else await wfOpsApi.policyCreate(policyForm)
    ElMessage.success(t('sharing.common.msgSaved'))
    policyVisible.value = false
    loadPolicy()
  } catch (e) { ElMessage.error(e.message) } finally { policySaving.value = false }
}

async function delPolicy(row) {
  try {
    await ElMessageBox.confirm(t('sharing.ops.confirmDeletePolicy'), t('sharing.ops.titleDeletePolicy'), { type: 'warning' })
    await wfOpsApi.policyDelete(row.id)
    ElMessage.success(t('sharing.common.colDeleted'))
    loadPolicy()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 生效策略试算
const effectiveVisible = ref(false)
const effectiveLoading = ref(false)
const effectiveQ = reactive({ modelKey: '', nodeKey: '' })
const effectiveResult = ref(null)
function openEffective() { effectiveVisible.value = true; effectiveResult.value = null }
async function doEffective() {
  effectiveLoading.value = true
  try {
    effectiveResult.value = await wfOpsApi.policyEffective({
      modelKey: effectiveQ.modelKey || undefined,
      nodeKey: effectiveQ.nodeKey || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { effectiveLoading.value = false }
}

// ---------------- 2. 规则试算 ----------------
const ruleForm = reactive({ taskType: '', bizType: '', amount: null, companyId: null, expenseItemId: null, currencyCode: '', legalEntityId: null })
const ruleLoading = ref(false)
const ruleResult = ref(null)
// 试算入参里的公司/费项/法人是主键：给选择器，让运维同学在试算台上猜号
const companies = ref([])
const expenseItems = ref([])
async function loadRuleRefs() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { if (n) { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) } }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { companies.value = [] }
  try {
    const tree = (await mdApi.expenseItems()) || []
    const leaves = []
    for (const it of tree) { if (it.children && it.children.length) leaves.push(...it.children); else leaves.push(it) }
    expenseItems.value = leaves
  } catch { expenseItems.value = [] }
}
async function doRuleResolve() {
  ruleLoading.value = true
  try {
    const params = {}
    Object.entries(ruleForm).forEach(([k, v]) => { if (v !== '' && v !== null && v !== undefined) params[k] = v })
    ruleResult.value = await wfOpsApi.ruleResolve(params)
  } catch (e) { ElMessage.error(e.message) } finally { ruleLoading.value = false }
}

// ---------------- 3. 自动分配 + 日志 ----------------
const assignForm = reactive({ taskRowId: null, priority: 'NORMAL', dryRun: true })
const assignLoading = ref(false)
const assignResult = ref(null)
async function doAssign() {
  if (!assignForm.taskRowId) { ElMessage.warning(t('sharing.ops.errTaskRowId')); return }
  assignLoading.value = true
  try {
    assignResult.value = await wfOpsApi.assign({ ...assignForm })
    ElMessage.success(assignForm.dryRun ? t('sharing.ops.msgDryRunDone') : t('sharing.ops.msgExecDone'))
  } catch (e) { ElMessage.error(e.message) } finally { assignLoading.value = false }
}

const logQ = reactive({ taskId: '', bizNo: '', strategy: '' })
const logPager = useTablePager({ mode: 'server', storageKey: 'sharing_ops_dispatch_log' })
const { rows: logRows, loading: logLoading } = logPager
const chainVisible = ref(false)
const chainLoading = ref(false)
const chainRows = ref([])

function logReset() { logPager.resetPage(); loadLog() }
async function loadLog() {
  await logPager.load(async (p) => {
    const r = await wfOpsApi.dispatchLogPage({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      taskId: logQ.taskId || undefined,
      bizNo: logQ.bizNo || undefined,
      strategy: logQ.strategy || undefined,
    })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}
async function openChain(row) {
  chainVisible.value = true
  chainLoading.value = true
  try {
    chainRows.value = await wfOpsApi.dispatchLogChain(row.taskId)
  } catch (e) { ElMessage.error(e.message); chainRows.value = [] } finally { chainLoading.value = false }
}

// ---------------- 4. 双时限 ----------------
const limitQ = reactive({ limit: 200 })
const dueLoading = ref(false)
const dueScan = ref(null)
const scanReport = ref(null)
const backfillLoading = ref(false)
const markVisible = ref(false)
const markLoading = ref(false)
const markForm = reactive({ taskRowId: null, priority: 'NORMAL' })
// 第2批：时限标记弹窗接入（原只有一句 toast）——任务行 ID 必须为正整数、优先级必选
const markFormRef = ref(null)
const markRules = {
  taskRowId: [{ required: true, type: 'number', min: 1, message: '请确认任务行' }],
  priority: commonRules.select(t('sharing.common.colPriority')),
}

async function loadDueScan() {
  dueLoading.value = true
  try { dueScan.value = await wfOpsApi.timeLimitDueScan(limitQ.limit) } catch (e) { ElMessage.error(e.message) } finally { dueLoading.value = false }
}
async function doBackfill() {
  backfillLoading.value = true
  try {
    const r = await wfOpsApi.timeLimitBackfill(limitQ.limit)
    ElMessage.success(t('sharing.ops.msgBackfilled', { v1: r?.backfilled ?? 0 }))
    loadDueScan()
  } catch (e) { ElMessage.error(e.message) } finally { backfillLoading.value = false }
}
async function loadScanReport() {
  try { scanReport.value = await wfOpsApi.timeLimitScanJobReport() } catch (e) { ElMessage.error(e.message) }
}
async function doMark() {
  if (!(await validateForms([markFormRef]))) return
  if (!markForm.taskRowId) { ElMessage.warning(t('sharing.ops.errTaskRowId')); return }
  markLoading.value = true
  try {
    await wfOpsApi.timeLimitMark(markForm.taskRowId, markForm.priority)
    ElMessage.success(t('sharing.ops.labelMarked'))
    markVisible.value = false
  } catch (e) { ElMessage.error(e.message) } finally { markLoading.value = false }
}

onMounted(() => {
  loadedTabs.add('policy')
  loadPolicy()
  loadRuleRefs()
})

// ---------------- 5. 制证失败·执行结果视图（A12） ----------------
const VF_SOURCES = computed(() => ([
  { value: 'EXPENSE', label: t('sharing.ops.srcExpense') },
  { value: 'EXPENSE_PAY', label: t('sharing.ops.srcVendorPay') },
  { value: 'EXP_LOAN', label: t('sharing.common.colLoan') },
  { value: 'EXP_REPAY', label: t('sharing.common.colRepay') },
]))
// 视图行里的单据状态是英文码（不臆造映射：未收录的原样显示，便于发现漏配）
const VF_BILL_STATUS = computed(() => ({
  APPROVING: t('status.approving'), APPROVED: t('sharing.ops.labelApproved'), PAID: t('status.paid'), CLOSED: t('status.closed'),
  REPAID: t('sharing.ops.labelRepaid'), REJECTED: t('status.rejected'), WITHDRAWN: t('sharing.common.colWithdrawn'), DRAFT: t('status.draft'),
}))
const vfQ = reactive({ status: '', sourceType: '', billNo: '' })
const vfPager = useTablePager({ mode: 'server', storageKey: 'sharing_ops_vouchfail' })
const { rows: vfRows, loading: vfLoading } = vfPager
const vfStats = ref(null)
const vfError = ref('')
const vfDetailVisible = ref(false)
const vfDetail = ref(null)
const vfRetrying = ref(false)

function billStatusLabel(s) { return VF_BILL_STATUS.value[s] || s || '—' }
function periodLabel(row) {
  if (!row || row.periodYear == null || row.periodMonth == null) return '—'
  return `${row.periodYear}-${String(row.periodMonth).padStart(2, '0')}`
}

function vfReset() { vfPager.resetPage(); loadVfPage() }

async function loadVfPage() {
  vfError.value = ''
  await vfPager.load(async (p) => {
    try {
      const r = await sharingApi.voucherGenFailurePage({
        pageNum: p.pageNum,
        pageSize: p.pageSize,
        status: vfQ.status || undefined,
        sourceType: vfQ.sourceType || undefined,
        billNo: vfQ.billNo || undefined,
      })
      if (!(r?.list || []).length && !vfQ.status && !vfQ.sourceType && !vfQ.billNo) {
        vfError.value = t('sharing.ops.hintNoEvents')
      }
      return { list: r?.list || [], total: r?.total || 0 }
    } catch (e) {
      vfError.value = t('sharing.ops.errVoucherRead') + (e?.message || t('sharing.ops.fallbackUnknown'))
      return { list: [], total: 0 }
    }
  })
}

async function loadVfStats() {
  try {
    vfStats.value = await sharingApi.voucherGenFailureStats({ sourceType: vfQ.sourceType || undefined })
  } catch {
    // 计数与列表同一权限，失败原因已在列表侧的红色提示中体现，不重复弹提示
    vfStats.value = null
  }
}

function openVfDetail(row) {
  vfDetail.value = row
  vfDetailVisible.value = true
}

/**
 * 补生成＝调用既有记账凭证生成接口（同源已有凭证时后端幂等返回，不会重复入账）。
 * 仅对后端判定 retriable 的行开放：借款/还款的来源走该入口会落错来源，见 D-DEC-271。
 */
async function doVfRetry(row) {
  if (!row || !row.retriable) return
  try {
    await ElMessageBox.confirm(
      t('sharing.ops.confirmRegen', { v1: row.billNo || row.billId, v2: row.sourceTypeName }),
      t('sharing.ops.titleRegenVoucher'), { type: 'warning' })
  } catch { return }
  vfRetrying.value = true
  try {
    await sharingApi.voucherGenerate(row.billId)
    ElMessage.success(t('sharing.ops.colRegenerated'))
    vfDetailVisible.value = false
    loadVfPage()
    loadVfStats()
  } catch (e) { ElMessage.error(e.message) } finally { vfRetrying.value = false }
}
</script>

<style scoped>
.header-title { font-size: 15px; font-weight: 600; color: #303133; margin-right: 10px; }
.header-note { font-size: 12px; color: #909399; }
.sub-card :deep(.el-card__header) { padding: 10px 14px; }
</style>