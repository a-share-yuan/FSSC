// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
//
// 简体中文语言包（基准语言 / fallback）
// ---------------------------------------------------------------------------
// 键命名：<层级>.<模块>.<键>，全小写下划线或驼峰，同层保持一致。
// 本文件是「键的权威来源」：en-US.js 必须与本文件键集完全对齐（CI 校验）。
// 域8B 存量抽取时按「菜单 > 按钮/标签 > 提示/校验 > 长文案」四级追加。

export default {
  // ---- 通用 ------------------------------------------------------------
  common: {
    confirm: '确定',
    cancel: '取消',
    save: '保存',
    submit: '提交',
    reset: '重置',
    search: '查询',
    refresh: '刷新',
    close: '关闭',
    back: '返回',
    next: '下一步',
    prev: '上一步',
    loading: '加载中...',
    empty: '暂无数据',
    success: '操作成功',
    failed: '操作失败',
    yes: '是',
    no: '否',
    all: '全部',
    total: '共 {total} 条',
    pleaseSelect: '请选择',
    pleaseInput: '请输入',
    operation: '操作',
    status: '状态',
    remark: '备注',
    createTime: '创建时间',
    updateTime: '更新时间',
    creator: '创建人',
  },

  // ---- 语言切换器 ------------------------------------------------------
  locale: {
    switch: '切换语言',
    current: '当前语言：{lang}',
  },

  // ---- 登录 ------------------------------------------------------------
  login: {
    title: '财务共享平台',
    subtitle: 'Financial Shared Service Center',
    username: '用户名',
    password: '密码',
    tenant: '租户',
    submit: '登录',
    remember: '记住用户名',
    usernameRequired: '请输入用户名',
    passwordRequired: '请输入密码',
    tenantRequired: '请选择租户',
    loginFailed: '登录失败',
    backendDown: '后端服务未连接：无法访问 {target}，请先启动 fssc-server',
    retry: '重试',
    stillDown: '仍未连接上后端，请检查 fssc-server 是否已启动',
    recovered: '后端已恢复连接，正在刷新页面...',
  },

  // ---- 布局 / 导航 -----------------------------------------------------
  layout: {
    profile: '个人中心',
    logout: '退出登录',
    logoutConfirm: '确认退出登录？',
    home: '首页',
    notice: '消息通知',
    noPermission: '无访问权限',
    notFound: '页面不存在',
    backHome: '返回首页',
  },

  // ---- 通用状态 -------------------------------------------------------
  status: {
    draft: '草稿',
    submitted: '已提交',
    approving: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    paid: '已付款',
    payable: '待付款',
    failed: '已失败',
    void: '已作废',
    closed: '已关闭',
    pending: '待处理',
  },

  // ---- 错误码（后端 MessageSource 映射：前端 $t('err.' + code)）---------
  // 注意：错误文案由后端按 Accept-Language 返回多语；此处仅为前端本地兜底。
  err: {
    4030: '缺少权限，无法执行该操作',
    401: '登录已过期，请重新登录',
    403: '无访问权限',
    404: '请求的资源不存在',
    500: '服务器内部错误',
    networkError: '网络连接失败，请检查网络后重试',
    circuitBreak: '连接治理拦截：熔断或限流中，请稍后重试',
  },
}
