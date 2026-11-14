-- =====================================================================
-- 回滚 V1.6.10
--
-- 只在「已确认没有新版代码依赖这两列的租户过滤」时执行。
-- 删列不会动业务数据本身（订单行/流程备份的实体字段都在），只丢租户隔离能力；
--    代码仍按新包运行时，任何经 MyBatis-Plus 的查询都会先报 Unknown column 'tenant_id'，
--    因此必须先回滚代码（换回旧 jar）再跑本脚本。
-- =====================================================================
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'so_order_line'
                    AND INDEX_NAME = 'idx_so_order_line_tenant');
SET @s := IF(@has_idx > 0, 'ALTER TABLE `so_order_line` DROP INDEX `idx_so_order_line_tenant`', 'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_model_bpmn_backup'
                    AND INDEX_NAME = 'idx_wf_bpmn_backup_tenant');
SET @s := IF(@has_idx > 0, 'ALTER TABLE `wf_model_bpmn_backup` DROP INDEX `idx_wf_bpmn_backup_tenant`', 'DO 0');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CALL fssc_drop_column('wf_model_bpmn_backup', 'tenant_id');
CALL fssc_drop_column('so_order_line', 'tenant_id');

SELECT 'V1.6.10 rollback done' AS result;
