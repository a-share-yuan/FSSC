<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>GL 专项流程</b>
        <!-- R5-D-163：副标题原枚举六项漏第七个页签「合并报表」，按页签实有条数补齐 -->
        <span style="color:#909399; font-size:12px">税金计提 / 薪酬发放 / 内部往来抵消 / 一般记账 / 月结 / 合并报表 / GL主数据 —— 生成即发起审批流并落账</span>
        <!-- 本单上下文（D-DEC-127/139/148）：待办「去处理」带 ?billNo= 进来时，列表只展示被审的那一张 -->
        <el-tag v-if="deepKeyword" size="small" type="warning" closable @close="clearDeepLink">
          已按单号定位：{{ deepKeyword }}
        </el-tag>
        <el-button size="small" style="margin-left:auto" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- 税金计提 -->
      <el-tab-pane label="税金计提" name="tax">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <!-- R5-D-118：期间选择器原为空转控件（改选后列表请求 period 恒空）——现改选即按所选期间重查列表 -->
          <el-date-picker v-model="taxPeriod" type="month" value-format="YYYYMM" placeholder="税金期间" style="width:130px" @change="loadTax" />
          <!-- D-DEC-140：默认期间的来源在界面写明，避免"没改就直接点到别的期间去" -->
          <span style="color:#909399; font-size:12px">
            默认期间取{{ periodSource }}：{{ openPeriod || '取数中…' }}
          </span>
          <el-button type="success" @click="doProvision">计提税金（提交审批）</el-button>
          <!-- R5-D-154：落账挂接核查——计提是否真落了账，本页可查（只读，不补记不改账） -->
          <el-button v-if="canLinkCheck" type="primary" plain @click="openLinkCheck">落账挂接核查</el-button>
          <!-- D-DEC-440：缴纳税金属资金/出纳岗职能，计提审批通过只确立应付税额，缴款本身走对公付款通道；
               本页不再暴露"缴纳"发起入口（流程定义 GL_TAX_PAY 保留，供计提-缴纳联动与历史实例查询）。 -->
          <span class="tax-pay-route">税金通过对公付款通道缴纳（付款单 / 资金付款流程），本页面只做计提</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table class="cm-target-gl" :data="taxData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据" @row-dblclick="handleView">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="provisionNo" label="计提单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column prop="taxType" label="税种" width="110"  sortable :formatter="(row, col, val) => taxTypeLabel(val)" />
          <el-table-column label="计税基数" width="130" align="right"><template #default="{ row }">{{ fmt(row.baseAmount) }}</template></el-table-column>
          <el-table-column label="税率" width="90" align="right"><template #default="{ row }">{{ row.rate }}%</template></el-table-column>
          <el-table-column label="税额" width="130" align="right"><template #default="{ row }">{{ fmt(row.taxAmount) }}</template></el-table-column>
          <el-table-column label="科目" width="120"><template #default="{ row }">{{ subjectName(row.subjectId) }}</template></el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- R5-D-153 -->
          <el-table-column prop="voucherNo" label="记账凭证号" min-width="140" show-overflow-tooltip  sortable>
            <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerTax" @change="loadTax" />
      </el-tab-pane>

      <!-- 薪酬发放 -->
      <el-tab-pane label="薪酬发放" name="salary">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <el-input v-model="salaryForm.period" placeholder="期间 yyyyMM" style="width:140px" />
          <el-select v-model="salaryForm.deptId" filterable clearable placeholder="选择部门" style="width:180px">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-input-number v-model="salaryForm.empCount" :min="1" placeholder="人数" style="width:120px" />
          <el-button type="success" :disabled="salaryPeriodClosed" @click="doCalculate">核算薪酬（提交审批）</el-button>
          <span v-if="salaryPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
          <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="salaryData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="runNo" label="批次号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column label="部门" width="120"><template #default="{ row }">{{ deptName(row.deptId) }}</template></el-table-column>
          <el-table-column prop="empCount" label="人数" width="70" align="center"  sortable />
          <el-table-column label="应发工资" width="130" align="right"><template #default="{ row }">{{ fmt(row.salaryAmount) }}</template></el-table-column>
          <!-- D-DEC-143：deduction_amount（列注释「扣除合计（旷工/罚款等）」）此前不上列表，「其他扣除」对使用者不可见 -->
          <el-table-column label="其他扣除" width="120" align="right"><template #default="{ row }">{{ fmt(row.deductionAmount) }}</template></el-table-column>
          <el-table-column label="社保" width="120" align="right"><template #default="{ row }">{{ fmt(row.socialSecurityAmount) }}</template></el-table-column>
          <el-table-column label="个税" width="110" align="right"><template #default="{ row }">{{ fmt(row.taxAmount) }}</template></el-table-column>
          <el-table-column label="应付总额" width="130" align="right"><template #default="{ row }">{{ fmt(row.totalAmount) }}</template></el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.payStatus)" size="small">{{ stLabel(row.payStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">          <InlineActions :visible="true" @view="handleView(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.payStatus !== BillStatus.PAID" link size="small" type="warning" @click="doPay(row)">发放</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerSalary" @change="loadSalary" />
      </el-tab-pane>

      <!-- 内部往来抵消 -->
      <el-tab-pane label="内部往来抵消" name="inter">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <el-input v-model="interForm.period" placeholder="期间 yyyyMM" style="width:140px" />
          <el-button type="success" :disabled="interPeriodClosed" @click="doGenerate">生成抵消（提交审批）</el-button>
          <span v-if="interPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
          <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
          <!-- X-52（D-DEC-294）手工成对录入口子：后端 POST /glex/inter-offset/create 早已就位，
               缺的只是本页入口。按本页既有风格在同页签内展开录入区，不叠弹窗。 -->
          <el-button v-if="canManualPair" type="primary" plain @click="toggleManualPair">
            {{ manualVisible ? '收起手工录入' : '手工成对录入' }}
          </el-button>
          <span v-if="canManualPair" style="color:#909399; font-size:12px">
            往来余额不匹配、需按实际业务手工录入一一对应的抵消分录时用此项
          </span>
        </div>
        <!-- 手工成对录入：一条金额 → 来账(应收) + 往账(应付) 两条镜像分录，方向由表单固定，
             提交即成对相等，落后端 assertPairEqual；必填口径见 manualRules 注释。 -->
        <el-form v-if="canManualPair" v-show="manualVisible" ref="manualFormRef" :model="manualForm"
                 :rules="manualRules" inline style="margin-bottom:10px">
          <el-form-item label="期间" prop="period">
            <el-input v-model="manualForm.period" placeholder="期间 yyyyMM" style="width:120px" />
          </el-form-item>
          <el-form-item label="应收方公司">
            <el-select v-model="manualForm.receivableCompanyId" filterable clearable placeholder="来账方" style="width:170px">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="应付方公司">
            <el-select v-model="manualForm.payableCompanyId" filterable clearable placeholder="往账方" style="width:170px">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="抵消金额" prop="amount">
            <el-input-number v-model="manualForm.amount" :min="0.01" :precision="2" :controls="false"
                              placeholder="双方同额" style="width:140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="success" :loading="manualSubmitting" @click="doManualPair">
              提交成对分录（提交审批）
            </el-button>
          </el-form-item>
          <div style="color:#909399; font-size:12px; line-height:1.6">
            提交后生成 2 条草稿抵消分录：来账（应收抵消，应收方→应付方）与往账（应付抵消，应付方→应收方），金额同为上填值；
            审批通过后仍需在本列表「抵消记账」置为已抵消并出记账凭证号。期间与金额为必填（与后端一致），公司可留空。
          </div>
        </el-form>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="interData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="offsetNo" label="抵消单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column label="来账公司" width="130"><template #default="{ row }">{{ companyName(row.fromCompanyId) }}</template></el-table-column>
          <el-table-column label="往账公司" width="130"><template #default="{ row }">{{ companyName(row.toCompanyId) }}</template></el-table-column>
          <el-table-column prop="interFlag" label="类型" width="110"  sortable :formatter="(row, col, val) => interFlagLabel(val)" />
          <el-table-column label="抵消金额" width="130" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.offsetStatus)" size="small">{{ stLabel(row.offsetStatus) }}</el-tag>
            </template>
          </el-table-column>
          <!-- R5-D-153 -->
          <el-table-column prop="voucherNo" label="记账凭证号" min-width="140" show-overflow-tooltip  sortable>
            <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.offsetStatus !== 'OFFSET'" link size="small" type="warning" @click="doPost(row)">抵消记账</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerInter" @change="loadInter" />
      </el-tab-pane>
    <!-- 一般总账记账 -->
      <el-tab-pane label="一般记账" name="journal">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center; flex-wrap:wrap">
          <el-input v-model="journalForm.period" placeholder="期间 yyyyMM" style="width:120px" />
          <el-select v-model="journalForm.entryType" placeholder="记账类型" style="width:150px">
            <el-option label="费用预提" value="ACCRUAL" />
            <el-option label="费用待摊" value="DEFER" />
            <el-option label="手工凭证" value="MANUAL" />
            <el-option label="其他收款" value="OTHER_RECEIPT" />
          </el-select>
          <el-input-number v-model="journalForm.amount" :min="1" placeholder="金额" style="width:120px" />
          <el-input v-model="journalForm.summary" placeholder="摘要(可选)" style="width:160px" />
          <el-button type="success" :disabled="journalPeriodClosed" @click="doJournal">记账（发起对应审批）</el-button>
          <span v-if="journalPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
          <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="journalData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="entryNo" label="记账单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ typeName(row.entryType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="150" show-overflow-tooltip  sortable />
          <el-table-column label="金额" width="130" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- R5-D-153 -->
          <el-table-column prop="voucherNo" label="记账凭证号" min-width="140" show-overflow-tooltip  sortable>
            <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
          </el-table-column>
          <!-- R5-D-167：后端 GlJournalController POST /{id}/reverse（红字反向凭证/草稿作废，带守卫）原在 UI 无入口，
               本列补齐；仅 POSTED 行可冲销（服务端亦只放行 POSTED→REVERSED，原因必填留痕）。 -->
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'POSTED'" link size="small" type="warning" @click="doJournalReverse(row)">冲销</el-button>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerJournal" @change="loadJournal" />
      </el-tab-pane>

      <!-- 月结 -->
      <el-tab-pane label="月结" name="monthclose">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center; flex-wrap:wrap">
          <el-input v-model="mcForm.period" placeholder="期间 yyyyMM" style="width:120px" />
          <el-select v-model="mcForm.companyId" filterable clearable placeholder="选择公司" style="width:180px">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-button type="success" :disabled="mcPeriodClosed" @click="doMonthClose">月结（试算平衡并发起关账审批）</el-button>
          <span v-if="mcPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
          <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
          <!-- 反结账入口：输入期间 + 二次确认，回退后该期间重新开放记账；后端校验权限与下一期间状态 -->
          <el-button type="warning" @click="doMonthUnclose">反结账</el-button>
          <!-- 损益结转向导：选择期间 → 确认 → 展示后端返回结果（幂等拒绝时原样提示「该期间已结转」） -->
          <el-button type="primary" plain @click="openProfitCarry">损益结转</el-button>
        </div>
        <!-- R5-D-215：月结检查闸清单——后端三端点（生成清单 / 逐项完成 / 执行月结）接入本页 -->
        <div v-if="canCloseCheck" class="close-check">
          <div class="cc-head">
            <b>月结检查闸清单</b>
            <span class="cc-meta">期间 {{ checkPeriodLabel }} · 公司 {{ mcCompLabel }}</span>
            <el-progress v-if="closeCheck.length" :percentage="ccPercent" :stroke-width="10" style="width:150px" />
            <span v-if="closeCheck.length" class="cc-meta">已办结 {{ ccDone }} / 共 {{ closeCheck.length }} 项</span>
            <el-button size="small" :loading="ccLoading" @click="loadCloseCheck">刷新清单</el-button>
            <el-button size="small" type="primary" plain :loading="ccSeeding" @click="seedCloseCheck()">生成检查清单</el-button>
          </div>
          <el-alert v-if="!ccLoading && !closeCheck.length" type="warning" :closable="false" show-icon
            title="该期间尚未建立月结检查清单——直接月结会被检查闸拦下。先点「生成检查清单」建立 8 项，再由各负责岗位逐项办结。" />
          <el-table v-if="closeCheck.length" :data="closeCheck" size="small" border stripe v-loading="ccLoading">
            <el-table-column label="办结" width="70" align="center">
              <template #default="{ row }">
                <el-checkbox :model-value="isCcDone(row)" :disabled="ccTicking" @change="(v) => tickCloseCheck(row, v)" />
              </template>
            </el-table-column>
            <el-table-column prop="itemName" label="检查项" min-width="220" show-overflow-tooltip />
            <el-table-column label="负责岗位" width="110">
              <template #default="{ row }">{{ ccOwnerLabel(row.ownerRole) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="isCcDone(row) ? 'success' : 'info'" size="small">{{ isCcDone(row) ? '已办结' : '待办结' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="办结人 / 办结时间" width="210">
              <template #default="{ row }">
                {{ isCcDone(row) ? `${row.checker || '—'} / ${formatDateTime(row.checkTime)}` : '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="monthData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="closeNo" label="月结单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column label="借方合计" width="130" align="right"><template #default="{ row }">{{ fmt(row.debitTotal) }}</template></el-table-column>
          <el-table-column label="贷方合计" width="130" align="right"><template #default="{ row }">{{ fmt(row.creditTotal) }}</template></el-table-column>
          <el-table-column label="差异" width="110" align="right"><template #default="{ row }">{{ fmt(row.diff) }}</template></el-table-column>
          <el-table-column label="平衡" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.balanced ? 'success' : 'danger'" size="small">{{ row.balanced ? '平衡' : '不平' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- D-DEC-191（证据 12-31:169）：gl_month_close 表上没有 proc_inst_id/approve_time/audit_time 三列
               （加列属 DDL 裁决，注释态 SQL 见 GlMonthClose 实体类尾），后端 page() 已按
               wf_instance(biz_type='GL_MONTH_CLOSE', biz_id=月结单ID) ＋ wf_task_history 末节点留痕
               做读侧富化，这里把两个方向摊开：这张月结单挂在哪条审批流上、谁在何时批的。 -->
          <el-table-column label="审批流" width="150" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.wfProcInstId">{{ wfFlowLabel(row.wfFlowStatus) }} · {{ shortProc(row.wfProcInstId) }}</span>
              <span v-else style="color:#c0c4cc">无审批流</span>
            </template>
          </el-table-column>
          <el-table-column label="办结/末节复核" width="180">
            <template #default="{ row }">
              <span v-if="row.approveTime || row.auditTime">
                <!-- R5-D-162：原直插 LocalDateTime ISO 串（2026-10-02T22:40:18），统一走 formatDateTime 全站格式 -->
                {{ row.approveTime ? formatDateTime(row.approveTime) : '在途' }} / {{ row.auditTime ? formatDateTime(row.auditTime) : '—' }}
                <span v-if="row.auditUserName" style="color:#909399">（{{ row.auditUserName }}）</span>
              </span>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="resultMsg" label="说明" min-width="200" show-overflow-tooltip  sortable />
          <!-- R5-D-167：月结单挂 GL_MONTH_CLOSE 域追溯（TRACE_BIZ_BY_TAB 既有定义，零后端改动） -->
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerMonth" @change="loadMonthClose" />
      </el-tab-pane>

      <!-- 合并报表 -->
      <el-tab-pane label="合并报表" name="consolidate">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center; flex-wrap:wrap">
          <el-input v-model="consForm.period" placeholder="期间 yyyyMM" style="width:120px" />
          <el-button type="success" :disabled="consPeriodClosed" @click="doConsolidate">汇总合并（提交审批）</el-button>
          <span v-if="consPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
          <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
          <span style="color:#909399; font-size:12px">合并底稿汇总抵消后发起合并报表审批流</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="consolidateData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="reportNo" label="合并单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <!-- gl_consolidate 无金额列（底稿在 raw_rows JSON 里），缺值一律显示「—」而不是 0.00，避免把空读成零 -->
          <el-table-column label="合并金额" width="130" align="right"><template #default="{ row }">{{ row.amount == null ? '—' : fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- R5-D-164：原「说明」列 prop="resultMsg" 而 gl_consolidate 列面无该列（恒空）⇒ 已删；
               底稿明细在 raw_rows JSON（表侧无说明语义）。monthclose 页签的说明列保留（gl_month_close 实有 result_msg）。 -->
          <!-- R5-D-167：合并单挂 GL_MERGE 域追溯（TRACE_BIZ_BY_TAB 既有定义） -->
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerConsolidate" @change="loadConsolidate" />
      </el-tab-pane>

      <!-- GL主数据维护 -->
      <el-tab-pane label="GL主数据" name="master">
        <el-form ref="masterFormRef" :model="masterForm" :rules="masterRules" inline style="margin-bottom: 10px">
          <el-form-item label="期间" prop="period">
            <el-input v-model="masterForm.period" placeholder="期间 yyyyMM" style="width:120px" />
          </el-form-item>
          <!-- 主数据类型/动作下拉恒有默认值且不可清空，不会出现未填态，故不加红星 -->
          <el-form-item label="主数据">
            <el-select v-model="masterForm.type" placeholder="主数据" style="width:130px">
              <el-option label="科目" value="SUBJECT" />
              <el-option label="成本中心" value="COST_CENTER" />
              <el-option label="银行" value="BANK" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="动作">
            <el-select v-model="masterForm.action" placeholder="动作" style="width:100px">
              <el-option label="新增" value="ADD" />
              <el-option label="变更" value="MODIFY" />
              <el-option label="停用" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="科目编码" prop="subjectCode">
            <el-input v-model="masterForm.subjectCode" placeholder="科目编码" style="width:110px" />
          </el-form-item>
          <el-form-item label="科目名称" prop="subjectName">
            <el-input v-model="masterForm.subjectName" placeholder="科目名称" style="width:130px" />
          </el-form-item>
          <el-form-item>
            <el-button type="success" :disabled="masterPeriodClosed" @click="doMaster">提交审批</el-button>
            <span v-if="masterPeriodClosed" class="period-note">所选期间已结账（CLOSED），禁止提交</span>
            <span v-else-if="!canLoadPeriod" class="period-note">当前账号不可读会计期间，无法校验期间状态，提交以服务端校验为准</span>
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="exportCsv">导出 CSV</el-button>
            <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
          </el-form-item>
        </el-form>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="masterData" size="small" border stripe v-else v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="reqNo" label="申请单号" width="150"  sortable />
          <el-table-column prop="period" label="期间(yyyyMM)" width="110"  sortable />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ masterTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="动作" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.action === 'ADD' ? 'success' : row.action === 'MODIFY' ? 'warning' : 'danger'" size="small">{{ masterActionLabel(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="subjectCode" label="科目编码" width="110"  sortable />
          <el-table-column prop="subjectName" label="科目名称" min-width="130" show-overflow-tooltip  sortable />
          <el-table-column prop="description" label="变更说明" min-width="160" show-overflow-tooltip  sortable />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- R5-D-153 -->
          <el-table-column prop="voucherNo" label="记账凭证号" min-width="140" show-overflow-tooltip  sortable>
            <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
          </el-table-column>
          <!-- R5-D-167：主数据申请单挂 GL_SHARED_MASTER 域追溯（TRACE_BIZ_BY_TAB 既有定义） -->
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerMaster" @change="loadMaster" />
      </el-tab-pane>
    </el-tabs>

    <!-- 损益结转向导 -->
    <el-dialog v-model="pcVisible" title="损益结转" width="420px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
        title="将所选期间全部损益类科目发生额结转至本年利润，并生成结转凭证；重复执行同一期间会被拒绝。" />
      <el-form ref="pcFormRef" :model="pcModel" :rules="pcRules" label-width="90px">
        <el-form-item label="结转期间" prop="pcPeriod">
          <el-date-picker v-model="pcPeriod" type="month" value-format="YYYYMM" placeholder="选择期间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pcVisible = false">取消</el-button>
        <el-button type="primary" :loading="pcLoading" @click="doProfitCarry">确认结转</el-button>
      </template>
    </el-dialog>
    <!-- R5-D-154：落账挂接核查（只读）——审批流 / 凭证 / 总账分录 / 缴款流逐条对照，未落账条数与金额如实报出 -->
    <el-dialog v-model="lcVisible" title="税金计提落账挂接核查" width="920px" destroy-on-close>
      <el-alert :type="lcSummary.missingEntryCount || lcSummary.missingVoucherCount || lcSummary.missingFlowCount ? 'warning' : 'success'"
        :closable="false" show-icon style="margin-bottom: 10px"
        :title="`期间 ${lcPeriod}：共 ${lcSummary.total || 0} 条计提；无审批流程 ${lcSummary.missingFlowCount || 0} 条（¥${fmt(lcSummary.missingFlowAmount)}）、未出凭证 ${lcSummary.missingVoucherCount || 0} 条（¥${fmt(lcSummary.missingVoucherAmount)}）、已入账但总账无分录 ${lcSummary.missingEntryCount || 0} 条（¥${fmt(lcSummary.missingEntryAmount)}）`" />
      <el-table :data="lcRows" size="small" border stripe v-loading="lcLoading" empty-text="暂无数据">
        <el-table-column prop="provisionNo" label="计提单号" width="150" />
        <el-table-column label="税种" width="100"><template #default="{ row }">{{ taxTypeLabel(row.taxType) }}</template></el-table-column>
        <el-table-column label="税额" width="120" align="right"><template #default="{ row }">{{ fmt(row.taxAmount) }}</template></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="stTagType(row.status)" size="small">{{ stLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="审批流程" width="100" align="center">
          <template #default="{ row }"><el-tag :type="row.hasApproveFlow ? 'success' : 'danger'" size="small">{{ row.hasApproveFlow ? '已挂' : '未挂' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="凭证" width="90" align="center">
          <template #default="{ row }"><el-tag :type="row.hasVoucher ? 'success' : 'danger'" size="small">{{ row.hasVoucher ? '已出' : '未出' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="总账分录" width="100" align="center">
          <template #default="{ row }"><el-tag :type="(row.entryCount || 0) > 0 ? 'success' : 'danger'" size="small">{{ (row.entryCount || 0) > 0 ? row.entryCount + ' 行' : '0 行' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="记账凭证号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.voucherNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="缴款流程" width="100" align="center">
          <template #default="{ row }"><el-tag :type="row.hasPayFlow ? 'success' : 'info'" size="small">{{ row.hasPayFlow ? '已挂' : '无' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span style="color:#909399; font-size:12px; margin-right:12px">本核查为只读查询，不补记、不改账；未落账部分请按计提单号复核后重新计提，或联系管理员人工处理。</span>
        <el-button @click="lcVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <CoachMark ref="glSpecialCm" :guide-key="_guideKey" :selector="'.cm-target-gl'" title="总账特殊业务" text="总账特殊业务页面处理期末调账、结转损益等特殊凭证业务。" />
  </div>
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { taxTypeLabel, interFlagLabel, masterTypeLabel, masterActionLabel } from '@/constants/enums'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { readBillDeepLink } from '../../utils/bizEntry'
import { ElMessage, ElMessageBox } from 'element-plus'
import { glexApi, glApi, orgApi, mdApi, masterApi, periodApi } from '../../api'
// R5-D-215 / R5-D-154：月结检查闸三端点与计提落账挂接核查端点未收进 api.js（该文件为多页共享面，本轮不改），
// 走本仓既有的复用路径——request.js 再导出的同一个 http 实例（baseURL /api、鉴权头、响应解包与 api.js 完全一致），
// TaxDiffTab/ArchiveBoard/CorpPay 等页即用此法。
import { http } from '../../request'
import { formatDateTime } from '../../utils/format'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useTablePager } from '../../composables/useTablePager'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import TablePager from '../../components/common/TablePager.vue'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
const glSpecialCm = ref(null)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
/**
 * 页签 → 单据域（D-DEC-127/139/148 的「追溯」半）：追溯抽屉按 (bizType, bizId) 锚 wf_instance，
 * 缺了域就只能让服务端拿裸主键去猜单据（那正是 152 把别人的报销单渲染出来的路径）。
 * GL 专项一张表可能挂多个流程域 ⇒ 单靠页签定不下来的（journal 按 entry_type 分 5 域、
 * salary 的计提/发放共用同一行）见 {@link traceBizOf}。
 */
const TRACE_BIZ_BY_TAB = {
  tax: 'GL_TAX_PROV',
  salary: 'GL_SALARY_PROV',
  inter: 'GL_OFFSET',
  journal: 'GL_MANUAL_VOUCHER',
  monthclose: 'GL_MONTH_CLOSE',
  consolidate: 'GL_MERGE',
  master: 'GL_SHARED_MASTER',
}
// gl_journal_entry 是「计提/待摊/手工/其他收款/盘点差异」五合一（GlJournalService:125-135
// 的 entry_type → bizType 映射），同一张表上跑五种流程域 ⇒ 按行的 entryType 声明域。
const TRACE_BIZ_BY_ENTRY_TYPE = {
  ACCRUAL: 'GL_ACCRUAL',
  DEFER: 'GL_DEFER',
  MANUAL: 'GL_MANUAL_VOUCHER',
  OTHER_RECEIPT: 'GL_OTHER_RECEIPT',
  CHECK_DIFF: 'GL_CHECK_DIFF',
}
function traceBizOf(row) {
  if (tab.value === 'journal') return TRACE_BIZ_BY_ENTRY_TYPE[row?.entryType] || TRACE_BIZ_BY_TAB.journal
  // 薪酬：计提链与发放链共用 gl_salary_run 同一行、且该行无 proc_inst_id（D-DEC-139/239 家族）。
  // 业务页只有这一行的视角 ⇒ 如实声明计提域；发放链请从待办「追溯」进入（那里 row.billType 是真值）。
  return TRACE_BIZ_BY_TAB[tab.value] || null
}
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = traceBizOf(row)
  traceVisible.value = true
}

/**
 * 「去处理」带进来的本单上下文（D-DEC-127/139/148 的落地页半：路径修对了但没有 billNo
 * → 列表照旧全量第一页，办理人依然看不到被审对象）。GL 各页签的后端 page 接口都支持
 * keyword（单号/凭证号 LIKE），所以把 ?billNo= 直接当 keyword 用即可命中本单。
 * billId 无法用于定位——GL 专项各域均无按主键取详情的端点（见报告「需后端窗口」条）。
 */
const deepLink = readBillDeepLink(useRoute())
const deepKeyword = ref(deepLink.billNo)
function clearDeepLink() { deepKeyword.value = ''; reload() }

const selRows = ref([])
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
/**
 * D-DEC-140：税金计提期间与「当前开账期间」同源。
 * 原先默认值是写死的 '202609'，与页面列表展示的期间、与账套实际开账的期间都没有关系：
 * 用户没动选择器就直接点「计提税金」，三条计提会整体落到那个历史期间里去。
 * 现在默认值改为取 md_period 里第一个未结账期间（yyyyMM，与列表「期间(yyyyMM)」列同格式），
 * 无 md:period:list 权限或接口异常时退回真实时钟当月，并在选择器旁如实标注来源。
 */
const taxPeriod = ref('')
const openPeriod = ref('')
const canLoadPeriod = hasPerm('md:period:list')
// 默认期间的来源照实标注：取到开账期间 / 期间表全已关退回最后一期 / 无权限或取数失败退回系统当月
const periodSource = ref('系统当月')
const tab = ref(props.tab || 'tax')
const loading = ref(false)
const depts = ref([])
const companies = ref([])
const subjects = ref([])
const taxs = ref([])
const salarys = ref([])
const inters = ref([])
const journals = ref([])
const monthCloses = ref([])
const masters = ref([])
const consolidates = ref([])

// P0-1: GL 专项各 tab 列表本地分页（后端未分页，前端切片）。每个 tab 独立一个 pager
const pagerTax = useTablePager({ mode: 'server', storageKey: 'gl_special_tax' })
const pagerSalary = useTablePager({ mode: 'server', storageKey: 'gl_special_salary' })
const pagerInter = useTablePager({ mode: 'server', storageKey: 'gl_special_inter' })
const pagerJournal = useTablePager({ mode: 'server', storageKey: 'gl_special_journal' })
const pagerMonth = useTablePager({ mode: 'server', storageKey: 'gl_special_monthclose' })
const pagerMaster = useTablePager({ mode: 'server', storageKey: 'gl_special_master' })
const pagerConsolidate = useTablePager({ mode: 'server', storageKey: 'gl_special_consolidate' })

// ElTable 需要解包后的数组（pager 对象为普通对象，模板不会自动解包嵌套 ref）
const taxData = computed(() => pagerTax.pagedData.value)
const salaryData = computed(() => pagerSalary.pagedData.value)
const interData = computed(() => pagerInter.pagedData.value)
const journalData = computed(() => pagerJournal.pagedData.value)
const monthData = computed(() => pagerMonth.pagedData.value)
const masterData = computed(() => pagerMaster.pagedData.value)
const consolidateData = computed(() => pagerConsolidate.pagedData.value)

// 部门/公司/科目名称查找
function deptName(id) { const d = depts.value.find((x) => x.id === id); return d ? d.name : (id || '—') }
function companyName(id) { const c = companies.value.find((x) => x.id === id); return c ? c.name : (id || '—') }
// R5-D-157：业务表历史数据里「科目」列混存 id 与编码（journal 侧存的是编码串），原实现只按 id 查、
// 查不到直接回吐裸值。现按 id → 编码两腿查找，都取不到才如实报「科目已失效」——界面不再出现
// 222105 这类无上下文的裸码。历史 code 串一次性映射回 id 属数据治理，见报告转人工清单。
function subjectName(id) {
  const key = id == null ? '' : String(id)
  if (!key) return '—'
  const s = subjects.value.find((x) => String(x.id) === key)
  if (s) return s.subjectName || s.name || ''
  const byCode = subjects.value.find((x) => String(x.code ?? '') === key)
  if (byCode) return byCode.subjectName || byCode.name || ''
  return `科目已失效（id ${id}）`
}

// 状态中文映射
const statusMap = STATUS_LABEL
function stLabel(s) { return statusMap[s] || s }
function stTagType(s) { return STATUS_TYPE[s] || 'info' }

// D-DEC-191：GL_MONTH_CLOSE 审批流的读侧富化展示（实例号/流程状态/末节点复核人）
function wfFlowLabel(s) {
  return ({ RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已作废', TERMINATED: '已终止' })[s] || s || '—'
}
function shortProc(p) { return p && p.length > 8 ? p.slice(0, 8) : (p || '—') }
// R5-D-169：六个页签的期间默认值一律留空，由 loadOpenPeriod() 用「当前打开期间」填（与税金页签同源）。
// 原写死 '202609' 且该期在会计期间表已结账——用户不动输入框就直接提交，业务会整体落到禁入期间。
const salaryForm = ref({ period: '', deptId: 0, empCount: 1 })
const interForm = ref({ period: '' })
const journalForm = ref({ period: '', entryType: 'ACCRUAL', amount: 10000, summary: '' })
const mcForm = ref({ period: '', companyId: null })
const consForm = ref({ period: '' })
const masterForm = ref({ period: '', type: 'SUBJECT', action: 'ADD', subjectCode: '', subjectName: '', description: '' })
const masterFormRef = ref(null)
// 与后端 GlSharedMasterService.submit 同口径：期间/编码必填，停用只需编码（名称可空）
const masterRules = computed(() => ({
  period: [...commonRules.required('请填写会计期间'), { pattern: /^\d{6}$/, message: '期间格式应为 yyyyMM', trigger: 'blur' }],
  subjectCode: commonRules.required('请填写主数据编码（科目/成本中心/银行编码）'),
  subjectName: masterForm.value.action === 'DELETE' ? [] : commonRules.required('请填写主数据名称'),
}))

// R5-D-169：已结账（CLOSED）期间禁用提交。期间状态复用 loadOpenPeriod() 那一次 md_period 读数（不新发请求、
// 不新造取期逻辑，与税金页签同一来源）；读不到期间表时集合为空 ⇒ 提交钮维持旧行为，只出显式提示，拦截以服务端为准。
const closedPeriods = ref([])
function isClosedPeriod(p) {
  const s = String(p ?? '').trim()
  return !!s && closedPeriods.value.includes(s)
}
const salaryPeriodClosed = computed(() => isClosedPeriod(salaryForm.value.period))
const interPeriodClosed = computed(() => isClosedPeriod(interForm.value.period))
const journalPeriodClosed = computed(() => isClosedPeriod(journalForm.value.period))
const mcPeriodClosed = computed(() => isClosedPeriod(mcForm.value.period))
const consPeriodClosed = computed(() => isClosedPeriod(consForm.value.period))
const masterPeriodClosed = computed(() => isClosedPeriod(masterForm.value.period))

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// 期间类必填项闸门：不得用 || '202609' 兜底代填（会让用户以为提交的是当前期间，实际写进历史期间）。
// 返回归一化后的 yyyyMM；为空或格式不符时提示并返回 null，调用方据此中止提交。
function periodOrWarn(raw) {
  const p = String(raw ?? '').trim()
  if (!p) { ElMessage.warning('请先填写会计期间（yyyyMM）'); return null }
  if (!/^\d{6}$/.test(p)) { ElMessage.warning('会计期间格式应为 6 位 yyyyMM'); return null }
  return p
}

/**
 * R5-D-166：落账／关账类写动作的统一二次确认（除计提外，本页写按钮原先一键即发，误点即成账）。
 * 期间／金额／类型等关键要素在框内回显（ElMessageBox 不渲染换行，故单行以「；」拼接）；
 * 用户取消返回 false，调用方立即 return，不产生任何写入。与 R5-D-152 的确认口径同族。
 */
async function confirmWrite(title, lines) {
  try {
    await ElMessageBox.confirm(lines.filter(Boolean).join('；'), title,
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消', closeOnClickModal: false })
    return true
  } catch { return false }
}

// R5-D-118：列表查询带上「税金期间」选择值（为空不传，服务端按原口径返回；后端 GlTaxProvisionService.page
// 以 StringUtils.hasText(period) 判存，空串与 null 等价不附加过滤）。
function loadTax() {
  try { return pagerTax.load(async ({ pageNum, pageSize }) => { const d = await glexApi.taxPage(pageNum, pageSize, String(taxPeriod.value ?? '').trim(), '', deepKeyword.value); taxs.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
function loadSalary() {
  try { return pagerSalary.load(async ({ pageNum, pageSize }) => { const d = await glexApi.salaryPage(pageNum, pageSize, '', '', deepKeyword.value); salarys.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
function loadInter() {
  try { return pagerInter.load(async ({ pageNum, pageSize }) => { const d = await glexApi.interPage(pageNum, pageSize, '', '', deepKeyword.value); inters.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}

let provisioning = false
async function doProvision() {
  const period = periodOrWarn(taxPeriod.value)
  if (!period) return
  if (provisioning) return
  // D-DEC-140：这一击不是一句查询——一次写 3 条计提记录并起 1 条 GL_TAX_PROV 审批流，
  // 界面侧没有冲销出口，属不可回滚的业务事实，故必须先二次确认，
  // 并把「落到哪个期间、生成几条、会起流程」在框内讲清（原先无任何确认，误点即成账）。
  try {
    // R5-D-119：出处句必须与「所选期间」同源——原句直接引用 periodSource/openPeriod 的默认值口径，
    // 与用户改选后的 period 各说各话（如默认 202610 而所选 202612），框内成账口径自相矛盾会误导确认。
    // 三态：open 不可读（显式免责，不硬禁用）/ 与所选一致 / 与所选不一致（确认按钮点名所选期间）。
    const sel = period
    const open = String(openPeriod.value ?? '').trim()
    const sourceNote = !open
      ? `当前账号不可读会计期间，无法与系统开账期间比对；本次计提期间以所选 ${period} 为准，请再次确认。`
      : (open === sel
        ? `所选期间与系统当前开账期间（${openPeriod.value}）一致。`
        : `所选期间 ${sel} 与系统当前开账期间（${openPeriod.value}）不一致，请确认无误后再继续。`)
    await ElMessageBox.confirm(
      `确认对期间 ${period} 执行税金计提？本次将一次生成 3 条计提记录（增值税 / 附加税 / 所得税）`
      + `并同步发起税金计提审批流，记录生成后本页面不提供冲销入口。`
      + sourceNote,
      '确认计提税金',
      { type: 'warning', confirmButtonText: open && open !== sel ? `确认在 ${sel} 计提` : '确认计提', cancelButtonText: '取消' })
  } catch { return } // 用户取消：不产生任何写入
  provisioning = true
  try {
    const rows = await glexApi.taxProvision(period)
    ElMessage.success('税金计提完成 ' + (rows ? rows.length : 0) + ' 条，已发起税金计提审批')
    loadTax()
  } catch (e) { ElMessage.error('税金计提失败：' + (e?.message || '未知错误')) } finally { provisioning = false }
}
// D-DEC-440：原「缴纳」入口（POST /glex/tax-provision/pay → GL_TAX_PAY）不再生成任何付款动作，
// 缴款事实由对公付款通道产生 ⇒ 入口与调用腿一并摘掉，只留上方那句通道指引。
async function doCalculate() {
  const period = periodOrWarn(salaryForm.value.period)
  if (!period) return
  const deptId = salaryForm.value.deptId || null
  const empCount = salaryForm.value.empCount || 1
  // R5-D-166：落账类写动作两步确认＋要素回显
  if (!(await confirmWrite('确认薪酬核算', [
    `期间 ${period}；部门 ${deptId ? deptName(deptId) : '未选'}；人数 ${empCount}`,
    '将生成应付工资并发起工资计提审批',
  ]))) return
  try {
    const run = await glexApi.salaryCalculate(period, deptId, empCount)
    ElMessage.success('薪酬核算完成，应付 ¥' + fmt(run.totalAmount) + '，已发起工资计提审批')
    loadSalary()
  } catch (e) { ElMessage.error(e.message) }
}
async function doPay(row) { try { await glexApi.salaryPay(row.id); ElMessage.success('已发放'); loadSalary() } catch (e) { ElMessage.error(e.message) } }

async function doGenerate() {
  const period = periodOrWarn(interForm.value.period)
  if (!period) return
  if (!(await confirmWrite('确认生成抵消', [
    `期间 ${period}`,
    '将按内部往来余额自动生成成对抵消分录并同步发起内部往来抵消审批（无余额时生成 0 条、不发起审批）',
  ]))) return
  try {
    const rows = await glexApi.interGenerate(period)
    // 整改(20260918): 生成 0 条时不应提示"已发起审批"，避免与实际不一致
    if (!rows || rows.length === 0) {
      ElMessage.warning('该期间无内部往来余额，抵消分录生成 0 条，未发起审批')
    } else {
      ElMessage.success('抵消分录生成 ' + rows.length + ' 条，已发起内部往来抵消审批')
    }
    loadInter()
  } catch (e) { ElMessage.error(e.message) }
}
async function doPost(row) { try { await glexApi.interPost(row.id); ElMessage.success('已抵消记账'); loadInter() } catch (e) { ElMessage.error(e.message) } }

/**
 * X-52（D-DEC-294）内部往来对冲手工成对录入。
 * 权限码取后端真值：GlInterOffsetController.create 的 @RequiresPerm("gl:inter:offset")（与"生成抵消""抵消记账"同一码）。
 * 必填口径与后端一致、不另立规则：GlInterOffsetService.create:155-166 只校验
 *   ① 期间非空（本页期间统一 yyyyMM，沿用本页 periodOrWarn/masterRules 的同一格式判定）
 *   ② 两条分录金额 > 0 且成对相等 —— 表单只有一个金额字段，镜像两条分录共用它，结构上必然相等
 *   ③ 方向 ∈ {RECEIVABLE, PAYABLE} —— 由表单固定，不由用户选
 * 来账/往账公司后端未做非空校验（gl_inter_offset 两列可空），故不设必填，只作可选标注。
 */
const canManualPair = hasPerm('gl:inter:offset')
const manualVisible = ref(false)
const manualSubmitting = ref(false)
const manualFormRef = ref(null)
const manualForm = ref({ period: '', receivableCompanyId: null, payableCompanyId: null, amount: null })
const manualRules = {
  period: [...commonRules.required('请填写会计期间'), { pattern: /^\d{6}$/, message: '期间格式应为 yyyyMM', trigger: 'blur' }],
  amount: commonRules.amount('抵消金额必须大于 0'),
}
function toggleManualPair() {
  // 首次展开把期间预填为当前开账期间（与税金计提同一默认口径），不预填金额，也不代为提交
  if (!manualVisible.value && !manualForm.value.period) manualForm.value.period = openPeriod.value || ''
  manualVisible.value = !manualVisible.value
}
async function doManualPair() {
  if (!(await validateForms([manualFormRef]))) return
  if (manualSubmitting.value) return
  const period = String(manualForm.value.period || '').trim()
  const amount = Number(manualForm.value.amount)
  const receivableCompanyId = manualForm.value.receivableCompanyId || null
  const payableCompanyId = manualForm.value.payableCompanyId || null
  // R5-D-159：同主体自我抵消拦在界面侧（后端 create 同批加 assertDistinctCompanies 兜底）——
  // 两条镜像分录公司相同＝对自身抵消，属上下文矛盾的废单（存量已有 3 条同主体记录，含 0 元行）。
  // 公司留空（双 null）维持原口径不受影响，仍交后端按原逻辑处理。
  if (receivableCompanyId && payableCompanyId && String(receivableCompanyId) === String(payableCompanyId)) {
    ElMessage.warning('应收方与应付方不能为同一家公司（自我抵消）；请修改后再提交')
    return
  }
  // 顺序即契约：pair[0]=来账(应收)、pair[1]=往账(应付)（GlInterOffsetController:45-50）
  const pair = [
    { period, fromCompanyId: receivableCompanyId, toCompanyId: payableCompanyId, interFlag: 'RECEIVABLE', amount },
    { period, fromCompanyId: payableCompanyId, toCompanyId: receivableCompanyId, interFlag: 'PAYABLE', amount },
  ]
  // R5-D-166：写动作两步确认＋要素回显
  if (!(await confirmWrite('确认提交成对分录', [
    `期间 ${period}；金额 ¥${fmt(amount)}`,
    `来账（应收）${receivableCompanyId ? companyName(receivableCompanyId) : '未指定'} / 往账（应付）${payableCompanyId ? companyName(payableCompanyId) : '未指定'}`,
    '将生成 2 条草稿抵消分录并发起内部往来抵消审批',
  ]))) return
  manualSubmitting.value = true
  try {
    const rows = await glexApi.interCreate(pair)
    ElMessage.success('手工成对录入 ' + (rows ? rows.length : 0) + ' 条（草稿），已发起内部往来抵消审批')
    manualVisible.value = false
    loadInter()
  } catch (e) { ElMessage.error(e.message) } finally { manualSubmitting.value = false }
}

function typeName(t) {
  return { ACCRUAL: '费用预提', DEFER: '费用待摊', MANUAL: '手工凭证', OTHER_RECEIPT: '其他收款' }[t] || t
}

function loadJournal() {
  try { return pagerJournal.load(async ({ pageNum, pageSize }) => { const d = await glexApi.journalPage(pageNum, pageSize, '', '', deepKeyword.value); journals.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
function loadMonthClose() {
  try { return pagerMonth.load(async ({ pageNum, pageSize }) => { const d = await glexApi.monthClosePage(pageNum, pageSize, '', deepKeyword.value); monthCloses.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
function loadMaster() {
  try { return pagerMaster.load(async ({ pageNum, pageSize }) => { const d = await glexApi.sharedMasterPage(pageNum, pageSize, '', '', deepKeyword.value); masters.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
async function doJournal() {
  const period = periodOrWarn(journalForm.value.period)
  if (!period) return
  const amount = Number(journalForm.value.amount)
  if (!isFinite(amount) || amount <= 0) { ElMessage.warning('请填写记账金额（须大于 0）'); return }
  // R5-D-166：写动作两步确认＋要素回显
  if (!(await confirmWrite('确认记账', [
    `期间 ${period}；类型 ${typeName(journalForm.value.entryType)}；金额 ¥${fmt(amount)}`,
    journalForm.value.summary ? `摘要 ${journalForm.value.summary}` : '无摘要',
    '将生成记账凭证并发起对应审批',
  ]))) return
  try {
    const e = await glexApi.journalGenerate(period, journalForm.value.entryType,
      journalForm.value.summary, amount)
    ElMessage.success(typeName(e.entryType) + '记账完成 ¥' + fmt(e.amount) + '，已发起审批')
    loadJournal()
  } catch (e) { ElMessage.error(e.message) }
}

/**
 * R5-D-167：一般记账冲销入口。后端 GlJournalController POST /{id}/reverse（GlJournalService.reverse）
 * 仅放行 POSTED（→ REVERSED，红字反向凭证作废原凭证），原因必填并写入审计留痕。
 * 权限码暂复用 gl:journal:generate（独立码 gl:journal:reverse 待 sys_menu 种子扩码，见报告裁决项）。
 */
async function doJournalReverse(row) {
  if (!row?.id) return
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认冲销记账单 ${row.entryNo || row.id}（金额 ¥${fmt(row.amount)}）？冲销后单据置为 REVERSED 并生成红字反向凭证，请填写冲销原因（必填、留痕）。`,
      '冲销记账',
      {
        inputPlaceholder: '冲销原因（必填）',
        inputValidator: (v) => !!String(v || '').trim() || '请填写冲销原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        type: 'warning',
      },
    )
    reason = String(value || '').trim()
  } catch { return } // 用户取消：不产生任何写入
  try {
    await glexApi.journalReverse(row.id, reason)
    ElMessage.success('记账单已冲销（REVERSED），红字反向凭证已生成')
    loadJournal()
  } catch (e) { ElMessage.error(e.message) }
}
async function doMonthClose() {
  const period = periodOrWarn(mcForm.value.period)
  if (!period) return
  // R5-D-215：月结前先过检查闸——清单为空则先引导「生成检查清单」后中止，有未办结项则列出未完成项并中止。
  // 服务端 GlMonthCloseService 的闸门硬校验一字未改，这里不放宽、不绕过，只是把拦阻原因提前讲清、把办结入口给到岗位。
  if (canCloseCheck) {
    let list = []
    try { list = await fetchCloseCheck() } catch (e) { ElMessage.error(e.message); return }
    closeCheck.value = list
    if (!list.length) {
      await seedCloseCheck()
      return
    }
    const pending = list.filter((r) => !isCcDone(r))
    if (pending.length) {
      ElMessageBox.alert(
        `期间 ${period} 的月结检查闸尚未通过：还有 ${pending.length} 项未办结——${pending.map((r) => r.itemName).join('、')}。`
        + `请通知对应负责岗位在本页「月结检查闸清单」逐项办结后再执行月结。`,
        '无法月结', { type: 'warning', confirmButtonText: '知道了' })
      return
    }
  }
  // R5-D-166：写动作两步确认＋要素回显（检查闸通过后、提交前）
  if (!(await confirmWrite('确认发起月结', [
    `期间 ${period}；公司 ${mcCompLabel.value}`,
    '将执行试算平衡并发起关账审批；末节点审批通过后自动关账，期间在此之前保持开账',
  ]))) return
  try {
    const e = await glexApi.monthClose(period, mcForm.value.companyId)
    // D-DEC-189 的前端半（登记归 189 连带，不占新号）：本按钮只做「试算平衡 + 发起 GL_MONTH_CLOSE 审批」，
    // 关账时点在末节点审批通过之后（服务端 GlMonthCloseService 发起态已置 RUNNING，resultMsg 也写明
    // 「期间尚未关账——末节点通过后自动关账」）。原文案不分状态一律报「月结完成」，等于在提单同秒向
    // 总账会计宣称已关账，与先审后关口径矛盾，会让人据此停止记账、并误以为审批只是事后追认。
    const st = String(e?.status || '').toUpperCase()
    if (st === 'CLOSED') ElMessage.success('月结完成（期间已关账）：' + (e.resultMsg || ''))
    else if (st === 'RUNNING') ElMessage.success('月结审批已发起，期间尚未关账（末节点通过后自动关账）：' + (e.resultMsg || ''))
    else if (st === 'DRAFT' || e?.balanced === false) ElMessage.warning('试算借贷不平，本次未进入月结审批、期间保持开账：' + (e.resultMsg || ''))
    else ElMessage.success(`月结已受理（当前状态：${stLabel(st) || '—'}），是否关账以会计期间状态为准：` + (e.resultMsg || ''))
    loadMonthClose()
  } catch (e) { ElMessage.error(e.message) }
}

/**
 * R5-D-215：月结检查闸清单（生成清单 → 逐项完成 → 执行月结）。
 * 后端三端点已就位（GlPeriodOpsController，权限码与月结同码 gl:monthclose:close）：
 *   GET  /api/tenant/gl/close-check/list?period&companyId  当期清单
 *   POST /api/tenant/gl/close-check/seed?period&companyId  生成 8 项（同名项已存在则跳过，可重复点）
 *   POST /api/tenant/gl/close-check/check  body={id,done}  勾项 / 回退
 * 公司口径必须与月结一致（未选公司即后端默认 1），三端点与「月结」按钮传同一个值——
 * 否则清单建在 A 公司、闸门查的是 B 公司，检查闸永远过不去（GlMonthCloseService:248/285-300）。
 */
const canCloseCheck = hasPerm('gl:monthclose:close')
const closeCheck = ref([])
const ccLoading = ref(false)
const ccSeeding = ref(false)
const ccTicking = ref(false)
// 清单跟随月结表单的期间与公司；期间未满 6 位时不发请求（与 periodOrWarn 同口径）
const ccQuery = computed(() => {
  const p = String(mcForm.value.period ?? '').trim()
  if (!/^\d{6}$/.test(p)) return null
  return mcForm.value.companyId ? { period: p, companyId: mcForm.value.companyId } : { period: p }
})
const checkPeriodLabel = computed(() => (ccQuery.value ? ccQuery.value.period : '未填期间'))
const mcCompLabel = computed(() => {
  const id = mcForm.value.companyId || 1
  const c = companies.value.find((x) => String(x.id) === String(id))
  return c ? c.name : '本公司'
})
function isCcDone(row) { return String(row?.status || '').toUpperCase() === 'DONE' }
const ccDone = computed(() => closeCheck.value.filter((r) => isCcDone(r)).length)
const ccPercent = computed(() => (closeCheck.value.length ? Math.round((ccDone.value * 100) / closeCheck.value.length) : 0))
// 负责岗位显示名：取会计期间检查项的责任岗位编码对应的岗位名称（岗位名称与本仓 sys_role 种子一致），
// 界面只出中文岗位名，不把岗位编码/权限码这类内部标识摆给业务用户看。
const CC_OWNER_LABEL = { FIN_GL: '总账会计', CASHIER: '出纳', FIN_AR: '应收会计', FA_ASSET_ACCOUNT: '资产核算岗' }
function ccOwnerLabel(code) { return CC_OWNER_LABEL[String(code || '').trim()] || '未指定岗位' }

async function fetchCloseCheck(q) {
  const query = q || ccQuery.value
  if (!query) return []
  const res = await http.get('/tenant/gl/close-check/list', { params: query, silent: true })
  if (Array.isArray(res)) return res
  return res?.list || res?.records || []
}
async function loadCloseCheck() {
  if (!canCloseCheck || !ccQuery.value) { closeCheck.value = []; return }
  ccLoading.value = true
  try { closeCheck.value = await fetchCloseCheck() } catch (e) { ElMessage.error(e.message) } finally { ccLoading.value = false }
}
async function seedCloseCheck(q) {
  const query = q || ccQuery.value
  if (!query) { ElMessage.warning('请先填写会计期间（yyyyMM）'); return }
  try {
    await ElMessageBox.confirm(
      `确认为期间 ${query.period}（公司：${mcCompLabel.value}）生成月结检查清单？本次生成 8 项待办结检查项，已存在的项不重复生成。`,
      '生成月结检查清单', { type: 'warning', confirmButtonText: '生成清单', cancelButtonText: '取消' })
  } catch { return } // 用户取消：不产生任何写入
  ccSeeding.value = true
  try {
    const added = Number(await http.post('/tenant/gl/close-check/seed', null, { params: query, silent: true }))
    ElMessage.success(added > 0
      ? `检查清单已生成 ${added} 项，请各负责岗位逐项办结后再执行月结`
      : '该期间的检查清单已存在，无需重复生成，请直接逐项办结')
    await loadCloseCheck()
  } catch (e) { ElMessage.error(e.message) } finally { ccSeeding.value = false }
}
async function tickCloseCheck(row, done) {
  if (ccTicking.value) return
  ccTicking.value = true
  const prev = row.status
  row.status = done ? 'DONE' : 'PENDING'
  try {
    await http.post('/tenant/gl/close-check/check', { id: row.id, done }, { silent: true })
    await loadCloseCheck()
  } catch (e) {
    row.status = prev // 后端未落，界面如实退回未办结态
    ElMessage.error(e.message)
  } finally { ccTicking.value = false }
}
watch([() => mcForm.value.period, () => mcForm.value.companyId], () => { loadCloseCheck() })

/**
 * R5-D-154：税金计提落账挂接核查（只读）。
 * 后端 GET /api/tenant/glex/tax-provision/link-check?period（GlTaxProvisionService.linkCheck，权限 gl:taxprov:list）
 * 逐条报出 ①审批流 ②凭证 ③总账分录行数 ④缴款流，并给出「已入账但总账无分录」的条数与金额；
 * 本页只呈现，不补记、不改账——存量应落未落的处置由人工裁决腿负责。
 */
const canLinkCheck = hasPerm('gl:taxprov:list')
const lcVisible = ref(false)
const lcLoading = ref(false)
const lcRows = ref([])
const lcPeriod = ref('')
const lcSummary = ref({})
async function openLinkCheck() {
  const period = periodOrWarn(taxPeriod.value)
  if (!period) return
  lcPeriod.value = period
  lcVisible.value = true
  lcLoading.value = true
  try {
    const res = await http.get('/tenant/glex/tax-provision/link-check', { params: { period }, silent: true })
    lcSummary.value = res || {}
    lcRows.value = res?.rows || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally { lcLoading.value = false }
}

/* ---------------- 反结账：输入期间 + 二次确认 → POST /api/tenant/gl/month/unclose {period}
 * 警示：回退后该期间重新开放记账；后端校验权限与下一期间状态，失败经全局拦截器 + 此处 catch 原样展示（不吞错）。 */
async function doMonthUnclose() {
  let period = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入要反结账的期间（yyyyMM）',
      '反结账',
      {
        inputValue: mcForm.value.period || openPeriod.value || '',
        inputPlaceholder: '期间 yyyyMM',
        inputValidator: (v) => /^\d{6}$/.test(String(v || '').trim()) || '请输入 6 位期间（yyyyMM）',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        type: 'warning',
      },
    )
    period = String(value || '').trim()
  } catch (_) { return } // 用户取消
  // 二次确认（警示文案：回退后该期间重新开放记账）
  try {
    await ElMessageBox.confirm(
      `确认对期间 ${period} 反结账？回退后该期间将重新开放记账，已生成的结转/月结结果需人工复核！`,
      '反结账二次确认',
      { confirmButtonText: '确认反结账', cancelButtonText: '取消', type: 'warning', closeOnClickModal: false },
    )
  } catch (_) { return }
  try {
    await glApi.monthUnclose(period)
    ElMessage.success(`期间 ${period} 已反结账，该期间重新开放记账`)
    loadMonthClose()
  } catch (e) { ElMessage.error(e.message) }
}

/* ---------------- 损益结转向导：选择期间（el-date-picker month）→ POST /api/tenant/gl/profit-carry {period} ---------------- */
const pcVisible = ref(false)
const pcPeriod = ref('')
const pcLoading = ref(false)
// 结转期间是弹窗唯一必输项（原裸 required 只出红星不校验），红星走 rules+prop+model 三件套
const pcFormRef = ref(null)
const pcRules = { pcPeriod: commonRules.date('请选择结转期间') }
const pcModel = computed(() => ({ pcPeriod: pcPeriod.value }))

function openProfitCarry() {
  pcPeriod.value = /^\d{6}$/.test(String(mcForm.value.period || '').trim()) ? mcForm.value.period : ''
  pcVisible.value = true
}

// 展示后端返回结果（宽容读取常见字段，未知结构回退 JSON 原文）
function profitCarrySummary(res) {
  if (res == null) return '损益结转完成'
  if (typeof res === 'string') return res
  if (typeof res === 'object') {
    const parts = []
    if (res.voucherNo) parts.push('结转凭证 ' + res.voucherNo)
    if (res.resultMsg || res.msg || res.message) parts.push(res.resultMsg || res.msg || res.message)
    const amt = res.amount ?? res.totalAmount ?? res.carryAmount
    if (amt != null) parts.push('结转金额 ' + fmt(amt))
    if (Array.isArray(res.lines)) parts.push('分录 ' + res.lines.length + ' 条')
    if (parts.length) return parts.join('；')
    return '损益结转完成：' + JSON.stringify(res)
  }
  return '损益结转完成：' + String(res)
}

async function doProfitCarry() {
  if (!(await validateForms([pcFormRef]))) return
  const period = String(pcPeriod.value || '').trim()
  pcLoading.value = true
  try {
    const res = await glApi.profitCarry(period)
    ElMessage.success(`期间 ${period} 损益结转完成`)
    ElMessageBox.alert(profitCarrySummary(res), '损益结转结果', { type: 'success', confirmButtonText: '知道了' }).catch(() => {})
    pcVisible.value = false
    loadMonthClose()
  } catch (e) {
    // 幂等拒绝（该期间已结转）等后端校验失败原样提示，不关闭弹窗便于改期间重试
    ElMessage.error(e.message)
  } finally { pcLoading.value = false }
}
async function doMaster() {
  if (!(await validateForms([masterFormRef]))) return
  const mf = masterForm.value
  // R5-D-166：写动作两步确认＋要素回显
  if (!(await confirmWrite('确认提交主数据维护', [
    `期间 ${mf.period}；类型 ${masterTypeLabel(mf.type)}；动作 ${masterActionLabel(mf.action)}`,
    `编码 ${mf.subjectCode}${mf.subjectName ? '（' + mf.subjectName + '）' : ''}`,
    '将发起 GL 主数据审批流',
  ]))) return
  try {
    const r = await glexApi.sharedMasterSubmit(masterForm.value.period, masterForm.value.type,
      masterForm.value.action, masterForm.value.subjectCode, masterForm.value.subjectName, masterForm.value.description)
    ElMessage.success('GL主数据维护已提交，已发起审批')
    loadMaster()
    return r
  } catch (e) { ElMessage.error(e.message) }
}
function loadConsolidate() {
  try { return pagerConsolidate.load(async ({ pageNum, pageSize }) => { const d = await glexApi.consolidatePage(pageNum, pageSize, '', '', deepKeyword.value); consolidates.value = d.list || []; return { list: d.list || [], total: d.total || 0 } }).catch((e) => ElMessage.error(e.message)) } catch (e) { /* noop */ }
}
async function doConsolidate() {
  const period = periodOrWarn(consForm.value.period)
  if (!period) return
  // R5-D-166：写动作两步确认＋要素回显
  if (!(await confirmWrite('确认汇总合并', [
    `期间 ${period}`,
    '将汇总抵消合并底稿并发起合并报表审批流',
  ]))) return
  try {
    await glexApi.consolidateRun(period)
    ElMessage.success('合并报表已汇总，已发起合并报表审批')
    loadConsolidate()
  } catch (e) { ElMessage.error(e.message) }
}

/**
 * R5-D-117：页签 → 加载器 + 端点权限码（逐一核对后端 Gl*Controller 的 @RequiresPerm 真值）。
 * 单页形态（菜单深链 isSingle）只加载当前页签对应的 1 个 loader，其余页签首次切换时懒加载；
 * 两种形态都按 perms 跳过无权端点——无权端点后端必拒，原实现每轮 7 连发会弹 3~5 条权限码 toast，
 * 并把本域唯一有数请求拖过 5s 线（进页与每次刷新各打满一轮）。
 */
const TAB_LOADERS = {
  tax: { load: loadTax, perm: 'gl:taxprov:list' },
  salary: { load: loadSalary, perm: 'gl:salary:list' },
  inter: { load: loadInter, perm: 'gl:inter:list' },
  journal: { load: loadJournal, perm: 'gl:journal:list' },
  monthclose: { load: loadMonthClose, perm: 'gl:monthclose:list' },
  consolidate: { load: loadConsolidate, perm: 'gl:consolidate:list' },
  master: { load: loadMaster, perm: 'gl:sharedmaster:list' },
}
const tabFetched = new Set()
function loadTab(t, force) {
  const e = TAB_LOADERS[t]
  if (!e) return Promise.resolve()
  if (e.perm && !hasPerm(e.perm)) { tabFetched.add(t); return Promise.resolve() } // 无权端点不请求（也不再逐轮弹权限码 toast）
  if (!force && tabFetched.has(t)) return Promise.resolve()
  tabFetched.add(t)
  try { return Promise.resolve(e.load()).catch(() => {}) } catch (err) { return Promise.resolve() }
}
// 页签切换懒加载：未取过的页签首次进入才发请求（已取过的切回不重复发）
watch(tab, (t) => { loadTab(t) })

async function reload() {
  loading.value = true
  try {
    if (isSingle.value) {
      // R5-D-117：单页形态只打当前页签对应的 1 个端点（原无条件 7 连发）
      await loadTab(tab.value, true)
    } else {
      await Promise.all(Object.keys(TAB_LOADERS).map((t) => loadTab(t, true)))
    }
  } finally {
    loading.value = false
  }
  loadCloseCheck() // R5-D-215：刷新时同步带回当期检查闸清单
}

/**
 * R5-D-157 第二因：md_subject 存活 851 行，原单页 pageSize=500 静默丢 351 行 ⇒ 名称查找批量落空。
 * 先取第一页读 total，再按 total 逐页拉全（防御上限 20 页），并集按 id 去重后返回。
 */
async function fetchAllSubjects() {
  const size = 500
  const first = await mdApi.subjects({ pageNum: 1, pageSize: size })
  let list = first?.list || first?.records || []
  const total = Number(first?.total || 0)
  const pages = Math.min(Math.ceil(total / size), 20)
  for (let p = 2; p <= pages; p++) {
    const d = await mdApi.subjects({ pageNum: p, pageSize: size })
    list = list.concat(d?.list || d?.records || [])
  }
  const seen = new Set()
  return list.filter((s) => (seen.has(s.id) ? false : (seen.add(s.id), true)))
}

// 加载部门/公司/科目主数据（用于下拉选择和列表名称查找）
async function loadMasters() {
  try {
    const [deptRes, compRes, subRes] = await Promise.allSettled([
      orgApi.deptTree(),
      orgApi.companyTree(),
      fetchAllSubjects(),
    ])
    if (deptRes.status === 'fulfilled') {
      // 部门树可能返回嵌套结构，递归提取
      const flat = []
      const walk = (arr) => { arr.forEach((n) => { flat.push({ id: n.id, name: n.deptName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
      walk(deptRes.value || [])
      depts.value = flat
    }
    if (compRes.status === 'fulfilled') {
      const flat = []
      const walk = (arr) => { arr.forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
      walk(compRes.value || [])
      companies.value = flat
    }
    if (subRes.status === 'fulfilled') {
      // fetchAllSubjects 已摊平为数组（原为 PageResult 单页对象）
      subjects.value = (subRes.value || []).map((s) => ({ id: s.id, subjectName: s.subjectName || s.name, code: s.subjectCode }))
    }
  } catch (e) { /* ignore */ }
}
/**
 * D-DEC-140：解析「当前开账期间」（yyyyMM）。
 * 口径：md_period 中状态非 CLOSED 的最早一期＝仍在入账的期间（月结是自前往后关账）；
 * 全期已关时退回最后一期，接口不可用/无权限时退回真实时钟当月。
 * 只用于填默认值，不改变 periodOrWarn 的必填校验，也不代为提交。
 */
async function loadOpenPeriod() {
  const now = new Date()
  let ym = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
  if (canLoadPeriod) {
    try {
      const res = await periodApi.page({ pageNum: 1, pageSize: 120 }, { silent: true })
      const list = res?.list || res?.records || []
      // R5-D-169：期间字段名按 MdPeriod 实体真身为 periodYear / periodMonth（会计期间表列 period_year/period_month）；
      // 原读 p.year / p.month 恒为 undefined ⇒ toYm 产出 "undefinedundefined"、被 /^\d{6}$/ 全滤掉，
      // 于是本函数永远退回「系统当月」，六个页签根本拿不到当前打开期间。year/month 仅作容错兜底保留。
      const toYm = (p) => {
        const y = Number(p.periodYear ?? p.year)
        const m = Number(p.periodMonth ?? p.month)
        return y > 0 && m > 0 ? `${y}${String(m).padStart(2, '0')}` : ''
      }
      const all = list.map(toYm).filter((s) => /^\d{6}$/.test(s)).sort()
      const opened = all.filter((s) => {
        const raw = list.find((p) => toYm(p) === s)
        return raw && String(raw.status || '').toUpperCase() !== 'CLOSED'
      })
      if (opened.length) { ym = opened[0]; periodSource.value = '当前开账期间' }
      // 全期已关：退回期间表最后一期。此时来源不能再自称「开账期间」，照实写清已结账，
      // 免得界面把一个人造的默认值说成系统事实（计提会落进已关期间，服务端会拒）。
      else if (all.length) { ym = all[all.length - 1]; periodSource.value = '会计期间表最后一期（均已结账）' }
      else periodSource.value = '系统当月'
      // R5-D-169：同一份期间表读数顺带记下已结账期间（status=CLOSED），供六个页签提交钮的禁用态复用；
      // 无权限或取数失败时集合为空，按钮维持旧行为（服务端仍会拒）。
      closedPeriods.value = list.filter((p) => String(p.status || '').toUpperCase() === 'CLOSED')
        .map(toYm).filter((s) => /^\d{6}$/.test(s))
    } catch { periodSource.value = '系统当月' /* 取数失败退回真实时钟当月，来源标注随之改口 */ }
  }
  openPeriod.value = ym
  if (!taxPeriod.value) taxPeriod.value = ym
  // R5-D-169：其余六个页签（薪酬发放/内部往来抵消/一般记账/月结/合并报表/GL主数据）的期间默认值
  // 与税金页签同源，统一取上面解析出的当前打开期间；只填「用户还没动过的空值」，已填的不覆盖，用户仍可手改。
  for (const f of [salaryForm, interForm, journalForm, mcForm, consForm, masterForm]) {
    if (!String(f.value.period ?? '').trim()) f.value.period = ym
  }
}

// R5-D-148：首屏不再串行等主数据。依赖核对过两件事——
// 1) reload() 的七枚表腿（税金/薪酬/内部往来/记账/月结/GL主数据/合并报表）只传分页＋关键字，
//    既不读 openPeriod 也不读 depts/companies/subjects，故表格取数不依赖主数据与期间；
// 2) 唯一吃期间的末腿 loadCloseCheck() 取的是 mcForm.period，而本页已 watch 该字段，
//    loadOpenPeriod() 回填期间后它会自动补跑，selects／期间默认值都会在各自请求落地后补齐。
// 故三枚发起并行：期间调用仍排在最前，主数据不再 await，表格立即起量。
onMounted(() => {
  loadOpenPeriod().catch(() => {})
  loadMasters().catch(() => {})
  reload()
})

function exportCsv() {
  if (!masters.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'reqNo', label: '申请单号' }, { prop: 'period', label: '期间(yyyyMM)' }, { prop: 'subjectCode', label: '科目编码' }, { prop: 'subjectName', label: '科目名称' }, { prop: 'description', label: '变更说明' }, { prop: 'voucherNo', label: '记账凭证号' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = masters.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `GlSpecial_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `GlSpecial_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_glSpecialCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => glSpecialCm.value?.show(), 800) } } catch {}
// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: reload })

function handleView(row) {
  ElMessage.info('批次：' + (row.runNo || row.id))
}
</script>

<style scoped>
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
.tax-pay-route { color: #e6a23c; font-size: 12px; }
/* R5-D-169：六页签提交钮旁的期间提示（所选期间已结账禁提 / 期间状态不可读），沿用本页既有 12px 警示色 */
.period-note { color: #e6a23c; font-size: 12px; }
/* R5-D-215：月结检查闸清单区（沿用本页「头部一行 + el-table」的既有排布，不引新样式体系） */
.close-check { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; margin-bottom: 12px; background: #fafcff; }
.cc-head { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
.cc-meta { color: #909399; font-size: 12px; }
</style>