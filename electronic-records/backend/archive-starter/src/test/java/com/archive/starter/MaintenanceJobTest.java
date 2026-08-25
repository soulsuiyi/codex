package com.archive.starter;

import com.archive.core.service.FileService;
import com.archive.job.index.IndexHealthJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维任务测试：分片残留清理与全文索引健康检查（不依赖 MinIO）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceJobTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private IndexHealthJob indexHealthJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cleanStaleChunksRemovesOnlyStaleDirs() throws Exception {
        Path root = Paths.get(System.getProperty("java.io.tmpdir"), "archive-chunks");
        Files.createDirectories(root);
        Path stale = root.resolve("stale-" + System.currentTimeMillis());
        Path fresh = root.resolve("fresh-" + System.currentTimeMillis());
        Files.createDirectory(stale);
        Files.createDirectory(fresh);
        Files.setLastModifiedTime(stale,
                FileTime.fromMillis(System.currentTimeMillis() - 48L * 60 * 60 * 1000));
        Files.setLastModifiedTime(fresh, FileTime.fromMillis(System.currentTimeMillis()));

        try {
            int cleaned = fileService.cleanStaleChunks(24L * 60 * 60 * 1000);
            assertThat(cleaned).isGreaterThanOrEqualTo(1);
            assertThat(Files.exists(stale)).isFalse();
            assertThat(Files.exists(fresh)).isTrue();
        } finally {
            Files.deleteIfExists(stale);
            Files.deleteIfExists(fresh);
        }
    }

    @Test
    void indexHealthCheckPasses() {
        indexHealthJob.checkIndexHealth();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM FT.INDEXES WHERE \"TABLE\" = 'SYS_FILE'", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
