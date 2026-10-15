# FSSC 集团财务共享 SaaS 平台 — 架构设计说明书

> 版本：v1.0　　编制日期：2026-08-11
> 需求来源：《集团财务共享系统需求规格要点（立项/招标/可研版）》《集团财务共享系统流程引擎（BPM）专项详细技术要求》《财务共享项目详细业务设计方案 v3.6》《SaaS平台软件核心要求》
> 关联文档：`02-数据库设计说明书.md`、`03-API接口规范.md`

---

## 0. 一句话定位

一套代码、多租户隔离的**集团财务共享服务中心（FSSC）SaaS 平台**：平台方运营租户与套餐计费，租户方（集团企业）在自己的数据边界内完成全员报账、共享作业、流程审批、影像归档与核算抛转。

---

## 1. 架构总览

### 1.1 分层视图

```
┌──────────────────────────────────────────────────────────────┐
│  接入层   fssc-web(PC/Element-Plus)   fssc-h5(移动/Vant4→APP) │
│           企业微信 / 钉钉 / 扫描客户端                          │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTPS  Authorization / X-Company-Id / X-Client
┌───────────────────────────▼──────────────────────────────────┐
│  网关与安全层  JwtAuthFilter → TenantFilter → 权限注解 AOP     │
├──────────────────────────────────────────────────────────────┤
│  应用层                                                        │
│   platform 平台运营域  │ system 租户系统域 │ masterdata 主数据  │
│   expense 费用单据域   │ budget 预算域     │ workflow 流程域    │
│   sharing 共享运营域   │ image 影像域      │ invoice 发票域     │
├──────────────────────────────────────────────────────────────┤
│  引擎底座层                                                    │
│   Flowable 7 BPM │ 规则引擎(可配置) │ 编号规则 │ 配额 │ 审计   │
│   文件存储 │ 缓存 │ 消息通知 │ 定时任务                        │
├──────────────────────────────────────────────────────────────┤
│  数据层  MySQL 8（共享库共享 Schema + tenant_id）│ Redis │ 本地/OSS 文件 │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 工程结构

```
fssc/
├── fssc-server/          后端  Spring Boot 3.4.5 + MyBatis-Plus 3.5.9 + Flowable 7.1.0
├── fssc-web/             PC 管理端  Vue 3 + Element-Plus + Vite
├── fssc-h5/              移动端    Vue 3 + Vant 4 + Vite（预留 Capacitor 打 APP）
├── db/                   分域建表 SQL
├── docs/                 设计与接口文档
└── scripts/              建库、自检、编译检查脚本
```

后端包结构（**按业务域垂直切分，域内分层**）：

```
com.fssc
├── FsscApplication.java
├── common/                 通用层（不依赖任何业务）
│   ├── core/               Result / ErrorCode / PageQuery / PageResult
│   ├── entity/             BaseEntity
│   ├── enums/              SubjectType / BillType / BillStatus ...
│   ├── exception/          BizException / GlobalExceptionHandler
│   └── util/               工具类
├── framework/              框架层（SaaS 底座，不依赖业务域）
│   ├── config/             MybatisPlusConfig / WebMvcConfig / Knife4jConfig / MetaObjectHandler
│   ├── tenant/             TenantContext / TenantLineHandler / TenantFilter / SchemaValidator
│   ├── security/           JwtAuthFilter / TokenService / UserContext / LoginUser / 权限注解与切面
│   ├── cache/              CacheService（local / redis 双实现）
│   ├── log/                审计日志 AOP（双轨：平台日志 / 租户日志）
│   ├── quota/              套餐配额与功能开关校验
│   ├── file/               文件上传下载（租户隔离 + 一次性下载令牌）
│   └── seq/                单据编号规则引擎
├── platform/               L1 平台运营域   /api/platform/**
├── system/                 L2 租户系统域   /api/tenant/system/**
├── masterdata/             主数据域        /api/tenant/md/**
├── budget/                 预算域          /api/tenant/budget/**
├── expense/                费用单据域      /api/tenant/expense/**
├── workflow/               流程域          /api/tenant/wf/**
├── sharing/                共享运营域      /api/tenant/op/**
├── image/                  影像域          /api/tenant/image/**
└── invoice/                发票域          /api/tenant/invoice/**
```

**架构红线（由 ArchUnit 单测守护）**：
1. `platform` 包**禁止**依赖任何租户业务域的 Mapper / Service —— 防止平台管理员直连业务表。
2. `framework` 包**禁止**依赖任何业务域 —— 框架层若反向依赖业务，多租户底座就无法独立演进。
3. 业务域之间只允许通过 Service 接口调用，禁止跨域直连 Mapper。

---

## 2. 多租户隔离：四道防线

> 这是整个平台的**最高优先级设计**。SaaS 最高危事故是租户数据串户，一次泄露即项目死亡。
> 设计原则：**fail-closed**（宁可报错，绝不放行一条没有租户条件的业务 SQL）。

| 防线 | 位置 | 作用 | 失效后果 |
|------|------|------|----------|
| ① 身份防线 | `JwtAuthFilter` | 从**已验签**的 JWT 解析 tenantId，绝不信任任何请求头 | 前端可伪造租户身份 |
| ② 状态防线 | `TenantFilter` | 校验租户状态（正常/冻结/过期/注销），写入 `TenantContext` | 过期欠费租户仍可调接口 |
| ③ 权限防线 | `@RequiresPerm` / `@RequiresFeature` / `@PlatformOnly` / `@DataScope` AOP | 菜单权限、按钮权限、套餐功能开关、数据范围 | 越权访问本租户内不该看的数据 |
| ④ 数据防线 | MyBatis-Plus `TenantLineInnerInterceptor` | 所有业务表 SQL **自动**追加 `tenant_id = ?` | 跨租户数据泄露 |

### 2.1 为什么 tenantId 只能来自 Token

```
 错误做法：前端传 X-Tenant-Id 请求头
   → 攻击者改一个数字就能读别人的库

 本项目做法：tenantId 编码在 JWT claims 中，由服务端签发并验签
   → 系统中【不存在】X-Tenant-Id 这个请求头，前端无法影响租户上下文
```

`X-Company-Id` 是**存在**的，但它只在**本租户内部**切换纳税主体（集团型租户有多个法人公司），后端会校验该 company 归属当前租户，越界即拒绝。

### 2.2 拦截器注册顺序（高危坑）

```java
//  多租户拦截器必须在分页拦截器【之前】注册
interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(handler)); // 先
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(MYSQL));   // 后
```

顺序反了：分页的 `count` 语句不带 `tenant_id`，列表只有本租户数据，**总数却是全库的** —— 总数本身已经泄露了其他租户的数据规模。

### 2.3 认证链路的时序死锁及解法

`JwtAuthFilter`（Order 1）要查 `sys_user` 拿用户权限，但 `sys_user` 带 `tenant_id`，而租户上下文要等 `TenantFilter`（Order 2）才建立 —— 成环。

**解法**：`TenantContext.callAs(tenantIdFromVerifiedToken, () -> ...)` 临时建立上下文。
关键在于此处 tenantId 来自**已验签的 Token claims**，不是客户端可篡改的输入。
这比"把 `sys_user` 加进忽略表白名单"安全得多 —— 后者等于把认证表变成公共表，任何人都能查全库用户。

### 2.4 平台端的数据边界（红线）

```java
// 平台端登录后，tenantId 保持 null，而不是塞一个值
// 一旦给平台端塞租户ID，平台就能"合法"地查到某个租户的业务明细
```

- 平台端只能访问 `pf_*` 前缀表（无 `tenant_id`）。
- 平台端需要跨租户统计时，走 `TenantContext.runIgnoreTenant()` 显式开洞，且**只允许聚合查询**（count/sum），禁止查明细。该方法调用点在 ArchUnit 中登记白名单。

### 2.5 忽略表白名单（不参与 tenant_id 改写）

| 类别 | 表 |
|------|-----|
| 平台运营表 | `pf_*` 全部 |
| 全局共享定义表 | `sys_menu`（一套代码一套菜单）、`sys_dict_type_def`、`sys_dict_data_def`（平台内置字典） |
| 流程引擎表 | `ACT_*`、`FLW_*`（Flowable 自管，用其原生 TENANT_ID_ 字段） |

> 注意：Flowable 使用**独立的 MyBatis SqlSessionFactory**，MyBatis-Plus 的租户拦截器天然不会作用于 `ACT_*` 表。Flowable 侧的隔离通过其原生多租户能力（`tenantId` 参数）实现，见 §5.3。

### 2.6 数据库层强约束

1. 所有租户业务表**必须**有 `tenant_id bigint NOT NULL`，且紧跟 `id` 之后。
2. 所有租户业务表**必须**有以 `tenant_id` 打头的索引（`idx_tenant`）。
3. 租户表上**不得**存在"首列非 tenant_id"的唯一索引 —— 否则 A 租户建了编码 `C001`，B 租户就再也建不了，形成**跨租户编码抢占**（一种隐蔽的数据串扰）。
4. 审计日志表**不设** `del_flag`，Mapper 不提供 delete 方法。

这三条由 `scripts/db-check.sh`（构建期）+ `TenantSchemaValidator`（启动期）**双重自检**，不通过则**拒绝启动**。

---

## 3. 两级管理体系

### 3.1 主体类型（SubjectType）

| 类型 | 说明 | tenantId | 路由前缀 |
|------|------|----------|----------|
| `PLATFORM` | 平台超级管理员 / 运营人员 | **null** | `/api/platform/**` |
| `TENANT` | 租户内部用户（含租户管理员、财务、共享作业岗、普通员工） | 有值 | `/api/tenant/**` |

> 一期不引入外部协同端（供应商门户）。表结构预留 `SubjectType` 枚举扩展位。

### 3.2 平台超级管理员能力

| 模块 | 能力 |
|------|------|
| 租户管理 | 新增/审核租户、启用禁用、过期关停、注销、设置名称/域名/Logo |
| 套餐管理 | 套餐定义、功能开关组合、用户数上限、存储上限、API 次数上限 |
| 计费账单 | 订购、续费、账单生成、到期提醒、**欠费冻结** |
| 全局配置 | 平台参数、登录页、版权、消息模板 |
| 平台统计 | 租户数、活跃租户、用户数、使用量（仅聚合，不看明细） |
| 平台 RBAC | 平台用户、平台角色、平台菜单 |
| 平台审计 | 平台操作日志（与租户日志物理分离） |

### 3.3 租户管理员能力

用户管理、角色权限配置、组织机构、职位、企业个性化（Logo/系统名/登录页）、查看本租户账单与套餐、租户参数配置、租户操作日志、数据导出。

---

## 4. 权限模型（RBAC + 数据权限 + 功能开关）

### 4.1 三层权限叠加

```
最终可用权限 = 角色权限集合  ∩  套餐功能码集合
                   ↑                ↑
              租户自己配的       平台卖给他的
```

租户管理员把某菜单授给角色，但租户套餐没买这个功能 → **不生效**。这条交集规则在登录加载权限时一次算完，缓存 5 分钟，避免每次请求重复计算。

### 4.2 权限粒度

| 粒度 | 载体 | 前端 | 后端 |
|------|------|------|------|
| 菜单权限 | `sys_menu.menu_type = M/C` | 动态路由渲染 | 登录返回菜单树 |
| 按钮权限 | `sys_menu.menu_type = F`，`perms` 如 `exp:claim:submit` | `v-hasPermi` 指令 | `@RequiresPerm("exp:claim:submit")` |
| 数据权限 | `sys_role.data_scope` | — | `@DataScope` 切面拼 SQL 条件 |
| 功能开关 | `pf_feature.feature_code` 如 `F_EXPENSE_CLAIM` | 菜单过滤 | `@RequiresFeature("F_EXPENSE_CLAIM")` |

### 4.3 数据权限范围（data_scope）

| 值 | 含义 |
|----|------|
| `1` | 本租户全部数据（租户管理员、共享中心作业岗） |
| `2` | 本公司（纳税主体）数据 |
| `3` | 本部门及以下数据 |
| `4` | 本部门数据 |
| `5` | 仅本人数据（普通员工默认） |

**多角色取最宽**（`min(data_scope)`）—— 角色是"授予"而非"限制"。
**查不到角色时给最窄的 `5`** —— fail-closed。

### 4.4 权限码命名规范

`{域}:{模块}:{动作}`，例：`md:supplier:list`、`exp:claim:submit`、`op:dispatch:grab`、`platform:tenant:freeze`。

---

## 5. 流程引擎（BPM）方案

### 5.1 选型

**Flowable 7.1.0 + Spring Boot 3.4.5 + JDK 21**，满足招标文件"独立专业流程引擎、禁止 ERP/OA 内嵌简易工作流"的红线。

### 5.2 Flowable 原生能力 vs 需自建能力

| 招标要求 | Flowable 原生 | 需自建 |
|----------|---------------|--------|
| 可视化拖拽建模 | BPMN 2.0 标准 |  前端集成 `bpmn-js` 设计器（BPMN 对财务人员不友好，另做**表单化流程配置**转 BPMN） |
| 串行/并行/会签/或签 |  multiInstance | — |
| 条件分支/金额分支 |  网关 + EL 表达式 | 变量注入约定 |
| 驳回/撤回/加签/转签 | 部分（需 API 编排） |  `WfOperationService` 统一封装 |
| 流程版本管理 |  原生版本号 | 业务侧版本切换策略 |
| 多租户 |  原生 `tenantId` |  强制注入，禁止空租户部署 |
| **共享作业池派单** |  |  `op_work_order` + 派单规则引擎 |
| **SLA 时效与超时升级** | 部分（Timer 边界事件） |  `wf_node_config.sla_*` + 定时巡检 |
| **跨组织流转** |  |  审批人解析器按组织架构解析 |
| 全流程审计留痕 |  历史表 |  冗余 `wf_task_history` 便于业务查询 |

### 5.3 Flowable 多租户接入约定

1. **部署必带 tenantId**：`repositoryService.createDeployment().tenantId(String.valueOf(tenantId))`。
2. **发起必带 tenantId**：`runtimeService.createProcessInstanceBuilder().tenantId(...)`。
3. **查询必带 tenantId**：所有 `*Query` 强制 `.processInstanceTenantId(...)` / `.taskTenantId(...)`。
4. 统一由 `FlowableTenantGuard` 包装，业务代码**禁止**直接注入 `RuntimeService`/`TaskService`，只能通过 `WfEngineFacade`。这样"忘了加 tenantId"在编译期就不可能发生。

### 5.4 业务侧流程扩展表

Flowable 的 `ACT_*` 表结构面向引擎，不适合业务查询（列表页要按单据类型、金额、申请人、公司过滤）。因此建立**业务视图冗余表**：

- `wf_process_config` 流程定义业务扩展（绑定单据类型、公司、费用类型、生效范围）
- `wf_node_config` 节点配置（审批人规则、SLA 时长、节点权限）
- `wf_instance` 流程实例业务视图（带 tenant_id，冗余单据字段）
- `wf_task` 待办任务业务视图（带 tenant_id，支持共享池抢单）
- `wf_task_history` 审批轨迹（不可删）

冗余表由 Flowable 事件监听器（`FlowableEventListener`）在同一事务内同步写入，保证一致性。

### 5.5 MySQL 驱动坑（必须配置）

JDBC URL 必须加 **`nullCatalogMeansCurrent=true`**，否则 Flowable 建表自检会跨库扫描误判，报 `Table 'xxx.act_ge_property' doesn't exist`。
另外必须提供名为 **`applicationTaskExecutor`** 的 `ThreadPoolTaskExecutor` Bean，否则异步执行器任务堆积。

---

## 6. 租户生命周期与计费

### 6.1 状态机

```
APPLYING(待审核) ──审核通过──> NORMAL(正常) ──到期/欠费──> FROZEN(冻结)
      │                          │  ▲                        │
      │                          │  └────────续费────────────┘
   拒绝│                     申请注销│
      ▼                          ▼
  REJECTED(已拒绝)         CANCELLING(注销中) ──期满──> CANCELLED(已注销)
```

### 6.2 各状态的接口放行规则（`TenantFilter` 实现）

| 状态 | 放行范围 |
|------|----------|
| `NORMAL` | 全部 |
| `APPLYING` | 拒绝（提示审核中） |
| `FROZEN` | **只读请求** + 账单/续费/数据导出/个人资料/认证接口。**禁止一切写操作** |
| `CANCELLING` | 仅数据导出 + 认证接口 |
| `CANCELLED` / `REJECTED` | 全部拒绝 |

> 关键：这是**后端接口级**校验，不是页面提示。SaaS 常见事故就是"过期只在前端提示，后端接口照样能调"。

### 6.3 配额校验

`@RequiresQuota(QuotaType.USER_COUNT)` 切面，在新增用户/上传文件/调用 API 前校验套餐上限，超限抛 `30001 账号数超限`。

---

## 7. 审计日志（双轨制）

| 日志表 | 记录对象 | 特点 |
|--------|----------|------|
| `pf_oper_log` | 平台管理员的所有操作 | 无 `tenant_id`，无 `del_flag`，只增不删 |
| `sys_oper_log` | 租户内所有用户操作 | 有 `tenant_id`，无 `del_flag`，只增不删 |

由 `@OperLog(module="租户管理", type=OperType.UPDATE)` 注解 + AOP 自动落库，记录：操作人、时间、IP、模块、动作、请求参数、响应结果、耗时、成功/失败。

**敏感字段脱敏**：密码、身份证、银行账号在入库前掩码。

**高危事件单独告警**：跨租户越权尝试（错误码 `40303`）打 `error` 日志并预留告警通道。

---

## 8. 文件存储与租户隔离

### 8.1 落盘路径规则

```
{storageRoot}/{tenantId}/{yyyyMM}/{bizType}/{uuid}.{ext}
```

路径中带 `tenantId`，使越权在**文件系统层**也失效 —— 即使代码漏判，路径也拼不出别人的目录。

### 8.2 下载两步走（防 URL 猜测越权）

```
① GET  /api/tenant/file/{fileId}/token   → 校验 fileId 归属本租户，签发一次性令牌（5 分钟，绑定 tenantId+userId+fileId）
② GET  /api/tenant/file/download?token=  → 校验令牌，流式输出
```

**禁止**把文件真实路径或可枚举 ID 直接暴露为下载 URL。每次下载写 `sys_file_access_log`（不可删）。

---

## 9. 可配置化（禁止硬编码业务参数）

| 可配置项 | 配置载体 |
|----------|----------|
| 单据编号规则 | `sys_number_rule`（前缀+日期格式+流水位数+重置周期） |
| 业务枚举/下拉值 | `sys_dict_type` / `sys_dict_data`（租户可自定义，覆盖平台内置） |
| 系统参数 | `sys_config`（租户级）/ `pf_config`（平台级） |
| 表单字段显隐必填 | `md_form_detail_config` |
| 费用标准/借款额度 | `md_expense_standard` / `md_loan_quota` |
| 审批流程 | Flowable 流程定义 + `wf_process_config` |
| 派单规则 | `op_dispatch_rule` |
| 套餐功能开关 | `pf_package_feature` |

**平台配置与租户配置物理分表** —— 防止租户改到平台全局参数（SaaS 高频踩坑第 5 条）。

---

## 10. 前端架构与移动端

### 10.1 双工程

| 工程 | 端 | UI | 路由模式 | 面向对象 |
|------|-----|-----|----------|----------|
| `fssc-web` | PC | Element-Plus 2.9 | history | 平台运营端 + 租户端完整功能 + 共享作业台 |
| `fssc-h5` | 移动 | Vant 4.9 | **hash** | 员工填单/拍票、领导审批、待办 |

> H5 用 hash 模式：未来 Capacitor 打包成 APP 时，history 模式在 `file://` 协议下会白屏。

### 10.2 移动端设计约束（面向未来打 APP）

1. `baseURL` 来自 `VITE_API_BASE`，生产必须是**完整域名** —— APP 内无同源概念，不能依赖 Vite proxy / Nginx 同源。
2. 认证**只用 Header 不用 Cookie** —— WebView 的 Cookie 行为不可靠。
3. 原生能力抽象层 `src/native/`（camera/storage/push/file），页面永远 `import { takePhoto } from '@/native/camera'`，浏览器降级为 `<input type=file capture>`，套壳后切 Capacitor 插件 —— 业务代码零改动。
4. `postcss-px-to-viewport`（viewportWidth 375），安全区 `env(safe-area-inset-bottom)`，最小热区 44×44px。
5. **页面保持简单**：移动端只做"填单 + 拍票 + 审批 + 待办"四件事，复杂配置类页面只在 PC 端提供。

### 10.3 共享代码层

`shared/`（api / types / constants / utils/request.ts）在 web 与 h5 之间**单向同步**，由 `scripts/sync-shared.sh` 维护，避免两端接口定义漂移。

---

## 11. 关键技术决策记录（ADR）

| # | 决策 | 理由 | 代价 |
|---|------|------|------|
| ADR-1 | 共享数据库共享 Schema + `tenant_id` | 中小企业 SaaS 成本最优，运维简单，一套代码统一升级 | 代码处处要处理租户过滤，靠拦截器 + 双重自检兜底 |
| ADR-2 | 不引 Spring Security 全家桶，只用 `spring-security-crypto` 的 BCrypt，鉴权自写 Filter | Security 过滤链与平台端/租户端双路由前缀冲突严重，调试成本高 | 需自己保证过滤器覆盖完整，靠 `anon-paths` 白名单集中管理 |
| ADR-3 | 主键 `bigint AUTO_INCREMENT` 而非雪花 | 单库部署，自增索引更紧凑、范围扫描更快；ID 不外泄业务含义（对外用编号字段） | 未来分库需改造为雪花 |
| ADR-4 | JWT 双令牌 + 会话版本号 | 吊销时只需递增 Redis 版本号，无需维护 Token 黑名单 | 依赖缓存可用性，本地缓存模式下多实例部署需切 Redis |
| ADR-5 | 引入 Flowable 而非自研引擎 | 招标明确要求独立专业 BPM，Flowable 是 BPMN 2.0 事实标准，规避"内嵌简易工作流"红线 | ACT_* 表不适合业务查询，需维护业务冗余视图表 |
| ADR-6 | 统一单据主表 `exp_bill` + 明细子表，而非每种单据一张主表 | 事前申请/报销/借款/还款/对公支付字段高度重叠，统一后新增单据类型只需配置不需建表，契合"配置化"要求 | 主表字段较宽，需靠 `bill_type` 严格区分校验规则 |
| ADR-7 | 平台字典与租户字典物理分表 | 混在一张表用 `tenant_id=0` 表示公共，会破坏"所有查询强制 tenant_id 过滤"的铁律，留下隐蔽后门 | 查询时需合并两个来源，由 `DictService` 统一封装 |
| ADR-8 | 审计日志表无 `del_flag`，不提供删除接口 | 合规要求日志不可篡改 | 需定期归档控制表体积 |

---

## 12. 自动化质量守护

| 手段 | 时机 | 内容 |
|------|------|------|
| `scripts/db-check.sh` | 建库后 / CI | 表数量、`tenant_id` 覆盖、索引前缀、唯一索引前缀、审计表无 del_flag |
| `TenantSchemaValidator` | 应用启动 | 同上，扫 `information_schema`，失败**拒绝启动** |
| ArchUnit 单测 | `mvn test` | 架构红线（platform 不依赖业务 Mapper 等） |
| `scripts/check.sh` | 每次改完代码 | 后端 `mvn -q compile` + 前端 `vue-tsc --noEmit` + `eslint`，一条命令全量体检 |
| MyBatis SQL 日志 | 开发期 | `StdOutImpl` 打印 SQL，肉眼确认 `tenant_id` 条件已自动追加 |

---

## 13. 非功能设计要点

| 要求 | 落地手段 |
|------|----------|
| 万人级并发 | Tomcat 线程池 400、Druid 连接池 50、Redis 缓存权限与字典、列表页强制分页（上限 200 条） |
| 索引优化 | 所有租户表以 `tenant_id` 为索引首列；高频查询建 `(tenant_id, company_id, status)` 等组合索引 |
| 高可用 | 无状态服务（会话在 Redis），支持多实例 + 负载均衡；Flowable 异步执行器支持集群 |
| 灰度升级 | 一套代码统一升级；功能级灰度靠 `pf_package_feature` 按租户开关 |
| 防全表操作 | `BlockAttackInnerInterceptor` 阻断无 where 的 update/delete |
| 乐观锁 | `OptimisticLockerInnerInterceptor` + `@Version`，防单据并发审批冲突 |
| 敏感字段加密 | 手机号、身份证、银行账号 AES 存储 + 查询脱敏 |
| 接口防刷 | 令牌桶限流 + 登录失败 5 次锁定 15 分钟 |

---

## 14. 实施路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 架构设计、数据库设计、完整建表 SQL、自检脚本 |  本阶段 |
| P1 | 后端框架层：多租户底座、认证鉴权、审计、文件、编号、异常、分页 | 待开始 |
| P2 | 平台运营端：租户、套餐、计费、平台用户、平台日志 | 待开始 |
| P3 | 租户系统端：组织、用户、角色、菜单、字典、参数 | 待开始 |
| P4 | 主数据：公司、成本中心、项目、费用类型、供应商、客户、报销标准等 | 待开始 |
| P5 | Flowable 集成：流程配置、发起、审批、待办、SLA、共享池派单 | 待开始 |
| P6 | 报销主线：事前申请 → 费用报销 → 借款还款 → 对公支付 | 待开始 |
| P7 | 前端 PC 端 fssc-web | 待开始 |
| P8 | 前端移动端 fssc-h5 | 待开始 |

每阶段交付后运行 `scripts/check.sh` 全量体检，通过方进入下一阶段。


---

## 需求对齐

- **需求基线**：《财务共享平台-AI开发需求文档》（`docs/财务共享平台-AI开发需求文档.md`，来源 `fssc/需求/`）
- **对应章节**：第1章 整体系统功能框架（系统概述/集成关系）
- **对齐矩阵**：`docs/00-需求与设计索引.md`（11 个一级域 ↔ 设计文档 ↔ 实现状态）

> 注：本文档为落地实现参考；业务功能、字段、页面规则以需求基线为唯一验收标准。如有出入，以需求文档为准。
