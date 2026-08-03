package com.youthexpedition.azit.batch.job.order.listener

import com.youthexpedition.azit.batch.domain.Order
import com.youthexpedition.azit.batch.job.order.dto.OrderExpireTarget
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

/**
 * 만료 스텝의 스킵 로깅 + 종료 시 대상/성공/실패 건수 요약 로깅을 함께 담당한다.
 * readCount = 대상 총 건수, writeCount = 성공 건수, skipCount = 실패 건수.
 */
@Component
class OrderExpireStepListener :
    SkipListener<Order, OrderExpireTarget>,
    StepExecutionListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSkipInRead(t: Throwable) {
        log.error("[ORDER_EXPIRE] 대상 조회 중 오류로 스킵되었습니다.", t)
    }

    override fun onSkipInProcess(
        item: Order,
        t: Throwable,
    ) {
        log.error("[ORDER_EXPIRE] orderId: {} 변환 중 오류로 스킵되었습니다.", item.id, t)
    }

    override fun onSkipInWrite(
        item: OrderExpireTarget,
        t: Throwable,
    ) {
        // 스킵된 주문은 PENDING 상태로 남아 다음 실행에서 자동 재시도
        log.error("[ORDER_EXPIRE] orderId: {} 만료 처리 중 오류로 스킵되었습니다. 다음 실행에서 재시도됩니다.", item.orderId, t)
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        log.info(
            "[ORDER_EXPIRE] 주문 만료 배치 종료 - 대상: {}건, 성공: {}건, 실패: {}건",
            stepExecution.readCount,
            stepExecution.writeCount,
            stepExecution.skipCount,
        )
        return stepExecution.exitStatus
    }
}
