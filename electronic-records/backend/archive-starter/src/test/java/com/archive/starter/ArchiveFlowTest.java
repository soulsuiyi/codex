package com.archive.starter;

import com.archive.auth.entity.SysRole;
import com.archive.auth.entity.SysUser;
import com.archive.auth.entity.SysUserRole;
import com.archive.auth.mapper.SysRoleMapper;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.auth.mapper.SysUserRoleMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 归档管理端到端测试：权限、一键归档、完整性校验、记录查询、审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArchiveFlowTest {

    private static final byte[] FILE_BYTES = "archive flow content".getBytes(StandardCharsets.UTF_8);
    private static final String ARCHIVIST_USERNAME = "archive_archivist";
    private static final String HANDLER_USERNAME = "archive_handler";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

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
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过归档测试");
        cleanUp();
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissionChecks() {
        ResponseEntity<Map> unauthorized = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(new HashMap<>(), new HttpHeaders()), Map.class);
        assertThat(unauthorized.getBody().get("code")).isEqualTo(401);

        createUserWithRole(HANDLER_USERNAME, "CASE_HANDLER");
        String handlerToken = login(HANDLER_USERNAME);
        String caseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(caseNo), authHeaders(handlerToken)), Map.class);
        assertThat(forbidden.getBody().get("code")).isEqualTo(403);

        createUserWithRole(ARCHIVIST_USERNAME, "ARCHIVIST");
        String archivistToken = login(ARCHIVIST_USERNAME);
        String caseNo2 = createCase("ARCH-TEST-" + System.currentTimeMillis());
        String adminToken = login();
        upload(adminToken, caseNo2, "archivist.txt", FILE_BYTES);
        ResponseEntity<Map> ok = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(caseNo2), authHeaders(archivistToken)), Map.class);
        assertThat(ok.getBody().get("code")).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void archiveFlow() throws Exception {
        String token = login();
        String caseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        Long fileId = upload(token, caseNo, "archive.txt", FILE_BYTES);

        ResponseEntity<Map> archiveResponse = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(caseNo), authHeaders(token)), Map.class);
        assertThat(archiveResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) archiveResponse.getBody().get("data");
        Long archiveId = ((Number) data.get("id")).longValue();
        assertThat(((Number) data.get("fileCount")).intValue()).isEqualTo(1);
        assertThat(data.get("status")).isEqualTo("SUCCESS");

        // 数据库状态
        Map<String, Object> fileRow = jdbcTemplate.queryForMap(
                "SELECT stage, storage_bucket FROM sys_file WHERE id = ?", fileId);
        assertThat(fileRow.get("stage")).isEqualTo("ARCHIVED");
        assertThat(fileRow.get("storage_bucket")).isEqualTo("archive-bucket");
        String caseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_case WHERE case_no = ?", String.class, caseNo);
        assertThat(caseStatus).isEqualTo("ARCHIVED");
        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_archive WHERE case_no = ?", Integer.class, caseNo);
        assertThat(archiveCount).isEqualTo(1);

        // 归档对象可下载且内容一致（URL 指向 archive-bucket）
        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/files/" + fileId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        String url = (String) downloadResponse.getBody().get("data");
        assertThat(url).contains("/archive-bucket/");
        HttpResponse<byte[]> objectResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(objectResponse.statusCode()).isEqualTo(200);
        assertThat(objectResponse.body()).isEqualTo(FILE_BYTES);

        // 重复归档 → 400
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(caseNo), authHeaders(token)), Map.class);
        assertThat(duplicate.getBody().get("code")).isEqualTo(400);

        // 记录列表与详情
        ResponseEntity<Map> listResponse = restTemplate.exchange(
                "/api/v1/archives?page=1&size=10&caseNo=" + caseNo, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(listResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> page = (Map<String, Object>) listResponse.getBody().get("data");
        assertThat(((Number) page.get("total")).longValue()).isEqualTo(1);

        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                "/api/v1/archives/" + archiveId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(detailResponse.getBody().get("code")).isEqualTo(200);
        assertThat(((Map<String, Object>) detailResponse.getBody().get("data")).get("caseNo")).isEqualTo(caseNo);

        awaitAuditLog("ARCHIVE", "ARCHIVE", String.valueOf(archiveId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void archiveCountsStructuredDocs() {
        String token = login();
        String caseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        // 内容必须不同，避免 SHA-256 秒传去重
        upload(token, caseNo, "询问笔录.pdf", "笔录内容".getBytes(StandardCharsets.UTF_8));
        upload(token, caseNo, "起诉书.docx", "起诉书内容".getBytes(StandardCharsets.UTF_8));
        upload(token, caseNo, "证据扫描件.jpg", "扫描件内容".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map> archiveResponse = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(caseNo), authHeaders(token)), Map.class);
        assertThat(archiveResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) archiveResponse.getBody().get("data");
        assertThat(((Number) data.get("fileCount")).intValue()).isEqualTo(3);
        // 笔录 + 起诉书命中 STRUCTURED_DOC 关键词，证据扫描件不命中
        assertThat(((Number) data.get("structDocCount")).intValue()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void archiveErrors() {
        String token = login();

        // 案件不存在
        ResponseEntity<Map> noCase = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody("ARCH-TEST-NONE"), authHeaders(token)), Map.class);
        assertThat(noCase.getBody().get("code")).isEqualTo(400);

        // 无文件
        String emptyCaseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        ResponseEntity<Map> noFiles = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(emptyCaseNo), authHeaders(token)), Map.class);
        assertThat(noFiles.getBody().get("code")).isEqualTo(400);

        // 篡改指纹
        String tamperCaseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        Long fileId = upload(token, tamperCaseNo, "tamper.txt", FILE_BYTES);
        SysFile file = sysFileMapper.selectById(fileId);
        file.setFileHash("0".repeat(64));
        sysFileMapper.updateById(file);
        ResponseEntity<Map> tampered = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(tamperCaseNo), authHeaders(token)), Map.class);
        assertThat(tampered.getBody().get("code")).isEqualTo(400);

        // 非 ACTIVE 案件
        String closedCaseNo = createCase("ARCH-TEST-" + System.currentTimeMillis());
        upload(token, closedCaseNo, "closed.txt", FILE_BYTES);
        SysCase closed = sysCaseMapper.selectOne(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, closedCaseNo));
        closed.setStatus("CLOSED");
        sysCaseMapper.updateById(closed);
        ResponseEntity<Map> closedResponse = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(caseBody(closedCaseNo), authHeaders(token)), Map.class);
        assertThat(closedResponse.getBody().get("code")).isEqualTo(400);
    }

    private Map<String, Object> caseBody(String caseNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("caseNo", caseNo);
        return body;
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
        category.setName("ARCH测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("归档测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
    }

    private void createUserWithRole(String username, String roleCode) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName("归档测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
        SysRole role = sysRoleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);
    }

    private String login() {
        return login("admin", "admin123");
    }

    private String login(String username) {
        return login(username, "123456");
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

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
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
        jdbcTemplate.update("DELETE FROM sys_archive WHERE case_no LIKE 'ARCH-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'ARCH-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'ARCH-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'ARCH测试分类'");
        for (String username : new String[]{ARCHIVIST_USERNAME, HANDLER_USERNAME}) {
            SysUser user = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (user != null) {
                sysUserRoleMapper.delete(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()));
                sysUserMapper.deleteById(user.getId());
            }
        }
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'ARCHIVE'");
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
