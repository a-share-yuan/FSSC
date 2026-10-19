-- =====================================================================================
-- V1.4.17  bg_occupy 期初结转（seed）—— 让「预算行占用汇总」与「占用流水」账实相符
-- 日期：2026-09-21    前置：V1.4.16_masterdata_realignment.sql 已执行
-- -------------------------------------------------------------------------------------
-- 问题（缺陷审计 B 系列 / P0-C2 复核实测）：
--   bg_item.occupy_amount 由 BudgetService.occupy/release/writeOff 与 bg_occupy 流水**成对**维护
--   （appendFlow + addAmount），因此应用自身保证了不变式
--       I1：bg_item.occupy_amount == Σ bg_occupy.amount（同 budget_item_id，全流水，含正负）
--   但演示库的 1324 条历史预算行是初始化直接写入汇总数、从未走过流水：
--   实测 1450 条预算行中 **238 条 occupy_amount > Σ 流水**（合计 ¥2,105,160.35），
--   且 1448 条完全没有流水。
--   后果：P0-C2 之后核销按「单据在该预算行的净占用」推导，历史占用不可见 → 这些行的占用
--   永远不会被核销/释放冲减，也无法向审计解释占用构成（无法穿透到单据）。
--
--   初始化时把既有余额以「期初结转」凭证式记录进流水，而不是改写历史单据。
--   本脚本照此办理：对每条不平的预算行补 **一条** OCCUPY 流水，
--   biz_type='INIT_CARRY'、biz_id=0（bg_occupy.biz_id 为 NOT NULL，用 0 作期初哨兵）、
--   biz_line_id=0（同理：期初结转无单据行，用 0 而非 NULL，便于 4.3「biz_line_id NOT NULL」落地时不冲突）。
--
-- 与业务链路的隔离性（为什么不会污染核销/释放）：
--   BgOccupyMapper 的两条账本查询 sumNetOccupyByItemAndBiz / sumOccupiedByItemAndBiz
--   都带 `biz_id = #{bizId}` 且 `biz_type = #{bizType} 或 biz_type IN (费用域白名单)`；
--   真实单据 id 恒 > 0，'INIT_CARRY' 也不在 EXP_CLAIM/EXP_PAY/EXP_APPLY/EXP_LOAN/EXP_REPAY/TR_PAY 白名单内
--   → 这些结转行**永远不会**被任何单据的净占用计算命中。
--
-- 不做的事（重要）：
--   1) **不改 bg_item 的任何汇总数**（total/adjust/occupy/used 全部保持原值 → 可用额度不变 → 业务行为零变化）；
--   2) **不给 used_amount 补流水**。实测 635 条行有 used_amount 但无 WRITE_OFF 流水（合计 ¥24,611,523.68）。
--      这不是缺陷：BudgetService.writeOff 对"该单据从未占用过"的线下直报分支就是只加已用、不写核销流水
--      （BudgetService:537-545），因此 I2（used == Σ|WRITE_OFF|）**本就不是系统不变式**。
--      若强行补成对 OCCUPY+WRITE_OFF，等于凭空造出从未发生过的占用与核销，属篡改账簿；
--      这批数据改由 V1.4.17_used_no_ledger.sql 出**只读清单**交财务认定。
--   3) 不动任何业务事实表。
--
-- 幂等：每条预算行至多补一行（NOT EXISTS 按 biz_type='INIT_CARRY' 守卫），可重复执行。
-- 回滚：V1.4.17_bg_occupy_opening_carryover_rollback.sql（按 biz_type='INIT_CARRY' 删除，只删本次新增行）
-- 备份：自动化测试/backup/fssc_masterdata_20260921_1950.sql
-- 备注：bg_occupy 按 create_time 月分区；本次结转行落在执行时刻所在分区，
--       期间发生额类报表须按 biz_type <> 'INIT_CARRY' 过滤，已在 remark 中标注。
-- =====================================================================================

START TRANSACTION;

-- 1) 占用侧残差为正 → 补期初占用流水（OCCUPY 正数，与 BudgetService.occupy 的记账方向一致）
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id,
     oper_user_id, remark, create_time)
SELECT i.tenant_id, i.id, 'OCCUPY',
       i.occupy_amount - COALESCE(f.s, 0),
       'INIT_CARRY', 0, 'INIT-CARRY', 0,
       1,
       CONCAT('V1.4.17 期初结转：预算行既有占用 ', i.occupy_amount - COALESCE(f.s, 0),
              ' 无历史流水（初始化直写汇总数），按厂商"预算期初占用"口径补记；非本期发生额，报表按 biz_type 过滤 INIT_CARRY'),
       NOW()
