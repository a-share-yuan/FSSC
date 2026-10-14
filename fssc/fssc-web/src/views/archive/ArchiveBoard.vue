<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>FM 档案管理</b>
        <span style="color:#909399; font-size:12px">档案调阅 / 销毁申请 / 单据流转 / 凭证归档 —— 全流程可视化</span>
        <el-button type="info" size="small" style="margin-left:auto" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- ====== 档案调阅 ====== -->
      <el-tab-pane label="档案调阅" name="access">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button type="success" @click="openAccess">发起调阅申请</el-button>
          <el-button type="info" plain @click="loadAccess()">刷新列表</el-button>
          <!-- D-DEC-257：调阅留痕原本只出 PENDING，已批准/已驳回的历史行界面永不可见（库里 3 行只见 1 行）。
               端点已加 status 入参，这里默认「全部状态」把留痕摊开；审批动作仍只对 PENDING 行给入口。 -->
          <el-select v-model="accessStatusFilter" size="small" style="width:130px" @change="loadAccess">
            <el-option label="全部状态" value="ALL" />
            <el-option label="仅待审批" value="PENDING" />
            <el-option label="仅已批准" value="APPROVED" />
            <el-option label="仅已驳回" value="REJECTED" />
          </el-select>
          <!-- RA-D248b：申请人一律以姓名为准，工号列默认隐藏、可勾出（对齐厂商"内码列默认隐藏"的列语义） -->
          <el-checkbox v-model="showUserNameCols" size="small" label="显示工号列" />
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table class="cm-target-board" :data="pagedAccess" size="small" border stripe v-else empty-text="暂无数据" v-loading="loading" @row-dblclick="openTrace">
          <!-- D-DEC-047（12-16:59）：本列的数据源只有裸 archive_id，改前靠"当前 tab 恰好加载过的那页档案"
               本地 join，恒退化成 #ID ⇒ 档案编码列空相。现走档案目录回落（archiveLabel→findArchiveRow），
               出的是真 archive_no（D-DEC-254 定案：号在 archive_no 列，冗余列 archive_code 只是 184 的镜像）。 -->
          <el-table-column label="档案编码" min-width="140" show-overflow-tooltip>
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
                 原先 InlineActions 上另挂了一份 approve/reject 且未传 decision（点通过=驳回），已移除；
                 该组件的 showApprove/showReject **默认为 true**，故必须显式关掉，
                 否则图标仍在＝点了没有处理器的死按钮，且对无 compliance:archiveaccess:approve 的人构成假入口。 -->
            <template #default="{ row }">
              <InlineActions :visible="true" :show-approve="false" :show-reject="false" @view="openArchive(row)" :show-trace="true" @trace="openTrace(row)" />
              <el-button v-if="canApproveAccess && row.approvalStatus === 'PENDING'" link size="small" type="primary" @click="approveAccess(row, true)">批准</el-button>
              <el-button v-if="canApproveAccess && row.approvalStatus === 'PENDING'" link size="small" type="danger" @click="approveAccess(row, false)">驳回</el-button>
              <!-- 批准过后才谈得上授予访问；已实际取阅（accessed_at 落值）则不再给入口 -->
              <el-button v-if="canApproveAccess && row.approvalStatus === BillStatus.APPROVED && !row.accessedAt" link size="small" type="warning" @click="grantAccess(row)">授予</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerAccess" @change="loadAccess" />
      </el-tab-pane>

      <!-- ====== 档案销毁 ====== -->
      <el-tab-pane label="档案销毁" name="destroy">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="success" @click="openDestroy">发起销毁申请</el-button>
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
              <el-button v-if="row.approvalStatus === 'PENDING' || row.approvalStatus == null" link size="small" type="primary" @click="approveDestroy(row, true)">批准</el-button>
              <el-button v-if="row.approvalStatus === 'PENDING' || row.approvalStatus == null" link size="small" type="danger" @click="approveDestroy(row, false)">驳回</el-button>
                <el-button v-if="row.approvalStatus === BillStatus.APPROVED && !row.destroyedAt" link size="small" type="warning" :loading="busy('destroy-exec-' + row.id)" @click="executeDestroy(row)">执行销毁</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerDestroy" @change="loadDestroy" />
      </el-tab-pane>

      <!-- ====== 单据流转 ====== -->
      <el-tab-pane label="单据流转" name="dispatch">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button type="success" @click="openDispatch">发起单据流转</el-button>
          <el-button type="info" plain @click="loadDispatch">刷新</el-button>
      <el-button type="info" size="small" @click="exportCsv">导出 CSV</el-button>
          <span style="color:#909399; font-size:12px">派单 -> 审核组 -> 完成/升级</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedDispatch" size="small" border stripe v-else max-height="420" empty-text="暂无数据" v-loading="loading">
          <el-table-column prop="id" label="编号" width="80"  sortable />
          <el-table-column prop="billId" label="单据标识" min-width="120" show-overflow-tooltip  sortable />
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
              <el-button v-if="row.status !== 'COMPLETED' && row.status !== 'ESCALATED'" link size="small" type="primary" @click="completeDispatch(row)">完成</el-button>
                <el-button v-if="row.status !== 'COMPLETED' && row.status !== 'ESCALATED'" link size="small" type="warning" @click="escalateDispatch(row)">升级</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerDispatch" @change="loadDispatch" />
      </el-tab-pane>

      <!-- ====== 凭证归档 ====== -->
      <el-tab-pane label="凭证归档" name="archive">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-button type="success" @click="openArchive">新建档案目录</el-button>
          <el-button type="info" plain @click="loadArchive">刷新</el-button>
          <span style="color:#909399; font-size:12px">创建档案目录 -> 执行归档 -> 审批闭环</span>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="pagedArchive" size="small" border stripe v-else max-height="420" empty-text="暂无数据" v-loading="loading">
          <!-- D-DEC-047／D-DEC-254 定案：本列绑 archiveNo（op_archive.archive_no，DDL NOT NULL、取号器唯一），
               不绑冗余列 archiveCode——该列在 184 的"编号镜像"上线前整列 NULL，绑它就是"档案编码列全空"。 -->
          <el-table-column prop="archiveNo" label="档案编码" min-width="150" show-overflow-tooltip  sortable />
          <el-table-column label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="archiveTypeTag(row.archiveType)">{{ archiveTypeLabel(row.archiveType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="archiveYear" label="年度" width="80" align="center"  sortable />
          <el-table-column prop="archivePeriod" label="期间" width="90" align="center"  sortable />
          <el-table-column prop="billId" label="关联单据" width="100"  sortable />
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
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.archiveStatus !== BillStatus.ARCHIVED && row.archiveStatus !== 'ARCHIVING'" link size="small" type="primary" :loading="busy('archive-do-' + row.id)" @click="doArchive(row)">执行归档</el-button>
              <el-button v-if="row.archiveStatus !== 'DISPOSED'" link size="small" type="danger" @click="destroyArchive(row)">发起销毁申请</el-button>
              <el-button link size="small" @click="verifyArchive(row)">完整性校验</el-button>
            </template>
          </el-table-column>
        </el-table>
        <TablePager :pager="pagerArchive" @change="loadArchive" />
      </el-tab-pane>
    </el-tabs>

    <!-- ====== 调阅申请弹窗 ====== -->
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

    <!-- ====== 销毁申请弹窗 ====== -->
    <el-dialog v-model="dlgDestroy" title="发起档案销毁申请" width="640px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        档案销毁是不可逆操作，请确认所选档案已过保管期限且经审批后方可执行。
      </el-alert>
      <el-form ref="destroyFormRef" :model="destroyModel" :rules="destroyRules" label-width="100px" size="small">
        <el-form-item label="销毁档案" prop="archiveIds">
          <div style="display:flex; gap:8px; width:100%; align-items:flex-start">
            <div style="flex:1; min-height:32px; padding:4px 8px; border:1px solid #dcdfe6; border-radius:4px; background:#f5f7fa">
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

    <!-- ====== 单据流转弹窗 ====== -->
    <el-dialog v-model="dlgDispatch" title="发起单据流转" width="560px" destroy-on-close>
      <el-form ref="dispatchFormRef" :model="dpForm" :rules="dispatchRules" label-width="100px" size="small">
        <el-form-item label="单据标识" prop="billId">
          <!-- R5-D-202：改前为纯手输框（placeholder 写「业务单据编号」，实际要的却是数字主键，
               用户无从得知填什么）——现从「我的单据」列表选取（billApi.myPage），也允许直接输入数字ID -->
          <el-select
            v-model="dpForm.billId"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="请选择单据（从列表选择；也可直接输入数字单据ID，如 19）"
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

    <!-- ====== 新建档案目录弹窗 ====== -->
    <el-dialog v-model="dlgArchive" title="新建档案目录" width="560px" destroy-on-close>
      <el-form ref="archiveFormRef" :model="arForm" :rules="archiveFormRules" label-width="100px" size="small">
        <el-form-item label="档案类型" prop="archiveType">
          <el-select v-model="arForm.archiveType" style="width:100%">
            <!-- RA-D255a：选项集与 archiveTypeLabel 词表对齐（8 项）。改前只给 4 项，
                 而现网占多数的是 BILL（单据档案）——库里能存、列表能显示、新建却选不到。
                 权威词表由后端白名单把守（ArchiveService.ARCHIVE_TYPES），前端不再自造第 9 个值。 -->
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
        <el-form-item label="关联单据" prop="billId">
          <el-input v-model="arForm.billId" placeholder="关联业务单据编号（可选）" />
        </el-form-item>
        <el-form-item label="年度" prop="archiveYear">
          <el-input-number v-model="arForm.archiveYear" :min="2000" :max="2100" style="width:100%" />
        </el-form-item>
        <el-form-item label="期间" prop="archivePeriod">
          <el-input v-model="arForm.archivePeriod" placeholder="如：2026-08" />
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
        <el-button type="primary" @click="saveArchive">创建档案目录</el-button>
      </template>
    </el-dialog>

    <!-- ====== 档案选择器弹窗（借阅单选 / 销毁多选共用） ====== -->
    <el-dialog v-model="dlgPicker" :title="pickerMode === 'multi' ? '选择档案（可多选）' : '选择档案'" width="780px" append-to-body>
      <div style="margin-bottom:12px; display:flex; gap:8px; align-items:center">
        <!-- D-DEC-256（核实册:325）：检索臂＝卷号/编码/关联单号/标题/类型，本地零命中还会下推到服务端查全量；
             提示语跟着改口径，别再只说"档案编码"（那一列历史上恒空，正是 047/254 的成因）。 -->
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
        :row-key="(r) => r.id"
        ref="pickerTable"
      >
        <el-table-column v-if="pickerMode === 'multi'" type="selection" width="45" />
        <el-table-column v-else label="" width="45" align="center">
          <template #default="{ row }">
            <el-radio :model-value="pickerSingleId" :label="row.id" @change="pickerSingleId = row.id">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <!-- D-DEC-047／D-DEC-254：选择器这张表也绑 archiveNo（真卷号），不是恒空的冗余列 archiveCode -->
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

  <CoachMark ref="archiveCm" :guide-key="_guideKey" :selector="'.cm-target-board'" title="归档看板" text="归档看板展示已归档单据的统计和检索入口。" />
  </div>
</template>

<script setup>

import { onMounted, ref, computed, watch, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveAccessApi, archiveDestroyApi, fmApi, billApi, userApi, orgApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import { useTablePager } from '../../composables/useTablePager'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import TablePager from '../../components/common/TablePager.vue'
import { hasPerm } from '@/utils/perm'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})
const archiveCm = ref(null)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
const TRACE_BIZ_BY_TAB = { access: 'FM_BORROW', destroy: 'FM_DESTROY', dispatch: 'FM_DOC_FLOW', archive: 'FM_ARCHIVE' }
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = TRACE_BIZ_BY_TAB[tab.value] || null
  traceVisible.value = true
}

// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'access')
const loading = ref(false)
const accesses = ref([])
// D-DEC-257：调阅留痕状态筛选（ALL=全部，端点默认值 PENDING 保持不变）
const accessStatusFilter = ref('ALL')
const destroys = ref([])
const dispatches = ref([])
const archives = ref([])
// P0-2: 四个 tab 全部切 server 模式（后端均有分页接口；原 local 会把第一页当全量导致截断）
const pagerAccess = useTablePager({ mode: 'server', storageKey: 'archive_access' })
const pagerDestroy = useTablePager({ mode: 'server', storageKey: 'archive_destroy' })
const pagerDispatch = useTablePager({ mode: 'server', storageKey: 'archive_dispatch' })
const pagerArchive = useTablePager({ mode: 'server', storageKey: 'archive_archive' })
const { pagedData: pagedAccess } = pagerAccess
const { pagedData: pagedDestroy } = pagerDestroy
const { pagedData: pagedDispatch } = pagerDispatch
const { pagedData: pagedArchive } = pagerArchive

// --- 用户与公司数据 ---
const users = ref([])
const companyTree = ref([])
const currentUserId = computed(() => store.user?.userId ?? store.user?.id ?? 0)
const currentUserName = computed(() => store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '当前用户')

// --- 档案选择器 ---
const dlgPicker = ref(false)
const pickerMode = ref('single')
const pickerSearch = ref('')
const pickerTypeFilter = ref('')
const filteredArchives = ref([])
// P0-2: 选择器独立数据源（主表格已分页，当前页不再是全集；打开弹窗时按需拉取）
const pickerSource = ref([])
const pickerSingleId = ref(null)
const pickerMultiSelection = ref([])
const pickerTable = ref(null)

// --- 调阅申请 ---
const dlgAccess = ref(false)
const accForm = ref({ archiveId: null, accessType: 'READ', reason: '' })
const selectedArchiveLabel = computed(() => {
  if (!accForm.value.archiveId) return ''
  // D-DEC-047：走目录回落（archives 只是凭证归档 tab 的当前页）；取不到真号就说档案#ID，不显 null
  const row = findArchiveRow(accForm.value.archiveId)
  const no = archiveRealNo(row)
  if (no) return `${no} (${archiveTypeLabel(row.archiveType)})`
  return `档案#${accForm.value.archiveId}`
})

// --- 销毁申请 ---
const dlgDestroy = ref(false)
const destroyReason = ref('')
const selectedDestroyArchives = ref([])

// --- 单据流转 ---
const dlgDispatch = ref(false)
const dpForm = ref({ billId: '', billType: 'EXPENSE', expenseType: '', amount: 0, legalEntityId: null })
// R5-D-202：单据标识选取源（billApi.myPage＝「我的单据」；只尝试拉一次，失败不阻断手工输数字ID）
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

// --- 凭证归档 ---
const dlgArchive = ref(false)
const arForm = ref({ billId: '', archiveType: 'EXPENSE', archiveYear: new Date().getFullYear(), archivePeriod: '', retentionPeriod: 10 })

// 校验规则唯一口径＝中央登记表（useFormRules）；页面不再本地手写规则对象
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
 * R5-D-200（假失败＋重复起流程）：长耗时写动作（起流程类）在途锁。
 * 提交在飞或慢回执窗口里连点两次，会把同一批档案起两条流程（实证：前端 20s 断连的
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

// ===================== 工具函数 =====================
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function accessTag(s) { return STATUS_TYPE[s] || 'info' }
function accessStatusLabel(s) { return STATUS_LABEL[s] || s || '未知' }

/**
 * 后端对 access_type 无白名单（历史接口写入过 VIEW 等越词值），故未知值原样显示，不并入「复制」。
 * D-DEC-263：VIEW 是 2026-09-24 探针经 /request 写入的历史值（申请弹窗只会产生 READ/COPY），语义与 READ 同为线上查阅；
 * 原先缺键把英文码直出到列表，且 tag 取空串触发 ElementPlus 校验告警。
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
// R5-D-209：销毁终态 NOT_APPLICABLE 独立成像（已按制度销毁 ⇒ 完整性"不适用"），
// 不再与真实篡改的 MISMATCH 混成同一个"不一致"标记
// R5-D-208：UNVERIFIABLE（历史档案未固化哈希，无法校验）独立成像，不再冒充"被篡改"
function integrityLabel(s) {
  if (s === 'NOT_APPLICABLE') return '不适用（已销毁）'
  if (s === 'UNVERIFIABLE') return '无法校验（未固化哈希）'
  return s === 'VERIFIED' ? '已校验' : s === 'MISMATCH' ? '不一致' : '未校验'
}

function billTypeLabel(t) { return ({ EXPENSE: '费用报销', PAYMENT: '采购付款', CORP_PAY: '对公付款', LOAN: '借款' })[t] || t || '—' }

function archiveTypeLabel(t) { return ({ BILL: '单据档案', VOUCHER: '凭证档案', EXPENSE: '报销凭证', PAYMENT: '付款凭证', CONTRACT: '合同', INVOICE: '发票', REPORT: '报表', OTHER: '其他' })[t] || t || '—' }
function archiveTypeTag(t) { return ({ BILL: 'info', VOUCHER: 'info', EXPENSE: '', PAYMENT: 'success', CONTRACT: 'warning', INVOICE: 'danger' })[t] || 'info' }

// ===================== 档案编号显示（D-DEC-047／D-DEC-254 同根） =====================
/**
 * 档案目录缓存：调阅留痕表与销毁申请表上只有裸 archive_id，「档案编码」列此前只能拿
 * archives（＝凭证归档 tab 的<b>当前页</b>，而 D-DEC-266 之后别的 tab 根本不加载它）本地 join，
 * 于是恒退化成「#12」——列上没有过真档案号，这就是 047 记的"档案编码列全空"在调阅/销毁两页的形态。
 * 现按需拉一次档案目录（与选择器同一端点、同一份数据）作为回落源。
 */
const archiveDir = ref([])
// 目录只尝试拉一次：无 compliance:archive:list 的人反复切 tab 会反复吃权限报错（D-DEC-266 同一教训）
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
 * D-DEC-047（12-16:59，沿 D-DEC-254 定案）：取<b>真档案号</b>的唯一口径。
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

// 将档案ID转为可读标签
function archiveLabel(id) {
  const a = findArchiveRow(id)
  if (!a) return `档案#${id}`
  const no = archiveRealNo(a)
  return `${no || `档案#${id}`} (${archiveTypeLabel(a.archiveType)})`
}

// 将用户ID转为用户名
function userLabel(id) {
  if (!id) return '—'
  const u = users.value.find((x) => x.id === id)
  return u ? (u.nickName || u.userName) : `用户#${id}`
}

/**
 * RA-D248b：申请人以**后端富化好的姓名**为主，前端本地 join 只作兜底（历史脏 ID / 姓名缺失）。
 * 工号不混进姓名，另走「显示工号列」开关。
 */
function personLabel(name, id) {
  return name || userLabel(id)
}

/** RA-D248b：工号列默认隐藏，勾出才显示（同一开关管调阅与销毁两张表） */
const showUserNameCols = ref(false)

// 将档案ID数组转为名称数组
function archiveNamesFromIds(ids) {
  // 后端 archive_ids 落的是 JSON 字符串（"[12,24]"），不是数组——原先只判 Array.isArray，本列恒为「—」。
  let list = ids
  if (typeof list === 'string') {
    try { list = JSON.parse(list) } catch { list = list.split(',') }
  }
  if (!Array.isArray(list)) return []
  return list.filter((v) => v !== null && v !== undefined && v !== '').map((id) => {
    // D-DEC-047：销毁申请行上的档案号同样走目录回落（archives 不含别的 tab 的数据），
    // 真号取不到就说档案#ID，不出现 "null"/"undefined"
    const a = findArchiveRow(id)
    return archiveRealNo(a) || `档案#${id}`
  })
}

// ===================== 档案选择器 =====================
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
    // D-DEC-256（核实册:325，与 254 同根）：候选集只是"已经取回的第一页目录"，改前又只认
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
  // D-DEC-256：本地零命中且有关键字 ⇒ 按同一关键字请服务端在全量目录里查一次
  // （/compliance/archive/page 的 keyword 臂与 ArchiveService.search 的 D-DEC-184 检索面共用同一把尺，
  //  但不像 search 那样硬过滤 ARCHIVED ⇒ 刚立的待归档卷，输卷号同样选得到；184 检索面现状不受影响）
  if (kw && !list.length) {
    try {
      const params = { pageNum: 1, pageSize: 50, keyword: pickerSearch.value.trim() }
      if (pickerTypeFilter.value) params.archiveType = pickerTypeFilter.value
      const res = await fmApi.archivePage(params)
      const hits = (res && res.list) || []
      filteredArchives.value = hits
      // 服务端命中并入目录缓存：调阅/销毁两表的 ID→卷号 回落（D-DEC-047）也认得这些卷
      const known = {}
      archiveDir.value.forEach((x) => { known[String(x.id)] = true })
      archiveDir.value = archiveDir.value.concat(hits.filter((x) => !known[String(x.id)]))
    } catch (e) { /* 服务端不可达时保留本地结果，不把选择器变成空白 */ }
  }
}

