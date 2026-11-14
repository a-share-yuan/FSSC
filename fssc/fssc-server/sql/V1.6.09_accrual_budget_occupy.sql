-- =====================================================================
-- V1.6.09 预提/待摊纳入预算「申请占用」（预算影响类型三值之一）
--
-- 背景：整改前 ACCRUAL/DEFER 记账单与预算零关联，而合同反而占预算——与在册手册相反。
--       预算影响类型按三类划分：
--         ① 申请占用（对预算数扣减，参与控制检查，费用尚未实际执行）
--         ② 预算执行（费用实际发生）
--         ③ 预算冲回（冲回/负数，只影响可用预算数）
--       实际执行 = 申请占用 − 申请冲回 + 预算执行 − 执行冲回；
--       可用预算 = 调整后预算数 − 实际执行。预提落在 ①，冲销预提落在 ③。
--
-- 依赖：V1.6.00（fssc_add_column 助手过程）
-- 可重复执行：是
-- 影响表：gl_journal_entry（只加列 + 一次历史行定性回填，不改金额、不改状态）
-- =====================================================================

-- ① 预算维度入参：费用项目 + 承担部门（预提单上由制单人选择，用于定位 bg_item 行）
CALL fssc_add_column('gl_journal_entry', 'expense_item_id',
    '`expense_item_id` bigint DEFAULT NULL COMMENT ''费用项目 md_expense_item.id（预算维度来源）''');
CALL fssc_add_column('gl_journal_entry', 'dept_id',
    '`dept_id` bigint DEFAULT NULL COMMENT ''承担部门 sys_dept.id（预算维度来源）''');

-- ② 占用结果：解析到的预算明细 + 占用状态（NONE/OCCUPIED/RELEASED）
--    budget_item_id 落库而非每次重算——主数据调整后仍能找回"当初占的是哪条预算"，释放才不会跑偏。
CALL fssc_add_column('gl_journal_entry', 'budget_item_id',
    '`budget_item_id` bigint DEFAULT NULL COMMENT ''实际占用到的预算明细 bg_item.id''');
CALL fssc_add_column('gl_journal_entry', 'budget_occupy_status',
    '`budget_occupy_status` varchar(16) DEFAULT NULL COMMENT ''预算占用状态：OCCUPIED/RELEASED/NONE/null''');

-- ③ 历史行定性：升级前的 ACCRUAL/DEFER 从未占用过预算，标 NONE 而不是留 NULL。
--    留 NULL 会与「MANUAL 等本就不涉预算的类型」混为一谈，报表上分不清"没占"和"不涉及"。
UPDATE gl_journal_entry
   SET budget_occupy_status = 'NONE'
 WHERE entry_type IN ('ACCRUAL', 'DEFER')
   AND budget_occupy_status IS NULL;

SELECT 'V1.6.09 完成：gl_journal_entry 已具备预提/待摊预算占用字段' AS result;
