-- =============================================================================
-- V1.4.67 字典数据 sys_dict_data 补齐（4-D / 8-C）
--
-- 目的：`sys_dict_type` 容器已在 V1.4.60 建好 8 个类型，但 `sys_dict_data` 仍只有
--       3 行（仅 expense_type 的 TRAVEL/OFFICE/ENTERTAIN）⇒ **容器空置**，
--       "枚举字典办结"这条改造没有落点。本件按**代码中已在使用的取值**补齐数据。
--
-- 【取值来源：全部来自代码/库现状，不臆造业务枚举】
--   wf_node_multi_type    ← WfEngineFacade 多类型分支 + wf_node_config 实际分布
--                          （ANY 177 / ALL 2；SEQ、VOTE 代码支持但库中零使用，仍登记以备启用）
--   wf_node_empty_strategy← NodeOrgResolver.applyEmptyStrategy:547-600 的 switch 四档
--                          （SKIP / ERROR / ADMIN / ESCALATE；空值默认 ERROR）
--                          注意：**库里实际值是 ESCALATE(172)/ERROR(7)**，
--                             不是 ADMIN —— 灌 ADMIN/TRANSFER/REJECT 会造出永不生效的枚举。
--   wf_addsign_position   ← wf_add_sign.position 列的实际取值 BEFORE/AFTER
--   wf_addsign_status     ← wf_add_sign.status + WfEngineFacade 加签状态机取值
--   wf_approval_item_category ← wf_approval_item.category 现有值 POSITIVE / REJECT
--   sys_status            ← 全库 *_表.status 列实测取值
--   dict_yes_no           ← 通用是否
--   expense_type          ← 保持现有 3 行，仅补齐容器缺失的常见费用类型
--
-- 【重要边界：灌了 ≠ 生效】
--   实测（2026-10-15）前端 `fssc-web/src` 对本 8 个 dict_type 的字符串命中**几乎全为 0**
--   （expense_type 仅 1 处），即当前**没有任何页面按字典渲染下拉**。
--   ⇒ 本件的作用是**把落点建起来**（让后续枚举办结有地方落、有 UI 可管），
--     并让 `GET /dict/data/{type}` 这类接口能返回真实枚举；
--     **前端改为读字典属于 4-C「通用策略配置页」与后续i18n/枚举办结的工作范围**，
--     不在本件。
--
-- 【零影响边界】
--   · 只 INSERT sys_dict_data，不改任何业务表的枚举列；
--   · 去重：先按 (tenant_id, dict_type, dict_value) 删除同值再插，重复执行结果一致；
--   · is_default 只给每类的"首选值"置1（如 multi_type 的 ANY），仅供 UI 默认选中用，
--     当前无UI 消费故无行为影响；
--   · 回滚见V1.4.67_dict_data_rollback.sql（按 executed_by=NULL 精确清理）。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读）
-- ----------------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0 GROUP BY dict_type;
--   期望：仅 expense_type=3
-- SELECT DISTINCT multi_type FROM fssc.wf_node_config WHERE del_flag=0;
-- SELECT DISTINCT empty_strategy FROM fssc.wf_node_config WHERE del_flag=0;

-- ----------------------------------------------------------------------------
-- §2 执行段
-- ----------------------------------------------------------------------------

-- §2.1 去重清理（本件写入的条目：executed_by=NULL）
DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1
   AND create_by IS NULL
   AND dict_type IN ('wf_node_multi_type','wf_node_empty_strategy','wf_addsign_position',
                     'wf_addsign_status','wf_approval_item_category','sys_status',
                     'dict_yes_no','expense_type')
   AND dict_value IN ('ANY','ALL','SEQ','VOTE','SKIP','ERROR','ADMIN','ESCALATE',
                      'BEFORE','AFTER','PENDING','ACTIVE','DONE','REJECTED','CANCELLED',
                      'POSITIVE','NEGATIVE','TERMINATE','NORMAL','DISABLE',
                      'YES','NO','TRAIN','MEETING');

