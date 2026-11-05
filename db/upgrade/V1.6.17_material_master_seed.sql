-- =====================================================================
-- V1.6.17  物料主数据铺底（D-V4R-1103-04）
-- 背景：门户「物料编码／单位」两列恒空，根因不是同步丢字段，而是**源头没有物料可填**：
--       fssc 库没有 md_material，物料台账 md_inventory 为 0 行，采购订单行的「物料编码」
--       下拉接的其实是费用项目（md_expense_item）⇒ 填进去的是费用项目编号。
-- 本增量：为演示租户（tenant 1 / 公司 1）铺 8 条劳保与办公物料，供采购订单行选择，
--       选中后由前端回填「物料名称」与「单位」。
-- 幂等：按 (tenant_id, company_id, item_code) 判在册，重复执行不产生重复行。
-- 回滚：V1.6.17_material_master_seed_rollback.sql
-- =====================================================================

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-001', '安全帽', 'ABS 加厚 白色', '顶', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-001');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-002', '防护手套', '棉纱 12 双装', '双', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-002');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-003', '防砸安全鞋', '牛皮 钢包头', '双', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-003');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-004', '防护眼镜', 'PC 镜片 防雾', '副', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-004');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-005', '反光背心', '涤纶 荧光黄', '件', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-005');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-LB-006', '防尘口罩', 'KN95 头戴式', '只', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-LB-006');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-BG-001', '复印纸', 'A4 70g 5 包/箱', '箱', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-BG-001');

INSERT INTO md_inventory (tenant_id, item_code, item_name, spec, unit, company_id, price_method, create_time)
SELECT 1, 'MAT-BG-002', '签字笔', '0.5mm 黑色 12 支/盒', '盒', 1, 'MA', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM md_inventory WHERE tenant_id = 1 AND company_id = 1 AND item_code = 'MAT-BG-002');
