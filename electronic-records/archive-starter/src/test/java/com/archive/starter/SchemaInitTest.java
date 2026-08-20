package com.archive.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 H2 初始化脚本创建了全部 15 张表。
 */
@SpringBootTest
class SchemaInitTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allFifteenTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);
        List<String> expected = Arrays.asList(
                "SYS_USER", "SYS_ROLE", "SYS_USER_ROLE", "SYS_MENU", "SYS_CASE",
                "SYS_CASE_CATEGORY", "SYS_FILE", "SYS_FILE_VERSION", "SYS_ARCHIVE",
                "SYS_BORROW_APPLY", "SYS_BORROW_APPROVAL", "SYS_BORROW_TOKEN",
                "SYS_AUDIT_LOG", "SYS_DICT", "SYS_API_KEY");
        assertThat(tables).containsAll(expected);
    }
}
