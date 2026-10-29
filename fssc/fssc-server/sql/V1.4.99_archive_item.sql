-- =============================================================================
-- V1.4.99  档案组卷改造 P0：案卷卷内件清单（一卷多件）
-- 依据：DA/T 22—2015《归档文件整理规则》以"件"为最小整理单位、案卷↔件＝1:N；
--       《会计档案管理办法》（财政部 国家档案局令第79号）第五条 收集整理保管利用鉴定销毁制度。
-- 现状：op_archive 只有 bill_id/ref_id/related_ids/file_id 单值列 ⇒ 一卷一件（见
--       fssc/docs/档案组卷改造方案_20261028.md §一 S1~S6）。
-- 原则：删 op_archive 任何既有列（历史卷与开源分支在引用）；新表为唯一真相源，旧列降级为"首件镜像"。
-- 执行：测试库直接跑；生产/交付库走人工放行窗。回滚见同名 _rollback.sql。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `op_archive_item` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `tenant_id`     bigint       NOT NULL,
  `archive_id`    bigint       NOT NULL COMMENT '所属案卷 op_archive.id',
  `seq_no`        int          NOT NULL COMMENT '卷内件号，自 1 连续（DA/T 22 件号）',
  `bill_type`     varchar(32)  DEFAULT NULL COMMENT '来源单据类型（他域按 bizType 解释）',
  `bill_id`       bigint       DEFAULT NULL COMMENT '来源单据主键；费用域＝exp_bill.id',
  `bill_no`       varchar(64)  DEFAULT NULL COMMENT '来源单据编号（冗余，供卷内目录直显）',
  `voucher_no`    varchar(64)  DEFAULT NULL COMMENT '来源凭证号（冗余）',
  `item_date`     date         DEFAULT NULL COMMENT '件日期（业务日期/凭证日期）',
  `title`         varchar(200) DEFAULT NULL COMMENT '件题名（默认取单据标题）',
  `page_from`     int          DEFAULT NULL COMMENT '起页（P1 编页；纸质卷必填）',
  `page_count`    int          DEFAULT NULL COMMENT '页数（P1；电子卷以文件数替代）',
  `file_ids`      varchar(1000) DEFAULT NULL COMMENT '该件的文件ID清单，逗号串，顺序即卷内文件顺序',
  `content_hash`  varchar(64)  DEFAULT NULL COMMENT '件级快照哈希（卷级哈希＝件清单哈希⊕各件哈希）',
  `amount`        decimal(18,2) DEFAULT NULL COMMENT '件金额（卷合计与审批核对用）',
  `company_id`    bigint       DEFAULT NULL COMMENT '件所属公司；跨公司混卷，入卷清单按本公司过滤',
  `remark`        varchar(500) DEFAULT NULL,
  `create_by`     bigint       DEFAULT NULL,
  `create_time`   datetime     DEFAULT NULL,
  `update_time`   datetime     DEFAULT NULL,
  `del_flag`      tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_arc_item_seq` (`tenant_id`, `archive_id`, `seq_no`),
  KEY `idx_arc_item_bill` (`tenant_id`, `bill_id`, `del_flag`),
  KEY `idx_arc_item_archive` (`archive_id`, `del_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='案卷卷内件清单（一卷多件）';

-- 历史回填：现有每卷按其来源单据生成 1 条件行（件号＝1），related_ids 原样带入 file_ids。
-- 幂等：NOT EXISTS 保护，重复执行不产生第二行。
INSERT INTO `op_archive_item`
  (`tenant_id`, `archive_id`, `seq_no`, `bill_type`, `bill_id`, `bill_no`, `item_date`, `title`,
   `file_ids`, `company_id`, `remark`, `create_time`, `update_time`, `del_flag`)
SELECT a.`tenant_id`, a.`id`, 1,
       CASE WHEN a.`bill_id` IS NOT NULL THEN 'EXP_BILL' ELSE NULL END,
       COALESCE(a.`bill_id`, CASE WHEN a.`archive_type` IN ('BILL','EXPENSE','PAYMENT') THEN a.`ref_id` ELSE NULL END),
       a.`ref_no`, DATE(a.`filing_time`), a.`title`, a.`related_ids`, a.`company_id`,
       'V1.4.99 历史卷回填（一卷一件）', NOW(), NOW(), 0
  FROM `op_archive` a
 WHERE a.`del_flag` = 0
   AND NOT EXISTS (SELECT 1 FROM `op_archive_item` i WHERE i.`archive_id` = a.`id`);

-- 自检（执行后手工核对，两条都应为 0 差异）：
--   SELECT COUNT(*) FROM op_archive a LEFT JOIN op_archive_item i ON i.archive_id=a.id AND i.del_flag=0
--    WHERE a.del_flag=0 AND i.id IS NULL;                                   -- 期望 0（每卷至少一件）
--   SELECT COUNT(*) FROM (SELECT bill_id FROM op_archive_item WHERE del_flag=0 AND bill_id IS NOT NULL
--    GROUP BY tenant_id, bill_id HAVING COUNT(DISTINCT archive_id) > 1) t;  -- 期望 0（一单不在两卷）
