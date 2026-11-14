-- V1.4.28  批次2 第三族（GL 月结 / 总账族）配置侧对齐：wf_node_config <- 新 BPMN 唯一真值
-- 前置：13 个模型 BPMN 已经 /api/tenant/wf/designer/publish 重发布
--   GL_MONTH_CLOSE v4 / GL_MERGE v3 / GL_SALARY_PAY v3 / GL_SALARY_PROV v3 / GL_ACCRUAL v3 / GL_DEFER v3 /
--   GL_MANUAL_VOUCHER v3 / GL_OTHER_RECEIPT v3 / GL_SHARED_MASTER v3 / GL_TAX_PAY v3 / GL_TAX_PROV v3 /
--   GL_TAX_REFUND v2 / GL_OFFSET v3   （GL6-7 / GL8 零代码发起路径，本轮不重造，登记为 §5.10 下线候选）
-- 为什么必须这一步：发布链路只写 wf_model + Flowable deploy，不回写 wf_node_config；WfEngineFacade.applyNodeConfig 的
--   ROLE 分支把 config.assign_value 作为候选组【追加】到 BPMN candidateGroups 之上、从不摘除。
--   不同改 -> 已出链/已改岗的旧角色仍可办理新节点（参与人两处真值漂移）。本族 4 处改岗：
--   GL_MONTH_CLOSE.Task_9 FIN_STAFF->FIN_AUDIT（自审自查）、GL_SHARED_MASTER.Task_7 FIN_GL->FIN_AUDIT（制证与复核分岗）、
--   GL_MERGE.Task_5 FUND_STAFF->FIN_AR、GL_SALARY_PAY.Task_5 FUND_PAY_STAFF->FUND_STAFF（网银录入与审核分岗）。
-- 结构：本族 13 模型 100 个人工节点 -> 42 个（-58%）。已出链旧节点的配置行按批次2规则【保留不删】（DB 增量原则）：
--   引擎按 BPMN 节点反查配置，幽灵行不产生运行时影响，仅在复核 C 里盘点计数。
-- 本文件做四件事：1) 39 个已存在节点对齐名称/参与人/empty_strategy/超时；2) 3 个新 key 插行
--   （GL_SALARY_PAY.Task_13 超50万CFO档、GL_MONTH_CLOSE.Task_17_0/17_1 出具财务报表并行双签）；
--   3) 7 个作业节点置 handling_mode='POOL'（任务池，候选人主动领取锁定）；4) 复核。
-- empty_strategy 全族统一 ESCALATE（批次1 定档：ERROR 会在 4 家零用户公司直接抛错卡死，ESCALATE 沿汇报线兜到真人）。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.28_fam3_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1428_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('GL_MONTH_CLOSE','Task_1'),('GL_MONTH_CLOSE','Task_7'),('GL_MONTH_CLOSE','Task_9'),('GL_MONTH_CLOSE','Task_15'),('GL_MONTH_CLOSE','Task_17_0'),('GL_MONTH_CLOSE','Task_17_1'),('GL_MERGE','Task_4'),('GL_MERGE','Task_5'),('GL_MERGE','Task_10'),('GL_MERGE','Task_13'),('GL_MERGE','Task_14'),('GL_SALARY_PAY','Task_1'),('GL_SALARY_PAY','Task_3'),('GL_SALARY_PAY','Task_13'),('GL_SALARY_PAY','Task_4'),('GL_SALARY_PAY','Task_5'),('GL_SALARY_PAY','Task_9'),('GL_SALARY_PROV','Task_1'),('GL_SALARY_PROV','Task_3'),('GL_ACCRUAL','Task_2'),('GL_ACCRUAL','Task_4'),('GL_ACCRUAL','Task_6'),('GL_DEFER','Task_3'),('GL_DEFER','Task_5'),('GL_MANUAL_VOUCHER','Task_2'),('GL_MANUAL_VOUCHER','Task_5'),('GL_OTHER_RECEIPT','Task_2'),('GL_OTHER_RECEIPT','Task_4'),('GL_OTHER_RECEIPT','Task_5'),('GL_SHARED_MASTER','Task_3'),('GL_SHARED_MASTER','Task_7'),('GL_TAX_PAY','Task_2'),('GL_TAX_PAY','Task_4'),('GL_TAX_PAY','Task_6'),('GL_TAX_PROV','Task_2'),('GL_TAX_PROV','Task_4'),('GL_TAX_REFUND','Task_2'),('GL_TAX_REFUND','Task_3'),('GL_TAX_REFUND','Task_5'),('GL_OFFSET','Task_2'),('GL_OFFSET','Task_6'),('GL_OFFSET','Task_7'));

