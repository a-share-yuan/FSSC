-- =============================================================================
-- V1.0.10  fa_cer_request 资本性支出申请表（FINAL-03 / DF2-04 CER 预算集成）
-- FA_CER 原先 bizId=null 无业务单据表：审批事件收不到回写(DA-01)、预算无法占用核销。
-- 现引入简化申请记录，发起时以 bizId=fa_cer_request.id 启动 FA_CER 审批流。
-- =============================================================================

CREATE TABLE IF NOT EXISTS fa_cer_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL COMMENT '租户',
  request_no VARCHAR(64) NOT NULL COMMENT '申请编号',
  asset_name VARCHAR(300) NULL COMMENT '资产/项目名称',
  amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '申请金额',
  budget_item_id BIGINT NULL COMMENT '预算科目 bg_item.id',
  dept_id BIGINT NULL COMMENT '申请部门',
  company_id BIGINT NULL COMMENT '申请公司',
  remark VARCHAR(500) NULL COMMENT '备注',
  status VARCHAR(20) NOT NULL DEFAULT 'APPLYING' COMMENT 'APPLYING/APPROVED/REJECTED',
  proc_inst_id VARCHAR(64) NULL COMMENT '流程实例ID',
  apply_user_id BIGINT NULL COMMENT '申请人ID',
  apply_user_name VARCHAR(50) NULL COMMENT '申请人姓名',
  create_by BIGINT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag TINYINT NOT NULL DEFAULT 0,
  KEY idx_tenant (tenant_id),
  KEY idx_proc (proc_inst_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资本性支出申请(FA_CER)简化记录';
