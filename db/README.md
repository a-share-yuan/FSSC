# db/ — 演示数据库（一键导入）

本目录是 FSSC 的**可直接导入的 MySQL 演示库**：结构 + 演示主数据 + 已部署流程定义，一次导入即可跑起整套后端。

## 一分钟导入

```bash
# Linux / macOS / Git Bash
bash db/import.sh
```

```bat
:: Windows（CMD / PowerShell）
db\import.bat
```

等价的手工方式（不推荐，仅用于理解导入顺序）：

```bash
mysql -uroot -p -e "CREATE DATABASE fssc DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
                    CREATE DATABASE fssc_image DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
# 先导表（各自 USE 自己的库），视图/存储过程必须最后
cat $(ls db/demo-db/*.sql | grep -v routines) | mysql -uroot -p --default-character-set=utf8mb4
mysql -uroot -p --default-character-set=utf8mb4 < db/demo-db/fssc_routines.sql
```

- 导入账号默认 `root@127.0.0.1:3306`，可用 `-h/-P/-u` 或环境变量 `FSSC_DB_HOST/PORT/USER`、`MYSQL_PWD` 覆盖。
- 每个文件自带 `CREATE DATABASE IF NOT EXISTS` + `USE`，**无需预先建库**；每张表都是 `DROP TABLE IF EXISTS + CREATE + INSERT`，**可重复执行**（幂等）。

## 导入内容（实测数字）

| 项 | 数量 | 说明 |
|---|---:|---|
| 分表 SQL 文件 | 323 | `fssc_*.sql`（主库）+ `fssc_image_*.sql`（影像库）+ `fssc_routines.sql` |
| 主库 `fssc` 表 | 316 | 业务表 + 平台表（sys_*/pf_*）+ Flowable ACT_*/FLW_* |
| 影像库 `fssc_image` 表 | 6 | img_batch / img_file / img_setting / image_exception / image_review / ocr_record |
| 已部署流程定义 | 315 | `act_re_procdef`：8 个流程模型的历史版本，**开箱即可发起/审批**，无需再部署 BPMN |
| 视图 | 1 | `v_sod3_probe`（职责分离探测），故 routines 必须最后导入 |
| 演示用户 | 460 | 含 `admin`；全部种子用户口令见下 |
| 菜单 / 字典 / 客户 / 供应商 | 694 / 全量 / 63 / 56 | 主数据齐备，可直接演示各业务域 |

## 演示账号

| 账号 | 姓名 | 用途 |
|---|---|---|
| `admin` | 系统管理员 | 全部菜单与功能 |
| `100001` | 丁宇轩 | 普通员工（提单） |
| `350001` | 董宇轩 | 另一业务线员工 |

- **所有种子用户初始口令均为 `123456`**（BCrypt 哈希实测校验通过），与 `application-dev.yml` 的 dev 默认口令一致。
- 登录用 `user_name`（如 `admin`），不是用户 id。租户编码 `DEMO1`（爱共享公司）。
- 更多角色账号可在 `fssc.sys_user` / `fssc.sys_user_role` 中按需挑选。

## 数据脱敏说明

本 dump 已按开源标准净化，可直接公开分发：

- **企业名全部虚构**：公司主体为「爱共享」系（AIG-HQ/AIG-SH/…）；客户/供应商为环海重工、弘毅贸易等虚构名。
- **银行账号为保留测试值**：`622202` 前缀 + 序号，非真实账号。
- **手机号为保留测试号段**：`1380013xxxx`。
- **邮箱域名**：`example.com`（RFC 2606 保留域）。
- `sys_config` 中的口令/令牌类配置**均为空值或 demo 值**，无真实凭据；生产凭据请用环境变量注入（见根目录 `.env.example`）。

## 已知边界

- 导入依赖 **MySQL 8.x**（窗口函数 + utf8mb4）；MariaDB / 5.7 未验证。
- 存储过程/视图的 `DEFINER` 子句已在导出时剥离，跨机导入不会因 definer 权限失败。
- 影像库表当前只有配置行、无业务行（与源库一致），上传发票后自动产生数据。
- 导入验证记录：2026-10-23 在干净库上实测，0 错误，行数与 dump 一致；「起后端 → 登录 → 提单 → 审批」的全栈验证见发布清单。
