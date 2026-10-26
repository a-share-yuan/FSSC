<!-- SPDX-License-Identifier: Apache-2.0 -->
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
          <img class="brand-logo-img" src="../../assets/FSSC_LOGO.png" alt="FSSC 财务共享" />
          <h1 class="brand-title">集团财务<br />共享服务平台</h1>
        </div>
        <p class="brand-sub">费控 · 共享 · 资金 · 全流程数字化审批</p>
        <ul class="brand-feats">
          <li><el-icon><Connection /></el-icon><div><strong>业财税金 · 全流程贯通</strong><span>费用、应付、应收、总账、资产、税务、资金、合同八大业务域，单据到凭证全链路闭环</span></div></li>
          <li><el-icon><Tickets /></el-icon><div><strong>智能三单匹配 · 识票验真</strong><span>采购订单/入库/发票自动比对，OCR 识票、验真验重、数电票一键认证抵扣</span></div></li>
          <li><el-icon><Link /></el-icon><div><strong>产业链协同</strong><span>供应商门户与客户门户，在线对账、开票、付款状态实时同步</span></div></li>
          <li><el-icon><Money /></el-icon><div><strong>银企直联 · 资金统筹</strong><span>银企直联一键付款，资金池归集下拨，现金流实时滚动预测</span></div></li>
        </ul>
        <p class="brand-foot">© 2026 FSSC 财务共享服务中心</p>
      </aside>

      <!-- 右侧表单区 -->
      <main class="form-side">
        <div class="form-box">
          <h2 class="form-title">企业员工登录</h2>
          <p class="form-sub">使用企业编码与员工工号进入办公门户</p>

          <el-form :model="form" :rules="loginRules" label-position="top" size="large" @submit.prevent="doLogin">
            <el-form-item label="企业编码" prop="tenantCode">
              <el-input v-model="form.tenantCode" placeholder="请输入企业编码" :prefix-icon="OfficeBuilding" clearable />
            </el-form-item>
            <el-form-item label="账号" prop="username">
              <el-input v-model="form.username" placeholder="请输入工号或用户名" :prefix-icon="User" clearable />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入登录密码" :prefix-icon="Lock" @keyup.enter="doLogin" />
            </el-form-item>
            <!-- 图形验证码：仅在服务端回 4011/4012（连续失败达阈值）时出现，平时不打扰登录 -->
            <el-form-item v-if="captchaVisible" label="图形验证码" prop="captchaCode">
              <div class="captcha-line">
                <el-input v-model="form.captchaCode" placeholder="请输入图中字符" :prefix-icon="Picture" maxlength="6" clearable @keyup.enter="doLogin" />
                <img
                  v-if="captchaSrc"
                  class="captcha-img"
                  :src="captchaSrc"
                  alt="点击刷新验证码"
                  title="看不清？点击刷新"
                  @click="loadCaptcha"
                />
                <el-button v-else class="captcha-img captcha-img-fallback" :loading="captchaLoading" @click="loadCaptcha">获取</el-button>
              </div>
              <div class="captcha-hint">连续登录失败次数已达阈值，请输入右侧字符（点击图片可换一张）</div>
            </el-form-item>
            <div class="form-row">
              <el-checkbox v-model="form.rememberMe">7 天内免登录</el-checkbox>
              <el-link type="primary" :underline="false" @click="router.push('/platform/login')">
                <el-icon class="row-link-icon"><Platform /></el-icon>平台管理员入口
              </el-link>
            </div>
            <el-button class="submit-btn" type="primary" size="large" :loading="loading" @click="doLogin">登 录</el-button>
            <el-alert v-if="error" :title="error" type="error" :closable="false" class="error-tip" />
            <div class="reg-link">
              <el-link type="primary" :underline="false" @click="regVisible = true">企业还没有账号？立即注册</el-link>
            </div>
          </el-form>
        </div>
      </main>
    </div>

    <!-- 企业自助注册弹窗 -->
    <el-dialog v-model="regVisible" title="企业注册" width="480px" destroy-on-close>
      <el-form :model="reg" :rules="regRules" label-width="100px" size="default">
        <el-form-item label="企业编码" prop="tenantCode" required>
          <el-input v-model="reg.tenantCode" placeholder="2-20 位大写字母/数字/下划线，登录用" />
        </el-form-item>
        <el-form-item label="企业名称" prop="tenantName" required>
          <el-input v-model="reg.tenantName" placeholder="企业全称" />
        </el-form-item>
        <el-form-item label="企业简称" prop="shortName">
          <el-input v-model="reg.shortName" />
        </el-form-item>
        <el-form-item label="统一信用码" prop="creditCode">
          <el-input v-model="reg.creditCode" />
        </el-form-item>
        <el-form-item label="行业" prop="industry">
          <el-select v-model="reg.industry" clearable style="width: 100%">
            <el-option label="制造业" value="MANUFACTURING" />
            <el-option label="批发零售" value="TRADE" />
            <el-option label="信息技术" value="IT" />
            <el-option label="服务业" value="SERVICE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业规模" prop="scale">
          <el-select v-model="reg.scale" clearable style="width: 100%">
            <el-option label="微型" value="MICRO" />
            <el-option label="小型" value="SMALL" />
            <el-option label="中型" value="MEDIUM" />
            <el-option label="大型" value="LARGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人" prop="contactName" required>
          <el-input v-model="reg.contactName" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone" required>
          <el-input v-model="reg.contactPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="reg.contactEmail" />
        </el-form-item>
        <el-form-item label="申请说明" prop="remark">
          <el-input v-model="reg.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regVisible = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="doRegister">提交注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { OfficeBuilding, User, Lock, Tickets, Connection, Link, Money, Platform, Picture } from '@element-plus/icons-vue'
