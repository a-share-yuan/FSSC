<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->
<template>
  <el-card>
    <template #header>
      <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap">
        <b>预算口径</b>
        <span style="color:#909399; font-size:12px">
          同一套预算引擎，不同企业口径不同。这里决定「哪些单据算占用」，改完即时生效
        </span>
        <el-button v-if="canEdit" type="primary" size="small" style="margin-left:auto"
                   :loading="saving" @click="save">保存</el-button>
      </div>
    </template>

    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
      title="关闭不追溯：开关只影响之后新发生的占用；已经占上的额度不会因为关掉开关而消失，仍按原路径在冲销 / 驳回 / 关单时释放。" />

    <SkeletonList v-if="loading" :rows="3" :cols="2" />
    <div v-else>
      <div class="sw-row">
        <div class="sw-main">
          <div class="sw-title">预提 / 待摊占用预算</div>
          <div class="sw-desc">
            开启（默认）：费用预提单、待摊单<b>提交即按「申请占用」扣预算</b>，冲销 / 驳回 / 撤回时自动释放。
            即「预算影响类型＝申请占用」，也是在册手册「选择预提期间即占用预算的期间」的做法。
          </div>
          <div class="sw-desc">
            关闭：预提只在会计上确认费用，<b>不进预算控制</b>；单据上「预算占用」列显示
            <el-tag size="small" type="info">按配置不占</el-tag>，与「未占预算」（该占却没找到预算明细）区分开。
          </div>
        </div>
        <el-switch v-model="form.accrualOccupy" :disabled="!canEdit"
                   active-text="占用" inactive-text="不占" inline-prompt />
      </div>

      <el-divider />

      <div class="sw-row">
        <div class="sw-main">
          <div class="sw-title">合同占用预算</div>
          <div class="sw-desc">
            开启（默认）：合同签订即<b>锁定额度</b>（强控，防止同一笔预算被重复承诺），取消 / 关闭时释放。
          </div>
          <div class="sw-desc">
            关闭：合同只<b>查看</b>预算余额、不锁额度（弱控）——
            注意在弱控下，「签了合同」不会挤占额度，后续报销仍能正常占用。
          </div>
        </div>
        <el-switch v-model="form.contractOccupy" :disabled="!canEdit"
                   active-text="占用" inactive-text="不占" inline-prompt />
      </div>

      <el-divider />

      <div style="color:#909399; font-size:12px; line-height:1.9">
        取值口径：两项都<b>缺省为「占用」</b>，即升级后行为与之前完全一致；只有显式关掉才改变。
        配置项落在系统参数 <code>budget.accrual.occupy.enabled</code> /
        <code>budget.contract.occupy.enabled</code>（1＝占用、0＝不占），备份恢复时随参数表一起走。
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { budgetApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'

// 只读用户也能看（读权限由菜单 budget:scheme:list 门控），但改要 update
const canEdit = hasPerm('budget:scheme:update')
const loading = ref(false)
const saving = ref(false)
const form = ref({ accrualOccupy: true, contractOccupy: true })

async function load() {
  loading.value = true
  try {
    const d = (await budgetApi.switchGet()) || {}
    // 后端缺省返回 true；显式 false 才关。这里不做 !! 强转，避免 null 被当成关
    form.value = {
      accrualOccupy: d.accrualOccupy !== false,
      contractOccupy: d.contractOccupy !== false,
    }
  } catch (e) {
    ElMessage.error(e.message || '读取预算口径失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await budgetApi.switchSave({
      accrualOccupy: form.value.accrualOccupy,
      contractOccupy: form.value.contractOccupy,
    })
    ElMessage.success('已保存，即时生效')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sw-row { display: flex; gap: 24px; align-items: flex-start; justify-content: space-between; }
.sw-main { flex: 1; min-width: 0; }
.sw-title { font-weight: 600; margin-bottom: 6px; }
.sw-desc { color: #606266; font-size: 12px; line-height: 1.8; }
</style>
