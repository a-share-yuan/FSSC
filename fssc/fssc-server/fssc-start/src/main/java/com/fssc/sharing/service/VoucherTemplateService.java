package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.sharing.entity.VoucherTemplate;
import com.fssc.sharing.mapper.VoucherTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** 凭证模板管理服务：CRUD + 按来源类型和金额区间匹配最合适的模板 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherTemplateService {

    private final VoucherTemplateMapper voucherTemplateMapper;

    /** 创建模板，校验 templateCode 唯一性 */
    public Long create(VoucherTemplate tpl) {
        long dup = voucherTemplateMapper.selectCount(
                new LambdaQueryWrapper<VoucherTemplate>()
                        .eq(VoucherTemplate::getTemplateCode, tpl.getTemplateCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS, "模板编码已存在: " + tpl.getTemplateCode());
        }
        tpl.setId(null);
        if (tpl.getPriority() == null) tpl.setPriority(5);
        if (tpl.getStatus() == null) tpl.setStatus("NORMAL");
        voucherTemplateMapper.insert(tpl);
        return tpl.getId();
    }

    /** 更新模板 */
    public void update(VoucherTemplate tpl) {
        VoucherTemplate exist = voucherTemplateMapper.selectById(tpl.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板不存在: " + tpl.getId());
        }
        // 如果修改了编码，检查唯一性
        if (tpl.getTemplateCode() != null && !tpl.getTemplateCode().equals(exist.getTemplateCode())) {
            long dup = voucherTemplateMapper.selectCount(
                    new LambdaQueryWrapper<VoucherTemplate>()
                            .eq(VoucherTemplate::getTemplateCode, tpl.getTemplateCode()));
            if (dup > 0) {
                throw new BizException(ErrorCode.DATA_ALREADY_EXISTS, "模板编码已存在: " + tpl.getTemplateCode());
            }
        }
        voucherTemplateMapper.updateById(tpl);
    }

    /** 删除模板 */
    public void delete(Long id) {
        VoucherTemplate exist = voucherTemplateMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板不存在: " + id);
        }
        // 逻辑删行仍占用 template_code，会把「删除后重建同码模板」变成 1062；退场时改写为历史态让码位可用
        String code = exist.getTemplateCode();
        if (code != null && !code.contains("#")) {
            exist.setTemplateCode(code + "#" + exist.getId());
            voucherTemplateMapper.updateById(exist);
        }
        voucherTemplateMapper.deleteById(id);
    }

    /** 按ID查询 */
    public VoucherTemplate getById(Long id) {
        return voucherTemplateMapper.selectById(id);
    }

    /** 分页查询 */
    public PageResult<VoucherTemplate> page(PageQuery q, String keyword, String sourceType, String status) {
        LambdaQueryWrapper<VoucherTemplate> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(VoucherTemplate::getTemplateName, keyword)
                    .or().like(VoucherTemplate::getTemplateCode, keyword));
        }
        if (sourceType != null && !sourceType.isBlank()) {
            w.eq(VoucherTemplate::getSourceType, sourceType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(VoucherTemplate::getStatus, status);
        }
        w.orderByAsc(VoucherTemplate::getPriority).orderByDesc(VoucherTemplate::getId);
        Page<VoucherTemplate> p = voucherTemplateMapper.selectPage(
                Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    /** 按来源类型查询启用的模板列表 */
    public List<VoucherTemplate> listBySourceType(String sourceType) {
        return voucherTemplateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplate>()
                        .eq(VoucherTemplate::getSourceType, sourceType)
                        .eq(VoucherTemplate::getStatus, "NORMAL")
                        .orderByAsc(VoucherTemplate::getPriority));
    }

    /**
     * 按来源类型和金额解析匹配模板。
     * 匹配规则：sourceType 精确匹配，金额在 [amountMin, amountMax] 区间内。
     * 返回 priority 最小的匹配模板，无匹配返回 null。
     */
    public VoucherTemplate resolveTemplate(String sourceType, BigDecimal amount) {
        List<VoucherTemplate> templates = voucherTemplateMapper.selectList(
                new LambdaQueryWrapper<VoucherTemplate>()
                        .eq(VoucherTemplate::getSourceType, sourceType)
                        .eq(VoucherTemplate::getStatus, "NORMAL")
                        .orderByAsc(VoucherTemplate::getAmountMin));
        for (VoucherTemplate t : templates) {
            if (amount == null) {
                return t;
            }
            BigDecimal min = t.getAmountMin() == null ? BigDecimal.ZERO : t.getAmountMin();
            BigDecimal max = t.getAmountMax();
            if (amount.compareTo(min) >= 0 && (max == null || amount.compareTo(max) <= 0)) {
                return t;
            }
        }
        return null;
    }
}