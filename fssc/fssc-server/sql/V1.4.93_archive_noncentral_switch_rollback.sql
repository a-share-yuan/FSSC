-- =============================================================================
-- V1.4.93 回滚：档案非集中模式（公司 33）→ 集中
-- =============================================================================
-- 对应正向件：V1.4.93_archive_noncentral_switch.sql
--
-- 回滚动作（按依赖倒序，全部可重复执行）：
--   1) 公司 33 模式 → CENTRAL（只改本公司）
--   2) 删除 33 公司的 _NC 路由副本
--   3) 删除 _NC 节点配置
--   4) 齐套键移除本件追加的四支（REPLACE 精确串，保留 TR_PAY 现网值）
--   5) 删除四支 _NC 的 wf_model 行
--
-- 不做的事：
--   - 不删 Flowable 已部署的 CLAIM_NC:… 流程定义（ACT_RE_*）。删除引擎定义属破坏性操作，
--     需另走停服窗口；无路由指向时它们不会被启动，保留无行为影响。
--   - 不动集中版模型 / 集中版节点配置 / 其他公司。
-- =============================================================================

-- 步骤 0：回滚前读数
SELECT 'A company33_mode' AS chk, (SELECT archive_mode FROM sys_company WHERE id = 33) AS v
UNION ALL SELECT 'B nc_routes_33', (SELECT COUNT(*) FROM wf_process_config
         WHERE RIGHT(model_key, 3) = '_NC' AND company_id = 33 AND del_flag = 0)
UNION ALL SELECT 'C nc_node_cfg', (SELECT COUNT(*) FROM wf_node_config WHERE RIGHT(model_key, 3) = '_NC')
UNION ALL SELECT 'D nc_models', (SELECT COUNT(*) FROM wf_model WHERE RIGHT(model_key, 3) = '_NC' AND del_flag = 0)
UNION ALL SELECT 'E completeness_models', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.models')
UNION ALL SELECT 'F completeness_rules', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.rules');

-- 1) 公司回集中
UPDATE sys_company SET archive_mode = 'CENTRAL'
 WHERE id = 33 AND company_code = 'AIG-KS' AND archive_mode = 'NON_CENTRAL';

-- 2) 路由副本
DELETE FROM wf_process_config
 WHERE RIGHT(model_key, 3) = '_NC' AND company_id = 33;

-- 3) 节点配置副本
DELETE FROM wf_node_config WHERE RIGHT(model_key, 3) = '_NC';

-- 3b) 参与人方案副本（先维后方案，避免悬挂）
DELETE FROM wf_assignee_dim
 WHERE scheme_id IN (SELECT id FROM wf_assignee_scheme WHERE RIGHT(model_key, 3) = '_NC');
DELETE FROM wf_assignee_scheme WHERE RIGHT(model_key, 3) = '_NC';

-- 4) 齐套键：只移除本件追加的精确片段
UPDATE sys_config
   SET config_value = REPLACE(config_value, ',CLAIM_NC,SETTLE_NC,FA_ADD_NC,FA_TRANSFER_NC', '')
 WHERE config_key = 'bill.completeness.models'
   AND INSTR(config_value, ',CLAIM_NC,SETTLE_NC,FA_ADD_NC,FA_TRANSFER_NC') > 0;

UPDATE sys_config
   SET config_value = REPLACE(config_value,
       ';CLAIM_NC:NODE:Task_4=attach;SETTLE_NC:NODE:Task_4=attach;FA_ADD_NC:NODE:FC_2=attach;FA_TRANSFER_NC:NODE:FT_2=attach', '')
 WHERE config_key = 'bill.completeness.rules'
   AND INSTR(config_value,
       ';CLAIM_NC:NODE:Task_4=attach;SETTLE_NC:NODE:Task_4=attach;FA_ADD_NC:NODE:FC_2=attach;FA_TRANSFER_NC:NODE:FT_2=attach') > 0;

-- 5) 模型行（Flowable 定义保留）
DELETE FROM wf_model WHERE model_key IN ('CLAIM_NC','SETTLE_NC','FA_ADD_NC','FA_TRANSFER_NC');

-- 回滚后复点
SELECT 'G company33_mode(expect CENTRAL)' AS chk, (SELECT archive_mode FROM sys_company WHERE id = 33) AS v
UNION ALL SELECT 'H nc_routes_33(expect 0)', (SELECT COUNT(*) FROM wf_process_config
         WHERE RIGHT(model_key, 3) = '_NC' AND company_id = 33)
UNION ALL SELECT 'I nc_node_cfg(expect 0)', (SELECT COUNT(*) FROM wf_node_config WHERE RIGHT(model_key, 3) = '_NC')
UNION ALL SELECT 'J nc_models(expect 0)', (SELECT COUNT(*) FROM wf_model WHERE RIGHT(model_key, 3) = '_NC')
UNION ALL SELECT 'J2 nc_scheme(expect 0/0)',
       (SELECT CONCAT((SELECT COUNT(*) FROM wf_assignee_scheme WHERE RIGHT(model_key, 3) = '_NC'), '/',
                      (SELECT COUNT(*) FROM wf_assignee_dim d JOIN wf_assignee_scheme s ON s.id = d.scheme_id
                        WHERE RIGHT(s.model_key, 3) = '_NC')))
UNION ALL SELECT 'K completeness_models(expect TR_PAY)', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.models')
UNION ALL SELECT 'L completeness_rules(expect TR_PAY:SUBMIT=contract_or_po)', (SELECT config_value FROM sys_config WHERE config_key = 'bill.completeness.rules');
