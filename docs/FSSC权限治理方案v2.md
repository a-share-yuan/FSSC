# FSSC 权限治理方案 v2

> 版本：v2.0 ｜ 日期：2026-09-17 ｜ 状态：P1 已落地（DB+代码），P2~P4 待排期
> 关联：`docs/财务共享平台-AI开发文档.md` ｜ 实证依据：`实测报告/recert_20260917.md`、`scripts/scan-sod.py`、`scripts/check-user-perms.py`
> 对标：金蝶云苍穹 / 用友 BIP / SAP GRC 权限模型，及《企业内部控制基本规范》18 项指引、SOX 404

---

## 1. 治理目标与原则

**目标**：建立"最小授权、职责分离、可复核、可追溯"的权限体系，使权限风险可度量、可拦截、可回滚。


| 原则 | 含义 | FSSC 落点 |
|---|---|---|
| 最小授权 | 默认拒绝，授权必附理由与有效期 | 基础角色瘦身（§4）；COMMON_EMP=26 个最小码 |
| 职责分离（SoD） | 授权/执行/记录/保管/稽核五权分离 | 双层 SoD：角色对 + 用户级权限码并集（§5） |
| 分层授权 | 功能→数据→字段三层，角色分层复用 | v2 先做功能层，数据/字段层列 P2 |
| 定期复核 | Recertification：主管季度确认授权 | `recert-export.py`（§6） |

---

## 2. 现状基线（2026-09-17 实测）

- 规模：**137 用户 / 78 角色 / 558 菜单**；权限链路 `sys_user → sys_user_role → sys_role_menu → sys_menu(perms)`。
- 权限计算：`SysUserMapper.selectPerms`（登录 perms claim）+ `SysMenuMapper.selectMenuIdsByUser`（菜单树），租户条件由 `FsscTenantLineHandler` 自动注入。
- 既有治理资产：`sod_role_conflict`（6 条角色对互斥，授权时实时校验）、`scan-sod.py`（22 条权限码规则）、`perm-health.py`（CI 10 规则）、UIMS 权限申请审批。
- **实证暴露的问题**（`recert_20260917.md`，129/137 用户有待复核项）：

| 发现 | 数据 | 风险定级 |
|---|---|---|
| 权限码级 SoD 冲突（用户并集） | **181 处命中**，重灾：CASHIER+FUND_STAFF（SoD-01/02/08/19/20）、CONFIG_ADMIN+FSSC_SUPERVISOR 285 码（SoD-10/11/12/21/22）、FIN_AP+FIN_GL+FM_ARCHIVIST 132 码（8 条规则命中） | 高 |
| 过度授权（>80 码） | 11 人，最高 285 码 | 高 |
| 从未登录仍持权 | 82 人 | 中 |
| 僵尸账号（无角色无组） | 2 人 | 低 |
| 基础角色被塞业务权限 | COMMON_EMP 曾含 `budget:scheme:create`/`budget:adjust:create`（全员可编制预算）；更早版本曾泄漏 `asset:*`/`wf:model:*`（Y009154 案实证） | 高 |

**结论**：问题不在"没有权限体系"，而在 ①基础角色当垃圾箱、②SoD 只有角色对维度（角色各自合法、组合违规的盲区）、③授权后无人复核。v2 按此三点根治。

---

## 3. 目标权限模型（四层）

```
用户 User ──┬── 直接角色 sys_user_role ──────────┐
            └── 用户组 sys_user_group_rel         │
                    └─→ sys_group_role ──────────┤
                                                 ▼
                                    角色 sys_role ─→ sys_role_menu ─→ 菜单/按钮 perms
                                                 ▼
                        生效权限 = 直接角色 ∪ 组派生角色 的菜单 perms 并集 ∩ 套餐 feature_code
```

- **用户组（新增）**：批量授权单元 = "一组角色 × 一批人"。业务权限一律经用户组授予，基础角色只放全员最小集。
- **角色分层**（P1 起步，P2 完善）：系统角色（COMMON_EMP 等）、业务岗位角色（FIN_GL、CASHIER…）、复合角色（叠加多岗位）、派生角色（按组织范围复用）。
- **数据/字段权限**（P2）：行级按 公司/部门/成本中心/项目 维度注入查询拦截器；字段级按角色控制金额、银行账号、税率可见性。

---

## 4. 【已落地】(a) 用户组抽象 + 基础角色瘦身

### 4.1 新增表（已执行 DDL，`scripts/_exec_gov_changes.py`）

