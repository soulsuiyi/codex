package com.archive.starter;

import com.archive.core.entity.SysFile;
import com.archive.core.service.impl.StructuredDocCounter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化文书计数工具测试（无需 Spring 上下文）。
 */
class StructuredDocCounterTest {

    @Test
    void countsFilesMatchingKeywords() {
        List<SysFile> files = List.of(
                file("询问笔录.pdf"),
                file("起诉书.docx"),
                file("证据材料扫描件.jpg"),
                file("合同.pdf"),
                file("裁决书.pdf"));
        assertThat(StructuredDocCounter.count(files, List.of("笔录", "起诉书", "裁决书"))).isEqualTo(3);
    }

    @Test
    void handlesEmptyInputs() {
        assertThat(StructuredDocCounter.count(null, List.of("笔录"))).isZero();
        assertThat(StructuredDocCounter.count(List.of(file("笔录.pdf")), null)).isZero();
        assertThat(StructuredDocCounter.count(List.of(), List.of("笔录"))).isZero();
    }

    @Test
    void ignoresNullOrBlankKeywords() {
        assertThat(StructuredDocCounter.count(
                List.of(file("询问笔录.pdf"), file("合同.pdf")),
                Arrays.asList("  ", null, "笔录"))).isEqualTo(1);
    }

    private SysFile file(String name) {
        SysFile file = new SysFile();
        file.setFileName(name);
        return file;
    }
}
