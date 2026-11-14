-- V1.4.39 回滚：按记录表 wf_model_biztype_bak_V1439_20260923 的 id 精确还原 biz_type（本批 17 行的原值全部是 NULL）
-- 只还原这 17 行的 biz_type/update_time，其余列、其余行一律不动；零 DELETE。

-- 0) 将要还原的行（人工核对条数=17）
SELECT id, model_key, old_biz_type FROM wf_model_biztype_bak_V1439_20260923 ORDER BY id;

-- 1) 还原
UPDATE wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id
   SET m.biz_type=b.old_biz_type, m.update_time=b.old_update_time;

-- 2) 复核：回到改前基线（期望 null_rows=17，本批登记行剩 0）
SELECT (SELECT COUNT(*) FROM wf_model WHERE del_flag=0 AND (biz_type IS NULL OR biz_type='')) AS null_rows,
       (SELECT COUNT(*) FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id WHERE m.biz_type IS NOT NULL) AS still_named;