-- 0b) 新 key 插行前体检（期望 3 行，与下方 INSERT 一一对应；已有行则必须先人工核对再执行）
SELECT k.model_key, k.node_key FROM (
  SELECT 'GL_MONTH_CLOSE' model_key, 'Task_17_0' node_key
  UNION ALL
  SELECT 'GL_MONTH_CLOSE' model_key, 'Task_17_1' node_key
  UNION ALL
  SELECT 'GL_SALARY_PAY' model_key, 'Task_13' node_key
) k LEFT JOIN wf_node_config c ON c.model_key = k.model_key AND c.node_key = k.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL;

-- 1) 逐节点对齐 node_name / assign_type / assign_value / empty_strategy / timeout_hours / timeout_action
-- GL_MONTH_CLOSE.Task_1  月结 12 项作业齐套：账务/折旧摊销/银行核对/库存事务/内部往来/税金计提/制造费用/劳务预提/完工测算/收入确认/重分类/过账（作业池）【财务专员】
UPDATE wf_node_config
   SET node_name      = '月结 12 项作业齐套：账务/折旧摊销/银行核对/库存事务/内部往来/税金计提/制造费用/劳务预提/完工测算/收入确认/重分类/过账（作业池）【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_1';

-- GL_MONTH_CLOSE.Task_7  核对内部往来差异【总账会计】
UPDATE wf_node_config
   SET node_name      = '核对内部往来差异【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_7';

-- GL_MONTH_CLOSE.Task_9  制造费用分配复核【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '制造费用分配复核【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_9';

-- GL_MONTH_CLOSE.Task_15  核对科目余额表【总账会计】
UPDATE wf_node_config
   SET node_name      = '核对科目余额表【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_15';

-- GL_MERGE.Task_4  合并底稿编制：单体CF/PBC、国内公司CF、小合并、大合并底稿与CF、利息资本化、合并层面收入成本、合并BS/PL 及注释（作业池）【总账会计】
UPDATE wf_node_config
   SET node_name      = '合并底稿编制：单体CF/PBC、国内公司CF、小合并、大合并底稿与CF、利息资本化、合并层面收入成本、合并BS/PL 及注释（作业池）【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_MERGE' AND node_key = 'Task_4';

-- GL_MERGE.Task_5  内部资金往来抵消确认【应收会计】
UPDATE wf_node_config
   SET node_name      = '内部资金往来抵消确认【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MERGE' AND node_key = 'Task_5';

-- GL_MERGE.Task_10  抵消分录复核【总账会计】
UPDATE wf_node_config
   SET node_name      = '抵消分录复核【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MERGE' AND node_key = 'Task_10';

-- GL_MERGE.Task_13  合并报表审核【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '合并报表审核【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MERGE' AND node_key = 'Task_13';

-- GL_MERGE.Task_14  对外报送【财务专员】
UPDATE wf_node_config
   SET node_name      = '对外报送【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MERGE' AND node_key = 'Task_14';

-- GL_SALARY_PAY.Task_1  工资发放数据计算与网银明细准备（作业池）【总账会计】
UPDATE wf_node_config
   SET node_name      = '工资发放数据计算与网银明细准备（作业池）【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_1';

-- GL_SALARY_PAY.Task_3  审核工资发放【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核工资发放【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_3';

