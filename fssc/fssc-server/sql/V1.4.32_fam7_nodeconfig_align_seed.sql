-- V1.4.32  批次2 第七族（档案 FM_ARCHIVE/FM_BORROW/FM_DESTROY/FM_DOC_FLOW）配置侧对齐：wf_node_config <- 已发布 BPMN 唯一真值
-- 前置：4 个模型已 /api/tenant/wf/designer/publish 重发布（FM_ARCHIVE / FM_BORROW / FM_DESTROY / FM_DOC_FLOW），
--   发布前回读复核 4/4 与 draft 逐字节一致（e2e-ui/batch2/fam7-verify.log）、在途实例双规则均为 0
--   （designer/in-flight runningTotal=0 且 staleCount=0；wf_instance 与 ACT_RU_EXECUTION 按 FM% 亦各 0 行）。
-- 结构：本族 4 模型 30 个人工节点 -> 13 个（-17，-57%）；
--   作业池 5 个（handling_mode=POOL）、排他网关 0 个、并联会审 0 组
--   —— **本族是批次 2 六族里唯一"三条手段只剩两条"的一族：DOA 金额网关族级落点为 0**，理由逐模型附 Java 取证：
--     FM_ARCHIVE 走五参 start 但 amount 恒传 null（ArchiveService 第 87~88 行）→ 挂档必假；
--     FM_BORROW / FM_DESTROY 走四参 start（ArchiveAccessService 第 40 行 / ArchiveDestroyService 第 54 行）→ 无 amount 变量；
--     FM_DOC_FLOW 传了 amount（DispatchService 第 100 行）但单据流转不是新增支出授权（同一张单据金额已在报销链批过）→ §5.3 理由②显式不建。
-- 相邻同角色：重造前 13 对（FM_ARCHIVE 档案管理员 Task_3→9 六连、FM_BORROW 档案管理员四连＋普通员工、FM_DESTROY 两处、FM_DOC_FLOW 扫描岗一处）
--   -> 重造后 0 对，且【同一模型内同一角色只出现一次】（build.js 自检断言）；
--   FM_ARCHIVIST（全租户仅 1 人）挂载点 18 -> 3（每模型 ≤1）。
-- 本族另有一条族级判断：档案链的"审批"其实全部是物理作业（扫描/打印/装订/粘贴/上架/出库/移交/归还/清点/销毁），
--   真人控制点原链一个都没有（FM_ARCHIVE 9 道全作业、FM_DOC_FLOW 3 道全作业）→ 本轮不是单纯删节点，
--   而是【把与权限规则相符的岗补成真人控制点】：归档完整性复核(共享主管)、借阅批准(共享会计)、归还核销(共享主管)、监销复核(内审经理)。
-- 改岗清单（BPMN candidateGroups 与旧 assign_value 不同者，共 6 处）：
--   FM_ARCHIVE.Task_2 FIN_GL->FM_ARCHIVIST、FM_ARCHIVE.Task_3 FM_ARCHIVIST->FSSC_SUPERVISOR、FM_BORROW.Task_2 FM_ARCHIVIST->FSSC_AUDITOR、FM_BORROW.Task_8 FM_ARCHIVIST->FSSC_SUPERVISOR、FM_DESTROY.Task_3 BASE_FIN_MANAGER->FSSC_SUPERVISOR、FM_DESTROY.Task_2 FM_ARCHIVIST->INTERNAL_AUDITOR
-- 挂账（配置侧无法自解，须代码/权限侧处置，详见方案 §8.10）：
--  deny_self 在本族全死：op_archive 的用户列是 filing_user_id、archive_access_log 是 requester_id、
--     archive_destroy_approval 是 applicant_id，而 applyColumn() 只特例 tax_refund＋4 张 GL 表、其余查 apply_user_id（本族四张表都没有该列）
--     → resolveApplyUserId() 恒 null → 提单人自审无服务端拦截（前三族同型的第五次暴露；比它们更好修：回退取 wf_instance.start_user_id 即可，无需加列）；
--  公司解析：companyTable 只有 FM_ARCHIVE/FM_BORROW 两条（第 996~997 行），且 FM_BORROW 的 bizId 实为 archive_access_log.id
--     却被指向 op_archive → 串表撞号取到"另一份档案的公司"；FM_DESTROY/FM_DOC_FLOW 无映射，而 archive_destroy_approval/dispatch_task
--     两张表根本没有 company_id 列 → 本族重造后【13 个节点全部是 GROUP 范围角色】，不依赖公司解析（这是规避，不是修复）；
--  销毁有【三条】路径且互不相容（本条为办结时逐行读码后的更正版，初判"两条互相覆写"不准确，见方案 §8.10 G 节）：
--     ① 绕流程一键销毁：POST /archive/{id}/destroy（compliance:archive:destroy，仅授 FM_ARCHIVIST）→ ArchiveService.destroy() 第 96~106 行
--        直写 archive_status=DISPOSED＋destroyed_at＋integrity_status=MISMATCH，【既不经流程、也不做 assertAllExpired() 到期校验】；
--     ② 合规双签：ArchiveDestroyService.approveDestroy() 第 78~120 行（要求 PENDING、拒同人两签、第二签才置 APPROVED）＋ executeDestroy()
--        第 122~159 行（须 APPROVED ∥ second_approver_id 非空 ∥ 复校验到期）；
--     ③ 流程最终状态钩子：ArchiveFacadeImpl.handleDestroyApproval() 第 110~136 行只写 approvalStatus=APPROVED＋approved_at，
--        【不填 second_approver_id/second_approved_at、不校验到期】，且把档案置成 status=DESTROYED（而非 ①② 写的 archive_status=DISPOSED）
--        → 后果不是"覆写"而是【死锁】：流程一旦批完，approvalStatus 已非 PENDING ⇒ approveDestroy() 第 83 行拒绝补第二签，
--          而 executeDestroy() 第 130 行又因缺第二复核人必抛 ⇒ 走完全流程的销毁单【无法合规执行】，只剩 ① 这条绕流程的路能真销毁。
--        借阅链同型但不死锁（grantAccess() 第 76 行只校验 APPROVED）；代价是 approveAccess() 第 57~68 行【无任何状态守卫】，
--        已最终状态可反复改判、REJECTED 可被翻成 APPROVED。
--  链权脱钩：借阅批准权 compliance:archiveaccess:approve 只授 FSSC_AUDITOR/FSSC_SUPERVISOR、销毁批准权
--     compliance:archivedestroy:approve 只授 FSSC_AUDITOR/FSSC_SUPERVISOR/INTERNAL_AUDITOR/ORG_HEAD，两处的持权人都没有 FM_ARCHIVIST，
--     而原链 18 个档案管理员节点里含全部"审核/批准"节点＝页面上无对应按钮（本轮 4 处改岗已按权限规则对齐，权限表本身未动）；
--  先归档后审批、且发起失败不挡业务（ 同型，本族第四次）：ArchiveService.archive 第 73~92 行
--     先把 archive_status 置 ARCHIVED＋固化 integrity_hash＋updateById，之后才 start("FM_ARCHIVE")，整段包在 try/catch 只 log.error
--     ⇒ 归档事实与哈希已定，审批只是事后追认；本族 4 条链（archive/access/destroy/dispatch）的 start() 全部是这种"做完再发起、失败不回滚"，
--     4/4 命中；
--  业务侧审批人身份由请求体自报、未绑登录态：ArchiveAccessController 第 36/45 行与 ArchiveDestroyController 第 37/48 行
--     取 requesterId/approverId/applicantId 都是 req.get(...) 且缺省 0L ⇒ 持批准权者可代人签（双签的"两名不同复核人"只校验 id 不相等，
--     不校验 id 是否就是当前登录人），0L 更是一个不存在的用户 ⇒ deny_self 即便修好也会被 0L 绕过；
--   R7-05 FM_DOC_FLOW 不在 ArchiveBizAction.bizTypes()（只登记 FM_ARCHIVE/FM_DESTROY/FM_BORROW）→ 流程最终状态无任何收尾动作；
--   R7-06 本族 4 个模型 wf_process_config 路由行 0 条，启动完全依赖服务层硬编码 start("FM_*")（与 R5-10/R6 同型，批次 3 裁决）。
-- 档位规则（方案 §5.9）：授权层级(CFO/PRESIDENT) 48h AUTO_REMIND 且永不 AUTO_PASS；共享审单(FSSC_AUDITOR/SCAN_STAFF) 8h；
--   业务审批(共享主管/内审经理/财务专员) 24h；作业池 48h。本族无 BOARD_OFFICE 节点 → 无 168h NOTIFY；empty_strategy 全族 ESCALATE。
-- allow_transfer / multi_type / auto_pass_self / form_perm 一律不动（沿用批次1 定档）。
-- 不删任何数据；回滚见 V1.4.32_fam7_nodeconfig_align_rollback.sql
-- 执行：mysql -uroot -p123456 -h127.0.0.1 -P3306 -Dfssc --default-character-set=utf8mb4 < 本文件

