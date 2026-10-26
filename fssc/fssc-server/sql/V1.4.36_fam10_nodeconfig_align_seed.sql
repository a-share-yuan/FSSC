-- V1.4.36  批次2 第十族（事前申请三链＋合同＋主数据两链）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
--   TRAVEL id104 v11→v12 引擎 228→229、TRAIN id105 v10→v11 引擎 228→229、MEETING id189 v1→v2 引擎 122→123、
--   CONTRACT id200 v1→v2 引擎 80→81、CUSTOMER_MAINT id116 v3→v4 引擎 228→229、SUPPLIER_ADD id110 v5→v6 引擎 228→229，
--   fam10-publish.log），发布后回读与 draft 逐字节一致 6/6（fam10-verify.log）、三方 md5 全等
--   （仓内 latest / 库内 wf_model.bpmn_xml / 本地 draft）、在途双规则 0
--   （designer/in-flight runningTotal=0 且 staleCount=0，fam10-publish-check.log），本族 wf_instance 历史实例数亦为 0
--   （fam10-instakes.log ①）⇒ 零回归面，同时零验证面（结构正确性只有 simulate 路径预测做证据）。
--   ★取证自纠（本 seed 上一版头注有三处规则错，按 published.json 原文更正）：① 把 TRAVEL 与 TRAIN 的版本迁移对调了
--   （实为 TRAVEL v11→v12、TRAIN v10→v11）；② CUSTOMER_MAINT 写成 v1→v2，实为 v3→v4（该链此前已有两版）；
--   ③ "引擎"一栏是 procDefId 的第二段＝deployment id，不是流程定义版本（ACT_RE_PROCDEF 里同 key 的 VERSION_ 最高达
--   v229，与本文件的 wf_model.version 序号不是一个规则，前九族注释里的"引擎 228→229"全部按此重读）。
-- 结构：TRAVEL 8→6、TRAIN 9→6、MEETING 8→6、CONTRACT 4→3、CUSTOMER_MAINT 4→2、SUPPLIER_ADD 9→8；
--   族合计 42→31（-11，-26%）。
--   作业池 1 个（SUPPLIER_ADD.Task_1）、排他网关 9 个（三链各 3 档金额授权）、并联会审 1 组。
--   UPDATE=31 行、INSERT=0 行（本族全部沿用原 node_key ⇒ 纯 UPDATE、零插行）。
-- 本族配置侧的五处处置（逐行取证见 gen-sql.js 头注与 build.js 的 note）：
--   ① 改岗 0 处（批次 2 第三次，继第八、九族）：31 个在用 node_key 的 assign_value 与 BPMN candidateGroups 本就一致；
--      本族真正修的是"名实不符"与"链序"，不是岗位。断言 2 已把任何不等做成硬失败。
--   ② sort 同步 21 处（批次 2 第二次）：删提单/归口节点后链序整体前移（TRAIN 的 BASE_GM 由第 7 位→第 4 位），
--      不改 sort 就重演「配置顺序≠图顺序」（[[fssc-wf-nodeconfig-desync]]）；旧 sort 全量留在备份表可还原。
--   ③ handling_mode 改动 1 处：SUPPLIER_ADD.Task_1「录入供应商基础信息并上传资质资料（作业池）【应付会计】」AUTO→POOL。
--      依据＝该节点在 BPMN 名称里已自称作业池，而配置侧仍是 AUTO（不产生可领取任务池待办）＝§2 名实不符的又一例。
--   ④ empty_strategy 改动 1 处（SUPPLIER_ADD.Task_1 ERROR→ESCALATE）：ERROR 的代价是"该公司无此岗 ⇒ 单据当场报错卡死"，
--      而本链 14 个岗位里有 6 个是 COMPANY 范围（DEPT_MANAGER/PROJECT_MANAGER/BASE_GM/PLATE_DIV_LEADER/FIN_MANAGER/
--      PURCHASE_MANAGER，复核 B 实测 org_scope），62 家公司里必然有零在编人的公司；
--      SKIP 又会把"供应商准入必须有资料"变成无人确认 ⇒ 在用行统一 ESCALATE（沿汇报线兜给上级真人，永不兜 admin）。
--      实测改前本族 42 行有 8 行 ERROR（批次 1 定档），其中【在用行仅 1 行】即本条，另 7 行随节点出链原地保留。
--      与批次 1 定档的偏离是有记录的：现库 POOL 47 行 = 48h/ESCALATE×43 ＋ 24h/ESCALATE×1 ＋ 8h/ERROR×3，
--      三条 ERROR 全是收单扫描（CLAIM/CLAIM_TRAVEL/SETTLE 的 Task_2），本节点不属该性质；§5.9 默认处理行原文
--      "ERROR 仅保留给业务上必须报错"。第九族已对 PREPAY.Task_2 做过同向改动（ERROR→ESCALATE）。
--   ⑤ timeout_action：本族在用 31 行 NOTIFY 由 31 行（100%，与第九族并列批次 2 最大单项违规）
--      降为 0 行 ⇒ §七 R8（≤30%）达族级规则；实测改前全族 42 行 42 条 NOTIFY（100%），
--      改后含 11 条出链幽灵行（仍保留 NOTIFY）为 11/42＝26%，仍达标。
--      时限按 §5.9 由"节点性质"推导：作业池/授权档 48h（DIV_LEADER 与 BASE_GM/PLATE_DIV_LEADER/PRESIDENT 同档，
--      故 CONTRACT.Task_4 与 SUPPLIER_ADD.Task_7 由 24h→48h）、总监/中心级 PURCHASE_DIRECTOR 与 RND_CENTER_DIRECTOR 同档保持 48h、
--      共享审单 FIN_AUDIT/FIN_AR 8h、业务审批 24h，全部 AUTO_REMIND、零 AUTO_PASS（白名单 wf.timeout.autopass.nodes 仍为空）。
--      时限改动 10 处：TRAVEL.Task_6 24h→48h、TRAIN.Task_7 24h→48h、MEETING.Task_6 24h→48h、CONTRACT.Task_2 24h→8h、CONTRACT.Task_4 24h→48h、CUSTOMER_MAINT.Task_3 48h→24h、SUPPLIER_ADD.Task_1 8h→48h、SUPPLIER_ADD.Task_2 8h→24h、SUPPLIER_ADD.Task_7 24h→48h、SUPPLIER_ADD.Task_8 24h→48h
--   ★ R9-04 回归校验（第九族办结发现的规则缺口）：本产出器的 handling_mode 不再硬编码 POOL/AUTO 两值，
--      改前是 PICK 且非作业池的行【原样保留 PICK】。本族在用 31 行改前 PICK=0（全库 12 行 PICK 均在
--      CLAIM/CLAIM_TRAVEL/SETTLE/LOAN/PREPAY）⇒ 本 seed 降档数 0；断言 3 与复核 I 各证伪一次。
-- 岗名对齐清单（node_name 改动 29 处）：TRAVEL.Task_3「部门经理审核【部门经理】」→「部门经理审核业务必要性及归口安排【部门经理】」、TRAVEL.Task_4「项目经理审核【项目经理/费用承担部门负责人】」→「项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】」、TRAVEL.Task_2「审核申请单【财务审核岗】」→「财务审核岗审核申请单及标准合规性【财务审核岗】」、TRAVEL.Task_6「基地总经理审核【基地总经理】」→「基地总经理审批【基地总经理·20万以上】」、TRAVEL.Task_7「板块分管领导审核【板块分管领导】」→「板块分管领导审批【板块分管领导·50万以上】」、TRAVEL.Task_8「总裁审批【总裁】」→「总裁审批【总裁·500万以上】」、TRAIN.Task_3「部门经理审核【部门经理】」→「部门经理审核业务必要性及归口安排【部门经理】」、TRAIN.Task_4「项目经理审核【项目经理/费用承担部门负责人】」→「项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】」、TRAIN.Task_2「审核申请单【财务审核岗】」→「财务审核岗审核申请单及标准合规性【财务审核岗】」、TRAIN.Task_7「基地总经理审核【基地总经理】」→「基地总经理审批【基地总经理·20万以上】」、TRAIN.Task_8「板块分管领导审核【板块分管领导】」→「板块分管领导审批【板块分管领导·50万以上】」、TRAIN.Task_9「总裁审批【总裁】」→「总裁审批【总裁·500万以上】」、MEETING.Task_3「部门经理审核【部门经理】」→「部门经理审核业务必要性及归口安排【部门经理】」、MEETING.Task_4「项目经理审核【项目经理/费用承担部门负责人】」→「项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】」、MEETING.Task_2「审核申请单【财务审核岗】」→「财务审核岗审核申请单及标准合规性【财务审核岗】」、MEETING.Task_6「基地总经理审核【基地总经理】」→「基地总经理审批【基地总经理·20万以上】」、MEETING.Task_7「板块分管领导审核【板块分管领导】」→「板块分管领导审批【板块分管领导·50万以上】」、MEETING.Task_8「总裁审批【总裁】」→「总裁审批【总裁·500万以上】」、CONTRACT.Task_3「部门经理审核【部门经理】」→「部门经理审核业务真实性【部门经理】」、CONTRACT.Task_2「法务审核【财务审核岗】」→「合同合规审核（条款/金额/相对方）【财务审核岗】」、CONTRACT.Task_4「分管领导审批【分管领导】」→「分管领导授权审批【分管领导】」、CUSTOMER_MAINT.Task_3「审核主数据信息的完整性和准确性【部门经理】」→「部门经理审核客户档案变更必要性【部门经理】」、CUSTOMER_MAINT.Task_2「审核主数据信息的完整性和准确性【应收会计】」→「应收会计审核客户主数据完整性与唯一性【应收会计】」、SUPPLIER_ADD.Task_1「录入供应商基础信息并上传资料【应付会计】」→「录入供应商基础信息并上传资质资料（作业池）【应付会计】」、SUPPLIER_ADD.Task_2「供应商准入申请【采购经理】」→「采购经理审核准入申请【采购经理】」、SUPPLIER_ADD.Task_4「研发设计中心总监审核【研发设计中心总监】」→「研发设计中心总监审核技术资质【研发设计中心总监】」、SUPPLIER_ADD.Task_5「财务经理审核【财务经理】」→「财务经理审核财务资信【财务经理】」、SUPPLIER_ADD.Task_6「企业管理部经理审核【企业管理部经理】」→「企业管理部经理审核合规准入【企业管理部经理】」、SUPPLIER_ADD.Task_8「总裁终审(准入/退出)【总裁】」→「总裁终审（准入/退出）【总裁】」
-- 出链幽灵行（11 行，不删不改，仅盘点）：CONTRACT.Task_1 / CUSTOMER_MAINT.Task_1 / CUSTOMER_MAINT.Task_4 / MEETING.Task_1 / MEETING.Task_5 / SUPPLIER_ADD.Task_9 / TRAIN.Task_1 / TRAIN.Task_5 / TRAIN.Task_6 / TRAVEL.Task_1 / TRAVEL.Task_5
--   —— 保留理由：增量原则（不删数据）＋ BPMN 若回滚到 v10/v11/v1/v5 结构，配置侧须能原样恢复；
--      引擎按 BPMN 节点反查配置，不再读取它们。其中 MEETING/TRAIN/TRAVEL 的 Task_1 是本族清零的
--      最后 3 条 ${approver} 点名牌照节点（配置侧 assign_type=INITIATOR、assign_value=NULL），
--      现库 wf_model 含 ${approver} 的行数已为 0（fam10-redline.log E 段）。
-- 挂账（配置侧无法自解，详见方案 §8.13 C 节）：
--   D-FAM10-01 合同链结构性空转：ContContractService.submit() 起流时不传 amount ⇒ 引擎恒置 0
--     （WfEngineFacade.start 第 ~214 行 amount==null?ZERO:amount），本族 CONTRACT 无金额网关因此不受影响，
--     但"合同审批"与 /cont/contract/{id}/approve 直通接口并存 ⇒ 链不是唯一出口。
--   D-FAM10-06 CONTRACT/CUSTOMER_MAINT/SUPPLIER_ADD 三个 bizType 不在 NodeOrgResolver.companyTable() 白名单
--     ⇒ 链上 6 个 COMPANY 范围岗位（DEPT_MANAGER/PROJECT_MANAGER/BASE_GM/PLATE_DIV_LEADER/FIN_MANAGER/PURCHASE_MANAGER）
--     取人落空；因 canOperate 在单据公司为 null 时放行，后果是**候选面扩大到全租户**而非死锁（＝范围失控，比死锁更难发现）。
--     与 D-FAM2-03 同源（该缺陷的白名单缺口本族再补 3 个 bizType ⇒ 累计 8 个）。
--   D-FAM10-07 主数据收尾写库无人读：MasterDataBizAction 把 md_supplier.status / crm_customer.status 写成 'ACTIVE'，
--     而两个 CRUD Service 与全部 mapper XML 只写/读 'NORMAL'/'DISABLED'（SupplierService:54,79,103、CustomerService:39,68）
--     ⇒ 同一列两套状态词汇；供应商准入"通过"后若有人点一次启用/停用，状态被覆回 NORMAL，审批结果消失。
--     实测现库 md_supplier=NORMAL 54/ACTIVE 7、crm_customer=NORMAL 8/ACTIVE 17，**PENDING 一行都没有**
--     ⇒ 该动作注释宣称的"PENDING → ACTIVE"在现存数据上零样本。
--   D-FAM10-08 收尾失败静默：MasterDataBizAction 第 67~69 行 catch 后只 log.error ⇒ 实例已 APPROVED 而主数据未翻状态，
--     无重试、无告警、无补偿入口。
--   D-FAM10-09 三链（MEETING/TRAIN/TRAVEL）的 3 档金额网关**没有任何历史数据触达高档**：
--     实测 11 张 APPLY 单行合计最大 ¥24,300（全部 <5万）、TRAVEL/TRAIN 触达 20万/50万/500万 三档均为 0 次
--     （fam10-instakes.log ②b）⇒ 新增的 BASE_GM/PLATE_DIV_LEADER/PRESIDENT 三道在演示账套上是纯设计证据。
--   D-FAM10-10 bg_scheme 无金额维度、ISSUED 可绕过审批直接下达；GL6-7/GL8/OTC06/PTP02-02 四链本轮只做 §5.10 检查未发布
--     （2 节点链已证明引擎合法，业务授权仍待裁决）。
--   D-FAM10-11 DIV_LEADER（分管领导）co1 在编 0 人：批次 2 十族里第一个"链上岗位在演示公司无人可派"的情形；
--     因该岗是 GROUP 范围不按公司过滤，后果是**跨公司代签**（co32 孙海燕 / co35 何文青 签 co1 的合同与供应商准入），
--     而不是死锁——比死锁更难被发现。复核 B 的 active_co1 列即证据（fam10-role-holders.log）。
--   D-FAM10-12 供应商准入链 2/8 道由"模拟"账号独占：FIN_AP＝3933 应付审核模拟(FIN_AP)（本轮新转的作业池首节点）、
--     RND_CENTER_DIRECTOR＝3943 研发设计中心总监(模拟)——历次运营演练造账号遗留，真人覆盖为 0 的岗位挂在生产链上。
--     与 D-FAM10-11 同属 R5 角色收敛（RA 系列）范围，本轮不增岗、不改授权。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.36_fam10_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1436_20260923 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'));

