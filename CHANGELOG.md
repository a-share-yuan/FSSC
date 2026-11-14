# CHANGELOG — FSSC 集团财务共享平台（fssc-server）

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 约定。
交付物＝**后端 jar（fssc-server）＋ 数据库脚本（sql/ V 系列）＋ 权限治理脚本（Ashare/权限治理/）** 三件套，任一层变更均在此登记。

- SQL 脚本序号索引与执行规则：[`sql/README.md`](sql/README.md)
- 权限数据变更（2026-09 起）统一走 `Ashare/权限治理/`，执行器 `apply_perm_sql.py <file> --dry-run|--commit`

---

## [未发布] - 2026-10-28（归档组卷闭环 · 档号与载体硬化）

### 行为变化（测试与交付方必读）

1. **一卷可多件**：新增卷内件清单（`op_archive_item`）。件号／凭证号／题名／日期／起页／页数／文件数在「档案管理 › 归档整理」卷行展开的卷内目录里维护，支持【追加件】【移出卷】；卷表 `bill_id/ref_no/related_ids` 降级为**首件镜像**（列表「关联单据」列仍显示首件，全量以卷内目录为准）。
2. **定时归档不再产出「已归档」卷**：02:00 定时作业、「归档所选」、「自动归档」接口只把卷登记为**待归档（`PENDING`，完整性 `UNKNOWN`＝哈希已算、未校验）**，终态一律由档案岗点【执行归档】并办结 `FM_ARCHIVE` 审批后回写。禁止把「待归档」当「已归档」判。
3. **空卷与实物未签收单不能执行归档**：卷内 0 件（按钮即置灰）、或卷内任一件 `delivery_status=UNSIGNED`（非集中管理公司豁免）时，执行归档返回 400 并点名单号；报错文案指引先到「共享运营 › 实物流签收」签收。`delivery_status` 为空（无实物交付单）不拦。
4. **档案岗可在案卷内直接查看卷内文件**：卷内文件经档案域取流令牌打开，不再绕「单据详情」找附件；范围限本卷件清单里的文件，越权仍被拒。
5. **单据流转联查末段副标题文案变更**：「最终状态」→「待归档／归档审批中」；办结后该段显示件（卷）数与已归档标记。

### 新增

6. **案卷载体类型（`V1.5.01`，列 `op_archive.carrier_type`）**：纸质／电子。**纸质卷**在执行归档与审批定稿前必须**逐件填写起页与页数**（同一卷内连续、不重叠），电子卷按件计数不强制编页；空值一律落纸质（严格侧）。待归档卷可在卷行展开处改载体，审批在途／已定稿只读。
7. **档号改用全宗号前缀（`V1.5.00`）**：`archive_no` 形如 `AIG-HQ-AR2026102800001`（全宗号取立档单位 `sys_company.company_code`），`archive_code` 同步镜像；历史卷已按迁移脚本重编号并留备份表 `bak_op_archive_no_v1500`。档号一经赋予**不可变**——改号会使该卷完整性哈希失效。
8. **完整性哈希维护端点**：`POST /tenant/compliance/archive/{id}/resign` 按卷重算并回写 `integrity_hash`／`integrity_status`（档号、件、附件任一变更后由运维执行）；`POST /tenant/compliance/archive/{id}/carrier` 改待归档卷的载体。不再允许用 SQL 直改 `integrity_status` 充绿。

### 迁移与回滚

- `V1.4.99_archive_item.sql`（卷内件表）、`V1.5.00`（档号迁移＋备份表）、`V1.5.01`（载体列）均已在本环境执行；三件均带前置/后置自检 SQL，期望值为 0。
- 回滚：`V1.5.00` 用备份表逐行还原 `archive_no`（脚本内含还原段）；`V1.5.01` 为加列，回滚＝删列（注释态脚本，未自动执行）。

---

## [1.2.0] - 2026-10-18

### 阶段⑦~③ 四项差距闭环（打印/导入导出引擎、元数据驱动、多账簿）