-- 0) 备份将被改动的行（去重：表已存在则跳过，备份表不删）
CREATE TABLE IF NOT EXISTS wf_node_config_bak_V1432_20260922 AS
SELECT * FROM wf_node_config WHERE (model_key, node_key) IN (('FM_ARCHIVE','Task_1'),('FM_ARCHIVE','Task_2'),('FM_ARCHIVE','Task_3'),('FM_BORROW','Task_2'),('FM_BORROW','Task_4'),('FM_BORROW','Task_8'),('FM_DESTROY','Task_3'),('FM_DESTROY','Task_2'),('FM_DESTROY','Task_5'),('FM_DESTROY','Task_6'),('FM_DESTROY','Task_7'),('FM_DOC_FLOW','Task_1'),('FM_DOC_FLOW','Task_3'));

-- 0b) 本族无新 key：13 个节点全部沿用原模型 Task_N，故只有 UPDATE、没有 INSERT。

-- 1) 逐节点对齐（13 行）
-- FM_ARCHIVE.Task_1  扫描影像与条码齐套（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '扫描影像与条码齐套（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_ARCHIVE' AND node_key = 'Task_1';

-- FM_ARCHIVE.Task_2  组卷装订与档案上架登记（作业池）【档案管理员】
UPDATE wf_node_config
   SET node_name      = '组卷装订与档案上架登记（作业池）【档案管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FM_ARCHIVIST',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_ARCHIVE' AND node_key = 'Task_2';