-- 1) 逐节点对齐（31 行，含 sort 链序同步；handling_mode 保留 PICK 分支见复核 I）
-- TRAVEL.Task_3  部门经理审核业务必要性及归口安排【部门经理】  DEPT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=1
UPDATE wf_node_config
   SET node_name      = '部门经理审核业务必要性及归口安排【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_3';

-- TRAVEL.Task_4  项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】  PROJECT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_4';

-- TRAVEL.Task_2  财务审核岗审核申请单及标准合规性【财务审核岗】  FIN_AUDIT  8h AUTO_REMIND ESCALATE AUTO sort=3
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核申请单及标准合规性【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_2';

-- TRAVEL.Task_6  基地总经理审批【基地总经理·20万以上】  BASE_GM  48h AUTO_REMIND ESCALATE AUTO sort=4
UPDATE wf_node_config
   SET node_name      = '基地总经理审批【基地总经理·20万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_6';

-- TRAVEL.Task_7  板块分管领导审批【板块分管领导·50万以上】  PLATE_DIV_LEADER  48h AUTO_REMIND ESCALATE AUTO sort=5
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批【板块分管领导·50万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_7';

-- TRAVEL.Task_8  总裁审批【总裁·500万以上】  PRESIDENT  48h AUTO_REMIND ESCALATE AUTO sort=6
UPDATE wf_node_config
   SET node_name      = '总裁审批【总裁·500万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 6,
       update_time    = NOW()
 WHERE model_key = 'TRAVEL' AND node_key = 'Task_8';

