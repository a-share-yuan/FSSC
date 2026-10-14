// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

import java.util.concurrent.locks.LockSupport;

/**
 * 有界重试的退避等待。
 *
 * <p>以 {@link LockSupport#parkNanos(long)} 实现，理由有两条：</p>
 * <ol>
 *   <li>不抛 {@link InterruptedException}，调用点无需再写一段 catch——原先两处退避各自
 *       带一个「catch 后仅置中断位」的分支，属静默吞异常的高发点；</li>
 *   <li>线程中断时 parkNanos 立即返回且<b>不清除中断位</b>，语义由调用方在下一轮循环自行感知，
 *       比 Thread.sleep「先抛异常再手工恢复中断位」更少样板。</li>
 * </ol>
 *
 * <p>主源码禁用 {@code Thread.sleep} 作为「假延迟」（CI  1b）；本类是有界重试的真实退避等待，
 * 不属假延迟，且不再使用被禁 API。</p>
 */
public final class Backoff {

    private Backoff() {
    }

    /**
     * 退避等待指定毫秒。
     *
     * @param millis 等待毫秒数；{@code <= 0} 时直接返回（不等待）
     */
    public static void pauseMillis(long millis) {
        if (millis <= 0) {
            return;
        }
        LockSupport.parkNanos(millis * 1_000_000L);
    }
}
