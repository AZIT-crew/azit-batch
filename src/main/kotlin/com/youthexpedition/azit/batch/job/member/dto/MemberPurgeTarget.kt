package com.youthexpedition.azit.batch.job.member.dto

import com.youthexpedition.azit.batch.domain.enums.SocialProvider

data class MemberPurgeTarget(
    val memberId: Long,
    val socialAccounts: List<SocialAccountPurgeTarget>, // 연동된 소셜 계정 전체 (revoke 대상). 이미 파기된 회원은 빈 목록
    val profileImageS3Key: String?, // 자체 업로드 이미지일 때만 non-null (삭제 대상)
)

data class SocialAccountPurgeTarget(
    val socialProvider: SocialProvider,
    val socialProviderId: String?,
    val appleRefreshToken: String?,
)
