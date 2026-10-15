<!--
  共享审批弹窗：把散落在 30+ 处的 ElMessageBox.confirm/prompt 单行框收敛成一个带上下文的对话框。
  用法（Promise 式，与 ElMessageBox 手感一致）：
    const res = await approvalRef.value.open({ row, decision: 'approve', taskId: row.taskId })
    if (!res) return                       // 用户取消
    await wfApi.approve({ taskId: row.taskId, approved: res.approved, comment: res.comment,
                          nextAssigneeUserId: res.nextAssigneeUserId })
  设计约束：审批意见必经输入框且必填（通过预填「同意」可改写，驳回必须写原因）——留痕口径与原
  Approval.vue 的合规整改保持一致，不静默补默认值。
  下一环节点名：传了 taskId 才会查 /wf/next-candidates 并渲染「下一环节」区块；节点为 PICK 档时
  必选（候选=1 自动预选），其余情况 res.nextAssigneeUserId 为 null，后端按现状 AUTO 取人。
  提交回显（D-DEC-022）：调用方可改传 opts.submit = async (payload) => {...} 把请求交给弹窗执行 ——
  成功才关窗，被服务端业务异常拒绝时弹窗保持打开、意见与选择保留、原因就地显示并可原地重试。
  不传 submit 的调用点行为与改造前逐字一致（先关窗、由调用方自行发请求）。
  批量明细（D-DEC-198）：批量调用点传 opts.items = [{ billNo, billType, amount, node, ... }]，
  弹窗内逐单列出「单号/类型/金额/节点」并按明细真算金额合计——金额取不到（null）的单不计入合计、
  如实标注「未取到」，绝不再按 0 累加出一个看上去自洽的 ¥0.00；不传 items 的调用点零变化。
