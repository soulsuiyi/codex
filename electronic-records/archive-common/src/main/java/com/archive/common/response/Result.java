package com.archive.common.response;

import java.io.Serializable;

/**
 * 统一响应体：{ code, msg, data, timestamp }。
 * 所有 REST 接口必须返回本类型，禁止返回裸数据或自定义包装结构。
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码，成功为 200 */
    private int code;
    /** 提示信息，成功为 "success" */
    private String msg;
    /** 业务数据 */
    private T data;
    /** 服务器时间戳（毫秒） */
    private long timestamp;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
