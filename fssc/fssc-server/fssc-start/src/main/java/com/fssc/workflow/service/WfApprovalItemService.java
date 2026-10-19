package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.workflow.entity.WfApprovalItem;
import com.fssc.workflow.mapper.WfApprovalItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 流程审批项解析与维护（wf_approval_item）——本表的唯一服务入口。
 *
 * 解析口径与 {@link WfNodeConfigResolver} 完全一致——{@code modelKey + nodeKey} <b>精确匹配</b>，
 * 匹配不到即返回空，<b>不做任何位置兜底</b>（历史教训：位置兜底曾把策略落到错误节点上造成越权）。
 *
 * <p><b>零影响</b>：未配置审批项的节点 {@link #listEnabled} 返回空列表、{@link #resolve} 返回 null，
 * 审批主链路完全不走审批项分支，行为与改造前逐字一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfApprovalItemService {

    private final WfApprovalItemMapper wfApprovalItemMapper;

    /** 某节点启用的审批项（按 sort 升序）。无配置返回空列表，不抛异常。 */
    public List<WfApprovalItem> listEnabled(String modelKey, String nodeKey) {
        if (modelKey == null || modelKey.isBlank() || nodeKey == null || nodeKey.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<WfApprovalItem> rows = wfApprovalItemMapper.selectList(
                    new LambdaQueryWrapper<WfApprovalItem>()
                            .eq(WfApprovalItem::getModelKey, modelKey)
                            .eq(WfApprovalItem::getNodeKey, nodeKey)
                            .eq(WfApprovalItem::getEnabled, 1)
                            .orderByAsc(WfApprovalItem::getSort)
                            .orderByAsc(WfApprovalItem::getId));
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            // 读配置失败不得让审批主链路塌掉：按"该节点无审批项"处理（等价改造前行为）
            log.warn("审批项读取失败（按无审批项处理）modelKey={} nodeKey={}: {}", modelKey, nodeKey, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按编码解析审批项。命中即返回；未命中/已停用/编码为空 返回 {@code null}。
     *
     * <p>调用方（{@code WfEngineFacade.complete}）对 null 的处置是<b>明确拒绝</b>而非静默放行——
     * 审批人点了一个按钮却什么都没发生，比报错更难排查。
     */
    public WfApprovalItem resolve(String modelKey, String nodeKey, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        String code = itemCode.trim();
        for (WfApprovalItem it : listEnabled(modelKey, nodeKey)) {
            if (code.equalsIgnoreCase(it.getItemCode() == null ? "" : it.getItemCode().trim())) {
                return it;
            }
        }
        return null;
    }

    /** 类别归一：非法/空值一律归为 {@code POSITIVE}（按"继续流转"最保守处理，不产生破坏性动作） */
    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return WfApprovalItem.CAT_POSITIVE;
        }
        String c = category.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case WfApprovalItem.CAT_REJECT -> WfApprovalItem.CAT_REJECT;
            case WfApprovalItem.CAT_TERMINATE -> WfApprovalItem.CAT_TERMINATE;
            default -> WfApprovalItem.CAT_POSITIVE;
        };
    }

    // ==================== 配置维护（管理端） ====================

    public PageResult<WfApprovalItem> page(PageQuery q, String modelKey, String nodeKey, String keyword) {
        LambdaQueryWrapper<WfApprovalItem> w = new LambdaQueryWrapper<>();
        if (modelKey != null && !modelKey.isBlank()) {
            w.eq(WfApprovalItem::getModelKey, modelKey);
        }
        if (nodeKey != null && !nodeKey.isBlank()) {
            w.eq(WfApprovalItem::getNodeKey, nodeKey);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(WfApprovalItem::getItemName, keyword).or().like(WfApprovalItem::getItemCode, keyword));
        }
        w.orderByAsc(WfApprovalItem::getModelKey).orderByAsc(WfApprovalItem::getNodeKey)
                .orderByAsc(WfApprovalItem::getSort).orderByAsc(WfApprovalItem::getId);
        Page<WfApprovalItem> p = wfApprovalItemMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    public Long create(WfApprovalItem item) {
        validate(item);
        assertCodeUnique(item, null);
        wfApprovalItemMapper.insert(item);
        return item.getId();
    }

    public void update(WfApprovalItem item) {
        AssertUtil.notNull(item.getId(), ErrorCode.PARAM_INVALID, "审批项ID必填");
        validate(item);
        assertCodeUnique(item, item.getId());
        wfApprovalItemMapper.updateById(item);
    }

    public void delete(Long id) {
        AssertUtil.notNull(id, ErrorCode.PARAM_INVALID, "审批项ID必填");
        // 物理删除：软删后唯一键不释放，同一 model+node+code 就再也建不回来（与 wf_node_config 同口径）
        wfApprovalItemMapper.hardDelete(id);
    }

    private void validate(WfApprovalItem item) {
        AssertUtil.notBlank(item.getModelKey(), ErrorCode.PARAM_INVALID, "请选择流程（modelKey）");
        AssertUtil.notBlank(item.getNodeKey(), ErrorCode.PARAM_INVALID, "请选择节点（nodeKey）");
        AssertUtil.notBlank(item.getItemCode(), ErrorCode.PARAM_INVALID, "请输入审批项编码");
        AssertUtil.notBlank(item.getItemName(), ErrorCode.PARAM_INVALID, "请输入审批项名称");
        if (item.getCategory() != null && !item.getCategory().isBlank()) {
            String c = item.getCategory().trim().toUpperCase(Locale.ROOT);
            if (!WfApprovalItem.CAT_POSITIVE.equals(c) && !WfApprovalItem.CAT_REJECT.equals(c)
                    && !WfApprovalItem.CAT_TERMINATE.equals(c)) {
                throw new com.fssc.common.exception.BizException(ErrorCode.PARAM_INVALID,
                        "审批项类别只能是 POSITIVE/REJECT/TERMINATE");
            }
        }
        if (item.getEnabled() == null) {
            item.setEnabled(1);
        }
        if (item.getSort() == null) {
            item.setSort(0);
        }
    }

    /** 同租户同节点内 item_code 唯一（库层亦有唯一键，此处给出可读报错） */
    private void assertCodeUnique(WfApprovalItem item, Long excludeId) {
        Long cnt = wfApprovalItemMapper.selectCount(new LambdaQueryWrapper<WfApprovalItem>()
                .eq(WfApprovalItem::getModelKey, item.getModelKey())
                .eq(WfApprovalItem::getNodeKey, item.getNodeKey())
                .eq(WfApprovalItem::getItemCode, item.getItemCode())
                .ne(excludeId != null, WfApprovalItem::getId, excludeId));
        if (cnt != null && cnt > 0) {
            throw new com.fssc.common.exception.BizException(ErrorCode.PARAM_INVALID,
                    "该节点下审批项编码已存在：" + item.getItemCode());
        }
    }
}