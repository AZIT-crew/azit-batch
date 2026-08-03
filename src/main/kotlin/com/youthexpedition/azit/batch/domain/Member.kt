package com.youthexpedition.azit.batch.domain

import com.youthexpedition.azit.batch.domain.enums.MemberRole
import com.youthexpedition.azit.batch.domain.enums.MemberStatus
import com.youthexpedition.azit.batch.domain.enums.SocialProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 20)
    val socialProvider: SocialProvider,

    @Column(name = "social_provider_id", length = 255)
    val socialProviderId: String?,

    @Column(name = "nickname", nullable = false, length = 20)
    val nickname: String = "",

    @Column(name = "email", length = 255)
    val email: String? = null,

    @Column(name = "is_email_sharing_enabled", nullable = false)
    val isEmailSharingEnabled: Boolean = true,

    @Column(name = "profile_image_url", length = 255)
    val profileImageUrl: String?,

    @Column(name = "apple_refresh_token", length = 500)
    val appleRefreshToken: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    val status: MemberStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: MemberRole = MemberRole.MEMBER,

    @Column(name = "total_points", nullable = false)
    val totalPoints: Long = 0L,

    @Column(name = "total_attendance_count", nullable = false)
    val totalAttendanceCount: Int = 0,

    @Column(name = "essential_terms_agreed_at")
    val essentialTermsAgreedAt: LocalDateTime? = null, // 필수 약관(전체) 동의 시점

    @Column(name = "is_marketing_terms_agreed", nullable = false)
    val isMarketingTermsAgreed: Boolean = false, // 마케팅 동의 여부

    @Column(name = "marketing_terms_agreed_at")
    val marketingTermsAgreedAt: LocalDateTime? = null, // 마케팅 동의 시점

    @Column(name = "is_notification_agreed", nullable = false)
    val isNotificationAgreed: Boolean = false, // 알림 수신 동의 여부

    @Column(name = "notification_agreed_at")
    val notificationAgreedAt: LocalDateTime? = null, // 알림 수신 동의 시점

    @Column(name = "withdrawn_at")
    val withdrawnAt: LocalDateTime?, // 탈퇴 시점

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null, // 데이터 생성일시 (DB default)

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null, // 데이터 수정일시 (DB on update)
)
