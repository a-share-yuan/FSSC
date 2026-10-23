================================================================================
                    FSSC 集团财务共享服务平台
                     中文安装运行说明（readme.txt）
================================================================================

一、这是什么
--------------------------------------------------------------------------------
FSSC（Financial Shared Service Center，财务共享服务中心）是一套多租户 SaaS
架构的集团财务共享平台，覆盖完整业务链路：

    员工提单 → 预算控制 → 合规预检 → 多级审批（Flowable BPMN 引擎）
    → 共享中心作业排班 → 发票查验/OCR → 出纳付款 → 自动生成总账凭证
    → 影像归档 → 单据全链路联查

主要特点：
  * 多租户 SaaS：tenant_id 列级隔离 + 拦截器自动重写，一套系统服务多家企业；
  * 流程引擎：Flowable 8 + bpmn-js 在线设计器，条件分支/会签/票签/转办/
    加签/撤回/传阅/自由驳回全套能力，流程变更不发版；
  * 业务完整：费用报销、应收应付、总账凭证、资产存货、资金票据、影像 OCR、
    共享作业运营六大域，后端约 160 个控制器 / 1,200+ HTTP 端点；
  * 数据规模：主库 316 张表 + 影像库 6 张表，演示库内置 315 个已部署流程
    定义，导入即可跑审批；
  * 开源协议：Apache License 2.0（见 LICENSE / NOTICE）。

二、运行环境（版本必须对齐）
--------------------------------------------------------------------------------
  组件        版本要求                    说明
  ----------  --------------------------  -----------------------------------
  JDK         26.0.1（maven.compiler      java -version 校验
              .release=26）
  Maven       3.9.x                       mvn -v 校验
  MySQL       8.x（utf8mb4 /              mariadb 与 5.7 未经验证
              utf8mb4_general_ci）
  Node        24.x（Vite 6）              node -v 校验
  Redis       可选，单机部署不需要         多实例分布式状态才需要

  硬件建议：内存 8GB+，磁盘 5GB+（含构建产物）。

  说明：后端依赖 MySQL 8 的窗口函数与 utf8mb4；影像库为独立库 fssc_image。

三、快速安装（三步）
--------------------------------------------------------------------------------
第 1 步：导入数据库

  Linux / macOS / Git Bash：
      bash db/import.sh
  Windows（CMD / PowerShell）：
      db\import.bat

  默认连 127.0.0.1:3306 的 root；口令用环境变量 MYSQL_PWD 设置，或按提示输入。
  其他主机/端口/账号：bash db/import.sh -h <主机> -P <端口> -u <账号>

  等价手工方式（不推荐，仅用于理解）：
      mysql -uroot -p -e "CREATE DATABASE fssc DEFAULT CHARACTER SET utf8mb4
                          COLLATE utf8mb4_general_ci;
                          CREATE DATABASE fssc_image DEFAULT CHARACTER SET utf8mb4
                          COLLATE utf8mb4_general_ci;"
      cat $(ls db/demo-db/*.sql | grep -v routines) | mysql -uroot -p
      mysql -uroot -p < db/demo-db/fssc_routines.sql

  注意：db/demo-db 共 323 个分表 SQL，每个文件自带建库/选库语句，可重复执行
  （幂等）。fssc_routines.sql（视图）依赖业务表，必须最后导入——import 脚本
  已自动处理该顺序，手工导入时请勿按字母序一把梭。

第 2 步：配置环境变量

      cp .env.example .env
      编辑 .env，至少填两项：
        FSSC_DB_PASSWORD=你的MySQL口令
        FSSC_JWT_SECRET=至少32字节随机串（openssl rand -base64 48）

  其余项（OCR 通道、银企直联、Redis）默认 mock/关闭，本地演示无需配置。

第 3 步：启动后端 + 前端

      cd fssc/fssc-server
      mvn -q clean package -DskipTests
      java -jar fssc-start/target/fssc-server.jar        # → http://localhost:8089

      新开终端：
      cd fssc/fssc-web
      npm install
      npm run dev                                        # → http://localhost:8080

四、验证安装
--------------------------------------------------------------------------------
  1. 浏览器打开 http://localhost:8080
  2. 用演示账号登录（登录名是 user_name，不是用户 id）：
       账号：admin      密码：123456      （系统管理员，全部功能）
       账号：100001     密码：123456      （普通员工，提单/审批）
     租户编码：DEMO1（爱共享公司）
  3. 走一遍主链路：我的工作台 → 费用报销 → 新建 → 提交 → 审批 → 查看流程轨迹。
  4. 后端健康检查：curl http://localhost:8089/actuator/health/liveness

  更多演示账号：见 db/README.md；完整业务演示路径见 docs/ 下的业务流程文档。

五、目录结构
--------------------------------------------------------------------------------
  fssc/
    fssc-server/            后端（Spring Boot 4.1 + JDK 26 + Flowable 8）
      fssc-common/          公共模块（租户/权限/工具）
      fssc-start/           启动模块（打包产物 fssc-server.jar）
      sql/                  历史迁移脚本（V1.4.x 系列，新装无需执行）
      scripts/              造数/巡检等辅助脚本
    fssc-web/               前端（Vue 3.5 + Vite 6 + Element Plus）
    docker/                 Dockerfile 与 k8s 编排样例
    image-service/          影像服务（可选独立部署）
    docs/                   配套文档（业务流程/系统介绍/部署运维）
  bpmn-repo/                流程模型仓库（8 个流程，版本化管理）
  db/
    demo-db/                演示数据库（323 个分表 SQL，含已部署流程）
    import.sh / import.bat  一键导入脚本
    README.md               数据库导入说明与演示账号
  _tools/                   开源发布工具链（密钥扫描/引用检查等）

六、常见问题
--------------------------------------------------------------------------------
Q1 启动报 "Access denied for user 'root@localhost'"？
   .env 的 FSSC_DB_PASSWORD 与 MySQL 实际口令不一致；确认后重启。

Q2 前端页面打开但接口 401/跨域？
   确认后端已起在 8089（前端 Vite 代理 /api → 8089）；.env 的
   FSSC_API_TARGET 仅影响 preview 模式。

Q3 提示找不到表/flowable 相关错误？
   确认 db/import.sh 完整执行过（323 个文件全部导入）；Flowable 的
   ACT_* 表演示库已内置，首次启动只会做结构校准。

Q4 JDK 版本报错？
   本项目锁定 JDK 26（maven.compiler.release=26），JDK 21/17 无法编译，
   请先安装 JDK 26.0.1。

Q5 想清空演示数据重新开始？
   重复执行 bash db/import.sh 即可（幂等，DROP 后重建）；影像文件在
   fssc/fssc-server/data/files/ 下，按需清理。

Q6 想接真实的发票 OCR / 银企直联？
   .env.example 里有 FSSC_OCR_CHANNEL（mock/gateway/paddle/http）与
   FSSC_BANK_GATEWAY 开关；生产凭据一律走环境变量，不要写进配置文件。

七、更多文档
--------------------------------------------------------------------------------
  db/README.md              数据库导入、演示账号、数据脱敏说明
  docs/                     系统介绍 / 系统特点 / 业务流程 / 部署运维等
                            （文档随版本持续补充）
  README.md                 项目总览与开源须知（中英混排）
  CONTRIBUTING.md           参与贡献指南
  SECURITY.md               安全漏洞披露流程

================================================================================
  协议：Apache License 2.0。第三方组件许可说明见 NOTICE。
  本软件按"现状"提供，不作任何担保；生产部署前请自行完成安全评估与压测。
================================================================================
