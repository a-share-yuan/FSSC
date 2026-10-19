-- V1.4.37 回滚：把 wf_instance.biz_no 还原为备份表里的原值（原值全为 NULL ⇒ 等价于清掉本轮回填）
-- 仅在 seed 执行后需要撤销时使用；未执行 seed 时本脚本无副作用（备份表不存在会先报错，属预期）。
UPDATE wf_instance w JOIN wf_instance_bak_V1437_20260923 b ON b.id = w.id
   SET w.biz_no = b.biz_no;

SELECT '回滚后仍非空的目标域行数(应为0)' k, COUNT(*) c
FROM wf_instance w
WHERE w.biz_no IS NOT NULL
  AND w.id IN (SELECT id FROM wf_instance_bak_V1437_20260923);

-- 确认上面为 0 后再手工放开这一行，彻底删除备份表（保守：默认保留）
-- DROP TABLE wf_instance_bak_V1437_20260923;
