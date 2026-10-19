-- =============================================================================
-- V1.4.68 回滚：清理本件新增的 15 个字典容器与数据
--
-- 口径精确：只删本件所建的 dict_type（V1.4.67 的 8 个容器与其 24 行数据**不动**）。
-- 执行后校验：期望 8 个类型 / 24 行（回到 V1.4.67 完成态）
--   SELECT COUNT(*) FROM fssc.sys_dict_type WHERE del_flag=0;
--   SELECT COUNT(*) FROM fssc.sys_dict_data WHERE del_flag=0;
-- =============================================================================

-- 先删数据（sys_dict_data 无外键约束，顺序仅为可读性）
DELETE FROM fssc.sys_dict_data
 WHERE tenant_id = 1
   AND dict_type IN ('bill_status_main','bill_status_exception','wf_instance_status',
                     'job_status','loan_status','master_data_status','archive_status',
                     'recon_status','receipt_status','bill_domain_status','cip_status',
                     'month_close_status','model_publish_status','bill_sub_type',
                     'bill_reject_type');

-- 再删容器
DELETE FROM fssc.sys_dict_type
 WHERE tenant_id = 1
   AND dict_type IN ('bill_status_main','bill_status_exception','wf_instance_status',
                     'job_status','loan_status','master_data_status','archive_status',
                     'recon_status','receipt_status','bill_domain_status','cip_status',
                     'month_close_status','model_publish_status','bill_sub_type',
                     'bill_reject_type');