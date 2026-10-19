package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.glex.entity.TaxInputCertify;
import com.fssc.glex.mapper.TaxInputCertifyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 进项认证服务（P1-3 税务一体化）。
 * <p>
 * 管理进项发票认证全生命周期：
 * 待认证(PENDING) → 已认证(CERTIFIED) → 已抵扣(DEDUCTED) → [可转出(TRANSFERRED)]
 * <p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCertifyService {

    private final TaxInputCertifyMapper certifyMapper;

    /**
     * 新增进项认证台账记录（发票录入时自动建台账）。
     */
    public Long create(TaxInputCertify record) {
        if (!StringUtils.hasText(record.getInvoiceNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票号码必填");
        }
        record.setCertifyStatus("PENDING");
        if (record.getRiskFlag() == null) {
            record.setRiskFlag(0);
        }
        // WS-6 税域收口：非本租户全部档时，强制公司归属为登录人公司（未带则回填，已带则校验）
        Long scopedCompany = DataScopeKit.scopedCompanyId();
        if (scopedCompany != null) {
            if (record.getCompanyId() == null) {
                record.setCompanyId(scopedCompany);
            } else {
                DataScopeKit.requireCompanyInScope(record.getCompanyId());
            }
        }
        certifyMapper.insert(record);
        log.info("新增进项认证台账: invoiceNo={}, taxAmount={}", record.getInvoiceNo(), record.getTaxAmount());
        return record.getId();
    }

    /**
     * 认证发票：PENDING → CERTIFIED。
     */
    public void certify(Long id, String period) {
        TaxInputCertify row = requireRow(id);
        if (!"PENDING".equals(row.getCertifyStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + row.getInvoiceNo() + " 当前状态 " + row.getCertifyStatus() + " 不可认证");
        }
        TaxInputCertify upd = new TaxInputCertify();
        upd.setId(id);
        upd.setCertifyStatus("CERTIFIED");
        upd.setCertifyDate(LocalDate.now());
        upd.setCertifyPeriod(period != null ? period : currentPeriod());
        certifyMapper.updateById(upd);
        log.info("发票 {} 认证成功, 属期={}", row.getInvoiceNo(), period);
    }

    /**
     * 批量认证。
     */
    public int batchCertify(List<Long> ids, String period) {
        int count = 0;
        for (Long id : ids) {
            try {
                certify(id, period);
                count++;
            } catch (Exception ex) {
                log.warn("批量认证: id={} 失败: {}", id, ex.getMessage());
            }
        }
        log.info("批量认证完成: 共{}张, 成功{}张", ids.size(), count);
        return count;
    }

    /**
     * 抵扣认证：CERTIFIED → DEDUCTED。
     */
    public void deduct(Long id, String deductPeriod) {
        TaxInputCertify row = requireRow(id);
        if (!"CERTIFIED".equals(row.getCertifyStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + row.getInvoiceNo() + " 未认证，不可抵扣");
        }
        TaxInputCertify upd = new TaxInputCertify();
        upd.setId(id);
        upd.setCertifyStatus("DEDUCTED");
        upd.setDeductPeriod(deductPeriod != null ? deductPeriod : currentPeriod());
        certifyMapper.updateById(upd);
        log.info("发票 {} 抵扣成功, 属期={}", row.getInvoiceNo(), deductPeriod);
    }

    /**
     * 进项转出：DEDUCTED → TRANSFERRED。
     */
    public void transfer(Long id, String reason) {
        TaxInputCertify row = requireRow(id);
        if (!"DEDUCTED".equals(row.getCertifyStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + row.getInvoiceNo() + " 未抵扣，不可转出");
        }
        TaxInputCertify upd = new TaxInputCertify();
        upd.setId(id);
        upd.setCertifyStatus("TRANSFERRED");
        upd.setTransferReason(reason);
        certifyMapper.updateById(upd);
        log.info("发票 {} 进项转出: {}", row.getInvoiceNo(), reason);
    }

    /**
     * 撤销认证：CERTIFIED → PENDING。
     */
    public void revoke(Long id) {
        TaxInputCertify row = requireRow(id);
        if (!"CERTIFIED".equals(row.getCertifyStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + row.getInvoiceNo() + " 当前状态 " + row.getCertifyStatus() + " 不可撤销");
        }
        TaxInputCertify upd = new TaxInputCertify();
        upd.setId(id);
        upd.setCertifyStatus("PENDING");
        upd.setCertifyDate(null);
        upd.setCertifyPeriod(null);
        certifyMapper.updateById(upd);
        log.info("发票 {} 撤销认证", row.getInvoiceNo());
    }

    /**
     * 查询可抵扣税额汇总（按属期）。
     */
    public BigDecimal sumDeductible(String period) {
        LambdaQueryWrapper<TaxInputCertify> qw = new LambdaQueryWrapper<TaxInputCertify>()
                .eq(TaxInputCertify::getCertifyStatus, "CERTIFIED")
                .eq(TaxInputCertify::getCertifyPeriod, period);
        // WS-6 税域收口：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxInputCertify::getCompanyId, null, null);
        List<TaxInputCertify> rows = certifyMapper.selectList(qw);
        return rows.stream()
                .map(r -> r.getTaxAmount() == null ? BigDecimal.ZERO : r.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 分页查询。
     */
    public PageResult<TaxInputCertify> page(long pn, long ps, String keyword, String status) {
        LambdaQueryWrapper<TaxInputCertify> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(TaxInputCertify::getInvoiceNo, keyword)
                    .or().like(TaxInputCertify::getSupplierName, keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(TaxInputCertify::getCertifyStatus, status);
        }
        // WS-6 税域收口：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxInputCertify::getCompanyId, null, null);
        qw.orderByDesc(TaxInputCertify::getCreateTime);
        Page<TaxInputCertify> p = certifyMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    private TaxInputCertify requireRow(Long id) {
        TaxInputCertify row = certifyMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "进项认证记录不存在: id=" + id);
        }
        // WS-6 税域收口：单条读后写，按公司归属校验越权
        DataScopeKit.requireCompanyInScope(row.getCompanyId());
        return row;
    }

    private String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
}
