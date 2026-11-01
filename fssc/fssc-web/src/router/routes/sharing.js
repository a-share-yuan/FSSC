// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 共享运营域路由（派工/质检/档案/实物流/SLA/影像） */
export default [
  // 共享运营
  { path: 'sharing', redirect: '/sharing/quality' },
  { path: 'sharing/quality', component: () => import('../../views/sharing/SharingQuality.vue'), meta: { title: '共享质检' } },
  // 档案（归档+借阅销毁收敛为一个工作区；/archive 深链保留）
  { path: 'sharing/archive', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'archive' }, meta: { title: '档案管理' } },
  { path: 'sharing/statement', component: () => import('../../views/sharing/SharingStatement.vue'), meta: { title: '银行流水' } },
  // 共享配置四个独立页已收进「共享运营配置」工作区（/cfg/sharing，query.tab 直达）；
  // 旧路径保留为永久深链重定向，书签/外链不悬空（菜单 visible='N' 属 DB 面，另列转人工）。
  { path: 'sharing/job', redirect: '/cfg/sharing?tab=job' },
  { path: 'sharing/group', redirect: '/cfg/sharing?tab=group' },
  { path: 'sharing/calendar', redirect: '/cfg/sharing?tab=calendar' },
  { path: 'sharing/dispatch-rule', redirect: '/cfg/sharing?tab=dispatch-rule' },
  { path: 'sharing/task-pool', component: () => import('../../views/sharing/TaskPool.vue'), meta: { title: '任务池' } },
  { path: 'sharing/task-work', component: () => import('../../views/sharing/TaskWork.vue'), meta: { title: '工单处理' } },
  // G3 共享作业层（新视图，数据源 /api/tenant/wf/pool|ops/**）
  // ②：三个视图页补 perms 前置校验（码集=其端点实需码的并集；全局守卫在 router.js 消费）：
  // shared-pool：/wf/pool/views|mine 需 wf:task:list、/wf/pool/all 需 wf:instance:list
  { path: 'sharing/shared-pool', component: () => import('../../views/sharing/SharedPool.vue'), meta: { title: '共享任务池（五视图）', perms: ['wf:task:list', 'wf:instance:list'] } },
  // shared-ops：/wf/ops/** 读 wf:config:list|wf:instance:list（含 policy/dispatch-log/quality/time-limit）
  // 「补生成凭证」页签还需 sharing:voucher:generate：只持制证权的角色（FIN_GL/AUDITOR 等）也要能进
  { path: 'sharing/shared-ops', component: () => import('../../views/sharing/SharedOpsConsole.vue'), meta: { title: '共享作业控制台', perms: ['wf:config:list', 'wf:instance:list', 'sharing:voucher:generate'] } },
  // task-quality：/wf/ops/quality/** 全部需 wf:instance:list
  { path: 'sharing/task-quality', component: () => import('../../views/sharing/TaskQualityReport.vue'), meta: { title: '任务质量报表', perms: ['wf:instance:list'] } },
  // 实物流签收：父路径重定向到首个 tab，不渲染汇总的 hub 页
  { path: 'sharing/delivery', redirect: '/sharing/delivery/sign' },
  { path: 'sharing/delivery/sign', component: () => import('../../views/sharing/DeliveryPage.vue'), props: { tab: 'sign' }, meta: { title: '签收中心' } },
  { path: 'sharing/delivery/apply', component: () => import('../../views/sharing/DeliveryPage.vue'), props: { tab: 'apply' }, meta: { title: '交单登记' } },
  { path: 'sharing/delivery/barcode', component: () => import('../../views/sharing/DeliveryPage.vue'), props: { tab: 'barcode' }, meta: { title: '条码管理' } },
  // SLA
  // MF5-05: SLA监控孤儿页已删除（无菜单/无引用/全零数据；后端 /compliance/sla/* 端点保留未动）
  // 影像 / 预算
  { path: 'image', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'image' }, meta: { title: '影像管理' } },
  { path: 'image/review', component: () => import('../../views/image/ImageReview.vue'), props: { tab: 'review' }, meta: { title: '影像调阅评价' } },
  { path: 'image/exception', component: () => import('../../views/image/ImageReview.vue'), props: { tab: 'exc' }, meta: { title: '影像异常处理' } },
  { path: 'image/task', component: () => import('../../views/image/ImageTask.vue'), meta: { title: '影像任务' } },
  { path: 'image/ocr-records', component: () => import('../../views/image/OcrRecords.vue'), meta: { title: 'OCR识别记录' } },
  // 页面级 perms 前置校验（ImageSettingController 全端点需 image:setting:list/update）
  { path: 'image/setting', component: () => import('../../views/system/ImageSetting.vue'), meta: { title: '系统设置', perms: ['image:setting:list'] } },
]
