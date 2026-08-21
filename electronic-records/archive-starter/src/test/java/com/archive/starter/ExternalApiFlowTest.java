package com.archive.starter;

import com.archive.auth.entity.SysApiKey;
import com.archive.auth.mapper.SysApiKeyMapper;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开放 API 端到端测试：API Key + HMAC 鉴权、防重放、白名单、上传与列表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExternalApiFlowTest {

    private static final String API_KEY = "ext-test-key";
    private static final String API_SECRET = "ext-test-secret";
    private static final byte[] FILE_BYTES = "external api content".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysApiKeyMapper sysApiKeyMapper;

    @Autowired
    private SysCaseMapper sysCaseMapper;

    @Autowired
    private SysCaseCategoryMapper sysCaseCategoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        createApiKey(API_KEY, API_SECRET, 1, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void authFailures() {
        // 缺少鉴权头 → 401
        ResponseEntity<Map> noHeaders = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(noHeaders.getBody().get("code")).isEqualTo(401);

        // 错误签名 → 403
        String ts = String.valueOf(System.currentTimeMillis());
        HttpHeaders badSigHeaders = apiHeaders(API_KEY, API_SECRET, "GET", "/api/v1/external/cases");
        badSigHeaders.set("X-Signature", "0".repeat(64));
        ResponseEntity<Map> badSignature = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(badSigHeaders), Map.class);
        assertThat(badSignature.getBody().get("code")).isEqualTo(403);

        // 过期时间戳 → 403
        String staleTs = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L);
        HttpHeaders staleHeaders = apiHeadersWith(API_KEY, API_SECRET, "GET", "/api/v1/external/cases", staleTs);
        ResponseEntity<Map> stale = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(staleHeaders), Map.class);
        assertThat(stale.getBody().get("code")).isEqualTo(403);

        // nonce 重放 → 403
        HttpHeaders replayHeaders = apiHeaders(API_KEY, API_SECRET, "GET", "/api/v1/external/cases");
        HttpEntity<Void> replayEntity = new HttpEntity<>(replayHeaders);
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET, replayEntity, Map.class);
        assertThat(first.getBody().get("code")).isEqualTo(200);
        ResponseEntity<Map> replay = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET, replayEntity, Map.class);
        assertThat(replay.getBody().get("code")).isEqualTo(403);

        // 禁用 Key → 403
        createApiKey("ext-test-disabled", "s1", 0, null, null);
        ResponseEntity<Map> disabled = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(apiHeaders("ext-test-disabled", "s1", "GET", "/api/v1/external/cases")), Map.class);
        assertThat(disabled.getBody().get("code")).isEqualTo(403);

        // 过期 Key → 403
        createApiKey("ext-test-expired", "s2", 1, LocalDateTime.now().minusDays(1), null);
        ResponseEntity<Map> expired = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(apiHeaders("ext-test-expired", "s2", "GET", "/api/v1/external/cases")), Map.class);
        assertThat(expired.getBody().get("code")).isEqualTo(403);

        // IP 白名单不匹配 → 403（请求来自 127.0.0.1）
        createApiKey("ext-test-ip", "s3", 1, null, "10.0.0.1");
        ResponseEntity<Map> ipBlocked = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(apiHeaders("ext-test-ip", "s3", "GET", "/api/v1/external/cases")), Map.class);
        assertThat(ipBlocked.getBody().get("code")).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadAndLists() {
        String caseNo = createCase("EXT-TEST-" + System.currentTimeMillis());

        // 上传（Base64）
        Map<String, Object> body = new HashMap<>();
        body.put("caseNo", caseNo);
        body.put("fileName", "ext-test.txt");
        body.put("fileData", Base64.getEncoder().encodeToString(FILE_BYTES));
        ResponseEntity<Map> upload = restTemplate.exchange(
                "/api/v1/external/scan/upload", HttpMethod.POST,
                new HttpEntity<>(body, apiHeaders(API_KEY, API_SECRET, "POST", "/api/v1/external/scan/upload")),
                Map.class);
        assertThat(upload.getBody().get("code")).isEqualTo(200);
        Long fileId = ((Number) ((Map<String, Object>) upload.getBody().get("data")).get("id")).longValue();
        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM sys_file WHERE id = ?", String.class, fileId);
        assertThat(stage).isEqualTo("STAGING");

        // 非法 Base64 → 400
        Map<String, Object> badBody = new HashMap<>();
        badBody.put("caseNo", caseNo);
        badBody.put("fileName", "bad.txt");
        badBody.put("fileData", "!!!not-base64!!!");
        ResponseEntity<Map> badUpload = restTemplate.exchange(
                "/api/v1/external/scan/upload", HttpMethod.POST,
                new HttpEntity<>(badBody, apiHeaders(API_KEY, API_SECRET, "POST", "/api/v1/external/scan/upload")),
                Map.class);
        assertThat(badUpload.getBody().get("code")).isEqualTo(400);

        // 案件列表
        ResponseEntity<Map> cases = restTemplate.exchange(
                "/api/v1/external/cases", HttpMethod.GET,
                new HttpEntity<>(apiHeaders(API_KEY, API_SECRET, "GET", "/api/v1/external/cases")), Map.class);
        assertThat(cases.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                ((Map<String, Object>) cases.getBody().get("data")).get("records");
        assertThat(records).anyMatch(r -> caseNo.equals(r.get("caseNo")));

        // 案件文件列表
        String filesPath = "/api/v1/external/files?caseNo=" + caseNo;
        ResponseEntity<Map> files = restTemplate.exchange(
                "/api/v1/external/files?caseNo=" + caseNo, HttpMethod.GET,
                new HttpEntity<>(apiHeaders(API_KEY, API_SECRET, "GET", filesPath)), Map.class);
        assertThat(files.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> fileList = (List<Map<String, Object>>) files.getBody().get("data");
        assertThat(fileList).anyMatch(f -> ((Number) f.get("id")).longValue() == fileId);
    }

    private HttpHeaders apiHeaders(String apiKey, String secret, String method, String path) {
        return apiHeadersWith(apiKey, secret, method, path, String.valueOf(System.currentTimeMillis()));
    }

    private HttpHeaders apiHeadersWith(String apiKey, String secret, String method, String path, String ts) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);
        headers.set("X-Timestamp", ts);
        headers.set("X-Nonce", UUID.randomUUID().toString());
        headers.set("X-Signature", signature(secret, ts, method, path));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String signature(String secret, String ts, String method, String path) {
        String canonical = ts + "\n" + method + "\n" + path;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createApiKey(String apiKey, String secret, int status,
                              LocalDateTime expireTime, String ipWhitelist) {
        SysApiKey key = new SysApiKey();
        key.setAppName("EXT-TEST");
        key.setApiKey(apiKey);
        key.setApiSecret(secret);
        key.setStatus(status);
        key.setExpireTime(expireTime);
        key.setIpWhitelist(ipWhitelist);
        sysApiKeyMapper.insert(key);
    }

    private String createCase(String caseNo) {
        SysCaseCategory category = new SysCaseCategory();
        category.setParentId(0L);
        category.setName("EXT测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("开放API测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
    }

    private void cleanUp() {
        sysApiKeyMapper.delete(Wrappers.<SysApiKey>lambdaQuery().likeRight(SysApiKey::getApiKey, "ext-test-"));
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'EXT-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'EXT-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'EXT测试分类'");
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'FILE' AND user_id IS NULL");
    }
}
