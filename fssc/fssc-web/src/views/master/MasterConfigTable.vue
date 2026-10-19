<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <!-- Toolbar -->
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center">
      <el-select
        v-if="!cfg.readOnly"
        v-model="currentVariantId"
        class="variant-select"
        size="small"
        placeholder="查询方案"
        clearable
        :loading="variantLoading"
        @change="selectVariant"
      >
        <el-option v-for="v in variantList" :key="v.id" :label="(v.isDefault === 1 ? '★ ' : '') + v.variantName" :value="v.id" />
      </el-select>
      <el-dropdown v-if="!cfg.readOnly" trigger="click" @command="onVariantCommand">
        <el-button size="small">方案</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="save">另存为方案</el-dropdown-item>
            <el-dropdown-item command="update" :disabled="!currentVariantId">用当前条件覆盖方案</el-dropdown-item>
            <el-dropdown-item command="default" :disabled="!currentVariantId">设为默认方案</el-dropdown-item>
            <el-dropdown-item command="delete" :disabled="!currentVariantId" divided>删除当前方案</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-input v-if="!cfg.readOnly" v-model="searchForm.keyword" placeholder="搜索" clearable style="width: 240px" @keyup.enter="load" />
      <el-button v-if="!cfg.readOnly" type="primary" @click="load">查询</el-button>
      <el-button v-if="!cfg.readOnly" @click="resetSearch">重置</el-button>
      <el-button v-if="!cfg.readOnly" type="success" @click="openCreate">新增</el-button>
      <el-button type="primary" plain @click="load">刷新</el-button>
      <span v-if="cfg.hint" style="color: #909399; font-size: 12px; margin-left: auto">{{ cfg.hint }}</span>
    </div>

    <!-- Table -->
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" border stripe v-else @selection-change="onSelChange" empty-text="暂无数据">
      <el-table-column v-if="!cfg.readOnly" type="selection" width="45" />
      <el-table-column v-for="col in cfg.columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth || 120" :align="col.align || 'left'" :sortable="col.sortable" show-overflow-tooltip>
        <!-- R5-D-032：formatter / optionLabel 的判断须在 #default 插槽作用域内求值，不得并列第二个 v-else-if 模板 -->
        <template #default="{ row }">
          <template v-if="col.formatter">
            <span v-if="col.formatter === 'money'" style="font-family: monospace">{{ fmt(row[col.prop]) }}</span>
            <el-tag v-else-if="col.formatter === 'status'" :type="row[col.prop] ? 'success' : 'info'" size="small">{{ row[col.prop] ? '启用' : '停用' }}</el-tag>
            <el-tag v-else-if="col.formatter === 'enable'" :type="(row.enabled ?? ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)) ? 'success' : 'danger'" size="small">{{ (row.enabled ?? ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)) ? '启用' : '停用' }}</el-tag>
            <span v-else :title="col.tooltip ? col.formatter(row[col.prop], row) : null">{{ col.formatter(row[col.prop], row) }}</span>
          </template>
          <template v-else-if="optionLabel(col, row) != null">{{ optionLabel(col, row) }}</template>
          <!-- R5-D-032：链尾这一支是「有行有值」的唯一保证——无 formatter 且无 select 词表的普通文本列必须落原值，缺它就是整表空白 -->
          <span v-else>{{ row[col.prop] }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!cfg.readOnly" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="cfg.api.enable" link size="small" :type="(row.enabled ?? ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)) ? 'warning' : 'success'" @click="toggleEnable(row)">{{ (row.enabled ?? ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)) ? '停用' : '启用' }}</el-button>
          <el-button link size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > pageSize" class="pager" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; load() }" />

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dlg" :title="editing.id ? '编辑' + cfg.title : '新增' + cfg.title" width="560px">
      <el-form ref="cfgFormRef" :model="editing" :rules="configRules" label-width="120px" size="small">
        <el-form-item v-for="f in cfg.formFields" :key="f.prop" :label="f.label" :prop="f.prop">
          <el-input v-if="f.type === 'text' || !f.type" v-model="editing[f.prop]" :placeholder="f.placeholder || ''" />
          <el-input-number v-else-if="f.type === 'number'" v-model="editing[f.prop]" :min="0" :precision="f.precision ?? 2" style="width: 100%" />
          <el-input v-else-if="f.type === 'textarea'" v-model="editing[f.prop]" type="textarea" :rows="f.rows || 2" />
          <el-date-picker v-else-if="f.type === 'date'" v-model="editing[f.prop]" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          <el-select v-else-if="f.type === 'select'" v-model="editing[f.prop]" filterable clearable style="width: 100%">
            <el-option v-for="o in (f.options || dynOptions[f.optionsSource] || [])" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-switch v-else-if="f.type === 'switch'" v-model="editing[f.prop]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 另存/覆盖 查询方案 -->
    <el-dialog v-model="saveDialogVisible" :title="saveDialogUpdate ? '更新查询方案' : '另存为查询方案'" width="420px">
      <el-form ref="variantFormRef" :model="saveDialogForm" :rules="variantRules" label-width="90px" size="small">
        <el-form-item label="方案名称" prop="variantName">
          <el-input v-model="saveDialogForm.variantName" maxlength="50" show-word-limit placeholder="如：只看冻结中的供应商" @keyup.enter="submitVariant" />
        </el-form-item>
      </el-form>
      <div class="variant-tip">保存的是当前页面的搜索条件，仅自己可见；设默认后每次进入本页自动套用。</div>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitVariant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { masterApi, masterCfgApi, dictApi, configApi, complianceApi, expensePolicyApi, numberApi, noticeApi, messageApi, fileApi, supplierApi, jobApi, flowApi, auditApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { auditActionLabel, docTypeLabel, billTypeLabel, msgTypeLabel, payeeTypeLabel, ruleCategoryLabel } from '@/constants/enums.js'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
import { useQueryVariant } from '../../composables/useQueryVariant'
import { maskAccount } from '@/utils/mask'

const props = defineProps({
  configKey: { type: String, required: true },
})

// ========== CONFIG REGISTRY ==========
// 列声明的 formatter 在 REGISTRY 构造时就要取到值，所以这几枚格式化器必须写在注册表之前。
/** R5-D-037：op_job_heartbeat.last_result 只有 SUCCESS/FAIL/RUNNING 三枚码（实体注释＋写入侧 markRunning/markSuccess/markFail），词表外的大写原码兜底，不猜标签 */
const JOB_RESULT_LABEL = { SUCCESS: '成功', FAIL: '失败', RUNNING: '执行中' }
const jobResultLabel = (v) => (v == null || v === '' ? '' : JOB_RESULT_LABEL[String(v)] || String(v).toUpperCase())
/** R5-D-037：last_error 屏上只留摘要——先剥 SQL 语句主体（库里存的是 JDBC/MyBatis 原始报错，整段 SQL 正文不落 UI），再截首 200 字；全文不内联展开、不做「查看全文」 */
const MAX_CELL_ERR_LEN = 200
/** 剥 SQL 语句正文：SQL [ ... ]（含被库侧 1000 字上限截断而未闭合的尾巴）与 ### SQL: 行整行替换为占位文案 */
const stripSqlBodies = (s) => s
  .replace(/SQL\s*\[[\s\S]*?\]/g, 'SQL [语句主体已省略]')
  .replace(/SQL\s*\[[\s\S]*$/g, 'SQL [语句主体已省略]')
  .replace(/###\s*SQL:[^\n]*/g, '### SQL: 语句主体已省略')
const errSummary = (v) => {
  const t = stripSqlBodies(v == null ? '' : String(v))
  return t.length > MAX_CELL_ERR_LEN ? t.slice(0, MAX_CELL_ERR_LEN) + '…' : t
}
/** R5-D-037：只有上次运行 FAIL 才把错误摘要落「最近错误」列；SUCCESS/RUNNING 行的历史残值不外显（写入侧 markSuccess 已清 last_error，此处兜住存量旧行） */
const lastErrCell = (v, row) => (row != null && String(row.lastResult || '').toUpperCase() === 'FAIL' ? errSummary(v) : '')
const REGISTRY = {
  // --- masterApi CRUD pages ---
  currency: { title: '币种', api: masterApi.currency, columns: [{ prop: 'currencyCode', label: '币种编码', width: 120 }, { prop: 'currencyName', label: '币种名称', minWidth: 140 }, { prop: 'symbol', label: '符号', width: 80 }, { prop: 'decimalDigit', label: '精度', width: 80, align: 'center' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'currencyCode', label: '币种编码', required: true }, { prop: 'currencyName', label: '币种名称', required: true }, { prop: 'symbol', label: '符号' }, { prop: 'decimalDigit', label: '精度', type: 'number', precision: 0 }] },
  project: { title: '项目', api: masterApi.project, columns: [{ prop: 'projectCode', label: '项目编码', width: 120 }, { prop: 'projectName', label: '项目名称', minWidth: 160 }, { prop: 'projectType', label: '类型', width: 100 }, { prop: 'beginDate', label: '开始日期', width: 120 }, { prop: 'endDate', label: '结束日期', width: 120 }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'projectCode', label: '项目编码', required: true }, { prop: 'projectName', label: '项目名称', required: true }, { prop: 'projectType', label: '类型' }, { prop: 'beginDate', label: '开始日期', type: 'date' }, { prop: 'endDate', label: '结束日期', type: 'date' }] },
  // R5-D-051：账号/卡号列一律 maskAccount（首尾各留 4 位），接口原值不动。
  bank: { title: '银行账户', api: masterApi.bank, columns: [{ prop: 'accountCode', label: '账号', width: 180, formatter: maskAccount }, { prop: 'accountName', label: '账户名称', minWidth: 140 }, { prop: 'bankName', label: '开户行', minWidth: 140 }, { prop: 'currencyCode', label: '币种', width: 80 }, { prop: 'balance', label: '余额', width: 120, align: 'right', formatter: 'money' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'accountCode', label: '账号', required: true }, { prop: 'accountName', label: '账户名称', required: true }, { prop: 'bankName', label: '开户行' }, { prop: 'bankBranch', label: '支行' }, { prop: 'currencyCode', label: '币种' }, { prop: 'balance', label: '余额', type: 'number' }] },
  'expense-item': { title: '费用类型', api: masterApi.item, columns: [{ prop: 'itemCode', label: '编码', width: 120 }, { prop: 'itemName', label: '名称', minWidth: 160 }, { prop: 'billType', label: '分类', width: 100, formatter: (v) => docTypeLabel(v) }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'itemCode', label: '编码', required: true }, { prop: 'itemName', label: '名称', required: true }, { prop: 'billType', label: '分类' }] },
  city: { title: '城市等级', api: masterApi.city, columns: [{ prop: 'cityCode', label: '城市编码', width: 120 }, { prop: 'cityName', label: '城市名称', minWidth: 140 }, { prop: 'cityLevel', label: '等级', width: 80, align: 'center' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'cityCode', label: '城市编码', required: true }, { prop: 'cityName', label: '城市名称', required: true }, { prop: 'cityLevel', label: '等级', type: 'select', options: [{ label: '一线', value: 1 }, { label: '二线', value: 2 }, { label: '三线', value: 3 }] }] },
  'tax-rate': { title: '税码税率', api: masterApi.tax, columns: [{ prop: 'rateCode', label: '税码', width: 100 }, { prop: 'rateName', label: '税名', minWidth: 140 }, { prop: 'rateValue', label: '税率', width: 100, align: 'right', formatter: (v) => (Number(v) * 100).toFixed(2) + '%' }, { prop: 'taxType', label: '类型', width: 100 }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'rateCode', label: '税码', required: true }, { prop: 'rateName', label: '税名', required: true }, { prop: 'rateValue', label: '税率', type: 'number', precision: 4 }, { prop: 'taxType', label: '类型' }] },
  'expense-standard': { title: '报销标准', api: masterApi.std, columns: [{ prop: 'stdName', label: '标准名称', minWidth: 140 }, { prop: 'stdType', label: '类型', width: 100 }, { prop: 'amount', label: '标准金额', width: 120, align: 'right', formatter: 'money' }, { prop: 'cityLevel', label: '城市等级', width: 100 }], formFields: [{ prop: 'stdCode', label: '标准编码', required: true }, { prop: 'stdName', label: '标准名称', required: true }, { prop: 'stdType', label: '类型' }, { prop: 'amount', label: '标准金额', type: 'number' }, { prop: 'cityLevel', label: '城市等级', type: 'number', precision: 0 }] },
  payee: { title: '收款方', api: masterApi.payee, columns: [{ prop: 'payeeType', label: '类型', width: 120, formatter: (v) => payeeTypeLabel(v) }, { prop: 'payeeName', label: '名称', minWidth: 160 }, { prop: 'bankName', label: '开户行', minWidth: 140 }, { prop: 'bankAccount', label: '账号', width: 180, formatter: maskAccount }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'payeeType', label: '类型', required: true }, { prop: 'payeeName', label: '名称', required: true }, { prop: 'bankName', label: '开户行' }, { prop: 'bankAccount', label: '账号' }] },
  'exchange-rate': { title: '汇率', api: masterApi.rate, columns: [{ prop: 'fromCurrency', label: '源币种', width: 100 }, { prop: 'toCurrency', label: '目标币种', width: 100 }, { prop: 'rate', label: '汇率', width: 120, align: 'right', formatter: 'money' }, { prop: 'effectDate', label: '日期', width: 120 }], formFields: [{ prop: 'fromCurrency', label: '源币种', required: true }, { prop: 'toCurrency', label: '目标币种', required: true }, { prop: 'rate', label: '汇率', type: 'number', precision: 6, required: true }, { prop: 'effectDate', label: '日期', type: 'date' }] },
  // --- masterCfgApi pages ---
  form: { title: '表单配置', api: masterCfgApi.form, columns: [{ prop: 'formCode', label: '表单编码', width: 120 }, { prop: 'formName', label: '表单名称', minWidth: 160 }, { prop: 'billType', label: '单据类型', width: 100, formatter: (v) => billTypeLabel(v) }], formFields: [{ prop: 'formCode', label: '表单编码', required: true }, { prop: 'formName', label: '表单名称', required: true }, { prop: 'billType', label: '单据类型' }] },
  loanLimit: { title: '借款额度', api: masterCfgApi.loanLimit, columns: [{ prop: 'userName', label: '员工', width: 120 }, { prop: 'loanType', label: '借款类型', width: 100 }, { prop: 'limitAmount', label: '额度', width: 120, align: 'right', formatter: 'money' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'userName', label: '员工', required: true }, { prop: 'loanType', label: '借款类型' }, { prop: 'limitAmount', label: '额度', type: 'number', required: true }] },
  loanInitial: { title: '初始借款余额', api: masterCfgApi.loanInitial, columns: [{ prop: 'userName', label: '员工', minWidth: 120 }, { prop: 'balance', label: '初始余额', width: 120, align: 'right', formatter: 'money' }, { prop: 'importDate', label: '日期', width: 120 }], formFields: [{ prop: 'userName', label: '员工', required: true }, { prop: 'balance', label: '初始余额', type: 'number', required: true }, { prop: 'importDate', label: '日期', type: 'date' }] },
  orgType: { title: '组织类型', api: masterCfgApi.orgType, columns: [{ prop: 'typeCode', label: '编码', width: 120 }, { prop: 'typeName', label: '名称', minWidth: 160 }, { prop: 'remark', label: '说明', minWidth: 200 }], formFields: [{ prop: 'typeCode', label: '编码', required: true }, { prop: 'typeName', label: '名称', required: true }, { prop: 'remark', label: '说明', type: 'textarea' }] },
  cashFlow: { title: '现金流量码', api: masterCfgApi.cashFlow, columns: [{ prop: 'cashCode', label: '编码', width: 120 }, { prop: 'cashName', label: '名称', minWidth: 160 }, { prop: 'direction', label: '方向', width: 80, align: 'center' }], formFields: [{ prop: 'cashCode', label: '编码', required: true }, { prop: 'cashName', label: '名称', required: true }, { prop: 'direction', label: '方向', type: 'select', options: [{ label: '流入', value: 'IN' }, { label: '流出', value: 'OUT' }] }] },
  itemCashMap: { title: '费用类型-现金流量码映射', api: masterCfgApi.itemCashMap, columns: [{ prop: 'itemId', label: '费用ID', width: 120 }, { prop: 'itemName', label: '费用名称', minWidth: 140 }, { prop: 'cashCode', label: '现金流编码', width: 120 }, { prop: 'cashName', label: '现金流名称', minWidth: 140 }], formFields: [{ prop: 'itemId', label: '费用ID', required: true, type: 'select', optionsSource: 'expense-item' }, { prop: 'itemName', label: '费用名称' }, { prop: 'cashCode', label: '现金流编码', required: true }, { prop: 'cashName', label: '现金流名称' }] },
  costCenterMap: { title: '成本中心-预算组织映射', api: masterCfgApi.costCenterMap, columns: [{ prop: 'costCenterId', label: '成本中心ID', width: 120 }, { prop: 'costCenterName', label: '成本中心名称', minWidth: 140 }, { prop: 'budgetOrgId', label: '预算组织ID', width: 120 }, { prop: 'budgetOrgName', label: '预算组织名称', minWidth: 140 }], formFields: [{ prop: 'costCenterId', label: '成本中心ID', required: true }, { prop: 'costCenterName', label: '成本中心名称' }, { prop: 'budgetOrgId', label: '预算组织ID', required: true }, { prop: 'budgetOrgName', label: '预算组织名称' }] },
  vehicle: { title: '交通工具', api: masterCfgApi.vehicle, columns: [{ prop: 'vehicleCode', label: '编码', width: 120 }, { prop: 'vehicleName', label: '名称', minWidth: 140 }, { prop: 'seatClass', label: '舱位', width: 100 }, { prop: 'levelRange', label: '等级范围', width: 100 }], formFields: [{ prop: 'vehicleCode', label: '编码', required: true }, { prop: 'vehicleName', label: '名称', required: true }, { prop: 'seatClass', label: '舱位' }, { prop: 'levelRange', label: '等级范围' }] },
  'bank-inst': { title: '金融机构', api: masterCfgApi.bank, columns: [{ prop: 'bankCode', label: '编码', width: 120 }, { prop: 'bankName', label: '名称', minWidth: 160 }, { prop: 'bankType', label: '类型', width: 100 }], formFields: [{ prop: 'bankCode', label: '编码', required: true }, { prop: 'bankName', label: '名称', required: true }, { prop: 'bankType', label: '类型' }] },
  employeeLevel: { title: '员工等级', api: masterCfgApi.employeeLevel, columns: [{ prop: 'levelCode', label: '编码', width: 120 }, { prop: 'levelName', label: '名称', minWidth: 140 }, { prop: 'sort', label: '排序', width: 80, align: 'center' }], formFields: [{ prop: 'levelCode', label: '编码', required: true }, { prop: 'levelName', label: '名称', required: true }, { prop: 'sort', label: '排序', type: 'number', precision: 0 }] },
  // D-DEC-171：本页此前把「借方科目/贷方科目」绑在 debit/credit_subject_code 上，实测这两列 704/704 全空
  // （本域根本没有写入点），故主列恒空。真值在 subject_code/subject_name + direction——md_gl_config 是
  // 「一行一侧」的模型（DEBIT 279 / CREDIT 425，subject_code 零空），取数侧 VoucherService:330-356 也正好
  // 按 direction 从规则池挑行后读 getSubjectCode()/getSubjectName()。列表与表单一律改绑承载列；
  // debit/credit_subject_id 两列是引擎取科目 ID 的入口（279/425 非空），原样保留可编辑。
  // R5-D-047：subject_name 存的是接口侧规则原文，与 aux_rule（辅助核算规则）同源重复，列表只留 auxRule 这一枚承载规则的列，subject_name 退到表单里可编辑。
  glConfig: { title: '记账配置', api: masterCfgApi.glConfig, columns: [{ prop: 'itemName', label: '费用类型', width: 140 }, { prop: 'direction', label: '方向', width: 76, align: 'center', formatter: (v) => (v === 'DEBIT' ? '借' : v === 'CREDIT' ? '贷' : (v || '')) }, { prop: 'subjectCode', label: '科目编码', width: 130 }, { prop: 'auxRule', label: '辅助核算规则', minWidth: 140 }], formFields: [{ prop: 'itemId', label: '费用类型ID', required: true }, { prop: 'itemName', label: '费用类型名称' }, { prop: 'direction', label: '借贷方向', type: 'select', options: [{ label: '借', value: 'DEBIT' }, { label: '贷', value: 'CREDIT' }] }, { prop: 'subjectCode', label: '科目编码' }, { prop: 'subjectName', label: '科目名称' }, { prop: 'debitSubjectId', label: '借方科目ID' }, { prop: 'creditSubjectId', label: '贷方科目ID' }, { prop: 'auxRule', label: '辅助核算规则' }] },
  incomeExpense: { title: '收支设置', api: masterCfgApi.incomeExpense, columns: [{ prop: 'ieCode', label: '编码', width: 120 }, { prop: 'ieName', label: '名称', minWidth: 140 }, { prop: 'ieType', label: '收支类型', width: 100 }], formFields: [{ prop: 'ieCode', label: '编码', required: true }, { prop: 'ieName', label: '名称', required: true }, { prop: 'ieType', label: '收支类型', type: 'select', options: [{ label: '收入', value: 'IN' }, { label: '支出', value: 'OUT' }] }] },
  loanPayType: { title: '借款付款类型', api: masterCfgApi.loanPayType, columns: [{ prop: 'typeCode', label: '编码', width: 120 }, { prop: 'typeName', label: '名称', minWidth: 140 }, { prop: 'remark', label: '说明', minWidth: 200 }], formFields: [{ prop: 'typeCode', label: '编码', required: true }, { prop: 'typeName', label: '名称', required: true }, { prop: 'remark', label: '说明', type: 'textarea' }] },
  contractType: { title: '合同类型', api: masterCfgApi.contractType, columns: [{ prop: 'typeCode', label: '编码', width: 120 }, { prop: 'typeName', label: '名称', minWidth: 160 }, { prop: 'remark', label: '说明', minWidth: 200 }], formFields: [{ prop: 'typeCode', label: '编码', required: true }, { prop: 'typeName', label: '名称', required: true }, { prop: 'remark', label: '说明', type: 'textarea' }] },
  cip: { title: '在建工程', api: masterCfgApi.cip, columns: [{ prop: 'cipCode', label: '在建工程编码', width: 140 }, { prop: 'cipName', label: '在建工程名称', minWidth: 220 }, { prop: 'companyName', label: '公司', width: 140, align: 'center' }, { prop: 'remark', label: '备注', minWidth: 200 }, { prop: 'status', label: '状态', width: 100, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'cipCode', label: '在建工程编码', required: true }, { prop: 'cipName', label: '在建工程名称', required: true }, { prop: 'companyName', label: '所属公司' }, { prop: 'remark', label: '备注', type: 'textarea' }, { prop: 'status', label: '启用', type: 'switch' }] },
  budgetType: { title: '预算类型', api: masterCfgApi.budgetType, columns: [{ prop: 'typeName', label: '名称', minWidth: 200 }, { prop: 'hasProject', label: '包含项目', width: 110, align: 'center', formatter: (v) => v === 'Y' ? '是' : '否' }, { prop: 'remark', label: '备注', minWidth: 200 }, { prop: 'status', label: '状态', width: 100, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'typeName', label: '名称', required: true }, { prop: 'hasProject', label: '包含项目', type: 'select', options: [{ label: '是', value: 'Y' }, { label: '否', value: 'N' }] }, { prop: 'remark', label: '备注', type: 'textarea' }, { prop: 'status', label: '启用', type: 'switch' }] },
  // --- system pages ---
  dict: { title: '字典管理', api: { page: (p) => dictApi.typePage(p), create: (b) => dictApi.typeCreate(b), update: (b) => dictApi.typeUpdate(b), delete: (id) => dictApi.typeDelete(id) }, columns: [{ prop: 'dictType', label: '字典类型', width: 140 }, { prop: 'dictName', label: '字典名称', minWidth: 160 }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'dictType', label: '字典类型', required: true }, { prop: 'dictName', label: '字典名称', required: true }] },
  'sys-config': { title: '全局参数', api: configApi, columns: [{ prop: 'configKey', label: '参数键', width: 180 }, { prop: 'configValue', label: '参数值', minWidth: 200 }, { prop: 'configName', label: '参数名称', minWidth: 200 }], formFields: [{ prop: 'configKey', label: '参数键', required: true }, { prop: 'configValue', label: '参数值', required: false }, { prop: 'configName', label: '参数名称', type: 'textarea' }] },
  'sys-notice': { title: '公告管理', api: { page: (p) => noticeApi.page(p), create: (b) => noticeApi.create(b), update: (b) => noticeApi.update(b), delete: (id) => noticeApi.delete(id) }, columns: [{ prop: 'noticeTitle', label: '标题', minWidth: 200 }, { prop: 'status', label: '状态', width: 100, align: 'center', formatter: (v) => v === 'PUBLISHED' ? '已发布' : (v === 'CLOSED' ? '已关闭' : (v || '草稿')) }, { prop: 'publishTime', label: '发布时间', width: 160 }], formFields: [{ prop: 'noticeTitle', label: '标题', required: true }, { prop: 'noticeContent', label: '内容', type: 'textarea', rows: 4 }] },
  'sys-message': { title: '消息中心', api: { page: (p) => messageApi.page(p), delete: (id) => messageApi.delete(id) }, readOnly: true, columns: [{ prop: 'title', label: '标题', minWidth: 200 }, { prop: 'content', label: '内容', minWidth: 300 }, { prop: 'msgType', label: '类型', width: 100, formatter: (v) => msgTypeLabel(v) }, { prop: 'createTime', label: '时间', width: 160 }, { prop: 'isRead', label: '状态', width: 80, align: 'center', formatter: (v) => v ? '已读' : '未读' }] },
  'sys-file': { title: '文件管理', api: { page: (p) => fileApi.page(p), delete: (id) => fileApi.delete(id) }, readOnly: true, columns: [{ prop: 'fileName', label: '文件名', minWidth: 200 }, { prop: 'fileSize', label: '大小', width: 100, align: 'right', formatter: (v) => (v / 1024).toFixed(1) + ' KB' }, { prop: 'storageZone', label: '存储区域', width: 100, align: 'center' }, { prop: 'bizType', label: '业务类型', width: 100, formatter: (v) => docTypeLabel(v) }, { prop: 'createTime', label: '上传时间', width: 160 }] },
  'sys-policy': { title: '费用政策', hint: '标准明细与版本请用「费用标准」专用页维护', api: { page: (p) => expensePolicyApi.page(p), create: (b) => expensePolicyApi.create(b), update: (b) => expensePolicyApi.update(b), delete: (id) => expensePolicyApi.remove(id) }, columns: [{ prop: 'policyCode', label: '政策编码', width: 120 }, { prop: 'policyName', label: '政策名称', minWidth: 160 }, { prop: 'expenseType', label: '费用类型', width: 100 }, { prop: 'maxAmount', label: '金额上限', width: 120, align: 'right', formatter: 'money' }, { prop: 'submitDays', label: '提交时限(天)', width: 110, align: 'center' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: (v) => (v === 1 ? '启用' : '停用') }], formFields: [{ prop: 'policyCode', label: '政策编码', required: true }, { prop: 'policyName', label: '政策名称', required: true }, { prop: 'expenseType', label: '费用类型' }, { prop: 'expenseItemName', label: '费用项名称' }, { prop: 'maxAmount', label: '金额上限', type: 'number', required: true }, { prop: 'approveAboveAmount', label: '超额审批阈值', type: 'number' }, { prop: 'submitDays', label: '提交时限(天)', type: 'number', precision: 0 }, { prop: 'priority', label: '优先级', type: 'number', precision: 0 }, { prop: 'needInvoice', label: '需要发票', type: 'select', options: [{ label: '是', value: 1 }, { label: '否', value: 0 }] }, { prop: 'effectiveStart', label: '生效起', type: 'date' }, { prop: 'effectiveEnd', label: '生效止', type: 'date' }, { prop: 'remark', label: '备注', type: 'textarea', rows: 2 }] },
  'sys-number': { title: '编号规则', hint: '改动后对新单据生效；已生成的存量单据号不变', api: { page: (p) => numberApi.rulePage(p), create: (b) => numberApi.ruleCreate(b), update: (b) => numberApi.ruleUpdate(b), delete: (id) => numberApi.ruleDelete(id), enable: (id, on) => numberApi.ruleEnable(id, on) }, columns: [{ prop: 'ruleCode', label: '规则编码', width: 130 }, { prop: 'ruleName', label: '规则名称', minWidth: 150 }, { prop: 'prefix', label: '前缀', width: 90 }, { prop: 'dateFormat', label: '日期格式', width: 100, formatter: (v) => v || '不含日期' }, { prop: 'seqLength', label: '序号长度', width: 90, align: 'center' }, { prop: 'resetCycle', label: '重置周期', width: 90, align: 'center', formatter: (v) => ({ NONE: '不重置', DAY: '按日', MONTH: '按月', YEAR: '按年' }[v] || v || '-') }, { prop: 'step', label: '步长', width: 70, align: 'center' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }, { prop: 'remark', label: '备注', minWidth: 140 }], formFields: [{ prop: 'ruleCode', label: '规则编码', required: true }, { prop: 'ruleName', label: '规则名称', required: true }, { prop: 'prefix', label: '前缀' }, { prop: 'dateFormat', label: '日期格式', placeholder: '如 yyyyMMdd，留空表示不含日期' }, { prop: 'seqLength', label: '序号长度', type: 'number', precision: 0 }, { prop: 'resetCycle', label: '重置周期', type: 'select', options: [{ label: '不重置', value: 'NONE' }, { label: '按日', value: 'DAY' }, { label: '按月', value: 'MONTH' }, { label: '按年', value: 'YEAR' }] }, { prop: 'step', label: '步长', type: 'number', precision: 0 }, { prop: 'status', label: '状态', type: 'select', options: [{ label: '启用', value: 'NORMAL' }, { label: '停用', value: 'DISABLED' }] }, { prop: 'remark', label: '备注', type: 'textarea', rows: 2 }] },
  // --- supplier ---
  // R5-D-051 扫描结论：本页列表没有账号列（bankAccount 只出现在表单项，编辑框要回写全号不能打码），所以此处无打码落点；带账号列的是 bank / payee 两行。
  supplier: { title: '供应商', api: supplierApi, columns: [{ prop: 'supplierCode', label: '编码', width: 120 }, { prop: 'supplierName', label: '名称', minWidth: 160 }, { prop: 'contactName', label: '联系人', width: 100 }, { prop: 'contactPhone', label: '电话', width: 120 }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'supplierCode', label: '编码', required: true }, { prop: 'supplierName', label: '名称', required: true }, { prop: 'contactName', label: '联系人' }, { prop: 'contactPhone', label: '电话' }, { prop: 'bankName', label: '开户行' }, { prop: 'bankAccount', label: '银行账号' }] },
  // --- review rules (CRUD with enable/disable) ---
  'review-rules': { title: '智能审单规则', api: { page: (p) => complianceApi.reviewRules(p), create: (b) => complianceApi.reviewRuleCreate(b), update: (b) => complianceApi.reviewRuleUpdate(b), delete: (id) => complianceApi.reviewRuleDelete(id) }, columns: [{ prop: 'ruleName', label: '规则名称', minWidth: 180 }, { prop: 'ruleCategory', label: '分类', width: 120, formatter: (v) => ruleCategoryLabel(v) }, { prop: 'riskLevel', label: '风险等级', width: 100, align: 'center' }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'ruleName', label: '规则名称', required: true }, { prop: 'ruleCategory', label: '规则分类' }, { prop: 'riskLevel', label: '风险等级', type: 'select', options: [{ label: '低', value: 'LOW' }, { label: '中', value: 'MEDIUM' }, { label: '高', value: 'HIGH' }] }, { prop: 'matchExpression', label: '匹配表达式', type: 'textarea', rows: 3 }] },
  // --- read-only pages ---
  'job-heartbeat': { title: '定时任务健康', readOnly: true, api: { page: (p) => jobApi.heartbeatPage(p) }, columns: [{ prop: 'jobName', label: '任务名称', minWidth: 180 }, { prop: 'lastResult', label: '状态', width: 100, align: 'center', formatter: jobResultLabel }, { prop: 'lastRunAt', label: '上次执行', width: 160 }, { prop: 'durationMs', label: '耗时(ms)', width: 100, align: 'center' }, { prop: 'failCount', label: '失败次数', width: 100, align: 'center' }, { prop: 'lastError', label: '最近错误', minWidth: 200, formatter: lastErrCell, tooltip: true }] },
  fraud: { title: '反欺诈检测', readOnly: true, api: { page: (p) => complianceApi.fraudList(p) }, columns: [{ prop: 'billId', label: '单据ID', width: 100, align: 'center' }, { prop: 'detectionType', label: '检测类型', width: 140 }, { prop: 'riskScore', label: '风险分', width: 100, align: 'center' }, { prop: 'details', label: '详情', minWidth: 200 }, { prop: 'actionTaken', label: '处置动作', width: 120 }, { prop: 'createTime', label: '检测时间', width: 160 }] },
  'service-catalog': { title: '服务目录', api: { page: (p) => complianceApi.catalogPage(p), create: (b) => complianceApi.catalogCreate(b), update: (b) => complianceApi.catalogUpdate(b), delete: (id) => complianceApi.catalogDelete(id) }, columns: [{ prop: 'serviceName', label: '服务名称', minWidth: 180 }, { prop: 'serviceCode', label: '编码', width: 120 }, { prop: 'category', label: '类别', width: 100 }, { prop: 'responseSlaHours', label: '响应SLA(小时)', width: 120, align: 'center' }, { prop: 'ownerRole', label: '责任岗位', width: 120 }, { prop: 'status', label: '状态', width: 80, align: 'center', formatter: 'enable' }], formFields: [{ prop: 'serviceName', label: '服务名称', required: true }, { prop: 'serviceCode', label: '编码', required: true }, { prop: 'category', label: '类别' }, { prop: 'responseSlaHours', label: '响应SLA(小时)', type: 'number', precision: 0 }, { prop: 'description', label: '说明', type: 'textarea' }] },
  satisfaction: { title: '满意度调查', readOnly: true, api: { page: (p) => complianceApi.satisfactionPage(p) }, columns: [{ prop: 'billNo', label: '单据号', minWidth: 140 }, { prop: 'serviceCode', label: '服务编码', width: 120 }, { prop: 'score', label: '评分', width: 100, align: 'center' }, { prop: 'surveyUserId', label: '评价人', width: 100, align: 'center' }, { prop: 'comment', label: '评价', minWidth: 200 }] },
  'audit-trail': { title: '审计轨迹', readOnly: true, api: { page: (p) => auditApi.trailPage(p) }, columns: [{ prop: 'action', label: '动作', width: 120, formatter: (v) => auditActionLabel(v) }, { prop: 'bizType', label: '业务类型', width: 120, formatter: (v) => docTypeLabel(v) }, { prop: 'bizNo', label: '单据号', width: 140 }, { prop: 'operator', label: '操作人', width: 120 }, { prop: 'createTime', label: '时间', width: 160 }, { prop: 'remark', label: '备注', minWidth: 250 }] },
  'wf-instance': { title: '流程实例', readOnly: true, api: { page: (p) => flowApi.instancePage(p) }, columns: [{ prop: 'title', label: '流程名称', minWidth: 180 }, { prop: 'bizType', label: '业务类型', width: 120, formatter: (v) => docTypeLabel(v) }, { prop: 'bizNo', label: '单据号', width: 140 }, { prop: 'status', label: '状态', width: 100, align: 'center', formatter: (v) => statusLabel(v) }, { prop: 'currentNode', label: '当前节点', width: 120 }, { prop: 'currentAssignee', label: '当前审批人', width: 120 }, { prop: 'startTime', label: '发起时间', width: 160 }, { prop: 'endTime', label: '结束时间', width: 160 }] },
}

