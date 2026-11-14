-- =====================================================================
-- V1.5.08 回滚：删除性能基线索引 28 枚
-- 生成日期：2026-10-30    适用库：fssc
-- 注意：回滚后列表/待办/追溯页会回到慢查询（实测差见方案册 §17）
-- =====================================================================
ALTER TABLE wf_task         DROP INDEX idx_tenant_done_agg;
ALTER TABLE wf_task         DROP INDEX idx_tenant_status_node;
ALTER TABLE wf_task         DROP INDEX idx_tenant_status_ct;
ALTER TABLE wf_task         DROP INDEX idx_tenant_status_due;
ALTER TABLE wf_task_history DROP INDEX idx_tenant_inst_oper;
ALTER TABLE wf_task_history DROP INDEX idx_tenant_oper;
ALTER TABLE wf_instance     DROP INDEX idx_proc_inst_model;
ALTER TABLE wf_instance     DROP INDEX idx_tenant_status;
ALTER TABLE wf_instance     DROP INDEX idx_tenant_end;
ALTER TABLE exp_bill        DROP INDEX idx_tenant_agentuser;
ALTER TABLE exp_bill        DROP INDEX idx_tenant_owner_pair;
ALTER TABLE exp_bill        DROP INDEX idx_tenant_company_period;
ALTER TABLE exp_bill        DROP INDEX idx_tenant_company_status;
ALTER TABLE exp_bill_line   DROP INDEX idx_report_cover;
ALTER TABLE op_archive      DROP INDEX idx_tenant_owner;
ALTER TABLE op_voucher      DROP INDEX idx_tenant_del;
ALTER TABLE op_voucher      DROP INDEX idx_tenant_company_del_id;
ALTER TABLE op_voucher      DROP INDEX idx_tenant_srcdoc;
ALTER TABLE op_audit_task   DROP INDEX idx_tenant_bill_type;
ALTER TABLE op_audit_task   DROP INDEX idx_tenant_pool_del;
ALTER TABLE op_pay_batch    DROP INDEX idx_tenant_owner;
ALTER TABLE pay_plan        DROP INDEX idx_tenant_owner;
ALTER TABLE po_order        DROP INDEX idx_tenant_owner;
ALTER TABLE po_receipt      DROP INDEX idx_tenant_owner;
ALTER TABLE rc_receivable   DROP INDEX idx_tenant_owner;
ALTER TABLE inv_invoice     DROP INDEX idx_tenant_owner;
ALTER TABLE cont_contract   DROP INDEX idx_tenant_owner;
ALTER TABLE ar_recon_record DROP INDEX idx_tenant_owner;
