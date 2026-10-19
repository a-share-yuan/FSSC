-- ================================================================
-- V1.4.70  新增「全局状态展示字典」bill_status_display
-- ----------------------------------------------------------------
-- 背景：billStatus.js 的 statusLabel() 被 55 个业务文件直接调用，
--       是**全局单一映射**（不分区），而 V1.4.68 按域拆的 15 个字典
--       是**下拉口径**（某字段可选值）。两者职责不同：
--         · 域字典    → 下拉/表单可选值（按域精确）
--         · 本展示字典 → 列表/详情任意状态码的中文显示（全局扁平）
--       若强行让 statusLabel 去查 15 个域字典，CLOSED/DRAFT/NORMAL
--       这类跨义键无法判定归属 ⇒ 故新增本扁平字典，语义明确。
--
-- 数据来源：fssc-web/src/constants/billStatus.js 的 STATUS_LABEL/STATUS_TYPE
--          **标签逐字取自该文件，未自造一个中文词**；css_class 取自 STATUS_TYPE，
--          使 el-tag 颜色也变成可运营维护的数据。
--
-- 幂等：sys_dict_type 有 uk_tenant_dict_type、sys_dict_data 有 uk_tenant_type_value，
--       可重复执行。回滚见 V1.4.70_bill_status_display_rollback.sql。
-- ================================================================

INSERT INTO fssc.sys_dict_type
 (tenant_id,dict_type,dict_name,is_builtin,status,create_by,del_flag,remark) VALUES
 (1,'bill_status_display','全局状态展示（statusLabel 统一映射）',1,'NORMAL',NULL,0,
  '扁平展示口径：与按域拆的下拉字典职责不同，仅用于列表/详情的状态码→中文显示');

DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type='bill_status_display';

INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag,remark) VALUES
 (1,'bill_status_display','启用中','ACTIVE','success',1,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已审批','APPROVED','success',2,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','审批中','APPROVING','warning',3,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已归档','ARCHIVED','success',4,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已取消','CANCELLED','info',5,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已关闭','CLOSED','danger',6,0,'NORMAL',NULL,0,'单据已关闭（与会计期间 month_close_status.CLOSED「已结账」不同义）；tag=danger'),
 (1,'bill_status_display','已完成','COMPLETED','success',7,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已确认','CONFIRMED','warning',8,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已合并','CONSOLIDATED','info',9,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已发布','DEPLOYED','info',10,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','有差异','DIFF','info',11,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已停用','DISABLE','info',12,0,'NORMAL',NULL,0,'界面开关写入值（历史种子用）；与 DISABLED 同义共存，勿删；tag=info'),
 (1,'bill_status_display','已停用','DISABLED','info',13,0,'NORMAL',NULL,0,'已停用（与 DISABLE 同义，界面开关现写此值）；tag=info'),
 (1,'bill_status_display','已完成','DONE','success',14,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','草稿','DRAFT','info',15,0,'NORMAL',NULL,0,'单据草稿（与流程模型 model_publish_status.DRAFT「未发布」不同义）；tag=info'),
 (1,'bill_status_display','已生效','EFFECTIVE','info',16,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已启用','ENABLED','info',17,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已过期','EXPIRED','warning',18,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','失败','FAIL','danger',19,0,'NORMAL',NULL,0,'tag=danger'),
 (1,'bill_status_display','失败','FAILED','danger',20,0,'NORMAL',NULL,0,'tag=danger'),
 (1,'bill_status_display','已立卷','FILED','info',21,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已生成','GENERATED','success',22,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已停用','INACTIVE','info',23,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已开票','INVOICED','info',24,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','进行中','IN_PROGRESS','info',25,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已下达','ISSUED','success',26,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已勾对','MATCHED','info',27,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','状态不一','MIXED','info',28,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','正常','NORMAL','success',29,0,'NORMAL',NULL,0,'主数据正常（与借款 loan_status.NORMAL「未还」不同义）；tag=success'),
 (1,'bill_status_display','已抵消','OFFSET','success',30,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已逾期','OVERDUE','warning',31,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已付款','PAID','success',32,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','部分还款','PARTIAL_REPAID','warning',33,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已验收','PASSED','info',34,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','待处理','PENDING','info',35,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已记账','POSTED','success',36,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','处理中','PROCESSING','warning',37,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已完成','PROCESS_COMPLETED','success',38,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已计提','PROVISIONED','info',39,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已收货','RECEIVED','info',40,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已驳回','REJECTED','danger',41,0,'NORMAL',NULL,0,'tag=danger'),
 (1,'bill_status_display','已还款','REPAID','success',42,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已冲销','REVERSED','info',43,0,'NORMAL',NULL,0,'已冲销（记账域红字冲销；坏账核销域同码同义）；tag=info'),
 (1,'bill_status_display','运行中','RUNNING','primary',44,0,'NORMAL',NULL,0,'tag=primary'),
 (1,'bill_status_display','已跳过','SKIPPED','info',45,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已提交','SUBMITTED','info',46,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','成功','SUCCESS','success',47,0,'NORMAL',NULL,0,'tag=success'),
 (1,'bill_status_display','已挂起','SUSPENDED','warning',48,0,'NORMAL',NULL,0,'tag=warning'),
 (1,'bill_status_display','已终止','TERMINATED','danger',49,0,'NORMAL',NULL,0,'tag=danger'),
 (1,'bill_status_display','待办','TODO','info',50,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已转固','TRANSFERRED','info',51,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','转固审批中','TRANSFERRING','info',52,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','未归档','UNARCHIVED','info',53,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','未结账','UNCLOSED','info',54,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','未对账','UNRECON','info',55,0,'NORMAL',NULL,0,'tag=info'),
 (1,'bill_status_display','已作废','VOID','danger',56,0,'NORMAL',NULL,0,'tag=danger'),
 (1,'bill_status_display','已撤回','WITHDRAWN','info',57,0,'NORMAL',NULL,0,'tag=info');

-- 行数校验：应等于 STATUS_LABEL 的键数
SELECT COUNT(*) AS should_be_57 FROM fssc.sys_dict_data
  WHERE tenant_id=1 AND dict_type='bill_status_display' AND del_flag=0;
