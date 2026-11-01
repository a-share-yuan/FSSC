// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

/**
 * OCR 通道 SPI（可插拔）。业务层（OcrService）只依赖本接口，通道切换不影响识别流程。
 *
 * <p>双实现按 {@code fssc.ocr.channel} 条件装配（同 framework/spi/DistributedState 先例）：</p>
 * <ul>
 *   <li>{@link MockOcrChannel}（mock 或缺省）：确定性模拟票面，演示/冒烟专用，行为与原先一致；</li>
 *   <li>{@link HttpOcrChannel}（http）：通用 HTTP POST 通道，真实引擎（百度/腾讯/开灵等）
 *       只需在「系统设置 → 全局参数」配 ocr.api.url / ocr.api.token / ocr.api.mapping，无需改代码。</li>
 * </ul>
 */
public interface OcrChannel {

    /**
     * 识别一张票据影像。实现不得向调用方抛穿异常：失败以 {@link OcrResult#isOk()}=false + errorMsg 返回。
     *
     * @param image 影像字节（当前链路未落盘读取，可为 null）
     * @param fileName 文件名/文件标识（Mock 通道据此生成确定性数据）
     */
    OcrResult recognize(byte[] image, String fileName);
}
