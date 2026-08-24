package com.archive.starter;

import com.archive.auth.entity.SysRole;
import com.archive.auth.entity.SysUser;
import com.archive.auth.entity.SysUserRole;
import com.archive.auth.mapper.SysRoleMapper;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.auth.mapper.SysUserRoleMapper;
import com.archive.common.dto.LoginRequest;
import com.archive.core.entity.SysBorrowApply;
import com.archive.core.entity.SysBorrowToken;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysBorrowApplyMapper;
import com.archive.core.mapper.SysBorrowTokenMapper;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.service.BorrowService;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 借阅管理端到端测试：双重审批状态机、Token、借阅下载、归还、到期回收。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BorrowFlowTest {

    private static final byte[] FILE_BYTES = "borrow flow content".getBytes(StandardCharsets.UTF_8);
    private static final String SECRETARY_USERNAME = "borrow_secretary";
    private static final String ARCHIVIST_USERNAME = "borrow_archivist";
    private static final String HANDLER_USERNAME = "borrow_handler";
    private static final String OTHER_HANDLER_USERNAME = "borrow_other_handler";

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
    private SysBorrowApplyMapper sysBorrowApplyMapper;

    @Autowired
    private SysBorrowTokenMapper sysBorrowTokenMapper;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过借阅测试");
        cleanUp();
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissionAndRejectFlow() {
        String adminToken = login();
        String caseNo = createCase("BORROW-TEST-" + System.currentTimeMillis());
        Long fileId = upload(adminToken, caseNo, "borrow.txt", FILE_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Long applyId = applyId(handlerToken, caseNo, List.of(fileId), true);

        // 申请人无审批权限
        assertThat(code(approve(applyId, handlerToken, "APPROVED", null))).isEqualTo(403);
        // 档案管理员不能审第一步
        String archivistToken = login(ARCHIVIST_USERNAME);
        assertThat(code(approve(applyId, archivistToken, "APPROVED", null))).isEqualTo(403);
        // 秘书驳回
        String secretaryToken = login(SECRETARY_USERNAME);
        ResponseEntity<Map> reject = approve(applyId, secretaryToken, "REJECTED", "材料不齐");
        assertThat(code(reject)).isEqualTo(200);
        assertThat(((Map<String, Object>) reject.getBody().get("data")).get("status")).isEqualTo("REJECTED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullBorrowFlow() throws Exception {
        String adminToken = login();
        String caseNo = createCase("BORROW-TEST-" + System.currentTimeMillis());
        Long fileId = upload(adminToken, caseNo, "borrow.txt", FILE_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Long applyId = applyId(handlerToken, caseNo, List.of(fileId), true);

        String secretaryToken = login(SECRETARY_USERNAME);
        ResponseEntity<Map> step1 = approve(applyId, secretaryToken, "APPROVED", "同意初审");
        assertThat(code(step1)).isEqualTo(200);
        assertThat(((Map<String, Object>) step1.getBody().get("data")).get("status")).isEqualTo("PENDING_ADMIN");

        // 秘书不能审第二步
        assertThat(code(approve(applyId, secretaryToken, "APPROVED", null))).isEqualTo(403);

        String archivistToken = login(ARCHIVIST_USERNAME);
        ResponseEntity<Map> step2 = approve(applyId, archivistToken, "APPROVED", "同意终审");
        assertThat(code(step2)).isEqualTo(200);
        assertThat(((Map<String, Object>) step2.getBody().get("data")).get("status")).isEqualTo("ACTIVE");

        // 我的列表
        ResponseEntity<Map> my = restTemplate.exchange(
                "/api/v1/borrows/my?page=1&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        assertThat(code(my)).isEqualTo(200);
        assertThat(((Number) ((Map<String, Object>) my.getBody().get("data")).get("total")).longValue())
                .isGreaterThanOrEqualTo(1);

        // 获取 Token
        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/token", HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        assertThat(code(tokenResponse)).isEqualTo(200);
        String tokenValue = (String) ((Map<String, Object>) tokenResponse.getBody().get("data")).get("tokenValue");

        // 借阅下载
        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody(tokenValue, fileId), authHeaders(handlerToken)), Map.class);
        assertThat(code(downloadResponse)).isEqualTo(200);
        String url = (String) downloadResponse.getBody().get("data");
        assertThat(url).contains("/archive-bucket/");
        HttpResponse<byte[]> objectResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(objectResponse.statusCode()).isEqualTo(200);
        assertThat(objectResponse.body()).isEqualTo(FILE_BYTES);

        // 他人持 Token 下载 → 403
        ResponseEntity<Map> otherDownload = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody(tokenValue, fileId), authHeaders(secretaryToken)), Map.class);
        assertThat(code(otherDownload)).isEqualTo(403);

        // 归还
        ResponseEntity<Map> returnResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/return", HttpMethod.POST,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        assertThat(code(returnResponse)).isEqualTo(200);
        assertThat(((Map<String, Object>) returnResponse.getBody().get("data")).get("status")).isEqualTo("RETURNED");

        // 归还后下载 → 403
        ResponseEntity<Map> afterReturn = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody(tokenValue, fileId), authHeaders(handlerToken)), Map.class);
        assertThat(code(afterReturn)).isEqualTo(403);

        awaitAuditLog("BORROW", "APPLY", String.valueOf(applyId));
        awaitAuditLog("BORROW", "APPROVE", String.valueOf(applyId));
        awaitAuditLog("BORROW", "RETURN", String.valueOf(applyId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadWithoutPermission() {
        String adminToken = login();
        String caseNo = createCase("BORROW-TEST-" + System.currentTimeMillis());
        Long fileId = upload(adminToken, caseNo, "borrow.txt", FILE_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Long applyId = applyId(handlerToken, caseNo, List.of(fileId), false);
        approve(applyId, login(SECRETARY_USERNAME), "APPROVED", null);
        approve(applyId, login(ARCHIVIST_USERNAME), "APPROVED", null);

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/token", HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        String tokenValue = (String) ((Map<String, Object>) tokenResponse.getBody().get("data")).get("tokenValue");

        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody(tokenValue, fileId), authHeaders(handlerToken)), Map.class);
        assertThat(code(downloadResponse)).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingAndDetailFlow() {
        String adminToken = login();
        String caseNo = createCase("BORROW-TEST-" + System.currentTimeMillis());
        Long fileId = upload(adminToken, caseNo, "borrow.txt", FILE_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Long applyId = applyId(handlerToken, caseNo, List.of(fileId), true);

        // 非本人且非审批人查看详情 → 403
        String otherHandlerToken = login(OTHER_HANDLER_USERNAME);
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/v1/borrows/" + applyId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherHandlerToken)), Map.class);
        assertThat(code(forbidden)).isEqualTo(403);

        // 申请人本人可查看详情（含文件、无审批记录）
        ResponseEntity<Map> ownerDetail = restTemplate.exchange(
                "/api/v1/borrows/" + applyId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        assertThat(code(ownerDetail)).isEqualTo(200);
        Map<String, Object> detailData = (Map<String, Object>) ownerDetail.getBody().get("data");
        assertThat(((Map<String, Object>) detailData.get("apply")).get("status")).isEqualTo("PENDING_SECRETARY");
        assertThat((List<Map<String, Object>>) detailData.get("files"))
                .anyMatch(f -> ((Number) f.get("id")).longValue() == fileId);
        assertThat((List<Map<String, Object>>) detailData.get("approvals")).isEmpty();

        // 待我审批：秘书与管理员可见，办案人员不可见
        assertPendingContains(login(SECRETARY_USERNAME), applyId, true);
        assertPendingContains(login(), applyId, true);
        assertPendingContains(otherHandlerToken, applyId, false);
        assertPendingContains(login(ARCHIVIST_USERNAME), applyId, false);

        // 秘书初审通过
        String secretaryToken = login(SECRETARY_USERNAME);
        approve(applyId, secretaryToken, "APPROVED", "同意初审");

        // 初审后：秘书待办清空，档案管理员待办出现
        assertPendingContains(secretaryToken, applyId, false);
        assertPendingContains(login(ARCHIVIST_USERNAME), applyId, true);

        // 详情包含一条审批记录
        ResponseEntity<Map> detail2 = restTemplate.exchange(
                "/api/v1/borrows/" + applyId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        List<Map<String, Object>> approvals = (List<Map<String, Object>>)
                ((Map<String, Object>) detail2.getBody().get("data")).get("approvals");
        assertThat(approvals).hasSize(1);
        assertThat(approvals.get(0).get("approvalStep")).isEqualTo("SECRETARY");
        assertThat(approvals.get(0).get("result")).isEqualTo("APPROVED");
        assertThat(approvals.get(0).get("approverName")).isNotNull();

        // 档案管理员终审通过
        approve(applyId, login(ARCHIVIST_USERNAME), "APPROVED", "同意终审");
        assertPendingContains(login(ARCHIVIST_USERNAME), applyId, false);

        // 审批记录两条
        ResponseEntity<Map> detail3 = restTemplate.exchange(
                "/api/v1/borrows/" + applyId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(handlerToken)), Map.class);
        assertThat((List<Map<String, Object>>)
                ((Map<String, Object>) detail3.getBody().get("data")).get("approvals")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void expireFlow() {
        String adminToken = login();
        String caseNo = createCase("BORROW-TEST-" + System.currentTimeMillis());
        Long fileId = upload(adminToken, caseNo, "borrow.txt", FILE_BYTES);
        archiveCase(adminToken, caseNo);

        String handlerToken = login(HANDLER_USERNAME);
        Long applyId = applyId(handlerToken, caseNo, List.of(fileId), true);
        approve(applyId, login(SECRETARY_USERNAME), "APPROVED", null);
        approve(applyId, login(ARCHIVIST_USERNAME), "APPROVED", null);

        SysBorrowToken token = sysBorrowTokenMapper.selectOne(Wrappers.<SysBorrowToken>lambdaQuery()
                .eq(SysBorrowToken::getApplyId, applyId).last("LIMIT 1"));
        token.setExpireTime(LocalDateTime.now().minusMinutes(1));
        sysBorrowTokenMapper.updateById(token);

        int expired = borrowService.expireExpiredBorrows();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        SysBorrowApply apply = sysBorrowApplyMapper.selectById(applyId);
        assertThat(apply.getStatus()).isEqualTo("EXPIRED");
        SysBorrowToken revoked = sysBorrowTokenMapper.selectById(token.getId());
        assertThat(revoked.getIsRevoked()).isTrue();

        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/download", HttpMethod.POST,
                new HttpEntity<>(downloadBody(token.getTokenValue(), fileId), authHeaders(handlerToken)), Map.class);
        assertThat(code(downloadResponse)).isEqualTo(403);
    }

    @SuppressWarnings("unchecked")
    private Long applyId(String token, String caseNo, List<Long> fileIds, boolean needDownload) {
        Map<String, Object> body = new HashMap<>();
        body.put("caseNo", caseNo);
        body.put("fileIds", fileIds);
        body.put("reason", "测试借阅");
        body.put("needDownload", needDownload);
        body.put("expireTime", "2099-12-31T23:59:59");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/borrows/apply", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(code(response)).isEqualTo(200);
        return ((Number) ((Map<String, Object>) response.getBody().get("data")).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }

    private ResponseEntity<Map> approve(Long applyId, String token, String result, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("result", result);
        if (comment != null) {
            body.put("comment", comment);
        }
        return restTemplate.exchange(
                "/api/v1/borrows/" + applyId + "/approve", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
    }

    private Map<String, Object> downloadBody(String tokenValue, Long fileId) {
        Map<String, Object> body = new HashMap<>();
        body.put("tokenValue", tokenValue);
        body.put("fileId", fileId);
        return body;
    }

    @SuppressWarnings("unchecked")
    private void assertPendingContains(String token, Long applyId, boolean expected) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/borrows/pending?page=1&size=50", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(code(response)).isEqualTo(200);
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                ((Map<String, Object>) response.getBody().get("data")).get("records");
        boolean contains = records.stream().anyMatch(r -> ((Number) r.get("id")).longValue() == applyId);
        assertThat(contains).isEqualTo(expected);
    }

    private void archiveCase(String token, String caseNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("caseNo", caseNo);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/archives/archive", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(code(response)).isEqualTo(200);
    }

    private Long upload(String token, String caseNo, String fileName, byte[] bytes) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/files/upload", HttpMethod.POST,
                multipart(token, caseNo, bytes, fileName), Map.class);
        assertThat(code(response)).isEqualTo(200);
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
        category.setName("BORROW测试分类");
        category.setSortOrder(0);
        category.setLevel(1);
        sysCaseCategoryMapper.insert(category);

        SysCase caseEntity = new SysCase();
        caseEntity.setCaseNo(caseNo);
        caseEntity.setCaseName("借阅测试案件");
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
        assertThat(code(response)).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest("admin", "admin123"), headers), Map.class);
        assertThat(code(response)).isEqualTo(200);
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
        user.setRealName("借阅测试用户");
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
                + "(SELECT id FROM sys_borrow_apply WHERE case_no LIKE 'BORROW-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_borrow_token WHERE apply_id IN "
                + "(SELECT id FROM sys_borrow_apply WHERE case_no LIKE 'BORROW-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_borrow_apply WHERE case_no LIKE 'BORROW-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_file WHERE case_id IN "
                + "(SELECT id FROM sys_case WHERE case_no LIKE 'BORROW-TEST-%')");
        jdbcTemplate.update("DELETE FROM sys_case WHERE case_no LIKE 'BORROW-TEST-%'");
        jdbcTemplate.update("DELETE FROM sys_case_category WHERE name = 'BORROW测试分类'");
        for (String username : new String[]{
                SECRETARY_USERNAME, ARCHIVIST_USERNAME, HANDLER_USERNAME, OTHER_HANDLER_USERNAME}) {
            SysUser user = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (user != null) {
                sysUserRoleMapper.delete(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()));
                sysUserMapper.deleteById(user.getId());
            }
        }
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'BORROW'");
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
