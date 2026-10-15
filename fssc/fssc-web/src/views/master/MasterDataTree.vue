<template>
  <div class="md-tree-page">
    <!-- RA-PAGE-21 2.3：会计科目/成本中心双形态并入一个菜单（原 205 会计科目退册，宿主=206）——切换即跳对侧路由 -->
    <el-radio-group :model-value="mode" size="small" style="margin-bottom:8px" @change="onModeSwitch">
      <el-radio-button value="subject">会计科目</el-radio-button>
      <el-radio-button value="costCenter">成本中心</el-radio-button>
    </el-radio-group>
    <el-row :gutter="16">
      <!-- 左侧：树 -->
      <el-col :span="8">
        <el-card shadow="never" class="tree-card">
          <template #header>
            <div class="card-header">
              <span class="header-title">{{ title }}</span>
              <div>
                <el-button size="small" @click="expandAll">展开</el-button>
                <el-button size="small" @click="collapseAll">收起</el-button>
                <el-button type="primary" size="small" @click="addRoot">新增根节点</el-button>
              </div>
            </div>
          </template>
          <!-- R5-D-042（公司维度缺失）：公司筛选与弹窗「所属公司」同源（orgApi.companyTree 词表，不另造清单）；
               选中值随树请求入服务端 query.companyId，服务端再叠加登录人 data_scope（选了公司只会更窄） -->
          <el-select v-model="query.companyId" placeholder="公司" clearable filterable size="small"
                     style="width:100%;margin-bottom:8px" @change="loadTree">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.companyCode" :value="c.id" />
          </el-select>
          <el-input v-model="filterText" placeholder="搜索编码/名称..." clearable size="small" style="margin-bottom:8px" />
          <el-button size="small" @click="filterText = ''" style="margin-bottom:8px">重置</el-button>
          <SkeletonDetail v-if="loading" :rows="6" />
          <div class="tree-container" v-else>
            <el-tree
              ref="treeRef"
              :data="treeData"
              :props="treeProps"
              node-key="id"
              :filter-node-method="filterNode"
              :expand-on-click-node="false"
              highlight-current
              default-expand-all
              @node-click="onNodeClick"
            >
              <template #default="{ node, data: nodeData }">
                <span class="tree-node-content">
                  <span>{{ node.label }}</span>
                  <span class="tree-node-code" v-if="getCode(nodeData)">[{{ getCode(nodeData) }}]</span>
                  <el-tag v-if="nodeData.status === 'DISABLED'" size="small" type="danger" style="margin-left:4px">停用</el-tag>
                  <!-- R5-D-042：行归属公司——值取服务端 nodeData.companyId，名称按 orgApi.companyTree 词表翻译
                       （md_subject／md_cost_center 均无 company_name 列，加列＝DDL，禁） -->
                  <span class="tree-node-company" v-if="companyLabel(nodeData)">[{{ companyLabel(nodeData) }}]</span>
                  <span class="tree-node-actions">
                    <el-button link type="primary" size="small" @click.stop="addChild(nodeData)">子级</el-button>
                  </span>
                </span>
              </template>
            </el-tree>
            <el-empty v-if="!treeData.length && !loading" :description="`暂无${title}数据`" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：详情表单 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="header-title">{{ selectedNode ? (selectedNode.id ? '编辑' : '新增') : '详情' }}</span>
              <div v-if="selectedNode">
                <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
                <el-button v-if="selectedNode.id" size="small" :type="selectedNode.status === 'NORMAL' ? 'warning' : 'success'" @click="toggleEnable">
                  {{ selectedNode.status === 'NORMAL' ? '停用' : '启用' }}
                </el-button>
                <el-button v-if="selectedNode.id" type="danger" size="small" @click="del">删除</el-button>
              </div>
            </div>
          </template>

          <!-- 会计科目表单 -->
          <el-form v-if="mode === 'subject' && selectedNode" ref="formRef" :model="selectedNode" :rules="subjectRules" label-width="120px" size="default" style="max-width:600px">
            <el-form-item label="上级科目">
              <el-input :model-value="selectedNode.parentId ? findName(selectedNode.parentId) : '（根节点）'" disabled />
            </el-form-item>
            <!-- R5-D-042：只读展示行归属公司（建档时的公司主体由后端按登录主体落，不在本页改口径） -->
            <el-form-item v-if="selectedNode.id" label="所属公司">
              <el-input :model-value="companyLabel(selectedNode) || '（未定）'" disabled />
            </el-form-item>
            <el-form-item label="科目编码" prop="subjectCode"><el-input v-model="selectedNode.subjectCode" /></el-form-item>
            <el-form-item label="科目名称" prop="subjectName"><el-input v-model="selectedNode.subjectName" /></el-form-item>
            <el-form-item label="科目层级">
              <el-input-number v-model="selectedNode.subjectLevel" :min="1" :max="5" :controls="false" style="width:100%" />
            </el-form-item>
            <el-form-item label="科目类型">
              <el-select v-model="selectedNode.subjectType" style="width:100%">
                <el-option label="资产" value="ASSET" />
                <el-option label="负债" value="LIABILITY" />
                <el-option label="权益" value="EQUITY" />
                <el-option label="收入" value="REVENUE" />
                <el-option label="费用" value="COST" />
              </el-select>
            </el-form-item>
            <el-form-item label="余额方向">
              <el-radio-group v-model="selectedNode.direction">
                <el-radio value="DEBIT">借方</el-radio>
                <el-radio value="CREDIT">贷方</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="是否末级">
              <el-switch v-model="selectedNode.isLeaf" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="辅助核算项">
              <el-checkbox-group v-model="auxArr">
                <el-checkbox value="company">公司</el-checkbox>
                <el-checkbox value="dept">部门</el-checkbox>
                <el-checkbox value="project">项目</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="selectedNode.remark" type="textarea" :rows="2" /></el-form-item>
          </el-form>

          <!-- 成本中心表单 -->
          <el-form v-if="mode === 'costCenter' && selectedNode" ref="formRef" :model="selectedNode" :rules="costCenterRules" label-width="120px" size="default" style="max-width:600px">
            <el-form-item label="上级成本中心">
              <el-input :model-value="selectedNode.parentId ? findName(selectedNode.parentId) : '（根节点）'" disabled />
            </el-form-item>
            <!-- R5-D-042：只读展示行归属公司 -->
            <el-form-item v-if="selectedNode.id" label="所属公司">
              <el-input :model-value="companyLabel(selectedNode) || '（未定）'" disabled />
            </el-form-item>
            <el-form-item label="编码" prop="ccCode"><el-input v-model="selectedNode.ccCode" /></el-form-item>
            <el-form-item label="名称" prop="ccName"><el-input v-model="selectedNode.ccName" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="selectedNode.ccType" style="width:100%">
                <el-option label="部门" value="DEPT" />
                <el-option label="项目" value="PROJECT" />
                <el-option label="区域" value="REGION" />
                <el-option label="虚拟" value="VIRTUAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="selectedNode.sort" :min="0" :controls="false" style="width:100%" />
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="selectedNode.remark" type="textarea" :rows="2" /></el-form-item>
          </el-form>

          <el-empty v-if="!selectedNode" description="请从左侧选择一个节点查看或编辑详情" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// R5-D-042：http/orgApi 直取——masterApi.subject.tree／costCenter.tree 是无参闭包（api.js 不在本缺陷改动面内），
