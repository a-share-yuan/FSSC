<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>OCR 通道配置</b>
        <el-tag :type="tagType" size="small">当前生效：{{ channelLabel }}</el-tag>
        <span style="margin-left: auto; color: #909399; font-size: 13px">保存后即时生效，无需重启</span>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
      title="系统默认走「发票能力网关」：发票 OCR 识别统一由内部封装服务完成，票面字段与查验结论都出自真实通道，不做模拟识别。网关地址留空或总开关置 false 即整体回退；PaddleOCR：本机微服务的图片发票真识别；HTTP：接入任意云端 OCR 引擎，按响应结构配置字段映射；模拟 Mock 仅供演示，需显式选择才生效。" />

    <el-form :model="form" :rules="ocrRules" label-width="140px" style="max-width: 760px">
      <el-form-item label="识别通道" prop="channel">
        <el-radio-group v-model="form.channel">
          <el-radio value="">跟随系统默认（发票能力网关）</el-radio>
          <el-radio value="gateway">发票能力网关（默认）</el-radio>
          <el-radio value="paddle">PaddleOCR 本地服务</el-radio>
          <el-radio value="http">HTTP 真通道</el-radio>
          <el-radio value="mock">模拟 Mock（无外部调用，仅演示）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="网关地址" prop="gatewayUrl">
        <el-input v-model="form.gatewayUrl" placeholder="http://127.0.0.1:8096（留空用系统配置默认值）" clearable />
        <div class="tip">发票能力网关的基址，识别与真伪查验共用；上游厂商凭证只存在于网关进程内，本页面不体现</div>
      </el-form-item>
      <el-form-item label="网关密钥" prop="gatewayKey">
        <el-input v-model="form.gatewayKey" type="password" show-password
                  :placeholder="form.gatewayKeyMasked ? '已保存：' + form.gatewayKeyMasked + '（留空表示不修改）' : 'X-Gateway-Api-Key，网关未设密钥时可留空'" />
      </el-form-item>
      <el-form-item label="网关总开关" prop="gatewayEnabled">
        <el-radio-group v-model="form.gatewayEnabled">
          <el-radio value="">跟随系统配置（开）</el-radio>
          <el-radio value="true">开</el-radio>
          <el-radio value="false">关</el-radio>
        </el-radio-group>
        <div class="tip">选「关」时识别退回其余通道、验真退回「不判定」口径，绝不产出模拟票面或假验真结论</div>
      </el-form-item>
      <el-form-item label="PaddleOCR 地址" prop="paddleUrl">
        <el-input v-model="form.paddleUrl" placeholder="http://127.0.0.1:8087/invoice_ocr（留空用默认本机地址）" clearable />
        <div class="tip">通道选 PaddleOCR 时生效；POST 图片至该端点，响应即标准票面字段，无需映射</div>
      </el-form-item>
      <el-form-item label="PaddleOCR 密钥" prop="paddleKey">
        <el-input v-model="form.paddleKey" type="password" show-password
                  :placeholder="form.paddleKeyMasked ? '已保存：' + form.paddleKeyMasked + '（留空表示不修改）' : 'X-Ocr-Key，OCR 服务未设 OCR_API_KEY 时可留空'" />
      </el-form-item>
      <el-form-item label="API 地址" prop="url">
        <el-input v-model="form.url" placeholder="https://ocr.example.com/recognize" clearable />
        <div class="tip">示例为占位假地址；通道选 HTTP 时必填真实服务地址</div>
      </el-form-item>
      <el-form-item label="API Token" prop="token">
        <el-input v-model="form.token" type="password" show-password
                  :placeholder="form.tokenMasked ? '已保存：' + form.tokenMasked + '（留空表示不修改）' : 'Bearer Token，可留空'" />
      </el-form-item>
      <el-form-item label="字段映射(JSON)" prop="mapping">
        <el-input v-model="form.mapping" type="textarea" :rows="8"
          placeholder='{"invoiceNo":"result.ocr_number","amount":"result.ocr_amount", ...} 留空=内置百度式默认' />
        <div class="tip">
          键=系统字段（invoiceCode/invoiceType/invoiceDate/invoiceNo/amount/taxAmount/netAmount/sellerName/sellerTaxNo/buyerName/buyerTaxNo），
          值=识别响应的点号取值路径。<el-button link type="primary" size="small" @click="validateMapping">校验 JSON</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        <el-button @click="load">重新加载</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi } from '../../api'

const form = reactive({ channel: '', url: '', token: '', mapping: '', tokenMasked: '', paddleUrl: '', paddleKey: '', paddleKeyMasked: '',
  gatewayUrl: '', gatewayKey: '', gatewayKeyMasked: '', gatewayEnabled: '', effectiveChannel: 'gateway' })
const saving = ref(false)

const CHANNEL_LABELS = { gateway: '发票能力网关（默认）', http: 'HTTP 真通道', paddle: 'PaddleOCR 本地服务', mock: '模拟 Mock' }
const channelLabel = computed(() => CHANNEL_LABELS[form.effectiveChannel] || '发票能力网关（默认）')
const tagType = computed(() => form.effectiveChannel === 'gateway' ? 'success'
  : form.effectiveChannel === 'http' ? 'warning'
  : form.effectiveChannel === 'paddle' ? 'success' : 'info')

const ocrRules = reactive({
  url: [{ required: false, message: '请输入API地址', trigger: 'blur' }],
})

async function load() {
  try {
    const d = await configApi.ocrGet()
    form.channel = d.channel || ''
    form.url = d.url || ''
    form.mapping = d.mapping || ''
    form.tokenMasked = d.tokenMasked || ''
    form.paddleUrl = d.paddleUrl || ''
    form.paddleKeyMasked = d.paddleKeyMasked || ''
    form.gatewayUrl = d.gatewayUrl || ''
    form.gatewayKeyMasked = d.gatewayKeyMasked || ''
    form.gatewayEnabled = d.gatewayEnabled || ''
    form.effectiveChannel = d.effectiveChannel || 'gateway'
    form.token = ''
    form.paddleKey = ''
    form.gatewayKey = ''
  } catch (e) { ElMessage.error(e.message) }
}

function validateMapping() {
  if (!form.mapping.trim()) { ElMessage.success('留空将使用内置默认映射'); return }
  try { JSON.parse(form.mapping); ElMessage.success('JSON 格式合法') }
  catch (e) { ElMessage.error('JSON 不合法：' + e.message) }
}

async function save() {
  if (form.mapping && form.mapping.trim()) {
    try { JSON.parse(form.mapping) } catch (e) { ElMessage.error('字段映射 JSON 不合法，请修正后保存'); return }
  }
  if (form.channel === 'http' && !form.url.trim()) { ElMessage.warning('HTTP 通道必须填写 API 地址'); return }
  saving.value = true
  try {
    await configApi.ocrSave({ channel: form.channel, url: form.url, token: form.token, mapping: form.mapping,
      paddleUrl: form.paddleUrl, paddleKey: form.paddleKey,
      gatewayUrl: form.gatewayUrl, gatewayKey: form.gatewayKey, gatewayEnabled: form.gatewayEnabled })
    ElMessage.success('已保存并即时生效')
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.tip { color: #909399; font-size: 12px; line-height: 1.6; }
</style>
