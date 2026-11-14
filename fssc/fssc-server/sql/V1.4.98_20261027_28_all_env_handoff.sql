-- =============================================================================
-- 跨环境执行汇总件  2026-10-27 / 2026-10-28 两窗（费用报销册 F 册 ＋ 其它业务流程册 H 册）
-- 版本名 V1.4.98；正本目录＝Ashare/fssc/fssc-server/sql
-- -----------------------------------------------------------------------------
-- 覆盖的库面变更（全部为「配置/授权/标签」面，本两窗禁止无表结构变更）：
--  §1 D-F1 补「审批工作台」菜单入口（原路由在册、菜单零行 ⇒ 左侧栏看不到）
--   §2 V1.4.97   给该行补图标（前端 v-if="n.icon"，值为空则整枚图标不输出）
--  §3 D-F1 补授「前序审批人」列取数点 wf:history:list（菜单给了就必须一并给读权）
--  §4 D-F1 停用与金额分档设计冲突的 AUTO_REJECT 审单规则
--  §5 D-F1 建「智能审单规则」菜单 ＋ 一并授 policy:list 取数点
--   §6 H5.7      给资产核算岗补「共享凭证生成」功能点（折旧计提批【发起】按钮的门控码）
--  §7 D-F1 两枚零消费方参数改标签（禁止补消费方＝改变业务拦截口径）
--
-- 执行方式：mysql -h <host> -P <port> -u <user> -p <目标库名> < 本件
--   不要把本件里的表名改成带库名前缀——目标环境的库名由命令行给；
--     OCR 那两列在另一套库（fssc_image），本件不含 DDL，见末尾「前置依赖检查」。
--
-- 本件【不含代码变更】。下列修复只在应用侧，必须随 jar／前端发布，不是 SQL：
--   后端（本仓库 jar md5＝68b4c4946510，92,853,317 B，2026-10-28 07:04 出包）
--  1) PurReturnService.java:152 编辑保存改为服务端重算税额
--  2) SaleReturnService.java deriveTaxAmount 改含税派生＋对账腿落数口径
--  3) BillArchiveDTO ＋ BillFacadeImpl 补 totalAmount／approveTime 两字段映射
--  4) BankReconcileService.saveReconItems insert 前按三元组现读复用
--     5) SharingService.undoAfterAutoReject() ＋ VoucherService.draftVoucherIdsOfBill()
--  ＋ ExpenseBillService.submit 清上一轮驳回意见（D-F1；端到端正证禁止未做）
--  6) ExpenseBillService.replaceAttaches 回退误判去重（D-F1 撤销）
--   前端（vite 直读 src／需重新构建产物时按发布流程）
--  7) views/returns/PurchaseReturn.vue:88、views/returns/SalesReturn.vue:82 数量列重算
--  8) views/returns/SalesReturn.vue:252 derivedTax 改价内税
--  9) views/image/OcrRecords.vue:26/29 空值显「—」
--  10) components/bill/FlowProgressPanel.vue 进度卡自动重试（D-F1；端到端正证禁止未做）
--
-- 定位纪律：本件一律走业务键（menu_name/path/perms、role_code、config_key、rule_code），
--   禁止按 id——id 是各环境自增的（本仓库参考值：菜单 3916/3917，角色 99031/99011/99007，
--   权限点 3295/3301/3357/3364，父项 3390/3550；只作对照，禁止当条件）。
--
-- §5/§6/§7 三处是「配置改动」，上线前须书面批准（签字栏留空＝代判≠代签）。
--   本件禁止删除任何业务数据、禁止动主数据、禁止动流程定义、禁止改 sys_config 的取值（只改标签）。
--
-- 生效方式：RBAC 权限集在**登录时装配** ⇒ 受影响账号必须退出重登才会看到新菜单/新按钮。
-- =============================================================================

-- 先声明连接字符集：本件按中文菜单名/参数名定位，客户端若以 latin1 送出 UTF-8 字节，
--   WHERE menu_name='审批工作台' 会静默匹配 0 行＝看着执行成功、实际没改。
SET NAMES utf8mb4;


