# BPMN 模型仓库化导出清单

> 生成时间：2026-10-31 12:13:55
>
> 事实来源：`wf_model.bpmn_xml`（设计态唯一权威）。本目录是它的文件化快照，请连同代码一起提交，用于 diff、review 与误覆盖回滚。
> 手工全量导出：`POST /api/tenant/wf/designer/export-repo`；发布动作会自动补写版本快照与 diff。

| modelKey | 名称 | 版本 | XML 摘要(sha256/12) | 最近部署 ID |
| --- | --- | --- | --- | --- |
| AR_RECON | 应收对账（作业） | 4 | ea4cdf6a0b8e | 39393ee0-bb65-11f1-bc06-d8bbc1e55a1a |
| AR_REPORT | 应收账款管理分析报告审批流程 | 4 | 5ad880bec737 | c28b621f-b671-11f1-b3a2-d8bbc1e55a1a |
| BG_SCHEME | 预算方案审批流程 | 1 | ccd85ec832b1 | d3a644c4-b46d-11f1-8a2d-d8bbc1e55a1a |
| CLAIM | 员工费用报销审批流程（差旅/招待/金额档） | 23 | fde1c951b544 | 26589173-c855-11f1-a6c4-d8bbc1e55a1a |
| CLAIM_NC | 费用报销（非集中） | 2 | f24f6f6b99c9 | 87ce042b-cf43-11f1-94f2-d8bbc1e55a1a |
| CONTRACT | 合同审批流程 | 2 | bc609a80ab0a | ac25c1db-b6b4-11f1-8056-d8bbc1e55a1a |
| FA_ADD | 资产新增审批流程（含在建转固档） | 7 | 0e210b4035b3 | 3180ff63-bb63-11f1-bc06-d8bbc1e55a1a |
| FA_ADD_NC | 资产新增（非集中） | 2 | 3846d53e4ffb | 91a9f902-cf43-11f1-94f2-d8bbc1e55a1a |
| FA_CER | 资本性支出申请(CER)审批流程 | 6 | 3de0db946834 | e2358133-b68c-11f1-8056-d8bbc1e55a1a |
| FA_CHECK_DIFF | 资产/存货盘点差异（作业） | 7 | 59dfb7dc5a56 | 3915e08e-bb62-11f1-8f3d-d8bbc1e55a1a |
| FA_DEPRECIATION | 折旧摊销（作业） | 6 | 8bcb1ba46b9c | 38bd875a-bb62-11f1-8f3d-d8bbc1e55a1a |
| FA_DISPOSE | 资产处置审批流程（报废/对外销售档） | 13 | ca35ba75fd62 | 31ea217b-bb63-11f1-bc06-d8bbc1e55a1a |
| FA_TRANSFER | 资产调拨审批流程（同公司/跨法人档） | 8 | 5526fde30014 | 31c1b1e7-bb63-11f1-bc06-d8bbc1e55a1a |
| FA_TRANSFER_NC | 资产调拨（非集中） | 2 | 52bb945144b4 | 9222f996-cf43-11f1-94f2-d8bbc1e55a1a |
| FA_VALUE_ADJUST | 资产价值调整审批流程（原值/减值/跌价档） | 7 | 7ed6a54e8cac | 322aacef-bb63-11f1-bc06-d8bbc1e55a1a |
| FM_ARCHIVE | 凭证归档（作业） | 5 | b8a69e4f093e | 6fd7ca38-bb65-11f1-bc06-d8bbc1e55a1a |
| FM_BORROW | 凭证借阅审批流程 | 4 | e62710eccf17 | 0534a497-b69e-11f1-8056-d8bbc1e55a1a |
| FM_DESTROY | 凭证销毁审批流程 | 6 | f97d94bd8018 | 1632e20a-b761-11f1-890e-d8bbc1e55a1a |
| GL_MANUAL_VOUCHER | 总账凭证审批流程（做账） | 7 | 7e8387e4ea19 | 3eabef21-c850-11f1-9186-d8bbc1e55a1a |
| GL_MERGE | 合并报表（作业） | 5 | c12f7d43af1a | f3902e67-c84f-11f1-9186-d8bbc1e55a1a |
| GL_MONTH_CLOSE | 期间关账（作业） | 7 | 3eb002447e8b | d74d2385-bb5f-11f1-8f3d-d8bbc1e55a1a |
| GL_OFFSET | 内部往来抵消（作业） | 4 | 0ebb16df6cf2 | da7146ed-bb5f-11f1-8f3d-d8bbc1e55a1a |
| INV_DISPOSAL_APPLY | 存货处置审批流程（申请→确认→执行） | 4 | 5598b4ffe7a4 | 3971e346-bb62-11f1-8f3d-d8bbc1e55a1a |
| INV_ISSUE_PROJECT | 存货领用出库（作业） | 5 | 2070d8a41f4e | 394220b2-bb62-11f1-8f3d-d8bbc1e55a1a |
| INVOICE_ISSUE | 开票与收入确认审批流程（含服务申请档） | 8 | 3013ff943e2d | 275c45db-c855-11f1-a6c4-d8bbc1e55a1a |
| INVOICE_MATCH | 发票校验复审（作业） | 7 | 5e844da13c27 | 4bd0e1cf-bb64-11f1-bc06-d8bbc1e55a1a |
| LOAN | 借款与还款审批流程 | 12 | 6e3b9ef8068a | 26dd9ff7-c855-11f1-a6c4-d8bbc1e55a1a |
| MD_MASTER | 主数据核准流程（五档合一） | 2 | 56bd042123e9 | 6a57b574-bb65-11f1-bc06-d8bbc1e55a1a |
| PAY_PLAN_BASE | 付款计划（作业） | 5 | f5c13b86956c | 4cb94607-bb64-11f1-bc06-d8bbc1e55a1a |
| PTP02-02 | 采购订单审批 | 1 | a8e1eb508a10 | 4e682bf3-b053-11f1-8f08-d8bbc1e55a1a |
| RECEIVABLE | 应收账款收款审批流程 | 5 | bd15843af898 | 5235e18c-c844-11f1-8e2d-d8bbc1e55a1a |
| RECEIVABLE_COLLECT | 应收账款催收（作业） | 5 | b2b1368c9978 | 395fb2a4-bb65-11f1-bc06-d8bbc1e55a1a |
| RECON_ACTIVE | 供应商对账（作业） | 6 | e7c3b6eb5774 | 4c987793-bb64-11f1-bc06-d8bbc1e55a1a |
| SETTLE | 对公付款审批流程（采购/预付/税缴/薪酬档） | 11 | cc74f491c94c | f46a61ca-c84f-11f1-9186-d8bbc1e55a1a |
| SETTLE_NC | 对公付款（非集中） | 2 | d8b2fa3fbf5e | 915dfbdf-cf43-11f1-94f2-d8bbc1e55a1a |
| TR_BANK_RECON | 银行对账（作业） | 5 | e826487e5914 | 39b6100c-bb65-11f1-bc06-d8bbc1e55a1a |
| TR_PAY | 付款执行审批流程（网银/票据档） | 7 | 73f316c20362 | 91fa3fb3-cde4-11f1-bd9e-d8bbc1e55a1a |
| TR_PLAN | 资金计划审批流程（编制/调整档） | 6 | 739172ec3dc0 | 39831928-bb65-11f1-bc06-d8bbc1e55a1a |
| TR_RECEIPT | 收款确认（作业） | 4 | 1958fbc530c9 | 39dd9540-bb65-11f1-bc06-d8bbc1e55a1a |
| TR_TRANSFER | 资金调拨/上存审批流程 | 6 | 4ce6b2267f7b | bcb87c97-b697-11f1-8056-d8bbc1e55a1a |
| TRAVEL | 出差事前申请审批流程 | 12 | 4b3821989bc4 | abf53bf3-b6b4-11f1-8056-d8bbc1e55a1a |
