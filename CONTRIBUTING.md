# 贡献指南

谢谢你想改这个项目。它体量不小（**1,007 个 Java 文件 / 131,428 行 / 33 个业务域 / 1,257 个接口 / 243 张映射表**），请务必先读完本页再动手，能省下你两天时间。

## 10 分钟准备

```bash
cp .env.example .env                 # 填 FSSC_DB_PASSWORD / FSSC_JWT_SECRET
mysql -uroot -p < db/schema-baseline.sql   # 自带 CREATE DATABASE + USE，主库与影像库一起建；只建结构，无数据行
cd fssc-server && mvn clean package -DskipTests && java -jar fssc-start/target/fssc-server.jar
cd ../fssc-web  && npm install && npm run dev
```

**动手前先跑起来，并且走通一张报销单。** 这个项目大量行为只有运行期才看得出来。

**发布形态（已定，别开新路子）**：本仓库**只发源码**。前端包保持 `"private": true`（`fssc-web/package.json`），不发 npm 包、不提供托管 demo；jar 与镜像由使用者自己 `mvn clean package` / `docker build` 产出。所以：不要把任何子包拆成"可独立发布的库"，也不要在 PR 里把 `private` 改成 `false`。判词与依据见 [`docs/release-checklist.md`](docs/release-checklist.md) §8 第 ② 项。

## 必读四页

| 页 | 为什么必读 |
|---|---|
| [`docs/module-map.md`](docs/module-map.md) | 33 个域各自管什么、API 前缀规律（`/api/tenant/**` vs `/api/platform/**`） |
| [`docs/data-model.md`](docs/data-model.md) | 表前缀图例 + 通用列约定 + **列名坑清单** |
| [`docs/permissions.md`](docs/permissions.md) | 多租户隔离靠拦截器；`@RequiresPerm` 多码是 **OR**；403 会被前端渲染成"暂无数据" |
| [`docs/workflow-engine.md`](docs/workflow-engine.md) §9 与 §10 | 五件最常做的改动各要动哪几处、怎么验证；以及**本文档哪些结论还没被运行验证过**——先看清这两节再动手，能省掉大量白改 |

## 代码约定（跟现有代码保持一致）

**后端**

- 类上 `@RestController` + `@RequestMapping("/api/tenant/<域>/...")`；**平台侧功能才用 `/api/platform/`**。URL 第二段决定鉴权域，别自造第三段。
- 返回统一 `{code, msg, data}`；分页结果的数据键是 **`list`**（不是 `records`/`items`），前端已按此对接。
- 需要权限的接口加 `@RequiresPerm`；操作留痕加 `@OperLog`。
- 跨域调用走 `framework/facade`，**不要**直接引用别的域的 Mapper。
- 访问影像域表要带 `@FsscDataSource("image")`，否则打到主库。
- 涉及查询层改动（`@Select`/`@Insert` 注解 SQL、以及直连 `JdbcTemplate` 的写法）：必须自证租户条件生效。本项目**没有 Mapper XML**（全仓 `*Mapper.xml` 零命中），SQL 只有注解与 Java 两种形态；绕过 MyBatis-Plus 拦截器的写法是**跨租户泄漏**。
- 注释与界面文案当前为中文，且项目**没有接国际化框架**（`package.json` 无 `vue-i18n`，全仓无 `createI18n`/`$t(` 调用）。新增用户可见文案就写中文字面量，别去造一份没人读的英文资源；状态/枚举码的中文显示统一登记在 `fssc-web/src/constants/billStatus.js`，做法见 `docs/workflow-engine.md` 9.5。

**前端**

- Vue 3 `<script setup>` + Element Plus；请求一律经 `src/request.js`。
- 页面放 `src/views/<域>/`，路由注册在 `src/router/`；权限用 `src/directives/` 的指令。
- 枚举的中文显示映射集中在 `src/constants/` —— 历史上出过"列表直接显示英文枚举值"的问题，改枚举务必同时看这里和后端出参。

## 提交规范

