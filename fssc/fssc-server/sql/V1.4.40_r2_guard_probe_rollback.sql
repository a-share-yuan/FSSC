-- 批次3 R2 · 上一条取证行的回滚（逻辑删除，与 @TableLogic 一致；行留在库里可再审计，不物理删）
-- 期望：probe_rows_left=0，wf_process_config 未删除行数回到 83（V1.4.38 的 68 + V1.4.40 别名 8 + 原有 7）
UPDATE wf_process_config SET del_flag=1, update_time=NOW()
 WHERE remark='V1.4.40批次3运行期取证临时行' AND del_flag=0;
SELECT (SELECT COUNT(*) FROM wf_process_config WHERE biz_type='Z_B3_GUARD_PROBE' AND del_flag=0) AS probe_rows_left,
       (SELECT COUNT(*) FROM wf_process_config WHERE del_flag=0) AS active_rows,
       (SELECT COUNT(*) FROM wf_process_config) AS total_rows_incl_logical;
-- 物理删除（仅在确认永不再复用这条取证行时手工放开）
-- DELETE FROM wf_process_config WHERE remark='V1.4.40批次3运行期取证临时行';
