// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.channel;

import java.time.LocalDateTime;

/**
 * 凭证推送结果（通道无关的纯数据载体）。
 * 通道实现只负责返回本对象；写入数据库与对外响应组装由 VoucherPushService 统一完成。
 */
public class VoucherPushResult {

    /** 是否成功 */
    private boolean ok;
    /** 外部系统返回单号（失败时可为空） */
    private String externalNo;
    /** 通道名（local / http） */
    private String channel;
    /** 推送时间 */
    private LocalDateTime pushTime = LocalDateTime.now();
    /** 失败原因 / 附加说明 */
    private String message;
    /** 外部响应快照（截断后写入数据库，便于排查） */
    private String responseBody;

    public static VoucherPushResult ok(String externalNo, String channel, String responseBody) {
        VoucherPushResult r = new VoucherPushResult();
        r.ok = true;
        r.externalNo = externalNo;
        r.channel = channel;
        r.responseBody = responseBody;
        return r;
    }

    public static VoucherPushResult fail(String channel, String message, String responseBody) {
        VoucherPushResult r = new VoucherPushResult();
        r.ok = false;
        r.channel = channel;
        r.message = message;
        r.responseBody = responseBody;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public String getExternalNo() {
        return externalNo;
    }

    public String getChannel() {
        return channel;
    }

    public LocalDateTime getPushTime() {
        return pushTime;
    }

    public String getMessage() {
        return message;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
