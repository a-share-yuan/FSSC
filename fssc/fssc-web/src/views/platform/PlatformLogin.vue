<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-blob blob-a"></div>
    <div class="bg-blob blob-b"></div>
    <div class="bg-blob blob-c"></div>

    <div class="login-panel">
      <!-- 左侧品牌区 -->
      <aside class="brand-side">
        <div class="brand-head">
          <div class="brand-logo">
            <el-icon :size="26"><Platform /></el-icon>
          </div>
          <span class="brand-name">{{ $t('platform.platformLogin.FSSC') }}</span>
        </div>
        <h1 class="brand-title">平台运营<br />{{ $t('platform.platformLogin.Management') }}</h1>
        <p class="brand-sub">租户 · 套餐 · 监控 · 平台级统一运营</p>
        <ul class="brand-feats">
          <li><el-icon><OfficeBuilding /></el-icon><div><strong>{{ $t('platform.platformLogin.Full') }}</strong><span>{{ $t('platform.platformLogin.One') }}</span></div></li>
          <li><el-icon><Box /></el-icon><div><strong>{{ $t('platform.platformLogin.Packages') }}</strong><span>{{ $t('platform.platformLogin.Flexible') }}</span></div></li>
          <li><el-icon><TrendCharts /></el-icon><div><strong>{{ $t('platform.platformLogin.Operations') }}</strong><span>{{ $t('platform.platformLogin.Tenant') }}</span></div></li>
          <li><el-icon><Lock /></el-icon><div><strong>{{ $t('platform.platformLogin.Platform') }}</strong><span>{{ $t('platform.platformLogin.Unified') }}</span></div></li>
        </ul>
        <p class="brand-foot">© 2026 FSSC 财务共享服务中心</p>
      </aside>

      <!-- 右侧表单区 -->
      <main class="form-side">
        <div class="form-box">
          <h2 class="form-title">{{ $t('platform.platformLogin.Platform2') }}</h2>
          <p class="form-sub">仅限平台运营人员，管理全部租户与套餐</p>

          <el-form :model="form" :rules="loginRules" label-position="top" size="large" @submit.prevent="doLogin">
            <el-form-item :label="$t('login.main.Account')" prop="username">
              <el-input v-model="form.username" :placeholder="$t('platform.platformLogin.Enter')" :prefix-icon="User" clearable maxlength="64" />
            </el-form-item>
            <el-form-item :label="$t('login.main.Password')" prop="password">
              <el-input v-model="form.password" type="password" show-password :placeholder="$t('login.main.Please')" :prefix-icon="Lock" @keyup.enter="doLogin" />
            </el-form-item>
            <!-- 图形验证码：仅在服务端回 4011/4012（连续失败达阈值）时出现，平时不打扰登录 -->
            <el-form-item v-if="captchaVisible" :label="$t('login.main.Captcha')" prop="captchaCode">
              <div class="captcha-line">
                <el-input v-model="form.captchaCode" :placeholder="$t('login.main.Enter3')" :prefix-icon="Picture" maxlength="6" clearable @keyup.enter="doLogin" />
                <img
                  v-if="captchaSrc"
                  class="captcha-img"
                  :src="captchaSrc"
                  alt="点击刷新验证码"
                  :title="$t('login.main.Can')"
                  @click="loadCaptcha"
                />
                <el-button v-else class="captcha-img captcha-img-fallback" :loading="captchaLoading" @click="loadCaptcha">{{ $t('common.get') }}</el-button>
              </div>
              <div class="captcha-hint">连续登录失败次数已达阈值，请输入右侧字符（点击图片可换一张）</div>
            </el-form-item>
            <div class="form-row">
              <el-checkbox v-model="form.rememberMe">7 天内免登录</el-checkbox>
              <el-link type="primary" :underline="false" @click="router.push('/login')">
                <el-icon class="row-link-icon"><OfficeBuilding /></el-icon>企业员工入口
              </el-link>
            </div>
            <el-button class="submit-btn" type="primary" size="large" :loading="loading" @click="doLogin">{{ $t('login.main.Sign') }}</el-button>
            <el-alert v-if="error" :title="error" type="error" :closable="false" class="error-tip" />
          </el-form>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Platform, OfficeBuilding, Box, TrendCharts, Picture } from '@element-plus/icons-vue'
import { login, store, getCaptcha, CAPTCHA_ERROR_CODES } from '../../api'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const router = useRouter()
const form = reactive({ username: '', password: '', rememberMe: false, captchaId: '', captchaCode: '' })
const loading = ref(false)
const error = ref('')

// C-3 图形验证码（平台端与企业端同一套后端机制）：默认不显示，
// 服务端判定"该身份连续失败已达阈值"（4011/4012）时才出现并自动拉图
const captchaRequiredCode = (code) => (CAPTCHA_ERROR_CODES || []).includes(code)
const captchaVisible = ref(false)
const captchaSrc = ref('')
const captchaLoading = ref(false)

const loginRules = reactive({
  username: [{ required: true, message: t('login.main.Please2') }],
  password: [{ required: true, message: t('login.main.Please3') }],
})