-- TRAIN.Task_3  部门经理审核业务必要性及归口安排【部门经理】  DEPT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=1
UPDATE wf_node_config
   SET node_name      = '部门经理审核业务必要性及归口安排【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_3';

-- TRAIN.Task_4  项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】  PROJECT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_4';

-- TRAIN.Task_2  财务审核岗审核申请单及标准合规性【财务审核岗】  FIN_AUDIT  8h AUTO_REMIND ESCALATE AUTO sort=3
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核申请单及标准合规性【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_2';

-- TRAIN.Task_7  基地总经理审批【基地总经理·20万以上】  BASE_GM  48h AUTO_REMIND ESCALATE AUTO sort=4
UPDATE wf_node_config
   SET node_name      = '基地总经理审批【基地总经理·20万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_7';

-- TRAIN.Task_8  板块分管领导审批【板块分管领导·50万以上】  PLATE_DIV_LEADER  48h AUTO_REMIND ESCALATE AUTO sort=5
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批【板块分管领导·50万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_8';

-- TRAIN.Task_9  总裁审批【总裁·500万以上】  PRESIDENT  48h AUTO_REMIND ESCALATE AUTO sort=6
UPDATE wf_node_config
   SET node_name      = '总裁审批【总裁·500万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 6,
       update_time    = NOW()
 WHERE model_key = 'TRAIN' AND node_key = 'Task_9';

