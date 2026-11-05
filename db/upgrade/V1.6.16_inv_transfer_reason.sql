-- 进项转出原因留痕：inv_invoice 增列 transfer_reason
-- 背景：进项转出是"已抵扣进项不得抵扣"的调账动作，税务上必须留原因（集体福利/个人消费/非正常损失/
--       简易计税/免税项目/其他）。此前只有确认框、无原因输入，也没有落库列（缺陷 T-1104-07）。
-- 取值：存中文口径（与界面下拉一致，允许自定义），最长 64 字。
-- 兼容：可为空——历史已转出的票没有原因，不能用 NOT NULL 回填。

ALTER TABLE inv_invoice
  ADD COLUMN transfer_reason VARCHAR(64) NULL COMMENT '进项转出原因（集体福利/个人消费/非正常损失/简易计税/免税项目/其他）' AFTER reverse_date;
