-- =====================================================================================
-- V1.4.51 DB-1／／R5-D-254：fssc_image.img_setting 的 uk_setting 名为唯一实则非唯一
--          —— 补真实 UNIQUE 约束。本文件【已执行】
-- 执行时间：2026-10-07（收官后波次·主窗，用户已授权改库）
-- 执行人：测试执行代判（代判≠代签）
--
-- 【改前实测留值（2026-10-07 只读，r837/r838）】
--   1. information_schema.STATISTICS：INDEX_NAME='uk_setting' NON_UNIQUE=1，列组合
--  (tenant_id, setting_type, setting_code)，SEQ 1/2/3 齐全 ⇒ 名字承诺唯一、物理上不唯一（ 本体）。
--   2. 重复组合前置硬条件：GROUP BY tenant_id,setting_type,setting_code HAVING COUNT(*)>1 ⇒ dup_groups=0；
--      rows_total=5，SUM(del_flag)=0（无软删残留行）⇒ 可直接加约束，无需历史处置（D2 荐法第 1 步已过）。
--   3. 同库同族样板：img_batch 的 uk_tenant_batch_no 已是真唯一（NON_UNIQUE=0），
--      本仓库 60+ 张带 del_flag 的业务表唯一键一律不含 del_flag（md_subject/md_expense_item/…），
--      仅 ext_interface_config 一枚把 del_flag 纳进唯一键 ⇒ 本件沿用主流规则（不纳入 del_flag）。
--   4. 代码面：ImgSetting extends TenantAuditBase（@TableLogic 软删），
--      ImageSettingController.java:67-69 delete 走 deleteById ⇒ 软删。
--      因此"先删后重建同 (setting_type,setting_code)"会被本约束拦下（Duplicate entry），
--      这是全仓一致的既有语义（md_* 主数据同样如此），不是本件新引入的行为；
--      若要允许重建，需业务改规则为"复活原行"而非"插新行"，属人工裁决。
--
-- 【执行动作＝同名索引原地转唯一（不改变列组合，只改 NON_UNIQUE）】
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置只读（期望 dup_groups=0 才允许往下走）
SELECT COUNT(*) AS dup_groups FROM (
  SELECT tenant_id, setting_type, setting_code FROM fssc_image.img_setting
  GROUP BY tenant_id, setting_type, setting_code HAVING COUNT(*) > 1) x;

-- §1 改前留值
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='fssc_image' AND TABLE_NAME='img_setting' AND INDEX_NAME='uk_setting'
 GROUP BY INDEX_NAME, NON_UNIQUE;

-- §2 原地转唯一
ALTER TABLE fssc_image.img_setting DROP INDEX uk_setting, ADD UNIQUE INDEX uk_setting (tenant_id, setting_type, setting_code);

-- §3 复点（期望 NON_UNIQUE=0）
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='fssc_image' AND TABLE_NAME='img_setting' AND INDEX_NAME='uk_setting'
 GROUP BY INDEX_NAME, NON_UNIQUE;
SELECT COUNT(*) AS rows_after, COUNT(DISTINCT tenant_id, setting_type, setting_code) AS distinct_combo
  FROM fssc_image.img_setting;
