package com.archive.job.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * H2 数据备份：每日 05:00 复制数据库文件，保留 30 天。
 */
@Component
public class H2BackupJob {

    private static final Logger log = LoggerFactory.getLogger(H2BackupJob.class);
    private static final String JDBC_FILE_PREFIX = "jdbc:h2:file:";

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${archive.backup.dir:./data/backup/h2}")
    private String backupDir;

    @Value("${archive.backup.retention-days:30}")
    private int retentionDays;

    public H2BackupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 5 * * *")
    public void run() {
        try {
            backup();
            cleanupOldBackups();
        } catch (Exception e) {
            log.warn("H2 数据备份执行失败: {}", e.getMessage());
        }
    }

    public void backup() {
        String path = resolveH2FilePath();
        if (path == null) {
            log.warn("非文件模式 H2，跳过备份: {}", datasourceUrl);
            return;
        }
        try {
            jdbcTemplate.execute("CHECKPOINT");
            Path dir = Paths.get(backupDir);
            Files.createDirectories(dir);
            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            Path dbFile = Paths.get(path + ".mv.db");
            boolean copied = false;
            if (Files.exists(dbFile)) {
                try {
                    Files.copy(dbFile, dir.resolve("archive-" + date + ".mv.db"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("H2 数据备份完成（文件复制）: {}", dir.resolve("archive-" + date + ".mv.db").toAbsolutePath());
                    copied = true;
                } catch (IOException e) {
                    log.warn("H2 文件复制失败（可能被占用），改用 SCRIPT 导出: {}", e.getMessage());
                }
            }
            if (!copied) {
                Path sqlFile = dir.resolve("archive-" + date + ".sql");
                String sqlPath = sqlFile.toAbsolutePath().toString().replace('\\', '/');
                jdbcTemplate.execute("SCRIPT TO '" + sqlPath + "'");
                log.info("H2 数据备份完成（SCRIPT 导出）: {}", sqlFile.toAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("H2 数据备份执行失败: {}", e.getMessage());
        }
    }

    public void cleanupOldBackups() {
        Path dir = Paths.get(backupDir);
        if (!Files.isDirectory(dir)) {
            return;
        }
        LocalDate deadline = LocalDate.now().minusDays(retentionDays);
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> stale = stream
                    .filter(p -> p.getFileName().toString().matches("archive-\\d{8}\\.(mv\\.db|sql)"))
                    .filter(p -> {
                        String date = p.getFileName().toString().substring(8, 16);
                        return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE).isBefore(deadline);
                    })
                    .toList();
            for (Path file : stale) {
                Files.deleteIfExists(file);
                log.info("已清理过期 H2 备份: {}", file.getFileName());
            }
        } catch (IOException e) {
            log.warn("H2 备份清理失败: {}", e.getMessage());
        }
    }

    private String resolveH2FilePath() {
        if (!StringUtils.hasText(datasourceUrl) || !datasourceUrl.startsWith(JDBC_FILE_PREFIX)) {
            return null;
        }
        String path = datasourceUrl.substring(JDBC_FILE_PREFIX.length());
        int queryIdx = path.indexOf(';');
        return queryIdx > 0 ? path.substring(0, queryIdx) : path;
    }
}