-- -----------------------------------------------------------------------------
-- 第 0 节  前置复点（只读。先跑这一段，四条键都要＝1 才继续）
-- -----------------------------------------------------------------------------
SELECT 'p0_task_perm_rows'   k, COUNT(*) v FROM sys_menu WHERE menu_type='F' AND perms='wf:task:list'        AND del_flag=0
UNION ALL SELECT 'p0_hist_perm_rows',  COUNT(*) FROM sys_menu WHERE menu_type='F' AND perms='wf:history:list' AND del_flag=0
UNION ALL SELECT 'p0_policy_perm_rows',COUNT(*) FROM sys_menu WHERE menu_type='F' AND perms='policy:list'     AND del_flag=0
UNION ALL SELECT 'p0_voucher_perm_rows',COUNT(*) FROM sys_menu WHERE menu_type='F' AND perms='sharing:voucher:generate' AND del_flag=0
UNION ALL SELECT 'p0_parent_flow_center',COUNT(*) FROM sys_menu WHERE menu_type='M' AND menu_name='流程中心'  AND del_flag=0
UNION ALL SELECT 'p0_parent_sys_admin',  COUNT(*) FROM sys_menu WHERE menu_type='M' AND menu_name='系统管理'  AND del_flag=0
UNION ALL SELECT 'p0_sibling_expense_std',COUNT(*) FROM sys_menu WHERE menu_type='C' AND path='/compliance/expense-policy' AND del_flag=0
UNION ALL SELECT 'p0_role_compliance',   COUNT(*) FROM sys_role WHERE role_code='COMPLIANCE_ADMIN' AND del_flag=0
UNION ALL SELECT 'p0_role_auditor',      COUNT(*) FROM sys_role WHERE role_code='INTERNAL_AUDITOR' AND del_flag=0
UNION ALL SELECT 'p0_role_asset_acct',   COUNT(*) FROM sys_role WHERE role_code='FA_ASSET_ACCOUNT' AND del_flag=0
UNION ALL SELECT 'p0_cfg_invoice_key',   COUNT(*) FROM sys_config WHERE config_key='expense.invoice.required'
UNION ALL SELECT 'p0_cfg_limit_key',     COUNT(*) FROM sys_config WHERE config_key='expense.amount.limit';

-- 现状（改前应有值；本仓库改前＝approval_menu_rows 0 / review_rules_menu_rows 0 / 两条 AUTO_REJECT 仍 NORMAL）
SELECT 'approval_menu_rows' k, COUNT(*) v FROM sys_menu WHERE menu_type='C' AND path='/approval' AND del_flag=0
UNION ALL SELECT 'review_rules_menu_rows', COUNT(*) FROM sys_menu WHERE menu_type='C' AND path='/compliance/review-rules' AND del_flag=0
UNION ALL SELECT 'auto_reject_normal_before', COUNT(*) FROM op_review_rule WHERE action='AUTO_REJECT' AND status='NORMAL' AND del_flag=0
UNION ALL SELECT 'policy_list_holders_before', COUNT(DISTINCT role_id) FROM sys_role_menu
   WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='F' AND perms='policy:list' AND del_flag=0 LIMIT 1);


-- -----------------------------------------------------------------------------
-- 第 1 节 D-F1 补「审批工作台」菜单行（挂在 流程中心 下，排在 我的单据 与 我的委托代理 之间）
-- 预期影响行数＝1（若目标环境已有该行，本节＝0 行，属正常）
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, terminal, sort, visible, status, create_time)
SELECT p.id, '审批工作台', 'C', '/approval', 'PC', 4, 1, 1, NOW()
  FROM (SELECT id FROM sys_menu WHERE menu_type='M' AND menu_name='流程中心' AND del_flag=0 LIMIT 1) p
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_type='C' AND m.path='/approval' AND m.del_flag=0);

