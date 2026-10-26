-- V1.4.34  批次2 第九族（预付与招待 PREPAY/CLAIM_G1）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：两模型已 /api/tenant/wf/designer/publish 重发布（PREPAY v11→v12 引擎 228→229；CLAIM_G1 v4→v5 引擎 3→4），
--   发布前回读复核与 draft 逐字节一致（fam9-verify.log）、在途双规则 0（designer/in-flight runningTotal=0 且 staleCount=0，
--   fam9-publish-check.log）、四件套 md5 全等（fam9-repo-sync.log：PREPAY dee8bc2c…、CLAIM_G1 2cc5c7f4…）。
-- 结构：PREPAY 13→8（净 -5）、
--   CLAIM_G1 7→5（净 -2）；族合计 20→13（-35%）。
--   作业池 1 个（PREPAY.Task_2 扫描，handling_mode=POOL）、排他网关 5 个（PREPAY 0→4、CLAIM_G1 2→1）、并联会审 0 组。
--   UPDATE=13 行、INSERT=0 行（本族全部沿用原 node_key ⇒ 纯 UPDATE、零插行，批次 2 第一次）。
-- 本族三处处置（逐行 Java/SQL 取证见 build.js 的 note 与方案 §8.12）：
--   ① PREPAY 加 4 个金额网关（原链 0 网关＝1 元与 1,000 万预付同样签 13 道），阈值逐字借用同域已发布兄弟链
--      CLAIM/SETTLE 的阶梯 5万/20万/50万/500万（wf_process_config 实测 EXP_PAY 域由 PREPAY 与 SETTLE 两条链分流，
--      同表 exp_bill、同 bizType；build.js 断言 1 从仓内 latest 件反向抓取该集合做硬核对）。
--      实测改前 simulate：¥1 / ¥4.99万 / ¥5万 / ¥20万 / ¥50万 / ¥500万 **6 检查全部 13 步、1 条路径**（fam9-gate-before.log）；
--      改后 9 检查 → **5 条不同路径（4/5/6/7/8 步）**（fam9-gate.log）。
--   ② PREPAY 合并同角色多道：Task_4+Task_5（DEPT_MANAGER 相邻同角色，co1 仅 1 人连签两遍）、
--      Task_3+Task_6+Task_13（FIN_AUDIT 一链三角色位）；删 Task_1「提交报账单」（本族唯一 ${approver} 点名提单节点，
--      配置行 1616 assign_type=INITIATOR）与 Task_8「基地分管领导审批」（BASE_DIV_LEADER 全租户仅 1 人、无条件签字，
--      且实测已发布的同域报销三链 CLAIM/CLAIM_TRAVEL/SETTLE 都不含这一档）。
--   ③ CLAIM_G1 删 Gw_1「招待费判定」及其默认流 Flow_3「非招待费 → EndEvent」：该网关恒真（Java resolveStartProcessKey
--      已用同一个 resolveExpenseType() 判过、只有 ENTERTAIN 才投进本模型），而默认分支是**零审批直通出口**——
--      实测改前 simulate 6 检查**全部「审批0步 → 结束」**（validate errors=0 warnings=0，设计器完全不告警）。
--      同时删提单节点 Task_1【COMMON_EMP】、合并 Task_3/Task_6 两道 FSSC_AUDITOR，并把两处名实不符对齐
--      （Task_4 名「财务总监」实挂 CENTER_APPROVER＝中心/总监级审批档；真岗"财务总监"是另一角色 CFO）。
-- 岗名对齐清单（node_name 改动 12 处，assign_value 改岗 0 处）：PREPAY.Task_2「扫描单据【财务扫描岗】」→「扫描单据及影像齐套（作业池）【财务扫描岗】」、PREPAY.Task_4「直接上级审批【部门经理】」→「部门经理审核【部门经理】」、PREPAY.Task_7「项目经理审批【项目经理/费用承担部门负责人】」→「项目经理/费用承担部门审核【项目经理/费用承担部门负责人】」、PREPAY.Task_3「审核单据【财务审核岗】」→「审核单据及付款申请【财务审核岗】」、PREPAY.Task_9「基地总经理审批【基地总经理】」→「基地总经理审核【基地总经理】」、PREPAY.Task_10「板块财务经理审批【板块财务经理】」→「板块财务经理审核【板块财务经理】」、PREPAY.Task_11「板块分管领导审批【板块分管领导】」→「板块分管领导审核【板块分管领导】」、CLAIM_G1.Task_2「部门经理审核【部门经理—核业务真实性】」→「部门经理审核业务真实性【部门经理】」、CLAIM_G1.Task_3「财务共享审核【共享会计—人均标准/真实性】」→「共享审核人均标准及预制凭证【共享会计】」、CLAIM_G1.Task_4「财务总监审核【财务总监】」→「总监级授权审批【中心/总监级审批档】」、CLAIM_G1.Task_5「执行总裁审批【执行总裁—大额升级】」→「高管决策审批【高管决策审批档】」、CLAIM_G1.Task_7「生成凭证【总账会计】」→「审核预制凭证【总账会计】」
-- 链序同步（sort 改动 13 处，本 seed 第一次同步 sort，防"配置顺序≠图顺序"脱钩）：PREPAY.Task_2 2→1、PREPAY.Task_4 4→2、PREPAY.Task_7 7→3、PREPAY.Task_3 3→4、PREPAY.Task_9 9→5、PREPAY.Task_10 10→6、PREPAY.Task_11 11→7、PREPAY.Task_12 12→8、CLAIM_G1.Task_2 2→1、CLAIM_G1.Task_3 3→2、CLAIM_G1.Task_4 4→3、CLAIM_G1.Task_5 5→4、CLAIM_G1.Task_7 7→5
-- 超时矩阵：作业池 48h／授权档 48h／共享审单 8h／业务审批 24h，timeout_action 全 AUTO_REMIND
--   ⇒ 本族在用 13 行 NOTIFY 由 13 行（100%，批次 2 至今最大单项违规）降为 0 行（0%），达 §七 R8 ≤30%；
--   含 7 条出链幽灵行未动（仍为 NOTIFY），按"含幽灵"规则为 7/20＝35%。
-- empty_strategy：在用 13 行全 ESCALATE（原 5 行 ERROR 全部改出，理由见 gen-sql.js 注释；ESCALATE 沿汇报线兜、绝不兜 admin）。
-- 出链幽灵行（7 行，不删不改，仅盘点）：CLAIM_G1.Task_1 / CLAIM_G1.Task_6 / PREPAY.Task_1 / PREPAY.Task_13 / PREPAY.Task_5 / PREPAY.Task_6 / PREPAY.Task_8
--   —— 保留理由：增量原则（不删数据）＋ 若 BPMN 回滚到 v11/v4 结构，配置侧须能原样恢复；引擎按 BPMN 节点反查配置，不再读取它们。
-- 挂账（配置侧无法自解，详见方案 §8.12 C 节）：
--   D-FAM9-01 PREPAY/CLAIM_G1 双零流量（0 张单据、0 个历史实例）⇒ 结构改动运行期不可验证；
--   D-FAM9-02 设计器 validate 对"纯串行无档位"与"入口网关默认流直通 End（零审批）"双双放行（errors=0/warnings=0）；
--   D-FAM9-03 招待费特批链接线（缺陷9 20260918）在现存数据上零产出：三张招待费单 5819/5837/5838 全落在 CLAIM；
--   D-FAM9-04 链上岗位"名实不符"（CLAIM_G1 Task_4/Task_5 两例）；
--   D-FAM9-05 无 DOA 主数据表（SHOW TABLES LIKE '%doa%' 空）⇒ 阈值只以字符串散落在各 BPMN，同岗位跨链不一致无法被校验发现；
--   D-FAM9-06 对公付款的"付款"与"账务"不在链上（监听器第 241/257/260/261 行自动产凭证、回写冲销、产 WAITING 付款记录、派作业链），
--     是粗粒度 sharing:task:receive/finish（D-FAM8-04 同源）；
--   R9-01 预付冲销链路（sumPrepayOffsets/writeBackPrepayOffsets）因全库 0 张 PREPAY 单而从未真实执行过。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.34_fam9_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1434_20260923 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7'));

