// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.skill;

import com.fssc.ai.memory.SessionStore;
import com.fssc.ai.skill.SlotExtractor.Slots;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 确定性技能路由：先于模型执行，零外部依赖。
 *
 * <p>判据不是"多堆关键词"，而是结构化槽位：金额/日期/状态/单号从问句抽出后交给技能声明的参数映射，
 * 产出可直接调用的动作参数。模型只在这层匹配不到时接管长尾；写技能始终只出票、不执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRouter {

    private static final Pattern CHANGE = Pattern.compile(
            "(?:金额)?(?:改成|改为|修改为|变成|调整为|换成)\\s*(\\d+(?:\\.\\d+)?)\\s*(万元|万|元|块|块钱)?");
    private static final Pattern AGAIN = Pattern.compile(
            "再(?:填|来|开|建|做)一张|再来一张|再填一张|再开一张|再建一张|照这样再|同样再|和上次一样");
    /** 查询语气：命中即不按"办理"理解（避免把读问题当成写指令出票）。 */
    private static final String[] READ_INTENT = {
            "有哪些", "哪几", "多少", "列表", "明细", "台账", "余额", "记录", "历史", "统计", "汇总", "报表", "总数", "数量"};
    /** 写技能金额兜底：句子里只剩一个裸数字时才认（单位缺失的"填写1000"）。 */
    private static final Pattern LOOSE_WRITE_AMOUNT = Pattern.compile(
            "(?:金额|额度|填写|填|借|报销|报账|还款)\\s*(?:为|是|：|:)?\\s*\\d+(?:\\.\\d+)?");

    private final SkillRegistry registry;
    private final SlotExtractor slotExtractor;

    /**
     * @param permBlocked true 表示技能本身命中了语义，但它的 backing 动作不在本人可见范围
     *                    权限不足。此时不执行、也不猜别的动作，改为如实报 NO_PERM。
     */
    public record Match(Skill skill, Map<String, Object> args, String reason, BigDecimal amount,
                        boolean inherited, String navRoute, boolean permBlocked) {
        public Match(Skill skill, Map<String, Object> args, String reason, BigDecimal amount,
                     boolean inherited, String navRoute) {
            this(skill, args, reason, amount, inherited, navRoute, false);
        }
    }

    /**
     * @param q 用户原话
     * @param withWrites 指令通道=true；问数通道=false（写技能不参与）
     * @param visibleCodes 本人可见的目录动作码（技能的全部 backing 必须都在其中）
     * @param ses 会话状态（可为空状态对象）
     */
    public Match route(String q, boolean withWrites, Set<String> visibleCodes, Slots slots,
                       SessionStore.SessionState ses) {
        if (q == null || q.isBlank()) {
            return null;
        }
        Match refine = refine(q, withWrites, visibleCodes, slots, ses);
        if (refine != null) {
            return refine;
        }

        Skill best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        Skill blocked = null;
        int blockedScore = -1;
        for (Skill s : registry.all()) {
            if (s.isWrite() && !withWrites) {
                continue;
            }
            if (s.excluded(q)) {
                continue;
            }
            int kw = hits(q, s.getKeywords());
            int vb = hits(q, s.getVerbs());
            if (!eligible(s, q, kw, vb, slots)) {
                continue;
            }
            int score = kw * 10 + vb * 6 + s.getPriority();
            if (s.isNav() && navIntent(q)) {
                score += 60; // 明确问"哪个页面/在哪儿"时，导航优先于同名列表
            }
            if (!visible(s, visibleCodes)) {
                // 语义命中但本人无权：记下来备查（宁可不答，也不拿别的动作顶替）
                if (score > blockedScore) {
                    blocked = s;
                    blockedScore = score;
                }
                continue;
            }
            if (score > bestScore) {
                best = s;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore && best != null && !best.getName().equals(s.getName())) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            // 同分不同技能：不猜，交给模型/拒答链；宁可不答也不随机选一个
            log.info("AI 技能路由歧义（不猜）：question={} best={} score={}",
                    q, best == null ? null : best.getName(), bestScore);
            return null;
        }
        if (best != null) {
            return build(best, q, slots, withWrites);
        }
        if (blocked != null) {
            return new Match(blocked, Map.of(),
                    "技能「" + blocked.getTitle() + "」对应的动作不在本人可见范围", null, false, null, true);
        }
        return null;
    }

    /**
     * 候选资格：
     * <ul>
     *   <li>写技能：必须同时命中"业务词"与"办理语气词"，且整句不是查询语气——
     *       否则「我的借款单」会被当成办一张借款单。</li>
     *   <li>读技能：命中业务词即可；若技能声明了查询动词，还要求命中动词，
     *       但"已带日期"或"已带单据状态"时同样算数（这两个槽位本身就是查询限定）。</li>
     * </ul>
     */
    private boolean eligible(Skill s, String q, int kw, int vb, Slots slots) {
        if (kw == 0) {
            return false;
        }
        if (s.isWrite()) {
            return vb > 0 && !readIntent(q);
        }
        boolean datedEnough = s.isRead() && s.isAllowWhenDated() && slots != null && slots.date != null;
        boolean statusAnchored = s.isRead() && slots != null && slots.status != null && s.getStatusField() != null;
        if (!s.getVerbs().isEmpty() && vb == 0 && !datedEnough && !statusAnchored) {
            return false;
        }
        return true;
    }

    private static boolean visible(Skill s, Set<String> visibleCodes) {
        return s.getBacking().isEmpty() || visibleCodes.containsAll(s.getBacking());
    }

    /** 明确在问"入口/位置"：这类问法要给页面入口，而不是同名的数据列表。 */
    private static boolean navIntent(String q) {
        return q.contains("页面") || q.contains("菜单") || q.contains("模块") || q.contains("入口")
                || q.contains("在哪个") || q.contains("在哪") || q.contains("在哪儿") || q.contains("哪里");
    }

    private static boolean readIntent(String q) {
        for (String m : READ_INTENT) {
            if (q.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private Match build(Skill s, String q, Slots slots, boolean withWrites) {
        if (s.isRead()) {
            return new Match(s, readArgs(s, q, slots),
                    "本地技能规则命中（" + s.getTitle() + "）", null, false, null);
        }
        if (s.isWrite()) {
            BigDecimal amount = slots == null ? null : slots.amount;
            if (amount == null && LOOSE_WRITE_AMOUNT.matcher(q).find()) {
                amount = slotExtractor.extractLooseAmount(q);
            }
            Map<String, Object> bill = new LinkedHashMap<>();
            bill.put("billType", s.getBillType());
            if (amount != null) {
                bill.put("totalAmount", amount);
                bill.put("title", titleOf(s, amount));
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("bill", bill);
            return new Match(s, args, "本地技能规则命中（" + s.getTitle() + "）", amount, false, null);
        }
        if (s.isNav()) {
            String route = null;
            for (Map.Entry<String, String> e : s.getRoutes().entrySet()) {
                if (q.contains(e.getKey())) {
                    route = e.getValue();
                    break;
                }
            }
            return new Match(s, Map.of(), "本地技能规则命中（打开页面）", null, false, route);
        }
        return new Match(s, Map.of(), "本地技能规则命中（知识问答）", null, false, null);
    }

    /** 会话继承：改成 X / 再填一张。仅在上一轮确有写票据时生效。 */
    private Match refine(String q, boolean withWrites, Set<String> visibleCodes, Slots slots,
                         SessionStore.SessionState ses) {
        if (!withWrites || ses == null || ses.getLastWriteSkill() == null) {
            return null;
        }
        Matcher cm = CHANGE.matcher(q);
        boolean change = cm.find();
        boolean again = AGAIN.matcher(q).find();
        if (!change && !again) {
            return null;
        }
        Skill s = registry.byName(ses.getLastWriteSkill());
        if (s == null || !s.isWrite() || !visibleCodes.containsAll(s.getBacking())) {
            return null;
        }
        BigDecimal amount = slots == null ? null : slots.amount;
        if (change) {
            // 「改成 1200」没有单位词，槽位抽不出金额——按改写语句自身解析，缺单位即按元
            BigDecimal changed = changedAmount(cm);
            if (changed != null) {
                amount = changed;
            }
        }
        if (amount == null && !again) {
            return null; // 「改成」但没给金额 → 不猜
        }
        Map<String, Object> args = ses.copyLastWriteArgs();
        if (amount != null) {
            putAmount(args, amount);
        }
        return new Match(s, args, change ? "会话继承：修改上一张票据金额" : "会话继承：照上一张再出一张",
                amountOf(args), true, null);
    }

    /** 解析「改成 1200（万元）」；单位缺省按元，万元/万按一万倍。 */
    private static BigDecimal changedAmount(Matcher m) {
        if (!m.reset().find()) {
            return null;
        }
        String num = m.group(1);
        String unit = m.group(2);
        if (num == null) {
            return null;
        }
        BigDecimal v;
        try {
            v = new BigDecimal(num);
        } catch (NumberFormatException ex) {
            return null;
        }
        if ("万元".equals(unit) || "万".equals(unit)) {
            v = v.multiply(BigDecimal.valueOf(10000));
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static void putAmount(Map<String, Object> args, BigDecimal amount) {
        if (args.get("bill") instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bm = (Map<String, Object>) m;
            bm.put("totalAmount", amount);
            Object type = bm.get("billType");
            String prefix = "LOAN".equals(type) ? "借款" : "REPAY".equals(type) ? "还款" : "费用报销";
            bm.put("title", prefix + "-" + money(amount));
        }
    }

    private static BigDecimal amountOf(Map<String, Object> args) {
        if (args.get("bill") instanceof Map<?, ?> m) {
            Object v = m.get("totalAmount");
            if (v instanceof BigDecimal b) return b;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
            if (v instanceof String s) {
                try { return new BigDecimal(s); } catch (NumberFormatException ignore) { return null; }
            }
        }
        return null;
    }

    private static String titleOf(Skill s, BigDecimal amount) {
        return (s.getTitlePrefix() == null ? s.getTitle() : s.getTitlePrefix()) + "-" + money(amount);
    }

    private static String money(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private static int hits(String q, List<String> words) {
        int n = 0;
        for (String w : words) {
            if (w != null && !w.isBlank() && q.contains(w)) {
                n++;
            }
        }
        return n;
    }

    /** 读参数映射：基础参数 + 单据类型 + 日期/状态/单号映射 + 条件参数。 */
    public Map<String, Object> readArgs(Skill s, String q, Slots slots) {
        Map<String, Object> args = new LinkedHashMap<>(s.getArgs());
        if (s.getBillType() != null) {
            args.put("billType", s.getBillType());
        }
        if (slots != null && slots.date != null) {
            String style = s.getDateStyle() == null ? "none" : s.getDateStyle();
            switch (style) {
                case "range" -> {
                    if (s.getDateStartField() != null) args.put(s.getDateStartField(), slots.date.from().toString());
                    if (s.getDateEndField() != null) args.put(s.getDateEndField(), slots.date.to().toString());
                }
                case "period" -> {
                    if (s.getYearField() != null) args.put(s.getYearField(), slots.date.from().getYear());
                    if (s.getMonthField() != null) args.put(s.getMonthField(), slots.date.from().getMonthValue());
                }
                case "yearMonth" -> {
                    if (slots.date.from().getYear() == slots.date.to().getYear()) {
                        if (s.getYearField() != null) args.put(s.getYearField(), slots.date.from().getYear());
                        if (slots.date.from().getMonthValue() == slots.date.to().getMonthValue()
                                && s.getMonthField() != null) {
                            args.put(s.getMonthField(), slots.date.from().getMonthValue());
                        }
                    } else if (s.getYearField() != null) {
                        args.put(s.getYearField(), slots.date.to().getYear());
                        if (s.getMonthField() != null) args.put(s.getMonthField(), slots.date.to().getMonthValue());
                    }
                }
                case "monthPeriod" -> {
                    if (slots.date.from().getYear() == slots.date.to().getYear()
                            && slots.date.from().getMonthValue() == slots.date.to().getMonthValue()
                            && s.getPeriodField() != null) {
                        args.put(s.getPeriodField(), String.format("%04d%02d",
                                slots.date.from().getYear(), slots.date.from().getMonthValue()));
                    }
                }
                default -> {
                }
            }
        }
        if (slots != null && s.getStatusField() != null && slots.status != null) {
            args.put(s.getStatusField(), slots.status);
        }
        if (slots != null && s.getKeywordField() != null && slots.billNo != null) {
            args.put(s.getKeywordField(), slots.billNo);
        }
        for (Map<String, Object> flag : s.getFlags()) {
            Object contains = flag.get("contains");
            Object field = flag.get("field");
            if (contains != null && field != null && q.contains(String.valueOf(contains))) {
                args.put(String.valueOf(field), flag.get("value"));
            }
        }
        return args;
    }
}
