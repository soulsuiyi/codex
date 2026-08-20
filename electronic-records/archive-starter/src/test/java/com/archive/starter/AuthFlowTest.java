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
}
