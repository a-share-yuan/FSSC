-- V1.4.38  批次3（代码轨）round 2 · 单据类型流程绑定"路由合一"第①批：把内置映射镜像进 wf_process_config
-- 生成方式：bpmn-repo/drafts/batch3/r2-gen-seed-sql.js 从 r2-seed-rows.tsv 机械推导（该 tsv 由 r2-routing-matrix.js
--   对 resolveProcessKey 三级链路【绑定表 → 内置 switch → 部署存在性 → expenseClaim】的 JS 1:1 复算得出，非手写）。
-- 判据（每条镜像行都必须同时满足，否则不生成）：
--   ① 改后落点＝改前落点：镜像行的 model_key 就是今天 resolveProcessKey 实际返回的 key（纯搬家，零路由变更）；
--   ② 目标 key 已部署：绑定表这条路径原本不查 ACT_RE_PROCDEF 存在性（内置映射查），指向未部署 key 会把
--      "静默落错流程"升级成"发起直接报错"，故只 seed 已部署 key；
--   ③ 有可达性证据：bizType 出现在 wfEngineFacade.start(...) 的字面量或 flowKey 推导值里，或历史实例里；
--   ④ 不加约束：company_id/dept_id/amount_min/amount_max 全 NULL、status=NORMAL、priority=10、sub_type=NULL(通配)，
--      与现有 7 行同一规则，保证任何金额/任何公司都命中，等价于今天"表未命中→内置映射"的结果；
--   ⑤ 已有绑定行的 bizType 一律不动（EXP_CLAIM / EXP_LOAN / EXP_PAY / EXP_REPAY 共 7 行不在本 seed 范围内）。
-- 不迁移项（有意留空，见 r2-routing-matrix.log §C）：
--   断链 bizType PURCHASE_ORDER_REVIEW / SUPPLIER_PAY / ARCHIVE_RECON / TR_SEAL_CHANGE：内置目标 key 未部署，
--   现状静默落 jar 内置孤儿流程 expenseClaim（3 节点、assignee=${approver}、wf_node_config 0 行、wf_model 无台账）。
--   给它们 seed 一条指向"猜出来的"流程＝替业务做裁决，故只登记为 R2-D1~D4 待裁决，不写入本文件。
-- 影响面：wf_process_config 为配置表（非业务表），本文件 INSERT-only、零 UPDATE、零 DELETE；不动 wf_model / wf_node_config /
--   业务单据表 / ACT_* 引擎表。co1 2026-09 账期仍 CLOSED，本 seed 与账期无关。回滚见同名 _rollback.sql。
-- 标记：remark='V1.4.38批次3路由合一镜像seed'（回滚按此精确匹配，只删本文件插入的行）

-- 0) 改前基线（人工记录：before_rows 应为 7，dup_biz_types 应为空）
SELECT COUNT(*) AS before_rows, SUM(del_flag=0 AND status='NORMAL') AS before_active FROM wf_process_config;
SELECT biz_type, COUNT(*) c FROM wf_process_config WHERE del_flag=0 GROUP BY biz_type HAVING c > (SELECT COUNT(DISTINCT IFNULL(sub_type,'')) FROM wf_process_config x WHERE x.del_flag=0 AND x.biz_type=wf_process_config.biz_type);

-- 0.5) 改前记录表（本 seed 是 INSERT-only、回滚按 remark 精确匹配即可；此表只为审计默认处理，不删）
CREATE TABLE IF NOT EXISTS wf_process_config_bak_V1438_20260923 AS SELECT * FROM wf_process_config;
SELECT COUNT(*) AS bak_rows FROM wf_process_config_bak_V1438_20260923;

-- 1) 镜像行（去重：同 (biz_type, sub_type, model_key) 已有未删除行则跳过）
--  1. AR_CLAIM（通配） -> AR_CLAIM   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'AR_CLAIM', NULL, 'AR服务申请审批', 'AR_CLAIM', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='AR_CLAIM' AND IFNULL(x.sub_type,'')='' AND x.model_key='AR_CLAIM');

--  2. AR_RECON（通配） -> AR_RECON   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'AR_RECON', NULL, '应收账款对账流程', 'AR_RECON', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='AR_RECON' AND IFNULL(x.sub_type,'')='' AND x.model_key='AR_RECON');

--  3. AR_REPORT（通配） -> AR_REPORT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'AR_REPORT', NULL, '应收账款管理分析报告审批流程', 'AR_REPORT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='AR_REPORT' AND IFNULL(x.sub_type,'')='' AND x.model_key='AR_REPORT');

--  4. BAD_DEBT_CONFIRM（通配） -> BAD_DEBT_CONFIRM   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'BAD_DEBT_CONFIRM', NULL, '坏账确认审批流程', 'BAD_DEBT_CONFIRM', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='BAD_DEBT_CONFIRM' AND IFNULL(x.sub_type,'')='' AND x.model_key='BAD_DEBT_CONFIRM');

--  5. BAD_DEBT_PROV（通配） -> BAD_DEBT_PROV   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'BAD_DEBT_PROV', NULL, '坏账计提审批流程', 'BAD_DEBT_PROV', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='BAD_DEBT_PROV' AND IFNULL(x.sub_type,'')='' AND x.model_key='BAD_DEBT_PROV');

--  6. BG_SCHEME（通配） -> BG_SCHEME   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'BG_SCHEME', NULL, '预算方案审批流程', 'BG_SCHEME', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='BG_SCHEME' AND IFNULL(x.sub_type,'')='' AND x.model_key='BG_SCHEME');

--  7. CONTRACT_APPROVE（通配） -> CONTRACT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'CONTRACT_APPROVE', NULL, '合同审批流程', 'CONTRACT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='CONTRACT_APPROVE' AND IFNULL(x.sub_type,'')='' AND x.model_key='CONTRACT');

--  8. CUSTOMER_MAINT（通配） -> CUSTOMER_MAINT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'CUSTOMER_MAINT', NULL, '客户主数据维护审批流程', 'CUSTOMER_MAINT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='CUSTOMER_MAINT' AND IFNULL(x.sub_type,'')='' AND x.model_key='CUSTOMER_MAINT');

--  9. EXP_APPLY（通配） -> TRAVEL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'EXP_APPLY', NULL, '出差事前申请审批流程', 'TRAVEL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='EXP_APPLY' AND IFNULL(x.sub_type,'')='' AND x.model_key='TRAVEL');

-- 10. EXP_APPLY/MEETING -> MEETING   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'EXP_APPLY', 'MEETING', '会议事前申请', 'MEETING', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='EXP_APPLY' AND IFNULL(x.sub_type,'')='MEETING' AND x.model_key='MEETING');

-- 11. EXP_APPLY/TRAIN -> TRAIN   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'EXP_APPLY', 'TRAIN', '培训费事前申请审批流程', 'TRAIN', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='EXP_APPLY' AND IFNULL(x.sub_type,'')='TRAIN' AND x.model_key='TRAIN');

-- 12. FA_ADD（通配） -> FA_ADD   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_ADD', NULL, '资产新增审批流程', 'FA_ADD', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_ADD' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_ADD');

-- 13. FA_ALIENATE（通配） -> FA_ALIENATE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_ALIENATE', NULL, '资产处置审批流程(对外销售)', 'FA_ALIENATE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_ALIENATE' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_ALIENATE');

