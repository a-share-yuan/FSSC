// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.asset.entity.MdInventory;
import com.fssc.asset.entity.StockMove;
import com.fssc.asset.mapper.MdInventoryMapper;
import com.fssc.asset.mapper.StockMoveMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 存货收发存服务：入库（移动平均）、出库（先进先出规则）、结存查询（FA 投资到资产-存货）。
 * <p>计价方式 priceMethod=MA（移动平均）。入库按"结存金额 + 入库数量×单价"累计；
 * 出库发出成本按移动平均单位成本（结存金额/结存数量）计算并核减结存——
 * 本模型仅保存聚合结存（无批次明细），故发出成本基于移动平均单价推导。</p>
 * <p>P2 接线（F2）：领用出库/报废/处置/内部销售/盘点差异/跌价准备 6 条存货流程均由此出单
 * 调 {@code WfEngineFacade.start()} 发起对应审批（INV_ISSUE/DISPOSE/DISPOSAL/INTERNAL_SALE/
 * PHYSICAL_CHECK/DEVAL）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdInventoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MdInventoryMapper mapper;
    private final StockMoveMapper stockMoveMapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;

    public PageResult<MdInventory> page(long pn, long ps, String keyword) {
        LambdaQueryWrapper<MdInventory> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdInventory::getItemCode, keyword)
                    .or().like(MdInventory::getItemName, keyword));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(MdInventory::getCompanyId, scopeC);
        }
        qw.orderByAsc(MdInventory::getItemCode);
        Page<MdInventory> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /** R5-D-189：出入库流水读点（stock_move 此前只写不读）——按物料编码/仓库倒序分页。 */
    public PageResult<StockMove> movesPage(long pn, long ps, String itemCode, Long warehouseId) {
        LambdaQueryWrapper<StockMove> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(itemCode)) {
            qw.eq(StockMove::getItemCode, itemCode);
        }
        if (warehouseId != null) {
            qw.eq(StockMove::getWarehouseId, warehouseId);
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(StockMove::getCompanyId, scopeC);
        }
        qw.orderByDesc(StockMove::getId);
        Page<StockMove> p = stockMoveMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /** 移动平均入库：更新结存数量=原结存+数量，结存金额=原结存金额+数量×单价。 */
    public void inStock(MdInventory key, BigDecimal qty, BigDecimal price) {
        requireManual(key);
        orgGuard.assertCompanyAllowed(key.getCompanyId());
        requirePositive(qty, "入库数量");
        if (price == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入入库单价（不能为负）");
        }
        if (price.compareTo(ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库单价不能为负");
        }
        BigDecimal amount = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        MdInventory row = findByKey(key);
        if (row == null) {
            MdInventory n = new MdInventory();
            n.setItemCode(key.getItemCode());
            n.setItemName(key.getItemName());
            n.setSpec(key.getSpec());
            n.setUnit(key.getUnit());
            n.setWarehouseId(key.getWarehouseId());
            n.setPriceMethod("MA");
            // P0 结存=期初+入−出 恒等式：新建行期初为 0，首笔入库计入 inNum 而非期初，
            // 避免把首批既算期初又算入、造成结存翻倍不齐（此前首库 50 报 期初+50-0=100 但结存=50）。
            n.setOpeningNum(ZERO);
            n.setOpeningAmount(ZERO);
            n.setInNum(qty);
            n.setInAmount(amount);
            n.setRemainingNum(qty);
            n.setRemainingAmount(amount);
            // 多分子公司审批路由 V1.3.17：仅新建存货行补公司（key 已带则沿用），已存在行不覆盖
            if (n.getCompanyId() == null) {
                Long cid = key.getCompanyId();
                if (cid == null) {
                    com.fssc.framework.security.LoginUser lu = UserContext.get();
                    if (lu != null) cid = lu.getCompanyId();
                }
                n.setCompanyId(cid);
            }
            n.setCreateBy(userId());
            mapper.insert(n);
            recordMove("RECEIVE", n, qty, price, amount, null, null);
            assertLedger(n);
            return;
        }
        row.setInNum(nvl(row.getInNum()).add(qty));
        row.setInAmount(nvl(row.getInAmount()).add(amount));
        row.setRemainingNum(nvl(row.getRemainingNum()).add(qty));
        row.setRemainingAmount(nvl(row.getRemainingAmount()).add(amount));
        mapper.updateById(row);
        recordMove("RECEIVE", row, qty, price, amount, null, null);
        assertLedger(row);
    }

    /** 出库：按移动平均单位成本计算发出成本，核减结存数量与金额。返回发出成本。 */
    public BigDecimal outStock(MdInventory key, BigDecimal qty) {
        requireManual(key);
        requirePositive(qty, "出库数量");
        MdInventory row = requireRow(key);
        if (nvl(row.getRemainingNum()).compareTo(qty) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出库数量超过结存数量: 结存 " + row.getRemainingNum());
        }
        BigDecimal unitCost = row.getRemainingNum().signum() == 0
                ? ZERO
                : nvl(row.getRemainingAmount()).divide(row.getRemainingNum(), 6, RoundingMode.HALF_UP);
        BigDecimal issueCost = unitCost.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        row.setOutNum(nvl(row.getOutNum()).add(qty));
        row.setOutAmount(nvl(row.getOutAmount()).add(issueCost));
        row.setRemainingNum(nvl(row.getRemainingNum()).subtract(qty));
        row.setRemainingAmount(nvl(row.getRemainingAmount()).subtract(issueCost));
        mapper.updateById(row);
        recordMove("ISSUE", row, qty, unitCost, issueCost, null, null);
        assertLedger(row);
        return issueCost;
    }

    /** 结存查询：返回结存数量与结存金额。 */
    public MdInventory balanceQuery(MdInventory key) {
        // 仓库留空时 selectOne 会在该物料的多仓存货里任取一行，报出来的结存根本不是用户想看的仓
        requireManual(key);
        return requireRow(key);
    }

    /** 存货领用出库：执行出库并发起领用出库审批流。projectType=PROJECT 项目内 / NONPROJECT 项目外，默认项目内；
     *  两个 bizType 共用同一条流程（流程清单瘦身 §六 M1，载体 INV_ISSUE_PROJECT），项目内/外仍靠 bizType 区分以便追溯。 */
    public void issue(MdInventory key, BigDecimal qty, String purpose, String projectType) {
        requireText(purpose, "请填写领用用途");
        BigDecimal issueCost = outStock(key, qty);
        MdInventory row = findByKey(key);
        String flowKey = "NONPROJECT".equals(projectType) ? "INV_ISSUE_NONPROJECT" : "INV_ISSUE_PROJECT";
        try {
            wfEngineFacade.start(flowKey, row == null ? null : row.getId(), userId(),
                    "存货领用出库-" + key.getItemName() + (purpose == null ? "" : purpose), issueCost);
        } catch (Exception ex) {
            log.error("存货 {} 发起领用出库审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 兼容旧签名：默认项目内（INV_ISSUE_PROJECT） */
    public void issue(MdInventory key, BigDecimal qty, String purpose) {
        issue(key, qty, purpose, "PROJECT");
    }

    /** 存货报废（INV_DISPOSE，FA11-04）：核减结存并发起报废审批流。qty 为报废数量，按结存移均单价作成本。 */
    public void disposeScrap(MdInventory key, BigDecimal qty) {
        BigDecimal cost = writeOff(key, qty, "报废");
        MdInventory row = findByKey(key);
        try {
            wfEngineFacade.start("INV_DISPOSE", row == null ? null : row.getId(), userId(),
                    "存货报废-" + key.getItemName(), cost);
        } catch (Exception ex) {
            log.error("存货 {} 发起报废审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货处置（INV_DISPOSAL_APPLY，FA11-04 处置申请审批）：处置核销（报废/清理）并发起处置申请审批流。 */
    public void disposal(MdInventory key, BigDecimal qty, String remark) {
        requireText(remark, "请填写处置说明");
        BigDecimal cost = writeOff(key, qty, "处置");
        MdInventory row = findByKey(key);
        try {
            wfEngineFacade.start("INV_DISPOSAL_APPLY", row == null ? null : row.getId(), userId(),
                    "存货处置-" + key.getItemName() + (remark == null ? "" : remark), cost);
        } catch (Exception ex) {
            log.error("存货 {} 发起处置审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货处置-物资状态确认（INV_DISPOSAL_CHECK，FA11-04 第1阶段）：处置前物资状态/可用性审批确认，不改库存。 */
    public void disposalCheck(MdInventory key, BigDecimal qty, String remark) {
        requireManual(key);
        // 此前既不校验数量也不校验说明：一条「确认了什么、确认多少」全空的审批照样发出
        requirePositive(qty, "处置数量");
        requireText(remark, "请填写处置说明");
        MdInventory row = findByKey(key);
        try {
            wfEngineFacade.start("INV_DISPOSAL_CHECK", row == null ? null : row.getId(), userId(),
                    "存货处置-状态确认-" + key.getItemName() + (remark == null ? "" : remark),
                    qty == null ? ZERO : qty);
        } catch (Exception ex) {
            log.error("存货 {} 发起处置状态确认审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货处置-处置执行与账务（INV_DISPOSAL_EXEC，FA11-04 第3阶段）：协议签订/收款开票/结转后发起执行账务审批，不改库存。 */
    public void disposalExec(MdInventory key, BigDecimal qty, String remark) {
        requireManual(key);
        requirePositive(qty, "处置数量");
        requireText(remark, "请填写处置说明");
        MdInventory row = findByKey(key);
        // R1 语义修正：金额分档以「处置金额」判别，金额=|处置数量| × 移均单价（此前误将数量 qty 当金额）
        BigDecimal disposalAmt = nvl(qty).abs().multiply(avgUnitCost(row)).setScale(2, RoundingMode.HALF_UP);
        try {
            wfEngineFacade.start("INV_DISPOSAL_EXEC", row == null ? null : row.getId(), userId(),
                    "存货处置-执行账务-" + key.getItemName() + (remark == null ? "" : remark),
                    disposalAmt);
        } catch (Exception ex) {
            log.error("存货 {} 发起处置执行账务审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货内部销售（INV_INTERNAL_SALE，FA11-03）：关联公司间销售，寄售仓库结转前的销售出库审批。 */
    public void internalSale(MdInventory key, BigDecimal qty, Long targetWarehouseId) {
        BigDecimal cost = outStock(key, qty);
        MdInventory row = findByKey(key);
        try {
            wfEngineFacade.start("INV_INTERNAL_SALE", row == null ? null : row.getId(), userId(),
                    "存货内部销售-" + key.getItemName(), cost);
        } catch (Exception ex) {
            log.error("存货 {} 发起内部销售审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货盘点差异（INV_PHYSICAL_CHECK，FA11-05）：盘点数量与账存数量差异走盘点差异审批流。 */
    public void physicalCheck(MdInventory key, BigDecimal checkedQty) {
        requireManual(key);
        MdInventory row = requireRow(key);
        BigDecimal diffNum = (checkedQty == null ? ZERO : checkedQty)
                .subtract(nvl(row.getRemainingNum()));
        // R1 语义修正：金额分档以「盘点差异金额」判别，金额=|差异数量| × 移均单价（此前误将数量 diffNum 当金额）
        BigDecimal diffAmt = diffNum.abs().multiply(avgUnitCost(row)).setScale(2, RoundingMode.HALF_UP);
        try {
            wfEngineFacade.start("INV_PHYSICAL_CHECK", row.getId(), userId(),
                    "存货盘点差异-" + key.getItemName(), diffAmt);
        } catch (Exception ex) {
            log.error("存货 {} 发起盘点差异审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 存货跌价准备（INV_DEVAL，FA11-06）：计提跌价准备，发起跌价准备审批流。 */
    public void devalue(MdInventory key, BigDecimal provisionAmount) {
        requireManual(key);
        requirePositive(provisionAmount, "跌价准备金额");
        MdInventory row = requireRow(key);
        try {
            wfEngineFacade.start("INV_DEVAL", row.getId(), userId(),
                    "存货跌价准备-" + key.getItemName(), provisionAmount);
        } catch (Exception ex) {
            log.error("存货 {} 发起跌价准备审批失败: {}", key.getItemCode(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 核销结存辅助：按移均单价计算 qty 对应的成本并核减（报废/处置共用）。 */
    private BigDecimal writeOff(MdInventory key, BigDecimal qty, String action) {
        requireManual(key);
        requirePositive(qty, action + "数量");
        MdInventory row = requireRow(key);
        if (nvl(row.getRemainingNum()).compareTo(qty) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "核销数量超过结存数量: 结存 " + row.getRemainingNum());
        }
        BigDecimal unitCost = row.getRemainingNum().signum() == 0
                ? ZERO
                : nvl(row.getRemainingAmount()).divide(row.getRemainingNum(), 6, RoundingMode.HALF_UP);
        BigDecimal cost = unitCost.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        row.setOutNum(nvl(row.getOutNum()).add(qty));
        row.setOutAmount(nvl(row.getOutAmount()).add(cost));
        row.setRemainingNum(nvl(row.getRemainingNum()).subtract(qty));
        row.setRemainingAmount(nvl(row.getRemainingAmount()).subtract(cost));
        mapper.updateById(row);
        recordMove("SCRAP".equals(action) ? "SCRAP" : "DISPOSAL", row, qty, unitCost, cost, null, null);
        assertLedger(row);
        return cost;
    }

    /** R1 移均单价：结存金额/结存数量，结存为0时返回0。与 writeOff 取价规则一致，供盘点差异/处置执行按金额分档折算。 */
    private BigDecimal avgUnitCost(MdInventory row) {
        if (row == null) return ZERO;
        return row.getRemainingNum() != null && row.getRemainingNum().signum() != 0
                ? nvl(row.getRemainingAmount()).divide(row.getRemainingNum(), 6, RoundingMode.HALF_UP)
                : ZERO;
    }

    /**
     * P0 冗余断言：结存 = 期初 + 入 − 出（数量精确、金额容差 0.02）。
     * 聚合字段由出入库累计维护，此处显式校验一致性，防脏写/被绕过。
     */
    private void assertLedger(MdInventory r) {
        BigDecimal openingNum = nvl(r.getOpeningNum());
        BigDecimal inNum = nvl(r.getInNum());
        BigDecimal outNum = nvl(r.getOutNum());
        BigDecimal remainingNum = nvl(r.getRemainingNum());
        BigDecimal expectedNum = openingNum.add(inNum).subtract(outNum);
        if (remainingNum.compareTo(expectedNum) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "存货结存数量不一致: 期初+" + inNum + "-" + outNum + "=" + expectedNum
                            + " 但结存=" + remainingNum + "（itemCode=" + r.getItemCode() + "）");
        }
        BigDecimal openingAmt = nvl(r.getOpeningAmount());
        BigDecimal inAmt = nvl(r.getInAmount());
        BigDecimal outAmt = nvl(r.getOutAmount());
        BigDecimal remainingAmt = nvl(r.getRemainingAmount());
        BigDecimal expectedAmt = openingAmt.add(inAmt).subtract(outAmt);
        if (remainingAmt.subtract(expectedAmt).abs().compareTo(new BigDecimal("0.02")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "存货结存金额不一致: 期初+" + inAmt + "-" + outAmt + "=" + expectedAmt
                            + " 但结存=" + remainingAmt + "（itemCode=" + r.getItemCode() + "）");
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private MdInventory findByKey(MdInventory key) {
        return mapper.selectOne(buildKeyWrapper(key));
    }

    private MdInventory requireRow(MdInventory key) {
        MdInventory row = findByKey(key);
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存货不存在: itemCode=" + key.getItemCode() + ", warehouseId=" + key.getWarehouseId());
        }
        return row;
    }

    private LambdaQueryWrapper<MdInventory> buildKeyWrapper(MdInventory key) {
        if (!StringUtils.hasText(key.getItemCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "物料编码必填");
        }
        return new LambdaQueryWrapper<MdInventory>()
                .eq(MdInventory::getItemCode, key.getItemCode())
                .eq(key.getWarehouseId() != null, MdInventory::getWarehouseId, key.getWarehouseId())
                .last("limit 1");
    }

    private void requirePositive(BigDecimal v, String name) {
        if (v == null || v.compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, name + "必须大于0");
        }
    }

    /**
     * 存货操作必填守卫（必填项治理批次 2B）：文案与前端 INVENTORY_OP 规则一致。
     *
     * <p>仓库之所以必输：{@link #buildKeyWrapper} 对 warehouseId 用的是条件 eq，留空时
     * 「itemCode + limit 1」会命中该物料在**任意仓库**的一行存货并据此核减——发错仓、扣错账。
     * 计价方式（priceMethod）前端已必输，但后端新建存货行目前固定按移动平均（MA）写入数据库、
     * 未消费该字段，故此处不做后端断言（已登记为挂账缺陷，避免造出"校验了却没用"的假）。
     */
    private void requireManual(MdInventory key) {
        if (key == null || !StringUtils.hasText(key.getItemCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入物料编码");
        }
        if (!StringUtils.hasText(key.getItemName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入物料名称");
        }
        if (key.getWarehouseId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择仓库");
        }
    }

    private void requireText(String v, String msg) {
        if (!StringUtils.hasText(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID, msg);
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    /**
     * P1-2: 写入 stock_move 交易记录。
     * 每笔出入库/报废/处置均落盘一笔移动单，替代此前"流程跑在空模型上"。
     */
    private void recordMove(String moveType, MdInventory row, BigDecimal qty,
                            BigDecimal unitCost, BigDecimal amount,
                            String sourceDocType, Long sourceDocId) {
        try {
            StockMove m = new StockMove();
            m.setMoveNo("SM" + System.currentTimeMillis());
            m.setMoveType(moveType);
            m.setItemCode(row.getItemCode());
            m.setItemName(row.getItemName());
            m.setWarehouseId(row.getWarehouseId());
            m.setCompanyId(row.getCompanyId());
            m.setQty(qty);
            m.setUnitCost(unitCost);
            m.setAmount(amount);
            m.setSourceDocType(sourceDocType != null ? sourceDocType : "INVENTORY");
            m.setSourceDocId(sourceDocId != null ? sourceDocId : row.getId());
            m.setStatus("APPROVED");
            m.setCreateBy(userId());
            stockMoveMapper.insert(m);
            // P4-3 规则：移动记录为本方法新建写入数据库、无旧值，不接入审计（不伪造 before）
        } catch (Exception ex) {
            log.error("写入存货移动记录失败(moveType={}, item={}): {}", moveType, row.getItemCode(), ex.getMessage(), ex);
        }
    }
}