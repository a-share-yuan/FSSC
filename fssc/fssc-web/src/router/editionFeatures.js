// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 档位能力路由闸门（由 scripts/gen_edition_features.py 生成，请勿手工编辑）。
 *
 * 背景：菜单裁剪只作用于侧栏，超管在路由守卫里被 isSuperPerm() 直接放行，
 * 因此切到报销版/费控版后仍可敲地址进入应收、应付、总账等共享类页面，
 * 报表工作台内部的分组按钮也不受菜单裁剪影响。
 *
 * 判据与后端 MenuService.filterByFeature 同源：sys_menu.feature_code。
 * 三档套餐的 features 是登录时写入 JWT 的快照，切换档位后重新登录即生效。
 * 本闸门对包括超管在内的所有账号生效——档位能力不等于用户权限。
 */

import { store } from '../api'

/** sys_menu 登记的页面路径 → 特性码 */
export const EDITION_FEATURE_BY_PATH = {
  '/apflow': 'FSSC_AP',
  '/apflow/ap-month-close': 'FSSC_AP',
  '/approval': 'WF_COMMON',
  '/arflow': 'FSSC_AR',
  '/asset/asset': 'FSSC_FA',
  '/asset/depr-batch': 'FSSC_FA',
  '/audit/reconcile': 'SYS_OPS',
  '/bank/reconcile': 'FSSC_SHARED',
  '/bcp-objection': 'FSSC_BCP',
  '/budget/workbench': 'F_BUDGET',
  '/cfg/budget': 'F_BUDGET',
  '/cfg/expense': 'FC_STANDARD',
  '/cfg/finance': 'SYS_MASTER',
  '/cfg/loan': 'SYS_ADVANCED',
  '/cfg/ops': 'SYS_OPS',
  '/cfg/org': 'SYS_BASIC',
  '/cfg/sharing': 'FSSC_SHARED',
  '/cfg/system': 'SYS_BASIC',
  '/compliance/board': 'FSSC_COMPLIANCE',
  '/compliance/expense-policy': 'FC_STANDARD',
  '/compliance/fraud': 'SYS_OPS',
  '/compliance/precheck-stats': 'FSSC_SHARED',
  '/compliance/review-rules': 'FSSC_SHARED',
  '/contract': 'FSSC_AP',
  '/corp-pay/prepay': 'FC_CORPPAY',
  '/corp-pay/settle': 'FC_CORPPAY',
  '/corp-pay/voucher': 'FSSC_GL',
  '/customer': 'SYS_MASTER',
  '/expense/air-query': 'FC_CORPPAY',
  '/expense/apply': 'FC_APPLY',
  '/expense/claim': 'FC_EXPENSE',
  '/expense/flow': 'FC_WF_CONFIG',
  '/expense/loan': 'FC_LOAN',
  '/expense/my': 'WF_COMMON',
  '/flow-center': 'FSSC_WF_CENTER',
  '/flow-monitor': 'FSSC_WF_CENTER',
  '/form/DEMO_FORM': 'FC_FORM_PLATFORM',
  '/form/my': 'FC_FORM_PLATFORM',
  '/glex/consolidate': 'FSSC_GL',
  '/glex/fx-reval': 'FSSC_GL',
  '/glex/special': 'FSSC_GL',
  '/glex/tax-refund': 'FSSC_GL',
  '/image': 'FSSC_IMAGE',
  '/image/ocr-records': 'FSSC_IMAGE',
  '/image/review': 'FSSC_IMAGE',
  '/invoice/query': 'FSSC_TAX',
  '/master/bank': 'SYS_MASTER',
  '/master/cip': 'FSSC_FA',
  '/master/cost-center': 'SYS_MASTER',
  '/master/expense-standard': 'FC_STANDARD',
  '/master/invoice-rule': 'SYS_ADVANCED',
  '/my/delegate': 'WF_COMMON',
  '/op/gl': 'FSSC_GL',
  '/op/gl/balance': 'FSSC_GL',
  '/op/gl/trial': 'FSSC_GL',
  '/op/gl/voucher': 'FSSC_GL',
  '/pay': 'FSSC_FUND',
  '/pay/match': 'FSSC_SHARED',
  '/pay/recon': 'FSSC_AP',
  '/purchase/order': 'FSSC_AP',
  '/purchase/pay-plan': 'FSSC_AP',
  '/purchase/receipt': 'FSSC_AP',
  '/report/summary': 'SYS_OPS',
  '/reports': 'FC_REPORT',
  '/reports/ap-aging': 'FSSC_REPORT',
  '/reports/ar-aging': 'FSSC_REPORT',
  '/reports/bank-balance': 'FSSC_REPORT',
  '/reports/budget': 'F_BUDGET',
  '/reports/budget-alert': 'F_BUDGET',
  '/reports/cash-flow': 'FSSC_REPORT',
  '/reports/composition': 'FC_REPORT',
  '/reports/contract': 'FSSC_REPORT',
  '/reports/contract-exec': 'FSSC_REPORT',
  '/reports/dept': 'FC_REPORT',
  '/reports/detail': 'FC_REPORT',
  '/reports/eff': 'FSSC_REPORT',
  '/reports/g-ap': 'FSSC_REPORT',
  '/reports/g-ar': 'FSSC_REPORT',
  '/reports/g-cash': 'FSSC_REPORT',
  '/reports/g-contract': 'FSSC_REPORT',
  '/reports/g-loan': 'FC_REPORT',
  '/reports/g-ops': 'FSSC_REPORT',
  '/reports/g-voucher': 'FSSC_REPORT',
  '/reports/image': 'FSSC_REPORT',
  '/reports/input-invoice': 'FSSC_REPORT',
  '/reports/loan': 'FC_REPORT',
  '/reports/loan-overdue': 'FC_REPORT',
  '/reports/ops': 'FSSC_REPORT',
  '/reports/ops-eff': 'FSSC_REPORT',
  '/reports/output-invoice': 'FSSC_REPORT',
  '/reports/quality': 'FSSC_REPORT',
  '/reports/summary': 'FC_REPORT',
  '/reports/supplier-pay': 'FSSC_REPORT',
  '/reports/tax-burden': 'FSSC_REPORT',
  '/reports/trend': 'FC_REPORT',
  '/reports/voucher-stats': 'FSSC_REPORT',
  '/requisition/mine': 'WF_COMMON',
  '/returns/purchase-return': 'FSSC_AP',
  '/returns/sales-return': 'FSSC_AR',
  '/sharing/archive': 'FSSC_ARCHIVE',
  '/sharing/delivery': 'FSSC_SHARED',
  '/sharing/quality': 'FSSC_SHARED',
  '/sharing/shared-ops': 'FSSC_SHARED',
  '/sharing/task-pool': 'FSSC_SHARED',
  '/sharing/task-work': 'FSSC_SHARED',
  '/system/ext-interface': 'SYS_ADVANCED',
  '/system/log': 'SYS_OPS',
  '/system/org': 'SYS_BASIC',
  '/system/period': 'SYS_BASIC',
  '/system/post': 'SYS_BASIC',
  '/system/role': 'SYS_ADVANCED',
  '/system/status': 'SYS_OPS',
  '/system/supplier': 'SYS_MASTER',
  '/system/user': 'SYS_BASIC',
  '/tax/workbench': 'FSSC_GL',
  '/treasury': 'FSSC_FUND',
  '/wf/instance': 'FSSC_WF_CENTER',
  '/wf/timeout/stats': 'FSSC_WF_CENTER',
}

