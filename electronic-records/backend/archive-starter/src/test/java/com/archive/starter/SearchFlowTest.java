package com.archive.starter;

import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.common.dto.LoginRequest;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * H2 全文检索端到端测试：异步文本提取 → FULLTEXT 检索 → 高亮。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchFlowTest {

    private static final String KEYWORD = "archiveindex2026";
    private static final String NO_ROLE_USERNAME = "search_no_role_user";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysCaseMapper sysCaseMapper;

    @Autowired
    private SysCaseCategoryMapper sysCaseCategoryMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过检索测试");
        cleanUp();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAndDetailWithHighlight() {
        String token = login();
        String caseNo = createCase("SEARCH-TEST-" + System.currentTimeMillis());
        String content = "lorem ipsum " + KEYWORD + " full text content for archive search";
        Long fileId = upload(token, caseNo, "search-test.txt", content.getBytes(StandardCharsets.UTF_8));

        awaitIndexed(fileId);

        ResponseEntity<Map> searchResponse = restTemplate.exchange(
                "/api/v1/search/files?keyword=" + KEYWORD + "&page=1&size=10",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(searchResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> page = (Map<String, Object>) searchResponse.getBody().get("data");
        assertThat(((Number) page.get("total")).longValue()).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        Map<String, Object> hit = records.stream()
                .filter(r -> ((Number) ((Map<String, Object>) r.get("file")).get("id")).longValue() == fileId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("检索未命中目标文件"));
        assertThat((String) hit.get("snippet")).contains("<em>" + KEYWORD + "</em>");

        // 详情含高亮
        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                "/api/v1/search/files/" + fileId + "?keyword=" + KEYWORD,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(detailResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> detail = (Map<String, Object>) detailResponse.getBody().get("data");
        assertThat(((Number) ((Map<String, Object>) detail.get("file")).get("id")).longValue()).isEqualTo(fileId);
        assertThat((String) detail.get("snippet")).contains("<em>" + KEYWORD + "</em>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyKeywordAndNoRole() {
        String token = login();
        ResponseEntity<Map> emptyKeyword = restTemplate.exchange(
                "/api/v1/search/files?keyword=", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(emptyKeyword.getBody().get("code")).isEqualTo(400);

        createUser(NO_ROLE_USERNAME);
        String noRoleToken = login(NO_ROLE_USERNAME, "123456");
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/v1/search/files?keyword=" + KEYWORD, HttpMethod.GET,
                new HttpEntity<>(authHeaders(noRoleToken)), Map.class);
        assertThat(forbidden.getBody().get("code")).isEqualTo(403);
    }

    private void awaitIndexed(Long fileId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String content = jdbcTemplate.queryForObject(
                    "SELECT content FROM sys_file WHERE id = ?", String.class, fileId);
            if (content != null && !content.isBlank()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("文件文本未在 5 秒内完成提取索引");
    }

    private Long upload(String token, String caseNo, String fileName, byte[] bytes) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, caseNo, bytes, fileName), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return ((Number) ((Map<String, Object>) response.getBody().get("data")).get("id")).longValue();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(
            String token, String caseNo, byte[] bytes, String fileName) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("caseNo", caseNo);
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return new HttpEntity<>(body, headers);
    }

    private String createCase(String caseNo) {
        SysCaseCategory category = new SysCaseCategory();
        category.setParentId(0L);
        category.setName("SEARCH测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("检索测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
    }

    private String login() {
        return login("admin", "admin123");
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

    private void createUser(String username) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName("检索测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        return headers;
    }

    private boolean minioHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://127.0.0.1:9000/minio/health/live", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'SEARCH-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'SEARCH-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'SEARCH测试分类'");
        sysUserMapper.delete(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, NO_ROLE_USERNAME));
    }
}
