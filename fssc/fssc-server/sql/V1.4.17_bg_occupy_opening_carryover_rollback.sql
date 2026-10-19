-- =====================================================================================
-- V1.4.17  bg_occupy 期初结转（rollback）
-- 与 V1.4.17_bg_occupy_opening_carryover.sql 配对。
-- 只删本次结转新增的行（biz_type='INIT_CARRY' 为该脚本独占标签），
-- 真实业务流水（EXP_CLAIM/EXP_PAY/… 及将来由单据产生的占用/核销/释放）一律不动。
-- 最后防线：自动化测试/backup/fssc_bg_occupy_pre_v1417_20260921.sql（mysqldump 原表 41 行）
-- =====================================================================================

START TRANSACTION;

DELETE FROM bg_occupy WHERE biz_type = 'INIT_CARRY';

COMMIT;

-- 回滚后验收：期望 0 / 0
SELECT 'carryover_rows_left' k, COUNT(*) v, 0 expect FROM bg_occupy WHERE biz_type='INIT_CARRY'
UNION ALL SELECT 'carryover_amount_left', COALESCE(SUM(amount),0), 0 FROM bg_occupy WHERE biz_type='INIT_CARRY';

-- 回滚后 I1 会重新不平（238 行），属预期：撤销结转即回到"汇总数无流水支撑"的原状。
-- 确认无需保留后手工清理备份表/文件即可。