-- MEETING.Task_3  部门经理审核业务必要性及归口安排【部门经理】  DEPT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=1
UPDATE wf_node_config
   SET node_name      = '部门经理审核业务必要性及归口安排【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_3';

-- MEETING.Task_4  项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】  PROJECT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】',
       assign_type    = 'ROLE',
       assign_value   = 'PROJECT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_4';

-- MEETING.Task_2  财务审核岗审核申请单及标准合规性【财务审核岗】  FIN_AUDIT  8h AUTO_REMIND ESCALATE AUTO sort=3
UPDATE wf_node_config
   SET node_name      = '财务审核岗审核申请单及标准合规性【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_2';

-- MEETING.Task_6  基地总经理审批【基地总经理·20万以上】  BASE_GM  48h AUTO_REMIND ESCALATE AUTO sort=4
UPDATE wf_node_config
   SET node_name      = '基地总经理审批【基地总经理·20万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'BASE_GM',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_6';

-- MEETING.Task_7  板块分管领导审批【板块分管领导·50万以上】  PLATE_DIV_LEADER  48h AUTO_REMIND ESCALATE AUTO sort=5
UPDATE wf_node_config
   SET node_name      = '板块分管领导审批【板块分管领导·50万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PLATE_DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_7';

-- MEETING.Task_8  总裁审批【总裁·500万以上】  PRESIDENT  48h AUTO_REMIND ESCALATE AUTO sort=6
UPDATE wf_node_config
   SET node_name      = '总裁审批【总裁·500万以上】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 6,
       update_time    = NOW()
 WHERE model_key = 'MEETING' AND node_key = 'Task_8';

