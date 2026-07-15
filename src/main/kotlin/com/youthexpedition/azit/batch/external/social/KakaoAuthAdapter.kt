package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.SocialProvider
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@Component
class KakaoAuthAdapter(
    @Qualifier("kakaoRestClient") private val kakaoRestClient: RestClient,
    @Value("\${kakao.oauth.admin-key}") private val adminKey: String,
) : SocialAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val UNLINK_URI = "/v1/user/unlink"
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
            val formData =
                LinkedMultiValueMap<String, String>().apply {
                    add("target_id_type", TARGET_ID_TYPE)
                    add("target_id", targetId.toString())
                }
            kakaoRestClient
                .post()
                .uri(UNLINK_URI)
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_PREFIX + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .toBodilessEntity()
            log.info("카카오 연동 해제에 성공했습니다.")
        } catch (e: HttpClientErrorException.BadRequest) {
            // 이미 연동 해제된 사용자 → 멱등성을 위해 성공으로 간주 (배치 재실행 대비)
            log.warn("카카오 연동이 이미 해제된 사용자입니다. 응답: {}", e.responseBodyAsString)
        }
    }
}