-->
<template>
  <el-dialog v-model="st.visible" :title="st.title" :width="st.items.length ? '820px' : '560px'"
             :close-on-click-modal="false" append-to-body destroy-on-close class="approval-dialog"
             @update:model-value="onVisibleChange" @opened="focusComment">
    <div v-if="st.summary.length" class="ad-summary">
      <div v-for="s in st.summary" :key="s.label" class="ad-sum-item" :class="{ 'ad-full': s.full }">
        <span class="ad-sum-k">{{ s.label }}</span>
        <span class="ad-sum-v" :class="{ 'ad-amt': s.amount }">{{ s.value }}</span>
      </div>
    </div>

    <!-- D-DEC-198（12-31:176）：批量弹窗从前只有「条数/合计/示例」四行摘要，审批人对着一句
         「等 6 张」就把 6 单一起办了——看不见每一单是谁、多少钱、在哪个节点，等于闭眼签字。
         这里补逐单明细表（数据取批量前已勾选的行，不另发请求、不新增取数范围），
         金额合计改由明细真算：amount 为 null 的行不计入合计并如实标「未取到」，
         不再 Number(x || 0) 把"没取到"当成"金额为 0"（¥0.00 就是这么来的）。 -->
    <div v-if="st.items.length" class="ad-items">
      <div class="ad-items-head">
        <span class="ad-cc-title">本批明细（{{ st.items.length }} 条）</span>
        <span class="ad-items-sum">
          金额合计 <b class="ad-items-amt">¥ {{ fmtMoney(itemsSum.sum) }}</b>
          <span v-if="itemsSum.missing" class="ad-items-miss">
            （另 {{ itemsSum.missing }} 条金额未取到，未计入合计）
          </span>
          <span v-else-if="!itemsSum.known" class="ad-items-miss">（本批 {{ st.items.length }} 条金额均未取到）</span>
        </span>
      </div>
      <el-table :data="st.items" size="small" max-height="220" class="ad-items-table" row-key="key">
        <el-table-column label="单号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.billNo">{{ row.billNo }}</span>
            <span v-else style="color:#c0c4cc">{{ row.title || ('任务 ' + (row.taskId || '—')) }}</span>
            <el-tag v-if="row.unresolved" size="small" type="warning" effect="plain" style="margin-left:6px">无关联单据</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.billTypeText || '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span v-if="itemAmountOf(row) != null">¥ {{ fmtMoney(itemAmountOf(row)) }}</span>
            <span v-else class="ad-items-none">未取到</span>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.node || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.locked" size="small" type="danger" effect="plain">{{ row.lockOwnerName || '他人' }} 办理中</el-tag>
            <span v-else style="color:#c0c4cc;font-size:12px">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-alert v-if="st.notice" :title="st.notice" type="warning" :closable="false" show-icon style="margin-bottom:12px" />

    <div v-if="st.chain.length" class="ad-chain">
      审批链 {{ chainProgressText }}
      <el-popover placement="bottom-start" :width="520" trigger="hover">
        <template #reference><el-link type="primary" :underline="false">查看全部节点</el-link></template>
        <FlowChain :nodes="st.chain" />
      </el-popover>
    </div>

    <!-- 会签/票签进度（P1）：多实例节点显示"已投 x/n · 赞成 a 反对 r"。
         VOTE 额外给通过线与"本次提交后能否收口"，让投票人知道这一票的分量。 -->
    <div v-if="st.mi && st.mi.multi" class="ad-mi">
      <div class="ad-mi-head">
        <span class="ad-mi-title">{{ miTypeLabel }}进度</span>
        <span class="ad-mi-count">已投 <b>{{ miVoted }}</b>/{{ st.mi.total }}</span>
        <el-tag size="small" type="success">赞成 {{ st.mi.approved }}</el-tag>
        <el-tag size="small" type="danger">反对 {{ st.mi.rejected }}</el-tag>
        <el-tag v-if="st.mi.multiType === 'VOTE'" size="small" type="warning">
          ≥{{ st.mi.voteRatio }}% 通过
        </el-tag>
      </div>
      <el-progress :percentage="miPct" :stroke-width="8" :show-text="false"
                   :status="miPct >= 100 ? 'success' : undefined" />
      <div v-if="miHint" class="ad-mi-hint">{{ miHint }}</div>
      <div v-if="st.mi.votes && st.mi.votes.length" class="ad-mi-votes">
        <!-- 会签同伴的意见正文（D-DEC-098）：只给工号＋立场时，第二人无从判断第一人附加了什么条件 -->
        <el-tooltip v-for="(v, i) in st.mi.votes" :key="i" placement="top" :show-after="150"
                    :disabled="!v.comment">
          <template #content>
            <div class="ad-mi-tip">
              <div class="ad-mi-tip-who">{{ v.userName || v.userId }} · {{ v.action === 'REJECT' ? '反对' : '赞成' }}</div>
              <div v-if="v.operTime" class="ad-mi-tip-time">{{ String(v.operTime).replace('T', ' ').slice(0, 16) }}</div>
              <div v-if="v.comment" class="ad-mi-tip-cmt">意见：{{ v.comment }}</div>
            </div>
          </template>
          <el-tag size="small"
                  :type="v.action === 'REJECT' ? 'danger' : 'success'">
            {{ (v.userName || v.userId) + '·' + (v.action === 'REJECT' ? '反对' : '赞成') }}
          </el-tag>
        </el-tooltip>
      </div>
    </div>

    <el-radio-group v-if="showDecision" v-model="st.decision" class="ad-decision" @change="onDecisionChange">
      <el-radio-button value="approve">通过</el-radio-button>
      <el-radio-button value="reject">驳回</el-radio-button>
    </el-radio-group>

         仅当后端返回非空候选才出现 —— 本组件被 30+ 处复用，表里没有行时行为与改造前完全一致。
         点选后服务端按类别（正向/驳回/终止）覆盖 approved 并写 ActionResult 流程变量，
         故上方的「通过/驳回」单选钮在此仅作默认值，界面的页签会被同步切换以保持一致。 -->
    <div v-if="approvalBlock" class="ad-ai">
      <div class="ad-cc-head">
        <span class="ad-cc-title">审批项</span>
        <span class="ad-cc-tip">本节点预置的审批结果，选中后由服务端按其类别决定通过 / 驳回 / 终止</span>
      </div>
      <div class="ad-ai-items">
        <el-tag v-for="it in approvalBlock" :key="it.itemCode" size="default" class="ad-ai-tag"
                :type="it.category === 'POSITIVE' ? 'success' : (it.category === 'REJECT' ? 'danger' : 'warning')"
                :effect="st.approvalItemCode === it.itemCode ? 'dark' : 'plain'"
                @click="pickApprovalItem(it)">{{ it.itemName || it.itemCode }}</el-tag>
        <el-link v-if="st.approvalItemCode" type="info" :underline="false" class="ad-ai-clear"
                 @click="st.approvalItemCode = null">清除</el-link>
      </div>
      <div v-if="pickedApprovalItem" class="ad-ai-hint">
        已选「{{ pickedApprovalItem.itemName || pickedApprovalItem.itemCode }}」：{{
          pickedApprovalItem.category === 'POSITIVE' ? '本节点判通过，流转下一环节'
            : (pickedApprovalItem.category === 'REJECT'
              ? (pickedApprovalItem.targetNode
                ? '本节点判驳回；退回目标 ' + pickedApprovalItem.targetNode
                  + '（受该节点驳回范围配置约束，未放行时落回终止本流程）'
                : '本节点判驳回：终止本流程，单据作废待重提')
              : '本节点判终止：不走驳回路径，单据作废待重提') }}
        <span v-if="pickedApprovalItem.linkedOper">；表单操作：{{ pickedApprovalItem.linkedOper }}</span>
      </div>
    </div>

    <!-- P1 取人方式 PICK：通过即决定下一环节由谁办，所以放在意见之前、驳回时整块隐藏 -->
    <div v-if="nextBlock" class="ad-next">
      <div v-if="nextBlock.ambiguous" class="ad-next-auto">
        下一环节存在多个分支或流程即将结束，落点按各节点配置自动取人。
      </div>
      <template v-else>
        <div class="ad-next-head">
          <span class="ad-next-title">下一环节</span>
          <span class="ad-next-node">{{ nextBlock.nodeName }}</span>
          <!-- D-DEC-160③：POOL（共享任务池）节点原先与 AUTO 共用「自动指派」字样，
               而引擎对池节点从不自动指派（须先「领取」，先到先得）；预告与实际走向不符。
               取人方式文案与 constants/enums.js 的 HANDLING_MODE 逐字对齐。 -->
          <el-tag size="small" :type="nextBlock.pick ? 'warning' : (nextBlock.pool ? 'primary' : 'info')">
            {{ nextBlock.pick ? '需指定办理人' : (nextBlock.pool ? '任务池领取' : '自动指派') }}
          </el-tag>
          <!-- D-DEC-097：多签节点在预览里曾被当成"选人节点"，只列 1 个人（引擎实为每人发一条待办） -->
          <el-tag v-if="nextBlock.mi" size="small" type="primary" effect="plain">{{ nextBlock.miLabel }}</el-tag>
        </div>
        <!-- 无候选：只有 PICK 档才是真故障（AUTO 档取不到人另有 empty_strategy 兜底，且多为动态指派节点） -->
        <div v-if="nextBlock.pick && !nextBlock.candidates.length" class="ad-next-danger">
          该节点当前无可选办理人（节点角色在本单据所属公司下没有在职非管理员成员）。
          请先在「系统管理 · 角色授权」补岗后再审批此单，否则单据会卡在这一节。
        </div>
        <el-select v-else-if="nextBlock.pick && nextBlock.candidates.length > 1" v-model="st.nextUserId"
                   class="ad-next-select" clearable filterable placeholder="请选择下一环节办理人">
          <el-option v-for="c in nextBlock.candidates" :key="c.userId" :value="c.userId" :label="c.name">
            <span>{{ c.name }}</span>
            <span class="ad-next-opt-sub">{{ subOf(c) }}</span>
          </el-option>
        </el-select>
        <!-- D-DEC-097：会签/票签不是"从名单里挑一个人"，说破"各生成一条待办"，人数与引擎集合同源 -->
        <div v-else-if="nextBlock.mi && nextBlock.candidates.length" class="ad-next-one">
          该节点为{{ nextBlock.miLabel }}：以下 <b>{{ nextBlock.candidates.length }}</b> 人<b>各生成一条待办</b>
          <span class="ad-next-names">{{ nextBlock.candidates.map((c) => c.name).join('、') }}</span>
        </div>
        <!-- D-DEC-013：PICK 节点候选唯一时也给「指定办理人」下拉。
             语义沿用提单（ASSIGN）那一套：唯一候选人只是自动带出的默认值，改派入口不能因为"只有一个"就消失，
             否则审批人看到的是"将流转给 张三"一行灰字，而服务端仍要求带点名值（曾出现"看起来没得选"）。
             放在会签分支之后，D-DEC-097 的名单口径不被吃成单选；不给 clearable——清空即未点名，
             唯一候选清空后无第二人可选，与其提交后吃红字，不如根本不提供清空。 -->
        <template v-else-if="nextBlock.pick && nextBlock.candidates.length === 1">
          <el-select v-model="st.nextUserId" class="ad-next-select" filterable
                     placeholder="已按唯一候选人自动带出">
            <el-option v-for="c in nextBlock.candidates" :key="c.userId" :value="c.userId" :label="c.name">
              <span>{{ c.name }}</span>
              <span class="ad-next-opt-sub">{{ subOf(c) }}</span>
            </el-option>
          </el-select>
          <div class="ad-next-auto">本环节由你指定办理人；候选唯一，已自动带出，如需改派请先在「系统管理 · 角色授权」补岗。</div>
        </template>
        <!-- D-DEC-160③：池节点不点名、不自动指派，说破"先到先得 + 需先领取"（POOL 软锁口径） -->
        <div v-else-if="nextBlock.pool" class="ad-next-auto">
          该节点由共享任务池承接，<span v-if="nextBlock.candidates.length">本单公司下
            <b>{{ nextBlock.candidates.length }}</b> 人可办（{{ nextBlock.candidates.map((c) => c.name).join('、') }}），</span>
          提交后不指派到具体人，谁先「领取」谁办理；未被领取时它挂在每个人的待办里显示为「待领取」。
        </div>
        <div v-else-if="nextBlock.candidates.length === 1" class="ad-next-one">
          将流转给 <b>{{ nextBlock.candidates[0].name }}</b><span v-if="subOf(nextBlock.candidates[0])">
            （{{ subOf(nextBlock.candidates[0]) }}）</span>
        </div>
        <div v-else class="ad-next-auto">
          该节点按配置自动取人{{ nextBlock.candidates.length ? '：' + nextBlock.candidates.map(c => c.name).join('、') : '' }}
        </div>
        <!-- D-DEC-013：口径从"仅候选≥2"扩到"候选非空"，唯一候选未点名同样是未指定 -->
        <div v-if="st.nextErr && nextBlock.pick && nextBlock.candidates.length" class="ad-err">
          {{ nextBlock.candidates.length > 1
            ? `请指定下一环节办理人后再提交（共 ${nextBlock.candidates.length} 人可选）`
            : '请指定下一环节办理人后再提交（本节点候选唯一，未点名服务端不受理）' }}
        </div>
      </template>
    </div>

    <template v-if="st.allowComment">
      <!-- D-DEC-198：批量通道后端（/workflow/task/batch-approve）只收一条 comment，逐单独立意见在
           现有接口上无处安放；与其偷偷套用一个意见（现网曾把一条 425 字意见原样写进 6 单的
           wf_task_history.comment），不如把"共用"这件事当场说破，并实时报字数，
           让审批人知道自己这一句将复制几遍。 -->
      <div v-if="st.batch > 1" class="ad-batch-cmt">
        <el-tag size="small" type="warning" effect="plain">同批共用意见</el-tag>
        这一条意见（当前 {{ commentChars }} 字）将<b>原样写入本批 {{ st.batch }} 单</b>的审批历史，
        逐单不可各写各的；需要按单表态请关掉本弹窗、回到待办逐条办理。
      </div>
      <div class="ad-quick">
        <div class="ad-chips">
          <span class="ad-chips-label">{{ st.decision === 'approve' ? '通过意见' : '驳回原因' }}</span>
          <el-tag v-for="c in presetList" :key="c" size="small" class="ad-chip" :title="c"
                  :closable="isCustomPhrase(c)" :close-effect="false" @click="pickQuick(c)"
                  @close="removePhrase(c)">{{ c }}</el-tag>
          <!-- E-11：意见必填却每次重写长句，这里把当前意见攒成本人常用语（换账号不可见，点 × 删除） -->
          <el-link v-if="canSavePhrase" class="ad-chip-save" type="primary" :underline="false"
                   @click="savePhrase">＋存为常用语</el-link>
        </div>
        <div v-if="recentList.length" class="ad-chips">
          <span class="ad-chips-label">最近使用</span>
          <el-tag v-for="c in recentList" :key="c" size="small" type="info" class="ad-chip" :title="c"
                  @click="pickQuick(c)">{{ c }}</el-tag>
        </div>
      </div>

      <el-input ref="commentRef" v-model="st.comment" type="textarea" :rows="3"
                maxlength="500" show-word-limit :autosize="{ minRows: 3, maxRows: 6 }"
                :placeholder="st.decision === 'approve' ? '请填写审批意见（必填）' : '请说明驳回原因（必填，将反馈给发起人）'"
                @keydown.ctrl.enter.prevent="submit" @keydown.meta.enter.prevent="submit" />
      <!-- D-DEC-073：只提示不强拦——意见里写的金额与单据金额对不上时给一句轻提示，提交动作与必填校验一律不变 -->
      <div v-if="commentAmountHint" class="ad-cmt-hint">{{ commentAmountHint }}</div>
      <div v-if="st.showError && !commentOk" class="ad-err">
        {{ st.decision === 'approve' ? '请填写不少于 2 个字的审批意见' : '请填写不少于 2 个字的驳回原因' }}
      </div>
    </template>
    <div v-else class="ad-nocmt">该确认动作由后端业务接口处理，不提供审批意见字段，本次不记录意见。</div>

    <!-- G2 传阅（2026-09-23）：审批通过后抄送给谁。
         仅当调用方显式传 allowCc=true 才渲染 —— 本组件被 30+ 处复用，默认零影响。
         与后端的关系：提交时以逗号串 ccUserIds 传给 /wf/approve，后端再与节点配置
         wf_node_config.cc_users/cc_roles 取并集后写入 wf_cc。 -->
    <div v-if="st.allowCc && st.decision === 'approve'" class="ad-cc">
      <div class="ad-cc-head">
        <span class="ad-cc-title">抄送给</span>
        <span class="ad-cc-tip">{{ ccTip }}</span>
      </div>
      <el-select v-model="st.ccUserIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
                 :remote-method="searchCc" :loading="st.ccLoading"
                 placeholder="搜索姓名选择抄送人（留空＝只按节点配置抄送）" style="width:100%">
        <el-option v-for="c in st.ccOptions" :key="c.userId" :value="c.userId"
                   :label="c.name + (c.userName ? '（' + c.userName + '）' : '')" />
      </el-select>
    </div>

    <!-- G4 自由驳回（P4）：驳回时可选退回级次。
         仅当后端开关开启（reject-targets 返回 enabled=true）才渲染 —— 开关关着连 UI 都不出现，
         避免再造悬空配置。数据来自 GET /wf/reject-targets 只读预览（silent：404/开关关 ⇒ 不显示区块）。 -->
    <div v-if="st.decision === 'reject' && rejectBlock" class="ad-rt">
      <div class="ad-cc-head">
        <span class="ad-cc-title">退回级次</span>
        <span class="ad-cc-tip">目标节点会重新打开并需重办；它之后已走过的审批全部作废重走</span>
      </div>
      <el-select v-model="st.rejectToNodeKey" clearable style="width:100%"
                 placeholder="不选＝终止流程、单据作废待重提（默认，与现状一致）">
        <el-option v-for="c in rejectBlock.candidates" :key="c.nodeKey" :value="c.nodeKey"
                   :label="c.nodeName + (c.handler ? '（' + c.handler + '）' : '')">
          <span>{{ c.nodeName }}</span>
          <span class="ad-rt-sub">退回后作废 {{ c.willVoidCount }} 个后续节点</span>
        </el-option>
      </el-select>
      <div v-if="pickedRejectTarget?.willVoidCount" class="ad-rt-warn">
        退回后以下节点将作废重走：{{ pickedRejectTarget.willVoidNodes.join('、') }}
      </div>
    </div>

    <div v-if="st.history.length" class="ad-history">
      <div class="ad-his-title">前序审批</div>
      <!-- D-DEC-079 / D-DEC-112：这一段与待办列表的「前序审批人」列同源同口径（调用方按
           节点办理留痕四类动作、时间正序喂进来）。动作标签补齐：提单/自动通过与人工通过
           在链上是三种不同的事，从前一律写「通过」，提单那一行被说成别人审过了。 -->
      <div v-for="(h, i) in st.history" :key="i" class="ad-his-row">
        <span class="ad-his-node">{{ h.node || h.nodeName || '—' }}</span>
        <span class="ad-his-who">{{ h.assignee || h.assigneeName || '—' }}</span>
        <el-tag size="small" :type="h.approved === false ? 'danger' : (h.action === 'AUTO_PASS' ? 'info' : 'success')">
          {{ h.approved === false ? '驳回' : (h.action === 'SUBMIT' ? '提交' : (h.action === 'AUTO_PASS' ? '自动通过' : '通过')) }}
        </el-tag>
        <span class="ad-his-cmt">{{ h.comment || '—' }}</span>
      </div>
    </div>

    <!-- D-DEC-022：提交失败就地回显。原先点「确认通过」被服务端业务异常拒绝时弹窗已经关掉，
         审批意见与各项选择一起丢，界面上只剩一条飘过顶部的红条，人无从判断卡在哪、要不要重来。
         传了 opts.submit 的调用点走这条路：失败 ⇒ 弹窗保持打开、意见保留、原地可重试。 -->
    <el-alert v-if="st.submitErr" class="ad-submit-err" type="error" show-icon :closable="false"
              :title="`本次${st.decision === 'approve' ? '「通过」' : '「驳回」'}未生效，弹窗已保持打开`">
      <div class="ad-submit-err-msg">{{ st.submitErr }}</div>
      <!-- D-DEC-038：拒绝语只说"不许"、不说"那该怎么办"（台账 12-22:964/973/1025 记的正是自审/连办闸
           没有任何改派出口提示），审批人只能反复点同一颗按钮。这里按后端原文给出下一步。 -->
      <div v-if="submitErrAdvice" class="ad-submit-err-msg">{{ submitErrAdvice }}</div>
      <div class="ad-submit-err-tip">修正后可直接重试；若不再办理请点「取消」——未生效的提交不会留下任何流程留痕。</div>
    </el-alert>

    <template #footer>
      <div class="ad-footer">
        <el-button v-if="st.showDetail" @click="emit('detail')">查看单据详情</el-button>
        <span class="ad-footer-actions">
          <el-button :disabled="st.submitting" @click="cancel">取消</el-button>
          <el-button :type="st.decision === 'approve' ? 'primary' : 'danger'" :loading="st.submitting" @click="submit">
            {{ st.decision === 'approve' ? '确认通过' : '确认驳回' }}
          </el-button>
        </span>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import FlowChain from '../FlowChain.vue'
