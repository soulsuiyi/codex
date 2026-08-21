package com.archive.search.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 H2 全文索引存在（sys_file(file_name, content)），幂等创建。
 */
@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SearchIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM FT.INDEXES WHERE \"TABLE\" = 'SYS_FILE'", Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("CALL FT_CREATE_INDEX('PUBLIC', 'SYS_FILE', 'FILE_NAME,CONTENT')");
                log.info("已创建 H2 全文索引: sys_file(file_name, content)");
            }
        } catch (Exception e) {
            log.warn("H2 全文索引初始化失败: {}", e.getMessage());
        }
    }
}
