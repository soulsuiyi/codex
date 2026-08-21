package com.archive.starter;

import com.archive.common.dto.LoginRequest;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 子阶段 B：分片上传/合并、多版本覆盖、预览。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileStageBTest {

    private static final byte[] FILE_BYTES = "chunk-merge-content-12345".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysCaseMapper sysCaseMapper;

    @Autowired
    private SysCaseCategoryMapper sysCaseCategoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过文件测试");
        cleanUp();
    }

    @Test
    @SuppressWarnings("unchecked")
    void chunkUploadMergeFlow() throws Exception {
        String token = login();
        String caseNo = createCase("SB-TEST-" + System.currentTimeMillis());
        String identifier = "sbtest-" + System.currentTimeMillis();

        byte[] first = java.util.Arrays.copyOfRange(FILE_BYTES, 0, FILE_BYTES.length / 2);
        byte[] second = java.util.Arrays.copyOfRange(FILE_BYTES, FILE_BYTES.length / 2, FILE_BYTES.length);
        ResponseEntity<Map> chunk1 = restTemplate.exchange(
                "/api/v1/files/upload/chunk", HttpMethod.POST,
                chunkPart(token, caseNo, identifier, 1, 2, first, "part"), Map.class);
        assertThat(chunk1.getBody().get("code")).isEqualTo(200);
        ResponseEntity<Map> chunk2 = restTemplate.exchange(
                "/api/v1/files/upload/chunk", HttpMethod.POST,
                chunkPart(token, caseNo, identifier, 2, 2, second, "part"), Map.class);
        assertThat(chunk2.getBody().get("code")).isEqualTo(200);

        // 缺片：只传 1 片就要求合并 3 片 → 400
        ResponseEntity<Map> missingResponse = restTemplate.exchange(
                "/api/v1/files/upload/chunk/merge?caseNo=" + caseNo
                        + "&identifier=" + identifier + "&fileName=chunk-test.txt&totalChunks=3",
                HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(missingResponse.getBody().get("code")).isEqualTo(400);

        ResponseEntity<Map> mergeResponse = restTemplate.exchange(
                "/api/v1/files/upload/chunk/merge?caseNo=" + caseNo
                        + "&identifier=" + identifier + "&fileName=chunk-test.txt&totalChunks=2",
                HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(mergeResponse.getBody().get("code")).isEqualTo(200);
        Long fileId = ((Number) ((Map<String, Object>) mergeResponse.getBody().get("data")).get("id")).longValue();

        // 下载内容与合并字节一致
        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/files/" + fileId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        String url = (String) downloadResponse.getBody().get("data");
        HttpResponse<byte[]> objectResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(objectResponse.statusCode()).isEqualTo(200);
        assertThat(objectResponse.body()).isEqualTo(FILE_BYTES);

        awaitAuditLog("FILE", "UPLOAD", String.valueOf(fileId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void versionCoverFlow() {
        String token = login();
        String caseNo = createCase("SB-TEST-" + System.currentTimeMillis());

        Long v1Id = upload(token, caseNo, "same.txt", "version-one-content".getBytes(StandardCharsets.UTF_8));
        Integer v1 = jdbcTemplate.queryForObject(
                "SELECT version FROM sys_file WHERE id = ?", Integer.class, v1Id);
        assertThat(v1).isEqualTo(1);

        Long v2Id = upload(token, caseNo, "same.txt", "version-two-content".getBytes(StandardCharsets.UTF_8));
        assertThat(v2Id).isEqualTo(v1Id);
        Integer v2 = jdbcTemplate.queryForObject(
                "SELECT version FROM sys_file WHERE id = ?", Integer.class, v2Id);
        assertThat(v2).isEqualTo(2);

        ResponseEntity<Map> versionsResponse = restTemplate.exchange(
                "/api/v1/files/" + v2Id + "/versions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(versionsResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) versionsResponse.getBody().get("data");
        Map<String, Object> current = (Map<String, Object>) data.get("current");
        assertThat(((Number) current.get("version")).intValue()).isEqualTo(2);
        java.util.List<Map<String, Object>> history = (java.util.List<Map<String, Object>>) data.get("history");
        assertThat(history).hasSize(1);
        assertThat(((Number) history.get(0).get("version")).intValue()).isEqualTo(1);

        // 秒传：同内容再传返回同一 id，版本不新增
        Long v2Again = upload(token, caseNo, "same.txt", "version-two-content".getBytes(StandardCharsets.UTF_8));
        assertThat(v2Again).isEqualTo(v2Id);
        Integer v2After = jdbcTemplate.queryForObject(
                "SELECT version FROM sys_file WHERE id = ?", Integer.class, v2Id);
        assertThat(v2After).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewFlow() throws Exception {
        String token = login();
        String caseNo = createCase("SB-TEST-" + System.currentTimeMillis());

        Long pngId = upload(token, caseNo, "preview.png", PNG_BYTES);
        ResponseEntity<byte[]> pngPreview = restTemplate.exchange(
                "/api/v1/files/" + pngId + "/preview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(pngPreview.getStatusCode().value()).isEqualTo(200);
        assertThat(pngPreview.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(pngPreview.getBody()).isNotEqualTo(PNG_BYTES);

        Long txtId = upload(token, caseNo, "preview.txt", "hello".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<Map> txtPreview = restTemplate.exchange(
                "/api/v1/files/" + txtId + "/preview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(txtPreview.getBody().get("code")).isEqualTo(400);
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

    private HttpEntity<MultiValueMap<String, Object>> chunkPart(
            String token, String caseNo, String identifier, int chunkIndex, int totalChunks,
            byte[] bytes, String fileName) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("caseNo", caseNo);
        body.add("identifier", identifier);
        body.add("chunkIndex", String.valueOf(chunkIndex));
        body.add("totalChunks", String.valueOf(totalChunks));
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
        category.setName("SB测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("子阶段B测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest("admin", "admin123"), headers), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
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
        jdbcTemplate.update("DELETE FROM sys_file_version WHERE file_id IN "
                + "(SELECT id FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'SB-TEST-%'))");
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'SB-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'SB-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'SB测试分类'");
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'FILE'");
        File chunkRoot = new File(System.getProperty("java.io.tmpdir"), "archive-chunks");
        File[] dirs = chunkRoot.listFiles((dir, name) -> name.startsWith("sbtest"));
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
                dir.delete();
            }
        }
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
