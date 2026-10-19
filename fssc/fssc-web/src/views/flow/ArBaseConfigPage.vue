<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.baseCfg.pageTitle') }}</span>
        <div class="ops">
          <el-button size="small" @click="doExport">{{ $t('flow.report.exportTabCsv') }}</el-button>
          <el-button size="small" @click="loadAll">{{ $t('common.refresh') }}</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab">
      <el-tab-pane :label="$t('flow.baseCfg.tabFx')" name="fx">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          :title="$t('flow.baseCfg.fxHint')" />
        <el-table :data="fxRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" :label="$t('flow.baseCfg.colParamKey')" min-width="150" />
          <el-table-column prop="configName" :label="$t('flow.baseCfg.colParamName')" min-width="150" />
          <el-table-column :label="$t('flow.baseCfg.colRate')" width="180">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">{{ $t('common.save') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="ops" style="margin-top: 8px">
          <el-input v-model="newCcy" size="small" style="width: 120px" :placeholder="$t('flow.baseCfg.phCurrency')" />
          <el-input v-model="newRate" size="small" style="width: 130px" :placeholder="$t('flow.baseCfg.colRate')" />
          <el-button size="small" type="primary" @click="addFx">{{ $t('flow.baseCfg.btnAddCurrency') }}</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.baseCfg.tabTolerance')" name="tolerance">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          :title="$t('flow.baseCfg.toleranceHint')" />
        <el-table :data="toleranceRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" :label="$t('flow.baseCfg.colParamKey')" min-width="220" />
          <el-table-column prop="configName" :label="$t('flow.baseCfg.colParamName')" min-width="180" />
          <el-table-column :label="$t('flow.baseCfg.colTolerance')" width="160">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" :placeholder="$t('flow.baseCfg.phToleranceValue')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">{{ $t('common.save') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="$t('flow.baseCfg.tabProvisionRate')" name="badDebt">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          :title="$t('flow.baseCfg.provisionRateHint')" />
        <el-table :data="badDebtRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" :label="$t('flow.baseCfg.colParamKey')" min-width="170" />
          <el-table-column prop="configName" :label="$t('flow.baseCfg.colParamName')" min-width="220" />
          <el-table-column :label="$t('flow.baseCfg.colRatePct')" width="140">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" :placeholder="$t('flow.baseCfg.phRatePctValue')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">{{ $t('common.save') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="$t('flow.baseCfg.tabDispatch')" name="dispatch">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          :title="$t('flow.baseCfg.dispatchHint')" />
        <div class="ops" style="margin-bottom: 8px">
          <el-button size="small" type="primary" @click="addRule">{{ $t('flow.baseCfg.btnAddRule') }}</el-button>
        </div>
        <el-table :data="ruleRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="ruleName" :label="$t('flow.baseCfg.colRuleName')" min-width="130" />
          <el-table-column prop="claimType" :label="$t('flow.baseCfg.colType')" width="120" :formatter="(row, col, val) => writeoffTypeLabel(val)" />
          <el-table-column prop="customerPrefix" :label="$t('flow.baseCfg.colCustPrefix')" width="100" />
          <el-table-column prop="handlers" :label="$t('flow.baseCfg.colOwner')" min-width="140" />
          <el-table-column prop="groupLeader" :label="$t('field.leader')" width="100" />
          <el-table-column :label="$t('flow.baseCfg.colMultiMode')" width="110">
            <template #default="{ row }">{{ row.autoBalance === 1 ? t('flow.baseCfg.valAutoAssign') : t('flow.baseCfg.valWorkPool') }}</template>
          </el-table-column>
          <el-table-column prop="slaHours" label="SLA(h)" width="80" align="right" />
          <el-table-column :label="$t('common.operation')" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="delRule(row)">{{ $t('flow.baseCfg.btnDelete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑派工规则（41号审查 P0-1 同源整改：废弃 window.prompt） -->
    <el-dialog v-model="ruleVisible" :title="$t('flow.baseCfg.dlgAddRule')" width="520px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="100px" size="small">
        <el-form-item :label="$t('flow.baseCfg.colRuleName')" prop="ruleName"><el-input v-model="ruleForm.ruleName" :placeholder="$t('flow.baseCfg.phRuleName')" /></el-form-item>
        <el-form-item :label="$t('flow.baseCfg.formServiceType')">
          <el-select v-model="ruleForm.claimType" clearable :placeholder="$t('flow.baseCfg.phTypeAllHint')" style="width:100%">
            <el-option :label="$t('flow.baseCfg.optAllTypes')" value="" />
            <el-option :label="$t('flow.baseCfg.typeReceiptWriteoff')" value="RECEIPT" />
            <el-option :label="$t('flow.baseCfg.typeNoteWriteoff')" value="NOTE" />
            <el-option :label="$t('flow.baseCfg.typeOffsetWriteoff')" value="OFFSET" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.baseCfg.colCustPrefix')"><el-input v-model="ruleForm.customerPrefix" :placeholder="$t('flow.baseCfg.phCustPrefix')" /></el-form-item>
        <el-form-item :label="$t('flow.baseCfg.colOwner')" prop="handlerList">
          <el-select v-model="ruleForm.handlerList" multiple filterable allow-create :placeholder="$t('flow.baseCfg.phOwnerMulti')" style="width:100%">
            <el-option v-for="u in ruleUserOptions" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('field.leader')"><el-input v-model="ruleForm.groupLeader" :placeholder="$t('flow.baseCfg.phGroupLeader')" /></el-form-item>
        <el-form-item :label="$t('flow.baseCfg.colMultiMode')">
          <el-radio-group v-model="ruleForm.autoBalance">
            <el-radio :value="0">{{ $t('flow.baseCfg.optWorkPool') }}</el-radio>
            <el-radio :value="1">{{ $t('flow.baseCfg.optAutoAssign') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('flow.baseCfg.formSlaHours')"><el-input-number v-model="ruleForm.slaHours" :min="0" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ruleVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="saveRule">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { writeoffTypeLabel } from '@/constants/enums'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi, arGapApi, userApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { exportCsv } from '../../utils/csv'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tab = ref('fx')
const loading = ref(false)
const configRows = ref([])
const newCcy = ref('')
const newRate = ref('')

const fxRows = computed(() => configRows.value.filter((r) => String(r.configKey).startsWith('ar.fx.')))
const toleranceRows = computed(() => configRows.value.filter((r) => String(r.configKey).startsWith('ar.recon.tolerance')))
const badDebtRows = computed(() => configRows.value.filter((r) => String(r.configKey).startsWith('baddebt.rate.')))

async function loadAll() {
  loading.value = true
  try {
    const [fxPage, bdPage] = await Promise.all([
      configApi.page({ pageNum: 1, pageSize: 100, keyword: 'ar.' }),
      configApi.page({ pageNum: 1, pageSize: 50, keyword: 'baddebt.rate' }),
    ])
    const merged = [...(fxPage.list || fxPage.records || []), ...(bdPage.list || bdPage.records || [])]
    configRows.value = merged.map((r) => ({ ...r, _value: r.configValue }))
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function save(row) {
  await configApi.update({ id: row.id, configValue: row._value })
  ElMessage.success(t('flow.baseCfg.msgRateSaved', { v1: row.configKey }))
  await loadAll()
}

async function addFx() {
  if (!newCcy.value || !newRate.value) return ElMessage.warning(t('flow.baseCfg.errNeedCcyRate'))
  await configApi.create({
    tenantId: 1,
    configKey: `ar.fx.${newCcy.value.toUpperCase()}`,
    configName: t('flow.baseCfg.nameFxRate', { v1: newCcy.value.toUpperCase() }),
    configValue: newRate.value,
    valueType: 'NUMBER',
    editable: 1,
  })
  ElMessage.success(t('flow.baseCfg.msgCcyAdded'))
  newCcy.value = ''
  newRate.value = ''
  await loadAll()
}

// ---- AR派工规则 ----
const ruleRows = ref([])
const ruleUserOptions = ref([])

async function loadRuleUsers() {
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
    ruleUserOptions.value = list.map((u) => u.nickName || u.userName).filter(Boolean)
  } catch { ruleUserOptions.value = [] }
}

async function loadRules() {
  loading.value = true
  try {
    ruleRows.value = await arGapApi.dispatchRules()
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

// 41号审查 P0-1 同源整改：新增规则由 window.prompt 改为表单弹窗
const ruleVisible = ref(false)
const ruleForm = ref({ ruleName: '', claimType: '', customerPrefix: '', handlerList: [], groupLeader: '', autoBalance: 0, slaHours: null })
const ruleFormRef = ref(null)
const ruleRules = computed(() => ({
  ruleName: commonRules.required(t('flow.baseCfg.errRuleNameRequired')),
  handlerList: commonRules.multiSelect(t('flow.baseCfg.errOwnerRequired')),
}))

function addRule() {
  ruleForm.value = { ruleName: '', claimType: '', customerPrefix: '', handlerList: [], groupLeader: '', autoBalance: 0, slaHours: null }
  ruleVisible.value = true
}

async function saveRule() {
  if (!(await validateForms([ruleFormRef]))) return
  const f = ruleForm.value
  try {
    await arGapApi.saveDispatchRule({
      ruleName: f.ruleName, claimType: f.claimType || null, customerPrefix: f.customerPrefix || null,
      handlers: f.handlerList.join(','), groupLeader: f.groupLeader || null,
      autoBalance: Number(f.autoBalance || 0), slaHours: Number(f.slaHours || 0) || null,
    })
    ElMessage.success(t('flow.baseCfg.msgRuleSaved'))
    ruleVisible.value = false
    await loadRules()
  } catch (e) { ElMessage.error(e.message) }
}

// 41号审查 P1-9：按页签导出配置
const TAB_EXPORT = computed(() => ({
  fx: () => [fxRows.value, [
    { prop: 'configKey', label: t('flow.baseCfg.colParamKey') }, { prop: 'configName', label: t('flow.baseCfg.colParamName') }, { prop: 'configValue', label: t('flow.baseCfg.colRate') },
  ], 'ArBaseConfig_Fx'],
  tolerance: () => [toleranceRows.value, [
    { prop: 'configKey', label: t('flow.baseCfg.colParamKey') }, { prop: 'configName', label: t('flow.baseCfg.colParamName') }, { prop: 'configValue', label: t('flow.baseCfg.colTolerance') },
  ], 'ArBaseConfig_Tolerance'],
  badDebt: () => [badDebtRows.value, [
    { prop: 'configKey', label: t('flow.baseCfg.colParamKey') }, { prop: 'configName', label: t('flow.baseCfg.colParamName') }, { prop: 'configValue', label: t('flow.baseCfg.colRatePct') },
  ], 'ArBaseConfig_BadDebt'],
  dispatch: () => [ruleRows.value, [
    { prop: 'id', label: 'ID' }, { prop: 'ruleName', label: t('flow.baseCfg.colRuleName') }, { prop: 'claimType', label: t('field.type') },
    { prop: 'customerPrefix', label: t('flow.baseCfg.colCustPrefix') }, { prop: 'handlers', label: t('flow.baseCfg.colOwner') }, { prop: 'groupLeader', label: t('field.leader') },
    { prop: 'autoBalance', label: t('flow.baseCfg.colMultiModeFlag') }, { prop: 'slaHours', label: 'SLA(h)' },
  ], 'ArBaseConfig_DispatchRules'],
}))
function doExport() {
  const [rows, cols, name] = TAB_EXPORT.value[tab.value]()
  exportCsv(rows, cols, name)
}

async function delRule(row) {
  await arGapApi.deleteDispatchRule(row.id)
  ElMessage.success(t('flow.baseCfg.msgDeleted'))
  await loadRules()
}

onMounted(() => { loadAll(); loadRules(); loadRuleUsers() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
