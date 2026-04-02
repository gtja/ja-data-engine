package com.jingansi.uav.engine.biz.infrastructure.storage.s3;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public interface S3ObjectStorageService {

    void upload(String bucketName, String objectKey, File file);

    void download(String bucketName, String objectKey, OutputStream outputStream) throws IOException;
}
