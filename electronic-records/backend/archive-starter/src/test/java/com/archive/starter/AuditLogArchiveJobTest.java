package com.archive.starter;

import com.archive.auth.entity.SysAuditLog;
import com.archive.auth.mapper.SysAuditLogMapper;
import com.archive.core.service.AuditArchiveService;
import com.archive.core.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计日志冷存储归档测试（MinIO 就绪时执行）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditLogArchiveJobTest {

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private AuditArchiveService auditArchiveService;

    @Autowired
    private StorageService storageService;

    @Value("${minio.buckets.cold}")
    private String coldBucket;

    @Test
    void archivesExpiredAuditLogs() {
        SysAuditLog old = new SysAuditLog();
        old.setUsername("archive_old_user");
        old.setModule("SYSTEM");
        old.setAction("CREATE");
        old.setIp("127.0.0.1");
        old.setResult("SUCCESS");
        old.setCreatedAt(LocalDateTime.now().minusDays(366));
        sysAuditLogMapper.insert(old);

        SysAuditLog fresh = new SysAuditLog();
        fresh.setUsername("archive_fresh_user");
        fresh.setModule("SYSTEM");
        fresh.setAction("CREATE");
        fresh.setIp("127.0.0.1");
        fresh.setResult("SUCCESS");
        fresh.setCreatedAt(LocalDateTime.now());
        sysAuditLogMapper.insert(fresh);

        try {
            int archived = auditArchiveService.archiveExpiredAuditLogs();
            assertThat(archived).isEqualTo(1);
            assertThat(sysAuditLogMapper.selectById(old.getId())).isNull();
            assertThat(sysAuditLogMapper.selectById(fresh.getId())).isNotNull();

            List<String> objects = storageService.listObjectNames(coldBucket, "audit/");
            assertThat(objects).isNotEmpty();
        } finally {
            sysAuditLogMapper.deleteById(old.getId());
            sysAuditLogMapper.deleteById(fresh.getId());
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            for (String object : storageService.listObjectNames(coldBucket, "audit/")) {
                if (object.contains(today)) {
                    storageService.removeObject(coldBucket, object);
                }
            }
        }
    }
}
