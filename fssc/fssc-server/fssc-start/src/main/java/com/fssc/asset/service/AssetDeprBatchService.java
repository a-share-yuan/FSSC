package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.asset.entity.AssetDeprBatch;
import com.fssc.asset.entity.AssetDeprDetail;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.mapper.AssetDeprBatchMapper;
import com.fssc.asset.mapper.AssetDeprDetailMapper;
import com.fssc.asset.mapper.MdAssetMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 折旧计提批单服务（FA_DEPRECIATION 两阶段落地）。
 *
 * <p>设计稿：资产折旧摊销流程目标规范为「资产核算岗」单一责任岗，重点自动生成凭证 + 审核记录。
 * 月度计提不直接落账，而是生成批单并发起 FA_DEPRECIATION 审批流；审批通过事件
 * （{@link AssetWfEventListener} 监听 {@code WfProcessEndedEvent}）触发 {@link #executeDepr} 去重落账。</p>
 *
 * <p>去重：以资产 {@code lastDeprDate} 为界，批单审批通过后再次执行会跳过本期间已计提资产；
 * 批单状态 APPROVING→DONE 仅推进一次。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetDeprBatchService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AssetDeprBatchMapper batchMapper;
    private final MdAssetMapper assetMapper;
    private final AssetDeprDetailMapper deprDetailMapper;
    private final WfEngineFacade wfEngineFacade;
    private final VoucherService voucherService;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 月折旧批处理触发：生成批单并发起 FA_DEPRECIATION 审批流（两阶段阶段一）。 */
    @Transactional
    public AssetDeprBatch createAndSubmit(String period) {
        AssetDeprBatch batch = create(period);
        submit(batch.getId());
        return batch;
    }

    /** 阶段一(生成)：统计待计提资产并生成 DRAFT 批单（不落账）。
     *  整改：候选集按<b>传入期间</b>筛（不再用真实时钟），传入期间同时作为批单期间，
     *  二者同源——原先 period 只是标签，筛资产恒用 {@code YearMonth.now()}，
     *  传 2026-12 却按真实当月筛，期间语义倒置且本月已计提的资产当月永不可补提。 */
    @Transactional
    public AssetDeprBatch create(String period) {
        YearMonth ref = parsePeriod(period);
        List<MdAsset> targets = eligibleAssets(ref);
        // 待计提数=0 显式拒绝，杜绝 0 资产空批（覆盖 /create、/submit、定时 Job 三入口）
        if (targets.isEmpty()) {
            // 原提示只讲「判定规则」不讲「怎么办」，手册 G5.6 与 G5.7 那样的组合按此规则
            // 必然被拒，新手只能反复试。下面补两类成因的计数与可执行出路，
            // 避免把规则当故障。
            throw new BizException(ErrorCode.PARAM_INVALID, "计提期间 " + ref + " 无待计提资产（待计提数=0），不允许生成折旧批单"
                    + "。判定规则：该期间已计提（最后计提日期不早于 " + ref.atDay(1) + "）、或该期间及之后才购置的资产不计入"
                    + "（当月购置当月不提折旧，会计惯例）。"
                    + "处理办法：① 本期刚购置的资产请改计提下一期间；"
                    + "② 若确需本期计提，请把资产购置日期调整到 " + ref.minusMonths(1) + " 或更早（资产卡片可改），再重试本期间"
                    + "；③ 若资产已计提过，请确认是重复计提而非漏提。");
        }
        String p = ref.toString();
        AssetDeprBatch batch = new AssetDeprBatch();
        batch.setBatchNo(genBatchNo());
        batch.setPeriod(p);
        batch.setAssetCount(targets.size());
        batch.setTotalAmount(targets.stream().map(this::monthlyOf)
                .reduce(ZERO, BigDecimal::add));
        if (batch.getTotalAmount().signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "待计提 " + targets.size() + " 项资产月折旧额合计为 0，不允许生成折旧批单");
        }
        batch.setStatus("DRAFT");
        batch.setCreateBy(userId());
        batchMapper.insert(batch);
        // 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
        log.info("生成折旧计提批单 id={} period={} assetCount={} totalAmount={}",
                batch.getId(), p, targets.size(), batch.getTotalAmount());
        return batch;
    }

    /** 阶段一(发起)：DRAFT 批单发起 FA_DEPRECIATION 审批流，置 APPROVING。 */
    @Transactional
    public void submit(Long batchId) {
        AssetDeprBatch batch = require(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿批单可发起审批，当前状态: " + batch.getStatus());
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", batch.getStatus());
        AssetDeprBatch upd = new AssetDeprBatch();
        upd.setId(batchId);
        upd.setStatus("APPROVING");
        upd.setUpdateBy(userId());
        batchMapper.updateById(upd);
        // 审计：状态跃迁（草稿 → 审批中）
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", upd.getStatus());
        auditTrailService.recordUpdate("ASSET_DEPR", batchId, batch.getBatchNo(), "asset_depr_batch",
                String.valueOf(batchId), auditBefore, auditAfter, "发起折旧批单审批");
        try {
            wfEngineFacade.start("FA_DEPRECIATION", batchId, userId(),
                    "月度折旧计提-" + batch.getPeriod(), batch.getTotalAmount());
        } catch (Exception ex) {
            // 发起失败回滚状态，避免残留 APPROVING 但无实例
            AssetDeprBatch rollback = new AssetDeprBatch();
            rollback.setId(batchId);
            rollback.setStatus("DRAFT");
            rollback.setUpdateBy(userId());
            batchMapper.updateById(rollback);
            // 审计：状态回退（审批中 → 草稿，发起流程失败补偿）
            auditTrailService.recordUpdate("ASSET_DEPR", batchId, batch.getBatchNo(), "asset_depr_batch",
                    String.valueOf(batchId),
                    Collections.singletonMap("status", "APPROVING"),
                    Collections.singletonMap("status", "DRAFT"), "发起审批失败回退草稿");
            throw ex;
        }
        log.info("折旧批单 {} 发起 FA_DEPRECIATION 审批流", batchId);
    }

    /**
     * 阶段二(落账)：审批通过后执行实际计提（去重）。仅 APPROVING 批单可执行；
     * 已 DONE 直接返回，REJECTED 拒绝执行。
     */
    @Transactional
    public void executeDepr(Long batchId) {
        AssetDeprBatch batch = require(batchId);
        if ("DONE".equals(batch.getStatus())) {
            log.info("折旧批单 {} 已 DONE，跳过重复执行", batchId);
            return;
        }
        if ("REJECTED".equals(batch.getStatus())) {
            log.warn("折旧批单 {} 审批驳回，不执行计提", batchId);
            return;
        }
        if (!"APPROVING".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中(APPROVING)批单可执行计提，当前状态: " + batch.getStatus());
        }
        YearMonth ref = parsePeriod(batch.getPeriod());
        LocalDate monthStart = ref.atDay(1);
        String period = ref.toString();
        // 落账阶段同样以<b>批单期间</b>为唯一规则筛候选（原先此处再取一次真实当月，
        // 跨零点执行时批单期间与实际计提期间会劈叉）
        List<MdAsset> targets = eligibleAssets(ref);
        // 写明细前置校验——批头行必须真实存在。require() 虽已在入口查过批单，但本方法随后要写
        // md_asset_depr_detail（该表 batch_id 可空且<b>无外键约束</b>，information_schema 已核实），
        // 一旦批头在途消失（清理脚本会 TRUNCATE asset_depr_batch 而不清子表，
        // 留下指向不存在批次的孤儿明细），写出来的就是无法回溯的半截批次。此处 fail-closed。
        assertHeaderPresent(batchId);
        List<String> executed = new ArrayList<>();
        int count = 0;
        for (MdAsset a : targets) {
            AssetDeprDetail detail = applyDepr(a, monthStart, batchId, period);
            // 逐个资产生成折旧凭证。期间必须传批单期间，
            // 否则凭证按系统当期落账，与批单/明细期间劈叉。
            try {
                Long voucherId = voucherService.generateDepreciationVoucher(a, period);
                // 凭证ID回写明细 source_voucher_id（双向可追：明细→凭证，
                // 凭证→source_doc_id→资产卡）；金额为 0 时凭证未生成（null），不回写。
                if (voucherId != null) {
                    AssetDeprDetail du = new AssetDeprDetail();
                    du.setId(detail.getId());
                    du.setSourceVoucherId(voucherId);
                    deprDetailMapper.updateById(du);
                }
            } catch (Exception vex) {
                log.error("资产 {} 折旧凭证生成失败（不影响计提）: {}", a.getId(), vex.getMessage(), vex);
            }
            executed.add(String.valueOf(a.getId()));
            count++;
        }
        // 写明细后置一致性断言（同 assertAllLinked 风格）——
        // 批单下已落明细条数少于本次计提数＝半截批次，显式抛错让整单回滚，不留「明细在、批头/批单结果不在」。
        assertDetailsLinked(batchId, count);
        AssetDeprBatch upd = new AssetDeprBatch();
        upd.setId(batchId);
        upd.setStatus("DONE");
        upd.setAssetCount(count);
        // 定稿规则（对照 BankReconBizAction 的 CONFIRMED 最终状态模式）：审批通过回调是唯一
        // 定稿入口——批单 APPROVING→DONE 仅由本方法推进一次（上方 DONE 去重返回）；result_json
        // 落 finalized 标记与定稿时点，审批驳回走 reject() 置 REJECTED（同为最终状态、不落账）。
        upd.setResultJson("{\"executed\": " + count + ", \"assetIds\": [" + String.join(",", executed) + "]"
                + ", \"finalized\": true, \"finalizedAt\": \"" + LocalDateTime.now() + "\"}");
        upd.setUpdateBy(userId());
        batchMapper.updateById(upd);
        // 审计：状态跃迁（审批中 → 已完成定稿）
        auditTrailService.recordUpdate("ASSET_DEPR", batchId, batch.getBatchNo(), "asset_depr_batch",
                String.valueOf(batchId),
                Collections.singletonMap("status", batch.getStatus()),
                Collections.singletonMap("status", upd.getStatus()), "折旧批单审批通过定稿（落账）");
        log.info("折旧批单 {} 审批通过，实际计提 {} 条资产", batchId, count);
    }

    /** 审批驳回：批单置 REJECTED（不落账）。 */
    @Transactional
    public void reject(Long batchId) {
        AssetDeprBatch batch = require(batchId);
        if ("DONE".equals(batch.getStatus())) {
            return;
        }
        AssetDeprBatch upd = new AssetDeprBatch();
        upd.setId(batchId);
        upd.setStatus("REJECTED");
        upd.setUpdateBy(userId());
        batchMapper.updateById(upd);
        // 审计：状态跃迁（→ 已驳回）
        auditTrailService.recordUpdate("ASSET_DEPR", batchId, batch.getBatchNo(), "asset_depr_batch",
                String.valueOf(batchId),
                Collections.singletonMap("status", batch.getStatus()),
                Collections.singletonMap("status", upd.getStatus()), "折旧批单审批驳回");
        log.info("折旧批单 {} 审批驳回，置 REJECTED", batchId);
    }

    public PageResult<AssetDeprBatch> page(long pn, long ps) {
        Page<AssetDeprBatch> p = batchMapper.selectPage(Page.of(pn, ps),
                new LambdaQueryWrapper<AssetDeprBatch>()
                        .orderByDesc(AssetDeprBatch::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    public AssetDeprBatch get(Long id) {
        return require(id);
    }

    /** 查询折旧明细（按批单或按资产） */
    public List<AssetDeprDetail> detailsByBatch(Long batchId) {
        return deprDetailMapper.selectList(new LambdaQueryWrapper<AssetDeprDetail>()
                .eq(AssetDeprDetail::getBatchId, batchId)
                .orderByAsc(AssetDeprDetail::getAssetId));
    }

    public List<AssetDeprDetail> detailsByAsset(Long assetId) {
        return deprDetailMapper.selectList(new LambdaQueryWrapper<AssetDeprDetail>()
                .eq(AssetDeprDetail::getAssetId, assetId)
                .orderByDesc(AssetDeprDetail::getDeprDate));
    }

    // 内部辅助

    /**
     * 待计提资产：未报废、非在建工程(CIP) 且 <b>传入期间</b>尚未计提 且 非<b>传入期间</b>及之后新增。
     * <p>整改：两条判定原先都关联在 {@code YearMonth.now()}（真实时钟）上，界面传的 period
     * 只当标签用 → 传 2026-12 也按真实当月筛，候选恒空 / 本月已计提的资产当月不可补提。
     * 现一律以参数 {@code refMonth}（＝单据期间）为规则，时钟值只在 period 缺省时由
     * {@link #parsePeriod(String)} 兜一次底（月度 Job 语义"当月"保持不变）。
     * <p>缺陷修复（H-03 / DA-02）：在建工程达到预定可使用状态并转固（FA_CIP_TO_ASSET）后方可计提折旧
     * 企业会计准则第4号——固定资产，CIP 状态卡片不得进入月度折旧批。
     */
    private List<MdAsset> eligibleAssets(YearMonth refMonth) {
        LocalDate monthStart = refMonth.atDay(1);
        List<MdAsset> all = assetMapper.selectList(new LambdaQueryWrapper<MdAsset>()
                .ne(MdAsset::getStatus, "SCRAP")
                .ne(MdAsset::getStatus, "CIP")
                .and(w -> w.isNull(MdAsset::getLastDeprDate)
                        .or().lt(MdAsset::getLastDeprDate, monthStart)));
        List<MdAsset> targets = new ArrayList<>();
        for (MdAsset a : all) {
            if (a.getPurchaseDate() != null
                    && !YearMonth.from(a.getPurchaseDate()).isBefore(refMonth)) {
                continue;
            }
            targets.add(a);
        }
        return targets;
    }

    /**
     * 期间解析：接受 {@code yyyy-MM}（界面/Job 规则）与 {@code yyyyMM}（GL 域规则），
     * 空值按<b>当前月</b>——服务层传 null 表示"当月"的既有语义保持不变（{@code MdAssetService.depreciateMonth}）。
     * 非法格式显式拒绝，不再静默 fallback 到当月（静默补值是 「期间语义倒置」的另一半）。
     */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        String s = period.trim();
        try {
            if (s.matches("\\d{4}-\\d{2}")) {
                return YearMonth.parse(s);
            }
            if (s.matches("\\d{6}")) {
                return YearMonth.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)));
            }
        } catch (Exception ignore) {
            // 统一落到下面的显式拒绝，不抛解析器原始异常
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "计提期间格式不正确：" + period + "（应为 yyyy-MM，如 2026-12）");
    }

    /** 计提一个月折旧：累加累计折旧（封顶应计折旧额）、回写净值、更新 lastDeprDate、状态转在用。
     *  同时写入折旧明细记录(md_asset_depr_detail)。
     *  计提日期规则改为<b>所属期间月末</b>（原先写 {@code LocalDate.now()}）——
     *  跨月补提时把"今天"当 last_depr_date 会把该资产在后续期间的候选集里永久挤掉
     *  （下一期间月首 &lt;= lastDeprDate 即被筛除），补提与重跑因此不可达。 */
    private AssetDeprDetail applyDepr(MdAsset a, LocalDate monthStart, Long batchId, String period) {
        // batch_id 列可空，写 NULL 就等于造一条无从归属批单的孤儿计提明细——明细必须挂批头，缺批头即拒写。
        if (batchId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "折旧计提落账缺少批单ID（batch_id 不可为空），已拒绝写入资产 " + a.getId() + " 的计提明细");
        }
        BigDecimal base = nvl(a.getOriginalAmount()).subtract(nvl(a.getResidualValue()));
        BigDecimal monthly = nvl(a.getMonthlyDepr());
        BigDecimal accum = nvl(a.getAccumDepr()).add(monthly);
        if (accum.compareTo(base) > 0) {
            accum = base;
            monthly = base.subtract(nvl(a.getAccumDepr()));
        }
        BigDecimal netAfter = nvl(a.getOriginalAmount()).subtract(accum);
        LocalDate deprDate = monthStart.plusMonths(1).minusDays(1);
        MdAsset upd = new MdAsset();
        upd.setId(a.getId());
        upd.setAccumDepr(accum);
        upd.setNetAmount(netAfter);
        upd.setStatus("USING");
        upd.setLastDeprDate(deprDate);
        assetMapper.updateById(upd);
        // 审计：资产状态跃迁（→ 在用，月度折旧计提）
        auditTrailService.recordUpdate("MD_ASSET", a.getId(), a.getAssetNo(), "md_asset",
                String.valueOf(a.getId()),
                Collections.singletonMap("status", a.getStatus()),
                Collections.singletonMap("status", upd.getStatus()), "月度折旧计提");

        // 写折旧明细记录
        AssetDeprDetail detail = new AssetDeprDetail();
        detail.setAssetId(a.getId());
        detail.setBatchId(batchId);
        detail.setPeriod(period);
        detail.setMonthlyDepr(monthly);
        detail.setAccumDeprAfter(accum);
        detail.setNetAmountAfter(netAfter);
        detail.setDeprDate(deprDate);
        deprDetailMapper.insert(detail);
        // 返回明细（含自增 id），供调用方在凭证生成后回写 source_voucher_id
        return detail;
    }

    private BigDecimal monthlyOf(MdAsset a) {
        return nvl(a.getMonthlyDepr());
    }

    private String genBatchNo() {
        return "DPR" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private AssetDeprBatch require(Long id) {
        AssetDeprBatch b = id == null ? null : batchMapper.selectById(id);
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "折旧批单不存在: " + id);
        }
        return b;
    }

    /**
     * 批头存在性断言（fail-closed）：折旧计提明细只允许写在真实存在的批单下。
     *
     * <p>本仓库唯一的明细写点是 {@link #applyDepr}（batch_id 取自批单主键），
     * 「头行 0 行 + 明细指向不存在批次」并非代码造出的半截批次，而是外部清理脚本
     * （{@code fssc/sql/V9.9.9_cleanup_garbage_data.sql:161}、{@code V9.9.12_cleanup_after_wf_refactor.sql:265}
     * 都 {@code TRUNCATE TABLE asset_depr_batch} 而不清 {@code md_asset_depr_detail}）
     * 加无外键约束共同造成的孤儿（批头已是空表，明细仍挂着不存在的批次）。
     * 代码侧自此把不变式写死：<b>写明细前批头查不到 ⇒ 整单抛错回滚</b>，孤儿不再可能由本域产生。
     */
    private void assertHeaderPresent(Long batchId) {
        if (batchId == null || batchMapper.selectById(batchId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "折旧落账中断：批单 " + batchId + " 头行不存在（批单被删除或未保存），禁止写入计提明细");
        }
    }

    /**
     * 明细↔批单一致性断言：本次计提 N 个资产，批单下就至少要有 N 条明细；
     * 少了说明有明细没落进批单（半截批次），显式抛错让 {@code @Transactional} 整体回滚，绝不静默。
     */
    private void assertDetailsLinked(Long batchId, int expected) {
        if (expected <= 0) {
            return;
        }
        Long linked = deprDetailMapper.selectCount(new LambdaQueryWrapper<AssetDeprDetail>()
                .eq(AssetDeprDetail::getBatchId, batchId));
        int n = linked == null ? -1 : linked.intValue();
        if (n < expected) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "折旧批单 " + batchId + " 计提明细只落 " + Math.max(n, 0) + " 条（应 " + expected
                            + " 条），半截批次整单回滚");
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}