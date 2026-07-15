package com.youthexpedition.azit.batch.external.s3

interface ImageStoragePort {
    /**
     * S3 객체 삭제. 존재하지 않는 키를 삭제해도 에러가 발생하지 않는다(멱등).
     */
    fun delete(s3Key: String)
}
