package com.fssc.framework.security;

import com.fssc.framework.spi.DistributedState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * JWT 黑名单服务（存储经 {@link DistributedState} 可插拔）。
 * <p>
 * 支持：用户主动退出（自己下线）、管理员强制踢下线（将用户 Token 加入黑名单）。
 * 黑名单基于 token 摘要（SHA-256 全串哈希）存储，防止明文令牌滞留。
 *
 * <p><b>为什么必须外置到分布式存储</b>：早期实现是进程内 {@code ConcurrentHashMap}，
 * 单实例部署无碍；一旦拆分为多实例/多服务（如影像档案独立部署），主服务踢下线后
 * 其他服务进程完全无感知，被踢用户的 Token 仍可继续访问——这是实打实的安全洞。
 * 经 SPI 存取：
 * <ul>
 *   <li>{@code fssc.redis.enabled=false}（缺省）：走 {@code LocalDistributedState}，行为与原先一致；</li>
 *   <li>{@code fssc.redis.enabled=true}：走 Redis SET EX，多实例/多服务共享同一份黑名单。</li>
 * </ul>
 *
 * <p>TTL 取 Token 自身剩余有效期（不超过 24 小时上限），Token 到期后黑名单标记自动清除，
 * 无需额外清理任务。
 */
@Slf4j
@Service
public class TokenBlacklistService {

    /** key 命名空间：与 DistributedState 的全局前缀共同构成 fssc:jwt:blacklist:{摘要} */
    private static final String NS = "jwt:blacklist:";
    /** TTL 上限：兜底防止异常长有效期 Token 长期占用存储 */
    private static final Duration MAX_TTL = Duration.ofHours(24);

    private final DistributedState state;
    private final TokenService tokenService;

    public TokenBlacklistService(DistributedState state, TokenService tokenService) {
        this.state = state;
        this.tokenService = tokenService;
    }

    /**
     * 将 Token 加入黑名单，有效期 = Token 剩余有效期（≤ 24h）。
     * Token 已过期时不写入（其本身即不可用，无需占用存储）。
     */
    public void add(String token) {
        if (token == null || token.isBlank()) return;
        Duration ttl = resolveTtl(token);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        state.mark(NS + digest(token), ttl);
    }

    /** 检查 Token 是否在黑名单中（存储不可用时按 SPI 约定 fail-closed 拒绝） */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return state.marked(NS + digest(token));
    }

    /**
     * 计算黑名单 TTL：取 Token 剩余有效期与 24h 上限的较小值。
     * 解析失败（密钥轮换后的旧 Token、格式非法等）兜底按上限处理——安全设施宁可多存，
     * 也必须保证"踢下线一定生效"。
     */
    private Duration resolveTtl(String token) {
        try {
            long remain = tokenService.parseRemainingMillis(token);
            return remain <= 0 ? Duration.ZERO : Duration.ofMillis(Math.min(remain, MAX_TTL.toMillis()));
        } catch (Exception e) {
            log.warn("[TokenBlacklist] 解析 Token 有效期失败，按上限 {} 兜底拉黑", MAX_TTL, e);
            return MAX_TTL;
        }
    }

    /** token 全串 SHA-256 摘要（不可用前 N 位：JWT 头相同，会导致同用户全量误封） */
    private String digest(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return token;
        }
    }
}
