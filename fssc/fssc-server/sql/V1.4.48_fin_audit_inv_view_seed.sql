-- =====================================================================================
-- V1.4.48  D-DEC-078 授权轨：给 FIN_AUDIT（财务审核岗）授发票只读查看码 —— 全部为【注释态】，本文件未执行
-- 生成时间：2026-10-01（执行前须重跑 §0 只读校验，数据快照会随演练推进而失效）
-- 执行人：________（需业务/DBA 书面授权；本修复会话只读，未执行任何 INSERT/UPDATE）
--
-- 【要解决的缺陷（D-DEC-078，出处 12-21:71《流程记录_12-21_手工三单匹配_INVOICE_MATCH_MANUAL_全链》§四）】
--   Task_2「去处理」跳发票池 #/invoice/query?billNo=…&billId=26，而本节点办理人
--   FIN_AUDIT（孙燕飞 3873）不具备 inv:list / inv:verify:list ⇒ 整页「暂无数据／共 0 条」
--   ＋ 4 条「无该功能/按钮权限」提示——"发票合规审核岗看不到发票"，只能关提示回列表行内办理。
--
-- 【本会话已取证的现状（2026-10-01 MySQL 只读 SELECT 复核，perms 是逗号串、按 LIKE/FIND_IN_SET 重查）】
--   1. sys_role：FIN_AUDIT = role_id 15（tenant 1，财务审核岗，NORMAL）；持有人 3873 孙燕飞 / 3929 吕建生。
--   2. sys_menu 发票域（del_flag=0 现行值）：
--      - 215  M   发票管理（父节点，invoice:list）
--      - 266  C   发票管理 /invoice/query（perms=inv:list ← 发票池列表唯一门控码）
--      - 3212 F   发票明细查询（inv:line:list，池内行展开用，可选）
--      已写/已停用的 inv 码一律不授：267/3407（inv:create）、3209（inv:bind）、
--      3210/3211/3213（inv:line:create/delete/update）、3214（inv:verify:run）——
--      本脚本给审核岗的是「看得见」，不是「改得动」。
--   3. sys_role_menu：role_id=15 现持 66 条菜单，其中 menu 215 = 0 行、menu 266 = 0 行、
--      任何 perms LIKE '%inv:%' 的菜单 = 0 行（重查方式：JOIN sys_menu 按 perms 逗号串过滤）。
--      对照：FSSC_AUDITOR(60) 持 215/266/268/3212；FIN_AP/FIN_AR/FIN_GL/FSSC_SUPERVISOR/TAX_* 持 266。
--   4. 后端校验（@RequiresPerm）：InvoiceController 的 /page、/bill-invoices、/filing-summary、
--      /anomaly、/used-count/audit 均挂 inv:list；InvoiceEnhanceController /line/list 挂
--      inv:line:list、/verify/page 挂 inv:verify:list。
--   5. 前端校验：BillDetailPanel 审批详情内嵌「关联发票」区 canViewInvoice=hasPerm('inv:list')；
--      Approval.vue / Workbench.vue / FlowCenter.vue「去处理」已加 D-DEC-078 落点校验
--      （无 inv:list 时不再跳发票池、改落全链路追溯）。本 SQL 执行并重登后该校验自动放行。
--
-- 【跳校验链路与去重】
--   授权只补数据缺口，不改代码。INSERT ... SELECT ... WHERE NOT EXISTS 去重可重复执行；
--   必须 215+266 成对授（缺父节点 215 则菜单树渲染不出，参照 audit-role-log-grant-20260920.sql §2 教训）。
--   回滚：V1.4.48_fin_audit_inv_view_rollback.sql（按 role_code+menu_id 精确配对删除，同为注释态）。
--    生效条件：权限点烤进 JWT（AuthService→TokenService 'per' claim），授权后须重新登录；
--     菜单树由 MenuService.myTree 实时读库（刷新即可见）。
--    已知残留缺口（无法用本脚本闭合，登记备查）：inv:verify:list 的载体菜单 268 已逻辑删除
--     （del_flag=1），发票池「验真记录」页签对全员不可达；如需开放须先裁决恢复该菜单，勿在此偷渡。
-- =====================================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- §0 前置校验（只读，执行 §1 前必须先看结果）
-- ----------------------------------------------------------------------------

