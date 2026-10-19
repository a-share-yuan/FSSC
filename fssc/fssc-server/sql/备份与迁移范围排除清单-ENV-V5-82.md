# 备份与迁移范围·库级排除清单（ENV-V5-82 落盘件）

> 用途＝给「备份脚本／迁移清单／上线数据切换」一个**库级作用域判据**：哪些 schema 属于本代系统（必须备份、必须迁移），哪些是别代对象（必须显式排除、不得随本代迁移）。
> 读数出处＝`.tmp_b16/r980_w2_db_recheck.txt` F 段、`r981_w2_db_recheck2.txt` H 段、`r987_image_tables.txt`（mysql 只读，`information_schema` × `COUNT(*)`）；代码面出处＝`fssc-start/src/main/resources/application-dev.yml:26/36`、`application-prod.yml:76-84`。
> 纪律＝本件是**登记件**，不含任何写语句；执行权＝运维／DBA。

## 1. 判定表（10-07 实测）

| schema | 表数 | 建表区间 | 是否本代对象 | 判据 | 处置 |
|---|---|---|---|---|---|
| `fssc` | 295 | — | **是（主库）** | `application-dev.yml:26` 的 master `jdbc-url` 直指 `/fssc`；台账与全部走查读数均落此库 | 备份＋迁移**必含** |
| `fssc_image` | 6 | 2026-09-11 11:45:31 ～ **2026-10-07 09:59:04** | **是（影像子库，分库第 2 数据源）** | `application-dev.yml:36` image 数据源直指 `/fssc_image`；`application-prod.yml:76` 注释即「分库：master=fssc／image=fssc_image」；且在用表 `img_setting`（5 行）已于 10-07 由 `V1.4.51_img_setting_uk_unique_seed.sql` 加过真实唯一约束 | 备份＋迁移**必含**；**不得排除**（见 §2 更正） |
| `bcp` | 71 | — | **是（BCP 门户库）** | BCP 后端（`:8501`，PID 12692）在用；`sys_security_config` 等 13 键读数来自该库（台账 ENV-V5-89） | 备份＋迁移**必含**；随 BCP 发布窗切 |
| `fssc_drill` | 264 | 2026-09-16 00:30:29 ～ 00:32:02（**同一 2 分钟内建完**） | **否（一次性沙盘／演练库）** | 无任何 `jdbc-url` 指向它；建表区间＝单窗口批量脚本痕迹；FSSC 代码与配置零引用 | **排除**：不进备份、不进迁移、不参与数据切换 |
| `unierp_core` | 1,017 | 2026-09-04 17:44:47 ～ **2026-10-06 23:17:42（仍在动）** | **否（另一代产品：UniErp 核心库）** | 由 `:8090`（PID 1924，`unierp-core-web-1.0.0.jar`，进程启动 10-06 23:19:34）在用；与 `fssc`／`bcp` 是两套系统 | **排除**：本代上线不迁它；备份按 UniErp 自己的窗口走 |

## 2. 对本方案 §5 DB-2 的更正（判据级，不改原文只落此件）

方案《V5收官后运行方案-五线收口.md》§5 DB-2 原写「**三个**未登记库（`fssc_drill`／`fssc_image`／`unierp_core`）从备份与迁移范围显式排除」。实测后**其中一枚判错**：

- `fssc_image` 是 FSSC 的**影像分库**（第 2 数据源，dev 与 prod 两套 profile 都声明），把它排除＝影像域的 6 张表（含已加唯一约束的 `img_setting`）不备份、不迁移＝上线时影像设置与批次／文件／OCR 记录全部丢。
- 修正后的排除面＝**两枚**：`fssc_drill` ＋ `unierp_core`。
- 取证坑（沿用）：**判「某库是不是本代对象」不能只看 `information_schema` 里有没有它**，必须回读 `application*.yml` 的数据源声明；FSSC 是多数据源，单看 master 会把 image 子库误判成外部库。

## 3. 落此件之后仍要跑的腿（人工／运维）

- 备份脚本按 §1 的「必含／排除」两面各写一次清单，并跑一次 `--no-tablespaces` 试备（不落库内容），复点产物内 schema 名＝`fssc`＋`fssc_image`＋`bcp`、且不含 `fssc_drill`／`unierp_core`。
- 04 册 §5-Q3「沙盘数据是否迁生产」的拍板＝owner 项（人工清单 D1／M 项），本件只定作用域、不定迁移方向。

**签字栏**（本件为代判登记，代判≠代签）：＿＿＿＿＿＿（待人工签署）