// P0-2: 打开选择器时按需拉取归档目录（原逻辑复用主表格全量数据，分页后需独立拉取）
async function refreshPickerSource() {
  try {
    const res = await fmApi.archivePage({ pageNum: 1, pageSize: 200 })
    pickerSource.value = res.list || []
  } catch (e) { pickerSource.value = archives.value }
  // D-DEC-047：同一份目录也供调阅/销毁两张表的「档案编码」列回落使用（它们只有裸 archive_id）
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
    if (!pickerSingleId.value) { ElMessage.warning('请选择一个档案'); return }
    accForm.value.archiveId = pickerSingleId.value
  } else {
    if (!pickerMultiSelection.value.length) { ElMessage.warning('请至少选择一个档案'); return }
    selectedDestroyArchives.value = [...pickerMultiSelection.value]
  }
  dlgPicker.value = false
}

function removeDestroyArchive(id) {
  selectedDestroyArchives.value = selectedDestroyArchives.value.filter((x) => x.id !== id)
}

// ===================== 档案调阅 =====================
async function loadAccess(quiet) {
  try {
    await pagerAccess.load(async ({ pageNum, pageSize }) => {
      const r = await archiveAccessApi.pendingPage({ pageNum, pageSize, status: accessStatusFilter.value })
      accesses.value = r.list || []
      return { list: accesses.value, total: r.total || 0 }
    })
    // D-DEC-047：调阅留痕行只有裸 archive_id，「档案编码」列要靠档案目录才能出真号
    //（D-DEC-266 改按 tab 取数后 archives 在本 tab 根本不加载，列就恒退化成 #ID）
    await ensureArchiveDir()
  } catch (e) {
    // D-DEC-444：quiet 严格判 true——本函数被 @click/@change/TablePager 直接绑定时会收到事件对象，
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
      ElMessage.success('调阅申请已提交')
      dlgAccess.value = false
      // D-DEC-444：request 已成功，随后的留痕刷新才撞 compliance:archiveaccess:list 缺权——
      // 原样 ElMessage.error 会把成功报成失败（真跑实测：建单成功＋同秒弹「缺少权限」）。
      // 提交后的刷新属附带动作：失败只明示权限缺口，不改变"已提交"结论。
      try {
        await loadAccess(true)
      } catch (refreshErr) {
        ElMessage.warning(`调阅申请已提交成功；本账号无「调阅留痕」列表查询权限（compliance:archiveaccess:list），列表未刷新（${refreshErr?.message || refreshErr}）`)
      }
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function approveAccess(row, approved) {
  try { await archiveAccessApi.approve(row.id, currentUserId.value, approved); ElMessage.success(approved ? '已批准' : '已驳回'); loadAccess() }
  catch (e) { ElMessage.error(e.message) }
}

async function grantAccess(row) {
  try { await archiveAccessApi.grant(row.id); ElMessage.success('已授予档案访问权限'); loadAccess() }
  catch (e) { ElMessage.error(e.message) }
}

// ===================== 档案销毁 =====================
async function loadDestroy() {
  try {
    await pagerDestroy.load(async ({ pageNum, pageSize }) => {
      const r = await archiveDestroyApi.listPage({ pageNum, pageSize })
      destroys.value = r.list || []
      return { list: destroys.value, total: r.total || 0 }
    })
    await ensureArchiveDir()   // D-DEC-047：销毁申请表的「销毁档案」列出真卷号，同上
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
        ElMessage.success('销毁申请已提交')
        dlgDestroy.value = false
        loadDestroy()
      } catch (e) {
        if (e?.timeout) {
          // R5-D-200：起流程类长写超时≠失败——服务端多半已受理建单（实证 20006ms 断连时 wf_instance 已落）。
          // 拦截器已按「已提交，处理中」口径显式提示；这里收口：关窗＋回读列表，堵住"再点一次"的重复起流程窗口。
          dlgDestroy.value = false
          loadDestroy()
        } else { ElMessage.error(e.message) }
      }
    })
  })
}

