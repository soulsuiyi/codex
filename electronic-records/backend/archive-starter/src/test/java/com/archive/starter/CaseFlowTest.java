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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 案件管理 + 分类树端到端测试：权限、CRUD、分页、分类树、审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaseFlowTest {

    private static final String NO_ROLE_USERNAME = "case_no_role_user";
    private static final String NO_ROLE_PASSWORD = "123456";

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
    void cleanUp() {
        SysUser noRoleUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, NO_ROLE_USERNAME));
        if (noRoleUser != null) {
            sysUserRoleMapper.delete(
                    Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, noRoleUser.getId()));
            sysUserMapper.deleteById(noRoleUser.getId());
        }
        sysCaseMapper.delete(Wrappers.<SysCase>lambdaQuery().likeRight(SysCase::getCaseNo, "CASE-TEST-"));
        sysCaseCategoryMapper.delete(Wrappers.<SysCaseCategory>lambdaQuery().likeRight(SysCaseCategory::getName, "CASE测试"));
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module IN ('CASE', 'CATEGORY')");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unauthorizedReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/cases", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void noRoleUserGets403() {
        createUser(NO_ROLE_USERNAME, NO_ROLE_PASSWORD, null);
        String token = login(NO_ROLE_USERNAME, NO_ROLE_PASSWORD);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/cases", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void caseCrudFlow() {
        String token = login("admin", "admin123");
        String caseNo = "CASE-TEST-" + System.currentTimeMillis();

        // 新建分类作为案件分类
        Long categoryId = createCategory(token, "CASE测试根", null);

        // 新建案件
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("caseNo", caseNo);
        createBody.put("caseName", "测试案件");
        createBody.put("categoryId", categoryId);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/cases", HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)), Map.class);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
        Long caseId = ((Number) created.get("id")).longValue();
        assertThat(created.get("status")).isEqualTo("ACTIVE");

        // 重复案号 → 400
        ResponseEntity<Map> duplicateResponse = restTemplate.exchange(
                "/api/v1/cases", HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)), Map.class);
        assertThat(duplicateResponse.getBody().get("code")).isEqualTo(400);

        // 非法分类 → 400
        Map<String, Object> badCategoryBody = new HashMap<>(createBody);
        badCategoryBody.put("caseNo", "CASE-TEST-BAD-" + System.currentTimeMillis());
        badCategoryBody.put("categoryId", 999999999L);
        ResponseEntity<Map> badCategoryResponse = restTemplate.exchange(
                "/api/v1/cases", HttpMethod.POST,
                new HttpEntity<>(badCategoryBody, authHeaders(token)), Map.class);
        assertThat(badCategoryResponse.getBody().get("code")).isEqualTo(400);

        // 分页 + keyword
        ResponseEntity<Map> listResponse = restTemplate.exchange(
                "/api/v1/cases?page=1&size=10&keyword=" + caseNo, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(listResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> pageData = (Map<String, Object>) listResponse.getBody().get("data");
        assertThat(((Number) pageData.get("total")).longValue()).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
        assertThat(records).anyMatch(r -> caseNo.equals(r.get("caseNo")));

        // 详情
        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                "/api/v1/cases/" + caseId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(detailResponse.getBody().get("code")).isEqualTo(200);
        Map<String, Object> detail = (Map<String, Object>) detailResponse.getBody().get("data");
        assertThat(detail.get("caseNo")).isEqualTo(caseNo);

        // 更新
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("caseName", "测试案件-已更新");
        updateBody.put("categoryId", categoryId);
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/v1/cases/" + caseId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(updateResponse.getBody().get("code")).isEqualTo(200);
        assertThat(((Map<String, Object>) updateResponse.getBody().get("data")).get("caseName"))
                .isEqualTo("测试案件-已更新");

        // 归档后禁止更新 → 400
        SysCase archived = sysCaseMapper.selectById(caseId);
        archived.setStatus("ARCHIVED");
        sysCaseMapper.updateById(archived);
        ResponseEntity<Map> archivedUpdateResponse = restTemplate.exchange(
                "/api/v1/cases/" + caseId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(archivedUpdateResponse.getBody().get("code")).isEqualTo(400);

        // 审计：新建与更新均异步写入
        awaitAuditLog("CASE", "CREATE", String.valueOf(caseId));
        awaitAuditLog("CASE", "UPDATE", String.valueOf(caseId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoryTreeFlow() {
        String token = login("admin", "admin123");

        Long rootId = createCategory(token, "CASE测试根", null);
        Long childId = createCategory(token, "CASE测试子", rootId);

        // 父分类不存在 → 400
        Map<String, Object> badBody = new HashMap<>();
        badBody.put("name", "CASE测试坏子");
        badBody.put("parentId", 999999999L);
        ResponseEntity<Map> badResponse = restTemplate.exchange(
                "/api/v1/cases/categories", HttpMethod.POST,
                new HttpEntity<>(badBody, authHeaders(token)), Map.class);
        assertThat(badResponse.getBody().get("code")).isEqualTo(400);

        // 分类树结构
        ResponseEntity<Map> treeResponse = restTemplate.exchange(
                "/api/v1/cases/categories", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(treeResponse.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeResponse.getBody().get("data");
        Map<String, Object> root = tree.stream()
                .filter(node -> ((Number) node.get("id")).longValue() == rootId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("根分类不在树中"));
        assertThat(((Number) root.get("level")).intValue()).isEqualTo(1);
        List<Map<String, Object>> children = (List<Map<String, Object>>) root.get("children");
        assertThat(children).anyMatch(node -> ((Number) node.get("id")).longValue() == childId);
        assertThat(((Number) children.get(0).get("level")).intValue()).isEqualTo(2);
    }

    private Long createCategory(String token, String name, Long parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/cases/categories", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return ((Number) ((Map<String, Object>) response.getBody().get("data")).get("id")).longValue();
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

    private void createUser(String username, String password, String roleCode) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName("案件测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
        if (roleCode != null) {
            SysRole role = sysRoleMapper.selectOne(
                    Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(role.getId());
            sysUserRoleMapper.insert(userRole);
        }
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
