# 安全说明（SECURITY.md）

## 报告漏洞：请不要开公开 Issue

如果你发现本项目的安全问题（鉴权绕过、越权、注入、密钥泄漏等），请通过私密渠道报告：

- 首选：Gitee 站内私信仓库所有者（[a-share-yuan](https://gitee.com/a-share-yuan)）——本仓库托管在 Gitee，**没有** GitHub 那套 Security Advisory 页面
- 若日后镜像到 GitHub：用镜像仓库首页的 **Security → Report a vulnerability**
-  下面这枚邮箱仍是占位，对外公告前必须换成真实地址（现在它不属于任何人）
- 邮箱：`security@<你的域名>`（发布前替换为真实地址）

我们承诺 72 小时内响应，并在修复后公开致谢（如你愿意）。**在修复发布前请勿公开披露细节。**

## 这个项目是财务系统，请把下面几条当作红线

本项目处理报销单据、凭证、付款、影像档案与多租户数据，以下事项不是"建议"，是**部署前提**：

### 1. 仓库内置的默认凭据仅供本地演示，不可用于任何可访问网络的环境

| 配置 | 内置默认 | 生产必须 |
|---|---|---|
| `FSSC_DB_PASSWORD` | 6 位弱口令占位 | 强口令，且账号权限最小化（不要给 root、不要给 DDL） |
| `FSSC_JWT_SECRET` | 内置占位串 | `openssl rand -base64 48`，≥32 字节，多实例一致 |
| `FSSC_LINKU_CALLBACK_SECRET` | 空 | 与对接方约定后注入（空值时回调已全部拒绝，fail-fast） |
| `INVOICE_GATEWAY_API_KEY` | 空 | 网关启用内网免密钥模式时才可留空 |
| 演示账号 | 见 `docs/演示账号.md` | **上线前必须全部改密或删除** |

非 `dev` profile 下若仍在使用默认 JWT 密钥，`TokenService` 启动自检会**直接拒绝启动**。这是设计，不是 bug。

### 2. 生产环境必须关闭的端点

- `FSSC_OPENAPI_ENABLED=false` —— 接口文档会暴露完整攻击面
- Actuator 仅暴露 `health,info,metrics,prometheus`；`env/beans/configprops/heapdump/threaddump/shutdown` **不暴露**（已在配置中固定，请勿放开）
- `/actuator/health`（含 liveness/readiness）是**免鉴权白名单**，用于 K8s 探针与负载均衡；`/actuator/prometheus`、`/actuator/metrics` **需要 Token**。放公网时请用网络策略而非应用配置来兜底。

### 3. 多租户隔离模型（评估风险前必读）

平台与租户**共库**，隔离靠 `tenant_id` 列 + MyBatis 拦截器注入 `WHERE tenant_id = ?`，**不是**独立 schema。这意味着：

- 任何绕过拦截器的原生 SQL（自定义 XML、`JdbcTemplate` 直查、`@InterceptorIgnore`）都是潜在跨租户泄漏点
- 影像域走**独立数据源** `fssc_image`，由 `@FsscDataSource("image")` + AOP 路由
- 提 PR 涉及查询层改动时，请附跨租户验证结论

### 4. 已知未实现项（诚实清单，请勿假设已具备）

- **登录图形验证码**：`fssc.login.captcha-enabled` 只是开关，验证码下发/校验本体未实现。默认 `false` 是为演示脚本让步，**不是**生产可用配置。
- **权限校验**：`@RequiresPerm` 多码语义是 **OR**（任一命中即放行），不是 AND。写新接口时按此理解。
- **附件存储**：默认本地磁盘 `./data/files`（temp/business/archive 三区），**未做对象存储加密**。含发票/合同原件的目录必须在操作系统层做访问控制。
- **AI 网关**（`/api/ai/**`）：受"目录 + 红线 + 权限 + 一次性确认票据"四重闸门约束，高风险动作票据 TTL 10 分钟。它复用人侧 `/api/tenant/**` 链路，因此**人侧的越权缺陷同样会被 AI 侧继承**。

## 数据与合规

- 本仓库**不包含**任何真实生产数据、生产配置、客户名称或影像原件。若你在新建数据库时看到 `db/Dump*`、`db/backup/*_full_*.sql` 之类的文件，说明有人误提交了备份——请立即按上文渠道报告，不要下载。
- 若你部署后产生生产数据，`data/files/`、数据库备份、日志文件（本项目 dev profile 打开 MyBatis SQL 标准输出，日志会含业务参数）均属敏感数据，纳入你的备份与留存策略。
