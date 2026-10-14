<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="ops-console">
    <el-card shadow="never">
      <template #header>
        <span class="header-title">共享作业控制台</span>
        <span class="header-note">分配策略四模式 · 分配规则试算 · 自动分配日志 · 普通/加急时限 · 制证失败执行结果</span>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- ---------------- 1. 分配策略配置 ---------------- -->
        <el-tab-pane label="分配策略" name="policy">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item label="关键字">
              <el-input v-model="policyQ.keyword" placeholder="策略名称/流程/节点" clearable style="width: 200px" @keyup.enter="policyReset" />
            </el-form-item>
            <el-form-item label="策略">
              <el-select v-model="policyQ.strategy" clearable placeholder="全部" style="width: 140px" @change="policyReset">
                <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="policyReset">查询</el-button>
              <el-button @click="openEffective">生效策略试算</el-button>
              <el-button v-permission="'wf:config:update'" type="success" @click="openPolicy(null)">新增策略</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="policyRows" v-loading="policyLoading" size="small" stripe border max-height="440" empty-text="暂无策略配置">
            <el-table-column prop="modelKey" label="流程" width="140" show-overflow-tooltip />
            <el-table-column prop="nodeKey" label="节点" width="130" show-overflow-tooltip />
            <el-table-column prop="policyName" label="策略名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="分配模式" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="strategyTag(row.strategy)" effect="plain">{{ strategyLabel(row.strategy) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="normalHours" label="普通时限(h)" width="105" align="right" />
            <el-table-column prop="urgentHours" label="加急时限(h)" width="105" align="right" />
            <el-table-column prop="balanceIntervalHours" label="均衡间隔(h)" width="105" align="right" />
            <el-table-column prop="balanceBatchSize" label="均衡批大小" width="100" align="right" />
            <el-table-column label="启用" width="72" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'wf:config:update'" link type="primary" size="small" @click="openPolicy(row)">编辑</el-button>
                <el-button v-permission="'wf:config:update'" link type="danger" size="small" @click="delPolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="policyPager" @change="loadPolicy" />
        </el-tab-pane>

        <!-- ---------------- 2. 分配规则顺序解析试算 ---------------- -->
        <el-tab-pane label="规则试算" name="rule">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 10px"
            title="按派单规则的优先级从先到后逐条评估、命中即止；未命中的规则同样记入轨迹并给出原因。"
          />
          <el-form :inline="true" size="small">
            <el-form-item label="作业项">
              <el-select v-model="ruleForm.taskType" clearable placeholder="不限" style="width: 130px">
                <el-option label="审核" value="AUDIT" />
                <el-option label="记账" value="BOOKKEEP" />
                <el-option label="付款" value="PAY" />
                <el-option label="凭证" value="VOUCHER" />
                <el-option label="归档" value="ARCHIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="单据类型"><el-input v-model="ruleForm.bizType" placeholder="如 EXP_CLAIM" style="width: 150px" /></el-form-item>
            <el-form-item label="金额"><el-input-number v-model="ruleForm.amount" :min="0" :precision="2" :controls="false" style="width: 130px" /></el-form-item>
            <el-form-item label="公司ID"><el-input-number v-model="ruleForm.companyId" :min="0" :controls="false" style="width: 120px" /></el-form-item>
            <el-form-item label="费用项目ID"><el-input-number v-model="ruleForm.expenseItemId" :min="0" :controls="false" style="width: 120px" /></el-form-item>
            <el-form-item label="币种"><el-input v-model="ruleForm.currencyCode" placeholder="CNY" style="width: 90px" /></el-form-item>
            <el-form-item label="法人主体ID"><el-input-number v-model="ruleForm.legalEntityId" :min="0" :controls="false" style="width: 120px" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="ruleLoading" @click="doRuleResolve">试算</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="ruleResult" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item label="命中规则">{{ ruleResult.matchedRuleName || '未命中任何规则' }}</el-descriptions-item>
            <el-descriptions-item label="规则编码">{{ ruleResult.matchedRuleCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="评估条数">{{ ruleResult.evaluatedCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ ruleResult.note || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="ruleResult" :data="ruleResult.trace || []" size="small" stripe border max-height="380" empty-text="无评估轨迹">
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column prop="ruleCode" label="规则编码" width="150" show-overflow-tooltip />
            <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="dimension" label="维度" width="130" />
            <el-table-column prop="condition" label="条件" min-width="150" show-overflow-tooltip />
            <el-table-column label="命中" width="72" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.matched ? 'success' : 'info'">{{ row.matched ? '命中' : '未中' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="判定说明" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- ---------------- 3. 自动分配 + 调度日志 ---------------- -->
        <el-tab-pane label="自动分配·日志" name="dispatch">
          <el-card shadow="never" class="sub-card">
            <template #header><b>自动分配</b>（试算不保存；执行需任务改派权限）</template>
            <el-form :inline="true" size="small">
              <el-form-item label="任务行ID"><el-input-number v-model="assignForm.taskRowId" :min="0" :controls="false" style="width: 140px" /></el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="assignForm.priority" style="width: 110px">
                  <el-option label="普通" value="NORMAL" />
                  <el-option label="加急" value="URGENT" />
                </el-select>
              </el-form-item>
              <el-form-item label="试算">
                <el-switch v-model="assignForm.dryRun" />
              </el-form-item>
              <el-form-item>
                <el-button v-permission="'wf:task:reassign'" type="primary" :loading="assignLoading" @click="doAssign">执行</el-button>
              </el-form-item>
            </el-form>
            <el-descriptions v-if="assignResult" :column="4" border size="small">
              <el-descriptions-item label="策略">{{ strategyLabel(assignResult.strategy) }}</el-descriptions-item>
              <el-descriptions-item label="命中规则">{{ assignResult.matchedRuleCode || '—' }}</el-descriptions-item>
              <el-descriptions-item label="试算">{{ assignResult.dryRun ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="已保存">{{ assignResult.applied ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="选中办理人">{{ assignResult.decision?.chosenUserName || '未指派（进池待领取）' }}</el-descriptions-item>
              <el-descriptions-item label="决策说明" :span="3">{{ assignResult.decision?.reason || '—' }}</el-descriptions-item>
              <el-descriptions-item label="候选快照" :span="4">{{ assignResult.decision?.candidateSnapshot || '—' }}</el-descriptions-item>
              <el-descriptions-item label="时限(h)">{{ assignResult.limitHours ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="到期时间" :span="3">{{ formatDateTime(assignResult.dueTime) || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="never" class="sub-card" style="margin-top: 12px">
            <template #header><b>自动分配日志</b>（谁在什么时候被谁派了哪条单、为什么）</template>
            <el-form :inline="true" size="small" style="margin-bottom: 8px">
              <el-form-item label="任务ID"><el-input v-model="logQ.taskId" clearable style="width: 150px" @keyup.enter="logReset" /></el-form-item>
              <el-form-item label="单号"><el-input v-model="logQ.bizNo" clearable style="width: 150px" @keyup.enter="logReset" /></el-form-item>
              <el-form-item label="策略">
                <el-select v-model="logQ.strategy" clearable placeholder="全部" style="width: 130px" @change="logReset">
                  <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
              </el-form-item>
              <el-form-item><el-button type="primary" @click="logReset">查询</el-button></el-form-item>
            </el-form>
            <el-table :data="logRows" v-loading="logLoading" size="small" stripe border max-height="380" empty-text="暂无分配日志">
              <el-table-column prop="bizNo" label="单号" width="140" show-overflow-tooltip />
              <el-table-column prop="nodeName" label="节点" width="130" show-overflow-tooltip />
              <el-table-column prop="dispatchMode" label="方式" width="100" :formatter="(row, col, val) => dispatchModeLabel(val)" />
              <el-table-column prop="strategy" label="策略" width="110">
                <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
              </el-table-column>
              <el-table-column prop="matchedRuleCode" label="命中规则" width="130" show-overflow-tooltip />
              <el-table-column prop="chosenUserName" label="选中办理人" width="110" />
              <el-table-column prop="decisionReason" label="决策说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="成功" width="72" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" :disabled="!row.taskId" @click="openChain(row)">决策链</el-button>
                </template>
              </el-table-column>
            </el-table>
            <TablePager :pager="logPager" @change="loadLog" />
          </el-card>
        </el-tab-pane>

        <!-- ---------------- 4. 双时限 ---------------- -->
        <el-tab-pane label="办理时限" name="timelimit">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            title="超时判定以任务的「应完成时间」为准；尚无应完成时间的历史存量单独标注，可一键补算（重复补算结果不变）。" />
          <el-form :inline="true" size="small">
            <el-form-item label="扫描条数上限"><el-input-number v-model="limitQ.limit" :min="1" :max="1000" style="width: 130px" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="dueLoading" @click="loadDueScan">超时扫描</el-button>
              <el-button v-permission="'wf:config:update'" :loading="backfillLoading" @click="doBackfill">补算应完成时间</el-button>
              <el-button @click="loadScanReport">定时任务最近报告</el-button>
              <el-button v-permission="'wf:config:update'" @click="markVisible = true">标记优先级</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="dueScan" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item label="扫描时间">{{ formatDateTime(dueScan.scannedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="在办任务">{{ dueScan.pendingTotal ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="已超时">{{ dueScan.overdueTotal ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="未落时限">{{ dueScan.noDueTimeCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="dueScan" :data="dueScan.overdue || []" size="small" stripe border max-height="320" empty-text="无超时任务">
            <el-table-column prop="bizNo" label="单号" width="140" show-overflow-tooltip />
            <el-table-column prop="nodeName" label="节点" width="140" show-overflow-tooltip />
            <el-table-column prop="taskId" label="任务ID" width="150" show-overflow-tooltip />
            <el-table-column prop="assigneeId" label="受理人ID" width="110" />
            <el-table-column label="优先级" width="88" align="center">
              <template #default="{ row }">{{ row.priority === 'URGENT' ? '加急' : '普通' }}</template>
            </el-table-column>
            <el-table-column label="到期时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" prop="dueTime" />
            <el-table-column prop="overdueHours" label="已超时(h)" width="100" align="right" />
          </el-table>

          <el-descriptions v-if="scanReport" :column="3" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="扫描时间">{{ formatDateTime(scanReport.scannedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="超时租户数">{{ scanReport.overdueTenantCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="超时总数">{{ scanReport.overdueTotal ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="说明" :span="3">{{ scanReport.note || '—' }}</el-descriptions-item>
          </el-descriptions>

          <el-dialog v-model="markVisible" title="标记优先级与时限" width="420px">
            <el-form label-width="100px" size="small">
              <el-form-item label="任务行ID"><el-input-number v-model="markForm.taskRowId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="markForm.priority" style="width: 100%">
                  <el-option label="普通" value="NORMAL" />
                  <el-option label="加急" value="URGENT" />
                </el-select>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="markVisible = false">取消</el-button>
              <el-button type="primary" :loading="markLoading" @click="doMark">确定</el-button>
            </template>
          </el-dialog>
        </el-tab-pane>

        <!-- ---------------- 5. 制证失败·执行结果视图（A12） ---------------- -->
        <el-tab-pane label="制证失败" name="vouchfail">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            title="审批通过后系统自动生成凭证失败的事件在此可见；「未补生成」＝总账仍缺这张凭证。范围＝报销/对公付款/借款/还款四类自动制证。" />
          <el-alert v-if="vfError" type="error" :closable="false" show-icon style="margin-bottom: 10px" :title="vfError" />
          <el-descriptions v-if="vfStats" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item label="失败事件">{{ vfStats.allEvents ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="未补生成">{{ vfStats.allPending ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="统计范围">最近 {{ vfStats.scannedRows ?? 0 }} 条事件</el-descriptions-item>
            <el-descriptions-item label="是否已截断">{{ vfStats.truncated ? '是（仅统计最近 ' + vfStats.scanLimit + ' 条）' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="按来源计数" :span="4">
              <el-tag v-for="s in vfStats.bySource || []" :key="s.sourceType" size="small" style="margin-right: 8px">
                {{ s.sourceTypeName }}：未补 {{ s.pending }} / 事件 {{ s.events }}
              </el-tag>
              <span v-if="!(vfStats.bySource || []).length">—</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form :inline="true" size="small" style="margin-bottom: 8px">
            <el-form-item label="状态">
              <el-select v-model="vfQ.status" clearable placeholder="不限" style="width: 130px" @change="vfReset">
                <el-option label="未补生成" value="PENDING" />
                <el-option label="已补生成" value="RESOLVED" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源">
              <el-select v-model="vfQ.sourceType" clearable placeholder="不限" style="width: 140px" @change="vfReset">
                <el-option v-for="s in VF_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="单号">
              <el-input v-model="vfQ.billNo" clearable placeholder="单据号含" style="width: 170px" @keyup.enter="vfReset" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="vfLoading" @click="vfReset">查询</el-button>
              <el-button @click="loadVfStats">刷新计数</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="vfRows" v-loading="vfLoading" size="small" stripe border max-height="380" empty-text="暂无制证失败事件">
            <el-table-column prop="operTime" label="发生时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column prop="sourceTypeName" label="来源" width="100" />
            <el-table-column prop="billNo" label="单据号" width="150" show-overflow-tooltip />
            <el-table-column prop="billTypeName" label="单据类型" width="120">
              <template #default="{ row }">{{ row.billTypeName || '—' }}</template>
            </el-table-column>
            <el-table-column label="单据状态" width="100">
              <template #default="{ row }">{{ billStatusLabel(row.billStatus) }}</template>
            </el-table-column>
            <el-table-column label="期间" width="90">
              <template #default="{ row }">{{ periodLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="结果" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 'RESOLVED' ? 'success' : 'danger'">{{ row.resultName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="失败原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="补出凭证" width="130">
              <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openVfDetail(row)">详情</el-button>
                <el-tooltip v-permission="'sharing:voucher:generate'" :content="row.retryHint || ''" placement="top" :disabled="row.retriable">
                  <el-button link type="warning" size="small"
                    :disabled="!row.retriable" :loading="vfRetrying" @click="doVfRetry(row)">补生成</el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="vfPager" @change="loadVfPage" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 策略编辑 -->
    <el-dialog v-model="policyVisible" :title="policyForm.id ? '编辑分配策略' : '新增分配策略'" width="620px" destroy-on-close>
      <el-form ref="policyFormRef" :model="policyForm" :rules="policyRules" label-width="120px" size="small">
        <el-form-item label="流程 key" prop="modelKey"><el-input v-model="policyForm.modelKey" placeholder="* 表示全局默认" /></el-form-item>
        <el-form-item label="节点 key" prop="nodeKey"><el-input v-model="policyForm.nodeKey" placeholder="* 表示全局默认" /></el-form-item>
        <el-form-item label="策略名称" prop="policyName"><el-input v-model="policyForm.policyName" /></el-form-item>
        <el-form-item label="分配模式" prop="strategy">
          <el-select v-model="policyForm.strategy" style="width: 100%">
            <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="普通时限(h)"><el-input-number v-model="policyForm.normalHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="加急时限(h)"><el-input-number v-model="policyForm.urgentHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="均衡间隔(h)"><el-input-number v-model="policyForm.balanceIntervalHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="均衡批大小"><el-input-number v-model="policyForm.balanceBatchSize" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="权重·在手量"><el-input-number v-model="policyForm.smartWeightVolume" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="权重·紧迫度"><el-input-number v-model="policyForm.smartWeightUrgency" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="权重·优先级"><el-input-number v-model="policyForm.smartWeightPriority" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="policyForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="policyForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="savePolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 生效策略试算 -->
    <el-dialog v-model="effectiveVisible" title="某节点当前生效策略（只读）" width="520px">
      <el-form :inline="true" size="small">
        <el-form-item label="流程 key"><el-input v-model="effectiveQ.modelKey" placeholder="可留空" style="width: 150px" /></el-form-item>
        <el-form-item label="节点 key"><el-input v-model="effectiveQ.nodeKey" placeholder="可留空" style="width: 150px" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="effectiveLoading" @click="doEffective">查询</el-button></el-form-item>
      </el-form>
      <el-descriptions v-if="effectiveResult" :column="2" border size="small">
        <el-descriptions-item label="策略名称">{{ effectiveResult.policyName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="分配模式">{{ strategyLabel(effectiveResult.strategy) }}</el-descriptions-item>
        <el-descriptions-item label="普通时限(h)">{{ effectiveResult.normalHours ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="加急时限(h)">{{ effectiveResult.urgentHours ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="流程">{{ effectiveResult.modelKey || '—' }}</el-descriptions-item>
        <el-descriptions-item label="节点">{{ effectiveResult.nodeKey || '—' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">节点级 → 全局默认行 → 系统默认（抢单 24 小时/4 小时）</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="点击「查询」查看生效策略" :image-size="60" />
    </el-dialog>

    <!-- 决策链 -->
    <el-dialog v-model="chainVisible" title="分配决策链" width="640px">
      <el-table :data="chainRows" v-loading="chainLoading" size="small" stripe border max-height="400" empty-text="暂无决策记录">
        <el-table-column prop="dispatchMode" label="方式" width="90" :formatter="(row, col, val) => dispatchModeLabel(val)" />
        <el-table-column prop="strategy" label="策略" width="100">
          <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
        </el-table-column>
        <el-table-column prop="chosenUserName" label="选中办理人" width="110" />
        <el-table-column prop="decisionReason" label="决策说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
      </el-table>
    </el-dialog>
    <!-- 制证失败·行明细钻取 -->
    <el-dialog v-model="vfDetailVisible" title="制证失败事件明细" width="640px">
      <el-descriptions v-if="vfDetail" :column="2" border size="small">
        <el-descriptions-item label="单据号">{{ vfDetail.billNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="单据ID">{{ vfDetail.billId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ vfDetail.billTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="单据状态">{{ billStatusLabel(vfDetail.billStatus) }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ vfDetail.sourceTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="公司/期间">{{ vfDetail.companyId ?? '—' }} / {{ periodLabel(vfDetail) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ formatDateTime(vfDetail.operTime) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ vfDetail.resultName }}</el-descriptions-item>
        <el-descriptions-item label="补出凭证" :span="2">{{ vfDetail.voucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">
          <div style="white-space: pre-wrap; line-height: 1.6">{{ vfDetail.reason || '—' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="补生成" :span="2">
          <el-tag size="small" :type="vfDetail.retriable ? 'success' : 'info'">{{ vfDetail.retriable ? '可补生成' : '暂不可补' }}</el-tag>
          <span style="margin-left: 8px">{{ vfDetail.retryHint || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="vfDetail.inconsistency" label="需注意" :span="2">
          <span style="color: var(--el-color-danger)">{{ vfDetail.inconsistency }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="事件行" :span="2">{{ vfDetail.method || '—' }} #{{ vfDetail.logId }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="vfDetailVisible = false">关闭</el-button>
        <el-button v-permission="'sharing:voucher:generate'" type="warning" :disabled="!vfDetail?.retriable"
          :loading="vfRetrying" @click="doVfRetry(vfDetail)">补生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { dispatchModeLabel } from '@/constants/enums'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wfOpsApi, sharingApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime } from '@/utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'

/**
 * 共享作业控制台（G3）：全部数据源为 WfSharedOpsController（/api/tenant/wf/ops/**）。
 * 权限：读 wf:config:list（策略）/ wf:instance:list（日志、时限、质量）；写 wf:config:update / wf:task:reassign。
 */
const STRATEGIES = [
  { value: 'PICK', label: '抢单' },
  { value: 'BALANCE_TIME', label: '均衡·按时' },
  { value: 'BALANCE_VOLUME', label: '均衡·按量' },
  { value: 'SMART', label: '智能' },
]
const STRATEGY_MAP = Object.fromEntries(STRATEGIES.map((s) => [s.value, s.label]))
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
const policyRules = reactive({
  modelKey: commonRules.required('请输入流程 key（* 为全局默认）'),
  nodeKey: commonRules.required('请输入节点 key（* 为全局默认）'),
  policyName: commonRules.required('请输入策略名称'),
  strategy: commonRules.select('请选择分配模式'),
})

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
    ElMessage.success('保存成功')
    policyVisible.value = false
    loadPolicy()
  } catch (e) { ElMessage.error(e.message) } finally { policySaving.value = false }
}

async function delPolicy(row) {
  try {
    await ElMessageBox.confirm('删除后该节点回落到全局默认行，确认删除？', '删除策略', { type: 'warning' })
    await wfOpsApi.policyDelete(row.id)
    ElMessage.success('已删除')
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
  if (!assignForm.taskRowId) { ElMessage.warning('请输入任务行ID'); return }
  assignLoading.value = true
  try {
    assignResult.value = await wfOpsApi.assign({ ...assignForm })
    ElMessage.success(assignForm.dryRun ? '试算完成' : '执行完成')
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

async function loadDueScan() {
  dueLoading.value = true
  try { dueScan.value = await wfOpsApi.timeLimitDueScan(limitQ.limit) } catch (e) { ElMessage.error(e.message) } finally { dueLoading.value = false }
}
async function doBackfill() {
  backfillLoading.value = true
  try {
    const r = await wfOpsApi.timeLimitBackfill(limitQ.limit)
    ElMessage.success(`已补算 ${r?.backfilled ?? 0} 条`)
    loadDueScan()
  } catch (e) { ElMessage.error(e.message) } finally { backfillLoading.value = false }
}
async function loadScanReport() {
  try { scanReport.value = await wfOpsApi.timeLimitScanJobReport() } catch (e) { ElMessage.error(e.message) }
}
async function doMark() {
  if (!markForm.taskRowId) { ElMessage.warning('请输入任务行ID'); return }
  markLoading.value = true
  try {
    await wfOpsApi.timeLimitMark(markForm.taskRowId, markForm.priority)
    ElMessage.success('已标记')
    markVisible.value = false
  } catch (e) { ElMessage.error(e.message) } finally { markLoading.value = false }
}

onMounted(() => {
  loadedTabs.add('policy')
  loadPolicy()
})

// ---------------- 5. 制证失败·执行结果视图（A12） ----------------
const VF_SOURCES = [
  { value: 'EXPENSE', label: '费用报销' },
  { value: 'EXPENSE_PAY', label: '对公付款' },
  { value: 'EXP_LOAN', label: '借款' },
  { value: 'EXP_REPAY', label: '还款' },
]
// 视图行里的单据状态是英文码（不臆造映射：未收录的原样显示，便于发现漏配）
const VF_BILL_STATUS = {
  APPROVING: '审批中', APPROVED: '已审批', PAID: '已付款', CLOSED: '已关闭',
  REPAID: '已还款', REJECTED: '已驳回', WITHDRAWN: '已撤回', DRAFT: '草稿',
}
const vfQ = reactive({ status: '', sourceType: '', billNo: '' })
const vfPager = useTablePager({ mode: 'server', storageKey: 'sharing_ops_vouchfail' })
const { rows: vfRows, loading: vfLoading } = vfPager
const vfStats = ref(null)
const vfError = ref('')
const vfDetailVisible = ref(false)
const vfDetail = ref(null)
const vfRetrying = ref(false)

function billStatusLabel(s) { return VF_BILL_STATUS[s] || s || '—' }
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
        vfError.value = '本次未取到失败事件：可能是确无失败单据，也可能是当前账号缺少读取权限被接口拒绝。'
      }
      return { list: r?.list || [], total: r?.total || 0 }
    } catch (e) {
      vfError.value = '读取制证失败事件失败：' + (e?.message || '未知错误')
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
      `将对单据 ${row.billNo || row.billId} 补生成「${row.sourceTypeName}」凭证，确认执行？`,
      '补生成凭证', { type: 'warning' })
  } catch { return }
  vfRetrying.value = true
  try {
    await sharingApi.voucherGenerate(row.billId)
    ElMessage.success('已补生成')
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