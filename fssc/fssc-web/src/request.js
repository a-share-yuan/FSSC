// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 双 http 合并：本文件原为并行开发时复制的第二套同款 axios 实例（baseURL/超时/Token 头/统一响应解包
// 与 api.js 完全一致），现将 api.js 的唯一实例再导出，五个页面（ReceivableBoard/Invoice/
// ArchiveBoard/CorpPay/AssetBoard）的 import 路径保持不变、零改动。
export { http } from './api'