- **⑦ 空库造真实业务数据（V1.4.81）**：`scripts/seed_biz_data.py` 造数引擎（--dry-run/--commit/--clean/--verify）。
  9,853 行 / 64 表 / 2026 全年 12 个会计期间，覆盖销售→对账→预制发票→应收→收款核销→坏账全链、
  690 张借贷平衡凭证、费用链与流程日志、AP 协同、资金/税务/银行/总账、运营治理。
  **空表 138 → 78**；10 项勾稽验证全通过（凭证平衡/分录合计/应收平衡/核销合计/外键有效/月结标记等）。
- **②-A 打印/套打引擎（V1.4.81/V1.4.82）**：新建 `md_print_template` / `_item` / `_set` / `_set_item` 4 张表，
  `PrintTemplateService` 做「毫米绝对坐标换算 + 数据绑定 + 必录拦截 + HTML 渲染」，
  `PrintTemplateController` 6 个端点。**5 套硬编码在前端 TEMPLATES 常量里的模板已逐字迁入库**（V1.4.82，59 行），
  `usePrintTemplate.js` 改为「读后端模板 → 传数据 → 打印」，旧 API 零外部引用故无破坏。
  **必录属性真会拦**：验收实测挖掉「单据编号」→ blocked=true 且列出缺失字段。
- **②-B 统一导入导出引擎（V1.4.83/V1.4.84）**：新建 `md_io_template` / `_field` 2 张表，
  `IoTemplateService` 按模板解释器统一执行（STRING/NUMBER/DATE/DATETIME/BOOLEAN/ENUM + 参照表反查 + 长度校验 + 多日期格式兜底 + Excel 序列号），
  **不中断整批**（逐行错误清单，用户一次改完）。
  **6 支手写 `*ExcelService` 的列头已逐字迁为 7 个模板（V1.4.84，81 行）**，含对账单导出反向模板。
- **① 表单元数据驱动（V1.4.85/V1.4.86）**：新建 `md_form_field` / `md_form_validation` 2 张表。
  306 个字段 / 162 条校验规则**由真实表结构反射生成**（`scripts/gen_form_meta_seed.py` 读 information_schema 的列注释/类型/长度/精度），
  不手写字段列表 ⇒ 元数据与物理表逐字对得上。
  `FormMetaService` 做服务端**权威校验**（前端绕过直调也拦得住）+ 按角色折叠 readonly/visible 锁定属性 + 默认值填充；
  `useFormMeta.js` 前端动态表单渲染器。**md_form_config 从「只有分节名的死登记」变成被真消费的配置**。
- **③ 多账簿/多准则/多本位币（V1.4.87/V1.4.88）**：新建 7 张表（gl_standard / gl_currency / gl_accounting_book /
  gl_book_subject_map / gl_book_balance / gl_book_voucher / `_line`）。
  `uk_gab_main` 唯一索引把「一个核算组织在同一核算体系内仅能存在唯一的主账簿」落到**数据库级兜底**，
  应用层校验只负责给可读报错（并发穿透由索引拦住，实测插重复主账簿被 1062 拒绝）。
  已建 **15 本账簿**（14 主 + 1 副），AIG-HQ 同时持 ASBE 主账簿 + ASBE 副账簿(USD) + SME 主账簿，
  昆山基地建 4 月制非自然年度账簿；**11 本账簿期初试算全部 net=0.00**。
  顺带补种权益科目（4001/400101/410101/410102）—— 实测 `md_subject` 所有者权益类 0 条，
  缺权益侧试算不可能平衡（恒等式 资产+费用=负债+权益+收入）。
- **验证**：`scripts/_gap_acceptance.py` 端到端验收 28 项全通过（真实 HTTP 调用，覆盖「配置→生效」闭环）；
  `mvn package` BUILD SUCCESS，jar 88.17 MB（fat jar，非瘦 jar）；服务 8089 健康。

## [1.1.0] - 2026-10-14

### Added

