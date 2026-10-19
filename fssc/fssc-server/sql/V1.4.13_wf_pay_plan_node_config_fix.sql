-- V1.4.13 付款计划(PAY_PLAN_BASE)节点配置过期修正（缺陷 D-10）
-- 背景：wf_node_config 的 N020/N025/N030/N040 四行仍是旧版 BPMN 的产物——节点名称统一为
--       "审核付款计划"、assign_value 全部为 FIN_AUDIT。运行时 WfNodeConfigResolver 把
--       Task_2~Task_5 依次映射到这四行，节点配置覆盖(三级解析第①级)生效，导致
--       Task_2/Task_3 都错投给 FIN_AUDIT 持有人(孙燕飞)，而非 BPMN v3 定义的真实岗位。
--       流程配置 UI 无 assign_value 编辑字段(FlowConfig.vue 全前端零引用)，无法 UI 修正。
-- 处置：按 BPMN v3 链序把四行的角色值与节点名称对齐：
--       Task_2→N020 DIRECT_SUPERVISOR / Task_3→N025 PROJECT_MANAGER /
--       Task_4→N030 BASE_FIN_MANAGER / Task_5→N040 BASE_GM。N010(发起人提交)不动。
-- 备份：修正前整行现值已存档 BCP/.p0-verify/d10_backup_before_v1413.txt（回滚按该文件 UPDATE 回填）。
-- 本迁移为系统配置变更，经用户批准（选项「批准SQL配置迁移」），不触碰业务数据。

UPDATE wf_node_config SET node_name = '直接上级审核付款计划',   assign_value = 'DIRECT_SUPERVISOR' WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'N020';
UPDATE wf_node_config SET node_name = '项目经理审核付款计划',   assign_value = 'PROJECT_MANAGER'   WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'N025';
UPDATE wf_node_config SET node_name = '基地财务经理审核付款计划', assign_value = 'BASE_FIN_MANAGER'  WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'N030';
UPDATE wf_node_config SET node_name = '基地总经理审核付款计划',  assign_value = 'BASE_GM'           WHERE model_key = 'PAY_PLAN_BASE' AND node_key = 'N040';

-- 复核：应输出 N010 INITIATOR / N020 DIRECT_SUPERVISOR / N025 PROJECT_MANAGER / N030 BASE_FIN_MANAGER / N040 BASE_GM
SELECT node_key, node_name, assign_type, assign_value, sort FROM wf_node_config WHERE model_key='PAY_PLAN_BASE' ORDER BY sort;
