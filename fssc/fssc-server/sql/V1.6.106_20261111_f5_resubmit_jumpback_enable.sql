-- ============================================================================
-- V1.6.106 重提跳回被驳回节点 —— CLAIM 单链灰度启用
--
-- 背景：`resubmit_jump_back` 与 `wf.resubmit.jumpback.*` 的代码早已实现
--       （WfEngineFacade.recordRejectOriginNode / injectResubmitJumpBackAnchor /
--        consumeRejectAnchor + Flowable moveActivityIdTo），
--       但全库 `resubmit_jump_back=0`、三枚 sys_config 缺失 ⇒ 配置面存在、从未被读，
--       属"配了不生效的假配置"。结果是：一处小错（补张发票）也要从部门经理开始全链重走。
--
-- 口径本身很明确：驳回时记下所在节点，重提直接回到该节点，不必从部门经理重新走。
-- 本次只搬最小的那一段：只放 CLAIM 一条链、只放
-- Task_7（财务共享审核【共享会计】）一个锚点。
--
-- 生效面（四把锁，缺一不生效）：
--       ① wf.resubmit.jumpback.enabled = 1
--       ② 'CLAIM' ∈ wf.resubmit.jumpback.models
--       ③ 锚点节点 wf_node_config.resubmit_jump_back = 1
--       ④ 运行期：金额未变 + 旧路由对账通过 + 图上可达 + 锚点非会签
--    任一不满足 ⇒ 降级为「全链重走」并记日志，不阻断重提。
--
-- 为何不同时开「自由驳回」（wf.reject.free.enabled）：
--       全库 222 个节点 reject_scope=ANY_DONE，一开总开关就一次性放开全部节点，
--       与「灰度一条链」不可调和。本次先只开跳回；自由驳回另行裁决。
-- 幂等：全部 INSERT IGNORE / 带原值守卫的 UPDATE，重复执行结果不变。
-- ============================================================================

-- ---------- 0) 前置快照（执行前留证；只读）----------
-- SELECT id, model_key, node_key, node_name, sort, multi_type, reject_scope, resubmit_jump_back
--   FROM wf_node_config WHERE del_flag = 0 AND model_key = 'CLAIM' ORDER BY sort;
-- SELECT config_key, config_value FROM sys_config
--  WHERE config_key IN ('wf.resubmit.jumpback.enabled','wf.resubmit.jumpback.models','wf.reject.free.enabled');

-- ---------- 1) 三枚灰度配置（不存在才插；已存在不动业务改过的值）----------
-- tenant_id 必须为 1：sys_config 走租户过滤，插 NULL/0 的行永远读不到＝假配置。
INSERT IGNORE INTO `sys_config`
  (`tenant_id`, `config_key`, `config_name`, `value_type`, `group_code`, `config_value`,
   `is_builtin`, `editable`, `remark`, `create_time`, `update_time`, `del_flag`)
VALUES
  (1, 'wf.resubmit.jumpback.enabled',
   '重新提交跳回被驳回节点总开关（1=允许搬执行，0=一律全链重走）',
   'BOOL', 'WORKFLOW', '0', 0, 1,
   '搬执行前六道校验（总开关/节点列/金额未变/旧路由对账/可达性/非会签），任一不过即降级为全链重走并记日志。',
   NOW(), NOW(), 0),
  (1, 'wf.resubmit.jumpback.models',
   '重提跳回逐链灰度白名单（model_key 逗号分隔，空＝全库不跳回）',
   'STRING', 'WORKFLOW', '', 0, 1,
   '与总开关同一 fail-closed 范式；跳回会跳过中间已完成环节，影响审批链完整性，故单独一把锁，可与自由驳回分别开关。',
   NOW(), NOW(), 0),
  (1, 'wf.reject.free.models',
   '自由驳回逐链灰度白名单（model_key 逗号分隔，空＝即使总开关=1 也全库不生效）',
   'STRING', 'WORKFLOW', '', 0, 1,
   '补第三把锁，避免单开总开关一次性放开全库 ANY_DONE 节点。',
   NOW(), NOW(), 0);

-- ---------- 2) 锚点节点：CLAIM Task_7 财务共享审核单据【共享会计】----------
-- 只配「返工后中间环节无复核价值」的岗。按主键定位，安全模式下可执行。
UPDATE `wf_node_config`
   SET `resubmit_jump_back` = 1, `update_time` = NOW()
 WHERE `id` = 1348 AND `resubmit_jump_back` = 0
   AND `model_key` = 'CLAIM' AND `node_key` = 'Task_7';

-- ---------- 3) 打开跳回开关（只放 CLAIM 一条链）----------
UPDATE `sys_config` SET `config_value` = '1', `update_time` = NOW()
 WHERE `tenant_id` = 1 AND `config_key` = 'wf.resubmit.jumpback.enabled';

UPDATE `sys_config` SET `config_value` = 'CLAIM', `update_time` = NOW()
 WHERE `tenant_id` = 1 AND `config_key` = 'wf.resubmit.jumpback.models';

-- ---------- 4) 后置校验 ----------
-- 预期：enabled=1 / models=CLAIM / anchor=1 / 其余节点 jump_back 仍为 0
SELECT CONCAT(
  'enabled=', (SELECT config_value FROM `sys_config`
                WHERE tenant_id = 1 AND config_key = 'wf.resubmit.jumpback.enabled'),
  '|models=', (SELECT config_value FROM `sys_config`
                WHERE tenant_id = 1 AND config_key = 'wf.resubmit.jumpback.models'),
  '|anchor=', (SELECT COUNT(*) FROM `wf_node_config`
                WHERE del_flag = 0 AND model_key = 'CLAIM'
                  AND node_key = 'Task_7' AND resubmit_jump_back = 1),
  '|anchor_total=', (SELECT COUNT(*) FROM `wf_node_config`
                WHERE del_flag = 0 AND resubmit_jump_back = 1),
  '|free_reject=', (SELECT config_value FROM `sys_config`
                WHERE tenant_id = 1 AND config_key = 'wf.reject.free.enabled')
) AS verify;
