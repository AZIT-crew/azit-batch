package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.enums.SocialProvider
import com.youthexpedition.azit.batch.external.client.KakaoApiClient
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

@Component
class KakaoAuthAdapter(
    private val kakaoApiClient: KakaoApiClient,
    @Value("\${kakao.oauth.admin-key}") private val adminKey: String,
) : SocialAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TARGET_ID_TYPE = "user_id"
        private const val AUTHORIZATION_PREFIX = "KakaoAK "
    }

    override fun supports(provider: SocialProvider): Boolean = provider == SocialProvider.KAKAO

    override fun revoke(command: SocialRevokeCommand) {
        val targetId = command.socialProviderId?.toLongOrNull()
        if (targetId == null) {
            log.warn("카카오 연동 해제를 위한 provider ID가 없어 건너뜁니다.")
            return
        }

        try {
            kakaoApiClient.unlink(AUTHORIZATION_PREFIX + adminKey, TARGET_ID_TYPE, targetId)
            log.info("카카오 연동 해제에 성공했습니다.")
        } catch (exception: HttpClientErrorException.BadRequest) {
            // 이미 연동 해제된 사용자 → 멱등성을 위해 성공으로 간주 (배치 재실행 대비)
            log.warn("카카오 연동이 이미 해제된 사용자입니다. 응답: {}", exception.responseBodyAsString)
        }
    }
}
