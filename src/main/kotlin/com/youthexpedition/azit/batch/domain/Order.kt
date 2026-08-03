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
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "order_number", nullable = false, length = 20)
    val orderNumber: String = "",

    // 배송지 정보 스냅샷
    @Column(name = "recipient_name", nullable = false, length = 50)
    val recipientName: String = "",

    @Column(name = "phone_number", nullable = false, length = 20)
    val phoneNumber: String = "",

    @Column(name = "base_address", nullable = false, length = 255)
    val baseAddress: String = "",

    @Column(name = "detail_address", nullable = false, length = 255)
    val detailAddress: String = "",

    @Column(name = "shipping_instruction", length = 100)
    val shippingInstruction: String? = null,

    // 결제 금액 정보
    @Column(name = "total_product_price", nullable = false)
    val totalProductPrice: Long = 0L,

    @Column(name = "total_shipping_fee", nullable = false)
    val totalShippingFee: Long = 0L,

    @Column(name = "membership_discount", nullable = false)
    val membershipDiscount: Long = 0L,

    @Column(name = "used_points", nullable = false)
    val usedPoints: Long = 0L,

    @Column(name = "total_payment_price", nullable = false)
    val totalPaymentPrice: Long = 0L,

    @Column(name = "depositor_name", length = 50)
    val depositorName: String? = null, // 입금자명 (무통장입금)

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    val paymentMethod: PaymentMethod,

    @Column(name = "courier", length = 50)
    val courier: String? = null,

    @Column(name = "tracking_number", length = 50)
    val trackingNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: OrderStatus,

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null,
)