-- 0b) 新 key 插行前体检（期望 0 行；本族为纯 UPDATE，此查询应返回空集）
-- （无新 key，跳过）

-- 1) 逐节点对齐（13 行，含 sort 链序同步）
-- PREPAY.Task_2  扫描单据及影像齐套（作业池）【财务扫描岗】  sort=1
UPDATE wf_node_config
   SET node_name      = '扫描单据及影像齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_2';

-- PREPAY.Task_4  部门经理审核【部门经理】  sort=2
UPDATE wf_node_config
   SET node_name      = '部门经理审核【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_4';

-- PREPAY.Task_7  项目经理/费用承担部门审核【项目经理/费用承担部门负责人】  sort=3
UPDATE wf_node_config
   SET node_name      = '项目经理/费用承担部门审核【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_7';

-- PREPAY.Task_3  审核单据及付款申请【财务审核岗】  sort=4
UPDATE wf_node_config
   SET node_name      = '审核单据及付款申请【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_3';

-- PREPAY.Task_9  基地总经理审核【基地总经理】  sort=5
UPDATE wf_node_config
   SET node_name      = '基地总经理审核【基地总经理】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_9';

-- PREPAY.Task_10  板块财务经理审核【板块财务经理】  sort=6
UPDATE wf_node_config
   SET node_name      = '板块财务经理审核【板块财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 6,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_10';

