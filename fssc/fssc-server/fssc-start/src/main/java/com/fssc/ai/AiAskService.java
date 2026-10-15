package com.fssc.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.trace.TraceIdFilter;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.connector.api.ConnectorFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3 只读问答编排（自然语言 → 目录内 read 动作 → 网关执行 → 答案＋口径＋权限判定过程）。
 *
 * <p>三条纪律：
 * <ol>
 *   <li>候选动作 = 目录里 kind=read ∧ aiAllowed ∧ risk=LOW ∧ 下令真人实际持有其权限码
 *       —— LLM 连"看不见的动作"都不知道，越权在提示词层就被剪枝；执行仍过网关全套闸门。</li>
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

    public AskResult ask(String question, HttpServletRequest http, LoginUser user, String traceId) {
        List<AiAction> visible = visibleReadActions(user);
        return runChooseThenExecute(question, visible, false, http, user, traceId, new AskResult());
    }

    /**
     * 自然语言指令 → 目录内动作（前端 AI 指令台的入口）。
     *
     * <p>只分两挡：只读低风险立即执行；其余（写/较高风险）不执行，改为按参数摘要出一张确认票据，
     * 等<b>另一位</b>持 {@code ai:confirm:approve} 的真人背书后，由前端持 confirmId 走
     * {@code POST /api/ai/gateway/execute} 真正落地。本方法自身零业务判断，红线与权限仍由网关把。
     */
    public InstructResult instruct(String question, HttpServletRequest http, LoginUser user, String traceId) {
        List<AiAction> visible = visibleActions(user, true);
        return (InstructResult) runChooseThenExecute(question, visible, true, http, user, traceId, new InstructResult());
    }

    /** 选型 →（只读直跑 ／ 写则出票）的统一编排，ask 与 instruct 共用同一条执行路径。 */
    private AskResult runChooseThenExecute(String question, List<AiAction> visible, boolean withWrites,
                                          HttpServletRequest http, LoginUser user, String traceId, AskResult r) {
        ExtInterfaceConfig model = connector.getConfig("AI_MODEL");
        Selection sel = selectByLlm(question, visible, withWrites, model);
        // 模型显式拒答也走关键词兜底：规则只在"本人可见"集内匹配，不会造成越权
        if (sel == null || sel.action() == null) {
            Selection byRule = selectByKeyword(question, visible);
            if (byRule != null && byRule.action() != null) {
                sel = byRule;
            }
        }
        r.setQuestion(question);
        r.setTraceId(traceId);
        r.setEngine(sel == null ? null : sel.engine());
        r.setReason(sel == null ? null : sel.reason());

        if (sel == null || sel.action() == null) {
            // D-N17：拒答分态并落 refusal_reason —— 原实现只判"缺权限"一维，而红线动作（aiAllowed=false）
            // 在候选集构筑时就被剔除，于是"目录里有、但被 §6 红线封禁"被误报成"目录里没有"。
            // 此处按 红线 → 缺码 → 通道档位 的次序单独探测（只做解释，绝不执行），三态不再是死状态。
            AiAction redlined = redlineGapAction(question, withWrites, model);
            AiAction gap = redlined == null
                    ? permGapAction(question, visible, withWrites, !withWrites, model, user) : null;
            if (redlined != null) {
                // reason 也一并改写为系统口径：首轮 reason 是模型对"常规候选集"的评述，在红线路上它会说
                // "动作清单中不存在…"，而事实是"目录里有、只是被红线封了"——与 refusalReason 自相矛盾。
                r.setReason("该请求指向的动作已被系统红线封禁："
                        + redlined.getCode()
                        + (redlined.getRedlineFamily() == null ? "" : "（族：" + redlined.getRedlineFamily() + "）"));
                r.setRefusalReason("REDLINE_BLOCKED");
                r.setAction(redlined.getCode());
                r.setActionUrl(redlined.getMethod() + " " + redlined.getUrl());
                r.setPermCode(redlined.getPermCode());
                log.info("AI_REFUSAL reason=REDLINE_BLOCKED channel={} user={} action={} family={} question={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        redlined.getCode(), redlined.getRedlineFamily(), question, traceId);
                r.setAnswer("这个动作在目录里存在（" + redlined.getCode() + "），但被标为 AI 不可调用"
                        + (redlined.getRedlineFamily() == null ? "" : "，封禁族「" + redlined.getRedlineFamily() + "」")
                        + "：网关侧一律 403、确认票据也不受理，我不会执行它。请由真人在对应页面上办理。"
                        + "（refusal=REDLINE_BLOCKED）");
                return r;
            }
            if (gap != null) {
                String perm = gap.getPermCode();
                boolean lacks = user == null || (!user.isAdmin()
                        && (perm == null || user.getPerms() == null || !user.getPerms().contains(perm)));
                // 同红线路：reason 改写为系统口径，避免与 refusalReason 各说各话
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
            r.setRefusalReason("NO_MATCH");
            log.info("AI_REFUSAL reason=NO_MATCH channel={} user={} question={} traceId={}",
                    withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(), question, traceId);
            r.setAnswer("目录里没有能回答这个问题的动作（我不会猜测数字，也不会自行造动作）。");
            return r;
        }
        AiAction a = sel.action();
        Map<String, Object> args = sel.args() == null ? Map.of() : sel.args();
        // 目录 v2：动作带机械采集的参数结构时，第二次调用只干"照结构填参"这一件事——
        // 模型不再凭一行 Java 签名猜字段名（D-N12 的根因）。填参失败即沿用选型阶段的 args，不猜。
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
            // D-N17：此分支是"问数通道候选集只有 read∧LOW"这一不变量的守护——
            // 正常走不到（ask 的候选集本就剪过档位），"读动作但档位非 LOW"的解释性拒答
            // 由上面的 permGapAction（readAnyRisk）产出。留着它只为剪枝口径若被改动时不至于静默执行。
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
            r.setData(summarize(g.data()));
            if (!r.isOk()) {
                r.setRefusalReason("EXEC_DENIED");
                log.warn("AI_REFUSAL reason=EXEC_DENIED channel={} user={} action={} bizCode={} msg={} traceId={}",
                        withWrites ? "instruct" : "ask", user == null ? null : user.getUsername(),
                        a.getCode(), g.bizCode(), g.errorMsg(), traceId);
                r.setAnswer("动作执行被服务端拒绝：" + g.errorMsg() + "（权限码 " + a.getPermCode() + "，traceId=" + traceId + "）");
            } else {
                r.setAnswer("已通过只读动作 " + a.getCode() + " 取数，结果见 data；数据口径=该端点的查询条件，权限判定=用户实际持有的 " + a.getPermCode() + "。");
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

    /** 写／较高风险：只出票不执行；票据摘要由网关按同一规则算，参数改动即作废。 */
    private InstructResult issueTicket(AiAction a, Map<String, Object> args, String question, String traceId,
                                       LoginUser user, InstructResult ir) {
        ir.setKind(a.getKind());
        ir.setRiskLevel(a.getRiskLevel());
        ir.setArgs(args);
        ir.setOk(false);
        try {
            com.fssc.ai.entity.AiActionConfirm c = gateway.requestConfirm(a, args, traceId, user);
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
        com.fssc.framework.security.LoginUser prev = com.fssc.framework.security.UserContext.get();
        com.fssc.framework.security.UserContext.set(owner);
        Object data;
        try {
            data = localReadInvoker.invoke(a, args);
        } finally {
            if (prev != null) {
                com.fssc.framework.security.UserContext.set(prev);
            } else {
                com.fssc.framework.security.UserContext.clear();
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

    private List<AiAction> visibleReadActions(LoginUser user) {
        return visibleActions(user, false);
    }

    /**
     * 候选人机共用动作面：目录内 aiAllowed ∧ 非 §6 红线类型 ∧ 有权限码 ∧ 本人实际持码 ∧ 租户端点。
     * withWrites=false 时只留只读低风险（问数）；放开写动作时仍不含 approve/void/config/delete，
     * 且写动作到网关侧必然还要过票据闸门——可见集宽窄不改变执行判据。
     */
    private List<AiAction> visibleActions(LoginUser user, boolean withWrites) {
        return visibleActions(user, withWrites, false, false);
    }

    /**
     * @param ignorePerm   true 时放开"本人持码"这一层 —— 只用于 D-N17 判定"目录里有、但本人无权"。
     * @param readAnyRisk  true 时放开"只读必须 LOW 档"这一层 —— 只用于 D-N17 判定
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
     * D-N17 分态之"权限/档位缺口"：把本人权限面（必要时连风险档）放开后再选一次，
     * 若此时存在匹配动作，说明拒绝的真实原因不是"目录里没有"——返回该动作，否则返回 null。
     * <p>两种命中：本人不持其权限码 ⇒ NO_PERM；本人持码但风险档非 LOW（问数通道只自动跑 LOW）⇒ CHANNEL_READ_ONLY。
     * 只在选型失败路径调用，正常问答路径零额外开销。
     */
    private AiAction permGapAction(String question, List<AiAction> visible, boolean withWrites, boolean readAnyRisk,
                                   ExtInterfaceConfig model, LoginUser user) {
        try {
            List<AiAction> wide = visibleActions(user, withWrites, true, readAnyRisk);
            if (wide.size() == visible.size() && !readAnyRisk) {
                return null; // 放开权限面后候选集未变大 ⇒ 不存在权限缺口（放开档位时另论）
            }
            Selection w = selectByLlm(question, wide, withWrites, model);
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
     * D-N17 分态之"红线缺口"：红线动作（aiAllowed=false）在建候选集时就被整族剔除，
     * 于是"目录里确实有、但被 §6 红线封禁"会被误报成 NO_MATCH（"目录里没有"与事实不符）。
     * 本探测单独在红线池里选一次，<b>只用于解释拒答，绝不执行</b>；
     * 命中即说明存在同义动作被封禁，由调用方连族名一起回给人看。
     * <p>池内只取 kind 不在 {@link AiGatewayService#forbiddenKind} 里的条目：类型级红线
     * （approve/void/config/delete）语义过于宽泛，放进来只会让"审批/删除"这类词胡乱命中；
     * 族级红线（无权限码、期间反关账、付款批执行、导出族等）才是最需要解释清楚的部分。
     */
    private AiAction redlineGapAction(String question, boolean withWrites, ExtInterfaceConfig model) {
        try {
            List<AiAction> pool = catalog.all().stream()
                    .filter(a -> !a.isAiAllowed() && !AiGatewayService.forbiddenKind(a.getKind()))
                    .filter(a -> withWrites || "read".equals(a.getKind()))
                    .filter(a -> a.getUrl() != null && a.getUrl().startsWith("/api/tenant/"))
                    .toList();
            if (pool.isEmpty()) {
                return null;
            }
            // 用红线专用人设：这条链只要"识别得出"，不要模型自己替系统做"该不该干"的判断（见 ROLE_REDLINE）
            Selection s = selectByLlm(question, pool, withWrites, model, ROLE_REDLINE);
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

    /**
     * 红线池的确定性兜底：中文口语 → 端点特征。
     * <p>为什么需要它：大模型在这条链上会偶发"多诉求句/口语化句认不出"——实测同一句
     * 「把今年所有单据的期间强制反关账并清空审计日志」在两次运行间一次命中红线、一次回 null，
     * 而"为什么不能做"是要如实回答用户的问题，不该靠模型运气。这几条把最常见的红线说法钉死，
     * 使红线拒答既有一致性、又不依赖外网通道。命中只用于解释，绝不执行（池内动作过不了网关）。
     * <p>谓词匹配"动词+宾语"的组合（而非单个名词），避免把"查一下审计日志"这类正常问句错认成红线。
     */
    private Selection selectByRedlineKeyword(String q, List<AiAction> pool) {
        record R(String qRegex, String urlRegex, String why) {
        }
        List<R> rules = List.of(
                new R("反关账|反结账|撤销关账|强制关账|期间重开|反过账",
                        "/(unclose|reopen)$", "期间反关账/重开"),
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

    // ---------------------------------------------------------------- LLM 面

    private record Selection(AiAction action, Map<String, Object> args, String engine, String reason) {
    }

    private static final String ROLE_ASK =
            "你是财务共享系统的只读问数助手。只能从给定动作清单中选一个最匹配的动作来回答用户问题；"
                    + "没有匹配项就返回 {\"action\":null,\"reason\":\"...\"}。";

    private static final String ROLE_INSTRUCT =
            "你是财务共享系统的指令执行助手。只能从给定动作清单中选一个最匹配的动作来完成用户指令，并按该端点语义给出 args；"
                    + "写动作会先经人工确认票据才真正执行，你无权跳过；清单里没有合适的动作就返回 {\"action\":null,\"reason\":\"...\"}。";

    /**
     * D-N17 红线探测专用人设。清单里给的全是**已被永久禁止调用**的动作，本环节只做"识别"不做"执行"。
     * 沿用常规指令人设会坏在"模型自己也认定这事不该干 ⇒ 回 action=null"，把"识别得出"变成"识别不出"
     * （实测：「把今年所有单据的期间强制反关账并清空审计日志」在常规人设下被判为"清单中不存在"，
     * 而 /gl/month/unclose 就在池里）。
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
    private Selection selectByLlm(String question, List<AiAction> visible, boolean withWrites, ExtInterfaceConfig cfg) {
        return selectByLlm(question, visible, withWrites, cfg, withWrites ? ROLE_INSTRUCT : ROLE_ASK);
    }

    /** @param role 选型人设：问数 {@link #ROLE_ASK}／指令 {@link #ROLE_INSTRUCT}／红线识别 {@link #ROLE_REDLINE}。 */
    private Selection selectByLlm(String question, List<AiAction> visible, boolean withWrites, ExtInterfaceConfig cfg,
                                  String role) {
        if (cfg == null || !"ENABLED".equals(cfg.getStatus())) {
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
                log.warn("AI 问数：大模型通道失败 status={} err={}", resp.getStatusCode(), resp.getError());
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
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
            return null;
        }
    }

    private String extractJson(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : "{}";
    }

    // ------------------------------------------------------ 第二次调用：照结构填参

    private record Fill(Map<String, Object> args, String missing) {
    }

    /**
     * chat/completions 请求体（OpenAI 兼容形）。{@code enable_thinking=false} 是实测结论：
     * 选型与填参都是机械活，同一条 905 token 的填参提示词开思考链 16.4 s、关掉 1.2 s，
     * 而 16.4 s 会把调用拖过模型通道超时、把整条填参链吃掉（D-N12 回归首轮就是这样失败的）。
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
     * D-N15：目录声明了请求体或必填参数，而模型一个值都没填上 —— 这种票不该出。
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

    /** 把目录 v2 的参数结构交给模型，只让它填值、不让它猜字段名；失败即回 null 由调用方沿用原 args。 */
    private Fill fillArgs(AiAction a, String question, ExtInterfaceConfig cfg) {
        try {
            String systemPrompt = "你是财务共享系统的接口填参器。根据用户指令为指定端点产出调用参数，只输出一个 JSON 对象，不要解释、不要代码块围栏。\n"
                    + "规则：\n"
                    + "1) 只能用下面「参数结构」里出现的键，层级照抄；结构里的类型名与 /*注释*/ 只是说明，不是取值。\n"
                    + "2) 用户说了的才填；用户没说的可选字段一律省略，不要凑字段。\n"
                    + "3) 主数据引用（各类 ID／编码／用户／公司／部门／费用项／科目／供应商）用户没给就绝不编造，宁可留空——"
                    + "缺口会写进确认票据给真人看。\n"
                    + "4) 金额用数字，日期用 yyyy-MM-dd，今天是 " + java.time.LocalDate.now() + "。\n"
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

    // ------------------------------------------------------------ 确定性兜底

    /** §7 纵切问题等的本地匹配（无大模型也可跑通；动作仍全部来自目录、仍过网关）。 */
    private Selection selectByKeyword(String q, List<AiAction> visible) {
        record Rule(String[] keywords, String urlRegex, String reason) {
        }
        List<Rule> rules = List.of(
                new Rule(new String[]{"未派工", "派工", "对账任务"}, "/sharing/task/pool|recon/page", "共享任务池=未派工对账任务"),
                new Rule(new String[]{"待办"}, "/wf/todo", "我的待办"),
                new Rule(new String[]{"单据", "查单"}, "/expense/bill/page|/sharing/bill/my-page", "我的单据列表"),
                new Rule(new String[]{"凭证"}, "/gl/voucher/page|/voucher/page", "凭证列表"));
        for (Rule rule : rules) {
            for (String k : rule.keywords()) {
                if (!q.contains(k)) {
                    continue;
                }
                AiAction a = visible.stream().filter(x -> x.getUrl().matches(".*" + rule.urlRegex() + ".*"))
                        .findFirst().orElse(null);
                if (a != null) {
                    Map<String, Object> args = a.getUrl().contains("/page") ? Map.of("pageNum", 1, "pageSize", 50) : Map.of();
                    return new Selection(a, args, "local-rule", rule.reason());
                }
            }
        }
        return null;
    }

    /** data 摘要：列表只回计数＋前 5 行，避免把整页数据塞进对话气泡。入参为 {@link com.fssc.ai.AiGatewayService.GatewayResult#data()} 的普通 Java 值（D-N09）。 */
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
         * D-N17 拒答原因（也是"AI 清楚自己不能做什么"的机器可判据）：
         * NO_MATCH＝目录内确无匹配动作；NO_PERM＝动作存在但本人不持其权限码；
         * REDLINE_BLOCKED＝动作在目录里存在，但被标为 AI 不可调用（§6 红线族／无权限码最严口径），永久不可执行；
         * CHANNEL_READ_ONLY＝命中的只读动作风险档非 LOW，问数通道只自动跑 read∧LOW（不执行写类）；
         * PARAM_MISSING＝必填参数一个没填上故不出票；EXEC_DENIED＝执行被服务端拒绝。非拒答时为 null。
         */
        private String refusalReason;
        private Object data;
    }

    /** 指令通道结果：在问数结果之上多"动作类型/风险档/参数/票据"四件，前端据此渲染确认卡与轮询。 */
    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class InstructResult extends AskResult {
        private String kind;
        private String riskLevel;
        private Map<String, Object> args;
        private boolean needsConfirm;
        private Long confirmId;
        private String paramsDigest;
        private String expireAt;
        /** 填参阶段模型自报的缺口（主数据 ID 之类它不该编的东西），供确认人看。 */
        private String missing;
    }
}
