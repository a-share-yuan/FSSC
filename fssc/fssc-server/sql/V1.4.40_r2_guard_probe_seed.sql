-- 批次3 R2 · V1.4.38 加固点的运行期取证（临时行，用完即逻辑删除）
-- 目的：证明"绑定表命中但目标 key 未部署"这一格不再静默发起，而是 WARN + 回退内置映射。
--       加固代码：WfEngineFacade.resolveProcessKey —— processDefinitionKey(bound).count()>0 才 return。
-- 影响面：wf_process_config（配置表）INSERT 1 行，biz_type 为不可能与真实单据重名的哨兵值；
--         紧随其后的 r2-guard-test-cleanup.sql 把该行 del_flag 置 1（@TableLogic 语义），不物理删。
INSERT INTO wf_process_config (tenant_id, biz_type, sub_type, config_name, model_key, priority, status, remark, create_time, update_time, del_flag)
SELECT 1, 'Z_B3_GUARD_PROBE', NULL, 'V1.4.38 加固点取证（未部署目标）', 'ZZ_NO_SUCH_PROCESS_KEY', 10, 'NORMAL', 'V1.4.40批次3运行期取证临时行', NOW(), NOW(), 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wf_process_config x
        WHERE x.del_flag=0 AND x.biz_type='Z_B3_GUARD_PROBE' AND IFNULL(x.sub_type,'')='');
SELECT id, biz_type, model_key, status, remark FROM wf_process_config WHERE del_flag=0 AND biz_type='Z_B3_GUARD_PROBE';
SELECT COUNT(*) AS target_deployed_defs FROM ACT_RE_PROCDEF WHERE KEY_='ZZ_NO_SUCH_PROCESS_KEY';
