package com.youthexpedition.azit.batch.external.client

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.PostExchange

interface KakaoApiClient {
    /**
     * 카카오 연동 해제 (어드민 키 방식).
     * contentType이 form-urlencoded이므로 @RequestParam 값들은 요청 본문(form data)으로 전송된다.
     */
    @PostExchange(url = "/v1/user/unlink", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun unlink(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String, // "KakaoAK {adminKey}"
        @RequestParam("target_id_type") targetIdType: String, // "user_id"
        @RequestParam("target_id") targetId: Long,
    )
}
