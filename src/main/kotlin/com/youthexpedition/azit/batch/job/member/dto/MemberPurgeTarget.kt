package com.youthexpedition.azit.batch.job.member.dto

import com.youthexpedition.azit.batch.domain.SocialProvider

data class MemberPurgeTarget(
    val memberId: Long,
    val socialProvider: SocialProvider,
    val socialProviderId: String?,
    val appleRefreshToken: String?,
    val profileImageS3Key: String?, // 자체 업로드 이미지일 때만 non-null (삭제 대상)
)