-- PREPAY.Task_11  板块分管领导审核【板块分管领导】  sort=7
UPDATE wf_node_config
   SET node_name      = '板块分管领导审核【板块分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 7,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_11';

-- PREPAY.Task_12  总裁审批【总裁】  sort=8
UPDATE wf_node_config
   SET node_name      = '总裁审批【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 8,
       update_time    = NOW()
 WHERE model_key = 'PREPAY' AND node_key = 'Task_12';

-- CLAIM_G1.Task_2  部门经理审核业务真实性【部门经理】  sort=1
UPDATE wf_node_config
   SET node_name      = '部门经理审核业务真实性【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'CLAIM_G1' AND node_key = 'Task_2';

-- CLAIM_G1.Task_3  共享审核人均标准及预制凭证【共享会计】  sort=2
UPDATE wf_node_config
   SET node_name      = '共享审核人均标准及预制凭证【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'CLAIM_G1' AND node_key = 'Task_3';

-- CLAIM_G1.Task_4  总监级授权审批【中心/总监级审批档】  sort=3
UPDATE wf_node_config
   SET node_name      = '总监级授权审批【中心/总监级审批档】',
       assign_type    = 'ROLE',
       assign_value   = 'CENTER_APPROVER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'CLAIM_G1' AND node_key = 'Task_4';

-- CLAIM_G1.Task_5  高管决策审批【高管决策审批档】  sort=4
UPDATE wf_node_config
   SET node_name      = '高管决策审批【高管决策审批档】',
       assign_type    = 'ROLE',
       assign_value   = 'EXEC_APPROVER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'CLAIM_G1' AND node_key = 'Task_5';

-- CLAIM_G1.Task_7  审核预制凭证【总账会计】  sort=5
UPDATE wf_node_config
   SET node_name      = '审核预制凭证【总账会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_GL',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'CLAIM_G1' AND node_key = 'Task_7';

