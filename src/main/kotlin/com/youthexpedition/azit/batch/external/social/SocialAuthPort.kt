package com.youthexpedition.azit.batch.external.social

import com.youthexpedition.azit.batch.domain.enums.SocialProvider
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand

interface SocialAuthPort {
    fun supports(provider: SocialProvider): Boolean

    /**
     * 소셜 연동 해제. 이미 해제된 경우도 성공으로 간주하여 멱등성을 보장한다.
     */
    fun revoke(command: SocialRevokeCommand)
}
