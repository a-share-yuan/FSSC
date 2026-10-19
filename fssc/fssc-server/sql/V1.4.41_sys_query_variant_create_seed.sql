-- =============================================================
-- V1.4.41  查询方案（sys_query_variant）建表
-- 批次：表单必填项治理 挂账 C（用户裁决＝补 DDL 并接回界面）
--
-- 背景：后端 4 个文件（QueryVariantController/QueryVariantService/
--       SysQueryVariant/SysQueryVariantMapper）与前端 queryVariantApi、
--       useQueryVariant.js 一直在，但表从未建过 ⇒ 6 个端点必然 500。
--       列定义严格对齐实体 + BaseEntity 约定（tenant_id/create_by/
--       create_time/update_by/update_time/del_flag 由 MetaObjectHandler 填充）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行无副作用。
-- 回滚：V1.4.41_sys_query_variant_create_rollback.sql（DROP TABLE）
-- =============================================================

CREATE TABLE IF NOT EXISTS `sys_query_variant` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `tenant_id`    bigint       NOT NULL COMMENT '租户ID（MetaObjectHandler 插入时填充）',
  `user_id`      bigint       NOT NULL COMMENT '归属用户（方案是个人级，不是共享级）',
  `variant_name` varchar(100) NOT NULL COMMENT '方案名称（同一 user_id+page_key 下唯一，服务端查重）',
  `page_key`     varchar(100) NOT NULL COMMENT '页面标识，如 master_config:Supplier',
  `filters_json` text         DEFAULT NULL COMMENT '筛选条件 JSON（searchForm 序列化）',
  `sort_by`      varchar(50)  DEFAULT NULL COMMENT '排序列',
  `sort_order`   varchar(10)  DEFAULT NULL COMMENT '排序方向 ASC/DESC',
  `is_default`   int          NOT NULL DEFAULT 0 COMMENT '1=该页默认方案（同一 user_id+page_key 仅一个）',
  `create_by`    varchar(50)  DEFAULT NULL,
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`    varchar(50)  DEFAULT NULL,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`     int          NOT NULL DEFAULT 0 COMMENT '0未删/1已删（@TableLogic）',
  PRIMARY KEY (`id`),
  KEY `idx_user_page` (`tenant_id`, `user_id`, `page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='查询方案（个人常用筛选组合）';
