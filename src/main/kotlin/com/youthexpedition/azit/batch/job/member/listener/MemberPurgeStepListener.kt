package com.youthexpedition.azit.batch.job.member.listener

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

/**
 * 파기 스텝의 스킵 로깅 + 종료 시 대상/성공/실패 건수 요약 로깅을 함께 담당한다.
 * readCount = 대상 총 건수, writeCount = 성공 건수, skipCount = 실패 건수.
 * (Processor는 순수 필드 매핑만 하고 예외를 던지지 않으므로 read/process 스킵은 사실상 발생하지 않는다.)
 */
@Component
class MemberPurgeStepListener :
    SkipListener<Member, MemberPurgeTarget>,
    StepExecutionListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSkipInRead(t: Throwable) {
        log.error("[MEMBER_PURGE] 대상 조회 중 오류로 스킵되었습니다.", t)
    }

    override fun onSkipInProcess(
        item: Member,
        t: Throwable,
    ) {
        log.error("[MEMBER_PURGE] memberId: {} 변환 중 오류로 스킵되었습니다.", item.id, t)
    }

    override fun onSkipInWrite(
        item: MemberPurgeTarget,
        t: Throwable,
    ) {
        // 스킵된 회원은 WITHDRAWN 상태로 남아 다음 정기 실행에서 자동 재시도
        log.error("[MEMBER_PURGE] memberId: {} 파기 중 오류로 스킵되었습니다. 다음 실행에서 재시도됩니다.", item.memberId, t)
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        log.info(
            "[MEMBER_PURGE] 회원 데이터 파기 배치 종료 - 대상: {}건, 성공: {}건, 실패: {}건",
            stepExecution.readCount,
            stepExecution.writeCount,
            stepExecution.skipCount,
        )
        return stepExecution.exitStatus
    }
}