-- CONTRACT.Task_3  部门经理审核业务真实性【部门经理】  DEPT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=1
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
 WHERE model_key = 'CONTRACT' AND node_key = 'Task_3';

-- CONTRACT.Task_2  合同合规审核（条款/金额/相对方）【财务审核岗】  FIN_AUDIT  8h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '合同合规审核（条款/金额/相对方）【财务审核岗】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AUDIT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'CONTRACT' AND node_key = 'Task_2';

-- CONTRACT.Task_4  分管领导授权审批【分管领导】  DIV_LEADER  48h AUTO_REMIND ESCALATE AUTO sort=3
UPDATE wf_node_config
   SET node_name      = '分管领导授权审批【分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'CONTRACT' AND node_key = 'Task_4';

-- CUSTOMER_MAINT.Task_3  部门经理审核客户档案变更必要性【部门经理】  DEPT_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=1
UPDATE wf_node_config
   SET node_name      = '部门经理审核客户档案变更必要性【部门经理】',
       assign_type    = 'ROLE',
       assign_value   = 'DEPT_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'CUSTOMER_MAINT' AND node_key = 'Task_3';

-- CUSTOMER_MAINT.Task_2  应收会计审核客户主数据完整性与唯一性【应收会计】  FIN_AR  8h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '应收会计审核客户主数据完整性与唯一性【应收会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'CUSTOMER_MAINT' AND node_key = 'Task_2';

