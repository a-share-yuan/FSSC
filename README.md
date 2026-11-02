# FSSC · 集团财务共享服务平台

> 多租户 SaaS 财务共享中心（Financial Shared Service Center）——覆盖费用报销、应收应付、总账凭证、资产存货、资金票据、影像 OCR、共享作业运营与 BPMN 审批引擎的完整业务底座。
>
> 名称说明：这里的 **FSSC** 是"财务共享服务中心"这一行业通用缩写的字面含义，指本软件所服务的业务形态本身；本项目与任何同名或近名的商业软件、认证体系没有隶属或关联关系，也不包含其代码、界面素材或文档。

![license](https://img.shields.io/badge/license-AGPL--3.0-blue) ![java](https://img.shields.io/badge/JDK-26-orange) ![spring-boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green) ![vue](https://img.shields.io/badge/Vue-3.5-brightgreen) ![flowable](https://img.shields.io/badge/Flowable-8.0.0-yellow)

> ** 发布前校准状态**：本 README 的代码事实（域/表/端点/版本）由脚本从源码实测抽取（见 `docs/_facts/`）；`docs/快速上手.md` 中的启动步骤**尚未在一台干净机器上端到端跑通验证**。在标记为"已验证"之前，请把 Quickstart 视为高置信度草案而非承诺。

<details open>
<summary><b>English TL;DR</b>（本仓库不做整篇中英对照，理由见下）</summary>

**What this is.** A multi-tenant SaaS **financial shared service center** backend: expense claims → compliance pre-check → budget control → multi-level approval (Flowable BPMN) → shared-service work queues and scheduling → invoice verification → payment → journal vouchers → image archiving, with full cross-document drill-through and row-level data scope.

**Stack.** JDK 26 + Spring Boot 4.1 + Flowable 8 + MyBatis-Plus (245 mappers, **no Mapper XML**) on MySQL 8; Vue 3.5 + Vite 6 + Element Plus + bpmn-js 16.5 for the front end and the process designer.

**Read this first.** `docs/代码阅读指南.md` (how to orient in a large codebase), then `docs/模块地图.md`, `docs/数据模型.md`, `docs/权限与多租户.md`, `docs/流程引擎技术说明.md` §9 ("I need to change something — where do I touch?"). Every `path:line` citation in the docs is checked by `_tools/cite_check.mjs` in CI.

**Honest caveats.**
- **Internationalisation is wired up but only partly extracted.** `fssc-web/package.json` depends on `vue-i18n`, `src/i18n/index.js` builds the instance, `main.js` registers it plus a global `LocaleSwitch`, and `src/i18n/locales/{zh-CN,en-US}.js` carry ≈4,010 keys each. Measured on this snapshot: 72 of 263 `.vue`/`.js` files under `src/` call `$t()`/`useI18n()` (4,038 call sites) — the rest are still hard-coded Chinese literals, so switching to English leaves parts of the UI untranslated.
- **Source-only release.** The front-end package keeps `"private": true`; there is no npm package and no hosted demo. You build the jar/image yourself.
- **Database bootstrap = one full database dump.** `db/Dump<导出日期>/` (323 files, one table per file, ≈6.8 MB) is the supported way to create the databases; import it as `db/README.md` describes. The accumulated per-version migration scripts under `fssc-server/sql/` are **not** published — there is no Flyway/Liquibase, so nobody outside the project can order them anyway. Note that the dump is an export of a real drill database: it carries business rows (bills, vouchers, approval history) and 2.3 MB of uploaded attachment bytes in `act_ge_bytearray`.
- The declared JDK is pinned to **26**, a non-LTS release whose end of availability has already passed; the maintainers have adjudicated a move to the current LTS line but have not run that build yet — see `docs/开源发布前置检查清单.md` §8 ⑥.
- This code has been hardened through long manual business drills; treat documented behaviour as *documented*, not *guaranteed*. Each doc states which claims were measured and which were only read off the code.

</details>

---

## 30 秒看懂：这是什么

假设你是一家有 20 家分子公司、每天 3,000 张报销单的企业集团。员工在手机上提单，单据要经过**预算控制 → 合规预检 → 多级审批 → 共享中心作业排班 → 发票查验 → 出纳付款 → 自动生成凭证 → 影像归档**，还要保证 A 公司的人看不见 B 公司的单据。

FSSC 就是为这条完整链路准备的系统：

```mermaid
flowchart LR
  U[员工 / 领导<br/>提单·审批] --> W[前端 Vue3<br/>:8080]
  O[共享作业员<br/>审核·派工] --> W
  W -->|/api 反代| S[后端 Spring Boot<br/>:8089 · 33 个业务域]
  S --> DB[(MySQL 主库 fssc<br/>243+ 表)]
  S --> IDB[(影像分库 fssc_image)]
  S --> F[Flowable 8<br/>BPMN 流程引擎]
  S -.可选.-> G[发票/OCR 网关 :8096]
  S -.可选.-> R[(Redis<br/>多实例分布式状态)]
  S -.可选.-> B[银企直联网关]
```

**为什么值得看**：市面上开源 OA 很多、ERP 很多，但把「财务共享中心的作业运营 + 单据全链路联查 + 可配置审批引擎 + 多租户 SaaS 底座」四件事一起做成的开源实现极少。这个仓库可以直接读到这套业务的表设计、状态机和引擎接入方式。

## 能力地图（真实数字，来自源码扫描）

| | 数量 | 说明 |
|---|---:|---|
| 后端 Java 文件 | 1,007 | `fssc-server/fssc-start/src/main/java` |
| 后端代码行 | 131,428 | 不含测试与前端 |
| REST 控制器 | 160 | `@RestController` |
| HTTP 端点 | 1,257 | `@*Mapping` 计数 |
| 业务域包 | 33 | `com.fssc.<domain>` |
| 实体映射表 | 243 | `@TableName` 计数；库内实际表数更多 |
| 前端页面组件 | 134 | `fssc-web/src/views/**/*.vue` |
| 流程模型（BPMN） | 见 `bpmn-repo/publish` | 审批流模板库 |

按域看规模（代码行排序，前 12）：

`workflow` 31.9k · `sharing` 15.1k · `compliance` 8.1k · `expense` 8.0k · `system` 6.4k · `glex` 5.7k · `arflow` 5.7k · `framework` 5.6k · `masterdata` 5.6k · `image` 3.8k · `asset` 3.6k · `document` 3.6k

 逐域职责、API 前缀与对应表 → **[`docs/模块地图.md`](docs/模块地图.md)**

### 功能域一览

| 分类 | 域 | 一句话 |
|---|---|---|
| 底座 | `framework` `platform` `auth` `system` | 多租户隔离、JWT 鉴权、平台开租户、用户/角色/菜单/参数/编号规则 |
| 主线 | `expense` `document` `invoice` `image` `compliance` `budget` | 报销提单 → 合规预检 → 预算占用 → 发票查验 → 影像调阅 |
| 审批 | `workflow` `collab` | Flowable BPMN + 自研 `wf_*` 台账：会签/票签/加签/转办/委托/超时升级 |
| 财务 | `glex` `gl` `arflow` `apflow` `receivable` `treasury` `payplan` `bank` `purchase` `asset` | 凭证自动生成、应收应付、资金计划、批量付款、银企直联、采购、资产折旧与存货 |
| 运营 | `sharing` `report` `audit` `tracing` `match` | 共享作业池/派工/SLA/绩效、报表看板、操作留痕、单据全链路联查、三单匹配 |
| 扩展 | `ai` `contract` `customer` `returns` | AI 动作网关、合同、客户、退单 |

## 快速开始

### 前置（版本必须对齐）

| 依赖 | 版本 | 校验命令 |
|---|---|---|
| JDK | **26.0.1**（`maven.compiler.release=26`） | `java -version` |
| Maven | 3.9.x | `mvn -v` |
| MySQL | 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci` | `mysql --version` |
| Node | **24.x**（Vite 6） | `node -v` |
| Redis | 可选，单机部署不需要 | — |

> 后端依赖 MySQL 8 的窗口函数与 `utf8mb4`，且 MyBatis-Plus 走 JSqlParser 做租户条件注入。用 MariaDB 或 5.7 未经验证。

### 三步跑起来

```bash
# ① 建库：导入整库 dump（结构 + 演示数据，建库入口只有这一条）
for f in db/Dump*/fssc_*.sql; do mysql --default-character-set=utf8mb4 -uroot -p < "$f"; done
# 每个文件自带 CREATE DATABASE + USE，主库 fssc 与影像库 fssc_image 一起建好；
# 都是 DROP TABLE IF EXISTS + CREATE + INSERT，可重复执行，文件顺序不影响成败（各自关掉外键检查）
# Flowable 的 ACT_* / FLW_* 表已在 dump 内，首启时 database-schema-update=true 会补齐缺项

# ② 配环境
cp .env.example .env      # 必填：FSSC_DB_PASSWORD、FSSC_JWT_SECRET

# ③ 起后端 + 前端
cd fssc-server && mvn -q clean package -DskipTests
java -jar fssc-start/target/fssc-server.jar            # → http://localhost:8089

cd ../fssc-web && npm install && npm run dev           # → http://localhost:8080
```

浏览器打开 `http://localhost:8080`。dump 里带着演练期间的账号、组织、菜单、参数与单据数据，导入后界面就有内容可点；但**口令值不在本仓库里**——[`docs/演示账号.md`](docs/演示账号.md) 目前仍是"待补、刻意不列口令"的角色说明，所以登录要按你自己的口令策略重置目标账号（本系统的审批引擎不允许同人自审，演示一条完整链路至少需要提单人／部门审批人／财务初审·复核／出纳这几类身份）。审批链路与状态语义以 `docs/` 各册为准，不要用截图或印象推断。

 端口、双库路由、OCR/银企 mock 通道、启动失败排查 → **[`docs/快速上手.md`](docs/快速上手.md)**

## 技术栈

**后端**：Spring Boot 4.1.0 · Spring MVC + 自研 `JwtAuthFilter` · MyBatis-Plus 3.5.15（含 JSqlParser 租户拦截）· Flowable 8.0.0（BPMN）· jjwt 0.12.6 · Lombok 1.18.46 · commons-lang3 3.17.0 · springdoc-openapi 3.1.0（`/swagger-ui.html`）· Actuator + Micrometer/Prometheus · 双数据源 HikariCP · 可选 Redis（`framework/spi/DistributedState` 可插拔）

**前端**：Vue 3.5 + Vite 6 · Element Plus 2.9 · Pinia 2 · vue-router 4 · bpmn-js 16 + properties-panel（在线流程设计器）· ECharts 5 · vitest 3 + jsdom

**运维**：`docker/Dockerfile` · `docker/k8s-fssc.yaml` · 优雅停机（`server.shutdown=graceful`，30s 窗口）· 免鉴权探针 `/actuator/health/{liveness,readiness}`

## 界面

<!-- TODO(release-blocker): 缺图的 README 转化率极低；下面五个图位建好后把 ![]() 补上 -->
本节计划放 5 张界面截图，图位与文件名已定（目录尚未建立，**现在这里没有图**）：
`docs/images/01-dashboard.png`（工作台）、`02-expense-form.png`（报销提单）、`03-bpmn-designer.png`（流程设计器）、`04-work-pool.png`（共享作业池）、`05-trace-chain.png`（单据全链路联查）。

截图须取自演示数据环境，且**不得**包含真实姓名、工号、手机号、身份证号、公司实名。

## 文档索引

| 想做什么 | 读哪份 |
|---|---|
| 5 分钟跑起来、排查启动失败 | [`docs/快速上手.md`](docs/快速上手.md) |
| 找一个功能在哪改 | [`docs/模块地图.md`](docs/模块地图.md) |
| 理解分层与请求生命周期 | [`docs/架构总览.md`](docs/架构总览.md) |
| **第一次接手这个库、不知从哪读起** | [`docs/代码阅读指南.md`](docs/代码阅读指南.md) |
| 查表 / 写 SQL / 加字段 | [`docs/数据模型.md`](docs/数据模型.md) |
| 看不懂业务黑话（会签、核销、联查…） | [`docs/术语表.md`](docs/术语表.md) |
| 搞清审批引擎能配什么、不能配什么 | [`docs/流程引擎技术说明.md`](docs/流程引擎技术说明.md) |
| **第一次动手改代码，不知道要动哪几处**（加查询条件 / 加字段 / 加权限 / 改节点 / 改状态文案） | [`docs/流程引擎技术说明.md`](docs/流程引擎技术说明.md) §9，末尾 §10 是本文结论的可靠性清单 |
| 权限怎么加、为什么 403 显示"暂无数据" | [`docs/权限与多租户.md`](docs/权限与多租户.md) |
| 业务故事：这张单据是谁提的、谁审的、钱怎么出去 | [`docs/business-flows/`](docs/business-flows/) |
| 部署到生产 | [`docs/部署手册.md`](docs/部署手册.md) · [`docs/部署运维.md`](docs/部署运维.md) |
| 想知道这系统解决什么问题、给谁用 | [`docs/系统介绍.md`](docs/系统介绍.md) · [`docs/系统特点.md`](docs/系统特点.md) |
| 演示环境该登哪个账号、要准备哪几类身份 | [`docs/演示账号.md`](docs/演示账号.md) |
| 发布／上线前逐项核对 | [`docs/开源发布前置检查清单.md`](docs/开源发布前置检查清单.md) |
| 全部文档一览 | [`docs/README.md`](docs/README.md)（每篇散文文档另有同名 `.docx`） |
| 提 PR 前必读 | [`CONTRIBUTING.md`](CONTRIBUTING.md) · [`SECURITY.md`](SECURITY.md) |

## 项目是怎么来的（以及这意味着什么）

本系统按财务共享业务域分层建设，历经长期真实业务演练与缺陷治理：真跑覆盖 40+ 业务流程、数百条缺陷台账、以及逐能力的功能核对记录（这些核对材料本身属内部分析，**不随本仓库发布**）。工程侧价值在于**业务域完整性与状态机细节**，而非代码优雅度。

由此产生两条你需要知道的事：

1. **文档里的"配置项"不保证生效。** 大型代码库常见"配置读进来了但没人用"。我们在 `docs/流程引擎技术说明.md` 等处标注了**已验证生效 / 仅登记未接线**的区分，改动前请先看该文档。
2. **判断"哪份代码是活的"要用构建产物，别看文件名。** 仓库历史上存在同名多版本文件（不同 `dist*`、`*_v3.md` 与 `*_v3.0_*`）。发布版已剔除产物，源码树单一。

## 成熟度声明

| 状态 | 内容 |
|---|---|
|  已实现且真实运行验证 | 报销主线、多级审批与会签、凭证生成、多租户与权限、影像与 OCR 通道框架、共享作业池、单据联查 |
|  已实现，功能验证有限 | 资产折旧与存货、资金计划与银企（mock）、预算控制细则、合规规则引擎多数规则、AI 动作网关 |
|  需要自行扩展才能生产 | 真实 OCR/发票查验通道接入、银企直联真实实现、对象存储、SSO/LDAP、短信渠道 |
|  明确不具备 | 生产级高可用验证（未经大规模压测）、财务报表准则级审计背书、界面文案的全量英文抽取（i18n 已接入，但实测仅 72/263 个源件走 `$t()`，其余仍是中文字面量） |

本项目**不适合**直接当生产财务系统上线，适合作为二次开发底座、教学参考或同类系统能力研究。

## 参与

- 请先读 [`CONTRIBUTING.md`](CONTRIBUTING.md)——尤其是「改代码前先跑一次」和「涉及查询层必须验证跨租户」两条。
- 想上手？看 `good first issue` 标签。
- 想报漏洞？看 [`SECURITY.md`](SECURITY.md)，**不要**开公开 Issue。

## 许可

本项目采用**双许可**模式：

- **开源许可（默认）**：GNU Affero General Public License v3.0（AGPL-3.0），见 [`LICENSE`](LICENSE)。AGPL-3.0 是强 copyleft 许可且含网络条款——若你修改本程序并作为网络服务向他人提供，必须按 AGPL-3.0 开源你的修改。
- **商业许可**：如你希望在不遵守 AGPL-3.0 网络 / copyleft 条款的前提下将本程序用于闭源或商业场景，可另行取得商业许可，见 [`LICENSE-COMMERCIAL.md`](LICENSE-COMMERCIAL.md)（条款待补，采购联系见该文件）。

分发时 `NOTICE` 须一并保留。
选型说明：AGPL-3.0 的专利授权与网络条款对含自研流程引擎接入的项目是必要的；如需更宽松的闭源路径，请通过商业许可取得授权，而非改用其它开源许可。

## 免责声明

开源通道（AGPL-3.0）下本仓库不提供任何形式的商业支持或 SLA；商业支持与 SLA 以 [`LICENSE-COMMERCIAL.md`](LICENSE-COMMERCIAL.md) 约定的商业许可为准。财务与税务处理请以你所在司法辖区的法规与准则为准。