-- 授给「已持待办查询点 wf:task:list 且已持父项 流程中心」的角色（＝能进流程中心的人就该看到入口）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, rm.role_id, nm.id, NULL, NOW()
  FROM sys_role_menu rm
  JOIN sys_menu tsk ON tsk.menu_type='F' AND tsk.perms='wf:task:list' AND tsk.del_flag=0
  JOIN sys_menu par ON par.menu_type='M' AND par.menu_name='流程中心' AND par.del_flag=0
  JOIN sys_menu nm  ON nm.menu_type='C'  AND nm.path='/approval'      AND nm.del_flag=0
  JOIN sys_role r   ON r.id=rm.role_id AND r.del_flag=0
 WHERE rm.menu_id=tsk.id
   AND EXISTS (SELECT 1 FROM sys_role_menu p WHERE p.role_id=rm.role_id AND p.menu_id=par.id)
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=rm.role_id AND t.menu_id=nm.id)
 GROUP BY r.tenant_id, rm.role_id, nm.id;


-- -----------------------------------------------------------------------------
-- 第 2 节  V1.4.97  该行补图标（MenuTree.vue 是 v-if="n.icon"，空值就整枚 <el-icon> 不输出）
-- 必须用 Element Plus 图标名；Stamp＝印章＝审批语义。目标环境若换名，只能换成已在册在用的名字。
-- 预期影响行数＝1（icon 已是 Stamp 时＝0 行）
-- -----------------------------------------------------------------------------
UPDATE sys_menu
   SET icon='Stamp'
 WHERE menu_type='C' AND path='/approval' AND menu_name='审批工作台' AND del_flag=0
   AND (icon IS NULL OR icon='');


-- -----------------------------------------------------------------------------
-- 第 3 节 D-F1 补授「前序审批人」列取数点 wf:history:list
-- 根因：审批工作台列表面包着 wf:task:list 就能进，但「前序审批人」列另需 wf:history:list；
--       该点此前零持有者 ⇒ 列位显示「无权限查看」＝红字影响操作体验，属缺陷。
-- 影响面：仅 sys_role_menu 增行。
-- -----------------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, s.role_id, hist.id, NULL, NOW()
  FROM (SELECT DISTINCT rm.role_id
          FROM sys_role_menu rm
          JOIN sys_menu tsk ON tsk.menu_type='F' AND tsk.perms='wf:task:list' AND tsk.del_flag=0
         WHERE rm.menu_id=tsk.id) s
  JOIN sys_menu hist ON hist.menu_type='F' AND hist.perms='wf:history:list' AND hist.del_flag=0
  JOIN sys_role r    ON r.id=s.role_id AND r.del_flag=0
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=s.role_id AND t.menu_id=hist.id);


-- -----------------------------------------------------------------------------
-- 第 4 节 D-F1 停用与金额分档设计冲突的 AUTO_REJECT 规则
-- 根因：CLAIM／TR_PAY 等模型已按金额四道网关分档（>5万/>20万/>50万/>500万），
--       而这三条「amount > 50000 ⇒ 自动驳回」的规则会先把 5 万以上的单子打死，
--  与分档设计冲突（10-27 实测：5,200,000 报销单被 AUTO_REJECT，流程侧却已 APPROVED＝D-F1 现象面）。
-- 只动 AUTO_REJECT ＋ 表达式含 amount > 50000 的行；禁止动 AUTO_PASS／MANUAL_REVIEW／REJECT。
-- 预期影响行数：＝前置复点 auto_reject_normal_before 里命中这两条 rule_code 的枚数（本仓库＝2）
-- -----------------------------------------------------------------------------
UPDATE op_review_rule
   SET status='DISABLED', updated_at=NOW()
 WHERE del_flag=0 AND action='AUTO_REJECT' AND status='NORMAL'
   AND rule_code IN ('RR_AMOUNT_HIGH','W14UNN7-REV','W14B6RO-RULE','W14B6RO-REV')
   AND match_expression LIKE '%amount > 50000%';


-- -----------------------------------------------------------------------------
-- 第 5 节 D-F1 「智能审单规则」菜单行 ＋ 取数点
-- 根因：路由 /compliance/review-rules 在册、sys_menu 零行 ⇒ 左侧栏看不到，停用规则只能改库（10-27 就是这么停的）。
-- 新行按同级「费用标准」页同式（父项＝系统管理，component＝master/MasterConfigTable，perms＝policy:list，icon＝Operation）。
-- 禁止只给读与「停用」：compliance:review:list／:update；禁止授 compliance:review:add／:delete（内审独立性）。
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, terminal, sort, visible, status, create_by, create_time, del_flag)
SELECT p.id, '智能审单规则', 'C', '/compliance/review-rules', 'master/MasterConfigTable', 'policy:list', 'Operation', 'PC', 2, 1, 1, 1, NOW(), 0
  FROM (SELECT m.parent_id AS id FROM sys_menu m WHERE m.menu_type='C' AND m.path='/compliance/expense-policy' AND m.del_flag=0 LIMIT 1) p
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.menu_type='C' AND x.path='/compliance/review-rules' AND x.del_flag=0);

