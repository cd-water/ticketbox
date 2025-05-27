package com.cdwater.ticketbox.storage;

import com.cdwater.ticketbox.common.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class StorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final MinioClient client;
    private final String bucket;

    public StorageService(MinioClient client, @Value("${app.minio.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucket() {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                return;
            } catch (Exception exception) {
                if (attempt == 29) {
                    throw new IllegalStateException("Cannot prepare MinIO bucket " + bucket, exception);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while preparing MinIO bucket " + bucket, interrupted);
                }
            }
        }
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "An image file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Images must be at most 5 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Cannot read uploaded file");
        }

        String contentType = detectContentType(content);
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                    "Only JPEG, PNG or WebP images are allowed");
        }

        String objectKey = "events/" + UUID.randomUUID() + extension;
        try (InputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Cannot read uploaded file");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Cannot store uploaded file");
        }
        return objectKey;
    }

    private static String detectContentType(byte[] content) {
        if (content.length >= 3
                && content[0] == (byte) 0xFF && content[1] == (byte) 0xD8 && content[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (content.length >= 8
                && content[0] == (byte) 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && content[4] == 0x0D && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A) {
            return "image/png";
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public String presignedUrl(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Cannot sign object URL");
        }
    }
}
