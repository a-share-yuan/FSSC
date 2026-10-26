-- V1.4.33  批次2 第八族（借款 LOAN/REPAY）配置侧对齐【本轮仅 REPAY】：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：REPAY 已 /api/tenant/wf/designer/publish 重发布（v7→v8，新 procDef REPAY:229），
--   发布前回读复核与 draft 逐字节一致（e2e-ui/batch2/fam8-verify.log）、在途双规则为 0
--   （designer/in-flight runningTotal=0 且 staleCount=0；fam8-publish-check.log）。
-- ★ LOAN 本轮【不发布也不对齐】——§八 在途门槛第一次真的拦下东西：
--   fam8-publish-check.log 实测 LOAN runningTotal=2（exp_bill 5893/5894，co31，批次 1 为"待分配→人工分配"取证特意保留的在途样本；
--   另有 5865/5871 两张 status=APPROVING 却无 wf_instance 的孤儿单，与任务 #28 那 13 张 CLAIM 孤儿同根，处置需业务特批）。
--   不发布的后果必须是"配置也不动"：applyNodeConfig 会把 config.assign_value【追加】到 BPMN candidateGroups 之上，
--   若按未发布的 LOAN_draft 把 12 行改成 8 行，就会出现"配置说 8 道、引擎跑 12 道"的反向脱钩（新图节点拿到旧行档位才是真危害）。
--   实测当前 LOAN：node_config 12 行 == 活体 BPMN 12 节点（fam8-audit-post.log），两侧仍一致，不存在遗留漂移。
--   → LOAN 的 12→8 对齐整包顺延到在途样本处置完成后（方案 §8.11 F 节）。
-- 结构（REPAY）：5 个人工节点 -> 3 个（净 -2，-40%；规则＝留 2 道（Task_2/Task_3）＋删 3 道（Task_1 点名提单、Task_4/Task_5 机器动作）＋新增 1 道（Task_6 到账确认））；
--   作业池 1 个（handling_mode=POOL，新增的到账确认）、排他网关 0 个、并联会审 0 组。
--   本族是批次 2 里第一个"开工三问全部通过"的族（公司解析/deny_self/start 时序三条皆有 Java 正证，见方案 §8.11 B 节），
--   因此 REPAY 的整改是纯结构：删提单点名节点、删两道机器动作节点、【补】一道链上原本不存在的到账确认控制点。
-- 三处节点处置（逐行 Java 取证见 build.js REPAY.note）：
--   ① 删 Task_1「提交还款单」——全库最后一条 flowable:assignee="${approver}" 点名提单节点（配置行 assign_type=INITIATOR）；
--      ${approver} 取自 ExpenseBillController:41-49 请求参数自报的 approverUserId（ExpenseBillService:651 原样入参）＝点名对象由调用方任意指定（D-FAM8-03）。
--   ② 删 Task_4「审核单据及/预制凭证」+ Task_5「生成凭证」（本族原链唯一一对相邻同角色，挂同一岗位 FIN_GL）：
--      还款凭证由 ExpenseWfEventListener.handleApproved() 第 201 行 generateByTemplate(billId,"EXP_REPAY") 自动产出、
--      第 212 行 repayLoan() 自动核销、第 213 行派共享作业链「收款核对→冲销账务归档」→ 人工签字不改变任何数据。
--   ③ 新增 Task_6「出纳确认还款到账并登记收款（作业池）」【CASHIER】排在链首：本族唯一一处"补控制点而不是删节点"。
--      原链从提单直进 Task_2「审核还款单并核实还款信息」，但"钱是否到账"链上无节点负责——到账确认被排在审批【之后】的作业链，
--      而 repayLoan() 在审批通过那一刻就把借款余额核销了＝先核销债权、后确认到账（D-FAM8-05，代码侧缺陷，本轮只读未动码）。
-- 改岗清单（BPMN candidateGroups 与旧 assign_value 不同者，共 0 处）：（无——留下的两道本就是 FIN_AUDIT / PLATE_FIN_MANAGER）
-- 出链幽灵行（3 行，不删不改，仅盘点）：REPAY.Task_1 / REPAY.Task_4 / REPAY.Task_5
--   —— 其中 Task_1 是 INITIATOR 点名行（本族清零后它已无对应节点），Task_4/Task_5 是 FIN_GL 机器动作行；
--      保留理由：增量原则（不删数据）＋ 若 BPMN 回滚到 v7 结构，配置侧须能原样恢复。
-- 挂账（配置侧无法自解，须代码/权限侧处置，详见方案 §8.11）：
--   D-FAM8-01 链权脱钩（第七次暴露）：本族无借款/还款专属权限码，审核动作统一挂在 exp:bill:audit（sys_menu id 3181）上，
--     持权角色 14 个（BASE_FIN_MANAGER,BIZ_FIN_MANAGER,CENTER_APPROVER,CENTER_DIRECTOR,CFO,DEPT_MANAGER,EXEC_APPROVER,FIN_AUDIT,
--     FIN_DIRECTOR,FIN_MANAGER,FIN_MGR_ORG,FSSC_AUDITOR,FSSC_SUPERVISOR,PLATE_FIN_MANAGER）；重造前 LOAN 12 道里 8 道岗位不在名单、
--     REPAY Task_4/Task_5 挂 FIN_GL 亦不在名单；而流程侧审批走 WfTaskController:554 /wf/approve【无 @RequiresPerm】、只靠候选组判定
--     ⇒ 这些节点"待办中心能办、单据审核页无按钮"（本轮把岗名与权限规则对齐，权限表本身未动）；
--   D-FAM8-02 sys_menu id 225「还款管理 /expense/repay」的 perms 为 NULL＝整页不受任何权限码约束；
--   D-FAM8-03 ${approver} 由请求参数自报（本轮已随 Task_1 出链清零，但 submit 接口仍可传该参数给其它 bizType，属报销族同型遗留）；
--   D-FAM8-04 借款的"付款"与"账务"其实不在这条流程里：审批通过后 SharingService:255-258 另派作业链「合规审核→资金支付→账务处理」，
--     其认领/完成是两个粗粒度权限码 sharing:task:receive / sharing:task:finish（SharingController:49/57，实测各授 7 个角色
--     CASHIER/FIN_AUDIT/FIN_GL/FSSC_AUDITOR/FSSC_SUPERVISOR/SCAN_STAFF/SHARING_SUPPORT），既不区分环节类型也不区分单据类型，
--     finish() 无第二人复核 ⇒ 连扫描岗都能认领并完成借款的"资金支付"环节（这是 LOAN.Task_11 留池不删的理由）；
--   D-FAM8-05 先核销债权、后确认到账（见 ③），且 ExpenseBillService.markPaid()（第 1244 行）在借款链上零调用方 ⇒ LOAN 单永不进 PAID；
--   R8-01 借款 approve-deduct 无 Flowable 最终状态校验；R8-02 co31 之外 DIRECT_SUPERVISOR 全租户仅 2 人、co1 为 0（本轮已随 Task_2 出链）。
-- 档位规则（方案 §5.9）：作业池 48h AUTO_REMIND、共享审单(FIN_AUDIT) 8h AUTO_REMIND、业务审批(PLATE_FIN_MANAGER) 24h AUTO_REMIND；
--   empty_strategy 全 ESCALATE（理由见 gen-sql.js 注释）。NOTIFY 本轮 0 条（§七 R8 占比规则）。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.33_fam8_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1433_20260923 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('REPAY','Task_6'),('REPAY','Task_2'),('REPAY','Task_3'));

