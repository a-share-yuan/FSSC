-- V1.4.39  批次3（代码轨）round 2 · wf_model.biz_type 台账补登记（R5-11 / R6-05 挂账项）
-- 做什么：把 17 行 biz_type 为空的已发布模型登记为 biz_type＝model_key（与路由事实同名）。
-- 为什么零行为影响（先说清楚，避免被当成修复）：
--   全仓 grep 无任何一处读取 WfModel.bizType（读方为 0；写方仅 WfModelService.java:184 的 syncFromTemplates，
--   而 wf_business_flow 当前 0 行 ⇒ 该同步是空跑）。发起路由走 wf_process_config.biz_type ＋ 内置 switch，
--   与这一列无关。所以本文件是"台账完整性"，不是功能修复：登记前后所有单据落点、待办、审批链完全一致，
--   设计器列表也不显示这一列（前端 grep 无引用）；它的价值是让 R5-11/R6-05 挂账的台账规则与代码事实对齐。
-- 取值规则（不猜）：只补 biz_type＝model_key，且每一行都有一条代码调用点证据（下表）；无证据的行不出现在本文件里。
--   17 行里 8 行另有历史实例同值佐证（wf_instance.biz_type＝model_key，条数见每行注释），
--   9 行只有调用点证据（该流程尚无历史实例，属新上线/未跑过的模型）。
-- 影响面：配置/台账表 wf_model，UPDATE-only、只改 biz_type 与 update_time 两列，零 INSERT、零 DELETE、不动 bpmn_xml；
--   不触碰 wf_node_config / 业务单据表 / ACT_* 引擎表；co1 2026-09 账期仍 CLOSED，与本文件无关。
-- 回滚：同名 _rollback.sql 从记录表按 id 精确还原为 NULL。

-- 0) 改前基线（期望 null_rows=17）
SELECT COUNT(*) AS null_rows FROM wf_model WHERE del_flag=0 AND (biz_type IS NULL OR biz_type='');

-- 0.5) 改前记录（只存这 17 行的 id/原值，回滚按它还原）
DROP TABLE IF EXISTS wf_model_biztype_bak_V1439_20260923;
CREATE TABLE wf_model_biztype_bak_V1439_20260923 AS SELECT id, model_key, version, biz_type AS old_biz_type, update_time AS old_update_time
  FROM wf_model WHERE del_flag=0 AND (biz_type IS NULL OR biz_type='');
SELECT COUNT(*) AS bak_rows FROM wf_model_biztype_bak_V1439_20260923;

-- 1) 逐行补登记（去重：带 biz_type IS NULL 前置条件，重复执行不会覆盖已有值）
--  1. id=163 AR_RECON v3 DEPLOYED  证据=receivable/service/RcReceivableService.java:139  start("AR_RECON", amount=BigDecimal.ZERO)  历史实例同值 6 条
UPDATE wf_model SET biz_type='AR_RECON', update_time=NOW()
 WHERE id=163 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  2. id=174 BAD_DEBT_CONFIRM v4 DEPLOYED  证据=receivable/service/RcBadDebtService.java:185  start("BAD_DEBT_CONFIRM", amount=d.getAmount())  历史实例同值 4 条
UPDATE wf_model SET biz_type='BAD_DEBT_CONFIRM', update_time=NOW()
 WHERE id=174 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  3. id=173 BAD_DEBT_PROV v3 DEPLOYED  证据=receivable/service/RcBadDebtService.java:123  start("BAD_DEBT_PROV", amount=totalAmount)  历史实例同值 2 条
UPDATE wf_model SET biz_type='BAD_DEBT_PROV', update_time=NOW()
 WHERE id=173 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  4. id=165 FA_CIP_TO_ASSET v3 DEPLOYED  证据=asset/service/MdAssetService.java:271  start("FA_CIP_TO_ASSET", amount=a.getOriginalAmount())
UPDATE wf_model SET biz_type='FA_CIP_TO_ASSET', update_time=NOW()
 WHERE id=165 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  5. id=166 FA_DEPRECIATION v4 DEPLOYED  证据=asset/service/AssetDeprBatchService.java:102  start("FA_DEPRECIATION", amount=batch.getTotalAmount())
UPDATE wf_model SET biz_type='FA_DEPRECIATION', update_time=NOW()
 WHERE id=166 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  6. id=172 GL_SALARY_PAY v3 DEPLOYED  证据=glex/service/GlSalaryRunService.java:166  start("GL_SALARY_PAY", amount=nvl(run.getTotalAmount()))  历史实例同值 2 条
UPDATE wf_model SET biz_type='GL_SALARY_PAY', update_time=NOW()
 WHERE id=172 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  7. id=171 GL_SALARY_PROV v3 DEPLOYED  证据=glex/service/GlSalaryRunService.java:112  start("GL_SALARY_PROV", amount=run.getTotalAmount())  历史实例同值 2 条
