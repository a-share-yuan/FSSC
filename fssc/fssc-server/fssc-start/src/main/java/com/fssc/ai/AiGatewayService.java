package com.fssc.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fssc.ai.entity.AiActionConfirm;
import com.fssc.ai.entity.AiToolCallLog;
import com.fssc.ai.mapper.AiActionConfirmMapper;
import com.fssc.ai.mapper.AiToolCallLogMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * AI 入站网关内核（AI 平权 P2，方案 §4-②）。
 *
 * <p>职责边界：只做"目录校验 / 红线分级 / 真人身份透传 / 双日志"，<b>不含任何业务逻辑</b>。
 * 实际执行是把请求原样回环转发到与人相同的那条 {@code /api/tenant/**} 链路 ——
 * 认证、RBAC（@RequiresPerm）、套餐开关（@RequiresFeature）、数据范围、状态机、
 * 单据守卫、@OperLog 审计全部照常生效，AI 得到的权限恰是"下指令那个人"的权限，不多不少。
 *
 * <p>判据（§5-P2）：未登记动作＝403；任一调用可按 traceId 回放"指令→动作→结果→操作人"；
 * 关闭网关开关不影响任何既有页面功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    /** §6 红线：这些动作类型永不经网关执行（审批决策属人，写规则属配置面）。 */
    private static final Set<String> NEVER_KINDS = Set.of("approve", "void", "config", "delete");

    /** 参数文本快照上限（D-N14）：确认队列页要回显，不宜让单条票据撑爆响应体。 */
    private static final int ARGS_TEXT_MAX = 2000;

    /**
     * 动作发现的一次性返回上限（D-N34）：只是防"无界响应"的最后一道闸，<b>不是过滤手段</b>。
     * <p>原实现把 {@code .limit(500)} 放在 aiOnly 过滤<b>之前</b>（过滤在 Controller 里），目录到 1222 条后
     * "只看 AI 可调用动作"永远只回前 500 条里的可调用子集（读数恒 407），与真实可调用数不符。
     * 现把 aiOnly 下推到本方法内、上限放宽到远大于目录规模，读数＝真实条数。
     */
    private static final int CATALOG_LIMIT = 5000;

    /** §6 红线动作类型（供问数/指令编排在建候选集时就排除，与 {@link #gatekeeping} 同一把尺子）。 */
    public static boolean forbiddenKind(String kind) {
        return NEVER_KINDS.contains(kind);
    }

    private final AiActionCatalog catalog;
    private final ObjectMapper objectMapper;
    private final AiToolCallLogMapper toolCallLogMapper;
    private final AiActionConfirmMapper confirmMapper;

    @Value("${fssc.ai.gateway.enabled:false}")
    private boolean enabled;

    /** 回环转发基址：默认跟随本机监听端口；容器内可通过环境变量指向服务自身。 */
    @Value("${fssc.ai.gateway.base-url:http://127.0.0.1:${server.port:8089}}")
    private String baseUrl;

    @Value("${fssc.ai.gateway.confirm-ttl-minutes:10}")
    private int confirmTtlMinutes;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(120_000);
        return new RestTemplate(f);
    }

    // ------------------------------------------------------------------ 执行

    /**
     * 网关执行主流程。任何一步不过即拒（BizException→对应 HTTP 码），且每一步都落 ai_tool_call_log。
     */
    public GatewayResult execute(GatewayRequest req, HttpServletRequest http, LoginUser user, String traceId) {
        long start = System.currentTimeMillis();
        AiToolCallLog entry = newEntry(http, user, traceId, req);
        AiActionConfirm ticket = null;

        try {
            if (!enabled) {
                throw new BizException(ErrorCode.FORBIDDEN, "AI 网关未开启（fssc.ai.gateway.enabled=false）");
            }
            // 身份铁律：只接受真人租户 token；平台账号不下业务单据，服务账号在本系统不存在（JWT 即登录会话）
            if (user == null || !user.isTenant()) {
                throw new BizException(ErrorCode.FORBIDDEN, "AI 只能以下令真人的租户身份执行");
            }
            AiAction action = resolve(req);
            entry.setActionCode(action.getCode());
            entry.setActionUrl(action.getUrl());
            entry.setActionMethod(action.getMethod());
            entry.setActionKind(action.getKind());
            entry.setRiskLevel(action.getRiskLevel());

            ticket = gatekeeping(action, req, user, http);

            Loopback resp = executeLoopback(action, req, http, user);
            entry.setHttpStatus(resp.status());
            JsonNode biz = readTree(resp.body());
            entry.setBizCode(biz.path("code").asInt(-1));
            entry.setBizResult(bizResultOf(resp));
            boolean bizOk = entry.getBizCode() == 0;
            entry.setStatus(bizOk ? 1 : 0);
            if (!bizOk) {
                entry.setErrorMsg(truncate(biz.path("msg").asText(""), 2000));
            }
            // 票据消耗语义（人工裁决：业务 5xx 不消耗票据）：票据已在执行前原子置 USED；
            // 若上游返回 5xx（服务端异常、请求未获业务裁决）则退回 CONFIRMED，不烧掉一次真人背书。
            if (ticket != null && resp.status() >= 500) {
                String note = releaseTicket(ticket);
                entry.setErrorMsg(truncate((entry.getErrorMsg() == null ? "" : entry.getErrorMsg() + " ｜ ") + note, 2000));
            }
            return new GatewayResult(traceId, action.getCode(), entry.getBizCode(),
                    entry.getErrorMsg(), plain(biz.path("data")), resp.body());
        } catch (BizException ex) {
            releaseTicketQuietly(ticket);
            entry.setStatus(0);
            entry.setErrorMsg(truncate(ex.getMessage(), 2000));
            throw ex;
        } catch (RuntimeException ex) {
            releaseTicketQuietly(ticket);
            entry.setHttpStatus(entry.getHttpStatus() == null ? -1 : entry.getHttpStatus());
            entry.setStatus(0);
            entry.setErrorMsg(truncate(ex.getMessage(), 2000));
            throw ex;
        } finally {
            entry.setCostMs(System.currentTimeMillis() - start);
            writeLog(entry);
        }
    }

    // ------------------------------------------------------------ 目录与闸门

    private AiAction resolve(GatewayRequest req) {
        AiAction a = null;
        if (req.action() != null && !req.action().isBlank()) {
            a = catalog.byCode(req.action().trim());
            if (a == null) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "动作未登记：" + req.action() + "。AI 只能调用动作目录里登记的端点（未登记＝不可调用）。");
            }
        }
        if (a == null && req.verb() != null && req.path() != null) {
            a = catalog.match(req.verb(), req.path());
            if (a == null) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "动作未登记：" + req.verb().toUpperCase() + " " + req.path()
                                + " 不在动作目录中（未登记＝不可调用）。");
            }
        }
        if (a == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "action 与 verb+path 至少给一种");
        }
        return a;
    }

    private AiActionConfirm gatekeeping(AiAction a, GatewayRequest req, LoginUser user, HttpServletRequest http) {
        if (!a.isAiAllowed()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "动作 " + a.getCode() + " 标记为 AI 不可调用（§6 红线：删除/审批/反结账/权限变更/发布流程等永不开放）。");
        }
        if (NEVER_KINDS.contains(a.getKind())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "动作类型 " + a.getKind() + " 永不经 AI 网关执行（决策属人，配置属裁决）。");
        }
        // 权限预检只是快速失败的人话版本；真判仍然由下游 @RequiresPerm 按同一 token 执行
        if (a.getPermCode() == null || a.getPermCode().isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "动作 " + a.getCode() + " 目录中无权限码，网关按最严口径拒绝（请先补目录或补 @RequiresPerm）。");
        }
        if (!user.isAdmin() && !hasPerm(user, a.getPermCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少权限：" + a.getPermCode() + "（继承自下令用户 " + user.getUsername() + "）");
        }
        // 幂等约定：写类动作必须带 X-Idempotency-Key（或由 body 内 clientReqNo 承担），重放不产生第二张单
        if (!"read".equals(a.getKind())) {
            String idem = http.getHeader("X-Idempotency-Key");
            if ((idem == null || idem.isBlank()) && !hasClientReqNo(req.args())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "写类动作必须携带 X-Idempotency-Key 头（或参数含 clientReqNo）：" + a.getIdempotency());
            }
        }
        // 风险分级：LOW 直跑；其余必须持有效确认票据（P4）——票据在此处原子消耗，5xx 时由调用方退回
        if (!"LOW".equalsIgnoreCase(a.getRiskLevel())) {
            return useConfirmTicket(a, req, user);
        }
        return null;
    }

    private boolean hasPerm(LoginUser user, String perm) {
        Set<String> perms = user.getPerms();
        return perms != null && (perms.contains(perm) || perms.contains("*:*:*"));
    }

    private boolean hasClientReqNo(Map<String, Object> args) {
        return args != null && args.get("clientReqNo") != null;
    }

    /** 票据消耗：原子 PENDING/CONFIRMED→USED，参数摘要必须一致，过期即废。 */
    private AiActionConfirm useConfirmTicket(AiAction a, GatewayRequest req, LoginUser user) {
        Long ticketId = req.confirmId();
        if (ticketId == null) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "动作 " + a.getCode() + " 风险档=" + a.getRiskLevel() + "，需先 POST /api/ai/gateway/confirm-request 申请人工确认票据");
        }
        AiActionConfirm c = confirmMapper.selectById(ticketId);
        if (c == null || !c.getActionCode().equals(a.getCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "确认票据不存在或动作不符");
        }
        if (!"CONFIRMED".equals(c.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "确认票据状态=" + c.getStatus() + "，不可使用（一次性）");
        }
        if (c.getExpireAt() != null && c.getExpireAt().isBefore(LocalDateTime.now())) {
            c.setStatus("EXPIRED");
            confirmMapper.updateById(c);
            throw new BizException(ErrorCode.FORBIDDEN, "确认票据已过期（TTL=" + confirmTtlMinutes + " 分钟），请重新申请");
        }
        String digest = digest(req.args());
        if (!digest.equals(c.getParamsDigest())) {
            throw new BizException(ErrorCode.FORBIDDEN, "参数与确认时不一致（摘要不符即作废），请重新申请确认");
        }
        // 票据是"申请人下令、其他真人背书"的授权凭证：执行者必须是申请人本人（AI 以其身份跑），
        // 且同租户——防他人凭 confirmId 消费别人的已确认票。自审禁止在 confirmTicket 确认环节把住，此处不再判申请人。
        if (!user.getUserId().equals(c.getApplicantUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "票据只能由申请人本人的会话执行（他人票据不可代用）");
        }
        if (c.getTenantId() != null && !c.getTenantId().equals(user.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "票据租户与当前会话不符");
        }
        c.setStatus("USED");
        c.setUpdateTime(LocalDateTime.now());
        confirmMapper.updateById(c);
        return c;
    }

    /**
     * 上游 5xx / 未交付时把票据退回 CONFIRMED（人工裁决：「业务 5xx 不消耗票据」）。
     * 只在票据确实处于 USED 时回退，避免误改他人状态；若退回时已过 TTL 则置 EXPIRED。
     */
    private String releaseTicket(AiActionConfirm used) {
        try {
            AiActionConfirm fresh = confirmMapper.selectById(used.getId());
            if (fresh == null || !"USED".equals(fresh.getStatus())) {
                return "票据 " + used.getId() + " 未回退（当前状态=" + (fresh == null ? "不存在" : fresh.getStatus()) + "）";
            }
            if (fresh.getExpireAt() != null && fresh.getExpireAt().isBefore(LocalDateTime.now())) {
                fresh.setStatus("EXPIRED");
                fresh.setUpdateTime(LocalDateTime.now());
                confirmMapper.updateById(fresh);
                return "票据 " + used.getId() + " 因服务端异常未交付，且已过 TTL，置 EXPIRED";
            }
            fresh.setStatus("CONFIRMED");
            fresh.setUpdateTime(LocalDateTime.now());
            confirmMapper.updateById(fresh);
            return "服务端异常未交付业务裁决，票据 " + used.getId() + " 已回退 CONFIRMED（TTL 内可用同一票据重试）";
        } catch (Exception ex) {
            log.error("票据回退失败(id={})", used.getId(), ex);
            return "票据 " + used.getId() + " 回退失败，请人工核对";
        }
    }

    /** 异常退出路径的静默回退（不打日志表，只保证不白烧票据）。 */
    private void releaseTicketQuietly(AiActionConfirm used) {
        if (used != null) {
            releaseTicket(used);
        }
    }

    // ------------------------------------------------------------------ 票据面

    /** 高风险写：先出"待确认"票据，由真人在待确认队列里点确认，AI 只能出示票据号。 */
    public AiActionConfirm requestConfirm(AiAction a, Map<String, Object> args, String instructionId, LoginUser user) {
        if (!enabled) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI 网关未开启");
        }
        if (NEVER_KINDS.contains(a.getKind()) || !a.isAiAllowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该动作属 §6 红线，永不可执行，票据亦不可申请");
        }
        AiActionConfirm c = new AiActionConfirm();
        c.setTenantId(user.getTenantId());
        c.setInstructionId(instructionId);
        c.setActionCode(a.getCode());
        c.setActionUrl(a.getUrl());
        c.setActionMethod(a.getMethod());
        c.setParamsDigest(digest(args));
        c.setArgsText(argsText(args));
        c.setApplicantUserId(user.getUserId());
        c.setStatus("PENDING");
        c.setExpireAt(LocalDateTime.now().plusMinutes(confirmTtlMinutes));
        c.setCreateTime(LocalDateTime.now());
        confirmMapper.insert(c);
        return c;
    }

    public AiActionConfirm confirmTicket(Long id, boolean approve, String reason, LoginUser user) {
        AiActionConfirm c = confirmMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(user.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据不存在");
        }
        if (!"PENDING".equals(c.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "票据状态=" + c.getStatus() + "，只有 PENDING 可确认");
        }
        // 过期即废在执行侧一直成立；确认侧同样不能放行一张注定用不了的票（否则会留下过期仍 CONFIRMED 的死票）
        if (c.getExpireAt() != null && c.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "票据已于 " + c.getExpireAt() + " 过期（TTL=" + confirmTtlMinutes + " 分钟），背书无意义，请让申请人重新出票");
        }
        if (user.getUserId().equals(c.getApplicantUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "自审禁止：确认人不能是票据申请人");
        }
        c.setStatus(approve ? "CONFIRMED" : "REJECTED");
        c.setConfirmerUserId(user.getUserId());
        c.setConfirmerUsername(user.getUsername());
        c.setReason(reason);
        c.setConfirmTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        confirmMapper.updateById(c);
        return c;
    }

    public AiAction actionByCode(String code) {
        return code == null ? null : catalog.byCode(code);
    }

    public List<AiAction> catalog(String kindFilter, String keyword, boolean aiOnly) {
        return catalog.all().stream()
                .filter(a -> !aiOnly || a.isAiAllowed())
                .filter(a -> kindFilter == null || kindFilter.isBlank() || kindFilter.equalsIgnoreCase(a.getKind()))
                .filter(a -> keyword == null || keyword.isBlank()
                        || String.valueOf(a.getCode()).contains(keyword) || String.valueOf(a.getUrl()).contains(keyword))
                .limit(CATALOG_LIMIT)
                .toList();
    }

    // ------------------------------------------------------------ 回环执行面

    private Loopback executeLoopback(AiAction a, GatewayRequest req, HttpServletRequest http, LoginUser user) {
        String path = a.getUrl();
        Map<String, Object> args = req.args() == null ? new TreeMap<>() : new TreeMap<>(req.args());
        // 路径参数：args 里存在同名键则填充；其余键 GET/DELETE 走查询串、其它动词走 JSON body
        for (Map.Entry<String, Object> e : new TreeMap<>(args).entrySet()) {
            String ph = "{" + e.getKey() + "}";
            if (path.contains(ph)) {
                path = path.replace(ph, String.valueOf(e.getValue()));
                args.remove(e.getKey());
            }
        }
        boolean queryStyle = "GET".equalsIgnoreCase(a.getMethod()) || "DELETE".equalsIgnoreCase(a.getMethod());
        StringBuilder qs = new StringBuilder();
        if (queryStyle) {
            String upstream = http.getQueryString();
            if (upstream != null && !upstream.isBlank()) {
                qs.append(upstream);
            }
            for (Map.Entry<String, Object> e : args.entrySet()) {
                if (upstream != null && upstream.contains(e.getKey() + "=")) {
                    continue;
                }
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(e.getKey()).append('=')
                        .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        String url = baseUrl + path + (qs.length() > 0 ? "?" + qs : "");
        HttpHeaders headers = new HttpHeaders();
        String auth = http.getHeader("Authorization");
        if (auth != null) {
            headers.set("Authorization", auth);
        }
        headers.set(TraceIdFilter.HEADER, currentTraceId());
        String idem = http.getHeader("X-Idempotency-Key");
        if (idem != null && !idem.isBlank()) {
            headers.set("X-Idempotency-Key", idem);
        }
        String company = http.getHeader("X-Company-Id");
        if (company != null && !company.isBlank()) {
            headers.set("X-Company-Id", company);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity;
        if (queryStyle) {
            entity = new HttpEntity<>(headers);
        } else {
            try {
                entity = new HttpEntity<>(objectMapper.writeValueAsString(args), headers);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "参数序列化失败: " + ex.getMessage());
            }
        }
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url,
                    HttpMethod.valueOf(a.getMethod().toUpperCase()), entity, String.class);
            return new Loopback(resp.getStatusCode().value(), resp.getBody(), contentTypeOf(resp.getHeaders()));
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            // 下游业务拒绝也要如实回传并留痕（403/400/500 都是"后端拒绝"这一判据的证据）
            return new Loopback(ex.getStatusCode().value(), ex.getResponseBodyAsString(), contentTypeOf(ex.getResponseHeaders()));
        }
    }

    private String contentTypeOf(HttpHeaders headers) {
        org.springframework.http.MediaType mt = headers == null ? null : headers.getContentType();
        return mt == null ? null : mt.toString();
    }

    // ------------------------------------------------------------------ 工具面

    /** 参数规范化摘要：TreeMap 深排序后序列化，字段顺序不影响摘要（改值必改摘要）。 */
    public String digest(Map<String, Object> args) {
        try {
            String canon = objectMapper.writeValueAsString(canonical(args));
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("digest 失败", ex);
        }
    }

    /**
     * 参数文本快照（D-N14，选项乙）：给人读的明文参数，口令/令牌类字段落盘前打码。
     * 与 {@link #digest} 分工明确——digest 防篡改（执行时复核），本方法只负责让确认人
     * "看得见自己背书的是什么"，不再是面对一串哈希闭眼签字。
     */
    private String argsText(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        Map<String, Object> masked = new TreeMap<>();
        args.forEach((k, v) -> masked.put(String.valueOf(k), isSecretKey(k) ? "***" : v));
        try {
            return truncate(objectMapper.writeValueAsString(masked), ARGS_TEXT_MAX);
        } catch (Exception ex) {
            return truncate(String.valueOf(masked), ARGS_TEXT_MAX);
        }
    }

    /** 口令/令牌类键名一律打码：票据是给另一位真人看的，不该顺带把凭据抄一份进队列页。 */
    private static boolean isSecretKey(String k) {
        if (k == null) {
            return false;
        }
        String s = k.toLowerCase();
        return s.contains("password") || s.contains("passwd") || s.contains("pwd")
                || s.contains("secret") || s.contains("token") || s.contains("credential");
    }

    private Object canonical(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new TreeMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), canonical(val)));
            return out;
        }
        if (v instanceof List<?> l) {
            return l.stream().map(this::canonical).toList();
        }
        return v;
    }

    /**
     * D-N21：审计只留"可判读的摘要"——非 JSON 响应（ZIP/CSV/字节流）一律不落原文，
     * 改写为 contentType＋字节数＋哈希前缀，杜绝把导出文件明文副本灌进 ai_tool_call_log。
     */
    private String bizResultOf(Loopback resp) {
        String ct = resp.contentType() == null ? "" : resp.contentType().toLowerCase();
        boolean jsonLike = ct.isBlank() || ct.contains("json");
        if (jsonLike && looksJson(resp.body())) {
            return truncate(resp.body(), 2000);
        }
        int bytes = resp.body() == null ? 0 : resp.body().length();
        return truncate("[D-N21：非 JSON 响应不落原文] contentType=" + (ct.isBlank() ? "unknown" : ct)
                + " bytes=" + bytes + " sha256_16=" + sha256(resp.body()), 2000);
    }

    private boolean looksJson(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        char c = body.trim().charAt(0);
        return c == '{' || c == '[';
    }

    private String sha256(String s) {
        try {
            String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 16);
        } catch (Exception ex) {
            return "n/a";
        }
    }

    private String currentTraceId() {
        String t = org.slf4j.MDC.get(TraceIdFilter.MDC_KEY);
        return t;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("code", -1).put("msg", "服务返回内容无法解析");
        }
    }

    /** JsonNode → 普通 Java 值（D-N09）：标量取原生类型、容器转 Map/List，缺失/空回 null，避免把 JsonNode 当 POJO 序列化。 */
    private Object plain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private AiToolCallLog newEntry(HttpServletRequest http, LoginUser user, String traceId, GatewayRequest req) {
        AiToolCallLog e = new AiToolCallLog();
        e.setTraceId(traceId);
        e.setInstructionText(truncate(req.instruction(), 2000));
        e.setModel(req.model());
        e.setClientIp(http.getRemoteAddr());
        e.setCallTime(LocalDateTime.now());
        e.setConfirmId(req.confirmId());
        String idem = http.getHeader("X-Idempotency-Key");
        e.setIdempotencyKey(idem);
        if (user != null) {
            e.setTenantId(user.getTenantId());
            e.setOperatorUserId(user.getUserId());
            e.setOperatorUsername(user.getUsername());
        }
        try {
            e.setArgs(truncate(sanitize(objectMapper.writeValueAsString(req.args())), 2000));
        } catch (Exception ignored) {
            // 参数打不了快照不影响主流程
        }
        return e;
    }

    /** 与 OperLogAspect 同口径的敏感字段脱敏，避免 token/password 进 AI 日志。 */
    private String sanitize(String json) {
        return json == null ? null : json.replaceAll(
                "(\"(?:password|token|oldPassword|secret|pwd)\"\\s*:\\s*\")[^\"]*(\")", "$1******$2");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 日志失败不影响业务结果（与 OperLogWriter 同语义）；同步写，表轻量且审计不可丢。 */
    private void writeLog(AiToolCallLog entry) {
        try {
            toolCallLogMapper.insert(entry);
        } catch (Exception ex) {
            log.error("AI 调用日志写入失败(traceId={})", entry.getTraceId(), ex);
        }
    }

    // ------------------------------------------------------------------ 类型

    public record GatewayRequest(String instruction, String model, String action, String verb, String path,
                                 Map<String, Object> args, Long confirmId) {
    }

    private record Loopback(int status, String body, String contentType) {
    }

    public record GatewayResult(String traceId, String actionCode, Integer bizCode, String errorMsg,
                                Object data, String rawJson) {
    }
}
