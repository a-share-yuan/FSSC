# 系统架构（architecture）

> 本文回答「请求是怎么进来的、经过哪几层、最后落到哪个库」。要找「某个功能改哪」，去看 [`module-map.md`](module-map.md)；要查表/写 SQL，去看 [`data-model.md`](data-model.md)。

## 1. 分层

```
浏览器 (Vue3 SPA, :8080)
   │  Vite dev 代理 /api → :8089
   ▼
JwtAuthFilter（自研，Spring MVC 拦截器，无独立网关进程）
   │  解析 JWT → 写入租户上下文 TenantContext + 当前用户
   ▼
多租户拦截器（JSqlParser 改写 SQL，注入 tenant_id 条件）
   │
   ▼
@RequiresPerm / @RequiresFeature 鉴权（perms ∩ 套餐 features）
   │
   ▼
Controller → Service → Mapper（MyBatis-Plus，拦截器保证租户条件）
   │
   ├─ 普通业务表 → 主库 fssc
   └─ 影像/发票识别表（带 @FsscDataSource("image")）→ 影像库 fssc_image
   ▼
统一返回 {code, msg, data}（分页键固定为 list）
流程类操作会经 Flowable 引擎（BPMN 模型在 bpmn-repo/）
```

- **接入层**：`fssc-web`（Vue3 + Vite，`dist36` 为产物目录）。生产可前置 Nginx。
- **应用层**：Spring Boot 4.1，33 个业务域，包结构 `com/fssc/<域>/{controller,service,mapper,entity,dto,vo}`。
- **持久层**：MyBatis-Plus 3.5.15 + Flowable 8.0.0（BPMN）；**双数据源**——主库 `fssc` 与影像库 `fssc_image`（靠 `@FsscDataSource("image")` 路由）。
- **调度**：单机用 MySQL `GET_LOCK`；多实例可插拔 Redis（`framework/spi/DistributedState`）。

## 2. 请求生命周期（逐跳）

1. 浏览器请求经 Vite 代理打到 `:8089`；
2. `JwtAuthFilter` 解析 JWT，写入租户上下文与当前用户（**URL 第二段 `/api/tenant|platform` 决定鉴权域**）；
3. 多租户拦截器用 JSqlParser 改写 SQL，自动追加 `tenant_id` 条件；
4. `@RequiresPerm`（多码为 OR）/ `@RequiresFeature` 做功能鉴权；
5. Controller → Service → Mapper，MP 拦截器保证租户条件不被绕过；
6. 标注 `@FsscDataSource("image")` 的 Mapper 才打到 `fssc_image`，否则全走主库；
7. 统一 `{code, msg, data}` 返回，分页数据键是 **`list`**（不是 `records`/`items`）；
8. 流程类接口（提交/审批/加签/驳回）最终落到 Flowable 引擎，BPMN 定义来自 `bpmn-repo/`。

## 3. 多租户隔离（重点，易出跨租户泄漏）

- 模型：**shared-DB shared-Schema**，`tenant_id` 由拦截器自动重写，**不要**在业务 SQL 里手拼租户条件（绕过 MP 拦截器的写法是跨租户泄漏）。
- 验证铁律：任何查询层改动（`@Select`/`@Insert` 注解 SQL、直连 `JdbcTemplate`）都必须自证租户条件生效。本项目**没有 Mapper XML**，SQL 只有注解与 Java 两种形态。
- 详见 [`permissions.md`](permissions.md) 与 [`data-model.md`](data-model.md) 的列名坑清单。

## 4. 双库路由

- 主库 `fssc`：业务表（316 张）+ 视图。
- 影像库 `fssc_image`：影像/发票识别相关 6 张表。
- 路由判据：**`@FsscDataSource("image")` 注解**。漏注会打到主库报「表不存在」。

## 5. 构建产物

- 后端：`fssc/fssc-start/target/fssc-server.jar`（Spring Boot repackage 胖包）。
- 前端：`fssc/fssc-web/dist36/`（Vite 产物目录是 `dist36`，不是 `dist`）。
- 本仓库**只发源码**：`package.json` 保持 `"private": true`，不发布 npm 包、不提供托管 demo。

## 6. 为什么配置项「读进来不一定生效」

本项目历史上出现过「配置键被读取但无分支真正使用」的伪配置。涉及配置项的改动，先搜该键的读取点、顺藤摸瓜确认是否有分支消费它；相关结论标注在 [`workflow-engine.md`](workflow-engine.md) 的能力矩阵里。
