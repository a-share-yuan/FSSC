-- ================================================================
-- V1.4.97  审批工作台菜单补图标（缺陷：侧栏「审批工作台」无图标）
-- ----------------------------------------------------------------
-- 现象（2026-10-27 真机实测，登录账号 100011）：左侧菜单里「审批工作台」这一项前面是空的，
--       同级的「我的单据 Document」「流程监控大屏 Monitor」「审批流看板 Odometer」都有图标。
-- 根因（库面实查，不是前端丢的）：
--       sys_menu id=3916（menu_name='审批工作台'，path='/approval'，parent_id=3550 流程中心）
--       的 icon 列 = NULL。渲染侧 MenuTree.vue:20 写的是 `v-if="n.icon"`，
--       值为空就整枚 <el-icon> 不输出 ⇒ 界面上"图标不见了"，与图标名写错、组件注册缺失都无关。
--       普查口径：menu_type='C' 且带 path 的页面菜单里，icon 为 NULL/空串的**只有这一枚**（1 枚 / 全量页面菜单）。
--       该行没有 INSERT 迁移脚本在册（db/sql、fssc-server/sql 全查无 '/approval' 插入语句），
--       是直接在界面「菜单管理」上建的 ⇒ 建菜单时漏填 icon，新环境重装也不会自愈，故落本迁移。
-- 规则：只补这一枚的 icon 值，不动 name/path/perms/授权面。取 Stamp（印章）＝审批语义，
--       且已核 @element-plus/icons-vue 当前安装版本导出 293 枚图标里含 Stamp
--       （拿不存在的图标名填进去＝component :is 解析失败，界面症状与本次缺陷一模一样）。
--       图标名一律走 Element Plus 图标名，与同目录其他行同格式（Connection/Document/Bell/Monitor…）。
-- 执行：属库面写入，须走放行窗；执行前先做 sys_menu 整表镜像，回滚件见文末。
-- 回滚见 V1.4.97_menu_icon_approval_workbench_rollback.sql。
-- ================================================================

UPDATE sys_menu
   SET icon = 'Stamp'
 WHERE id = 3916
   AND menu_name = '审批工作台'
   AND path = '/approval'
   AND menu_type = 'C';

-- 落章后自检：应返回 1 行且 icon='Stamp'；同时全库"带 path 的页面菜单无图标"应归零
SELECT id, menu_name, path, icon FROM sys_menu WHERE id = 3916;
SELECT COUNT(*) AS page_menus_without_icon
  FROM sys_menu
 WHERE menu_type = 'C' AND status = 1 AND del_flag = 0
   AND path IS NOT NULL AND path <> ''
   AND (icon IS NULL OR icon = '');
