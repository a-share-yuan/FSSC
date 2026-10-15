# FSSC 报表权限整改方案 —— 报表权限 = 业务域权限的投影

> 版本：v1.2（2026-09-17 P0 已执行 / P1 已落地待重启生效）
> 触发：Y009394（仅费用报销业务权限）登录后可见 24 张管理报表，其中 19 张属费用域之外业务
>
> ** P0 执行记录（2026-09-17 17:06）**：撤销越权角色-报表授权 **605 条**（原测算 647 − 白名单豁免 42），复核剩余越权 **0 条**。备份 `fssc/scripts/_report_rollback_exec_20260917_170638.json`。白名单豁免：内审经理(99011)、董事会(53)。管理层补码本轮未做（CFO 暂保留 20 张，补授应收/合同/影像域只读码后可恢复 25 张）。Y009394 实测：报表菜单 24 → **9 张**（费用域 7 + 审批效率 + 报表预聚合），全部落在业务权限内。
>
> ** P1 落地记录（2026-09-17 17:13，mvn compile 通过， 需重启 8089 生效）**：
> 1. **接口域防线**：新增 `framework/security/ReportDomain`（12 域枚举+判据码）、`annotation/ReportDomain`、`ReportDomainAspect`；ReportQueryController 28 个报表端点全部挂 `@ReportDomain`，无域业务权限（或 `report:audit:all` / `*:*:*`）访问即拒。
> 2. **授权卡点**：`RoleService.assignMenus` 增加报表域投影校验——授予 `/reports/*` 菜单时按"未来权限集"校验域判据码，违反整单拒绝并列出全部违规项。
> 3. **权限码细化（加法式）**：25 张报表菜单 perms 增补 `report:<domain>:view` 域码（保留 report:view 兼容既有）；备份 `_menu_perms_backup_20260917_171026.json`。
> 4. **审计阅示白名单**：新增隐藏 F 菜单「报表审计阅示」(`report:audit:all`, parent=10) 授予内审经理/董事会，接口侧全域放行 + 季度复核。
> 5. **CI 规则**：`perm-health.py` 新增 **R11 报表越权**（HIGH 级）；首跑即抓到 FIN_AP 凭 `pay:match:list` 越持资金域报表 2 条（三单匹配属应付侧，判据口径已全链统一），已撤销；复跑 **高危 0 项**。
> 判据唯一事实源：Java `ReportDomain` 枚举；Python 侧（matrix/perm-health）与之一致。

---

## 一、问题实证（2026-09-17 实测）

### 1.1 Y009394 案例（COMMON_EMP + DEPT_MANAGER）

| 维度 | 实测值 | 结论 |
|---|---|---|
| 业务菜单 | 仅费用报销域（报销/借款/还款/报账工作台） | 业务边界 = 费用域 |
| 报表菜单 | 24 张（费用域 7 张 + 越权 17 张） | 应付/应收/资金/合同/税负/运营/质检/影像/凭证统计全部可见 |
| 越权来源 | DEPT_MANAGER 被授 25 张报表（除 /report/summary 全集） | **角色-报表授权与业务授权脱钩** |

### 1.2 系统性现状（全量审计）

- 26 张报表菜单：25 张挂**同一个粗粒度权限码 `report:view`**，1 张挂 `report:summary:list`——无法按域表达。
- **58 个角色中 40 个被一刀切授了全部 26 张报表**（seed 模板复制痕迹）：出纳、人力资源总监、仓库管理员、董事长会、培训部门负责人……全部 26 报表全开。
- 授权链路 `角色 → sys_role_menu → sys_menu` 之间**没有任何「报表 ⊆ 业务」约束**，授权时可以只挂报表不挂业务，权限漂移无卡点。

### 1.3 根因定性

| # | 根因 | 层级 |
|---|---|---|
| R1 | 角色-报表菜单授权一刀切（40/58 角色全量） | 授权数据 |
| R2 | 报表权限码粗粒度（25 张共用 report:view） | 权限码设计 |
| R3 | 无「报表授权 ⊆ 业务域授权」联动校验 | 机制（授权卡点缺失） |

---


### 2.1 核心原则

> **报表权限不是独立授权项，而是业务域权限的投影。用户在某业务域没有业务权限，该域报表一定不可见——机制保证，不靠人工自觉。**

 NC/BIP 对应做法：功能权限 = 模块 → 功能节点 → 按钮，报表节点挂在模块下，未授模块则报表节点不可授权。

### 2.2 业务域 × 报表归属矩阵（25 张纳入域整改）

| 业务域 | 域判据权限码（角色命中任一即视为持有该域） | 归属报表 |
|---|---|---|
| EXP 费用 | exp:bill:list / exp:bill:audit / exp:share:list | 费用发生汇总、费用月度变动、费用构成情况、费用明细表、借款余额汇总、借款逾期分析、部门报销排名 |
| BUDGET 预算 | budget:scheme:list / budget:adjust:list / budget:occupy:list / budget:item:list | 预算执行、预算超支预警 |
| AR 应收 | receivable:list | 应收账龄分析、销项发票统计 |
| P2P 采购应付 | po:order:list / inv:list / pay:match:list / pay:recon:list | 应付账龄分析、供应商付款汇总、进项发票统计 |
| CASH 资金 | op:pay:list / treasury:*:list / bank:reconcile:list / op:bank:list | 资金收支汇总、资金余额表 |
| GL 总账 | gl:entry:list / voucher:list:page / md:subject:list | 凭证生成统计 |
| TAX 税务 | gl:taxrefund:list / inv:certify / gl:taxprov:list | 税负分析 |
| CONTRACT 合同 | contract:list | 合同汇总、合同执行明细 |
| SHARING 共享运营 | sharing:task:list / op:quality:list / op:archive:list | 运营数据统计、运营效率分析、质检合格率 |
| APPROVAL 审批 | exp:bill:audit / sharing:task:list | 审批效率 |
| IMAGE 影像 | image:batch:list / image:review | 影像统计 |