-- 0b) 新 key 插行前体检（期望 1 行，与下方 INSERT 一一对应）
SELECT k.model_key, k.node_key FROM (
  SELECT 'REPAY' model_key, 'Task_6' node_key
) k LEFT JOIN wf_node_config c ON c.model_key = k.model_key AND c.node_key = k.node_key AND c.del_flag = 0
 WHERE c.node_key IS NULL;

-- 1) 逐节点对齐（2 行）
-- REPAY.Task_2  审核还款并核销借款余额【财务审核岗】
UPDATE wf_node_config
   SET node_name      = '审核还款并核销借款余额【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'REPAY' AND node_key = 'Task_2';

-- REPAY.Task_3  审批核销板块内借款【板块财务经理】
UPDATE wf_node_config
   SET node_name      = '审批核销板块内借款【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'REPAY' AND node_key = 'Task_3';

-- 2) 新 key 插行（1 行，NOT EXISTS 守卫保证去重）
-- REPAY.Task_6  出纳确认还款到账并登记收款（作业池）【出纳】  ← 链首新增控制点，sort=1 让它排在保留节点之前
INSERT INTO wf_node_config
  (tenant_id, model_key, node_key, node_name, assign_type, assign_value, handling_mode, up_level, multi_type,
   vote_ratio, allow_reject, reject_to, allow_transfer, allow_addsign, allow_edit, empty_strategy, auto_pass_self,
   timeout_hours, timeout_action, sort, create_time, update_time, del_flag)