-- FM_ARCHIVE.Task_3  复核归档完整性与调阅范围【共享主管】
UPDATE wf_node_config
   SET node_name      = '复核归档完整性与调阅范围【共享主管】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_SUPERVISOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_ARCHIVE' AND node_key = 'Task_3';

-- FM_BORROW.Task_2  审核借阅必要性与密级范围【共享会计】
UPDATE wf_node_config
   SET node_name      = '审核借阅必要性与密级范围【共享会计】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 8,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_BORROW' AND node_key = 'Task_2';

-- FM_BORROW.Task_4  档案出库、移交与借阅登记（作业池）【档案管理员】
UPDATE wf_node_config
   SET node_name      = '档案出库、移交与借阅登记（作业池）【档案管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FM_ARCHIVIST',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_BORROW' AND node_key = 'Task_4';

-- FM_BORROW.Task_8  确认按期归还并核销借阅台账【共享主管】
UPDATE wf_node_config
   SET node_name      = '确认按期归还并核销借阅台账【共享主管】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_SUPERVISOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_BORROW' AND node_key = 'Task_8';

-- FM_DESTROY.Task_3  共享主管审核账面与档案一致性【共享主管】
UPDATE wf_node_config
   SET node_name      = '共享主管审核账面与档案一致性【共享主管】',
       assign_type    = 'ROLE',
       assign_value   = 'FSSC_SUPERVISOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DESTROY' AND node_key = 'Task_3';