const cfg = computed(() => REGISTRY[props.configKey] || { title: props.configKey, columns: [], formFields: [], api: {} })

/**
 * 列未声明 formatter 时，用本页 formField 自带的 select 选项把原码换回中文。
 * 词表只在这一处声明（编辑弹窗下拉与列表列同源），不在这里再抄一份 map。
 */
function optionLabel(col, row) {
  const f = (cfg.value.formFields || []).find(x => x.prop === col.prop && x.type === 'select' && x.options)
  if (!f) return null
  const hit = f.options.find(o => String(o.value) === String(row[col.prop]))
  return hit ? hit.label : null
}

const cfgFormRef = ref(null)
/**
 * 配置驱动的必填规则。此前 el-form-item 没有 prop，这份规则从未被 el-form 绑定过，
 * 红星只由 :required 画出来、save() 也不校验——补上 prop 后规则必须走自定义 validator：
 * 本注册表的必填字段里有数字（汇率/额度/余额），纯 {required,type,trigger} 三键规则会把 0 判成未填。
 */
const configRules = computed(() => {
  const rules = {}
  cfg.value.formFields.forEach(f => {
    if (!f.required || !f.prop) return
    const msg = ['select', 'date', 'datetime'].includes(f.type) ? `请选择${f.label}`
      : (f.type === 'textarea' ? `请填写${f.label}` : `请输入${f.label}`)
    rules[f.prop] = commonRules.required(msg)
  })
  return rules
})

