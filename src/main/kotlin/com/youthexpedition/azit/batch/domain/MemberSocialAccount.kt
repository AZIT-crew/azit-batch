package com.youthexpedition.azit.batch.domain

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

/**
 * 회원에 연동된 소셜 계정. 회원 1명이 여러 플랫폼(카카오/애플)을 동시에 연동 가능
 */
@Entity
@Table(name = "member_social_account")
class MemberSocialAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 20)
    val socialProvider: SocialProvider,

    @Column(name = "social_provider_id", nullable = false, length = 255)
    val socialProviderId: String,

    @Column(name = "email", length = 255)
    val email: String? = null, // 해당 소셜 계정에서 받은 이메일

    @Column(name = "is_email_sharing_enabled", nullable = false)
    val isEmailSharingEnabled: Boolean = true, // 플랫폼별 이메일 공유 상태

    @Column(name = "apple_refresh_token", length = 500)
    val appleRefreshToken: String? = null, // 애플 revoke 대상 토큰

    @Column(name = "linked_at", nullable = false)
    val linkedAt: LocalDateTime, // 연동 일시

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null, // 데이터 생성일시

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null, // 데이터 수정일시
)