-- GL_SALARY_PAY.Task_4  网银录入发放明细【资金支付岗】
UPDATE wf_node_config
   SET node_name      = '网银录入发放明细【资金支付岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_PAY_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_4';

-- GL_SALARY_PAY.Task_5  网银付款审核【资金岗】
UPDATE wf_node_config
   SET node_name      = '网银付款审核【资金岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_5';

-- GL_SALARY_PAY.Task_9  复核发放记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核发放记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_9';

-- GL_SALARY_PROV.Task_1  复核工资计提汇总数据【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核工资计提汇总数据【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PROV' AND node_key = 'Task_1';

-- GL_SALARY_PROV.Task_3  审核计提记账单【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核计提记账单【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SALARY_PROV' AND node_key = 'Task_3';

-- GL_ACCRUAL.Task_2  编制费用预提表并上传签批版底稿（作业池）【成本费用岗】
UPDATE wf_node_config
   SET node_name      = '编制费用预提表并上传签批版底稿（作业池）【成本费用岗】',
       assign_type    = 'ROLE',
       assign_value   = 'COST_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_ACCRUAL' AND node_key = 'Task_2';

-- GL_ACCRUAL.Task_4  审核费用计提表【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核费用计提表【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_ACCRUAL' AND node_key = 'Task_4';

-- GL_ACCRUAL.Task_6  复核计提记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核计提记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_ACCRUAL' AND node_key = 'Task_6';

-- GL_DEFER.Task_3  资产模块维护待摊业务卡片并运行摊销（作业池）【资产管理员】
UPDATE wf_node_config
   SET node_name      = '资产模块维护待摊业务卡片并运行摊销（作业池）【资产管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FA_ASSET_ADMIN',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_DEFER' AND node_key = 'Task_3';

-- GL_DEFER.Task_5  审核待摊记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '审核待摊记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_DEFER' AND node_key = 'Task_5';

-- GL_MANUAL_VOUCHER.Task_2  审核手工凭证记账单【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核手工凭证记账单【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MANUAL_VOUCHER' AND node_key = 'Task_2';

-- GL_MANUAL_VOUCHER.Task_5  复核手工凭证记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核手工凭证记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_MANUAL_VOUCHER' AND node_key = 'Task_5';

-- GL_OTHER_RECEIPT.Task_2  收款确认并开收据、提交收款认领单【资金岗】
UPDATE wf_node_config
   SET node_name      = '收款确认并开收据、提交收款认领单【资金岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FUND_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_OTHER_RECEIPT' AND node_key = 'Task_2';

-- GL_OTHER_RECEIPT.Task_4  审核收款认领记账单【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核收款认领记账单【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_OTHER_RECEIPT' AND node_key = 'Task_4';

-- GL_OTHER_RECEIPT.Task_5  复核收款记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核收款记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_OTHER_RECEIPT' AND node_key = 'Task_5';

-- GL_SHARED_MASTER.Task_3  主数据维护、凭证规则定义与同步（作业池）【总账会计】
UPDATE wf_node_config
   SET node_name      = '主数据维护、凭证规则定义与同步（作业池）【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_SHARED_MASTER' AND node_key = 'Task_3';

-- GL_SHARED_MASTER.Task_7  审核确认主数据变更【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核确认主数据变更【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_SHARED_MASTER' AND node_key = 'Task_7';

-- GL_TAX_PAY.Task_2  审核税金缴纳申报【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核税金缴纳申报【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_PAY' AND node_key = 'Task_2';

-- GL_TAX_PAY.Task_4  税务系统确认缴款【税务经理】
UPDATE wf_node_config
   SET node_name      = '税务系统确认缴款【税务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'TAX_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_PAY' AND node_key = 'Task_4';

-- GL_TAX_PAY.Task_6  复核缴款记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核缴款记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_PAY' AND node_key = 'Task_6';

-- GL_TAX_PROV.Task_2  审核计提金额与进项认证金额【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核计提金额与进项认证金额【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_PROV' AND node_key = 'Task_2';

