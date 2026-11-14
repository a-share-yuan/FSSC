-- V1.4.96 回滚：删除 ocr_record 的识别结果/失败原因两列
-- 回滚后 ocr_record 回到"只承载成功识别"的旧语义；含 代码的 jar 仍可运行
-- （失败记录环节是 catch-only，列缺失只落一条 warn），但失败将再次无记录。
ALTER TABLE fssc_image.ocr_record
  DROP COLUMN fail_reason,
  DROP COLUMN recognize_status;
