package com.archive.common.response;

/**
 * 统一错误码（与 AGENTS.md 接口规范一致）：
 * 200 成功、400 参数错误、401 未认证、403 无权限、404 不存在、500 服务器错误。
 */
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
