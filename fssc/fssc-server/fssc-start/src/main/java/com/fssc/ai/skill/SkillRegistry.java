// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.ai.AiAction;
import com.fssc.ai.AiActionCatalog;
import com.fssc.ai.AiGatewayService;
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

/**
 * 技能注册表加载器。
 *
 * <p>产物由 {@code scripts/ai/build-skill-registry.js} 生成并打包进 classpath。启动时按动作目录再校验一次：
 * backing 必须存在、aiAllowed=true、kind 与技能类型一致。不合格的技能被剔除（fail-closed），
 * 不会因为某个技能写错而拖垮整个 AI 入口，也绝不会执行未登记动作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    public static final String RESOURCE = "ai/skills-fssc.json";

    private final AiActionCatalog catalog;
    private final ObjectMapper objectMapper;

    private final Map<String, Skill> byName = new LinkedHashMap<>();
    private List<Skill> all = List.of();
    private int rejected;

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            SkillBundle bundle = objectMapper.readValue(in, SkillBundle.class);
            List<Skill> valid = new ArrayList<>();
            for (Skill s : bundle.getSkills()) {
                String why = invalidReason(s);
                if (why != null) {
                    rejected++;
                    log.error("技能被剔除（fail-closed）：{} —— {}", s.getName(), why);
                    continue;
                }
                valid.add(s);
                byName.put(s.getName(), s);
            }
            all = List.copyOf(valid);
            log.info("AI 技能注册表加载完成：total={} valid={} rejected={} generatedAt={} sourceHash={}",
                    bundle.getSkills().size(), valid.size(), rejected,
                    bundle.getGeneratedAt(), bundle.getSourceHash());
        } catch (Exception e) {
            // 资源缺失属于打包缺陷：宁可启动失败，也不要带着"没有技能"的 AI 静默上线
            throw new IllegalStateException("AI 技能注册表加载失败（" + RESOURCE + "）：" + e.getMessage(), e);
        }
    }

    private String invalidReason(Skill s) {
        if (s.getName() == null || s.getName().isBlank()) {
            return "缺少 name";
        }
        if (s.getType() == null || !List.of("read", "write", "knowledge", "nav").contains(s.getType())) {
            return "未知 type=" + s.getType();
        }
        if (s.isKnowledge() || s.isNav()) {
            return s.getBacking().isEmpty() ? null : "知识/导航技能不应绑定动作";
        }
        if (s.getBacking().isEmpty()) {
            return "缺少 backing";
        }
        for (String code : s.getBacking()) {
            AiAction a = catalog.byCode(code);
            if (a == null) {
                return "backing 未登记：" + code;
            }
            if (!a.isAiAllowed()) {
                return "backing 被红线封禁：" + code;
            }
            if (AiGatewayService.forbiddenKind(a.getKind())) {
                return "backing 属 §6 红线 kind=" + a.getKind() + "：" + code;
            }
            if (s.isRead() && !"read".equals(a.getKind())) {
                return "read 技能引用非只读动作：" + code;
            }
            if (s.isWrite() && "read".equals(a.getKind())) {
                return "write 技能引用只读动作：" + code;
            }
        }
        return null;
    }

    public List<Skill> all() {
        return all;
    }

    public Skill byName(String name) {
        return name == null ? null : byName.get(name);
    }

    public int size() {
        return all.size();
    }

    public int rejected() {
        return rejected;
    }

    @lombok.Data
    public static class SkillBundle {
        private int version;
        private String generatedAt;
        private String catalogGeneratedAt;
        private String sourceHash;
        private Map<String, Object> stats = new LinkedHashMap<>();
        private List<Skill> skills = new ArrayList<>();
    }
}
