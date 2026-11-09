// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 可配置表单域路由。
 *
 * 只登记三条：我的申请、表单设计、以及按 formCode 承载任意自定义表单的通用页。
 * 客户新建的表单不需要新增路由 —— 菜单里登记的 path 指到 `/form/{formCode}` 即可，
 * 页面本身按元数据渲染。
 */
export default [
  { path: 'form/my', component: () => import('../../views/form/FormBillList.vue'), meta: { title: '我的申请' } },
  // 通用单据页：新建、编辑、只读详情共用一个组件
  { path: 'form/:formCode', component: () => import('../../views/form/FormBill.vue'), meta: { title: '单据' } },
]
