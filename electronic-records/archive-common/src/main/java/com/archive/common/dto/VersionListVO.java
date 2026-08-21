package com.archive.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 文件版本列表：当前文件 + 历史版本。
 */
public class VersionListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private FileVO current;

    private List<FileVersionVO> history;

    public VersionListVO() {
    }

    public VersionListVO(FileVO current, List<FileVersionVO> history) {
        this.current = current;
        this.history = history;
    }

    public FileVO getCurrent() {
        return current;
    }

    public void setCurrent(FileVO current) {
        this.current = current;
    }

    public List<FileVersionVO> getHistory() {
        return history;
    }

    public void setHistory(List<FileVersionVO> history) {
        this.history = history;
    }
}
