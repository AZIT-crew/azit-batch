package com.youthexpedition.azit.batch.job.member.processor

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.domain.MemberStatus
import com.youthexpedition.azit.batch.domain.SocialProvider
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberPurgeItemProcessorTest {
    private val processor = MemberPurgeItemProcessor()

    private fun withdrawnMember(profileImageUrl: String?): Member =
        Member(
            id = 1L,
            socialProvider = SocialProvider.KAKAO,
            socialProviderId = "12345",
            appleRefreshToken = null,
            profileImageUrl = profileImageUrl,
            status = MemberStatus.WITHDRAWN,
            withdrawnAt = LocalDateTime.of(2026, 6, 1, 0, 0),
        )

    @Test
    fun process_extractsS3Key_whenOwnUploadedImage() {
        // given
        val member = withdrawnMember("/profile/1/2026-06-01_uuid.jpg")

        // when
        val target = processor.process(member)

        // then
        assertEquals("profile/1/2026-06-01_uuid.jpg", target.profileImageS3Key)
    }

    @Test
    fun process_extractsS3Key_whenTempImage() {
        // given - 이미지 이동 전에 탈퇴한 경우 temp 경로가 남을 수 있음
        val member = withdrawnMember("/temp/profile/1/2026-06-01_uuid.jpg")

        // when
        val target = processor.process(member)

        // then
        assertEquals("temp/profile/1/2026-06-01_uuid.jpg", target.profileImageS3Key)
    }

    @Test
    fun process_returnsNullS3Key_whenDefaultImage() {
        // given - 공용 기본 이미지는 삭제 대상이 아님
        val member = withdrawnMember("/default/member/default_1.svg")

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_returnsNullS3Key_whenExternalUrl() {
        // given - 카카오 프로필 등 외부 URL은 삭제 대상이 아님
        val member = withdrawnMember("https://k.kakaocdn.net/profile/abc123.jpg")

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_returnsNullS3Key_whenProfileImageUrlIsNull() {
        // given
        val member = withdrawnMember(null)

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_mapsMemberFields() {
        // given
        val member = withdrawnMember(null)

        // when
        val target = processor.process(member)

        // then
        assertEquals(1L, target.memberId)
        assertEquals(SocialProvider.KAKAO, target.socialProvider)
        assertEquals("12345", target.socialProviderId)
        assertNull(target.appleRefreshToken)
    }
}
