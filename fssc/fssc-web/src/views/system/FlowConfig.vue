<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-config-page">
    <el-row class="cm-target-flow" :gutter="16">
      <!-- 左侧：流程模型列表（span 7→6：宽度缩窄 ~14%，栅格最接近"缩 20%"的档位） -->
      <el-col :span="6">
        <el-card class="cm-target-flow model-list-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">流程模型</span>
              <div>
                <el-button v-if="canModelCreate" size="small" @click="importFlow">导入</el-button>
                <el-button v-if="canModelCreate" type="primary" size="small" @click="openModelCreate">新增</el-button>
              </div>
            </div>
          </template>
          <el-input v-model="searchKey" placeholder="搜索模型编码/名称..." clearable size="small" style="margin-bottom:8px" @input="filterModels" />
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
            <el-empty v-if="!filteredModels.length && !loading.models" description="暂无流程模型" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：模型详情面板 -->
      <el-col :span="18">
        <!-- 页签被权限裁剪时给出明确原因（原实现直接静默隐藏，用户只看到"残缺页面"误以为功能缺失） -->
        <el-alert v-if="!canNodeList || !canConfigList || !canInstanceList" type="info" :closable="false" show-icon
                  class="perm-hint"
                  title="当前账号的流程配置页签按权限显示"
                  :description="hiddenTabDesc" />
        <el-card v-if="selectedModel || modelDraft" shadow="never">
          <template #header>
            <div class="panel-header">
              <div class="model-detail-header">
                <span class="panel-title">{{ selectedModel ? selectedModel.modelName : '新建流程模型' }}</span>
                <el-tag v-if="selectedModel" size="small" type="info" style="margin-left:8px">{{ selectedModel.modelKey }}</el-tag>
                <el-tag v-if="selectedModel && selectedModel.bizType" size="small" type="warning" style="margin-left:4px"
                        :title="'业务单据类型编码：' + selectedModel.bizType">{{ bizTypeLabel(selectedModel.bizType) }}</el-tag>
                <el-tag v-if="selectedModel && selectedModel.category" size="small" style="margin-left:4px">{{ modelCategoryLabel(selectedModel.category) }}</el-tag>
                <el-tag v-if="modelDraft" size="small" type="warning" style="margin-left:8px">未保存</el-tag>
              </div>
              <div v-if="selectedModel">
                <el-button v-if="canModelUpdate" size="small" type="success" @click="validateModel">校验</el-button>
                <el-button v-if="canModelUpdate" size="small" type="warning" @click="simulateModel">模拟</el-button>
                <el-button v-if="canModelUpdate" size="small" type="primary" @click="publishModel">发布</el-button>
                <el-button v-if="canModelUpdate" size="small" @click="openInFlight">在途影响</el-button>
                <el-button v-if="canModelUpdate" size="small" @click="exportRepo">导出入仓</el-button>
                <el-button v-if="canModelUpdate" size="small" @click="exportModel">导出</el-button>
                <el-button v-if="canModelDelete" size="small" type="danger" @click="deleteModel">删除</el-button>
              </div>
            </div>
          </template>

          <el-tabs v-model="detailTab" type="border-card" @tab-change="onTabChange">
            <!-- 基本信息 -->
            <el-tab-pane v-if="canModelList" label="基本信息" name="info">
              <el-form ref="modelFormRef" :model="modelForm" :rules="modelFormRules" label-width="120px" size="default" style="max-width:700px">
                <el-form-item label="模型编码" prop="modelKey">
                  <el-input v-model="modelForm.modelKey" :disabled="!!modelForm.id" />
                </el-form-item>
                <el-form-item label="模型名称" prop="modelName">
                  <el-input v-model="modelForm.modelName" />
                </el-form-item>
                <el-form-item label="分类">
                  <el-select v-model="modelForm.category" placeholder="选择业务域" style="width:100%">
                    <el-option v-for="(label, code) in WF_MODEL_CATEGORY" :key="code" :label="label" :value="code" />
                  </el-select>
                  <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
                    分类＝业务域，用于模型清单分组和"按域查询"，改动只影响分组、不影响流转（路由按模型编码绑定）。
                    <b>未归类（模板导入）</b>不是一个域：后端按需求模板批量建流程时曾把「来源」的取值 <code>TEMPLATE</code>
                    写进「分类」列，本账套 18 行已于 2026-09-24 归回真实业务域（脚本 seed_wfmodel_category_domain_fix_20260924.sql）；
                    若在别的账套看到它，逐个改到真实业务域即可。新建/模板导入未指定域的模型显示为「未归类」。
                  </div>
                </el-form-item>
                <el-form-item label="业务单据类型">
                  <el-select v-model="modelForm.bizType" filterable allow-create default-first-option
                             placeholder="选择单据类型" style="width:100%">
                    <el-option v-for="o in bizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                  <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
                    选项＝全局业务单据类型字典（下拉可搜索，也可直接输入尚未登记的编码）。
                    注意：单据提单时命中哪条流程按<b>模型编码</b>绑定，这一项是模型上的业务类型标注（清单标签、流程校验按它归集），改动不影响在途与新建单据的路由。
                    若显示成全大写英文，说明该编码没登记进 <code>constants/enums.js</code> 的 BIZ_TYPE，补一行即恢复中文。
                  </div>
                </el-form-item>
                <el-form-item label="生效组织范围">
                  <el-select v-model="modelForm.orgScope" style="width:100%">
                    <el-option label="全集团" value="ALL" />
                    <el-option label="指定公司" value="ORG" />
                  </el-select>
                </el-form-item>
                <el-form-item label="允许撤回">
                  <el-switch v-model="modelForm.allowWithdraw" />
                </el-form-item>
                <el-form-item label="驳回策略">
                  <el-select v-model="modelForm.rejectStrategy" style="width:100%">
                    <el-option label="驳回至发起人" value="TO_INITIATOR" />
                    <el-option label="驳回至上一节点" value="TO_PREV" />
                  </el-select>
                </el-form-item>
                <el-form-item label="开启代理审批">
                  <el-switch v-model="modelForm.enableDelegate" />
                </el-form-item>
                <el-form-item>
                  <el-button v-if="modelDraft ? canModelCreate : canModelUpdate" type="primary" :loading="saving" @click="saveModel">{{ modelDraft ? '创建模型' : '保存' }}</el-button>
                  <el-button v-if="modelDraft" @click="cancelModelDraft">取消</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 流程图（BPMN 图形）——2026-09-16 补齐：
                 此前本页只有表单/表格，而全站的图形设计器 BpmnDesigner 只被一个从未挂路由的
                 通用页引用（其 isFlow 分支要求 route.path === '/expense/flow'，而该路由由本页渲染）
                 → 图形化流程图在任何入口都打不开。
                 现直接在本页提供：有 bpmnXml 即渲染图形；无 wf:model:update 走只读 Viewer。 -->
            <el-tab-pane v-if="canModelList && !modelDraft" label="流程图" name="diagram">
              <template v-if="selectedModel">
                <!-- 无图形定义 + 无编辑权：给明确说明，不渲染假图 -->
                <el-alert v-if="!selectedModel.bpmnXml && !canModelUpdate" type="warning" :closable="false" show-icon
                          title="该流程模型尚未包含 BPMN 图形定义"
                          description="请使用具备「流程建模」权限（wf:model:update）的账号导入或绘制流程图。" />
                <template v-else>
                  <div class="diagram-tip">
                    <span>模型 <b>{{ selectedModel.modelKey }}</b> · v{{ selectedModel.version }} · {{ modelStatusText(selectedModel) }}
                      <span v-if="!canModelUpdate" class="ro">只读（无 wf:model:update）</span>
                    </span>
                    <el-button size="small" :type="isDiagramFullscreen ? 'warning' : 'default'" @click="toggleDiagramFullscreen">
                      {{ isDiagramFullscreen ? '退出全屏' : '全屏设计' }}
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
              <el-empty v-else description="请先从左侧选择一个流程模型，再查看其流程图" :image-size="70" />
            </el-tab-pane>

            <!-- 节点配置 -->
            <el-tab-pane v-if="canNodeList && !modelDraft" label="节点配置" name="nodes">
              <div style="margin-bottom:12px">
                <el-button v-if="canNodeCreate" type="primary" size="small" @click="openNodeDialog(null)">新增节点</el-button>
              </div>
              <el-alert v-if="hasMultiInstanceNode" type="warning" :closable="false" show-icon style="margin-bottom:10px"
                        title="本模型含会签/票签节点：改动「会签模式」后必须重新「发布」模型，多实例任务才会生成（保存节点配置本身不会部署 BPMN）。" />
              <SkeletonList v-if="loading.nodes" :rows="8" :cols="5" />
              <el-table class="cm-target-flow" :data="nodes" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="nodeKey" label="节点编码" width="90" />
                <el-table-column prop="nodeName" label="节点名称" min-width="140" />
                <el-table-column prop="assignType" label="审批人类型" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="assignTypeTag(row.assignType)">{{ assignTypeLabel(row.assignType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="handlingMode" label="取人方式" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="handlingModeTag(row.handlingMode)">
                      {{ handlingModeLabel(row.handlingMode) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="multiType" label="会签模式" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="multiTypeTag(row.multiType)">
                      {{ multiTypeLabel(row.multiType) }}
                    </el-tag>
                    <span v-if="row.multiType === 'VOTE'" style="font-size:12px;color:#909399">
                      ≥{{ row.voteRatio == null ? 50 : row.voteRatio }}%
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="assignValue" label="审批人取值" min-width="130" show-overflow-tooltip />
                <el-table-column prop="timeoutHours" label="超时(h)" width="80" />
                <el-table-column prop="timeoutAction" label="超时动作" width="120">
                  <template #default="{ row }">{{ timeoutActionLabel(row.timeoutAction) }}</template>
                </el-table-column>
                <el-table-column prop="upLevel" label="升级层级" width="90">
                  <template #default="{ row }">
                    <span v-if="row.assignType === 'DEPT_LEADER_UP' || isEscalateAction(row.timeoutAction) || row.emptyStrategy === 'ESCALATE'">
                      {{ upLevelLabel(row.upLevel) }}
                    </span>
                    <span v-else style="color:#c0c4cc">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canNodeUpdate" link type="primary" size="small" @click="openNodeDialog(row)">编辑</el-button>
                    <el-button v-if="canNodeDelete" link type="danger" size="small" @click="deleteNode(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 流程绑定 -->
            <el-tab-pane v-if="canConfigList && !modelDraft" label="流程绑定" name="bindings">
              <div style="margin-bottom:12px">
                <el-button v-if="canConfigCreate" type="primary" size="small" @click="openBindingDialog(null)">新增绑定</el-button>
              </div>
              <SkeletonList v-if="loading.bindings" :rows="8" :cols="5" />
              <el-table :data="bindings" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="bizType" label="单据类型" width="120">
                  <template #default="{ row }">{{ flowTypeLabel(row.bizType) }}</template>
                </el-table-column>
                <el-table-column prop="configName" label="配置名称" min-width="140" />
                <el-table-column prop="amountMin" label="金额下限" width="100" :formatter="(r,c,v) => formatMoney(v)" />
                <el-table-column prop="amountMax" label="金额上限" width="100" :formatter="(r,c,v) => formatMoney(v)" />
                <el-table-column prop="priority" label="优先级" width="80" />
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status === 'NORMAL' ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canConfigUpdate" link type="primary" size="small" @click="openBindingDialog(row)">编辑</el-button>
                    <el-button v-if="canConfigDelete" link type="danger" size="small" @click="deleteBinding(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 全局配置 -->
            <el-tab-pane v-if="canConfigList && !modelDraft" label="全局配置" name="configs">
              <el-radio-group v-model="cfgType" size="small" style="margin-bottom:10px" @change="loadConfigs">
                <el-radio-button value="THRESHOLD">金额阈值</el-radio-button>
                <el-radio-button value="TIMEOUT">超时处理</el-radio-button>
                <el-radio-button value="ACTION">审批动作</el-radio-button>
                <el-radio-button value="INTEGRATION">外部集成</el-radio-button>
              </el-radio-group>
              <div style="margin-bottom:12px">
                <el-button v-if="canConfigCreate" type="primary" size="small" @click="openConfigDialog(null)">新增配置</el-button>
              </div>
              <SkeletonList v-if="loading.configs" :rows="8" :cols="5" />
              <el-table :data="configs" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="cfgCode" label="编码" width="120" />
                <el-table-column prop="cfgName" label="名称" min-width="140" />
                <el-table-column prop="cfgJson" label="配置内容" min-width="240" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span>{{ cfgSummary(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status === 'NORMAL' ? '正常' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canConfigUpdate" link type="primary" size="small" @click="openConfigDialog(row)">编辑</el-button>
                    <el-button v-if="canConfigDelete" link type="danger" size="small" @click="deleteConfig(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 运行实例 -->
            <el-tab-pane v-if="canInstanceList && !modelDraft" label="运行实例" name="instances">
              <SkeletonList v-if="loading.instances" :rows="8" :cols="5" />
              <el-table :data="instances" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="id" label="实例ID" width="120" />
                <el-table-column prop="bizType" label="业务类型" width="110">
                  <template #default="{ row }">{{ flowTypeLabel(row.bizType) }}</template>
                </el-table-column>
                <el-table-column prop="bizNo" label="单据号" width="140" />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="instanceStatusTag(row.status)">{{ instanceStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="currentNode" label="当前节点" width="120" />
                <el-table-column prop="initiatorName" label="发起人" width="100" />
                <el-table-column prop="startTime" label="发起时间" width="160" />
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
            <el-tab-pane v-if="canDelegateList && !modelDraft" label="委托代理" name="delegates">
              <div style="margin-bottom:12px">
                <el-button v-if="canDelegateCreate" type="primary" size="small" @click="openDelegateDialog(null)">新增委托</el-button>
              </div>
              <SkeletonList v-if="loading.delegates" :rows="8" :cols="5" />
              <el-table :data="delegates" size="small" stripe v-else @row-dblclick="openRowDetail">
                <el-table-column prop="fromUserName" label="委托人" width="120" />
                <el-table-column prop="toUserName" label="代理人" width="120" />
                <el-table-column prop="bizTypes" label="适用单据" min-width="180">
                  <template #default="{ row }">{{ delegateScopeLabel(row.bizTypes) }}</template>
                </el-table-column>
                <el-table-column prop="beginTime" label="开始时间" width="150"
                                 :formatter="(row, col) => formatDateTime(row[col.property])" />
                <el-table-column prop="endTime" label="结束时间" width="150"
                                 :formatter="(row, col) => formatDateTime(row[col.property])" />
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : row.status === 'EXPIRED' ? 'info' : 'danger'">{{ row.status === 'NORMAL' ? '启用' : row.status === 'EXPIRED' ? '已过期' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canDelegateUpdate" link type="danger" size="small" @click="cancelDelegate(row)">取消</el-button>
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
          <el-empty v-if="canAnyDetail" description="请从左侧选择一个流程模型，查看其节点配置、流程绑定、运行实例等详情" :image-size="80" />
          <el-empty v-else description="当前账号仅有流程模型查看权限，如需节点配置／流程绑定／运行实例等信息请联系数据配置管理员" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 模型新增/编辑弹窗已内联在基本信息Tab，此处省略 -->

    <!-- 节点编辑弹窗 -->
    <el-dialog v-model="nodeDialog.visible" :title="nodeDialog.editing ? '编辑节点' : '新增节点'" width="600px" destroy-on-close>
      <el-form ref="nodeFormRef" :model="nodeDialog.form" :rules="nodeFormRules" label-width="120px" size="default">
        <el-form-item label="节点编码" prop="nodeKey"><el-input v-model="nodeDialog.form.nodeKey" /></el-form-item>
        <el-form-item label="节点名称" prop="nodeName"><el-input v-model="nodeDialog.form.nodeName" /></el-form-item>
        <!-- F1-6（体检 E-06b）：原「节点类型」下拉已摘除。wf_node_config 没有 node_type 列，
             后端也不消费该键（全仓 Java 检索仅 XML DOM 的 Node.getNodeType()），
             留着等于让配置员以为选「抄送」就能产生抄送。真正的抄送走下方「办结后抄送 / 抄送角色」。 -->
        <el-form-item label="审批人类型">
          <el-select v-model="nodeDialog.form.assignType" style="width:100%" @change="onAssignTypeChange">
            <el-option label="指定用户" value="USER" />
            <el-option label="指定角色" value="ROLE" />
            <el-option label="发起人" value="INITIATOR" />
            <el-option label="逐级上级（沿汇报线上收 N 级）" value="DEPT_LEADER_UP" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            需要沿汇报线取人时，可用「指定角色」＋角色编码 DIRECT_SUPERVISOR（直线上级，固定 1 级），
            或用「逐级上级」＋下方「升级层级」（向上第 N 级，跳过发起人本人与 admin，
            级数不足取已到最高级，再退单据所属公司的组织负责人）。
          </div>
        </el-form-item>
        <el-form-item v-if="nodeDialog.form.assignType !== 'INITIATOR' && nodeDialog.form.assignType !== 'DEPT_LEADER_UP'" label="审批人取值" prop="assignValue">
          <el-input v-model="nodeDialog.form.assignValue" :placeholder="assignValuePlaceholder" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">{{ assignValueHint }}</div>
          <RoleHolderHint v-if="nodeDialog.form.assignType === 'ROLE'" :role-code="firstAssignRole" />
        </el-form-item>
        <el-form-item label="取人方式">
          <el-select v-model="nodeDialog.form.handlingMode" style="width:100%">
            <el-option label="自动指派（按上面的范围取一人）" value="AUTO" />
            <!-- 点名/任务池两个取值只在 ROLE 分支被消费；配到别的类型上是假功能，直接置灰 -->
            <el-option label="上一环节指定（审批人从候选里点一人）" value="PICK"
                       :disabled="nodeDialog.form.assignType !== 'ROLE'" />
            <el-option label="任务池领取（不自动指派，候选人自己领取锁定）" value="POOL"
                       :disabled="nodeDialog.form.assignType !== 'ROLE'" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            PICK 需总开关放行才生效：系统参数 approve.pick_mode.enabled=1 且本流程 model_key 在
            approve.pick_mode.models 白名单内，否则一律按自动指派执行。
            POOL＝任务池：节点不自动指派，任务停在「待领取」态，同池候选人在「我的待办」点「领取」锁定办理
            （并发抢单只有一人成功），办不动可「释放」退回任务池；无人可领时仍按下面的空策略兜底。
            候选人只有一人时不会弹选人框，只在审批弹窗里显示"将流转给谁"。
            <span v-if="nodeDialog.form.assignType !== 'ROLE'">「上一环节指定」「任务池领取」仅适用于审批人类型=指定角色的节点。</span>
          </div>
        </el-form-item>
        <el-form-item label="会签模式">
          <!-- wf_node_config.multi_type：后端 WfBpmnTemplateService 只消费 ALL/SEQ/VOTE（P1，2026-09-23）。
               选中后本次「发布」会为该节点生成 BPMN multiInstanceLoopCharacteristics 多实例任务。 -->
          <el-select v-model="nodeDialog.form.multiType" style="width:100%">
            <el-option label="任一通过（单人办理，默认）" value="ANY" />
            <el-option label="会签（全员通过才流转）" value="ALL" />
            <el-option label="顺序会签（按顺序逐个办理）" value="SEQ" />
            <el-option label="票签（按比例通过）" value="VOTE" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            候选人来自上面的「审批人类型/取值」——会签只在<b>按角色取到多人</b>时才有意义；
            取值只解析出 1 人时退化为单人办理。选「会签/顺序会签/票签」后<b>必须重新「发布」模型</b>
            （save≠deploy）才会生成多实例任务，仅保存节点配置不会生效。
            <br />「发布」只是把当前画布的 XML 原样部署一次：多实例写在 XML 里才会生效。若本模型画布不是由
            节点配置生成的（导入/手绘），发布也不会补上多实例——需先在设计器里为该节点加上
            multiInstanceLoopCharacteristics 再保存并发布。可在「设计器」中查看 XML 是否已包含。
          </div>
        </el-form-item>
        <el-form-item v-if="nodeDialog.form.multiType === 'VOTE'" label="票签阈值(%)">
          <el-input-number v-model="nodeDialog.form.voteRatio" :min="1" :max="100" :step="5" style="width:100%" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            赞成票占比 ≥ 该阈值即<b>提前通过</b>并终止剩余投票任务；反对票占比 &gt; (100−该阈值) 即提前否决。
            例：3 人节点阈值 50% ⇒ 2 票赞成即通过（无需等第 3 票）。缺省 50。
          </div>
        </el-form-item>
        <el-form-item label="超时小时数">
          <el-input-number v-model="nodeDialog.form.timeoutHours" :min="0" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="超时动作">
          <!-- WfTimeoutJob 真消费的三档。存量/外部拼写由服务端 WfTimeoutJob.normalizeAction 归一：
               AUTO_REMIND / REMIND / URGE / NOTIFY → 催办；TRANSFER / ESCALATE / AUTO_ESCALATE /
               REDISTRIBUTE / AUTO_TRANSFER → 升级；AUTO_PASS → 自动通过。 -->
          <el-select v-model="nodeDialog.form.timeoutAction" style="width:100%">
            <el-option label="催办通知" value="NOTIFY" />
            <el-option label="催办N次后升级（有缓冲期）" value="ESCALATE_AFTER_URGES" />
            <el-option label="超时自动通过" value="AUTO_PASS" />
            <el-option label="超时升级转办（ESCALATE，SLA一到立即升）" value="ESCALATE" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            四档都会真实执行：催办＝站内信发给办理人（未认领的候选组/任务池任务发给候选人，同一任务 24 小时内只催一次）；
            催办N次后升级＝先催办，累计满 <code>sys_config.wf.timeout.escalate.after_urges</code>（缺省 3）次仍无人办理才升级；
            自动通过＝写 AUTO_PASS 审批历史并给下一环节取人推进，且需命中 <code>wf.timeout.autopass.nodes</code> 白名单，否则降级为催办；
            升级＝写 ESCALATE 历史、改派给沿汇报线的真实上级并同步待办。
            升级解析不到真人时退化为催办，<b>绝不转给 admin</b>。
          </div>
        </el-form-item>
        <el-form-item
          v-if="nodeDialog.form.assignType === 'DEPT_LEADER_UP' || isEscalateAction(nodeDialog.form.timeoutAction) || nodeDialog.form.emptyStrategy === 'ESCALATE'"
          label="升级层级">
          <el-input-number v-model="nodeDialog.form.upLevel" :min="0" :max="10" :step="1" style="width:100%" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            沿部门汇报线向上取第几级真实负责人（跳过发起人本人、跳过 admin，同一人只计一次，最多 10 跳）。
            0 或 1 都表示升 1 级；级数不足时取已到最高一级，再退单据所属公司的组织负责人。
            <b>本字段同时被三处消费</b>：审批人类型=逐级上级（取人）、超时动作=升级、无候选空策略=升级。
          </div>
        </el-form-item>
        <el-form-item label="无候选空策略">
          <!-- NodeOrgResolver.applyEmptyStrategy 真消费的三档；ADMIN 档被写入闸拒绝（铁律：admin 禁入流程） -->
          <el-select v-model="nodeDialog.form.emptyStrategy" style="width:100%" placeholder="缺省 ERROR">
            <el-option label="报错阻断（默认：暴露缺配，人工补授权）" value="ERROR" />
            <el-option label="沿汇报线升级到真实负责人" value="ESCALATE" />
            <el-option label="自动跳过该节点" value="SKIP" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            仅当按角色取人在本单据所属公司下解析不到任何候选办理人时触发；新建节点不填即 ERROR（报错阻断）。
          </div>
        </el-form-item>
        <el-form-item label="允许转办">
          <el-switch v-model="nodeDialog.form.allowTransfer" :active-value="1" :inactive-value="0" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            关掉后本节点办理人不能把待办手工转给他人（服务端 transfer 直接拒绝）；确需换人由流程管理员「改派」处理。
            超时自动转办不受此开关影响，它由上面的「超时动作」决定。缺省=允许。
          </div>
        </el-form-item>
        <el-form-item label="允许加签">
          <el-switch v-model="nodeDialog.form.allowAddsign" :active-value="1" :inactive-value="0" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            关掉后本节点不能发起前/后加签（服务端 addSign 直接拒绝，待办里的「加签」按钮同时隐藏）。
            该开关需先由管理员打开灰度总闸 <code>approve.addsign_gate.enabled</code> 并把本流程列入
            <code>approve.addsign_gate.models</code> 才生效。
          </div>
        </el-form-item>
        <el-form-item label="提单即完成">
          <!-- F10（2026-09-24）：把"首节点不产生人工待办"从节点名猜测改为显式属性 first_node_auto_pass。
               后端 WfEngineFacade.autoSkipSelfConfirmFirstNode / firstApprovalNodeKeys 真消费本字段。
               不给「未配置」选项：存量行已由 V2.2.0_g4_governance_20260924.sql 显式 seed，
               后端对 null 不做更新（updateById 跳过 null），故「未配置」选项在已保存行上不可回退，
               只留 是/否 两态，未选择时由 placeholder 说明回退旧判据。 -->
          <el-select v-model="nodeDialog.form.firstNodeAutoPass" style="width:100%" clearable
                     placeholder="未配置（按旧判据：节点名含「提交」）">
            <el-option label="是：提单即完成，本节点不产生人工待办" :value="1" />
            <el-option label="否：本节点必须人工办理" :value="0" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            只对<b>流程第一个用户任务</b>有意义：置「是」时发起流程即自动完成该节点、直接落到第二个节点，
            提交人不需再点一次「提交」（原逻辑靠节点名含「提交」二字判定，属"节点名承载控制流"）。
            留空＝未配置，仍按旧判据（节点名含「提交」）判定，保证上线行为零变化；
            建议逐节点显式选择「是/否」，此后不再依赖节点名。POOL 任务池节点不受本开关影响（作业须留痕到人）。
          </div>
        </el-form-item>
        <!-- G2 传阅（2026-09-23）：节点办结后自动抄送。后端 WfCcService 真消费这两个字段；
             与上面的「办理人取值」不同——抄送人不参与取人、不影响流程走向，只往 wf_cc 投递记录。 -->
        <el-form-item label="办结后抄送">
          <el-input v-model="nodeDialog.form.ccUsers" placeholder="抄送人用户ID，英文逗号分隔，如 3892,3893（可留空）" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            本节点<b>审批通过并推进</b>后，自动把单据抄送给这些人（出现在其「流程中心 · 我的抄送」）。
            驳回不投递（驳回会终止实例，已有独立的终止通知链路）；抄送给操作人自己会被自动剔除。
            同一实例同一节点对同一人只投递一次（库层唯一键兜底）。
          </div>
        </el-form-item>
        <el-form-item label="抄送角色">
          <el-input v-model="nodeDialog.form.ccRoles" placeholder="角色编码，英文逗号分隔，如 FIN_AUDIT,FIN_MANAGER（可留空）" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            运行期展开为该角色在本租户下的<b>在职</b>成员（含用户组派生角色），与「办结后抄送」的用户ID取并集后去重投递。
            <b> 节点编码必须与已部署 BPMN 的 userTask id 完全一致</b>：配置表按设计位置连续编号 Task_1..N，
            而真实 BPMN 的 userTask id 常常跳号（缺 Task_1/Task_5 等），编码对不上的行属「孤儿行」——
            后端<b>精确匹配、不读其任何配置</b>（超时与抄送一并不生效）。配置前可先用
            <code>scripts/_audit_timeout_action.py</code> 的 ⓪ 体检确认本节点不是孤儿。
          </div>
        </el-form-item>
        <!-- G2 驳回产品化（2026-09-24）：把"谁能退、能退多远"从私有全局开关落到节点配置上。
              本控件替代了此前的三个死列 allow_reject / reject_to / reject_node
             （全仓零读取、本页原本也无控件，已在 V2.2.0_g2_reject_productize_20260924.sql 中摘除）。 -->
        <el-form-item label="驳回范围">
          <!-- 后端 WfEngineFacade.freeRejectScopeOf() 真消费本字段；生效条件是「本字段 ≠ NONE」且总闸开启 -->
          <el-select v-model="nodeDialog.form.rejectScope" style="width:100%">
            <el-option label="不允许指定节点驳回（默认）" value="NONE" />
            <el-option label="只允许退回上一节点" value="PREV_ONE" />
            <el-option label="允许退回任一已完成节点" value="ANY_DONE" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            指定节点驳回走 Flowable 的「搬执行」，会真实改动在办单据本体，故默认关闭。
            <b>需同时满足总闸</b>：系统参数 <code>wf.reject.free.enabled=1</code>，否则本字段一律不生效（总闸保留作 kill-switch）。
            「只允许退回上一节点」时后端会校验目标节点就是链上紧邻的上一已完成节点，越级退回直接报错。
          </div>
        </el-form-item>
        <el-form-item label="重新提交跳回本节点">
          <el-switch v-model="nodeDialog.form.resubmitJumpBack" :active-value="1" :inactive-value="0" />
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            开启后本节点驳回时把本节点编码记入流程实例变量 <code>rejectOriginNode</code>（可在
            「驳回可选节点」接口回显中看到），供重新提交路径决定是否跳回本节点重办。缺省=关闭。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="nodeDialog.saving" @click="saveNode">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定编辑弹窗 -->
    <el-dialog v-model="bindingDialog.visible" :title="bindingDialog.editing ? '编辑绑定' : '新增绑定'" width="600px" destroy-on-close>
      <el-form ref="bindingFormRef" :model="bindingDialog.form" :rules="bindingFormRules" label-width="120px" size="default">
        <el-form-item label="单据类型" prop="bizType">
          <el-select v-model="bindingDialog.form.bizType" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="o in bindingBizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:18px;margin-top:2px">
            取值必须<b>逐字等于业务代码发起审批时传入的业务类型</b>（如采购订单为 <code>PURCHASE_ORDER_REVIEW</code>），
            与模型编码不同名时请手动输入；拼错的类型不会报错，只会让该单据永远路由不到流程。
          </div>
        </el-form-item>
        <el-form-item label="配置名称" prop="configName"><el-input v-model="bindingDialog.form.configName" /></el-form-item>
        <el-form-item label="绑定流程模型">
          <el-input :model-value="selectedModel?.modelKey" disabled />
        </el-form-item>
        <el-form-item label="金额下限（含）">
          <el-input-number v-model="bindingDialog.form.amountMin" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="金额上限（不含）">
          <el-input-number v-model="bindingDialog.form.amountMax" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="bindingDialog.form.priority" :min="0" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="bindingDialog.form.status" style="width:100%">
            <el-option label="启用" value="NORMAL" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="bindingDialog.saving" @click="saveBinding">保存</el-button>
      </template>
    </el-dialog>

    <!-- 全局配置编辑弹窗 -->
    <el-dialog v-model="configDialog.visible" :title="configDialog.editing ? '编辑配置' : '新增配置'" width="600px" destroy-on-close>
      <el-form ref="configFormRef" :model="configDialog.form" :rules="configFormRules" label-width="120px" size="default">
        <el-form-item label="配置类型" prop="cfgType">
          <el-select v-model="configDialog.form.cfgType" style="width:100%" @change="onCfgTypeChange">
            <el-option label="金额阈值" value="THRESHOLD" />
            <el-option label="超时处理" value="TIMEOUT" />
            <el-option label="审批动作" value="ACTION" />
            <el-option label="外部集成" value="INTEGRATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置编码" prop="cfgCode"><el-input v-model="configDialog.form.cfgCode" /></el-form-item>
        <el-form-item label="配置名称" prop="cfgName"><el-input v-model="configDialog.form.cfgName" /></el-form-item>

        <!-- ===== 按配置类型动态渲染结构化表单 ===== -->
        <!-- 金额阈值 -->
        <template v-if="configDialog.form.cfgType === 'THRESHOLD'">
          <el-form-item label="金额下限（含）">
            <el-input-number v-model="configDialog.cfgData.min" :min="0" :precision="2" :step="1000" style="width:100%" />
          </el-form-item>
          <el-form-item label="金额上限（不含）">
            <el-input-number v-model="configDialog.cfgData.max" :min="0" :precision="2" :step="1000" style="width:100%" />
          </el-form-item>
          <el-form-item label="审批级别">
            <el-input-number v-model="configDialog.cfgData.level" :min="1" :max="10" :step="1" style="width:100%" />
            <div class="cfg-hint">级别越高，审批链路越长</div>
          </el-form-item>
        </template>

        <!-- 超时处理 -->
        <template v-else-if="configDialog.form.cfgType === 'TIMEOUT'">
          <el-form-item label="超时小时数">
            <el-input-number v-model="configDialog.cfgData.timeoutHours" :min="1" :max="720" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="超时动作">
            <!-- 全局规则走 WfTimeoutJob.handle 的 targetRole 分支；AUTO_APPROVE 无任何消费方，已摘除。
                 注意：AUTO_ESCALATE/REDISTRIBUTE 必须填目标角色编码才有效果（空角色=只留日志） -->
            <el-select v-model="configDialog.cfgData.action" style="width:100%">
              <el-option label="通知负责人" value="NOTIFY" />
              <el-option label="自动升级上一级（加候选角色）" value="AUTO_ESCALATE" />
              <el-option label="自动转派备用岗（加候选角色）" value="REDISTRIBUTE" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="configDialog.cfgData.action === 'AUTO_ESCALATE' || configDialog.cfgData.action === 'REDISTRIBUTE'" label="目标角色编码">
            <el-input v-model="configDialog.cfgData.targetRole" placeholder="如 FIN_MANAGER，为空则升级/转派不生效" />
          </el-form-item>
          <el-form-item label="通知用户">
            <el-input v-model="configDialog.cfgData.notifyUsers" placeholder="多用户逗号分隔，如 zhangsan,lisi" />
          </el-form-item>
          <el-form-item label="超时前提醒(h)">
            <el-input-number v-model="configDialog.cfgData.remindBefore" :min="0" :max="48" :step="1" style="width:100%" />
            <div class="cfg-hint">提前多少小时提醒，0=不提醒</div>
          </el-form-item>
        </template>

        <!-- 审批动作 -->
        <template v-else-if="configDialog.form.cfgType === 'ACTION'">
          <el-form-item label="审批动作">
            <el-select v-model="configDialog.cfgData.action" style="width:100%">
              <el-option label="通过" value="APPROVE" />
              <el-option label="驳回" value="REJECT" />
              <el-option label="转办" value="TRANSFER" />
              <el-option label="加签" value="ADDSIGN" />
            </el-select>
          </el-form-item>
          <el-form-item label="自动通过">
            <el-switch v-model="configDialog.cfgData.autoApprove" />
          </el-form-item>
          <el-form-item label="允许撤回">
            <el-switch v-model="configDialog.cfgData.allowWithdraw" />
          </el-form-item>
          <el-form-item label="默认备注">
            <el-input v-model="configDialog.cfgData.comment" type="textarea" :rows="2" placeholder="审批时默认填入的备注" />
          </el-form-item>
        </template>

        <!-- 外部集成 -->
        <template v-else-if="configDialog.form.cfgType === 'INTEGRATION'">
          <el-form-item label="系统名称">
            <el-input v-model="configDialog.cfgData.systemName" placeholder="如 ERP、OA、HR" />
          </el-form-item>
          <el-form-item label="接口地址">
            <el-input v-model="configDialog.cfgData.apiUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="HTTP方法">
            <el-select v-model="configDialog.cfgData.method" style="width:100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="重试次数">
            <el-input-number v-model="configDialog.cfgData.retryTimes" :min="0" :max="10" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="超时秒数">
            <el-input-number v-model="configDialog.cfgData.timeoutSeconds" :min="1" :max="300" :step="1" style="width:100%" />
          </el-form-item>
        </template>

        <!-- 未匹配的配置类型回退到 JSON textarea -->
        <el-form-item v-else label="配置内容 JSON">
          <el-input v-model="configDialog.form.cfgJson" type="textarea" :rows="6" placeholder='如 {"min":5000,"max":50000,"level":2}' />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="configDialog.form.status" style="width:100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="configDialog.saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 委托代理弹窗 -->
    <el-dialog v-model="delegateDialog.visible" title="新增委托代理" width="600px" destroy-on-close>
      <el-form ref="delegateFormRef" :model="delegateDialog.form" :rules="delegateFormRules" label-width="120px" size="default">
        <el-form-item label="委托人">
          <!-- 后端硬校验「委托人必须是当前登录用户」⇒ 系统带出只读值，按 Q1 口径不加红星 -->
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>
        <el-form-item label="代理人" prop="toUserId">
          <UserSelect v-model="delegateDialog.form.toUserId" show-dept />
        </el-form-item>
        <el-form-item label="适用单据类型">
          <el-input v-model="delegateDialog.form.bizTypes" placeholder="逗号分隔，空=全部。如 EXP_CLAIM,EXP_LOAN" />
        </el-form-item>
        <el-form-item label="开始时间" prop="beginTime">
          <!-- 后端 Jackson 只认 ISO 'T' 分隔，空格格式会 500（同 MyDelegate.vue） -->
          <el-date-picker v-model="delegateDialog.form.beginTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="delegateDialog.form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="委托原因">
          <el-input v-model="delegateDialog.form.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="delegateDialog.saving" @click="saveDelegate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 校验结果弹窗 -->
    <el-dialog v-model="checkResult.visible" title="流程校验结果" width="500px">
      <el-alert v-if="checkResult.data?.valid" type="success" :closable="false" title="校验通过" />
      <el-alert v-else type="error" :closable="false" title="校验失败" :description="checkResult.data?.message || '未知错误'" />
      <div v-if="checkResult.data?.errors?.length" style="margin-top:12px">
        <div v-for="(e, i) in checkResult.data.errors" :key="i" style="color:#f56c6c;margin-bottom:4px">{{ e }}</div>
      </div>
    </el-dialog>

    <!-- 模拟结果弹窗 -->
    <el-dialog v-model="simResult.visible" title="流程模拟" width="600px">
      <el-form :model="simResult" :rules="simRules" label-width="100px" style="margin-bottom:16px">
        <el-form-item label="模拟金额">
          <el-input-number v-model="simResult.amount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="费用项">
          <el-input v-model="simResult.itemName" placeholder="可空" />
        </el-form-item>
        <el-button type="primary" size="small" @click="runSimulate">执行模拟</el-button>
      </el-form>
      <div v-if="simResult.trace?.length">
        <el-timeline>
          <el-timeline-item v-for="(t, i) in simResult.trace" :key="i" :type="t.passed ? 'success' : 'primary'">
            <strong>{{ t.nodeName }}</strong> <small style="color:#999">{{ t.nodeKey }}</small>
            <div v-if="t.assignee">审批人: {{ t.assignee }}</div>
            <div v-if="t.action">动作: {{ wfActionLabel(t.action) }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
      <el-empty v-else description="点击执行模拟查看路由轨迹" :image-size="60" />
    </el-dialog>

    <!-- G3-3/G3-4 发布影响面：在途旧实例清单 + 发布 diff 记录（抽屉，不挤占配置页） -->
    <el-drawer v-model="inFlight.visible" :title="`发布影响面：${inFlight.modelName || ''}`" size="60%" direction="rtl">
      <el-tabs v-model="inFlight.tab">
        <el-tab-pane v-if="canInstanceList" label="在途旧实例" name="inflight">
          <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
            :title="`该模型在途 ${inFlight.data?.runningTotal ?? 0} 单，其中 ${inFlight.data?.staleCount ?? 0} 单仍按旧版本流程执行`"
            :description="inFlight.data?.note" />
          <div v-if="Object.keys(inFlight.data?.byProcDef || {}).length" style="margin-bottom:10px; font-size:12px; color:#606266">
            按流程定义分布：
            <el-tag v-for="(c, k) in (inFlight.data?.byProcDef || {})" :key="k" size="small" effect="plain"
              :type="k === inFlight.data?.newProcDefId ? 'success' : 'info'" style="margin-right:6px">
              {{ k }} · {{ c }} 单
            </el-tag>
          </div>
          <el-table :data="inFlight.data?.items || []" size="small" stripe max-height="420" empty-text="无在途旧实例">
            <el-table-column prop="bizNo" label="单号" width="150" show-overflow-tooltip />
            <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
            <el-table-column prop="currentNode" label="当前节点" width="120" />
            <el-table-column label="在办人" min-width="110">
              <template #default="{ row }">{{ row.currentAssigneeNames || '—' }}</template>
            </el-table-column>
            <el-table-column label="停留" width="90" align="right">
              <template #default="{ row }">{{ row.stayMinutes != null ? row.stayMinutes + ' 分' : '—' }}</template>
            </el-table-column>
            <el-table-column label="版本" min-width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="row.procDefId === inFlight.data?.newProcDefId ? 'success' : 'warning'" effect="plain">
                  {{ row.procDefId === inFlight.data?.newProcDefId ? '新版' : '旧版' }}
                </el-tag>
                <span style="margin-left:4px; font-size:11px; color:#909399">{{ row.procDefId }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="inFlight.data?.truncated" style="font-size:12px; color:#e6a23c; margin-top:6px">
            仅显示前 100 条，完整清单请到「流程监控 → 在途实例」按状态 RUNNING 查询。
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canModelUpdate" label="发布记录（diff）" name="history">
          <div style="font-size:12px; color:#909399; margin-bottom:8px">
            每次真实部署都会把 BPMN 快照与统一 diff 写入服务端 BPMN 仓库目录（配置 <code>fssc.wf.bpmn-repo-dir</code>），
            建议将该目录纳入代码仓库，流程变更即可 review / 回滚。
          </div>
          <el-table :data="inFlight.history" size="small" stripe max-height="420" empty-text="暂无发布记录（发布一次后生成）">
            <el-table-column prop="time" label="发布时间" width="160" />
            <el-table-column prop="name" label="diff 文件" min-width="220" show-overflow-tooltip />
            <el-table-column prop="summary" label="增删" width="100" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="loadDiff(row.name)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pre v-if="inFlight.diff" class="diff-view">{{ inFlight.diff }}</pre>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button size="small" @click="inFlight.visible = false">关闭</el-button>
        <el-button size="small" type="primary" @click="refreshInFlight">刷新</el-button>
      </template>
    </el-drawer>
  </div>

  <CoachMark ref="flowConfigCm" :guide-key="_guideKey" :selector="'.cm-target-flow'" title="流程配置" text="流程配置页面用于设置审批流程的节点、审批人和条件规则。" />
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
import { enumLabel, bizTypeLabel, modelCategoryLabel, wfActionLabel, BIZ_TYPE, WF_MODEL_CATEGORY, ASSIGN_TYPE, TIMEOUT_ACTION, HANDLING_MODE, MULTI_TYPE } from '@/constants/enums.js'
import CoachMark from '@/components/CoachMark.vue'
import RoleHolderHint from '@/components/common/RoleHolderHint.vue'
import { formatMoney, formatDateTime, showRowDetail } from '@/utils/format'
import { hasPerm } from '@/utils/perm'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
const flowConfigCm = ref(null)

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
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

// 被权限裁剪掉的页签说明（拼给 alert 用：原实现静默隐藏，用户只看到"残缺页面"）
const hiddenTabDesc = computed(() => {
  const miss = []
  if (!canNodeList) miss.push('节点配置（wf:node:list）')
  if (!canConfigList) miss.push('流程绑定 / 全局配置（wf:config:list）')
  if (!canInstanceList) miss.push('运行实例（wf:instance:list）')
  return '已隐藏 ' + miss.join('、') + '。这些权限当前仅授予「数据配置管理员」等配置类角色，如需查看请联系数据配置管理员开通。'
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

const bindingBizTypes = [
  { label: '员工费用报销', value: 'EXP_CLAIM' }, { label: '事前申请', value: 'EXP_APPLY' },
  { label: '对公付款', value: 'EXP_PAY' }, { label: '员工借款', value: 'EXP_LOAN' },
  { label: '员工还款', value: 'EXP_REPAY' }, { label: '资金付款', value: 'TR_PAY' },
  { label: '资金调拨', value: 'TR_TRANSFER' }, { label: '资金收款', value: 'TR_RECEIPT' },
  { label: '资金计划', value: 'TR_PLAN' }, { label: '资产新增', value: 'FA_ADD' },
  { label: '资产处置', value: 'FA_DISPOSE' }, { label: '资产调拨', value: 'FA_TRANSFER' },
  { label: '存货处置', value: 'INV_DISPOSE' }, { label: '应收收款', value: 'RECEIVABLE' },
]

// 后端写入的是 DEPLOYED（WfDesignerController 发布）/ DRAFT（新建草稿），种子导入的历史模型为 ACTIVE；
// 原本只判 'PUBLISHED'（全库 0 行）→ 84 个已部署模型在列表里一律显示「草稿」。
const PUBLISHED_MODEL_STATUS = new Set(['DEPLOYED', 'ACTIVE', 'PUBLISHED'])
function isModelPublished(m) { return PUBLISHED_MODEL_STATUS.has(m?.status) }
/** 下线是与"未发布"完全不同的状态（不可发起，但历史实例仍在跑）——不能并入"草稿"显示，否则操作者会以为随手就能发布 */
function modelStatusText(m) {
  if (m?.status === 'OFFLINE') return '已下线'
  return isModelPublished(m) ? '已发布' : '草稿'
}
function modelStatusType(m) {
  if (m?.status === 'OFFLINE') return 'danger'
  return isModelPublished(m) ? 'success' : 'info'
}

// 原本下拉只有这 14 项，而业务代码发起审批时传入的类型有 75+（采购订单 PURCHASE_ORDER_REVIEW、
// 印鉴变更 TR_SEAL_CHANGE 等都选不到）→ 只能靠写库补绑定。故按已建流程模型动态补全，
// 并配合 allow-create 允许手工输入尚未建模的类型编码。
const bindingBizTypeOptions = computed(() => {
  const map = new Map(bindingBizTypes.map(o => [o.value, o.label]))
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

const modelFormRules = {
  modelKey: commonRules.required('请输入模型编码'),
  modelName: commonRules.required('请输入模型名称'),
}
const nodeFormRules = computed(() => ({
  nodeKey: commonRules.required('请输入节点编码'),
  nodeName: commonRules.required('请输入节点名称'),
  // 发起人与「直线上级+层级」两类不需要填审批人取值，故不挂 required（否则红星是假的）
  assignValue: ['INITIATOR', 'DEPT_LEADER_UP'].includes(nodeDialog.form.assignType) ? [] : [{
    required: true, validator: (rule, value, cb) => {
    const t = nodeDialog.form.assignType
    const v = String(value == null ? '' : value).trim()
    if (!v) return cb(new Error(t === 'USER' ? '请填写用户ID（数字）' : '请填写角色编码'))
    if (t === 'USER' && !/^\s*\d+(\s*,\s*\d+)*\s*$/.test(v)) return cb(new Error('用户ID须为数字，多个用英文逗号分隔'))
    cb()
  } }],
}))
const bindingFormRules = {
  bizType: commonRules.select('请选择单据类型'),
  configName: commonRules.required('请输入配置名称'),
}
const configFormRules = {
  cfgType: commonRules.select('请选择配置类型'),
  cfgCode: commonRules.required('请输入配置编码'),
  cfgName: commonRules.required('请输入配置名称'),
}
const delegateFormRules = {
  toUserId: commonRules.select('请选择代理人'),
  // 委托生效判定只看时间区间（WfDelegateService.activeDelegateTo），区间留空＝一条永不生效的委托
  beginTime: commonRules.date('请选择开始时间'),
  endTime: commonRules.date('请选择结束时间'),
}
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
    ElMessage.success('保存成功')
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
  USER: '用户ID（数字），如 3892；当前运行时仅指派列表第一个',
  ROLE: '角色编码，多个用英文逗号分隔（任一人可办），如 FIN_AUDIT,FIN_MANAGER',
  INITIATOR: '',
}[nodeDialog.form.assignType] || ''))
const assignValueHint = computed(() => nodeDialog.form.assignType === 'ROLE'
  ? '运行时按公司维度解析这些角色在本单据公司的成员；一个成员都没有时走下方「无候选空策略」。编码须与角色管理一致（如 DIRECT_SUPERVISOR=直线上级）。'
  : nodeDialog.form.assignType === 'USER'
    ? '指定用户为单人硬指派（忽略公司/角色）。'
    : '')
/** 自动指派只取第一个角色码落地（见 WfEngineFacade 的 assignValue.split(",")[0]），故体检按它做 */
const firstAssignRole = computed(() => (nodeDialog.form.assignValue || '').split(',')[0].trim())

function openNodeDialog(row) {
  if (!selectedModel.value) { ElMessage.warning('请先选择模型'); return }
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
    ElMessage.success('保存成功')
    nodeDialog.visible = false
    await loadNodes(selectedModel.value.modelKey)
  } catch (e) { ElMessage.error(e.message) } finally { nodeDialog.saving = false }
}

async function deleteNode(row) {
  try {
    await ElMessageBox.confirm('确认删除该节点？', '删除', { type: 'warning' })
    await flowApi.nodeDelete(row.id)
    ElMessage.success('已删除')
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
  if (!selectedModel.value) { ElMessage.warning('请先选择模型'); return }
  bindingDialog.editing = !!row
  Object.assign(bindingDialog, {
    visible: true, saving: false,
    form: row ? { ...row } : {
      modelKey: selectedModel.value.modelKey, bizType: '', configName: '',
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
    ElMessage.success('保存成功')
    bindingDialog.visible = false
    await loadBindings(selectedModel.value.modelKey)
  } catch (e) { ElMessage.error(e.message) } finally { bindingDialog.saving = false }
}

async function deleteBinding(row) {
  try {
    await ElMessageBox.confirm('确认删除该绑定？', '删除', { type: 'warning' })
    await flowApi.processDelete(row.id)
    ElMessage.success('已删除')
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
          min: '下限', max: '上限', level: '级别',
          timeoutHours: '超时(h)', action: '动作', notifyUsers: '通知',
          targetRole: '目标角色',
          remindBefore: '提前提醒(h)',
          autoApprove: '自动通过', allowWithdraw: '可撤回', comment: '备注',
          systemName: '系统', apiUrl: '接口', method: '方法',
          retryTimes: '重试', timeoutSeconds: '超时(s)',
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
    ElMessage.success('保存成功')
    configDialog.visible = false
    await loadConfigs()
  } catch (e) { ElMessage.error(e.message) } finally { configDialog.saving = false }
}

async function deleteConfig(row) {
  try {
    await ElMessageBox.confirm('确认删除该配置？', '删除', { type: 'warning' })
    await flowConfigApi.delete(row.id)
    ElMessage.success('已删除')
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
  || store.user?.username || store.user?.userName || '当前用户')

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
    ElMessage.success('保存成功')
    delegateDialog.visible = false
    await loadDelegates()
  } catch (e) { ElMessage.error(e.message) } finally { delegateDialog.saving = false }
}

async function cancelDelegate(row) {
  try {
    await ElMessageBox.confirm('确认取消该委托？', '确认', { type: 'warning' })
    await flowApi.delegateCancel(row.id)
    ElMessage.success('已取消')
    await loadDelegates()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ========== 模型操作 ==========
const checkResult = reactive({ visible: false, data: null })
async function validateModel() {
  try {
    checkResult.data = await designerApi.validate(selectedModel.value.bpmnXml || '')
    checkResult.visible = true
  } catch (e) { ElMessage.error(e.message) }
}

const simResult = reactive({ visible: false, amount: 10000, itemName: '', trace: [] })
function simulateModel() {
  if (!selectedModel.value.bpmnXml) { ElMessage.warning('该模型未包含 BPMN 流程定义'); return }
  simResult.amount = 10000
  simResult.itemName = ''
  simResult.trace = []
  simResult.visible = true
}
async function runSimulate() {
  try {
    const r = await designerApi.simulate(selectedModel.value.bpmnXml, simResult.amount, simResult.itemName)
    simResult.trace = r?.trace || []
    if (!simResult.trace.length) ElMessage.warning('模拟未返回路由轨迹')
    else ElMessage.success('模拟完成，共 ' + simResult.trace.length + ' 步')
  } catch (e) { ElMessage.error(e.message) }
}

async function publishModel() {
  let warn = ''
  try {
    const f = await designerApi.inFlight(selectedModel.value.modelKey)
    if (f?.staleCount) warn = `\n注意：当前有 ${f.staleCount} 单在途仍按旧版本执行，新版本只作用于之后发起的单据。`
  } catch (e) { /* 影响面查询失败不阻断发布 */ }
  try {
    await ElMessageBox.confirm(`发布流程「${selectedModel.value.modelName}」？将生成新版本并立即生效${warn}`, '发布', { type: 'warning' })
    await designerApi.publish(selectedModel.value)
    ElMessage.success('已发布')
    await loadModels()
    const found = models.value.find(m => m.id === selectedModel.value.id)
    if (found) { selectedModel.value = found; Object.assign(modelForm, { ...found }); clearModelValidate() }
    // 发布后直接把影响面摊开（在途旧实例清单 + 本次 diff），不再只提示"已发布"
    await openInFlight()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '发布失败') }
}

// ========== G3-3/G3-4 发布影响面（在途旧实例 + BPMN 仓库化 diff） ==========
const inFlight = reactive({ visible: false, tab: 'inflight', modelName: '', data: null, history: [], diff: '' })

async function openInFlight() {
  if (!selectedModel.value?.modelKey) { ElMessage.warning('请先选择流程模型'); return }
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
    inFlight.diff = r?.content || '（diff 文件不存在或已被清理）'
  } catch (e) { ElMessage.error(e.message) }
}

async function exportRepo() {
  try {
    const r = await designerApi.exportRepo()
    ElMessage.success(`已导出 ${r.exported}/${r.models} 个模型到 ${r.dir}`)
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
    ElMessage.success('已导出')
  } catch (e) { ElMessage.error(e.message) }
}

async function deleteModel() {
  try {
    await ElMessageBox.confirm(`确认删除流程模型「${selectedModel.value.modelName}」？`, '删除', { type: 'warning' })
    await flowApi.modelDelete(selectedModel.value.id)
    ElMessage.success('已删除')
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
    ElMessage.success('流程图已保存')
    await loadModels()
  } catch (e) { ElMessage.error(e.message || '流程图保存失败') }
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
      ({ value: key } = await ElMessageBox.prompt('请输入模型编码（唯一）', '导入流程模型', {
        inputValue: 'IMPORT_' + Date.now().toString().slice(-6),
        inputPattern: /^[A-Za-z][A-Za-z0-9_-]{1,49}$/,
        inputErrorMessage: '编码须以字母开头，2-50 位字母、数字、下划线或连字符',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      }))
    } catch { return }
    if (!key) return
    try {
      await designerApi.importModel({ modelKey: key, modelName: key, bpmnXml: text })
      ElMessage.success('导入成功：' + key)
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
  if (!codes.length) return '全部单据类型'
  return codes.map(c => bizTypeLabel(c)).join('、')
}
function assignTypeLabel(v) {
  return enumLabel(ASSIGN_TYPE, v)
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
  return (n < 1 ? 1 : n) + ' 级'
}
/** 取人方式：列/详情用。老后端不返回该字段时显示"自动指派"（等价 DB 默认值，不是缺数据） */
function handlingModeLabel(v) {
  return enumLabel(HANDLING_MODE, v || 'AUTO')
}
/** POOL 用 primary 色：它意味着该节点会有"待领取"任务停在池里，是运维要盯的状态 */
function handlingModeTag(v) {
  const m = v || 'AUTO'
  return m === 'PICK' ? 'warning' : m === 'POOL' ? 'primary' : 'info'
}
function timeoutActionLabel(v) {
  return enumLabel(TIMEOUT_ACTION, v) || '—'
}
/** 会签模式：ANY 为列默认值（普通单任务），显示"任一通过"而非空白 */
function multiTypeLabel(v) {
  return enumLabel(MULTI_TYPE, v || 'ANY')
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
      ElMessage.warning('当前浏览器不支持全屏 API')
    }
  } catch (e) {
    ElMessage.error('全屏失败：' + e.message)
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
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-title { font-size: 15px; font-weight: 600; color: #303133; }
.model-list { max-height: 600px; overflow-y: auto; }
.model-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.model-item:hover { background: #f5f7fa; border-color: #e4e7ed; }
.model-item.active { background: #ecf5ff; border-color: #b3d8ff; }
.model-item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.model-name { font-weight: 600; font-size: 14px; color: #303133; }
.model-meta { display: flex; gap: 12px; font-size: 12px; color: #909399; }
.model-detail-header { display: flex; align-items: center; }
.cfg-hint { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 4px; }
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
