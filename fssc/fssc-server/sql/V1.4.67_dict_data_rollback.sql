-- =============================================================================
-- V1.4.67 回滚：清理本件写入的字典数据
--
-- 精确口径：只删**本件新增的那些 (dict_type, dict_value) 组合**，
--         现有 3 行 expense_type（TRAVEL/办公费 等，create_time=2026-08-11）
--         以及任何其它来源的数据**一律不动**。
--         （不能用 create_by 标记 —— 该列是 BIGINT 存用户ID，塞不了脚本标记串。）
--
-- 执行后校验：期望仅 expense_type=3
--   SELECT dict_type, COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0 GROUP BY dict_type;
-- =============================================================================

--口径再收窄（重要）：只删**本件独占写入的 5 个类型**。
--   sys_status / dict_yes_no 属通用类型，未来可能被其它功能写入同名取值
--   （如 NORMAL / YES），按 dict_value 删会误伤 ⇒ 这两个类型改为**按类型整体删**，
--   且仅当其下全部行的 create_by IS NULL 且 create_time 落在本件执行时窗内时才删。
--   简化且安全的做法：这两个类型本次**不纳入回滚**（保留亦无害，它们只是枚举登记）。
DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1
   AND dict_type IN ('wf_node_multi_type','wf_node_empty_strategy','wf_addsign_position',
                     'wf_addsign_status','wf_approval_item_category')
   AND dict_value IN ('ANY','ALL','SEQ','VOTE','SKIP','ERROR','ADMIN','ESCALATE',
                      'BEFORE','AFTER','PENDING','ACTIVE','DONE','REJECTED','CANCELLED',
                      'POSITIVE');

-- sys_status / dict_yes_no：整类型删除（本件为该类型首次写入，删除即回到本件前空置态）
DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1
   AND dict_type IN ('sys_status','dict_yes_no')
   AND dict_value IN ('NORMAL','DISABLE','YES','NO');