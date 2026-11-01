// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
// Element Plus 组件文案跟随应用语言（域8A）
// Element Plus 自带 locale 包（日期/分页/确认框等组件内文案）。若只切 vue-i18n
// 而不同步 EP locale，会出现「表格分页还是中文」这类半切换。
// 故由 <LocaleProvider> 统一注入 <el-config-provider :locale>。

import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const EP_LOCALES = {
  'zh-CN': zhCn,
  'en-US': en,
}

/** 应用 locale → Element Plus locale 包；新增语种在此登记一行即可。 */
export function elementLocaleOf(appLocale) {
  return EP_LOCALES[appLocale] || EP_LOCALES['zh-CN']
}

export default EP_LOCALES