// ========== State ==========
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const searchForm = reactive({ keyword: '' })
const loading = ref(false)
const dlg = ref(false)
const editing = ref({})
const selRows = ref([])

// ========== 查询方案（个人常用筛选组合） ==========
// 本组件被 30+ 条路由复用且 router 未加 :key ⇒ pageKey 必须现取，不能定死在 setup 里
const {
  variantList, currentVariantId, variantLoading,
  saveDialogVisible, saveDialogForm, saveDialogUpdate,
  selectVariant, openSaveDialog, openUpdateDialog, confirmSave,
  deleteVariant, setDefaultVariant, loadDefaultVariant,
} = useQueryVariant(() => `master_config:${props.configKey}`, searchForm, load)

const variantFormRef = ref(null)
const variantRules = { variantName: commonRules.required('请输入方案名称') }

function onVariantCommand(cmd) {
  if (cmd === 'save') openSaveDialog()
  else if (cmd === 'update') openUpdateDialog()
  else if (cmd === 'default') setDefaultVariant(currentVariantId.value)
  else if (cmd === 'delete') deleteVariant(currentVariantId.value)
  // 弹窗常驻不销毁，上次遗留的行内报错要清掉
  nextTick(() => variantFormRef.value?.clearValidate())
}

async function submitVariant() {
  if (!(await validateForms([variantFormRef]))) return
  await confirmSave()
}

