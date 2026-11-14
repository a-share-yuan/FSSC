-- 开放接口审计表：无幂等键的查询类请求不参与唯一约束
-- 生成列原先对「SUCCESS + 幂等键为空」也算出 event_type:''，同一事件类型的所有查询
-- 共用这一个值，第二条起即撞 uk_linku_sync_log_idem，审计写入失败并掩盖业务结果。
-- 改为只有「SUCCESS 且幂等键非空」才生成值；空键行取 NULL，MySQL 唯一索引允许多个 NULL。

ALTER TABLE linku_sync_log DROP INDEX uk_linku_sync_log_idem;
ALTER TABLE linku_sync_log DROP COLUMN active_idem_key;

ALTER TABLE linku_sync_log
  ADD COLUMN active_idem_key varchar(200)
  GENERATED ALWAYS AS (
    CASE WHEN response_status = 'SUCCESS'
          AND idempotency_key IS NOT NULL
          AND idempotency_key <> ''
         THEN CONCAT(event_type, ':', idempotency_key)
         ELSE NULL END
  ) VIRTUAL COMMENT '成功且带幂等键的行才参与去重；FAILED 与无键查询均为 NULL';

ALTER TABLE linku_sync_log ADD UNIQUE KEY uk_linku_sync_log_idem (active_idem_key);
