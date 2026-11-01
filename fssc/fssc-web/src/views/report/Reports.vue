<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="['reports', { tabless: isSingle }]">
    <el-card header="费用概览">
      <!-- 四格只摆服务端概览接口的真返回值；查询失败如实标「本次未刷新成功」，不再拿 0 顶数 -->
      <el-alert v-if="overviewError" type="error" :closable="false" show-icon style="margin-bottom:12px"
        title="本次未刷新成功：费用概览未取到服务端数据" />
      <el-row :gutter="16">
        <el-col :span="6" v-for="s in overview" :key="s.label">
          <el-statistic v-if="s.value != null" :title="s.label" :value="s.value" :precision="s.precision" :suffix="s.suffix" />
          <div v-else class="overview-missing">
            <div class="overview-missing-label">{{ s.label }}</div>
            <div class="overview-missing-value">{{ overviewError ? '本次未刷新成功' : '加载中…' }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card header="管理报表" style="margin-top:16px">
      <div class="toolbar">
        <el-button type="success" size="small" v-permission="'report:export'" @click="exp('bills','费用明细')">导出费用明细</el-button>
        <el-button type="success" size="small" v-permission="'report:export'" @click="exp('deptRanking','部门排名')">导出部门排名</el-button>
        <el-button type="success" size="small" v-permission="'report:export'" @click="exp('budgetExecution','预算执行')">导出预算执行</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      </div>
      <!-- W5（非人性化整改）：25 个页签横排难定位，提供分组下拉直达；页签本身保留不动 -->
      <div class="toolbar" style="margin-bottom:8px">
        <el-select v-model="tab" filterable placeholder="报表快速定位（按分组跳转）" size="small" style="width: 260px">
          <el-option-group v-for="g in REPORT_GROUPS" :key="g.label" :label="g.label">
            <el-option v-for="t in g.items" :key="t.name" :label="t.label" :value="t.name" />
          </el-option-group>
        </el-select>
        <span style="margin-left:10px;color:#909399;font-size:12px">共 {{ REPORT_GROUPS.reduce((n, g) => n + g.items.length, 0) }} 张报表，可按分组直接跳转，无需在页签条上逐个数</span>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="部门报销排名" name="dept" v-if="tabAllowed('dept')">
          <BaseChart :option="deptChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.deptRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="deptName" label="部门"  min-width="120" sortable />
            <el-table-column prop="billCount" label="单量" width="100"  sortable />
            <el-table-column prop="totalAmount" label="金额" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="预算执行" name="budget" v-if="tabAllowed('budget')">
          <BaseChart :option="budgetChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.budgetRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="dimKey" label="预算维度"  width="120" sortable />
            <el-table-column prop="totalAmount" label="总额" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="usedAmount" label="已用" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="occupyAmount" label="占用" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="executionRate" label="执行率(%)" width="110" sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="审批效率" name="eff" v-if="tabAllowed('eff')">
          <BaseChart :option="effChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.effRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="nodeName" label="单据类型"  width="120" sortable :formatter="(row, col, val) => billTypeLabel(val)" />
            <el-table-column prop="avgHours" label="平均耗时(时)" width="140"  sortable />
            <el-table-column prop="maxHours" label="最长耗时(时)" width="140"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="借款余额汇总" name="loan" v-if="tabAllowed('loan')">
          <el-table :data="paged.loanRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="applyUserName" label="员工"  width="100" sortable />
            <el-table-column prop="billCnt" label="借款笔数" width="100"  sortable />
            <el-table-column prop="loanTotal" label="借款总额" width="140"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="balance" label="未冲销余额" width="140" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.balance) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="合同汇总" name="contract" v-if="tabAllowed('contract')">
          <el-table :data="paged.contractRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="contractNo" label="合同编号" width="130"  sortable />
            <el-table-column prop="contractName" label="合同名称" min-width="140"  sortable />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ contractTypeLabel(row.contractType) }}</template>
            </el-table-column>
            <el-table-column prop="partyBName" label="供应商" min-width="120"  sortable />
            <el-table-column prop="contractAmount" label="合同总额" width="110"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="paidAmount" label="已付款" width="110" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.paidAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用明细表" name="detail" v-if="tabAllowed('detail')">
          <div style="margin-bottom:8px;display:flex;gap:8px">
            <el-input v-model="detailKw" placeholder="按 单号/经办人/费用项 搜索" clearable style="width:260px" @keyup.enter="loadDetail" />
            <el-button size="small" type="primary" @click="loadDetail">查询</el-button>
            <el-button size="small" @click="detailKw = ''; loadDetail()">重置</el-button>
          </div>
          <el-table :data="paged.detailRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="单号" width="150"  sortable />
            <el-table-column prop="applyUserName" label="经办人" width="90"  sortable />
            <el-table-column prop="itemName" label="费用项" width="110"  sortable />
            <el-table-column prop="amount" label="金额" width="100"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="applyDate" label="日期" width="110"  sortable :formatter="(r, c, v) => formatDate(v)" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="voucherNo" label="凭证号" width="140"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="运营数据统计" name="ops" v-if="tabAllowed('ops')">
          <el-table :data="paged.opsRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="kpi" label="指标"  min-width="120" sortable />
            <el-table-column prop="val" label="数值" width="140"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用发生汇总" name="summary" v-if="tabAllowed('summary')">
          <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center">
            <el-select v-model="sumYear" style="width:120px" placeholder="年度">
              <el-option v-for="y in [2025,2026,2027]" :key="y" :label="y" :value="y" />
            </el-select>
            <el-select v-model="sumMonth" clearable style="width:120px" placeholder="月份">
              <el-option v-for="m in 12" :key="m" :label="m + ' 月'" :value="m" />
            </el-select>
            <el-button size="small" type="primary" @click="loadSummary">查询</el-button>
            <el-button size="small" @click="sumYear = new Date().getFullYear(); sumMonth = null; loadSummary()">重置</el-button>
          </div>
          <BaseChart :option="summaryChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.sumRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="itemName" label="费用类型"  min-width="120" sortable />
            <el-table-column prop="billCnt" label="单据数" width="100"  sortable />
            <el-table-column prop="lineCnt" label="明细行数" width="100"  sortable />
            <el-table-column prop="totalAmount" label="发生金额" width="140" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.totalAmount) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用月度变动" name="trend" v-if="tabAllowed('trend')">
          <BaseChart :option="trendChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.trendRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="year" label="年" width="70"  sortable />
            <el-table-column prop="month" label="月" width="70"  sortable />
            <el-table-column prop="itemName" label="费用类型"  min-width="120" sortable />
            <el-table-column prop="totalAmount" label="金额" width="140" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.totalAmount) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用构成情况" name="composition" v-if="tabAllowed('composition')">
          <div style="margin-bottom:8px;display:flex;gap:8px">
            <el-input v-model="compItem" placeholder="输入费用类型过滤（如：差旅）" clearable style="width:240px" />
            <el-button size="small" type="primary" @click="loadComposition">查询</el-button>
            <el-button size="small" @click="compItem = ''; loadComposition()">重置</el-button>
          </div>
          <el-table :data="paged.compRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column label="组织" width="120">
              <template #default="{ row }">{{ companyName(row.companyId) }}</template>
            </el-table-column>
            <el-table-column prop="year" label="年" width="70"  sortable />
            <el-table-column prop="month" label="月" width="70"  sortable />
            <el-table-column prop="totalAmount" label="金额" width="140" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.totalAmount) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="应付账龄分析" name="apAging" v-if="tabAllowed('apAging')">
          <BaseChart :option="apAgingChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.apAgingRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="payeeName" label="供应商" min-width="140"  sortable />
            <el-table-column prop="billCnt" label="单据数" width="80"  sortable />
            <el-table-column prop="balance" label="应付余额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f56c6c;font-weight:600">{{ formatMoney(row.balance) }}</span></template>
            </el-table-column>
            <el-table-column prop="d0_30" label="0-30天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d31_60" label="31-60天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d61_90" label="61-90天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d90_plus" label="90天以上" width="100" sortable>
              <template #default="{ row }"><span style="color:#f56c6c">{{ formatMoney(row.d90_plus) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="应收账龄分析" name="arAging" v-if="tabAllowed('arAging')">
          <el-table :data="paged.arAgingRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column label="客户" width="120">
              <template #default="{ row }">{{ row.customerName || '—' }}</template>
            </el-table-column>
            <el-table-column prop="billCnt" label="单据数" width="80"  sortable />
            <el-table-column prop="balance" label="应收余额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.balance) }}</span></template>
            </el-table-column>
            <el-table-column prop="d0_30" label="0-30天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d31_60" label="31-60天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d61_90" label="61-90天" width="100"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="d90_plus" label="90天以上" width="100" sortable>
              <template #default="{ row }"><span style="color:#f56c6c">{{ formatMoney(row.d90_plus) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="资金收支汇总" name="cashFlow" v-if="tabAllowed('cashFlow')">
          <BaseChart :option="cashFlowChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.cashFlowRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="period" label="期间" width="100"  sortable />
            <el-table-column prop="income" label="收入" width="140" sortable>
              <template #default="{ row }"><span style="color:#67c23a">{{ formatMoney(row.income) }}</span></template>
            </el-table-column>
            <el-table-column prop="expense" label="支出" width="140" sortable>
              <template #default="{ row }"><span style="color:#f56c6c">{{ formatMoney(row.expense) }}</span></template>
            </el-table-column>
            <el-table-column prop="netAmount" label="净额" width="140" sortable>
              <template #default="{ row }"><span style="font-weight:600">{{ formatMoney(row.netAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="transCnt" label="交易笔数" width="100"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="资金余额表" name="bankBalance" v-if="tabAllowed('bankBalance')">
          <el-table :data="paged.bankBalanceRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="accountName" label="账户名称" min-width="140"  sortable />
            <el-table-column prop="bankName" label="开户行" min-width="140"  sortable />
            <el-table-column prop="bankAccount" label="账号" width="160"  sortable />
            <el-table-column prop="currencyCode" label="币种" width="80"  sortable />
            <el-table-column prop="balance" label="余额" width="140" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.balance) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="供应商付款汇总" name="supplierPay" v-if="tabAllowed('supplierPay')">
          <el-table :data="paged.supplierPayRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="payeeName" label="供应商" min-width="140"  sortable />
            <el-table-column prop="billCnt" label="单据数" width="80"  sortable />
            <el-table-column prop="totalAmount" label="应付总额" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="paidAmount" label="已付金额" width="120" sortable>
              <template #default="{ row }"><span style="color:#67c23a">{{ formatMoney(row.paidAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="unpaidAmount" label="未付金额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f56c6c">{{ formatMoney(row.unpaidAmount) }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="进项发票统计" name="inputInvoice" v-if="tabAllowed('inputInvoice')">
          <el-table :data="paged.inputInvRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="period" label="期间" width="100"  sortable />
            <el-table-column prop="invoiceCnt" label="发票数" width="80"  sortable />
            <el-table-column prop="totalAmount" label="价税合计" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="taxAmount" label="税额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.taxAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="netAmount" label="不含税" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="销项发票统计" name="outputInvoice" v-if="tabAllowed('outputInvoice')">
          <el-table :data="paged.outputInvRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="period" label="期间" width="100"  sortable />
            <el-table-column prop="invoiceCnt" label="发票数" width="80"  sortable />
            <el-table-column prop="totalAmount" label="价税合计" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="taxAmount" label="税额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.taxAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="netAmount" label="不含税" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="税负分析" name="taxBurden" v-if="tabAllowed('taxBurden')">
          <BaseChart :option="taxBurdenChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.taxRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="period" label="期间" width="100"  sortable />
            <!-- 手册 H10.4a 期望表头「税种/税基/税额/税负率」，原页只有三个税额列。
                 税种＝增值税（inv_invoice 全量为 VAT/VAT_NORMAL，本系统无其它税种）；
                 税基＝销项不含税金额（后端 taxBurdenAnalysis 新增 taxBase）；税负率＝应纳税额/税基。 -->
            <el-table-column label="税种" width="90">
              <template #default>增值税</template>
            </el-table-column>
            <el-table-column prop="taxBase" label="税基" width="130" align="right" sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="inputTax" label="进项税额" width="120" sortable>
              <template #default="{ row }"><span style="color:#67c23a">{{ formatMoney(row.inputTax) }}</span></template>
            </el-table-column>
            <el-table-column prop="outputTax" label="销项税额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f56c6c">{{ formatMoney(row.outputTax) }}</span></template>
            </el-table-column>
            <el-table-column prop="netTax" label="应纳税额" width="120" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.netTax) }}</span></template>
            </el-table-column>
            <el-table-column label="税负率" width="100" align="right">
              <template #default="{ row }">{{ taxBurdenRate(row) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="运营效率分析" name="opsEff" v-if="tabAllowed('opsEff')">
          <el-table :data="paged.opsEffRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="taskType" label="任务类型" width="120"  sortable :formatter="(row, col, val) => taskTypeLabel(val)" />
            <el-table-column prop="taskCnt" label="任务数" width="80"  sortable />
            <el-table-column prop="avgMinutes" label="平均耗时(分)" width="120"  sortable />
            <el-table-column prop="maxMinutes" label="最长耗时(分)" width="120"  sortable />
            <el-table-column prop="slaBreachCnt" label="SLA超时数" width="120" sortable>
              <template #default="{ row }"><span :style="{color: row.slaBreachCnt > 0 ? '#f56c6c' : '#67c23a'}">{{ row.slaBreachCnt }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="质检合格率" name="quality" v-if="tabAllowed('quality')">
          <BaseChart :option="qualityChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.qualityRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="checkType" label="抽检方式" width="120"  sortable :formatter="(row, col, val) => checkSamplingLabel(val)" />
            <el-table-column prop="totalCnt" label="抽检总数" width="100"  sortable />
            <el-table-column prop="passCnt" label="合格数" width="100"  sortable />
            <el-table-column prop="passRate" label="合格率(%)" width="120" sortable>
              <template #default="{ row }"><span :style="{color: row.passRate >= 95 ? '#67c23a' : '#f56c6c', fontWeight: 600}">{{ row.passRate }}</span></template>
            </el-table-column>
            <el-table-column prop="avgScore" label="平均评分" width="100"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="影像统计" name="image" v-if="tabAllowed('image')">
          <el-table :data="paged.imageRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="bizType" label="业务类型" width="120"  sortable :formatter="(row, col, val) => bizTypeLabel(val)" />
            <el-table-column prop="batchCnt" label="批次数量" width="100"  sortable />
            <el-table-column prop="totalDocs" label="文档总数" width="100"  sortable />
            <el-table-column prop="reviewedCnt" label="已审核" width="100"  sortable />
            <el-table-column prop="sealedCnt" label="已签章" width="100"  sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="合同执行明细" name="contractExec" v-if="tabAllowed('contractExec')">
          <el-table :data="paged.contractExecRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="contractNo" label="合同编号" width="130"  sortable />
            <el-table-column prop="contractName" label="合同名称" min-width="140"  sortable />
            <el-table-column prop="partyBName" label="供应商" min-width="120"  sortable />
            <el-table-column prop="contractAmount" label="合同总额" width="110"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="paidAmount" label="已付款" width="110" sortable>
              <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.paidAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="executionRate" label="执行率(%)" width="100" sortable>
              <template #default="{ row }"><span :style="{color: row.executionRate >= 100 ? '#f56c6c' : row.executionRate >= 80 ? '#f59a23' : '#67c23a', fontWeight: 600}">{{ row.executionRate }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="预算超支预警" name="budgetAlert" v-if="tabAllowed('budgetAlert')">
          <BaseChart :option="budgetAlertChart" height="280px" style="margin-bottom:12px" />
          <el-table :data="paged.budgetAlertRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="deptName" label="部门" min-width="140"  sortable />
            <el-table-column prop="itemName" label="费用项" min-width="140"  sortable />
            <el-table-column prop="budgetYear" label="年度" width="80"  sortable />
            <el-table-column prop="totalAmount" label="预算总额" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="usedAmount" label="已使用" width="120" sortable>
              <template #default="{ row }"><span style="color:#f56c6c;font-weight:600">{{ formatMoney(row.usedAmount) }}</span></template>
            </el-table-column>
            <el-table-column prop="usageRate" label="使用率(%)" width="100"  sortable :formatter="(r, c, v) => (v == null ? '—' : Number(v).toFixed(1))" />
            <el-table-column label="预警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.alertLevel === 'OVER' ? 'danger' : 'warning'" size="small">{{ row.alertLevel === 'OVER' ? '超支' : '预警' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="借款逾期分析" name="loanOverdue" v-if="tabAllowed('loanOverdue')">
          <el-table :data="paged.loanOverdueRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="单号" width="150"  sortable />
            <el-table-column prop="applyUserName" label="借款人" width="90"  sortable />
            <el-table-column prop="loanAmount" label="借款金额" width="110"  sortable :formatter="(r, c, v) => formatMoney(v)" />
            <el-table-column prop="balance" label="未还余额" width="110" sortable>
              <template #default="{ row }"><span style="color:#f56c6c;font-weight:600">{{ formatMoney(row.balance) }}</span></template>
            </el-table-column>
            <el-table-column prop="expectRepayDate" label="预计还款日" width="120"  sortable :formatter="(r, c, v) => formatDate(v)" />
            <el-table-column prop="overdueDays" label="逾期天数" width="100" sortable>
              <template #default="{ row }"><span :style="{color: row.overdueDays > 30 ? '#f56c6c' : '#f59a23', fontWeight: 600}">{{ row.overdueDays }}</span></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="凭证生成统计" name="voucherStats" v-if="tabAllowed('voucherStats')">
          <el-table :data="paged.voucherRows" size="small" stripe v-loading="loading" max-height="360" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="sourceType" label="来源类型" width="120"  sortable :formatter="(row, col, val) => voucherSourceLabel(val)" />
            <el-table-column prop="period" label="期间" width="100"  sortable />
            <el-table-column prop="voucherCnt" label="凭证数" width="80"  sortable />
            <el-table-column prop="debitTotal" label="借方合计" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
            <el-table-column prop="creditTotal" label="贷方合计" width="140"  sortable :formatter="(r,c,v) => formatMoney(v)" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <!-- 逐 tab 本地分页（只渲染当前激活 tab 的 pager） -->
      <TablePager v-if="activePager" :pager="activePager" />
    </el-card>
  </div>
</template>

<script setup>
import { taskTypeLabel, checkSamplingLabel, billTypeLabel, bizTypeLabel, voucherSourceLabel, contractTypeLabel } from '@/constants/enums'
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { dashApi, reportApi, orgApi, http, store } from '../../api'
import BaseChart from '../../components/BaseChart.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatMoney, formatDate } from '@/utils/format'
import { hasPerm } from '@/utils/perm'

// G1（权限治理）：25 tab 按 12 报表域门控。
// 每枚 tab 绑定一个 report:<domain>view 权限码，无权则隐藏 tab（不发请求、不渲染）。
// 域定义见后端 ReportDomain.java（EXP/AR/P2P/CASH/ASSET/GL/BUDGET/CONTRACT/SHARING/APPROVAL/IMAGE/TAX）。
const TAB_DOMAIN = {
  dept: 'report:exp:view', summary: 'report:exp:view', trend: 'report:exp:view',
  composition: 'report:exp:view', detail: 'report:exp:view', loan: 'report:exp:view', loanOverdue: 'report:exp:view',
  budget: 'report:budget:view', budgetAlert: 'report:budget:view',
  eff: 'report:approval:view',
  contract: 'report:contract:view', contractExec: 'report:contract:view',
  ops: 'report:sharing:view', opsEff: 'report:sharing:view', quality: 'report:sharing:view',
  apAging: 'report:p2p:view', supplierPay: 'report:p2p:view', inputInvoice: 'report:p2p:view',
  arAging: 'report:ar:view', outputInvoice: 'report:ar:view',
  cashFlow: 'report:cash:view', bankBalance: 'report:cash:view',
  taxBurden: 'report:tax:view',
  image: 'report:image:view',
  voucherStats: 'report:gl:view',
}
/** 判断某 tab 是否有权限（超管通配自动放行） */
function tabAllowed(name) { return hasPerm(TAB_DOMAIN[name]) }

// 公司列表（用于报表中 companyId → 公司名）
const selRows = ref([])
const companies = ref([])
function companyName(id) { const c = companies.value.find((x) => x.value === id); return c ? c.label : (id ? '#' + id : '—') }

// 通用状态翻译

// 合同类型翻译

// 默认 tab 支持路由 prop 指定（10 个报表子菜单指向本页不同 tab）
// 组件复用时不重建实例，必须 watch props 同步 tab
// default 由 'dept' 改 ''——宿主路由（/reports 无 props）渲染 25 页签工作台；
// 显式传 tab 的深链（/reports/dept 等）仍单页模式。
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'dept')
watch(() => props.tab, (v) => { if (v) tab.value = v })

// W5（非人性化整改）：25 张报表按业务分组，供「报表快速定位」下拉跳转。
// name 必须与 el-tab-pane 的 name 逐一对应；新增/下线页签时两处同步。
const REPORT_GROUPS = [
  { label: '费用报销与预算', items: [
    { name: 'dept', label: '部门报销排名' }, { name: 'summary', label: '费用发生汇总' },
    { name: 'trend', label: '费用月度变动' }, { name: 'composition', label: '费用构成情况' },
    { name: 'detail', label: '费用明细表' }, { name: 'budget', label: '预算执行' },
    { name: 'budgetAlert', label: '预算超支预警' } ] },
  { label: '应付与采购付款', items: [
    { name: 'apAging', label: '应付账龄分析' }, { name: 'supplierPay', label: '供应商付款汇总' },
    { name: 'inputInvoice', label: '进项发票统计' } ] },
  { label: '应收与销售开票', items: [
    { name: 'arAging', label: '应收账龄分析' }, { name: 'outputInvoice', label: '销项发票统计' } ] },
  { label: '资金与税务', items: [
    { name: 'cashFlow', label: '资金收支汇总' }, { name: 'bankBalance', label: '资金余额表' },
    { name: 'taxBurden', label: '税负分析' } ] },
  { label: '借款', items: [
    { name: 'loan', label: '借款余额汇总' }, { name: 'loanOverdue', label: '借款逾期分析' } ] },
  { label: '合同', items: [
    { name: 'contract', label: '合同汇总' }, { name: 'contractExec', label: '合同执行明细' } ] },
  { label: '审批与共享运营', items: [
    { name: 'eff', label: '审批效率' }, { name: 'ops', label: '运营数据统计' },
    { name: 'opsEff', label: '运营效率分析' }, { name: 'quality', label: '质检合格率' },
    { name: 'image', label: '影像统计' } ] },
  { label: '记账凭证', items: [
    { name: 'voucherStats', label: '凭证生成统计' } ] },
]
const loading = ref(false)
// 概览四格的值一律来自服务端概览接口；取不到即置 overviewError，界面上摆「本次未刷新成功」，
// 不再用 ?? 0 把「没取到」说成「金额为 0」。初值为 null（渲染「查询中…」），成功回填后才出数。
const overviewError = ref(false)
// 预算执行率由服务端回两位小数的百分数（如 81.55），裸渲染会被统计卡按默认精度抹成「81」，
// 故该格单列 precision/suffix；金额与单数保持整数千分位。
const overview = reactive([
  { label: '本月报销总额', value: null },
  { label: '待审单据', value: null },
  { label: '已付金额', value: null },
  { label: '预算执行率', value: null, precision: 2, suffix: '%' },
])
const deptRows = ref([])
const budgetRows = ref([])
const effRows = ref([])
const loanRows = ref([])
const contractRows = ref([])
const detailRows = ref([])
const detailKw = ref('')
const opsRows = ref([])
const sumRows = ref([])
const sumYear = ref(new Date().getFullYear())
const sumMonth = ref(null)
const trendRows = ref([])
const compRows = ref([])
const compItem = ref('')

// 扩展报表数据
const apAgingRows = ref([])
const arAgingRows = ref([])
const cashFlowRows = ref([])
const bankBalanceRows = ref([])
const supplierPayRows = ref([])
const inputInvRows = ref([])
const outputInvRows = ref([])
const taxRows = ref([])
const opsEffRows = ref([])
const qualityRows = ref([])
const imageRows = ref([])
const contractExecRows = ref([])
const budgetAlertRows = ref([])
const loanOverdueRows = ref([])
const voucherRows = ref([])

// 逐 tab 本地分页
// 25 个报表 tab 各自独立 local pager（watch 数据源→setSource，total/页码自动 clamp）；
// 底部单个 <TablePager> 只渲染当前激活 tab 的 pager；跨页勾选导出仅限当前页（ERP 标准行为）。
function makeTabPager(srcRef, key) {
  const p = useTablePager({ mode: 'local', storageKey: 'reports_' + key })
  watch(srcRef, (v) => { p.setSource(v || []) }, { immediate: true })
  return { pager: p, sliced: p.pagedData }
}
const __tabPagedDefs = [
  ['deptRows', deptRows, 'dept'], ['budgetRows', budgetRows, 'budget'], ['effRows', effRows, 'eff'], ['loanRows', loanRows, 'loan'], ['contractRows', contractRows, 'contract'],
  ['detailRows', detailRows, 'detail'], ['opsRows', opsRows, 'ops'], ['sumRows', sumRows, 'summary'], ['trendRows', trendRows, 'trend'], ['compRows', compRows, 'composition'],
  ['apAgingRows', apAgingRows, 'apAging'], ['arAgingRows', arAgingRows, 'arAging'], ['cashFlowRows', cashFlowRows, 'cashFlow'], ['bankBalanceRows', bankBalanceRows, 'bankBalance'],
  ['supplierPayRows', supplierPayRows, 'supplierPay'], ['inputInvRows', inputInvRows, 'inputInvoice'], ['outputInvRows', outputInvRows, 'outputInvoice'],
  ['taxRows', taxRows, 'taxBurden'], ['opsEffRows', opsEffRows, 'opsEff'], ['qualityRows', qualityRows, 'quality'], ['imageRows', imageRows, 'image'],
  ['contractExecRows', contractExecRows, 'contractExec'], ['budgetAlertRows', budgetAlertRows, 'budgetAlert'], ['loanOverdueRows', loanOverdueRows, 'loanOverdue'],
  ['voucherRows', voucherRows, 'voucherStats'],
]
// paged 用 reactive 包裹：内部 ref 在模板中自动解包（paged.deptRows 直接是数组）
const paged = reactive({})
const tabPagers = {}
for (const [name, src, key] of __tabPagedDefs) {
  const { pager, sliced } = makeTabPager(src, key)
  tabPagers[key] = pager
  paged[name] = sliced
}
const activePager = computed(() => tabPagers[tab.value] || null)

// ECharts computed options
const deptChart = computed(() => {
  const rows = deptRows.value || []
  const sorted = [...rows].sort((a, b) => (b.totalAmount || 0) - (a.totalAmount || 0)).slice(0, 15)
  return {
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].name}: ${p[0].value}` },
    grid: { left: '3%', right: '6%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '金额' },
    yAxis: { type: 'category', data: sorted.map(r => r.deptName).reverse(), axisLabel: { width: 80, overflow: 'truncate' } },
    series: [{ type: 'bar', data: sorted.map(r => r.totalAmount).reverse(), itemStyle: { color: '#409eff' }, barMaxWidth: 24 }],
  }
})
const budgetChart = computed(() => {
  const rows = (budgetRows.value || []).slice(0, 20)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['预算总额', '已用', '占用'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.dimKey), axisLabel: { rotate: 30, interval: 0 } },
    yAxis: { type: 'value', name: '金额' },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 16 }],
    series: [
      { name: '预算总额', type: 'bar', data: rows.map(r => r.totalAmount), itemStyle: { color: '#409eff' } },
      { name: '已用', type: 'bar', data: rows.map(r => r.usedAmount), itemStyle: { color: '#67c23a' } },
      { name: '占用', type: 'bar', data: rows.map(r => r.occupyAmount), itemStyle: { color: '#e6a23c' } },
    ],
  }
})
const effChart = computed(() => {
  const rows = effRows.value || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均耗时', '最长耗时'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => billTypeLabel(r.nodeName)), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '小时' },
    series: [
      { name: '平均耗时', type: 'bar', data: rows.map(r => r.avgHours), itemStyle: { color: '#409eff' } },
      { name: '最长耗时', type: 'bar', data: rows.map(r => r.maxHours), itemStyle: { color: '#f56c6c' } },
    ],
  }
})
const summaryChart = computed(() => {
  const rows = sumRows.value || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: 10, top: 20, bottom: 20 },
    series: [{
      type: 'pie', radius: ['30%', '60%'], center: ['38%', '50%'],
      data: rows.map(r => ({ name: r.itemName || '未分类', value: r.totalAmount })).filter(d => d.value != null),
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    }],
  }
})
const trendChart = computed(() => {
  const rows = trendRows.value || []
  const items = [...new Set(rows.map(r => r.itemName).filter(Boolean))]
  const periods = [...new Set(rows.map(r => `${r.year}-${String(r.month).padStart(2, '0')}`))].sort()
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: items, top: 0, type: 'scroll' },
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: periods, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '金额' },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 16 }],
    series: items.map(item => ({
      name: item, type: 'line', smooth: true,
      connectNulls: true,
      data: periods.map(p => {
        const [y, m] = p.split('-').map(Number)
        const row = rows.find(r => r.itemName === item && r.year === y && r.month === m)
        return row ? row.totalAmount : null
      }),
    })),
  }
})
const cashFlowChart = computed(() => {
  const rows = cashFlowRows.value || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出', '净额'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.period) },
    yAxis: { type: 'value', name: '金额' },
    series: [
      { name: '收入', type: 'line', smooth: true, data: rows.map(r => r.income), itemStyle: { color: '#67c23a' }, areaStyle: { opacity: 0.08 } },
      { name: '支出', type: 'line', smooth: true, data: rows.map(r => r.expense), itemStyle: { color: '#f56c6c' }, areaStyle: { opacity: 0.08 } },
      { name: '净额', type: 'line', smooth: true, data: rows.map(r => r.netAmount), itemStyle: { color: '#409eff' } },
    ],
  }
})
const apAgingChart = computed(() => {
  const rows = apAgingRows.value || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['0-30天', '31-60天', '61-90天', '90天以上'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    // 1366 宽屏下供应商名较长且家数多，旋转+全画会把标签挤成一片；改横排并按空间自动抽稀，
    // 超宽标签截断（overflow:truncate 只影响轴标签，tooltip 与表格仍是全称）。
    xAxis: {
      type: 'category', data: rows.map(r => r.payeeName),
      axisLabel: { interval: 'auto', hideOverlap: true, width: 72, overflow: 'truncate' },
    },
    yAxis: { type: 'value', name: '金额' },
    series: [
      { name: '0-30天', type: 'bar', stack: 'aging', data: rows.map(r => r.d0_30), itemStyle: { color: '#67c23a' } },
      { name: '31-60天', type: 'bar', stack: 'aging', data: rows.map(r => r.d31_60), itemStyle: { color: '#e6a23c' } },
      { name: '61-90天', type: 'bar', stack: 'aging', data: rows.map(r => r.d61_90), itemStyle: { color: '#f59a23' } },
      { name: '90天以上', type: 'bar', stack: 'aging', data: rows.map(r => r.d90_plus), itemStyle: { color: '#f56c6c' } },
    ],
  }
})
const taxBurdenChart = computed(() => {
  const rows = taxRows.value || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['进项税额', '销项税额', '应纳税额'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.period) },
    yAxis: { type: 'value', name: '税额' },
    series: [
      { name: '进项税额', type: 'bar', data: rows.map(r => r.inputTax), itemStyle: { color: '#67c23a' } },
      { name: '销项税额', type: 'bar', data: rows.map(r => r.outputTax), itemStyle: { color: '#f56c6c' } },
      { name: '应纳税额', type: 'bar', data: rows.map(r => r.netTax), itemStyle: { color: '#409eff' } },
    ],
  }
})
// 税负率＝应纳税额 / 税基（销项不含税金额）。分母为 0 时无从计算，显式「—」而非 0%。
function taxBurdenRate(row) {
  const base = Number(row?.taxBase || 0)
  if (!base) return '—'
  return ((Number(row?.netTax || 0) / base) * 100).toFixed(2) + '%'
}
const qualityChart = computed(() => {
  const rows = qualityRows.value || []
  return {
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].name}<br/>合格率: ${p[0].value}%` },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => checkSamplingLabel(r.checkType)), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '合格率(%)', max: 100 },
    series: [{
      type: 'bar', barMaxWidth: 36,
      data: rows.map(r => ({ value: r.passRate, itemStyle: { color: r.passRate >= 95 ? '#67c23a' : '#f56c6c' } })),
      label: { show: true, position: 'top', formatter: '{c}%' },
      markLine: { silent: true, data: [{ yAxis: 95, lineStyle: { color: '#67c23a', type: 'dashed' } }] },
    }],
  }
})
const budgetAlertChart = computed(() => {
  const rows = budgetAlertRows.value || []
  return {
    tooltip: { trigger: 'axis', formatter: (p) => {
      const r = rows[p[0].dataIndex]
      if (!r) return ''
      return `${r.dimKey}<br/>使用率: ${r.usageRate}%<br/>预警: ${r.alertLevel === 'OVER' ? '超支' : '预警'}`
    }},
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.dimKey), axisLabel: { rotate: 30, interval: 0 } },
    yAxis: { type: 'value', name: '使用率(%)' },
    series: [{
      type: 'bar', barMaxWidth: 32,
      data: rows.map(r => ({ value: r.usageRate, itemStyle: { color: r.usageRate >= 100 ? '#f56c6c' : '#e6a23c' } })),
      markLine: { silent: true, data: [{ yAxis: 100, lineStyle: { color: '#f56c6c', type: 'dashed' } }] },
    }],
  }
})

