package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService {

    /** Hard cap khi streamable size không xác định để tránh OOM. */
    private static final long UNKNOWN_SIZE_BUFFER_MAX_BYTES = 50L * 1024L * 1024L;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    @PostConstruct
    public void init() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                try {
                    s3Client.createBucket(b -> b.bucket(bucketName));
                } catch (S3Exception createEx) {
                    throw new AppException(ErrorCode.STORAGE_ERROR, createEx);
                }
            } else {
                throw new AppException(ErrorCode.STORAGE_ERROR, e);
            }
        }

        setPublicAccessPolicy();
    }

    /**
     * Public-read chỉ cho asset hiển thị (avatar, banner, icon, thumbnail).
     * Resource files (resources/files/*) là PRIVATE — chỉ truy cập qua endpoint
     * có kiểm tra quyền của application.
     */
    private void setPublicAccessPolicy() {
        try {
            String policy = String.format("{\n" +
                    "    \"Version\": \"2012-10-17\",\n" +
                    "    \"Statement\": [\n" +
                    "        {\n" +
                    "            \"Effect\": \"Allow\",\n" +
                    "            \"Principal\": {\n" +
                    "                \"AWS\": [\n" +
                    "                    \"*\"\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            \"Action\": [\n" +
                    "                \"s3:GetObject\"\n" +
                    "            ],\n" +
                    "            \"Resource\": [\n" +
                    "                \"arn:aws:s3:::%s/avatars/*\",\n" +
                    "                \"arn:aws:s3:::%s/banners/*\",\n" +
                    "                \"arn:aws:s3:::%s/icons/*\",\n" +
                    "                \"arn:aws:s3:::%s/profiles/*\",\n" +
                    "                \"arn:aws:s3:::%s/categories/*\",\n" +
                    "                \"arn:aws:s3:::%s/resources/thumbnails/*\"\n" +
                    "            ]\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}", bucketName, bucketName, bucketName, bucketName, bucketName, bucketName);

            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();

            s3Client.putBucketPolicy(policyRequest);

        } catch (S3Exception e) {
            log.warn("Failed to set MinIO bucket policy: {}", e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file, String folderName) {
        try {
            return uploadFile(file.getInputStream(), folderName, file.getOriginalFilename(),
                    file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    public String uploadFile(InputStream inputStream, String folderName, String originalFilename,
            String contentType, long contentLength) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String key = folderName + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            RequestBody body;
            if (contentLength > 0) {
                body = RequestBody.fromInputStream(inputStream, contentLength);
            } else {
                body = bufferToRequestBody(inputStream);
            }

            s3Client.putObject(putObjectRequest, body);

            return String.format("%s/%s/%s", publicEndpoint, bucketName, key);

        } catch (S3Exception e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    /**
     * Khi size không xác định, đọc tối đa {@value #UNKNOWN_SIZE_BUFFER_MAX_BYTES}
     * bytes vào memory. Vượt → STORAGE_ERROR để không sập JVM.
     */
    private RequestBody bufferToRequestBody(InputStream inputStream) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            long total = 0;
            int n;
            while ((n = inputStream.read(chunk)) != -1) {
                total += n;
                if (total > UNKNOWN_SIZE_BUFFER_MAX_BYTES) {
                    throw new AppException(ErrorCode.STORAGE_ERROR);
                }
                buffer.write(chunk, 0, n);
            }
            byte[] bytes = buffer.toByteArray();
            return RequestBody.fromInputStream(new ByteArrayInputStream(bytes), bytes.length);
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            String prefix = publicEndpoint + "/" + bucketName + "/";
            if (fileUrl.startsWith(prefix)) {
                String key = fileUrl.substring(prefix.length());
                s3Client.deleteObject(b -> b.bucket(bucketName).key(key));
            }
        } catch (S3Exception e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    public String getPresignedUrl(String objectKey) {
        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest
                    .builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner
                    .presignGetObject(presignRequest);

            return presignedRequest.url().toString();
        } catch (Exception e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    public InputStream getObject(String fileUrl) throws Exception {
        try {
            String prefix = publicEndpoint + "/" + bucketName + "/";
            String key;
            if (fileUrl.startsWith(prefix)) {
                key = fileUrl.substring(prefix.length());
            } else if (fileUrl.startsWith("http")) {
                key = fileUrl.substring(fileUrl.lastIndexOf(bucketName) + bucketName.length() + 1);
            } else {
                key = fileUrl;
            }

            return s3Client.getObject(b -> b.bucket(bucketName).key(key));
        } catch (S3Exception e) {
            log.error("Failed to get file from MinIO (key derived from {}): {}", fileUrl, e.getMessage());
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }
}
