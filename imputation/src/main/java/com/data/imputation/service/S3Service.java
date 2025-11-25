package com.data.imputation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;
    private final String keyPrefix;

    public S3Service(
            S3Client s3Client,
            @Value("${app.s3.bucket-name}") String bucketName,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.key-prefix:}") String keyPrefix
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix.trim();
    }

    /**
     * Uploads the given file to S3 and returns a public-style HTTPS URL.
     *
     * @param filePath local file path
     * @return S3 object URL
     */
    public String uploadFile(Path filePath) throws Exception {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        String fileName = filePath.getFileName().toString();
        String key = buildKey(fileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("text/csv")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));

        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("+", "%20"); // spaces as %20

        // basic virtual-hosted S3 URL format
        String base = "https://" + bucketName + ".s3." + region + ".amazonaws.com/";
        return base + encodedKey;
    }

    private String buildKey(String fileName) {
        if (keyPrefix.isEmpty()) {
            return fileName;
        }
        String prefix = keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";
        return prefix + fileName;
    }
}