- **`wf_assignee_scheme` 首套方案真跑起来（2026-10-15，P0-5）** —— 补齐 POST 维度取人的全部前置，并落一套**取人结果不变**的方案：
  - **`sql/V1.4.62_post_backfill_{seed,rollback}.sql` — `sys_user.post_id` 补数（173 人）**。`WfAssigneeResolver` 的 POST 维度走 `sys_post → sys_user.post_id`，但实测落库前 `post_id` **非空 = 0**（173 个用户全 NULL）⇒ POST 维度必然解析不出人 → `resolve()` 抛 `BizException`「参与人解析失败」→ **单据卡在该节点无人可办**。映射口径：`sys_post` 的 20 个岗位是**通用职级**（J0001 首席执行官…J0004 助理经理）＋4 个专用岗，`sys_role` 的 70 个角色是**业务职责岗**（CFO/共享会计/…），两者是不同维度，故按语义就近映射（映射表见 `.tmp/_post_mapping.py`，可审可改），**不做 1:1 生搬**；取人优先级＝显式映射角色 > 未映射的具体角色 > `COMMON_EMP` 兜底（J0004）。审核类角色（共享会计/总账会计/预制凭证审核/财务审核/内审）统一上抬到 J0016 主任，避免与被审方同层（职责分离）。落库后实测 **每个岗位的 POST 维度都可解析出人（零在岗岗位 = 0）**。**注**：现有 176 个存活节点 `assign_type` 全为 `ROLE`，走 `listRoleCandidates` 不读 `post_id`，故本件对现网**零行为差异**。不写 `update_by` —— `sys_user.update_by` 是 bigint 审计人 ID，非字符串。
  - **`sql/V1.4.63_assignee_scheme_{seed,rollback}.sql` — 首套方案 + `CLAIM/Task_7` 改 `assign_type=POST`**。选 `Task_7`「财务共享审核单据【共享会计】」试点。**为何不是简单配单维度 POST**：单配 `POST(J0016)` 会把该节点可办人**从 4 人扩到 16 人**（J0016 主任里混有仓库管理员/档案管理员/内审经理，与"共享会计审核"语义不符）—— 属业务语义变更，需另案裁决。故改用 **`combine_mode=AND` 双维度交集**：`ROLE(FSSC_AUDITOR) ∩ POST(J0016)`，实测交集＝`{3797 共享会计-01, 3820 共享会计-02, 3836 成本费用岗-01, 3898 共享会计-03}`，**与现状 ROLE 取人逐字相同（A==B 已断言）** ⇒ 落库后 Task_7 取人结果**不变**，但走的是「参与人方案」新链路（优先级＋条件表达式＋多维度 AND/OR/EXCLUDE），机制得到真实验证。只改 `assign_type` 一列，`assign_value` 保留 `FSSC_AUDITOR` 便于一键回滚；`UPDATE` 带 `AND assign_type='ROLE'` 幂等护栏。
  - **★ 运行期实证（2026-10-15 11:0x）**：真实发起 `CLAIM` 实例（`amount=6000000`）并经 `POST /api/tenant/wf/approve` 逐节点推进 `Task_2 → Task_4 → Task_5 → Task_6 → Task_7`，五个节点全部通过**合法办理人本人 token** 办结（越权会被引擎 403 拦截）。`Task_7` 的引擎实际指派 `ACT_RU_TASK.ASSIGNEE_=3797`（共享会计-01），与 `GET /api/tenant/wf/config/node/assignee-preview` 返回的方案解析 `userIds` **一致** ⇒ **「改 assign_type=POST → 走 WfAssigneeResolver 方案链路 → 真实指派」整条通道运行期闭合**。对照组 `Task_11`（未改 ROLE、方案为空）返回 `resolved=false` + 「运行时保持 wf_node_config.assign_type 既有取人行为」，证明 `resolved=true` **非端点恒真**。顺带实证三条业务规则生效：共享作业池互斥（403「同时只允许一人办理…流程管理员可用改派」）、连办/自审拦截（403「禁止同一人在同一审批流程中连续办理多个节点」）。
  - ** 并行改动导致的口径偏移（非引擎缺陷，需知悉）**：`wf_assignee_dim` 的 POST 维度行（id=2）**已被软删**（`del_flag=1`），当前方案只剩 ROLE 单维度 ⇒ `combine_mode=AND` 对单维度退化为恒等，解析结果 = ROLE 全集 **12 人**。成因：并行窗口同期**新增 264 名用户**（173→453，其中 8 名子公司共享会计使 `FSSC_AUDITOR` 由 4 人变 12 人）并**重构岗位体系**（`sys_post` 20→24 个，新增 `J0050 主管` / `J0055 财务总监` / `J0060 员工` / `J0090 内审`；审核类人员统一改到 `J0060 员工`(id=25，205 人)，致 **`J0016 主任` 归零**）。若 POST 维度仍在场，`AND(ROLE ∩ POST(J0016))` 会因 J0016 空而解析不出人 → `resolve()` 抛 `BizException` → **单据卡死**，故当前软删在数据上反而安全。**取人范围对比：改前 ROLE 分支 12 人 == 改后方案解析 12 人，一致。**后续需裁决「岗位取人」维度是否继续投入。
  - **★ 本轮纠正的一条判断**（P0-2 阶段曾误判）：**「代码在 + 数据空」≠「只缺数据」**。`WfEngineFacade.java:1645` 的 `applyAssigneeSchemes(...)` 只挂在 `switch` 的 **`default`** 分支，而落库前实测 `wf_node_config` 全表 386 行的 `assign_type` 是 ROLE 364 / INITIATOR 21 / USER 1，`POST`/`SHARE_POOL`/`EXPRESSION` **历史上零行** ⇒ **没有任何节点会落进 `default`，灌方案表纯空转**。真解法必须先有一枚节点改 `assign_type` 为 `POST`/`SHARE_POOL`/`EXPRESSION`，本件即补上这第一枚（`CLAIM/Task_7`）。
  - **上一轮「HTTP 腿不可用」结论已推翻**：09:0x 复测 `/actuator/health`、`/api/auth/profile`、`/api/tenant/wf/task/todo`、`/wf/config/node/assignee-preview`、`/wf/dev/start` **全部 200 且秒回**（`dev/start` 真跑引擎 11.3s）⇒ 08:42 那次归因的「服务端 GET / 拦截器阻塞」**不成立**，真实原因是**沙箱网络栈瞬态抖动**。教训：把「某类请求全部失败」直接归因为「服务端有问题」不够 —— 应先隔时段复测再下结论，否则会让不存在的缺陷进台账。

