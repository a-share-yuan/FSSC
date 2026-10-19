package com.fssc.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.ai.entity.AiActionConfirm;
import com.fssc.ai.entity.AiInspectRule;
import com.fssc.ai.mapper.AiActionConfirmMapper;
import com.fssc.ai.mapper.AiInspectRuleMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 常驻巡检规则（AI 平权 P5"可组织"）：自然语言→规则落库，job 按频率复跑，
 * 命中即产生"待确认队列"条目——低风险自动读、高风险写进队列等人确认（§5-P5 判据）。
 *
 * <p>刻意复用的既有件：
 * <ul>
 *   <li>读动作走 {@link AiAskService} 同一目录口径（只 read∧aiAllowed∧LOW 可做巡检源）；</li>
 *   <li>待确认条目直接落 {@code ai_action_confirm}（人侧队列 UI/确认接口零新增）；</li>
 *   <li>调度线程无租户上下文 ⇒ {@code TenantContext.callAs} 逐租户回灌（WfTimeoutJob 同款纪律，
 *       2026-09-22 现网教训：漏了会整批 fail-closed 抛错）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInspectService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(today|month|year)\\}\\}");

    private final AiInspectRuleMapper ruleMapper;
    private final AiActionConfirmMapper confirmMapper;
    private final com.fssc.ai.mapper.AiToolCallLogMapper toolCallLogMapper;
    private final AiAskService askService;
    private final AiActionCatalog catalog;
    private final com.fssc.system.mapper.SysUserMapper userMapper;
    private final com.fssc.connector.api.ConnectorFacade connector;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------- 规则管理面

    /** 建规则（真人操作：页面按钮或 AI 助手"帮我把这条巡检固化下来"）。动作必须是目录内只读 LOW 动作。 */
    public AiInspectRule create(String instructionText, AiInspectRule form, LoginUser user) {
        AiAction a = catalog.byCode(form.getActionCode());
        if (a == null || !a.isAiAllowed() || !"read".equals(a.getKind()) || !"LOW".equalsIgnoreCase(a.getRiskLevel())) {
            throw new BizException(ErrorCode.FORBIDDEN, "巡检源必须是目录内只读低风险动作：" + form.getActionCode());
        }
        form.setId(null);
        form.setTenantId(user.getTenantId());
        form.setEnabled(form.getEnabled() == null ? 1 : form.getEnabled());
        form.setIntervalMinutes(form.getIntervalMinutes() == null ? 60 : Math.max(5, form.getIntervalMinutes()));
        form.setOnBreach("QUEUE"); // 本阶段唯一处置；AUTO_EXECUTE 需另立裁决
        form.setCreatedBy(user.getUsername());
        form.setInstructionText(truncate(instructionText, 2000));
        form.setCreateTime(LocalDateTime.now());
        form.setUpdateTime(LocalDateTime.now());
        ruleMapper.insert(form);
        return form;
    }

    /**
     * 自然语言→规则草稿（P5"可组织"的表达载体）。有大模型通道时由模型从本人可见只读动作里挑选；
     * 无通道/解析失败⇒返回 null，前端引导人工填表 —— 绝不瞎编一条规则。
     */
    public AiInspectRule suggest(String instruction, LoginUser user) {
        try {
            var cfg = connector.getConfig("AI_MODEL");
            if (cfg == null || !"ENABLED".equals(cfg.getStatus())) {
                return null;
            }
            List<Map<String, Object>> visible = askService.discover(user);
            if (visible.isEmpty()) {
                return null;
            }
            String actionList;
            try {
                actionList = objectMapper.writeValueAsString(visible);
            } catch (Exception ex) {
                return null;
            }
            String sys = "把用户的巡检诉求翻译成一条巡检规则，只允许输出一个 JSON 对象（无其他文字）："
                    + "{\"ruleName\":..,\"actionCode\":只能取动作清单里的 code,\"argsTemplate\":{..},"
                    + "\"jsonPath\":\"如 $.total 或 $.list\",\"op\":\"LT|LTE|GT|GTE|EQ|EMPTY|NOT_EMPTY\","
                    + "\"expected\":\"数字或空\",\"intervalMinutes\":数字}。清单里没有能回答该诉求的动作时输出 {\"none\":true}。"
                    + "\n动作清单=" + actionList;
            var resp = connector.invokeRead("AI_MODEL", java.util.Map.of(
                    "model", cfg.getModelName(),
                    "messages", java.util.List.of(
                            java.util.Map.of("role", "system", "content", sys),
                            java.util.Map.of("role", "user", "content", instruction)),
                    "temperature", 0));
            if (!resp.isOk()) {
                return null;
            }
            String content = objectMapper.readTree(resp.getBody())
                    .path("choices").path(0).path("message").path("content").asText("");
            int a = content.indexOf('{'), b = content.lastIndexOf('}');
            if (a < 0 || b <= a) {
                return null;
            }
            var j = objectMapper.readTree(content.substring(a, b + 1));
            if (j.path("none").asBoolean(false) || j.path("actionCode").isMissingNode()) {
                return null;
            }
            AiInspectRule r = new AiInspectRule();
            r.setRuleName(j.path("ruleName").asText(instruction));
            r.setActionCode(j.path("actionCode").asText());
            r.setArgsTemplate(j.path("argsTemplate").toString());
            r.setJsonPath(j.path("jsonPath").asText("$.total"));
            r.setOp(j.path("op").asText("GT"));
            r.setExpected(j.path("expected").asText("0"));
            r.setIntervalMinutes(j.path("intervalMinutes").asInt(60));
            return r;
        } catch (Exception ex) {
            log.warn("巡检规则自然语言转换失败: {}", ex.getMessage());
            return null;
        }
    }

    /** 停用即止（可审计：行不删，enabled=0，last_result 保留现场）。 */
    public void toggle(Long id, boolean on, LoginUser user) {
        AiInspectRule r = own(id, user);
        r.setEnabled(on ? 1 : 0);
        r.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(r);
    }

    public List<AiInspectRule> list(LoginUser user) {
        return ruleMapper.selectList(new LambdaQueryWrapper<AiInspectRule>()
                .eq(AiInspectRule::getTenantId, user.getTenantId())
                .orderByDesc(AiInspectRule::getId));
    }

    // ---------------------------------------------------------------- 执行面

    /** 每 5 分钟扫一轮到期的规则（心跳包装由 JobHeartbeatService 统一记账，这里只包最简 try-catch）。 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void runDueRules() {
        try {
            TenantContext.runIgnoreTenant(() -> {
                List<AiInspectRule> due = ruleMapper.selectList(new LambdaQueryWrapper<AiInspectRule>()
                        .eq(AiInspectRule::getEnabled, 1)
                        .and(w -> w.isNull(AiInspectRule::getLastRunAt)
                                .or().le(AiInspectRule::getLastRunAt, LocalDateTime.now().minusMinutes(5))));
                for (AiInspectRule r : due) {
                    TenantContext.callAs(r.getTenantId(), () -> evaluate(r));
                }
            });
        } catch (Exception ex) {
            log.error("AI 巡检轮次异常", ex);
        }
    }

    private void evaluate(AiInspectRule r) {
        // 到期判断：interval 精确控制（fixedDelay 只是心跳粒度）
        if (r.getLastRunAt() != null && r.getLastRunAt().isAfter(LocalDateTime.now().minusMinutes(r.getIntervalMinutes()))) {
            return;
        }
        r.setLastRunAt(LocalDateTime.now());
        long t0 = System.currentTimeMillis();
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LoginUser owner = null;
        try {
            // 以"规则创建人"的权限视角执行：创建人已离职/失权 ⇒ 规则自然失效（fail-closed）
            owner = ownerIdentity(r);
            JsonNode data = askService.runReadActionForInspect(owner, r.getActionCode(), fill(r.getArgsTemplate()));
            String actual = path(data, r.getJsonPath());
            boolean breach = breached(r.getOp(), actual, r.getExpected());
            r.setLastResult(breach ? "BREACHED" : "OK");
            r.setLastDetail(truncate("actual=" + actual, 500));
            if (breach) {
                queueForConfirm(r, actual);
            }
            logToolCall(r, owner, traceId, t0, 1, null, r.getLastDetail());
        } catch (Exception ex) {
            r.setLastResult("ERROR");
            r.setLastDetail(truncate(ex.getMessage(), 500));
            logToolCall(r, owner, traceId, t0, 0, ex.getMessage(), null);
            log.warn("AI 巡检规则 {} 执行失败: {}", r.getId(), ex.getMessage());
        }
        r.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(r);
    }

    /** 双日志原则：AI 自发的每次读取也要在 ai_tool_call_log 留痕（审计不看调用方是人还是 job）。 */
    private void logToolCall(AiInspectRule r, LoginUser owner, String traceId, long t0, int status, String err, String detail) {
        try {
            AiAction a = catalog.byCode(r.getActionCode());
            com.fssc.ai.entity.AiToolCallLog l = new com.fssc.ai.entity.AiToolCallLog();
            l.setTenantId(r.getTenantId());
            l.setTraceId(traceId);
            l.setInstructionText("inspect://" + r.getRuleName() + (r.getInstructionText() == null ? "" : " | " + r.getInstructionText()));
            l.setModel("inspect-job");
            l.setActionCode(r.getActionCode());
            if (a != null) {
                l.setActionUrl(a.getUrl());
                l.setActionMethod(a.getMethod());
                l.setActionKind(a.getKind());
                l.setRiskLevel(a.getRiskLevel());
            }
            l.setArgs(fill(r.getArgsTemplate()));
            l.setStatus(status);
            l.setBizResult(detail);
            l.setErrorMsg(err == null ? null : truncate(err, 500));
            l.setOperatorUserId(owner == null ? null : owner.getUserId());
            l.setOperatorUsername(owner == null ? r.getCreatedBy() : owner.getUsername());
            l.setCostMs(System.currentTimeMillis() - t0);
            l.setCallTime(LocalDateTime.now());
            toolCallLogMapper.insert(l);
        } catch (Exception ex) {
            log.error("AI 巡检留痕写入失败 rule={}", r.getId(), ex);
        }
    }

    private LoginUser ownerIdentity(AiInspectRule r) {
        com.fssc.system.entity.SysUser su = userMapper.selectOne(new LambdaQueryWrapper<com.fssc.system.entity.SysUser>()
                .eq(com.fssc.system.entity.SysUser::getUserName, r.getCreatedBy()).last("LIMIT 1"));
        if (su == null || !"NORMAL".equals(su.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "规则创建人 " + r.getCreatedBy() + " 已不存在或停用，规则失效");
        }
        LoginUser u = new LoginUser();
        u.setSubjectType(com.fssc.common.enums.SubjectType.TENANT);
        u.setTenantId(r.getTenantId());
        u.setUserId(su.getId());
        u.setUsername(su.getUserName());
        u.setRealName(su.getNickName());
        u.setCompanyId(su.getCompanyId());
        u.setDeptId(su.getDeptId());
        u.setPerms(new java.util.HashSet<>(userMapper.selectPerms(su.getId())));
        return u;
    }

    /** 命中→进 ai_action_confirm 待确认队列（decision 属人：这里只报信，不代办）。 */
    private void queueForConfirm(AiInspectRule r, String actual) {
        AiActionConfirm c = new AiActionConfirm();
        c.setTenantId(r.getTenantId());
        c.setInstructionId("inspect-rule-" + r.getId());
        c.setActionCode("inspect://" + r.getRuleName());
        c.setParamsDigest(sha1(r.getId() + "|" + actual + "|" + System.currentTimeMillis()));
        c.setApplicantUserId(0L); // 机器发起：无"申请人"，确认时不受自确约束
        c.setStatus("PENDING");
        c.setReason(String.format("巡检规则[%s]命中：动作 %s 的 %s %s %s，实测=%s。请人工研判后续处置。",
                r.getRuleName(), r.getActionCode(), r.getJsonPath(), opText(r.getOp()), r.getExpected(), actual));
        c.setExpireAt(LocalDateTime.now().plusHours(24));
        c.setCreateTime(LocalDateTime.now());
        confirmMapper.insert(c);
    }

    // ---------------------------------------------------------------- 判据面

    static String fill(String template) {
        if (template == null) {
            return "{}";
        }
        LocalDate d = LocalDate.now();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = switch (m.group(1)) {
                case "month" -> d.toString().substring(0, 7);
                case "year" -> String.valueOf(d.getYear());
                default -> d.toString();
            };
            m.appendReplacement(sb, v);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 极简 jsonpath：$.a.b / $.list[0].x；列表根用 $ 直接表示。 */
    private String path(JsonNode root, String expr) {
        if (expr == null || expr.isBlank() || "$".equals(expr)) {
            return String.valueOf(root);
        }
        JsonNode cur = root;
        for (String seg : expr.replaceFirst("^\\$\\.?", "").split("\\.")) {
            Matcher mm = Pattern.compile("(\\w+)\\[(\\d+)]").matcher(seg);
            if (mm.matches()) {
                cur = cur.path(mm.group(1)).path(Integer.parseInt(mm.group(2)));
            } else if (seg.endsWith("[]")) {
                cur = cur.path(seg.substring(0, seg.length() - 2));
                return String.valueOf(cur.isArray() ? cur.size() : -1);
            } else {
                cur = cur.path(seg);
            }
        }
        return cur.isMissingNode() ? null : cur.asText();
    }

    private boolean breached(String op, String actual, String expected) {
        if (actual == null) {
            return !"EMPTY".equals(op);
        }
        return switch (op == null ? "GT" : op) {
            case "EMPTY" -> actual.equals("[]") || actual.equals("null") || actual.isBlank() || actual.equals("0");
            case "NOT_EMPTY" -> !(actual.equals("[]") || actual.equals("null") || actual.isBlank() || actual.equals("0"));
            case "EQ" -> actual.equals(expected);
            case "LT", "LTE", "GT", "GTE" -> {
                try {
                    double a = Double.parseDouble(actual), b = Double.parseDouble(expected);
                    yield switch (op) {
                        case "LT" -> a < b;
                        case "LTE" -> a <= b;
                        case "GTE" -> a >= b;
                        default -> a > b;
                    };
                } catch (NumberFormatException e) {
                    // 非数字或越界一律按「不匹配」处理（fail-closed，未命中即不通过）
                    yield false;
                }
            }
            default -> false;
        };
    }

    private String opText(String op) {
        return switch (op == null ? "GT" : op) {
            case "LT" -> "<"; case "LTE" -> "<="; case "GT" -> ">"; case "GTE" -> ">=";
            case "EQ" -> "="; case "NOT_EMPTY" -> "非空"; case "EMPTY" -> "为空";
            default -> op;
        };
    }

    private AiInspectRule own(Long id, LoginUser user) {
        AiInspectRule r = ruleMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(user.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则不存在");
        }
        return r;
    }

    private static String sha1(String s) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
