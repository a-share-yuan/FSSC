-- =============================================================================
-- 跨环境执行汇总件  2026-10-27 放行窗（本窗库面变更的全部）
-- -----------------------------------------------------------------------------
-- 来源：本窗「审批工作台菜单图标缺失」修复（正本＝V1.4.97_menu_icon_approval_workbench.sql）
--       ＋「开发库写路径 fsync 降档」（运行期参数，非数据结构变更）
-- 执行方式：mysql -h <host> -P <port> -u <user> -p <目标库名> < 本件
--           （不要把本文件里的表名改成带库名前缀——目标环境的库名由你在命令行给）
--
-- 本件【不含】代码变更。下列改动只在应用侧，必须随 jar／前端发布，不是 SQL：
--   1) sys_config 读缓存        ConfigService.getValue（写入口 create/update/setValue 挂 evict）
--   2) wf_node_config 全表读合并 WfNodeConfigResolver.allCacheKey/evictAll
--      ＋读侧 WfNodeConfigResolver / WfPerfService，写侧 WfNodeConfigService / WfAssigneeReplaceService
--   3) dev SQL stdout 关闭       application-dev.yml  log-impl=${FSSC_MYBATIS_LOG_IMPL:...NoLoggingImpl}
--   4) 菜单树每页只取一次        fssc-web：router/pathAccess.js(ensureMenuTree) / Layout.vue / ProcessNavMap.vue
--   5) 待办徽标改服务端计数      fssc-web：components/HeaderBadges.vue ＋ api.js(todoPage 透传 cfg / submit 走 longWrite)
--   6) 提交审批防重复点击        fssc-web：BillWorkbench.vue(pendingWrite) / H5ExpenseCreate.vue(busy 前置)
--
-- 实测口径（本窗，禁止别在别的机器引作现况）：改前 page_menus_without_icon=1（id=3916）；
--   改后＝0，sys_menu 共 695 行；业务键 WHERE 命中 1 行。
-- =============================================================================

-- 先声明连接字符集：本件按中文菜单名定位，客户端若以 latin1 送出 UTF-8 字节，
--   WHERE menu_name='审批工作台' 会静默匹配 0 行＝看着执行成功、实际没改。
SET NAMES utf8mb4;


-- -----------------------------------------------------------------------------
-- 第 0 节  前置复点（先在目标库只读跑这一段，确认目标确实是同一套菜单口径）
-- -----------------------------------------------------------------------------
-- 期望：match_by_bizkey=1；null_icon_before=1（若已是 0，说明该环境已修，第 1 节会是 0 行更新，属正常）
SELECT COUNT(*) AS match_by_bizkey
  FROM sys_menu
 WHERE menu_type = 'C' AND path = '/approval' AND menu_name = '审批工作台' AND del_flag = 0;

SELECT COUNT(*) AS null_icon_before
  FROM sys_menu
 WHERE menu_type = 'C' AND status = 1 AND del_flag = 0
   AND path IS NOT NULL AND path <> '' AND (icon IS NULL OR icon = '');

-- 该行的现状（icon 应为 NULL；menu_type 应为 C）
SELECT id, parent_id, menu_name, path, menu_type, status, visible, del_flag, icon
  FROM sys_menu
 WHERE menu_type = 'C' AND path = '/approval';


-- -----------------------------------------------------------------------------
-- 第 1 节  数据变更：审批工作台菜单补图标
-- -----------------------------------------------------------------------------
-- 根因：sys_menu 该行 icon=NULL，而前端 MenuTree.vue 是 `v-if="n.icon"` ⇒ 值为空就整枚 <el-icon> 不输出，
--       界面上表现为"这一项前面是空的"。禁止前端兜底默认图标（那是加机制绕数据缺项）。
-- 取 Stamp（印章＝审批语义）。必须是 Element Plus 图标名，且目标环境前端的 @element-plus/icons-vue
--   必须真的导出该名——填不存在的名字时 `component :is` 解析失败，界面症状与本次缺陷一模一样。
--   同目录在册用法：Connection / Share / List / Document / Bell / Finished / Promotion / DataBoard /
--   Monitor / Timer / Odometer。若目标环境要换名，只能换成上述这类"已在册且在用"的名字。
-- 禁止按 id 定位（id 是各环境自增的，本仓库＝3916）；一律走业务键。
-- 预期影响行数＝1

