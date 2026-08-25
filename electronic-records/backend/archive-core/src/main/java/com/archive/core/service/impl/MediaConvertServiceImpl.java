package com.archive.core.service.impl;

import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.service.HlsResult;
import com.archive.core.service.MediaConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 音视频转码服务实现：FFmpeg 无头转 HLS（libx264/aac，10 秒切片）。
 */
@Service
public class MediaConvertServiceImpl implements MediaConvertService {

    private static final Logger log = LoggerFactory.getLogger(MediaConvertServiceImpl.class);
    private static final long TRANSCODE_TIMEOUT_SECONDS = 300;

    private static final Set<String> SUPPORTED = Set.of(
            "video/mp4",
            "video/x-msvideo",
            "video/quicktime",
            "audio/mpeg",
            "audio/wav",
            "audio/x-wav",
            "audio/mp4",
            "audio/x-m4a");

    private final String command;

    public MediaConvertServiceImpl(@Value("${archive.media.ffmpeg-command:ffmpeg}") String command) {
        this.command = command;
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && SUPPORTED.contains(mimeType);
    }

    @Override
    public HlsResult convertToHls(String fileName, InputStream source) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("archive-hls-");
            Path input = workDir.resolve("input" + extensionOf(fileName));
            try (InputStream in = source) {
                Files.copy(in, input);
            }
            Process process = new ProcessBuilder(
                    command, "-y", "-i", input.toString(),
                    "-c:v", "libx264", "-preset", "veryfast",
                    "-c:a", "aac",
                    "-hls_time", "10", "-hls_list_size", "0",
                    "-hls_segment_filename", workDir.resolve("seg_%03d.ts").toString(),
                    workDir.resolve("playlist.m3u8").toString())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "音视频转码超时，请稍后重试");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "音视频转码失败，请检查 FFmpeg 环境");
            }
            Path playlist = workDir.resolve("playlist.m3u8");
            if (!Files.exists(playlist)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "音视频转码失败：未生成 HLS 播放列表");
            }
            List<String> segmentNames = new ArrayList<>();
            try (var stream = Files.list(workDir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("seg_")
                                && p.getFileName().toString().endsWith(".ts"))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(segmentNames::add);
            }
            return new HlsResult(workDir, "playlist.m3u8", segmentNames);
        } catch (IOException e) {
            deleteQuietly(workDir);
            log.warn("音视频转码执行失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "音视频转码失败（请确认服务器已安装 FFmpeg）");
        } catch (InterruptedException e) {
            deleteQuietly(workDir);
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "音视频转码被中断");
        } catch (BusinessException e) {
            deleteQuietly(workDir);
            throw e;
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
        return ".mp4";
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
