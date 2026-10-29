// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.treasury.entity.TrTransfer;
import com.fssc.treasury.mapper.TrTransferMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资金调拨服务。
 * create(草稿 DRAFT) -> APPROVED 审批 -> execute 执行落银行账户余额(PAID)。
 * 调拨单创建即发起 TR_TRANSFER 资金上存/调拨审批流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrTransferService {

    private final TrTransferMapper mapper;
    private final MdBankAccountMapper bankAccountMapper;
    private final WfEngineFacade wfEngineFacade;
    /**
     * 乙：只读取调拨单的账证侧（凭证号／过账态），用于把"审批已过、钱未动、账未登"
     * 三件事分开说明。仅 SELECT，不在本类生成或过账凭证。
     */
    private final OpVoucherMapper voucherMapper;
    /**
     * 只读取 sys_user 的姓名列，把执行人 id 解析成真名（列表页外显"执行人"列）。
     * 仅 SELECT，不在本类写用户。
     */
    private final SysUserMapper sysUserMapper;

    /** 创建调拨草稿 */
    public Long create(TrTransfer transfer) {
        if (transfer.getFromBankAccountId() == null || transfer.getToBankAccountId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转出/转入银行账户不能为空");
        }
        if (transfer.getFromBankAccountId().equals(transfer.getToBankAccountId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转出与转入账户不能相同");
        }
        if (transfer.getAmount() == null || transfer.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调拨金额必须大于 0");
        }
        if (transfer.getStatus() == null) transfer.setStatus("DRAFT");
        // 创建即生成单号（口径：单据创建即有业务主键），
        // 否则 transfer_no 恒为空，wf_instance.biz_no 也就无法回写。
        if (!StringUtils.hasText(transfer.getTransferNo())) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long seq = mapper.selectCount(new LambdaQueryWrapper<TrTransfer>()
                    .likeRight(TrTransfer::getTransferNo, "DB" + date)) + 1;
            transfer.setTransferNo(String.format("DB%s%04d", date, seq));
        }
        transfer.setId(null);
        transfer.setTenantId(TenantContext.getTenantId());
        mapper.insert(transfer);
        // 调拨单创建即发起 TR_TRANSFER 资金上存/调拨审批流（出纳经办→资金岗复核→财务经理/财务总监阈值审批）
        try {
            wfEngineFacade.start("TR_TRANSFER", transfer.getId(), userId(),
                    "资金调拨-" + transfer.getId(), transfer.getAmount());
        } catch (Exception ex) {
            log.error("资金调拨单 {} 发起调拨审批失败: {}", transfer.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return transfer.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 草稿/被驳回调拨单重新提交审批。驳回侧 TreasuryBizAction 保持 DRAFT「待重提」，
     * 但若没有 submit 端点（唯一出口是 approve 旁路），重提就只能绕道；本方法即正规通道。
     *
     * @param flowType TR_TRANSFER（调拨，默认）/ TR_UPLOAD（上存）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, String flowType) {
        TrTransfer t = requireExist(id);
        if (!"DRAFT".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿（含驳回后）调拨单可提交审批，当前状态: " + t.getStatus());
        }
        boolean upload = "TR_UPLOAD".equals(flowType);
        String model = upload ? "TR_UPLOAD" : "TR_TRANSFER";
        if (wfEngineFacade.hasActiveTask(model, id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请勿重复提交：该调拨单已有在办审批流程");
        }
        try {
            wfEngineFacade.start(model, id, userId(),
                    (upload ? "资金上存-" : "资金调拨-") + (t.getTransferNo() != null ? t.getTransferNo() : String.valueOf(id)),
                    t.getAmount());
        } catch (Exception ex) {
            log.error("资金调拨单 {} 重新发起审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡
        }
    }

    /**
     * 资金上存（T1）：将基地/项目多余资金上划集团资金池账户。
     * 复用 tr_transfer，purpose 标注"上存集团"，发起 TR_UPLOAD 上存审批流。
     */
    public Long upload(TrTransfer transfer) {
        if (transfer.getFromBankAccountId() == null || transfer.getToBankAccountId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上存/接收银行账户不能为空");
        }
        if (transfer.getAmount() == null || transfer.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上存金额必须大于 0");
        }
        if (transfer.getStatus() == null) transfer.setStatus("DRAFT");
        transfer.setId(null);
        transfer.setTenantId(TenantContext.getTenantId());
        transfer.setPurpose((transfer.getPurpose() == null ? "" : transfer.getPurpose() + "；") + "资金上存集团");
        mapper.insert(transfer);
        try {
            wfEngineFacade.start("TR_UPLOAD", transfer.getId(), userId(),
                    "资金上存-" + transfer.getId(), transfer.getAmount());
        } catch (Exception ex) {
            log.error("资金上存单 {} 发起上存审批失败: {}", transfer.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return transfer.getId();
    }

    /**
     * 审批通过：DRAFT -> APPROVED
     * <p>乙：本方法与流程收尾都<b>不</b>直调 {@link #execute(Long)}——审批放行是授权，
     * 动账是另一件事，仍由出纳人工执行；可读性由 {@link #fillExecState(List)} 在详情/列表出参明示。
     */
    @Transactional
    public void approve(Long id) {
        TrTransfer transfer = requireExist(id);
        if ("PAID".equals(transfer.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已执行调拨不可重复审批");
        }
        transfer.setStatus("APPROVED");
        mapper.updateById(transfer);
    }

    /** 调拨执行：DRAFT/APPROVED 落银行账户余额 -> PAID */
    @Transactional
    public void execute(Long id) {
        TrTransfer transfer = requireExist(id);
        if ("PAID".equals(transfer.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该调拨已执行");
        }
        BigDecimal amount = transfer.getAmount();

        MdBankAccount from = bankAccountMapper.selectById(transfer.getFromBankAccountId());
        if (from == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转出银行账户不存在");
        }
        MdBankAccount to = bankAccountMapper.selectById(transfer.getToBankAccountId());
        if (to == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转入银行账户不存在");
        }
        BigDecimal fromBalance = nz(from.getBalance());
        if (fromBalance.compareTo(amount) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转出账户余额不足");
        }

        MdBankAccount fromUpd = new MdBankAccount();
        fromUpd.setId(from.getId());
        fromUpd.setBalance(fromBalance.subtract(amount));

        MdBankAccount toUpd = new MdBankAccount();
        toUpd.setId(to.getId());
        toUpd.setBalance(nz(to.getBalance()).add(amount));

        bankAccountMapper.updateById(fromUpd);
        bankAccountMapper.updateById(toUpd);

        transfer.setStatus("PAID");
        mapper.updateById(transfer);
    }

    public TrTransfer detail(Long id) {
        TrTransfer t = requireExist(id);
        fillExecState(List.of(t));
        return t;
    }

    public PageResult<TrTransfer> page(long pageNum, long pageSize, String status) {
        LambdaQueryWrapper<TrTransfer> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) qw.eq(TrTransfer::getStatus, status);
        qw.orderByDesc(TrTransfer::getId);
        Page<TrTransfer> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillExecState(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 乙：把"审批已过／钱未动／账未登"三件事在接口上分开说明（不改状态机、不新建列）。
     * <p>推导只用现有列：{@code tr_transfer.status} 是唯一状态载体，而 {@link #execute(Long)} 是
     * 改 {@code md_bank_account.balance} 的唯一环节 ⇒ status≠PAID 即"余额未变"；
     * 本域没有现金/银行日记账的登记环节（全仓无日记账写入），故登账位恒为未登账——
     * 审批收尾生成的凭证已过账，容易被读成"钱已划走"，这里把凭证号与过账态一并带出以便对照。
     * <p>凭证按 {@code op_voucher.source_doc_type ∈ (TR_TRANSFER, TR_UPLOAD)＋source_doc_id} 批量取一次，
     * 列表页不逐行查。
     * <p>追加：已执行行的执行人真名（{@code update_by} → sys_user.nick_name）同样批量解析一次，
     * 使列表能直接外显"执行人/执行时间"两列，不必再去 {@code sys_oper_log} 翻流水。
     */
    private void fillExecState(List<TrTransfer> rows) {
        if (rows == null || rows.isEmpty()) return;
        Map<Long, OpVoucher> vouchers = new HashMap<>();
        List<Long> ids = rows.stream().map(TrTransfer::getId).filter(Objects::nonNull).toList();
        if (!ids.isEmpty()) {
            for (OpVoucher v : voucherMapper.selectList(
                    new LambdaQueryWrapper<OpVoucher>()
                            .in(OpVoucher::getSourceDocType, List.of("TR_TRANSFER", "TR_UPLOAD"))
                            .in(OpVoucher::getSourceDocId, ids)
                            .orderByAsc(OpVoucher::getId))) {
                if (v.getSourceDocId() != null) vouchers.putIfAbsent(v.getSourceDocId(), v);
            }
        }
        // 执行人真名批查一次（只对已执行行取——execute() 是 tr_transfer 置 PAID 的唯一写环节，
        // 故 PAID 行的 update_by/update_time 就是执行人/执行时刻；未执行行的 update_by 是审批或改动者，
        // 拿它当"执行人"会张冠李戴，一律不填）。
        Map<Long, String> execNames = new HashMap<>();
        List<Long> execUids = rows.stream()
                .filter(t -> "PAID".equals(t.getStatus()))
                .map(TrTransfer::getUpdateBy)
                .filter(uid -> uid != null && uid > 0)
                .distinct()
                .toList();
        if (!execUids.isEmpty()) {
            for (SysUser u : sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getId, execUids))) {
                execNames.put(u.getId(), realNameOf(u));
            }
        }
        for (TrTransfer t : rows) {
            boolean paid = "PAID".equals(t.getStatus());
            boolean approved = "APPROVED".equals(t.getStatus());
            OpVoucher v = t.getId() == null ? null : vouchers.get(t.getId());
            boolean posted = v != null && "POSTED".equals(v.getStatus());
            t.setApproved(approved || paid);
            t.setExecuted(paid);
            t.setBalanceMoved(paid);
            t.setJournalBooked(false);
            t.setExecutorName(paid ? execNames.get(t.getUpdateBy()) : null);
            t.setVoucherNo(v == null ? null : v.getVoucherNo());
            t.setVoucherStatus(v == null ? null : v.getStatus());
            if (paid) {
                t.setExecStateCode("EXECUTED");
                t.setExecStateText("已执行·账户余额已落账");
                t.setJournalText("未登账：现金/银行日记账未登记，动账后仍需另行登账");
                t.setNextActionText(null);
            } else if (approved) {
                t.setExecStateCode("APPROVED_NOT_EXECUTED");
                t.setExecStateText("已审批·未执行·账户余额未变");
                t.setJournalText("未登账：现金/银行日记账未登记"
                        + (posted ? "（凭证 " + v.getVoucherNo() + " 已过账，凭证过账不等于已动账、也不等于已登账）" : ""));
                t.setNextActionText("需由出纳执行「调拨执行」后款项才真的在两个账户间划转");
            } else {
                t.setExecStateCode("NOT_APPROVED");
                t.setExecStateText("未审批·未执行·账户余额未变");
                t.setJournalText("未登账：现金/银行日记账未登记");
                t.setNextActionText("先提交审批，审批通过后再执行动账");
            }
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 真名规则＝nickName 优先、空白回落 userName（工号），与应付域/退货域同一套 */
    private String realNameOf(SysUser u) {
        if (u == null) return null;
        String nick = u.getNickName();
        return StringUtils.hasText(nick) ? nick.trim() : u.getUserName();
    }

    private TrTransfer requireExist(Long id) {
        TrTransfer transfer = mapper.selectById(id);
        if (transfer == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金调拨单不存在：" + id);
        }
        return transfer;
    }
}