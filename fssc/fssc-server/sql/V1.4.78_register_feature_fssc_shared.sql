-- =============================================================================
-- V1.4.78  八类差距收尾⑨：补注册缺失的特性码 FSSC_SHARED（共享运营域整体 403 的根因）
-- 日期：2026-10-17
--
-- 【根因】系统用「特性码(feature_code)」做套餐校验：菜单挂 feature_code，端点加
--   @RequiresFeature("XXX")，租户套餐未开通该特性码 ⇒ 一律 4031「当前套餐未开通该功能」。
-- 实测发现 pf_feature（特性码登记表）里**根本没有 FSSC_SHARED 这一行**，
--   而 sys_menu 中有 13 条菜单挂着 feature_code='FSSC_SHARED'，
--   SharedOpsController 整类（员工信用 / SLA / 质检 / 满意度）也全部 @RequiresFeature("FSSC_SHARED")。
--   ⇒ 无论租户买哪个套餐（STANDARD / PRO），整个共享运营域都会被拦成 403，
--     这些功能**在 UI 上看得到菜单、点进去一律报错**，属「有菜单无功能」的假交付。
--
-- 【为什么本轮修】这正是 §4-⑨「op_sla_config 有数据无界面」的上游成因：
--   上一轮补的 SLA 维护端点实测仍返回 4031，不是端点问题，是特性码没注册。
--
-- 【影响面提示】本脚本会让共享运营域（SLA/质检/满意度/员工信用）从「全部 403」
--   变为「按界面权限正常可用」。这是修正数据缺陷、恢复已实现功能，不是新增权限。
-- 回滚：见同名 _rollback.sql
-- =============================================================================

-- 1) 注册特性码。id 取 9（现最大 8）；module 用既有取值域（SHARING），sort 130 接在 FSSC_ARCHIVE 之后。
INSERT INTO pf_feature
  (id, feature_code, feature_name, module, sort, remark, create_time, update_time, del_flag)
VALUES
  (9, 'FSSC_SHARED', '共享运营', 'SHARING', 130,
   '共享运营实质化域：员工信用 / SLA 监控与阈值配置 / 质检抽检 / 满意度调查', NOW(), NOW(), 0);

-- 2) 开通给两个套餐（STANDARD id=1 / PRO id=2）。
--    两个套餐都开：共享运营是该产品的基座能力，与 FSSC_ARCHIVE 同为「两个套餐都带」的先例
--    （见 pf_package_feature id=14/15），保持一致。
INSERT INTO pf_package_feature
  (package_id, feature_code, enabled, create_time, update_time, del_flag)
SELECT p.id, 'FSSC_SHARED', 1, NOW(), NOW(), 0
FROM pf_package p
WHERE p.package_code IN ('STANDARD', 'PRO')
  AND NOT EXISTS (
    SELECT 1 FROM pf_package_feature x
    WHERE x.package_id = p.id AND x.feature_code = 'FSSC_SHARED'
  );