- **P0「配置面→生效面」三项通道补齐**（2026-10-15，依据《FSSC产品化标准化差距分析》八类框架的类别 4「配置到运行产物没有通道」）：
  - **`sql/V1.4.59_addsign_gate_key_{seed,rollback}.sql` — 加签闸口两枚 `sys_config` 键**。代码读点早已就位（`WfEngineFacade.isAddSignGateEnabled`/`addSignGateModels`/`isConfigGatedOn`），但 `sys_config` 里查无 `approve.addsign_gate.enabled`/`.models`，故 `allow_addsign` 一直是「界面不显示、运行时零读取」的三重落空。取**窄灰度**：`enabled=1` + `models=CLAIM,LOAN`（依据实测——`allow_addsign=1` 的 26 枚存活节点只分布在 CLAIM 12 / LOAN 12 / TR_PAY 1 / SETTLE 1 四个模型）。实测效果：灰度内 CLAIM/LOAN 的 **11 枚 `allow_addsign=0` 节点开始真拦**（403 且提示可读原因），24 枚 `=1` 仍放行，**灰度外 139 枚逐字不变**。回滚只有 DELETE 一条路径（两键本不存在）。
  - **`sql/V1.4.61_approval_item_{seed,rollback}.sql` — `wf_approval_item` 灌 6 行**（CLAIM 的 Task_5 部门经理 / Task_11 财务总监 / Task_13 总裁，各配 `同意`+`驳回` 两项）。**本件替代了原计划的「灌 `wf_assignee_scheme`」**——逐条读码证伪：`applyAssigneeSchemes` 只挂在 `WfEngineFacade.java:1645` 的 **default 分支**，而实测 `wf_node_config` 全表 386 行（含软删）的 `assign_type` 分布为 ROLE 364 / INITIATOR 21 / USER 1，**POST/SHARE_POOL/EXPRESSION 历史上从未出现过一行** ⇒ 没有任何节点会落进 default，灌方案表一行也不会生效（空转）。审批项不经过该 switch，且实体 javadoc 明写「库里无行时 complete() 收不到 approvalItemCode，行为逐字一致」——是当前唯一「灌了立刻见效且可一键回退」的目标。消费链已逐段读码确认闭合：`GET /workflow/task/approval-items` → `WfEngineFacade.approvalItemsOf(#2395)` → `listEnabled`；`POST /workflow/task/approve{approvalItemCode}` 与其**同源同一解析器**，不存在「看得见点不动」。`modelKey` 取 `procDefId.split(":")[0]`，实测 `CLAIM:232:…` ⇒ 逐字同源已核。**安全边界**：①不写 `wf_task_history.action`（该列 `varchar(20) NOT NULL` 且 `WfQueryService:676` 用 `"REJECT".equals(action)` 反推实例状态，写文案会误判「已驳回」为「已办结」）；②`target_node` 一律留空 ⇒ 落回原「终止本流程」路径（真退上游需先开 `sys_config.wf.reject.free.enabled`，现值 0）；③**一律不配 `TERMINATE`**——终止不可逆，须业务先签字。
  - **`sql/V1.4.60_dict_type_{seed,rollback}.sql` — `sys_dict_type` 灌 8 行**。该表原为 **0 行**，而 `sys_dict_data` 已有 3 行 `expense_type` ⇒ 字典项在游、类型容器空，前端已注册的 dict CRUD（`api.js:937-941`）在界面上管不到任何东西。本件补 `expense_type` 孤儿容器，并立 5 个枚举类型骨架（`wf_node_multi_type`/`wf_node_empty_strategy`/`wf_addsign_position`/`wf_addsign_status`/`wf_approval_item_category`/`sys_status`/`dict_yes_no`）作为「枚举字典收口」的目标表。**只灌 type 不灌 data**：枚举值须逐域由业务 owner 判定，本件不代判，避免造出错值污染渲染层。孤儿检查归零。
  - 三件均按仓内铁律执行：seed/rollback 成对、先落整表镜像（`Ashare/权限治理/_mirror_20261015/*.tsv`）、先 dry-run 再 commit。未触碰 `wf_node_config` / `wf_task_history` / `sys_dict_data`（复点确认行数未变）。