UPDATE sys_menu
   SET icon = 'Stamp'
 WHERE menu_type = 'C'
   AND path = '/approval'
   AND menu_name = '审批工作台'
   AND del_flag = 0;


-- -----------------------------------------------------------------------------
-- 第 2 节  自检（必须看到 after=0；行内容 icon='Stamp'）
-- -----------------------------------------------------------------------------
SELECT id, menu_name, path, icon FROM sys_menu
 WHERE menu_type = 'C' AND path = '/approval' AND del_flag = 0;

SELECT COUNT(*) AS page_menus_without_icon
  FROM sys_menu
 WHERE menu_type = 'C' AND status = 1 AND del_flag = 0
   AND path IS NOT NULL AND path <> '' AND (icon IS NULL OR icon = '');

-- 若上面仍 >0：说明目标环境还有别的页面菜单缺图标。本件不自动填（每枚图标的语义要业务裁定），
-- 先取这份清单交人裁：
SELECT id, parent_id, menu_name, path
  FROM sys_menu
 WHERE menu_type = 'C' AND status = 1 AND del_flag = 0
   AND path IS NOT NULL AND path <> '' AND (icon IS NULL OR icon = '')
 ORDER BY parent_id, id;


-- -----------------------------------------------------------------------------
-- 第 3 节  环境项（可选）：开发/测试库写路径降 fsync
-- -----------------------------------------------------------------------------
-- 为什么：本窗实测这台库「每条语句固定成本」极高——最简 UPDATE avg 9~12ms、COMMIT avg 2.79ms，
--   而出包普查里一个提交类请求要发 80~190 条 SQL ⇒ 秒级耗时主要来自往返次数 × 固定成本，不是缺索引。
--   双 fsync（innodb_flush_log_at_trx_commit=1 且 sync_binlog=1）＝每笔提交两次落盘同步。
-- 效果（本窗实测，13 枚心跳样本）：UPDATE avg 11.85→3.62ms / 9.06→2.39ms；COMMIT 2.79→0.08ms。
-- 代价：崩溃时可能丢最近 1 秒事务、binlog 不再逐笔落盘 ⇒ **只可用于开发/测试库**。
--   禁止生产库不要执行本节。生产要的是同一份「去重/加缓存」的应用改动，而不是牺牲持久性。
-- SET GLOBAL 只在运行期生效：MySQL 重启即回到 my.cnf 的值（要长期保留请改配置文件，本件不动配置文件）。
-- 生效只影响**新建连接**；连接池里的旧连接仍是旧会话值（要立刻见效可回收连接池/重启应用）。

SELECT @@innodb_flush_log_at_trx_commit AS before_flag_trx, @@sync_binlog AS before_flag_binlog;

SET GLOBAL innodb_flush_log_at_trx_commit = 2;
SET GLOBAL sync_binlog = 0;

SELECT @@innodb_flush_log_at_trx_commit AS after_flag_trx, @@sync_binlog AS after_flag_binlog;


-- -----------------------------------------------------------------------------
-- 第 4 节  回滚（按需单独执行，禁止与上面各节同跑）
-- -----------------------------------------------------------------------------
-- 4.1 撤图标（回到本窗执行前的 NULL 态；缺陷会复现）
-- UPDATE sys_menu SET icon = NULL
--  WHERE menu_type = 'C' AND path = '/approval' AND menu_name = '审批工作台' AND del_flag = 0;

-- 4.2 恢复双 fsync（本窗改前实测值＝1 / 1）
-- SET GLOBAL innodb_flush_log_at_trx_commit = 1;
-- SET GLOBAL sync_binlog = 1;


-- -----------------------------------------------------------------------------
-- 尾注：本窗做过的其它库动作，不要在目标环境当"变更"重放
-- -----------------------------------------------------------------------------
-- TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
--   ＝只是把语句摘要计数清零以开测量窗（本机取证动作），不是业务变更；重放会把对方环境的观测基线抹掉。
-- 本窗另有整表镜像留底：sys_menu 695 行全量 INSERT（本仓库 db/Dump20261027_win_icon/fssc_win_sys_menu.sql），
--   目标环境执行前请自行为 sys_menu 做同等级镜像。
