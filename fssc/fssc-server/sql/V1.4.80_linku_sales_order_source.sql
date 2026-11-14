-- ============================================================
-- V1.4.80 — LinkU 开放接口 · AR 销售订单/出库发货源表（P0-C 依赖）
--
-- 背景：《AR 三单匹配改造》P0-C 要求 AR 侧改走「销售订单 + 销售出库发货单 + 销项发票」三环节。
--       FSSC 现役库**不存在** so_order / so_order_line / so_shipment（全仓零命中），
--       开放端点 /api/open/linku/query/sales-orders、/query/sale-shipments 目前经
--       LinkuOpenQueryService 表存在性探测后返回空（不伪造数据）。
--       本脚本补齐源表，使 BCP 侧 SalesOrderSyncService 可经 HTTP 通道落数。
--
-- 列契约来源：LinkuOpenQueryMapper.selectSalesOrderLines / selectSaleShipments 的 SELECT
--             （列别名即 BCP 消费键，两端逐字对齐）。
-- 伙伴关联：customer_id → crm_customer.id（该表已存在，勿在本脚本内改动）。
--
-- 表归属：销售订单/出库单属 ERP/销售模块域，**须由业务/上游确认规则后写入数据库**。
--    本脚本为候选契约稿，未在任何环境执行；如需本地联调，可先在本库执行本脚本。
-- 去重：CREATE TABLE IF NOT EXISTS，可重复执行（不 DROP、不改既有对象）。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- §0 预检（只读）：确认三表当前不存在
-- ============================================================
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('so_order', 'so_order_line', 'so_shipment');

-- ============================================================
-- §1 销售订单头（订单环节；行级查询以头为关联）
-- ============================================================
CREATE TABLE IF NOT EXISTS so_order (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT        NULL COMMENT '租户 ID',
    company_id    BIGINT        NULL COMMENT '公司 ID',
    so_no         VARCHAR(64)   NOT NULL COMMENT '销售订单号（开放接口 soNo）',
    customer_id   BIGINT        NULL COMMENT '客户 ID（crm_customer.id）',
    customer_name VARCHAR(200)  NULL COMMENT '客户名称（报文 customerName）',
    order_date    DATE          NULL COMMENT '订单日期（报文 soDate）',
    status        VARCHAR(32)   NULL COMMENT 'OPEN/CONFIRMED/CLOSED/CANCELLED（报文 soStatus）',
    total_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '价税合计（报文 totalAmount）',
    tax_amount    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额（报文 taxAmount）',
    remark        VARCHAR(500)  NULL COMMENT '备注（报文 remark）',
    create_by     BIGINT        NULL,
    create_time   DATETIME      NULL,
    update_by     BIGINT        NULL,
    update_time   DATETIME      NULL,
    del_flag      INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 删除',
    UNIQUE KEY uk_so_order_no (tenant_id, so_no),
    KEY idx_so_order_company (company_id, order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT '销售订单头（AR 三单匹配订单腿数据源，P0-C）';

-- ============================================================
-- §2 销售订单行（订单环节行级）
-- ============================================================
CREATE TABLE IF NOT EXISTS so_order_line (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    so_id         BIGINT        NOT NULL COMMENT '销售订单头主键（so_order.id）',
    line_no       VARCHAR(32)   NOT NULL COMMENT '行号（报文 lineNo）',
    material_code VARCHAR(64)   NULL COMMENT '物料代码（报文 materialCode）',
    material_name VARCHAR(255)  NULL COMMENT '物料名称（报文 materialName）',
    quantity      DECIMAL(18,4) NULL COMMENT '数量（报文 quantity）',
    unit          VARCHAR(16)   NULL COMMENT '计量单位（报文 unit）',
    unit_price    DECIMAL(18,6) NULL COMMENT '含税单价（报文 unitPrice）',
    amount        DECIMAL(18,2) NULL COMMENT '行金额（不含税，报文 amount）',
    tax_rate      DECIMAL(9,4)  NULL COMMENT '税率，如 0.13（报文 taxRate）',
    tax_amount    DECIMAL(18,2) NULL COMMENT '税额（报文 lineTaxAmount）',
    shipped_qty   DECIMAL(18,4) NULL COMMENT '已发数量（发货腿维护；报文 shippedQty）',
    status        VARCHAR(32)   NULL COMMENT 'OPEN/PARTIAL/FULL/CLOSED（报文 lineStatus）',
    create_time   DATETIME      NULL,
    del_flag      INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 删除',
    UNIQUE KEY uk_so_order_line (so_id, line_no),
    KEY idx_so_order_line_so (so_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT '销售订单行（AR 三单匹配订单腿行级数据源，P0-C）';

-- ============================================================
-- §3 销售出库发货单（发货环节；行粒度，经 so_no 桥回订单环节）
-- ============================================================
CREATE TABLE IF NOT EXISTS so_shipment (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT        NULL COMMENT '租户 ID',
    company_id    BIGINT        NULL COMMENT '公司 ID',
    ship_no       VARCHAR(64)   NOT NULL COMMENT '发货单号（报文 sourceBillNo）',
    so_no         VARCHAR(64)   NULL COMMENT '销售订单号（发货腿→订单腿的桥，报文 soNo）',
    ship_line_no  VARCHAR(32)   NULL COMMENT '发货行号（报文 shipLineNo）',
    customer_id   BIGINT        NULL COMMENT '客户 ID（crm_customer.id）',
    customer_name VARCHAR(200)  NULL COMMENT '客户名称（报文 partnerName）',
    material_code VARCHAR(64)   NULL COMMENT '物料代码（报文 materialCode）',
    quantity      DECIMAL(18,4) NULL COMMENT '发货数量（报文 qty）',
    unit_price    DECIMAL(18,6) NULL COMMENT '含税单价（报文 unitPrice）',
    amount        DECIMAL(18,2) NULL COMMENT '发货金额（报文 amount）',
    status        VARCHAR(32)   NULL COMMENT '发货单状态（报文 status）',
    ship_date     DATE          NULL COMMENT '发货日期（报文 receiptDate）',
    create_by     BIGINT        NULL,
    create_time   DATETIME      NULL,
    update_by     BIGINT        NULL,
    update_time   DATETIME      NULL,
    del_flag      INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 删除',
    KEY idx_so_shipment_company (company_id, ship_date),
    KEY idx_so_shipment_so (so_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT '销售出库发货单（AR 三单匹配发货腿数据源，P0-C）';

-- ============================================================
-- §4 校验（只读）
-- ============================================================
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('so_order', 'so_order_line', 'so_shipment');

-- ============================================================
-- §5 本脚本刻意没做的事
--   ① 不预置任何业务数据行（源数据由 ERP/销售模块产生，避免"种子冒充同步"）；
--   ② 不改动 crm_customer / md_supplier（已存在，仅作关联）；
--   ③ 不加权限点/菜单（开放端点走匿名白名单 + HMAC 验签，无 UI 入口）。
-- ============================================================
