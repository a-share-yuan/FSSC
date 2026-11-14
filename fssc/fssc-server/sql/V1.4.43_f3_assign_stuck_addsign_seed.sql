-- ============================================================================
-- V1.4.43 F3 取人负载 / 卡单上报 / 假开关接真 —— 配置 seed（全部注释态，未执行）
-- 生成时刻：2026-09-26；数据来源：fssc 库只读普查（mysql -e SELECT，零写入）
-- 方案：流程引擎问题修复方案_20260926.md §4（F3）；裁决项：RA-F3a（灰度清单）/ RA-F3b（改档）
--
-- 本文件在本轮演练中【一行都没有执行】。铁律：演练期 MySQL SELECT-only。
--    开启前必须由业务方逐条裁决，并按 §末回滚脚本准备退路。
-- ============================================================================

-- 【只读普查快照（2026-09-26）】
--   wf_node_config（del_flag=0，355 行有效节点）timeout_action 分布：
--     AUTO_REMIND 250 + NOTIFY 92 = 342 催办档；ESCALATE_AFTER_URGES 7 + AUTO_PASS 3 + ESCALATE 3 = 13 处置档
--     其中配了 timeout_hours>0 的催办档节点 339 个；另 3 个（MI_TEST/MI_SEQ/MI_VOTE 的 Task_1）配了动作却没有 SLA ⇒ 永不被扫
--   卡单现状（wf_task，status=PENDING，2026-09-26 16:52 快照）：16 条在办，其中 urged_count>=3 共 8 条，最长 37 次；

--   wf_model.enable_delegate：87 行全为 1（⇒ 代码接真后行为零变化，见 F3-3）
--   wf_node_config.allow_addsign（del_flag=0）：1 有 44 个节点、0 有 311 个节点（此前实体不映射 + 界面不显示 + 运行时零读取）
--   关键岗（资金/出纳/付款/月结/结算/共享主管）催办档节点：47 个 = 35 个审批节点（本文件建议改档）+ 12 个 POOL 作业池节点（不适用，见文末）
--   流程管理员角色 WF_ADMIN（sys_role id=99035）授权人数：0 ⇒ 卡单聚合告警当前无处可发（已登记缺陷，需先补授权）
-- ============================================================================

-- ---------- 1) sys_config：三组新键（缺省值＝与改造前逐字一致） ----------
-- tenant_id 必须是 1：sys_config 是租户表，读取走租户拦截器（WHERE tenant_id=<当前租户>），
--    插 NULL/0 的行永远读不到＝假配置。列名规则对齐现网 V1.4.42（无 config_desc 列，名称列是 config_name）。
-- INSERT IGNORE：键已存在时不覆盖运维调过的值（uk_tenant_config_key 保唯一）
-- INSERT IGNORE INTO sys_config (tenant_id, config_key, config_name, config_value, value_type, group_code, is_builtin, editable, remark)
-- VALUES
--  (1,'wf.assign.strategy','ROLE 节点取人策略（FIRST_ID=第一个候选人，LEAST_LOADED=在办任务最少，ROUND_ROBIN=轮询）','FIRST_ID',
--     'STRING','WORKFLOW',0,1,'F3-1／体检 E-07、E-13：单人岗与多人岗取人一律落在第一个候选人上，忙的人越来越忙。改值只对下面白名单里的模型生效。'),
--  (1,'wf.assign.strategy.models','取人策略灰度白名单（model_key 逗号分隔，空＝全库不生效）','',
--     'STRING','WORKFLOW',0,1,'RA-F3a 建议首版：LOAN,CLAIM,SETTLE。空清单时总开关值再硬也不生效（fail-closed）。'),
--  (1,'wf.timeout.stuck.alert.after_urges','卡单阈值：同一待办催满 N 次仍 PENDING＝卡单，聚合成一条站内信发流程管理员；0/负数＝关闭','3',
--     'NUMBER','WORKFLOW',0,1,'F3-2／体检 E-03「只催不升」。只出声不处置：不改派、不自动通过。现网实测最长催办 37 次无人升级。'),
--  (1,'approve.addsign_gate.enabled','允许加签节点开关是否真拦（0=只看按钮不看校验，1=按 wf_node_config.allow_addsign 硬拒）','0',
--     'STRING','WORKFLOW',0,1,'F3-3／体检 E-11：allow_addsign 此前实体不映射、界面不显示、运行时零读取（三重落空），现网 44 行=1、311 行=0。总开关=0 时加签行为与改造前逐字一致。'),
--  (1,'approve.addsign_gate.models','加签校验灰度白名单（model_key 逗号分隔，空＝总开关开了也不生效）','',
--     'STRING','WORKFLOW',0,1,'建议首版：TR_PAY。校验生效时，未勾 allow_addsign 的节点服务端直接拒绝加签并在待办列表隐藏按钮。');
-- 注：不 seed 也可以跑——代码缺省即 FIRST_ID / 空清单 / 阈值 3 / 校验关。seed 的作用是把规则显式写进配置面，便于审计。

