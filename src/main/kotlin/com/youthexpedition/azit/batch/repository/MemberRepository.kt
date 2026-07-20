package com.youthexpedition.azit.batch.repository

import com.youthexpedition.azit.batch.domain.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberRepository : JpaRepository<Member, Long> {
    /**
     * 회원 개인정보 익명화 + 파기 완료(DELETED) 처리.
     * WHERE status = 'WITHDRAWN' 조건으로 배치 실행 중 재활성화된 회원(ACTIVE) 및
     * 이미 파기된 회원(DELETED)을 보호한다. 영향 행이 0이면 후속 파기 단계를 진행하지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE member SET
                nickname = '탈퇴한 사용자',
                email = NULL,
                is_email_sharing_enabled = false,
                social_provider_id = NULL,
                apple_refresh_token = NULL,
                profile_image_url = NULL,
                essential_terms_agreed_at = NULL,
                marketing_terms_agreed_at = NULL,
                notification_agreed_at = NULL,
                is_marketing_terms_agreed = false,
                is_notification_agreed = false,
                status = 'DELETED'
            WHERE id = :memberId AND status = 'WITHDRAWN'
        """,
        nativeQuery = true,
    )
    fun anonymize(
        @Param("memberId") memberId: Long,
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM delivery_address WHERE member_id = :memberId", nativeQuery = true)
    fun deleteDeliveryAddresses(
        @Param("memberId") memberId: Long,
    ): Int

    /**
     * 주문 레코드는 전자상거래법상 보존 의무(5년)가 있어 삭제하지 않고,
     * 배송지 스냅샷 등 개인 식별 필드만 마스킹한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE orders SET
                recipient_name = '***',
                phone_number = '***',
                base_address = '***',
                detail_address = '***',
                shipping_instruction = NULL,
                depositor_name = CASE WHEN depositor_name IS NULL THEN NULL ELSE '***' END
            WHERE member_id = :memberId
        """,
        nativeQuery = true,
    )
    fun maskOrderDeliverySnapshots(
        @Param("memberId") memberId: Long,
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM point_history WHERE member_id = :memberId", nativeQuery = true)
    fun deletePointHistories(
        @Param("memberId") memberId: Long,
    ): Int
}