/** 拉一张一次性验证码（点击图片也走这里）：captchaId 与 image 成对下发，一次一用 */
async function loadCaptcha() {
  captchaLoading.value = true
  form.captchaId = ''
  form.captchaCode = ''
  captchaSrc.value = ''
  try {
    const c = await getCaptcha()
    form.captchaId = c?.captchaId || ''
    captchaSrc.value = c?.image || ''
    if (!form.captchaId || !captchaSrc.value) {
      error.value = '验证码获取失败，请点击图片重试'
    }
  } catch (e) {
    error.value = e?.message || '验证码获取失败，请稍后重试'
  } finally {
    captchaLoading.value = false
  }
}

async function doLogin() {
  if (captchaVisible.value && !form.captchaCode) {
    error.value = '请输入图形验证码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await login({ ...form, subjectType: 'PLATFORM' })
    store.token = data.token
    store.user = data.user
    ElMessage.success(t('login.main.Signed'))
    router.push('/platform/console')
  } catch (e) {
    error.value = e.message
    if (captchaRequiredCode(e?.code)) {
      // 4011/4012：服务端已要求验证码 —— 显示输入框并自动拉图
      captchaVisible.value = true
      await loadCaptcha()
    } else if (captchaVisible.value) {
      // 验证码一次一用：本轮无论成败都已消费，失败后必须换一张，否则下次提交必被判无效
      await loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #10305f 0%, #144a7c 48%, #1a6a9e 100%);
  overflow: hidden;
}
/* 背景光斑 */
.bg-blob { position: absolute; border-radius: 50%; filter: blur(70px); opacity: .35; pointer-events: none; }
.blob-a { width: 480px; height: 480px; left: -140px; top: -160px; background: #3fa4ff; }
.blob-b { width: 420px; height: 420px; right: -120px; bottom: -140px; background: #6fc3ff; }
.blob-c { width: 260px; height: 260px; right: 18%; top: 8%; background: #1f5c8f; opacity: .5; }

.login-panel {
  position: relative;
  display: flex;
  width: 960px;
  max-width: 100%;
  min-height: 560px;
  border-radius: 20px;
  overflow: hidden;
  /* 深浅色体检：表单卡随主题走（写死 #fff 时深色下白卡配黑输入框）；品牌渐变区保持原设计 */
  background: var(--app-card-bg);
  box-shadow: 0 24px 70px rgba(4, 22, 48, .45);
}

/* 左侧品牌区 */
.brand-side {
  flex: 0 0 42%;
  display: flex;
  flex-direction: column;
  padding: 44px 40px 28px;
  color: #fff;
  background: linear-gradient(160deg, #0d2b52 0%, #10538a 60%, #1470ab 100%);
}
.brand-head { display: flex; align-items: center; gap: 10px; }
.brand-logo {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, .16);
  border: 1px solid rgba(255, 255, 255, .28);
}
.brand-name { font-size: 16px; font-weight: 600; letter-spacing: 1px; }
.brand-title { margin: 42px 0 10px; font-size: 30px; line-height: 1.35; font-weight: 700; letter-spacing: 2px; }
.brand-sub { margin: 0 0 34px; font-size: 13px; color: rgba(255, 255, 255, .78); letter-spacing: 1px; }
.brand-feats { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 20px; }
.brand-feats li { display: flex; align-items: flex-start; gap: 12px; }
.brand-feats .el-icon {
  flex: none; margin-top: 2px; width: 34px; height: 34px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, .14); border: 1px solid rgba(255, 255, 255, .22); font-size: 17px;
}
.brand-feats strong { display: block; font-size: 14px; margin-bottom: 3px; }
.brand-feats span { font-size: 12px; color: rgba(255, 255, 255, .68); line-height: 1.5; }
.brand-foot { margin-top: auto; font-size: 12px; color: rgba(255, 255, 255, .5); }

/* 右侧表单区 */
.form-side { flex: 1; display: flex; align-items: center; justify-content: center; padding: 36px 44px; }
.form-box { width: 100%; max-width: 400px; }
.form-title { margin: 0 0 6px; font-size: 24px; color: var(--app-text); font-weight: 700; }
.form-sub { margin: 0 0 26px; font-size: 13px; color: var(--app-text-secondary); }
.form-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.row-link-icon { margin-right: 3px; }
/* C-3 图形验证码：输入框 + 可点击刷新的图片并排，图片 120x40 与服务端渲染尺寸一致 */
.captcha-line { display: flex; align-items: center; gap: 10px; width: 100%; }
.captcha-img {
  flex: none; width: 120px; height: 40px; border-radius: 8px; cursor: pointer;
  border: 1px solid var(--app-border); background: #f5f7fa; object-fit: cover;
}
.captcha-img-fallback { display: inline-flex; align-items: center; justify-content: center; font-size: 13px; }
.captcha-hint { margin-top: 6px; font-size: 12px; color: #e6a23c; line-height: 1.5; }
.submit-btn { width: 100%; height: 44px; font-size: 16px; letter-spacing: 8px; border-radius: 10px; }
.error-tip { margin-top: 12px; }

/* 响应式 */
@media (max-width: 900px) {
  .brand-side { display: none; }
  .login-panel { width: 520px; min-height: 0; }
}
@media (max-width: 560px) {
  .login-page { padding: 12px; }
  .form-side { padding: 28px 22px; }
}
</style>
