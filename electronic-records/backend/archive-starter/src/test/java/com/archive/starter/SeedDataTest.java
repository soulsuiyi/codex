package com.archive.starter;

import com.archive.common.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 种子数据验证：4 个角色、管理员账号及角色绑定存在，且 admin/admin123 可登录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeedDataTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void rolesAdminAndLogin() {
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role "
                        + "WHERE role_code IN ('ADMIN', 'SECRETARY', 'ARCHIVIST', 'CASE_HANDLER')",
                Integer.class);
        assertThat(roleCount).isEqualTo(4);

        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'admin'", Integer.class);
        assertThat(adminCount).isEqualTo(1);

        Integer bindCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur "
                        + "INNER JOIN sys_user u ON u.id = ur.user_id "
                        + "INNER JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE u.username = 'admin' AND r.role_code = 'ADMIN'",
                Integer.class);
        assertThat(bindCount).isEqualTo(1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> loginEntity =
                new HttpEntity<>(new LoginRequest("admin", "admin123"), headers);
        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity("/api/v1/auth/login", loginEntity, Map.class);
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().get("code")).isEqualTo(200);
        assertThat(loginResponse.getBody().get("data")).isNotNull();
    }
}