-- FM_DESTROY.Task_2  内审经理监销复核销毁清单与到期合规【内审经理】
UPDATE wf_node_config
   SET node_name      = '内审经理监销复核销毁清单与到期合规【内审经理】',
       assign_type    = 'ROLE',
       assign_value   = 'INTERNAL_AUDITOR',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DESTROY' AND node_key = 'Task_2';

-- FM_DESTROY.Task_5  财务总监审批档案销毁【财务总监】
UPDATE wf_node_config
   SET node_name      = '财务总监审批档案销毁【财务总监】',
       assign_type    = 'ROLE',
       assign_value   = 'CFO',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DESTROY' AND node_key = 'Task_5';

-- FM_DESTROY.Task_6  总裁审批档案销毁【总裁】
UPDATE wf_node_config
   SET node_name      = '总裁审批档案销毁【总裁】',
       assign_type    = 'ROLE',
       assign_value   = 'PRESIDENT',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DESTROY' AND node_key = 'Task_6';

-- FM_DESTROY.Task_7  取出清点与销毁执行并登记监销记录（作业池）【档案管理员】
UPDATE wf_node_config
   SET node_name      = '取出清点与销毁执行并登记监销记录（作业池）【档案管理员】',
       assign_type    = 'ROLE',
       assign_value   = 'FM_ARCHIVIST',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DESTROY' AND node_key = 'Task_7';

-- FM_DOC_FLOW.Task_1  单据扫描与纸质-影像一致性复核（作业池）【财务扫描岗】
UPDATE wf_node_config
   SET node_name      = '单据扫描与纸质-影像一致性复核（作业池）【财务扫描岗】',
       assign_type    = 'ROLE',
       assign_value   = 'SCAN_STAFF',
       handling_mode  = 'POOL',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 48,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DOC_FLOW' AND node_key = 'Task_1';

-- FM_DOC_FLOW.Task_3  整理保管纸质单据并签收流转【财务专员】
UPDATE wf_node_config
   SET node_name      = '整理保管纸质单据并签收流转【财务专员】',
       assign_type    = 'ROLE',
       assign_value   = 'FIN_STAFF',
       handling_mode  = 'AUTO',
       empty_strategy = 'ESCALATE',
       timeout_hours  = 24,
       timeout_action = 'AUTO_REMIND',
       update_time    = NOW()
 WHERE model_key = 'FM_DOC_FLOW' AND node_key = 'Task_3';

-- 2) 新 key 插行（0 行，NOT EXISTS 守卫保证去重）

