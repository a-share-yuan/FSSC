<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>资金管理</b>
        <span style="color:#909399; font-size:12px">资金计划 / 收款(渠道流水) / 资金调拨 / 票据结算 —— 编制、选中、核销、执行全流程可视化</span>
        <el-button size="small" style="margin-left:auto" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- 资金计划 -->
      <el-tab-pane label="资金计划" name="plan">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="success" @click="openPlan">编制计划</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table class="cm-target-treasury" :data="pagedPlans" size="small" border stripe v-else @selection-change="handleSelectionChange" :empty-text="planDenied ? '无权限查看：当前账号缺少资金计划读权限，请联系管理员开通' : '暂无数据'" @row-dblclick="openTrace">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="planNo" label="计划编号" width="150"  sortable />
          <el-table-column prop="period" label="期间" width="110"  sortable />
          <el-table-column prop="planDate" label="计划日期" width="110"  sortable />
          <el-table-column label="计划收入" width="120" align="right"><template #default="{ row }">{{ fmt(row.incomeAmount) }}</template></el-table-column>
          <el-table-column label="计划支出" width="120" align="right"><template #default="{ row }">{{ fmt(row.outcomeAmount) }}</template></el-table-column>
          <el-table-column label="实际收入" width="120" align="right"><template #default="{ row }">{{ fmt(row.incomeActual) }}</template></el-table-column>
          <el-table-column label="实际支出" width="120" align="right"><template #default="{ row }">{{ fmt(row.outcomeActual) }}</template></el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ stLabel(planStMap, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status === BillStatus.DRAFT" link size="small" type="primary" @click="selectPlan(row)">选中</el-button>
              <el-button v-if="row.status === 'REJECTED'" link size="small" type="warning" @click="resubmitPlan(row)">编辑重提</el-button>
              <el-button v-if="row.status !== 'COMPLETED' && row.status !== 'REJECTED'" link size="small" type="warning" @click="openCombine(row)">回填实际</el-button>
              <el-button v-if="row.status !== 'REJECTED'" link size="small" type="danger" @click="openAdjust(row)">调整</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 收款（渠道流水） -->
      <el-tab-pane label="资金收款" name="credit">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="success" @click="openCreditBill">渠道流水认领</el-button>
          <el-button type="warning" @click="autoRecon">收款流水自动核销</el-button>
      <el-button size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedCredits" size="small" border stripe v-else @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="单据号" width="150"  sortable />
          <el-table-column prop="receiveDate" label="收款日期" width="110"  sortable />
          <el-table-column prop="payerBank" label="付款方银行" min-width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="payerAccount" label="付款方账号" width="150" show-overflow-tooltip  sortable />
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column prop="sourceChannel" label="渠道" width="100"  sortable />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ stLabel(credStMap, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status !== 'RECONCILED'" link size="small" type="primary" @click="confirmRecon(row)">确认核销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 资金调拨 -->
      <el-tab-pane label="资金调拨" name="transfer">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center; flex-wrap:wrap">
          <el-button type="success" @click="openTransfer('transfer')">发起调拨</el-button>
          <el-button type="warning" @click="openTransfer('upload')">资金上存</el-button>
          <!-- 审批放行是授权、动账是另一件事，界面不写破就会被读成"批了钱就到账了" -->
          <span class="tf-leg-note">审批通过只完成授权，款项在两个账户间划转仍需出纳点「执行」</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedTransfers" size="small" border stripe v-else @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="transferNo" label="调拨号" width="150"  sortable />
          <el-table-column prop="transferDate" label="调拨日期" width="110"  sortable />
          <!-- 账户名可重名（108/109 同为「上海爱共享集团有限公司 银行账户」）⇒ 只显名称时
               付款/收款两列完全一样，H4.5 要求执行前核对方向就无从核对；第二行补银行账号（与下拉同格式）。 -->
          <el-table-column label="付款账户" width="175">
            <template #default="{ row }">
              <div>{{ bankAccName(row.fromBankAccountId) }}</div>
              <div class="tf-journal">{{ bankAccNo(row.fromBankAccountId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="收款账户" width="175">
            <template #default="{ row }">
              <div>{{ bankAccName(row.toBankAccountId) }}</div>
              <div class="tf-journal">{{ bankAccNo(row.toBankAccountId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="140" show-overflow-tooltip  sortable />
          <el-table-column label="状态 / 执行标注" width="240" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ stLabel(tfStMap, row.status) }}</el-tag>
              <div class="tf-exec-state" :class="row.status === BillStatus.APPROVED ? 'tf-exec-wait' : ''"
                   :title="row.nextActionText || ''">{{ execStateText(row) }}</div>
              <div v-if="row.status === BillStatus.APPROVED || row.status === BillStatus.PAID" class="tf-journal">
                {{ journalText(row) }}
              </div>
            </template>
          </el-table-column>
          <!-- 执行记录外显——执行前（DRAFT/APPROVED）显示「—」，执行后（PAID）显示执行人与执行时间。
               executorName 由后端按 update_by 解析真名回填（与「真名审核人」同族）；updateTime 即 execute() 落账时刻。 -->
          <el-table-column label="执行人" width="100" align="center">
            <template #default="{ row }">{{ row.status === BillStatus.PAID ? (row.executorName || '—') : '—' }}</template>
          </el-table-column>
          <el-table-column label="执行时间" width="150" align="center">
            <template #default="{ row }">{{ row.status === BillStatus.PAID ? (formatDateTime(row.updateTime) || '—') : '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status === BillStatus.DRAFT" link size="small" type="primary" @click="submitTransfer(row)">提交审批</el-button>
              <!-- 核准是直批旁路（后端要 treasury:transfer:update，三个资金角色都持有）。
                   连创建人自己也看得到【审批】不符合职责分离：非本人创建才渲染并补权限码门控，后端仍照旧校验。 -->
              <el-button v-if="canApproveTransfer(row)" v-permission="'treasury:transfer:update'" link size="small" type="primary" @click="approveTransfer(row)">审批</el-button>
              <el-button v-if="row.status === BillStatus.APPROVED" link size="small" type="warning" @click="executeTransfer(row)">执行</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 票据结算 -->
      <el-tab-pane label="票据结算" name="settle">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="success" @click="openSettle">登记票据</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedSettles" size="small" border stripe v-else @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="ticketNo" label="票据号" width="160"  sortable />
          <el-table-column prop="ticketType" label="票面种类" width="90"  sortable />
          <el-table-column prop="issuerBank" label="出票行" min-width="140" show-overflow-tooltip  sortable />
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column prop="issueDate" label="出票日" width="110"  sortable />
          <el-table-column prop="dueDate" label="到期日" width="110"  sortable />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ stLabel(settleStMap, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status === 'STOCK'" link size="small" type="primary" @click="endorse(row)">背书</el-button>
              <el-button v-if="row.status === 'STOCK'" link size="small" type="warning" @click="discount(row)">贴现</el-button>
              <el-button v-if="row.status === 'STOCK'" link size="small" type="success" @click="collect(row)">托收</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 银行主数据 -->
      <el-tab-pane label="银行主数据" name="bank">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <!-- 账户量大而默认每页条数小，复核某个账户余额只能一页页翻。
               补关键字检索（账号/户名/开户行，与「银行账户管理」页同一后端 keyword 规则）。 -->
          <el-input v-model="bankKeyword" size="small" style="width:220px" placeholder="账号/户名/开户行" clearable
                    @keyup.enter="searchBank" @clear="searchBank" />
          <el-button size="small" type="primary" @click="searchBank">查询</el-button>
          <el-button type="success" @click="openAccount">新增账户</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedAccounts" size="small" border stripe v-else @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="accountCode" label="账户编码" width="150"  sortable />
          <el-table-column prop="accountName" label="账户名称" min-width="160" show-overflow-tooltip  sortable />
          <el-table-column prop="bankName" label="开户银行" min-width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="bankAccount" label="账号" width="180" show-overflow-tooltip  sortable />
          <!-- 余额列补排序（复核调拨前后余额时按金额定位账户） -->
          <el-table-column prop="balance" label="余额" width="120" align="right" sortable><template #default="{ row }">{{ fmt(row.balance) }}</template></el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ stLabel(accStMap, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button link size="small" type="warning" @click="toggleAccount(row)">
                {{ row.status === 'NORMAL' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 银行对账 -->
      <el-tab-pane label="银行对账" name="bankRecon">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="warning" @click="runBankRecon">银行流水勾对</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedRecons" size="small" border stripe v-else @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="serialNo" label="银行流水号" min-width="180" show-overflow-tooltip  sortable />
          <!-- 本列同列混过两种值（旧数据是 exp_bill_payment 主键数字，新写为 exp_bill 单号），列名按语义纠正为付款单号 -->
          <el-table-column prop="paymentNo" label="付款单号" width="110"  sortable />
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column prop="accountNo" label="往来账号" width="170" show-overflow-tooltip  sortable />
          <el-table-column prop="matchTime" label="勾对时间" min-width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
          <el-table-column label="勾对状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="tagType(row.matchStatus)" size="small">{{ stLabel(reconStMap, row.matchStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 资金头寸（司库日幕 —— 账户余额 + 待付流出 + 应收未收 + 当期计划 → 净头寸） -->
      <el-tab-pane label="头寸" name="position">
        <div style="display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px">
          <div class="pos-card" :style="{ borderColor: netPosition >= 0 ? '#67c23a' : '#f56c6c' }">
            <div class="pos-label">净头寸（余额+应收−待付）</div>
            <div class="pos-value" :style="{ color: netPosition >= 0 ? '#67c23a' : '#f56c6c' }">{{ fmt(position?.netPosition) }}</div>
          </div>
          <div class="pos-card">
            <div class="pos-label">账户总余额</div>
            <div class="pos-value">{{ fmt(position?.totalBalance) }}</div>
          </div>
          <div class="pos-card">
            <div class="pos-label">待付款流出</div>
            <div class="pos-value" style="color:#e6a23c">{{ fmt(position?.payableWaiting) }} <span class="pos-sub">共 {{ position?.payableWaitingCount || 0 }} 笔</span></div>
          </div>
          <div class="pos-card">
            <div class="pos-label">应收未收</div>
            <div class="pos-value" style="color:#409eff">{{ fmt(position?.receivableOpen) }} <span class="pos-sub">共 {{ position?.receivableOpenCount || 0 }} 笔</span></div>
          </div>
          <div class="pos-card">
            <div class="pos-label">当期计划（{{ position?.plan?.period || '—' }}）</div>
            <div class="pos-value" style="font-size:16px">
              收 {{ fmt(position?.plan?.planIncome) }} / 支 {{ fmt(position?.plan?.planOutcome) }}
              <div class="pos-sub">实际 收 {{ fmt(position?.plan?.actualIncome) }} / 支 {{ fmt(position?.plan?.actualOutcome) }}</div>
            </div>
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:8px; margin-bottom:6px">
          <span style="color:#909399; font-size:12px">各账户最近余额（bank_balance 按余额表日取最近一次；未登记行余额取账户当前值，请用「登记余额」补录快照）</span>
          <el-button size="small" type="primary" plain style="margin-left:auto" @click="openBalance">登记余额</el-button>
        </div>
        <el-table :data="positionAccounts" size="small" border stripe :empty-text="positionDenied ? '无权限查看：当前账号缺少资金头寸读权限，请联系管理员开通' : '暂无数据，可点右上角「登记余额」补录账户余额快照'">
          <el-table-column prop="accountName" label="账户名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="bankName" label="开户行" min-width="160" show-overflow-tooltip />
          <el-table-column prop="bankAccount" label="银行账号" min-width="190" show-overflow-tooltip />
          <el-table-column label="最近余额" width="150" align="right"><template #default="{ row }">{{ fmt(row.balance) }}</template></el-table-column>
          <el-table-column prop="balanceDate" label="余额表日" width="170" align="center">
            <template #default="{ row }">
              <!-- 余额表未登记时显式标注：回落「—」会与"当日无数"同形；服务端此情形取账户主数据余额 -->
              <el-tag v-if="!row.balanceDate" size="small" type="warning">未登记（取账户余额）</el-tag>
              <span v-else>{{ row.balanceDate }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <!-- 当前 tab 的服务端分页器 -->
    <TablePager v-if="activePager" :pager="activePager" @change="reloadTab" />

    <!-- 余额快照登记弹窗：头寸页没有登记入口时余额表恒空，头寸余额一直是 0 -->
    <el-dialog v-model="dlgBalance" title="登记账户余额（余额快照）" width="500px">
      <el-form ref="balanceFormRef" :model="balanceForm" :rules="balanceFormRules" label-width="110px" size="small">
        <el-form-item label="账户" prop="accountId">
          <el-select v-model="balanceForm.accountId" filterable placeholder="选择账户" style="width:100%">
            <el-option v-for="a in positionAccounts" :key="a.accountId" :label="a.accountName + ' (' + (a.bankAccount || '') + ')'" :value="a.accountId" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额日期" prop="balanceDate"><el-date-picker v-model="balanceForm.balanceDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></el-form-item>
        <el-form-item label="余额" prop="balance"><el-input-number v-model="balanceForm.balance" :precision="2" :step="1000" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="balanceForm.remark" placeholder="选填，如对账依据/来源" /></el-form-item>
        <div style="color:#909399; font-size:12px; padding-left:2px">同一账户同一日期重复登记会覆盖已有登记；保存后头寸页余额与余额表立即刷新</div>
      </el-form>
      <template #footer>
        <el-button @click="dlgBalance = false">取消</el-button>
        <el-button type="primary" :loading="balanceSaving" @click="saveBalance">保存</el-button>
      </template>
    </el-dialog>

    <!-- 资金计划弹窗 -->
    <el-dialog v-model="dlgPlan" title="编制资金计划" width="520px">
      <el-form ref="planFormRef" :model="planForm" :rules="planFormRules" label-width="120px" size="small">
        <el-form-item label="期间" prop="period"><el-date-picker v-model="planForm.period" type="month" value-format="YYYYMM" placeholder="选择月份" style="width:100%" /></el-form-item>
        <el-form-item label="计划日期" prop="planDate"><el-date-picker v-model="planForm.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="所属公司" prop="companyId"><el-select v-model="planForm.companyId" filterable clearable placeholder="选择公司" style="width:100%"><el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.name" :value="c.id" /></el-select></el-form-item>
        <el-form-item label="计划收入"><el-input-number v-model="planForm.incomeAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="计划支出"><el-input-number v-model="planForm.outcomeAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgPlan = false">取消</el-button>
        <el-button type="primary" :loading="planSaving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 实际回填弹窗 -->
    <el-dialog v-model="dlgCombine" title="回填实际收支" width="480px">
      <el-form ref="combineFormRef" :model="combineForm" :rules="combineFormRules" label-width="120px" size="small">
        <el-form-item label="计划编号"><b>{{ combinePlan.planNo || '—' }}</b></el-form-item>
        <el-form-item label="计划收入"><span style="font-weight:600">{{ fmt(combinePlan.incomeAmount) }}</span></el-form-item>
        <el-form-item label="计划支出"><span style="font-weight:600">{{ fmt(combinePlan.outcomeAmount) }}</span></el-form-item>
        <el-form-item label="实际收入" prop="incomeActual"><el-input-number v-model="combineForm.incomeActual" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="实际支出" prop="outcomeActual"><el-input-number v-model="combineForm.outcomeActual" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgCombine = false">取消</el-button>
        <el-button type="primary" :loading="combineSaving" @click="saveCombine">保存</el-button>
      </template>
    </el-dialog>

    <!-- 资金计划调整弹窗 -->
    <el-dialog v-model="dlgAdjust" title="资金计划调整" width="520px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustFormRules" label-width="140px" size="small">
        <el-form-item label="计划编号"><b>{{ adjustPlanNo }}</b></el-form-item>
        <el-form-item label="期间"><span>{{ adjustPlan.period || '—' }}</span></el-form-item>
        <el-form-item label="原计划收入"><span style="font-weight:600">{{ fmt(adjustPlan.incomeAmount) }}</span></el-form-item>
        <el-form-item label="原计划支出"><span style="font-weight:600">{{ fmt(adjustPlan.outcomeAmount) }}</span></el-form-item>
        <el-form-item label="收入调整额" prop="incomeAdjust"><el-input-number v-model="adjustForm.incomeAdjust" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="支出调整额" prop="outcomeAdjust"><el-input-number v-model="adjustForm.outcomeAdjust" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="调整后计划收入"><span style="font-weight:600;color:#409eff">{{ fmt(adjustedIncome) }}</span></el-form-item>
        <el-form-item label="调整后计划支出"><span style="font-weight:600;color:#409eff">{{ fmt(adjustedOutcome) }}</span></el-form-item>
        <div style="color:#909399; font-size:12px; padding-left:2px">调整额在原计划收/支基础上追加，保存并发起 TR_PLAN_ADJUST 计划调整审批流</div>
      </el-form>
      <template #footer>
        <el-button @click="dlgAdjust = false">取消</el-button>
        <el-button type="primary" :loading="adjustSaving" @click="saveAdjust">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 渠道流水认领弹窗 -->
    <el-dialog v-model="dlgCredit" title="渠道流水认领（生成收款）" width="520px">
      <el-form ref="creditFormRef" :model="creditForm" :rules="creditFormRules" label-width="120px" size="small">
        <el-form-item label="收款日期"><el-date-picker v-model="creditForm.receiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="往来单位"><el-select v-model="creditForm.customerId" filterable clearable placeholder="选择客户/供应商" style="width:100%"><el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" /></el-select></el-form-item>
        <el-form-item label="支付方式"><el-select v-model="creditForm.payMethod" filterable allow-create default-first-option placeholder="请选择支付方式" style="width:100%"><el-option label="电汇" value="电汇" /><el-option label="支票" value="支票" /><el-option label="承兑汇票" value="承兑汇票" /><el-option label="现金" value="现金" /><el-option label="网银转账" value="网银转账" /></el-select></el-form-item>
        <el-form-item label="付款方银行"><el-input v-model="creditForm.payerBank" placeholder="请输入付款方银行" /></el-form-item>
        <el-form-item label="付款方账号"><el-input v-model="creditForm.payerAccount" placeholder="请输入付款方账号" maxlength="64" /></el-form-item>
        <el-form-item label="收款金额" prop="amount"><el-input-number v-model="creditForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="渠道"><el-select v-model="creditForm.sourceChannel" filterable allow-create default-first-option placeholder="请选择渠道" style="width:100%"><el-option label="银行直联" value="银行直联" /><el-option label="票据贴现" value="票据贴现" /><el-option label="第三方支付" value="第三方支付" /><el-option label="其他" value="其他" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgCredit = false">取消</el-button>
        <el-button type="primary" :loading="creditSaving" @click="saveCredit">认领</el-button>
      </template>
    </el-dialog>

    <!-- 资金调拨/上存弹窗 -->
    <el-dialog v-model="dlgTransfer" :title="tfMode === 'upload' ? '资金上存（上划集团）' : '发起资金调拨'" width="520px">
      <el-form ref="tfFormRef" :model="tfForm" :rules="transferFormRules" label-width="130px" size="small">
        <el-form-item label="调拨日期"><el-date-picker v-model="tfForm.transferDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="付款账户" prop="fromBankAccountId"><el-select v-model="tfForm.fromBankAccountId" filterable clearable placeholder="选择付款账户" style="width:100%"><el-option v-for="a in accountOptions" :key="a.id" :label="a.accountName + ' (' + (a.bankAccount || '') + ')'" :value="a.id" /></el-select></el-form-item>
        <el-form-item label="收款账户" prop="toBankAccountId"><el-select v-model="tfForm.toBankAccountId" filterable clearable placeholder="选择收款账户" style="width:100%"><el-option v-for="a in accountOptions" :key="a.id" :label="a.accountName + ' (' + (a.bankAccount || '') + ')'" :value="a.id" /></el-select></el-form-item>
        <el-form-item label="所属公司"><el-select v-model="tfForm.companyId" filterable clearable placeholder="选择公司" style="width:100%"><el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.name" :value="c.id" /></el-select></el-form-item>
        <el-form-item label="调拨金额" prop="amount"><el-input-number v-model="tfForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="用途"><el-select v-model="tfForm.purpose" filterable allow-create default-first-option placeholder="请选择用途" style="width:100%"><el-option label="日常周转" value="日常周转" /><el-option label="归还借款" value="归还借款" /><el-option label="投资理财" value="投资理财" /><el-option label="采购付款" value="采购付款" /><el-option label="其他" value="其他" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgTransfer = false">取消</el-button>
        <el-button type="primary" :loading="tfSaving" @click="saveTransfer">发起</el-button>
      </template>
    </el-dialog>

    <!-- 票据登记弹窗 -->
    <el-dialog v-model="dlgSettle" title="登记票据" width="560px">
      <el-form ref="settleFormRef" :model="stForm" :rules="settleFormRules" label-width="130px" size="small">
        <el-form-item label="票据号" prop="ticketNo"><el-input v-model="stForm.ticketNo" placeholder="请输入票据号" maxlength="64" /></el-form-item>
        <el-form-item label="票面种类">
          <el-select v-model="stForm.ticketType" style="width:100%"><el-option label="银承" value="银承" /><el-option label="商承" value="商承" /></el-select>
        </el-form-item>
        <el-form-item label="出票行"><el-select v-model="stForm.issuerBank" filterable allow-create default-first-option placeholder="请选择或输入出票行" style="width:100%"><el-option label="工商银行" value="工商银行" /><el-option label="建设银行" value="建设银行" /><el-option label="农业银行" value="农业银行" /><el-option label="中国银行" value="中国银行" /><el-option label="交通银行" value="交通银行" /><el-option label="招商银行" value="招商银行" /><el-option label="邮储银行" value="邮储银行" /><el-option label="其他" value="其他" /></el-select></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="stForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="出票日"><el-date-picker v-model="stForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="到期日"><el-date-picker v-model="stForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="受益人"><el-input v-model="stForm.beneficiary" placeholder="请输入受益人" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgSettle = false">取消</el-button>
        <el-button type="primary" :loading="stSaving" @click="saveSettle">登记</el-button>
      </template>
    </el-dialog>

    <!-- 银行主数据新增弹窗 -->
    <el-dialog v-model="dlgAccount" title="新增银行账户" width="520px">
      <el-form ref="accFormRef" :model="accForm" :rules="accountFormRules" label-width="130px" size="small">
        <el-form-item label="账户编码"><el-input v-model="accForm.accountCode" placeholder="留空由系统取号" maxlength="64" /></el-form-item>
        <el-form-item label="账户名称" prop="accountName"><el-input v-model="accForm.accountName" placeholder="请输入账户名称" /></el-form-item>
        <el-form-item label="开户银行" prop="bankName"><el-select v-model="accForm.bankName" filterable allow-create default-first-option placeholder="请选择或输入开户银行" style="width:100%"><el-option label="工商银行" value="工商银行" /><el-option label="建设银行" value="建设银行" /><el-option label="农业银行" value="农业银行" /><el-option label="中国银行" value="中国银行" /><el-option label="交通银行" value="交通银行" /><el-option label="招商银行" value="招商银行" /><el-option label="邮储银行" value="邮储银行" /><el-option label="其他" value="其他" /></el-select></el-form-item>
        <el-form-item label="银行账号" prop="bankAccount"><el-input v-model="accForm.bankAccount" placeholder="请输入银行账号" maxlength="64" /></el-form-item>
        <el-form-item label="币种"><el-select v-model="accForm.currencyCode" style="width:100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
</el-select></el-form-item>
        <div style="color:#909399; font-size:12px; padding-left:2px">保存即发起 TR_BANK_MASTER 银行主数据审批流（资金岗经办→财务经理复核）</div>
      </el-form>
      <template #footer>
        <el-button @click="dlgAccount = false">取消</el-button>
        <el-button type="primary" :loading="accSaving" @click="saveAccount">保存提交审批</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <CoachMark ref="treasuryCm" :guide-key="_guideKey" :selector="'.cm-target-treasury'" title="资金看板" text="资金看板展示资金收支概况、银行余额和现金流预测。" />
  </div>
</template>

<script setup>

import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { validateForms } from '@/composables/useSubmitGuard'
import { commonRules } from '@/composables/useFormRules'
import { treasuryApi, masterApi, p2Api, orgApi, customerApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { formatDateTime } from '@/utils/format'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import CoachMark from '@/components/CoachMark.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { useCurrency } from '@/composables/useCurrency'
// 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})
const treasuryCm = ref(null)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
/**
 * 页签 → 单据域（最后一个未声明域的追溯入口就在 bankRecon 页签）。
 * 追溯抽屉按 (bizType, bizId) 关联 wf_instance：不声明域时服务端只能拿裸主键去猜单据。
 * TR_BANK_RECON 与 exp_bill 的 biz_id 编号空间重叠，裸主键撞上就会渲染成别人的单据。
 * position 页签是聚合视图（非单据列表）⇒ 无追溯入口，不在本表。
 */
const TRACE_BIZ_BY_TAB = {
  plan: 'TR_PLAN',
  credit: 'TR_RECEIPT',
  transfer: 'TR_TRANSFER',
  settle: 'TR_BILL',
  bank: 'TR_BANK_MASTER',
  bankRecon: 'TR_BANK_RECON',
}
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = TRACE_BIZ_BY_TAB[tab.value] || null
  traceVisible.value = true
}

const selRows = ref([])
// 资金管理在 sys_menu 里只有「资金调拨」一条菜单行，七个子视图没有拆出的子菜单。
// 页签条若按「一 TAB = 一菜单」隐藏，收款/调拨/票据/银行主数据/对账/头寸就只能手敲 URL。
// 菜单未拆的页一律露页签：深链进来仍定位到指定页签，其余页签也可点。
const props = defineProps({ tab: { type: String, default: '' } })
const tab = ref(props.tab || 'plan')
const loading = ref(false)
const plans = ref([])
const credits = ref([])
const transfers = ref([])
const settles = ref([])
const accounts = ref([])
// 调拨弹窗直接吃「银行主数据」分页器的当前页（20 条）时，目标账户不在该页就永远搜不到。
// 弹窗与行内账户名查找改用独立全量查询，与主数据表格的分页互不牵制。
const accountOptions = ref([])
const recons = ref([])
// 资金头寸（非分页聚合，不做 server pager → activePager 自动为 null 隐藏 TablePager）
const position = ref(null)
const positionAccounts = ref([])
const netPosition = computed(() => Number(position.value?.netPosition || 0))

// 6 个 tab 全部走服务端分页：后端本就返回 PageResult，前端曾固定截断 200 条
const pagers = {
  plan: useTablePager({ mode: 'server', storageKey: 'treasury_plan' }),
  credit: useTablePager({ mode: 'server', storageKey: 'treasury_credit' }),
  transfer: useTablePager({ mode: 'server', storageKey: 'treasury_transfer' }),
  settle: useTablePager({ mode: 'server', storageKey: 'treasury_settle' }),
  bank: useTablePager({ mode: 'server', storageKey: 'treasury_bank' }),
  bankRecon: useTablePager({ mode: 'server', storageKey: 'treasury_bankRecon' }),
}
const { pagedData: pagedPlans } = pagers.plan
const { pagedData: pagedCredits } = pagers.credit
const { pagedData: pagedTransfers } = pagers.transfer
const { pagedData: pagedSettles } = pagers.settle
const { pagedData: pagedAccounts } = pagers.bank
const { pagedData: pagedRecons } = pagers.bankRecon
const tabLoaders = {}
const activePager = computed(() => pagers[tab.value] || null)
function reloadTab() { tabLoaders[tab.value]?.() }
const companies = ref([])
const customers = ref([])

// 名称查找
function bankAcc(id) { return accountOptions.value.find((x) => x.id === id) || accounts.value.find((x) => x.id === id) || null }
function bankAccName(id) { const a = bankAcc(id); return a ? a.accountName : (id || '—') }
function bankAccNo(id) { const a = bankAcc(id); return a ? (a.bankAccount || '') : '' }
function companyName(id) { const c = companies.value.find((x) => x.id === id); return c ? c.name : (id || '—') }
function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? c.customerName : (id || '—') }

// 状态中文
function stLabel(map, s) { return map[s] || s }
const planStMap = { DRAFT: '草稿', SELECTED: '已选中', COMPLETED: '已完成' }
const credStMap = { DRAFT: '草稿', RECONCILED: '已核销', UNMATCHED: '未匹配' }
const tfStMap = STATUS_LABEL
const settleStMap = { STOCK: '在库', ENDORSED: '已背书', DISCOUNTED: '已贴现', COLLECTING: '托收中', MATURED: '已到期' }
const accStMap = { NORMAL: '正常', DISABLED: '已停用' }
const reconStMap = { MATCHED: '已勾对', UNMATCHED: '未勾对' }
function tagType(s) { return STATUS_TYPE[s] || 'info' }

/**
 * 审批/动账/登账三件事各自取值。优先用接口明示位（execStateText／journalText），
 * 老包没带时按 status 兜底推导——execute() 是改 md_bank_account.balance 的唯一环节，
 * 而本域没有日记账登记环节，故非 PAID 即"余额未变"、任何状态都"未登账"。
 */
function execStateText(row) {
  if (row.execStateText) return row.execStateText
  if (row.status === BillStatus.PAID) return '已执行·账户余额已落账'
  if (row.status === BillStatus.APPROVED) return '已审批·未执行·账户余额未变'
  return '未审批·未执行·账户余额未变'
}
function journalText(row) {
  return row.journalText || '未登账：现金/银行日记账未登记'
}

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// Form rules
// 七个 rules 对象若留空且 el-form 不带 model/prop，提交时根本不校验＝三条都缺的死规则。
// 只写 `{ required, message, trigger }` 三键会让 async-validator 改走 string 校验器，
// 于是 companyId（下拉选中的数字）被判非法＝填齐也提交不过（假拦，见 requiredRules.spec.js 断言）。
// 现统一取 `commonRules.*`（自定义 validator，类型无关且仍带红星）。
// 必填集规则＝逐支只镜像各自服务端已有的断言；服务端没拦的字段不在前端加码
//（前端单方加码＝假拦，且绕过界面仍是脏数据＝与"星号＝拦截"规则相反）。
const planFormRules = {
  period: commonRules.required('请选择计划期间'),
  planDate: commonRules.required('请选择计划日期'),
  companyId: commonRules.required('请选择所属公司'),
}
// TrFundPlanService.combineActual 无断言（null 兜 0）⇒ 只拦"没填"，0 与负数放行
const combineFormRules = {
  incomeActual: commonRules.required('请输入实际收入'),
  outcomeActual: commonRules.required('请输入实际支出'),
}
// TrFundPlanService.adjust:177「收入/支出调整额不能同时为空或 0」＋:184「调整后不能为负」，文案与服务端逐字一致
const adjustFormRules = {
  incomeAdjust: adjustAmountRule(),
  outcomeAdjust: adjustAmountRule(),
}
// TrCreditBillService.receiveRecord:62「收款金额必须大于 0」＝本支唯一服务端断言
const creditFormRules = {
  amount: commonRules.amount('收款金额必须大于 0'),
}
// TrTransferService.create:46/49/52＝账户非空 + 两账户不同 + 金额>0
const transferFormRules = {
  fromBankAccountId: commonRules.required('请选择付款账户'),
// 不要本条规则不带 message：async-validator 4.2.5 见到 rule.message 就把 validator 抛出的 Error 文案整条替换掉
//（`if (errorList.length && rule.message !== undefined) errorList = [].concat(rule.message)`），
// 于是"没选收款账户"会被显示成"转出与转入账户不能相同"＝报错与病因错位。多因 validator 一律只留 required 标志（红星仍出）。
  toBankAccountId: [{
    required: true,
    validator: (rule, value, cb) => {
      if (value === null || value === undefined || value === '') return cb(new Error('请选择收款账户'))
      if (value === tfForm.value.fromBankAccountId) return cb(new Error('转出与转入账户不能相同'))
      cb()
    },
  }],
  amount: commonRules.amount('调拨金额必须大于 0'),
}
// 本支服务端只断言「票据号不能为空」；票面金额/出票日/到期日服务端没有校验，前端不加码
const settleFormRules = {
  ticketNo: commonRules.required('票据号不能为空'),
}
// 服务端断言账户名称/开户银行/银行账号非空；账户编码由系统取号、人工只读⇒不加红星
const accountFormRules = {
  accountName: commonRules.required('账户名称不能为空'),
  bankName: commonRules.required('开户银行不能为空'),
  bankAccount: commonRules.required('银行账号不能为空'),
}
function adjustAmountRule() {
  // 同上：不带 message，否则界面上只会显示「请输入收入调整额」，服务端真实原因（同时为空或 0／调整后为负）被吞。
  return [{
    required: true,
    validator: (rule, value, cb) => {
      const inc = Number(adjustForm.value.incomeAdjust || 0)
      const out = Number(adjustForm.value.outcomeAdjust || 0)
      if (inc === 0 && out === 0) return cb(new Error('收入/支出调整额不能同时为空或 0'))
      if (adjustedIncome.value < 0 || adjustedOutcome.value < 0) return cb(new Error('调整失败：调整后期间计划金额不能为负'))
      cb()
    },
  }]
}
const emptyPlan = () => ({ period: '', planDate: '', companyId: null, incomeAmount: 0, outcomeAmount: 0, status: 'DRAFT' })
const planForm = ref(emptyPlan())
const planFormRef = ref(null)
const planSaving = ref(false)
const dlgPlan = ref(false)
// 六支弹窗的 el-form ref＋提交在途位：防连点的前端环节
const combineFormRef = ref(null)
const adjustFormRef = ref(null)
const creditFormRef = ref(null)
const tfFormRef = ref(null)
const settleFormRef = ref(null)
const accFormRef = ref(null)
const combineSaving = ref(false)
const adjustSaving = ref(false)
const creditSaving = ref(false)
const tfSaving = ref(false)
const stSaving = ref(false)
const accSaving = ref(false)
const dlgCombine = ref(false)
const combineId = ref(null)
// 金额缺省一律 null，不用裸 `0` 当「未填」哨兵。
// 服务端 combineActual/adjust 各自有 null 兜法（combine 取原值再兜 0；adjust 由 :177 拦「同时为空或 0」），
// 裸 0 会让 required 规则永远不触发＝红星形同虚设，且把「没填」写成覆盖性的 0。
const combineForm = ref({ incomeActual: null, outcomeActual: null })
const combinePlan = ref({})
const dlgAdjust = ref(false)
const adjustId = ref(null)
const adjustPlanNo = ref('')
const adjustPlan = ref({})
const adjustForm = ref({ incomeAdjust: null, outcomeAdjust: null })
// 调整后 = 原计划 + 本次调整额（实时联动显示）
const adjustedIncome = computed(() => Number(adjustPlan.value.incomeAmount || 0) + Number(adjustForm.value.incomeAdjust || 0))
const adjustedOutcome = computed(() => Number(adjustPlan.value.outcomeAmount || 0) + Number(adjustForm.value.outcomeAdjust || 0))
// F3：裸值 `0` 当"未选"哨兵＝下拉永远显示空、却把 id=0 当真值发出去
//（tr_credit_bill.customer_id=0 是脏引用；TrTransferService.create 只判 `== null`，
//  0 能穿过非空断言、到 execute() 才报"转出银行账户不存在"＝报错与病因错位）。未选一律 null。
const emptyCredit = () => ({ receiveDate: '', customerId: null, payMethod: '', payerBank: '', payerAccount: '', amount: 0, currencyCode: 'CNY', sourceChannel: '' })
const creditForm = ref(emptyCredit())
const dlgCredit = ref(false)
const emptyTf = () => ({ transferDate: '', fromBankAccountId: null, toBankAccountId: null, companyId: null, amount: 0, purpose: '', status: 'DRAFT' })
const tfForm = ref(emptyTf())
const tfMode = ref('transfer')
const dlgTransfer = ref(false)
const emptySt = () => ({ ticketNo: '', ticketType: '银承', issuerBank: '', amount: 0, issueDate: '', dueDate: '', beneficiary: '', status: 'STOCK' })
const stForm = ref(emptySt())
const dlgSettle = ref(false)
// 余额快照登记（头寸页→bank_balance；服务端 BankReconcileService#saveBalance 三非空＋同账户同日期去重覆盖）
const dlgBalance = ref(false)
const balanceFormRef = ref(null)
const balanceSaving = ref(false)
const emptyBalance = () => ({ accountId: null, balanceDate: '', balance: null, remark: '' })
const balanceForm = ref(emptyBalance())
const balanceFormRules = {
  accountId: commonRules.required('请选择账户'),
  balanceDate: commonRules.required('请选择余额日期'),
  balance: commonRules.required('请输入余额'),
}

function loadPlan() { try { return pagers.plan.load(async ({ pageNum, pageSize }) => { const d = await treasuryApi.fundPlanPage({ pageNum, pageSize }); plans.value = d.list || []; planDenied.value = false; return { list: plans.value, total: d.total || 0 } }).catch((e) => { if (e?.code === 4030) { planDenied.value = true; return } ElMessage.error(e.message) }) } catch (e) { /* noop */ } }
function loadCredit() { try { return pagers.credit.load(async ({ pageNum, pageSize }) => { const d = await treasuryApi.creditBillPage({ pageNum, pageSize }); credits.value = d.list || []; return { list: credits.value, total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ } }
function loadTransfer() { try { return pagers.transfer.load(async ({ pageNum, pageSize }) => { const d = await treasuryApi.transferPage({ pageNum, pageSize }); transfers.value = d.list || []; return { list: transfers.value, total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ } }
function loadSettle() { try { return pagers.settle.load(async ({ pageNum, pageSize }) => { const d = await treasuryApi.billSettlePage({ pageNum, pageSize }); settles.value = d.list || []; return { list: settles.value, total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ } }

async function openPlan() { planForm.value = emptyPlan(); dlgPlan.value = true; planFormRef.value?.clearValidate?.() }
async function savePlan() {
  // B6：F1 重复提交的两条前端环节——①必填校验（缺期间/日期/公司不再发请求）
  //     ②一次提交未完成前不再接受第二次点击（:loading + in-flight 兜底）。
  if (planSaving.value) return
  if (!(await validateForms([planFormRef]))) return
  planSaving.value = true
  try { await treasuryApi.fundPlanCreate(planForm.value); ElMessage.success('计划已编制'); dlgPlan.value = false; loadPlan() }
  catch (e) { ElMessage.error(e.message) }
  finally { planSaving.value = false }
}
async function selectPlan(row) { try { await treasuryApi.fundPlanSelect(row.id); ElMessage.success('已选中'); loadPlan() } catch (e) { ElMessage.error(e.message) } }
// 驳回计划唯一正规出口——REJECTED→DRAFT 并重新发起 TR_PLAN 审批流
async function resubmitPlan(row) {
  try { await ElMessageBox.confirm('确认将该驳回计划转回草稿并重新发起审批流？', '编辑重提', { type: 'warning' }) } catch { return }
  try { await treasuryApi.fundPlanResubmit(row.id); ElMessage.success('已重新提交审批'); loadPlan() } catch (e) { ElMessage.error(e.message) }
}
function openCombine(row) {
  combineId.value = row.id
  combineForm.value = { incomeActual: row.incomeActual ?? null, outcomeActual: row.outcomeActual ?? null }
  combinePlan.value = { ...row }
  dlgCombine.value = true
  combineFormRef.value?.clearValidate?.()
}
async function saveCombine() {
  if (combineSaving.value) return
  if (!(await validateForms([combineFormRef]))) return
  combineSaving.value = true
  try { await treasuryApi.fundPlanCombine(combineId.value, combineForm.value.incomeActual, combineForm.value.outcomeActual); ElMessage.success('已回填'); dlgCombine.value = false; loadPlan() }
  catch (e) { ElMessage.error(e.message) }
  finally { combineSaving.value = false }
}

async function openCreditBill() { creditForm.value = emptyCredit(); dlgCredit.value = true; creditFormRef.value?.clearValidate?.() }
async function saveCredit() {
  if (creditSaving.value) return
  if (!(await validateForms([creditFormRef]))) return
  creditSaving.value = true
  try { await treasuryApi.creditBillReceive(creditForm.value); ElMessage.success('已认领'); dlgCredit.value = false; loadCredit() }
  catch (e) { ElMessage.error(e.message) }
  finally { creditSaving.value = false }
}
async function autoRecon() {
  // 原按钮与「银行对账」tab 的自动对账同名「自动对账」，且确认框把对象写错成
  // 「应收票据」——本端点实为 tr_credit_bill（资金收款单）按同金额银行流水自动核销。
  // 现按钮名/确认框/落空文案三处都按真实对象与真实结果说。
  try { await ElMessageBox.confirm('将对全部待核销的资金收款单，按同金额银行流水自动核销（命中即置已核销，不可撤销），确认继续？', '收款流水自动核销', { type: 'warning' }) } catch { return }
  try {
    const n = await treasuryApi.creditBillAutoRecon()
    const hit = Number(n || 0)
    if (hit <= 0) ElMessage({ type: 'warning', message: '自动核销完成：命中 0 条（无同金额未对账银行流水），未产生任何核销' })
    else ElMessage.success('自动核销完成，命中 ' + hit + ' 条')
    loadCredit()
  } catch (e) { ElMessage.error(e.message) }
}
async function confirmRecon(row) {
  try { await ElMessageBox.confirm('核销后该票据状态将变为已核销且不可撤销，确认核销？', '确认核销', { type: 'warning' }) } catch { return }
  try {
    // 丙：核销照做，只把"本人建单—本人确认核销"这条不经审批的旁路显示给经办看（不拆权、不拦操作）
    const r = await treasuryApi.creditBillConfirmRecon(row.id)
    if (r && r.warning) ElMessage({ type: 'warning', message: '已核销：' + r.warning, duration: 6000 })
    else ElMessage.success('已核销')
    loadCredit()
  } catch (e) { ElMessage.error(e.message) }
}

async function openTransfer(mode) { tfMode.value = mode || 'transfer'; tfForm.value = emptyTf(); dlgTransfer.value = true; tfFormRef.value?.clearValidate?.() }
async function saveTransfer() {
  if (tfSaving.value) return
  if (!(await validateForms([tfFormRef]))) return
  tfSaving.value = true
  try {
    if (tfMode.value === 'upload') { await treasuryApi.transferUpload(tfForm.value); ElMessage.success('上存已发起，进入资金上存审批') }
    else { await treasuryApi.transferCreate(tfForm.value); ElMessage.success('调拨已发起') }
    dlgTransfer.value = false; loadTransfer()
  } catch (e) { ElMessage.error(e.message) }
  finally { tfSaving.value = false }
}
// 草稿/驳回待重提调拨单的正规重提通道（上存单按 purpose 标记切 TR_UPLOAD 流）
async function submitTransfer(row) {
  const flow = (row.purpose || '').includes('上存') ? 'TR_UPLOAD' : 'TR_TRANSFER'
  try { await treasuryApi.transferSubmit(row.id, flow); ElMessage.success('已提交审批'); loadTransfer() } catch (e) { ElMessage.error(e.message) }
}
async function approveTransfer(row) { try { await treasuryApi.transferApprove(row.id); ElMessage.success('已审批'); loadTransfer() } catch (e) { ElMessage.error(e.message) } }
// 核准按钮的可见条件：草稿态 + 不是本人创建。取不到登录用户时按「可核准」放行，
// 宁可多画一枚被后端 4030 拦，也不要把有权限的人误锁在门外。
function canApproveTransfer(row) {
  if (row.status !== BillStatus.DRAFT) return false
  const uid = store?.user?.id
  if (!uid || row.createBy == null) return true
  return String(row.createBy) !== String(uid)
}
async function executeTransfer(row) {
  // 调拨执行发生在实例已 APPROVED 之后，执行动作只落账、不再新起任何审批节点
  // （sys_oper_log 有记录而 wf_task_history 无新行）。文案若暗示还有后续审批，会把用户引到空等。
  try { await ElMessageBox.confirm('执行后款项余额将直接落账、不可直接撤销，确认执行？', '执行调拨', { type: 'warning' }) } catch { return }
  try { await treasuryApi.transferExecute(row.id); ElMessage.success('已执行，余额已落账'); loadTransfer() } catch (e) { ElMessage.error(e.message) }
}

function openAdjust(row) {
  adjustId.value = row.id
  adjustPlanNo.value = row.planNo || String(row.id)
  adjustPlan.value = { ...row }
  adjustForm.value = { incomeAdjust: null, outcomeAdjust: null }
  dlgAdjust.value = true
  adjustFormRef.value?.clearValidate?.()
}
async function saveAdjust() {
  if (adjustSaving.value) return
  if (!(await validateForms([adjustFormRef]))) return
  adjustSaving.value = true
  try { await treasuryApi.fundPlanAdjust(adjustId.value, adjustForm.value.incomeAdjust, adjustForm.value.outcomeAdjust); ElMessage.success('计划调整已提交，进入审批'); dlgAdjust.value = false; loadPlan() }
  catch (e) { ElMessage.error(e.message) }
  finally { adjustSaving.value = false }
}

async function openSettle() { stForm.value = emptySt(); dlgSettle.value = true; settleFormRef.value?.clearValidate?.() }
async function saveSettle() {
  if (stSaving.value) return
  if (!(await validateForms([settleFormRef]))) return
  stSaving.value = true
  try { await treasuryApi.billSettleCreate(stForm.value); ElMessage.success('票据已登记'); dlgSettle.value = false; loadSettle() }
  catch (e) { ElMessage.error(e.message) }
  finally { stSaving.value = false }
}
async function endorse(row) { try { await treasuryApi.billSettleEndorse(row.id); ElMessage.success('已背书转让'); loadSettle() } catch (e) { ElMessage.error(e.message) } }
async function discount(row) { try { await treasuryApi.billSettleDiscount(row.id); ElMessage.success('已贴现'); loadSettle() } catch (e) { ElMessage.error(e.message) } }
async function collect(row) { try { await treasuryApi.billSettleCollection(row.id); ElMessage.success('已托收到期收妥'); loadSettle() } catch (e) { ElMessage.error(e.message) } }

// 银行主数据
const emptyAcc = () => ({ accountCode: '', accountName: '', bankName: '', bankAccount: '', currencyCode: 'CNY', status: 'NORMAL' })
const accForm = ref(emptyAcc())
const dlgAccount = ref(false)
// 调拨/上存弹窗的账户全量候选：按 200/页翻到底（上限 20 页＝4,000 条），只留启用账户
async function loadAccountOptions() {
  try {
    let all = [], pageNum = 1, total = 0
    do {
      const d = await masterApi.bank.page({ pageNum, pageSize: 200 })
      all = all.concat(d.list || [])
      total = Number(d.total || 0)
      pageNum++
    } while (all.length < total && pageNum <= 20)
    accountOptions.value = all.filter((a) => !a.status || a.status === 'NORMAL')
  } catch (e) { /* 无 md:bankaccount:list 时留空，由后端写校验兜底 */ }
}
// 银行主数据页签的关键字检索（后端 masterApi.bank.page 的 keyword 与「银行账户管理」页同一规则）
const bankKeyword = ref('')
function searchBank() { pagers.bank.resetPage(); loadBank() }
function loadBank() { try { return pagers.bank.load(async ({ pageNum, pageSize }) => { const d = await masterApi.bank.page({ pageNum, pageSize, keyword: bankKeyword.value || undefined }); accounts.value = d.list || []; return { list: accounts.value, total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ } }
function openAccount() { accForm.value = emptyAcc(); dlgAccount.value = true; accFormRef.value?.clearValidate?.() }
async function saveAccount() {
  if (accSaving.value) return
  if (!(await validateForms([accFormRef]))) return
  accSaving.value = true
  try { await masterApi.bank.create(accForm.value); ElMessage.success('银行主数据已提交，进入「银行主数据」审批'); dlgAccount.value = false; loadBank() }
  catch (e) { ElMessage.error(e.message) }
}
async function toggleAccount(row) {
  try { await masterApi.bank.enable(row.id, row.status !== 'NORMAL'); ElMessage.success('已更新账户状态'); loadBank() }
  catch (e) { ElMessage.error(e.message) }
}

// 银行对账
function loadRecon() { try { return pagers.bankRecon.load(async ({ pageNum, pageSize }) => { const d = await p2Api.bankReconcilePage({ pageNum, pageSize }); recons.value = d.list || []; return { list: recons.value, total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ } }
Object.assign(tabLoaders, { plan: loadPlan, credit: loadCredit, transfer: loadTransfer, settle: loadSettle, bank: loadBank, bankRecon: loadRecon })
// 4030 权限拒绝独立空态（资金计划/资金头寸各自判定，否则与「暂无数据」同形）
const planDenied = ref(false)
const positionDenied = ref(false)
// 资金头寸加载（聚合接口，一次拉全量）
async function loadPosition() {
  try {
    const d = await treasuryApi.position()
    position.value = d || {}
    positionAccounts.value = d?.accounts || []
    positionDenied.value = false
  } catch (e) {
    // 4030（无 treasury:position:list 等任一读码）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { positionDenied.value = true; return }
    ElMessage.error(e.message)
  }
}
tabLoaders.position = loadPosition
// 登记余额入口——保存后立即刷新头寸（余额/余额表日/净头寸联动）
function openBalance() { balanceForm.value = emptyBalance(); dlgBalance.value = true; balanceFormRef.value?.clearValidate?.() }
async function saveBalance() {
  if (balanceSaving.value) return
  if (!(await validateForms([balanceFormRef]))) return
  balanceSaving.value = true
  try { await p2Api.bankBalanceSave(balanceForm.value); ElMessage.success('余额已登记，头寸已刷新'); dlgBalance.value = false; loadPosition() }
  catch (e) { ElMessage.error(e.message) }
  finally { balanceSaving.value = false }
}
async function runBankRecon() {
  // 银企直连页的自动对账同样必须当场反馈结果体（勾对/未勾对条数 + 未勾对流水号 + 拒配原因），
  // 出纳不必翻页核对、也不必等调节表环节才发现漏勾的流水
  // 甲＋乙：命中 0 条不得宣告「已发起 TR_BANK_RECON 对账审批」；"已发起审批"只认后端 processStarted
  //   （后端 BankReconcileService 仅在勾对数 > 0 且 start 成功时置 true，零命中必为 false）
  // 丙：高危批处理动作统一加确认框
  try {
    await ElMessageBox.confirm(
      '将扫描未勾对的银行流水并按同金额自动勾对，命中后自动发起银行对账审批；无银行流水时命中 0 条、不产生任何写入。确认继续？',
      '银行流水勾对', { type: 'warning', confirmButtonText: '确认勾对', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  try {
    const r = await p2Api.bankReconcileRun()
    const m = Number(r?.matched || 0); const u = Number(r?.unmatched || 0)
    const s = Number(r?.scanned || 0)
    const a = Number(r?.ambiguous || 0); const f = Number(r?.foreignSerialBlocked || 0)
    const veto = (a > 0 ? `，同金额多候选拒配 ${a} 条` : '') + (f > 0 ? `，${f} 条同金额单据已归属其他银行流水号` : '')
    if (m === 0) {
      ElMessage({
        type: 'warning', duration: 6000,
        message: s === 0 ? '无可对账流水（银行流水未接入），未发起审批'
          : `自动勾对完成：扫描未勾对流水 ${s} 条，命中 0 条（无同金额单据可配${veto}），未产生勾对记录，未发起银行对账审批`
      })
    } else {
      // 丙：命中本次勾对的收款核销单若是建单人本人勾对，同一句里报出复核提示（不阻断）
      ElMessage({ type: (u > 0 || r?.selfMatchWarning) ? 'warning' : 'success', duration: 6000, message: `自动对账完成：勾对 ${m} 条、未勾对 ${u} 条${a > 0 ? `（同金额多候选拒配 ${a} 条）` : ''}${f > 0 ? `（${f} 条同金额单据已归属其他银行流水号，拒绝顶替）` : ''}${r?.selfMatchWarning ? `（${r.selfMatchWarning}）` : ''}；${r?.processStarted ? '已发起银行对账审批' : '未发起审批（勾对记录已生成，请人工复核）'}` })
    }
    const serials = Array.isArray(r?.unmatchedSerials) ? r.unmatchedSerials : []
    if (serials.length) ElMessage({ type: 'info', duration: 10000, message: '未勾对流水：' + serials.slice(0, 12).join('、') + (serials.length > 12 ? ' 等' + serials.length + '条' : '') })
    loadRecon()
  }
  catch (e) { ElMessage.error(e.message) }
}

function reload() { loadPlan(); loadCredit(); loadTransfer(); loadSettle(); loadBank(); loadAccountOptions(); loadRecon(); loadPosition() }

async function loadMasters() {
  try {
    const [compRes, custRes] = await Promise.allSettled([
      orgApi.companyTree(),
      customerApi.page({ pageNum: 1, pageSize: 500 }),
    ])
    if (compRes.status === 'fulfilled') {
      const flat = []
      const walk = (arr) => { arr.forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
      walk(compRes.value || [])
      companies.value = flat
    }
    if (custRes.status === 'fulfilled') {
      customers.value = custRes.value.list || []
    }
  } catch (e) { /* ignore */ }
}
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

onMounted(async () => { await loadMasters(); reload() })
 loadCurrency()

function exportCsv() {
  if (!credits.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '单据号' }, { prop: 'receiveDate', label: '收款日期' }, { prop: 'payerBank', label: '付款方银行' }, { prop: 'payerAccount', label: '付款方账号' }, { prop: 'sourceChannel', label: '渠道' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = credits.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `TreasuryBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
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
  a.download = `TreasuryBoard_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}


// Show guide on first visit
const _guideKey = 'fssc_guide_treasuryCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => treasuryCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
.tf-leg-note { color: #e6a23c; font-size: 12px; }
.tf-exec-state { font-size: 12px; color: var(--app-text-secondary, #606266); margin-top: 2px; line-height: 1.4; }
.tf-exec-state.tf-exec-wait { color: #e6a23c; font-weight: 600; }
.tf-journal { font-size: 11px; color: #909399; line-height: 1.4; }
.pos-card {
  border: 1px solid #e4e7ed;
  border-left: 4px solid #909399;
  border-radius: 6px;
  padding: 10px 16px;
  min-width: 200px;
  background: var(--app-hover, #fafafa);
}
.pos-label { color: #909399; font-size: 12px; margin-bottom: 4px; }
.pos-value { font-size: 20px; font-weight: 600; color: var(--app-text, #303133); }
.pos-sub { font-size: 12px; font-weight: 400; color: #909399; margin-left: 4px; }
</style>