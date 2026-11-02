# 设计决策记录（ADR）

> **本目录当前是空的骨架，需要补写。** 这是大型项目最缺、对外部读者最值钱的一类文档：代码能告诉你"做了什么"，只有 ADR 能告诉你"为什么不是另一种做法"。

## 格式

一个决策一个文件，命名 `NNN-短标题.md`：

```markdown
# NNN. 标题

日期：YYYY-MM-DD     状态：提议 | 已采纳 | 已取代(→NNN) | 已废弃
涉及域：workflow / framework / ...

## 背景与约束
当时面对什么问题、有哪些硬性限制（性能、合规、存量数据、引擎能力边界）。

## 决策
选了哪条路。一句话说清。

## 放弃的选项与代价
每个被否决的方案：为什么否决、如果将来要走这条路会付什么代价。

## 影响
需要改哪些地方、运维须知（尤其"是否需要重启"）、已知遗留风险。
```

## 优先待写的 14 条（从代码与配置反推的决策点）

| # | 标题 | 你在代码里能看到的证据 |
|---|---|---|
| 001 | 多租户采用共库 + `tenant_id` 拦截器，而非独立 schema | `framework/tenant/`、MyBatis-Plus 拦截器；`application-dev.yml` 注释直述该选择 |
| 002 | 鉴权用自研 `JwtAuthFilter` 而非 Spring Security 过滤链 | `framework/security/` 19 文件；配置里 `JwtAuthFilter` 白名单说明；Actuator 因自研鉴权而"无法感知已登录" |
| 003 | 权限清单放进 JWT，导致大 token 与 64KB 请求头上限 | `server.max-http-request-header-size: 64KB` 及其注释（300+ 权限 → ~8.7KB token） |
| 004 | 影像域独立分库 `fssc_image`；档案/发票/只读副本三枚路由键就位待开 | 多路由键数据源 + `@FsscDataSource` + AOP 路由，见 `docs/分库路由与服务拆分.md` |
| 005 | Flowable 引擎表与自研 `wf_*` 业务台账并存 | `wf_model/wf_instance/wf_task/wf_task_history` vs `ACT_RE/RU/HI`；历史保留 6 个月只清 `ACT_HI_` |
| 006 | 流程超时从轮询作业改为 BPMN 边界定时器 + 异步执行器 | `flowable.async-executor-activate: true` 及其长篇理由注释（旧 60s 轮询与执行器无关，掩盖了开关必要性） |
| 007 | BPMN 仓库化：模型 XML 落 git 目录而非只存库 | `fssc.wf.bpmn-repo-dir` 配置注释（获得 review/回溯能力、发布自动补写快照） |
| 008 | 分布式状态做成可插拔 SPI，默认进程内 + MySQL `GET_LOCK` | `framework/spi/DistributedState`、`fssc.redis.enabled=false` 默认值与注释 |
| 009 | 优雅停机 + 就绪探针，滚动发布不切在途请求 | `server.shutdown=graceful`、`timeout-per-shutdown-phase: 30s`、k8s `maxUnavailable: 0` |
| 010 | OCR 通道可插拔（gateway/mock/http/paddle），接真引擎免改码免重启 | `image/channel/` 13 文件 + `fssc.ocr.channel` 配置注释 + `sys_config` 的 `ocr.api.url/token/mapping` |
| 011 | 附件按 temp/business/archive 三区分区，根目录可外置 | `fssc.file.zones` 配置与注释 |
| 012 | AI 动作网关复用人侧接口，加"目录+红线+权限+一次性票据"四重闸门 | `fssc.ai.gateway.*` 配置、`/api/ai/gateway`、`confirm-ttl-minutes: 10` |
| 013 | 合规规则、派工规则、审批规则表驱动，而非硬编码 | `md_compliance_rule`、`op_dispatch_rule/policy`、`ar/ap_dispatch_rule`、`wf_*_config` |
| 014 | 会计档案与影像留存策略：`ACT_HI_*` 定期清理、业务台账不清 | `fssc.wf.history-retention-months: 6` + 周日 03:00 归档任务 |

## 谁来写

维护者每条 20 分钟即可写完（证据已经在代码注释里，只是要补"放弃了什么"）。**在补齐之前，`docs/流程引擎技术说明.md` 与 `docs/权限与多租户.md` 里的行为描述就是读者唯一的依据** —— 这也是为什么那两份文档要求逐条给 `file:line` 证据。
