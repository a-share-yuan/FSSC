<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="h5-page h5-page--tabbed">
    <!-- 顶栏：sticky（全页唯一滚动容器＝文档）；右侧只放姓名。
         「退出」已移入「我的」页并加二次确认：它原先的点击热区只有 42px，
         而破坏性动作不该和常规动作同排。 -->
    <header class="h5-header">
      <span class="h5-header__title">{{ $t('h5.main.FSSC') }}</span>
      <span class="h5-header__sub">{{ myName }}</span>
    </header>

    <!-- 待办（审批） -->
    <section v-if="tab === 'todo'" class="h5-body">
      <!-- 三态·加载：骨架屏（原先只有「加载中…」一行字） -->
      <template v-if="todosLoading && !todos.length">
        <div v-for="i in 3" :key="i" class="h5-skeleton">
          <div class="h5-skeleton__line h5-skeleton__line--m" />
          <div class="h5-skeleton__line h5-skeleton__line--s" />
        </div>
      </template>
      <div v-else-if="todoError" class="h5-error">
        <div class="h5-error__d">{{ todoError }}</div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="reloadTodos">重试</button>
      </div>
      <template v-else>
        <div v-for="t in todos" :key="t.taskId" class="h5-card h5-card--tap" @click="openApproval(t)">
          <div class="h5-card__row">
            <span class="h5-card__title h5-flush">{{ t.title || t.taskName }}</span>
            <span v-if="stayOf(t.createTime)" class="h5-flag" :class="[stayOf(t.createTime).cls]" :title="'到达时间 ' + t.createTime">
              {{ stayOf(t.createTime).text }}
            </span>
          </div>
          <div class="h5-card__meta">节点：{{ t.taskName || '—' }}</div>
          <div class="h5-card__meta">单据编号：{{ t.billNo || '—' }} · {{ docTypeLabel(t.billType) }}</div>
          <div v-if="t.totalAmount != null" class="h5-card__meta">金额：￥{{ money(t.totalAmount) }}</div>
          <div v-if="t.assigneeName" class="h5-card__meta">在办人：{{ t.assigneeName }}</div>
          <!-- 不可办要说明原因，不能让人点了才知道（服务端 canOperate 同一规则硬拦） -->
          <div v-if="t.lockedByOther" class="h5-flag h5-flag--warn h5-flag--block">同池他人办理中，暂时点不动</div>
          <div v-else-if="t.bizResolved === false" class="h5-flag h5-flag--info h5-flag--block">关联单据未取到，请在电脑端核对</div>
          <div v-else-if="!isExpBillCarrier(t.billType)" class="h5-flag h5-flag--info h5-flag--block">非费用单据（{{ t.billType || '其他域' }}）：移动端暂不支持办理，请在电脑端处理</div>
        </div>
        <div v-if="todosLoading" class="h5-loadmore is-loading"><i class="h5-spin" />加载中…</div>
        <div v-else-if="todoHasMore" class="h5-loadmore" @click="loadMoreTodos">加载更多 ↓</div>
        <div v-else-if="todos.length" class="h5-tip">— 已全部加载，共 {{ todoTotal }} 条 —</div>
        <!-- 空态给动作（§5 三态行），不是「暂无待办」四个字了事 -->
        <div v-if="!todos.length" class="h5-state">
          <div class="h5-state__title">没有待办事项</div>
          <div class="h5-state__hint">审批到达时会出现在这里</div>
          <button class="h5-state__act" @click="switchTab('list')">看我的单据</button>
        </div>
      </template>
    </section>

    <!-- 单据（我的单据） -->
    <section v-if="tab === 'list'" class="h5-body">
      <div class="h5-field">
        <label for="h5-bill-kw">搜索</label>
        <input id="h5-bill-kw" v-model="billKeyword" class="h5-input" type="search"
               placeholder="单号 / 标题关键字" @input="onKeywordInput" />
      </div>
      <!-- 状态 chip：与 PC 枚举标签同一规则，禁中英直接显示（既有枚举治理约定） -->
      <div class="h5-chips">
        <span class="h5-chips__cap">状态</span>
        <button v-for="c in BILL_STATUS_CHIPS" :key="c.value" class="h5-chip"
                :class="{ on: billStatus === c.value }" @click="switchStatus(c.value)">{{ c.label }}</button>
      </div>
      <!-- 剩余半边：类型筛选也走服务端参数，不做前端本地过滤（本地过滤只会筛掉还没加载的页＝假筛选）。 -->
      <div class="h5-chips">
        <span class="h5-chips__cap">类型</span>
        <button v-for="c in BILL_TYPE_CHIPS" :key="c.value" class="h5-chip"
                :class="{ on: billType === c.value }" @click="switchType(c.value)">{{ c.label }}</button>
      </div>
      <!-- 申请日期区间（myPage 的 startDate/endDate 闭区间，两端各自可缺）。
           本行只在服务端确认「日期参数真的进了 SQL」后才出现（判据见 billDateReady 注释）：
           后端未发版时不显示，而不是显示一个"选了没反应"的假筛选。 -->
      <div v-if="billDateReady" class="h5-field">
        <label for="h5-bill-from">日期</label>
        <input id="h5-bill-from" v-model="billFrom" class="h5-input" type="date"
               :max="billTo || undefined" @change="onDateChange" />
        <span class="h5-chips__cap">至</span>
        <input id="h5-bill-to" v-model="billTo" class="h5-input" type="date"
               :min="billFrom || undefined" @change="onDateChange" />
      </div>

      <template v-if="billsLoading && !bills.length">
        <div v-for="i in 3" :key="i" class="h5-skeleton">
          <div class="h5-skeleton__line h5-skeleton__line--m" />
          <div class="h5-skeleton__line h5-skeleton__line--s" />
        </div>
      </template>
      <div v-else-if="billError" class="h5-error">
        <div class="h5-error__d">{{ billError }}</div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="reloadBills">重试</button>
      </div>
      <template v-else>
        <div v-for="b in bills" :key="b.id" class="h5-card h5-card--tap" @click="openDetail(b)">
          <div class="h5-card__row">
            <!-- 单号是文本；后 .no 只剩这一处语义，按钮类名改 .h5-btn-danger -->
            <span class="no">{{ b.billNo }}</span>
            <el-tag size="small" :type="statusType(b.status)">{{ statusLabel(b.status) }}</el-tag>
          </div>
          <div class="h5-card__title">{{ b.title }}</div>
          <div class="h5-card__meta">￥{{ money(b.totalAmount) }} · {{ docTypeLabel(b.billType) }}{{ b.billSubType === 'TRAVEL' ? '（差旅）' : '' }} · {{ dayText(b.applyDate) }}</div>
          <div v-if="b.currentNode" class="h5-card__meta">当前节点：{{ b.currentNode }}</div>
          <div v-if="b.voucherNo" class="h5-card__meta">凭证：{{ b.voucherNo }}</div>
        </div>
        <div v-if="billsLoading" class="h5-loadmore is-loading"><i class="h5-spin" />加载中…</div>
        <div v-else-if="billHasMore" class="h5-loadmore" @click="loadMoreBills">加载更多 ↓</div>
        <div v-else-if="bills.length" class="h5-tip">— 已全部加载，共 {{ billTotal }} 条 —</div>
        <div v-if="!bills.length" class="h5-state">
          <div class="h5-state__title">{{ billFilterOn ? '没有符合条件的单据' : '还没有提过单据' }}</div>
          <div class="h5-state__hint">{{ billFilterOn ? '换个关键字、状态、类型或日期看看' : '手机就能报一笔，明细发票都能带上' }}</div>
          <button v-if="billFilterOn" class="h5-state__act" @click="clearFilters">清空筛选</button>
          <button v-else class="h5-state__act" @click="switchTab('create')">去提报</button>
        </div>
      </template>
    </section>

    <!-- 提报 -->
    <section v-if="tab === 'create'" class="h5-body" :data-dt-src="docTypesSource">
      <!-- 可选区与明示区都由「移动端单据启用清单」渲染——服务端 exp_mobile_template 读到就用它，
           读不到（接口未上线/无权限/空清单）回落 utils/h5.js 的常量，两面的取值域与顺序规则一致。 -->
      <div v-if="mobileTypes.length" class="h5-card">
        <div class="h5-card__title h5-flush">发起报账</div>
        <div class="h5-tip h5-tip--left h5-tip--tight">支持多行费用明细、发票关联、附件与影像上传；填到一半退出也会自动留在本机草稿里。</div>
        <button v-for="d in mobileTypes" :key="d.key" class="h5-btn" @click="goCreate(d)">{{ d.label }}</button>
      </div>
      <div v-else class="h5-denied">
        <div class="h5-denied__t">当前没有可在手机上提报的单据类型</div>
        <div class="h5-denied__d">可提报的单据类型由管理员在电脑端维护，调整后这里会自动更新；下方类型请继续在电脑端提报。</div>
      </div>
      <!-- 未启用类型明示列出并给去向，而不是从入口里静默消失（§4.2-② 移动启用清单） -->
      <div v-if="pcOnlyTypes.length" class="h5-card">
        <div class="h5-section-title h5-flush">以下类型请在电脑端提报</div>
        <div v-for="d in pcOnlyTypes" :key="d.key" class="h5-row-sub">
          <span>{{ d.label }}</span>
          <span class="h5-flag h5-flag--info">{{ docTypeDenyReason(d) }} · {{ d.pcPath }}</span>
        </div>
        <div class="h5-tip h5-tip--left">移动端只做「能交出一张不缺件的单」的类型；差旅/申请/借款在电脑端有完整明细段，手机上强提只会交出一张被打回的单。</div>
      </div>
      <div v-if="draftTip" class="h5-denied">
        <div class="h5-denied__t">本机有未提交的草稿</div>
        <div class="h5-denied__d">{{ draftTip }}</div>
        <button class="h5-btn h5-btn--ghost h5-mt-8" @click="router.push('/h5/expense/new')">继续填写</button>
      </div>
    </section>

    <!-- 发票 -->
    <section v-if="tab === 'invoice'" class="h5-body">
      <div class="h5-card">
        <div class="h5-card__title h5-flush">发票扫描 / 上传</div>
        <!-- 模板里不能写 @click="document.getElementById(...)"：Vue 3 模板全局白名单里没有
             document，运行时解析成 _ctx.document＝undefined ⇒ TypeError，按钮点了没反应。
             要拿 DOM 引用就走模板 ref。 -->
        <input ref="invInputRef" type="file" accept="image/*,.pdf,.ofd,.xml" multiple capture="environment"
               hidden @change="onScan" />
        <button class="h5-btn" :disabled="scanning" :aria-busy="scanning ? 'true' : 'false'" @click="invInputRef && invInputRef.click()">
          <i v-if="scanning" class="h5-spin" />{{ scanning ? '上传识别中…' : '拍摄 / 从相册选择发票' }}
        </button>
        <!-- 扫描版 PDF 转图像识别要等 30~60 秒，没有出口时用户只能杀页面 ⇒
             服务端照写不误（识别链是独立事务），回来就看到一张"来历不明"的票。
             中断只断请求，行内如实标「已中断·结果未知」。 -->
        <button v-if="scanning" class="h5-btn h5-btn--ghost" @click="cancelScanWait">取消等待</button>
        <div class="h5-tip h5-tip--left">
          电子发票（OFD/PDF/XML）先走本地版式解析，秒级出结果，只解析票面、不会生成票夹记录；
          扫描版 PDF（没有文本层）才会自动转图像识别，约需 30~60 秒。
          图片票识别成功且票面要素齐全才会自动进票夹；要素不全或没识别出来时文件仍已保存，
          可改传 OFD/XML 版式文件重试，或在电脑端「发票管理 › 发票补录」补录票面。
        </div>
        <div v-for="u in uploadStates" :key="u.name" class="h5-row-sub h5-row-sub--block">
          <div class="h5-card__row">
            <span>{{ u.name }}</span>
            <span class="h5-flag" :class="u.cls">{{ u.text }}</span>
          </div>
          <div v-if="u.status === 'up'" class="h5-pf"><i :style="{ width: u.pct + '%' }" /></div>
          <div v-if="u.msg" class="h5-card__meta">{{ u.msg }}</div>
        </div>
      </div>

      <!-- 队列计数用真权限判定：无 inv:verify:list 时不再显示 0，而是明说「验真由发票管理岗处理」；
           有权限才给数字与执行按钮（同码办结）。
           10-04 复核订正：核查册原判该键为「幽灵键」不成立——它在 InvoiceEnhanceController.java:67
           有真消费者（GET /verify/page），且 5 个角色持有（57/60/61/68/99032）。
           提报岗看不到计数是正常权限面，不是坏键，所以这里只做文案降级、不动授权。 -->
      <div v-if="verifyDenied" class="h5-denied">
        <div class="h5-denied__t">验真队列：当前账号无查看权限</div>
        <div class="h5-denied__d">验真由发票管理岗触发；识别结果与发票台账在电脑端「发票管理 › 发票验真记录-查询」里查看。急件请联系发票岗。</div>
      </div>
      <div v-else class="h5-card">
        <div class="h5-card__row">
          <span>验真队列：{{ verifyPending }} 张待验</span>
          <button v-if="canRunVerify" class="h5-btn-ok h5-btn-ok--inline" @click="runVerify">立即验真</button>
        </div>
        <div v-if="!canRunVerify" class="h5-tip h5-tip--left">当前账号无发票验真执行权限，验真由发票管理岗触发。</div>
      </div>

      <div class="h5-section-title">我的票夹</div>
      <template v-if="invDenied">
        <div class="h5-denied">
          <div class="h5-denied__t">票夹：当前账号无查看权限</div>
          <div class="h5-denied__d">这不是「你没有发票」——上面拍的票据影像仍会作为附件随单提交、审批侧照常可看；发票台账由发票岗在电脑端「发票管理 › 发票查询」维护。</div>
        </div>
      </template>
      <div v-else-if="invError" class="h5-error">
        <div class="h5-error__d">{{ invError }}</div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadInvoices">重试</button>
      </div>
      <template v-else>
        <div v-for="inv in invoices" :key="inv.id" class="h5-card">
          <div class="h5-card__row">
            <span class="no">{{ inv.invoiceNo }}</span>
            <el-tag size="small" :type="inv.status === 'SUCCESS' ? 'success' : 'info'">{{ statusLabel(inv.status) }}</el-tag>
          </div>
          <div class="h5-card__meta">{{ inv.sellerName || '—' }} · ￥{{ money(inv.amount) }}</div>
          <div class="h5-card__meta">{{ dayText(inv.invoiceDate) }}</div>
        </div>
        <div v-if="!invoices.length" class="h5-state">
          <div class="h5-state__title">票夹是空的</div>
          <div class="h5-state__hint">上面拍完的票会出现在这里，提单时可直接选</div>
        </div>
      </template>
    </section>

    <!-- 我的 -->
    <section v-if="tab === 'mine'" class="h5-body">
      <!-- 没有「我是谁 / 我在哪个公司」⇒ 提单被跨法人拦下时用户无从判断 -->
      <div class="h5-card">
        <div class="h5-idcard">
          <span class="h5-idcard__ava">{{ (myName || '我').slice(0, 1) }}</span>
          <span class="h5-flex-1">
            <span class="h5-idcard__name">{{ myName }}</span>
            <span class="h5-card__meta h5-card__meta--block">{{ myAccount }} · 公司 {{ myCompany }} · 部门 {{ myDept }}</span>
          </span>
        </div>
      </div>

      <div class="h5-card">
        <div class="h5-card__title h5-flush">借款余额</div>
        <div v-if="mineLoading" class="h5-tip">计算中…</div>
        <template v-else-if="mineError">
          <div class="h5-error__d">{{ mineError }}</div>
          <button class="h5-btn h5-btn--sub h5-mt-8" @click="loadMine">重试</button>
        </template>
        <template v-else>
          <div class="h5-big-num">￥{{ money(loanBalance) }}</div>
          <!-- 原规则＝只加总前 N 条还宣称是全额，数字不可信。改成库里 SUM 聚合后
               与分页能取回多少条无关；聚合口未上线时回落分页累加，并在同一行如实标出是哪条环节。 -->
          <div class="h5-card__meta">
            未冲销完毕的借款余额（{{ loanCount }} 笔）
            <template v-if="mineStatMode === 'server'"> · 全量统计（服务端汇总）</template>
            <template v-else> · 按最近 {{ loanCount }} 笔合计（服务端聚合口未上线）</template>
          </div>
        </template>
      </div>

      <!-- 在途张数。规则＝status ∈ {SUBMITTED, APPROVING}，与后端 ExpenseMineStatService
           的名单逐字相同；数字来自 SQL 聚合/服务端 total，不是"翻到的前几条里数数"。 -->
      <div class="h5-card">
        <div class="h5-amount-row">
          <span class="h5-card__title h5-flush">在途单据</span>
          <span class="h5-big-num">{{ mineLoading ? '…' : inTransitCount }}</span>
        </div>
        <div class="h5-card__meta">已提交、流程还在办的张数</div>
      </div>

      <!-- 总数取服务端 total（库里 COUNT），不再是"翻到的前几条里数数"；0 张时不挂角标 -->
      <div class="h5-section-title">打回的单据<span v-if="rejectedTotal" class="h5-flag h5-flag--danger">{{ rejectedTotal }} 张</span></div>
      <template v-if="rejectedDenied">
        <div class="h5-denied">
          <div class="h5-denied__t">打回列表加载受限</div>
          <div class="h5-denied__d">{{ rejectedDenied.msg }}</div>
        </div>
      </template>
      <div v-for="r in rejected" :key="r.id" class="h5-card">
        <div class="h5-card__row">
          <span class="no">{{ r.billNo }}</span>
          <el-tag size="small" type="danger">已打回</el-tag>
        </div>
        <div class="h5-card__title">{{ r.title }}</div>
        <div class="h5-card__meta">￥{{ money(r.totalAmount) }} · 审批意见：{{ r.auditRemark || '—' }}</div>
        <!-- 打回重交留在移动端。费用报销走 H5 载入原单；
             其余类型（差旅/申请/借款）H5 无完整明细段 ⇒ 明示去电脑端，不再把人扔到 PC 路由了事。 -->
        <button v-if="r.billType === 'CLAIM' && r.billSubType !== 'TRAVEL'" class="h5-btn h5-mt-8"
                @click="goResubmit(r)">修改并重新提交</button>
        <button v-else class="h5-btn h5-btn--sub h5-mt-8" @click="switchTab('list')">
          该类型需电脑端修改（{{ docTypeLabel(r.billType) }}）
        </button>
      </div>
      <div v-if="!rejected.length && !rejectedDenied && !mineLoading" class="h5-state">
        <div class="h5-state__title">没有被打回的单据</div>
      </div>

      <!-- 退出移出主导航（破坏性动作不入主导航）＋二次确认 -->
      <button class="h5-btn h5-btn--sub h5-mt-16" @click="logout">退出登录</button>
    </section>

    <!-- 吸底 Tab Bar（原导航在顶部且含退出） -->
    <nav class="h5-tabbar">
      <button class="h5-tab" :class="{ on: tab === 'todo' }" @click="switchTab('todo')">
        <span class="h5-tab__icon" aria-hidden="true">✓</span><span>待办</span>
        <span v-if="todoTotal" class="h5-tab__badge">{{ todoTotal > 99 ? '99+' : todoTotal }}</span>
        <span class="h5-sr">{{ todoTotal ? '待办 ' + todoTotal + ' 条' : '' }}</span>
      </button>
      <button class="h5-tab" :class="{ on: tab === 'create' }" @click="switchTab('create')">
        <span class="h5-tab__icon" aria-hidden="true">＋</span><span>报销</span>
      </button>
      <button class="h5-tab" :class="{ on: tab === 'list' }" @click="switchTab('list')">
        <span class="h5-tab__icon" aria-hidden="true">≡</span><span>单据</span>
      </button>
      <button class="h5-tab" :class="{ on: tab === 'invoice' }" @click="switchTab('invoice')">
        <span class="h5-tab__icon" aria-hidden="true">▣</span><span>发票</span>
      </button>
      <button class="h5-tab" :class="{ on: tab === 'mine' }" @click="switchTab('mine')">
        <span class="h5-tab__icon" aria-hidden="true">☺</span><span>我的</span>
      </button>
    </nav>

    <!-- 下拉刷新指示：仅在文档已滚到顶、向下拉时出现 -->
    <div v-if="pullHint" class="h5-pull">{{ pulling ? '松开刷新' : '下拉刷新…' }}</div>

    <!-- 详情弹层（进出场走 <Transition name="h5-pop">，面板自底部升起＝移动端弹层的方向语义词） -->
    <Transition name="h5-pop">
      <div v-if="detail" class="h5-mask" @click="detail = null">
        <div class="h5-sheet" @click.stop>
          <div class="h5-sheet__grip" />
          <div class="h5-sheet__title">{{ detail.billNo }}（{{ statusLabel(detail.status) }}）</div>
          <div class="h5-sheet__row"><span>标题</span><span>{{ detail.title }}</span></div>
          <div class="h5-sheet__row"><span>金额</span><span>￥{{ money(detail.totalAmount) }}</span></div>
          <div class="h5-sheet__row"><span>日期</span><span>{{ dayText(detail.applyDate) }}</span></div>
          <div class="h5-sheet__row"><span>明细</span><span>{{ linesText }}</span></div>
          <div class="h5-sheet__row"><span>附件</span><span>{{ attachText }}</span></div>
          <div class="h5-sheet__row"><span>审批意见</span><span>{{ detail.auditRemark || '—' }}</span></div>
          <button class="h5-btn h5-mt-12" @click="detail = null">关闭</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, wfApi, mdApi, fileApi, invoiceApi, store, isRequestCancelled } from '../../api'