- **国际化基座（i18n，域8A/T0.5）**：`fssc-web` 引入 `vue-i18n`（**^9.14.5**，Composition API + 全局注入；注：registry 将 `^11` 解析为 9.x，v9 同样支持 `legacy:false` + `useI18n` + ref 式 `locale`，不影响本基座用法）；新增 `src/i18n/index.js`（语言探测/持久化/`<html lang>` 同步）、`src/i18n/element-locale.js`（Element Plus 组件文案跟随，避免"表格分页仍是中文"的半切换）、`src/i18n/locales/{zh-CN,en-US}.js`（**70 键，首发 en-US，配 zh-CN 回落**）、`src/components/common/LocaleSwitch.vue`（全局语言切换器，已注册为全局组件）。`main.js` 装载 i18n 并监听 locale 变化同步 EP locale。**注意：业务页面文案抽取属域8B 存量范围，本次只落地基座，不改业务视图。**
- **i18n ** `scripts/i18n-check.py` + `scripts/i18n-dump-keys.mjs`：①语言包键对齐（Node 真实 import 导出扁平键集，zh/en 必须完全一致）；②禁动态 key 拼接（`$t('x.'+v)` 会让扫描/校验/回退全部失效）；③硬编码中文残留对基线 `scripts/i18n-baseline.json` 比对，只许下降不许上升（CI 下 `--strict` 超基线即阻断）。
- **交付纪律** `scripts/release-check.sh`：CHANGELOG 必须含当前 pom 版本条目 + `sql/` V 系列序号校验 + 复用 i18n ；已接入 `.github/workflows/ci.yml` 新增 `release-check` 作业。

- **税务通道归属裁决（域2 / T1.4）**：发票四组能力（识别 / 查验 / 认证抵扣 / **数电开票**）已由 `InvoiceGateway` 封装为中性契约 `/api/tax/v1/**`（含 `/billing/blue` 蓝字开具、`/billing/task/{requestId}` 异步轮询、`/billing/invoices/query`，且已内建"同单号仅可开具一次"幂等语义与全字段语义映射）。裁决：**乐企作为 InvoiceGateway 的第二上游封入网关，不让 FSSC 直连**——依据交付说明不变量 1「上游凭证只活在网关进程内」，乐企密钥/税控盘证书/报文格式不得进入业务系统。据此**作废** `TaxInvoiceAdapter` 中臆造的 `codeType`/`signRequired`/`receiptNo`/`invoiceNo` 报文与自造回执校验（会造出第二份契约并与网关漂移），改为**原样透传**，税务三不变量写进类注释。实测：FSSC `sys_config.invoice.gateway.base-url=http://127.0.0.1:8096`、`ocr.channel=gateway`（识别/查验已在跑）；`GOLDEN_TAX` 通道配置**不存在**故 `pushTax` 当前走模拟分支；网关进程 8096 当前不可达。裁决记录见 `Ashare/权限治理/T1.4_发票能力归属裁决_20261014.md`。

