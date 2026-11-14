-- =====================================================================
-- 回滚 V1.6.09（预提/待摊预算占用字段）
--
-- 只在「已确认这些列不再被新版代码读取」时执行：先回滚代码再回滚本脚本。
-- 列内数据（占用了哪条预算、是否已冲回）随列一并删除；bg_occupy 的占用流水不受影响，
--    因此预算额度不会错乱——删列只丢"预提单 ↔ 预算行"的关联，不丢额度本身。
-- =====================================================================
CALL fssc_drop_column('gl_journal_entry', 'budget_occupy_status');
CALL fssc_drop_column('gl_journal_entry', 'budget_item_id');
CALL fssc_drop_column('gl_journal_entry', 'dept_id');
CALL fssc_drop_column('gl_journal_entry', 'expense_item_id');

SELECT 'V1.6.09 rollback done' AS result;
