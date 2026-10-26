-- ================================================================
-- V1.4.96  ocr_record 增「识别结果 + 失败原因」两列（缺陷 D-H7-01）
-- ----------------------------------------------------------------
-- 现象（2026-10-24 H7 真跑实测，库 fssc_image）：img_batch=1、img_file=1（ocr_status=FAILED）、
--       ocr_record=0 ⇒ 识别失败在 OCR 记录页恒 0 行，失败无记录、无从复核，
--       只能去 img_exception 异常池或运维日志里找。
-- 根因：ocr_record 原本只承载"识别成功"的结构化票面（表无状态列），
--       OcrService 的三条失败环节（未识别出票面 / 识别抛异常 / 附件直传识别失败）都只 log + 写异常池。
-- 规则：失败也落一行 ocr_record，recognize_status='FAILED' + fail_reason 写业务话术原因；
--       成功行 recognize_status='SUCCESS'。img_file.ocr_text 仍**不写**失败 JSON——
--       影像页「识别失败」面板的判据就是无 OCR 数据（D-DEC-009/367 既有裁定，不动）。
--
--  部署顺序：本 DDL 必须**先于**含 D-H7-01 代码的 jar 上线。
--   代码侧失败记录环节是 catch-only（与既有 saveOcrRecord 同），列缺失只会退化成一条 warn，
--   不会打断识别；但那样缺陷等于没修，故按"先库后码"执行。
-- 回滚见 V1.4.96_ocr_record_status_rollback.sql。
-- ================================================================

ALTER TABLE fssc_image.ocr_record
  ADD COLUMN recognize_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS'
      COMMENT '识别结果：SUCCESS 成功 / FAILED 失败（D-H7-01，失败也留痕）' AFTER channel_name,
  ADD COLUMN fail_reason VARCHAR(500) NULL
      COMMENT '识别失败原因（业务话术；技术详情见运维日志）' AFTER recognize_status;

-- 历史行（本 DDL 之前落的都是成功行，DEFAULT 'SUCCESS' 已覆盖）复点：
-- SELECT recognize_status, COUNT(*) FROM fssc_image.ocr_record GROUP BY recognize_status;
