package com.archive.core.service.impl;

import com.archive.core.entity.SysFile;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 结构化文书计数工具：按文件名关键词匹配（关键词由 sys_dict 的 STRUCTURED_DOC 字典维护）。
 */
public final class StructuredDocCounter {

    private StructuredDocCounter() {
    }

    /**
     * 统计文件列表中文件名命中任意关键词的文件数。
     */
    public static long count(List<SysFile> files, List<String> keywords) {
        if (files == null || files.isEmpty() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        long count = 0;
        for (SysFile file : files) {
            if (file.getFileName() == null) {
                continue;
            }
            boolean matched = false;
            for (String keyword : keywords) {
                if (StringUtils.hasText(keyword) && file.getFileName().contains(keyword.trim())) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                count++;
            }
        }
        return count;
    }
}
