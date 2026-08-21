package com.archive.starter;

import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.common.dto.LoginRequest;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.entity.SysFile;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 文件管理（中转站）端到端测试：上传/秒传/列表/下载/删除/审计。
 * MinIO 不可达时跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileFlowTest {

    private static final String NO_ROLE_USERNAME = "file_no_role_user";
    private static final String NO_ROLE_PASSWORD = "123456";
    private static final byte[] FILE_BYTES = "hello archive file".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysCaseMapper sysCaseMapper;

    @Autowired
    private SysCaseCategoryMapper sysCaseCategoryMapper;

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过文件测试");
        cleanUp();
    }

    @Test
    @SuppressWarnings("unchecked")
    void unauthorizedReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/cases/FILE-TEST-NONE/files", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void noRoleUserGets403() {
        createUser(NO_ROLE_USERNAME, NO_ROLE_PASSWORD);
        String token = login(NO_ROLE_USERNAME, NO_ROLE_PASSWORD);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/cases/FILE-TEST-NONE/files", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadListDownloadDeleteFlow() throws Exception {
        String token = login("admin", "admin123");
        String caseNo = createCase("FILE-TEST-" + System.currentTimeMillis());

        // 上传
        ResponseEntity<Map> uploadResponse = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, caseNo, FILE_BYTES, "file-test.txt"), Map.class);
        assertThat(uploadResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> uploaded = (Map<String, Object>) uploadResponse.getBody().get("data");
        Long fileId = ((Number) uploaded.get("id")).longValue();
        assertThat(uploaded.get("stage")).isEqualTo("STAGING");
        String dbHash = jdbcTemplate.queryForObject(
                "SELECT file_hash FROM sys_file WHERE id = ?", String.class, fileId);
        assertThat(dbHash).hasSize(64);

        // 秒传：同内容再传一次返回同一记录，库中不新增
        ResponseEntity<Map> secondResponse = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, caseNo, FILE_BYTES, "file-test.txt"), Map.class);
        assertThat(secondResponse.getBody().get("code")).isEqualTo(200);
        assertThat(((Number) ((Map<String, Object>) secondResponse.getBody().get("data")).get("id")).longValue())
                .isEqualTo(fileId);
        Integer fileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_file WHERE case_id = "
                        + "(SELECT id FROM sys_case WHERE case_no = ?) AND is_deleted = false",
                Integer.class, caseNo);
        assertThat(fileCount).isEqualTo(1);

        // 不存在案件上传 → 400
        ResponseEntity<Map> badCaseResponse = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, "FILE-TEST-NONE", FILE_BYTES, "file-test.txt"), Map.class);
        assertThat(badCaseResponse.getBody().get("code")).isEqualTo(400);

        // 列表
        ResponseEntity<Map> listResponse = restTemplate.exchange(
                "/api/v1/cases/" + caseNo + "/files", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(listResponse.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> records = (List<Map<String, Object>>) listResponse.getBody().get("data");
        assertThat(records).anyMatch(r -> ((Number) r.get("id")).longValue() == fileId);

        // 下载 URL 可访问
        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/files/" + fileId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(downloadResponse.getBody().get("code")).isEqualTo(200);
        String url = (String) downloadResponse.getBody().get("data");
        assertThat(url).startsWith("http://127.0.0.1:9000/transit-bucket/");
        HttpResponse<byte[]> objectResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(objectResponse.statusCode()).isEqualTo(200);
        assertThat(objectResponse.body()).isEqualTo(FILE_BYTES);

        // 删除 → 列表不含、下载 404
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                "/api/v1/files/" + fileId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteResponse.getBody().get("code")).isEqualTo(200);
        ResponseEntity<Map> afterDeleteList = restTemplate.exchange(
                "/api/v1/cases/" + caseNo + "/files", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(((List<Map<String, Object>>) afterDeleteList.getBody().get("data"))).isEmpty();
        ResponseEntity<Map> deletedDownload = restTemplate.exchange(
                "/api/v1/files/" + fileId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deletedDownload.getBody().get("code")).isEqualTo(404);

        // 审计
        awaitAuditLog("FILE", "UPLOAD", String.valueOf(fileId));
        awaitAuditLog("FILE", "DELETE", String.valueOf(fileId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void archivedFileCannotDelete() {
        String token = login("admin", "admin123");
        String caseNo = createCase("FILE-TEST-" + System.currentTimeMillis());
        ResponseEntity<Map> uploadResponse = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, caseNo, FILE_BYTES, "file-test.txt"), Map.class);
        Long fileId = ((Number) ((Map<String, Object>) uploadResponse.getBody().get("data")).get("id")).longValue();

        SysFile file = sysFileMapper.selectById(fileId);
        file.setStage("ARCHIVED");
        sysFileMapper.updateById(file);

        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                "/api/v1/files/" + fileId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteResponse.getBody().get("code")).isEqualTo(400);
    }

    private String createCase(String caseNo) {
        SysCaseCategory category = new SysCaseCategory();
        category.setParentId(0L);
        category.setName("FILE测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("文件测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
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

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, password), headers), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private void createUser(String username, String password) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName("文件测试用户");
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
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'FILE-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'FILE-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'FILE测试分类'");
        sysUserMapper.delete(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, NO_ROLE_USERNAME));
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'FILE'");
    }

    private void awaitAuditLog(String module, String action, String targetId) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE module = ? AND action = ? AND target_id = ?",
                    Integer.class, module, action, targetId);
            if (count != null && count > 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("审计日志未在 2 秒内写入: module=" + module + ", action=" + action + ", targetId=" + targetId);
    }
}