import { login, registerTenant, store, getCaptcha, CAPTCHA_ERROR_CODES, clearGlobalOverlays } from '../../api'
import { hasPerm } from '../../utils/perm'

const router = useRouter()
const form = reactive({ tenantCode: '', username: '', password: '', rememberMe: false, captchaId: '', captchaCode: '' })
const loading = ref(false)
const error = ref('')

// C-3 图形验证码：默认不显示，只有服务端判定"连续失败已达阈值"（4011/4012）才出现并自动拉图。
//  单测注意：vitest 对 mock 缺失导出是"访问即抛"而非 undefined，`|| []` 接不住 ——
// vi.mock('@/api') 工厂必须一并提供本常量与 getCaptcha（Login.spec.js 已带 [4011,4012]）。
const captchaRequiredCode = (code) => (CAPTCHA_ERROR_CODES || []).includes(code)
const captchaVisible = ref(false)
const captchaSrc = ref('')
const captchaLoading = ref(false)

// 注册
const regVisible = ref(false)
const regLoading = ref(false)
const reg = reactive({
  tenantCode: '', tenantName: '', shortName: '', creditCode: '', industry: '', scale: '',
  contactName: '', contactPhone: '', contactEmail: '', remark: '',
})

const loginRules = reactive({
  tenantCode: [{ required: true, message: '请输入企业编码' }],
  username: [{ required: true, message: '请输入账号' }],
  password: [{ required: true, message: '请输入密码' }],
})