-- 14. FA_CER（通配） -> FA_CER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_CER', NULL, '资本性支出申请(CER)审批流程', 'FA_CER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_CER' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_CER');

-- 15. FA_CHECK_DIFF（通配） -> FA_CHECK_DIFF   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_CHECK_DIFF', NULL, '资产盘点差异处理审批流程', 'FA_CHECK_DIFF', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_CHECK_DIFF' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_CHECK_DIFF');

-- 16. FA_CIP_TO_ASSET（通配） -> FA_CIP_TO_ASSET   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_CIP_TO_ASSET', NULL, '在建工程转固定资产流程', 'FA_CIP_TO_ASSET', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_CIP_TO_ASSET' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_CIP_TO_ASSET');

-- 17. FA_DEPRECIATION（通配） -> FA_DEPRECIATION   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_DEPRECIATION', NULL, '资产折旧摊销流程', 'FA_DEPRECIATION', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_DEPRECIATION' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_DEPRECIATION');

-- 18. FA_DISPOSE（通配） -> FA_DISPOSE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_DISPOSE', NULL, '资产处置审批流程(报废/卖出)', 'FA_DISPOSE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_DISPOSE' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_DISPOSE');

-- 19. FA_IMPAIRMENT（通配） -> FA_IMPAIRMENT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_IMPAIRMENT', NULL, '资产减值准备审批流程', 'FA_IMPAIRMENT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_IMPAIRMENT' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_IMPAIRMENT');

-- 20. FA_TRANSFER（通配） -> FA_TRANSFER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_TRANSFER', NULL, '资产调拨审批流程(部门间)', 'FA_TRANSFER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_TRANSFER' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_TRANSFER');

-- 21. FA_TRANSFER_CROSS（通配） -> FA_TRANSFER_CROSS   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_TRANSFER_CROSS', NULL, '资产调拨审批流程(跨法人)', 'FA_TRANSFER_CROSS', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_TRANSFER_CROSS' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_TRANSFER_CROSS');

-- 22. FA_VALUE_ADJUST（通配） -> FA_VALUE_ADJUST   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FA_VALUE_ADJUST', NULL, '资产原值调整审批流程', 'FA_VALUE_ADJUST', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FA_VALUE_ADJUST' AND IFNULL(x.sub_type,'')='' AND x.model_key='FA_VALUE_ADJUST');

-- 23. FM_ARCHIVE（通配） -> FM_ARCHIVE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FM_ARCHIVE', NULL, '凭证归档审批流程', 'FM_ARCHIVE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FM_ARCHIVE' AND IFNULL(x.sub_type,'')='' AND x.model_key='FM_ARCHIVE');

-- 24. FM_BORROW（通配） -> FM_BORROW   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FM_BORROW', NULL, '会计凭证借阅审批流程', 'FM_BORROW', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FM_BORROW' AND IFNULL(x.sub_type,'')='' AND x.model_key='FM_BORROW');

-- 25. FM_DESTROY（通配） -> FM_DESTROY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FM_DESTROY', NULL, '会计凭证销毁审批流程', 'FM_DESTROY', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FM_DESTROY' AND IFNULL(x.sub_type,'')='' AND x.model_key='FM_DESTROY');

-- 26. FM_DOC_FLOW（通配） -> FM_DOC_FLOW   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'FM_DOC_FLOW', NULL, '单据流转审批流程', 'FM_DOC_FLOW', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='FM_DOC_FLOW' AND IFNULL(x.sub_type,'')='' AND x.model_key='FM_DOC_FLOW');

-- 27. GL_ACCRUAL（通配） -> GL_ACCRUAL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_ACCRUAL', NULL, '一般总账记账(费用预提)审批流程', 'GL_ACCRUAL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_ACCRUAL' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_ACCRUAL');

-- 28. GL_DEFER（通配） -> GL_DEFER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_DEFER', NULL, '一般总账记账(费用待摊)审批流程', 'GL_DEFER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_DEFER' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_DEFER');

-- 29. GL_MANUAL_VOUCHER（通配） -> GL_MANUAL_VOUCHER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_MANUAL_VOUCHER', NULL, '一般总账记账(手工凭证)审批流程', 'GL_MANUAL_VOUCHER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_MANUAL_VOUCHER' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_MANUAL_VOUCHER');

-- 30. GL_MERGE（通配） -> GL_MERGE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_MERGE', NULL, '合并报表审批流程', 'GL_MERGE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_MERGE' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_MERGE');

-- 31. GL_MONTH_CLOSE（通配） -> GL_MONTH_CLOSE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_MONTH_CLOSE', NULL, '月结审批流程', 'GL_MONTH_CLOSE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_MONTH_CLOSE' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_MONTH_CLOSE');

-- 32. GL_OFFSET（通配） -> GL_OFFSET   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_OFFSET', NULL, '内部往来抵消审批流程', 'GL_OFFSET', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_OFFSET' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_OFFSET');

-- 33. GL_OTHER_RECEIPT（通配） -> GL_OTHER_RECEIPT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_OTHER_RECEIPT', NULL, '一般总账记账(其他收款核算)审批流程', 'GL_OTHER_RECEIPT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_OTHER_RECEIPT' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_OTHER_RECEIPT');

-- 34. GL_SALARY_PAY（通配） -> GL_SALARY_PAY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_SALARY_PAY', NULL, '薪酬核算（工资发放）流程', 'GL_SALARY_PAY', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_SALARY_PAY' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_SALARY_PAY');

-- 35. GL_SALARY_PROV（通配） -> GL_SALARY_PROV   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_SALARY_PROV', NULL, '薪酬核算（工资计提）流程', 'GL_SALARY_PROV', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_SALARY_PROV' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_SALARY_PROV');

-- 36. GL_SHARED_MASTER（通配） -> GL_SHARED_MASTER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_SHARED_MASTER', NULL, 'GL主数据维护审批流程', 'GL_SHARED_MASTER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_SHARED_MASTER' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_SHARED_MASTER');

-- 37. GL_TAX_PAY（通配） -> GL_TAX_PAY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_TAX_PAY', NULL, '税金支付审批流程', 'GL_TAX_PAY', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_TAX_PAY' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_TAX_PAY');

-- 38. GL_TAX_PROV（通配） -> GL_TAX_PROV   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_TAX_PROV', NULL, '税金计提审批流程', 'GL_TAX_PROV', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_TAX_PROV' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_TAX_PROV');

-- 39. GL_TAX_REFUND（通配） -> GL_TAX_REFUND   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'GL_TAX_REFUND', NULL, '税务退税审批流程', 'GL_TAX_REFUND', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='GL_TAX_REFUND' AND IFNULL(x.sub_type,'')='' AND x.model_key='GL_TAX_REFUND');

-- 40. INVOICE_ISSUE（通配） -> INVOICE_ISSUE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INVOICE_ISSUE', NULL, '发票开具及收入确认审批流程', 'INVOICE_ISSUE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INVOICE_ISSUE' AND IFNULL(x.sub_type,'')='' AND x.model_key='INVOICE_ISSUE');

-- 41. INVOICE_MATCH（通配） -> INVOICE_MATCH   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INVOICE_MATCH', NULL, '发票校验异常审批', 'INVOICE_MATCH', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INVOICE_MATCH' AND IFNULL(x.sub_type,'')='' AND x.model_key='INVOICE_MATCH');

