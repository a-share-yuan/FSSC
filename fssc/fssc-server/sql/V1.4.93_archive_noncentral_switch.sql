-- =============================================================================
-- V1.4.93 档案非集中模式切换（单公司试点：33 昆山爱共享精密制造有限公司）
-- =============================================================================
-- 来源：人工测试/档案集中管理模式开关_非集中模式流程改造方案_20261024.md §5.2 / §7
--
-- 前置（缺一不可，未满足时本件以 SIGNAL 中止，不会半执行）：
--   1) :8089 已换含 V1.4.92 代码腿的新包（DeliveryService.isNonCentralArchive 在位）；
--   2) 四支 _NC 模型已由设计器导入并发布：CLAIM_NC / SETTLE_NC / FA_ADD_NC / FA_TRANSFER_NC，
--      且 wf_model.status='DEPLOYED'、deploy_id / proc_def_id 非空；
--   3) wf_model.org_scope/org_ids 已置 ORG/33（本件第 2 步统一写入，作第二道 fail-closed）。
--
-- 幂等口径：
--   - 本件为**一次性执行**：步骤 1 的硬闸发现任何 _NC 残留即中止（防误覆盖别人的试点）；
--     需要重跑必须先执行回滚件；
--   - 齐套键：只做「追加」，用 FIND_IN_SET / INSTR 护栏，绝不整值覆盖（现网 TR_PAY 规则必须保住）；
--   - 公司模式：仅当 company_code='AIG-KS' 且当前为 CENTRAL 时更新。
--
-- 回滚：V1.4.93_archive_noncentral_switch_rollback.sql
--  表名/列名依据 2026-10-24 实测 information_schema；本库曾于 10-23 20:03~20:23 整库重建，
--   执行前必须重跑本件「步骤 0」并核对期望值。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 步骤 0：执行前只读复点（对不上就停）
-- -----------------------------------------------------------------------------
SELECT 'A nc_company(expect 1=33/AIG-KS/CENTRAL)' AS chk,
       (SELECT COUNT(*) FROM sys_company
         WHERE id = 33 AND company_code = 'AIG-KS' AND archive_mode = 'CENTRAL' AND del_flag = 0) AS v
UNION ALL
SELECT 'B other_non_central(expect 0)',
       (SELECT COUNT(*) FROM sys_company WHERE archive_mode <> 'CENTRAL' AND del_flag = 0)
UNION ALL
SELECT 'C nc_models_deployed(expect 4)',
       (SELECT COUNT(*) FROM wf_model
         WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC')
           AND status = 'DEPLOYED' AND deploy_id IS NOT NULL AND del_flag = 0)
UNION ALL
SELECT 'D nc_node_cfg_rows(expect 0)',
       (SELECT COUNT(*) FROM wf_node_config WHERE RIGHT(model_key, 3) = '_NC')
UNION ALL
SELECT 'E nc_route_rows(expect 0)',
       (SELECT COUNT(*) FROM wf_process_config
         WHERE RIGHT(model_key, 3) = '_NC' AND company_id = 33)