import { hasPerm } from '../../utils/perm'
import { invoiceOutcome, abortedOutcome, INVOICE_TIERS } from '@/utils/invoiceOutcome'
import { BillStatus, statusLabel, statusType, editBillPath } from '@/constants/billStatus.js'
import { docTypeLabel, BILL_TYPE } from '@/constants/enums.js'
import { H5_DOC_TYPES, normalizeMobileTemplates, docTypeDenyReason, isExpBillCarrier, money, dayText, stayOf, permDeniedOf, loadLocalDraft } from '@/utils/h5.js'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

defineOptions({ name: 'H5Workbench' })

const router = useRouter()
const PAGE_SIZE = 20

const tab = ref('list')
const touched = ref(false)

const bills = ref([])
const billPage = ref(1)
const billTotal = ref(0)
const billHasMore = ref(false)
const billsLoading = ref(false)
const billError = ref('')
const billStatus = ref('')
const billType = ref('')
const billKeyword = ref('')
// 申请日期区间（闭区间，两端各自可缺）＋能力校验，见 loadBills/probeDateSupport 注释
const billFrom = ref('')
const billTo = ref('')
const billDateReady = ref(false)

const todos = ref([])
const todoPage = ref(1)
const todoTotal = ref(0)
const todoHasMore = ref(false)
const todosLoading = ref(false)
const todoError = ref('')

