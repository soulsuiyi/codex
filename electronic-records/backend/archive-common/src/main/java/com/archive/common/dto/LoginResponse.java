package com.archive.common.dto;

/**
 * 登录响应：返回 Sa-Token 令牌。
 */
public class LoginResponse {

    /** 令牌名称（默认 Authorization） */
    private String tokenName;

    /** 令牌值 */
    private String tokenValue;

    public LoginResponse() {
    }

    public LoginResponse(String tokenName, String tokenValue) {
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }
}