// 公司维度必须随请求入服务端，故本页按同一 /tenant/masterdata/** 前缀自行带参调用，不改公共 api 层
import { masterApi, orgApi, http } from '../../api'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'

const props = defineProps({
  mode: { type: String, required: true }, // 'subject' | 'costCenter'
  title: { type: String, default: '' },
})

const router = useRouter()
// 科目⇄成本中心切换（RA-PAGE-21 2.3）
function onModeSwitch(v) {
  if (v === props.mode) return
  router.push(v === 'subject' ? '/master/subject' : '/master/cost-center')
}

const treeRef = ref()
const loading = ref(false)
const saving = ref(false)
const flatList = ref([])
const selectedNode = ref(null)
const filterText = ref('')
const auxArr = ref([])
// 两种 mode 各只有一个 el-form 挂载（v-if），共用一个 ref
const formRef = ref(null)

const api = computed(() => props.mode === 'subject' ? masterApi.subject : masterApi.costCenter)

// ---------------- R5-D-042：公司维度（列表/树归属可见＋可筛） ----------------
// 筛选值走服务端（?companyId=），服务端 SubjectService.tree(Long) 已按 md_subject.company_id 落谓词并叠 @DataScope；
// 未选＝不带参，由服务端按登录人 data_scope 收口。SubjectController.tree() 已补绑 companyId（R5-D-043/158 顺带修，
// 见其控制器注解）；CostCenterController.tree() 暂不绑（不在本次改动面），故下方另做一道「按 id 精确」的可见集
// 收口兜底——它只收窄、不放宽，控制器补参后与服务端结果同口径（幂等），不会掩盖越权数据。
const query = reactive({ companyId: null })
const treeUrl = computed(() => `/tenant/masterdata/${props.mode === 'subject' ? 'subject' : 'cost-center'}/tree`)
// 词表＝orgApi.companyTree 摊平（与 BankAccount.vue 同一机制），不硬编码公司清单
const companies = ref([])

