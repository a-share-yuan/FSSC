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
      <!-- 原单载入失败：必须明示。改造前是"catch 后照常显示空表单"⇒ 用户以为在改打回的单，
           实际新建了一张，原单还在打回状态（P0 数据丢失腿之一）。 -->
      <section v-if="loadError" class="h5-body">
        <div class="h5-error">
          <div class="h5-error__d">{{ loadError }}</div>
        </div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="boot">重新载入</button>
        <button class="h5-btn h5-btn--sub h5-mt-8" @click="leave">返回</button>
      </section>

      <section v-else class="h5-body">
        <!-- 本机草稿恢复提示（P1-4 / UX-06：填一半被返回就没了） -->
        <div v-if="restoredAt" class="h5-denied">
          <div class="h5-denied__t">已恢复本机未提交的草稿</div>
          <div class="h5-denied__d">保存于 {{ timeText(restoredAt) }}。提交成功后会自动清掉；不想接着填可点下面丢弃。</div>
          <button class="h5-btn h5-btn--sub h5-mt-8" @click="discardDraft">丢弃本机草稿</button>
        </div>

        <!-- 单据归属：只读展示，避免"我在哪个公司/部门"靠猜（跨法人会被 OrgGuard 拦下） -->
        <div class="h5-card">
          <div class="h5-card__row">
            <span class="h5-card__title h5-flush">费用报销 · 人民币 CNY</span>
            <span class="h5-flag h5-flag--info">{{ isEdit ? '修改原单' : '新建' }}</span>
          </div>
          <div class="h5-card__meta">申请人：{{ form.bill.applyUserName || myName }}（自动归属本人，不可代提）</div>
          <div class="h5-card__meta">公司 {{ form.bill.companyId || '—' }} · 部门 {{ form.bill.deptId || '—' }}</div>
          <div class="h5-tip h5-tip--left">
            移动端只收「费用报销」。差旅 / 事前申请 / 借款的明细段在电脑端才齐，手机上强提会交出一张缺件单，
            请到电脑端对应页面提单。
          </div>
        </div>

        <!-- ============ 表头（必输项与电脑端同一份登记表：constants/billRequired.js） ============ -->
        <div class="h5-card">
          <div class="h5-section-title h5-flush">基本信息</div>

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
        </div>

        <!-- ============ 费用明细（P0-9：逐行校验 + 字段级错误，不再只查第一行） ============ -->
        <div class="h5-card">
          <div class="h5-section-title h5-flush">费用明细（{{ form.lines.length }} 行）</div>

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

            <!-- 预算占用实时预览（P1-7）：额度不够在提单前就知道，而不是提交被拦才发现 -->
            <div v-if="l.budgetItemId" class="h5-budget">
              <div class="h5-card__row">
                <span class="h5-card__meta">{{ budgetLabelOf(l.budgetItemId) }}</span>
                <span class="h5-flag" :class="lineBudgetCls(l)">{{ lineBudgetText(l) }}</span>
              </div>
            </div>
          </div>

          <button class="h5-btn h5-btn--ghost h5-mt-8" @click="addLine">+ 添加明细</button>
        </div>

        <!-- ============ 预算占用汇总 + 闸门状态（判据与 PC useBill.js:605-629 同源） ============ -->
        <div v-if="budgetRows.length" class="h5-card">
          <div class="h5-section-title h5-flush">预算占用</div>
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
            {{ budgetWarnRows.length }} 项预警超支：本次放行，占用流水会留痕。
          </div>
        </div>

        <!-- ============ 附件 / 票据影像（P0-5：真 ref，原 document.getElementById 必报 TypeError） ============ -->
        <div class="h5-card">
          <div class="h5-section-title h5-flush">附件 / 票据影像</div>
          <input ref="fileRef" type="file" accept="image/*,.pdf,.ofd,.xml" multiple hidden @change="onPickFile" />
          <button class="h5-btn h5-btn--ghost" :disabled="uploading" @click="pickFile">
            {{ uploading ? '上传识别中…' : (hasInvoiceAttach ? '继续上传' : '拍票据 / 上传发票') }}
          </button>
          <!-- D-DEC-463：长请求（扫描版 PDF 图像识别 30~60 秒）必须给一个出口，
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
        </div>

        <!-- ============ 关联票夹发票（三态：缺权 / 出错 / 真的空，P1-3） ============ -->
        <div class="h5-card">
          <div class="h5-section-title h5-flush">关联发票</div>
          <template v-if="invDenied">
            <div class="h5-denied">
              <div class="h5-denied__t">票夹列表对当前账号不开放</div>
              <div class="h5-denied__d">
            缺权码：{{ invDenied.permKey || 'inv:list' }}。这不代表你没有发票——上面拍的票据仍会作为附件随单提交，
            发票台账由发票管理岗在电脑端协同。
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
        </div>

        <!-- ============ 收款信息（后端 assertPayeeAccount：CLAIM 金额>0 时收款对象＋收款账号硬必填） ============
             改造前 H5 表单根本没有这一段 ⇒ 附件腿修好后，提交仍会被 PARAM_INVALID 打回，
             表现为「移动端提单一直失败」。这里补齐，判据与 ExpenseBillService.assertPayeeAccount 一致。 -->
        <div class="h5-card">
          <div class="h5-section-title h5-flush">收款信息</div>
          <div class="h5-field" :class="{ 'is-error': errOf('payTarget') }">
            <label for="h5-f-payTarget">收款对象</label>
            <select id="h5-f-payTarget" v-model="form.bill.payTarget" @change="onPayTargetChange">
              <option :value="''">请选择</option>
              <option value="EMPLOYEE">员工（报给自己）</option>
              <option value="SUPPLIER">供应商</option>
              <option value="CUSTOMER">客户</option>
            </select>
          </div>
          <div v-if="errOf('payTarget')" class="h5-field__err">{{ errOf('payTarget') }}</div>

          <div class="h5-field" :class="{ 'is-error': errOf('payMethod') }">
            <label for="h5-f-payMethod">收款方式</label>
            <select id="h5-f-payMethod" v-model="form.bill.extJson.payMethod">
              <option :value="''">请选择</option>
              <option value="BANK">银行转账</option>
              <option value="CASH">现金</option>
              <option value="CHECK">支票</option>
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
        </div>

        <!-- 首审办理人（PICK 模式才出现；与 PC 同判据：需要点名却没点就不提交） -->
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

      <!-- ============ 吸底三出口（§4.2-③：合计常驻＋存草稿＋提交；提交不再是唯一出口） ============ -->
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
         P2-5：进出场走 <Transition name="h5-pop"> -->
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
 * 移动报销端 · 提单页（P0 止血 + P1-4/P1-7/P1-10 落点）
 *
 * 本窗改造前实测到的四条会丢数据的腿，逐条对应下面的注释：
 *  P0-2 `attaches: attaches.map(a => a.id)` —— 后端 BillReq.attaches 是 List<ExpBillAttach>，
 *       数字数组反序列化必炸（保存草稿即 500）。现按 PC 同形状传 {fileId,fileName,attachType}。
 *  P0-3 companyId/deptId 硬编码 1/1 —— 跨法人账号（如 co33）被 OrgGuard.assertCompanyAllowed 拒，
 *       即便通过也是把部门预算记到别的主体。现取登录态。
 *  P0-5 `@click="document.getElementById('h5-file').click()"` —— Vue 3 模板全局白名单不含 document，
 *       运行时 _ctx.document＝undefined ⇒ TypeError，两个上传按钮全是死的。现用真 ref。
 *  P0-9 校验只查 form.lines[0] —— 第二行起金额为空/没选费用项也照样提交，
 *       后端 item_id NOT NULL 直接 500（整单回滚，用户看不到原因）。现逐行校验＋字段级错误。
 *
 * 另补一段 PC 有、H5 整块缺失的「收款信息」：ExpenseBillService.assertPayeeAccount
 * （实测源码 1099-1122 行）对 CLAIM 且金额>0 强制 payTarget＋payeeAccountNo，
 * 缺任一条提交即 PARAM_INVALID。改造前 H5 表单没有这两个字段 ⇒ 移动端提单从来没可能成功。
 */
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, mdApi, masterCfgApi, orgApi, invoiceApi, fileApi, wfApi, store, isRequestCancelled } from '../../api'
import { requiredOf, missingBillField } from '../../constants/billRequired.js'
import { invoiceOutcome, abortedOutcome } from '@/utils/invoiceOutcome'
import { pickBudgetLine } from '../../utils/budget.js'
import { splitTaxInclusive } from '../../utils/tax.js'
import { budgetLabel, budgetUnseenLabel } from '../../utils/format'
import { money, dayText, permDeniedOf, saveLocalDraft, loadLocalDraft, clearLocalDraft, pushRecentItems, recentItemIds, compressImage } from '@/utils/h5.js'

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

