-- =============================================================================
-- V1.6.99 下线「表单设计」菜单入口
-- =============================================================================
-- 背景：/form/design 的页面组件尚未提供，菜单已登记 ⇒ 用户点进去会落到
--       /form/:formCode 通配路由，被当作 formCode=design 查元数据，报
--       「表单不存在：design」。菜单入口必须先于页面下线。
--
-- 范围：只回收「表单设计」这一条入口及其权限点；
--       /form/my（我的申请）与客户表单菜单保留不动。
-- 可重放：全部按存在性判断，重复执行无副作用。
-- =============================================================================

-- ① 先解绑角色授权（外键方向：sys_role_menu → sys_menu）
DELETE FROM sys_role_menu
 WHERE menu_id IN (SELECT id FROM sys_menu
                    WHERE path = '/form/design'
                       OR perms IN ('form:design:config', 'form:design:save', 'form:design:menu'));

-- ② 再删菜单与权限点
DELETE FROM sys_menu
 WHERE path = '/form/design'
    OR perms IN ('form:design:config', 'form:design:save', 'form:design:menu');

-- =============================================================================
-- 回滚：重新登记该入口（仅在页面组件已提供时使用）
-- =============================================================================
-- INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon,
--                       feature_code, terminal, sort, visible, status, create_time, del_flag)
-- SELECT 3032, '表单设计', 'C', '/form/design', 'form/FormDesign', 'form:design:config',
--        'SetUp', 'FC_FORM_PLATFORM', 'PC', 9, 1, 1, NOW(), 0
--   FROM DUAL
--  WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/form/design');
