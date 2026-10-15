<template>
  <span class="upload-btn-wrap" style="display:inline-flex;align-items:center;gap:8px">
    <el-upload :show-file-list="false" :http-request="doUpload" :accept="accept" :multiple="multiple" style="display:inline-block">
      <el-button :type="type" size="small">{{ busy && parse ? '识别中…' : label }}</el-button>
    </el-upload>
    <!-- D-DEC-463（10-05）：识别是同步串行的长请求（扫描版 PDF 可达几十秒），给用户一个"不再等"的出口。
         按钮只说"取消等待"、不说"取消上传"——前端 abort() 并不会让服务端停手（识别链是 REQUIRES_NEW），
         中断后一律按"结果未知"提示，并让用户先去票夹核对，别再传同一张票。 -->
    <el-button v-if="busy && parse" size="small" text type="danger" @click="cancelWaiting">取消等待</el-button>
  </span>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { fileApi, imageApi, isRequestCancelled } from '../api'
import { abortedOutcome } from '../utils/invoiceOutcome'

/**
 * 通用上传按钮（修复"点上传无反应"：改用 el-upload http-request 标准链路，走 axios 带 token）。
 * mode=file 走文件服务 /tenant/system/file/upload；mode=image 走影像服务 /tenant/image/batch/upload。
 */
const props = defineProps({
  label: { type: String, default: '上传文件' },
  type: { type: String, default: 'warning' },
  accept: { type: String, default: '' },
  multiple: { type: Boolean, default: false },
  bizType: { type: String, default: 'OTHER' },
  bizId: { type: Number, default: null },
  mode: { type: String, default: 'file' },
  // 存储区域：TEMP 临时附件（草稿）/ BUSINESS 业务正式影像（审批中）/ ARCHIVE 归档档案
  zone: { type: String, default: 'BUSINESS' },
  // parse=true：上传走 /expense/attach/upload，对 ofd/xml/pdf 自动解析返回 {id,fileName,attachType,invoice}
  parse: { type: Boolean, default: false },
  onDone: { type: Function, default: null },
})

/**
 * 单文件体积上限（MB）。与后端 `fssc.file.max-size` / `spring.servlet.multipart.max-file-size` 对齐。
 * 前端先拦是为了不让大文件白传一趟；后端仍会二次校验并返回明确原因（）。
 * 2026-09-12 第二次对齐：后端 multipart 上限已放宽到 20MB（原 10MB 会把 15MB 的合规发票影像挡在门外），
 * 前端阈值同步改为 20MB。**改这里必须同时改 application.yml 的 max-file-size / fssc.file.max-size**，
 * 三者不一致就会出现「前端放行、后端静默拒绝」或反之。
 */
const MAX_SIZE_MB = 20

/**
 * D-DEC-463：本按钮在飞的请求集合。
 * 写成数组而不是单枚＝el-upload 的 multiple 会**逐文件各调一次** doUpload，
 * 单枚 controller 会被后一次赋值顶掉 ⇒「取消等待」只中断最后一条、其余仍在跑却不再有任何出口
 * （10-05 登记时已把这条列为已知限制，本轮收掉）。
 */
const busy = ref(false)
const controllers = []

function newController() {
  const c = new AbortController()
  controllers.push(c)
  return c
}

function dropController(c) {
  const i = controllers.indexOf(c)
  if (i >= 0) controllers.splice(i, 1)
}

function cancelWaiting() {
  controllers.slice().forEach((c) => {
    try { c.abort() } catch (e) { /* 已 abort 的重复调用不影响 */ }
  })
  controllers.length = 0
  busy.value = false
}

// 离开本页时同样收口：在飞的识别请求不断线＝服务端照写，但本页已无人接收结果（见 abortedOutcome）
onBeforeUnmount(cancelWaiting)

async function doUpload({ file, onSuccess, onError }) {
  if (file && file.size > MAX_SIZE_MB * 1024 * 1024) {
    const mb = (file.size / 1024 / 1024).toFixed(1)
    ElMessage.error(`附件体积 ${mb}MB 超过上限 ${MAX_SIZE_MB}MB，请压缩或拆分后重试`)
    onError && onError(new Error('FILE_TOO_LARGE'))
    return
  }
  // D-DEC-474 界面腿：PDF 这一支的等待时长由文件类型决定，事先说明免得用户以为卡死重复上传。
  // 电子发票（有文本层）走本地版式解析＝秒级；扫描版 PDF 才会转图像识别通道＝几十秒量级。
  // 文案只陈述后端真实次序（本地解析优先，解析不出才发识别请求），不承诺结果。
  if (props.parse && /\.pdf$/i.test((file && file.name) || '')) {
    ElMessage({
      type: 'info',
      duration: 4000,
      message: 'PDF 解析中：电子发票走本地版式解析（秒级）；扫描版会自动转图像识别，约需 30~60 秒，请勿离开本页',
    })
  }
  const controller = newController()
  busy.value = true
  try {
    let rec = null
    if (props.mode === 'image') {
      const fd = new FormData()
      fd.append('files', file)
      fd.append('bizType', props.bizType || 'OTHER')
      fd.append('bizId', props.bizId == null ? '' : String(props.bizId))
      fd.append('zone', props.zone || 'BUSINESS')
      await imageApi.batchUpload(fd)
    } else {
      rec = props.parse
        ? await fileApi.attachUpload(file, props.bizId == null ? null : props.bizId, controller.signal)
        : await fileApi.upload(file, props.bizType || 'OTHER', props.bizId, props.zone || 'BUSINESS')
    }
    ElMessage.success('上传成功')
    onSuccess && onSuccess()
    props.onDone && props.onDone(rec)
  } catch (e) {
    if (isRequestCancelled(e)) {
      // D-DEC-463：用户中断的是"等待"，不是服务端的写入 ⇒ 明示结果未知、先去票夹核对
      const out = abortedOutcome(file && file.name)
      ElMessage({ type: out.tone, duration: 6000, message: out.message })
      onError && onError(e)
      return
    }
    // 回显后端原因（体积超限/格式不支持等），不再只有一句笼统的“上传失败”
    ElMessage.error('上传失败：' + ((e && (e.msg || e.message)) || '未知错误'))
    onError && onError(e)
  } finally {
    dropController(controller)
    // multiple 批量时仍有别的文件在飞 ⇒ busy 取"还有没有在等的请求"，一个完成就把整排的等待态熄掉
    busy.value = controllers.length > 0
  }
}
</script>
