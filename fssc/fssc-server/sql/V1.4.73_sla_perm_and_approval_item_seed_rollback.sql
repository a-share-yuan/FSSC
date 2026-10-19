-- =============================================================================
-- V1.4.73 回滚  八类差距收尾①：SLA 维护权限码 + wf_approval_item 首批审批项种子
-- 日期：2026-10-17
-- 说明：审批项按 (tenant_id, model_key, node_key, item_code) 精确定位删除，
--       只删本次灌入的 9 条，不影响他人后续新增的同名以外行。
-- =============================================================================

-- 1) 回收新增的 3 个 SLA 权限码授权
DELETE FROM sys_role_menu WHERE menu_id IN (3775, 3776, 3777);

-- 2) 删权限码本身
DELETE FROM sys_menu WHERE id IN (3775, 3776, 3777) AND perms IN ('gl:sla:create', 'gl:sla:update', 'gl:sla:delete');

-- 3) 删本次 9 条审批项种子
DELETE FROM wf_approval_item
WHERE tenant_id = 1 AND model_key = 'CLAIM'
  AND (node_key, item_code) IN (
    ('Task_2',  'SCAN_PASS'),
    ('Task_2',  'SCAN_RETURN'),
    ('Task_2',  'SCAN_VOID'),
    ('Task_7',  'FS_PASS'),
    ('Task_7',  'FS_ADJUST'),
    ('Task_7',  'FS_REJECT'),
    ('Task_13', 'GM_PASS'),
    ('Task_13', 'GM_REJECT'),
    ('Task_13', 'GM_TERMINATE')
  );
