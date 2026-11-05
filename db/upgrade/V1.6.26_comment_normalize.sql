-- =====================================================================
-- V1.6.26  2026-11-05  表/列注释规范化
--
-- 只调整 COMMENT 文案，不改任何列类型/默认值/数据；
-- ALTER ... MODIFY COLUMN 重复执行结果一致。
-- =====================================================================

ALTER TABLE `gl_accounting_book` MODIFY COLUMN `book_type` varchar(16) NOT NULL DEFAULT 'MAIN' COMMENT '账簿类型:MAIN主账簿/SUB副账簿(口径统一)';
ALTER TABLE `md_form_field` MODIFY COLUMN `required` tinyint NOT NULL DEFAULT '0' COMMENT '必录属性:1-必录';
ALTER TABLE `md_form_field` MODIFY COLUMN `locked` tinyint NOT NULL DEFAULT '0' COMMENT '锁定属性:1-编辑时灰显不可改';
ALTER TABLE `md_print_template` COMMENT='打印模板头（套打设计器载体）';
ALTER TABLE `md_print_template_item` MODIFY COLUMN `locked` tinyint NOT NULL DEFAULT '0' COMMENT '锁定属性:1-打印时灰显不可改';
ALTER TABLE `op_dispatch_policy` MODIFY COLUMN `normal_hours` decimal(8,2) NOT NULL DEFAULT '24.00' COMMENT '普通任务时限(小时)，缺省 24h';
ALTER TABLE `op_dispatch_policy` MODIFY COLUMN `urgent_hours` decimal(8,2) NOT NULL DEFAULT '4.00' COMMENT '加急任务时限(小时)，缺省 4h';
ALTER TABLE `op_dispatch_policy` MODIFY COLUMN `balance_interval_hours` decimal(8,2) DEFAULT '1.00' COMMENT '均衡·按时的分配间隔(小时)，缺省 1h 一分';
ALTER TABLE `op_dispatch_policy` MODIFY COLUMN `balance_batch_size` int DEFAULT '10' COMMENT '均衡·按量的累积批大小(单)，缺省 10 单一分配';
ALTER TABLE `wf_approval_item` COMMENT='G2 流程节点审批项';
ALTER TABLE `wf_process_log` COMMENT='流程处理详细日志（详细日志开关，管理中心可查）';
ALTER TABLE `wf_process_outbox` COMMENT='流程投影事件发件箱（事务性发件箱实现）';
