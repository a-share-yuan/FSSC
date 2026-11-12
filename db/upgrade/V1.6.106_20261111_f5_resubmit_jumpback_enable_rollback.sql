-- ============================================================================
-- V1.6.106 回滚：撤销「重提跳回被驳回节点」的 CLAIM 灰度
-- 回到 V1.6.106 执行前的状态：
--   · 总开关 wf.resubmit.jumpback.enabled = 0（代码缺省即是"全链重走"）
--   · 白名单清空
--   · 锚点 resubmit_jump_back 归 0
-- 三枚 sys_config 行**保留**（值为关）：留着才能被运维看见并再次灰度；
-- 若确要物理删除，见文件末尾的可选语句。
-- ============================================================================

-- ---------- 1) 关闭总开关并清空白名单 ----------
UPDATE `sys_config` SET `config_value` = '0', `update_time` = NOW()
 WHERE `tenant_id` = 1 AND `config_key` = 'wf.resubmit.jumpback.enabled';

UPDATE `sys_config` SET `config_value` = '', `update_time` = NOW()
 WHERE `tenant_id` = 1 AND `config_key` = 'wf.resubmit.jumpback.models';

-- ---------- 2) 锚点归零（按主键，安全模式下可执行）----------
UPDATE `wf_node_config` SET `resubmit_jump_back` = 0, `update_time` = NOW()
 WHERE `id` = 1348 AND `resubmit_jump_back` = 1
   AND `model_key` = 'CLAIM' AND `node_key` = 'Task_7';

-- ---------- 3) 校验：应回到 enabled=0 / models 空 / anchor_total=0 ----------
SELECT CONCAT(
  'enabled=', (SELECT config_value FROM `sys_config`
                WHERE tenant_id = 1 AND config_key = 'wf.resubmit.jumpback.enabled'),
  '|models=', (SELECT config_value FROM `sys_config`
                WHERE tenant_id = 1 AND config_key = 'wf.resubmit.jumpback.models'),
  '|anchor_total=', (SELECT COUNT(*) FROM `wf_node_config`
                WHERE del_flag = 0 AND resubmit_jump_back = 1)
) AS verify;

-- ---------- 4) 可选：彻底移除三枚配置行（默认不执行）----------
-- DELETE FROM `sys_config`
--  WHERE tenant_id = 1
--    AND config_key IN ('wf.resubmit.jumpback.enabled',
--                       'wf.resubmit.jumpback.models',
--                       'wf.reject.free.models');
