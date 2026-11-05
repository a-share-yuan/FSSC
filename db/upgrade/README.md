# db/upgrade/ — v1.0.0 → v1.1.1 增量脚本

本目录是仓库里**唯一**发布增量 SQL 的地方，只覆盖 `v1.0.0` 升到 `v1.1.1` 这一段。
更早的历史脚本（V1.4.x / V1.5.x）不发布——外部无法还原执行顺序，也没有意义。

- 推荐路径仍然是 **重导 `db/Dump20261104/`**（结构＋主数据一起到位，见 `db/README.md`）。
- 本目录给的是**必须原地升级**时的官方物料：结构、参数、菜单、种子数据都在这里。
- 每个脚本都幂等（`NOT EXISTS` / 临时过程判在册），可以重复执行；每个都有配套 `*_rollback.sql`。

## 执行顺序

```bash
# 0) 备份！这些脚本会改结构、改菜单、加参数
mysqldump -uroot -p --single-transaction --databases fssc fssc_image > backup_before_v111.sql

# 1) 结构 + 种子 + 菜单 + 权限（覆盖 V1.6.00/01/02/04/05/06/07/08/09/10/11/12/13/14）
mysql -uroot -p --default-character-set=utf8mb4 fssc < V1.6.90_20261103_all_in_one.sql

# 2) 后续增量，按序
for s in V1.6.15_tax_declaration_no_prefix.sql \
         V1.6.16_inv_transfer_reason.sql \
         V1.6.17_material_master_seed.sql \
         V1.6.18_budget_item_496_orphan_map.sql \
         V1.6.19_menu_withdraw_period_perm.sql \
         V1.6.20_prepay_max_ratio.sql; do
  mysql -uroot -p --default-character-set=utf8mb4 fssc < "$s" || break
done

# 3) 停服窗口：Flowable 租户回填（必须在旧 jar 停掉、新 jar 起来之前）
#    systemctl stop fssc / 停掉 java 进程（有看门狗的先停看门狗）
mysql -uroot -p --default-character-set=utf8mb4 fssc < V1.6.03_flowable_tenant_backfill.sql
#    换 1.1.1 的 jar 起服
```

##  这组脚本**不覆盖**制证科目守卫的主数据

v1.1.1 的「费用项目 → 会计科目」映射是**业务配置**，不是结构，脚本里没有：

| 缺什么 | 影响 |
| --- | --- |
| 34 条明细科目（660205～660231、660301/660303/660304 等） | 泛科目无法落末级 |
| 210 条 `md_expense_item.subject_id` | 报销单/对公结算单**提交即被拦**（"未配置对应会计科目，无法制证"） |
| 112 条 `md_gl_config` 借方科目 | 自动制证仍走旧泛科目 |
| `tax_provision_rule` 3 行种子 | 计提规则页空白 |

V1.6.08 只补了 2 条税金科目，那 34+210+112 是 2026-11-04 的库内迁移（带 `*_bak_20261104` 备份表），没有沉淀成脚本。

**所以跑完本目录的脚本后，必须二选一：**

1. 按 `docs/更新说明-v1.1.1.md` 第八节「旧库升级」的清单补齐主数据（科目、费用项目映射、记账规则、计提规则），跑完用那节的自检 SQL 核对；
2. 或者直接重导 `db/Dump20261104/`，一次性到位。

只跑脚本不补主数据，服务能起、页面能开，但**报销提交会被科目守卫拦下**——这是设计行为，不是故障。

## 脚本清单

| 脚本 | 做什么 | 回滚 |
| --- | --- | --- |
| `V1.6.90_20261103_all_in_one.sql` | V1.6.00～V1.6.14 合集：加列助手、税金计提规则表、预算链、税务菜单、税金种子、平台清理与视图、预算下达权限、科目末级修正、预提预算占用、租户列收口、进项认证台账退役、权限补授、参数说明清理 | 按子脚本 `*_rollback.sql` 逆序 |
| `V1.6.03_flowable_tenant_backfill.sql` | 流程引擎定义/实例的 `tenant_id` 回填（**停服窗口**） | 同名 rollback |
| `V1.6.15` | 税务申报单号去 `TD` 前缀 | ✔ |
| `V1.6.16` | `inv_invoice.transfer_reason` 列（进项转出原因） | ✔ |
| `V1.6.17` | 物料主数据铺底（8 条劳保/办公物料，供采购订单行选） | ✔ |
| `V1.6.18` | 预算孤儿映射清理（208 行 `del_flag` + 2 条 `bg_item`），会建 `md_item_budget_map_bak20261104` 备份表 | ✔ |
| `V1.6.19` | 「我的费用单据」撤回入口菜单 + 会计期间只读点补授 + CONFIG_ADMIN 种子行归位 | ✔ |
| `V1.6.20` | `prepay.max.ratio` 参数（预付款比例上限，缺省 0.30） | ✔ |

> `V1.6.18` 建的那张 `*_bak20261104` 表是脚本自带的备份，确认数据无误后可自行删除，
> 它不在 `db/Dump20261104/` 里。

## 跑完怎么验

```sql
-- 结构：应返回 16
SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND (table_name, column_name) IN (
     ('bg_item','budget_type_id'),('bg_item','budget_period_id'),('bg_item','freeze'),
     ('exp_bill','tax_type'),('exp_bill','tax_period'),
     ('gl_journal_entry','expense_item_id'),('gl_journal_entry','dept_id'),
     ('gl_journal_entry','budget_item_id'),('gl_journal_entry','budget_occupy_status'),
     ('inv_invoice','transfer_reason'),('md_gl_config','doc_type'),
     ('md_item_budget_map','control_type'),('so_order_line','tenant_id'),
     ('tax_declaration','declaration_kind'),('tax_declaration','related_id'),
     ('wf_model_bpmn_backup','tenant_id'));

-- 参数与菜单
SELECT config_value FROM sys_config WHERE config_key = 'prepay.max.ratio';        -- 0.30
SELECT menu_name   FROM sys_menu   WHERE path = '/expense/my' AND del_flag = 0;   -- 我的费用单据
SELECT COUNT(*)    FROM md_inventory WHERE del_flag = 0;                         -- 8（物料铺底）
```