-- 0.1 FIN_AUDIT 角色定位（预期：仅一行，tenant_id=1 / role_id=15 / NORMAL）
SELECT id AS role_id, tenant_id, role_code, role_name, status FROM sys_role
 WHERE role_code = 'FIN_AUDIT' AND del_flag = 0;

-- 0.2 目标菜单现行状态（预期：215/266 均 del_flag=0、visible=1、status=1）
SELECT id, parent_id, menu_name, menu_type, path, perms, del_flag FROM sys_menu WHERE id IN (215, 266);

-- 0.3 缺口基线（预期：0 行 ⇒ 本脚本确有缺口要补；若已有行则 §1 去重空转）
SELECT rm.role_id, rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id
 WHERE r.role_code = 'FIN_AUDIT' AND r.del_flag = 0 AND rm.menu_id IN (215, 266);

-- ----------------------------------------------------------------------------
-- §1 授权（注释态；解开注释并按需核对 §0 结果后在部署窗口执行）
--     按 role_code 关联取 role_id/tenant_id，不硬编码 15（多租户/环境漂移防线）。
-- ----------------------------------------------------------------------------

-- 1.1 父节点「发票管理」（215，M）→ FIN_AUDIT（缺父则侧栏菜单树渲染不到 266）
-- INSERT INTO sys_role_menu(tenant_id, role_id, menu_id, create_time)
-- SELECT r.tenant_id, r.id, 215, NOW() FROM sys_role r
--  WHERE r.role_code = 'FIN_AUDIT' AND r.del_flag = 0
--    AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 215);

-- 1.2 发票管理页（266，C /invoice/query，perms=inv:list）→ FIN_AUDIT
--     一个码同时解锁三处只读面：发票池列表（InvoiceController /page）、审批详情内嵌
--     「关联发票」区（/bill-invoices）、以及前端「去处理」落点校验（D-DEC-078 注释见 Approval.vue）。
-- INSERT INTO sys_role_menu(tenant_id, role_id, menu_id, create_time)
-- SELECT r.tenant_id, r.id, 266, NOW() FROM sys_role r
--  WHERE r.role_code = 'FIN_AUDIT' AND r.del_flag = 0
--    AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 266);

-- 1.3（可选，须业务签认后再解）发票明细查询（3212，F，inv:line:list）→ FIN_AUDIT
--     仅当审核岗要在池内展开发票行项目时才需要；不解不阻塞 §1.2 的三处只读面。
-- INSERT INTO sys_role_menu(tenant_id, role_id, menu_id, create_time)
-- SELECT r.tenant_id, r.id, 3212, NOW() FROM sys_role r
--  WHERE r.role_code = 'FIN_AUDIT' AND r.del_flag = 0
--    AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 3212);

-- ----------------------------------------------------------------------------
-- §2 验证（只读；执行 §1 后预期 FIN_AUDIT 持 215/266（若解 1.3 则 +3212），每角色行数=授出数）
-- ----------------------------------------------------------------------------
-- SELECT r.role_code, rm.menu_id, m.menu_name, m.perms FROM sys_role_menu rm
--   JOIN sys_role r ON r.id = rm.role_id JOIN sys_menu m ON m.id = rm.menu_id
--  WHERE r.role_code = 'FIN_AUDIT' AND rm.menu_id IN (215, 266, 3212)
--  ORDER BY rm.menu_id;

-- 复核规则（逗号串必须 FIND_IN_SET，勿等值匹配——历史假阴性前科）：
-- SELECT r.role_code FROM sys_role_menu rm
--   JOIN sys_role r ON r.id = rm.role_id JOIN sys_menu m ON m.id = rm.menu_id
--  WHERE FIND_IN_SET('inv:list', m.perms) GROUP BY r.role_code;
