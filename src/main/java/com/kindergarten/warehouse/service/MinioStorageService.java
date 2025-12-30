package com.kindergarten.warehouse.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Service
public class MinioStorageService {

    private final S3Client s3Client;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    public MinioStorageService(S3Client s3Client,
            software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostConstruct
    public void init() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                try {
                    s3Client.createBucket(b -> b.bucket(bucketName));
                } catch (S3Exception createEx) {
                    throw new RuntimeException("Failed to create MinIO bucket: " + bucketName, createEx);
                }
            } else {
                throw new RuntimeException("Failed to connect to MinIO bucket: " + bucketName, e);
            }
        }
    }

    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Construct Public URL
            // Format: http://<VPS_IP>:9000/<bucket>/<filename>
            return String.format("%s/%s/%s", publicEndpoint, bucketName, fileName);

        } catch (IOException | S3Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            // Extract filename from URL
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            s3Client.deleteObject(b -> b.bucket(bucketName).key(fileName));
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to delete file from MinIO", e);
        }
    }

    public String getPresignedUrl(String objectKey) {
        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest
                    .builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
                    .builder()
                    .signatureDuration(java.time.Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presignedRequest = s3Presigner
                    .presignGetObject(presignRequest);

            return presignedRequest.url().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