// D-DEC-361（补漏腿·H5 制单页）：预填日期不取 toISOString().slice(0,10)＝UTC 串——
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
    // P0-3：公司/部门取登录态（PC useBill.js 同一写法），跨法人单据会被后端 OrgGuard 拒
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
 * P2-6 埋点：本次移动端填报会话的起始钟点。
 *
 * 口径写清楚，别把它当"提单耗时"的全生命周期指标：
 *  - 起点＝本页本次打开（新建走 onMounted boot、切回本页走 onActivated 复位），终点＝**这一次**组装载荷的时刻；
 *  - 中途离开页面再回来 ⇒ 计时重置，所以 `h5StayMs` 是"最后一段连续填报会话"的毫秒数，不是累计；
 *  - 存进 `exp_bill.ext_json` 的 `h5StayMs` 一个键，不新建表、不加接口、不动 PC 逻辑。
 * 两处已知失真，如实记下不掩饰：
 *  ① PC 端保存会按它自己的表单模型重写 ext_json ⇒ 该键可能被洗掉，本字段口径＝"最后一次保存方"；
 *  ② 页面停在后台（切走 App）也照常计时，手机上的"停留"不等于"在填"。
 * 方案册 P2-6 里"平均提单时长"用的就是这条腿，取值时须按 ext_json.h5StayMs 的 NULL/非 NULL 分档，
 * 不能拿全表平均值当移动端指标（PC 单没有这个键）。
 */
