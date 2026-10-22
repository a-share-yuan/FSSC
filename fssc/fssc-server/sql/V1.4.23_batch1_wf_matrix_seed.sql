-- ============================================================================
-- V1.4.23 批次 1 · 配置止血：超时矩阵落位 + empty_strategy 按节点性质定档 + 风控三档开关 seed
-- 依据：财务共享流程全面优化方案_20260922.md §8.2 已裁决档位 ①~⑥（用户 2026-09-22「认可，执行吧」）
--       ② AUTO_PASS 白名单制默认关；③ 审批节点 ESCALATE / 纯知会 SKIP / 作业节点维持 ERROR；⑤ 反欺诈三档（硬阻断位下移到付款前）
-- 铁律：只加行、只改新列值，不删任何行、不动业务表；本脚本可被同名 _rollback 脚本逐值还原。
-- 前置实测（2026-09-22，只读盘点）：
--   · wf_node_config 602 行（tenant_id=1）empty_strategy 全为 ERROR、timeout_action 全为 NOTIFY（批次0 临时值已回滚）
--   · timeout_hours：24h 280 行 / 8h 136 行 / 48h 112 行 / NULL 74 行
--   · sys_config 现存相关键：approve.deny_self=1、approve.pick_mode.enabled=1、approve.pick_mode.models=LOAN、
--     approve.transfer_strict.enabled=0、fraud.frequency.threshold=10、fraud.whitelist.users=…（本脚本新增键均不存在）
--   · 节点性质分类（按 ③ 口径实跑）：审批/决策 299 行→ESCALATE；纯知会 1 行→SKIP；作业执行 93 + 其它核算/提交 151 + 非组织取人 58 →维持 ERROR
-- 回滚：V1.4.23_batch1_wf_matrix_seed_rollback.sql
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

-- ---------- 0. 写前备份（只备份将被改动的列，回滚按 id 逐值还原） ----------
CREATE TABLE IF NOT EXISTS wf_node_config_bak_v1423_20260922 AS
  SELECT id, tenant_id, model_key, node_key, node_name, assign_type,
         empty_strategy, timeout_hours, timeout_action, handling_mode
    FROM wf_node_config WHERE del_flag = 0;
CREATE TABLE IF NOT EXISTS sys_config_bak_v1423_20260922 AS
  SELECT * FROM sys_config
   WHERE config_key IN ('wf.timeout.autopass.nodes','wf.timeout.autopass.max_amount',
                        'fraud.block_on_submit.enabled','fraud.shadow_mode.enabled',
                        'approve.deny_serial_same_handler');

-- ---------- 1. ② 超时矩阵：AUTO_PASS 白名单制、默认不放行 ----------
-- INSERT IGNORE：键已存在时**不覆盖**运维调过的值（uk_tenant_config_key 保唯一）。
INSERT IGNORE INTO sys_config (tenant_id, config_key, config_name, config_value, value_type, group_code, is_builtin, editable, remark)
VALUES
 (1,'wf.timeout.autopass.nodes','超时自动通过白名单(modelKey:nodeKey逗号分隔,空=一律不放行)','',
    'STRING','COMMON',0,1,'批次1②：AUTO_PASS 走白名单制、默认关。四条硬门槛同时成立才放行（代码 WfTimeoutJob.autoPassDenial）：①在本白名单内 ②单据无金额敞口或低于 max_amount ③节点非作业执行节点 ④非会签/并联多实例节点。逐节点由业务确认后填入，宁缺毋滥。'),
 (1,'wf.timeout.autopass.max_amount','超时自动通过金额上限(元,超此金额一律不放行)','5000',
    'NUMBER','WORKFLOW',1,1,'批次1②：白名单节点还须单据金额≤本值才允许自动通过。初值 5000 元，业务复核后可在参数配置页直接改。'),
-- ---------- 2. ⑤ 反欺诈三档：提单侧改"人工待审"，硬阻断位下移到付款指令执行前 ----------
 (1,'fraud.block_on_submit.enabled','反欺诈高风险是否在提单时硬阻断(1阻断/0转人工待审)','0',
    'STRING','compliance',0,1,'批次1⑤：默认 0＝提单侧只打标进复核队列不挡流程，硬阻断改由付款指令执行前把关（PayBatchService.execute→paymentBlockReason）。置 1 即恢复旧的提单硬拦截口径。'),
 (1,'fraud.shadow_mode.enabled','反欺诈影子模式(1只打标不阻断/0正常处置)','0',
    'STRING','compliance',0,1,'批次1⑤：批次2端到端演练期置 1，只记录不阻断，用于观察误拦率；演练结束必须改回 0，否则付款前刚性拦截也失效。'),
-- ---------- 3. D-MC-2 同人连办：本轮仍不开，先把开关显性化（空=不生效，与现状一致） ----------
 (1,'approve.deny_serial_same_handler','同人连办拦截生效流程链(modelKey逗号分隔,空=不启用)','',
    'STRING','COMMON',0,1,'§5.4 同人多节点未梳理前开启会批量卡单，故批次1仅登记开关不启用；批次2评估。');

