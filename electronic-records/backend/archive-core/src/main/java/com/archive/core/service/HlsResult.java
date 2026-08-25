package com.archive.core.service;

import java.nio.file.Path;
import java.util.List;

/**
 * HLS 转码结果：临时工作目录、播放列表文件名与分片文件名列表（目录由调用方清理）。
 */
public record HlsResult(Path workDir, String playlistFileName, List<String> segmentFileNames) {
}