-- 2) 新 key 插行（0 行）
-- （本族 13 个在用 node_key 全部沿用原模型 key ⇒ 零 INSERT，批次 2 第一次）
-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT COUNT(*) AS drift_rows_A FROM (
  SELECT e.model_key, e.node_key
    FROM (
    SELECT 'PREPAY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '扫描单据及影像齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理/费用承担部门审核【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '审核单据及付款申请【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审核【基地总经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_10' node_key, 'ROLE' exp_type, 'PLATE_FIN_MANAGER' exp_role, '板块财务经理审核【板块财务经理】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 6 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_11' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审核【板块分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 7 exp_sort
    UNION ALL
    SELECT 'PREPAY' model_key, 'Task_12' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 8 exp_sort
    UNION ALL
    SELECT 'CLAIM_G1' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核业务真实性【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'CLAIM_G1' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '共享审核人均标准及预制凭证【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'CLAIM_G1' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'CENTER_APPROVER' exp_role, '总监级授权审批【中心/总监级审批档】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'CLAIM_G1' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'EXEC_APPROVER' exp_role, '高管决策审批【高管决策审批档】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'CLAIM_G1' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FIN_GL' exp_role, '审核预制凭证【总账会计】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
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
      OR IFNULL(c.sort, -1) <> e.exp_sort
  ) x;

-- 4) 复核 B：本族 13 个岗位的存在性与 co1 在编持岗人数（规则 status='NORMAL' 且 is_admin=0）。
--     单点岗位：BASE_GM/EXEC_APPROVER/DEPT_MANAGER(co1) 各 1 人、PLATE_DIV_LEADER/PLATE_FIN_MANAGER 全租户 2 人
--      → 断流靠 ESCALATE 沿汇报线外兜（永不兜 admin）。角色收敛属 R5（等 RA-01~15 裁决），本轮不增岗。
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('BASE_GM','CENTER_APPROVER','DEPT_MANAGER','EXEC_APPROVER','FIN_AUDIT','FIN_GL','FSSC_AUDITOR','PLATE_DIV_LEADER','PLATE_FIN_MANAGER','PRESIDENT','PROJECT_MANAGER','SCAN_STAFF') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(13) + 出链后的幽灵行（仅盘点，不改；
--    期望 rows_total=20、rows_in_new_bpmn=13，即 PREPAY 13→8、CLAIM_G1 7→5，7 条幽灵行保留）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('PREPAY','CLAIM_G1') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 1 行 POOL）
SELECT COUNT(*) AS pool_rows_D FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7')) AND handling_mode = 'POOL';

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT COUNT(*) AS admin_or_named_rows_E FROM (
  SELECT c.model_key, c.node_key
    FROM wf_node_config c
   WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7'))
     AND ( c.assign_type <> 'ROLE'
        OR c.assign_value REGEXP '^[0-9]+$'
        OR c.assign_value = ''
        OR c.assign_value IS NULL
        OR EXISTS (SELECT 1 FROM sys_user u
                     JOIN sys_user_role ur ON ur.user_id = u.id
                     JOIN sys_role r ON r.id = ur.role_id
                    WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) )
  ) x;

-- 8) 复核 F：非 ROLE/空 assign_value 残留普查（PREPAY.Task_1 原为 assign_type=INITIATOR＋assign_value NULL，
--    该节点已出链成幽灵行；本 seed 触及的 13 行必须全是 ROLE 且非空）期望 0 行
SELECT COUNT(*) AS named_user_rows_F FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7'))
   AND (assign_type <> 'ROLE' OR assign_value IS NULL OR assign_value = '');

-- 8b) 复核 H：§七 R8 超时规则 —— 本族在用行 NOTIFY 占比（期望 0/13）；同时打印含幽灵行的全族占比供对账
SELECT SUM(CASE WHEN timeout_action = 'NOTIFY' THEN 1 ELSE 0 END) AS notify_in_new_bpmn,
       COUNT(*) AS in_new_bpmn_rows,
       SUM(CASE WHEN timeout_action = 'NOTIFY' THEN 1 ELSE 0 END) * 100 DIV COUNT(*) AS pct_H
  FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('PREPAY','Task_2'),('PREPAY','Task_4'),('PREPAY','Task_7'),('PREPAY','Task_3'),('PREPAY','Task_9'),('PREPAY','Task_10'),('PREPAY','Task_11'),('PREPAY','Task_12'),('CLAIM_G1','Task_2'),('CLAIM_G1','Task_3'),('CLAIM_G1','Task_4'),('CLAIM_G1','Task_5'),('CLAIM_G1','Task_7'));

-- 9) 复核 G：在途门槛复述（本 seed 生效期间两模型必须仍为 0 在途；若不为 0，说明有人在旧定义上起了新单，
--    这些实例会停在旧结构里 → 只盘点、不改数据，处置走方案 §8.12 F 节特批流程）
SELECT 'V1.4.34 执行后本族在途（期望 0，非 0 需登记）' AS chk,
       (SELECT COUNT(*) FROM wf_instance WHERE model_key IN ('PREPAY','CLAIM_G1') AND status = 'RUNNING') AS running_from_wf_instance;
