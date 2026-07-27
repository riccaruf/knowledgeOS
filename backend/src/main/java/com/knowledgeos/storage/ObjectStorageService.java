package com.knowledgeos.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Storage dei binari documento su MinIO (zona "raw" del data lake documentale,
 * 02_SOLUTION_ARCHITECTURE.md §3.7). I metadati restano sempre in PostgreSQL.
 */
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final MinioClient minioClient;

    public void put(String bucket, String objectKey, InputStream content, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Impossibile salvare il file su object storage.", e);
        }
    }

    public InputStream get(String bucket, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Impossibile leggere il file da object storage.", e);
        }
    }

    public void delete(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Impossibile eliminare il file da object storage.", e);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
