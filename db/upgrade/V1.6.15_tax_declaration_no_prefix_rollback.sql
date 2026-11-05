-- 回滚 V1.6.15：申报单号前缀 SB -> TX
-- 注意：回滚只还原规则；用 SB 前缀新开的单据不会改号（单号是凭证性标识，不允许回改）。

UPDATE sys_number_rule
   SET prefix = 'TX',
       update_by = 1,
       update_time = NOW()
 WHERE rule_code = 'TAX_DECLARATION'
   AND del_flag = 0;
