-- SPDX-License-Identifier: AGPL-3.0-or-later
-- V1.6.92 回滚：移除装配档列与索引。
-- 说明：这两列是可重建的装配标记，不含业务数据；回滚后装配脚本会退回用 remark 之外的
-- 判据（当前脚本以 pf_tenant.op_mode 为完成标记，回滚后须同步停用该脚本）。
ALTER TABLE pf_tenant
  DROP INDEX idx_tenant_op_mode,
  DROP COLUMN onboard_time,
  DROP COLUMN op_mode;
