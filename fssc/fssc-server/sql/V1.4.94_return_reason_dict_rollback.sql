-- V1.4.94 回滚：删除退货原因字典（类型 + 数据）
-- 回滚后两个退货页面的原因下拉逐字回落到页面内置常量（useDict 默认处理），界面不空、不需发版。
DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type IN ('enum_return_reason_pur','enum_return_reason_sale');
DELETE FROM fssc.sys_dict_type WHERE tenant_id=1 AND dict_type IN ('enum_return_reason_pur','enum_return_reason_sale');
