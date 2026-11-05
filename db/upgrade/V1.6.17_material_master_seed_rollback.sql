-- 回滚 V1.6.17：删除本增量铺入的 8 条物料（按编码点删，不碰既有物料）
DELETE FROM md_inventory
WHERE tenant_id = 1 AND company_id = 1
  AND item_code IN ('MAT-LB-001','MAT-LB-002','MAT-LB-003','MAT-LB-004',
                    'MAT-LB-005','MAT-LB-006','MAT-BG-001','MAT-BG-002');
