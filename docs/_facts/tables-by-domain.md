# 表清单（按业务域分组）

> 由 `_tools/inventory.mjs` 同源生成。数据源 = 源码 `@TableName` 注解，共 259 张。
> 未映射到实体的表（视图/临时表/纯 SQL 表）不在此列，真实 schema 以 `mysqldump --no-data` 为准。

## `masterdata` — 51 张

`gl_accounting_book` · `gl_book_subject_map` · `gl_currency` · `gl_standard`
`md_bank` · `md_bank_account` · `md_budget_category` · `md_budget_org`
`md_budget_period` · `md_budget_type` · `md_cash_flow` · `md_cip`
`md_cip_cost` · `md_city` · `md_contract_type` · `md_cost_center`
`md_cost_center_map` · `md_currency` · `md_employee_level` · `md_exchange_rate`
`md_expense_item` · `md_expense_standard` · `md_form_config` · `md_form_field`
`md_form_validation` · `md_gl_config` · `md_income_expense` · `md_invoice_rule`
`md_io_template` · `md_io_template_field` · `md_item_budget_map` · `md_item_cash_map`
`md_loan_initial` · `md_loan_limit` · `md_loan_pay_type` · `md_match_tolerance`
`md_org_type` · `md_payee` · `md_period` · `md_print_template`
`md_print_template_item` · `md_print_template_set` · `md_print_template_set_item` · `md_project`
`md_subject` · `md_supplier` · `md_supplier_bank` · `md_supplier_contact`
`md_supplier_qualification` · `md_tax_rate` · `md_vehicle`

## `system` — 27 张

`ext_interface_config` · `integration_call_log` · `sod_perm_conflict` · `sod_role_conflict`
`sys_company` · `sys_config` · `sys_dept` · `sys_dict_data`
`sys_dict_type` · `sys_file` · `sys_file_token` · `sys_group_role`
`sys_login_log` · `sys_menu` · `sys_message` · `sys_notice`
`sys_number_rule` · `sys_number_seq` · `sys_oper_log` · `sys_post`
`sys_query_variant` · `sys_role` · `sys_role_menu` · `sys_user`
`sys_user_group` · `sys_user_group_rel` · `sys_user_role`

## `workflow` — 21 张

`op_dispatch_log` · `op_dispatch_policy` · `op_task_time_limit` · `op_transfer_log`
`wf_add_sign` · `wf_approval_item` · `wf_assignee_dim` · `wf_assignee_scheme`
`wf_business_flow` · `wf_cc` · `wf_delegate` · `wf_event_action_config`
`wf_flow_config` · `wf_instance` · `wf_model` · `wf_node_config`
`wf_process_config` · `wf_process_log` · `wf_process_outbox` · `wf_task`
`wf_task_history`

## `sharing` — 19 张

`linku_sync_log` · `op_audit_task` · `op_bank_statement` · `op_barcode`
`op_calendar` · `op_dispatch_rule` · `op_group` · `op_job`
`op_pay_batch` · `op_pay_batch_detail` · `op_physical_delivery` · `op_quality`
`op_voucher` · `op_voucher_line` · `op_voucher_push_log` · `op_voucher_template`
`supplier_recon` · `supplier_recon_detail` · `supplier_recon_diff`

## `compliance` — 15 张

`dispatch_task` · `employee_credit` · `expense_policy` · `expense_policy_standard`
`expense_policy_version` · `fraud_detection_log` · `md_compliance_rule` · `op_review_log`
`op_review_rule` · `op_sla_breach` · `op_sla_config` · `precheck_result`
`reviewer` · `satisfaction_survey` · `service_catalog`

## `glex` — 14 张

`gl_close_check_item` · `gl_consolidate` · `gl_fx_revaluation` · `gl_inter_offset`
`gl_journal_entry` · `gl_month_close` · `gl_salary_run` · `gl_salary_run_detail`
`gl_shared_master` · `gl_tax_provision` · `tax_declaration` · `tax_input_certify`
`tax_refund` · `tax_risk_indicator`

## `arflow` — 13 张

`ar_claim` · `ar_dispatch_rule` · `ar_ebs_adjust` · `ar_invoice_rule`
`ar_mail_notify_log` · `ar_mail_package` · `ar_mail_recipient` · `ar_month_close`
`ar_note` · `ar_pre_invoice` · `ar_statement` · `ar_writeoff_fund`
`ar_writeoff_record`

## `platform` — 13 张

`pf_bill` · `pf_config` · `pf_feature` · `pf_login_log`
`pf_notice_template` · `pf_oper_log` · `pf_order` · `pf_package`
`pf_package_feature` · `pf_tenant` · `pf_tenant_package` · `pf_tenant_stat`
`pf_user`

## `expense` — 11 张

`exp_bill` · `exp_bill_attach` · `exp_bill_line` · `exp_bill_payment`
`exp_bill_receipt` · `exp_bill_relation` · `exp_bill_share` · `exp_bill_trace`
`exp_loan_offset` · `exp_mobile_template` · `exp_travel`

## `asset` — 9 张

`asset_depr_batch` · `fa_cer_request` · `md_asset` · `md_asset_category`
`md_asset_change_log` · `md_asset_depr_detail` · `md_inventory` · `stock_cost_settle`
`stock_move`

## `image` — 6 张

`image_exception` · `image_review` · `img_batch` · `img_file`
`img_setting` · `ocr_record`

## `receivable` — 6 张

`ar_recon_record` · `rc_bad_debt` · `rc_collect_todo` · `rc_receipt`
`rc_receipt_line` · `rc_receivable`

## `budget` — 5 张

`bg_adjust` · `bg_adjust_line` · `bg_item` · `bg_occupy`
`bg_scheme`

## `document` — 5 张

`archive_access_log` · `archive_attachment` · `archive_destroy_approval` · `op_archive`
`op_archive_item`

## `invoice` — 5 张

`inv_bill_invoice` · `inv_invoice` · `inv_invoice_global` · `inv_invoice_line`
`inv_verify_log`

## `ai` — 4 张

`ai_action_confirm` · `ai_feedback` · `ai_inspect_rule` · `ai_tool_call_log`

## `collab` — 4 张

`collab_ccp_shipping` · `collab_scp_bill` · `collab_scp_recon` · `collab_sync_log`

## `returns` — 4 张

`pur_return` · `pur_return_line` · `sale_return` · `sale_return_line`

## `treasury` — 4 张

`tr_bill_settle` · `tr_credit_bill` · `tr_fund_plan` · `tr_transfer`

## `apflow` — 3 张

`ap_dispatch_rule` · `ap_month_close` · `ap_service_request`

## `bank` — 3 张

`bank_balance` · `bank_recon_item` · `bank_reconcile`

## `contract` — 3 张

`cont_contract` · `cont_pay_condition` · `cont_pay_record`

## `purchase` — 3 张

`po_order` · `po_order_line` · `po_receipt`

## `report` — 3 张

`rpt_report_def` · `rpt_summary` · `rpt_user_fav`

## `audit` — 2 张

`audit_reconcile` · `audit_trail`

## `payplan` — 2 张

`op_plan_paid_log` · `pay_plan`

## `customer` — 1 张

`crm_customer`

## `framework` — 1 张

`op_job_heartbeat`

## `gl` — 1 张

`op_gl_entry`

## `match` — 1 张

`match_tolerance`

## 未在域目录下命中 — 0 张



## 交叉归属（同一张表被多个域映射）

| 表 | 被这些域映射 |
|---|---|

共 0 张表存在跨域映射（占 259 张的 0%）。