-- SUPPLIER_ADD.Task_1  录入供应商基础信息并上传资质资料（作业池）【应付会计】  FIN_AP  48h AUTO_REMIND ESCALATE POOL sort=1
UPDATE wf_node_config
   SET node_name      = '录入供应商基础信息并上传资质资料（作业池）【应付会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_AP',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 1,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_1';

-- SUPPLIER_ADD.Task_2  采购经理审核准入申请【采购经理】  PURCHASE_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=2
UPDATE wf_node_config
   SET node_name      = '采购经理审核准入申请【采购经理】',
       assign_type    = 'ROLE',
       assign_value   = 'PURCHASE_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 2,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_2';

-- SUPPLIER_ADD.Task_3  采购管理部总监审核【采购总监】  PURCHASE_DIRECTOR  48h AUTO_REMIND ESCALATE AUTO sort=3
UPDATE wf_node_config
   SET node_name      = '采购管理部总监审核【采购总监】',
       assign_type    = 'ROLE',
       assign_value   = 'PURCHASE_DIRECTOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 3,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_3';

-- SUPPLIER_ADD.Task_4  研发设计中心总监审核技术资质【研发设计中心总监】  RND_CENTER_DIRECTOR  48h AUTO_REMIND ESCALATE AUTO sort=4
UPDATE wf_node_config
   SET node_name      = '研发设计中心总监审核技术资质【研发设计中心总监】',
       assign_type    = 'ROLE',
       assign_value   = 'RND_CENTER_DIRECTOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 4,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_4';

-- SUPPLIER_ADD.Task_5  财务经理审核财务资信【财务经理】  FIN_MANAGER  24h AUTO_REMIND ESCALATE AUTO sort=5
UPDATE wf_node_config
   SET node_name      = '财务经理审核财务资信【财务经理】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_MANAGER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 5,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_5';

-- SUPPLIER_ADD.Task_6  企业管理部经理审核合规准入【企业管理部经理】  ENT_MGMT_MGR  24h AUTO_REMIND ESCALATE AUTO sort=6
UPDATE wf_node_config
   SET node_name      = '企业管理部经理审核合规准入【企业管理部经理】',
       assign_type    = 'ROLE',
       assign_value   = 'ENT_MGMT_MGR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       sort           = 6,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_6';

-- SUPPLIER_ADD.Task_7  分管领导审批【分管领导】  DIV_LEADER  48h AUTO_REMIND ESCALATE AUTO sort=7
UPDATE wf_node_config
   SET node_name      = '分管领导审批【分管领导】',
       assign_type    = 'ROLE',
       assign_value   = 'DIV_LEADER',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 7,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_7';

-- SUPPLIER_ADD.Task_8  总裁终审（准入/退出）【总裁】  PRESIDENT  48h AUTO_REMIND ESCALATE AUTO sort=8
UPDATE wf_node_config
   SET node_name      = '总裁终审（准入/退出）【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       sort           = 8,
       update_time    = NOW()
 WHERE model_key = 'SUPPLIER_ADD' AND node_key = 'Task_8';

