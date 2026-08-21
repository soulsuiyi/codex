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
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态水印端到端测试：图片/PDF 预览与下载注入水印流，借阅下载同样水印化。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WatermarkFlowTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final String SECRETARY_USERNAME = "wm_secretary";
    private static final String ARCHIVIST_USERNAME = "wm_archivist";
    private static final String HANDLER_USERNAME = "wm_handler";

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过水印测试");
        cleanUp();
    }

    @Test
    void watermarkImagePreviewAndDownload() throws Exception {
        String token = login();
        String caseNo = createCase("WM-TEST-" + System.currentTimeMillis());
        Long pngId = upload(token, caseNo, "watermark.png", PNG_BYTES);

        ResponseEntity<byte[]> preview = restTemplate.exchange(
                "/api/v1/files/" + pngId + "/preview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(preview.getStatusCode().value()).isEqualTo(200);
        assertThat(preview.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(preview.getBody()).isNotEqualTo(PNG_BYTES);

        ResponseEntity<byte[]> download = restTemplate.exchange(
                "/api/v1/files/" + pngId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment");
        assertThat(download.getBody()).isNotEqualTo(PNG_BYTES);
    }

    @Test
    void watermarkPdfPreviewAndArchivedDownload() throws Exception {
        String token = login();
        String caseNo = createCase("WM-TEST-" + System.currentTimeMillis());
        byte[] pdfBytes = createPdf("原始 PDF 内容");
        Long pdfId = upload(token, caseNo, "watermark.pdf", pdfBytes);

        ResponseEntity<byte[]> preview = restTemplate.exchange(
                "/api/v1/files/" + pdfId + "/preview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(preview.getStatusCode().value()).isEqualTo(200);
        assertThat(preview.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(preview.getBody()).isNotEqualTo(pdfBytes);
        String previewText = extractPdfText(preview.getBody());
        assertThat(previewText).contains("办案中").contains("内部文件-禁止外传");

        archiveCase(token, caseNo);
        ResponseEntity<byte[]> download = restTemplate.exchange(
                "/api/v1/files/" + pdfId + "/download", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        String downloadText = extractPdfText(download.getBody());
        assertThat(downloadText).contains("已归档");
    }

    @Test
    void borrowDownloadWatermarked() {
        String adminToken = login();
        String caseNo = createCase("WM-TEST-" + System.currentTimeMillis());
        Long pngId = upload(adminToken, caseNo, "borrow.png", PNG_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Map<String, Object> applyBody = new HashMap<>();
        applyBody.put("caseNo", caseNo);
        applyBody.put("fileIds", List.of(pngId));
        applyBody.put("reason", "水印测试借阅");
        applyBody.put("needDownload", true);
        applyBody.put("expireTime", "2099-12-31T23:59:59");
        ResponseEntity<Map> applyResponse = restTemplate.exchange(
                "/api/v1/borrows/apply", HttpMethod.POST,
                new HttpEntity<>(applyBody, authHeaders(handlerToken)), Map.class);
        Long applyId = ((Number) ((Map<String, Object>) applyResponse.getBody().get("data")).get("id")).longValue();

        approve(applyId, login(SECRETARY_USERNAME), "APPROVED");
        approve(applyId, login(ARCHIVIST_USERNAME), "APPROVED");

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/token", HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        String tokenValue = (String) ((Map<String, Object>) tokenResponse.getBody().get("data")).get("tokenValue");

        Map<String, Object> downloadBody = new HashMap<>();
        downloadBody.put("tokenValue", tokenValue);
        downloadBody.put("fileId", pngId);
        ResponseEntity<byte[]> download = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody, authHeaders(handlerToken)), byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(download.getBody()).isNotEqualTo(PNG_BYTES);
    }

    private byte[] createPdf(String content) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private void approve(Long applyId, String token, String result) {
        Map<String, Object> body = new HashMap<>();
        body.put("result", result);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/approve", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(((Number) response.getBody().get("code")).intValue()).isEqualTo(200);
    }

    private void archiveCase(String token, String caseNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("caseNo", caseNo);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(((Number) response.getBody().get("code")).intValue()).isEqualTo(200);
    }

    private Long upload(String token, String caseNo, String fileName, byte[] bytes) {
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
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertThat(((Number) response.getBody().get("code")).intValue()).isEqualTo(200);
        return ((Number) ((Map<String, Object>) response.getBody().get("data")).get("id")).longValue();
    }

    private String createCase(String caseNo) {
        SysCaseCategory category = new SysCaseCategory();
        category.setParentId(0L);
        category.setName("WM测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("水印测试案件");
        caseEntity.setCategoryId(category.getId());
        caseEntity.setStatus("ACTIVE");
        sysCaseMapper.insert(caseEntity);
        return caseNo;
    }

    private String login(String username) {
        createUserWithRoleIfAbsent(username);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, "123456"), headers), Map.class);
        assertThat(((Number) response.getBody().get("code")).intValue()).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest("admin", "admin123"), headers), Map.class);
        assertThat(((Number) response.getBody().get("code")).intValue()).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private void createUserWithRoleIfAbsent(String username) {
        if (sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            return;
        }
        String roleCode = switch (username) {
            case SECRETARY_USERNAME -> "SECRETARY";
            case ARCHIVIST_USERNAME -> "ARCHIVIST";
            default -> "CASE_HANDLER";
        };
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName("水印测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
        SysRole role = sysRoleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);
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
        jdbcTemplate.update("DELETE FROM sys_borrow_approval WHERE apply_id IN "
                + "(SELECT id FROM sys_borrow_apply WHERE case_no LIKE 'WM-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_borrow_token WHERE apply_id IN "
                + "(SELECT id FROM sys_borrow_apply WHERE case_no LIKE 'WM-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_borrow_apply WHERE case_no LIKE 'WM-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'WM-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'WM-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'WM测试分类'");
        for (String username : new String[]{SECRETARY_USERNAME, ARCHIVIST_USERNAME, HANDLER_USERNAME}) {
            SysUser user = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (user != null) {
                sysUserRoleMapper.delete(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()));
                sysUserMapper.deleteById(user.getId());
            }
        }
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module IN ('FILE', 'BORROW')");
    }
}
