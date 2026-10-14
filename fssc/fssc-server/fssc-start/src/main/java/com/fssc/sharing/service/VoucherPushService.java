// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.sharing.channel.VoucherPushChannel;
import com.fssc.sharing.channel.VoucherPushChannelRouter;
import com.fssc.sharing.channel.VoucherPushResult;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.entity.OpVoucherPushLog;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.mapper.OpVoucherPushLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 凭证推送服务：将已记账凭证推送至 NC / IFS 外部财务系统。
 *
 * <p>P1-8 整改说明（消除 Mock 残留）：</p>
 * <ul>
 *   <li>传输方式改为可插拔通道 SPI（{@link VoucherPushChannelRouter}，
 *       sys_config {@code voucher.push.channel} 控制 local/http），业务不感知具体实现；</li>
 *   <li>删除原实现的 {@code Thread.sleep(150)} 人为网络延迟；</li>
 *   <li>推送记录由进程内 Map 改为落库 {@code op_voucher_push_log}，
 *       支持审计、多实例一致、重启不丢。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherPushService {

    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final OpVoucherPushLogMapper pushLogMapper;
    private final VoucherPushChannelRouter channelRouter;

    /**
     * 推送单张凭证至外部系统。
     * 校验凭证状态为 POSTED → 选择通道 → 推送 → 落库推送日志。
     */
    public Map<String, Object> pushToExternal(Long voucherId) {
        OpVoucher voucher = opVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        }
        if (!"POSTED".equals(voucher.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证未记账，无法推送至外部系统");
        }

        // 推送目标：按来源类型选择（IFS 来源走 IFS，其余走 NC）
        String target = "NC_SYSTEM";
        if (voucher.getSourceType() != null && voucher.getSourceType().contains("IFS")) {
            target = "IFS_SYSTEM";
        }

        // P0-2 幂等：同一凭证 + 同一目标已成功推送过则回放，不再外呼（防重复点击/超时重发致外部重复建账）。
        // 失败记录不在此拦截，仍可重推。
        OpVoucherPushLog prior = pushLogMapper.selectOne(new LambdaQueryWrapper<OpVoucherPushLog>()
                .eq(OpVoucherPushLog::getVoucherId, voucherId)
                .eq(OpVoucherPushLog::getTarget, target)
                .eq(OpVoucherPushLog::getStatus, "SUCCESS")
                .orderByDesc(OpVoucherPushLog::getId)
                .last("limit 1"));
        if (prior != null) {
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("pushed", true);
            dup.put("duplicate", true);
            dup.put("target", target);
            dup.put("channel", prior.getChannel());
            dup.put("pushTime", prior.getPushTime() == null ? null : prior.getPushTime().toString());
            dup.put("voucherNo", prior.getVoucherNo());
            dup.put("status", "SUCCESS");
            dup.put("externalNo", prior.getExternalNo());
            log.info("凭证推送幂等命中（跳过重复外呼）: voucherId={} target={} externalNo={}",
                    voucherId, target, prior.getExternalNo());
            return dup;
        }

        Map<String, Object> payload = buildPayload(voucher);
        String requestBody = String.valueOf(payload);

        VoucherPushChannel channel = channelRouter.current();
        VoucherPushResult r = channel.push(target, payload);

        // 落库推送日志（独立写入，不在外部调用期间占用事务连接）
        OpVoucherPushLog logRow = new OpVoucherPushLog();
        logRow.setVoucherId(voucherId);
        logRow.setVoucherNo(voucher.getVoucherNo());
        logRow.setTarget(target);
        logRow.setChannel(r.getChannel());
        logRow.setStatus(r.isOk() ? "SUCCESS" : "FAILED");
        logRow.setExternalNo(r.getExternalNo());
        logRow.setPushTime(r.getPushTime());
        logRow.setErrorMsg(r.isOk() ? null : r.getMessage());
        logRow.setRequestBody(truncate(requestBody));
        logRow.setResponseBody(truncate(r.getResponseBody()));
        pushLogMapper.insert(logRow);

        Map<String, Object> result = new HashMap<>();
        result.put("pushed", r.isOk());
        result.put("target", target);
        result.put("channel", r.getChannel());
        result.put("pushTime", r.getPushTime().toString());
        result.put("voucherNo", voucher.getVoucherNo());
        result.put("status", r.isOk() ? "SUCCESS" : "FAILED");
        result.put("externalNo", r.getExternalNo());
        if (!r.isOk()) {
            result.put("error", r.getMessage());
        }
        if (r.isOk()) {
            log.info("凭证推送成功: voucherId={}, voucherNo={}, target={}, channel={}, externalNo={}",
                    voucherId, voucher.getVoucherNo(), target, r.getChannel(), r.getExternalNo());
        } else {
            log.warn("凭证推送失败: voucherId={}, voucherNo={}, target={}, channel={}, error={}",
                    voucherId, voucher.getVoucherNo(), target, r.getChannel(), r.getMessage());
        }
        return result;
    }

    /**
     * 批量推送凭证至外部系统。
     * 遍历 voucherIds 逐个推送，返回每个凭证的推送结果（单笔失败不中断整批）。
     */
    public List<Map<String, Object>> pushBatch(List<Long> voucherIds) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long voucherId : voucherIds) {
            try {
                results.add(pushToExternal(voucherId));
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("voucherId", voucherId);
                errorResult.put("pushed", false);
                errorResult.put("status", "FAILED");
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
                log.error("凭证推送失败: voucherId={}", voucherId, e);
            }
        }
        return results;
    }

    /**
     * 查询凭证最近的推送状态。
     * 有推送记录则返回最近一条，否则返回 {status: "NOT_PUSHED"}。
     */
    public Map<String, Object> getPushStatus(Long voucherId) {
        OpVoucherPushLog last = pushLogMapper.selectOne(new LambdaQueryWrapper<OpVoucherPushLog>()
                .eq(OpVoucherPushLog::getVoucherId, voucherId)
                .orderByDesc(OpVoucherPushLog::getId)
                .last("limit 1"));
        if (last == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "NOT_PUSHED");
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", last.getStatus());
        result.put("target", last.getTarget());
        result.put("channel", last.getChannel());
        result.put("externalNo", last.getExternalNo());
        result.put("pushTime", last.getPushTime() == null ? null : last.getPushTime().toString());
        result.put("voucherNo", last.getVoucherNo());
        result.put("error", last.getErrorMsg());
        return result;
    }

    /** 组装推送报文（凭证头 + 分录摘要），供外部系统建账 */
    private Map<String, Object> buildPayload(OpVoucher v) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("voucherNo", v.getVoucherNo());
        payload.put("voucherWord", v.getVoucherWord());
        payload.put("companyId", v.getCompanyId());
        payload.put("periodYear", v.getPeriodYear());
        payload.put("periodMonth", v.getPeriodMonth());
        payload.put("voucherDate", v.getVoucherDate() == null ? null : v.getVoucherDate().toString());
        payload.put("summary", v.getSummary());
        payload.put("debitTotal", v.getDebitTotal());
        payload.put("creditTotal", v.getCreditTotal());
        payload.put("sourceType", v.getSourceType());

        List<OpVoucherLine> lines = opVoucherLineMapper.selectByVoucherId(v.getId());
        payload.put("lines", lines.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.getLineNo());
            m.put("summary", l.getSummary());
            m.put("subjectCode", l.getSubjectCode());
            m.put("subjectName", l.getSubjectName());
            m.put("direction", l.getDirection());
            m.put("amount", l.getAmount());
            m.put("currencyCode", l.getCurrencyCode());
            return m;
        }).collect(Collectors.toList()));
        return payload;
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
