package com.fssc.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 动作目录注册表。
 *
 * <p>目录不是手写文档：{@code bpmn-repo/drafts/ai-rights/build-action-catalog.js} 从
 * controller 注解机械生成 {@code catalog-fssc.json}，构建期打包进 classpath，启动期加载。
 * 网关的唯一事实来源就是这份文件 —— 未登记＝不可调用；{@code aiAllowed=false}＝§6 红线，永不放行。
 *
 * <p>匹配规则：真人请求走 {@code /api/tenant/**}，AI 走网关后按同一路径模板命中
 * （路径参数 {@code {id}} 转正则），因此 AI 与人在"动作"这一层严格等价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiActionCatalog {

    public static final String RESOURCE = "ai/catalog-fssc.json";

    private final ObjectMapper objectMapper;

    private final Map<String, AiAction> byCode = new LinkedHashMap<>();
    private final List<Templated> tenantTemplates = new ArrayList<>();

    private record Templated(AiAction action, Pattern pathPattern) {
    }

    @PostConstruct
    public void load() throws Exception {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode n : root.path("actions")) {
                AiAction a = new AiAction(
                        text(n, "code"), text(n, "url"), text(n, "method"), text(n, "javaMethod"),
                        text(n, "permCode"), n.path("audited").asBoolean(false),
                        text(n, "kind"), text(n, "riskLevel"), text(n, "reversibility"),
                        text(n, "idempotency"), text(n, "preconditions"),
                        n.path("aiAllowed").asBoolean(false), text(n, "domain"), text(n, "controller"),
                        n.path("schema").isMissingNode() || n.path("schema").isNull() ? null : n.path("schema"),
                        // 被拒族名（生成器已写进 JSON，仅 aiAllowed=false 的条目有值）
                        text(n, "redlineFamily"));
                byCode.put(a.getCode(), a);
                if (a.getUrl() != null && a.getUrl().startsWith("/api/tenant/")) {
                    // matchIndexable=false 的动作不进 (verb,path) 模板索引 ——
                    // 判据是"AI 经网关只能传 JSON，无法承载 multipart 文件"，这类动作按路径匹配到的请求
                    // 注定跑不通（下游 415/400），且会让"同 verb+path 双登记"产生歧义。登记本身不丢：仍可按 code 寻址。
                    if (n.path("matchIndexable").asBoolean(true)) {
                        tenantTemplates.add(new Templated(a, compile(a.getUrl())));
                    }
                }
            }
        }
        log.info("AI 动作目录加载完成：total={} 可寻址(/api/tenant)={} aiAllowed={} 带参数schema={}",
                byCode.size(), tenantTemplates.size(),
                byCode.values().stream().filter(AiAction::isAiAllowed).count(),
                byCode.values().stream().filter(a -> a.getSchema() != null).count());
    }

    public AiAction byCode(String code) {
        return code == null ? null : byCode.get(code);
    }

    /** 按真实请求（verb + URI）命中登记动作；命中不了即"未登记"。 */
    public AiAction match(String verb, String uri) {
        String path = uri;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        AiAction hit = null;
        for (Templated t : tenantTemplates) {
            if (t.action.getMethod().equalsIgnoreCase(verb) && t.pathPattern.matcher(path).matches()) {
                if (hit != null) {
                    // fail-closed：同一 verb+path 命中多条 ⇒ 模板本身有歧义，
                    // 此时谁都不要给（返回 null，网关按"未登记"403），而不是让目录顺序决定 AI 究竟调了哪一个。
                    // 宁可让该动作暂时不可达并留下告警，也不能出现"目录说是 A、执行落到 B"。
                    log.warn("动作目录歧义（fail-closed）：{} {} 同时命中 {} 与 {}，本次按未登记拒绝",
                            verb, path, hit.getCode(), t.action.getCode());
                    return null;
                }
                hit = t.action;
            }
        }
        return hit;
    }

    public List<AiAction> all() {
        return List.copyOf(byCode.values());
    }

    public int size() {
        return byCode.size();
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** URL 模板 → 正则：{xxx} 段匹配一段路径；其余按字面（Ant 风格段由 Pattern.quote 保证安全）。 */
    private static Pattern compile(String urlTemplate) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < urlTemplate.length()) {
            int open = urlTemplate.indexOf('{', i);
            if (open < 0) {
                sb.append(Pattern.quote(urlTemplate.substring(i)));
                break;
            }
            int close = urlTemplate.indexOf('}', open);
            sb.append(Pattern.quote(urlTemplate.substring(i, open))).append("[^/]+");
            i = close + 1;
        }
        return Pattern.compile(sb.append('$').toString());
    }
}
