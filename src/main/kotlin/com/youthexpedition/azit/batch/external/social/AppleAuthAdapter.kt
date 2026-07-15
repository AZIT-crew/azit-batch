package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.SocialProvider
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class AppleAuthAdapter(
    @Qualifier("appleRestClient") private val appleRestClient: RestClient,
    private val appleClientSecretGenerator: AppleClientSecretGenerator,
    @Value("\${apple.oauth.client-id}") private val clientId: String,
) : SocialAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REVOKE_URI = "/auth/revoke"
        private const val TOKEN_TYPE_HINT = "refresh_token"
    }

    override fun supports(provider: SocialProvider): Boolean = provider == SocialProvider.APPLE

    override fun revoke(command: SocialRevokeCommand) {
        val refreshToken = command.appleRefreshToken
        if (refreshToken.isNullOrBlank()) {
            // 애플 웹훅(CONSENT_REVOKED)으로 탈퇴한 경우 이미 연동이 끊겨 토큰이 없을 수 있음
            log.warn("애플 연동 해제를 위한 리프레시 토큰이 없어 건너뜁니다.")
            return
        }

        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("client_id", clientId)
                add("client_secret", appleClientSecretGenerator.generate())
                add("token", refreshToken)
                add("token_type_hint", TOKEN_TYPE_HINT)
            }
        appleRestClient
            .post()
            .uri(REVOKE_URI)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .toBodilessEntity()
        log.info("애플 연동 해제에 성공했습니다.")
    }
}
