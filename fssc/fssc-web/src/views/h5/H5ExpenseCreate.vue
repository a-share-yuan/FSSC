<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="h5-page h5-page--bar">
    <header class="h5-header">
      <button class="h5-back" aria-label="返回" @click="leave">&lt;</button>
      <span class="h5-header__title">{{ isEdit ? '修改报销单' : '费用报销' }}</span>
      <span class="h5-header__sub">{{ myName }}</span>
    </header>

    <!-- 三态·加载：原单/基础数据没回来之前不给空表单（填了也是填在空气上） -->
    <section v-if="bootLoading" class="h5-body">
      <div v-for="i in 3" :key="i" class="h5-skeleton">
        <div class="h5-skeleton__line h5-skeleton__line--m" />
        <div class="h5-skeleton__line h5-skeleton__line--s" />
      </div>
    </section>

    <template v-else>
      <!-- 原单载入失败：必须明示。原先是"catch 后照常显示空表单"⇒ 用户以为在改打回的单，
           实际新建了一张，原单还在打回状态（会造成数据丢失）。 -->
      <section v-if="loadError" class="h5-body">
        <div class="h5-error">
          <div class="h5-error__d">{{ loadError }}</div>
        </div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="boot">重新载入</button>
        <button class="h5-btn h5-btn--sub h5-mt-8" @click="leave">返回</button>
      </section>

      <section v-else class="h5-body">
        <!-- 本机草稿恢复提示（/ UX-06：填一半被返回就没了） -->
        <div v-if="restoredAt" class="h5-denied">
          <div class="h5-denied__t">已恢复本机未提交的草稿</div>
          <div class="h5-denied__d">保存于 {{ timeText(restoredAt) }}。提交成功后会自动清掉；不想接着填可点下面丢弃。</div>
          <button class="h5-btn h5-btn--sub h5-mt-8" @click="discardDraft">丢弃本机草稿</button>
        </div>

        <!-- 上一轮被驳回的原因必须让人看见。
             原实现只把单子转回可编辑态就继续，用户看不到为什么被打回，
             改完再提交还是原样 ⇒ 同一个问题被打回两次。 -->
        <div v-if="rejectRemark" class="h5-denied">
          <div class="h5-denied__t">上一轮被驳回，请按意见改后再提交</div>
          <div class="h5-denied__d">{{ rejectRemark.comment || '（审批人未填写驳回意见）' }}</div>
          <div class="h5-card__meta">
            驳回环节：{{ rejectRemark.nodeName || '未记录' }}
            <template v-if="rejectRemark.handler"> · 处理人 {{ rejectRemark.handler }}</template>
            <template v-if="rejectRemark.time"> · {{ rejectRemark.time }}</template>
          </div>
        </div>

        <!-- 单据归属：只读展示，避免"我在哪个公司/部门"靠猜（跨法人会被 OrgGuard 拦下）
             本页唯一默认展开的一段，其余各段收起、收起态标题行带摘要（表单段带待补项计数）；
             校验失败会自动展开出错那一段并滚到字段，不会把红字藏在收起的卡里，见 scrollToField。 -->
        <H5FoldCard v-model:open="folds.owner" :summary="ownerSum">
          <template #head>
            <span class="h5-card__title h5-flush">费用报销 · 人民币 CNY</span>
            <span class="h5-flag h5-flag--info">{{ isEdit ? '修改原单' : '新建' }}</span>
          </template>
          <div class="h5-card__meta">申请人：{{ form.bill.applyUserName || myName }}（自动归属本人，不可代提）</div>
          <div class="h5-card__meta">公司 {{ form.bill.companyId || '—' }} · 部门 {{ form.bill.deptId || '—' }}</div>
          <div class="h5-tip h5-tip--left">
            移动端只收「费用报销」。差旅 / 事前申请 / 借款的明细段在电脑端才齐，手机上强提会交出一张缺件单，
            请到电脑端对应页面提单。
          </div>
        </H5FoldCard>

        <!-- 表头（必输项与电脑端同一份登记表：constants/billRequired.js） -->
        <H5FoldCard v-model:open="folds.base" title="基本信息" :summary="baseSum" :tone="baseTone"
                    @input="clearErrOnInput" @change="clearErrOnInput">

          <div class="h5-field" :class="{ 'is-error': errOf('title') }">
            <label for="h5-f-title">标题</label>
            <input id="h5-f-title" v-model="form.bill.title" type="text" placeholder="一句话说清这笔业务" />
          </div>
          <div v-if="errOf('title')" class="h5-field__err">{{ errOf('title') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('applyDate') }">
            <label for="h5-f-applyDate">申请日期</label>
            <input id="h5-f-applyDate" v-model="form.bill.applyDate" type="date" />
          </div>
          <div v-if="errOf('applyDate')" class="h5-field__err">{{ errOf('applyDate') }}</div>

          <div class="h5-field h5-field--col" :class="{ 'is-error': errOf('remark') }">
            <label for="h5-f-remark">单据说明</label>
            <textarea id="h5-f-remark" v-model="form.bill.remark" class="h5-textarea" rows="2"
                      placeholder="业务背景、事由、为什么现在报（必填，审批人看这里）"></textarea>
          </div>
          <div v-if="errOf('remark')" class="h5-field__err">{{ errOf('remark') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('periodYear') }">
            <label for="h5-f-periodYear">费用年度</label>
            <select id="h5-f-periodYear" v-model.number="form.bill.periodYear">
              <option v-for="y in years" :key="y" :value="y">{{ y }} 年</option>
            </select>
          </div>
          <div v-if="errOf('periodYear')" class="h5-field__err">{{ errOf('periodYear') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('paperAttachCount') }">
            <label for="h5-f-paperAttachCount">纸质附件</label>
            <input id="h5-f-paperAttachCount" v-model.number="form.bill.extJson.paperAttachCount"
                   type="number" min="0" inputmode="numeric" placeholder="随单交上去的纸质票据张数" />
          </div>
          <div v-if="errOf('paperAttachCount')" class="h5-field__err">{{ errOf('paperAttachCount') }}</div>
        </H5FoldCard>

        <!-- 费用明细（逐行校验 + 字段级错误，不再只查第一行） -->
        <H5FoldCard v-model:open="folds.lines" title="费用明细" :summary="linesSum" :tone="linesTone"
                    @input="clearErrOnInput" @change="clearErrOnInput">

          <!-- 常用费用项 Top5：一次点按填进当前空行，省掉在手机长下拉里翻（UX-08） -->
          <div v-if="recentChips.length" class="h5-chips">
            <button v-for="c in recentChips" :key="c.id" class="h5-chip"
                    :class="{ on: form.lines.some((l) => String(l.itemId) === String(c.id)) }"
                    @click="useRecent(c)">{{ c.itemName }}</button>
          </div>

          <div v-for="(l, i) in form.lines" :key="i" class="h5-lines">
            <div class="h5-card__row">
              <span class="h5-card__meta">明细 {{ i + 1 }}</span>
              <button class="h5-btn-link" :disabled="form.lines.length <= 1" @click="removeLine(i)">删除</button>
            </div>

            <div class="h5-field" :class="{ 'is-error': errOf(`line${i}-itemId`) }">
              <label :for="`h5-f-line${i}-itemId`">费用项</label>
              <select :id="`h5-f-line${i}-itemId`" v-model="l.itemId" @change="onItemChange(l)">
                <option :value="null">请选择费用项</option>
                <option v-for="it in items" :key="it.id" :value="it.id">{{ it.itemName }}</option>
              </select>
            </div>
            <div v-if="errOf(`line${i}-itemId`)" class="h5-field__err">{{ errOf(`line${i}-itemId`) }}</div>

            <div class="h5-field" :class="{ 'is-error': errOf(`line${i}-amount`) }">
              <label :for="`h5-f-line${i}-amount`">金额</label>
              <input :id="`h5-f-line${i}-amount`" v-model.number="l.amount" type="number" step="0.01"
                     min="0" inputmode="decimal" placeholder="0.00" @input="recalcLine(l)" />
            </div>
            <div v-if="errOf(`line${i}-amount`)" class="h5-field__err">{{ errOf(`line${i}-amount`) }}</div>

            <div class="h5-field">
              <label :for="`h5-f-line${i}-tax`">税率</label>
              <select :id="`h5-f-line${i}-tax`" v-model.number="l.taxRate" @change="recalcLine(l)">
                <option v-for="r in TAX_RATES" :key="r" :value="r">{{ r === 0 ? '不含税' : r + '%' }}</option>
              </select>
            </div>
            <div class="h5-card__meta">税额 ￥{{ money(l.taxAmount) }} · 不含税 ￥{{ money(l.netAmount) }}</div>

            <div class="h5-field" :class="{ 'is-error': errOf(`line${i}-expenseDate`) }">
              <label :for="`h5-f-line${i}-expenseDate`">费用日期</label>
              <input :id="`h5-f-line${i}-expenseDate`" v-model="l.expenseDate" type="date" />
            </div>
            <div v-if="errOf(`line${i}-expenseDate`)" class="h5-field__err">{{ errOf(`line${i}-expenseDate`) }}</div>

            <div class="h5-field">
              <label :for="`h5-f-line${i}-remark`">备注</label>
              <input :id="`h5-f-line${i}-remark`" v-model="l.remark" type="text" placeholder="选填" />
            </div>

            <!-- 预算占用实时预览：额度不够在提单前就知道，而不是提交被拦才发现 -->
            <div v-if="l.budgetItemId" class="h5-budget">
              <div class="h5-card__row">
                <span class="h5-card__meta">{{ budgetLabelOf(l.budgetItemId) }}</span>
                <span class="h5-flag" :class="lineBudgetCls(l)">{{ lineBudgetText(l) }}</span>
              </div>
            </div>
            <!-- 年度内没有这条预算行时要说出来。
                 原来界面什么都不显示，用户以为"这单不用占预算"，实际是查不到行。 -->
            <div v-else-if="l.itemId != null" class="h5-card__meta">
              {{ form.bill.periodYear }} 年度未下达该费用项的预算行，本单不会占用预算。
            </div>
          </div>

          <button class="h5-btn h5-btn--ghost h5-mt-8" @click="addLine">+ 添加明细</button>
        </H5FoldCard>

        <!-- 预算占用汇总 + 校验状态（判据与 PC useBill.js:605-629 同源） -->
        <H5FoldCard v-if="budgetRows.length" v-model:open="folds.budget" title="预算占用" :summary="budgetSum" :tone="budgetTone">
          <div v-for="r in budgetRows" :key="r.bid" class="h5-row-sub h5-row-sub--block">
            <div class="h5-card__row">
              <span>{{ r.label }}</span>
              <span class="h5-flag" :class="r.flagCls">{{ r.flagText }}</span>
            </div>
            <div class="h5-card__meta">本单 ￥{{ money(r.used) }} / 可用 {{ r.available == null ? '额度未知' : '￥' + money(r.available) }}</div>
          </div>
          <div v-if="budgetHard.length" class="h5-error h5-mt-8">
            <div class="h5-error__d">
              {{ budgetHard.length }} 项超出可用额度，且是硬控方案 ⇒ 本单不能提交。请减额、换费用项或走电脑端预算调整。
            </div>
          </div>
          <template v-else-if="budgetSoft.length">
            <div class="h5-denied h5-mt-8">
              <div class="h5-denied__t">{{ budgetSoft.length }} 项软控超支，须填超支理由</div>
              <div class="h5-denied__d">{{ form.bill.extJson.overrunReason || '理由还没填，提交时会再问一次。' }}</div>
            </div>
            <button class="h5-btn h5-btn--sub h5-mt-8" @click="askOverrunReason">填写超支理由</button>
          </template>
          <div v-else-if="budgetWarnRows.length" class="h5-tip h5-tip--left">
            {{ budgetWarnRows.length }} 项预警超支：本次放行，占用流水会记录。
          </div>
        </H5FoldCard>

        <!-- 附件 / 票据影像（真 ref，原 document.getElementById 必报 TypeError）
             折叠正文用 v-show（H5FoldCard 内），fileRef 收起时仍在 DOM ⇒ 上传按钮不会因为折叠而变死。 -->
        <H5FoldCard v-model:open="folds.attach" title="附件 / 票据影像" :summary="attachSum" :tone="attachTone">
          <input ref="fileRef" type="file" accept="image/*,.pdf,.ofd,.xml" multiple hidden @change="onPickFile" />
          <button class="h5-btn h5-btn--ghost" :disabled="uploading" @click="pickFile">
            {{ uploading ? '上传识别中…' : (hasInvoiceAttach ? '继续上传' : '拍票据 / 上传发票') }}
          </button>
          <!-- 长请求（扫描版 PDF 图像识别 30~60 秒）必须给一个出口，
               否则用户只会杀页面 ⇒ 服务端仍在写、回来看到一张"来历不明"的票。
               中断只是断开请求，文案如实说「结果未知」，见 abortedOutcome。 -->
          <button v-if="uploading" class="h5-btn h5-btn--ghost" @click="cancelUploadWait">取消等待</button>
          <div class="h5-tip h5-tip--left">
            电子发票（OFD/PDF/XML）先走本地版式解析，秒级出结果，只回填票面、不进票夹；
            扫描版 PDF（没有文本层）才会自动转图像识别，约需 30~60 秒，请勿离开本页。
            图片票走票据识别：识别成功且票面要素齐全才会自动进票夹（可在「发票」里勾选）；
            要素不全或没识别出来时，附件也已保存并带「发票」标记，
            可在「费用明细」手工填写金额，或到电脑端「发票管理 › 发票补录」补录票面。
            选中的费用项若要求必附发票，这里必须有「发票」标记才能提交。
          </div>
          <div v-for="a in attaches" :key="a.fileId" class="h5-row-sub">
            <span class="h5-flex-1">{{ a.fileName }}</span>
            <span class="h5-flag" :class="a.attachType === 'INVOICE' ? 'h5-flag--ok' : 'h5-flag--info'">
              {{ a.attachType === 'INVOICE' ? '发票' : '附件' }}
            </span>
            <button class="h5-btn-link" aria-label="移除附件" @click="removeAttach(a)">移除</button>
          </div>
          <div v-if="!attaches.length" class="h5-tip">还没上传附件</div>
        </H5FoldCard>

        <!-- 关联票夹发票（三态：缺权 / 出错 / 真的空） -->
        <H5FoldCard v-model:open="folds.inv" title="关联发票" :summary="invSum" :tone="invTone">
          <template v-if="invDenied">
            <div class="h5-denied">
              <div class="h5-denied__t">票夹列表对当前账号不开放</div>
              <div class="h5-denied__d">
            这不代表你没有发票——上面拍的票据仍会作为附件随单提交、审批侧照常可看；
            发票台账与勾稽由发票管理岗在电脑端「发票管理」维护。
          </div>
            </div>
          </template>
          <div v-else-if="invError" class="h5-error">
            <div class="h5-error__d">{{ invError }}</div>
            <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadInvoicePool">重试</button>
          </div>
          <template v-else>
            <div v-for="inv in selectedInvoices" :key="inv.id" class="h5-row-sub">
              <span class="no">{{ inv.invoiceNo }}</span>
              <span class="h5-flex-1">{{ inv.sellerName || '—' }}</span>
              <span>￥{{ money(inv.amount) }}</span>
              <button class="h5-btn-link" aria-label="取消关联" @click="toggleInvoice(inv.id)">取消</button>
            </div>
            <div v-if="!invoiceIds.length" class="h5-tip">{{ invPool.length ? '还没选发票' : '票夹里暂时没有可选发票，可直接上传影像' }}</div>
            <button class="h5-btn h5-btn--sub h5-mt-8" @click="openInvoicePicker">
              从票夹选择{{ invPool.length ? `（${invPool.length} 张）` : '' }}
            </button>
          </template>
        </H5FoldCard>

        <!-- 收款信息（后端 assertPayeeAccount：CLAIM 金额>0 时收款对象＋收款账号硬必填）
             原先 H5 表单根本没有这一段 ⇒ 附件环节修好后，提交仍会被 PARAM_INVALID 打回，
             表现为「移动端提单一直失败」。这里补齐，判据与 ExpenseBillService.assertPayeeAccount 一致。 -->
        <H5FoldCard v-model:open="folds.payee" title="收款信息" :summary="payeeSum" :tone="payeeTone"
                    @input="clearErrOnInput" @change="clearErrOnInput">
          <div class="h5-field" :class="{ 'is-error': errOf('payTarget') }">
            <label for="h5-f-payTarget">收款对象</label>
            <select id="h5-f-payTarget" v-model="form.bill.payTarget" @change="onPayTargetChange">
              <option :value="''">请选择</option>
              <option v-for="o in payeeTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div v-if="errOf('payTarget')" class="h5-field__err">{{ errOf('payTarget') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('payMethod') }">
            <label for="h5-f-payMethod">收款方式</label>
            <select id="h5-f-payMethod" v-model="form.bill.extJson.payMethod">
              <option :value="''">请选择</option>
              <option v-for="o in payMethodOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div v-if="errOf('payMethod')" class="h5-field__err">{{ errOf('payMethod') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('accountName') }">
            <label for="h5-f-accountName">账户名称</label>
            <input id="h5-f-accountName" v-model="form.bill.extJson.accountName" type="text" placeholder="银行卡户名" />
          </div>
          <div v-if="errOf('accountName')" class="h5-field__err">{{ errOf('accountName') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('payeeAccountNo') }">
            <label for="h5-f-payeeAccountNo">收款账号</label>
            <input id="h5-f-payeeAccountNo" v-model="form.bill.payeeAccountNo" type="text"
                   inputmode="numeric" placeholder="收款对象的银行账号（防付错）" />
          </div>
          <div v-if="errOf('payeeAccountNo')" class="h5-field__err">{{ errOf('payeeAccountNo') }}</div>

          <div class="h5-field">
            <label for="h5-f-bankName">开户行</label>
            <input id="h5-f-bankName" v-model="form.bill.extJson.bankName" type="text" placeholder="选填" />
          </div>
          <div class="h5-tip h5-tip--left">账户名称与收款账号请与银行留存的完全一致，付错由提单人负责。</div>
        </H5FoldCard>

        <!-- 首审办理人（PICK 模式才出现；与 PC 同判据：需要点名却没点就不提交）
             这一段是提交前的必选动作区，不折叠：收起会让人在"还差首审办理人"的提示前找不到选人入口。 -->
        <div v-if="firstPick.mode === 'PICK' && firstPick.candidates.length" class="h5-card">
          <div class="h5-section-title h5-flush">首审办理人</div>
          <div class="h5-chips">
            <button v-for="c in firstPick.candidates" :key="c.userId" class="h5-chip"
                    :class="{ on: String(firstPick.userId) === String(c.userId) }"
                    @click="firstPick.userId = c.userId">{{ c.name || c.userName }}</button>
          </div>
          <div class="h5-card__meta">环节「{{ firstPick.nodeName || '第一道审批' }}」需要指定办理人{{ firstPick.required ? '（必选）' : '（不选则按默认岗位派发）' }}。</div>
        </div>
      </section>

      <!-- 吸底三出口（§4.2-③：合计常驻＋存草稿＋提交；提交不再是唯一出口） -->
      <footer v-if="!loadError" class="h5-actionbar">
        <div class="h5-actionbar__sum">合计<b>￥{{ money(totalAmount) }}</b></div>
        <button class="h5-btn h5-btn--sub" :disabled="busy" :aria-busy="busy ? 'true' : 'false'" @click="saveDraftOnly">
          <i v-if="busy" class="h5-spin" />存草稿
        </button>
        <button class="h5-btn" :disabled="busy" :aria-busy="busy ? 'true' : 'false'" @click="submit">
          <i v-if="busy" class="h5-spin" />{{ busy ? '处理中…' : (isEdit ? '重新提交' : '提交审批') }}
        </button>
      </footer>
    </template>

    <!-- 票夹选择弹层（唯一允许内层滚动的区块，见 h5-tokens.css 硬线 3 的例外说明）
         进出场走 <Transition name="h5-pop"> -->
    <Transition name="h5-pop">
      <div v-if="showInvPicker" class="h5-mask" @click="showInvPicker = false">
        <div class="h5-sheet" @click.stop>
          <div class="h5-sheet__grip" />
          <div class="h5-sheet__title">选择发票（已选 {{ invoiceIds.length }} 张）</div>
          <div class="h5-sheet__list">
            <div v-for="inv in invPool" :key="inv.id" class="h5-row-sub">
              <span class="h5-flex-1">
                <span class="no">{{ inv.invoiceNo }}</span>
                <span class="h5-card__meta h5-card__meta--block">{{ inv.sellerName || '—' }} · {{ dayText(inv.invoiceDate) }}</span>
              </span>
              <span>￥{{ money(inv.amount) }}</span>
              <button class="h5-btn-link h5-btn-link--plain"
                      @click="toggleInvoice(inv.id)">{{ invoiceIds.includes(inv.id) ? '已选' : '选择' }}</button>
            </div>
            <div v-if="!invPool.length" class="h5-state">
              <div class="h5-state__title">票夹里没有可用发票</div>
              <div class="h5-state__hint">可以先拍票据上传，识别成功后会自动进票夹</div>
            </div>
          </div>
          <button class="h5-btn h5-mt-12" @click="showInvPicker = false">完成</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
/**
 * 移动报销端 · 提单页
 *
 * 几处必须守住的约束，违反任一条提单都到不了后端或写错主体：
 *  attaches 传 {fileId,fileName,attachType} 对象数组 —— 后端 BillReq.attaches 是
 *       List<ExpBillAttach>，数字数组反序列化直接 500。
 *  companyId/deptId 取登录态 —— 写死会被 OrgGuard.assertCompanyAllowed 拒掉跨法人账号，
 *       侥幸通过也是把部门预算记到别的主体。
 *  上传按钮用 ref 触发 —— Vue 3 模板全局白名单不含 document，取到 undefined 会抛 TypeError。
 *  校验逐行做 —— 只查首行时，第二行起金额为空也照样提交，后端 item_id NOT NULL 报 500。
 *
 * 「收款信息」段在 PC 一直有、H5 一度整块缺失：ExpenseBillService.assertPayeeAccount 对
 * CLAIM 且金额>0 强制 payTarget＋payeeAccountNo，缺任一条即 PARAM_INVALID。
 */
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import H5FoldCard from '@/components/h5/H5FoldCard.vue'
import { billApi, mdApi, masterCfgApi, orgApi, invoiceApi, fileApi, wfApi, flowApi, store, isRequestCancelled } from '../../api'
import { requiredOf, missingBillField } from '../../constants/billRequired.js'
import { invoiceOutcome, abortedOutcome } from '@/utils/invoiceOutcome'
import { pickBudgetLine, resolveLineBudgetId } from '../../utils/budget.js'
import { splitTaxInclusive } from '../../utils/tax.js'
import { budgetLabel, budgetUnseenLabel, formatDateTime } from '../../utils/format'
import { money, dayText, permDeniedOf, saveLocalDraft, loadLocalDraft, clearLocalDraft, pushRecentItems, recentItemIds, compressImage } from '@/utils/h5.js'
import { hasPerm } from '../../utils/perm'
// 收款方式改为读 sys_dict 的 pay_method（基础数据办结）。
// 本页全站唯一未接 i18n 的页面（文案直接写中文），故 label 直接用字典 label，不做 i18n 合成。
//    字典不可用时 useDict 自动回落到本地兜底常量，界面与原先一致。
import { useDict } from '@/composables/useDict.js'
import { PAY_METHOD_LOCAL, PAYEE_TYPE_LOCAL } from '@/composables/useBaseDict'

// 收款方式下拉的选项来源（本地兜底常量，字典到位后被字典覆盖）
const payMethodDict = useDict('pay_method', PAY_METHOD_LOCAL)
const payMethodOptions = payMethodDict.options

// 收款对象类型（enum_payee_type）。字典值域与本页原写死的 EMPLOYEE/SUPPLIER/CUSTOMER 一致。
// EMPLOYEE 的「（报给自己）」是本页特有的业务提示（非字典文案），故在此拼接而非丢给字典。
const payeeTypeDict = useDict('enum_payee_type', PAYEE_TYPE_LOCAL)
// useDict().options 是 computed ref（见 useDict.js:120），此处必须取 .value 再 map。
// 直接 .map 会抛 "options.map is not a function"，导致 H5 制单页白屏。
const payeeTypeOptions = computed(() => payeeTypeDict.options.value.map((o) => ({
  value: o.value,
  label: o.value === 'EMPLOYEE' ? `${o.label}（报给自己）` : o.label
})))

defineOptions({ name: 'H5ExpenseCreate' })

const route = useRoute()
const router = useRouter()

// 税率档位沿用 PC 明细列的可选项；默认不含税（0），与移动端"先讲清金额"的定位一致
const TAX_RATES = [0, 1, 3, 6, 9, 13]
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]

const editId = computed(() => route.params.id || route.query.editBillId || null)
const isEdit = computed(() => !!editId.value)
const userId = computed(() => store.user?.userId ?? store.user?.id ?? null)
const myName = computed(() => store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '我')

// 补漏环节·H5 制单页：预填日期不取 toISOString().slice(0,10)＝UTC 串——
// 东八区恒差 −8h、0~8 点会把 apply_date/expense_date 预填成"昨天"。本地钟口，与 PC today() 同判据。
function todayLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function timeText(ts) {
  const d = new Date(Number(ts))
  if (isNaN(d)) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
const round2 = (n) => Math.round((Number(n) || 0) * 100) / 100

function newLine() {
  return {
    id: null, itemId: null, itemName: '', amount: 0, taxRate: 0, taxAmount: 0, netAmount: 0,
    budgetItemId: null, expenseDate: todayLocal(), remark: '', currencyCode: 'CNY',
    quantity: 1, unitPrice: null, extJson: {},
  }
}

function newBill() {
  return {
    id: null, billType: 'CLAIM', billSubType: '', title: '',
    // 公司/部门取登录态（PC useBill.js 同一写法），跨法人单据会被后端 OrgGuard 拒
    companyId: store.user?.companyId ?? null,
    deptId: store.user?.deptId ?? null,
    applyUserId: store.user?.userId ?? store.user?.id ?? null,
    applyUserName: store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '',
    applyDate: todayLocal(), periodYear: new Date().getFullYear(),
    currencyCode: 'CNY', exchangeRate: 1,
    payTarget: '', payeeName: '', payeeAccountNo: '', remark: '',
    totalAmount: 0, extJson: { paperAttachCount: 0, payMethod: '', accountName: '', bankName: '' },
  }
}

const form = reactive({ bill: newBill(), lines: [newLine()] })

/**
 * 埋点：本次移动端填报会话的起始钟点。
 *
 * 规则写清楚，别把它当"提单耗时"的全生命周期指标：
 *  - 起点＝本页本次打开（新建走 onMounted boot、切回本页走 onActivated 复位），终点＝这一次组装载荷的时刻；
 *  - 中途离开页面再回来 ⇒ 计时重置，所以 `h5StayMs` 是"最后一段连续填报会话"的毫秒数，不是累计；
 *  - 存进 `exp_bill.ext_json` 的 `h5StayMs` 一个键，不新建表、不加接口、不动 PC 逻辑。
 * 两处已知失真，如实记下不掩饰：
 *  ① PC 端保存会按它自己的表单模型重写 ext_json ⇒ 该键可能被洗掉，本字段含义＝"最后一次保存方"；
 *  ② 页面停在后台（切走 App）也照常计时，手机上的"停留"不等于"在填"。
 * 方案册 里"平均提单时长"用的就是这条环节，取值时须按 ext_json.h5StayMs 的 NULL/非 NULL 分档，
 * 不能拿全表平均值当移动端指标（PC 单没有这个键）。
 */
let sessionOpenedAt = Date.now()
function resetSessionClock() { sessionOpenedAt = Date.now() }

const items = ref([])
const budgetPool = ref([])
/** 当前预算池对应的费用年度（池按年度取，年度变了要重取） */
const budgetPoolYear = ref(null)
const itemBudgetMap = ref({})
const companies = ref([])
const depts = ref([])
const attaches = ref([])
const invPool = ref([])
const invoiceIds = ref([])
// 编辑态里"进页面时就已经挂在这张单上的发票"：bind 后端是追加占用语义
// （InvoiceController.bind → 逐票 bindToBill），把已绑过的再发一遍＝重复占用。
// 故只对增量 id 调 bind、对被摘掉的原有发票调 unbind，避免"改一次单发票被占两次"。
const boundInitially = ref(new Set())
const boundRows = ref([])

const bootLoading = ref(true)
const loadError = ref('')
const busy = ref(false)
const uploading = ref(false)
/* 「上传＋识别」在同一个请求里串行，而后端识别链是 REQUIRES_NEW 独立事务 ⇒
   用户按「取消等待」只是断开这一条 HTTP 连接，服务端并不会因此停手（关掉页面后仍会写
   ocr_record 并继续入池）。所以中断只报「结果未知」，见 abortedOutcome。 */
let uploadController = null
const uploadAborted = ref(false)
function cancelUploadWait() {
  uploadAborted.value = true
  if (uploadController) uploadController.abort()
}
const showInvPicker = ref(false)
const restoredAt = ref(0)
/* 上一轮驳回意见 { comment, nodeName, handler, time } */
const rejectRemark = ref(null)
const errs = reactive({})
const fileRef = ref(null)

const firstPick = reactive({ mode: 'AUTO', nodeKey: '', nodeName: '', required: false, candidates: [], userId: null })

// 脏标记：给"离开前确认"用；hydrating 期间不置脏（载入本身不是用户输入）
let hydrating = true
const dirty = ref(false)
let savedJustNow = false

const totalAmount = computed(() => round2(form.lines.reduce((a, l) => a + Number(l.amount || 0), 0)))
// 已选发票的展示源＝票夹池 ∪ 本单已勾稽回读行：后者可能因"已被占用"不出现在 /invoice/page 里，
// 只用池会让"已关联 3 张"在界面上显示成 0 张（选中状态与实际占用不一致）。
const invoiceIndex = computed(() => {
  const m = new Map()
  ;[...invPool.value, ...boundRows.value].forEach((x) => {
    if (x && x.id != null && !m.has(String(x.id))) m.set(String(x.id), x)
  })
  return m
})
const selectedInvoices = computed(() => invoiceIds.value
  .map((id) => invoiceIndex.value.get(String(id)))
  .filter(Boolean))
const hasInvoiceAttach = computed(() => attaches.value.some((a) => a.attachType === 'INVOICE'))

/* 字段级错误 */
function errOf(k) { return errs[k] || '' }
function setErr(k, msg) { errs[k] = msg }
function clearErrs() { Object.keys(errs).forEach((k) => delete errs[k]) }

/** 字段属于哪一段折叠卡（明细行的 key 形如 `line0-amount`，按前缀归段） */
const FIELD_FOLD = {
  title: 'base', applyDate: 'base', remark: 'base', periodYear: 'base', paperAttachCount: 'base',
  payTarget: 'payee', payMethod: 'payee', accountName: 'payee', payeeAccountNo: 'payee',
}
function foldOfField(k) {
  const s = String(k || '')
  if (s.startsWith('line')) return 'lines'
  return FIELD_FOLD[s] || ''
}

async function scrollToField(k) {
  // 卡片折叠后出错字段可能在收起的正文里：先展开所属段再滚，否则只弹一句 toast、红字看不见
  const f = foldOfField(k)
  if (f) folds[f] = true
  await nextTick()
  const el = document.getElementById('h5-f-' + k)
  if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' })
}

/**
 * 收起态摘要会写「N 项待补」，用户补完那一格就该消掉，否则红字挂在标题行上赖着不走。
 * 原生 input/change 会冒泡到卡片根（H5FoldCard 单根 ⇒ 监听器随 $attrs 落到根 div），
 * 按 id（h5-f-<key>）反查错误键即可，不改任何校验规则：清掉的这一格下次 validateForm 会重判。
 */
function clearErrOnInput(e) {
  const id = String(e?.target?.id || '')
  if (!id.startsWith('h5-f-')) return
  delete errs[id.slice(5)]
}

/* 折叠态与收起态摘要
 * 七段卡片顺排要滚五六屏，所以只默认展开「单据归属」，其余收起，
 * 收起态标题行给一句摘要（合计与行数、发票张数、待补项计数）。
 * 表单段收起会藏住字段级红字，故两条兜底：①有错时摘要换成红色「N 项待补」；
 * ②scrollToField 先展开出错那一段。摘要一律现算本页已有数据，缺权/失败照实说。 */
const folds = reactive({ owner: true, base: false, lines: false, budget: false, attach: false, inv: false, payee: false })

const BASE_FIELDS = ['title', 'applyDate', 'remark', 'periodYear', 'paperAttachCount']
const PAYEE_FIELDS = ['payTarget', 'payMethod', 'accountName', 'payeeAccountNo']
function errCountOf(fields) { return fields.filter((k) => errs[k]).length }
function lineErrCount() { return Object.keys(errs).filter((k) => k.startsWith('line')).length }

/** 所选费用项里有「必附发票」的（与服务端 PrecheckService 同规则，见 gateInvoiceAttach） */
const needInvoiceAttach = computed(() => form.lines
  .filter((l) => l.itemId != null)
  .some((l) => Number(items.value.find((x) => String(x.id) === String(l.itemId))?.needInvoice) === 1))

const ownerSum = computed(() => `${form.bill.applyUserName || myName.value} · 公司 ${form.bill.companyId || '—'} · 部门 ${form.bill.deptId || '—'}`)

const baseSum = computed(() => (errCountOf(BASE_FIELDS) ? `${errCountOf(BASE_FIELDS)} 项待补` : (form.bill.title || '标题未填')))
const baseTone = computed(() => (errCountOf(BASE_FIELDS) ? 'err' : ''))

const linesSum = computed(() => {
  const n = lineErrCount()
  if (n) return `${n} 项待补`
  if (!form.lines.some((l) => l.itemId != null && Number(l.amount) > 0)) return '还没填完整明细（费用项＋金额）'
  return `${form.lines.length} 行 · ￥${money(totalAmount.value)}`
})
const linesTone = computed(() => {
  if (lineErrCount()) return 'err'
  return form.lines.some((l) => l.itemId != null && Number(l.amount) > 0) ? '' : 'warn'
})

const budgetSum = computed(() => {
  if (budgetHard.value.length) return `${budgetHard.value.length} 项超额度（硬控，不能提交）`
  if (budgetSoft.value.length) return `${budgetSoft.value.length} 项软控超支·需填理由`
  if (budgetWarnRows.value.length) return `${budgetWarnRows.value.length} 项预警超支`
  return `${budgetRows.value.length} 项占用正常`
})
const budgetTone = computed(() => {
  if (budgetHard.value.length) return 'err'
  return (budgetSoft.value.length || budgetWarnRows.value.length) ? 'warn' : ''
})

const attachSum = computed(() => {
  const lack = needInvoiceAttach.value && !hasInvoiceAttach.value
  if (!attaches.value.length) return lack ? '缺发票附件' : '未上传'
  const inv = attaches.value.filter((a) => a.attachType === 'INVOICE').length
  return `${attaches.value.length} 份 · 发票 ${inv}` + (lack ? '（还缺发票）' : '')
})
const attachTone = computed(() => (needInvoiceAttach.value && !hasInvoiceAttach.value ? 'warn' : ''))

const invSum = computed(() => {
  if (invDenied.value) return '票夹对当前账号不开放'
  if (invError.value) return '加载失败'
  const n = invoiceIds.value.length
  if (!n) return '未关联'
  const rows = selectedInvoices.value
  // 已勾稽回读行可能不在票夹分页里（invoiceIndex 也补不全时）⇒ 只报张数，不报一个偏小的合计
  if (rows.length !== n) return `已关联 ${n} 张`
  return `${n} 张 · ￥${money(rows.reduce((a, x) => a + (Number(x.amount) || 0), 0))}`
})
const invTone = computed(() => (invDenied.value ? 'warn' : (invError.value ? 'err' : '')))

const payeeSum = computed(() => {
  const n = errCountOf(PAYEE_FIELDS)
  if (n) return `${n} 项待补`
  const no = String(form.bill.payeeAccountNo || '')
  if (!no) return '未填（金额＞0 时必填）'
  const opt = payeeTypeOptions.value.find((o) => o.value === form.bill.payTarget)
  return `${opt ? opt.label : (form.bill.payTarget || '—')} · 尾号 ${no.slice(-4)}`
})
const payeeTone = computed(() => {
  if (errCountOf(PAYEE_FIELDS)) return 'err'
  return form.bill.payeeAccountNo ? '' : 'warn'
})

/* 明细 */
function addLine() {
  const l = newLine()
  l.expenseDate = form.bill.applyDate || todayLocal()
  form.lines.push(l)
}
async function removeLine(i) {
  if (form.lines.length <= 1) return
  // §5：删除类动作先确认（热区也已按 token 抬到 ≥44）
  try {
    await ElMessageBox.confirm(`确认删除明细 ${i + 1}？已上传的票面信息不会自动补回。`, '删除明细', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch { return }
  form.lines.splice(i, 1)
}
function recalcLine(l) {
  // 与 PC 同一价内税规则（utils/tax.js），避免"移动端税额＝金额×税率"的另一种算法
  const r = splitTaxInclusive(l.amount, l.taxRate)
  l.taxAmount = r.taxAmount
  l.netAmount = r.netAmount
}
function onItemChange(l) {
  const it = items.value.find((x) => String(x.id) === String(l.itemId))
  l.itemName = it?.itemName || ''
  // 费用项一换，预算行必须跟着换。
  // 原实现只在 budgetItemId 为空时取一次 ⇒ 换了费用项仍挂着上一个费用项的预算行，
  // 占用便记到别的科目（例如行政办公费的单占到劳动保护费行）。
  // 移动端没有人工选预算科目这一步（预算区只读展示），所以这里无条件重取，取不到即置空。
  l.budgetItemId = pickBudgetFor(l.itemId)
  delete errs[`line${form.lines.indexOf(l)}-itemId`]
}
function useRecent(c) {
  // Top5 点按：优先填当前"没选费用项的空行"，全填过就追加一行
  let target = form.lines.find((l) => l.itemId == null)
  if (!target) { addLine(); target = form.lines[form.lines.length - 1] }
  target.itemId = c.id
  onItemChange(target)
}
async function removeAttach(a) {
  try {
    await ElMessageBox.confirm(`确认移除「${a.fileName}」？已在票夹里的发票不受影响。`, '移除附件', {
      type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消',
    })
  } catch { return }
  attaches.value = attaches.value.filter((x) => x.fileId !== a.fileId)
}

/* 预算（判据逐字对齐 useBill.js:319-343 / 605-629） */
function pickBudgetFor(itemId) {
  return pickBudgetLine({
    pool: budgetPool.value, itemId, map: itemBudgetMap.value,
    companyId: form.bill.companyId, deptId: form.bill.deptId,
    // 年度硬条件——预算行年度必须与单据费用年度一致
    budgetYear: form.bill.periodYear,
  })
}
/**
 * 按单据费用年度重取预算行。
 * /budget/item/pick 支持 budgetYear；不按年取时前 200 行被最早年度占满
 * （bg_item 排序＝budget_year/budget_period/dim_key 升序），2026 年的单会命中 2025 年行。
 * 同一年度不重复取（载入旧单与年度变更都会走到这里）。
 *
 * seq 守卫：年度 watcher 与原单载入会并发触发两次取数，年份来回改时后发的响应可能先到，
 * 不守卫就会出现"池是 A 年、字段是 B 年"。晚到的旧结果直接丢弃。
 */
let budgetPoolSeq = 0
async function loadBudgetPool(year) {
  const y = year ?? null
  if (String(budgetPoolYear.value) === String(y)) return
  const seq = ++budgetPoolSeq
  try {
    const bg = await mdApi.budgets({ pageNum: 1, pageSize: 200, budgetYear: y ?? undefined })
    if (seq !== budgetPoolSeq) return
    budgetPool.value = (bg && bg.list) || []
    budgetPoolYear.value = y
  } catch {
    // 取不到预算行＝额度未知，不阻断填写（提交时服务端按同一年度规则自行定位）
    if (seq !== budgetPoolSeq) return
    budgetPool.value = []
  }
}
/**
 * 明细行的预算行重解析。
 * 载入旧单与年度变更后都要走这里：旧行带的是上一年度的预算行，
 * 留着就会把占用记到错的科目上（跨年的预算行仍会命中）。
 */
function resolveLineBudgets() {
  form.lines.forEach((l) => {
    l.budgetItemId = resolveLineBudgetId(l, {
      pool: budgetPool.value, map: itemBudgetMap.value,
      companyId: form.bill.companyId, deptId: form.bill.deptId,
      budgetYear: form.bill.periodYear,
    })
  })
}
/**
 * 费用年度一变，预算池与每行的预算行都要跟着变。
 * 原来年度只影响提交时的字段，不影响预算行的选取 ⇒ 改完年度单子还占着上一年度的额度。
 */
watch(() => form.bill.periodYear, async (y) => {
  await loadBudgetPool(y)
  resolveLineBudgets()
})
const budgetAgg = computed(() => {
  const sum = {}
  form.lines.forEach((l) => {
    if (!l.budgetItemId) return
    const k = String(l.budgetItemId)
    sum[k] = (sum[k] || 0) + Number(l.amount || 0)
  })
  return sum
})
const budgetRows = computed(() => Object.keys(budgetAgg.value).map((bid) => {
  const b = budgetPool.value.find((x) => String(x.id) === bid)
  const loaded = !!b
  const avail = loaded
    ? Number(b.totalAmount || 0) + Number(b.adjustAmount || 0) - Number(b.occupyAmount || 0) - Number(b.usedAmount || 0)
    : null
  const used = budgetAgg.value[bid]
  const over = loaded && used > avail
  // 控制方式随行带出，取不到按 HARD fail-closed（与 PC 同判据）
  const control = (loaded && b.controlType) || 'HARD'
  return {
    bid, used, available: avail, over, unknown: !loaded, control,
    label: loaded ? budgetLabel(items.value, depts.value, [], b, { companies: companies.value, projects: [] })
      : budgetUnseenLabel(bid),
    flagCls: over ? (control === 'HARD' ? 'h5-flag--danger' : control === 'SOFT' ? 'h5-flag--warn' : 'h5-flag--info')
      : (loaded ? 'h5-flag--ok' : 'h5-flag--info'),
    flagText: over ? `超 ${round2(used - avail).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}（${controlText(control)}）`
      : (loaded ? (avail != null && used <= avail ? '额度内' : '—') : '额度未知'),
  }
}))
function controlText(c) { return { HARD: '硬控', SOFT: '软控', WARN: '预警' }[c] || c }
const budgetWarnRows = computed(() => budgetRows.value.filter((r) => r.over))
const budgetHard = computed(() => budgetWarnRows.value.filter((r) => (r.control || 'HARD') === 'HARD'))
const budgetSoft = computed(() => budgetWarnRows.value.filter((r) => r.control === 'SOFT'))

function budgetLabelOf(bid) {
  const r = budgetRows.value.find((x) => String(x.bid) === String(bid))
  return r && r.label ? `预算：${r.label}` : '预算：（未取到名称）'
}
function lineBudgetCls(l) {
  const r = budgetRows.value.find((x) => String(x.bid) === String(l.budgetItemId))
  return r ? r.flagCls : 'h5-flag--info'
}
function lineBudgetText(l) {
  const r = budgetRows.value.find((x) => String(x.bid) === String(l.budgetItemId))
  return r ? r.flagText : '—'
}

/* 收款对象联动 */
function onPayTargetChange() {
  // 员工报销默认户名＝本人（可改）；对公收款的户名必须人工核对，不替用户猜
  if (form.bill.payTarget === 'EMPLOYEE' && !String(form.bill.extJson.accountName || '').trim()) {
    form.bill.extJson.accountName = form.bill.applyUserName || myName.value
  }
  delete errs.payTarget
}

/* 票夹 */
const invDenied = ref(null)
const invError = ref('')
async function loadInvoicePool() {
  invDenied.value = null
  invError.value = ''
  // silent 只能挡住全局 toast，挡不住每次开页的无效往返与控制台 4030 噪声。
  if (!hasPerm('inv:list')) {
    invDenied.value = { msg: '', permKey: 'inv:list' }
    invPool.value = []
    return
  }
  try {
    // silent：员工岗通常没有 inv:list，刷新票夹被校验时
    // 只走下面的 invDenied 降级面板；全局拦截器再弹一次 toast 会把"识别成功"盖成权限报错。
    const d = await invoiceApi.page({ pageNum: 1, pageSize: 50 }, { silent: true })
    invPool.value = (d && d.list) || []
  } catch (e) {
    const denied = permDeniedOf(e)
    if (denied) invDenied.value = denied
    else invError.value = e.message || '票夹加载失败'
  }
}
function openInvoicePicker() { showInvPicker.value = true }
function toggleInvoice(id) {
  const idx = invoiceIds.value.indexOf(id)
  if (idx >= 0) invoiceIds.value.splice(idx, 1)
  else invoiceIds.value.push(id)
}

/* 附件上传（走发票识别通道：/expense/attach/upload）
     与电脑端同一接口、同一优先级次序——OFD/PDF/XML 先本地版式解析（不发识别请求），
     只有扫描版 PDF（无文本层）才由后端渲染首页 PNG 转图像识别通道。
     图像识别走发票能力网关（sys_config ocr.channel=gateway）；冷启动首支可能接近一分钟，
     仍在本请求 120s 覆写超时内（api.js attachUpload）。
     后端对识别失败是 invoice.ok=false 且不阻断上传（ExpenseAttachController 类注释），
     所以识别服务离线时这条链依然安全，只是票面要手工填写。 */
function pickFile() {
  if (fileRef.value) fileRef.value.click()
}
async function onPickFile(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  uploading.value = true
  uploadAborted.value = false
  for (const f of files) {
    if (uploadAborted.value) break
    // 每张一个 controller：多条串行上传时，中断只影响当前这一条（上一张已返回结果已定）
    uploadController = new AbortController()
    try {
      const file = await compressImage(f)
      if (uploadAborted.value) break
      const rec = await fileApi.attachUpload(file, form.bill.id || null, uploadController.signal)
      if (!rec || rec.id == null) throw new Error('未返回文件记录')
      const attachType = rec.attachType === 'INVOICE' ? 'INVOICE' : 'OTHER'
      if (!attaches.value.some((a) => a.fileId === rec.id)) {
        attaches.value = [...attaches.value, { fileId: rec.id, fileName: rec.fileName || f.name, attachType }]
      }
      if (attachType === 'INVOICE') {
        // 档位由后端 inPool/errorMsg 判定（唯一分档口 invoiceOutcome），
        // 不要在前端重算"金额自洽／发票号有无"，也不要只看 ok 就报「已识别发票」。
        const out = invoiceOutcome(rec.invoice, { fileName: rec.fileName || f.name, end: 'h5' })
        ElMessage({ type: out.tone, duration: 6000, message: out.message })
      } else {
        ElMessage.success(`附件已上传：${rec.fileName || f.name}`)
      }
    } catch (err) {
      if (isRequestCancelled(err)) {
        // 中断＝结果未知，不要报「上传失败」（用户会重传同一张票面 ⇒ ocr_record 叠记录）
        const out = abortedOutcome(f.name)
        ElMessage({ type: out.tone, duration: 8000, message: out.message })
        break
      }
      const d = permDeniedOf(err)
      ElMessage.error(`上传失败：${f.name}（${d ? d.msg : (err.message || '网络异常')}）`)
    } finally {
      uploadController = null
    }
  }
  uploading.value = false
  loadInvoicePool()
}

/* 校验与校验 */
function validateForm() {
  clearErrs()
  // 表头必输：直接吃 PC 的登记表（同一份数据 ⇒ 两端不会再各说各话）
  const miss = missingBillField(form.bill, requiredOf('CLAIM', ''))
  if (miss) {
    const leaf = String(miss.field).split('.').pop()
    setErr(leaf, miss.tip)
    ElMessage.warning(miss.tip)
    scrollToField(leaf)
    return false
  }
  // 逐行校验（原实现只查 lines[0]）
  let firstBad = ''
  form.lines.forEach((l, i) => {
    if (l.itemId == null && Number(l.amount || 0) > 0) { setErr(`line${i}-itemId`, '这一行还没选费用项，选了才能提交'); firstBad = firstBad || `line${i}-itemId` }
    if (l.itemId != null && !(Number(l.amount) > 0)) { setErr(`line${i}-amount`, '金额必须大于 0'); firstBad = firstBad || `line${i}-amount` }
    if (l.itemId != null && !l.expenseDate) { setErr(`line${i}-expenseDate`, '请选择费用日期'); firstBad = firstBad || `line${i}-expenseDate` }
  })
  const usable = form.lines.filter((l) => l.itemId != null && Number(l.amount) > 0)
  if (!usable.length) {
    ElMessage.warning(items.value.length
      ? '请至少填写一条完整费用明细（费用项 + 金额）'
      : '费用项目基础数据没加载出来，请下拉重试或到电脑端提单')
    return false
  }
  if (firstBad) { scrollToField(firstBad); return false }
  // 只提交已选费用项的行（PC 同判据：item_id NOT NULL，带着空行提交会整单 500）
  return true
}
/** 预算校验（语义逐条对齐 useBill.js:605-629）：HARD 拦死；SOFT 必须留理由；WARN 只提醒 */
async function gateBudget() {
  const overs = budgetWarnRows.value
  if (!overs.length) return true
  if (budgetHard.value.length) {
    ElMessage.error('预算超出可用额度（硬控方案），无法提交')
    return false
  }
  if (budgetSoft.value.length) await askOverrunReason()
  const warn = overs.filter((x) => x.control === 'WARN')
  if (warn.length && !budgetSoft.value.length) {
    ElMessage.warning(`有 ${warn.length} 项预算超出可用额度（预警方案），本次放行、占用流水记录`)
  }
  return !!form.bill.extJson.overrunReason || !budgetSoft.value.length
}
async function askOverrunReason() {
  if (String(form.bill.extJson.overrunReason || '').trim()) return true
  try {
    const r = await ElMessageBox.prompt(
      `${budgetSoft.value.length} 项预算超出可用额度，当前为软控方案：请填写超支理由（随单据记录，服务端按同一规则校验）`,
      '软控超支·需填写理由',
      { type: 'warning', inputValidator: (v) => (v && v.trim() ? true : '超支理由不能为空') })
    form.bill.extJson = { ...form.bill.extJson, overrunReason: String(r.value || '').trim() }
    return true
  } catch { return false } // 取消＝不写理由
}
/** 「必附发票」判据落在写库之前（服务端 PrecheckService 同一规则，不宽于后端）
 *  判据收在 needInvoiceAttach 这个 computed 里，收起态的附件卡摘要也能提前说出"缺发票附件"。 */
function gateInvoiceAttach() {
  if (hasInvoiceAttach.value || !needInvoiceAttach.value) return true
  ElMessage.error('所选费用项要求必须附发票：请用「拍票据 / 上传发票」上传后再提交（从票夹关联只登记票面信息，不构成发票附件）')
  folds.attach = true
  return false
}

/* 写入数据库 */
function buildPayload() {
  // 埋点：随载荷带出"本次移动端会话停留毫秒数"（规则与两处已知失真见 sessionOpenedAt 注释）。
  // 只写进这次提交的 ext 副本，不回写 form.bill.extJson——否则页面上每次重新组包都会把停留数滚大。
  const ext = { ...form.bill.extJson, h5StayMs: Math.max(0, Date.now() - sessionOpenedAt) }
  return {
    bill: {
      ...form.bill,
      // 渠道记录：新建环节显式标 H5（后端 ExpenseBillService#normalizeBillSource 白名单只认 PC/H5，
      // 不传则由后端补 PC／由列默认值得到同一个结果）。
      // 编辑与打回重交环节原样回传库里既有值——source 记的是"这单从哪个渠道发起"，
      // PC 上建的单在手机改完重交仍算 PC 单；取不到既有值时整个键省略（JSON.stringify 丢 undefined），
      // 后端编辑环节走 keepBlank 分支保留原值，不会被静默洗成 PC。
      source: form.bill.id ? (form.bill.source || undefined) : 'H5',
      totalAmount: totalAmount.value,
      // 同 PC 前端载荷环节：户名镜像进 payee_name，出纳与付款批次取的是这一列
      payeeName: form.bill.payeeName || ext.accountName || '',
      extJson: JSON.stringify(ext),
    },
    // 配套：只提交选过费用项的行，未选行留在页面上
    lines: form.lines.filter((l) => l.itemId != null).map((l) => ({
      ...l,
      amount: Number(l.amount || 0),
      taxAmount: Number(l.taxAmount || 0),
      netAmount: Number(l.netAmount || 0),
      currencyCode: 'CNY',
      extJson: JSON.stringify(l.extJson || {}),
    })),
    // 后端 BillReq.attaches 是 List<ExpBillAttach>，对象数组不是 id 数组
    attaches: attaches.value.map((a) => ({ fileId: a.fileId, fileName: a.fileName, attachType: a.attachType })),
  }
}
async function doSaveDraft() {
  try {
    const id = await billApi.saveDraft(buildPayload())
    if (id == null || id === '') return null
    form.bill.id = Number(id)
    await syncInvoiceBinding(Number(id))
    dumpDraft()
    return Number(id)
  } catch { return null } // 业务错误已由 api.js 拦截器统一弹（去重后不会双弹）
}
/** 发票占用增量同步：只 bind 新选的、只 unbind 被摘掉的原有发票（bind 是追加语义，重复发＝重复占用） */
async function syncInvoiceBinding(billId) {
  const was = boundInitially.value
  const now = invoiceIds.value.map((x) => String(x))
  const toBind = invoiceIds.value.filter((x) => !was.has(String(x)))
  const toUnbind = [...was].filter((x) => !now.includes(x))
  if (toBind.length) {
    try { await invoiceApi.bind({ billId, invoiceIds: toBind.slice() }) }
    catch (e) { ElMessage.warning('发票关联没成功（单据已保存）：' + e.message) }
  }
  if (toUnbind.length && form.bill.billNo) {
    for (const iid of toUnbind) {
      try { await invoiceApi.unbind({ billNo: form.bill.billNo, invoiceId: Number(iid) }) }
      catch (e) { ElMessage.warning(`发票 ${iid} 取消关联没成功（单据已保存）：${e.message}`) }
    }
  }
  boundInitially.value = new Set(now)
}
async function loadFirstPick(billId) {
  try {
    const d = (await wfApi.startCandidates({
      bizType: 'EXP_CLAIM', bizId: billId, subType: undefined,
      amount: totalAmount.value || undefined,
    }, { silent: true })) || {}
    firstPick.mode = d.mode || 'AUTO'
    firstPick.nodeName = d.nodeName || ''
    firstPick.required = !!d.required
    firstPick.candidates = Array.isArray(d.candidates) ? d.candidates : []
    if (firstPick.candidates.length === 1) firstPick.userId = firstPick.candidates[0].userId
    else if (!firstPick.candidates.some((c) => c.userId === firstPick.userId)) firstPick.userId = null
  } catch {
    // 端点缺失/网络异常 ⇒ 降级为不显示选人区，绝不让提单因此提交不了（PC 同判据）
    firstPick.mode = 'AUTO'; firstPick.required = false; firstPick.candidates = []; firstPick.userId = null
  }
}
async function doSubmit(id) {
  try {
    await billApi.submit(id, null, firstPick.mode === 'PICK' ? firstPick.userId : null)
    pushRecentItems(userId.value, form.lines.filter((l) => l.itemId != null).map((l) => l.itemId))
    clearLocalDraft(userId.value, draftDoc())
    savedJustNow = true
    dirty.value = false
    ElMessage.success('已提交审批')
    router.replace('/h5')
  } catch { /* 拦截器已弹错；草稿仍在页面与本机 */ }
}
async function submit() {
  // 点击即占住 busy：前置校验、软控理由弹窗、存草稿、首审选人、提交整条链都在同一个罩子里。
  // 罩子原来从 doSaveDraft 才开始，前面那段（校验＋弹窗＋一次预算判据）里按钮仍可点，
  // 二次点击会开出第二条链 ⇒ 同一张单落两次草稿、起两个流程实例。
  if (busy.value) return
  busy.value = true
  try {
    if (!validateForm()) return
    if (!(await gateBudget())) return
    if (!gateInvoiceAttach()) return
    const id = await doSaveDraft()
    if (id == null) return
    await loadFirstPick(id)
    if (firstPick.mode === 'PICK' && !firstPick.candidates.length) {
      ElMessage.error(`首审环节「${firstPick.nodeName || '第一道审批'}」在本单据公司下暂无可选办理人，请先在电脑端「系统管理 · 角色授权」补岗`)
      return
    }
    if (firstPick.mode === 'PICK' && firstPick.required && !firstPick.userId) {
      ElMessage.warning('草稿已保存，还差首审办理人：请选择后再提交')
      return
    }
    await doSubmit(id)
  } finally { busy.value = false }
}
async function saveDraftOnly() {
  if (busy.value) return
  if (!validateForm()) return
  busy.value = true
  try {
    const id = await doSaveDraft()
    if (id != null) {
      ElMessage.success(`草稿已保存（${isEdit.value ? '原单已更新' : '单号稍后可在「单据」页看到'}），还没提交`)
      await loadFirstPick(id)
      dirty.value = false
    }
  } finally { busy.value = false }
}

/* 本机草稿 */
// 编辑态与新建态分开存，避免"改打回单"把新建草稿冲掉
function draftDoc() { return isEdit.value ? `CLAIM#${editId.value}` : 'CLAIM' }
let draftTimer = null
function dumpDraft() {
  saveLocalDraft(userId.value, draftDoc(), {
    bill: JSON.parse(JSON.stringify(form.bill)),
    lines: JSON.parse(JSON.stringify(form.lines)),
    attaches: JSON.parse(JSON.stringify(attaches.value)),
    invoiceIds: invoiceIds.value.slice(),
  })
}
function restoreDraft() {
  const d = loadLocalDraft(userId.value, draftDoc())
  if (!d || !d.bill) return false
  hydrating = true
  form.bill = { ...newBill(), ...d.bill, extJson: { ...newBill().extJson, ...(d.bill.extJson || {}) } }
  form.lines = (Array.isArray(d.lines) && d.lines.length) ? d.lines.map((l) => ({ ...newLine(), ...l })) : [newLine()]
  attaches.value = Array.isArray(d.attaches) ? d.attaches : []
  invoiceIds.value = Array.isArray(d.invoiceIds) ? d.invoiceIds : []
  restoredAt.value = Number(d.savedAt) || Date.now()
  nextTick(() => { hydrating = false })
  return true
}
function discardDraft() {
  clearLocalDraft(userId.value, draftDoc())
  restoredAt.value = 0
  ElMessage.success('已丢弃本机草稿（已保存的正式草稿单不受影响）')
}
watch([() => form.bill, () => form.lines, attaches, invoiceIds], () => {
  if (hydrating) return
  dirty.value = true
  clearTimeout(draftTimer)
  draftTimer = setTimeout(dumpDraft, 800)
}, { deep: true })

/* 原单载入（打回重交留在移动端） */
/**
 * 取上一轮的驳回意见。
 *
 * 时机是硬约束：editForResubmit 会把单子转回 DRAFT 并把 proc_inst_id 置 NULL
 * （ExpenseBillService.returnToEdit），之后审批链就查不到了 ⇒ 必须在转态之前取。
 * 权威来源＝审批链上的 REJECT 节点（/wf/query/chain 无 wf:history:list 权限要求，
 * 提单人可读）；链读不到时退回单据上的 audit_remark（ExpenseWfEventListener 写驳回意见时落的字段）。
 */
async function fetchRejectInfo(bill) {
  const pid = bill?.procInstId
  if (pid) {
    try {
      const chain = await flowApi.chain(pid, { silent: true })
      const rows = Array.isArray(chain) ? chain : ((chain && chain.list) || [])
      const rej = [...rows].reverse().find((n) => n && (n.action === 'REJECT' || n.state === 'REJECTED'))
      if (rej) {
        return {
          comment: rej.comment || bill.auditRemark || '',
          nodeName: rej.nodeName || '',
          handler: rej.handler || '',
          time: rej.time ? formatDateTime(rej.time) : '',
        }
      }
    } catch { /* 链读不到不算失败，退回 audit_remark */ }
  }
  if (bill?.auditRemark) return { comment: bill.auditRemark, nodeName: '', handler: '', time: '' }
  return null
}
async function loadOriginal(id) {
  let d
  try {
    d = await billApi.detail(id)
    if ((d.bill || {}).status === 'REJECTED') {
      // 驳回意见必须在转态之前取（转态会把 proc_inst_id 置空）
      rejectRemark.value = await fetchRejectInfo(d.bill || {})
      // 与 PC 同一支：打回单要先转回可编辑态再回读（editForResubmit 是后端状态机硬要求）
      await billApi.editForResubmit(id)
      d = await billApi.detail(id)
    } else if ((d.bill || {}).status === 'DRAFT' && (d.bill || {}).auditRemark) {
      // 第二次进入：上一进已把单子转成 DRAFT 并清掉 proc_inst_id，审批链查不到了，
      // 但驳回意见还落在 audit_remark 上（提交成功会清空它），照样要显示出来。
      rejectRemark.value = await fetchRejectInfo(d.bill || {})
    }
  } catch (e) {
    loadError.value = `原单载入失败：${e.message || '网络异常'}。请确认这张单还归你、且是费用报销单。`
    return
  }
  const bl = d.bill || {}
  if (!bl.id) { loadError.value = '没取到这张单据，返回「单据」页重新进入。'; return }
  if (bl.billType !== 'CLAIM') {
    loadError.value = `这张单是「${bl.billType || '未知'}」类型，移动端只能改费用报销。请到电脑端修改后重新提交。`
    return
  }
  hydrating = true
  let ext = {}
  try { ext = typeof bl.extJson === 'string' ? JSON.parse(bl.extJson || '{}') : (bl.extJson || {}) } catch { ext = {} }
  form.bill = { ...newBill(), ...bl, extJson: { ...newBill().extJson, ...ext } }
  if (form.bill.extJson.paperAttachCount == null) form.bill.extJson.paperAttachCount = 0
  if (Array.isArray(d.lines) && d.lines.length) {
    // 与 PC 同判据：明细行 id 置空 ⇒ 重新提交是重建行，不去改已审批过的历史行
    form.lines = d.lines.map((l) => ({
      ...newLine(), ...l, id: null, quantity: null, unitPrice: null,
      extJson: typeof l.extJson === 'string' ? safeParse(l.extJson) : (l.extJson || {}),
    }))
  }
  attaches.value = Array.isArray(d.attaches) ? d.attaches.map((a) => ({ ...a, id: null })) : []
  // 预算行按原单的年度重解析：
  // 原单带的是上一年度的预算行，直接留着会把占用记到别的科目上。
  await loadBudgetPool(form.bill.periodYear)
  resolveLineBudgets()
  // 本单已勾稽发票回读（补充信息：无 inv:bind/读权限时静默失败，不该挡住"改这张单"本身）
  boundRows.value = []
  invoiceIds.value = []
  boundInitially.value = new Set()
  try {
    const bound = (await invoiceApi.billInvoices(id, { silent: true })) || []
    boundRows.value = Array.isArray(bound) ? bound : []
    invoiceIds.value = boundRows.value.map((x) => x.id).filter((x) => x != null)
    boundInitially.value = new Set(invoiceIds.value.map(String))
  } catch { /* 读不到就按"未关联"呈现，提交时不会重复占用 */ }
  nextTick(() => { hydrating = false; dirty.value = false })
}
function safeParse(s) { try { return JSON.parse(s || '{}') } catch { return {} } }

/* 离开确认（UX-06） */
function leave() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/h5')
}
onBeforeRouteLeave(async () => {
  if (savedJustNow || !dirty.value || loadError.value) return true
  try {
    await ElMessageBox.confirm('这张单还没提交。本机草稿已经自动留着，随时回来接着填。', '离开提单页', {
      type: 'warning', confirmButtonText: '知道了，离开', cancelButtonText: '继续填写',
    })
  } catch (a) {
    if (a === 'close') return false // 点遮罩/关闭＝留在本页
    return false                   // 「继续填写」
  }
  return true
})

/* 基础数据 */
async function loadMd() {
  const [it, map, comp, dept] = await Promise.all([
    mdApi.expenseItems().catch(() => null),
    masterCfgApi.itemBudgetMap.options().catch(() => null),
    orgApi.companyTree().catch(() => null),
    orgApi.deptTree().catch(() => null),
  ])
  // /expense-item/tree 返回平铺清单（ExpenseItemService.tree() 只做排序，不组装 children），
  // 所以旧页面那段"展平 children"是死代码，这里直接按 itemName 过滤空名。
  items.value = (it || []).filter((x) => x && x.itemName && String(x.itemName).trim())
  const m = {}
  ;(map || []).forEach((x) => { if (x && x.itemId != null && x.budgetItemId != null) m[String(x.itemId)] = x.budgetItemId })
  itemBudgetMap.value = m
  const cf = []; (function walk(arr) { (arr || []).forEach((n) => { if (n) { cf.push({ id: n.id, name: n.companyName || n.name || '' }); if (n.children?.length) walk(n.children) } }) })(comp)
  companies.value = cf
  const df = []; (function walk(arr) { (arr || []).forEach((n) => { if (n) { df.push({ id: n.id, name: n.deptName || n.name || '' }); if (n.children?.length) walk(n.children) } }) })(dept)
  depts.value = df
  // 预算行按单据费用年度取
  await loadBudgetPool(form.bill?.periodYear)
}

const recentChips = computed(() => recentItemIds(userId.value, 5)
  .map((id) => items.value.find((x) => String(x.id) === String(id)))
  .filter(Boolean))

async function boot() {
  payMethodDict.load()
  payeeTypeDict.load()
  resetSessionClock() // 埋点起点：本页本次打开（规则见 sessionOpenedAt 注释）
  bootLoading.value = true
  loadError.value = ''
  try {
    await loadMd()
    if (editId.value) {
      await loadOriginal(editId.value)
      // 库里这单是准，本机若还留着同一张单的未保存编辑，则覆盖回来（草稿键含单号，不会串到别的单）
      if (!loadError.value) restoreDraft()
    } else {
      const restored = restoreDraft()
      if (!restored) { form.bill = newBill(); form.lines = [newLine()]; attaches.value = []; invoiceIds.value = [] }
    }
    loadInvoicePool()
  } catch (e) {
    loadError.value = `页面数据加载失败：${e.message || '网络异常'}`
  } finally {
    nextTick(() => { hydrating = false; dirty.value = false; bootLoading.value = false })
  }
}

onMounted(boot)
onActivated(() => {
  // keep-alive 回访：上一次刚提交成功 ⇒ 换一张干净的单，绝不把已提交的内容留在屏上
  if (savedJustNow) { savedJustNow = false; boot() }
})
onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  // 离开本页会掐断进行中的识别请求，而后端识别链（REQUIRES_NEW）并不因此回滚 ⇒
  // 只报「结果未知＋去「发票」列表核对」，不要报"已取消/未保存"（用户会重传同一张票面）。
  if (uploading.value) {
    if (uploadController) uploadController.abort()
    const out = abortedOutcome('')
    ElMessage({ type: out.tone, duration: 8000, message: out.message })
  }
})
watch(editId, () => {
  // 同一 keep-alive 实例下在「新建 ↔ 编辑某张单」之间切换时 boot() 不会再跑，计时起点会留在上一张单上。
  // 单号一变就是另一段会话 ⇒ 这里补一次复位；boot() 自己那次重复无害（同一毫秒级动作）。
  resetSessionClock()
})
</script>

<style scoped>
/* 明细块之间的分隔：只留页面特有的一件，其余全部走 h5-tokens.css */
.h5-lines { border-top: 1px dashed var(--h5-border); padding-top: 8px; margin-top: 8px; }
.h5-lines:first-of-type { border-top: 0; padding-top: 0; margin-top: 0; }
.h5-budget { background: #f7fbff; border-radius: var(--h5-radius-sm); padding: 6px 8px; margin-top: 6px; }
</style>
