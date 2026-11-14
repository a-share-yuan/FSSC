-- =============================================================
-- V1.4.42 回滚：删除 F2 资料齐套校验的三枚灰度键
-- 删除后 BillCompletenessGuard 读不到键 ⇒ 三调用点全部直通（等价改造前），
-- 与"总开关置 0"效果相同，区别只在于参数配置页不再露出这三项。
-- 不含 D4 账户外键硬拦的回滚——那段代码不吃开关；若需退回，回滚 jar 即可
--    （部署时另存的 fssc-server.jar.bak-<日期>-preF2）。
-- =============================================================

SET SESSION lock_wait_timeout = 10;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS sys_config_bak_v1442_rb_20260926 AS
  SELECT * FROM sys_config WHERE config_key LIKE 'bill.completeness.%';

DELETE FROM sys_config WHERE config_key LIKE 'bill.completeness.%';

COMMIT;

SELECT '剩余齐套键' chk, COUNT(*) n FROM sys_config WHERE config_key LIKE 'bill.completeness.%';
