-- =============================================================================
-- V1.4.68 经营域字典补齐（4-D / 8-C代码侧）
--
-- 目的：把「前端硬编码枚举」办结到数据库字典。
--   【真实缺口，不是臆造出来的任务】前端`fssc-web/src/constants/billStatus.js`
--   已维护一张约 70 键的**统一状态名称表**（STATUS_LABEL / STATUS_TYPE），
--   且注释明确要求「新增枚举码须同步登记，缺键会把英文码直接显示到界面」；
--   而 V1.4.67 写入数据库后 `sys_dict_data` 里**一个业务状态都没有**
--   ⇒ **前后端两套枚举并存**，字典侧完全空转。本次把名称表按域拆开灌进字典。
--
-- 【取值来源：唯一权威= billStatus.js 的 STATUS_LABEL】
--   · 标签（dict_label）逐字取自该文件，**未自造一个中文词**；
--   · 该文件里每键都带 D-DEC 编号注释，说明是哪张表哪一列，本件沿用其归属作为字典类型边界；
--   · 交叉检查过 3 个跨域复用键（CLOSED / DRAFT / NORMAL），语义确实不同域
--     （CLOSED＝单据已关闭 vs 会计期间已结账；DRAFT＝单据草稿 vs 流程模型未发布；
--      NORMAL＝借款未还 vs 主数据正常）⇒ **必须分类型，不能塞进同一个字典**。
--
-- 【为什么要拆成 15 个类型而不是一个巨型字典】
--   字典按"同一 dict_value 同一含义"组织；上表 3 个跨域键正是反例。
--   拆开后 `sys_dict_data` 无跨义重复，页面按域取字典即可直接渲染。
--
-- 【本件只是把落点建起来，不改任何渲染逻辑】
--   前端目前**仍读 billStatus.js，不读字典**（实测`fssc-web/src` 对本批dict_type 命中 0）。
--   故本件零行为变化；把 statusLabel 切到字典是**独立的前端改造项**，
--   与 V1.4.67 同性质（字典有数据 ≠ 界面已按字典渲染）。切之前请先确认：
--   切了之后所有页面必须同步改造，否则会出现"部分页面读字典、部分读常量"的分裂态。
--
-- 【零风险边界】
--   · 只 INSERT sys_dict_type（缺失的 15 个）与 sys_dict_data，**不碰任何业务表的枚举列**；
--   · 去重：先DELETE 同 (dict_type, dict_value) 再插，重复执行结果一致；
--   · 不改 V1.4.67 已灌的 8 个容器（expense_type / sys_status / wf_* / dict_yes_no）；
--   · create_by 置 NULL（该列是 BIGINT 存用户 ID，塞不了脚本标记串）；
--   · 回滚见 V1.4.68_..._rollback.sql（按 dict_type 精确清理本件所建）。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读；期望：这15 个 dict_type 均不存在、wf_instance_status 行数为 6）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS existed FROM fssc.sys_dict_type
--  WHERE dict_type IN ('bill_status_main','wf_instance_status','job_status');   -- 期望 0
-- SELECT COUNT(*) AS rows_ FROM fssc.sys_dict_data WHERE dict_type='wf_instance_status';

-- ----------------------------------------------------------------------------
-- §2 容器（sys_dict_type）
-- ----------------------------------------------------------------------------
INSERT INTO fssc.sys_dict_type (tenant_id, dict_type, dict_name, is_builtin, status, remark, create_by, del_flag) VALUES
 (1,'bill_status_main',      '单据主状态（费用/应付/收款主链路）',1,'NORMAL','取自 billStatus.js 标准 6 步生命周期',              NULL,0),
 (1,'bill_status_exception', '单据异常/终态分支',1,'NORMAL','取自 billStatus.js 异常分支',NULL,0),
 (1,'wf_instance_status',    '流程实例状态（wf_instance.status）',1,'NORMAL','对应 WfInstance 状态机',              NULL,0),
 (1,'job_status',            '作业/异步任务状态',1,'NORMAL','取自 billStatus.js 作业状态段',                          NULL,0),
 (1,'loan_status',           '借款/核销生命周期',1,'NORMAL','LOAN域专用，与单据主状态不同义',                           NULL,0),
 (1,'master_data_status',    '主数据/定义启停态',1,'NORMAL','含 DISABLED/DISABLE 双写法（见文末说明）',                   NULL,0),
 (1,'archive_status',        '归档/立卷态',1,'NORMAL','exp_bill.archive_status / op_archive.status',            NULL,0),
 (1,'recon_status',          '对账/勾对态',1,'NORMAL','op_bank_statement.recon_status / bank_reconcile.match_status', NULL,0),
 (1,'receipt_status',        '采购收货/验收态',1,'NORMAL','po_receipt.status / quality_status',                      NULL,0),
 (1,'bill_domain_status',    '跨域单据域状态（采购/合并/抵消/坏账）',1,'NORMAL','跨域归并引入的各域本表状态码', NULL,0),
 (1,'cip_status',            '在建工程转固态（md_cip.status）',1,'NORMAL','billStatus.js:74-77',                                    NULL,0),
 (1,'month_close_status',    '总账会计期间关账态',1,'NORMAL','与单据 CLOSED 不同义，故独立成类型',                        NULL,0),
 (1,'model_publish_status',  '流程模型发布态（wf_model.status）',1,'NORMAL','与单据 DRAFT 不同义，故独立成类型',                      NULL,0),
 (1,'bill_sub_type',         '单据子类型（exp_bill.bill_sub_type）',1,'NORMAL','取自 billStatus.js:201-209 BILL_SUBTYPE_ROUTE',   NULL,0),
 (1,'bill_reject_type',      '驳回类型（exp_bill.reject_type）',1,'NORMAL','取自 billStatus.js:180-183 REJECT_TYPE_LABEL',NULL,0)
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