import { flowApi, wfApi, store } from '../../api'
import { statusLabel } from '../../constants/billStatus'
import { docTypeLabel } from '../../constants/enums'

const emit = defineEmits(['detail'])
const commentRef = ref(null)

const QUICK_APPROVE = ['同意', '已核实无误', '符合规定', '同预算口径，同意']
const QUICK_REJECT = ['金额超出预算', '发票不合规', '资料不完整', '费用不合理', '审批流程不符']
const RECENT_KEY = 'fssc.approval.recentComments'
const PHRASE_KEY = 'fssc.approval.quickPhrases'

/**
 * E-09（2026-09-26）：历史意见与常用语原先挂在浏览器级固定键上，同一台机器换账号登录后，
 * 上一位审批人的意见原文会直接出现在对方的「最近使用」里（现网三账号连开弹窗已复现）。
 * 改为按登录用户分片存储；登出不清理（那是本人历史，不是缓存）。
 */
function uidScope() {
  const u = store.user
  return String(u?.userId ?? u?.id ?? 'anon')
}
const recentKey = () => `${RECENT_KEY}.${uidScope()}`
const phraseKey = () => `${PHRASE_KEY}.${uidScope()}`

const st = reactive({
  visible: false, title: '审批', showError: false,
  decision: 'approve', comment: '', notice: '',
  allowApprove: true, allowReject: true, allowComment: true, showDetail: false,
  summary: [], history: [], chain: [],
  // D-DEC-198：批量调用的逐单明细（items）与条数（batch）；缺省空数组/1 ⇒ 非批量调用点零变化
  items: [], batch: 1,
  next: null, nextUserId: null, nextErr: false,
  mi: null,
  // G2 传阅：allowCc 默认 false ⇒ 复用本组件的 30+ 调用点行为完全不变
  allowCc: false, ccUserIds: [], ccOptions: [], ccLoading: false,
  // G4 自由驳回：rejectInfo 为 reject-targets 只读预览结果；enabled=false 时区块整个不渲染
  rejectInfo: null, rejectToNodeKey: null,
  // ①层审批项：approvalItems 为当前节点启用中的自定义审批结果；空数组 ⇒ 区块整个不渲染
  approvalItems: [], approvalItemCode: null,
  // D-DEC-022：submit 为调用方注入的提交动作（如 wfApi.approve）；缺省 ⇒ 沿用"关窗后由调用方发请求"手感
  submit: null, submitting: false, submitErr: '',
  // D-DEC-073：意见与单据事实比对所需的取数（单号 + 金额），一律由调用方已传入的 row/items 现算，不额外发请求
  docAmounts: [], docNo: '',
})

/** 通过/驳回只有一个可选时不显示单选钮（如批量审批、列表页的单独入口） */
const showDecision = computed(() => st.allowApprove && st.allowReject)

function fmtMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 单条明细的金额取值：取不到（null/空/非数）一律返回 null，绝不折算成 0 */
function itemAmountOf(it) {
  const v = it?.amount
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/** 明细金额汇总：sum 只累加"取到值"的行，missing 如实计数（itemsSum 与批量提示共用一处口径） */
function summarizeItems(items) {
  let sum = 0, known = 0, missing = 0
  for (const it of items || []) {
    const n = itemAmountOf(it)
    if (n === null) { missing++; continue }
    sum += n; known++
  }
  return { sum: Math.round(sum * 100) / 100, known, missing }
}

/**
 * D-DEC-198：批量金额合计按明细真算。
 * 关键是把"没取到金额"与"金额为 0"分开：原实现 `Number(r.totalAmount || 0)` 把 NULL 直接当 0 累加，
 * 6 单全 NULL 时就报「金额合计 ¥0.00」——一个语法正确、业务上完全虚假的合计。
 * 现在只累加取到值的行，并如实报未取到的条数（合计为 0 且 known=0 时界面显示"均未取到"）。
 */
const itemsSum = computed(() => summarizeItems(st.items))

/** 金额字面量 → 数字（千分位/币种符号容忍）；取不到返回 null，绝不折算成 0 */
function toAmount(v) {
  if (v == null || v === '') return null
  const n = Number(String(v).replace(/[¥￥,\s]/g, ''))
  return Number.isFinite(n) ? n : null
}

/** D-DEC-073：本弹窗可用于比对的单据金额集合——批量取逐单明细，单条取行数据，自定义摘要里的金额也认 */
function collectDocAmounts(opts) {
  const out = []
  const push = (v) => {
    const n = toAmount(v)
    if (n !== null && n > 0 && !out.some((x) => Math.abs(x - n) < 0.005)) out.push(n)
  }
  ;(Array.isArray(opts.items) ? opts.items : []).forEach((it) => push(it?.amount))
  const r = opts.row
  if (r) push(r.amount ?? r.totalAmount ?? r.billAmount)
  ;(Array.isArray(opts.summary) ? opts.summary : []).forEach((s) => { if (s && (s.amount || s.label === '金额')) push(s.value) })
  return out
}

/**
 * D-DEC-073：从意见原文里取金额。只认带币种符号、带"元/万元"单位、或紧跟金额类词的数字，
 * 否则"第 3 节""2 个工作日"这类普通数字会被误报成金额不符。
 * ⑤-Ⅳb：紧跟"人均/每人/单价/每件…"这类**行级或均摊口径**的数字，天然不等于单据总额，
 * 旧实现把它们当总额比 ⇒ "人均 200.00"在 800.00 的单子上必弹不一致＝误伤合规写法，故此处直接不收。
 */
const PER_UNIT_QUAL = /(?:人均|每人|单价|每件|每份|每间|每晚|每张|单张|分摊后|明细|行金额|标准)/
function isPerUnitAmount(s, start) {
  return PER_UNIT_QUAL.test(s.slice(Math.max(0, start - 12), start))
}
function amountsInComment(text) {
  const s = String(text || '')
  const out = []
  const add = (n) => {
    if (Number.isFinite(n) && n > 0 && !out.some((x) => Math.abs(x - n) < 0.005)) out.push(n)
  }
  for (const m of s.matchAll(/[¥￥]\s*(\d[\d,]*(?:\.\d{1,2})?)/g)) {
    if (!isPerUnitAmount(s, m.index)) add(Number(m[1].replace(/,/g, '')))
  }
  for (const m of s.matchAll(/(\d[\d,]*(?:\.\d{1,2})?)\s*(万元|元)/g)) {
    if (isPerUnitAmount(s, m.index)) continue
    add(Number(m[1].replace(/,/g, '')) * (m[2] === '万元' ? 10000 : 1))
  }
  for (const m of s.matchAll(/(?:价税合计|金额|合计|实付|实报|报销|付款|冲销)[^\d]{0,8}(\d[\d,]*(?:\.\d{1,2})?)/g)) {
    if (isPerUnitAmount(s, m.index)) continue
    add(Number(m[1].replace(/,/g, '')))
  }
  return out
}

/** D-DEC-073：轻提示文案——1% 或 1 元内的尾差视为口径取整，不打扰；对不上才出声，且不拦提交 */
const commentAmountHint = computed(() => {
  const facts = st.docAmounts || []
  if (!facts.length) return ''
  const said = amountsInComment(st.comment).filter((a) => !facts.some((f) => Math.abs(a - f) <= Math.max(1, Math.abs(f) * 0.01)))
  if (!said.length) return ''
  const total = Math.round(facts.reduce((a, b) => a + b, 0) * 100) / 100
  const factText = facts.length > 1 ? `本批 ${facts.length} 单合计 ¥${fmtMoney(total)}` : `单据金额 ¥${fmtMoney(facts[0])}`
  const docText = st.docNo ? `（${st.docNo}）` : ''
  return `意见里的 ${said.map((x) => '¥' + fmtMoney(x)).join('、')} 与${factText}${docText}不一致，请核对后再提交；本提示不拦提交。`
})

/** 当前意见字数（批量"同批共用意见"提示用；含空格，与后端留痕原文一致） */
const commentChars = computed(() => String(st.comment || '').length)

/**
 * 抄送区文案：批量时必须讲清投递方式。
 * 后端批量接口 POST /workflow/task/batch-approve 的入参只有 {taskIds, comment, approved}，
 * <b>没有</b> ccUserIds；抄送只在单条 /wf/approve 的入参里生效（再与节点配置取并集写 wf_cc）。
 * 所以批量勾选了抄送人时，调用方（Approval.vue）改走逐单 /wf/approve 通道，
 * 而不是把选择悄悄丢掉——界面说的和发生的事必须一致。
 */
const ccTip = computed(() => (st.batch > 1
  ? '可选。批量接口本身不带抄送入参：一旦在此选人，本批将改为逐单办理并各自投递抄送（同一批单、同一意见，结果与批量一致）'
  : '可选。这些人会在「流程中心 · 我的抄送」看到本单（仅通过时投递，驳回不抄送）'))

/**
 * D-DEC-091 / D-DEC-110（2026-12-22:83、12-24:60）：分母口径改为「本单实际会走的节数」。
 * 后端 WfQueryService.chain() 对每个节点多发三个字段：
 *   counted=false —— 配置里有、本单不会走的节点（未命中分支、START/END 之类）；
 *   seq          —— 该节点在"实际链"里的序号（未计数节点为 null）；
 *   seqTotal     —— 实际链总节数（会签仍是 1 节，加签节点次不计入）。
 * 老包缺这三个字段时 counted 恒真、seqTotal 恒缺 ⇒ 自动退回改动前的算法，同一份代码两种包都不断档。
 */
const isCountedNode = (n) => n && n.counted !== false
const chainSteps = computed(() => st.chain.filter(isCountedNode))
const chainSeqTotal = computed(() => {
  const hit = st.chain.find((n) => typeof n.seqTotal === 'number')
  return hit ? hit.seqTotal : chainSteps.value.length
})
/** 当前节在「实际链」里的下标（-1 表示没有进行中的节点） */
const chainStepIndex = computed(() => chainSteps.value.findIndex((n) => n.state === 'CURRENT'))
const chainIndex = computed(() => (chainStepIndex.value >= 0 ? chainStepIndex.value + 1 : chainSteps.value.length))
const chainCurrentName = computed(() => {
  const cur = st.chain.find((n) => n.state === 'CURRENT')
  return cur ? cur.nodeName : '已办结'
})
/**
 * E-08（2026-09-26）：原先恒显示「第 N/总节点数」，而分母是模型配的全部节点，
 * 含本单未必会走的条件分支（TR_PAY 实走 4 节，弹窗报"第 7/9 节"）⇒ 审批人误判自己在链条后段、
 * 误判剩余周期。链上还有"尚未到达"节点时路径未定，只报已过节数；路径全定时才给分母。
 * D-DEC-091 续：这里的分母与节点集合都只取 counted 节点，加签（ADDSIGN）与未走分支不再抬高分母。
 */
const chainProgressText = computed(() => {
  const cur = chainCurrentName.value
  const steps = chainSteps.value
  const done = steps.filter((n) => n.state === 'DONE' || n.state === 'REJECTED').length
  const hasTodo = steps.some((n) => !n.state || n.state === 'TODO')
  return hasTodo
    ? `已过 ${done} 节 · 当前【${cur}】`
    : `第 ${chainIndex.value}/${chainSeqTotal.value} 节 · 当前【${cur}】`
})

/**
 * 「下一步处理人」区块数据（P1 取人方式 PICK）。
 * 驳回时整块隐藏：驳回会终止实例，不存在"下一环节交给谁"。
 * ambiguous=true 表示后端无法唯一预判下一节点（多分支/已到终点），此时只给线索、不点名。
 */
const nextBlock = computed(() => {
  const n = st.next
  if (!n || st.decision !== 'approve') return null
  // D-DEC-097：multiInstance/multiType 由 WfEngineFacade.nextCandidates 下发，
  // 取值域与引擎注入 <nodeKey>_assignees 的那一份判定逐字一致（ALL/SEQ/VOTE）。
  const mi = !!n.multiInstance
  const miType = String(n.multiType || '').toUpperCase()
  return {
    ambiguous: !n.nodeKey,
    nodeName: n.nodeName || '—',
    pick: n.mode === 'PICK',
    // D-DEC-160③：WfEngineFacade.nextCandidates 的 mode 取值域是 PICK|POOL|AUTO，
    // 此前只判 PICK，POOL 落进"自动指派"那一档 → 池节点被预告成"自动指派给某人"，
    // 与"先到先得 + 需先领取"的软锁口径冲突，审批人据此误判投递对象。
    pool: n.mode === 'POOL',
    candidates: Array.isArray(n.candidates) ? n.candidates : [],
    mi,
    miLabel: mi ? (miType === 'VOTE' ? '票签' : miType === 'SEQ' ? '顺序会签' : '会签') : '',
  }
})

/** 多实例已投票数（赞成+反对）；进度条与"已投 x/n"共用 */
const miVoted = computed(() => {
  const m = st.mi
  return m ? Number(m.approved || 0) + Number(m.rejected || 0) : 0
})
const miPct = computed(() => {
  const total = Number(st.mi?.total || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.round((miVoted.value / total) * 100))
})
const miTypeLabel = computed(() => {
  const t = st.mi?.multiType
  return t === 'VOTE' ? '票签' : t === 'SEQ' ? '顺序会签' : '会签'
})

const miReach = computed(() => {
  const m = st.mi
  if (!m || !m.multi) return false
  const total = Number(m.total || 0)
  if (total <= 0) return false
  if (m.multiType === 'VOTE') {
    return Number(m.approved || 0) * 100 >= Number(m.voteRatio || 50) * total
  }
  return miVoted.value >= total
})

/** 是否已达成否决线（仅票签有"比例否决"的概念；会签任一驳回即否决） */
const miRejectReach = computed(() => {
  const m = st.mi
  if (!m || m.multiType !== 'VOTE') return false
  const total = Number(m.total || 0)
  return total > 0 && Number(m.rejected || 0) * 100 > (100 - Number(m.voteRatio || 50)) * total
})

/**
 * 进度提示语：把"这一票的分量"说清楚——票签投票人最关心的是"我这票能不能收口"，
 * 光给"已投 2/3"看不出来，所以直接给通过线还差几票 / 是否已达否决线。
 */
const miHint = computed(() => {
  const m = st.mi
  if (!m || !m.multi) return ''
  const total = Number(m.total || 0)
  if (m.multiType === 'VOTE') {
    const ack = Number(m.approved || 0)
    const ratio = Number(m.voteRatio || 50)
    const need = m.passThreshold == null ? Math.ceil((ratio * total) / 100) : Number(m.passThreshold)
    if (miRejectReach.value) {
      return `反对票已达否决线（需 > ${100 - ratio}%），本节点将判否决并驳回单据`
    }
    if (miReach.value) return `已达通过线（赞成 ≥ ${need}/${total}），本节点立即收口流转`
    return `需 ≥ ${need}/${total} 赞成才通过；当前 ${ack} 票，还差 ${Math.max(0, need - ack)} 票`
  }
  if (m.multiType === 'SEQ') return `顺序会签：按顺序逐个办理，共 ${total} 人，人人通过才流转`
  return `会签：${total} 人全部通过后流转，任一驳回即否决本节点`
})

/**
 * 会签/票签进度。与 loadNext 同为"锦上添花"：silent 调用，老后端无此端点（404/403）
 * 或查询失败时 st.mi 保持 null → 区块不渲染，审批照旧可提交。
 */
let miReq = ''
function loadMi(taskId) {
  try {
    wfApi.miProgress(taskId, { silent: true })
      .then((res) => { if (miReq === taskId && res && typeof res === 'object') st.mi = res })
      .catch(() => { /* 端点未部署/无权，静默不显示 */ })
  } catch (_) { /* 忽略 */ }
}

/** 下拉副标题：部门 + 在办量，帮审批人挑"谁现在有空"而不是熟的人 */
function subOf(c) {
  if (!c) return ''
  const parts = []
  if (c.deptName) parts.push(c.deptName)
  if (c.pendingCount !== undefined && c.pendingCount !== null) parts.push(`在办 ${c.pendingCount}`)
  return parts.join(' · ')
}

const recent = ref(readRecent())
const phrases = ref(readPhrases())
const presetList = computed(() => {
  const base = st.decision === 'approve' ? QUICK_APPROVE : QUICK_REJECT
  return [...base, ...phrases.value[poolKey()]]
})
const isCustomPhrase = (p) => phrases.value[poolKey()].includes(p)
/** 当前意见值得攒下来：非空、且预置/常用里还没有 */
const canSavePhrase = computed(() => commentOk.value && !presetList.value.includes(st.comment.trim()))
const recentList = computed(() => {
  const pool = recent.value[poolKey()]
  return pool.filter((r) => !presetList.value.includes(r)).slice(0, 3)
})
const commentOk = computed(() => String(st.comment || '').trim().length >= 2)

function poolKey() {
  return st.decision === 'approve' ? 'approve' : 'reject'
}
function readBucket(key) {
  const empty = { approve: [], reject: [] }
  try {
    const raw = JSON.parse(localStorage.getItem(key) || 'null')
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return empty
    return {
      approve: Array.isArray(raw.approve) ? raw.approve.slice(0, 8) : [],
      reject: Array.isArray(raw.reject) ? raw.reject.slice(0, 8) : [],
    }
  } catch { return empty }
}
/** 历史意见按决策分池存：驳回原因不会出现在「通过」页签，反之亦然 */
function readRecent() {
  const r = readBucket(recentKey())
  return { approve: r.approve.slice(0, 8), reject: r.reject.slice(0, 8) }
}
/** E-11：常用语（本人攒，换账号不可见）；预置词之外的自留快捷语 */
function readPhrases() {
  const r = readBucket(phraseKey())
  return { approve: r.approve.slice(0, 10), reject: r.reject.slice(0, 10) }
}
function persistPhrases() {
  try { localStorage.setItem(phraseKey(), JSON.stringify(phrases.value)) } catch { /* 隐私模式下忽略 */ }
}
function savePhrase() {
  const c = String(st.comment || '').trim()
  if (c.length < 2 || isCustomPhrase(c) || presetList.value.includes(c)) return
  phrases.value = { ...phrases.value, [poolKey()]: [c, ...phrases.value[poolKey()]].slice(0, 10) }
  persistPhrases()
}
function removePhrase(c) {
  phrases.value = { ...phrases.value, [poolKey()]: phrases.value[poolKey()].filter((p) => p !== c) }
  persistPhrases()
}
function writeRecent(comment, decision) {
  const key = decision === 'approve' ? 'approve' : 'reject'
  const list = [comment, ...recent.value[key].filter((r) => r !== comment)].slice(0, 8)
  recent.value = { ...recent.value, [key]: list }
  try { localStorage.setItem(recentKey(), JSON.stringify(recent.value)) } catch { /* 隐私模式下忽略 */ }
}

/**
 * 审批链由弹窗自己按行数据的流程实例查：30+ 调用点不用各传一次，
 * 传了 opts.chain 则以调用方为准（如工作台已有缓存）。
 */
let chainReq = ''
function loadChain(id) {
  try {
    flowApi.chain(id, { silent: true })
      .then((res) => { if (chainReq === id) st.chain = Array.isArray(res) ? res : [] })
      .catch(() => { /* 辅助信息，查不到就不显示 */ })
  } catch (_) { /* 忽略 */ }
}

/**
 * 下一环节候选人。整块是"锦上添花"，因此：
 *  - silent 调用，后端未部署该端点（404/403）或解析失败时 st.next 保持 null → 区块不渲染，审批照旧可提交；
 *  - 老后端下 PICK 节点本就不存在（灰度开关必须与新 jar 同步开），所以静默降级不会让必选条件失配；
 *  - 服务端 complete 仍会独立校验点名，绕开弹窗直接调接口只会收到可读的 BizException。
 */
let nextReq = ''
function loadNext(taskId) {
  try {
    wfApi.nextCandidates(taskId, { silent: true })
      .then((res) => {
        if (nextReq !== taskId || !res || typeof res !== 'object') return
        st.next = res
        // PICK 且候选唯一：后端此时仍要求带上点名值，故自动预选，避免"看起来没得选却被判未指定"。
        if (res.mode === 'PICK' && Array.isArray(res.candidates) && res.candidates.length === 1) {
          st.nextUserId = res.candidates[0].userId
        }
      })
      .catch(() => { /* 端点未部署/无权，静默不显示 */ })
  } catch (_) { /* 忽略 */ }
}

/**
 * G2 传阅：抄送候选人远程搜索。与 loadNext 同为"锦上添花"——silent 调用，
 * 端点未部署（404）或无权时 st.ccOptions 为空，用户不选即可，绝不阻断审批提交。
 * 用自增序号丢弃过期响应，避免快速连续输入时后到的旧结果覆盖新结果。
 */
let ccSeq = 0
let rejectSeq = 0
/**
 * G4 自由驳回：拉取可退回级次（只读预览）。
 * silent：开关未开 / 端点缺失（404）都静默降级为「不显示区块」，绝不阻断驳回提交 ——
 * 该能力是配置驱动的增量功能，旧后端/未开开关时必须完全隐形。
 */
async function loadRejectTargets(taskId) {
  const seq = ++rejectSeq
  try {
    const res = await wfApi.rejectTargets({ taskId }, { silent: true })
    if (seq !== rejectSeq) return
    st.rejectInfo = res && typeof res === 'object' ? res : null
  } catch {
    if (seq === rejectSeq) st.rejectInfo = null
  }
}
/**
 * 退回级次区块可见性：只在后端明确 enabled=true 时渲染。
 * enabled=false（开关关 / 票签节点 / 并联在办）⇒ 整个区块消失，界面退回「纯终止式驳回」现状。
 */
const rejectBlock = computed(() => {
  const r = st.rejectInfo
  return r && r.enabled && Array.isArray(r.candidates) && r.candidates.length ? r : null
})
const pickedRejectTarget = computed(() =>
  rejectBlock.value?.candidates?.find((c) => c.nodeKey === st.rejectToNodeKey) || null)

/**
 * silent：开关未配 / 老后端无此端点（404/403）都静默降级为「不显示区块」，绝不阻断审批提交 ——
 * 本能力是配置驱动的增量功能（表里没有行就一个按钮都不出现），旧后端下必须完全隐形。
 */
let approvalSeq = 0
/** 审批项候选的当前任务标识（open/cancel 维护；批量时为 ''，不查） */
let approvalReq = ''
async function loadApprovalItems(taskId) {
  const seq = ++approvalSeq
  try {
    const rows = await wfApi.approvalItems(taskId, { silent: true })
    if (seq !== approvalSeq) return
    st.approvalItems = Array.isArray(rows) ? rows : []
  } catch {
    if (seq === approvalSeq) st.approvalItems = []
  }
}

/** 审批项区块可见性：无配置 ⇒ 整块消失，界面退回「通过/驳回」二选一的现状 */
const approvalBlock = computed(() => (st.approvalItems.length ? st.approvalItems : null))

/**
 * 选中审批项即决定结果类别（与后端 normalizeCategory + complete() 的覆盖口径同源）：
 * POSITIVE 正向 → approved=true；REJECT 驳回 / TERMINATE 终止 → approved=false。
 * 前端只负责把 decision 摆到正确的页签上（驳回原因框、退回级次才该出现），
 * 真正的语义判定仍由服务端按 category 执行，避免两处各表一套真值。
 */
function pickApprovalItem(item) {
  const next = item.category === 'POSITIVE' ? 'approve' : 'reject'
  // 先切页签（onDecisionChange 会清空选中项），再落选中项，避免"类别与页签不符"
  if (next !== st.decision) onDecisionChange(next)
  st.approvalItemCode = item.itemCode
}

/** 已选审批项的展示信息（含"表单操作"提示，让审批人知道这一项还会改表单字段） */
const pickedApprovalItem = computed(() =>
  st.approvalItems.find((i) => i.itemCode === st.approvalItemCode) || null)

async function searchCc(keyword) {
  const seq = ++ccSeq
  st.ccLoading = true
  try {
    const rows = await flowApi.ccCandidates({ keyword: keyword || undefined, limit: 100 }, { silent: true })
    if (seq !== ccSeq) return
    st.ccOptions = Array.isArray(rows) ? rows : []
  } catch (_) {
    if (seq === ccSeq) st.ccOptions = []
  } finally {
    if (seq === ccSeq) st.ccLoading = false
  }
}

/** 从行数据自动摘出常用上下文，各页无需逐字段拼 */
function buildSummary(row) {
  if (!row) return []
  const out = []
  const push = (label, value, extra) => {
    if (value !== undefined && value !== null && value !== '') out.push({ label, value, ...extra })
  }
  push('单号', row.billNo || row.orderNo || row.docNo || row.no)
  push('类型', docTypeLabel(row.billType || row.bizType))
  const amt = row.amount ?? row.totalAmount ?? row.billAmount
  if (amt !== undefined && amt !== null && amt !== '') {
    push('金额', '¥ ' + Number(amt).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }), { amount: true })
  }
  push('提交人', row.submitTypeName || row.applyUserName || row.createByName || row.initiatorName || row.employeeName)
  push('当前节点', row.taskName || row.currentNode || row.nodeName)
  push('当前在办人', row.assigneeName
    // 未指派时若名单只有一人，等价于"必然流到他"，直接给出，省得审批人再猜
    || (row.candidateNames && row.candidateNames.length === 1 ? row.candidateNames[0] : null))
  if (Array.isArray(row.candidateNames) && row.candidateNames.length > 1) {
    push('本节点可办理', row.candidateNames.join('、'), { full: true })
  }
  push('单据状态', row.billStatus ? statusLabel(row.billStatus) : null)
  // 标题往往最长，放最后并独占一行，避免把两列网格挤歪
  push('标题', row.title || row.summary || row.billTitle, { full: true })
  return out
}

