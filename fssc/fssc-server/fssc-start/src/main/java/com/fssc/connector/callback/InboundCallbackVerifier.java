// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.callback;

import com.fssc.common.util.HmacSignatures;
import org.springframework.stereotype.Component;

/**
 * 入站回调统一验签入口（形态 B · G 步：入站回调统一 + 验签）。
 *
 * <p>把外部系统回调的签名校验收敛到连接域，供各回调端点统一调用，替代各控制器各自持有
 * 秘钥与校验逻辑。算法与既有 BCP / LinkU 实现完全一致（HMAC-SHA256 + 时间窗 + 常量时间比较），
 * 当前委托 {@link HmacSignatures}（中立包工具），因此<b>行为零变化</b>。</p>
 *
 * <p>后续接入新的外部系统回调（银行 / 税局 / ERP 响应）时，只在此扩展签名算法分支即可，
 * 回调端点无需各写一套秘钥与时间窗判断。</p>
 */
@Component
public class InboundCallbackVerifier {

    /**
     * 校验结果。
     *
     * @param ok 是否通过
     * @param reason 未通过原因；{@code ts|...} 前缀表示时间窗错误（应对应 400），其余对应 401
     */
    public record Result(boolean ok, String reason) {

        public static Result pass() {
            return new Result(true, null);
        }

        public static Result fail(String reason) {
            return new Result(false, reason);
        }

        /** 失败是否属时间窗错误（返回 400 而非 401）。 */
        public boolean isTimestampError() {
            return reason != null && reason.startsWith("ts|");
        }
    }

    /**
     * 校验回调签名与时间窗。
     *
     * @param secret 共享密钥（非空；空密钥由调用方先行拒绝，本方法不判定）
     * @param tsHeader 时间戳头（Unix 秒）
     * @param sigHeader 签名头（{@code sha256=} 前缀可选）
     * @param rawBody 原始请求体（参与签名的原文）
     * @param nowEpochSeconds 当前 Unix 秒
     * @param maxSkewSeconds 允许时间偏差（秒）
     */
    public Result verify(String secret, String tsHeader, String sigHeader, String rawBody,
                         long nowEpochSeconds, long maxSkewSeconds) {
        String err = HmacSignatures.verify(secret, tsHeader, sigHeader, rawBody, nowEpochSeconds, maxSkewSeconds);
        return err == null ? Result.pass() : Result.fail(err);
    }
}
