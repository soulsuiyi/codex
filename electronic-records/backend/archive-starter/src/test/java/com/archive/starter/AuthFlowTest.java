package com.archive.starter;

import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.common.dto.LoginRequest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证骨架集成测试：通过真实 HTTP 链路验证 登录 → 查询当前用户 → 登出。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

    private static final String TEST_USERNAME = "auth_test";
    private static final String TEST_PASSWORD = "123456";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        sysUserMapper.delete(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, TEST_USERNAME));
        SysUser user = new SysUser();
        user.setUsername(TEST_USERNAME);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRealName("认证测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginLogoutAndCurrentUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> loginEntity =
                new HttpEntity<>(new LoginRequest(TEST_USERNAME, TEST_PASSWORD), headers);

        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity("/api/v1/auth/login", loginEntity, Map.class);
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().get("code")).isEqualTo(200);

        Map<String, Object> data = (Map<String, Object>) loginResponse.getBody().get("data");
        assertThat(data.get("tokenName")).isEqualTo("Authorization");
        String tokenValue = (String) data.get("tokenValue");
        assertThat(tokenValue).isNotBlank();

        ResponseEntity<Map> unauthorizedResponse =
                restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(unauthorizedResponse.getBody()).isNotNull();
        assertThat(unauthorizedResponse.getBody().get("code")).isEqualTo(401);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", tokenValue);

        ResponseEntity<Map> meResponse =
                restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET,
                        new HttpEntity<>(authHeaders), Map.class);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().get("code")).isEqualTo(200);
        assertThat(meResponse.getBody().get("data")).isNotNull();

        ResponseEntity<Map> logoutResponse =
                restTemplate.exchange("/api/v1/auth/logout", HttpMethod.POST,
                        new HttpEntity<>(authHeaders), Map.class);
        assertThat(logoutResponse.getBody()).isNotNull();
        assertThat(logoutResponse.getBody().get("code")).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void profileAndPasswordFlow() {
        String token = login(TEST_USERNAME, TEST_PASSWORD);
        HttpHeaders headers = authHeaders(token);

        // 获取资料
        ResponseEntity<Map> profileResponse = restTemplate.exchange(
                "/api/v1/auth/profile", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(profileResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> profile = (Map<String, Object>) profileResponse.getBody().get("data");
        assertThat(profile.get("username")).isEqualTo(TEST_USERNAME);

        // 更新资料
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("realName", "认证测试-更新");
        updateBody.put("phone", "13800000000");
        updateBody.put("email", "auth@test.com");
        updateBody.put("deptId", 1);
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/v1/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers), Map.class);
        assertThat(updateResponse.getBody().get("code")).isEqualTo(200);
        assertThat(((Map<String, Object>) updateResponse.getBody().get("data")).get("realName"))
                .isEqualTo("认证测试-更新");

        // 原密码错误 → 400
        Map<String, Object> badBody = new HashMap<>();
        badBody.put("oldPassword", "wrong");
        badBody.put("newPassword", "654321");
        ResponseEntity<Map> badResponse = restTemplate.exchange(
                "/api/v1/auth/password", HttpMethod.PUT,
                new HttpEntity<>(badBody, headers), Map.class);
        assertThat(badResponse.getBody().get("code")).isEqualTo(400);

        // 正确修改密码
        Map<String, Object> okBody = new HashMap<>();
        okBody.put("oldPassword", TEST_PASSWORD);
        okBody.put("newPassword", "654321");
        ResponseEntity<Map> okResponse = restTemplate.exchange(
                "/api/v1/auth/password", HttpMethod.PUT,
                new HttpEntity<>(okBody, headers), Map.class);
        assertThat(okResponse.getBody().get("code")).isEqualTo(200);

        // 新密码可登录，旧密码失效
        assertThat(loginCode(TEST_USERNAME, "654321")).isEqualTo(200);
        assertThat(loginCode(TEST_USERNAME, TEST_PASSWORD)).isEqualTo(401);
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, password), headers), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private int loginCode(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, password), headers), Map.class);
        return ((Number) response.getBody().get("code")).intValue();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
