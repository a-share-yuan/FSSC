# db/upgrade/ — v1.0.0 → v1.2.2 增量脚本

本目录是仓库里**唯一**发布增量 SQL 的地方，只覆盖 `v1.0.0` 升到 `v1.2.2` 这一段。
更早的历史脚本（V1.4.x / V1.5.x）不发布——外部无法还原执行顺序，也没有意义。

- 推荐路径仍然是 **重导 `db/Dump20261114/`**（结构＋主数据一起到位，见 `db/README.md`）。
- 本目录给的是**必须原地升级**时的官方物料：结构、参数、菜单、种子数据都在这里。
- 每个脚本都幂等（`NOT EXISTS` / 临时过程判在册），可以重复执行；多数提供配套 `*_rollback.sql`，下方本期新增组里的数据级定点脚本不提供回滚，执行前请先备份。

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
         V1.6.20_prepay_max_ratio.sql \
         V1.6.21_budget_item_freeze_perm.sql \
         V1.6.22_review_rule_config.sql \
         V1.6.23_credit_auto_approve.sql \
         V1.6.24_violation_reason.sql \
         V1.6.25_precheck_stats_menu.sql \
         V1.6.26_comment_normalize.sql; do
  mysql -uroot -p --default-character-set=utf8mb4 fssc < "$s" || break
done

# 3) 停服窗口：Flowable 租户回填（必须在旧 jar 停掉、新 jar 起来之前）
#    systemctl stop fssc / 停掉 java 进程（有看门狗的先停看门狗）
mysql -uroot -p --default-character-set=utf8mb4 fssc < V1.6.03_flowable_tenant_backfill.sql
#    换 1.2.2 的 jar 起服
```

## 注意：这组脚本**不覆盖**制证科目守卫的主数据

v1.2.0 的「费用项目 → 会计科目」映射是**业务配置**，不是结构，脚本里没有：

| 缺什么 | 影响 |
| --- | --- |
| 34 条明细科目（660205～660231、660301/660303/660304 等） | 泛科目无法落末级 |
| 210 条 `md_expense_item.subject_id` | 报销单/对公结算单**提交即被拦**（"未配置对应会计科目，无法制证"） |
| 112 条 `md_gl_config` 借方科目 | 自动制证仍走旧泛科目 |
| `tax_provision_rule` 3 行种子 | 计提规则页空白 |

V1.6.08 只补了 2 条税金科目，那 34+210+112 是 2026-11-04 的库内迁移（带 `*_bak_20261104` 备份表），没有沉淀成脚本。

**所以跑完本目录的脚本后，必须二选一：**

1. 按 `docs/更新说明-v1.2.0.md` 第八节「旧库升级」的清单补齐主数据（科目、费用项目映射、记账规则、计提规则），跑完用那节的自检 SQL 核对；
2. 或者直接重导 `db/Dump20261114/`，一次性到位。

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
| `V1.6.21` | 预算行冻结/解冻权限点 `budget:item:freeze`，并等价授予已持有 `budget:item:create` 的角色 | ✔ |
| `V1.6.22` | 智能审单规则页权限码 `policy:list`→`compliance:review:list`、菜单移到「共享运营」、清理 3 条波次测试规则、停用恒不命中的 `PRE-INV-DUPLICATE` | ✔ |
| `V1.6.23` | 员工信用铺底（452 人，600 分/C 级/500 元免审额度）、信用免审三参数（总开关/最低等级 B/抽检 10%）、`RR_AMOUNT_HIGH` 归档说明 | ✔ |
| `V1.6.24` | 违规原因字典 `md_violation_reason`（6 条行为类）+ `op_quality.violation_reason_code`，质检退回按类扣分 | ✔ |
| `V1.6.25` | 「预检漏拦分析」看板菜单，并把 `compliance:precheck:list` 补给合规岗（此前该组码一条授权都没有） | ✔ |
| `V1.6.26` | 表/列注释规范化（只改 COMMENT，不动类型与数据） | — |

> `V1.6.18` 建的那张 `*_bak20261104` 表是脚本自带的备份，确认数据无误后可自行删除，
> 它不在 `db/Dump20261114/` 里。

## 本期新增脚本（接在 V1.6.26 之后）

`db/Dump20261114/` 已包含本期大部分结构、菜单与主数据变更（含 `pf_tenant.op_mode`、
`FC_WF_CONFIG`、表单平台配置、菜单图标回填与仓库主数据 `md_warehouse`）。

**例外**：`sys_notify_template` 表已随快照发布、不必再单独建表；但 `V1.6.107` 的第二条模板
（质检退回）与 `V1.6.106` 的三枚灰度配置、CLAIM 锚点都晚于这份快照，新部署导入 dump 之后
仍需执行这两支——代码对缺失的模板与配置是 fail-closed（查不到就不通知、不开功能），
不执行不会报错，只是对应通知与重提跳回不生效。其余脚本只服务**已经在跑、不能重导库**的环境。

### 结构与菜单（仅原地升级需要）

```bash
for s in V1.6.92_tenant_op_mode.sql \
         V1.6.93_preset_flow_diagram.sql \
         V1.6.94_edition_flow_config.sql \
         V1.6.95_flow_settings.sql \
         V1.6.96_20261108_linku_idem_key.sql \
         V1.6.97_form_platform.sql \
         V1.6.99_drop_form_design_menu.sql \
         V1.6.100_menu_icons_backfill.sql \
         V1.6.101_20261110_warehouse_master.sql \
         V1.6.105_20261111_exp_notify_template.sql \
         V1.6.106_20261111_f5_resubmit_jumpback_enable.sql \
         V1.6.107_20261112_exp_quality_back_template.sql; do
  mysql -uroot -p --default-character-set=utf8mb4 fssc < "$s" || break
