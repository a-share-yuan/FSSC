// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 退货管理域路由（采购退货单 / 销售退货单，BCP勾选式对账一期退货数据腿） */
export default [
  { path: 'returns/purchase-return', component: () => import('../../views/returns/PurchaseReturn.vue'), meta: { title: '采购退货单' } },
  { path: 'returns/sales-return', component: () => import('../../views/returns/SalesReturn.vue'), meta: { title: '销售退货单' } },
]