const items = ref([])
const invoices = ref([])
const invDenied = ref(null)
const invError = ref('')
const verifyPending = ref(0)
const verifyDenied = ref(null)
const loanBalance = ref(0)
const loanCount = ref(0)
// 「我的」页两个计数：在途单数／打回总数。总数一律取服务端 total（真 COUNT），
// 不在"已加载的前 N 条"里数数——那是 U 记下的假数根因。
const inTransitCount = ref(0)
const rejectedTotal = ref(0)
// 数值出处可见性：'server'＝/mine/stat 库里聚合；'local'＝聚合口未上线时的分页累加回落
// 借款余额那一行文案会如实标出走了哪条环节
const mineStatMode = ref('')
const rejected = ref([])
const rejectedDenied = ref(null)
const mineLoading = ref(false)
const mineError = ref('')
const scanning = ref(false)
const uploadStates = ref([])
/* 票夹扫描环节一次可并发 4 张，中断要把手上全部请求一起断开。
   后端识别链是 REQUIRES_NEW 独立事务 ⇒ 断开只是"不再等响应"，不等于服务端没写进去，
   所以断开的行按 abortedOutcome 报「结果未知」，并提示刷新票夹核对。 */
let scanControllers = []
const scanAborted = ref(false)
function cancelScanWait() {
  scanAborted.value = true
  scanControllers.forEach((c) => { try { c.abort() } catch (_) { /* 已结束的连接忽略 */ } })
}
const detail = ref(null)
const linesText = ref('')
const attachText = ref('')
const invInputRef = ref(null)
const draftTip = ref('')

