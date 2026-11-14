package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysNumberRule;
import com.fssc.system.entity.SysNumberSeq;
import com.fssc.system.mapper.SysNumberRuleMapper;
import com.fssc.system.mapper.SysNumberSeqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 单据编号规则（sys_number_rule + sys_number_seq）。
 * <p>
 * 取号并发安全：先 UPDATE sys_number_seq SET current_val = current_val + step
 * 行锁保证唯一，影响行数为 0 时 INSERT 初始行（uk_tenant_rule_cycle 防并发重复，
 * 冲突则重试一次）。编号 = prefix + 日期 + 补零流水号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NumberRuleService {

    private final SysNumberRuleMapper ruleMapper;
    private final SysNumberSeqMapper seqMapper;

    // 规则 CRUD

    public PageResult<SysNumberRule> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysNumberRule> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysNumberRule::getRuleCode, keyword)
                    .or().like(SysNumberRule::getRuleName, keyword));
        }
        qw.orderByAsc(SysNumberRule::getId);
        Page<SysNumberRule> p = ruleMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(SysNumberRule rule) {
        long dup = ruleMapper.selectCount(new LambdaQueryWrapper<SysNumberRule>()
                .eq(SysNumberRule::getRuleCode, rule.getRuleCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则编码已存在: " + rule.getRuleCode());
        }
        rule.setId(null);
        if (rule.getSeqLength() == null) {
            rule.setSeqLength(4);
        }
        if (rule.getResetCycle() == null) {
            rule.setResetCycle("DAY");
        }
        if (rule.getStep() == null) {
            rule.setStep(1);
        }
        if (rule.getStatus() == null) {
            rule.setStatus("NORMAL");
        }
        ruleMapper.insert(rule);
        return rule.getId();
    }

    public void update(SysNumberRule rule) {
        if (ruleMapper.selectById(rule.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则不存在: " + rule.getId());
        }
        ruleMapper.updateById(rule);
    }

    public void enable(Long id, boolean enable) {
        SysNumberRule r = new SysNumberRule();
        r.setId(id);
        r.setStatus(enable ? "NORMAL" : "DISABLED");
        ruleMapper.updateById(r);
    }

    public void delete(Long id) {
        ruleMapper.hardDelete(id);
    }

    // 取号

    /**
     * 按规则编码取下一个单据号（并发安全，行锁 + 唯一键）。
     * 业务侧统一走本方法，禁止手写前缀拼号。
     * 分库改造：sys_number_rule/sys_number_seq 在 master 库；REQUIRES_NEW 挂起外层
     * 可能为 image 库 事务、绑定 master 连接独立取号——外层回滚时号段已消费
     * 出现跳号，属取号服务常规可接受语义。
     */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String next(String ruleCode) {
        SysNumberRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<SysNumberRule>()
                .eq(SysNumberRule::getRuleCode, ruleCode));
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编号规则不存在: " + ruleCode);
        }
        if ("DISABLED".equals(rule.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编号规则已停用: " + ruleCode);
        }
        String cycleKey = cycleKey(rule);
        long seq = incrementSeq(rule, cycleKey);
        return buildNumber(rule, cycleKey, seq);
    }

    /** 计算周期键：DAY→yyyyMMdd / MONTH→yyyyMM / YEAR→yyyy / NONE→"" */
    private String cycleKey(SysNumberRule rule) {
        String cycle = rule.getResetCycle() == null ? "DAY" : rule.getResetCycle();
        LocalDate now = LocalDate.now();
        switch (cycle) {
            case "MONTH":
                return now.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEAR":
                return now.format(DateTimeFormatter.ofPattern("yyyy"));
            case "NONE":
                return "";
            default:
                return now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
    }

    /** 原子自增；无行则插入初始行（并发冲突重试一次） */
    private long incrementSeq(SysNumberRule rule, String cycleKey) {
        Long tenantId = TenantContext.getTenantId();
        int step = rule.getStep() == null ? 1 : rule.getStep();
        int updated = seqMapper.update(null,
                new LambdaUpdateWrapper<SysNumberSeq>()
                        .eq(SysNumberSeq::getTenantId, tenantId)
                        .eq(SysNumberSeq::getRuleCode, rule.getRuleCode())
                        .eq(SysNumberSeq::getCycleKey, cycleKey)
                        .setSql("current_val = current_val + " + step));
        if (updated == 0) {
            try {
                SysNumberSeq init = new SysNumberSeq();
                init.setTenantId(tenantId);
                init.setRuleCode(rule.getRuleCode());
                init.setCycleKey(cycleKey);
                init.setCurrentVal((long) step);
                seqMapper.insert(init);
                return step;
            } catch (Exception e) {
                // 并发插入冲突（uk_tenant_rule_cycle），重试一次自增
                int retry = seqMapper.update(null,
                        new LambdaUpdateWrapper<SysNumberSeq>()
                                .eq(SysNumberSeq::getTenantId, tenantId)
                                .eq(SysNumberSeq::getRuleCode, rule.getRuleCode())
                                .eq(SysNumberSeq::getCycleKey, cycleKey)
                                .setSql("current_val = current_val + " + step));
                if (retry == 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "编号取号失败: " + rule.getRuleCode());
                }
            }
        }
        SysNumberSeq cur = seqMapper.selectOne(new LambdaQueryWrapper<SysNumberSeq>()
                .eq(SysNumberSeq::getTenantId, tenantId)
                .eq(SysNumberSeq::getRuleCode, rule.getRuleCode())
                .eq(SysNumberSeq::getCycleKey, cycleKey));
        return cur == null ? step : cur.getCurrentVal();
    }

    /** 编号 = prefix + 日期(可选) + 流水号补零 */
    private String buildNumber(SysNumberRule rule, String cycleKey, long seq) {
        StringBuilder sb = new StringBuilder();
        if (rule.getPrefix() != null) {
            sb.append(rule.getPrefix());
        }
        String df = rule.getDateFormat();
        if (df != null && !df.isBlank()) {
            String datePart = cycleKey;
            // DAY 周期且日期格式非 yyyyMMdd 时按格式重算
            if ("DAY".equals(rule.getResetCycle()) && !"yyyyMMdd".equals(df)) {
                datePart = LocalDate.now().format(DateTimeFormatter.ofPattern(df, Locale.ROOT));
            }
            sb.append(datePart);
        }
        int len = rule.getSeqLength() == null ? 4 : rule.getSeqLength();
        sb.append(String.format(Locale.ROOT, "%0" + len + "d", seq));
        return sb.toString();
    }
}
