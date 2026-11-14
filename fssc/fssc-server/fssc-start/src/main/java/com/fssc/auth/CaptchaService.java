package com.fssc.auth;

import com.fssc.framework.security.LoginGuardService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录图形验证码本体。
 *
 * <p>与同仓库 BCP 门户 {@code com.unierp.bcp.portal.auth.CaptchaService} <b>同构移植</b>，
 * 不另发明第二套机制：纯 JDK {@code java.awt} 渲染 PNG（零新增三方依赖——本仓库 Maven 离线，
 * 加依赖会打断后续构建窗），校验码存<b>进程内一次性</b>缓存（TTL 120s、取用即删防重放），
 * 接口形态一致（一次性 captchaId + base64 dataURL 图片）。
 *
 * <p><b>部署语义</b>：进程内 Map 即"单实例可见"（与 BCP 现状、与
 * {@code LoginGuardService} 未启用 Redis 时的规则相同）。
 * 多实例部署时把 {@link #store} 换成 {@code DistributedState}／Redis 即可——
 * generate/validate 两步语义已收敛，替换零改动。
 *
 * <p><b>职责边界</b>：本类只回答"这张验证码对不对"，
 * 是否<b>需要</b>验证码由 {@link CaptchaTriggerService}（失败阈值触发）判定，
 * 密码校验仍由 {@link AuthService} 负责。过期 / 不存在 / 输入不符三种情形
 * 统一返回 false（对外同一个错误码），不向攻击者区分失败原因。
 */
@Service
public class CaptchaService {

    /** 存活时间：120 秒（与 BCP 同值）；到期即失效，防长期撞码。 */
    private static final long CAPTCHA_TTL_MILLIS = 120_000L;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    /** 字符数：4（与 BCP 同值，登录框一屏可见） */
    private static final int LEN = 4;
    /** 排除易混淆字符（0/O、1/I/l）。 */
    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    /** 缓存条数上限：超量先清过期项再淘汰最早到期的半数（防被刷图无限增长）。 */
    private static final int MAX_ENTRIES = 5000;

    /** captchaId → 明文条目（一次性） */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * 联调/自动化透传图片明文（与 BCP {@code bcp.auth.captcha.debug-text} 同义）。
     * 默认 false；生产必须 false，否则验证码形同虚设。仅当总开关
     * {@code fssc.login.captcha-enabled=true} 时才有实际用途（未达阈值根本不校验）。
     */
    @Value("${fssc.login.captcha-debug-text:false}")
    private boolean debugText;

    /** 生成一张验证码（同时顺带清扫过期项）。 */
    public GeneratedCaptcha generate() {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        StringBuilder sb = new StringBuilder(LEN);
        for (int i = 0; i < LEN; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        String code = sb.toString();
        store.put(id, new Entry(code, System.currentTimeMillis() + CAPTCHA_TTL_MILLIS));
        if (store.size() > MAX_ENTRIES) {
            trimIfOverCapacity();
        }
        return new GeneratedCaptcha(id, renderBase64Png(code), debugText ? code : null);
    }

    /**
     * 校验验证码并<b>立即失效</b>（一次一用，无论对错都删除，防撞码与重放）。
     *
     * @return true = 验证码正确且未过期
     */
    public boolean validate(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        Entry e = store.remove(captchaId);
        if (e == null) {
            return false;
        }
        if (System.currentTimeMillis() > e.expiresAt) {
            return false;
        }
        return e.code.equalsIgnoreCase(code.trim());
    }

    /** 存活时间（秒），供日志/联调核对。 */
    public long ttlSeconds() {
        return CAPTCHA_TTL_MILLIS / 1000L;
    }

    private String renderBase64Png(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            // 干扰线
            g.setColor(new Color(200, 205, 215));
            for (int i = 0; i < 5; i++) {
                int x1 = random.nextInt(WIDTH);
                int y1 = random.nextInt(HEIGHT);
                g.drawLine(x1, y1, x1 + random.nextInt(40), y1 + random.nextInt(30));
            }
            // 干扰点
            for (int i = 0; i < 60; i++) {
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
            }
            // 字符（逐字随机色 + 微位移，与 BCP 同款）
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(random.nextInt(80), random.nextInt(90), random.nextInt(120)));
                g.drawString(String.valueOf(code.charAt(i)), 16 + i * 24, 28 + random.nextInt(6));
            }
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            // 渲染失败是服务端问题（无头环境缺字体/编码异常），不能伪装成"验证码错误"放行
            throw new IllegalStateException("验证码图像渲染失败", e);
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> en.getValue().expiresAt <= now);
    }

    /** 超容量兜底：先清过期，仍超限则淘汰最早到期的一半（与 LocalDistributedState 同一规则） */
    private void trimIfOverCapacity() {
        purgeExpired();
        if (store.size() <= MAX_ENTRIES) {
            return;
        }
        store.entrySet().stream()
                .sorted(Comparator.comparingLong(en -> en.getValue().expiresAt))
                .limit(store.size() / 2L)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(store::remove);
    }

    /** 验证码生成结果（对外契约：captchaId + base64 图片；debugCode 仅联调模式出现）。 */
    public static class GeneratedCaptcha {
        private final String captchaId;
        private final String imageDataUrl;
        /** 仅当 {@code fssc.login.captcha-debug-text=true} 时给出明文；生产禁止开启。 */
        private final String debugCode;

        GeneratedCaptcha(String captchaId, String imageDataUrl, String debugCode) {
            this.captchaId = captchaId;
            this.imageDataUrl = imageDataUrl;
            this.debugCode = debugCode;
        }

        public String getCaptchaId() { return captchaId; }

        public String getImageDataUrl() { return imageDataUrl; }

        public String getDebugCode() { return debugCode; }
    }

    private static final class Entry {
        final String code;
        final long expiresAt;

        Entry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}
