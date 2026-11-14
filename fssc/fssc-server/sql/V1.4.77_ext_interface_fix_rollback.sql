-- =============================================================================
-- V1.4.77 回滚  八类差距收尾⑧：外部接口通道配置纠错 + 待接入原因标注
-- 日期：2026-10-17
-- 只回滚 id=2 的实质改动（api_url 与原 remark）；其余仅为 remark 文字标注，
-- 回滚为便于复现「原始脏数据」时可按下方原值还原。
-- =============================================================================

-- id=2：恢复原自指地址与原 remark（仅在确需复现原问题时才执行）
UPDATE ext_interface_config
SET api_url = 'http://localhost:8089/api/tenant/system/ext-interface/2',
    remark = '发票验真接口，启用后定时验真任务将调用此接口'
WHERE id = 2;

-- 其余仅恢复 remark 文字，status 全程未被本脚本改动
UPDATE ext_interface_config SET remark = NULL WHERE id IN (10, 11, 17);
UPDATE ext_interface_config SET remark = NULL WHERE id IN (12, 13, 14, 15, 16);
UPDATE ext_interface_config SET remark = NULL WHERE id = 1;
