package com.youthexpedition.azit.batch.domain

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
    @Column(name = "social_provider")
    val socialProvider: SocialProvider,

    @Column(name = "social_provider_id")
    val socialProviderId: String?,

    @Column(name = "apple_refresh_token")
    val appleRefreshToken: String?,

    @Column(name = "profile_image_url")
    val profileImageUrl: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: MemberStatus,

    @Column(name = "withdrawn_at")
    val withdrawnAt: LocalDateTime?,
)
