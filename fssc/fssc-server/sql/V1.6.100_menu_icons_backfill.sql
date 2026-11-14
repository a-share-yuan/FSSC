-- 侧边栏菜单图标补齐
-- sys_menu.icon 为空的页面/分组在侧栏只渲染文字、没有图形，与同级项对不齐；
-- 「我的费用单据」「共享作业控制台」「预检漏拦分析」以及「管理报表」下的 8 个分组都是这种状态。
-- 这里按语义补 Element Plus 图标名（前端 main.js 已全量注册该图标库），
-- 只填空值，已有图标的一律不动。
-- 路径为键而非写死 id：同一套菜单在不同交付库里 id 可能不同，path 是稳定标识。

UPDATE sys_menu SET icon = 'Files'     WHERE path = '/expense/my'              AND (icon IS NULL OR icon = '');
UPDATE sys_menu SET icon = 'SetUp'     WHERE path = '/sharing/shared-ops'      AND (icon IS NULL OR icon = '');
UPDATE sys_menu SET icon = 'Histogram' WHERE path = '/compliance/precheck-stats' AND (icon IS NULL OR icon = '');
UPDATE sys_menu SET icon = 'Folder'
 WHERE path IN ('/reports/g-expense', '/reports/g-ap', '/reports/g-ar', '/reports/g-cash',
                '/reports/g-loan', '/reports/g-contract', '/reports/g-ops', '/reports/g-voucher')
   AND (icon IS NULL OR icon = '');

-- 校验：应返回 0 行（可见的非按钮菜单都应有图标）
-- SELECT id, parent_id, menu_name, path FROM sys_menu
--  WHERE del_flag = 0 AND menu_type <> 'F' AND visible = 1 AND (icon IS NULL OR icon = '');
