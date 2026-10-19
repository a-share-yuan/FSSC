-- ============================================================
-- V1.4.80 回滚 — LinkU 开放接口 · AR 销售订单/出库发货源表
--
--  回滚前置判据：确认三表内无已落库业务数据（或已另行备份）后再执行。
--   下面 §0 查询任一不为 0 即先停手。
--  仅回滚本脚本新建的三张表；不得波及 crm_customer / md_supplier 等既有对象。
-- ============================================================

SET NAMES utf8mb4;

-- §0 数据核对（任一不为 0 先停手）
SELECT (SELECT COUNT(*) FROM so_order)      AS so_order_rows,
       (SELECT COUNT(*) FROM so_order_line) AS so_order_line_rows,
       (SELECT COUNT(*) FROM so_shipment)   AS so_shipment_rows;

-- §1 删表（依赖顺序：先行后头）
DROP TABLE IF EXISTS so_shipment;
DROP TABLE IF EXISTS so_order_line;
DROP TABLE IF EXISTS so_order;

-- §2 复核（应返回空集）
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('so_order', 'so_order_line', 'so_shipment');
