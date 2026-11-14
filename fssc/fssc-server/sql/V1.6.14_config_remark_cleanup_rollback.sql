-- =====================================================================
-- 回滚 V1.6.14（恢复参数说明原文）
--
-- 说明：原文含内部迭代批次号与内部裁决编号，仅用于追溯变更历史。
--       恢复后参数配置页会重新显示这些编号，请确认确有需要再执行。
-- =====================================================================

UPDATE sys_config
   SET remark = '2026-10-19 实测：值=1 表示对所有流程启用连办拦截。本轮已按此口径补足人力（38个角色全部满足「人数>=单流程最大节点数+1」），闸保持开启。原remark「批次1仅登记开关不启用」与实际值矛盾，已订正以免后续误判。'
 WHERE config_key = 'approve.deny_serial_same_handler';

UPDATE sys_config
   SET remark = '2026-10-15 过渡配置：总闸键名已对齐代码（gate.enabled）并实测生效；规则收敛到 SUBMIT 档（RA-F2a：提单层只告警不阻断）⇒ 不拦任何单据。不可直接改成 TR_PAY:CONFIRM=contract_or_po 开硬闸：PayBatchService 调 assertComplete 时 modelKey/bizType 硬编码 TR_PAY、billsOfBatch 会把员工报销/借款单一起捞进来（那类单据天然无合同无PO）⇒ 会拦死报销付款批次。开硬闸前须先修 D2 适用范围（见 V5 册零章 Z35）'
 WHERE config_key = 'bill.completeness.rules';

UPDATE sys_config
   SET remark = '批次1⑤：默认 0＝提单侧只打标进复核队列不挡流程，硬阻断改由付款指令执行前把关（PayBatchService.execute→paymentBlockReason）。置 1 即恢复旧的提单硬拦截口径。'
 WHERE config_key = 'fraud.block_on_submit.enabled';

UPDATE sys_config
   SET remark = '批次1⑤：批次2端到端演练期置 1，只记录不阻断，用于观察误拦率；演练结束必须改回 0，否则付款前刚性拦截也失效。'
 WHERE config_key = 'fraud.shadow_mode.enabled';

UPDATE sys_config
   SET remark = '批次1②：白名单节点还须单据金额≤本值才允许自动通过。初值 5000 元，业务复核后可在参数配置页直接改。'
 WHERE config_key = 'wf.timeout.autopass.max_amount';

UPDATE sys_config
   SET remark = '批次1②：AUTO_PASS 走白名单制、默认关。四条硬门槛同时成立才放行（代码 WfTimeoutJob.autoPassDenial）：①在本白名单内 ②单据无金额敞口或低于 max_amount ③节点非作业执行节点 ④非会签/并联多实例节点。逐节点由业务确认后填入，宁缺毋滥。'
 WHERE config_key = 'wf.timeout.autopass.nodes';

SELECT 'V1.6.14 rollback 完成：参数说明已恢复原文' AS result;
