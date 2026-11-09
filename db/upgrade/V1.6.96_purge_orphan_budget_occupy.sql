-- 回收已删除单据遗留的预算占用流水，按流水 id 精确处理，可重复执行
-- 判定：bg_occupy.biz_id 已不在 exp_bill 中，且 oper_type='OCCUPY'

UPDATE bg_item SET occupy_amount=GREATEST(0, occupy_amount - 100.00)
 WHERE id=5 AND occupy_amount >= 100.00;
DELETE FROM bg_occupy
 WHERE id=98 AND biz_id=62 AND oper_type='OCCUPY' AND amount=100.00;
