package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.sharing.entity.OpAuditTask;
import com.fssc.sharing.entity.OpBarcode;
import com.fssc.sharing.entity.OpPhysicalDelivery;
import com.fssc.sharing.mapper.OpAuditTaskMapper;
import com.fssc.sharing.mapper.OpBarcodeMapper;
import com.fssc.sharing.mapper.OpPhysicalDeliveryMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P0-5 实物流签收服务（行业特性 TC12：电子流 + 实物流双到达）。
 * 传统交单模式：实物未签收前，共享会计（AUDIT）待办不暴露对应审核任务；
 * 共享会计签收实物后才激活首环节审核任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final OpPhysicalDeliveryMapper deliveryMapper;
    private final OpAuditTaskMapper opAuditTaskMapper;
    private final ExpBillMapper expBillMapper;
    private final OpBarcodeMapper barcodeMapper;
    /** R5-D-231：交单登记自动生成条码 */
    private final BarcodeService barcodeService;
    /** R5-D-239：交付人姓名统一取 nick_name */
    private final SysUserMapper sysUserMapper;

    /**
     * 判定单据是否需要实物签收：
     * 无纸化单据（paperless=Y）免实物；其余（N/未设，含纸质交单传统模式）需签收。
     */
    public boolean requiresDelivery(ExpBill bill) {
        return !"Y".equals(bill.getPaperless());
    }

    /**
     * 若无纸化豁免则置单据 deliveryStatus=NOT_REQUIRED；
     * 否则登记一张 PENDING 实物流签收单并把单据置 UNSIGNED（幂等：已登记不重复建）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerIfNeeded(ExpBill bill) {
        if (bill == null || bill.getId() == null) {
            return;
        }
        // R5-D-243：草稿/已取消单据不得进入交付链（此前 DRAFT 单也登记了 PENDING 交付与共享作业任务）
        if ("DRAFT".equals(bill.getStatus()) || "CANCELLED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据处于草稿/已取消状态，不可登记实物流（请先提交单据）");
        }
        if ("Y".equals(bill.getPaperless())) {
            if (!"NOT_REQUIRED".equals(bill.getDeliveryStatus())) {
                ExpBill upd = new ExpBill();
                upd.setId(bill.getId());
                upd.setDeliveryStatus("NOT_REQUIRED");
                expBillMapper.updateById(upd);
            }
            return;
        }
        // 需实物：若尚未登记签收单则新建
        Long cnt = deliveryMapper.selectCount(new LambdaQueryWrapper<OpPhysicalDelivery>()
                .eq(OpPhysicalDelivery::getBillId, bill.getId())
                .eq(OpPhysicalDelivery::getStatus, "PENDING"));
        if (cnt != null && cnt > 0) {
            // R5-D-229 同口径：已有在途 PENDING 单而单据侧无痕（NULL）时补 UNSIGNED，消除双轨
            markBillUnsignedInTransit(bill);
            return;
        }
        OpPhysicalDelivery d = new OpPhysicalDelivery();
        d.setTenantId(TenantContext.getTenantId());
        d.setBillId(bill.getId());
        d.setBillNo(bill.getBillNo());
        d.setBillType(bill.getBillType());
        d.setDeliverBy(bill.getApplyUserId());
        // R5-D-239：姓名统一取 nick_name（此前取 applyUserName＝裸工号，如 Y005713）
        d.setDeliverName(resolveDisplayName(bill.getApplyUserId(), bill.getApplyUserName()));
        d.setDeliveryType("HANDOVER");
        d.setStatus("PENDING");
        d.setDeliverTime(LocalDateTime.now());
        deliveryMapper.insert(d);
        ExpBill upd = new ExpBill();
        upd.setId(bill.getId());
        upd.setDeliveryStatus("UNSIGNED");
        expBillMapper.updateById(upd);
        log.info("实物流待签收已登记 billNo={} deliveryId={}", bill.getBillNo(), d.getId());
    }

    /**
     * 共享会计/档案岗签收实物：置 SIGNED，单据 deliveryStatus=SIGNED，
     * 并激活该单据首个共享 AUDIT 审核任务（R5-D-230：仅当存在共享任务链时——APPLY/事前申请类
     * 单据不入共享作业、无任务链，此时静默跳过，属设计口径而非链路缺口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long deliveryId, Long signBy, String signName) {
        sign(deliveryId, signBy, signName, null);
    }

    /** R5-D-241：带签收备注的签收（sign_remark 随签收一次写入，此前单条签收路径恒 NULL） */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long deliveryId, Long signBy, String signName, String signRemark) {
        OpPhysicalDelivery d = deliveryMapper.selectById(deliveryId);
        if (d == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实物流签收单不存在: " + deliveryId);
        }
        if (!"PENDING".equals(d.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该签收单已处理，当前状态: " + d.getStatus());
        }
        // R5-D-243：草稿/已取消单据不可签收（签收后无审批对象，故前置拦截）
        if (d.getBillId() != null) {
            ExpBill bill = expBillMapper.selectById(d.getBillId());
            if (bill != null && ("DRAFT".equals(bill.getStatus()) || "CANCELLED".equals(bill.getStatus()))) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "单据处于草稿/已取消状态，不可签收，请先提交单据");
            }
        }
        OpPhysicalDelivery upd = new OpPhysicalDelivery();
        upd.setId(deliveryId);
        upd.setStatus("SIGNED");
        upd.setSignBy(signBy);
        upd.setSignName(signName == null || signName.isBlank() ? "共享会计" + (signBy == null ? "" : signBy) : signName);
        upd.setSignTime(LocalDateTime.now());
        if (signRemark != null && !signRemark.isBlank()) {
            upd.setSignRemark(signRemark);
        }
        deliveryMapper.updateById(upd);
        if (d.getBillId() != null) {
            ExpBill bUpd = new ExpBill();
            bUpd.setId(d.getBillId());
            bUpd.setDeliveryStatus("SIGNED");
            expBillMapper.updateById(bUpd);
            // 激活首环节共享审核任务（若存在任务链则激活首环节；APPLY 类单据无链，静默跳过——R5-D-230）
            OpAuditTask first = opAuditTaskMapper.selectOne(new LambdaQueryWrapper<OpAuditTask>()
                    .eq(OpAuditTask::getBillId, d.getBillId())
                    .eq(OpAuditTask::getTaskType, "AUDIT")
                    .orderByAsc(OpAuditTask::getChainSeq).last("limit 1"));
            if (first != null) {
                OpAuditTask act = new OpAuditTask();
                act.setId(first.getId());
                act.setActiveFlag("Y");
                opAuditTaskMapper.updateById(act);
            }
        }
        log.info("实物流已签收 deliveryId={} billNo={} by={}", deliveryId, d.getBillNo(), signName);
    }

    /** 待签收清单 */
    public PageResult<OpPhysicalDelivery> pending(long pageNum, long pageSize) {
        Page<OpPhysicalDelivery> p = deliveryMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<OpPhysicalDelivery>()
                        .eq(OpPhysicalDelivery::getStatus, "PENDING")
                        .orderByAsc(OpPhysicalDelivery::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public List<OpPhysicalDelivery> byBill(Long billId) {
        return deliveryMapper.selectList(new LambdaQueryWrapper<OpPhysicalDelivery>()
                .eq(OpPhysicalDelivery::getBillId, billId).orderByAsc(OpPhysicalDelivery::getId));
    }

    /** 单据是否已满足实物签收门控（无纸化或已签收返回 true；需实物未签收返回 false） */
    public boolean canEnterSharedPool(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null || !requiresDelivery(bill)) {
            return true;
        }
        return "SIGNED".equals(bill.getDeliveryStatus());
    }

    // ==================== 增强功能 ====================

    /**
     * 分页查询签收单（支持多条件筛选）。
     */
    public PageResult<OpPhysicalDelivery> page(long pageNum, long pageSize,
                                                 String status, String billNo, String expressNo,
                                                 String deliveryType, String keyword) {
        LambdaQueryWrapper<OpPhysicalDelivery> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(OpPhysicalDelivery::getStatus, status);
        }
        if (billNo != null && !billNo.isBlank()) {
            w.like(OpPhysicalDelivery::getBillNo, billNo);
        }
        if (expressNo != null && !expressNo.isBlank()) {
            w.like(OpPhysicalDelivery::getExpressNo, expressNo);
        }
        if (deliveryType != null && !deliveryType.isBlank()) {
            w.eq(OpPhysicalDelivery::getDeliveryType, deliveryType);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(OpPhysicalDelivery::getBillNo, keyword)
                    .or().like(OpPhysicalDelivery::getExpressNo, keyword)
                    .or().like(OpPhysicalDelivery::getBarcode, keyword)
                    .or().like(OpPhysicalDelivery::getDeliverName, keyword));
        }
        w.orderByDesc(OpPhysicalDelivery::getId);
        Page<OpPhysicalDelivery> p = deliveryMapper.selectPage(Page.of(pageNum, pageSize), w);
        // R5-D-243：批量回填关联单据状态（UI 对 DRAFT/CANCELLED 单隐藏签收/拒签并给原因）
        enrichBillStatus(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** R5-D-243：批量填充关联单据状态（仅出参，零加列） */
    private void enrichBillStatus(List<OpPhysicalDelivery> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> billIds = rows.stream().map(OpPhysicalDelivery::getBillId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (billIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, String> statusMap = expBillMapper.selectBatchIds(billIds).stream()
                .collect(java.util.stream.Collectors.toMap(ExpBill::getId,
                        b -> b.getStatus() == null ? "" : b.getStatus(), (a, b) -> a));
        for (OpPhysicalDelivery r : rows) {
            if (r.getBillId() != null) {
                r.setBillStatus(statusMap.get(r.getBillId()));
            }
        }
    }

    /**
     * 批量签收：一次签收多个签收单（条码枪逐个扫描后批量提交）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchSign(List<Long> deliveryIds, Long signBy, String signName, String signRemark) {
        int count = 0;
        for (Long id : deliveryIds) {
            OpPhysicalDelivery d = deliveryMapper.selectById(id);
            if (d == null || !"PENDING".equals(d.getStatus())) {
                continue;
            }
            OpPhysicalDelivery upd = new OpPhysicalDelivery();
            upd.setId(id);
            upd.setStatus("SIGNED");
            upd.setSignBy(signBy);
            upd.setSignName(signName == null || signName.isBlank() ? "共享会计" : signName);
            upd.setSignTime(LocalDateTime.now());
            upd.setSignRemark(signRemark);
            deliveryMapper.updateById(upd);
            // Update bill delivery status + activate audit task
            if (d.getBillId() != null) {
                ExpBill bUpd = new ExpBill();
                bUpd.setId(d.getBillId());
                bUpd.setDeliveryStatus("SIGNED");
                expBillMapper.updateById(bUpd);
                activateFirstAuditTask(d.getBillId());
            }
            count++;
        }
        log.info("Batch signed {} deliveries by {}", count, signName);
        return count;
    }

    /**
     * 扫码签收：通过条码枪扫描的值查找签收单并签收。
     * 扫描值可以是：条码(barcode)、单据号(billNo)、运单号(expressNo)。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpPhysicalDelivery scanSign(String scanValue, Long signBy, String signName, String signRemark) {
        // 1. Try matching by barcode
        OpPhysicalDelivery d = deliveryMapper.selectOne(new LambdaQueryWrapper<OpPhysicalDelivery>()
                .eq(OpPhysicalDelivery::getBarcode, scanValue)
                .eq(OpPhysicalDelivery::getStatus, "PENDING")
                .last("limit 1"));
        // 2. Try matching by bill number
        if (d == null) {
            d = deliveryMapper.selectOne(new LambdaQueryWrapper<OpPhysicalDelivery>()
                    .eq(OpPhysicalDelivery::getBillNo, scanValue)
                    .eq(OpPhysicalDelivery::getStatus, "PENDING")
                    .last("limit 1"));
        }
        // 3. Try matching by express number
        if (d == null) {
            d = deliveryMapper.selectOne(new LambdaQueryWrapper<OpPhysicalDelivery>()
                    .eq(OpPhysicalDelivery::getExpressNo, scanValue)
                    .eq(OpPhysicalDelivery::getStatus, "PENDING")
                    .last("limit 1"));
        }
        if (d == null) {
            // R5-D-231：无匹配给出可行动文案（此前仅报未找到，用户不知下一步）
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未找到匹配的待签收单据，扫描值: " + scanValue
                            + "；可扫描条码/单据号/运单号，或先在条码管理中按单据生成条码后重试");
        }
        // Record barcode scan
        if (d.getBarcode() != null && !d.getBarcode().isBlank()) {
            OpBarcode bc = barcodeMapper.selectOne(new LambdaQueryWrapper<OpBarcode>()
                    .eq(OpBarcode::getBarcode, d.getBarcode()).last("limit 1"));
            if (bc != null) {
                OpBarcode bcUpd = new OpBarcode();
                bcUpd.setId(bc.getId());
                bcUpd.setStatus("SCANNED");
                bcUpd.setScanBy(signBy);
                bcUpd.setScanName(signName);
                bcUpd.setScanTime(LocalDateTime.now());
                barcodeMapper.updateById(bcUpd);
            }
        }
        // Sign it（R5-D-241：签收备注与签收同步写入，避免先签后补的中间态）
        sign(d.getId(), signBy, signName, signRemark);
        return deliveryMapper.selectById(d.getId());
    }

    /**
     * 拒签退件（R5-D-240：与 sign() 对称回写——拒签不能只改签收单状态）。
     * 同一事务内：单据侧交付状态退回待签收等待重新交单；未办环节撤回任务池并置签收前挂起态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long deliveryId, Long rejectBy, String rejectName, String rejectReason) {
        OpPhysicalDelivery d = deliveryMapper.selectById(deliveryId);
        if (d == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实物流签收单不存在: " + deliveryId);
        }
        if (!"PENDING".equals(d.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该签收单已处理，当前状态: " + d.getStatus());
        }
        // R5-D-243：草稿/已取消单据不可拒签（与签收同闸，防呆）
        if (d.getBillId() != null) {
            ExpBill bill = expBillMapper.selectById(d.getBillId());
            if (bill != null && ("DRAFT".equals(bill.getStatus()) || "CANCELLED".equals(bill.getStatus()))) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "单据处于草稿/已取消状态，不可拒签，请先提交单据");
            }
        }
        // 必填收口（批次 2C，与前端 DeliveryPage 拒签弹窗同口径）：拒签原因是退件留痕，留空则退回无从追责
        AssertUtil.notBlank(rejectReason, ErrorCode.PARAM_INVALID, "请填写拒签原因");
        OpPhysicalDelivery upd = new OpPhysicalDelivery();
        upd.setId(deliveryId);
        upd.setStatus("REJECTED");
        upd.setSignBy(rejectBy);
        upd.setSignName(rejectName);
        upd.setSignTime(LocalDateTime.now());
        upd.setRejectReason(rejectReason);
        deliveryMapper.updateById(upd);
        if (d.getBillId() != null) {
            // R5-D-240 ①：单据侧对称回写——退回待签收等待重新交单。delivery_status 既有取值域
            // NOT_REQUIRED/UNSIGNED/SIGNED 无「已拒签」码，拒签事实由 op_physical_delivery.REJECTED
            // 与 reject_reason 承载，不发明新码；update_time 随 updateById 经 FsscMetaObjectHandler
            // 的 updateFill 无条件刷新（与 sign() 同一机制）。
            ExpBill bUpd = new ExpBill();
            bUpd.setId(d.getBillId());
            bUpd.setDeliveryStatus("UNSIGNED");
            expBillMapper.updateById(bUpd);
            // R5-D-240 ②：撤链——未办环节（WAITING/RECEIVED）退回任务池并置 activeFlag=N 签收前挂起态
            // （与 SharingService 入池挂起同口径），已认领环节清回认领人与认领时间；不发明新状态，
            // 重新签收后由 sign() 再激活首环节。
            opAuditTaskMapper.update(null, new LambdaUpdateWrapper<OpAuditTask>()
                    .set(OpAuditTask::getPoolStatus, "WAITING")
                    .set(OpAuditTask::getActiveFlag, "N")
                    .set(OpAuditTask::getAssigneeId, null)
                    .set(OpAuditTask::getReceiveTime, null)
                    .eq(OpAuditTask::getBillId, d.getBillId())
                    .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED"));
        }
        log.info("Delivery {} rejected by {} reason={}", deliveryId, rejectName, rejectReason);
    }

    /**
     * 员工交单登记（含快递信息录入）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applyDelivery(Long billId, String expressCompany, String expressNo,
                               Integer packageCount, String deliveryType, String remark,
                               Long deliverBy, String deliverName) {
        // 必填收口（批次 2C，与前端「选择单据」同口径）：否则 selectById(null) 只会给出「单据不存在: null」
        AssertUtil.notNull(billId, ErrorCode.PARAM_INVALID, "请选择单据");
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        // R5-D-243：草稿/已取消单据不得进入交付链（此前 DRAFT 单 id=6 也建了交付与 6 条工单）
        if ("DRAFT".equals(bill.getStatus()) || "CANCELLED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据处于草稿/已取消状态，不可交单登记（请先提交单据）");
        }
        // R5-D-239：交付人姓名统一取 sys_user.nick_name（此前存裸工号如 Y005713，与人员列治理同批）
        Long effectiveDeliverBy = deliverBy != null ? deliverBy : bill.getApplyUserId();
        String effectiveDeliverName = resolveDisplayName(effectiveDeliverBy,
                deliverName != null ? deliverName : bill.getApplyUserName());
        // Check if already has a PENDING delivery
        Long cnt = deliveryMapper.selectCount(new LambdaQueryWrapper<OpPhysicalDelivery>()
                .eq(OpPhysicalDelivery::getBillId, billId)
                .eq(OpPhysicalDelivery::getStatus, "PENDING"));
        if (cnt != null && cnt > 0) {
            // Update existing with express info
            OpPhysicalDelivery existing = deliveryMapper.selectOne(new LambdaQueryWrapper<OpPhysicalDelivery>()
                    .eq(OpPhysicalDelivery::getBillId, billId)
                    .eq(OpPhysicalDelivery::getStatus, "PENDING")
                    .last("limit 1"));
            OpPhysicalDelivery upd = new OpPhysicalDelivery();
            upd.setId(existing.getId());
            upd.setExpressCompany(expressCompany);
            upd.setExpressNo(expressNo);
            upd.setPackageCount(packageCount);
            upd.setDeliveryType(deliveryType != null ? deliveryType : "POST");
            upd.setRemark(remark);
            upd.setDeliverBy(effectiveDeliverBy);
            upd.setDeliverName(effectiveDeliverName);
            upd.setDeliverTime(LocalDateTime.now());
            // R5-D-231：存量在途单补生条码（此前邮寄交单 barcode 恒 NULL，条码管理与扫码签收无输入）
            if (existing.getBarcode() == null || existing.getBarcode().isBlank()) {
                OpBarcode bc = barcodeService.generateForRef("BILL", billId, bill.getBillNo(),
                        effectiveDeliverBy, effectiveDeliverName);
                if (bc != null) {
                    upd.setBarcode(bc.getBarcode());
                }
            }
            deliveryMapper.updateById(upd);
            // R5-D-229：交单在途期单据侧同步置 UNSIGNED（幂等）
            markBillUnsignedInTransit(bill);
            return existing.getId();
        }
        // Create new delivery record
        OpPhysicalDelivery d = new OpPhysicalDelivery();
        d.setTenantId(TenantContext.getTenantId());
        d.setBillId(billId);
        d.setBillNo(bill.getBillNo());
        d.setBillType(bill.getBillType());
        d.setDeliverBy(effectiveDeliverBy);
        d.setDeliverName(effectiveDeliverName);
        d.setDeliveryType(deliveryType != null ? deliveryType : "POST");
        d.setExpressCompany(expressCompany);
        d.setExpressNo(expressNo);
        d.setPackageCount(packageCount != null ? packageCount : 1);
        d.setRemark(remark);
        d.setStatus("PENDING");
        d.setDeliverTime(LocalDateTime.now());
        deliveryMapper.insert(d);
        // R5-D-231：登记成功即生成条码并回写签收单（扫码签收/条码管理页随即可用）
        OpBarcode bc = barcodeService.generateForRef("BILL", billId, bill.getBillNo(),
                effectiveDeliverBy, effectiveDeliverName);
        if (bc != null) {
            OpPhysicalDelivery bcUpd = new OpPhysicalDelivery();
            bcUpd.setId(d.getId());
            bcUpd.setBarcode(bc.getBarcode());
            deliveryMapper.updateById(bcUpd);
        }
        // R5-D-229：交单成功后同步置 UNSIGNED（幂等），使在途可视并供 SLA 统计
        markBillUnsignedInTransit(bill);
        log.info("Delivery applied billNo={} expressNo={} deliveryId={} barcode={}", bill.getBillNo(), expressNo, d.getId(),
                bc == null ? null : bc.getBarcode());
        return d.getId();
    }

    /**
     * 员工查看自己的交单记录。
     */
    public PageResult<OpPhysicalDelivery> myDeliveries(long pageNum, long pageSize, Long deliverBy) {
        LambdaQueryWrapper<OpPhysicalDelivery> w = new LambdaQueryWrapper<>();
        if (deliverBy != null) {
            w.eq(OpPhysicalDelivery::getDeliverBy, deliverBy);
        }
        w.orderByDesc(OpPhysicalDelivery::getId);
        Page<OpPhysicalDelivery> p = deliveryMapper.selectPage(Page.of(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 激活首环节共享审核任务 */
    private void activateFirstAuditTask(Long billId) {
        OpAuditTask first = opAuditTaskMapper.selectOne(new LambdaQueryWrapper<OpAuditTask>()
                .eq(OpAuditTask::getBillId, billId)
                .eq(OpAuditTask::getTaskType, "AUDIT")
                .orderByAsc(OpAuditTask::getChainSeq).last("limit 1"));
        if (first != null) {
            OpAuditTask act = new OpAuditTask();
            act.setId(first.getId());
            act.setActiveFlag("Y");
            opAuditTaskMapper.updateById(act);
        }
    }

    /**
     * R5-D-229：交单在途期单据侧留痕——将 exp_bill.deliveryStatus 置 UNSIGNED（幂等）。
     * 已有 SIGNED/NOT_REQUIRED 终态记不回退（重复交单/签收后再登记不产生状态倒挂）；
     * 存量裸工号姓名等历史脏行不在本方法治理范围（只读清单交人工）。
     */
    private void markBillUnsignedInTransit(ExpBill bill) {
        if (bill == null || bill.getId() == null) {
            return;
        }
        String cur = bill.getDeliveryStatus();
        if ("UNSIGNED".equals(cur) || (cur != null && !cur.isBlank())) {
            return;
        }
        ExpBill upd = new ExpBill();
        upd.setId(bill.getId());
        upd.setDeliveryStatus("UNSIGNED");
        expBillMapper.updateById(upd);
    }

    /**
     * R5-D-239：姓名取值口径统一——sys_user.nick_name 优先，回退 user_name → fallback。
     * （存量已写入裸工号的历史行由人工只读清单处置，本方法只收口新写入侧）
     */
    private String resolveDisplayName(Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) {
            return fallback;
        }
        if (u.getNickName() != null && !u.getNickName().isBlank()) {
            return u.getNickName();
        }
        if (u.getUserName() != null && !u.getUserName().isBlank()) {
            return u.getUserName();
        }
        return fallback;
    }
}