/* 提报清单（库里可配，常量兜底）
   docTypes 初值＝常量，所以「接口没上线/读失败」时提报 tab 首屏就有内容，不出现空壳；
   docTypesSource 只做运行期可证性用（写在 section 的 data-dt-src 上，界面不显示），
   值面验证时要区分「服务端清单」和「常量兜底」两个环节，见 §7-V 记录。 */
const docTypes = ref(H5_DOC_TYPES)
const docTypesSource = ref('local')
const mobileTypes = computed(() => docTypes.value.filter((d) => d.mobile))
const pcOnlyTypes = computed(() => docTypes.value.filter((d) => !d.mobile))

async function loadDocTypes() {
  try {
    const rows = await billApi.mobileTemplates()
    const list = normalizeMobileTemplates(rows)
    // 空清单（表没建、全被软删、型域对不上）不改判：宁可用常量渲染，也不把提报页交成"一个类型都没有"
    if (list.length) { docTypes.value = list; docTypesSource.value = 'server' }
  } catch { /* 404（后端未发版）/4030/网络错一律静默回落常量；api.js 已 silent，不打扰用户 */ }
}

function goCreate(d) {
  // 可选区只渲染 capable（H5 有表单）的行，所以 h5Path 必非空；真出现空路径说明清单与表单能力分叉了，
  // 此时宁可不跳转并说明原因，也不把人丢在一个空白页上。
  if (d?.h5Path) router.push(d.h5Path)
  else ElMessage.info('该类型还没有移动端表单，请到电脑端提报')
}