-- ① 菜单行授给 合规管理岗／内审经理（与同级「费用标准」页的授权面一致）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, nm.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu nm ON nm.menu_type='C' AND nm.path='/compliance/review-rules' AND nm.del_flag=0
 WHERE r.del_flag=0 AND r.role_code IN ('COMPLIANCE_ADMIN','INTERNAL_AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=r.id AND t.menu_id=nm.id);

-- ② 一并授取数点 policy:list（该功能点此前零持有者 ⇒ 只给菜单进页即 PERM_DENIED）
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, pl.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu pl ON pl.menu_type='F' AND pl.perms='policy:list' AND pl.del_flag=0
 WHERE r.del_flag=0 AND r.role_code IN ('COMPLIANCE_ADMIN','INTERNAL_AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=r.id AND t.menu_id=pl.id);

-- ③ 规则「读」点 compliance:review:list ⇒ 合规管理岗＋内审经理
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, mm.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu mm ON mm.menu_type='F' AND mm.perms='compliance:review:list' AND mm.del_flag=0
 WHERE r.del_flag=0 AND r.role_code IN ('COMPLIANCE_ADMIN','INTERNAL_AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=r.id AND t.menu_id=mm.id);

-- ④ 规则「改」（界面【停用】走的就是 update 点）⇒ 只给合规管理岗，禁止给内审经理
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, mm.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu mm ON mm.menu_type='F' AND mm.perms='compliance:review:update' AND mm.del_flag=0
 WHERE r.del_flag=0 AND r.role_code='COMPLIANCE_ADMIN'
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=r.id AND t.menu_id=mm.id);


-- -----------------------------------------------------------------------------
-- 第 6 节  H5.7  折旧计提批【发起】按钮门控码 sharing:voucher:generate → 资产核算岗
-- 根因（本仓库实测）：持该功能点的 4 枚角色（FIN_GL／FSSC_AUDITOR／FSSC_SUPERVISOR／SHARING_SUPPORT）
--       都禁止有「折旧计提批」菜单；有该菜单的资产岗角色禁止无该码 ⇒ 零交集，没有任何业务账号
--       能"既看得见页面又点得动发起"。按「菜单给了就必须一并给该页该有的操作权」补一条角色授权。
-- 只授 FA_ASSET_ACCOUNT（计提入账是该岗职责）；禁止授 FA_ASSET_ADMIN（验收岗）、禁止授 WH_KEEPER（仓储岗）。
-- 治理遗留：菜单「折旧计提批」的 perms 仍只有 asset:list,asset:update、禁止含 generate 码
--       ＝菜单-按钮码错配的形态未变，本轮只是补授权，仍待管理员册定口径。
-- -----------------------------------------------------------------------------
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id, create_by, create_time)
SELECT r.tenant_id, r.id, g.id, 1, NOW()
  FROM sys_role r
  JOIN sys_menu g ON g.menu_type='F' AND g.perms='sharing:voucher:generate' AND g.del_flag=0
 WHERE r.del_flag=0 AND r.role_code='FA_ASSET_ACCOUNT'
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id=r.id AND t.menu_id=g.id);