let sessionOpenedAt = Date.now()
function resetSessionClock() { sessionOpenedAt = Date.now() }

const items = ref([])
const budgetPool = ref([])
const itemBudgetMap = ref({})
const companies = ref([])
const depts = ref([])
const attaches = ref([])
const invPool = ref([])
const invoiceIds = ref([])
// 编辑态里"进页面时就已经挂在这张单上的发票"：bind 后端是**追加占用**语义
// （InvoiceController.bind → 逐票 bindToBill，实测源码 85-95 行），把已绑过的再发一遍＝重复占用。
// 故只对增量 id 调 bind、对被摘掉的原有发票调 unbind，避免"改一次单发票被占两次"。
const boundInitially = ref(new Set())
const boundRows = ref([])

const bootLoading = ref(true)
const loadError = ref('')
const busy = ref(false)
const uploading = ref(false)
/* D-DEC-463（10-05）：「上传＋识别」在同一个请求里串行，而后端识别链是 REQUIRES_NEW 独立事务 ⇒
   用户按「取消等待」只是断开这一条 HTTP 连接，服务端并不会因此停手（实测 18:33:27 关浏览器后
   18:33:33 仍落 ocr_record、18:33:37 仍入池）。所以中断只报「结果未知」，见 abortedOutcome。 */
let uploadController = null
const uploadAborted = ref(false)
function cancelUploadWait() {
  uploadAborted.value = true
  if (uploadController) uploadController.abort()
}
const showInvPicker = ref(false)
const restoredAt = ref(0)
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

/* ---------------- 字段级错误 ---------------- */
function errOf(k) { return errs[k] || '' }
function setErr(k, msg) { errs[k] = msg }
function clearErrs() { Object.keys(errs).forEach((k) => delete errs[k]) }
async function scrollToField(k) {
  await nextTick()
  const el = document.getElementById('h5-f-' + k)
  if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' })
}

