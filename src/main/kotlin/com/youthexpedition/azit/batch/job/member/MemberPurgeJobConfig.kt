package com.youthexpedition.azit.batch.job.member

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import com.youthexpedition.azit.batch.job.member.listener.MemberPurgeStepListener
import com.youthexpedition.azit.batch.job.member.processor.MemberPurgeItemProcessor
import com.youthexpedition.azit.batch.job.member.writer.MemberPurgeItemWriter
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
 * 탈퇴 유예기간(30일) 만료 회원 개인정보 파기 job.
 *
 * 실행 예시:
 * - 스케줄러 실행:   --spring.batch.job.name=memberPurgeJob baseDate=2026-07-09
 * - 수동 재처리: --spring.batch.job.name=memberPurgeJob baseDate=2026-07-09 from=2026-06-01T00:00:00 to=2026-06-05T00:00:00
 */
@Configuration
class MemberPurgeJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val memberPurgeItemReader: JpaCursorItemReader<Member>,
    private val memberPurgeItemProcessor: MemberPurgeItemProcessor,
    private val memberPurgeItemWriter: MemberPurgeItemWriter,
    private val memberPurgeStepListener: MemberPurgeStepListener,
) {
    companion object {
        const val JOB_NAME = "memberPurgeJob"
        const val STEP_NAME = "memberPurgeStep"
        const val CHUNK_SIZE = 10
        const val SKIP_LIMIT = 100L
    }

    @Bean
    fun memberPurgeJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(memberPurgeStep())
            .build()

    /**
     * 소셜 revoke 등 외부 API 오류로 배치 전체가 실패하지 않도록 회원 단위로 스킵한다.
     * 스킵된 회원은 WITHDRAWN 상태로 남아 다음 스케줄러 실행에서 자동 재시도된다.
     *
     * transactionManager를 지정하지 않으면 청크 트랜잭션이 JPA EntityManagerFactory와
     * 동기화되지 않아 @Modifying 쿼리 실행 시 "No active transaction for update or
     * delete query" 오류가 발생한다. chunk(chunkSize, transactionManager)는 구버전
     * SimpleStepBuilder(Int skipLimit 등)를 반환하는 deprecated 오버로드이므로,
     * 최신 ChunkOrientedStepBuilder를 유지하기 위해 별도 체이닝 메서드로 지정한다.
     */
    @Bean
    fun memberPurgeStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .chunk<Member, MemberPurgeTarget>(CHUNK_SIZE)
            .transactionManager(transactionManager)
            .reader(memberPurgeItemReader)
            .processor(memberPurgeItemProcessor)
            .writer(memberPurgeItemWriter)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(SKIP_LIMIT)
            .listener(memberPurgeStepListener)
            .build()
}
