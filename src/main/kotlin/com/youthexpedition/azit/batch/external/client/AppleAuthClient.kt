package com.youthexpedition.azit.batch.external.client

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.PostExchange

interface AppleAuthClient {
    /**
     * 애플 연동 해제 (리프레시 토큰 revoke).
     * contentType이 form-urlencoded이므로 @RequestParam 값들은 요청 본문(form data)으로 전송된다.
     */
    @PostExchange(url = "/auth/revoke", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun revoke(
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("token") token: String,
        @RequestParam("token_type_hint") tokenTypeHint: String, // "refresh_token"
    )
}
