// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.mapper.FraudDetectionLogMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private static final int FREQUENCY_THRESHOLD = 10;
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("50000");
    /** 反欺诈频次类规则白名单配置键（sys_config.config_key，值为逗号分隔的 user_name 列表） */
    private static final String WHITELIST_CONFIG_KEY = "fraud.whitelist.users";
    /** D-FRD-1：频次阈值可配置键（sys_config，正整数；未配置/非法则回退默认 10） */
    private static final String FREQUENCY_THRESHOLD_CONFIG_KEY = "fraud.frequency.threshold";
    /**
     * 批次 1 ⑤：提单侧是否硬阻断（sys_config，'1'=阻断）。<b>缺省 0＝不阻断</b>——
     * 反欺诈改为三档「提示 → 人工待审 → 阻断」，硬阻断位下移到付款指令执行前
     * （"风险检查点挂在支付指令上"、"解释为什么不通过"式提示）。
     */
    private static final String SUBMIT_BLOCK_CONFIG_KEY = "fraud.block_on_submit.enabled";
    /** 批次 1 ⑤：影子模式（sys_config，'1'=只打标不阻断，含付款侧）；演练期观察误拦率用 */
    private static final String SHADOW_MODE_CONFIG_KEY = "fraud.shadow_mode.enabled";

    private final FraudDetectionLogMapper fraudDetectionLogMapper;
    /** 白名单配置来源（sys_config），无该 key 时视为空白名单，行为与原先完全一致 */
    private final com.fssc.system.service.ConfigService configService;
    /** 白名单 user_name → 用户ID 解析 */
    private final com.fssc.system.mapper.SysUserMapper sysUserMapper;

    /**
     * 判断员工是否命中反欺诈频次类规则白名单。
     * 白名单来源 sys_config（config_key='fraud.whitelist.users'，config_value=逗号分隔 user_name）；
     * 未配置该 key / 配置为空 / 读取异常 → 空白名单（返回 false，行为与原先完全一致）。
     * 命中白名单的用户跳过 FREQUENCY/AMOUNT/SUPPLIER/TIME/GEOGRAPHY 等频次类规则（其他不合规项照常）。
     */
    /**
     * G2R-02（2026-10-19 真跑实证修复）：统计员工近 7 天<b>实际提交的单据数</b>，供 FREQUENCY 规则查询。
     * <p>查询以 create_by 为提交人（历史数据缺失时回退 apply_user_id），按 create_time 圈近 7 天，
     * 只数 exp_bill 主表行数（一张单一行），不含检测流水 —— 因此不会像旧实现那样被自身的检测记录放大。
     * 查询异常不得反噬风控判定：回退为「检测流水按 bill_id 去重后的单据数」（仍与提交单据一一对应），
     * 绝不退回「流水行数」规则。
     */
    private long countSubmittedBillsIn7Days(Long employeeId, LocalDateTime since) {
        try {
            LambdaQueryWrapper<ExpBill> w = new LambdaQueryWrapper<>();
            w.eq(ExpBill::getCreateBy, employeeId).ge(ExpBill::getCreateTime, since);
            long n = expBillMapper.selectCount(w);
            if (n > 0) {
                return n;
            }
            LambdaQueryWrapper<ExpBill> w2 = new LambdaQueryWrapper<>();
            w2.eq(ExpBill::getApplyUserId, employeeId).ge(ExpBill::getCreateTime, since);
            return expBillMapper.selectCount(w2);
        } catch (Exception ex) {
            log.warn("[反欺诈] 按提交单据统计近7天频次失败（employeeId={}），回退按检测流水去重: {}",
                    employeeId, ex.getMessage());
            try {
                QueryWrapper<FraudDetectionLog> q = new QueryWrapper<>();
                q.select("DISTINCT bill_id")
                        .eq("employee_id", employeeId)
                        .eq("detection_type", "FREQUENCY")
                        .isNotNull("bill_id")
                        .ge("create_time", since);
                return fraudDetectionLogMapper.selectCount(q);
            } catch (Exception ex2) {
                log.warn("[反欺诈] 频次统计回退亦失败（employeeId={}）: {}", employeeId, ex2.getMessage());
                return 0L;
            }
        }
    }

    private boolean isFrequencyWhitelisted(Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        String csv;
        try {
            csv = configService.getValue(WHITELIST_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("读取反欺诈白名单配置 {} 失败（按未配置处理）: {}", WHITELIST_CONFIG_KEY, e.getMessage());
            return false;
        }
        if (csv == null || csv.isBlank()) {
            return false;
        }
        List<String> names = Arrays.stream(csv.split("[,，;；\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (names.isEmpty()) {
            return false;
        }
        try {
            List<com.fssc.system.entity.SysUser> users = sysUserMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.system.entity.SysUser>()
                            .in(com.fssc.system.entity.SysUser::getUserName, names));
            boolean hit = users.stream().anyMatch(u -> employeeId.equals(u.getId()));
            if (hit) {
                log.info("反欺诈检测: 员工 {} 命中白名单（{}），跳过频次类规则", employeeId, WHITELIST_CONFIG_KEY);
            }
            return hit;
        } catch (Exception e) {
            log.warn("解析反欺诈白名单用户失败（按未配置处理）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * D-FRD-1：生效的频次阈值 —— sys_config.fraud.frequency.threshold（正整数）优先，
     * 未配置/解析失败/非正数回退默认 10（行为与硬编码时代一致）。
     */
    private int frequencyThreshold() {
        try {
            String v = configService.getValue(FREQUENCY_THRESHOLD_CONFIG_KEY);
            if (v != null && !v.isBlank()) {
                int n = Integer.parseInt(v.trim());
                if (n > 0) return n;
            }
        } catch (Exception e) {
            log.warn("读取反欺诈频次阈值配置 {} 失败（回退默认 {}）: {}",
                    FREQUENCY_THRESHOLD_CONFIG_KEY, FREQUENCY_THRESHOLD, e.getMessage());
        }
        return FREQUENCY_THRESHOLD;
    }

    /** 开关读取：值为 '1' 视为开启；缺失/其他值/读取异常一律按未开启（保持缺省语义）。 */
    private boolean configOn(String key) {
        try {
            String v = configService.getValue(key);
            return v != null && "1".equals(v.trim());
        } catch (Exception e) {
            log.warn("读取反欺诈开关 {} 失败（按未开启处理）: {}", key, e.getMessage());
            return false;
        }
    }

    /** 提单侧是否硬阻断（批次 1 ⑤，缺省关＝高风险只进人工待审队列，不挡流程） */
    public boolean isSubmitBlockEnabled() {
        return configOn(SUBMIT_BLOCK_CONFIG_KEY) && !isShadowMode();
    }

    /** 影子模式（批次 1 ⑤）：开启后全链路只打标不阻断，用于演练期观察误拦率 */
    public boolean isShadowMode() {
        return configOn(SHADOW_MODE_CONFIG_KEY);
    }

    /**
     * 付款指令执行前的反欺诈硬阻断（批次 1 ⑤：阻断位从"提单"下移到"付钱前"）。
     *
     * <p>规则：本单据存在<b>高风险且尚未人工复审结案</b>（{@code reviewed_by} 为空且处置不是
     * {@code RELEASE}）的检测记录 → 拒付。影子模式（{@code fraud.shadow_mode.enabled=1}）只记日志不阻断；
     * 读取异常按放行处理（与提单侧同一规则：风控设施故障不得把付款链整条卡死）。
     *
     * @return {@code null}=放行；否则为拒付原因（调用方据此抛业务异常）
     */
    public String paymentBlockReason(Long billId) {
        if (billId == null) {
            return null;
        }
        List<FraudDetectionLog> hits;
        try {
            LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
            qw.eq(FraudDetectionLog::getBillId, billId)
                    .eq(FraudDetectionLog::getDetectionResult, "HIGH_RISK")
                    .isNull(FraudDetectionLog::getReviewedBy)
                    .ne(FraudDetectionLog::getActionTaken, "RELEASE");
            hits = fraudDetectionLogMapper.selectList(qw);
        } catch (Exception e) {
            log.warn("付款侧反欺诈检查异常（按放行处理）billId={}: {}", billId, e.getMessage());
            return null;
        }
        if (hits.isEmpty()) {
            return null;
        }
        String reason = hits.stream()
                .map(h -> "[" + h.getDetectionType() + "] " + h.getDetails())
                .collect(Collectors.joining("；"));
        if (isShadowMode()) {
            log.warn("反欺诈影子模式：付款侧命中高风险仅打标不阻断 billId={} {}", billId, reason);
            return null;
        }
        log.warn("付款侧反欺诈阻断 billId={}: {}", billId, reason);
        return reason;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<FraudDetectionLog> detectFraud(Long employeeId, Long billId) {
        List<FraudDetectionLog> logs = new ArrayList<>();

        if (employeeId == null) {
            return logs;
        }

        // 白名单命中 → 跳过频次统计与全部频次类规则（检测项仍落 NORMAL 记录，保持流水完整）
        boolean whitelisted = isFrequencyWhitelisted(employeeId);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        // G2R-02（2026-10-19 真跑实证修复）：此前 recentCount 统计的是 fraud_detection_log 的
        // **流水行数**——而每一次 detectFraud() 会同时落 FREQUENCY/AMOUNT/GEOGRAPHY/VENDOR/TIME
        // 等多条检测记录，于是「规则的输出在喂养规则的输入」：阈值 10 ⇒ 约 2 次检测必然命中，
        // 且系统用得越久误拦率越高（实案：员工 3933 近 7 天"检测次数"达 63 次，付款链被硬拦）。
        // 语义上本规则要管的是「员工近 7 天提交了多少张单」，故查询改为 exp_bill 的实际提交单据数。
        long recentCount = whitelisted ? 0 : countSubmittedBillsIn7Days(employeeId, sevenDaysAgo);
        int threshold = frequencyThreshold();

        if (!whitelisted && recentCount >= threshold) {
            FraudDetectionLog freqLog = new FraudDetectionLog();
            freqLog.setEmployeeId(employeeId);
            freqLog.setBillId(billId);
            freqLog.setDetectionType("FREQUENCY");
            freqLog.setDetectionResult("HIGH_RISK");
            freqLog.setRiskScore(new BigDecimal("80"));
            freqLog.setDetails("员工近7天内提交单据达 " + recentCount + " 张，超过阈值 " + threshold + " 张");
            // 三档语义（批次 1 ⑤）：高风险默认进人工待审队列（REVIEW），只有显式开回提单侧阻断才是 BLOCK
            freqLog.setActionTaken(isSubmitBlockEnabled() ? "BLOCK" : "REVIEW");
            fraudDetectionLogMapper.insert(freqLog);
            logs.add(freqLog);
        } else if (!whitelisted && recentCount >= threshold / 2) {
            FraudDetectionLog freqLog = new FraudDetectionLog();
            freqLog.setEmployeeId(employeeId);
            freqLog.setBillId(billId);
            freqLog.setDetectionType("FREQUENCY");
            freqLog.setDetectionResult("SUSPICIOUS");
            freqLog.setRiskScore(new BigDecimal("50"));
            freqLog.setDetails("员工近7天内提交单据达 " + recentCount + " 张，接近阈值");
            freqLog.setActionTaken("WARN");
            fraudDetectionLogMapper.insert(freqLog);
            logs.add(freqLog);
        }

        FraudDetectionLog amountLog = new FraudDetectionLog();
        amountLog.setEmployeeId(employeeId);
        amountLog.setBillId(billId);
        amountLog.setDetectionType("AMOUNT");
        if (!whitelisted && billId != null && recentCount > 0) {
            if (recentCount >= threshold) {
                amountLog.setDetectionResult("HIGH_RISK");
                amountLog.setRiskScore(new BigDecimal("90"));
                amountLog.setDetails("员工高频提交单据，可能存在金额操纵风险");
                amountLog.setActionTaken(isSubmitBlockEnabled() ? "BLOCK" : "REVIEW");
            } else if (recentCount >= threshold / 2) {
                amountLog.setDetectionResult("SUSPICIOUS");
                amountLog.setRiskScore(new BigDecimal("60"));
                amountLog.setDetails("员工近期提交频率较高，需关注金额异常");
                amountLog.setActionTaken("WARN");
            } else {
                amountLog.setDetectionResult("NORMAL");
                amountLog.setRiskScore(BigDecimal.ZERO);
                amountLog.setDetails("金额检测通过");
                amountLog.setActionTaken("ALLOW");
            }
        } else {
            amountLog.setDetectionResult("NORMAL");
            amountLog.setRiskScore(BigDecimal.ZERO);
            amountLog.setDetails("金额检测通过");
            amountLog.setActionTaken("ALLOW");
        }
        fraudDetectionLogMapper.insert(amountLog);
        logs.add(amountLog);

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        LambdaQueryWrapper<FraudDetectionLog> geoWrapper = new LambdaQueryWrapper<>();
        geoWrapper.eq(FraudDetectionLog::getEmployeeId, employeeId)
                .ge(FraudDetectionLog::getCreateTime, oneDayAgo)
                .eq(FraudDetectionLog::getDetectionType, "GEOGRAPHY");
        long recentGeoCount = whitelisted ? 0 : fraudDetectionLogMapper.selectCount(geoWrapper);

        FraudDetectionLog geoLog = new FraudDetectionLog();
        geoLog.setEmployeeId(employeeId);
        geoLog.setBillId(billId);
        geoLog.setDetectionType("GEOGRAPHY");
        if (!whitelisted && recentGeoCount > 5) {
            geoLog.setDetectionResult("SUSPICIOUS");
            geoLog.setRiskScore(new BigDecimal("55"));
            geoLog.setDetails("员工近期地理位置异常，可能存在异地操作");
            geoLog.setActionTaken("WARN");
        } else {
            geoLog.setDetectionResult("NORMAL");
            geoLog.setRiskScore(BigDecimal.ZERO);
            geoLog.setDetails("地理检测通过");
            geoLog.setActionTaken("ALLOW");
        }
        fraudDetectionLogMapper.insert(geoLog);
        logs.add(geoLog);

        FraudDetectionLog vendorLog = new FraudDetectionLog();
        vendorLog.setEmployeeId(employeeId);
        vendorLog.setBillId(billId);
        vendorLog.setDetectionType("SUPPLIER");
        if (!whitelisted && recentCount >= 8) {
            vendorLog.setDetectionResult("SUSPICIOUS");
            vendorLog.setRiskScore(new BigDecimal("45"));
            vendorLog.setDetails("员工高频与供应商交互，需关注利益冲突");
            vendorLog.setActionTaken("WARN");
        } else {
            vendorLog.setDetectionResult("NORMAL");
            vendorLog.setRiskScore(BigDecimal.ZERO);
            vendorLog.setDetails("供应商检测通过");
            vendorLog.setActionTaken("ALLOW");
        }
        fraudDetectionLogMapper.insert(vendorLog);
        logs.add(vendorLog);

        int currentHour = LocalDateTime.now().getHour();
        FraudDetectionLog timeLog = new FraudDetectionLog();
        timeLog.setEmployeeId(employeeId);
        timeLog.setBillId(billId);
        timeLog.setDetectionType("TIME");
        if (!whitelisted && currentHour >= 0 && currentHour < 6) {
            timeLog.setDetectionResult("SUSPICIOUS");
            timeLog.setRiskScore(new BigDecimal("40"));
            timeLog.setDetails("非工作时间提交单据");
            timeLog.setActionTaken("WARN");
        } else {
            timeLog.setDetectionResult("NORMAL");
            timeLog.setRiskScore(BigDecimal.ZERO);
            timeLog.setDetails("时间检测通过");
            timeLog.setActionTaken("ALLOW");
        }
        fraudDetectionLogMapper.insert(timeLog);
        logs.add(timeLog);

        log.info("反欺诈检测完成: employeeId={}, billId={}, 检测条数={}, 风险等级={}",
                employeeId, billId, logs.size(),
                logs.stream().anyMatch(l -> "HIGH_RISK".equals(l.getDetectionResult())) ? "HIGH" :
                logs.stream().anyMatch(l -> "SUSPICIOUS".equals(l.getDetectionResult())) ? "MEDIUM" : "LOW");

        return logs;
    }

    public List<FraudDetectionLog> listByEmployee(Long employeeId) {
        LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
        qw.eq(FraudDetectionLog::getEmployeeId, employeeId);
        qw.orderByDesc(FraudDetectionLog::getCreateTime);
        return fraudDetectionLogMapper.selectList(qw);
    }

    public List<FraudDetectionLog> listByBill(Long billId) {
        LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
        qw.eq(FraudDetectionLog::getBillId, billId);
        qw.orderByDesc(FraudDetectionLog::getCreateTime);
        return fraudDetectionLogMapper.selectList(qw);
    }

    public List<FraudDetectionLog> list(String detectionResult, Long employeeId, String actionTaken) {
        LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
        if (detectionResult != null && !detectionResult.isBlank()) {
            qw.eq(FraudDetectionLog::getDetectionResult, detectionResult);
        }
        if (employeeId != null) {
            qw.eq(FraudDetectionLog::getEmployeeId, employeeId);
        }
        if (actionTaken != null && !actionTaken.isBlank()) {
            qw.eq(FraudDetectionLog::getActionTaken, actionTaken);
        }
        qw.orderByDesc(FraudDetectionLog::getCreateTime);
        return fraudDetectionLogMapper.selectList(qw);
    }

    /** P2-5: 分页查询检测记录（风控看板数据源；detectionResult=HIGH_RISK/SUSPICIOUS/NORMAL） */
    public PageResult<com.fssc.compliance.vo.FraudDetectionLogVO> page(long pageNum, long pageSize, String detectionResult,
                                              Long employeeId, String detectionType,
                                              String actionTaken, Boolean unreviewed,
                                              Boolean pendingRelease) {
        LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
        if (detectionResult != null && !detectionResult.isBlank()) {
            qw.eq(FraudDetectionLog::getDetectionResult, detectionResult);
        }
        if (employeeId != null) {
            qw.eq(FraudDetectionLog::getEmployeeId, employeeId);
        }
        if (detectionType != null && !detectionType.isBlank()) {
            qw.eq(FraudDetectionLog::getDetectionType, detectionType);
        }
        if (actionTaken != null && !actionTaken.isBlank()) {
            qw.eq(FraudDetectionLog::getActionTaken, actionTaken);
        }
        // D-F1R-07：待处置队列必须与付款校验 {@link #paymentBlockReason} <b>同源同判据</b>，
        // 否则风控看到的"待复审"和真正卡住付款的那批记录不是同一批。
        // 原判据只按 actionTaken 精确相等筛选，而记录写入数据库时 actionTaken 的初值就是 'REVIEW'
        // （＝转人工待审），既不是空也不是 'PENDING' ⇒ 按"未处置/空值"筛必然得到 0 条，
        // 风控会误以为没有待办，付款却一直被拦。现在两条专用队列：
        //  · pendingRelease=true → 正在阻断付款的记录（与 paymentBlockReason 逐条对齐）
        //  · unreviewed=true      → 未做终局处置的记录（语义＝无终局处置人且未放行）
        if (Boolean.TRUE.equals(pendingRelease)) {
            qw.eq(FraudDetectionLog::getDetectionResult, "HIGH_RISK")
                    .isNull(FraudDetectionLog::getReviewedBy)
                    .ne(FraudDetectionLog::getActionTaken, "RELEASE");
        }
        if (Boolean.TRUE.equals(unreviewed)) {
            qw.isNull(FraudDetectionLog::getReviewedAt)
                    .and(w -> w.isNull(FraudDetectionLog::getActionTaken)
                            .or().ne(FraudDetectionLog::getActionTaken, "RELEASE"));
        }
        qw.orderByDesc(FraudDetectionLog::getId);
        
        // 使用关联查询补充员工姓名和单据编号
        com.baomidou.mybatisplus.core.metadata.IPage<com.fssc.compliance.vo.FraudDetectionLogVO> p = 
            fraudDetectionLogMapper.selectPageWithRelations(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long logId, Long reviewerId, String action) {
        if (!"RELEASE".equals(action) && !"BLOCK".equals(action) && !"REVIEW".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "复审处置只允许 RELEASE / BLOCK / REVIEW，收到: " + action);
        }
        FraudDetectionLog logEntity = fraudDetectionLogMapper.selectById(logId);
        if (logEntity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "欺诈检测记录不存在: " + logId);
        }
        logEntity.setReviewedBy(reviewerId);
        logEntity.setReviewedAt(LocalDateTime.now());
        logEntity.setActionTaken(action);
        fraudDetectionLogMapper.updateById(logEntity);

        log.info("欺诈风险审核完成: logId={}, reviewerId={}, action={}", logId, reviewerId, action);
    }

    /**
     * 41号审查 P2-12 申诉闭环：被拦截/预警方登记申诉。原因追加进 details 记录，
     * 处置状态不变（等待财务申诉复审）；同一单可多次申诉记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void appeal(Long logId, String reason) {
        FraudDetectionLog logEntity = fraudDetectionLogMapper.selectById(logId);
        if (logEntity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "欺诈检测记录不存在: " + logId);
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申诉原因必填");
        }
        String stamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String mark = "[申诉待审 " + stamp + "] " + reason.trim();
        logEntity.setDetails((logEntity.getDetails() == null ? "" : logEntity.getDetails() + "\n") + mark);
        fraudDetectionLogMapper.updateById(logEntity);
        log.info("欺诈风险申诉登记: logId={}, {}", logId, reason);
    }

    /**
     * 41号审查 P2-12 申诉复审：approve=true → 放行(RELEASE)并结案；false → 维持原处置并记录驳回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void appealReview(Long logId, boolean approve, String note, Long reviewerId) {
        FraudDetectionLog logEntity = fraudDetectionLogMapper.selectById(logId);
        if (logEntity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "欺诈检测记录不存在: " + logId);
        }
        String stamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String suffix = (logEntity.getDetails() == null ? "" : logEntity.getDetails() + "\n");
        if (approve) {
            logEntity.setActionTaken("RELEASE");
            logEntity.setDetails(suffix + "[申诉通过 " + stamp + "] " + (note == null ? "" : note));
        } else {
            logEntity.setDetails(suffix + "[申诉驳回 " + stamp + "] " + (note == null || note.isBlank() ? "维持原处置" : note));
        }
        logEntity.setReviewedBy(reviewerId);
        logEntity.setReviewedAt(LocalDateTime.now());
        fraudDetectionLogMapper.updateById(logEntity);
        log.info("欺诈风险申诉复审: logId={}, approve={}", logId, approve);
    }

    public long getHighRiskCount(Long employeeId) {
        LambdaQueryWrapper<FraudDetectionLog> qw = new LambdaQueryWrapper<>();
        qw.eq(FraudDetectionLog::getEmployeeId, employeeId)
                .eq(FraudDetectionLog::getDetectionResult, "HIGH_RISK");
        return fraudDetectionLogMapper.selectCount(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<FraudDetectionLog> detectFraudWithDetails(Long employeeId, Long billId,
                                                           BigDecimal amount, String supplierCode,
                                                           String expenseType) {
        List<FraudDetectionLog> logs = detectFraud(employeeId, billId);

        if (amount != null) {
            FraudDetectionLog roundAmountLog = new FraudDetectionLog();
            roundAmountLog.setEmployeeId(employeeId);
            roundAmountLog.setBillId(billId);
            roundAmountLog.setDetectionType("ROUND_AMOUNT");
            if (amount.doubleValue() >= 10000 && amount.doubleValue() % 10000 == 0) {
                roundAmountLog.setDetectionResult("SUSPICIOUS");
                roundAmountLog.setRiskScore(new BigDecimal("35"));
                roundAmountLog.setDetails("金额为整万数: " + amount + "，可能存在金额操纵");
                roundAmountLog.setActionTaken("WARN");
            } else if (amount.doubleValue() >= 1000 && amount.doubleValue() % 1000 == 0) {
                roundAmountLog.setDetectionResult("NORMAL");
                roundAmountLog.setRiskScore(new BigDecimal("15"));
                roundAmountLog.setDetails("金额为整千数: " + amount);
                roundAmountLog.setActionTaken("MONITOR");
            } else {
                roundAmountLog.setDetectionResult("NORMAL");
                roundAmountLog.setRiskScore(BigDecimal.ZERO);
                roundAmountLog.setDetails("金额检测通过");
                roundAmountLog.setActionTaken("ALLOW");
            }
            fraudDetectionLogMapper.insert(roundAmountLog);
            logs.add(roundAmountLog);

            if (amount.compareTo(new BigDecimal("100000")) > 0) {
                FraudDetectionLog highAmountLog = new FraudDetectionLog();
                highAmountLog.setEmployeeId(employeeId);
                highAmountLog.setBillId(billId);
                highAmountLog.setDetectionType("HIGH_AMOUNT");
                highAmountLog.setDetectionResult("SUSPICIOUS");
                highAmountLog.setRiskScore(new BigDecimal("45"));
                highAmountLog.setDetails("大额报销: " + amount + "，需关注");
                highAmountLog.setActionTaken("WARN");
                fraudDetectionLogMapper.insert(highAmountLog);
                logs.add(highAmountLog);
            }
        }

        if (supplierCode != null && !supplierCode.isBlank()) {
            FraudDetectionLog supplierLog = new FraudDetectionLog();
            supplierLog.setEmployeeId(employeeId);
            supplierLog.setBillId(billId);
            supplierLog.setDetectionType("SUPPLIER_RISK");
            supplierLog.setDetectionResult("NORMAL");
            supplierLog.setRiskScore(BigDecimal.ZERO);
            supplierLog.setDetails("供应商检测: " + supplierCode);
            supplierLog.setActionTaken("ALLOW");
            fraudDetectionLogMapper.insert(supplierLog);
            logs.add(supplierLog);
        }

        if ("ENTERTAINMENT".equals(expenseType) && amount != null
                && amount.compareTo(new BigDecimal("5000")) > 0) {
            FraudDetectionLog entertainLog = new FraudDetectionLog();
            entertainLog.setEmployeeId(employeeId);
            entertainLog.setBillId(billId);
            entertainLog.setDetectionType("ENTERTAINMENT");
            entertainLog.setDetectionResult("SUSPICIOUS");
            entertainLog.setRiskScore(new BigDecimal("40"));
            entertainLog.setDetails("招待费超过5000元: " + amount);
            entertainLog.setActionTaken("WARN");
            fraudDetectionLogMapper.insert(entertainLog);
            logs.add(entertainLog);
        }

        return logs;
    }

    public BigDecimal calculateTotalRiskScore(List<FraudDetectionLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (FraudDetectionLog log : logs) {
            if (log.getRiskScore() != null) {
                total = total.add(log.getRiskScore());
            }
        }
        return total;
    }

    // ==================== 增强检测（P1 增强） ====================

    private final ExpBillMapper expBillMapper;

    /**
     * 增强反欺诈检测：包含所有基础检测 + 高级模式分析。
     * 高级模式分析实时计算，不重复写入数据库只返回风险提示。
     */
    public List<FraudDetectionLog> enhancedDetect(Long employeeId, Long billId,
                                                    BigDecimal amount, String supplierCode,
                                                    String expenseType, LocalDate expenseDate) {
        // 基础检测
        List<FraudDetectionLog> logs = detectFraudWithDetails(employeeId, billId, amount, supplierCode, expenseType);

        // 同一供应商频繁报销检测
        if (supplierCode != null && !supplierCode.isBlank()) {
            FraudDetectionLog sameSupplierLog = new FraudDetectionLog();
            sameSupplierLog.setEmployeeId(employeeId);
            sameSupplierLog.setBillId(billId);
            sameSupplierLog.setDetectionType("SAME_SUPPLIER");
            sameSupplierLog.setCreateTime(LocalDateTime.now());

            LambdaQueryWrapper<FraudDetectionLog> supplierQw = new LambdaQueryWrapper<>();
            supplierQw.eq(FraudDetectionLog::getEmployeeId, employeeId)
                    .eq(FraudDetectionLog::getDetectionType, "SUPPLIER_RISK")
                    .ge(FraudDetectionLog::getCreateTime, LocalDateTime.now().minusMonths(1));
            long recentSupplierCount = fraudDetectionLogMapper.selectCount(supplierQw);

            if (recentSupplierCount >= 5) {
                sameSupplierLog.setDetectionResult("SUSPICIOUS");
                sameSupplierLog.setRiskScore(new BigDecimal("55"));
                sameSupplierLog.setDetails("近1个月内与同一供应商交互 " + (recentSupplierCount + 1) + " 次，需关注利益冲突");
                sameSupplierLog.setActionTaken("WARN");
            } else {
                sameSupplierLog.setDetectionResult("NORMAL");
                sameSupplierLog.setRiskScore(BigDecimal.ZERO);
                sameSupplierLog.setDetails("供应商频率检测通过");
                sameSupplierLog.setActionTaken("ALLOW");
            }
            fraudDetectionLogMapper.insert(sameSupplierLog);
            logs.add(sameSupplierLog);
        }

        // 节假日/周末提交检测
        if (expenseDate != null) {
            FraudDetectionLog holidayLog = new FraudDetectionLog();
            holidayLog.setEmployeeId(employeeId);
            holidayLog.setBillId(billId);
            holidayLog.setDetectionType("HOLIDAY");
            holidayLog.setCreateTime(LocalDateTime.now());

            DayOfWeek dow = expenseDate.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                holidayLog.setDetectionResult("SUSPICIOUS");
                holidayLog.setRiskScore(new BigDecimal("30"));
                holidayLog.setDetails("费用发生在周末: " + expenseDate);
                holidayLog.setActionTaken("WARN");
            } else {
                holidayLog.setDetectionResult("NORMAL");
                holidayLog.setRiskScore(BigDecimal.ZERO);
                holidayLog.setDetails("日期检测通过");
                holidayLog.setActionTaken("ALLOW");
            }
            fraudDetectionLogMapper.insert(holidayLog);
            logs.add(holidayLog);
        }

        // 费用类型与金额匹配度检测
        if (amount != null && expenseType != null && !expenseType.isBlank()) {
            FraudDetectionLog typeAmountLog = new FraudDetectionLog();
            typeAmountLog.setEmployeeId(employeeId);
            typeAmountLog.setBillId(billId);
            typeAmountLog.setDetectionType("TYPE_AMOUNT_MISMATCH");
            typeAmountLog.setCreateTime(LocalDateTime.now());

            // 简单规则：差旅费单笔>50000 或招待费>50000 或办公用品>50000 视为异常
            boolean mismatch = false;
            String detail = "";
            if ("TRAVEL".equals(expenseType) && amount.compareTo(new BigDecimal("50000")) > 0) {
                mismatch = true;
                detail = "差旅费单笔 " + amount + " 元，超过50000元异常阈值";
            } else if ("ENTERTAINMENT".equals(expenseType) && amount.compareTo(new BigDecimal("50000")) > 0) {
                mismatch = true;
                detail = "招待费单笔 " + amount + " 元，超过50000元异常阈值";
            } else if ("OFFICE_SUPPLIES".equals(expenseType) && amount.compareTo(new BigDecimal("30000")) > 0) {
                mismatch = true;
                detail = "办公用品单笔 " + amount + " 元，超过30000元异常阈值";
            }

            if (mismatch) {
                typeAmountLog.setDetectionResult("SUSPICIOUS");
                typeAmountLog.setRiskScore(new BigDecimal("40"));
                typeAmountLog.setDetails(detail);
                typeAmountLog.setActionTaken("WARN");
            } else {
                typeAmountLog.setDetectionResult("NORMAL");
                typeAmountLog.setRiskScore(BigDecimal.ZERO);
                typeAmountLog.setDetails("费用类型金额匹配度检测通过");
                typeAmountLog.setActionTaken("ALLOW");
            }
            fraudDetectionLogMapper.insert(typeAmountLog);
            logs.add(typeAmountLog);
        }

        // 同一供应商同时段多笔检测
        if (supplierCode != null && !supplierCode.isBlank() && amount != null) {
            LambdaQueryWrapper<FraudDetectionLog> multiSupplierQw = new LambdaQueryWrapper<>();
            multiSupplierQw.eq(FraudDetectionLog::getEmployeeId, employeeId)
                    .eq(FraudDetectionLog::getDetectionType, "SUPPLIER_RISK")
                    .ge(FraudDetectionLog::getCreateTime, LocalDateTime.now().minusDays(3));
            long multiSupplierCount = fraudDetectionLogMapper.selectCount(multiSupplierQw);

            if (multiSupplierCount >= 3) {
                FraudDetectionLog multiLog = new FraudDetectionLog();
                multiLog.setEmployeeId(employeeId);
                multiLog.setBillId(billId);
                multiLog.setDetectionType("MULTI_SUPPLIER");
                multiLog.setDetectionResult("SUSPICIOUS");
                multiLog.setRiskScore(new BigDecimal("50"));
                multiLog.setDetails("3天内对同一供应商多次报销 (" + (multiSupplierCount + 1) + "次)，可能存在拆分报销");
                multiLog.setActionTaken("WARN");
                multiLog.setCreateTime(LocalDateTime.now());
                fraudDetectionLogMapper.insert(multiLog);
                logs.add(multiLog);
            }
        }

        log.info("增强反欺诈检测完成: employeeId={}, billId={}, 总检测条数={}",
                employeeId, billId, logs.size());
        return logs;
    }

    /** 获取欺诈检测统计摘要 */
    public Map<String, Object> getFraudSummary() {
        Map<String, Object> result = new LinkedHashMap<>();

        long total = fraudDetectionLogMapper.selectCount(new LambdaQueryWrapper<>());
        long highRisk = fraudDetectionLogMapper.selectCount(
                new LambdaQueryWrapper<FraudDetectionLog>().eq(FraudDetectionLog::getDetectionResult, "HIGH_RISK"));
        long suspicious = fraudDetectionLogMapper.selectCount(
                new LambdaQueryWrapper<FraudDetectionLog>().eq(FraudDetectionLog::getDetectionResult, "SUSPICIOUS"));
        long normal = fraudDetectionLogMapper.selectCount(
                new LambdaQueryWrapper<FraudDetectionLog>().eq(FraudDetectionLog::getDetectionResult, "NORMAL"));

        // 按检测类型分组
        QueryWrapper<FraudDetectionLog> typeQw = new QueryWrapper<>();
        typeQw.select("detection_type", "count(*) as cnt")
                .groupBy("detection_type")
                .orderByDesc("cnt");
        List<Map<String, Object>> byType = fraudDetectionLogMapper.selectMaps(typeQw);

        // 人工待审队列积压（三档处置的第二档：进队列不挡流程）
        long pendingReview = fraudDetectionLogMapper.selectCount(
                new LambdaQueryWrapper<FraudDetectionLog>()
                        .eq(FraudDetectionLog::getActionTaken, "REVIEW")
                        .isNull(FraudDetectionLog::getReviewedAt));

        result.put("total", total);
        result.put("highRisk", highRisk);
        result.put("suspicious", suspicious);
        result.put("normal", normal);
        result.put("pendingReview", pendingReview);
        result.put("riskRate", total > 0
                ? BigDecimal.valueOf((highRisk + suspicious) * 100.0 / total).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("byType", byType);

        return result;
    }

    // ==================== P2-5: 风险模型拦截/预警 ====================

    /**
     * P2-5: 提交时反欺诈检测 + 拦截/预警。
     * <p>
     * 在 {@link #detectFraud} 基础上聚合结果：
     * <ul>
     *  <li>HIGH_RISK → shouldBlock=true，调用方应抛异常阻断提交</li>
     *  <li>SUSPICIOUS → shouldWarn=true，调用方记录预警但放行</li>
     *  <li>全部 NORMAL → shouldBlock=false, shouldWarn=false</li>
     * </ul>
     *
     * @param employeeId 提交人ID
     * @param billId     单据ID
     * @return 聚合后的风险检查结果
     */
    @Transactional(rollbackFor = Exception.class)
    public FraudCheckResult checkAndBlock(Long employeeId, Long billId) {
        List<FraudDetectionLog> logs = detectFraud(employeeId, billId);

        boolean shouldBlock = false;
        boolean shouldWarn = false;
        BigDecimal maxScore = BigDecimal.ZERO;
        List<String> riskDetails = new ArrayList<>();

        for (FraudDetectionLog logEntry : logs) {
            if ("HIGH_RISK".equals(logEntry.getDetectionResult())) {
                shouldBlock = true;
                riskDetails.add("[" + logEntry.getDetectionType() + "] " + logEntry.getDetails());
                if (logEntry.getRiskScore() != null && logEntry.getRiskScore().compareTo(maxScore) > 0) {
                    maxScore = logEntry.getRiskScore();
                }
            } else if ("SUSPICIOUS".equals(logEntry.getDetectionResult())) {
                shouldWarn = true;
                riskDetails.add("[" + logEntry.getDetectionType() + "] " + logEntry.getDetails());
                if (logEntry.getRiskScore() != null && logEntry.getRiskScore().compareTo(maxScore) > 0) {
                    maxScore = logEntry.getRiskScore();
                }
            }
        }

        // 三档语义（批次 1 ⑤）：HIGH_RISK 默认只进人工待审队列（风控看板/复审接口），不再挡提单；
        // 硬阻断位在付款指令执行前（{@link #paymentBlockReason}）。要回退旧行为把
        // sys_config.fraud.block_on_submit.enabled 置 1；演练期观察误拦率置 fraud.shadow_mode.enabled=1。
        boolean blockAtSubmit = shouldBlock && isSubmitBlockEnabled();
        if (shouldBlock && !blockAtSubmit) {
            shouldWarn = true;
            log.warn("反欺诈高风险（转人工待审，不挡提单）employeeId={}, billId={}, 明细={}",
                    employeeId, billId, riskDetails);
        }

        String maxRiskLevel = shouldBlock ? "HIGH" : (shouldWarn ? "MEDIUM" : "LOW");
        String summary = riskDetails.isEmpty() ? "无风险" : String.join("；", riskDetails);

        log.info("P2-5 风险拦截检测: employeeId={}, billId={}, riskLevel={}, shouldBlock={}, shouldWarn={}",
                employeeId, billId, maxRiskLevel, blockAtSubmit, shouldWarn);

        return new FraudCheckResult(blockAtSubmit, shouldWarn, maxRiskLevel, maxScore, summary, logs);
    }

    /** P2-5: 风险检查聚合结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class FraudCheckResult {
        /** HIGH_RISK 命中<b>且提单侧阻断已开启</b>（{@code fraud.block_on_submit.enabled=1}）→ 阻断提交 */
        private boolean shouldBlock;
        /** SUSPICIOUS 命中，或高风险转人工待审 → 预警放行 */
        private boolean shouldWarn;
        /** 最高风险等级 LOW/MEDIUM/HIGH */
        private String maxRiskLevel;
        /** 最高风险分值 */
        private BigDecimal maxRiskScore;
        /** 风险摘要 */
        private String summary;
        /** 原始检测明细 */
        private List<FraudDetectionLog> logs;
    }
}