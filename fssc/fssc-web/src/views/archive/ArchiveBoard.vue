<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <!-- 单页模式（菜单/工作区传入 tab）由宿主给标题，卡头不再重复一遍「档案管理」 -->
    <template v-if="!isSingle" #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ $t('archive.main.Archive') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('archive.main.Archiving') }}</span>
        <el-button type="info" size="small" style="margin-left:auto" @click="reload">{{ $t('common.refresh') }}</el-button>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- 借阅利用 -->
      <el-tab-pane label="借阅利用" name="access">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button v-if="canRequestAccess" type="success" @click="openAccess">发起调阅申请</el-button>
          <el-button type="info" plain @click="loadAccess()">刷新列表</el-button>
          <!-- 调阅记录原本只出 PENDING，已批准/已驳回的历史行界面永不可见（库里 3 行只见 1 行）。
               端点已加 status 入参，这里默认「全部状态」把记录摊开；审批动作仍只对 PENDING 行给入口。 -->
          <el-select v-model="accessStatusFilter" size="small" style="width:130px" @change="loadAccess">
            <el-option label="全部状态" value="ALL" />
            <el-option label="仅待审批" value="PENDING" />
            <el-option label="仅已批准" value="APPROVED" />
            <el-option label="仅已驳回" value="REJECTED" />
          </el-select>
          <!-- 申请人一律以姓名为准，工号列默认隐藏、可勾出（对齐外部系统"内码列默认隐藏"的列语义） -->
          <el-checkbox v-model="showUserNameCols" size="small" label="显示工号列" />
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table class="cm-target-board" :data="pagedAccess" size="small" border stripe v-else empty-text="暂无数据" v-loading="loading" @row-dblclick="openTrace">
          <!-- 本列的数据源只有裸 archive_id，靠"当前 tab 恰好加载过的那页档案"
               本地 join 会恒退化成 #ID ⇒ 档案编码列空相。故走档案目录回落（archiveLabel→findArchiveRow），
               出的是真 archive_no（号在 archive_no 列，冗余列 archive_code 只是它的镜像）。 -->
          <el-table-column label="档案号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ archiveLabel(row.archiveId) }}</template>
          </el-table-column>
          <el-table-column label="调阅类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="accessTypeTag(row.accessType)">{{ accessTypeLabel(row.accessType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="accessReason" label="申请理由" min-width="200" show-overflow-tooltip  sortable />
          <el-table-column label="申请人" width="110">
            <template #default="{ row }">{{ personLabel(row.requesterName, row.requesterId) }}</template>
          </el-table-column>
          <el-table-column v-if="showUserNameCols" label="工号" width="100">
            <template #default="{ row }">{{ row.requesterUserName || userLabel(row.requesterId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="accessTag(row.approvalStatus)" size="small">{{ accessStatusLabel(row.approvalStatus) }}</el-tag>
            </template>
          </el-table-column>
          <!-- 授权时间：批准写 approved_at、实际取阅写 accessed_at，取先发生的那个（后端无 grantTime 字段） -->
          <el-table-column label="授权时间" width="160" sortable>
            <template #default="{ row }">{{ formatDateTime(row.approvedAt || row.accessedAt) || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <!-- 审批动作统一走下方「批准 / 驳回」（显式传 decision、按 PENDING 判定）。
                 InlineActions 上不能再挂一份 approve/reject：不传 decision 会让点通过=驳回。
                 该组件的 showApprove/showReject 默认为 true，故必须显式关掉，
                 否则图标仍在＝点了没有处理器的死按钮，且对无 compliance:archiveaccess:approve 的人构成假入口。 -->
            <template #default="{ row }">
              <InlineActions :visible="true" :show-approve="false" :show-reject="false" @view="viewAccess(row)" :show-trace="true" @trace="openTrace(row)" />
              <el-button v-if="canApproveAccess && row.approvalStatus === 'PENDING'" link size="small" type="primary" @click="approveAccess(row, true)">批准</el-button>
              <el-button v-if="canApproveAccess && row.approvalStatus === 'PENDING'" link size="small" type="danger" @click="approveAccess(row, false)">驳回</el-button>
              <!-- 批准过后才谈得上授予访问；已实际取阅（accessed_at 落值）则不再给入口 -->
              <el-button v-if="canApproveAccess && row.approvalStatus === BillStatus.APPROVED && !row.accessedAt" link size="small" type="warning" @click="grantAccess(row)">授予</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerAccess" @change="loadAccess" />
      </el-tab-pane>

      <!-- 鉴定销毁 -->
      <el-tab-pane label="鉴定销毁" name="destroy">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button v-if="canCreateDestroy" type="success" @click="openDestroy">发起销毁申请</el-button>
          <el-button type="info" plain @click="loadDestroy">刷新列表</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedDestroy" size="small" border stripe v-else empty-text="暂无数据" v-loading="loading">
          <el-table-column prop="id" label="申请编号" width="90"  sortable />
          <el-table-column label="销毁档案" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <div v-if="archiveNamesFromIds(row.archiveIds).length">
                <el-tag v-for="name in archiveNamesFromIds(row.archiveIds)" :key="name" size="small" style="margin:2px">{{ name }}</el-tag>
              </div>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="destroyReason" label="销毁理由" min-width="200" show-overflow-tooltip  sortable />
          <el-table-column label="申请人" width="110">
            <template #default="{ row }">{{ personLabel(row.applicantName, row.applicantId) }}</template>
          </el-table-column>
          <el-table-column v-if="showUserNameCols" label="工号" width="100">
            <template #default="{ row }">{{ row.applicantUserName || userLabel(row.applicantId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="destroyTag(row.approvalStatus)" size="small">{{ destroyStatusLabel(row.approvalStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="已销毁" width="90" align="center">
            <template #default="{ row }">
              <!-- 后端无 executed 布尔列，销毁动作只落 destroyed_at -->
              <el-tag :type="row.destroyedAt ? 'danger' : 'info'" size="small">{{ row.destroyedAt ? '已执行' : '未执行' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="canApproveDestroy && (row.approvalStatus === 'PENDING' || row.approvalStatus == null)" link size="small" type="primary" @click="approveDestroy(row, true)">批准</el-button>
              <el-button v-if="canApproveDestroy && (row.approvalStatus === 'PENDING' || row.approvalStatus == null)" link size="small" type="danger" @click="approveDestroy(row, false)">驳回</el-button>
                <el-button v-if="canExecuteDestroy && row.approvalStatus === BillStatus.APPROVED && !row.destroyedAt" link size="small" type="warning" :loading="busy('destroy-exec-' + row.id)" @click="executeDestroy(row)">执行销毁</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerDestroy" @change="loadDestroy" />
      </el-tab-pane>

      <!-- 单据流转 -->
      <el-tab-pane label="单据流转" name="dispatch">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button v-if="canRunDispatch" type="success" @click="openDispatch">发起单据流转</el-button>
          <el-button type="info" plain @click="loadDispatch">刷新</el-button>
      <el-button type="info" size="small" @click="exportCsv">导出</el-button>
          <span style="color:#909399; font-size:12px">派单 -> 审核组 -> 完成/升级</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedDispatch" size="small" border stripe v-else max-height="420" empty-text="暂无数据" v-loading="loading">
          <el-table-column prop="id" label="编号" width="80"  sortable />
          <el-table-column label="单据标识" min-width="140" show-overflow-tooltip  sortable>
            <template #default="{ row }">{{ billRefText(row) }}</template>
          </el-table-column>
          <el-table-column label="单据类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ billTypeLabel(row.billType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expenseType" label="费项" min-width="120" show-overflow-tooltip  sortable />
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="reviewerName" label="审核员" width="110"  sortable />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="dispTag(row.status)" size="small">{{ dispStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="taskCode" label="任务编码" width="160"  sortable />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="canRunDispatch && row.status !== 'COMPLETED' && row.status !== 'ESCALATED'" link size="small" type="primary" @click="completeDispatch(row)">完成</el-button>
                <el-button v-if="canRunDispatch && row.status !== 'COMPLETED' && row.status !== 'ESCALATED'" link size="small" type="warning" @click="escalateDispatch(row)">升级</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerDispatch" @change="loadDispatch" />
      </el-tab-pane>

      <!-- 归档整理（scope=filing）／档案库（scope=library） -->
      <el-tab-pane label="归档整理" name="archive">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button v-if="canEditVolume && !isLibrary" type="success" @click="openArchive">新建案卷</el-button>
          <el-button type="info" plain @click="reload">刷新</el-button>
          <span style="color:#909399; font-size:12px">{{ isLibrary ? '已归档案卷：目录检索 → 卷内目录 → 查看卷内文件／完整性校验' : '待立卷候选 → 组卷（一卷多件）→ 执行归档 → 审批定稿' }}</span>
        </div>
        <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:10px">
          <el-input v-model="archQ.keyword" placeholder="档案编号 / 关联单号 / 题名" clearable style="width:220px" @keyup.enter="archSearch" />
          <el-select v-if="!isLibrary" v-model="archQ.status" size="small" style="width:170px" @change="archSearch">
            <el-option label="在办（待归档/归档中）" value="PENDING,ARCHIVING" />
            <el-option label="全部状态" value="ALL" />
            <el-option label="待归档" value="PENDING" />
            <el-option label="归档中" value="ARCHIVING" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
          <el-button type="primary" plain size="small" @click="archSearch">查询</el-button>
          <el-button size="small" @click="exportArchiveCsv">导出目录</el-button>
        </div>
        <!-- 先勾一批单据，再新建案卷（清单与「共享运营›档案归档」同一取数） -->
        <div v-if="canListPending && !isLibrary" style="margin-bottom:12px">
          <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:6px">
            <b style="font-size:13px">待立卷候选（{{ candTotal }}）</b>
            <el-button type="primary" size="small" :disabled="!candSelected.length" @click="openArchiveBatch">
              按所选新建案卷（{{ candSelected.length }}）
            </el-button>
            <el-button size="small" type="info" plain @click="loadCandidates">刷新清单</el-button>
            <span style="color:#909399; font-size:12px">勾选后案卷要素填一次，所选单据一次性挂进<b>同一个案卷</b>、各成一件（勾选顺序即件号）；同一张单据不能重复入卷，服务端会拒绝并报出它已在的卷号</span>
          </div>
          <el-table :data="candidates" size="small" border stripe max-height="240"
                    empty-text="暂无待立卷单据（取数＝已审批完成且影像齐全的单据；实物签收在建卷/归档时校验，未签收的会被服务端拦下并报出单号）" v-loading="candLoading"
                    @selection-change="(s) => (candSelected = s)">
            <el-table-column type="selection" width="40" />
            <el-table-column prop="billNo" label="单据编号" width="150" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="影像" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.imageStatus === 'COMPLETE' ? 'success' : 'warning'">{{ row.imageStatus === 'COMPLETE' ? '完整' : '待补' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerCandidate" @change="loadCandidates" />
        </div>
        <span v-else-if="!isLibrary" style="display:block; color:#909399; font-size:12px; margin-bottom:10px">
          本岗无待归档清单读取权（op:archive:list），故此处不列候选单据；【新建案卷】仍可从「我的单据」中选单入卷。
        </span>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table ref="archiveTable" :data="pagedArchive" size="small" border stripe v-else max-height="420" empty-text="暂无数据"
                  v-loading="loading" row-key="id" @expand-change="onVolumeExpand">
          <!-- 展开行＝卷内目录（案卷→件→页的中间一层），展开时按需拉取 -->
          <el-table-column type="expand">
            <template #default="{ row }">
              <div style="padding:8px 14px 12px; background:var(--app-hover, #fafcff)">
                <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:6px">
                  <b style="font-size:13px">卷内目录（{{ (volumeItems[row.id] || []).length }} 件）</b>
                  <el-tag v-if="!(canEditVolume && !isLibrary && !volumeLocked(row))" size="small" effect="plain"
                          :type="row.carrierType === 'ELECTRONIC' ? 'info' : ''">
                    {{ row.carrierType === 'ELECTRONIC' ? '电子载体' : '纸质载体·须编页' }}
                  </el-tag>
                  <!-- 待归档卷可直接订正载体（建卷选错了不必求 SQL）；审批在途/已定稿只读 -->
                  <el-select v-else :model-value="row.carrierType || 'PAPER'" size="small" style="width:172px"
                             :loading="busy('archive-carrier-' + row.id)"
                             @change="(v) => changeCarrier(row, v)">
                    <el-option label="纸质载体·须编页" value="PAPER" />
                    <el-option label="电子载体·不强制编页" value="ELECTRONIC" />
                  </el-select>
                  <el-button v-if="canEditVolume && !isLibrary && !volumeLocked(row)" type="primary" size="small"
                             :loading="busy('archive-item-add-' + row.id)" @click="openAppendItems(row)">追加件</el-button>
                  <el-button size="small" type="info" plain @click="loadVolumeItems(row.id)">刷新</el-button>
                  <span v-if="volumeLocked(row)" style="color:#e6a23c; font-size:12px">已定稿/审批在途，卷内件清单不可变更</span>
                  <span v-else-if="!canEditVolume" style="color:#909399; font-size:12px">本岗无案卷维护权（compliance:archive:create），卷内目录仅供查阅</span>
                </div>
                <el-table :data="volumeItems[row.id] || []" size="small" border max-height="260"
                          v-loading="!!volumeItemsLoading[row.id]"
                          :empty-text="volumeLocked(row) ? '本卷暂无件（卷内件清单已定稿，不可变更）' : '本卷暂无件，可点【追加件】或到上方「待立卷候选」勾单'">
                  <el-table-column prop="seqNo" label="件号" width="70" align="center" />
                  <el-table-column prop="billNo" label="单据编号" min-width="150" show-overflow-tooltip>
                    <template #default="{ row: it }">{{ it.billNo || '—' }}</template>
                  </el-table-column>
                  <el-table-column prop="voucherNo" label="凭证号" width="140" show-overflow-tooltip>
                    <template #default="{ row: it }">{{ it.voucherNo || '—' }}</template>
                  </el-table-column>
                  <el-table-column prop="title" label="题名" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: it }">{{ it.title || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="日期" width="110" align="center">
                    <template #default="{ row: it }">{{ formatDate(it.itemDate) || '—' }}</template>
                  </el-table-column>
                  <!-- 编页：纸质卷装订后「第几页」是核对实物的锚点；可改窗口内直接在目录里编 -->
                  <el-table-column label="起页" width="96" align="center">
                    <template #default="{ row: it }">
                      <el-input-number v-if="canEditVolume && !isLibrary && !volumeLocked(row)" v-model="it.pageFrom"
                                       :min="1" :controls="false" size="small" style="width: 72px" placeholder="—" />
                      <span v-else>{{ it.pageFrom == null ? '—' : it.pageFrom }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="页数" width="96" align="center">
                    <template #default="{ row: it }">
                      <el-input-number v-if="canEditVolume && !isLibrary && !volumeLocked(row)" v-model="it.pageCount"
                                       :min="1" :controls="false" size="small" style="width: 72px" placeholder="—" />
                      <span v-else>{{ it.pageCount == null ? '—' : it.pageCount }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="文件数" width="80" align="center">
                    <template #default="{ row: it }">{{ it.fileCount == null ? '—' : it.fileCount }}</template>
                  </el-table-column>
                  <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
                    <template #default="{ row: it }">{{ it.remark || '—' }}</template>
                  </el-table-column>
                  <el-table-column v-if="canEditVolume && !isLibrary && !volumeLocked(row)" label="操作" width="170" align="center">
                    <template #default="{ row: it }">
                      <el-button link size="small" type="primary" :loading="busy('archive-item-pages-' + it.id)"
                                 @click="saveItemPages(row, it)">保存编页</el-button>
                      <el-button link size="small" type="danger" :loading="busy('archive-item-remove-' + it.id)"
                                 @click="removeVolumeItem(row, it)">移出卷</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <!-- 说明：本列绑 archiveNo（op_archive.archive_no，DDL NOT NULL、取号器唯一），
               不绑冗余列 archiveCode——该列在 184 的"编号镜像"上线前整列 NULL，绑它就是"档案编码列全空"。 -->
          <el-table-column prop="archiveNo" label="档案号" min-width="150" show-overflow-tooltip  sortable />
          <!-- 题名＝档案目录的必备著录项（DA/T 22 归档文件目录），卷级列表原缺此列，档案库只能靠编号认卷 -->
          <el-table-column prop="title" label="题名" min-width="180" show-overflow-tooltip />
          <el-table-column label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="archiveTypeTag(row.archiveType)">{{ archiveTypeLabel(row.archiveType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="载体" width="92" align="center">
            <!-- 纸质卷归档前须逐件编页，电子卷不要求——载体是本页"能不能直接归档"的判据来源，
                 列表直接给出，免得点【执行归档】才吃 400 -->
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.carrierType === 'ELECTRONIC' ? 'info' : ''">
                {{ row.carrierType === 'ELECTRONIC' ? '电子' : '纸质' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="archiveYear" label="年度" width="80" align="center"  sortable />
          <el-table-column prop="archivePeriod" label="期间" width="90" align="center"  sortable />
          <el-table-column v-if="isLibrary" prop="retentionYears" label="保管期限" width="90" align="center" />
          <el-table-column v-if="isLibrary" label="原件保管" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.paperKeeperCompanyId">{{ companyNameOf(row.paperKeeperCompanyId) }}<span v-if="row.paperLocation"> · {{ row.paperLocation }}</span></span>
              <span v-else style="color:#909399">集中保管</span>
            </template>
          </el-table-column>
          <el-table-column label="关联单据" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ billRefText(row) }}</template>
          </el-table-column>
          <el-table-column label="卷内件数" width="90" align="center">
            <template #default="{ row }">
              <span v-if="itemCountOf(row) !== undefined" :style="itemCountOf(row) === 0 ? 'color:#f56c6c' : ''">
                {{ itemCountOf(row) }}</span>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="archTag(row.archiveStatus)" size="small">{{ archStatusLabel(row.archiveStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完整性" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="integrityTag(row.integrityStatus)" size="small">{{ integrityLabel(row.integrityStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="400" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <!-- 卷内 0 件的卷，服务端入口闸必拦（「卷内无件，不得执行归档」）⇒ 这里提前置灰，不让人撞了才知道 -->
              <el-tooltip v-if="!isLibrary && canRunArchive" :disabled="itemCountOf(row) !== 0" content="卷内无件，请先加入待归档单据" placement="top">
                <span>
                  <el-button v-if="row.archiveStatus !== BillStatus.ARCHIVED && row.archiveStatus !== 'ARCHIVING'" link size="small" type="primary"
                             :disabled="itemCountOf(row) === 0" :loading="busy('archive-do-' + row.id)" @click="doArchive(row)">执行归档</el-button>
                </span>
              </el-tooltip>
              <el-button link size="small" type="primary" :loading="busy('archive-files-' + row.id)" @click="viewVolumeFiles(row)">查看卷内文件</el-button>
              <el-button v-if="!isLibrary && canCreateDestroy && row.archiveStatus !== 'DISPOSED'" link size="small" type="danger" @click="destroyArchive(row)">发起销毁申请</el-button>
              <!-- 空卷出口：建错卷/件全移出后留下的空壳既不能归档也无处删。只放行「待归档＋0 件」，已归档请走销毁 -->
              <el-button v-if="!isLibrary && canEditVolume && row.archiveStatus === 'PENDING' && itemCountOf(row) === 0" link size="small" type="danger" @click="deleteEmptyVolume(row)">删除空卷</el-button>
              <el-button link size="small" @click="verifyArchive(row)">完整性校验</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerArchive" @change="loadArchive" />
      </el-tab-pane>
    </el-tabs>

    <!-- 调阅申请弹窗 -->
    <el-dialog v-model="dlgAccess" title="发起档案调阅申请" width="560px" destroy-on-close>
      <el-form ref="accessFormRef" :model="accForm" :rules="accessRules" label-width="100px" size="small">
        <el-form-item label="调阅档案" prop="archiveId">
          <div style="display:flex; gap:8px; width:100%">
            <el-input :model-value="selectedArchiveLabel" disabled placeholder="请选择要调阅的档案" style="flex:1" />
            <el-button type="primary" plain @click="openArchivePicker('single')">选择档案</el-button>
          </div>
        </el-form-item>
        <el-form-item label="调阅类型" prop="accessType">
          <el-radio-group v-model="accForm.accessType">
            <el-radio-button label="READ">查阅</el-radio-button>
            <el-radio-button label="COPY">复制副本</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请理由" prop="reason">
          <el-input v-model="accForm.reason" type="textarea" :rows="3" placeholder="请简要说明调阅目的，如：审计核查2025年度报销凭证" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgAccess = false">取消</el-button>
        <el-button type="primary" :disabled="!accForm.archiveId" @click="saveAccess">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 销毁申请弹窗 -->
    <el-dialog v-model="dlgDestroy" title="发起档案销毁申请" width="640px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        档案销毁是不可逆操作，请确认所选档案已过保管期限且经审批后方可执行。
      </el-alert>
      <el-form ref="destroyFormRef" :model="destroyModel" :rules="destroyRules" label-width="100px" size="small">
        <el-form-item label="销毁档案" prop="archiveIds">
          <div style="display:flex; gap:8px; width:100%; align-items:flex-start">
            <div style="flex:1; min-height:32px; padding:4px 8px; border:1px solid #dcdfe6; border-radius:4px; background: var(--app-bg, #f5f7fa)">
              <template v-if="selectedDestroyArchives.length">
                <el-tag v-for="a in selectedDestroyArchives" :key="a.id" closable size="small" style="margin:2px" @close="removeDestroyArchive(a.id)">{{ archiveRealNo(a) || ('档案#' + a.id) }} ({{ archiveTypeLabel(a.archiveType) }})</el-tag>
              </template>
              <span v-else style="color:#c0c4cc; line-height:24px">请选择要销毁的档案</span>
            </div>
            <el-button type="primary" plain @click="openArchivePicker('multi')">选择档案</el-button>
          </div>
        </el-form-item>
        <el-form-item label="销毁理由" prop="destroyReason">
          <el-input v-model="destroyReason" type="textarea" :rows="3" placeholder="请说明销毁原因，如：档案已过保管期限10年" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgDestroy = false">取消</el-button>
        <el-button type="danger" :disabled="!selectedDestroyArchives.length || busy('destroy-create')" :loading="busy('destroy-create')" @click="saveDestroy">提交销毁申请</el-button>
      </template>
    </el-dialog>

    <!-- 单据流转弹窗 -->
    <el-dialog v-model="dlgDispatch" title="发起单据流转" width="560px" destroy-on-close>
      <el-form ref="dispatchFormRef" :model="dpForm" :rules="dispatchRules" label-width="100px" size="small">
        <el-form-item label="单据标识" prop="billId">
          <!-- 纯手输框在这里行不通（placeholder 写「业务单据编号」，实际要的却是数字主键，
               用户无从得知填什么）——故从「我的单据」列表选取（billApi.myPage），也允许直接输入 -->
          <el-select
            v-model="dpForm.billId"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="请选择单据（从列表选择；也可直接输入单据编号或ID）"
            style="width:100%"
            @focus="ensureBillOptions"
          >
            <el-option v-for="b in billOptions" :key="b.id" :label="billOptionLabel(b)" :value="String(b.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据类型" prop="billType">
          <el-select v-model="dpForm.billType" style="width:100%">
            <el-option label="费用报销" value="EXPENSE" />
            <el-option label="采购付款" value="PAYMENT" />
            <el-option label="对公付款" value="CORP_PAY" />
            <el-option label="借款" value="LOAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="费项" prop="expenseType">
          <el-input v-model="dpForm.expenseType" placeholder="如：办公费、差旅费" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="dpForm.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="法人主体" prop="legalEntityId">
          <el-tree-select
            v-model="dpForm.legalEntityId"
            :data="companyTree"
            :props="{ label: 'companyName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="请选择法人主体"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgDispatch = false">取消</el-button>
        <el-button type="primary" @click="saveDispatch">发起流转</el-button>
      </template>
    </el-dialog>

    <!-- 新建案卷弹窗 -->
    <el-dialog v-model="dlgArchive" title="新建案卷" width="560px" destroy-on-close>
      <el-form ref="archiveFormRef" :model="arForm" :rules="archiveFormRules" label-width="100px" size="small">
        <el-form-item label="档案类型" prop="archiveType">
          <el-select v-model="arForm.archiveType" style="width:100%">
            <!-- 选项集与 archiveTypeLabel 名称表对齐（8 项）。只给 4 项时，
                 现网占多数的 BILL（单据档案）库里能存、列表能显示、新建却选不到。
                 权威名称表由后端白名单把守（ArchiveService.ARCHIVE_TYPES），前端不再自造第 9 个值。 -->
            <el-option label="单据档案" value="BILL" />
            <el-option label="凭证档案" value="VOUCHER" />
            <el-option label="报销凭证" value="EXPENSE" />
            <el-option label="付款凭证" value="PAYMENT" />
            <el-option label="合同" value="CONTRACT" />
            <el-option label="发票" value="INVOICE" />
            <el-option label="报表" value="REPORT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="载体类型" prop="carrierType">
          <!-- 载体决定"归档前是否强制逐件编页"——纸质卷装订后页码是实物位置标识，
               电子档案以「件/文件」为最小单位不要求页码。默认纸质＝严格侧（认不出就按纸质要求）。 -->
          <el-radio-group v-model="arForm.carrierType">
            <el-radio label="PAPER">纸质</el-radio>
            <el-radio label="ELECTRONIC">电子</el-radio>
          </el-radio-group>
          <div style="color:#909399; font-size:12px; line-height:1.6">
            纸质卷归档前须逐件填写起页与页数；电子卷按件计数，不强制编页
          </div>
        </el-form-item>
        <el-form-item label="入卷单据" prop="billIds">
          <!-- 不能做成纯文本框（提示写「关联业务单据编号」，服务端要的却是数字主键）⇒ 输单号必被 400。
               一个案卷可挂多张单据⇒多选；选中顺序即卷内件号，与「待立卷候选」勾选预填同一入口。 -->
          <el-select v-model="arForm.billIds"
                     multiple
                     filterable
                     remote
                     reserve-keyword
                     :remote-method="searchFilingOptions"
                     :loading="filingSearching"
                     placeholder="请选择单据（可输入单号搜索，可多选）"
                     style="width:100%"
                     @focus="ensureFilingOptions"
                     @change="pinSelectedFiling"
          >
            <el-option v-for="b in filingOptions" :key="filingOptionId(b)" :label="filingOptionLabel(b)" :value="filingOptionId(b)" />
          </el-select>
          <div style="color:#909399; font-size:12px; line-height:1.6">
            {{ arForm.billIds.length > 1
              ? `将建 1 卷、挂 ${arForm.billIds.length} 件（按选择先后编件号）`
              : (arForm.billIds.length === 1 ? '将建 1 卷、挂 1 件；建好后仍可在该卷的卷内目录里【追加件】' : '请至少选择一张单据，否则无法建卷') }}
          </div>
        </el-form-item>
        <el-form-item label="年度" prop="archiveYear">
          <el-input-number v-model="arForm.archiveYear" :min="2000" :max="2100" style="width:100%" />
        </el-form-item>
        <el-form-item label="期间" prop="archivePeriod">
          <el-input v-model="arForm.archivePeriod" placeholder="如：2026-08" maxlength="20" />
        </el-form-item>
        <el-form-item label="保管期限" prop="retentionPeriod">
          <el-select v-model="arForm.retentionPeriod" style="width:100%">
            <el-option label="3年" :value="3" />
            <el-option label="5年" :value="5" />
            <el-option label="10年" :value="10" />
            <el-option label="15年" :value="15" />
            <el-option label="30年（永久）" :value="30" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgArchive = false">取消</el-button>
        <el-button type="primary" :loading="busy('archive-volume-create')" @click="saveArchive">创建案卷</el-button>
      </template>
    </el-dialog>

    <!-- 卷内追加件弹窗（往已存在的卷里再加单据） -->
    <el-dialog v-model="dlgAppend" title="向本卷追加单据" width="560px" destroy-on-close>
      <div style="margin-bottom:10px; color: var(--app-text-secondary, #606266); font-size:12px">
        案卷 {{ appendVolumeNo }}：选中的单据按先后续编件号加入本卷，已在卷内的单据不会重复加入。
      </div>
      <el-select v-model="appendBillIds"
                 multiple
                 filterable
                 remote
                 reserve-keyword
                 :remote-method="searchFilingOptions"
                 :loading="filingSearching"
                 placeholder="请选择要加入本卷的单据（可输入单号搜索，可多选）"
                 style="width:100%"
                 @focus="ensureFilingOptions"
                 @change="pinSelectedFiling"
      >
        <el-option v-for="b in filingOptions" :key="filingOptionId(b)" :label="filingOptionLabel(b)" :value="filingOptionId(b)" />
      </el-select>
      <template #footer>
        <el-button @click="dlgAppend = false">取消</el-button>
        <el-button type="primary" :disabled="!appendBillIds.length" :loading="busy('archive-item-add-' + appendVolumeId)" @click="submitAppendItems">
          加入本卷（{{ appendBillIds.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 档案选择器弹窗（借阅单选 / 销毁多选共用） -->
    <el-dialog v-model="dlgPicker" :title="pickerMode === 'multi' ? '选择档案（可多选）' : '选择档案'" width="780px" append-to-body>
      <div style="margin-bottom:12px; display:flex; gap:8px; align-items:center">
        <!-- 检索臂＝卷号/编码/关联单号/标题/类型，本地零命中还会下推到服务端查全量；
             提示语跟着检索规则写，别再只说"档案编码"（那一列历史上恒空，按它搜必然零命中）。 -->
        <el-input v-model="pickerSearch" placeholder="搜索档案号/单号/标题" clearable size="small" style="width:260px" @input="filterArchives" />
        <el-select v-model="pickerTypeFilter" placeholder="档案类型" clearable size="small" style="width:140px" @change="filterArchives">
          <el-option label="单据档案" value="BILL" />
          <el-option label="报销凭证" value="EXPENSE" />
          <el-option label="付款凭证" value="PAYMENT" />
          <el-option label="合同" value="CONTRACT" />
          <el-option label="发票" value="INVOICE" />
        </el-select>
        <el-button size="small" @click="pickerSearch = ''; pickerTypeFilter = ''; filterArchives()">重置</el-button>
        <span style="color:#909399; font-size:12px">共 {{ filteredArchives.length }} 条</span>
      </div>
      <el-table v-loading="loading" empty-text="暂无数据"
        :data="filteredArchives"
        size="small"
        border
        stripe
        max-height="380"
        @selection-change="onPickerSelection"
        @row-click="onPickerRowClick"
        style="cursor:pointer"
        :row-key="(r) => r.id"
        ref="pickerTable"
      >
        <el-table-column v-if="pickerMode === 'multi'" type="selection" width="45" />
        <el-table-column v-else label="" width="45" align="center">
          <template #default="{ row }">
            <el-radio :model-value="pickerSingleId" :label="row.id" @change="pickerSingleId = row.id">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <!-- 选择器这张表也绑 archiveNo（真卷号），不是恒空的冗余列 archiveCode -->
        <el-table-column prop="archiveNo" label="档案编码" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="archiveTypeTag(row.archiveType)">{{ archiveTypeLabel(row.archiveType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="archiveYear" label="年度" width="80" align="center"  sortable />
        <el-table-column prop="archivePeriod" label="期间" width="90" align="center"  sortable />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="archTag(row.archiveStatus)" size="small">{{ archStatusLabel(row.archiveStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dlgPicker = false">取消</el-button>
        <el-button type="primary" @click="confirmPicker">确认选择</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  <!-- 卷内文件查看器：文件清单与取流令牌都走案卷自己的端点（档案岗无单据归属，不要经单据详情取流） -->
  <FileViewer ref="fileViewer" />

  <CoachMark ref="archiveCm" :guide-key="_guideKey" :selector="'.cm-target-board'" title="归档看板" text="归档看板展示已归档单据的统计和检索入口。" />
  </div>
</template>

<script setup>

import { onMounted, ref, computed, watch, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveAccessApi, archiveDestroyApi, fmApi, billApi, opApi, userApi, orgApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import FileViewer from '../../components/FileViewer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { billTypeLabel as enumBillTypeLabel } from '@/constants/enums'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useTablePager } from '../../composables/useTablePager'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import TablePager from '../../components/common/TablePager.vue'
import { hasPerm } from '@/utils/perm'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})
const archiveCm = ref(null)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
const TRACE_BIZ_BY_TAB = { access: 'FM_BORROW', destroy: 'FM_DESTROY', dispatch: 'FM_DOC_FLOW', archive: 'FM_ARCHIVE' }
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = TRACE_BIZ_BY_TAB[tab.value] || null
  traceVisible.value = true
}

// P: tab 页拆分——由菜单/工作区传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
// scope：档案 tab 的用途分流——'filing'＝归档整理（在办卷＋待立卷候选）、'library'＝档案库（已归档目录）；空＝全量
const props = defineProps({
  tab: { type: String, default: '' },
  scope: { type: String, default: '' },
})
const isSingle = computed(() => !!props.tab)
const isLibrary = computed(() => props.tab === 'archive' && props.scope === 'library')
const tab = ref(props.tab || 'access')
const loading = ref(false)
const accesses = ref([])
// 调阅记录状态筛选（ALL=全部，端点默认值 PENDING 保持不变）
const accessStatusFilter = ref('ALL')
const destroys = ref([])
const dispatches = ref([])
const archives = ref([])
// 四个 tab 全部切 server 模式（后端均有分页接口；原 local 会把第一页当全量导致截断）
const pagerAccess = useTablePager({ mode: 'server', storageKey: 'archive_access' })
const pagerDestroy = useTablePager({ mode: 'server', storageKey: 'archive_destroy' })
const pagerDispatch = useTablePager({ mode: 'server', storageKey: 'archive_dispatch' })
const pagerArchive = useTablePager({ mode: 'server', storageKey: 'archive_archive' })
const { pagedData: pagedAccess } = pagerAccess
const { pagedData: pagedDestroy } = pagerDestroy
const { pagedData: pagedDispatch } = pagerDispatch
const { pagedData: pagedArchive } = pagerArchive

// 用户与公司数据
const users = ref([])
const companyTree = ref([])
const currentUserId = computed(() => store.user?.userId ?? store.user?.id ?? 0)
const currentUserName = computed(() => store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '当前用户')

// 档案选择器
const dlgPicker = ref(false)
const pickerMode = ref('single')
const pickerSearch = ref('')
const pickerTypeFilter = ref('')
const filteredArchives = ref([])
// 选择器独立数据源（主表格已分页，当前页不再是全集；打开弹窗时按需拉取）
const pickerSource = ref([])
const pickerSingleId = ref(null)
const pickerMultiSelection = ref([])
const pickerTable = ref(null)

// 调阅申请
const dlgAccess = ref(false)
const accForm = ref({ archiveId: null, accessType: 'READ', reason: '' })
const selectedArchiveLabel = computed(() => {
  if (!accForm.value.archiveId) return ''
  // 走目录回落（archives 只是凭证归档 tab 的当前页）；取不到真号就说档案#ID，不显 null
  const row = findArchiveRow(accForm.value.archiveId)
  const no = archiveRealNo(row)
  if (no) return `${no} (${archiveTypeLabel(row.archiveType)})`
  return `档案#${accForm.value.archiveId}`
})

// 销毁申请
const dlgDestroy = ref(false)
const destroyReason = ref('')
const selectedDestroyArchives = ref([])

// 单据流转
const dlgDispatch = ref(false)
const dpForm = ref({ billId: '', billType: 'EXPENSE', expenseType: '', amount: 0, legalEntityId: null })
// 单据标识选取源（billApi.myPage＝「我的单据」；只尝试拉一次，失败不阻断手工输数字ID）
const billOptions = ref([])
let billOptionsTried = false
async function ensureBillOptions() {
  if (billOptions.value.length || billOptionsTried) return
  billOptionsTried = true
  try {
    const r = await billApi.myPage({ pageNum: 1, pageSize: 200 })
    billOptions.value = r.list || r.records || r.rows || []
  } catch { /* 无单/无权限账号仍可手工输入数字单据ID */ }
}
function billOptionLabel(b) {
  const no = b.billNo || ('ID ' + b.id)
  const t = b.billType ? ` · ${billTypeLabel(b.billType)}` : ''
  const amt = b.totalAmount != null ? ` · ¥${b.totalAmount}` : ''
  return no + t + amt
}

// 凭证归档
const dlgArchive = ref(false)
const arForm = ref({ billIds: [], archiveType: 'EXPENSE', carrierType: 'PAPER', archiveYear: new Date().getFullYear(), archivePeriod: '', retentionPeriod: 10 })

/**
 * 先勾一批单据，再新建案卷。
 * 候选清单与「共享运营›档案归档」同一把尺（审批完成＋影像完整），不要另起一套取数。
 */
const pagerCandidate = useTablePager({ mode: 'server', storageKey: 'archive_candidate' })
const candidates = pagerCandidate.rows
const candLoading = pagerCandidate.loading
const candSelected = ref([])
const candTotal = computed(() => pagerCandidate.total.value)
const canListPending = computed(() => hasPerm('op:archive:list'))
/** 卷内件清单的增删＝写动作，与服务端同一个码；无该码只读，不给假入口 */
const canEditVolume = computed(() => hasPerm('compliance:archive:create'))
// 以下四个＝各页签写动作的服务端同款码：本岗没有就不要渲染按钮，不要等点了才吃「缺少权限」红字。
const canRunArchive = computed(() => hasPerm('compliance:archive:archive'))
const canRequestAccess = computed(() => hasPerm('compliance:archiveaccess:request'))
const canCreateDestroy = computed(() => hasPerm('compliance:archivedestroy:create'))
const canApproveDestroy = computed(() => hasPerm('compliance:archivedestroy:approve'))
const canExecuteDestroy = computed(() => hasPerm('compliance:archivedestroy:execute'))
/** 单据流转（派单作业）已迁到「共享运营配置」页签：读按 list、写按 run/assign，只读者不给假按钮 */
const canRunDispatch = computed(() => hasPerm('compliance:dispatch:run') || hasPerm('compliance:dispatch:assign'))

async function loadCandidates() {
  if (!canListPending.value) return
  try {
    await pagerCandidate.load(async ({ pageNum, pageSize }) => {
      const res = await opApi.archivePending({ pageNum, pageSize, archiveStatus: '' })
      return { list: res.list || res.records || [], total: res.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

/**
 * 入卷选单源。有清单权时＝待归档清单；否则回落「我的单据」（同源）。
 * 不要让用户手输内部主键——文本框提示写「业务单据编号」，服务端要的却是 Long，
 * 输单号必被 400，而报错还指向"单据列表"（弹窗里根本没有列表）。
 * 一个案卷可多选后，远端搜索结果（filingRemote）与已选中的行（filingPinned）都要并入选项，
 * 否则翻页/换关键字后已勾的单据只剩一串数字主键、看不清选了什么。
 */
const filingRemote = ref([])
const filingPinned = ref([])
const filingSearching = ref(false)
const filingOptions = computed(() => {
  const base = canListPending.value && candidates.value.length ? candidates.value : billOptions.value
  const seen = {}
  return [...filingRemote.value, ...filingPinned.value, ...(base || [])].filter((b) => {
    const k = filingOptionId(b)
    if (!k || seen[k]) return false
    seen[k] = true
    return true
  })
})
function filingOptionLabel(b) {
  if (b.billNo) return `${b.billNo}${b.billType ? ` · ${billTypeLabel(b.billType)}` : ''}${b.totalAmount != null ? ` · ¥${b.totalAmount}` : ''}`
  return billOptionLabel(b)
}
function filingOptionId(b) {
  return String(b.id ?? b.billId)
}

/** 远端搜单：仍用清单/我的单据这两个既有取数口，不要新端点 */
async function searchFilingOptions(kw) {
  const keyword = String(kw || '').trim()
  if (!keyword) { filingRemote.value = []; return }
  filingSearching.value = true
  try {
    if (canListPending.value) {
      const r = await opApi.archivePending({ pageNum: 1, pageSize: 50, archiveStatus: '', keyword })
      filingRemote.value = r.list || r.records || []
    } else {
      const r = await billApi.myPage({ pageNum: 1, pageSize: 50 })
      const list = r.list || r.records || r.rows || []
      const k = keyword.toLowerCase()
      filingRemote.value = list.filter((b) => String(b.billNo || '').toLowerCase().includes(k)
        || String(b.title || '').toLowerCase().includes(k))
    }
  } catch (e) { ElMessage.error(e.message) } finally { filingSearching.value = false }
}

/** 弹窗聚焦时按当前岗拉选单源：有清单权＝待归档清单（含他人单据），否则＝我的单据 */
function ensureFilingOptions() {
  if (canListPending.value) {
    if (!candidates.value.length) loadCandidates()
    return
  }
  ensureBillOptions()
}

/** 已选单据钉进选项：换页/换关键字后标签仍是单号，不塌成一串数字主键 */
function pinSelectedFiling(vals) {
  const pool = [...filingRemote.value, ...filingPinned.value, ...(canListPending.value ? candidates.value : billOptions.value)]
  filingPinned.value = (vals || [])
    .map((v) => pool.find((b) => filingOptionId(b) === String(v)))
    .filter(Boolean)
}

// 校验规则唯一规则＝中央登记表（useFormRules）；页面不再本地手写规则对象
const accessFormRef = ref(null)
const destroyFormRef = ref(null)
const dispatchFormRef = ref(null)
const archiveFormRef = ref(null)
const { rules: accessRules } = useFormRules('ARCHIVE_ACCESS')
const { rules: destroyRules } = useFormRules('ARCHIVE_DESTROY')
const { rules: dispatchRules } = useFormRules('ARCHIVE_DISPATCH')
const { rules: archiveFormRules } = useFormRules('ARCHIVE_CREATE')
const { submit: guardAccess } = useSubmitGuard(accessFormRef)
const { submit: guardDestroy } = useSubmitGuard(destroyFormRef)
const { submit: guardDispatch } = useSubmitGuard(dispatchFormRef)
const { submit: guardArchive } = useSubmitGuard(archiveFormRef)
// 销毁档案是选择器（非表单控件），把已选 ID 映射进模型，prop="archiveIds" 才解析得到
const destroyModel = computed(() => ({
  archiveIds: selectedDestroyArchives.value.map((a) => a.id),
  destroyReason: destroyReason.value,
}))

/**
 * 假失败＋重复起流程：长耗时写动作（起流程类）在途锁。
 * 提交在飞或慢响应窗口里连点两次，会把同一批档案起两条流程（前端 20s 断连时，
 * archive-destroy/create 服务端其实已建 wf_instance）；useSubmitGuard 只管必填校验、无在途态，
 * 这里按「动作＋对象」维度补上：整批动作（销毁申请）一个 key，逐行动作（执行归档/执行销毁）每行一个 key。
 */
const inFlight = reactive({})
const busy = (key) => !!inFlight[key]
async function runLocked(key, fn) {
  if (inFlight[key]) return
  inFlight[key] = true
  try { return await fn() } finally { inFlight[key] = false }
}

// 工具函数
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * 列表一律以业务编号定位，不要内部主键上屏。
 * 若写成 `row.billNo || 单据#${row.billId}`，会踩两个坑：
 * 档案分页出参是裸实体 OpArchive（无 billNo 字段）、派工出参是裸实体 DispatchTask（同），
 * ⇒ billNo 恒 undefined，这列永远显示主键；免审腿连 bill_id 都不写，于是渲染成「单据#null」。
 * 单号其实一直在 ref_no 里（ArchiveService.fillCatalogFromSourceBill／BillArchiveService 三条腿同源）。
 */
function billRefText(row) {
  return row.billNo || row.refNo || '—'
}

function accessTag(s) { return STATUS_TYPE[s] || 'info' }
function accessStatusLabel(s) { return STATUS_LABEL[s] || s || '未知' }

/**
 * 后端对 access_type 无白名单（历史接口写入过 VIEW 等越词值），故未知值原样显示，不并入「复制」。
 * VIEW 是历史接口经 /request 写入的值（申请弹窗只会产生 READ/COPY），语义与 READ 同为线上查阅；
 * 名称表缺这个键就会把英文码直出到列表，且 tag 取空串触发 ElementPlus 校验告警。
 */
function accessTypeLabel(t) { return ({ READ: '查阅', VIEW: '查阅', COPY: '复制副本' })[t] || t || '—' }
function accessTypeTag(t) { return t === 'COPY' ? 'warning' : 'info' }

/** 审批/授予入口按服务端同款权限码门控：无 compliance:archiveaccess:approve 的人不应看到按钮 */
const canApproveAccess = computed(() => hasPerm('compliance:archiveaccess:approve'))

function destroyTag(s) { return STATUS_TYPE[s] || 'info' }
function destroyStatusLabel(s) { return STATUS_LABEL[s] || '待审批' }

function dispTag(s) { return s === 'COMPLETED' ? 'success' : s === 'ESCALATED' ? 'danger' : s === 'IN_PROGRESS' ? 'warning' : 'info' }
function dispStatusLabel(s) { return ({ COMPLETED: '已完成', ESCALATED: '已升级', IN_PROGRESS: '进行中', PENDING: '待处理' })[s] || s || '未知' }

function archTag(s) { return s === BillStatus.ARCHIVED ? 'success' : s === 'DISPOSED' ? 'danger' : 'warning' }
function archStatusLabel(s) { return ({ ARCHIVED: '已归档', ARCHIVING: '归档中', DISPOSED: '已销毁', PENDING: '待归档' })[s] || s || '未知' }

function integrityTag(s) { return s === 'VERIFIED' ? 'success' : s === 'MISMATCH' ? 'danger' : s === 'UNVERIFIABLE' ? 'warning' : 'info' }
// 销毁最终状态 NOT_APPLICABLE 独立成像（已按制度销毁 ⇒ 完整性"不适用"），
// 不再与真实篡改的 MISMATCH 混成同一个"不一致"标记
// UNVERIFIABLE（历史档案未固化哈希，无法校验）独立成像，不再冒充"被篡改"
function integrityLabel(s) {
  if (s === 'NOT_APPLICABLE') return '不适用（已销毁）'
  if (s === 'UNVERIFIABLE') return '无法校验（未固化哈希）'
  return s === 'VERIFIED' ? '已校验' : s === 'MISMATCH' ? '不一致' : '未校验'
}

/** 本页面只列本域会遇到的四类，其余一律回落中央名称表（不要枚举码直显英文） */
function billTypeLabel(t) {
  return ({ EXPENSE: '费用报销', PAYMENT: '采购付款', CORP_PAY: '对公付款', LOAN: '借款' })[t] || enumBillTypeLabel(t) || '—'
}

function archiveTypeLabel(t) { return ({ BILL: '单据档案', VOUCHER: '凭证档案', EXPENSE: '报销凭证', PAYMENT: '付款凭证', CONTRACT: '合同', INVOICE: '发票', REPORT: '报表', OTHER: '其他' })[t] || t || '—' }
function archiveTypeTag(t) { return ({ BILL: 'info', VOUCHER: 'info', EXPENSE: '', PAYMENT: 'success', CONTRACT: 'warning', INVOICE: 'danger' })[t] || 'info' }

// 档案编号显示
/**
 * 档案目录缓存：调阅记录表与销毁申请表上只有裸 archive_id，「档案编码」列只能拿
 * archives（＝凭证归档 tab 的<b>当前页</b>，别的 tab 根本不加载它）本地 join，
 * 于是恒退化成「#12」——列上拿不到真档案号，「档案编码列全空」在调阅/销毁两页都是这个形态。
 * 故按需拉一次档案目录（与选择器同一端点、同一份数据）作为回落源。
 */
const archiveDir = ref([])
// 目录只尝试拉一次：无 compliance:archive:list 的人反复切 tab 会反复吃权限报错
let archiveDirTried = false

async function ensureArchiveDir() {
  if (archiveDir.value.length || archiveDirTried) return
  archiveDirTried = true
  await refreshPickerSource()
}

/** 按档案 ID 找目录行（跨 tab 可用；archives 只覆盖当前页，目录缓存覆盖首 200 卷，取不到返回 null） */
function findArchiveRow(id) {
  if (id === null || id === undefined || id === '') return null
  const k = String(id)
  return archives.value.find((x) => String(x.id) === k)
    || archiveDir.value.find((x) => String(x.id) === k)
    || null
}

/**
 * 59，沿用「取<b>真档案号</b>的唯一规则」。
 * 优先序 archive_no → archive_code（184 已把它做成编号镜像，老卷仍 NULL）→ 目录回落；
 * 全都取不到时返回<b>空串</b>，由调用方整段不显号——绝不再把 null/undefined 拼进文案。
 */
function archiveRealNo(row) {
  if (!row) return ''
  const direct = row.archiveNo || row.archiveCode
  if (direct) return String(direct)
  const hit = findArchiveRow(row.archiveId ?? row.id)
  return hit && (hit.archiveNo || hit.archiveCode) ? String(hit.archiveNo || hit.archiveCode) : ''
}

/** 确认框/提示里"哪一卷"的说法：有真号就点号，没号就如实说卷号未生成，不显 null */
function archiveSubject(row) {
  const no = archiveRealNo(row)
  if (no) return `档案「${no}」`
  const id = row?.archiveId ?? row?.id
  return id ? `该档案（ID ${id}，档案编号尚未生成）` : '该档案（档案编号尚未生成）'
}

function archiveLabel(id) {
  const a = findArchiveRow(id)
  if (!a) return `档案#${id}`
  const no = archiveRealNo(a)
  return `${no || `档案#${id}`} (${archiveTypeLabel(a.archiveType)})`
}

function userLabel(id) {
  if (!id) return '—'
  const u = users.value.find((x) => x.id === id)
  // 与全仓其它页面同一规则：昵称优先、缺失才退登录号（只取 userName 会在列上显示工号）
  return u ? (u.nickName || u.userName) : `用户#${id}`
}

/**
 * 申请人以后端补充好的姓名为主，前端本地 join 只作兜底（历史脏 ID / 姓名缺失）。
 * 工号不混进姓名，另走「显示工号列」开关。
 */
function personLabel(name, id) {
  return name || userLabel(id)
}

/** 工号列默认隐藏，勾出才显示（同一开关管调阅与销毁两张表） */
const showUserNameCols = ref(false)

function archiveNamesFromIds(ids) {
  // 后端 archive_ids 落的是 JSON 字符串（"[12,24]"），不是数组——只判 Array.isArray 会让本列恒为「—」。
  let list = ids
  if (typeof list === 'string') {
    try { list = JSON.parse(list) } catch { list = list.split(',') }
  }
  if (!Array.isArray(list)) return []
  return list.filter((v) => v !== null && v !== undefined && v !== '').map((id) => {
    // 销毁申请行上的档案号同样走目录回落（archives 不含别的 tab 的数据），
    // 真号取不到就说档案#ID，不出现 "null"/"undefined"
    const a = findArchiveRow(id)
    return archiveRealNo(a) || `档案#${id}`
  })
}

// 档案选择器
async function openArchivePicker(mode) {
  pickerMode.value = mode
  pickerSearch.value = ''
  pickerTypeFilter.value = ''
  pickerSingleId.value = accForm.value.archiveId || null
  pickerMultiSelection.value = [...selectedDestroyArchives.value]
  await refreshPickerSource()
  filterArchives()
  dlgPicker.value = true
  // 恢复多选选中状态
  if (mode === 'multi' && pickerTable.value) {
    setTimeout(() => {
      filteredArchives.value.forEach((row) => {
        if (selectedDestroyArchives.value.find((x) => x.id === row.id)) {
          pickerTable.value.toggleRowSelection(row, true)
        }
      })
    }, 100)
  }
}

async function filterArchives() {
  let list = pickerSource.value
  const kw = (pickerSearch.value || '').trim().toLowerCase()
  if (kw) {
    // 候选集只是"已经取回的第一页目录"，只认
    // archiveNo/archiveType 两臂 ⇒ 真实卷号落在第一页外（或卷号在 archive_code 镜像列上）就"返 0 条"。
    // 本地臂先补齐（卷号/编码/关联单号/标题/类型），仍零命中就下推到服务端问全量（见下）。
    list = list.filter((a) => (a.archiveNo || '').toLowerCase().includes(kw)
      || (a.archiveCode || '').toLowerCase().includes(kw)
      || (a.refNo || '').toLowerCase().includes(kw)
      || (a.title || '').toLowerCase().includes(kw)
      || (a.archiveType || '').toLowerCase().includes(kw)
      || archiveTypeLabel(a.archiveType).toLowerCase().includes(kw))
  }
  if (pickerTypeFilter.value) {
    list = list.filter((a) => a.archiveType === pickerTypeFilter.value)
  }
  filteredArchives.value = list
  // 本地零命中且有关键字 ⇒ 按同一关键字请服务端在全量目录里查一次
  // （/compliance/archive/page 的 keyword 臂与 ArchiveService.search 的 检索面共用同一把尺，
  //  但不像 search 那样硬过滤 ARCHIVED ⇒ 刚立的待归档卷，输卷号同样选得到；检索面的取数范围不受影响）
  if (kw && !list.length) {
    try {
      const params = { pageNum: 1, pageSize: 50, keyword: pickerSearch.value.trim() }
      if (pickerTypeFilter.value) params.archiveType = pickerTypeFilter.value
      const res = await fmApi.archivePage(params)
      const hits = (res && res.list) || []
      filteredArchives.value = hits
      // 服务端命中并入目录缓存：调阅/销毁两表的 ID→卷号 回落也认得这些卷
      const known = {}
      archiveDir.value.forEach((x) => { known[String(x.id)] = true })
      archiveDir.value = archiveDir.value.concat(hits.filter((x) => !known[String(x.id)]))
    } catch (e) { /* 服务端不可达时保留本地结果，不把选择器变成空白 */ }
  }
}

// 打开选择器时按需拉取归档目录（原逻辑复用主表格全量数据，分页后需独立拉取）
async function refreshPickerSource() {
  try {
    const res = await fmApi.archivePage({ pageNum: 1, pageSize: 200 })
    pickerSource.value = res.list || []
  } catch (e) { pickerSource.value = archives.value }
  // 同一份目录也供调阅/销毁两张表的「档案编码」列回落使用（它们只有裸 archive_id）
  archiveDir.value = pickerSource.value
}

function onPickerSelection(rows) {
  pickerMultiSelection.value = rows
}

function onPickerRowClick(row) {
  if (pickerMode.value === 'single') {
    pickerSingleId.value = row.id
  }
}

function confirmPicker() {
  if (pickerMode.value === 'single') {
    if (!pickerSingleId.value) { ElMessage.warning(t('archive.main.Please')); return }
    accForm.value.archiveId = pickerSingleId.value
  } else {
    if (!pickerMultiSelection.value.length) { ElMessage.warning(t('archive.main.Select')); return }
    selectedDestroyArchives.value = [...pickerMultiSelection.value]
  }
  dlgPicker.value = false
}

function removeDestroyArchive(id) {
  selectedDestroyArchives.value = selectedDestroyArchives.value.filter((x) => x.id !== id)
}

// 档案调阅
async function loadAccess(quiet) {
  try {
    await pagerAccess.load(async ({ pageNum, pageSize }) => {
      const r = await archiveAccessApi.pendingPage({ pageNum, pageSize, status: accessStatusFilter.value })
      accesses.value = r.list || []
      return { list: accesses.value, total: r.total || 0 }
    })
    // 调阅记录行只有裸 archive_id，「档案编码」列要靠档案目录才能出真号
    //（改按 tab 查询后 archives 在本 tab 根本不加载，列就恒退化成 #ID）
    await ensureArchiveDir()
  } catch (e) {
    // quiet 严格判 true——本函数被 @click/@change/TablePager 直接绑定时会收到事件对象，
    // 若按真值判断就把刷新失败当成静默重抛，调用方（TAB_LOADERS）接不到 ⇒ 既无提示也无 handle。
    if (quiet !== true) ElMessage.error(e.message); else throw e
  }
}

function openAccess() {
  accForm.value = { archiveId: null, accessType: 'READ', reason: '' }
  dlgAccess.value = true
}

async function saveAccess() {
  await guardAccess(async () => {
    try {
      const body = {
        archiveId: accForm.value.archiveId,
        accessType: accForm.value.accessType,
        reason: accForm.value.reason,
        requesterId: currentUserId.value,
      }
      await archiveAccessApi.request(body)
      ElMessage.success(t('archive.main.Retrieval'))
      dlgAccess.value = false
      // request 已成功，随后的记录刷新才撞 compliance:archiveaccess:list 缺权——
      // 原样 ElMessage.error 会把成功报成失败（建单成功＋同秒弹「缺少权限」）。
      // 提交后的刷新属附带动作：失败只明示权限缺口，不改变"已提交"结论。
      try {
        await loadAccess(true)
      } catch {
        ElMessage.warning(t('archive.main.Retrieval2'))
      }
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function approveAccess(row, approved) {
  try { await archiveAccessApi.approve(row.id, currentUserId.value, approved); ElMessage.success(t('purchase.poOrder.Rejected')); loadAccess() }
  catch (e) { ElMessage.error(e.message) }
}

async function grantAccess(row) {
  try { await archiveAccessApi.grant(row.id); ElMessage.success(t('archive.main.Archive2')); loadAccess() }
  catch (e) { ElMessage.error(e.message) }
}

// 档案销毁
async function loadDestroy() {
  try {
    await pagerDestroy.load(async ({ pageNum, pageSize }) => {
      const r = await archiveDestroyApi.listPage({ pageNum, pageSize })
      destroys.value = r.list || []
      return { list: destroys.value, total: r.total || 0 }
    })
    await ensureArchiveDir()   // 销毁申请表的「销毁档案」列出真卷号，同上
  } catch (e) { ElMessage.error(e.message) }
}

function openDestroy() {
  selectedDestroyArchives.value = []
  destroyReason.value = ''
  dlgDestroy.value = true
}

async function saveDestroy() {
  await guardDestroy(async () => {
    await runLocked('destroy-create', async () => {
      try {
        const ids = selectedDestroyArchives.value.map((a) => a.id)
        await archiveDestroyApi.create(ids, destroyReason.value, currentUserId.value)
        ElMessage.success(t('archive.main.Destruction'))
        dlgDestroy.value = false
        loadDestroy()
      } catch (e) {
        if (e?.timeout) {
          // 起流程类长写超时≠失败：前端 20s 断连时服务端的建单/流程往往已经落库。
          // 拦截器已按「已提交，处理中」提示；这里直接办结——关窗并回读列表，堵住"再点一次"重复起流程的窗口。
          dlgDestroy.value = false
          loadDestroy()
        } else { ElMessage.error(e.message) }
      }
    })
  })
}

async function approveDestroy(row, approved) {
  try { await archiveDestroyApi.approve(row.id, currentUserId.value, approved); ElMessage.success(t('purchase.poOrder.Rejected')); loadDestroy() }
  catch (e) { ElMessage.error(e.message) }
}

async function executeDestroy(row) {
  await runLocked('destroy-exec-' + row.id, async () => {
    try {
      await ElMessageBox.confirm('确认执行销毁该批档案？此操作不可恢复！', '危险操作', { type: 'warning' })
      await archiveDestroyApi.execute(row.id)
      ElMessage.success(t('archive.main.Destruction2'))
      loadDestroy()
    } catch (e) {
      if (e === 'cancel' || e === 'close') return // 用户取消，静默
      // 只有用户取消才算静默，真实失败必须显式报错；超时按「已提交，处理中」办结（拦截器已提示），回读列表确认
      if (e?.timeout) { loadDestroy(); return }
      ElMessage.error(e.message)
    }
  })
}

// 单据流转
async function loadDispatch() {
  if (!hasPerm('compliance:dispatch:list')) return // 无读权限就不发请求，免得 4030 弹成红字
  try {
    await pagerDispatch.load(async ({ pageNum, pageSize }) => {
      const r = await fmApi.dispatchListPage({ pageNum, pageSize })
      dispatches.value = r.list || []
      return { list: dispatches.value, total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

function openDispatch() {
  dpForm.value = { billId: '', billType: 'EXPENSE', expenseType: '', amount: 0, legalEntityId: null }
  ensureBillOptions()
  dlgDispatch.value = true
}

async function saveDispatch() {
  await guardDispatch(async () => {
    try {
      await fmApi.dispatchRun({ ...dpForm.value })
      ElMessage.success(t('archive.main.Document'))
      dlgDispatch.value = false
      loadDispatch()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function completeDispatch(row) {
  try { await fmApi.dispatchComplete(row.id); ElMessage.success(t('archive.main.Dispatch')); loadDispatch() } catch (e) { ElMessage.error(e.message) }
}

async function escalateDispatch(row) {
  try {
    await ElMessageBox.prompt('请输入升级原因', '升级派单', { inputValue: '' })
      .then(async ({ value }) => { await fmApi.dispatchEscalate(row.id, value || ''); ElMessage.success(t('archive.main.Escalated')); loadDispatch() })
      .catch(() => {})
  } catch (e) { ElMessage.error(e.message) }
}

// 归档整理／档案库
// 目录筛选：整理页默认「在办」＝待归档＋归档中；档案库固定出已归档；深链（scope 空）＝不限
const archQ = reactive({
  keyword: '',
  status: props.scope === 'library' ? 'ARCHIVED' : (props.scope === 'filing' ? 'PENDING,ARCHIVING' : ''),
})
async function loadArchive() {
  const status = isLibrary.value ? 'ARCHIVED'
    : (archQ.status && archQ.status !== 'ALL' ? archQ.status : undefined)
  try {
    await pagerArchive.load(async ({ pageNum, pageSize }) => {
      const res = await fmApi.archivePage({
        pageNum, pageSize,
        keyword: archQ.keyword || undefined,
        archiveStatus: status,
      })
      archives.value = res.list || []
      return { list: archives.value, total: res.total || 0 }
    })
    // 件数由档案列表端点一次批量带回（row.itemCount，服务端 GROUP BY），不要逐卷再打一次接口
  } catch (e) { ElMessage.error(e.message) }
}
function archSearch() { pagerArchive.resetPage(); loadArchive() }

/** 原件保管单位 id → 公司名（companyTree 只读接口，普通账号仅见本公司） */
function companyNameOf(id) {
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (String(n.id) === String(id)) return n.companyName
      const hit = walk(n.children)
      if (hit) return hit
    }
    return ''
  }
  return walk(companyTree.value) || `公司#${id}`
}

/**
 * 卷内件数：优先用本地缓存（追加/移出件后刚刷新过），否则用列表端点带的 row.itemCount。
 * undefined＝未知（不置灰按钮，结论交给服务端入口闸），0＝空卷（置灰＋提示）。
 */
function itemCountOf(row) {
  if (!row) return undefined
  const cached = volumeItemCount[row.id]
  if (cached !== undefined) return cached
  return typeof row.itemCount === 'number' ? row.itemCount : undefined
}

/**
 * 卷内目录（件清单）与件数：卷 id → 行数组／件数。
 * 展开行与「执行归档」按钮都读这两张表；undefined＝件数未知（按钮不置灰）。
 */
const volumeItems = reactive({})
const volumeItemCount = reactive({})
const volumeItemsLoading = reactive({})
const archiveTable = ref(null)
const fileViewer = ref(null)

async function loadVolumeItems(volumeId) {
  if (volumeId === null || volumeId === undefined || volumeId === '') return
  volumeItemsLoading[volumeId] = true
  try {
    const list = await fmApi.archiveItems(volumeId)
    volumeItems[volumeId] = list || []
    volumeItemCount[volumeId] = (list || []).length
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    volumeItemsLoading[volumeId] = false
  }
}

function onVolumeExpand(row, arg) {
  // 展开才拉卷内目录（不进列表主查询，避免每页 N 倍取数）；收起时不请求
  // 展开事件的第二参是当前展开行数组，个别版本给布尔 ⇒ 两种都认
  const open = Array.isArray(arg) ? arg.some((x) => String(x && x.id) === String(row.id)) : !!arg
  if (open) loadVolumeItems(row.id)
}

/** 定稿／审批在途的卷：卷内件清单不可增删（与服务端同一判据，这里提前显出来） */
function volumeLocked(row) {
  return row.archiveStatus === 'ARCHIVING' || row.archiveStatus === BillStatus.ARCHIVED
}

function openAppendItems(row) {
  appendVolumeId.value = row.id
  appendVolumeNo.value = archiveRealNo(row) || `第 ${row.archiveYear || ''} 年 ${row.archivePeriod || ''} 期`
  appendBillIds.value = []
  filingPinned.value = []
  dlgAppend.value = true
}

async function submitAppendItems() {
  const ids = toBillIds(appendBillIds.value)
  if (!ids.length) { ElMessage.warning(t('archive.main.Please2')); return }
  await runLocked('archive-item-add-' + appendVolumeId.value, async () => {
    try {
      const n = await fmApi.archiveItemAdd(appendVolumeId.value, ids)
      ElMessage.success(`已加入 ${n ?? 0} ${t('archive.main.Items')}`)
      dlgAppend.value = false
      appendBillIds.value = []
      await loadVolumeItems(appendVolumeId.value)
      loadArchive()
      loadCandidates()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function removeVolumeItem(volume, item) {
  await runLocked('archive-item-remove-' + item.id, async () => {
    try {
      await ElMessageBox.confirm(
        `确认把${item.seqNo ? `第 ${item.seqNo} 件` : '这一件'}（${item.billNo || `单据 ${item.billId}`}）移出${archiveSubject(volume)}？单据本身不删除，可再加入其他案卷。`,
        '移出卷内件', { type: 'warning' })
      await fmApi.archiveItemRemove(volume.id, item.id)
      ElMessage.success(t('archive.main.Removed'))
      await loadVolumeItems(volume.id)
      loadArchive()
      loadCandidates()
    } catch (e) {
      if (e === 'cancel' || e === 'close') return // 用户取消，静默
      ElMessage.error(e.message)
    }
  })
}

/**
 * 件级编页：起页＋页数成对提交（服务端同一把尺校验：区间不得与卷内其它件重叠，
 * 且全卷要么都编、要么都不编，否则执行归档会被定稿判据拦下）。
 */
async function saveItemPages(volume, item) {
  const from = item.pageFrom == null || item.pageFrom === '' ? null : Number(item.pageFrom)
  const count = item.pageCount == null || item.pageCount === '' ? null : Number(item.pageCount)
  if ((from === null) !== (count === null)) {
    ElMessage.warning(t('archive.main.Start'))
    return
  }
  await runLocked('archive-item-pages-' + item.id, async () => {
    try {
      await fmApi.archiveItemPages(volume.id, item.id, { pageFrom: from, pageCount: count })
      ElMessage.success(from === null ? '已清除该件页号' : `第 ${item.seqNo} 件编页：${from} 起、共 ${count} ${t('archive.main.Pages')}`)
      await loadVolumeItems(volume.id)
    } catch (e) {
      ElMessage.error(e.message)
      await loadVolumeItems(volume.id) // 服务端拒绝（重叠/非可改窗口）时把行回读成库里真值，别留下假的编辑态
    }
  })
}

/**
 * 改载体类型。不要乐观更新——服务端拒绝（非待归档窗口）时选择框保持旧值，
 * 由 :model-value 绑行数据回显，不出现"界面已改、库里没改"的假态。
 */
async function changeCarrier(volume, carrierType) {
  if (!volume || (volume.carrierType || 'PAPER') === carrierType) return
  await runLocked('archive-carrier-' + volume.id, async () => {
    try {
      await fmApi.archiveCarrier(volume.id, carrierType)
      volume.carrierType = carrierType
      ElMessage.success(carrierType === 'ELECTRONIC'
        ? '已改为电子载体：执行归档不再强制逐件编页'
        : '已改为纸质载体：执行归档前须逐件填写起页与页数')
    } catch (e) {
      ElMessage.error(e.message)
    }
  })
}

/**
 * 查看卷内文件：清单与取流令牌都取自案卷自己的端点。
 * 不能走 billApi.detail——档案岗不在「单据归属」白名单里，必被 4030 拦死，点了没反应。
 * FileViewer 第三个参数按 fileId 索引，给了 name 就不再打文件域详情接口、给了 tokenProvider 就不打文件域令牌。
 */
async function viewVolumeFiles(row) {
  await runLocked('archive-files-' + row.id, async () => {
    try {
      const files = await fmApi.archiveVolumeFiles(row.id)
      const list = (files || []).filter((f) => f && f.fileId !== null && f.fileId !== undefined)
      if (!list.length) { ElMessage.info(t('archive.main.No')); return }
      const opts = {}
      const ids = []
      list.forEach((f) => {
        if (ids.some((x) => String(x) === String(f.fileId))) return
        ids.push(f.fileId)
        opts[f.fileId] = {
          name: f.fileName || `文件 ${f.fileId}`,
          tokenProvider: (fileId) => fmApi.archiveVolumeFileToken(row.id, fileId),
        }
      })
      fileViewer.value?.open(ids, `卷内文件 - ${archiveRealNo(row) || `档案 ${row.id}`}`, opts)
    } catch (e) { ElMessage.error(e.message) }
  })
}

/** 调阅申请行的「查看」＝只读详情。这里不能挂 openArchive()（那是新建档案目录表单，且不接 row），否则点了查看出来一张空白写单。 */
function viewAccess(row) {
  return showRowDetail({
    id: row.id,
    archive: archiveLabel(row.archiveId),
    accessType: accessTypeLabel(row.accessType),
    accessReason: row.accessReason,
    requester: personLabel(row.requesterName, row.requesterId),
    approvalStatus: accessStatusLabel(row.approvalStatus),
    grantTime: formatDateTime(row.approvedAt || row.accessedAt) || '—',
  }, {
    title: '调阅申请详情',
    labels: { id: '申请编号', archive: '档案', accessType: '调阅类型', accessReason: '申请理由',
      requester: '申请人', approvalStatus: '状态', grantTime: '授权时间' },
  })
}

function openArchive() {
  filingRemote.value = []
  filingPinned.value = []
  arForm.value = { billIds: [], archiveType: 'EXPENSE', carrierType: 'PAPER', archiveYear: new Date().getFullYear(), archivePeriod: '', retentionPeriod: 10 }
  dlgArchive.value = true
}

/** 勾完候选单据再开同一张新建案卷表单：要素填一次，所选单据一次性入卷、各成一件 */
function openArchiveBatch() {
  if (!candSelected.value.length) return
  const rows = [...candSelected.value]
  filingPinned.value = rows
  filingRemote.value = []
  arForm.value = {
    billIds: rows.map((b) => filingOptionId(b)),
    archiveType: 'EXPENSE', carrierType: 'PAPER', archiveYear: new Date().getFullYear(), archivePeriod: '', retentionPeriod: 10,
  }
  dlgArchive.value = true
}

/** 卷内件写入端的入参：选择器值一律字符串，后端要数字主键 */
function toBillIds(vals) {
  return (Array.isArray(vals) ? vals : [])
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0)
}

// 卷内追加件弹窗
const dlgAppend = ref(false)
const appendVolumeId = ref(null)
const appendVolumeNo = ref('')
const appendBillIds = ref([])

async function saveArchive() {
  await guardArchive(async () => {
    const billIds = toBillIds(arForm.value.billIds)
    if (!billIds.length) { ElMessage.warning(t('archive.main.Select2')); return }
    await runLocked('archive-volume-create', async () => {
      try {
        const { archiveType, carrierType, archiveYear, archivePeriod, retentionPeriod } = arForm.value
        const volumeId = await fmApi.archiveVolumeCreate({ archiveType, carrierType, archiveYear, archivePeriod, retentionPeriod, billIds })
        ElMessage.success(`案卷已创建，卷内 ${billIds.length} ${t('archive.main.Items')}`)
        dlgArchive.value = false
        candSelected.value = []
        arForm.value.billIds = []
        await loadArchive()
        loadCandidates()
        // 建完就把这卷的卷内目录摊开：件号是否按选择顺序落下去，当场能核对
        const fresh = archives.value.find((x) => String(x.id) === String(volumeId))
        if (fresh) archiveTable.value?.toggleRowExpansion(fresh, true)
        await loadVolumeItems(volumeId)
      } catch (e) { ElMessage.error(e.message) }
    })
  })
}

async function doArchive(row) {
  await runLocked('archive-do-' + row.id, async () => {
    try {
      // 确认框点的是<b>真档案号</b>（archive_no 优先，编码列只是 184 的镜像），
      // 号缺位时整段换成「该档案（ID …，档案编号尚未生成）」，不再出现「档案「null」？」
      await ElMessageBox.confirm(`确认执行归档${archiveSubject(row)}？将发起凭证归档审批流。`, '执行归档', { type: 'warning' })
      await fmApi.archiveDo(row.id)
      ElMessage.success(t('archive.main.Archiving2'))
      loadArchive()
    } catch (e) {
      if (e === 'cancel' || e === 'close') return // 用户取消，静默
      // 超时≠失败（前端 20s 断连时服务端的流程往往已经起来了），
      // 拦截器已按「已提交，处理中，请稍后刷新」提示；回读列表确认，不引导重复起流程
      if (e?.timeout) { loadArchive(); return }
      ElMessage.error(e.message)
    }
  })
}

/**
 * 内控红线，销毁不可逆：行内的【销毁】不能直调旧「一键销毁」接口，那样会绕过到期校验与
 * FM_DESTROY 双签，未届满档案一键就变 DISPOSED。这里带入该卷打开「发起销毁申请」弹窗——
 * 提交走 ArchiveDestroyService 合规链（到期校验＋双人复核＋FM_DESTROY 起流程），本页不直接落销毁。
 */
function destroyArchive(row) {
  openDestroy()
  selectedDestroyArchives.value = [row]
}

/** 删除空卷（服务端只放行「待归档＋0 件」；非空/已归档由服务端拒并报原文，不在前端假装能删） */
async function deleteEmptyVolume(row) {
  try {
    await ElMessageBox.confirm(`确认删除空案卷${archiveSubject(row)}？该卷没有任何卷内件，删除后不可恢复，单据不受影响。`, '删除空卷', { type: 'warning' })
    await fmApi.archiveVolumeDelete(row.id)
    ElMessage.success(t('archive.main.Empty'))
    loadArchive()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e.message)
  }
}

/**
 * 校验会写库：结论要落 integrity_status，必须走显式 POST，不能用 GET——
 * GET 带写副作用的话，反复点「校验」就会反复写状态、把审计时间列污染（GET 不去重）。
 * 故走 fmApi.archiveVerify（POST /{id}/verify），服务端持久化后回读列表刷新完整性状态列。
 */
async function verifyArchive(row) {
  try {
    const r = await fmApi.archiveVerify(row.id)
    // 三态响应（VERIFIED/MISMATCH/UNVERIFIABLE）随响应返回三句文案；
    // 老包（未发布新服务端）只回布尔时按二态回落，且此时无法区分"未固化哈希"
    const status = r && typeof r === 'object' ? r.status : (r ? 'VERIFIED' : 'MISMATCH')
    row.integrityStatus = status
    if (status === 'VERIFIED') { ElMessage.success(t('archive.main.Integrity')) }
    else if (status === 'UNVERIFIABLE') { ElMessage.warning(t('archive.main.Cannot')) }
    else { ElMessage.error(t('archive.main.Integrity2')) }
    loadArchive()
  } catch (e) { ElMessage.error(e.message) }
}

// 初始化
// 四个页签不能在挂载时并发查询：只有「档案调阅」权限的人一进页面就吃两次
// 「缺少权限：compliance:dispatch:list」红色报错（数据没用到，请求却已经发了）。故按当前页签查询。
// 凭证归档页签同时拉「待立卷候选清单」（无清单权的岗位内部直接 return）
const TAB_LOADERS = {
  access: loadAccess,
  destroy: loadDestroy,
  dispatch: loadDispatch,
  archive: () => (isLibrary.value ? loadArchive() : Promise.all([loadArchive(), loadCandidates()])),
}

async function reload() {
  loading.value = true
  try {
    await (TAB_LOADERS[tab.value] || loadAccess)()
  } finally {
    loading.value = false
  }
}

watch(tab, reload)

async function loadUsers() {
  // 人员下拉 pageSize 取 200 并带 companyId：全表拉取既慢，又会把别家员工带进选择器
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
    users.value = list
  } catch (e) { /* ignore */ }
}

async function loadCompanyTree() {
  try { companyTree.value = await orgApi.companyTree() || [] } catch (e) { /* ignore */ }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadCompanyTree()])
  reload()
})

function exportCsv() {
  if (!dispatches.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'id', label: t('archive.main.No2') }, { prop: 'billId', label: t('archive.main.Document2') }, { prop: 'expenseType', label: t('archive.main.Fee') }, { prop: 'reviewerName', label: t('archive.main.Auditor') }, { prop: 'taskCode', label: t('archive.main.Task') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = dispatches.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ArchiveBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}

/** 档案目录导出（与列表同一批筛选结果，供离职交接／审计取档） */
function exportArchiveCsv() {
  if (!archives.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [
    { label: '档案编号', get: (r) => r.archiveNo },
    { label: '题名', get: (r) => r.title },
    { label: '关联单据', get: (r) => billRefText(r) },
    { label: '类型', get: (r) => archiveTypeLabel(r.archiveType) },
    { label: '年度', get: (r) => r.archiveYear },
    { label: '期间', get: (r) => r.archivePeriod },
    { label: '保管期限', get: (r) => r.retentionYears },
    { label: '状态', get: (r) => archStatusLabel(r.archiveStatus) },
    { label: '完整性', get: (r) => integrityLabel(r.integrityStatus) },
    { label: '立卷时间', get: (r) => formatDateTime(r.filingTime) },
  ]
  const header = cols.map((c) => c.label).join(',')
  const lines = archives.value.map((row) => cols.map((c) => {
    const v = c.get(row); if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${isLibrary.value ? '档案库目录' : '归档整理'}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}


// Show guide on first visit
const _guideKey = 'fssc_guide_archiveCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => archiveCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
