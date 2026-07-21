package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.SocialProvider
import com.youthexpedition.azit.batch.external.client.AppleAuthClient
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

@Component
class AppleAuthAdapter(
    private val appleAuthClient: AppleAuthClient,
    private val appleClientSecretGenerator: AppleClientSecretGenerator,
    @Value("\${apple.oauth.client-id}") private val clientId: String,
) : SocialAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
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

        try {
            appleAuthClient.revoke(
                clientId = clientId,
                clientSecret = appleClientSecretGenerator.generate(),
                token = refreshToken,
                tokenTypeHint = TOKEN_TYPE_HINT,
            )
            log.info("애플 연동 해제에 성공했습니다.")
        } catch (exception: HttpClientErrorException.BadRequest) {
            // 이미 해제되었거나 유효하지 않은 토큰 → 멱등성을 위해 성공으로 간주 (배치 재실행 대비)
            log.warn("애플 연동이 이미 해제된 사용자입니다. 응답: {}", exception.responseBodyAsString)
        }
    }
}
