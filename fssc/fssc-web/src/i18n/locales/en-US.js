// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
//
// English (US) locale pack — must stay key-aligned with zh-CN.js (CI gate checks).
// ---------------------------------------------------------------------------

export default {
  // ---- Common ---------------------------------------------------------
  common: {
    confirm: 'OK',
    cancel: 'Cancel',
    save: 'Save',
    submit: 'Submit',
    reset: 'Reset',
    search: 'Search',
    refresh: 'Refresh',
    close: 'Close',
    back: 'Back',
    next: 'Next',
    prev: 'Previous',
    loading: 'Loading...',
    empty: 'No data',
    success: 'Operation succeeded',
    failed: 'Operation failed',
    yes: 'Yes',
    no: 'No',
    all: 'All',
    total: '{total} in total',
    pleaseSelect: 'Please select',
    pleaseInput: 'Please input',
    operation: 'Action',
    status: 'Status',
    remark: 'Remark',
    createTime: 'Created at',
    updateTime: 'Updated at',
    creator: 'Created by',
  },

  // ---- Locale switcher -------------------------------------------------
  locale: {
    switch: 'Switch language',
    current: 'Current language: {lang}',
  },

  // ---- Login ----------------------------------------------------------
  login: {
    title: 'Financial Shared Service Center',
    subtitle: 'Financial Shared Service Center',
    username: 'Username',
    password: 'Password',
    tenant: 'Tenant',
    submit: 'Sign in',
    remember: 'Remember username',
    usernameRequired: 'Please enter username',
    passwordRequired: 'Please enter password',
    tenantRequired: 'Please select tenant',
    loginFailed: 'Sign-in failed',
    backendDown: 'Backend service unreachable: cannot access {target}. Please start fssc-server first.',
    retry: 'Retry',
    stillDown: 'Still unreachable. Please check whether fssc-server is running.',
    recovered: 'Backend reconnected, reloading...',
  },

  // ---- Layout / navigation --------------------------------------------
  layout: {
    profile: 'Profile',
    logout: 'Sign out',
    logoutConfirm: 'Sign out?',
    home: 'Home',
    notice: 'Notifications',
    noPermission: 'Access denied',
    notFound: 'Page not found',
    backHome: 'Back to home',
  },

  // ---- Status ---------------------------------------------------------
  status: {
    draft: 'Draft',
    submitted: 'Submitted',
    approving: 'Approving',
    approved: 'Approved',
    rejected: 'Rejected',
    paid: 'Paid',
    payable: 'Payable',
    failed: 'Failed',
    void: 'Voided',
    closed: 'Closed',
    pending: 'Pending',
  },

  // ---- Error codes (front-end fallback; backend returns localized msgs) -
  err: {
    4030: 'Permission denied',
    401: 'Session expired, please sign in again',
    403: 'Access denied',
    404: 'Resource not found',
    500: 'Internal server error',
    networkError: 'Network error, please check your connection and retry',
    circuitBreak: 'Blocked by circuit breaker or rate limiting, please retry later',
  },
}
