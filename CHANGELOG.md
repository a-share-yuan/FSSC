# 更新日志（Changelog）

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 开源发布骨架：`LICENSE`（立项时为 Apache-2.0，2026-11-01 改判 AGPL-3.0-or-later）、`NOTICE`、`README.md`、`CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md`
- 文档体系：`docs/快速上手.md`、`docs/架构总览.md`、`docs/模块地图.md`、`docs/数据模型.md`、`docs/权限与多租户.md`、`docs/流程引擎技术说明.md`、`docs/术语表.md`、`docs/部署手册.md`
- `docs/代码阅读指南.md`：读懂一个不认识的模块的通用手法（从路由表倒推、从实体正推、从异常落点反推），配套 `docs/adr/` 与 `docs/business-flows/` 两个目录
- `docs/流程引擎技术说明.md` 补齐三章：**§8 流程设计器（前端 bpmn-js）**——设计器在哪个页签、画布内容存进哪一列、一次编辑经过哪几跳 HTTP、界面标签 → 数据库列 → 后端读取处的对照表、前端校验与后端那道闸的分歧清单；**§9 新手改动指引**——加查询条件 / 加字段 / 加权限 / 改审批节点 / 让状态码显示中文，每样都给了动哪几处、怎么验证、有什么坑；**§10 最需要人工复核的结论**——本文哪些结论是跑出来的、哪些只是读码推断，逐条标明复核办法
- `docs/_facts/`：从源码自动抽取的事实清单（域度量、243 张表分组、API 前缀），由 `_tools/inventory.mjs` 生成
- `.gitignore`（覆盖构建产物、日志、数据库备份、本地凭据）与 `.env.example`
- GitHub CI（后端 Maven 构建 + 前端 Vite 构建与 vitest）、Issue/PR 模板

### Changed
- 待补：`vite.config.js` 的 `build.outDir` 从历史遗留的 `dist36` 改回 `dist` 并启用 `emptyOutDir`
- 待补：`fssc-web/package.json` 的 `"private": true` 需要重新决策（发布为 npm 包还是仅源码）
- 文档更正（三条按行业惯例写出来、实测发现本项目并没有的机制）：
  - `CONTRIBUTING.md` 原文教人"把 SQL 写进 Mapper XML"——全仓 `*Mapper.xml` **零命中**，245 个 `*Mapper.java` 里只有 19 个用注解 SQL，其余是空接口＋MyBatis-Plus 条件构造器；已改成按真实形态写
  - `README.md`、`CONTRIBUTING.md`、`docs/模块地图.md`、内部发布检查清单 原文把项目说成"有 i18n 骨架"——实际**没有接国际化框架**（无 `vue-i18n` 依赖、无 `createI18n`、无 `$t(` 调用，`src/i18n/` 只有一个 bpmn-js 画布的中文补丁），界面文案就是中文字面量；已改为明示"没有"并指出状态码中文显示登记在 `fssc-web/src/constants/billStatus.js`
  - `CONTRIBUTING.md` 原文提到迁移工具——项目**没有 Flyway／Liquibase**，迁移是 `V*_seed.sql` ＋ `V*_rollback.sql` 成对脚本＋台账表 `sys_schema_version`，DDL 只向前、回滚靠还原迁移前备份；已改为成对提交的要求并指向 `docs/流程引擎技术说明.md` 9.2
- `README.md` 文档索引新增一行：第一次动手改代码、不知道该动哪几处的人直接指向 `docs/流程引擎技术说明.md` §9；`CONTRIBUTING.md` 的"必读三页"改为"必读四页"并把同一章加进去
- 内部发布检查清单 §4c 的记录基线与整改记录同步刷新（引用数随文档增补变化，引用前须重跑 `_tools/cite_check.mjs`，不要抄册子里的数字）
- 内部发布检查清单 §8 的六项待决**全部代判**，每项给两条锚（外部原文带链接＋本仓实测 `路径:行号` 或读数），签字栏一律留空：① 许可证当时判为维持 Apache-2.0（**2026-11-01 改判 AGPL-3.0-or-later**，全树逐文件头与骨架文档同步改口）；② 发布形态＝仅源码、`private: true` 不动（npm 上 `fssc`/`fssc-web` 实测均 404，名字空闲但没有可发布的库层）；③ README 不做整篇英译，只加英文 TL;DR（项目没有国际化框架）；④ 命名保留 `fssc` 技术前缀，只补一句"取行业通用缩写、与同名产品无关"的说明（商标可注册性仍待法务）；⑤ 保留 MySQL Connector/J 9.7.0 不换 MariaDB 驱动（MySQL 发行方公布的 FOSS Exception 许可清单里明确列有 Apache-2.0）；⑥ 发布基线由 JDK 26 降到 25 LTS——26 是非 LTS 且 End of Availability 为 2026-09（今天 2026-10-07 已过），Boot 4.1 支持区间 17–26 覆盖 25。同时给阻断表新增第 13 行（1,049 个待发布的 `.java` 里带 Apache 许可头的 **0** 个）与第 14 行（JDK 基线待降，须走 CI＋构建窗）

