-- SPDX-License-Identifier: AGPL-3.0-or-later
-- 使用方式标记：pf_tenant 记录企业选择了哪一档能力集合，以及何时完成选择。
-- op_mode 为 NULL 表示尚未选择；前端首登引导据此决定是否跳转 /onboard。
-- 新部署保持 NULL，由企业管理员首次登录时自行选择；已上线系统升级时另执行
-- V1.6.92_tenant_op_mode_backfill.sql 把既有租户标记为 SHARED，避免被拦在引导页。
-- 列允许 NULL 且带默认值，历史 INSERT (列清单式) 不受影响。
ALTER TABLE pf_tenant
  ADD COLUMN op_mode VARCHAR(32) NULL DEFAULT NULL
      COMMENT '使用方式 EXPENSE/CONTROL/SHARED；NULL=尚未选择' AFTER admin_user_id,
  ADD COLUMN onboard_time DATETIME NULL DEFAULT NULL
      COMMENT '选择完成时间；NULL=未选择' AFTER op_mode,
  ADD INDEX idx_tenant_op_mode (op_mode);
