-- =====================================================================
-- V1.6.10 三张「缺 tenant_id」表收口（ / 清库窗任务）
--
-- 处置原则不是"一律补列"，而是按表的语义分两类：
--   ① 业务表 —— 必须补 tenant_id，让 MyBatis-Plus 拦截器统一接管；
--   ② 全局运维表 —— 补列反而错（一天一行会变成一租户一天一行），登记进白名单即可。
--
-- 分表结论：
--   so_order_line        业务表（销售订单行）→ 补列 + 由 so_order 回填 + 索引
--   wf_model_bpmn_backup 业务附属表（流程模型 BPMN 备份）→ 补列 + 由 wf_model 回填 + 索引
--   op_stability_log     全局运维表（稳定性日快照，StabilityLogJob 每日一行）→ 不补列，
--                        登记进 FsscTenantLineHandler.GLOBAL_DEF_TABLES 白名单（同 op_job_heartbeat）
--
-- 依赖：V1.6.00（fssc_add_column 助手过程）
-- 可重复执行：是
-- =====================================================================

-- ① so_order_line：补列（默认 1 只为满足 NOT NULL；紧接着按父单真实租户回填）
CALL fssc_add_column('so_order_line', 'tenant_id',
    '`tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户ID（由 so_order 回填）''');

UPDATE so_order_line l
  JOIN so_order o ON o.id = l.so_id
   SET l.tenant_id = o.tenant_id
 WHERE l.tenant_id <> o.tenant_id;

-- ② wf_model_bpmn_backup：同上，父单是 wf_model
CALL fssc_add_column('wf_model_bpmn_backup', 'tenant_id',
    '`tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户ID（由 wf_model 回填）''');

UPDATE wf_model_bpmn_backup b
  JOIN wf_model m ON m.id = b.model_id
   SET b.tenant_id = m.tenant_id
 WHERE b.tenant_id <> m.tenant_id;

-- ③ 索引：拦截器给每条 MP 查询都追加 tenant_id 谓词，无索引会退化成全表扫
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'so_order_line'
                    AND INDEX_NAME = 'idx_so_order_line_tenant');
SET @s := IF(@has_idx = 0,
    'ALTER TABLE `so_order_line` ADD INDEX `idx_so_order_line_tenant` (`tenant_id`)',
    'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_model_bpmn_backup'
                    AND INDEX_NAME = 'idx_wf_bpmn_backup_tenant');
SET @s := IF(@has_idx = 0,
    'ALTER TABLE `wf_model_bpmn_backup` ADD INDEX `idx_wf_bpmn_backup_tenant` (`tenant_id`, `model_key`)',
    'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ④ op_stability_log 不建列：只做存在性断言（若历史环境曾误加列，此处不删，交人工判定）
SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_stability_log'
                             AND COLUMN_NAME = 'tenant_id')
            THEN 'WARN: op_stability_log 已有 tenant_id（本脚本不改它，请确认是否历史误加）'
            ELSE 'OK: op_stability_log 保持全局运维表形态（已在 Java 白名单登记）'
       END AS op_stability_log_check;

SELECT 'V1.6.10 完成：so_order_line / wf_model_bpmn_backup 已补 tenant_id 并回填' AS result;