// 与 InvoiceEnhanceController 的 @RequiresPerm("inv:verify:run") 同码办结入口可见性
// （超管持 *:*:* 由 utils/perm 统一放行，不会被误藏）
const canRunVerify = hasPerm('inv:verify:run')

const BILL_STATUS_CHIPS = [
  { value: '', label: t('common.all') },
  { value: BillStatus.DRAFT, label: statusLabel(BillStatus.DRAFT) },
  { value: BillStatus.APPROVING, label: statusLabel(BillStatus.APPROVING) },
  { value: BillStatus.REJECTED, label: statusLabel(BillStatus.REJECTED) },
  { value: BillStatus.APPROVED, label: statusLabel(BillStatus.APPROVED) },
]
// 类型筛选的取值域来自 exp_bill 现有数据（del_flag=0）的五种单据类型；
// 标签一律走 BILL_TYPE 名称表，禁页面内手写 map（枚举治理约定）。
// 与「提报入口只开 CLAIM」（A6 规则）不矛盾：全部类型都能查看与筛选，非费用载体在详情页明示去电脑端。
const BILL_TYPE_CHIPS = [
  { value: '', label: '不限' },
  { value: 'CLAIM', label: BILL_TYPE.CLAIM },
  { value: 'LOAN', label: BILL_TYPE.LOAN },
  { value: 'REPAY', label: BILL_TYPE.REPAY },
  { value: 'APPLY', label: BILL_TYPE.APPLY },
  { value: 'PAY', label: BILL_TYPE.PAY },
]

const myName = computed(() => store.user?.realName || store.user?.nickName || store.user?.username || '我')
const myAccount = computed(() => store.user?.userName || store.user?.username || '—')
// U＋跨法人：companyId 取登录态（同一规则），并把「我在哪个公司」摊开显示，
// 被 OrgGuard 拦下时用户至少知道是不是自己公司的单
const myCompany = computed(() => store.user?.companyId ?? '—')
const myDept = computed(() => store.user?.deptId ?? '—')

/* 单据列表：真分页（服务端 myPage 支持 status/billType/keyword/日期区间，本窗已核签名） */
/**
 * 日期区间能力检查（一次性）：myPage 认不认 startDate/endDate 由服务端自己回答，不看版本号。
 * 把两端都指向 1970-01-01（本库申请日期全在 2026 年）⇒ 真支持必回 total=0；
 * 老签名会忽略这两个参数、原样返回全量 total。
 * 探不出支持就不渲染日期行——宁可少一个入口，也不摆一个"选了没反应"的假筛选。
 * 探活失败（无权限/网络）同样按不支持处理，不影响列表本身加载。
 */
async function probeDateSupport() {
  if (billDateReady.value) return
  try {
    const r = await billApi.myPage({ pageNum: 1, pageSize: 1, startDate: '1970-01-01', endDate: '1970-01-01' })
    billDateReady.value = (Number(r.total) || 0) === 0
  } catch { billDateReady.value = false }
}

async function loadBills(reset = false) {
  if (billsLoading.value && !reset) return
  billsLoading.value = true
  if (reset) { billPage.value = 1; billError.value = '' }
  try {
    const r = await billApi.myPage({
      pageNum: billPage.value,
      pageSize: PAGE_SIZE,
      status: billStatus.value || undefined,
      billType: billType.value || undefined,
      keyword: billKeyword.value.trim() || undefined,
      // 只在服务端确认支持后才带上（billDateReady 为假时这两个参数根本不存在于界面上）
      startDate: billDateReady.value ? (billFrom.value || undefined) : undefined,
      endDate: billDateReady.value ? (billTo.value || undefined) : undefined,
    })
    const list = r.list || r.records || []
    bills.value = billPage.value === 1 ? dedupe(list, 'id') : dedupe([...bills.value, ...list], 'id')
    billTotal.value = Number(r.total) || 0
    // 同类修法：分页判据用服务端 pages/total，不用 list.length >= PAGE_SIZE
    // （原先待办用后者，接口不分页时恒真 ⇒ 整份列表反复追加）
    const pages = Number(r.pages) || Math.ceil(billTotal.value / PAGE_SIZE) || 1
    billHasMore.value = billPage.value < pages
  } catch (e) {
    const d = permDeniedOf(e)
    if (d) { billError.value = ''; bills.value = [] }
    billError.value = d ? `无权限：${d.msg}` : (e.message || '加载失败')
  } finally { billsLoading.value = false }
}
function reloadBills() {
  // 每次进「单据」tab 都探一次（探到即短路返回）：后端发版这个事件是运行期发生的，
  // 前端不该靠版本号猜，探到支持日期就自己把日期行长出来。
  probeDateSupport()
  loadBills(true)
}
function loadMoreBills() {
  if (!billHasMore.value || billsLoading.value) return
  billPage.value++
  loadBills()
}
let kwTimer = null
function onKeywordInput() {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => loadBills(true), 300)
}
function switchStatus(v) {
  billStatus.value = v
  loadBills(true)
}
function switchType(v) {
  billType.value = v
  loadBills(true)
}
// 日期两端的本地格式 yyyy-MM-dd 与 apply_date（date 列）同形，String 直传、服务端 ge/le 拼进条件
function onDateChange() { loadBills(true) }
// 四条筛选都走服务端参数（ExpenseBillService.java:1802-1838 逐条 eq/like/ge/le 拼接），
// 所以「有没有在筛」要四条一起看，否则选了类型又清空状态时，空态文案会退回「还没有提过单据」。
const billFilterOn = computed(() => !!(billKeyword.value.trim() || billStatus.value || billType.value
  || billFrom.value || billTo.value))
function clearFilters() {
  billKeyword.value = ''
  billStatus.value = ''
  billType.value = ''
  billFrom.value = ''
  billTo.value = ''
  loadBills(true)
}

/* 待办：换服务端分页口 */
// 原先打的是 /wf/todo —— 服务端签名只有 limit（WfTaskController:71-77），
// 传 pageNum/pageSize 被静默丢弃 ⇒ hasMore 恒真、整份列表反复追加。
// 现改 /wf/todo/page（PC 已用），返回 {list,total,pages,pageNum,pageSize}。
async function loadTodos(reset = false) {
  if (todosLoading.value && !reset) return
  todosLoading.value = true
  if (reset) { todoPage.value = 1; todoError.value = '' }
  try {
    const r = await wfApi.todoPage({ pageNum: todoPage.value, pageSize: PAGE_SIZE })
    const list = r.list || []
    if (reset) todos.value = []
    todos.value = dedupe([...todos.value, ...list], 'taskId')
    todoTotal.value = Number(r.total) || 0
    const pages = Number(r.pages) || Math.ceil(todoTotal.value / PAGE_SIZE) || 1
    todoHasMore.value = todoPage.value < pages
    todoError.value = ''
  } catch (e) {
    const d = permDeniedOf(e)
    todoError.value = d ? `无权限：${d.msg}` : (e.message || '待办加载失败')
  } finally { todosLoading.value = false }
}
function reloadTodos() { loadTodos(true) }
function loadMoreTodos() {
  if (!todoHasMore.value || todosLoading.value) return
  todoPage.value++
  loadTodos()
}
/** 按 key 去重：分页规则下同一 taskId 二次出现即视为异常，宁可不重复也不能重复办同一道 */
function dedupe(arr, key) {
  const seen = new Set()
  return arr.filter((x) => {
    const k = x?.[key]
    if (k == null || seen.has(String(k))) return false
    seen.add(String(k))
    return true
  })
}

