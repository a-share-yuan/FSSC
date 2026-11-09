-- SPDX-License-Identifier: AGPL-3.0-or-later
-- 已有系统升级专用：把升级前就在运行的租户标记为共享版（全量能力）。
-- 新部署不要执行本脚本 —— 保持 op_mode 为 NULL，管理员首次登录时自行选择使用方式。
-- 幂等：只回填 op_mode 为空的行，不会覆盖已经选过档的企业。
UPDATE pf_tenant
   SET op_mode = 'SHARED', onboard_time = COALESCE(create_time, NOW())
 WHERE op_mode IS NULL AND del_flag = 0;
