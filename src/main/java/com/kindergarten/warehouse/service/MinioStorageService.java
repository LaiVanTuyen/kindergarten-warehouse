package com.kindergarten.warehouse.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
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

        // Ensure bucket policy is set for public access to avatars
        setPublicAccessPolicy();
    }

    private void setPublicAccessPolicy() {
        try {
            // JSON policy to allow public read access to "avatars/*"
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
                    "                \"arn:aws:s3:::%s/profiles/*\"\n" +
                    "            ]\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}", bucketName, bucketName, bucketName, bucketName);

            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();

            s3Client.putBucketPolicy(policyRequest);

        } catch (S3Exception e) {
            // Log warning but don't fail startup if policy update fails (might already
            // exist or permission issue)
            System.err.println("Warning: Failed to set MinIO bucket policy: " + e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file, String folderName) {
        try {
            return uploadFile(file.getInputStream(), folderName, file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to get input stream from file", e);
        }
    }

    public String uploadFile(java.io.InputStream inputStream, String folderName, String originalFilename,
            String contentType) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = folderName + "/" + UUID.randomUUID().toString() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            // S3Client needs length. We might need to read all bytes if length is unknown,
            // but for classpath resources we can usually read bytes first.
            // Converting to byte array to get size. This is safe for small banner images.
            byte[] bytes = inputStream.readAllBytes();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

            // Construct Public URL
            // Format: http://<VPS_IP>:9000/<bucket>/<key>
            return String.format("%s/%s/%s", publicEndpoint, bucketName, fileName);

        } catch (IOException | S3Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            // Extract key from URL. URL is: endpoint/bucket/key
            // Key might contain slashes (e.g., avatars/abc.jpg)
            // Robust way: remove prefix "endpoint/bucket/"
            String prefix = publicEndpoint + "/" + bucketName + "/";
            if (fileUrl.startsWith(prefix)) {
                String key = fileUrl.substring(prefix.length());
                s3Client.deleteObject(b -> b.bucket(bucketName).key(key));
            } else {
                // Fallback or ignore if URL format doesn't match
                // Maybe it's already a key?
            }
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
