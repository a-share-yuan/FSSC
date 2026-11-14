-- V1.0.8 合规整改 DF2-03 / DF2-09 配套种子与配置
-- 1) DF2-03 资产凭证落账：补齐标准资产科目（当前库缺 1601 固定资产；1604 在建工程仅有 160400000 长码）。
--    凭证生成侧已有默认处理：科目缺失时记 WARN 跳过凭证生成，不阻断审批。
-- 2) DF2-03 FA_DISPOSE 收尾接线：资产处置流程结束 → assetBizAction 生成处置凭证（显式注册，与其他 FA_* 保持一致）。

INSERT INTO md_subject (tenant_id, company_id, subject_code, subject_name, parent_id, subject_level, subject_type, direction, is_leaf, status, remark)
SELECT 1, NULL, '1601', '固定资产', 0, 1, 'ASSET', 'DEBIT', 1, 'NORMAL', 'DF2-03 资产新增/转固/处置凭证用标准科目（种子补齐）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM md_subject WHERE subject_code = '1601' AND del_flag = 0);

INSERT INTO md_subject (tenant_id, company_id, subject_code, subject_name, parent_id, subject_level, subject_type, direction, is_leaf, status, remark)
SELECT 1, NULL, '1604', '在建工程', 0, 1, 'ASSET', 'DEBIT', 1, 'NORMAL', 'DF2-03 在建工程转固凭证用标准科目（种子补齐，长码 160400000 兼容保留）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM md_subject WHERE subject_code = '1604' AND del_flag = 0);

INSERT INTO wf_event_action_config (tenant_id, biz_type, action_bean, trigger_when, sort_order, enabled, description)
SELECT 0, 'FA_DISPOSE', 'assetBizAction', 'BOTH', 10, 1, '资产处置审批收尾：通过生成处置凭证（借累计折旧+处置损益/贷固定资产）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_event_action_config WHERE biz_type = 'FA_DISPOSE' AND action_bean = 'assetBizAction' AND del_flag = 0);
