// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.asset.entity.AssetDeprBatch;
import com.fssc.asset.entity.FaCerRequest;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.entity.MdAssetChangeLog;
import com.fssc.asset.mapper.FaCerRequestMapper;
import com.fssc.asset.mapper.MdAssetChangeLogMapper;
import com.fssc.asset.mapper.MdAssetMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.budget.entity.BgItem;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产卡片服务：建档 + 报废 + 逐月折旧 + 净值查询（FA 投资到资产-固定资产）。
 * 折旧：月折旧额按月计提并累加至累计折旧，报废时累计折旧一次性提足（账面净值冲至残值）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MdAssetService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal TWO = new BigDecimal("2");

    private final MdAssetMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    private final AssetDeprBatchService deprBatchService;
    /** CER 资本性支出申请记录 + 预算集成 */
    private final FaCerRequestMapper cerRequestMapper;
    private final BudgetService budgetService;
    private final BgItemMapper bgItemMapper;
    private final NumberRuleService numberRuleService;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /**
     * 资产变动台账＝原值调整/盘点差异的原因与前后幅度载体。
     * 卡片 {@code md_asset} 只有末值，历史镜像一律落本表（写入位置见 {@code writeChangeLog}）。
     */
    private final MdAssetChangeLogMapper changeLogMapper;

    public PageResult<MdAsset> page(long pn, long ps, String keyword) {
        LambdaQueryWrapper<MdAsset> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdAsset::getAssetNo, keyword)
                    .or().like(MdAsset::getAssetName, keyword)
                    .or().like(MdAsset::getInvoiceNo, keyword));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(MdAsset::getCompanyId, scopeC);
        }
        qw.orderByDesc(MdAsset::getId);
        Page<MdAsset> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /**
     * 人工建档/改档的必填守卫：文案与前端 ASSET_CARD 规则逐字一致，
     * 由 {@code MdAssetController} 在人工入口调用。
     *
     * <p>刻意不放进 {@link #create} —— 采购收货转固（{@code PurchaseService#createAssetFromReceipt}）
     * 与在建工程转固（{@link #buildForCip}）都以机器身份走同一个 create，它们天然没有类别/使用部门，
     * 守卫写在服务里会把自动化链路一起拦死。资产编号按规则 Q1 属系统编码、人工只读，故不校验。
     */
    public void requireManualCard(MdAsset e) {
        if (e == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写资产卡片信息");
        }
        if (!StringUtils.hasText(e.getAssetName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入资产名称");
        }
        if (e.getCategoryId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择资产类别");
        }
        if (e.getDeptId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择使用部门");
        }
        if (e.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择所属公司");
        }
        orgGuard.assertCompanyAllowed(e.getCompanyId());
        if (e.getPurchaseDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择购置日期");
        }
        if (e.getOriginalAmount() == null || e.getOriginalAmount().compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入原值（须大于 0）");
        }
        if (!StringUtils.hasText(e.getDepreciationMethod())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择折旧方法");
        }
        if (e.getDepreciationMonths() == null || e.getDepreciationMonths() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入折旧月数（须大于 0）");
        }
    }

    /** 资产建档：按类别/方法计算月折旧额，初始累计折旧为 0、净值为原值。 */
    public Long create(MdAsset e) {
        e.setId(null);
        // Q1 规则：资产编号属系统编码，创建时自动生成（缺规则时降级 FA+时间戳，不阻断建档）
        if (!StringUtils.hasText(e.getAssetNo())) {
            try {
                e.setAssetNo(numberRuleService.next("FA_ASSET"));
            } catch (Exception ex) {
                e.setAssetNo("FA" + System.currentTimeMillis());
                log.warn("资产编号生成失败，降级使用时间戳: {}", ex.getMessage());
            }
        }
        if (e.getOriginalAmount() == null) e.setOriginalAmount(ZERO);
        if (e.getResidualValue() == null) e.setResidualValue(ZERO);
        computeDepr(e);
        if (e.getStatus() == null) e.setStatus("INIT");
        // 登记人必须显式赋值：本表既有 FieldFill.INSERT 字段会让 FsscMetaObjectHandler 在 createBy 为 null 时补 0L 而非 NULL，0 是无效用户 id、deny_self 取到它就永不命中
        e.setCreateBy(userId());
        mapper.insert(e);
        // 规则：本方法新建写入数据库、无旧值，不接入审计（不伪造 before）
        // 资产新增即发起 FA03 资产新增审批流（资产管理员→部门经理→资产管理岗→财务经理(>200000)）
        try {
            wfEngineFacade.start("FA_ADD", e.getId(), userId(),
                    "资产-" + (e.getAssetName() == null ? e.getId() : e.getAssetName()), e.getOriginalAmount());
        } catch (Exception ex) {
            log.error("资产 {} 发起新增审批失败: {}", e.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
        return e.getId();
    }

    /**
     * 折旧初始化计算（原值-残值 → 月折旧额）：累计折旧初始 0、净值=原值。
     * 含 #13 残值勾稽、折旧月数校验、#16 税法年限校验。
     */
    private void computeDepr(MdAsset e) {
        if (e.getOriginalAmount().compareTo(ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产原值不能为负");
        }
        if (e.getResidualValue().compareTo(ZERO) < 0 || e.getResidualValue().compareTo(e.getOriginalAmount()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "残值(" + e.getResidualValue() + ")越界：应为 [0, 原值(" + e.getOriginalAmount() + ")]");
        }
        if (e.getDepreciationMonths() == null || e.getDepreciationMonths() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "折旧月数必须大于0");
        }
        // #16 税务规则：税法年限若填写须为正（未填视为与会计年限一致）
        assertTaxLifeMonths(e.getTaxLifeMonths());
        BigDecimal base = e.getOriginalAmount().subtract(e.getResidualValue()); // 应计折旧额（原值-残值）
        BigDecimal factor = "ACCELERATED".equals(e.getDepreciationMethod()) ? TWO : BigDecimal.ONE;
        BigDecimal monthly = base.multiply(factor)
                .divide(BigDecimal.valueOf(e.getDepreciationMonths()), 2, RoundingMode.HALF_UP);
        e.setMonthlyDepr(monthly);
        e.setAccumDepr(ZERO);
        e.setNetAmount(e.getOriginalAmount());
    }

    /**
     * 由在建工程转固建卡：按调用方传入的折旧规则计算初始折旧，落一张 status=CIP 的资产卡片。
     * 不发起 FA_ADD 审批（转固走 FA_CIP_TO_ASSET），由调用方随后触发转固审批链。
     */
    public Long buildForCip(MdAsset e) {
        e.setId(null);
        if (e.getOriginalAmount() == null) e.setOriginalAmount(ZERO);
        if (e.getResidualValue() == null) e.setResidualValue(ZERO);
        computeDepr(e);
        e.setStatus("CIP");
        e.setCreateBy(userId());
        mapper.insert(e);
        // 规则：本方法新建写入数据库、无旧值，不接入审计（不伪造 before）
        return e.getId();
    }

    /**
     * 报废发起：只起流程，<b>零改账</b>。
     * <p>原实现在发起瞬间就预写改账——{@code accum_depr} 直接补提到顶（原值−残值）、
     * {@code net_amount} 冲至残值、{@code status} 置 SCRAP，然后才 {@code start(FA_DISPOSE)}，
     * 而本类无 REJECTED 回冲路径 ⇒ ①任何人驳回也回不来；②"补提到顶"抹掉真实折旧轨迹
     * （本月只该提 4,591.67，累计列却跳到 501,000）；③后续处置凭证的借方累计折旧用的是这次
     * 预写值，折旧与清理两件事被合并进一笔、规则不可追；④卡片"已报废"与流程"在办"并存。
     * <p>现改账整体移到审批通过环节 {@code AssetWfEventListener.handleDisposeEnded}
     * （与 部门间调拨"发起侧不预写、通过侧落值"同法）：驳回即天然零改动，无需回冲。
     * 重复点「报废」会并起第二条 FA_DISPOSE，但通过环节写的是绝对值（补提到顶＝去重）、
     * 处置凭证按 {@code existingVoucherId} 判重，故重放不会产生第二笔账。
     */
    public void scrap(Long id) {
        MdAsset a = require(id);
        if ("SCRAP".equals(a.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该资产已报废");
        }
        // 资产报废即发起 FA06 资产处置审批流（资产管理员→部门经理→资产管理岗→财务总监(>500000)）
        try {
            wfEngineFacade.start("FA_DISPOSE", a.getId(), userId(),
                    "资产处置-" + (a.getAssetName() == null ? a.getId() : a.getAssetName()), a.getOriginalAmount());
        } catch (Exception ex) {
            log.error("资产 {} 发起处置审批失败: {}", a.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 资本性支出申请（FA01 FA_CER）：立项申请即发起资本性支出审批流，不落资产卡片（购建/调拨后建档）。
     * 兼容旧签名（未指定预算科目 → 自动取综合预算兜底科目）。
     */
    public void capitalRequest(BigDecimal amount, String assetName, Long deptId, Long companyId, String remark) {
        capitalRequest(amount, assetName, deptId, companyId, remark, null);
    }

    /**
     * 资本性支出申请（FA01 FA_CER）—— 预算集成改造：
     * <ol>
     *  <li>发起时对指定预算科目做准入判定（乙）：超出只回结论与金额并随申请说明记录，申请照常成立；</li>
     *  <li>落一条 fa_cer_request 简化申请记录（原实现 bizId=null，无业务单据表：审批结束事件收不到 bizId
     *      不发布 → 资产域监听器收不到回写（DA-01），预算也无法占用/核销）；</li>
     *  <li>以 {@code bizId=fa_cer_request.id} 启动 FA_CER 审批流；审批通过后由
     *      AssetWfEventListener 占用预算并置 APPROVED，驳回置 REJECTED（如已占用则释放）。</li>
     * </ol>
     *
     * @param budgetItemId 预算科目（bg_item.id），为空时自动取综合预算兜底科目
     */
    public void capitalRequest(BigDecimal amount, String assetName, Long deptId, Long companyId, String remark,
                               Long budgetItemId) {
        // 必填守卫：文案与前端 ASSET_CAPITAL 规则一致。原先 companyId 为空时静默按
        // 登录用户所属公司补值——申请人看到的「所属公司」是空的，单据却记到了另一家公司，故改为必输。
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入支出金额（须大于 0）");
        }
        if (!StringUtils.hasText(assetName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入资产/项目名称");
        }
        if (deptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择使用部门");
        }
        if (companyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择所属公司");
        }
        orgGuard.assertCompanyAllowed(companyId);
        if (!StringUtils.hasText(remark)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入申请说明");
        }
        Long bid = budgetItemId != null ? budgetItemId : defaultCerBudgetItem();
        // 乙：发起位只出「超出」结论与金额，不再打挂申请（扣减校验在审批通过环节按档位执行）
        BudgetService.OccupyCheckResult ck = budgetService.checkOccupyResult(bid, amount);
        FaCerRequest cer = new FaCerRequest();
        cer.setRequestNo(nextCerNo());
        cer.setAssetName(assetName);
        cer.setAmount(amount);
        cer.setBudgetItemId(bid);
        cer.setDeptId(deptId);
        cer.setCompanyId(companyId);
        // 超出不得静默：结论随申请说明记录（fa_cer_request.remark 既有列，上限 500 超长截尾保新痕）
        cer.setRemark(ck.isOver() ? capRemark(remark + "；【预算超出·可继续】" + ck.getMessage()) : remark);
        cer.setStatus("APPLYING");
        var u = UserContext.get();
        cer.setApplyUserId(u == null ? null : u.getUserId());
        cer.setApplyUserName(u == null ? null : u.getUsername());
        cerRequestMapper.insert(cer);
        // 规则：本方法新建写入数据库、无旧值，不接入审计（不伪造 before）
        String title = "资本性支出-" + (assetName == null || assetName.isBlank() ? cer.getRequestNo() : assetName);
        try {
            String procInst = wfEngineFacade.start("FA_CER", cer.getId(), userId(), title, amount);
            FaCerRequest upd = new FaCerRequest();
            upd.setId(cer.getId());
            upd.setProcInstId(procInst);
            cerRequestMapper.updateById(upd);
        } catch (Exception ex) {
            log.error("资本性支出申请 {} 发起审批失败: {}", cer.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** CER 默认预算科目：取当前租户预算总额最大的科目（综合预算兜底，id=19 场景） */
    private Long defaultCerBudgetItem() {
        List<BgItem> items = bgItemMapper.selectList(
                new LambdaQueryWrapper<BgItem>()
                        .orderByDesc(BgItem::getTotalAmount)
                        .last("LIMIT 1"));
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置可用预算科目，无法做资本性支出预算硬控，请先维护预算方案或显式指定预算科目");
        }
        return items.get(0).getId();
    }

    /** CER 申请取号：规则 FA_CER；缺失时降级 CER+时间戳（不阻断申请） */
    private String nextCerNo() {
        try {
            return numberRuleService.next("FA_CER");
        } catch (Exception ex) {
            return "CER" + System.currentTimeMillis();
        }
    }

    /** fa_cer_request.remark 上限 500，超长截尾（保住后贴的超出记录） */
    private static String capRemark(String v) {
        if (v == null) {
            return null;
        }
        return v.length() <= 500 ? v : v.substring(v.length() - 500);
    }

    /** 变动原因截断：change_reason 上限 500，原因为人工输入的正文，超长保头截尾（与 capRemark 的保尾规则相反） */
    private static String capReason(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.length() <= 500 ? v : v.substring(0, 500);
    }

    /**
     * 单卡变动台账读取环节——原值调整/盘点差异的原因、前后镜像与最终状态。
     * <p>写侧在 {@code writeChangeLog}，本读点是它的消费者，否则该表就成了只写不读的哑台账。
     * <p>规则与 {@code page} 一致：受组织范围约束的账号只能看本公司卡片的变动行，防止跨法人查询。
     */
    public List<MdAssetChangeLog> changeLogs(Long assetId) {
        require(assetId);
        Long scopeC = orgGuard.enforceCompany();
        return changeLogMapper.selectList(new LambdaQueryWrapper<MdAssetChangeLog>()
                .eq(MdAssetChangeLog::getAssetId, assetId)
                .eq(scopeC != null, MdAssetChangeLog::getCompanyId, scopeC)
                .orderByDesc(MdAssetChangeLog::getId));
    }

    /**
     * 资产变动台账写入——原值调整/盘点差异的原因与前后幅度只拼在流程标题里的话，
     * 卡片只留末值，事后无从回查"为什么改、从多少改到多少"。
     *
     * <p>调用时点＝{@code wfEngineFacade.start} 返回之后：start 五个重载都回 procInstId（本域 FA_CER 已有
     * {@code :262} 取用先例），台账行因此天然带流程号，审批最终状态按 proc_inst_id 回写状态即可，
     * 不必按 asset_id 猜哪一条在途。起流失败时方法不会走到这里（上方 catch 已冒泡），不留孤儿台账行。
     * <p>台账写失败只记 ERROR，不把已发起的流程一起退掉——流程在途时回退单据比缺一行台账更伤业务。
     */
    private void writeChangeLog(MdAsset asset, String changeType, String reason, String procInstId,
                                BigDecimal beforeOriginal, BigDecimal afterOriginal,
                                BigDecimal beforeNet, BigDecimal afterNet, BigDecimal delta, String remark) {
        try {
            MdAssetChangeLog row = new MdAssetChangeLog();
            row.setAssetId(asset.getId());
            row.setAssetNo(asset.getAssetNo());
            row.setCompanyId(asset.getCompanyId());
            row.setDeptId(asset.getDeptId());
            row.setChangeType(changeType);
            // 取值域：change_type 记"变了什么"，本列记"怎么变的"——
            // 有流程号＝审批流变动(WF)，无流程号＝不经审批的直接改账(DIRECT)。
            // 现两个写入方（盘点差异/原值调整）都先起流再落台账，⇒值面只会出 WF；DIRECT 臂待直改环节接入。
            row.setChangeMethod(StringUtils.hasText(procInstId) ? "WF" : "DIRECT");
            row.setChangeReason(capReason(reason));
            row.setBeforeOriginalAmount(beforeOriginal);
            row.setAfterOriginalAmount(afterOriginal);
            row.setBeforeNetAmount(beforeNet);
            row.setAfterNetAmount(afterNet);
            row.setDeltaAmount(delta);
            row.setStatus("APPROVING");
            row.setRemark(remark);
            row.setProcInstId(procInstId);
            // create_by 是裸字段（无 fill 注解）⇒ 必须显式赋值，否则落 DB NULL
            row.setCreateBy(userId());
            changeLogMapper.insert(row);
            log.info("资产变动台账 id={} 资产={} 类型={} 原因={} 幅度={} 流程={}",
                    row.getId(), asset.getId(), changeType, row.getChangeReason(), delta, procInstId);
        } catch (Exception ex) {
            log.error("资产 {} {} 变动台账写入失败（流程已发起，该行需人工补记）: {}",
                    asset.getId(), changeType, ex.getMessage(), ex);
        }
    }

    /** 资产调拨（FA05）：部门间 FA_TRANSFER(FA05-01) / 跨法人 FA_TRANSFER_CROSS(FA05-C)。 */
    public void transfer(Long id, Long targetDeptId, Long targetCompanyId, String remark) {
        MdAsset a = require(id);
        // 必填守卫：原先 targetDeptId 为空时只是「什么都不改」，
        // 却照样写入数据库并发起一条调拨审批——审批人看到的是一张目标部门为空的调拨单。
        if (targetDeptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择目标部门");
        }
        boolean cross = targetCompanyId != null && a.getCompanyId() != null
                && !targetCompanyId.equals(a.getCompanyId());
        // 调拨改账挪到审批通过后执行——
        // 此处不预写 dept_id/company_id（先写库再起流：驳回不回滚＝脏账，起流失败不反弹）。
        // 目标值经流程变量传递，assetBizAction 在 APPROVED 时写入数据库；驳回侧资产零改动（R-4 回滚半径=0）。
        Map<String, Object> extra = new HashMap<>();
        extra.put("targetDeptId", targetDeptId);
        extra.put("targetCompanyId", targetCompanyId);
        try {
            if (cross) {
                wfEngineFacade.start("FA_TRANSFER_CROSS", id, userId(),
                        "资产调拨-" + a.getAssetName(), a.getOriginalAmount(), null, extra);
            } else {
                wfEngineFacade.start("FA_TRANSFER", id, userId(),
                        "资产调拨-" + a.getAssetName(), a.getOriginalAmount(), null, extra);
            }
        } catch (Exception ex) {
            log.error("资产 {} 发起调拨审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** 在建工程转固定资产（FA_CIP_TO_ASSET，FA01）：CIP 状态资产达到预定可使用状态，转固并发起转固审批流。 */
    public void cipToAsset(Long id, String transferDate, String remark) {
        MdAsset a = require(id);
        if (!"CIP".equals(a.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅在建工程(CIP)状态资产可转固，当前状态: " + a.getStatus());
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", a.getStatus());
        MdAsset upd = new MdAsset();
        upd.setId(id);
        upd.setStatus("USING");
        // 必填守卫：转固日期就是这张卡片的购置日期 / 折旧起算规则。
        // 留空会静默沿用建卡时的旧值、格式错误直接 ignore，两种情况都会让卡片带着错误的起算日进入折旧。
        if (!StringUtils.hasText(transferDate)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择转固日期");
        }
        try {
            upd.setPurchaseDate(LocalDate.parse(transferDate));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择转固日期（格式 yyyy-MM-dd）");
        }
        mapper.updateById(upd);
        // 审计：状态跃迁（在建工程 → 在用）
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", upd.getStatus());
        auditTrailService.recordUpdate("MD_ASSET", id, a.getAssetNo(), "md_asset",
                String.valueOf(id), auditBefore, auditAfter, "在建工程转固定资产");
        try {
            wfEngineFacade.start("FA_CIP_TO_ASSET", id, userId(),
                    "在建工程转固定资产-" + a.getAssetName() + (StringUtils.hasText(remark) ? remark : ""),
                    a.getOriginalAmount());
        } catch (Exception ex) {
            log.error("资产 {} 发起转固审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** 资产处置-对外销售（FA07 FA_ALIENATE）：与报废(FA06)不同，走对外销售处置审批流。 */
    public void disposeSell(Long id, BigDecimal sellAmount) {
        MdAsset a = require(id);
        if ("SCRAP".equals(a.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已报废资产不可再对外销售处置");
        }
        try {
            wfEngineFacade.start("FA_ALIENATE", id, userId(),
                    "资产对外销售处置-" + a.getAssetName(), sellAmount == null ? a.getOriginalAmount() : sellAmount);
        } catch (Exception ex) {
            log.error("资产 {} 发起对外销售处置审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** 资产盘点差异处理（FA08 FA_CHECK_DIFF）。 */
    public void checkDiff(Long id, BigDecimal diffAmount, String remark) {
        MdAsset a = require(id);
        String procInstId = null;
        try {
            procInstId = wfEngineFacade.start("FA_CHECK_DIFF", id, userId(),
                    "资产盘点差异-" + a.getAssetName(), diffAmount);
        } catch (Exception ex) {
            log.error("资产 {} 发起盘点差异审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
        // remark 原先在方法体内完全丢弃＝差异说明零载体，现随台账写入数据库。
        // 前后镜像四列按「申请时刻卡片现值」写入数据库（before=after=现值快照）——本审批只做差异确认、
        // 不改卡片（结转凭证走总账 GL_CHECK_DIFF），四列如实记录申请时点的账面现值，
        // 不再四列全 NULL（旧式让台账行无任何金额镜像可比对，等于镜像列形同虚设）。
        BigDecimal orig = nvl(a.getOriginalAmount());
        BigDecimal net = nvl(a.getNetAmount());
        writeChangeLog(a, "CHECK_DIFF", remark, procInstId, orig, orig, net, net, diffAmount,
                "差异确认审批在途；账面结转须在总账域另起 GL_CHECK_DIFF 做账");
    }

    /** 资产原值调整（FA09 FA_VALUE_ADJUST）：调整原值并重算净值，发起原值调整审批流。 */
    public void adjustValue(Long id, BigDecimal newOriginalAmount, String reason) {
        MdAsset a = require(id);
        if (newOriginalAmount == null || newOriginalAmount.compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新原值必须大于0");
        }
        BigDecimal delta = newOriginalAmount.subtract(nvl(a.getOriginalAmount()));
        // 原值/净值改账挪到审批通过后（assetBizAction 按流程变量写入数据库），
        // 此处不再预写——驳回/起流失败资产零改动。新值经变量传递，审批人看标题金额=调整额。
        Map<String, Object> extra = new HashMap<>();
        extra.put("newOriginalAmount", newOriginalAmount);
        BigDecimal targetNet = newOriginalAmount.subtract(nvl(a.getAccumDepr()));
        extra.put("newNetAmount", targetNet);
        extra.put("adjustReason", reason == null ? "" : reason);
        String procInstId = null;
        try {
            procInstId = wfEngineFacade.start("FA_VALUE_ADJUST", id, userId(),
                    "资产原值调整-" + a.getAssetName() + (reason == null ? "" : reason),
                    delta.abs(), null, extra);
        } catch (Exception ex) {
            log.error("资产 {} 发起原值调整审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
        // 原因＋前后镜像落变动台账。after 取的就是上面传给审批环节的两个目标值，
        // 同规则不同表——改账仍只在审批通过时发生，台账行只记录"这次要把原值从 X 改成 Y、为什么改"。
        writeChangeLog(a, "VALUE_ADJUST", reason, procInstId,
                nvl(a.getOriginalAmount()), newOriginalAmount, nvl(a.getNetAmount()), targetNet, delta, null);
    }

    /** 资产减值准备（FA10 FA_IMPAIRMENT）：计提减值，发起减值准备审批流。 */
    public void impair(Long id, BigDecimal impairmentAmount) {
        MdAsset a = require(id);
        if (impairmentAmount == null || impairmentAmount.compareTo(ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "减值金额不能为负");
        }
        BigDecimal net = nvl(a.getNetAmount()).subtract(impairmentAmount);
        // 同 adjustValue——净值改账挪到审批通过后，目标值经变量传递。
        Map<String, Object> extra = new HashMap<>();
        extra.put("newNetAmount", net.compareTo(ZERO) < 0 ? ZERO : net);
        extra.put("impairmentAmount", impairmentAmount);
        try {
            wfEngineFacade.start("FA_IMPAIRMENT", id, userId(),
                    "资产减值准备-" + a.getAssetName(), impairmentAmount, null, extra);
        } catch (Exception ex) {
            log.error("资产 {} 发起减值准备审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }
    }

    /** 月折旧：计提一个月折旧并累加至累计折旧（不超过应计折旧额），状态转在用。 */
    public MdAsset depreciate(Long id) {
        MdAsset a = require(id);
        if ("SCRAP".equals(a.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报废资产不再计提折旧");
        }
        // 当月新增资产自次月起计提：购置年月 ≥ 计提目标年月（当月）时拒绝
        if (a.getPurchaseDate() != null
                && !YearMonth.from(a.getPurchaseDate()).isBefore(YearMonth.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当月新增资产自次月起计提");
        }
        LocalDate monthStart = YearMonth.now().atDay(1);
        if (a.getLastDeprDate() != null && !a.getLastDeprDate().isBefore(monthStart)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该资产本期间已计提折旧，请勿重复计提");
        }
        applyDepr(a);
        return a;
    }

    /**
     * 月度折旧批处理（FA_DEPRECIATION 两阶段：阶段一）。
     *
     * <p>按设计稿改造：月度计提不再直接落账，而是生成折旧批单并发起 FA_DEPRECIATION 审批流
     * （{@link AssetDeprBatchService#createAndSubmit}）；审批通过事件回调（{@link AssetWfEventListener}）
     * 触发阶段二实际计提。返回本批待计提资产数。</p>
     */
    public int depreciateMonth() {
        AssetDeprBatch batch = deprBatchService.createAndSubmit(null);
        return batch.getAssetCount() == null ? 0 : batch.getAssetCount();
    }

    /** 计提一个月折旧：累加累计折旧（封顶应计折旧额）、回写净值、更新 lastDeprDate、状态转在用。 */
    private void applyDepr(MdAsset a) {
        BigDecimal base = nvl(a.getOriginalAmount()).subtract(nvl(a.getResidualValue()));
        BigDecimal monthly = nvl(a.getMonthlyDepr());
        BigDecimal accum = nvl(a.getAccumDepr()).add(monthly);
        if (accum.compareTo(base) > 0) {
            accum = base;
        }
        a.setAccumDepr(accum);
        a.setNetAmount(nvl(a.getOriginalAmount()).subtract(accum));
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", a.getStatus());
        a.setStatus("USING");
        a.setLastDeprDate(LocalDate.now());
        mapper.updateById(a);
        // 审计：状态跃迁（→ 在用，首期计提）
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", a.getStatus());
        auditTrailService.recordUpdate("MD_ASSET", a.getId(), a.getAssetNo(), "md_asset",
                String.valueOf(a.getId()), auditBefore, auditAfter, "计提月折旧");
    }

    /** 净值：原值 - 累计折旧。 */
    public BigDecimal netValue(Long id) {
        MdAsset a = require(id);
        return nvl(a.getOriginalAmount()).subtract(nvl(a.getAccumDepr()));
    }

    public MdAsset get(Long id) {
        MdAsset a = require(id);
        orgGuard.assertVisible(a.getCompanyId(), null);
        return a;
    }

    public void update(MdAsset e) {
        if (e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产ID不能为空");
        }
        MdAsset exist = require(e.getId());
        // #13 净值/残值强勾稽：残值 ∈ [0,原值]；净值 = 原值 - 累计折旧（不一致即拦，防脏写）
        BigDecimal orig = e.getOriginalAmount() != null ? e.getOriginalAmount() : nvl(exist.getOriginalAmount());
        BigDecimal resid = e.getResidualValue() != null ? e.getResidualValue() : nvl(exist.getResidualValue());
        BigDecimal accum = e.getAccumDepr() != null ? e.getAccumDepr() : nvl(exist.getAccumDepr());
        if (orig.compareTo(ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产原值不能为负");
        }
        if (resid.compareTo(ZERO) < 0 || resid.compareTo(orig) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "残值(" + resid + ")越界：应为 [0, 原值(" + orig + ")]");
        }
        if (accum.compareTo(ZERO) < 0 || accum.compareTo(orig.subtract(resid)) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "累计折旧(" + accum + ")越界：应为 [0, 应计折旧额(" + orig.subtract(resid) + ")]");
        }
        if (e.getNetAmount() != null && e.getNetAmount().compareTo(orig.subtract(accum)) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "净值勾稽不符：净值(" + e.getNetAmount() + ") ≠ 原值(" + orig + ") - 累计折旧(" + accum + ") = " + orig.subtract(accum));
        }
        // #16 税务规则：税法年限若填写须为正（未填视为与会计年限一致）
        if (e.getTaxLifeMonths() != null) {
            assertTaxLifeMonths(e.getTaxLifeMonths());
        }
        mapper.updateById(e);
    }

    /**
     * #16 税会差异（A105080 资产折旧摊销纳税调整规则）：按税法年限计算税法折旧，与会计折旧比对。
     *
     * <p>会计折旧 &gt; 税法折旧 → 纳税调增（会计多扣，税法不认）；
     * 会计折旧 &lt; 税法折旧 → 纳税调减。税法年限未设置时视为与会计年限一致（差异为 0）。
     */
    public List<Map<String, Object>> taxDiff() {
        List<MdAsset> all = mapper.selectList(new LambdaQueryWrapper<MdAsset>()
                .ne(MdAsset::getStatus, "SCRAP"));
        return diffRows(all);
    }

    /**
     * 税会差异（可选分页）：不传分页参数=全量清单（兼容旧前端）；
     * 传则「全量计算差异 → 过滤 → 服务端切片」，返回 {list,total}，total 恒与 list 同源。
     */
    public Object taxDiff(Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<MdAsset> qw = new LambdaQueryWrapper<MdAsset>()
                .ne(MdAsset::getStatus, "SCRAP");
        // 差异清单取决于逐行计算（diffRows 会过滤掉无差异项），DB 侧 Page 的计数分母
        // 是"参与计算的资产"而非"有差异的行" ⇒ 先全量计算再服务端切片，total 与 list 恒同源，
        // 翻到后页不会出现"总数说有、页面全空"的空档。
        List<Map<String, Object>> all = diffRows(mapper.selectList(qw));
        if (pageNum == null || pageSize == null) {
            return all;
        }
        long fromL = Math.max(0L, (long) (pageNum - 1) * pageSize);
        int from = (int) Math.min(fromL, all.size());
        int to = (int) Math.min(all.size(), from + pageSize);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("list", from >= all.size() ? new ArrayList<>() : new ArrayList<>(all.subList(from, to)));
        resp.put("total", all.size());
        return resp;
    }

    /** 计算并过滤：仅输出存在差异的资产，避免清单被无差异项淹没。 */
    private List<Map<String, Object>> diffRows(List<MdAsset> assets) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MdAsset a : assets) {
            Map<String, Object> row = taxDiffOne(a);
            if (((BigDecimal) row.get("diffAmount")).signum() != 0) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** 单资产税会差异明细 */
    public Map<String, Object> taxDiffOne(MdAsset a) {
        BigDecimal base = nvl(a.getOriginalAmount()).subtract(nvl(a.getResidualValue()));
        int acctMonths = a.getDepreciationMonths() == null ? 0 : a.getDepreciationMonths();
        int taxMonths = a.getTaxLifeMonths() == null || a.getTaxLifeMonths() <= 0 ? acctMonths : a.getTaxLifeMonths();
        BigDecimal acctMonthly = base.signum() > 0 && acctMonths > 0
                ? base.divide(BigDecimal.valueOf(acctMonths), 2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal taxMonthly = base.signum() > 0 && taxMonths > 0
                ? base.divide(BigDecimal.valueOf(taxMonths), 2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal diff = acctMonthly.subtract(taxMonthly);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetId", a.getId());
        m.put("assetNo", a.getAssetNo());
        m.put("assetName", a.getAssetName());
        m.put("originalAmount", a.getOriginalAmount());
        m.put("accountMonths", acctMonths);
        m.put("taxMonths", taxMonths);
        m.put("accountMonthlyDepr", acctMonthly);
        m.put("taxMonthlyDepr", taxMonthly);
        m.put("diffAmount", diff);
        m.put("adjustType", diff.signum() > 0 ? "调增" : (diff.signum() < 0 ? "调减" : "无差异"));
        return m;
    }

    private void assertTaxLifeMonths(Integer months) {
        if (months != null && months <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税法折旧年限必须大于0（月），当前: " + months);
        }
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private MdAsset require(Long id) {
        MdAsset a = mapper.selectById(id);
        if (a == null) throw new BizException(ErrorCode.PARAM_INVALID, "资产不存在: " + id);
        return a;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}