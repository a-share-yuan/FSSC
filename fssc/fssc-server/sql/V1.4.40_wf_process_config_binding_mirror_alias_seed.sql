-- V1.4.40  批次3（代码轨）round 2 · 单据类型流程绑定"路由合一"：把内置映射镜像进 wf_process_config
-- 批次归属：第②批（别名拼写）。V1.4.38 第①批的跳过理由"别名拼写无代码调用点"经取证后作废——
--   fssc-web/src/views/common/GenericList.vue:315 的"手工发起"弹窗下拉允许选 CLAIM/APPLY/LOAN/PAY/REPAY/CONTRACT/OTHER，
--   且 filterable + allow-create，别名拼写是 UI 可达的真实入口，不是死代码。
--   本批把这 8 个别名单元格（5 通配 + 3 子类型精确）镜像进表，使 UI 可达单元格也走"表→引擎"单一真源。
--   同 priority 时子类型精确行优先于通配行：WfProcessConfigService.java:82-83 的比较器给精确行 key=0、通配行 key=1，
--   List.sort 是稳定排序、保留 SQL 的 priority DESC，故 CLAIM/TRAVEL 命中 CLAIM_TRAVEL 而不会落到 CLAIM/*。
--   全库 wf_model 无一行 org_scope<>ALL、无一行 exclude_org_ids（已查＝0 行），resolveModelKey 的 orgScopeMatches
--   fail-closed 分支在本账套恒不触发 ⇒ 镜像行对任意 companyId 都与内置映射同落点，不限于检查用的公司31。
-- 生成方式：bpmn-repo/drafts/batch3/r2-gen-seed-sql.js 从 r2-seed-rows-alias.tsv 机械推导（该 tsv 由 r2-routing-matrix.js
--   对 resolveProcessKey 三级链路【绑定表 → 内置 switch → 部署存在性 → expenseClaim】的 JS 1:1 复算得出，非手写）。
-- 判据（每条镜像行都必须同时满足，否则不生成）：
--   ① 改后落点＝改前落点：镜像行的 model_key 就是今天 resolveProcessKey 实际返回的 key（纯搬家，零路由变更）；
--   ② 目标 key 已部署：绑定表这条路径原本不查 ACT_RE_PROCDEF 存在性（内置映射查），指向未部署 key 会把
--      "静默落错流程"升级成"发起直接报错"，故只 seed 已部署 key；
--   ③ 有可达性证据：bizType 出现在 wfEngineFacade.start(...) 的字面量或 flowKey 推导值里，或历史实例里；
--   ④ 不加约束：company_id/dept_id/amount_min/amount_max 全 NULL、status=NORMAL、priority=10、sub_type=NULL(通配)，
--      与现有行同一规则，保证任何金额/任何公司都命中，等价于今天"表未命中→内置映射"的结果；
--   ⑤ 已有绑定行的 bizType 一律不动（生成本文件时库内 75 行所属 bizType 均不在本 seed 范围内）。
-- 不迁移项（有意留空，见 r2-routing-matrix.log §C）：
--   ① 断链 bizType（PURCHASE_ORDER_REVIEW / SUPPLIER_PAY / ARCHIVE_RECON / TR_SEAL_CHANGE / BG_ADJUST）：同 V1.4.38，
--     seed 会让 start() 直接抛错或替业务挑流程，保持不写入。
--   ② PAY：UI"手工发起"下拉里的 PAY 今天走内置 switch→SETTLE/PREPAY/AGENCY/AIR，其中 PAY/AGENCY、PAY/AIR 的目标 key
--     未部署→落 expenseClaim。若给 PAY 加通配镜像行 PAY/*->PREPAY，会把这两个子类型从"落报销流程"变成"落 PREPAY"，
--     这是行为变更而非搬家，违反判据①，故排除并登记为 R2-D5 待裁决。
--   ③ UI 选项 OTHER：无对应 flowKey、无内置分支、无流程，属"选了也发不起来"的死选项，登记 R2-D6。
-- 影响面：wf_process_config 为配置表（非业务表），本文件 INSERT-only、零 UPDATE、零 DELETE；不动 wf_model / wf_node_config /
--   业务单据表 / ACT_* 引擎表。co1 2026-09 账期仍 CLOSED，本 seed 与账期无关。回滚见同名 _rollback.sql。
-- 标记：remark='V1.4.40批次3路由合一镜像seed别名'（回滚按此精确匹配，只删本文件插入的行）

-- 0) 改前基线（人工记录：before_rows 应为 75，dup_biz_types 应为空）
SELECT COUNT(*) AS before_rows, SUM(del_flag=0 AND status='NORMAL') AS before_active FROM wf_process_config;
SELECT biz_type, COUNT(*) c FROM wf_process_config WHERE del_flag=0 GROUP BY biz_type HAVING c > (SELECT COUNT(DISTINCT IFNULL(sub_type,'')) FROM wf_process_config x WHERE x.del_flag=0 AND x.biz_type=wf_process_config.biz_type);

-- 0.5) 改前记录表（本 seed 是 INSERT-only、回滚按 remark 精确匹配即可；此表只为审计默认处理，不删）
CREATE TABLE IF NOT EXISTS wf_process_config_bak_V1440_20260923 AS SELECT * FROM wf_process_config;
SELECT COUNT(*) AS bak_rows FROM wf_process_config_bak_V1440_20260923;

-- 1) 镜像行（去重：同 (biz_type, sub_type, model_key) 已有未删除行则跳过）
--  1. CLAIM（通配） -> CLAIM   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'CLAIM', NULL, '员工个人报销审批流程', 'CLAIM', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='CLAIM' AND IFNULL(x.sub_type,'')='' AND x.model_key='CLAIM');

--  2. CLAIM/TRAVEL -> CLAIM_TRAVEL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'CLAIM', 'TRAVEL', '事前申请报销（差旅培训报销）审批流程', 'CLAIM_TRAVEL', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='CLAIM' AND IFNULL(x.sub_type,'')='TRAVEL' AND x.model_key='CLAIM_TRAVEL');

--  3. APPLY（通配） -> TRAVEL   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'APPLY', NULL, '出差事前申请审批流程', 'TRAVEL', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='APPLY' AND IFNULL(x.sub_type,'')='' AND x.model_key='TRAVEL');

--  4. APPLY/MEETING -> MEETING   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'APPLY', 'MEETING', '会议事前申请', 'MEETING', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='APPLY' AND IFNULL(x.sub_type,'')='MEETING' AND x.model_key='MEETING');

--  5. APPLY/TRAIN -> TRAIN   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'APPLY', 'TRAIN', '培训费事前申请审批流程', 'TRAIN', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='APPLY' AND IFNULL(x.sub_type,'')='TRAIN' AND x.model_key='TRAIN');

--  6. LOAN（通配） -> LOAN   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'LOAN', NULL, '员工借款审批流程', 'LOAN', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='LOAN' AND IFNULL(x.sub_type,'')='' AND x.model_key='LOAN');

--  7. REPAY（通配） -> REPAY   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'REPAY', NULL, '员工还款审批流程', 'REPAY', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='REPAY' AND IFNULL(x.sub_type,'')='' AND x.model_key='REPAY');

--  8. CONTRACT（通配） -> CONTRACT   改前路径=内置→已部署
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'CONTRACT', NULL, '合同审批流程', 'CONTRACT', 10, 'NORMAL', 'V1.4.40批次3路由合一镜像seed别名', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='CONTRACT' AND IFNULL(x.sub_type,'')='' AND x.model_key='CONTRACT');

-- 2) 复核 A：镜像行是否与推导清单一一对应（期望 expected=actual、missing=extra=0）
SELECT (SELECT COUNT(*) FROM (
SELECT 'CLAIM' biz_type, '' sub_type, 'CLAIM' model_key UNION ALL
SELECT 'CLAIM' biz_type, 'TRAVEL' sub_type, 'CLAIM_TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'LOAN' biz_type, '' sub_type, 'LOAN' model_key UNION ALL
SELECT 'REPAY' biz_type, '' sub_type, 'REPAY' model_key UNION ALL
SELECT 'CONTRACT' biz_type, '' sub_type, 'CONTRACT' model_key
) e) AS expected,
       (SELECT COUNT(*) FROM wf_process_config c JOIN (
SELECT 'CLAIM' biz_type, '' sub_type, 'CLAIM' model_key UNION ALL
SELECT 'CLAIM' biz_type, 'TRAVEL' sub_type, 'CLAIM_TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'LOAN' biz_type, '' sub_type, 'LOAN' model_key UNION ALL
SELECT 'REPAY' biz_type, '' sub_type, 'REPAY' model_key UNION ALL
SELECT 'CONTRACT' biz_type, '' sub_type, 'CONTRACT' model_key
) e ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.40批次3路由合一镜像seed别名') AS actual,
       (SELECT COUNT(*) FROM (
SELECT 'CLAIM' biz_type, '' sub_type, 'CLAIM' model_key UNION ALL
SELECT 'CLAIM' biz_type, 'TRAVEL' sub_type, 'CLAIM_TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'LOAN' biz_type, '' sub_type, 'LOAN' model_key UNION ALL
SELECT 'REPAY' biz_type, '' sub_type, 'REPAY' model_key UNION ALL
SELECT 'CONTRACT' biz_type, '' sub_type, 'CONTRACT' model_key
) e LEFT JOIN wf_process_config c ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key AND c.del_flag=0 WHERE c.id IS NULL) AS missing,
       (SELECT COUNT(*) FROM wf_process_config c LEFT JOIN (
SELECT 'CLAIM' biz_type, '' sub_type, 'CLAIM' model_key UNION ALL
SELECT 'CLAIM' biz_type, 'TRAVEL' sub_type, 'CLAIM_TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, '' sub_type, 'TRAVEL' model_key UNION ALL
SELECT 'APPLY' biz_type, 'MEETING' sub_type, 'MEETING' model_key UNION ALL
SELECT 'APPLY' biz_type, 'TRAIN' sub_type, 'TRAIN' model_key UNION ALL
SELECT 'LOAN' biz_type, '' sub_type, 'LOAN' model_key UNION ALL
SELECT 'REPAY' biz_type, '' sub_type, 'REPAY' model_key UNION ALL
SELECT 'CONTRACT' biz_type, '' sub_type, 'CONTRACT' model_key
) e ON e.biz_type=c.biz_type AND e.sub_type=IFNULL(c.sub_type,'') AND e.model_key=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.40批次3路由合一镜像seed别名' AND e.biz_type IS NULL) AS extra;

-- 3) 复核 B：一个 bizType 只应有一条通配镜像行（期望空集）
SELECT biz_type, COUNT(*) c FROM wf_process_config WHERE del_flag=0 AND IFNULL(sub_type,'')='' GROUP BY biz_type HAVING c>1;

-- 4) 复核 C：镜像行的目标 key 必须全部已部署（期望 bad_target=0）
SELECT COUNT(*) AS bad_target FROM wf_process_config c LEFT JOIN (SELECT DISTINCT KEY_ FROM ACT_RE_PROCDEF) p ON p.KEY_=c.model_key WHERE c.del_flag=0 AND c.remark='V1.4.40批次3路由合一镜像seed别名' AND p.KEY_ IS NULL;

-- 5) 最终状态快照
SELECT biz_type, IFNULL(sub_type,'*') sub, model_key, priority, status, remark FROM wf_process_config WHERE del_flag=0 ORDER BY biz_type, priority DESC, id;
