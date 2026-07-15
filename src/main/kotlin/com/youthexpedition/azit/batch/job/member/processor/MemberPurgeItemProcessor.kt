package com.youthexpedition.azit.batch.job.member.processor

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

@Component
class MemberPurgeItemProcessor : ItemProcessor<Member, MemberPurgeTarget> {
    companion object {
        private const val DEFAULT_IMAGE_PREFIX = "/default"
    }

    override fun process(member: Member): MemberPurgeTarget =
        MemberPurgeTarget(
            memberId = member.id,
            socialProvider = member.socialProvider,
            socialProviderId = member.socialProviderId,
            appleRefreshToken = member.appleRefreshToken,
            profileImageS3Key = extractOwnS3Key(member.profileImageUrl),
        )

    /**
     * 자체 버킷에 업로드된 이미지("/profile/..", "/temp/..")만 S3 삭제 대상 키로 변환한다.
     * 외부 URL(카카오 프로필 등, "http..")과 공용 기본 이미지("/default/..")는 삭제하지 않는다.
     */
    private fun extractOwnS3Key(profileImageUrl: String?): String? =
        profileImageUrl
            ?.takeIf { it.startsWith("/") && !it.startsWith(DEFAULT_IMAGE_PREFIX) }
            ?.removePrefix("/")
}
