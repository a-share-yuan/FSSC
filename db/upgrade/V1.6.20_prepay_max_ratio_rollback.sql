-- 回滚 V1.6.20：删除预付款比例参数（删除前代码按缺省 0.30 处理，行为不变）
DELETE FROM sys_config WHERE config_key = 'prepay.max.ratio';
