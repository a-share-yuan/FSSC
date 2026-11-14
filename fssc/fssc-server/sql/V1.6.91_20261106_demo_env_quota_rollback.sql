-- SPDX-License-Identifier: AGPL-3.0-or-later
-- V1.6.91 回滚：把演示租户切回 STANDARD（max_user=50）。
-- 注意：切回后 §5.3 新建用户会再次被配额拦下（现存 453 > 50），只在需要复现该缺陷时使用。

UPDATE pf_tenant_package
SET package_id = (SELECT id FROM pf_package WHERE package_code = 'STANDARD')
WHERE ref_tenant_id = 1
  AND status = 'ACTIVE';

UPDATE pf_package SET status = 0 WHERE package_code = 'DEMO';

SELECT tp.ref_tenant_id, p.package_code, p.max_user
FROM pf_tenant_package tp
JOIN pf_package p ON p.id = tp.package_id
WHERE tp.ref_tenant_id = 1 AND tp.status = 'ACTIVE';
