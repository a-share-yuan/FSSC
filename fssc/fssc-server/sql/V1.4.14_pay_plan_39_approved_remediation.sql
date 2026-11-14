-- V1.4.14 历史付款计划 39 审批收尾状态补救（缺陷 D-12 的一次性数据修复）
-- 背景：PAY_PLAN_BASE 五级审批（Task_1~Task_5）已于 2026-09-19 22:24~22:47 由真实岗位
--       UI 逐节点通过（张立威→孙燕飞*→李晓谊→韩雪梅→安德臣，*Task_3 系 D-10 误投后按
--       真实岗位转办李晓谊），流程实例 31ee6448 办结、act_ru_task 在途 0。
--       但收尾钩子缺失（D-12：wf_event_action_config 无行、无 BizAction Bean），
--       pay_plan.status 丢失 APPROVED 派生态，永久停留 APPROVING。
--       付款计划 UI 仅有新建/查询，无作废/重提/状态同步入口，收尾事件不可重发——UI 不可修复。
-- 处置：经用户批准（选项「一次性SQL补状态」），将派生态补齐至审批事实态。
--       PayPlanBizAction 已上线（22:53 启动日志自动注册），此后新单据不再产生同类滞留。
-- 本迁移仅补齐单行派生状态字段，不改动任何业务事实数据（金额/审批记录/流程实例均原样）。

SELECT id, plan_no, status, amount, paid_amount, proc_inst_id FROM pay_plan WHERE id=39;

UPDATE pay_plan SET status='APPROVED' WHERE id=39 AND status='APPROVING';

-- 回滚参考：UPDATE pay_plan SET status='APPROVING' WHERE id=39 AND status='APPROVED';
-- 复核：应输出 APPROVED
SELECT id, plan_no, status FROM pay_plan WHERE id=39;
