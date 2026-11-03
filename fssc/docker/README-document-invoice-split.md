# document / invoice 域：分库＋门面（阶段一）

本文件是 `document`／`invoice` 两域"先分库路由＋门面、暂不分进程"的落地说明书，
也是 `ArchiveFacadeImpl`、`InvoiceDomainFacade`、`DashboardService`、`DocTracingService`
四处注释里引用的那份清单。

> 阶段定位：与影像域**同级**的"分库＋门面"，不是独立进程。
> 当前 `fssc.datasource.document.enabled`、`fssc.datasource.invoice.enabled` 均为 `false`，
> 两个路由键都**别名到 master**（同一物理库），因此本阶段对运行期行为零改动；
> 它交付的是"归属声明 + 跨域出口收敛 + 切库前必须补齐的清单"。

---

## 一、这一阶段做了什么

### 1. 路由键就位

`framework/config/DataSourceConfig.java`

| 路由键 | 连接池 bean | 建池条件 | 未建池时 |
| --- | --- | --- | --- |
| `master` | `masterDataSource` | 恒建 | — |
| `image` | `imageDataSource` | 恒建 | — |
| `replica` | `replicaDataSource` | `fssc.datasource.replica.enabled=true` | 别名 master，**并套只读守卫** |
| `document` | `documentDataSource` | `fssc.datasource.document.enabled=true` | 别名 master（无守卫） |
| `invoice` | `invoiceDataSource` | `fssc.datasource.invoice.enabled=true` | 别名 master（无守卫） |

`FsscRoutingDataSource#setLenientFallback(false)`：未知路由键直接抛异常，不静默回落主库。

### 2. 路由标注（与影像同形）

影像域的做法是：控制器＋服务＋门面各自声明归属路由，跨域读取用**方法级** `@FsscDataSource("master")`
（必要时叠 `NOT_SUPPORTED, readOnly=true` 挂起外层事务，参考 `image/channel/HttpOcrChannel#recognize`）。
document／invoice 两域照此落地，本次新增的是类级归属声明：

| 域 | 类级 `@FsscDataSource("document")` |
| --- | --- |
| document | `ArchiveController`、`ArchiveAccessController`、`ArchiveAttachmentController`、`ArchiveDestroyController`、`ArchiveService`、`ArchiveAccessService`、`ArchiveAttachmentService`、`ArchiveDestroyService`、`ArchiveIntegrityService`、`BillArchiveService`、`ArchiveExpireJob`、`ArchiveFacadeImpl` |

| 域 | 类级 `@FsscDataSource("invoice")` |
| --- | --- |
| invoice | `InvoiceController`、`InvoiceEnhanceController`、`InvoiceService`、`InvoiceEnhanceService`、`InvoiceVerifyScheduler`、`InvoiceAbnormalAlertJob`、`InvoiceFacadeImpl` |

已存在的方法级跨域标注（保持不变）：

- `document/service/ArchiveService#enrichFlowKey` → `master`（只读 `wf_instance`，补归档流程实例号）
- `audit/service/AuditTrailService` → 类级 `master`（`audit_trail` / `sys_user`）
- `expense/facade/BillFacadeImpl` → 类级 `master`（`exp_bill`）
- `system/service/NumberRuleService#next` → `master` + `REQUIRES_NEW`（**跨库取号的标准写法**：
  挂起外层（可能是 image/document/invoice 库）事务、绑定 master 连接独立取号）

### 3. 跨域出口收敛到门面

`framework/facade` 下的三个门面是 document／invoice 两域对外唯一的读写出口：

| 门面 | 实现 | 说明 |
| --- | --- | --- |
| `ArchiveFacade` | `document/facade/ArchiveFacadeImpl` | 15 个方法：原有 9 个（含 3 参默认方法 `handleBorrowApproval`）＋ 新增 6 个（`findAllForExport`／`findByBillIdOrVoucherIds`／`pageArchives`／`createArchive`／`updateArchive`／`requestDestroy`） |
| `InvoiceFacade` | `InvoiceFacadeImpl` | 只保留"入池通道"2 个方法：`poolFromOcr`／`existsInPool`。影像独立进程用 `StandaloneInvoiceFacade` 占位，**不必**实现查询/勾稽 |
| `InvoiceDomainFacade` | `InvoiceFacadeImpl`（同一实现类） | 21 个方法：发票＋勾稽表的跨域读写。之所以与 `InvoiceFacade` 分成两个接口，就是为了让影像进程的占位实现只背负它能兑现的承诺 |

