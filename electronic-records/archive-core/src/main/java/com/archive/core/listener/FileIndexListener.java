package com.archive.core.listener;

import com.archive.core.entity.SysFile;
import com.archive.core.event.FileUploadedEvent;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.service.StorageService;
import com.archive.search.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 文件上传事件监听：异步提取文本并写回 sys_file.content（H2 全文检索索引自动维护）。
 */
@Component
public class FileIndexListener {

    private static final Logger log = LoggerFactory.getLogger(FileIndexListener.class);
    private static final int CONTENT_MAX_LENGTH = 100_000;

    private final SysFileMapper sysFileMapper;
    private final StorageService storageService;
    private final TextExtractionService textExtractionService;

    public FileIndexListener(SysFileMapper sysFileMapper,
                             StorageService storageService,
                             TextExtractionService textExtractionService) {
        this.sysFileMapper = sysFileMapper;
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
    }

    @Async
    @EventListener
    public void onFileUploaded(FileUploadedEvent event) {
        try {
            SysFile file = sysFileMapper.selectById(event.getFileId());
            if (file == null) {
                return;
            }
            try (InputStream in = storageService.getObject(file.getStorageBucket(), file.getStoragePath())) {
                String text = textExtractionService.extractText(in, file.getMimeType(), file.getFileName());
                if (StringUtils.hasText(text)) {
                    SysFile update = new SysFile();
                    update.setId(file.getId());
                    update.setContent(text.length() > CONTENT_MAX_LENGTH
                            ? text.substring(0, CONTENT_MAX_LENGTH) : text);
                    sysFileMapper.updateById(update);
                }
            }
        } catch (Exception e) {
            log.warn("文件文本提取失败: fileId={}", event.getFileId(), e);
        }
    }
}
