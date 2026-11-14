-- =====================================================================
-- V1.6.14 参数配置说明文字订正
--
-- 背景
--   参数配置页直接展示 sys_config.remark。历史备注里夹带了内部迭代批次号
--   （「批次1②」「批次1⑤」「批次2端到端演练」）、内部裁决编号（「RA-F2a」）、
--   内部文档坐标（「V5 册零章 Z35」）。客户在参数配置页看到这些，既无法对应
--   到自己的需求，也不该看到内部工作方式。
--
-- 处理
--   仅订正说明文字，**不动任何配置值**，不影响运行行为。
--   保留：日期、技术原因、不可如何操作的告警——这些对运维有实际价值。
--
-- 幂等：重复执行结果一致（文本替换，改完再跑无变化）。
-- 回滚：V1.6.14_config_remark_cleanup_rollback.sql（恢复原文）
-- =====================================================================

-- ① 连办拦截开关
UPDATE sys_config
   SET remark = '值=1 表示对所有流程启用连办拦截；已按此口径补足人力（38 个角色全部满足「人数 ≥ 单流程最大节点数 + 1」），闸保持开启。'
 WHERE config_key = 'approve.deny_serial_same_handler';

-- ② 单据完整性总闸规则（保留为什么不能直接开硬闸的技术原因）
UPDATE sys_config
   SET remark = '2026-10-15 过渡配置：总闸键名已对齐代码（gate.enabled）并实测生效；规则收敛到 SUBMIT 档（提单层只告警不阻断）⇒ 不拦任何单据。不可直接改成 TR_PAY:CONFIRM=contract_or_po 开硬闸：PayBatchService 调 assertComplete 时 modelKey/bizType 硬编码 TR_PAY、billsOfBatch 会把员工报销/借款单一起捞进来（那类单据天然无合同无 PO）⇒ 会拦死报销付款。开硬闸前须先修适用范围。'
 WHERE config_key = 'bill.completeness.rules';

-- ③ 提单侧硬阻断
UPDATE sys_config
   SET remark = '默认 0＝提单侧只打标进复核队列不挡流程，硬阻断改由付款指令执行前把关（PayBatchService.execute→paymentBlockReason）。置 1 即恢复旧的提单硬拦截口径。'
 WHERE config_key = 'fraud.block_on_submit.enabled';

-- ④ 反欺诈影子模式
UPDATE sys_config
   SET remark = '演练期置 1，只记录不阻断，用于观察误拦率；演练结束必须改回 0，否则付款前刚性拦截也失效。'
 WHERE config_key = 'fraud.shadow_mode.enabled';

-- ⑤ 超时自动通过：金额上限
UPDATE sys_config
   SET remark = '白名单节点还须单据金额 ≤ 本值才允许自动通过。初值 5000 元，业务复核后可在参数配置页直接改。'
 WHERE config_key = 'wf.timeout.autopass.max_amount';

-- ⑥ 超时自动通过：节点白名单
UPDATE sys_config
   SET remark = 'AUTO_PASS 走白名单制、默认关。四条硬门槛同时成立才放行（代码 WfTimeoutJob.autoPassDenial）：①在本白名单内 ②单据无金额敞口或低于 max_amount ③节点非作业执行节点 ④非会签/并联多实例节点。逐节点由业务确认后填入，宁缺毋滥。'
 WHERE config_key = 'wf.timeout.autopass.nodes';

-- ⑦ 校验输出
SELECT config_key, LEFT(remark, 60) AS remark_head
  FROM sys_config
 WHERE config_key IN ('approve.deny_serial_same_handler', 'bill.completeness.rules',
                      'fraud.block_on_submit.enabled', 'fraud.shadow_mode.enabled',
                      'wf.timeout.autopass.max_amount', 'wf.timeout.autopass.nodes')
 ORDER BY config_key;

-- 残留检查：应为 0 行
SELECT '残留内部编号（应为 0 行）' AS step, config_key, LEFT(remark, 40) AS remark_head
  FROM sys_config
 WHERE remark REGEXP '批次[0-9]|RA-F[0-9]|V5 册|本轮|上一版';

SELECT 'V1.6.14 完成：参数说明文字已订正（未改任何配置值）' AS result;
