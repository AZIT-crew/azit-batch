package com.youthexpedition.azit.batch.job.order

import com.youthexpedition.azit.batch.domain.Order
import com.youthexpedition.azit.batch.job.JobFailureExitCodeGenerator
import com.youthexpedition.azit.batch.job.listener.DiscordNotificationJobListener
import com.youthexpedition.azit.batch.job.order.dto.OrderExpireTarget
import com.youthexpedition.azit.batch.job.order.listener.OrderExpireStepListener
import com.youthexpedition.azit.batch.job.order.processor.OrderExpireItemProcessor
import com.youthexpedition.azit.batch.job.order.writer.OrderExpireItemWriter
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 무통장입금 입금 기한(24시간) 만료 주문 처리 job
 *
 * 실행 예시:
 * - 스케줄러 실행(매시간):
 *   --spring.batch.job.name=orderExpireJob referenceTime=2026-07-25T04:00:00
 * - 수동 재처리:
 *   --spring.batch.job.name=orderExpireJob referenceTime=2026-07-25T04:00:00
 *   from=2026-07-01T00:00:00 to=2026-07-20T00:00:00
 */
@Configuration
class OrderExpireJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val orderExpireItemReader: JpaCursorItemReader<Order>,
    private val orderExpireItemProcessor: OrderExpireItemProcessor,
    private val orderExpireItemWriter: OrderExpireItemWriter,
    private val orderExpireStepListener: OrderExpireStepListener,
    private val discordNotificationJobListener: DiscordNotificationJobListener,
    private val jobFailureExitCodeGenerator: JobFailureExitCodeGenerator,
) {
    companion object {
        const val JOB_NAME = "orderExpireJob"
        const val STEP_NAME = "orderExpireStep"
        const val CHUNK_SIZE = 10
        const val SKIP_LIMIT = 100L
    }

    @Bean
    fun orderExpireJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(orderExpireStep())
            .listener(discordNotificationJobListener)
            .listener(jobFailureExitCodeGenerator)
            .build()

    /**
     * 재고/포인트 처리 중 오류로 배치 전체가 실패하지 않도록 주문 단위로 스킵한다.
     * 스킵된 주문은 PENDING 상태로 남아 다음 스케줄러 실행에서 자동 재시도된다.
     */
    @Bean
    fun orderExpireStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .chunk<Order, OrderExpireTarget>(CHUNK_SIZE)
            .transactionManager(transactionManager)
            .reader(orderExpireItemReader)
            .processor(orderExpireItemProcessor)
            .writer(orderExpireItemWriter)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(SKIP_LIMIT)
            .listener(orderExpireStepListener)
            .build()
}
