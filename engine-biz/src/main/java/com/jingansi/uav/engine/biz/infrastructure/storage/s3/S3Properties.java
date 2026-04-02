package com.jingansi.uav.engine.biz.infrastructure.storage.s3;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 配置。
 */
@Data
@ConfigurationProperties(prefix = "s3")
public class S3Properties {

    private String region;

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName;

    private String prefix;

    private String externalPrefix;

    private String attachmentBucketName;

    private String entityImageBucketName;
}
