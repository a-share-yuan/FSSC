package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.TaxProvisionRule;
import com.fssc.glex.mapper.TaxProvisionRuleMapper;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 税金计提规则服务。
 *
 * <p>取数口只有一个：{@link #effectiveRules()}。调用方（计提 / 申报出证 / 缴税付款凭证）
 * 一律经它取规则，<b>不得</b>再各自写一份默认值——三处各写一份默认值正是 的成因
 * （注释说「缴款走付款通道出 2221」，付款凭证却写死 2202）。
 *
 * <p>空表兜底：升级到本版的租户表是空的，此时返回<b>内置三条</b>（增值税 13% / 附加 12% / 所得税 25%），
 * 与改造前 {@code GlTaxProvisionService} 的行为逐字一致，保证「不发数据升级也照旧能计提」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxProvisionRuleService {

    private final TaxProvisionRuleMapper ruleMapper;
    private final MdSubjectMapper mdSubjectMapper;

    /** 内置兜底规则：与改造前写死在 GlTaxProvisionService 的三条常量同值同科目 */
    private static final List<TaxProvisionRule> BUILTIN = Collections.unmodifiableList(Arrays.asList(
            builtin("VAT", "增值税-月度", new BigDecimal("13.00"), "6403", "22210101"),
            builtin("SURCHARGE", "附加税-月度", new BigDecimal("12.00"), "6403", "2221"),
            builtin("INCOME", "企业所得税-季度", new BigDecimal("25.00"), "6801", "22210201")
    ));

    private static TaxProvisionRule builtin(String taxType, String name, BigDecimal rate,
                                            String debit, String credit) {
        TaxProvisionRule r = new TaxProvisionRule();
        r.setTaxType(taxType);
        r.setRuleName(name);
        r.setBaseSource("MANUAL");
        r.setTaxRate(rate);
        r.setDebitSubjectCode(debit);
        r.setCreditSubjectCode(credit);
        r.setDeclareCreditSubjectCode("2221");
        r.setPayDebitSubjectCode("2221");
        r.setPeriodType("MONTHLY");
        r.setDeclareDeadlineDay(15);
        r.setPayDeadlineDay(15);
        r.setSort(0);
        r.setStatus("NORMAL");
        return r;
    }

    /** 列表（配置页用，含停用行） */
    public List<TaxProvisionRule> list() {
        return ruleMapper.selectList(new LambdaQueryWrapper<TaxProvisionRule>()
                .orderByAsc(TaxProvisionRule::getSort)
                .orderByAsc(TaxProvisionRule::getId));
    }

    /**
     * 生效规则：NORMAL 行按 sort 排序；一条都没有时返回内置三条。
     * 只读、不写库——空表租户不做自动灌种子（灌种子属数据迁移，走 SQL 脚本由运维决定）。
     */
    public List<TaxProvisionRule> effectiveRules() {
        List<TaxProvisionRule> rows = ruleMapper.selectList(new LambdaQueryWrapper<TaxProvisionRule>()
                .eq(TaxProvisionRule::getStatus, "NORMAL")
                .orderByAsc(TaxProvisionRule::getSort)
                .orderByAsc(TaxProvisionRule::getId));
        if (rows == null || rows.isEmpty()) {
            log.debug("税金计提规则表为空（租户 {}），按内置三条兜底", TenantContext.getTenantId());
            return BUILTIN;
        }
        return rows;
    }

    /** 按税种取生效规则（无该税种返回 null，调用方据此跳过而不是编一条） */
    public TaxProvisionRule effectiveRuleOf(String taxType) {
        if (!StringUtils.hasText(taxType)) {
            return null;
        }
        return effectiveRules().stream()
                .filter(r -> taxType.equalsIgnoreCase(r.getTaxType()))
                .findFirst().orElse(null);
    }

    public TaxProvisionRule save(TaxProvisionRule rule) {
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则不能为空");
        }
        if (!StringUtils.hasText(rule.getTaxType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税种必填");
        }
        if (rule.getTaxRate() == null || rule.getTaxRate().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税率必须大于 0");
        }
        String src = Optional.ofNullable(rule.getBaseSource()).orElse("MANUAL").toUpperCase();
        if (!Arrays.asList("MANUAL", "DECLARATION", "INVOICE").contains(src)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "计税基数来源非法: " + rule.getBaseSource());
        }
        rule.setBaseSource(src);
        if ("MANUAL".equals(src) && rule.getBaseAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手工基数来源必须填写缺省计税基数");
        }
        if (rule.getStatus() == null) {
            rule.setStatus("NORMAL");
        }
        if (rule.getPeriodType() == null) {
            rule.setPeriodType("MONTHLY");
        }
        // 同租户同税种只允许一条生效规则：税种是计提的唯一身份，重复会让同一期间出两条同税种计提
        TaxProvisionRule same = ruleMapper.selectOne(new LambdaQueryWrapper<TaxProvisionRule>()
                .eq(TaxProvisionRule::getTaxType, rule.getTaxType().toUpperCase())
                .last("limit 1"));
        if (same != null && !same.getId().equals(rule.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税种 " + rule.getTaxType() + " 已有计提规则（ID " + same.getId() + "），请直接编辑该条");
        }
        rule.setTaxType(rule.getTaxType().toUpperCase());
        if (rule.getId() == null) {
            rule.setId(null);
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则 ID 不能为空");
        }
        if (ruleMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则不存在: " + id);
        }
        ruleMapper.deleteById(id);
    }

    /**
     * 科目码 → 科目 ID（按科目码解析，跨公司共用）。
     * 解析不到返回 null，由调用方决定是抛错还是降级——本方法不替调用方编科目。
     */
    public Long subjectIdOf(String subjectCode) {
        if (!StringUtils.hasText(subjectCode)) {
            return null;
        }
        MdSubject s = mdSubjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, subjectCode)
                .orderByAsc(MdSubject::getId)
                .last("limit 1"));
        return s == null ? null : s.getId();
    }

    /** 批量解析科目码 → 名称（凭证摘要用），解析不到的键不出现 */
    public List<String[]> subjectPairs(List<String> codes) {
        List<String[]> out = new ArrayList<>();
        if (codes == null) {
            return out;
        }
        for (String code : codes) {
            Long id = subjectIdOf(code);
            if (id == null) {
                continue;
            }
            MdSubject s = mdSubjectMapper.selectById(id);
            out.add(new String[]{code, s == null ? code : s.getSubjectName()});
        }
        return out;
    }
}
