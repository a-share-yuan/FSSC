-- V1.4.12 审批空策略治理：全流程节点禁入 admin（用户铁律 2026-09-19）
-- 背景：wf_node_config 582 个节点 empty_strategy 显式配置为 ADMIN（找不到审批人时转租户管理员默认处理），
--       与「所有流程节点不允许 admin 参与、必须由真实岗位办理」铁律冲突。
-- 处置：统一翻为 ERROR——节点解析不出可办理人时在审批入口抛业务异常阻断，
--       引导到【角色授权】补齐真实岗位持有人，而非静默落入 admin 待办。
-- 关联代码修复：NodeOrgResolver.companyTable() 补 PAY_PLAN_BASE→pay_plan 白名单；
--       applyEmptyStrategy() 缺省值 ADMIN→ERROR（未显式配置的节点同样不再默认处理 admin）。
-- 本迁移为系统配置变更，经用户批准（选项「白名单+默认ERROR」），不触碰业务数据。

UPDATE wf_node_config SET empty_strategy = 'ERROR' WHERE empty_strategy = 'ADMIN';

-- 复核：应 0 行
SELECT COUNT(*) AS remaining_admin_fallback FROM wf_node_config WHERE empty_strategy = 'ADMIN';
