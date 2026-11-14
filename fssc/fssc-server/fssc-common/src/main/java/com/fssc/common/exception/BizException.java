package com.fssc.common.exception;

import com.fssc.common.core.ErrorCode;

/**
 * 业务异常。由业务代码显式抛出，携带 ErrorCode，GlobalExceptionHandler 统一转成 Result。
 * 不要用 RuntimeException 散落各处 —— 统一的异常类型是"自动检查/可观测"的前提。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + "：" + detail);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