UNION ALL
SELECT 'F completeness_models',
       (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.models')
UNION ALL
SELECT 'G completeness_rules',
       (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.rules');

-- -----------------------------------------------------------------------------
-- 步骤 1：前置硬闸（模型未发布 / 已有非集中公司 / 公司档案模式不符 → 中止）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS tmp_v1493_guard;
DELIMITER $$
CREATE PROCEDURE tmp_v1493_guard()
BEGIN
  IF (SELECT COUNT(*) FROM sys_company
       WHERE id = 33 AND company_code = 'AIG-KS' AND archive_mode = 'CENTRAL' AND del_flag = 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: company 33 (AIG-KS) 不存在或不是 CENTRAL，禁止继续';
  END IF;
  IF (SELECT COUNT(*) FROM sys_company WHERE archive_mode <> 'CENTRAL' AND del_flag = 0) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 已存在非集中公司，本件只允许首个试点，禁止继续';
  END IF;
  IF (SELECT COUNT(*) FROM wf_model
       WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC')
         AND status = 'DEPLOYED' AND deploy_id IS NOT NULL AND del_flag = 0) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 四支 _NC 模型未全部发布，禁止继续';
  END IF;
  IF (SELECT COUNT(*) FROM wf_node_config WHERE RIGHT(model_key, 3) = '_NC') <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 已存在 _NC 节点配置（疑似上次未回滚），先人工核对';
  END IF;
  IF (SELECT COUNT(*) FROM wf_process_config
       WHERE RIGHT(model_key, 3) = '_NC' AND company_id = 33) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 已存在 33 公司的 _NC 路由（疑似上次未回滚），先人工核对';
  END IF;
  -- CLAIM/Task_7 为 POST 档，运行时只能靠参与人方案取人；源方案缺失或已残留副本都中止
  IF (SELECT COUNT(*) FROM wf_assignee_scheme
       WHERE model_key = 'CLAIM' AND node_key = 'Task_7' AND del_flag = 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 集中版 CLAIM/Task_7 参与人方案不是唯一一条，禁止继续';
  END IF;
  IF (SELECT COUNT(*) FROM wf_assignee_scheme WHERE model_key = 'CLAIM_NC') <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V1.4.93 ABORT: 已存在 CLAIM_NC 参与人方案（疑似上次未回滚），先人工核对';
  END IF;
END$$
DELIMITER ;
CALL tmp_v1493_guard();
DROP PROCEDURE tmp_v1493_guard;

-- -----------------------------------------------------------------------------
-- 步骤 2：_NC 模型元数据（org_scope=ORG + org_ids=33；其余元数据与集中版同值）
--   模型实体本身由设计器导入/发布创建，本步只补齐「组织范围」与模板元数据
-- -----------------------------------------------------------------------------
UPDATE wf_model nc JOIN wf_model src ON src.model_key = LEFT(nc.model_key, CHAR_LENGTH(nc.model_key) - 3)
   SET nc.org_scope = 'ORG', nc.org_ids = '33', nc.source = 'TEMPLATE',
       nc.category = src.category, nc.biz_type = src.biz_type
 WHERE nc.model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC');

-- -----------------------------------------------------------------------------
-- 步骤 3：节点配置副本（整行复制集中版，摘掉扫描节点）
--   扫描节点：CLAIM/ SETTLE Task_2、FA_ADD FC_1、FA_TRANSFER FT_1
--   del_flag=1 的历史行一并复制，保持与集中版相同的回退语义
-- -----------------------------------------------------------------------------
DELETE FROM wf_node_config WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC');

INSERT INTO wf_node_config (tenant_id, model_key, node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, create_by, update_by, del_flag,
       reject_scope, resubmit_jump_back)
SELECT tenant_id, 'CLAIM_NC', node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, NULL, NULL, del_flag,
       reject_scope, resubmit_jump_back
  FROM wf_node_config
 WHERE model_key = 'CLAIM' AND node_key <> 'Task_2';

INSERT INTO wf_node_config (tenant_id, model_key, node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, create_by, update_by, del_flag,
       reject_scope, resubmit_jump_back)
SELECT tenant_id, 'SETTLE_NC', node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, NULL, NULL, del_flag,
       reject_scope, resubmit_jump_back
  FROM wf_node_config
 WHERE model_key = 'SETTLE' AND node_key <> 'Task_2';

INSERT INTO wf_node_config (tenant_id, model_key, node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, create_by, update_by, del_flag,
       reject_scope, resubmit_jump_back)
SELECT tenant_id, 'FA_ADD_NC', node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, NULL, NULL, del_flag,
       reject_scope, resubmit_jump_back
  FROM wf_node_config
 WHERE model_key = 'FA_ADD' AND node_key <> 'FC_1';

INSERT INTO wf_node_config (tenant_id, model_key, node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, create_by, update_by, del_flag,
       reject_scope, resubmit_jump_back)
SELECT tenant_id, 'FA_TRANSFER_NC', node_key, node_name, assign_type, assign_value,
       handling_mode, up_level, multi_type, vote_ratio, vote_mode, vote_value, vote_basis, vote_fallback,
       allow_transfer, allow_addsign, empty_strategy, auto_pass_self, timeout_hours, timeout_action,
       form_perm, cc_users, cc_roles, first_node_auto_pass, sort, NULL, NULL, del_flag,
       reject_scope, resubmit_jump_back
  FROM wf_node_config
 WHERE model_key = 'FA_TRANSFER' AND node_key <> 'FT_1';

-- -----------------------------------------------------------------------------
-- 步骤 3b：参与人方案副本（仅 CLAIM/Task_7）
--   该节点 assign_type=POST，运行时由 wf_assignee_scheme + wf_assignee_dim 取人；
--   不复制则 CLAIM_NC/Task_7 无人可办且不报错（引擎 default 分支静默 no-op）。
--   源方案：ROLE FSSC_AUDITOR ＋ POST J0060（OR，priority 1）。
-- -----------------------------------------------------------------------------
INSERT INTO wf_assignee_scheme (tenant_id, model_key, node_key, priority, condition_expr,
       combine_mode, enabled, sort, create_by, del_flag)
SELECT tenant_id, 'CLAIM_NC', node_key, priority, condition_expr,
       combine_mode, enabled, sort, NULL, 0
  FROM wf_assignee_scheme
 WHERE model_key = 'CLAIM' AND node_key = 'Task_7' AND del_flag = 0;
SET @v1493_scheme_id = LAST_INSERT_ID();
INSERT INTO wf_assignee_dim (tenant_id, scheme_id, dim_type, dim_value, relation,
       up_level, include_children, sort, create_by, del_flag)
SELECT tenant_id, @v1493_scheme_id, dim_type, dim_value, relation,
       up_level, include_children, sort, NULL, 0
  FROM wf_assignee_dim
 WHERE scheme_id = (SELECT id FROM wf_assignee_scheme
                     WHERE model_key = 'CLAIM' AND node_key = 'Task_7' AND del_flag = 0
                     ORDER BY id LIMIT 1)
   AND del_flag = 0;

-- -----------------------------------------------------------------------------
-- 步骤 4：路由副本（18 条扇出逐条镜像到 33 公司，priority=30 > 现网最大 20）
--   sub_type / biz_type 必须与原规则同值，否则会被集中版精确规则抢走
-- -----------------------------------------------------------------------------
DELETE FROM wf_process_config WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC') AND company_id = 33;

INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, company_id,
       dept_id, amount_min, amount_max, condition_expr, priority, status, remark, create_by, del_flag)
SELECT tenant_id, biz_type, sub_type, CONCAT(config_name, '（非集中档案·公司33）'), 'CLAIM_NC', 33,
       dept_id, amount_min, amount_max, condition_expr, 30, 'NORMAL',
       'V1.4.93 非集中档案试点副本', NULL, 0
  FROM wf_process_config
 WHERE model_key = 'CLAIM' AND del_flag = 0;

INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, company_id,
       dept_id, amount_min, amount_max, condition_expr, priority, status, remark, create_by, del_flag)
SELECT tenant_id, biz_type, sub_type, CONCAT(config_name, '（非集中档案·公司33）'), 'SETTLE_NC', 33,
       dept_id, amount_min, amount_max, condition_expr, 30, 'NORMAL',
       'V1.4.93 非集中档案试点副本', NULL, 0
  FROM wf_process_config
 WHERE model_key = 'SETTLE' AND del_flag = 0;

INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, company_id,
       dept_id, amount_min, amount_max, condition_expr, priority, status, remark, create_by, del_flag)
SELECT tenant_id, biz_type, sub_type, CONCAT(config_name, '（非集中档案·公司33）'), 'FA_ADD_NC', 33,
       dept_id, amount_min, amount_max, condition_expr, 30, 'NORMAL',
       'V1.4.93 非集中档案试点副本', NULL, 0
  FROM wf_process_config
 WHERE model_key = 'FA_ADD' AND del_flag = 0;

INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, company_id,
       dept_id, amount_min, amount_max, condition_expr, priority, status, remark, create_by, del_flag)