/** 重置：搜索条件与已选方案一起清，否则下拉里的名字和表单内容会对不上 */
function resetSearch() {
  searchForm.keyword = ''
  currentVariantId.value = null
  load()
}

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// ========== Methods ==========
async function load() {
  if (!cfg.value.api?.page) return
  loading.value = true
  try {
    const d = await cfg.value.api.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: searchForm.keyword })
    rows.value = d?.list || d?.records || []
    total.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate() {
  const obj = {}
  cfg.value.formFields.forEach((f) => { obj[f.prop] = f.type === 'switch' ? true : (f.type === 'number' ? 0 : '') })
  editing.value = obj
  loadFormSources()
  dlg.value = true
  // 弹窗不销毁重建，上一次打开留下的行内报错须清掉
  nextTick(() => cfgFormRef.value?.clearValidate())
}

function openEdit(row) {
  editing.value = { ...row }
  loadFormSources()
  dlg.value = true
  nextTick(() => cfgFormRef.value?.clearValidate())
}

async function save() {
  if (!(await validateForms([cfgFormRef]))) return
  try {
    if (editing.value.id) { await cfg.value.api.update(editing.value) } else { await cfg.value.api.create(editing.value) }
    ElMessage.success('已保存'); dlg.value = false; load()
  } catch (e) { ElMessage.error(e.message) }
}

