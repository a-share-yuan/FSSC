// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 自定义调色板：覆盖默认「创建任务」(bpmn:Task)，改为直接创建「用户任务」(bpmn:UserTask 审批节点)。
 * 财务审批流全部是人工审批节点，统一类型避免出现「任务」节点（无人像、无审批人配置）。
 */
export default {
  __init__: ['fsscPaletteProvider'],
  fsscPaletteProvider: ['type', FsscPaletteProvider],
}

function FsscPaletteProvider(palette, create, elementFactory) {
  this._create = create
  this._elementFactory = elementFactory
  palette.registerProvider(this)
}

FsscPaletteProvider.$inject = ['palette', 'create', 'elementFactory']

FsscPaletteProvider.prototype.getPaletteEntries = function () {
  const create = this._create
  const elementFactory = this._elementFactory

  /** 拖拽/点击创建用户任务（审批节点） */
  function createUserTask(event) {
    const shape = elementFactory.createShape({ type: 'bpmn:UserTask' })
    create.start(event, shape)
  }

  /** 拖拽/点击创建子流程调用节点（调用活动，UC12） */
  function createCallActivity(event) {
    const shape = elementFactory.createShape({ type: 'bpmn:CallActivity' })
    create.start(event, shape)
  }

  return {
    // 覆盖默认 create.task（bpmn:Task）→ 创建 bpmn:UserTask
    'create.task': {
      group: 'activity',
      className: 'bpmn-icon-user-task',
      title: '创建审批节点（用户任务）',
      action: {
        click: createUserTask,
        dragstart: createUserTask,
      },
    },
    // 「系统自动节点(ServiceTask)」入口已摘除：flowable:systemAction 属性无后端消费方，
    // 且无 delegateExpression 的 ServiceTask 一到达就抛引擎异常——画出来只会画出一个死节点。
    // 新增：创建子流程调用节点（bpmn:CallActivity，UC12）
    'create.call-activity': {
      group: 'activity',
      className: 'bpmn-icon-call-activity',
      title: '创建子流程调用（引用公共子流程）',
      action: {
        click: createCallActivity,
        dragstart: createCallActivity,
      },
    },
  }
}