共用同一实现类的两个接口：`InvoiceFacadeImpl implements InvoiceFacade, InvoiceDomainFacade`。
勾稽写入（`createLink`）的"占用额"语义在 `ExpenseBillService` 一侧保持：缺省取发票面额，
否则跨单占用累计恒为 0，三单匹配与超付判定会失真。

依赖环的处理：`InvoiceFacadeImpl` 对 `InvoiceService` 用 `ObjectProvider` 延迟解析。
原因是共享域 `VoucherService` 反向依赖本门面，而 `InvoiceService → VoucherService`，
直接构造注入会构成构造器循环（Spring Boot 4 默认禁止循环引用）；Lombok 的 `@RequiredArgsConstructor`
**不会**把字段上的 `@Lazy` 复制到构造器参数（`javap -v` 已验），因此只能走 `ObjectProvider`。

---

## 二、例外清单（唯一允许跨域直连）
只有两处，均为**只读聚合**，且都跑在报表只读进程内：

| 文件 | 直连的表 | 为什么允许 |
| --- | --- | --- |
| `report/service/DashboardService.java` | `op_archive`、`inv_invoice` | 工作台看板按公司/状态做聚合计数，逐域 RPC 会变成 N+1；该类所在进程（8094）扫描包为 `com.fssc.report` / `tracing` / `framework` / `common`，**不装配** document／invoice 的门面实现 |
| `tracing/service/DocTracingService.java` | `inv_invoice`、`op_archive` | 单据追溯的联查聚合，同上 |

结构化复验（一条命令就能重新得到"只有这两处"）：

```bash
cd fssc/fssc-server/fssc-start/src/main/java/com/fssc
grep -rn "import com\.fssc\.\(document\|invoice\)\.mapper\." --include=*.java . | grep -v "^\./document/" | grep -v "^\./invoice/"
```

分库真正生效后，这两处要么把两张表并进副本库（推荐，报表进程本来就连副本），
要么改由域服务出数——二选一，**不要在报表进程里装两个域的门面实现**。

---

## 三、切库前置条件（未满足前不得置 `enabled=true`）

两个路由键现在只是"声明了归属"，**并不等于**代码已经能跨库运行。以下类在同一个方法里
既写本域表、又直读 master 表（或调用 master 域服务）；一旦连接真的分到两个库，
这些方法会在运行期报"表不存在"或写错库。切库前必须逐个重切：

