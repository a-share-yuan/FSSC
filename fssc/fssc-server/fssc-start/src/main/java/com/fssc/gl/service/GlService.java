// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.gl.entity.GlEntry;
import com.fssc.gl.mapper.GlEntryMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 总账模块。凭证 POSTED 时落账到 op_gl_entry（所有 EER / PTP 凭证统一入账）。
 * 余额与试算平衡通过明细实时聚合，天然支持反向凭证、无冲销增量回滚的脏数据风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlService {

    public static final String DEBIT = "DEBIT";
    public static final String CREDIT = "CREDIT";

    private final GlEntryMapper glEntryMapper;
    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    /** 落账时把过账人姓名冗余进分录（列待产品确认后执行 DDL，见 {@code GlEntry}） */
    private final SysUserMapper sysUserMapper;

    /**
     * 凭证 POSTED 落账，按凭证明细行逐行写入总账。去重：同凭证先清理再写入。
     *
     * <p>过账人从凭证头 {@code post_user_id} 取（见 {@link #postVoucherToGl(Long, Long)}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int postVoucherToGl(Long voucherId) {
        return postVoucherToGl(voucherId, null);
    }

    /**
     * 凭证 POSTED 落账（分录侧带过账人）。
     *
     * @param operatorId 触发落账的操作人；为空时回退凭证头 {@code op_voucher.post_user_id}——
     *                   该列由 {@code VoucherService.post()} 在落账<b>之前</b>写好，
     *                   故 {@code /op/gl/repost} 这类"换人补落账"的入口不必再传，
     *                   落进来的仍是<b>当初过账那个人</b>，而不是碰巧点按钮的人。
     */
    @Transactional(rollbackFor = Exception.class)
    public int postVoucherToGl(Long voucherId, Long operatorId) {
        OpVoucher v = opVoucherMapper.selectById(voucherId);
        if (v == null) {
            return 0;
        }
        // 去重：重复 POSTED/重跑先清旧行
        glEntryMapper.delete(new LambdaQueryWrapper<GlEntry>().eq(GlEntry::getVoucherId, voucherId));
        List<OpVoucherLine> lines = opVoucherLineMapper.selectByVoucherId(voucherId);
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        // 过账人落到每一行分录上（列未写入数据库前这两个值不会写库，见 GlEntry 的注释态 DDL）
        Long poster = operatorId != null ? operatorId : v.getPostUserId();
        String posterName = poster == null ? null : userName(poster);
        for (OpVoucherLine ln : lines) {
            if (ln.getAmount() == null) {
                continue;
            }
            GlEntry e = new GlEntry();
            e.setTenantId(v.getTenantId());
            e.setCompanyId(v.getCompanyId());
            e.setPeriodYear(v.getPeriodYear());
            e.setPeriodMonth(v.getPeriodMonth());
            e.setVoucherId(v.getId());
            e.setVoucherNo(v.getVoucherNo());
            e.setVoucherLineId(ln.getId());
            e.setSubjectId(ln.getSubjectId());
            e.setSubjectCode(ln.getSubjectCode() == null ? "" : ln.getSubjectCode());
            e.setSubjectName(ln.getSubjectName() == null ? "" : ln.getSubjectName());
            e.setDirection(ln.getDirection());
            e.setAmount(ln.getAmount());
            e.setSummary(ln.getSummary() != null ? ln.getSummary() : v.getSummary());
            e.setBizId(v.getBillId());
            e.setBizType(v.getSourceType());
            e.setPostUserId(poster);
            e.setPostUserName(posterName);
            e.setCreateTime(now);
            e.setUpdateTime(now);
            glEntryMapper.insert(e);
        }
        log.info("总账落账完成：凭证 {} 分录 {} 条，期间 {}-{}，公司 {}，过账人 {}{}",
                v.getVoucherNo(), lines.size(), v.getPeriodYear(), v.getPeriodMonth(), v.getCompanyId(),
                poster == null ? "未知（凭证头 post_user_id 为空）" : poster,
                posterName == null ? "" : "/" + posterName);
        return lines.size();
    }

    /** 过账人姓名（分录侧冗余一份，防人员离职后只剩裸 ID 查不到人）；查不到就留空不编 */
    private String userName(Long userId) {
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null) {
                return null;
            }
            return u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName();
        } catch (Exception ex) {
            log.warn("总账落账取过账人姓名失败 userId={}: {}", userId, ex.getMessage());
            return null;
        }
    }

    /** 凭证是否已落账 */
    public boolean isPostedToGl(Long voucherId) {
        return glEntryMapper.selectCount(
                new LambdaQueryWrapper<GlEntry>().eq(GlEntry::getVoucherId, voucherId)) > 0;
    }

    /**
     * 一页凭证的落账分录数批量聚合——SQL 侧按 {@code voucher_id} 分组计数，<b>单条查询</b>
     * 返回整页读数，供凭证列表接口一次性带回落账态（{@code glEntryCount}），取代页面逐张
     * /op/gl/posted 的 N+1 串行探测（该探测失败还曾被降级显示成「未落账」，根因）。
     *
     * <p>租户过滤由框架层拦截器自动追加（与 {@link #isPostedToGl} 同一张表、同一规则）；
     * 未出现在结果里的凭证＝库内没有落账分录，由调用方按 0（未落账）落值——
     * 与"逐张探测得 0 行"语义一致，但一页只发一条 SQL。
     *
     * @param voucherIds 本页凭证 ID 集（空集直接返回空 Map，不发 SQL）
     * @return voucherId → op_gl_entry 行数（仅含计数 &gt; 0 的凭证）
     */
    public Map<Long, Integer> countGlEntriesByVoucherIds(Collection<Long> voucherIds) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = glEntryMapper.selectMaps(new QueryWrapper<GlEntry>()
                .select("voucher_id AS vid", "COUNT(*) AS cnt")
                .in("voucher_id", voucherIds)
                .groupBy("voucher_id"));
        Map<Long, Integer> countById = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long vid = null;
            Integer cnt = 0;
            for (Map.Entry<String, Object> en : row.entrySet()) {
                String key = en.getKey() == null ? "" : en.getKey().toLowerCase();
                if ("vid".equals(key) && en.getValue() instanceof Number nv) {
                    vid = nv.longValue();
                } else if ("cnt".equals(key) && en.getValue() instanceof Number nc) {
                    cnt = nc.intValue();
                }
            }
            if (vid != null) {
                countById.put(vid, cnt);
            }
        }
        return countById;
    }

    /** 总账分录分页（凭证行为粒度） */
    public PageResult<GlEntry> page(long pageNum, long pageSize,
                                    Long companyId, Integer year, Integer month, Long subjectId) {
        return page(pageNum, pageSize, companyId, year, month, subjectId, null, null);
    }

    /**
     * 总账分录分页（支持列头服务端排序）。
     *
     * <p>背景：前端列头若只挂裸 {@code sortable}——element-plus 只对「当前页 20 行」做前端排序，
     * 翻页即乱序。排序收到后端：{@code orderBy} 为前端列 prop（camelCase），
     * <b>仅白名单列</b>映射到实体 getter（{@link #sortColumnOf}），非白名单值一律忽略回退默认序；
     * {@code order} 仅识别 desc（其余按 asc）。排序值不进入任何 SQL 字面量拼接，无注入面。
     */
    public PageResult<GlEntry> page(long pageNum, long pageSize,
                                    Long companyId, Integer year, Integer month, Long subjectId,
                                    String orderBy, String order) {
        LambdaQueryWrapper<GlEntry> qw = new LambdaQueryWrapper<>();
        if (companyId != null) {
            qw.eq(GlEntry::getCompanyId, companyId);
        }
        if (year != null) {
            qw.eq(GlEntry::getPeriodYear, year);
        }
        if (month != null) {
            qw.eq(GlEntry::getPeriodMonth, month);
        }
        if (subjectId != null) {
            qw.eq(GlEntry::getSubjectId, subjectId);
        }
        SFunction<GlEntry, ?> sortCol = sortColumnOf(orderBy);
        if (sortCol != null) {
            boolean asc = !"desc".equalsIgnoreCase(order);
            qw.orderBy(true, asc, sortCol);
            // 排序列同值（如方向/科目）时以 id 作稳定次级序，避免翻页出现重复/漂移
            qw.orderByAsc(GlEntry::getId);
        } else {
            qw.orderByAsc(GlEntry::getId);
        }
        Page<GlEntry> p = glEntryMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** orderBy 前端列 prop（camelCase）→ 实体列白名单；白名单外的值返回 null（回退默认 id 升序）。 */
    private static SFunction<GlEntry, ?> sortColumnOf(String orderBy) {
        if (orderBy == null) {
            return null;
        }
        return switch (orderBy.trim()) {
            case "voucherNo" -> GlEntry::getVoucherNo;
            case "voucherId" -> GlEntry::getVoucherId;
            case "subjectCode" -> GlEntry::getSubjectCode;
            case "subjectName" -> GlEntry::getSubjectName;
            case "direction" -> GlEntry::getDirection;
            case "amount" -> GlEntry::getAmount;
            case "summary" -> GlEntry::getSummary;
            case "bizId" -> GlEntry::getBizId;
            default -> null;
        };
    }

    /**
     * 总账科目余额（含期初）。期初 = 该科目在小于本期期间的全部发生合计；
     * 本期借/贷 = 本期发生；期末 = 期初 + 本期借 - 本期贷，返回方向与绝对值。
     *
     * <p><b>科目余额表合计借贷不平</b>：本方法原先<b>只遍历本期发生行</b>，
     * 期初集合只是拿来"按编码查一下"——于是<b>只有期初余额、本期没有发生</b>的科目整行消失，
     * 它的期初金额也跟着凭空不见，合计行自然对不平——这些科目在借、贷两侧缺的金额并不对等，
     * 差额就落在合计行上；换到本期无发生的期间更是如此，
     * 整张表直接变<b>空白</b>。
     * <b>科目全集 = 本期发生 ∪ 期初余额</b>，两边都有的合并成一行。
     *
     * <p>同轮把<b>期初也按科目方向规范化</b>（净额落借或贷一边，与期末同一规则）：
     * 原先期初借/期初贷直接取发生额合计，同一科目两边同时挂数（如 1602 累计折旧期初借 501,000 又贷 4,591.67），
     * 资产类科目还会出现"期初贷方余额"这种方向异常读数——那是<b> gross 发生额被当成余额</b>的规则错，
     * 不是账错了。净额化后 {@code 期初借合计 = 期初贷合计}（每份凭证借贷等额、且全部科目都在表内 ⇒
     * 各科目净额之和恒为 0），期末同理，本期发生额保持借贷分列的 gross 规则（报表惯例）。
     *
     * <p>行数放大是有意的：科目余额表本来就该列出所有有余额的科目。净额化不改变任何科目的
     * {@code 期末}（{@code 期初借-期初贷} 的差不变量），故唯一下游
     * {@code GlProfitCarryService}（只读 {@code periodDebit/periodCredit} 判损益发生额）行为不变。
     */
    public List<Map<String, Object>> balance(Long companyId, int year, int month) {
        List<Map<String, Object>> period = sumGroup(companyId,
                new QueryWrapper<GlEntry>()
                        .eq("company_id", companyId)
                        .eq("period_year", year)
                        .eq("period_month", month));
        List<Map<String, Object>> opening = sumGroup(companyId,
                new QueryWrapper<GlEntry>()
                        .eq("company_id", companyId)
                        .apply("(period_year < {0} OR (period_year = {0} AND period_month < {1}))", year, month));
        // code -> [期初借, 期初贷, 本期借, 本期贷]；TreeMap = 按科目编码升序出表（原来跟 GROUP BY 的随机序）
        Map<String, BigDecimal[]> amounts = new TreeMap<>();
        Map<String, Object[]> labels = new HashMap<>();
        accumulate(amounts, labels, opening, 0);
        accumulate(amounts, labels, period, 2);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> en : amounts.entrySet()) {
            String code = en.getKey();
            BigDecimal[] a = en.getValue();
            BigDecimal ob = a[0], oc = a[1], debit = a[2], credit = a[3];
            BigDecimal openNet = ob.subtract(oc);
            BigDecimal obShown = openNet.signum() >= 0 ? openNet : BigDecimal.ZERO;
            BigDecimal ocShown = openNet.signum() >= 0 ? BigDecimal.ZERO : openNet.negate();
            BigDecimal net = openNet.add(debit).subtract(credit);
            boolean isDebit = net.compareTo(BigDecimal.ZERO) >= 0;
            Object[] label = labels.get(code);
            Map<String, Object> m = new HashMap<>();
            m.put("subjectId", label == null ? null : label[0]);
            m.put("subjectCode", code);
            m.put("subjectName", label == null ? "" : str(label[1]));
            m.put("openDebit", obShown);
            m.put("openCredit", ocShown);
            m.put("periodDebit", debit);
            m.put("periodCredit", credit);
            m.put("endDebit", isDebit ? net : BigDecimal.ZERO);
            m.put("endCredit", isDebit ? BigDecimal.ZERO : net.negate());
            m.put("direction", isDebit ? DEBIT : CREDIT);
            result.add(m);
        }
        return result;
    }

    /** 把一组聚合行并进余额表的累加器：offset 0=期初两列，2=本期两列；同一编码多行（改过名/换过 id）合并 */
    private void accumulate(Map<String, BigDecimal[]> amounts, Map<String, Object[]> labels,
                            List<Map<String, Object>> rows, int offset) {
        for (Map<String, Object> row : rows) {
            String code = str(row.get("pcode"));
            Object[] known = labels.get(code);
            if (known == null) {
                labels.put(code, new Object[]{row.get("pid"), row.get("pname")});
            } else if (known[0] == null && row.get("pid") != null) {
                // 期初行没带出 id（历史脏数据）时让本期行补上，前端"点科目看明细"才有点得动
                known[0] = row.get("pid");
                known[1] = row.get("pname");
            }
            BigDecimal[] a = amounts.computeIfAbsent(code, k -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            a[offset] = a[offset].add(dec(row.get("debit")));
            a[offset + 1] = a[offset + 1].add(dec(row.get("credit")));
        }
    }

    /**
     * 试算平衡：统计本期（公司+期间）借方合计、贷方合计及差异。
     * 借贷合计相等且差异为 0 即平衡。
     */
    public Map<String, Object> trialBalance(Long companyId, int year, int month) {
        return trialBalance(companyId, year, month, false);
    }

    /**
     * 试算平衡（可含未过账凭证）。
     *
     * <p>{@code op_gl_entry} 只在凭证过账时写入，故默认口径＝本期已过账分录合计。
     * {@code includeUnposted} 为 true 时，另把本期间内状态为 DRAFT 的凭证借贷合计
     * 分别加到两侧，并把未过账金额与张数单独回传，供界面明示"两个来源"。
     *
     * @param includeUnposted true＝含未过账凭证；默认 false（月结校验沿用此默认）
     */
    public Map<String, Object> trialBalance(Long companyId, int year, int month, boolean includeUnposted) {
        QueryWrapper<GlEntry> qw = new QueryWrapper<GlEntry>()
                .eq("company_id", companyId)
                .eq("period_year", year)
                .eq("period_month", month)
                .select("SUM(CASE WHEN direction='DEBIT' THEN amount ELSE 0 END) debit",
                        "SUM(CASE WHEN direction='CREDIT' THEN amount ELSE 0 END) credit",
                        "COUNT(*) AS `rows`");
        List<Map<String, Object>> postedRows = glEntryMapper.selectMaps(qw);
        Map<String, Object> r = postedRows.isEmpty() ? Map.of("debit", ZERO, "credit", ZERO) : postedRows.get(0);
        BigDecimal postedDebit = dec(r.get("debit"));
        BigDecimal postedCredit = dec(r.get("credit"));
        BigDecimal unpostedDebit = ZERO;
        BigDecimal unpostedCredit = ZERO;
        long unpostedVouchers = 0L;
        if (includeUnposted) {
            QueryWrapper<OpVoucher> vq = new QueryWrapper<OpVoucher>()
                    .eq("company_id", companyId)
                    .eq("period_year", year)
                    .eq("period_month", month)
                    .eq("status", "DRAFT")
                    .select("SUM(debit_total) debit", "SUM(credit_total) credit", "COUNT(*) AS `rows`");
            List<Map<String, Object>> draftRows = opVoucherMapper.selectMaps(vq);
            Map<String, Object> d = draftRows.isEmpty() ? Map.of() : draftRows.get(0);
            unpostedDebit = dec(d.get("debit"));
            unpostedCredit = dec(d.get("credit"));
            Object cnt = d.get("rows");
            unpostedVouchers = cnt instanceof Number n ? n.longValue() : 0L;
        }
        BigDecimal debit = postedDebit.add(unpostedDebit);
        BigDecimal credit = postedCredit.add(unpostedCredit);
        BigDecimal diff = debit.subtract(credit);
        Map<String, Object> out = new HashMap<>();
        out.put("companyId", companyId);
        out.put("periodYear", year);
        out.put("periodMonth", month);
        out.put("debitTotal", debit);
        out.put("creditTotal", credit);
        out.put("diff", diff);
        out.put("balanced", diff.compareTo(BigDecimal.ZERO) == 0);
        out.put("includeUnposted", includeUnposted);
        out.put("postedDebitTotal", postedDebit);
        out.put("postedCreditTotal", postedCredit);
        out.put("unpostedDebitTotal", unpostedDebit);
        out.put("unpostedCreditTotal", unpostedCredit);
        out.put("unpostedVoucherCount", unpostedVouchers);
        return out;
    }

    // 工具

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private List<Map<String, Object>> sumGroup(Long companyId, QueryWrapper<GlEntry> qw) {
        qw.select("subject_id pid", "subject_code pcode", "subject_name pname",
                        "SUM(CASE WHEN direction='DEBIT' THEN amount ELSE 0 END) debit",
                        "SUM(CASE WHEN direction='CREDIT' THEN amount ELSE 0 END) credit")
                .groupBy("subject_id", "subject_code", "subject_name");
        return glEntryMapper.selectMaps(qw);
    }

    private BigDecimal dec(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return ZERO;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}