-- 42. INVOICE_MATCH_MANUAL（通配） -> INVOICE_MATCH_MANUAL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INVOICE_MATCH_MANUAL', NULL, '发票校验流程(手工)', 'INVOICE_MATCH_MANUAL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INVOICE_MATCH_MANUAL' AND IFNULL(x.sub_type,'')='' AND x.model_key='INVOICE_MATCH_MANUAL');

-- 43. INV_DEVAL（通配） -> INV_DEVAL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_DEVAL', NULL, '存货跌价准备审批流程', 'INV_DEVAL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_DEVAL' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_DEVAL');

-- 44. INV_DISPOSAL_APPLY（通配） -> INV_DISPOSAL_APPLY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_DISPOSAL_APPLY', NULL, '存货处置-处置申请审批流程', 'INV_DISPOSAL_APPLY', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_DISPOSAL_APPLY' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_DISPOSAL_APPLY');

-- 45. INV_DISPOSAL_CHECK（通配） -> INV_DISPOSAL_CHECK   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_DISPOSAL_CHECK', NULL, '存货处置-物资状态确认流程', 'INV_DISPOSAL_CHECK', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_DISPOSAL_CHECK' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_DISPOSAL_CHECK');

-- 46. INV_DISPOSAL_EXEC（通配） -> INV_DISPOSAL_EXEC   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_DISPOSAL_EXEC', NULL, '存货处置-处置执行与账务流程', 'INV_DISPOSAL_EXEC', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_DISPOSAL_EXEC' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_DISPOSAL_EXEC');

-- 47. INV_DISPOSE（通配） -> INV_DISPOSE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_DISPOSE', NULL, '存货报废审批流程', 'INV_DISPOSE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_DISPOSE' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_DISPOSE');

-- 48. INV_INTERNAL_SALE（通配） -> INV_INTERNAL_SALE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_INTERNAL_SALE', NULL, '存货内部销售审批流程', 'INV_INTERNAL_SALE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_INTERNAL_SALE' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_INTERNAL_SALE');

-- 49. INV_ISSUE_NONPROJECT（通配） -> INV_ISSUE_NONPROJECT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_ISSUE_NONPROJECT', NULL, '存货领用出库审批流程（项目外物资）', 'INV_ISSUE_NONPROJECT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_ISSUE_NONPROJECT' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_ISSUE_NONPROJECT');

-- 50. INV_ISSUE_PROJECT（通配） -> INV_ISSUE_PROJECT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_ISSUE_PROJECT', NULL, '存货领用出库审批流程（项目内物资）', 'INV_ISSUE_PROJECT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_ISSUE_PROJECT' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_ISSUE_PROJECT');

-- 51. INV_PHYSICAL_CHECK（通配） -> INV_PHYSICAL_CHECK   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'INV_PHYSICAL_CHECK', NULL, '存货盘点差异处理审批流程', 'INV_PHYSICAL_CHECK', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='INV_PHYSICAL_CHECK' AND IFNULL(x.sub_type,'')='' AND x.model_key='INV_PHYSICAL_CHECK');

-- 52. PAY_PLAN_BASE（通配） -> PAY_PLAN_BASE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'PAY_PLAN_BASE', NULL, '基地采购付款计划审批流程', 'PAY_PLAN_BASE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='PAY_PLAN_BASE' AND IFNULL(x.sub_type,'')='' AND x.model_key='PAY_PLAN_BASE');

-- 53. PAY_PLAN_PLATE（通配） -> PAY_PLAN_PLATE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'PAY_PLAN_PLATE', NULL, '板块采购付款计划审批流程', 'PAY_PLAN_PLATE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='PAY_PLAN_PLATE' AND IFNULL(x.sub_type,'')='' AND x.model_key='PAY_PLAN_PLATE');

-- 54. RECEIVABLE（通配） -> RECEIVABLE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'RECEIVABLE', NULL, '应收账款收款审批流程', 'RECEIVABLE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='RECEIVABLE' AND IFNULL(x.sub_type,'')='' AND x.model_key='RECEIVABLE');

-- 55. RECEIVABLE_COLLECT（通配） -> RECEIVABLE_COLLECT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'RECEIVABLE_COLLECT', NULL, '应收账款催收管理审批流程', 'RECEIVABLE_COLLECT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='RECEIVABLE_COLLECT' AND IFNULL(x.sub_type,'')='' AND x.model_key='RECEIVABLE_COLLECT');

-- 56. RECON_ACTIVE（通配） -> RECON_ACTIVE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'RECON_ACTIVE', NULL, '供应商主动对账确认', 'RECON_ACTIVE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='RECON_ACTIVE' AND IFNULL(x.sub_type,'')='' AND x.model_key='RECON_ACTIVE');

-- 57. RECON_PASSIVE（通配） -> RECON_PASSIVE   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'RECON_PASSIVE', NULL, '供应商被动对账确认', 'RECON_PASSIVE', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='RECON_PASSIVE' AND IFNULL(x.sub_type,'')='' AND x.model_key='RECON_PASSIVE');

-- 58. SUPPLIER_ADD（通配） -> SUPPLIER_ADD   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'SUPPLIER_ADD', NULL, '供应商新增准入', 'SUPPLIER_ADD', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='SUPPLIER_ADD' AND IFNULL(x.sub_type,'')='' AND x.model_key='SUPPLIER_ADD');

-- 59. TR_BANK_MASTER（通配） -> TR_BANK_MASTER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_BANK_MASTER', NULL, '资金结算/银行主数据审批流程', 'TR_BANK_MASTER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_BANK_MASTER' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_BANK_MASTER');

-- 60. TR_BANK_RECON（通配） -> TR_BANK_RECON   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_BANK_RECON', NULL, '银行对账审批流程', 'TR_BANK_RECON', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_BANK_RECON' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_BANK_RECON');

-- 61. TR_BILL（通配） -> TR_BILL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_BILL', NULL, '票据结算审批流程(背书/贴现/托收)', 'TR_BILL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_BILL' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_BILL');

-- 62. TR_PAY（通配） -> TR_PAY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_PAY', NULL, '资金付款审批流程(网银)', 'TR_PAY', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_PAY' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_PAY');

-- 63. TR_PAY_FAIL（通配） -> TR_PAY_FAIL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_PAY_FAIL', NULL, '资金付款-支付失败处理审批流程', 'TR_PAY_FAIL', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_PAY_FAIL' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_PAY_FAIL');

-- 64. TR_PLAN（通配） -> TR_PLAN   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_PLAN', NULL, '资金计划编制审批流程', 'TR_PLAN', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_PLAN' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_PLAN');

-- 65. TR_PLAN_ADJUST（通配） -> TR_PLAN_ADJUST   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_PLAN_ADJUST', NULL, '资金计划调整审批流程', 'TR_PLAN_ADJUST', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_PLAN_ADJUST' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_PLAN_ADJUST');

-- 66. TR_RECEIPT（通配） -> TR_RECEIPT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_RECEIPT', NULL, '资金收款确认流程', 'TR_RECEIPT', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_RECEIPT' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_RECEIPT');