// 报表按需加载（波次十八）
// 规则取自报表平台对 Tab 块的既有做法——「首次加载第一个 Tab 块，后续点哪个 Tab 块即加载哪个」，
// 预加载其余 Tab 是可选项而非默认项（FineReport 帮助文档《决策报表新自适应》）。
// 旧规则一次挂载并发 26 请求、同屏渲染 50 张表；改为「公司列表＋费用概览＋当前页签」3 枚起量，
// 其余页签首次点亮时才取自己那一枚，取过的不重复请求（筛选条件变化由「查询/重置」强制重取）。
// G1（权限治理）的 403 不连坐规则原样保留：allSettled 逐枚独立，缺权只落空值兜底。
// 每枚报表＝{ get 请求, empty 兜底, put 落值 }；枚名与 el-tab-pane 的 name 一一对应，ensureTab 按名直取。
const REPORTS = {
  overview: {
    // 概览四格改指服务端真汇总（单枚 camelCase Map），不再拿分组明细数组去凑。
    // 查询失败或缺任一指标都置错误态，界面直说「本次未刷新成功」；原 ?? 0 的兜底已删。
    get: () => http.get('/tenant/dashboard/expense/overview').catch((e) => { overviewError.value = true; throw e }),
    empty: null, put: (v) => {
    if (!v) { overviewError.value = true; return }
    const cells = [v.totalAmount, v.pendingCount, v.paidAmount, v.budgetRate]
    if (cells.some((x) => x == null)) { overviewError.value = true; return }
    overviewError.value = false
    cells.forEach((x, i) => { overview[i].value = x })
  } },
  dept: { get: () => dashApi.deptRanking(), empty: [], put: (v) => { deptRows.value = v } },
  budget: { get: () => dashApi.budget(), empty: [], put: (v) => { budgetRows.value = v } },
  eff: { get: () => dashApi.approvalEfficiency(), empty: [], put: (v) => { effRows.value = v } },
  loan: { get: () => reportApi.loanBalance(), empty: [], put: (v) => { loanRows.value = v } },
  contract: { get: () => reportApi.contractSummary(), empty: [], put: (v) => { contractRows.value = v } },
  ops: { get: () => reportApi.opsStatistics(), empty: [], put: (v) => { opsRows.value = v } },
  summary: { get: () => reportApi.expenseSummary({ year: sumYear.value, month: sumMonth.value || undefined }), empty: [], put: (v) => { sumRows.value = v } },
  trend: { get: () => reportApi.monthlyTrend(), empty: [], put: (v) => { trendRows.value = v } },
  composition: { get: () => reportApi.expenseComposition(compItem.value || undefined), empty: [], put: (v) => { compRows.value = v } },
  detail: { get: () => reportApi.expenseDetail(detailKw.value), empty: [], put: (v) => { detailRows.value = v } },
  apAging: { get: () => reportApi.apAging(), empty: [], put: (v) => { apAgingRows.value = v } },
  arAging: { get: () => reportApi.arAging(), empty: [], put: (v) => { arAgingRows.value = v } },
  cashFlow: { get: () => reportApi.cashFlowSummary(), empty: [], put: (v) => { cashFlowRows.value = v } },
  bankBalance: { get: () => reportApi.bankBalance(), empty: [], put: (v) => { bankBalanceRows.value = v } },
  supplierPay: { get: () => reportApi.supplierPayment(), empty: [], put: (v) => { supplierPayRows.value = v } },
  inputInvoice: { get: () => reportApi.inputInvoiceStats(), empty: [], put: (v) => { inputInvRows.value = v } },
  outputInvoice: { get: () => reportApi.outputInvoiceStats(), empty: [], put: (v) => { outputInvRows.value = v } },
  taxBurden: { get: () => reportApi.taxBurdenAnalysis(), empty: [], put: (v) => { taxRows.value = v } },
  opsEff: { get: () => reportApi.opsEfficiency(), empty: [], put: (v) => { opsEffRows.value = v } },
  quality: { get: () => reportApi.qualityPassRate(), empty: [], put: (v) => { qualityRows.value = v } },
  image: { get: () => reportApi.imageStatistics(), empty: [], put: (v) => { imageRows.value = v } },
  contractExec: { get: () => reportApi.contractExecution(), empty: [], put: (v) => { contractExecRows.value = v } },
  budgetAlert: { get: () => reportApi.budgetOverrunAlert(), empty: [], put: (v) => { budgetAlertRows.value = v } },
  loanOverdue: { get: () => reportApi.loanOverdue(), empty: [], put: (v) => { loanOverdueRows.value = v } },
  voucherStats: { get: () => reportApi.voucherStats(), empty: [], put: (v) => { voucherRows.value = v } },
}
const fetchedReports = new Set()
/** 权限类失败（HTTP 403 / 业务码 4030）：拦截器已出提示，页内不再补弹，空值兜底即可 */
function isPermErr(e) { return e?.code === 4030 || e?.status === 403 }