| 表 | 用途 | 关键列 |
|---|---|---|
| `sys_user_group` | 用户组定义 | tenant_id, group_code(租户内唯一), group_name, status, remark |
| `sys_group_role` | 组→角色 | tenant_id, group_id, role_id（三元唯一） |
| `sys_user_group_rel` | 用户→组 | tenant_id, user_id, group_id（三元唯一） |
| `sod_perm_conflict` | SoD 权限码互斥规则（平台级 tenant_id NULL + 租户自定义） | rule_code, pattern_a, pattern_b, risk, note |

租户隔离：前三表由 `FsscTenantLineHandler` 自动注入 tenant_id；`sod_perm_conflict` 已登记全局忽略名单（同 `sod_role_conflict`），由服务手工按 `(tenant_id IS NULL OR tenant_id=当前)` 读取。

### 4.2 权限计算融合（已改码，`mvn compile` 通过）

| 文件 | 改动 |
|---|---|
| `SysUserMapper.java` | `selectPerms / selectRoleCodes / selectRoles` 均改为「直接角色 ∪ 组派生角色」UNION —— 登录 perms claim、角色展示、`*:*:*` 判定链全部生效 |
| `SysMenuMapper.java` | `selectMenuIdsByUser` 同样 UNION（菜单树经组角色可见）；新增 `selectPermsByRoleIds`（SoD 计算用） |
| `FsscTenantLineHandler.java` | 忽略名单补登记 `sod_perm_conflict` |
| 新增 | `SysUserGroup/SysGroupRole/SysUserGroupRel/SodPermConflict` 实体 + 4 个 Mapper |

>  生效条件：**纯后端改动，须重启 8089 后生效**（按铁律未重启，待授权）。

### 4.3 基础角色瘦身（已执行，含回滚脚本）

| 项 | 改动前 | 改动后 |
|---|---|---|
| COMMON_EMP（role_id=12，tenant=1）权限码 | 28 个（含 `budget:scheme:create`、`budget:adjust:create`） | **26 个**（budget:* 已剥离） |
| 剥离菜单 | — | 3101 预算调整编制、3105 预算方案创建（2 行 sys_role_menu，备份于 `sys_role_menu_bak_gov_20260917_*`） |
| 剥离权限去向 | — | 新角色 `BUDGET_EDITOR`（id=99026，挂 2 个预算菜单）→ 挂入用户组 `GRP_BUDGET_EDITOR`（id=1） |
| 组成员 | — | **空**，待业务主管确认后经「用户组管理」接口分配（见 4.4） |
| 回滚 | — | `scripts/gov_rollback_20260917_143343.sql` |

瘦身后 COMMON_EMP 保留 26 码 = 全员最小集：个人报销（exp:bill:list/save/submit/withdraw）、费用分摊（exp:share:*）、流程委托（wf:delegate:*）、单据填单所需主数据引用（md:expenseitem/expensestd/costcenter/taxrate/exchangerate/bankaccount/supplier + system:dept）、文件上传下载、消息、工作台/看板/报表查看。
> 说明：`md:supplier:list` 等"广谱只读引用"为全平台 100+ 角色共持的既定约定，本轮保留；是否收敛为引用组，列 P2 议题。

### 4.4 用户组管理接口（已新增，生效同 4.2）

`/api/tenant/system/group/**`：`list`（system:group:list）、`create`（system:group:create）、`{id}/assignRoles`、`{id}/assignMembers`（system:group:grant，**逐成员先跑用户级 SoD 再落库**）、`{id}/delete`（system:group:delete）。权限码为新编码，管理员经 `*:*:*` 直通；非管理员待菜单 seed 下发后可用（P1 收尾项）。

### 4.5 链路实证（`scripts/_verify_gov.py`，事务级）

```
组 id=1, 绑定角色=[BUDGET_EDITOR(99026)]
挂组前(Y009154): 26 码, 含 budget:* = False
挂组后:          28 码, 新增=[budget:adjust:create, budget:scheme:create]
回滚后:          26 码, 与挂组前一致=True；关系表已回滚(计数=0)
→ PASS：用户组→角色→权限链路生效，回滚无痕
```

---

## 5. 【已落地】(b) 用户级 SoD 实时校验

### 5.1 双层 SoD 体系

| 层 | 表 | 拦截时机 | 能捕获 |
|---|---|---|---|
| ① 角色对（既有） | `sod_role_conflict`（6 条） | `UserService.assignRoles` → `SoDService.assertNoConflict` | 明确互斥岗位组合 |
| ② **权限码并集（新增）** | `sod_perm_conflict`（**22 条**，已从 `scan-sod.py` 外部化入库） | `UserService.assignRoles` → `SoDService.assertNoUserPermConflict`；用户组 `assignRoles/assignMembers` 同样前置 | **"角色各自合法、组合违规"**（如 CASHIER+FUND_STAFF） |

### 5.2 校验算法（`SoDService.assertNoUserPermConflict`）

