<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <!-- 流程进度：生命周期 + 当前节点 + 审批链全景，常驻顶部（三个页签都看得见） -->
  <FlowProgressPanel :bill="focusBill" :nodes="chainNodes" :loading="chainLoading"
                     :updated-at="chainAt" @refresh="refreshProgress" />

  <!-- 提交审批的结果横幅，常驻到被手动关闭或下一次操作覆盖。
       放在页签之上（不是「发起单据」页内）—— 提交成功后 composable 会把页签切到「我的单据」，
       页内的提示条会跟着被藏起来，等于没有提示。 -->
  <el-alert v-if="submitOutcome" class="bw-outcome" :type="submitOutcome.ok ? 'success' : 'warning'"
            show-icon :closable="true" :title="submitOutcome.title" :description="submitOutcome.desc"
            @close="submitOutcome = null" />

  <el-tabs v-model="b.activeTab">
    <!-- 发起单据 -->
    <el-tab-pane label="发起单据" name="create">
      <el-row :gutter="16">
        <!-- 左侧：概要 / 费用详情 / 分摊（由页面 slot 提供） -->
        <el-col :xs="24" :md="14">
          <slot name="create" v-bind="b" />
        </el-col>

        <!-- 右侧：通用区块（公司/部门/预算/附件/冲借款/关联/合同） -->
        <el-col :xs="24" :md="10">
          <el-card header="单据信息">
            <el-form ref="billInfoFormRef" :model="b.form.bill" :rules="billInfoRules" label-width="80px" size="default">
              <el-form-item label="公司" prop="companyId">
                <el-select v-model="b.form.bill.companyId" filterable placeholder="选择公司" style="width:100%" @change="onCompanyChange">
                  <el-option v-for="c in b.companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="部门" prop="deptId">
                <el-select v-model="b.form.bill.deptId" filterable placeholder="选择部门" style="width:100%">
                  <el-option v-for="d in filteredDepts" :key="d.id" :label="d.name" :value="d.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-card>

          <el-card header="预算占用预览" style="margin-top: 12px">
            <el-alert v-if="b.budgetWarn.length" type="warning" :closable="false" show-icon
              title="以下预算科目将超出可用额度（提交时会被拦截）" style="margin-bottom: 8px" />
            <el-table :data="b.budgetPreview" size="small" border empty-text="暂无预算科目占用">
              <el-table-column prop="label" label="预算科目" />
              <el-table-column label="本次申请" width="110">
                <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ b.fmt(row.used) }}</span></template>
              </el-table-column>
              <el-table-column label="可用余额" width="110">
                <template #default="{ row }">{{ row.unknown ? '—' : b.fmt(row.available) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.unknown ? 'warning' : row.over ? 'danger' : 'success'" size="small">
                    {{ row.unknown ? '额度未知' : row.over ? '超预算' : '可支' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card header="发票 / 附件" style="margin-top: 12px">
            <div class="attach-row">
              <!-- accept 同时放行普通附件（doc/xls/ppt/zip/txt 等）：只列发票格式的话，用户要传合同、
                   行程单一类附件得先在文件选择器里切「所有文件」，再挂上一个永远不会发生的「待识别」。
                   上传后由后端按扩展名定性：发票类 attachType=INVOICE 才识别，其余落 OTHER。 -->
              <UploadButton label="选择文件" :accept="ATTACH_ACCEPT" biz-type="INVOICE" :biz-id="b.form.bill.id || null" zone="TEMP" parse :on-done="onAttachUploaded" />
              <el-button v-if="b.form.attaches.length" size="small" type="primary" link @click="previewAll">预览全部</el-button>
            </div>
            <div class="attach-hint">
              发票类（PDF / OFD / 数电票 XML / 图片）上传后自动识别票面；Office、压缩包等其它格式作为普通附件保存，不参与识别。
            </div>
            <!-- 缩略图网格 -->
            <div v-if="b.form.attaches.length" class="thumb-grid">
              <div v-for="(a, i) in b.form.attaches" :key="i" class="thumb-card">
                <div class="thumb-img-wrap">
                  <el-image
                    v-if="isImage(a.fileName) && thumbUrls[a.fileId]"
                    :src="thumbUrls[a.fileId]"
                    :preview-src-list="previewList"
                    :initial-index="i"
                    fit="cover"
                    class="thumb-img"
                    lazy
                  />
                  <div v-else-if="isImage(a.fileName)" class="thumb-placeholder">
                    <el-icon size="28"><Picture /></el-icon>
                    <span>加载中</span>
                  </div>
                  <div v-else class="thumb-placeholder">
                    <el-icon size="28"><Document /></el-icon>
                    <span>{{ getFileExt(a.fileName) || 'FILE' }}</span>
                  </div>
                  <!-- OCR 状态角标：只有发票影像才有"待识别/识别中/已识别/失败"，
                       Office/压缩包等普通附件显示「普通附件」，不再挂着永远不会发生的"待识别" -->
                  <span class="ocr-badge" :class="ocrBadgeClass(a)">
                    {{ ocrBadgeText(a) }}
                  </span>
                </div>
                <div class="thumb-name" :title="a.fileName">{{ a.fileName }}</div>
                <div class="thumb-actions">
                  <el-button link size="small" @click="previewOne(a)">查看</el-button>
                  <el-button v-if="isImage(a.fileName)" link size="small" type="warning" @click="triggerOcr(a)">OCR识别</el-button>
                  <el-button link size="small" type="danger" @click="removeAttach(i)">删除</el-button>
                </div>
              </div>
            </div>

            <!-- 已解析发票：勾选 → 填入明细行并合计 -->
            <div style="margin-top: 12px">
              <div style="font-weight: 600; margin-bottom: 6px">已解析发票 / 手工录入（勾选后自动填入明细行并合计）</div>
              <el-table :data="b.form.invoices" size="small" border empty-text="暂无发票，请上传发票文件或新增发票行手录">
                <el-table-column label="填单" width="52">
                  <template #default="{ row }"><el-checkbox v-model="row.checked" @change="b.toggleInvoice(row)" /></template>
                </el-table-column>
                <el-table-column label="发票号" min-width="150">
                  <template #default="{ row }"><el-input v-model="row.invoiceNo" size="small" placeholder="发票号" @change="onInvoiceNoChange(row)" maxlength="255" /></template>
                </el-table-column>
                <el-table-column label="开票日期" width="126">
                  <template #default="{ row }"><el-date-picker v-model="row.invoiceDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" /></template>
                </el-table-column>
                <el-table-column label="金额(价税)" width="118">
                  <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" @change="b.recalcInvoice(row); b.syncInvoiceLine(row)" /></template>
                </el-table-column>
                <el-table-column label="税率%" width="110">
                  <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" style="width:100%" @change="b.recalcInvoice(row); b.syncInvoiceLine(row)" /></template>
                </el-table-column>
                <el-table-column label="税额" width="84">
                  <template #default="{ row }">{{ b.fmt(row.taxAmount) }}</template>
                </el-table-column>
                <el-table-column label="不含税" width="86">
                  <template #default="{ row }">{{ b.fmt(row.netAmount) }}</template>
                </el-table-column>
                <el-table-column label="销方" min-width="120">
                  <template #default="{ row }"><el-input v-model="row.sellerName" size="small" placeholder="销方名称" @change="b.syncInvoiceLine(row)" /></template>
                </el-table-column>
                <el-table-column label="货物/服务名称" min-width="140">
                  <template #default="{ row }"><el-input v-model="row.primaryItemName" size="small" placeholder="解析自票面，可改后重匹配" @change="b.applyItemMatch(row)" /></template>
                </el-table-column>
                <el-table-column label="类型" width="130">
                  <template #default="{ row }">
                    <el-select v-model="row.invoiceType" size="small" style="width: 100%" @change="b.syncInvoiceLine(row)">
                      <el-option v-for="t in INVOICE_TYPES" :key="t.code" :label="t.label" :value="t.code" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="来源" width="80">
                  <template #default="{ row }">
                    <el-tag v-if="row.parsed" type="success" size="small">已解析</el-tag>
                    <el-tag v-else type="warning" size="small">手工</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="52">
                  <template #default="{ $index }"><el-button type="danger" link @click="b.removeInvoice($index)">删</el-button></template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top: 8px" @click="b.form.invoices.push(b.newInvoice())">+ 新增发票行（扫描件手工录入）</el-button>
            </div>
          </el-card>

          <el-card v-if="pay" header="合同 / 往来单位" style="margin-top: 12px">
            <!-- 合同/往来单位均为 clearable 选填引用（customerId 与主表单同源，由主表单校验），
                 挂空规则数组只为纳入中央框架视野，不做强制校验 -->
            <el-form ref="billExtraFormRef" :model="b.form.bill" :rules="billExtraRules" label-width="90px" size="default">
              <el-form-item label="合同">
                <el-select v-model="b.form.bill.contractId" filterable clearable style="width:100%">
                  <el-option v-for="c in b.contracts" :key="c.id" :label="(c.contractNo||'')+' '+(c.contractName||'')" :value="c.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="往来单位">
                <!-- 这里与主表单是同一个 customerId，但原先没接 @change ⇒ 从右侧卡片选单位
                     只改了 id，counterName/收款账号/PO 与收货单候选全不跑＝半初始化单据。
                     与主表单同源＝把变更交给宿主页面注册的 onCounterPick（页面侧才有那套派生逻辑）。 -->
                <el-select v-model="b.form.bill.customerId" filterable clearable style="width:100%"
                           @change="(id) => props.onCounterPick && props.onCounterPick(b, id)">
                  <el-option v-for="c in b.customers" :key="c.id" :label="c.customerName" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-card>

          <el-card v-if="claim" header="冲销借款" style="margin-top: 12px">
            <el-button size="small" @click="b.openLoanDialog">+ 选择借款单冲抵</el-button>
            <el-table :data="b.form.loanOffsets" size="small" border style="margin-top: 8px">
              <el-table-column prop="loanBillNo" label="借款单号" />
              <el-table-column label="冲抵金额" width="120">
                <template #default="{ row }"><el-input-number v-model="row.offsetAmount" :min="0" :max="row.maxAmount || undefined" :precision="2" style="width: 100%" /></template>
              </el-table-column>
              <el-table-column label="操作" width="60">
                <template #default="{ $index }"><el-button type="danger" link @click="b.form.loanOffsets.splice($index, 1)">删</el-button></template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="claim" header="关联事前申请（自动带出分摊）" style="margin-top: 12px">
            <el-button size="small" @click="b.openApplyDialog">+ 选择事前申请单</el-button>
            <el-table :data="b.form.relations" size="small" border style="margin-top: 8px">
              <el-table-column prop="sourceBillNo" label="申请单号" />
              <el-table-column prop="useAmount" label="可冲抵金额" width="120" />
              <el-table-column label="操作" width="60">
                <template #default="{ $index }"><el-button type="danger" link @click="b.form.relations.splice($index, 1)">删</el-button></template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- 首审环节办理人（提单选人）：仅当该流程首审节点配成 PICK/POOL 档时出现，AUTO 不渲染 -->
      <el-card v-if="b.firstPick.mode === 'PICK' || b.firstPick.mode === 'POOL'" style="margin-top: 12px">
        <template #header>
          <span>第一道审批</span>
          <el-tag size="small" :type="b.firstPick.mode === 'PICK' ? 'warning' : 'primary'" style="margin-left: 8px">
            {{ b.firstPick.mode === 'PICK' ? '提单时指定办理人' : '任务池抢单（无需指定）' }}
          </el-tag>
        </template>
        <div v-if="b.firstPick.mode === 'POOL'" style="font-size: 13px; color: var(--el-text-color-regular)">
          环节「{{ b.firstPick.nodeName || '第一道审批' }}」由共享任务池承接，本单据公司下
          {{ b.firstPick.candidates.length }} 人可领取，提交后先到先得，无需在此指定办理人。
        </div>
        <div v-else-if="!b.firstPick.candidates.length" style="font-size: 13px; color: var(--el-color-danger)">
          环节「{{ b.firstPick.nodeName || '第一道审批' }}」在本单据所属公司下暂无可选办理人，
          请先在「系统管理 · 角色授权」补岗后再提交。
        </div>
        <div v-else style="display: flex; align-items: center; gap: 10px">
          <span style="font-size: 13px">环节「{{ b.firstPick.nodeName }}」由谁受理</span>
          <el-select v-model="b.firstPick.userId" filterable :style="{ width: '320px' }"
            :placeholder="b.firstPick.required ? '请选择办理人（必选）' : '已按唯一候选人自动带出'">
            <el-option v-for="c in b.firstPick.candidates" :key="c.userId" :value="c.userId" :label="c.name">
              <span>{{ c.name }}</span>
              <span style="float: right; color: #999; font-size: 12px">
                {{ [c.deptName, c.pendingCount == null ? '' : `在办 ${c.pendingCount}`].filter(Boolean).join(' · ') }}
              </span>
            </el-option>
          </el-select>
          <span v-if="b.firstPick.required" style="font-size: 12px; color: var(--el-color-warning)">
            共 {{ b.firstPick.candidates.length }} 人可选，未选择不能提交
          </span>
        </div>
      </el-card>

      <!-- 底部操作栏 -->
      <el-card style="margin-top: 12px">
        <el-button :disabled="pendingWrite" @click="doSaveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="b.submitting" :disabled="pendingWrite" @click="doSubmit">提交审批</el-button>
        <el-button v-if="b.form.bill.id" link @click="b.resetForm()">新建空白单</el-button>
      </el-card>
    </el-tab-pane>

    <!-- 我的单据 -->
    <el-tab-pane label="我的单据" name="my">
      <el-card>
        <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
          <el-select v-model="b.filterType" style="width: 160px" @change="b.loadBills">
            <el-option label="全部" value="" />
            <el-option label="事前申请" value="APPLY" />
            <el-option label="报销" value="CLAIM" />
            <el-option label="付款" value="PAY" />
            <el-option label="借款" value="LOAN" />
            <el-option label="还款" value="REPAY" />
          </el-select>
          <el-button size="small" @click="b.exportCsv">导出</el-button>
        </div>
        <el-table :data="b.bills" size="small" stripe highlight-current-row style="cursor:pointer"
                  @row-click="(row) => { b.selected = row }">
          <el-table-column prop="billNo" label="单号" width="150" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ b.billTypeLabel(row.billType) }}</template>
          </el-table-column>
          <el-table-column label="子类" width="90">
            <template #default="{ row }">{{ b.billSubTypeLabel(row.billSubType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" />
          <el-table-column label="金额" width="110">
            <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ b.fmt(row.totalAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="b.statusType(row.status)" size="small">{{ b.statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <!-- 驳回类型（exp_bill.reject_type）：被驳回的单看不出是
               「漏单驳回（电子流直送填单人）」还是「逐级驳回（退回上一审批环节）」，重报路径只能靠猜。 -->
          <el-table-column label="驳回类型" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.rejectType" type="danger" size="small">{{ rejectTypeLabel(row.rejectType) }}</el-tag>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="当前流程" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.flowName" size="small" type="info">{{ row.flowName }}</el-tag>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="当前节点" min-width="130">
            <template #default="{ row }">
              <span v-if="row.currentNode" style="color:#409eff">{{ row.currentNode }}</span>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="voucherNo" label="凭证号" width="140" />
          <el-table-column label="操作" width="430" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click.stop="b.openDetail(row)">详情</el-button>
              <!-- WITHDRAWN（撤回）按「撤回 ≡ 退回草稿」处理：否则前端只剩「详情/追溯」，
                   后端 returnToEdit / voidBill 也只认 DRAFT/REJECTED ⇒ 撤回的单既不能改、不能重提、
                   也不能作废，成为永久脏数据且白占单号与预算。 -->
              <el-tooltip v-if="editableStatus(row)" content="载入表单修改，重新提交走新流程实例" placement="top">
                <el-button link size="small" type="primary" @click.stop="b.loadBillForEdit(row)">{{ row.status === 'DRAFT' ? '编辑' : '编辑重提' }}</el-button>
              </el-tooltip>
              <el-button v-if="row.status === 'DRAFT' || row.status === 'WITHDRAWN'" link size="small" type="danger" @click.stop="b.voidBill(row)">作废</el-button>
              <el-button v-if="row.status === 'APPROVING'" link size="small" type="warning" @click.stop="b.withdraw(row)">撤回</el-button>
              <!-- E-10/提单人的催办入口（服务端按发起人校验 + 同一环节 24 小时只催一次） -->
              <el-tooltip v-if="row.status === 'APPROVING'" content="给当前办理人发一次提醒；同一环节 24 小时内只催一次" placement="top">
                <el-button link size="small" type="warning" @click.stop="b.urge(row)">催办</el-button>
              </el-tooltip>
              <el-button v-if="row.status === 'APPROVED'" link size="small" type="primary" @click.stop="b.copyBill(row)">复制</el-button>
              <!-- 反审核（配套 · 单据反操作链第二环）：APPROVED → DRAFT，前置校验在服务层 -->
              <el-tooltip v-if="row.status === 'APPROVED'" content="退回草稿修改重提（已制证/付款已执行/批次在途/存在有效勾稽时服务端拒绝）" placement="top">
                <el-button link size="small" type="danger" @click.stop="b.unaudit(row)">反审核</el-button>
              </el-tooltip>
              <el-button v-if="row.billType === 'APPLY' && row.status === 'APPROVED'" link size="small" type="warning" @click.stop="b.closeApply(row)">关闭</el-button>
              <el-button v-if="row.billType === 'APPLY' && row.status === 'APPROVED'" link size="small" type="danger" @click.stop="b.terminateApply(row)">终止报销</el-button>
              <el-button v-if="row.voucherNo" link size="small" type="success" @click.stop="b.openVoucher(row)">凭证</el-button>
              <!-- 甲：借款/还款等走本共享工作台的单据行内原本只有 详情/凭证，无七档联查入口 -->
              <el-button link size="small" @click.stop="openTrace(row)">追溯</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-tab-pane>

    <!-- 凭证预览 / 记账 -->
    <el-tab-pane label="凭证" name="voucher">
      <el-card v-if="!b.voucher.id" header="凭证预览">
        <el-empty description="请在我的单据中选择带凭证号的单据查看" />
      </el-card>
      <el-card v-else :header="`凭证 ${b.voucher.voucherNo}（${b.statusLabel(b.voucher.status)}）`">
        <el-alert type="info" :closable="false" :title="`单据：${b.selected.billNo} ${b.selected.title}`" style="margin-bottom: 8px" />
        <el-table :data="b.voucherLines" size="small" border>
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column prop="summary" label="摘要" />
          <el-table-column prop="subjectCode" label="科目码" width="90" />
          <el-table-column prop="subjectName" label="科目" width="140" />
          <el-table-column label="方向" width="70">
            <template #default="{ row }">
              <el-tag :type="row.direction === 'DEBIT' ? 'danger' : 'success'" size="small">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ b.fmt(row.amount) }}</template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 10px">
          <el-button type="primary" :disabled="b.voucher.status === 'POSTED'" @click="b.postVoucher()">记账（过账）</el-button>
          <span style="margin-left: 12px; color: #909399">借方合计 {{ b.fmt(b.voucher.debitTotal) }} / 贷方合计 {{ b.fmt(b.voucher.creditTotal) }} / 平衡 {{ b.voucher.isBalanced === 1 ? '是' : '否' }}</span>
        </div>
      </el-card>
    </el-tab-pane>
  </el-tabs>

  <!-- 借款单选择（勾选 + 可改冲抵金额 + 底部【确定】，统一走 CandidatePickerDialog） -->
  <CandidatePickerDialog v-model="b.loanDialog" title="选择借款单冲抵"
                        :rows="b.loanCandidates" max-field="availableBalance"
                        extra-prop="title" extra-label="标题"
                        no-label="借款单号" amount-label="借款额" max-label="可冲抵余额"
                        input-label="本次冲抵金额"
                        :picked-ids="loanOffsetIds"
                        @confirm="b.confirmLoanPicks" />

  <!-- 事前申请选择（关联金额可改，支持部分关联） -->
  <CandidatePickerDialog v-model="b.applyDialog" title="选择事前申请单"
                        :rows="b.applyCandidates" max-field="totalAmount" :show-max-column="false"
                        extra-prop="title" extra-label="标题"
                        no-label="申请单号" amount-label="申请金额"
                        input-label="本次关联金额"
                        :picked-ids="relationIds"
                        @confirm="b.confirmApplyPicks" />

  <!-- 单据详情抽屉 -->
  <el-drawer v-model="b.detailVisible" :title="`单据详情 ${b.detailData.bill.billNo || ''}`" size="560px">
    <template v-if="b.detailData.bill.id">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单号">{{ b.detailData.bill.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ b.statusLabel(b.detailData.bill.status) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ b.billTypeLabel(b.detailData.bill.billType) }}{{ b.detailData.bill.billSubType ? ' / ' + b.billSubTypeLabel(b.detailData.bill.billSubType) : '' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ b.fmt(b.detailData.bill.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ b.detailData.bill.applyUserName }}</el-descriptions-item>
        <el-descriptions-item label="申请日期">{{ b.detailData.bill.applyDate }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ b.detailData.bill.title }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ b.detailData.bill.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="凭证号" :span="2">{{ b.detailData.bill.voucherNo || '—' }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin: 14px 0 6px">费用明细</h4>
      <el-table :data="b.detailData.lines" size="small" border>
        <el-table-column prop="itemName" label="费用项" :formatter="(row, col, v) => v || (row.itemId != null ? '费用项#' + row.itemId : '—')" />
        <el-table-column prop="amount" label="金额" width="100" :formatter="(r,c,v) => b.fmt(v)" />
        <el-table-column prop="taxAmount" label="税额" width="90" :formatter="(r,c,v) => b.fmt(v)" />
        <!-- 招待三项回显（真实列优先，缺失回退行 ext_json） -->
        <el-table-column v-if="hasEntertainLine" label="接待对象" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ entertainOfLine(row).target || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="hasEntertainLine" label="人数" width="70" align="right">
          <template #default="{ row }">{{ entertainOfLine(row).count ?? '—' }}</template>
        </el-table-column>
        <el-table-column v-if="hasEntertainLine" label="人均标准" width="95" align="right">
          <template #default="{ row }">{{ entertainOfLine(row).perCapita != null ? b.fmt(entertainOfLine(row).perCapita) : '—' }}</template>
        </el-table-column>
        <el-table-column label="发票号" width="150">
          <template #default="{ row }">{{ b.invOf(row)?.invoiceNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
      </el-table>

      <template v-if="b.detailData.attaches.length">
        <h4 style="margin: 14px 0 6px">发票 / 附件</h4>
        <el-table :data="b.detailData.attaches" size="small" border>
          <el-table-column label="文件名" min-width="180">
            <template #default="{ row }">{{ row.fileName || '—' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.attachType === 'INVOICE'" type="success" size="small">发票</el-tag>
              <el-tag v-else type="info" size="small">附件</el-tag>
            </template>
          </el-table-column>
          <!-- 上传人显姓名（uploadByName 服务端批量反查 sys_user），空显「—」不显裸 id -->
          <el-table-column label="上传人" width="90">
            <template #default="{ row }">{{ row.uploadByName || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="previewDetailAttach(row)">预览</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="b.detailData.shares.length">
        <h4 style="margin: 14px 0 6px">分摊信息</h4>
        <el-table :data="b.detailData.shares" size="small" border>
          <el-table-column label="成本中心" width="120">
            <template #default="{ row }">{{ ccName(row.costCenterId) }}</template>
          </el-table-column>
          <el-table-column label="项目" width="120">
            <template #default="{ row }">{{ projName(row.projectId) }}</template>
          </el-table-column>
          <el-table-column prop="shareAmount" label="分摊金额" width="110" :formatter="(r,c,v) => b.fmt(v)" />
        </el-table>
      </template>

      <template v-if="b.detailData.travels.length">
        <h4 style="margin: 14px 0 6px">行程信息</h4>
        <el-table :data="b.detailData.travels" size="small" border>
          <el-table-column prop="travelerName" label="出行人" />
          <el-table-column label="交通" width="80">
            <template #default="{ row }">{{ b.trafficTypeLabel(row.trafficType) }}</template>
          </el-table-column>
          <el-table-column prop="hotelAmount" label="住宿费" width="90" :formatter="(r,c,v) => b.fmt(v)" />
          <el-table-column prop="totalAmount" label="小计" width="90" :formatter="(r,c,v) => b.fmt(v)" />
        </el-table>
      </template>

      <!-- 审批流程轨迹 -->
      <template v-if="b.detailData.wfTrace.length">
        <h4 style="margin: 14px 0 6px">审批流程轨迹</h4>
        <el-timeline>
          <el-timeline-item
            v-for="t in b.detailData.wfTrace"
            :key="t.id"
            :timestamp="t.operTime"
            :type="wfActionType(t.action)"
            placement="top"
          >
            <div class="trace-node">
              <span class="trace-node-name">{{ t.nodeName || '—' }}</span>
              <el-tag :type="wfActionType(t.action)" size="small" style="margin-left: 8px">{{ wfActionLabel(t.action) }}</el-tag>
              <span class="trace-user">{{ t.operUserName || '—' }}</span>
              <span v-if="t.targetUserId" class="trace-target"> → 转办至用户{{ t.targetUserId }}</span>
            </div>
            <div v-if="t.comment" class="trace-comment">{{ t.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
      <template v-else-if="b.detailData.bill.id && b.detailData.bill.status !== 'DRAFT'">
        <el-empty description="暂无审批轨迹" :image-size="60" style="margin-top: 14px" />
      </template>

      <div style="margin-top: 16px; text-align: right">
        <el-button @click="printVisible = true">打印单据</el-button>
        <el-button type="primary" @click="b.copyBill(b.detailData.bill)">复制单据</el-button>
      </div>
    </template>
  </el-drawer>

  <PrintDialog v-model:visible="printVisible" mode="bill" :bill="b.detailData.bill" :bill-lines="printLines" />

  <!-- 文件预览器 -->
  <FileViewer ref="fileViewerRef" />

  <!-- 全链路追溯抽屉（单据→付款→批次→凭证→档案），与 ExpenseBillListTab 同组件复用 -->
  <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { reactive, computed, ref, watch } from 'vue'
import { useBill } from '../../composables/useBill'
import UploadButton from '../UploadButton.vue'
import FileViewer from '../FileViewer.vue'
import FlowProgressPanel from './FlowProgressPanel.vue'
import BillTraceDrawer from '../BillTraceDrawer.vue'
import CandidatePickerDialog from './CandidatePickerDialog.vue'
import PrintDialog from '../PrintDialog.vue'
import { fileApi, imageApi, flowApi } from '../../api'
import { Picture, Document } from '@element-plus/icons-vue'
import { wfActionLabel, wfActionType } from '@/constants/enums.js'
import { entertainOfLine, hasEntertainLines } from '@/utils/format'
import { attachKindOf, ATTACH_ACCEPT } from '@/utils/attachKind'
import { INVOICE_TYPES } from '../../constants/invoiceType'
import { rejectTypeLabel } from '@/constants/billStatus.js'
import { ElMessage } from 'element-plus'
import { commonRules } from '../../composables/useFormRules'

const props = defineProps({
  billType: { type: String, required: true },
  subType: { type: String, default: '' },
  claim: { type: Boolean, default: false },
  travel: { type: Boolean, default: false },
  pay: { type: Boolean, default: false },
  // 提交前钩子：允许页面同步 extJson/明细行后再校验提交（返回 false 阻止）
  beforeSubmit: { type: Function, default: null },
  // 保存草稿前钩子（与 beforeSubmit 同形）。顶层列位的同步逻辑若只挂在提交钩子上，
  // 而「保存草稿」按钮直调 b.saveDraft() 完全绕过它 ⇒ 草稿态三单环节列（po_id/po_receipt_id/contract_id）
  // 为空，被匹配台/手工关联/付款齐套校验读到就误判「缺环节」。此处补一个对称的前置钩子。
  beforeSaveDraft: { type: Function, default: null },
  // 关联事前申请后钩子：允许页面把申请单费用详情/分摊带入本单
  onApplyPick: { type: Function, default: null },
  // 右侧卡片「往来单位」变更钩子（与主表单同一套派生逻辑，由宿主页面注入）
  onCounterPick: { type: Function, default: null },
})

// reactive({...}) 会把返回对象中的 ref 全部深解包，slot v-bind 传参后可直接读写（form 为共享响应式代理）
// `v-bind="b"` 每次渲染对 b 做浅拷贝 ⇒ 页面侧「新赋的顶层键」既不进 b、也不触发重渲染。
// 凡是页面自建的列表状态必须在容器上预声明，页面侧只对该数组做原地增删（splice），靠引用共享回写。
const b = reactive({
  ...useBill({ billType: props.billType, subType: props.subType, claim: props.claim, travel: props.travel, pay: props.pay, onApplyPick: props.onApplyPick }),
  // 采购订单/收货单候选（仅付款 SETTLE 子类型查询，见 CorpPay.loadPos/loadReceipts）
  pos: [],
  receipts: [],
})

// /已入账的借款单/申请单 id —— 候选选择器里置灰，防同一张单重复入账
const loanOffsetIds = computed(() => (b.form?.loanOffsets || []).map((o) => o.loanBillId))
const relationIds = computed(() => (b.form?.relations || []).map((r) => r.sourceBillId))

// 全链路追溯抽屉状态（行 billType 直传 biz-type，与抽屉 (bizType,bizId) 关联点同一规则）
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)

const printVisible = ref(false)
const printLines = computed(() => (b.detailData?.lines || []).map((l) => ({
  ...l,
  expenseItemName: l.expenseItemName || l.itemName || '',
  summary: l.summary || l.remark || '',
})))
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = row.billType || null
  traceVisible.value = true
}

/** 发票行防重：同单内发票号重复会在票覆盖/核销合计中虚增（同一张票被计两次），
 *  发票号失焦即校验，重复则清空本行发票号并提示（保留行供删除或改号）。 */
function onInvoiceNoChange(row) {
  const no = (row.invoiceNo || '').trim()
  if (!no) return
  const dup = (b.form.invoices || []).filter((x) => x !== row && (x.invoiceNo || '').trim() === no)
  if (dup.length) {
    row.invoiceNo = ''
    ElMessage.warning(`发票号 ${no} 在本单据中已存在（${dup.length} 行），请勿重复录入；如需调整请修改原行或删除本行`)
  }
}

/**
 * 借款单点「提交审批」后零可见反馈，只能刷新页面或去「我的单据」查才知道
 * 到底提上去没有。原 `doSubmit` 是 `b.submit()` 即发即忘 —— 页面不参与结果、读数一行不变，
 * "提交成功""被校验拦下""服务端报错"三种结果在屏幕上同形。现在：
 *   ① await 整条提交链，并用可复核的信号判成败（useBill.submit 只在 billApi.submit 返回成功后
 *      才把 activeTab 切到 my 并 focusRow ⇒ 页首流程进度读的就是这张单的最新落点）；
 *   ② 结果写成一条常驻横幅（3 秒 toast 会自己消失，页面却什么都没变 ⇒ 才算"零可见反馈"）；
 *   ③ 成功后主动 refreshProgress()，当场把「当前节点／已发生几节」刷出来，不再要求人手刷新。
 * 原因文案由 useBill 那一份给（缺项/预算/合规预检/首审未点名），submit 每拦一次写进 submitHint 一处，
 * 这里在失败分支原样带进横幅（⑤-Ⅳa）——toast 三秒就消失，"为什么没提上去"必须留在屏上可读。
 */
/** 可载入编辑的集合 = 草稿 / 已驳回 / 已撤回（撤回等同退回草稿，见台账） */
function editableStatus(row) {
  return row.status === 'DRAFT' || row.status === 'REJECTED' || row.status === 'WITHDRAWN'
}

/**
 * 一次点击只允许一条写链。b.submitting 只罩住 useBill.submit() 那一段——
 * 前置本页校验、以及提交成功后的列表回读＋进度刷新（要好几秒）都落在它外面，
 * 那几秒里按钮恢复可点，用户按不动的直觉再点一次，服务端就按「仅草稿可提交」拒掉，
 * 于是"我自己把单子点坏了"是错觉、实际是被二次点击拦下。这里把罩子拉到整条点击跨度。
 */
const pendingWrite = ref(false)

/**
 * 保存草稿入口。先跑 beforeSaveDraft（页面对齐顶层列位），再写入数据库。
 * 不做返回值拦截——草稿允许半成品，钩子返回 false 也照常保存（与 doSubmit 的语义区别）。
 */
async function doSaveDraft() {
  if (pendingWrite.value) return
  pendingWrite.value = true
  try {
    if (props.beforeSaveDraft) {
      try { await props.beforeSaveDraft(b) } catch (e) { /* 钩子异常不阻断保存 */ }
    }
    await b.saveDraft()
  } finally { pendingWrite.value = false }
}

const submitOutcome = ref(null)
// 单据信息区（公司/部门）曾用装饰性红星——el-form-item required 无 prop 不触发任何校验，
// 缺公司/部门时 useBill 兜底只弹一句 toast，用户看不到是哪一项缺。现挂真 prop+rules，
// doSubmit 入口先跑本区校验，红字直接落在字段上。
const billInfoFormRef = ref(null)
const billExtraFormRef = ref(null)
const billInfoRules = {
  companyId: commonRules.select('请选择公司'),
  deptId: commonRules.select('请选择部门'),
}
// 合同/往来单位辅助区：选填（clearable），customerId 由主表单校验，此处不强制
const billExtraRules = { contractId: [], customerId: [] }
async function doSubmit() {
  if (pendingWrite.value) return
  pendingWrite.value = true
  try { await runSubmit() } finally { pendingWrite.value = false }
}
async function runSubmit() {
  submitOutcome.value = null
  if (billInfoFormRef.value) {
    try { await billInfoFormRef.value.validate() } catch { return }
  }
  if (props.beforeSubmit) {
    const r = await props.beforeSubmit(b)
    if (r === false) {
      // 页面 beforeSubmit 的具体拦截原因只走 3 秒 toast 的话（闪过即没），
      // 常驻横幅只有一句写死的「本页校验未通过」，用户根本不知道哪一项没过。
      // 页面各拦截点已同步写 b.submitHint，这里原样带进横幅。
      const why = String(b.submitHint || '').trim()
      submitOutcome.value = {
        ok: false,
        title: '未提交：本页校验未通过',
        desc: (why ? `未通过的校验项：${why}。\n` : '')
          + '本次没有向服务端发起提交，单据仍是草稿态，页首流程进度不变。请按提示补正后再次点击「提交审批」。',
      }
      return
    }
  }
  // 兜底校验：提交前再查一次发票号重复（防止绕过失焦校验的路径），重复即中止提交
  const nos = (b.form.invoices || []).map((x) => (x.invoiceNo || '').trim()).filter(Boolean)
  const invDup = nos.filter((n, i) => nos.indexOf(n) !== i)
  if (invDup.length) {
    ElMessage.error('发票行发票号重复：' + [...new Set(invDup)].join('、') + '，请删除重复发票行后重新提交')
    submitOutcome.value = {
      ok: false,
      title: '未提交：发票号重复',
      desc: `重复发票号 ${[...new Set(invDup)].join('、')}。已保留本单草稿内容，删除重复发票行后可重新提交。`,
    }
    return
  }
  // 一致性校验：每个 INVOICE 附件都应有对应发票明细行（1 附件↔1 发票），
  // 防止删附件不联动/手动乱序后，票面数据缺失还放行提交（票覆盖、影像核验都会缺票）。
  const invAttaches = (b.form.attaches || []).filter((a) => a && a.attachType === 'INVOICE')
  if (invAttaches.length) {
    const invFileIds = new Set((b.form.invoices || []).map((x) => String((x && x.fileId) || '')))
    const orphans = invAttaches.filter((a) => !invFileIds.has(String(a.fileId || '')))
    if (orphans.length) {
      const names = orphans.map((a) => a.fileName || ('#' + a.fileId)).join('、')
      ElMessage.error(`附件与发票明细不一致：${names} 没有对应的发票明细行，请补录或删除多余附件后重新提交`)
      submitOutcome.value = {
        ok: false,
        title: '未提交：附件与发票明细数量不一致',
        desc: `以下 INVOICE 附件没有对应的发票明细行：${names}。请在「已解析发票 / 手工录入」区补齐发票行（或删除多余附件）后重新提交。`,
      }
      return
    }
  }
  const billId = b.form.bill.id || null
  await b.submit()
  const cur = b.focusedBill || b.form.bill || {}
  const st = cur.status || b.form.bill.status
  const ok = b.activeTab === 'my' || (st && st !== 'DRAFT')
  if (ok) {
    await refreshProgress()
    submitOutcome.value = {
      ok: true,
      title: `已提交审批：${cur.billNo || b.form.bill.billNo || ('单据 id ' + (billId || '—'))}`,
      desc: `单据状态「${b.statusLabel(st) || st || '已提交'}」，页首流程进度已刷新到当前节点（已发生节数与各节办理人以进度面板为准）。如需换办理人或撤回，请到「我的单据」行内操作。`,
    }
  } else {
    const why = String(b.submitHint || '').trim()
    // -05 同族：超时通道（服务端多半已受理、只是响应没回来）不能被写成"未受理"，
    // 那句话与库内事实相反，正是诱导用户重复提单的那句 ⇒ 按原文分档出话。
    const pending = /已提交，处理中|结果未知|仍在处理/.test(why)
    submitOutcome.value = {
      ok: false,
      title: pending ? '提交结果待确认：服务端处理较慢' : '本次提交未生效',
      desc: (why ? `未通过的原因：\n${why}\n` : '')
        + (pending
          ? '服务端多半已受理本次提交（超时≠失败）。不要重复点【提交审批】；请到「我的单据」刷新核对这张单的状态与页首流程进度，确认确实没进入审批流后再重提。'
          : '服务端未受理本次提交，单据没有进入审批流（缺项、预算/合规校验、首审办理人未点名等原因见上行），页首流程进度与「我的单据」读数都不应发生变化；若与提示不一致请以「我的单据」列表读数为准并反馈。'),
    }
  }
}

// ID → 名称查找（分摊详情）
function ccName(id) { const c = b.costCenters.find((x) => x.id === id); return c ? (c.ccName || '—') : '—' }
function projName(id) { const p = b.projects.find((x) => x.id === id); return p ? (p.projectName || '—') : '—' }

// 仅当明细里确实存在招待三项时才追加这三列（非招待类单据不凭空多空白列）
const hasEntertainLine = computed(() => hasEntertainLines(b.detailData.lines))

// 部门按公司过滤
const filteredDepts = computed(() => {
  if (!b.form.bill.companyId) return b.depts
  return b.depts.filter((d) => !d.companyId || d.companyId === b.form.bill.companyId)
})
function onCompanyChange() {
  // 公司变更后，如果当前部门不属于该公司则清空
  if (b.form.bill.deptId && !filteredDepts.value.find((d) => d.id === b.form.bill.deptId)) {
    b.form.bill.deptId = null
  }
}

defineExpose({ b })

// 缩略图预览 + OCR 角标
const fileViewerRef = ref(null)
const thumbUrls = reactive({})   // fileId → download URL
const ocrStatus = reactive({})   // fileId → 'idle' | 'processing' | 'done' | 'failed'
const IMG_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']

function isImage(name) {
  if (!name) return false
  const ext = (name.split('.').pop() || '').toLowerCase()
  return IMG_EXTS.includes(ext)
}
/** 该附件是否"发票影像"（要识别）：后端 attachType 优先，缺失按扩展名（规则见 utils/attachKind） */
function isInvoiceAttach(a) {
  return attachKindOf(a) === 'INVOICE'
}
function getFileExt(name) {
  if (!name) return ''
  return (name.split('.').pop() || '').toUpperCase()
}

const previewList = computed(() => {
  return b.form.attaches.filter((a) => isImage(a.fileName)).map((a) => thumbUrls[a.fileId]).filter(Boolean)
})

async function loadThumb(att) {
  if (!att || !att.fileId || thumbUrls[att.fileId]) return
  try {
    const tk = await fileApi.createToken(att.fileId)
    thumbUrls[att.fileId] = '/api/tenant/system/file/download?token=' + tk
  } catch (_) { /* ignore */ }
}

function onAttachUploaded(rec) {
  b.onAttachUploaded(rec)
  if (rec && rec.id) {
    ocrStatus[rec.id] = 'idle'
    loadThumb({ fileId: rec.id, fileName: rec.fileName })
  }
}

// 当已有附件时（编辑模式加载），批量加载缩略图
function loadAllThumbs() {
  b.form.attaches.forEach((a) => {
    if (a.fileId && !ocrStatus[a.fileId]) ocrStatus[a.fileId] = 'idle'
    loadThumb(a)
  })
}

// 延迟调用 loadAllThumbs（等 b.form.attaches 被填充）
setTimeout(() => { loadAllThumbs() }, 500)

function previewOne(a) {
  if (fileViewerRef.value) fileViewerRef.value.open([a.fileId], '文件预览')
}
function previewAll() {
  if (fileViewerRef.value) fileViewerRef.value.open(b.form.attaches.map((a) => a.fileId), '全部附件')
}
function previewDetailAttach(a) {
  if (fileViewerRef.value && a && a.fileId) fileViewerRef.value.open([a.fileId], '发票/附件：' + (a.fileName || ''))
}
function removeAttach(i) {
  const a = b.form.attaches[i]
  if (!a) return
  const fid = a.fileId == null ? '' : String(a.fileId)
  if (fid) {
    delete thumbUrls[a.fileId]
    delete ocrStatus[a.fileId]
  }
  b.form.attaches.splice(i, 1)
  // + 10-20：删除附件必须同时带走它解析出的发票行与勾选生成的费用明细行。
  // 旧实现只删发票行，明细行仍留在 form.lines，保存后成为「没有附件却有条目」的脏单。
  if (!fid) return
  const removedInvs = (b.form.invoices || []).filter((x) => String(x.fileId || '') === fid)
  if (removedInvs.length) {
    b.form.invoices = (b.form.invoices || []).filter((x) => String(x.fileId || '') !== fid)
  }
  const lineRefs = new Set(removedInvs.map((x) => x.lineRef).filter(Boolean))
  const beforeLines = (b.form.lines || []).length
  b.form.lines = (b.form.lines || []).filter((l) => {
    if (lineRefs.has(l)) return false
    const meta = b.invOf(l)
    return !(meta && String(meta.fileId || '') === fid)
  })
  const removedLines = beforeLines - b.form.lines.length
  if (removedInvs.length || removedLines) {
    const parts = []
    if (removedInvs.length) parts.push(`${removedInvs.length} 行发票`)
    if (removedLines) parts.push(`${removedLines} 行费用明细`)
    ElMessage.success(`已同步移除由该附件解析的 ${parts.join('、')}`)
  }
}

async function triggerOcr(a) {
  if (!a || !a.fileId) return
  // 普通附件不识别：按钮原本只对图片显示，但拖拽/旧数据仍可能触发，这里兜一句明确原因
  if (!isInvoiceAttach(a)) {
    ElMessage.warning('该附件不是发票影像（' + (getFileExt(a.fileName) || '未知格式') + '），无需识别')
    return
  }
  ocrStatus[a.fileId] = 'processing'
  try {
    // 附件维度识别：后端按 fileId 解析所属影像批次
    // 原先误把 fileId 当 batchId 调 /image/batch/{id}/ocr，必然 "影像批次不存在" 而静默失败
    await imageApi.ocrByFile(a.fileId)
    ocrStatus[a.fileId] = 'done'
    ElMessage.success('OCR 识别完成')
  } catch (e) {
    ocrStatus[a.fileId] = 'failed'
    // 失败必须回显后端原因（如"图片发票需配置图像识别通道"），不再只显示一个"失败"角标
    ElMessage.error((e && (e.msg || e.message)) || 'OCR 识别失败')
  }
}

/**
 * 附件角标状态。取值顺序（后者只在前者缺失时兜底）：
 *  ① 非发票文件（Office/压缩包/文本…）→ 'other'，不参与识别，绝不显示"待识别"
 *  ② 本次会话内的识别进度 ocrStatus（点了「OCR识别」或上传时后端已解析）
 *  ③ 已解析出发票行（重新进页面 ocrStatus 已清空，用发票行的 parsed 反推，避免明明已识别却显示待识别）
 *  ④ 其余发票文件 → 'idle'（待识别）
 */
function ocrStateOf(a) {
  if (!a) return 'other'
  if (!isInvoiceAttach(a)) return 'other'
  const s = ocrStatus[a.fileId]
  if (s) return s
  const inv = (b.form.invoices || []).find((x) => String(x.fileId || '') === String(a.fileId || ''))
  if (inv) return inv.parsed ? 'done' : 'failed'
  return 'idle'
}
function ocrBadgeClass(a) {
  const s = ocrStateOf(a)
  return {
    idle: 'ocr-idle', processing: 'ocr-processing', done: 'ocr-done', failed: 'ocr-failed', other: 'ocr-other',
  }[s] || 'ocr-idle'
}
function ocrBadgeText(a) {
  const s = ocrStateOf(a)
  return {
    idle: '待识别', processing: '识别中', done: '已识别', failed: '失败', other: '普通附件',
  }[s] || '待识别'
}

// 流程进度卡片：跟着「当前这张单」走（详情抽屉 > 当前页签的选中行/正在编辑的单，见 useBill.focusedBill）。
// 只看 detailData 的话：抽屉开过一次就永久停在上一张单上，草稿态还会把步骤条钉死在第 1 步。
const focusBill = computed(() => b.focusedBill)

// 审批链全景：单据进入流程（有 procInstId）后给出全部节点与当前所在节点
const chainNodes = ref([])
const chainLoading = ref(false)
const chainAt = ref('')
const chainTick = ref(0)
const chainProcInstId = computed(() => focusBill.value?.procInstId || '')
watch([chainProcInstId, chainTick], async ([id]) => {
  if (!id) { chainNodes.value = []; chainAt.value = ''; return }
  chainLoading.value = true
  try {
    const res = await flowApi.chain(id, { silent: true }).catch(() => null)
    if (chainProcInstId.value !== id) return // 切单竞态：晚到的响应丢弃
    chainNodes.value = Array.isArray(res) ? res : []
    chainAt.value = new Date().toTimeString().slice(0, 8)
  } finally {
    chainLoading.value = false
  }
}, { immediate: true })

// 刷新进度：重读单据状态（列表/详情）+ 重取审批链，审批人刚办完时不必整页刷新
async function refreshProgress() {
  const bill = focusBill.value
  if (!bill?.id) { chainTick.value++; return }
  if (b.detailVisible) await b.openDetail({ id: bill.id }).catch(() => {})
  else await b.loadBills().catch(() => {})
  chainTick.value++
}

// 审批动作 → 中文标签 / timeline 类型：名称表办结到 constants/enums
</script>

<style scoped>
/* 结果横幅与进度面板/页签之间留一档间距，标题按中文行长换行 */
.bw-outcome { margin-bottom: 12px; }
.bw-outcome :deep(.el-alert__title) { font-size: 14px; font-weight: 600; }
.bw-outcome :deep(.el-alert__description) { font-size: 12px; line-height: 20px; margin-top: 4px; white-space: pre-line; }
.attach-row { display: flex; align-items: center; gap: 8px; }
.attach-hint { margin-top: 6px; font-size: 12px; color: #909399; line-height: 18px; }
.attach-name { font-size: 12px; color: #67c23a; }
.trace-node { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.trace-node-name { font-weight: 600; color: var(--app-text, #303133); }
.trace-user { color: var(--app-text-secondary, #606266); margin-left: 4px; }
.trace-target { color: #e6a23c; font-size: 12px; }
.trace-comment { margin-top: 4px; color: var(--app-text-secondary, #606266); font-size: 12px; background: var(--app-bg, #f5f7fa); padding: 4px 8px; border-radius: 4px; }

/* 缩略图网格 */
.thumb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 10px;
  margin-top: 10px;
}
.thumb-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: var(--app-hover, #fafafa);
}
.thumb-img-wrap {
  position: relative;
  width: 100%;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--app-bg, #f5f7fa);
}
.thumb-img {
  width: 100%;
  height: 100%;
  cursor: pointer;
}
.thumb-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #c0c4cc;
  font-size: 12px;
}
.ocr-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  line-height: 16px;
}
.ocr-idle { background: #909399; }
.ocr-processing { background: #e6a23c; animation: ocr-pulse 1s infinite; }
.ocr-done { background: #67c23a; }
.ocr-failed { background: #f56c6c; }
/* 普通附件（Office/压缩包等）：不参与识别，用低饱和的蓝灰区分于"待识别"的灰 */
.ocr-other { background: #7f8c9b; }
@keyframes ocr-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
.thumb-name {
  padding: 4px 6px;
  font-size: 11px;
  color: var(--app-text-secondary, #606266);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.thumb-actions {
  display: flex;
  justify-content: center;
  gap: 2px;
  padding: 2px 0 4px;
}
</style>
