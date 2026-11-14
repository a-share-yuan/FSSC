-- V1.4.15 op_voucher 同源生成唯一键（审计 C5/D2 的库级防线）
-- 背景：FSSC财务业务逻辑同类缺陷审计_20260921 第 C5/D2 条——VoucherService.generate(ExpBill) 是全部
--       generate* 入口中唯一无查重的一条，审批回调重放即产生双凭证；实测 13 张单据挂着
--       op_voucher 中不存在的 voucher_no（先回写后写入数据库 + REQUIRES_NEW 回滚 + catch-log 吞异常）。
-- 应用层防线（先行、无需 DDL、已随代码上线）：generate() 入口 existingVoucherId("EXPENSE", billId) 去重返回。
-- 本迁移补库级防线：同一 (租户, 源单据类型, 源单据ID) 只允许一张「有效」凭证。
--       VOID 凭证必须可共存（作废后允许重制），MySQL 无部分索引，故用生成列 + NULL 不冲突实现：
--       active_source_key 在 status='VOID' 时为 NULL，唯一索引对 NULL 不去重。
-- 执行前提（须由 DBA/负责人在变更窗口执行，本轮整改只交付脚本、未执行）：
--   1) 先跑「步骤 0 重复检测」，任一组 cnt>1 必须先人工处置（保留一张、其余作废为 VOID），
--      否则 ALTER 会因唯一键冲突失败；
--   2) op_voucher 为凭证主表，加列 + 加索引期间锁表，演示环境数据量小（实测 1 行）但仍需停写入；
--   3) 与并发会话共用同一库时，先与对方确认无进行中的制证操作。

-- ---------- 步骤 0：重复检测（只读，必须先执行且结果为空集） ----------
SELECT tenant_id, source_type, source_doc_id, COUNT(*) AS cnt, GROUP_CONCAT(id) AS voucher_ids
FROM op_voucher
WHERE status <> 'VOID' AND source_type IS NOT NULL AND source_doc_id IS NOT NULL
GROUP BY tenant_id, source_type, source_doc_id
HAVING COUNT(*) > 1;

-- ---------- 步骤 1：生成列 + 唯一索引 ----------
ALTER TABLE op_voucher
    ADD COLUMN active_source_key VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'VOID' THEN NULL
                 ELSE CONCAT(IFNULL(source_type, ''), ':', IFNULL(source_doc_id, '')) END
        ) VIRTUAL COMMENT '同源凭证幂等键（VOID=NULL，不参与唯一约束）';

ALTER TABLE op_voucher
    ADD UNIQUE KEY uk_op_voucher_active_source (tenant_id, active_source_key);

-- ---------- 步骤 2：复核（只读） ----------
SELECT COUNT(*) AS cols_added
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'op_voucher' AND column_name = 'active_source_key';

SELECT COUNT(*) AS idx_added
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'op_voucher' AND index_name = 'uk_op_voucher_active_source';

-- ---------- 回滚参考 ----------
-- ALTER TABLE op_voucher DROP KEY uk_op_voucher_active_source;
-- ALTER TABLE op_voucher DROP COLUMN active_source_key;