function flattenCompanies(nodes, out = []) {
  for (const n of nodes || []) {
    out.push(n)
    if (n.children && n.children.length) flattenCompanies(n.children, out)
  }
  return out
}

async function loadCompanies() {
  if (companies.value.length) return
  try {
    companies.value = flattenCompanies(await orgApi.companyTree() || [])
  } catch { /* 词表为空时列显示裸 companyId，不影响服务端收口 */ }
}

/** 公司名翻译：只查词表，绝不拼「公司+id」之类字符串冒充归属（那是把未知说成已知） */
function companyName(id) {
  if (id == null) return ''
  const c = companies.value.find((x) => x.id === id)
  return c ? (c.companyName || c.companyCode || '') : String(id)
}

/** 行归属文案：md_subject.company_id 为空＝租户级共用科目表（实体注释口径），成本中心无此语义则留空 */
function companyLabel(data) {
  if (!data) return ''
  if (data.companyId == null) return props.mode === 'subject' ? '租户共用' : ''
  return companyName(data.companyId)
}

/**
 * 兜底可见集收口（仅按 id 精确判定，不做公司名模糊匹配）：
 * 科目＝本公司 ∪ 租户共用；成本中心＝仅本公司（无共用语义）。
 */
function withinCompany(rows) {
  const cid = query.companyId
  if (cid == null) return rows
  if (props.mode === 'subject') return rows.filter((r) => r.companyId == null || r.companyId === cid)
  return rows.filter((r) => r.companyId === cid)
}

// 编码/名称＝主数据业务键，人工维护（非系统取号，不适用口径 Q1）；与后端 SubjectService / CostCenterService 同口径。
// R5-D-043／R5-D-158：科目编码加「仅 ASCII」规则（与后端 CODE_PATTERN 同源：以数字开头、只含数字与 . - _、≤30 位），
// 拦掉中文/全角/空格（存量 265 行把中文名写进编码列）；名称 required（存量 216 行空名，后端 assertWritable 已同判）。
const SUBJECT_CODE_ASCII = /^[0-9][0-9._-]{0,29}$/
const subjectRules = {
  subjectCode: [
    ...commonRules.required('请输入科目编码'),
    { pattern: SUBJECT_CODE_ASCII, message: '科目编码只能使用数字（可用小数点或短横线作层级分隔，如 1002、1002.01），需以数字开头且不超过 30 位，不能包含中文、空格或全角字符', trigger: 'blur' },
  ],
  subjectName: commonRules.required('请输入科目名称'),
}
const costCenterRules = {
  ccCode: commonRules.required('请输入编码'),
  ccName: commonRules.required('请输入名称'),
}
const treeProps = computed(() => {
  if (props.mode === 'subject') return { label: 'subjectName', children: 'children' }
  return { label: 'ccName', children: 'children' }
})
const treeData = ref([])

function getCode(node) {
  return props.mode === 'subject' ? node.subjectCode : node.ccCode
}
function findName(id) {
  const found = flatList.value.find(x => x.id === id)
  return found ? (props.mode === 'subject' ? found.subjectName : found.ccName) : ''
}