-- GL_TAX_PROV.Task_4  复核计提记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核计提记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_PROV' AND node_key = 'Task_4';

-- GL_TAX_REFUND.Task_2  审核退税资料【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核退税资料【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_REFUND' AND node_key = 'Task_2';

-- GL_TAX_REFUND.Task_3  税务系统确认退税到账【税务经理】
UPDATE wf_node_config
   SET node_name      = '税务系统确认退税到账【税务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'TAX_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_REFUND' AND node_key = 'Task_3';

-- GL_TAX_REFUND.Task_5  复核退税记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核退税记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_TAX_REFUND' AND node_key = 'Task_5';

-- GL_OFFSET.Task_2  计算结算金额并提交开票申请（作业池）【财务专员】
UPDATE wf_node_config
   SET node_name      = '计算结算金额并提交开票申请（作业池）【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'GL_OFFSET' AND node_key = 'Task_2';

-- GL_OFFSET.Task_6  审核报账单【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核报账单【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_OFFSET' AND node_key = 'Task_6';

-- GL_OFFSET.Task_7  复核内部往来抵消记账单【总账会计】
UPDATE wf_node_config
   SET node_name      = '复核内部往来抵消记账单【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'NOTIFY',
       update_time    = NOW()
 WHERE model_key = 'GL_OFFSET' AND node_key = 'Task_7';

-- 2) 新 key 插行（3 行：BPMN 有节点、config 无行 -> applyNodeConfig 取不到配置，只能靠 BPMN 候选组裸跑）
--    用 NOT EXISTS 守卫保证去重（本文件重复执行不会产生重复配置行）
-- GL_MONTH_CLOSE.Task_17_0  出具财务报表批准【财务总监】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'GL_MONTH_CLOSE', 'Task_17_0', '出具财务报表批准【财务总监】', 'ROLE', 'CFO', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 48, 'NOTIFY', 100, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_17_0' AND del_flag = 0);

-- GL_MONTH_CLOSE.Task_17_1  出具财务报表复核【财务经理】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'GL_MONTH_CLOSE', 'Task_17_1', '出具财务报表复核【财务经理】', 'ROLE', 'FIN_MANAGER', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 48, 'NOTIFY', 101, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'GL_MONTH_CLOSE' AND node_key = 'Task_17_1' AND del_flag = 0);

