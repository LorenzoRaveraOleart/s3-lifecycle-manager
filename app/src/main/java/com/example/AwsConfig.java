// src/main/java/com/example/AwsConfig.java
package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class AwsConfig {
    @Bean
    S3Client s3Client(
            @Value("${AWS_REGION:ap-southeast-2}") String region,
            @Value("${AWS_ENDPOINT:}") String endpoint) {

        S3Configuration s3cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(true)     // <-- IMPORTANT for LocalStack
                .build();

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(s3cfg)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                System.getenv().getOrDefault("AWS_ACCESS_KEY_ID","test"),
                                System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY","test")
                        )
                ));

        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
