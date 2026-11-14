-- ============================================================================
-- V1.4.53_approve_serial_scope.sql
-- 连办拦截闸的作用域收窄
--
-- approve.deny_serial_same_handler 控制「同一人在同一流程实例内不得连续办理
-- 多个节点」这一约束。值 '1'（或 'ALL'）表示对所有流程生效；也支持传入逗号
-- 分隔的 bizType 列表，仅对列出的流程生效。
--
-- 该约束面向月结关账链 GL_MONTH_CLOSE：资金与关账场景要求全程由不同人办理。
-- 若全域开启，节点较多且多个节点落在同一角色的审批链会因可用人员不足而无法
-- 继续推进。因此把作用域收窄为显式链 GL_MONTH_CLOSE，其余流程的制复核分离
-- 由流程中的复核节点与 approve.deny_self=1 自审闸共同承担。
--
-- 配置层已支持按 bizType 列表判定，无需改动代码。
-- ============================================================================

UPDATE sys_config
   SET config_value = 'GL_MONTH_CLOSE',
       remark = '连办拦截闸作用域：GL_MONTH_CLOSE=仅月结关账链强制全程不同人；'
                '1/ALL=对所有流程生效。按角色铺人的多节点审批链在全域开启时'
                '可能因可用人员不足而无法推进，故默认仅对月结关账链启用。'
 WHERE config_key = 'approve.deny_serial_same_handler'
   AND config_value <> 'GL_MONTH_CLOSE';

-- ---- 执行后复核 ----
-- SELECT config_value FROM sys_config WHERE config_key='approve.deny_serial_same_handler';
--
-- ---- 回滚（恢复全域拦截）----
-- UPDATE sys_config SET config_value='1',
--        remark='对所有流程启用连办拦截。'
--  WHERE config_key='approve.deny_serial_same_handler';
-- 注意：回滚前请确认各审批链在「全程不同人」约束下仍有足够的可用人员。
