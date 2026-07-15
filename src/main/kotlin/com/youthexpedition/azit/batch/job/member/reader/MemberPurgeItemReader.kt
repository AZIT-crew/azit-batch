package com.youthexpedition.azit.batch.job.member.reader

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.domain.MemberStatus
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import java.time.LocalDateTime

@Configuration
class MemberPurgeItemReader {

    companion object {
        const val READER_NAME = "memberPurgeItemReader"
        const val WITHDRAWAL_GRACE_PERIOD_DAYS = 30L // AZIT_Server의 Member.WITHDRAWAL_GRACE_PERIOD_DAYS와 동일해야 함
    }

    /**
     * 파기 대상: 탈퇴(WITHDRAWN) 후 유예기간 30일이 지난 회원.
     *
     * - baseDate(필수, yyyy-MM-dd): 실행 기준일. cron에서 당일 날짜를 넘긴다. 파기 상한 = baseDate 00:00 - 30일
     * - from / to(선택, ISO datetime): 수동 재처리 시 withdrawn_at 범위 지정 (to 지정 시 baseDate 기반 상한을 대체)
     *
     * 하한을 열어두는 이유: 이전 실행에서 실패(스킵)한 회원은 status가 WITHDRAWN으로 남아
     * 다음 정기 실행에서 자동으로 재시도된다. 파기 완료된 회원은 DELETED로 바뀌어 조회에서 제외된다.
     *
     * 처리된 회원이 조회 조건에서 빠져나가며 페이지가 밀리는 문제(page drift)가 있어
     * PagingItemReader 대신 CursorItemReader를 사용한다.
     */
    @Bean
    @StepScope
    fun memberPurgeItemReader(
        entityManagerFactory: EntityManagerFactory,
        @Value("#{jobParameters['baseDate']}") baseDate: String?,
        @Value("#{jobParameters['from']}") from: String?,
        @Value("#{jobParameters['to']}") to: String?,
    ): JpaCursorItemReader<Member> {
        requireNotNull(baseDate) { "baseDate JobParameter는 필수입니다. (형식: yyyy-MM-dd)" }

        val purgeDeadline = to?.let(LocalDateTime::parse)
            ?: LocalDate.parse(baseDate).atStartOfDay().minusDays(WITHDRAWAL_GRACE_PERIOD_DAYS)
        val fromDateTime = from?.let(LocalDateTime::parse)

        val parameters = mutableMapOf<String, Any>(
            "status" to MemberStatus.WITHDRAWN,
            "purgeDeadline" to purgeDeadline,
        )
        val query = buildString {
            append("SELECT m FROM Member m WHERE m.status = :status AND m.withdrawnAt <= :purgeDeadline")
            if (fromDateTime != null) {
                append(" AND m.withdrawnAt >= :from")
                parameters["from"] = fromDateTime
            }
            append(" ORDER BY m.id")
        }

        return JpaCursorItemReaderBuilder<Member>()
            .name(READER_NAME)
            .entityManagerFactory(entityManagerFactory)
            .queryString(query)
            .parameterValues(parameters)
            .build()
    }
}