-- -----------------------------------------------------------------------------
-- 第 7 节 D-F1 两枚零消费方参数：改标签（禁止补消费方＝改变业务拦截口径）
-- 根因：expense.invoice.required 全仓仅 SysConfig.java 的一句注释提到，实际"报销必须有票"由
--       ExpenseBillService:917／PrecheckService:122 走费用政策(ExpensePolicy)判定；
--       expense.amount.limit 全仓零命中，实测 5,200,000 的报销单不受该值拦截，
--       金额档真实口径＝流程模型 CLAIM 的四道金额网关。
-- 禁止改 config_value（取值不动＝业务拦截口径不动），只改 config_name＋remark。
-- -----------------------------------------------------------------------------
UPDATE sys_config
   SET config_name='报销是否附发票（登记项·本参数未被业务逻辑读取）',
       remark='全仓检索本键仅 SysConfig.java 注释提到，无任何消费方；实际"报销必须有票"由 ExpenseBillService:917 / PrecheckService:122 走费用政策(ExpensePolicy)判定。本行只作登记，禁止以本参数值下判合规性。',
       update_by=1, update_time=NOW()
 WHERE config_key='expense.invoice.required'
   AND config_name<>'报销是否附发票（登记项·本参数未被业务逻辑读取）';

UPDATE sys_config
   SET config_name='单笔报销上限(元)（登记项·本参数未被业务逻辑读取）',
       remark='本键全仓零消费方；实测 5,200,000 的报销单不受本值拦截（一路走完 12 节并付款）。金额档的真实口径＝流程模型 CLAIM 的四道金额网关（>5万/>20万/>50万/>500万）。禁止以本参数值当上限。',
       update_by=1, update_time=NOW()
 WHERE config_key='expense.amount.limit'
   AND config_name<>'单笔报销上限(元)（登记项·本参数未被业务逻辑读取）';


-- -----------------------------------------------------------------------------
-- 第 8 节  执行后复点（只读。本仓库实测值写在注释里，目标环境禁止硬套数字，只核「是否 ≥1 / 是否 0」）
-- -----------------------------------------------------------------------------
SELECT 'a1_approval_menu'      k, COUNT(*) v FROM sys_menu WHERE menu_type='C' AND path='/approval' AND del_flag=0
UNION ALL SELECT 'a1_approval_icon_set',   COUNT(*) FROM sys_menu WHERE menu_type='C' AND path='/approval' AND del_flag=0 AND icon='Stamp'
UNION ALL SELECT 'a1_approval_grants',     COUNT(DISTINCT role_id) FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='C' AND path='/approval' AND del_flag=0 LIMIT 1)
UNION ALL SELECT 'a2_hist_gap_left',       COUNT(DISTINCT rm.role_id) FROM sys_role_menu rm JOIN sys_menu tsk ON tsk.menu_type='F' AND tsk.perms='wf:task:list' AND tsk.del_flag=0
                                                WHERE rm.menu_id=tsk.id AND NOT EXISTS (SELECT 1 FROM sys_role_menu x JOIN sys_menu h ON h.menu_type='F' AND h.perms='wf:history:list' WHERE x.role_id=rm.role_id AND x.menu_id=h.id AND h.del_flag=0)
UNION ALL SELECT 'a3_auto_reject_normal_left', COUNT(*) FROM op_review_rule WHERE action='AUTO_REJECT' AND status='NORMAL' AND del_flag=0 AND rule_code IN ('RR_AMOUNT_HIGH','W14UNN7-REV','W14B6RO-RULE','W14B6RO-REV')
UNION ALL SELECT 'a4_review_rules_menu',   COUNT(*) FROM sys_menu WHERE menu_type='C' AND path='/compliance/review-rules' AND del_flag=0
UNION ALL SELECT 'a4_menu_grants',         COUNT(DISTINCT rm.role_id) FROM sys_role_menu rm WHERE rm.menu_id=(SELECT id FROM sys_menu WHERE menu_type='C' AND path='/compliance/review-rules' AND del_flag=0 LIMIT 1)
UNION ALL SELECT 'a4_policy_list_holders', COUNT(DISTINCT rm.role_id) FROM sys_role_menu rm JOIN sys_menu pl ON pl.menu_type='F' AND pl.perms='policy:list' AND pl.id=rm.menu_id
UNION ALL SELECT 'a4_review_update_holders', COUNT(DISTINCT r.role_code) FROM sys_role_menu rm JOIN sys_menu mm ON mm.menu_type='F' AND mm.perms='compliance:review:update' AND mm.id=rm.menu_id JOIN sys_role r ON r.id=rm.role_id AND r.del_flag=0
UNION ALL SELECT 'a5_voucher_gen_holders', COUNT(DISTINCT r.role_code) FROM sys_role_menu rm JOIN sys_menu g ON g.menu_type='F' AND g.perms='sharing:voucher:generate' AND g.id=rm.menu_id JOIN sys_role r ON r.id=rm.role_id AND r.del_flag=0
UNION ALL SELECT 'a6_cfg_renamed',         COUNT(*) FROM sys_config WHERE config_key IN ('expense.invoice.required','expense.amount.limit') AND config_name LIKE '%登记项%';
-- 本仓库执行后读数（对照用，禁止当目标环境判据）：a1=1/1/若干角色、a2=0、a3=0、a4=1/2、policy_list=2、
--   review_update=1（只 COMPLIANCE_ADMIN）、a5=5（原 4 枚＋FA_ASSET_ACCOUNT）、a6=2；
--   另：零持有权限点总数由 10-22 的 156 枚降到本仓库现读 **151** 枚（差值＝10-23~10-28 多轮授权累计，禁止全归本窗）。


