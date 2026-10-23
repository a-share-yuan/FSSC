# 代码阅读指南（how-to-read-this-code）

> 本仓库主体代码经长期真实业务演练加固。这类代码库的典型风险是
> "能跑但读不动"。本文给一套 30 分钟建立地图的方法。

## 1. 先建立三个坐标

### 坐标一：域分包
```
com.fssc.<domain>/
  ├── controller/   入口，统一 /api/tenant/<域>/
  ├── service/      业务逻辑（接口 + Impl）
  ├── entity/       表实体（类名驼峰 = 表名下划线）
  ├── mapper/       纯注解 Mapper（无 XML）
  └── （域特有）dto/vo/convert/
```
**找功能的路径**：菜单/界面反查 → 前端 `api.js` 端点 → 后端 Controller → Service →
Mapper → 表。

### 坐标二：表前缀
见 [数据模型](data-model.md) §2。`md_`=主数据、`exp_`=报销、`op_`=运营、
`wf_`=流程配置、`act_`=引擎。看到表名就知道去哪个域。

### 坐标三：两道横切
- **租户拦截器**：所有 SQL 自动追加 `tenant_id`，业务代码不写租户条件；
- **权限注解**：`@RequiresPerm` 在 Controller 上，是接口级闸门。

## 2. 推荐阅读顺序

| 步骤 | 读什么 | 目的 |
|---|---|---|
| 1 | `FsscApplication.java` | 看自动装配了哪些 starter/config |
| 2 | `framework/`（租户/权限/异常） | 理解两道横切 |
| 3 | `platform` 域（登录/字典/菜单） | 理解 SaaS 底座 |
| 4 | `expense` 域（报销主链） | 理解业务域的标准写法 |
| 5 | `workflow` 域 | 理解流程引擎接入 |
| 6 | `md_*` 主数据 | 理解"配置驱动"的边界 |

报销单是**最完整的业务域样本**：它有审批流、有影像、有发票、有付款、有凭证，
读完它其它域都是变体。

## 3. 识别"AI 味"代码的特征与对策

| 特征 | 风险 | 对策 |
|---|---|---|
| 超长 Service 方法 | 逻辑缠成一团 | 先读方法头注释（本项目要求写清输入输出口径） |
| 大量冗余 getter/setter | 噪声 | 用 IDE 结构视图，忽略样板 |
| 注释里的"实测/验证"记录 | **宝藏** | 这些是人工加固时留的锚点，比代码更可信 |
| `_bak`/`_tmp` 命名 | 施工残留 | 正式模型里没有，见命名规范 |

## 4. 读之前先问三个问题

1. **这张单的生命周期是什么？**（状态机：草稿→审批中→...→终态）
2. **钱和账在哪落脚？**（付款批次？凭证？哪个域写的？）
3. **谁可能改它？**（本人/审批人/共享中心/定时任务）

三个问题都能在域内 `service` 的注释头或 [业务流程文档](README.md) 找到答案。

## 5. 改动前必读

- **改表**：先查冷表（`_cold`）是否要同步加列——归档依赖逐列同序；
- **改流程**：node_key 必须命中已部署 BPMN 的 userTask id，见 [流程引擎](workflow-engine.md)；
- **改权限**：菜单码 ↔ 接口码 ↔ 角色授权三方对齐，见 [权限体系](permissions.md)；
- **改字典**：按域建，不跨域复用；展示取词走 `enumLabel`/`statusLabel` 收口点。

## 6. 测试与验证姿势

- 后端单测：`mvn test -pl fssc-start`（域级用例覆盖核心 service）；
- 接口验证：登录后带 JWT 调 `/api/tenant/...`，先用 admin 排除权限干扰；
- 造数：`fssc/fssc-server/scripts/seed_biz_data.py`（`--dry-run` 先看）；
- 前端：不跑 `vite build`（会写 dist 干扰 dev），用 `@vue/compiler-sfc` 做语法校验。

## 7. 文档引用核对

本文档中所有 `path:line` 形式的引用由 `_tools/cite_check.mjs` 在 CI 中核对，
失效引用会直接让 CI 变红——如果你读到过期引用，那是 bug，请提 issue。
