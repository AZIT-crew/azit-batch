package com.youthexpedition.azit.batch.job.member.processor

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.domain.MemberSocialAccount
import com.youthexpedition.azit.batch.domain.enums.MemberStatus
import com.youthexpedition.azit.batch.domain.enums.SocialProvider
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberPurgeItemProcessorTest {
    private val processor = MemberPurgeItemProcessor()

    private val linkedAt = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun kakaoAccount(): MemberSocialAccount =
        MemberSocialAccount(
            id = 1L,
            memberId = 1L,
            socialProvider = SocialProvider.KAKAO,
            socialProviderId = "12345",
            linkedAt = linkedAt,
        )

    private fun appleAccount(): MemberSocialAccount =
        MemberSocialAccount(
            id = 2L,
            memberId = 1L,
            socialProvider = SocialProvider.APPLE,
            socialProviderId = "apple-sub",
            appleRefreshToken = "appleRefreshToken",
            linkedAt = linkedAt,
        )

    private fun withdrawnMember(
        profileImageUrl: String? = null,
        socialAccounts: List<MemberSocialAccount> = listOf(kakaoAccount()),
    ): Member =
        Member(
            id = 1L,
            profileImageUrl = profileImageUrl,
            status = MemberStatus.WITHDRAWN,
            withdrawnAt = LocalDateTime.of(2026, 6, 1, 0, 0),
            socialAccounts = socialAccounts,
        )

    @Test
    fun process_whenOwnUploadedImage_extractsS3Key() {
        // given
        val member = withdrawnMember("/profile/1/2026-06-01_uuid.jpg")

        // when
        val target = processor.process(member)

        // then
        assertEquals("profile/1/2026-06-01_uuid.jpg", target.profileImageS3Key)
    }

    @Test
    fun process_whenTempImage_extractsS3Key() {
        // given - 이미지 이동 전에 탈퇴한 경우 temp 경로가 남을 수 있음
        val member = withdrawnMember("/temp/profile/1/2026-06-01_uuid.jpg")

        // when
        val target = processor.process(member)

        // then
        assertEquals("temp/profile/1/2026-06-01_uuid.jpg", target.profileImageS3Key)
    }

    @Test
    fun process_whenDefaultImage_returnsNullS3Key() {
        // given - 공용 기본 이미지는 삭제 대상이 아님
        val member = withdrawnMember("/default/member/default_1.svg")

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_whenExternalUrl_returnsNullS3Key() {
        // given - 카카오 프로필 등 외부 URL은 삭제 대상이 아님
        val member = withdrawnMember("https://k.kakaocdn.net/profile/abc123.jpg")

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_whenProfileImageUrlIsNull_returnsNullS3Key() {
        // given
        val member = withdrawnMember(null)

        // when
        val target = processor.process(member)

        // then
        assertNull(target.profileImageS3Key)
    }

    @Test
    fun process_validMember_mapsMemberFields() {
        // given
        val member = withdrawnMember()

        // when
        val target = processor.process(member)

        // then
        assertEquals(1L, target.memberId)
        assertEquals(1, target.socialAccounts.size)

        val account = target.socialAccounts.first()
        assertEquals(SocialProvider.KAKAO, account.socialProvider)
        assertEquals("12345", account.socialProviderId)
        assertNull(account.appleRefreshToken)
    }

    @Test
    fun process_whenMultipleProvidersLinked_mapsAllSocialAccounts() {
        // given - 카카오/애플을 모두 연동한 회원은 두 계정 모두 revoke 대상이어야 함
        val member = withdrawnMember(socialAccounts = listOf(kakaoAccount(), appleAccount()))

        // when
        val target = processor.process(member)

        // then
        assertEquals(2, target.socialAccounts.size)
        assertEquals(
            listOf(SocialProvider.KAKAO, SocialProvider.APPLE),
            target.socialAccounts.map { it.socialProvider },
        )
        assertEquals("appleRefreshToken", target.socialAccounts[1].appleRefreshToken)
    }

    @Test
    fun process_whenNoSocialAccountLinked_returnsEmptySocialAccounts() {
        // given - 이전 실행에서 revoke까지 성공하고 스킵된 회원은 연동 계정이 없다.
        // 파기 대상에서 누락되지 않고 빈 목록으로 통과해야 한다.
        val member = withdrawnMember(socialAccounts = emptyList())

        // when
        val target = processor.process(member)

        // then
        assertEquals(1L, target.memberId)
        assertTrue(target.socialAccounts.isEmpty())
    }
}
