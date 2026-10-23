# 快速开始（getting-started）

> 本文与仓库根目录 `readme.txt` 内容同源但更详细：readme.txt 面向"下载即跑"，
> 本文补充每一步的验证方法、失败排查与结构说明。

## 1. 前置检查

```bash
java -version     # 必须 26.0.1（项目锁定 maven.compiler.release=26）
mvn -v            # 3.9.x
mysql --version   # 8.x
node -v           # 24.x
```

数据库字符集要求 `utf8mb4` / `utf8mb4_general_ci`（建库时指定）。
MariaDB 与 MySQL 5.7 **未经验证**，不要直接上生产。

## 2. 导入演示数据库

```bash
# Linux / macOS / Git Bash
bash db/import.sh

# Windows
db\import.bat
```

默认参数：`127.0.0.1:3306`，用户 `root`。覆盖方式：

```bash
MYSQL_PWD='你的口令' bash db/import.sh -h <mysql-host> -P <port> -u <user>
```

导入内容：`db/demo-db/` 下 323 个分表 SQL → 主库 `fssc`（316 表）+ 影像库
`fssc_image`（6 表），含 460 个演示用户、694 个菜单、315 个已部署流程定义。
**幂等**：可重复执行（每表 DROP 后重建）。

> 手动导入时的顺序约束：`fssc_routines.sql`（视图）依赖业务表，必须最后导入；
> import 脚本已自动处理，手工 `cat *.sql` 一把梭会报 `Table 'sys_user' doesn't exist`。

验证导入：

```sql
SELECT COUNT(*) FROM fssc.sys_user;            -- 期望 460
SELECT COUNT(*) FROM fssc.md_bank_account;     -- 期望 113
SELECT COUNT(*) FROM fssc_image.img_setting;   -- 期望 5
```

## 3. 配置环境变量

```bash
cp .env.example .env
```

必填两项：

| 变量 | 说明 |
|---|---|
| `FSSC_DB_PASSWORD` | MySQL 口令（留空启动失败，这是有意的 fail-fast） |
| `FSSC_JWT_SECRET` | ≥32 字节随机串，`openssl rand -base64 48`；多实例部署必须同值 |

可选项默认即可演示：`FSSC_OCR_CHANNEL=mock`（离线模拟票面）、
`FSSC_BANK_GATEWAY=mock`、`FSSC_REDIS_ENABLED=false`（单机用 MySQL GET_LOCK 调度）。

## 4. 启动

```bash
# 后端（端口 8089）
cd fssc/fssc-server
mvn -q clean package -DskipTests
java -jar fssc-start/target/fssc-server.jar

# 前端（端口 8080，Vite 代理 /api → 8089）
cd fssc/fssc-web
npm install
npm run dev
```

Docker/K8s 形态见 `fssc/docker/` 与 [部署运维](部署运维.md)。

## 5. 验证跑通

1. 打开 http://localhost:8080，`admin` / `123456` 登录（租户 DEMO1）；
2. 健康检查：`curl http://localhost:8089/actuator/health/liveness` → `UP`；
3. 走一张单：我的工作台 → 费用报销 → 新建 → 保存 → 提交 → 待办里审批通过；
4. 看轨迹：单据详情 → 流程轨迹（时间线来自 `chain.summary.reached`）；
5. 看出账：总账 → 凭证查询，应能看到该单自动生成的凭证。

## 6. 已知启动坑（启动失败排查）

| 现象 | 根因 | 处置 |
|---|---|---|
| `Access denied for user` | `FSSC_DB_PASSWORD` 不对 | 核对 .env 后重启 |
| `Communications link failure` | MySQL 没起/端口不对 | 起库；改 `FSSC_DB_MASTER_URL` |
| `Unknown database 'fssc'` | 没导库 | 跑 `db/import.sh` |
| 前端白屏 / 401 | 后端没起，或 Vite 代理没连上 8089 | 先 `curl :8089/actuator/health/liveness` 确认后端 UP，再看前端 |
| 接口 403 缺权限 | 用了 admin 之外的账号，或角色未授权 | 换 `admin`；或到「系统管理 → 角色」给当前角色勾上对应权限 |
| 登录后菜单/数据为空（非 403） | 登录时选错租户（非 `DEMO1`） | 登录页把租户切回 `DEMO1`；演示库数据都在该租户下 |
| 后端起不来 / 端口 8089 已被占用 | 旧进程或看门狗未退出，文件锁 | `netstat -ano \| findstr 8089` 看 PID 并结束；确认无残留再重启 |
| Flowable 报缺表 | 导库不完整 | 重跑 `db/import.sh`（幂等，每表 DROP 后重建） |
| `Unsupported class file major version` | JDK 不是 26 | 装 JDK 26.0.1（项目锁定 `maven.compiler.release=26`） |
| 启动报 `xxxMapper` 找不到表 / 租户条件缺失 | 改了查询层却绕过 MP 拦截器 | 见 `docs/permissions.md` 与 `docs/data-model.md` 的列名坑清单，必须自证租户条件生效 |

## 7. 目录速览

```
fssc/fssc-server/   后端（fssc-common + fssc-start 两模块）
fssc/fssc-web/      前端（Vue3 + Vite）
fssc/docker/        Dockerfile + k8s yaml
bpmn-repo/          8 个流程模型（版本化）
db/                 演示库 + 导入脚本 + 说明
docs/               本文档集
```

## 8. 已验证 / 未验证边界（诚实声明）

- **已验证**：数据库导入（干净库 0 错误，行数与 dump 一致）；演示账号登录口径；
  后端单测与前端构建在 CI 内。
- **未验证**：干净机器全栈（起后端→登录→提单→审批→凭证）尚未端到端跑通验证，
  遇阻请提 issue 并附日志。
