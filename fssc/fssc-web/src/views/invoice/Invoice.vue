<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <el-tabs v-model="tab">
      <!-- ============ 7.1 发票验真（发票池 + 实时/定时验真） ============ -->
      <el-tab-pane v-if="canList" label="发票池" name="pool">
        <div class="query-bar">
          <el-input v-model="q.invoiceNo" placeholder="发票号码" clearable style="width: 130px" @keyup.enter="loadPool(1)" />
          <el-input v-model="q.invoiceCode" placeholder="发票代码" clearable style="width: 130px" @keyup.enter="loadPool(1)" />
          <el-input v-model="q.sellerName" placeholder="销售方" clearable style="width: 140px" @keyup.enter="loadPool(1)" />
          <el-input v-model="q.billNo" placeholder="业务单号" clearable style="width: 140px" @keyup.enter="loadPool(1)" />
          <el-date-picker v-model="q.invoiceDate" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开票起" end-placeholder="开票止" style="width: 240px" />
          <el-date-picker v-model="q.scanDate" type="daterange" value-format="YYYY-MM-DD" start-placeholder="录入起" end-placeholder="录入止" style="width: 240px" />
          <el-select v-model="q.verifyStatus" placeholder="验真状态" clearable style="width: 120px">
            <el-option label="待验真" value="NONE" />
            <el-option label="验真中" value="PENDING" />
            <el-option label="验真通过" value="SUCCESS" />
            <el-option label="验真失败" value="FAILED" />
          </el-select>
          <el-select v-model="q.direction" placeholder="方向" clearable style="width: 110px">
            <el-option label="进项（收到的票）" value="INPUT" />
            <el-option label="销项（开出的票）" value="OUTPUT" />
          </el-select>
          <el-button type="primary" size="small" @click="loadPool(1)">查询</el-button>
          <el-button size="small" @click="reset">重置</el-button>
          <el-button v-permission="'inv:verify'" type="success" size="small" :disabled="!poolSelected.length" @click="verifyBatch">
            批量验真（{{ poolSelected.length }}）
          </el-button>
          <el-button v-permission="'inv:audit'" type="warning" size="small" @click="openAudit">占用对账</el-button>
      <el-button v-permission="'inv:export'" size="small" @click="exportCsv">导出 CSV</el-button>
          <span style="margin-left: auto; font-size: 13px; color: #909399">
            定时验真：每 5 分钟扫描待验发票，仅「有影像且非手工补录」的发票进队列（无影像票不参与定时验真，需走【实时验真】）；外部验真通道未启用时不判定通过（保持未验真/验真中）
          </span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table class="cm-target-invoice" :data="rows" size="small" stripe v-else max-height="480" row-key="id"
                  @selection-change="(s) => (poolSelected = s)" @row-dblclick="handleView">
          <el-table-column type="selection" width="40" reserve-selection :selectable="(row) => dir(row) === 'INPUT' && row.verifyStatus !== 'SUCCESS'" />
          <el-table-column prop="invoiceNo" label="发票号码" width="150"  sortable />
          <el-table-column prop="invoiceCode" label="发票代码" width="120"  sortable />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ invTypeLabel(row.invoiceType) }}</template>
          </el-table-column>
          <el-table-column label="方向" width="80" sortable>
            <template #default="{ row }">
              <el-tag :type="dir(row) === 'OUTPUT' ? 'warning' : 'success'" size="small" effect="plain">
                {{ dir(row) === 'OUTPUT' ? '销项' : '进项' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sellerName" label="销售方" min-width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="invoiceDate" label="开票日期" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
          <el-table-column prop="amount" label="价税合计" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="验真状态" width="110">
            <template #default="{ row }">
              <el-tag :type="vsType(row.verifyStatus)" size="small">{{ vsLabel(row.verifyStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="verifyTime" label="验真时间" width="160"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
          <el-table-column label="操作" width="430" fixed="right">
            <template #default="{ row }">          <InlineActions :visible="true" @view="handleView(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.imgFileId" link type="success" size="small" @click="viewImage(row)">查看影像</el-button>
              <el-button v-if="dir(row) === 'INPUT' && row.verifyStatus !== 'SUCCESS'" v-permission="'inv:verify'" link type="primary" size="small" @click="verifyNow(row)">实时验真</el-button>
              <el-button v-if="dir(row) === 'INPUT'" v-permission="'inv:bind'" link size="small" @click="openBind(row)">关联单据</el-button>
              <el-button v-if="dir(row) === 'INPUT' && row.lockStatus === 'LOCKED_IN_USE'" v-permission="'inv:unbind'" link size="small" type="danger" @click="unbindBill(row)">解除关联</el-button>
              <el-button v-permission="'inv:line:list'" link size="small" @click="showLines(row)">明细行</el-button>
              <!-- 销项发票作废/红冲：原因必填，后端校验当月/核销引用等条件，失败原样提示 -->
              <el-button v-if="dir(row) === 'OUTPUT'" v-permission="'inv:void'" link size="small" type="danger" @click="voidOutputInvoice(row)">作废</el-button>
              <el-button v-if="dir(row) === 'OUTPUT'" v-permission="'inv:redflush'" link size="small" type="warning" @click="redFlushInvoice(row)">红冲</el-button>
              <el-popconfirm v-if="dir(row) === 'INPUT'" title="用该发票自动生成报销草稿并占用票面？" width="240" @confirm="openClaim(row)">
                <template #reference>
                  <el-button v-permission="'inv:claim'" link type="warning" size="small">生成报销草稿</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 10px; justify-content: flex-end"
          layout="total, prev, pager, next"
          :total="total" :page-size="q.pageSize" :current-page="q.pageNum"
          @current-change="loadPool"
        />
      </el-tab-pane>

      <!-- ============ 7.2 发票补录（人工录入 + 手动验真） ============ -->
      <el-tab-pane v-if="canCreate" label="发票补录" name="create">
        <el-form ref="formRef" :rules="formRules" :model="form" label-width="90px" style="max-width: 720px" size="default">
          <el-row class="cm-target-invoice" :gutter="12">
            <el-col :span="12"><el-form-item label="发票代码"><el-input v-model="form.invoiceCode" placeholder="数电票无发票代码，可留空" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="发票号码" prop="invoiceNo"><el-input v-model="form.invoiceNo" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="发票类型">
                <el-select v-model="form.invoiceType" style="width: 100%">
                  <el-option label="增值税专用发票" value="VAT_SPECIAL" />
                  <el-option label="增值税普通发票" value="VAT_NORMAL" />
                  <el-option label="电子发票" value="ELECTRONIC" />
                  <el-option label="通行费发票" value="TOLL" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发票种类">
                <el-select v-model="form.invoiceKind" style="width: 100%">
                  <el-option label="增值税专用发票" value="SPECIAL" />
                  <el-option label="增值税普通发票" value="NORMAL" />
                  <el-option label="电子发票" value="ELECTRONIC" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发票性质">
                <el-radio-group v-model="form.invoiceNature">
                  <el-radio-button value="BLUE">蓝票</el-radio-button>
                  <el-radio-button value="RED">红票</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="开票日期" prop="invoiceDate"><el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="销售方名称" prop="sellerName"><el-input v-model="form.sellerName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="销售方税号"><el-input v-model="form.sellerTaxNo" /></el-form-item></el-col>
            <!-- D3：购方信息此前只绑 form.invoiceTitle/form.taxNo，而 InvInvoice 上另有
                 buyerName/buyerTaxNo 两列（注释分别为「购方名称」「购方抬头」）语义重复。
                 结果：补录填的购方全落进 invoice_title/tax_no，buyer_name/buyer_tax_no 恒空，
                 按 buyer_name 过滤的报表一条都查不到（实测 5/5 张票 buyer_name 为空）。
                 这里两个框都双向同步，两套列同时有值，两种口径的查询都能命中。 -->
            <el-col :span="12"><el-form-item label="购买方名称" prop="invoiceTitle"><el-input v-model="form.invoiceTitle" @input="form.buyerName = form.invoiceTitle" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="购买方税号"><el-input v-model="form.taxNo" @input="form.buyerTaxNo = form.taxNo" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="未税金额" prop="netAmount"><el-input-number v-model="form.netAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="价税合计" prop="amount"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="税额" prop="taxAmount"><el-input-number v-model="form.taxAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="发票校验码"><el-input v-model="form.checkCode" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="币种" prop="currencyCode"><el-select v-model="form.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
</el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
          <el-form-item>
            <el-button v-permission="'inv:create'" type="primary" @click="saveCreate">保存补录</el-button>
            <el-button v-if="lastCreatedId" v-permission="'inv:verify'" @click="verifyLast">对刚录入发票发起验真</el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="lastCreatedId" :closable="false" type="success" show-icon
          :title="`已补录发票 #${lastCreatedId}：手工补录且无影像的发票不进定时验真队列，需点「对刚录入发票发起验真」发起；未启用外部验真通道时只置「验真中」，不判定验真通过`"
          style="max-width: 720px" />
      </el-tab-pane>

      <!-- ============ 7.3 销售开票（OTC02 INVOICE_ISSUE：登记发票并发起开票审批流） ============ -->
      <el-tab-pane v-if="canIssue" label="销售开票" name="issue">
        <el-form ref="issueFormRef" :model="issueForm" :rules="issueRules" label-width="110px" style="max-width: 720px" size="default">
          <el-alert :closable="false" type="info" show-icon style="margin-bottom: 12px"
            title="销售发票开具登记：价税分离校验（价税合计 = 未税 + 税额）通过后，自动发起开票审批（应收会计 → 财务经理）" />
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="发票号码" prop="invoiceNo"><el-input v-model="issueForm.invoiceNo" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="销售方名称" prop="sellerName"><el-input v-model="issueForm.sellerName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="购买方名称" prop="invoiceTitle"><el-input v-model="issueForm.invoiceTitle" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="开票日期" prop="invoiceDate"><el-date-picker v-model="issueForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="未税金额" prop="netAmount"><el-input-number v-model="issueForm.netAmount" :min="0" :precision="2" style="width: 100%" @change="syncIssueAmount" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="税额" prop="taxAmount"><el-input-number v-model="issueForm.taxAmount" :min="0" :precision="2" style="width: 100%" @change="syncIssueAmount" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="价税合计" prop="amount"><el-input-number v-model="issueForm.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="币种" prop="currencyCode"><el-select v-model="issueForm.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
</el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="issueForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
          <el-form-item>
            <el-button v-permission="'inv:issue'" type="primary" :loading="submitting" :disabled="submitting" @click="saveIssue">登记并发起开票审批</el-button>
            <el-button @click="resetIssue">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="lastIssueId" :closable="false" type="success" show-icon
          :title="`已登记销售发票 #${lastIssueId}，开票审批流已发起`" style="max-width: 720px" />
      </el-tab-pane>

      <!-- ============ 发票验真记录（7.1 单张实时验真 / 批量 / 定时 + 验真日志） ============ -->
      <el-tab-pane v-if="canVerifyList" label="发票验真记录" name="verify">
        <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <el-select v-model="realtimeInv" placeholder="选择待验发票（实时验真）" filterable clearable style="width: 260px">
            <el-option v-for="i in pendingInvs" :key="i.id" :label="(i.invoiceNo || '') + ' ' + (i.sellerName || '')" :value="i.id" />
          </el-select>
          <el-button v-permission="'inv:verify'" type="success" size="small" :disabled="!realtimeInv" @click="verifyRealtime">实时验真</el-button>
          <el-button v-permission="'inv:verify'" type="primary" size="small" @click="batchVerify">批量发起验真（无外部报文不判定通过）</el-button>
          <el-button size="small" @click="loadVerify">刷新</el-button>
          <span style="color: #909399; font-size: 13px">定时验真：每 5 分钟扫描待验发票（@Scheduled），有影像且非手工补录者才判定；以下为验真日志（证据链，只增不改，含请求与返回报文）</span>
        </div>
        <SkeletonList v-if="vloading" :rows="8" :cols="5" />
        <el-table :data="vlogs" size="small" stripe v-else max-height="480">
          <el-table-column prop="id" label="日志ID" width="80"  sortable />
          <el-table-column prop="invoiceNo" label="发票号码" width="130"  sortable />
          <el-table-column label="渠道" width="110">
            <template #default="{ row }">{{ channelLabel(row.channel) }}</template>
          </el-table-column>
          <el-table-column prop="result" label="结果" width="90" sortable>
            <template #default="{ row }">
              <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">{{ row.result === 'PASS' ? '通过' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="msg" label="备注" min-width="140"  sortable />
          <el-table-column prop="reqParam" label="请求参数" min-width="160" show-overflow-tooltip  sortable />
          <el-table-column label="验真人" width="90">
            <template #default="{ row }">{{ userName(row.verifyUserId) }}</template>
          </el-table-column>
          <el-table-column prop="verifyTime" label="验真时间" width="160"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
        </el-table>
        <el-pagination
          style="margin-top: 10px; justify-content: flex-end"
          layout="total, prev, pager, next"
          :total="vtotal" :page-size="vq.pageSize" :current-page="vq.pageNum"
          @current-change="(n) => { vq.pageNum = n; loadVerify() }"
        />
      </el-tab-pane>

      <!-- ============ 进项发票认证（RA-PAGE-21 2.7：原独立菜单 3075 并入；无 inv:certify 码不渲染，勿扩权） ============ -->
      <el-tab-pane v-if="canCertify" label="进项认证" name="certify" lazy>
        <InvoiceCertify />
      </el-tab-pane>
    </el-tabs>

    <!-- 明细行 -->
    <el-dialog v-model="linesVis" title="发票明细行" width="620px" destroy-on-close>
      <el-table :data="lines" size="small" border max-height="320">
        <el-table-column prop="lineNo" label="行" width="50"  sortable />
        <el-table-column prop="itemName" label="货物/服务" min-width="140"  sortable />
        <el-table-column prop="spec" label="规格" width="100"  sortable />
        <el-table-column prop="qty" label="数量" width="80"  sortable />
        <el-table-column prop="price" label="单价" width="90"  sortable />
        <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 发票影像查看器 -->
    <FileViewer ref="fileViewer" />

    <!-- 关联单据 -->
    <el-dialog v-model="bindVis" title="发票关联单据" width="420px" destroy-on-close>
      <el-form ref="bindFormRef" label-width="90px" :model="{ billId: bindBillId, occupyAmount: bindAmount }" :rules="bindRules">
        <el-form-item label="发票">{{ currentInv?.invoiceNo }}</el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="bindBillNo" placeholder="输入单据号，回车查询" @keyup.enter="searchBill" />
        </el-form-item>
        <el-form-item label="选择单据" prop="billId">
          <el-select v-model="bindBillId" filterable style="width: 100%">
            <el-option v-for="b in bills" :key="b.id" :label="b.billNo + ' ' + (b.title || '')" :value="b.id">
              <span>{{ b.billNo }} {{ b.title || '' }}</span>
              <!-- D-DEC-113：绑票前就看清本单已勾稽多少票、还差多少（三单匹配按此判超付） -->
              <span style="float:right;margin-left:16px;color:#909399;font-size:12px">
                已关联 {{ fmt(b.invoiceBound) }} ／ 缺口 {{ fmt(b.invoiceGap) }}
              </span>
            </el-option>
          </el-select>
          <div style="color:#909399;font-size:12px">候选＝本公司及本人提的单据（不含作废/终止/驳回/撤回）；他人代办的单也可在此关联，按单据号或提单人检索</div>
        </el-form-item>
        <el-form-item label="覆盖金额" prop="occupyAmount">
          <el-input-number v-model="bindAmount" :min="0" :precision="2" :max="Number(currentInv?.amount || 0)"
                           style="width: 100%" />
          <div style="color:#909399;font-size:12px">本发票计入该单据的核销/占用金额，默认为发票面额；三单匹配按此判定超付</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVis = false">取消</el-button>
        <el-button type="primary" @click="doBind">关联</el-button>
      </template>
    </el-dialog>

    <!-- 生成报销草稿（发票→CLAIM 自动填单，复用 mdApi 费用项树数据源） -->
    <el-dialog v-model="claimVis" title="生成报销草稿" width="460px" destroy-on-close>
      <el-form ref="claimFormRef" label-width="80px" :model="claimForm" :rules="formRules">
        <el-form-item label="发票">{{ claimInv?.invoiceNo }}（价税合计 {{ fmt(claimInv?.amount) }}）</el-form-item>
        <el-form-item label="费用项" prop="itemId">
          <el-select v-model="claimForm.itemId" filterable placeholder="选择报销费用项" style="width: 100%">
            <el-option v-for="i in claimItems" :key="i.id" :label="i.itemName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="claimForm.title" :placeholder="`默认：发票报销-${claimInv?.invoiceNo || claimInv?.id}`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="claimVis = false">取消</el-button>
        <el-button type="primary" :loading="claimLoading" @click="doClaim">生成并占用发票</el-button>
      </template>
    </el-dialog>

    <!-- 发票占用对账 -->
    <el-dialog v-model="auditVis" title="发票占用次数对账" width="680px" destroy-on-close>
      <div style="display:flex; gap:8px; align-items:center; margin-bottom:10px">
        <el-button v-permission="'inv:audit:fix'" type="warning" size="small" :loading="auditLoading" @click="runAuditFix">执行修复</el-button>
        <span style="color:#909399; font-size:13px">共 {{ auditRows.length }} 张发票占用次数与单据实际关联数不一致，「执行修复」将按单据重算 usedCount。</span>
      </div>
      <SkeletonList v-if="auditLoading" :rows="8" :cols="5" />
      <el-table :data="auditRows" size="small" border stripe max-height="360" v-else
                empty-text="未发现占用次数不一致的发票">
        <el-table-column prop="invoiceNo" label="发票号码" width="150"  sortable />
        <el-table-column prop="usedCount" label="系统占用数" width="110" align="center"  sortable />
        <el-table-column prop="actualCount" label="实际关联数" width="110" align="center"  sortable />
        <el-table-column prop="billNo" label="最近关联单据" min-width="140" show-overflow-tooltip  sortable />
      </el-table>
      <template #footer>
        <el-button @click="auditVis = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="INVOICE_ISSUE" />

  <CoachMark ref="invoiceCm" :guide-key="_guideKey" :selector="'.cm-target-invoice'" title="发票管理" text="发票管理页面展示发票列表，支持验真、认证和三单匹配。" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>

const { rules: formRules } = useFormRules('INVOICE')
const { rules: bindRules } = useFormRules('INVOICE_BIND')
// 第1批 P0：价税勾稽（价税合计 = 未税金额 + 税额，容差 1 分）前置到录入/开票两个表单。
// 后端 InvoiceService.requireManualHeader/create 已有同口径拦截，此处让用户录入当时就看到，
// 而不是提交后拿 400。claim 表单只涉及 itemId，不挂金额三元组，继续复用 formRules。
const entryRules = computed(() => ({ ...formRules.value, ...invoiceAmountMatch(() => form) }))
const issueRules = computed(() => ({ ...formRules.value, ...invoiceAmountMatch(() => issueForm) }))
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invoiceApi, billApi, mdApi, userApi } from '../../api'
import { http } from '../../request'
import FileViewer from '../../components/FileViewer.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useFormRules, invoiceAmountMatch } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
// RA-PAGE-21 2.7：进项认证 tab（原独立页 3075 并入为第 5 页签）
import InvoiceCertify from './InvoiceCertify.vue'
import { hasPerm } from '@/utils/perm'
// R5-D-121/122：发票类型/验真渠道词表统一走共享字典（一处定义，禁止组件内局部映射）
import { invoiceTypeLabel, invoiceChannelLabel } from '@/constants/enums.js'
import { useCurrency } from '@/composables/useCurrency'
// G14: 逐 tab 权限门控
const canList = computed(() => hasPerm('inv:list'))
const canCreate = computed(() => hasPerm('inv:create'))
const canIssue = computed(() => hasPerm('inv:issue'))
const canVerifyList = computed(() => hasPerm('inv:verify:list'))
const canCertify = computed(() => hasPerm('inv:certify'))

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// R5-13: 快捷键
useKeyboard({
  'ctrl+s': () => { try { saveCreate() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})
const invoiceCm = ref(null)

// 必填闸门（治理口径 P1）：四个写入表单各自挂 ref，提交前先跑 rules 再发请求
const formRef = ref(null)
const issueFormRef = ref(null)
const bindFormRef = ref(null)
const claimFormRef = ref(null)
const { submit: guardCreate } = useSubmitGuard(formRef)
const { submit: guardIssue } = useSubmitGuard(issueFormRef)
const { submit: guardBind } = useSubmitGuard(bindFormRef)
const { submit: guardClaim } = useSubmitGuard(claimFormRef)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const router = useRouter()

// 用户列表（用于验真人 ID → 用户名）
const users = ref([])
function userName(id) { const u = users.value.find((x) => x.id === id); return u ? (u.nickName || u.userName) : (id ? '#' + id : '—') }

// 默认 tab 支持路由 prop 指定（pool/create/issue/verify/certify）
// 组件复用时不重建实例，必须 watch props 同步 tab（否则点左侧菜单无效）
// RA-PAGE-21 2.7：default 由 'pool' 改 ''——宿主路由（/invoice/query 无 props）渲染全页签工作台；
// 显式传 tab 的深链（/invoice/create 等）仍单页模式（isSingle）。
// G13: 增加 direction prop，支持进项/销项发票池深链预设过滤
const props = defineProps({ tab: { type: String, default: '' }, direction: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'pool')
watch(() => props.tab, (v) => { if (v) tab.value = v })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ pageNum: 1, pageSize: 20, invoiceNo: '', invoiceCode: '', sellerName: '', billNo: '',
  invoiceDate: [], scanDate: [], verifyStatus: '', direction: '' })

// G13: direction prop 变化时同步到查询条件并刷新发票池
// D-V4R-80（根因修复）：本 watch 原声明在 `const q = reactive(...)` **之前**且带 immediate:true，
// immediate 回调在声明处同步执行 ⇒ 访问尚未初始化的 `q` 触发 TDZ ReferenceError，
// setup 抛错 ⇒ `/invoice/input-pool`、`/invoice/output-pool` 直达时整页空白（只剩面包屑）。
// `/invoice/query` 因 direction='' 短路未触发访问，故同文件另一路由正常，掩盖了这个 bug。
// 修法：把 watch 移到 q 声明之后（loadPool 是函数声明，本就提升，无需等待）。
watch(() => props.direction, (v) => { if (v) { q.direction = v; loadPool(1) } }, { immediate: true })

const form = reactive({ invoiceCode: '', invoiceNo: '', invoiceType: 'VAT_SPECIAL', invoiceKind: 'SPECIAL', invoiceNature: 'BLUE', invoiceDate: '',
  // D3：buyerName/buyerTaxNo 与 invoiceTitle/taxNo 语义重复但分属两组列，
  // 补录时必须同时有值，否则按 buyer_name 过滤的报表查不到这张票（详见模板处注释）。
  sellerName: '', sellerTaxNo: '', invoiceTitle: '', taxNo: '', buyerName: '', buyerTaxNo: '', netAmount: 0, amount: 0, taxAmount: 0, checkCode: '', currencyCode: 'CNY', remark: '' })
const { autofill, saveFormHistory } = useAutoFill()
const lastCreatedId = ref(null)

const vlogs = ref([])
const vtotal = ref(0)
const vloading = ref(false)
const vq = reactive({ pageNum: 1, pageSize: 20 })
const realtimeInv = ref(null)
const pendingInvs = ref([])

const linesVis = ref(false)
const lines = ref([])
const fileViewer = ref(null)

/** 查看发票影像（imgFileId → 通用查看器） */
function viewImage(row) {
  if (!row.imgFileId) { ElMessage.info('该发票无影像文件'); return }
  fileViewer.value?.open([row.imgFileId], '发票影像：' + (row.invoiceNo || row.id))
}
const bindVis = ref(false)
const currentInv = ref(null)
const bindBillNo = ref('')
const bindBillId = ref(null)
const bindAmount = ref(0)
const bills = ref([])

// 发票占用次数对账
const auditVis = ref(false)
const auditRows = ref([])
const auditLoading = ref(false)

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function vsLabel(s) { return { NONE: '待验真', PENDING: '验真中', SUCCESS: '验真通过', FAILED: '验真失败' }[s] || s }
// R5-D-121：类型词表统一走共享字典 constants/enums.js（VAT/OTHER 等缺档一处补齐，禁止组件内局部映射）
function invTypeLabel(t) { return invoiceTypeLabel(t) }
// R5-D-122：渠道词表同上（REALTIME/TAX 等缺档已补，验真记录页与详情共用一处定义）
function channelLabel(c) { return invoiceChannelLabel(c) }
function vsType(s) { return s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'PENDING' ? 'warning' : 'info' }

/**
 * 发票方向判定（进项 INPUT／销项 OUTPUT）。
 * 优先取后端 direction 列；旧版后端未返回该列时按状态/来源单据类型兜底推导，
 * 保证「前端已热更、后端仍在跑旧 jar」的代次差窗口内方向语义依然可用。
 */
function dir(row) {
  if (row.direction) return row.direction
  const st = row.status, sdt = row.sourceDocType
  return (sdt === 'SALES_INVOICE' || sdt === 'RED_FLUSH'
    || st === 'ISSUING' || st === 'ISSUED' || st === 'RED_FLUSHED' || st === 'RED_FLUSH') ? 'OUTPUT' : 'INPUT'
}

async function loadPool(page) {
  if (page) q.pageNum = page
  loading.value = true
  try {
    const params = {
      pageNum: q.pageNum, pageSize: q.pageSize,
      invoiceNo: q.invoiceNo || undefined, invoiceCode: q.invoiceCode || undefined,
      sellerName: q.sellerName || undefined, billNo: q.billNo || undefined,
      invoiceDateFrom: q.invoiceDate?.[0], invoiceDateTo: q.invoiceDate?.[1],
      scanDateFrom: q.scanDate?.[0], scanDateTo: q.scanDate?.[1],
      verifyStatus: q.verifyStatus || undefined,
      direction: q.direction || undefined,
    }
    const r = await invoiceApi.page(params)
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function reset() {
  Object.assign(q, { invoiceNo: '', invoiceCode: '', sellerName: '', billNo: '', invoiceDate: [], scanDate: [], verifyStatus: '', direction: '', pageNum: 1 })
  loadPool()
}

async function verifyNow(row) {
  try {
    await invoiceApi.verifyNow(row.id, '', '发票池行内发起验真')
    ElMessage.success(`发票 ${row.invoiceNo} 验真已发起：无外部报文时只置「验真中」，不判定验真通过`)
    loadPool()
  } catch (e) { ElMessage.error(e.message) }
}

/* ---------------- 销项发票作废 / 红冲 
 * 弹窗原因必填（≥2 字，对齐 PoOrder 作废范式）；成功后刷新列表；
 * 后端校验当月开票/核销引用等条件，失败错误经全局拦截器 + 此处 catch 原样展示（不吞错）。 */
async function voidOutputInvoice(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认作废发票 ${row.invoiceNo || row.id}（${row.sellerName || ''}）？作废后不可恢复`,
      '发票作废',
      {
        inputPlaceholder: '请填写作废原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的作废原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        type: 'warning',
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await invoiceApi.voidInvoice({ invoiceId: row.id, reason })
    ElMessage.success('发票已作废')
    loadPool()
  } catch (e) { ElMessage.error(e.message) }
}

async function redFlushInvoice(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认对发票 ${row.invoiceNo || row.id}（${row.sellerName || ''}）红冲？将生成等额红字发票`,
      '发票红冲',
      {
        inputPlaceholder: '请填写红冲原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的红冲原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        type: 'warning',
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await invoiceApi.redFlush({ invoiceId: row.id, reason })
    ElMessage.success('红冲已提交')
    loadPool()
  } catch (e) { ElMessage.error(e.message) }
}

/** 批量验真：多选发票 → 一键验真 */
const poolSelected = ref([])
async function verifyBatch() {
  const ids = poolSelected.value.map((r) => r.id)
  if (!ids.length) return
  try {
    const n = await invoiceApi.verifyBatch(ids)
    ElMessage.success(`批量验真已受理：${n} / ${ids.length} 张置「验真中」（无外部报文不判定通过）`)
    poolSelected.value = []
    loadPool()
  } catch (e) { ElMessage.error(e.message) }
}

function resetForm() {
  Object.assign(form, { invoiceCode: '', invoiceNo: '', invoiceType: 'VAT_SPECIAL', invoiceKind: 'SPECIAL', invoiceNature: 'BLUE', invoiceDate: '',
    sellerName: '', sellerTaxNo: '', invoiceTitle: '', taxNo: '', buyerName: '', buyerTaxNo: '', netAmount: 0, amount: 0, taxAmount: 0, checkCode: '', currencyCode: 'CNY', remark: '' })
}

async function saveCreate() {
  await guardCreate(async () => {
    try {
      const id = await invoiceApi.create({ ...form })
      lastCreatedId.value = id
      ElMessage.success('补录成功')
      resetForm()
      loadPool()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function verifyLast() {
  try {
    await invoiceApi.verifyNow(lastCreatedId.value, '', '补录后手动发起验真')
    ElMessage.success('验真已发起：无外部报文时只置「验真中」，不判定验真通过')
    lastCreatedId.value = null
    loadPool()
  } catch (e) { ElMessage.error(e.message) }
}

/* ---------------- 销售开票（OTC02 INVOICE_ISSUE） ---------------- */
const issueForm = reactive({
  invoiceNo: '', sellerName: '', invoiceTitle: '', invoiceDate: '',
  netAmount: 0, taxAmount: 0, amount: 0, currencyCode: 'CNY', remark: '',
})
const lastIssueId = ref(null)
// R5-D-109：提交即置灰。服务端实测处理 45.9s 而 axios 20s 超时，按钮不禁用会被连续重试（六发全打到服务端）
const submitting = ref(false)

function syncIssueAmount() { issueForm.amount = Number(Number(issueForm.netAmount || 0) + Number(issueForm.taxAmount || 0)) }
function resetIssue() {
  Object.assign(issueForm, { invoiceNo: '', sellerName: '', invoiceTitle: '', invoiceDate: '',
    netAmount: 0, taxAmount: 0, amount: 0, currencyCode: 'CNY', remark: '' })
}
async function saveIssue() {
  if (submitting.value) return
  submitting.value = true
  try {
    await guardIssue(async () => {
      try {
        const id = await invoiceApi.issue({ ...issueForm })
        lastIssueId.value = id
        ElMessage.success(`登记成功并已发起开票审批（#${id}）`)
        resetIssue()
        loadPool()
      } catch (e) {
        // R5-D-109：超时≠未受理——外部网关慢（实测 45.9s），服务端往往已开票成功，只提示勿重复提交并刷新核对
        const isTimeout = e?.code === 'ECONNABORTED' || e?.code === 'ETIMEDOUT' || /timeout|超时/i.test(String(e?.message || ''))
        if (isTimeout) {
          ElMessage.warning('开票请求仍在处理中，请勿重复提交，稍后刷新列表查看结果')
          loadPool()
          return
        }
        // R5-D-110：唯一键/幂等兜底命中（code=4090 或含「已存在」）＝该发票已开票成功，走成功引导，不报 error
        if (e?.code === 4090 || /已存在/.test(String(e?.message || ''))) {
          ElMessage.warning('该发票已开票成功，请勿重复提交')
          locateIssuedInvoice(issueForm.invoiceNo)
          return
        }
        ElMessage.error(e.message)
      }
    })
  } finally {
    submitting.value = false
  }
}

/** R5-D-110：按票号在发票池内定位（从简：以票号作查询条件刷新列表，命中行置前可见） */
async function locateIssuedInvoice(no) {
  q.invoiceNo = no || ''
  await loadPool(1)
}

async function batchVerify() {
  try {
    // 批量发起验真：取待验发票一次提交后端受理。
    // D-DEC-115：这里原本逐张硬编码 result='PASS' 调实时验真，等于一次点击把最多 50 张票刷成「验真通过」
    // （按钮文案当时就写着「模拟税务通道」）；现改为走批量受理口，无外部报文只到「验真中」。
    const r = await invoiceApi.page({ pageNum: 1, pageSize: 50, verifyStatus: '' })
    const pending = (r.list || []).filter((x) => x.verifyStatus !== 'SUCCESS')
    if (!pending.length) return ElMessage.info('没有待验真发票')
    const ok = await invoiceApi.verifyBatch(pending.map((x) => x.id))
    ElMessage.success(`批量验真已受理：${ok} 张置「验真中」（无外部报文不判定通过）`)
    loadPool(); loadVerify()
  } catch (e) { ElMessage.error(e.message) }
}

async function verifyRealtime() {
  if (!realtimeInv.value) return
  try {
    await invoiceApi.verifyNow(realtimeInv.value, '', '实时验真（发票验真记录页发起）')
    ElMessage.success('验真已发起：无外部报文时只置「验真中」，不判定验真通过')
    realtimeInv.value = null
    loadVerify()
  } catch (e) { ElMessage.error(e.message) }
}

async function loadPendingInvs() {
  try {
    const r = await invoiceApi.page({ pageNum: 1, pageSize: 50, verifyStatus: '' })
    pendingInvs.value = (r.list || []).filter((x) => x.verifyStatus !== 'SUCCESS')
  } catch (e) { /* ignore */ }
}

async function loadVerify() {
  vloading.value = true
  try {
    const r = await invoiceApi.verifyPage({ pageNum: vq.pageNum, pageSize: vq.pageSize })
    vlogs.value = r.list || []
    vtotal.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { vloading.value = false }
}

async function showLines(row) {
  try {
    lines.value = (await invoiceApi.lineList({ invoiceId: row.id })) || []
    linesVis.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function openBind(row) {
  currentInv.value = row
  bindBillNo.value = ''
  bindBillId.value = null
  bindAmount.value = Number(row?.amount || 0)
  bills.value = []
  bindVis.value = true
  // 弹窗打开即取一次候选（空关键字＝最近可绑单据），不再"必须手输单号才有下拉"
  searchBill()
}

/**
 * 关联单据候选：走发票域自己的取数窗口（D-DEC-113）。
 * 改前取 billApi.myPage，服务端硬过滤 apply_user_id=登录人，共享会计（代位录票）选不到他人单据，
 * 三单匹配「补录发票 → 关联单据」这条唯一出口不可达；现按 inv:bind 取数、带发票腿差额。
 */
async function searchBill() {
  try {
    const r = await invoiceApi.bindableBills({ pageNum: 1, pageSize: 20, keyword: bindBillNo.value || undefined })
    bills.value = r.list || []
    if (!bills.value.length) ElMessage.info('没有可关联的单据（已排除作废/终止/驳回/撤回单，或不在本公司数据范围内）')
  } catch (e) { ElMessage.error(e.message) }
}

async function doBind() {
  await guardBind(async () => {
    try {
      await invoiceApi.bind({ billId: bindBillId.value, invoiceId: currentInv.value.id, amount: bindAmount.value })
      ElMessage.success('已关联')
      bindVis.value = false
    } catch (e) { ElMessage.error(e.message) }
  })
}

// 仅 LOCKED_IN_USE 的发票显示入口；付款已执行时服务端拒绝（既成资金事实走付款退款单）。
async function unbindBill(row) {
  try {
    const { value: billNo } = await ElMessageBox.prompt(
      `解除发票 ${row.invoiceNo || ''} 与业务单据的勾稽关联（占用额度随之释放）。输入业务单号：`,
      '解除关联（反核销）', { inputPlaceholder: '如 FK202609300002', inputValidator: (v) => (v && v.trim() ? true : '请输入业务单号') })
    const n = await invoiceApi.unbind({ billNo: billNo.trim(), invoiceId: row.id })
    ElMessage.success(`已解除关联（${n || 1} 条勾稽记录），发票已解锁`)
    loadPool()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

/* ---------------- 生成报销草稿（发票→CLAIM 自动填单 + 自动占用） ---------------- */
const claimVis = ref(false)
const claimInv = ref(null)
const claimForm = reactive({ itemId: null, title: '' })
const claimItems = ref([])
const claimLoading = ref(false)

/** 打开弹窗并懒加载费用项（mdApi 树接口返回全量，取 CLAIM 适用的叶子节点） */
async function openClaim(row) {
  claimInv.value = row
  claimForm.itemId = null
  claimForm.title = ''
  claimVis.value = true
  if (!claimItems.value.length) {
    try {
      const list = (await mdApi.expenseItems()) || []
      claimItems.value = list.filter((x) => x.isLeaf === 1 && (!x.billType || String(x.billType).includes('CLAIM')))
    } catch (e) { ElMessage.error(e.message) }
  }
}

async function doClaim() {
  await guardClaim(async () => {
    claimLoading.value = true
    try {
      const r = await billApi.fromInvoice(claimInv.value.id, { itemId: claimForm.itemId, title: claimForm.title || undefined })
      ElMessage.success(`已生成报销草稿 ${r.billNo || r.billId}（发票已占用），可前往报销编辑提交`)
      claimVis.value = false
      loadPool()
      router.push('/expense/claim')
    } catch (e) { ElMessage.error(e.message) } finally { claimLoading.value = false }
  })
}

/* ---------------- 发票占用次数对账（used-count/audit） ---------------- */
async function openAudit() { auditVis.value = true; loadAudit() }
async function loadAudit() {
  auditLoading.value = true
  try { auditRows.value = (await http.get('/tenant/invoice/used-count/audit', { params: { fix: false } })) || [] }
  catch (e) { ElMessage.error(e.message) } finally { auditLoading.value = false }
}
async function runAuditFix() {
  try {
    await http.get('/tenant/invoice/used-count/audit', { params: { fix: true } })
    ElMessage.success('已执行占用次数修复重算')
    loadAudit()
  } catch (e) { ElMessage.error(e.message) }
}

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

onMounted(() => {
  loadCurrency()
  autofill(form, 'INVOICE')
 loadPool(); loadVerify(); loadPendingInvs(); userApi.page({ pageNum: 1, pageSize: 200 }).then((d) => { users.value = d?.list || [] }).catch(() => {}) })

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'invoiceNo', label: '发票号码' }, { prop: 'invoiceCode', label: '发票代码' }, { prop: 'direction', label: '方向' }, { prop: 'sellerName', label: '销售方' }, { prop: 'invoiceDate', label: '开票日期' }, { prop: 'amount', label: '价税合计' }, { prop: 'verifyTime', label: '验真时间' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    if (c.prop === 'direction') return dir(row) === 'OUTPUT' ? '销项' : '进项'
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Invoice_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_invoiceCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => invoiceCm.value?.show(), 800) } } catch {}
// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: loadPool })

function handleView(row) {
  showLines(row)
}
</script>

<style scoped>
.query-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; align-items: center; }
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