/**
 * D-DEC-198：批量默认提示。原先只有一句"请逐条确认适用"，但弹窗里根本没有可逐条确认的东西，
 * 等于把责任推给审批人却不给工具。现在明确三件事：意见共用、明细可查、金额未取到的条数。
 */
function batchNotice(batch, items) {
  if (!(batch > 1)) return ''
  const miss = summarizeItems(items).missing
  return `将对勾选的 ${batch} 条待办统一填写同一条意见（同批共用，逐单不各自留痕），请对照下方明细逐单确认适用。`
    + (miss ? `本批有 ${miss} 条未取到金额，合计不含这些单。` : '')
}

/**
 * @param opts.row        行数据（自动摘摘要）
 * @param opts.summary    自定义摘要 [{label,value}]，给了就不再从 row 推导
 * @param opts.decision   初始决策 'approve' | 'reject'，默认 'approve'
 * @param opts.title      弹窗标题
 * @param opts.notice     顶部黄条提示（如超标、超预算）
 * @param opts.batch      批量件数 >1 时用于标题与提示
 * @param opts.items      D-DEC-198 批量逐单明细：[{ key, taskId, billNo, billTypeText, amount(Number|null),
 *                        node, unresolved, locked, lockOwnerName }]。给了才渲染「本批明细」表并把合计改为
 *                        按明细真算；缺省空数组 ⇒ 摘要式旧观感逐字不变（30+ 复用点零影响）。
 *                        amount 传"取不到"时请传 null 而不是 0 —— 弹窗按 null 判定"未取到"。
 * @param opts.allowApprove / opts.allowReject 该决策是否可选（都可选才显示单选钮）
 * @param opts.allowComment 后端是否支持审批意见；false 时隐藏输入框并明示本次不记录意见
 * @param opts.history    前序审批链
 * @param opts.chain      全流程节点链（/wf/query/chain），用于显示"第几节/当前节点"
 * @param opts.taskId     待办任务标识；给了才查询「下一环节候选人」（列表页多为 row.taskId，可自动兜底）
 * @param opts.showDetail 是否显示「查看单据详情」按钮（父页接 @detail）
 * @param opts.allowCc    是否显示「抄送给」多选区（G2 传阅）；缺省 false —— 不渲染也不发请求
 * @param opts.submit     提交动作 (payload) => Promise；给了就由弹窗在关闭之前执行：
 *                        成功 ⇒ 关窗并 resolve；失败 ⇒ 弹窗保持打开、意见与选择保留、就地显示原因
 *                        （D-DEC-022）。不给 ⇒ 沿用旧手感（先关窗、resolve 后由调用方自己发请求）。
 * @returns Promise<{approved:boolean, comment:string, nextAssigneeUserId?:number, ccUserIds?:string,
 *                   rejectToNodeKey?:string, approvalItemCode?:string} | null>
 *          approvalItemCode（①层审批项）：仅当后端在本节点配了启用中的审批项、且用户点选时才非空；
 *          携带时服务端按其类别（正向/驳回/终止）覆盖 approved 并写入 ActionResult 流程变量。
 */