SELECT tenant_id, biz_type, sub_type, CONCAT(config_name, '（非集中档案·公司33）'), 'FA_TRANSFER_NC', 33,
       dept_id, amount_min, amount_max, condition_expr, 30, 'NORMAL',
       'V1.4.93 非集中档案试点副本', NULL, 0
  FROM wf_process_config
 WHERE model_key = 'FA_TRANSFER' AND del_flag = 0;

-- -----------------------------------------------------------------------------
-- 步骤 5：齐套闸扩到 _NC（追加，不覆盖 TR_PAY 现网值）
--   接续节点＝扫描节点被摘后的同分支后继（2026-10-24 按在役 BPMN 连线实测）
--   CLAIM→Task_4、SETTLE→Task_4、FA_ADD（转固档）→FC_2、FA_TRANSFER（默认档）→FT_2
-- -----------------------------------------------------------------------------
UPDATE sys_config
   SET config_value = CONCAT(config_value, ',CLAIM_NC,SETTLE_NC,FA_ADD_NC,FA_TRANSFER_NC')
 WHERE config_key = 'bill.completeness.models'
   AND FIND_IN_SET('CLAIM_NC', config_value) = 0;

UPDATE sys_config
   SET config_value = CONCAT(config_value,
       ';CLAIM_NC:NODE:Task_4=attach;SETTLE_NC:NODE:Task_4=attach;FA_ADD_NC:NODE:FC_2=attach;FA_TRANSFER_NC:NODE:FT_2=attach')
 WHERE config_key = 'bill.completeness.rules'
   AND INSTR(config_value, 'CLAIM_NC') = 0;

