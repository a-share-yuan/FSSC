-- =====================================================================================
-- V1.4.47 回滚：撤销采购订单行项目表 po_order_line
-- 与 V1.4.47_po_order_line_seed.sql 配对；本文件同样为【注释态】，未执行。
--  回滚会连带删除已录入的行项目数据。执行前必须先跑 §0 确认是否已有业务数据落在此表：
--    若下游（BCP 采购订单同步通道）已按行查询，撤表等于让通道退化为单头同步。
-- =====================================================================================

SET NAMES utf8mb4;

-- §0 前置（只读）：行数与涉及订单数，非 0 时须业务确认后方可回滚
SELECT COUNT(*) AS rows_to_lose FROM po_order_line;
SELECT COUNT(DISTINCT po_id) AS orders_affected FROM po_order_line;

-- §1 回滚
DROP TABLE IF EXISTS po_order_line;

-- §2 代码侧配套（同批回滚，否则启动即报表不存在）
--   purchase/entity/PoOrderLine.java、purchase/mapper/PoOrderLineMapper.java
--   PoOrder.lines 字段、PurchaseService 的 saveLines/loadLines 调用点