-- 3) 复核 A：逐节点期望值对账（期望 0 行；输出任一行即为 BPMN 与 config 两处真值漂移）
SELECT COUNT(*) AS drift_rows_A FROM (
  SELECT e.model_key, e.node_key
    FROM (
    SELECT 'FM_ARCHIVE' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '扫描影像与条码齐套（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FM_ARCHIVE' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FM_ARCHIVIST' exp_role, '组卷装订与档案上架登记（作业池）【档案管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FM_ARCHIVE' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FSSC_SUPERVISOR' exp_role, '复核归档完整性与调阅范围【共享主管】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_BORROW' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'FSSC_AUDITOR' exp_role, '审核借阅必要性与密级范围【共享会计】' exp_name, 'ESCALATE' exp_es, 8 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_BORROW' model_key, 'Task_4' node_key, 'ROLE' exp_type, 'FM_ARCHIVIST' exp_role, '档案出库、移交与借阅登记（作业池）【档案管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FM_BORROW' model_key, 'Task_8' node_key, 'ROLE' exp_type, 'FSSC_SUPERVISOR' exp_role, '确认按期归还并核销借阅台账【共享主管】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_DESTROY' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FSSC_SUPERVISOR' exp_role, '共享主管审核账面与档案一致性【共享主管】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_DESTROY' model_key, 'Task_2' node_key, 'ROLE' exp_type, 'INTERNAL_AUDITOR' exp_role, '内审经理监销复核销毁清单与到期合规【内审经理】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_DESTROY' model_key, 'Task_5' node_key, 'ROLE' exp_type, 'CFO' exp_role, '财务总监审批档案销毁【财务总监】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_DESTROY' model_key, 'Task_6' node_key, 'ROLE' exp_type, 'PRESIDENT' exp_role, '总裁审批档案销毁【总裁】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
    UNION ALL
    SELECT 'FM_DESTROY' model_key, 'Task_7' node_key, 'ROLE' exp_type, 'FM_ARCHIVIST' exp_role, '取出清点与销毁执行并登记监销记录（作业池）【档案管理员】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FM_DOC_FLOW' model_key, 'Task_1' node_key, 'ROLE' exp_type, 'SCAN_STAFF' exp_role, '单据扫描与纸质-影像一致性复核（作业池）【财务扫描岗】' exp_name, 'ESCALATE' exp_es, 48 exp_th, 'AUTO_REMIND' exp_ta, 'POOL' exp_hm
    UNION ALL
    SELECT 'FM_DOC_FLOW' model_key, 'Task_3' node_key, 'ROLE' exp_type, 'FIN_STAFF' exp_role, '整理保管纸质单据并签收流转【财务专员】' exp_name, 'ESCALATE' exp_es, 24 exp_th, 'AUTO_REMIND' exp_ta, 'AUTO' exp_hm
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

-- 4) 复核 B：本族 8 个角色的存在性与 co1 在编持岗人数（规则 status='NORMAL' 且 is_admin=0）。
--    本族单点角色：FM_ARCHIVIST 全租户 1 人挂 3 口（三族作业池，均为"只有档案岗能做的物理动作"）、
--      INTERNAL_AUDITOR 1 人、PRESIDENT 1 人、FIN_STAFF 1 人 → 断流只能靠 ESCALATE 沿汇报线外兜；
--      这属于 R5 角色收敛（18→5）范围，等 RA-01~15 裁决，本轮不增岗。
--    复核 C 里 rows_total>rows_in_new_bpmn 的差额＝已出链幽灵行（17 行），不改不删。
--  本族重造后 org_scope=COMPANY 的角色数量为 0（ 的规避结果），故不再有"公司解析失败"这一类风险敞口。
SELECT r.role_code, ANY_VALUE(r.role_name) role_name, ANY_VALUE(r.org_scope) org_scope, ANY_VALUE(r.status) status,
       COUNT(DISTINCT ur.user_id) holders_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 THEN 1 ELSE 0 END) active_total,
       SUM(CASE WHEN u.status = 'NORMAL' AND u.is_admin = 0 AND u.company_id = 1 THEN 1 ELSE 0 END) active_co1
  FROM sys_role r
  LEFT JOIN sys_user_role ur ON ur.role_id = r.id
  LEFT JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
 WHERE r.role_code IN ('CFO','FIN_STAFF','FM_ARCHIVIST','FSSC_AUDITOR','FSSC_SUPERVISOR','INTERNAL_AUDITOR','PRESIDENT','SCAN_STAFF') AND r.del_flag = 0
 GROUP BY r.role_code ORDER BY active_co1, r.role_code;

