<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>基础配置（汇率维护 / 核对容差）</span>
        <div class="ops">
          <el-button size="small" @click="doExport">导出当前页签 CSV</el-button>
          <el-button size="small" @click="loadAll">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab">
      <el-tab-pane label="汇率维护（国税局当期汇率）" name="fx">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          title="外币对账单/服务申请按协同平台维护的月度汇率折算人民币开票" />
        <el-table :data="fxRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" label="参数键" min-width="150" />
          <el-table-column prop="configName" label="参数名称" min-width="150" />
          <el-table-column label="汇率" width="180">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="ops" style="margin-top: 8px">
          <el-input v-model="newCcy" size="small" style="width: 120px" placeholder="币种如 SGD" />
          <el-input v-model="newRate" size="small" style="width: 130px" placeholder="汇率" />
          <el-button size="small" type="primary" @click="addFx">新增币种</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="核对容差配置" name="tolerance">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          title="对账单金额/税额与应收明细核对时，容差范围内核对通过；超出容差提示失败，走重新导入或财务调整" />
        <el-table :data="toleranceRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" label="参数键" min-width="220" />
          <el-table-column prop="configName" label="参数名称" min-width="180" />
          <el-table-column label="容差(%)" width="160">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" placeholder="如 0.1 表示 0.1%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="坏账计提比例" name="badDebt">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          title="按逾期账龄档配置计提比例（百分比）：365=逾期365天以上、180=180~364天、90=90~179天、30=30~89天、0=30天以内；未配置时回退代码内行业经验值" />
        <el-table :data="badDebtRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="configKey" label="参数键" min-width="170" />
          <el-table-column prop="configName" label="参数名称" min-width="220" />
          <el-table-column label="比例(%)" width="140">
            <template #default="{ row }">
              <el-input v-model="row._value" size="small" placeholder="如 50 表示 50%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="save(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="应收服务派工规则" name="dispatch">
        <el-alert type="info" :closable="false" style="margin-bottom: 8px"
          title="按客户十位码前缀+服务申请类型路由；多人=选单池(组内抢单)，开启负载均衡则按在办量最少自动直派；SLA超时升级组长" />
        <div class="ops" style="margin-bottom: 8px">
          <el-button size="small" type="primary" @click="addRule">新增规则</el-button>
        </div>
        <el-table :data="ruleRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="ruleName" label="规则名" min-width="130" />
          <el-table-column prop="claimType" label="类型(空=全部)" width="120" :formatter="(row, col, val) => writeoffTypeLabel(val)" />
          <el-table-column prop="customerPrefix" label="客户前缀" width="100" />
          <el-table-column prop="handlers" label="负责人" min-width="140" />
          <el-table-column prop="groupLeader" label="组长" width="100" />
          <el-table-column label="多人模式" width="110">
            <template #default="{ row }">{{ row.autoBalance === 1 ? '负载直派' : '选单池' }}</template>
          </el-table-column>
          <el-table-column prop="slaHours" label="SLA(h)" width="80" align="right" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="delRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑派工规则（41号审查 P0-1 同源整改：废弃 window.prompt） -->
    <el-dialog v-model="ruleVisible" title="新增派工规则" width="520px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="100px" size="small">
        <el-form-item label="规则名" prop="ruleName"><el-input v-model="ruleForm.ruleName" placeholder="如：华东-收款核销" /></el-form-item>
        <el-form-item label="服务申请类型">
          <el-select v-model="ruleForm.claimType" clearable placeholder="空=全部类型" style="width:100%">
            <el-option label="（全部类型）" value="" />
            <el-option label="收款核销" value="RECEIPT" />
            <el-option label="票据核销" value="NOTE" />
            <el-option label="对冲核销" value="OFFSET" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户前缀"><el-input v-model="ruleForm.customerPrefix" placeholder="客户十位码前缀，可空" /></el-form-item>
        <el-form-item label="负责人" prop="handlerList">
          <el-select v-model="ruleForm.handlerList" multiple filterable allow-create placeholder="选择或输入负责人（可多选）" style="width:100%">
            <el-option v-for="u in ruleUserOptions" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="组长"><el-input v-model="ruleForm.groupLeader" placeholder="SLA 超时升级对象，可空" /></el-form-item>
        <el-form-item label="多人模式">
          <el-radio-group v-model="ruleForm.autoBalance">
            <el-radio :value="0">选单池（组内抢单）</el-radio>
            <el-radio :value="1">负载直派（在办量最少）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="SLA(小时)"><el-input-number v-model="ruleForm.slaHours" :min="0" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="saveRule">保存</el-button>
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
  ElMessage.success(`${row.configKey} 已保存`)
  await loadAll()
}

async function addFx() {
  if (!newCcy.value || !newRate.value) return ElMessage.warning('请填写币种与汇率')
  await configApi.create({
    tenantId: 1,
    configKey: `ar.fx.${newCcy.value.toUpperCase()}`,
    configName: `国税局汇率-${newCcy.value.toUpperCase()}`,
    configValue: newRate.value,
    valueType: 'NUMBER',
    editable: 1,
  })
  ElMessage.success('币种已新增')
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
const ruleRules = {
  ruleName: commonRules.required('请输入规则名'),
  handlerList: commonRules.multiSelect('请选择或输入负责人（派工规则没有负责人即无人可派）'),
}

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
    ElMessage.success('规则已保存')
    ruleVisible.value = false
    await loadRules()
  } catch (e) { ElMessage.error(e.message) }
}

// 41号审查 P1-9：按页签导出配置
const TAB_EXPORT = {
  fx: () => [fxRows.value, [
    { prop: 'configKey', label: '参数键' }, { prop: 'configName', label: '参数名称' }, { prop: 'configValue', label: '汇率' },
  ], 'ArBaseConfig_Fx'],
  tolerance: () => [toleranceRows.value, [
    { prop: 'configKey', label: '参数键' }, { prop: 'configName', label: '参数名称' }, { prop: 'configValue', label: '容差(%)' },
  ], 'ArBaseConfig_Tolerance'],
  badDebt: () => [badDebtRows.value, [
    { prop: 'configKey', label: '参数键' }, { prop: 'configName', label: '参数名称' }, { prop: 'configValue', label: '比例(%)' },
  ], 'ArBaseConfig_BadDebt'],
  dispatch: () => [ruleRows.value, [
    { prop: 'id', label: 'ID' }, { prop: 'ruleName', label: '规则名' }, { prop: 'claimType', label: '类型' },
    { prop: 'customerPrefix', label: '客户前缀' }, { prop: 'handlers', label: '负责人' }, { prop: 'groupLeader', label: '组长' },
    { prop: 'autoBalance', label: '多人模式(1负载直派)' }, { prop: 'slaHours', label: 'SLA(h)' },
  ], 'ArBaseConfig_DispatchRules'],
}
function doExport() {
  const [rows, cols, name] = TAB_EXPORT[tab.value]()
  exportCsv(rows, cols, name)
}

async function delRule(row) {
  await arGapApi.deleteDispatchRule(row.id)
  ElMessage.success('已删除')
  await loadRules()
}

onMounted(() => { loadAll(); loadRules(); loadRuleUsers() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
