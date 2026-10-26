-- =====================================================================================
-- V1.4.50  R5-D-034 权限码播种：SoD「执行检查」动作码 system:sod:scan —— 本文件【已执行】
-- 执行时间：2026-10-07（收官后波次·主窗，用户已授权改库）
-- 执行人：测试执行代判（代判≠代签，签认见 16/ V5 收官裁决记录 §11）
--
-- 【要解决的问题】
--   SoDCheckController.java:55 的 POST /api/tenant/compliance/sod/check/run 注解为
--   @RequiresPerm({"system:sod:scan","system:sod:list"})。system:sod:scan 此前只出现在代码注解里
--   （/api/tenant/system/sod/scan-perm 亦用该码），从未播种进 sys_menu ⇒ 该码在库内不可授予、
--   不可回收，"执行"这一动作实际完全靠 system:sod:list 兜住，权限面上是个假校验。
--
-- 【播种前实测（2026-10-07 只读，r834/r835/r836/r837/r838）】
--   1. sys_menu 中 FIND_IN_SET('system:sod:scan') = 0 行（r836 §A），system:sod:list 存活行仅
--      menu 3621（C 型工作区「运行监控与合规」，perms 逗号打包）；menu 3080（独立 SoD 菜单）
--      del_flag=1 已停用，故 3080 不能作为父关联点（前一版注释态 SQL 的父关联点写法在此作废）。
--   2. 持有 3621 的角色 7 个：CONFIG_ADMIN / CONTRACT_ADMIN / CONTRACT_MANAGER /
--      INTERNAL_AUDITOR(99011) / IT_ADMIN(99013) / LEGAL / WF_ADMIN（r835 §B）。
--   3. 父 3033「系统监控」下 F 型按钮码段位习惯＝9010/9020（3633 审计轨迹-查询、3634 合规规则-查询），
--      本码取 9030 同段（r836 §E）。
--   4. sys_menu 列面/默认值：terminal NOT NULL 默认 'PC'，sort/visible/status/del_flag 均 NOT NULL，
--      create_time/update_time 有 CURRENT_TIMESTAMP 默认（r834 §F、r836 §F）。
--
-- 【本次授予面＝3 个治理岗，不外扩】
--   INTERNAL_AUDITOR(99011) / COMPLIANCE_ADMIN(99031) / IT_ADMIN(99013)。
--   理由：SoD 巡检属内控治理动作；合同/法务岗虽能看到检查结果（持 list），但"执行检查"归内审与合规。
--   注解保留了 system:sod:list 的 OR 默认处理 ⇒ 播种与授权不会让任何现网角色从"能用"变"不能用"（零回归）。
--   若要真正办结成"只有 scan 码可执行"，须先删注解里的 OR 项，属业务裁决环节（人工清单 #权限与角色复核）。
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置只读校验：确认未播种（期望 0），且父关联点 3033 存活
SELECT COUNT(*) AS pre_existing_scan_rows FROM fssc.sys_menu WHERE perms = 'system:sod:scan' AND del_flag = 0;
SELECT id, menu_name, menu_type, del_flag FROM fssc.sys_menu WHERE id IN (3033, 3621);

-- §1 播种 F 型按钮码（去重：已存在则不重复插）
INSERT INTO fssc.sys_menu
    (parent_id, menu_name, menu_type, path, component, perms, icon, feature_code, terminal, sort, visible, status, del_flag)
SELECT 3033, '职责分离-执行检查', 'F', NULL, NULL, 'system:sod:scan', NULL, NULL, 'PC', 9030, 1, 1, 0
  FROM (SELECT 1) d
 WHERE NOT EXISTS (SELECT 1 FROM fssc.sys_menu x WHERE x.perms = 'system:sod:scan' AND x.del_flag = 0);

-- §2 授权 3 个治理岗（按 role_code + perms 精确配对，去重）
INSERT INTO fssc.sys_role_menu (tenant_id, role_id, menu_id, create_by)
SELECT r.tenant_id, r.id, m.id, NULL
  FROM fssc.sys_role r
  JOIN fssc.sys_menu m ON m.perms = 'system:sod:scan' AND m.del_flag = 0
 WHERE r.role_code IN ('INTERNAL_AUDITOR', 'COMPLIANCE_ADMIN', 'IT_ADMIN')
   AND r.del_flag = 0
   AND NOT EXISTS (SELECT 1 FROM fssc.sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- §3 复点：新行与各角色授权数（期望 1 行菜单 + 3 行授权）
SELECT id, parent_id, menu_name, menu_type, perms, terminal, sort, visible, status, del_flag
  FROM fssc.sys_menu WHERE perms = 'system:sod:scan';
SELECT r.role_code, COUNT(*) AS granted FROM fssc.sys_role_menu rm
  JOIN fssc.sys_role r ON r.id = rm.role_id
  JOIN fssc.sys_menu m ON m.id = rm.menu_id AND m.perms = 'system:sod:scan'
 GROUP BY r.role_code ORDER BY r.role_code;