| 类 | 同方法内混用的 master 资源 | 处置方向 |
| --- | --- | --- |
| `document/service/ArchiveService` | `wfEngineFacade`（起 FM_ARCHIVE 流程）、`wfInstanceMapper`（读 wf_instance）、`numberRuleService`、`deliveryService`（sys_company）、`billFacade`（exp_bill） | 流程/编号/单据侧调用改到本域事务之外或改门面；`enrichFlowKey` 已是方法级 master，可作模板 |
| `document/service/ArchiveDestroyService` | `wfEngineFacade`（FM_DESTROY）、`numberRuleService`、`sysUserMapper` | 同上；取号已是跨库安全写法，可先照搬 |
| `document/service/ArchiveIntegrityService` | `sysFileMapper`（sys_file 取文件裸 MD5） | 读文件摘要移出门面/或由影像域门面提供（`ImageFacade` 已返回 `SysFile`） |
| `document/service/ArchiveAccessService` | `wfEngineFacade`（FM_BORROW）、`sysUserMapper` | 同上 |
| `document/service/BillArchiveService` | `billFacade`（exp_bill）、`voucherService`（凭证）、`deliveryService`、`numberRuleService` | 已有门面形态，切库时把"取单据/凭证"挪到本域事务之外（或改 `NOT_SUPPORTED` 读段） |
| `document/facade/ArchiveFacadeImpl#handleArchiveApproval` | 读 `op_archive` 后调 `billFacade.markArchived`（写 exp_bill） | 审批收尾天然跨域：单据回写失败已 try/catch 降级，切库时改为"先定档、后回写"两段 |
| `document/job/ArchiveExpireJob` | `sysRoleMapper`/`sysUserRoleMapper`/`sysUserMapper` | 作业无外层事务，按"先取 master 收件人、再写本域"两段式改写，或把调用放到 Job 侧（Job 已类级标注 document） |
| `invoice/service/InvoiceService` | `glTaxProvisionMapper`、`mdSupplierMapper`、`expBillMapper`、`sysRoleMapper`/`sysUserRoleMapper`/`sysUserMapper`、`wfEngineFacade`、`voucherService` | 最重的一处；建议按"主数据/单据读"与"发票写"拆两段，读段套 `@FsscDataSource("master")` |
| `invoice/service/InvoiceVerifyScheduler` | `extInterfaceConfigService`（sys_config/ext_interface_config） | 作业无外层事务，两段式 |
| `invoice/job/InvoiceAbnormalAlertJob` | `sysRoleMapper`/`sysUserRoleMapper`/`sysUserMapper` | 同上 |

复验命令（切库前每条都必须落到"只剩方法级 master 或已拆两段"）：

```bash
cd fssc/fssc-server/fssc-start/src/main/java/com/fssc
grep -nE "(wfEngineFacade|wfInstanceMapper|numberRuleService|deliveryService|billFacade|sysFileMapper|sysUserMapper|sysRoleMapper|sysUserRoleMapper|expBillMapper|mdSupplierMapper|glTaxProvisionMapper|extInterfaceConfigService|voucherService|roleMapper|userRoleMapper|userMapper)\." \
  document/service/*.java document/job/*.java document/facade/*.java invoice/service/*.java invoice/job/*.java invoice/facade/*.java
```

（该命令在 `document`／`invoice` 两域共命中 52 行，分布为
`BillArchiveService` 14 / `InvoiceService` 12 / `ArchiveService` 11 / `InvoiceAbnormalAlertJob` 3 /
`ArchiveDestroyService` 3 / `ArchiveExpireJob` 3 / `ArchiveAccessService` 2 / `ArchiveFacadeImpl` 2 /
`InvoiceVerifyScheduler` 1 / `ArchiveIntegrityService` 1。
命中数含注释文本，切库前按文件逐个定级，不要按行数当工作量。）

另有一条**结构性约束**：`DataSourceAspect` 只在事务开启前决定路由。方法已在事务中时，
中途改路由键对已绑定连接无效——跨库写必须像 `NumberRuleService#next` 那样显式
`REQUIRES_NEW`，跨库读则用 `NOT_SUPPORTED, readOnly=true`。这是"分库＋门面"阶段的已知边界，
不改 `DataSourceAspect` 去猜意图。

---

## 四、真正的切库步骤（additive，不删原表）

> 顺序原则：先建库搬表 → 再改配置 → 再重启 → 最后才停写主库的旧表。
> 整个过程主库 `fssc` 中的原表**不删除**，回滚只需改回 `enabled=false` 并重启。

### 1. 测量当前数据量（切库成本）

2026-10-27 01:3x 只读实测（`SELECT COUNT(*)`，库 `fssc`）：

| 表 | 行数 | 表 | 行数 |
| --- | --- | --- | --- |
| `op_archive` | 0 | `inv_invoice` | 2 |
| `archive_attachment` | 0 | `inv_invoice_line` | 0 |
| `archive_access_log` | 0 | `inv_invoice_global` | 2 |
| `archive_destroy_approval` | 0 | `inv_bill_invoice` | 1 |
| | | `inv_verify_log` | 0 |

合计 5 行。当前是历史上最便宜的切库时刻；表结构以主库为准（仓库内没有这两域的完整 DDL，
导出用 `mysqldump` 而不是手写建表）。

