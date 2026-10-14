// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.core;

import com.fssc.common.exception.BizException;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装。全平台所有 Controller 返回都走它，前端约定：
 * <pre>
 * { "code": 0, "msg": "成功", "data": {...} }
 * code=0 表示成功；非 0 表示业务失败（见 ErrorCode）。
 * </pre>
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0=成功，非0=失败 */
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = "成功";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> fail(BizException ex) {
        return fail(ex.getCode(), ex.getMessage());
    }

    public boolean isSuccess() {
        return this.code == 0;
    }
}
