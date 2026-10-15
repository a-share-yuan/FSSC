<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>三单匹配容差参数</b>
        <el-button size="small" @click="reload">刷新</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 12px">采购订单 -> 收货(GRN) -> 发票 三单匹配的金额容差控制（按公司维度维护）</span>
      </div>
    </template>

    <el-alert v-if="loadFailed" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
      title="容差参数查询接口暂不可用（后端未部署或暂无该公司的配置），当前展示默认值；保存时若仍不可用会给出明确提示" />

    <el-form ref="formRef" :rules="formRules" :model="form" label-width="130px" size="default"
             style="max-width: 760px" v-loading="loading">
      <el-divider content-position="left">适用公司</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="公司" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width: 100%" placeholder="选择公司后自动带出该公司的容差配置" @change="loadTolerance">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">金额容差</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="金额容差(%)" prop="amountPct">
            <el-input-number v-model="form.amountPct" :min="0" :max="100" :precision="2" style="width: 100%" />
            <div style="font-size:12px;color:#909399">允许的相对差异百分比（按单据金额计算），0 表示不启用比例容差</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="绝对值容差(元)" prop="amountAbs">
            <el-input-number v-model="form.amountAbs" :min="0" :precision="2" style="width: 100%" />
            <div style="font-size:12px;color:#909399">允许的绝对差异金额；与百分比容差任一满足即视为匹配通过</div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">启用状态</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="容差匹配" prop="status">
            <!-- 取值必须与后端 MatchToleranceService.STATUS_NORMAL/STATUS_DISABLED 一致（NORMAL 而非 ACTIVE），
                 否则保存会被"status 仅支持 NORMAL/DISABLED"拦下 -->
            <el-switch v-model="form.status" active-value="NORMAL" inactive-value="DISABLED"
                       active-text="启用" inactive-text="停用" />
            <div style="font-size:12px;color:#909399">停用后三单匹配严格按金额一致校验（差异为 0）</div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button @click="loadTolerance(form.companyId)">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orgApi, matchToleranceApi } from '../../api'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'

const companies = ref([])
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const formRef = ref(null)
const form = reactive({ companyId: null, amountPct: 0, amountAbs: 0, status: 'NORMAL' })

// 校验规则唯一口径＝中央登记表（useFormRules）；页面不再本地手写规则对象
const { rules: formRules } = useFormRules('MATCH_TOLERANCE')
const { submit: guardSave } = useSubmitGuard(formRef)

/** 按公司加载容差配置（GET /api/tenant/match-tolerance?companyId=）
 *  后端并行实现中：404/失败时静默降级为默认值并给出顶部提示，不阻塞页面 */
async function loadTolerance(companyId) {
  if (!companyId) return
  loading.value = true
  loadFailed.value = false
  try {
    const d = await matchToleranceApi.get(companyId)
    if (d && typeof d === 'object') {
      form.amountPct = Number(d.amountPct ?? 0)
      form.amountAbs = Number(d.amountAbs ?? 0)
      form.status = d.status || 'NORMAL'
    } else {
      // 该公司尚无配置：保留默认值（0/0/启用）
      form.amountPct = 0
      form.amountAbs = 0
      form.status = 'NORMAL'
    }
  } catch (e) {
    loadFailed.value = true
  } finally { loading.value = false }
}

async function save() {
  await guardSave(async () => {
    saving.value = true
    try {
      await matchToleranceApi.save({
        companyId: form.companyId,
        amountPct: Number(form.amountPct || 0),
        amountAbs: Number(form.amountAbs || 0),
        status: form.status || 'NORMAL',
      })
      ElMessage.success('容差参数已保存')
      loadTolerance(form.companyId)
    } catch (e) {
      ElMessage.error(e?.message || '容差参数保存失败')
    } finally { saving.value = false }
  })
}

function reload() {
  if (form.companyId) loadTolerance(form.companyId)
}

onMounted(async () => {
  // 公司树扁平化（与费用报销页同口径），供公司下拉选择
  try {
    const tree = (await orgApi.companyTree()) || []
    const flat = []
    const walk = (ns) => (ns || []).forEach((n) => {
      flat.push({ id: n.id, name: n.companyName || n.name || n.label })
      walk(n.children)
    })
    walk(tree)
    companies.value = flat
    if (flat.length && !form.companyId) {
      form.companyId = flat[0].id
      loadTolerance(form.companyId)
    }
  } catch (e) { /* 公司下拉为空时用户可感知，接口错误已由拦截器提示 */ }
})
</script>
