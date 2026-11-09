# db/ — 整库 dump（建库入口）

`Dump20261109/` 是本项目一次演练库的 MySQL 整库导出，**建库、起服务、演示都以它为准**。
327 个文件＝一表一文件 326 枚（主库 `fssc` 320 枚，影像库 `fssc_image` 6 枚）＋ `fssc_routines.sql`（视图定义），合计约 5.5 MB。

## 导入

推荐使用 MySQL Workbench 选择 Dump20261109 目录后全部导入。

```bash
# 每个文件自带 CREATE DATABASE IF NOT EXISTS + USE，两个库一起建好，无需预先手工建库
for f in db/Dump20261109/fssc_*.sql; do
  mysql --default-character-set=utf8mb4 -uroot -p < "$f"
done
```

Windows（CMD）：

```bat
for %f in (db\Dump20261109\fssc_*.sql) do mysql --default-character-set=utf8mb4 -uroot -p < "%f"
```

- 依赖 MySQL 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`。
- 每个文件都带 `SET FOREIGN_KEY_CHECKS=0`，所以**文件名顺序不影响导入成败**；脚本按 `DROP TABLE IF EXISTS + CREATE + INSERT` 写，可重复执行。
- 视图：本 dump 里有一枚 `v_tax_input_certify`（进项发票认证视图，在 `fssc_routines.sql` 内），语句前面带着
  `/*!50013 DEFINER='root'@'localhost' SQL SECURITY DEFINER */` 这一段。目标库里没有 `root@localhost` 这个账号时，
  这一个文件会报 `ERROR 1449`；把那一段 definer 注释删掉再单独重导这一个文件即可（其余 326 个文件不受影响）。
  存储过程、函数、触发器：无（`db/upgrade/V1.6.90` 里的 `fssc_add_column` / `fssc_drop_column` 由该脚本自建，不依赖 dump）。
- Flowable 的 `ACT_*` / `FLW_*` 表已在 dump 内；`flowable.database-schema-update=true` 会在首启时补齐缺项。
- 影像库表当前只有配置行、无业务行（与源库一致），上传发票后自动产生数据。
- 口令一律走 `.env` / 环境变量（见根目录 `.env.example`），本目录不含任何明文凭据文件。

## 这份 dump 里有什么

结构、主数据、菜单与权限、流程定义齐全，**业务单据为空**（`exp_bill` / `op_voucher` / `wf_instance` / `op_pay_batch` 均为 0 行）——
它是清库后重新铺底的一版，导入即得一个干净可演示的库，单据由使用者自己跑出来。

`act_ge_bytearray` 约 804 KB，是随附的 BPMN 定义与流程图资源（44 条流程定义、43 个流程模型），
**不是上传附件**。

`fssc_crm_customer` / `fssc_md_supplier(_contact)` / `fssc_sys_user` 等主数据表里带着名称、邮箱、手机号形态的字段值，
发布前请确认这些数据可以公开；要换一批干净数据，就重新导出一份同名结构的 dump 覆盖本目录并同步更新本文件的日期。

> 内容构成：`2026-11-09` 的整库导出。结构、主数据、菜单与权限、流程定义齐全，业务单据为空。
> 本期新增的租户使用方式标记、流程设置能力码、表单平台配置与菜单图标回填均已落库；
> 压测表与内部备份表未随仓发布，仓内共 326 张表（主库 320 张、影像库 6 张）。
> `md_inventory` 含 8 条物料铺底（劳保用品与办公耗材），`ar.period.lock.1.2026-11` 为 `0`（未锁），
> 导入后 2026-11 账期可直接演示。

`db/Dump*/` 与 `db/backup/` 在 `.gitignore` 里，是为了防止日后随手 dump 又被 `git add -A` 带进来；
`Dump20261109/` 已在 `.gitignore` 里显式放行，是仓内唯一一枚建库快照。

## 增量脚本：只有 v1.0.0 → v1.2.1 这一段

建库仍以整库 dump 为唯一入口，没有「从空库按版本顺序跑脚本」这条路，项目也未引入 Flyway/Liquibase。
库结构要往前走，首选重新导出一份整库 dump 覆盖 `db/Dump<日期>/`，并同步更新本文件的日期与枚数
（怎么产这份 dump 见 `docs/数据模型.md` §6）。

唯一的例外是 `db/upgrade/`：把 **v1.0.0 升到 v1.2.1** 所需的增量脚本（结构、参数、菜单、种子）成对发布，
供不能重导库的部署使用。更早的历史脚本不发布——外部还原不了执行顺序，发了也没意义。

`db/hotfix/` 放**数据级**定点修正（某个环境的数据被演练写坏、界面上又没有入口改），
与版本无关，每个文件同样成对带 rollback。首个条目是 2026-11-05 的差旅费预算行解冻。

注意 `db/upgrade/` **不含**制证科目守卫的主数据（34 条明细科目 / 210 条费用项目映射 / 112 条记账规则），
那批是 11-04 的库内迁移，没有沉淀成脚本；原地升级时必须另行补齐，否则报销提交会被科目守卫拦下。
详见 `db/upgrade/README.md` 与 `docs/更新说明-v1.2.0.md` 第八节。