UPDATE wf_model SET biz_type='GL_SALARY_PROV', update_time=NOW()
 WHERE id=171 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  8. id=170 GL_TAX_PAY v3 DEPLOYED  证据=glex/service/GlTaxProvisionService.java:131  start("GL_TAX_PAY", amount=target.getTaxAmount())  历史实例同值 1 条
UPDATE wf_model SET biz_type='GL_TAX_PAY', update_time=NOW()
 WHERE id=170 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
--  9. id=169 GL_TAX_PROV v3 DEPLOYED  证据=glex/service/GlTaxProvisionService.java:99  start("GL_TAX_PROV", amount=total)  历史实例同值 1 条
UPDATE wf_model SET biz_type='GL_TAX_PROV', update_time=NOW()
 WHERE id=169 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 10. id=178 INV_DISPOSAL_APPLY v3 DEPLOYED  证据=asset/service/MdInventoryService.java:167  start("INV_DISPOSAL_APPLY", amount=cost)
UPDATE wf_model SET biz_type='INV_DISPOSAL_APPLY', update_time=NOW()
 WHERE id=178 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 11. id=177 INV_DISPOSAL_CHECK v3 DEPLOYED  证据=asset/service/MdInventoryService.java:178  start("INV_DISPOSAL_CHECK", amount=qty==null?ZERO:qty)
UPDATE wf_model SET biz_type='INV_DISPOSAL_CHECK', update_time=NOW()
 WHERE id=177 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 12. id=179 INV_DISPOSAL_EXEC v3 DEPLOYED  证据=asset/service/MdInventoryService.java:192  start("INV_DISPOSAL_EXEC", amount=disposalAmt)
UPDATE wf_model SET biz_type='INV_DISPOSAL_EXEC', update_time=NOW()
 WHERE id=179 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 13. id=176 INV_ISSUE_NONPROJECT v3 DEPLOYED  证据=asset/service/MdInventoryService.java:136 同上三元表达式的另一分支
UPDATE wf_model SET biz_type='INV_ISSUE_NONPROJECT', update_time=NOW()
 WHERE id=176 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 14. id=175 INV_ISSUE_PROJECT v3 DEPLOYED  证据=asset/service/MdInventoryService.java:136  flowKey = "NONPROJECT".equals(projectType) ? "INV_ISSUE_NONPROJECT" : "INV_ISSUE_PROJECT"（:138 start(flowKey)）
UPDATE wf_model SET biz_type='INV_ISSUE_PROJECT', update_time=NOW()
 WHERE id=175 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 15. id=164 INVOICE_MATCH_MANUAL v3 DEPLOYED  证据=sharing/service/ThreeWayMatchService.java:130  start("INVOICE_MATCH_MANUAL", amount=bill.getTotalAmount())  历史实例同值 4 条
UPDATE wf_model SET biz_type='INVOICE_MATCH_MANUAL', update_time=NOW()
 WHERE id=164 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 16. id=167 PAY_PLAN_BASE v4 DEPLOYED  证据=payplan/service/PayPlanService.java:82  flowKey = "PLATE".equals(plan.getPlanType()) ? "PAY_PLAN_PLATE" : "PAY_PLAN_BASE"（:86 start(flowKey)）
UPDATE wf_model SET biz_type='PAY_PLAN_BASE', update_time=NOW()
 WHERE id=167 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');
-- 17. id=168 PAY_PLAN_PLATE v7 DEPLOYED  证据=payplan/service/PayPlanService.java:82 同上三元表达式的另一分支
UPDATE wf_model SET biz_type='PAY_PLAN_PLATE', update_time=NOW()
 WHERE id=168 AND del_flag=0 AND (biz_type IS NULL OR biz_type='');

-- 2) 复核 A：空值清零、登记值与 model_key 同名（期望 null_left=0、named_rows=17、diff_named=0）
SELECT (SELECT COUNT(*) FROM wf_model WHERE del_flag=0 AND (biz_type IS NULL OR biz_type='')) AS null_left,
       (SELECT COUNT(*) FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id WHERE m.biz_type=m.model_key) AS named_rows,
       (SELECT COUNT(*) FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id WHERE m.biz_type<>m.model_key) AS diff_named;

-- 3) 复核 B：本文件只碰了 biz_type/update_time 两列（期望 identity_changed=0、rows_touched=17）
SELECT (SELECT COUNT(*) FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id
        WHERE m.model_key<>b.model_key OR m.version<>b.version) AS identity_changed,
       (SELECT COUNT(*) FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id) AS rows_touched;

-- 4) 最终状态快照
SELECT m.id, m.model_key, m.biz_type, m.version, m.status FROM wf_model m JOIN wf_model_biztype_bak_V1439_20260923 b ON b.id=m.id ORDER BY m.model_key;