async function approveDestroy(row, approved) {
  try { await archiveDestroyApi.approve(row.id, currentUserId.value, approved); ElMessage.success(approved ? '已批准' : '已驳回'); loadDestroy() }
  catch (e) { ElMessage.error(e.message) }
}

async function executeDestroy(row) {
  await runLocked('destroy-exec-' + row.id, async () => {
    try {
      await ElMessageBox.confirm('确认执行销毁该批档案？此操作不可恢复！', '危险操作', { type: 'warning' })
      await archiveDestroyApi.execute(row.id)
      ElMessage.success('销毁已执行')
      loadDestroy()
    } catch (e) {
      if (e === 'cancel' || e === 'close') return // 用户取消，静默
      // R5-D-200：原实现把真实失败也当 cancelled 静默吞掉；超时按「已提交，处理中」口径收口（拦截器已提示），回读列表
      if (e?.timeout) { loadDestroy(); return }
      ElMessage.error(e.message)
    }
  })
}

// ===================== 单据流转 =====================
async function loadDispatch() {
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
      ElMessage.success('单据流转已发起')
      dlgDispatch.value = false
      loadDispatch()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function completeDispatch(row) {
  try { await fmApi.dispatchComplete(row.id); ElMessage.success('派单已完成'); loadDispatch() } catch (e) { ElMessage.error(e.message) }
}

async function escalateDispatch(row) {
  try {
    await ElMessageBox.prompt('请输入升级原因', '升级派单', { inputValue: '' })
      .then(async ({ value }) => { await fmApi.dispatchEscalate(row.id, value || ''); ElMessage.success('已升级'); loadDispatch() })
      .catch(() => {})
  } catch (e) { ElMessage.error(e.message) }
}

// ===================== 凭证归档 =====================
async function loadArchive() {
  try {
    await pagerArchive.load(async ({ pageNum, pageSize }) => {
      const res = await fmApi.archivePage({ pageNum, pageSize })
      archives.value = res.list || []
      return { list: archives.value, total: res.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

function openArchive() {
  arForm.value = { billId: '', archiveType: 'EXPENSE', archiveYear: new Date().getFullYear(), archivePeriod: '', retentionPeriod: 10 }
  dlgArchive.value = true
}

async function saveArchive() {
  await guardArchive(async () => {
    try {
      await fmApi.archiveCreate({ ...arForm.value })
      ElMessage.success('档案目录已创建')
      dlgArchive.value = false
      loadArchive()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function doArchive(row) {
  await runLocked('archive-do-' + row.id, async () => {
    try {
      // D-DEC-047（12-16:59）：确认框点的是<b>真档案号</b>（archive_no 优先，编码列只是 184 的镜像），
      // 号缺位时整段换成「该档案（ID …，档案编号尚未生成）」，不再出现「档案「null」？」
      await ElMessageBox.confirm(`确认执行归档${archiveSubject(row)}？将发起凭证归档审批流。`, '执行归档', { type: 'warning' })
      await fmApi.archiveDo(row.id)
      ElMessage.success('归档已执行，审批流已发起')
      loadArchive()
    } catch (e) {
      if (e === 'cancel' || e === 'close') return // 用户取消，静默
      // R5-D-200：超时≠失败（服务端多半已起流程，实证 20s 断连时 wf_instance 已落），
      // 拦截器已按「已提交，处理中，请稍后刷新」口径提示；回读列表确认，不引导重复起流程
      if (e?.timeout) { loadArchive(); return }
      ElMessage.error(e.message)
    }
  })
}

/**
 * R5-D-197（内控红线，销毁不可逆）：改前行内【销毁】直调旧「一键销毁」接口，会绕过到期校验与
 * FM_DESTROY 双签（实测未届满档案一键变 DISPOSED）。现改为带入该卷打开「发起销毁申请」弹窗——
 * 提交走 ArchiveDestroyService 合规链（到期校验＋双人复核＋FM_DESTROY 起流程），本页不再直落销毁。
 */
function destroyArchive(row) {
  openDestroy()
  selectedDestroyArchives.value = [row]
}

/**
 * R5-D-199（读端点写库）：校验结论会落 integrity_status（写动作），必须走显式 POST——
 * 改前走 GET `/{id}/verify`，而 GET 却带写副作用：反复点「校验」即反复写状态、审计时间列被
 * 污染（GET 不幂等）。现走 api.js 既有 fmApi.archiveVerify（POST /{id}/verify），
 * 服务端持久化后回读列表刷新完整性状态列。
 */
async function verifyArchive(row) {
  try {
    const r = await fmApi.archiveVerify(row.id)
    // R5-D-208：三态回执（VERIFIED/MISMATCH/UNVERIFIABLE）随回执返回三句文案；
    // 老包（未发布新服务端）只回布尔时按二态回落，且此时无法区分"未固化哈希"
    const status = r && typeof r === 'object' ? r.status : (r ? 'VERIFIED' : 'MISMATCH')
    row.integrityStatus = status
    if (status === 'VERIFIED') { ElMessage.success('完整性校验通过') }
    else if (status === 'UNVERIFIABLE') { ElMessage.warning('无法校验：该档案未固化完整性哈希（历史档案），本次不产生篡改结论；建议重新归档后再校验') }
    else { ElMessage.error('完整性校验不一致，档案可能被篡改') }
    loadArchive()
  } catch (e) { ElMessage.error(e.message) }
}

// ===================== 初始化 =====================
// D-DEC-266：原先四个页签在挂载时并发取数，只有「档案调阅」权限的人一进页面就吃两次
// 「缺少权限：compliance:dispatch:list」红色报错（数据没用到，请求却已经发了）。改为按当前页签取数。
const TAB_LOADERS = { access: loadAccess, destroy: loadDestroy, dispatch: loadDispatch, archive: loadArchive }

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
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 500 })).list || []
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
  if (!dispatches.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'id', label: '编号' }, { prop: 'billId', label: '单据标识' }, { prop: 'expenseType', label: '费项' }, { prop: 'reviewerName', label: '审核员' }, { prop: 'taskCode', label: '任务编码' }]
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
  ElMessage.success('已导出 CSV')
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_archiveCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => archiveCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
