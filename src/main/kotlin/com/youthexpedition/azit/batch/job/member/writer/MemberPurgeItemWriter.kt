package com.youthexpedition.azit.batch.job.member.writer

import com.youthexpedition.azit.batch.external.dto.SocialRevokeCommand
import com.youthexpedition.azit.batch.external.s3.ImageStoragePort
import com.youthexpedition.azit.batch.external.social.SocialAuthPortRouter
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import com.youthexpedition.azit.batch.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component

@Component
class MemberPurgeItemWriter(
    private val socialAuthPortRouter: SocialAuthPortRouter,
    private val imageStoragePort: ImageStoragePort,
    private val memberRepository: MemberRepository,
) : ItemWriter<MemberPurgeTarget> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun write(chunk: Chunk<out MemberPurgeTarget>) {
        chunk.items.forEach(::purge)
    }

    /**
     * 회원 1명 파기 파이프라인.
     * 순서 중요: 소셜 revoke는 socialProviderId / appleRefreshToken이 익명화로 지워지기 전에 실행해야 한다.
     * revoke, S3 삭제는 멱등이므로 이후 DB 단계가 롤백되어도 재실행 시 문제가 없다.
     */
    private fun purge(target: MemberPurgeTarget) {
        // 1. 소셜 연동 해제
        socialAuthPortRouter
            .getAdapter(target.socialProvider)
            .revoke(SocialRevokeCommand(target.socialProviderId, target.appleRefreshToken))

        // 2. S3 프로필 이미지 삭제
        target.profileImageS3Key?.let(imageStoragePort::delete)

        // 3. 회원 익명화 + DELETED 처리 (배치 도중 재활성화된 회원 보호를 위한 조건부 UPDATE)
        val anonymized = memberRepository.anonymize(target.memberId)
        if (anonymized == 0) {
            log.warn("[MEMBER_PURGE] memberId: {} 익명화 대상이 아니어서 건너뜁니다. (재활성화되었거나 이미 파기됨)", target.memberId)
            return
        }

        // 4. 배송지 삭제
        memberRepository.deleteDeliveryAddresses(target.memberId)

        // 5. 주문 배송지 스냅샷 마스킹 (주문 레코드는 법정 보존 기간으로 유지)
        memberRepository.maskOrderDeliverySnapshots(target.memberId)

        // 6. 포인트 이력 삭제
        memberRepository.deletePointHistories(target.memberId)

        log.info("[MEMBER_PURGE] memberId: {} 개인정보 파기를 완료했습니다.", target.memberId)
    }
}
