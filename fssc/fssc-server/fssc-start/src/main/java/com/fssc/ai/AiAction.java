package com.fssc.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 动作目录条目。由 build-action-catalog.js 机械生成后打包进 classpath，
 * 网关按 (method, path) 或 code 命中；未登记 / aiAllowed=false ⇒ 403。
 */
public final class AiAction {

    private final String code;
    private final String url;
    private final String method;
    private final String javaMethod;
    private final String permCode;
    private final boolean audited;
    private final String kind;
    private final String riskLevel;
    private final String reversibility;
    private final String idempotency;
    private final String preconditions;
    private final boolean aiAllowed;
    private final String domain;
    private final String controller;
    /** 目录 v2：@RequestBody DTO／@RequestParam／@PathVariable 的机械采集结果，供填参提示词使用。 */
    private final JsonNode schema;
    /**
     * 被拒时的"因哪一族被封"（仅 aiAllowed=false 的条目有值）。
     * 拒答要能解释，就得报得出族名，故把生成器已写进 JSON 的 redlineFamily 读进实体。
     */
    private final String redlineFamily;

    public AiAction(String code, String url, String method, String javaMethod, String permCode, boolean audited,
                    String kind, String riskLevel, String reversibility, String idempotency,
                    String preconditions, boolean aiAllowed, String domain, String controller, JsonNode schema,
                    String redlineFamily) {
        this.code = code;
        this.url = url;
        this.method = method;
        this.javaMethod = javaMethod;
        this.permCode = permCode;
        this.audited = audited;
        this.kind = kind;
        this.riskLevel = riskLevel;
        this.reversibility = reversibility;
        this.idempotency = idempotency;
        this.preconditions = preconditions;
        this.aiAllowed = aiAllowed;
        this.domain = domain;
        this.controller = controller;
        this.schema = schema;
        this.redlineFamily = redlineFamily;
    }

    public String getCode() { return code; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public String getJavaMethod() { return javaMethod; }
    public String getPermCode() { return permCode; }
    public boolean isAudited() { return audited; }
    public String getKind() { return kind; }
    public String getRiskLevel() { return riskLevel; }
    public String getReversibility() { return reversibility; }
    public String getIdempotency() { return idempotency; }
    public String getPreconditions() { return preconditions; }
    public boolean isAiAllowed() { return aiAllowed; }
    public String getDomain() { return domain; }
    public String getController() { return controller; }

    /** 仅被拒动作为非空；用于把"为什么不能做"讲清楚。 */
    public String getRedlineFamily() { return redlineFamily; }

    /** 参数结构只在服务端编排里用，不扩到对外动作清单 JSON（保持既有响应形状不变）。 */
    @JsonIgnore
    public JsonNode getSchema() { return schema; }
}
