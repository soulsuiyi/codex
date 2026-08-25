package com.archive.starter;

import com.archive.job.backup.H2BackupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 数据备份测试（每日备份生成 + 过期备份清理）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class H2BackupJobTest {

    @Autowired
    private H2BackupJob h2BackupJob;

    @Value("${archive.backup.dir}")
    private String backupDir;

    @Test
    void createsAndPrunesBackups() throws Exception {
        Path dir = Paths.get(backupDir);
        Files.createDirectories(dir);

        h2BackupJob.backup();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> backups = stream
                    .filter(p -> p.getFileName().toString().matches("archive-\\d{8}\\.(mv\\.db|sql)"))
                    .toList();
            assertThat(backups).isNotEmpty();
            assertThat(Files.size(backups.get(0))).isGreaterThan(0);
        }

        Path stale = dir.resolve("archive-20200101.mv.db");
        Files.write(stale, new byte[]{1});
        Files.setLastModifiedTime(stale,
                FileTime.fromMillis(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000));
        try {
            h2BackupJob.cleanupOldBackups();
            assertThat(Files.exists(stale)).isFalse();
        } finally {
            Files.deleteIfExists(stale);
        }
    }
}
