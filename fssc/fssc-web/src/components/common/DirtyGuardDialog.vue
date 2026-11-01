<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  DirtyGuardDialog —— 含表单的 el-dialog 通用「脏值守卫」薄封装。

  存在的问题：新建退货单弹窗里已填好客户/日期/方式/原因＋明细行，
  为收起展开的下拉按一下 Escape，弹窗被 el-dialog 直接关掉、输入全丢：
  el-dialog 的 Escape / 点遮罩 / 右上 X 三条关闭路径都不过任何"有未保存内容"的确认，
  键盘用户与快捷键误触一律整单丢失，其余含表单弹窗同病（本仓 70 页含表单弹窗）。

  做法：包装 el-dialog，把「关窗」收敛到 before-close 与 requestClose 两条出口，
  关之前先问一句「表单是否已脏」，脏就弹二次确认，确认了才放行；不脏则原样关闭。
  逐页改 70 处必漏 ⇒ 只做这一个封装件，页面接入三行（见文件尾"接入方式"）。

  props:
    modelValue v-model 显隐（与 el-dialog 同义）
    dirty Boolean | Function —— 是否已脏。传函数时关窗当场求值（可 async），
                      比维护一个响应式标记可靠：页面只需比对"开场快照 vs 当前值"。
    dirtyTitle 二次确认标题（默认「未保存提示」）
    dirtyMessage 二次确认正文（默认「有未保存内容，确认关闭？」）
    dirtyConfirmText 确认按钮文案（默认「放弃并关闭」）
    dirtyCancelText 取消按钮文案（默认「继续编辑」）
  其余属性（width/title/destroy-on-close/close-on-click-modal…）一律透传给 el-dialog。

  emits:
    update:modelValue 显隐回写（经守卫后才回写 false）

  slots:
    default 带插槽 props { isDirty, requestClose }
    header / title / footer 原样透传给 el-dialog

  接入方式（示例，只列不改其余页）：
    <DirtyGuardDialog ref="formDialogRef" v-model="formVisible" :dirty="isFormDirty" title="新建单据">
      ...原 el-dialog 内容...
      <template #footer>
        <el-button @click="formDialogRef?.requestClose()">取消</el-button> <-- 必须走 requestClose
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </DirtyGuardDialog>
  注意：页面直接写 formVisible = false 会绕过守卫（el-dialog 只在
  Escape/遮罩/X 三条路径上回调 before-close）；保存成功后的主动关窗可直接赋 false。

  expose: requestClose()（受守卫关闭，供取消按钮用）、isDirty()（当场求值）
-->
<template>
  <el-dialog
    v-bind="dialogAttrs"
    :model-value="modelValue"
    :before-close="handleBeforeClose"
    @update:model-value="onDialogUpdate"
  >
    <slot :is-dirty="isDirty" :request-close="requestClose" />
    <template v-if="$slots.header" #header><slot name="header" /></template>
    <template v-if="$slots.title" #title><slot name="title" /></template>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </el-dialog>
</template>

<script setup>
/**
 * 关窗守卫逻辑：
 *  - handleBeforeClose：el-dialog Escape／点遮罩／右上 X 三条路径都进这里；
 *    不脏 → done() 放行；脏 → ElMessageBox.confirm，确认才 done()，取消则静默留在原地
 *    弹窗保持打开，已录入内容一个字不动。
 *  - requestClose：页面底部「取消」按钮等主动关窗的出口，同一套判定，防绕过。
 *  - dialogAttrs：剔除父组件 v-model 落进 $attrs 的 onUpdate:modelValue 再透传，
 *    否则 el-dialog 的内部 emit 会直通父组件、绕过守卫（防漏口）。
 */
import { computed, useAttrs } from 'vue'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'DirtyGuardDialog', inheritAttrs: false })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 是否已脏：Boolean，或返回 Boolean/Promise<Boolean> 的求值函数 */
  dirty: { type: [Boolean, Function], default: false },
  dirtyTitle: { type: String, default: '未保存提示' },
  dirtyMessage: { type: String, default: '有未保存内容，确认关闭？' },
  dirtyConfirmText: { type: String, default: '放弃并关闭' },
  dirtyCancelText: { type: String, default: '继续编辑' },
})

const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs()

const dialogAttrs = computed(() => {
  // 防绕过：父组件 v-model 的监听器不能直通 el-dialog
  const { 'onUpdate:modelValue': _drop, ...rest } = attrs
  return rest
})

/** 当场求值「已脏」（函数型可 async；求值异常按未脏处理，不阻塞正常关闭） */
async function isDirty() {
  const d = props.dirty
  if (typeof d === 'function') {
    try { return !!(await d()) } catch { return false }
  }
  return !!d
}

/** 二次确认：确认放弃返回 true；取消或直接关掉确认框返回 false（= 不关主弹窗） */
async function confirmDiscard() {
  try {
    await ElMessageBox.confirm(props.dirtyMessage, props.dirtyTitle, {
      confirmButtonText: props.dirtyConfirmText,
      cancelButtonText: props.dirtyCancelText,
      type: 'warning',
      distinguishCancelAndClose: true,
    })
    return true
  } catch { return false }
}

/** before-close：Escape／点遮罩／右上 X 的必由之路 */
function handleBeforeClose(done) {
  isDirty().then((dirty) => {
    if (!dirty) { done(); return }
    confirmDiscard().then((ok) => { if (ok) done() })
  })
}

/** 受守卫的主动关窗出口（底部「取消」按钮等），语义与 Escape 一致 */
async function requestClose() {
  if (await isDirty()) {
    if (!(await confirmDiscard())) return
  }
  emit('update:modelValue', false)
}

function onDialogUpdate(val) {
  emit('update:modelValue', val)
}

defineExpose({ requestClose, isDirty })
</script>
