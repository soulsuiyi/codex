package com.archive.common.dto;

import java.io.Serializable;

/**
 * 全文检索结果：文件 + 命中片段（含 &lt;em&gt; 高亮）。
 */
public class SearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private FileVO file;

    private String snippet;

    public SearchResultVO() {
    }

    public SearchResultVO(FileVO file, String snippet) {
        this.file = file;
        this.snippet = snippet;
    }

    public FileVO getFile() {
        return file;
    }

    public void setFile(FileVO file) {
        this.file = file;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