/** 待办徽标：只取 total，不拉整份列表 */
async function loadTodoBadge() {
  try {
    const r = await wfApi.todoPage({ pageNum: 1, pageSize: 1 })
    todoTotal.value = Number(r?.total) || 0
    // U：首屏给谁看 —— 有待办先落待办，没有再落单据
    if (todoTotal.value > 0 && !touched.value && tab.value === 'list' && !bills.value.length) {
      tab.value = 'todo'
      loadTodos(true)
    }
  } catch { /* 徽标取不到不影响页面，静默 */ }
}

async function loadItems() {
  try {
    const t = await mdApi.expenseItems()
    const flat = []
    const walk = (arr) => (arr || []).forEach((n) => { if (n) { flat.push(n); if (n.children?.length) walk(n.children) } })
    walk(t)
    items.value = flat
  } catch { /* 主数据取不到＝下拉为空，提交时另有提示，不静默改判 */ }
}

/* 发票 */
/**
 * 票夹与验真队列都属发票管理岗职能（inv:list／inv:verify:list 只授发票、税务、共享三岗，
 * 其余 H5 岗位多数不持）。这两个面板本就按缺权降级，但每次开页照样发请求
 * ⇒ 全局拦截器（api.js:200）给每个提报岗在首屏弹一次「您没有「发票·list」权限，请联系管理员开通」，
 * 既修不好问题（这权限本就不该给），又把真正的提示盖掉。
 * 正解＝先判权再决定发不发：无权直接落降级面板，一声不响；有权才取数（并 silent，
 * 防服务端因依赖闭包等其它口径再拒一次而弹 toast）。与本页 canRunVerify＝hasPerm('inv:verify:run') 同一手法。
 */
async function loadInvoices() {
  invError.value = ''
  invDenied.value = null
  if (!hasPerm('inv:list')) {
    invDenied.value = { msg: '', permKey: 'inv:list' }
    invoices.value = []
  } else {
    try {
      const r = await invoiceApi.page({ pageNum: 1, pageSize: 50 }, { silent: true })
      invoices.value = r.list || []
    } catch (e) {
      const d = permDeniedOf(e)
      if (d) invDenied.value = d
      else { invError.value = e.message || '票夹加载失败'; invoices.value = [] }
    }
  }
  verifyDenied.value = null
  if (!hasPerm('inv:verify:list')) {
    verifyDenied.value = { msg: '', permKey: 'inv:verify:list' }
    verifyPending.value = 0
  } else {
    try {
      const v = await invoiceApi.verifyPage({ pageNum: 1, pageSize: 1 })
      verifyPending.value = Number(v?.total) || 0
    } catch (e) {
      const d = permDeniedOf(e)
      if (d) verifyDenied.value = d
      else verifyPending.value = 0
    }
  }
}

/**
 * 发票扫描走识别通道。
 * 通用 /system/file/upload 不带识别，后端带 OCR 的 /expense/attach/upload 才是这条链
 * （ExpenseAttachController:37）；打错端点时「扫描」只是个假功能。
 * 识别通道取自 sys_config 的 ocr.channel（现走发票能力网关），本页不做假设；
 * 服务端识别异常时降级为 invoice.ok=false 且不阻断上传（ExpenseAttachController:91-97），
 * 下面的播报规则按此分档。
 * 识别成功 ≠ 已进票夹（票面要素不全／金额不自洽时后端只 log 不入池），
 * 所以档位一律取 invoiceOutcome 这个唯一分档口，不要在这里重算判据。
 */
const SCAN_TIER_CHIP = {
  [INVOICE_TIERS.POOLED]: { text: '已入票夹', cls: 'h5-flag--ok' },
  [INVOICE_TIERS.NOT_POOLED]: { text: '已识别·未入票夹', cls: 'h5-flag--warn' },
  [INVOICE_TIERS.PARSED_ONLY]: { text: '已解析票面', cls: 'h5-flag--info' },
  [INVOICE_TIERS.POOL_UNDETERMINED]: { text: '已识别·入池待核对', cls: 'h5-flag--info' },
  [INVOICE_TIERS.FAILED]: { text: '已上传·未识别', cls: 'h5-flag--warn' },
}

async function onScan(e) {
  const files = Array.from(e.target.files || [])
  if (!files.length) return
  scanning.value = true
  scanAborted.value = false
  scanControllers = []
  uploadStates.value = files.map((f) => ({ name: f.name, status: 'wait', pct: 0, cls: 'h5-flag--info', text: '待上传', msg: '' }))
  // 并发 ≤4（§5 上传行），逐张如实回报，失败的单独重试入口由「重拍/重选」承担
  const queue = files.map((file, i) => ({ file, i }))
  let cursor = 0
  const workers = Array.from({ length: Math.min(4, queue.length) }, async () => {
    while (cursor < queue.length) {
      const { file, i } = queue[cursor++]
      if (scanAborted.value) {
        // 已按「取消等待」⇒ 还在排队的张次不再发起（这几条是真没上传，与下面"结果未知"分得开）
        uploadStates.value[i].status = 'wait'
        uploadStates.value[i].text = '未上传'
        uploadStates.value[i].cls = 'h5-flag--info'
        uploadStates.value[i].msg = '已取消，这一张没有上传'
        continue
      }
      uploadStates.value[i].status = 'up'
      uploadStates.value[i].text = '上传中'
      uploadStates.value[i].cls = 'h5-flag--info'
      // 压缩到长边 1600／质量 0.8／目标 ≤500KB 在提单页做（那里要带明细行）；
      // 票夹这条环节直接原图上传——压缩会糊票面，识别率优先于流量。
      const controller = new AbortController()
      scanControllers.push(controller)
      try {
        const r = await fileApi.attachUpload(file, null, controller.signal)
        const inv = r?.invoice
        const out = invoiceOutcome(inv, { fileName: r?.fileName || file.name, end: 'h5' })
        const chip = SCAN_TIER_CHIP[out.tier] || SCAN_TIER_CHIP[INVOICE_TIERS.FAILED]
        uploadStates.value[i].status = 'done'
        uploadStates.value[i].pct = 100
        uploadStates.value[i].text = chip.text
        uploadStates.value[i].cls = chip.cls
        uploadStates.value[i].msg = (out.tier === INVOICE_TIERS.POOLED && (inv.invoiceNo || inv.invoice?.invoiceNo)
          ? `票号 ${inv.invoiceNo || inv.invoice?.invoiceNo}｜`
          : '') + out.message
      } catch (err) {
        if (isRequestCancelled(err)) {
          // 这条请求已经发出去了，中断＝"结果未知"，不要报「失败」（服务端可能仍在写）
          uploadStates.value[i].status = 'abort'
          uploadStates.value[i].text = '已中断·结果未知'
          uploadStates.value[i].cls = 'h5-flag--warn'
          uploadStates.value[i].msg = abortedOutcome(file.name).message
        } else {
          const d = permDeniedOf(err)
          uploadStates.value[i].status = 'fail'
          uploadStates.value[i].text = '失败'
          uploadStates.value[i].cls = 'h5-flag--danger'
          uploadStates.value[i].msg = d ? `无权限：${d.msg}` : (err.message || '上传失败')
        }
      }
    }
  })
  await Promise.all(workers)
  scanning.value = false
  e.target.value = ''
  await loadInvoices()
}