-- GL_SALARY_PAY.Task_13  财务总监审批（超 50 万）【财务总监】
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'GL_SALARY_PAY', 'Task_13', '财务总监审批（超 50 万）【财务总监】', 'ROLE', 'CFO', 'AUTO', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 24, 'NOTIFY', 102, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'GL_SALARY_PAY' AND node_key = 'Task_13' AND del_flag = 0);

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为两处真值漂移）
SELECT e.model_key, e.node_key, e.exp_role AS bpmn_groups, c.assign_value AS config_role,
       e.exp_name AS bpmn_name, c.node_name AS config_name, e.exp_es AS exp_empty, IFNULL(c.empty_strategy,'') got_empty,
       e.exp_th AS exp_th, IFNULL(c.timeout_hours,-1) got_th, e.exp_ta exp_ta, IFNULL(c.timeout_action,'') got_ta,
       e.exp_hm exp_hm, IFNULL(c.handling_mode,'') got_hm
  FROM (
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '月结 12 项作业齐套：账务/折旧摊销/银行核对/库存事务/内部往来/税金计提/制造费用/劳务预提/完工测算/收入确认/重分类/过账（作业池）【财务专员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '核对内部往来差异【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '制造费用分配复核【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_15' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '核对科目余额表【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_17_0' node_key, 'ROLE' exp_type, 'CFO' exp_role, '出具财务报表批准【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MONTH_CLOSE' model_key, 'Task_17_1' node_key, 'ROLE' exp_type, 'FIN_MANAGER' exp_role, '出具财务报表复核【财务经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MERGE' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '合并底稿编制：单体CF/PBC、国内公司CF、小合并、大合并底稿与CF、利息资本化、合并层面收入成本、合并BS/PL 及注释（作业池）【总账会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_MERGE' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '内部资金往来抵消确认【应收会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MERGE' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '抵消分录复核【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MERGE' model_key, 'Task_13' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '合并报表审核【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MERGE' model_key, 'Task_14' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '对外报送【财务专员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '工资发放数据计算与网银明细准备（作业池）【总账会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核工资发放【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_13' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批（超 50 万）【财务总监】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FUND_PAY_STAFF' exp_role, '网银录入发放明细【资金支付岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FUND_STAFF' exp_role, '网银付款审核【资金岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PAY' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核发放记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PROV' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核工资计提汇总数据【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SALARY_PROV' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核计提记账单【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_ACCRUAL' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'COST_STAFF' exp_role, '编制费用预提表并上传签批版底稿（作业池）【成本费用岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_ACCRUAL' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核费用计提表【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_ACCRUAL' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核计提记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_DEFER' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FA_ASSET_ADMIN' exp_role, '资产模块维护待摊业务卡片并运行摊销（作业池）【资产管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_DEFER' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '审核待摊记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MANUAL_VOUCHER' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核手工凭证记账单【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_MANUAL_VOUCHER' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核手工凭证记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_OTHER_RECEIPT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FUND_STAFF' exp_role, '收款确认并开收据、提交收款认领单【资金岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_OTHER_RECEIPT' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核收款认领记账单【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_OTHER_RECEIPT' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核收款记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_SHARED_MASTER' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '主数据维护、凭证规则定义与同步（作业池）【总账会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_SHARED_MASTER' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核确认主数据变更【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_PAY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核税金缴纳申报【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_PAY' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'TAX_MANAGER' exp_role, '税务系统确认缴款【税务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_PAY' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核缴款记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_PROV' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核计提金额与进项认证金额【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_PROV' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核计提记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_REFUND' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核退税资料【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_REFUND' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'TAX_MANAGER' exp_role, '税务系统确认退税到账【税务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_TAX_REFUND' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核退税记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_OFFSET' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '计算结算金额并提交开票申请（作业池）【财务专员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'GL_OFFSET' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核报账单【财务审核岗】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'GL_OFFSET' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '复核内部往来抵消记账单【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'NOTIFY' exp_ta, 'AUTO' exp_hm
  ) e
  LEFT JOIN wf_node_config c ON c.model_key = e.model_key AND c.node_key = e.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL
    OR IFNULL(c.assign_type, '') <> e.exp_type
    OR IFNULL(c.assign_value, '') <> e.exp_role
    OR IFNULL(c.node_name, '') <> e.exp_name
    OR IFNULL(c.empty_strategy, '') <> e.exp_es
    OR IFNULL(c.timeout_hours, -1) <> e.exp_th
    OR IFNULL(c.timeout_action, '') <> e.exp_ta
    OR IFNULL(c.handling_mode, '') <> e.exp_hm;

