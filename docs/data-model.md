# 数据模型（data-model）

## 1. 库表分布

| 库 | 表数 | 内容 |
|---|---|---|
| `fssc` | 316 | 全部业务表 + 平台表 + Flowable 引擎表（`act_*`/`flw_*` 43 张） |
| `fssc_image` | 6 | 影像独立库：`img_batch`/`img_file`/`img_setting`/`image_exception`/`image_review`/`ocr_record` |

业务表约 270 张，按前缀分族：

```
md_   (53) 主数据        exp_  (18) 费用报销      op_  (28) 共享运营
sys_  (27) 平台管理      gl_   (16) 总账          wf_  (21) 流程配置
act_  (39) 流程引擎      ar_   (14) 应收流程      pf_  (13) 租户套餐
rc_    (5) 应收执行      inv_   (5) 发票          tr_   (4) 资金
bg_    (5) 预算          tax_  (4) 税务          img_  (3) 影像桥接
其余: cont_/bank_/po_/so_/rpt_/collab_/supplier_/archive_/fa_/sod_ ...
```

## 2. 命名规范

| 前缀 | 含义 | 例 |
|---|---|---|
| `md_` | master data，主数据/配置 | `md_expense_item` |
| `exp_` | 费用报销单据族 | `exp_bill`/`exp_bill_line` |
| `_cold` 后缀 | 冷归档表（结构同热表，逐列同序） | `exp_bill_cold` |
| `bak_` 前缀 | 施工备份表（**不属于正式模型**） | `bak_sys_user_20261019c` |
| `act_`/`flw_` | Flowable 引擎表，勿手工改 | `act_ru_task` |

**冷热归档铁律**：`INSERT INTO 冷表 SELECT *, NOW() FROM 热表` 依赖**逐列同序**。
热表加列必须同步冷表，否则归档失败；两边列数相同但列名不同更毒（顺序巧合对齐时
静默写错列）。

## 3. 公共字段（每张业务表都有）

| 字段 | 说明 |
|---|---|
| `id` | 自增主键（**本域主键**，联查时注意不是业务单号） |
| `tenant_id` | 租户隔离列，拦截器自动注入/重写 |
| `create_by`/`create_time`/`update_by`/`update_time` | 审计列 |
| `del_flag` | 逻辑删除（0 正常，1 删除，2 软删待审） |

## 4. 多租户隔离机制

- 拦截器解析 JWT 拿租户上下文，对所有 SQL 自动追加 `tenant_id = ?`；
- 跨租户访问没有"查不到"这种温柔报错，直接空结果；
- 租户套餐（`pf_feature`）与角色权限取交集，两个闸门任一不过即 403；
- **演示库**：租户 `DEMO1`（爱共享公司），`tenant_id = 1`。

## 5. 核心实体关系（报销主链）

```
exp_bill (报销单头)
 ├── exp_bill_line      明细行（费用项/金额/票据）
 ├── exp_bill_payment   付款记录
 ├── exp_bill_share     分摊明细
 ├── exp_bill_receipt   票据关联
 ├── exp_bill_trace     全链路时间线（联查入口）
 └── exp_bill_cold      冷归档

wf_instance ── wf_task ── wf_approval_item ── wf_node_config ── act_ru_task
                                                              （引擎执行态）

inv_invoice ── inv_invoice_line（发票/明细）
op_pay_batch ── op_pay_batch_detail（付款批次）
gl_book_voucher ── gl_book_voucher_line（凭证）
```

**联查注意**：`billId` 是本域自增主键，不等于业务单号字段；全链路时间线的唯一
真相源是流程实例的 `chain.summary.reached`。

## 6. 号段与单据编号

- `sys_number_rule`（号段规则）+ `sys_number_seq`（序列）双表驱动；
- 规则可配前缀/日期格式/流水位数/重置周期，租户级隔离；
- **空库造数后必须核对号段序列**，否则正式单会撞演示单号。

## 7. 字典体系（三层，勿跨域复用）

| 层 | 表/载体 | 作用 |
|---|---|---|
| 域字典 | `bill_sub_type` 等域内码表 | 下拉可选值口径 |
| 展示字典 | `sys_dict_data`/`sys_dict_type`（`enum_*` 类型） | 列表展示口径 |
| 状态字典 | `bill_status_display` | 单据状态扁平展示 |

**同一码在不同域含义不同是常态**（如 `IN` 在现金流量码=流入、收支设置=收入）。
字典按域建，跨域复用必出展示错乱。

## 8. 演示库数据规模

| 表 | 行数 | 说明 |
|---|---|---|
| `sys_user` | 460 | 含 admin 与各岗位演示账号（口令 123456） |
| `sys_menu` | 694 | 全量菜单 |
| `md_bank_account` | 113 | 银行账户（脱敏后为虚构号） |
| `crm_customer` | 63 | 客户/供应商主数据 |
| `act_re_procdef` | 315 | 已部署流程定义 |
| `wf_node_config` | — | 节点配置（**约四成 node_key 是孤儿行，引用统计前先扣**） |

完整清单与导入方式见 `db/README.md`。

## 9. 数据脱敏口径（本开源包）

演示库已整体脱敏：企业名全部虚构（爱共享系）、银行账号为 `622202` 前缀虚构号、
手机号为 `1380013xxxx` 保留测试号段、邮箱域名 `example.com`、
`sys_config` 中口令/令牌类配置为空值或 demo 值。生产凭据一律走环境变量。
