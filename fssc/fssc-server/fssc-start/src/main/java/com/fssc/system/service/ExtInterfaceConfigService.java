// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.mapper.ExtInterfaceConfigMapper;
import com.fssc.framework.datasource.FsscDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 外部接口统一配置管理服务。
 *
 * <p>集中管理发票验真、发票认证、OCR 影像识别、AI 大模型四类外部接口的连接参数。
 * 支持增删改查、启用/停用、设为默认、连接测试。</p>
 *
 * <p>R5-D-038：本服务一律出真值（掩码只做在控制器 HTTP 边界），否则网关与定时任务会拿到星号串。
 * 写入时 apiKey / apiSecret / extraConfig 内的凭据若为空或掩码形态，视为「未修改」，沿用库中原值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("master")
public class ExtInterfaceConfigService {

    private final ExtInterfaceConfigMapper configMapper;
    /** 仅用于 AI 模型连通性测试的最小报文序列化（单例、无状态）。 */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "INVOICE_VERIFY", "INVOICE_CERTIFY", "OCR", "AI_MODEL", "LINKU", "BANK",
            // 应收应付蓝图直连通道（任务3.1：接口配置功能，未配置走模拟回退）
            "EKP", "EBS", "GOLDEN_TAX", "ECDS", "GL_PUSH",
            // 多 ERP 适配（V1.4，见 ErpChannelResolver）：ERP=租户级 ERP 实例声明（provider 由租户配置，含 CUSTOM），
            // {PROVIDER}_{ACTION} 为对应通道的具体接口（如 {PROVIDER}_AP / {PROVIDER}_ORDER_GET）
            "ERP", "SAP", "YONYOU", "KINGDEE",
            "EBS_AP", "EBS_AP_BLUE", "EBS_ORDER_GET",
            "SAP_AP", "SAP_AP_BLUE", "SAP_ORDER_GET",
            "YONYOU_AP", "YONYOU_AP_BLUE", "YONYOU_ORDER_GET",
            "KINGDEE_AP", "KINGDEE_AP_BLUE", "KINGDEE_ORDER_GET",
            "CUSTOM_AP", "CUSTOM_AP_BLUE", "CUSTOM_ORDER_GET");

    /** 分页查询（支持按分类过滤 + 关键词搜索） */
    public PageResult<ExtInterfaceConfig> page(long pageNum, long pageSize, String category, String keyword) {
        LambdaQueryWrapper<ExtInterfaceConfig> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            qw.eq(ExtInterfaceConfig::getCategory, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ExtInterfaceConfig::getInterfaceName, keyword)
                    .or().like(ExtInterfaceConfig::getInterfaceCode, keyword)
                    .or().like(ExtInterfaceConfig::getProvider, keyword));
        }
        qw.orderByAsc(ExtInterfaceConfig::getCategory)
          .orderByDesc(ExtInterfaceConfig::getIsDefault)
          .orderByDesc(ExtInterfaceConfig::getId);
        Page<ExtInterfaceConfig> p = configMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // R5-D-038 此处出真值，掩码由控制器在 HTTP 边界完成
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** R5-D-039：按 category 汇总配置条数（含 0 条类别，供前端页签 (n) 徽标） */
    public List<Map<String, Object>> categoryStats() {
        QueryWrapper<ExtInterfaceConfig> qw = new QueryWrapper<>();
        qw.select("category", "count(*) as cnt").groupBy("category");
        List<Map<String, Object>> rows = configMapper.selectMaps(qw);
        Map<String, Long> cntMap = new LinkedHashMap<>();
        for (String c : VALID_CATEGORIES) {
            cntMap.put(c, 0L);
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object c = row.get("category");
                if (c == null) {
                    continue;
                }
                Object n = row.get("cnt");
                cntMap.put(String.valueOf(c), n instanceof Number num ? num.longValue() : 0L);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        cntMap.forEach((category, cnt) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", category);
            m.put("cnt", cnt);
            out.add(m);
        });
        return out;
    }

    /** 查询单条详情（R5-D-038：返真值，出参掩码在控制器做） */
    public ExtInterfaceConfig getById(Long id) {
        ExtInterfaceConfig c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口配置不存在: " + id);
        }
        return c;
    }

    /** 新增 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ExtInterfaceConfig config) {
        // 必填办结（批次 2C，与前端 ExtInterfaceConfig 弹窗同一规则）：名称/编码人工维护，编码租户内唯一
        AssertUtil.notBlank(config.getInterfaceName(), ErrorCode.PARAM_INVALID, "请输入接口名称");
        AssertUtil.notBlank(config.getInterfaceCode(), ErrorCode.PARAM_INVALID, "请输入接口编码");
        validateCategory(config.getCategory());
        // R5-D-038 新增场景掩码串不是可用凭据，写入数据库即坏数据，直接拦下
        rejectMaskedCredential(config.getApiKey(), "API Key");
        rejectMaskedCredential(config.getApiSecret(), "API Secret");
        if (ExtInterfaceConfig.hasMaskedSecretMember(config.getExtraConfig())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附加配置含掩码占位，请填入真实值");
        }
        // Check duplicate interface_code within tenant
        long dup = configMapper.selectCount(new LambdaQueryWrapper<ExtInterfaceConfig>()
                .eq(ExtInterfaceConfig::getInterfaceCode, config.getInterfaceCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口编码已存在: " + config.getInterfaceCode());
        }
        config.setId(null);
        if (config.getStatus() == null || config.getStatus().isBlank()) {
            config.setStatus("DISABLED");
        }
        if (config.getIsDefault() == null) {
            config.setIsDefault(0);
        }
        // If set as default, unset other defaults in same category
        if (config.getIsDefault() == 1) {
            clearDefaultInCategory(config.getCategory());
        }
        configMapper.insert(config);
        log.info("外部接口配置已新增: {} [{}] provider={}", config.getInterfaceName(),
                config.getCategory(), config.getProvider());
        return config.getId();
    }

    /** 更新（R5-D-038：凭据字段传 null/空串/掩码形态均表示不修改） */
    @Transactional(rollbackFor = Exception.class)
    public void update(ExtInterfaceConfig config) {
        ExtInterfaceConfig exist = configMapper.selectById(config.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口配置不存在: " + config.getId());
        }
        // 弹窗提交完整对象，故与 create 同一规则拦空
        AssertUtil.notBlank(config.getInterfaceName(), ErrorCode.PARAM_INVALID, "请输入接口名称");
        AssertUtil.notBlank(config.getInterfaceCode(), ErrorCode.PARAM_INVALID, "请输入接口编码");
        if (config.getCategory() != null) {
            validateCategory(config.getCategory());
        }
        // R5-D-038 留空或掩码形态一律视为「未修改」，星号串不得覆盖库中真值
        config.setApiKey(keepIfUnchanged(config.getApiKey(), exist.getApiKey()));
        config.setApiSecret(keepIfUnchanged(config.getApiSecret(), exist.getApiSecret()));
        config.setExtraConfig(ExtInterfaceConfig.restoreExtraConfigSecrets(
                config.getExtraConfig(), exist.getExtraConfig()));
        // If switching to default, unset other defaults in same category
        if (config.getIsDefault() != null && config.getIsDefault() == 1
                && (exist.getIsDefault() == null || exist.getIsDefault() != 1)) {
            String cat = config.getCategory() != null ? config.getCategory() : exist.getCategory();
            clearDefaultInCategory(cat);
        }
        configMapper.updateById(config);
        log.info("外部接口配置已更新: id={} name={}", config.getId(),
                config.getInterfaceName() != null ? config.getInterfaceName() : exist.getInterfaceName());
    }

    /** 删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        configMapper.deleteById(id);
        log.info("外部接口配置已删除: id={}", id);
    }

    /** 切换启用/停用状态 */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, String status) {
        ExtInterfaceConfig c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口配置不存在: " + id);
        }
        if (!Set.of("ENABLED", "DISABLED").contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态仅支持 ENABLED / DISABLED");
        }
        c.setStatus(status);
        configMapper.updateById(c);
        log.info("外部接口状态已切换: id={} status={} name={}", id, status, c.getInterfaceName());
    }

    /** 设为默认（同分类仅一个默认） */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        ExtInterfaceConfig c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口配置不存在: " + id);
        }
        clearDefaultInCategory(c.getCategory());
        c.setIsDefault(1);
        configMapper.updateById(c);
        log.info("外部接口已设为默认: id={} category={} name={}", id, c.getCategory(), c.getInterfaceName());
    }

    /**
     * 查询指定分类下已启用的默认接口配置（供业务模块调用外部接口时读取连接参数）。
     * 优先返回 is_default=1 且 status=ENABLED 的；否则返回该分类下第一个 status=ENABLED 的。
     */
    public ExtInterfaceConfig getEnabledByCategory(String category) {
        // Try default + enabled first
        ExtInterfaceConfig c = configMapper.selectOne(new LambdaQueryWrapper<ExtInterfaceConfig>()
                .eq(ExtInterfaceConfig::getCategory, category)
                .eq(ExtInterfaceConfig::getStatus, "ENABLED")
                .eq(ExtInterfaceConfig::getIsDefault, 1)
                .last("limit 1"));
        if (c != null) {
            return c;
        }
        // Fallback: first enabled in category
        return configMapper.selectOne(new LambdaQueryWrapper<ExtInterfaceConfig>()
                .eq(ExtInterfaceConfig::getCategory, category)
                .eq(ExtInterfaceConfig::getStatus, "ENABLED")
                .last("limit 1"));
    }

    /**
     * 测试连接：对 apiUrl 发起 HTTP GET/POST 请求，返回测试结果。
     * AI 模型类走简单的 models 列表或 ping 请求；其他类直接 GET apiUrl。
     */
    public Map<String, Object> testConnection(Long id) {
        ExtInterfaceConfig c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接口配置不存在: " + id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("interfaceName", c.getInterfaceName());
        result.put("category", c.getCategory());

        String url = c.getApiUrl();
        if (url == null || url.isBlank()) {
            result.put("success", false);
            result.put("message", "未配置 API 地址");
            saveTestResult(id, "FAIL", "未配置 API 地址");
            return result;
        }
        // R5-D-039：自指配置先拦下（实测 id=2 曾把本服务的管理端点填成外部发票验真接口）
        if (isSelfReference(url)) {
            result.put("success", false);
            result.put("message", "API 地址指向本服务自身（" + url + "），属配置错误，请填真实外部接口地址");
            saveTestResult(id, "FAIL", result.get("message").toString());
            return result;
        }

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TEST_TIMEOUT).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TEST_TIMEOUT);
            // Add Authorization header if apiKey is present
            if (c.getApiKey() != null && !c.getApiKey().isBlank()) {
                // R5-D-038 库里存了掩码占位（历史脏数据）时不做无谓外发
                if (ExtInterfaceConfig.looksMasked(c.getApiKey())) {
                    result.put("success", false);
                    result.put("message", "API Key 为掩码占位，请先填入真实值");
                    saveTestResult(id, "FAIL", "API Key 为掩码占位，请先填入真实值");
                    return result;
                }
                builder.header("Authorization", "Bearer " + c.getApiKey());
            }
            // AI 模型通道是 chat/completions：GET 会被网关判为参数缺失（"model missing"），
            // 把"模型不可用/未订购"误报成"配置填错"。这里按真实协议发最小补全请求。
            boolean aiModel = "AI_MODEL".equalsIgnoreCase(c.getCategory())
                    || url.toLowerCase(Locale.ROOT).contains("/chat/completions");
            HttpResponse<String> resp;
            if (aiModel) {
                if (c.getModelName() == null || c.getModelName().isBlank()) {
                    result.put("success", false);
                    result.put("message", "未填写模型名称（modelName），无法测试 AI 通道");
                    saveTestResult(id, "FAIL", "未填写模型名称（modelName），无法测试 AI 通道");
                    return result;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", c.getModelName());
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                body.put("max_tokens", 1);
                body.put("temperature", 0);
                builder.header("Content-Type", "application/json");
                resp = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body))).build(),
                        HttpResponse.BodyHandlers.ofString());
            } else {
                resp = client.send(builder.GET().build(),
                        HttpResponse.BodyHandlers.ofString());
            }
            boolean httpOk = resp.statusCode() >= 200 && resp.statusCode() < 300;
            String msg = scrubCredential("HTTP " + resp.statusCode() + (resp.body() != null && !resp.body().isBlank()
                    ? " (" + resp.body().substring(0, Math.min(resp.body().length(), 200)) + ")" : ""), c);

            // R5-D-039 修正（2026-10-17 运行期实测）：原实现只看 HTTP 状态码，
            // 而大量国内接口在鉴权/参数错误时仍返回 HTTP 200，把业务错误写进 body
            // （如百度 OCR `{"error_code":23,"error_msg":"IAM API-KEY authentication failed"}`）。
            // ⇒ 2xx 被一律判为「连接成功」，运维据此认为通道已接通，实际一调就失败。
            // 现在补业务错误识别：HTTP 层通过但 body 带业务错误码 ⇒ 判 FAIL。
            String bizErr = httpOk ? detectBusinessError(resp.body()) : null;
            boolean ok = httpOk && bizErr == null;
            if (bizErr != null) {
                msg = msg + " | 业务错误: " + bizErr;
            }

            result.put("success", ok);
            result.put("message", msg);
            saveTestResult(id, ok ? "SUCCESS" : "FAIL", msg);
            if (bizErr != null) {
                log.warn("外部接口 HTTP 通过但业务报错 id={} url={}: {}", id, url, bizErr);
            }
        } catch (Exception e) {
            String msg = scrubCredential("连接异常: " + e.getMessage(), c);
            result.put("success", false);
            result.put("message", msg);
            saveTestResult(id, "FAIL", msg);
            log.warn("外部接口连接测试失败 id={} url={}: {}", id, url, scrubCredential(e.getMessage(), c));
        }
        return result;
    }

    // ---------- private helpers ----------

    /**
     * 从响应体里识别「业务错误」，返回可读描述；没有则返回 {@code null}（视为成功）。
     *
     * <p>覆盖实测到的两类国内接口约定：</p>
     * <ul>
     *   <li>{@code error_code != 0}（百度 OCR / 阿里云系）：如 {@code error_code:23} 鉴权失败；</li>
     *   <li>{@code success=false} 或 {@code code != 0}（FSSC 自身响应包装，如误配成自指 URL
     *       打到自己的登录拦截会返回 {@code {"code":401,"msg":"未登录…}}）。</li>
     * </ul>
     * 只认「能明确判定为失败」的情况，解析不了就返回 null 保持宽松，
     * 避免把正常业务响应误判成失败。
     */
    private static String detectBusinessError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String b = body.trim();
        try {
            com.fasterxml.jackson.databind.JsonNode n =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(b);
            if (n.isObject()) {
                JsonNode ec = n.get("error_code");
                if (ec != null && !ec.isNull() && ec.asInt() != 0) {
                    return "error_code=" + ec.asInt()
                            + (n.hasNonNull("error_msg") ? " " + n.get("error_msg").asText() : "");
                }
                JsonNode succ = n.get("success");
                if (succ != null && succ.isBoolean() && !succ.asBoolean()) {
                    return n.hasNonNull("msg") ? n.get("msg").asText() : "success=false";
                }
                JsonNode code = n.get("code");
                if (code != null && code.isNumber() && code.asInt() != 0) {
                    return "code=" + code.asInt()
                            + (n.hasNonNull("msg") ? " " + n.get("msg").asText() : "");
                }
            }
        } catch (Exception ignore) {
            // 非 JSON（如 HTML 错误页）——解析不了就不判业务错误，HTTP 层已给出结论
        }
        return null;
    }

    /**
     * 自指检测：配置的 apiUrl 指向本服务自身（历史脏数据里出现过
     * {@code http://localhost:8089/api/tenant/system/ext-interface/2} 这类把管理端点
     * 当外部接口填的配置）。这种配置 GET 回来必然是本服务的鉴权响应，会被判成「已连通」，
     * 属配置错误而非通道可用，故直接拦下。
     */
    private static boolean isSelfReference(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("localhost:8089")
                || u.contains("127.0.0.1:8089")
                || u.contains("/api/tenant/system/ext-interface");
    }

    private void validateCategory(String category) {
        if (category == null || !VALID_CATEGORIES.contains(category)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "接口分类仅支持: " + String.join(" / ", VALID_CATEGORIES));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void clearDefaultInCategory(String category) {
        var defaults = configMapper.selectList(new LambdaQueryWrapper<ExtInterfaceConfig>()
                .eq(ExtInterfaceConfig::getCategory, category)
                .eq(ExtInterfaceConfig::getIsDefault, 1));
        for (ExtInterfaceConfig d : defaults) {
            d.setIsDefault(0);
            configMapper.updateById(d);
        }
    }

    private void saveTestResult(Long id, String testResult, String testMsg) {
        ExtInterfaceConfig update = new ExtInterfaceConfig();
        update.setId(id);
        update.setLastTestTime(LocalDateTime.now());
        update.setLastTestResult(testResult);
        update.setLastTestMsg(testMsg != null && testMsg.length() > 500
                ? testMsg.substring(0, 500) : testMsg);
        configMapper.updateById(update);
    }

    /** R5-D-038 留空 / 掩码形态 / 等于原值的出参掩码 —— 均判定为「未修改」，返回库中原值 */
    private String keepIfUnchanged(String incoming, String stored) {
        if (incoming == null || incoming.isBlank()
                || ExtInterfaceConfig.looksMasked(incoming)
                || incoming.equals(ExtInterfaceConfig.maskCredential(stored))
                || incoming.equals(previousMaskShape(stored))) {
            return stored;
        }
        return incoming;
    }

    /** R5-D-038 本缺陷修复前的旧出参掩码形态（前 2 + **** + 后 2），防历史缓存把星号串写回 */
    private String previousMaskShape(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        return v.length() <= 4 ? "****" : v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }

    /** R5-D-038 新增场景：掩码串不是可用凭据，拦下避免写入数据库脏数据 */
    private void rejectMaskedCredential(String value, String label) {
        if (ExtInterfaceConfig.looksMasked(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + " 请填入真实值，掩码占位不能被保存");
        }
    }

    /** R5-D-038 上游可能回显我们发出的凭据，写入数据库／回显／打日志前先替换成掩码 */
    private String scrubCredential(String text, ExtInterfaceConfig c) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        if (c.getApiKey() != null && !c.getApiKey().isBlank()) {
            out = out.replace(c.getApiKey(), ExtInterfaceConfig.maskCredential(c.getApiKey()));
        }
        if (c.getApiSecret() != null && !c.getApiSecret().isBlank()) {
            out = out.replace(c.getApiSecret(), ExtInterfaceConfig.maskCredential(c.getApiSecret()));
        }
        return out;
    }
}
