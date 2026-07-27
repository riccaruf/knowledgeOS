package com.knowledgeos.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledgeos.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey) {
}