done
```

| 脚本 | 做什么 |
| --- | --- |
| `V1.6.92_tenant_op_mode.sql` | `pf_tenant` 增加使用方式标记与选择时间，首登引导据此决定是否跳转 |
| `V1.6.93_preset_flow_diagram.sql` | 为缺少图形位置信息的流程模型补 BPMN DI 布局，执行语义不变 |
| `V1.6.94_edition_flow_config.sql` | 新增 `FC_WF_CONFIG` 能力码，流程维护能力从共享运营拆出 |
| `V1.6.95_flow_settings.sql` | 「流程设置」归入系统管理，管理类角色补齐流程维护权限 |
| `V1.6.96_20261108_linku_idem_key.sql` | 开放接口审计表生成列改为「成功且幂等键非空」才生成值 |
| `V1.6.97_form_platform.sql` | 可配置表单平台：单据类型注册 + 通用单据运行库 |
| `V1.6.99_drop_form_design_menu.sql` | 下线尚未提供页面的「表单设计」入口与权限点 |
| `V1.6.100_menu_icons_backfill.sql` | 按 `path` 补齐空图标项，已有配置不动 |
| `V1.6.101_20261110_warehouse_master.sql` | 建 `md_warehouse` 并铺默认仓（上海总部、昆山），把 `md_inventory.warehouse_id` 回填到本公司默认仓；配套 `*_rollback.sql` |
| `V1.6.105_20261111_exp_notify_template.sql` | 建 `sys_notify_template`（触发点 → 标题/正文/深链），内置「报销单被驳回」一条模板 |
| `V1.6.106_20261111_f5_resubmit_jumpback_enable.sql` | 补三枚 `wf.resubmit.jumpback.*` / `wf.reject.free.*` 灰度配置，并把 CLAIM 的 `Task_7` 设为跳回锚点；配套 `*_rollback.sql` |
| `V1.6.107_20261112_exp_quality_back_template.sql` | 补「报销单质检退回整改」模板行（`EXP_QUALITY_BACK`），质检结果为 `BACK` 时通知申请人；配套 `*_rollback.sql`（默认只停用） |

### 仅已上线系统升级用

| 脚本 | 做什么 |
| --- | --- |
| `V1.6.92_tenant_op_mode_backfill.sql` | 把升级前就在运行的租户标记为共享版，避免被拦在引导页。**新部署不要执行**，保持标记为空让管理员自行选择 |

### 可选 / 定点

| 脚本 | 做什么 |
| --- | --- |
| `V1.6.98_form_platform_sample.sql` | 可选样例配置：一张通用申请单 + 一条流程路由 + 五个字段，只写配置数据 |
| `V1.6.96_purge_orphan_budget_occupy.sql` | **特定库的一次性数据修正**：按写死的流水 id 回收已删除单据遗留的预算占用，只适用于产生该状态的那套库，其它部署不要执行 |

本组脚本中带 `*_rollback.sql` 的可按同名逆序回滚；数据级定点脚本不带回滚，
执行前请先备份。

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
