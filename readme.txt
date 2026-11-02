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
  * 业务完整：费用报账、应收应付、总账凭证、资产存货、资金票据、影像 OCR、
    共享作业运营六大域，后端约 160 个控制器 / 1,200+ HTTP 端点；
  * 数据规模：主库 317 张表 + 影像库 6 张表，演示库内置 315 个已部署流程
    定义，导入即可跑审批；
  * 开源协议：GNU Affero General Public License v3.0（SPDX 标识 AGPL-3.0-or-later，见 LICENSE / NOTICE）；闭源或商业使用需另行取得商业许可（见 LICENSE-COMMERCIAL.md）。

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

  脚本会自动选中 db/ 下最新的一份 Dump<日期>/（当前是 db/Dump20261102/），
  逐文件导入；主库 fssc 与影像库 fssc_image 在这个过程中自动建好。

  等价手工方式（不推荐，仅用于理解）：
      for f in db/Dump20261102/*.sql; do
        mysql --default-character-set=utf8mb4 -uroot -p < "$f"
      done

  Windows（CMD）：
      for %f in (db\Dump20261102\*.sql) do mysql --default-character-set=utf8mb4 -uroot -p < "%f"

  注意：db/Dump20261102/ 共 323 个文件 = 一表一文件（主库 fssc 317 表 + 影像库
  fssc_image 6 表），每个文件自带 CREATE DATABASE IF NOT EXISTS、USE 与
  SET FOREIGN_KEY_CHECKS=0，所以无需预先建库，文件顺序也不影响成败，可重复
  执行（幂等）。dump 内容与脱敏说明见 db/README.md。

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
  3. 走一遍主链路：我的工作台 → 费用报账 → 员工报销 → 员工费用报销（该页
     页签即填单，没有【新建】钮）→ 提交 → 审批 → 查看流程轨迹。
  4. 后端健康检查：curl http://localhost:8089/actuator/health/liveness

   同一张单不能由同一个人既提单又审批（引擎有防自审判定）。想跑通完整链路，
    照 docs/真实场景测试/ 的三本逐步操作手册做——每步写明"谁、登哪个账号、
    点哪里、应该看到什么"，各册 §1.2 有本册完整账号表：
        01-人工测试操作手册-费用报账全流程-新手版.docx
        02-人工测试操作手册-其它业务流程与功能-新手版.docx
        03-人工测试操作手册-系统管理员与流程配置-新手版.docx
    只用看手册时可以先记这几枚：100005/叶思琪（部门经理）、
    100002/赵勇（共享会计）、100003/雷子涵（出纳）、100023/覃立新（预制凭证审核）。
    完整业务背景见 docs/ 下的业务流程文档。

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
    Dump20261102/           整库 dump（323 个一表一文件，含演示数据与已部署流程）
    import.sh / import.bat  一键导入脚本（自动选最新的 Dump<日期>/）
    README.md               数据库导入说明（dump 内容、导入与脱敏注意事项）
  _tools/                   开发辅助工具（密钥扫描/引用检查/事实件生成等）

六、常见问题
--------------------------------------------------------------------------------
Q1 启动报 "Access denied for user 'root@localhost'"？
   .env 的 FSSC_DB_PASSWORD 与 MySQL 实际口令不一致；确认后重启。

Q2 前端页面打开但接口 401/跨域？
   确认后端已起在 8089（前端 Vite 代理 /api → 8089）；.env 的
   FSSC_API_TARGET 仅影响 preview 模式。

Q3 提示找不到表/flowable 相关错误？
   确认 db/import.sh 完整执行过（db/Dump20261102/ 的 323 个文件全部导入）；Flowable 的
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
  db/README.md              数据库导入说明与 dump 内容说明
  docs/                     系统介绍 / 系统特点 / 业务流程 / 部署运维等
  docs/真实场景测试/         三本逐步操作手册（含完整演示账号表与逐步判据）
  README.md                 项目总览与开源须知（中英混排）
  CONTRIBUTING.md           参与贡献指南
  SECURITY.md               安全漏洞披露流程

================================================================================
  协议：AGPL-3.0-or-later（AGPL-3.0，含网络条款）。第三方组件许可说明见 NOTICE。
  本软件按"现状"提供，不作任何担保；生产部署前请自行完成安全评估与压测。
================================================================================