- 分支：`main` 保护；特性分支 `feat/<域>-<摘要>`、修复 `fix/<issue号>-<摘要>`。
- Commit：Conventional Commits，** scope 用域名**，例：
  `fix(expense): 撤回后单据状态回退为 DRAFT 而非停留在 APPROVING (#123)`
- 一个 PR 只做一件事。跨域改动请拆开，或在描述里画清影响面。
- **禁止提交**：`target/`、`dist*/`、`logs/`、`data/files/`、`.env`、任何 `db/Dump*` 或 `*_full_*.sql`（含真实数据）、演示账号明文口令、截图里的真实姓名/工号/证件号。CI 有兜底检查，但别指望它。

## PR 检查清单

- [ ] 本地跑起来并**实际操作**过改动涉及的功能（截图/录屏附在 PR 里）
- [ ] `mvn clean package` 通过、`npm run build` 通过
- [ ] 相关测试新增或更新；`npm run test` 绿
- [ ] 改了查询层 → 写了跨租户验证结论
- [ ] 改了配置项 → 在 `docs/` 里同步该项说明（含是否需要重启）
- [ ] 改了表结构 → 变更 SQL 与回滚 SQL **成对**提交（本项目**没有接 Flyway / Liquibase**，靠人工按序执行；约定与坑见 `docs/workflow-engine.md` 9.2 与它的 §10 第 7 条），并说明在全新库上如何复现；同时更新 `docs/data-model.md` 的表清单
- [ ] 改了文档 → `node _tools/cite_check.mjs <改动的 md>` 绿（引用路径与行号能跳到，且不指向未发布的转储/备份）
- [ ] 改了文档 → 顺手自查**作用域**：只写本项目三棵树（`fssc/`、`bpmn-repo/`、本仓库自身）里看得见的东西。不写绝对机器路径（`D:\…`、`C:\…`、你的用户名），不点名工作区里跟本项目同级的那些目录/工程，也不把内部施工件的编号当出处。闸现在会拦"作用域外引用"，但**别把它当唯一防线**——散文里提一句"我本机怎么配的"它抓不到，而对外部读者那就是噪声＋隐私泄漏。判词与实测记录见 `docs/release-checklist.md` §4c 整改记录④
- [ ] 改了枚举/状态码/用户可见文案 → 说明中文显示登记在哪（`billStatus.js` 的三张表），以及是否需要重跑 `npm run test`（本项目当前无国际化，不需要补英文资源）

## 测试

```bash
cd fssc-web && npm run test          # vitest 单测
cd fssc-web && npm run test:coverage
cd fssc-server && mvn test           # 后端测试（surefire）
```

端到端 UI 脚本**不在这个仓库里**（它是内部施工目录，里面有把口令写成字面值、把真实 JWT 落盘的调试件，已从发布白名单整体剔除，理由见 `docs/release-checklist.md` 第 2 节）。想在 CI 里跑界面级冒烟，请按那份清单的口径另制 `examples/api-smoke/`（2~3 个文件，凭据一律从 `process.env` 取），**不要**从施工目录拷任何子集进来。

**注意**：这个项目的正确性判定高度依赖运行期行为。数据库、流程引擎、多租户上下文相关的用例**必须打真库**，不要用 mock 掩盖问题（历史上出现过 mock 通过但生产迁移失败的情形）。

时区提醒：surefire 输出用 UTC，业务时区是 `Asia/Shanghai`，比对时间戳时先统一口径。

## 提问之前

先看 `docs/getting-started.md` 第 7 节「已知启动坑」——8 条覆盖了大半新手报错，尤其第 1 条（后端没起）和第 4 条（403 看着像空数据）。

## 接手本项目前要知道的两个现象

遇到时不要以为是自己的问题：

1. **配置项可能只被读入而未被执行。** 判断方法：搜该键的读取点，再顺着找有没有分支真正使用它。`docs/workflow-engine.md` 里有能力矩阵标注了哪些已验证生效。
2. **仓库里可能有多份同名不同代的文件/产物。** 判断"哪份是活的"看源码树与构建产物，不看文件名或文档标题。发布版已剔除产物。

如果你修好了这样一类问题，请在 PR 描述里写明"该配置此前是否为伪配置"，这是最有价值的信息。
