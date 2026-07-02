package com.wallaceespindola.reportcomposer.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(AppProperties props) {
        return MinioClient.builder()
                .endpoint(props.minio().endpoint())
                .credentials(props.minio().accessKey(), props.minio().secretKey())
                .build();
    }
}
