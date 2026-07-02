package com.wallaceespindola.reportcomposer.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioArtifactStorageTest {

    @Mock private MinioClient minioClient;

    private MinioArtifactStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioArtifactStorage(minioClient, TestFixtures.appProperties("worker", "local"));
    }

    @Test
    void putCreatesBucketWhenMissingAndUploads() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storage.put("BE/key.txt", "body".getBytes(), "text/plain");

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void putWrapsClientErrors() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> storage.put("k", new byte[0], "text/plain"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void getWrapsClientErrors() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> storage.get("k")).isInstanceOf(StorageException.class);
    }
}
