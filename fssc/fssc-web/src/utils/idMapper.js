// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * ID到名称的全局映射工具（解决表格列/表单直接显示数字ID的问题）
 *
 * 设计原则：
 * 1. 高频主数据（用户、部门、公司）→ 前端全局缓存映射
 * 2. 业务数据（单据、任务）→ 由后端补充关联字段，本工具提供降级方案
 * 3. 映射失败时回落显示原始ID，避免空白
 */

import { userApi, orgApi } from '@/api'
import { fetchAllPages } from './paging'

const caches = {
  user: new Map(),
  dept: new Map(),
  company: new Map(),
}

let preloaded = false

/**
 * 预加载所有主数据映射（在应用启动或登录成功后调用一次）
 */
export async function preloadMappers() {
  if (preloaded) return

  try {
    const [users, depts, companies] = await Promise.all([
      // 用户量超过单页上限时，未拉到的用户会回落显示「用户#123」——分页拉全，别写死上限。
      fetchAllPages((p) => userApi.list(p), { pageSize: 500 }).catch(() => []),
      orgApi.deptTree().catch(() => []),
      orgApi.companyList?.().catch(() => []).then(r => r || []),
    ])

    // 用户映射：id → nickName
    (users || []).forEach(u => {
      caches.user.set(u.id, u.nickName || u.userName || `用户#${u.id}`)
    })

    // 部门映射：扁平化树形结构
    function flattenTree(nodes) {
      const result = []
      function walk(list) {
        list?.forEach(node => {
          result.push(node)
          if (node.children?.length) walk(node.children)
        })
      }
      walk(nodes)
      return result
    }
    flattenTree(depts).forEach(d => {
      caches.dept.set(d.id, d.name || `部门#${d.id}`)
    })

    // 公司映射
    (companies || []).forEach(c => {
      caches.company.set(c.id, c.name || c.companyName || `公司#${c.id}`)
    })

    preloaded = true
    console.log('[idMapper] 主数据映射预加载完成', {
      users: caches.user.size,
      depts: caches.dept.size,
      companies: caches.company.size,
    })
  } catch (e) {
    console.warn('[idMapper] 主数据映射预加载失败', e)
  }
}

/**
 * 根据用户ID获取用户名
 * @param {number|string} id - 用户ID
 * @returns {string} 用户名或"用户#ID"
 */
export function getUserName(id) {
  if (!id) return '—'
  return caches.user.get(Number(id)) || `用户#${id}`
}

/**
 * 根据部门ID获取部门名
 */
export function getDeptName(id) {
  if (!id) return '—'
  return caches.dept.get(Number(id)) || `部门#${id}`
}

/**
 * 根据公司ID获取公司名
 */
export function getCompanyName(id) {
  if (!id) return '—'
  return caches.company.get(Number(id)) || `公司#${id}`
}

/**
 * 批量转换工具：将包含ID字段的对象数组转换为包含名称的数组
 * @param {Array} list - 原始数据列表
 * @param {Object} mappings - 映射配置 { employeeId: 'employeeName', deptId: 'deptName', companyId: 'companyName' }
 * @returns {Array} 转换后的列表
 */
export function mapIdsToNames(list, mappings = {}) {
  const mappers = {
    employeeId: getUserName,
    userId: getUserName,
    assigneeId: getUserName,
    reviewedBy: getUserName,
    createBy: getUserName,
    deptId: getDeptName,
    targetDeptId: getDeptName,
    companyId: getCompanyName,
    targetCompanyId: getCompanyName,
  }

  return (list || []).map(item => {
    const newItem = { ...item }
    Object.entries(mappings).forEach(([idField, nameField]) => {
      if (item[idField] != null && mappers[idField]) {
        newItem[nameField] = mappers[idField](item[idField])
      }
    })
    return newItem
  })
}
