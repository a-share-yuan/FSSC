# 发布检查单（release-checklist）

每次对外发布（打 tag / 推远端 / 发版说明）前，按本清单逐项核对并留痕。
任何一项不过，不发布。

## A. 卫生与安全（一票否决）

- [ ] `node _tools/secret_scan.mjs --fail-on-hit` 退出码 0（无口令/私钥/证件号/内网 IP）；
- [ ] `node _tools/secret_scan.mjs --docs-only --fail-on-hit` 退出码 0（对外文档无外部系统名）；
- [ ] 全仓 grep 无真实企业名/真实人名/真实银行账号（对照脱敏映射表抽查）；
- [ ] `db/demo-db/` 抽查：账号列无 `622202` 以外的纯数字串；手机号全 `1380013` 段；
      邮箱域名仅 `example.com`；`sys_config` 口令/令牌类为空或 demo 值；
- [ ] `.env.example` 与仓库内所有 `application*.yml` 无真实凭据；
- [ ] 无 `.prebak*`/`_bak`/`vsd_*` 等施工残留被跟踪（`git ls-files | grep -cE 'prebak|vsd_'` = 0）；
- [ ] `python scripts/health/check_delivery_clean.py --probe` 退出码 0
      —— 交付物（代码/SQL/界面文案/文档）无内部工作项编号与过程叙述，见 `交付规范-内部痕迹禁令.md`。

## B. 文档与引用

- [ ] `node _tools/cite_check.mjs` 0 死链（README/docs 内 `path:line` 引用全部有效）；
- [ ] README 快速开始与实际操作一致（导入命令、路径、端口）；
- [ ] `docs/` 索引（docs/README.md）列出的文件全部存在；
- [ ] CHANGELOG 已补本次版本条目（Keep a Changelog 格式，语义化版本）。

## C. 构建与测试

- [ ] `mvn -o clean package -DskipTests` 成功，产物 jar 约 92MB 且 `BOOT-INF/lib`
      依赖齐全（**瘦 jar = 打包中断，必须重打**）；
- [ ] 校验 zip 内新增 class：`zipfile` 读 `BOOT-INF/classes/.../Xxx.class`，
      确认含新增端点字符串 + entry 时间戳为新；
- [ ] 后端单测全绿（`mvn test -pl fssc-start`）；
- [ ] 前端构建通过（CI 内 `vite build` 或等效校验；本地用 compiler-sfc 校验避免写 dist）；
- [ ] CI 三个全绿：静态 guard / release-check / backend+frontend。

## D. 数据库与演示环境

- [ ] `bash db/import.sh` 在**干净库**实测 0 错误；行数与 dump 一致
      （sys_user=460 / md_bank_account=113 / crm_customer=63 / 主库 317 表）；
- [ ] 演示账号登录通过：`admin/123456`（租户 DEMO1）；
- [ ] 端到端冒烟：登录 → 提单 → 审批 → 付款 → 凭证 → 轨迹联查；
- [ ] `sql/` 迁移脚本序号连续无跳号；回滚脚本齐全；
- [ ] `wf_node_config` 孤儿行比例已核算并记录（引用统计前扣除）。

## E. 版本与仓库

- [ ] 版本号三处对齐：CHANGELOG / 前端 package.json / tag；
- [ ] git 身份已配置（非机器身份）；
- [ ] tag 已打（`vX.Y.Z`），Release notes 已写；
- [ ] 远端仓库已建（GitHub/Gitee），branch protection 已配（main 禁直推）；
- [ ] LICENSE / NOTICE / SECURITY.md / CONTRIBUTING.md 在册且为最新。

## F. 发布后 24 小时

- [ ] 干净机器按 readme.txt 跑通全栈（重点：首次启动的 Flowable 建表与首登）；
- [ ] issue 模板/讨论区已开；
- [ ] 监控告警已接（SLA 违约/卡单率/接口 5xx）；
- [ ] 收集首批用户反馈，登记已知问题清单。

## G. 已知待办（发布时需在 Release notes 声明）

- [ ] JDK 26 为非 LTS 版本，迁移 LTS 需重验构建（影响面：pom + CI + 用户环境）；
- [ ] 界面中文单语，无 i18n 框架；
- [ ] 前端包 `private: true`，不发布 npm 包；
- [ ] 干净机器全栈验证尚未完成前，Quickstart 标注为"高置信度草案"。

---
*清单用法：复制到版本目录逐项打勾，完成件随版本归档。不过项的处置：
能修的当场修；不能修的进 §G 声明，不许静默带病发布。*