-- ---------- 2) 开启语句（RA-F3a 裁决后再执行，逐模型灰度） ----------
-- UPDATE sys_config SET config_value='LEAST_LOADED' WHERE tenant_id=1 AND config_key='wf.assign.strategy';
-- UPDATE sys_config SET config_value='LOAN,CLAIM,SETTLE' WHERE tenant_id=1 AND config_key='wf.assign.strategy.models';
-- UPDATE sys_config SET config_value='1' WHERE tenant_id=1 AND config_key='approve.addsign_gate.enabled';
-- UPDATE sys_config SET config_value='TR_PAY' WHERE tenant_id=1 AND config_key='approve.addsign_gate.models';
-- 卡单上报无需开启：只出声不处置（不改派、不自动通过），阈值即开关（设 0 即熄火）。

-- ---------- 3) RA-F3b：关键岗节点改档 AUTO_REMIND/NOTIFY → ESCALATE_AFTER_URGES ----------
-- 语义：先催办，催满 wf.timeout.escalate.after_urges（默认 3）次仍无人办理，
--       沿汇报线按本节点 up_level 升级给真实负责人（绝不落 admin）。升级过一次不再二次升级。
-- 逐节点语句（共 35 个）：WHERE 带原值判定，重复执行不会二次改写。

-- [1378] BAD_DEBT_CONFIRM / Task_3 / 往来结算岗审核【应收会计】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1378 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1742] FM_ARCHIVE / Task_3 / 复核归档完整性与调阅范围【共享主管】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1742 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1562] FM_BORROW / Task_8 / 确认按期归还并核销借阅台账【共享主管】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1562 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1445] FM_DESTROY / Task_3 / 共享主管审核账面与档案一致性【共享主管】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1445 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1767] GL_MERGE / Task_5 / 内部资金往来抵消确认【应收会计】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1767 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1787] GL_OTHER_RECEIPT / Task_2 / 收款确认并开收据、提交收款认领单【资金岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1787 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1794] GL_SALARY_PAY / Task_4 / 网银录入发放明细【资金支付岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1794 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1795] GL_SALARY_PAY / Task_5 / 网银付款审核【资金岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1795 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1366] LOAN / Task_10 / 共享主管复核【共享主管】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1366 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1367] LOAN / Task_11 / 出纳付款【出纳】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1367 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1871] PAY_PLAN_BASE / Task_2 / 部门经理审核付款计划与供应商【部门经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1871 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1872] PAY_PLAN_BASE / Task_3 / 项目经理审核付款计划与费用归属【项目经理/费用承担部门负责人】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1872 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1873] PAY_PLAN_BASE / Task_4 / 基地财务经理审核付款计划【基地财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1873 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1874] PAY_PLAN_BASE / Task_5 / 基地总经理审批付款计划【基地总经理】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1874 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1467] PAY_PLAN_PLATE / Task_2_0 / 项目经理会审付款计划与项目预算【项目经理/费用承担部门负责人】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1467 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1468] PAY_PLAN_PLATE / Task_2_1 / 基地财务经理会审资金与预算占用【基地财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1468 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1469] PAY_PLAN_PLATE / Task_2_2 / 资金支付岗会审支付可行性【资金支付岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1469 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1470] PAY_PLAN_PLATE / Task_2_3 / 板块财务经理会审板块资金平衡【板块财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1470 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1473] PAY_PLAN_PLATE / Task_4 / 财务总监审批付款计划（超 100 万）【财务总监】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1473 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1474] PAY_PLAN_PLATE / Task_5 / 总裁审批付款计划（超 500 万）【总裁】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1474 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1618] PREPAY / Task_3 / 审核单据及付款申请【财务审核岗】 / SLA 8h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1618 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1876] RECEIVABLE / Task_2 / 查询收款信息并提交收款认领单【资金支付岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1876 AND del_flag=0 AND timeout_action='NOTIFY';

