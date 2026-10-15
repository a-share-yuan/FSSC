package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArMailPackage;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.mapper.ArMailPackageMapper;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 发票邮包服务（蓝图 SCC_AR_07）：创建→寄送→邮件通知→签收→归档。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArMailPackageService {

    private final ArMailPackageMapper mapper;
    private final ArPreInvoiceMapper preInvoiceMapper;

    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    /** 创建邮包：invoiceNos 逗号分隔，须已开票并打印。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage create(ArMailPackage p, String invoiceNos) {
        if (invoiceNos == null || invoiceNos.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包必须包含至少一张已开发票");
        }
        p.setId(null);
        p.setPackageNo("FSC" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT));
        p.setInvoiceNosJson(Arrays.stream(invoiceNos.split("[,，\\s]+")).filter(s -> !s.isBlank()).toList().toString());
        p.setMailNotified(0);
        p.setStatus(ArMailPackage.ST_CREATED);
        mapper.insert(p);
        for (String no : invoiceNos.split("[,，\\s]+")) {
            if (no.isBlank()) {
                continue;
            }
            ArPreInvoice inv = preInvoiceMapper.selectOne(new LambdaQueryWrapper<ArPreInvoice>()
                    .eq(ArPreInvoice::getPreInvoiceNo, no.trim()).last("LIMIT 1"));
            if (inv == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预制发票不存在：" + no);
            }
            if (!ArPreInvoice.ST_INVOICED.equals(inv.getStatus()) || inv.getPrintedFlag() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "发票 " + no + " 未开票或未打印，不可邮寄");
            }
            inv.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ") + "邮包:" + p.getPackageNo());
            preInvoiceMapper.updateById(inv);
        }
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage send(Long id, String courierCompany, String courierNo) {
        ArMailPackage p = require(id);
        requireStatus(p, ArMailPackage.ST_CREATED, "寄送");
        if (courierNo == null || courierNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "快递单号必填（打印快递单）");
        }
        String auditOldStatus = p.getStatus();
        String auditOldCourierNo = p.getCourierNo();
        p.setCourierCompany(courierCompany);
        p.setCourierNo(courierNo);
        p.setStatus(ArMailPackage.ST_SENT);
        mapper.updateById(p);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("courierNo", auditOldCourierNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", p.getStatus());
        auditAfter.put("courierNo", p.getCourierNo());
        auditTrailService.recordUpdate("AR_MAIL_PACKAGE", p.getId(), p.getPackageNo(), "ar_mail_package",
                String.valueOf(p.getId()), auditBefore, auditAfter, "邮包寄送");
        return p;
    }

    /** 邮件通知客户主数据邮箱。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage notify(Long id) {
        ArMailPackage p = require(id);
        requireStatus(p, ArMailPackage.ST_SENT, "邮件通知");
        p.setMailNotified(1);
        mapper.updateById(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage sign(Long id) {
        ArMailPackage p = require(id);
        requireStatus(p, ArMailPackage.ST_SENT, "确认签收");
        String auditOldStatus = p.getStatus();
        p.setStatus(ArMailPackage.ST_SIGNED);
        p.setSignTime(LocalDateTime.now());
        mapper.updateById(p);
        auditTrailService.recordUpdate("AR_MAIL_PACKAGE", p.getId(), p.getPackageNo(), "ar_mail_package",
                String.valueOf(p.getId()), java.util.Collections.singletonMap("status", auditOldStatus),
                java.util.Collections.singletonMap("status", p.getStatus()), "邮包确认签收");
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage archive(Long id) {
        ArMailPackage p = require(id);
        requireStatus(p, ArMailPackage.ST_SIGNED, "归档");
        String auditOldStatus = p.getStatus();
        p.setStatus(ArMailPackage.ST_ARCHIVED);
        p.setArchiveTime(LocalDateTime.now());
        mapper.updateById(p);
        auditTrailService.recordUpdate("AR_MAIL_PACKAGE", p.getId(), p.getPackageNo(), "ar_mail_package",
                String.valueOf(p.getId()), java.util.Collections.singletonMap("status", auditOldStatus),
                java.util.Collections.singletonMap("status", p.getStatus()), "邮包归档");
        return p;
    }

    public List<ArMailPackage> list(String status) {
        LambdaQueryWrapper<ArMailPackage> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ArMailPackage::getStatus, status);
        }
        return mapper.selectList(q.orderByDesc(ArMailPackage::getId));
    }

    private ArMailPackage require(Long id) {
        ArMailPackage p = mapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包不存在 id=" + id);
        }
        return p;
    }

    private void requireStatus(ArMailPackage p, String expected, String action) {
        if (!expected.equals(p.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包 " + p.getPackageNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + p.getStatus());
        }
    }
}