-- ---------- 4. ③ empty_strategy 定档：审批/决策节点 → ESCALATE ----------
-- 判定口径（与写前实跑分类完全一致）：组织取人（ROLE/POST）+ 非作业执行动作 + 名称或【岗位】体现决策权。
-- 无上级可升级时仍按 ERROR 抛出「缺哪个岗、哪家公司的、找谁补」的明确提示，不会静默放行。
-- 岗位是经理/总监但动作本身是"提交/开具/创建/维护/上传/计提"的，属作业而非决策（14 行，如「开具发票【税务经理】」）：
-- 这类节点无人时升级到上级只会让任务落到不做这件事的人手里（隐性滞留），不如维持 400 明确补岗 —— 故按"动词优先于岗位"排除。
UPDATE wf_node_config
   SET empty_strategy = 'ESCALATE'
 WHERE del_flag = 0 AND tenant_id = 1
   AND empty_strategy = 'ERROR'
   AND assign_type IN ('ROLE','POST')
   AND node_name NOT REGEXP '扫描|影像|归档|登记|台账|邮寄|打印|制证|生成凭证|录入|开票|付款|支付|打款|认领|对账函|推送|接收'
   AND node_name NOT REGEXP '^知会|^抄送|知会$|抄送$|消息|系统通知|邮件通知'
   AND ( node_name REGEXP '审批|审核|复核|核准|批准|签批|会签|终审|核定|确认|审查|评价|认定'
      OR SUBSTRING_INDEX(SUBSTRING_INDEX(node_name,'【',-1),'】',1) REGEXP '经理|总监|总裁|总经理|分管|负责人|主管|上级|办公会|董办' )
   AND NOT ( node_name REGEXP '提交|发起|开具|创建|维护|上传|计提|收款计划|认证'
         AND node_name NOT REGEXP '^(审核|审批|复核|会签|终审|签批|核定|认定|审查|评价)' );

-- ---------- 5. ③ 纯知会/消息节点 → SKIP（全库仅 1 行，逐节点显式授权并留痕） ----------
-- RECEIVABLE Task_5「邮件通知项目经理、项目会计和相关领导」：纯通知动作，无审批权、不绑定单据操作；
-- co31/32/35 等无 PROJECT_MANAGER 的公司此前必然 400 卡死在途收款流程，是本轮真实撞到的滞留点。
-- 跳过会写 wf_task_history 的 AUTO_SKIP 行（WfEngineFacade.recordEmptyStrategy），审计可回溯。
UPDATE wf_node_config
   SET empty_strategy = 'SKIP'
 WHERE del_flag = 0 AND tenant_id = 1
   AND model_key = 'RECEIVABLE' AND node_key = 'Task_5'
   AND node_name LIKE '邮件通知%';

-- ---------- 6. ② 时限矩阵铺满：无时限的 74 行补普通档 24h（NOTIFY 催办，不自动通过） ----------
-- 行业方案共享口径：普通任务时限 24h、加急 4h，且时限是考核标记而非放行开关。
-- 现网 8/24/48 三档存量值一律不动（改档属业务口径调整，需逐流程确认，不入批次1）；加急 4h 档留待批次2按节点手工设。
UPDATE wf_node_config
   SET timeout_hours = 24
 WHERE del_flag = 0 AND tenant_id = 1
   AND timeout_hours IS NULL;

-- ---------- 7. 自检 ----------
SELECT 'empty_strategy 分布' chk, empty_strategy v, COUNT(*) n
  FROM wf_node_config WHERE del_flag = 0 GROUP BY empty_strategy ORDER BY n DESC;
SELECT 'timeout_hours 分布' chk, IFNULL(CAST(timeout_hours AS CHAR),'NULL') v, COUNT(*) n
  FROM wf_node_config WHERE del_flag = 0 GROUP BY timeout_hours ORDER BY n DESC;
SELECT 'timeout_action 分布' chk, timeout_action v, COUNT(*) n
  FROM wf_node_config WHERE del_flag = 0 GROUP BY timeout_action ORDER BY n DESC;
SELECT config_key, config_value, editable FROM sys_config
 WHERE del_flag = 0 AND config_key IN ('wf.timeout.autopass.nodes','wf.timeout.autopass.max_amount',
                                        'fraud.block_on_submit.enabled','fraud.shadow_mode.enabled',
                                        'approve.deny_serial_same_handler') ORDER BY config_key;
SELECT '备份行数' chk,
       (SELECT COUNT(*) FROM wf_node_config_bak_v1423_20260922) node_bak,
       (SELECT COUNT(*) FROM sys_config_bak_v1423_20260922) cfg_bak;
-- 期望：ESCALATE 299 / SKIP 1 / ERROR 302；timeout_hours 无 NULL；timeout_action 仍全 NOTIFY（AUTO_PASS 由白名单空=不放行）；5 个键各 1 行