-- 2) 新 key 插行（0 行）
-- （本族 31 个在用 node_key 全部沿用原模型 key ⇒ 零 INSERT，与第九族同为纯 UPDATE 包）

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT COUNT(*) AS drift_rows_A FROM (
  SELECT e.model_key, e.node_key
    FROM (
    SELECT 'TRAVEL' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核业务必要性及归口安排【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'TRAVEL' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'TRAVEL' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核申请单及标准合规性【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'TRAVEL' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批【基地总经理·20万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'TRAVEL' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批【板块分管领导·50万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
    UNION ALL
    SELECT 'TRAVEL' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批【总裁·500万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 6 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核业务必要性及归口安排【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核申请单及标准合规性【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批【基地总经理·20万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批【板块分管领导·50万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
    UNION ALL
    SELECT 'TRAIN' model_key, 'Task_9' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批【总裁·500万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 6 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核业务必要性及归口安排【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'PROJECT_MANAGER' exp_role, '项目经理/费用承担部门审核预算【项目经理/费用承担部门负责人】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '财务审核岗审核申请单及标准合规性【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'BASE_GM' exp_role, '基地总经理审批【基地总经理·20万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'PLATE_DIV_LEADER' exp_role, '板块分管领导审批【板块分管领导·50万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
    UNION ALL
    SELECT 'MEETING' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批【总裁·500万以上】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 6 exp_sort
    UNION ALL
    SELECT 'CONTRACT' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核业务真实性【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'CONTRACT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AUDIT' exp_role, '合同合规审核（条款/金额/相对方）【财务审核岗】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'CONTRACT' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'DIV_LEADER' exp_role, '分管领导授权审批【分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'CUSTOMER_MAINT' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'DEPT_MANAGER' exp_role, '部门经理审核客户档案变更必要性【部门经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'CUSTOMER_MAINT' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FIN_AR' exp_role, '应收会计审核客户主数据完整性与唯一性【应收会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'FIN_AP' exp_role, '录入供应商基础信息并上传资质资料（作业池）【应付会计】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm, 1 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'PURCHASE_MANAGER' exp_role, '采购经理审核准入申请【采购经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 2 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'PURCHASE_DIRECTOR' exp_role, '采购管理部总监审核【采购总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 3 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'RND_CENTER_DIRECTOR' exp_role, '研发设计中心总监审核技术资质【研发设计中心总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 4 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'FIN_MANAGER' exp_role, '财务经理审核财务资信【财务经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 5 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'ENT_MGMT_MGR' exp_role, '企业管理部经理审核合规准入【企业管理部经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 6 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'DIV_LEADER' exp_role, '分管领导审批【分管领导】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 7 exp_sort
    UNION ALL
    SELECT 'SUPPLIER_ADD' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁终审（准入/退出）【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm, 8 exp_sort
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

-- 4) 复核 B：本族 14 个岗位的存在性与 co1 在编持岗人数（规则 status='NORMAL' 且 is_admin=0）。
--     批次 2 首次出现"co1 在编 0 人"的岗位：DIV_LEADER（分管领导，GROUP 范围）2 名持岗人分别在 co32/co35，
--      co1 无在编分管领导 ⇒ CONTRACT.Task_4 与 SUPPLIER_ADD.Task_7 两道在 co1 单上**必然由外公司人签**
--      （GROUP 范围不按公司过滤，不是死锁而是跨公司代签，登记为 D-FAM10-11）；
--     两名"模拟"账号独占链上岗位：FIN_AP＝3933 应付审核模拟(FIN_AP)、RND_CENTER_DIRECTOR＝3943 研发设计中心总监(模拟)
--      ⇒ 供应商准入链的 8 道里有 2 道（含新转的作业池首节点）没有真实在编人，登记为 D-FAM10-12；
--    · 其余单点岗（co1 各 1 人）：BASE_GM/PRESIDENT/FIN_AR/FIN_MANAGER/PURCHASE_MANAGER/ENT_MGMT_MGR/DEPT_MANAGER/PROJECT_MANAGER，
--      断流靠 ESCALATE 沿汇报线外兜（永不兜 admin）。角色收敛属 R5（等 RA-01~15 裁决），本轮不增岗。
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('BASE_GM','DEPT_MANAGER','DIV_LEADER','ENT_MGMT_MGR','FIN_AP','FIN_AR','FIN_AUDIT','FIN_MANAGER','PLATE_DIV_LEADER','PRESIDENT','PROJECT_MANAGER','PURCHASE_DIRECTOR','PURCHASE_MANAGER','RND_CENTER_DIRECTOR') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(31) + 出链后的幽灵行（仅盘点，不改；
--    期望 rows_total=42、rows_in_new_bpmn=31，即 11 条幽灵行保留）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('TRAVEL','TRAIN','MEETING','CONTRACT','CUSTOMER_MAINT','SUPPLIER_ADD') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 1 行 POOL，且全部 48h/ESCALATE/AUTO_REMIND）
SELECT handling_mode, COUNT(*) cnt, GROUP_CONCAT(CONCAT(model_key,'.',node_key) SEPARATOR ' ') nodes
  FROM wf_node_config WHERE del_flag = 0 AND (model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'))
 GROUP BY handling_mode ORDER BY handling_mode;

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT COUNT(*) AS admin_or_named_rows_E FROM (
  SELECT c.model_key, c.node_key
    FROM wf_node_config c
   WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'))
     AND ( c.assign_type <> 'ROLE'
        OR c.assign_value REGEXP '^[0-9]+$'
        OR c.assign_value = ''
        OR c.assign_value IS NULL
        OR EXISTS (SELECT 1 FROM sys_user u
                     JOIN sys_user_role ur ON ur.user_id = u.id
                     JOIN sys_role r ON r.id = ur.role_id
                    WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) )
  ) x;

-- 8) 复核 F：非 ROLE/空 assign_value 残留普查（三条提单节点原为 assign_type=INITIATOR＋assign_value NULL，
--    已随节点出链成幽灵行；本 seed 触及的 31 行必须全是 ROLE 且非空）期望 0 行
SELECT COUNT(*) AS named_user_rows_F FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'))
   AND (assign_type <> 'ROLE' OR assign_value IS NULL OR assign_value = '');

-- 8b) 复核 H：§七 R8 超时规则 —— 本族在用行 NOTIFY 占比（期望 0/31）；同时打印含幽灵行的全族占比供对账
SELECT SUM(CASE WHEN timeout_action = 'NOTIFY' THEN 1 ELSE 0 END) AS notify_in_new_bpmn,
       COUNT(*) AS in_new_bpmn_rows,
       SUM(CASE WHEN timeout_action = 'NOTIFY' THEN 1 ELSE 0 END) * 100 DIV COUNT(*) AS pct_H
  FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'));