function open(opts = {}) {
  const batch = Number(opts.batch || 1)
  const allowApprove = opts.allowApprove !== false
  const allowReject = opts.allowReject !== false
  let decision = opts.decision === 'reject' ? 'reject' : 'approve'
  if (decision === 'reject' && !allowReject) decision = 'approve'
  if (decision === 'approve' && !allowApprove) decision = 'reject'
  Object.assign(st, {
    visible: true, showError: false,
    title: opts.title || (batch > 1 ? `批量审批（${batch} 条）` : '审批'),
    decision,
    comment: '', notice: opts.notice || batchNotice(batch, opts.items),
    allowApprove, allowReject,
    allowComment: opts.allowComment !== false,
    showDetail: !!opts.showDetail,
    summary: opts.summary ? opts.summary.filter((s) => s.value) : buildSummary(opts.row),
    // D-DEC-198：逐单明细与条数（合计、意见共用提示、抄送文案都读这两项）
    items: Array.isArray(opts.items) ? opts.items : [], batch,
    history: opts.history || [],
    chain: opts.chain || [],
    next: null, nextUserId: null, nextErr: false,
    mi: null,
    allowCc: !!opts.allowCc,
    ccUserIds: [], ccOptions: [], ccLoading: false,
    // G4：每次打开重置退回级次状态；以驳回态打开时立即拉取候选
    rejectInfo: null, rejectToNodeKey: null,
    // ①层审批项：每次打开清空；候选由 loadApprovalItems 拉取（无候选则区块不渲染）
    approvalItems: [], approvalItemCode: null,
    // D-DEC-022：每次打开重新登记提交动作，并清掉上一次的失败回显
    submit: typeof opts.submit === 'function' ? opts.submit : null,
    submitting: false, submitErr: '',
    // D-DEC-073：每次打开重取本单事实（金额 / 单号），用于意见与单据的轻比对提示
    docAmounts: collectDocAmounts(opts),
    docNo: opts.batch > 1 ? '' : String(opts.row?.billNo || opts.row?.orderNo || opts.row?.docNo || ''),
  })
  // G2 传阅：仅显式开启时才拉候选人；默认不开启 ⇒ 不产生任何额外请求（对 30+ 复用点零影响）
  if (st.allowCc) searchCc('')
  chainReq = opts.row?.processInstanceId || opts.row?.procInstId || ''
  if (!st.chain.length && chainReq) loadChain(chainReq)
  // 批量件数 >1 时不查也不点名：每条单据的下一节点各不相同，一份名单套不到 N 条上。
  nextReq = batch > 1 ? '' : String(opts.taskId || opts.row?.taskId || '')
  if (nextReq) loadNext(nextReq)
  // G4：以驳回态打开时立即拉取可退回级次（用户后切到驳回由 onDecisionChange 兜底再拉）
  if (decision === 'reject' && nextReq) loadRejectTargets(nextReq)
  // 会签/票签进度：与下一环节同样只对单条查（批量时各条进度互不相同，给一份会误导）
  miReq = nextReq
  if (miReq) loadMi(miReq)
  // ①层审批项：批量时各条的节点配置可能不同，一份按钮套不到 N 条上，故同样只对单条查
  approvalReq = batch > 1 ? '' : nextReq
  if (approvalReq) loadApprovalItems(approvalReq)
  if (st.decision === 'approve' && st.allowComment) st.comment = '同意'
  return new Promise((resolve) => { st.resolve = resolve })
}

