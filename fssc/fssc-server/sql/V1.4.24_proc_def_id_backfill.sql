-- ============================================================================
-- V1.4.24 批次 2 前置 · wf_model.proc_def_id / deploy_id 回写引擎真值（任务 #24）
-- 症状（2026-09-22 只读实测）：83 个有 BPMN 的模型里 **77 个指针失真**——
--   · CLAIM 存 CLAIM:4:9d006ac6…，引擎真实 CLAIM:4:95f23a53…（UUID 段是部署时生成的，
--     每次重启都重新部署一遍，所以库里的 UUID 必然过期）；LOAN 存 LOAN:4 而引擎已到 228 版；
--   · 40 余个模型 proc_def_id 直接为 NULL（从未走过带部署的发布口，如 SETTLE/MEETING）。
-- 后果：`designer/in-flight`（G3-3 在途影响面）拿这个悬空 ID 去比对 ACT_RU_EXECUTION，
--   staleCount 恒等于全部在途 → 发布前"有没有在跑的老单子"这个判断一直不可用。
-- 规则：只回写 proc_def_id / deploy_id 两列，取 ACT_RE_PROCDEF 中 KEY_=model_key 的最大 VERSION_ 行；
--   `wf_model.version` 是业务发布计数（批次 2 的 diff 台账靠它），**不**与 Flowable VERSION_（228+ 重启噪声）对齐，保持不动。
-- 铁律：只改列值不删行；写前建备份表，回滚按 id 逐值还原。
-- 回滚：V1.4.24_proc_def_id_backfill_rollback.sql
-- ============================================================================

SET SESSION lock_wait_timeout = 10;
SET SESSION innodb_lock_wait_timeout = 10;
SELECT COUNT(*) AS open_trx_should_be_0 FROM information_schema.INNODB_TRX;

-- ---------- 0. 写前备份 ----------
CREATE TABLE IF NOT EXISTS wf_model_bak_v1424_20260922 AS
  SELECT id, model_key, version, proc_def_id, deploy_id, status
    FROM wf_model WHERE bpmn_xml IS NOT NULL AND del_flag = 0;

-- ---------- 1. 体检：写前三态计数（悬空 / NULL / 已正确） ----------
SELECT SUM(m.proc_def_id IS NULL)                                   AS null_ptr,
       SUM(m.proc_def_id IS NOT NULL AND m.proc_def_id <> t.ID_)    AS dangling,
       SUM(m.proc_def_id = t.ID_)                                   AS already_ok,
       COUNT(*)                                                     AS total
  FROM wf_model m
  JOIN (SELECT p.KEY_ k, p.ID_ ID_, p.DEPLOYMENT_ID_ D_
          FROM ACT_RE_PROCDEF p
          JOIN (SELECT KEY_ k2, MAX(VERSION_) v2 FROM ACT_RE_PROCDEF GROUP BY KEY_) x
            ON x.k2 = p.KEY_ AND x.v2 = p.VERSION_) t ON t.k = m.model_key
 WHERE m.bpmn_xml IS NOT NULL AND m.del_flag = 0;

-- ---------- 2. 回写引擎真值（每个 model_key 取最新部署版本） ----------
UPDATE wf_model m
  JOIN (SELECT p.KEY_ k, p.ID_ ID_, p.DEPLOYMENT_ID_ D_
          FROM ACT_RE_PROCDEF p
          JOIN (SELECT KEY_ k2, MAX(VERSION_) v2 FROM ACT_RE_PROCDEF GROUP BY KEY_) x
            ON x.k2 = p.KEY_ AND x.v2 = p.VERSION_) t ON t.k = m.model_key
   SET m.proc_def_id = t.ID_,
       m.deploy_id   = t.D_
 WHERE m.bpmn_xml IS NOT NULL AND m.del_flag = 0
   AND (m.proc_def_id IS NULL OR m.proc_def_id <> t.ID_ OR m.deploy_id IS NULL OR m.deploy_id <> t.D_);

-- ---------- 3. 写后复核：应只剩 already_ok ----------
SELECT SUM(m.proc_def_id IS NULL)                                   AS null_ptr_after,
       SUM(m.proc_def_id IS NOT NULL AND m.proc_def_id <> t.ID_)    AS dangling_after,
       SUM(m.proc_def_id = t.ID_)                                   AS ok_after,
       COUNT(*)                                                     AS total
  FROM wf_model m
  JOIN (SELECT p.KEY_ k, p.ID_ ID_
          FROM ACT_RE_PROCDEF p
          JOIN (SELECT KEY_ k2, MAX(VERSION_) v2 FROM ACT_RE_PROCDEF GROUP BY KEY_) x
            ON x.k2 = p.KEY_ AND x.v2 = p.VERSION_) t ON t.k = m.model_key
 WHERE m.bpmn_xml IS NOT NULL AND m.del_flag = 0;

-- ---------- 4. 回写后即可用：仍在"旧定义"上跑着的在途实例（= 发布前必须看一眼的 staleCount 规则） ----------
SELECT m.model_key, m.proc_def_id AS latest_def,
       (SELECT COUNT(*) FROM ACT_RU_EXECUTION e
         WHERE e.PROC_DEF_ID_ IS NOT NULL AND e.PARENT_ID_ IS NULL
           AND e.PROC_DEF_ID_ IN (SELECT p.ID_ FROM ACT_RE_PROCDEF p WHERE p.KEY_ = m.model_key)
           AND e.PROC_DEF_ID_ <> m.proc_def_id)                      AS stale_running
  FROM wf_model m
 WHERE m.del_flag = 0 AND m.bpmn_xml IS NOT NULL AND m.proc_def_id IS NOT NULL
 HAVING stale_running > 0
 ORDER BY stale_running DESC;
