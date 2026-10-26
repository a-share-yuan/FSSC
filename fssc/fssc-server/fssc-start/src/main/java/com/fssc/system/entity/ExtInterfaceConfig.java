// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部接口统一配置（ext_interface_config，带 tenant_id）。
 * 集中管理发票验真、发票认证、OCR 影像识别、AI 大模型等外部接口的连接参数。
 * 每条记录代表一个可启停的外部接口实例；同分类下仅一个 is_default=1。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ext_interface_config")
public class ExtInterfaceConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    // R5-D-038 出参凭据掩码规则：固定长度星号 + 后 4 位供人工识别，不泄露原文长度
    /** 凭据掩码前缀（定长） */
    public static final String CRED_MASK = "********";
    /** 掩码保留的尾部可读位数 */
    public static final int CRED_MASK_TAIL = 4;
    /** 短于此长度的凭据只出全星，避免尾部可读位把原文暴露大半 */
    public static final int CRED_MASK_MIN_LEN = 8;

    /** R5-D-038 extraConfig 中按词元判定为凭据的键名片段 */
    private static final Set<String> SECRET_NAME_TOKENS = Set.of(
            "key", "secret", "token", "password", "pwd", "passwd",
            "credential", "credentials", "signature");

    /** R5-D-038 extraConfig 中按整名判定为凭据的键名（小写） */
    private static final Set<String> SECRET_NAMES = Set.of(
            "apikey", "apisecret", "secretkey", "accesskey", "accesssecret",
            "appkey", "appsecret", "clientsecret", "accesstoken",
            "refreshtoken", "authtoken", "secret", "token", "password",
            "pwd", "passwd", "credential", "credentials", "signature", "signkey");

    /** R5-D-038 JSON 文本成员（键名 + 冒号 + 字符串值），值内允许转义 */
    private static final Pattern JSON_TEXT_MEMBER = Pattern.compile(
            "\"((?:[^\"\\\\]|\\\\.)+)\"(\\s*:\\s*)\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** 接口分类：INVOICE_VERIFY 发票验真 / INVOICE_CERTIFY 发票认证 / OCR 影像识别 / AI_MODEL AI模型 */
    private String category;

    /** 接口名称（如：百度发票验真） */
    private String interfaceName;

    /** 接口编码（租户内唯一） */
    private String interfaceCode;

    /** 服务商：BAIDU / TENCENT / ALIYUN / OPENAI / QIANFAN / CUSTOM */
    private String provider;

    /** 接口请求地址 */
    private String apiUrl;

    /** API Key / AppKey（出参掩码，R5-D-038） */
    private String apiKey;

    /** API Secret / SecretKey（出参掩码，R5-D-038） */
    private String apiSecret;

    /** 模型名称（AI 模型专用，如 ERNIE-4.0-Turbo-8K） */
    private String modelName;

    /** 附加配置 JSON（如字段映射、超时、重试策略等） */
    private String extraConfig;

    /** ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 1=该分类下默认接口（同分类仅一个） */
    private Integer isDefault;

    /** 备注 */
    private String remark;

    /** 最后一次连接测试时间 */
    private LocalDateTime lastTestTime;

    /** 最后一次测试结果：SUCCESS / FAIL */
    private String lastTestResult;

    /** 最后一次测试消息 */
    private String lastTestMsg;

    // ---------- R5-D-038 凭据掩码 / 反掩码工具（仅供出参与写侧判等使用，不参与持久化） ----------

    /** R5-D-038 出参掩码：空值返空串，短凭据只出全星，其余定长星号 + 后 4 位；去重 */
    public static String maskCredential(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        String s = v.trim();
        if (looksMasked(s)) {
            return s;
        }
        return s.length() < CRED_MASK_MIN_LEN ? CRED_MASK
                : CRED_MASK + s.substring(s.length() - CRED_MASK_TAIL);
    }

    /** R5-D-038 是否为本类掩码串：≥8 个前导星号且星号后非星字符 ≤4 */
    public static boolean looksMasked(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim();
        int stars = 0;
        while (stars < s.length() && s.charAt(stars) == '*') {
            stars++;
        }
        return stars >= CRED_MASK.length() && s.length() - stars <= CRED_MASK_TAIL;
    }

    /** R5-D-038 extraConfig 内凭据类键的值出参掩码，非凭据键保持原样可读可编辑 */
    public static String maskExtraConfigSecrets(String json) {
        return rewriteSecretMembers(json, null, true);
    }

    /** R5-D-038 写侧防线：入参 extraConfig 中掩码形态的凭据值用库中原值回填 */
    public static String restoreExtraConfigSecrets(String incoming, String stored) {
        if (incoming == null || incoming.isBlank() || stored == null || stored.isBlank()) {
            return incoming;
        }
        return rewriteSecretMembers(incoming, collectSecretMembers(stored), false);
    }

    /** R5-D-038 extraConfig 中是否存在掩码形态的凭据值（新增场景拦脏数据） */
    public static boolean hasMaskedSecretMember(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        Matcher m = JSON_TEXT_MEMBER.matcher(json);
        while (m.find()) {
            if (isSecretName(m.group(1)) && looksMasked(m.group(3))) {
                return true;
            }
        }
        return false;
    }

    /** R5-D-038 masking=true 出掩码；false 时按 stored 回填掩码值 */
    private static String rewriteSecretMembers(String json, Map<String, String> stored, boolean masking) {
        if (json == null || json.isBlank()) {
            return json;
        }
        Matcher m = JSON_TEXT_MEMBER.matcher(json);
        StringBuilder sb = new StringBuilder(json.length());
        int last = 0;
        while (m.find()) {
            String name = m.group(1);
            String value = m.group(3);
            String replacement = null;
            if (isSecretName(name)) {
                if (masking) {
                    if (!looksMasked(value)) {
                        replacement = maskCredential(value);
                    }
                } else if (looksMasked(value) && stored != null) {
                    String original = stored.get(name.toLowerCase());
                    if (original != null && !looksMasked(original)) {
                        replacement = original;
                    }
                }
            }
            if (replacement != null && !replacement.equals(value)) {
                sb.append(json, last, m.start()).append('"').append(name).append('"')
                        .append(m.group(2)).append('"').append(replacement).append('"');
                last = m.end();
            }
        }
        sb.append(json, last, json.length());
        return sb.toString();
    }

    /** R5-D-038 收集 JSON 中凭据类键的原值（键名小写 → 原文捕获值） */
    private static Map<String, String> collectSecretMembers(String json) {
        Map<String, String> out = new HashMap<>();
        Matcher m = JSON_TEXT_MEMBER.matcher(json);
        while (m.find()) {
            String name = m.group(1);
            if (isSecretName(name)) {
                out.putIfAbsent(name.toLowerCase(), m.group(3));
            }
        }
        return out;
    }

    /** R5-D-038 键名是否属凭据类：整名命中或下划线/中划线词元命中 */
    private static boolean isSecretName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toLowerCase();
        if (SECRET_NAMES.contains(n)) {
            return true;
        }
        for (String t : n.split("[^a-z0-9]+")) {
            if (SECRET_NAME_TOKENS.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