async function loadTree() {
  loading.value = true
  try {
    // R5-D-042：公司维度入请求（服务端谓词＋data_scope 为权威口径），返回后再按 id 精确收口兜底
    const data = await http.get(treeUrl.value, {
      params: query.companyId != null ? { companyId: query.companyId } : {},
    })
    flatList.value = withinCompany(data || [])
    treeData.value = buildTree(flatList.value)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function buildTree(list) {
  const map = {}
  const roots = []
  list.forEach(item => { map[item.id] = { ...item, children: [] } })
  list.forEach(item => {
    const node = map[item.id]
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  })
  // 移除空 children 数组（避免箭头显示）
  const cleanChildren = (nodes) => {
    nodes.forEach(n => {
      if (n.children && n.children.length === 0) delete n.children
      else if (n.children) cleanChildren(n.children)
    })
  }
  cleanChildren(roots)
  return roots
}

function onNodeClick(data) {
  selectedNode.value = { ...data }
  if (props.mode === 'subject') {
    auxArr.value = selectedNode.value.auxItems ? selectedNode.value.auxItems.split(',') : []
  }
}

function addChild(parentData) {
  selectedNode.value = {
    parentId: parentData.id,
    subjectLevel: (parentData.subjectLevel || 1) + 1,
    subjectType: parentData.subjectType,
    direction: parentData.direction,
    isLeaf: 1,
    status: 'NORMAL',
  }
  if (props.mode === 'costCenter') {
    selectedNode.value = {
      parentId: parentData.id,
      ccType: parentData.ccType,
      sort: 0,
      status: 'NORMAL',
    }
  }
  auxArr.value = []
}

function addRoot() {
  if (props.mode === 'subject') {
    selectedNode.value = { parentId: null, subjectLevel: 1, subjectType: 'ASSET', direction: 'DEBIT', isLeaf: 1, status: 'NORMAL' }
  } else {
    selectedNode.value = { parentId: null, ccType: 'DEPT', sort: 0, status: 'NORMAL' }
  }
  auxArr.value = []
}

async function save() {
  if (!(await validateForms([formRef]))) return
  if (props.mode === 'subject') {
    selectedNode.value.auxItems = auxArr.value.join(',')
  }
  saving.value = true
  try {
    if (selectedNode.value.id) {
      await api.value.update({ ...selectedNode.value })
    } else {
      await api.value.create({ ...selectedNode.value })
    }
    ElMessage.success('保存成功')
    await loadTree()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function toggleEnable() {
  const enable = selectedNode.value.status !== 'NORMAL'
  try {
    await api.value.enable(selectedNode.value.id, enable)
    ElMessage.success(enable ? '已启用' : '已停用')
    selectedNode.value.status = enable ? 'NORMAL' : 'DISABLED'
    await loadTree()
  } catch (e) { ElMessage.error(e.message) }
}

async function del() {
  try {
    await ElMessageBox.confirm(`确认删除「${getCode(selectedNode.value)} ${findName(selectedNode.value.id)}」？`, '删除', { type: 'warning' })
    await api.value.delete(selectedNode.value.id)
    ElMessage.success('已删除')
    selectedNode.value = null
    await loadTree()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 搜索过滤
function filterNode(value, data) {
  if (!value) return true
  const name = props.mode === 'subject' ? data.subjectName : data.ccName
  const code = getCode(data)
  return (name || '').toLowerCase().includes(value.toLowerCase()) ||
         (code || '').toLowerCase().includes(value.toLowerCase())
}

watch(filterText, (v) => { treeRef.value?.filter(v) })

function expandAll() {
  const arr = [...treeData.value]
  const set = new Set()
  const walk = (nodes) => nodes.forEach(n => { set.add(n.id); if (n.children) walk(n.children) })
  walk(arr)
  treeRef.value?.setExpandedKeys([...set])
}

function collapseAll() {
  // el-tree 没有直接 collapseAll，遍历关闭
  const collapse = (nodes) => {
    nodes.forEach(n => {
      if (n.id) treeRef.value?.setExpanded(n.id, false)
      if (n.children) collapse(n.children)
    })
  }
  collapse(treeData.value)
}

onMounted(async () => {
  // R5-D-042：先取公司词表（只用于「公司」归属翻译），再拉树（树数据本身由服务端按公司维度收口）
  await loadCompanies()
  loadTree()
})
</script>

<style scoped>
.md-tree-page { min-height: 100%; }
.tree-card { height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
.tree-container { max-height: 600px; overflow-y: auto; }
.tree-node-content { display: flex; align-items: center; flex: 1; }
.tree-node-code { margin-left: 6px; font-size: 12px; color: #909399; }
.tree-node-company { margin-left: 6px; font-size: 12px; color: #b37700; }
.tree-node-actions { margin-left: auto; }
</style>
