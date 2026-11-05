# db/ — 整库 dump（建库入口）

`Dump20261104/` 是本项目一次演练库的 MySQL 整库导出，**建库、起服务、演示都以它为准**。
326 个文件＝一表一文件 325 枚（主库 `fssc` 319 枚，影像库 `fssc_image` 6 枚）＋ `fssc_routines.sql`（存储过程与函数），合计约 6.9 MB。
## 导入

推荐使用 MySQL Workbench 选择 Dump20261104 目录后全部导入。

```bash
# 每个文件自带 CREATE DATABASE IF NOT EXISTS + USE，两个库一起建好，无需预先手工建库
for f in db/Dump20261104/fssc_*.sql; do
  mysql --default-character-set=utf8mb4 -uroot -p < "$f"
done
```

Windows（CMD）：

```bat
for %f in (db\Dump20261104\fssc_*.sql) do mysql --default-character-set=utf8mb4 -uroot -p < "%f"
```

- 依赖 MySQL 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`。
- 每个文件都带 `SET FOREIGN_KEY_CHECKS=0`，所以**文件名顺序不影响导入成败**；脚本按 `DROP TABLE IF EXISTS + CREATE + INSERT` 写，可重复执行。
- 视图：本 dump 里有一枚 `v_tax_input_certify`（进项发票认证视图，在 `fssc_routines.sql` 内），语句前面带着
  `/*!50013 DEFINER='root'@'localhost' SQL SECURITY DEFINER */` 这一段。目标库里没有 `root@localhost` 这个账号时，
  这一个文件会报 `ERROR 1449`；把那一段 definer 注释删掉再单独重导这一个文件即可（其余 325 个文件不受影响）。
  存储过程、函数、触发器：无。
- Flowable 的 `ACT_*` / `FLW_*` 表已在 dump 内；`flowable.database-schema-update=true` 会在首启时补齐缺项。
- 影像库表当前只有配置行、无业务行（与源库一致），上传发票后自动产生数据。
- 口令一律走 `.env` / 环境变量（见根目录 `.env.example`），本目录不含任何明文凭据文件。

## 这份 dump 里有什么

结构、主数据、菜单与权限、流程定义齐全，**业务单据为空**（`exp_bill` / `op_voucher` / `wf_instance` 均为 0 行）——
它是清库后重新铺底的一版，导入即得一个干净可演示的库，单据由使用者自己跑出来。
`act_ge_bytearray` 的 2.2 MB 是 190 份 BPMN 定义与 183 张流程图，**不是上传附件**。
`fssc_crm_customer` / `fssc_md_supplier(_contact)` / `fssc_sys_user` 等主数据表里带着名称、邮箱、手机号形态的字段值，
发布前请确认这些数据可以公开；要换一批干净数据，就重新导出一份同名结构的 dump 覆盖本目录并同步更新本文件的日期。

> 内容构成：`2026-11-04 23:30` 的整库导出，再于 `2026-11-05` 补入 `V1.6.17`～`V1.6.20` 四个增量
> （物料铺底 8 条、预算孤儿映射清理、撤回入口菜单与权限、预付款比例参数），因此
> `md_inventory` / `bg_item` / `md_item_budget_map` / `sys_config` / `sys_menu` / `sys_role_menu`
> 其中 `md_inventory` / `bg_item` / `md_item_budget_map` / `sys_config` / `sys_menu` / `sys_role_menu` / `employee_credit` / `op_review_rule` / `op_quality` 与新增的 `md_violation_reason` 是 11-05 重新导出的，其余文件保持 11-04 的原样。

`db/Dump*/` 与 `db/backup/` 在 `.gitignore` 里，是为了防止日后随手 dump 又被 `git add -A` 带进来；
`Dump20261104/` 是**明确 `git add -f` 入库**的那一枚例外。

## 增量脚本：只有 v1.0.0 → v1.2.0 这一段

建库仍以整库 dump 为唯一入口，没有「从空库按版本顺序跑脚本」这条路，项目也未引入 Flyway/Liquibase。
库结构要往前走，首选重新导出一份整库 dump 覆盖 `db/Dump<日期>/`，并同步更新本文件的日期与枚数
（怎么产这份 dump 见 `docs/数据模型.md` §6）。

唯一的例外是 `db/upgrade/`：把 **v1.0.0 升到 v1.2.0** 所需的增量脚本（结构、参数、菜单、种子）成对发布，
供不能重导库的部署使用。更早的历史脚本不发布——外部还原不了执行顺序，发了也没意义。

`db/hotfix/` 放**数据级**定点修正（某个环境的数据被演练写坏、界面上又没有入口改），
与版本无关，每个文件同样成对带 rollback。首个条目是 2026-11-05 的差旅费预算行解冻。

注意 `db/upgrade/` **不含**制证科目守卫的主数据（34 条明细科目 / 210 条费用项目映射 / 112 条记账规则），
那批是 11-04 的库内迁移，没有沉淀成脚本；原地升级时必须另行补齐，否则报销提交会被科目守卫拦下。
详见 `db/upgrade/README.md` 与 `docs/更新说明-v1.2.0.md` 第八节。
