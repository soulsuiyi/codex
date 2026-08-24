package com.archive.common.dto;

/**
 * 用户启停请求：0-禁用 1-启用。
 */
public class UserStatusRequest {

    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