async function fetchReports(names, force) {
  const todo = names.filter((n) => REPORTS[n] && (force || !fetchedReports.has(n)))
  if (!todo.length) return
  loading.value = true
  try {
    const results = await Promise.allSettled(todo.map((n) => REPORTS[n].get()))
    results.forEach((r, i) => {
      const spec = REPORTS[todo[i]]
      if (r.status === 'fulfilled') { spec.put(r.value ?? spec.empty); fetchedReports.add(todo[i]); return }
      // 缺权＝该域本就不该看到这张表：兜底并标记已取，避免每次点亮重放同一记 403
      if (isPermErr(r.reason)) { spec.put(spec.empty); fetchedReports.add(todo[i]) }
      // 其余失败（超时/服务异常）不落标记：重新点亮该页签即重试
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 页签点亮即取该枚（已取过不重复请求） */
function ensureTab(name) { if (REPORTS[name]) fetchReports([name], false) }
watch(tab, (v) => ensureTab(v))

// 「查询/重置」按钮＝条件已变，强制重取该枚（绕过 fetchedReports 去重）
async function loadSummary() {
  // 同期加的第一道查询校验：费用发生汇总按年度查询，年度为空即查询＝跨年全表扫描，
  // 与报表平台「参数界面的不能为空校验需要在查询按钮中设置」同一规则（先拦再查，不放行空参查询）。
  if (!sumYear.value) { ElMessage.warning('请先选择年度后再查询'); return }
  await fetchReports(['summary'], true)
}
async function loadComposition() { await fetchReports(['composition'], true) }
async function loadDetail() { await fetchReports(['detail'], true) }

// 导出改走原始 fetch 直取二进制——http 通道会把非 JSON 响应按 code!==0 业务错拒绝，
// 且无 blob 绕过；xlsx 由后端返回，失败时按 JSON 错误体提示。
async function exp(kind, name) {
  const path = kind.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase()
  try {
    const resp = await fetch(`/api/tenant/report/export/${path}`, {
      headers: store.token ? { Authorization: `Bearer ${store.token}` } : {},
    })
    const ct = resp.headers.get('content-type') || ''
    if (!resp.ok || ct.includes('application/json')) {
      let msg = `导出失败(${resp.status})`
      try { const body = await resp.json(); msg = body.message || body.msg || msg } catch { /* 非 JSON 错误体 */ }
      throw new Error(msg)
    }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `${name}_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click(); URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${name}`)
  } catch (e) { ElMessage.error(e.message || '导出失败') }
}

onMounted(() => {
  // 加载公司列表
  orgApi.companyTree().then((tree) => {
    const flat = []
    const walk = (arr) => { arr.forEach((n) => { if (n) { flat.push({ label: n.companyName, value: n.id }); if (n.children?.length) walk(n.children) } }) }
    walk(tree || [])
    companies.value = flat
  }).catch(() => {})
  // 首屏只发「公司列表＋费用概览＋当前页签」，其余 24 枚页签点亮时才取（见 REPORTS/ensureTab）
  fetchReports(['overview'], false)
  ensureTab(tab.value)
})

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
  a.download = `Reports_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
/* 概览格在「查询中／本次未刷新成功」两态下的占位样式，与 el-statistic 同排不跳版 */
.overview-missing { padding: 0; }
.overview-missing-label { font-size: 14px; color: #909399; line-height: 24px; }
.overview-missing-value { margin-top: 8px; font-size: 18px; font-weight: 500; color: #f56c6c; }
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