- **银企直联收编进 connector（域3 / T1.1-T1.3）**：消除银行能力的平行实现。
  - **新增** `com.fssc.connector.adapter.bank.BankPaymentAdapter`（`ChannelType.BANK_PAYMENT`）：Spring 自动注册进 `ChannelAdapterRegistry`，承接通道语义（幂等键透传、错误归一、多银行 provider 分派挂接位）；凭据托管/幂等回放/重试/超时/`integration_call_log` 留痕全部复用统一网关。
  - **改造** `HttpBankGateway`：`fssc.bank.gateway=http` 时内部由「自查配置 + RestTemplate 手拼 `X-Api-Key`」改为**经 `ConnectorFacade` 走统一治理**，因而获得**幂等**（原先无，重复提交会重复付款）、凭据加密注入（`Authorization: Bearer` + `X-API-Secret`，原先 apiSecret 明文未用）、`integration_call_log` 留痕、熔断限流。通道配置沿用 `category=BANK`，**配置零迁移**。
  - **业务层零改动**：`BankGateway` 接口与 `PayBatchService` 均未修改。
  - **幂等失败重试方案（方案甲，决策记录见 `Ashare/权限治理/T1.1_幂等失败重试方案决策_20261014.md`）**：网关 `findPrior()` 按 `(category, idempotency_key, tenant_id, is_duplicate=0)` 精确匹配且**不校验 `success`**，首次失败也会被回放 → 付款失败将永远重试不成功。故幂等键改为 `BANK_PAY:{tenantId}:{batchNo}#a{attempt}`：同批次重复提交（同键）命中回放→**不重复付款**；失败重试（换键 `#a1`）未命中→**真实外呼**。**零改公共网关**；治本方案（`findPrior` 仅回放 `success=1`）建议独立立项并做全通道回归。
  - **连接域铁律合规**：报文组装与流水号解析保留在业务包 `HttpBankGateway` 内（按 CI 规则 9，业务包禁 import `connector.adapter.*`），两侧仅通过 `ChannelResponse` 契约对接；`ci-check` 规则 9/10 通过。
  - **实测**：后端 1031 源文件 `BUILD SUCCESS`；`ci-check EXIT=0`。BANK 通道实测 `status=DISABLED`/`api_url` 为空，验证用本地假银行（`scripts/_t13_idempotency_verify.py`）并已完整还原环境（通道恢复、留痕清零）。

- **核心域集成 E2E**（T0.2）：新增 `scripts/e2e-core-domains.py`——覆盖**费用/工作流/付款/凭证/预算**五域契约 + 跨域贯通（单据详情与溯源链），端点路径与必填参数**实测抄自各 Controller 签名**（如 `/gl/balance` 需 companyId+year+month、`/gl/posted` 需 voucherId），不猜测；默认只读零污染（`--write` 才开写）。已接入 `scripts/run-all-smokes.py` 编排（9 脚本）。在线 8089 实跑：共享会计与总账会计两账号各 **14/14 通过**，总账侧真实数据面（总账分录 20 条、科目余额 8 行、试算平衡含借贷合计、预算占用 10 条）均 code=0。

- **参与者解析 fast-fail 守卫** `WfAssigneeTypeGuard`：启动时全量扫描流程模型参与者配置（`log.error` 大声报告、不中断启动）；模型发布/重部署时对非原生 assign_type（`USER/ROLE/INITIATOR/DEPT_LEADER/DEPT_LEADER_UP` 之外）强制要求存在启用的 `wf_assignee_scheme`，**写库前**硬失败，杜绝「坏配置落库、发起即炸」的不一致态。
- **form_perm 字段级守卫扩点**：扣款审批 `approveDeduct()` 的 `WfFormPermGuard` 补入 `auditRemark`（审批意见）字段，堵住越权改字段缺口。
- **`/task-form-perm` 读守卫**：该端点补 `wfReadGuard.assertTaskReadable(taskId)`，与兄弟端点对齐，防越权读他人任务表单权限。