async function runVerify() {
  if (!canRunVerify) { ElMessage.warning('当前账号无发票验真执行权限，验真请由发票管理岗触发'); return }
  try {
    // 顺带消一处未编号 500：原先调 invoiceApi.verifyRun() 不带 body，而
    // verify/run 是「单张验真结果登记」口，服务端取不到 invoiceId 即 NPE ⇒ 有权限的人点了反而必炸。
    // 本按钮语义是「把待验的票一次受理」，改走与 PC 发票池同一批量受理口 /verify/batch（同码 inv:verify:run）。
    const pending = invoices.value.filter((x) => x.verifyStatus !== 'SUCCESS')
    if (!pending.length) { ElMessage.info(t('h5.main.No')); return }
    const ok = await invoiceApi.verifyBatch(pending.map((x) => x.id))
    ElMessage.success(`批量验真已受理：${ok ?? 0} 张置「验真中」（无外部报文不判定通过）`)
    loadInvoices()
  } catch (e) { ElMessage.error('验真触发失败：' + (e?.message || '服务端未返回具体原因')) }
}

/* 我的数据（数字改由服务端出，前端只做搬运） */
// 回落环节的分页上限
const MINE_PAGE_LIMIT = 200
// 「仍在贷」＝已批且还没冲销完毕。名单与后端 ExpenseMineStatService.LOAN_OPEN_STATUSES 逐字相同
// （PC 侧 LoanApply.vue#LOAN_OPEN_STATUSES 亦同值）。
// 为什么不能只认 APPROVED：借款单一旦挂上冲抵/还款就迁到 PARTIAL_REPAID，
// 只认 APPROVED 会让这类人在「仍在贷」上恒显 ￥0.00。
const MINE_LOAN_STATUSES = ['APPROVED', 'PARTIAL_REPAID', 'REPAID']
// 「在途」＝已提交、流程在办、尚未出结论；SUBMITTED 当前 0 行但属会落状态的历史枚举码，漏算就把在途读小。
const MINE_TRANSIT_STATUSES = ['SUBMITTED', 'APPROVING']

async function loadMine() {
  mineLoading.value = true
  mineError.value = ''
  rejectedDenied.value = null
  try {
    // ① 首选服务端聚合：GET /mine/stat 一条 SUM(CASE WHEN) 出四个数，与"一次能取回多少条"彻底解耦。
    //    接口缺权/未上线（4030／404／5xx）都算不可用，静默回落 ②，页面不因后端没发版而空掉。
    let st = null
    try {
      const r = await billApi.mineStat()
      if (r && r.loanBalance !== undefined && r.loanBalance !== null) st = r
    } catch (e) {
      if (permDeniedOf(e)) throw e // 缺权是权限面，不能拿分页算法把它盖成"能算但不准"
      st = null
    }
    // ② 打回明细卡片两环节都要（要能点进「修改并重新提交」），故放在分岔之前统一拉
    const q = await billApi.myPage({ pageNum: 1, pageSize: MINE_PAGE_LIMIT, status: 'REJECTED' })
    rejected.value = q.list || []
    rejectedTotal.value = Number(q.total) || 0
    if (st) {
      mineStatMode.value = 'server'
      loanBalance.value = Number(st.loanBalance || 0)
      loanCount.value = Number(st.loanCount || 0)
      inTransitCount.value = Number(st.inTransitCount || 0)
      // 聚合口给的打回数是库里 COUNT，比上面那一页的 total 更权威（列表本身可能翻页不全）
      if (st.rejectedCount !== undefined && st.rejectedCount !== null) rejectedTotal.value = Number(st.rejectedCount)
    } else {
      mineStatMode.value = 'local'
      // 借款余额：minBalance>0 是 myPage 既有参数（本窗核过 ExpenseBillController:183-195），
      // 库里 loan_balance>0 天然把 REPAID(0.00) 与 WITHDRAWN(NULL) 挡在外面；状态名单再兜一层。
      const r = await billApi.myPage({ pageNum: 1, pageSize: MINE_PAGE_LIMIT, billType: 'LOAN', minBalance: 0 })
      const list = (r.list || []).filter((x) => MINE_LOAN_STATUSES.includes(x.status))
      loanCount.value = list.length
      loanBalance.value = list.reduce((a, x) => a + Number(x.loanBalance || 0), 0)
      // 在途两态各问一次：只取服务端 total（真 COUNT），pageSize=1 不搬明细
      const counts = await Promise.all(
        MINE_TRANSIT_STATUSES.map((s) => billApi.myPage({ pageNum: 1, pageSize: 1, status: s })))
      inTransitCount.value = counts.reduce((a, x) => a + (Number(x.total) || 0), 0)
    }
  } catch (e) {
    const d = permDeniedOf(e)
    if (d) { rejectedDenied.value = d; mineError.value = `无权限：${d.msg}` }
    else mineError.value = e.message || '加载失败'
  } finally { mineLoading.value = false }
}

// 打回重交留在 #/h5 路由族内（原先 location.hash 直接跳 PC 页 ⇒ U）
function goResubmit(r) {
  router.push(`/h5/expense/${r.id}/edit`)
}

