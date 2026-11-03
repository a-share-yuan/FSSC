# db/ — 整库 dump（建库入口）

`Dump20261102/` 是本项目一次演练库的 MySQL 整库导出，**建库、起服务、演示都以它为准**。
325 个文件＝一表一文件 324 枚（主库 `fssc` 318 枚，影像库 `fssc_image` 6 枚）＋ `fssc_routines.sql`（存储过程与函数），合计约 6.9 MB。

## 导入

推荐使用 MySQL Workbench 选择 Dump20261102 目录后全部导入。

```bash
# 每个文件自带 CREATE DATABASE IF NOT EXISTS + USE，两个库一起建好，无需预先手工建库
for f in db/Dump20261102/fssc_*.sql; do
  mysql --default-character-set=utf8mb4 -uroot -p < "$f"
done
```

Windows（CMD）：

```bat
for %f in (db\Dump20261102\fssc_*.sql) do mysql --default-character-set=utf8mb4 -uroot -p < "%f"
```

- 依赖 MySQL 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`。
- 每个文件都带 `SET FOREIGN_KEY_CHECKS=0`，所以**文件名顺序不影响导入成败**；脚本按 `DROP TABLE IF EXISTS + CREATE + INSERT` 写，可重复执行。
- 视图：本 dump 里有一枚 `v_tax_input_certify`（进项发票认证视图，在 `fssc_routines.sql` 内），语句前面带着
  `/*!50013 DEFINER='root'@'localhost' SQL SECURITY DEFINER */` 这一段。目标库里没有 `root@localhost` 这个账号时，
  这一个文件会报 `ERROR 1449`；把那一段 definer 注释删掉再单独重导这一个文件即可（其余 324 个文件不受影响）。
  存储过程、函数、触发器：无。
- Flowable 的 `ACT_*` / `FLW_*` 表已在 dump 内；`flowable.database-schema-update=true` 会在首启时补齐缺项。
- 影像库表当前只有配置行、无业务行（与源库一致），上传发票后自动产生数据。
- 口令一律走 `.env` / 环境变量（见根目录 `.env.example`），本目录不含任何明文凭据文件。

## 这份 dump 里带着演练期间的业务数据

它是真实跑过一遍业务的库导出，所以**含单据、凭证、审批流水，以及 `act_ge_bytearray` 里 2.3 MB 的上传附件字节**；
`fssc_crm_customer` / `fssc_md_supplier(_contact)` / `fssc_sys_user` 等表里也带着名称、邮箱、手机号形态的字段值。
发布前请确认这些数据可以公开；要换一批干净数据，就重新导出一份同名结构的 dump 覆盖本目录并同步更新本文件的日期。

`db/Dump*/` 与 `db/backup/` 在 `.gitignore` 里，是为了防止日后随手 dump 又被 `git add -A` 带进来；
`Dump20261102/` 是**明确 `git add -f` 入库**的那一枚例外。

## 为什么没有增量迁移脚本

本仓库只给一条建库路径：导入 `db/Dump<导出日期>/`。没有"从空库按版本顺序跑迁移脚本"这条路，
项目也未引入 Flyway/Liquibase。库结构要往前走，就重新导出一份同名结构的整库 dump 覆盖
`db/Dump<日期>/`，并同步更新本文件的日期与枚数（怎么产这份 dump 见 `docs/数据模型.md` §6）。
