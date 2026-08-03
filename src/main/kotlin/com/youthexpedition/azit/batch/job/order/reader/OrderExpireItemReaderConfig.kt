package com.youthexpedition.azit.batch.job.order.reader

import com.youthexpedition.azit.batch.domain.Order
import com.youthexpedition.azit.batch.domain.OrderStatus
import com.youthexpedition.azit.batch.domain.PaymentMethod
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDateTime

@Configuration
class OrderExpireItemReaderConfig {
    companion object {
        const val READER_NAME = "orderExpireItemReader"
        const val PAYMENT_DUE_HOURS = 24L
    }

    /**
     * 만료 대상: 무통장입금(BANK_TRANSFER)이면서 결제 대기(PENDING) 상태로 입금 기한(24시간)이 지난 주문
     *
     * - referenceTime(필수, ISO-8601): 실행 기준 시각. 만료 상한 = referenceTime - 24시간.
     * - from / to(선택, ISO-8601): 수동 재처리 시 createdAt 범위 지정 (to 지정 시 referenceTime 기반 상한을 대체)
     */
    @Bean
    @StepScope
    fun orderExpireItemReader(
        entityManagerFactory: EntityManagerFactory,
        @Value("#{jobParameters['referenceTime']}") referenceTime: String?,
        @Value("#{jobParameters['from']}") from: String?,
        @Value("#{jobParameters['to']}") to: String?,
    ): JpaCursorItemReader<Order> {
        requireNotNull(referenceTime) { "referenceTime JobParameter는 필수입니다. (형식: yyyy-MM-ddTHH:mm:ss)" }

        val deadline =
            to?.let(LocalDateTime::parse)
                ?: LocalDateTime.parse(referenceTime).minusHours(PAYMENT_DUE_HOURS)
        val fromDateTime = from?.let(LocalDateTime::parse)
        println("deadline: $deadline")

        val parameters =
            mutableMapOf<String, Any>(
                "paymentMethod" to PaymentMethod.BANK_TRANSFER,
                "status" to OrderStatus.PENDING,
                "deadline" to deadline,
            )
        val query =
            buildString {
                append("SELECT o FROM Order o WHERE o.paymentMethod = :paymentMethod")
                append(" AND o.status = :status AND o.createdAt <= :deadline")
                if (fromDateTime != null) {
                    append(" AND o.createdAt >= :from")
                    parameters["from"] = fromDateTime
                }
                append(" ORDER BY o.id")
            }

        return JpaCursorItemReaderBuilder<Order>()
            .name(READER_NAME)
            .entityManagerFactory(entityManagerFactory)
            .queryString(query)
            .parameterValues(parameters)
            .build()
    }
}
