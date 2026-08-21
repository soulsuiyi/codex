package com.archive.starter.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * MinIO 健康检查：探测 /minio/health/live（对应 codeplan 8.4 健康检查）。
 */
@Component
public class MinioHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    public MinioHealthIndicator(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    minioEndpoint + "/minio/health/live", String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up().withDetail("endpoint", minioEndpoint).build();
            }
            return Health.down()
                    .withDetail("endpoint", minioEndpoint)
                    .withDetail("httpStatus", response.getStatusCode().value())
                    .build();
        } catch (Exception e) {
            return Health.down(e).withDetail("endpoint", minioEndpoint).build();
        }
    }
}
