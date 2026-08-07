package com.youthexpedition.azit.batch.job.member.writer

import com.youthexpedition.azit.batch.domain.enums.SocialProvider
import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import com.youthexpedition.azit.batch.external.s3.ImageStoragePort
import com.youthexpedition.azit.batch.external.social.SocialAuthPort
import com.youthexpedition.azit.batch.external.social.SocialAuthPortRouter
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import com.youthexpedition.azit.batch.job.member.dto.SocialAccountPurgeTarget
import com.youthexpedition.azit.batch.repository.MemberRepository
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.batch.infrastructure.item.Chunk
import kotlin.test.Test

class MemberPurgeItemWriterTest {
    private val socialAuthPortRouter = mock(SocialAuthPortRouter::class.java)
    private val imageStoragePort = mock(ImageStoragePort::class.java)
    private val memberRepository = mock(MemberRepository::class.java)

    private val writer = MemberPurgeItemWriter(socialAuthPortRouter, imageStoragePort, memberRepository)

    private val kakaoAuthPort = mock(SocialAuthPort::class.java)
    private val appleAuthPort = mock(SocialAuthPort::class.java)

    private val kakaoAccount =
        SocialAccountPurgeTarget(
            socialProvider = SocialProvider.KAKAO,
            socialProviderId = "12345",
            appleRefreshToken = null,
        )

    private val appleAccount =
        SocialAccountPurgeTarget(
            socialProvider = SocialProvider.APPLE,
            socialProviderId = "apple-sub",
            appleRefreshToken = "appleRefreshToken",
        )

    private fun target(
        socialAccounts: List<SocialAccountPurgeTarget>,
        profileImageS3Key: String? = null,
    ) = MemberPurgeTarget(
        memberId = MEMBER_ID,
        socialAccounts = socialAccounts,
        profileImageS3Key = profileImageS3Key,
    )

    private fun givenAnonymizeSucceeds() {
        `when`(memberRepository.anonymize(MEMBER_ID)).thenReturn(1)
    }

    private fun givenRoutedAdapters() {
        `when`(socialAuthPortRouter.getAdapter(SocialProvider.KAKAO)).thenReturn(kakaoAuthPort)
        `when`(socialAuthPortRouter.getAdapter(SocialProvider.APPLE)).thenReturn(appleAuthPort)
    }

    @Test
    fun write_whenMultipleProvidersLinked_revokesEveryLinkedProvider() {
        // given - 카카오/애플을 모두 연동한 회원은 두 플랫폼 모두 해제되어야 함
        givenAnonymizeSucceeds()
        givenRoutedAdapters()

        // when
        writer.write(Chunk(listOf(target(listOf(kakaoAccount, appleAccount)))))

        // then
        verify(kakaoAuthPort).revoke(SocialRevokeCommand("12345", null))
        verify(appleAuthPort).revoke(SocialRevokeCommand("apple-sub", "appleRefreshToken"))
        verify(memberRepository).deleteSocialAccounts(MEMBER_ID)
    }

    @Test
    fun write_validTarget_revokesBeforeDeletingSocialAccounts() {
        // given - revoke에 필요한 정보가 row 삭제로 사라지기 전에 해제가 끝나야 함
        givenAnonymizeSucceeds()
        givenRoutedAdapters()

        // when
        writer.write(Chunk(listOf(target(listOf(appleAccount)))))

        // then
        val order = inOrder(appleAuthPort, memberRepository)
        order.verify(appleAuthPort).revoke(SocialRevokeCommand("apple-sub", "appleRefreshToken"))
        order.verify(memberRepository).deleteSocialAccounts(MEMBER_ID)
    }

    @Test
    fun write_whenNoSocialAccountLinked_skipsRevokeAndPurgesMember() {
        // given - 이전 실행에서 revoke까지 성공하고 스킵된 회원도 파기가 완료되어야 함
        givenAnonymizeSucceeds()

        // when
        writer.write(Chunk(listOf(target(emptyList()))))

        // then
        verify(socialAuthPortRouter, never()).getAdapter(SocialProvider.KAKAO)
        verify(socialAuthPortRouter, never()).getAdapter(SocialProvider.APPLE)
        verify(memberRepository).deleteSocialAccounts(MEMBER_ID)
        verify(memberRepository).deletePointHistories(MEMBER_ID)
    }

    @Test
    fun write_validTarget_purgesEveryMemberOwnedTable() {
        // given
        givenAnonymizeSucceeds()
        givenRoutedAdapters()

        // when
        writer.write(Chunk(listOf(target(listOf(kakaoAccount)))))

        // then - 파기 대상 테이블이 하나도 빠지지 않아야 함
        verify(memberRepository).deleteSocialAccounts(MEMBER_ID)
        verify(memberRepository).deleteDeliveryAddresses(MEMBER_ID)
        verify(memberRepository).maskOrderDeliverySnapshots(MEMBER_ID)
        verify(memberRepository).deletePointHistories(MEMBER_ID)
        verify(memberRepository).deleteTermsConsents(MEMBER_ID)
        verify(memberRepository).deleteTermsConsentHistories(MEMBER_ID)
    }

    @Test
    fun write_whenMemberReactivated_doesNotDeleteSocialAccounts() {
        // given - 배치 도중 재활성화되어 익명화 대상에서 빠진 회원 (anonymize 영향 행 0)
        givenRoutedAdapters()

        // when
        writer.write(Chunk(listOf(target(listOf(kakaoAccount)))))

        // then
        verify(memberRepository, never()).deleteSocialAccounts(MEMBER_ID)
        verify(memberRepository, never()).deleteDeliveryAddresses(MEMBER_ID)
        verify(memberRepository, never()).deletePointHistories(MEMBER_ID)
        verify(memberRepository, never()).deleteTermsConsents(MEMBER_ID)
        verify(memberRepository, never()).deleteTermsConsentHistories(MEMBER_ID)
    }

    @Test
    fun write_whenOwnUploadedImage_deletesS3Object() {
        // given
        givenAnonymizeSucceeds()
        givenRoutedAdapters()

        // when
        writer.write(Chunk(listOf(target(listOf(kakaoAccount), profileImageS3Key = "profile/1/image.jpg"))))

        // then
        verify(imageStoragePort).delete("profile/1/image.jpg")
    }

    companion object {
        private const val MEMBER_ID = 1L
    }
}
