package com.archive.job.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 全文索引健康检查：校验 sys_file 的 H2 FULLTEXT 索引，缺失时幂等重建（AGENTS.md 3.3）。
 */
@Component
public class IndexHealthJob {

    private static final Logger log = LoggerFactory.getLogger(IndexHealthJob.class);

    private final JdbcTemplate jdbcTemplate;

    public IndexHealthJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void checkIndexHealth() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM FT.INDEXES WHERE \"TABLE\" = 'SYS_FILE'", Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("CALL FT_CREATE_INDEX('PUBLIC', 'SYS_FILE', 'FILE_NAME,CONTENT')");
                log.warn("全文索引缺失，已重建: sys_file(file_name, content)");
            } else {
                log.info("全文索引健康检查通过: 索引数 {}", count);
            }
        } catch (Exception e) {
            log.warn("全文索引健康检查失败: {}", e.getMessage());
        }
    }
}
