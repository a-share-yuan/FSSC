-- V1.4.40 回滚：只删 V1.4.40 插入的流程绑定镜像行（remark 精确匹配），其余数据一律不动
-- 前置确认：先跑本文件第 0 段看清楚要删哪些行；删除范围＝remark='V1.4.40批次3路由合一镜像seed别名' 的行，改前的 75 行不受影响。
-- 与 seed 一样：零业务表写入、零引擎表写入、零 wf_model/wf_node_config 改动。

-- 0) 将要删除的行（人工核对：条数应等于 seed 的 ②复核A.actual）
SELECT id, tenant_id, biz_type, IFNULL(sub_type,'*') sub_type, config_name, model_key, priority, status, remark
  FROM wf_process_config WHERE remark='V1.4.40批次3路由合一镜像seed别名' ORDER BY biz_type, id;

-- 1) 逻辑删除（推荐：与 @TableLogic 语义一致，行仍在库、可再审计）
UPDATE wf_process_config SET del_flag=1, update_time=NOW() WHERE remark='V1.4.40批次3路由合一镜像seed别名' AND del_flag=0;

-- 2) 复核：回到改前基线（期望 active_rows=75，本 seed 剩行=0）
SELECT (SELECT COUNT(*) FROM wf_process_config WHERE del_flag=0 AND status='NORMAL') AS active_rows,
       (SELECT COUNT(*) FROM wf_process_config WHERE remark='V1.4.40批次3路由合一镜像seed别名' AND del_flag=0) AS seed_rows_left;

-- 3) 物理删除（仅在确认永不再复用本 seed 时手工放开）
-- DELETE FROM wf_process_config WHERE remark='V1.4.40批次3路由合一镜像seed别名';