-- ----------------------------------------------------------------------------
-- §3 数据（sys_dict_data）—— 标签逐字取自 billStatus.js STATUS_LABEL
-- ----------------------------------------------------------------------------

-- §3.0 去重清理（本件所建类型 + 已存在容器的同值行）
DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type IN
 ('bill_status_main','bill_status_exception','wf_instance_status','job_status','loan_status',
  'master_data_status','archive_status','recon_status','receipt_status','bill_domain_status',
  'cip_status','month_close_status','model_publish_status','bill_sub_type','bill_reject_type');

-- §3.1 单据主状态（标准 6 步生命周期）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'bill_status_main','草稿','DRAFT','info',1,1,'NORMAL',NULL,0),
 (1,'bill_status_main','已提交','SUBMITTED','info',2,0,'NORMAL',NULL,0),
 (1,'bill_status_main','审批中','APPROVING','warning',3,0,'NORMAL',NULL,0),
 (1,'bill_status_main','已审批','APPROVED','success',4,0,'NORMAL',NULL,0),
 (1,'bill_status_main','已付款','PAID','success',5,0,'NORMAL',NULL,0),
 (1,'bill_status_main','已记账','POSTED','success',6,0,'NORMAL',NULL,0),
 (1,'bill_status_main','已归档','ARCHIVED','info',7,0,'NORMAL',NULL,0);

-- §3.2 单据异常/最终状态分支
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'bill_status_exception','已驳回','REJECTED','danger',1,0,'NORMAL',NULL,0),
 (1,'bill_status_exception','已作废','VOID','danger',2,0,'NORMAL',NULL,0),
 (1,'bill_status_exception','已关闭','CLOSED','danger',3,0,'NORMAL',NULL,0),
 (1,'bill_status_exception','已终止','TERMINATED','danger',4,0,'NORMAL',NULL,0),
 (1,'bill_status_exception','已冲销','REVERSED','info',5,0,'NORMAL',NULL,0);

-- §3.3 流程实例状态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'wf_instance_status','运行中','RUNNING','primary',1,1,'NORMAL',NULL,0),
 (1,'wf_instance_status','已完成','COMPLETED','success',2,0,'NORMAL',NULL,0),
 (1,'wf_instance_status','已完成','PROCESS_COMPLETED','success',3,0,'NORMAL',NULL,0),
 (1,'wf_instance_status','已取消','CANCELLED','info',4,0,'NORMAL',NULL,0),
 (1,'wf_instance_status','已撤回','WITHDRAWN','info',5,0,'NORMAL',NULL,0),
 (1,'wf_instance_status','已挂起','SUSPENDED','warning',6,0,'NORMAL',NULL,0);

-- §3.4 作业/异步任务状态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'job_status','待处理','PENDING','info',1,0,'NORMAL',NULL,0),
 (1,'job_status','处理中','PROCESSING','warning',2,0,'NORMAL',NULL,0),
 (1,'job_status','待办','TODO','info',3,0,'NORMAL',NULL,0),
 (1,'job_status','已完成','DONE','success',4,1,'NORMAL',NULL,0),
 (1,'job_status','成功','SUCCESS','success',5,0,'NORMAL',NULL,0),
 (1,'job_status','失败','FAIL','danger',6,0,'NORMAL',NULL,0),
 (1,'job_status','失败','FAILED','danger',7,0,'NORMAL',NULL,0),
 (1,'job_status','已跳过','SKIPPED','info',8,0,'NORMAL',NULL,0),
 (1,'job_status','已生成','GENERATED','success',9,0,'NORMAL',NULL,0);

-- §3.5 借款/核销生命周期
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'loan_status','正常（未还）','NORMAL','info',1,1,'NORMAL',NULL,0),
 (1,'loan_status','已下达','ISSUED','primary',2,0,'NORMAL',NULL,0),
 (1,'loan_status','已还款','REPAID','success',3,0,'NORMAL',NULL,0),
 (1,'loan_status','部分还款','PARTIAL_REPAID','warning',4,0,'NORMAL',NULL,0);

