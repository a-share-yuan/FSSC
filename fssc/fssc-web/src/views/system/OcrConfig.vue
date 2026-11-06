<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('system.ocr.ttlChannel') }}</b>
        <el-tag :type="tagType" size="small">{{ $t('system.ocr.statEffective', { v1: channelLabel }) }}</el-tag>
        <span style="margin-left: auto; color: #909399; font-size: 13px">{{ $t('system.ocr.hintInstant') }}</span>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
      :title="$t('system.ocr.hintChannels')" />

    <el-form :model="form" :rules="ocrRules" label-width="140px" style="max-width: 760px">
      <el-form-item :label="$t('system.ocr.lblChannel')" prop="channel">
        <el-radio-group v-model="form.channel">
          <el-radio value="">{{ $t('system.ocr.optFollowSystem') }}</el-radio>
          <el-radio value="gateway">{{ $t('system.ocr.optGateway') }}</el-radio>
          <el-radio value="paddle">{{ $t('system.ocr.optPaddleLocal') }}</el-radio>
          <el-radio value="http">{{ $t('system.ocr.optHttpReal') }}</el-radio>
          <el-radio value="mock">{{ $t('system.ocr.optMockDemo') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblGatewayUrl')" prop="gatewayUrl">
        <el-input v-model="form.gatewayUrl" :placeholder="$t('system.ocr.phGatewayUrl')" clearable />
        <div class="tip">{{ $t('system.ocr.hintGatewayUrl') }}</div>
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblGatewayKey')" prop="gatewayKey">
        <el-input v-model="form.gatewayKey" type="password" show-password
                  :placeholder="form.gatewayKeyMasked ? $t('system.ocr.hintSavedMasked', { v1: form.gatewayKeyMasked }) : t('system.ocr.phGatewayKey')" />
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblGatewaySwitch')" prop="gatewayEnabled">
        <el-radio-group v-model="form.gatewayEnabled">
          <el-radio value="">{{ $t('system.ocr.optFollowConfig') }}</el-radio>
          <el-radio value="true">{{ $t('system.ocr.optOn') }}</el-radio>
          <el-radio value="false">{{ $t('system.ocr.optOff') }}</el-radio>
        </el-radio-group>
        <div class="tip">{{ $t('system.ocr.hintGatewayOff') }}</div>
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblPaddleUrl')" prop="paddleUrl">
        <el-input v-model="form.paddleUrl" :placeholder="$t('system.ocr.phPaddleUrl')" clearable />
        <div class="tip">{{ $t('system.ocr.hintPaddleUrl') }}</div>
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblPaddleKey')" prop="paddleKey">
        <el-input v-model="form.paddleKey" type="password" show-password
                  :placeholder="form.paddleKeyMasked ? $t('system.ocr.hintSavedMasked', { v1: form.paddleKeyMasked }) : t('system.ocr.phPaddleKey')" />
      </el-form-item>
      <el-form-item :label="$t('system.common.colApiUrl')" prop="url">
        <el-input v-model="form.url" placeholder="https://ocr.example.com/recognize" clearable />
        <div class="tip">{{ $t('system.ocr.hintHttpUrl') }}</div>
      </el-form-item>
      <el-form-item label="API Token" prop="token">
        <el-input v-model="form.token" type="password" show-password
                  :placeholder="form.tokenMasked ? $t('system.ocr.hintSavedMasked', { v1: form.tokenMasked }) : t('system.ocr.phBearerToken')" />
      </el-form-item>
      <el-form-item :label="$t('system.ocr.lblFieldMapping')" prop="mapping">
        <el-input v-model="form.mapping" type="textarea" :rows="8"
          :placeholder="$t('system.ocr.phMappingJson')" />
        <div class="tip">
          {{ $t('system.ocr.hintMappingKeys') }}
          {{ $t('system.ocr.hintMappingValues') }}<el-button link type="primary" size="small" @click="validateMapping">{{ $t('system.common.btnCheckJson') }}</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('system.ocr.btnSaveConfig') }}</el-button>
        <el-button @click="load">{{ $t('system.ocr.btnReload') }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi } from '../../api'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const form = reactive({ channel: '', url: '', token: '', mapping: '', tokenMasked: '', paddleUrl: '', paddleKey: '', paddleKeyMasked: '',
  gatewayUrl: '', gatewayKey: '', gatewayKeyMasked: '', gatewayEnabled: '', effectiveChannel: 'gateway' })
const saving = ref(false)

const CHANNEL_LABELS = computed(() => ({ gateway: t('system.ocr.optGateway'), http: t('system.ocr.optHttpReal'), paddle: t('system.ocr.optPaddleLocal'), mock: t('system.ocr.optMock') }))
const channelLabel = computed(() => CHANNEL_LABELS.value[form.effectiveChannel] || t('system.ocr.optGateway'))
const tagType = computed(() => form.effectiveChannel === 'gateway' ? 'success'
  : form.effectiveChannel === 'http' ? 'warning'
  : form.effectiveChannel === 'paddle' ? 'success' : 'info')

const ocrRules = computed(() => (reactive({
  url: [{ required: false, message: t('system.ocr.errApiUrlRequired'), trigger: 'blur' }],
})))

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
  if (!form.mapping.trim()) { ElMessage.success(t('system.ocr.hintMappingEmpty')); return }
  try { JSON.parse(form.mapping); ElMessage.success(t('system.common.msgJsonValid')) }
  catch (e) { ElMessage.error(t('system.ocr.errJsonInvalid') + e.message) }
}

async function save() {
  if (form.mapping && form.mapping.trim()) {
    try { JSON.parse(form.mapping) } catch (e) { ElMessage.error(t('system.ocr.errMappingJson')); return }
  }
  if (form.channel === 'http' && !form.url.trim()) { ElMessage.warning(t('system.ocr.errHttpNeedsUrl')); return }
  saving.value = true
  try {
    await configApi.ocrSave({ channel: form.channel, url: form.url, token: form.token, mapping: form.mapping,
      paddleUrl: form.paddleUrl, paddleKey: form.paddleKey,
      gatewayUrl: form.gatewayUrl, gatewayKey: form.gatewayKey, gatewayEnabled: form.gatewayEnabled })
    ElMessage.success(t('system.ocr.msgSavedInstant'))
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.tip { width: 100%; color: #909399; font-size: 12px; line-height: 1.6; }
</style>