### Fixed
- 已由并发施工会话修复并于 2026-10-07 07:40 实测复核：`fssc/docker/Dockerfile:8` 的 `COPY` 目标原先写作 `fssc-start-1.0.0.jar`，与 `fssc/fssc-server/fssc-start/pom.xml:178` 的 `<finalName>fssc-server</finalName>` 不一致 → 当时 `docker build` 必然失败；现名字已对上，同批新增此前缺失的 `fssc/.dockerignore`（构建上下文里单 `target/` 实测 4.5 G）。 **仍未实跑 `docker build`**（本机 `docker: command not found`），故 `docs/部署手册.md` 第 2 节的容器行为仍属"读码＋读文件"级结论
- 文档更正：上述缺陷在 `docs/架构总览.md`、`docs/部署手册.md`、内部发布检查清单 三处仍被写成"未修的发布阻断项"，已按现状改为"已修·未经运行验证"；`docs/部署手册.md` 第 2 节那段构建命令另有实机错（`cd fssc-server` 后 `-f docker/Dockerfile` 无处可寻，实测 `docker/` 与 `.dockerignore` 在 `fssc/` 这一层、`fssc/fssc-server/docker` 不存在），已拆成两条并标明各自的工作目录
- 发布册**作用域清洗**（10-07 08:2x）：清掉六处不属于本项目的内容——`_tools/scan_all.txt`（整台开发机工作区的扫描输出，会点名同级其他工程与个人目录）已移出发布目录；内部发布检查清单 §4b 那行"全工作区"统计换成只算本项目三棵树的四行表＋一段扫描面口径硬约定；§3 分拣表不再引内部施工文档的目录编号；`CONTRIBUTING.md` 测试块里那条 `cd` 进本项目之外那个施工目录的命令删除；两处绝对机器路径（本清单 §8 ⑥ 的 `JAVA_HOME` 句、`docs/_facts/facts.json` 的 `srcRoot`）改为相对表述；`_tools/secret_scan.mjs` 的跳过名单与 `.gitignore` 里几枚本项目之外的目录名（本项目树内实测 0 枚）删除。 顺带修掉一个真实缺陷：`_tools/inventory.mjs` 与 `_tools/emit_facts.mjs` 原先把绝对机器路径写死在源码里，**换一台机器就跑不动**，现在一律以 cwd 为根，并加了"源码根找不到就报错退出"的兜底（免得静默产出一份全 0 的事实清单）
- `_tools/cite_check.mjs` 新增第五条判据**「作用域外引用」并计入阻断**（`EXIT=1`，已用自测件验过会 FAIL）：此前脚本只判"文件在不在"，而开发机上工作区根有一个与本仓库 `docs/` **同名**的目录：写在该目录里那篇文档的一行，会被判"通过"——对外是死链、对内是串项目，属于双重假绿。`CONTRIBUTING.md` 的 PR 清单同步加了一条作用域自查
- 作用域清洗**第二轮**（10-07 08:3x，用 Node 复扫全发布目录 38 个文本文件后追出三处）：`docs/模块地图.md`「域外子项目」表里有一行写着 `fssc/` 下的一个端到端测试目录，实测**该目录不存在**（本项目里没有这一层，那套脚本在本项目之外），却标着"保留，需在 CI 接通"⇒ 删行并改成一句"端到端 UI 脚本不在本项目三棵树内"；`.gitignore` 里剩下的一枚本项目之外的目录名删除，§4 第 3 条的枚举同步更新；§2 白名单补上 `_tools/`（`.github/workflows/ci.yml` 有三步直接 `node _tools/*.mjs`，不列进去首个 PR 的 Actions 就找不到脚本）。同时给 `_tools/cite_check.mjs` 加**第六条判据「绝对机器路径」**（盘符开头或 UNC 写法，计入 `EXIT=1`，自测件实测会 FAIL；报错只给行列**不回显内容**）——起因是清洗记录自己把被清掉的路径原文抄了回来，那一行成了全库唯一一处绝对路径，而两道闸当时**全绿**：`_tools/` 里的 md 不在 CI 核对面内，这条要靠判据本身补。⇒ 本轮末态：**742 条引用全通过、0 作用域外、0 绝对机器路径**，docs-only 扫描 0 命中，20 份文档 / 3,617 行（08:36:20 实测）
- 同批连带发现：`fssc/scripts/start-server.bat:8`、`fssc/scripts/start-server.sh:7`、`fssc/scripts/_probe_state.py:38` 仍引用旧 jar 名 `fssc-start-1.0.0.jar`（`scripts/` 不在发布白名单内 ⇒ 不阻断发布，属内部脚本残留；现役启动器 `fssc/start-server.bat:7`、`fssc/运行.bat:33` 名字已正确）。口径教训记在 内部发布检查清单 §4c 整改记录③：**文档里的" 未修／构建必失败"是关于代码的断言，代码一变它就过期，同源抄在几处就同时过期几处** ⇒ 转引前先重测被引那一行

### Removed
- 待移除（发布阻断项）：205 个历史增量 SQL 归档、多代 `dist*` 构建产物、`db/Dump*` 与 `db/backup/*_full_*.sql` 数据库导出、1,149 个临时探查脚本、各级 `logs/` 与 `target/`、`.run/`、`backup/`、`releases/`

## [1.0.0] - 未发布

首个公开版本。发布前尚需逐项确认（下列各项未全部完成）：

- [ ] 建库入口＝整库 dump `db/Dump<导出日期>/`（323 枚一表一文件，含演示数据）已入仓；`fssc-server/sql/` 那批累积迁移脚本不在本仓库内。**"干净机器按 dump 导入 → 起服 → 走通一张单"尚未在空环境真机验证**
- [ ] 一台干净机器照 `docs/快速上手.md` 30 分钟内跑通并提出一张单
- [ ] 默认凭据卫生完成（口令类环境变量无弱默认值）
- [ ] 脱敏扫描通过（无生产数据、无密钥、无真实姓名工号）
- [ ] README 截图补齐（取自演示环境）
- [ ] CI 首次绿

[Unreleased]: https://github.com/<owner>/<repo>/commits/main