FROM bg_item i
LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f
       ON f.budget_item_id = i.id
WHERE i.del_flag = 0
  AND i.occupy_amount - COALESCE(f.s, 0) > 0
  AND NOT EXISTS (SELECT 1 FROM bg_occupy o
                  WHERE o.budget_item_id = i.id AND o.biz_type = 'INIT_CARRY');

-- 2) 占用侧残差为负（汇总数小于流水，历史上被多扣）→ 补释放流水（负数，与 release() 记账方向一致）
--    实测当前 0 条；保留分支使脚本对后续复跑同样成立。
INSERT INTO bg_occupy
    (tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, biz_line_id,
     oper_user_id, remark, create_time)
SELECT i.tenant_id, i.id, 'RELEASE',
       i.occupy_amount - COALESCE(f.s, 0),          -- 负数
       'INIT_CARRY', 0, 'INIT-CARRY', 0,
       1,
       CONCAT('V1.4.17 期初结转：预算行占用汇总小于流水合计，补释放差额 ', i.occupy_amount - COALESCE(f.s, 0),
              ' 使 I1 成立；非本期发生额，报表按 biz_type 过滤 INIT_CARRY'),
       NOW()
FROM bg_item i
LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f
       ON f.budget_item_id = i.id
WHERE i.del_flag = 0
  AND i.occupy_amount - COALESCE(f.s, 0) < 0
  AND NOT EXISTS (SELECT 1 FROM bg_occupy o
                  WHERE o.budget_item_id = i.id AND o.biz_type = 'INIT_CARRY');

COMMIT;

-- =====================================================================================
-- 执行后验收（只读，期望值见 expect 列）
-- =====================================================================================
-- H) 不变式 I1 破坏行数            → 期望 0
-- I) 结转行数                      → 期望 238
-- J) 结转金额合计                  → 期望 2105160.35
-- K) 结转行是否可能被单据口径命中   → 期望 0（biz_id>0 或 biz_line_id 非 0 的 INIT_CARRY 行，必须一条都没有）
-- M) used 无核销流水（只报告不改） → 期望 635 行 / 24611523.68
--    注：bg_item 的 total/adjust/occupy/used 四列本脚本零写入，可用额度不变（可另跑
--        SELECT COUNT(*) FROM bg_item WHERE del_flag=0; → 期望仍为 1450）
SELECT 'I1_break_rows' k, COUNT(*) v, 0 expect FROM bg_item i
  LEFT JOIN (SELECT budget_item_id, SUM(amount) s FROM bg_occupy GROUP BY budget_item_id) f ON f.budget_item_id=i.id
  WHERE i.del_flag=0 AND i.occupy_amount <> COALESCE(f.s,0)
UNION ALL SELECT 'carryover_rows', COUNT(*), 238 FROM bg_occupy WHERE biz_type='INIT_CARRY'
UNION ALL SELECT 'carryover_amount', COALESCE(SUM(amount),0), 2105160.35 FROM bg_occupy WHERE biz_type='INIT_CARRY'
UNION ALL SELECT 'carryover_visible_to_bills', COUNT(*), 0 FROM bg_occupy WHERE biz_type='INIT_CARRY' AND (biz_id>0 OR biz_line_id IS NOT NULL AND biz_line_id<>0)
UNION ALL SELECT 'used_no_ledger_rows', COUNT(*), 635 FROM bg_item i WHERE i.del_flag=0
    AND i.used_amount <> COALESCE((SELECT SUM(-o.amount) FROM bg_occupy o WHERE o.budget_item_id=i.id AND o.oper_type='WRITE_OFF' AND o.biz_type<>'INIT_CARRY'),0)
UNION ALL SELECT 'used_no_ledger_amount', COALESCE(SUM(t.d),0), 24611523.68 FROM (
    SELECT i.used_amount - COALESCE((SELECT SUM(-o.amount) FROM bg_occupy o WHERE o.budget_item_id=i.id AND o.oper_type='WRITE_OFF' AND o.biz_type<>'INIT_CARRY'),0) d
    FROM bg_item i WHERE i.del_flag=0
      AND i.used_amount <> COALESCE((SELECT SUM(-o.amount) FROM bg_occupy o WHERE o.budget_item_id=i.id AND o.oper_type='WRITE_OFF' AND o.biz_type<>'INIT_CARRY'),0)) t;
