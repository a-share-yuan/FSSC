package com.fssc.ai.skill;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 槽位抽取：金额、日期/期间、单据状态、单号、单据类型。
 *
 * <p>全部是确定性规则，不依赖模型；抽不到就返回 null（不猜）。写技能的金额允许"裸数字"兜底，
 * 但必须先剥掉单号与日期里的数字，避免把 BX012025120003 里的 202512 当成金额。
 */
@Component
public class SlotExtractor {

    public record DateRange(LocalDate from, LocalDate to, String label) {
    }

    public static class Slots {
        public BigDecimal amount;
        public String amountText;
        public DateRange date;
        public String status;
        public String billNo;
        public String billType;
        public String reason;
    }

    private static final Pattern AMOUNT_WITH_UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(万元|万|元|块|块钱|圆|RMB|rmb|人民币)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CN_AMOUNT = Pattern.compile(
            "([零一二两三四五六七八九十百千万]+)\\s*(万元|万|元|块|块钱|圆)");
    private static final Pattern BILL_NO = Pattern.compile("[A-Z]{2,}[0-9]{6,}");
    private static final Pattern MONTH = Pattern.compile("(20\\d{2})\\s*[-年/]\\s*(\\d{1,2})\\s*月?");
    private static final Pattern RECENT_DAYS = Pattern.compile("(?:最近|近)\\s*(\\d{1,3})\\s*天");
    private static final Pattern DAYS_AGO = Pattern.compile("(\\d{1,3})\\s*天前");
    private static final Pattern RECENT_MONTHS = Pattern.compile("(?:最近|近)\\s*(\\d{1,2})\\s*个?月");
    private static final Pattern REASON = Pattern.compile("(?:事由|用途|用于|因为)\\s*[:：]?\\s*([^，。；;]{2,40})");

    private static final Map<String, String> STATUS = new LinkedHashMap<>();

    static {
        STATUS.put("草稿", "DRAFT");
        STATUS.put("未提交", "DRAFT");
        STATUS.put("已批准", "APPROVED");
        STATUS.put("已通过", "APPROVED");
        STATUS.put("审批通过", "APPROVED");
        STATUS.put("已驳回", "REJECTED");
        STATUS.put("被驳回", "REJECTED");
        STATUS.put("已作废", "VOID");
        STATUS.put("已撤回", "WITHDRAWN");
        STATUS.put("已付款", "PAID");
        STATUS.put("已记账", "POSTED");
        STATUS.put("已完成", "COMPLETED");
        STATUS.put("已还款", "REPAID");
        STATUS.put("已关闭", "CLOSED");
    }

    public Slots extract(String q) {
        Slots s = new Slots();
        String text = q == null ? "" : q;
        Matcher bn = BILL_NO.matcher(text);
        if (bn.find()) {
            s.billNo = bn.group();
            text = text.replace(s.billNo, " ");
        }
        s.date = extractDate(text);
        // 日期段里的数字不能当金额：先把命中片段抹掉再找金额
        String forAmount = text;
        if (s.date != null) {
            Matcher m = MONTH.matcher(forAmount);
            forAmount = m.replaceAll(" ");
            forAmount = forAmount.replaceAll("20\\d{2}\\s*年", " ");
        }
        s.amount = extractAmount(forAmount);
        if (s.amount != null) {
            s.amountText = s.amount.stripTrailingZeros().toPlainString();
        }
        s.status = statusOf(text);
        s.billType = billTypeOf(text);
        Matcher r = REASON.matcher(text);
        if (r.find()) {
            s.reason = r.group(1).trim();
        }
        return s;
    }

