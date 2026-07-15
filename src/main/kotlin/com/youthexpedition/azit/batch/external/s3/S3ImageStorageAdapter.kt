package com.youthexpedition.azit.batch.external.s3

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client

@Component
class S3ImageStorageAdapter(
    private val s3Client: S3Client,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucket: String,
) : ImageStoragePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun delete(s3Key: String) {
        s3Client.deleteObject { it.bucket(bucket).key(s3Key) }
        log.info("S3 프로필 이미지를 삭제했습니다. key: {}", s3Key)
    }
}
