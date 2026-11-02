# 更新日志（Changelog）

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 文档体系：`docs/快速上手.md`、`docs/架构总览.md`、`docs/模块地图.md`、`docs/数据模型.md`、`docs/权限与多租户.md`、`docs/流程引擎技术说明.md`、`docs/术语表.md`、`docs/代码阅读指南.md`、`docs/部署手册.md`、`docs/部署运维.md`
- `docs/流程引擎技术说明.md` 两章：流程设计器（前端 bpmn-js）与新手改动指引（加查询条件 / 加字段 / 加权限 / 改审批节点 / 让状态码显示中文，各带动哪几处与怎么验证）
- `docs/_facts/`：从源码抽取的事实清单（域度量、表分组、API 前缀），由 `_tools/inventory.mjs` 生成、`_tools/emit_facts.mjs` 落盘
- CI：后端 Maven 构建＋前端 Vite 构建与单元测试，外加两道文档闸（`_tools/secret_scan.mjs --docs-only`、`_tools/cite_check.mjs`）
- Issue / PR 模板与 `SECURITY.md` 私密漏洞报告通道

### Changed
- 许可证：全仓改为 AGPL-3.0-or-later，逐文件头同步；`LICENSE-COMMERCIAL.md` 为商业条款占位文件，条款未生效
- 建库入口统一为 `db/Dump<日期>/` 的整库导出（323 枚、一表一文件，结构与演示数据一起建）
- `vite.config.js` 的 `build.outDir` 改回 `dist` 并启用 `emptyOutDir`
- 文档口径与代码对齐：Mapper 的 SQL 实际写法、国际化现状、变更 SQL 与回滚 SQL 成对提交的要求

### Fixed
- `docker/Dockerfile` 的 `COPY` 目标与 `fssc-server/pom.xml` 的 `<finalName>` 对齐，并补齐 `.dockerignore`
- `docs/部署手册.md` 第 2 节的构建命令按各自的工作目录拆开书写
- `_tools/inventory.mjs`、`_tools/emit_facts.mjs` 以当前工作目录为根，不再依赖固定的机器路径

### Removed
- 构建产物、日志、数据库备份与临时脚本由 `.gitignore` 排除，不进入发布面

## [1.0.0] - 2026-11-02

首个公开版本：多租户财务共享服务中心的完整源码。

- **后端** Spring Boot 4.1 ＋ MyBatis-Plus ＋ Flowable 8，1,123 个 Java 文件、33 个业务域、173 个控制器；多数据源（业务库、影像库）与 JWT ＋ RBAC ＋ 五级数据范围
- **流程** 在线 BPMN 设计器、会签/加签/转办/驳回/撤回/作废、委托与超时催办、流程绩效看板
- **业务域** 费用报销、采购到付款、销售与开票、应收应付与对账、总账与凭证、资产、预算、合规与风控、资金、税务、影像与会计档案、共享作业运营
- **前端** Vue 3.5 ＋ Vite，PC 端 141 个 `.vue` 页面另有 H5 移动端，界面文案走 `vue-i18n`（中英两套语言包）
- **数据** 259 张业务表，建库入口为 `db/Dump<日期>/` 整库导出（含演示数据）
- **部署** 单 jar ＋ 静态站点、`docker/` 内的镜像构建与 Kubernetes 清单
- **许可** AGPL-3.0-or-later

[Unreleased]: https://gitee.com/a-share-yuan/FSSC/commits/main
