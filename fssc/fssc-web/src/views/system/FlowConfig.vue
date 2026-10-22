<template>
  <div class="flow-config-page">
    <el-row class="cm-target-flow" :gutter="16">
      <!-- 左侧：流程模型列表（span 7→6：宽度缩窄 ~14%，栅格最接近"缩 20%"的档位） -->
      <el-col :span="6">
        <el-card class="cm-target-flow model-list-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">{{ $t('system.flow.ttlModels') }}</span>
              <div>
                <el-button v-if="canModelCreate" size="small" @click="importFlow">{{ $t('system.flow.btnImport') }}</el-button>
                <el-button v-if="canModelCreate" type="primary" size="small" @click="openModelCreate">{{ $t('system.common.btnAdd') }}</el-button>
              </div>
            </div>
          </template>
          <el-input v-model="searchKey" :placeholder="$t('system.flow.phSearchModel')" clearable size="small" style="margin-bottom:8px" @input="filterModels" />
          <SkeletonDetail v-if="loading.models" :rows="6" />
          <div class="model-list" v-else>
            <div
              v-for="m in filteredModels"
              :key="m.id"
              class="model-item"
              :class="{ active: selectedModel?.id === m.id }"
              @click="selectModel(m)"
            >
              <div class="model-item-header">
                <span class="model-name">{{ m.modelName }}</span>
                <el-tag size="small" :type="modelStatusType(m)">{{ modelStatusText(m) }}</el-tag>
              </div>
              <div class="model-meta">
                <span>{{ m.modelKey }}</span>
                <span>{{ modelCategoryLabel(m.category) }}</span>
                <span>v{{ m.version }}</span>
              </div>
            </div>
            <el-empty v-if="!filteredModels.length && !loading.models" :description="$t('system.flow.emptyModels')" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：模型详情面板 -->
      <el-col :span="18">
        <!-- 页签被权限裁剪时给出明确原因（原实现直接静默隐藏，用户只看到"残缺页面"误以为功能缺失） -->
        <el-alert v-if="!canNodeList || !canConfigList || !canInstanceList" type="info" :closable="false" show-icon
                  class="perm-hint"
                  :title="$t('system.flow.hintPermTabs')"
                  :description="hiddenTabDesc" />
        <el-card v-if="selectedModel || modelDraft" shadow="never">
          <template #header>
            <div class="panel-header">
              <div class="model-detail-header">
                <span class="panel-title">{{ selectedModel ? selectedModel.modelName : t('system.flow.ttlNewModel') }}</span>
                <el-tag v-if="selectedModel" size="small" type="info" style="margin-left:8px">{{ selectedModel.modelKey }}</el-tag>
                <el-tag v-if="selectedModel && selectedModel.bizType" size="small" type="warning" style="margin-left:4px">{{ bizTypeLabel(selectedModel.bizType) }}</el-tag>
                <el-tag v-if="selectedModel && selectedModel.category" size="small" style="margin-left:4px">{{ modelCategoryLabel(selectedModel.category) }}</el-tag>
                <el-tag v-if="modelDraft" size="small" type="warning" style="margin-left:8px">{{ $t('system.flow.stUnsaved') }}</el-tag>
              </div>
              <div v-if="selectedModel" class="model-actions">
                <el-button v-if="canModelUpdate" size="small" @click="validateModel">{{ $t('system.flow.btnValidate') }}</el-button>
                <el-button v-if="canModelUpdate" size="small" type="warning" @click="simulateModel">{{ $t('system.flow.btnSimulate') }}</el-button>
                <el-button v-if="canModelUpdate" size="small" type="primary" @click="publishModel">{{ $t('system.flow.btnPublish') }}</el-button>
                <el-dropdown v-if="canModelUpdate || canModelDelete" trigger="click" @command="onModelCommand">
                  <el-button size="small">
                    {{ $t('system.flow.btnMoreActions') }}<el-icon class="el-icon--right"><arrow-down /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canModelUpdate" command="inflight">{{ $t('system.flow.btnInflightImpact') }}</el-dropdown-item>
                      <el-dropdown-item v-if="canModelUpdate" command="exportRepo">{{ $t('system.flow.btnExportRepo') }}</el-dropdown-item>
                      <el-dropdown-item v-if="canModelUpdate" command="export">{{ $t('system.flow.btnExport') }}</el-dropdown-item>
                      <el-dropdown-item v-if="canModelDelete" command="delete" divided>{{ $t('system.common.btnDelete') }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>

          <el-tabs v-model="detailTab" type="border-card" @tab-change="onTabChange">
            <!-- 基本信息 -->
            <el-tab-pane v-if="canModelList" :label="$t('system.flow.tabBasic')" name="info">
              <el-form ref="modelFormRef" :model="modelForm" :rules="modelFormRules" label-width="120px" size="default" style="max-width:700px">
                <el-form-item :label="$t('system.flow.colModelCode')" prop="modelKey">
                  <el-input v-model="modelForm.modelKey" :disabled="!!modelForm.id" />
                </el-form-item>
                <el-form-item :label="$t('system.common.colModelName')" prop="modelName">
                  <el-input v-model="modelForm.modelName" />
                </el-form-item>
                <el-form-item :label="$t('system.common.colCategory')">
                  <el-select v-model="modelForm.category" :placeholder="$t('system.flow.phSelectDomain')" style="width:100%">
                    <el-option v-for="(label, code) in WF_MODEL_CATEGORY" :key="code" :label="label" :value="code" />
                  </el-select>
                  <div class="field-hint">{{ $t('system.flow.hintCategoryBrief') }}</div>
                </el-form-item>
                <el-form-item :label="$t('system.flow.lblBizDocType')">
                  <el-select v-model="modelForm.bizType" filterable allow-create default-first-option
                             :placeholder="$t('system.flow.phSelectDocType')" style="width:100%">
                    <el-option v-for="o in bizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                  <div class="field-hint">{{ $t('system.flow.hintDocTypeBrief') }}</div>
                </el-form-item>
                <el-form-item :label="$t('system.flow.lblScope')">
                  <el-select v-model="modelForm.orgScope" style="width:100%">
                    <el-option :label="$t('system.flow.optScopeAll')" value="ALL" />
                    <el-option :label="$t('system.flow.optScopeCompany')" value="ORG" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="$t('system.flow.lblWithdrawable')">
                  <el-switch v-model="modelForm.allowWithdraw" />
                </el-form-item>
                <el-form-item :label="$t('system.flow.lblRejectPolicy')">
                  <el-select v-model="modelForm.rejectStrategy" style="width:100%">
                    <el-option :label="$t('system.flow.optRejectToInitiator')" value="TO_INITIATOR" />
                    <el-option :label="$t('system.flow.optRejectToPrev')" value="TO_PREV" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="$t('system.flow.lblDelegateOn')">
                  <el-switch v-model="modelForm.enableDelegate" />
                </el-form-item>
                <el-form-item>
                  <el-button v-if="modelDraft ? canModelCreate : canModelUpdate" type="primary" :loading="saving" @click="saveModel">{{ modelDraft ? t('system.flow.btnCreateModel') : t('common.save') }}</el-button>
                  <el-button v-if="modelDraft" @click="cancelModelDraft">{{ $t('common.cancel') }}</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 流程图（BPMN 图形）——2026-09-16 补齐：
                 此前本页只有表单/表格，而全站的图形设计器 BpmnDesigner 只被一个从未挂路由的
                 通用页引用（其 isFlow 分支要求 route.path === '/expense/flow'，而该路由由本页渲染）
                 → 图形化流程图在任何入口都打不开。
                 现直接在本页提供：有 bpmnXml 即渲染图形；无 wf:model:update 走只读 Viewer。 -->
            <el-tab-pane v-if="canModelList && !modelDraft" :label="$t('system.flow.tabDiagram')" name="diagram">
              <template v-if="selectedModel">
                <!-- 无图形定义 + 无编辑权：给明确说明，不渲染假图 -->
                <el-alert v-if="!selectedModel.bpmnXml && !canModelUpdate" type="warning" :closable="false" show-icon
                          :title="$t('system.flow.emptyDiagram')"
                          :description="$t('system.flow.hintNeedModelPerm')" />
                <template v-else>
                  <div class="diagram-tip">
                    <span>{{ $t('system.extInterface.colModel') }}<b>{{ selectedModel.modelKey }}</b> · v{{ selectedModel.version }} · {{ modelStatusText(selectedModel) }}
                      <span v-if="!canModelUpdate" class="ro">{{ $t('system.flow.stReadOnly') }}</span>
                    </span>
                    <el-button size="small" :type="isDiagramFullscreen ? 'warning' : 'default'" @click="toggleDiagramFullscreen">
                      {{ isDiagramFullscreen ? t('system.flow.btnExitFullscreen') : t('system.flow.btnFullscreenDesign') }}
                    </el-button>
                  </div>
                  <div ref="diagramWrapRef" class="diagram-wrap">
                    <BpmnDesigner ref="bpmnRef" :key="selectedModel.id || selectedModel.modelKey"
                                  :model-value="selectedModel.bpmnXml || ''"
                                  :readonly="!canModelUpdate"
                                  @saved="onBpmnSaved" />
                  </div>
                </template>
              </template>
              <el-empty v-else :description="$t('system.flow.hintPickModelDiagram')" :image-size="70" />
            </el-tab-pane>

            <!-- 节点配置 -->
            <el-tab-pane v-if="canNodeList && !modelDraft" :label="$t('system.flow.tabNodes')" name="nodes">
              <div style="margin-bottom:12px">
                <el-button v-if="canNodeCreate" type="primary" size="small" @click="openNodeDialog(null)">{{ $t('system.flow.btnAddNode') }}</el-button>
              </div>
              <el-alert v-if="hasMultiInstanceNode" type="warning" :closable="false" show-icon style="margin-bottom:10px"
                        :title="$t('system.flow.hintMultistageRepublish')" />
              <SkeletonList v-if="loading.nodes" :rows="8" :cols="5" />
              <el-table class="cm-target-flow" :data="nodes" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="nodeKey" :label="$t('system.flow.colNodeCode')" width="90" />
                <el-table-column prop="nodeName" :label="$t('system.flow.colNodeName')" min-width="140" />
                <el-table-column prop="assignType" :label="$t('system.flow.lblApproverType')" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="assignTypeTag(row.assignType)">{{ assignTypeLabel(row.assignType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="handlingMode" :label="$t('system.flow.lblAssignMode')" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="handlingModeTag(row.handlingMode)">
                      {{ handlingModeLabel(row.handlingMode) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="multiType" :label="$t('system.flow.lblCountersign')" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="multiTypeTag(row.multiType)">
                      {{ multiTypeLabel(row.multiType) }}
                    </el-tag>
                    <span v-if="row.multiType === 'VOTE'" style="font-size:12px;color:#909399">
                      ≥{{ row.voteRatio == null ? 50 : row.voteRatio }}%
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="assignValue" :label="$t('system.flow.lblApproverValue')" min-width="130" show-overflow-tooltip />
                <el-table-column prop="timeoutHours" :label="$t('system.flow.colTimeoutH')" width="80" />
                <el-table-column prop="timeoutAction" :label="$t('system.flow.lblTimeoutAction')" width="120">
                  <template #default="{ row }">{{ timeoutActionLabel(row.timeoutAction) }}</template>
                </el-table-column>
                <el-table-column prop="upLevel" :label="$t('system.flow.lblEscalateLevels')" width="90">
                  <template #default="{ row }">
                    <span v-if="row.assignType === 'DEPT_LEADER_UP' || isEscalateAction(row.timeoutAction) || row.emptyStrategy === 'ESCALATE'">
                      {{ upLevelLabel(row.upLevel) }}
                    </span>
                    <span v-else style="color:#c0c4cc">—</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('common.operation')" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canNodeUpdate" link type="primary" size="small" @click="openNodeDialog(row)">{{ $t('system.common.btnEdit') }}</el-button>
                    <el-button v-if="canNodeDelete" link type="danger" size="small" @click="deleteNode(row)">{{ $t('system.common.btnDelete') }}</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 流程绑定 -->
            <el-tab-pane v-if="canConfigList && !modelDraft" :label="$t('system.flow.tabBindings')" name="bindings">
              <div style="margin-bottom:12px">
                <el-button v-if="canConfigCreate" type="primary" size="small" @click="openBindingDialog(null)">{{ $t('system.flow.btnAddBinding') }}</el-button>
              </div>
              <SkeletonList v-if="loading.bindings" :rows="8" :cols="5" />
              <el-table :data="bindings" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="bizType" :label="$t('field.billType')" width="120">
                  <template #default="{ row }">{{ flowTypeLabel(row.bizType) }}</template>
                </el-table-column>
                <el-table-column prop="configName" :label="$t('system.flow.colCfgName')" min-width="140" />
                <el-table-column prop="modelKey" :label="$t('system.flow.colModelKey', '流程模型')" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.modelKey">{{ modelLabel(row.modelKey) }}</span>
                    <el-tag v-else size="small" type="danger">未设模型</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="subType" :label="$t('system.flow.colSubType', '单据子类')" width="110">
                  <template #default="{ row }">{{ row.subType || '全部' }}</template>
                </el-table-column>
                <el-table-column prop="amountMin" :label="$t('system.flow.colAmountMin')" width="100" :formatter="(r,c,v) => formatMoney(v)" />
                <el-table-column prop="amountMax" :label="$t('system.flow.colAmountMax')" width="100" :formatter="(r,c,v) => formatMoney(v)" />
                <el-table-column prop="priority" :label="$t('system.flow.colPriority')" width="80" />
                <el-table-column prop="status" :label="$t('common.status')" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status === 'NORMAL' ? t('system.common.btnEnable') : t('system.common.btnDeactivate') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('common.operation')" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canConfigUpdate" link type="primary" size="small" @click="openBindingDialog(row)">{{ $t('system.common.btnEdit') }}</el-button>
                    <el-button v-if="canConfigDelete" link type="danger" size="small" @click="deleteBinding(row)">{{ $t('system.common.btnDelete') }}</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 全局配置 -->
            <el-tab-pane v-if="canConfigList && !modelDraft" :label="$t('system.flow.tabGlobalCfg')" name="configs">
              <el-radio-group v-model="cfgType" size="small" style="margin-bottom:10px" @change="loadConfigs">
                <el-radio-button value="THRESHOLD">{{ $t('system.flow.tabAmountRule') }}</el-radio-button>
                <el-radio-button value="TIMEOUT">{{ $t('system.flow.tabTimeout') }}</el-radio-button>
                <el-radio-button value="ACTION">{{ $t('system.flow.tabActions') }}</el-radio-button>
                <el-radio-button value="INTEGRATION">{{ $t('system.flow.tabIntegration') }}</el-radio-button>
              </el-radio-group>
              <div style="margin-bottom:12px">
                <el-button v-if="canConfigCreate" type="primary" size="small" @click="openConfigDialog(null)">{{ $t('system.flow.ttlNewConfig') }}</el-button>
              </div>
              <SkeletonList v-if="loading.configs" :rows="8" :cols="5" />
              <el-table :data="configs" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="cfgCode" :label="$t('system.common.colCode')" width="120" />
                <el-table-column prop="cfgName" :label="$t('system.common.colName')" min-width="140" />
                <el-table-column prop="cfgJson" :label="$t('system.flow.colCfgContent')" min-width="240" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span>{{ cfgSummary(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" :label="$t('common.status')" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status === 'NORMAL' ? t('system.common.stNormal') : t('system.common.btnDeactivate') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('common.operation')" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canConfigUpdate" link type="primary" size="small" @click="openConfigDialog(row)">{{ $t('system.common.btnEdit') }}</el-button>
                    <el-button v-if="canConfigDelete" link type="danger" size="small" @click="deleteConfig(row)">{{ $t('system.common.btnDelete') }}</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 运行实例 -->
            <el-tab-pane v-if="canInstanceList && !modelDraft" :label="$t('system.flow.tabInstances')" name="instances">
              <SkeletonList v-if="loading.instances" :rows="8" :cols="5" />
              <el-table :data="instances" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="id" :label="$t('system.flow.colInstanceId')" width="120" />
                <el-table-column prop="bizType" :label="$t('system.common.colBizType')" width="110">
                  <template #default="{ row }">{{ flowTypeLabel(row.bizType) }}</template>
                </el-table-column>
                <el-table-column prop="bizNo" :label="$t('system.flow.colDocNo')" width="140" />
                <el-table-column prop="status" :label="$t('common.status')" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="instanceStatusTag(row.status)">{{ instanceStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="currentNode" :label="$t('system.flow.colCurrentNode')" width="120" />
                <el-table-column prop="initiatorName" :label="$t('system.flow.colInitiator')" width="100" />
                <el-table-column prop="startTime" :label="$t('system.flow.colStartedAt')" width="160"
                                 :formatter="(row, col) => formatDateTime(row[col.property])" />
              </el-table>
              <el-pagination
                v-model:current-page="instPageNum"
                v-model:page-size="instPageSize"
                :total="instTotal"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                style="margin-top: 12px; justify-content: flex-end;"
                @size-change="handleInstSizeChange"
                @current-change="handleInstCurrentChange"
              />
            </el-tab-pane>

            <!-- 委托代理 -->
            <el-tab-pane v-if="canDelegateList && !modelDraft" :label="$t('system.flow.tabDelegation')" name="delegates">
              <div style="margin-bottom:12px">
                <el-button v-if="canDelegateCreate" type="primary" size="small" @click="openDelegateDialog(null)">{{ $t('system.flow.btnAddDelegate') }}</el-button>
              </div>
              <SkeletonList v-if="loading.delegates" :rows="8" :cols="5" />
              <el-table :data="delegates" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="fromUserName" :label="$t('system.flow.colDelegator')" width="120" />
                <el-table-column prop="toUserName" :label="$t('system.flow.colDelegate')" width="120" />
                <el-table-column prop="bizTypes" :label="$t('system.flow.colAppliesTo')" min-width="180">
                  <template #default="{ row }">{{ delegateScopeLabel(row.bizTypes) }}</template>
                </el-table-column>
                <el-table-column prop="beginTime" :label="$t('system.flow.colStartAt')" width="150"
                                 :formatter="(row, col) => formatDateTime(row[col.property])" />
                <el-table-column prop="endTime" :label="$t('system.flow.colEndAt')" width="150"
                                 :formatter="(row, col) => formatDateTime(row[col.property])" />
                <el-table-column prop="status" :label="$t('common.status')" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : row.status === 'EXPIRED' ? 'info' : 'danger'">{{ row.status === 'NORMAL' ? t('system.common.btnEnable') : row.status === 'EXPIRED' ? t('system.flow.stExpired') : t('system.common.btnDeactivate') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('common.operation')" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canDelegateUpdate" link type="danger" size="small" @click="cancelDelegate(row)">{{ $t('common.cancel') }}</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                v-model:current-page="dlgPageNum"
                v-model:page-size="dlgPageSize"
                :total="dlgTotal"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                style="margin-top: 12px; justify-content: flex-end;"
                @size-change="handleDlgSizeChange"
                @current-change="handleDlgCurrentChange"
              />
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <el-card v-else shadow="never">
          <el-empty v-if="canAnyDetail" :description="$t('system.flow.hintPickModel')" :image-size="80" />
          <el-empty v-else :description="$t('system.flow.hintViewOnly')" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 模型新增/编辑弹窗已内联在基本信息Tab，此处省略 -->

    <!-- 节点编辑弹窗：按业务分组＋短提示，避免单页过长 -->
    <el-dialog v-model="nodeDialog.visible" :title="nodeDialog.editing ? t('system.flow.dlgEditNode') : t('system.flow.btnAddNode')"
               width="760px" top="6vh" destroy-on-close class="node-dialog">
      <el-form ref="nodeFormRef" :model="nodeDialog.form" :rules="nodeFormRules" label-width="104px" size="default">
        <div class="form-section-title">{{ $t('system.flow.secNodeBasic') }}</div>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item :label="$t('system.flow.colNodeCode')" prop="nodeKey">
              <el-input v-model="nodeDialog.form.nodeKey" :placeholder="$t('system.flow.phNodeCode')" />
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item :label="$t('system.flow.colNodeName')" prop="nodeName">
              <el-input v-model="nodeDialog.form.nodeName" :placeholder="$t('system.flow.phNodeName')" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">{{ $t('system.flow.secApprover') }}</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblApproverType')">
              <el-select v-model="nodeDialog.form.assignType" style="width:100%" @change="onAssignTypeChange">
                <el-option :label="$t('system.flow.optAssigneeUser')" value="USER" />
                <el-option :label="$t('system.flow.optAssigneeRole')" value="ROLE" />
                <el-option :label="$t('system.flow.colInitiator')" value="INITIATOR" />
                <el-option :label="$t('system.flow.optAssigneeChain')" value="DEPT_LEADER_UP" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintApproverTypeBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12"
                  v-if="nodeDialog.form.assignType !== 'INITIATOR' && nodeDialog.form.assignType !== 'DEPT_LEADER_UP'">
            <el-form-item :label="$t('system.flow.lblApproverValue')" prop="assignValue">
              <el-input v-model="nodeDialog.form.assignValue" :placeholder="assignValuePlaceholder" />
              <div class="field-hint">{{ assignValueHint }}</div>
              <RoleHolderHint v-if="nodeDialog.form.assignType === 'ROLE'" :role-code="firstAssignRole" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblAssignMode')">
              <el-select v-model="nodeDialog.form.handlingMode" style="width:100%">
                <el-option :label="$t('system.flow.optPickAuto')" value="AUTO" />
                <el-option :label="$t('system.flow.optPickPrev')" value="PICK"
                           :disabled="nodeDialog.form.assignType !== 'ROLE'" />
                <el-option :label="$t('system.flow.optPickPool')" value="POOL"
                           :disabled="nodeDialog.form.assignType !== 'ROLE'" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintAssignModeBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblCountersign')">
              <el-select v-model="nodeDialog.form.multiType" style="width:100%">
                <el-option :label="$t('system.flow.optCsAny')" value="ANY" />
                <el-option :label="$t('system.flow.optCsAll')" value="ALL" />
                <el-option :label="$t('system.flow.optCsSequential')" value="SEQ" />
                <el-option :label="$t('system.flow.optCsVote')" value="VOTE" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintCountersignBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="nodeDialog.form.multiType === 'VOTE'" :label="$t('system.flow.lblVoteRatio')">
          <el-input-number v-model="nodeDialog.form.voteRatio" :min="1" :max="100" :step="5" style="width:100%" />
          <div class="field-hint">{{ $t('system.flow.hintVoteBrief') }}</div>
        </el-form-item>

        <div class="form-section-title">{{ $t('system.flow.secTimeout') }}</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblTimeoutHours')">
              <el-input-number v-model="nodeDialog.form.timeoutHours" :min="0" :step="1" style="width:100%" />
              <div class="field-hint">{{ $t('system.flow.hintTimeoutHoursBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblTimeoutAction')">
              <el-select v-model="nodeDialog.form.timeoutAction" style="width:100%">
                <el-option :label="$t('system.flow.optActRemind')" value="NOTIFY" />
                <el-option :label="$t('system.flow.optRemindEscalate')" value="ESCALATE_AFTER_URGES" />
                <el-option :label="$t('system.flow.optTimeoutAuto')" value="AUTO_PASS" />
                <el-option :label="$t('system.flow.optTimeoutEscalate')" value="ESCALATE" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintTimeoutActionBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"
                  v-if="nodeDialog.form.assignType === 'DEPT_LEADER_UP' || isEscalateAction(nodeDialog.form.timeoutAction) || nodeDialog.form.emptyStrategy === 'ESCALATE'">
            <el-form-item :label="$t('system.flow.lblEscalateLevels')">
              <el-input-number v-model="nodeDialog.form.upLevel" :min="0" :max="10" :step="1" style="width:100%" />
              <div class="field-hint">{{ $t('system.flow.hintUpLevelBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblEmptyPolicy')">
              <el-select v-model="nodeDialog.form.emptyStrategy" style="width:100%" :placeholder="$t('system.flow.lblEmptyDefault')">
                <el-option :label="$t('system.flow.optEmptyBlock')" value="ERROR" />
                <el-option :label="$t('system.flow.optEmptyEscalate')" value="ESCALATE" />
                <el-option :label="$t('system.flow.optEmptySkip')" value="SKIP" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintEmptyBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">{{ $t('system.flow.secPermissions') }}</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblAllowTransfer')">
              <el-switch v-model="nodeDialog.form.allowTransfer" :active-value="1" :inactive-value="0" />
              <div class="field-hint">{{ $t('system.flow.hintTransferBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblAllowAddSign')">
              <el-switch v-model="nodeDialog.form.allowAddsign" :active-value="1" :inactive-value="0" />
              <div class="field-hint">{{ $t('system.flow.hintAddSignBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblSubmitDone')">
              <el-select v-model="nodeDialog.form.firstNodeAutoPass" style="width:100%" clearable
                         :placeholder="$t('system.flow.optLegacyJudge')">
                <el-option :label="$t('system.flow.optSubmitDone')" :value="1" />
                <el-option :label="$t('system.flow.optManualRequired')" :value="0" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintFirstNodeBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">{{ $t('system.flow.secCcReject') }}</div>
        <el-form-item :label="$t('system.flow.lblCcAfter')">
          <el-input v-model="nodeDialog.form.ccUsers" :placeholder="$t('system.flow.phCcUserIds')" />
          <div class="field-hint">{{ $t('system.flow.hintCcUsersBrief') }}</div>
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblCcRoles')">
          <el-input v-model="nodeDialog.form.ccRoles" :placeholder="$t('system.flow.phCcRoleCodes')" />
          <div class="field-hint">{{ $t('system.flow.hintCcRolesBrief') }}</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblRejectRange')">
              <el-select v-model="nodeDialog.form.rejectScope" style="width:100%">
                <el-option :label="$t('system.flow.optRejectNoTarget')" value="NONE" />
                <el-option :label="$t('system.flow.optRejectPrev')" value="PREV_ONE" />
                <el-option :label="$t('system.flow.optRejectAnyDone')" value="ANY_DONE" />
              </el-select>
              <div class="field-hint">{{ $t('system.flow.hintRejectBrief') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('system.flow.lblResubmitBack')">
              <el-switch v-model="nodeDialog.form.resubmitJumpBack" :active-value="1" :inactive-value="0" />
              <div class="field-hint">{{ $t('system.flow.hintResubmitBrief') }}</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="nodeDialog.saving" @click="saveNode">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 绑定编辑弹窗 -->
    <el-dialog v-model="bindingDialog.visible" :title="bindingDialog.editing ? t('system.flow.dlgEditBinding') : t('system.flow.btnAddBinding')" width="600px" destroy-on-close>
      <el-form ref="bindingFormRef" :model="bindingDialog.form" :rules="bindingFormRules" label-width="120px" size="default">
        <el-form-item :label="$t('field.billType')" prop="bizType">
          <el-select v-model="bindingDialog.form.bizType" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="o in bindingBizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="field-hint">{{ $t('system.flow.hintBizTypeBrief') }}</div>
        </el-form-item>
        <el-form-item :label="$t('system.flow.colCfgName')" prop="configName"><el-input v-model="bindingDialog.form.configName" /></el-form-item>
        <el-form-item :label="$t('system.flow.lblBindModel')">
          <el-input :model-value="selectedModel?.modelKey" disabled />
        </el-form-item>
        <el-form-item :label="$t('system.flow.colSubType', '单据子类')">
          <el-input v-model="bindingDialog.form.subType" :placeholder="$t('system.flow.phSubType')" />
          <div class="field-hint">{{ $t('system.flow.hintSubTypeRoute') }}</div>
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblAmountMinIncl')">
          <el-input-number v-model="bindingDialog.form.amountMin" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblAmountMaxExcl')">
          <el-input-number v-model="bindingDialog.form.amountMax" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.colPriority')">
          <el-input-number v-model="bindingDialog.form.priority" :min="0" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-select v-model="bindingDialog.form.status" style="width:100%">
            <el-option :label="$t('system.common.btnEnable')" value="NORMAL" />
            <el-option :label="$t('system.common.btnDeactivate')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="bindingDialog.saving" @click="saveBinding">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 全局配置编辑弹窗 -->
    <el-dialog v-model="configDialog.visible" :title="configDialog.editing ? t('system.flow.dlgEditConfig') : t('system.flow.ttlNewConfig')" width="600px" destroy-on-close>
      <el-form ref="configFormRef" :model="configDialog.form" :rules="configFormRules" label-width="120px" size="default">
        <el-form-item :label="$t('system.flow.colCfgType')" prop="cfgType">
          <el-select v-model="configDialog.form.cfgType" style="width:100%" @change="onCfgTypeChange">
            <el-option :label="$t('system.flow.tabAmountRule')" value="THRESHOLD" />
            <el-option :label="$t('system.flow.tabTimeout')" value="TIMEOUT" />
            <el-option :label="$t('system.flow.tabActions')" value="ACTION" />
            <el-option :label="$t('system.flow.tabIntegration')" value="INTEGRATION" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.flow.colCfgCode')" prop="cfgCode"><el-input v-model="configDialog.form.cfgCode" /></el-form-item>
        <el-form-item :label="$t('system.flow.colCfgName')" prop="cfgName"><el-input v-model="configDialog.form.cfgName" /></el-form-item>

        <!-- ===== 按配置类型动态渲染结构化表单 ===== -->
        <!-- 金额阈值 -->
        <template v-if="configDialog.form.cfgType === 'THRESHOLD'">
          <el-form-item :label="$t('system.flow.lblAmountMinIncl')">
            <el-input-number v-model="configDialog.cfgData.min" :min="0" :precision="2" :step="1000" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblAmountMaxExcl')">
            <el-input-number v-model="configDialog.cfgData.max" :min="0" :precision="2" :step="1000" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblApproveLevel')">
            <el-input-number v-model="configDialog.cfgData.level" :min="1" :max="10" :step="1" style="width:100%" />
            <div class="cfg-hint">{{ $t('system.flow.hintLevelLonger') }}</div>
          </el-form-item>
        </template>

        <!-- 超时处理 -->
        <template v-else-if="configDialog.form.cfgType === 'TIMEOUT'">
          <el-form-item :label="$t('system.flow.lblTimeoutHours')">
            <el-input-number v-model="configDialog.cfgData.timeoutHours" :min="1" :max="720" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblTimeoutAction')">
            <!-- 全局规则走 WfTimeoutJob.handle 的 targetRole 分支；AUTO_APPROVE 无任何消费方，已摘除。
                 注意：AUTO_ESCALATE/REDISTRIBUTE 必须填目标角色编码才有效果（空角色=只留日志） -->
            <el-select v-model="configDialog.cfgData.action" style="width:100%">
              <el-option :label="$t('system.flow.optNotifyOwner')" value="NOTIFY" />
              <el-option :label="$t('system.flow.optAutoEscalate')" value="AUTO_ESCALATE" />
              <el-option :label="$t('system.flow.optAutoReassign')" value="REDISTRIBUTE" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="configDialog.cfgData.action === 'AUTO_ESCALATE' || configDialog.cfgData.action === 'REDISTRIBUTE'" :label="$t('system.flow.lblTargetRoleCode')">
            <el-input v-model="configDialog.cfgData.targetRole" :placeholder="$t('system.flow.phTargetRoleCode')" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblNotifyUsers')">
            <el-input v-model="configDialog.cfgData.notifyUsers" :placeholder="$t('system.flow.phNotifyUsers')" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblRemindBefore')">
            <el-input-number v-model="configDialog.cfgData.remindBefore" :min="0" :max="48" :step="1" style="width:100%" />
            <div class="cfg-hint">{{ $t('system.flow.hintRemindHours') }}</div>
          </el-form-item>
        </template>

        <!-- 审批动作 -->
        <template v-else-if="configDialog.form.cfgType === 'ACTION'">
          <el-form-item :label="$t('system.flow.tabActions')">
            <el-select v-model="configDialog.cfgData.action" style="width:100%">
              <el-option :label="$t('field.approve')" value="APPROVE" />
              <el-option :label="$t('field.reject')" value="REJECT" />
              <el-option :label="$t('system.flow.optActionTransfer')" value="TRANSFER" />
              <el-option :label="$t('system.flow.optActionAddSign')" value="ADDSIGN" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.flow.optActionAutoApprove')">
            <el-switch v-model="configDialog.cfgData.autoApprove" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblWithdrawable')">
            <el-switch v-model="configDialog.cfgData.allowWithdraw" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblDefaultRemark')">
            <el-input v-model="configDialog.cfgData.comment" type="textarea" :rows="2" :placeholder="$t('system.flow.phDefaultRemark')" />
          </el-form-item>
        </template>

        <!-- 外部集成 -->
        <template v-else-if="configDialog.form.cfgType === 'INTEGRATION'">
          <el-form-item :label="$t('system.flow.lblSystemName')">
            <el-input v-model="configDialog.cfgData.systemName" :placeholder="$t('system.flow.phSystemName')" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblEndpoint')">
            <el-input v-model="configDialog.cfgData.apiUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblHttpMethod')">
            <el-select v-model="configDialog.cfgData.method" style="width:100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblRetryCount')">
            <el-input-number v-model="configDialog.cfgData.retryTimes" :min="0" :max="10" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$t('system.flow.lblTimeoutSeconds')">
            <el-input-number v-model="configDialog.cfgData.timeoutSeconds" :min="1" :max="300" :step="1" style="width:100%" />
          </el-form-item>
        </template>

        <!-- 未匹配的配置类型回退到 JSON textarea -->
        <el-form-item v-else :label="$t('system.flow.lblConfigJson')">
          <el-input v-model="configDialog.form.cfgJson" type="textarea" :rows="6" :placeholder="$t('system.flow.phCfgJsonFallback')" />
        </el-form-item>

        <el-form-item :label="$t('common.status')">
          <el-select v-model="configDialog.form.status" style="width:100%">
            <el-option :label="$t('system.common.stNormal')" value="NORMAL" />
            <el-option :label="$t('system.common.btnDeactivate')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="configDialog.saving" @click="saveConfig">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 委托代理弹窗 -->
    <el-dialog v-model="delegateDialog.visible" :title="$t('system.flow.ttlNewDelegate')" width="600px" destroy-on-close>
      <el-form ref="delegateFormRef" :model="delegateDialog.form" :rules="delegateFormRules" label-width="120px" size="default">
        <el-form-item :label="$t('system.flow.colDelegator')">
          <!-- 后端硬校验「委托人必须是当前登录用户」⇒ 系统带出只读值，按 Q1 口径不加红星 -->
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>
        <el-form-item :label="$t('system.flow.colDelegate')" prop="toUserId">
          <!-- P0 修复（2026-10-21）：默认只加载同公司人员 -->
          <UserSelect v-model="delegateDialog.form.toUserId" scope="same-company" show-dept />
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblApplyDocTypes')">
          <el-input v-model="delegateDialog.form.bizTypes" :placeholder="$t('system.flow.phApplyDocTypes')" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.colStartAt')" prop="beginTime">
          <!-- 后端 Jackson 只认 ISO 'T' 分隔，空格格式会 500（同 MyDelegate.vue） -->
          <el-date-picker v-model="delegateDialog.form.beginTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.colEndAt')" prop="endTime">
          <el-date-picker v-model="delegateDialog.form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblDelegateReason')">
          <el-input v-model="delegateDialog.form.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="delegateDialog.saving" @click="saveDelegate">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 校验结果弹窗 -->
    <el-dialog v-model="checkResult.visible" :title="$t('system.flow.ttlCheckResult')" width="500px">
      <el-alert v-if="checkResult.data?.valid" type="success" :closable="false" :title="$t('system.flow.stCheckPass')" />
      <el-alert v-else type="error" :closable="false" :title="$t('system.flow.stCheckFail')" :description="checkFailDesc" />
    </el-dialog>

    <!-- 模拟结果弹窗：先给结论，再给审批路径和节点明细 -->
    <el-dialog v-model="simResult.visible" :title="$t('system.flow.ttlSimulate')" width="720px" top="6vh">
      <el-form :model="simResult" :rules="simRules" label-width="80px" inline class="sim-query">
        <el-form-item :label="$t('system.flow.lblSimAmount')">
          <el-input-number v-model="simResult.amount" :min="0" :precision="2" style="width:150px" />
        </el-form-item>
        <el-form-item :label="$t('system.flow.lblExpenseItem')">
          <el-input v-model="simResult.itemName" :placeholder="$t('system.flow.optNullable')" style="width:150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="simResult.running" @click="runSimulate">{{ $t('system.flow.btnRunSim') }}</el-button>
        </el-form-item>
      </el-form>

      <template v-if="simResult.ran">
        <el-alert v-if="simResult.errors.length" type="error" :closable="false" show-icon
                  :title="$t('system.flow.simHasErrors')" :description="simResult.errors.join('；')" />
        <el-alert v-else type="success" :closable="false" show-icon
                  :title="$t('system.flow.simSummary', { v1: simAmountText, v2: simApprovalCount, v3: simResult.trace.length })" />
        <el-alert v-if="simResult.warnings.length" type="warning" :closable="false" show-icon class="sim-warn"
                  :title="$t('system.flow.simWarnings')" :description="simResult.warnings.join('；')" />

        <template v-if="simResult.trace.length">
          <div class="sim-section-title">{{ $t('system.flow.simPathTitle') }}</div>
          <div class="sim-path">
            <template v-for="(t, i) in simResult.trace" :key="i">
              <span v-if="i" class="sim-arrow">→</span>
              <el-tag :type="simTagType(t.type)" effect="light" class="sim-path-node">{{ t.nodeName }}</el-tag>
            </template>
          </div>

          <div class="sim-section-title">{{ $t('system.flow.simDetailTitle') }}</div>
          <el-table :data="simResult.trace" size="small" border>
            <el-table-column type="index" :label="$t('system.flow.colStep')" width="56" align="center" />
            <el-table-column :label="$t('system.flow.colNodeType')" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="simTagType(row.type)">{{ simTypeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nodeName" :label="$t('system.flow.colNodeName')" min-width="150" show-overflow-tooltip />
            <el-table-column :label="$t('system.flow.colStepDesc')" min-width="220">
              <template #default="{ row }">
                <template v-if="row.type === 'task'">
                  <span v-if="row.assignee">{{ $t('system.flow.simApproverLabel', { v1: row.assignee }) }}</span>
                  <span v-else class="sim-muted">{{ $t('system.flow.simNoApprover') }}</span>
                </template>
                <span v-else>{{ row.tip }}</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else :description="$t('system.flow.msgSimNoPath')" :image-size="60" />
      </template>
      <el-empty v-else :description="$t('system.flow.emptySimResult')" :image-size="60" />
    </el-dialog>

    <!-- G3-3/G3-4 发布影响面：在途旧实例清单 + 发布 diff 记录（抽屉，不挤占配置页） -->
    <el-drawer v-model="inFlight.visible" :title="$t('system.flow.ttlPublishImpact', { v1: inFlight.modelName || '' })" size="60%" direction="rtl">
      <el-tabs v-model="inFlight.tab">
        <el-tab-pane v-if="canInstanceList" :label="$t('system.flow.lblStaleInstances')" name="inflight">
          <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
            :title="$t('system.flow.alertInFlightStale', { v1: inFlight.data?.runningTotal ?? 0, v2: inFlight.data?.staleCount ?? 0 })"
            :description="inFlight.data?.note" />
          <div v-if="Object.keys(inFlight.data?.byProcDef || {}).length" style="margin-bottom:10px; font-size:12px; color:#606266">
            {{ $t('system.flow.lblByProcDefDist') }}
            <el-tag v-for="(c, k) in (inFlight.data?.byProcDef || {})" :key="k" size="small" effect="plain"
              :type="k === inFlight.data?.newProcDefId ? 'success' : 'info'" style="margin-right:6px">
              {{ $t('system.flow.statInflightCount', { v1: k === inFlight.data?.newProcDefId ? t('system.flow.stNewVer') : t('system.flow.stOldVer'), v2: c }) }}</el-tag>
          </div>
          <el-table :data="inFlight.data?.items || []" size="small" stripe max-height="420" :empty-text="$t('system.flow.emptyStale')">
            <el-table-column prop="bizNo" :label="$t('field.docNo')" width="150" show-overflow-tooltip />
            <el-table-column prop="title" :label="$t('system.flow.colDocTitle')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="currentNode" :label="$t('system.flow.colCurrentNode')" width="120" />
            <el-table-column :label="$t('system.flow.colAssignee')" min-width="110">
              <template #default="{ row }">{{ row.currentAssigneeNames || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('system.flow.colDwell')" width="90" align="right">
              <template #default="{ row }">{{ row.stayMinutes != null ? $t('system.flow.textMinutesSuffix', { v1: row.stayMinutes }) : '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('system.flow.colVer')" min-width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="row.procDefId === inFlight.data?.newProcDefId ? 'success' : 'warning'" effect="plain">
                  {{ row.procDefId === inFlight.data?.newProcDefId ? t('system.flow.stNewVer') : t('system.flow.stOldVer') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="inFlight.data?.truncated" style="font-size:12px; color:#e6a23c; margin-top:6px">
            {{ $t('system.flow.hintStaleLimit') }}</div>
        </el-tab-pane>

        <el-tab-pane v-if="canModelUpdate" :label="$t('system.flow.ttlReleases')" name="history">
          <div class="field-hint" style="margin-bottom:8px">
            {{ $t('system.flow.hintBpmnRepoHead') }}
          </div>
          <el-table :data="inFlight.history" size="small" stripe max-height="420" :empty-text="$t('system.flow.emptyReleases')">
            <el-table-column prop="time" :label="$t('system.flow.colPublishedAt')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column prop="name" :label="$t('system.flow.colDiffFile')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="summary" :label="$t('system.flow.colDiffCount')" width="100" />
            <el-table-column :label="$t('common.operation')" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="loadDiff(row.name)">{{ $t('system.common.btnView') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pre v-if="inFlight.diff" class="diff-view">{{ inFlight.diff }}</pre>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button size="small" @click="inFlight.visible = false">{{ $t('common.close') }}</el-button>
        <el-button size="small" type="primary" @click="refreshInFlight">{{ $t('common.refresh') }}</el-button>
      </template>
    </el-drawer>
  </div>

  <CoachMark ref="flowConfigCm" :guide-key="_guideKey" :selector="'.cm-target-flow'" :title="$t('system.flow.ttlPage')" :text="$t('system.flow.hintPage')" />
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flowApi, flowConfigApi, designerApi, store } from '../../api'
// bpmn-js 全家桶较重：异步组件，仅在切到「流程图」页签时才加载独立 chunk
const BpmnDesigner = defineAsyncComponent(() => import('../../components/BpmnDesigner.vue'))
import SkeletonList from '../../components/SkeletonList.vue'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { enumLabel, bizTypeLabel, modelCategoryLabel, BIZ_TYPE, WF_MODEL_CATEGORY, ASSIGN_TYPE, TIMEOUT_ACTION, HANDLING_MODE, MULTI_TYPE } from '@/constants/enums.js'
// 【2026-10-15枚举收口】字典读取层：标签「字典优先、本地常量兜底」，接口不可用时行为与改造前逐字一致
import { useDict } from '@/composables/useDict.js'
// 第1批：多实例相关标签走字典
const multiTypeDict = useDict('wf_node_multi_type', MULTI_TYPE)
// 第2批：取人方式 / 取人档位 / 超时处置三处也走字典（字典为 V1.4.69 灌入，取值与本地常量一致）
//   —— 本地常量继续作为兜底常量传入，两边取值若有差异，以字典为准（运营可维护的权威源）
const assignTypeDict = useDict('wf_assign_type', ASSIGN_TYPE)
const handlingModeDict = useDict('wf_handling_mode', HANDLING_MODE)
const timeoutActionDict = useDict('wf_timeout_action', TIMEOUT_ACTION)

/** 统一触发本组件用到的全部枚举字典加载（第1批 1 个 + 第2批 3 个）。
 *  useDict 内部有 inflight 去重与 fail-soft，这里只负责"一次触发、全部并发"。
 *   不 await、不catch 抛出：字典不可用时标签函数已回落本地常量，界面零影响。 */
function useDictsReady() {
  return Promise.all([
    multiTypeDict.load(),
    assignTypeDict.load(),
    handlingModeDict.load(),
    timeoutActionDict.load(),
  ])
}
import CoachMark from '@/components/CoachMark.vue'
import RoleHolderHint from '@/components/common/RoleHolderHint.vue'
import { formatMoney, formatDateTime, showRowDetail } from '@/utils/format'
import { hasPerm } from '@/utils/perm'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const flowConfigCm = ref(null)

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

// ========== 模型列表 ==========
const models = ref([])
const filteredModels = ref([])
const selectedModel = ref(null)
// 「新增」不再把详情面板整块藏掉（原实现 selectedModel=null ⇒ 表单消失、模型无法从界面新建）：
// 草稿态只保留「基本信息」页签，其余页签要等有 modelKey 才有意义。
const modelDraft = ref(false)
const searchKey = ref('')
const loading = reactive({ models: false, nodes: false, bindings: false, configs: false, instances: false, delegates: false })
const detailTab = ref('info')
const saving = ref(false)

// ---------- 权限门控（P0 修复 · 2026-09-16） ----------
// 本页是「六页签聚合页」，但菜单 104「流程管理」只挂了一个码 wf:model:list，
// 而各页签分别打不同接口、要不同权限码。改前 selectModel() 会并行拉取
// 节点/绑定/实例/委托四份数据，导致仅有 wf:model:list 的角色（如普通员工
// COMMON_EMP）一进页就连报「缺少权限：wf:node:list / wf:instance:list」——
// 用户 2026-09-16 报障即为此。现按各页签真实权限码门控：无权限则不打请求、
// 并隐藏对应页签与操作按钮，做到"看不到而非报错"。
const canModelList = hasPerm('wf:model:list')
const canModelCreate = hasPerm('wf:model:create')
const canModelUpdate = hasPerm('wf:model:update')
const canModelDelete = hasPerm('wf:model:delete')
const canNodeList = hasPerm('wf:node:list')
const canNodeCreate = hasPerm('wf:node:create')
const canNodeUpdate = hasPerm('wf:node:update')
const canNodeDelete = hasPerm('wf:node:delete')
const canConfigList = hasPerm('wf:config:list')
const canConfigCreate = hasPerm('wf:config:create')
const canConfigUpdate = hasPerm('wf:config:update')
const canConfigDelete = hasPerm('wf:config:delete')
const canInstanceList = hasPerm('wf:instance:list')
const canDelegateList = hasPerm('wf:delegate:list')
const canDelegateCreate = hasPerm('wf:delegate:create')
const canDelegateUpdate = hasPerm('wf:delegate:update')
// 是否至少能看一个详情页签（否则右侧详情区直接给空态提示，不再发任何请求）
const canAnyDetail = canNodeList || canConfigList || canInstanceList || canDelegateList

// 被权限裁剪掉的页签说明（原实现静默隐藏，用户只看到"残缺页面"）
const hiddenTabDesc = computed(() => {
  const miss = []
  if (!canNodeList) miss.push(t('system.flow.tabNodes'))
  if (!canConfigList) miss.push(t('system.flow.tabGlobalCfg'))
  if (!canInstanceList) miss.push(t('system.flow.tabInstances'))
  if (!canDelegateList) miss.push(t('system.flow.tabDelegation'))
  return t('system.flow.hintHiddenTabs', { v1: miss.join('、') })
})

/**
 * 业务单据类型选项＝BIZ_TYPE 字典全集（2026-09-24）。
 * 原先是手写的 12 项静态清单，而库里 biz_type 有 84 种 ⇒ 基本信息页选中任何一项都显示成全大写英文码。
 * 中间版本曾把「模型名称」当作缺省标签兜底（`bt → modelName`），结果 MEETING/SUPPLIER_ADD 这类
 * 行的单据类型显示得跟模型名称一模一样，用户判定为"显示的是其它字段的数据"——已回退。
 * 现在只认字典：缺哪个编码就补 BIZ_TYPE 一行（见 enums.js），不拿别的字段凑数。
 */
const bizTypeOptions = computed(() =>
  Object.entries(BIZ_TYPE).map(([value, label]) => ({ value, label: label || value }))
)

const bindingBizTypes = computed(() => ([
  { label: t('system.flow.docExpenseClaim'), value: 'EXP_CLAIM' }, { label: t('system.flow.docPreApplication'), value: 'EXP_APPLY' },
  { label: t('system.flow.docVendorPayment'), value: 'EXP_PAY' }, { label: t('system.flow.docEmpLoan'), value: 'EXP_LOAN' },
  { label: t('system.flow.docEmpRepay'), value: 'EXP_REPAY' }, { label: t('system.flow.docFundPayment'), value: 'TR_PAY' },
  { label: t('system.flow.docFundTransfer'), value: 'TR_TRANSFER' }, { label: t('system.flow.docFundReceipt'), value: 'TR_RECEIPT' },
  { label: t('system.flow.docCashPlan'), value: 'TR_PLAN' }, { label: t('system.flow.docAssetAdd'), value: 'FA_ADD' },
  { label: t('system.flow.docAssetDisposal'), value: 'FA_DISPOSE' }, { label: t('system.flow.docAssetTransfer'), value: 'FA_TRANSFER' },
  { label: t('system.flow.docInventoryDisposal'), value: 'INV_DISPOSE' }, { label: t('system.flow.docARReceipt'), value: 'RECEIVABLE' },
]))

// 后端写入的是 DEPLOYED（WfDesignerController 发布）/ DRAFT（新建草稿），种子导入的历史模型为 ACTIVE；
// 原本只判 'PUBLISHED'（全库 0 行）→ 84 个已部署模型在列表里一律显示「草稿」。
const PUBLISHED_MODEL_STATUS = new Set(['DEPLOYED', 'ACTIVE', 'PUBLISHED'])
function isModelPublished(m) { return PUBLISHED_MODEL_STATUS.has(m?.status) }
/** 下线是与"未发布"完全不同的状态（不可发起，但历史实例仍在跑）——不能并入"草稿"显示，否则操作者会以为随手就能发布 */
function modelStatusText(m) {
  if (m?.status === 'OFFLINE') return t('system.flow.stOffline')
  return isModelPublished(m) ? t('system.flow.stPublished') : t('system.flow.stDraft')
}
function modelStatusType(m) {
  if (m?.status === 'OFFLINE') return 'danger'
  return isModelPublished(m) ? 'success' : 'info'
}

/**
 * 绑定列表里的「流程模型」列：modelKey 是路由目标（resolveModelKey 的返回值），
 * 但键本身不直观（CLAIM / TR_SEAL_CHANGE…），补一个可读名。
 * 找不到对应模型时如实回退为键本身并标红——那是脏数据，不该被静默显示成一个正常名字。
 */
function modelLabel(modelKey) {
  const m = models.value.find(x => x.modelKey === modelKey)
  return m ? (m.modelName || modelKey) : modelKey
}

// 原本下拉只有这 14 项，而业务代码发起审批时传入的类型有 75+（采购订单 PURCHASE_ORDER_REVIEW、
// 印鉴变更 TR_SEAL_CHANGE 等都选不到）→ 只能靠写库补绑定。故按已建流程模型动态补全，
// 并配合 allow-create 允许手工输入尚未建模的类型编码。
const bindingBizTypeOptions = computed(() => {
  const map = new Map(bindingBizTypes.value.map(o => [o.value, o.label]))
  for (const m of models.value) {
    const label = m.modelName || ''
    if (m.modelKey && !map.has(m.modelKey)) map.set(m.modelKey, label)
    if (m.bizType && !map.has(m.bizType)) map.set(m.bizType, label)
  }
  return [...map].map(([value, label]) => ({ value, label: label ? `${label}（${value}）` : value }))
})

async function loadModels() {
  loading.models = true
  try {
    const r = await flowApi.modelPage({ pageNum: 1, pageSize: 200 })
    models.value = r?.list || r?.records || []
    filteredModels.value = [...models.value]
  } catch (e) { ElMessage.error(e.message) } finally { loading.models = false }
}

function filterModels() {
  const k = searchKey.value.toLowerCase()
  filteredModels.value = models.value.filter(m =>
    (m.modelKey || '').toLowerCase().includes(k) || (m.modelName || '').toLowerCase().includes(k)
  )
}

async function selectModel(m) {
  modelDraft.value = false
  selectedModel.value = m
  detailTab.value = 'info'
  Object.assign(modelForm, { ...m })
  clearModelValidate()
  await loadAllDetails(m)
}

async function loadAllDetails(m) {
  if (!m) return
  // 按权限逐页签决定是否请求（P0 修复：原为无条件 Promise.all 四连发）
  const jobs = []
  if (canNodeList) jobs.push(loadNodes(m.modelKey))
  if (canConfigList) jobs.push(loadBindings(m.modelKey))
  if (canInstanceList) jobs.push(loadInstances(m.modelKey))
  if (canDelegateList) jobs.push(loadDelegates())
  await Promise.all(jobs)
}

// ========== 基本信息 ==========
const modelForm = reactive({})

const modelFormRules = computed(() => ({
  modelKey: commonRules.required(t('system.flow.errModelCodeRequired')),
  modelName: commonRules.required(t('system.flow.errModelNameRequired')),
}))
const nodeFormRules = computed(() => ({
  nodeKey: commonRules.required(t('system.flow.errNodeCodeRequired')),
  nodeName: commonRules.required(t('system.flow.errNodeNameRequired')),
  // 发起人与「直线上级+层级」两类不需要填审批人取值，故不挂 required（否则红星是假的）
  assignValue: ['INITIATOR', 'DEPT_LEADER_UP'].includes(nodeDialog.form.assignType) ? [] : [{
    required: true, validator: (rule, value, cb) => {
    const asgnType = nodeDialog.form.assignType
    const v = String(value == null ? '' : value).trim()
    if (!v) return cb(new Error(asgnType === 'USER' ? t('system.flow.errUserIdRequired') : t('system.flow.errRoleCodeRequired')))
    if (asgnType === 'USER' && !/^\s*\d+(\s*,\s*\d+)*\s*$/.test(v)) return cb(new Error(t('system.flow.errUserIdFormat')))
    cb()
  } }],
}))
const bindingFormRules = computed(() => ({
  bizType: commonRules.select(t('system.flow.errDocTypeRequired')),
  configName: commonRules.required(t('system.flow.errCfgNameRequired')),
}))
const configFormRules = computed(() => ({
  cfgType: commonRules.select(t('system.flow.errCfgTypeRequired')),
  cfgCode: commonRules.required(t('system.flow.errCfgCodeRequired')),
  cfgName: commonRules.required(t('system.flow.errCfgNameRequired')),
}))
const delegateFormRules = computed(() => ({
  toUserId: commonRules.select(t('system.flow.errDelegateRequired')),
  // 委托生效判定只看时间区间（WfDelegateService.activeDelegateTo），区间留空＝一条永不生效的委托
  beginTime: commonRules.date(t('system.flow.errStartRequired')),
  endTime: commonRules.date(t('system.flow.errEndRequired')),
}))
const modelFormRef = ref(null)
const nodeFormRef = ref(null)
const bindingFormRef = ref(null)
const configFormRef = ref(null)
const delegateFormRef = ref(null)
// 「基本信息」是常驻页签：切换模型时程序化改写 modelForm 不会触发行内校验，需手动清掉上一个模型的报错
function clearModelValidate() { nextTick(() => modelFormRef.value?.clearValidate()) }
const simRules = {}
function openModelCreate() {
  selectedModel.value = null
  modelDraft.value = true
  Object.assign(modelForm, {
    id: null, modelKey: '', modelName: '', category: '', bizType: '',
    orgScope: 'ALL', allowWithdraw: true,
    rejectStrategy: 'TO_INITIATOR', enableDelegate: false, version: 1, status: 'DRAFT',
  })
  detailTab.value = 'info'
  nodes.value = []; bindings.value = []; instances.value = []
  clearModelValidate()
}

function cancelModelDraft() {
  modelDraft.value = false
  selectedModel.value = null
  clearModelValidate()
}

async function saveModel() {
  if (!(await validateForms([modelFormRef]))) return
  saving.value = true
  try {
    if (modelForm.id) {
      await flowApi.modelUpdate({ ...modelForm })
    } else {
      await flowApi.modelCreate({ ...modelForm })
    }
    ElMessage.success(t('system.common.msgSaved'))
    await loadModels()
    // 重新选中
    const found = models.value.find(m => m.modelKey === modelForm.modelKey)
    if (found) {
      modelDraft.value = false
      selectedModel.value = found; Object.assign(modelForm, { ...found })
      await loadAllDetails(found)
    }
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

// ========== 节点配置 ==========
const nodes = ref([])
const nodeDialog = reactive({
  visible: false, editing: false, saving: false,
  form: {},
})

async function loadNodes(modelKey) {
  if (!modelKey) { nodes.value = []; return }
  loading.nodes = true
  try { nodes.value = await flowApi.nodeList(modelKey) || [] }
  catch (e) { ElMessage.error(e.message) } finally { loading.nodes = false }
}

/** 改成非 ROLE 类型时把 PICK/POOL 退回 AUTO（后端两档都只在 ROLE 分支被消费） */
function onAssignTypeChange(v) {
  const mode = nodeDialog.form.handlingMode
  if (v !== 'ROLE' && (mode === 'PICK' || mode === 'POOL')) nodeDialog.form.handlingMode = 'AUTO'
  nodeFormRef.value?.clearValidate('assignValue')
}

const assignValuePlaceholder = computed(() => ({
  USER: t('system.flow.hintUserIdValue'),
  ROLE: t('system.flow.hintRoleCodesValue'),
  INITIATOR: '',
}[nodeDialog.form.assignType] || ''))
const assignValueHint = computed(() => nodeDialog.form.assignType === 'ROLE'
  ? t('system.flow.hintRoleResolve')
  : nodeDialog.form.assignType === 'USER'
    ? t('system.flow.hintHardAssign')
    : '')
/** 自动指派只取第一个角色码落地（见 WfEngineFacade 的 assignValue.split(",")[0]），故体检按它做 */
const firstAssignRole = computed(() => (nodeDialog.form.assignValue || '').split(',')[0].trim())

function openNodeDialog(row) {
  if (!selectedModel.value) { ElMessage.warning(t('system.flow.msgNoModel')); return }
  nodeDialog.editing = !!row
  const form = row
    ? { ...row }
    : {
      modelKey: selectedModel.value.modelKey, nodeKey: '', nodeName: '',
      // F1-6：不再下发 nodeType（wf_node_config 无此列、后端不读，Jackson 静默丢弃＝假配置）
      assignType: 'USER', assignValue: '',
      handlingMode: 'AUTO',
      timeoutHours: 24, timeoutAction: 'NOTIFY', emptyStrategy: 'ERROR', allowTransfer: 1, allowAddsign: 1,
      multiType: 'ANY', voteRatio: 50, upLevel: 1,
      // G2 传阅：办结后抄送（用户ID / 角色编码，英文逗号分隔）；留空＝本节点不抄送
      ccUsers: '', ccRoles: '',
      // G2 驳回产品化：reject_scope 列默认 NONE、resubmit_jump_back 列默认 0（未配置即零行为变化）
      rejectScope: 'NONE', resubmitJumpBack: 0,
      // F10 治理：first_node_auto_pass 缺省 NULL＝未配置（回退旧判据），不强制填值以保持零行为变化
      firstNodeAutoPass: null,
    }
  // 存量行兜底：multi_type 列默认 ANY、vote_ratio 列默认 50，但老接口可能不回传 ⇒ 补默认避免控件空值
  if (form.multiType == null || form.multiType === '') form.multiType = 'ANY'
  if (form.voteRatio == null) form.voteRatio = 50
  // reject_scope / resubmit_jump_back 两列亦为 NOT NULL DEFAULT，老接口不回传时补默认
  if (form.rejectScope == null || form.rejectScope === '') form.rejectScope = 'NONE'
  if (form.resubmitJumpBack == null) form.resubmitJumpBack = 0
  // up_level 列默认 1；老接口不回传时补 1，避免「升级层级」控件空值被当作未配置
  if (form.upLevel == null) form.upLevel = 1
  Object.assign(nodeDialog, { visible: true, saving: false, form })
}

async function saveNode() {
  if (!(await validateForms([nodeFormRef]))) return
  nodeDialog.saving = true
  try {
    if (nodeDialog.editing) await flowApi.nodeUpdate({ ...nodeDialog.form })
    else await flowApi.nodeCreate({ ...nodeDialog.form })
    ElMessage.success(t('system.common.msgSaved'))
    nodeDialog.visible = false
    await loadNodes(selectedModel.value.modelKey)
  } catch (e) { ElMessage.error(e.message) } finally { nodeDialog.saving = false }
}

async function deleteNode(row) {
  try {
    await ElMessageBox.confirm(t('system.flow.askDeleteNode'), t('system.common.btnDelete'), { type: 'warning' })
    await flowApi.nodeDelete(row.id)
    ElMessage.success(t('system.common.stDeleted'))
    await loadNodes(selectedModel.value.modelKey)
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ========== 流程绑定 ==========
const bindings = ref([])
const bindingDialog = reactive({
  visible: false, editing: false, saving: false,
  form: {},
})

async function loadBindings(modelKey) {
  if (!modelKey) { bindings.value = []; return }
  loading.bindings = true
  try {
    const r = await flowApi.processPage({ modelKey, pageNum: 1, pageSize: 200 })
    bindings.value = r?.list || r?.records || []
  } catch (e) { ElMessage.error(e.message) } finally { loading.bindings = false }
}

function openBindingDialog(row) {
  if (!selectedModel.value) { ElMessage.warning(t('system.flow.msgNoModel')); return }
  bindingDialog.editing = !!row
  Object.assign(bindingDialog, {
    visible: true, saving: false,
    form: row ? { ...row } : {
      modelKey: selectedModel.value.modelKey, bizType: '', configName: '', subType: '',
      amountMin: null, amountMax: null, priority: 0, status: 'NORMAL',
    },
  })
}

async function saveBinding() {
  if (!(await validateForms([bindingFormRef]))) return
  bindingDialog.saving = true
  try {
    // 上限留空=不限；填 0 会被引擎判成「amount ≥ 0 一律排除」的死规则，永不出票。
    const form = { ...bindingDialog.form }
    if (form.amountMax === 0) form.amountMax = null
    if (form.amountMin === 0) form.amountMin = null
    if (bindingDialog.editing) await flowApi.processUpdate(form)
    else await flowApi.processCreate(form)
    ElMessage.success(t('system.common.msgSaved'))
    bindingDialog.visible = false
    await loadBindings(selectedModel.value.modelKey)
  } catch (e) { ElMessage.error(e.message) } finally { bindingDialog.saving = false }
}

async function deleteBinding(row) {
  try {
    await ElMessageBox.confirm(t('system.flow.askDeleteBinding'), t('system.common.btnDelete'), { type: 'warning' })
    await flowApi.processDelete(row.id)
    ElMessage.success(t('system.common.stDeleted'))
    await loadBindings(selectedModel.value.modelKey)
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ========== 全局配置 ==========
const configs = ref([])
const cfgType = ref('THRESHOLD')
const configDialog = reactive({
  visible: false, editing: false, saving: false,
  form: {},
  cfgData: {},  // 结构化表单数据
})

/** 按 cfgType 初始化结构化表单默认值 */
function defaultCfgData(type) {
  const presets = {
    THRESHOLD:    { min: 0, max: 0, level: 1 },
    TIMEOUT:      { timeoutHours: 24, action: 'NOTIFY', targetRole: '', notifyUsers: '', remindBefore: 0 },
    ACTION:       { action: 'APPROVE', autoApprove: false, allowWithdraw: false, comment: '' },
    INTEGRATION:  { systemName: '', apiUrl: '', method: 'POST', retryTimes: 3, timeoutSeconds: 30 },
  }
  return { ...(presets[type] || {}) }
}

/** 从 JSON 字符串解析到结构化表单数据 */
function parseCfgJson(type, jsonStr) {
  const base = defaultCfgData(type)
  if (!jsonStr) return base
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return { ...base, ...obj }
  } catch {
    return base
  }
}

/** 将结构化表单数据序列化为 JSON 字符串 */
function serializeCfgData(type, data) {
  const result = {}
  const defaults = defaultCfgData(type)
  // 只输出与默认值不同或非空的字段，保持 JSON 简洁
  for (const key of Object.keys(defaults)) {
    const val = data[key]
    if (val !== undefined && val !== null && val !== '') {
      result[key] = val
    }
  }
  return JSON.stringify(result)
}

/** 表格列：将 cfgJson 转为人类可读摘要 */
function cfgSummary(row) {
  if (!row.cfgJson) return '-'
  try {
    const obj = typeof row.cfgJson === 'string' ? JSON.parse(row.cfgJson) : row.cfgJson
    const parts = []
    for (const [k, v] of Object.entries(obj)) {
      if (v !== null && v !== '' && v !== undefined) {
        const labelMap = {
          min: t('system.flow.colMinShort'), max: t('system.flow.colMaxShort'), level: t('system.flow.colLevel'),
          timeoutHours: t('system.flow.colTimeoutH'), action: t('field.action'), notifyUsers: t('system.flow.colNotify'),
          targetRole: t('system.flow.colTargetRole'),
          remindBefore: t('system.flow.colRemindH'),
          autoApprove: t('system.flow.optActionAutoApprove'), allowWithdraw: t('system.flow.colWithdrawable'), comment: t('common.remark'),
          systemName: t('system.flow.colSystem'), apiUrl: t('field.api'), method: t('system.common.colMethod'),
          retryTimes: t('system.flow.colRetry'), timeoutSeconds: t('system.flow.colTimeoutS'),
        }
        const label = labelMap[k] || k
        parts.push(`${label}: ${v}`)
      }
    }
    return parts.join('，') || '-'
  } catch {
    return String(row.cfgJson)
  }
}

/** 配置类型切换时重置结构化表单数据 */
function onCfgTypeChange() {
  configDialog.cfgData = defaultCfgData(configDialog.form.cfgType)
}

async function loadConfigs() {
  loading.configs = true
  try {
    const r = await flowConfigApi.page({ cfgType: cfgType.value, pageNum: 1, pageSize: 200 })
    configs.value = r?.list || r?.records || []
  } catch (e) { ElMessage.error(e.message) } finally { loading.configs = false }
}

function openConfigDialog(row) {
  configDialog.editing = !!row
  const type = row ? row.cfgType : cfgType.value
  Object.assign(configDialog, {
    visible: true, saving: false,
    form: row ? { ...row } : { cfgType: type, cfgCode: '', cfgName: '', cfgJson: '', status: 'NORMAL' },
    cfgData: row ? parseCfgJson(row.cfgType, row.cfgJson) : defaultCfgData(type),
  })
}

async function saveConfig() {
  if (!(await validateForms([configFormRef]))) return
  configDialog.saving = true
  try {
    // 将结构化表单数据序列化为 cfgJson
    configDialog.form.cfgJson = serializeCfgData(configDialog.form.cfgType, configDialog.cfgData)
    if (configDialog.editing) await flowConfigApi.update({ ...configDialog.form })
    else await flowConfigApi.create({ ...configDialog.form })
    ElMessage.success(t('system.common.msgSaved'))
    configDialog.visible = false
    await loadConfigs()
  } catch (e) { ElMessage.error(e.message) } finally { configDialog.saving = false }
}

async function deleteConfig(row) {
  try {
    await ElMessageBox.confirm(t('system.flow.askDeleteConfig'), t('system.common.btnDelete'), { type: 'warning' })
    await flowConfigApi.delete(row.id)
    ElMessage.success(t('system.common.stDeleted'))
    await loadConfigs()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ========== 运行实例 ==========
const instances = ref([])
const instPageNum = ref(1)
const instPageSize = ref(20)
const instTotal = ref(0)

async function loadInstances(modelKey) {
  if (!modelKey) { instances.value = []; instTotal.value = 0; return }
  loading.instances = true
  try {
    const r = await flowApi.instancePage({ modelKey, pageNum: instPageNum.value, pageSize: instPageSize.value })
    instances.value = r?.list || r?.records || []
    instTotal.value = r?.total ?? r?.totalCount ?? instances.value.length
  } catch (e) { ElMessage.error(e.message) } finally { loading.instances = false }
}

function handleInstSizeChange(val) {
  instPageSize.value = val
  instPageNum.value = 1
  if (selectedModel.value) loadInstances(selectedModel.value.modelKey)
}

function handleInstCurrentChange(val) {
  instPageNum.value = val
  if (selectedModel.value) loadInstances(selectedModel.value.modelKey)
}

// ========== 委托代理 ==========
const delegates = ref([])
const dlgPageNum = ref(1)
const dlgPageSize = ref(20)
const dlgTotal = ref(0)
const delegateDialog = reactive({
  visible: false, saving: false,
  form: {},
})

async function loadDelegates() {
  loading.delegates = true
  try {
    // E-12/F1-2：委托关系是审批授权信息，服务端已按身份过滤。本 tab 是流程管理视角——
    // 集团级（无公司维度）账号看全量，公司级账号只剩本人相关。
    const scope = store.user?.companyId == null ? 'all' : 'mine'
    const r = await flowApi.delegatePage({ pageNum: dlgPageNum.value, pageSize: dlgPageSize.value, scope })
    delegates.value = r?.list || r?.records || []
    dlgTotal.value = r?.total ?? r?.totalCount ?? delegates.value.length
  } catch (e) { ElMessage.error(e.message) } finally { loading.delegates = false }
}

function handleDlgSizeChange(val) {
  dlgPageSize.value = val
  dlgPageNum.value = 1
  loadDelegates()
}

function handleDlgCurrentChange(val) {
  dlgPageNum.value = val
  loadDelegates()
}

// 委托契约是用户 ID（WfDelegate.fromUserId/toUserId）；委托人由后端硬校验必须是登录人，故系统带出
const currentUserId = computed(() => store.user?.userId ?? store.user?.id ?? null)
const currentUserName = computed(() => store.user?.realName || store.user?.nickName
  || store.user?.username || store.user?.userName || t('system.flow.optCurrentUser'))

function openDelegateDialog() {
  Object.assign(delegateDialog, {
    visible: true, saving: false,
    form: { fromUserId: currentUserId.value, toUserId: null, bizTypes: '', beginTime: '', endTime: '', reason: '', status: 'NORMAL' },
  })
}

async function saveDelegate() {
  if (!(await validateForms([delegateFormRef]))) return
  delegateDialog.saving = true
  try {
    await flowApi.delegateCreate({ ...delegateDialog.form })
    ElMessage.success(t('system.common.msgSaved'))
    delegateDialog.visible = false
    await loadDelegates()
  } catch (e) { ElMessage.error(e.message) } finally { delegateDialog.saving = false }
}

async function cancelDelegate(row) {
  try {
    await ElMessageBox.confirm(t('system.flow.askCancelDelegate'), t('system.common.btnConfirm'), { type: 'warning' })
    await flowApi.delegateCancel(row.id)
    ElMessage.success(t('system.flow.msgCancelled'))
    await loadDelegates()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ========== 模型操作 ==========
const checkResult = reactive({ visible: false, data: null })
const checkFailDesc = computed(() => {
  const errors = Array.isArray(checkResult.data?.errors) ? checkResult.data.errors.filter(Boolean) : []
  if (errors.length) return errors.join('；')
  return checkResult.data?.message || t('system.flow.msgUnknownError')
})
async function validateModel() {
  try {
    checkResult.data = await designerApi.validate(selectedModel.value.bpmnXml || '')
    checkResult.visible = true
  } catch (e) { ElMessage.error(e.message) }
}

const simResult = reactive({
  visible: false, amount: 10000, itemName: '', running: false, ran: false,
  trace: [], errors: [], warnings: [], approvalSteps: 0,
})
const simApprovalCount = computed(() =>
  simResult.approvalSteps || simResult.trace.filter(x => x.type === 'task').length
)
const simAmountText = computed(() =>
  Number(simResult.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
)
function simTagType(type) {
  return { task: 'primary', gateway: 'warning', join: 'info', end: 'success', error: 'danger' }[type] || 'info'
}
function simTypeLabel(type) {
  const key = { task: 'simTypeTask', gateway: 'simTypeGateway', join: 'simTypeJoin', end: 'simTypeEnd', error: 'simTypeError' }[type]
  return key ? t('system.flow.' + key) : type
}
function normalizeSimTrace(t) {
  const type = t?.type || ''
  const tip = (t?.tip || '').trim()
  return {
    type,
    nodeName: t?.name || t?.nodeName || t?.nodeKey || t('system.flow.simUnnamedNode'),
    nodeKey: t?.nodeKey || '',
    assignee: type === 'task' ? tip : '',
    tip,
    passed: t?.passed ?? type !== 'error',
  }
}
function resetSim() {
  simResult.ran = false
  simResult.trace = []
  simResult.errors = []
  simResult.warnings = []
  simResult.approvalSteps = 0
}
function simulateModel() {
  if (!selectedModel.value.bpmnXml) { ElMessage.warning(t('system.flow.msgNoBpmn')); return }
  simResult.amount = 10000
  simResult.itemName = ''
  resetSim()
  simResult.visible = true
}
async function runSimulate() {
  simResult.running = true
  try {
    const r = await designerApi.simulate(selectedModel.value.bpmnXml, simResult.amount, simResult.itemName)
    simResult.trace = (r?.trace || []).map(normalizeSimTrace)
    simResult.errors = Array.isArray(r?.errors) ? r.errors.filter(Boolean) : []
    simResult.warnings = Array.isArray(r?.warnings) ? r.warnings.filter(Boolean) : []
    simResult.approvalSteps = Number(r?.approvalSteps || 0)
    simResult.ran = true
    if (!simResult.trace.length) ElMessage.warning(t('system.flow.msgSimNoPath'))
    else ElMessage.success(t('system.flow.msgSimDone', { v1: simResult.trace.length }))
  } catch (e) {
    simResult.ran = true
    simResult.trace = []
    simResult.errors = [e.message || t('system.flow.msgUnknownError')]
    simResult.warnings = []
  } finally {
    simResult.running = false
  }
}

async function publishModel() {
  let warn = ''
  try {
    const f = await designerApi.inFlight(selectedModel.value.modelKey)
    if (f?.staleCount) warn = t('system.flow.warnInflightNote', { v1: f.staleCount })
  } catch (e) { /* 影响面查询失败不阻断发布 */ }
  try {
    await ElMessageBox.confirm(t('system.flow.askPublish', { v1: selectedModel.value.modelName, v2: warn }), t('system.flow.btnPublish'), { type: 'warning' })
    await designerApi.publish(selectedModel.value)
    ElMessage.success(t('system.flow.stPublished'))
    await loadModels()
    const found = models.value.find(m => m.id === selectedModel.value.id)
    if (found) { selectedModel.value = found; Object.assign(modelForm, { ...found }); clearModelValidate() }
    // 发布后直接把影响面摊开（在途旧实例清单 + 本次 diff），不再只提示"已发布"
    await openInFlight()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || t('system.flow.msgPublishFail')) }
}

// ========== G3-3/G3-4 发布影响面（在途旧实例 + BPMN 仓库化 diff） ==========
const inFlight = reactive({ visible: false, tab: 'inflight', modelName: '', data: null, history: [], diff: '' })

async function openInFlight() {
  if (!selectedModel.value?.modelKey) { ElMessage.warning(t('system.flow.msgPickModelFirst')); return }
  inFlight.modelName = selectedModel.value.modelName || selectedModel.value.modelKey
  inFlight.diff = ''
  inFlight.visible = true
  await refreshInFlight()
}

async function refreshInFlight() {
  const key = selectedModel.value?.modelKey
  if (!key) return
  try {
    inFlight.data = await designerApi.inFlight(key)
    inFlight.history = await designerApi.publishHistory(key) || []
  } catch (e) { ElMessage.error(e.message) }
}

async function loadDiff(name) {
  try {
    const r = await designerApi.publishDiff(selectedModel.value.modelKey, name)
    inFlight.diff = r?.content || t('system.flow.diffMissing')
  } catch (e) { ElMessage.error(e.message) }
}

function onModelCommand(cmd) {
  if (cmd === 'inflight') openInFlight()
  else if (cmd === 'exportRepo') exportRepo()
  else if (cmd === 'export') exportModel()
  else if (cmd === 'delete') deleteModel()
}

async function exportRepo() {
  try {
    const r = await designerApi.exportRepo()
    ElMessage.success(t('system.flow.msgExportedTo', { v1: r.exported, v2: r.models }))
  } catch (e) { ElMessage.error(e.message) }
}

async function exportModel() {
  try {
    const r = await designerApi.exportModel(selectedModel.value.id)
    const blob = new Blob([r.bpmnXml], { type: 'text/xml;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${r.modelKey}_v${r.version}.bpmn20.xml`
    a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success(t('system.flow.msgExportedShort'))
  } catch (e) { ElMessage.error(e.message) }
}

async function deleteModel() {
  try {
    await ElMessageBox.confirm(t('system.flow.askDeleteModel', { v1: selectedModel.value.modelName }), t('system.common.btnDelete'), { type: 'warning' })
    await flowApi.modelDelete(selectedModel.value.id)
    ElMessage.success(t('system.common.stDeleted'))
    selectedModel.value = null
    await loadModels()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

/** 「流程图」页签：设计器保存回写 bpmnXml（后端 /model/update 同码鉴权 wf:model:update） */
async function onBpmnSaved(xml) {
  if (!selectedModel.value) return
  try {
    await flowApi.modelUpdate({ ...selectedModel.value, bpmnXml: xml })
    selectedModel.value = { ...selectedModel.value, bpmnXml: xml }
    Object.assign(modelForm, { ...modelForm, bpmnXml: xml })
    ElMessage.success(t('system.flow.msgDiagramSaved'))
    await loadModels()
  } catch (e) { ElMessage.error(e.message || t('system.flow.msgDiagramSaveFail')) }
}

async function importFlow() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xml,.bpmn'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    const text = await file.text()
    // 原生 prompt() 的按钮由浏览器渲染（英文 OK/Cancel），换成 ElMessageBox 保持中文与风格一致
    let key
    try {
      ({ value: key } = await ElMessageBox.prompt(t('system.flow.phModelCodeUnique'), t('system.flow.ttlImport'), {
        inputValue: 'IMPORT_' + Date.now().toString().slice(-6),
        inputPattern: /^[A-Za-z][A-Za-z0-9_-]{1,49}$/,
        inputErrorMessage: t('system.flow.errCodeFormat'),
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        closeOnClickModal: false,
      }))
    } catch { return }
    if (!key) return
    try {
      await designerApi.importModel({ modelKey: key, modelName: key, bpmnXml: text })
      ElMessage.success(t('system.flow.msgImportOk') + key)
      await loadModels()
    } catch (e) { ElMessage.error(e.message) }
  }
  input.click()
}

// ========== 辅助方法 ==========
/** 流程/单据类型 → 中文：先查业务类型字典（唯一口径），字典没有才退回同名模型的名称 */
function flowTypeLabel(v) {
  if (!v) return ''
  if (BIZ_TYPE[v]) return BIZ_TYPE[v]
  const m = models.value.find((x) => x.modelKey === v)
  return m?.modelName || v
}
/** 委托的适用范围：库里存逗号分隔的单据类型编码，空＝全部单据（WfDelegateService 同口径） */
function delegateScopeLabel(v) {
  const codes = String(v || '').split(',').map(s => s.trim()).filter(Boolean)
  if (!codes.length) return t('system.flow.optAllDocTypes')
  return codes.map(c => bizTypeLabel(c)).join('、')
}
function assignTypeLabel(v) {
  // 【第2批】字典优先；字典未到/失败 ⇒回落本地常量，行为与改造前一致
  return assignTypeDict.label(v) || enumLabel(ASSIGN_TYPE, v)
}
function assignTypeTag(v) {
  return { USER: 'success', ROLE: 'primary', DEPT_HEAD: 'warning', INITIATOR: 'info', LEADER: 'warning',
    DEPT_LEADER_UP: 'warning' }[v] || 'info'
}
/**
 * 超时动作是否属于「升级」档（决定「升级层级」控件是否显示）。
 * 归一拼写照 WfTimeoutJob.normalizeAction；阶梯档（ESCALATE_AFTER_URGES）是"延迟升级"，
 * 最终同样走 escalateTarget(upLevel)，故一并计入。
 */
function isEscalateAction(v) {
  const s = String(v == null ? '' : v).trim().toUpperCase()
  return ['ESCALATE', 'AUTO_ESCALATE', 'AUTO_TRANSFER', 'TRANSFER', 'REDISTRIBUTE',
    'ESCALATE_AFTER_URGES', 'URGE_THEN_ESCALATE', 'REMIND_THEN_ESCALATE'].includes(s)
}
/** 升级层级显示：0/1 都是升 1 级（NodeOrgResolver.escalateTarget 把 <1 视作 1） */
function upLevelLabel(v) {
  const n = v == null || v === '' ? 1 : Number(v)
  if (!Number.isFinite(n)) return '—'
  return t('system.flow.textLevelSuffix', { v1: (n < 1 ? 1 : n) })
}
/** 取人方式：列/详情用。老后端不返回该字段时显示"自动指派"（等价 DB 默认值，不是缺数据） */
function handlingModeLabel(v) {
  // 【第2批】字典优先；字典未到/失败 ⇒ 回落本地常量
  return handlingModeDict.label(v || 'AUTO') || enumLabel(HANDLING_MODE, v || 'AUTO')
}
/** POOL 用 primary 色：它意味着该节点会有"待领取"任务停在池里，是运维要盯的状态 */
function handlingModeTag(v) {
  const m = v || 'AUTO'
  return m === 'PICK' ? 'warning' : m === 'POOL' ? 'primary' : 'info'
}
function timeoutActionLabel(v) {
  // 【第2批】字典优先；字典未到/失败 ⇒ 回落本地常量。
  //  TIMEOUT_ACTION 在前端是「别名多对一」映射（NOTIFY/REMIND/URGE 都标"催办通知"），
  //    而字典只登记后端真正读的值（含库中存量 16 个节点的 NOTIFY 旧拼写）。
  //    两边标签有差异（如 AUTO_TRANSFER：前端"超时升级转办"vs 字典无此项），
  //    故字典没命中时必须回落本地常量，否则存量别名会显示成英文码。
  return timeoutActionDict.label(v) || enumLabel(TIMEOUT_ACTION, v) || '—'
}
/** 会签模式：ANY 为列默认值（普通单任务），显示"任一通过"而非空白
 *
 *  【2026-10-15 第1批枚举收口】标签改为「字典优先、本地常量兜底」：
 *   字典 wf_node_multi_type（V1.4.67 灌入 4 项）的文案比本地常量更贴合业务
 *   （字典："或签（任一人办理即可）"／常量："任一通过（单人办理）"），且字典由运营可维护。
 *    但字典是**运行时远程数据**，接口不可用时必须回落本地常量 —— 否则配置页一打开就空白。
 *   useDict 内部已做 fail-soft：加载中/失败/未配置 ⇒ 一律用本地常量，界面零变化。
 */
function multiTypeLabel(v) {
  return multiTypeDict.label(v || 'ANY') || enumLabel(MULTI_TYPE, v || 'ANY')
}
/** ALL/SEQ/VOTE 都是多实例任务（前端会签进度/投票界面要据此渲染），用 warning 色提示"与单任务不同" */
function multiTypeTag(v) {
  const m = v || 'ANY'
  return m === 'ANY' ? 'info' : 'warning'
}
/** 是否有真实的多实例节点（模型级提示用：让配置者知道"发布"后会生成会签任务） */
const hasMultiInstanceNode = computed(() => (nodes.value || []).some(
  (n) => n.multiType === 'ALL' || n.multiType === 'SEQ' || n.multiType === 'VOTE'))
function instanceStatusTag(v) {
  return statusType(v)
}
function instanceStatusText(v) {
  // 后端统一拼写为 CANCELLED（双 L），此前本地表写成 CANCELED 导致该态原样显示英文
  return v ? statusLabel(v) : '—'
}

// 当切换到 configs Tab 时自动加载
function onTabChange(tab) {
  if (tab === 'configs' && !configs.value.length) loadConfigs()
}

// ========== 流程图全屏设计 ==========
const bpmnRef = ref(null)
const diagramWrapRef = ref(null)
const isDiagramFullscreen = ref(false)

function toggleDiagramFullscreen() {
  const el = diagramWrapRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else if (el.requestFullscreen) {
      el.requestFullscreen()
    } else {
      ElMessage.warning(t('system.flow.msgNoFullscreen'))
    }
  } catch (e) {
    ElMessage.error(t('system.flow.msgFullscreenFail') + e.message)
  }
}

// 进入/退出全屏后容器尺寸突变：等布局稳定后让设计器重新适配视图
function onFullscreenChange() {
  isDiagramFullscreen.value = !!document.fullscreenElement && document.fullscreenElement === diagramWrapRef.value
  setTimeout(() => {
    try { bpmnRef.value?.fitView?.() } catch (e) { /* ignore */ }
  }, 150)
}

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  // 【2026-10-15】取枚举字典（第1批会签模式 + 第2批取人方式/档位/超时处置）。
  //   失败不阻断页面（useDict内部 fail-soft 回落本地常量），也不 await 到阻塞首屏——
  //   标签函数是同步的，字典回来后 computed 自动刷新。
  useDictsReady()
  loadModels().then(() => {
    if (filteredModels.value.length) selectModel(filteredModels.value[0])
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  // 若仍在全屏中离开页面，主动退出避免浏览器停留全屏状态
  try { if (document.fullscreenElement) document.exitFullscreen() } catch (e) { /* ignore */ }
})

// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_flowConfigCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => flowConfigCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.flow-config-page { min-height: 100%; }
.model-list-card { height: 100%; }
.panel-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.panel-title { font-size: 15px; font-weight: 600; color: #303133; }
.model-actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
.model-actions .el-button + .el-button { margin-left: 0; }
.model-list { max-height: 600px; overflow-y: auto; }
.model-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.model-item:hover { background: #f5f7fa; border-color: #e4e7ed; }
.model-item.active { background: #ecf5ff; border-color: #b3d8ff; }
.model-item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.model-name { font-weight: 600; font-size: 14px; color: #303133; }
.model-meta { display: flex; gap: 12px; font-size: 12px; color: #909399; }
.model-detail-header { display: flex; align-items: center; }
.cfg-hint { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 4px; }
.field-hint { font-size: 12px; color: #909399; line-height: 18px; margin-top: 2px; }
/* 节点弹窗：分组标题＋内容区限高，避免整页过长 */
.node-dialog :deep(.el-dialog__body) { max-height: 70vh; overflow-y: auto; padding-top: 8px; }
.form-section-title {
  font-size: 13px; font-weight: 600; color: #303133;
  margin: 4px 0 12px; padding-left: 8px; line-height: 16px;
  border-left: 3px solid var(--el-color-primary);
}
.form-section-title:not(:first-child) { margin-top: 16px; }
/* 模拟结果：结论→路径→明细 */
.sim-query { margin-bottom: 4px; }
.sim-query :deep(.el-form-item) { margin-bottom: 12px; }
.sim-warn { margin-top: 8px; }
.sim-section-title { font-size: 13px; font-weight: 600; color: #303133; margin: 16px 0 8px; }
.sim-path {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  padding: 10px 12px; background: #f8f9fb; border-radius: 4px;
}
.sim-arrow { color: #c0c4cc; font-size: 12px; }
.sim-path-node { max-width: 180px; overflow: hidden; text-overflow: ellipsis; }
.sim-muted { color: #909399; }
/* 页签权限裁剪提示 */
.perm-hint { margin-bottom: 12px; }
/* 流程图页签 */
.diagram-tip {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px; font-size: 12px; color: #606266;
}
.diagram-tip .ro { color: #e6a23c; }
.diagram-wrap { height: 620px; min-height: 420px; }
/* 全屏设计：铺满整个屏幕，给拖放/预览最大空间 */
.diagram-wrap:fullscreen {
  background: #f8f9fb;
  padding: 10px;
  overflow: hidden;
}
.diagram-wrap:fullscreen .bpmn-designer { height: 100%; }
/* 发布 diff 原文 */
.diff-view {
  margin-top: 10px; max-height: 320px; overflow: auto;
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 4px;
  padding: 8px 10px; font-size: 12px; line-height: 1.5; color: #303133;
  white-space: pre-wrap; word-break: break-all;
}
</style>