function onDecisionChange(v) {
  st.showError = false
  st.nextErr = false
  st.comment = v === 'approve' && st.allowComment ? '同意' : ''
  // ①层审批项：用户手动切页签 ⇒ 视为放弃自定义结果，退回「纯通过/驳回」现状
  st.approvalItemCode = null
  // G4：切到驳回才拉取退回级次；切回通过即清空选择（提交时只对驳回生效，双保险）
  if (v === 'reject' && nextReq) {
    if (!st.rejectInfo) loadRejectTargets(nextReq)
  } else {
    st.rejectToNodeKey = null
  }
}

function pickQuick(c) {
  const cur = String(st.comment || '').trim()
  const isPreset = QUICK_APPROVE.includes(cur) || QUICK_REJECT.includes(cur)
  st.comment = !cur || isPreset ? c : `${cur}；${c}`
}

function focusComment() {
  commentRef.value?.focus?.()
}

function cancel() {
  if (st.submitting) return // 提交在飞：不让中途关窗，否则成功/失败都无从回显
  st.visible = false
  nextReq = ''
  miReq = ''
  approvalReq = ''
  st.resolve?.(null)
  st.resolve = null
}

/** 弹窗内一次性收口：关窗 + 把 payload 交回调用方 */
function closeWithResult(payload) {
  st.visible = false
  nextReq = ''
  miReq = ''
  approvalReq = ''
  st.resolve?.(payload)
  st.resolve = null
}

/**
 * D-DEC-038（负向操作零反馈家族）：把服务端拒绝原文翻译成"下一步做什么"。
 * 只补出口、不改判据 —— 服务端各条闸（自审/连办、任务池须先领取、下一环节须点名、期间已结账）
 * 一律原样保留，重复点击也不会放行；缺的是"该走哪条合法出口"这句话（台账 12-22:964/973/1025）。
 * 未命中任何已知拒绝族时返回空串 ⇒ 只显示后端原文，不编造建议。
 */
const submitErrAdvice = computed(() => {
  const m = String(st.submitErr || '')
  if (!m) return ''
  if (/自审|同一人|连办/.test(m)) {
    return '处置：内控闸口不允许同一人在同一条流程里连办多节，重复提交不会放行。请点「取消」回到待办列表，用行内「转办」把本节点交给同角色其他人，或联系流程管理员「改派」。'
  }
  if (/任务已被处理|不存在或已处理/.test(m)) {
    return '处置：本任务已被办理或已流转。请点「取消」回列表刷新，再到「单据追溯」确认最新落点，不要重复提交。'
  }
  if (/会计期间|结账/.test(m)) {
    return '处置：该单据所属会计期间已结账，本期间内不能再办理。请联系总账会计按流程打开期间（或调整期次）后再回来办理。'
  }
  if (/须先.*领取|请先.*领取/.test(m)) {
    return '处置：任务池节点要先「领取」再办理。请点「取消」回列表点该行「领取」，成功后重新打开本弹窗并重填意见（本次未生效的意见不会入库，也不会自动带回）。'
  }
  if (/指定.*办理人|未点名|下一环节/.test(m)) {
    return '处置：请在上方「下一环节」区块点名办理人后再次提交 —— 本节点按配置需由上一环节指定到人。'
  }
  if (/无权|权限/.test(m)) {
    return '处置：当前账号没有本节点该动作的权限点（或已不在候选名单内）。请到「系统管理 · 角色授权」核岗，或改由名单内同事办理。'
  }
  return ''
})

function submit() {
  if (st.submitting) return
  if (st.allowComment && !commentOk.value) { st.showError = true; return }
  const nb = nextBlock.value
  // 与后端 enforcePickOnApprove 同一判定（下一节点唯一 + PICK + 候选非空）：
  // 候选≥2 未点名、候选=0 无岗可派，都在此处先拦住，省得提交后吃一个红色报错。
  if (nb && nb.pick && nb.candidates.length === 0) { st.nextErr = true; return }
  // D-DEC-013：把「候选=1」并入未点名拦截（原条件 >1 只覆盖多候选）。
  // 注意这不是收紧服务端闸——服务端对唯一候选同样要求点名（:1815 一带的 enforcePickOnApprove 未动），
  // 只是把已经存在的规则在前端说破，避免"界面没得选、提交却报未指定"。
  if (nb && nb.pick && nb.candidates.length >= 1 && !st.nextUserId) { st.nextErr = true; return }
  const comment = st.allowComment ? String(st.comment).trim() : ''
  const payload = {
    approved: st.decision === 'approve', comment,
    nextAssigneeUserId: st.decision === 'approve' ? st.nextUserId : null,
    // G2 传阅：逗号串与后端 ApproveReq.ccUserIds 同形；驳回不投递故不带
    ccUserIds: st.decision === 'approve' && st.ccUserIds.length ? st.ccUserIds.join(',') : null,
    // G4 自由驳回：仅在驳回且用户显式选了级次时携带；不选 ⇒ 后端走原「终止本流程」路径
    rejectToNodeKey: st.decision === 'reject' ? (st.rejectToNodeKey || null) : null,
    // ①层审批项：选了自定义结果才携带；不选 ⇒ 后端完全不走审批项分支（逐字不变）
    approvalItemCode: st.approvalItemCode || null,
  }
  // 旧手感（未注入 submit 的 30+ 调用点）：先关窗、把 payload 交出去，请求由调用方自己发。
  if (typeof st.submit !== 'function') {
    if (comment) writeRecent(comment, st.decision)
    closeWithResult(payload)
    return
  }
  // D-DEC-022：注入了提交动作 ⇒ 由弹窗自己执行，成功后才关窗。
  // 失败不关窗：意见、下一环节点名、审批项选择全部保留，原因就地显示，可原地重试。
  st.submitting = true
  st.submitErr = ''
  Promise.resolve()
    .then(() => st.submit(payload))
    .then(() => {
      if (comment) writeRecent(comment, st.decision)
      st.submitting = false
      closeWithResult(payload)
    })
    .catch((e) => {
      st.submitting = false
      st.submitErr = e?.message || '服务端未返回具体原因（本次提交未生效）'
    })
}

