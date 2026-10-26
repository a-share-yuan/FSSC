// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.mapper.MdAssetMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 资产域流程结束收尾（P0-1 事件编排总线策略实现）。
 *
 * <p>V1.3.17：修复跨法人调拨公司时序缺陷——
 * {@code MdAssetService.transfer} 跨法人调拨时不再先改资产归属公司（否则公司维度校验会把调出公司审批人全部拦截），
 * 目标公司经流程变量 {@code targetCompanyId} 传递；本监听器在审批通过后读取并更新资产 {@code company_id}。
 *
 * <ul>
 *  <li>FA_TRANSFER_CROSS 通过 → 资产归属公司更新为目标公司（此时审批链已走完，公司维度不再影响后续）</li>
 *  <li>FA_TRANSFER_CROSS 驳回 → 资产公司未动；部门已改需人工处理（记录日志提示）</li>
 *  <li>合规整改( )：FA_ADD / FA_CIP_TO_ASSET / FA_DISPOSE 审批通过 → 生成资产凭证落 op_voucher
 *      （借固定资产/贷银行存款或在建工程；处置借累计折旧+处置损益贷固定资产），去重防重、科目缺失记 WARN 跳过不阻断审批</li>
 * </ul>
 * <p>
 * 原 @EventListener 已移除，由 {@link com.fssc.workflow.event.EventOrchestrationBus} 统一分发。
 */
@Slf4j
@Component("assetBizAction")
@RequiredArgsConstructor
public class AssetWfEventListener implements WfBizAction {

    private final MdAssetMapper assetMapper;
    private final HistoryService historyService;
    private final AssetDeprBatchService deprBatchService;
    private final com.fssc.masterdata.service.MdCipService cipService;
    private final VoucherService voucherService;
    /** FA_CER 资本性支出申请收尾（预算占用/释放） */
    private final com.fssc.asset.mapper.FaCerRequestMapper cerRequestMapper;
    private final com.fssc.budget.service.BudgetService budgetService;
    private final com.fssc.budget.mapper.BgOccupyMapper bgOccupyMapper;
    private final com.fssc.workflow.service.WfQueryService wfQueryService;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** X-06（D-DEC-431）：变动台账最终状态回写（发起侧 MdAssetService 插行，本类只把 APPROVING 改成最终状态） */
    private final com.fssc.asset.mapper.MdAssetChangeLogMapper changeLogMapper;