-- -----------------------------------------------------------------------------
-- 步骤 6：公司模式切换（放最后：此前所有腿已就位，翻转后即整套语义生效）
-- -----------------------------------------------------------------------------
UPDATE sys_company SET archive_mode = 'NON_CENTRAL'
 WHERE id = 33 AND company_code = 'AIG-KS' AND archive_mode = 'CENTRAL';

-- -----------------------------------------------------------------------------
-- 步骤 7：执行后复点
-- -----------------------------------------------------------------------------
SELECT 'H company33_mode(expect NON_CENTRAL)' AS chk,
       (SELECT archive_mode FROM sys_company WHERE id = 33) AS v
UNION ALL
SELECT 'I nc_models(expect 4 ORG/33)',
       (SELECT COUNT(*) FROM wf_model WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC')
          AND org_scope = 'ORG' AND org_ids = '33' AND status = 'DEPLOYED')
UNION ALL
SELECT 'J nc_node_cfg(expect 23/21/18/13)',
       (SELECT GROUP_CONCAT(CONCAT(model_key, '=', c) ORDER BY model_key)
          FROM (SELECT model_key, COUNT(*) c FROM wf_node_config
                 WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC') GROUP BY model_key) t)
UNION ALL
SELECT 'K nc_routes(expect 18)', (SELECT COUNT(*) FROM wf_process_config
         WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC') AND company_id = 33 AND del_flag = 0)
UNION ALL
SELECT 'N nc_scheme(expect scheme=1 dims=2)',
       (SELECT CONCAT('scheme=', (SELECT COUNT(*) FROM wf_assignee_scheme WHERE model_key = 'CLAIM_NC' AND del_flag = 0),
                      ' dims=', (SELECT COUNT(*) FROM wf_assignee_dim d
                                   JOIN wf_assignee_scheme s ON s.id = d.scheme_id
                                  WHERE s.model_key = 'CLAIM_NC' AND d.del_flag = 0)))
UNION ALL
SELECT 'L completeness_models', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.models')
UNION ALL
SELECT 'M completeness_rules', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.rules');
