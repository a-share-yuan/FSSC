-- ============================================================================
-- V1.6.105 驳回通知模板化
-- 背景：驳回/撤回是唯一「单据死了但没人知道」的终态——档案到期、申报到期、发票异常、
--       应收逾期都走 NotifyService 通知，唯独「你的报销被退了」不通知。此处补通知，
--       并且不把文案写死在 Java 里（消息模板 + 消息配置），
--       改为「触发点 → 模板行」，业务可自行改文案/开关。
--
-- 设计要点：
--   1. 一条触发点一行（uk_tenant_trigger 唯一），查不到即「不通知」，fail-closed 不报错；
--   2. 模板只做 ${var} 字符串替换，不引入表达式引擎（避免模板注入与调试黑洞）；
--   3. link_tpl 生成站内信深链，点消息直达单据列表并按单号过滤（前端 ExpenseBillListTab
--      已支持 billNo query 自动定位）；
--   4. 本文件幂等：重复执行不新增行、不覆盖业务已改过的文案（仅 INSERT IGNORE）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_notify_template` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`    BIGINT       NOT NULL DEFAULT 1 COMMENT '租户',
  `trigger_code` VARCHAR(64)  NOT NULL COMMENT '触发点编码，如 EXP_BILL_REJECTED',
  `trigger_name` VARCHAR(128) NOT NULL COMMENT '触发点名称（人读）',
  `msg_type`     VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT '消息类型：NOTICE/APPROVAL/ALERT',
  `title_tpl`    VARCHAR(200) NOT NULL COMMENT '标题模板，支持 ${billNo} 等占位符',
  `content_tpl`  VARCHAR(1000) NOT NULL COMMENT '正文模板',
  `link_tpl`     VARCHAR(255) DEFAULT NULL COMMENT '跳转链接模板，可空（空＝不可点击）',
  `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用即不通知）',
  `is_builtin`   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=内置模板（升级不覆盖，仅提示）',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注/变量说明',
  `create_by`    BIGINT       DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT NULL,
  `update_by`    BIGINT       DEFAULT NULL,
  `update_time`  DATETIME     DEFAULT NULL,
  `del_flag`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_trigger` (`tenant_id`, `trigger_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板（触发点 → 标题/正文/深链）';

-- ---------- 内置模板：报销单被驳回 ----------
-- 可用变量：${billNo} ${title} ${billType} ${rejectType} ${comment} ${applyUser}
INSERT IGNORE INTO `sys_notify_template`
  (`tenant_id`, `trigger_code`, `trigger_name`, `msg_type`, `title_tpl`, `content_tpl`,
   `link_tpl`, `enabled`, `is_builtin`, `remark`, `create_time`, `update_time`, `del_flag`)
VALUES
  (1, 'EXP_BILL_REJECTED', '报销单被驳回', 'ALERT',
   '单据 ${billNo} 被驳回，请处理后重新提交',
   '您的《${title}》（${billNo}）在审批中被驳回（${rejectType}）。驳回意见：${comment}。'
   '修改后可在「我的单据」直接编辑重提。',
   '/expense/my?billNo=${billNo}',
   1, 1,
   '变量：${billNo} 单号 / ${title} 事由 / ${billType} 单据类型 / ${rejectType} 驳回类型 / ${comment} 驳回意见 / ${applyUser} 申请人',
   NOW(), NOW(), 0);

-- ---------- 校验 ----------
-- 预期：模板 1 行、enabled=1
SELECT CONCAT('templates=', COUNT(*), '|enabled=', SUM(enabled))
  FROM `sys_notify_template` WHERE del_flag = 0 AND trigger_code = 'EXP_BILL_REJECTED';