/* ---------------- 明细 ---------------- */
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
  // 与 PC 同一价内税口径（utils/tax.js），避免"移动端税额＝金额×税率"的另一种算法
  const r = splitTaxInclusive(l.amount, l.taxRate)
  l.taxAmount = r.taxAmount
  l.netAmount = r.netAmount
}
function onItemChange(l) {
  const it = items.value.find((x) => String(x.id) === String(l.itemId))
  l.itemName = it?.itemName || ''
  if (l.budgetItemId == null) l.budgetItemId = pickBudgetFor(l.itemId)
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

/* ---------------- 预算（判据逐字对齐 useBill.js:319-343 / 605-629） ---------------- */
function pickBudgetFor(itemId) {
  return pickBudgetLine({
    pool: budgetPool.value, itemId, map: itemBudgetMap.value,
    companyId: form.bill.companyId, deptId: form.bill.deptId,
  })
}
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
  // D-DEC-372：控制方式随行带出，取不到按 HARD fail-closed（与 PC 同判据）
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

/* ---------------- 收款对象联动 ---------------- */
function onPayTargetChange() {
  // 员工报销默认户名＝本人（可改）；对公收款的户名必须人工核对，不替用户猜
  if (form.bill.payTarget === 'EMPLOYEE' && !String(form.bill.extJson.accountName || '').trim()) {
    form.bill.extJson.accountName = form.bill.applyUserName || myName.value
  }
  delete errs.payTarget
}

/* ---------------- 票夹 ---------------- */
const invDenied = ref(null)
const invError = ref('')
async function loadInvoicePool() {
  invDenied.value = null
  invError.value = ''
  try {
    // silent：员工岗通常没有 inv:list（实测 3867 只持申请类角色），刷新票夹被闸时
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

/* ---------------- 附件上传（走发票识别通道：/expense/attach/upload） ----------------
     10-05 D-DEC-474：本腿与电脑端是同一个接口、同一优先级次序——OFD/PDF/XML 先本地版式解析（不发识别请求），
     只有扫描版 PDF（无文本层）才由后端渲染首页 PNG 转图像识别通道。
     10-04 实测：paddle 通道（本机 :8087）已在跑，图片票识别往返 23.1s／26.9s（暖机后），
     冷启动首支 57.8s ⇒ 仍在本请求 120s 覆写超时内（api.js attachUpload）。
      现网通道已不是 paddle：sys_config ocr.channel=gateway（发票能力网关 :8096，10-04 19:19 起），
     10-05 11:1x 实测 :8096 LISTENING／invoice.gateway.enabled=true。
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
        // D-DEC-461：档位由后端 inPool/errorMsg 判定（唯一分档口 invoiceOutcome），
        // 在前端重算"金额自洽／发票号有无"，也只看 ok 就报「已识别发票」。
        const out = invoiceOutcome(rec.invoice, { fileName: rec.fileName || f.name, end: 'h5' })
        ElMessage({ type: out.tone, duration: 6000, message: out.message })
      } else {
        ElMessage.success(`附件已上传：${rec.fileName || f.name}`)
      }
    } catch (err) {
      if (isRequestCancelled(err)) {
        // D-DEC-463：中断＝结果未知，报「上传失败」（用户会重传同一张票面 ⇒ ocr_record 叠记录）
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

/* ---------------- 校验与闸门 ---------------- */
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
  // P0-9：逐行校验（原实现只查 lines[0]）
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
/** 预算闸（语义逐条对齐 useBill.js:605-629）：HARD 拦死；SOFT 必须留理由；WARN 只提醒 */
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
    ElMessage.warning(`有 ${warn.length} 项预算超出可用额度（预警方案），本次放行、占用流水留痕`)
  }
  return !!form.bill.extJson.overrunReason || !budgetSoft.value.length
}
async function askOverrunReason() {
  if (String(form.bill.extJson.overrunReason || '').trim()) return true
  try {
    const r = await ElMessageBox.prompt(
      `${budgetSoft.value.length} 项预算超出可用额度，当前为软控方案：请填写超支理由（随单据留痕，服务端同口径校验）`,
      '软控超支·需填写理由',
      { type: 'warning', inputValidator: (v) => (v && v.trim() ? true : '超支理由不能为空') })
    form.bill.extJson = { ...form.bill.extJson, overrunReason: String(r.value || '').trim() }
    return true
  } catch { return false } // 取消＝不写理由
}
/** D-B4-01：「必附发票」判据前移到写库之前（服务端 PrecheckService 同口径，不宽于后端） */
function gateInvoiceAttach() {
  if (hasInvoiceAttach.value) return true
  const need = form.lines.filter((l) => l.itemId != null).some((l) => {
    const it = items.value.find((x) => String(x.id) === String(l.itemId))
    return it && Number(it.needInvoice) === 1
  })
  if (need) {
    ElMessage.error('所选费用项要求必须附发票：请用「拍票据 / 上传发票」上传后再提交（从票夹关联只登记票面信息，不构成发票附件）')
    return false
  }
  return true
}

/* ---------------- 落库 ---------------- */
function buildPayload() {
  // P2-6 埋点：随载荷带出"本次移动端会话停留毫秒数"（口径与两处已知失真见 sessionOpenedAt 注释）。
  // 只写进这次提交的 ext 副本，不回写 form.bill.extJson——否则页面上每次重新组包都会把停留数滚大。
  const ext = { ...form.bill.extJson, h5StayMs: Math.max(0, Date.now() - sessionOpenedAt) }
  return {
    bill: {
      ...form.bill,
      // P2-1 渠道留痕：新建腿显式标 H5（后端 ExpenseBillService#normalizeBillSource 白名单只认 PC/H5，
      // 不传则由后端补 PC／由列默认值得到同一个结果）。
      // 编辑与打回重交腿原样回传库里既有值——source 记的是"这单从哪个渠道发起"，
      // PC 上建的单在手机改完重交仍算 PC 单；取不到既有值时整个键省略（JSON.stringify 丢 undefined），
      // 后端编辑腿走 keepBlank 分支保留原值，不会被静默洗成 PC。
      source: form.bill.id ? (form.bill.source || undefined) : 'H5',
      totalAmount: totalAmount.value,
      // D-DEC-368（同 PC 前端载荷腿）：户名镜像进 payee_name，出纳与付款批次取的是这一列
      payeeName: form.bill.payeeName || ext.accountName || '',
      extJson: JSON.stringify(ext),
    },
    // P0-9 配套：只提交选过费用项的行，未选行留在页面上
    lines: form.lines.filter((l) => l.itemId != null).map((l) => ({
      ...l,
      amount: Number(l.amount || 0),
      taxAmount: Number(l.taxAmount || 0),
      netAmount: Number(l.netAmount || 0),
      currencyCode: 'CNY',
      extJson: JSON.stringify(l.extJson || {}),
    })),
    // P0-2：后端 BillReq.attaches 是 List<ExpBillAttach>，对象数组不是 id 数组
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
  busy.value = true
  try {
    await billApi.submit(id, null, firstPick.mode === 'PICK' ? firstPick.userId : null)
    pushRecentItems(userId.value, form.lines.filter((l) => l.itemId != null).map((l) => l.itemId))
    clearLocalDraft(userId.value, draftDoc())
    savedJustNow = true
    dirty.value = false
    ElMessage.success('已提交审批')
    router.replace('/h5')
  } catch { /* 拦截器已弹错；草稿仍在页面与本机 */ } finally { busy.value = false }
}
async function submit() {
  if (busy.value) return
  if (!validateForm()) return
  if (!(await gateBudget())) return
  if (!gateInvoiceAttach()) return
  busy.value = true
  try {
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

/* ---------------- 本机草稿（P1-4） ---------------- */
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

/* ---------------- 原单载入（P1-10：打回重交留在移动端） ---------------- */
async function loadOriginal(id) {
  let d
  try {
    d = await billApi.detail(id)
    if ((d.bill || {}).status === 'REJECTED') {
      // 与 PC 同一支：打回单要先转回可编辑态再回读（editForResubmit 是后端状态机硬要求）
      await billApi.editForResubmit(id)
      d = await billApi.detail(id)
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
  form.lines.forEach((l) => { if (l.itemId != null && l.budgetItemId == null) l.budgetItemId = pickBudgetFor(l.itemId) })
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

/* ---------------- 离开确认（UX-06） ---------------- */
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

/* ---------------- 基础数据 ---------------- */
async function loadMd() {
  const [it, bg, map, comp, dept] = await Promise.all([
    mdApi.expenseItems().catch(() => null),
    mdApi.budgets({ pageNum: 1, pageSize: 200 }).catch(() => null),
    masterCfgApi.itemBudgetMap.options().catch(() => null),
    orgApi.companyTree().catch(() => null),
    orgApi.deptTree().catch(() => null),
  ])
  // /expense-item/tree 实测返回**平铺**清单（ExpenseItemService.tree() 只做排序，不组装 children），
  // 所以旧页面那段"展平 children"是死代码，这里直接按 itemName 过滤空名。
  items.value = (it || []).filter((x) => x && x.itemName && String(x.itemName).trim())
  budgetPool.value = (bg && bg.list) || []
  const m = {}
  ;(map || []).forEach((x) => { if (x && x.itemId != null && x.budgetItemId != null) m[String(x.itemId)] = x.budgetItemId })
  itemBudgetMap.value = m
  const cf = []; (function walk(arr) { (arr || []).forEach((n) => { if (n) { cf.push({ id: n.id, name: n.companyName || n.name || '' }); if (n.children?.length) walk(n.children) } }) })(comp)
  companies.value = cf
  const df = []; (function walk(arr) { (arr || []).forEach((n) => { if (n) { df.push({ id: n.id, name: n.deptName || n.name || '' }); if (n.children?.length) walk(n.children) } }) })(dept)
  depts.value = df
}

const recentChips = computed(() => recentItemIds(userId.value, 5)
  .map((id) => items.value.find((x) => String(x.id) === String(id)))
  .filter(Boolean))

async function boot() {
  resetSessionClock() // P2-6 埋点起点：本页本次打开（口径见 sessionOpenedAt 注释）
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
  // D-DEC-463：离开本页会掐断进行中的识别请求，而后端识别链（REQUIRES_NEW）并不因此回滚 ⇒
  // 只报「结果未知＋去「发票」列表核对」，报"已取消/未保存"（用户会重传同一张票面）。
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
