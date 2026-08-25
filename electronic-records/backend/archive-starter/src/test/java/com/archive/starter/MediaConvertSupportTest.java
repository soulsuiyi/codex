package com.archive.starter;

import com.archive.core.service.impl.MediaConvertServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 媒体转换服务 MIME 支持判定测试（无需 FFmpeg/Spring 上下文）。
 */
class MediaConvertSupportTest {

    private final MediaConvertServiceImpl service = new MediaConvertServiceImpl("ffmpeg");

    @Test
    void supportsMediaTypes() {
        assertThat(service.supports("video/mp4")).isTrue();
        assertThat(service.supports("video/x-msvideo")).isTrue();
        assertThat(service.supports("video/quicktime")).isTrue();
        assertThat(service.supports("audio/mpeg")).isTrue();
        assertThat(service.supports("audio/wav")).isTrue();
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThat(service.supports("application/pdf")).isFalse();
        assertThat(service.supports("application/msword")).isFalse();
        assertThat(service.supports("image/png")).isFalse();
        assertThat(service.supports(null)).isFalse();
    }
}
