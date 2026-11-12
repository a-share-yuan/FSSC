// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.ai.AiGatewayService;
import com.fssc.ai.entity.AiActionConfirm;
import com.fssc.ai.knowledge.KnowledgeIndex;
import com.fssc.ai.memory.SessionStore;
import com.fssc.ai.resolve.EntityResolver;
import com.fssc.ai.skill.Skill;
import com.fssc.ai.skill.SkillRegistry;
import com.fssc.ai.skill.SkillRouter;
import com.fssc.ai.skill.SlotExtractor;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.trace.TraceIdFilter;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ConnectorFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 只读问答编排（自然语言 → 目录内 read 动作 → 网关执行 → 答案＋规则＋权限判定过程）。
 *
 * <p>三条纪律：
 * <ol>
 *   <li>候选动作 = 目录里 kind=read ∧ aiAllowed ∧ risk=LOW ∧ 下令真人实际持有其权限码
 *       —— LLM 连"看不见的动作"都不知道，越权在提示词层就被剪枝；执行仍过网关全套校验。</li>
 *   <li>没有大模型通道或通道故障时走本地关键词匹配（确定性兜底），§7 纵切问题不依赖外网。</li>
 *   <li>读不到就明确说读不到（作者："重点看它是否清楚自己不能做什么"）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAskService {

    private final AiActionCatalog catalog;
    private final AiGatewayService gateway;
    private final AiLocalReadInvoker localReadInvoker;
    private final ConnectorFacade connector;
    private final ObjectMapper objectMapper;
    private final SkillRegistry skillRegistry;
    private final SkillRouter skillRouter;
    private final SlotExtractor slotExtractor;
    private final SessionStore sessionStore;
    private final KnowledgeIndex knowledgeIndex;
    private final EntityResolver entityResolver;

    /** 常见业务状态码 → 中文；只收录库里实际出现的值，未收录原样显示。 */
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("DRAFT", "草稿"),
            Map.entry("PENDING", "待处理"),
            Map.entry("WAITING", "待处理"),
            Map.entry("APPROVED", "已批准"),
            Map.entry("REJECTED", "已驳回"),
            Map.entry("POSTED", "已记账"),
            Map.entry("PAID", "已付款"),
            Map.entry("COMPLETED", "已完成"),
            Map.entry("DONE", "已完成"),
            Map.entry("VOID", "已作废"),
            Map.entry("CANCELLED", "已取消"),
            Map.entry("WITHDRAWN", "已撤回"),
            Map.entry("REPAID", "已还款"),
            Map.entry("NORMAL", "正常"),
            Map.entry("CLAIMED", "已领取"),
            Map.entry("AUDITING", "审核中"),
            Map.entry("SUBMITTED", "已提交"),
            Map.entry("CLOSED", "已关闭"));

    public AskResult ask(String question, HttpServletRequest http, LoginUser user, String traceId, String sessionId) {
        List<AiAction> visible = visibleReadActions(user);
        return runChooseThenExecute(question, visible, false, http, user, traceId, new AskResult(), sessionId);
    }

    /**
     * 自然语言指令 → 目录内动作（前端 AI 指令台的入口）。
     *
     * <p>只分两挡：只读低风险立即执行；其余（写/较高风险）不执行，改为按参数摘要出一张确认票据，
     * 等<b>另一位</b>持 {@code ai:confirm:approve} 的真人背书后，由前端持 confirmId 走
     * {@code POST /api/ai/gateway/execute} 真正落地。本方法自身零业务判断，红线与权限仍由网关把。
     */
    public InstructResult instruct(String question, HttpServletRequest http, LoginUser user, String traceId, String sessionId) {
        List<AiAction> visible = visibleActions(user, true);
        return (InstructResult) runChooseThenExecute(question, visible, true, http, user, traceId, new InstructResult(), sessionId);
    }

    /** 选型 →（只读直跑 ／ 写则出票）的统一编排，ask 与 instruct 共用同一条执行路径。 */
    private AskResult runChooseThenExecute(String question, List<AiAction> visible, boolean withWrites,
                                          HttpServletRequest http, LoginUser user, String traceId, AskResult r,
                                          String sessionId) {
        SessionStore.SessionState ses = sessionStore.getOrCreate(sessionId, user);
        r.setSessionId(ses.getSessionId());
        r.setQuestion(question);
        r.setTraceId(traceId);
        Set<String> visibleCodes = visible.stream().map(AiAction::getCode).collect(Collectors.toSet());

        // 红线/绕过检测必须先于任何路由。挂在"选型失败"分支里的话，
        // 「把今年所有单据都审批通过」一旦被只读技能或只读动作接住，就绕过了检测。
        // 红线是系统规则，不是"没匹配到动作时的解释"，因此放在入口第一道。
        AskResult redlined = preRouteRedline(question, withWrites, user, traceId, r);
        if (redlined != null) {
            return redlined;
        }

        // 确定性技能优先于模型。零外部依赖，槽位（金额/日期/状态）由代码抽取，
        // 模型只接管这层匹配不到的长尾；写技能永远只出票、不执行。
        SlotExtractor.Slots slots = slotExtractor.extract(question);
        if (withWrites) {
            List<String> clauses = splitClauses(question);
            if (clauses.size() >= 2) {
                List<SkillRouter.Match> ms = new ArrayList<>();
                List<SlotExtractor.Slots> ss = new ArrayList<>();
                for (String c : clauses) {
                    SlotExtractor.Slots cs = slotExtractor.extract(c);
                    ss.add(cs);
                    ms.add(skillRouter.route(c, true, visibleCodes, cs, ses));
                }
                if (ms.stream().allMatch(m -> m != null && m.skill().isWrite())) {
                    return runCompoundWrite(clauses, ms, ss, traceId, user, (InstructResult) r, ses);
                }
            }
        }
        SkillRouter.Match sm = skillRouter.route(question, withWrites, visibleCodes, slots, ses);
        if (sm != null && sm.permBlocked()) {
            // 语义命中、但该技能的动作本人无权：不猜别的动作顶替，如实报缺权限
            return reportPermBlocked(sm, question, user, traceId, r);
        }
        if (sm != null) {
            AskResult bySkill = runSkill(sm, question, http, user, traceId, r, ses, withWrites);
            if (bySkill != null) {
                return bySkill;
            }
        }

        ExtInterfaceConfig model = connector.getConfig("AI_MODEL");
        // 通道故障必须能到达用户：把"模型 403/超时"与"确实没有匹配动作"合并成 engine=null，
        // 每次问数都像"没听懂"。这里记录首次失败原因，随结果一起回显。
        LlmTrace llm = new LlmTrace();
        // 办理类指令（新建/提交/审批…）：模型不可用时绝不能拿只读列表冒充"已办"，
        // 也不能让关键词兜底把"建单"匹配成"我的单据"。
        boolean writeIntent = withWrites && looksLikeWriteIntent(question);
        Selection sel = selectByLlm(question, visible, withWrites, model, llm);
        // 模型显式拒答也走关键词兜底：规则只在"本人可见"集内匹配，不会造成越权
        if (sel == null || sel.action() == null) {
            Selection byRule = writeIntent ? null : selectByKeyword(question, visible);
            if (byRule != null && byRule.action() != null) {
                sel = byRule;
            }
        }
        if (writeIntent && sel != null && sel.action() != null && "read".equals(sel.action().getKind())) {
            sel = null;
        }
        r.setQuestion(question);
        r.setTraceId(traceId);
        r.setEngine(sel == null ? null : sel.engine());
        r.setReason(sel == null ? null : sel.reason());
        r.setChannelError(llm.error);

        if (sel == null || sel.action() == null) {
            // 提示词注入/绕过规则：输入里的"指令"只是数据，不能让 AI 跳过审批与双人确认。
            if (looksLikeBypass(question)) {
                r.setReason("该请求试图绕过审批/双人确认规则");
                r.setRefusalReason("REDLINE_BLOCKED");
                log.info("AI_REFUSAL reason=REDLINE_BLOCKED(bypass) channel={} user={} question={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(), question, traceId);
                r.setAnswer("我不会执行绕过审批、确认票据或权限规则的指令；您话里的这类要求只被当作文本，不会变成动作。"
                        + "需要办理请走正常流程（AI 出票 → 另一位真人确认 → 以您的权限执行）。（refusal=REDLINE_BLOCKED）");
                return r;
            }
            // 类型级红线（approve/void/delete/config）在建候选集时就被整族剔除，红线探测池也刻意不含它们；
            // 若不在此单独识别，"帮我审批这张单"会掉进 WRITE_CHANNEL_UNAVAILABLE（说成模型不可用），
            // 而事实是这类动作 AI 永不经手。命中只用于解释，不出票、不执行。
            String forbidden = forbiddenKindOf(question);
            if (forbidden != null) {
                r.setReason("该请求属于 §6 红线类型：" + forbidden + "（AI 永不经手，确认票据也不受理）");
                r.setRefusalReason("REDLINE_BLOCKED");
                log.info("AI_REFUSAL reason=REDLINE_BLOCKED(type) channel={} user={} kind={} question={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        forbidden, question, traceId);
                r.setAnswer("「" + forbiddenName(forbidden) + "」属于系统红线类型（" + forbidden
                        + "）：AI 永不经手，也不会为它出确认票据。请由真人在对应页面按流程办理。"
                        + "（refusal=REDLINE_BLOCKED）");
                return r;
            }
            // 拒答分态并落 refusal_reason —— 只判"缺权限"一维不够：红线动作（aiAllowed=false）
            // 在候选集构筑时就被剔除，于是"目录里有、但被红线封禁"会被误报成"目录里没有"。
            // 此处按 红线 → 缺码 → 通道档位 的次序单独探测（只做解释，绝不执行），三态不再是死状态。
            AiAction redlineAction = redlineGapAction(question, withWrites, model, llm);
            AiAction gap = redlineAction == null
                    ? permGapAction(question, visible, withWrites, !withWrites, model, user, llm) : null;
            // 分态探测本身也会调模型：首次选型成功、探测失败时同样要如实回显通道故障
            r.setChannelError(llm.error);
            if (redlineAction != null) {
                // reason 也一并改写为系统规则：首轮 reason 是模型对"常规候选集"的评述，在红线路上它会说
                // "动作清单中不存在…"，而事实是"目录里有、只是被红线封了"——与 refusalReason 自相矛盾。
                r.setReason("该请求指向的动作已被系统红线封禁："
                        + redlineAction.getCode()
                        + (redlineAction.getRedlineFamily() == null ? "" : "（族：" + redlineAction.getRedlineFamily() + "）"));
                r.setRefusalReason("REDLINE_BLOCKED");
                r.setAction(redlineAction.getCode());
                r.setActionUrl(redlineAction.getMethod() + " " + redlineAction.getUrl());
                r.setPermCode(redlineAction.getPermCode());
                log.info("AI_REFUSAL reason=REDLINE_BLOCKED channel={} user={} action={} family={} question={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        redlineAction.getCode(), redlineAction.getRedlineFamily(), question, traceId);
                r.setAnswer("这个动作在目录里存在（" + redlineAction.getCode() + "），但被标为 AI 不可调用"
                        + (redlineAction.getRedlineFamily() == null ? "" : "，封禁族「" + redlineAction.getRedlineFamily() + "」")
                        + "：网关侧一律 403、确认票据也不受理，我不会执行它。请由真人在对应页面上办理。"
                        + "（refusal=REDLINE_BLOCKED）");
                return r;
            }
            if (gap != null) {
                String perm = gap.getPermCode();
                boolean lacks = user == null || (!user.isAdmin()
                        && (perm == null || user.getPerms() == null || !user.getPerms().contains(perm)));
                // 同红线路：reason 改写为系统规则，避免与 refusalReason 各说各话
                r.setReason(lacks
                        ? "该请求指向的动作存在，但本人不持其权限码：" + perm
                        : "该请求指向只读动作 " + gap.getCode() + "，风险档 " + gap.getRiskLevel()
                                + "，问数通道只自动执行 LOW 档");
                r.setAction(gap.getCode());
                r.setActionUrl(gap.getMethod() + " " + gap.getUrl());
                r.setPermCode(perm);
                r.setRefusalReason(lacks ? "NO_PERM" : "CHANNEL_READ_ONLY");
                log.info("AI_REFUSAL reason={} channel={} user={} action={} perm={} risk={} question={} traceId={}",
                        r.getRefusalReason(), withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        gap.getCode(), perm, gap.getRiskLevel(), question, traceId);
                r.setAnswer(lacks
                        ? "这个动作在目录里存在，但您本人不持有它的权限码 " + perm
                                + "，所以我不能替您取这个数——按您的权限面，我能做的是另一些动作。（refusal=NO_PERM）"
                        : "该问题对应只读动作 " + gap.getCode() + "，风险档=" + gap.getRiskLevel()
                                + "：问数通道只自动执行低风险（LOW）只读读数，非 LOW 档我不自动跑。"
                                + "如需这份数据，请走指令通道并由另一位真人确认。（refusal=CHANNEL_READ_ONLY）");
                return r;
            }
            if (writeIntent) {
                r.setRefusalReason("WRITE_CHANNEL_UNAVAILABLE");
                log.info("AI_REFUSAL reason=WRITE_CHANNEL_UNAVAILABLE channel=instruct user={} question={} traceId={}",
                        user == null ? null : user.getUsername(), question, traceId);
                r.setAnswer("这是一条办理类指令（新建/提交/审批等），需要大模型把您的意思填进具体动作的参数；"
                        + "当前大模型通道不可用"
                        + (llm.error == null ? "" : "：" + llm.error)
                        + "。我不会用一条只读查询冒充办理结果，也不会凭空猜参数。"
                        + "请恢复模型通道后再试，或到对应页面办理。（refusal=WRITE_CHANNEL_UNAVAILABLE）");
                return r;
            }
            r.setRefusalReason("NO_MATCH");
            log.info("AI_REFUSAL reason=NO_MATCH channel={} user={} question={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(), question, traceId);
            r.setAnswer("目录里没有能回答这个问题的动作（我不会猜测数字，也不会自行造动作）。"
                    + channelNote(llm, "以上结论由本地关键词兜底产生"));
            return r;
        }
        AiAction a = sel.action();
        Map<String, Object> args = sel.args() == null ? Map.of() : sel.args();
        // 动作带机械采集的参数结构时，第二次调用只干"照结构填参"这一件事——
        // 让模型凭一行 Java 签名猜字段名，猜错是必然的。填参失败即沿用选型阶段的 args，不猜。
        String missing = null;
        if (needFillArgs(a, args, model)) {
            Fill fill = fillArgs(a, question, model);
            if (fill != null) {
                if (!fill.args().isEmpty()) {
                    args = fill.args();
                    r.setEngine(r.getEngine() + "+fill");
                }
                missing = fill.missing();
            }
        }
        r.setAction(a.getCode());
        r.setActionUrl(a.getMethod() + " " + a.getUrl());
        r.setPermCode(a.getPermCode());

        if (!"read".equals(a.getKind()) || !"LOW".equalsIgnoreCase(a.getRiskLevel())) {
            if (r instanceof InstructResult ir) {
                if (args.isEmpty() && declaresArgs(a)) {
                    ir.setOk(false);
                    ir.setMissing(missing);
                    ir.setRefusalReason("PARAM_MISSING");
                    log.info("AI_REFUSAL reason=PARAM_MISSING channel=instruct user={} action={} missing={} traceId={}",
                            user == null ? null : user.getUsername(), a.getCode(), missing, traceId);
                    ir.setAnswer("动作 " + a.getCode() + " 要求请求体/路径参数，但从这句话里我一个参数都没填上"
                            + (missing == null ? "" : "（还缺：" + missing + "）")
                            + "。空参数的票据没有审核价值（确认人无从判断要放行什么），本次不出票——"
                            + "补上信息（金额/单据类型/单号等）再说一遍即可。");
                    return ir;
                }
                ir.setMissing(missing);
                return issueTicket(a, args, question, traceId, user, ir);
            }
            // 此分支是"问数通道候选集只有 read∧LOW"这一不变量的守护——
            // 正常走不到（ask 的候选集本就剪过档位），"读动作但档位非 LOW"的解释性拒答
            // 由上面的 permGapAction（readAnyRisk）产出。留着它只为剪枝规则若被改动时不至于静默执行。
            r.setOk(false);
            r.setRefusalReason("CHANNEL_READ_ONLY");
            log.info("AI_REFUSAL reason=CHANNEL_READ_ONLY channel=ask user={} action={} kind={} traceId={}",
                    user == null ? null : user.getUsername(), a.getCode(), a.getKind(), traceId);
            r.setAnswer("该问题对应 " + a.getCode() + "，属" + kindName(a.getKind()) + "、风险档=" + a.getRiskLevel()
                    + "。问数通道只读，不会执行它（refusal=CHANNEL_READ_ONLY）。");
            return r;
        }
        try {
            AiGatewayService.GatewayRequest req = new AiGatewayService.GatewayRequest(
                    question, r.getEngine(), a.getCode(), null, null, args, null);
            AiGatewayService.GatewayResult g = gateway.execute(req, http, user, traceId);
            r.setOk(g.bizCode() != null && g.bizCode() == 0);
            Object data = summarize(g.data());
            r.setData(data);
            if (!r.isOk()) {
                r.setRefusalReason("EXEC_DENIED");
                log.warn("AI_REFUSAL reason=EXEC_DENIED channel={} user={} action={} bizCode={} msg={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        a.getCode(), g.bizCode(), g.errorMsg(), traceId);
                r.setAnswer("动作执行被服务端拒绝：" + g.errorMsg() + "（权限码 " + a.getPermCode() + "，traceId=" + traceId + "）");
            } else {
                r.setAnswer(answerOf(a, data, llm));
            }
        } catch (BizException ex) {
            r.setOk(false);
            r.setRefusalReason("EXEC_DENIED");
            log.warn("AI_REFUSAL reason=EXEC_DENIED(gate) channel={} user={} action={} msg={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                    a.getCode(), ex.getMessage(), traceId);
            r.setAnswer("读不到：" + ex.getMessage() + "。我不会绕过权限给出数字。");
        }
        return r;
    }

    // 技能编排

    /**
     * 入口级红线闸（先于技能路由与模型选型）。
     *
     * <p>三道都只用确定性规则，不调模型：一是"绕过规则/提示词注入"的语气，二是类型级红线
     * （approve/void/delete/config），三是中文红线说法（反关账、清空审计日志、付款批执行…），
     * 第四道是族级红线探测池里的确定性映射。
     *
     * <p>命中只用于解释拒答，绝不执行、绝不出票。
     */
    private AskResult preRouteRedline(String question, boolean withWrites, LoginUser user,
                                       String traceId, AskResult r) {
        if (looksLikeBypass(question)) {
            r.setOk(false);
            r.setReason("该请求试图绕过审批/双人确认规则");
            r.setRefusalReason("REDLINE_BLOCKED");
            log.info("AI_REFUSAL reason=REDLINE_BLOCKED(bypass) channel={} user={} question={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(), question, traceId);
            r.setAnswer("我不会执行绕过审批、确认票据或权限规则的指令；您话里的这类要求只被当作文本，不会变成动作。"
                    + "需要办理请走正常流程（AI 出票 → 另一位真人确认 → 以您的权限执行）。（refusal=REDLINE_BLOCKED）");
            return r;
        }
        String forbidden = forbiddenKindOf(question);
        if (forbidden != null) {
            r.setOk(false);
            r.setReason("该请求属于 §6 红线类型：" + forbidden + "（AI 永不经手，确认票据也不受理）");
            r.setRefusalReason("REDLINE_BLOCKED");
            log.info("AI_REFUSAL reason=REDLINE_BLOCKED(type) channel={} user={} kind={} question={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                    forbidden, question, traceId);
            r.setAnswer("「" + forbiddenName(forbidden) + "」属于系统红线类型（" + forbidden
                    + "）：AI 永不经手，也不会为它出确认票据。请由真人在对应页面按流程办理。"
                    + "（refusal=REDLINE_BLOCKED）");
            return r;
        }
        AiAction family = redlineByKeyword(question, withWrites);
        if (family != null) {
            r.setOk(false);
            r.setReason("该请求指向的动作已被系统红线封禁：" + family.getCode()
                    + (family.getRedlineFamily() == null ? "" : "（族：" + family.getRedlineFamily() + "）"));
            r.setRefusalReason("REDLINE_BLOCKED");
            r.setAction(family.getCode());
            r.setActionUrl(family.getMethod() + " " + family.getUrl());
            r.setPermCode(family.getPermCode());
            log.info("AI_REFUSAL reason=REDLINE_BLOCKED(family) channel={} user={} action={} question={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                    family.getCode(), question, traceId);
            r.setAnswer("这个动作在目录里存在（" + family.getCode() + "），但被标为 AI 不可调用"
                    + (family.getRedlineFamily() == null ? "" : "，封禁族「" + family.getRedlineFamily() + "」")
                    + "：网关侧一律 403、确认票据也不受理，我不会执行它。请由真人在对应页面上办理。"
                    + "（refusal=REDLINE_BLOCKED）");
            return r;
        }
        return null;
    }

    /** 技能语义命中、但其 backing 动作本人不可见：如实报缺权限，不拿别的动作顶替。 */
    private AskResult reportPermBlocked(SkillRouter.Match m, String question, LoginUser user,
                                        String traceId, AskResult r) {
        Skill s = m.skill();
        AiAction a = s.getBacking().isEmpty() ? null : catalog.byCode(s.getBacking().get(0));
        r.setOk(false);
        r.setSkill(s.getName());
        r.setEngine("skill:" + s.getName());
        r.setReason(m.reason());
        r.setRefusalReason("NO_PERM");
        if (a != null) {
            r.setAction(a.getCode());
            r.setActionUrl(a.getMethod() + " " + a.getUrl());
            r.setPermCode(a.getPermCode());
        }
        log.info("AI_REFUSAL reason=NO_PERM(skill-pruned) user={} skill={} action={} perm={} question={} traceId={}",
                user == null ? null : user.getUsername(), s.getName(),
                a == null ? null : a.getCode(), a == null ? null : a.getPermCode(), question, traceId);
        r.setAnswer("「" + s.getTitle() + "」对应的动作" + (a == null ? "" : "（" + a.getCode() + "）")
                + "不在您本人的可见范围，我没有权限替您取这份数据，也不能换一个不相干的动作来顶替。"
                + "请找有权限的同事办理，或到对应页面查询。（refusal=NO_PERM）");
        return r;
    }

    /** 技能命中后的分支：知识 → 导航 → 只读 → 写（出票）。返回 null 表示交给模型长尾。 */
    private AskResult runSkill(SkillRouter.Match m, String question, HttpServletRequest http, LoginUser user,
                               String traceId, AskResult r, SessionStore.SessionState ses, boolean withWrites) {
        Skill s = m.skill();
        r.setSkill(s.getName());
        r.setEngine("skill:" + s.getName());
        r.setReason(m.reason());

        if (s.isKnowledge()) {
            return answerKnowledge(question, user, r);
        }
        if (s.isNav()) {
            String route = m.navRoute();
            r.setRoute(route);
            if (route == null) {
                r.setOk(false);
                r.setRefusalReason("NO_MATCH");
                r.setAnswer("我看出您想打开某个功能页面，但没说是哪一块。可以说「打开审批页面」「打开发票查询」这类带业务名的说法。");
            } else {
                r.setOk(true);
                r.setAnswer("入口已找到：" + route + "（直接点这个地址即可打开；我不会通过链接执行业务写操作）。");
            }
            return r;
        }
        if (s.isRead()) {
            return executeSkillRead(m, question, http, user, traceId, r, ses);
        }
        if (s.isWrite() && r instanceof InstructResult ir) {
            if (!withWrites) {
                return null;
            }
            return runWriteSkill(m, question, traceId, user, ir, ses);
        }
        return null;
    }

    /** 知识问答：抽取式回答 + 出处；无证据就说没有，不让模型自由发挥。 */
    private AskResult answerKnowledge(String question, LoginUser user, AskResult r) {
        KnowledgeIndex.Answer ans = knowledgeIndex.isEnabled() ? knowledgeIndex.answer(question, user) : null;
        if (ans == null) {
            r.setOk(false);
            r.setRefusalReason("NO_EVIDENCE");
            r.setAnswer(knowledgeIndex.isEnabled()
                    ? "知识库里没有找到能回答这个问题的原文（我不会凭空编流程）。可以换个说法，或到对应页面查看。"
                    : "知识索引未启用，本次无法给出有出处的回答。请让管理员检查 fssc.ai.knowledge.enabled 与索引产物。");
            log.info("AI_KNOWLEDGE no-evidence user={} question={}", user == null ? null : user.getUsername(), question);
            return r;
        }
        r.setOk(true);
        r.setAnswer(ans.text());
        r.setCitations(ans.hits().stream()
                .map(h -> new Citation(h.title(), h.docPath(), h.heading(), h.score()))
                .toList());
        return r;
    }

    /** 只读技能：参数由技能映射直接产出，执行仍走网关（同鉴权/同审计/同回环）。 */
    private AskResult executeSkillRead(SkillRouter.Match m, String question, HttpServletRequest http, LoginUser user,
                                       String traceId, AskResult r, SessionStore.SessionState ses) {
        AiAction a = catalog.byCode(m.skill().getBacking().get(0));
        if (a == null) {
            return null;
        }
        Map<String, Object> args = m.args() == null ? Map.of() : m.args();
        r.setAction(a.getCode());
        r.setActionUrl(a.getMethod() + " " + a.getUrl());
        r.setPermCode(a.getPermCode());
        r.setArgs(args); // 回显本次真正用的查询条件：让用户看得见"按什么条件查的"
        try {
            AiGatewayService.GatewayRequest req = new AiGatewayService.GatewayRequest(
                    question, r.getEngine(), a.getCode(), null, null, args, null);
            AiGatewayService.GatewayResult g = gateway.execute(req, http, user, traceId);
            r.setOk(g.bizCode() != null && g.bizCode() == 0);
            Object data = summarize(g.data());
            r.setData(data);
            if (!r.isOk()) {
                r.setRefusalReason("EXEC_DENIED");
                r.setAnswer("动作执行被服务端拒绝：" + g.errorMsg() + "（权限码 " + a.getPermCode() + "，traceId=" + traceId + "）");
            } else {
                r.setAnswer(answerOf(a, data, null));
                Long billId = null;
                String billNo = null;
                if (data instanceof Map<?, ?> dm && dm.get("top5") instanceof List<?> top && !top.isEmpty()
                        && top.get(0) instanceof Map<?, ?> row) {
                    billId = longOf(row.get("id"), row.get("billId"));
                    billNo = row.get("billNo") == null ? null : String.valueOf(row.get("billNo"));
                }
                ses.rememberRead(m.skill().getName(), args, billId, billNo, question);
            }
        } catch (BizException ex) {
            r.setOk(false);
            r.setRefusalReason("EXEC_DENIED");
            r.setAnswer("读不到：" + ex.getMessage() + "。我不会绕过权限给出数字。");
        }
        return r;
    }

    /** 写技能：补主体参数 → 生成人话预览/缺项 → 出票（不执行），并作废本会话上一张未用票。 */
    private InstructResult runWriteSkill(SkillRouter.Match m, String question, String traceId, LoginUser user,
                                         InstructResult ir, SessionStore.SessionState ses) {
        Skill s = m.skill();
        Map<String, Object> args = deepCopy(m.args());
        @SuppressWarnings("unchecked")
        Map<String, Object> bill = args.get("bill") instanceof Map<?, ?> bm
                ? (Map<String, Object>) bm : new LinkedHashMap<>();
        args.put("bill", bill);

        List<String> missing = new ArrayList<>();
        BigDecimal amount = amountOf(bill);
        if (amount == null && m.amount() != null) {
            amount = m.amount();
            bill.put("totalAmount", amount);
            bill.put("title", titleOf(s, amount));
        }
        if (s.isAmountRequired() && amount == null) {
            missing.add("金额");
        }
        String companyName = null;
        String itemName = null;
        for (EntityResolver.Resolution res : entityResolver.resolveInText(question, user)) {
            if (res.none()) {
                continue;
            }
            if (res.unique()) {
                EntityResolver.Entity e = res.first();
                switch (res.type()) {
                    case "company" -> {
                        if (user.getCompanyId() == null || user.getCompanyId().equals(e.id())) {
                            bill.put("companyId", e.id());
                            companyName = e.name();
                        } else {
                            missing.add("公司「" + e.name() + "」与您所属公司不一致，不能以该公司建单");
                        }
                    }
                    case "dept" -> bill.put("deptId", e.id());
                    case "expense_item" -> itemName = e.name();
                    default -> {
                    }
                }
            } else {
                missing.add(switch (res.type()) {
                    case "company" -> "公司有多个候选：" + EntityResolver.candidatesText(res) + "（请指明一个）";
                    case "dept" -> "部门有多个候选：" + EntityResolver.candidatesText(res) + "（请指明一个）";
                    default -> "费用项有多个候选：" + EntityResolver.candidatesText(res) + "（请指明一个）";
                });
            }
        }
        // 票据只落表头：行明细（费用项/金额拆分）不自动编造
        if ("CLAIM".equals(s.getBillType())) {
            missing.add(itemName == null ? "费用类别（费用项）与行明细" : "行明细（费用项已识别为「" + itemName + "」，金额待拆行）");
        } else {
            missing.add("行明细（费用项/金额）");
        }

        if (amount != null) {
            String title = String.valueOf(bill.get("title") == null ? titleOf(s, amount) : bill.get("title"));
            bill.putIfAbsent("title", title);
            bill.putIfAbsent("totalAmount", amount);
        }
        String preview = previewOf(s, bill, amount, companyName, user);

        ir.setSkill(s.getName());
        ir.setArgs(args);
        ir.setPreview(preview);
        ir.setMissing(missing.isEmpty() ? null : String.join("；", missing));
        ir.setEngine("skill:" + s.getName());
        ir.setReason(m.reason());
        ir.setOk(false);

        if (s.isAmountRequired() && amount == null) {
            ir.setNeedsConfirm(false);
            ir.setRefusalReason("PARAM_MISSING");
            ir.setAnswer("要新建「" + s.getTitle() + "」，我还差一个金额（比如「1000块」）。"
                    + "没有金额的草稿票据没有审核价值，所以本次不出票。");
            log.info("AI_SKILL_PARAM_MISSING skill={} user={} question={} traceId={}",
                    s.getName(), user == null ? null : user.getUsername(), question, traceId);
            return ir;
        }

        AiAction a = catalog.byCode(s.getBacking().get(0));
        if (a == null) {
            ir.setRefusalReason("NO_MATCH");
            ir.setAnswer("技能指向的动作不在目录中，已拒绝出票（fail-closed）。");
            return ir;
        }
        ir.setAction(a.getCode());
        ir.setActionUrl(a.getMethod() + " " + a.getUrl());
        ir.setPermCode(a.getPermCode());
        ir.setKind(a.getKind());
        ir.setRiskLevel(a.getRiskLevel());

        try {
            AiActionConfirm c = gateway.requestConfirm(a, args, traceId, user);
            ir.setNeedsConfirm(true);
            ir.setConfirmId(c.getId());
            ir.setParamsDigest(c.getParamsDigest());
            ir.setExpireAt(c.getExpireAt() == null ? null : c.getExpireAt().toString());
            ir.setAnswer(preview
                    + "<br>已出确认票据 #" + c.getId() + "（一次性、过期即废），须由<b>另一位</b>有确认权限的真人背书；"
                    + "确认后我仍以下令人本人的权限执行。"
                    + (ir.getMissing() == null ? "" : "<br>还缺：" + ir.getMissing() + "（我不会编造主数据 ID，请补全或驳回。）"));
            // 「改成1200」产生新票后，上一张未用票立即作废，避免队列里留两张互相矛盾的票
            Long prev = ses.getLastConfirmId();
            if (prev != null && !prev.equals(c.getId())) {
                try {
                    gateway.cancelTicket(prev, "会话产生新票据，旧票作废", user);
                } catch (Exception ignore) {
                    // 上一张已被使用/确认/过期时不可撤，属正常
                }
            }
            ses.rememberWrite(s.getName(), args, c.getId(), question);
        } catch (BizException ex) {
            ir.setAnswer("出票失败：" + ex.getMessage() + "（§6 红线动作或权限不足时不会出票）");
        }
        return ir;
    }

    /** 复合写指令（「填一张借款，再填一张报销」）：逐句各出一张票，互不覆盖。 */
    private InstructResult runCompoundWrite(List<String> clauses, List<SkillRouter.Match> matches,
                                            List<SlotExtractor.Slots> slots, String traceId, LoginUser user,
                                            InstructResult r, SessionStore.SessionState ses) {
        List<StepResult> steps = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        Long firstTicket = null;
        String firstDigest = null;
        String firstExpire = null;
        Map<String, Object> firstArgs = null;
        String firstAction = null;
        boolean allConfirmedNeeded = false;
        boolean allOk = true;
        for (int i = 0; i < clauses.size(); i++) {
            InstructResult step = new InstructResult();
            step.setQuestion(clauses.get(i));
            step.setTraceId(traceId);
            step.setSessionId(ses.getSessionId());
            runWriteSkill(matches.get(i), clauses.get(i), traceId, user, step, ses);
            StepResult sr = new StepResult();
            sr.setQuestion(clauses.get(i));
            sr.setSkill(step.getSkill());
            sr.setAnswer(step.getAnswer());
            sr.setPreview(step.getPreview());
            sr.setOk(step.isOk());
            sr.setNeedsConfirm(step.isNeedsConfirm());
            sr.setConfirmId(step.getConfirmId());
            sr.setParamsDigest(step.getParamsDigest());
            sr.setExpireAt(step.getExpireAt());
            sr.setRefusalReason(step.getRefusalReason());
            sr.setKind(step.getKind());
            sr.setRiskLevel(step.getRiskLevel());
            sr.setAction(step.getAction());
            sr.setArgs(step.getArgs());
            steps.add(sr);
            answers.add(step.getAnswer());
            allOk &= step.isOk();
            if (step.isNeedsConfirm()) {
                allConfirmedNeeded = true;
                if (firstTicket == null) {
                    firstTicket = step.getConfirmId();
                    firstDigest = step.getParamsDigest();
                    firstExpire = step.getExpireAt();
                    firstArgs = step.getArgs();
                    firstAction = step.getAction();
                }
            } else {
                allOk = false;
            }
        }
        r.setSkill("compound");
        r.setEngine("skill:compound");
        r.setSteps(steps);
        r.setNeedsConfirm(allConfirmedNeeded);
        r.setConfirmId(firstTicket);
        r.setParamsDigest(firstDigest);
        r.setExpireAt(firstExpire);
        r.setArgs(firstArgs);
        r.setAction(firstAction);
        r.setOk(allOk && !allConfirmedNeeded);
        r.setAnswer(String.join("<br>", answers));
        return r;
    }

    private static List<String> splitClauses(String q) {
        List<String> out = new ArrayList<>();
        if (q == null) {
            return out;
        }
        for (String raw : q.replaceAll("[，,；;]+", "|").split("\\|")) {
            String c = raw.trim().replaceAll("^(然后|接着|并且|同时|另外|还有|再)[，,：:]?", "").trim();
            if (!c.isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private static String previewOf(Skill s, Map<String, Object> bill, BigDecimal amount, String companyName,
                                    LoginUser user) {
        String who = user == null ? "本人" : (user.getRealName() == null ? user.getUsername() : user.getRealName());
        String company = companyName == null ? "本人所属公司" : companyName;
        return "将新建「" + s.getTitle() + "」草稿：" + (amount == null ? "金额待补" : "金额 ¥" + money(amount.toString()))
                + (bill.get("title") == null ? "" : "，标题「" + bill.get("title") + "」")
                + "；申请人 " + who + "；公司 " + company + "。仅生成草稿（不提交、不审批）。";
    }

    private static BigDecimal amountOf(Map<String, Object> bill) {
        Object v = bill == null ? null : bill.get("totalAmount");
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static String titleOf(Skill s, BigDecimal amount) {
        return (s.getTitlePrefix() == null ? s.getTitle() : s.getTitlePrefix()) + "-" + money(amount.toString());
    }

    private static Long longOf(Object... vals) {
        for (Object v : vals) {
            if (v instanceof Number n) {
                return n.longValue();
            }
            if (v instanceof String str && !str.isBlank()) {
                try {
                    return Long.parseLong(str.trim());
                } catch (NumberFormatException ignore) {
                    // 继续试下一个键
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            out.put(e.getKey(), v instanceof Map<?, ?> m ? deepCopy((Map<String, Object>) m) : v);
        }
        return out;
    }

    /** 写／较高风险：只出票不执行；票据摘要由网关按同一规则算，参数改动即作废。 */
    private InstructResult issueTicket(AiAction a, Map<String, Object> args, String question, String traceId,
                                       LoginUser user, InstructResult ir) {
        ir.setKind(a.getKind());
        ir.setRiskLevel(a.getRiskLevel());
        ir.setArgs(args);
        ir.setOk(false);
        try {
            AiActionConfirm c = gateway.requestConfirm(a, args, traceId, user);
            ir.setNeedsConfirm(true);
            ir.setConfirmId(c.getId());
            ir.setParamsDigest(c.getParamsDigest());
            ir.setExpireAt(c.getExpireAt() == null ? null : c.getExpireAt().toString());
            ir.setAnswer("动作 " + a.getCode() + " 属" + kindName(a.getKind()) + "、风险档=" + a.getRiskLevel()
                    + "，我不会自行执行。已按下述参数出确认票据 #" + c.getId()
                    + "（一次性、过期即废），须由<b>另一位</b>有确认权限的真人背书；"
                    + "确认后我仍以下令人本人的权限执行。"
                    + (ir.getMissing() == null ? "" : "<br>我还缺：" + ir.getMissing() + "（我不会编造主数据 ID，请补全或驳回。）"));
        } catch (BizException ex) {
            // 出票失败属预期拒绝路径（§6 红线/权限不足），已转为用户可读答复返回，不再上抛
            ir.setAnswer("出票失败：" + ex.getMessage() + "（§6 红线动作或权限不足时不会出票）");
        }
        return ir;
    }

    private String kindName(String kind) {
        return switch (kind == null ? "" : kind) {
            case "create" -> "新建类写操作";
            case "update" -> "修改类写操作";
            case "export" -> "导出";
            case "read" -> "读操作";
            default -> "动作类型 " + kind;
        };
    }

    /**
     * 巡检 job 专用：目录校验（read∧aiAllowed∧LOW）后，由 {@link AiLocalReadInvoker} 调
     * 与人完全相同的那条 Service 入口（job 线程已由 TenantContext.callAs 建立租户语义）。
     */
    public JsonNode runReadActionForInspect(LoginUser owner, String actionCode, String argsJson) {
        AiAction a = catalog.byCode(actionCode);
        if (a == null || !a.isAiAllowed() || !"read".equals(a.getKind()) || !"LOW".equalsIgnoreCase(a.getRiskLevel())) {
            throw new BizException(ErrorCode.FORBIDDEN, "巡检源必须是目录内只读低风险动作：" + actionCode);
        }
        // 创建人权限复核（与真人链路同一权限码）：失权即失效，job 不享有超越任何真人的读取面
        boolean admin = owner.isAdmin();
        if (!admin && (a.getPermCode() == null || owner.getPerms() == null || !owner.getPerms().contains(a.getPermCode()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "规则创建人已无该动作权限：" + a.getPermCode());
        }
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argsJson == null || argsJson.isBlank() ? "{}" : argsJson, Map.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "巡检参数模板不是合法 JSON: " + ex.getMessage());
        }
        // 控制器是切面代理：本地反射同样会触发 PermissionAspect（要求登录态），
        // 必须把"规则创建人"绑进本线程 UserContext —— 权限校验与人侧同一把尺子。
        LoginUser prev = UserContext.get();
        UserContext.set(owner);
        Object data;
        try {
            data = localReadInvoker.invoke(a, args);
        } finally {
            if (prev != null) {
                UserContext.set(prev);
            } else {
                UserContext.clear();
            }
        }
        return objectMapper.valueToTree(data);
    }

    /** 只回"这个人真的能读"的动作面 —— 越权剪枝发生在 AI 的认知层。 */
    public List<Map<String, Object>> discover(LoginUser user) {
        return visibleReadActions(user).stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", a.getCode());
                    m.put("url", a.getUrl());
                    m.put("method", a.getMethod());
                    m.put("permCode", a.getPermCode());
                    m.put("preconditions", a.getPreconditions());
                    return m;
                })
                .toList();
    }

    /** 我的能力地图：技能按本人权限剪枝，越权的技能连名字都不出现。 */
    public List<Map<String, Object>> skills(LoginUser user) {
        Set<String> visibleCodes = visibleActions(user, true).stream()
                .map(AiAction::getCode).collect(Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Skill s : skillRegistry.all()) {
            if (!s.getBacking().isEmpty() && !visibleCodes.containsAll(s.getBacking())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("type", s.getType());
            m.put("title", s.getTitle());
            m.put("description", s.getDescription());
            m.put("examples", s.getExamples());
            if (s.getDerived() != null) {
                m.put("kinds", s.getDerived().getKinds());
                m.put("permCodes", s.getDerived().getPermCodes());
            }
            out.add(m);
        }
        return out;
    }

    /** 健康面：模型通道/知识索引/技能注册表（不返回任何密钥）。 */
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        ExtInterfaceConfig model = connector.getConfig("AI_MODEL");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", model != null);
        if (model != null) {
            m.put("provider", model.getProvider());
            m.put("model", model.getModelName());
            m.put("status", model.getStatus());
            m.put("lastTestResult", model.getLastTestResult());
        }
        out.put("model", m);
        Map<String, Object> kb = new LinkedHashMap<>();
        kb.put("enabled", knowledgeIndex.isEnabled());
        kb.put("corpusHash", knowledgeIndex.corpusHash());
        out.put("knowledge", kb);
        Map<String, Object> sk = new LinkedHashMap<>();
        sk.put("total", skillRegistry.size());
        sk.put("rejected", skillRegistry.rejected());
        out.put("skills", sk);
        return out;
    }

    private List<AiAction> visibleReadActions(LoginUser user) {
        return visibleActions(user, false);
    }

    /**
     * 候选人机共用动作面：目录内 aiAllowed ∧ 非 §6 红线类型 ∧ 有权限码 ∧ 本人实际持码 ∧ 租户端点。
     * withWrites=false 时只留只读低风险（问数）；放开写动作时仍不含 approve/void/config/delete，
     * 且写动作到网关侧必然还要过票据校验——可见集宽窄不改变执行口径。
     */
    private List<AiAction> visibleActions(LoginUser user, boolean withWrites) {
        return visibleActions(user, withWrites, false, false);
    }

    /**
     * @param ignorePerm true 时放开"本人持码"这一层 —— 只用于判定"目录里有、但本人无权"。
     * @param readAnyRisk true 时放开"只读必须 LOW 档"这一层 —— 只用于判定
     *                     "目录里有、本人也持码，但风险档不是 LOW（问数通道只自动跑 LOW）"。
     */
    private List<AiAction> visibleActions(LoginUser user, boolean withWrites, boolean ignorePerm, boolean readAnyRisk) {
        boolean admin = user.isAdmin();
        return catalog.all().stream()
                .filter(a -> a.isAiAllowed() && !AiGatewayService.forbiddenKind(a.getKind()))
                .filter(a -> withWrites
                        || ("read".equals(a.getKind()) && (readAnyRisk || "LOW".equalsIgnoreCase(a.getRiskLevel()))))
                .filter(a -> a.getUrl() != null && a.getUrl().startsWith("/api/tenant/"))
                .filter(a -> ignorePerm || (a.getPermCode() != null
                        && (admin || (user.getPerms() != null && user.getPerms().contains(a.getPermCode())))))
                .toList();
    }

    /**
     * "权限/档位缺口"分态：把本人权限面（必要时连风险档）放开后再选一次，
     * 若此时存在匹配动作，说明拒绝的真实原因不是"目录里没有"——返回该动作，否则返回 null。
     * <p>两种命中：本人不持其权限码 ⇒ NO_PERM；本人持码但风险档非 LOW（问数通道只自动跑 LOW）⇒ CHANNEL_READ_ONLY。
     * 只在选型失败路径调用，正常问答路径零额外开销。
     */
    private AiAction permGapAction(String question, List<AiAction> visible, boolean withWrites, boolean readAnyRisk,
                                   ExtInterfaceConfig model, LoginUser user, LlmTrace llm) {
        try {
            List<AiAction> wide = visibleActions(user, withWrites, true, readAnyRisk);
            if (wide.size() == visible.size() && !readAnyRisk) {
                return null; // 放开权限面后候选集未变大 ⇒ 不存在权限缺口（放开档位时另论）
            }
            Selection w = selectByLlm(question, wide, withWrites, model, llm);
            if (w == null || w.action() == null) {
                Selection byRule = selectByKeyword(question, wide);
                w = (byRule != null && byRule.action() != null) ? byRule : w;
            }
            if (w == null || w.action() == null) {
                return null;
            }
            AiAction hit = w.action();
            String perm = hit.getPermCode();
            boolean lacks = user == null || (!user.isAdmin()
                    && (perm == null || user.getPerms() == null || !user.getPerms().contains(perm)));
            if (lacks || (readAnyRisk && !"LOW".equalsIgnoreCase(hit.getRiskLevel()))) {
                return hit;
            }
            return null; // 本人持码且档位本来就够，只是这一问确实没匹配上 ⇒ 归 NO_MATCH
        } catch (Exception ex) {
            log.warn("AI 拒答分态判定失败，按 NO_MATCH 处理：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * "红线缺口"分态：红线动作（aiAllowed=false）在建候选集时就被整族剔除，
     * 于是"目录里确实有、但被 §6 红线封禁"会被误报成 NO_MATCH（"目录里没有"与事实不符）。
     * 本探测单独在红线池里选一次，只用于解释拒答，绝不执行；
     * 命中即说明存在同义动作被封禁，由调用方连族名一起回给人看。
     * <p>池内只取 kind 不在 {@link AiGatewayService#forbiddenKind} 里的条目：类型级红线
     * （approve/void/config/delete）语义过于宽泛，放进来只会让"审批/删除"这类词胡乱命中；
     * 族级红线（无权限码、期间反关账、付款批执行、导出族等）才是最需要解释清楚的部分。
     */
    private AiAction redlineGapAction(String question, boolean withWrites, ExtInterfaceConfig model, LlmTrace llm) {
        try {
            List<AiAction> pool = redlinePool(withWrites);
            if (pool.isEmpty()) {
                return null;
            }
            // 用红线专用人设：这条链只要"识别得出"，不要模型自己替系统做"该不该干"的判断（见 ROLE_REDLINE）
            Selection s = selectByLlm(question, pool, withWrites, model, ROLE_REDLINE, llm);
            if (s == null || s.action() == null) {
                Selection byRule = selectByRedlineKeyword(question, pool);
                s = (byRule != null && byRule.action() != null) ? byRule : s;
            }
            return s == null ? null : s.action();
        } catch (Exception ex) {
            log.warn("AI 拒答红线态判定失败，按未命中处理：{}", ex.getMessage());
            return null;
        }
    }

    /** 红线动作池（族级探测用；类型级 approve/void/config/delete 不入池，理由见 redlineGapAction 注释）。 */
    private List<AiAction> redlinePool(boolean withWrites) {
        return catalog.all().stream()
                .filter(a -> !a.isAiAllowed() && !AiGatewayService.forbiddenKind(a.getKind()))
                .filter(a -> withWrites || "read".equals(a.getKind()))
                .filter(a -> a.getUrl() != null && a.getUrl().startsWith("/api/tenant/"))
                .toList();
    }

    /**
     * 确定性红线说法的探测池：与 {@link #redlinePool} 的差别是<b>不限 kind</b>。
     * 期间反关账、清空审计日志这类红线动作在目录里都是 write 类，而问数通道的红线池只留 read，
     * 于是「强制反关账并清空审计日志」在问数通道上根本看不见这条红线，被当成普通查询放了过去。
     * 这里的池只用于"识别并拒答"，永不执行，故不受通道 kind 限制。
     */
    private List<AiAction> redlineDetectionPool() {
        return catalog.all().stream()
                .filter(a -> !a.isAiAllowed() && !AiGatewayService.forbiddenKind(a.getKind()))
                .filter(a -> a.getUrl() != null && a.getUrl().startsWith("/api/tenant/"))
                .toList();
    }

    /**
     * 中文红线说法的确定性命中（不调模型）：反关账、清空审计日志、付款批执行、重置口令、销毁档案、吊销认证。
     *
     * <p>查询语气（"反关账是什么""怎么查审计日志"）不算红线——那是问概念，不是要动手。
     */
    private AiAction redlineByKeyword(String question, boolean withWrites) {
        if (looksLikeReadQuestion(question)) {
            return null;
        }
        List<AiAction> pool = redlineDetectionPool();
        if (pool.isEmpty()) {
            return null;
        }
        Selection s = selectByRedlineKeyword(question, pool);
        return s == null ? null : s.action();
    }

    /** 明显是"问东西"的语气：含这些词时不按红线动手意图处理。 */
    private static boolean looksLikeReadQuestion(String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        for (String w : new String[]{"查", "看", "多少", "哪些", "列表", "历史", "记录", "流程", "制度",
                "怎么", "如何", "是什么", "什么意思", "说明", "介绍", "哪里", "在哪"}) {
            if (q.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 红线池的确定性兜底：中文口语 → 端点特征。
     * <p>为什么需要它：大模型在这条链上会偶发"多诉求句/口语化句认不出"——同一句话在两次运行间
     * 可能一次命中红线、一次回 null，而"为什么不能做"是要如实回答用户的问题，不该靠模型运气。
     * 这几条把最常见的红线说法钉死，使红线拒答既有一致性、又不依赖外网通道。命中只用于解释，绝不执行（池内动作过不了网关）。
     * <p>谓词匹配"动词+宾语"的组合（而非单个名词），避免把"查一下审计日志"这类正常问句错认成红线。
     */
    private Selection selectByRedlineKeyword(String q, List<AiAction> pool) {
        record R(String qRegex, String urlRegex, String why) {
        }
        List<R> rules = List.of(
                new R("反关账|反结账|撤销关账",
                        "/unclose$", "期间反关账"),
                new R("强制关账|期间重开|反过账",
                        "/reopen$", "期间重开"),
                new R("(清空|清理|删除|抹除|篡改).{0,8}(日志|审计)",
                        "/process-log/clean$", "清理流程/审计日志"),
                new R("(执行|直接|跳过|放行|无需|不用).{0,12}付款|付款批.{0,12}(执行|直接|跳过|放行)",
                        "/pay/batch/\\{id\\}/(execute|confirm)$", "执行/确认付款批"),
                new R("重置.{0,8}(密码|口令)|密码重置|重置口令",
                        "/resetPwd$", "重置登录密码"),
                new R("销毁.{0,8}(档案|文件|凭证)|档案销毁",
                        "/(destroy|batch-destroy)$", "销毁档案"),
                new R("吊销.{0,8}(认证|证书|签名)|认证吊销",
                        "/(revoke|pre-certify|cancel-sign)$", "吊销认证/签名"));
        for (R rule : rules) {
            if (!q.matches("(?s).*(" + rule.qRegex() + ").*")) {
                continue;
            }
            AiAction a = pool.stream().filter(x -> x.getUrl().matches(".*(" + rule.urlRegex() + ").*"))
                    .findFirst().orElse(null);
            if (a != null) {
                return new Selection(a, Map.of(), "local-rule", "中文红线说法命中：" + rule.why());
            }
        }
        return null;
    }

    // LLM 面

    private record Selection(AiAction action, Map<String, Object> args, String engine, String reason) {
    }

    private static final String ROLE_ASK =
            "你是财务共享系统的只读问数助手。只能从给定动作清单中选一个最匹配的动作来回答用户问题；"
                    + "没有匹配项就返回 {\"action\":null,\"reason\":\"...\"}。";

    private static final String ROLE_INSTRUCT =
            "你是财务共享系统的指令执行助手。只能从给定动作清单中选一个最匹配的动作来完成用户指令，并按该端点语义给出 args；"
                    + "写动作会先经人工确认票据才真正执行，你无权跳过；清单里没有合适的动作就返回 {\"action\":null,\"reason\":\"...\"}。";

    /**
     * 红线探测专用人设。清单里给的全是**已被永久禁止调用**的动作，本环节只做"识别"不做"执行"。
     * 沿用常规指令人设会坏在"模型自己也认定这事不该干 ⇒ 回 action=null"，把"识别得出"变成"识别不出"
     * （「把今年所有单据的期间强制反关账并清空审计日志」在常规人设下就被判成"清单中不存在"，
     * 而 /gl/month/unclose 明明在池里）。
     */
    private static final String ROLE_REDLINE =
            "你是财务共享系统的动作识别器。下面这份清单是**系统已永久禁止由 AI 调用**的动作，用途只有一个："
                    + "当用户要求做某件事时，如实告诉他「这件事对应的动作被禁止了」。本环节不会执行任何动作，"
                    + "你只需指出用户这句话在语义上指向清单里的哪一个动作。"
                    + "即使指令违规、即使该动作危险，也必须选出最贴近的动作码：点名禁止项是为了解释，不会造成越权；"
                    + "只有清单里确实没有任何语义相关项时，才返回 {\"action\":null,\"reason\":\"...\"}。"
                    + "注意中文口语与英文端点名的对应，例如：反关账／反结账／期间重开 → unclose 或 {id}/reopen；"
                    + "清空／清理日志 → process-log/clean；绕过审批直接执行付款批 → pay/batch/{id}/execute。";

    /** OpenAI-compatible chat/completions 报文（Qianfan 兼容端点同形）。模型只许返回 {"action":...,"args":...}。 */
    private Selection selectByLlm(String question, List<AiAction> visible, boolean withWrites, ExtInterfaceConfig cfg,
                                  LlmTrace llm) {
        return selectByLlm(question, visible, withWrites, cfg, withWrites ? ROLE_INSTRUCT : ROLE_ASK, llm);
    }

    /** @param role 选型人设：问数 {@link #ROLE_ASK}／指令 {@link #ROLE_INSTRUCT}／红线识别 {@link #ROLE_REDLINE}。 */
    private Selection selectByLlm(String question, List<AiAction> visible, boolean withWrites, ExtInterfaceConfig cfg,
                                  String role, LlmTrace llm) {
        if (cfg == null) {
            llm.fail("未配置已启用的 AI_MODEL 接口");
            return null;
        }
        if (!"ENABLED".equals(cfg.getStatus())) {
            llm.fail("AI_MODEL 状态=" + cfg.getStatus());
            return null;
        }
        try {
            List<Map<String, Object>> compact = new ArrayList<>();
            for (AiAction a : visible) {
                if (compact.size() >= 300) {
                    break; // 提示词预算：300 个动作足够覆盖问数场景，防止 token 爆炸
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", a.getCode());
                item.put("url", a.getUrl());
                item.put("method", a.getMethod());
                item.put("perm", String.valueOf(a.getPermCode()));
                if (withWrites) {
                    item.put("kind", a.getKind());
                    item.put("risk", a.getRiskLevel());
                }
                compact.add(item);
            }
            String systemPrompt = role
                    + "绝对不允许发明清单外的动作。回答必须是单个 JSON 对象：{\"action\":\"动作码\",\"args\":{参数},\"reason\":\"一句话\"}。"
                    + "\n动作清单(JSON)：" + objectMapper.writeValueAsString(compact);
            Map<String, Object> body = chatBody(cfg, systemPrompt, question);
            var resp = connector.invokeRead("AI_MODEL", body);
            if (!resp.isOk()) {
                String detail = upstreamDetail(resp);
                log.warn("AI 问数：大模型通道失败 status={} detail={}", resp.getStatusCode(), detail);
                llm.fail(detail);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                llm.fail("模型响应无 choices/content");
                return null;
            }
            JsonNode j = objectMapper.readTree(extractJson(content));
            String code = j.path("action").isNull() ? null : j.path("action").asText(null);
            if (code == null) {
                return new Selection(null, Map.of(), "llm:" + cfg.getModelName(), j.path("reason").asText("模型判定无匹配动作"));
            }
            AiAction a = visible.stream().filter(x -> x.getCode().equals(code)).findFirst().orElse(null);
            if (a == null) {
                // 模型越界发明动作 —— 按"未登记=不可调用"处理，回落关键词
                log.warn("AI 问数：模型返回目录外动作 {}", code);
                return null;
            }
            Map<String, Object> args = objectMapper.convertValue(j.path("args"), Map.class);
            return new Selection(a, args == null ? Map.of() : args, "llm:" + cfg.getModelName(), j.path("reason").asText(""));
        } catch (Exception ex) {
            log.warn("AI 问数：LLM 解析失败，回落本地匹配：{}", ex.getMessage());
            llm.fail("调用/解析异常 " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private String extractJson(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : "{}";
    }

    // 第二次调用：照结构填参

    private record Fill(Map<String, Object> args, String missing) {
    }

    /**
     * chat/completions 请求体（OpenAI 兼容形）。{@code enable_thinking=false}：
     * 选型与填参都是机械活，开思考链会让一次填参的耗时膨胀十倍以上，足以拖过模型通道超时、
     * 把整条填参链吃掉。
     */
    private Map<String, Object> chatBody(ExtInterfaceConfig cfg, String systemPrompt, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("messages", List.of(Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)));
        body.put("temperature", 0);
        body.put("enable_thinking", false);
        return body;
    }

    /**
     * 是否需要"照结构填参"：写动作一律需要（失败面最大）；只读动作仅在选型没给出参数、
     * 且该端点确实有查询/路径参数时才补，避免为翻页这类小事多花一次模型时延。
     */
    private boolean needFillArgs(AiAction a, Map<String, Object> args, ExtInterfaceConfig cfg) {
        if (cfg == null || !"ENABLED".equals(cfg.getStatus()) || a.getSchema() == null) {
            return false;
        }
        boolean read = "read".equals(a.getKind()) && "LOW".equalsIgnoreCase(a.getRiskLevel());
        return !read || args.isEmpty();
    }

    /**
     * 目录声明了请求体或必填参数，而模型一个值都没填上 —— 这种票不该出。
     * 只读/无 schema 的动作不受此约束（宁松勿严，不误伤本来就没有参数的写端点）。
     */
    private boolean declaresArgs(AiAction a) {
        JsonNode s = a.getSchema();
        if (s == null || !s.isObject()) {
            return false;
        }
        if (s.path("body").isObject() && !s.path("body").isEmpty()) {
            return true;
        }
        for (String bucket : List.of("path", "query")) {
            for (JsonNode p : s.path(bucket)) {
                if (p.path("req").asBoolean(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 把动作的参数结构交给模型，只让它填值、不让它猜字段名；失败即回 null 由调用方沿用原 args。 */
    private Fill fillArgs(AiAction a, String question, ExtInterfaceConfig cfg) {
        try {
            String systemPrompt = "你是财务共享系统的接口填参器。根据用户指令为指定端点产出调用参数，只输出一个 JSON 对象，不要解释、不要代码块围栏。\n"
                    + "规则：\n"
                    + "1) 只能用下面「参数结构」里出现的键，层级照抄；结构里的类型名与 /*注释*/ 只是说明，不是取值。\n"
                    + "2) 用户说了的才填；用户没说的可选字段一律省略，不要凑字段。\n"
                    + "3) 主数据引用（各类 ID／编码／用户／公司／部门／费用项／科目／供应商）用户没给就绝不编造，宁可留空——"
                    + "缺口会写进确认票据给真人看。\n"
                    + "4) 金额用数字，日期用 yyyy-MM-dd，今天是 " + LocalDate.now() + "。\n"
                    + "5) 输出 {\"args\":{...},\"missing\":\"按必填清单还缺什么，没有则空串\"}。\n"
                    + "端点：" + a.getMethod() + " " + a.getUrl() + "\n"
                    + "前置条件：" + a.getPreconditions() + "\n"
                    + renderArgsShape(a.getSchema());
            Map<String, Object> body = chatBody(cfg, systemPrompt, question);
            var resp = connector.invokeRead("AI_MODEL", body);
            if (!resp.isOk()) {
                log.warn("AI 指令：填参通道失败 status={} err={}", resp.getStatusCode(), resp.getError());
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode j = objectMapper.readTree(extractJson(content));
            JsonNode argsNode = j.path("args");
            if (!argsNode.isObject() || argsNode.isEmpty()) {
                return null;
            }
            Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);
            String missing = j.path("missing").asText("");
            return new Fill(args, missing.isBlank() ? null : missing);
        } catch (Exception ex) {
            log.warn("AI 指令：填参解析失败，沿用选型阶段的参数：{}", ex.getMessage());
            return null;
        }
    }

    /** 参数结构 → 提示词里的"args 形状"：顶层参数与请求体层级、类型、枚举、必填清单。 */
    private String renderArgsShape(JsonNode schema) {
        List<String> required = new ArrayList<>();
        List<String> top = new ArrayList<>();
        for (String bucket : List.of("path", "query")) {
            for (JsonNode p : schema.path(bucket)) {
                String n = p.path("n").asText();
                top.add(n + "=" + leafHint(p));
                if (p.path("req").asBoolean(false)) {
                    required.add(n);
                }
            }
        }
        StringBuilder sb = new StringBuilder("参数结构：\n");
        if (!top.isEmpty()) {
            sb.append("· 顶层（路径/查询）参数：").append(String.join("；", top)).append('\n');
        }
        JsonNode body = schema.path("body");
        if (body.isObject()) {
            if (body.path("free").asBoolean(false)) {
                List<String> ks = new ArrayList<>();
                for (JsonNode k : body.path("k")) {
                    ks.add(k.asText());
                }
                sb.append("· 请求体是自由 JSON，已知键：").append(String.join("、", ks)).append('\n');
            } else {
                sb.append("· 请求体（写在这些键上，可整体省略）：")
                        .append(renderFields(body.path("f"), required, "")).append('\n');
            }
        }
        sb.append("必填：").append(required.isEmpty() ? "结构里未声明必填（写动作请只填用户明确给出的值）"
                : String.join("、", required));
        return sb.toString();
    }

    private String renderFields(JsonNode fields, List<String> required, String prefix) {
        if (!fields.isArray() || fields.isEmpty()) {
            return "{…}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (JsonNode f : fields) {
            String n = f.path("n").asText();
            String at = prefix.isEmpty() ? n : prefix + "." + n;
            if (f.path("req").asBoolean(false)) {
                required.add(at);
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            JsonNode sub = f.path("f");
            boolean arr = f.path("arr").asBoolean(false);
            sb.append('"').append(n).append("\": ");
            if (sub.isArray() && !sub.isEmpty()) {
                String inner = renderFields(sub, required, arr ? at + "[]" : at);
                sb.append(arr ? "[" + inner + "]" : inner);
            } else {
                sb.append(leafHint(f));
            }
        }
        return sb.append('}').toString();
    }

    private String leafHint(JsonNode f) {
        String t = f.path("t").asText("?");
        List<String> es = new ArrayList<>();
        for (JsonNode x : f.path("e")) {
            es.add(x.asText());
        }
        if (!es.isEmpty()) {
            t = t + "{" + String.join("|", es) + "}";
        }
        String d = f.path("d").asText("");
        if (!d.isEmpty()) {
            t = t + "/*" + (d.length() > 22 ? d.substring(0, 22) : d) + "*/";
        }
        return t;
    }

    // 确定性兜底

    /** 是否是"要办一件事"（而非问一件事）：有办理动词、且没有查询类字眼。 */
    private static boolean looksLikeWriteIntent(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        if (q.contains("待办") || q.contains("待审") || q.contains("待我审批") || q.contains("任务池")) {
            return false;
        }
        for (String read : new String[]{"查", "看", "多少", "哪些", "列表", "统计", "有没有", "是什么", "明细"}) {
            if (q.contains(read)) {
                return false;
            }
        }
        for (String w : new String[]{"创建", "新建", "建单", "帮我建", "填写", "填单", "录入", "提交", "发起",
                "申请", "修改", "更新", "删除", "作废", "撤销", "撤回", "审批", "审核", "同意", "驳回",
                "转办", "派工", "付款", "核销", "归档"}) {
            if (q.contains(w)) {
                return true;
            }
        }
        return (q.contains("报销") || q.contains("报账")) && (q.contains("我要") || q.contains("帮我") || q.contains("申请"));
    }

    private static final Pattern MONTH_PATTERN =
            Pattern.compile("(20\\d{2})\\s*[-年/]\\s*(\\d{1,2})\\s*月?");

    /** 绕过规则/提示词注入识别：只做解释，不出票、不执行。 */
    private static boolean looksLikeBypass(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        return q.matches("(?s).*忽略.{0,8}(以上|之前|上述|前面).{0,8}(规则|指令|限制|要求).*")
                || q.matches("(?s).*(ignore|disregard|forget).{0,20}(previous|above|all|earlier).{0,20}(instruction|rule|prompt).*")
                || q.matches("(?s).*(跳过|绕过|无需|不用|不必).{0,10}(审批|审核|确认|票据|双人|规则|权限).*");
    }

    /** 类型级红线识别：只在"明确的办理语气"上命中，查询类（查/哪些/多少/历史/流程）不受影响。 */
    private static String forbiddenKindOf(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        // 批量审批类：「把今年所有单据都审批通过」——量大＋审批动词＝要动手，不是查询。
        // 放在"查询词提前返回"之前：这类句子里常带"所有/全部"，正是要动手的信号。
        if (q.matches("(?s).*(所有|全部|批量|这批|每张|每笔|逐一).{0,12}(审批|审核|批准|通过|驳回|同意).*")
                || q.matches("(?s).*(审批|审核|批准|驳回|同意).{0,8}(所有|全部|批量).*")) {
            return "approve";
        }
        for (String read : new String[]{"查", "哪些", "多少", "列表", "历史", "记录", "流程", "制度", "怎么", "如何"}) {
            if (q.contains(read)) {
                return null;
            }
        }
        if (q.matches("(?s).*(帮我|替我|你给我|直接|立刻|马上|现在).{0,6}(审批|审核|批准|通过|同意|驳回).*")
                || q.matches("(?s).*(审批|审核|批准|驳回).{0,6}(这张|这个|该|此)[单票].*")
                || q.matches("(?s).*(把|将).{0,8}(这张|这个|该|此)?[单票].{0,6}(审批|审核|通过|驳回).*")) {
            return "approve";
        }
        if (q.matches("(?s).*(删除|删掉|作废|废止).{0,10}(这张|这个|该|此)?(草稿|单据|单|票|申请).*")) {
            return "delete";
        }
        if (q.matches("(?s).*(修改|更改|调整|重置).{0,8}(配置|系统参数|权限|密码).*")) {
            return "config";
        }
        return null;
    }

    private static String forbiddenName(String kind) {
        return switch (kind) {
            case "approve" -> "审批/审核";
            case "delete" -> "删除/作废";
            case "config" -> "修改配置/权限";
            default -> kind;
        };
    }

    /** 从问句里抽"上个月/本月/今年/去年/YYYY年M月"→ startDate/endDate；抽不到原样返回。 */
    private static Map<String, Object> withDateArgs(Map<String, Object> args, String q) {
        LocalDate today = LocalDate.now();
        LocalDate from = null;
        LocalDate to = null;
        if (q.contains("上个月") || q.contains("上月")) {
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            from = first;
            to = first.withDayOfMonth(first.lengthOfMonth());
        } else if (q.contains("本月") || q.contains("这个月") || q.contains("当月")) {
            from = today.withDayOfMonth(1);
            to = today.withDayOfMonth(today.lengthOfMonth());
        } else if (q.contains("今年")) {
            from = today.withDayOfYear(1);
            to = today.withDayOfYear(today.lengthOfYear());
        } else if (q.contains("去年")) {
            LocalDate last = today.minusYears(1);
            from = last.withDayOfYear(1);
            to = last.withDayOfYear(last.lengthOfYear());
        } else {
            Matcher m = MONTH_PATTERN.matcher(q);
            if (m.find()) {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                if (mo >= 1 && mo <= 12) {
                    from = LocalDate.of(y, mo, 1);
                    to = from.withDayOfMonth(from.lengthOfMonth());
                }
            }
        }
        if (from == null) {
            return args;
        }
        Map<String, Object> out = new LinkedHashMap<>(args);
        out.put("startDate", from.toString());
        out.put("endDate", to.toString());
        return out;
    }

    private static String dateNote(Map<String, Object> args) {
        Object from = args.get("startDate");
        Object to = args.get("endDate");
        return from == null ? "" : "（时间 " + from + "~" + to + "）";
    }

    /** 问句里的单据状态词 → 枚举值；没有就返回 null（不猜）。 */
    private static String statusArgOf(String q) {
        String[][] pairs = {
                {"草稿", "DRAFT"}, {"已批准", "APPROVED"}, {"已驳回", "REJECTED"}, {"已作废", "VOID"},
                {"已撤回", "WITHDRAWN"}, {"已付款", "PAID"}, {"已记账", "POSTED"},
                {"已完成", "COMPLETED"}, {"已还款", "REPAID"}};
        for (String[] p : pairs) {
            if (q.contains(p[0])) {
                return p[1];
            }
        }
        return null;
    }

    /** 关键词本地匹配（无大模型也能跑通；动作仍全部来自目录、仍过网关）。 */
    private Selection selectByKeyword(String q, List<AiAction> visible) {
        record Rule(String[] keywords, String urlRegex, Map<String, Object> args, String reason) {
        }
        Map<String, Object> page = Map.of("pageNum", 1, "pageSize", 50);
        List<Rule> rules = List.of(
                new Rule(new String[]{"待办", "待审", "待我处理", "待我审批", "待审批"}, "/wf/pool/mine$",
                        Map.of("view", "MY_TODO", "pageNum", 1, "pageSize", 50), "我的待办（任务池 MY_TODO 视图）"),
                new Rule(new String[]{"未派工", "派工", "对账任务", "任务池"}, "/sharing/task/pool$",
                        page, "共享任务池=未派工对账任务"),
                new Rule(new String[]{"我的共享任务", "我的任务"}, "/sharing/task/mine$", page, "我的共享任务"),
                new Rule(new String[]{"报销单", "我的单据", "我的报销", "单据", "查单"}, "/expense/bill/myPage$", page, "我的单据"),
                new Rule(new String[]{"发票"}, "/invoice/page$", page, "公司发票列表"),
                new Rule(new String[]{"凭证"}, "/sharing/voucher/page$", page, "公司凭证列表"),
                new Rule(new String[]{"抄送"}, "/wf/query/cc/mine$", page, "我的抄送"));
        for (Rule rule : rules) {
            for (String k : rule.keywords()) {
                if (!q.contains(k)) {
                    continue;
                }
                AiAction a = visible.stream().filter(x -> x.getUrl().matches(".*" + rule.urlRegex()))
                        .findFirst().orElse(null);
                if (a != null) {
                    Map<String, Object> args = withDateArgs(rule.args(), q);
                    String reason = rule.reason() + dateNote(args);
                    String status = statusArgOf(q);
                    if (status != null && rule.urlRegex().contains("expense/bill/myPage")) {
                        Map<String, Object> withStatus = new LinkedHashMap<>(args);
                        withStatus.put("status", status);
                        args = withStatus;
                        reason += "，状态=" + statusLabel(status);
                    }
                    return new Selection(a, args, "local-rule", reason);
                }
            }
        }
        return null;
    }

    /** 通道故障说明：没有故障返回空串；有故障时如实标注（不把 403 粉饰成"没听懂"）。 */
    private String channelNote(LlmTrace llm, String fallback) {
        if (llm == null || llm.error == null) return "";
        return "（大模型通道本次不可用：" + llm.error + "；" + fallback + "）";
    }

    /**
     * 成功读数的确定性中文答案：条数＋前几条关键字段，数据全部来自本次真实响应，不做推测。
     * 只回"结果见 data"等于把原始 JSON 丢给用户——这不是回答。
     */
    private String answerOf(AiAction a, Object data, LlmTrace llm) {
        if (data == null) {
            return "已调用 " + labelOf(a) + "，接口没有返回数据。" + channelNote(llm, "本次由本地规则选型");
        }
        StringBuilder sb = new StringBuilder("已读取「").append(labelOf(a)).append("」：");
        if (data instanceof Map<?, ?> m && m.get("top5") instanceof List<?> top) {
            Object total = m.get("total");
            sb.append("共 ").append(total instanceof Number n ? n.intValue() : top.size()).append(" 条");
            if (top.isEmpty()) {
                sb.append("，当前没有数据。");
            } else {
                sb.append("。前 ").append(top.size()).append(" 条：");
                for (int i = 0; i < top.size(); i++) {
                    if (i > 0) {
                        sb.append("；");
                    }
                    sb.append(rowBrief(top.get(i)));
                }
                sb.append("。");
            }
        } else if (data instanceof List<?> list) {
            sb.append("共 ").append(list.size()).append(" 条");
            if (list.isEmpty()) {
                sb.append("，当前没有数据。");
            } else {
                sb.append("。前 ").append(Math.min(3, list.size())).append(" 条：");
                for (int i = 0; i < Math.min(3, list.size()); i++) {
                    if (i > 0) {
                        sb.append("；");
                    }
                    sb.append(rowBrief(list.get(i)));
                }
                sb.append("。");
            }
        } else if (data instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                return sb.append("接口没有返回数据。").append(channelNote(llm, "本次由本地规则选型")).toString();
            }
            sb.append("返回 ").append(m.size()).append(" 个字段");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (parts.size() >= 5) {
                    break;
                }
                Object v = e.getValue();
                if (v == null || v instanceof Map || v instanceof List) {
                    continue;
                }
                parts.add(e.getKey() + "=" + brief(String.valueOf(v), 40));
            }
            if (!parts.isEmpty()) {
                sb.append("：").append(String.join("，", parts));
            }
            sb.append("。");
        } else {
            sb.append(brief(String.valueOf(data), 200)).append("。");
        }
        return sb.append(channelNote(llm, "本次由本地规则选型")).toString();
    }

    /** 单行摘要：优先单号/名称，其次标题、节点、金额、状态、时间；字段不存在就不写，绝不补造。 */
    private String rowBrief(Object row) {
        if (!(row instanceof Map<?, ?> m)) {
            return brief(String.valueOf(row), 60);
        }
        List<String> parts = new ArrayList<>();
        String id = first(m, "billNo", "invoiceNo", "voucherNo", "code", "name");
        if (id != null) {
            parts.add(id);
        }
        String title = first(m, "title", "billTitle");
        if (title != null && !title.equals(id)) {
            parts.add("「" + title + "」");
        }
        String node = first(m, "nodeName", "node_name");
        if (node != null) {
            parts.add("节点 " + node);
        }
        String amount = first(m, "amount", "totalAmount", "total_amount", "amt");
        if (amount != null) {
            parts.add("金额 " + money(amount));
        }
        String status = first(m, "statusName", "status", "poolStatus");
        if (status != null) {
            parts.add("状态 " + statusLabel(status));
        }
        String time = first(m, "createTime", "billDate", "applyDate");
        if (time != null) {
            parts.add(time.replace('T', ' '));
        }
        String due = first(m, "dueTime", "due_time");
        if (due != null) {
            parts.add("截止 " + due.replace('T', ' '));
        }
        return parts.isEmpty() ? "1 行记录" : String.join("，", parts);
    }

    /** 状态码转中文；未收录的保持原样，不猜。 */
    private static String statusLabel(String s) {
        return STATUS_LABELS.getOrDefault(s.trim().toUpperCase(), s);
    }

    /** 金额加千分位、保留两位；解析不了就原样返回。 */
    private static String money(String s) {
        try {
            return String.format("%,.2f", new BigDecimal(s.replace(",", "")));
        } catch (Exception e) {
            return s;
        }
    }

    private String first(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return brief(String.valueOf(v), 60);
            }
        }
        return null;
    }

    private String labelOf(AiAction a) {
        String url = a.getUrl() == null ? "" : a.getUrl();
        if (url.endsWith("/wf/pool/mine")) return "我的待办";
        if (url.contains("/expense/bill/myPage")) return "我的单据";
        if (url.endsWith("/sharing/task/pool")) return "共享任务池";
        if (url.endsWith("/sharing/task/mine")) return "我的共享任务";
        if (url.endsWith("/invoice/page")) return "公司发票列表";
        if (url.endsWith("/sharing/voucher/page")) return "公司凭证列表";
        if (url.endsWith("/wf/query/cc/mine")) return "我的抄送";
        return a.getCode();
    }

    /**
     * 通道失败原因：网关 error（已含 HTTP 码）＋响应体里的上游错误码/信息。
     * 用户要能一眼看出是欠费、鉴权还是模型名写错，而不是只看到 "HTTP 403"。
     */
    private String upstreamDetail(ChannelResponse resp) {
        String err = resp.getError();
        String base = err == null || err.isBlank() ? "HTTP " + resp.getStatusCode() : err;
        String body = resp.getBody();
        if (body == null || body.isBlank()) {
            return base;
        }
        try {
            JsonNode n = objectMapper.readTree(body);
            String code = n.path("error").path("code").asText(n.path("code").asText(""));
            String msg = n.path("error").path("message").asText(n.path("message").asText(""));
            String peek = (code + " " + msg).trim();
            if (!peek.isBlank()) {
                return base + "（" + brief(peek, 120) + "）";
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体（网关 HTML/纯文本）退回原文截断
        }
        return base + "（" + brief(body, 120) + "）";
    }

    private static String brief(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }

    /** 单次问答内的模型通道故障记录（首次失败即定，后续回退调用不覆盖）。 */
    private static final class LlmTrace {
        private String error;

        void fail(String e) {
            if (error == null && e != null && !e.isBlank()) {
                error = brief(e, 160);
            }
        }
    }


    /** data 摘要：列表只回计数＋前 5 行，避免把整页数据塞进对话气泡。入参为 {@link AiGatewayService.GatewayResult#data()} 的普通 Java 值。 */
    private Object summarize(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof Map<?, ?> m && m.get("list") instanceof List<?> list) {
            Map<String, Object> out = new LinkedHashMap<>();
            Object total = m.get("total");
            out.put("total", total instanceof Number n ? n.intValue() : list.size());
            List<Object> head = new ArrayList<>();
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                head.add(list.get(i));
            }
            out.put("top5", head);
            return out;
        }
        return data;
    }

    @lombok.Data
    public static class AskResult {
        private String question;
        private String traceId;
        private String engine;
        private String action;
        private String actionUrl;
        private String permCode;
        private String reason;
        private boolean ok;
        private String answer;
        /**
         * 拒答原因（也是"AI 清楚自己不能做什么"的机器可判定项）：
         * NO_MATCH＝目录内确无匹配动作；NO_PERM＝动作存在但本人不持其权限码；
         * REDLINE_BLOCKED＝动作在目录里存在，但被标为 AI 不可调用（§6 红线族／无权限码最严规则），永久不可执行；
         * CHANNEL_READ_ONLY＝命中的只读动作风险档非 LOW，问数通道只自动跑 read∧LOW（不执行写类）；
         * PARAM_MISSING＝必填参数一个没填上故不出票；EXEC_DENIED＝执行被服务端拒绝。非拒答时为 null。
         */
        private String refusalReason;
        /** 本次大模型通道的真实故障（如 "HTTP 403 ..."）；通道正常或无配置时为 null。 */
        private String channelError;
        private Object data;
        /** 命中的技能名（确定性路由）或 null（模型/兜底路径）。 */
        private String skill;
        /** 会话号：前端回传后，"改成1200/再填一张"可继承上一轮上下文。 */
        private String sessionId;
        /** 导航技能的落点路由（如 #/approval）。 */
        private String route;
        /** 知识问答的出处（与 answer 里的 [1][2] 对应）。 */
        private List<Citation> citations;
        /** 写票据的人话预览（做什么、金额、申请人、公司、影响范围）。 */
        private String preview;
        /** 复合写指令的逐句结果（只在一次话说多件事时出现）。 */
        private List<StepResult> steps;
        /** 本次实际调用动作用的参数（读技能＝技能映射产出的查询条件；写技能＝票据参数）。 */
        private Map<String, Object> args;
    }

    /** 知识出处。 */
    @lombok.Data
    public static class Citation {
        private String title;
        private String docPath;
        private String heading;
        private double score;

        public Citation() {
        }

        public Citation(String title, String docPath, String heading, double score) {
            this.title = title;
            this.docPath = docPath;
            this.heading = heading;
            this.score = score;
        }
    }

    /** 复合指令的单步结果（如"借款一张 + 报销一张"各自一张票）。 */
    @lombok.Data
    public static class StepResult {
        private String question;
        private String skill;
        private String answer;
        private String preview;
        private boolean ok;
        private boolean needsConfirm;
        private Long confirmId;
        private String paramsDigest;
        private String expireAt;
        private String refusalReason;
        private String kind;
        private String riskLevel;
        private String action;
        private Map<String, Object> args;
    }

    /** 指令通道结果：在问数结果之上多"动作类型/风险档/参数/票据"四件，前端据此渲染确认卡与轮询。 */
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class InstructResult extends AskResult {
        private String kind;
        private String riskLevel;
        private boolean needsConfirm;
        private Long confirmId;
        private String paramsDigest;
        private String expireAt;
        /** 填参阶段模型自报的缺口（主数据 ID 之类它不该编的东西），供确认人看。 */
        private String missing;
    }
}
