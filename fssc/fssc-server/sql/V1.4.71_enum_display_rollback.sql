-- V1.4.71 回滚：删除 enum_* 展示字典（类型 + 数据）
DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type LIKE 'enum\_%%';
DELETE FROM fssc.sys_dict_type WHERE tenant_id=1 AND dict_type LIKE 'enum\_%%';