async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
    await cfg.value.api.delete(row.id)
    ElMessage.success('已删除'); load()
  } catch (e) { /* cancelled */ }
}

async function toggleEnable(row) {
  try {
    const isEnabled = row.enabled ?? ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)
    await cfg.value.api.enable(row.id, !isEnabled)
    if ('enabled' in row) {
      row.enabled = !isEnabled
    } else {
      row.status = !isEnabled ? 'NORMAL' : 'DISABLED'
    }
    ElMessage.success(isEnabled ? '已停用' : '已启用'); load()
  } catch (e) { ElMessage.error(e.message) }
}

function onSelChange(val) { selRows.value = val }

// configKey 变化（组件实例被复用时，如外层直接嵌入无 :key 的场景）：重置分页/搜索并按新配置重新加载，
// 否则会残留上一页签的 rows 与本页签的 columns 错配 → 整页字段全空（2026-09-17 实测）
watch(() => props.configKey, () => {
  pageNum.value = 1
  pageSize.value = 20
  searchForm.keyword = ''
  rows.value = []
  total.value = 0
  initVariantAndLoad()
})

// R5-D-048：表单下拉的「动态选项源」——费用ID 等外键字段由选项源拉取 label/value，
// 避免用户手输数字 ID 且名称列悬空。
const dynOptions = reactive({})
const sourceLoaded = reactive({})
const DYN_OPTION_SOURCES = {
  'expense-item': async () => {
    const d = await masterApi.item.page({ pageNum: 1, pageSize: 500 })
    return (d?.list || d?.records || []).map((x) => ({ label: x.itemName, value: x.id }))
  },
}
async function loadFormSources() {
  for (const f of cfg.value.formFields || []) {
    const src = f.optionsSource
    if (!src || sourceLoaded[src]) continue
    const loader = DYN_OPTION_SOURCES[src]
    if (!loader) continue
    sourceLoaded[src] = true
    try { dynOptions[src] = await loader() } catch { dynOptions[src] = [] }
  }
}
// 选中费用项后回填名称（itemCashMap：itemId 是外键，itemName 是展示名）
watch(() => editing.value?.itemId, (v) => {
  if (v == null || v === '') return
  const hit = (dynOptions['expense-item'] || []).find((o) => String(o.value) === String(v))
  if (hit) editing.value.itemName = hit.label
})

async function initVariantAndLoad() {
  currentVariantId.value = null
  // 只读页没有搜索框，方案无处可存，不必发这两个请求
  if (!cfg.value.readOnly) await loadDefaultVariant()
  load()
}

onMounted(initVariantAndLoad)
defineExpose({ load })
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
.variant-select { width: 160px; }
.variant-tip { margin: -8px 0 0 90px; font-size: 12px; color: #909399; line-height: 18px; }
</style>
