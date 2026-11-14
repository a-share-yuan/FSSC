-- =====================================================================================
-- V1.4.47  采购订单行项目表 po_order_line —— 全部为【注释态】，本文件未执行
-- 生成时间：2026-09-26（执行前须重跑 §0 只读校验，数据快照会随演练推进而失效）
-- 执行人：________（需业务/DBA 书面授权；本演练会话只读，未执行任何 CREATE/INSERT/UPDATE）
--
-- 【要解决的缺陷（实测，非推断）】
--   F-1  FSSC 有采购订单表但没有行表：information_schema 中 fssc 库 po% 只有
--        po_order / po_receipt；po_order 25 列里没有任何行级字段（无 line_no/quantity/unit_price）。
--   F-2  前端录入了行项目，后端整块丢弃：
--        fssc-web/src/views/purchase/PoOrder.vue:480  提交 { ...form, lines: formLines.value.filter(...) }
--        purchase/controller/PurchaseController.java:41  形参是 @RequestBody PoOrder o
--        purchase/entity/PoOrder.java                   无 lines 字段
--        ⇒ JSON 里的 lines 在反序列化阶段无人接收，从未写入数据库。
--   F-3  演练单 PO1790434820845（id=11）的两批明细只以文字存在于 remark：
--        remark = 「合同2026-09-23签订：不含税80,000（税率13%，价税90,400）。
--                  分两批：批一50,000（价税56,500）/批二30,000（价税33,900）；账期=货到票到后7日付款。」
--        amount = 80,000.00，status = APPROVED。
--   F-4  订单详情弹窗行项目恒空（viewDetail() 取 row.lines，后端从不返回）。
--
-- 【为什么要建这张表，而不是只在 BCP 侧建】
--   BCP 侧 bcp_purchase_order / bcp_purchase_order_line 结构早已齐备（bcp_schema.sql:885-917，
--   含 uk_po(partner_id,po_no) 唯一键），只是 0 行。缺的是"数据从哪来"：
--   FSSC 不落行项目 ⇒ 任何同步通道都只能搬单头 ⇒ SCP 接单/分批 ASN/开票对账无凭可查。
--   详见《BCP/docs/… 或 LinkU/docs/业务运行记录/V3引擎重构版/01/B-9补救方案_FSSC到BCP采购订单同步通道.md》。
--
-- 【规则】行金额与表头 amount 同为不含税（表头 80,000 = 不含税、remark 记税 10,400、价税 90,400）。
--   本表不加 tax_rate/tax_amount：PoOrder.vue 行项目列只有 物料编码/物料名称/数量/单位/单价/金额，
--   加没人填的列 = 死列。税额规则在 BCP 同步侧按配置税率推导（bcp sys_config po.sync.tax-rate）。
-- =====================================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- §0 前置校验（只读，执行 §1 前必须先看结果）
-- ----------------------------------------------------------------------------

-- 0.1 确认行表确实不存在（预期：Empty set）
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'po_order_line';

-- 0.2 历史订单规模（决定 §2 是否需要补录、以及补录工作量）
SELECT status, COUNT(*) AS cnt FROM po_order WHERE del_flag = 0 GROUP BY status;

-- 0.3 已审批通过、后续要走协同的订单（本演练至少要能同步出行的那些）
SELECT id, po_no, supplier_name, amount, received_amount, status
  FROM po_order WHERE del_flag = 0 AND status IN ('APPROVED','INVOICED','OPEN')
 ORDER BY id;

-- ----------------------------------------------------------------------------
-- §1 建表（去重：CREATE TABLE IF NOT EXISTS）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS po_order_line (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT        NOT NULL COMMENT '租户ID【多租户隔离字段】',
  po_id         BIGINT        NOT NULL COMMENT '采购订单主表ID（po_order.id）',
  line_no       INT           NOT NULL DEFAULT 1 COMMENT '行号（前端表格序号，从 1 起）',
  material_code VARCHAR(64)   NULL COMMENT '物料编码（md_item.item_code，可手录）',
  material_name VARCHAR(200)  NULL COMMENT '物料/服务名称（冗余快照）',
  quantity      DECIMAL(18,4) NULL COMMENT '数量',
  unit          VARCHAR(16)   NULL COMMENT '计量单位',
  unit_price    DECIMAL(18,6) NULL COMMENT '单价（与表头口径一致：不含税）',
  amount        DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '行金额（不含税，= 数量×单价）',
  remark        VARCHAR(500)  NULL COMMENT '行备注（分批交付/账期等人工说明）',
  create_by     BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT NULL,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT NOT NULL DEFAULT 0,
  -- ★ 刻意不建 uk(po_id,line_no)：编辑订单走行"逻辑删除 + 重插"记录（对齐 FSSC 全程禁物理删的规则），
  --   同 po_id 同 line_no 会出现多行 del_flag=1 的历史版本，唯一键会当场把第二次编辑顶死。
  KEY idx_po (po_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单行项目（po_order 的行腿，金额不含税）';

-- ----------------------------------------------------------------------------
-- §2 历史订单行补录 —— 【本文件不自动补录，理由如下】
--
--   F-2 是"提交即丢弃"，不是"存了没搬"：操作员当初在界面上敲的数量/单价**没有任何地方留存**，
--   只有 remark 里的金额文字活了下来。据 remark 反推数量=编数据，违反"数据必须勾稽"。
--
--   正规出口（窗口内按此做，不跑 SQL）：
--     ① 升级后的 FSSC 会落行项目 ⇒ 由原建单岗（采购员）在【采购订单】里对该单
--        用【编辑】重新录入行项目（改版后 updateOrder 会落行）；
--     ② 若该单已进入审批后状态而业务要求"改单必须重走审批"，则走【作废】+【重新下单】，
--        不得靠 UPDATE 造行。
--
--   仅当业务书面确认"remark 里的金额即行金额、数量按合同单价可倒推"时，才使用下面这条
--   注释态模板（把值替换为业务确认后的数值，逐单填）：
-- ----------------------------------------------------------------------------
-- INSERT INTO po_order_line (tenant_id, po_id, line_no, material_name, quantity, unit, unit_price, amount, remark, create_by)
-- SELECT o.tenant_id, o.id, x.line_no, x.material_name, x.quantity, x.unit, x.unit_price, x.amount,
--        'V1.4.47 按业务书面确认补录（源值取自 po_order.remark）', o.create_by
--   FROM po_order o
--   JOIN ( SELECT 'PO1790434820845' AS po_no, 1 AS line_no, '精密结构件' AS material_name,
--                 NULL AS quantity, NULL AS unit, NULL AS unit_price, 50000.00 AS amount
--          UNION ALL SELECT 'PO1790434820845', 2, '精密结构件', NULL, NULL, NULL, 30000.00 ) x
--     ON x.po_no = o.po_no
--  WHERE o.del_flag = 0
--    AND NOT EXISTS (SELECT 1 FROM po_order_line l WHERE l.po_id = o.id AND l.line_no = x.line_no AND l.del_flag = 0);

-- ----------------------------------------------------------------------------
-- §3 校验（只读，执行 §1 后）
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS table_created FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'po_order_line';

SELECT l.po_id, o.po_no, COUNT(l.id) AS active_lines, SUM(l.amount) AS line_total, o.amount AS header_amount
  FROM po_order_line l JOIN po_order o ON o.id = l.po_id
 WHERE l.del_flag = 0 GROUP BY l.po_id, o.po_no, o.amount;

-- 回滚见 V1.4.47_po_order_line_rollback.sql（DROP TABLE；行表由本版本新建，回滚即整表撤销）