-- 67. TR_TRANSFER（通配） -> TR_TRANSFER   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_TRANSFER', NULL, '资金上存/调拨审批流程', 'TR_TRANSFER', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_TRANSFER' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_TRANSFER');

-- 68. TR_UPLOAD（通配） -> TR_UPLOAD   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'TR_UPLOAD', NULL, '资金上存审批流程', 'TR_UPLOAD', 10, 'NORMAL', 'V1.4.38批次3路由合一镜像seed', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='TR_UPLOAD' AND IFNULL(x.sub_type,'')='' AND x.model_key='TR_UPLOAD');

-- 2) 复核 A：镜像行是否与推导清单一一对应（期望 expected=actual、missing=extra=0）
SELECT (SELECT COUNT(*) FROM (
SELECT 'AR_CLAIM' biz_type, '' sub_type, 'AR_CLAIM' model_key UNION ALL
SELECT 'AR_RECON' biz_type, '' sub_type, 'AR_RECON' model_key UNION ALL
SELECT 'AR_REPORT' biz_type, '' sub_type, 'AR_REPORT' model_key UNION ALL
SELECT 'BAD_DEBT_CONFIRM' biz_type, '' sub_type, 'BAD_DEBT_CONFIRM' model_key UNION ALL
SELECT 'BAD_DEBT_PROV' biz_type, '' sub_type, 'BAD_DEBT_PROV' model_key UNION ALL
SELECT 'BG_SCHEME' biz_type, '' sub_type, 'BG_SCHEME' model_key UNION ALL
SELECT 'CONTRACT_APPROVE' biz_type, '' sub_type, 'CONTRACT' model_key UNION ALL
SELECT 'CUSTOMER_MAINT' biz_type, '' sub_type, 'CUSTOMER_MAINT' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'FA_ADD' biz_type, '' sub_type, 'FA_ADD' model_key UNION ALL
SELECT 'FA_ALIENATE' biz_type, '' sub_type, 'FA_ALIENATE' model_key UNION ALL
SELECT 'FA_CER' biz_type, '' sub_type, 'FA_CER' model_key UNION ALL
SELECT 'FA_CHECK_DIFF' biz_type, '' sub_type, 'FA_CHECK_DIFF' model_key UNION ALL
SELECT 'FA_CIP_TO_ASSET' biz_type, '' sub_type, 'FA_CIP_TO_ASSET' model_key UNION ALL
SELECT 'FA_DEPRECIATION' biz_type, '' sub_type, 'FA_DEPRECIATION' model_key UNION ALL
SELECT 'FA_DISPOSE' biz_type, '' sub_type, 'FA_DISPOSE' model_key UNION ALL
SELECT 'FA_IMPAIRMENT' biz_type, '' sub_type, 'FA_IMPAIRMENT' model_key UNION ALL
SELECT 'FA_TRANSFER' biz_type, '' sub_type, 'FA_TRANSFER' model_key UNION ALL
SELECT 'FA_TRANSFER_CROSS' biz_type, '' sub_type, 'FA_TRANSFER_CROSS' model_key UNION ALL
SELECT 'FA_VALUE_ADJUST' biz_type, '' sub_type, 'FA_VALUE_ADJUST' model_key UNION ALL
SELECT 'FM_ARCHIVE' biz_type, '' sub_type, 'FM_ARCHIVE' model_key UNION ALL
SELECT 'FM_BORROW' biz_type, '' sub_type, 'FM_BORROW' model_key UNION ALL
SELECT 'FM_DESTROY' biz_type, '' sub_type, 'FM_DESTROY' model_key UNION ALL
SELECT 'FM_DOC_FLOW' biz_type, '' sub_type, 'FM_DOC_FLOW' model_key UNION ALL
SELECT 'GL_ACCRUAL' biz_type, '' sub_type, 'GL_ACCRUAL' model_key UNION ALL
SELECT 'GL_DEFER' biz_type, '' sub_type, 'GL_DEFER' model_key UNION ALL
SELECT 'GL_MANUAL_VOUCHER' biz_type, '' sub_type, 'GL_MANUAL_VOUCHER' model_key UNION ALL
SELECT 'GL_MERGE' biz_type, '' sub_type, 'GL_MERGE' model_key UNION ALL
SELECT 'GL_MONTH_CLOSE' biz_type, '' sub_type, 'GL_MONTH_CLOSE' model_key UNION ALL
SELECT 'GL_OFFSET' biz_type, '' sub_type, 'GL_OFFSET' model_key UNION ALL
SELECT 'GL_OTHER_RECEIPT' biz_type, '' sub_type, 'GL_OTHER_RECEIPT' model_key UNION ALL
SELECT 'GL_SALARY_PAY' biz_type, '' sub_type, 'GL_SALARY_PAY' model_key UNION ALL
SELECT 'GL_SALARY_PROV' biz_type, '' sub_type, 'GL_SALARY_PROV' model_key UNION ALL
SELECT 'GL_SHARED_MASTER' biz_type, '' sub_type, 'GL_SHARED_MASTER' model_key UNION ALL
SELECT 'GL_TAX_PAY' biz_type, '' sub_type, 'GL_TAX_PAY' model_key UNION ALL
SELECT 'GL_TAX_PROV' biz_type, '' sub_type, 'GL_TAX_PROV' model_key UNION ALL
SELECT 'GL_TAX_REFUND' biz_type, '' sub_type, 'GL_TAX_REFUND' model_key UNION ALL
SELECT 'INVOICE_ISSUE' biz_type, '' sub_type, 'INVOICE_ISSUE' model_key UNION ALL
SELECT 'INVOICE_MATCH' biz_type, '' sub_type, 'INVOICE_MATCH' model_key UNION ALL
SELECT 'INVOICE_MATCH_MANUAL' biz_type, '' sub_type, 'INVOICE_MATCH_MANUAL' model_key UNION ALL
SELECT 'INV_DEVAL' biz_type, '' sub_type, 'INV_DEVAL' model_key UNION ALL
SELECT 'INV_DISPOSAL_APPLY' biz_type, '' sub_type, 'INV_DISPOSAL_APPLY' model_key UNION ALL
SELECT 'INV_DISPOSAL_CHECK' biz_type, '' sub_type, 'INV_DISPOSAL_CHECK' model_key UNION ALL
SELECT 'INV_DISPOSAL_EXEC' biz_type, '' sub_type, 'INV_DISPOSAL_EXEC' model_key UNION ALL
SELECT 'INV_DISPOSE' biz_type, '' sub_type, 'INV_DISPOSE' model_key UNION ALL
SELECT 'INV_INTERNAL_SALE' biz_type, '' sub_type, 'INV_INTERNAL_SALE' model_key UNION ALL
SELECT 'INV_ISSUE_NONPROJECT' biz_type, '' sub_type, 'INV_ISSUE_NONPROJECT' model_key UNION ALL
SELECT 'INV_ISSUE_PROJECT' biz_type, '' sub_type, 'INV_ISSUE_PROJECT' model_key UNION ALL
SELECT 'INV_PHYSICAL_CHECK' biz_type, '' sub_type, 'INV_PHYSICAL_CHECK' model_key UNION ALL
SELECT 'PAY_PLAN_BASE' biz_type, '' sub_type, 'PAY_PLAN_BASE' model_key UNION ALL
SELECT 'PAY_PLAN_PLATE' biz_type, '' sub_type, 'PAY_PLAN_PLATE' model_key UNION ALL
SELECT 'RECEIVABLE' biz_type, '' sub_type, 'RECEIVABLE' model_key UNION ALL
SELECT 'RECEIVABLE_COLLECT' biz_type, '' sub_type, 'RECEIVABLE_COLLECT' model_key UNION ALL
SELECT 'RECON_ACTIVE' biz_type, '' sub_type, 'RECON_ACTIVE' model_key UNION ALL
SELECT 'RECON_PASSIVE' biz_type, '' sub_type, 'RECON_PASSIVE' model_key UNION ALL
SELECT 'SUPPLIER_ADD' biz_type, '' sub_type, 'SUPPLIER_ADD' model_key UNION ALL
SELECT 'TR_BANK_MASTER' biz_type, '' sub_type, 'TR_BANK_MASTER' model_key UNION ALL
SELECT 'TR_BANK_RECON' biz_type, '' sub_type, 'TR_BANK_RECON' model_key UNION ALL
SELECT 'TR_BILL' biz_type, '' sub_type, 'TR_BILL' model_key UNION ALL
SELECT 'TR_PAY' biz_type, '' sub_type, 'TR_PAY' model_key UNION ALL
SELECT 'TR_PAY_FAIL' biz_type, '' sub_type, 'TR_PAY_FAIL' model_key UNION ALL
SELECT 'TR_PLAN' biz_type, '' sub_type, 'TR_PLAN' model_key UNION ALL
SELECT 'TR_PLAN_ADJUST' biz_type, '' sub_type, 'TR_PLAN_ADJUST' model_key UNION ALL
SELECT 'TR_RECEIPT' biz_type, '' sub_type, 'TR_RECEIPT' model_key UNION ALL
SELECT 'TR_TRANSFER' biz_type, '' sub_type, 'TR_TRANSFER' model_key UNION ALL
SELECT 'TR_UPLOAD' biz_type, '' sub_type, 'TR_UPLOAD' model_key
) e) AS expected,
       (SELECT COUNT(*) FROM wf_process_config c JOIN (
SELECT 'AR_CLAIM' biz_type, '' sub_type, 'AR_CLAIM' model_key UNION ALL
SELECT 'AR_RECON' biz_type, '' sub_type, 'AR_RECON' model_key UNION ALL
SELECT 'AR_REPORT' biz_type, '' sub_type, 'AR_REPORT' model_key UNION ALL
SELECT 'BAD_DEBT_CONFIRM' biz_type, '' sub_type, 'BAD_DEBT_CONFIRM' model_key UNION ALL
SELECT 'BAD_DEBT_PROV' biz_type, '' sub_type, 'BAD_DEBT_PROV' model_key UNION ALL
SELECT 'BG_SCHEME' biz_type, '' sub_type, 'BG_SCHEME' model_key UNION ALL
SELECT 'CONTRACT_APPROVE' biz_type, '' sub_type, 'CONTRACT' model_key UNION ALL
SELECT 'CUSTOMER_MAINT' biz_type, '' sub_type, 'CUSTOMER_MAINT' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'FA_ADD' biz_type, '' sub_type, 'FA_ADD' model_key UNION ALL
SELECT 'FA_ALIENATE' biz_type, '' sub_type, 'FA_ALIENATE' model_key UNION ALL
SELECT 'FA_CER' biz_type, '' sub_type, 'FA_CER' model_key UNION ALL
SELECT 'FA_CHECK_DIFF' biz_type, '' sub_type, 'FA_CHECK_DIFF' model_key UNION ALL
SELECT 'FA_CIP_TO_ASSET' biz_type, '' sub_type, 'FA_CIP_TO_ASSET' model_key UNION ALL
SELECT 'FA_DEPRECIATION' biz_type, '' sub_type, 'FA_DEPRECIATION' model_key UNION ALL
SELECT 'FA_DISPOSE' biz_type, '' sub_type, 'FA_DISPOSE' model_key UNION ALL
SELECT 'FA_IMPAIRMENT' biz_type, '' sub_type, 'FA_IMPAIRMENT' model_key UNION ALL
SELECT 'FA_TRANSFER' biz_type, '' sub_type, 'FA_TRANSFER' model_key UNION ALL
SELECT 'FA_TRANSFER_CROSS' biz_type, '' sub_type, 'FA_TRANSFER_CROSS' model_key UNION ALL
SELECT 'FA_VALUE_ADJUST' biz_type, '' sub_type, 'FA_VALUE_ADJUST' model_key UNION ALL
SELECT 'FM_ARCHIVE' biz_type, '' sub_type, 'FM_ARCHIVE' model_key UNION ALL
SELECT 'FM_BORROW' biz_type, '' sub_type, 'FM_BORROW' model_key UNION ALL
SELECT 'FM_DESTROY' biz_type, '' sub_type, 'FM_DESTROY' model_key UNION ALL
SELECT 'FM_DOC_FLOW' biz_type, '' sub_type, 'FM_DOC_FLOW' model_key UNION ALL
SELECT 'GL_ACCRUAL' biz_type, '' sub_type, 'GL_ACCRUAL' model_key UNION ALL
SELECT 'GL_DEFER' biz_type, '' sub_type, 'GL_DEFER' model_key UNION ALL
SELECT 'GL_MANUAL_VOUCHER' biz_type, '' sub_type, 'GL_MANUAL_VOUCHER' model_key UNION ALL
SELECT 'GL_MERGE' biz_type, '' sub_type, 'GL_MERGE' model_key UNION ALL
SELECT 'GL_MONTH_CLOSE' biz_type, '' sub_type, 'GL_MONTH_CLOSE' model_key UNION ALL
SELECT 'GL_OFFSET' biz_type, '' sub_type, 'GL_OFFSET' model_key UNION ALL
SELECT 'GL_OTHER_RECEIPT' biz_type, '' sub_type, 'GL_OTHER_RECEIPT' model_key UNION ALL
SELECT 'GL_SALARY_PAY' biz_type, '' sub_type, 'GL_SALARY_PAY' model_key UNION ALL
SELECT 'GL_SALARY_PROV' biz_type, '' sub_type, 'GL_SALARY_PROV' model_key UNION ALL
SELECT 'GL_SHARED_MASTER' biz_type, '' sub_type, 'GL_SHARED_MASTER' model_key UNION ALL
SELECT 'GL_TAX_PAY' biz_type, '' sub_type, 'GL_TAX_PAY' model_key UNION ALL
SELECT 'GL_TAX_PROV' biz_type, '' sub_type, 'GL_TAX_PROV' model_key UNION ALL
SELECT 'GL_TAX_REFUND' biz_type, '' sub_type, 'GL_TAX_REFUND' model_key UNION ALL
SELECT 'INVOICE_ISSUE' biz_type, '' sub_type, 'INVOICE_ISSUE' model_key UNION ALL
SELECT 'INVOICE_MATCH' biz_type, '' sub_type, 'INVOICE_MATCH' model_key UNION ALL
SELECT 'INVOICE_MATCH_MANUAL' biz_type, '' sub_type, 'INVOICE_MATCH_MANUAL' model_key UNION ALL
SELECT 'INV_DEVAL' biz_type, '' sub_type, 'INV_DEVAL' model_key UNION ALL
SELECT 'INV_DISPOSAL_APPLY' biz_type, '' sub_type, 'INV_DISPOSAL_APPLY' model_key UNION ALL
SELECT 'INV_DISPOSAL_CHECK' biz_type, '' sub_type, 'INV_DISPOSAL_CHECK' model_key UNION ALL
SELECT 'INV_DISPOSAL_EXEC' biz_type, '' sub_type, 'INV_DISPOSAL_EXEC' model_key UNION ALL
SELECT 'INV_DISPOSE' biz_type, '' sub_type, 'INV_DISPOSE' model_key UNION ALL
SELECT 'INV_INTERNAL_SALE' biz_type, '' sub_type, 'INV_INTERNAL_SALE' model_key UNION ALL
SELECT 'INV_ISSUE_NONPROJECT' biz_type, '' sub_type, 'INV_ISSUE_NONPROJECT' model_key UNION ALL
SELECT 'INV_ISSUE_PROJECT' biz_type, '' sub_type, 'INV_ISSUE_PROJECT' model_key UNION ALL
SELECT 'INV_PHYSICAL_CHECK' biz_type, '' sub_type, 'INV_PHYSICAL_CHECK' model_key UNION ALL
SELECT 'PAY_PLAN_BASE' biz_type, '' sub_type, 'PAY_PLAN_BASE' model_key UNION ALL
SELECT 'PAY_PLAN_PLATE' biz_type, '' sub_type, 'PAY_PLAN_PLATE' model_key UNION ALL
SELECT 'RECEIVABLE' biz_type, '' sub_type, 'RECEIVABLE' model_key UNION ALL
SELECT 'RECEIVABLE_COLLECT' biz_type, '' sub_type, 'RECEIVABLE_COLLECT' model_key UNION ALL
SELECT 'RECON_ACTIVE' biz_type, '' sub_type, 'RECON_ACTIVE' model_key UNION ALL
SELECT 'RECON_PASSIVE' biz_type, '' sub_type, 'RECON_PASSIVE' model_key UNION ALL
SELECT 'SUPPLIER_ADD' biz_type, '' sub_type, 'SUPPLIER_ADD' model_key UNION ALL
SELECT 'TR_BANK_MASTER' biz_type, '' sub_type, 'TR_BANK_MASTER' model_key UNION ALL
SELECT 'TR_BANK_RECON' biz_type, '' sub_type, 'TR_BANK_RECON' model_key UNION ALL
SELECT 'TR_BILL' biz_type, '' sub_type, 'TR_BILL' model_key UNION ALL
SELECT 'TR_PAY' biz_type, '' sub_type, 'TR_PAY' model_key UNION ALL
SELECT 'TR_PAY_FAIL' biz_type, '' sub_type, 'TR_PAY_FAIL' model_key UNION ALL
SELECT 'TR_PLAN' biz_type, '' sub_type, 'TR_PLAN' model_key UNION ALL
SELECT 'TR_PLAN_ADJUST' biz_type, '' sub_type, 'TR_PLAN_ADJUST' model_key UNION ALL
SELECT 'TR_RECEIPT' biz_type, '' sub_type, 'TR_RECEIPT' model_key UNION ALL
SELECT 'TR_TRANSFER' biz_type, '' sub_type, 'TR_TRANSFER' model_key UNION ALL
SELECT 'TR_UPLOAD' biz_type, '' sub_type, 'TR_UPLOAD' model_key
) e ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.38批次3路由合一镜像seed') AS actual,
       (SELECT COUNT(*) FROM (
SELECT 'AR_CLAIM' biz_type, '' sub_type, 'AR_CLAIM' model_key UNION ALL
SELECT 'AR_RECON' biz_type, '' sub_type, 'AR_RECON' model_key UNION ALL
SELECT 'AR_REPORT' biz_type, '' sub_type, 'AR_REPORT' model_key UNION ALL
SELECT 'BAD_DEBT_CONFIRM' biz_type, '' sub_type, 'BAD_DEBT_CONFIRM' model_key UNION ALL
SELECT 'BAD_DEBT_PROV' biz_type, '' sub_type, 'BAD_DEBT_PROV' model_key UNION ALL
SELECT 'BG_SCHEME' biz_type, '' sub_type, 'BG_SCHEME' model_key UNION ALL
SELECT 'CONTRACT_APPROVE' biz_type, '' sub_type, 'CONTRACT' model_key UNION ALL
SELECT 'CUSTOMER_MAINT' biz_type, '' sub_type, 'CUSTOMER_MAINT' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'FA_ADD' biz_type, '' sub_type, 'FA_ADD' model_key UNION ALL
SELECT 'FA_ALIENATE' biz_type, '' sub_type, 'FA_ALIENATE' model_key UNION ALL
SELECT 'FA_CER' biz_type, '' sub_type, 'FA_CER' model_key UNION ALL
SELECT 'FA_CHECK_DIFF' biz_type, '' sub_type, 'FA_CHECK_DIFF' model_key UNION ALL
SELECT 'FA_CIP_TO_ASSET' biz_type, '' sub_type, 'FA_CIP_TO_ASSET' model_key UNION ALL
SELECT 'FA_DEPRECIATION' biz_type, '' sub_type, 'FA_DEPRECIATION' model_key UNION ALL
SELECT 'FA_DISPOSE' biz_type, '' sub_type, 'FA_DISPOSE' model_key UNION ALL
SELECT 'FA_IMPAIRMENT' biz_type, '' sub_type, 'FA_IMPAIRMENT' model_key UNION ALL
SELECT 'FA_TRANSFER' biz_type, '' sub_type, 'FA_TRANSFER' model_key UNION ALL
SELECT 'FA_TRANSFER_CROSS' biz_type, '' sub_type, 'FA_TRANSFER_CROSS' model_key UNION ALL
SELECT 'FA_VALUE_ADJUST' biz_type, '' sub_type, 'FA_VALUE_ADJUST' model_key UNION ALL
SELECT 'FM_ARCHIVE' biz_type, '' sub_type, 'FM_ARCHIVE' model_key UNION ALL
SELECT 'FM_BORROW' biz_type, '' sub_type, 'FM_BORROW' model_key UNION ALL
SELECT 'FM_DESTROY' biz_type, '' sub_type, 'FM_DESTROY' model_key UNION ALL
SELECT 'FM_DOC_FLOW' biz_type, '' sub_type, 'FM_DOC_FLOW' model_key UNION ALL
SELECT 'GL_ACCRUAL' biz_type, '' sub_type, 'GL_ACCRUAL' model_key UNION ALL
SELECT 'GL_DEFER' biz_type, '' sub_type, 'GL_DEFER' model_key UNION ALL
SELECT 'GL_MANUAL_VOUCHER' biz_type, '' sub_type, 'GL_MANUAL_VOUCHER' model_key UNION ALL
SELECT 'GL_MERGE' biz_type, '' sub_type, 'GL_MERGE' model_key UNION ALL
SELECT 'GL_MONTH_CLOSE' biz_type, '' sub_type, 'GL_MONTH_CLOSE' model_key UNION ALL
SELECT 'GL_OFFSET' biz_type, '' sub_type, 'GL_OFFSET' model_key UNION ALL
SELECT 'GL_OTHER_RECEIPT' biz_type, '' sub_type, 'GL_OTHER_RECEIPT' model_key UNION ALL
SELECT 'GL_SALARY_PAY' biz_type, '' sub_type, 'GL_SALARY_PAY' model_key UNION ALL
SELECT 'GL_SALARY_PROV' biz_type, '' sub_type, 'GL_SALARY_PROV' model_key UNION ALL
SELECT 'GL_SHARED_MASTER' biz_type, '' sub_type, 'GL_SHARED_MASTER' model_key UNION ALL
SELECT 'GL_TAX_PAY' biz_type, '' sub_type, 'GL_TAX_PAY' model_key UNION ALL
SELECT 'GL_TAX_PROV' biz_type, '' sub_type, 'GL_TAX_PROV' model_key UNION ALL
SELECT 'GL_TAX_REFUND' biz_type, '' sub_type, 'GL_TAX_REFUND' model_key UNION ALL
SELECT 'INVOICE_ISSUE' biz_type, '' sub_type, 'INVOICE_ISSUE' model_key UNION ALL
SELECT 'INVOICE_MATCH' biz_type, '' sub_type, 'INVOICE_MATCH' model_key UNION ALL
SELECT 'INVOICE_MATCH_MANUAL' biz_type, '' sub_type, 'INVOICE_MATCH_MANUAL' model_key UNION ALL
SELECT 'INV_DEVAL' biz_type, '' sub_type, 'INV_DEVAL' model_key UNION ALL
SELECT 'INV_DISPOSAL_APPLY' biz_type, '' sub_type, 'INV_DISPOSAL_APPLY' model_key UNION ALL
SELECT 'INV_DISPOSAL_CHECK' biz_type, '' sub_type, 'INV_DISPOSAL_CHECK' model_key UNION ALL
SELECT 'INV_DISPOSAL_EXEC' biz_type, '' sub_type, 'INV_DISPOSAL_EXEC' model_key UNION ALL
SELECT 'INV_DISPOSE' biz_type, '' sub_type, 'INV_DISPOSE' model_key UNION ALL
SELECT 'INV_INTERNAL_SALE' biz_type, '' sub_type, 'INV_INTERNAL_SALE' model_key UNION ALL
SELECT 'INV_ISSUE_NONPROJECT' biz_type, '' sub_type, 'INV_ISSUE_NONPROJECT' model_key UNION ALL
SELECT 'INV_ISSUE_PROJECT' biz_type, '' sub_type, 'INV_ISSUE_PROJECT' model_key UNION ALL
SELECT 'INV_PHYSICAL_CHECK' biz_type, '' sub_type, 'INV_PHYSICAL_CHECK' model_key UNION ALL
SELECT 'PAY_PLAN_BASE' biz_type, '' sub_type, 'PAY_PLAN_BASE' model_key UNION ALL
SELECT 'PAY_PLAN_PLATE' biz_type, '' sub_type, 'PAY_PLAN_PLATE' model_key UNION ALL
SELECT 'RECEIVABLE' biz_type, '' sub_type, 'RECEIVABLE' model_key UNION ALL
SELECT 'RECEIVABLE_COLLECT' biz_type, '' sub_type, 'RECEIVABLE_COLLECT' model_key UNION ALL
SELECT 'RECON_ACTIVE' biz_type, '' sub_type, 'RECON_ACTIVE' model_key UNION ALL
SELECT 'RECON_PASSIVE' biz_type, '' sub_type, 'RECON_PASSIVE' model_key UNION ALL
SELECT 'SUPPLIER_ADD' biz_type, '' sub_type, 'SUPPLIER_ADD' model_key UNION ALL
SELECT 'TR_BANK_MASTER' biz_type, '' sub_type, 'TR_BANK_MASTER' model_key UNION ALL
SELECT 'TR_BANK_RECON' biz_type, '' sub_type, 'TR_BANK_RECON' model_key UNION ALL
SELECT 'TR_BILL' biz_type, '' sub_type, 'TR_BILL' model_key UNION ALL
SELECT 'TR_PAY' biz_type, '' sub_type, 'TR_PAY' model_key UNION ALL
SELECT 'TR_PAY_FAIL' biz_type, '' sub_type, 'TR_PAY_FAIL' model_key UNION ALL
SELECT 'TR_PLAN' biz_type, '' sub_type, 'TR_PLAN' model_key UNION ALL
SELECT 'TR_PLAN_ADJUST' biz_type, '' sub_type, 'TR_PLAN_ADJUST' model_key UNION ALL
SELECT 'TR_RECEIPT' biz_type, '' sub_type, 'TR_RECEIPT' model_key UNION ALL
SELECT 'TR_TRANSFER' biz_type, '' sub_type, 'TR_TRANSFER' model_key UNION ALL
SELECT 'TR_UPLOAD' biz_type, '' sub_type, 'TR_UPLOAD' model_key
) e LEFT JOIN wf_process_config c ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key AND c.del_flag=0 WHERE c.id IS NULL) AS missing,
       (SELECT COUNT(*) FROM wf_process_config c LEFT JOIN (
SELECT 'AR_CLAIM' biz_type, '' sub_type, 'AR_CLAIM' model_key UNION ALL
SELECT 'AR_RECON' biz_type, '' sub_type, 'AR_RECON' model_key UNION ALL
SELECT 'AR_REPORT' biz_type, '' sub_type, 'AR_REPORT' model_key UNION ALL
SELECT 'BAD_DEBT_CONFIRM' biz_type, '' sub_type, 'BAD_DEBT_CONFIRM' model_key UNION ALL
SELECT 'BAD_DEBT_PROV' biz_type, '' sub_type, 'BAD_DEBT_PROV' model_key UNION ALL
SELECT 'BG_SCHEME' biz_type, '' sub_type, 'BG_SCHEME' model_key UNION ALL
SELECT 'CONTRACT_APPROVE' biz_type, '' sub_type, 'CONTRACT' model_key UNION ALL
SELECT 'CUSTOMER_MAINT' biz_type, '' sub_type, 'CUSTOMER_MAINT' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'EXP_APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'FA_ADD' biz_type, '' sub_type, 'FA_ADD' model_key UNION ALL
SELECT 'FA_ALIENATE' biz_type, '' sub_type, 'FA_ALIENATE' model_key UNION ALL
SELECT 'FA_CER' biz_type, '' sub_type, 'FA_CER' model_key UNION ALL
SELECT 'FA_CHECK_DIFF' biz_type, '' sub_type, 'FA_CHECK_DIFF' model_key UNION ALL
SELECT 'FA_CIP_TO_ASSET' biz_type, '' sub_type, 'FA_CIP_TO_ASSET' model_key UNION ALL
SELECT 'FA_DEPRECIATION' biz_type, '' sub_type, 'FA_DEPRECIATION' model_key UNION ALL
SELECT 'FA_DISPOSE' biz_type, '' sub_type, 'FA_DISPOSE' model_key UNION ALL
SELECT 'FA_IMPAIRMENT' biz_type, '' sub_type, 'FA_IMPAIRMENT' model_key UNION ALL
SELECT 'FA_TRANSFER' biz_type, '' sub_type, 'FA_TRANSFER' model_key UNION ALL
SELECT 'FA_TRANSFER_CROSS' biz_type, '' sub_type, 'FA_TRANSFER_CROSS' model_key UNION ALL
SELECT 'FA_VALUE_ADJUST' biz_type, '' sub_type, 'FA_VALUE_ADJUST' model_key UNION ALL
SELECT 'FM_ARCHIVE' biz_type, '' sub_type, 'FM_ARCHIVE' model_key UNION ALL
SELECT 'FM_BORROW' biz_type, '' sub_type, 'FM_BORROW' model_key UNION ALL
SELECT 'FM_DESTROY' biz_type, '' sub_type, 'FM_DESTROY' model_key UNION ALL
SELECT 'FM_DOC_FLOW' biz_type, '' sub_type, 'FM_DOC_FLOW' model_key UNION ALL
SELECT 'GL_ACCRUAL' biz_type, '' sub_type, 'GL_ACCRUAL' model_key UNION ALL
SELECT 'GL_DEFER' biz_type, '' sub_type, 'GL_DEFER' model_key UNION ALL
SELECT 'GL_MANUAL_VOUCHER' biz_type, '' sub_type, 'GL_MANUAL_VOUCHER' model_key UNION ALL
SELECT 'GL_MERGE' biz_type, '' sub_type, 'GL_MERGE' model_key UNION ALL
SELECT 'GL_MONTH_CLOSE' biz_type, '' sub_type, 'GL_MONTH_CLOSE' model_key UNION ALL
SELECT 'GL_OFFSET' biz_type, '' sub_type, 'GL_OFFSET' model_key UNION ALL
SELECT 'GL_OTHER_RECEIPT' biz_type, '' sub_type, 'GL_OTHER_RECEIPT' model_key UNION ALL
SELECT 'GL_SALARY_PAY' biz_type, '' sub_type, 'GL_SALARY_PAY' model_key UNION ALL
SELECT 'GL_SALARY_PROV' biz_type, '' sub_type, 'GL_SALARY_PROV' model_key UNION ALL
SELECT 'GL_SHARED_MASTER' biz_type, '' sub_type, 'GL_SHARED_MASTER' model_key UNION ALL
SELECT 'GL_TAX_PAY' biz_type, '' sub_type, 'GL_TAX_PAY' model_key UNION ALL
SELECT 'GL_TAX_PROV' biz_type, '' sub_type, 'GL_TAX_PROV' model_key UNION ALL
SELECT 'GL_TAX_REFUND' biz_type, '' sub_type, 'GL_TAX_REFUND' model_key UNION ALL
SELECT 'INVOICE_ISSUE' biz_type, '' sub_type, 'INVOICE_ISSUE' model_key UNION ALL
SELECT 'INVOICE_MATCH' biz_type, '' sub_type, 'INVOICE_MATCH' model_key UNION ALL
SELECT 'INVOICE_MATCH_MANUAL' biz_type, '' sub_type, 'INVOICE_MATCH_MANUAL' model_key UNION ALL
SELECT 'INV_DEVAL' biz_type, '' sub_type, 'INV_DEVAL' model_key UNION ALL
SELECT 'INV_DISPOSAL_APPLY' biz_type, '' sub_type, 'INV_DISPOSAL_APPLY' model_key UNION ALL
SELECT 'INV_DISPOSAL_CHECK' biz_type, '' sub_type, 'INV_DISPOSAL_CHECK' model_key UNION ALL
SELECT 'INV_DISPOSAL_EXEC' biz_type, '' sub_type, 'INV_DISPOSAL_EXEC' model_key UNION ALL
SELECT 'INV_DISPOSE' biz_type, '' sub_type, 'INV_DISPOSE' model_key UNION ALL
SELECT 'INV_INTERNAL_SALE' biz_type, '' sub_type, 'INV_INTERNAL_SALE' model_key UNION ALL
SELECT 'INV_ISSUE_NONPROJECT' biz_type, '' sub_type, 'INV_ISSUE_NONPROJECT' model_key UNION ALL
SELECT 'INV_ISSUE_PROJECT' biz_type, '' sub_type, 'INV_ISSUE_PROJECT' model_key UNION ALL
SELECT 'INV_PHYSICAL_CHECK' biz_type, '' sub_type, 'INV_PHYSICAL_CHECK' model_key UNION ALL
SELECT 'PAY_PLAN_BASE' biz_type, '' sub_type, 'PAY_PLAN_BASE' model_key UNION ALL
SELECT 'PAY_PLAN_PLATE' biz_type, '' sub_type, 'PAY_PLAN_PLATE' model_key UNION ALL
SELECT 'RECEIVABLE' biz_type, '' sub_type, 'RECEIVABLE' model_key UNION ALL
SELECT 'RECEIVABLE_COLLECT' biz_type, '' sub_type, 'RECEIVABLE_COLLECT' model_key UNION ALL
SELECT 'RECON_ACTIVE' biz_type, '' sub_type, 'RECON_ACTIVE' model_key UNION ALL
SELECT 'RECON_PASSIVE' biz_type, '' sub_type, 'RECON_PASSIVE' model_key UNION ALL
SELECT 'SUPPLIER_ADD' biz_type, '' sub_type, 'SUPPLIER_ADD' model_key UNION ALL
SELECT 'TR_BANK_MASTER' biz_type, '' sub_type, 'TR_BANK_MASTER' model_key UNION ALL
SELECT 'TR_BANK_RECON' biz_type, '' sub_type, 'TR_BANK_RECON' model_key UNION ALL
SELECT 'TR_BILL' biz_type, '' sub_type, 'TR_BILL' model_key UNION ALL
SELECT 'TR_PAY' biz_type, '' sub_type, 'TR_PAY' model_key UNION ALL
SELECT 'TR_PAY_FAIL' biz_type, '' sub_type, 'TR_PAY_FAIL' model_key UNION ALL
SELECT 'TR_PLAN' biz_type, '' sub_type, 'TR_PLAN' model_key UNION ALL
SELECT 'TR_PLAN_ADJUST' biz_type, '' sub_type, 'TR_PLAN_ADJUST' model_key UNION ALL
SELECT 'TR_RECEIPT' biz_type, '' sub_type, 'TR_RECEIPT' model_key UNION ALL
SELECT 'TR_TRANSFER' biz_type, '' sub_type, 'TR_TRANSFER' model_key UNION ALL
SELECT 'TR_UPLOAD' biz_type, '' sub_type, 'TR_UPLOAD' model_key
) e ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.38批次3路由合一镜像seed' AND e.biz_type IS NULL) AS extra;

-- 3) 复核 B：一个 bizType 只应有一条通配镜像行（期望空集）
SELECT biz_type, COUNT(*) c FROM wf_process_config WHERE del_flag=0 AND IFNULL(sub_type,'')='' GROUP BY biz_type HAVING c>1;

-- 4) 复核 C：镜像行的目标 key 必须全部已部署（期望 bad_target=0）
SELECT COUNT(*) AS bad_target FROM wf_process_config c LEFT JOIN (SELECT DISTINCT KEY_ FROM ACT_RE_PROCDEF) p ON p.KEY_=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.38批次3路由合一镜像seed' AND p.KEY_ IS NULL;

-- 5) 最终状态快照
SELECT biz_type, IFNULL(sub_type,'*') sub, model_key, priority, status, remark FROM wf_process_config WHERE del_flag=0 ORDER BY biz_type, priority DESC, id;
