package com.youthexpedition.azit.batch.external.dto

data class SocialRevokeCommand(
    val socialProviderId: String?, // 카카오 unlink 대상 ID
    val appleRefreshToken: String?, // 애플 revoke 대상 토큰
)