### 2. 搬表（示例，库名自定）

```bash
# 口令走 MYSQL_PWD，不落命令行
mysqldump -uroot -h127.0.0.1 --single-transaction --no-create-db \
  --default-character-set=utf8mb4 \
  fssc op_archive archive_attachment archive_access_log archive_destroy_approval \
  > /tmp/fssc_document.sql
mysqldump -uroot -h127.0.0.1 --single-transaction --no-create-db \
  --default-character-set=utf8mb4 \
  fssc inv_invoice inv_invoice_line inv_invoice_global inv_bill_invoice inv_verify_log \
  > /tmp/fssc_invoice.sql

mysql -uroot -h127.0.0.1 -e "CREATE DATABASE IF NOT EXISTS fssc_document DEFAULT CHARSET utf8mb4; CREATE DATABASE IF NOT EXISTS fssc_invoice DEFAULT CHARSET utf8mb4;"
mysql -uroot -h127.0.0.1 fssc_document < /tmp/fssc_document.sql
mysql -uroot -h127.0.0.1 fssc_invoice  < /tmp/fssc_invoice.sql
```

### 3. 配置（`application-prod.yml` 已有键，只是取值）

```yaml
fssc:
  datasource:
    document:
      enabled: true
      jdbc-url: ${FSSC_DB_DOCUMENT_JDBC_URL:}   # 例：jdbc:mysql://.../fssc_document?...
    invoice:
      enabled: true
      jdbc-url: ${FSSC_DB_INVOICE_JDBC_URL:}
```

### 4. 验收判据（每条都要能反置）

1. **路由生效**：启动日志/健康端点里 `document`、`invoice` 连接池为独立池（非 `master-alias`）。
   反置：把 `enabled` 改回 `false`，探针读数必须回到别名。
2. **不再有跨库混用**：第三节的 grep 结果只剩允许形态（方法级 `master` 或两段式）。
3. **冒烟读写**：档案列表分页、档案登记一卷、发票池查询/入池、单据勾稽一条，
   全部落对应库（`SELECT` 新库能查到、主库这两张表行数不变）。
4. **只读副本不受影响**：`/api/report/**`、`/api/tracing/**` 在 8094 上仍只读通过
   （例外清单两处要么已并库，要么已改门面——见第二节）。

### 5. 回滚

`enabled=false` → 重启。两个路由键回到别名 master，业务继续读写主库原表；
新库里多出来的写入是**增量**，回滚前需手工比对一次（行数/最大 ID），确认没有只写在新库的数据。

---

## 五、进程（不在本阶段）

本阶段只做"分库＋门面"。独立进程排在"跑一阵"之后，理由：两域目前都与 master 域的
流程引擎、编号规则、单据表在同一方法里交织（第三节），先分库能把交织点全部暴露出来，
再谈进程才不会把 bug 变成远程调用的超时。真要起进程时，可照 `docker/k8s-image.yaml`
与 `start-image.bat` 的形状：同一个 fat jar、`-Dloader.main` 指定第二个 `@SpringBootApplication`、
profile 里排除 15 项 Flowable 自动配置、`xxxDomain` 健康组件只出现在 readiness。

启动器（`start-{ai,gateway,report,image}.bat`）还带三条实测约束：① 现有 `fssc-server.jar`
早于四个拆分主类，因此主类取不到时脚本用 `-Dloader.path=fssc-start\target\classes`
跑新编出的类，两边都没有就报错退出而不是抛 `ClassNotFoundException`；② `loader.path` 只放**一个**
条目——分号分隔的多条目在 .bat 里会被静默吃掉（实测结果仍是 CNFE），而 `fssc-common` 的编译产物
与 jar 内嵌 `fssc-common-1.1.0.jar` 逐字节一致（md5 `38a61d7a`），无需再挂第二条；③ `.bat` 内容
保持纯 ASCII，cmd 按 OEM 代码页解析，UTF-8 中文 `rem` 会漏出命令片段（实测报
`ng.profiles.active 不是内部或外部命令`）。