-- 5) 复核 C：配置行盘点 = 新 BPMN 有效节点(13) + 出链后的幽灵行（仅盘点，不改；期望 rows_in_new_bpmn 合计 13）
SELECT model_key, COUNT(*) rows_total,
       SUM((model_key, node_key) IN (('FM_ARCHIVE','Task_1'),('FM_ARCHIVE','Task_2'),('FM_ARCHIVE','Task_3'),('FM_BORROW','Task_2'),('FM_BORROW','Task_4'),('FM_BORROW','Task_8'),('FM_DESTROY','Task_3'),('FM_DESTROY','Task_2'),('FM_DESTROY','Task_5'),('FM_DESTROY','Task_6'),('FM_DESTROY','Task_7'),('FM_DOC_FLOW','Task_1'),('FM_DOC_FLOW','Task_3'))) rows_in_new_bpmn
  FROM wf_node_config
 WHERE model_key IN ('FM_ARCHIVE','FM_BORROW','FM_DESTROY','FM_DOC_FLOW') AND del_flag = 0
 GROUP BY model_key ORDER BY model_key;

-- 6) 复核 D：作业池档位落地（期望 5 行 POOL）
SELECT COUNT(*) AS pool_rows_D FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('FM_ARCHIVE','Task_1'),('FM_ARCHIVE','Task_2'),('FM_ARCHIVE','Task_3'),('FM_BORROW','Task_2'),('FM_BORROW','Task_4'),('FM_BORROW','Task_8'),('FM_DESTROY','Task_3'),('FM_DESTROY','Task_2'),('FM_DESTROY','Task_5'),('FM_DESTROY','Task_6'),('FM_DESTROY','Task_7'),('FM_DOC_FLOW','Task_1'),('FM_DOC_FLOW','Task_3')) AND handling_mode = 'POOL';

-- 7) 复核 E：零 admin 硬门槛 —— 本族任一节点都不得由 admin 参与；点名残留（assign_value 是纯数字 userId）也算违规
SELECT COUNT(*) AS admin_or_named_rows_E FROM (
  SELECT c.model_key, c.node_key
    FROM wf_node_config c
   WHERE c.del_flag = 0 AND (c.model_key, c.node_key) IN (('FM_ARCHIVE','Task_1'),('FM_ARCHIVE','Task_2'),('FM_ARCHIVE','Task_3'),('FM_BORROW','Task_2'),('FM_BORROW','Task_4'),('FM_BORROW','Task_8'),('FM_DESTROY','Task_3'),('FM_DESTROY','Task_2'),('FM_DESTROY','Task_5'),('FM_DESTROY','Task_6'),('FM_DESTROY','Task_7'),('FM_DOC_FLOW','Task_1'),('FM_DOC_FLOW','Task_3'))
     AND ( c.assign_type <> 'ROLE'
        OR c.assign_value REGEXP '^[0-9]+$'
        OR EXISTS (SELECT 1 FROM sys_user u
                     JOIN sys_user_role ur ON ur.user_id = u.id
                     JOIN sys_role r ON r.id = ur.role_id
                    WHERE u.is_admin = 1 AND u.del_flag = 0 AND r.role_code = c.assign_value) )
  ) x;

-- 8) 复核 F：出链后仍在的【点名 assignee】残留普查（本族原链无 ${approver} 点名节点，此处仍按同一硬门槛证伪；期望 0 行）
SELECT COUNT(*) AS named_user_rows_F FROM wf_node_config
 WHERE del_flag = 0 AND (model_key, node_key) IN (('FM_ARCHIVE','Task_1'),('FM_ARCHIVE','Task_2'),('FM_ARCHIVE','Task_3'),('FM_BORROW','Task_2'),('FM_BORROW','Task_4'),('FM_BORROW','Task_8'),('FM_DESTROY','Task_3'),('FM_DESTROY','Task_2'),('FM_DESTROY','Task_5'),('FM_DESTROY','Task_6'),('FM_DESTROY','Task_7'),('FM_DOC_FLOW','Task_1'),('FM_DOC_FLOW','Task_3')) AND assign_type = 'USER';
