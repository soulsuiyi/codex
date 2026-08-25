package com.archive.core.service.impl;

import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.service.OfficeConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Office 文档转 PDF 服务实现：调用 LibreOffice 无头模式转换。
 */
@Service
public class OfficeConvertServiceImpl implements OfficeConvertService {

    private static final Logger log = LoggerFactory.getLogger(OfficeConvertServiceImpl.class);
    private static final long CONVERT_TIMEOUT_SECONDS = 60;

    private static final Set<String> SUPPORTED = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final String command;

    public OfficeConvertServiceImpl(@Value("${archive.convert.command:soffice}") String command) {
        this.command = command;
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && SUPPORTED.contains(mimeType);
    }

    @Override
    public InputStream convertToPdf(String fileName, InputStream source) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("archive-office-");
            Path input = workDir.resolve("input" + extensionOf(fileName));
            try (InputStream in = source) {
                Files.copy(in, input);
            }
            Process process = new ProcessBuilder(
                    command, "--headless", "--convert-to", "pdf",
                    "--outdir", workDir.toString(), input.toString())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "文档转换超时，请稍后重试");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "文档转换失败，请检查 LibreOffice 环境");
            }
            Path output = workDir.resolve("input.pdf");
            if (!Files.exists(output)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "文档转换失败：未生成 PDF 输出");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Files.copy(output, buffer);
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (IOException e) {
            log.warn("文档转换执行失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "文档转换失败（请确认服务器已安装 LibreOffice）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文档转换被中断");
        } finally {
            deleteQuietly(workDir);
        }
    }

    private String extensionOf(String fileName) {
        if (StringUtils.hasText(fileName)) {
            int idx = fileName.lastIndexOf('.');
            if (idx >= 0 && idx < fileName.length() - 1) {
                String ext = fileName.substring(idx).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{1,10}")) {
                    return ext;
                }
            }
        }
        return ".docx";
    }

    private void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 忽略单文件清理失败
                }
            });
        } catch (IOException ignored) {
            // 忽略目录清理失败
        }
    }
}
