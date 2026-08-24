package com.archive.starter;

import com.archive.auth.entity.SysApiKey;
import com.archive.auth.entity.SysMenu;
import com.archive.auth.entity.SysRole;
import com.archive.auth.entity.SysUser;
import com.archive.auth.entity.SysUserRole;
import com.archive.auth.mapper.SysApiKeyMapper;
import com.archive.auth.mapper.SysMenuMapper;
import com.archive.auth.mapper.SysRoleMapper;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.auth.mapper.SysUserRoleMapper;
import com.archive.common.dto.LoginRequest;
import com.archive.core.entity.SysDict;
import com.archive.core.mapper.SysDictMapper;
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
 * 系统管理端到端测试：权限控制、用户/角色/菜单/字典/API Key CRUD、审计日志。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private SysDictMapper sysDictMapper;

    @Autowired
    private SysApiKeyMapper sysApiKeyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        List<SysUser> testUsers = sysUserMapper.selectList(
                Wrappers.<SysUser>lambdaQuery().likeRight(SysUser::getUsername, "SYS-TEST-"));
        for (SysUser user : testUsers) {
            sysUserRoleMapper.delete(
                    Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()));
            sysUserMapper.deleteById(user.getId());
        }
        sysRoleMapper.delete(Wrappers.<SysRole>lambdaQuery().likeRight(SysRole::getRoleCode, "SYS_TEST_"));
        jdbcTemplate.update("DELETE FROM sys_menu WHERE parent_id IN "
                + "(SELECT id FROM sys_menu WHERE menu_name LIKE 'SYS测试%')");
        sysMenuMapper.delete(Wrappers.<SysMenu>lambdaQuery().likeRight(SysMenu::getMenuName, "SYS测试"));
        sysDictMapper.delete(Wrappers.<SysDict>lambdaQuery().eq(SysDict::getDictType, "SYS_TEST_TYPE"));
        sysApiKeyMapper.delete(Wrappers.<SysApiKey>lambdaQuery().likeRight(SysApiKey::getAppName, "SYS-TEST-"));
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE module = 'SYSTEM'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unauthorizedReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/system/users", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonAdminGets403() {
        createUser("SYS-TEST-403", "123456", "CASE_HANDLER");
        String token = login("SYS-TEST-403", "123456");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/system/users", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleCrudFlow() {
        String token = login("admin", "admin123");
        String roleCode = "SYS_TEST_" + System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("roleCode", roleCode);
        body.put("roleName", "系统测试角色");
        body.put("description", "测试角色");
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/system/roles", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(create.getBody().get("code")).isEqualTo(200);
        Long roleId = ((Number) ((Map<String, Object>) create.getBody().get("data")).get("id")).longValue();

        // 重复编码 → 400
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/system/roles", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(duplicate.getBody().get("code")).isEqualTo(400);

        // 列表包含新角色
        ResponseEntity<Map> list = restTemplate.exchange(
                "/api/v1/system/roles", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(list.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> roles = (List<Map<String, Object>>) list.getBody().get("data");
        assertThat(roles).anyMatch(r -> roleCode.equals(r.get("roleCode")));

        // 更新
        Map<String, Object> updateBody = new HashMap<>(body);
        updateBody.put("roleName", "系统测试角色-更新");
        ResponseEntity<Map> update = restTemplate.exchange(
                "/api/v1/system/roles/" + roleId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(update.getBody().get("code")).isEqualTo(200);
        assertThat(((Map<String, Object>) update.getBody().get("data")).get("roleName"))
                .isEqualTo("系统测试角色-更新");

        // 删除（未分配用户）
        ResponseEntity<Map> delete = restTemplate.exchange(
                "/api/v1/system/roles/" + roleId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(delete.getBody().get("code")).isEqualTo(200);
        awaitAuditLog("SYSTEM", "CREATE", String.valueOf(roleId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void userCrudFlow() {
        String token = login("admin", "admin123");
        String username = "SYS-TEST-" + System.currentTimeMillis();
        Long caseHandlerRoleId = roleIdByCode("CASE_HANDLER");

        // 新建用户并绑定角色
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", "123456");
        body.put("realName", "系统测试用户");
        body.put("status", 1);
        body.put("roleIds", List.of(caseHandlerRoleId));
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(create.getBody().get("code")).isEqualTo(200);
        Long userId = ((Number) ((Map<String, Object>) create.getBody().get("data")).get("id")).longValue();

        // 重复用户名 → 400
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(duplicate.getBody().get("code")).isEqualTo(400);

        // 分页查询含新用户
        ResponseEntity<Map> list = restTemplate.exchange(
                "/api/v1/system/users?page=1&size=10&keyword=" + username, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(list.getBody().get("code")).isEqualTo(200);
        Map<String, Object> page = (Map<String, Object>) list.getBody().get("data");
        assertThat(((Number) page.get("total")).longValue()).isGreaterThanOrEqualTo(1);

        // 详情含角色编码
        ResponseEntity<Map> detail = restTemplate.exchange(
                "/api/v1/system/users/" + userId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        Map<String, Object> detailData = (Map<String, Object>) detail.getBody().get("data");
        assertThat((List<String>) detailData.get("roleCodes")).contains("CASE_HANDLER");

        // 更新
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("realName", "系统测试用户-更新");
        updateBody.put("status", 1);
        updateBody.put("roleIds", List.of(caseHandlerRoleId));
        ResponseEntity<Map> update = restTemplate.exchange(
                "/api/v1/system/users/" + userId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(update.getBody().get("code")).isEqualTo(200);

        // 禁用后无法登录 → 401
        restTemplate.exchange("/api/v1/system/users/" + userId + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", 0), authHeaders(token)), Map.class);
        ResponseEntity<Map> disabledLogin = restTemplate.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, "123456"), jsonHeaders()), Map.class);
        assertThat(disabledLogin.getBody().get("code")).isEqualTo(401);

        // 启用 + 重置密码后可用新密码登录
        restTemplate.exchange("/api/v1/system/users/" + userId + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", 1), authHeaders(token)), Map.class);
        restTemplate.exchange("/api/v1/system/users/" + userId + "/password", HttpMethod.PUT,
                new HttpEntity<>(Map.of("password", "654321"), authHeaders(token)), Map.class);
        ResponseEntity<Map> relogin = restTemplate.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, "654321"), jsonHeaders()), Map.class);
        assertThat(relogin.getBody().get("code")).isEqualTo(200);

        // 重新分配角色（清空）
        restTemplate.exchange("/api/v1/system/users/" + userId + "/roles", HttpMethod.PUT,
                new HttpEntity<>(Map.of("roleIds", List.of()), authHeaders(token)), Map.class);
        ResponseEntity<Map> afterAssign = restTemplate.exchange(
                "/api/v1/system/users/" + userId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat((List<String>) ((Map<String, Object>) afterAssign.getBody().get("data")).get("roleCodes"))
                .isEmpty();

        awaitAuditLog("SYSTEM", "CREATE", String.valueOf(userId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void menuCrudFlow() {
        String token = login("admin", "admin123");

        Map<String, Object> rootBody = new HashMap<>();
        rootBody.put("menuName", "SYS测试根" + System.currentTimeMillis());
        rootBody.put("menuType", "M");
        rootBody.put("sortOrder", 1);
        rootBody.put("visible", 1);
        ResponseEntity<Map> root = restTemplate.exchange(
                "/api/v1/system/menus", HttpMethod.POST,
                new HttpEntity<>(rootBody, authHeaders(token)), Map.class);
        assertThat(root.getBody().get("code")).isEqualTo(200);
        Long rootId = ((Number) ((Map<String, Object>) root.getBody().get("data")).get("id")).longValue();

        Map<String, Object> childBody = new HashMap<>();
        childBody.put("parentId", rootId);
        childBody.put("menuName", "SYS测试子");
        childBody.put("menuType", "C");
        childBody.put("path", "/sys/test");
        childBody.put("component", "system/Test");
        ResponseEntity<Map> child = restTemplate.exchange(
                "/api/v1/system/menus", HttpMethod.POST,
                new HttpEntity<>(childBody, authHeaders(token)), Map.class);
        assertThat(child.getBody().get("code")).isEqualTo(200);
        Long childId = ((Number) ((Map<String, Object>) child.getBody().get("data")).get("id")).longValue();

        // 树中包含父子结构
        ResponseEntity<Map> tree = restTemplate.exchange(
                "/api/v1/system/menus/tree", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(tree.getBody().get("code")).isEqualTo(200);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.getBody().get("data");
        Map<String, Object> rootNode = nodes.stream()
                .filter(n -> ((Number) n.get("id")).longValue() == rootId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("根菜单不在树中"));
        assertThat((List<Map<String, Object>>) rootNode.get("children"))
                .anyMatch(n -> ((Number) n.get("id")).longValue() == childId);

        // 父级为自身 → 400
        Map<String, Object> selfParent = new HashMap<>(childBody);
        selfParent.put("parentId", childId);
        ResponseEntity<Map> bad = restTemplate.exchange(
                "/api/v1/system/menus/" + childId, HttpMethod.PUT,
                new HttpEntity<>(selfParent, authHeaders(token)), Map.class);
        assertThat(bad.getBody().get("code")).isEqualTo(400);

        // 有子菜单时禁止删除根 → 400
        ResponseEntity<Map> deleteRoot = restTemplate.exchange(
                "/api/v1/system/menus/" + rootId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteRoot.getBody().get("code")).isEqualTo(400);

        // 先删子菜单再删根
        ResponseEntity<Map> deleteChild = restTemplate.exchange(
                "/api/v1/system/menus/" + childId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteChild.getBody().get("code")).isEqualTo(200);
        ResponseEntity<Map> deleteRoot2 = restTemplate.exchange(
                "/api/v1/system/menus/" + rootId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteRoot2.getBody().get("code")).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dictCrudFlow() {
        String token = login("admin", "admin123");

        Map<String, Object> body = new HashMap<>();
        body.put("dictType", "SYS_TEST_TYPE");
        body.put("dictCode", "A");
        body.put("dictLabel", "测试A");
        body.put("dictValue", "a");
        body.put("sortOrder", 1);
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/system/dicts", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(create.getBody().get("code")).isEqualTo(200);
        Long dictId = ((Number) ((Map<String, Object>) create.getBody().get("data")).get("id")).longValue();

        // 同类型同编码 → 400
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/system/dicts", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(duplicate.getBody().get("code")).isEqualTo(400);

        // 按类型查询
        ResponseEntity<Map> byType = restTemplate.exchange(
                "/api/v1/system/dicts/type/SYS_TEST_TYPE", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(byType.getBody().get("code")).isEqualTo(200);
        assertThat((List<Map<String, Object>>) byType.getBody().get("data"))
                .anyMatch(d -> "a".equals(d.get("dictValue")));

        // 更新
        Map<String, Object> updateBody = new HashMap<>(body);
        updateBody.put("dictLabel", "测试A-更新");
        ResponseEntity<Map> update = restTemplate.exchange(
                "/api/v1/system/dicts/" + dictId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(update.getBody().get("code")).isEqualTo(200);

        // 删除
        ResponseEntity<Map> delete = restTemplate.exchange(
                "/api/v1/system/dicts/" + dictId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(delete.getBody().get("code")).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void apiKeyCrudFlow() {
        String token = login("admin", "admin123");
        String appName = "SYS-TEST-APP-" + System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("appName", appName);
        body.put("ipWhitelist", "10.0.0.1");
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/system/api-keys", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(create.getBody().get("code")).isEqualTo(200);
        Map<String, Object> created = (Map<String, Object>) create.getBody().get("data");
        Long keyId = ((Number) created.get("id")).longValue();
        assertThat((String) created.get("apiKey")).startsWith("ak_");
        assertThat((String) created.get("apiSecret")).isNotEqualTo("******");

        // 列表返回掩码
        ResponseEntity<Map> list = restTemplate.exchange(
                "/api/v1/system/api-keys?page=1&size=10&keyword=" + appName, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(list.getBody().get("code")).isEqualTo(200);
        Map<String, Object> page = (Map<String, Object>) list.getBody().get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        assertThat(records).anyMatch(k -> "******".equals(k.get("apiSecret")));

        // 更新
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("appName", appName + "-UPD");
        updateBody.put("status", 0);
        ResponseEntity<Map> update = restTemplate.exchange(
                "/api/v1/system/api-keys/" + keyId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), Map.class);
        assertThat(update.getBody().get("code")).isEqualTo(200);

        // 删除
        ResponseEntity<Map> delete = restTemplate.exchange(
                "/api/v1/system/api-keys/" + keyId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(delete.getBody().get("code")).isEqualTo(200);
    }

    private String login(String username, String password) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(new LoginRequest(username, password), jsonHeaders()), Map.class);
        assertThat(response.getBody().get("code")).isEqualTo(200);
        return (String) ((Map<String, Object>) response.getBody().get("data")).get("tokenValue");
    }

    private void createUser(String username, String password, String roleCode) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName("系统测试用户");
        user.setStatus(1);
        sysUserMapper.insert(user);
        SysRole role = sysRoleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);
    }

    private Long roleIdByCode(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
        assertThat(role).isNotNull();
        return role.getId();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
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
