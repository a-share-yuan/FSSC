# db/ — 数据库脚本

## 结构基线

`schema-baseline.sql`：323 张业务/平台表的建表语句（MySQL 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`），
**只含结构、不含任何数据行**——账号、客户、供应商、附件字节一概不在文件里。
每个原表文件自带的 `CREATE DATABASE IF NOT EXISTS` + `USE` 都保留了，所以主库 `fssc` 与影像库 `fssc_image`
一条命令即可一起建好，无需预先手工建库：

```bash
mysql -uroot -p < db/schema-baseline.sql
```

Flowable 的 `ACT_*` / `FLW_*` 表不用手工建：`flowable.database-schema-update=true` 会在首次启动时自建。

## 增量迁移

`../fssc/fssc-server/sql/V*.sql` 按版本号升序执行（`*_rollback.sql` 只在回退时用）。
迁移脚本里带了菜单与角色配置（`sys_menu`／`sys_role`），但不带演示账号、系统参数、发号规则与已部署流程定义。

## 演示数据不在发布面

本目录**不发布任何数据转储**：`db/Dump*/` 与 `db/backup/` 已在 `.gitignore` 里排除，
仓库里也没有 `seed-demo.sql` 这类文件。要演示数据请在本地库里造，用
`mysqldump --no-create-info` 单独导出到自己机器上，别提交进仓。

口令一律走 `.env` / 环境变量（见根目录 `.env.example`），不写进本目录任何文件。
