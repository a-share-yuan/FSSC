// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.entity.InvInvoiceLine;
import com.fssc.invoice.entity.InvVerifyLog;
import com.fssc.invoice.mapper.InvInvoiceLineMapper;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.invoice.mapper.InvVerifyLogMapper;
import com.fssc.framework.datasource.FsscDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票增强：明细行（inv_invoice_line）+ 验真日志（inv_verify_log）。
 * 明细行用于费用行与发票行勾稽；验真日志为外部接口证据链，审计表只增不改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceEnhanceService {

    private final InvInvoiceLineMapper lineMapper;
    private final InvVerifyLogMapper verifyLogMapper;
    private final InvInvoiceMapper invoiceMapper;

    // 发票明细行

    public List<InvInvoiceLine> linesByInvoice(Long invoiceId) {
        return lineMapper.selectList(new LambdaQueryWrapper<InvInvoiceLine>()
                .eq(InvInvoiceLine::getInvoiceId, invoiceId).orderByAsc(InvInvoiceLine::getLineNo));
    }

    public Long addLine(InvInvoiceLine line) {
        if (line.getInvoiceId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票ID必填");
        }
        line.setId(null);
        if (line.getLineNo() == null) {
            line.setLineNo(1);
        }
        lineMapper.insert(line);
        return line.getId();
    }

    /** 覆盖保存某发票全部明细行 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceLines(Long invoiceId, List<InvInvoiceLine> lines) {
        lineMapper.delete(new LambdaQueryWrapper<InvInvoiceLine>()
                .eq(InvInvoiceLine::getInvoiceId, invoiceId));
        if (lines != null) {
            int no = 1;
            for (InvInvoiceLine l : lines) {
                l.setId(null);
                l.setInvoiceId(invoiceId);
                if (l.getLineNo() == null) {
                    l.setLineNo(no);
                }
                lineMapper.insert(l);
                no++;
            }
        }
    }

    public void deleteLine(Long id) {
        lineMapper.deleteById(id);
    }

    // 发票验真日志（审计表，只增不改）

    public PageResult<InvVerifyLog> verifyLogPage(long pageNum, long pageSize, Long invoiceId) {
        LambdaQueryWrapper<InvVerifyLog> qw = new LambdaQueryWrapper<>();
        if (invoiceId != null) {
            qw.eq(InvVerifyLog::getInvoiceId, invoiceId);
        }
        qw.orderByDesc(InvVerifyLog::getId);
        Page<InvVerifyLog> p = verifyLogMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // inv_verify_log 无 invoice_no 列（审计表按真实列声明，加列属 DDL 待确认）——
        // 列表查询后按 invoiceId 一次批查发票号码，回填到实体 transient 列供「发票号码」列按票稽核
        List<InvVerifyLog> records = p.getRecords();
        if (!records.isEmpty()) {
            Set<Long> invIds = records.stream()
                    .map(InvVerifyLog::getInvoiceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!invIds.isEmpty()) {
                Map<Long, String> noMap = new HashMap<>();
                for (InvInvoice inv : invoiceMapper.selectBatchIds(invIds)) {
                    noMap.put(inv.getId(), inv.getInvoiceNo());
                }
                records.forEach(r -> r.setInvoiceNo(noMap.get(r.getInvoiceId())));
            }
        }
        return PageResult.of(records, p.getTotal(), pageNum, pageSize);
    }

    /**
     * 记录一次验真（税务/第三方/人工渠道；审计表不提供更新/删除）。
     *
     * <p><b>写环节补齐</b>：本表两个"人/时间"列必须有人赋值，否则
     * 验真记录页的「验真时间」「验真人」因此恒空——
     * <ul>
     *   <li>{@code verify_time}：实体上写的是 {@code @TableField(fill = FieldFill.INSERT)}，
     *       而 {@code FsscMetaObjectHandler} 只填充 createBy/updateBy/createTime/updateTime/delFlag/tenantId
     *       六个名字，不认识 verifyTime ⇒ 填充列被 INSERT 无条件写成 NULL（连表默认值都跳过）。
     *       写侧显式取当前时间（注解同步摘除，见实体注释）。</li>
     *   <li>{@code verify_user_id}：HTTP 入口（{@code /verify/run}）本就传登录人，但服务层没有兜底，
     *       任何新调用方漏传即静默无痕；现调用方未传时回落到 {@link UserContext} 当前登录人。
     *       <b>系统作业（定时验真）无登录人 ⇒ 保持 NULL</b>，不拿 0/"system" 冒充某个具体经办人，
     *       该行的归属由 {@code channel=SYSTEM} 表达。</li>
     * </ul>
     *
     * @param result PASS通过/FAIL失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordVerify(Long invoiceId, String channel, String reqParam, String respContent,
                             String result, String msg, Long verifyUserId) {
        InvVerifyLog log = new InvVerifyLog();
        log.setInvoiceId(invoiceId);
        log.setChannel(channel == null ? "MANUAL" : channel);
        log.setReqParam(reqParam);
        log.setRespContent(respContent);
        log.setResult(result == null ? "PASS" : result);
        log.setMsg(msg);
        log.setVerifyUserId(verifyUserId != null ? verifyUserId : currentUserId());
        log.setVerifyTime(LocalDateTime.now());
        verifyLogMapper.insert(log);
    }

    /** 当前登录人ID；无登录上下文（定时作业、匿名回调）返回 null——审计表按"无人即不留名"如实记 */
    private static Long currentUserId() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }

    /**
     * 验真结论写入数据库（<b>唯一</b>能把 verify_status 写成 SUCCESS/FAILED 的出口）。
     *
     * <p>调用前提：本次结论来自<b>真实通道</b>（外部验真接口/人工对照税局查验），且拿得到请求与返回报文。
     * 报文随结论一起进 {@code inv_verify_log}，事后能被复核；没有报文的模拟请求请走
     * {@link #verifyNow(Long, String, String, Long)}，它不给「验真通过」。
     *
     * @param pass 通道给出的判定
     * @param msg 说明（空值时按判定给默认文案）
     * @param channel 验真渠道，取 {@code inv_verify_log.channel} 既有名称表；为空按 {@code SYSTEM}（系统作业）
     * @param reqParam 请求报文（为空回落 invoiceNo）
     * @param respContent 返回报文（真实通道的证据本体）
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyVerifyConclusion(Long invoiceId, boolean pass, String msg, String channel,
                                      String reqParam, String respContent, Long verifyUserId) {
        InvInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票不存在: " + invoiceId);
        }
        InvInvoice update = new InvInvoice();
        update.setId(invoiceId);
        update.setVerifyStatus(pass ? "SUCCESS" : "FAILED");
        update.setVerifyTime(LocalDateTime.now());
        update.setVerifyMsg(msg == null || msg.isBlank() ? (pass ? "验真通过" : "验真失败") : msg);
        invoiceMapper.updateById(update);
        recordVerify(invoiceId, channel == null || channel.isBlank() ? "SYSTEM" : channel,
                reqParam == null || reqParam.isBlank() ? "invoiceNo=" + invoice.getInvoiceNo() : reqParam,
                respContent, pass ? "PASS" : "FAIL", msg, verifyUserId);
    }

    /**
     * 实时/手动验真（需求 7.1/7.2）：发票池按钮与批量入口共用的「发起」出口。
     *
     * <p>【】模拟通道不再产出「验真通过」。本方法拿不到任何外部报文——调用方是浏览器
     * （{@code POST /verify/now}、{@code /verify/batch}，前端把 result 硬编码成 PASS、按钮文案写着
     * 「模拟税务通道」）或旧定时作业的回退分支。旧实现把这些请求一律按 {@code result≠FAIL 即通过}
     * 写成 {@code verify_status=SUCCESS}，并顺手在 inv_verify_log 落一行 {@code resp_content=result=PASS}
     * 的<b>无中生有的报文</b>；于是「补录一张任意发票 + 点一次按钮」就能凑出验真事实，
     * 而这条状态又被下游当准入依据（发票覆盖、合规审核、归档检查）。
     *
     * <p>现规则（取值全部用现有枚举，见 {@code constants/enums.js} 的 VERIFY_STATUS 与 inv_invoice 列注释）：
     * <ul>
     *   <li>自报失败（result=FAIL/FAILED）⇒ 照旧落 {@code FAILED}，从严方向不受影响；</li>
     *   <li>其余（含 PASS/空）⇒ 只推进到 {@code PENDING}（验真中），verify_time 不动，
     *       并写明未判定原因；不写 SUCCESS，也不落 inv_verify_log——该表 result 列名称表只有
     *       PASS/FAIL，没有「未判定」一格，写进去等于再造一条假证据，发起动作的记录由
     *       {@code sys_oper_log}（@OperLog）与 verify_msg 承担；</li>
     *   <li>要拿到「验真通过」，必须由真实通道给结论：见 {@link #applyVerifyConclusion}，
     *       定时作业已改为只在「外部接口配置」存在 ENABLED 的 INVOICE_VERIFY 通道时逐张调用并落报文。</li>
     * </ul>
     *
     * <p>影响面核对：{@code verify_status=PENDING} 在系统内按「未验真」处理
     * （发票池可选待验集合、详情标签、异常告警作业只看 FAILED），不会把单据卡死。
     */
    @Transactional(rollbackFor = Exception.class)
    public void verifyNow(Long invoiceId, String result, String msg, Long verifyUserId) {
        InvInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票不存在: " + invoiceId);
        }
        boolean selfReportedFail = "FAIL".equalsIgnoreCase(result) || "FAILED".equalsIgnoreCase(result);
        InvInvoice update = new InvInvoice();
        update.setId(invoiceId);
        if (selfReportedFail) {
            update.setVerifyStatus("FAILED");
            update.setVerifyTime(LocalDateTime.now());
            update.setVerifyMsg(msg == null || msg.isBlank() ? "验真失败（人工申报）" : msg);
        } else {
            update.setVerifyStatus("PENDING");
            update.setVerifyMsg("已发起验真：本次请求无外部报文，模拟通道不判定「验真通过」（等待外部验真接口结论，"
                    + "请在「外部接口配置」启用 category=发票验真 的通道）");
        }
        invoiceMapper.updateById(update);
        log.info("发票 {} 验真请求（操作人 {}）无外部报文：verify_status={}，不写 SUCCESS",
                invoiceId, verifyUserId, selfReportedFail ? "FAILED" : "PENDING");
    }

    /**
     * 批量验真（发票列表多选 → 一键验真）：逐张走 {@link #verifyNow} 的发起规则并返回受理张数。
     *
     * <p>旧实现在这里硬编码 {@code result="PASS"}（注释自陈「当前模拟 PASS」），
     * 每张被点选的发票都直接变成 SUCCESS；真实通道接入前不产出通过结论。
     */
    @Transactional(rollbackFor = Exception.class)
    public int verifyBatch(List<Long> invoiceIds, Long verifyUserId) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择待验真发票");
        }
        int count = 0;
        for (Long id : invoiceIds) {
            try {
                verifyNow(id, "PENDING", "批量发起验真（未获外部报文，不判定通过）", verifyUserId);
                count++;
            } catch (Exception e) {
                log.warn("批量验真受理失败 invoiceId={}", id, e);
            }
        }
        log.info("发票批量验真受理完成：{} / {}（模拟通道不产生验真通过结论）", count, invoiceIds.size());
        return count;
    }
}
