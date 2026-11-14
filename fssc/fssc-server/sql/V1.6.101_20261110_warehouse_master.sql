-- SPDX-License-Identifier: AGPL-3.0-or-later
-- =====================================================================
-- V1.6.101  仓库主数据（建 md_warehouse + 默认仓种子 + 存货回填）
--
-- 背景：存货领用 / 处置 / 入库此前恒报 400「请选择仓库」，原因有两层：
--     ① `md_inventory.warehouse_id` 全为 NULL；
--     ② 业务库缺少仓库主数据表，前端无处可选，后端
--        `MdInventoryService.requireManual()` 遇到 null 直接抛错。
--   受此影响，`INV_ISSUE_PROJECT` / `INV_DISPOSAL_APPLY` 两条流程一直无法发起。
--
-- 配套代码改动（同批次，非本脚本范围）：
--   `MdInventoryService.requireManual()` 增加默认仓兜底 —— 未指定仓库时回落
--   `is_default=1` 的仓，避免以后新增物料再撞同一处 400。
--
-- 本脚本按幂等写法，可对空库重放。
-- 回滚：V1.6.101_20261110_warehouse_master_rollback.sql
-- =====================================================================

-- ① 建表：仓库主数据
--    列设计对齐 `md_*` 既有主数据表（tenant_id + del_flag + create/update_time）
CREATE TABLE IF NOT EXISTS md_warehouse (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL DEFAULT 1,
  company_id      BIGINT       DEFAULT NULL,
  warehouse_code  VARCHAR(64)  NOT NULL,
  warehouse_name  VARCHAR(100) NOT NULL,
  location        VARCHAR(200) DEFAULT NULL,
  keeper_user_id  BIGINT       DEFAULT NULL,
  is_default      TINYINT      NOT NULL DEFAULT 0,
  status          VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
  remark          VARCHAR(500) DEFAULT NULL,
  del_flag        TINYINT      NOT NULL DEFAULT 0,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_id, warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='仓库主数据';

-- ② 种子数据：每个公司一个默认仓（is_default=1）
--    公司 1 = 上海总部；公司 33 = 昆山（非集中核算主体）
INSERT INTO md_warehouse (tenant_id, company_id, warehouse_code, warehouse_name, location, is_default, status)
SELECT 1, 1, 'WH-MAIN', '主仓库', '上海总部', 1, 'NORMAL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_warehouse WHERE tenant_id = 1 AND warehouse_code = 'WH-MAIN');

INSERT INTO md_warehouse (tenant_id, company_id, warehouse_code, warehouse_name, location, is_default, status)
SELECT 1, 33, 'WH-KS', '昆山车间仓', '昆山厂区', 0, 'NORMAL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_warehouse WHERE tenant_id = 1 AND warehouse_code = 'WH-KS');

-- ③ 回填：存量存货挂到本公司默认仓，没有默认仓的挂 WH-MAIN
--    只在 warehouse_id IS NULL 时写，绝不覆盖人工已指定的仓
UPDATE md_inventory i
JOIN md_warehouse w
  ON w.tenant_id = i.tenant_id
 AND w.del_flag = 0
 AND w.status = 'NORMAL'
 AND w.is_default = 1
 AND (w.company_id = i.company_id OR w.company_id IS NULL)
SET i.warehouse_id = w.id
WHERE i.warehouse_id IS NULL;

-- 兜底：仍未挂上的（公司无默认仓），统一挂 WH-MAIN
UPDATE md_inventory i
JOIN md_warehouse w ON w.tenant_id = i.tenant_id AND w.warehouse_code = 'WH-MAIN'
SET i.warehouse_id = w.id
WHERE i.warehouse_id IS NULL;

-- =====================================================================
-- 执行后自检（应为 0 行）
--   SELECT COUNT(*) FROM md_inventory WHERE warehouse_id IS NULL;
-- 仓库清单
--   SELECT id, warehouse_code, warehouse_name, is_default FROM md_warehouse;
-- =====================================================================
