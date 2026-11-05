-- 申报单号前缀 TX -> SB：把「税务申报」与「税金计提」的单号区分开
-- 背景：两者都取 TX+yyyyMM+4 位序号，同一期间会出现 TX2026110004（申报）与 TX202610001（计提）
--       并存，肉眼无法按单号判断单据类型（缺陷 T-1104-13/18）。
-- 影响面：只改编号规则的 prefix，历史单号不动（存量单据保持 TX 前缀，追溯不受影响）。
-- 适用：全部租户

UPDATE sys_number_rule
   SET prefix = 'SB',
       update_by = 1,
       update_time = NOW()
 WHERE rule_code = 'TAX_DECLARATION'
   AND del_flag = 0;
