-- =============================================================================
-- V1.4.74  八类差距收尾⑤：费用标准菜单 + 三层种子数据（政策/标准明细/版本）
-- 日期：2026-10-17
-- 背景：FSSC未实现项归类复核_8类框架_20261014.md §4-⑤
--   expense_policy / expense_policy_standard / expense_policy_version 三表 0 行，
--   后端 CRUD 齐全，前端仅有 system/policy 复用通用表组件（只覆盖主表 3/18 字段、无子表）。
-- 本件做两件事：
--   1) 新增「费用标准」菜单，挂「业务规则」(3022) 下，走新建的专用页
--      （前端路由 compliance/expense-policy → views/compliance/ExpensePolicyManage.vue）
--   2) 灌三条政策种子，expense_type 严格取字典 expense_type 的三个合法值
--      （TRAVEL 差旅费 / OFFICE 办公费 / ENTERTAIN 招待费）——该列是
--      ExpensePolicyService.listEffective(expenseType) 的真实查询键，不可臆造。
-- 回滚：见同名 _rollback.sql
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 菜单（C 类型）
--    component 列为元数据：前端路由是静态注册（router/routes/system.js），
--    既有菜单的 component 值与真实文件也并非一一对应，故按同段命名惯例填写即可。
--    perms 复用既有 compliance:policy:list（菜单页鉴权用）。
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, terminal, sort, visible, status, del_flag)
VALUES (3778, 3022, '费用标准', 'C', '/compliance/expense-policy', 'compliance/expense-policy/index',
        'compliance:policy:list', 'Money', 'PC', 1, 1, 1, 0);

-- 授权给既有 compliance:policy:list 的持有角色（执行时 role 99013 IT_ADMIN），保持授权面一致
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_time)
SELECT 1, rm.role_id, 3778, NOW()
FROM sys_role_menu rm
WHERE rm.menu_id = 3137
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu d WHERE d.role_id = rm.role_id AND d.menu_id = 3778);

-- ---------------------------------------------------------------------------
-- 2. 政策种子（expense_policy，tenant_id=1）
--    id 为 auto_increment，标准明细靠子查询按 policy_code 关联，避免硬编码 id。
--    NOT NULL 必填：policy_code / policy_name / expense_type / effective_start
-- ---------------------------------------------------------------------------
INSERT INTO expense_policy
  (tenant_id, policy_code, policy_name, expense_type, expense_item_name,
   max_amount, need_invoice, submit_days, approve_above_amount, priority,
   effective_start, effective_end, status, remark,
   create_by, create_time, del_flag, created_by, created_at)
VALUES
  (1, 'TRV_CN', '国内差旅费用标准', 'TRAVEL', '差旅费',
   20000.00, 1, 30, 5000.00, 10,
   '2026-01-01', '2026-12-31', 1, '含住宿/交通/伙食，超标仅警告不拦截',
   1, NOW(), 0, 1, NOW()),
  (1, 'TRV_INTL', '国际差旅费用标准', 'TRAVEL', '差旅费',
   50000.00, 1, 45, 20000.00, 20,
   '2026-01-01', '2026-12-31', 1, '需事前审批，超额需总监以上附加审批',
   1, NOW(), 0, 1, NOW()),
  (1, 'OFFICE_STD', '办公用品费用标准', 'OFFICE', '办公费',
   5000.00, 1, 60, 2000.00, 30,
   '2026-01-01', '2026-12-31', 1, '办公用品与耗材',
   1, NOW(), 0, 1, NOW()),
  (1, 'ENT_STD', '业务招待费用标准', 'ENTERTAIN', '招待费',
   10000.00, 1, 30, 3000.00, 40,
   '2026-01-01', '2026-12-31', 1, '招待费须注明被招待客户与事由',
   1, NOW(), 0, 1, NOW());

-- ---------------------------------------------------------------------------
-- 3. 标准明细种子（expense_policy_standard）
--    standard_type / level_type / overtime_action 取值严格对齐实体注释：
--      standard_type : AMOUNT 金额标准 / QUOTA 限额 / RATE 比例
--      level_type    : EMPLOYEE_LEVEL 员工级别 / POSITION 职位 / DEPARTMENT 部门
--      overtime_action: WARN 警告 / BLOCK 阻止 / EXTRAAUTH 附加审批
--    NOT NULL 必填：policy_id / standard_type / standard_key / standard_unit
-- ---------------------------------------------------------------------------