-- -----------------------------------------------------------------------------
-- 第 9 节  回滚（注释态。按需逐条放开；本件禁止自动回滚，禁止删行前先与在册文档对撞）
-- -----------------------------------------------------------------------------
-- UPDATE sys_menu SET icon=NULL WHERE menu_type='C' AND path='/approval' AND icon='Stamp';
-- DELETE FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='C' AND path='/approval' LIMIT 1);
-- DELETE FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='F' AND perms='wf:history:list' LIMIT 1)
--   AND role_id IN (SELECT role_id FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='F' AND perms='wf:task:list' LIMIT 1));
-- UPDATE op_review_rule SET status='NORMAL', updated_at=NOW()
--   WHERE rule_code IN ('W14UNN7-REV','W14B6RO-REV') AND action='AUTO_REJECT' AND status='DISABLED';
--   -- 回滚这两条＝把 5,200,000 那一档再拦一遍（10-27 已裁定停用）；禁止随手回滚，需先复核裁定。
-- DELETE FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='C' AND path='/compliance/review-rules' LIMIT 1);
-- DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE menu_type='F' AND perms IN ('policy:list','compliance:review:list','compliance:review:update'))
--   AND role_id IN (SELECT id FROM sys_role WHERE role_code IN ('COMPLIANCE_ADMIN','INTERNAL_AUDITOR'));
-- DELETE FROM sys_role_menu WHERE menu_id=(SELECT id FROM sys_menu WHERE menu_type='F' AND perms='sharing:voucher:generate' LIMIT 1)
--   AND role_id=(SELECT id FROM sys_role WHERE role_code='FA_ASSET_ACCOUNT' LIMIT 1);
-- DELETE FROM sys_menu WHERE menu_type='C' AND path='/compliance/review-rules';
-- UPDATE sys_config SET config_name='报销是否附发票',     remark=NULL WHERE config_key='expense.invoice.required';
-- UPDATE sys_config SET config_name='单笔报销上限(元)',   remark=NULL WHERE config_key='expense.amount.limit';
--   -- 原始标签名以目标环境执行前第 0 节截图为准，禁止参照本行（本仓库原名如上）。


-- -----------------------------------------------------------------------------
-- 第 10 节  前置依赖检查（本件禁止包含这三件，若目标环境没跑过，§5/§6 之外的功能会缺列/缺字典）
-- -----------------------------------------------------------------------------
-- V1.4.94 退货原因字典（两域各 6 项）      期望＝2：SELECT COUNT(*) FROM sys_dict_type WHERE dict_type IN ('enum_return_reason_pur','enum_return_reason_sale');
-- V1.4.95 rc_receipt.receivable_ids 回填   期望＝0：SELECT COUNT(*) FROM rc_receipt WHERE receivable_ids IS NULL AND del_flag=0;
-- V1.4.96 ocr_record 增两列（库＝fssc_image）期望＝2：
--   SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='fssc_image' AND TABLE_NAME='ocr_record' AND COLUMN_NAME IN ('recognize_status','fail_reason');
--  该 DDL 必须**先于**含 代码的 jar 上线。
-- =============================================================================
