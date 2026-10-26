-- ============================================================================
-- F2 资料齐套校验 · sys_config 灰度键播种（V1.4.42 / 2026-09-26）
-- 方案出处：流程引擎问题修复方案_20260926.md §3（F2）；代码落点：
--   com.fssc.workflow.service.BillCompletenessGuard（三调用点：WfEngineFacade.start=SUBMIT、
--   WfEngineFacade.complete=NODE:<taskDefKey>、PayBatchService.confirm=CONFIRM）
--
-- ★ 本脚本只播种"关"状态：enabled='0'／models=''／rules='' ⇒ 校验零行为变更（等价改造前）。
--   真正的"开启"是逐键 UPDATE，属人工裁决 RA-F2a/b/c 之后的动作，本脚本不含。
--
-- 规则披露（与既有 wf 开关一致，非本片新增）：
--   BillCompletenessGuard 读键用 sysConfigMapper + limit 1，**不带 tenant 过滤**，
--   与 WfEngineFacade.readConfig（approve.pick_mode.*／approve.transfer_strict.*／approve.deny_self）
--   同一规则；即本表三键为**全局开关**。多租户量产前需与那几键一并收敛到租户级，登记为挂账项。
--
-- 回滚：V1.4.42_f2_completeness_gate_rollback.sql
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
START TRANSACTION;

-- 备份（回滚前记录；空表也建，便于脚本重复执行）
CREATE TABLE IF NOT EXISTS sys_config_bak_v1442_20260926 AS
  SELECT * FROM sys_config WHERE config_key LIKE 'bill.completeness.%';

-- INSERT IGNORE：键已存在时不覆盖运维调过的值（uk_tenant_config_key 保唯一）
INSERT IGNORE INTO sys_config (tenant_id, config_key, config_name, config_value, value_type, group_code, is_builtin, editable, remark)
VALUES
 (1,'bill.completeness.gate.enabled','资料齐套闸口总闸（0=只记日志不改结果，1=按灰度白名单硬拦）','0',
    'STRING','WORKFLOW',0,1,'F2/体检E-01E-02：缺附件、缺合同/PO、出账账户外键悬空的单据此前可一路直走到付款。总闸=0 时三调用点全为直通（等价改造前）。唯一例外：D4 账户外键悬空属脏数据硬错误，不设开关、恒硬拦（方案 §3.1 例外条款），开闸前必须先跑只读普查出存量清单。'),
 (1,'bill.completeness.models','齐套闸口灰度白名单（model_key 逗号分隔，空=一律不拦）','',
    'STRING','WORKFLOW',0,1,'建议首版资金链：TR_PAY,SETTLE,PREPAY。仅这些 model_key 命中时校验，其余流程行为零变化。RA-F2a 口径：SUBMIT 层只告警不阻断，CONFIRM/NODE 层缺件硬拦。'),
 (1,'bill.completeness.rules','齐套闸口分维度规则（分号分隔条目，形如 模型:阶段=维度[,维度]）','',
    'STRING','WORKFLOW',0,1,'条目示例：TR_PAY:CONFIRM=attach,contract_or_po;SETTLE:NODE=attach;CLAIM:NODE:Task_8=attach。阶段取 SUBMIT／CONFIRM／NODE（NODE 可再跟 :节点KEY 精确到单个环节）。维度：attach=D1 附件/发票影像≥1 件、contract_or_po=D2 合同与采购订单至少关联其一。D3 预算控制方式、D5 银行回单/收款单本片未接（普查证据见 人工测试/F2齐套闸口普查_20260926.md）。');

COMMIT;

-- 校验（只读）
SELECT config_key, config_value, editable FROM sys_config
 WHERE del_flag = 0 AND config_key LIKE 'bill.completeness.%' ORDER BY config_key;

-- ----------------------------------------------------------------------------
-- 【开启语句·待授权后由人工执行，本脚本不执行】
-- UPDATE sys_config SET config_value='1'  WHERE config_key='bill.completeness.gate.enabled';
-- UPDATE sys_config SET config_value='TR_PAY,SETTLE,PREPAY' WHERE config_key='bill.completeness.models';
-- UPDATE sys_config SET config_value='TR_PAY:CONFIRM=attach,contract_or_po;SETTLE:NODE=attach'
--   WHERE config_key='bill.completeness.rules';
-- 执行前必读普查报告的"开启后果"一节：现网 11 个付款批次的 bank_account_id 全部指向不存在的 id=1，
-- D4 硬拦与灰度无关，部署即生效。
-- ----------------------------------------------------------------------------