async function openApproval(t) {
  if (!isExpBillCarrier(t.billType)) {
    // 不是 exp_bill 载体的单（AR_RECON/FA_ADD/TR_PLAN…）：待办行的 billId 指向的是各自业务表的主键，
    // 拿它去查 exp_bill 会串到另一张同号的单上审批 ⇒ 明示去电脑端，绝不跳详情。
    ElMessageBox.alert(
      `本待办属「${docTypeLabel(t.billType) || t.billType || '其他业务域'}」流程，移动端当前只支持费用类单据的审批办理，请在电脑端处理。`,
      '移动端暂不支持',
      { confirmButtonText: '知道了' })
    return
  }
  const billId = t.billId || t.businessKey || t.id
  if (!billId) { ElMessage.warning('这条待办没有关联到单据，请在电脑端核对'); return }
  // 被锁/未取到关联单：不再拦在列表外（看不见单据更糟），而是带判据进详情——
  // 详情页据此只给阅读视图、不给审批动作，并把"为什么现在办不了"写在页面上。
  const query = {
    taskId: t.taskId || '',
    billType: t.billType || '',
    lk: t.lockedByOther ? '1' : '',
    bz: t.bizResolved === false ? '0' : '',
    as: t.addSignAllowed === false ? '0' : '',
  }
  router.push({ path: `/h5/approval/${billId}`, query })
}

async function openDetail(b) {
  detail.value = b
  linesText.value = '加载中…'
  attachText.value = '—'
  try {
    const d = await billApi.detail(b.id)
    detail.value = d.bill || b
    linesText.value = (d.lines || []).map((l) => `${l.itemName || l.itemId} ${money(l.amount)}元`).join('；') || '—'
    attachText.value = (d.attaches || []).length ? `${(d.attaches || []).length} 个（影像/附件在待办详情里可看大图）` : '无附件'
  } catch (e) {
    const msg = e?.message || '加载失败'
    linesText.value = msg
    attachText.value = msg
  }
}

/** 草稿提示：进「提报」页时看一眼本机有没有未交的单 */
function refreshDraftTip() {
  const uid = store.user?.userId ?? store.user?.id
  const d = loadLocalDraft(uid, 'CLAIM')
  if (!d) { draftTip.value = ''; return }
  const lines = Array.isArray(d.lines) ? d.lines.filter((l) => l && (l.itemId || Number(l.amount) > 0)).length : 0
  const amt = (Array.isArray(d.lines) ? d.lines : []).reduce((a, l) => a + Number(l.amount || 0), 0)
  draftTip.value = `${d.title || '未命名'} · ${lines} 行 · ￥${money(amt)} · ${dayText(d.savedAt)} 保存`
}

function switchTab(t) {
  touched.value = true
  tab.value = t
  if (t === 'list') reloadBills()
  if (t === 'todo') reloadTodos()
  if (t === 'invoice') loadInvoices()
  if (t === 'mine') loadMine()
  if (t === 'create') { refreshDraftTip(); if (docTypesSource.value === 'local') loadDocTypes() }
}

/* 下拉刷新（只在文档滚到顶、向下拖时介入，不 preventDefault 常规滚动） */
const pullHint = ref(false)
const pulling = ref(false)
let pullStart = 0
function onTouchStart(e) {
  if (detail.value) return
  if ((window.scrollY || document.documentElement.scrollTop || 0) > 4) return
  pullStart = e.touches?.[0]?.clientY || 0
}
function onTouchMove(e) {
  if (!pullStart) return
  const dy = (e.touches?.[0]?.clientY || 0) - pullStart
  if (dy > 24) { pullHint.value = true; pulling.value = dy > 70 }
}
async function onTouchEnd() {
  if (!pullStart) return
  const wasPull = pullHint.value && pulling.value
  pullStart = 0; pullHint.value = false; pulling.value = false
  if (!wasPull) return
  if (tab.value === 'list') await reloadBills()
  else if (tab.value === 'todo') { await Promise.all([reloadTodos(), loadTodoBadge()]) }
  else if (tab.value === 'invoice') await loadInvoices()
  else if (tab.value === 'mine') await loadMine()
  // 提报 tab 没有列表可刷，但清单是可配面——下拉一次＝重读一次配置（配合管理端改完库不用刷新页面）
  else if (tab.value === 'create') { docTypesSource.value = 'local'; await loadDocTypes(); refreshDraftTip() }
  else ElMessage.info('这个页面没有需要刷新的列表')
}

// keep-alive 复用实例时（从提单/审批返回）刷新列表
let firstActivated = true
onActivated(() => {
  if (firstActivated) { firstActivated = false; return }
  // 办完（或被驳回）后回到工作台，待办卡片必须消失，且与底部计数同源。
  // 旧实现是"追加式"刷新（loadTodos()/loadBills() 不带 reset）：
  //  - 待办：已办的卡片留在列表里，而计数取服务端 total ⇒ 同屏"共 0 条"却还挂着卡片；
  //  - 单据：停留在第 2 页以上时把当页再追加一遍。
  // 改为重建取数（reset=true）：卡片与计数取自同一次响应，翻页位置也回到第一页。
  if (tab.value === 'list') loadBills(true)
  else if (tab.value === 'todo') loadTodos(true)
  else if (tab.value === 'create') refreshDraftTip()
  else if (tab.value === 'mine') loadMine()
  loadTodoBadge()
})

async function logout() {
  try {
    await ElMessageBox.confirm('退出后需要重新输入账号密码。本机未提交的草稿会保留，下次登录仍可继续填。', '确认退出登录？', {
      type: 'warning', confirmButtonText: '退出', cancelButtonText: '再待一会儿',
    })
  } catch { return }
  store.logout()
  location.hash = '#/login'
}

onMounted(() => {
  loadBills(true)
  loadTodoBadge()
  loadItems()
  loadDocTypes()
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  window.addEventListener('touchend', onTouchEnd, { passive: true })
})
onBeforeUnmount(() => {
  window.removeEventListener('touchstart', onTouchStart)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onTouchEnd)
  clearTimeout(kwTimer)
  // 离开本页时把手上还没响应的识别请求断开，免得回来面对一批"来历不明"的票。
  // 不要这不等于服务端停手（识别链是 REQUIRES_NEW），行内已按「结果未知」规则播报。
  if (scanning.value && scanControllers.length) {
    scanAborted.value = true
    scanControllers.forEach((c) => { try { c.abort() } catch (_) { /* 已结束的连接忽略 */ } })
  }
})
</script>

<style scoped>
/* 本页不再重复定义卡片/按钮/表单样式（三页各抄一份）；
   只留页面特有的下拉刷新指示器。其余见 src/styles/h5-tokens.css。 */
.h5-pull {
  position: fixed;
  top: calc(8px + var(--h5-safe-t));
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  background: rgba(0, 0, 0, .65);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  padding: 6px 14px;
  border-radius: 999px;
}
</style>
