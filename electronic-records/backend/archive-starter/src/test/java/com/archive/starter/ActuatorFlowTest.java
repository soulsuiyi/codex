package com.archive.starter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监控告警基础验证：Actuator 健康检查、Prometheus 指标端点与应用信息。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(minioHealthy(), "MinIO 不可达，跳过健康检查断言");
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        Map<String, Object> minio = (Map<String, Object>) components.get("minio");
        assertThat(minio.get("status")).isEqualTo("UP");
    }

    @Test
    void prometheusEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("jvm_");
    }

    @Test
    void infoEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/info", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> app = (Map<String, Object>) response.getBody().get("app");
        assertThat(app.get("name")).isEqualTo("archive-system");
        assertThat(app.get("version")).isEqualTo("0.1.0-SNAPSHOT");
    }

    private boolean minioHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://127.0.0.1:9000/minio/health/live", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