-- 3.1 国内差旅：住宿按员工级别分档（超标警告）
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'AMOUNT', 'HOTEL_RATE', lv.rate, NULL, '元/晚',
       'EMPLOYEE_LEVEL', lv.lv, 'WARN', NULL, '住宿费超标仅提示', NOW(), NOW()
FROM expense_policy p
JOIN (SELECT 'P1' lv, 800.00 rate UNION ALL SELECT 'P2', 600.00 UNION ALL SELECT 'P3', 450.00) lv
WHERE p.policy_code = 'TRV_CN' AND p.tenant_id = 1;

-- 3.2 国内差旅：伙食补助（全级别统一）
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'AMOUNT', 'MEAL_ALLOWANCE', 120.00, NULL, '元/人天',
       NULL, NULL, 'WARN', NULL, '按出差自然日计', NOW(), NOW()
FROM expense_policy p WHERE p.policy_code = 'TRV_CN' AND p.tenant_id = 1;

-- 3.3 国内差旅：市内交通限额（超额附加审批）
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'QUOTA', 'LOCAL_TRANSPORT', 50.00, 200.00, '元/人天',
       NULL, NULL, 'EXTRAAUTH', 'DEPT_MANAGER', '超 200 元/人天需部门经理附加审批', NOW(), NOW()
FROM expense_policy p WHERE p.policy_code = 'TRV_CN' AND p.tenant_id = 1;

-- 3.4 国内差旅：交通工具按职位限定
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'QUOTA', 'TRANSPORT_CLASS', NULL, NULL, '类',
       'POSITION', pv.pos, 'BLOCK', NULL, '限定可选交通工具档次', NOW(), NOW()
FROM expense_policy p
JOIN (SELECT 'P1' pos UNION ALL SELECT 'P2' UNION ALL SELECT 'P3') pv
WHERE p.policy_code = 'TRV_CN' AND p.tenant_id = 1;

-- 3.5 国际差旅：住宿与餐费（按级别，较高档次）
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'AMOUNT', k.sk, k.sv, NULL, k.unit,
       'EMPLOYEE_LEVEL', lv.lv, 'EXTRAAUTH', 'CFO', k.rm, NOW(), NOW()
FROM expense_policy p
JOIN (SELECT 'P1' lv UNION ALL SELECT 'P2') lv
JOIN (SELECT 'HOTEL_INTL' sk, 1500.00 sv, '元/晚' unit, '国际住宿超标需 CFO 附加审批' rm
      UNION ALL SELECT 'MEAL_INTL', 400.00, '元/人天', '国际餐费超标需 CFO 附加审批') k
WHERE p.policy_code = 'TRV_INTL' AND p.tenant_id = 1;

-- 3.6 办公用品：单笔限额
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'QUOTA', 'OFFICE_ITEM_LIMIT', 2000.00, 5000.00, '元/单',
       NULL, NULL, 'EXTRAAUTH', 'DEPT_MANAGER', '单笔超 2000 元需部门经理审批', NOW(), NOW()
FROM expense_policy p WHERE p.policy_code = 'OFFICE_STD' AND p.tenant_id = 1;

-- 3.7 招待费：按被招待人数定额
INSERT INTO expense_policy_standard
  (tenant_id, policy_id, standard_type, standard_key, standard_value_min, standard_value_max, standard_unit,
   level_type, level_value, overtime_action, overtime_auth_level, remark, created_at, updated_at)
SELECT 1, p.id, 'AMOUNT', 'ENTERTAIN_PER_HEAD', 200.00, NULL, '元/人',
       NULL, NULL, 'EXTRAAUTH', 'CENTER_APPROVER', '人均超标需总监级附加审批', NOW(), NOW()
FROM expense_policy p WHERE p.policy_code = 'ENT_STD' AND p.tenant_id = 1;

-- ---------------------------------------------------------------------------
-- 4. 版本快照种子（expense_policy_version）
--    NOT NULL 必填：policy_id / version / snapshot_data / change_log
--    仅对国内差旅建 v1.0.0 已生效版本，其余政策待运营在页面上手工存快照。
-- ---------------------------------------------------------------------------
INSERT INTO expense_policy_version (tenant_id, policy_id, version, snapshot_data, change_log, status, created_at)
SELECT 1, p.id, 'v1.0.0',
       JSON_OBJECT('policyCode', p.policy_code, 'policyName', p.policy_name,
                   'maxAmount', p.max_amount, 'effectiveStart', p.effective_start),
       '2026 年度标准首次发布', 'ACTIVE', NOW()
FROM expense_policy p WHERE p.policy_code = 'TRV_CN' AND p.tenant_id = 1;