    /** 写技能的金额兜底：剥掉单号与日期后，句子里只剩一个数字才认。 */
    public BigDecimal extractLooseAmount(String q) {
        if (q == null) {
            return null;
        }
        String text = BILL_NO.matcher(q).replaceAll(" ");
        Matcher m = MONTH.matcher(text);
        text = m.replaceAll(" ");
        text = text.replaceAll("20\\d{2}\\s*年", " ");
        Matcher dm = Pattern.compile("\\d{1,3}\\s*天").matcher(text);
        text = dm.replaceAll(" ");
        // 「10月的报销单」里的 10 是月份，不是金额
        text = text.replaceAll("\\d{1,2}\\s*月", " ");
        Matcher nums = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)(?![\\d.])").matcher(text);
        BigDecimal only = null;
        int count = 0;
        while (nums.find()) {
            count++;
            try {
                only = new BigDecimal(nums.group(1));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return count == 1 ? only.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal extractAmount(String text) {
        Matcher m = AMOUNT_WITH_UNIT.matcher(text);
        if (m.find()) {
            BigDecimal v = new BigDecimal(m.group(1));
            String unit = m.group(2);
            if ("万元".equals(unit) || "万".equals(unit)) {
                v = v.multiply(BigDecimal.valueOf(10000));
            }
            return v.setScale(2, RoundingMode.HALF_UP);
        }
        Matcher c = CN_AMOUNT.matcher(text);
        if (c.find()) {
            BigDecimal v = cnToNumber(c.group(1));
            if (v == null) {
                return null;
            }
            if ("万元".equals(c.group(2)) || "万".equals(c.group(2))) {
                v = v.multiply(BigDecimal.valueOf(10000));
            }
            return v.setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** 中文数字（金额量级）：一千=1000，一万五=15000，两百=200。 */
    static BigDecimal cnToNumber(String raw) {
        String s = raw.replace("两", "二");
        if (s.isEmpty()) {
            return null;
        }
        // 口语「一万五」= 15000（万后的单个数字按千计）
        s = s.replaceAll("万([一二三四五六七八九])$", "万$1千");
        long total = 0;
        long section = 0;
        long number = 0;
        for (char ch : s.toCharArray()) {
            int d = switch (ch) {
                case '零' -> 0;
                case '一' -> 1;
                case '二' -> 2;
                case '三' -> 3;
                case '四' -> 4;
                case '五' -> 5;
                case '六' -> 6;
                case '七' -> 7;
                case '八' -> 8;
                case '九' -> 9;
                default -> -1;
            };
            if (d >= 0) {
                number = d;
                continue;
            }
            switch (ch) {
                case '十' -> {
                    if (number == 0) number = 1;
                    section += number * 10;
                    number = 0;
                }
                case '百' -> {
                    section += number * 100;
                    number = 0;
                }
                case '千' -> {
                    section += number * 1000;
                    number = 0;
                }
                case '万' -> {
                    section = (section + number) * 10000;
                    total += section;
                    section = 0;
                    number = 0;
                }
                default -> {
                    return null;
                }
            }
        }
        long v = total + section + number;
        return v <= 0 ? null : BigDecimal.valueOf(v);
    }

    private DateRange extractDate(String q) {
        LocalDate today = LocalDate.now();
        if (q.contains("今天") || q.contains("今日")) {
            return new DateRange(today, today, "今天");
        }
        if (q.contains("昨天")) {
            LocalDate d = today.minusDays(1);
            return new DateRange(d, d, "昨天");
        }
        if (q.contains("前天")) {
            LocalDate d = today.minusDays(2);
            return new DateRange(d, d, "前天");
        }
        Matcher rd = RECENT_DAYS.matcher(q);
        if (rd.find()) {
            int n = Integer.parseInt(rd.group(1));
            return new DateRange(today.minusDays(Math.max(0, n - 1)), today, "最近" + n + "天");
        }
        Matcher da = DAYS_AGO.matcher(q);
        if (da.find()) {
            LocalDate d = today.minusDays(Integer.parseInt(da.group(1)));
            return new DateRange(d, d, da.group(1) + "天前");
        }
        Matcher rm = RECENT_MONTHS.matcher(q);
        if (rm.find()) {
            int n = Integer.parseInt(rm.group(1));
            return new DateRange(today.minusMonths(n).plusDays(1), today, "最近" + n + "个月");
        }
        if (q.contains("本周") || q.contains("这周") || q.contains("本星期")) {
            LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(from, from.plusDays(6), "本周");
        }
        if (q.contains("上周")) {
            LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            return new DateRange(from, from.plusDays(6), "上周");
        }
        if (q.contains("上个月") || q.contains("上月")) {
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            return new DateRange(first, first.withDayOfMonth(first.lengthOfMonth()), "上个月");
        }
        if (q.contains("本月") || q.contains("这个月") || q.contains("当月")) {
            LocalDate first = today.withDayOfMonth(1);
            return new DateRange(first, first.withDayOfMonth(first.lengthOfMonth()), "本月");
        }
        if (q.contains("上季度") || q.contains("上个季度")) {
            int qm = (today.getMonthValue() - 1) / 3; // 1..4
            LocalDate start = LocalDate.of(today.getYear(), qm * 3 - 2, 1).minusMonths(3);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return new DateRange(start, end, "上季度");
        }
        if (q.contains("本季度") || q.contains("这个季度") || q.contains("当季")) {
            int startMonth = (today.getMonthValue() - 1) / 3 * 3 + 1;
            LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
            return new DateRange(start, start.plusMonths(3).minusDays(1), "本季度");
        }
        if (q.contains("去年")) {
            int y = today.getYear() - 1;
            return new DateRange(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), "去年");
        }
        if (q.contains("今年") || q.contains("本年")) {
            int y = today.getYear();
            return new DateRange(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), "今年");
        }
        Matcher m = MONTH.matcher(q);
        if (m.find()) {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            if (mo >= 1 && mo <= 12) {
                LocalDate from = LocalDate.of(y, mo, 1);
                return new DateRange(from, from.withDayOfMonth(from.lengthOfMonth()), y + "年" + mo + "月");
            }
        }
        return null;
    }

    private String statusOf(String q) {
        for (Map.Entry<String, String> e : STATUS.entrySet()) {
            if (q.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private String billTypeOf(String q) {
        if (q.contains("借款") || q.contains("借支") || q.contains("备用金")) return "LOAN";
        if (q.contains("还款")) return "REPAY";
        if (q.contains("报销") || q.contains("报账")) return "CLAIM";
        if (q.contains("对公付款") || q.contains("付款单")) return "PAY";
        return null;
    }
}
