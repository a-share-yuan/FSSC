-- SPDX-License-Identifier: AGPL-3.0-or-later
-- 演示环境数据修复（幂等；只动平台套餐与订购，不改业务单据）
--
-- 背景：演示库预置 453 个正常账号，而演示租户订购的是 STANDARD（max_user=50）。
--       TenantQuotaService.assertUserQuota 在「现存量 >= 上限」时直接拒绝新增用户，
--       导致「系统管理员与流程配置」册 §5.3（新建受控用户）整链不可执行。
-- 处置：新增 DEMO 套餐（max_user=0 ＝ 不限，列注释口径），功能位与 STANDARD 逐条对齐，
--       把租户 1 的 ACTIVE 订购切到 DEMO。STANDARD/PRO 的商品口径不动。
-- 回滚：见同名 _rollback.sql（切回 STANDARD；DEMO 套餐保留不删，仅停用）。

-- 1) 套餐行（已存在则跳过）
INSERT INTO pf_package
  (package_code, package_name, package_type, price_month, price_year, max_user,
   max_company, max_storage_mb, max_api_daily, max_bill_month, status, sort, remark)
SELECT 'DEMO', '演示版', 'CUSTOM', 0, 0, 0, 0, 0, 0, 0, 1, 90,
       '演示环境套餐：人数不限（0=不限），功能与标准版一致'
WHERE NOT EXISTS (SELECT 1 FROM pf_package WHERE package_code = 'DEMO');

-- 2) 功能位与 STANDARD 对齐（缺哪条补哪条，不覆盖已有行）
INSERT INTO pf_package_feature (package_id, feature_code, enabled, del_flag)
SELECT (SELECT id FROM pf_package WHERE package_code = 'DEMO'), f.feature_code, f.enabled, 0
FROM pf_package_feature f
JOIN pf_package p ON p.id = f.package_id
WHERE p.package_code = 'STANDARD'
  AND f.del_flag = 0
  AND NOT EXISTS (
        SELECT 1 FROM pf_package_feature x
        WHERE x.package_id = (SELECT id FROM pf_package WHERE package_code = 'DEMO')
          AND x.feature_code = f.feature_code);

-- 3) 演示租户（ref_tenant_id=1）切到 DEMO
UPDATE pf_tenant_package
SET package_id = (SELECT id FROM pf_package WHERE package_code = 'DEMO')
WHERE ref_tenant_id = 1
  AND status = 'ACTIVE';

-- 4) 校验：应为 DEMO / max_user=0 / 8 条功能位
SELECT tp.ref_tenant_id, p.package_code, p.max_user,
       (SELECT COUNT(*) FROM pf_package_feature f WHERE f.package_id = p.id AND f.del_flag = 0) AS features
FROM pf_tenant_package tp
JOIN pf_package p ON p.id = tp.package_id
WHERE tp.ref_tenant_id = 1 AND tp.status = 'ACTIVE';