### Changed

- 版本号 `1.0.0` → `1.1.0`（根 pom + fssc-common/fssc-start 两个模块 pom 的 parent 引用同步）。

### Database & Permissions（随包 SQL，不在 jar 内）

- **R15 三用户定夺整改**（新口径「一人一职能角色 + `is_builtin=1` 基线豁免」落地，R15 违例 3→**0**）：按「影响最小」解绑 4 行 `sys_user_role`——Y000283 摘 `DEPT_MANAGER`（留 `FIN_AR`）、Y002010 摘 `COST_STAFF`+`GL_VOUCHER_REVIEW`（留 `FSSC_AUDITOR`）、Y005791 摘 `PROJECT_MANAGER`（留 `AR_COLLECTOR`）。`Ashare/权限治理/seed_manual_r15_3users_20261014.sql` + 同名 rollback 成对。三人在途待办实测均为 0，无任务被孤儿化。
- **预制凭证审核岗 `GL_VOUCHER_REVIEW` 复位**（恢复制审分离）：R15 整改摘除该岗后其 CLAIM 三个「审核预制凭证」节点 0 在岗，临时改派 `FIN_GL` 虽消除断链，但导致**审核预制凭证与 `GL_MANUAL_VOUCHER` 的制证确认/记账复核同为 `FIN_GL`**，削弱制审分离（另一测试会话独立发现）。本次复位为专职岗：①CLAIM `Task_14`/`ET_7`/`ETB_7` + **此前漏改的 `SETTLE/Task_14`（id=1647）** 共 4 节点改回 `GL_VOUCHER_REVIEW`；②该岗补 7 项只读复核菜单（`214/247/3064/3065/3066/3302/3419`），**刻意不授予**过账/生成权限 `3193(gl:entry:post)`/`3301(voucher:generate)`/`3303(voucher:post)`，使制审分离在**菜单权限层**同样成立；③由零职能角色的 Y001081/Y005391（财务管理部）承接 → 仍满足一人一职能，R15 不新增违例。核验 8 项全过：R15=0、SoD `GL_VOUCHER_REVIEW↔FIN_GL`(sod id=33)=0、全库无 0 在岗角色节点。`Ashare/权限治理/seed_manual_gl_voucher_reviewer_20261014.sql` + 同名 rollback 成对。
- **总账域制审分离整改**（收口历史遗留，先于 R15 整改即存在）：①`GL_MANUAL_VOUCHER` `GK_3`「总账记账复核」由 `FIN_GL` 改派 `FIN_AUDIT`（财务审核岗），使**记账复核与 `GK_4` 制证确认分人**；②`GL_MERGE` `GMG_2`「合并复核」由 `FIN_GL` 改派 `FIN_MANAGER`（合并复核属经理级事项），使**编制与复核分人**；③`GK_1`「编制底稿/卡片摊销（按类型解析责任人）【共享会计】」原节点名写【共享会计】却派 `FIN_GL`，改派 `FSSC_AUDITOR` 使名实相符（该角色↔`FIN_GL` 早有 SoD 互斥 id=4）。同步修正 `GK_3`/`GMG_2` 节点名。④补登 SoD 互斥对 `FIN_GL↔FIN_AUDIT`(id=34)、`FIN_GL↔FIN_MANAGER`(id=35)，平台级。角色节点数：`FIN_GL` 13→10。核验 7 项全过（R15=0、新增两对 SoD=0、无 0 在岗节点）。`Ashare/权限治理/seed_gl_sod_separation_20261014.sql` + 同名 rollback 成对。
- **COMPLIANCE_ADMIN 去 IAM 码**（用户 2026-10-14 裁决）：撤销 `system:user:assignRole`(3333) + `system:user:resetPwd`(3336)，保留 `system:sod:scan`(3762)。`Ashare/权限治理/seed_comp_admin_20261014.sql`（成对 rollback 同在）。
- **在建工程(3393)补授权 FA_ASSET_ADMIN**：全系统唯一「存活 C 页零授权」修复。根因＝`seed_d1_ws2_20261013.sql` 有意删行 `(58,3393)`（D1 WS-2 域重裁「撤 C/M 导航」），但 3393 的六枚 `md:cip:*` 功能码无独立 F 行、随 C 页一并消亡（连带损伤，违背 D1「保留 F 功能码」口径）。本次恢复该行即恢复功能码；如需严格维持 D1 裁决，执行 `rollback_cip_grant_20261014.sql` 可逆。`Ashare/权限治理/seed_cip_grant_20261014.sql`（成对 rollback 同在）。执行后存活 C 页零授权数 1 → **0**。
- **页面收敛批次3复核收口**（`财务共享页面收敛执行台账_批次3_20261014.md`）：原始 7 项中 2 项已天然闭合（228/108 前批已退），余 5 项确认维持缓办（T-GL-3/T-TR-5 流程载体未部署；3402→3070、3365 降按钮转前端 backlog）。
- **税务岗税负分析最小修复**（用户 2026-10-14 点名「保留并最小修复」）：T5 回归抽检证实税务岗（TAX_MANAGER/TAX_RPT_STAFF）因 10-13 P0-B + D1 WS-2 删除其存活报表载体而丢失税负分析（403）。按 D1 §7 预约路径，对照存量 13 个报表角色的同款组合，补活载体三件套 **216 管理报表(M) + 3622 管理报表工作台(C) + 3706 税负分析-查看(F)** × 2 角色共 6 行（`Ashare/权限治理/seed_tax_report_restore_20261014.sql`，成对 rollback 同在）。DB 核验 6 行落库无重复；8089 实测 Y007482/Y001652 税负分析 **403→200 真实数据**，合规岗仍正确 4030，collab 持码/无码行为零变化。最小边界：3701/3058/3297 及 47 角色的已退册存量行明确不动（D1 既定状态保留）。

