// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArMailNotifyLog;
import com.fssc.arflow.entity.ArMailPackage;
import com.fssc.arflow.entity.ArMailRecipient;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.mapper.ArMailNotifyLogMapper;
import com.fssc.arflow.mapper.ArMailPackageMapper;
import com.fssc.arflow.mapper.ArMailRecipientMapper;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.notify.NotifyService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final ArMailRecipientMapper recipientMapper;
    private final ArMailNotifyLogMapper notifyLogMapper;
    private final NotifyService notifyService;

    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 创建邮包：invoiceNos 逗号分隔，须已开票并打印。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMailPackage create(ArMailPackage p, String invoiceNos) {
        if (invoiceNos == null || invoiceNos.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包必须包含至少一张已开发票");
        }
        List<String> nos = Arrays.stream(invoiceNos.split("[,，\\s]+"))
                .filter(s -> !s.isBlank()).map(String::trim).distinct().toList();
        if (nos.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包必须包含至少一张已开发票");
        }
        List<ArPreInvoice> invoices = new ArrayList<>();
        for (String no : nos) {
            ArPreInvoice inv = preInvoiceMapper.selectOne(new LambdaQueryWrapper<ArPreInvoice>()
                    .eq(ArPreInvoice::getPreInvoiceNo, no).last("LIMIT 1"));
            if (inv == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预制发票不存在：" + no);
            }
            if (!ArPreInvoice.ST_INVOICED.equals(inv.getStatus()) || inv.getPrintedFlag() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "发票 " + no + " 未开票或未打印，不可邮寄");
            }
            invoices.add(inv);
        }
        p.setId(null);
        p.setPackageNo("FSC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT));
        p.setInvoiceNosJson(nos.toString());
        // 客户与公司随发票带出，避免邮包落在列表里成为无客户归属的孤儿
        ArPreInvoice first = invoices.get(0);
        if (p.getCompanyId() == null) {
            p.setCompanyId(first.getCompanyId());
        }
        if (p.getCustomerCode() == null || p.getCustomerCode().isBlank()) {
            p.setCustomerCode(first.getCustomerCode());
        }
        if (p.getCustomerName() == null || p.getCustomerName().isBlank()) {
            p.setCustomerName(first.getCustomerName());
        }
        // 收件人地址簿：姓名/电话/邮箱/地址一次带出；手工填写的内容优先
        if (p.getRecipientId() != null) {
            ArMailRecipient r = recipientMapper.selectById(p.getRecipientId());
            if (r == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "收件人不存在 id=" + p.getRecipientId());
            }
            if (isBlank(p.getReceiver())) {
                p.setReceiver(r.getRecipientName());
            }
            if (isBlank(p.getReceiverPhone())) {
                p.setReceiverPhone(r.getPhone());
            }
            if (isBlank(p.getReceiverEmail())) {
                p.setReceiverEmail(r.getEmail());
            }
            if (isBlank(p.getAddress())) {
                p.setAddress(r.getAddress());
            }
            if (isBlank(p.getCustomerCode())) {
                p.setCustomerCode(r.getCustomerCode());
            }
            if (isBlank(p.getCustomerName())) {
                p.setCustomerName(r.getCustomerName());
            }
            if (p.getCompanyId() == null) {
                p.setCompanyId(r.getCompanyId());
            }
        }
        if (isBlank(p.getReceiver())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包必须指定收件人");
        }
        if (isBlank(p.getAddress())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮包必须填写收件地址");
        }
        p.setMailNotified(0);
        p.setStatus(ArMailPackage.ST_CREATED);
        mapper.insert(p);
        for (ArPreInvoice inv : invoices) {
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
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("courierNo", auditOldCourierNo);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", p.getStatus());
        auditAfter.put("courierNo", p.getCourierNo());
        auditTrailService.recordUpdate("AR_MAIL_PACKAGE", p.getId(), p.getPackageNo(), "ar_mail_package",
                String.valueOf(p.getId()), auditBefore, auditAfter, "邮包寄送");
        return p;
    }

    /** 邮件通知客户收件人：按地址簿邮箱真发（未配置 SMTP 时模拟记录），每次尝试写发送记录。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BizException.class)
    public ArMailPackage notify(Long id) {
        ArMailPackage p = require(id);
        requireStatus(p, ArMailPackage.ST_SENT, "邮件通知");
        String email = p.getReceiverEmail();
        if (isBlank(email) && p.getRecipientId() != null) {
            ArMailRecipient r = recipientMapper.selectById(p.getRecipientId());
            if (r != null) {
                email = r.getEmail();
                p.setReceiverEmail(email);
                if (isBlank(p.getReceiver())) {
                    p.setReceiver(r.getRecipientName());
                }
            }
        }
        if (isBlank(email)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收件人未维护邮箱，请先在「收件人管理」补全邮箱");
        }
        String subject = "发票邮包寄送通知 " + p.getPackageNo();
        StringBuilder text = new StringBuilder()
                .append("尊敬的 ").append(p.getReceiver()).append("：\n")
                .append("以下发票邮包已寄出，请查收。\n")
                .append("邮包号：").append(p.getPackageNo()).append('\n')
                .append("发票：").append(p.getInvoiceNosJson()).append('\n');
        if (!isBlank(p.getCourierCompany()) || !isBlank(p.getCourierNo())) {
            text.append("快递：").append(p.getCourierCompany() == null ? "" : p.getCourierCompany())
                    .append(' ').append(p.getCourierNo() == null ? "" : p.getCourierNo()).append('\n');
        }
        text.append("收件地址：").append(p.getAddress() == null ? "" : p.getAddress()).append('\n')
                .append("联系电话：").append(p.getReceiverPhone() == null ? "" : p.getReceiverPhone());
        NotifyService.MailSendResult result = notifyService.sendMail(email.trim(), subject, text.toString());
        ArMailNotifyLog logRow = new ArMailNotifyLog();
        logRow.setPackageId(p.getId());
        logRow.setPackageNo(p.getPackageNo());
        logRow.setRecipientId(p.getRecipientId());
        logRow.setReceiver(p.getReceiver());
        logRow.setEmail(email.trim());
        logRow.setSubject(subject);
        logRow.setContent(text.toString());
        logRow.setSendStatus(result.status());
        logRow.setErrorMsg(result.message());
        logRow.setSendTime(LocalDateTime.now());
        notifyLogMapper.insert(logRow);
        if ("FAILED".equals(result.status())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮件发送失败：" + result.message());
        }
        p.setMailNotified(1);
        p.setNotifyTime(LocalDateTime.now());
        mapper.updateById(p);
        return p;
    }

    /** 邮件发送记录（可选按邮包号/状态过滤），供「邮件管理」查看与重发追溯。 */
    public List<ArMailNotifyLog> notifyLogs(String packageNo, String status) {
        LambdaQueryWrapper<ArMailNotifyLog> q = new LambdaQueryWrapper<>();
        if (!isBlank(packageNo)) {
            q.like(ArMailNotifyLog::getPackageNo, packageNo.trim());
        }
        if (!isBlank(status)) {
            q.eq(ArMailNotifyLog::getSendStatus, status.trim());
        }
        return notifyLogMapper.selectList(q.orderByDesc(ArMailNotifyLog::getId).last("LIMIT 200"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
                String.valueOf(p.getId()), Collections.singletonMap("status", auditOldStatus),
                Collections.singletonMap("status", p.getStatus()), "邮包确认签收");
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
                String.valueOf(p.getId()), Collections.singletonMap("status", auditOldStatus),
                Collections.singletonMap("status", p.getStatus()), "邮包归档");
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