-- 4) 复核 B：本族 11 个角色的存在性与 co1 在编持岗人数（规则：status='NORMAL' 且非 admin；
--    COMPANY 范围角色必须问"该公司有没有在编持有人"，不是全局有没有人）
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('CFO','COST_STAFF','FA_ASSET_ADMIN','FIN_AR','FIN_AUDIT','FIN_GL','FIN_MANAGER','FIN_STAFF','FUND_PAY_STAFF','FUND_STAFF','TAX_MANAGER') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点 + 出链后的幽灵行（仅盘点，不改）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('GL_MONTH_CLOSE','Task_1'),('GL_MONTH_CLOSE','Task_7'),('GL_MONTH_CLOSE','Task_9'),('GL_MONTH_CLOSE','Task_15'),('GL_MONTH_CLOSE','Task_17_0'),('GL_MONTH_CLOSE','Task_17_1'),('GL_MERGE','Task_4'),('GL_MERGE','Task_5'),('GL_MERGE','Task_10'),('GL_MERGE','Task_13'),('GL_MERGE','Task_14'),('GL_SALARY_PAY','Task_1'),('GL_SALARY_PAY','Task_3'),('GL_SALARY_PAY','Task_13'),('GL_SALARY_PAY','Task_4'),('GL_SALARY_PAY','Task_5'),('GL_SALARY_PAY','Task_9'),('GL_SALARY_PROV','Task_1'),('GL_SALARY_PROV','Task_3'),('GL_ACCRUAL','Task_2'),('GL_ACCRUAL','Task_4'),('GL_ACCRUAL','Task_6'),('GL_DEFER','Task_3'),('GL_DEFER','Task_5'),('GL_MANUAL_VOUCHER','Task_2'),('GL_MANUAL_VOUCHER','Task_5'),('GL_OTHER_RECEIPT','Task_2'),('GL_OTHER_RECEIPT','Task_4'),('GL_OTHER_RECEIPT','Task_5'),('GL_SHARED_MASTER','Task_3'),('GL_SHARED_MASTER','Task_7'),('GL_TAX_PAY','Task_2'),('GL_TAX_PAY','Task_4'),('GL_TAX_PAY','Task_6'),('GL_TAX_PROV','Task_2'),('GL_TAX_PROV','Task_4'),('GL_TAX_REFUND','Task_2'),('GL_TAX_REFUND','Task_3'),('GL_TAX_REFUND','Task_5'),('GL_OFFSET','Task_2'),('GL_OFFSET','Task_6'),('GL_OFFSET','Task_7'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('GL_MONTH_CLOSE','GL_MERGE','GL_SALARY_PAY','GL_SALARY_PROV','GL_ACCRUAL','GL_DEFER','GL_MANUAL_VOUCHER','GL_OTHER_RECEIPT','GL_SHARED_MASTER','GL_TAX_PAY','GL_TAX_PROV','GL_TAX_REFUND','GL_OFFSET') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地情况（期望 7 行 POOL）
SELECT model_key, node_key, handling_mode, assign_value, node_name FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('GL_MONTH_CLOSE','Task_1'),('GL_MONTH_CLOSE','Task_7'),('GL_MONTH_CLOSE','Task_9'),('GL_MONTH_CLOSE','Task_15'),('GL_MONTH_CLOSE','Task_17_0'),('GL_MONTH_CLOSE','Task_17_1'),('GL_MERGE','Task_4'),('GL_MERGE','Task_5'),('GL_MERGE','Task_10'),('GL_MERGE','Task_13'),('GL_MERGE','Task_14'),('GL_SALARY_PAY','Task_1'),('GL_SALARY_PAY','Task_3'),('GL_SALARY_PAY','Task_13'),('GL_SALARY_PAY','Task_4'),('GL_SALARY_PAY','Task_5'),('GL_SALARY_PAY','Task_9'),('GL_SALARY_PROV','Task_1'),('GL_SALARY_PROV','Task_3'),('GL_ACCRUAL','Task_2'),('GL_ACCRUAL','Task_4'),('GL_ACCRUAL','Task_6'),('GL_DEFER','Task_3'),('GL_DEFER','Task_5'),('GL_MANUAL_VOUCHER','Task_2'),('GL_MANUAL_VOUCHER','Task_5'),('GL_OTHER_RECEIPT','Task_2'),('GL_OTHER_RECEIPT','Task_4'),('GL_OTHER_RECEIPT','Task_5'),('GL_SHARED_MASTER','Task_3'),('GL_SHARED_MASTER','Task_7'),('GL_TAX_PAY','Task_2'),('GL_TAX_PAY','Task_4'),('GL_TAX_PAY','Task_6'),('GL_TAX_PROV','Task_2'),('GL_TAX_PROV','Task_4'),('GL_TAX_REFUND','Task_2'),('GL_TAX_REFUND','Task_3'),('GL_TAX_REFUND','Task_5'),('GL_OFFSET','Task_2'),('GL_OFFSET','Task_6'),('GL_OFFSET','Task_7')) AND handling_mode = 'POOL'
 ORDER BY model_key, node_key;
