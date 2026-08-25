package com.archive.starter;

import com.archive.core.service.impl.OfficeConvertServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Office 转换服务 MIME 支持判定测试（无需 LibreOffice/Spring 上下文）。
 */
class OfficeConvertSupportTest {

    private final OfficeConvertServiceImpl service = new OfficeConvertServiceImpl("soffice");

    @Test
    void supportsOfficeTypes() {
        assertThat(service.supports("application/msword")).isTrue();
        assertThat(service.supports(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
        assertThat(service.supports("application/vnd.ms-excel")).isTrue();
        assertThat(service.supports(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
        assertThat(service.supports("application/vnd.ms-powerpoint")).isTrue();
        assertThat(service.supports(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isTrue();
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThat(service.supports("application/pdf")).isFalse();
        assertThat(service.supports("image/png")).isFalse();
        assertThat(service.supports("video/mp4")).isFalse();
        assertThat(service.supports(null)).isFalse();
    }
}