1. 生效角色集 = 直接角色 ∪ 组派生角色 ∪ 本次待授予角色；
2. 展开为权限码并集（`selectPermsByRoleIds`，兼容逗号多码）；
3. 逐条 ACTIVE 规则做 `pattern_a`×`pattern_b` 正则双命中检测（非法正则降级为不命中，不阻断授权）；
4. 命中 → 抛 `FORBIDDEN`（"不相容职务分离校验不通过（权限组合）：SoD-xx …"），事务回滚，授权不落库。

### 5.3 巡检与复核面

- 后端：`GET /api/tenant/system/sod/scan-perm`（system:sod:scan）→ `SoDService.scanPermViolations()` 返回当前租户全部命中用户。
- 脚本：`scripts/scan-sod.py`（原有，规则已同步入库为单一事实源）。

---

## 6. 【已落地】定期复核（Recertification）

**脚本**：`scripts/recert-export.py`（只读）

```bash
python scripts/recert-export.py --out 实测报告/recert_20260917.csv --stale-days 90
```

输出 CSV 全量矩阵（账号/用户组/角色/权限码/最近登录/标记）+ MD 汇总报告。自动标记：

| 标记 | 触发 | 处置建议 |
|---|---|---|
| SOD-PERM | 权限码并集命中 `sod_perm_conflict` | 拆分职责，或登记补偿性控制（谁复核、如何留痕） |
| SOD-ROLE | 命中 `sod_role_conflict` | 同上 |
| STALE>N 天 | N 天未登录仍持权（默认 90） | 回收角色/组 |
| NEVER-LOGIN | 从未登录 | 停用或确认 |
| OVERSIZE | 权限码 > 80 | 审查是否过度授权 |
| NO-ROLE | 无角色无组 | 确认僵尸账号 |

**复核流程（季度）**：跑脚本 → 按部门分发待复核清单 → 主管确认（保留/回收，SOX 404 留痕）→ 管理员执行回收 → `perm-health.py` CI 防回退。离职/转岗：人事事件触发 `assignRoles(空)`/组移除，实时生效。

**首轮实测结论**（recert_20260917）：129/137 人有待复核项；优先处置 Top3 —— ① CONFIG_ADMIN+FSSC_SUPERVISOR 两账号 285 码命中 5 条规则；② 4 名 FIN_AP+FIN_GL+FM_ARCHIVIST 组合 132 码命中 8 条；③ CASHIER+FUND_STAFF 组合命中付款链 5 条规则。属授权数据调整，**须业务确认后执行**。

---

## 7. Roadmap（v2 余量）

| 阶段 | 内容 | 状态 |
|---|---|---|
| P1 | 用户组抽象 + 基础角色瘦身 + 双层 SoD + 复核脚本 | **本轮完成**（重启后端生效）；待办：组管理菜单/权限码 seed SQL、首轮 Top3 SoD 治理（业务确认） |
| P2 | 数据权限精细化（组织/成本中心/项目行级）+ 字段权限（金额/银行账号/税率）+ 委托到期自动失效 | 待排期 |
| P3 | 复核流程线上化（确认单据 + 回收工单）+ 权限变更审计可视化 + 关键操作二次认证 | 待排期 |
| P4 | 命名统一 `{域}:{对象}:{动作}` 收敛 + 孤儿码清理 + perm-health 接 CI 常态化 | 待排期 |

## 8. 附录

**A. 变更清单（本轮）**
- DB：4 张新表、22 条 SoD 规则、COMMON_EMP -2 菜单、新角色 BUDGET_EDITOR、新组 GRP_BUDGET_EDITOR（回滚：`scripts/gov_rollback_20260917_143343.sql`）
- 后端（compile 通过，待重启生效）：`SysUserMapper`、`SysMenuMapper`、`FsscTenantLineHandler`、`SoDService`、`UserService`、新增 4 实体 + 4 Mapper + `UserGroupService` + `TenantUserGroupController`

**B. 22 条 SoD 规则摘要**：SoD-01 供应商欺诈、02 自审自付、03 自报自批、04 应付审批↔付款、05 制单↔过账、06 关账↔补录凭证、07 资产建档↔变动、08 银行对账↔收付、09 票据登记↔出纳、10 流程建模↔审批、11 权限管理↔业务审批、12 反欺诈检测↔复核、13~14 档案借阅/销毁双人、15~16 税务申报/退税双人、17 算薪↔发薪、18 预算编制↔调整审批、19 资金调拨发起↔执行、20 票据结算↔资金执行、21 主数据配置↔过账、22 预检规则↔检测。完整正则见 `sod_perm_conflict` 表。

**C. 纪律**：授权数据"减"必须部署后执行并业务确认；本轮回滚脚本仅供异常恢复；重启 8089 前不得执行 `mvn package`（2026-09-16 事故教训）。