> `/report/summary`（报表预聚合）有独立码 `report:summary:list`，不纳入域整改。

### 2.3 例外机制：监督阅示岗（Audit Exception）


- 新增配置 `report_audit_exception`（或复用权限治理 v2 的全局配置）：角色级白名单，命中的角色保留全域报表；
- 白名单角色进入季度权限复核清单（recert-export.py）强制复核；
- 首批候选：INTERNAL_AUDITOR（内审经理）、BOARD_OF_DIRECTORS（董事会）——**是否纳入由业务确认**。

**管理层阅示**（CFO 撤了应收账龄等 5 张）：CFO 缺 `receivable:list` 等应收域只读码。处理原则：**给管理层补授域只读查询码，而不是给报表开口子**——权限对依然成对出现，投影规则不被破坏。CFO/FIN_DIRECTOR/GM/VP 等领导角色需补哪些域码，附在整改报告里由业务逐项确认。

---

## 三、分阶段落地

### P0 授权数据整改（纯 SQL，无代码改动，立即生效）

**已完成 dry-run**，工具与产物：

| 产物 | 路径 | 说明 |
|---|---|---|
| 域矩阵计算器 | `fssc/scripts/report-domain-matrix.py` | 域判据/报表归属可维护，重跑即重算 |
| dry-run 报告 | `fssc/scripts/_report_delta.txt` | 58 角色、撤销 647 条、保留 568 条，逐条列出 |
| 整改 SQL | `fssc/scripts/_report_remediate_20260917_1700.sql` | DELETE sys_role_menu，**未执行** |
| 回滚备份 | `fssc/scripts/_report_rollback_20260917_1700.json` | role_id+menu_id 全量，可精确回滚 |

Y009394 相关角色整改后效果：

| 角色 | 改前报表 | 改后报表 | 撤销 |
|---|---|---|---|
| COMMON_EMP | 6 | 5（费用域） | 预算执行 1 |
| DEPT_MANAGER | 25 | 8（费用域+审批效率） | 17 |

**执行前置条件（需业务确认）**：
1. 例外白名单角色名单（内审/董事会）——确认后这些角色从 SQL 中剔除；
2. 管理层补码清单（CFO 缺应收域等）——确认补码后对应报表撤销项剔除；
3. SHARING 域判据复核——出纳等角色因历史模板污染持有 sharing:task:list，是否属真实业务权限。

### P1 机制固化（代码改动，防复发）

| # | 改动 | 位置 | 说明 |
|---|---|---|---|
| 1 | 授权卡点：给角色勾选报表菜单时，校验该角色已持有对应域判据码，无则拒绝并提示 | `TenantRoleController.assignMenus`（复用权限治理 v2 的 SoDService 校验模式） | 治本：授权动作无法再产生漂移 |
| 2 | 报表权限码细化：`report:view` → `report:<domain>:view`（如 report:exp:view / report:ar:view），26 菜单批量 UPDATE，后端报表接口 `@RequiresPerm` 同步 | sys_menu + Report*Controller | 消除 R2，接口层可独立按域拦截 |
| 3 | CI 规则：`perm-health.py` 新增「报表授权 ⊆ 业务域授权」规则，每次巡检输出越权矩阵 | `fssc/scripts/perm-health.py` | 防数据再漂移 |
| 4 | 导航地图/入口页已改为菜单树同源判定（今日已完成），报表菜单撤权后地图自动收敛 | 无需改动 | — |

### P2 数据权限细化（行级，复用既有设施）

- 报表行级过滤统一走 `DataScopeKit`（@DataScope 已落地）：部门报销排名只看本部门及下级、员工只看本人费用（数据层面而非仅菜单层面）；
- 管理报表查询日志纳入审计哈希链（涉敏感汇总数据）。

---

## 四、影响面与回滚

| 项 | 说明 |
|---|---|
| 影响面 | 58 角色 / 647 条角色-报表授权撤销；涉及用户下次登录（菜单树实时查库，无需清缓存）或刷新即时生效 |
| 前端 | 无需改动（菜单树同源，撤权自动收敛）；导航地图同步收敛 |
| 后端 | P0 无改动；P1 需发版（assignMenus 卡点 + 权限码细化） |
| 回滚 | `_report_rollback_20260917_1700.json` → 按条重新 INSERT sys_role_menu，精确到条 |
| 验证 | ① Y009394 登录：报表菜单收敛为费用域 7 张 ② 逐角色 `check-user-perms.py <账号>` 比对 ③ `perm-health.py` 全绿 ④ 领导层账号抽查（补码后报表不缺失） |

## 五、待确认事项（执行前必答）

1. **例外白名单**：内审经理、董事会是否保留全域报表？
2. **管理层补码**：CFO/总经理/副总裁等是否补授应收/合同/影像等域只读码（补码后对应报表保留）？
3. **SHARING 判据**：出纳等非共享岗持有 sharing:task:list 是否历史污染需要专项清理？
4. **执行窗口**：SQL 直接执行（菜单实时生效）还是随维护窗口？
