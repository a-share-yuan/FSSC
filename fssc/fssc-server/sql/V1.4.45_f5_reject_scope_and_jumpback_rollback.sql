-- ============================================================================
-- V1.4.45 F5 驳回级次 + 重提跳回 —— 回滚脚本
-- 对应 seed：V1.4.45_f5_reject_scope_and_jumpback_seed.sql（本轮演练未执行）
-- 生成时刻：2026-09-26；数据来源：fssc 库只读普查（mysql -N -e SELECT，零写入）
--
--  本回滚只对"2026-09-26 普查基线"成立：reject_scope 当时为 ANY_DONE 355/355、
--    resubmit_jump_back 当时为 0 355/355。执行 seed 前必须先落一份逐行快照存证：
--      SELECT id, model_key, node_key, sort, multi_type, reject_scope, resubmit_jump_back
--        FROM wf_node_config WHERE del_flag=0 ORDER BY id;
--    若 seed 之后另有第三方改过节点档位，请以该快照为准逐行还原，勿用下面的整表语句。
-- ============================================================================

-- ---------- 1) 先关闸（顺序不能颠倒：先断执行面，再改配置列）----------
-- UPDATE sys_config SET config_value='0' WHERE tenant_id=1 AND config_key='wf.reject.free.enabled';
-- UPDATE sys_config SET config_value=''   WHERE tenant_id=1 AND config_key='wf.reject.free.models';
-- UPDATE sys_config SET config_value='0'  WHERE tenant_id=1 AND config_key='wf.resubmit.jumpback.enabled';
-- UPDATE sys_config SET config_value=''   WHERE tenant_id=1 AND config_key='wf.resubmit.jumpback.models';

-- ---------- 2) 还原 reject_scope 基线（355 行全部回到 ANY_DONE）----------
-- UPDATE wf_node_config SET reject_scope='ANY_DONE', update_time=NOW()
--  WHERE del_flag=0 AND reject_scope IN ('NONE','PREV_ONE');
-- 预期影响行数 = 43（NONE：链首 39 ＋ 会签 4）＋ 241（PREV_ONE）＝ 284；ANY_DONE 的 71 行不被本句命中。
-- 校验（必须只剩 ANY_DONE 一档、且总数 355）：
-- SELECT CONCAT('rows=',COUNT(*),'|distinct=',GROUP_CONCAT(DISTINCT reject_scope))
--   FROM wf_node_config WHERE del_flag=0;

-- ---------- 3) 还原 resubmit_jump_back 基线（5 行回到 0）----------
-- UPDATE wf_node_config SET resubmit_jump_back=0, update_time=NOW()
--  WHERE del_flag=0 AND id IN (1348,1504,1505,1640,1641) AND resubmit_jump_back=1;
-- 校验（jump_back=1 的行数必须为 0）：
-- SELECT CONCAT('jumpback_on=',COUNT(*)) FROM wf_node_config WHERE del_flag=0 AND resubmit_jump_back=1;

-- ---------- 4) 删除本批新增的三枚灰度键（可选；建议保留为空值而非删行）----------
-- 保留更安全的理由：删行后代码走"配置缺失"分支（与空值同样关），但审计面就查不到"曾经配过哪一档"。
-- DELETE FROM sys_config WHERE tenant_id=1
--   AND config_key IN ('wf.reject.free.models','wf.resubmit.jumpback.enabled','wf.resubmit.jumpback.models');

-- ---------- 5) 回滚后必须核的"已搬走的执行"（数据不随配置回滚）----------
-- 配置回滚只挡住"以后不再发生"，不撤销已经发生的搬执行。核查在办实例的三方一致性：
-- SELECT CONCAT(i.id,'|',IFNULL(i.current_node,'NULL'),'|',IFNULL(i.current_assignee,'NULL'),'|',i.status)
--   FROM wf_instance i WHERE i.status='RUNNING' ORDER BY i.id;
-- SELECT CONCAT(t.proc_inst_id,'|',t.node_key,'|',t.status,'|',IFNULL(t.assignee_id,'NULL'))
--   FROM wf_task t WHERE t.status='PENDING' ORDER BY t.proc_inst_id, t.node_key;
-- 两条清单里，同一实例的"当前节点"必须与唯一一条 PENDING 镜像同源；不一致即说明回滚时机上有半完成驳回，
-- 必须按方案 §6 步骤 6 的三方一致口径逐单处置（人工，不改库）。