    @Override
    public Set<String> bizTypes() {
        // D-DEC-319（批次0 P-4）：新增 FA_TRANSFER / FA_VALUE_ADJUST / FA_IMPAIRMENT——
        // 这三条此前"先改账再起流、驳回不回滚"（MdAssetService 零 @Transactional），
        // 现发起侧只传目标值入流程变量，改账统一挪到本监听器 APPROVED 环节（驳回侧资产零改动）。
        // D-DEC-322（批次2）：FA_ALIENATE 并档进 FA_DISPOSE 载体（B 案 T-FA-3），事件 bizType 不变，
        // 对外销售通过侧复用处置凭证环节（§1.4 无环节 #3 消除）。
        // D-DEC-322（批次2）：+FA_CHECK_DIFF 盘点差异作业确认闭环记录（结转凭证走 GL_CHECK_DIFF 做账档，账务分离）
        return Set.of("FA_DEPRECIATION", "FA_TRANSFER_CROSS", "FA_TRANSFER", "FA_VALUE_ADJUST", "FA_IMPAIRMENT",
                "FA_ADD", "FA_CIP_TO_ASSET", "FA_DISPOSE", "FA_ALIENATE", "FA_CER", "FA_CHECK_DIFF");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        String bizType = event.getBizType();
        if ("FA_DEPRECIATION".equals(bizType)) {
            handleDepreciationEnded(event);
            return;
        }
        if ("FA_CER".equals(bizType)) {
            handleFaCerEnded(event);
            return;
        }
        if ("FA_ADD".equals(bizType)) {
            handleAssetAddEnded(event);
            return;
        }
        if ("FA_CIP_TO_ASSET".equals(bizType)) {
            handleCipToAssetEnded(event);
            return;
        }
        if ("FA_DISPOSE".equals(bizType) || "FA_ALIENATE".equals(bizType)) {
            handleDisposeEnded(event);
            return;
        }
        if ("FA_CHECK_DIFF".equals(bizType)) {
            handleCheckDiffEnded(event);
            return;
        }
        if ("FA_TRANSFER".equals(bizType)) {
            handleTransferEnded(event);
            return;
        }
        if ("FA_VALUE_ADJUST".equals(bizType)) {
            handleValueAdjustEnded(event);
            return;
        }
        if ("FA_IMPAIRMENT".equals(bizType)) {
            handleImpairmentEnded(event);
            return;
        }
        if (!"FA_TRANSFER_CROSS".equals(bizType)) {
            return;
        }
        Long assetId = event.getBizId();
        MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("跨法人调拨结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                Long targetCompanyId = readTargetCompany(event.getProcInstId());
                if (targetCompanyId == null) {
                    log.warn("跨法人调拨审批通过但未读到目标公司变量，资产 {} 归属公司未更新", assetId);
                    return;
                }
                if (targetCompanyId.equals(asset.getCompanyId())) {
                    log.info("跨法人调拨资产 {} 归属公司已是目标公司 {}", assetId, targetCompanyId);
                    return;
                }
                MdAsset upd = new MdAsset();
                upd.setId(assetId);
                upd.setCompanyId(targetCompanyId);
                assetMapper.updateById(upd);
                // P4-3 审计扩面：本次实际变化为归属公司（company_id）跨法人转移
                java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                auditBefore.put("companyId", asset.getCompanyId());
                java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                auditAfter.put("companyId", upd.getCompanyId());
                auditTrailService.recordUpdate(event.getBizType(), assetId, asset.getAssetNo(), "md_asset",
                        String.valueOf(assetId), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("跨法人调拨审批通过，资产 {} 归属公司 {} → {}",
                        assetId, asset.getCompanyId(), targetCompanyId);
            } else {
                // 驳回：资产归属公司未变（transfer 未写入数据库）；部门可能已调整，提示人工核对
                log.warn("跨法人调拨 {} 审批驳回，资产 {} 归属公司保持 {}（部门如已调整请人工核对）",
                        event.getComment() == null ? "" : event.getComment(), assetId, asset.getCompanyId());
            }
        } catch (Exception ex) {
            log.error("跨法人调拨资产 {} 流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_DEPRECIATION 月度折旧计提审批结束收尾：通过 → 执行实际计提（阶段二）；驳回 → 批单置 REJECTED。
     * 去重由 AssetDeprBatchService.executeDepr 以 lastDeprDate/DONE 状态保障。
     */
    private void handleDepreciationEnded(WfProcessEndedEvent event) {
        Long batchId = event.getBizId();
        if (batchId == null) {
            log.warn("FA_DEPRECIATION 结束事件缺少批单ID");
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                deprBatchService.executeDepr(batchId);
            } else {
                deprBatchService.reject(batchId);
            }
        } catch (Exception ex) {
            log.error("折旧批单 {} 流程收尾失败: {}", batchId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_CER 资本性支出申请审批结束收尾（合规整改 ，联动 DA-01 修复）：
     * 通过 → 按申请金额占用预算科目（去重：已有 OCCUPY 流水跳过）并置 APPROVED；
     * 驳回 → 置 REJECTED；若此前已发生占用（异常时序/重复事件）则释放，正常时序未占用不动预算（防负占用）。
     */
    private void handleFaCerEnded(WfProcessEndedEvent event) {
        Long cerId = event.getBizId();
        if (cerId == null) {
            log.warn("FA_CER 结束事件缺少申请记录ID");
            return;
        }
        com.fssc.asset.entity.FaCerRequest cer = cerRequestMapper.selectById(cerId);
        if (cer == null) {
            log.warn("FA_CER 结束事件指向不存在的申请记录: {}", cerId);
            return;
        }
        try {
            // DA-01 补充：驳回走 deleteProcessInstance(PROCESS_CANCELLED)，不触发 PROCESS_COMPLETED
            // 的统一状态回写，这里按事件结果补写 wf_instance 最终状态（APPROVED 去重覆盖无害）
            if (event.getProcInstId() != null && !event.getProcInstId().isBlank()) {
                try {
                    wfQueryService.updateInstanceStatus(event.getProcInstId(),
                            Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED",
                            java.time.LocalDateTime.now());
                } catch (Exception ignore) { /* 视图回写失败不影响业务收尾 */ }
            }
            if (Boolean.TRUE.equals(event.getApproved())) {
                if (cer.getBudgetItemId() != null && cer.getAmount() != null
                        && cerOccupyAmount(cerId).compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    budgetService.occupy(cer.getBudgetItemId(), cer.getAmount(), "FA_CER", cerId, cer.getRequestNo());
                    log.info("资本性支出申请 {} 审批通过，预算科目 {} 占用 {}",
                            cer.getRequestNo(), cer.getBudgetItemId(), cer.getAmount());
                }
                if (!"APPROVED".equals(cer.getStatus())) {
                    // P4-3 审计扩面：改状态前采集旧值
                    String oldStatus = cer.getStatus();
                    com.fssc.asset.entity.FaCerRequest upd = new com.fssc.asset.entity.FaCerRequest();
                    upd.setId(cerId);
                    upd.setStatus("APPROVED");
                    cerRequestMapper.updateById(upd);
                    java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                    auditBefore.put("status", oldStatus);
                    java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                    auditAfter.put("status", upd.getStatus());
                    auditTrailService.recordUpdate(event.getBizType(), cerId, cer.getRequestNo(), "fa_cer_request",
                            String.valueOf(cerId), auditBefore, auditAfter,
                            "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                }
            } else {
                if (cer.getBudgetItemId() != null && cer.getAmount() != null) {
                    java.math.BigDecimal occupied = cerOccupyAmount(cerId);
                    if (occupied.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        budgetService.release(cer.getBudgetItemId(), occupied, "FA_CER", cerId, cer.getRequestNo());
                        log.info("资本性支出申请 {} 审批驳回，释放预算占用 {}", cer.getRequestNo(), occupied);
                    }
                }
                if (!"REJECTED".equals(cer.getStatus())) {
                    // P4-3 审计扩面：改状态前采集旧值
                    String oldStatus = cer.getStatus();
                    com.fssc.asset.entity.FaCerRequest upd = new com.fssc.asset.entity.FaCerRequest();
                    upd.setId(cerId);
                    upd.setStatus("REJECTED");
                    cerRequestMapper.updateById(upd);
                    java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                    auditBefore.put("status", oldStatus);
                    java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                    auditAfter.put("status", upd.getStatus());
                    auditTrailService.recordUpdate(event.getBizType(), cerId, cer.getRequestNo(), "fa_cer_request",
                            String.valueOf(cerId), auditBefore, auditAfter,
                            "审批驳回" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                }
                log.warn("资本性支出申请 {} 审批驳回（{}）", cer.getRequestNo(),
                        event.getComment() == null ? "" : event.getComment());
            }
        } catch (Exception ex) {
            log.error("资本性支出申请 {} 流程收尾失败: {}", cerId, ex.getMessage(), ex);
        }
    }

    /** 汇总 FA_CER 申请已发生的预算占用(OCCUPY−RELEASE 净额)，用于去重占用与驳回释放 */
    private java.math.BigDecimal cerOccupyAmount(Long cerId) {
        java.math.BigDecimal net = bgOccupyMapper.sumAmountByBiz("FA_CER", cerId);
        return net == null ? java.math.BigDecimal.ZERO : net;
    }

    /**
     * FA_ADD 资产新增审批结束收尾（FA03-D1 修复）：
     * 通过 → 状态 INIT→USING 并生成资产编号 asset_no（FA+yyyyMM+4位序号，当月递增）；
     * 驳回 → 保持 INIT，可修正后重新发起新增。
     * 去重：仅对仍处于 INIT 的资产执行收尾，重复事件不会重复编号。
     */
    private void handleAssetAddEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        if (assetId == null) {
            log.warn("FA_ADD 结束事件缺少资产ID");
            return;
        }
        MdAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("资产新增结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // 合规整改( )：资产新增审批通过 → 生成资产增加凭证（借固定资产1601/贷银行存款1002）。
                // 去重：existingVoucherId 同源判重；失败仅记日志，不阻断状态收尾。
                try {
                    voucherService.generateAssetVoucher(asset, "FA_ADD");
                } catch (Exception vex) {
                    log.error("资产 {} 新增凭证生成失败（不影响状态收尾）: {}", assetId, vex.getMessage(), vex);
                }
                if (!"INIT".equals(asset.getStatus())) {
                    log.info("资产 {} 状态已是 {}，跳过新增收尾（幂等）", assetId, asset.getStatus());
                    return;
                }
                MdAsset upd = new MdAsset();
                upd.setId(assetId);
                upd.setStatus("USING");
                String assetNo = asset.getAssetNo();
                if (assetNo == null || assetNo.isBlank()) {
                    assetNo = nextAssetNo();
                    upd.setAssetNo(assetNo);
                }
                assetMapper.updateById(upd);
                // P4-3 审计扩面：本次实际变化为 status（INIT→USING），资产编号为空时一并记录
                java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                auditBefore.put("status", asset.getStatus());
                java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                if (upd.getAssetNo() != null) {
                    auditBefore.put("assetNo", asset.getAssetNo());
                    auditAfter.put("assetNo", upd.getAssetNo());
                }
                auditTrailService.recordUpdate(event.getBizType(), assetId, assetNo, "md_asset",
                        String.valueOf(assetId), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("资产新增审批通过，资产 {} 状态 INIT→USING，资产编号 {}", assetId, assetNo);
            } else {
                log.warn("资产新增 {} 审批驳回（{}），资产保持 INIT，可修正后重新发起",
                        assetId, event.getComment() == null ? "" : event.getComment());
            }
        } catch (Exception ex) {
            log.error("资产 {} 新增流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_CIP_TO_ASSET 在建工程转固审批结束收尾：
     * 通过 → 补资产编号（若空），并在建工程置"已转固"；驳回 → 在建工程回到可重新发起状态。
     * 去重：仅当资产编号为空才补号，md_cip 更新以 asset_id 回链定位。
     */
    private void handleCipToAssetEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // 合规整改( )：转固审批通过 → 生成转固凭证（借固定资产1601/贷在建工程1604）
                if (asset != null) {
                    try {
                        voucherService.generateAssetVoucher(asset, "FA_CIP_TO_ASSET");
                    } catch (Exception vex) {
                        log.error("资产 {} 转固凭证生成失败（不影响状态收尾）: {}", assetId, vex.getMessage(), vex);
                    }
                }
                if (asset != null && (asset.getAssetNo() == null || asset.getAssetNo().isBlank())) {
                    MdAsset upd = new MdAsset();
                    upd.setId(assetId);
                    upd.setAssetNo(nextAssetNo());
                    assetMapper.updateById(upd);
                    // P4-3 审计扩面：本次实际变化为补写资产编号（asset_no）
                    java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                    auditBefore.put("assetNo", asset.getAssetNo());
                    java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                    auditAfter.put("assetNo", upd.getAssetNo());
                    auditTrailService.recordUpdate(event.getBizType(), assetId, upd.getAssetNo(), "md_asset",
                            String.valueOf(assetId), auditBefore, auditAfter,
                            "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                }
                cipService.markTransferred(assetId);
                log.info("在建工程转固审批通过，资产 {} 已固化编号", assetId);
            } else {
                cipService.markTransferredRejected(assetId);
                log.warn("在建工程转固审批驳回（{}），在建工程回退待重新发起", event.getComment() == null ? "" : event.getComment());
            }
        } catch (Exception ex) {
            log.error("在建工程转固流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_DISPOSE 资产处置（报废）审批结束收尾（合规整改 + D-DEC-428 甲案改账时点后移）：
     * <p>通过 → 此刻才改账（累计折旧补提到顶＝原值−残值、账面净值冲至残值、状态置 SCRAP），
     * 再生成处置凭证（借 累计折旧+处置净损失 贷 固定资产，金额=资产原值）；
     * 驳回 → <b>卡片零改动</b>，只记录（发起侧已不预写，故无需回冲路径）。
     * <p>原形态＝发起瞬间预写改账（{@code MdAssetService.scrap}）＋驳回无回冲，本类因此出现
     * 「折旧与清理被合并进一笔、借方累计折旧取的是发起时的预写值」；现折旧轨迹与清理时点同源。
     * <p>去重：补提取"绝对值到顶"写法且已在顶时跳过更新；凭证按 existingVoucherId(bizType, assetId)
     * 同源判重，重复事件不会重复生成。
     */
    private void handleDisposeEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        if (assetId == null) {
            log.warn("FA_DISPOSE 结束事件缺少资产ID");
            return;
        }
        MdAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("资产处置结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // D-DEC-428：改账时点＝审批通过此刻（原值−残值为累计折旧上限，只补提不回退）
                java.math.BigDecimal original = asset.getOriginalAmount() == null
                        ? java.math.BigDecimal.ZERO : asset.getOriginalAmount();
                java.math.BigDecimal residual = asset.getResidualValue() == null
                        ? java.math.BigDecimal.ZERO : asset.getResidualValue();
                java.math.BigDecimal accum = asset.getAccumDepr() == null
                        ? java.math.BigDecimal.ZERO : asset.getAccumDepr();
                java.math.BigDecimal base = original.subtract(residual);
                if (!"SCRAP".equals(asset.getStatus()) || accum.compareTo(base) < 0) {
                    java.math.BigDecimal newAccum = accum.compareTo(base) >= 0 ? accum : base;
                    java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
                    before.put("status", asset.getStatus());
                    before.put("accumDepr", asset.getAccumDepr());
                    before.put("netAmount", asset.getNetAmount());
                    MdAsset upd = new MdAsset();
                    upd.setId(assetId);
                    upd.setAccumDepr(newAccum);
                    upd.setNetAmount(original.subtract(newAccum));
                    upd.setStatus("SCRAP");
                    assetMapper.updateById(upd);
                    java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
                    after.put("status", "SCRAP");
                    after.put("accumDepr", newAccum);
                    after.put("netAmount", original.subtract(newAccum));
                    auditTrailService.recordUpdate(event.getBizType(), assetId, asset.getAssetNo(), "md_asset",
                            String.valueOf(assetId), before, after,
                            "处置审批通过，累计折旧补提至残值并转为报废（SCRAP）"
                                    + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                    // 凭证按改账后的规则出（借方累计折旧＝本次真实补提后的累计额）
                    asset.setStatus("SCRAP");
                    asset.setAccumDepr(newAccum);
                    asset.setNetAmount(original.subtract(newAccum));
                }
                try {
                    voucherService.generateAssetVoucher(asset, "FA_DISPOSE");
                } catch (Exception vex) {
                    log.error("资产 {} 处置凭证生成失败（不影响审批收尾）: {}", assetId, vex.getMessage(), vex);
                }
                log.info("资产处置审批通过，资产 {} 已改账为 SCRAP（累计折旧 {}／净值 {}），处置凭证已生成/按幂等跳过",
                        assetId, asset.getAccumDepr(), asset.getNetAmount());
            } else {
                log.info("资产处置 {} 审批驳回（{}），卡片保持 {}：发起侧零改账，无需回冲（D-DEC-428）",
                        assetId, event.getComment() == null ? "" : event.getComment(), asset.getStatus());
            }
        } catch (Exception ex) {
            log.error("资产 {} 处置流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_TRANSFER 部门间（同公司）调拨收尾（D-DEC-319，批次0 P-4 / A1）：
     * 发起侧（MdAssetService.transfer）不再预写 dept/company，目标值经流程变量传递；
     * 通过 → 落 targetDeptId（targetCompanyId 非空且不同时一并落，防御性）；驳回 → 资产零改动，只记录。
     * 去重：目标值与现值一致时跳过更新（重复事件无害）。
     */
    private void handleTransferEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("部门间调拨结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("部门间调拨 {} 审批驳回，资产 {} 部门/公司保持不变（发起侧不再预写，无需回滚）",
                        assetId, event.getComment() == null ? "" : event.getComment());
                return;
            }
            Long targetDeptId = readLongVar(event.getProcInstId(), "targetDeptId");
            Long targetCompanyId = readLongVar(event.getProcInstId(), "targetCompanyId");
            if (targetDeptId == null) {
                log.warn("部门间调拨审批通过但未读到目标部门变量，资产 {} 部门未更新", assetId);
                return;
            }
            java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
            before.put("deptId", asset.getDeptId());
            MdAsset upd = new MdAsset();
            upd.setId(assetId);
            upd.setDeptId(targetDeptId);
            java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
            after.put("deptId", upd.getDeptId());
            if (targetCompanyId != null && !targetCompanyId.equals(asset.getCompanyId())) {
                upd.setCompanyId(targetCompanyId);
                before.put("companyId", asset.getCompanyId());
                after.put("companyId", targetCompanyId);
            }
            boolean same = targetDeptId.equals(asset.getDeptId())
                    && (upd.getCompanyId() == null || upd.getCompanyId().equals(asset.getCompanyId()));
            if (same) {
                log.info("部门间调拨资产 {} 目标值与现值一致，跳过（幂等）", assetId);
                return;
            }
            assetMapper.updateById(upd);
            auditTrailService.recordUpdate(event.getBizType(), assetId, asset.getAssetNo(), "md_asset",
                    String.valueOf(assetId), before, after,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("部门间调拨审批通过，资产 {} 部门 {} → {}{}", assetId, asset.getDeptId(), targetDeptId,
                    upd.getCompanyId() != null ? "，公司 → " + upd.getCompanyId() : "");
        } catch (Exception ex) {
            log.error("部门间调拨资产 {} 流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_VALUE_ADJUST 原值调整收尾（D-DEC-319，批次0 P-4 / A1）：
     * 通过 → 按流程变量落 originalAmount/netAmount；驳回 → 资产零改动。
     */
    private void handleValueAdjustEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        // X-06（D-DEC-431）：台账最终状态回写放在资产判空之前——卡片缺失也不该让台账行永远停在 APPROVING
        markChangeLogTerminated(event);
        MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("原值调整结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("原值调整 {} 审批驳回，资产 {} 原值/净值保持不变（发起侧不再预写，无需回滚）",
                        assetId, event.getComment() == null ? "" : event.getComment());
                return;
            }
            java.math.BigDecimal newOriginal = readDecimalVar(event.getProcInstId(), "newOriginalAmount");
            java.math.BigDecimal newNet = readDecimalVar(event.getProcInstId(), "newNetAmount");
            if (newOriginal == null) {
                log.warn("原值调整审批通过但未读到新原值变量，资产 {} 未更新", assetId);
                return;
            }
            java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
            before.put("originalAmount", asset.getOriginalAmount());
            before.put("netAmount", asset.getNetAmount());
            MdAsset upd = new MdAsset();
            upd.setId(assetId);
            upd.setOriginalAmount(newOriginal);
            if (newNet != null) {
                upd.setNetAmount(newNet);
            }
            assetMapper.updateById(upd);
            java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
            after.put("originalAmount", upd.getOriginalAmount());
            after.put("netAmount", upd.getNetAmount());
            auditTrailService.recordUpdate(event.getBizType(), assetId, asset.getAssetNo(), "md_asset",
                    String.valueOf(assetId), before, after,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("原值调整审批通过，资产 {} 原值 {} → {}，净值 → {}", assetId,
                    asset.getOriginalAmount(), newOriginal, upd.getNetAmount());
        } catch (Exception ex) {
            log.error("原值调整资产 {} 流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_IMPAIRMENT 减值准备收尾（D-DEC-319，批次0 P-4 / A1）：
     * 通过 → 按流程变量落 netAmount；驳回 → 资产零改动。
     */
    private void handleImpairmentEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("减值准备结束事件指向不存在的资产: {}", assetId);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("减值准备 {} 审批驳回，资产 {} 净值保持不变（发起侧不再预写，无需回滚）",
                        assetId, event.getComment() == null ? "" : event.getComment());
                return;
            }
            java.math.BigDecimal newNet = readDecimalVar(event.getProcInstId(), "newNetAmount");
            if (newNet == null) {
                log.warn("减值准备审批通过但未读到新净值变量，资产 {} 未更新", assetId);
                return;
            }
            java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
            before.put("netAmount", asset.getNetAmount());
            MdAsset upd = new MdAsset();
            upd.setId(assetId);
            upd.setNetAmount(newNet);
            assetMapper.updateById(upd);
            java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
            after.put("netAmount", newNet);
            auditTrailService.recordUpdate(event.getBizType(), assetId, asset.getAssetNo(), "md_asset",
                    String.valueOf(assetId), before, after,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("减值准备审批通过，资产 {} 净值 {} → {}", assetId, asset.getNetAmount(), newNet);
        } catch (Exception ex) {
            log.error("减值准备资产 {} 流程收尾失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * FA_CHECK_DIFF 盘点差异作业确认闭环（D-DEC-322，批次2 B-12）：
     * 作业侧只记录不记科目——差异结转凭证由总账凭证载体（GL_CHECK_DIFF 盘点结转档）依记账单生成，
     * 作业与账务分离（P-2 规则），此处保证审批最终状态有记录可溯。
     */
    private void handleCheckDiffEnded(WfProcessEndedEvent event) {
        Long assetId = event.getBizId();
        // X-06（D-DEC-431）：差异说明的载体＝变动台账行（发起侧 MdAssetService#checkDiff 插入），此处回写最终状态
        markChangeLogTerminated(event);
        try {
            MdAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
            java.util.Map<String, Object> same = new java.util.LinkedHashMap<>();
            same.put("assetNo", asset == null ? null : asset.getAssetNo());
            auditTrailService.recordUpdate(event.getBizType(), assetId,
                    asset == null ? String.valueOf(assetId) : asset.getAssetNo(), "md_asset",
                    String.valueOf(assetId), same, same,
                    (Boolean.TRUE.equals(event.getApproved()) ? "盘点差异作业确认通过，差异结转凭证走总账凭证（GL_CHECK_DIFF）"
                            : "盘点差异作业确认驳回") + (event.getComment() == null || event.getComment().isBlank()
                            ? "" : "：" + event.getComment()));
        } catch (Exception ex) {
            log.error("盘点差异作业 {} 留痕失败: {}", assetId, ex.getMessage(), ex);
        }
    }

    /**
     * X-06（D-DEC-431）：按 proc_inst_id 把变动台账从 APPROVING 回写为最终状态（通过 APPROVED／驳回 REJECTED）。
     *
     * <p>只回写状态，不改卡片——改账环节各自保持原规则（D-DEC-319 原值调整、D-DEC-322 盘点差异作业与账务分离），
     * 本方法解决的是"这次变动最后成没成"在库里没有答案。
     * <p>键＝proc_inst_id（发起侧起流后拿到的返回值），不用 asset_id 猜在途行；
     * 去重＝只更新 status 仍为 APPROVING 的行，重复事件第二次 0 行命中。
     * <p>{@code update_by} 是裸字段（无 fill 注解）⇒ 本回写不填更新人：审批事件线程里拿不到可靠操作人，
     * 填 0L 哨兵会把"系统回写"谎报成"某个用户改的"，留 NULL 更诚实；{@code update_time} 由注解回填＝回写时刻。
     */
    private void markChangeLogTerminated(WfProcessEndedEvent event) {
        String procInstId = event.getProcInstId();
        if (procInstId == null || procInstId.isBlank()) {
            log.warn("变动台账终态回写缺少流程实例号，资产 {} 的台账行保持在途", event.getBizId());
            return;
        }
        try {
            com.fssc.asset.entity.MdAssetChangeLog upd = new com.fssc.asset.entity.MdAssetChangeLog();
            upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
            int n = changeLogMapper.update(upd, new LambdaQueryWrapper<com.fssc.asset.entity.MdAssetChangeLog>()
                    .eq(com.fssc.asset.entity.MdAssetChangeLog::getProcInstId, procInstId)
                    .eq(com.fssc.asset.entity.MdAssetChangeLog::getStatus, "APPROVING"));
            if (n == 0) {
                log.info("变动台账按流程号 {} 未命中在途行（可能为改造前发起的存量流程）", procInstId);
            } else {
                log.info("变动台账 {} 行终态回写为 {}", n, upd.getStatus());
            }
        } catch (Exception ex) {
            log.error("变动台账终态回写失败 procInstId={}: {}", procInstId, ex.getMessage(), ex);
        }
    }

    /** 生成资产编号：FA + yyyyMM + 4位序号（取当月最大序号+1） */
    private String nextAssetNo() {
        String prefix = "FA" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        MdAsset last = assetMapper.selectOne(new LambdaQueryWrapper<MdAsset>()
                .likeRight(MdAsset::getAssetNo, prefix)
                .orderByDesc(MdAsset::getAssetNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getAssetNo() != null && last.getAssetNo().length() >= 4) {
            try {
                seq = Integer.parseInt(last.getAssetNo().substring(last.getAssetNo().length() - 4)) + 1;
            } catch (NumberFormatException ignore) {
                // 资产编号尾部非数字（历史数据/人工编号）：无法续号，从 1 重排并记录告警，
                // 便于发现潜在重号风险（uk 冲突时人工介入）
                log.warn("资产编号 {} 尾部非数字，序号重排为 1", last.getAssetNo());
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    /** 从流程历史变量读取 targetCompanyId（流程结束后 ACT_RU_VARIABLE 已清理，读 ACT_HI_VARINST） */
    private Long readTargetCompany(String procInstId) {
        return readLongVar(procInstId, "targetCompanyId");
    }

    /** 从流程历史变量读 Long（D-DEC-319）：值为空/类型不符返回 null，异常只告警不阻断收尾 */
    private Long readLongVar(String procInstId, String name) {
        Object v = readVarRaw(procInstId, name);
        if (v == null) return null;
        if (v instanceof Number num) return num.longValue();
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException ex) {
            log.warn("流程变量 {} 非数值: {}", name, v);
            return null;
        }
    }

    /** 从流程历史变量读 BigDecimal（D-DEC-319）：同上 fail-soft 规则 */
    private java.math.BigDecimal readDecimalVar(String procInstId, String name) {
        Object v = readVarRaw(procInstId, name);
        if (v == null) return null;
        if (v instanceof java.math.BigDecimal bd) return bd;
        if (v instanceof Number num) return java.math.BigDecimal.valueOf(num.doubleValue());
        try {
            return new java.math.BigDecimal(String.valueOf(v));
        } catch (NumberFormatException ex) {
            log.warn("流程变量 {} 非数值: {}", name, v);
            return null;
        }
    }

    private Object readVarRaw(String procInstId, String name) {
        if (procInstId == null || procInstId.isBlank()) {
            return null;
        }
        try {
            HistoricVariableInstance v = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(procInstId)
                    .variableName(name)
                    .singleResult();
            return v == null ? null : v.getValue();
        } catch (Exception ex) {
            log.warn("读取流程变量 {} 失败 procInstId={}: {}", name, procInstId, ex.getMessage());
            return null;
        }
    }
}
