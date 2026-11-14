-- =====================================================================
-- V1.6.20  预付款比例上限参数（D-V5-1104-08）
-- 背景：预付款（bill_sub_type=EXP_PREPAY）不再走「四单齐套」通用档——它先款后货，
--       收货单与发票在付款时点不存在。改按「合同/订单额 × 本比例」控上限。
-- 口径：非 wf./approve. 前缀 ⇒ 属租户级业务参数，租户管理员可在【全局参数】自行维护；
--       置 0 等于"任何预付款都不放行"（回滚开关）。
-- 回滚：V1.6.20_prepay_max_ratio_rollback.sql
-- =====================================================================

INSERT INTO sys_config (tenant_id, config_key, config_name, config_value, value_type, group_code,
                        is_builtin, editable, remark, create_time)
SELECT 1, 'prepay.max.ratio', '预付款比例上限（合同或订单额的比例）', '0.30', 'NUMBER', 'COMMON',
       0, 1, '预付款档专用：本次预付款 ≤ 合同额（无合同取订单额）× 本比例。缺省 0.30；置 0 = 一律不放行（回滚开关）。读取方 ThreeWayMatchService.prepayMaxRatio。', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'prepay.max.ratio' AND del_flag = 0);
