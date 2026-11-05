-- 回滚 V1.6.16：删除 inv_invoice.transfer_reason
-- 注意：删列会丢弃已登记的原因文本，回滚前如需保留请先导出该列。

ALTER TABLE inv_invoice
  DROP COLUMN transfer_reason;
