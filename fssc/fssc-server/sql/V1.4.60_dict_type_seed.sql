-- =============================================================================
-- V1.4.60 字典类型 sys_dict_type 灌 seed（P0-3）
--
-- 目的：让"枚举字典收口"这条改造有目标表可落。
--       实测（2026-10-15）：sys_dict_type **0 行**，而 sys_dict_data 已有 3 行
--       （dict_type='expense_type'：TRAVEL/差旅费、OFFICE/办公费、ENTERTAIN/招待费）。
--       ⇒ 字典项在游、类型容器空 —— 前端 api.js:937-941 的 dict CRUD
--         （typePage/typeCreate/typeUpdate/typeDelete/dataList）在界面上管不到任何东西。
--       本件补齐类型容器，并预置后续枚举收口要用的类型骨架（is_builtin=1，租户不可删）。
--
-- 为什么只灌 type 不灌 data：
--   · data 的枚举值必须逐域对业务语义落（哪些状态是"已终态"、哪些是"在途"），
--     由各业务域 owner 判定，本件不代判，避免造出错误的枚举值污染渲染层；
--   · 本件只补"容器"，让后续每收一个枚举都有地方落、有 UI 可管。
--
-- 影响面：sys_dict_type 目前无任何运行时读取分支（本轮全仓 grep 仅 Controller/Service/Mapper
--         走 MyBatis-Plus 通用 CRUD），故本件落库**不改变任何现有页面行为**，
--         唯一效果是让已注册的 dict 管理接口有数据可管。
-- =============================================================================

-- ----------------------------------------------------------------------------
-- §1 前置复点（只读；期望：types=0，data_types=1 且为 expense_type）
-- ----------------------------------------------------------------------------
-- SELECT COUNT(*) AS type_rows FROM fssc.sys_dict_type WHERE del_flag = 0;
--   期望 0
-- SELECT dict_type, COUNT(*) AS n FROM fssc.sys_dict_data WHERE del_flag = 0 GROUP BY dict_type;
--   期望 expense_type / 3

-- ----------------------------------------------------------------------------
-- §2 执行段（幂等：先删同 tenant 同 dict_type，再插）
-- ----------------------------------------------------------------------------
DELETE FROM fssc.sys_dict_type
 WHERE tenant_id = 1
   AND dict_type IN ('expense_type','wf_node_multi_type','wf_node_empty_strategy',
                     'wf_addsign_position','wf_addsign_status','wf_approval_item_category',
                     'sys_status','dict_sex','dict_yes_no');

INSERT INTO fssc.sys_dict_type
  (tenant_id, dict_type, dict_name, is_builtin, status, remark, create_by, del_flag)
VALUES
  -- ① 补齐孤儿容器：data 已有 3 行但 type 缺失
  (1, 'expense_type', '费用类型', 1, 'NORMAL',
   '2026-10-15 P0-3：补齐孤儿容器。sys_dict_data 已有 TRAVEL/OFFICE/ENTERTAIN 三项但 type 表空，'
   '导致 dict 管理界面管不到任何东西。', NULL, 0),

  -- ② 以下为"枚举字典收口"的类型骨架，当前 data 暂空，供各域逐步迁入
  (1, 'wf_node_multi_type', '会签模式', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。取值域与 wf_node_config.multi_type 一致：ANY/ALL/VOTE/SEQ。'
   '对应 wf_assignee_scheme 的 OR/AND/EXCLUDE 语义在 dim 侧，不在本字典。', NULL, 0),

  (1, 'wf_node_empty_strategy', '空审批人兜底策略', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。取值域与 wf_node_config.empty_strategy 一致：'
   'ESCALATE（上报上级）/ERROR（报错阻断）/SKIP（跳过）。实测分布 168/7/1。', NULL, 0),

  (1, 'wf_addsign_position', '加签位置', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。取值域与 wf_add_sign.position 一致：BEFORE（前加签）/AFTER（后加签）。', NULL, 0),

  (1, 'wf_addsign_status', '加签状态', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。取值域与 wf_add_sign.status 一致：'
   'PENDING/ACTIVE/DONE/CANCELLED/FAILED。wf_add_sign 表当前 0 行，本字典先立容器。', NULL, 0),

  (1, 'wf_approval_item_category', '审批项类别', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。取值域与 wf_approval_item.category 一致：'
   'POSITIVE（通过并继续）/REJECT（驳回，可逆向运行）/TERMINATE（终止）。', NULL, 0),

  (1, 'sys_status', '通用启用状态', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。NORMAL（正常）/DISABLED（停用）。'
   '与 wf_node_config.enabled 的 0/1 数值型并存，此处为字符串型通用口径。', NULL, 0),

  (1, 'dict_yes_no', '是否（通用）', 1, 'NORMAL',
   '2026-10-15 P0-3 新建。Y/N，用于替代各处裸 0/1 展示。', NULL, 0);

-- ----------------------------------------------------------------------------
-- §3 后置复点（期望 8 行，且 expense_type 能与 sys_dict_data 3 行对上）
-- ----------------------------------------------------------------------------
-- SELECT dict_type, dict_name, is_builtin FROM fssc.sys_dict_type
--  WHERE tenant_id = 1 AND del_flag = 0 ORDER BY dict_type;
--
-- 孤儿检查（期望 0 行：有 data 无 type 的 dict_type）
-- SELECT DISTINCT d.dict_type
--   FROM fssc.sys_dict_data d
--   LEFT JOIN fssc.sys_dict_type t
--     ON t.tenant_id = d.tenant_id AND t.dict_type = d.dict_type AND t.del_flag = 0
--  WHERE d.del_flag = 0 AND t.id IS NULL;

-- =============================================================================
-- 回滚
-- =============================================================================
-- 本件新增 8 行（含 1 行是补孤儿容器），回滚即整体删除：
-- DELETE FROM fssc.sys_dict_type
--  WHERE tenant_id = 1
--    AND dict_type IN ('expense_type','wf_node_multi_type','wf_node_empty_strategy',
--                      'wf_addsign_position','wf_addsign_status','wf_approval_item_category',
--                      'sys_status','dict_yes_no');
--
-- 回滚后 sys_dict_type 回到 0 行，sys_dict_data 的 3 行 expense_type 恢复为"孤儿"状态
-- （与本件执行前完全一致 —— 本件未触碰 sys_dict_data）。
--
-- 回滚后复点
-- SELECT COUNT(*) FROM fssc.sys_dict_type WHERE tenant_id = 1 AND del_flag = 0;  -- 期望 0
-- SELECT COUNT(*) FROM fssc.sys_dict_data  WHERE tenant_id = 1 AND del_flag = 0;  -- 期望 3（未被本件改动）