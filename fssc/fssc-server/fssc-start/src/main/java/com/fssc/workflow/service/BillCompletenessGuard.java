// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillAttachMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.mapper.OpPayBatchDetailMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资料齐套闸口（F2／方案 §3，2026-09-26）。
 *
 * <p>回答体检 E-01/E-02 的"前面为什么没拦"：附件、合同/PO、出账账户外键在现网全部零校验，
 * 缺件单据可以一路直走到付款与归档（十二月运营手册 12-22 三控制点全空仍审批通过、
 * 12-23 批次确认时供应商银行账户档案 0 条，均为本组件要收口的对象）。
 *
 * <p>调用点：{@code WfEngineFacade.start}（SUBMIT）、{@code WfEngineFacade.complete}（NODE:节点）、
 * {@code PayBatchService.confirm}（CONFIRM）、{@code PayBatchService.execute}（EXECUTE 二次闸）。
 * 按人工裁决 RA-F2a 的口径，SUBMIT 层只告警不阻断（避免提单人被"影像还没上传"彻底卡死），
 * CONFIRM／NODE 层缺件硬拦。
 *
 * <p>灰度（缺省＝改造前行为）：{@code sys_config} 三键
 * {@code bill.completeness.gate.enabled=1} ＋ {@code bill.completeness.models} 白名单
 * ＋ {@code bill.completeness.rules} 分维度规则；任一缺失或读取异常都按未生效处理。
 * 唯一例外是 D4 出账账户外键：账户主数据里查不到对应记录属脏数据硬错误，不吃灰度开关，
 * 但只在 CONFIRM（生成资金指令那一步）与 EXECUTE（对外付款动作前，RA-F2d 二次闸）拒绝——在途审批
 * 节点的「同意」不因此被打死；存量悬空批次由只读普查出清单后逐单人工修数（RA-F2c），不由代码兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillCompletenessGuard {

    /** 提单收口：只告警（RA-F2a） */
    public static final String STAGE_SUBMIT = "SUBMIT";
    /** 付款批次确认：硬拦 */
    public static final String STAGE_CONFIRM = "CONFIRM";
    /** 审批办理：{@code NODE:<taskDefinitionKey>}，硬拦 */
    public static final String STAGE_NODE_PREFIX = "NODE:";
    /**
     * 付款执行（对外打款动作前）二次闸，硬拦——人工裁决 RA-F2d（REF-B「两点都拦」）。
     *
     * <b>没有</b>"付款执行前再查一次齐套"的成文条目。本档是把同一套标准复用到打款这一步，
     *
     * <p>与"下移"的区别：确认时点（{@link #STAGE_CONFIRM}）的闸口<b>保留不动</b>，此处只做增量；
     * 规则维度沿用 {@code modelKey:CONFIRM} 已配置的那一套（见 {@link #scopeMatches}），
     * 免得多出一个"配了才生效"的假开关。
     */
    public static final String STAGE_EXECUTE = "EXECUTE";

    private static final String KEY_ENABLED = "bill.completeness.gate.enabled";
    private static final String KEY_MODELS = "bill.completeness.models";
    private static final String KEY_RULES = "bill.completeness.rules";

    /** 维度 D1：附件/发票影像件数 */
    private static final String DIM_ATTACH = "attach";
    /** 维度 D2：合同或采购订单至少关联其一 */
    private static final String DIM_CONTRACT_OR_PO = "contract_or_po";

    private final SysConfigMapper sysConfigMapper;
    private final ExpBillMapper expBillMapper;
    private final ExpBillAttachMapper expBillAttachMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final OpPayBatchDetailMapper opPayBatchDetailMapper;
    private final MdBankAccountMapper mdBankAccountMapper;

    /**
     * 齐套校验入口。
     *
     * @param modelKey 流程 key（{@code wf_instance.model_key}／processKey），灰度白名单按它取
     * @param stage    {@link #STAGE_SUBMIT}／{@link #STAGE_CONFIRM}／{@link #STAGE_EXECUTE}／{@code NODE:Task_7}
     * @param bizType  流程业务类型：{@code EXP_CLAIM/EXP_APPLY/EXP_LOAN/EXP_REPAY/EXP_PAY}（bizId=exp_bill.id）
     *                 或 {@code TR_PAY}（bizId=op_pay_batch.id）
     * @param bizId    业务主键
     * @throws BizException 缺件（CONFIRM／EXECUTE／NODE 档）；SUBMIT 档只写日志不抛
     */
    public void assertComplete(String modelKey, String stage, String bizType, Long bizId) {
        if (bizId == null || bizType == null || bizType.isBlank()) {
            return;
        }
        boolean isBatch = "TR_PAY".equals(bizType);
        // D4：出账账户外键悬空＝脏数据硬错误，不设开关（方案 §3.1 D4 例外条款）
        if (isBatch) {
            assertBankAccountResolvable(bizId, stage);
        }
        List<ExpBill> bills = isBatch ? billsOfBatch(bizId) : billsOfBill(bizId);
        List<String> missing = new ArrayList<>();
        for (String dim : dimensionsOf(modelKey, stage)) {
            if (DIM_ATTACH.equals(dim)) {
                missing.addAll(missingAttach(bills));
            } else if (DIM_CONTRACT_OR_PO.equals(dim)) {
                missing.addAll(missingContractOrPo(bills));
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        String detail = String.join("；", missing);
        if (stage != null && stage.startsWith(STAGE_SUBMIT)) {
            // RA-F2a：提交层只告警，缺件单据仍可提交，由后续 NODE/CONFIRM 档收口
            log.warn("齐套告警（提单层不阻断）[{}#{}] {}", modelKey, bizId, detail);
            return;
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "资料不齐，闸口拒绝（F2 齐套闸口·" + modelKey + "·" + stage + "）：" + detail);
    }

    // === D4 ===

    private void assertBankAccountResolvable(Long batchId, String stage) {
        OpPayBatch batch = opPayBatchMapper.selectById(batchId);
        if (batch == null || batch.getBankAccountId() == null) {
            return;
        }
        MdBankAccount account = mdBankAccountMapper.selectById(batch.getBankAccountId());
        if (account != null && (account.getDelFlag() == null || account.getDelFlag() == 0)) {
            return;
        }
        String msg = "批次 " + batch.getBatchNo() + " 的出账账户在银行账户主数据中不存在（id="
                + batch.getBankAccountId() + "），请先在公司银行账户档案建档并重新指定出账账户";
        // 只在 CONFIRM（生成资金指令的那一步）与 EXECUTE（打款动作前，RA-F2d 二次闸）硬拦：审批节点是
        // 复核动作，把在途 TR_PAY 实例的「同意」也一起打死，等于用脏数据把已受理的业务永久摁住——
        // 存量悬空批次按 RA-F2c 出清单人工修数。
        if (!STAGE_CONFIRM.equals(stage) && !STAGE_EXECUTE.equals(stage)) {
            log.warn("齐套告警（非确认/执行档不阻断）[{}#batch{}] {}", stage, batchId, msg);
            return;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, msg + "（F2 齐套闸口·D4 账户外键）");
    }

    // === 规则解析 ===

    /** 该 modelKey＋stage 需要校验的维度；闸口未开或无规则时返回空（＝零行为变更）。 */
    private List<String> dimensionsOf(String modelKey, String stage) {
        if (!"1".equals(readConfig(KEY_ENABLED))) {
            return List.of();
        }
        if (!inWhitelist(readConfig(KEY_MODELS), modelKey)) {
            return List.of();
        }
        String rules = readConfig(KEY_RULES);
        if (rules == null || rules.isBlank()) {
            return List.of();
        }
        Set<String> dims = new LinkedHashSet<>();
        for (String entry : rules.split(";")) {
            String e = entry.trim();
            int eq = e.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String scope = e.substring(0, eq).trim();
            // 规则串形如 TR_PAY:CONFIRM=attach,contract_or_po；NODE 档支持逐节点与全链通配
            if (!scopeMatches(scope, modelKey, stage)) {
                continue;
            }
            for (String d : e.substring(eq + 1).split(",")) {
                if (!d.trim().isEmpty()) {
                    dims.add(d.trim());
                }
            }
        }
        return new ArrayList<>(dims);
    }

    private static boolean scopeMatches(String scope, String modelKey, String stage) {
        String[] parts = scope.split(":");
        if (parts.length < 2 || !parts[0].trim().equalsIgnoreCase(modelKey == null ? "" : modelKey.trim())) {
            return false;
        }
        String wantStage = stage == null ? "" : stage.trim();
        String wantNode = wantStage.startsWith(STAGE_NODE_PREFIX)
                ? wantStage.substring(STAGE_NODE_PREFIX.length()) : "";
        if (parts[1].trim().equalsIgnoreCase(STAGE_SUBMIT) && wantStage.equalsIgnoreCase(STAGE_SUBMIT)) {
            return parts.length == 2;
        }
        if (parts[1].trim().equalsIgnoreCase(STAGE_CONFIRM)
                // EXECUTE（RA-F2d 二次闸）沿用 CONFIRM 档已配的同一套维度：两点同标准，不另立需要
                // 额外配置才生效的规则键（现网 bill.completeness.* 三键在库中 0 行，另立即成假开关）。
                && (STAGE_CONFIRM.equalsIgnoreCase(wantStage) || STAGE_EXECUTE.equalsIgnoreCase(wantStage))) {
            return parts.length == 2;
        }
        if (!parts[1].trim().equalsIgnoreCase(STAGE_NODE_PREFIX.isEmpty() ? "" : "NODE")) {
            return false;
        }
        // NODE 通配（modelKey:NODE）对任意节点生效；modelKey:NODE:Task_7 只对该节点生效
        return parts.length == 2 || parts[2].trim().equalsIgnoreCase(wantNode);
    }

    private static boolean inWhitelist(String csv, String modelKey) {
        if (csv == null || csv.isBlank() || modelKey == null) {
            return false;
        }
        for (String m : csv.split(",")) {
            if (modelKey.equalsIgnoreCase(m.trim())) {
                return true;
            }
        }
        return false;
    }

    private String readConfig(String key) {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key).last("limit 1"));
            return c == null || c.getConfigValue() == null ? null : c.getConfigValue().trim();
        } catch (Exception ex) {
            log.error("读取齐套闸口配置异常，按未生效处理 {}: {}", key, ex.getMessage());
            return null;
        }
    }

    // === 维度 D1 / D2 ===

    private List<String> missingAttach(List<ExpBill> bills) {
        List<String> out = new ArrayList<>();
        for (ExpBill bill : bills) {
            long n = expBillAttachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, bill.getId()));
            if (n < 1) {
                out.add("单据 " + billNo(bill) + " 无附件/发票影像（0 件，要求 ≥1）");
            }
        }
        return out;
    }

    private List<String> missingContractOrPo(List<ExpBill> bills) {
        List<String> out = new ArrayList<>();
        for (ExpBill bill : bills) {
            if (bill.getContractId() == null && bill.getPoId() == null) {
                out.add("单据 " + billNo(bill) + " 未关联合同，也未关联采购订单");
            }
        }
        return out;
    }

    private List<ExpBill> billsOfBill(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        return bill == null ? List.of() : List.of(bill);
    }

    /** 批次 → 明细 → 付款记录 → 源单据（多源合并批次逐张核，任何一张缺件即整体拒绝） */
    private List<ExpBill> billsOfBatch(Long batchId) {
        List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>().eq(OpPayBatchDetail::getBatchId, batchId));
        Set<Long> billIds = new LinkedHashSet<>();
        for (OpPayBatchDetail d : details) {
            if (d.getBillPaymentId() == null) {
                continue;
            }
            ExpBillPayment p = expBillPaymentMapper.selectById(d.getBillPaymentId());
            if (p != null && p.getBillId() != null) {
                billIds.add(p.getBillId());
            }
        }
        List<ExpBill> bills = new ArrayList<>();
        for (Long id : billIds) {
            ExpBill bill = expBillMapper.selectById(id);
            if (bill != null) {
                bills.add(bill);
            }
        }
        return bills;
    }

    private static String billNo(ExpBill bill) {
        return bill.getBillNo() == null ? ("id=" + bill.getId()) : bill.getBillNo();
    }
}