/**
 * 用户点 X / 按 ESC 的收口：v-model 变 false 时若不 resolve，调用方的 await 会永久挂住
 * （原实现即如此：只能靠「取消」按钮走 cancel()）。这里统一按取消结算。
 * 提交在飞时拒绝这次关闭，失败原因必须留在屏上。
 */
function onVisibleChange(v) {
  if (v) return
  if (st.submitting) { st.visible = true; return }
  cancel()
}

defineExpose({ open })
</script>

<style scoped>
.approval-dialog :deep(.el-dialog__body) { padding-top: 12px; }
.ad-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 16px;
  background: #f7f8fa; border-radius: 4px; padding: 10px 12px; margin-bottom: 12px; }
.ad-sum-item { min-width: 0; }
.ad-sum-item.ad-full { grid-column: 1 / -1; }
.ad-sum-k { color: #909399; font-size: 12px; margin-right: 6px; }
.ad-sum-v { color: #303133; font-size: 13px; word-break: break-all; }
.ad-sum-v.ad-amt { font-weight: 600; color: #b8860b; }
.ad-chain { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #606266;
  border: 1px solid #e4e7ed; border-left: 3px solid #409eff; border-radius: 4px;
  padding: 7px 10px; margin-bottom: 12px; background: #fff; }
.ad-chain b { color: #409eff; }
/* 会签/票签进度：与审批链同族边框，但用紫色区分"这是投票统计，不是流程位置" */
.ad-mi { border: 1px solid #e4e7ed; border-left: 3px solid #9b59b6; border-radius: 4px;
  padding: 8px 10px; margin-bottom: 10px; background: #fff; }
.ad-mi-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; color: #606266; }
.ad-mi-title { color: #909399; }
.ad-mi-count b { color: #9b59b6; }
.ad-mi-hint { font-size: 12px; color: #909399; margin-top: 6px; line-height: 18px; }
.ad-mi-votes { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.ad-mi-tip { max-width: 320px; line-height: 20px; }
.ad-mi-tip-who { font-weight: 600; margin-bottom: 2px; }
.ad-mi-tip-time { color: #c0c4cc; font-size: 12px; }
.ad-mi-tip-cmt { word-break: break-all; }
.ad-decision { margin-bottom: 10px; }
/* 下一环节点名：与上方审批链同族边框，但用橙色（标签同色）表示"这里需要你做一次选择" */
.ad-next { border: 1px solid #e4e7ed; border-left: 3px solid #e6a23c; border-radius: 4px;
  padding: 8px 10px; margin-bottom: 10px; background: #fff; }
.ad-next-head { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #606266; }
.ad-next-title { color: #909399; }
.ad-next-node { font-weight: 600; color: #303133; }
.ad-next-select { width: 100%; margin-top: 8px; }
.ad-next-one { font-size: 13px; color: #303133; margin-top: 6px; }
/* D-DEC-097：会签名单单独一行，人数与人名分得清 */
.ad-next-names { display: block; margin-top: 3px; font-size: 12px; color: #606266; }
.ad-next-auto { font-size: 12px; color: #909399; margin-top: 6px; }
.ad-next-opt-sub { float: right; margin-left: 16px; color: #909399; font-size: 12px; }
.ad-next-danger { font-size: 12px; color: #f56c6c; background: #fef0f0; border-radius: 4px;
  padding: 7px 9px; margin-top: 6px; line-height: 20px; }
.ad-quick { margin-bottom: 8px; min-width: 0; }
.ad-chips { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 6px; min-width: 0; }
.ad-chips-label { flex: 0 0 auto; font-size: 12px; color: #909399; }
/* 历史意见是 500 字自由文本：允许收缩到弹窗宽度内，超出用省略号 + title 悬浮看全文 */
.ad-chip { cursor: pointer; max-width: 100%; min-width: 0; }
.ad-chip :deep(.el-tag__content) { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 「＋存为常用语」不参与收缩，否则长意见会把入口挤成竖排 */
.ad-chip-save { flex: 0 0 auto; font-size: 12px; }
.ad-nocmt { font-size: 12px; color: #909399; background: #f7f8fa; border-radius: 4px; padding: 8px 10px; }
/* G2 传阅：与「下一环节」同族边框，用青色区分"这只会多一条通知，不改变流程走向" */
.ad-cc { border: 1px solid #e4e7ed; border-left: 3px solid #17b3a3; border-radius: 4px;
  padding: 8px 10px; margin-top: 10px; background: #fff; }
.ad-cc-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.ad-cc-title { font-size: 12px; color: #909399; }
.ad-cc-tip { font-size: 12px; color: #909399; }
/* G4 自由驳回：退回级次区块（仅开关开启时渲染） */
.ad-rt { border: 1px solid #e4e7ed; border-left: 3px solid #f56c6c; border-radius: 4px;
  padding: 8px 10px; margin-top: 10px; }
.ad-rt-sub { float: right; font-size: 12px; color: #909399; margin-left: 12px; }
.ad-rt-warn { font-size: 12px; color: #e6a23c; margin-top: 6px; line-height: 18px; }
/* ①层审批项：与「下一环节」同族边框，用深蓝区分"这是节点预置的结果，不只是同意/不同意" */
.ad-ai { border: 1px solid #e4e7ed; border-left: 3px solid #2c6fbb; border-radius: 4px;
  padding: 8px 10px; margin-bottom: 10px; background: #fff; }
.ad-ai-items { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.ad-ai-tag { cursor: pointer; }
.ad-ai-clear { font-size: 12px; margin-left: 4px; }
.ad-ai-hint { font-size: 12px; color: #909399; margin-top: 6px; line-height: 18px; }
.ad-err { color: #f56c6c; font-size: 12px; margin-top: 4px; }
/* D-DEC-073：金额与单据不一致的轻提示——与必填报错分开配色，避免被读成"提交会被拒" */
.ad-cmt-hint { color: #e6a23c; font-size: 12px; line-height: 1.5; margin-top: 4px; }
/* D-DEC-198 批量逐单明细：与审批链同族边框，用绿色区分"这是你要签字的对象清单" */
.ad-items { border: 1px solid #e4e7ed; border-left: 3px solid #67c23a; border-radius: 4px;
  padding: 8px 10px; margin-bottom: 10px; background: #fff; }
.ad-items-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12px;
  color: #606266; margin-bottom: 6px; }
.ad-items-sum { margin-left: auto; color: #606266; }
.ad-items-amt { color: #b8860b; font-size: 13px; }
.ad-items-miss { color: #e6a23c; }
.ad-items-none { color: #c0c4cc; font-size: 12px; }
.ad-items-table { width: 100%; }
/* 「同批共用意见」条：紧贴意见框上方，说破"这一句会复制 N 遍" */
.ad-batch-cmt { font-size: 12px; color: #606266; background: #fdf6ec; border: 1px solid #faecd8;
  border-radius: 4px; padding: 7px 9px; margin-bottom: 8px; line-height: 19px; }
/* D-DEC-022：提交失败就地回显（弹窗不关，意见保留） */
.ad-submit-err { margin-top: 12px; }
.ad-submit-err-msg { font-size: 13px; line-height: 20px; word-break: break-all; }
.ad-submit-err-tip { font-size: 12px; color: #909399; line-height: 18px; margin-top: 4px; }
.ad-history { margin-top: 14px; border-top: 1px dashed #dcdfe6; padding-top: 8px; }
.ad-his-title { font-size: 12px; color: #909399; margin-bottom: 6px; }
.ad-his-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #606266; line-height: 22px; }
.ad-his-node { min-width: 90px; }
.ad-his-who { min-width: 64px; }
.ad-his-cmt { color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.ad-footer { display: flex; align-items: center; gap: 8px; }
.ad-footer-actions { margin-left: auto; display: flex; gap: 8px; }
</style>
