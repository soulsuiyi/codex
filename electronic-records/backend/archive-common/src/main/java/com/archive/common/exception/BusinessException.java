package com.archive.common.exception;

import com.archive.common.response.ResultCode;

/**
 * 业务异常：业务模块在服务层抛出，由全局异常处理器统一转换为标准响应体。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