-- [1504] TR_PAY / Task_2 / 资金主任审核付款批次与账户头寸【资金岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1504 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1505] TR_PAY / Task_3 / 基地财务经理审核付款批次【基地财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1505 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1506] TR_PAY / Task_4 / 基地总经理审批付款（超 50 万）【基地总经理】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1506 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1507] TR_PAY / Task_5 / 板块财务经理审批付款（超 100 万）【板块财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1507 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1508] TR_PAY / Task_6 / 财务总监审批付款（超 200 万）【财务总监】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1508 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1509] TR_PAY / Task_7 / 总裁审批付款（超 500 万）【总裁】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1509 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1510] TR_PAY / Task_8 / 共享主管复核付款单据齐套性【共享主管】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1510 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1515] TR_PLAN / Task_3 / 基地财务经理审核资金计划【基地财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1515 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1517] TR_PLAN / Task_5 / 板块财务经理审批资金计划【板块财务经理】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1517 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1518] TR_PLAN / Task_6 / 财务总监审批资金计划【财务总监】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1518 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1519] TR_PLAN / Task_7 / 总裁审批资金计划【总裁】 / SLA 48h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1519 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1690] TR_TRANSFER / Task_1 / 资金支付岗审核调拨申请与账户头寸【资金支付岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1690 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- [1903] TR_UPLOAD / Task_1 / 资金支付岗审核上存申请与账户头寸【资金支付岗】 / SLA 24h
-- UPDATE wf_node_config SET timeout_action='ESCALATE_AFTER_URGES', update_time=NOW()
--  WHERE id=1903 AND del_flag=0 AND timeout_action='AUTO_REMIND';

-- ---------- 4) 明确不改档的 12 个关键岗节点（POOL 作业池） ----------
-- WfTimeoutJob.handleEscalateLadder() 对作业节点（handling_mode=POOL / assign_type=SHARE_POOL）
-- 走的是另一条既有档位：催满 2 次自动 releaseByTimeout 退回任务池，让同池他人可领。
-- 若把它们也配成 ESCALATE_AFTER_URGES，代码里那一档会被 isWorkNode() 短路 ⇒ 配了不生效（正是本轮体检要消灭的"假配置"）。
-- 清单：
--   [1572] GL_MONTH_CLOSE / Task_1 / 月结 12 项作业齐套：账务/折旧摊销/银行核对/库存事务/内部往来/税金计提/制造费用/劳务预提/完工测算/收入确认/重分类/过账（作业池）【财务专员】 / SLA 24h / 现值 AUTO_REMIND
--   [1778] GL_OFFSET / Task_2 / 计算结算金额并提交开票申请（作业池）【财务专员】 / SLA 48h / 现值 AUTO_REMIND
--   [1914] REPAY / Task_6 / 出纳确认还款到账并登记收款（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1671] TR_BANK_MASTER / Task_6 / 维护账户主数据与网银参数并影像齐套（作业池）【共享主管】 / SLA 48h / 现值 AUTO_REMIND
--   [1672] TR_BANK_RECON / Task_1 / 获取银行明细、导出日记账并编制余额调节表（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1888] TR_BILL / Task_1 / 票据开具背书签章、银行办理与台账登记（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1514] TR_PLAN / Task_2 / 基地财务汇总编制资金计划并齐套依据（作业池）【资金岗】 / SLA 48h / 现值 AUTO_REMIND
--   [1683] TR_PLAN_ADJUST / Task_2 / 录入调整资金计划与调整依据（作业池）【资金岗】 / SLA 48h / 现值 AUTO_REMIND
--   [1899] TR_RECEIPT / Task_4 / 录入票据与收款信息并核对到款（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1694] TR_TRANSFER / Task_5 / 银企转账执行与回单影像齐套（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1904] TR_UPLOAD / Task_2 / 账户资金归集执行与回单影像齐套（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND
--   [1715] TR01-02 / Task_1 / 印鉴变更授权书与新印鉴卡影像齐套（作业池）【出纳】 / SLA 48h / 现值 AUTO_REMIND

-- ---------- 5) 前置动作（必须先做，否则本批只改出日志噪音） ----------
-- (a) 给 WF_ADMIN 角色补授权（sys_role id=99035，现网授权人数实测 0 ⇒ 卡单聚合告警无收件人，
--     现在只能在日志里看到「卡单聚合告警无接收人…」，站内信一条也发不出去）：
--     INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (1, <流程管理员用户ID>, 99035);
--     多个管理员逐行列举；执行后用 SELECT COUNT(*) FROM sys_user_role WHERE role_id=99035 复核。
-- (b) 3 个 MI_* 测试节点（MI_TEST/MI_SEQ/MI_VOTE 的 Task_1）配了 timeout_action 却 timeout_hours=NULL ⇒ 永不被扫描命中。
