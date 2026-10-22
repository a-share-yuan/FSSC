import { ref, reactive } from 'vue'
import { queryVariantApi } from '@/api.js'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 查询方案（Variant）组合式函数。
 *
 * @param {string|Function} pageKey - 页面标识（如 expense_bill_list），或返回该标识的函数
 * @param {object} searchForm - 响应式搜索表单对象（reactive）
 * @param {Function} onApply - 应用方案后的回调（如 reload）
 */
export function useQueryVariant(pageKey, searchForm, onApply) {
  // 传函数是因为宿主可能是「同一组件实例被多条路由复用」的列表页（router 未加 :key 时
  // props.configKey 会在实例存活期内原地变），定死字符串会把方案存到别的页面键下。
  const resolveKey = () => (typeof pageKey === 'function' ? pageKey() : pageKey)

  const variantList = ref([])
  const currentVariantId = ref(null)
  const variantLoading = ref(false)
  const saveDialogVisible = ref(false)
  /** 保存弹窗的表单模型（宿主可直接 :model 绑定并挂 prop 校验） */
  const saveDialogForm = reactive({ variantName: '' })
  const saveDialogUpdate = ref(false)

  /** 加载方案列表 */
  async function loadVariants() {
    const key = resolveKey()
    if (!key) return
    variantLoading.value = true
    try {
      variantList.value = await queryVariantApi.list(key) || []
    } catch (e) {
      console.warn('加载查询方案失败', e)
      variantList.value = []
    } finally {
      variantLoading.value = false
    }
  }

  /** 只把方案的筛选条件写进 searchForm，不触发查询（供启动时用，避免二次请求） */
  function fillForm(variant) {
    if (!variant) return false
    currentVariantId.value = variant.id
    if (variant.filtersJson) {
      try {
        const filters = JSON.parse(variant.filtersJson)
        Object.keys(filters).forEach((key) => {
          searchForm[key] = filters[key]
        })
      } catch (e) {
        console.warn('解析方案筛选条件失败', e)
      }
    }
    return true
  }

  /** 应用指定方案：填充 searchForm 后立即重查 */
  function applyVariant(variant) {
    if (!fillForm(variant)) return
    if (onApply) onApply()
  }

  /** 选择方案（从下拉切换） */
  function selectVariant(id) {
    const v = variantList.value.find((item) => item.id === id)
    if (v) applyVariant(v)
  }

  /** 打开保存方案对话框 */
  function openSaveDialog() {
    saveDialogForm.variantName = ''
    saveDialogUpdate.value = false
    saveDialogVisible.value = true
  }

  /** 打开更新方案对话框（覆盖当前方案） */
  function openUpdateDialog() {
    const cur = variantList.value.find((v) => v.id === currentVariantId.value)
    if (!cur) {
      ElMessage.warning('请先选择一个方案')
      return
    }
    saveDialogForm.variantName = cur.variantName
    saveDialogUpdate.value = true
    saveDialogVisible.value = true
  }

  /** 执行保存方案 */
  async function confirmSave() {
    const name = saveDialogForm.variantName?.trim()
    if (!name) {
      ElMessage.warning('请输入方案名称')
      return
    }
    const filtersJson = JSON.stringify({ ...searchForm })
    const cur = variantList.value.find((v) => v.id === currentVariantId.value)
    try {
      if (saveDialogUpdate.value && cur) {
        // 后端 update() 走 updateById 且无条件重算 isDefault，只提交三个字段会把「默认方案」标记洗掉
        await queryVariantApi.update({
          id: cur.id,
          variantName: name,
          pageKey: cur.pageKey,
          isDefault: cur.isDefault,
          filtersJson,
        })
        ElMessage.success('方案已更新')
      } else {
        // 新增方案（同页同名后端会拒）
        await queryVariantApi.create({
          variantName: name,
          pageKey: resolveKey(),
          filtersJson,
        })
        ElMessage.success('方案已保存')
      }
    } catch (e) {
      ElMessage.error(e.message)
      return
    }
    saveDialogVisible.value = false
    await loadVariants()
  }

  /** 删除方案 */
  async function deleteVariant(id) {
    if (!id) return
    try {
      await ElMessageBox.confirm('确定删除该查询方案？', '确认', { type: 'warning' })
    } catch {
      return
    }
    try {
      await queryVariantApi.delete(id)
      if (currentVariantId.value === id) {
        currentVariantId.value = null
      }
      ElMessage.success('方案已删除')
      await loadVariants()
    } catch (e) {
      ElMessage.error(e.message)
    }
  }

  /** 设为默认方案 */
  async function setDefaultVariant(id) {
    if (!id) return
    try {
      await queryVariantApi.setDefault(id)
      ElMessage.success('已设为默认方案')
      await loadVariants()
    } catch (e) {
      ElMessage.error(e.message)
    }
  }

  /**
   * 启动时用：命中默认方案就填充 searchForm（不查，交给宿主随后的一次 load），并拉方案列表。
   * @returns {Promise<boolean>} 是否应用了默认方案
   */
  async function loadDefaultVariant() {
    let applied = false
    try {
      applied = fillForm(await queryVariantApi.getDefault(resolveKey()))
    } catch (e) {
      // 无默认方案时忽略
    }
    await loadVariants()
    return applied
  }

  return {
    variantList,
    currentVariantId,
    variantLoading,
    saveDialogVisible,
    saveDialogForm,
    saveDialogUpdate,
    loadVariants,
    selectVariant,
    openSaveDialog,
    openUpdateDialog,
    confirmSave,
    deleteVariant,
    setDefaultVariant,
    loadDefaultVariant,
  }
}