// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.service.MdAssetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.dto.MdCipTransferRequest;
import com.fssc.masterdata.entity.MdCip;
import com.fssc.masterdata.entity.MdCipCost;
import com.fssc.masterdata.mapper.MdCipCostMapper;
import com.fssc.masterdata.mapper.MdCipMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysCompanyMapper;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 在建工程业务服务：卡片维护（公司必填/名称反查）、成本归集及累计金额回写、
 * 转固定资产（生成 CIP 资产卡 → 复用 FA_CIP_TO_ASSET 审批链）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MdCipService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MdCipMapper cipMapper;
    private final MdCipCostMapper costMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final MdAssetService assetService;

    // 卡片维护

    public Long create(MdCip e) {
        e.setId(null);
        requireManual(e);
        validateCodeUnique(e.getCipCode(), null);
        fillCompany(e);
        if (e.getAccruedAmount() == null) e.setAccruedAmount(ZERO);
        if (e.getTransferredAmount() == null) e.setTransferredAmount(ZERO);
        if (e.getStatus() == null) e.setStatus("NORMAL");
        cipMapper.insert(e);
        return e.getId();
    }

    public void update(MdCip e) {
        if (e.getId() == null) throw new BizException(ErrorCode.PARAM_INVALID, "在建工程ID不能为空");
        requireManual(e);
        MdCip exist = requireCip(e.getId());
        if (!"NORMAL".equals(exist.getStatus()) && !"DISABLED".equals(exist.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转固中/已转固的在建工程不能编辑");
        }
        if (e.getCipCode() != null && !e.getCipCode().equals(exist.getCipCode())) {
            validateCodeUnique(e.getCipCode(), e.getId());
        }
        if (e.getCompanyId() == null) e.setCompanyId(exist.getCompanyId());
        fillCompany(e);
        // 不允许覆盖累计金额（由归集明细维护）；paid_amount 由 CIP 付款凭证回写，编辑同样不可覆盖
        e.setAccruedAmount(null);
        e.setPaidAmount(null);
        e.setTransferredAmount(null);
        cipMapper.updateById(e);
    }

    public List<MdCip> listAll() {
        return cipMapper.selectList(new LambdaQueryWrapper<MdCip>().orderByAsc(MdCip::getCipCode));
    }

    /**
     * 人工建档必填守卫（批次 2B，与前端 CIP_CARD 登记表同一规则）：
     * md_cip.cip_name 为 NOT NULL 且无默认值，留空会直接抛 SQL 异常（500）而非友好提示；
     * cip_code 无自动编号规则（validateCodeUnique 只查重不补号），留空会写出无编码的在建工程。
     */
    private void requireManual(MdCip e) {
        if (e.getCipCode() == null || e.getCipCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入编码");
        }
        if (e.getCipName() == null || e.getCipName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入名称");
        }
    }

    /** 所属公司必填，且按 companyId 反查冗余公司名（防前端篡改） */
    private void fillCompany(MdCip e) {
        if (e.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属公司必选");
        }
        SysCompany c = sysCompanyMapper.selectById(e.getCompanyId());
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所选公司不存在: " + e.getCompanyId());
        }
        e.setCompanyName(c.getCompanyName());
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "在建工程编码必填");
        }
        LambdaQueryWrapper<MdCip> w = new LambdaQueryWrapper<MdCip>().eq(MdCip::getCipCode, code);
        if (excludeId != null) w.ne(MdCip::getId, excludeId);
        if (cipMapper.selectCount(w) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "在建工程编码已存在: " + code);
        }
    }

    private MdCip requireCip(Long id) {
        MdCip cip = cipMapper.selectById(id);
        if (cip == null) throw new BizException(ErrorCode.PARAM_INVALID, "在建工程不存在: " + id);
        return cip;
    }

    // 成本归集

    /**
     * 在建工程成本归集：明细（md_cip_cost）＋工程暂挂金额（md_cip.accrued_amount）两表成对写。
     * 无边界时明细已落而汇总未加＝暂挂金额永久少计，转资按少计额入账（跨表写成对者补；自定，无外部依据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addCost(Long cipId, MdCipCost cost) {
        MdCip cip = requireCip(cipId);
        // 必填守卫：归集类型决定成本规则（领料/设备/人工/待摊/资本化利息），留空无从归类
        if (cost.getCostType() == null || cost.getCostType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择归集类型");
        }
        if (cost.getAmount() == null || cost.getAmount().compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入归集金额（须大于 0）");
        }
        cost.setId(null);
        cost.setCipId(cipId);
        costMapper.insert(cost);
        cip.setAccruedAmount(nvl(cip.getAccruedAmount()).add(cost.getAmount()));
        cipMapper.updateById(cip);
        return cost.getId();
    }

    /** 与 {@link #addCost} 对称：删明细＋回退暂挂金额，半写会让汇总永久多计（同一规则）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCost(Long costId) {
        MdCipCost cost = costMapper.selectById(costId);
        if (cost == null) throw new BizException(ErrorCode.PARAM_INVALID, "归集明细不存在: " + costId);
        MdCip cip = requireCip(cost.getCipId());
        BigDecimal na = nvl(cip.getAccruedAmount()).subtract(nvl(cost.getAmount()));
        cip.setAccruedAmount(na.compareTo(ZERO) < 0 ? ZERO : na);
        costMapper.deleteById(costId);
        cipMapper.updateById(cip);
    }

    public PageResult<MdCipCost> costPage(Long cipId, PageQuery q) {
        Page<MdCipCost> p = costMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()),
                new LambdaQueryWrapper<MdCipCost>().eq(MdCipCost::getCipId, cipId).orderByDesc(MdCipCost::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    // 转固定资产

    /**
     * CIP 采购/付款成本归集：付款批次确认/执行后自动归集到在建工程成本。
     * 仅当批次显式关联在建工程（sourceDocType=CIP 且 sourceDocId=md_cip.id）时写入
     * md_cip_cost（成本类型 EQUIPMENT，描述含批次号去重防重），并回写累计归集金额。
     *
     * <p>技术方案说明：付款明细链路为 批次→付款记录→源单据(exp_bill)，exp_bill 仅有
     * project_id（项目主数据）与 contract_id，与 md_cip（在建工程主数据）无外键关联；
     * 供应商维度 md_cip 亦无 supplier 列。故"按供应商/项目自动匹配在建工程"当前无可靠关联键，
     * 采用显式关联（批次 source_doc_type=CIP）方案；后续如打通 CIP↔项目主数据映射，
     * 可在此方法扩展按 project_id 自动匹配。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void aggregateFromPayBatch(String sourceDocType, Long sourceDocId, String batchNo, BigDecimal amount) {
        try {
            if (sourceDocId == null || amount == null || amount.compareTo(ZERO) <= 0
                    || !"CIP".equalsIgnoreCase(sourceDocType == null ? "" : sourceDocType.trim())) {
                return;
            }
            MdCip cip = cipMapper.selectById(sourceDocId);
            if (cip == null) {
                log.warn("付款批次归集：sourceDocId={} 指向的在建工程不存在，跳过", sourceDocId);
                return;
            }
            String descKey = "付款批次自动归集:" + batchNo;
            Long dup = costMapper.selectCount(new LambdaQueryWrapper<MdCipCost>()
                    .eq(MdCipCost::getCipId, sourceDocId)
                    .like(MdCipCost::getDescription, descKey));
            if (dup != null && dup > 0) {
                log.info("付款批次 {} 的在建工程成本归集已存在，幂等跳过", batchNo);
                return;
            }
            MdCipCost cost = new MdCipCost();
            cost.setCipId(sourceDocId);
            cost.setCostType("EQUIPMENT");
            cost.setAmount(amount);
            cost.setOccurDate(LocalDate.now());
            cost.setDescription(descKey + "（付款批次 " + batchNo + " 自动归集）");
            costMapper.insert(cost);
            MdCip upd = new MdCip();
            upd.setId(cip.getId());
            upd.setAccruedAmount(nvl(cip.getAccruedAmount()).add(amount));
            cipMapper.updateById(upd);
            log.info("付款批次 {} 已自动归集在建工程 {} 成本 {}（累计 {}）",
                    batchNo, cip.getCipCode(), amount, upd.getAccruedAmount());
        } catch (Exception ex) {
            // 归集失败不阻断付款主流程，仅记录错误便于人工补录
            log.error("付款批次 {} 在建工程成本自动归集失败: {}", batchNo, ex.getMessage(), ex);
        }
    }

    /**
     * 采购收货 CIP 成本归集：收货单挂在建工程（po_receipt.cip_id，从 po_order 继承）时，
     * 收货成功后自动写入 md_cip_cost（成本类型 EQUIPMENT，描述含收货单号去重防重），并回写累计归集金额。
     * 与 {@link #aggregateFromPayBatch} 同一套去重/兜底约定：内部异常吞掉仅记日志，不阻断收货主流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public void aggregateFromReceipt(String receiptNo, Long cipId, BigDecimal amount) {
        try {
            if (cipId == null || amount == null || amount.compareTo(ZERO) <= 0) {
                return;
            }
            MdCip cip = cipMapper.selectById(cipId);
            if (cip == null) {
                log.warn("收货归集：在建工程不存在 cipId={}，跳过", cipId);
                return;
            }
            String descKey = "收货自动归集:" + receiptNo;
            Long dup = costMapper.selectCount(new LambdaQueryWrapper<MdCipCost>()
                    .eq(MdCipCost::getCipId, cipId)
                    .like(MdCipCost::getDescription, descKey));
            if (dup != null && dup > 0) {
                log.info("收货单 {} 的在建工程成本归集已存在，幂等跳过", receiptNo);
                return;
            }
            MdCipCost cost = new MdCipCost();
            cost.setCipId(cipId);
            cost.setCostType("EQUIPMENT");
            cost.setAmount(amount);
            cost.setOccurDate(LocalDate.now());
            cost.setDescription(descKey + "（采购收货 " + receiptNo + " 自动归集）");
            costMapper.insert(cost);
            MdCip upd = new MdCip();
            upd.setId(cipId);
            upd.setAccruedAmount(nvl(cip.getAccruedAmount()).add(amount));
            cipMapper.updateById(upd);
            log.info("收货单 {} 已自动归集在建工程 {} 成本 {}（累计 {}）",
                    receiptNo, cip.getCipCode(), amount, upd.getAccruedAmount());
        } catch (Exception ex) {
            // 归集失败不阻断收货主流程，仅记录错误便于人工补录
            log.error("收货单 {} 在建工程成本自动归集失败: {}", receiptNo, ex.getMessage(), ex);
        }
    }

    @Transactional
    public Long transferToAsset(Long cipId, MdCipTransferRequest req) {
        MdCip cip = requireCip(cipId);
        if (!"NORMAL".equals(cip.getStatus()) && !"DISABLED".equals(cip.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前状态(" + cip.getStatus() + ")不可转固");
        }
        if (nvl(cip.getAccruedAmount()).compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "累计归集金额为0，无法转固");
        }
        if (req.getCategoryId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择固定资产类别");
        }
        // 必填守卫：转固日期＝新建卡片的购置日期（折旧起算规则），使用部门＝卡片归属部门；
        // 两者留空就照样建卡并发审批的话，卡片要等到人工编辑才能补齐。
        if (req.getTransferDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择转固日期");
        }
        if (req.getDeptId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择使用部门");
        }
        if (req.getDepreciationMonths() == null || req.getDepreciationMonths() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入折旧月数（须大于 0）");
        }
        // 1. 按累计归集金额生成在建工程(CIP)资产卡片，不触发 FA_ADD
        MdAsset draft = new MdAsset();
        draft.setAssetName(cip.getCipName());
        draft.setCategoryId(req.getCategoryId());
        draft.setDeptId(req.getDeptId());
        draft.setCompanyId(cip.getCompanyId());
        draft.setPurchaseDate(req.getTransferDate());
        draft.setOriginalAmount(cip.getAccruedAmount());
        draft.setResidualValue(ZERO);
        draft.setDepreciationMethod(req.getDepreciationMethod());
        draft.setDepreciationMonths(req.getDepreciationMonths());
        draft.setTaxLifeMonths(req.getTaxLifeMonths());
        Long assetId = assetService.buildForCip(draft);
        // 2. 回写在建工程：状态置转固中、已转固金额、转固日期、资产回链
        cip.setStatus("TRANSFERRING");
        cip.setAssetId(assetId);
        cip.setTransferDate(req.getTransferDate());
        cip.setTransferredAmount(cip.getAccruedAmount());
        cipMapper.updateById(cip);
        // 3. 复用现有转固审批链 FA_CIP_TO_ASSET（内部置 USING 并发起审批）
        assetService.cipToAsset(assetId,
                req.getTransferDate() == null ? null : req.getTransferDate().toString(), req.getRemark());
        return assetId;
    }

    /** 按资产卡回链更新的在建工程（转固审批收尾用） */
    public void markTransferred(Long assetId) {
        MdCip cip = cipMapper.selectOne(new LambdaQueryWrapper<MdCip>().eq(MdCip::getAssetId, assetId).last("LIMIT 1"));
        if (cip == null) return;
        MdCip upd = new MdCip();
        upd.setId(cip.getId());
        upd.setStatus("TRANSFERRED");
        cipMapper.updateById(upd);
    }

    /** 转固审批驳回：在建工程回到可重新发起状态（保留已生成资产卡由人工处理） */
    public void markTransferredRejected(Long assetId) {
        MdCip cip = cipMapper.selectOne(new LambdaQueryWrapper<MdCip>().eq(MdCip::getAssetId, assetId).last("LIMIT 1"));
        if (cip == null) return;
        MdCip upd = new MdCip();
        upd.setId(cip.getId());
        upd.setStatus("NORMAL");
        cipMapper.updateById(upd);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}