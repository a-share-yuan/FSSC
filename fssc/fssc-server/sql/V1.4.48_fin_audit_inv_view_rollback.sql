-- =====================================================================================
-- V1.4.48 回滚：撤销「D-DEC-078 授权轨：FIN_AUDIT 发票只读查看码」—— 同为【注释态】，未执行
-- 与 V1.4.48_fin_audit_inv_view_seed.sql 成对；按 role_code+menu_id 精确配对删除
-- （沿用 audit-role-log-grant-20260920-rollback.sql 的配对规则，不按 create_time 扫删）。
--
-- 回滚前须知：
--   1. 删除后 FIN_AUDIT 退回到"看不到发票"的原状态（12-21:71 现象复现），但 Approval.vue /
--      Workbench.vue / FlowCenter.vue 的 D-DEC-078 落点校验仍在——「去处理」不会再被丢进
--      报错的发票池页，而是落全链路追溯、按钮显示「查看单据」。回滚不产生新的坏路径。
--   2. 权限点烤进 JWT，回滚后持有人（现网：3873 孙燕飞 / 3929 吕建生）须重新登录才失效。
--   3. §1.3（menu 3212，可选授）若当初未解，本文件对应 DELETE 为空转（WHERE 限定行不存在）。
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置（只读）：确认要删的行就是 seed 授出去的行
-- SELECT r.role_code, rm.menu_id, m.menu_name FROM sys_role_menu rm
--   JOIN sys_role r ON r.id = rm.role_id JOIN sys_menu m ON m.id = rm.menu_id
--  WHERE r.role_code = 'FIN_AUDIT' AND rm.menu_id IN (215, 266, 3212)
--  ORDER BY rm.menu_id;

-- §1 回滚（注释态）：只删 215/266/3212 三选一集合，不误伤 FIN_AUDIT 其余 66 条菜单
-- DELETE rm FROM sys_role_menu rm
--  JOIN sys_role r ON r.id = rm.role_id
--  WHERE r.role_code = 'FIN_AUDIT'
--    AND rm.menu_id IN (215, 266, 3212);

-- §2 验证（只读）：预期 0 行
-- SELECT rm.role_id, rm.menu_id FROM sys_role_menu rm
--   JOIN sys_role r ON r.id = rm.role_id
--  WHERE r.role_code = 'FIN_AUDIT' AND rm.menu_id IN (215, 266, 3212);
