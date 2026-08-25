package com.archive.core.service.impl;

import com.archive.auth.entity.SysAuditLog;
import com.archive.auth.mapper.SysAuditLogMapper;
import com.archive.common.dto.AuditRetentionVO;
import com.archive.core.service.AuditArchiveService;
import com.archive.core.service.StorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计日志归档服务实现：分批导出 JSON 至 MinIO 冷桶，上传成功后物理删除 H2 记录。
 */
@Service
public class AuditArchiveServiceImpl implements AuditArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveServiceImpl.class);
    private static final int BATCH_SIZE = 500;
    private static final Pattern ARCHIVE_DATE = Pattern.compile("audit-(\\d{8})-");

    private final SysAuditLogMapper auditLogMapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Value("${minio.buckets.cold}")
    private String coldBucket;

    @Value("${archive.audit.retention-days:365}")
    private int retentionDays;

    public AuditArchiveServiceImpl(SysAuditLogMapper auditLogMapper,
                                   StorageService storageService,
                                   ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public int archiveExpiredAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int batch = 0;
        int archived = 0;
        while (true) {
            List<SysAuditLog> records = auditLogMapper.selectList(
                    Wrappers.<SysAuditLog>lambdaQuery()
                            .lt(SysAuditLog::getCreatedAt, cutoff)
                            .orderByAsc(SysAuditLog::getId)
                            .last("LIMIT " + BATCH_SIZE));
            if (records.isEmpty()) {
                break;
            }
            batch++;
            try {
                byte[] json = objectMapper.writeValueAsBytes(records);
                String objectName = "audit/" + cutoff.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                        + "/audit-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + batch + ".json";
                storageService.putFile(coldBucket, objectName,
                        new ByteArrayInputStream(json), json.length, "application/json");
                auditLogMapper.deleteBatchIds(records.stream().map(SysAuditLog::getId).toList());
                archived += records.size();
            } catch (Exception e) {
                log.warn("审计日志归档批次失败，终止本轮: {}", e.getMessage());
                break;
            }
        }
        if (archived > 0) {
            log.info("审计日志归档完成: {} 条", archived);
        }
        return archived;
    }

    @Override
    public AuditRetentionVO retentionInfo() {
        AuditRetentionVO vo = new AuditRetentionVO();
        vo.setRetentionDays(retentionDays);
        List<String> objects = storageService.listObjectNames(coldBucket, "audit/");
        vo.setArchivedFiles(objects.size());
        LocalDate latest = null;
        for (String object : objects) {
            Matcher matcher = ARCHIVE_DATE.matcher(object);
            if (matcher.find()) {
                LocalDate date = LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                if (latest == null || date.isAfter(latest)) {
                    latest = date;
                }
            }
        }
        vo.setLastArchivedAt(latest);
        return vo;
    }
}