-- §3.6 主数据/定义启停态
--   DISABLED 与 DISABLE **两个键都保留**：billStatus.js:97注释明写
--   「角色停用态：界面开关写 DISABLED，历史种子用的是 DISABLE」⇒ 两者并存是现状，不合并。
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'master_data_status','正常','NORMAL','success',1,1,'NORMAL',NULL,0),
 (1,'master_data_status','启用中','ACTIVE','success',2,0,'NORMAL',NULL,0),
 (1,'master_data_status','已启用','ENABLED','success',3,0,'NORMAL',NULL,0),
 (1,'master_data_status','已停用','DISABLED','info',4,0,'NORMAL',NULL,0),
 (1,'master_data_status','已停用','DISABLE','info',5,0,'NORMAL',NULL,0),
 (1,'master_data_status','已停用','INACTIVE','info',6,0,'NORMAL',NULL,0),
 (1,'master_data_status','已过期','EXPIRED','warning',7,0,'NORMAL',NULL,0);

-- §3.7 归档/立卷态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'archive_status','未归档','UNARCHIVED','info',1,1,'NORMAL',NULL,0),
 (1,'archive_status','已立卷','FILED','success',2,0,'NORMAL',NULL,0);

-- §3.8 对账/勾对态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'recon_status','已勾对','MATCHED','success',1,0,'NORMAL',NULL,0),
 (1,'recon_status','未对账','UNRECON','info',2,1,'NORMAL',NULL,0),
 (1,'recon_status','有差异','DIFF','danger',3,0,'NORMAL',NULL,0);

-- §3.9 采购收货/验收态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'receipt_status','已收货','RECEIVED','success',1,0,'NORMAL',NULL,0),
 (1,'receipt_status','已验收','PASSED','success',2,1,'NORMAL',NULL,0);

-- §3.10 跨域单据域状态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'bill_domain_status','已开票','INVOICED','success',1,0,'NORMAL',NULL,0),
 (1,'bill_domain_status','已合并','CONSOLIDATED','success',2,0,'NORMAL',NULL,0),
 (1,'bill_domain_status','已抵消','OFFSET','success',3,0,'NORMAL',NULL,0),
 (1,'bill_domain_status','已计提','PROVISIONED','warning',4,0,'NORMAL',NULL,0);

-- §3.11 在建工程转固态
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'cip_status','进行中','IN_PROGRESS','info',1,1,'NORMAL',NULL,0),
 (1,'cip_status','转固审批中','TRANSFERRING','warning',2,0,'NORMAL',NULL,0),
 (1,'cip_status','已转固','TRANSFERRED','success',3,0,'NORMAL',NULL,0);

-- §3.12 总账会计期间关账态（CLOSED 在此＝已结账，与单据 CLOSED 不同义）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'month_close_status','未结账','UNCLOSED','info',1,1,'NORMAL',NULL,0),
 (1,'month_close_status','已结账','CLOSED','success',2,0,'NORMAL',NULL,0);

-- §3.13 流程模型发布态（DRAFT 在此＝模型草稿，与单据 DRAFT 不同义）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'model_publish_status','草稿（未发布）','DRAFT','info',1,0,'NORMAL',NULL,0),
 (1,'model_publish_status','已发布','DEPLOYED','success',2,1,'NORMAL',NULL,0);

-- §3.14 单据子类型（标签取自 billStatus.js BILL_SUBTYPE_ROUTE 的中文路由名）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'bill_sub_type','会议费','MEETING','info',1,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','差旅费','TRAVEL','info',2,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','培训费','TRAIN','info',3,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','预付','PREPAY','info',4,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','对公结算','SETTLE','info',5,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','Agency','AGENCY','info',6,0,'NORMAL',NULL,0),
 (1,'bill_sub_type','Air','AIR','info',7,0,'NORMAL',NULL,0);

-- §3.15 驳回类型（标签逐字取自 billStatus.js REJECT_TYPE_LABEL）
INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag) VALUES
 (1,'bill_reject_type','漏单驳回','THROW_BACK','danger',1,0,'NORMAL',NULL,0),
 (1,'bill_reject_type','逐级驳回','GRADE_BY_GRADE','warning',2,0,'NORMAL',NULL,0);

-- ----------------------------------------------------------------------------
-- §4 执行后复点
-- ----------------------------------------------------------------------------
-- 期望：23 个类型 / 89 行（V1.4.67 的 8 类型 24 行 + 本件 15 类型 65 行）
-- SELECT COUNT(*) AS types FROM fssc.sys_dict_type WHERE del_flag=0;
-- SELECT COUNT(*) AS rows_ FROM fssc.sys_dict_data WHERE del_flag=0;
-- SELECT dict_type, COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0 GROUP BY dict_type ORDER BY dict_type;

-- 校验①：同类型内 dict_value 无重复（期望 0 行）
-- SELECT dict_type, dict_value, COUNT(*) FROM fssc.sys_dict_data
--  WHERE del_flag=0 GROUP BY dict_type, dict_value HAVING COUNT(*)>1;

-- 校验②：数据项都能找到容器（期望 0 行）
-- SELECT DISTINCT d.dict_type FROM fssc.sys_dict_data d
--  WHERE d.del_flag=0 AND NOT EXISTS (SELECT 1 FROM fssc.sys_dict_type t
--      WHERE t.dict_type=d.dict_type AND t.del_flag=0);