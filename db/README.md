# db/ — 整库 dump（建库入口）

`Dump20261102/` 是本项目一次演练库的 MySQL 整库导出，**建库、起服务、演示都以它为准**。
323 个文件＝一表一文件（主库 `fssc` 317 枚，影像库 `fssc_image` 6 枚），合计约 6.8 MB。

## 导入

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
- 视图/存储过程：本 dump 里没有（导出时已剥离 `DEFINER` 子句，跨机导入不会因 definer 权限失败）。
- Flowable 的 `ACT_*` / `FLW_*` 表已在 dump 内；`flowable.database-schema-update=true` 会在首启时补齐缺项。
- 影像库表当前只有配置行、无业务行（与源库一致），上传发票后自动产生数据。
- 口令一律走 `.env` / 环境变量（见根目录 `.env.example`），本目录不含任何明文凭据文件。

## 这份 dump 里带着演练期间的业务数据

它是真实跑过一遍业务的库导出，所以**含单据、凭证、审批流水，以及 `act_ge_bytearray` 里 2.3 MB 的上传附件字节**；
`fssc_crm_customer` / `fssc_md_supplier(_contact)` / `fssc_sys_user` 等表里也带着名称、邮箱、手机号形态的字段值。
发布前请确认这些数据可以公开；要换一批干净数据，就重新导出一份同名结构的 dump 覆盖本目录并同步更新本文件的日期。

`db/Dump*/` 与 `db/backup/` 在 `.gitignore` 里，是为了防止日后随手 dump 又被 `git add -A` 带进来；
`Dump20261102/` 是**明确 `git add -f` 入库**的那一枚例外。

## 与 `fssc-server/sql/` 的关系

那批按施工顺序累积的增量迁移脚本已不在本仓库（本仓库不再提供"从空库跑迁移建库"的路径，
项目也没有引入 Flyway/Liquibase，外人无法据此定序）。源码树里仍保留它们作为施工记录，
但文档、README、CI 都不应以建库入口引用它。
