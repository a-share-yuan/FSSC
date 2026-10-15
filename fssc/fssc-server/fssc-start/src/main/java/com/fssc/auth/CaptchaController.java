package com.fssc.auth;

import com.fssc.common.core.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录图形验证码下发端点（C-3 登录验证码本体）。
 *
 * <pre>
 *   GET /api/auth/captcha  →  Result.data = { captchaId, image, [debugCode] }
 * </pre>
 *
 * <p><b>匿名可达</b>：本路径落在 {@code JwtAuthFilter} 的白名单 {@code /api/auth/**} 内
 * （验证码本来就是登录前拿的，见 {@link com.fssc.framework.security.JwtAuthFilter#WHITE_LIST} 约定）。</p>
 *
 * <p><b>不可缓存</b>：验证码是服务端一次性状态（captchaId → 明文），被 CDN/网关/浏览器缓存
 * 就等于把同一张图发给后续所有请求，故响应强制 {@code Cache-Control: no-store}。</p>
 *
 * <p><b>与阈值触发的配合</b>：端点本身不判断"这个人是否需要验证码"——任何时候都可以取图，
 * 是否<b>必须</b>携带由 {@link CaptchaTriggerService} 在登录时判定。这样前端在收到
 * {@code 4011 CAPTCHA_REQUIRED} 后可以直接拉图，无需二次确认。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /** 下发一张一次性图形验证码（PNG base64 dataURL，形态与 BCP 门户一致）。 */
    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        CaptchaService.GeneratedCaptcha c = captchaService.generate();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("captchaId", c.getCaptchaId());
        body.put("image", c.getImageDataUrl());
        // debug-text=false（默认）时 debugCode 为 null；Map.of 不接受 null 值，故条件放入
        if (c.getDebugCode() != null) {
            body.put("debugCode", c.getDebugCode());
        }
        return Result.success(body);
    }
}