SELECT 1, 'REPAY', 'Task_6', '出纳确认还款到账并登记收款（作业池）【出纳】', 'ROLE', 'CASHIER', 'POOL', 1, 'ANY',
       50, 1, 'INITIATOR', 1, 0, 0, 'ESCALATE', 0, 48, 'AUTO_REMIND', 1, NOW(), NOW(), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM wf_node_config WHERE model_key = 'REPAY' AND node_key = 'Task_6' AND del_flag = 0);

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT COUNT(*) AS drift_rows_A FROM (
  SELECT e.model_key, e.node_key
    FROM (
    SELECT 'REPAY' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'CASHIER' exp_role, '出纳确认还款到账并登记收款（作业池）【出纳】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'REPAY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核还款并核销借款余额【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'REPAY' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '审批核销板块内借款【板块财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    ) e
    LEFT JOIN wf_node_config c ON c.model_key = e.model_key AND c.node_key = e.node_key AND c.del_flag = 0
   WHERE c.node_key IS NULL
      OR IFNULL(c.assign_type, '') <> e.exp_type
      OR IFNULL(c.assign_value, '') <> e.exp_role
      OR IFNULL(c.node_name, '') <> e.exp_name
      OR IFNULL(c.empty_strategy, '') <> e.exp_es
      OR IFNULL(c.timeout_hours, -1) <> e.exp_th
      OR IFNULL(c.timeout_action, '') <> e.exp_ta
      OR IFNULL(c.handling_mode, '') <> e.exp_hm
  ) x;

-- 4) 复核 B：REPAY 3 个角色的存在性与 co1 在编持岗人数（规则 status='NORMAL' 且 is_admin=0）。
--     单点/双人角色：FIN_AUDIT co1 仅 1 人（挂 Task_2）、PLATE_FIN_MANAGER co1 2 人（COMPANY 范围）→ 断流靠 ESCALATE 沿汇报线外兜；
--      CASHIER co1 3 人（作业池，多人同池正是 POOL 的用意）。角色收敛属 R5（等 RA-01~15 裁决），本轮不增岗。
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('CASHIER','FIN_AUDIT','PLATE_FIN_MANAGER') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(3) + 出链后的幽灵行（仅盘点，不改；期望 rows_total=5、rows_in_new_bpmn=3）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('REPAY','Task_6'),('REPAY','Task_2'),('REPAY','Task_3'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('REPAY') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 1 行 POOL）
SELECT COUNT(*) AS pool_rows_D FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('REPAY','Task_6'),('REPAY','Task_2'),('REPAY','Task_3')) AND handling_mode = 'POOL';

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT COUNT(*) AS admin_or_named_rows_E FROM (
  SELECT c.model_key, c.node_key
    FROM wf_node_config c
   WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('REPAY','Task_6'),('REPAY','Task_2'),('REPAY','Task_3'))
     AND ( c.assign_type <> 'ROLE'
        OR c.assign_value REGEXP '^[0-9]+$'
        OR EXISTS (SELECT 1 FROM sys_user u
                     JOIN sys_user_role ur ON ur.user_id = u.id
                     JOIN sys_role r ON r.id = ur.role_id
                    WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) )
  ) x;

-- 8) 复核 F：出链后仍在的【点名 assignee】残留普查 —— REPAY 原本正是全库最后一条 INITIATOR 点名行（Task_1），
--    该节点已随重造出链（成为幽灵行），本 seed 触及的 3 行必须全是 ROLE；期望 0 行
SELECT COUNT(*) AS named_user_rows_F FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('REPAY','Task_6'),('REPAY','Task_2'),('REPAY','Task_3'))
   AND (assign_type <> 'ROLE' OR assign_value IS NULL OR assign_value = '');

-- 9) 复核 G：在途门槛复述（本 seed 生效期间 REPAY 必须仍为 0 在途；若不为 0，说明有人在 v7 定义上起了新单，
--    这些实例会停在旧结构里 → 只盘点、不改数据，处置走方案 §8.11 F 节特批流程）
SELECT 'V1.4.33 执行后 REPAY 在途（期望 0，非 0 需登记）' AS chk,
       (SELECT COUNT(*) FROM wf_instance WHERE model_key = 'REPAY' AND status = 'RUNNING') AS running_from_wf_instance;
