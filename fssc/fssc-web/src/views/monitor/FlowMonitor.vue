<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-monitor">
    <!-- 权限门控：
         本页展示「全公司在途单据」（单据号/金额/当前节点/处理人/停留时长），
         仅持 wf:instance:list 的财务管理层与共享运营岗可见。
         入口已在 Layout.vue 用户菜单/快捷键中隐藏，此处为第二道防线（防直达 URL）。
         无权限时给出说明而非弹全局错误提示 —— 该提示来自 api.js 响应拦截器，catch 吞不掉。 -->
    <div v-if="!canView" class="no-perm">
      <el-empty :description="$t('monitor.flowMonitor.No')">
        <template #description>
          <p class="np-title">无权限查看流程监控大屏</p>
          <p class="np-code">该大屏展示全公司在办流程与办理时效，仅向财务管理人员与共享运营岗开放。</p>
          <p class="np-tip">
            如需查看，请联系系统管理员为当前账号开放相应岗位授权。
          </p>
        </template>
      </el-empty>
    </div>

    <template v-else>
    <!-- 顶部统计卡片：全部来自 /wf/perf/overview 的真实聚合，不再从"当前页"现算 -->
    <div class="stat-row">
      <div class="stat-card" v-for="s in stats" :key="s.label" :class="s.cls">
        <div class="stat-val">{{ s.value }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 超时处置档计数——节点超时配置改了档（催办/催满升级/自动通过），
         这张表按档给出"配置了多少节点、窗口内真发生了多少行动作、当前有多少在办落在该档"。
         纯只读聚合：本页不触发升级、不触发自动通过，也不改任何单据状态。 -->
    <div class="table-card tier-card">
      <div class="table-title">超时处置档计数（近 {{ tierDays || days }} 天）</div>
      <el-table v-if="tierLoaded" :data="tierRows" size="small" stripe>
        <el-table-column label="处置档" min-width="150">
          <template #default="{ row }">{{ row.label }}</template>
        </el-table-column>
        <el-table-column prop="nodes" label="配置节点数" width="110" />
        <el-table-column prop="withSla" label="其中配超时时长" width="140" />
        <el-table-column prop="fired" label="窗口内动作行数" width="140">
          <template #default="{ row }">
            <span :class="row.fired ? 'text-ok' : 'text-muted'">{{ row.fired }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="onHand" label="在办落该档" width="120" />
        <el-table-column label="其中卡单" width="100">
          <template #default="{ row }">
            <span :class="row.stuck > 0 ? 'text-danger' : ''">{{ row.stuck }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :image-size="50" :description="tierEmptyText" />
      <div class="perf-note">{{ tierNote }}</div>
    </div>

    <div class="work-tabs">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="流程全景" name="panorama">
          <div class="pane-toolbar">
            <span class="tb-label">统计窗口</span>
            <el-select v-model="days" size="small" style="width:132px" @change="onDaysChange">
              <el-option :value="7" label="近 7 天" />
              <el-option :value="30" label="近 30 天" />
              <el-option :value="90" label="近 90 天" />
            </el-select>
            <span class="tb-note">{{ pathNote || dwellNote || '' }}</span>
          </div>
          <div class="chart-row">
            <!-- 桑基图：实例真实走过的节点路径 -->
            <div class="chart-card">
              <div class="chart-title">单据流转路径（实例真实路径）
                <span class="chart-sub">标签截断＋避让，悬停看全名</span>
              </div>
              <!-- 画布高度随节点数长，节点多时不再把标签挤成一团 -->
              <div v-show="hasPaths" ref="sankeyRef" class="chart-body" :style="{ height: sankeyHeight + 'px' }"></div>
              <el-empty v-if="!hasPaths" :image-size="60" :description="pathsEmptyText" />
            </div>

            <!-- 节点热力图：真实节点名 × 实际停留分桶 -->
            <div class="chart-card">
              <div class="chart-title">节点停留热力图（按审批记录统计）
                <span class="chart-sub">行名截断，格内只标非 0</span>
              </div>
              <div v-show="hasDwell" ref="heatmapRef" class="chart-body" :style="{ height: heatHeight + 'px' }"></div>
              <el-empty v-if="!hasDwell" :image-size="60" :description="dwellEmptyText" />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="积压与时效" name="perf">
          <div class="pane-toolbar">
            <span class="tb-label">统计窗口</span>
            <el-select v-model="days" size="small" style="width:132px" @change="loadPerf">
              <el-option :value="7" label="近 7 天" />
              <el-option :value="30" label="近 30 天" />
              <el-option :value="90" label="近 90 天" />
            </el-select>
            <span class="tb-note">{{ backlogNote }}</span>
          </div>

          <div class="table-row">
            <div class="table-card">
              <div class="table-title">在途积压账龄（{{ backlog.pending || 0 }} 条待办）</div>
              <div v-for="b in backlog.buckets || []" :key="b.label" class="aging-row">
                <span class="aging-label">{{ b.label }}</span>
                <el-progress :percentage="pct(b.count, backlog.pending)" :stroke-width="12" :show-text="false" class="aging-bar" />
                <span class="aging-count">{{ b.count }}</span>
              </div>
              <el-divider content-position="left">堆在哪个节点</el-divider>
              <el-table :data="backlog.byNode || []" size="small" stripe max-height="240" :empty-text="emptyTextOf(perfFailed, '暂无在途积压')">
                <el-table-column prop="node" label="节点" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.node }}
                    <!-- 与时效榜同一处理——缺名记录如实标，不并成伪节点 -->
                    <el-tag v-if="row.unnamedTrace" size="small" type="info">记录缺名</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="count" label="在办" width="70" />
                <el-table-column label="已超时" width="90">
                  <template #default="{ row }">
                    <!-- 本列按 due_time 实算；行尾括注＝其中没有 due_time、走兜底规则的条数 -->
                    <span :class="row.overdue ? 'text-danger' : ''">{{ row.overdue }}</span>
                    <span v-if="row.overdueFallback" class="basis-mark">（†{{ row.overdueFallback }}）</span>
                  </template>
                </el-table-column>
                <el-table-column label="最长账龄" width="100">
                  <template #default="{ row }">{{ formatDuration(row.maxAgeHours * 60) }}</template>
                </el-table-column>
                <!-- 该节点配置的处置档 + 落在该档的在办数 + 催办/卡单规则 -->
                <el-table-column label="处置档" width="120">
                  <template #default="{ row }">{{ tierLabel(row.timeoutAction) }}</template>
                </el-table-column>
                <el-table-column prop="onLadderTier" label="催满升级档在办" width="130" />
                <el-table-column prop="onAutoPassTier" label="自动通过档在办" width="130" />
                <el-table-column label="累计催办" width="90">
                  <template #default="{ row }">{{ row.urgedTotal }} 次</template>
                </el-table-column>
                <el-table-column label="卡单" width="80">
                  <template #default="{ row }">
                    <span :class="row.stuck ? 'text-danger' : ''">{{ row.stuck }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div class="table-card">
              <div class="table-title">超时未办 Top 榜（{{ (backlog.overdueTop || []).length }} 条）</div>
              <el-table :data="backlog.overdueTop || []" size="small" stripe max-height="360" :empty-text="emptyTextOf(perfFailed, '窗口内无超时未办')">
                <el-table-column prop="name" label="责任人" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.name }}
                    <!-- RA-F3d：榜改为「一任务一行」，未指派的多候选人合并进同一行显示，条数＝待办数 -->
                    <el-tag v-if="row.assigneeId === null && row.candidateCount > 0" size="small" type="warning">候选人{{ row.candidateCount }}人</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="node" label="节点" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.node }}
                    <!-- 记录没写节点名的行如实标出来（后端有 key 显 key，全无则标缺名），不当真节点 -->
                    <el-tag v-if="row.unnamedTrace" size="small" type="info">记录缺名</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="bizNo" label="单据号" width="130" show-overflow-tooltip />
                <!-- 本列＝now − wf_task.due_time（到期真值），不再是「停留 − 配置 SLA」；带 1 位小数 -->
                <el-table-column label="已超时" width="110">
                  <template #default="{ row }">
                    <span class="text-danger">{{ row.overdueHours }}h</span>
                    <el-tooltip v-if="row.overdueBasis === 'CREATE_PLUS_SLA'" placement="top"
                                content="该单据没有到期时间，超时按「账龄 − 节点办理时限」推算得出（计算方式已在表下说明）">
                      <span class="basis-mark">†</span>
                    </el-tooltip>
                    <span v-else-if="row.overdueBasis === 'NO_DUE_NO_SLA'" class="text-muted">不可测</span>
                  </template>
                </el-table-column>
                <el-table-column label="催办" width="120">
                  <template #default="{ row }">{{ row.urgedCount }} 次
                    <!-- 卡单红牌：催满阈值仍无人办理＝派生标记，不代表系统已处置（不会自动改派/自动通过） -->
                    <el-tag v-if="row.stuck" size="small" type="danger">卡单待处置</el-tag>
                  </template>
                </el-table-column>
                <!-- 这一单所在节点配的处置档（催办/催满升级/自动通过），只读显示 -->
                <el-table-column label="处置档" width="110">
                  <template #default="{ row }">{{ tierLabel(row.timeoutAction) }}</template>
                </el-table-column>
              </el-table>
              <!-- 规则写 in 页面，不再让读者拿「停留」去反算「已超时」 -->
              <div class="perf-note">
                已超时＝现在 − 该单据的到期时间（与超时提醒、任务池看待的是同一个到期时点），
                不再用「停留时长 − 配置时限」推算（发起时间早于流程真正启动时，旧算法会虚报数十小时）。
                <template v-if="backlog.noDueTimeTasks">
                  本快照有 {{ backlog.noDueTimeTasks }} 条在办没有到期时间（行标 † 者），按「发起时间＋节点办理时限」推算；
                </template>
                <template v-if="backlog.noDueTimeTasks === 0">当前全部在办都有到期时间，没有一行需要按推算处理。</template>
              </div>
            </div>
          </div>

          <div class="table-card" style="margin-top:16px">
            <div class="table-title">办理人时效 Top（近 {{ days }} 天，办理人 {{ handlerTotal }} 位）
              <span v-if="handlerRecon" class="recon-hint">
                <!-- /两列不再是恒 0 的死列，并把与同页 KPI 的对账式并排给出 -->
                ｜屏上两列合计 在办 {{ handlerRecon.onHandCellSum }} · 超时 {{ handlerRecon.overdueOnHandCellSum }}
                ＝全部人名下 {{ handlerRecon.onHandAllHandlersSum }} · {{ handlerRecon.overdueOnHandAllHandlersSum }}
                ｜顶部卡片 待办 {{ handlerRecon.pendingTasks }} · 超时 {{ handlerRecon.overdueTasks }}
                （有人可办 {{ handlerRecon.ownedTasks }} ＋ 无人认领 {{ handlerRecon.unownedTasks }}）
                <template v-if="handlerRecon.appendedOnHandRows">· 表尾补 {{ handlerRecon.appendedOnHandRows }} 位仅在办者</template>
              </span>
            </div>
            <el-table :data="handlers" size="small" stripe :empty-text="emptyTextOf(perfFailed, '窗口内无办结记录')">
              <el-table-column label="办理人" min-width="150">
                <template #default="{ row }">
                  {{ row.name }}
                  <!-- 窗口内没办结、但名下有在办的人补在表尾（否则这一屏两列仍会看着是 0） -->
                  <el-tag v-if="row.onHandOnly" size="small" type="info">仅在办·补行</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="handled" label="窗口内办结" width="100" />
              <el-table-column label="平均停留" width="110">
                <template #default="{ row }">{{ formatDuration(row.avgMinutes) }}</template>
              </el-table-column>
              <el-table-column label="最长停留" width="110">
                <template #default="{ row }">{{ formatDuration(row.maxMinutes) }}</template>
              </el-table-column>
              <el-table-column label="办结超时" width="100">
                <template #default="{ row }">
                  <span :class="row.overdueHandled ? 'text-danger' : ''">{{ row.overdueHandled }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="onHand" label="当前在办" width="100" />
              <el-table-column label="在办超时" width="100">
                <template #default="{ row }">
                  <span :class="row.overdueOnHand ? 'text-danger' : ''">{{ row.overdueOnHand }}</span>
                </template>
              </el-table-column>
              <!-- 在办量按处置档拆开，改档后"谁的单子会被催满升级/自动通过"看得见 -->
              <el-table-column prop="onLadderTier" label="催满升级档在办" width="140" />
              <el-table-column prop="onAutoPassTier" label="自动通过档在办" width="140" />
            </el-table>
            <div class="perf-note">{{ handlerNote }}</div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="节点 SLA 明细" name="sla">
          <div class="table-card">
            <div class="table-title">节点命中与 SLA 达成（近 {{ days }} 天）</div>
            <el-table :data="dwellRows" size="small" stripe :empty-text="emptyTextOf(slaFailed, '窗口内无审批事件')">
              <el-table-column prop="node" label="节点" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.node }}
                  <!-- 这一行不是节点，是 wf_task_history 没写 node_name 的记录 -->
                  <el-tag v-if="row.unnamedTrace" size="small" type="info">记录缺名</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="events" label="审批事件" width="100" />
              <el-table-column label="平均停留" width="110">
                <template #default="{ row }">{{ formatDuration(row.avgMinutes) }}</template>
              </el-table-column>
              <el-table-column label="最长停留" width="110">
                <template #default="{ row }">{{ formatDuration(row.maxMinutes) }}</template>
              </el-table-column>
              <el-table-column label="配置 SLA" width="100">
                <template #default="{ row }">{{ row.timeoutHours ? row.timeoutHours + 'h' : '未配置' }}</template>
              </el-table-column>
              <el-table-column label="达成率" width="110">
                <template #default="{ row }">
                  <span v-if="row.slaRate === null" class="text-muted">不可测</span>
                  <span v-else :class="row.slaRate < 90 ? 'text-danger' : 'text-ok'">{{ row.slaRate }}%</span>
                </template>
              </el-table-column>
              <el-table-column prop="overdue" label="超时次数" width="100" />
              <!-- 改档可见的两组列——该节点配置的处置档 + 窗口内该档真发生的机器动作行数 -->
              <el-table-column label="处置档" width="130">
                <template #default="{ row }">
                  <el-tag v-if="!row.timeoutAction || row.timeoutAction === 'UNSPECIFIED'" size="small" type="info">未配置</el-tag>
                  <span v-else>{{ tierLabel(row.timeoutAction) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="urgeEvents" label="催办行数" width="100" />
              <el-table-column label="自动通过行数" width="120">
                <template #default="{ row }">
                  <span :class="row.autoPassEvents ? 'text-ok' : 'text-muted'">{{ row.autoPassEvents }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="escalateEvents" label="升级行数" width="100" />
            </el-table>
            <div class="perf-note">{{ dwellNote }}<template v-if="droppedNodes">（另有 {{ droppedNodes }} 个低命中节点未列入热力图）</template><template v-if="unnamedEvents">·本窗口 {{ unnamedEvents }} 条记录无节点名（{{ unnamedRows }} 行），已如实标注、未造名</template></div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 流程实例列表 -->
    <div class="table-card">
      <div class="table-title">在途流程实例（共 {{ listTotal }} 单）</div>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="pagedData" stripe size="small" style="width:100%" v-else :empty-text="emptyTextOf(listFailed, '当前没有正在办理中的流程单据')">
        <el-table-column prop="bizType" label="业务类型" width="120">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="单据号" width="160" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="currentNode" label="当前节点" width="120" show-overflow-tooltip />
        <el-table-column prop="currentAssigneeNames" label="处理人" width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="发起时间" width="160" :formatter="(row, col, val) => formatDateTime(val)" />
        <el-table-column label="停留时长" width="120">
          <template #default="{ row }">
            <span :class="row.timeout ? 'text-danger' : ''">{{ formatDuration(row.stayMinutes) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.timeout" type="danger" size="small">超时 {{ row.overdueHours }}h</el-tag>
            <el-tag v-else type="info" size="small">在办</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goBiz(row)">查看业务</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager :pager="pager" @change="fetchData" />
      <!-- 接口侧 WfQueryService.fillStay 已合入 due_time 优先规则（主窗移交合入完成），
           本页与上方两表同源；无 due_time 的实例双方同走「create_time+节点 SLA」推算兜底 -->
      <div class="perf-note">
        本表「停留时长 / 超时」的计算方式＝<b>按单据到期时间实算优先</b>，没有到期时间的才按节点配置的超时时长推算，与上方「超时未办 Top 榜」同源。
      </div>
    </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { SankeyChart, HeatmapChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { initThemedChart, onThemeChange, isDarkTheme } from '../../utils/echartsTheme'
import { flowApi } from '../../api'
import { hasPerm } from '@/utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { formatDateTime, formatMoney } from '@/utils/format'
import { bizTypeLabel } from '@/constants/enums.js'
import { goBizPage } from '@/utils/bizEntry'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

echarts.use([SankeyChart, HeatmapChart, TooltipComponent, GridComponent, VisualMapComponent, CanvasRenderer])

const router = useRouter()
const sankeyRef = ref(null)
const heatmapRef = ref(null)
const loading = ref(false)
const tab = ref('panorama')
const days = ref(30)

/** 页面级权限门控：无 wf:instance:list 则不请求、不轮询、不渲染图表 */
const canView = hasPerm('wf:instance:list')

// 在途实例列表分页。注意：图表统计一律走 /wf/perf/** 的全量聚合，不再从"当前页"切片现算
const pager = useTablePager({ mode: 'server', storageKey: 'flow_monitor' })
const { pagedData } = pager
let sankeyChart = null
let heatChart = null
let pollTimer = null
let offTheme = null

const listFailed = ref(false)
const overviewFailed = ref(false)
const chartsFailed = ref(false)
const perfFailed = ref(false)
const slaFailed = ref(false)
const overviewPending = ref(true)
const FAILED_TEXT = '统计数据本次未刷新成功，请稍后重试或切换统计窗口'

/** 空态文案：区分"确实没有"与"这次没取到"，两者都用业务语言 */
function emptyTextOf(noData, emptyText) {
  return noData ? FAILED_TEXT : emptyText
}
const overviewNoData = computed(() => overviewPending.value || overviewFailed.value)
const listTotal = computed(() => Number(pager.total.value) || 0)
const tierEmptyText = computed(() => emptyTextOf(t('monitor.flowMonitor.No2')))
const pathsEmptyText = computed(() => emptyTextOf(t('monitor.flowMonitor.No3')))
const dwellEmptyText = computed(() => emptyTextOf(t('monitor.flowMonitor.No4')))

const overview = ref({})
const paths = ref({ nodes: [], links: [] })
const dwellRows = ref([])
const dwellBuckets = ref([])
const backlog = ref({})
const handlers = ref([])
const handlerTotal = ref(0)
const pathNote = ref('')
const dwellNote = ref('')
const backlogNote = ref('')
const handlerNote = ref('')
/** /时效榜与同页 KPI 的对账数（后端 handler-top.reconcile） */
const handlerReconcile = ref(null)
// 门控从"两列有数"改成"有待办"——对账串的作用正是解释"为什么这两列是 0"，
// 列真有数时它也要在（否则读者只看得到 0，看不到 0 的理由）。
const handlerRecon = computed(() => {
  const r = handlerReconcile.value
  return r && (r.pendingTasks || 0) > 0 ? r : null
})
const droppedNodes = ref(0)
// 无名记录的规模（后端 node-dwell 的 unnamedNodeRows/Events），页面上如实交代
const unnamedEvents = ref(0)
const unnamedRows = ref(0)
// 超时处置档台账（后端 /wf/perf/{overview,paths,node-dwell,backlog} 同一份聚合，任一应答取用）
const tierLedger = ref({})
/** 台账对应的回看天数（由后端应答携带，避免卡片标题与窗口规则错配） */
const tierDays = ref(0)
const TIER_LABELS = {
  AUTO_REMIND: '催办提醒',
  ESCALATE_AFTER_URGES: '催满后升级',
  AUTO_PASS: '超时自动通过',
  ESCALATE: '直接升级/改派',
  UNSPECIFIED: '未配置',
}
/** 处置档码 → 中文（同节点跨模型可能并列多档，'/' 分隔） */
function tierLabel(code) {
  if (!code) return TIER_LABELS.UNSPECIFIED
  return String(code).split('/').map(c => TIER_LABELS[c.trim()] || c.trim()).join(' / ')
}

/** 三档计数行：配置节点数 / 配了超时时长 / 窗口内机器动作行数 / 在办落该档 / 其中卡单 */
const tierLoaded = computed(() => !!((tierLedger.value || {}).configuredNodes))
const tierInFlight = computed(() => (tierLedger.value || {}).inFlight || {})
const tierByTier = computed(() => tierInFlight.value.byTier || {})
/**
 * 对账：五档「在办落该档」合计 + 无配置行数 ＝ 同页 KPI 待办数
 * （两侧都是 wf_task PENDING 的同一份快照，逐档出数后这条恒等式在屏幕上可自查）。
 */
const tierCellSum = computed(() => Object.values(tierByTier.value).reduce((s, v) => s + ((v || {}).tasks || 0), 0))
const tierPendingTotal = computed(() => tierInFlight.value.pendingTasks ?? 0)
const tierUnclassified = computed(() => tierInFlight.value.unclassifiedTasks ?? 0)
/** 后端未按档出数（旧应答/降级）时的兼容读法：只有阶梯档与 AUTO_PASS 两档有具名键，其余返回 '-'（＝未出数，不是 0） */
function legacyTierCount(tier, infl, field) {
  if (tier === 'ESCALATE_AFTER_URGES') return infl[field === 'tasks' ? 'escalateAfterUrgesTasks' : 'escalateAfterUrgesStuck'] ?? '-'
  if (tier === 'AUTO_PASS') return infl[field === 'tasks' ? 'autoPassTasks' : 'autoPassStuck'] ?? '-'
  return '-'
}
const tierRows = computed(() => {
  const t = tierLedger.value || {}
  const cfg = t.configuredNodes || {}
  const sla = t.configuredNodesWithSla || {}
  const fired = t.firedEvents || {}
  const byTier = tierByTier.value
  const infl = tierInFlight.value
  const row = (tier, label, firedKeys) => {
    const on = byTier[tier]
    return {
      tier,
      label,
      nodes: cfg[tier] ?? 0,
      withSla: sla[tier] ?? 0,
      fired: firedKeys.reduce((s, k) => s + (fired[k] ?? 0), 0),
      // 五档同一规则出数；仅在后端没有 byTier 时才回退旧的两个具名键，再退不到才是 '-'
      onHand: on ? on.tasks : legacyTierCount(tier, infl, 'tasks'),
      stuck: on ? on.stuck : legacyTierCount(tier, infl, 'stuck'),
    }
  }
  return [
    row('AUTO_REMIND', '催办提醒', ['URGE']),
    row('ESCALATE_AFTER_URGES', '催满后升级', ['ESCALATE']),
    row('AUTO_PASS', '超时自动通过', ['AUTO_PASS', 'AUTO_SKIP']),
    row('ESCALATE', '直接升级/改派', ['ESCALATE', 'TIMEOUT_RELEASE']),
    row('UNSPECIFIED', '未配置/不可识别', []),
  ]
})
const tierNote = computed(() => {
  const infl = tierInFlight.value
  const recon = tierPendingTotal.value
    ? `对账：五档在办合计 ${tierCellSum.value} + 无配置行 ${tierUnclassified.value} ＝ 待办任务 ${tierPendingTotal.value}（＝顶部卡片，同一份待办快照）。`
    : ''
  return '统计方式：配置节点数＝按节点超时处置配置归类后的节点计数（改档后此处即时可见）；'
    + '动作行数＝窗口内审批记录里的系统自动动作次数（催满后升级与直接升级同记为升级动作，两档合并计入）；'
    + '卡单＝已催办满 ' + (infl.stuckAfterUrges ?? '-') + ' 次仍在办（催办提醒阈值与升级阈值可各自配置、默认同为 3 次，故本列≠已升级）。'
    + recon
    + '本表只数不改：不触发升级、不触发自动通过。'
})

function pickTierLedger(...payloads) {
  for (const p of payloads) {
    if (p && p.timeoutActions) {
      tierLedger.value = p.timeoutActions
      // 台账自带窗口天数（积压档的机器动作窗口固定 30 天），卡片标题跟着台账走，避免规则错标
      if (p.days) tierDays.value = p.days
      return
    }
  }
}

function statNum(val, noData) {
  if (val === undefined || val === null) return noData ? '-' : 0
  return val
}

const stats = computed(() => [
  { label: '在途流程实例', value: statNum(overview.value.runningInstances, overviewNoData.value), cls: 'blue' },
  { label: '已完成流程实例', value: statNum(overview.value.approvedInstances, overviewNoData.value), cls: 'green' },
  { label: '已取消流程实例', value: statNum(overview.value.cancelledInstances, overviewNoData.value), cls: 'yellow' },
  { label: '待办任务', value: statNum(overview.value.pendingTasks, overviewNoData.value), cls: 'blue' },
  { label: '今日办结', value: statNum(overview.value.finishedToday, overviewNoData.value), cls: 'green' },
  { label: '超时未办', value: statNum(overview.value.overdueInFlight, overviewNoData.value), cls: 'red' },
  { label: 'SLA 达成率', value: overview.value.slaRate == null ? (overviewNoData.value ? '-' : '不可测') : overview.value.slaRate + '%', cls: 'purple' },
  { label: '平均节点停留', value: overviewNoData.value && overview.value.avgNodeMinutes == null ? '-' : formatDuration(overview.value.avgNodeMinutes), cls: 'yellow' },
])

const hasPaths = computed(() => (paths.value.links || []).length > 0)
const hasDwell = computed(() => dwellRows.value.length > 0)

/**
 * 画布高度随内容长。桑基每节点留 ~26px、热力每行 ~34px，
 * 否则 20+ 个节点名挤在固定 300px 里必然互相叠压；上限 640 防撑爆页面。
 */
const sankeyHeight = computed(() => Math.min(640, Math.max(300, ((paths.value.nodes || []).length) * 26 + 70)))
const heatHeight = computed(() => Math.min(640, Math.max(300, dwellRows.value.length * 34 + 120)))

/** 节点标签截断（全名交给 tooltip），「账龄催收派单作业【催收专员】」这类长名不再压住相邻标签 */
function shortNodeLabel(name, max = 12) {
  const s = String(name ?? '')
  return s.length <= max ? s : s.slice(0, max) + '…'
}

function pct(v, total) {
  if (!total) return 0
  return Math.round(((v || 0) / total) * 100)
}

// 停留时长格式化：分钟 → "x分钟" / "x.x小时"（与 BillTraceDrawer 保持一致）
function formatDuration(min) {
  if (!min && min !== 0) return '-'
  if (min < 60) return min + '分钟'
  return (min / 60).toFixed(1) + '小时'
}

async function loadOverview() {
  if (!canView) return
  try {
    const ov = await flowApi.perfOverview(days.value, { silent: true })
    if (ov) {
      overview.value = ov
      pickTierLedger(ov)
      overviewFailed.value = false
    } else {
      overviewFailed.value = true
    }
  } catch {
    overviewFailed.value = true
  } finally {
    overviewPending.value = false
  }
}

async function fetchData() {
  // 无权限直接返回：不发请求、不弹提示（入口已隐藏，此处防直达 URL 与权限变更）
  if (!canView) return
  loading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      // silent：本页每 30 秒轮询一次，若因权限/网络报错会变成"每 30 秒弹一次"的持续骚扰，
      // 而 try/catch 吞不掉 —— 提示来自 api.js 响应拦截器的全局 toastBizError，只有 silent 能抑制。
      // status=RUNNING：这一节标题是"在途流程实例"，不传状态会把已办结/驳回的单子也列进来，
      // 于是"在途"数与顶部卡片对不上，且已办结单永远显示"停留 -、状态 正常"。
      const data = await flowApi.instancePage({ pageNum, pageSize, status: 'RUNNING' }, { silent: true })
      const list = data?.records || data?.list || []
      return { list, total: data?.total || list.length }
    })
    listFailed.value = false
  } catch {
    listFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 图表数据（桑基 + 热力）：只在 全景 Tab 与窗口切换时取 */
async function loadCharts() {
  if (!canView) return
  try {
    const [p, d] = await Promise.all([
      flowApi.perfPaths(days.value, { silent: true }),
      flowApi.perfNodeDwell(days.value, { silent: true }),
    ])
    paths.value = p || { nodes: [], links: [] }
    pathNote.value = p?.note || ''
    dwellRows.value = d?.rows || []
    dwellBuckets.value = d?.buckets || []
    dwellNote.value = d?.note || ''
    droppedNodes.value = d?.droppedNodes || 0
    // 无名记录规模（SLA 明细档的表下说明用）
    unnamedEvents.value = d?.unnamedNodeEvents || 0
    unnamedRows.value = d?.unnamedNodeRows || 0
    pickTierLedger(d, p)
    chartsFailed.value = false
  } catch {
    chartsFailed.value = true
  }
  await nextTick()
  renderSankey()
  renderHeatmap()
}

async function loadSla() {
  if (!canView) return
  try {
    const d = await flowApi.perfNodeDwell(days.value, { silent: true })
    dwellRows.value = d?.rows || []
    dwellBuckets.value = d?.buckets || []
    dwellNote.value = d?.note || ''
    droppedNodes.value = d?.droppedNodes || 0
    unnamedEvents.value = d?.unnamedNodeEvents || 0
    unnamedRows.value = d?.unnamedNodeRows || 0
    pickTierLedger(d)
    slaFailed.value = false
  } catch {
    slaFailed.value = true
  }
}

/** 绩效 Tab 数据：账龄 + 超时未办榜 + 按人时效 */
async function loadPerf() {
  if (!canView) return
  let b = null
  try {
    const [bb, h] = await Promise.all([
      flowApi.perfBacklog({ silent: true }),
      flowApi.perfHandlerTop(days.value, { silent: true }),
    ])
    b = bb
    backlog.value = b || {}
    backlogNote.value = b?.note || ''
    handlers.value = h?.rows || []
    handlerTotal.value = h?.measured || 0
    handlerNote.value = h?.note || ''
    handlerReconcile.value = h?.reconcile || null
    perfFailed.value = false
  } catch {
    perfFailed.value = true
  }
  // 概览档的台账与页面窗口同一规则（积压档固定 30 天），只在概览没取到时退回用积压档的
  await loadOverview()
  pickTierLedger(overview.value, b)
}

function onDaysChange() {
  loadCharts()
  loadOverview()
}

function onTabChange(name) {
  if (name === 'panorama') loadCharts()
  else if (name === 'sla') loadSla()
  else loadPerf()
  nextTick(() => { sankeyChart?.resize(); heatChart?.resize() })
}

/** 桑基：节点＝真实 nodeName，连线值＝窗口内该转移的真实发生次数 */
function renderSankey() {
  if (!sankeyRef.value || !hasPaths.value) return
  if (!sankeyChart) sankeyChart = initThemedChart(sankeyRef.value)
  else sankeyChart.resize() // 容器高度随节点数变过，画布要先跟上再出图
  const hits = {}
  ;(paths.value.nodes || []).forEach(n => { hits[n.name] = n.hits })
  const nodes = (paths.value.links || []).reduce((acc, l) => {
    ;[l.source, l.target].forEach(n => { if (!acc.includes(n)) acc.push(n) })
    return acc
  }, []).map(name => ({ name, realValue: hits[name] || 0 }))
  sankeyChart.setOption({
    tooltip: {
      trigger: 'item',
      // 标签截断后，全名与经过实例数都在这里给回
      formatter: (p) => p.dataType === 'edge'
        ? `${p.data.source} → ${p.data.target}<br/>${p.data.value} 笔单据`
        : `${p.name}<br/>经过 ${hits[p.name] || 0} 个实例`,
    },
    series: [{
      type: 'sankey', data: nodes, links: paths.value.links,
      orient: 'horizontal', left: 10, right: 96,
      nodeWidth: 14, nodeGap: 14,
      lineStyle: { color: 'gradient', opacity: 0.3 },
      itemStyle: { borderWidth: 0 },
      // 标签截断到 12 字，并开 hideOverlap 让 ECharts 主动隐去避让不开的相邻标签
      // （叠加高度自适应，节点再多也只会出现"少几个标签"，不会出现整片文字压字）
      label: {
        fontSize: 11,
        formatter: (p) => `${shortNodeLabel(p.name)}(${hits[p.name] || 0})`,
        width: 92, overflow: 'truncate', ellipsis: '…',
      },
      labelLayout: { hideOverlap: true },
      emphasis: { focus: 'adjacency' },
    }],
  }, true)
}

/** 热力：行＝真实节点（命中数降序 Top12），列＝实际停留分桶，格值＝审批事件数 */
function renderHeatmap() {
  if (!heatmapRef.value || !hasDwell.value) return
  if (!heatChart) heatChart = initThemedChart(heatmapRef.value)
  else heatChart.resize() // 同上，行数变化后先跟上容器高度
  const nodes = dwellRows.value.map(r => r.node).reverse()
  const buckets = dwellBuckets.value.length ? dwellBuckets.value : ['<1小时', '1-4小时', '4-8小时', '8-24小时', '>24小时']
  const data = []
  let maxVal = 1
  dwellRows.value.forEach((r, rowIdx) => {
    const y = nodes.length - 1 - rowIdx
    ;(r.buckets || []).forEach((v, x) => {
      data.push([x, y, v])
      if (v > maxVal) maxVal = v
    })
  })
  heatChart.setOption({
    // 行名同样给全（截断只发生在轴标签上）
    tooltip: { position: 'top', formatter: (p) => `${nodes[p.value[1]]}<br/>${buckets[p.value[0]]}：${p.value[2]} 次` },
    grid: { left: 150, right: 20, top: 24, bottom: 56 },
    xAxis: { type: 'category', data: buckets, splitArea: { show: true }, axisLabel: { fontSize: 10, hideOverlap: true, interval: 0 } },
    // 宽度截断＋ellipsis＋hideOverlap，长节点名不再溢出到格子上互相压字
    yAxis: {
      type: 'category', data: nodes, splitArea: { show: true },
      axisLabel: { fontSize: 10, width: 140, overflow: 'truncate', ellipsis: '…', hideOverlap: true },
    },
    visualMap: {
      min: 0, max: maxVal, calculable: true,
      orient: 'horizontal', left: 'center', bottom: 0,
      // 深色下浅色阶会整片发白，换成同序的深底低亮色阶
      inRange: {
        color: isDarkTheme()
          ? ['#1e2b4a', '#27476f', '#356a9e', '#5c93c8', '#d73027']
          : ['#e0f3f8', '#abd9e9', '#74add1', '#4575b4', '#d73027'],
      },
    },
    series: [{
      type: 'heatmap', data: data,
      // 格内只标非 0（0 满屏时数字互相压字且没有信息量）
      label: { show: true, fontSize: 10, formatter: (p) => (p.value[2] > 0 ? p.value[2] : '') },
      emphasis: { itemStyle: { shadowBlur: 10 } },
    }],
  }, true)
}

/** 一键跳转到单据对应业务处理页（复用 bizEntry 分发器） */
function goBiz(row) {
  if (!goBizPage(router, row)) {
    ElMessage.warning(t('SearchCommand.main.Current'))
  }
}

onMounted(async () => {
  await nextTick()
  // 无权限时不请求、不启动轮询（否则无条件 fetch + setInterval 会每 30 秒弹一次错误）
  if (canView) {
    fetchData()
    loadOverview()
    loadCharts()
    pollTimer = setInterval(() => {
      fetchData()
      loadOverview()
    }, 30000)
  }
  window.addEventListener('resize', onResize)
  offTheme = onThemeChange(() => {
    sankeyChart?.dispose(); sankeyChart = null
    heatChart?.dispose(); heatChart = null
    nextTick(() => { renderSankey(); renderHeatmap() })
  })
})
onBeforeUnmount(() => {
  clearInterval(pollTimer)
  window.removeEventListener('resize', onResize)
  offTheme && offTheme()
  sankeyChart?.dispose()
  heatChart?.dispose()
})
function onResize() {
  sankeyChart?.resize()
  heatChart?.resize()
}
</script>

<style scoped>
.flow-monitor { padding: 16px; }
.no-perm { padding: 60px 20px; background: var(--app-card-bg, #fff); border-radius: 8px; }
.no-perm .np-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); margin: 0 0 6px; }
.no-perm .np-code { font-size: 13px; color: var(--app-text-secondary, #606266); margin: 0 0 10px; }
.no-perm .np-code code {
  background: var(--app-hover, #f4f4f5); padding: 1px 6px; border-radius: 3px;
  font-family: Consolas, Monaco, monospace; color: #c0392b;
}
.no-perm .np-tip { font-size: 12.5px; color: #909399; line-height: 1.9; margin: 0; }
.stat-row { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }
.stat-card {
  flex: 1 1 150px; background: var(--app-card-bg, #fff); border-radius: 8px; padding: 16px;
  border-left: 4px solid #dcdfe6; text-align: center;
}
.stat-card.blue { border-color: #409eff; }
.stat-card.green { border-color: #67c23a; }
.stat-card.red { border-color: #f56c6c; }
.stat-card.yellow { border-color: #e6a23c; }
.stat-card.purple { border-color: #7c5cff; }
.stat-val { font-size: 24px; font-weight: 700; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.work-tabs { background: var(--app-card-bg, #fff); border-radius: 8px; padding: 4px 12px 12px; margin-bottom: 16px; }
.tier-card { padding: 12px 12px 6px; margin-bottom: 16px; border: 1px solid var(--app-border, #f0f0f0); }
.tier-card .tier-code { margin-left: 6px; font-size: 11px; color: #c0c4cc; font-family: Consolas, Monaco, monospace; }
.pane-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.pane-toolbar .tb-label { font-size: 12px; color: var(--app-text-secondary, #606266); }
.pane-toolbar .tb-note { font-size: 12px; color: #909399; }
.chart-row { display: flex; gap: 16px; }
.chart-card { flex: 1; border: 1px solid var(--app-border, #f0f0f0); border-radius: 8px; padding: 12px; }
.chart-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.chart-title .chart-sub { margin-left: 8px; font-size: 11px; font-weight: 400; color: #c0c4cc; }
.chart-body { height: 300px; }
/* 兜底规则的行内标注（†），与表下的规则说明成对出现 */
.basis-mark { margin-left: 2px; color: #e6a23c; font-weight: 600; font-size: 11px; }
.perf-note code {
  background: var(--app-hover, #f4f4f5); padding: 0 4px; border-radius: 3px;
  font-family: Consolas, Monaco, monospace; font-size: 11.5px; color: var(--app-text-secondary, #606266);
}
.table-row { display: flex; gap: 16px; }
.table-row .table-card { flex: 1; }
.table-card { background: var(--app-card-bg, #fff); border-radius: 8px; }
.table-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.recon-hint { font-size: 12px; font-weight: 400; color: #909399; margin-left: 4px; }
.aging-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.aging-label { width: 60px; font-size: 12px; color: var(--app-text-secondary, #606266); }
.aging-bar { flex: 1; }
.aging-count { width: 40px; text-align: right; font-size: 12px; }
.perf-note { font-size: 12px; color: #909399; margin-top: 8px; line-height: 1.7; }
.text-danger { color: #f56c6c; font-weight: 600; }
.text-ok { color: #67c23a; font-weight: 600; }
.text-muted { color: #c0c4cc; }
</style>
