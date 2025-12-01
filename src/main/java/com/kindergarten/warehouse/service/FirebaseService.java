package com.kindergarten.warehouse.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class FirebaseService {

    @Value("${firebase.config.path}")
    private String firebaseConfigPath;

    @Value("${firebase.storage.bucket}")
    private String storageBucket;

    @PostConstruct
    public void initialize() {
        try {
            InputStream serviceAccount;
            if (firebaseConfigPath.startsWith("classpath:")) {
                serviceAccount = new ClassPathResource(firebaseConfigPath.replace("classpath:", "")).getInputStream();
            } else {
                serviceAccount = new FileInputStream(firebaseConfigPath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setStorageBucket(storageBucket)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }

    public String uploadFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = getExtension(originalFileName);
        String fileName = UUID.randomUUID().toString() + "." + extension;

        Bucket bucket = StorageClient.getInstance().bucket();
        Blob blob = bucket.create(fileName, file.getInputStream(), file.getContentType());

        // Generate a public URL (assuming the bucket is public or using a download
        // token)
        // For Firebase Storage, a common pattern is:
        // https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<name>?alt=media

        return String.format("https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                storageBucket,
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }
        // Extract file name from URL
        // URL format:
        // https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<name>?alt=media
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1, fileUrl.indexOf("?"));
            fileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);

            Bucket bucket = StorageClient.getInstance().bucket();
            Blob blob = bucket.get(fileName);
            if (blob != null) {
                blob.delete();
            }
        } catch (Exception e) {
            // Log error but don't stop execution
            System.err.println("Error deleting file from Firebase: " + e.getMessage());
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