const regRules = reactive({
  tenantCode: [{ required: true, message: '请输入企业编码' }],
  tenantName: [{ required: true, message: '请输入企业名称' }],
  contactName: [{ required: true, message: '请输入联系人' }],
  contactPhone: [{ required: true, message: '请输入联系电话' }],
  contactEmail: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
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
    const data = await login({ ...form, subjectType: 'TENANT' })
    store.token = data.token
    store.user = data.user
    // 快速切换账号时，上一会话挂在 body 上的提示条/确认框不会随路由卸载：
    // 登录成功即清场，避免新用户看到前任的提醒（本条「登录成功」在清场之后再弹）。
    clearGlobalOverlays()
    ElMessage.success('登录成功')
    // USG-01: 落地页按权限分流 —— 无 dashboard:view 的角色落工作台,避免默认页触发6个无权限聚合调用
    // R5-D-035 同族第三处落点：判定必须走 hasPerm()，否则 admin 的 *:*:* 通配码被 includes() 误判为无权、
    // 管理员登录会被分流到 /workbench 而不是 /dashboard（store.user 已在上一行赋好，此处读得到新 perms）
    router.push(hasPerm('dashboard:view') ? '/dashboard' : '/workbench')
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

async function doRegister() {
  if (!reg.tenantCode || !reg.tenantName || !reg.contactName || !reg.contactPhone) {
    ElMessage.warning('请填写企业编码、企业名称、联系人、联系电话')
    return
  }
  regLoading.value = true
  try {
    await registerTenant({ ...reg })
    // R5-D-303：此处原有一枚「管理员账号＋初始口令」明文提示，并被下方预填进登录表单 ⇒ 初始口令等同公开
    // （任何读过发布件的人都可用该口令登录新开通租户）。文案改为指向平台下发流程，初始口令不再出现在前端。
    ElMessage.success('注册申请已提交，请等待平台审核开通；管理员账号为 admin_{企业编码}，初始口令请向平台管理员索取')
    regVisible.value = false
    form.tenantCode = reg.tenantCode.toUpperCase()
    form.username = 'admin_' + reg.tenantCode.toLowerCase()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    regLoading.value = false
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
  background: linear-gradient(135deg, #0e2a6e 0%, #1d3f9e 48%, #2563c9 100%);
  overflow: hidden;
}
/* 背景光斑 */
.bg-blob { position: absolute; border-radius: 50%; filter: blur(70px); opacity: .35; pointer-events: none; }
.blob-a { width: 480px; height: 480px; left: -140px; top: -160px; background: #4f8cff; }
.blob-b { width: 420px; height: 420px; right: -120px; bottom: -140px; background: #7db0ff; }
.blob-c { width: 260px; height: 260px; right: 18%; top: 8%; background: #274b9f; opacity: .5; }

.login-panel {
  position: relative;
  display: flex;
  width: 960px;
  max-width: 100%;
  min-height: 600px;
  border-radius: 20px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 24px 70px rgba(6, 18, 60, .45);
}

/* ---------- 左侧品牌区 ---------- */
.brand-side {
  flex: 0 0 42%;
  display: flex;
  flex-direction: column;
  padding: 44px 40px 28px;
  color: #fff;
  background: linear-gradient(160deg, #16307e 0%, #1d47b8 60%, #2a63d8 100%);
}
.brand-head { display: flex; align-items: center; gap: 16px; }
.brand-logo-img {
  height: 56px; border-radius: 10px; background: #fff; padding: 6px 14px;
  box-shadow: 0 8px 20px rgba(6, 18, 60, .35); object-fit: contain; flex: none;
}
.brand-title { margin: 0; font-size: 20px; line-height: 1.3; font-weight: 700; letter-spacing: 1px; }
.brand-sub { margin: 30px 0 34px; font-size: 13px; color: rgba(255, 255, 255, .78); letter-spacing: 1px; }
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

/* ---------- 右侧表单区 ---------- */
.form-side { flex: 1; display: flex; align-items: center; justify-content: center; padding: 36px 44px; }
.form-box { width: 100%; max-width: 400px; }
.form-title { margin: 0 0 6px; font-size: 24px; color: #16244a; font-weight: 700; }
.form-sub { margin: 0 0 26px; font-size: 13px; color: #8a94a6; }
.form-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.row-link-icon { margin-right: 3px; }
/* C-3 图形验证码：输入框 + 可点击刷新的图片并排，图片 120x40 与服务端渲染尺寸一致 */
.captcha-line { display: flex; align-items: center; gap: 10px; width: 100%; }
.captcha-img {
  flex: none; width: 120px; height: 40px; border-radius: 8px; cursor: pointer;
  border: 1px solid #dcdfe6; background: #f5f7fa; object-fit: cover;
}
.captcha-img-fallback { display: inline-flex; align-items: center; justify-content: center; font-size: 13px; }
.captcha-hint { margin-top: 6px; font-size: 12px; color: #e6a23c; line-height: 1.5; }
.submit-btn { width: 100%; height: 44px; font-size: 16px; letter-spacing: 8px; border-radius: 10px; }
.error-tip { margin-top: 12px; }
.reg-link { text-align: center; margin-top: 14px; }

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .brand-side { display: none; }
  .login-panel { width: 520px; min-height: 0; }
}
@media (max-width: 560px) {
  .login-page { padding: 12px; }
  .form-side { padding: 28px 22px; }
}
</style>
