-- V1.4.79 LinkU 开放接口（BCP → FSSC）唯一键写入数据库
-- 背景：BCP-FSSC 对接 HTTP 化改造（《BCP-FSSC对接HTTP化改造方案.md》）——BCP 不再直连 FSSC 内网 MySQL，
--       改为以 HMAC 签名调用 FSSC 开放接口 /api/open/linku/**。开放接口对写入类端点要求
--       「事件类型 + 唯一键」唯一，重放必须返回 alreadyProcessed 而非重复落账。
-- 现状：linku_sync_log（LinkU 回调同步日志表）无 idempotency_key 列，无法承载开放接口去重判定。
-- 应用层防线（先行、无需 DDL、已随代码上线）：
--       LinkuOpenGuard 在去重列缺失时捕获异常并降级为「无去重保护」+ 审计仍写入数据库（insertWithoutKey），
--       因此本迁移是「加固」而非「前置依赖」，可在业务低峰期择机执行。
--  执行说明（2026-10-17 已在 fssc 库执行完成）：
--   1) 步骤 0 为只读重复检测，必须在加唯一索引前执行；若结果非空，先人工处置重复数据。
--   2) linku_sync_log 为纯日志表、无 tenant_id 列（已在 FsscTenantLineHandler 全局表白名单），
--      故唯一键不含 tenant_id。
--   3) ★ 唯一约束只对「成功行」生效（生成列，规则同 V1.4.15 op_voucher 的做法）：
--      LinkuOpenGuard 的去重判定是 countSuccessByIdempotencyKey（仅统计 response_status='SUCCESS'）。
--      若直接对 (event_type, idempotency_key) 建唯一索引，一条 FAILED 审计行会永久占住该键，
--      导致同一唯一键的合法重试「先撞唯一键、再降级为无键写入」，审计丢失唯一键。
--      故用 active_idem_key 生成列：仅 SUCCESS 行取非空值，FAILED 行为 NULL（唯一索引对 NULL 不去重），
--      使「失败可重试、成功不可重放」在库级也成立。
--   4) 历史行 idempotency_key 为 NULL，生成列为 NULL，不影响历史数据。

-- 步骤 1：加列
-- （顺序说明：步骤 0 的重复检测引用 idempotency_key 列，须先加列才能执行，
--   故实际执行顺序为 步骤1 → 步骤0 → 步骤2 → 步骤3 → 步骤4。）
ALTER TABLE linku_sync_log
    ADD COLUMN idempotency_key VARCHAR(128) NULL
        COMMENT '幂等键（开放接口写入端点：租户+业务主键+期间，重复调用据此判定 alreadyProcessed）'
        AFTER request_body;

-- ---------- 步骤 0：历史重复检测（只读，必须在加唯一索引前执行） ----------
SELECT event_type, idempotency_key, COUNT(*) AS cnt, GROUP_CONCAT(id) AS log_ids
FROM linku_sync_log
WHERE idempotency_key IS NOT NULL AND response_status = 'SUCCESS'
GROUP BY event_type, idempotency_key
HAVING COUNT(*) > 1;

-- ---------- 步骤 2：成功行唯一键生成列（仅 SUCCESS 参与唯一约束） ----------
ALTER TABLE linku_sync_log
    ADD COLUMN active_idem_key VARCHAR(200)
        GENERATED ALWAYS AS (
            CASE WHEN response_status = 'SUCCESS'
                 THEN CONCAT(IFNULL(event_type, ''), ':', IFNULL(idempotency_key, ''))
                 ELSE NULL END
        ) VIRTUAL COMMENT '成功行的幂等键（FAILED=NULL，不参与唯一约束，允许重试）';

-- ---------- 步骤 3：唯一索引（去重库级防线） ----------
ALTER TABLE linku_sync_log
    ADD UNIQUE KEY uk_linku_sync_log_idem (active_idem_key);

-- ---------- 步骤 4：复核（只读） ----------
SELECT COUNT(*) AS cols_added
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'linku_sync_log'
  AND column_name IN ('idempotency_key', 'active_idem_key');

SELECT COUNT(*) AS idx_added
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'linku_sync_log' AND index_name = 'uk_linku_sync_log_idem';

-- ---------- 回滚参考 ----------
-- ALTER TABLE linku_sync_log DROP KEY uk_linku_sync_log_idem;
-- ALTER TABLE linku_sync_log DROP COLUMN active_idem_key;
-- ALTER TABLE linku_sync_log DROP COLUMN idempotency_key;
