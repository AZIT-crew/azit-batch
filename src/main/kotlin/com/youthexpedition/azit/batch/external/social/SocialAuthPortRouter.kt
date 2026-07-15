package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.SocialProvider
import org.springframework.stereotype.Component

@Component
class SocialAuthPortRouter(
    private val adapters: List<SocialAuthPort>,
) {
    fun getAdapter(provider: SocialProvider): SocialAuthPort =
        adapters.find { it.supports(provider) }
            ?: throw IllegalArgumentException("지원하지 않는 소셜 제공자: $provider")
}
