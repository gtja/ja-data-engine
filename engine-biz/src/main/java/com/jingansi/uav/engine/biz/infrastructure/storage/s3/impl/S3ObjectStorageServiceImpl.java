package com.jingansi.uav.engine.biz.infrastructure.storage.s3.impl;

import com.jingansi.uav.engine.biz.infrastructure.storage.s3.S3ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * S3 对象存储服务。
 */
@Service
@RequiredArgsConstructor
public class S3ObjectStorageServiceImpl implements S3ObjectStorageService {

    private static final int BUFFER_SIZE = 8192;

    private final S3Client s3Client;

    @Override
    public void upload(String bucketName, String objectKey, File file) {
        ensureBucketExists(bucketName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build();
        s3Client.putObject(request, RequestBody.fromFile(file));
    }

    @Override
    public void download(String bucketName, String objectKey, OutputStream outputStream) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(request)) {
            inputStream.transferTo(outputStream);
        }
    }

    private void ensureBucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }
}
