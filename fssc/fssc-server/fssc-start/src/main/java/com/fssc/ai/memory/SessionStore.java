// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.memory;

import com.fssc.framework.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆（方案 §5.7）：进程内 LRU，租户+用户双绑定，30 分钟 TTL。
 *
 * <p>只保存"上一轮业务上下文"（技能、参数、票据号、单号），不保存完整对话文本；
 * 重启即失是进程内实现的取舍（尚未落表）。跨用户读取在同一把锁下被 key 拒绝。
 */
@Slf4j
@Component
public class SessionStore {

    @Value("${fssc.ai.session.ttl-minutes:30}")
    private long ttlMinutes = 30;

    @Value("${fssc.ai.session.max-sessions:500}")
    private int maxSessions = 500;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState getOrCreate(String sessionId, LoginUser user) {
        purge();
        String id = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId.trim();
        String key = key(user, id);
        SessionState st = sessions.computeIfAbsent(key, k -> new SessionState(id));
        st.touch();
        if (sessions.size() > maxSessions) {
            evictOldest();
        }
        return st;
    }

    private String key(LoginUser user, String sessionId) {
        return (user == null ? 0 : user.getTenantId()) + ":" + (user == null ? 0 : user.getUserId()) + ":" + sessionId;
    }

    private void purge() {
        Instant cutoff = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES);
        sessions.entrySet().removeIf(e -> e.getValue().getLastActive().isBefore(cutoff));
    }

    private void evictOldest() {
        sessions.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().getLastActive()))
                .ifPresent(e -> sessions.remove(e.getKey(), e.getValue()));
    }

    /** 单会话状态；所有写操作加锁，保证同一会话的并发轮次不互相覆盖。 */
    public static class SessionState {
        private final String sessionId;
        private Instant lastActive = Instant.now();

        /** 上一轮命中的技能（读或写） */
        private String lastSkill;
        /** 上一轮读查询的参数（供"那上个月的呢"之外的基础继承） */
        private Map<String, Object> lastReadArgs = new LinkedHashMap<>();
        /** 上一张写票据的业务上下文 */
        private String lastWriteSkill;
        private Map<String, Object> lastWriteArgs = new LinkedHashMap<>();
        private Long lastConfirmId;
        /** 上一轮读到的首条单据 */
        private Long lastBillId;
        private String lastBillNo;
        private String lastQuestion;

        public SessionState(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() { return sessionId; }
        public Instant getLastActive() { return lastActive; }
        public String getLastSkill() { return lastSkill; }
        public String getLastWriteSkill() { return lastWriteSkill; }
        public Long getLastConfirmId() { return lastConfirmId; }
        public Long getLastBillId() { return lastBillId; }
        public String getLastBillNo() { return lastBillNo; }
        public String getLastQuestion() { return lastQuestion; }

        public synchronized Map<String, Object> copyLastWriteArgs() {
            return deepCopy(lastWriteArgs);
        }

        public synchronized void touch() {
            lastActive = Instant.now();
        }

        public synchronized void rememberRead(String skill, Map<String, Object> args, Long billId, String billNo, String question) {
            this.lastSkill = skill;
            this.lastReadArgs = deepCopy(args);
            this.lastBillId = billId;
            this.lastBillNo = billNo;
            this.lastQuestion = question;
            touch();
        }

        public synchronized void rememberWrite(String skill, Map<String, Object> args, Long confirmId, String question) {
            this.lastSkill = skill;
            this.lastWriteSkill = skill;
            this.lastWriteArgs = deepCopy(args);
            this.lastConfirmId = confirmId;
            this.lastQuestion = question;
            touch();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> deepCopy(Map<String, Object> in) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (in == null) {
                return out;
            }
            for (Map.Entry<String, Object> e : in.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Map<?, ?> m) {
                    out.put(e.getKey(), deepCopy((Map<String, Object>) m));
                } else {
                    out.put(e.getKey(), v);
                }
            }
            return out;
        }
    }
}
