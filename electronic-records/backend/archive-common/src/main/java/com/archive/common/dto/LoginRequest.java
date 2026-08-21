package com.archive.common.dto;

/**
 * 登录请求参数。
 */
public class LoginRequest {

    /** 登录账号 */
    private String username;

    /** 登录密码（明文，服务端 BCrypt 校验） */
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