### Docs

- 新增 `sql/README.md`：251 个 SQL 脚本的序号索引（V 系列 125 主脚本按版本序编表、回滚成对列出、热修线/备份/过程产物分区）。
- 新增 `A4_fastfail_formperm扩点_交付说明_20261014.md`、`财务共享页面收敛执行台账_批次3_20261014.md`、`T5_回归抽检报告_collab403_税域范围_20261014.md`（含税务岗税负分析修复执行记录与复测矩阵）。

---

## [1.0.0] - 2026-09-10

首个发布版（`releases/fssc-server-1.0.0-20260910-200121.jar`）。数据库侧演进主线见 `sql/README.md` 一区 V 系列 V1.0.0 ~ V2.2.53，摘要：

### Added（按 SQL lineage 归纳）

- **P0 合规与核算底座**：合规规则、事件编排总线、核算自动化（凭证来源类型 2→9）、单据溯源双字段、实物流签收。
- **P1 业务域补齐**：资产折旧明细与转固链接、存货成本模型、税务一体化（进项认证/全税种申报/风险指标）、共享运营实质化、预置分析报表体系、编号规则统一（16 业务域）。
- **P2 智能化与集成**：OCR 结果落库、Integration Gateway 调用日志。
- **应收应付蓝图**：apflow/arflow 落地、Flowable 审批接线、索赔/付款真实分录、票据登记/核销工作台/AR 月结/坏账核销。
- **SCP/CCP 协同平台打通** + 账期锁定 + 坏账比例配置；APAR 全域节点级 RBAC。
- **流程引擎覆盖财务共享通用环节**：加签（G1）、节点抄送（G2）、投票超时、驳回产品化、工作日历、共享作业层（G3）、归档/治理/过程日志/消息 Outbox（G4）、全链路时间线存量回填。
- **页面收敛机制**：RA 归一批次、深链合并（批次1，31 页）、同域同权 tab 合并（批次2，17 页）、目录授权、根级游离页归位、治理约束落库（已退册菜单禁止物理删除）。
- **工程化收尾**：演示数据去实名化（两轮）、wave13 唯一约束转换 + 编号规则播种、wave14 系统列守卫、G4~G9 权限拆捆与断链 F 收敛、历史测试单据清理（V9.9.9~16）。

### Fixed

- 流程瘦身（A/B/D 段）、零节点模型修复、孤儿/兜底节点配置治理、菜单图标修正、演示种子数据清理（多轮）。

---

[1.1.0]: https://example.invalid/fssc/compare/1.0.0...1.1.0
[1.0.0]: https://example.invalid/fssc/releases/tag/1.0.0