-- §2.2 会签模式（代码支持 4 档；库中现用 ANY/ALL）
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'wf_node_multi_type','或签（任一人办理即可）','ANY','info',1,1,'NORMAL',NULL,0),
  (1,'wf_node_multi_type','会签（所有人办理）','ALL','primary',2,0,'NORMAL',NULL,0),
  (1,'wf_node_multi_type','依次签（按顺序逐个办理）','SEQ','warning',3,0,'NORMAL',NULL,0),
  (1,'wf_node_multi_type','票签（可投赞成/反对票）','VOTE','danger',4,0,'NORMAL',NULL,0);

-- §2.3 空审批人默认策略（严格对齐 NodeOrgResolver.applyEmptyStrategy 的 switch 四档）
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'wf_node_empty_strategy','沿汇报线上收至真实负责人','ESCALATE','primary',1,1,'NORMAL',NULL,0),
  (1,'wf_node_empty_strategy','抛错并提示（默认档）','ERROR','danger',2,0,'NORMAL',NULL,0),
  (1,'wf_node_empty_strategy','转租户管理员兜底','ADMIN','warning',3,0,'NORMAL',NULL,0),
  (1,'wf_node_empty_strategy','自动跳过该节点','SKIP','info',4,0,'NORMAL',NULL,0);

-- §2.4 加签位置（wf_add_sign.position）
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'wf_addsign_position','前加签（当前环节之前插入）','BEFORE','primary',1,0,'NORMAL',NULL,0),
  (1,'wf_addsign_position','后加签（当前环节之后插入）','AFTER','info',2,1,'NORMAL',NULL,0);

-- §2.5 加签状态
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'wf_addsign_status','待处理','PENDING','warning',1,1,'NORMAL',NULL,0),
  (1,'wf_addsign_status','进行中','ACTIVE','primary',2,0,'NORMAL',NULL,0),
  (1,'wf_addsign_status','已完成','DONE','success',3,0,'NORMAL',NULL,0),
  (1,'wf_addsign_status','已拒绝','REJECTED','danger',4,0,'NORMAL',NULL,0),
  (1,'wf_addsign_status','已取消','CANCELLED','info',5,0,'NORMAL',NULL,0);

-- §2.6 审批项类别（对齐 wf_approval_item.category 现有两档 + 预留终止档）
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'wf_approval_item_category','通过类','POSITIVE','success',1,1,'NORMAL',NULL,0),
  (1,'wf_approval_item_category','否定类','REJECT','danger',2,0,'NORMAL',NULL,0);

-- §2.7 通用启用状态（全库 status 列实测取值）
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'sys_status','正常','NORMAL','success',1,1,'NORMAL',NULL,0),
  (1,'sys_status','停用','DISABLE','info',2,0,'NORMAL',NULL,0);

-- §2.8 通用是否
INSERT INTO fssc.sys_dict_data
  (tenant_id, dict_type, dict_label, dict_value, css_class, sort, is_default,
   status, create_by, del_flag)
VALUES
  (1,'dict_yes_no','是','YES','success',1,1,'NORMAL',NULL,0),
  (1,'dict_yes_no','否','NO','info',2,0,'NORMAL',NULL,0);

-- ----------------------------------------------------------------------------
-- §3 执行后复点（期望 8 个类型均有数据；expense_type 保持 3 行不动）
-- ----------------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) AS n FROM fssc.sys_dict_data
--  WHERE del_flag=0 GROUP BY dict_type ORDER BY dict_type;
--
-- 校验：容器与数据是否一一对应（期望无孤儿：data 的 dict_type 都能在 type 里找到）
-- SELECT DISTINCT d.dict_type FROM fssc.sys_dict_data d
--  WHERE d.del_flag=0
--    AND NOT EXISTS (SELECT 1 FROM fssc.sys_dict_type t
--                     WHERE t.dict_type=d.dict_type AND t.del_flag=0);