/** 菜单行已软删 / 未登记、但前端路由仍注册的共享类业务域前缀（按长度降序） */
export const EDITION_PREFIX_FEATURES = [
  ['/receivable', 'FSSC_AR'],
  ['/purchase', 'FSSC_AP'],
  ['/contract', 'FSSC_AP'],
  ['/corp-pay', 'FSSC_AP'],
  ['/treasury', 'FSSC_FUND'],
  ['/returns', 'FSSC_AP'],
  ['/archive', 'FSSC_ARCHIVE'],
  ['/sharing', 'FSSC_SHARED'],
  ['/invoice', 'FSSC_TAX'],
  ['/arflow', 'FSSC_AR'],
  ['/apflow', 'FSSC_AP'],
  ['/asset', 'FSSC_FA'],
  ['/image', 'FSSC_IMAGE'],
  ['/op/gl', 'FSSC_GL'],
  ['/bank', 'FSSC_BANK'],
  ['/tax', 'FSSC_TAX'],
  ['/gl', 'FSSC_GL'],
]

/**
 * @returns '' 可达；'edition' 当前版本未包含该功能，应回工作台
 */
export function editionDenyReason(path) {
  if (!path) return ''
  const features = store.user?.features || []
  if (!Array.isArray(features)) return 'edition'
  const exact = EDITION_FEATURE_BY_PATH[path]
  if (exact) return features.includes(exact) ? '' : 'edition'
  for (const [prefix, feature] of EDITION_PREFIX_FEATURES) {
    if (path === prefix || path.startsWith(prefix + '/')) {
      return features.includes(feature) ? '' : 'edition'
    }
  }
  return ''
}
