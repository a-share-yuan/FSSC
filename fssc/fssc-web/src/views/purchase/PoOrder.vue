<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('purchase.poOrder.Purchase') }}</b>
        <el-button type="primary" size="small" @click="openCreate()">{{ $t('purchase.poOrder.New') }}</el-button>
        <el-input v-model="q.keyword" :placeholder="$t('purchase.poOrder.Order')" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="q.status" :placeholder="$t('common.status')" clearable style="width: 120px" @change="load">
          <el-option :label="$t('purchase.poOrder.Draft')" value="DRAFT" />
          <el-option :label="$t('purchase.poOrder.Running')" value="OPEN" />
          <el-option :label="$t('purchase.poOrder.Approved')" value="APPROVED" />
          <el-option :label="$t('purchase.poOrder.Rejected')" value="REJECTED" />
          <el-option :label="$t('purchase.poOrder.Invoiced')" value="INVOICED" />
          <el-option :label="$t('purchase.poOrder.Paid')" value="PAID" />
          <el-option :label="$t('purchase.poOrder.Closed')" value="CLOSED" />
          <el-option :label="$t('purchase.poOrder.Archived')" value="ARCHIVED" />
          <el-option :label="$t('purchase.poOrder.Voided')" value="VOID" />
        </el-select>
        <el-button type="primary" size="small" @click="load">{{ $t('common.search') }}</el-button>
      <el-button type="info" size="small" @click="exportCsv">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 12px">采购订单 → 收货 → 三单匹配 → 发票校验 →{{ $t('purchase.poOrder.Payment') }}</span>
      </div>
    </template>

    <!-- 无查看权限时显式无权限态（4030 不得被吞成「没数据」空表） -->
    <el-empty v-if="!canList" description="当前账号无采购订单查看权限，请联系管理员开通" />
    <SkeletonList v-if="loading && canList" :rows="8" :cols="5" />
    <el-table v-else-if="canList" :data="rows" size="small" stripe max-height="520" border @selection-change="handleSelectionChange" @row-dblclick="viewDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="poNo" label="订单号" width="150" fixed="left"  sortable />
      <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column label="币种" width="70" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.currency || 'CNY' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderDate" label="下单日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
      <el-table-column prop="deliveryDate" label="交货日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
      <el-table-column label="订单金额" width="120" align="right">
        <template #default="{ row }">
          <span style="font-weight:600">{{ fmt(row.amount) }}</span>
          <span style="color:#909399; font-size:11px; margin-left:2px">{{ row.currency || 'CNY' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="累计收货" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.receivedAmount) }}</template>
      </el-table-column>
      <el-table-column label="剩余可收" width="110" align="right">
        <template #default="{ row }">
          <span :style="{ color: Number(row.amount || 0) - Number(row.receivedAmount || 0) < 0 ? '#f56c6c' : '#67c23a' }">
            {{ fmt(Number(row.amount || 0) - Number(row.receivedAmount || 0)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="paymentTerms" label="付款条件" width="120" show-overflow-tooltip  sortable />
      <el-table-column prop="buyerName" label="采购员" width="90"  sortable />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="poListTagType(row.status)" size="small">{{ poListLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <!-- 批次1 状态门控：编辑/提交仅草稿；驳回单走「编辑重提」回退草稿后重报 -->
          <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openCreate(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="submitOrder(row)">提交审批</el-button>
          <el-button v-if="row.status === 'REJECTED'" link type="warning" size="small" @click="returnToEdit(row)">编辑重提</el-button>
          <el-button link type="info" size="small" @click="viewDetail(row)">详情</el-button>
          <!-- 删除：仅草稿(DRAFT)且无下游关联可删，逻辑删除 -->
          <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="removeOrder(row)">删除</el-button>
          <!-- 作废：非最终状态（含已驳回）可作废，需填写作废原因；与后端 voidOrder 同一规则 -->
          <el-button v-if="['DRAFT','OPEN','APPROVED','REJECTED'].includes(row.status)" link type="danger" size="small" @click="voidOrder(row)">作废</el-button>
          <!-- 最终状态机人工环节：「权限码 + 状态」双重门控，状态规则同 PurchaseService.terminateOrder
               （非 VOID 且非 PAID/CLOSED/ARCHIVED）。与「作废」并列可见：作废拦在下游收货/付款数据上，
               那种单改走「终止」，谁能点由后端状态前置兜底。 -->
          <el-button v-if="['OPEN','APPROVED','REJECTED','INVOICED'].includes(row.status) && hasPerm('po:order:update')" link type="danger" size="small" @click="terminateOrderRow(row)">终止</el-button>
          <!-- 恢复：终止/作废(VOID)后误操作的回退通道，回到 DRAFT 重新提交审批；已有收货金额的单不给恢复 -->
          <el-button v-if="row.status === 'VOID' && hasPerm('po:order:update')" link type="success" size="small" @click="restoreOrderRow(row)">{{ $t('purchase.poOrder.Restore') }}</el-button>
          <!-- 归档：仅已关闭(CLOSED)可归档，落最终状态 ARCHIVED 后不可再变更；规则同 PurchaseService.archiveOrder -->
          <el-button v-if="row.status === 'CLOSED' && hasPerm('po:order:update')" link type="warning" size="small" @click="archiveOrderRow(row)">归档</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <!-- 新增/编辑弹窗：取消/Esc/点遮罩统一走脏值守卫，长表单不因误触整单丢失 -->
    <DirtyGuardDialog ref="dlgRef" v-model="dlg" :dirty="isDlgDirty" :title="form.id ? '编辑采购订单' : '新增采购订单'" width="min(1040px, 94vw)">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="100px" size="small">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <!-- 订单号＝系统取号（后端留空自动生成 PO+年月+序号），按治理规则 Q1 人工只读字段不加红星 -->
            <el-form-item label="订单号">
              <el-input v-model="form.poNo" placeholder="留空则由系统自动取号" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下单日期" prop="orderDate">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" :default-value="todayDate" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select
                v-model="form.supplierId"
                filterable
                style="width: 100%"
                placeholder="搜索选择供应商"
                no-match-text="无匹配供应商，请从下拉列表中选择"
                no-data-text="供应商主数据为空，请先在主数据中维护"
                @change="(id) => { const s = suppliers.find(x => x.id === id); if (s) form.supplierName = s.supplierName }"
              >
                <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联合同">
              <el-select
                v-model="form.contractId"
                filterable
                clearable
                style="width: 100%"
                :placeholder="contractsDenied ? '当前账号无合同查看权限，可留空' : '选择框架协议合同（可选）'"
                :no-data-text="contractsDenied ? '当前账号无合同查看权限' : '无可选合同（仅列已审批/生效）'"
              >
                <el-option v-for="c in contracts" :key="c.id" :label="c.contractNo + ' ' + c.contractName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 采购订单关联在建工程（CIP 项目）：后端契约 /tenant/cip/list 未部署(404)时隐藏本字段 -->
        <el-row v-if="cipAvailable" :gutter="16">
          <el-col :span="12">
            <el-form-item label="在建工程项目">
              <template #label>在建工程项目<FieldTip text="仅显示在建项目；关联后收货/发票可按项目归集" /></template>
              <el-select v-model="form.cipId" filterable clearable style="width: 100%" placeholder="关联在建工程项目（可选）">
                <el-option v-for="c in cipOptions" :key="c.id" :label="cipLabel(c)" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">商务条件</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="订单金额" prop="amount">
              <template #label>订单金额<FieldTip text="须与行项目合计一致（尾差 ≤ 0.01），否则保存会被拒；只录表头金额、不录行项目时不做此校验" /></template>
              <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" :class="{ 'fld-error': amountMismatch }" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />






              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交货日期">
              <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" :default-value="todayDate" style="width: 100%" placeholder="预计交货日期" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="付款条件">
              <el-select v-model="form.paymentTerms" filterable allow-create style="width: 100%" placeholder="选择或输入付款条件">
                <el-option label="货到付款" value="货到付款" />
                <el-option label="30天净额" value="30天净额" />
                <el-option label="60天净额" value="60天净额" />
                <el-option label="90天净额" value="90天净额" />
                <el-option label="预付30%" value="预付30%" />
                <el-option label="预付50%" value="预付50%" />
                <el-option label="分期付款" value="分期付款" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <template #label>状态<FieldTip :text="statusHint" /></template>
              <el-select v-model="form.status" :disabled="!!form.id" style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="执行中" value="OPEN" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">联系信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="采购员">
              <el-input v-model="form.buyerName" placeholder="采购负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" placeholder="联系方式" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="法人主体" prop="companyId">
              <!-- 字段说明从常驻灰字改为标签旁的 ⓘ：不占表单行高，悬浮/键盘聚焦即显示 -->
              <template #label>法人主体<FieldTip text="决定入哪本账与后续三单匹配范围；公司账号建单时自动带入本公司" /></template>
              <el-tree-select
                v-model="form.companyId"
                :data="companyTree"
                :props="{ label: 'companyName', value: 'id', children: 'children' }"
                check-strictly
                placeholder="选择公司"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="交货地址">
          <el-input v-model="form.deliveryAddress" placeholder="详细交货地址" />
        </el-form-item>

        <el-divider content-position="left">行项目明细</el-divider>
        <el-table
          ref="lineTableRef"
          :data="formLines"
          size="small"
          border
          style="margin-bottom: 8px"
          max-height="44vh"
          empty-text="暂无行项目，点下方【+ 添加行】"
        >
          <el-table-column label="行号" width="50" align="center">
            <template #default="{ $index }">
              <span :class="{ 'row-err': rowHasIssue($index) }" :title="rowIssueTitle($index)">
                {{ $index + 1 }}<template v-if="rowHasIssue($index)"> </template>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="物料编码" width="160">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.materialCode"
                size="small"
                filterable
                remote
                reserve-keyword
                allow-create
                default-first-option
                placeholder="搜索物料编码/名称"
                no-match-text="未匹配到物料，可换关键词或直接录入编码"
                :remote-method="searchMaterials"
                :loading="materialSearching"
                style="width: 100%"
                @change="(val) => { const m = materialOptions.find(x => (x.itemCode || x.code) === val); if (m) { row.materialName = m.itemName || m.name || ''; if (m.unit || m.unitName) row.unit = m.unit || m.unitName } onLineEdit($index) }"
              >
                <el-option
                  v-for="m in materialOptions"
                  :key="m.code || m.id"
                  :label="(m.itemCode || m.code || '') + ' ' + (m.itemName || m.name || '')"
                  :value="m.itemCode || m.code || ''"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="150">
            <template #header><span class="th-req">*</span>物料名称</template>
            <template #default="{ row, $index }">
              <el-input
                v-model="row.materialName"
                size="small"
                placeholder="物料/服务名称（勿填金额）"
                :data-line="$index"
                data-field="materialName"
                :class="cellClass($index, 'materialName')"
                :title="cellTip($index, 'materialName')"
                @input="onLineEdit($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="92">
            <template #header><span class="th-req">*</span>数量</template>
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :controls="false"
                size="small"
                placeholder="须 > 0"
                style="width: 100%"
                :data-line="$index"
                data-field="quantity"
                :class="cellClass($index, 'quantity')"
                :title="cellTip($index, 'quantity')"
                @change="calcLineAmt(row); onLineEdit($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="86">
            <template #header><span class="th-req">*</span>单位</template>
            <template #default="{ row, $index }">
              <el-select
                v-model="row.unit"
                size="small"
                filterable
                allow-create
                default-first-option
                placeholder="必填"
                style="width: 100%"
                :data-line="$index"
                data-field="unit"
                :class="cellClass($index, 'unit')"
                :title="cellTip($index, 'unit')"
                @change="onLineEdit($index)"
              >
                <el-option label="个" value="个" /><el-option label="台" value="台" /><el-option label="套" value="套" /><el-option label="件" value="件" /><el-option label="批" value="批" /><el-option label="米" value="米" /><el-option label="吨" value="吨" /><el-option label="kg" value="kg" /><el-option label="箱" value="箱" /><el-option label="包" value="包" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="102">
            <template #header><span class="th-req">*</span>单价</template>
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.unitPrice"
                :min="0"
                :precision="4"
                :controls="false"
                size="small"
                placeholder="须 > 0"
                style="width: 100%"
                :data-line="$index"
                data-field="unitPrice"
                :class="cellClass($index, 'unitPrice')"
                :title="cellTip($index, 'unitPrice')"
                @change="calcLineAmt(row); onLineEdit($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row, $index }">
              <span :style="{ fontWeight: 600, color: cellTip($index, 'amount') ? 'var(--el-color-danger)' : '' }">{{ fmt(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeLine($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="line-bar">
          <el-button size="small" type="primary" plain @click="addLine">+ 添加行</el-button>
          <!-- 行项目校验结果：一次性列出全部问题，并允许点条目定位到具体单元格 -->
          <span v-if="lineIssues.length" class="line-bar__err">
             行项目有 {{ lineIssues.length }} 处待修正：
            <span v-for="(it, k) in lineIssueBrief" :key="k" class="line-bar__item">
              <a class="line-bar__link" @click="focusIssue(it)">{{ it.msg }}</a><span v-if="k < lineIssueBrief.length - 1">；</span>
            </span>
            <span v-if="lineIssues.length > lineIssueBrief.length"> 等</span>
          </span>
          <span v-else-if="amountMismatch" class="line-bar__err">
             行项目合计 {{ fmt(lineTotal) }} 与订单金额 {{ fmt(Number(form.amount || 0)) }} 不一致，保存将被拒
          </span>
          <span class="line-bar__sum">
            行项目合计：<b>{{ fmt(lineTotal) }}</b> {{ form.currency || 'CNY' }}
            <el-button v-if="lineTotal > 0 && amountMismatch" link type="primary" size="small" @click="fillAmountFromLines">按合计回填订单金额</el-button>
          </span>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="订单备注说明" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgRef?.requestClose()">取消</el-button>
        <el-button type="primary" @click="save(false)">保存</el-button>
        <!-- 弹窗内补【保存并提交】，免去"关闭弹窗→回列表找行→点提交"三步跳转 -->
        <el-button type="success" @click="save(true)">保存并提交</el-button>
        <div style="flex:1"></div>
        <span class="footer-hint">保存＝草稿，不进审批；保存并提交＝直接进入审批流</span>
      </template>
    </DirtyGuardDialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="dlgDetail" title="采购订单详情" width="780px">
      <el-descriptions :column="2" border size="small" v-if="detailRow">
        <el-descriptions-item label="订单号">{{ detailRow.poNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === 'OPEN' ? 'success' : statusType(detailRow.status)" size="small">{{ detailRow.status === 'OPEN' ? '执行中' : statusLabel(detailRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailRow.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="下单日期">{{ detailRow.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ fmt(detailRow.amount) }} {{ detailRow.currency || 'CNY' }}</el-descriptions-item>
        <el-descriptions-item label="累计收货">{{ fmt(detailRow.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="剩余可收">{{ fmt(Number(detailRow.amount || 0) - Number(detailRow.receivedAmount || 0)) }}</el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detailRow.deliveryDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="付款条件">{{ detailRow.paymentTerms || '—' }}</el-descriptions-item>
        <el-descriptions-item label="采购员">{{ detailRow.buyerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailRow.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailCipLabel" label="在建工程项目">{{ detailCipLabel }}</el-descriptions-item>
        <el-descriptions-item label="交货地址" :span="2">{{ detailRow.deliveryAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left" v-if="detailLines.length">行项目明细</el-divider>
      <el-table :data="detailLines" size="small" border v-if="detailLines.length" max-height="300" style="margin-bottom: 12px" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
        <el-table-column label="行号" width="55" align="center" type="index" />
        <el-table-column prop="materialCode" label="物料编码" width="120"  sortable />
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable />
        <el-table-column prop="unit" label="单位" width="70" align="center"  sortable />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right"  sortable />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600">{{ fmt(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dlgDetail = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="PURCHASE_ORDER_REVIEW" />
</template>

<script setup>

const { rules: formRules } = useFormRules('PO_ORDER')
import { onMounted, reactive, ref, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { purchaseApi, supplierApi, orgApi, masterApi, cipFlowApi, contractApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import FieldTip from '../../components/common/FieldTip.vue'
import DirtyGuardDialog from '../../components/common/DirtyGuardDialog.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime, todayLocal } from '@/utils/format'
import { useCurrency } from '@/composables/useCurrency'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+s': () => { try { save() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
// 工作台「我的单据」深链：?kw=订单号 直接定位该行（台账页自带行内「编辑重提」）
const route = useRoute()
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

/** 列表权限预判（与后端 @RequiresPerm("po:order:list") 同码）——无权不打接口、渲染无权限态 */
const canList = hasPerm('po:order:list')
const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const suppliers = ref([])
const contracts = ref([])
const companyTree = ref([])
const dlg = ref(false)
const dlgDetail = ref(false)
const detailRow = ref(null)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: route.query.kw ? String(route.query.kw) : '', status: '' })
// 列表显示规则（业务确认）：「执行中」只对应状态 OPEN；
// 其余状态一律走 billStatus 统一映射（APPROVED=已审批、DRAFT=草稿、CLOSED=已关闭、VOID=已作废），
// 不再用行内三元兜底把未列举状态误渲染为「已关闭」。
function poListLabel(s) {
  if (s === 'OPEN') return '执行中'
  return statusLabel(s)
}
function poListTagType(s) {
  if (s === 'OPEN') return 'success'
  return statusType(s)
}
const form = reactive({})
const formRef = ref(null)
const dlgRef = ref(null)
const lineTableRef = ref(null)
const { submit: guardSubmit } = useSubmitGuard(formRef)
const { autofill, saveFormHistory } = useAutoFill()
const formLines = ref([])
/**
 * 行项目校验结果：[{ idx, field, msg }]。
 * 收集全部问题而不是遇错即返回——多行多格同时出错时，用户只需看一遍就能改完，
 * 不必「改一处→再提交→再被拦」地反复试。field 用于把红框落到具体单元格。
 */
const lineIssues = ref([])
const lineIssueBrief = computed(() => lineIssues.value.slice(0, 3))
function lineIssueOf(idx, field) {
  return lineIssues.value.find(x => x.idx === idx && x.field === field)
}
function cellClass(idx, field) {
  return lineIssueOf(idx, field) ? 'fld-error' : ''
}
function cellTip(idx, field) {
  const it = lineIssueOf(idx, field)
  return it ? it.msg : ''
}
function rowHasIssue(idx) {
  return lineIssues.value.some(x => x.idx === idx)
}
function rowIssueTitle(idx) {
  return lineIssues.value.filter(x => x.idx === idx).map(x => x.msg).join('；')
}
/** 日期面板默认定位：无值时用当天，避免沿用上次浏览月份 */
const todayDate = new Date()
/** 表单快照：用于判断弹窗是否已有未保存内容 */
let dlgSnapshot = ''
const isDlgDirty = () => dlg.value && JSON.stringify([form, formLines.value]) !== dlgSnapshot
const statusHint = computed(() => (form.id
  ? '草稿单不发起审批，可直接删除；执行中保存即发起订单审批。已保存订单的状态由审批/收货/付款/结束等操作推进，编辑单据不改变状态'
  : '草稿单不发起审批，可直接删除；执行中保存即发起订单审批'))
const detailLines = ref([])
const materialOptions = ref([])
const materialSearching = ref(false)

// 采购订单关联在建工程（CIP 项目）
// 数据源 GET /api/tenant/cip/list?status=IN_PROGRESS（后端并行部署中）：
// 404/加载失败/空列表时隐藏「CIP 项目」字段，不弹错误、不影响新增/编辑
const cipAvailable = ref(false)
const cipOptions = ref([])

function cipLabel(c) {
  if (!c) return ''
  return ((c.cipCode || '') + ' ' + (c.cipName || '')).trim() || String(c.id ?? '')
}

async function loadCip() {
  try {
    const d = await cipFlowApi.inProgress()
    const list = Array.isArray(d) ? d : (d?.list || d?.records || [])
    if (list.length) {
      cipOptions.value = list
      cipAvailable.value = true
    }
  } catch (e) { cipAvailable.value = false }
}

const detailCipLabel = computed(() => {
  const row = detailRow.value
  if (!row || !row.cipId) return ''
  const c = cipOptions.value.find((x) => x.id === row.cipId)
  return (c && cipLabel(c)) || row.cipName || ('CIP#' + row.cipId)
})

const lineTotal = computed(() => formLines.value.reduce((s, r) => s + Number(r.amount || 0), 0))

// 表头金额与行项目合计勾稽（容差 0.01，与后端 assertHeaderLineAmountConsistent 同一规则）
// 无行项目时不判（采购订单允许只录表头金额）
const amountMismatch = computed(() => {
  const header = Number(form.value?.amount || form.amount || 0)
  const sum = Number(lineTotal.value || 0)
  return sum > 0 && Math.abs(header - sum) > 0.01
})

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

function addLine() {
  // 单价留空而非 0：默认 0 会让「未填」和「填了 0」长得一样，用户以为已经填好
  formLines.value.push({ materialCode: '', materialName: '', quantity: 1, unit: '', unitPrice: null, amount: 0 })
  nextTick(() => {
    const t = lineTableRef.value
    if (t?.setScrollTop) t.setScrollTop(1e6)
  })
}

function removeLine(idx) {
  formLines.value.splice(idx, 1)
  lineIssues.value = []
}

/**
 * 判断一行是否仍是 addLine() 追加时的原样空行。
 * 「全空行忽略」若按 quantity!==0 认定已填写，空行的默认数量恰是 1 ⇒ 自动追加的空行
 * 被当成有效行，行项目校验报「物料名称必填」拦下【保存并提交】（save 的 payload 又会把该行丢掉），
 * 用户看到的就是"点了没反应"。所以判据取「与默认值逐字段一致」；
 * 用户只要动过任一字段（含只填了数量），仍按未填完的行走红字提示，不静默丢数据。
 */
function isBlankLine(l) {
  return !l.materialName && !l.materialCode && !l.unit
    && !Number(l.unitPrice || 0) && !Number(l.amount || 0) && Number(l.quantity || 0) === 1
}

function calcLineAmt(row) {
  row.amount = Number(row.quantity || 0) * Number(row.unitPrice || 0)
}

/** 行内改动：提交过一次被拦后，标记随编辑实时刷新，改对了红框当场消失 */
function onLineEdit() {
  if (lineIssues.value.length) lineIssues.value = collectLineIssues()
}

/** 表头金额按行合计回填（勾稽不一致时的一键修复） */
function fillAmountFromLines() {
  form.amount = Number(Number(lineTotal.value).toFixed(2))
}

async function searchMaterials(query) {
  if (!query) { materialOptions.value = []; return }
  materialSearching.value = true
  try {
    // 数据源＝物料主数据（md_inventory），不再拿费用项目冒充物料编码
    materialOptions.value = (await purchaseApi.materialOptions(query)) || []
  } catch (e) { materialOptions.value = [] }
  finally { materialSearching.value = false }
}

function onMaterialSelect(row, itemId) {
  const m = materialOptions.value.find(x => x.id === itemId)
  if (m) {
    row.materialCode = m.itemCode || m.code || ''
    row.materialName = m.itemName || m.name || ''
  }
}

async function load() {
  if (!canList) return // 无列表权限就不发请求，避免 4030 被吞成空表
  loading.value = true
  try {
    const r = await purchaseApi.orderPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate(row) {
  Object.assign(form, row ? { ...row } : {
    poNo: '', supplierId: null, supplierName: '', contractId: null, cipId: null,
    orderDate: todayLocal(), amount: 0,
    currency: 'CNY', deliveryDate: '', paymentTerms: '', buyerName: '',
    contactPhone: '', deliveryAddress: '', companyId: store.user?.companyId ?? null,
    status: 'DRAFT', remark: '',
  })
  formLines.value = row && row.lines ? row.lines.map(l => ({ ...l })) : []
  if (!formLines.value.length) addLine()
  lineIssues.value = []
  dlg.value = true
  // 开场快照：与当前值不同即视为「有未保存内容」，关窗前要二次确认
  nextTick(() => { dlgSnapshot = JSON.stringify([form, formLines.value]) })
}

function viewDetail(row) {
  detailRow.value = row
  detailLines.value = row && row.lines ? row.lines : []
  dlgDetail.value = true
}

/**
 * 行项目逐行校验（与后端 PurchaseService.assertOrderLine 同一规则）。
 * guardSubmit 只能校验带 prop 的表单项，行项目明细不在 el-form 内，
 * 历史上从未被任何一层校验过——把金额填进「物料名称」、数量/单价留默认值时
 * 照样写入数据库，直到三单匹配才暴露。
 * 本函数只负责**收集**问题，不弹窗、不中断：一次列出全部错误，
 * 由调用方决定提示方式，并把红框落到具体单元格。
 */
function collectLineIssues() {
  const issues = []
  formLines.value.forEach((l, idx) => {
    if (isBlankLine(l)) return // 全空行忽略（模板新增的空行不算填写内容）
    const pos = `第 ${idx + 1} 行`
    const name = String(l.materialName || '').trim()
    if (!name) {
      issues.push({ idx, field: 'materialName', msg: `${pos}物料名称必填（只有物料编码不够）` })
    } else if (/^[0-9][0-9,]*(\.[0-9]+)?$/.test(name)) {
      issues.push({ idx, field: 'materialName', msg: `${pos}物料名称是纯数字，请填实际品名` })
    }
    if (!(Number(l.quantity) > 0)) {
      issues.push({ idx, field: 'quantity', msg: `${pos}数量必须大于 0` })
    }
    if (!(Number(l.unitPrice) > 0)) {
      issues.push({ idx, field: 'unitPrice', msg: `${pos}单价必须大于 0` })
    }
    // 单位是门户发货（ASN 发运数量）与三单匹配（收货数量换算）的取数依据，
    // 空值会让门户「单位」列恒空、数量口径无法核对。
    if (!String(l.unit || '').trim()) {
      issues.push({ idx, field: 'unit', msg: `${pos}单位必填` })
    }
    const expect = Number(l.quantity || 0) * Number(l.unitPrice || 0)
    const actual = Number(l.amount || 0)
    if (actual > 0 && expect > 0 && Math.abs(expect - actual) > 0.01) {
      issues.push({ idx, field: 'amount', msg: `${pos}量价勾稽不符（应为 ${expect.toFixed(2)}）` })
    }
  })
  return issues
}

/** 把视口滚到出错单元格并聚焦，配合红框形成「错在哪」的闭环 */
async function focusIssue(issue) {
  await nextTick()
  if (!issue) return
  // 弹窗内容经 Teleport 挂到 body，组件 $el 未必是真实容器 ⇒ 取不到就退回 document
  const el = dlgRef.value?.$el
  const root = el && typeof el.querySelector === 'function' ? el : document
  const anchor = root.querySelector(`[data-line="${issue.idx}"][data-field="${issue.field}"]`)
  const fallback = root.querySelector('.fld-error')
  const box = anchor?.closest?.('.el-input, .el-select, .el-input-number') || anchor || fallback
  if (!box) return
  box.scrollIntoView({ block: 'center', behavior: 'smooth' })
  const input = (anchor && anchor.matches?.('input') ? anchor : null)
    || box.querySelector?.('input')
  try { input?.focus({ preventScroll: true }) } catch { /* 只读控件忽略 */ }
}

/**
 * 行项目校验入口。
 * @returns {Promise<boolean>} 通过为 true；失败已写入 lineIssues 并完成提示与定位
 */
async function validateLines() {
  const issues = collectLineIssues()
  lineIssues.value = issues
  if (issues.length) {
    ElMessage.warning(issues[0].msg)
    await focusIssue(issues[0])
    return false
  }
  // 表头与行合计勾稽（与后端 assertHeaderLineAmountConsistent 同一规则）
  const sum = formLines.value.filter(l => !isBlankLine(l)).reduce((s, l) => s + Number(l.amount || 0), 0)
  const header = Number(form.amount || 0)
  if (sum > 0 && Math.abs(header - sum) > 0.01) {
    ElMessage.warning(`订单金额(${header.toFixed(2)}) ≠ 行项目合计(${sum.toFixed(2)})，请检查行明细`)
    return false
  }
  return true
}

/**
 * 保存 / 保存并提交。
 * 历史设计把「提交审批」只放在列表行内（PoOrder.vue 行操作列），弹窗底部只有【保存】，
 * 新手按手册「→【保存】→【提交】」操作时会在弹窗里找不到提交键，误以为功能缺失
 * 此处补【保存并提交】：一次点击完成「保存 → 提交审批」，
 * 省掉关闭弹窗、回列表找行、再点一次的三步跳转。
 * 编辑态（form.id 已存在且已是草稿）同样允许——驳回重提场景也需要一键重提。
 */
async function save(thenSubmit = false) {
  saveFormHistory('PO_ORDER', form.value || form)

  await guardSubmit(async () => {
    if (!(await validateLines())) return   // 行项目校验，先于请求发出
    try {
      const payload = { ...form, lines: formLines.value.filter(l => l.materialName || l.materialCode) }
      let saved
      if (form.id) saved = await purchaseApi.orderUpdate(payload)
      else saved = await purchaseApi.orderCreate(payload)

      if (!thenSubmit) {
        ElMessage.success(t('useCrud.main.Saved'))
        dlg.value = false
        load()
        return
      }

      // 保存并提交：create 接口 Result<Long> 经 http 拦截器解包后直接是 id；
      //              update 返回 void，故编辑态用回原 form.id。
      const id = form.id ?? (typeof saved === 'number' ? saved : saved?.id ?? saved?.data?.id ?? null)
      if (!id) {
        ElMessage.warning(t('purchase.poOrder.Saved'))
        dlg.value = false
        load()
        return
      }
      await purchaseApi.orderSubmit(id)
      ElMessage.success(t('purchase.poOrder.Saved2'))
      dlg.value = false
      load()
    } catch (e) {
      // 提交阶段失败但草稿已写入数据库：明确告知"已保存为草稿"，避免用户重复建单
      ElMessage.error(thenSubmit && (form.id || form.poNo)
        ? `${e.message}（单据已保存为草稿，可在列表中重试【提交审批】）`
        : e.message)
    }
  })
}

// 批次1：草稿订单提交审批（DRAFT → OPEN + 起 PURCHASE_ORDER_REVIEW）
async function submitOrder(row) {
  try {
    await purchaseApi.orderSubmit(row.id)
    ElMessage.success(`订单 ${row.poNo} ${t('asset.deprBatch.Submitted')}`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 批次1：驳回订单编辑重提（REJECTED → DRAFT），随即打开编辑表单
async function returnToEdit(row) {
  try {
    await purchaseApi.orderReturnToEdit(row.id)
    await load()
    const fresh = rows.value.find((x) => x.id === row.id)
    openCreate(fresh || { ...row, status: 'DRAFT' })
    ElMessage.success(t('purchase.poOrder.Returned'))
  } catch (e) { ElMessage.error(e.message) }
}

// 删除采购订单：仅草稿(DRAFT)且无下游关联可删（后端逻辑删除）
async function removeOrder(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除草稿订单 ${row.poNo}（${row.supplierName || ''}）？删除后列表不再显示`,
      '删除采购订单',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch (_) { return }
  try {
    await purchaseApi.orderDelete(row.id)
    ElMessage.success(`订单 ${row.poNo} ${t('useCrud.main.Deleted')}`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 作废采购订单：仅 DRAFT/OPEN 等非最终状态可作废，必填作废原因（≥2字）；
// 后端会拒绝已收货/已付款订单，错误信息经 request 统一拦截器+此处 catch 原样展示（不吞错）
async function voidOrder(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认作废订单 ${row.poNo}（${row.supplierName || ''}）？作废后不可恢复`,
      '作废采购订单',
      {
        inputPlaceholder: '请填写作废原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的作废原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await purchaseApi.orderVoid({ orderId: row.id, reason })
    ElMessage.success(t('purchase.poOrder.Voided'))
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 终止采购订单（最终状态机人工环节，权限码 po:order:update）：处理已收货/已挂付款但不再履约的单，
// 与「作废」的分工由后端同一规则把关；终止原因必填（≥2字）。终止后可【恢复】回 DRAFT 重走审批。
async function terminateOrderRow(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认终止订单 ${row.poNo}（${row.supplierName || ''}）？终止后剩余未执行部分不再履约；误操作可在列表用【恢复】退回草稿`,
      '终止采购订单',
      {
        inputPlaceholder: '请填写终止原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的终止原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await purchaseApi.orderTerminate(row.id, { reason })
    ElMessage.success(t('purchase.poOrder.Terminated'))
    load()
  } catch (e) { ElMessage.error(e.message) }
}

/**
 * 恢复已终止的采购订单：VOID → DRAFT，需重新提交审批。
 * 终止同时取消了审批流实例，所以只能回草稿重走，不能直接恢复成「已审批」。
 */
async function restoreOrderRow(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      t('purchase.poOrder.AskRestore', { v1: row.poNo, v2: row.supplierName || '' }),
      t('purchase.poOrder.TtlRestore'),
      {
        inputPlaceholder: t('purchase.poOrder.RestoreReasonPh'),
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || t('purchase.poOrder.RestoreReasonRequired'),
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await purchaseApi.orderRestore(row.id, { reason })
    ElMessage.success(t('purchase.poOrder.Restored'))
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 归档采购订单（最终状态机人工环节，权限码 po:order:update）：仅已关闭(CLOSED)单可归档，落最终状态 ARCHIVED
async function archiveOrderRow(row) {
  try {
    await ElMessageBox.confirm(
      `确认归档订单 ${row.poNo}（${row.supplierName || ''}）？归档后不可再变更`,
      '归档采购订单',
      { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' },
    )
  } catch (_) { return }
  try {
    await purchaseApi.orderArchive(row.id)
    ElMessage.success(`订单 ${row.poNo} ${t('purchase.poOrder.Archived')}`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

// 关联合同下拉恒空＝contracts 声明后从未查询。ContractController.page 按设计对所有登录用户
// 开放（无 @RequiresPerm），故这里只按状态办结：草稿/审批中的合同不能作下单依据。
const contractsDenied = ref(false)
async function loadContracts() {
  try {
    const d = await contractApi.page({ pageNum: 1, pageSize: 200 })
    contracts.value = (d.list || []).filter((c) => c.status === BillStatus.APPROVED || c.status === 'EFFECTIVE')
  } catch (e) {
    contracts.value = []
    contractsDenied.value = e?.code === 4030
  }
}

onMounted(async () => {
  loadCurrency()
  autofill(form, 'PO_ORDER')
  try {
    const [s, ct] = await Promise.all([
      supplierApi.page({ pageNum: 1, pageSize: 100 }),
      orgApi.companyTree(),
    ])
    suppliers.value = s.list || []
    companyTree.value = ct || []
  } catch (e) { /* 忽略 */ }
  loadContracts() // 独立查询：合同接口失败不得连累供应商/公司下拉
  loadCip() // CIP 项目下拉数据源（失败/404 静默隐藏字段）
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'poNo', label: t('purchase.poOrder.Order2') }, { prop: 'supplierName', label: t('purchase.payPlan.Supplier') }, { prop: 'orderDate', label: t('purchase.poOrder.Order3') }, { prop: 'deliveryDate', label: t('purchase.poOrder.Delivery') }, { prop: 'paymentTerms', label: t('master.supplier.Payment') }, { prop: 'buyerName', label: t('purchase.poOrder.Buyer') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `PoOrder_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `PoOrder_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
/* footer 内说明文案靠弹性占位推到右端 */
:deep(.el-dialog__footer) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-hint {
  margin-right: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 行项目工具条：错误摘要 + 合计回填 */
.line-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
  margin-bottom: 12px;
}

.line-bar__err {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.line-bar__item {
  margin-right: 2px;
}

.line-bar__link {
  color: var(--el-color-danger);
  text-decoration: underline dotted;
  cursor: pointer;
}

.line-bar__link:hover {
  color: var(--el-color-danger-dark-2);
}

.line-bar__sum {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.line-bar__sum b {
  font-size: 15px;
  color: var(--el-color-primary);
}

/* 表头必填红星：与 el-form-item 的红星同色，取代「单位 *」这类文字标记 */
:deep(.th-req) {
  margin-right: 2px;
  color: var(--el-color-danger);
}

/* 出错单元格红框：覆盖 input / select / input-number 三种根结构 */
:deep(.fld-error .el-input__wrapper),
:deep(.fld-error .el-select__wrapper),
:deep(.fld-error.el-input .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.row-err {
  font-weight: 600;
  color: var(--el-color-danger);
}
</style>