-- 8c) 复核 I：R9-04 回归校验（SQL 独立规则）——"改前是 PICK、改后不是 PICK"的行数必须为 0，
--     与 gen-sql.js 断言 3 互为独立证伪；顺带打印本族在用行 handling_mode 现值分布。
SELECT COUNT(*) AS pick_demoted_rows_I FROM wf_node_config c
  JOIN wf_node_config_bak_V1436_20260923 b
    ON b.model_key = c.model_key AND b.node_key = c.node_key
 WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8'))
   AND IFNULL(b.handling_mode, '') = 'PICK' AND IFNULL(c.handling_mode, '') <> 'PICK';

SELECT GROUP_CONCAT(CONCAT(handling_mode,'=',cnt) ORDER BY cnt DESC) AS hm_dist_I FROM (
  SELECT handling_mode, COUNT(*) cnt FROM wf_node_config
   WHERE del_flag = 0 AND (model_key, node_key) IN (('TRAVEL','Task_3'),('TRAVEL','Task_4'),('TRAVEL','Task_2'),('TRAVEL','Task_6'),('TRAVEL','Task_7'),('TRAVEL','Task_8'),('TRAIN','Task_3'),('TRAIN','Task_4'),('TRAIN','Task_2'),('TRAIN','Task_7'),('TRAIN','Task_8'),('TRAIN','Task_9'),('MEETING','Task_3'),('MEETING','Task_4'),('MEETING','Task_2'),('MEETING','Task_6'),('MEETING','Task_7'),('MEETING','Task_8'),('CONTRACT','Task_3'),('CONTRACT','Task_2'),('CONTRACT','Task_4'),('CUSTOMER_MAINT','Task_3'),('CUSTOMER_MAINT','Task_2'),('SUPPLIER_ADD','Task_1'),('SUPPLIER_ADD','Task_2'),('SUPPLIER_ADD','Task_3'),('SUPPLIER_ADD','Task_4'),('SUPPLIER_ADD','Task_5'),('SUPPLIER_ADD','Task_6'),('SUPPLIER_ADD','Task_7'),('SUPPLIER_ADD','Task_8')) GROUP BY handling_mode) t;

-- 9) 复核 G：在途门槛复述（本 seed 生效期间六模型必须仍为 0 在途；若不为 0，说明有人在旧定义上起了新单，
--    这些实例会停在旧结构里 → 只盘点、不改数据，处置走方案 §8.13 F 节特批流程）
SELECT 'V1.4.36 执行后本族在途（期望 0，非 0 需登记）' AS chk,
       (SELECT COUNT(*) FROM wf_instance WHERE model_key IN ('TRAVEL','TRAIN','MEETING','CONTRACT','CUSTOMER_MAINT','SUPPLIER_ADD') AND status = 'RUNNING') AS running_from_wf_instance;
